package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderResumableTransitionModelTest {
	@Test
	fun transitionIdentityCarriesExactAndSemanticBindingsAndImmediateParent() {
		val predecessor = transitionTestBinding(commitSequence = 1L)
		val parent = ReaderTransitionParentIdentity(7L, 11L, 12L)
		val exact = transitionTestId(
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(predecessor),
			parent = parent
		)
		val semantic = transitionTestId(
			operation = ReaderTransitionOperation.ExternalSemanticRelocation,
			expectedBinding = ReaderExpectedPresentationBinding.SemanticSuccessor(predecessor, 17L)
		)

		assertEquals(7L, exact.readerSessionGeneration)
		assertEquals(11L, exact.coordinatorEpoch)
		assertEquals(13L, exact.sequence)
		assertEquals(parent, exact.parent)
		assertIs<ReaderExpectedPresentationBinding.Exact>(exact.expectedBinding)
		assertIs<ReaderExpectedPresentationBinding.SemanticSuccessor>(semantic.expectedBinding)
	}

	@Test
	fun materialAllocationValidationAcceptsPublishedBindingsAndFencesTransitionIdentity() {
		val predecessor = transitionTestBinding(
			commitSequence = 1L,
			rasterGeneration = null,
			textureGeneration = null
		).copy(preparationGeneration = null)
		val successor = predecessor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic-session", 2L)
		)
		val cases = listOf(
			ReaderExpectedPresentationBinding.Exact(predecessor) to predecessor,
			ReaderExpectedPresentationBinding.SemanticSuccessor(predecessor, 1L) to successor,
			ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L) to successor
		)

		cases.forEach { (expected, published) ->
			val id = transitionTestId(
				operation = ReaderTransitionOperation.BootstrapNativePage,
				expectedBinding = expected
			)
			val allocated = published.copy(
				preparationGeneration = 101L,
				rasterGeneration = 103L,
				textureGeneration = 107L
			)
			val allocation = ReaderMaterialGenerationAllocation(
				id,
				allocated,
				101L,
				103L,
				107L
			)
			assertTrue(readerTransitionMaterialBindingIsValid(id, allocated, allocation))

			val foreignId = id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity())
			assertFalse(
				readerTransitionMaterialBindingIsValid(
					id,
					allocated,
					allocation.copy(transitionId = foreignId)
				)
			)
		}
	}

	@Test
	fun transitionIdentityRequiresExactImmediateParentChain() {
		val binding = transitionTestBinding(commitSequence = 1L)
		fun identity(
			session: Long = 7L,
			epoch: Long = 11L,
			sequence: Long,
			parent: ReaderTransitionParentIdentity?
		) = ReaderTransitionId(
			readerSessionGeneration = session,
			coordinatorEpoch = epoch,
			sequence = sequence,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding),
			parent = parent
		)

		val root = identity(sequence = 1L, parent = null)
		val second = identity(sequence = 2L, parent = root.parentIdentity())
		assertEquals(root.parentIdentity(), second.parent)
		assertFailsWith<IllegalArgumentException> {
			identity(sequence = 1L, parent = ReaderTransitionParentIdentity(7L, 11L, 1L))
		}
		assertFailsWith<IllegalArgumentException> { identity(sequence = 2L, parent = null) }
		assertFailsWith<IllegalArgumentException> {
			identity(sequence = 2L, parent = ReaderTransitionParentIdentity(8L, 11L, 1L))
		}
		assertFailsWith<IllegalArgumentException> {
			identity(sequence = 2L, parent = ReaderTransitionParentIdentity(7L, 12L, 1L))
		}
		assertFailsWith<IllegalArgumentException> {
			identity(sequence = 3L, parent = ReaderTransitionParentIdentity(7L, 11L, 1L))
		}
		assertFailsWith<IllegalArgumentException> {
			identity(sequence = 3L, parent = ReaderTransitionParentIdentity(7L, 11L, 3L))
		}
	}

	@Test
	fun curlLeaseRetainsSuppliedGestureIdentityIndependentOfTransitionSequence() {
		val fixture = journalAwaitingSettlement()
		val lease = assertIs<ReaderTransitionInputLease.ClaimedGesture>(
			requireNotNull(fixture.journal.active).phase.contract.inputLease
		)

		assertEquals(fixture.gestureId, lease.gestureId)
		assertTrue(fixture.id.sequence != lease.gestureId.value)
		assertEquals(
			fixture.gestureId,
			ReaderPageTurnIntent(
				ReaderPageTurnDirection.Next,
				fixture.gestureId,
				transitionTestSemanticHandle()
			).gestureId
		)
	}

	@Test
	fun matchingSettlementIsConsumedOnceBeforeConsequencesAndNeverInherited() {
		val fixture = journalAwaitingSettlement()
		val fact = matchingSettlementFact(fixture)

		val first = fixture.journal.reduce(fact)

		val active = assertNotNull(first.state.active)
		val firstConsumption = assertNotNull(active.consumedSettlement)
		assertEquals(fixture.id, firstConsumption.transitionId)
		assertEquals(
			setOf(ReaderTransitionProofKind.FrameTargetPreparation),
			active.phase.contract.awaitedProofs
		)
		val targetPreparation = assertIs<ReaderTransitionCommand.PrepareFrameTarget>(
			first.commands.single()
		)
		assertEquals(fixture.id, targetPreparation.transitionId)
		assertEquals(fixture.successor, targetPreparation.specification.binding)
		assertIs<ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame>(
			targetPreparation.specification
		)
		assertTrue(first.commands.none { it is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease })
		val duplicate = first.state.reduce(fact)
		assertTrue(duplicate.commands.isEmpty())
		assertEquals(first.state, duplicate.state)

		val relocation = duplicate.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(
				transitionId = null,
				binding = fixture.successor.copy(
					destinationCommitIdentity = ReaderDestinationCommitIdentity(
						fixture.successor.foliateSessionId,
						3L
					)
				)
			)
		)
		assertTrue(fixture.id != relocation.state.active?.id)
		val staleReceipt = relocation.state.reduce(fact)
		assertEquals(relocation.state, staleReceipt.state)
		assertTrue(staleReceipt.commands.isEmpty())
	}

	@Test
	fun settlementConsumptionIsBoundedToOneActiveTransitionAndClearedOnReplacement() {
		val fixture = journalAwaitingSettlement()
		val first = fixture.journal.reduce(matchingSettlementFact(fixture))
		assertEquals(
			fixture.id,
			assertNotNull(first.state.active?.consumedSettlement).transitionId
		)

		val replacement = first.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(
				transitionId = null,
				binding = fixture.successor.copy(
					destinationCommitIdentity = ReaderDestinationCommitIdentity(
						fixture.successor.foliateSessionId,
						3L
					)
				)
			)
		)
		assertNull(replacement.state.active?.consumedSettlement)

		val nextId = fixture.id.copy(
			sequence = fixture.id.sequence + 2L,
			parent = ReaderTransitionParentIdentity(
				fixture.id.readerSessionGeneration,
				fixture.id.coordinatorEpoch,
				fixture.id.sequence + 1L
			)
		)
		val nextPhase = ReaderTransitionLivenessTable.phase(
			id = nextId,
			kind = ReaderTransitionPhaseKind.AwaitingProof,
			retainedOwner = fixture.retainedOwner,
			gestureId = ReaderTransitionGestureId(103L)
		)
		val nextJournal = replacement.state.copy(
			active = ReaderActiveTransition(
				id = nextId,
				phase = nextPhase,
				predecessorResourceKey = fixture.predecessorResourceKey
			),
			lastTransitionSequence = nextId.sequence,
			lastIssuedTransitionIdentity = nextId.parentIdentity()
		)
		val nextSettlement = ReaderTransitionFact.SettlementAcknowledged(
			transitionId = nextId,
			binding = fixture.successor,
			acknowledgement = matchingSettlementFact(fixture).acknowledgement
		)

		val consumedNext = nextJournal.reduce(nextSettlement)
		assertEquals(
			nextId,
			assertNotNull(consumedNext.state.active?.consumedSettlement).transitionId
		)
	}

	@Test
	fun semanticSuccessorCommitsOnlyAfterTargetPreparationFrameAndPublicationAcknowledgement() {
		val fixture = journalAwaitingSemanticSuccessor()
		val successorOwner = transitionTestNativeOwner(fixture.id, fixture.successor)
		val settled = fixture.journal.reduce(matchingSettlementFact(fixture))
		val (awaitingFrame, target) = transitionTestApplyTargetPreparation(settled)
		val prepared = awaitingFrame.reduce(
			ReaderTransitionFact.PreparedFrame(
				fixture.id,
				target,
				successorOwner,
				target.resource
			)
		)

		assertEquals(ReaderTransitionPhaseKind.Committing, prepared.state.active?.phase?.kind)
		assertNull(prepared.state.lastOutcome)
		assertTrue(prepared.state.committed.retainedOwnerForTest() == fixture.retainedOwner, "Committed owner category mismatch")

		val completed = transitionTestApplySuccessorAcknowledgement(prepared)
		assertNull(completed.state.active)
		val succeeded = assertIs<ReaderTransitionOutcome.Succeeded>(completed.state.lastOutcome)
		assertEquals(fixture.successor, succeeded.binding)
		assertEquals(successorOwner, succeeded.committedOwner)
		assertTrue(completed.state.committed.retainedResourceKeyForTest() == target.resource.key, "Committed resource mismatch")
		assertEquals(
			fixture.id,
			settled.state.active?.consumedSettlement?.transitionId
		)
		val duplicate = completed.state.reduce(matchingSettlementFact(fixture))
		assertEquals(completed.state, duplicate.state)
		assertTrue(duplicate.commands.isEmpty())
	}

	@Test
	fun everyAppOriginatedExternalRouteRegistersBeforeSemanticCommand() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()

		ReaderExternalRelocationSource.entries.forEach { source ->
			val result = idle.reduce(
				ReaderTransitionFact.Intent(
					transitionId = null,
					intent = ReaderExternalRelocationIntent(source, transitionTestSemanticHandle())
				)
			)

			val active = assertNotNull(result.state.active, source.name)
			assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, active.id.operation, source.name)
			assertEquals(fixture.id.readerSessionGeneration, active.id.readerSessionGeneration, source.name)
			assertEquals(fixture.id.coordinatorEpoch, active.id.coordinatorEpoch, source.name)
			assertEquals(fixture.retainedOwner, active.phase.contract.retainedOwner, source.name)
			assertEquals(ReaderTransitionInputLease.ChromeOnly, active.phase.contract.inputLease, source.name)
			assertEquals(
				setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
				active.phase.contract.awaitedProofs,
				source.name
			)
			val retainedPublication = assertIs<ReaderTransitionCommand.PublishRetainedOwnerAndInputLease>(
				result.commands.single()
			)
			assertEquals(active.id, retainedPublication.transitionId, source.name)
			assertEquals(fixture.retainedOwner, retainedPublication.retainedOwner, source.name)
			assertEquals(ReaderTransitionInputLease.ChromeOnly, retainedPublication.requestedLease, source.name)
			assertTrue(result.commands.none { it is ReaderTransitionCommand.RequestSemanticSynchronization }, source.name)
			assertTrue(result.commands.none { it is ReaderTransitionCommand.RequestRasterPreparation }, source.name)
		}
	}

	@Test
	fun matchingUntaggedDestinationSettlesRegisteredExternalOperationWithoutSupersession() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()
		val started = idle.reduce(
			ReaderTransitionFact.Intent(
				transitionId = null,
				intent = ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Toc,
					transitionTestSemanticHandle()
				)
			)
		)
		val registeredId = assertNotNull(started.state.active).id
		val admitted = transitionTestApplyRetainedAcknowledgement(started)

		val committed = admitted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(
				transitionId = null,
				binding = fixture.successor
			)
		)

		val active = assertNotNull(committed.state.active)
		assertEquals(registeredId, active.id)
		assertFalse(ReaderTransitionProofKind.SemanticDestination in active.phase.contract.awaitedProofs)
		assertEquals(
			listOf(ReaderTransitionCommand.AllocateMaterialBinding(registeredId, fixture.successor.withoutTestMaterial())),
			committed.commands
		)
		assertFalse(committed.state.lastOutcome is ReaderTransitionOutcome.Cancelled)
	}

	@Test
	fun failedExternalRelocationRetainsNoninteractiveShieldAndRetryUsesFreshIdentity() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()
		val started = idle.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Search,
					transitionTestSemanticHandle()
				)
			)
		)
		val first = assertNotNull(started.state.active)
		val failed = started.state.reduce(ReaderTransitionFact.DeadlineExpired(first.id))

		val failure = assertIs<ReaderTransitionOutcome.Failed>(failed.state.lastOutcome)
		assertEquals(ReaderTransitionFailureReason.ExternalRelocationTimeout, failure.reason)
		assertEquals(fixture.retainedOwner, failure.retainedOwner)
		assertTrue(failed.state.committed.retainedResourceKeyForTest() == fixture.predecessorResourceKey, "Retained resource mismatch")
		assertTrue(failed.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})

		val retried = failed.state.reduce(ReaderTransitionFact.Retry(null))
		val retry = assertNotNull(retried.state.active)
		assertTrue(retry.id != first.id)
		assertEquals(first.id.parentIdentity(), retry.id.parent)
		assertEquals(first.id.operation, retry.id.operation)
		assertEquals(first.id.expectedBinding, retry.id.expectedBinding)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retry.phase.contract.inputLease)
		val resumed = transitionTestApplyRetainedAcknowledgement(retried)
		assertEquals(
			ReaderTransitionCommand.RequestSemanticSynchronization(
				retry.id,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Search,
					transitionTestSemanticHandle()
				),
				transitionTestSemanticHandle()
			),
			resumed.commands.single()
		)
	}

	@Test
	fun coverEntryRetryAfterAcceptedDestinationReusesExactAuthorityWithoutSemanticReplay() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.idleCommittedJournal().reduce(
			ReaderTransitionFact.Intent(null, ReaderCoverEntryIntent(transitionTestSemanticHandle()))
		)
		val first = assertNotNull(started.state.active)
		val admitted = transitionTestApplyRetainedAcknowledgement(started)
		val accepted = admitted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(first.id, fixture.successor)
		)
		val acceptedActive = assertNotNull(accepted.state.active)
		assertEquals(fixture.successor, acceptedActive.resolvedSuccessorBinding)
		assertEquals(
			listOf(ReaderTransitionCommand.AllocateMaterialBinding(first.id, fixture.successor.withoutTestMaterial())),
			accepted.commands
		)
		val duplicate = accepted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(first.id, fixture.successor)
		)
		assertEquals(accepted.state, duplicate.state)
		assertTrue(duplicate.commands.isEmpty())

		val failed = accepted.state.reduce(ReaderTransitionFact.DeadlineExpired(first.id))
		val retryable = assertNotNull(failed.state.retryableTransition)
		assertTrue(retryable.semanticDestinationCommitted)
		assertTrue(failed.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})

		val retried = failed.state.reduce(ReaderTransitionFact.Retry(null))
		val retry = assertNotNull(retried.state.active)
		assertTrue(retry.id.sequence > first.id.sequence)
		assertEquals(ReaderTransitionOperation.CoverToPageEntry, retry.id.operation)
		assertEquals(ReaderExpectedPresentationBinding.Exact(fixture.successor), retry.id.expectedBinding)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retry.phase.contract.inputLease)
		assertEquals(fixture.predecessorResourceKey, retry.predecessorResourceKey)
		val retainedPublication = assertIs<ReaderTransitionCommand.PublishRetainedOwnerAndInputLease>(
			retried.commands.single()
		)
		assertEquals(retry.id, retainedPublication.transitionId)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retainedPublication.requestedLease)
		assertTrue(retried.commands.none { it is ReaderTransitionCommand.AllocateMaterialBinding })
		assertTrue(retried.commands.none {
			it is ReaderTransitionCommand.RequestSemanticSynchronization
		})
	}

	@Test
	fun coverEntryRetryBeforeDestinationReissuesSemanticRequest() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.idleCommittedJournal().reduce(
			ReaderTransitionFact.Intent(null, ReaderCoverEntryIntent(transitionTestSemanticHandle()))
		)
		val first = assertNotNull(started.state.active)
		val failed = started.state.reduce(ReaderTransitionFact.DeadlineExpired(first.id))

		val retried = failed.state.reduce(ReaderTransitionFact.Retry(null))
		val retry = assertNotNull(retried.state.active)
		assertTrue(retry.id.sequence > first.id.sequence)
		assertEquals(first.id.expectedBinding, retry.id.expectedBinding)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retry.phase.contract.inputLease)
		val resumed = transitionTestApplyRetainedAcknowledgement(retried)
		assertEquals(
			ReaderTransitionCommand.RequestSemanticSynchronization(
				retry.id,
				ReaderCoverEntryIntent(transitionTestSemanticHandle()),
				transitionTestSemanticHandle()
			),
			resumed.commands.single()
		)
	}

	@Test
	fun retryBeforeSettlementUsesFreshChromeOnlyIdentityAndReissuesSemanticCommand() {
		val fixture = journalAwaitingSettlement()
		val intent = ReaderPageTurnIntent(
				ReaderPageTurnDirection.Next,
				fixture.gestureId,
				transitionTestSemanticHandle()
			)
		val started = fixture.idleCommittedJournal().reduce(
			ReaderTransitionFact.Intent(null, intent)
		)
		val first = assertNotNull(started.state.active)
		val oldGestureResource = ReaderTransitionResourceKey(
			first.id,
			ReaderTransitionResourceKind.CallbackRegistration,
			211L
		)
		val withGestureResource = started.state.copy(
			active = first.copy(ownedResourceKeys = setOf(oldGestureResource))
		)
		val failed = withGestureResource.reduce(
			ReaderTransitionFact.DeadlineExpired(first.id)
		)

		val retried = failed.state.reduce(ReaderTransitionFact.Retry(null))
		val retry = assertNotNull(retried.state.active)

		assertTrue(retry.id.sequence > first.id.sequence)
		assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement, retry.id.operation)
		assertEquals(first.id.parentIdentity(), retry.id.parent)
		assertEquals(
			setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
			retry.phase.contract.awaitedProofs
		)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retry.phase.contract.inputLease)
		assertTrue(retry.ownedResourceKeys.isEmpty())
		assertEquals(fixture.predecessorResourceKey, retry.predecessorResourceKey)
		assertTrue(failed.commands.any {
			it is ReaderTransitionCommand.ReleaseResource && it.key == oldGestureResource
		})
		assertTrue(failed.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})
		val resumed = transitionTestApplyRetainedAcknowledgement(retried)
		assertTrue(
			ReaderTransitionProofKind.SettlementAcknowledgement in
				requireNotNull(resumed.state.active).phase.contract.awaitedProofs
		)
		assertEquals(
			ReaderTransitionCommand.RequestSemanticSynchronization(retry.id, intent, intent.requestHandle),
			resumed.commands.single()
		)
		assertTrue(resumed.commands.none { it is ReaderTransitionCommand.RequestRasterPreparation })
	}

	@Test
	fun retryAfterSettlementUsesFreshRendererRecoveryWithoutGestureOrSecondPageTurn() {
		val fixture = journalAwaitingSettlement()
		val oldAttemptResource = ReaderTransitionResourceKey(
			fixture.id,
			ReaderTransitionResourceKind.Raster,
			211L
		)
		val registered = fixture.journal.reduce(
			ReaderTransitionFact.ResourceObserved(fixture.id, oldAttemptResource)
		)
		val settled = registered.state.reduce(matchingSettlementFact(fixture))
		val failed = settled.state.reduce(ReaderTransitionFact.DeadlineExpired(fixture.id))

		val retried = failed.state.reduce(ReaderTransitionFact.Retry(null))
		val retry = assertNotNull(retried.state.active)

		assertTrue(retry.id.sequence > fixture.id.sequence)
		assertEquals(ReaderTransitionOperation.RendererRecovery, retry.id.operation)
		assertEquals(ReaderExpectedPresentationBinding.Exact(fixture.successor), retry.id.expectedBinding)
		assertEquals(fixture.id.parentIdentity(), retry.id.parent)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retry.phase.contract.inputLease)
		assertEquals(
			setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
			retry.phase.contract.awaitedProofs
		)
		assertNull(retry.consumedSettlement)
		assertNull(retry.semanticIntent)
		assertEquals(fixture.predecessorResourceKey, retry.predecessorResourceKey)
		assertTrue(retried.commands.none { it is ReaderTransitionCommand.RequestSemanticSynchronization })
		val resumed = transitionTestApplyRetainedAcknowledgement(retried)
		assertEquals(
			setOf(
				ReaderTransitionProofKind.RendererGeneration,
				ReaderTransitionProofKind.MaterialBindingAllocation,
				ReaderTransitionProofKind.DeckOwnership,
				ReaderTransitionProofKind.DeckPrepared,
				ReaderTransitionProofKind.PreparedFrame
			),
			requireNotNull(resumed.state.active).phase.contract.awaitedProofs
		)
		assertEquals(
			ReaderTransitionCommand.AllocateMaterialBinding(
				retry.id,
				fixture.successor.withoutTestMaterial()
			),
			resumed.commands.single()
		)
		assertEquals(
			listOf(oldAttemptResource),
			failed.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
		assertTrue(failed.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})

		val deckKey = ReaderTransitionResourceKey(
			retry.id,
			ReaderTransitionResourceKind.Deck,
			requireNotNull(fixture.successor.textureGeneration)
		)
		val owner = transitionTestNativeOwner(retry.id, fixture.successor)
		val allocated = resumed.state.reduce(testAllocationFact(retry.id, fixture.successor))
		val withTarget = allocated.state.withTransitionTestNativeTarget(
			retry.id,
			fixture.successor,
			deckKey
		)
		val generation = withTarget.reduce(
			ReaderTransitionFact.RendererGenerationReady(retry.id, rendererGeneration = 227L)
		)
		val owned = generation.state.reduce(ReaderTransitionFact.DeckOwned(retry.id, deckKey))
		val prepared = owned.state.reduce(ReaderTransitionFact.DeckPrepared(retry.id, deckKey))
		val (awaitingFrame, target) = transitionTestApplyTargetPreparation(prepared)
		val committing = awaitingFrame.reduce(
			ReaderTransitionFact.PreparedFrame(retry.id, target, owner, target.resource)
		)
		val completed = transitionTestApplySuccessorAcknowledgement(committing)
		assertNull(completed.state.active)
		assertEquals(fixture.successor, assertIs<ReaderTransitionOutcome.Succeeded>(completed.state.lastOutcome).binding)
		assertEquals(
			listOf(fixture.predecessorResourceKey),
			completed.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun postSettlementRendererRecoveryCanFailAndRetryWithoutSettlementProof() {
		val fixture = journalAwaitingSettlement()
		val settled = fixture.journal.reduce(matchingSettlementFact(fixture))
		val firstFailure = settled.state.reduce(ReaderTransitionFact.DeadlineExpired(fixture.id))
		val retried = firstFailure.state.reduce(ReaderTransitionFact.Retry(null))
		val retry = assertNotNull(retried.state.active)

		val secondFailure = retried.state.reduce(ReaderTransitionFact.DeadlineExpired(retry.id))

		assertNull(secondFailure.state.active)
		val outcome = assertIs<ReaderTransitionOutcome.Failed>(secondFailure.state.lastOutcome)
		assertEquals(ReaderTransitionFailureReason.RendererRecoveryTimeout, outcome.reason)
		assertTrue(secondFailure.commands.none { it is ReaderTransitionCommand.RequestSemanticSynchronization })
		assertTrue(secondFailure.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})

		val secondRetry = secondFailure.state.reduce(ReaderTransitionFact.Retry(null))
		val secondAttempt = assertNotNull(secondRetry.state.active)
		assertEquals(ReaderTransitionOperation.RendererRecovery, secondAttempt.id.operation)
		assertTrue(secondAttempt.id.sequence > retry.id.sequence)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, secondAttempt.phase.contract.inputLease)
		val resumedSecondAttempt = transitionTestApplyRetainedAcknowledgement(secondRetry)
		assertEquals(
			ReaderTransitionCommand.AllocateMaterialBinding(
				secondAttempt.id,
				fixture.successor.withoutTestMaterial()
			),
			resumedSecondAttempt.commands.single()
		)
		assertTrue(secondRetry.commands.none { it is ReaderTransitionCommand.RequestSemanticSynchronization })
	}

	@Test
	fun cancelTerminatesRegisteredSemanticOperationWithoutReleasingItsShield() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()
		val started = idle.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Bookmark,
					transitionTestSemanticHandle()
				)
			)
		)
		val active = assertNotNull(started.state.active)

		val cancelled = started.state.reduce(
			ReaderTransitionFact.Intent(active.id, ReaderCancelIntent)
		)

		assertNull(cancelled.state.active)
		val outcome = assertIs<ReaderTransitionOutcome.Cancelled>(cancelled.state.lastOutcome)
		assertEquals(ReaderTransitionCancellationReason.UserCancelled, outcome.reason)
		assertEquals(fixture.retainedOwner, outcome.retainedOwner)
		assertEquals(ReaderTransitionCommand.CancelOwnedWork(active.id), cancelled.commands.first())
		assertTrue(cancelled.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})
	}

	@Test
	fun relocationAfterCancellationUsesMonotonicallyNewIdentity() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()
		val first = idle.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Bookmark,
					transitionTestSemanticHandle()
				)
			)
		)
		val firstId = assertNotNull(first.state.active).id
		val cancelled = first.state.reduce(
			ReaderTransitionFact.Intent(firstId, ReaderCancelIntent)
		)

		val second = cancelled.state.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Annotation,
					transitionTestSemanticHandle()
				)
			)
		)
		val secondId = assertNotNull(second.state.active).id

		assertTrue(secondId.sequence > firstId.sequence)
	}

	@Test
	fun relocationAfterFailureUsesMonotonicallyNewIdentity() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()
		val first = idle.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Search,
					transitionTestSemanticHandle()
				)
			)
		)
		val firstId = assertNotNull(first.state.active).id
		val failed = first.state.reduce(ReaderTransitionFact.DeadlineExpired(firstId))

		val second = failed.state.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Toc,
					transitionTestSemanticHandle()
				)
			)
		)
		val secondId = assertNotNull(second.state.active).id

		assertTrue(secondId.sequence > firstId.sequence)
	}

	@Test
	fun untaggedDestinationCreatesExternalRelocationAndRevokesPageInput() {
		val fixture = journalAwaitingSettlement()
		val result = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(
				transitionId = null,
				binding = fixture.successor
			)
		)

		val active = requireNotNull(result.state.active)
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, active.id.operation)
		assertEquals(
			ReaderExpectedPresentationBinding.Exact(fixture.successor),
			active.id.expectedBinding
		)
		assertEquals(fixture.id.parentIdentity(), active.id.parent)
		assertEquals(fixture.retainedOwner, active.phase.contract.retainedOwner)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, active.phase.contract.inputLease)
		assertEquals(
			setOf(ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement),
			active.phase.contract.awaitedProofs
		)
		assertEquals(
			ReaderTransitionCommand.CancelOwnedWork(fixture.id),
			result.commands.first()
		)
		assertTrue(result.commands.any {
			it is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease &&
				it.requestedLease == ReaderTransitionInputLease.ChromeOnly
		})
		assertTrue(result.commands.none { it is ReaderTransitionCommand.AllocateMaterialBinding })
		assertTrue(result.commands.none { it is ReaderTransitionCommand.ReleaseResource })
	}

	@Test
	fun untaggedAuthoritativeDestinationRetryReusesExactBindingWithoutSemanticReplay() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()
		val started = idle.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val first = assertNotNull(started.state.active)

		val failed = started.state.reduce(ReaderTransitionFact.DeadlineExpired(first.id))
		assertTrue(failed.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})

		val retried = failed.state.reduce(ReaderTransitionFact.Retry(null))
		val retry = assertNotNull(retried.state.active)
		assertTrue(retry.id.sequence > first.id.sequence)
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, retry.id.operation)
		assertEquals(ReaderExpectedPresentationBinding.Exact(fixture.successor), retry.id.expectedBinding)
		assertEquals(fixture.successor, retry.resolvedSuccessorBinding)
		assertTrue(retry.authoritativeDestinationCommitted)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retry.phase.contract.inputLease)
		assertEquals(fixture.predecessorResourceKey, retry.predecessorResourceKey)
		val retainedPublication = assertIs<ReaderTransitionCommand.PublishRetainedOwnerAndInputLease>(
			retried.commands.single()
		)
		assertEquals(retry.id, retainedPublication.transitionId)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, retainedPublication.requestedLease)
		assertTrue(retried.commands.none { it is ReaderTransitionCommand.AllocateMaterialBinding })
		assertTrue(retried.commands.none {
			it is ReaderTransitionCommand.RequestSemanticSynchronization
		})

		val resumed = transitionTestApplyRetainedAcknowledgement(retried)
		val allocated = resumed.state.reduce(testAllocationFact(retry.id, fixture.successor))
		val deckKey = ReaderTransitionResourceKey(
			retry.id,
			ReaderTransitionResourceKind.Deck,
			requireNotNull(fixture.successor.textureGeneration)
		)
		var physicalState = allocated.state.withTransitionTestNativeTarget(
			retry.id,
			fixture.successor,
			deckKey
		)
		val raster = physicalState.reduce(ReaderTransitionFact.RasterProven(retry.id))
		val owned = raster.state.reduce(ReaderTransitionFact.DeckOwned(retry.id, deckKey))
		val prepared = owned.state.reduce(ReaderTransitionFact.DeckPrepared(retry.id, deckKey))
		val (awaitingFrame, target) = transitionTestApplyTargetPreparation(prepared)
		val owner = transitionTestNativeOwner(retry.id, fixture.successor)
		val committing = awaitingFrame.reduce(
			ReaderTransitionFact.PreparedFrame(retry.id, target, owner, target.resource)
		)
		val completed = transitionTestApplySuccessorAcknowledgement(committing)

		assertNull(completed.state.active)
		assertEquals(
			listOf(fixture.predecessorResourceKey),
			completed.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun untaggedDifferentDestinationSupersedesAcceptedCoverEntry() {
		assertUntaggedDifferentDestinationSupersedesAcceptedIntent(ReaderCoverEntryIntent(transitionTestSemanticHandle()))
	}

	@Test
	fun untaggedDifferentDestinationSupersedesAcceptedExternalRelocation() {
		assertUntaggedDifferentDestinationSupersedesAcceptedIntent(
			ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Search,
					transitionTestSemanticHandle()
				)
		)
	}

	@Test
	fun untaggedSameDestinationCoalescesAfterAuthoritativeAcceptance() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.idleCommittedJournal().reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Search,
					transitionTestSemanticHandle()
				)
			)
		)
		val id = assertNotNull(started.state.active).id
		val accepted = started.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(id, fixture.successor)
		)

		val duplicate = accepted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)

		assertEquals(accepted.state, duplicate.state)
		assertTrue(duplicate.commands.isEmpty())
	}

	@Test
	fun untaggedSameDestinationCoalescesForExactUnsolicitedRelocation() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.idleCommittedJournal().reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)

		val duplicate = started.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)

		assertEquals(started.state, duplicate.state)
		assertTrue(duplicate.commands.isEmpty())
	}

	@Test
	fun taggedDifferentDestinationCannotSupersedeAcceptedDestination() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.idleCommittedJournal().reduce(
			ReaderTransitionFact.Intent(null, ReaderCoverEntryIntent(transitionTestSemanticHandle()))
		)
		val id = assertNotNull(started.state.active).id
		val accepted = started.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(id, fixture.successor)
		)
		val different = fixture.successor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.successor.foliateSessionId,
				3L
			)
		)

		val stale = accepted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(id, different)
		)

		assertEquals(accepted.state, stale.state)
		assertTrue(stale.commands.isEmpty())
	}

	private fun assertUntaggedDifferentDestinationSupersedesAcceptedIntent(
		intent: ReaderSemanticSynchronizationIntent
	) {
		val fixture = journalAwaitingSettlement()
		val started = fixture.idleCommittedJournal().reduce(
			ReaderTransitionFact.Intent(null, intent)
		)
		val first = assertNotNull(started.state.active)
		val admitted = transitionTestApplyRetainedAcknowledgement(started)
		val accepted = admitted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(first.id, fixture.successor)
		)
		val firstOwnedResources = listOf(
			ReaderTransitionResourceKey(first.id, ReaderTransitionResourceKind.Raster, 71L),
			ReaderTransitionResourceKey(first.id, ReaderTransitionResourceKind.CallbackRegistration, 73L)
		)
		val withResources = firstOwnedResources.fold(accepted.state) { state, key ->
			state.reduce(ReaderTransitionFact.ResourceObserved(first.id, key)).state
		}
		val replacementBinding = fixture.successor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.successor.foliateSessionId,
				3L
			)
		)

		val replaced = withResources.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, replacementBinding)
		)
		val replacement = assertNotNull(replaced.state.active)

		assertTrue(replacement.id.sequence > first.id.sequence)
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, replacement.id.operation)
		assertEquals(
			ReaderExpectedPresentationBinding.Exact(replacementBinding),
			replacement.id.expectedBinding
		)
		assertEquals(replacementBinding, replacement.resolvedSuccessorBinding)
		assertTrue(replacement.authoritativeDestinationCommitted)
		assertEquals(fixture.predecessorResourceKey, replacement.predecessorResourceKey)
		assertEquals(ReaderTransitionCommand.CancelOwnedWork(first.id), replaced.commands.first())
		assertEquals(
			firstOwnedResources,
			replaced.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
		assertTrue(replaced.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorResourceKey
		})
		val retainedPublication = assertIs<ReaderTransitionCommand.PublishRetainedOwnerAndInputLease>(
			replaced.commands.last()
		)
		assertEquals(replacement.id, retainedPublication.transitionId)
		assertTrue(replaced.commands.none { it is ReaderTransitionCommand.AllocateMaterialBinding })
		assertTrue(replaced.commands.none {
			it is ReaderTransitionCommand.RequestSemanticSynchronization
		})

		val staleFirst = replaced.state.reduce(ReaderTransitionFact.RasterProven(first.id))
		assertEquals(replaced.state, staleFirst.state)
		assertTrue(staleFirst.commands.isEmpty())
	}

	@Test
	fun replacingRelocationDrainsEveryDistinctSuccessorResourceAndRetainsOriginalShield() {
		val fixture = journalAwaitingSettlement()
		val first = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val firstActive = requireNotNull(first.state.active)
		val admitted = ReaderTransitionResourceKey(
			firstActive.id,
			ReaderTransitionResourceKind.Deck,
			71L
		)
		val pending = admitted.copy(opaqueId = 73L)
		val successor = admitted.copy(opaqueId = 79L)
		val populated = first.state.copy(
			active = firstActive.copy(
				admittedDeckKey = admitted,
				pendingPreparedDeckKey = pending,
				successorResourceKey = successor
			)
		)
		val replacementBinding = fixture.successor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.successor.foliateSessionId,
				3L
			)
		)

		val replaced = populated.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, replacementBinding)
		)

		val replacement = requireNotNull(replaced.state.active)
		assertEquals(firstActive.id.parentIdentity(), replacement.id.parent)
		assertEquals(fixture.predecessorResourceKey, replacement.predecessorResourceKey)
		assertEquals(
			ReaderTransitionCommand.CancelOwnedWork(firstActive.id),
			replaced.commands.first()
		)
		val releasedKeys = replaced.commands
			.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			.map { it.key }
		assertEquals(listOf(admitted, pending, successor), releasedKeys)
		assertFalse(fixture.predecessorResourceKey in releasedKeys)
	}

	@Test
	fun replacingRelocationDeduplicatesOverlappingSuccessorResourceKeys() {
		val fixture = journalAwaitingSettlement()
		val first = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val firstActive = requireNotNull(first.state.active)
		val shared = ReaderTransitionResourceKey(
			firstActive.id,
			ReaderTransitionResourceKind.Deck,
			83L
		)
		val populated = first.state.copy(
			active = firstActive.copy(
				admittedDeckKey = shared,
				pendingPreparedDeckKey = shared,
				successorResourceKey = shared
			)
		)
		val replacementBinding = fixture.successor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.successor.foliateSessionId,
				3L
			)
		)

		val replaced = populated.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, replacementBinding)
		)

		assertEquals(
			listOf(shared),
			replaced.commands
				.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
				.map { it.key }
		)
		assertEquals(
			fixture.predecessorResourceKey,
			requireNotNull(replaced.state.active).predecessorResourceKey
		)
	}

	@Test
	fun externalRelocationCommitsAfterFreshMaterialProofsInAnyOrderThenExactFrameAcknowledgement() {
		val permutations = listOf(
			listOf(RelocationProof.Raster, RelocationProof.DeckPrepared, RelocationProof.DeckOwned),
			listOf(RelocationProof.DeckOwned, RelocationProof.Raster, RelocationProof.DeckPrepared),
			listOf(RelocationProof.DeckPrepared, RelocationProof.DeckOwned, RelocationProof.Raster)
		)

		permutations.forEach { permutation ->
			val fixture = journalAwaitingSettlement()
			val started = fixture.journal.reduce(
				ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
			)
			val admitted = transitionTestApplyRetainedAcknowledgement(started)
			val relocationId = requireNotNull(admitted.state.active).id
			val deckKey = ReaderTransitionResourceKey(
				relocationId,
				ReaderTransitionResourceKind.Deck,
				requireNotNull(fixture.successor.textureGeneration)
			)
			val successorOwner = transitionTestNativeOwner(relocationId, fixture.successor)
			val allocation = admitted.state.reduce(testAllocationFact(relocationId, fixture.successor))
			var state = allocation.state.withTransitionTestNativeTarget(
				relocationId,
				fixture.successor,
				deckKey
			)
			val commands = (started.commands + admitted.commands + allocation.commands).toMutableList()
			lateinit var targetPreparation: ReaderTransitionReduction

			permutation.forEachIndexed { index, proof ->
				val reduction = when (proof) {
					RelocationProof.Raster -> state.reduce(ReaderTransitionFact.RasterProven(relocationId))
					RelocationProof.DeckOwned -> state.reduce(ReaderTransitionFact.DeckOwned(relocationId, deckKey))
					RelocationProof.DeckPrepared -> state.reduce(ReaderTransitionFact.DeckPrepared(relocationId, deckKey))
					RelocationProof.Frame -> error("Frame follows exact target preparation")
				}
				state = reduction.state
				commands += reduction.commands
				if (index < permutation.lastIndex) {
					assertEquals(fixture.retainedOwner, state.active?.phase?.contract?.retainedOwner)
					assertTrue(reduction.commands.none { it is ReaderTransitionCommand.ReleaseResource })
				} else {
					targetPreparation = reduction
				}
			}

			val (awaitingFrame, target) = transitionTestApplyTargetPreparation(targetPreparation)
			val prepared = awaitingFrame.reduce(
				ReaderTransitionFact.PreparedFrame(
					relocationId,
					target,
					successorOwner,
					target.resource
				)
			)
			commands += prepared.commands
			assertEquals(ReaderTransitionPhaseKind.Committing, prepared.state.active?.phase?.kind)
			assertTrue(prepared.state.committed.retainedOwnerForTest() == fixture.retainedOwner, "Committed owner category mismatch")
			val completed = transitionTestApplySuccessorAcknowledgement(prepared)
			state = completed.state
			commands += completed.commands

			assertNull(state.active)
			val succeeded = assertIs<ReaderTransitionOutcome.Succeeded>(state.lastOutcome)
			assertEquals(fixture.successor, succeeded.binding)
			assertEquals(successorOwner, succeeded.committedOwner)
			val releases = commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			assertEquals(listOf(fixture.predecessorResourceKey), releases.map { it.key })
			assertTrue(commands.any {
				it is ReaderTransitionCommand.CommitOwnerAndInputLease &&
					it.requestedLease is ReaderTransitionInputLease.NativePage
			})
		}
	}

	@Test
	fun idleCommittedFrameCanStartUntaggedExternalRelocationWithoutCancellation() {
		val predecessor = transitionTestBinding(commitSequence = 1L)
		val committedId = transitionTestId(
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(predecessor)
		)
		val committedOwner = transitionTestNativeOwner(committedId, predecessor)
		val predecessorKey = ReaderTransitionResourceKey(
			committedId,
			ReaderTransitionResourceKind.Deck,
			committedOwner.proof.textureGeneration
		)
		val idle = ReaderTransitionJournal(
			committed = ReaderCommittedTransition(
				committedId,
				committedOwner,
				predecessor,
				predecessorKey,
				transitionTestRegistration(predecessorKey)
			)
		)
		val successor = transitionTestBinding(commitSequence = 2L, rasterGeneration = 23L, textureGeneration = 29L)

		val result = idle.reduce(ReaderTransitionFact.FoliateDestinationCommitted(null, successor))

		val active = requireNotNull(result.state.active)
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, active.id.operation)
		assertEquals(committedId.parentIdentity(), active.id.parent)
		assertEquals(committedOwner, active.phase.contract.retainedOwner)
		assertEquals(predecessorKey, active.predecessorResourceKey)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, active.phase.contract.inputLease)
		assertTrue(result.commands.none { it is ReaderTransitionCommand.CancelOwnedWork })
		assertIs<ReaderTransitionCommand.PublishRetainedOwnerAndInputLease>(result.commands.single())
		assertTrue(result.commands.none { it is ReaderTransitionCommand.AllocateMaterialBinding })
	}

	@Test
	fun shellAndLiveProofsCommitExactSuccessorOwnerAndReleaseOnlyPredecessor() {
		val binding = transitionTestBinding()
		val operations = listOf(
			ReaderTransitionOperation.ShellCoverCommit,
			ReaderTransitionOperation.NativeToLiveHandoff
		)

		operations.forEach { operation ->
			val predecessorId = transitionTestId(
				ReaderTransitionOperation.BootstrapNativePage,
				ReaderExpectedPresentationBinding.Exact(binding),
				sequence = 12L
			)
			val id = transitionTestId(operation, ReaderExpectedPresentationBinding.Exact(binding))
			val predecessor = transitionTestNativeOwner(predecessorId, binding)
			val predecessorKey = ReaderTransitionResourceKey(
				predecessorId,
				ReaderTransitionResourceKind.Deck,
				predecessor.proof.textureGeneration
			)
			val phase = ReaderTransitionLivenessTable.phase(id, ReaderTransitionPhaseKind.AwaitingProof, predecessor)
			val journal = ReaderTransitionJournal(
				active = ReaderActiveTransition(id, phase, predecessorResourceKey = predecessorKey),
				committed = ReaderCommittedTransition(
					predecessorId,
					predecessor,
					binding,
					predecessorKey,
					transitionTestRegistration(predecessorKey)
				)
			)
			val successor = when (operation) {
				ReaderTransitionOperation.ShellCoverCommit -> transitionTestCoverOwner(id, binding)
				ReaderTransitionOperation.NativeToLiveHandoff -> transitionTestLiveOwner(id, binding)
				else -> error("unreachable")
			}
			val successorKey = ReaderTransitionResourceKey(
				id,
				ReaderTransitionResourceKind.FrameHandoff,
				67L
			)
			val preparedFact = transitionTestPreparedFrame(id, binding, successor, successorKey)
			val awaitingFramePhase = phase.copy(
				contract = phase.contract.copy(
					awaitedProofs = setOf(ReaderTransitionProofKind.PreparedFrame),
					callbackSources = setOf(ReaderTransitionFactKind.PreparedFrame)
				)
			)
			val committing = journal.copy(
				active = requireNotNull(journal.active).copy(
					phase = awaitingFramePhase,
					frameTarget = preparedFact.target
				)
			).reduce(preparedFact)
			assertEquals(ReaderTransitionPhaseKind.Committing, committing.state.active?.phase?.kind)
			val reduction = transitionTestApplySuccessorAcknowledgement(committing)

			assertNull(reduction.state.active)
			assertEquals(successor, assertIs<ReaderTransitionOutcome.Succeeded>(reduction.state.lastOutcome).committedOwner)
			val releases = reduction.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			assertEquals(listOf(predecessorKey), releases.map { it.key })
			val lease = committing.commands.filterIsInstance<ReaderTransitionCommand.CommitOwnerAndInputLease>().single().requestedLease
			when (operation) {
				ReaderTransitionOperation.ShellCoverCommit ->
					assertEquals(ReaderTransitionInputLease.CoverActions, lease)
				ReaderTransitionOperation.NativeToLiveHandoff ->
					assertFalse(lease is ReaderTransitionInputLease.NativePage)
			}
		}
	}

	@Test
	fun liveToNativeRejectsLiveFrameAndCommitsNativeOwnerWithNativeLease() {
		val binding = transitionTestBinding()
		val predecessorId = transitionTestId(
			ReaderTransitionOperation.NativeToLiveHandoff,
			ReaderExpectedPresentationBinding.Exact(binding),
			sequence = 12L
		)
		val id = transitionTestId(
			ReaderTransitionOperation.LiveToNativeHandback,
			ReaderExpectedPresentationBinding.Exact(binding)
		)
		val predecessor = transitionTestLiveOwner(predecessorId, binding)
		val predecessorKey = ReaderTransitionResourceKey(
			predecessorId,
			ReaderTransitionResourceKind.FrameHandoff,
			predecessor.proof.presentedFrameSequence
		)
		val phase = ReaderTransitionLivenessTable.phase(id, ReaderTransitionPhaseKind.AwaitingProof, predecessor)
		val journal = ReaderTransitionJournal(
			active = ReaderActiveTransition(id, phase, predecessorResourceKey = predecessorKey),
			committed = ReaderCommittedTransition(
				predecessorId,
				predecessor,
				binding,
				predecessorKey,
				transitionTestRegistration(predecessorKey)
			)
		)
		val wrongOwner = transitionTestLiveOwner(id, binding)
		val wrongKey = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.FrameHandoff, 71L)

		val rejected = journal.reduce(
			transitionTestPreparedFrame(id, binding, wrongOwner, wrongKey)
		)

		assertEquals(journal, rejected.state)
		assertEquals(
			listOf(wrongKey),
			rejected.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)

		val nativeOwner = transitionTestNativeOwner(id, binding)
		val nativeKey = ReaderTransitionResourceKey(
			id,
			ReaderTransitionResourceKind.Deck,
			nativeOwner.proof.textureGeneration
		)
		val preparedFact = transitionTestPreparedFrame(id, binding, nativeOwner, nativeKey)
		val awaitingExactTarget = rejected.state.copy(
			active = requireNotNull(rejected.state.active).copy(frameTarget = preparedFact.target)
		)
		val committing = awaitingExactTarget.reduce(preparedFact)
		assertEquals(ReaderTransitionPhaseKind.Committing, committing.state.active?.phase?.kind)
		val committed = transitionTestApplySuccessorAcknowledgement(committing)
		assertEquals(nativeOwner, assertIs<ReaderTransitionOutcome.Succeeded>(committed.state.lastOutcome).committedOwner)
		assertTrue(committed.state.committed.retainedResourceKeyForTest() == nativeKey, "Native resource mismatch")
		assertEquals(
			ReaderTransitionInputLease.NativePage(binding, nativeOwner.proof.textureGeneration),
			committing.commands.filterIsInstance<ReaderTransitionCommand.CommitOwnerAndInputLease>().single().requestedLease
		)
		assertEquals(
			listOf(predecessorKey),
			committed.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun rejectedPreparedFramesDrainExactStaleAndMismatchedResourcesButNotRetainedShield() {
		val fixture = journalAwaitingSettlement()
		val staleId = fixture.id.copy(
			sequence = 11L,
			parent = ReaderTransitionParentIdentity(
				fixture.id.readerSessionGeneration,
				fixture.id.coordinatorEpoch,
				10L
			)
		)
		val staleOwner = transitionTestNativeOwner(staleId, fixture.successor)
		val staleKey = ReaderTransitionResourceKey(
			staleId,
			ReaderTransitionResourceKind.Deck,
			89L
		)
		val mismatchedBinding = fixture.successor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.successor.foliateSessionId,
				3L
			)
		)
		val mismatchedOwner = transitionTestNativeOwner(fixture.id, mismatchedBinding)
		val mismatchedKey = ReaderTransitionResourceKey(
			fixture.id,
			ReaderTransitionResourceKind.Deck,
			97L
		)
		val rejectedFacts = listOf(
			transitionTestPreparedFrame(
				staleId,
				fixture.successor,
				staleOwner,
				staleKey
			),
			transitionTestPreparedFrame(
				fixture.id,
				mismatchedBinding,
				mismatchedOwner,
				mismatchedKey
			)
		)

		rejectedFacts.zip(listOf(staleKey, mismatchedKey)).forEach { (fact, expectedKey) ->
			val rejected = fixture.journal.reduce(fact)
			assertEquals(fixture.journal, rejected.state)
			assertEquals(
				listOf(expectedKey),
				rejected.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
			)
		}

		val retained = fixture.journal.reduce(
			transitionTestPreparedFrame(
				requireNotNull(fixture.predecessorResourceKey.owningTransitionIdOrNull),
				fixture.predecessor,
				fixture.retainedOwner,
				fixture.predecessorResourceKey
			)
		)
		assertEquals(fixture.journal, retained.state)
		assertTrue(retained.commands.isEmpty())
	}

	@Test
	fun deckProofsRequireCurrentExactAdmittedResourceKey() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val admittedResult = transitionTestApplyRetainedAcknowledgement(started)
		val id = requireNotNull(admittedResult.state.active).id
		val admitted = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 73L)
		val wrongTransition = admitted.copy(transitionId = fixture.id)
		val wrongDeck = admitted.copy(opaqueId = 79L)
		val allocated = admittedResult.state.reduce(testAllocationFact(id, fixture.successor))

		val staleOwned = allocated.state.reduce(ReaderTransitionFact.DeckOwned(id, wrongTransition))
		assertEquals(allocated.state, staleOwned.state)
		assertEquals(
			listOf(wrongTransition),
			staleOwned.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)

		val owned = staleOwned.state.reduce(ReaderTransitionFact.DeckOwned(id, admitted))
		assertEquals(admitted, owned.state.active?.admittedDeckKey)
		assertFalse(ReaderTransitionProofKind.DeckOwnership in requireNotNull(owned.state.active).phase.contract.awaitedProofs)

		val mismatchedPrepared = owned.state.reduce(ReaderTransitionFact.DeckPrepared(id, wrongDeck))
		assertTrue(ReaderTransitionProofKind.DeckPrepared in requireNotNull(mismatchedPrepared.state.active).phase.contract.awaitedProofs)
		assertEquals(
			listOf(wrongDeck),
			mismatchedPrepared.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)

		val prepared = mismatchedPrepared.state.reduce(ReaderTransitionFact.DeckPrepared(id, admitted))
		val preparedActive = requireNotNull(prepared.state.active)
		assertFalse(ReaderTransitionProofKind.DeckPrepared in preparedActive.phase.contract.awaitedProofs)
		assertEquals(admitted, preparedActive.admittedDeckKey)
	}

	@Test
	fun relocationTerminalPathsDrainAllNonPredecessorResourcesOnce() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val active = requireNotNull(started.state.active)
		val shared = ReaderTransitionResourceKey(
			active.id,
			ReaderTransitionResourceKind.Deck,
			103L
		)
		val pending = shared.copy(opaqueId = 107L)
		val populated = started.state.copy(
			active = active.copy(
				admittedDeckKey = shared,
				pendingPreparedDeckKey = pending,
				successorResourceKey = shared
			)
		)
		val rejected = shared.copy(opaqueId = 109L)
		val resumeRecord = ReaderTransitionResumeRecord(
			operation = active.id.operation,
			reason = ReaderTransitionDeferralReason.RendererCapacityUnavailable,
			nonce = ReaderTransitionNonce(113L, 127L),
			issuedAtMillis = 2_000L,
			expiresAtMillis = 902_000L,
			remainingRestorations = 1,
			requiredWake = ReaderTransitionWakeKind.RendererCapacityAvailable
		)
		val terminalCases = listOf(
			ReaderTransitionFact.RasterFailed(
				active.id,
				ReaderTransitionFailureReason.StaleProof
			) to listOf(shared, pending),
			ReaderTransitionFact.DeckRejected(
				active.id,
				rejected
			) to listOf(shared, pending, rejected),
			ReaderTransitionFact.DeadlineExpired(active.id) to listOf(shared, pending),
			ReaderTransitionFact.RasterDeferred(
				active.id,
				ReaderTransitionDeferralReason.RendererCapacityUnavailable,
				resumeRecord
			) to listOf(shared, pending)
		)

		terminalCases.forEach { (fact, releasedKeys) ->
			val terminal = populated.reduce(fact)
			assertNull(terminal.state.active)
			assertTrue(terminal.state.committed.retainedResourceKeyForTest() == fixture.predecessorResourceKey, "Terminal retained resource mismatch")
			assertEquals(
				listOf(ReaderTransitionCommand.CancelOwnedWork(active.id)) +
					releasedKeys.map { key ->
						ReaderTransitionCommand.ReleaseResource(active.id, key)
					},
				terminal.commands
			)
		}
	}

	@Test
	fun currentResourceRegistrationRetainsExactKeysWithoutRelease() {
		val fixture = journalRelocatingFromLivePredecessor()
		val activeId = requireNotNull(fixture.journal.active).id
		val reserved = ReaderTransitionResourceKey(
			activeId,
			ReaderTransitionResourceKind.Deck,
			139L
		)
		val observed = ReaderTransitionResourceKey(
			activeId,
			ReaderTransitionResourceKind.Raster,
			149L
		)

		val afterReserved = fixture.journal.reduce(
			ReaderTransitionFact.DeckReserved(activeId, reserved)
		)
		assertTrue(afterReserved.commands.isEmpty())
		assertEquals(setOf(reserved), requireNotNull(afterReserved.state.active).ownedResourceKeys)
		val afterObserved = afterReserved.state.reduce(
			ReaderTransitionFact.ResourceObserved(activeId, observed)
		)
		assertTrue(afterObserved.commands.isEmpty())
		assertEquals(
			setOf(reserved, observed),
			requireNotNull(afterObserved.state.active).ownedResourceKeys
		)

		val terminal = afterObserved.state.reduce(
			ReaderTransitionFact.DeadlineExpired(activeId)
		)
		assertEquals(
			listOf(reserved, observed),
			terminal.commands
				.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
				.map { it.key }
		)
	}

	@Test
	fun releaseConfirmationRemovesCurrentOwnershipAndNeverRequestsAnotherRelease() {
		val fixture = journalRelocatingFromLivePredecessor()
		val activeId = requireNotNull(fixture.journal.active).id
		val key = ReaderTransitionResourceKey(
			activeId,
			ReaderTransitionResourceKind.Deck,
			151L
		)
		val registered = fixture.journal.reduce(
			ReaderTransitionFact.DeckReserved(activeId, key)
		).state

		val confirmed = registered.reduce(
			ReaderTransitionFact.ResourceReleased(activeId, key)
		)
		assertTrue(confirmed.commands.isEmpty())
		assertFalse(key in requireNotNull(confirmed.state.active).ownedResourceKeys)
		val duplicate = confirmed.state.reduce(
			ReaderTransitionFact.ResourceReleased(activeId, key)
		)
		assertEquals(confirmed.state, duplicate.state)
		assertTrue(duplicate.commands.isEmpty())

		val staleKey = key.copy(
			transitionId = requireNotNull(fixture.predecessorKey.owningTransitionIdOrNull),
			opaqueId = 157L
		)
		val stale = duplicate.state.reduce(
			ReaderTransitionFact.ResourceReleased(requireNotNull(staleKey.owningTransitionIdOrNull), staleKey)
		)
		assertEquals(duplicate.state, stale.state)
		assertTrue(stale.commands.isEmpty())

		val terminal = stale.state.reduce(
			ReaderTransitionFact.DeadlineExpired(activeId)
		)
		assertTrue(
			terminal.commands
				.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
				.none { it.key == key }
		)
	}

	@Test
	fun preparedFrameDuplicateIsNoOpButDistinctSecondKeyIsReleased() {
		val fixture = journalAwaitingSettlement()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val admitted = transitionTestApplyRetainedAcknowledgement(started)
		val activeId = requireNotNull(admitted.state.active).id
		val owner = transitionTestNativeOwner(activeId, fixture.successor)
		val keyA = ReaderTransitionResourceKey(
			activeId,
			ReaderTransitionResourceKind.Deck,
			requireNotNull(fixture.successor.textureGeneration)
		)
		val keyB = keyA.copy(opaqueId = keyA.opaqueId + 1L)
		val allocated = admitted.state.reduce(testAllocationFact(activeId, fixture.successor))
		var state = allocated.state.withTransitionTestNativeTarget(
			activeId,
			fixture.successor,
			keyA
		)
		state = state.reduce(ReaderTransitionFact.RasterProven(activeId)).state
		state = state.reduce(ReaderTransitionFact.DeckOwned(activeId, keyA)).state
		val deckPrepared = state.reduce(ReaderTransitionFact.DeckPrepared(activeId, keyA))
		val (awaitingFrame, targetA) = transitionTestApplyTargetPreparation(deckPrepared)
		val frameA = ReaderTransitionFact.PreparedFrame(
			activeId,
			targetA,
			owner,
			targetA.resource
		)

		val retained = awaitingFrame.reduce(frameA)
		assertEquals(keyA, retained.state.active?.successorResourceKey)
		val duplicate = retained.state.reduce(frameA)
		assertEquals(retained.state, duplicate.state)
		assertTrue(duplicate.commands.isEmpty())

		val conflicting = retained.state.reduce(
			transitionTestPreparedFrame(
				activeId,
				fixture.successor,
				owner,
				keyB
			)
		)
		assertEquals(retained.state, conflicting.state)
		assertEquals(keyA, conflicting.state.active?.successorResourceKey)
		assertEquals(
			listOf(keyB),
			conflicting.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun everyStaleResourceFactUsesSemanticDispositionAndProtectsPredecessor() {
		val fixture = journalRelocatingFromLivePredecessor()
		val activeId = requireNotNull(fixture.journal.active).id
		val unprotected = fixture.predecessorKey.copy(opaqueId = 127L)
		val cases = resourceBearingFactCases()
		assertEquals(
			setOf(
				"ResourceObserved",
				"DeckReserved",
				"DeckOwned",
				"DeckPrepared",
				"DeckRejected",
				"ResourceReleased",
				"PreparedFrame",
				"CoverPostDraw",
				"WebViewExposure"
			),
			cases.map { it.name }.toSet()
		)

		cases.forEach { case ->
			val protected = fixture.journal.reduce(
				case.create(
					requireNotNull(fixture.predecessorKey.owningTransitionIdOrNull),
					fixture.predecessor,
					fixture.predecessorKey
				)
			)
			assertEquals(fixture.journal, protected.state, case.name)
			assertTrue(protected.commands.isEmpty(), case.name)

			val rejected = fixture.journal.reduce(
				case.create(
					requireNotNull(unprotected.owningTransitionIdOrNull),
					fixture.predecessor,
					unprotected
				)
			)
			assertEquals(fixture.journal, rejected.state, case.name)
			assertEquals(
				if (case.staleUnprotectedReleases) {
					listOf(ReaderTransitionCommand.ReleaseResource(activeId, unprotected))
				} else {
					emptyList()
				},
				rejected.commands,
				case.name
			)
		}
	}

	@Test
	fun mismatchedCoverAndLiveProofsReleaseExactFrameHandoffResources() {
		val fixture = journalRelocatingFromLivePredecessor()
		val active = requireNotNull(fixture.journal.active)
		val mismatchedBinding = fixture.predecessor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.predecessor.foliateSessionId,
				7L
			)
		)
		val facts = listOf(
			ReaderTransitionFact.CoverPostDraw(
				active.id,
				mismatchedBinding,
				transitionTestCoverOwner(active.id, mismatchedBinding),
				ReaderTransitionResourceKey(
					active.id,
					ReaderTransitionResourceKind.FrameHandoff,
					131L
				)
			),
			ReaderTransitionFact.WebViewExposure(
				active.id,
				mismatchedBinding,
				transitionTestLiveOwner(active.id, mismatchedBinding),
				ReaderTransitionResourceKey(
					active.id,
					ReaderTransitionResourceKind.FrameHandoff,
					137L
				)
			)
		)

		facts.forEach { fact ->
			val expectedKey = when (fact) {
				is ReaderTransitionFact.CoverPostDraw -> fact.resourceKey
				is ReaderTransitionFact.WebViewExposure -> fact.resourceKey
				else -> error("unreachable")
			}
			val rejected = fixture.journal.reduce(fact)
			assertEquals(fixture.journal, rejected.state)
			assertEquals(
				listOf(ReaderTransitionCommand.ReleaseResource(active.id, expectedKey)),
				rejected.commands
			)
		}
	}

	@Test
	fun destinationFromAnotherPublicationNeverRelocatesTheOldLifecycle() {
		val fixture = journalAwaitingSettlement()
		val anotherPublication = fixture.successor.copy(
			foliateSessionId = "different-publication-session",
			publicationGeneration = fixture.successor.publicationGeneration + 1L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"different-publication-session",
				1L
			)
		)

		val rejected = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, anotherPublication)
		)

		assertEquals(fixture.journal, rejected.state)
		assertTrue(rejected.commands.isEmpty())
	}

	@Test
	fun activeOnlyBootstrapAndRestoreFenceForeignPublicationDestinations() {
		val local = transitionTestBinding(commitSequence = 1L)
		val foreign = local.copy(
			foliateSessionId = "foreign-active-only-session",
			publicationGeneration = local.publicationGeneration + 1L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"foreign-active-only-session",
				1L
			)
		)
		val cases = listOf(
			ReaderTransitionOperation.BootstrapNativePage to ReaderExpectedPresentationBinding.Exact(local),
			ReaderTransitionOperation.VisibilityRestore to
				ReaderExpectedPresentationBinding.SemanticSuccessor(local, requestSequence = 17L)
		)

		cases.forEach { (operation, expectedBinding) ->
			val id = transitionTestId(operation, expectedBinding)
			val journal = ReaderTransitionJournal(
				active = ReaderActiveTransition(
					id = id,
					phase = ReaderTransitionLivenessTable.phase(
						id,
						ReaderTransitionPhaseKind.AwaitingPrerequisites,
						ReaderPresentationFrameOwner.Neutral
					)
				),
				committed = transitionTestNeutralCommitted(id),
				lastTransitionSequence = id.sequence,
				lastIssuedTransitionIdentity = id.parentIdentity()
			)

			val rejected = journal.reduce(
				ReaderTransitionFact.FoliateDestinationCommitted(null, foreign)
			)

			assertEquals(journal, rejected.state, operation.name)
			assertTrue(rejected.commands.isEmpty(), operation.name)
		}
	}

	@Test
	fun retryableOnlyIdentityFencesForeignPublicationDestination() {
		val fixture = journalAwaitingSettlement()
		val localId = fixture.id.copy(
			expectedBinding = ReaderExpectedPresentationBinding.SemanticSuccessor(
				fixture.predecessor,
				requestSequence = 19L
			)
		)
		val retryable = ReaderRetryableTransition(
			id = localId,
			retainedOwner = fixture.retainedOwner,
			predecessorResourceKey = fixture.predecessorResourceKey,
			semanticIntent = ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Toc,
					transitionTestSemanticHandle()
				),
			resolvedSuccessorBinding = null,
			gestureId = null,
			settlementConsumed = false,
			semanticDestinationCommitted = false
		)
		val journal = ReaderTransitionJournal(
			committed = fixture.journal.committed,
			retryableTransition = retryable,
			lastTransitionSequence = localId.sequence,
			lastIssuedTransitionIdentity = localId.parentIdentity()
		)
		val foreign = fixture.successor.copy(
			foliateSessionId = "foreign-retryable-only-session",
			publicationGeneration = fixture.successor.publicationGeneration + 1L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"foreign-retryable-only-session",
				1L
			)
		)

		val rejected = journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, foreign)
		)

		assertEquals(journal, rejected.state)
		assertTrue(rejected.commands.isEmpty())
	}

	@Test
	fun publicationReplacementCancelsActiveRelocationAndReleasesEveryOldResourceOnce() {
		val fixture = journalRelocatingFromLivePredecessor()
		val active = assertNotNull(fixture.journal.active)
		val raster = ReaderTransitionResourceKey(active.id, ReaderTransitionResourceKind.Raster, 223L)
		val deck = ReaderTransitionResourceKey(active.id, ReaderTransitionResourceKind.Deck, 227L)
		val populated = fixture.journal.copy(
			active = active.copy(
				ownedResourceKeys = setOf(raster, deck),
				admittedDeckKey = deck,
				pendingPreparedDeckKey = deck,
				successorResourceKey = deck
			)
		)

		val replaced = populated.reduce(ReaderTransitionFact.PublicationReplaced(null))

		assertNull(replaced.state.active)
		assertTrue(replaced.state.committed === populated.committed)
		assertNull(replaced.state.retryableTransition)
		assertEquals(
			ReaderTransitionCancellationReason.PublicationReplaced,
			assertIs<ReaderTransitionOutcome.Cancelled>(replaced.state.lastOutcome).reason
		)
		assertEquals(ReaderTransitionCommand.CancelOwnedWork(active.id), replaced.commands.first())
		assertEquals(
			setOf(fixture.predecessorKey, raster, deck),
			replaced.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }.toSet()
		)
		assertEquals(
			replaced.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size,
			replaced.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }.distinct().size
		)
	}

	@Test
	fun publicationReplacementAfterFailedRelocationClearsRetryAndReleasesCommittedShield() {
		val fixture = journalAwaitingSettlement()
		val idle = fixture.idleCommittedJournal()
		val started = idle.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Search,
					transitionTestSemanticHandle()
				)
			)
		)
		val relocationId = assertNotNull(started.state.active).id
		val failed = started.state.reduce(ReaderTransitionFact.DeadlineExpired(relocationId))
		assertNotNull(failed.state.retryableTransition)

		val replaced = failed.state.reduce(ReaderTransitionFact.PublicationReplaced(null))

		assertNull(replaced.state.active)
		assertTrue(replaced.state.committed === failed.state.committed)
		assertNull(replaced.state.retryableTransition)
		assertEquals(
			listOf(fixture.predecessorResourceKey),
			replaced.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun publicationReplacementReleasesExactCommittedResourceProvenance() {
		val binding = transitionTestBinding()
		val id = transitionTestId(
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(binding)
		)
		val owner = transitionTestNativeOwner(id, binding)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, owner.proof.textureGeneration)
		val journal = ReaderTransitionJournal(
			committed = ReaderCommittedTransition(id, owner, binding, key, transitionTestRegistration(key))
		)

		val replaced = journal.reduce(ReaderTransitionFact.PublicationReplaced(null))

		assertNull(replaced.state.active)
		assertTrue(replaced.state.committed === journal.committed)
		assertEquals(
			listOf(key),
			replaced.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun rejectSuccessorCloseTransitionIgnoresUntaggedDestination() {
		val fixture = journalAwaitingPublicationClose()
		val successor = fixture.binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.binding.foliateSessionId,
				2L
			)
		)

		val rejected = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, successor)
		)

		assertEquals(ReaderTransitionSupersession.RejectSuccessor, fixture.journal.active?.phase?.contract?.supersession)
		assertEquals(fixture.journal, rejected.state)
		assertTrue(rejected.commands.isEmpty())
	}

	@Test
	fun taggedPublicationCloseRequiresExactActiveCloseIdentity() {
		val fixture = journalAwaitingPublicationClose()
		val staleIds = listOf(
			fixture.id.copy(
				readerSessionGeneration = fixture.id.readerSessionGeneration + 1L,
				parent = requireNotNull(fixture.id.parent).copy(
					readerSessionGeneration = fixture.id.readerSessionGeneration + 1L
				)
			),
			fixture.id.copy(
				coordinatorEpoch = fixture.id.coordinatorEpoch + 1L,
				parent = requireNotNull(fixture.id.parent).copy(
					coordinatorEpoch = fixture.id.coordinatorEpoch + 1L
				)
			),
			fixture.id.copy(
				sequence = fixture.id.sequence + 1L,
				parent = fixture.id.parentIdentity()
			)
		)

		staleIds.forEach { staleId ->
			val stale = fixture.journal.reduce(
				ReaderTransitionFact.PublicationClosed(staleId)
			)
			assertEquals(fixture.journal, stale.state)
			assertTrue(stale.commands.isEmpty())
		}

		val nonClose = journalAwaitingSettlement()
		val wrongOperation = nonClose.journal.reduce(
			ReaderTransitionFact.PublicationClosed(nonClose.id)
		)
		assertEquals(nonClose.journal, wrongOperation.state)
		assertTrue(wrongOperation.commands.isEmpty())

		val exact = fixture.journal.reduce(
			ReaderTransitionFact.PublicationClosed(fixture.id)
		)
		assertNull(exact.state.active)
		assertTrue(exact.state.committed === fixture.journal.committed)
		assertIs<ReaderTransitionOutcome.Cancelled>(exact.state.lastOutcome)
	}

	@Test
	fun publicationCloseReleasesExactCommittedResourceProvenance() {
		val binding = transitionTestBinding()
		val id = transitionTestId(
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(binding)
		)
		val owner = transitionTestNativeOwner(id, binding)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, owner.proof.textureGeneration)
		val journal = ReaderTransitionJournal(
			committed = ReaderCommittedTransition(id, owner, binding, key, transitionTestRegistration(key))
		)

		val closed = journal.reduce(ReaderTransitionFact.PublicationClosed(null))

		assertNull(closed.state.active)
		assertTrue(closed.state.committed === journal.committed)
		assertEquals(
			listOf(key),
			closed.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun deferralTerminatesPhysicalAttemptAndRetainsExplicitResumeRecordUnchanged() {
		val binding = transitionTestBinding()
		val id = transitionTestId(
			ReaderTransitionOperation.CoverToPageEntry,
			ReaderExpectedPresentationBinding.Exact(binding)
		)
		val retainedOwner = transitionTestNativeOwner(id, binding)
		val journal = ReaderTransitionJournal(
			active = ReaderActiveTransition(
				id,
				ReaderTransitionLivenessTable.phase(
					id,
					ReaderTransitionPhaseKind.AwaitingPrerequisites,
					retainedOwner
				)
			),
			committed = transitionTestNeutralCommitted(id),
			lastTransitionSequence = id.sequence,
			lastIssuedTransitionIdentity = id.parentIdentity()
		)
		val resumeRecord = ReaderTransitionResumeRecord(
			operation = id.operation,
			reason = ReaderTransitionDeferralReason.RendererCapacityUnavailable,
			nonce = ReaderTransitionNonce(41L, 43L),
			issuedAtMillis = 2_000L,
			expiresAtMillis = 902_000L,
			remainingRestorations = 1,
			requiredWake = ReaderTransitionWakeKind.RendererCapacityAvailable
		)
		val result = journal.reduce(
			ReaderTransitionFact.RasterDeferred(
				transitionId = id,
				reason = ReaderTransitionDeferralReason.RendererCapacityUnavailable,
				resumeRecord = resumeRecord
			),
			nowMillis = 7_000L
		)

		assertNull(result.state.active)
		val deferred = assertIs<ReaderTransitionOutcome.Deferred>(result.state.lastOutcome)
		assertEquals(retainedOwner, deferred.retainedOwner)
		assertEquals(resumeRecord, deferred.resumeRecord)
		assertEquals(ReaderTransitionNonce(41L, 43L), deferred.resumeRecord.nonce)
		assertEquals(2_000L, deferred.resumeRecord.issuedAtMillis)
		assertEquals(902_000L, deferred.resumeRecord.expiresAtMillis)
		assertTrue(result.commands.none { it.startsNewPhysicalAttempt() })
	}

	@Test
	fun deferredFactRejectsResumeRecordForAnotherReason() {
		val fixture = journalAwaitingSettlement()
		val record = ReaderTransitionResumeRecord(
			operation = fixture.id.operation,
			reason = ReaderTransitionDeferralReason.RendererCapacityUnavailable,
			nonce = ReaderTransitionNonce(41L, 43L),
			issuedAtMillis = 2_000L,
			expiresAtMillis = 902_000L,
			remainingRestorations = 1,
			requiredWake = ReaderTransitionWakeKind.RendererCapacityAvailable
		)

		assertFailsWith<IllegalArgumentException> {
			ReaderTransitionFact.RasterDeferred(
				transitionId = fixture.id,
				reason = ReaderTransitionDeferralReason.HostUnavailable,
				resumeRecord = record
			)
		}
	}

	@Test
	fun rasterDeferralRejectsOperationIncompatibleReasonAndWake() {
		val fixture = journalAwaitingSettlement()
		val record = ReaderTransitionResumeRecord(
			operation = fixture.id.operation,
			reason = ReaderTransitionDeferralReason.HostUnavailable,
			nonce = ReaderTransitionNonce(41L, 43L),
			issuedAtMillis = 2_000L,
			expiresAtMillis = 902_000L,
			remainingRestorations = 1,
			requiredWake = ReaderTransitionWakeKind.HostAvailable
		)

		assertFailsWith<IllegalArgumentException> {
			ReaderTransitionFact.RasterDeferred(
				transitionId = fixture.id,
				reason = ReaderTransitionDeferralReason.HostUnavailable,
				resumeRecord = record
			)
		}
	}

	@Test
	fun resumeRecordRejectsUnboundedLifetimeOrRestorationBudget() {
		assertFailsWith<IllegalArgumentException> {
			ReaderTransitionResumeRecord(
				operation = ReaderTransitionOperation.VisibilityRestore,
				reason = ReaderTransitionDeferralReason.VisibilityRestore,
				nonce = ReaderTransitionNonce(1L, 2L),
				issuedAtMillis = 5L,
				expiresAtMillis = 5L,
				remainingRestorations = 1,
				requiredWake = ReaderTransitionWakeKind.VisibilityRestored
			)
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderTransitionResumeRecord(
				operation = ReaderTransitionOperation.VisibilityRestore,
				reason = ReaderTransitionDeferralReason.VisibilityRestore,
				nonce = ReaderTransitionNonce(1L, 2L),
				issuedAtMillis = 5L,
				expiresAtMillis = 6L,
				remainingRestorations = 2,
				requiredWake = ReaderTransitionWakeKind.VisibilityRestored
			)
		}
	}

	@Test
	fun factAndCommandContractsCoverAllDesignIngressWithoutAndroidDeckRoles() {
		val requiredFacts = setOf(
			ReaderTransitionFactKind.Intent,
			ReaderTransitionFactKind.FoliateDestinationCommitted,
			ReaderTransitionFactKind.SettlementAcknowledged,
			ReaderTransitionFactKind.ViewportProfileReplaced,
			ReaderTransitionFactKind.RasterProgress,
			ReaderTransitionFactKind.RasterProven,
			ReaderTransitionFactKind.RasterDeferred,
			ReaderTransitionFactKind.RasterFailed,
			ReaderTransitionFactKind.ResourceObserved,
			ReaderTransitionFactKind.DeckReserved,
			ReaderTransitionFactKind.DeckOwned,
			ReaderTransitionFactKind.DeckPrepared,
			ReaderTransitionFactKind.DeckRejected,
			ReaderTransitionFactKind.ResourceReleased,
			ReaderTransitionFactKind.RendererCapacityAvailable,
			ReaderTransitionFactKind.HostAvailable,
			ReaderTransitionFactKind.PaginationProfileReady,
			ReaderTransitionFactKind.RendererGenerationReady,
			ReaderTransitionFactKind.PreparedFrame,
			ReaderTransitionFactKind.CoverPostDraw,
			ReaderTransitionFactKind.WebViewExposure,
			ReaderTransitionFactKind.VisibilityChanged,
			ReaderTransitionFactKind.ResourceLost,
			ReaderTransitionFactKind.DeadlineExpired,
			ReaderTransitionFactKind.Retry,
			ReaderTransitionFactKind.PublicationReplaced,
			ReaderTransitionFactKind.PublicationClosed
		)
		assertTrue(ReaderTransitionFactKind.entries.containsAll(requiredFacts))

		val binding = transitionTestBinding()
		val id = transitionTestId(
			ReaderTransitionOperation.CoverToPageEntry,
			ReaderExpectedPresentationBinding.Exact(binding)
		)
		val command: ReaderTransitionCommand = ReaderTransitionCommand.ReserveDeck(
			transitionId = id,
			binding = binding,
			role = ReaderTransitionDeckRole.PageEntry
		)
		assertEquals(ReaderTransitionDeckRole.PageEntry, assertIs<ReaderTransitionCommand.ReserveDeck>(command).role)
	}

	@Test
	fun initialJournalRejectsSequenceOrIdentityBeforeAnyAuthenticOperation() {
		val committed = neutralInitialJournal().committed
		val fabricatedIdentity = ReaderTransitionParentIdentity(7L, 11L, 1L)

		assertFailsWith<IllegalArgumentException>("Initial journal must reject a positive sequence without identity") {
			ReaderTransitionJournal(
				committed = committed,
				lastTransitionSequence = 1L
			)
		}
		assertFailsWith<IllegalArgumentException>("Initial journal must reject identity at sequence zero") {
			ReaderTransitionJournal(
				committed = committed,
				lastIssuedTransitionIdentity = fabricatedIdentity
			)
		}
	}

	@Test
	fun transitionedJournalRejectsMissingOrMismatchedLastIssuedIdentity() {
		val fixture = journalAwaitingSettlement()
		val committed = requireNotNull(fixture.journal.committed)
		val committedId = assertIs<ReaderCommittedPresentation.Transition>(committed).committed.id

		assertFailsWith<IllegalArgumentException>("Transitioned journal must reject sequence zero") {
			ReaderTransitionJournal(committed = committed)
		}
		assertFailsWith<IllegalArgumentException>("Transitioned journal must reject mismatched identity") {
			ReaderTransitionJournal(
				committed = committed,
				lastTransitionSequence = committedId.sequence,
				lastIssuedTransitionIdentity = committedId.copy(
					sequence = committedId.sequence + 1L
				).parentIdentity()
			)
		}
	}

	@Test
	fun adoptedBaselineFirstSuccessorStartsAtOneWithoutParentAndReleasesAfterApplied() {
		val fixture = legacyAdoptedBaselineFixture()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val first = assertNotNull(started.state.active)
		assertNull(fixture.adoptedResource.key.owningTransitionIdOrNull)
		assertEquals(1L, first.id.sequence)
		assertNull(first.id.parent)
		assertTrue(started.commands.none { it is ReaderTransitionCommand.ReleaseResource })

		val retained = transitionTestApplyRetainedAcknowledgement(started)
		val allocated = retained.state.reduce(testAllocationFact(first.id, fixture.successor))
		val successorKey = ReaderTransitionResourceKey(
			first.id,
			ReaderTransitionResourceKind.Deck,
			requireNotNull(fixture.successor.textureGeneration)
		)
		var state = allocated.state.withTransitionTestNativeTarget(
			first.id,
			fixture.successor,
			successorKey
		)
		state = state.reduce(ReaderTransitionFact.RasterProven(first.id)).state
		state = state.reduce(ReaderTransitionFact.DeckOwned(first.id, successorKey)).state
		val preparedDeck = state.reduce(ReaderTransitionFact.DeckPrepared(first.id, successorKey))
		val (awaitingFrame, target) = transitionTestApplyTargetPreparation(preparedDeck)
		val committing = awaitingFrame.reduce(
			ReaderTransitionFact.PreparedFrame(
				first.id,
				target,
				transitionTestNativeOwner(first.id, fixture.successor),
				target.resource
			)
		)
		assertTrue(committing.commands.none { it is ReaderTransitionCommand.ReleaseResource })

		val applied = transitionTestApplySuccessorAcknowledgement(committing)
		val release = applied.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().single()
		assertTrue(release.key == fixture.adoptedResource.key, "Adopted release key mismatch")
		assertNull(release.issuerTransitionId)
	}

	@Test
	fun neutralBaselineBootstrapUsesFoliateBindingAndExistingOperation() {
		val binding = transitionTestBinding(commitSequence = 2L)
		val intent = ReaderBootstrapNativePageIntent(transitionTestSemanticHandle())
		val admitted = neutralInitialJournal().reduce(
			ReaderTransitionFact.Intent(null, intent)
		)
		val active = assertNotNull(admitted.state.active)
		assertEquals(ReaderTransitionOperation.BootstrapNativePage, active.id.operation)
		assertEquals(1L, active.id.sequence)
		assertNull(active.id.parent)
		assertIs<ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial>(active.id.expectedBinding)
		val semanticCommand = assertIs<ReaderTransitionCommand.RequestSemanticSynchronization>(
			admitted.commands.single()
		)
		assertTrue(semanticCommand.transitionId == active.id, "Bootstrap transition identity mismatch")
		assertEquals(intent.requestHandle.value, semanticCommand.requestHandle.value)

		val resolved = admitted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(active.id, binding)
		)
		assertTrue(resolved.state.committed === admitted.state.committed)
		assertTrue(
			assertNotNull(resolved.state.active).resolvedSuccessorBinding == binding,
			"Resolved bootstrap binding mismatch"
		)
		val allocation = assertIs<ReaderTransitionCommand.AllocateMaterialBinding>(resolved.commands.single())
		assertTrue(allocation.transitionId == active.id, "Allocation transition identity mismatch")
		assertTrue(allocation.binding == binding.withoutTestMaterial(), "Allocation binding mismatch")
	}

	@Test
	fun initialOriginCannotFabricateOperationOrParent() {
		val fixture = legacyAdoptedBaselineFixture()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Toc,
					transitionTestSemanticHandle()
				)
			)
		)

		val first = assertNotNull(started.state.active)
		assertEquals(1L, first.id.sequence)
		assertNull(first.id.parent)
		assertEquals(11, ReaderTransitionOperation.entries.size)
		assertEquals("ReaderCommittedPresentation.Initial(<redacted>)", fixture.baseline.toString())
		assertEquals(
			"ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(<redacted>)",
			fixture.baseline.origin.toString()
		)
	}

	@Test
	fun transitionSequenceIsMonotonicAcrossAbortRetryAndRestore() {
		val fixture = legacyAdoptedBaselineFixture()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Search,
					transitionTestSemanticHandle()
				)
			)
		)
		val first = assertNotNull(started.state.active)
		val failed = started.state.reduce(ReaderTransitionFact.DeadlineExpired(first.id))
		val retried = failed.state.reduce(ReaderTransitionFact.Retry(first.id))
		val second = assertNotNull(retried.state.active)
		val aborted = retried.state.reduce(ReaderTransitionFact.Intent(second.id, ReaderCancelIntent))
		val restored = aborted.state.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val third = assertNotNull(restored.state.active)

		assertEquals(listOf(1L, 2L, 3L), listOf(first.id.sequence, second.id.sequence, third.id.sequence))
		assertNull(first.id.parent)
		assertTrue(second.id.parent == first.id.parentIdentity(), "Retry parent mismatch")
		assertTrue(third.id.parent == second.id.parentIdentity(), "Restoration parent mismatch")
	}

	@Test
	fun retryAndRestorationPreserveTruthfulInitialBaseline() {
		val fixture = legacyAdoptedBaselineFixture()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Bookmark,
					transitionTestSemanticHandle()
				)
			)
		)
		val first = assertNotNull(started.state.active)
		val failed = started.state.reduce(ReaderTransitionFact.DeadlineExpired(first.id))
		val retried = failed.state.reduce(ReaderTransitionFact.Retry(first.id))

		assertTrue(failed.state.committed === fixture.baseline)
		assertTrue(retried.state.committed === fixture.baseline)
		assertTrue((retried.state.active?.ownedResourceKeys).orEmpty().isEmpty())
		val restored = retried.state.reduce(ReaderTransitionFact.VisibilityChanged(null, visible = true))
		assertTrue(restored.state.committed === fixture.baseline)
		assertTrue(restored.commands.isEmpty())

		val neutral = neutralInitialJournal()
		val neutralIntent = ReaderBootstrapNativePageIntent(transitionTestSemanticHandle(433L))
		val neutralStarted = neutral.reduce(ReaderTransitionFact.Intent(null, neutralIntent))
		val neutralFirst = assertNotNull(neutralStarted.state.active)
		val neutralFailed = neutralStarted.state.reduce(
			ReaderTransitionFact.DeadlineExpired(neutralFirst.id)
		)
		val neutralRetried = neutralFailed.state.reduce(ReaderTransitionFact.Retry(neutralFirst.id))
		val neutralSecond = assertNotNull(neutralRetried.state.active)
		assertTrue(neutralFailed.state.committed === neutral.committed)
		assertTrue(neutralRetried.state.committed === neutral.committed)
		assertEquals(2L, neutralSecond.id.sequence)
		assertEquals(
			2L,
			assertIs<ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial>(
				neutralSecond.id.expectedBinding
			).requestSequence
		)
		assertEquals(neutralFirst.id.parentIdentity(), neutralSecond.id.parent)
	}

	@Test
	fun initialLeaseCannotCarryClaimedGestureOrTransitionId() {
		val fixture = legacyAdoptedBaselineFixture()
		val origin = assertIs<ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor>(
			fixture.baseline.origin
		)
		val requestedLease: Any = origin.requestedLease
		val physicalLease: Any = origin.physicalLease
		assertFalse(requestedLease is ReaderTransitionInputLease.ClaimedGesture)
		assertFalse(physicalLease is ReaderTransitionInputLease.ClaimedGesture)

		val started = fixture.journal.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderPageTurnIntent(
					ReaderPageTurnDirection.Next,
					ReaderTransitionGestureId(401L),
					transitionTestSemanticHandle()
				)
			)
		)
		assertEquals(1L, assertNotNull(started.state.active).id.sequence)
	}

	@Test
	fun initialNativePageLeaseAllowsTextureGenerationZero() {
		val binding = transitionTestBinding(textureGeneration = 0L)
		val fixtureOnlyOwnerId = transitionTestId(
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(binding)
		)
		val owner = transitionTestNativeOwner(fixtureOnlyOwnerId, binding)
		val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(409L)
		val resource = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
				ReaderTransitionResourceKind.Deck,
				419L
			),
			ReaderResourceRetirementOrder(7L, 11L, 421L)
		)
		val lease = ReaderInitialPresentationInputLease.NativePage(binding, textureGeneration = 0L)
		val origin = ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
			seedId = seedId,
			readerSessionGeneration = 7L,
			coordinatorEpoch = 11L,
			owner = owner,
			binding = binding,
			resource = resource,
			requestedLease = lease,
			physicalLease = lease
		)
		assertEquals(0L, assertIs<ReaderInitialPresentationInputLease.NativePage>(origin.physicalLease).textureGeneration)
	}

	@Test
	fun publicationReplacementFromAdoptedBaselineClosesAndReleasesOnce() {
		val fixture = legacyAdoptedBaselineFixture()
		val replaced = fixture.journal.reduce(ReaderTransitionFact.PublicationReplaced(null))

		assertTrue(replaced.state.committed.retainedResourceKeyForTest() == fixture.adoptedResource.key, "Adopted resource mismatch")
		val release = replaced.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().single()
		assertTrue(release.key == fixture.adoptedResource.key, "Adopted release key mismatch")
		assertNull(release.issuerTransitionId)
		val duplicate = replaced.state.reduce(ReaderTransitionFact.PublicationReplaced(null))
		assertTrue(duplicate.commands.isEmpty())
	}

	@Test
	fun publicationReplacementFromNeutralBaselineClosesWithoutBaselineRelease() {
		val baseline = neutralInitialJournal()
		val replaced = baseline.reduce(ReaderTransitionFact.PublicationReplaced(null))

		assertTrue(replaced.state.committed === baseline.committed)
		assertIs<ReaderTransitionOutcome.Cancelled>(replaced.state.lastOutcome)
		assertEquals(0L, replaced.state.lastTransitionSequence)
		assertNull(replaced.state.lastIssuedTransitionIdentity)
		assertTrue(replaced.commands.none { it is ReaderTransitionCommand.ReleaseResource })
		val rejectedWork = replaced.state.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderBootstrapNativePageIntent(transitionTestSemanticHandle(443L))
			)
		)
		assertTrue(rejectedWork.state === replaced.state)
		assertTrue(rejectedWork.commands.isEmpty())
	}

	@Test
	fun closeFromAdoptedBaselineReleasesOnceWithoutTransitionBorrowing() {
		val fixture = legacyAdoptedBaselineFixture()
		val closed = fixture.journal.reduce(ReaderTransitionFact.PublicationClosed(null))

		assertTrue(closed.state.committed === fixture.baseline)
		assertNull(fixture.adoptedResource.key.owningTransitionIdOrNull)
		assertEquals(1L, closed.state.lastTransitionSequence)
		assertEquals(1L, closed.state.lastIssuedTransitionIdentity?.sequence)
		val release = closed.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().single()
		assertTrue(release.key === fixture.adoptedResource.key)
		assertNull(release.issuerTransitionId)
		assertTrue(closed.state.reduce(ReaderTransitionFact.PublicationClosed(null)).commands.isEmpty())
	}

	@Test
	fun closeFromNeutralBaselineEntersReleaseOnlyWithoutResourceCommand() {
		val baseline = neutralInitialJournal()
		val closed = baseline.reduce(ReaderTransitionFact.PublicationClosed(null))

		assertIs<ReaderTransitionOutcome.Cancelled>(closed.state.lastOutcome)
		assertTrue(closed.state.committed === baseline.committed)
		assertEquals(1L, closed.state.lastTransitionSequence)
		assertEquals(1L, closed.state.lastIssuedTransitionIdentity?.sequence)
		assertTrue(closed.commands.none { it is ReaderTransitionCommand.ReleaseResource })
		val duplicate = closed.state.reduce(ReaderTransitionFact.PublicationClosed(null))
		assertTrue(duplicate.commands.isEmpty())
		val rejectedWork = closed.state.reduce(
			ReaderTransitionFact.Intent(
				null,
				ReaderBootstrapNativePageIntent(transitionTestSemanticHandle(449L))
			)
		)
		assertTrue(rejectedWork.state === closed.state)
		assertTrue(rejectedWork.commands.isEmpty())
	}

	@Test
	fun unsolicitedRelocationFromAdoptedBaselineRetainsThenReleasesExactlyOnce() {
		val fixture = legacyAdoptedBaselineFixture()
		val started = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val first = assertNotNull(started.state.active)
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, first.id.operation)
		assertEquals(1L, first.id.sequence)
		assertNull(first.id.parent)
		assertIs<ReaderTransitionCommand.PublishRetainedOwnerAndInputLease>(started.commands.single())
		assertTrue(started.commands.none { it is ReaderTransitionCommand.ReleaseResource })

		val flow = completeInitialRelocationFlow(started, fixture.successor)
		assertTrue(flow.committing.commands.none { it is ReaderTransitionCommand.ReleaseResource })
		val releases = flow.applied.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
		assertEquals(1, releases.size)
		assertTrue(releases.single().key == fixture.adoptedResource.key, "Adopted resource release mismatch")
		assertNull(releases.single().issuerTransitionId)
		val duplicate = flow.applied.state.reduce(flow.appliedFact)
		assertTrue(duplicate.commands.isEmpty())
	}

	@Test
	fun unsolicitedRelocationFromNeutralBaselineNeedsNoRetainedPublication() {
		val baseline = neutralInitialJournal()
		val binding = transitionTestBinding(commitSequence = 2L, rasterGeneration = 23L, textureGeneration = 29L)
		val started = baseline.reduce(ReaderTransitionFact.FoliateDestinationCommitted(null, binding))
		val first = assertNotNull(started.state.active)

		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, first.id.operation)
		assertEquals(1L, first.id.sequence)
		assertNull(first.id.parent)
		assertTrue(started.commands.none {
			it is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease ||
				it is ReaderTransitionCommand.ReleaseResource
		})
		assertIs<ReaderTransitionCommand.AllocateMaterialBinding>(started.commands.single())
		val flow = completeInitialRelocationFlow(started, binding)
		assertTrue(flow.applied.commands.none { it is ReaderTransitionCommand.ReleaseResource })
		assertIs<ReaderCommittedPresentation.Transition>(flow.applied.state.committed)
	}

	@Test
	fun adoptedBaselineReleaseWaitsForSuccessorPublicationAcknowledgement() {
		val fixture = legacyAdoptedBaselineFixture()
		fun started() = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		fun assertBaselineNotReleased(reduction: ReaderTransitionReduction) {
			assertTrue(
				reduction.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
					.none { it.key == fixture.adoptedResource.key },
				"Adopted baseline released before exact successor acknowledgement"
			)
		}

		val timedOutStart = started()
		val timedOutId = assertNotNull(timedOutStart.state.active).id
		val timedOut = timedOutStart.state.reduce(ReaderTransitionFact.DeadlineExpired(timedOutId))
		assertBaselineNotReleased(timedOut)
		val retried = timedOut.state.reduce(ReaderTransitionFact.Retry(timedOutId))
		assertBaselineNotReleased(retried)
		val abortedStart = started()
		val abortedId = assertNotNull(abortedStart.state.active).id
		assertBaselineNotReleased(
			abortedStart.state.reduce(ReaderTransitionFact.Intent(abortedId, ReaderCancelIntent))
		)

		val flow = completeInitialRelocationFlow(started(), fixture.successor, applySuccessor = false)
		assertBaselineNotReleased(flow.committing)
		val command = assertIs<ReaderTransitionCommand.CommitOwnerAndInputLease>(flow.committing.commands.single())
		val stale = flow.committing.state.reduce(
			ReaderTransitionFact.OwnerAndInputPublicationApplied(
				command.transitionId,
				ReaderOwnerAndInputPublicationSubject.Successor(
					command.targetHandle,
					command.preparedFrameResource
				),
				command.owner,
				command.binding,
				command.requestedLease,
				ReaderOwnerAndInputPublicationIdentity(command.publicationIdentity.value + 1L)
			)
		)
		assertBaselineNotReleased(stale)
		val raster = ReaderTransitionResourceKey(
			command.transitionId,
			ReaderTransitionResourceKind.Raster,
			701L
		)
		val deck = ReaderTransitionResourceKey(
			command.transitionId,
			ReaderTransitionResourceKind.Deck,
			709L
		)
		val callback = ReaderTransitionResourceKey(
			command.transitionId,
			ReaderTransitionResourceKind.CallbackRegistration,
			719L
		)
		val registered = listOf(raster, deck, callback).fold(flow.committing.state) { state, key ->
			state.reduce(ReaderTransitionFact.ResourceObserved(command.transitionId, key)).state
		}
		val rejected = registered.reduce(
			ReaderTransitionFact.OwnerAndInputPublicationRejected(
				command.transitionId,
				ReaderOwnerAndInputPublicationSubject.Successor(
					command.targetHandle,
					command.preparedFrameResource
				),
				command.publicationIdentity,
				ReaderTransitionFailureReason.AtomicPublicationRejected
			)
		)
		assertBaselineNotReleased(rejected)
		assertEquals(1, rejected.commands.filterIsInstance<ReaderTransitionCommand.CancelOwnedWork>().size)
		val released = rejected.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			.map { it.key }
		assertEquals(setOf(raster, deck, callback, flow.successorKey), released.toSet())
		assertEquals(released.size, released.distinct().size, "Rejected resources released more than once")
	}

	@Test
	fun initialOriginEqualityDiagnosticsExposeOnlyBoundedCategories() {
		val adopted = legacyAdoptedBaselineFixture().baseline.origin
		val neutral = assertIs<ReaderCommittedPresentation.Initial>(neutralInitialJournal().committed).origin
		val variant = assertNotNull(adopted.equalityDiagnostic(neutral))
		assertEquals(ReaderInitialOriginKind.AdoptedPredecessor, variant.originKind)
		assertEquals(ReaderInitialOriginOwnerKind.NativePage, variant.ownerKind)
		assertEquals(ReaderTransitionResourceKind.Deck, variant.resourceKind)
		assertEquals(ReaderInitialOriginMismatchKind.Variant, variant.mismatch)

		val changedSession = ReaderInitialCommittedPresentationOrigin.Neutral(
			readerSessionGeneration = neutral.readerSessionGeneration + 1L,
			coordinatorEpoch = neutral.coordinatorEpoch,
			requestedLease = neutral.requestedLease,
			physicalLease = neutral.physicalLease
		)
		val session = assertNotNull(neutral.equalityDiagnostic(changedSession))
		assertEquals(ReaderInitialOriginKind.Neutral, session.originKind)
		assertNull(session.ownerKind)
		assertNull(session.resourceKind)
		assertEquals(ReaderInitialOriginMismatchKind.Session, session.mismatch)
		assertNull(neutral.equalityDiagnostic(neutral))
	}

	@Test
	fun adoptedInitialOriginDiagnosticsCoverEveryRegistrationField() {
		val origin = assertIs<ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor>(
			legacyAdoptedBaselineFixture().baseline.origin
		)
		val changedOpaqueIdentity = origin.copy(
			resource = origin.resource.copy(
				key = origin.resource.key.copy(opaqueId = origin.resource.key.opaqueId + 1L)
			)
		)
		val changedRetirementSequence = origin.copy(
			resource = origin.resource.copy(
				retirementOrder = origin.resource.retirementOrder.copy(
					sequence = origin.resource.retirementOrder.sequence + 1L
				)
			)
		)

		val opaqueDiagnostic = assertNotNull(origin.equalityDiagnostic(changedOpaqueIdentity))
		assertTrue(
			opaqueDiagnostic.mismatch in ReaderInitialOriginMismatchKind.entries,
			"Opaque resource identity mismatch must produce a bounded diagnostic"
		)
		val retirementDiagnostic = assertNotNull(origin.equalityDiagnostic(changedRetirementSequence))
		assertTrue(
			retirementDiagnostic.mismatch in ReaderInitialOriginMismatchKind.entries,
			"Retirement sequence mismatch must produce a bounded diagnostic"
		)
	}

	@Test
	fun adoptedReleaseCommandsExposeSessionAuthorityWithoutTransitionCast() {
		val fixture = legacyAdoptedBaselineFixture()
		val directRelease = ReaderTransitionCommand.ReleaseResource(fixture.adoptedResource)
		assertNull(directRelease.transitionId, "Adopted release must not fabricate transition authority")

		val started = fixture.journal.reduce(
			ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
		)
		val successorRelease = completeInitialRelocationFlow(started, fixture.successor).applied.commands
			.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			.single { it.key == fixture.adoptedResource.key }
		assertNull(successorRelease.transitionId, "Successor release must retain session authority")

		val closeRelease = fixture.journal.reduce(ReaderTransitionFact.PublicationClosed(null)).commands
			.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			.single()
		assertNull(closeRelease.transitionId, "Close release must retain session authority")
	}

	@Test
	fun sensitiveInitialWrappersRenderOnlyRedactedConstants() {
		val first = legacyAdoptedBaselineFixture()
		val second = legacyAdoptedBaselineFixture(seedValue = 509L, opaqueResourceId = 521L)
		val origin = assertIs<ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor>(first.baseline.origin)
		val release = ReaderTransitionCommand.ReleaseResource(first.adoptedResource)
		val exact = ReaderExpectedPresentationBinding.Exact(origin.binding)
		val otherExact = ReaderExpectedPresentationBinding.Exact(
			origin.binding.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					origin.binding.foliateSessionId,
					17L
				)
			)
		)

		assertEquals("ReaderAdoptedPredecessorSeedId(<redacted>)", origin.seedId.toString())
		assertEquals("ReaderTransitionResourceOwnerId.AdoptedPredecessor(<redacted>)", origin.resource.key.ownerId.toString())
		assertEquals("ReaderTransitionResourceKey(<redacted>)", origin.resource.key.toString())
		assertEquals("ReaderTransitionResourceRegistration(<redacted>)", origin.resource.toString())
		assertEquals("ReaderInitialPresentationInputLease.NativePage(<redacted>)", origin.requestedLease.toString())
		assertEquals("ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(<redacted>)", origin.toString())
		assertEquals("ReaderCommittedPresentation.Initial(<redacted>)", first.baseline.toString())
		assertEquals("ReaderTransitionJournal(<redacted>)", first.journal.toString())
		assertEquals("ReaderExpectedPresentationBinding.Exact(<redacted>)", exact.toString())
		assertEquals("ReaderTransitionCommand.ReleaseResource(<redacted>)", release.toString())
		assertEquals(first.baseline.hashCode(), second.baseline.hashCode())
		assertEquals(first.journal.hashCode(), second.journal.hashCode())
		assertEquals(first.adoptedResource.key.hashCode(), second.adoptedResource.key.hashCode())
		assertEquals(origin.requestedLease.hashCode(),
			assertIs<ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor>(second.baseline.origin)
				.requestedLease.hashCode())
		assertEquals(exact.hashCode(), otherExact.hashCode())
		assertEquals(release.hashCode(), ReaderTransitionCommand.ReleaseResource(second.adoptedResource).hashCode())
	}

	@Test
	fun sensitiveInitialEqualityFailureUsesSanitizedProjection() {
		val adopted = legacyAdoptedBaselineFixture().baseline.origin
		val neutral = assertIs<ReaderCommittedPresentation.Initial>(neutralInitialJournal().committed).origin
		val expected = assertNotNull(adopted.equalityDiagnostic(neutral))
		val actual = ReaderInitialOriginEqualityDiagnostic(
			ReaderInitialOriginKind.AdoptedPredecessor,
			ReaderInitialOriginOwnerKind.NativePage,
			ReaderTransitionResourceKind.Deck,
			ReaderInitialOriginMismatchKind.Binding
		)
		val failure = assertFailsWith<AssertionError> {
			assertEquals(expected, actual, "Initial origin category mismatch")
		}
		val message = failure.message.orEmpty()
		assertTrue(message.contains("Initial origin category mismatch"))
		assertFalse(message.contains("synthetic-session"))
		assertFalse(message.contains("307"))
		assertFalse(message.contains("311"))
		assertFalse(message.contains("ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(<redacted>)"))
	}

	@Test
	fun mismatchedRequestedLeaseRejectedForShellAdoption() {
		val binding = transitionTestBinding()
		val id = transitionTestId(ReaderTransitionOperation.ShellCoverCommit,
			ReaderExpectedPresentationBinding.Exact(binding))
		assertMismatchedRequestedLeaseRejected(
			transitionTestCoverOwner(id, binding),
			binding,
			ReaderTransitionResourceKind.FrameHandoff,
			ReaderInitialPresentationInputLease.NativePage(binding, requireNotNull(binding.textureGeneration))
		)
	}

	@Test
	fun mismatchedRequestedLeaseRejectedForNativeAdoption() {
		val binding = transitionTestBinding()
		val id = transitionTestId(ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(binding))
		assertMismatchedRequestedLeaseRejected(
			transitionTestNativeOwner(id, binding),
			binding,
			ReaderTransitionResourceKind.Deck,
			ReaderInitialPresentationInputLease.CoverActions
		)
	}

	@Test
	fun mismatchedRequestedLeaseRejectedForCurlAdoption() {
		val fixture = journalAwaitingSettlement()
		assertMismatchedRequestedLeaseRejected(
			fixture.retainedOwner,
			fixture.predecessor,
			ReaderTransitionResourceKind.Deck,
			ReaderInitialPresentationInputLease.CoverActions
		)
	}

	@Test
	fun mismatchedRequestedLeaseRejectedForLiveAdoption() {
		val binding = transitionTestBinding()
		val id = transitionTestId(ReaderTransitionOperation.NativeToLiveHandoff,
			ReaderExpectedPresentationBinding.Exact(binding))
		assertMismatchedRequestedLeaseRejected(
			transitionTestLiveOwner(id, binding),
			binding,
			ReaderTransitionResourceKind.FrameHandoff,
			ReaderInitialPresentationInputLease.CoverActions
		)
	}
}

