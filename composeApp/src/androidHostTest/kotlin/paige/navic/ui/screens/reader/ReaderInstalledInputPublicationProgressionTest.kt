package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderWhispersyncPageLocalRect
import paige.navic.reader.ReaderBootstrapNativePageIntent
import paige.navic.reader.ReaderCancelIntent
import paige.navic.reader.ReaderCommittedPresentation
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExternalRelocationIntent
import paige.navic.reader.ReaderExternalRelocationSource
import paige.navic.reader.ReaderInitialCommittedPresentationOrigin
import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderNativePageHostTokenState
import paige.navic.reader.ReaderOwnerAndInputPublicationResult
import paige.navic.reader.ReaderPageGestureLifecycle
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPagePointerRoute
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnIntent
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPlayLikeCurlDeckTargetIdentity
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderCurlSettlementTerminalIdentity
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionFrameGeometry
import paige.navic.reader.ReaderTransitionFrameTarget
import paige.navic.reader.ReaderTransitionFrameTargetSpecification
import paige.navic.reader.ReaderTransitionGestureId
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionInputLease
import paige.navic.reader.ReaderOwnerAndInputPublicationSubject
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.reader.publicationIdentity
import paige.navic.reader.ReaderCoordinator
import paige.navic.reader.ReaderEngineOpenRequest
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderLocator
import paige.navic.reader.defaultReaderSettings
import paige.navic.reader.onInstalledPageTurnRelocation
import paige.navic.reader.reduceSemanticDestination

