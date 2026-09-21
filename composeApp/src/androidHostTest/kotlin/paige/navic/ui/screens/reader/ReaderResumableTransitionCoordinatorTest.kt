package paige.navic.ui.screens.reader

import java.io.File
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
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderTransitionCommand
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
			assertEquals(1, timer.cancelCount)
			assertTrue(timer.cancelledBoundRegistration)
			assertEquals(null, snapshot.activeOperation)
			assertEquals(ReaderTransitionOutcomeKind.Failed, snapshot.lastOutcome)
			assertEquals(0, snapshot.mailboxSize)
			assertEquals(1, observedFacts.count { it == ReaderTransitionFactKind.DeadlineExpired })
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
					retainedOwner = owner
				)
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
	private val onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit
) : ReaderResumableTransitionPorts {
	val commands = mutableListOf<ReaderTransitionCommand>()

	override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
		commands += command
		onIssue(command, onFact)
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