private data class InitialRelocationFlow(
	val committing: ReaderTransitionReduction,
	val applied: ReaderTransitionReduction,
	val appliedFact: ReaderTransitionFact.OwnerAndInputPublicationApplied,
	val successorKey: ReaderTransitionResourceKey
)

private fun completeInitialRelocationFlow(
	started: ReaderTransitionReduction,
	successor: ReaderPresentationBinding,
	applySuccessor: Boolean = true
): InitialRelocationFlow {
	val admitted = if (started.commands.any {
		it is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease
	}) {
		transitionTestApplyRetainedAcknowledgement(started)
	} else {
		started
	}
	val id = requireNotNull(admitted.state.active).id
	val allocated = admitted.state.reduce(testAllocationFact(id, successor))
	val successorKey = ReaderTransitionResourceKey(
		id,
		ReaderTransitionResourceKind.Deck,
		requireNotNull(successor.textureGeneration)
	)
	var state = allocated.state.withTransitionTestNativeTarget(id, successor, successorKey)
	state = state.reduce(ReaderTransitionFact.RasterProven(id)).state
	state = state.reduce(ReaderTransitionFact.DeckOwned(id, successorKey)).state
	val preparedDeck = state.reduce(ReaderTransitionFact.DeckPrepared(id, successorKey))
	val (awaitingFrame, target) = transitionTestApplyTargetPreparation(preparedDeck)
	val committing = awaitingFrame.reduce(
		ReaderTransitionFact.PreparedFrame(
			id,
			target,
			transitionTestNativeOwner(id, successor),
			target.resource
		)
	)
	val command = assertIs<ReaderTransitionCommand.CommitOwnerAndInputLease>(committing.commands.single())
	val appliedFact = ReaderTransitionFact.OwnerAndInputPublicationApplied(
		command.transitionId,
		ReaderOwnerAndInputPublicationSubject.Successor(
			command.targetHandle,
			command.preparedFrameResource
		),
		command.owner,
		command.binding,
		command.requestedLease,
		command.publicationIdentity
	)
	return InitialRelocationFlow(
		committing = committing,
		applied = if (applySuccessor) committing.state.reduce(appliedFact) else committing,
		appliedFact = appliedFact,
		successorKey = successorKey
	)
}

