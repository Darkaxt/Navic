package paige.navic.ui.screens.reader

import android.os.Looper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import paige.navic.reader.ReaderActiveTransition
import paige.navic.reader.ReaderCancelIntent
import paige.navic.reader.ReaderCommittedPresentation
import paige.navic.reader.ReaderCommittedTransition
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderCoverEntryIntent
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderExternalRelocationIntent
import paige.navic.reader.ReaderExternalRelocationSource
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnIntent
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationControllerReducer
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus
import paige.navic.reader.ReaderSaturatingCallbackCount
import paige.navic.reader.ReaderSemanticExecutableRequest
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticPortContractViolationReason
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionCommandRejectionReason
import paige.navic.reader.ReaderTransitionCommandStage
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFactKind
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionGestureId
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionLivenessTable
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.journalAwaitingSettlement
import paige.navic.reader.matchingSettlementFact
import paige.navic.reader.parentIdentity
import paige.navic.reader.publicationIdentity
import paige.navic.reader.withReadyNativePresentationFixture

@RunWith(RobolectricTestRunner::class)
class ReaderResumableTransitionCoordinatorTest {
	@Test
	fun callbackBeforeCommandReturnIsQueuedAfterRegistration() {
		val trace = mutableListOf<String>()
		val fixture = coordinatorFixture(
			onIssue = { command, onFact ->
				assertTrue(command is ReaderTransitionCommand.ReserveDeck)
				trace += "issue-deck"
				trace += "enqueue-callback"
				onFact(ReaderTransitionFact.DeckReserved(command.transitionId, deckKey(command.transitionId)))
			},
			onObservation = { observation ->
				when {
					observation.kind == ReaderTransitionCoordinatorObservationKind.TransitionRegistered ->
						trace += "register"
					observation.kind == ReaderTransitionCoordinatorObservationKind.FactProcessed &&
						observation.factKind == ReaderTransitionFactKind.DeckReserved ->
						trace += "admit-callback"
				}
			}
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertEquals(1, fixture.coordinator.snapshot().activeTransitionsRegistered)
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		assertEquals(
			listOf("register", "issue-deck", "enqueue-callback", "admit-callback"),
			trace
		)
	}

	@Test
	fun semanticCallbackBeforeCommandReturnUsesRegisteredExactTransition() {
		val trace = mutableListOf<String>()
		val predecessor = binding("semantic-predecessor")
		val predecessorId = transitionId(
			predecessor,
			ReaderTransitionOperation.ShellCoverCommit
		)
		val predecessorOwner = shellOwner(predecessor)
		val predecessorKey = ReaderTransitionResourceKey(
			predecessorId,
			ReaderTransitionResourceKind.FrameHandoff,
			41L
		)
		val successor = predecessor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				predecessor.foliateSessionId,
				2L
			)
		)
		val clock = RecordingClock()
		val ports = RecordingPorts(
			clock,
			onIssue = { command, onFact ->
				if (command is ReaderTransitionCommand.RequestSemanticSynchronization) {
					trace += "issue-semantic"
					trace += "enqueue-callback"
					onFact(ReaderTransitionFact.FoliateDestinationCommitted(null, successor))
				}
			},
			ownerAndInputPublication = object : ReaderOwnerAndInputPublicationPort {
				override fun publish(command: ReaderTransitionCommand.CommitOwnerAndInputLease) =
					error("This fixture cannot publish a successor")
				override fun publish(command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease):
					paige.navic.reader.ReaderOwnerAndInputPublicationResult {
					trace += "publish-retained"
					return paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied(
						command.transitionId,
						paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Retained(
							command.retainedResource
						),
						command.retainedOwner,
						command.retainedBinding,
						command.requestedLease,
						command.publicationIdentity
					).also { trace += "return-applied" }
				}
			}
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(
				committed = ReaderCommittedTransition(
					predecessorId,
					predecessorOwner,
					predecessor,
					predecessorKey,
					ReaderTransitionResourceRegistration(
						predecessorKey,
						ReaderResourceRetirementOrder(
							predecessorId.readerSessionGeneration,
							predecessorId.coordinatorEpoch,
							1L
						)
					)
				)
			),
			onObservation = { observation ->
				when {
					observation.kind == ReaderTransitionCoordinatorObservationKind.TransitionRegistered ->
						trace += "register"
					observation.kind == ReaderTransitionCoordinatorObservationKind.FactProcessed &&
						observation.factKind == ReaderTransitionFactKind.FoliateDestinationCommitted ->
						trace += "admit-callback"
				}
			}
		)

