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
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFactKind
import paige.navic.reader.ReaderTransitionGestureId
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionLivenessTable
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.journalAwaitingSettlement
import paige.navic.reader.matchingSettlementFact
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
		val ports = RecordingPorts(clock) { command, onFact ->
			if (command is ReaderTransitionCommand.RequestSemanticSynchronization) {
				trace += "issue-semantic"
				trace += "enqueue-callback"
				onFact(ReaderTransitionFact.FoliateDestinationCommitted(null, successor))
			}
		}
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(
				committed = ReaderCommittedTransition(
					predecessorId,
					predecessorOwner,
					predecessor,
					predecessorKey
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
				ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc)
			)
		)

		assertEquals(
			listOf("register", "issue-semantic", "enqueue-callback", "admit-callback"),
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
		assertTrue(snapshot.factClassifications.isEmpty())
		assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement, snapshot.activeOperation)
		registration.close()
	}

	@Test
	fun wrongExactSettlementReceiptThroughGatewayIsClassifiedStale() {
		val model = journalAwaitingSettlement()
		val wrongId = model.id.copy(sequence = model.id.sequence + 1L)
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
		assertEquals(listOf(owned, late), releases)
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
				val key = deckKey(command.transitionId)
				onFact(ReaderTransitionFact.DeckReserved(command.transitionId, key))
				onFact(ReaderTransitionFact.DeckOwned(command.transitionId, key))
				onFact(ReaderTransitionFact.DeckPrepared(command.transitionId, key))
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
		val staleId = fixture.id.copy(sequence = fixture.id.sequence + 1L)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(staleId))

		val snapshot = fixture.coordinator.snapshot()
		assertEquals(
			1,
			snapshot.factClassifications[ReaderTransitionFactClassification.StaleTransition]
		)
		assertEquals(ReaderTransitionPhaseKind.AwaitingPrerequisites, snapshot.activePhase)
		assertTrue(fixture.ports.commands.isEmpty())
	}

	@Test
	fun synchronousPortCallbacksNeverIncreaseAdvancementDepthAboveOne() {
		val fixture = coordinatorFixture(
			onIssue = { command, onFact ->
				val key = deckKey(command.transitionId)
				onFact(ReaderTransitionFact.DeckOwned(command.transitionId, key))
			}
		)

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProven(fixture.id))

		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		assertEquals(0, fixture.coordinator.snapshot().mailboxSize)
	}

	@Test
	fun oneScheduledCallbackPerActivePhaseAndCancellationOnReplacementAndTerminal() {
		val fixture = coordinatorFixture()

		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		fixture.coordinator.enqueue(ReaderTransitionFact.RasterProgress(fixture.id))
		assertEquals(1, fixture.clock.scheduleCount)
		assertEquals(1, fixture.clock.activeRegistrationCount)

		val successor = binding("synthetic").copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"synthetic",
				2L
			)
		)
		fixture.coordinator.enqueue(ReaderTransitionFact.FoliateDestinationCommitted(null, successor))
		assertEquals(2, fixture.clock.scheduleCount)
		assertEquals(1, fixture.clock.cancelCount)
		assertEquals(1, fixture.clock.activeRegistrationCount)

		fixture.clock.fireLast()
		assertEquals(2, fixture.clock.cancelCount)
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
		val staleId = fixture.id.copy(sequence = fixture.id.sequence + 1L)

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
				ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Jump)
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
						ReaderTransitionGestureId(101L)
					)
				)
			)
			add("cover" to ReaderTransitionFact.Intent(null, ReaderCoverEntryIntent))
			ReaderExternalRelocationSource.entries.forEach { source ->
				add(
					source.name to ReaderTransitionFact.Intent(
						null,
						ReaderExternalRelocationIntent(source)
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
			effects = emptyList()
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
		assertContains(androidHost, "ReaderTransitionMode.Shadow")
		assertContains(androidHost, "enqueueReceipt = shadowCoordinator::enqueue")
		assertContains(
			androidHost,
			"shadowCoordinator.enqueue(ReaderTransitionFact.PublicationReplaced(null))"
		)
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
			originatingTransitionId = originatingTransitionId
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
			effects = emptyList()
		)
	}

	private fun coordinatorFixture(
		mode: ReaderTransitionMode = ReaderTransitionMode.Active,
		bindingValue: String = "synthetic",
		operation: ReaderTransitionOperation = ReaderTransitionOperation.CoverToPageEntry,
		clock: RecordingClock = RecordingClock(),
		onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit = { _, _ -> },
		onObservation: (ReaderTransitionCoordinatorObservation) -> Unit = {}
	): CoordinatorFixture {
		val binding = binding(bindingValue)
		val id = transitionId(binding, operation)
		val owner = shellOwner(binding)
		val journal = ReaderTransitionJournal(
			active = ReaderActiveTransition(
				id = id,
				phase = ReaderTransitionLivenessTable.phase(
					id = id,
					kind = ReaderTransitionPhaseKind.AwaitingPrerequisites,
					retainedOwner = owner
				)
			)
		)
		val ports = RecordingPorts(clock, onIssue)
		return CoordinatorFixture(
			id = id,
			clock = clock,
			ports = ports,
			coordinator = ReaderResumableTransitionCoordinator(
				ports = ports,
				mode = mode,
				journal = journal,
				onObservation = onObservation
			)
		)
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
		sequence = 23L,
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

private data class CoordinatorFixture(
	val id: ReaderTransitionId,
	val clock: RecordingClock,
	val ports: RecordingPorts,
	val coordinator: ReaderResumableTransitionCoordinator
)

private class RecordingPorts(
	override val clock: RecordingClock,
	private val onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit
) : ReaderResumableTransitionPorts {
	val commands = mutableListOf<ReaderTransitionCommand>()

	override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
		commands += command
		onIssue(command, onFact)
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