private fun assertMismatchedRequestedLeaseRejected(
	owner: ReaderPresentationFrameOwner,
	binding: ReaderPresentationBinding,
	resourceKind: ReaderTransitionResourceKind,
	requestedLease: ReaderInitialPresentationInputLease
) {
	val seed = ReaderAdoptedPredecessorSeedId.fromValidatedImport(601L)
	val resource = ReaderTransitionResourceRegistration(
		ReaderTransitionResourceKey(
			ReaderTransitionResourceOwnerId.AdoptedPredecessor(seed),
			resourceKind,
			607L
		),
		ReaderResourceRetirementOrder(7L, 11L, 613L)
	)
	assertFailsWith<IllegalArgumentException>("Owner-incompatible requested lease must be rejected") {
		ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
			seedId = seed,
			readerSessionGeneration = 7L,
			coordinatorEpoch = 11L,
			owner = owner,
			binding = binding,
			resource = resource,
			requestedLease = requestedLease,
			physicalLease = ReaderInitialPresentationInputLease.None
		)
	}
}

private fun transitionTestNeutralCommitted(id: ReaderTransitionId): ReaderCommittedPresentation.Initial =
	ReaderCommittedPresentation.Initial(
		ReaderInitialCommittedPresentationOrigin.Neutral(
			readerSessionGeneration = id.readerSessionGeneration,
			coordinatorEpoch = id.coordinatorEpoch,
			requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
			physicalLease = ReaderInitialPresentationInputLease.None
		)
	)