		coordinator.enqueue(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
						ReaderExternalRelocationSource.Toc,
						ReaderSemanticRequestHandle(1L)
					)
			)
		)

		assertEquals(
			listOf(
				"register",
				"publish-retained",
				"return-applied",
				"issue-semantic",
				"enqueue-callback",
				"admit-callback"
			),
			trace
		)
		assertEquals(1, coordinator.snapshot().activeTransitionsRegistered)
		assertEquals(1, coordinator.snapshot().maxAdvanceDepth)
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, coordinator.snapshot().activeOperation)
	}

	@Test
	fun semanticSynchronousCallbackThenAcceptedQueuesOneReceiptNonReentrantly() {
		val destination = binding("opaque-sync")
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				onReceipt(semanticDestinationReceipt(command, destination, 1L))
				ReaderSemanticCommandResult.Accepted
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		assertEquals(ReaderTransitionOperation.BootstrapNativePage, fixture.coordinator.snapshot().activeOperation)
	}

	@Test
	fun semanticAcceptedThenAsynchronousCallbackQueuesOneReceipt() {
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		lateinit var command: ReaderTransitionCommand.RequestSemanticSynchronization
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { issued, _, onReceipt ->
				command = issued
				callback = onReceipt
				ReaderSemanticCommandResult.Accepted
			}
		)
		fixture.coordinator.enqueue(bootstrapSemanticIntent())
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})

		callback(semanticDestinationReceipt(command, binding("opaque-async"), 1L))

		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
	}

	@Test
	fun semanticRejectedWithoutCallbackQueuesDistinctSynchronizationRejection() {
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { _, _, _ ->
				ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		val rejection = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.CommandRejected>()
			.single()
		assertEquals(ReaderTransitionCommandStage.SemanticSynchronization, rejection.stage)
		assertEquals(ReaderTransitionCommandRejectionReason.SemanticExecutionRejected, rejection.reason)
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		assertFalse(fixture.coordinator.snapshot().releaseOnlySink)
		assertEquals(ReaderTransitionOutcomeKind.Failed, fixture.coordinator.snapshot().lastOutcome)
	}

	@Test
	fun semanticThrowWithoutCallbackQueuesDistinctSynchronizationRejection() {
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { _, _, _ -> error("private semantic detail") }
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		val rejection = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.CommandRejected>()
			.single()
		assertEquals(ReaderTransitionCommandStage.SemanticSynchronization, rejection.stage)
		assertEquals(ReaderTransitionCommandRejectionReason.CommandThrew, rejection.reason)
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		assertFalse(fixture.coordinator.snapshot().releaseOnlySink)
		assertEquals(ReaderTransitionOutcomeKind.Failed, fixture.coordinator.snapshot().lastOutcome)
	}

	@Test
	fun semanticCallbackThenRejectedPreservesReceiptBeforeFatalViolation() {
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				onReceipt(semanticDestinationReceipt(command, binding("opaque-rejected"), 1L))
				ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.CallbackThenRejected,
			ReaderSaturatingCallbackCount.One
		)
		assertReceiptImmediatelyPrecedesViolation(fixture)
		assertTrue(fixture.coordinator.snapshot().releaseOnlySemanticAuthorityRetained)
	}

	@Test
	fun semanticCallbackThenThrowPreservesReceiptBeforeFatalViolation() {
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				onReceipt(semanticDestinationReceipt(command, binding("opaque-thrown"), 1L))
				error("private semantic detail")
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.CallbackThenThrew,
			ReaderSaturatingCallbackCount.One
		)
		assertReceiptImmediatelyPrecedesViolation(fixture)
		assertTrue(fixture.coordinator.snapshot().releaseOnlySemanticAuthorityRetained)
	}

	@Test
	fun semanticRejectedThenLateCallbackPreservesReceiptAndClosesReleaseOnly() {
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		lateinit var command: ReaderTransitionCommand.RequestSemanticSynchronization
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { issued, _, onReceipt ->
				command = issued
				callback = onReceipt
				ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
		)
		fixture.coordinator.enqueue(bootstrapSemanticIntent())
		assertFalse(fixture.coordinator.snapshot().releaseOnlySink)

		callback(semanticDestinationReceipt(command, binding("opaque-late-rejected"), 1L))

		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.RejectedThenLateCallback,
			ReaderSaturatingCallbackCount.One
		)
		assertReceiptImmediatelyPrecedesViolation(fixture)
	}

	@Test
	fun semanticThrowThenLateCallbackPreservesReceiptAndClosesReleaseOnly() {
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		lateinit var command: ReaderTransitionCommand.RequestSemanticSynchronization
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { issued, _, onReceipt ->
				command = issued
				callback = onReceipt
				error("private semantic detail")
			}
		)
		fixture.coordinator.enqueue(bootstrapSemanticIntent())
		assertFalse(fixture.coordinator.snapshot().releaseOnlySink)

		callback(semanticDestinationReceipt(command, binding("opaque-late-thrown"), 1L))

		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.RejectedThenLateCallback,
			ReaderSaturatingCallbackCount.One
		)
		assertReceiptImmediatelyPrecedesViolation(fixture)
	}

	@Test
	fun semanticDuplicateEqualCallbacksQueueLatestOnceThenFatalViolation() {
		val destination = binding("opaque-equal")
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				val receipt = semanticDestinationReceipt(command, destination, 1L)
				onReceipt(receipt)
				onReceipt(receipt)
				ReaderSemanticCommandResult.Accepted
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallback,
			ReaderSaturatingCallbackCount.Two
		)
	}

	@Test
	fun semanticDifferingSecondCallbackRetainsOnlyLatestAuthority() {
		val first = binding("opaque-differing").copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("opaque-differing", 2L)
		)
		val latest = first.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("opaque-differing", 3L)
		)
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				onReceipt(semanticDestinationReceipt(command, first, 1L))
				onReceipt(semanticDestinationReceipt(command, latest, 2L))
				ReaderSemanticCommandResult.Accepted
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		val allocation = fixture.ports.commands
			.filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>()
			.single()
		assertEquals(3L, allocation.binding.destinationCommitIdentity?.commitSequence)
		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallback,
			ReaderSaturatingCallbackCount.Two
		)
	}

	@Test
	fun semanticThreeOrMoreCallbacksSaturateWithoutThrowingOrGrowingReceiptHistory() {
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				repeat(5) { index ->
					onReceipt(
						semanticDestinationReceipt(
							command,
							binding("opaque-many").copy(
								destinationCommitIdentity = ReaderDestinationCommitIdentity(
									"opaque-many",
									index.toLong() + 2L
								)
							),
							index.toLong() + 1L
						)
					)
				}
				ReaderSemanticCommandResult.Accepted
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallback,
			ReaderSaturatingCallbackCount.ThreeOrMore
		)
	}

	@Test
	fun semanticThreeCallbacksThenRejectedUseCombinedSaturatedViolation() {
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				repeat(3) { index ->
					onReceipt(
						semanticDestinationReceipt(
							command,
							binding("opaque-many-rejected").copy(
								destinationCommitIdentity = ReaderDestinationCommitIdentity(
									"opaque-many-rejected",
									index.toLong() + 2L
								)
							),
							index.toLong() + 1L
						)
					)
				}
				ReaderSemanticCommandResult.RejectedBeforeMutation(
					ReaderTransitionFailureReason.PortRejected
				)
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallbackThenRejected,
			ReaderSaturatingCallbackCount.ThreeOrMore
		)
		assertReceiptImmediatelyPrecedesViolation(fixture)
	}

	@Test
	fun semanticDuplicateCallbacksThenThrowUseCombinedViolation() {
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, onReceipt ->
				onReceipt(semanticDestinationReceipt(command, binding("opaque-duplicate-throw"), 1L))
				onReceipt(semanticDestinationReceipt(command, binding("opaque-duplicate-throw"), 2L))
				error("private semantic detail")
			}
		)

		fixture.coordinator.enqueue(bootstrapSemanticIntent())

		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallbackThenThrew,
			ReaderSaturatingCallbackCount.Two
		)
		assertReceiptImmediatelyPrecedesViolation(fixture)
	}

	@Test
	fun semanticCallbackAfterAcceptedInvocationCompletionIsBoundedFatalDuplicate() {
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		lateinit var command: ReaderTransitionCommand.RequestSemanticSynchronization
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { issued, _, onReceipt ->
				command = issued
				callback = onReceipt
				ReaderSemanticCommandResult.Accepted
			}
		)
		fixture.coordinator.enqueue(bootstrapSemanticIntent())
		val receipt = semanticDestinationReceipt(command, binding("opaque-post-terminal"), 1L)
		callback(receipt)

		callback(receipt)

		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallback,
			ReaderSaturatingCallbackCount.Two
		)
		assertEquals(1, fixture.ports.acceptedFacts.count {
			it is ReaderTransitionFact.FoliateDestinationCommitted
		})
	}

	@Test
	fun delayedSemanticCallbackAfterCoordinatorTerminalStateIsSafeAndCannotReopen() {
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		lateinit var command: ReaderTransitionCommand.RequestSemanticSynchronization
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { issued, _, onReceipt ->
				command = issued
				callback = onReceipt
				ReaderSemanticCommandResult.Accepted
			}
		)
		fixture.coordinator.enqueue(bootstrapSemanticIntent())
		fixture.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink)

		callback(semanticDestinationReceipt(command, binding("opaque-after-close"), 1L))

		assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
		assertEquals(null, fixture.coordinator.snapshot().activeOperation)
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
	}

	@Test
	fun delayedSemanticCallbackAfterTransitionSupersessionCannotMutateSuccessor() {
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		lateinit var command: ReaderTransitionCommand.RequestSemanticSynchronization
		val predecessor = binding("opaque-predecessor")
		val owner = shellOwner(predecessor)
		val ports = RecordingPorts(
			clock = RecordingClock(),
			ownerAndInputPublication = object : ReaderOwnerAndInputPublicationPort {
				override fun publish(command: ReaderTransitionCommand.CommitOwnerAndInputLease) =
					error("Successor publication is outside this fixture")

				override fun publish(
					command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease
				) = paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied(
					command.transitionId,
					paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Retained(
						command.retainedResource
					),
					command.retainedOwner,
					command.retainedBinding,
					command.requestedLease,
					command.publicationIdentity
				)
			},
			semanticCommand = ReaderSemanticCommandPort { issued, _, onReceipt ->
				command = issued
				callback = onReceipt
				ReaderSemanticCommandResult.Accepted
			},
			onIssue = { _, _ -> }
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(
				committed = readerAndroidHostTestAdoptedInitial(
					owner,
					predecessor,
					17L,
					19L
				)
			)
		)
		coordinator.enqueue(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
					ReaderExternalRelocationSource.Toc,
					ReaderSemanticRequestHandle(1L)
				)
			)
		)
		coordinator.enqueue(ReaderTransitionFact.FoliateDestinationCommitted(null, predecessor))
		val registrationsAfterSupersession = coordinator.snapshot().activeTransitionsRegistered
		assertEquals(2, registrationsAfterSupersession)

		callback(
			semanticDestinationReceipt(
				command,
				predecessor.copy(
					destinationCommitIdentity = ReaderDestinationCommitIdentity(
						"opaque-predecessor",
						2L
					)
				),
				1L
			)
		)

		assertEquals(registrationsAfterSupersession, coordinator.snapshot().activeTransitionsRegistered)
		val violation = ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.SemanticPortContractViolated>()
			.single()
		assertEquals(
			ReaderSemanticPortContractViolationReason.CallbackAfterTerminalDisposition,
			violation.reason
		)
		assertEquals(ReaderSaturatingCallbackCount.One, violation.callbackCount)
		assertTrue(coordinator.snapshot().releaseOnlySink)
		assertEquals(null, coordinator.snapshot().activeOperation)
		assertEquals(1, coordinator.snapshot().maxAdvanceDepth)
	}

	@Test
	fun realExecutorDuplicateCallbacksReachCoordinatorAndRetainLatestAuthority() {
		val first = binding("opaque-real-duplicate").copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("opaque-real-duplicate", 2L)
		)
		val latest = first.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("opaque-real-duplicate", 3L)
		)
		val fixture = realSemanticCoordinatorFixture { origin, _, callback ->
			callback(semanticDestinationReceipt(origin, first, 1L))
			callback(semanticDestinationReceipt(origin, latest, 2L))
			ReaderSemanticExecutableResult.Accepted
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallback,
			ReaderSaturatingCallbackCount.Two
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorAsynchronousDifferingDuplicateReplacesAppliedFirstAuthority() {
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture { suppliedOrigin, _, suppliedCallback ->
			origin = suppliedOrigin
			callback = suppliedCallback
			ReaderSemanticExecutableResult.Accepted
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		val first = binding("opaque-real-async").copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("opaque-real-async", 2L)
		)
		val latest = first.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("opaque-real-async", 3L)
		)
		callback(semanticDestinationReceipt(origin, first, 1L))

		callback(semanticDestinationReceipt(origin, latest, 2L))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallback,
			ReaderSaturatingCallbackCount.Two
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorMutationStartedRejectionWithoutCallbackIsFatal() {
		val fixture = realSemanticCoordinatorFixture { _, mutationStart, _ ->
			mutationStart.mutationStarted()
			ReaderSemanticExecutableResult.Rejected
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		val violation = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.SemanticPortContractViolated>()
			.single()
		assertEquals(
			ReaderSemanticPortContractViolationReason.RejectedAfterMutationStarted,
			violation.reason
		)
		assertEquals(ReaderSaturatingCallbackCount.Zero, violation.callbackCount)
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
		assertEquals(0, fixture.executor.activeSlotCount)
	}

	@Test
	fun realExecutorThrowThenLateCallbackRetainsLateAuthority() {
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture {
				suppliedOrigin, mutationStart, suppliedCallback ->
			origin = suppliedOrigin
			callback = suppliedCallback
			mutationStart.mutationStarted()
			error("private semantic detail")
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		val mutationThrowViolation = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.SemanticPortContractViolated>()
			.single()
		assertEquals(
			ReaderSemanticPortContractViolationReason.ThrowAfterMutationStarted,
			mutationThrowViolation.reason
		)
		assertEquals(ReaderSaturatingCallbackCount.Zero, mutationThrowViolation.callbackCount)
		assertEquals(0, fixture.executor.activeSlotCount)
		val latest = binding("opaque-real-throw-late")

		callback(semanticDestinationReceipt(origin, latest, 1L))

		assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorCallbackAfterPublicationCloseIsFatalAndRetainsAuthority() {
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture { suppliedOrigin, _, suppliedCallback ->
			origin = suppliedOrigin
			callback = suppliedCallback
			ReaderSemanticExecutableResult.Accepted
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		assertEquals(1, fixture.executor.activeSlotCount)
		fixture.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		assertEquals(0, fixture.executor.activeSlotCount)
		val latest = binding("opaque-real-after-close")

		callback(semanticDestinationReceipt(origin, latest, 1L))

		val violation = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.SemanticPortContractViolated>()
			.single()
		assertEquals(
			ReaderSemanticPortContractViolationReason.CallbackAfterTerminalDisposition,
			violation.reason
		)
		assertEquals(ReaderSaturatingCallbackCount.One, violation.callbackCount)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorAcceptedSilenceThenNewInvocationMakesOldCallbackFatal() {
		lateinit var firstOrigin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var firstCallback: (ReaderPresentationEventReceipt) -> Unit
		var secondInvocationStarted = false
		val fixture = realSemanticCoordinatorFixture { suppliedOrigin, _, suppliedCallback ->
			firstOrigin = suppliedOrigin
			firstCallback = suppliedCallback
			ReaderSemanticExecutableResult.Accepted
		}
		val secondHandle = fixture.registry.register { _, _, _ ->
			secondInvocationStarted = true
			ReaderSemanticExecutableResult.Accepted
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		assertEquals(1, fixture.executor.activeSlotCount)
		fixture.coordinator.enqueue(bootstrapSemanticIntent(secondHandle))
		assertTrue(secondInvocationStarted)
		assertEquals(1, fixture.executor.activeSlotCount)
		val latest = binding("opaque-real-superseded")

		firstCallback(semanticDestinationReceipt(firstOrigin, latest, 1L))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.CallbackAfterTerminalDisposition,
			ReaderSaturatingCallbackCount.One
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
		assertEquals(0, fixture.executor.activeSlotCount)
	}

	@Test
	fun realExecutorAcceptedSilenceThenDeadlineMakesLateCallbackFatal() {
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture { suppliedOrigin, _, suppliedCallback ->
			origin = suppliedOrigin
			callback = suppliedCallback
			ReaderSemanticExecutableResult.Accepted
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		fixture.ports.clock.fireLast()
		val latest = binding("opaque-real-after-deadline")

		callback(semanticDestinationReceipt(origin, latest, 1L))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.CallbackAfterTerminalDisposition,
			ReaderSaturatingCallbackCount.One
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorMutationRejectionThenLateCallbackRetainsExactAuthority() {
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture {
				suppliedOrigin, mutationStart, suppliedCallback ->
			origin = suppliedOrigin
			callback = suppliedCallback
			mutationStart.mutationStarted()
			ReaderSemanticExecutableResult.Rejected
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		val latest = binding("opaque-real-reject-late")

		callback(semanticDestinationReceipt(origin, latest, 1L))

		val violation = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.SemanticPortContractViolated>()
			.single()
		assertEquals(
			ReaderSemanticPortContractViolationReason.RejectedAfterMutationStarted,
			violation.reason
		)
		assertEquals(ReaderSaturatingCallbackCount.Zero, violation.callbackCount)
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorCallbackThenMutationRejectionIsFatalWithExactAuthority() {
		val latest = binding("opaque-real-callback-reject")
		val fixture = realSemanticCoordinatorFixture { origin, mutationStart, callback ->
			mutationStart.mutationStarted()
			callback(semanticDestinationReceipt(origin, latest, 1L))
			ReaderSemanticExecutableResult.Rejected
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.CallbackThenRejected,
			ReaderSaturatingCallbackCount.One
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorCallbackThenThrowIsFatalWithExactAuthority() {
		val latest = binding("opaque-real-callback-throw")
		val fixture = realSemanticCoordinatorFixture { origin, mutationStart, callback ->
			mutationStart.mutationStarted()
			callback(semanticDestinationReceipt(origin, latest, 1L))
			error("private semantic detail")
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.CallbackThenThrew,
			ReaderSaturatingCallbackCount.One
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorExecutableRejectionBeforeMutationRemainsOrdinaryCommandRejection() {
		val fixture = realSemanticCoordinatorFixture { _, _, _ ->
			ReaderSemanticExecutableResult.Rejected
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		val rejection = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.CommandRejected>()
			.single()
		assertEquals(ReaderTransitionCommandStage.SemanticSynchronization, rejection.stage)
		assertEquals(
			ReaderTransitionCommandRejectionReason.SemanticExecutionRejected,
			rejection.reason
		)
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		assertFalse(fixture.coordinator.snapshot().releaseOnlySink)
	}

	@Test
	fun realExecutorExecutableThrowBeforeMutationRemainsOrdinaryCommandRejection() {
		val fixture = realSemanticCoordinatorFixture { _, _, _ ->
			error("private semantic detail")
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		val rejection = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.CommandRejected>()
			.single()
		assertEquals(ReaderTransitionCommandStage.SemanticSynchronization, rejection.stage)
		assertEquals(ReaderTransitionCommandRejectionReason.CommandThrew, rejection.reason)
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		assertFalse(fixture.coordinator.snapshot().releaseOnlySink)
	}

	@Test
	fun realExecutorPrevalidationRejectionRemainsOrdinaryCommandRejection() {
		val fixture = realSemanticCoordinatorFixture { _, _, _ ->
			error("unregistered request must not execute")
		}

		fixture.coordinator.enqueue(
			bootstrapSemanticIntent(ReaderSemanticRequestHandle(fixture.handle.value + 1L))
		)

		val rejection = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.CommandRejected>()
			.single()
		assertEquals(ReaderTransitionCommandStage.SemanticSynchronization, rejection.stage)
		assertEquals(
			ReaderTransitionCommandRejectionReason.SemanticExecutionRejected,
			rejection.reason
		)
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		assertFalse(fixture.coordinator.snapshot().releaseOnlySink)
	}

	@Test
	fun semanticRejectionThenRetryMakesOldCallbackFatalToNewInvocation() {
		lateinit var oldCommand: ReaderTransitionCommand.RequestSemanticSynchronization
		lateinit var oldCallback: (ReaderPresentationEventReceipt) -> Unit
		var invocationCount = 0
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, callback ->
				invocationCount += 1
				if (invocationCount == 1) {
					oldCommand = command
					oldCallback = callback
					ReaderSemanticCommandResult.RejectedBeforeMutation(
						ReaderTransitionFailureReason.PortRejected
					)
				} else {
					ReaderSemanticCommandResult.Accepted
				}
			}
		)
		fixture.coordinator.enqueue(bootstrapSemanticIntent())
		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))
		assertEquals(2, invocationCount)
		val latest = binding("opaque-rejected-retry-late")

		oldCallback(semanticDestinationReceipt(oldCommand, latest, 1L))

		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.RejectedThenLateCallback,
			ReaderSaturatingCallbackCount.One
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun semanticThrowThenRetryMakesOldCallbackFatalToNewInvocation() {
		lateinit var oldCommand: ReaderTransitionCommand.RequestSemanticSynchronization
		lateinit var oldCallback: (ReaderPresentationEventReceipt) -> Unit
		var invocationCount = 0
		val fixture = semanticCoordinatorFixture(
			ReaderSemanticCommandPort { command, _, callback ->
				invocationCount += 1
				if (invocationCount == 1) {
					oldCommand = command
					oldCallback = callback
					error("private semantic detail")
				}
				ReaderSemanticCommandResult.Accepted
			}
		)
		fixture.coordinator.enqueue(bootstrapSemanticIntent())
		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))
		assertEquals(2, invocationCount)
		val latest = binding("opaque-thrown-retry-late")

		oldCallback(semanticDestinationReceipt(oldCommand, latest, 1L))

		assertSemanticViolation(
			fixture,
			ReaderSemanticPortContractViolationReason.RejectedThenLateCallback,
			ReaderSaturatingCallbackCount.One
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorAcceptedSilenceRetiresEveryTerminalSlotAndPreservesLateFatalCallback() {
		val callbacks = mutableListOf<Pair<
			paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand,
			(ReaderPresentationEventReceipt) -> Unit
		>>()
		val request: ReaderSemanticExecutableRequest = { origin, _, callback ->
			callbacks += origin to callback
			ReaderSemanticExecutableResult.Accepted
		}
		val fixture = realSemanticCoordinatorFixture(request)

		repeat(8) { cycle ->
			val handle = if (cycle == 0) fixture.handle else fixture.registry.register(request)
			fixture.coordinator.enqueue(bootstrapSemanticIntent(handle))
			assertEquals(1, fixture.executor.activeSlotCount)
			fixture.ports.clock.fireLast()
			assertEquals(0, fixture.executor.activeSlotCount)
		}
		val ninthHandle = fixture.registry.register(request)
		fixture.coordinator.enqueue(bootstrapSemanticIntent(ninthHandle))
		assertEquals(1, fixture.executor.activeSlotCount)
		val (retiredOrigin, retiredCallback) = callbacks.first()
		val latest = binding("opaque-real-retired-capacity")

		retiredCallback(semanticDestinationReceipt(retiredOrigin, latest, 1L))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.CallbackAfterTerminalDisposition,
			ReaderSaturatingCallbackCount.One
		)
		assertEquals(0, fixture.executor.activeSlotCount)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorRejectionBeforeMutationThenNewInvocationMakesOldCallbackFatal() {
		lateinit var oldOrigin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var oldCallback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture { origin, _, callback ->
			oldOrigin = origin
			oldCallback = callback
			ReaderSemanticExecutableResult.Rejected
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		assertEquals(0, fixture.executor.activeSlotCount)
		val secondHandle = fixture.registry.register { _, _, _ ->
			ReaderSemanticExecutableResult.Accepted
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(secondHandle))
		assertEquals(1, fixture.executor.activeSlotCount)
		val latest = binding("opaque-real-rejected-new-invocation")

		oldCallback(semanticDestinationReceipt(oldOrigin, latest, 1L))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.RejectedThenLateCallback,
			ReaderSaturatingCallbackCount.One
		)
		assertEquals(0, fixture.executor.activeSlotCount)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorThrowBeforeMutationThenNewInvocationMakesOldCallbackFatal() {
		lateinit var oldOrigin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var oldCallback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture { origin, _, callback ->
			oldOrigin = origin
			oldCallback = callback
			error("private semantic detail")
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		assertEquals(0, fixture.executor.activeSlotCount)
		val secondHandle = fixture.registry.register { _, _, _ ->
			ReaderSemanticExecutableResult.Accepted
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(secondHandle))
		assertEquals(1, fixture.executor.activeSlotCount)
		val latest = binding("opaque-real-thrown-new-invocation")

		oldCallback(semanticDestinationReceipt(oldOrigin, latest, 1L))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.RejectedThenLateCallback,
			ReaderSaturatingCallbackCount.One
		)
		assertEquals(0, fixture.executor.activeSlotCount)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, latest)
	}

	@Test
	fun realExecutorThreeOrMoreCallbacksSaturateOnceAndRetainLatestAuthority() {
		val destinations = (2L..5L).map { sequence ->
			binding("opaque-real-many").copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					"opaque-real-many",
					sequence
				)
			)
		}
		val fixture = realSemanticCoordinatorFixture { origin, _, callback ->
			destinations.forEachIndexed { index, destination ->
				callback(
					semanticDestinationReceipt(
						origin,
						destination,
						index.toLong() + 1L
					)
				)
			}
			ReaderSemanticExecutableResult.Accepted
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.DuplicateCallback,
			ReaderSaturatingCallbackCount.ThreeOrMore
		)
		assertEquals(
			1,
			fixture.ports.acceptedFacts.count {
				it is ReaderTransitionFact.SemanticPortContractViolated
			}
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, destinations.last())
	}

	@Test
	fun realExecutorWrongOriginBeforeRetirementCannotConsumeOrContaminateExactCallback() {
		val wrong = binding("opaque-real-wrong-before")
		val exact = binding("opaque-real-exact-before").copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"opaque-real-exact-before",
				2L
			)
		)
		val fixture = realSemanticCoordinatorFixture { origin, _, callback ->
			val wrongOrigin = origin.copy(
				slotId = paige.navic.reader.ReaderSemanticCommandSlotId(origin.slotId.value + 100L)
			)
			callback(semanticDestinationReceipt(wrongOrigin, wrong, 1L))
			callback(semanticDestinationReceipt(origin, exact, 2L))
			ReaderSemanticExecutableResult.Accepted
		}

		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

		assertEquals(0, fixture.executor.activeSlotCount)
		assertEquals(
			1,
			fixture.ports.acceptedFacts.count {
				it is ReaderTransitionFact.FoliateDestinationCommitted
			}
		)
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		val allocation = fixture.ports.commands
			.filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>()
			.single()
		assertEquals(
			exact.destinationCommitIdentity,
			allocation.binding.destinationCommitIdentity
		)
	}

	@Test
	fun realExecutorWrongOriginAfterRetirementCannotContaminateLateExactCallback() {
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		val fixture = realSemanticCoordinatorFixture { suppliedOrigin, _, suppliedCallback ->
			origin = suppliedOrigin
			callback = suppliedCallback
			ReaderSemanticExecutableResult.Accepted
		}
		fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
		fixture.ports.clock.fireLast()
		assertEquals(0, fixture.executor.activeSlotCount)
		val wrongOrigin = origin.copy(
			slotId = paige.navic.reader.ReaderSemanticCommandSlotId(origin.slotId.value + 100L)
		)
		callback(
			semanticDestinationReceipt(
				wrongOrigin,
				binding("opaque-real-wrong-after"),
				1L
			)
		)
		assertTrue(fixture.ports.acceptedFacts.none {
			it is ReaderTransitionFact.SemanticPortContractViolated
		})
		val exact = binding("opaque-real-exact-after")

		callback(semanticDestinationReceipt(origin, exact, 2L))

		assertSemanticViolation(
			fixture.semanticFixture,
			ReaderSemanticPortContractViolationReason.CallbackAfterTerminalDisposition,
			ReaderSaturatingCallbackCount.One
		)
		assertReleaseOnlySemanticAuthority(fixture.coordinator, exact)
	}

	@Test
	fun workerCallbackBeforeExecutableReturnPrecedesRejectedCompletionOnMain() {
		val worker = Executors.newSingleThreadExecutor()
		val callbackReturned = CountDownLatch(1)
		val reducerThreads = mutableListOf<Boolean>()
		lateinit var callbackFuture: java.util.concurrent.Future<*>
		try {
			val fixture = realSemanticCoordinatorFixtureWithObservation( { origin, _, callback ->
					callbackFuture = worker.submit {
						try {
							callback(
								semanticDestinationReceipt(
									origin,
									binding("opaque-worker-before-return"),
									1L
								)
							)
						} finally {
							callbackReturned.countDown()
						}
					}
					assertTrue(callbackReturned.await(2L, TimeUnit.SECONDS))
					ReaderSemanticExecutableResult.Rejected
				},
				onObservation = { reducerThreads += Looper.myLooper() == Looper.getMainLooper() }
			)

			fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
			callbackFuture.get(2L, TimeUnit.SECONDS)

			assertSemanticViolation(
				fixture.semanticFixture,
				ReaderSemanticPortContractViolationReason.CallbackThenRejected,
				ReaderSaturatingCallbackCount.One
			)
			assertTrue(reducerThreads.isNotEmpty())
			assertTrue(reducerThreads.all { it })
			assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		} finally {
			worker.shutdownNow()
			assertTrue(worker.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun workerCallbackAfterRejectedCompletionRemainsOrderedAfterCompletion() {
		val worker = Executors.newSingleThreadExecutor()
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		try {
			val fixture = realSemanticCoordinatorFixture { suppliedOrigin, _, suppliedCallback ->
				origin = suppliedOrigin
				callback = suppliedCallback
				ReaderSemanticExecutableResult.Rejected
			}
			fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))

			worker.submit {
				callback(
					semanticDestinationReceipt(
						origin,
						binding("opaque-worker-after-completion"),
						1L
					)
				)
			}.get(2L, TimeUnit.SECONDS)
			org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

			assertSemanticViolation(
				fixture.semanticFixture,
				ReaderSemanticPortContractViolationReason.RejectedThenLateCallback,
				ReaderSaturatingCallbackCount.One
			)
			assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		} finally {
			worker.shutdownNow()
			assertTrue(worker.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun delayedWorkerCallbackAfterAcceptedIsDeliveredWithoutWorkerException() {
		val worker = Executors.newSingleThreadExecutor()
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		try {
			val fixture = realSemanticCoordinatorFixture { suppliedOrigin, _, suppliedCallback ->
				origin = suppliedOrigin
				callback = suppliedCallback
				ReaderSemanticExecutableResult.Accepted
			}
			fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
			val destination = binding("opaque-worker-delayed-accepted")

			worker.submit {
				callback(semanticDestinationReceipt(origin, destination, 1L))
			}.get(2L, TimeUnit.SECONDS)
			org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

			assertEquals(
				1,
				fixture.ports.acceptedFacts.count {
					it is ReaderTransitionFact.FoliateDestinationCommitted
				}
			)
			assertTrue(fixture.ports.acceptedFacts.none {
				it is ReaderTransitionFact.SemanticPortContractViolated
			})
			assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		} finally {
			worker.shutdownNow()
			assertTrue(worker.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun concurrentWorkerCallbacksSaturateOnceAndReduceOnlyOnMain() {
		val workers = Executors.newFixedThreadPool(4)
		val start = CyclicBarrier(5)
		val reducerThreads = mutableListOf<Boolean>()
		lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var callback: (ReaderPresentationEventReceipt) -> Unit
		try {
			val fixture = realSemanticCoordinatorFixtureWithObservation( { suppliedOrigin, _, suppliedCallback ->
					origin = suppliedOrigin
					callback = suppliedCallback
					ReaderSemanticExecutableResult.Accepted
				},
				onObservation = { reducerThreads += Looper.myLooper() == Looper.getMainLooper() }
			)
			fixture.coordinator.enqueue(bootstrapSemanticIntent(fixture.handle))
			val destination = binding("opaque-worker-concurrent-duplicates")
			val futures = (1L..4L).map { sequence ->
				workers.submit {
					start.await(2L, TimeUnit.SECONDS)
					callback(semanticDestinationReceipt(origin, destination, sequence))
				}
			}
			start.await(2L, TimeUnit.SECONDS)
			futures.forEach { it.get(2L, TimeUnit.SECONDS) }
			org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

			assertSemanticViolation(
				fixture.semanticFixture,
				ReaderSemanticPortContractViolationReason.DuplicateCallback,
				ReaderSaturatingCallbackCount.ThreeOrMore
			)
			assertEquals(
				1,
				fixture.ports.acceptedFacts.count {
					it is ReaderTransitionFact.SemanticPortContractViolated
				}
			)
			assertTrue(reducerThreads.isNotEmpty())
			assertTrue(reducerThreads.all { it })
			assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		} finally {
			workers.shutdownNow()
			assertTrue(workers.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun untaggedSettlementReceiptNeverBorrowsTheActiveTransitionIdentity() {
		val model = journalAwaitingSettlement()
		val receipt = settlementReceipt(model, eventSequence = 1L)
		val coordinator = settlementCoordinator(model)
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(coordinator::enqueue, coordinator::enqueue)

		gateway.observePresentationReceipt(receipt)

		val snapshot = coordinator.snapshot()
		assertEquals(0, snapshot.consumedSettlementCount)
		assertEquals(1, snapshot.factClassifications[ReaderTransitionFactClassification.Untagged])
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, snapshot.activeOperation)
		registration.close()
	}

	@Test
	fun wrongExactSettlementReceiptThroughGatewayIsClassifiedStale() {
		val model = journalAwaitingSettlement()
		val wrongId = model.id.copy(
			sequence = model.id.sequence + 1L,
			parent = model.id.parentIdentity()
		)
		val coordinator = settlementCoordinator(model)
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(coordinator::enqueue, coordinator::enqueue)

		gateway.observePresentationReceipt(
			settlementReceipt(model, eventSequence = 1L, originatingTransitionId = wrongId)
		)

		val snapshot = coordinator.snapshot()
		assertEquals(0, snapshot.consumedSettlementCount)
		assertEquals(1, snapshot.factClassifications[ReaderTransitionFactClassification.StaleTransition])
		assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement, snapshot.activeOperation)
		registration.close()
	}

	@Test
	fun supersededExactSettlementReceiptThroughGatewayCannotBecomeAnUnsolicitedDestination() {
		val model = journalAwaitingSettlement()
		val replacement = model.successor.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				model.successor.foliateSessionId,
				3L
			)
		)
		val coordinator = settlementCoordinator(model)
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(coordinator::enqueue, coordinator::enqueue)
		gateway.observePresentationReceipt(destinationReceipt(model, replacement, eventSequence = 1L))
		val registrationsAfterReplacement = coordinator.snapshot().activeTransitionsRegistered

		gateway.observePresentationReceipt(
			settlementReceipt(model, eventSequence = 2L, originatingTransitionId = model.id)
		)

		val snapshot = coordinator.snapshot()
		assertEquals(registrationsAfterReplacement, snapshot.activeTransitionsRegistered)
		assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, snapshot.activeOperation)
		assertEquals(1, snapshot.factClassifications[ReaderTransitionFactClassification.StaleTransition])
		assertEquals(0, snapshot.consumedSettlementCount)
		registration.close()
	}

	@Test
	fun newerSequenceDuplicateExactSettlementReceiptIsClassifiedStale() {
		val model = journalAwaitingSettlement()
		val coordinator = settlementCoordinator(model)
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(coordinator::enqueue, coordinator::enqueue)

		gateway.observePresentationReceipt(
			settlementReceipt(model, eventSequence = 1L, originatingTransitionId = model.id)
		)
		gateway.observePresentationReceipt(
			settlementReceipt(model, eventSequence = 2L, originatingTransitionId = model.id)
		)

		val snapshot = coordinator.snapshot()
		assertEquals(1, snapshot.consumedSettlementCount)
		assertEquals(1, snapshot.factClassifications[ReaderTransitionFactClassification.CurrentTransition])
		assertEquals(1, snapshot.factClassifications[ReaderTransitionFactClassification.StaleTransition])
		registration.close()
	}

	@Test
	fun offMainSemanticReceiptIsRejectedBeforeOneShotConsumption() {
		val model = journalAwaitingSettlement()
		val receipt = settlementReceipt(
			model,
			eventSequence = 1L,
			originatingTransitionId = model.id
		)
		val coordinator = settlementCoordinator(model)
		var failure: Throwable? = null

		Thread {
			failure = runCatching { coordinator.enqueue(receipt) }.exceptionOrNull()
		}.apply {
			start()
			join()
		}
		assertTrue(failure is IllegalStateException)
		assertEquals(0, coordinator.snapshot().consumedSettlementCount)
		assertTrue(coordinator.snapshot().factClassifications.isEmpty())

		coordinator.enqueue(receipt)
		coordinator.enqueue(receipt)

		assertEquals(1, coordinator.snapshot().consumedSettlementCount)
		assertEquals(
			1,
			coordinator.snapshot().factClassifications[ReaderTransitionFactClassification.CurrentTransition]
		)
	}

	@Test
	fun publicationReplacementDrainsOldCoordinatorAndFencesLateResourcesExactlyOnce() {
		val fixture = coordinatorFixture(mode = ReaderTransitionMode.Active)
		val owned = ReaderTransitionResourceKey(
			fixture.id,
			ReaderTransitionResourceKind.Raster,
			43L
		)
		val late = owned.copy(opaqueId = 47L)
		fixture.coordinator.enqueue(ReaderTransitionFact.ResourceObserved(fixture.id, owned))

		fixture.coordinator.enqueue(ReaderTransitionFact.PublicationReplaced(null))
		fixture.coordinator.enqueue(ReaderTransitionFact.PublicationReplaced(null))
		fixture.coordinator.enqueue(ReaderTransitionFact.ResourceObserved(fixture.id, late))
		fixture.coordinator.enqueue(ReaderTransitionFact.ResourceObserved(fixture.id, late))

		val releases = fixture.ports.commands
			.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			.map { it.key }
		assertEquals(3, releases.size)
		assertTrue(owned in releases, "Replacement must release the observed active resource")
		assertTrue(late in releases, "Release-only must release the exact late resource once")
		assertEquals(
			1,
			releases.count { it.kind == ReaderTransitionResourceKind.FrameHandoff },
			"Replacement must release the truthful committed shell predecessor"
		)
		assertEquals(1, fixture.ports.commands.count { it is ReaderTransitionCommand.CancelOwnedWork })
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
		assertEquals(0, fixture.coordinator.snapshot().scheduledCallbackCount)
	}

	@Test
	fun activatedCloseDispatchesCleanupWhenTimerCancellationRejectsOrThrows() {
		assertActivatedTerminalCleanupSurvivesTimerCancellationFailure { _ ->
			ReaderTransitionFact.PublicationClosed(null)
		}
	}

	@Test
	fun activatedReplacementDispatchesCleanupWhenTimerCancellationRejectsOrThrows() {
		assertActivatedTerminalCleanupSurvivesTimerCancellationFailure { _ ->
			ReaderTransitionFact.PublicationReplaced(null)
		}
	}

	@Test
	fun shadowModeIssuesNoMutatingCommands() {
		val fixture = coordinatorFixture(mode = ReaderTransitionMode.Shadow)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertTrue(fixture.ports.commands.isEmpty())
		assertEquals(1, fixture.coordinator.snapshot().shadowPredictions.size)
		assertEquals(
			setOf(ReaderTransitionCommandKind.ReserveDeck),
			fixture.coordinator.snapshot().shadowPredictions.single().commandKinds
		)
	}

	@Test
	fun fifoIngressKeepsSynchronousCallbacksBehindTheCurrentCommand() {
		val processed = mutableListOf<ReaderTransitionFactKind>()
		val fixture = coordinatorFixture(
			onIssue = { command, onFact ->
				if (command is ReaderTransitionCommand.ReserveDeck) {
					val key = deckKey(command.transitionId)
					onFact(ReaderTransitionFact.DeckReserved(command.transitionId, key))
					onFact(ReaderTransitionFact.DeckOwned(command.transitionId, key))
					onFact(ReaderTransitionFact.DeckPrepared(command.transitionId, key))
				}
			},
			onObservation = { observation ->
				if (observation.kind == ReaderTransitionCoordinatorObservationKind.FactProcessed) {
					processed += assertNotNull(observation.factKind)
				}
			}
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertEquals(
			listOf(
				ReaderTransitionFactKind.RasterProven,
				ReaderTransitionFactKind.DeckReserved,
				ReaderTransitionFactKind.DeckOwned,
				ReaderTransitionFactKind.DeckPrepared
			),
			processed
		)
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
	}

	@Test
	fun staleFactsAreClassifiedDeterministicallyWithoutChangingTheJournal() {
		val fixture = coordinatorFixture()
		val staleId = fixture.id.copy(
			sequence = fixture.id.sequence + 1L,
			parent = fixture.id.parentIdentity()
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(staleId))

		val snapshot = fixture.coordinator.snapshot()
		assertEquals(
			1,
			snapshot.factClassifications[ReaderTransitionFactClassification.StaleTransition]
		)
		assertEquals(ReaderTransitionPhaseKind.AwaitingProof, snapshot.activePhase)
		assertTrue(fixture.ports.commands.isEmpty())
	}

	@Test
	fun synchronousPortCallbacksNeverIncreaseAdvancementDepthAboveOne() {
		val fixture = coordinatorFixture(
			onIssue = { command, onFact ->
				val transitionId = requireNotNull(command.transitionId)
				val key = deckKey(transitionId)
				onFact(ReaderTransitionFact.DeckOwned(transitionId, key))
			}
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		assertEquals(0, fixture.coordinator.snapshot().mailboxSize)
	}

	@Test
	fun synchronousCombinedCommitAcknowledgementIsQueuedNonReentrantly() {
		val publicationPort = ReaderOwnerAndInputPublicationPort::class.java
		val publishMethods = publicationPort.methods.filter { it.name == "publish" }
		assertEquals(2, publishMethods.size)
		assertTrue(publishMethods.all { method ->
			method.returnType.name == "paige.navic.reader.ReaderOwnerAndInputPublicationResult"
		})
		assertTrue(
			ReaderResumableTransitionCoordinator::class.java.declaredMethods.any {
				it.name.contains("publication", ignoreCase = true)
			},
			"dispatcher must convert synchronous publication results into queued facts"
		)
	}

	@Test
	fun oneScheduledCallbackPerActivePhaseAndCancellationOnReplacementAndTerminal() {
		val fixture = coordinatorFixture()

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		assertEquals(1, fixture.clock.scheduleCount)
		assertEquals(1, fixture.clock.activeRegistrationCount)

		fixture.coordinator.enqueue(ReaderTransitionFact.PublicationReplaced(null))
		assertEquals(1, fixture.clock.scheduleCount)
		assertEquals(1, fixture.clock.cancelCount)
		assertEquals(0, fixture.clock.activeRegistrationCount)
		assertEquals(0, fixture.coordinator.snapshot().scheduledCallbackCount)

		fixture.clock.fireLast()
		assertEquals(1, fixture.clock.cancelCount)
		assertEquals(0, fixture.clock.activeRegistrationCount)
		assertEquals(0, fixture.coordinator.snapshot().scheduledCallbackCount)
	}

	@Test
	fun initialMaterialOperationExpiresAfterTenSecondsWithoutProgress() {
		val fixture = coordinatorFixture()

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))

		assertEquals(listOf(11_000L), fixture.clock.scheduledAtMillis)
	}

	@Test
	fun matchingProgressMovesOnlyTheNoProgressExpiry() {
		val fixture = coordinatorFixture()
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))

		fixture.clock.advanceTo(6_000L)
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))

		assertEquals(listOf(11_000L, 16_000L), fixture.clock.scheduledAtMillis)
		assertEquals(1, fixture.clock.cancelCount)
		assertEquals(1, fixture.clock.activeRegistrationCount)
	}

	@Test
	fun activatedHardDeadlineProgressRetainsTheOriginalTimer() {
		val timer = RejectingProgressTimer(
			callbackBeforeReject = false,
			permitsMatchingProgressRearm = false
		)
		val model = journalAwaitingSettlement()
		val ports = RecordingPorts(
			clock = RecordingClock(),
			task6FactOnlyTimer = timer,
			onIssue = { _, _ -> }
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = model.journal,
			activationState = { ReaderSessionActivationState.Activated }
		)
		coordinator.enqueue(ReaderTransitionFact.HostAvailable(model.id))

		coordinator.enqueue(matchingSettlementFact(model))

		val snapshot = coordinator.snapshot()
		assertEquals(1, timer.bindCount)
		assertEquals(0, timer.progressCount)
		assertEquals(0, timer.cancelCount)
		assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement, snapshot.activeOperation)
		assertEquals(1, snapshot.consumedSettlementCount)
		assertEquals(null, snapshot.lastOutcome)
	}

	@Test
	fun failedSuccessorTimerStillDispatchesExactSupersededCleanup() {
		listOf(
			TestTimerBindOutcome.Rejected,
			TestTimerBindOutcome.Throws,
			TestTimerBindOutcome.SynchronousExpiry
		).forEach { bindOutcome ->
			val timer = ScriptedBindingTimer(bindOutcome)
			val model = activatedNeutralTransitionFixture(timer)
			val fixture = model.fixture
			val successor = model.binding.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					model.binding.foliateSessionId,
					requireNotNull(model.binding.destinationCommitIdentity).commitSequence + 1L
				)
			)

			fixture.coordinator.enqueue(
				ReaderTransitionFact.FoliateDestinationCommitted(null, successor)
			)

			assertEquals(
				1,
				fixture.ports.commands.filterIsInstance<ReaderTransitionCommand.CancelOwnedWork>()
					.count { it.transitionId == fixture.id }
			)
			assertEquals(
				listOf(model.owned),
				fixture.ports.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
					.map { it.key }
			)
			assertTrue(
				fixture.ports.commands.none { it is ReaderTransitionCommand.AllocateMaterialBinding }
			)
			assertEquals(ReaderTransitionOutcomeKind.Failed, fixture.coordinator.snapshot().lastOutcome)
		}
	}

	@Test
	fun sameTransitionStaleDeckCleanupSurvivesTimerFailure() {
		listOf(
			TestTimerProgressOutcome.Rejected,
			TestTimerProgressOutcome.Throws,
			TestTimerProgressOutcome.SynchronousExpiry
		).forEach { progressOutcome ->
			val timer = SequencedFactOnlyTimer(
				bindScript = listOf(TestTimerBindOutcome.Accepted),
				progressScript = listOf(
					TestTimerProgressOutcome.Accepted,
					progressOutcome
				)
			)
			val fixture = coordinatorFixture(
				task6FactOnlyTimer = timer,
				activationState = { ReaderSessionActivationState.Activated }
			)
			val current = deckKey(fixture.id)
			val stale = current.copy(opaqueId = current.opaqueId + 1L)

			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))
			fixture.coordinator.enqueue(ReaderTransitionFact.DeckPrepared(fixture.id, stale))
			fixture.coordinator.enqueue(ReaderTransitionFact.DeckOwned(fixture.id, current))

			assertEquals(
				1,
				fixture.ports.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
					.count { it.key == stale }
			)
			assertEquals(ReaderTransitionOutcomeKind.Failed, fixture.coordinator.snapshot().lastOutcome)
		}
	}

	@Test
	fun acceptedCancellationClearsEveryAliasOfOneExactRegistration() {
		val timer = AliasingFactOnlyTimer()
		val model = activatedNeutralTransitionFixture(timer)
		val fixture = model.fixture
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		val successor = model.binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				model.binding.foliateSessionId,
				requireNotNull(model.binding.destinationCommitIdentity).commitSequence + 1L
			)
		)

		fixture.coordinator.enqueue(
			ReaderTransitionFact.FoliateDestinationCommitted(null, successor)
		)

		assertEquals(2, timer.bindCount)
		assertEquals(1, timer.cancelCount)
		assertEquals(0, timer.trackedRegistrationCount)
		assertEquals(0, fixture.coordinator.snapshot().scheduledCallbackCount)

		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))

		assertEquals(3, timer.bindCount)
		assertEquals(1, timer.cancelCount)
		assertEquals(1, timer.trackedRegistrationCount)
		assertEquals(1, fixture.coordinator.snapshot().scheduledCallbackCount)
		assertNotNull(fixture.coordinator.snapshot().activeOperation)
	}

	@Test
	fun exactExpiryClearsOnlyOneOfTwoSameTransitionRegistrations() {
		val timer = SequencedFactOnlyTimer(
			bindScript = listOf(
				TestTimerBindOutcome.Accepted,
				TestTimerBindOutcome.WrongPreviousOwner,
				TestTimerBindOutcome.Accepted
			),
			cancelScript = List(6) { TestTimerCancelOutcome.Rejected }
		)
		val model = activatedNeutralTransitionFixture(timer)
		val fixture = model.fixture
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		val successor = model.binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				model.binding.foliateSessionId,
				requireNotNull(model.binding.destinationCommitIdentity).commitSequence + 1L
			)
		)

		fixture.coordinator.enqueue(
			ReaderTransitionFact.FoliateDestinationCommitted(null, successor)
		)
		assertEquals(2, timer.bindCount)
		assertEquals(2, timer.trackedRegistrationCount)
		assertEquals(2, fixture.coordinator.snapshot().scheduledCallbackCount)

		timer.fireRegistration(ReaderTask6FactOnlyTimerRegistrationId(2L))
		assertEquals(1, timer.trackedRegistrationCount)
		assertEquals(1, fixture.coordinator.snapshot().scheduledCallbackCount)

		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))
		assertEquals(2, timer.bindCount)
		assertEquals(1, timer.trackedRegistrationCount)
		assertEquals(1, fixture.coordinator.snapshot().scheduledCallbackCount)

		timer.fireRegistration(ReaderTask6FactOnlyTimerRegistrationId(1L))
		assertEquals(0, timer.trackedRegistrationCount)
		assertEquals(0, fixture.coordinator.snapshot().scheduledCallbackCount)

		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))
		assertEquals(3, timer.bindCount)
		assertEquals(1, timer.trackedRegistrationCount)
		assertEquals(1, fixture.coordinator.snapshot().scheduledCallbackCount)
		assertNotNull(fixture.coordinator.snapshot().activeOperation)
	}

	@Test
	fun wrongOwnerRollbackCancellationFailureRetainsOwnershipUntilRetryCanBind() {
		listOf(
			TestTimerCancelOutcome.Rejected,
			TestTimerCancelOutcome.Throws
		).forEach { firstCancellation ->
			val timer = SequencedFactOnlyTimer(
				bindScript = listOf(
					TestTimerBindOutcome.WrongOwner,
					TestTimerBindOutcome.Accepted
				),
				cancelScript = listOf(
					firstCancellation,
					TestTimerCancelOutcome.Accepted
				)
			)
			val fixture = activatedNeutralTransitionFixture(timer).fixture

			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
			assertEquals(null, fixture.coordinator.snapshot().activeOperation)
			assertEquals(0, timer.trackedRegistrationCount)

			fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))

			assertEquals(2, timer.bindCount)
			assertEquals(2, timer.cancelCount)
			assertEquals(1, timer.trackedRegistrationCount)
			assertNotNull(fixture.coordinator.snapshot().activeOperation)
		}
	}

	@Test
	fun failedOldAndRollbackCancellationRetainBothUntilRetryWithoutAccumulation() {
		val timer = SequencedFactOnlyTimer(
			bindScript = listOf(
				TestTimerBindOutcome.Accepted,
				TestTimerBindOutcome.Accepted,
				TestTimerBindOutcome.Accepted
			),
			cancelScript = listOf(
				TestTimerCancelOutcome.Rejected,
				TestTimerCancelOutcome.Rejected,
				TestTimerCancelOutcome.Accepted,
				TestTimerCancelOutcome.Accepted
			)
		)
		val model = activatedNeutralTransitionFixture(timer)
		val fixture = model.fixture
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		val successor = model.binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				model.binding.foliateSessionId,
				requireNotNull(model.binding.destinationCommitIdentity).commitSequence + 1L
			)
		)

		fixture.coordinator.enqueue(
			ReaderTransitionFact.FoliateDestinationCommitted(null, successor)
		)
		assertEquals(0, timer.trackedRegistrationCount)

		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))

		assertEquals(3, timer.bindCount)
		assertEquals(4, timer.cancelCount)
		assertEquals(1, timer.trackedRegistrationCount)
		assertNotNull(fixture.coordinator.snapshot().activeOperation)
	}

	@Test
	fun exactExpiryBeforeRearmRejectionClearsOwnershipAndRetryBindsOnce() {
		val timer = SequencedFactOnlyTimer(
			bindScript = listOf(
				TestTimerBindOutcome.Accepted,
				TestTimerBindOutcome.Accepted
			),
			cancelScript = listOf(TestTimerCancelOutcome.Rejected),
			callbackBeforeProgressReject = true
		)
		val model = activatedNeutralTransitionFixture(timer)
		val fixture = model.fixture
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		fixture.coordinator.enqueue(
			ReaderTransitionFact.MaterialBindingAllocated(
				fixture.id,
				paige.navic.reader.ReaderMaterialGenerationAllocation(
					fixture.id,
					model.binding,
					requireNotNull(model.binding.preparationGeneration),
					requireNotNull(model.binding.rasterGeneration),
					requireNotNull(model.binding.textureGeneration)
				)
			)
		)
		assertEquals(null, fixture.coordinator.snapshot().activeOperation)
		assertEquals(0, timer.trackedRegistrationCount)

		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(null))

		assertEquals(2, timer.bindCount)
		assertEquals(0, timer.cancelCount)
		assertEquals(1, timer.trackedRegistrationCount)
		assertNotNull(fixture.coordinator.snapshot().activeOperation)
	}

	@Test
	fun rejectedOrThrowingTimerRearmBlocksCommandEmittingRasterProgression() {
		listOf(
			TestTimerProgressOutcome.Rejected,
			TestTimerProgressOutcome.Throws
		).forEach { progressOutcome ->
			val observedFacts = mutableListOf<ReaderTransitionFactKind>()
			val timer = ScriptedBindingTimer(
				outcome = TestTimerBindOutcome.Accepted,
				progressOutcome = progressOutcome
			)
			val fixture = coordinatorFixture(
				task6FactOnlyTimer = timer,
				activationState = { ReaderSessionActivationState.Activated },
				onObservation = { observation ->
					observation.factKind?.let(observedFacts::add)
				}
			)
			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))

			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

			val snapshot = fixture.coordinator.snapshot()
			assertTrue(fixture.ports.commands.none { it is ReaderTransitionCommand.ReserveDeck })
			assertEquals(null, snapshot.activeOperation)
			assertEquals(ReaderTransitionOutcomeKind.Failed, snapshot.lastOutcome)
			assertEquals(0, snapshot.mailboxSize)
			assertEquals(1, observedFacts.count { it == ReaderTransitionFactKind.CommandRejected })
			assertEquals(0, observedFacts.count { it == ReaderTransitionFactKind.DeadlineExpired })
		}
	}

	@Test
	fun synchronousExpiryBeforeTimerBindReturnBlocksCommandEmittingRasterProgression() {
		val observedFacts = mutableListOf<ReaderTransitionFactKind>()
		val timer = ScriptedBindingTimer(TestTimerBindOutcome.SynchronousExpiry)
		val fixture = coordinatorFixture(
			task6FactOnlyTimer = timer,
			activationState = { ReaderSessionActivationState.Activated },
			onObservation = { observation ->
				observation.factKind?.let(observedFacts::add)
			}
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		val snapshot = fixture.coordinator.snapshot()
		assertTrue(fixture.ports.commands.none { it is ReaderTransitionCommand.ReserveDeck })
		assertEquals(null, snapshot.activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Failed, snapshot.lastOutcome)
		assertEquals(0, snapshot.mailboxSize)
		assertEquals(
			listOf(
				ReaderTransitionFactKind.RasterProven,
				ReaderTransitionFactKind.DeadlineExpired
			),
			observedFacts
		)
	}

	@Test
	fun rejectedTask6ProgressRearmClearsRegistrationAndFailsClosed() {
		listOf(false, true).forEach { callbackBeforeReject ->
			val timer = RejectingProgressTimer(callbackBeforeReject)
			val observedFacts = mutableListOf<ReaderTransitionFactKind>()
			val fixture = coordinatorFixture(
				task6FactOnlyTimer = timer,
				activationState = { ReaderSessionActivationState.Activated },
				onObservation = { observation ->
					observation.factKind?.let(observedFacts::add)
				}
			)
			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
			assertEquals(1, timer.bindCount)

			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))

			val snapshot = fixture.coordinator.snapshot()
			assertEquals(1, timer.progressCount)
			assertEquals(if (callbackBeforeReject) 0 else 1, timer.cancelCount)
			assertEquals(!callbackBeforeReject, timer.cancelledBoundRegistration)
			assertEquals(null, snapshot.activeOperation)
			assertEquals(ReaderTransitionOutcomeKind.Failed, snapshot.lastOutcome)
			assertEquals(0, snapshot.mailboxSize)
			assertEquals(
				if (callbackBeforeReject) 1 else 0,
				observedFacts.count { it == ReaderTransitionFactKind.DeadlineExpired }
			)
			assertEquals(1, observedFacts.count { it == ReaderTransitionFactKind.CommandRejected })
		}
	}

	@Test
	fun phaseChangesDoNotExtendTheOriginalThirtySecondHardExpiry() {
		val fixture = coordinatorFixture()
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		listOf(9_000L, 17_000L, 25_000L).forEach { nowMillis ->
			fixture.clock.advanceTo(nowMillis)
			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		}
		assertEquals(31_000L, fixture.clock.scheduledAtMillis.last())

		fixture.clock.advanceTo(26_000L)
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertEquals(ReaderTransitionPhaseKind.AwaitingProof, fixture.coordinator.snapshot().activePhase)
		assertEquals(31_000L, fixture.clock.scheduledAtMillis.last())
		assertEquals(1, fixture.clock.activeRegistrationCount)
	}

	@Test
	fun lateCancelledCallbackForOldPhaseOfSameTransitionIsIgnored() {
		val fixture = coordinatorFixture()
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		fixture.clock.advanceTo(2_000L)
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))
		assertEquals(ReaderTransitionPhaseKind.AwaitingProof, fixture.coordinator.snapshot().activePhase)
		assertEquals(1, fixture.clock.activeRegistrationCount)

		fixture.clock.fireIgnoringCancellation(0)

		assertEquals(ReaderTransitionPhaseKind.AwaitingProof, fixture.coordinator.snapshot().activePhase)
		assertEquals(1, fixture.coordinator.snapshot().scheduledCallbackCount)
		assertEquals(1, fixture.clock.activeRegistrationCount)
	}

	@Test
	fun operationsWithoutNoProgressTimeoutKeepTheirHardExpiry() {
		val fixture = coordinatorFixture(operation = ReaderTransitionOperation.ShellCoverCommit)
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		fixture.clock.advanceTo(1_500L)
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))

		assertEquals(listOf(3_000L), fixture.clock.scheduledAtMillis)
		assertEquals(1, fixture.clock.activeRegistrationCount)
	}

	@Test
	fun synchronousDeadlineCallbackReentersThroughMailboxWithoutRecursion() {
		val fixture = coordinatorFixture(clock = RecordingClock(synchronous = true))

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))

		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		assertEquals(0, fixture.coordinator.snapshot().mailboxSize)
		assertEquals(null, fixture.coordinator.snapshot().activePhase)
		assertEquals(1, fixture.clock.cancelCount)
	}

	@Test
	fun activeModeIssuesTypedCommandsAndCallbacksReenterOnlyAsFacts() {
		val callbackKinds = mutableListOf<ReaderTransitionFactKind>()
		val fixture = coordinatorFixture(
			mode = ReaderTransitionMode.Active,
			onIssue = { command, onFact ->
				assertTrue(command is ReaderTransitionCommand.ReserveDeck)
				onFact(ReaderTransitionFact.DeckReserved(command.transitionId, deckKey(command.transitionId)))
			},
			onObservation = { observation ->
				if (
					observation.kind == ReaderTransitionCoordinatorObservationKind.FactProcessed &&
					observation.factKind == ReaderTransitionFactKind.DeckReserved
				) callbackKinds += observation.factKind
			}
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertTrue(fixture.ports.commands.single() is ReaderTransitionCommand.ReserveDeck)
		assertEquals(listOf(ReaderTransitionFactKind.DeckReserved), callbackKinds)
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
	}

	@Test
	fun boundedContentFreeShadowDiagnosticsExcludeBindingsAndPayloads() {
		val secret = "private-publication-href-cfi-user-session-payload"
		val fixture = coordinatorFixture(bindingValue = secret, mode = ReaderTransitionMode.Shadow)
		val staleId = fixture.id.copy(
			sequence = fixture.id.sequence + 1L,
			parent = fixture.id.parentIdentity()
		)

		repeat(ReaderTransitionCoordinatorSnapshot.MaxShadowPredictions + 9) {
			fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(staleId))
		}

		val snapshot = fixture.coordinator.snapshot()
		assertEquals(ReaderTransitionCoordinatorSnapshot.MaxShadowPredictions, snapshot.shadowPredictions.size)
		assertFalse(snapshot.toString().contains(secret))
		assertTrue(snapshot.shadowPredictions.all { prediction ->
			prediction.factKind == ReaderTransitionFactKind.RasterProgress &&
				prediction.classification == ReaderTransitionFactClassification.StaleTransition
		})
	}

	@Test
	fun navigateToViewerRouteRegistersJumpBeforeLegacyMoveToPageAndRejectsDeniedInput() {
		val locator = ReaderLocator(href = "synthetic-move-to-page.xhtml", pageIndex = 4)
		val action = ReaderViewerAction.NavigateTo(locator)
		val admittedController = ReaderController().withReadyNativePresentationFixture()
		val facts = mutableListOf<ReaderTransitionFact>()
		val trace = mutableListOf<String>()
		var registeredIntents = 0
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(
			enqueue = { fact ->
				facts += fact
				trace += "gateway"
			}
		)

		val admittedStep = gateway.dispatchViewerActionBeforeLegacy(
			state = admittedController.state,
			action = action,
			gestureId = ReaderTransitionGestureId(103L),
			onTransitionIntentRegistered = { registeredIntents += 1 }
		) {
			trace += "legacy"
			ReaderPresentationControllerReducer.onViewerAction(admittedController, action)
		}

		val expectedFacts: List<ReaderTransitionFact> = listOf(
			ReaderTransitionFact.Intent(
				null,
				ReaderExternalRelocationIntent(
						ReaderExternalRelocationSource.Jump,
						ReaderSemanticRequestHandle(1L)
					)
			)
		)
		assertEquals(expectedFacts, facts)
		assertEquals(listOf("gateway", "legacy"), trace)
		assertEquals(1, registeredIntents)
		assertEquals(
			locator,
			assertIs<ReaderEngineCommand.NavigateTo>(admittedStep.engineCommands.single()).locator
		)

		facts.clear()
		trace.clear()
		val rejectedController = ReaderController()
		val rejectedStep = gateway.dispatchViewerActionBeforeLegacy(
			state = rejectedController.state,
			action = action,
			gestureId = ReaderTransitionGestureId(107L),
			onTransitionIntentRegistered = { registeredIntents += 1 }
		) {
			trace += "legacy"
			ReaderPresentationControllerReducer.onViewerAction(rejectedController, action)
		}

		assertTrue(facts.isEmpty())
		assertEquals(listOf("legacy"), trace)
		assertEquals(1, registeredIntents)
		assertEquals(rejectedController.state, rejectedStep.controller.state)
		assertTrue(rejectedStep.engineCommands.isEmpty())
		assertTrue(rejectedStep.presentationEffects.isEmpty())
		registration.close()
	}

	@Test
	fun executableDispatchSeamRoutesEveryTask5IntentBeforeLegacyDispatch() {
		val facts = buildList {
			add(
				"page" to ReaderTransitionFact.Intent(
					null,
					ReaderPageTurnIntent(
						ReaderPageTurnDirection.Next,
						ReaderTransitionGestureId(101L),
						ReaderSemanticRequestHandle(3L)
					)
				)
			)
			add("cover" to ReaderTransitionFact.Intent(null, ReaderCoverEntryIntent(ReaderSemanticRequestHandle(5L))))
			ReaderExternalRelocationSource.entries.forEach { source ->
				add(
					source.name to ReaderTransitionFact.Intent(
						null,
						ReaderExternalRelocationIntent(source, ReaderSemanticRequestHandle(7L))
					)
				)
			}
			add("Retry" to ReaderTransitionFact.Retry(null))
			add("cancel" to ReaderTransitionFact.Intent(null, ReaderCancelIntent))
		}

		facts.forEach { (name, expectedFact) ->
			val trace = mutableListOf<String>()
			val received = mutableListOf<ReaderTransitionFact>()
			val gateway = ReaderTransitionGateway()
			val registration = gateway.attachShadow(
				enqueue = { fact ->
					received += fact
					trace += "gateway"
				}
			)

			val result = gateway.dispatchBeforeLegacy(expectedFact) {
				trace += "legacy"
				name
			}

			assertEquals(name, result)
			assertEquals(listOf(expectedFact), received, name)
			assertEquals(listOf("gateway", "legacy"), trace, name)
			registration.close()
		}
	}

	@Test
	fun executableReceiptSeamRoutesReceiptBeforeEffectRetention() {
		val model = journalAwaitingSettlement()
		val receipt = settlementReceipt(model, eventSequence = 1L)
		val trace = mutableListOf<String>()
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(
			enqueue = {},
			enqueueReceipt = { trace += "receipt" }
		)

		gateway.observeReceiptBeforeEffects(receipt) {
			trace += "effects"
		}

		assertEquals(listOf("receipt", "effects"), trace)
		registration.close()
	}

	@Test
	fun activatedGatewayRejectsLegacyEventsBeforeHostConsequenceDispatch() {
		val facts = mutableListOf<ReaderTransitionFact>()
		val gateway = ReaderTransitionGateway()
		gateway.attachActivated(
			enqueue = facts::add,
			registerSemanticRequest = { ReaderSemanticRequestHandle(1L) }
		)
		var legacyConsequenceCount = 0

		val accepted: Any? = gateway.observeLegacyPresentationEvent(ReaderPresentationEvent.Retry)
		if (accepted == true) legacyConsequenceCount += 1

		assertEquals(false, accepted)
		assertTrue(facts.isEmpty())
		assertEquals(0, legacyConsequenceCount)
		val host = source(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		)
		assertEquals(
			2,
			host.split("if (currentShadowTransitionGateway.observeLegacyPresentationEvent(event))").size - 1
		)
	}

	@Test
	fun gatewayObservesOnlyShadowFactsAndDetachesExactly() {
		val received = mutableListOf<ReaderTransitionFact>()
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(received::add)

		gateway.observeLegacyPresentationEvent(ReaderPresentationEvent.Retry)
		gateway.observeLegacyPresentationEvent(
			ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost)
		)
		registration.close()
		gateway.observeLegacyPresentationEvent(ReaderPresentationEvent.Retry)

		assertEquals(ReaderTransitionGatewayMode.Shadow, gateway.mode)
		assertEquals(
			listOf(
				ReaderTransitionFact.Retry(null),
				ReaderTransitionFact.VisibilityChanged(null, false)
			),
			received
		)
	}

	@Test
	fun gatewayRoutesTypedReceiptWithoutDowngradingFoliateEvent() {
		val model = journalAwaitingSettlement()
		val settlement = matchingSettlementFact(model)
		val version = ReaderPresentationReceiptVersion(
			readerSessionGeneration = model.id.readerSessionGeneration,
			publicationIdentity = model.successor.publicationIdentity,
			eventSequence = 1L
		)
		val receipt = ReaderPresentationEventReceipt(
			event = ReaderPresentationEvent.FoliateRelocated(
				model.successor,
				settlement.acknowledgement
			),
			preVersion = version.copy(eventSequence = 0L),
			version = version,
			disposition = ReaderPresentationEventDisposition.Accepted,
			postState = ReaderPresentationState(binding = model.successor),
			effects = emptyList(),
			origin = paige.navic.reader.ReaderPresentationEventOrigin.NonSemantic
		)
		val receivedFacts = mutableListOf<ReaderTransitionFact>()
		val receivedReceipts = mutableListOf<ReaderPresentationEventReceipt>()
		val gateway = ReaderTransitionGateway()
		val registration = gateway.attachShadow(
			receivedFacts::add,
			{ typedReceipt: ReaderPresentationEventReceipt -> receivedReceipts += typedReceipt }
		)

		gateway.observeLegacyPresentationEvent(receipt.event)
		gateway.observePresentationReceipt(receipt)

		assertTrue(receivedFacts.isEmpty())
		assertEquals(listOf(receipt), receivedReceipts)
		registration.close()
		gateway.observePresentationReceipt(
			receipt.copy(version = version.copy(eventSequence = 2L))
		)
		assertEquals(listOf(receipt), receivedReceipts)
	}

	@Test
	fun sourceBoundaryKeepsLegacyAsSoleWriterAndGatewayShadowOnly() {
		val commonRoot = source("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt")
		val commonScreen = source("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
		val platform = source("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt")
		val androidHost = source(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		)
		val coordinator = source(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderResumableTransitionCoordinator.android.kt"
		)

		assertContains(commonScreen, "val shadowTransitionGateway = remember")
		assertContains(commonScreen, "shadowTransitionGateway = shadowTransitionGateway")
		assertContains(commonScreen, "val step = coordinator.onPresentationEvent(event)")
		assertContains(commonRoot, "shadowTransitionGateway: ReaderTransitionGateway")
		assertContains(commonRoot, "LocalReaderTransitionGateway provides shadowTransitionGateway")
		assertContains(platform, "class ReaderTransitionGateway")
		assertContains(platform, "ReaderTransitionGatewayMode.Shadow")
		assertContains(platform, "fun observePresentationReceipt(")
		assertContains(androidHost, "attachShadow(")
		assertContains(androidHost, "enqueue = {}")
		assertContains(androidHost, "enqueueReceipt = {}")
		assertContains(androidHost, "currentShadowTransitionGateway.observeLegacyPresentationEvent(event)")
		assertContains(androidHost, "currentOnPresentationEvent(event)")
		assertFalse(androidHost.contains("ReaderTransitionMode.Active"))
		assertContains(coordinator, "ports.issue(command, ::enqueue)")
	}

	@Test
	fun composeRoutesEveryTask5SemanticIntentThroughGatewayBeforeLegacyDispatch() {
		val commonRoot = source("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt")
		val commonScreen = source("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")

		ReaderExternalRelocationSource.entries.forEach { source ->
			assertContains(commonScreen, "ReaderExternalRelocationSource.${source.name}")
		}
		assertContains(commonScreen, "dispatchViewerTransitionBeforeLegacy(action)")
		assertContains(commonScreen, "dispatchPageTurnBoundaryBeforeLegacy(direction)")
		assertContains(commonScreen, "dispatchExternalRelocationBeforeLegacy(")
		assertContains(commonRoot, "shadowTransitionGateway.dispatchBeforeLegacy(")
		assertContains(commonRoot, "ReaderTransitionFact.Retry(null)")
		assertContains(commonRoot, "dispatchAcceptedCancel = { event, legacyDispatch ->")
		assertContains(commonRoot, "ReaderTransitionFact.Intent(null, ReaderCancelIntent)")
		assertFalse(commonScreen.contains("shadowTransitionGateway.enqueue("))
		assertFalse(commonRoot.contains("shadowTransitionGateway.enqueue("))
		val applyCoordinatorStep = commonScreen
			.substringAfter("fun applyCoordinatorStep(")
			.substringBefore("fun applyReaderBackStep(")
		assertContains(
			applyCoordinatorStep,
			"shadowTransitionGateway.observeReceiptBeforeEffects(step.presentationReceipt)"
		)
	}

	@Test
	fun resumedSpecializedFrameTargetPhaseBindsTimerAndRestoresPhysicalAuthority() {
		val binding = binding("resumed-frame-target")
		val id = transitionId(binding, ReaderTransitionOperation.CoverToPageEntry)
		val owner = shellOwner(binding)
		val basePhase = ReaderTransitionLivenessTable.phase(
			id = id,
			kind = ReaderTransitionPhaseKind.AwaitingProof,
			retainedOwner = owner,
			satisfiedProofs = setOf(paige.navic.reader.ReaderTransitionProofKind.SemanticDestination)
		)
		val specializedPhase = basePhase.copy(
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(paige.navic.reader.ReaderTransitionProofKind.FrameTargetPreparation),
				callbackSources = setOf(
					ReaderTransitionFactKind.FrameTargetPrepared,
					ReaderTransitionFactKind.FrameTargetPreparationRejected,
					ReaderTransitionFactKind.DeadlineExpired,
					ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(
					ReaderTransitionCommandStage.FrameTargetPreparation,
					ReaderTransitionCommandStage.TimerBinding
				)
			)
		)
		val journal = readerAndroidHostTestJournal(
			committed = readerAndroidHostTestAdoptedInitial(
				owner = owner,
				binding = binding,
				readerSessionGeneration = id.readerSessionGeneration,
				coordinatorEpoch = id.coordinatorEpoch
			),
			lastTransitionSequence = id.sequence,
			lastIssuedTransitionIdentity = id.parentIdentity(),
			active = ReaderActiveTransition(
				id = id,
				phase = specializedPhase,
				pendingCommandStages = setOf(ReaderTransitionCommandStage.FrameTargetPreparation)
			)
		)
		val timer = RejectingProgressTimer(callbackBeforeReject = false)
		val ports = RecordingPorts(RecordingClock(), task6FactOnlyTimer = timer) { _, _ -> }
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = journal,
			activationState = { ReaderSessionActivationState.Activated }
		)

		coordinator.enqueue(ReaderTransitionFact.RasterProgress(id))

		assertEquals(1, timer.bindCount)
		assertEquals(ReaderTransitionOperation.CoverToPageEntry, coordinator.snapshot().activeOperation)
		coordinator.enqueue(
			ReaderTransitionFact.CommandRejected(
				id,
				ReaderTransitionCommandStage.FrameTargetPreparation,
				ReaderTransitionCommandRejectionReason.FrameTargetRejected
			)
		)
		assertEquals(null, coordinator.snapshot().activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Failed, coordinator.snapshot().lastOutcome)
	}

	@Test
	fun activatedPublicationCloseBindsTimerAndClosesWithoutConstructorCrash() {
		val timer = ScriptedBindingTimer(TestTimerBindOutcome.Accepted)
		val fixture = activatedPublicationCloseFixture(timer)

		fixture.coordinator.enqueue(ReaderTransitionFact.HostAvailable(fixture.id))

		val bound = fixture.coordinator.snapshot()
		assertEquals(1, timer.bindCount)
		assertEquals(ReaderTransitionOperation.PublicationClose, bound.activeOperation)
		assertEquals(null, bound.lastOutcome)
		assertFalse(bound.releaseOnlySink)

		fixture.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(fixture.id))

		val closed = fixture.coordinator.snapshot()
		assertEquals(null, closed.activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Cancelled, closed.lastOutcome)
		assertTrue(closed.releaseOnlySink)
		assertEquals(1, timer.cancelCount)
	}

	@Test
	fun activatedPublicationCloseTimerExpiryFailsIntoPermanentReleaseOnly() {
		val timer = ScriptedBindingTimer(TestTimerBindOutcome.Accepted)
		val fixture = activatedPublicationCloseFixture(timer)
		fixture.coordinator.enqueue(ReaderTransitionFact.HostAvailable(fixture.id))

		timer.fire()

		val expired = fixture.coordinator.snapshot()
		assertEquals(null, expired.activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Failed, expired.lastOutcome)
		assertTrue(expired.releaseOnlySink)
		assertEquals(0, timer.cancelCount)
		fixture.coordinator.enqueue(ReaderTransitionFact.Retry(fixture.id))
		assertEquals(null, fixture.coordinator.snapshot().activeOperation)
		assertEquals(1, timer.bindCount)
	}

	@Test
	fun activatedPublicationCloseWithoutTimerFailsDirectlyIntoReleaseOnly() {
		val fixture = activatedPublicationCloseFixture(timer = null)

		fixture.coordinator.enqueue(ReaderTransitionFact.HostAvailable(fixture.id))

		val failed = fixture.coordinator.snapshot()
		assertEquals(null, failed.activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Failed, failed.lastOutcome)
		assertTrue(failed.releaseOnlySink)
		assertEquals(0, failed.mailboxSize)
	}

	@Test
	fun activatedPublicationCloseRejectedTimerBindFailsDirectlyIntoReleaseOnly() {
		val timer = ScriptedBindingTimer(TestTimerBindOutcome.Rejected)
		val fixture = activatedPublicationCloseFixture(timer)

		fixture.coordinator.enqueue(ReaderTransitionFact.HostAvailable(fixture.id))

		val failed = fixture.coordinator.snapshot()
		assertEquals(1, timer.bindCount)
		assertEquals(null, failed.activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Failed, failed.lastOutcome)
		assertTrue(failed.releaseOnlySink)
	}

	@Test
	fun activatedPublicationCloseThrowingTimerBindFailsDirectlyIntoReleaseOnly() {
		val timer = ScriptedBindingTimer(TestTimerBindOutcome.Throws)
		val fixture = activatedPublicationCloseFixture(timer)

		fixture.coordinator.enqueue(ReaderTransitionFact.HostAvailable(fixture.id))

		val failed = fixture.coordinator.snapshot()
		assertEquals(1, timer.bindCount)
		assertEquals(null, failed.activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Failed, failed.lastOutcome)
		assertTrue(failed.releaseOnlySink)
	}

	@Test
	fun activatedPublicationCloseTimerOwnershipConflictFailsDirectlyIntoReleaseOnly() {
		val timer = ScriptedBindingTimer(TestTimerBindOutcome.WrongOwner)
		val fixture = activatedPublicationCloseFixture(timer)

		fixture.coordinator.enqueue(ReaderTransitionFact.HostAvailable(fixture.id))

		val failed = fixture.coordinator.snapshot()
		assertEquals(1, timer.bindCount)
		assertEquals(1, timer.cancelCount)
		assertEquals(null, failed.activeOperation)
		assertEquals(ReaderTransitionOutcomeKind.Failed, failed.lastOutcome)
		assertTrue(failed.releaseOnlySink)
	}

	@Test
	fun timerBindingRejectionQueuesTypedFactWithoutTimeoutSubstitution() {
		val observedFacts = mutableListOf<ReaderTransitionFactKind>()
		val fixture = coordinatorFixture(
			task6FactOnlyTimer = null,
			activationState = { ReaderSessionActivationState.Activated },
			onObservation = { observation -> observation.factKind?.let(observedFacts::add) }
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		val snapshot = fixture.coordinator.snapshot()
		assertEquals(ReaderTransitionOutcomeKind.Failed, snapshot.lastOutcome)
		assertEquals(null, snapshot.activeOperation)
		assertTrue(fixture.ports.commands.none { it is ReaderTransitionCommand.ReserveDeck })
		assertEquals(1, observedFacts.count { it == ReaderTransitionFactKind.CommandRejected })
		assertEquals(0, observedFacts.count { it == ReaderTransitionFactKind.DeadlineExpired })
	}

	@Test
	fun timerBindingThrowQueuesBoundedCommandRejectionBeforePhysicalWork() {
		val observedFacts = mutableListOf<ReaderTransitionFactKind>()
		val throwingTimer = object : ReaderTask6FactOnlyTimerPort {
			override fun bindBeforeWork(
				transitionId: ReaderTransitionId,
				onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
			): ReaderTask6FactOnlyTimerRegistration? = error("private timer detail")

			override fun matchingProgress(
				registration: ReaderTask6FactOnlyTimerRegistration,
				nowMillis: Long
			) = ReaderPortCommandResult.Accepted

			override fun snapshotForTask7Transfer(
				registration: ReaderTask6FactOnlyTimerRegistration
			): ReaderTask6FactOnlyTimerTransferSnapshot? = null

			override fun cancel(
				registration: ReaderTask6FactOnlyTimerRegistration
			) = ReaderPortCommandResult.Accepted
		}
		val fixture = coordinatorFixture(
			task6FactOnlyTimer = throwingTimer,
			activationState = { ReaderSessionActivationState.Activated },
			onObservation = { observation -> observation.factKind?.let(observedFacts::add) }
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertEquals(ReaderTransitionOutcomeKind.Failed, fixture.coordinator.snapshot().lastOutcome)
		assertTrue(fixture.ports.commands.none { it is ReaderTransitionCommand.ReserveDeck })
		assertEquals(1, observedFacts.count { it == ReaderTransitionFactKind.CommandRejected })
		assertEquals(0, observedFacts.count { it == ReaderTransitionFactKind.DeadlineExpired })
	}

	@Test
	fun materialAllocationRejectionReentersFifoAsExactRetryableCommandFact() {
		val binding = binding("typed-material-rejection")
		val observedFacts = mutableListOf<ReaderTransitionFactKind>()
		lateinit var ports: RecordingPorts
		ports = RecordingPorts(RecordingClock()) { command, onFact ->
			when (command) {
				is ReaderTransitionCommand.RequestSemanticSynchronization -> onFact(
					ReaderTransitionFact.FoliateDestinationCommitted(command.transitionId, binding)
				)
				is ReaderTransitionCommand.AllocateMaterialBinding -> onFact(
					ReaderTransitionFact.CommandRejected(
						command.transitionId,
						paige.navic.reader.ReaderTransitionCommandStage.MaterialAllocation,
						paige.navic.reader.ReaderTransitionCommandRejectionReason.MaterialAllocationRejected
					)
				)
				else -> Unit
			}
		}
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(
				committed = readerAndroidHostTestNeutralInitial(17L, 19L)
			),
			onObservation = { observation -> observation.factKind?.let(observedFacts::add) }
		)

		coordinator.enqueue(
			ReaderTransitionFact.Intent(
				null,
				paige.navic.reader.ReaderBootstrapNativePageIntent(ReaderSemanticRequestHandle(1L))
			)
		)

		val snapshot = coordinator.snapshot()
		assertEquals(ReaderTransitionOutcomeKind.Failed, snapshot.lastOutcome)
		assertEquals(null, snapshot.activeOperation)
		assertEquals(1, observedFacts.count { it == ReaderTransitionFactKind.CommandRejected })
		assertEquals(0, observedFacts.count { it == ReaderTransitionFactKind.DeadlineExpired })
		assertTrue(ports.commands.any { it is ReaderTransitionCommand.AllocateMaterialBinding })
	}

	private fun realSemanticCoordinatorFixture(
		request: ReaderSemanticExecutableRequest
	): RealSemanticCoordinatorFixture = realSemanticCoordinatorFixtureWithObservation(request) { }

	private fun realSemanticCoordinatorFixtureWithObservation(
		request: ReaderSemanticExecutableRequest,
		onObservation: (ReaderTransitionCoordinatorObservation) -> Unit
	): RealSemanticCoordinatorFixture {
		val registry = ReaderSemanticExecutableRequestRegistry(17L)
		val handle = registry.register(request)
		val executor = ReaderSemanticCommandExecutor(registry)
		val ports = RecordingPorts(
			clock = RecordingClock(),
			semanticCommand = ReaderSemanticCommandPort(executor::synchronize),
			onIssue = { _, _ -> }
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(
				committed = readerAndroidHostTestNeutralInitial(17L, 19L)
			),
			onObservation = onObservation
		)
		return RealSemanticCoordinatorFixture(
			handle = handle,
			registry = registry,
			executor = executor,
			semanticFixture = SemanticCoordinatorFixture(ports, coordinator)
		)
	}

	private fun semanticCoordinatorFixture(
		semanticPort: ReaderSemanticCommandPort
	): SemanticCoordinatorFixture {
		val ports = RecordingPorts(
			clock = RecordingClock(),
			semanticCommand = semanticPort,
			onIssue = { _, _ -> }
		)
		return SemanticCoordinatorFixture(
			ports = ports,
			coordinator = ReaderResumableTransitionCoordinator(
				ports = ports,
				mode = ReaderTransitionMode.Active,
				journal = ReaderTransitionJournal(
					committed = readerAndroidHostTestNeutralInitial(17L, 19L)
				)
			)
		)
	}

	private fun bootstrapSemanticIntent(
		handle: ReaderSemanticRequestHandle = ReaderSemanticRequestHandle(1L)
	) = ReaderTransitionFact.Intent(
		transitionId = null,
		intent = paige.navic.reader.ReaderBootstrapNativePageIntent(handle)
	)

	private fun semanticDestinationReceipt(
		command: ReaderTransitionCommand.RequestSemanticSynchronization,
		binding: ReaderPresentationBinding,
		eventSequence: Long
	): ReaderPresentationEventReceipt {
		val version = ReaderPresentationReceiptVersion(
			readerSessionGeneration = command.transitionId.readerSessionGeneration,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = eventSequence
		)
		return ReaderPresentationEventReceipt(
			event = ReaderPresentationEvent.FoliateRelocated(binding, null),
			preVersion = version.copy(eventSequence = eventSequence - 1L),
			version = version,
			disposition = ReaderPresentationEventDisposition.Accepted,
			postState = ReaderPresentationState(binding = binding),
			effects = emptyList(),
			origin = paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand(
				command.transitionId,
				paige.navic.reader.ReaderSemanticCommandSlotId(1L)
			)
		)
	}

	private fun semanticDestinationReceipt(
		origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand,
		binding: ReaderPresentationBinding,
		eventSequence: Long
	): ReaderPresentationEventReceipt {
		val version = ReaderPresentationReceiptVersion(
			readerSessionGeneration = origin.transitionId.readerSessionGeneration,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = eventSequence
		)
		return ReaderPresentationEventReceipt(
			event = ReaderPresentationEvent.FoliateRelocated(binding, null),
			preVersion = version.copy(eventSequence = eventSequence - 1L),
			version = version,
			disposition = ReaderPresentationEventDisposition.Accepted,
			postState = ReaderPresentationState(binding = binding),
			effects = emptyList(),
			origin = origin
		)
	}

	private fun assertReleaseOnlySemanticAuthority(
		coordinator: ReaderResumableTransitionCoordinator,
		expected: ReaderPresentationBinding
	) {
		val journal = ReaderResumableTransitionCoordinator::class.java
			.getDeclaredField("journal")
			.apply { isAccessible = true }
			.get(coordinator) as ReaderTransitionJournal
		assertEquals(
			expected,
			assertNotNull(journal.releaseOnlyCleanup?.authoritativeSemanticDestination).binding
		)
	}

	private fun assertSemanticViolation(
		fixture: SemanticCoordinatorFixture,
		reason: ReaderSemanticPortContractViolationReason,
		callbackCount: ReaderSaturatingCallbackCount
	) {
		val violation = fixture.ports.acceptedFacts
			.filterIsInstance<ReaderTransitionFact.SemanticPortContractViolated>()
			.single()
		assertEquals(reason, violation.reason)
		assertEquals(callbackCount, violation.callbackCount)
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
		assertEquals(ReaderTransitionOutcomeKind.Failed, fixture.coordinator.snapshot().lastOutcome)
		assertEquals(null, fixture.coordinator.snapshot().activeOperation)
	}

	private fun assertReceiptImmediatelyPrecedesViolation(fixture: SemanticCoordinatorFixture) {
		val semanticFacts = fixture.ports.acceptedFacts.filter {
			it is ReaderTransitionFact.FoliateDestinationCommitted ||
				it is ReaderTransitionFact.SemanticPortContractViolated
		}
		assertEquals(2, semanticFacts.size)
		assertTrue(semanticFacts[0] is ReaderTransitionFact.FoliateDestinationCommitted)
		assertTrue(semanticFacts[1] is ReaderTransitionFact.SemanticPortContractViolated)
	}

	private fun settlementCoordinator(
		model: paige.navic.reader.ReaderTransitionModelFixture
	) = ReaderResumableTransitionCoordinator(
		ports = RecordingPorts(RecordingClock()) { _, _ -> },
		mode = ReaderTransitionMode.Active,
		journal = model.journal
	)

	private fun settlementReceipt(
		model: paige.navic.reader.ReaderTransitionModelFixture,
		eventSequence: Long,
		originatingTransitionId: ReaderTransitionId? = null
	): ReaderPresentationEventReceipt {
		val version = ReaderPresentationReceiptVersion(
			readerSessionGeneration = model.id.readerSessionGeneration,
			publicationIdentity = model.successor.publicationIdentity,
			eventSequence = eventSequence
		)
		return ReaderPresentationEventReceipt(
			event = ReaderPresentationEvent.FoliateRelocated(
				model.successor,
				matchingSettlementFact(model).acknowledgement
			),
			preVersion = version.copy(eventSequence = eventSequence - 1L),
			version = version,
			disposition = ReaderPresentationEventDisposition.Accepted,
			postState = ReaderPresentationState(binding = model.successor),
			effects = emptyList(),
			origin = originatingTransitionId?.let {
				paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand(
					it,
					paige.navic.reader.ReaderSemanticCommandSlotId(eventSequence)
				)
			} ?: paige.navic.reader.ReaderPresentationEventOrigin.UnsolicitedFoliate
		)
	}

	private fun destinationReceipt(
		model: paige.navic.reader.ReaderTransitionModelFixture,
		binding: ReaderPresentationBinding,
		eventSequence: Long
	): ReaderPresentationEventReceipt {
		val version = ReaderPresentationReceiptVersion(
			readerSessionGeneration = model.id.readerSessionGeneration,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = eventSequence
		)
		return ReaderPresentationEventReceipt(
			event = ReaderPresentationEvent.FoliateRelocated(binding, null),
			preVersion = version.copy(eventSequence = eventSequence - 1L),
			version = version,
			disposition = ReaderPresentationEventDisposition.Accepted,
			postState = ReaderPresentationState(binding = binding),
			effects = emptyList(),
			origin = paige.navic.reader.ReaderPresentationEventOrigin.UnsolicitedFoliate
		)
	}

	private fun activatedNeutralTransitionFixture(
		timer: ReaderTask6FactOnlyTimerPort,
		onObservation: (ReaderTransitionCoordinatorObservation) -> Unit = {}
	): NeutralCoordinatorFixture {
		val binding = binding("activated-neutral")
		val id = transitionId(binding, ReaderTransitionOperation.CoverToPageEntry)
		val owned = ReaderTransitionResourceKey(
			id,
			ReaderTransitionResourceKind.Raster,
			401L
		)
		val journal = readerAndroidHostTestJournal(
			committed = readerAndroidHostTestNeutralInitial(
				id.readerSessionGeneration,
				id.coordinatorEpoch
			),
			lastTransitionSequence = id.sequence,
			lastIssuedTransitionIdentity = id.parentIdentity(),
			active = ReaderActiveTransition(
				id = id,
				phase = ReaderTransitionLivenessTable.phase(
					id = id,
					kind = ReaderTransitionPhaseKind.AwaitingPrerequisites,
					retainedOwner = ReaderPresentationFrameOwner.Neutral,
					satisfiedProofs = setOf(
						paige.navic.reader.ReaderTransitionProofKind.SemanticDestination
					)
				),
				pendingCommandStages = setOf(ReaderTransitionCommandStage.MaterialAllocation),
				resolvedSuccessorBinding = binding,
				ownedResourceKeys = setOf(owned),
				authoritativeDestinationCommitted = true
			)
		)
		val clock = RecordingClock()
		val ports = RecordingPorts(clock, task6FactOnlyTimer = timer) { _, _ -> }
		return NeutralCoordinatorFixture(
			binding = binding,
			owned = owned,
			fixture = CoordinatorFixture(
				id = id,
				clock = clock,
				ports = ports,
				coordinator = ReaderResumableTransitionCoordinator(
					ports = ports,
					mode = ReaderTransitionMode.Active,
					journal = journal,
					activationState = { ReaderSessionActivationState.Activated },
					onObservation = onObservation
				)
			)
		)
	}

	private fun assertActivatedTerminalCleanupSurvivesTimerCancellationFailure(
		terminalFact: (ReaderTransitionId) -> ReaderTransitionFact
	) {
		listOf(
			TestTimerCancelOutcome.Rejected,
			TestTimerCancelOutcome.Throws
		).forEach { cancelOutcome ->
			val timer = ScriptedBindingTimer(
				outcome = TestTimerBindOutcome.Accepted,
				cancelOutcome = cancelOutcome
			)
			val fixture = coordinatorFixture(
				task6FactOnlyTimer = timer,
				activationState = { ReaderSessionActivationState.Activated }
			)
			val owned = ReaderTransitionResourceKey(
				fixture.id,
				ReaderTransitionResourceKind.Raster,
				307L
			)
			fixture.coordinator.enqueue(ReaderTransitionFact.ResourceObserved(fixture.id, owned))
			assertEquals(1, timer.bindCount)

			val terminal = terminalFact(fixture.id)
			fixture.coordinator.enqueue(terminal)

			val releases = fixture.ports.commands
				.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
			assertEquals(2, releases.size)
			assertEquals(2, releases.map { it.key }.distinct().size)
			assertTrue(releases.any { it.key == owned })
			assertEquals(
				1,
				fixture.ports.commands.count { it is ReaderTransitionCommand.CancelOwnedWork }
			)
			val snapshot = fixture.coordinator.snapshot()
			assertTrue(snapshot.releaseOnlySink)
			assertEquals(null, snapshot.activeOperation)
			assertEquals(1, snapshot.scheduledCallbackCount)
			assertEquals(
				when (cancelOutcome) {
					TestTimerCancelOutcome.Rejected ->
						ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
					TestTimerCancelOutcome.Throws ->
						ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
					TestTimerCancelOutcome.Accepted -> error("Failure case required")
				},
				snapshot.releaseOnlyDeadlineStatus
			)
			assertEquals(1, timer.cancelCount)

			fixture.coordinator.enqueue(terminal)
			assertEquals(2, fixture.ports.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size)
			assertEquals(
				1,
				fixture.ports.commands.count { it is ReaderTransitionCommand.CancelOwnedWork }
			)
			assertEquals(1, timer.cancelCount)
			assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
		}
	}

	private fun activatedPublicationCloseFixture(
		timer: ReaderTask6FactOnlyTimerPort?
	): CoordinatorFixture {
		val binding = binding("activated-publication-close")
		val id = transitionId(binding, ReaderTransitionOperation.PublicationClose)
		val owner = shellOwner(binding)
		val journal = readerAndroidHostTestJournal(
			committed = readerAndroidHostTestAdoptedInitial(
				owner = owner,
				binding = binding,
				readerSessionGeneration = id.readerSessionGeneration,
				coordinatorEpoch = id.coordinatorEpoch
			),
			lastTransitionSequence = id.sequence,
			lastIssuedTransitionIdentity = id.parentIdentity(),
			active = ReaderActiveTransition(
				id = id,
				phase = ReaderTransitionLivenessTable.phase(
					id = id,
					kind = ReaderTransitionPhaseKind.AwaitingProof,
					retainedOwner = ReaderPresentationFrameOwner.Neutral
				)
			)
		)
		val clock = RecordingClock()
		val ports = RecordingPorts(clock, task6FactOnlyTimer = timer) { _, _ -> }
		return CoordinatorFixture(
			id = id,
			clock = clock,
			ports = ports,
			coordinator = ReaderResumableTransitionCoordinator(
				ports = ports,
				mode = ReaderTransitionMode.Active,
				journal = journal,
				activationState = { ReaderSessionActivationState.Activated }
			)
		)
	}

	private fun coordinatorFixture(
		mode: ReaderTransitionMode = ReaderTransitionMode.Active,
		bindingValue: String = "synthetic",
		operation: ReaderTransitionOperation = ReaderTransitionOperation.CoverToPageEntry,
		clock: RecordingClock = RecordingClock(),
		task6FactOnlyTimer: ReaderTask6FactOnlyTimerPort? = null,
		activationState: () -> ReaderSessionActivationState = { ReaderSessionActivationState.Legacy },
		onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit = { _, _ -> },
		onObservation: (ReaderTransitionCoordinatorObservation) -> Unit = {}
	): CoordinatorFixture {
		val binding = binding(bindingValue)
		val id = transitionId(binding, operation)
		val owner = shellOwner(binding)
		var journal = readerAndroidHostTestJournal(
			committed = readerAndroidHostTestAdoptedInitial(
				owner = owner,
				binding = binding,
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
					retainedOwner = owner,
					satisfiedProofs = setOf(
						paige.navic.reader.ReaderTransitionProofKind.SemanticDestination
					)
				),
				pendingCommandStages = setOf(ReaderTransitionCommandStage.MaterialAllocation),
				resolvedSuccessorBinding = binding,
				authoritativeDestinationCommitted = true
			)
		)
		if (
			paige.navic.reader.ReaderTransitionProofKind.MaterialBindingAllocation in
				journal.active!!.phase.contract.awaitedProofs
		) {
			journal = journal.reduce(
				ReaderTransitionFact.MaterialBindingAllocated(
					id,
					paige.navic.reader.ReaderMaterialGenerationAllocation(
						id,
						binding,
						requireNotNull(binding.preparationGeneration),
						requireNotNull(binding.rasterGeneration),
						requireNotNull(binding.textureGeneration)
					)
				)
			).state
		}
		val ports = RecordingPorts(
			clock,
			task6FactOnlyTimer = task6FactOnlyTimer,
			onIssue = onIssue
		)
		return CoordinatorFixture(
			id = id,
			clock = clock,
			ports = ports,
			coordinator = ReaderResumableTransitionCoordinator(
				ports = ports,
				mode = mode,
				journal = journal,
				activationState = activationState,
				onObservation = onObservation
			)
		)
	}

	@Test
	fun sharedJournalFixturePreservesAuthenticCommittedChainAndIssuedHistory() {
		val binding = binding("authentic-chain")
		val owner = shellOwner(binding)
		fun id(sequence: Long, parent: paige.navic.reader.ReaderTransitionParentIdentity?) =
			ReaderTransitionId(
				readerSessionGeneration = 17L,
				coordinatorEpoch = 19L,
				sequence = sequence,
				operation = ReaderTransitionOperation.ShellCoverCommit,
				expectedBinding = ReaderExpectedPresentationBinding.Exact(binding),
				parent = parent
			)
		val root = id(1L, null)
		val committedId = id(2L, root.parentIdentity())
		val activeId = id(3L, committedId.parentIdentity())
		val key = ReaderTransitionResourceKey(
			committedId,
			ReaderTransitionResourceKind.FrameHandoff,
			307L
		)
		val registration = ReaderTransitionResourceRegistration(
			key,
			ReaderResourceRetirementOrder(17L, 19L, 2L)
		)
		val committed = ReaderCommittedPresentation.Transition(
			ReaderCommittedTransition(committedId, owner, binding, key, registration)
		)
		val active = ReaderActiveTransition(
			id = activeId,
			phase = ReaderTransitionLivenessTable.phase(
				id = activeId,
				kind = ReaderTransitionPhaseKind.AwaitingPrerequisites,
				retainedOwner = owner
			)
		)

		val journal = readerAndroidHostTestJournal(
			committed = committed,
			lastTransitionSequence = activeId.sequence,
			lastIssuedTransitionIdentity = activeId.parentIdentity(),
			active = active
		)

		assertEquals(committed, journal.committed)
		assertEquals(3L, journal.lastTransitionSequence)
		assertEquals(activeId.parentIdentity(), journal.lastIssuedTransitionIdentity)
	}

	@Test
	fun coordinatorPreservesExactCommittedRegistrationWhenReleasing() {
		val binding = binding("registered-release")
		val id = transitionId(binding, ReaderTransitionOperation.ShellCoverCommit)
		val owner = shellOwner(binding)
		val key = ReaderTransitionResourceKey(
			id,
			ReaderTransitionResourceKind.FrameHandoff,
			41L
		)
		val registration = ReaderTransitionResourceRegistration(
			key,
			ReaderResourceRetirementOrder(
				id.readerSessionGeneration,
				id.coordinatorEpoch,
				99L
			)
		)
		val clock = RecordingClock()
		val ports = RecordingPorts(clock, onIssue = { _, _ -> })
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(
				committed = ReaderCommittedTransition(id, owner, binding, key, registration)
			)
		)

		coordinator.enqueue(ReaderTransitionFact.PublicationReplaced(null))

		val release = assertIs<ReaderTransitionCommand.ReleaseResource>(ports.commands.single())
		assertEquals(registration, release.registration)
	}

	private fun binding(value: String) = ReaderPresentationBinding(
		foliateSessionId = value,
		publicationGeneration = 2L,
		viewportGeneration = 3L,
		profileGeneration = 5L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity(value, 1L),
		rasterGeneration = 7L,
		textureGeneration = 11L,
		preparationGeneration = 13L
	)

	private fun transitionId(
		binding: ReaderPresentationBinding,
		operation: ReaderTransitionOperation
	) = ReaderTransitionId(
		readerSessionGeneration = 17L,
		coordinatorEpoch = 19L,
		sequence = 1L,
		operation = operation,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
	)

	private fun shellOwner(binding: ReaderPresentationBinding) = ReaderPresentationFrameOwner.ShellCover(
		ReaderShellCoverCommitProof(
			token = ReaderPresentationToken(29L),
			binding = binding,
			coverGeneration = 31L,
			presentedFrame = 37L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
	)

	private fun deckKey(id: ReaderTransitionId) = ReaderTransitionResourceKey(
		transitionId = id,
		kind = ReaderTransitionResourceKind.Deck,
		opaqueId = 41L
	)

	private fun source(path: String) = File(path).readText()
}

