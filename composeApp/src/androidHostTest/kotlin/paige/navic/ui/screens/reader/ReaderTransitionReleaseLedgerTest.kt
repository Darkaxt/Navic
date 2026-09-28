package paige.navic.ui.screens.reader

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import paige.navic.reader.ReaderActiveTransition
import paige.navic.reader.ReaderAdoptedPredecessorSeedId
import paige.navic.reader.ReaderCommittedPresentation
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderInitialCommittedPresentationOrigin
import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderPhysicalReleaseAttemptId
import paige.navic.reader.ReaderReleaseCommandAmbiguityReason
import paige.navic.reader.ReaderReleaseCommandIdentity
import paige.navic.reader.ReaderReleaseCommandRejectionReason
import paige.navic.reader.ReaderReleaseLedgerCleanupStatus
import paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus
import paige.navic.reader.ReaderReleaseOnlyCleanupGeneration
import paige.navic.reader.ReaderReleaseOnlyCleanupId
import paige.navic.reader.ReaderReleaseOnlyCleanupKey
import paige.navic.reader.ReaderReleasePortContractViolationReason
import paige.navic.reader.ReaderResourceReleaseIssuer
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderSaturatingCallbackCount
import paige.navic.reader.ReaderSemanticPortContractViolationReason
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionCommandRejectionReason
import paige.navic.reader.ReaderTransitionCommandStage
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionLivenessTable
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.parentIdentity

@RunWith(RobolectricTestRunner::class)
class ReaderTransitionReleaseLedgerTest {
	@Test
	fun staleResourceReceivesOneReleaseCommandAndConfirmation() {
		val ledger = ReaderTransitionReleaseLedger()
		val key = deckKey(transitionId(), opaqueId = 41L)

		assertNull(ledger.stateOf(key))
		assertTrue(ledger.register(key))
		val command = assertNotNull(ledger.requestRelease(key))
		assertNull(ledger.requestRelease(key))
		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertFalse(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(key))
	}

	@Test
	fun confirmationWithoutInstalledAttemptIsInert() {
		val ledger = ReaderTransitionReleaseLedger()
		val key = deckKey(transitionId(), opaqueId = 42L)

		assertFalse(ledger.confirmReleased(releaseFact(requireNotNull(key.owningTransitionIdOrNull), key)))
		assertTrue(ledger.register(key))
		val command = assertNotNull(ledger.requestRelease(key))
		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertFalse(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(key))
	}

	@Test
	fun ledgerExposesNoKeyOnlyConfirmationAuthority() {
		val keyOnlyConfirmation = ReaderTransitionReleaseLedger::class.java.declaredMethods.singleOrNull {
			it.name == "confirmReleased" &&
				it.parameterTypes.contentEquals(arrayOf(ReaderTransitionResourceKey::class.java))
		}

		assertNull(
			keyOnlyConfirmation,
			"Physical confirmation authority must include the exact release attempt identity"
		)
	}

	@Test
	fun unmatchedConfirmationsDoNotConsumeBoundedLedgerCapacity() {
		val ledger = ReaderTransitionReleaseLedger()
		repeat(33) { index ->
			val key = deckKey(
				transitionId(sequence = index.toLong() + 30L),
				opaqueId = 2_000L + index
			)
			assertFalse(
				ledger.confirmReleased(
					releaseFact(requireNotNull(key.owningTransitionIdOrNull), key)
				)
			)
		}
		val admitted = deckKey(transitionId(), opaqueId = 1_000L)
		assertTrue(ledger.register(admitted))
		assertNotNull(ledger.requestRelease(admitted))
		assertEquals(0, ledger.snapshot().releasedCount)
	}

	@Test
	fun exactDuplicateResourceAndCallbackFactsReleaseOnce() {
		val ledger = ReaderTransitionReleaseLedger()
		val key = deckKey(transitionId(), opaqueId = 43L)

		assertTrue(ledger.register(key))
		assertFalse(ledger.register(key))
		val command = assertIs<ReaderTransitionCommand.ReleaseResource>(ledger.requestRelease(key))
		assertNull(ledger.requestRelease(key))
		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertFalse(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertFalse(ledger.register(key))
		assertEquals(
			ReaderTransitionReleaseLedgerSnapshot(ownedCount = 0, issuedCount = 0, releasedCount = 1),
			ledger.snapshot()
		)
	}

	@Test
	fun wrongOriginConfirmationIsInertWhileExactIssuedConfirmationReleases() {
		val ledger = ReaderTransitionReleaseLedger()
		val key = deckKey(transitionId(), opaqueId = 53L)
		assertFalse(ledger.confirmReleased(releaseFact(requireNotNull(key.owningTransitionIdOrNull), key)))
		assertTrue(ledger.register(key))
		val command = assertNotNull(ledger.requestRelease(key))
		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertFalse(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(key))
	}

	@Test
	fun wrongAttemptConfirmationCannotReduceOwnedTransitionAuthority() {
		val id = transitionId()
		val key = deckKey(id, opaqueId = 55L)
		val base = journal(id)
		val owned = base.copy(
			active = requireNotNull(base.active).copy(ownedResourceKeys = setOf(key))
		)
		val ledger = ReaderTransitionReleaseLedger()
		val registration = assertNotNull(ledger.register(key, 19L, 23L))
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		val wrong = ReaderTransitionFact.ResourceReleased(
			ReaderReleaseCommandIdentity(
				ReaderPhysicalReleaseAttemptId.fromLedger(command.identity.attemptId.value + 1L),
				registration
			)
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(onIssue = { _, _ -> error("Shadow mode cannot issue") }),
			mode = ReaderTransitionMode.Shadow,
			journal = owned,
			releaseLedger = ledger
		)

		coordinator.enqueue(wrong)
		val journalField = coordinator.javaClass.getDeclaredField("journal").apply {
			isAccessible = true
		}
		val afterWrongAttempt = journalField.get(coordinator) as ReaderTransitionJournal
		assertTrue(
			key in requireNotNull(afterWrongAttempt.active).ownedResourceKeys,
			"Wrong attempt must not remove the exact resource from journal authority"
		)
		assertEquals(ReaderTransitionResourceState.ReleaseCommandIssued, ledger.stateOf(key))
		coordinator.enqueue(ReaderTransitionFact.ResourceReleased(command.identity))
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(key))
	}