private fun ReaderCommittedPresentation.retainedOwnerForTest(): ReaderPresentationFrameOwner? = when (this) {
	is ReaderCommittedPresentation.Initial -> when (val initial = origin) {
		is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor -> initial.owner
		is ReaderInitialCommittedPresentationOrigin.Neutral -> null
	}
	is ReaderCommittedPresentation.Transition -> committed.owner
}

private fun ReaderCommittedPresentation.retainedResourceKeyForTest(): ReaderTransitionResourceKey? = when (this) {
	is ReaderCommittedPresentation.Initial -> when (val initial = origin) {
		is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor -> initial.resource.key
		is ReaderInitialCommittedPresentationOrigin.Neutral -> null
	}
	is ReaderCommittedPresentation.Transition -> committed.resourceKey
}

private data class LegacyAdoptedBaselineFixture(
	val journal: ReaderTransitionJournal,
	val baseline: ReaderCommittedPresentation.Initial,
	val successor: ReaderPresentationBinding,
	val adoptedResource: ReaderTransitionResourceRegistration
)

private fun legacyAdoptedBaselineFixture(
	seedValue: Long = 307L,
	opaqueResourceId: Long = 311L
): LegacyAdoptedBaselineFixture {
	val binding = transitionTestBinding(commitSequence = 1L)
	val owner = ReaderPresentationFrameOwner.NativePage(
		ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 47L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = requireNotNull(binding.rasterGeneration),
			textureGeneration = requireNotNull(binding.textureGeneration)
		)
	)
	val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(seedValue)
	val key = ReaderTransitionResourceKey(
		ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
		ReaderTransitionResourceKind.Deck,
		opaqueResourceId
	)
	val resource = ReaderTransitionResourceRegistration(
		key,
		ReaderResourceRetirementOrder(7L, 11L, 313L)
	)
	val lease = ReaderInitialPresentationInputLease.NativePage(
		binding,
		owner.proof.textureGeneration
	)
	val baseline = ReaderCommittedPresentation.Initial(
		ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
			seedId = seedId,
			readerSessionGeneration = 7L,
			coordinatorEpoch = 11L,
			owner = owner,
			binding = binding,
			resource = resource,
			requestedLease = lease,
			physicalLease = lease
		)
	)
	return LegacyAdoptedBaselineFixture(
		journal = ReaderTransitionJournal(committed = baseline),
		baseline = baseline,
		successor = transitionTestBinding(
			commitSequence = 2L,
			rasterGeneration = 23L,
			textureGeneration = 29L
		),
		adoptedResource = resource
	)
}