internal fun readerAndroidHostTestJournal(
	committed: ReaderCommittedPresentation,
	lastTransitionSequence: Long,
	lastIssuedTransitionIdentity: paige.navic.reader.ReaderTransitionParentIdentity?,
	active: ReaderActiveTransition? = null,
	lastOutcome: paige.navic.reader.ReaderTransitionOutcome? = null,
	retryableTransition: paige.navic.reader.ReaderRetryableTransition? = null
): ReaderTransitionJournal = ReaderTransitionJournal(
	active = active,
	lastOutcome = lastOutcome,
	committed = committed,
	retryableTransition = retryableTransition,
	lastTransitionSequence = lastTransitionSequence,
	lastIssuedTransitionIdentity = lastIssuedTransitionIdentity
)

internal fun readerAndroidHostTestNeutralInitial(
	readerSessionGeneration: Long,
	coordinatorEpoch: Long
): ReaderCommittedPresentation = ReaderCommittedPresentation.Initial(
	paige.navic.reader.ReaderInitialCommittedPresentationOrigin.Neutral(
		readerSessionGeneration = readerSessionGeneration,
		coordinatorEpoch = coordinatorEpoch,
		requestedLease = paige.navic.reader.ReaderInitialPresentationInputLease.ChromeOnly,
		physicalLease = paige.navic.reader.ReaderInitialPresentationInputLease.None
	)
)

