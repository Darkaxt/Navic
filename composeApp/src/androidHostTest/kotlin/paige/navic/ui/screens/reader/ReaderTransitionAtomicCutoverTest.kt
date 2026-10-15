package paige.navic.ui.screens.reader

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import paige.navic.reader.parentIdentity
import paige.navic.reader.ReaderAdoptedPredecessorSeedId
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderExternalRelocationIntent
import paige.navic.reader.ReaderExternalRelocationSource
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderResourceReleaseIssuer
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionInputLease
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceRegistration

class ReaderTransitionAtomicCutoverTest {
	@Test
	fun resourceRegistrationUsesOwnerIndependentRetirementOrder() {
		val id = transitionId()
		val transitionKey = ReaderTransitionResourceKey(
			ReaderTransitionResourceOwnerId.TransitionOwned(id),
			ReaderTransitionResourceKind.Deck,
			41L
		)
		val first = ReaderTransitionResourceRegistration(
			transitionKey,
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val ledger = ReaderTransitionReleaseLedger()
		assertTrue(ledger.register(first))
		val physicalIdentity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(43L)),
			ReaderLegacyInventorySource.Deck,
			ReaderLegacySourceLocalOpaqueToken(47L)
		)
		val imported = assertNotNull(
			ledger.importLegacy(
				physicalIdentity,
				ReaderTransitionResourceOwnerId.AdoptedPredecessor(
					ReaderAdoptedPredecessorSeedId.fromValidatedImport(43L)
				),
				ReaderTransitionResourceKind.Deck,
				5L
			)
		)
		val second = imported.registration