private fun neutralInitialJournal(): ReaderTransitionJournal = ReaderTransitionJournal(
	committed = ReaderCommittedPresentation.Initial(
		ReaderInitialCommittedPresentationOrigin.Neutral(
			readerSessionGeneration = 7L,
			coordinatorEpoch = 11L,
			requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
			physicalLease = ReaderInitialPresentationInputLease.None
		)
	)
)

private enum class RelocationProof {
	Raster,
	DeckOwned,
	DeckPrepared,
	Frame
}

private fun transitionTestSemanticHandle(value: Long = 1L) = ReaderSemanticRequestHandle(value)

private fun transitionTestRegistration(
	key: ReaderTransitionResourceKey,
	sequence: Long = key.opaqueId
): ReaderTransitionResourceRegistration {
	val id = requireNotNull(key.owningTransitionIdOrNull)
	return ReaderTransitionResourceRegistration(
		key,
		ReaderResourceRetirementOrder(id.readerSessionGeneration, id.coordinatorEpoch, sequence)
	)
}

private fun ReaderTransitionJournal.withTransitionTestNativeTarget(
	id: ReaderTransitionId,
	binding: ReaderPresentationBinding,
	key: ReaderTransitionResourceKey
): ReaderTransitionJournal {
	val current = requireNotNull(active)
	val allocation = requireNotNull(current.materialAllocation)
	val registration = ReaderTransitionResourceRegistration(
		key,
		ReaderResourceRetirementOrder(id.readerSessionGeneration, id.coordinatorEpoch, key.opaqueId)
	)
	val specification = ReaderTransitionFrameTargetSpecification.NativePage(
		id,
		id.readerSessionGeneration,
		binding.publicationGeneration,
		binding,
		allocation,
		ReaderNativePageHostTokenState.Present(ReaderNativePageHostToken(id.sequence)),
		ReaderPlayLikeCurlDeckTargetIdentity(
			1L,
			requireNotNull(binding.textureGeneration),
			when (id.operation) {
				ReaderTransitionOperation.CoverToPageEntry -> ReaderTransitionDeckRole.PageEntry
				ReaderTransitionOperation.ReflowProfileReplacement -> ReaderTransitionDeckRole.Reflow
				ReaderTransitionOperation.RendererRecovery -> ReaderTransitionDeckRole.Recovery
				else -> ReaderTransitionDeckRole.Initial
			}
		),
		ReaderTransitionFrameGeometry(
			binding.viewportGeneration,
			binding.profileGeneration,
			0,
			0,
			1200,
			800
		),
		1L
	)
	return copy(
		active = current.copy(
			pendingFrameTargetSpecification = specification,
			pendingFrameTargetRegistration = registration
		)
	)
}