internal fun readerAndroidHostTestAdoptedInitial(
	owner: ReaderPresentationFrameOwner,
	binding: ReaderPresentationBinding,
	readerSessionGeneration: Long,
	coordinatorEpoch: Long,
	seedValue: Long = 1L
): ReaderCommittedPresentation {
	val seedId = paige.navic.reader.ReaderAdoptedPredecessorSeedId.fromValidatedImport(seedValue)
	val registration = ReaderTransitionResourceRegistration(
		ReaderTransitionResourceKey(
			paige.navic.reader.ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
			requireNotNull(readerAdoptedResourceKindFor(owner)),
			seedValue
		),
		ReaderResourceRetirementOrder(readerSessionGeneration, coordinatorEpoch, 1L)
	)
	return ReaderCommittedPresentation.Initial(
		paige.navic.reader.ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
			seedId = seedId,
			readerSessionGeneration = readerSessionGeneration,
			coordinatorEpoch = coordinatorEpoch,
			owner = owner,
			binding = binding,
			resource = registration,
			requestedLease = paige.navic.reader.ReaderInitialPresentationInputLease.ChromeOnly,
			physicalLease = paige.navic.reader.ReaderInitialPresentationInputLease.ChromeOnly
		)
	)
}

private data class NeutralCoordinatorFixture(
	val binding: ReaderPresentationBinding,
	val owned: ReaderTransitionResourceKey,
	val fixture: CoordinatorFixture
)