	@Test
	fun shadowReleasePredictionDoesNotIssueOrPoisonLaterCutoverAdoption() {
		val ledger = ReaderTransitionReleaseLedger()
		val ports = TestPorts(onIssue = { _, _ ->
			error("Shadow mode cannot issue commands")
		})
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Shadow,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			),
			releaseLedger = ledger
		)
		val key = deckKey(transitionId(), opaqueId = 57L)
		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(key.owningTransitionIdOrNull), key))

		val coordinatorSnapshot = coordinator.snapshot()
		assertEquals(
			setOf(ReaderTransitionCommandKind.ReleaseResource),
			coordinatorSnapshot.shadowPredictions.last().commandKinds
		)
		assertEquals(0, coordinatorSnapshot.releaseCommandIssuedCount)
		assertEquals(ReaderTransitionResourceState.Owned, ledger.stateOf(key))

		val cutover = ReaderDeckAdmissionCutover(
			legacyAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			coordinatorAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			legacyInventory = {
				ReaderLegacyDeckInventory.Complete(
					listOf(
						ReaderLegacyDeckResource(
							key = key,
							origin = ReaderLegacyDeckResourceOrigin.Owned,
							predecessorEvidence = ReaderLegacyPredecessorEvidence.Truthful
						)
					)
				)
			},
			releaseLedger = ledger,
			enqueueResourceFact = { error("A truthful predecessor must be adopted") }
		)
		assertTrue(cutover.activate())
		assertEquals(1, cutover.snapshot().adoptedCount)
		assertEquals(1, cutover.snapshot().releaseOwnedCount)
		assertEquals(0, cutover.snapshot().releaseCommandIssuedCount)
	}

	@Test
	fun releaseLedgerAdmissionFailurePublishesBoundedFailClosedFact() {
		val ledger = ReaderTransitionReleaseLedger()
		(1L..64L).forEach { sequence ->
			val id = transitionId(sequence = sequence)
			assertNotNull(ledger.register(deckKey(id, opaqueId = 10_000L + sequence), 19L, 23L))
		}
		val id = transitionId(sequence = 65L)
		val key = deckKey(id, opaqueId = 10_065L)
		val observedFactKinds = mutableListOf<String>()
		val clock = ControllableTransitionClock()
		val active = assertNotNull(journal(id).active)
		val capacityJournal = readerAndroidHostTestJournal(
			committed = readerAndroidHostTestNeutralInitial(
				id.readerSessionGeneration,
				id.coordinatorEpoch
			),
			lastTransitionSequence = id.sequence,
			lastIssuedTransitionIdentity = id.parentIdentity(),
			active = active
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(onIssue = { _, _ -> }, clockPort = clock),
			mode = ReaderTransitionMode.Active,
			journal = capacityJournal,
			releaseLedger = ledger,
			onObservation = { observation ->
				observation.factKind?.name?.let(observedFactKinds::add)
			}
		)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(id, key))

		assertTrue(coordinator.snapshot().releaseOnlySink)
		assertEquals(ReaderTransitionOutcomeKind.Failed, coordinator.snapshot().lastOutcome)
		assertEquals(
			"ReleaseLedgerAdmissionRejected",
			coordinatorJournal(coordinator).releaseOnlyCleanup?.trigger?.name,
			"Ledger admission failure must not fabricate a physical process-close trigger"
		)
		assertTrue(
			"ReleaseLedgerAdmissionRejected" in observedFactKinds,
			"A journal-owned resource rejected by the ledger requires a typed fail-closed fact"
		)
		assertEquals(
			ReaderTransitionResourceState.Owned,
			coordinator.releaseStateOf(key),
			"Capacity rejection must retain exact unresolved physical responsibility"
		)
		assertEquals(1, clock.activeRegistrationCount)

		clock.fireOnlyActive()

		assertEquals(ReaderTransitionResourceState.TimedOutUnreleased, coordinator.releaseStateOf(key))
	}

	@Test
	fun richerRejectedRequestIsNotSuppressedByEarlierKeyOnlyAdmissionEvidence() {
		val ledger = ReaderTransitionReleaseLedger()
		(1L..64L).forEach { sequence ->
			val existingId = transitionId(sequence = sequence)
			assertNotNull(
				ledger.register(deckKey(existingId, opaqueId = 20_000L + sequence), 19L, 23L)
			)
		}
		val id = transitionId(sequence = 65L)
		val rejectedKey = deckKey(id, opaqueId = 20_065L)
		val base = journal(id)
		val active = assertNotNull(base.active).let { current ->
			current.copy(
				phase = current.phase.copy(
					contract = current.phase.contract.copy(
						admissibleCommandStages = setOf(
							ReaderTransitionCommandStage.DeckReservation,
							ReaderTransitionCommandStage.TimerBinding
						)
					)
				),
				pendingCommandStages = setOf(ReaderTransitionCommandStage.DeckReservation)
			)
		}
		val clock = ControllableTransitionClock()
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(onIssue = { _, _ -> }, clockPort = clock),
			mode = ReaderTransitionMode.Active,
			journal = base.copy(active = active),
			releaseLedger = ledger
		)

		coordinator.enqueue(ReaderTransitionFact.DeckRejected(id, rejectedKey))

		assertTrue(
			coordinator.snapshot().releaseOnlySink,
			"The richer rejected request must retain post-terminal cleanup authority"
		)
		assertEquals(
			"ReleaseLedgerAdmissionRejected",
			coordinatorJournal(coordinator).releaseOnlyCleanup?.trigger?.name
		)
		assertEquals(ReaderTransitionResourceState.Owned, coordinator.releaseStateOf(rejectedKey))
		assertEquals(1, clock.activeRegistrationCount)
	}

	@Test
	fun coordinatorDeduplicatesStaleResourceFactsAndConfirmsThroughItsMailbox() {
		val commands = mutableListOf<ReaderTransitionCommand>()
		lateinit var ports: TestPorts
		ports = TestPorts(onIssue = { command, onFact ->
			commands += command
			if (command is ReaderTransitionCommand.ReleaseResource) {
				onFact(ReaderTransitionFact.ResourceReleased(command.identity))
			}
		})
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			)
		)
		val key = deckKey(transitionId(), opaqueId = 59L)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(key.owningTransitionIdOrNull), key))
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(key.owningTransitionIdOrNull), key))

		assertEquals(1, commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size)
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(key))
		assertEquals(1, coordinator.snapshot().releasedResourceCount)
	}

	@Test
	fun physicalReleaseCallbackUsesThreadSafeMailboxIngress() {
		lateinit var release: ReaderTransitionCommand.ReleaseResource
		lateinit var callback: (ReaderTransitionFact) -> Unit
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(onIssue = { command, onFact ->
				if (command is ReaderTransitionCommand.ReleaseResource) {
					release = command
					callback = onFact
				}
			}),
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			)
		)
		val key = deckKey(transitionId(), opaqueId = 59L)
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(key.owningTransitionIdOrNull), key))
		val callbackFailure = AtomicReference<Throwable?>(null)

		Thread {
			try {
				callback(ReaderTransitionFact.ResourceReleased(release.identity))
			} catch (failure: Throwable) {
				callbackFailure.set(failure)
			}
		}.apply {
			start()
			join()
		}
		ShadowLooper.runUiThreadTasks()

		assertNull(callbackFailure.get())
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(key))
	}

	@Test
	fun releaseOnlyCleanupKeepsBoundedDeadlineUntilAcceptedReleaseTimesOut() {
		val clock = ControllableTransitionClock()
		val commands = mutableListOf<ReaderTransitionCommand.ReleaseResource>()
		val id = transitionId()
		val key = deckKey(id, opaqueId = 60L)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(
				onIssue = { command, _ ->
					if (command is ReaderTransitionCommand.ReleaseResource) commands += command
				},
				clockPort = clock
			),
			mode = ReaderTransitionMode.Active,
			journal = journal(id)
		)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(id, key))
		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))

		assertEquals(1, commands.size)
		assertEquals(ReaderTransitionResourceState.ReleaseCommandIssued, coordinator.releaseStateOf(key))
		assertEquals(1, clock.activeRegistrationCount)
		assertEquals(1, coordinator.snapshot().scheduledCallbackCount)
		clock.fireOnlyActive()
		assertEquals(ReaderTransitionResourceState.TimedOutUnreleased, coordinator.releaseStateOf(key))
		assertEquals(0, clock.activeRegistrationCount)
		val terminalJournal = coordinatorJournal(coordinator)
		assertEquals(
			ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
			terminalJournal.releaseOnlyCleanup?.deadlineStatus
		)
		assertEquals(
			ReaderTransitionFailureReason.CloseDrainTimeout,
			assertIs<paige.navic.reader.ReaderTransitionOutcome.Failed>(terminalJournal.lastOutcome).reason
		)
	}

	@Test
	fun releaseCleanupDeadlineCoversTimerBindingRejectionAndThrowOrigins() {
		listOf(
			ReaderTransitionCommandRejectionReason.TimerBindingRejected to
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected,
			ReaderTransitionCommandRejectionReason.CommandThrew to
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
		).forEachIndexed { index, (reason, expectedStatus) ->
			val clock = ControllableTransitionClock()
			val id = transitionId(
				operation = ReaderTransitionOperation.PublicationClose,
				sequence = index.toLong() + 1L
			)
			val key = deckKey(id, opaqueId = 64L + index)
			val base = journal(id)
			val owned = base.copy(
				active = requireNotNull(base.active).copy(
					ownedResourceKeys = setOf(key),
					pendingCommandStages = setOf(ReaderTransitionCommandStage.TimerBinding)
				)
			)
			val coordinator = ReaderResumableTransitionCoordinator(
				ports = TestPorts(onIssue = { _, _ -> }, clockPort = clock),
				mode = ReaderTransitionMode.Active,
				journal = owned
			)

			coordinator.enqueue(
				ReaderTransitionFact.CommandRejected(
					id,
					ReaderTransitionCommandStage.TimerBinding,
					reason
				)
			)

			assertEquals(expectedStatus, coordinator.snapshot().releaseOnlyDeadlineStatus)
			assertEquals(ReaderTransitionResourceState.ReleaseCommandIssued, coordinator.releaseStateOf(key))
			assertEquals(1, clock.activeRegistrationCount)
			clock.fireOnlyActive()
			assertEquals(ReaderTransitionResourceState.TimedOutUnreleased, coordinator.releaseStateOf(key))
		}
	}

	@Test
	fun semanticViolationDoesNotFabricatePhysicalProcessClose() {
		val clock = ControllableTransitionClock()
		val id = transitionId()
		val key = deckKey(id, opaqueId = 60L)
		val base = journal(id)
		val owned = base.copy(
			active = requireNotNull(base.active).copy(ownedResourceKeys = setOf(key))
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(onIssue = { _, _ -> }, clockPort = clock),
			mode = ReaderTransitionMode.Active,
			journal = owned
		)

		coordinator.enqueue(
			ReaderTransitionFact.SemanticPortContractViolated(
				id,
				ReaderSemanticPortContractViolationReason.ThrowAfterMutationStarted,
				ReaderSaturatingCallbackCount.Zero
			)
		)

		assertEquals(ReaderTransitionResourceState.ReleaseCommandIssued, coordinator.releaseStateOf(key))
		assertEquals(1, clock.activeRegistrationCount)
	}

	@Test
	fun actualProcessCloseTerminalizesExactCleanupAndLateResourcesWithoutDispatch() {
		val clock = ControllableTransitionClock()
		val commands = mutableListOf<ReaderTransitionCommand.ReleaseResource>()
		val id = transitionId()
		val key = deckKey(id, opaqueId = 61L)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(
				onIssue = { command, _ ->
					if (command is ReaderTransitionCommand.ReleaseResource) commands += command
				},
				clockPort = clock
			),
			mode = ReaderTransitionMode.Active,
			journal = journal(id)
		)
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(id, key))
		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		val journalField = coordinator.javaClass.getDeclaredField("journal").apply {
			isAccessible = true
		}
		val cleanupKey = requireNotNull(
			(journalField.get(coordinator) as ReaderTransitionJournal).releaseOnlyCleanup
		).key

		coordinator.enqueueProcessClosed(cleanupKey)

		assertEquals(ReaderTransitionResourceState.ProcessClosedUnreleased, coordinator.releaseStateOf(key))
		assertEquals(0, clock.activeRegistrationCount)
		val processClosedJournal = coordinatorJournal(coordinator)
		assertEquals(
			ReaderReleaseOnlyCleanupDeadlineStatus.ProcessClosed,
			processClosedJournal.releaseOnlyCleanup?.deadlineStatus
		)
		assertEquals(
			ReaderTransitionFailureReason.CloseDrainTimeout,
			assertIs<paige.navic.reader.ReaderTransitionOutcome.Failed>(
				processClosedJournal.lastOutcome
			).reason
		)
		val lateId = id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity())
		val lateKey = deckKey(lateId, opaqueId = 63L)
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(lateId, lateKey))
		assertEquals(ReaderTransitionResourceState.ProcessClosedUnreleased, coordinator.releaseStateOf(lateKey))
		assertEquals(1, commands.size)
	}

	@Test
	fun nonactivatedCloseTimerCancelThrowRetainsExactCallbackAuthority() {
		val clock = ControllableTransitionClock(cancelThrows = true)
		val id = transitionId()
		val key = deckKey(id, opaqueId = 62L)
		val base = journal(id)
		val owned = base.copy(
			active = assertNotNull(base.active).copy(ownedResourceKeys = setOf(key))
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(
				onIssue = { command, onFact ->
					when (command) {
						is ReaderTransitionCommand.ReleaseResource ->
							onFact(ReaderTransitionFact.ResourceReleased(command.identity))
						is ReaderTransitionCommand.CancelOwnedWork -> onFact(
							ReaderTransitionFact.OwnedWorkCancellationCompleted(
								cleanupKey = assertNotNull(command.cleanupKey),
								cancelledTransitionId = command.transitionId,
								outcome = paige.navic.reader.ReaderOwnedWorkCancellationOutcome.Applied
							)
						)
						else -> Unit
					}
				},
				clockPort = clock
			),
			mode = ReaderTransitionMode.Active,
			journal = owned
		)

		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))

		assertEquals(
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew,
			coordinator.snapshot().releaseOnlyDeadlineStatus
		)
		assertEquals(1, coordinator.snapshot().scheduledCallbackCount)
		assertEquals(1, clock.activeRegistrationCount)
		assertEquals(1, clock.cancelCount)
		assertEquals(
			ReaderTransitionFailureReason.CloseDrainTimeout,
			assertIs<paige.navic.reader.ReaderTransitionOutcome.Failed>(
				coordinatorJournal(coordinator).lastOutcome
			).reason
		)

		clock.fireOnlyActive()

		assertEquals(
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew,
			coordinator.snapshot().releaseOnlyDeadlineStatus
		)
		assertEquals(0, coordinator.snapshot().scheduledCallbackCount)
	}

	@Test
	fun closeTimeoutRetainsReleaseOnlySinkForLateFacts() {
		val commands = mutableListOf<ReaderTransitionCommand>()
		val ports = TestPorts(onIssue = { command, onFact ->
			commands += command
			if (command is ReaderTransitionCommand.ReleaseResource) {
				onFact(ReaderTransitionFact.ResourceReleased(command.identity))
			}
		})
		val id = transitionId(operation = ReaderTransitionOperation.PublicationClose)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = journal(id)
		)

		coordinator.enqueue(ReaderTransitionFact.DeadlineExpired(id))
		assertTrue(coordinator.snapshot().releaseOnlySink)
		val commandCountAtClose = commands.size
		val lateKey = deckKey(id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity()), opaqueId = 61L)
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(lateKey.owningTransitionIdOrNull), lateKey))
		coordinator.enqueue(ReaderTransitionFact.DeckOwned(requireNotNull(lateKey.owningTransitionIdOrNull), lateKey))

		assertEquals(
			1,
			commands.drop(commandCountAtClose).filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size
		)
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(lateKey))
		assertTrue(coordinator.snapshot().releaseOnlySink)
		assertEquals(null, coordinator.snapshot().activePhase)
	}

	@Test
	fun lateReleaseOnlyResourceAfterTimeoutIsGroupedAndTerminalizedWithoutReissue() {
		val clock = ControllableTransitionClock()
		val commands = mutableListOf<ReaderTransitionCommand>()
		val ports = TestPorts(
			onIssue = { command, _ -> commands += command },
			clockPort = clock
		)
		val id = transitionId(operation = ReaderTransitionOperation.PublicationClose)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = journal(id)
		)
		coordinator.enqueue(ReaderTransitionFact.DeadlineExpired(id))
		val commandCountAtClose = commands.size
		val lateId = id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity())
		val lateKey = deckKey(lateId, opaqueId = 62L)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(lateId, lateKey))
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(lateId, lateKey))

		val release = commands.drop(commandCountAtClose)
			.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			.single()
		assertNotNull(release.cleanupKey)
		assertEquals(
			ReaderTransitionResourceState.TimedOutUnreleased,
			coordinator.releaseStateOf(lateKey),
			"A later row must inherit the already elapsed fixed cleanup budget"
		)
		assertEquals(0, clock.activeRegistrationCount)
		assertEquals(
			0,
			clock.scheduleCount,
			"An already elapsed close budget must not schedule a replacement deadline"
		)
	}

	@Test
	fun completedCleanupDoesNotReopenDeadlineForALateResource() {
		val clock = ControllableTransitionClock()
		val commands = mutableListOf<ReaderTransitionCommand>()
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(
				onIssue = { command, _ -> commands += command },
				clockPort = clock
			),
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(
				committed = ReaderCommittedPresentation.Initial(
					ReaderInitialCommittedPresentationOrigin.Neutral(
						readerSessionGeneration = 19L,
						coordinatorEpoch = 23L,
						requestedLease = ReaderInitialPresentationInputLease.None,
						physicalLease = ReaderInitialPresentationInputLease.None
					)
				)
			)
		)

		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		assertEquals(
			ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting,
			coordinatorJournal(coordinator).releaseOnlyCleanup?.deadlineStatus
		)
		assertEquals(0, clock.scheduleCount)
		val lateId = transitionId()
		val lateKey = deckKey(lateId, opaqueId = 65L)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(lateId, lateKey))

		assertEquals(1, commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size)
		assertEquals(ReaderTransitionResourceState.TimedOutUnreleased, coordinator.releaseStateOf(lateKey))
		assertEquals(0, clock.scheduleCount)
		assertEquals(
			ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting,
			coordinatorJournal(coordinator).releaseOnlyCleanup?.deadlineStatus,
			"Completed cleanup remains immutable while the late row terminalizes in the ledger"
		)
	}

	@Test
	fun adoptedReleaseConfirmationUsesRegistrationRetirementFenceWithoutTransitionTombstone() {
		val binding = transitionId().let {
			(it.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		}
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(79L),
				binding,
				83L,
				89L,
				1200,
				800
			)
		)
		val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(97L)
		val ledger = ReaderTransitionReleaseLedger()
		val physicalIdentity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(101L)),
			ReaderLegacyInventorySource.FrameOrHandoff,
			ReaderLegacySourceLocalOpaqueToken(103L)
		)
		val registration = assertNotNull(
			ledger.importLegacy(
				physicalIdentity,
				ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
				ReaderTransitionResourceKind.FrameHandoff,
				23L
			)
		).registration
		val journal = ReaderTransitionJournal(
			committed = ReaderCommittedPresentation.Initial(
				ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
					seedId = seedId,
					readerSessionGeneration = 19L,
					coordinatorEpoch = 23L,
					owner = owner,
					binding = binding,
					resource = registration,
					requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
					physicalLease = ReaderInitialPresentationInputLease.ChromeOnly
				)
			)
		)
		val issued = mutableListOf<ReaderTransitionCommand.ReleaseResource>()
		val ports = TestPorts(onIssue = { command, onFact ->
			if (command is ReaderTransitionCommand.ReleaseResource) {
				issued += command
				onFact(
					ReaderTransitionFact.ResourceReleased(command.identity)
				)
			}
		})
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = journal,
			releaseLedger = ledger
		)

		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))

		assertEquals(1, issued.size)
		assertNull(issued.single().transitionId)
		assertEquals(0, ledger.retentionSnapshot().activeStateCount)
		assertTrue(ledger.retirementFence().confirms(registration.retirementOrder))
	}

	@Test
	fun task4FactGateRejectsMalformedResourceFactsBeforeMailboxAccounting() {
		val commands = mutableListOf<ReaderTransitionCommand>()
		val ports = TestPorts(
			onIssue = { command, _ -> commands += command },
			factAcceptance = ReaderTransitionFact::isTask4CoordinatorFact
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			)
		)
		val id = transitionId()
		val otherId = id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity())
		val deckKey = deckKey(id, opaqueId = 71L)
		val malformed = listOf(
			ReaderTransitionFact.DeckOwned(id, deckKey.copy(transitionId = otherId)) to deckKey,
			ReaderTransitionFact.DeckPrepared(
				id,
				deckKey.copy(kind = ReaderTransitionResourceKind.Raster)
			) to deckKey,
			ReaderTransitionFact.ResourceObserved(
				id,
				deckKey.copy(kind = ReaderTransitionResourceKind.CallbackRegistration)
			) to deckKey,
			releaseFact(id, deckKey.copy(kind = ReaderTransitionResourceKind.FrameHandoff)) to deckKey
		)
		val before = coordinator.snapshot()

		malformed.forEach { (fact, key) ->
			assertFalse(fact.isTask4CoordinatorFact())
			assertFailsWith<IllegalStateException> { coordinator.enqueue(fact) }
			assertEquals(before, coordinator.snapshot())
			assertNull(coordinator.releaseStateOf(key))
		}
		assertTrue(commands.isEmpty())
	}

	@Test
	fun cleanupStatusRemainsOpenWhileAnyLaterRowIsUnresolved() {
		val ledger = ReaderTransitionReleaseLedger()
		val cleanup = cleanupKey()
		val firstId = transitionId(sequence = 1L)
		val first = assertNotNull(
			ledger.register(deckKey(firstId, opaqueId = 291L), 19L, 23L)
		)
		assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(firstId), first, cleanup)
		)
		assertEquals(1, ledger.markCleanupDeadlineElapsed(cleanup))
		val secondId = transitionId(sequence = 2L)
		val second = assertNotNull(
			ledger.register(deckKey(secondId, opaqueId = 293L), 19L, 23L)
		)
		assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(secondId), second, cleanup)
		)

		assertEquals(
			ReaderReleaseLedgerCleanupStatus.Open,
			ledger.cleanupStatus(cleanup),
			"A terminal predecessor row cannot hide an unresolved later cleanup row"
		)
	}

	@Test
	fun ordinaryReleaseAttemptTransfersToCleanupWithoutReissueAndTimesOutBoundedly() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId()
		val registration = assertNotNull(
			ledger.register(deckKey(id, opaqueId = 301L), 19L, 23L)
		)
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		assertNull(ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration))
		assertTrue(
			ledger.recordRejectedNoEffect(
				ReaderTransitionFact.ReleaseCommandRejected(
					cleanupKey = null,
					identity = command.identity,
					reason = ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
			)
		)

		val cleanup = cleanupKey()
		assertEquals(1, ledger.transferOutstandingAttemptsToCleanup(cleanup))
		assertNull(ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration))
		assertEquals(1, ledger.markCleanupDeadlineElapsed(cleanup))
		assertEquals(ReaderTransitionResourceState.TimedOutUnreleased, ledger.stateOf(registration.key))
		assertEquals(1, ledger.failureFence().tombstones.size)
	}

	@Test
	fun importedLegacyReleaseRequiresExactAttemptRegistrationAndPhysicalIdentity() {
		val ledger = ReaderTransitionReleaseLedger()
		val token = ReaderLegacyFreezeToken(307L)
		val domain = ReaderLegacyPhysicalDomain(19L, token)
		val physical = ReaderLegacyPhysicalIdentity(
			domain,
			ReaderLegacyInventorySource.Deck,
			ReaderLegacySourceLocalOpaqueToken(311L)
		)
		val otherSourceSameLocal = physical.copy(source = ReaderLegacyInventorySource.FrameOrHandoff)
		val otherDomain = physical.copy(
			domain = ReaderLegacyPhysicalDomain(20L, ReaderLegacyFreezeToken(307L))
		)
		val otherLocalToken = physical.copy(
			sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(312L)
		)
		val owner = ReaderTransitionResourceOwnerId.AdoptedPredecessor(
			ReaderAdoptedPredecessorSeedId.fromValidatedImport(313L)
		)
		val imported = assertNotNull(
			ledger.importLegacy(physical, owner, ReaderTransitionResourceKind.Deck, 23L)
		)
		val distinct = assertNotNull(
			ledger.importLegacy(otherSourceSameLocal, owner, ReaderTransitionResourceKind.Deck, 23L)
		)
		assertNull(
			ledger.importLegacy(physical, owner, ReaderTransitionResourceKind.Deck, 24L)
		)
		assertTrue(imported.registration != distinct.registration)
		val command = assertNotNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Session(19L, 23L),
				imported.registration
			)
		)
		val confirmation = ReaderTransitionFact.ResourceReleased(command.identity)

		assertFalse(ledger.confirmLegacyReleased(otherSourceSameLocal, confirmation))
		assertFalse(ledger.confirmLegacyReleased(otherDomain, confirmation))
		assertFalse(ledger.confirmLegacyReleased(otherLocalToken, confirmation))
		assertTrue(ledger.confirmLegacyReleased(physical, confirmation))
		assertFalse(ledger.confirmLegacyReleased(physical, confirmation))
		assertTrue(
			ledger.importLegacy(physical, owner, ReaderTransitionResourceKind.Deck, 23L) ===
				imported
		)
		assertNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Session(19L, 23L),
				imported.registration
			)
		)
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(imported.registration.key))
	}

	@Test
	fun adoptedReleaseAuthorityRequiresAnExactPhysicalImport() {
		val ledger = ReaderTransitionReleaseLedger()
		val owner = ReaderTransitionResourceOwnerId.AdoptedPredecessor(
			ReaderAdoptedPredecessorSeedId.fromValidatedImport(317L)
		)
		val key = ReaderTransitionResourceKey(
			owner,
			ReaderTransitionResourceKind.Deck,
			319L
		)
		val registration = ReaderTransitionResourceRegistration(
			key,
			ReaderResourceRetirementOrder(19L, 23L, 1L)
		)

		assertNull(ledger.register(key, 19L, 23L))
		assertFalse(ledger.register(registration))
		assertNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Session(19L, 23L),
				registration
			)
		)
		assertNull(ledger.stateOf(key))
	}

	@Test
	fun coordinatorRoutesImportedReleaseThroughExactPhysicalEnvelope() {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "imported-routing",
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 5L,
			rasterGeneration = 7L,
			textureGeneration = 11L
		)
		val owner = ReaderPresentationFrameOwner.NativePage(
			ReaderNativePagePresentationProof(
				binding = binding,
				transitionToken = null,
				presentedFrame = 19L,
				viewportWidth = 1200,
				viewportHeight = 800,
				rasterGeneration = 7L,
				textureGeneration = 11L
			)
		)
		val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(23L)
		val physical = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(29L)),
			ReaderLegacyInventorySource.Deck,
			ReaderLegacySourceLocalOpaqueToken(31L)
		)
		val ledger = ReaderTransitionReleaseLedger()
		val imported = assertNotNull(
			ledger.importLegacy(
				physical,
				ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
				ReaderTransitionResourceKind.Deck,
				23L
			)
		)
		val journal = ReaderTransitionJournal(
			committed = ReaderCommittedPresentation.Initial(
				ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
					seedId = seedId,
					readerSessionGeneration = 19L,
					coordinatorEpoch = 23L,
					owner = owner,
					binding = binding,
					resource = imported.registration,
					requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
					physicalLease = ReaderInitialPresentationInputLease.ChromeOnly
				)
			)
		)
		var routed = 0
		lateinit var routedCommand: ReaderTransitionCommand.ReleaseResource
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(
				onIssue = { _, _ -> error("Imported release cannot use the generic route") },
				onImportedIssue = { dispatch, onFact ->
					routed += 1
					routedCommand = dispatch.command
					assertTrue(dispatch.imported === imported)
					onFact(ReaderTransitionFact.ResourceReleased(dispatch.command.identity))
				}
			),
			mode = ReaderTransitionMode.Active,
			journal = journal,
			releaseLedger = ledger
		)

		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))

		assertEquals(1, routed)
		assertNull(routedCommand.transitionId)
		assertNotNull(routedCommand.cleanupKey)
		assertTrue(routedCommand.registration === imported.registration)
		assertEquals(
			ReaderTransitionResourceState.Released,
			ledger.stateOf(imported.registration.key)
		)
	}

	@Test
	fun releaseAdapterDoesNotPropagateFactSinkFailureIntoPhysicalCallback() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId()
		val registration = assertNotNull(
			ledger.register(deckKey(id, opaqueId = 315L), 19L, 23L)
		)
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		val outcome = runCatching {
			ReaderPhysicalReleaseCommandAdapter().dispatchGeneric(
				command = command,
				invoke = { callback ->
					callback(ReaderTransitionFact.ResourceReleased(command.identity))
					ReaderPortCommandResult.Accepted
				},
				onFact = { error("synthetic fact sink failure") }
			)
		}

		assertNull(outcome.exceptionOrNull())
	}

	@Test
	fun releaseAdapterCallbackThenRejectedKeepsAuthorityAndReportsOneViolation() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId()
		val registration = assertNotNull(
			ledger.register(deckKey(id, opaqueId = 317L), 19L, 23L)
		)
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		val facts = mutableListOf<ReaderTransitionFact>()
		ReaderPhysicalReleaseCommandAdapter().dispatchGeneric(
			command = command,
			invoke = { callback ->
				callback(ReaderTransitionFact.ResourceReleased(command.identity))
				ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
			},
			onFact = facts::add
		)

		assertEquals(2, facts.size)
		assertIs<ReaderTransitionFact.ResourceReleased>(facts[0])
		val violation = assertIs<ReaderTransitionFact.ReleasePortContractViolated>(facts[1])
		assertEquals(ReaderReleasePortContractViolationReason.CallbackThenRejected, violation.reason)
		assertTrue(ledger.confirmReleased(facts[0] as ReaderTransitionFact.ResourceReleased))
		assertFalse(
			ledger.recordRejectedNoEffect(
				ReaderTransitionFact.ReleaseCommandRejected(
					null,
					command.identity,
					ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
			)
		)
	}

	@Test
	fun releaseAdapterLateCallbackPromotesAmbiguousAttemptWithoutSecondDispatch() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId()
		val registration = assertNotNull(
			ledger.register(deckKey(id, opaqueId = 331L), 19L, 23L)
		)
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		lateinit var callback: (ReaderTransitionFact.ResourceReleased) -> Unit
		val facts = mutableListOf<ReaderTransitionFact>()
		ReaderPhysicalReleaseCommandAdapter().dispatchGeneric(
			command = command,
			invoke = { captured ->
				callback = captured
				throw IllegalStateException("bounded fixture failure")
			},
			onFact = facts::add
		)
		val threw = assertIs<ReaderTransitionFact.ReleaseCommandThrew>(facts.single())
		assertEquals(
			ReaderReleaseCommandAmbiguityReason.ThrowAfterPhysicalEffectMayHaveOccurred,
			threw.reason
		)
		assertTrue(ledger.recordAmbiguousFailure(threw))

		callback(ReaderTransitionFact.ResourceReleased(command.identity))
		val released = assertIs<ReaderTransitionFact.ResourceReleased>(facts[1])
		assertTrue(ledger.confirmReleased(released))
		assertNull(ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration))
	}

	@Test
	fun genericReleaseOrderingMatrixIsAttemptExactAndBounded() {
		val outcomes = listOf("accepted", "rejected", "throw")
		val callbackCounts = listOf(0, 1, 2, 4)
		var fixture = 0L
		for (outcome in outcomes) {
			for (callbackCount in callbackCounts) {
				fixture += 1L
				val ledger = ReaderTransitionReleaseLedger()
				val id = transitionId(sequence = fixture)
				val registration = assertNotNull(
					ledger.register(deckKey(id, opaqueId = 400L + fixture), 19L, 23L)
				)
				val command = assertNotNull(
					ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
				)
				val facts = mutableListOf<ReaderTransitionFact>()
				ReaderPhysicalReleaseCommandAdapter().dispatchGeneric(
					command = command,
					invoke = { callback ->
						repeat(callbackCount) {
							callback(ReaderTransitionFact.ResourceReleased(command.identity))
						}
						when (outcome) {
							"accepted" -> ReaderPortCommandResult.Accepted
							"rejected" -> ReaderPortCommandResult.Rejected(
								ReaderTransitionFailureReason.PortRejected
							)
							else -> throw IllegalStateException("bounded matrix throw")
						}
					},
					onFact = facts::add
				)

				val confirmations = facts.filterIsInstance<ReaderTransitionFact.ResourceReleased>()
				assertEquals(if (callbackCount == 0) 0 else 1, confirmations.size)
				confirmations.singleOrNull()?.let { assertTrue(it.identity === command.identity) }
				if (callbackCount == 0) {
					when (outcome) {
						"accepted" -> assertTrue(facts.isEmpty())
						"rejected" -> assertIs<ReaderTransitionFact.ReleaseCommandRejected>(facts.single())
						else -> assertIs<ReaderTransitionFact.ReleaseCommandThrew>(facts.single())
					}
				} else {
					val expectsViolation = callbackCount > 1 || outcome != "accepted"
					assertEquals(
						if (expectsViolation) 1 else 0,
						facts.filterIsInstance<ReaderTransitionFact.ReleasePortContractViolated>().size
					)
					facts.filterIsInstance<ReaderTransitionFact.ReleasePortContractViolated>()
						.singleOrNull()?.let { violation ->
							assertEquals(
								when (callbackCount) {
									1 -> ReaderSaturatingCallbackCount.One
									2 -> ReaderSaturatingCallbackCount.Two
									else -> ReaderSaturatingCallbackCount.ThreeOrMore
								},
								violation.callbackCount
							)
							val expectedReason = when {
								callbackCount > 1 && outcome != "accepted" ->
									ReaderReleasePortContractViolationReason.MultipleViolations
								callbackCount > 1 ->
									ReaderReleasePortContractViolationReason.DuplicateConfirmation
								outcome == "rejected" ->
									ReaderReleasePortContractViolationReason.CallbackThenRejected
								else -> ReaderReleasePortContractViolationReason.CallbackThenThrew
							}
							assertEquals(expectedReason, violation.reason)
						}
				}
			}
		}
	}

	@Test
	fun legacyReleaseOrderingMatrixRequiresAttemptAndPhysicalIdentity() {
		val outcomes = listOf("accepted", "rejected", "throw")
		val callbackCounts = listOf(0, 1, 2, 4)
		var fixture = 0L
		for (outcome in outcomes) {
			for (callbackCount in callbackCounts) {
				fixture += 1L
				val ledger = ReaderTransitionReleaseLedger()
				val physical = ReaderLegacyPhysicalIdentity(
					ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(500L + fixture)),
					ReaderLegacyInventorySource.Deck,
					ReaderLegacySourceLocalOpaqueToken(600L + fixture)
				)
				val imported = assertNotNull(
					ledger.importLegacy(
						physical,
						ReaderTransitionResourceOwnerId.AdoptedPredecessor(
							ReaderAdoptedPredecessorSeedId.fromValidatedImport(700L + fixture)
						),
						ReaderTransitionResourceKind.Deck,
						23L
					)
				)
				val command = assertNotNull(
					ledger.requestRelease(
						ReaderResourceReleaseIssuer.Session(19L, 23L),
						imported.registration
					)
				)
				val facts = mutableListOf<ReaderTransitionFact>()
				ReaderPhysicalReleaseCommandAdapter().dispatchLegacy(
					ReaderImportedLegacyReleaseDispatch(command, imported),
					invoke = { callback ->
						callback(
							physical.copy(source = ReaderLegacyInventorySource.FrameOrHandoff),
							ReaderTransitionFact.ResourceReleased(command.identity)
						)
						repeat(callbackCount) {
							callback(
								physical,
								ReaderTransitionFact.ResourceReleased(command.identity)
							)
						}
						when (outcome) {
							"accepted" -> ReaderPortCommandResult.Accepted
							"rejected" -> ReaderPortCommandResult.Rejected(
								ReaderTransitionFailureReason.PortRejected
							)
							else -> throw IllegalStateException("bounded legacy matrix throw")
						}
					},
					onFact = facts::add
				)
				assertEquals(
					if (callbackCount == 0) 0 else 1,
					facts.filterIsInstance<ReaderTransitionFact.ResourceReleased>().size
				)
				facts.filterIsInstance<ReaderTransitionFact.ResourceReleased>().singleOrNull()?.let {
					assertTrue(ledger.confirmLegacyReleased(physical, it))
				}
				if (callbackCount == 0) {
					when (outcome) {
						"accepted" -> assertTrue(facts.isEmpty())
						"rejected" ->
							assertIs<ReaderTransitionFact.ReleaseCommandRejected>(facts.single())
						else -> assertIs<ReaderTransitionFact.ReleaseCommandThrew>(facts.single())
					}
				} else {
					val violations = facts.filterIsInstance<
						ReaderTransitionFact.ReleasePortContractViolated
					>()
					val expectsViolation = callbackCount > 1 || outcome != "accepted"
					assertEquals(if (expectsViolation) 1 else 0, violations.size)
					violations.singleOrNull()?.let { violation ->
						assertEquals(
							when (callbackCount) {
								1 -> ReaderSaturatingCallbackCount.One
								2 -> ReaderSaturatingCallbackCount.Two
								else -> ReaderSaturatingCallbackCount.ThreeOrMore
							},
							violation.callbackCount
						)
						val expectedReason = when {
							callbackCount > 1 && outcome != "accepted" ->
								ReaderReleasePortContractViolationReason.MultipleViolations
							callbackCount > 1 ->
								ReaderReleasePortContractViolationReason.DuplicateConfirmation
							outcome == "rejected" ->
								ReaderReleasePortContractViolationReason.CallbackThenRejected
							else -> ReaderReleasePortContractViolationReason.CallbackThenThrew
						}
						assertEquals(expectedReason, violation.reason)
					}
				}
				assertFalse(
					facts.filterIsInstance<ReaderTransitionFact.ResourceReleased>().any {
						it.identity != command.identity
					}
				)
			}
		}
	}

	@Test
	fun asynchronousReleaseDuplicatesPublishOnlyFirstDuplicateAndSaturationEvidence() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId(sequence = 39L)
		val registration = assertNotNull(
			ledger.register(deckKey(id, opaqueId = 797L), 19L, 23L)
		)
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		lateinit var callback: (ReaderTransitionFact.ResourceReleased) -> Unit
		val facts = mutableListOf<ReaderTransitionFact>()
		ReaderPhysicalReleaseCommandAdapter().dispatchGeneric(
			command,
			invoke = { captured ->
				callback = captured
				ReaderPortCommandResult.Accepted
			},
			onFact = facts::add
		)

		repeat(100) {
			callback(ReaderTransitionFact.ResourceReleased(command.identity))
		}

		assertEquals(1, facts.filterIsInstance<ReaderTransitionFact.ResourceReleased>().size)
		assertEquals(
			listOf(ReaderSaturatingCallbackCount.Two, ReaderSaturatingCallbackCount.ThreeOrMore),
			facts.filterIsInstance<ReaderTransitionFact.ReleasePortContractViolated>()
				.map { it.callbackCount }
		)
		assertEquals(3, facts.size, "Saturated asynchronous duplicates must be publication-bounded")
	}

	@Test
	fun genericReleaseCallbacksAfterResultPromoteEveryOutstandingOutcome() {
		listOf("accepted", "rejected", "throw").forEachIndexed { index, outcome ->
			val ledger = ReaderTransitionReleaseLedger()
			val id = transitionId(sequence = 40L + index)
			val registration = assertNotNull(
				ledger.register(deckKey(id, opaqueId = 800L + index), 19L, 23L)
			)
			val command = assertNotNull(
				ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
			)
			lateinit var callback: (ReaderTransitionFact.ResourceReleased) -> Unit
			val facts = mutableListOf<ReaderTransitionFact>()
			ReaderPhysicalReleaseCommandAdapter().dispatchGeneric(
				command,
				invoke = { captured ->
					callback = captured
					when (outcome) {
						"accepted" -> ReaderPortCommandResult.Accepted
						"rejected" -> ReaderPortCommandResult.Rejected(
							ReaderTransitionFailureReason.PortRejected
						)
						else -> throw IllegalStateException("late generic matrix throw")
					}
				},
				onFact = facts::add
			)
			when (outcome) {
				"accepted" -> assertTrue(facts.isEmpty())
				"rejected" -> assertTrue(
					ledger.recordRejectedNoEffect(
						assertIs<ReaderTransitionFact.ReleaseCommandRejected>(facts.single())
					)
				)
				else -> assertTrue(
					ledger.recordAmbiguousFailure(
						assertIs<ReaderTransitionFact.ReleaseCommandThrew>(facts.single())
					)
				)
			}

			callback(ReaderTransitionFact.ResourceReleased(command.identity))

			val released = facts.filterIsInstance<ReaderTransitionFact.ResourceReleased>().single()
			assertTrue(ledger.confirmReleased(released))
			val violations = facts.filterIsInstance<ReaderTransitionFact.ReleasePortContractViolated>()
			assertEquals(if (outcome == "accepted") 0 else 1, violations.size)
			violations.singleOrNull()?.let { violation ->
				assertEquals(
					if (outcome == "rejected") {
						ReaderReleasePortContractViolationReason.LateCallbackAfterRejected
					} else {
						ReaderReleasePortContractViolationReason.LateCallbackAfterAmbiguousFailure
					},
					violation.reason
				)
			}
			assertNull(ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration))
		}
	}

	@Test
	fun importedReleaseCallbacksAfterResultRequireExactPhysicalOrigin() {
		listOf("accepted", "rejected", "throw").forEachIndexed { index, outcome ->
			val physical = ReaderLegacyPhysicalIdentity(
				ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(850L + index)),
				ReaderLegacyInventorySource.Deck,
				ReaderLegacySourceLocalOpaqueToken(860L + index)
			)
			val ledger = ReaderTransitionReleaseLedger()
			val imported = assertNotNull(
				ledger.importLegacy(
					physical,
					ReaderTransitionResourceOwnerId.AdoptedPredecessor(
						ReaderAdoptedPredecessorSeedId.fromValidatedImport(870L + index)
					),
					ReaderTransitionResourceKind.Deck,
					23L
				)
			)
			val command = assertNotNull(
				ledger.requestRelease(
					ReaderResourceReleaseIssuer.Session(19L, 23L),
					imported.registration
				)
			)
			lateinit var callback: (
				ReaderLegacyPhysicalIdentity,
				ReaderTransitionFact.ResourceReleased
			) -> Unit
			val facts = mutableListOf<ReaderTransitionFact>()
			ReaderPhysicalReleaseCommandAdapter().dispatchLegacy(
				ReaderImportedLegacyReleaseDispatch(command, imported),
				invoke = { captured ->
					callback = captured
					when (outcome) {
						"accepted" -> ReaderPortCommandResult.Accepted
						"rejected" -> ReaderPortCommandResult.Rejected(
							ReaderTransitionFailureReason.PortRejected
						)
						else -> throw IllegalStateException("late imported matrix throw")
					}
				},
				onFact = facts::add
			)
			val beforeWrongOrigin = facts.toList()
			callback(
				physical.copy(sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(999L)),
				ReaderTransitionFact.ResourceReleased(command.identity)
			)
			assertTrue(facts == beforeWrongOrigin)
			callback(physical, ReaderTransitionFact.ResourceReleased(command.identity))

			val released = facts.filterIsInstance<ReaderTransitionFact.ResourceReleased>().single()
			assertTrue(ledger.confirmLegacyReleased(physical, released))
			val violations = facts.filterIsInstance<ReaderTransitionFact.ReleasePortContractViolated>()
			assertEquals(if (outcome == "accepted") 0 else 1, violations.size)
			violations.singleOrNull()?.let { violation ->
				assertEquals(
					if (outcome == "rejected") {
						ReaderReleasePortContractViolationReason.LateCallbackAfterRejected
					} else {
						ReaderReleasePortContractViolationReason.LateCallbackAfterAmbiguousFailure
					},
					violation.reason
				)
			}
		}
	}

	@Test
	fun wrongAttemptRegistrationCleanupAndDuplicateOutcomesAreInert() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId()
		val registration = assertNotNull(
			ledger.register(deckKey(id, opaqueId = 811L), 19L, 23L)
		)
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		val wrongIdentity = ReaderReleaseCommandIdentity(
			ReaderPhysicalReleaseAttemptId.fromLedger(command.identity.attemptId.value + 1L),
			registration
		)
		val wrongRegistration = ReaderTransitionResourceRegistration(
			registration.key,
			ReaderResourceRetirementOrder(
				19L,
				23L,
				registration.retirementOrder.sequence + 1L
			)
		)
		val wrongRegistrationIdentity = ReaderReleaseCommandIdentity(
			command.identity.attemptId,
			wrongRegistration
		)
		val wrongCleanupGeneration = cleanupKey().copy(
			generation = ReaderReleaseOnlyCleanupGeneration(2L)
		)
		val wrongCleanupId = cleanupKey().copy(
			cleanupId = ReaderReleaseOnlyCleanupId(2L)
		)
		assertFalse(
			ledger.recordRejectedNoEffect(
				ReaderTransitionFact.ReleaseCommandRejected(
					wrongCleanupGeneration,
					command.identity,
					ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
			)
		)
		assertFalse(
			ledger.recordRejectedNoEffect(
				ReaderTransitionFact.ReleaseCommandRejected(
					wrongCleanupId,
					command.identity,
					ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
			)
		)
		assertFalse(
			ledger.recordRejectedNoEffect(
				ReaderTransitionFact.ReleaseCommandRejected(
					null,
					wrongRegistrationIdentity,
					ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
			)
		)
		assertFalse(
			ledger.confirmReleased(
				ReaderTransitionFact.ResourceReleased(wrongRegistrationIdentity)
			)
		)
		assertFalse(
			ledger.recordAmbiguousFailure(
				ReaderTransitionFact.ReleaseCommandThrew(
					null,
					wrongIdentity,
					ReaderReleaseCommandAmbiguityReason.ThrowAfterPhysicalEffectMayHaveOccurred
				)
			)
		)
		val wrongIssuerId = transitionId(sequence = 2L)
		val wrongIssuerRegistration = assertNotNull(
			ledger.register(deckKey(wrongIssuerId, opaqueId = 812L), 19L, 23L)
		)
		assertNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Session(19L, 23L),
				wrongIssuerRegistration
			)
		)
		val exact = ReaderTransitionFact.ReleaseCommandRejected(
			null,
			command.identity,
			ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
		)
		assertTrue(ledger.recordRejectedNoEffect(exact))
		assertFalse(ledger.recordRejectedNoEffect(exact))
		assertNull(ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration))

		val cleanupLedger = ReaderTransitionReleaseLedger()
		val cleanupId = transitionId(sequence = 3L)
		val cleanupRegistration = assertNotNull(
			cleanupLedger.register(deckKey(cleanupId, opaqueId = 813L), 19L, 23L)
		)
		val cleanup = cleanupKey()
		val cleanupCommand = assertNotNull(
			cleanupLedger.requestRelease(
				ReaderResourceReleaseIssuer.Transition(cleanupId),
				cleanupRegistration,
				cleanup
			)
		)
		assertFalse(
			cleanupLedger.recordRejectedNoEffect(
				ReaderTransitionFact.ReleaseCommandRejected(
					null,
					cleanupCommand.identity,
					ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
			)
		)
		assertTrue(
			cleanupLedger.recordRejectedNoEffect(
				ReaderTransitionFact.ReleaseCommandRejected(
					cleanup,
					cleanupCommand.identity,
					ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
				)
			)
		)
	}

	@Test
	fun rejectedAttemptAdmissionTransfersExactOwnedRegistrationIntoCleanup() {
		val ledger = ReaderTransitionReleaseLedger()
		val saturatedCleanup = cleanupKey()
		val saturated = (1L..32L).map { sequence ->
			val registration = assertNotNull(
				ledger.register(
					deckKey(transitionId(sequence = sequence), opaqueId = 3_000L + sequence),
					19L,
					23L
				)
			)
			val command = assertNotNull(
				ledger.requestRelease(
					ReaderResourceReleaseIssuer.Transition(
						requireNotNull(registration.key.owningTransitionIdOrNull)
					),
					registration,
					saturatedCleanup
				)
			)
			assertTrue(
				ledger.recordRejectedNoEffect(
					ReaderTransitionFact.ReleaseCommandRejected(
						cleanupKey = saturatedCleanup,
						identity = command.identity,
						reason = ReaderReleaseCommandRejectionReason.PortRejectedNoEffect
					)
				)
			)
			registration
		}
		assertEquals(32, ledger.markCleanupDeadlineElapsed(saturatedCleanup))
		assertEquals(32, saturated.size)
		val targetId = transitionId(sequence = 33L)
		val target = assertNotNull(
			ledger.register(deckKey(targetId, opaqueId = 3_033L), 19L, 23L)
		)
		assertNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Transition(targetId),
				target
			)
		)
		val cleanup = ReaderReleaseOnlyCleanupKey(
			readerSessionGeneration = 19L,
			coordinatorEpoch = 23L,
			cleanupId = ReaderReleaseOnlyCleanupId(2L),
			generation = ReaderReleaseOnlyCleanupGeneration(2L)
		)

		assertEquals(1, ledger.transferOutstandingAttemptsToCleanup(cleanup))
		assertEquals(ReaderReleaseLedgerCleanupStatus.Open, ledger.cleanupStatus(cleanup))
		assertEquals(ReaderTransitionResourceState.Owned, ledger.stateOf(target.key))
		assertEquals(1, ledger.markCleanupDeadlineElapsed(cleanup))
		assertEquals(ReaderTransitionResourceState.TimedOutUnreleased, ledger.stateOf(target.key))
	}

	@Test
	fun retirementGapCapacityIsReservedBeforePhysicalAttemptAdmission() {
		val ledger = ReaderTransitionReleaseLedger()
		val registrations = (1L..34L).map { sequence ->
			val id = transitionId(sequence = sequence)
			assertNotNull(
				ledger.register(deckKey(id, opaqueId = 1_000L + sequence), 19L, 23L)
			)
		}
		val commands = registrations.take(33).map { registration ->
			val owner = assertIs<ReaderTransitionResourceOwnerId.TransitionOwned>(
				registration.key.ownerId
			)
			assertNotNull(
				ledger.requestRelease(
					ReaderResourceReleaseIssuer.Transition(owner.transitionId),
					registration
				)
			)
		}
		val lastRegistration = registrations.last()
		val lastOwner = assertIs<ReaderTransitionResourceOwnerId.TransitionOwned>(
			lastRegistration.key.ownerId
		)
		assertNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Transition(lastOwner.transitionId),
				lastRegistration
			)
		)

		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(commands.first().identity)))
		val lastCommand = assertNotNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Transition(lastOwner.transitionId),
				lastRegistration
			)
		)
		(commands.drop(1) + lastCommand).asReversed().forEach { command ->
			assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
			assertTrue(ledger.retirementFence().sanitizedProjection().outOfOrderReleasedCount <= 32)
		}
		assertEquals(0, ledger.retirementFence().sanitizedProjection().outOfOrderReleasedCount)
	}

	@Test
	fun mismatchedRegistrationDomainCannotAdvanceTerminalLifecycle() {
		val ledger = ReaderTransitionReleaseLedger()
		val firstId = transitionId()
		val firstKey = deckKey(firstId, opaqueId = 1_301L)
		val first = assertNotNull(
			ledger.register(
				firstKey,
				firstId.readerSessionGeneration,
				firstId.coordinatorEpoch
			)
		)
		val release = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(firstId), first)
		)
		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(release.identity)))
		assertEquals(1, ledger.retentionSnapshot().terminalTombstoneCount)
		val newerId = firstId.copy(
			readerSessionGeneration = firstId.readerSessionGeneration + 1L
		)

		assertNull(
			ledger.register(
				deckKey(newerId, opaqueId = 1_303L),
				firstId.readerSessionGeneration,
				firstId.coordinatorEpoch
			)
		)
		assertEquals(1, ledger.retentionSnapshot().terminalTombstoneCount)
	}

	@Test
	fun rejectedRetirementOrderCannotAdvanceTerminalLifecycle() {
		val ledger = ReaderTransitionReleaseLedger()
		val firstId = transitionId()
		val firstKey = deckKey(firstId, opaqueId = 1_305L)
		val first = assertNotNull(
			ledger.register(
				firstKey,
				firstId.readerSessionGeneration,
				firstId.coordinatorEpoch
			)
		)
		val release = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(firstId), first)
		)
		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(release.identity)))
		assertEquals(1, ledger.retentionSnapshot().terminalTombstoneCount)
		val newerId = firstId.copy(
			readerSessionGeneration = firstId.readerSessionGeneration + 1L
		)
		val rejected = ReaderTransitionResourceRegistration(
			deckKey(newerId, opaqueId = 1_307L),
			ReaderResourceRetirementOrder(
				newerId.readerSessionGeneration,
				newerId.coordinatorEpoch,
				Long.MAX_VALUE
			)
		)

		assertFalse(ledger.register(rejected))
		assertEquals(1, ledger.retentionSnapshot().terminalTombstoneCount)
	}

	@Test
	fun evictedExactTransitionTombstonesRemainMonotonicallyFenced() {
		val ledger = ReaderTransitionReleaseLedger()
		lateinit var firstKey: ReaderTransitionResourceKey
		repeat(40) { index ->
			val sequence = index.toLong() + 1L
			val id = transitionId(sequence = sequence)
			val key = deckKey(id, opaqueId = 1_100L + sequence)
			if (index == 0) firstKey = key
			val registration = assertNotNull(ledger.register(key, 19L, 23L))
			val command = assertNotNull(
				ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
			)
			assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		}
		assertEquals(32, ledger.snapshot().releasedCount)
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(firstKey))
		assertFalse(ledger.register(firstKey))
	}

	@Test
	fun activeRegistrationCapacityFailsClosedWithoutEviction() {
		val ledger = ReaderTransitionReleaseLedger()
		val admitted = (1L..64L).map { sequence ->
			val id = transitionId(sequence = sequence)
			assertNotNull(
				ledger.register(deckKey(id, opaqueId = 1_200L + sequence), 19L, 23L)
			)
		}
		val overflowId = transitionId(sequence = 65L)
		assertNull(
			ledger.register(deckKey(overflowId, opaqueId = 1_265L), 19L, 23L)
		)
		assertEquals(64, ledger.retentionSnapshot().activeStateCount)
		assertEquals(64, admitted.distinct().size)
	}

	@Test
	fun unresolvedAdmissionOverflowSummarizesResponsibilityWithoutCrashing() {
		val ledger = ReaderTransitionReleaseLedger()
		(1L..64L).forEach { sequence ->
			assertTrue(
				ledger.register(
					deckKey(transitionId(sequence = sequence), opaqueId = 2_000L + sequence)
				)
			)
		}
		val retained = (65L..96L).map { sequence ->
			deckKey(transitionId(sequence = sequence), opaqueId = 2_000L + sequence).also { key ->
				assertTrue(ledger.retainUnresolvedAdmission(key, cleanupKey = null))
			}
		}
		val overflowId = transitionId(sequence = 97L)
		val overflow = deckKey(overflowId, opaqueId = 2_097L)
		val clock = ControllableTransitionClock()
		val commands = mutableListOf<ReaderTransitionCommand>()
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = TestPorts(
				onIssue = { command, _ -> commands += command },
				clockPort = clock
			),
			mode = ReaderTransitionMode.Active,
			journal = journal(overflowId),
			releaseLedger = ledger
		)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(overflowId, overflow))

		val cleanup = assertNotNull(coordinatorJournal(coordinator).releaseOnlyCleanup)
		assertNull(coordinatorJournal(coordinator).active)
		assertNull(ledger.stateOf(overflow))
		assertTrue(retained.all { ledger.stateOf(it) == ReaderTransitionResourceState.Owned })
		assertEquals(97, ledger.retentionSnapshot().activeStateCount)
		assertEquals(ReaderReleaseLedgerCleanupStatus.Open, ledger.cleanupStatus(cleanup.key))
		assertTrue(
			commands.none { it is ReaderTransitionCommand.ReleaseResource },
			"Overflow summary must not fabricate an exact physical release command"
		)
		assertEquals(1, clock.activeRegistrationCount)

		clock.fireOnlyActive()

		assertEquals(
			ReaderReleaseLedgerCleanupStatus.TerminalFailure,
			ledger.cleanupStatus(cleanup.key)
		)
		assertEquals(
			ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
			coordinatorJournal(coordinator).releaseOnlyCleanup?.deadlineStatus
		)
	}

	@Test
	fun adoptedTerminalCapacityBlocksNewReleaseWithoutEvictingExactIdentity() {
		val ledger = ReaderTransitionReleaseLedger()
		lateinit var first: ReaderImportedLegacyResourceRegistration
		repeat(32) { index ->
			val value = index.toLong() + 1L
			val physical = ReaderLegacyPhysicalIdentity(
				ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(1_300L + value)),
				ReaderLegacyInventorySource.Deck,
				ReaderLegacySourceLocalOpaqueToken(1_400L + value)
			)
			val imported = assertNotNull(
				ledger.importLegacy(
					physical,
					ReaderTransitionResourceOwnerId.AdoptedPredecessor(
						ReaderAdoptedPredecessorSeedId.fromValidatedImport(1_500L + value)
					),
					ReaderTransitionResourceKind.Deck,
					23L
				)
			)
			if (index == 0) first = imported
			val command = assertNotNull(
				ledger.requestRelease(
					ReaderResourceReleaseIssuer.Session(19L, 23L),
					imported.registration
				)
			)
			assertTrue(
				ledger.confirmLegacyReleased(
					physical,
					ReaderTransitionFact.ResourceReleased(command.identity)
				)
			)
		}
		val overflowPhysical = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(19L, ReaderLegacyFreezeToken(1_600L)),
			ReaderLegacyInventorySource.Deck,
			ReaderLegacySourceLocalOpaqueToken(1_601L)
		)
		val overflow = assertNotNull(
			ledger.importLegacy(
				overflowPhysical,
				ReaderTransitionResourceOwnerId.AdoptedPredecessor(
					ReaderAdoptedPredecessorSeedId.fromValidatedImport(1_602L)
				),
				ReaderTransitionResourceKind.Deck,
				23L
			)
		)
		assertNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Session(19L, 23L),
				overflow.registration
			)
		)
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(first.registration.key))
		assertTrue(
			first === ledger.importLegacy(
				first.physicalIdentity,
				assertIs<ReaderTransitionResourceOwnerId.AdoptedPredecessor>(
					first.registration.key.ownerId
				),
				ReaderTransitionResourceKind.Deck,
				23L
			)
		)
	}

	@Test
	fun processCloseRetainsBoundedFailureEvidenceAndLateConfirmationWins() {
		val ledger = ReaderTransitionReleaseLedger()
		val cleanup = cleanupKey()
		val commands = (1L..32L).map { sequence ->
			val id = transitionId(sequence = sequence)
			val registration = assertNotNull(
				ledger.register(deckKey(id, opaqueId = 900L + sequence), 19L, 23L)
			)
			val command = assertNotNull(
				ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
			)
			assertTrue(
				ledger.recordAmbiguousFailure(
					ReaderTransitionFact.ReleaseCommandThrew(
						null,
						command.identity,
						ReaderReleaseCommandAmbiguityReason.ThrowAfterPhysicalEffectMayHaveOccurred
					)
				)
			)
			command
		}
		assertEquals(32, ledger.transferOutstandingAttemptsToCleanup(cleanup))
		assertEquals(32, ledger.markProcessClosed(cleanup))
		assertEquals(32, ledger.failureFence().tombstones.size)
		val blockedId = transitionId(sequence = 33L)
		val blockedRegistration = assertNotNull(
			ledger.register(deckKey(blockedId, opaqueId = 999L), 19L, 23L)
		)
		assertNull(
			ledger.requestRelease(
				ReaderResourceReleaseIssuer.Transition(blockedId),
				blockedRegistration
			)
		)
		assertEquals(1, ledger.transferOutstandingAttemptsToCleanup(cleanup))
		assertEquals(ReaderReleaseLedgerCleanupStatus.Open, ledger.cleanupStatus(cleanup))
		assertEquals(1, ledger.markProcessClosed(cleanup))
		assertEquals(
			ReaderTransitionResourceState.ProcessClosedUnreleased,
			ledger.stateOf(blockedRegistration.key)
		)
		val late = ReaderTransitionFact.ResourceReleased(commands.last().identity)
		assertTrue(ledger.confirmReleased(late))
		assertEquals(
			ReaderTransitionResourceState.Released,
			ledger.stateOf(commands.last().key)
		)
		assertFalse(ledger.confirmReleased(late))
		assertEquals(32, ledger.failureFence().tombstones.size)
	}

	@Test
	fun retirementFenceFailingEqualityUsesOnlySanitizedProjection() {
		val ledger = ReaderTransitionReleaseLedger()
		val projection = ledger.retirementFence().sanitizedProjection()
		val expected = projection.copy(hasContiguousReleasedPrefix = !projection.hasContiguousReleasedPrefix)
		val failure = assertFailsWith<AssertionError> {
			assertEquals(expected, projection, "bounded retirement categories")
		}
		val message = failure.message.orEmpty()
		assertTrue(message.contains("bounded retirement categories"))
		assertFalse(message.contains("ReaderResourceRetirementOrder"))
	}

	@Test
	fun retirementFenceRenderingAndFailureProjectionContainNoRawIdentity() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId()
		val registration = assertNotNull(
			ledger.register(deckKey(id, opaqueId = 337L), 19L, 23L)
		)
		val command = assertNotNull(
			ledger.requestRelease(ReaderResourceReleaseIssuer.Transition(id), registration)
		)
		assertTrue(ledger.confirmReleased(ReaderTransitionFact.ResourceReleased(command.identity)))
		val fence = ledger.retirementFence()

		assertEquals("ReaderResourceRetirementFenceSnapshot(<redacted>)", fence.toString())
		assertTrue(fence.confirms(registration.retirementOrder))
		assertEquals(0, fence.sanitizedProjection().outOfOrderReleasedCount)
		assertFalse(fence.toString().contains(registration.retirementOrder.sequence.toString()))
	}

	private fun coordinatorJournal(
		coordinator: ReaderResumableTransitionCoordinator
	): ReaderTransitionJournal = coordinator.javaClass.getDeclaredField("journal").run {
		isAccessible = true
		get(coordinator) as ReaderTransitionJournal
	}

	private fun cleanupKey() = ReaderReleaseOnlyCleanupKey(
		readerSessionGeneration = 19L,
		coordinatorEpoch = 23L,
		cleanupId = ReaderReleaseOnlyCleanupId(1L),
		generation = ReaderReleaseOnlyCleanupGeneration(1L)
	)

	private class ControllableTransitionClock(
		private val cancelThrows: Boolean = false
	) : ReaderTransitionClock {
		private data class Scheduled(
			val action: () -> Unit,
			var cancelled: Boolean = false
		)

		private val scheduled = mutableListOf<Scheduled>()
		var cancelCount = 0
			private set
		override fun nowMillis(): Long = 1_000L
		override fun schedule(
			atMillis: Long,
			action: () -> Unit
		): ReaderTransitionClockRegistration {
			val entry = Scheduled(action)
			scheduled += entry
			return ReaderTransitionClockRegistration {
				cancelCount += 1
				if (cancelThrows) error("private cancel detail")
				entry.cancelled = true
			}
		}

		val activeRegistrationCount: Int
			get() = scheduled.count { !it.cancelled }
		val scheduleCount: Int
			get() = scheduled.size

		fun fireOnlyActive() {
			val entry = scheduled.single { !it.cancelled }
			entry.cancelled = true
			entry.action()
		}
	}

	private class TestPorts(
		private val onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit,
		private val factAcceptance: (ReaderTransitionFact) -> Boolean = { true },
		private val onImportedIssue: ((
			ReaderImportedLegacyReleaseDispatch,
			(ReaderTransitionFact) -> Unit
		) -> Unit)? = null,
		private val clockPort: ReaderTransitionClock = object : ReaderTransitionClock {
			override fun nowMillis(): Long = 1_000L
			override fun schedule(
				atMillis: Long,
				action: () -> Unit
			): ReaderTransitionClockRegistration = ReaderTransitionClockRegistration {}
		}
	) : ReaderResumableTransitionPorts {
		override val clock: ReaderTransitionClock
			get() = clockPort

		override fun acceptsFact(fact: ReaderTransitionFact): Boolean = factAcceptance(fact)

		override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
			onIssue(command, onFact)
		}

		override fun issueImportedLegacyRelease(
			dispatch: ReaderImportedLegacyReleaseDispatch,
			onFact: (ReaderTransitionFact) -> Unit
		) {
			val imported = onImportedIssue
			if (imported == null) {
				super.issueImportedLegacyRelease(dispatch, onFact)
			} else {
				imported(dispatch, onFact)
			}
		}
	}

	private fun journal(id: ReaderTransitionId): ReaderTransitionJournal =
		readerAndroidHostTestJournal(
			committed = readerAndroidHostTestNeutralInitial(
				readerSessionGeneration = id.readerSessionGeneration,
				coordinatorEpoch = id.coordinatorEpoch
			),
			lastTransitionSequence = id.sequence,
			lastIssuedTransitionIdentity = id.parentIdentity(),
			active = ReaderActiveTransition(
				id = id,
				phase = ReaderTransitionLivenessTable.phase(
					id = id,
					kind = ReaderTransitionPhaseKind.AwaitingPrerequisites,
					retainedOwner = ReaderPresentationFrameOwner.Neutral
				)
			)
		)

	private fun transitionId(
		operation: ReaderTransitionOperation = ReaderTransitionOperation.CoverToPageEntry,
		sequence: Long = 1L
	): ReaderTransitionId {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "synthetic",
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 5L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic", 7L),
			rasterGeneration = 11L,
			textureGeneration = 13L,
			preparationGeneration = 17L
		)
		return ReaderTransitionId(
			readerSessionGeneration = 19L,
			coordinatorEpoch = 23L,
			sequence = sequence,
			operation = operation,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding),
			parent = if (sequence == 1L) null else paige.navic.reader.ReaderTransitionParentIdentity(
				19L,
				23L,
				sequence - 1L
			)
		)
	}

	private fun releaseFact(
		id: ReaderTransitionId,
		key: ReaderTransitionResourceKey
	) = ReaderTransitionFact.ResourceReleased(
		ReaderReleaseCommandIdentity(
			ReaderPhysicalReleaseAttemptId.fromLedger(key.opaqueId),
			ReaderTransitionResourceRegistration(
				key,
				ReaderResourceRetirementOrder(
					id.readerSessionGeneration,
					id.coordinatorEpoch,
					id.sequence
				)
			)
		)
	)

	private fun deckKey(id: ReaderTransitionId, opaqueId: Long) = ReaderTransitionResourceKey(
		transitionId = id,
		kind = ReaderTransitionResourceKind.Deck,
		opaqueId = opaqueId
	)
}