private fun transitionTestPreparedTarget(
	command: ReaderTransitionCommand.PrepareFrameTarget
): ReaderTransitionFrameTarget {
	val handle = ReaderTransitionFrameTargetHandle(
		command.transitionId.readerSessionGeneration,
		command.specification.publicationGeneration,
		command.registration.key.opaqueId
	)
	return when (val specification = command.specification) {
		is ReaderTransitionFrameTargetSpecification.ShellCover ->
			ReaderTransitionFrameTarget.ShellCover(handle, specification, command.registration)
		is ReaderTransitionFrameTargetSpecification.NativePage ->
			ReaderTransitionFrameTarget.NativePage(handle, specification, command.registration)
		is ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame ->
			ReaderTransitionFrameTarget.CurlSettlementTerminalFrame(
				handle,
				specification,
				command.registration
			)
		is ReaderTransitionFrameTargetSpecification.LiveWebView ->
			ReaderTransitionFrameTarget.LiveWebView(handle, specification, command.registration)
	}
}

private fun transitionTestApplyTargetPreparation(
	reduction: ReaderTransitionReduction
): Pair<ReaderTransitionJournal, ReaderTransitionFrameTarget> {
	val command = reduction.commands.filterIsInstance<ReaderTransitionCommand.PrepareFrameTarget>().single()
	val target = transitionTestPreparedTarget(command)
	val prepared = reduction.state.reduce(
		ReaderTransitionFact.FrameTargetPrepared(command.transitionId, target)
	)
	assertEquals(
		listOf(ReaderTransitionCommand.RequestFramePresentation(command.transitionId, target)),
		prepared.commands
	)
	return prepared.state to target
}