private data class RealSemanticCoordinatorFixture(
	val handle: ReaderSemanticRequestHandle,
	val registry: ReaderSemanticExecutableRequestRegistry,
	val executor: ReaderSemanticCommandExecutor,
	val semanticFixture: SemanticCoordinatorFixture
) {
	val ports: RecordingPorts get() = semanticFixture.ports
	val coordinator: ReaderResumableTransitionCoordinator get() = semanticFixture.coordinator
}

private data class SemanticCoordinatorFixture(
	val ports: RecordingPorts,
	val coordinator: ReaderResumableTransitionCoordinator
)

private data class CoordinatorFixture(
	val id: ReaderTransitionId,
	val clock: RecordingClock,
	val ports: RecordingPorts,
	val coordinator: ReaderResumableTransitionCoordinator
)

private class RecordingPorts(
	override val clock: RecordingClock,
	override val ownerAndInputPublication: ReaderOwnerAndInputPublicationPort? = null,
	override val task6FactOnlyTimer: ReaderTask6FactOnlyTimerPort? = null,
	override val semanticCommand: ReaderSemanticCommandPort? = null,
	private val onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit
) : ReaderResumableTransitionPorts {
	val commands = mutableListOf<ReaderTransitionCommand>()
	val acceptedFacts = mutableListOf<ReaderTransitionFact>()

	override fun acceptsFact(fact: ReaderTransitionFact): Boolean {
		acceptedFacts += fact
		return true
	}

	override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
		commands += command
		onIssue(command, onFact)
	}
}