		val firstCommand = assertNotNull(ledger.requestRelease(first.key))
		val secondCommand = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Session(3L, 5L), second)
		)
		assertEquals(first, firstCommand.registration)
		assertEquals(second, secondCommand.registration)
		assertTrue(
			ledger.confirmLegacyReleased(
				physicalIdentity,
				paige.navic.reader.ReaderTransitionFact.ResourceReleased(secondCommand.identity)
			)
		)
		assertTrue(
			ledger.confirmReleased(
				paige.navic.reader.ReaderTransitionFact.ResourceReleased(firstCommand.identity)
			)
		)
		assertTrue(ledger.retirementFence().confirms(first.retirementOrder))
		assertTrue(ledger.retirementFence().confirms(second.retirementOrder))
	}

	@Test
	fun committedPresentationRequiresExactResourceRegistration() {
		val binding = binding()
		val id = transitionId(binding)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), binding, 31L, 37L, 1200, 800
			)
		)
		val key = ReaderTransitionResourceKey(
			ReaderTransitionResourceOwnerId.TransitionOwned(id),
			ReaderTransitionResourceKind.FrameHandoff,
			41L
		)

		val wrongRegistration = ReaderTransitionResourceRegistration(
			key.copy(opaqueId = 43L),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		assertFailsWith<IllegalArgumentException> {
			paige.navic.reader.ReaderCommittedTransition(
				id,
				owner,
				binding,
				key,
				wrongRegistration
			)
		}
	}

	@Test
	fun allocationMismatchRejectsBeforePhysicalWork() {
		var physicalCalls = 0
		val allocator = ReaderMaterialGenerationAllocator(
			readerSessionGeneration = 3L,
			prepare = { physicalCalls += 1 }
		)
		val id = transitionId()
		val semanticBinding = binding().copy(
			preparationGeneration = null,
			rasterGeneration = null,
			textureGeneration = null
		)
		val wrong = semanticBinding.copy(publicationGeneration = 99L)

		val rejected = allocator.allocate(
			ReaderTransitionCommand.AllocateMaterialBinding(id, wrong)
		)
		assertIs<ReaderPortCommandResult.Rejected>(rejected)
		assertEquals(0, physicalCalls)

		val exactId = id.copy(expectedBinding = ReaderExpectedPresentationBinding.Exact(semanticBinding))
		val accepted = allocator.allocate(
			ReaderTransitionCommand.AllocateMaterialBinding(exactId, semanticBinding)
		)
		assertIs<ReaderPortCommandResult.Accepted>(accepted)
		assertEquals(0, physicalCalls, "allocation itself must not start raster work")
	}

	@Test
	fun ownerAndInputCommitIsOneAtomicPublication() {
		val binding = binding()
		val id = transitionId(binding)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), binding, 31L, 37L, 1200, 800
			)
		)
		val registration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(id),
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val publisher = ReaderOwnerAndInputPublicationBarrier()

		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			publisher.publish(
				commitCommand(
					id, owner, binding, registration, ReaderTransitionInputLease.CoverActions, 1L
				)
			)
		)
		assertEquals(1, publisher.atomicCommitCount)
		assertEquals(owner, publisher.snapshot?.owner)
		assertEquals(ReaderTransitionInputLease.CoverActions, publisher.snapshot?.lease)
		assertFalse(publisher.observedPartialApplication)
	}

	@Test
	fun successorOwnerAndInputCommitAtomicallyReplacesPriorSnapshot() {
		val firstBinding = binding()
		val firstId = transitionId(firstBinding)
		val firstOwner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), firstBinding, 31L, 37L, 1200, 800
			)
		)
		val firstRegistration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(firstId),
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val secondBinding = firstBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L)
		)
		val secondId = transitionId(secondBinding, sequence = 8L)
		val secondOwner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(43L), secondBinding, 47L, 53L, 1200, 800
			)
		)
		val secondRegistration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(secondId),
				ReaderTransitionResourceKind.FrameHandoff,
				59L
			),
			ReaderResourceRetirementOrder(3L, 5L, 2L)
		)
		val publisher = ReaderOwnerAndInputPublicationBarrier()
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			publisher.publish(
				commitCommand(
					firstId,
					firstOwner,
					firstBinding,
					firstRegistration,
					ReaderTransitionInputLease.CoverActions,
					1L
				)
			)
		)

		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			publisher.publish(
				commitCommand(
					secondId,
					secondOwner,
					secondBinding,
					secondRegistration,
					ReaderTransitionInputLease.ChromeOnly,
					2L
				)
			)
		)
		assertEquals(2, publisher.atomicCommitCount)
		assertEquals(secondOwner, publisher.snapshot?.owner)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, publisher.snapshot?.lease)
		assertFalse(publisher.observedPartialApplication)
	}

	@Test
	fun combinedPublicationUsesPhysicalSafetyNarrowingBeforeItsSingleWrite() {
		val binding = binding()
		val id = transitionId(binding)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), binding, 31L, 37L, 1200, 800
			)
		)
		val registration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(id),
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val published = mutableListOf<ReaderOwnerAndInputPublicationSnapshot>()
		val barrier = ReaderOwnerAndInputPublicationBarrier(
			narrowOrVeto = { _, _ -> ReaderTransitionInputLease.ChromeOnly },
			publishAtomically = { snapshot -> published += snapshot; true }
		)

		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			barrier.publish(
				commitCommand(
					id,
					owner,
					binding,
					registration,
					ReaderTransitionInputLease.CoverActions,
					1L
				)
			)
		)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, published.single().lease)
		assertEquals(published.single(), barrier.snapshot)
		assertEquals(1, barrier.atomicCommitCount)
	}

	@Test
	fun retainedNativeClaimContinuesOnlyItsExactPublishedPredecessor() {
		val (barrier, command) = retainedNativeClaimFixture()
		val predecessor = assertNotNull(barrier.snapshot)
		val result = assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			barrier.publish(command)
		)
		assertEquals(command.requestedLease, result.finalPhysicalInputLease)
		assertEquals(predecessor.copy(lease = command.requestedLease), barrier.snapshot)
		assertEquals(2, barrier.atomicCommitCount)
	}

	@Test
	fun retainedNativeClaimCannotBorrowUnpublishedForeignOrInapplicableAuthority() {
		val (barrier, command) = retainedNativeClaimFixture()
		val predecessor = assertNotNull(barrier.snapshot)
		fun withTransition(id: ReaderTransitionId) = command.copy(
			transitionId = id,
			requestedLease = assertIs<ReaderTransitionInputLease.ClaimedGesture>(command.requestedLease)
				.copy(transitionId = id)
		)
		val id = command.transitionId
		val mismatches = listOf(
			command.copy(retainedResource = command.retainedResource.copy(
				key = command.retainedResource.key.copy(opaqueId = 99L))),
			command.copy(requestedLease = assertIs<ReaderTransitionInputLease.ClaimedGesture>(command.requestedLease)
				.copy(transitionId = id.copy(expectedBinding = ReaderExpectedPresentationBinding.Exact(command.retainedBinding)))),
			withTransition(id.copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)),
			withTransition(id.copy(expectedBinding = ReaderExpectedPresentationBinding.SemanticSuccessor(
				command.retainedBinding.copy(profileGeneration = 17L), 2L))),
			withTransition(id.copy(expectedBinding = ReaderExpectedPresentationBinding.Exact(command.retainedBinding))),
			withTransition(id.copy(expectedBinding = ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(2L))),
			withTransition(id.copy(expectedBinding = ReaderExpectedPresentationBinding.NoCommittedPresentation(2L)))
		)
		mismatches.forEach {
			assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected>(barrier.publish(it))
			assertEquals(predecessor, barrier.snapshot)
			assertEquals(1, barrier.atomicCommitCount)
		}
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected>(
			ReaderOwnerAndInputPublicationBarrier().publish(command)
		)
		val (narrowed, narrowedCommand) = retainedNativeClaimFixture(
			initialLease = ReaderTransitionInputLease.ChromeOnly
		)
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected>(narrowed.publish(narrowedCommand))
		assertEquals(ReaderTransitionInputLease.ChromeOnly, narrowed.snapshot?.lease)
		assertEquals(1, narrowed.atomicCommitCount)
	}

	@Test
	fun nativeClaimNeverAuthorizesSuccessorPublicationOrSafetyBroadening() {
		val (barrier, command) = retainedNativeClaimFixture()
		val predecessor = assertNotNull(barrier.snapshot)
		val successorResource = command.retainedResource.copy(
			key = command.retainedResource.key.copy(ownerId = ReaderTransitionResourceOwnerId.TransitionOwned(command.transitionId))
		)
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected>(barrier.publish(commitCommand(
			command.transitionId, command.retainedOwner, command.retainedBinding, successorResource, command.requestedLease, 2L
		)))
		assertEquals(predecessor, barrier.snapshot)
		assertEquals(1, barrier.atomicCommitCount)
		val (unsafe, unsafeCommand) = retainedNativeClaimFixture(narrowOrVeto = {
			if (it is ReaderTransitionInputLease.ClaimedGesture) ReaderTransitionInputLease.NativePage(
				command.retainedBinding, assertIs<ReaderPresentationFrameOwner.NativePage>(command.retainedOwner).proof.textureGeneration
			) else it
		})
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected>(unsafe.publish(unsafeCommand))
		assertEquals(1, unsafe.atomicCommitCount)
		val (safe, safeCommand) = retainedNativeClaimFixture(narrowOrVeto = {
			if (it is ReaderTransitionInputLease.ClaimedGesture) ReaderTransitionInputLease.ChromeOnly else it
		})
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(safe.publish(safeCommand))
		assertEquals(ReaderTransitionInputLease.ChromeOnly, safe.snapshot?.lease)
		assertEquals(2, safe.atomicCommitCount)
	}

	/** Public controlled UNIT barrier only, not Root/producer/display acceptance. The
	 * reflective prerequisite compiles against the old lease-only constructor and fails
	 * precisely before deeper assertions until the original callback carries its command.
	 * No installed source, physical ACK or committed Root candidate is fabricated. */
	@Test
	fun originalNarrowingReceivesExactCommandBeforeInitialSuccessorAndRetainedPublication() {
		lateinit var barrier: ReaderOwnerAndInputPublicationBarrier
		lateinit var expectedCommand: ReaderTransitionCommand
		lateinit var expectedFinal: ReaderTransitionInputLease
		var before: ReaderOwnerAndInputPublicationSnapshot? = null
		var physical: ReaderOwnerAndInputPublicationSnapshot? = null
		var narrowCalls = 0
		var publishCalls = 0
		fun requested(command: ReaderTransitionCommand): ReaderTransitionInputLease = when (command) {
			is ReaderTransitionCommand.CommitOwnerAndInputLease -> command.requestedLease
			is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> command.requestedLease
			else -> error("A407.Input.Unit.OriginalPublicationCommandRequired")
		}
		fun resource(command: ReaderTransitionCommand): ReaderTransitionResourceRegistration = when (command) {
			is ReaderTransitionCommand.CommitOwnerAndInputLease -> command.preparedFrameResource
			is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> command.retainedResource
			else -> error("A407.Input.Unit.OriginalPublicationCommandRequired")
		}
		barrier = originalCommandContextBarrier(
			narrowOrVeto = { command, lease ->
				assertTrue(command === expectedCommand && lease === requested(expectedCommand) &&
					resource(command) === resource(expectedCommand),
					"A407.Input.Unit.SoleNarrowingReceivesExactOriginalCommandLeaseAndResource")
				assertTrue(barrier.snapshot === before && narrowCalls == publishCalls,
					"A407.Input.Unit.NarrowingRunsOnceBeforeAnyPhysicalPublication")
				narrowCalls++
				expectedFinal
			},
			publishAtomically = { candidate ->
				assertTrue(narrowCalls == publishCalls + 1 && barrier.snapshot === before &&
					candidate.resource === resource(expectedCommand) && candidate.lease === expectedFinal,
					"A407.Input.Unit.PhysicalPublicationUsesOriginalResourceAndFinalNarrowedLease")
				val exactContext = when (val command = expectedCommand) {
					is ReaderTransitionCommand.CommitOwnerAndInputLease ->
						candidate.owner === command.owner && candidate.binding === command.binding
					is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease ->
						candidate.owner === command.retainedOwner && candidate.binding === command.retainedBinding
					else -> false
				}
				assertTrue(exactContext, "A407.Input.Unit.PhysicalCandidateCarriesExactOriginalOwnerAndBinding")
				publishCalls++
				physical = candidate
				true
			}
		)
		// Reuse the established Native UNIT fixture's actual original command/resource.
		// Its barrier is independent; do not transplant its snapshot into the new barrier.
		val (fixtureBarrier, retained) = retainedNativeClaimFixture()
		val fixtureSnapshot = assertNotNull(fixtureBarrier.snapshot,
			"A407.Input.Unit.ExistingNativeFixtureRequired")
		val originalId = (retained.retainedResource.key.ownerId as ReaderTransitionResourceOwnerId.TransitionOwned).transitionId
		val initial = commitCommand(originalId, retained.retainedOwner, retained.retainedBinding,
			retained.retainedResource, fixtureSnapshot.lease, 1L)
		val successorBinding = retained.retainedBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L))
		val successorId = transitionId(successorBinding, sequence = 3L).copy(
			operation = ReaderTransitionOperation.CurlClaimAndSettlement)
		val initialOwner = retained.retainedOwner as ReaderPresentationFrameOwner.NativePage
		val successorOwner = ReaderPresentationFrameOwner.NativePage(initialOwner.proof.copy(binding = successorBinding))
		val successorResource = retained.retainedResource.copy(
			key = ReaderTransitionResourceKey(successorId, ReaderTransitionResourceKind.Deck, 43L),
			retirementOrder = ReaderResourceRetirementOrder(3L, 5L, 2L))
		val successor = commitCommand(successorId, successorOwner, successorBinding, successorResource,
			ReaderTransitionInputLease.NativePage(successorBinding, successorOwner.proof.textureGeneration), 3L)
		fun publishExpected(command: ReaderTransitionCommand, finalLease: ReaderTransitionInputLease) {
			expectedCommand = command
			expectedFinal = finalLease
			before = barrier.snapshot
			val result = when (command) {
				is ReaderTransitionCommand.CommitOwnerAndInputLease -> barrier.publish(command)
				is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> barrier.publish(command)
				else -> error("A407.Input.Unit.OriginalPublicationCommandRequired")
			}
			assertTrue(result is paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied,
				"A407.Input.Unit.OriginalContextPublicationApplied")
			val applied = result as paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied
			assertTrue(applied.finalPhysicalInputLease === finalLease && barrier.snapshot === physical &&
				barrier.snapshot?.lease === finalLease && narrowCalls == publishCalls &&
				barrier.atomicCommitCount == publishCalls && !barrier.observedPartialApplication,
				"A407.Input.Unit.NoPostAcceptanceLeaseUpgradeOrSecondNarrowing")
		}
		assertTrue(barrier.snapshot == null, "A407.Input.Unit.InitialHasNoCommittedPredecessor")
		publishExpected(initial, initial.requestedLease)
		publishExpected(retained, retained.requestedLease)
		assertTrue(barrier.snapshot?.binding != successor.binding,
			"A407.Input.Unit.SuccessorHasNoAlreadyCommittedMatchingBinding")
		publishExpected(successor, ReaderTransitionInputLease.ChromeOnly)
		assertTrue(narrowCalls == 3 && publishCalls == 3,
			"A407.Input.Unit.OneNarrowingAndOneWritePerOriginalCommand")

		var rejectedPhysicalCalls = 0
		val incompatible = originalCommandContextBarrier({ command, lease ->
			assertTrue(command === successor && lease === successor.requestedLease,
				"A407.Input.Unit.ForeignLeaseControlReceivesOriginalContext")
			ReaderTransitionInputLease.NativePage(retained.retainedBinding, initialOwner.proof.textureGeneration)
		}, { rejectedPhysicalCalls++; true })
		assertTrue(incompatible.publish(successor) is paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected &&
			incompatible.snapshot == null && incompatible.atomicCommitCount == 0 && rejectedPhysicalCalls == 0,
			"A407.Input.Unit.IncompatibleFinalLeaseRejectedBeforePhysicalPublication")
		val chromeRequest = successor.copy(requestedLease = ReaderTransitionInputLease.ChromeOnly)
		val noUpgrade = originalCommandContextBarrier({ command, lease ->
			assertTrue(command === chromeRequest && lease === chromeRequest.requestedLease,
				"A407.Input.Unit.BroadeningControlReceivesOriginalContext")
			successor.requestedLease
		}, { rejectedPhysicalCalls++; true })
		assertTrue(noUpgrade.publish(chromeRequest) is paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected &&
			noUpgrade.snapshot == null && noUpgrade.atomicCommitCount == 0 && rejectedPhysicalCalls == 0,
			"A407.Input.Unit.CompatibleButBroaderFinalLeaseRejectedBeforePhysicalPublication")

		lateinit var reentrant: ReaderOwnerAndInputPublicationBarrier
		var reentryNarrowCalls = 0
		var reentryPhysicalCalls = 0
		reentrant = originalCommandContextBarrier({ command, lease ->
			reentryNarrowCalls++
			assertTrue((command === initial || command === successor) && lease === requested(command),
				"A407.Input.Unit.ReentryKeepsEachExactOriginalCommandContext")
			if (command === initial) {
				assertTrue(reentrant.snapshot == null && reentryPhysicalCalls == 0,
					"A407.Input.Unit.ReentryStartsInsideOriginalPrepublishNarrowing")
				assertTrue(reentrant.publish(successor) is paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied,
					"A407.Input.Unit.NewerReentrantOriginalPublicationApplied")
			}
			lease
		}, { candidate ->
			assertTrue(candidate.owner === successor.owner && candidate.resource === successor.preparedFrameResource &&
				candidate.lease === successor.requestedLease,
				"A407.Input.Unit.OnlyNewerReentrantCandidateReachesPhysicalPublication")
			reentryPhysicalCalls++
			true
		})
		assertTrue(reentrant.publish(initial) is paige.navic.reader.ReaderOwnerAndInputPublicationResult.Rejected &&
			reentryNarrowCalls == 2 && reentryPhysicalCalls == 1 && reentrant.atomicCommitCount == 1 &&
			reentrant.snapshot?.resource === successor.preparedFrameResource &&
			reentrant.snapshot?.lease === successor.requestedLease,
			"A407.Input.Unit.StaleNarrowingReturnCannotReplaceNewerPublication")
	}

	private fun originalCommandContextBarrier(
		narrowOrVeto: (ReaderTransitionCommand, ReaderTransitionInputLease) -> ReaderTransitionInputLease,
		publishAtomically: (ReaderOwnerAndInputPublicationSnapshot) -> Boolean
	): ReaderOwnerAndInputPublicationBarrier {
		val constructor = ReaderOwnerAndInputPublicationBarrier::class.java.declaredConstructors.singleOrNull {
			!it.isSynthetic && it.parameterTypes.contentEquals(arrayOf(Function2::class.java, Function1::class.java))
		}
		assertTrue(constructor != null,
			"A407.Input.Prerequisite.OriginalCommandContextNarrowingMissing")
		val barrier = runCatching {
			constructor!!.apply { isAccessible = true }.newInstance(narrowOrVeto, publishAtomically)
		}.getOrNull()
		assertTrue(barrier is ReaderOwnerAndInputPublicationBarrier,
			"A407.Input.Prerequisite.OriginalCommandContextNarrowingMissing")
		return barrier as ReaderOwnerAndInputPublicationBarrier
	}

	private fun retainedNativeClaimFixture(
		initialLease: ReaderTransitionInputLease? = null,
		narrowOrVeto: (ReaderTransitionInputLease) -> ReaderTransitionInputLease = { it }
	): Pair<ReaderOwnerAndInputPublicationBarrier, ReaderTransitionCommand.PublishRetainedOwnerAndInputLease> {
		val binding = binding()
		val initialId = transitionId(binding, sequence = 1L).copy(operation = ReaderTransitionOperation.BootstrapNativePage)
		val owner = ReaderPresentationFrameOwner.NativePage(paige.navic.reader.ReaderNativePagePresentationProof(
			binding, ReaderPresentationToken(23L), 29L, 1200, 800, 11L, 13L
		))
		val resource = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(initialId, ReaderTransitionResourceKind.Deck, 31L),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val barrier = ReaderOwnerAndInputPublicationBarrier(narrowOrVeto = { _, lease -> narrowOrVeto(lease) })
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(barrier.publish(commitCommand(
			initialId, owner, binding, resource,
			initialLease ?: ReaderTransitionInputLease.NativePage(binding, owner.proof.textureGeneration), 1L
		)))
		val turn = transitionId(binding, sequence = 2L).copy(
			operation = ReaderTransitionOperation.CurlClaimAndSettlement,
			expectedBinding = ReaderExpectedPresentationBinding.SemanticSuccessor(binding, 2L)
		)
		return barrier to ReaderTransitionCommand.PublishRetainedOwnerAndInputLease(
			turn, owner, binding, resource,
			ReaderTransitionInputLease.ClaimedGesture(turn, paige.navic.reader.ReaderTransitionGestureId(37L)),
			paige.navic.reader.ReaderOwnerAndInputPublicationIdentity(2L)
		)
	}

	@Test
	fun physicalNativePolicyRequiresExactPublishedBindingAndMaterialProof() {
		val (barrier, _) = retainedNativeClaimFixture()
		val published = assertNotNull(barrier.snapshot, "A407.Policy.NativePublication")
		val owner = assertIs<ReaderPresentationFrameOwner.NativePage>(published.owner,
			"A407.Policy.NativeOwner")
		val exact = assertIs<paige.navic.reader.ReaderPresentationInputPolicy.NativePage>(
			paige.navic.reader.readerTransitionPresentationInputPolicy(owner, published.binding, published.lease),
			"A407.Policy.NativeExact")
		assertTrue(exact.policy.newPointer == paige.navic.reader.ReaderPageNewPointerDecision.Accept &&
			exact.policy.continueActivePointer && !exact.policy.continueSettlement,
			"A407.Policy.NativeNewPointer")
		val wrongBinding = published.binding.copy(profileGeneration = published.binding.profileGeneration + 1L)
		val wrongLeases = listOf(
			ReaderTransitionInputLease.NativePage(wrongBinding, owner.proof.textureGeneration),
			ReaderTransitionInputLease.NativePage(published.binding, owner.proof.textureGeneration + 1L))
		for (lease in wrongLeases) assertTrue(
			paige.navic.reader.readerTransitionPresentationInputPolicy(owner, published.binding, lease) ==
				paige.navic.reader.ReaderPresentationInputPolicy.RecoveryOnly,
			"A407.Policy.NativeForeignLease")
		assertTrue(paige.navic.reader.readerTransitionPresentationInputPolicy(owner, wrongBinding, published.lease) ==
			paige.navic.reader.ReaderPresentationInputPolicy.RecoveryOnly, "A407.Policy.NativeForeignBinding")
		assertTrue(paige.navic.reader.readerTransitionPresentationInputPolicy(owner, published.binding,
			ReaderTransitionInputLease.ChromeOnly) == paige.navic.reader.ReaderPresentationInputPolicy.ChromeOnly,
			"A407.Policy.PhysicalNarrowing")
		assertTrue(paige.navic.reader.readerTransitionPresentationInputPolicy(owner, published.binding,
			ReaderTransitionInputLease.None) == paige.navic.reader.ReaderPresentationInputPolicy.RecoveryOnly,
			"A407.Policy.PhysicalVeto")
	}

	@Test
	fun physicalRetainedClaimPolicyContinuesOnlyOriginalStreamWithoutNewPointerAuthority() {
		val (barrier, command) = retainedNativeClaimFixture()
		val applied = assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(barrier.publish(command),
			"A407.Policy.OriginalClaimApplied")
		val policy = assertIs<paige.navic.reader.ReaderPresentationInputPolicy.NativePage>(
			paige.navic.reader.readerTransitionPresentationInputPolicy(applied.publishedOwner,
				applied.publishedBinding, applied.finalPhysicalInputLease), "A407.Policy.OriginalClaim")
		assertTrue(policy.policy.newPointer is paige.navic.reader.ReaderPageNewPointerDecision.Reject &&
			policy.policy.continueActivePointer && policy.policy.continueSettlement,
			"A407.Policy.OriginalStreamOnly")
		val claim = assertIs<ReaderTransitionInputLease.ClaimedGesture>(applied.finalPhysicalInputLease,
			"A407.Policy.OriginalClaimLease")
		val wrong = claim.copy(transitionId = claim.transitionId.copy(
			operation = ReaderTransitionOperation.ExternalSemanticRelocation))
		assertTrue(paige.navic.reader.readerTransitionPresentationInputPolicy(applied.publishedOwner,
			applied.publishedBinding, wrong) == paige.navic.reader.ReaderPresentationInputPolicy.RecoveryOnly,
			"A407.Policy.ForeignOperationCannotContinue")
		val initial = claim.copy(transitionId = claim.transitionId.copy(
			expectedBinding = ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(2L)))
		assertTrue(paige.navic.reader.readerTransitionPresentationInputPolicy(applied.publishedOwner,
			applied.publishedBinding, initial) == paige.navic.reader.ReaderPresentationInputPolicy.RecoveryOnly,
			"A407.Policy.InitialCannotBorrowClaim")
	}

	@Test
	fun semanticRegistryAndActiveSlotFreezeAsExactRestorableSourceRows() {
		val tokens = ReaderLegacySourceLocalTokenAllocator()
		val registry = ReaderSemanticExecutableRequestRegistry(
			readerSessionGeneration = 3L,
			tokenAllocator = tokens
		)
		var delayedCallback: ((paige.navic.reader.ReaderPresentationEventReceipt) -> Unit)? = null
		var delayedOrigin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand? = null
		val activeHandle = registry.register { origin, _, callback ->
			delayedOrigin = origin
			delayedCallback = callback
			ReaderSemanticExecutableResult.Accepted
		}
		val pendingHandle = registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }
		val executor = ReaderSemanticCommandExecutor(registry, tokens)
		val id = transitionId().copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, activeHandle),
					activeHandle
				),
				onRegistration = { }
			) { }
		)
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 3L,
			freezeToken = ReaderLegacyFreezeToken(71L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			executor.freezeForTransitionActivation(domain)
		)
		val rows = executor.snapshotFrozenOwnership()
		assertEquals(2, rows.size)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		assertTrue(rows.all {
			it.physicalIdentity.source == ReaderLegacyInventorySource.SemanticCommandSlot
		})
		assertEquals(
			ReaderSemanticCommandResult.RejectedBeforeMutation(
				paige.navic.reader.ReaderTransitionFailureReason.PortRejected
			),
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, pendingHandle),
					pendingHandle
				),
				onRegistration = { }
			) { }
		)
		assertEquals(rows, executor.snapshotFrozenOwnership())
		assertFailsWith<IllegalStateException> { registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted } }
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				executor.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		delayedCallback?.invoke(semanticReceipt(checkNotNull(delayedOrigin)))
		assertEquals(0, executor.activeSlotCount)
		assertEquals(0, registry.activeHandleCount)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			executor.restoreAfterTransitionActivation(domain)
		)
		assertTrue(registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }.value > 0L)
	}

	@Test
	fun registrationRetiredReentrantlyBeforeInvocationRejectsWithoutExecutableStart() {
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		var executableStarts = 0
		var receiptCalls = 0
		val handle = registry.register { _, mutationStart, _ ->
			executableStarts += 1
			mutationStart.mutationStarted()
			ReaderSemanticExecutableResult.Accepted
		}
		val executor = ReaderSemanticCommandExecutor(registry)
		val id = transitionId().copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
		var registrationCalls = 0
		val result = executor.synchronize(
			ReaderTransitionCommand.RequestSemanticSynchronization(
				id,
				ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
				handle
			),
			onRegistration = { registration ->
				registrationCalls += 1
				assertTrue(registry.activeHandleCount == 1, "Registration follows exact bounded request custody, before any executable start")
				assertTrue(executor.activeSlotCount == 1, "The real slot must exist before registration delivery")
				registration.retire()
				assertTrue(executor.activeSlotCount == 0, "Exact registration retirement must remove its slot")
			},
			onReceipt = { receiptCalls += 1 }
		)

		assertTrue(registrationCalls == 1, "Registration delivery must occur exactly once")
		assertTrue(executableStarts == 0, "Retirement before invocation must prevent executable side effects")
		assertTrue(result == ReaderSemanticCommandResult.RejectedBeforeMutation(
			paige.navic.reader.ReaderTransitionFailureReason.PortRejected
		), "An unstarted retired command must report rejection before mutation")
		assertTrue(receiptCalls == 0, "Retired unstarted execution must not deliver a receipt")
		assertTrue(registry.activeHandleCount == 0, "The actual request handle must be consumed once")
		assertTrue(executor.activeSlotCount == 0, "No retired slot may remain active")
	}

	@Test
	fun realRetryLeaseRejectsWrongSessionFrozenOwnerAndDisposedHandle() {
		val registry = ReaderSemanticExecutableRequestRegistry(3L)
		val executor = ReaderSemanticCommandExecutor(registry)
		var starts = 0
		val origins = mutableListOf<paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand>()
		val handle = registry.register { origin, _, _ ->
			starts += 1; origins += origin; ReaderSemanticExecutableResult.Rejected
		}
		val id = transitionId(sequence = 1L).copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
		fun command(next: ReaderTransitionId) = ReaderTransitionCommand.RequestSemanticSynchronization(next,
			ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle), handle)
		lateinit var custody: ReaderSemanticCommandRegistration
		executor.synchronize(command(id), { custody = it }) {}
		custody.retireForRetry()
		assertTrue(registry.activeHandleCount == 1 && executor.activeSlotCount == 0,
			"Retry custody is one real retained request without a physical slot")
		assertIs<ReaderSemanticCommandResult.RejectedBeforeMutation>(
			executor.synchronize(command(id.copy(readerSessionGeneration = 7L)), {}) {})
		assertTrue(starts == 1 && registry.activeHandleCount == 1,
			"Wrong session cannot execute or consume another session's exact request")
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(71L))
		assertEquals(ReaderPortCommandResult.Accepted, executor.freezeForTransitionActivation(domain))
		val rows = executor.snapshotFrozenOwnership()
		assertTrue(rows.size == 1 && rows.single().origin == ReaderLegacyResourceOrigin.Owned,
			"Frozen retained retry custody must be inventoried truthfully with its authentic token")
		assertIs<ReaderSemanticCommandResult.RejectedBeforeMutation>(executor.synchronize(command(id), {}) {})
		assertTrue(starts == 1, "Frozen owner cannot replay work")
		assertEquals(ReaderPortCommandResult.Accepted, executor.drainFrozenOwnership(rows.single().physicalIdentity) {})
		assertEquals(ReaderPortCommandResult.Accepted, executor.restoreAfterTransitionActivation(domain))
		assertIs<ReaderSemanticCommandResult.RejectedBeforeMutation>(executor.synchronize(command(id), {}) {})
		assertTrue(starts == 1 && registry.activeHandleCount == 0 && executor.activeSlotCount == 0,
			"Frozen retired retry work must not be restored or resurrected")
	}

	@Test
	fun oldRetryCustodyCannotDisposeNewExecutorSlotForSameRequest() {
		val registry = ReaderSemanticExecutableRequestRegistry(3L)
		val executor = ReaderSemanticCommandExecutor(registry)
		var starts = 0
		val handle = registry.register { _, _, _ ->
			starts += 1
			if (starts == 1) ReaderSemanticExecutableResult.Rejected else ReaderSemanticExecutableResult.Accepted
		}
		val id = transitionId(sequence = 1L).copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
		fun command(next: ReaderTransitionId) = ReaderTransitionCommand.RequestSemanticSynchronization(next,
			ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle), handle)
		lateinit var first: ReaderSemanticCommandRegistration
		executor.synchronize(command(id), { first = it }) {}
		first.retireForRetry()
		lateinit var second: ReaderSemanticCommandRegistration
		val next = id.copy(sequence = 2L, parent = id.parentIdentity())
		assertEquals(ReaderSemanticCommandResult.Accepted, executor.synchronize(command(next), { second = it }) {})
		first.retire(); first.retireForRetry()
		assertTrue(starts == 2 && executor.activeSlotCount == 1 && registry.activeHandleCount == 1,
			"Stale retry registration must not retire newer exact slot or its request lease")
		second.retire()
		assertTrue(executor.activeSlotCount == 0 && registry.activeHandleCount == 0,
			"Terminal retirement disposes only the matching current request lease")
	}

	@Test
	fun synchronousSemanticCallbacksRemainObservableAfterSlotRetirement() {
		val trace = mutableListOf<String>()
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val origins = mutableListOf<paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand>()
		val receipts = mutableListOf<paige.navic.reader.ReaderPresentationEventReceipt>()
		val handle: ReaderSemanticRequestHandle = registry.register { origin, _, callback ->
			trace += "invoke"
			origins += origin
			val receipt = semanticReceipt(origin)
			callback(receipt)
			callback(receipt)
			ReaderSemanticExecutableResult.Accepted
		}
		val executor = ReaderSemanticCommandExecutor(registry)
		val id = transitionId().copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)

		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
					handle
				),
				onRegistration = { }
			) { receipt -> receipts += receipt }
		)
		assertEquals(listOf("invoke"), trace)
		assertEquals(1, origins.size)
		assertEquals(id, origins.single().transitionId)
		assertEquals(2, receipts.size)
		assertTrue(receipts.all { it.originatingTransitionId == id })
		assertEquals(0, registry.activeHandleCount)
		assertEquals(0, executor.activeSlotCount)
	}

	@Test
	fun retiredSemanticRegistrationCannotRetireNewerSlotWithSameTransitionIdentity() {
		lateinit var oldOrigin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var oldCallback: (paige.navic.reader.ReaderPresentationEventReceipt) -> Unit
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val firstHandle = registry.register { origin, _, callback ->
			oldOrigin = origin
			oldCallback = callback
			ReaderSemanticExecutableResult.Accepted
		}
		val secondHandle = registry.register { _, _, _ ->
			ReaderSemanticExecutableResult.Accepted
		}
		val executor = ReaderSemanticCommandExecutor(registry)
		val id = transitionId().copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
		lateinit var firstRegistration: ReaderSemanticCommandRegistration
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, firstHandle),
					firstHandle
				),
				onRegistration = { firstRegistration = it },
				onReceipt = { }
			)
		)
		assertEquals(1, executor.activeSlotCount)
		firstRegistration.retire()
		firstRegistration.retire()
		assertEquals(0, executor.activeSlotCount)
		lateinit var secondRegistration: ReaderSemanticCommandRegistration
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, secondHandle),
					secondHandle
				),
				onRegistration = { secondRegistration = it },
				onReceipt = { }
			)
		)
		assertEquals(1, executor.activeSlotCount)

		oldCallback(semanticReceipt(oldOrigin))

		assertEquals(1, executor.activeSlotCount)
		secondRegistration.retire()
		assertEquals(0, executor.activeSlotCount)
	}

	@Test
	fun workerMutationStartBeforeRejectOrThrowAlwaysClassifiesPostMutation() {
		val worker = Executors.newSingleThreadExecutor()
		try {
			repeat(16) { cycle ->
				listOf(false, true).forEach { throws ->
					val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
					val mutationRecorded = CountDownLatch(1)
					lateinit var mutationFuture: java.util.concurrent.Future<*>
					val handle = registry.register { _, mutationStart, _ ->
						mutationFuture = worker.submit {
							mutationStart.mutationStarted()
							mutationRecorded.countDown()
						}
						assertTrue(mutationRecorded.await(2L, TimeUnit.SECONDS))
						if (throws) error("private semantic detail")
						ReaderSemanticExecutableResult.Rejected
					}
					val executor = ReaderSemanticCommandExecutor(registry)
					lateinit var registration: ReaderSemanticCommandRegistration
					val id = transitionId(sequence = cycle.toLong() + 1L).copy(
						operation = ReaderTransitionOperation.ExternalSemanticRelocation
					)

					val result = executor.synchronize(
						ReaderTransitionCommand.RequestSemanticSynchronization(
							id,
							ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
							handle
						),
						onRegistration = { registration = it },
						onReceipt = { }
					)

					assertEquals(
						if (throws) {
							ReaderSemanticCommandResult.ThrewAfterMutationStarted
						} else {
							ReaderSemanticCommandResult.RejectedAfterMutationStarted
						},
						result
					)
					mutationFuture.get(2L, TimeUnit.SECONDS)
					registration.retire()
					assertEquals(0, executor.activeSlotCount)
				}
			}
		} finally {
			worker.shutdownNow()
			assertTrue(worker.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun callbackAndExplicitRetirementRaceWithFreezeSnapshotsWithoutCorruption() {
		val workers = Executors.newFixedThreadPool(3)
		try {
			repeat(32) { cycle ->
				val tokens = ReaderLegacySourceLocalTokenAllocator()
				val registry = ReaderSemanticExecutableRequestRegistry(3L, tokens)
				lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
				lateinit var callback: (paige.navic.reader.ReaderPresentationEventReceipt) -> Unit
				val handle = registry.register { suppliedOrigin, _, suppliedCallback ->
					origin = suppliedOrigin
					callback = suppliedCallback
					ReaderSemanticExecutableResult.Accepted
				}
				val executor = ReaderSemanticCommandExecutor(registry, tokens)
				lateinit var registration: ReaderSemanticCommandRegistration
				val id = transitionId(sequence = cycle.toLong() + 1L).copy(
					operation = ReaderTransitionOperation.ExternalSemanticRelocation
				)
				assertEquals(
					ReaderSemanticCommandResult.Accepted,
					executor.synchronize(
						ReaderTransitionCommand.RequestSemanticSynchronization(
							id,
							ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
							handle
						),
						onRegistration = { registration = it },
						onReceipt = { }
					)
				)
				val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(cycle.toLong() + 1L))
				val start = CyclicBarrier(4)
				val futures = listOf(
					workers.submit {
						start.await(2L, TimeUnit.SECONDS)
						callback(semanticReceipt(origin))
					},
					workers.submit {
						start.await(2L, TimeUnit.SECONDS)
						registration.retire()
					},
					workers.submit {
						start.await(2L, TimeUnit.SECONDS)
						assertEquals(
							ReaderPortCommandResult.Accepted,
							executor.freezeForTransitionActivation(domain)
						)
					}
				)
				start.await(2L, TimeUnit.SECONDS)
				repeat(32) {
					val snapshot = executor.retirementSnapshot()
					assertTrue(snapshot.activeSlotCount in 0..1)
					executor.snapshotFrozenOwnership()
				}
				futures.forEach { it.get(2L, TimeUnit.SECONDS) }
				assertEquals(0, executor.activeSlotCount)
				assertEquals(0, executor.retirementSnapshot().activeSlotCount)
				assertEquals(
					ReaderPortCommandResult.Accepted,
					executor.restoreAfterTransitionActivation(domain)
				)
			}
		} finally {
			workers.shutdownNow()
			assertTrue(workers.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun oldLeaseRetirementRacingNewSameTransitionSlotNeverRemovesNewSlot() {
		val workers = Executors.newFixedThreadPool(2)
		try {
			repeat(32) { cycle ->
				val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
				val oldHandle = registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }
				val newHandle = registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }
				val executor = ReaderSemanticCommandExecutor(registry)
				val id = transitionId(sequence = cycle.toLong() + 1L).copy(
					operation = ReaderTransitionOperation.ExternalSemanticRelocation
				)
				lateinit var oldRegistration: ReaderSemanticCommandRegistration
				executor.synchronize(
					ReaderTransitionCommand.RequestSemanticSynchronization(
						id,
						ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, oldHandle),
						oldHandle
					),
					onRegistration = { oldRegistration = it },
					onReceipt = { }
				)
				lateinit var newRegistration: ReaderSemanticCommandRegistration
				val start = CyclicBarrier(3)
				val retire = workers.submit {
					start.await(2L, TimeUnit.SECONDS)
					oldRegistration.retire()
				}
				val admit = workers.submit {
					start.await(2L, TimeUnit.SECONDS)
					assertEquals(
						ReaderSemanticCommandResult.Accepted,
						executor.synchronize(
							ReaderTransitionCommand.RequestSemanticSynchronization(
								id,
								ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, newHandle),
								newHandle
							),
							onRegistration = { newRegistration = it },
							onReceipt = { }
						)
					)
				}
				start.await(2L, TimeUnit.SECONDS)
				retire.get(2L, TimeUnit.SECONDS)
				admit.get(2L, TimeUnit.SECONDS)
				oldRegistration.retire()
				assertEquals(1, executor.activeSlotCount)
				newRegistration.retire()
				assertEquals(0, executor.activeSlotCount)
			}
		} finally {
			workers.shutdownNow()
			assertTrue(workers.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun presentationReceiptRequiresExplicitCommandOrNonCommandOrigin() {
		assertTrue(
			paige.navic.reader.ReaderPresentationEventReceipt::class.java.declaredConstructors.none {
				it.isSynthetic
			},
			"Receipt origin cannot be supplied by a default-argument constructor"
		)
	}

	@Test
	fun everySemanticIntentCarriesOneExecutableRequestHandle() {
		val intentClasses = listOf(
			paige.navic.reader.ReaderPageTurnIntent::class.java,
			paige.navic.reader.ReaderExternalRelocationIntent::class.java,
			paige.navic.reader.ReaderCoverEntryIntent::class.java
		)
		intentClasses.forEach { intentClass ->
			assertTrue(
				intentClass.declaredFields.any { it.name == "requestHandle" },
				intentClass.name
			)
		}
		val semanticIntentConstructor = paige.navic.reader.ReaderExternalRelocationIntent::class.java
			.declaredConstructors
			.single { it.parameterCount == 2 }
		semanticIntentConstructor.isAccessible = true
		val semanticIntent = semanticIntentConstructor.newInstance(
				ReaderExternalRelocationSource.Toc,
				53L
			)
		val constructor = ReaderTransitionCommand.RequestSemanticSynchronization::class.java
			.declaredConstructors
			.single { it.parameterCount == 3 }
		constructor.isAccessible = true
		assertTrue(runCatching {
			constructor.newInstance(
				transitionId(),
				semanticIntent,
				null
			)
		}.isFailure)
	}

	@Test
	fun semanticSlotFenceCompactsContiguousRetirementInsteadOfExhaustingHistory() {
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val executor = ReaderSemanticCommandExecutor(registry)
		repeat(40) { index ->
			val handle = registry.register { origin, _, callback ->
				callback(semanticReceipt(origin))
				ReaderSemanticExecutableResult.Accepted
			}
			val id = transitionId(sequence = index.toLong() + 1L)
			assertEquals(
				ReaderSemanticCommandResult.Accepted,
				executor.synchronize(
					ReaderTransitionCommand.RequestSemanticSynchronization(
						id,
						ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
						handle
					),
					onRegistration = { }
				) {}
			)
		}
		assertEquals(0, executor.activeSlotCount)
		assertEquals(40L, executor.retirementSnapshot().contiguousRetiredThrough)
		assertTrue(executor.retirementSnapshot().outOfOrderRetiredSequences.isEmpty())
	}

	@Test
	fun activatedSemanticDispatchRegistersPrivateRequestAndSuppressesLegacyUntilCommand() {
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val gateway = ReaderTransitionGateway()
		val facts = mutableListOf<paige.navic.reader.ReaderTransitionFact>()
		var privateRequestCalls = 0
		var legacyCalls = 0
		gateway.attachActivated(
			enqueue = facts::add,
			registerSemanticRequest = registry::register
		)

		val result = gateway.dispatchSemanticActivatedOrLegacy(
			intent = { handle ->
				ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle)
			},
			request = { origin, _, callback ->
				privateRequestCalls += 1
				callback(semanticReceipt(origin))
				ReaderSemanticExecutableResult.Accepted
			},
			activatedResult = { "pending" },
			legacyDispatch = {
				legacyCalls += 1
				"legacy"
			}
		)

		assertEquals("pending", result)
		assertEquals(0, privateRequestCalls)
		assertEquals(0, legacyCalls)
		val intentFact = assertIs<paige.navic.reader.ReaderTransitionFact.Intent>(facts.single())
		val semanticIntent = assertIs<ReaderExternalRelocationIntent>(intentFact.intent)
		val transitionId = transitionId().copy(
			operation = ReaderTransitionOperation.ExternalSemanticRelocation
		)
		val receipts = mutableListOf<paige.navic.reader.ReaderPresentationEventReceipt>()
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			ReaderSemanticCommandExecutor(registry).synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					transitionId,
					semanticIntent,
					semanticIntent.requestHandle
				),
				onRegistration = { },
				onReceipt = receipts::add
			)
		)
		assertEquals(1, privateRequestCalls)
		assertEquals(0, legacyCalls)
		assertEquals(transitionId, receipts.single().originatingTransitionId)
	}

	@Test
	fun activatedGatewayNeverContinuesToLegacyConsequence() {
		val gateway = ReaderTransitionGateway()
		val facts = mutableListOf<paige.navic.reader.ReaderTransitionFact>()
		var legacyCalls = 0
		gateway.attachActivated(
			enqueue = { facts += it },
			registerSemanticRequest = ReaderSemanticExecutableRequestRegistry(3L)::register
		)

		val result = gateway.dispatchActivatedOrLegacy(
			fact = paige.navic.reader.ReaderTransitionFact.Retry(null),
			activatedResult = { "activated" },
			legacyDispatch = { legacyCalls += 1; "legacy" }
		)

		assertEquals("activated", result)
		assertEquals(0, legacyCalls)
		assertEquals(1, facts.size)
		assertEquals(ReaderTransitionGatewayMode.Activated, gateway.mode)
	}

	@Test
	fun preparedFrameDoesNotPublishSuccessBeforeCombinedCommitAcknowledgement() {
		val binding = binding()
		val predecessorId = transitionId(binding, sequence = 6L).copy(
			operation = ReaderTransitionOperation.BootstrapNativePage
		)
		val predecessor = ReaderPresentationFrameOwner.NativePage(
			paige.navic.reader.ReaderNativePagePresentationProof(
				binding = binding,
				transitionToken = ReaderPresentationToken(23L),
				presentedFrame = 29L,
				viewportWidth = 1200,
				viewportHeight = 800,
				rasterGeneration = 11L,
				textureGeneration = 13L
			)
		)
		val predecessorKey = ReaderTransitionResourceKey(
			predecessorId,
			ReaderTransitionResourceKind.Deck,
			31L
		)
		val id = transitionId(binding)
		val registration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.FrameHandoff, 47L),
			ReaderResourceRetirementOrder(3L, 5L, 2L)
		)
		val geometry = paige.navic.reader.ReaderTransitionFrameGeometry(3L, 5L, 0, 0, 1200, 800)
		val specification = paige.navic.reader.ReaderTransitionFrameTargetSpecification.ShellCover(
			id,
			3L,
			binding.publicationGeneration,
			binding,
			paige.navic.reader.ReaderShellCoverHostToken(37L),
			41L,
			binding.viewportGeneration,
			geometry,
			1L
		)
		val target = paige.navic.reader.ReaderTransitionFrameTarget.ShellCover(
			paige.navic.reader.ReaderTransitionFrameTargetHandle(
				3L,
				binding.publicationGeneration,
				43L
			),
			specification,
			registration
		)
		val basePhase = paige.navic.reader.ReaderTransitionLivenessTable.phase(
			id,
			paige.navic.reader.ReaderTransitionPhaseKind.AwaitingProof,
			predecessor
		)
		val phase = basePhase.copy(
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(paige.navic.reader.ReaderTransitionProofKind.PreparedFrame),
				callbackSources = setOf(
					paige.navic.reader.ReaderTransitionFactKind.PreparedFrame,
					paige.navic.reader.ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation,
					paige.navic.reader.ReaderTransitionCommandStage.TimerBinding
				)
			)
		)
		val journal = paige.navic.reader.ReaderTransitionJournal(
			active = paige.navic.reader.ReaderActiveTransition(
				id = id,
				phase = phase,
				pendingCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation
				),
				predecessorResourceKey = predecessorKey,
				frameTarget = target
			),
			committed = paige.navic.reader.ReaderCommittedTransition(
				predecessorId,
				predecessor,
				binding,
				predecessorKey,
				ReaderTransitionResourceRegistration(
					predecessorKey,
					ReaderResourceRetirementOrder(3L, 5L, 1L)
				)
			)
		)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(37L), binding, 41L, 43L, 1200, 800
			)
		)

		val reduction = journal.reduce(
			paige.navic.reader.ReaderTransitionFact.PreparedFrame(
				id,
				target,
				owner,
				registration
			)
		)

		assertEquals(
			paige.navic.reader.ReaderTransitionPhaseKind.Committing,
			reduction.state.active?.phase?.kind
		)
		assertEquals(null, reduction.state.lastOutcome)
		assertEquals(predecessor, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(reduction.state.committed).committed.owner)
		assertEquals(1, reduction.commands.count {
			it is ReaderTransitionCommand.CommitOwnerAndInputLease
		})
		assertTrue(reduction.commands.none { it is ReaderTransitionCommand.RequestResourceRelease })
	}

	@Test
	fun rejectedCombinedCommitRetainsPredecessorAndReleasesOnlySuccessor() {
		val fixture = preparedCommitFixture()
		val rejected = fixture.committingState.reduce(
			paige.navic.reader.ReaderTransitionFact.OwnerAndInputPublicationRejected(
				fixture.id,
				paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(
					fixture.target.handle,
					fixture.successorRegistration
				),
				fixture.commitCommand.publicationIdentity,
				paige.navic.reader.ReaderTransitionFailureReason.AtomicPublicationRejected
			)
		)

		assertEquals(null, rejected.state.active)
		assertIs<paige.navic.reader.ReaderTransitionOutcome.Failed>(rejected.state.lastOutcome)
		assertEquals(fixture.predecessorOwner, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(rejected.state.committed).committed.owner)
		assertEquals(
			listOf(fixture.successorRegistration.key),
			rejected.commands.filterIsInstance<ReaderTransitionCommand.RequestResourceRelease>().map { it.key }
		)
		assertTrue(rejected.commands.none {
			it is ReaderTransitionCommand.RequestResourceRelease && it.key == fixture.predecessorKey
		})
	}

	@Test
	fun acceptedCombinedCommitPublishesSuccessBeforePredecessorRelease() {
		val fixture = preparedCommitFixture()
		val accepted = fixture.committingState.reduce(
			paige.navic.reader.ReaderTransitionFact.OwnerAndInputPublicationApplied(
				fixture.id,
				paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(
					fixture.target.handle,
					fixture.successorRegistration
				),
				fixture.successorOwner,
				fixture.binding,
				fixture.commitCommand.requestedLease,
				fixture.commitCommand.publicationIdentity
			)
		)

		assertEquals(null, accepted.state.active)
		val outcome = assertIs<paige.navic.reader.ReaderTransitionOutcome.Succeeded>(
			accepted.state.lastOutcome
		)
		assertEquals(fixture.successorOwner, outcome.committedOwner)
		assertEquals(fixture.successorOwner, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(accepted.state.committed).committed.owner)
		assertEquals(
			listOf(fixture.predecessorKey),
			accepted.commands.filterIsInstance<ReaderTransitionCommand.RequestResourceRelease>().map { it.key }
		)
	}

	@Test
	fun staleOrWrongCombinedCommitAcknowledgementIsInert() {
		val fixture = preparedCommitFixture()
		val wrongIdentity = paige.navic.reader.ReaderOwnerAndInputPublicationIdentity(
			fixture.commitCommand.publicationIdentity.value + 1L
		)
		val stale = fixture.committingState.reduce(
			paige.navic.reader.ReaderTransitionFact.OwnerAndInputPublicationApplied(
				fixture.id,
				paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(
					fixture.target.handle,
					fixture.successorRegistration
				),
				fixture.successorOwner,
				fixture.binding,
				fixture.commitCommand.requestedLease,
				wrongIdentity
			)
		)

		assertEquals(fixture.committingState, stale.state)
		assertTrue(stale.commands.isEmpty())
		assertEquals(fixture.predecessorOwner, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(stale.state.committed).committed.owner)
	}

	@Test
	fun bindingOnlyFrameRequestIsUnrepresentable() {
		val requestClass = ReaderTransitionCommand.RequestFramePresentation::class.java
		val targetClass = protocolClass("paige.navic.reader.ReaderTransitionFrameTarget")
		assertTrue(
			requestClass.declaredConstructors.single().parameterTypes.contains(targetClass),
			"presentation must require an exact prepared target"
		)
		assertTrue(runCatching {
			Class.forName("paige.navic.reader.ReaderTransitionFrameRequest")
		}.isFailure, "binding-only frame-request hierarchy must not exist")
	}

	@Test
	fun shellCoverCommandCarriesExactTokenGenerationAndFrameResource() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$ShellCover",
			setOf("hostToken", "coverGeneration", "viewportGeneration", "geometry", "requestSequence")
		)
		assertProtocolClassExists("paige.navic.reader.ReaderTransitionCommand\$PrepareFrameTarget")
	}

	@Test
	fun liveExposureCommandCarriesExactHandoffTokenAndFrameResource() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$LiveWebView",
			setOf("handoffToken", "direction", "claimIdentity", "viewportGeneration", "geometry", "requestSequence")
		)
	}

	@Test
	fun nativeFrameCommandConsumesExactDeckTargetWithoutPolling() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$NativePage",
			setOf("allocation", "hostToken", "deckTarget", "geometry", "requestSequence")
		)
	}

	@Test
	fun curlSettlementFrameCommandPreservesGestureAndExactDeckTarget() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$CurlSettlementTerminalFrame",
			setOf("allocation", "gestureId", "settlement", "deckTarget", "geometry", "requestSequence")
		)
	}

	@Test
	fun sameBindingDifferentFrameTargetCannotSatisfyPreparedFrame() {
		val prepared = paige.navic.reader.ReaderTransitionFact.PreparedFrame::class.java
		val targetClass = protocolClass("paige.navic.reader.ReaderTransitionFrameTarget")
		assertTrue(
			prepared.declaredConstructors.single().parameterTypes.contains(targetClass),
			"prepared proof must carry exact target identity, not binding alone"
		)
	}

	@Test
	fun preparedFrameCannotFabricateResourceRegistration() {
		val prepared = paige.navic.reader.ReaderTransitionFact.PreparedFrame::class.java
		val constructors = prepared.declaredConstructors
		assertEquals(1, constructors.size, "prepared frame must have no default-registration constructor")
		assertTrue(
			constructors.single().parameterTypes.contains(
				ReaderTransitionResourceRegistration::class.java
			),
			"prepared proof must echo the coordinator-supplied registration"
		)
	}

	@Test
	fun transitionalInputChangeUsesAtomicRetainedOwnerPublication() {
		assertProtocolClassExists(
			"paige.navic.reader.ReaderTransitionCommand\$PublishRetainedOwnerAndInputLease"
		)
		assertTrue(
			ReaderTransitionCommand::class.java.declaredClasses.none { it.simpleName == "ApplyInputLease" },
			"activated input mutation must only use atomic retained-owner publication"
		)
	}

	@Test
	fun operationSetRemainsExactlyEleven() {
		assertEquals(11, ReaderTransitionOperation.entries.size)
	}

	private fun assertTargetSpecificationFields(className: String, expected: Set<String>) {
		val actual = protocolClass(className).declaredFields.map { it.name }.toSet()
		assertTrue(actual.containsAll(expected), "missing exact target fields")
	}

	private fun assertProtocolClassExists(className: String) {
		assertTrue(runCatching { Class.forName(className) }.isSuccess, "missing amended protocol type")
	}

	private fun protocolClass(className: String): Class<*> =
		runCatching { Class.forName(className) }.getOrElse {
			throw AssertionError("missing amended protocol type", it)
		}

	private data class PreparedCommitFixture(
		val id: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val predecessorOwner: ReaderPresentationFrameOwner,
		val predecessorKey: ReaderTransitionResourceKey,
		val successorOwner: ReaderPresentationFrameOwner,
		val successorRegistration: ReaderTransitionResourceRegistration,
		val target: paige.navic.reader.ReaderTransitionFrameTarget.ShellCover,
		val committingState: paige.navic.reader.ReaderTransitionJournal,
		val commitCommand: ReaderTransitionCommand.CommitOwnerAndInputLease
	)

	private fun preparedCommitFixture(): PreparedCommitFixture {
		val binding = binding()
		val predecessorId = transitionId(binding, sequence = 6L).copy(
			operation = ReaderTransitionOperation.BootstrapNativePage
		)
		val predecessorOwner = ReaderPresentationFrameOwner.NativePage(
			paige.navic.reader.ReaderNativePagePresentationProof(
				binding,
				ReaderPresentationToken(23L),
				29L,
				1200,
				800,
				11L,
				13L
			)
		)
		val predecessorKey = ReaderTransitionResourceKey(
			predecessorId,
			ReaderTransitionResourceKind.Deck,
			31L
		)
		val id = transitionId(binding)
		val successorRegistration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.FrameHandoff, 47L),
			ReaderResourceRetirementOrder(3L, 5L, 2L)
		)
		val specification = paige.navic.reader.ReaderTransitionFrameTargetSpecification.ShellCover(
			id,
			3L,
			binding.publicationGeneration,
			binding,
			paige.navic.reader.ReaderShellCoverHostToken(37L),
			41L,
			binding.viewportGeneration,
			paige.navic.reader.ReaderTransitionFrameGeometry(3L, 5L, 0, 0, 1200, 800),
			1L
		)
		val target = paige.navic.reader.ReaderTransitionFrameTarget.ShellCover(
			paige.navic.reader.ReaderTransitionFrameTargetHandle(
				3L,
				binding.publicationGeneration,
				43L
			),
			specification,
			successorRegistration
		)
		val basePhase = paige.navic.reader.ReaderTransitionLivenessTable.phase(
			id,
			paige.navic.reader.ReaderTransitionPhaseKind.AwaitingProof,
			predecessorOwner
		)
		val awaitingFrame = basePhase.copy(
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(paige.navic.reader.ReaderTransitionProofKind.PreparedFrame),
				callbackSources = setOf(
					paige.navic.reader.ReaderTransitionFactKind.PreparedFrame,
					paige.navic.reader.ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation,
					paige.navic.reader.ReaderTransitionCommandStage.TimerBinding
				)
			)
		)
		val journal = paige.navic.reader.ReaderTransitionJournal(
			active = paige.navic.reader.ReaderActiveTransition(
				id,
				awaitingFrame,
				pendingCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation
				),
				predecessorResourceKey = predecessorKey,
				frameTarget = target
			),
			committed = paige.navic.reader.ReaderCommittedTransition(
				predecessorId,
				predecessorOwner,
				binding,
				predecessorKey,
				ReaderTransitionResourceRegistration(
					predecessorKey,
					ReaderResourceRetirementOrder(3L, 5L, 1L)
				)
			)
		)
		val successorOwner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(37L),
				binding,
				41L,
				43L,
				1200,
				800
			)
		)
		val prepared = journal.reduce(
			paige.navic.reader.ReaderTransitionFact.PreparedFrame(
				id,
				target,
				successorOwner,
				successorRegistration
			)
		)
		return PreparedCommitFixture(
			id,
			binding,
			predecessorOwner,
			predecessorKey,
			successorOwner,
			successorRegistration,
			target,
			prepared.state,
			assertIs(prepared.commands.single())
		)
	}

	private fun commitCommand(
		id: ReaderTransitionId,
		owner: ReaderPresentationFrameOwner,
		binding: ReaderPresentationBinding,
		registration: ReaderTransitionResourceRegistration,
		lease: ReaderTransitionInputLease,
		publicationSequence: Long
	) = ReaderTransitionCommand.CommitOwnerAndInputLease(
		transitionId = id,
		targetHandle = paige.navic.reader.ReaderTransitionFrameTargetHandle(
			id.readerSessionGeneration,
			binding.publicationGeneration,
			publicationSequence
		),
		owner = owner,
		binding = binding,
		preparedFrameResource = registration,
		requestedLease = lease,
		publicationIdentity = paige.navic.reader.ReaderOwnerAndInputPublicationIdentity(
			publicationSequence
		)
	)

	private fun binding() = ReaderPresentationBinding(
		foliateSessionId = "fixture",
		publicationGeneration = 2L,
		viewportGeneration = 3L,
		profileGeneration = 5L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 1L),
		preparationGeneration = 7L,
		rasterGeneration = 11L,
		textureGeneration = 13L
	)

	private fun semanticReceipt(
		origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
	) = paige.navic.reader.ReaderPresentationEventReceipt(
		event = paige.navic.reader.ReaderPresentationEvent.Retry,
		preVersion = paige.navic.reader.ReaderPresentationReceiptVersion(
			origin.transitionId.readerSessionGeneration,
			null,
			0L
		),
		version = paige.navic.reader.ReaderPresentationReceiptVersion(
			origin.transitionId.readerSessionGeneration,
			null,
			1L
		),
		disposition = paige.navic.reader.ReaderPresentationEventDisposition.Accepted,
		postState = paige.navic.reader.ReaderPresentationState(),
		effects = emptyList(),
		origin = origin
	)

	private fun transitionId(
		binding: ReaderPresentationBinding = binding(),
		sequence: Long = 7L
	) = ReaderTransitionId(
		readerSessionGeneration = 3L,
		coordinatorEpoch = 5L,
		sequence = sequence,
		operation = ReaderTransitionOperation.ShellCoverCommit,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(binding),
		parent = if (sequence == 1L) null else paige.navic.reader.ReaderTransitionParentIdentity(
			3L,
			5L,
			sequence - 1L
		)
	)
}