private fun transitionTestApplySuccessorAcknowledgement(
	reduction: ReaderTransitionReduction
): ReaderTransitionReduction {
	val command = assertIs<ReaderTransitionCommand.CommitOwnerAndInputLease>(
		reduction.commands.single()
	)
	return reduction.state.reduce(
		ReaderTransitionFact.OwnerAndInputPublicationApplied(
			command.transitionId,
			ReaderOwnerAndInputPublicationSubject.Successor(
				command.targetHandle,
				command.preparedFrameResource
			),
			command.owner,
			command.binding,
			command.requestedLease,
			command.publicationIdentity
		)
	)
}

private fun transitionTestApplyRetainedAcknowledgement(
	reduction: ReaderTransitionReduction
): ReaderTransitionReduction {
	val command = reduction.commands.filterIsInstance<
		ReaderTransitionCommand.PublishRetainedOwnerAndInputLease
	>().single()
	return reduction.state.reduce(
		ReaderTransitionFact.OwnerAndInputPublicationApplied(
			command.transitionId,
			ReaderOwnerAndInputPublicationSubject.Retained(command.retainedResource),
			command.retainedOwner,
			command.retainedBinding,
			command.requestedLease,
			command.publicationIdentity
		)
	)
}

private fun transitionTestPreparedFrame(
	transitionId: ReaderTransitionId,
	binding: ReaderPresentationBinding,
	frameOwner: ReaderPresentationFrameOwner,
	resourceKey: ReaderTransitionResourceKey
): ReaderTransitionFact.PreparedFrame {
	val registration = ReaderTransitionResourceRegistration(
		resourceKey,
		ReaderResourceRetirementOrder(
			transitionId.readerSessionGeneration,
			transitionId.coordinatorEpoch,
			resourceKey.opaqueId
		)
	)
	val handle = ReaderTransitionFrameTargetHandle(
		transitionId.readerSessionGeneration,
		binding.publicationGeneration,
		resourceKey.opaqueId
	)
	val geometry = when (frameOwner) {
		is ReaderPresentationFrameOwner.NativePage -> ReaderTransitionFrameGeometry(
			binding.viewportGeneration,
			binding.profileGeneration,
			0,
			0,
			frameOwner.proof.viewportWidth,
			frameOwner.proof.viewportHeight
		)
		is ReaderPresentationFrameOwner.ShellCover -> ReaderTransitionFrameGeometry(
			binding.viewportGeneration,
			binding.profileGeneration,
			0,
			0,
			frameOwner.proof.viewportWidth,
			frameOwner.proof.viewportHeight
		)
		else -> ReaderTransitionFrameGeometry(
			binding.viewportGeneration,
			binding.profileGeneration,
			0,
			0,
			1200,
			800
		)
	}
	val target: ReaderTransitionFrameTarget = when (frameOwner) {
		is ReaderPresentationFrameOwner.ShellCover -> ReaderTransitionFrameTarget.ShellCover(
			handle,
			ReaderTransitionFrameTargetSpecification.ShellCover(
				transitionId,
				transitionId.readerSessionGeneration,
				binding.publicationGeneration,
				binding,
				ReaderShellCoverHostToken(frameOwner.proof.token.value),
				frameOwner.proof.coverGeneration,
				binding.viewportGeneration,
				geometry,
				resourceKey.opaqueId
			),
			registration
		)
		is ReaderPresentationFrameOwner.LiveEngine -> ReaderTransitionFrameTarget.LiveWebView(
			handle,
			ReaderTransitionFrameTargetSpecification.LiveWebView(
				transitionId,
				transitionId.readerSessionGeneration,
				binding.publicationGeneration,
				binding,
				ReaderLiveHandoffToken(frameOwner.proof.token.value),
				ReaderLiveHandoffDirection.NativeToLive,
				ReaderLiveHandoffClaimIdentity(resourceKey.opaqueId),
				binding.viewportGeneration,
				geometry,
				resourceKey.opaqueId
			),
			registration
		)
		is ReaderPresentationFrameOwner.NativePage -> {
			val allocation = ReaderMaterialGenerationAllocation(
				transitionId,
				binding,
				requireNotNull(binding.preparationGeneration),
				requireNotNull(binding.rasterGeneration),
				requireNotNull(binding.textureGeneration)
			)
			val deckTarget = ReaderPlayLikeCurlDeckTargetIdentity(
				1L,
				requireNotNull(binding.textureGeneration),
				if (transitionId.operation == ReaderTransitionOperation.CurlClaimAndSettlement) {
					ReaderTransitionDeckRole.Settlement
				} else ReaderTransitionDeckRole.Initial
			)
			if (transitionId.operation == ReaderTransitionOperation.CurlClaimAndSettlement) {
				ReaderTransitionFrameTarget.CurlSettlementTerminalFrame(
					handle,
					ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame(
						transitionId,
						transitionId.readerSessionGeneration,
						binding.publicationGeneration,
						binding,
						allocation,
						ReaderTransitionGestureId(101L),
						ReaderPageTurnSettlementAck(
							"synthetic",
							0,
							binding.foliateSessionId,
							requireNotNull(binding.rasterGeneration),
							requireNotNull(binding.textureGeneration)
						),
						deckTarget,
						geometry,
						resourceKey.opaqueId
					),
					registration
				)
			} else ReaderTransitionFrameTarget.NativePage(
				handle,
				ReaderTransitionFrameTargetSpecification.NativePage(
					transitionId,
					transitionId.readerSessionGeneration,
					binding.publicationGeneration,
					binding,
					allocation,
					frameOwner.proof.transitionToken?.let {
						ReaderNativePageHostTokenState.Present(ReaderNativePageHostToken(it.value))
					} ?: ReaderNativePageHostTokenState.AuthoritativeAbsent,
					deckTarget,
					geometry,
					resourceKey.opaqueId
				),
				registration
			)
		}
		is ReaderPresentationFrameOwner.Curl -> {
			val allocation = ReaderMaterialGenerationAllocation(
				transitionId,
				binding,
				requireNotNull(binding.preparationGeneration),
				requireNotNull(binding.rasterGeneration),
				requireNotNull(binding.textureGeneration)
			)
			ReaderTransitionFrameTarget.NativePage(
				handle,
				ReaderTransitionFrameTargetSpecification.NativePage(
					transitionId,
					transitionId.readerSessionGeneration,
					binding.publicationGeneration,
					binding,
					allocation,
					ReaderNativePageHostTokenState.AuthoritativeAbsent,
					ReaderPlayLikeCurlDeckTargetIdentity(
						1L,
						requireNotNull(binding.textureGeneration),
						ReaderTransitionDeckRole.Initial
					),
					geometry,
					resourceKey.opaqueId
				),
				registration
			)
		}
		ReaderPresentationFrameOwner.Neutral -> error("Unsupported prepared-frame fixture owner")
	}
	return ReaderTransitionFact.PreparedFrame(transitionId, target, frameOwner, registration)
}