private enum class TestTimerBindOutcome {
	Accepted,
	Rejected,
	Throws,
	WrongOwner,
	WrongPreviousOwner,
	SynchronousExpiry
}

private enum class TestTimerProgressOutcome { Accepted, Rejected, Throws, SynchronousExpiry }

private enum class TestTimerCancelOutcome { Accepted, Rejected, Throws }

private class AliasingFactOnlyTimer : ReaderTask6FactOnlyTimerPort {
	private var activeRegistration: ReaderTask6FactOnlyTimerRegistration? = null
	var bindCount = 0
		private set
	var cancelCount = 0
		private set
	val trackedRegistrationCount: Int
		get() = if (activeRegistration == null) 0 else 1

	override fun bindBeforeWork(
		transitionId: ReaderTransitionId,
		onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
	): ReaderTask6FactOnlyTimerRegistration {
		bindCount += 1
		if (bindCount == 2) return requireNotNull(activeRegistration)
		return ReaderTask6FactOnlyTimerRegistration(
			id = ReaderTask6FactOnlyTimerRegistrationId(bindCount.toLong()),
			transitionId = transitionId,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				domain = ReaderLegacyPhysicalDomain(
					transitionId.readerSessionGeneration,
					ReaderLegacyFreezeToken(bindCount.toLong())
				),
				source = ReaderLegacyInventorySource.DeadlineRegistration,
				sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(bindCount.toLong())
			),
			hardExpiresAtMillis = 31_000L,
			noProgressIntervalMillis = 10_000L,
			permitsMatchingProgressRearm = true
		).also { activeRegistration = it }
	}

	override fun matchingProgress(
		registration: ReaderTask6FactOnlyTimerRegistration,
		nowMillis: Long
	): ReaderPortCommandResult = ReaderPortCommandResult.Accepted

	override fun snapshotForTask7Transfer(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderTask6FactOnlyTimerTransferSnapshot? = null

	override fun cancel(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderPortCommandResult {
		cancelCount += 1
		return if (activeRegistration == registration) {
			activeRegistration = null
			ReaderPortCommandResult.Accepted
		} else {
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		}
	}
}

private class SequencedFactOnlyTimer(
	bindScript: List<TestTimerBindOutcome>,
	cancelScript: List<TestTimerCancelOutcome> = emptyList(),
	progressScript: List<TestTimerProgressOutcome> = emptyList(),
	private val callbackBeforeProgressReject: Boolean = false
) : ReaderTask6FactOnlyTimerPort {
	private val bindOutcomes = ArrayDeque(bindScript)
	private val cancelOutcomes = ArrayDeque(cancelScript)
	private val progressOutcomes = ArrayDeque(progressScript)
	private val activeRegistrations = linkedSetOf<ReaderTask6FactOnlyTimerRegistration>()
	private val expirationCallbacks = linkedMapOf<
		ReaderTask6FactOnlyTimerRegistrationId,
		(ReaderTransitionFact.DeadlineExpired) -> Unit
	>()
	private var firstTransitionId: ReaderTransitionId? = null
	var bindCount = 0
		private set
	var cancelCount = 0
		private set
	val trackedRegistrationCount: Int
		get() = activeRegistrations.size

	override fun bindBeforeWork(
		transitionId: ReaderTransitionId,
		onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
	): ReaderTask6FactOnlyTimerRegistration? {
		bindCount += 1
		val outcome = bindOutcomes.removeFirstOrNull() ?: TestTimerBindOutcome.Accepted
		if (outcome == TestTimerBindOutcome.Throws) error("private bind detail")
		if (outcome == TestTimerBindOutcome.Rejected) return null
		if (firstTransitionId == null) firstTransitionId = transitionId
		val owner = when (outcome) {
			TestTimerBindOutcome.WrongOwner ->
				transitionId.copy(readerSessionGeneration = transitionId.readerSessionGeneration + 1L)
			TestTimerBindOutcome.WrongPreviousOwner -> requireNotNull(firstTransitionId)
			else -> transitionId
		}
		val registration = ReaderTask6FactOnlyTimerRegistration(
			id = ReaderTask6FactOnlyTimerRegistrationId(bindCount.toLong()),
			transitionId = owner,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				domain = ReaderLegacyPhysicalDomain(
					owner.readerSessionGeneration,
					ReaderLegacyFreezeToken(bindCount.toLong())
				),
				source = ReaderLegacyInventorySource.DeadlineRegistration,
				sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(bindCount.toLong())
			),
			hardExpiresAtMillis = 31_000L,
			noProgressIntervalMillis = 10_000L,
			permitsMatchingProgressRearm = true
		)
		activeRegistrations += registration
		expirationCallbacks[registration.id] = onExpired
		if (outcome == TestTimerBindOutcome.SynchronousExpiry) {
			activeRegistrations -= registration
			expirationCallbacks.remove(registration.id)
			onExpired(ReaderTransitionFact.DeadlineExpired(owner))
		}
		return registration
	}

	override fun matchingProgress(
		registration: ReaderTask6FactOnlyTimerRegistration,
		nowMillis: Long
	): ReaderPortCommandResult {
		val outcome = progressOutcomes.removeFirstOrNull() ?: if (callbackBeforeProgressReject) {
			TestTimerProgressOutcome.Rejected
		} else {
			TestTimerProgressOutcome.Accepted
		}
		if (
			outcome == TestTimerProgressOutcome.SynchronousExpiry ||
			callbackBeforeProgressReject
		) {
			activeRegistrations -= registration
			val callback = expirationCallbacks.remove(registration.id)
			requireNotNull(callback)(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
		}
		return when (outcome) {
			TestTimerProgressOutcome.Accepted,
			TestTimerProgressOutcome.SynchronousExpiry -> ReaderPortCommandResult.Accepted
			TestTimerProgressOutcome.Rejected ->
				ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
			TestTimerProgressOutcome.Throws -> error("private progress detail")
		}
	}

	fun fireRegistration(id: ReaderTask6FactOnlyTimerRegistrationId) {
		val registration = requireNotNull(activeRegistrations.singleOrNull { it.id == id })
		activeRegistrations -= registration
		val callback = requireNotNull(expirationCallbacks.remove(id))
		callback(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
	}

	override fun snapshotForTask7Transfer(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderTask6FactOnlyTimerTransferSnapshot? = null

	override fun cancel(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderPortCommandResult {
		cancelCount += 1
		return when (cancelOutcomes.removeFirstOrNull() ?: TestTimerCancelOutcome.Accepted) {
			TestTimerCancelOutcome.Accepted -> {
				activeRegistrations -= registration
				expirationCallbacks.remove(registration.id)
				ReaderPortCommandResult.Accepted
			}
			TestTimerCancelOutcome.Rejected ->
				ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
			TestTimerCancelOutcome.Throws -> error("private cancel detail")
		}
	}
}

private class ScriptedBindingTimer(
	private val outcome: TestTimerBindOutcome,
	private val progressOutcome: TestTimerProgressOutcome = TestTimerProgressOutcome.Accepted,
	private val cancelOutcome: TestTimerCancelOutcome = TestTimerCancelOutcome.Accepted
) : ReaderTask6FactOnlyTimerPort {
	private var onExpired: ((ReaderTransitionFact.DeadlineExpired) -> Unit)? = null
	private var registration: ReaderTask6FactOnlyTimerRegistration? = null
	var bindCount = 0
		private set
	var cancelCount = 0
		private set

	override fun bindBeforeWork(
		transitionId: ReaderTransitionId,
		onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
	): ReaderTask6FactOnlyTimerRegistration? {
		bindCount += 1
		if (outcome == TestTimerBindOutcome.Throws) error("private timer detail")
		if (outcome == TestTimerBindOutcome.Rejected) return null
		this.onExpired = onExpired
		val owner = if (outcome == TestTimerBindOutcome.WrongOwner) {
			transitionId.copy(readerSessionGeneration = transitionId.readerSessionGeneration + 1L)
		} else {
			transitionId
		}
		val permitsProgressRearm = progressOutcome != TestTimerProgressOutcome.Accepted
		val result = ReaderTask6FactOnlyTimerRegistration(
			id = ReaderTask6FactOnlyTimerRegistrationId(bindCount.toLong()),
			transitionId = owner,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				domain = ReaderLegacyPhysicalDomain(
					owner.readerSessionGeneration,
					ReaderLegacyFreezeToken(bindCount.toLong())
				),
				source = ReaderLegacyInventorySource.DeadlineRegistration,
				sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(bindCount.toLong())
			),
			hardExpiresAtMillis = 3_000L,
			noProgressIntervalMillis = 1_000L.takeIf { permitsProgressRearm },
			permitsMatchingProgressRearm = permitsProgressRearm
		).also { registration = it }
		if (outcome == TestTimerBindOutcome.SynchronousExpiry) {
			onExpired(ReaderTransitionFact.DeadlineExpired(owner))
		}
		return result
	}

	fun fire() {
		val current = requireNotNull(registration)
		requireNotNull(onExpired)(ReaderTransitionFact.DeadlineExpired(current.transitionId))
	}

	override fun matchingProgress(
		registration: ReaderTask6FactOnlyTimerRegistration,
		nowMillis: Long
	): ReaderPortCommandResult = when (progressOutcome) {
		TestTimerProgressOutcome.Accepted -> ReaderPortCommandResult.Accepted
		TestTimerProgressOutcome.Rejected ->
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		TestTimerProgressOutcome.Throws -> error("private progress detail")
		TestTimerProgressOutcome.SynchronousExpiry -> {
			requireNotNull(onExpired)(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
			ReaderPortCommandResult.Accepted
		}
	}

	override fun snapshotForTask7Transfer(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderTask6FactOnlyTimerTransferSnapshot? = null

	override fun cancel(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderPortCommandResult {
		cancelCount += 1
		return when (cancelOutcome) {
			TestTimerCancelOutcome.Accepted -> ReaderPortCommandResult.Accepted
			TestTimerCancelOutcome.Rejected ->
				ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
			TestTimerCancelOutcome.Throws -> error("private cancellation detail")
		}
	}
}

private class RejectingProgressTimer(
	private val callbackBeforeReject: Boolean,
	private val permitsMatchingProgressRearm: Boolean = true
) : ReaderTask6FactOnlyTimerPort {
	private var onExpired: ((ReaderTransitionFact.DeadlineExpired) -> Unit)? = null
	private var boundRegistration: ReaderTask6FactOnlyTimerRegistration? = null
	var bindCount = 0
		private set
	var progressCount = 0
		private set
	var cancelCount = 0
		private set
	var cancelledBoundRegistration = false
		private set

	override fun bindBeforeWork(
		transitionId: ReaderTransitionId,
		onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
	): ReaderTask6FactOnlyTimerRegistration {
		bindCount += 1
		this.onExpired = onExpired
		val registration = ReaderTask6FactOnlyTimerRegistration(
			id = ReaderTask6FactOnlyTimerRegistrationId(bindCount.toLong()),
			transitionId = transitionId,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				domain = ReaderLegacyPhysicalDomain(
					transitionId.readerSessionGeneration,
					ReaderLegacyFreezeToken(bindCount.toLong())
				),
				source = ReaderLegacyInventorySource.DeadlineRegistration,
				sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(bindCount.toLong())
			),
			hardExpiresAtMillis = 31_000L,
			noProgressIntervalMillis = 10_000L.takeIf { permitsMatchingProgressRearm },
			permitsMatchingProgressRearm = permitsMatchingProgressRearm
		)
		boundRegistration = registration
		return registration
	}

	override fun matchingProgress(
		registration: ReaderTask6FactOnlyTimerRegistration,
		nowMillis: Long
	): ReaderPortCommandResult {
		progressCount += 1
		if (callbackBeforeReject) {
			requireNotNull(onExpired)(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
		}
		return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
	}

	override fun snapshotForTask7Transfer(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderTask6FactOnlyTimerTransferSnapshot? = null

	override fun cancel(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderPortCommandResult {
		cancelCount += 1
		cancelledBoundRegistration = registration === boundRegistration
		return ReaderPortCommandResult.Accepted
	}
}

private class RecordingClock(
	private val synchronous: Boolean = false
) : ReaderTransitionClock {
	private val registrations = mutableListOf<Registration>()
	private var nowMillis = 1_000L
	val scheduledAtMillis = mutableListOf<Long>()
	var scheduleCount = 0
		private set
	var cancelCount = 0
		private set

	val activeRegistrationCount: Int
		get() = registrations.count { !it.cancelled }

	override fun nowMillis(): Long = nowMillis

	fun advanceTo(value: Long) {
		require(value >= nowMillis)
		nowMillis = value
	}

	override fun schedule(
		atMillis: Long,
		action: () -> Unit
	): ReaderTransitionClockRegistration {
		assertTrue(atMillis > nowMillis())
		scheduleCount += 1
		scheduledAtMillis += atMillis
		val registration = Registration(action) { cancelCount += 1 }
		registrations += registration
		if (synchronous) registration.fire()
		return registration
	}

	fun fireLast() {
		registrations.last().fire()
	}

	fun fireIgnoringCancellation(index: Int) {
		registrations[index].fireIgnoringCancellation()
	}

	private class Registration(
		private val action: () -> Unit,
		private val onCancel: () -> Unit
	) : ReaderTransitionClockRegistration {
		var cancelled = false
			private set

		fun fire() {
			if (!cancelled) action()
		}

		fun fireIgnoringCancellation() {
			action()
		}

		override fun cancel() {
			if (cancelled) return
			cancelled = true
			onCancel()
		}
	}
}
