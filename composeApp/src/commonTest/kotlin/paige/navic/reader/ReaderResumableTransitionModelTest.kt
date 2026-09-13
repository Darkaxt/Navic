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
	fun transitionIdentityCarriesExactAndSemanticBindingsAndOptionalParent() {
		val predecessor = transitionTestBinding(commitSequence = 1L)
		val parent = ReaderTransitionParentIdentity(7L, 11L, 13L)
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
	fun curlLeaseRetainsSuppliedGestureIdentityIndependentOfTransitionSequence() {
		val fixture = journalAwaitingSettlement()
		val lease = assertIs<ReaderTransitionInputLease.ClaimedGesture>(
			requireNotNull(fixture.journal.active).phase.contract.inputLease
		)

		assertEquals(fixture.gestureId, lease.gestureId)
		assertTrue(fixture.id.sequence != lease.gestureId.value)
		assertEquals(
			fixture.gestureId,
			ReaderPageTurnIntent(ReaderPageTurnDirection.Next, fixture.gestureId).gestureId
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
			setOf(ReaderTransitionProofKind.PreparedFrame),
			active.phase.contract.awaitedProofs
		)
		assertTrue(first.commands.isEmpty())
		assertTrue(first.commands.none { it is ReaderTransitionCommand.ApplyInputLease })
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

		val nextId = fixture.id.copy(sequence = fixture.id.sequence + 2L)
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
			)
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
	fun semanticSuccessorCommitsInEitherFactOrderAndConsumesSettlementOnce() {
		listOf(false, true).forEach { frameFirst ->
			val fixture = journalAwaitingSemanticSuccessor()
			val successorOwner = transitionTestNativeOwner(fixture.id, fixture.successor)
			val successorKey = ReaderTransitionResourceKey(
				fixture.id,
				ReaderTransitionResourceKind.Deck,
				requireNotNull(fixture.successor.textureGeneration)
			)
			val frame = ReaderTransitionFact.PreparedFrame(
				fixture.id,
				fixture.successor,
				successorOwner,
				successorKey
			)
			val settlement = matchingSettlementFact(fixture)
			val first = fixture.journal.reduce(if (frameFirst) frame else settlement)
			if (!frameFirst) {
				assertTrue(first.commands.none { it is ReaderTransitionCommand.ApplyInputLease })
			}
			val second = first.state.reduce(if (frameFirst) settlement else frame)

			assertNull(second.state.active)
			val succeeded = assertIs<ReaderTransitionOutcome.Succeeded>(second.state.lastOutcome)
			assertEquals(fixture.successor, succeeded.binding)
			assertEquals(successorOwner, succeeded.committedOwner)
			assertEquals(successorKey, second.state.committed?.resourceKey)
			if (!frameFirst) {
				assertEquals(
					fixture.id,
					first.state.active?.consumedSettlement?.transitionId
				)
			}
			val duplicate = second.state.reduce(settlement)
			assertEquals(second.state, duplicate.state)
			assertTrue(duplicate.commands.isEmpty())
		}
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
		assertFalse(ReaderTransitionProofKind.SemanticDestination in active.phase.contract.awaitedProofs)
		assertTrue(ReaderTransitionProofKind.Raster in active.phase.contract.awaitedProofs)
		assertTrue(ReaderTransitionProofKind.DeckOwnership in active.phase.contract.awaitedProofs)
		assertTrue(ReaderTransitionProofKind.DeckPrepared in active.phase.contract.awaitedProofs)
		assertTrue(ReaderTransitionProofKind.PreparedFrame in active.phase.contract.awaitedProofs)
		assertEquals(
			ReaderTransitionCommand.CancelOwnedWork(fixture.id),
			result.commands.first()
		)
		assertTrue(result.commands.any {
			it is ReaderTransitionCommand.ApplyInputLease &&
				it.lease == ReaderTransitionInputLease.ChromeOnly
		})
		assertTrue(result.commands.any { it is ReaderTransitionCommand.RequestRasterPreparation })
		assertTrue(result.commands.none { it is ReaderTransitionCommand.ReleaseResource })
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
	fun externalRelocationCommitsAfterEachFreshProofArrivesOnceInAnyOrder() {
		val permutations = listOf(
			listOf(RelocationProof.Frame, RelocationProof.Raster, RelocationProof.DeckPrepared, RelocationProof.DeckOwned),
			listOf(RelocationProof.DeckOwned, RelocationProof.Frame, RelocationProof.Raster, RelocationProof.DeckPrepared),
			listOf(RelocationProof.Raster, RelocationProof.DeckPrepared, RelocationProof.DeckOwned, RelocationProof.Frame)
		)

		permutations.forEach { permutation ->
			val fixture = journalAwaitingSettlement()
			val started = fixture.journal.reduce(
				ReaderTransitionFact.FoliateDestinationCommitted(null, fixture.successor)
			)
			val relocationId = requireNotNull(started.state.active).id
			val deckKey = ReaderTransitionResourceKey(relocationId, ReaderTransitionResourceKind.Deck, 19L)
			val successorOwner = transitionTestNativeOwner(relocationId, fixture.successor)
			var state = started.state
			val commands = started.commands.toMutableList()

			permutation.forEachIndexed { index, proof ->
				val reduction = when (proof) {
					RelocationProof.Raster -> state.reduce(ReaderTransitionFact.RasterProven(relocationId))
					RelocationProof.DeckOwned -> state.reduce(ReaderTransitionFact.DeckOwned(relocationId, deckKey))
					RelocationProof.DeckPrepared -> state.reduce(ReaderTransitionFact.DeckPrepared(relocationId, deckKey))
					RelocationProof.Frame -> state.reduce(
						ReaderTransitionFact.PreparedFrame(
							relocationId,
							fixture.successor,
							successorOwner,
							deckKey
						)
					)
				}
				state = reduction.state
				commands += reduction.commands
				if (index < permutation.lastIndex) {
					assertEquals(fixture.retainedOwner, state.active?.phase?.contract?.retainedOwner)
					assertTrue(reduction.commands.none { it is ReaderTransitionCommand.ReleaseResource })
				}
			}

			assertNull(state.active)
			val succeeded = assertIs<ReaderTransitionOutcome.Succeeded>(state.lastOutcome)
			assertEquals(fixture.successor, succeeded.binding)
			assertEquals(successorOwner, succeeded.committedOwner)
			val releases = commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			assertEquals(listOf(fixture.predecessorResourceKey), releases.map { it.key })
			assertTrue(commands.any {
				it is ReaderTransitionCommand.ApplyInputLease &&
					it.lease is ReaderTransitionInputLease.NativePage
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
				predecessorKey
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
		assertTrue(result.commands.any { it is ReaderTransitionCommand.RequestRasterPreparation })
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
				ReaderActiveTransition(id, phase, predecessorResourceKey = predecessorKey)
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
			val reduction = when (operation) {
				ReaderTransitionOperation.ShellCoverCommit -> journal.reduce(
					ReaderTransitionFact.CoverPostDraw(id, binding, successor, successorKey)
				)
				ReaderTransitionOperation.NativeToLiveHandoff -> journal.reduce(
					ReaderTransitionFact.WebViewExposure(id, binding, successor, successorKey)
				)
			}

			assertNull(reduction.state.active)
			assertEquals(successor, assertIs<ReaderTransitionOutcome.Succeeded>(reduction.state.lastOutcome).committedOwner)
			val releases = reduction.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			assertEquals(listOf(predecessorKey), releases.map { it.key })
			val lease = reduction.commands.filterIsInstance<ReaderTransitionCommand.ApplyInputLease>().single().lease
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
			ReaderActiveTransition(id, phase, predecessorResourceKey = predecessorKey)
		)
		val wrongOwner = transitionTestLiveOwner(id, binding)
		val wrongKey = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.FrameHandoff, 71L)

		val rejected = journal.reduce(
			ReaderTransitionFact.PreparedFrame(id, binding, wrongOwner, wrongKey)
		)

		assertEquals(journal, rejected.state)
		assertEquals(
			listOf(ReaderTransitionCommand.ReleaseResource(id, wrongKey)),
			rejected.commands
		)

		val nativeOwner = transitionTestNativeOwner(id, binding)
		val nativeKey = ReaderTransitionResourceKey(
			id,
			ReaderTransitionResourceKind.Deck,
			nativeOwner.proof.textureGeneration
		)
		val committed = rejected.state.reduce(
			ReaderTransitionFact.PreparedFrame(id, binding, nativeOwner, nativeKey)
		)
		assertEquals(nativeOwner, assertIs<ReaderTransitionOutcome.Succeeded>(committed.state.lastOutcome).committedOwner)
		assertEquals(nativeKey, committed.state.committed?.resourceKey)
		assertEquals(
			ReaderTransitionInputLease.NativePage(binding, nativeOwner.proof.textureGeneration),
			committed.commands.filterIsInstance<ReaderTransitionCommand.ApplyInputLease>().single().lease
		)
		assertEquals(
			listOf(predecessorKey),
			committed.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun rejectedPreparedFramesDrainExactStaleAndMismatchedResourcesButNotRetainedShield() {
		val fixture = journalAwaitingSettlement()
		val staleId = fixture.id.copy(sequence = 11L)
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
			ReaderTransitionFact.PreparedFrame(
				staleId,
				fixture.successor,
				staleOwner,
				staleKey
			),
			ReaderTransitionFact.PreparedFrame(
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
				listOf(ReaderTransitionCommand.ReleaseResource(fixture.id, expectedKey)),
				rejected.commands
			)
		}

		val retained = fixture.journal.reduce(
			ReaderTransitionFact.PreparedFrame(
				fixture.predecessorResourceKey.transitionId,
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
		val id = requireNotNull(started.state.active).id
		val admitted = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 73L)
		val wrongTransition = admitted.copy(transitionId = fixture.id)
		val wrongDeck = admitted.copy(opaqueId = 79L)

		val staleOwned = started.state.reduce(ReaderTransitionFact.DeckOwned(id, wrongTransition))
		assertEquals(started.state, staleOwned.state)
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
			assertEquals(fixture.predecessorResourceKey, terminal.state.committed?.resourceKey)
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
			transitionId = fixture.predecessorKey.transitionId,
			opaqueId = 157L
		)
		val stale = duplicate.state.reduce(
			ReaderTransitionFact.ResourceReleased(staleKey.transitionId, staleKey)
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
		val activeId = requireNotNull(started.state.active).id
		val owner = transitionTestNativeOwner(activeId, fixture.successor)
		val keyA = ReaderTransitionResourceKey(
			activeId,
			ReaderTransitionResourceKind.Deck,
			163L
		)
		val keyB = keyA.copy(opaqueId = 167L)
		val frameA = ReaderTransitionFact.PreparedFrame(
			activeId,
			fixture.successor,
			owner,
			keyA
		)

		val retained = started.state.reduce(frameA)
		assertEquals(keyA, retained.state.active?.successorResourceKey)
		val duplicate = retained.state.reduce(frameA)
		assertEquals(retained.state, duplicate.state)
		assertTrue(duplicate.commands.isEmpty())

		val conflicting = retained.state.reduce(
			ReaderTransitionFact.PreparedFrame(
				activeId,
				fixture.successor,
				owner,
				keyB
			)
		)
		assertEquals(retained.state, conflicting.state)
		assertEquals(keyA, conflicting.state.active?.successorResourceKey)
		assertEquals(
			listOf(ReaderTransitionCommand.ReleaseResource(activeId, keyB)),
			conflicting.commands
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
					fixture.predecessorKey.transitionId,
					fixture.predecessor,
					fixture.predecessorKey
				)
			)
			assertEquals(fixture.journal, protected.state, case.name)
			assertTrue(protected.commands.isEmpty(), case.name)

			val rejected = fixture.journal.reduce(
				case.create(
					unprotected.transitionId,
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
			fixture.id.copy(readerSessionGeneration = fixture.id.readerSessionGeneration + 1L),
			fixture.id.copy(coordinatorEpoch = fixture.id.coordinatorEpoch + 1L),
			fixture.id.copy(sequence = fixture.id.sequence + 1L)
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
		assertNull(exact.state.committed)
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
			committed = ReaderCommittedTransition(id, owner, binding, key)
		)

		val closed = journal.reduce(ReaderTransitionFact.PublicationClosed(null))

		assertNull(closed.state.active)
		assertNull(closed.state.committed)
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
			ReaderActiveTransition(
				id,
				ReaderTransitionLivenessTable.phase(
					id,
					ReaderTransitionPhaseKind.AwaitingPrerequisites,
					retainedOwner
				)
			)
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
}

private enum class RelocationProof {
	Raster,
	DeckOwned,
	DeckPrepared,
	Frame
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
		ReaderTransitionFact.PreparedFrame(
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
			predecessorKey
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
				predecessorKey
			)
		),
		id = id,
		binding = binding
	)
}

private fun ReaderTransitionCommand.startsNewPhysicalAttempt(): Boolean = when (this) {
	is ReaderTransitionCommand.RequestSemanticSynchronization,
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
	return ReaderTransitionModelFixture(
		journal = ReaderTransitionJournal(
			active = ReaderActiveTransition(
				id,
				phase,
				predecessorResourceKey = predecessorResourceKey
			),
			committed = ReaderCommittedTransition(
				predecessorId,
				retainedOwner,
				predecessor,
				predecessorResourceKey
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
	return base.copy(
		journal = base.journal.copy(
			active = ReaderActiveTransition(
				id,
				phase,
				predecessorResourceKey = base.predecessorResourceKey
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
	parent: ReaderTransitionParentIdentity? = null
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