private fun transitionTestCoverOwner(
	transitionId: ReaderTransitionId,
	binding: ReaderPresentationBinding
): ReaderPresentationFrameOwner.ShellCover = ReaderPresentationFrameOwner.ShellCover(
	ReaderShellCoverCommitProof(
		token = ReaderPresentationToken(transitionId.sequence),
		binding = binding,
		coverGeneration = 53L,
		presentedFrame = 59L,
		viewportWidth = 1200,
		viewportHeight = 800
	)
)

private fun transitionTestLiveOwner(
	transitionId: ReaderTransitionId,
	binding: ReaderPresentationBinding
): ReaderPresentationFrameOwner.LiveEngine = ReaderPresentationFrameOwner.LiveEngine(
	ReaderLiveEnginePresentationProof(
		token = ReaderPresentationToken(transitionId.sequence),
		binding = binding,
		presentedFrameSequence = 61L
	)
)

private data class ResourceBearingFactCase(
	val name: String,
	val staleUnprotectedReleases: Boolean = true,
	val create: (
		ReaderTransitionId,
		ReaderPresentationBinding,
		ReaderTransitionResourceKey
	) -> ReaderTransitionFact
)

private fun resourceBearingFactCases() = listOf(
	ResourceBearingFactCase("ResourceObserved") { id, _, key ->
		ReaderTransitionFact.ResourceObserved(id, key)
	},
	ResourceBearingFactCase("DeckReserved") { id, _, key ->
		ReaderTransitionFact.DeckReserved(id, key)
	},
	ResourceBearingFactCase("DeckOwned") { id, _, key ->
		ReaderTransitionFact.DeckOwned(id, key)
	},
	ResourceBearingFactCase("DeckPrepared") { id, _, key ->
		ReaderTransitionFact.DeckPrepared(id, key)
	},
	ResourceBearingFactCase("DeckRejected") { id, _, key ->
		ReaderTransitionFact.DeckRejected(id, key)
	},
	ResourceBearingFactCase("ResourceReleased", staleUnprotectedReleases = false) { id, _, key ->
		ReaderTransitionFact.ResourceReleased(id, key)
	},
	ResourceBearingFactCase("PreparedFrame") { id, binding, key ->
		transitionTestPreparedFrame(
			id,
			binding,
			transitionTestLiveOwner(id, binding),
			key
		)
	},
	ResourceBearingFactCase("CoverPostDraw") { id, binding, key ->
		ReaderTransitionFact.CoverPostDraw(
			id,
			binding,
			transitionTestCoverOwner(id, binding),
			key
		)
	},
	ResourceBearingFactCase("WebViewExposure") { id, binding, key ->
		ReaderTransitionFact.WebViewExposure(
			id,
			binding,
			transitionTestLiveOwner(id, binding),
			key
		)
	}
)

private data class LivePredecessorRelocationFixture(
	val journal: ReaderTransitionJournal,
	val predecessor: ReaderPresentationBinding,
	val predecessorKey: ReaderTransitionResourceKey
)

private fun journalRelocatingFromLivePredecessor(): LivePredecessorRelocationFixture {
	val predecessor = transitionTestBinding(commitSequence = 1L)
	val predecessorId = transitionTestId(
		ReaderTransitionOperation.NativeToLiveHandoff,
		ReaderExpectedPresentationBinding.Exact(predecessor),
		sequence = 12L
	)
	val predecessorOwner = transitionTestLiveOwner(predecessorId, predecessor)
	val predecessorKey = ReaderTransitionResourceKey(
		predecessorId,
		ReaderTransitionResourceKind.FrameHandoff,
		predecessorOwner.proof.presentedFrameSequence
	)
	val idle = ReaderTransitionJournal(
		committed = ReaderCommittedTransition(
			predecessorId,
			predecessorOwner,
			predecessor,
			predecessorKey,
			transitionTestRegistration(predecessorKey)
		)
	)
	val successor = transitionTestBinding(
		commitSequence = 2L,
		rasterGeneration = 23L,
		textureGeneration = 29L
	)
	val started = idle.reduce(
		ReaderTransitionFact.FoliateDestinationCommitted(null, successor)
	)
	return LivePredecessorRelocationFixture(
		journal = started.state,
		predecessor = predecessor,
		predecessorKey = predecessorKey
	)
}

private data class PublicationCloseFixture(
	val journal: ReaderTransitionJournal,
	val id: ReaderTransitionId,
	val binding: ReaderPresentationBinding
)

private fun journalAwaitingPublicationClose(): PublicationCloseFixture {
	val binding = transitionTestBinding()
	val predecessorId = transitionTestId(
		ReaderTransitionOperation.BootstrapNativePage,
		ReaderExpectedPresentationBinding.Exact(binding),
		sequence = 12L
	)
	val owner = transitionTestNativeOwner(predecessorId, binding)
	val predecessorKey = ReaderTransitionResourceKey(
		predecessorId,
		ReaderTransitionResourceKind.Deck,
		owner.proof.textureGeneration
	)
	val id = transitionTestId(
		ReaderTransitionOperation.PublicationClose,
		ReaderExpectedPresentationBinding.Exact(binding)
	)
	val phase = ReaderTransitionLivenessTable.phase(
		id,
		ReaderTransitionPhaseKind.AwaitingProof,
		ReaderPresentationFrameOwner.Neutral
	)
	return PublicationCloseFixture(
		journal = ReaderTransitionJournal(
			active = ReaderActiveTransition(
				id,
				phase,
				predecessorResourceKey = predecessorKey
			),
			committed = ReaderCommittedTransition(
				predecessorId,
				owner,
				binding,
				predecessorKey,
				transitionTestRegistration(predecessorKey)
			)
		),
		id = id,
		binding = binding
	)
}

private fun ReaderPresentationBinding.withoutTestMaterial() = copy(
	preparationGeneration = null,
	rasterGeneration = null,
	textureGeneration = null
)

private fun testAllocationFact(
	id: ReaderTransitionId,
	binding: ReaderPresentationBinding
) = ReaderTransitionFact.MaterialBindingAllocated(
	id,
	ReaderMaterialGenerationAllocation(
		id,
		binding,
		requireNotNull(binding.preparationGeneration),
		requireNotNull(binding.rasterGeneration),
		requireNotNull(binding.textureGeneration)
	)
)

private fun ReaderTransitionCommand.startsNewPhysicalAttempt(): Boolean = when (this) {
	is ReaderTransitionCommand.RequestSemanticSynchronization,
	is ReaderTransitionCommand.AllocateMaterialBinding,
	is ReaderTransitionCommand.RequestRasterPreparation,
	is ReaderTransitionCommand.ReserveDeck,
	is ReaderTransitionCommand.RequestFramePresentation -> true
	else -> false
}

internal data class ReaderTransitionModelFixture(
	val journal: ReaderTransitionJournal,
	val id: ReaderTransitionId,
	val predecessor: ReaderPresentationBinding,
	val successor: ReaderPresentationBinding,
	val retainedOwner: ReaderPresentationFrameOwner.Curl,
	val predecessorResourceKey: ReaderTransitionResourceKey,
	val gestureId: ReaderTransitionGestureId
)

private fun ReaderTransitionModelFixture.idleCommittedJournal(): ReaderTransitionJournal {
	val committedId = assertIs<ReaderCommittedPresentation.Transition>(journal.committed).committed.id
	return journal.copy(
		active = null,
		lastOutcome = null,
		retryableTransition = null,
		lastTransitionSequence = committedId.sequence,
		lastIssuedTransitionIdentity = committedId.parentIdentity()
	)
}

internal fun journalAwaitingSettlement(): ReaderTransitionModelFixture {
	val predecessor = transitionTestBinding(commitSequence = 1L, rasterGeneration = 17L, textureGeneration = 19L)
	val successor = transitionTestBinding(commitSequence = 2L, rasterGeneration = 23L, textureGeneration = 29L)
	val predecessorId = transitionTestId(
		operation = ReaderTransitionOperation.BootstrapNativePage,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(predecessor),
		sequence = 12L
	)
	val id = transitionTestId(
		operation = ReaderTransitionOperation.CurlClaimAndSettlement,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(successor)
	)
	val gestureId = ReaderTransitionGestureId(101L)
	val retainedOwner = ReaderPresentationFrameOwner.Curl(
		ReaderCurlPresentationFrame(
			token = ReaderPresentationToken(31L, ReaderPresentationTokenDomain.Gesture),
			binding = predecessor,
			presentedFrame = 37L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = requireNotNull(predecessor.rasterGeneration),
			textureGeneration = requireNotNull(predecessor.textureGeneration)
		)
	)
	val predecessorResourceKey = ReaderTransitionResourceKey(
		predecessorId,
		ReaderTransitionResourceKind.Deck,
		requireNotNull(predecessor.textureGeneration)
	)
	val phase = ReaderTransitionLivenessTable.phase(
		id = id,
		kind = ReaderTransitionPhaseKind.AwaitingProof,
		retainedOwner = retainedOwner,
		gestureId = gestureId
	)
	val successorRegistration = ReaderTransitionResourceRegistration(
		ReaderTransitionResourceKey(
			id,
			ReaderTransitionResourceKind.Deck,
			requireNotNull(successor.textureGeneration)
		),
		ReaderResourceRetirementOrder(
			id.readerSessionGeneration,
			id.coordinatorEpoch,
			2L
		)
	)
	val allocation = ReaderMaterialGenerationAllocation(
		id,
		successor,
		requireNotNull(successor.preparationGeneration),
		requireNotNull(successor.rasterGeneration),
		requireNotNull(successor.textureGeneration)
	)
	val targetSpecification = ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame(
		id,
		id.readerSessionGeneration,
		successor.publicationGeneration,
		successor,
		allocation,
		gestureId,
		ReaderPageTurnSettlementAck(
			"synthetic",
			0,
			successor.foliateSessionId,
			requireNotNull(successor.rasterGeneration),
			requireNotNull(successor.textureGeneration)
		),
		ReaderPlayLikeCurlDeckTargetIdentity(
			1L,
			requireNotNull(successor.textureGeneration),
			ReaderTransitionDeckRole.Settlement
		),
		ReaderTransitionFrameGeometry(
			successor.viewportGeneration,
			successor.profileGeneration,
			0,
			0,
			1200,
			800
		),
		1L
	)
	return ReaderTransitionModelFixture(
		journal = ReaderTransitionJournal(
			active = ReaderActiveTransition(
				id,
				phase,
				predecessorResourceKey = predecessorResourceKey,
				pendingFrameTargetSpecification = targetSpecification,
				pendingFrameTargetRegistration = successorRegistration,
				materialAllocation = allocation
			),
			committed = ReaderCommittedTransition(
				predecessorId,
				retainedOwner,
				predecessor,
				predecessorResourceKey,
				transitionTestRegistration(predecessorResourceKey)
			)
		),
		id = id,
		predecessor = predecessor,
		successor = successor,
		retainedOwner = retainedOwner,
		predecessorResourceKey = predecessorResourceKey,
		gestureId = gestureId
	)
}

internal fun journalAwaitingSemanticSuccessor(): ReaderTransitionModelFixture {
	val base = journalAwaitingSettlement()
	val id = base.id.copy(
		expectedBinding = ReaderExpectedPresentationBinding.SemanticSuccessor(
			predecessor = base.predecessor,
			requestSequence = 17L
		)
	)
	val phase = ReaderTransitionLivenessTable.phase(
		id = id,
		kind = ReaderTransitionPhaseKind.AwaitingProof,
		retainedOwner = base.retainedOwner,
		gestureId = base.gestureId
	)
	val baseActive = requireNotNull(base.journal.active)
	val baseSpecification = baseActive.pendingFrameTargetSpecification as
		ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame
	val allocation = requireNotNull(baseActive.materialAllocation).copy(transitionId = id)
	val specification = baseSpecification.copy(
		transitionId = id,
		allocation = allocation
	)
	val registration = requireNotNull(baseActive.pendingFrameTargetRegistration).let {
		it.copy(key = it.key.copy(transitionId = id))
	}
	return base.copy(
		journal = base.journal.copy(
			active = ReaderActiveTransition(
				id,
				phase,
				predecessorResourceKey = base.predecessorResourceKey,
				pendingFrameTargetSpecification = specification,
				pendingFrameTargetRegistration = registration,
				materialAllocation = allocation
			)
		),
		id = id
	)
}

internal fun matchingSettlementFact(fixture: ReaderTransitionModelFixture) =
	ReaderTransitionFact.SettlementAcknowledged(
		transitionId = fixture.id,
		binding = fixture.successor,
		acknowledgement = ReaderPageTurnSettlementAck(
			token = "synthetic",
			pageIndex = 0,
			foliateSessionId = fixture.successor.foliateSessionId,
			rasterGeneration = requireNotNull(fixture.successor.rasterGeneration),
			textureGeneration = requireNotNull(fixture.successor.textureGeneration)
		)
	)

internal fun transitionTestNativeOwner(
	transitionId: ReaderTransitionId,
	binding: ReaderPresentationBinding
) = ReaderPresentationFrameOwner.NativePage(
	ReaderNativePagePresentationProof(
		binding = binding,
		transitionToken = ReaderPresentationToken(transitionId.sequence),
		presentedFrame = 47L,
		viewportWidth = 1200,
		viewportHeight = 800,
		rasterGeneration = requireNotNull(binding.rasterGeneration),
		textureGeneration = requireNotNull(binding.textureGeneration)
	)
)

internal fun transitionTestId(
	operation: ReaderTransitionOperation,
	expectedBinding: ReaderExpectedPresentationBinding,
	sequence: Long = 13L,
	parent: ReaderTransitionParentIdentity? = if (sequence == 1L) {
		null
	} else {
		ReaderTransitionParentIdentity(7L, 11L, sequence - 1L)
	}
) = ReaderTransitionId(
	readerSessionGeneration = 7L,
	coordinatorEpoch = 11L,
	sequence = sequence,
	operation = operation,
	expectedBinding = expectedBinding,
	parent = parent
)

internal fun transitionTestBinding(
	commitSequence: Long = 1L,
	rasterGeneration: Long? = 17L,
	textureGeneration: Long? = 19L
) = ReaderPresentationBinding(
	foliateSessionId = "synthetic-session",
	publicationGeneration = 2L,
	viewportGeneration = 3L,
	profileGeneration = 5L,
	destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic-session", commitSequence),
	rasterGeneration = rasterGeneration,
	textureGeneration = textureGeneration,
	preparationGeneration = 7L
)