/** Public callbacks exercise the original components, not a production all-port installation.
 * Only the ownerless initial origin is constructed; all active state, allocation, ledger
 * registration, targets, and publications must arise from commands and their callbacks.
 * The controllable frame source is deliberately not Foliate/GL acceptance evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReaderInstalledInputPublicationProgressionTest {
	@Test
	fun originalClaimedPointerSettlementMustContinueWithoutSeededFrameOrAllocation() {
		val fixture = ContinuousFixture()
		fixture.bootstrap()
		val predecessor = assertNotNull(fixture.physical)
		val down = fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Down(900f, 400f, 100L))
		val gesture = assertNotNull(down.gestureId)
		assertEquals(ReaderPagePointerRoute.Content, down.route)
		assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture), fixture.input.dispatchPointer(
			ReaderPageHostPointerEvent.Move(100f, 400f, 8f)).route)
		val turn = fixture.beginOriginalClaimedTurn(gesture)
		assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement, turn.operation, "C407.Turn.Operation")
		assertEquals(predecessor.owner, fixture.physical?.owner)
		assertEquals(ReaderPagePointerRoute.Curl(gesture),
			fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up).route,
			"C407.Turn.PointerUp")

		val beforeSettlement = fixture.commands.size
		fixture.returnSemantic(settlement = true)
		assertEquals(1, fixture.coordinator.snapshot().consumedSettlementCount,
			"C407.Turn.SettlementConsumed")
		assertEquals(predecessor.owner, fixture.physical?.owner)
		assertTrue(fixture.commands.drop(beforeSettlement).any {
			it is ReaderTransitionCommand.AllocateMaterialBinding ||
				it is ReaderTransitionCommand.PrepareFrameTarget
		}, "C407.Turn.MaterialContinuation")
		val continuation = fixture.commands.drop(beforeSettlement)
		assertEquals(listOf("allocation", "raster", "deck"), continuation.mapNotNull {
			when (it) {
				is ReaderTransitionCommand.AllocateMaterialBinding -> "allocation"
				is ReaderTransitionCommand.RequestRasterPreparation -> "raster"
				is ReaderTransitionCommand.ReserveDeck -> "deck"
				else -> null
			}
		}, "C407.Turn.MaterialStages")
		assertEquals(turn, assertNotNull(fixture.deck, "C407.Turn.DeckReserved").transitionId,
			"C407.Turn.DeckTransition")
		assertNotNull(fixture.deck?.allocation, "C407.Turn.DeckAllocation")
		assertTrue(fixture.deck?.binding != predecessor.binding, "C407.Turn.SuccessorMaterial")
		assertEquals(1, fixture.successorPublications)
		assertEquals(predecessor.binding, fixture.physical?.binding)
		// Actual terminal declaration/frame callbacks belong to Curl, not this Native
		// fixture. Starting material work is not terminal display or next-turn proof.
		assertTrue(fixture.input.complete(gesture, ReaderPageGestureTerminalOutcome.CommittedForward))
		assertFalse(fixture.input.complete(gesture, ReaderPageGestureTerminalOutcome.CommittedForward))
	}

	@Test
	fun settledOriginalTurnCannotSubstituteOrdinaryNativeDeclarationForCurlTerminalFrame() {
		val fixture = ContinuousFixture()
		fixture.bootstrap()
		val predecessor = assertNotNull(fixture.physical)
		val gesture = assertNotNull(fixture.input.dispatchPointer(
			ReaderPageHostPointerEvent.Down(900f, 400f, 150L)).gestureId)
		assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture), fixture.input.dispatchPointer(
			ReaderPageHostPointerEvent.Move(100f, 400f, 8f)).route)
		fixture.beginOriginalClaimedTurn(gesture)
		assertEquals(ReaderPagePointerRoute.Curl(gesture),
			fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up).route,
			"C407.NativeVeto.PointerUp")
		fixture.returnSemantic(settlement = true)
		assertEquals(1, fixture.coordinator.snapshot().consumedSettlementCount,
			"C407.NativeVeto.SettlementConsumed")
		val turnDeck = assertNotNull(fixture.deck, "C407.NativeVeto.DeckReserved")
		assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement, turnDeck.transitionId.operation,
			"C407.NativeVeto.SuccessorDeck")
		fixture.returnDeckReady(expectDeclarationAccepted = false)
		assertEquals(1, fixture.frames.requested.size)
		assertEquals(predecessor.owner, fixture.physical?.owner)
		assertEquals(1, fixture.coordinator.snapshot().consumedSettlementCount)
		assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement, fixture.coordinator.snapshot().activeOperation)
	}

	/** Prospective original-component contract; MAIN must qualify RED before success code.
	 * The source is controlled at the renderer boundary, never a live-GL assertion. */
	@Test
	fun typedOriginalCurlTerminalCallbackMustPublishAtomicallyBeforeNextPointer() {
		val fixture = ContinuousFixture()
		val (predecessor, gesture) = fixture.startSettledTurn()
		fixture.returnCurlDeckReady(predecessor, gesture)
		val request = assertNotNull(fixture.frames.terminalRequests.singleOrNull(), "C407.Terminal.Requested")
		val target = assertIs<ReaderTransitionFrameTarget.CurlSettlementTerminalFrame>(
			fixture.commands.filterIsInstance<ReaderTransitionCommand.RequestFramePresentation>().last().target,
			"C407.Terminal.TypedTarget")
		assertEquals(request.second, target.specification, "C407.Terminal.ExactRequestedSpecification")
		assertEquals(predecessor.resource.key, target.specification.terminalIdentity?.sourceDeckKey,
			"C407.Terminal.ExactSourceDeck")
		assertEquals(predecessor.owner, fixture.physical?.owner, "C407.Terminal.RetainedBeforeFrame")
		assertEquals(ReaderTransitionResourceState.Owned,
			fixture.coordinator.releaseStateOf(predecessor.resource.key), "C407.Terminal.PredecessorOwned")
		var exactAtomicAckObserved = false
		fixture.beforeSuccessorAck = { applied ->
			assertEquals(fixture.deck?.binding, applied.publishedBinding, "C407.Terminal.AtomicBinding")
			assertEquals(applied.publishedOwner, fixture.physical?.owner, "C407.Terminal.AtomicPhysicalOwner")
			assertEquals(applied.finalPhysicalInputLease, fixture.physical?.lease, "C407.Terminal.AtomicPhysicalInput")
			assertEquals(ReaderTransitionResourceState.Owned,
				fixture.coordinator.releaseStateOf(predecessor.resource.key), "C407.Terminal.PredecessorBeforeAck")
			exactAtomicAckObserved = true
		}
		fixture.frames.present(request.first)
		val successorOwner = assertIs<ReaderPresentationFrameOwner.NativePage>(fixture.physical?.owner,
			"C407.Terminal.SuccessorNativeOwner")
		assertTrue(successorOwner.proof.binding == fixture.deck?.binding &&
			successorOwner.proof.rasterGeneration == fixture.deck?.allocation?.rasterGeneration &&
			successorOwner.proof.textureGeneration == fixture.deck?.allocation?.textureGeneration &&
			successorOwner.proof.presentedFrame == request.first,
			"C407.Terminal.SuccessorNativeProof")
		assertTrue(exactAtomicAckObserved, "C407.Terminal.AtomicCommand")
		assertEquals(fixture.deck?.binding, fixture.physical?.binding, "C407.Terminal.SuccessorBinding")
		assertEquals(ReaderTransitionResourceState.Released,
			fixture.coordinator.releaseStateOf(predecessor.resource.key), "C407.Terminal.PredecessorAfterAck")
		assertTrue(fixture.input.complete(gesture, ReaderPageGestureTerminalOutcome.CommittedForward),
			"C407.Terminal.PointerCompletion")
		assertEquals(ReaderPagePointerRoute.Content, fixture.input.dispatchPointer(
			ReaderPageHostPointerEvent.Down(20f, 30f, 801L)).route, "C407.Terminal.NextPointer")
		fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up)
		assertEquals(fixture.physical?.binding,
			assertNotNull(fixture.input.takeDelayedTap(801L), "C407.Terminal.NextTap").continuationIdentity.binding,
			"C407.Terminal.NextBinding")
		assertNull(fixture.coordinator.snapshot().activeOperation, "C407.Terminal.Complete")
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth, "C407.Terminal.SingleDrain")
	}

	@Test
	fun typedOriginalCurlTerminalMustRecheckCurrentnessBeforeOriginalFrameRequest() {
		val fixture = ContinuousFixture()
		val (predecessor, gesture) = fixture.startSettledTurn()
		fixture.invalidateTerminalAtPresentation = true
		fixture.returnCurlDeckReady(predecessor, gesture)
		assertEquals(1, fixture.terminalPreparationAcceptedCount, "C407.Terminal.PreparedBeforeInvalidation")
		assertTrue(fixture.frames.terminalRequests.isEmpty(), "C407.Terminal.StaleBeforeRequest")
		assertEquals(predecessor.owner, fixture.physical?.owner, "C407.Terminal.StaleRequestRetainsOwner")
		assertEquals(1, fixture.successorPublications, "C407.Terminal.StaleRequestCannotPublish")
	}

	@Test
	fun typedOriginalCurlTerminalMustRecheckCurrentnessAtOriginalFrameCallback() {
		val fixture = ContinuousFixture()
		val (predecessor, gesture) = fixture.startSettledTurn()
		fixture.returnCurlDeckReady(predecessor, gesture)
		val request = assertNotNull(fixture.frames.terminalRequests.singleOrNull(), "C407.Terminal.CallbackRequested")
		fixture.invalidateTerminalCandidate()
		fixture.frames.present(request.first)
		fixture.frames.present(request.first)
		assertEquals(predecessor.owner, fixture.physical?.owner, "C407.Terminal.StaleCallbackRetainsOwner")
		assertEquals(1, fixture.successorPublications, "C407.Terminal.StaleCallbackCannotPublish")
		assertEquals(1, fixture.frames.cancelled.count { it == request.first }, "C407.Terminal.StaleCallbackRetired")
	}

	@Test
	fun typedOriginalCurlTerminalCancellationMustRetireExactCallbackBeforeDeck() {
		val fixture = ContinuousFixture()
		val (predecessor, gesture) = fixture.startSettledTurn()
		fixture.returnCurlDeckReady(predecessor, gesture)
		val request = assertNotNull(fixture.frames.terminalRequests.singleOrNull(), "C407.Terminal.CancelRequested")
		val lease = assertNotNull(fixture.deck, "C407.Terminal.CancelDeck")
		fixture.coordinator.enqueue(ReaderTransitionFact.Intent(lease.transitionId, ReaderCancelIntent))
		fixture.coordinator.enqueue(ReaderTransitionFact.Intent(lease.transitionId, ReaderCancelIntent))
		assertEquals(listOf(request.first), fixture.frames.cancelled, "C407.Terminal.CancelExactRequest")
		assertEquals(1, fixture.frameCancellationCountAtDeckRelease[lease.resourceKey], "C407.Terminal.CancelBeforeDeck")
		fixture.frames.present(request.first)
		assertEquals(predecessor.owner, fixture.physical?.owner, "C407.Terminal.CancelRetainsOwner")
		assertEquals(1, fixture.successorPublications, "C407.Terminal.CancelNoLatePublish")
	}

	@Test
	fun originalFrameSourceWithoutTerminalAdapterMustRejectInsteadOfGenericNativeFallback() {
		val fixture = ContinuousFixture()
		val (predecessor, gesture) = fixture.startSettledTurn()
		val candidate = ReaderCurlSettlementTerminalPresentationCandidate(fixture.curlDeclaration(predecessor, gesture))
		var genericRequests = 0
		var callbacks = 0
		val source = object : ReaderNativePagePresentedFrameSource {
			override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
				genericRequests++
				return 91L
			}
			override fun cancelPresentedFrameRequest(requestId: Long) = true
		}
		assertEquals(karacken.curl.PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
			source.requestCurlSettlementTerminalPresentedFrame(candidate) { callbacks++ },
			"C407.Terminal.MissingAdapterRejected")
		assertEquals(0, genericRequests, "C407.Terminal.NoGenericFallback")
		assertEquals(0, callbacks, "C407.Terminal.NoSyntheticCallback")
	}

	@Test
	fun typedOriginalCurlTerminalDeclarationRequiresIndependentSourceDeckAssociation() {
		for (missing in listOf(true, false)) {
			val fixture = ContinuousFixture()
			val (predecessor, gesture) = fixture.startSettledTurn()
			val lease = assertNotNull(fixture.deck, "C407.Terminal.AssociationDeck")
			val specification = fixture.curlDeclaration(predecessor, gesture)
			val invalid = if (missing) specification.copy(terminalIdentity = null) else specification.copy(
				terminalIdentity = assertNotNull(specification.terminalIdentity).copy(sourceDeckKey = lease.resourceKey))
			assertIs<ReaderPortCommandResult.Rejected>(fixture.coordinator.enqueueFrameTargetDeclaration(
				invalid, lease.resourceKey), "C407.Terminal.SourceAssociationRequired")
			assertTrue(fixture.frames.terminalRequests.isEmpty(), "C407.Terminal.BadAssociationNoRequest")
			assertEquals(predecessor.owner, fixture.physical?.owner, "C407.Terminal.BadAssociationRetainsOwner")
		}
	}

	@Test
	fun repeatedApplicableRelocationsPublishDespiteOriginalPendingTapInput() {
		val fixture = ContinuousFixture()
		fixture.bootstrap()
		repeat(3) { cycle ->
			val predecessor = assertNotNull(fixture.physical)
			val downTime = 200L + cycle
			val down = fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 30f, downTime))
			assertEquals(ReaderPagePointerRoute.Content, down.route)
			assertEquals(ReaderPagePointerRoute.Content,
				fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
			assertEquals(1, fixture.router.trackedSequenceCount())
			fixture.beginRelocation()
			assertEquals(predecessor.owner, fixture.physical?.owner)
			assertNull(fixture.input.takeDelayedTap(downTime))
			assertEquals(listOf(assertNotNull(down.gestureId) to ReaderPageGestureTerminalOutcome.CancelledLifecycle),
				fixture.terminals.takeLast(1))
			fixture.returnSemantic()
			fixture.returnDeckReady()
			val request = fixture.frames.requested.last()
			assertEquals(predecessor.owner, fixture.physical?.owner,
				"Preparation cannot substitute for the actual presented callback")
			fixture.frames.present(request)
			val successor = assertNotNull(fixture.physical)
			assertTrue(successor.binding != predecessor.binding)
			assertEquals(fixture.deck?.binding, successor.binding)
			assertIs<ReaderPresentationFrameOwner.NativePage>(successor.owner)
			val census = fixture.coordinator.snapshot()
			assertNull(census.activeOperation)
			assertEquals(0, census.mailboxSize)
			assertEquals(1, census.ownedResourceCount)
			assertEquals(0, census.releaseCommandIssuedCount)
			assertEquals(0, fixture.router.trackedSequenceCount())
			assertEquals(1, census.maxAdvanceDepth)
			val nextDownTime = 300L + cycle
			assertEquals(ReaderPagePointerRoute.Content, fixture.input.dispatchPointer(
				ReaderPageHostPointerEvent.Down(20f, 30f, nextDownTime)).route)
			fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up)
			val tap = assertNotNull(fixture.input.takeDelayedTap(nextDownTime))
			assertEquals(successor.binding, tap.continuationIdentity.binding)
			assertTrue(fixture.input.completeDelayedTap(tap.gestureId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction))
		}
		// Jump is an actual typed public intent. This test does not relabel it WordSync.
		assertEquals(4, fixture.successorPublications)
		assertEquals(4, fixture.frames.requested.size)
		assertEquals(4, fixture.commands.filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>().size)
		assertEquals(4, fixture.commands.filterIsInstance<ReaderTransitionCommand.RequestRasterPreparation>().size)
		assertEquals(4, fixture.commands.filterIsInstance<ReaderTransitionCommand.ReserveDeck>().size)
		assertEquals(4, fixture.commands.filterIsInstance<ReaderTransitionCommand.PrepareFrameTarget>().size)
		assertEquals(4, fixture.commands.filterIsInstance<ReaderTransitionCommand.RequestFramePresentation>().size)
	}

	@Test
	fun lateOriginalFrameCannotPublishAfterCancelOrClose() {
		for (close in listOf(false, true)) {
			val fixture = ContinuousFixture()
			fixture.bootstrap()
			val predecessor = assertNotNull(fixture.physical)
			fixture.beginRelocation()
			fixture.returnSemantic()
			fixture.returnDeckReady()
			val pending = fixture.frames.requested.last()
			val publications = fixture.successorPublications
			val terminal = if (close) ReaderTransitionFact.PublicationClosed(null)
				else ReaderTransitionFact.Intent(null, ReaderCancelIntent)
			fixture.coordinator.enqueue(terminal)
			fixture.coordinator.enqueue(terminal)
			if (close) fixture.closeOriginalConsumers()
			val pendingKey = assertNotNull(fixture.deck).resourceKey
			assertEquals(ReaderTransitionResourceState.Released, fixture.coordinator.releaseStateOf(pendingKey))
			assertEquals(1, fixture.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>()
				.count { it.key == pendingKey })
			// The original cancellation command reaches the existing publisher before
			// task4 resource retirement; dispose is only the final consumer fence.
			assertEquals(1, fixture.frames.cancelled.count { it == pending })
			fixture.frames.present(pending)
			fixture.frames.present(pending)
			assertEquals(1, fixture.frames.cancelled.count { it == pending })
			assertEquals(publications, fixture.successorPublications)
			if (close) {
				assertTrue(fixture.coordinator.snapshot().releaseOnlySink)
				assertEquals(ReaderPagePointerRoute.Ignore, fixture.input.dispatchPointer(
					ReaderPageHostPointerEvent.Down(20f, 30f, 400L)).route)
			} else {
				assertEquals(predecessor.owner, fixture.physical?.owner)
				assertFalse(fixture.coordinator.snapshot().releaseOnlySink)
			}
			assertEquals(0, fixture.router.trackedSequenceCount())
		}
	}

	@Test
	fun keyedCancellationMustRetireOriginalPhysicalRequestBeforeAnyLateFrame() {
		val fixture = ContinuousFixture()
		fixture.bootstrap()
		val predecessor = assertNotNull(fixture.physical)
		fixture.beginRelocation()
		fixture.returnSemantic()
		fixture.returnDeckReady()
		val work = assertNotNull(fixture.deck)
		val pending = fixture.frames.requested.last()
		val publications = fixture.successorPublications
		val predecessorId = assertNotNull(predecessor.resource.key.owningTransitionIdOrNull)
		fixture.coordinator.enqueue(ReaderTransitionFact.Intent(predecessorId, ReaderCancelIntent))
		assertTrue(fixture.frames.cancelled.isEmpty(), "A predecessor key cannot cancel the successor request")
		fixture.coordinator.enqueue(ReaderTransitionFact.Intent(work.transitionId, ReaderCancelIntent))
		fixture.coordinator.enqueue(ReaderTransitionFact.Intent(work.transitionId, ReaderCancelIntent))
		assertEquals(listOf(pending), fixture.frames.cancelled,
			"Logical cancellation must retire the exact original physical request before a late callback")
		assertEquals(1, fixture.frameCancellationCountAtDeckRelease[work.resourceKey],
			"The original physical callback must retire before that deck's release callback")
		assertEquals(ReaderTransitionResourceState.Released, fixture.coordinator.releaseStateOf(work.resourceKey))
		assertEquals(predecessor.owner, fixture.physical?.owner)
		fixture.frames.present(pending)
		fixture.frames.present(pending)
		assertEquals(listOf(pending), fixture.frames.cancelled)
		assertEquals(publications, fixture.successorPublications)
	}

	@Test
	fun applicableWordSyncBoundaryMasksRemainConstructibleDuringOriginalClaimedCurl() {
		val fixture = ContinuousFixture()
		fixture.bootstrap()
		val physical = assertNotNull(fixture.physical)
		val binding = physical.binding
		val down = fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Down(900f, 400f, 500L))
		val gesture = assertNotNull(down.gestureId)
		assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture), fixture.input.dispatchPointer(
			ReaderPageHostPointerEvent.Move(100f, 400f, 8f)).route)
		val profile = ReaderPlayLikeCurlRasterProfile("public-continuous-fixture",
			ReaderPlayLikeCurlOrientation.Portrait, ReaderPageBitmapQuality.Balanced,
			pageCount = 8, rasterGeneration = assertNotNull(binding.rasterGeneration))
		var targetCount = 0
		var maskCount = 0
		var previousProof: ReaderWhispersyncNativePresentationProof? = null
		repeat(3) { boundary ->
			val receipt = publicAnchorReceipt(binding, boundary.toLong() + 1L)
			val proof = receipt.nativePresentationProof()
			previousProof?.let { assertTrue(proof.isAdmissibleReplacementFor(it)) }
			val targets = assertNotNull(readerWhispersyncNativeOverlayTargets(receipt, proof,
				binding.foliateSessionId, profile, currentOrdinal = 1,
				textureGeneration = assertNotNull(binding.textureGeneration)))
			targetCount += targets.size
			for (target in targets) {
				val mask = assertNotNull(readerWhispersyncHighlightMask(receipt, target, 120, 80, 0x66336699))
				try { assertFalse(mask.isRecycled); maskCount++ } finally { mask.recycle() }
			}
			previousProof = proof
			assertEquals(physical, fixture.physical)
			assertEquals(1, fixture.router.trackedSequenceCount())
		}
		assertEquals(3, targetCount)
		assertEquals(3, maskCount)
		assertEquals(1, fixture.successorPublications)
		// These are original canonical proof/target/mask consumers, not a scheduler
		// or surface-publication substitute. Actual highlight-during-curl display is open.
		fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up)
		assertTrue(fixture.input.complete(gesture, ReaderPageGestureTerminalOutcome.CancelledByUser))
	}

	@Test
	fun delayedOriginalRasterKeepsOnePreparationAndPublishesOnceWhenItsCallbackReturns() {
		val fixture = ContinuousFixture()
		fixture.bootstrap()
		val predecessor = assertNotNull(fixture.physical)
		fixture.delayRaster = true
		val before = fixture.commands.size
		fixture.beginRelocation()
		fixture.returnSemantic()
		repeat(3) { cycle ->
			fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 30f, 600L + cycle))
			fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up)
			assertEquals(predecessor.owner, fixture.physical?.owner)
		}
		assertEquals(1, fixture.commands.drop(before)
			.filterIsInstance<ReaderTransitionCommand.RequestRasterPreparation>().size)
		assertEquals(0, fixture.commands.drop(before)
			.filterIsInstance<ReaderTransitionCommand.ReserveDeck>().size)
		fixture.returnRaster()
		fixture.returnDeckReady()
		fixture.frames.present(fixture.frames.requested.last())
		assertEquals(2, fixture.successorPublications)
		assertEquals(1, fixture.commands.drop(before)
			.filterIsInstance<ReaderTransitionCommand.ReserveDeck>().size)
		assertEquals(1, fixture.coordinator.snapshot().maxAdvanceDepth)
		assertEquals(0, fixture.router.trackedSequenceCount())
	}

	/** These capture-only consumers exercise the ORIGINAL coordinator/FIFO/barrier.
	 * They cannot write common state or stand in for Root/Foliate/GL acceptance.
	 * The metadata prerequisite qualifies only the unavailable consumer seam. */
	@Test
	fun originalAcceptedPublicationPreservesExactCommandResultAfterJournalAcceptanceBeforeRelease() {
		val accepted = mutableListOf<Pair<ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied>>()
		lateinit var fixture: ContinuousFixture
		var predecessor: ReaderOwnerAndInputPublicationSnapshot? = null
		fixture = ContinuousFixture { command, applied ->
			assertTrue(android.os.Looper.myLooper() == android.os.Looper.getMainLooper(), "A407.Accepted.MainThread")
			assertTrue(fixture.coordinator.snapshot().advancing && fixture.coordinator.snapshot().maxAdvanceDepth == 1,
				"A407.Accepted.OriginalDrain")
			if (command is ReaderTransitionCommand.CommitOwnerAndInputLease) {
				assertNull(fixture.coordinator.snapshot().activeOperation, "A407.Accepted.JournalCommitted")
				predecessor?.let { assertTrue(fixture.coordinator.releaseStateOf(it.resource.key) ==
					ReaderTransitionResourceState.Owned, "A407.Accepted.BeforePredecessorRelease") }
			}
			accepted += command to applied
		}
		fixture.bootstrap()
		predecessor = assertNotNull(fixture.physical, "A407.Accepted.Predecessor")
		fixture.beginRelocation()
		assertTrue(accepted.size == 2 && accepted.last().first is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease,
			"A407.Accepted.RetainedPublication")
		fixture.returnSemantic()
		fixture.returnDeckReady()
		var originalResult: ReaderOwnerAndInputPublicationResult.Applied? = null
		fixture.beforeSuccessorAck = { result ->
			assertTrue(accepted.size == 2, "A407.Accepted.NoProjectionInsidePort")
			originalResult = result
		}
		fixture.frames.present(fixture.frames.requested.last())
		assertTrue(accepted.size == 3 && accepted.last().first === fixture.lastSuccessorCommand &&
			accepted.last().second === originalResult, "A407.Accepted.ImmutableOriginalContext")
		assertTrue(fixture.coordinator.releaseStateOf(assertNotNull(predecessor, "A407.Accepted.PredecessorAfterReturn").resource.key) ==
			ReaderTransitionResourceState.Released, "A407.Accepted.ReleaseAfterConsumer")
	}

	@Test
	fun originalAcceptedPublicationIgnoresArbitraryDuplicateStaleAndWrongIdentityFacts() {
		val accepted = mutableListOf<ReaderOwnerAndInputPublicationResult.Applied>()
		val fixture = ContinuousFixture { _, applied -> accepted += applied }
		fixture.bootstrap()
		val original = accepted.single()
		val physical = fixture.physical
		val wrong = original.copy(publicationIdentity = paige.navic.reader.ReaderOwnerAndInputPublicationIdentity(
			original.publicationIdentity.value + 1L))
		val stale = original.copy(transitionId = original.transitionId.copy(
			coordinatorEpoch = original.transitionId.coordinatorEpoch + 1L))
		for (result in listOf(original, original, wrong, stale)) fixture.coordinator.enqueue(result.asUntrustedFact())
		assertTrue(accepted.size == 1 && fixture.physical == physical, "A407.Accepted.UntrustedFactsNoProjection")
		assertNull(fixture.coordinator.snapshot().activeOperation, "A407.Accepted.UntrustedFactsNoReopen")
	}

	@Test
	fun originalAcceptedPublicationCannotLoseTrustedResultToSynchronousExactCounterfeit() {
		val accepted = mutableListOf<ReaderOwnerAndInputPublicationResult.Applied>()
		val fixture = ContinuousFixture { _, applied -> accepted += applied }
		var original: ReaderOwnerAndInputPublicationResult.Applied? = null
		fixture.beforeSuccessorAck = { applied ->
			original = applied
			fixture.coordinator.enqueue(applied.copy().asUntrustedFact())
			assertTrue(accepted.isEmpty(), "A407.Accepted.CounterfeitQueuedNotReentrant")
		}
		fixture.bootstrap()
		assertTrue(accepted.size == 1 && accepted.single() === original,
			"A407.Accepted.CounterfeitCannotConsumeOriginalAuthority")
		assertTrue(fixture.coordinator.snapshot().maxAdvanceDepth == 1, "A407.Accepted.CounterfeitSingleDrain")
	}

	@Test
	fun originalAcceptedPublicationCannotProjectIgnoredReturnAfterSynchronousCloseOrSupersession() {
		for (close in listOf(true, false)) {
			val accepted = mutableListOf<ReaderOwnerAndInputPublicationResult.Applied>()
			val fixture = ContinuousFixture { _, applied -> accepted += applied }
			fixture.bootstrap()
			fixture.beginRelocation()
			fixture.returnSemantic()
			fixture.returnDeckReady()
			val before = accepted.size
			var obsolete: ReaderOwnerAndInputPublicationResult.Applied? = null
			fixture.beforeSuccessorAck = { applied ->
				obsolete = applied
				if (close) fixture.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
				else fixture.beginRelocation()
			}
			fixture.frames.present(fixture.frames.requested.last())
			assertTrue(accepted.none { it === obsolete }, "A407.Accepted.IgnoredReturnNoProjection")
			if (close) assertTrue(accepted.size == before && fixture.coordinator.snapshot().releaseOnlySink,
				"A407.Accepted.CloseCannotProject")
			else assertTrue(fixture.coordinator.snapshot().activeOperation == ReaderTransitionOperation.ExternalSemanticRelocation,
				"A407.Accepted.SupersessionOwnsContinuation")
			assertTrue(fixture.coordinator.snapshot().maxAdvanceDepth == 1, "A407.Accepted.SupersessionSingleDrain")
		}
	}

	@Test
	fun originalAcceptedPublicationRejectedSuccessorRetainsReadablePredecessorWithoutProjection() {
		val accepted = mutableListOf<ReaderOwnerAndInputPublicationResult.Applied>()
		val fixture = ContinuousFixture { _, applied -> accepted += applied }
		fixture.bootstrap()
		val predecessor = assertNotNull(fixture.physical, "A407.Accepted.RejectedPredecessor")
		fixture.beginRelocation()
		fixture.returnSemantic()
		fixture.returnDeckReady()
		val before = accepted.size
		fixture.rejectSuccessorPublication = true
		fixture.frames.present(fixture.frames.requested.last())
		assertTrue(accepted.size == before && fixture.physical?.owner == predecessor.owner &&
			fixture.physical?.binding == predecessor.binding, "A407.Accepted.RejectedNoProjection")
		assertTrue(fixture.coordinator.releaseStateOf(predecessor.resource.key) == ReaderTransitionResourceState.Owned,
			"A407.Accepted.RejectedReadablePredecessorOwned")
	}

	@Test
	fun originalAcceptedPublicationConsumerReentryRemainsFifoAndDoesNotBorrowLatestContext() {
		val accepted = mutableListOf<Pair<ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied>>()
		lateinit var fixture: ContinuousFixture
		var reenter = false
		fixture = ContinuousFixture { command, applied ->
			accepted += command to applied
			if (reenter && command is ReaderTransitionCommand.CommitOwnerAndInputLease) {
				reenter = false
				fixture.beginRelocation()
				assertTrue(accepted.last().first === command && accepted.last().second === applied,
					"A407.Accepted.ConsumerReentryDeferred")
			}
		}
		fixture.bootstrap()
		fixture.beginRelocation()
		fixture.returnSemantic()
		fixture.returnDeckReady()
		reenter = true
		fixture.frames.present(fixture.frames.requested.last())
		assertTrue(accepted.size == 4 && accepted[2].first === fixture.lastSuccessorCommand &&
			accepted.last().first is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease,
			"A407.Accepted.ConsumerReentryOriginalContexts")
		assertTrue(fixture.coordinator.snapshot().activeOperation == ReaderTransitionOperation.ExternalSemanticRelocation &&
			fixture.coordinator.snapshot().mailboxSize == 0 && fixture.coordinator.snapshot().maxAdvanceDepth == 1,
			"A407.Accepted.ConsumerReentrySingleDrain")
	}

	@Test
	fun originalAcceptedPublicationCarriesPhysicalNarrowingRatherThanRequestedNativeAuthority() {
		val accepted = mutableListOf<Pair<ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied>>()
		val fixture = ContinuousFixture { command, applied -> accepted += command to applied }
		fixture.narrowNativePublication = true
		fixture.bootstrap()
		val (command, applied) = accepted.single()
		assertTrue(command is ReaderTransitionCommand.CommitOwnerAndInputLease &&
			command.requestedLease is ReaderTransitionInputLease.NativePage &&
			applied.finalPhysicalInputLease == ReaderTransitionInputLease.ChromeOnly &&
			fixture.physical?.lease == applied.finalPhysicalInputLease,
			"A407.Accepted.ExactPhysicalNarrowing")
		assertTrue(paige.navic.reader.readerTransitionPresentationInputPolicy(applied.publishedOwner,
			applied.publishedBinding, applied.finalPhysicalInputLease) == ReaderPresentationInputPolicy.ChromeOnly,
			"A407.Accepted.ProjectionCannotBroaden")
	}

	@Test
	fun originalAcceptedTypedCurlTerminalCertifiesExactNativeSuccessorNotSyntheticCurlOwner() {
		val accepted = mutableListOf<Pair<ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied>>()
		val fixture = ContinuousFixture { command, applied -> accepted += command to applied }
		val (predecessor, gesture) = fixture.startSettledTurn()
		fixture.returnCurlDeckReady(predecessor, gesture)
		val request = assertNotNull(fixture.frames.terminalRequests.singleOrNull(), "A407.Accepted.TerminalOriginalRequest")
		val target = assertIs<ReaderTransitionFrameTarget.CurlSettlementTerminalFrame>(
			fixture.commands.filterIsInstance<ReaderTransitionCommand.RequestFramePresentation>().last().target,
			"A407.Accepted.TerminalOriginalTarget")
		fixture.frames.present(request.first)
		val (command, applied) = accepted.last()
		val commit = assertIs<ReaderTransitionCommand.CommitOwnerAndInputLease>(command, "A407.Accepted.TerminalOriginalCommit")
		val owner = assertIs<ReaderPresentationFrameOwner.NativePage>(applied.publishedOwner, "A407.Accepted.TerminalNativeSuccessor")
		assertTrue(accepted.size == 3 && commit === fixture.lastSuccessorCommand &&
			commit.targetHandle == target.handle && commit.preparedFrameResource == target.resource &&
			target.specification == request.second && target.specification.terminalIdentity?.sourceDeckKey == predecessor.resource.key &&
			owner.proof.binding == target.specification.binding && owner.proof.presentedFrame == request.first &&
			applied.finalPhysicalInputLease is ReaderTransitionInputLease.NativePage,
			"A407.Accepted.TerminalExactSourceAndSuccessor")
		assertTrue(paige.navic.reader.readerTransitionPresentationInputPolicy(applied.publishedOwner,
			applied.publishedBinding, applied.finalPhysicalInputLease) is ReaderPresentationInputPolicy.NativePage,
			"A407.Accepted.TerminalNativeInputProjection")
	}

	/** Prospective ORIGINAL consumer failure control. A real accepted barrier result
	 * still needs its lawful release handling; throwing is not successful projection. */
	@Test
	fun originalAcceptedPublicationConsumerFailurePropagatesWithoutLosingOriginalRelease() {
		var failConsumer = false
		val originalFailure = IllegalStateException("A407.Accepted.ConsumerFailure")
		val fixture = ContinuousFixture { command, _ ->
			if (failConsumer && command is ReaderTransitionCommand.CommitOwnerAndInputLease) throw originalFailure
		}
		fixture.bootstrap()
		val predecessor = assertNotNull(fixture.physical, "A407.Accepted.FailurePredecessor")
		fixture.beginRelocation()
		fixture.returnSemantic()
		fixture.returnDeckReady()
		failConsumer = true
		val observedFailure = try {
			fixture.frames.present(fixture.frames.requested.last())
			null
		} catch (failure: Throwable) { failure }
		assertTrue(observedFailure === originalFailure, "A407.Accepted.ExactConsumerFailurePropagates")
		org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		assertTrue(fixture.coordinator.releaseStateOf(predecessor.resource.key) == ReaderTransitionResourceState.Released,
			"A407.Accepted.ConsumerFailurePreservesOriginalPhysicalRelease")
		assertTrue(fixture.coordinator.snapshot().activeOperation == null &&
			fixture.coordinator.snapshot().mailboxSize == 0 && fixture.coordinator.snapshot().maxAdvanceDepth == 1,
			"A407.Accepted.ConsumerFailureLeavesLawfulTerminalDrain")
	}

	/** Prepared COMMON projection contracts. Only the original accepted FIFO callback
	 * invokes the prospective pure API; controlled semantic/material input stays UNIT
	 * scoped. Missing API is a bounded seam RED, not qualification of assertions below. */
	@Test
	fun originalAcceptedCallbacksProjectOwningCommonStateAcrossTwoTypedSuccessors() {
		val owning = OwningPublicationFixture()
		val fixture = owning.original
		fixture.bootstrap()
		repeat(2) { cycle ->
			val predecessor = assertNotNull(fixture.physical, "A407.Owning.Predecessor")
			val gesture = assertNotNull(fixture.input.dispatchPointer(
				ReaderPageHostPointerEvent.Down(900f, 400f, 900L + cycle)).gestureId,
				"A407.Owning.OriginalPointer")
			assertTrue(fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Move(100f, 400f, 8f)).route ==
				ReaderPagePointerRoute.ClaimCurl(gesture), "A407.Owning.OriginalClaim")
			fixture.beginOriginalClaimedTurn(gesture)
			assertTrue(owning.model.controller.state.presentationDecision.frameOwner == predecessor.owner &&
				owning.model.controller.state.presentation.binding == predecessor.binding &&
				owning.model.controller.state.presentationDecision.inputPolicy ==
				paige.navic.reader.readerTransitionPresentationInputPolicy(predecessor.owner,
					predecessor.binding, assertNotNull(fixture.physical, "A407.Owning.RetainedPhysical").lease),
				"A407.Owning.RetainedOriginalClaimProjection")
			fixture.input.dispatchPointer(ReaderPageHostPointerEvent.Up)
			fixture.returnSemantic(settlement = true)
			assertEquals(1, fixture.coordinator.snapshot().consumedSettlementCount,
				"A407.Owning.SettlementConsumedDuringOriginalTurn")
			fixture.returnCurlDeckReady(predecessor, gesture)
			val request = assertNotNull(fixture.frames.terminalRequests.lastOrNull(), "A407.Owning.TypedOriginalRequest")
			fixture.frames.present(request.first)
			assertTrue(fixture.input.complete(gesture, ReaderPageGestureTerminalOutcome.CommittedForward),
				"A407.Owning.OriginalPointerCompletion")
			assertTrue(owning.model.controller.state.chrome.currentLocator?.pageIndex == cycle + 1 &&
				owning.model.controller.state.chapterProgress.pageIndex == cycle + 1,
				"A407.Owning.SemanticProgressSurvivesPhysicalProjection")
			assertTrue(fixture.physical?.binding != predecessor.binding && fixture.coordinator.snapshot().activeOperation == null,
				"A407.Owning.TypedSuccessorTerminal")
		}
		// The original census reports only the active transition, not a lifetime total.
		assertTrue(owning.accepted.size == 5 && fixture.coordinator.snapshot().consumedSettlementCount == 0 &&
			fixture.coordinator.snapshot().activeTransitionsRegistered == 3,
			"A407.Owning.OneBootstrapTwoRetainedTwoTypedSuccessors")
	}

	@Test
	fun originalPublicationCurrencyUsesAwaitingCommandAndExpiresAfterCommitOrClose() {
		for (closeBeforeAck in listOf(false, true)) {
			val currencies = mutableListOf<Pair<ReaderTransitionCommand, () -> Boolean>>()
			var accepted = 0
			var originalCurrencyFailure: Throwable? = null
			val fixture = ContinuousFixture(acceptedPublicationConsumer = { _, _ ->
				accepted++
				assertTrue(currencies.all { !it.second() }, "A407.Input.Currency.EndedBeforeAcceptedConsumer")
			})
			fixture.observePublicationCurrency = { command ->
				try {
					val currency = assertNotNull(fixture.coordinator.capturePublicationCommandCurrency(command),
						"A407.Input.Currency.OriginalPendingCommandRequired")
					assertTrue(currency(), "A407.Input.Currency.OriginalPendingCommandCurrent")
					val issued = assertIs<ReaderTransitionCommand.CommitOwnerAndInputLease>(command)
					assertNull(fixture.coordinator.capturePublicationCommandCurrency(issued.copy(
						transitionId = issued.transitionId.copy())), "A407.Input.Currency.CopiedTransitionRejected")
					val mismatchedBinding = issued.binding.copy(profileGeneration = issued.binding.profileGeneration + 1L)
					val nativeOwner = assertIs<ReaderPresentationFrameOwner.NativePage>(issued.owner,
						"A407.Input.Currency.MismatchedBindingRejected")
					// Keep the command constructor's owner/binding invariant valid so the
					// original currency check, not construction, rejects this mismatch.
					assertNull(fixture.coordinator.capturePublicationCommandCurrency(issued.copy(
						binding = mismatchedBinding,
						owner = nativeOwner.copy(proof = nativeOwner.proof.copy(binding = mismatchedBinding)))),
						"A407.Input.Currency.MismatchedBindingRejected")
					currencies += command to currency
				} catch (failure: Throwable) {
					// The original port translates throws to rejection. Preserve this UNIT
					// assertion so a later fixture assertion cannot hide its fixed checkpoint.
					originalCurrencyFailure = failure
					throw failure
				}
			}
			if (closeBeforeAck) fixture.beforeSuccessorAck = {
				fixture.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
			}
			try {
				fixture.bootstrap()
			} catch (failure: Throwable) {
				throw originalCurrencyFailure ?: failure
			}
			originalCurrencyFailure?.let { throw it }
			assertTrue(currencies.size == 1 && !currencies.single().second() &&
				fixture.coordinator.capturePublicationCommandCurrency(currencies.single().first) == null &&
				accepted == if (closeBeforeAck) 0 else 1, "A407.Input.Currency.TerminalCannotReviveNarrowing")
		}
	}

	@Test
	fun originalAcceptedCallbackProjectsFinalChromeOnlyWithoutRequestedLeaseBroadening() {
		val owning = OwningPublicationFixture()
		owning.original.narrowNativePublication = true
		owning.original.bootstrap()
		val (command, applied) = owning.accepted.single()
		assertTrue(command is ReaderTransitionCommand.CommitOwnerAndInputLease &&
			command.requestedLease is ReaderTransitionInputLease.NativePage &&
			applied.finalPhysicalInputLease == ReaderTransitionInputLease.ChromeOnly &&
			owning.model.controller.state.presentationDecision.frameOwner == applied.publishedOwner &&
			owning.model.controller.state.presentation.binding == applied.publishedBinding &&
			owning.model.controller.state.presentationDecision.inputPolicy == ReaderPresentationInputPolicy.ChromeOnly,
			"A407.Owning.FinalPhysicalChromeOnlyProjection")
	}

	@Test
	fun originalCoordinatorRejectsCounterfeitStaleClosedAndRejectedCallbacksBeforeOwningProjection() {
		for (mode in 0..4) {
			val owning = OwningPublicationFixture()
			val fixture = owning.original
			fixture.bootstrap()
			val original = owning.accepted.single().second
			val beforeUntrusted = owning.model
			fixture.coordinator.enqueue(original.copy().asUntrustedFact())
			fixture.coordinator.enqueue(original.copy(transitionId = original.transitionId.copy(
				coordinatorEpoch = original.transitionId.coordinatorEpoch + 1L)).asUntrustedFact())
			assertTrue(owning.model === beforeUntrusted && owning.accepted.size == 1,
				"A407.Owning.UntrustedFactsCannotProject")
			// Use the original Curl ingress/ACK/typed terminal, not bootstrap's receipt path.
			val predecessor = assertNotNull(fixture.physical, "A407.Owning.NegativePredecessor")
			val gesture = assertNotNull(fixture.input.dispatchPointer(
				ReaderPageHostPointerEvent.Down(900f, 400f, 950L)).gestureId,
				"A407.Owning.NegativeOriginalPointer")
			assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture), fixture.input.dispatchPointer(
				ReaderPageHostPointerEvent.Move(100f, 400f, 8f)).route, "A407.Owning.NegativeOriginalClaim")
			fixture.beginOriginalClaimedTurn(gesture)
			assertEquals(ReaderPagePointerRoute.Curl(gesture), fixture.input.dispatchPointer(
				ReaderPageHostPointerEvent.Up).route, "A407.Owning.NegativeOriginalPointerUp")
			fixture.returnSemantic(settlement = true)
			fixture.returnCurlDeckReady(predecessor, gesture)
			val request = assertNotNull(fixture.frames.terminalRequests.singleOrNull(),
				"A407.Owning.NegativeTypedOriginalRequest")
			val before = owning.model
			val acceptedBefore = owning.accepted.size
			if (mode == 4) owning.afterProjection = { command, applied ->
				if (command is ReaderTransitionCommand.CommitOwnerAndInputLease) {
					owning.afterProjection = null
					val projected = owning.model
					fixture.beginRelocation()
					assertTrue(owning.model === projected && owning.accepted.last().first === command &&
						owning.accepted.last().second === applied,
						"A407.Owning.AcceptedConsumerReentryCannotBorrowLatestContext")
				}
			}
			fixture.rejectSuccessorPublication = mode == 0
			fixture.beforeSuccessorAck = { applied ->
				when (mode) {
					1 -> fixture.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
					2 -> fixture.beginRelocation()
					3 -> fixture.coordinator.enqueue(applied.copy().asUntrustedFact())
				}
				assertTrue(owning.model === before && owning.accepted.size == acceptedBefore,
					"A407.Owning.PortReentryCannotProjectBeforeJournal")
			}
			fixture.frames.present(request.first)
			when (mode) {
				0, 1 -> assertTrue(owning.model === before && owning.accepted.size == acceptedBefore,
					"A407.Owning.RejectedOrClosedReturnCannotProject")
				2 -> assertTrue(owning.accepted.drop(acceptedBefore).all {
					it.first is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease },
					"A407.Owning.SupersededReturnCannotBorrowSuccessorAuthority")
				3 -> assertTrue(owning.accepted.size == acceptedBefore + 1 &&
					owning.accepted.last().first === fixture.lastSuccessorCommand,
					"A407.Owning.CounterfeitCannotConsumeOriginalAcceptedCallback")
				4 -> assertTrue(owning.accepted.size == acceptedBefore + 2 &&
					owning.accepted[acceptedBefore].first === fixture.lastSuccessorCommand &&
					owning.accepted.last().first is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease &&
					fixture.coordinator.snapshot().activeOperation == ReaderTransitionOperation.ExternalSemanticRelocation,
					"A407.Owning.AcceptedConsumerReentryKeepsOrderedOriginalProjections")
			}
			assertTrue(fixture.coordinator.snapshot().maxAdvanceDepth == 1, "A407.Owning.SingleOriginalFifoDrain")
		}
	}

	/** Original Curl/barrier UNIT consumer only. This does not mount or seed Root and
	 * must be paired with the Native source-wiring regression in the normal host class. */
	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalControlledCurlAcceptsCommittedSnapshotObjectAndRejectsStructuralCopy() {
		ReaderInstalledRasterPreparationOwnershipHostTest().withMaterialFixture(legacySelection = false) { fixture ->
			fixture.start()
			fixture.presentAndCommit()
			val publication = assertNotNull(fixture.barrier.snapshot, "B407.NativeIdentity.OriginalBarrierSnapshot")
			val deck = assertNotNull(fixture.deck, "B407.NativeIdentity.OriginalDeck")
			assertTrue(fixture.curl.onInstalledOwnerAndInputPublished(publication.copy()) is ReaderPortCommandResult.Rejected,
				"B407.NativeIdentity.StructuralCopyRejectedByOriginalConsumer")
			assertTrue(fixture.curl.onInstalledOwnerAndInputPublished(publication) == ReaderPortCommandResult.Accepted &&
				fixture.barrier.snapshot === publication && fixture.curl.installedDeckFor(deck.resourceKey) === deck,
				"B407.NativeIdentity.ExactCommittedObjectAcceptedWithoutReplacement")
		}
	}

	private class OwningPublicationFixture {
		private val projection = requireOriginalPhysicalPublicationApi()
		var model = ReaderCoordinator().dispatch { open(ReaderEngineOpenRequest(
			ReaderPublicationIdentity("public-owning-fixture", resourceHref = "fixture.epub",
				kind = ReaderPublicationKind.Ebook, format = ReaderPublicationFormat.Epub),
			"https://example.invalid/fixture.epub", settings = defaultReaderSettings())) }.coordinator
			.onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.PublicationReady)).coordinator
			private set
		val accepted = mutableListOf<Pair<ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied>>()
		var afterProjection: ((ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied) -> Unit)? = null
		private var settledPage = 0
		val original = ContinuousFixture(sessionGeneration = model.controller.state.readerSessionGeneration) { command, applied ->
			val before = model
			model = projectOriginalAcceptedPublication(projection, model, command, applied)
			assertTrue(model.controller.state.presentationDecision.frameOwner == applied.publishedOwner &&
				model.controller.state.presentation.binding == applied.publishedBinding &&
				model.controller.state.presentationDecision.inputPolicy ==
				paige.navic.reader.readerTransitionPresentationInputPolicy(applied.publishedOwner,
					applied.publishedBinding, applied.finalPhysicalInputLease), "A407.Owning.ExactPhysicalProjection")
			assertTrue(model.controller.state.chrome == before.controller.state.chrome &&
				model.controller.state.chapterProgress == before.controller.state.chapterProgress &&
				model.controller.state.destinationCommitIdentity == before.controller.state.destinationCommitIdentity &&
				model.controller.state.pageTurnSettlementAck === before.controller.state.pageTurnSettlementAck &&
				model.wordSync === before.wordSync && model.engineAdapters === before.engineAdapters &&
				model.viewState === before.viewState, "A407.Owning.PhysicalProjectionPreservesSemanticDataAndWordSync")
			accepted += command to applied
			afterProjection?.invoke(command, applied)
		}
		init {
			original.semanticReceiptProducer = { event, origin ->
				val acknowledgement = event.acknowledgement
				val step = if (acknowledgement == null) {
					// Original common fixture data ingress, using this callback's binding.
					// No legacy physical event, material proof or Root producer is supplied.
					model = model.onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.LocationChanged(
						ReaderLocator(pageIndex = settledPage, pageCount = 8, chapterPageIndex = settledPage,
							chapterPageCount = 8, progress = settledPage / 8.0), event.binding.foliateSessionId,
						destinationCommitIdentity = event.binding.destinationCommitIdentity))).coordinator
					model.reduceSemanticDestination(event, origin)
				} else {
					val predecessor = assertNotNull(model.controller.state.presentation.binding, "A407.Owning.SemanticPredecessor")
					val location = ReaderBridgeEvent.LocationChanged(
						ReaderLocator(pageIndex = ++settledPage, pageCount = 8, chapterPageIndex = settledPage,
							chapterPageCount = 8, progress = settledPage / 8.0, reason = "page-turn"),
						event.binding.foliateSessionId, pageTurnSettleToken = acknowledgement.token,
						pageTurnSettleSessionId = acknowledgement.foliateSessionId,
						pageTurnSettleRasterGeneration = acknowledgement.rasterGeneration,
						pageTurnSettleTextureGeneration = acknowledgement.textureGeneration,
						destinationCommitIdentity = event.binding.destinationCommitIdentity)
					val data = model.onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(location))
					model.onInstalledPageTurnRelocation(location, predecessor, origin).also {
						assertTrue(it.coordinator.controller.state == data.coordinator.controller.state &&
							it.progressToSave == data.progressToSave && it.wordSyncEffects == data.wordSyncEffects &&
							it.coordinator.wordSync == data.coordinator.wordSync && it.coordinator.viewState == data.coordinator.viewState &&
							it.presentationEffects.isEmpty(), "A407.Owning.OriginalSemanticProgressAndWordSyncOutputsUnchanged")
					}
				}
				model = step.coordinator
				val receipt = assertNotNull(step.presentationReceipt, "A407.Owning.OriginalSemanticReceipt")
				// A returned receipt is not necessarily accepted semantic authority. Keep
				// this boundary explicit before assuming a successor deck was requested.
				assertTrue(receipt.disposition == ReaderPresentationEventDisposition.Accepted,
					"A407.Owning.OriginalSemanticReceiptAccepted")
				receipt
			}
		}
		companion object {
			private fun requireOriginalPhysicalPublicationApi(): java.lang.reflect.Method {
				val facade = try { Class.forName("paige.navic.reader.ReaderCoordinatorSemanticDestinationKt") }
					catch (_: ClassNotFoundException) { null }
				val method = facade?.declaredMethods?.singleOrNull {
					it.name == "onInstalledPhysicalPublication" && it.returnType == ReaderCoordinator::class.java &&
						it.parameterTypes.toList() == listOf(ReaderCoordinator::class.java, ReaderTransitionCommand::class.java,
							ReaderPresentationFrameOwner::class.java, ReaderPresentationBinding::class.java,
							ReaderTransitionInputLease::class.java)
				}
				return assertNotNull(method, "A407.Owning.Prerequisite.OnInstalledPhysicalPublicationMissing")
			}
			private fun projectOriginalAcceptedPublication(method: java.lang.reflect.Method, model: ReaderCoordinator,
				command: ReaderTransitionCommand, applied: ReaderOwnerAndInputPublicationResult.Applied): ReaderCoordinator {
				return try {
					method.invoke(null, model, command, applied.publishedOwner, applied.publishedBinding,
						applied.finalPhysicalInputLease) as ReaderCoordinator
				} catch (_: java.lang.reflect.InvocationTargetException) {
					throw AssertionError("A407.Owning.OriginalPureProjectionInvocationFailed")
				}
			}
		}
	}

	private fun ReaderOwnerAndInputPublicationResult.Applied.asUntrustedFact() =
		ReaderTransitionFact.OwnerAndInputPublicationApplied(transitionId, subject, publishedOwner,
			publishedBinding, finalPhysicalInputLease, publicationIdentity)

	private fun publicAnchorReceipt(binding: ReaderPresentationBinding, boundary: Long) =
		ReaderWhispersyncAnchorReceipt(
			foliateSessionId = binding.foliateSessionId,
			destinationCommitToken = "public-settlement",
			visualPageOrdinal = 1, spineIndex = 0,
			rasterGeneration = assertNotNull(binding.rasterGeneration),
			textureGeneration = assertNotNull(binding.textureGeneration),
			presentationMutationGeneration = 1L, presentationSequence = 1L,
			anchorGeneration = 1L, boundarySequence = boundary,
			layoutGeneration = 1L, viewGeneration = 1L,
			commitSequence = assertNotNull(binding.destinationCommitIdentity).commitSequence,
			committedSpineIndex = 0, committedChapterPageIndex = 1, committedChapterPageCount = 8,
			paginationFingerprint = "public-pagination", layoutFingerprint = "public-layout",
			readerSettingsRasterKey = "public-settings",
			captureGeometry = ReaderPageTurnCaptureGeometry(1200.0, 800.0,
				ReaderPageTurnLayoutMode.Single,
				listOf(ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 0.0, 0.0, 1200.0, 800.0))),
			pageLocalRects = listOf(ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Full,
				20.0, 30.0, 40.0, 20.0)))

	private class ContinuousFixture(
		private val sessionGeneration: Long = 17L,
		acceptedPublicationConsumer: ((ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied) -> Unit)? = null
	) {
		val initial = ReaderInitialCommittedPresentationOrigin.Neutral(sessionGeneration, 19L,
			ReaderInitialPresentationInputLease.None, ReaderInitialPresentationInputLease.None)
		var physical: ReaderOwnerAndInputPublicationSnapshot? = null
		val terminals = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(ReaderPageGestureLifecycle()) { id, outcome -> terminals += id to outcome }
		val input = ReaderPageInputSettlementHostController(
			initialPresentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
			initialLocalSafetyPolicy = readerPageOperationPolicy(ReaderPageReadinessState(
				textureDeck = ReaderTextureDeckState.Ready, interaction = ReaderPageInteractionState.Ready)),
			pointerRouter = router,
			cancellationPort = object : ReaderPageHostCancellationPort {
				override fun cancelForPointerInterruption(gestureId: Long) = Unit
				override fun clearCompletedPointerOwnership(gestureId: Long) = Unit
				override fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun cancelRendererWork(reason: ReaderPageLifecycleCancellationReason) = Unit
			})
		val frames = ControlledFrames()
		private var candidate: ReaderNativePagePresentationCandidate? = null
		private var terminalCandidate: ReaderCurlSettlementTerminalPresentationCandidate? = null
		var invalidateTerminalAtPresentation = false
		var terminalPreparationAcceptedCount = 0
			private set
		var beforeSuccessorAck: ((ReaderOwnerAndInputPublicationResult.Applied) -> Unit)? = null
		var lastSuccessorCommand: ReaderTransitionCommand.CommitOwnerAndInputLease? = null
			private set
		var rejectSuccessorPublication = false
		fun invalidateTerminalCandidate() { terminalCandidate = null }
		private val publisher = ReaderNativePagePresentationPublisher(frames,
			{ error("Installed work cannot consult the legacy candidate") },
			onEvent = { error("Installed work cannot emit a legacy event") })
		private val registry = ReaderSemanticExecutableRequestRegistry(sessionGeneration)
		var semanticReceiptProducer: ((ReaderPresentationEvent.FoliateRelocated,
			ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt)? = null
		private val semantic = ReaderSemanticCommandExecutor(registry)
		var semanticOrigin: ReaderPresentationEventOrigin.SemanticCommand? = null
		private var semanticCallback: ((ReaderPresentationEventReceipt) -> Unit)? = null
		// Observation of the original callback, not an alternate receipt producer.
		private var returnedSemanticReceipt: ReaderPresentationEventReceipt? = null
		private var semanticSequence = 0L
		private var receiptSequence = 0L
		var deck: ReaderDeckLease? = null
		private var deckCallbacks: ReaderDeckLeaseFactEmitter? = null
		private var deckRole: paige.navic.reader.ReaderTransitionDeckRole? = null
		private val declaredDeckTargets = linkedMapOf<ReaderTransitionResourceKey,
			ReaderPlayLikeCurlDeckTargetIdentity>()
		private var retainedPublication: Pair<ReaderTransitionCommand.PublishRetainedOwnerAndInputLease,
			ReaderOwnerAndInputPublicationResult>? = null
		val commands = mutableListOf<ReaderTransitionCommand>()
		val frameCancellationCountAtDeckRelease = linkedMapOf<paige.navic.reader.ReaderTransitionResourceKey, Int>()
		var successorPublications = 0
			private set
		var narrowNativePublication = false
		var observePublicationCurrency: ((ReaderTransitionCommand) -> Unit)? = null
		// Controlled narrowing preserves its outcome; this hook observes only the issued command.
		private val barrier = ReaderOwnerAndInputPublicationBarrier({ command, lease ->
			observePublicationCurrency?.invoke(command)
			input.narrowOrVeto(if (narrowNativePublication && lease is ReaderTransitionInputLease.NativePage)
				ReaderTransitionInputLease.ChromeOnly else lease)
		}) {
			physical = it
			true
		}
		var delayRaster = false
		private var pendingRaster: Pair<ReaderRasterPreparationLease, ReaderRasterLeaseFactEmitter>? = null
		fun returnRaster() {
			val (lease, callbacks) = assertNotNull(pendingRaster)
			pendingRaster = null
			callbacks.onProven(lease)
		}
		private val task4 = ReaderTask4TransitionPorts(FixtureClock,
			object : ReaderRasterPreparationCommandPort {
				override fun prepare(lease: ReaderRasterPreparationLease, callbacks: ReaderRasterLeaseFactEmitter) {
					if (delayRaster) {
						assertNull(pendingRaster)
						pendingRaster = lease to callbacks
					} else callbacks.onProven(lease)
				}
				override fun release(lease: ReaderRasterPreparationLease, onReleased: () -> Unit) = onReleased()
				override fun cancel(transitionId: ReaderTransitionId) = Unit
			}, object : ReaderRendererDeckCommandPort {
				override fun reserve(lease: ReaderDeckLease, callbacks: ReaderDeckLeaseFactEmitter) {
					deck = lease
					deckCallbacks = callbacks
				}
				override fun release(lease: ReaderDeckLease, onReleased: () -> Unit) {
					frameCancellationCountAtDeckRelease[lease.resourceKey] = frames.cancelled.size
					if (candidate?.binding == lease.binding) candidate = null
					onReleased()
				}
				override fun cancelPreparation(transitionId: ReaderTransitionId) = Unit
			})
		val coordinator = createCoordinator(
			ports = object : ReaderResumableTransitionPorts {
				override val clock = FixtureClock
				override val semanticCommand: ReaderSemanticCommandPort = object : ReaderSemanticCommandPort by semantic {
					override fun synchronize(
						command: ReaderTransitionCommand.RequestSemanticSynchronization,
						onRegistration: (ReaderSemanticCommandRegistration) -> Unit,
						onReceipt: (ReaderPresentationEventReceipt) -> Unit
					): ReaderSemanticCommandResult {
						commands += command
						return semantic.synchronize(command, onRegistration, onReceipt)
					}
				}
				override val ownerAndInputPublication = object : ReaderOwnerAndInputPublicationPort {
					override fun publish(command: ReaderTransitionCommand.CommitOwnerAndInputLease):
						ReaderOwnerAndInputPublicationResult {
						lastSuccessorCommand = command
						if (rejectSuccessorPublication) return ReaderOwnerAndInputPublicationResult.Rejected(
							command.transitionId,
							ReaderOwnerAndInputPublicationSubject.Successor(command.targetHandle, command.preparedFrameResource),
							command.publicationIdentity, paige.navic.reader.ReaderTransitionFailureReason.AtomicPublicationRejected)
						return barrier.publish(command).also {
							val applied = assertIs<ReaderOwnerAndInputPublicationResult.Applied>(it, "A407.Publication.OriginalBarrierApplied")
							successorPublications++
							beforeSuccessorAck?.invoke(applied)
						}
					}
					override fun publish(command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease):
						ReaderOwnerAndInputPublicationResult {
						commands += command
						return barrier.publish(command).also { retainedPublication = command to it }
					}
				}
				override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
					commands += command
					when (command) {
						is ReaderTransitionCommand.PrepareFrameTarget -> {
							val result = publisher.prepareTarget(command, onFact)
							if (command.specification is ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame &&
								result == ReaderPortCommandResult.Accepted) terminalPreparationAcceptedCount++
							assertEquals(ReaderPortCommandResult.Accepted, result, "C407.Frame.TargetPreparation")
						}
						is ReaderTransitionCommand.RequestFramePresentation -> {
							if (command.target is ReaderTransitionFrameTarget.CurlSettlementTerminalFrame &&
								invalidateTerminalAtPresentation) terminalCandidate = null
							val result = publisher.present(command, onFact)
							if (invalidateTerminalAtPresentation && command.target is ReaderTransitionFrameTarget.CurlSettlementTerminalFrame) {
								assertIs<ReaderPortCommandResult.Rejected>(result, "C407.Terminal.RequestCurrentness")
								onFact(ReaderTransitionFact.CommandRejected(command.transitionId,
									paige.navic.reader.ReaderTransitionCommandStage.FramePresentation,
									paige.navic.reader.ReaderTransitionCommandRejectionReason.FramePresentationRejected))
							} else assertEquals(ReaderPortCommandResult.Accepted, result, "C407.Frame.RequestPresentation")
						}
						is ReaderTransitionCommand.CancelOwnedWork -> {
							assertEquals(ReaderPortCommandResult.Accepted, publisher.cancelOwnedWork(command.transitionId))
							task4.issue(command, onFact)
						}
						else -> {
							if (command is ReaderTransitionCommand.ReserveDeck) deckRole = command.role
							task4.issue(command, onFact)
						}
					}
				}
			},
			journal = ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(initial)),
			acceptedPublicationConsumer = acceptedPublicationConsumer)

		private fun createCoordinator(
			ports: ReaderResumableTransitionPorts,
			journal: ReaderTransitionJournal,
			acceptedPublicationConsumer: ((ReaderTransitionCommand, ReaderOwnerAndInputPublicationResult.Applied) -> Unit)?
		): ReaderResumableTransitionCoordinator {
			if (acceptedPublicationConsumer == null) return ReaderResumableTransitionCoordinator(
				ports, ReaderTransitionMode.Active, journal)
			// Metadata-only prerequisite: no runtime journal/heap read and no substitute
			// writer. Until MAIN executes this selector, the missing seam is not a RED run.
			val types = listOf(ReaderResumableTransitionPorts::class.java, ReaderTransitionMode::class.java,
				ReaderTransitionJournal::class.java, ReaderTransitionReleaseLedger::class.java,
				kotlin.jvm.functions.Function0::class.java, kotlin.jvm.functions.Function1::class.java,
				kotlin.jvm.functions.Function2::class.java)
			val constructor = ReaderResumableTransitionCoordinator::class.java.declaredConstructors.singleOrNull {
				!it.isSynthetic && it.parameterTypes.toList() == types
			}
			assertNotNull(constructor, "E407.ProducerPrerequisite.OriginalAcceptedPublicationConsumerMissing")
			assertTrue(ReaderResumableTransitionCoordinator::class.java.declaredFields.any {
				it.name == "onAcceptedPublication" && it.type == kotlin.jvm.functions.Function2::class.java
			}, "E407.ProducerPrerequisite.OriginalAcceptedPublicationConsumerMissing")
			constructor.isAccessible = true
			val activation: () -> ReaderSessionActivationState = { ReaderSessionActivationState.Legacy }
			val observation: (ReaderTransitionCoordinatorObservation) -> Unit = {}
			return constructor.newInstance(ports, ReaderTransitionMode.Active, journal, ReaderTransitionReleaseLedger(),
				activation, observation, acceptedPublicationConsumer) as ReaderResumableTransitionCoordinator
		}

		init {
			val domain = ReaderLegacyPhysicalDomain(sessionGeneration, ReaderLegacyFreezeToken(71L))
			assertEquals(ReaderPortCommandResult.Accepted, input.freezeForTransitionActivation(domain))
			assertEquals(ReaderPortCommandResult.Accepted, input.activateInstalledInput({ physical }, { initial }))
			assertEquals(ReaderPortCommandResult.Accepted, input.finalizeActivatedHandoff(domain))
			assertEquals(ReaderPortCommandResult.Accepted, publisher.freezeForTransitionActivation(domain))
			assertTrue(publisher.frozenFrameRetirementComplete(domain))
			publisher.completeFrozenFrameRetirement(domain)
			publisher.activateCommandOnly { specification -> candidate?.takeIf { it.binding == specification.binding } }
			assertEquals(ReaderPortCommandResult.Accepted, publisher.activateCurlTerminalCommands { specification ->
				terminalCandidate?.takeIf { it.specification == specification }
			}, "C407.Terminal.TypedResolverActivation")
		}

		fun beginOriginalClaimedTurn(gesture: Long): ReaderTransitionId {
			val predecessor = assertNotNull(physical, "C407.Turn.Retained.Predecessor")
			assertNull(semanticCallback, "C407.Turn.Semantic.PreviousCallbackConsumed")
			semanticOrigin = null
			retainedPublication = null
			val before = commands.size
			val handle = registerSemantic()
			coordinator.enqueue(ReaderTransitionFact.Intent(null, ReaderPageTurnIntent(
				ReaderPageTurnDirection.Next, ReaderTransitionGestureId(gesture), handle)))
			// Observe ORIGINAL barrier/ACK/executor callbacks. No injected publication fact
			// may make a rejected retained lease look like a dispatched semantic command.
			val (command, result) = assertNotNull(retainedPublication, "C407.Turn.Retained.Command")
			assertEquals(predecessor.owner, command.retainedOwner, "C407.Turn.Retained.OriginalOwner")
			assertEquals(predecessor.binding, command.retainedBinding, "C407.Turn.Retained.OriginalBinding")
			assertEquals(predecessor.resource, command.retainedResource, "C407.Turn.Retained.OriginalResource")
			assertEquals(ReaderTransitionInputLease.ClaimedGesture(command.transitionId,
				ReaderTransitionGestureId(gesture)), command.requestedLease, "C407.Turn.Retained.OriginalClaim")
			val applied = assertIs<ReaderOwnerAndInputPublicationResult.Applied>(result,
				"C407.Turn.RetainedPublication.Applied")
			assertEquals(command.transitionId, applied.transitionId, "C407.Turn.Retained.ExactTransition")
			assertEquals(command.publicationIdentity, applied.publicationIdentity, "C407.Turn.Retained.ExactIdentity")
			assertEquals(ReaderOwnerAndInputPublicationSubject.Retained(predecessor.resource),
				applied.subject, "C407.Turn.Retained.ExactSubject")
			assertEquals(command.requestedLease, applied.finalPhysicalInputLease, "C407.Turn.Retained.ContinuedClaim")
			assertEquals(predecessor.copy(lease = applied.finalPhysicalInputLease), physical,
				"C407.Turn.Retained.AtomicPhysicalPublication")
			val requests = commands.drop(before).filterIsInstance<ReaderTransitionCommand.RequestSemanticSynchronization>()
			assertEquals(1, requests.size, "C407.Turn.Semantic.RequestDispatched")
			val request = requests.single()
			assertEquals(command.transitionId, request.transitionId, "C407.Turn.Semantic.ExactTransition")
			assertEquals(handle, request.requestHandle, "C407.Turn.Semantic.ExactRegistryHandle")
			assertTrue(commands.indexOf(command) < commands.indexOf(request), "C407.Turn.Semantic.AfterRetainedPublication")
			val origin = assertNotNull(semanticOrigin, "C407.Turn.Semantic.OriginalOrigin")
			assertEquals(command.transitionId, origin.transitionId, "C407.Turn.Semantic.OriginalRegistryTake")
			assertNotNull(semanticCallback, "C407.Turn.Semantic.OriginalCallback")
			return command.transitionId
		}

		fun startSettledTurn(): Pair<ReaderOwnerAndInputPublicationSnapshot, Long> {
			bootstrap()
			val predecessor = assertNotNull(physical, "C407.Terminal.BootstrapPhysical")
			val gesture = assertNotNull(input.dispatchPointer(
				ReaderPageHostPointerEvent.Down(900f, 400f, 800L)).gestureId, "C407.Terminal.PointerDown")
			assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture), input.dispatchPointer(
				ReaderPageHostPointerEvent.Move(100f, 400f, 8f)).route, "C407.Terminal.PointerClaim")
			beginOriginalClaimedTurn(gesture)
			assertEquals(ReaderPagePointerRoute.Curl(gesture), input.dispatchPointer(
				ReaderPageHostPointerEvent.Up).route, "C407.Terminal.PointerUp")
			returnSemantic(settlement = true)
			assertEquals(1, coordinator.snapshot().consumedSettlementCount, "C407.Terminal.SettlementConsumed")
			assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement,
				assertNotNull(deck, "C407.Terminal.MaterialDeck").transitionId.operation, "C407.Terminal.MaterialOperation")
			return predecessor to gesture
		}

		fun curlDeclaration(predecessor: ReaderOwnerAndInputPublicationSnapshot, gesture: Long):
			ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame {
			val lease = assertNotNull(deck, "C407.Terminal.DeclarationDeck")
			val allocation = assertNotNull(lease.allocation, "C407.Terminal.DeclarationAllocation")
			assertIs<ReaderPresentationFrameOwner.NativePage>(predecessor.owner)
			val receipt = assertNotNull(returnedSemanticReceipt, "A407.Owning.TerminalOriginalSemanticReceipt")
			assertTrue(receipt.origin === semanticOrigin && receipt.originatingTransitionId == lease.transitionId &&
				receipt.disposition == ReaderPresentationEventDisposition.Accepted,
				"A407.Owning.TerminalOriginalSemanticReceiptCurrent")
			val settlement = assertNotNull((receipt.event as? ReaderPresentationEvent.FoliateRelocated)?.acknowledgement,
				"A407.Owning.TerminalOriginalSettlementAcknowledgement")
			return ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame(
				lease.transitionId, sessionGeneration, lease.binding.publicationGeneration, lease.binding, allocation,
				ReaderTransitionGestureId(gesture), settlement,
				ReaderPlayLikeCurlDeckTargetIdentity(1L, lease.textureGeneration, assertNotNull(deckRole)),
				ReaderTransitionFrameGeometry(3L, 5L, 0, 0, 1200, 800), 1L,
				ReaderCurlSettlementTerminalIdentity(predecessor.resource.key,
					assertNotNull(declaredDeckTargets[predecessor.resource.key], "C407.Terminal.OriginalSourceDeck"),
					terminalSequence = 1L))
		}

		fun returnCurlDeckReady(predecessor: ReaderOwnerAndInputPublicationSnapshot, gesture: Long) {
			val lease = assertNotNull(deck, "C407.Terminal.ReadyDeck")
			val callbacks = assertNotNull(deckCallbacks, "C407.Terminal.ReadyCallbacks")
			deckCallbacks = null
			callbacks.onOwned(lease)
			val specification = curlDeclaration(predecessor, gesture)
			terminalCandidate = ReaderCurlSettlementTerminalPresentationCandidate(specification)
			assertEquals(ReaderPortCommandResult.Accepted,
				coordinator.enqueueFrameTargetDeclaration(specification, lease.resourceKey), "C407.Terminal.DeclarationAccepted")
			// A typed terminal presents a Native successor on this original deck.
			// Retain its actual declared target for the next turn's source association,
			// just as for bootstrap; do not synthesize an ordinary Native declaration.
			declaredDeckTargets[lease.resourceKey] = specification.deckTarget
			callbacks.onPrepared(lease)
		}

		fun closeOriginalConsumers() {
			input.onLifecycleEvent(ReaderPageHostLifecycleEvent.ReaderClosed)
			publisher.dispose()
			publisher.dispose()
		}

		fun registerSemantic(): ReaderSemanticRequestHandle = registry.register { origin, mutation, callback ->
			mutation.mutationStarted()
			semanticOrigin = origin
			semanticCallback = callback
			ReaderSemanticExecutableResult.Accepted
		}

		fun bootstrap() {
			coordinator.enqueue(ReaderTransitionFact.Intent(null, ReaderBootstrapNativePageIntent(registerSemantic())))
			returnSemantic()
			returnDeckReady()
			assertNull(physical)
			frames.present(frames.requested.last())
			assertIs<ReaderPresentationFrameOwner.NativePage>(assertNotNull(physical).owner)
			assertNull(coordinator.snapshot().activeOperation)
		}

		fun beginRelocation() = coordinator.enqueue(ReaderTransitionFact.Intent(null,
			ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Jump, registerSemantic())))

		fun returnSemantic(settlement: Boolean = false) {
			val origin = assertNotNull(semanticOrigin, "C407.Semantic.OriginalOrigin")
			val callback = assertNotNull(semanticCallback, "C407.Semantic.OriginalCallback")
			semanticCallback = null
			val beforeCallback = commands.size
			val binding = ReaderPresentationBinding("public-continuous-fixture", 2L, 3L, 5L,
				destinationCommitIdentity = ReaderDestinationCommitIdentity("public-continuous-fixture", ++semanticSequence))
			val acknowledgement = if (settlement) ReaderPageTurnSettlementAck("public-settlement", 1,
				binding.foliateSessionId, assertNotNull(physical?.binding?.rasterGeneration),
				assertNotNull(physical?.binding?.textureGeneration)) else null
			val event = ReaderPresentationEvent.FoliateRelocated(binding, acknowledgement)
			val produced = semanticReceiptProducer?.invoke(event, origin)
			val version = ReaderPresentationReceiptVersion(sessionGeneration, binding.publicationIdentity, ++receiptSequence)
			val receipt = produced ?: ReaderPresentationEventReceipt(event,
				version.copy(eventSequence = version.eventSequence - 1L), version,
				ReaderPresentationEventDisposition.Accepted, ReaderPresentationState(binding = binding), emptyList(), origin)
			returnedSemanticReceipt = receipt
			callback(receipt)
			if (settlement) {
				val continuation = commands.drop(beforeCallback)
				assertEquals(1, continuation.filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>().size,
					"C407.Semantic.AllocationRequested")
				assertEquals(1, continuation.filterIsInstance<ReaderTransitionCommand.RequestRasterPreparation>().size,
					"C407.Material.AllocationReturned")
				assertEquals(1, continuation.filterIsInstance<ReaderTransitionCommand.ReserveDeck>().size,
					"C407.Material.RasterReturned")
			}
		}

		fun returnDeckReady(expectDeclarationAccepted: Boolean = true) {
			val lease = assertNotNull(deck)
			val callbacks = assertNotNull(deckCallbacks)
			deckCallbacks = null
			callbacks.onOwned(lease)
			candidate = ReaderNativePagePresentationCandidate(lease.binding, null, semanticSequence.toInt(),
				1200, 800, ReaderPagePreparationFacts(ReaderPagePreparationPhase.Ready, lease.preparationGeneration))
			val declaration = ReaderTransitionFrameTargetSpecification.NativePage(lease.transitionId, sessionGeneration,
				lease.binding.publicationGeneration, lease.binding, assertNotNull(lease.allocation),
				ReaderNativePageHostTokenState.AuthoritativeAbsent,
				ReaderPlayLikeCurlDeckTargetIdentity(1L, lease.textureGeneration, assertNotNull(deckRole)),
				ReaderTransitionFrameGeometry(3L, 5L, 0, 0, 1200, 800), 1L)
			declaredDeckTargets[lease.resourceKey] = declaration.deckTarget
			val result = coordinator.enqueueFrameTargetDeclaration(declaration, lease.resourceKey)
			if (expectDeclarationAccepted) assertEquals(ReaderPortCommandResult.Accepted, result)
			else assertIs<ReaderPortCommandResult.Rejected>(result,
				"A settled Curl needs its actual terminal declaration, not Native readiness")
			callbacks.onPrepared(lease)
		}
	}

	private class ControlledFrames : ReaderNativePagePresentedFrameSource {
		private val callbacks = linkedMapOf<Long, (Long) -> Unit>()
		val requested = mutableListOf<Long>()
		val cancelled = mutableListOf<Long>()
		val terminalRequests = mutableListOf<Pair<Long, ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame>>()
		override fun requestCurlSettlementTerminalPresentedFrame(
			candidate: ReaderCurlSettlementTerminalPresentationCandidate,
			onPresented: (Long) -> Unit
		): Long = registerControlledRequest(onPresented).also { terminalRequests += it to candidate.specification }
		override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long = registerControlledRequest(onPresented)
		private fun registerControlledRequest(onPresented: (Long) -> Unit): Long =
			(requested.size.toLong() + 1L).also { requested += it; callbacks[it] = onPresented }
		override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
			cancelled += requestId
			return true
		}
		fun present(id: Long) = assertNotNull(callbacks[id]).invoke(id)
	}

	private object FixtureClock : ReaderTransitionClock {
		override fun nowMillis() = 1_000L
		override fun schedule(atMillis: Long, action: () -> Unit) = ReaderTransitionClockRegistration { }
	}
}
