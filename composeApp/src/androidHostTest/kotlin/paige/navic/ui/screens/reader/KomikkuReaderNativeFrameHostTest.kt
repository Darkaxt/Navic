package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import karacken.curl.PageDisplayRect
import karacken.curl.PageImage
import karacken.curl.PageLeafRole
import karacken.curl.PageMaterial
import karacken.curl.PageChange
import karacken.curl.PageDeck
import karacken.curl.PortraitPageDeck
import karacken.curl.RenderCapabilities
import karacken.curl.RenderFailure
import karacken.curl.RenderFailureReason
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowGLSurfaceView
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderControllerStep
import paige.navic.reader.ReaderCurlPresentationFrame
import paige.navic.reader.ReaderCurlSettlementStage
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderLegacyLiveCompatibilityContext
import paige.navic.reader.ReaderLegacyLiveCompatibilityIdentity
import paige.navic.reader.ReaderLiveEngineHandoffDirection
import paige.navic.reader.ReaderLiveEnginePresentationProof
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationReservationResult
import paige.navic.reader.ReaderPageRelocationTransferResult
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderPreparationPresentation
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationEffect
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationReduce
import paige.navic.reader.readerViewerActionIsAdmitted
import paige.navic.reader.publicationIdentity
import paige.navic.reader.readerPagePreparationState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KomikkuReaderNativeFrameHostTest {
	private val hostFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"KomikkuReaderNativeFrameHost.android.kt"
	)

	@Test
	fun productionRelocationTimeoutFailsExactCommonHandoffAndRetainsNativeAuthority() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 51L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "session-a"
		)
		acknowledgeTask7Relocation(queue, request)
		val handoffHost = Task7VisualHandoffHost()
		val binding = ReaderPresentationBinding(
			request.foliateSessionId, 2L, 3L, 4L,
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration,
			preparationGeneration = 7L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 8L, 1200, 800,
			request.rasterGeneration, request.textureGeneration
		)
		var presentation = ReaderPresentationState(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 52L
		)
		val events = mutableListOf<ReaderPresentationEvent>()
		fun publish(event: ReaderPresentationEvent) = readerTestPresentationReceipt(
			event = event,
			postState = readerPresentationReduce(presentation, event).also {
				events += event
				presentation = it.state
			}.state
		)
		val coordinator = task7CommonRelocationCoordinator(
			queue = queue,
			request = request,
			host = handoffHost,
			publish = ::publish
		)

		assertTrue(coordinator.onAcknowledged(request))
		val requested = assertIs<ReaderPresentationEvent.WebViewHandoffRequested>(events.single())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, requested.direction)
		val transition = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertIs<ReaderPresentationFrameOwner.NativePage>(
			readerPresentationDecision(presentation).frameOwner
		)

		handoffHost.timeOut()

		val terminal = assertIs<ReaderPresentationEvent.LiveEngineHandoffTimedOut>(events.last())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, terminal.direction)
		assertEquals(transition.token, terminal.token)
		assertEquals(transition.binding, terminal.binding)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				ReaderPresentationFailureReason.TimedOut,
				retryable = true,
				cancellable = true
			),
			readerPresentationDecision(presentation).diagnosticPresentation
		)
		assertIs<ReaderPresentationFrameOwner.NativePage>(
			readerPresentationDecision(presentation).frameOwner
		)
		assertEquals(request, queue.head())

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		val retry = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertFalse(retry.token == transition.token)
		assertTrue(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertFalse(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertFalse(handoffHost.takeVisual().deliver())
		handoffHost.attached = false
		handoffHost.deliverVisual()

		val retryTerminal = assertIs<ReaderPresentationEvent.LiveEngineExposureFailed>(events.last())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, retryTerminal.direction)
		assertEquals(retry.token, retryTerminal.token)
		assertEquals(retry.binding, retryTerminal.binding)
		assertEquals(
			ReaderPresentationFailureReason.LiveEngineUnavailable,
			retryTerminal.reason
		)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				ReaderPresentationFailureReason.LiveEngineUnavailable,
				retryable = true,
				cancellable = true
			),
			readerPresentationDecision(presentation).diagnosticPresentation
		)
		assertIs<ReaderPresentationFrameOwner.NativePage>(
			readerPresentationDecision(presentation).frameOwner
		)
	}

	@Test
	fun relocationAndModePublishersKeepLiveFrameSequenceMonotonicWithinPublication() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 52L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "shared-sequence-session"
		)
		acknowledgeTask7Relocation(queue, request)
		val binding = ReaderPresentationBinding(
			request.foliateSessionId, 2L, 3L, 4L,
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration,
			preparationGeneration = 7L
		)
		val predecessorProof = ReaderNativePagePresentationProof(
			binding, null, 8L, 1200, 800,
			request.rasterGeneration, request.textureGeneration
		)
		var presentation = ReaderPresentationState(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(predecessorProof)
			),
			binding = binding,
			nextTokenValue = 53L
		)
		val events = mutableListOf<ReaderPresentationEvent>()
		fun publish(event: ReaderPresentationEvent): paige.navic.reader.ReaderPresentationEventReceipt {
			val reduction = readerPresentationReduce(presentation, event)
			events += event
			presentation = reduction.state
			return readerTestPresentationReceipt(
				event = event,
				postState = reduction.state,
				disposition = reduction.disposition
			)
		}

		val liveFrameSequences = ReaderLiveEnginePresentedFrameSequenceAuthority()
		val relocationHost = Task7VisualHandoffHost()
		val coordinator = task7CommonRelocationCoordinator(
			queue = queue,
			request = request,
			host = relocationHost,
			publish = ::publish,
			presentedFrameSequenceSource = liveFrameSequences::next
		)

		assertTrue(coordinator.onAcknowledged(request))
		relocationHost.deliverVisual()
		relocationHost.presentFrame()
		val firstLive = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			presentation.authority
		).frame.proof
		assertEquals(1L, firstLive.presentedFrameSequence)
		assertNull(queue.head())

		publish(
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.LiveEngineToNative
			)
		)
		val nativeTransition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			readerPresentationDecision(presentation).requiredTransition
		)
		publish(
			ReaderPresentationEvent.NativePagePresented(
				predecessorProof.copy(
					transitionToken = nativeTransition.token,
					presentedFrame = 9L
				)
			)
		)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentation.authority)

		val modeHost = Task7VisualHandoffHost()
		val modeBridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(binding),
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(
				host = modeHost,
				presentedFrameSequenceSource = liveFrameSequences::next
			),
			liveEngineExposureRequired = { true },
			onEvent = ::publish
		)
		modeBridge.update(readerPresentationDecision(presentation))
		modeHost.deliverVisual()
		modeHost.presentFrame()

		val secondLive = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			presentation.authority
		).frame.proof
		assertEquals(2L, secondLive.presentedFrameSequence)
		assertTrue(secondLive.token.value > firstLive.token.value)
		assertEquals(
			listOf(1L, 2L),
			events.filterIsInstance<ReaderPresentationEvent.LiveEngineExposureCommitted>()
				.map { it.proof.presentedFrameSequence }
		)
	}

	@Test
	fun nativeTurnCausallyRebindsPendingLiveHandoffBeforeAcknowledgementRetry() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 53L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "pending-turn-session"
		)
		val bindingA = ReaderPresentationBinding(
			foliateSessionId = request.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				3L
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				4L
			),
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration
		)
		val proofA = ReaderNativePagePresentationProof(
			bindingA, null, 8L, 1200, 800, 9L, 19L
		)
		val settledA = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proofA)
			),
			binding = bindingA,
			nextTokenValue = 54L
		)
		val pendingA = readerPresentationReduce(
			settledA,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
		).state
		val transitionA = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(pendingA).requiredTransition
		)
		val nativeOwnerA = ReaderPresentationFrameOwner.NativePage(proofA)
		assertEquals(nativeOwnerA, readerPresentationDecision(pendingA).frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(
			readerPresentationDecision(pendingA).inputPolicy
		)

		val reboundReduction = readerPresentationReduce(
			pendingA,
			ReaderPresentationEvent.FoliateRelocated(bindingB, acknowledgement = null)
		)
		val authoritative = Task7PresentationStore(reboundReduction.state)
		val staleHost = Task7PresentationStore(pendingA)
		val recovery = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val visualHost = Task7VisualHandoffHost()
		val coordinator = task7CommonRelocationCoordinator(
			queue = queue,
			request = request,
			host = visualHost,
			publish = authoritative::publish,
			requestPresentationEvent = staleHost::publish,
			publishRecovery = { _, reason -> recovery += reason },
			publishLiveEngineHandoffTerminal = { error("Unexpected terminal: $it") }
		)

		acknowledgeTask7Relocation(queue, request)
		assertTrue(
			coordinator.onAcknowledged(request),
			"Exact acknowledged B should defer instead of hitting the stale-A start check"
		)
		assertTrue(recovery.isEmpty())

		assertEquals(ReaderPresentationEventDisposition.Accepted, reboundReduction.disposition)
		val pendingB = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
			authoritative.state.authority
		)
		assertEquals(transitionA.token, pendingB.token)
		assertEquals(bindingB, pendingB.binding)
		assertEquals(nativeOwnerA, pendingB.retainedFrame)
		assertEquals(nativeOwnerA, readerPresentationDecision(authoritative.state).frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(
			readerPresentationDecision(authoritative.state).inputPolicy
		)
		assertEquals(pendingA.nextTokenValue, authoritative.state.nextTokenValue)

		val delayedA = readerPresentationReduce(
			authoritative.state,
			ReaderPresentationEvent.LiveEngineExposureCommitted(
				ReaderLiveEnginePresentationProof(transitionA.token, bindingA, 1L)
			)
		)
		assertEquals(ReaderPresentationEventDisposition.Stale, delayedA.disposition)
		assertEquals(authoritative.state, delayedA.state)

		staleHost.state = authoritative.state
		val transitionB = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(authoritative.state).requiredTransition
		)
		assertTrue(coordinator.synchronizeCommonPresentationHandoff(transitionB))
		visualHost.deliverVisual()
		visualHost.presentFrame()

		val exposedB = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			authoritative.state.authority
		).frame.proof
		assertEquals(transitionA.token, exposedB.token)
		assertEquals(bindingB, exposedB.binding)
		assertNull(queue.head())
		assertTrue(recovery.isEmpty())
	}

	@Test
	fun causalLiveHandoffRebindKeepsExistingVisualOwnerAndDeadline() {
		val fixture = BridgeFixture()
		val bindingA = fixture.binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.binding.foliateSessionId,
				3L
			)
		)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.binding.foliateSessionId,
				4L
			)
		)
		val nativeA = fixture.nativeProof.copy(binding = bindingA)
		val settledA = fixture.nativeState.copy(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeA)
			),
			binding = bindingA
		)
		val pendingA = readerPresentationReduce(
			settledA,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
		).state
		val authorityA = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
			pendingA.authority
		)
		val pendingB = pendingA.copy(
			authority = authorityA.copy(binding = bindingB),
			binding = bindingB
		)
		val presentation = Task7PresentationStore(pendingA)
		val commitHost = FakeReaderPresentationCommitHost(bindingA)
		val visualHost = Task7VisualHandoffHost()
		val deadlines = HostBridgeDeadlineScheduler()
		val bridge = ReaderPresentationHostBridge(
			host = commitHost,
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(visualHost),
			liveEngineExposureRequired = { true },
			transitionTimeoutScheduler = deadlines,
			transitionNowMillis = { 0L },
			onEvent = presentation::publish
		)

		bridge.update(readerPresentationDecision(presentation.state))
		val existingVisualRequest = visualHost.takeVisual()
		assertEquals(1, deadlines.postCount)
		assertEquals(0, visualHost.delayedPostCount)
		val ownerGeneration = visualHost.ownerGeneration

		presentation.state = pendingB
		commitHost.currentBinding = bindingB
		bridge.update(readerPresentationDecision(pendingB))

		assertEquals(1, deadlines.postCount, "Causal rebind restarted the whole deadline")
		assertEquals(0, visualHost.delayedPostCount)
		assertEquals(ownerGeneration, visualHost.ownerGeneration)
		assertTrue(existingVisualRequest.deliver())
		visualHost.presentFrame()
		val exposedB = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			presentation.state.authority
		).frame.proof
		assertEquals(authorityA.token, exposedB.token)
		assertEquals(bindingB, exposedB.binding)
		bridge.dispose()
	}

	@Test
	fun rootModeHandoffAndCausalRelocationJoinOneAuthorityTransaction() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 54L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "root-owned-relocation-session"
		)
		assertEquals(request, queue.commandToDispatch())
		val bindingA = ReaderPresentationBinding(
			foliateSessionId = request.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				3L
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				4L
			),
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration
		)
		val proofA = ReaderNativePagePresentationProof(
			bindingA, null, 8L, 1200, 800, 9L, 19L
		)
		val settledA = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proofA)
			),
			binding = bindingA,
			nextTokenValue = 55L
		)
		val fixture = Task7ProductionCompositionFixture(queue, request, settledA)

		val modeRequest = ReaderPresentationEvent.WebViewHandoffRequested(
			ReaderLiveEngineHandoffDirection.NativeToLiveEngine
		)
		assertTrue(fixture.publish(modeRequest).authorizes(modeRequest))
		assertEquals(1, fixture.visualHost.visualPostCount)
		assertEquals(1, fixture.rootDeadlines.postCount)
		assertEquals(0, fixture.visualHost.delayedPostCount)

		val relocation = ReaderPresentationEvent.FoliateRelocated(bindingB, acknowledgement = null)
		assertTrue(fixture.setPageTurnVisualLocation(bindingB).authorizes(relocation))

		assertEquals(
			1,
			fixture.visualHost.visualPostCount,
			"The joined root transaction registered a second visual callback"
		)
		assertEquals(
			1,
			fixture.rootDeadlines.postCount,
			"The joined root transaction restarted its deadline"
		)
		assertEquals(0, fixture.visualHost.delayedPostCount)
		fixture.deliverRootFrame()
		val liveCommits = fixture.receipts.filter {
			it.event is ReaderPresentationEvent.LiveEngineExposureCommitted
		}
		assertEquals(1, liveCommits.size)
		assertEquals(ReaderPresentationEventDisposition.Accepted, liveCommits.single().disposition)
		assertEquals(
			bindingB,
			assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
				fixture.controller.state.presentation.authority
			).frame.proof.binding
		)
		assertEquals(listOf(request), fixture.releasedClaims)
		assertNull(queue.head())
		assertTrue(fixture.recoveries.isEmpty())
		assertEquals(0, fixture.rootHandoff.pendingCallbackCount())
		assertEquals(0, fixture.rootHandoff.pendingCapacityRetryEdgeCount())
		assertEquals(0, fixture.coordinator.pendingCallbackCount())
		assertEquals(0, fixture.coordinator.pendingCapacityRetryEdgeCount())
		fixture.close()
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun productionReadinessCallbacksDoNotDispatchCurlSuccessorBeforeNativeProof() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val controller = viewerClass.task7Field("playLikeCurlController")
			.get(viewer) as ReaderPlayLikeCurlFoliateController
		val queue = controller.javaClass.task7Field("relocationQueue")
			.get(controller) as ReaderPageRelocationQueue
		val foregroundOwnership = controller.javaClass.task7Field(
			"foregroundWebViewOwnership"
		).get(controller) as ReaderForegroundWebViewOwnership
		val liveDispatch = controller.javaClass.task7Field(
			"relocationLiveDispatchCoordinator"
		).get(controller) as ReaderPageRelocationLiveDispatchCoordinator
		val first = enqueueTask7Relocation(
			queue = queue,
			gestureId = 91L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "curl-readiness-settlement-session"
		)
		val second = enqueueTask7Relocation(
			queue = queue,
			gestureId = 92L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			foliateSessionId = first.foliateSessionId
		)
		assertEquals(first, queue.commandToDispatch())
		assertTrue(
			queue.acknowledge(
				first.token.value,
				first.destinationOrdinal,
				first.foliateSessionId,
				first.rasterGeneration,
				first.textureGeneration
			)
		)
		assertTrue(queue.completeHandoff(first.token.value))
		val sourceBinding = ReaderPresentationBinding(
			foliateSessionId = first.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.sourceOrdinal.toLong()
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val destinationBinding = sourceBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.destinationOrdinal.toLong()
			),
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val acknowledgement = ReaderPageTurnSettlementAck(
			token = first.token.value,
			pageIndex = first.destinationOrdinal,
			foliateSessionId = first.foliateSessionId,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val sourceProof = ReaderNativePagePresentationProof(
			binding = sourceBinding,
			transitionToken = null,
			presentedFrame = 90L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
			textureGeneration = requireNotNull(sourceBinding.textureGeneration)
		)
		val settled = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(sourceProof)
			),
			binding = sourceBinding
		)
		val claimed = readerPresentationReduce(
			settled,
			requireNotNull(
				readerCurlClaimEvent(
					readerPresentationDecision(settled),
					first.gestureId
				)
			)
		).state
		val awaitingFoliate = readerPresentationReduce(
			claimed,
			ReaderPresentationEvent.CurlTerminal(
				token = ReaderPresentationToken(first.gestureId),
				binding = sourceBinding,
				expectedAcknowledgement = acknowledgement
			)
		).state
		val awaitingNative = readerPresentationReduce(
			awaitingFoliate,
			ReaderPresentationEvent.FoliateRelocated(
				binding = destinationBinding,
				acknowledgement = acknowledgement
			)
		).state
		assertEquals(
			ReaderCurlSettlementStage.AwaitingNativePresentation,
			assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
				awaitingNative.authority
			).stage
		)

		val awaitingNativeDecision = readerPresentationDecision(awaitingNative)
		val nativeTransition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			awaitingNativeDecision.requiredTransition
		)
		controller.setEnabled(true)
		controller.setFoliateSessionId(first.foliateSessionId)
		controller.synchronizePresentationDecision(awaitingNativeDecision)
		assertNotNull(
			foregroundOwnership.tryAcquirePassive(sessionId = 1L) { _ -> }
		)
		val secondClaim = foregroundOwnership.acquireLive(second.gestureId)
		assertTrue(liveDispatch.transfer(second, secondClaim))

		controller.onHostContentReady()
		assertEquals(second, queue.head())
		assertFalse(queue.hasDispatchedHead())
		assertFalse(liveDispatch.isCurrent(second))

		controller.onWebViewAttachmentChanged(true)
		assertEquals(second, queue.head())
		assertFalse(queue.hasDispatchedHead())
		assertFalse(liveDispatch.isCurrent(second))

		assertEquals(second, queue.commandToDispatch())
		assertFalse(liveDispatch.dispatch(second))
		assertFalse(liveDispatch.isCurrent(second))

		val nativeProof = ReaderNativePagePresentationProof(
			binding = destinationBinding,
			transitionToken = nativeTransition.token,
			presentedFrame = 93L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val staleProof = readerPresentationReduce(
			awaitingNative,
			ReaderPresentationEvent.NativePagePresented(
				nativeProof.copy(
					transitionToken = ReaderPresentationToken(
						nativeTransition.token.value + 1L
					),
					presentedFrame = nativeProof.presentedFrame + 1L
				)
			)
		)
		assertEquals(ReaderPresentationEventDisposition.Stale, staleProof.disposition)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(staleProof.state)
		)
		assertFalse(liveDispatch.dispatch(second))

		val nativeEvent = ReaderPresentationEvent.NativePagePresented(nativeProof)
		val exactProof = readerPresentationReduce(awaitingNative, nativeEvent)
		assertEquals(ReaderPresentationEventDisposition.Accepted, exactProof.disposition)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(exactProof.state)
		)
		assertTrue(liveDispatch.dispatch(second))
		val duplicateProof = readerPresentationReduce(exactProof.state, nativeEvent)
		assertEquals(
			ReaderPresentationEventDisposition.Idempotent,
			duplicateProof.disposition
		)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(duplicateProof.state)
		)
		assertFalse(liveDispatch.dispatch(second))

		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun recoveredDeckCallbacksRetainSelectedAndReleaseActualPendingOwnershipOnce() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val controller = viewerClass.task7Field("playLikeCurlController")
			.get(viewer) as ReaderPlayLikeCurlFoliateController
		val controllerClass = controller.javaClass
		val surface = controller.surfaceView
		task8PrepareSurface(surface)
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "recovered-callback-fixture",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 3,
			rasterGeneration = 0L
		)
		controllerClass.task7Field("requestedProfile").set(controller, profile)
		controllerClass.task7Field("currentOrdinal").setInt(controller, 1)
		val selectedGeneration = 101L
		val selectedBinding = ReaderPresentationBinding(
			foliateSessionId = "recovered-callback-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"recovered-callback-session",
				4L
			),
			rasterGeneration = profile.rasterGeneration,
			textureGeneration = selectedGeneration,
			preparationGeneration = 7L
		)
		val selectedProof = ReaderNativePagePresentationProof(
			binding = selectedBinding,
			transitionToken = null,
			presentedFrame = 8L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = profile.rasterGeneration,
			textureGeneration = selectedGeneration
		)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(
				ReaderPresentationState(
					authority = ReaderPresentationAuthority.SettledNativePage(
						ReaderPresentationFrameOwner.NativePage(selectedProof)
					),
					binding = selectedBinding
				)
			)
		)
		val cleanup = controllerClass.task7Field("rendererCleanupRetryCoordinator")
			.get(controller) as ReaderRendererCleanupRetryCoordinator
		val owners = controllerClass.task7Field("generationOwners")
			.get(controller) as MutableMap<Long, Any>
		val listener = surface.pageSurfaceListener
		var selectedRasterReleaseCount = 0

		controller.onPreparationStateChanged(
			ReaderPagePreparationState(
				phase = ReaderPagePreparationPhase.Preparing,
				preparationGeneration = 7L
			)
		)
		val selectedPages = task8SubmitRecoveredDeck(
			controller = controller,
			generationId = selectedGeneration,
			role = ReaderDeckSubmissionRole.Active,
			profile = profile,
			onRasterReleased = { selectedRasterReleaseCount += 1 }
		)
		assertTrue(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		controller.retryPreparation(8L)

		listener.onDeckPrepared(selectedGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertIs<ReaderRendererCleanupRequest.StaleGeneration>(
			cleanup.pendingRequest(selectedGeneration)
		)
		assertSame(selectedPages, owners[selectedGeneration])
		assertTrue(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		assertEquals(0, selectedRasterReleaseCount)
		listener.onDeckPrepared(selectedGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertSame(selectedPages, owners[selectedGeneration])
		assertTrue(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		assertEquals(0, selectedRasterReleaseCount)

		val pendingGeneration = 102L
		var pendingRasterReleaseCount = 0
		task8BeginSurfaceSettlement(surface)
		task8SubmitRecoveredDeck(
			controller = controller,
			generationId = pendingGeneration,
			role = ReaderDeckSubmissionRole.Pending,
			profile = profile,
			onRasterReleased = { pendingRasterReleaseCount += 1 }
		)
		assertTrue(task8SurfaceOwnsGeneration(surface, pendingGeneration))
		val curlBinding = selectedBinding.copy(preparationGeneration = 8L)
		val curlFrame = ReaderCurlPresentationFrame(
			token = ReaderPresentationToken(72L),
			binding = curlBinding,
			presentedFrame = 9L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = profile.rasterGeneration,
			textureGeneration = selectedGeneration
		)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(
				ReaderPresentationState(
					authority = ReaderPresentationAuthority.CurlGesture(
						ReaderPresentationFrameOwner.Curl(curlFrame)
					),
					binding = curlBinding
				)
			)
		)

		listener.onDeckPrepared(pendingGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertFalse(owners.containsKey(pendingGeneration))
		assertFalse(task8SurfaceOwnsGeneration(surface, pendingGeneration))
		assertNull(cleanup.pendingRequest(pendingGeneration))
		assertEquals(1, pendingRasterReleaseCount)
		listener.onDeckPrepared(pendingGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertEquals(1, pendingRasterReleaseCount)
		assertSame(selectedPages, owners[selectedGeneration])
		assertEquals(0, selectedRasterReleaseCount)

		val liveProof = ReaderLiveEnginePresentationProof(
			token = ReaderPresentationToken(73L),
			binding = curlBinding,
			presentedFrameSequence = 10L
		)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(
				ReaderPresentationState(
					authority = ReaderPresentationAuthority.LiveEngineExposed(
						ReaderPresentationFrameOwner.LiveEngine(liveProof)
					),
					binding = curlBinding
				)
			)
		)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertFalse(owners.containsKey(selectedGeneration))
		assertFalse(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		assertNull(cleanup.pendingRequest(selectedGeneration))
		assertEquals(1, selectedRasterReleaseCount)

		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun missingCommonAuthorityRejectsPhysicalTouchClaimAndCleansPreclaimOnce() {
		val fixture = task8CurlAuthorityFixture(initialState = null)
		val gestureId = 201L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)

		val result = fixture.controller.onPageTouchEvent(down, gestureId)

		assertEquals(ReaderPageCurlDispatchResult.TerminalPublished, result)
		assertTrue(fixture.store.presentationEvents.isEmpty())
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertNull(fixture.controller.javaClass.task7Field("activeGestureId").get(fixture.controller))
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun rejectedPreclaimTerminalCleansLocalOwnershipWhenLegacyPublicationRejects() {
		val fixture = task8CurlAuthorityFixture(
			initialState = null,
			acceptLocalTerminals = false
		)
		fixture.controller.javaClass.task7Field("attached")
			.setBoolean(fixture.controller, false)
		val gestureId = 205L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)

		val result = fixture.controller.onPageTouchEvent(down, gestureId)

		assertEquals(ReaderPageCurlDispatchResult.TerminalPublished, result)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertTrue(fixture.store.presentationEvents.isEmpty())
		assertNull(fixture.controller.javaClass.task7Field("activeGestureId").get(fixture.controller))
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun rejectedCommonTouchAdmissionCancelsExactRendererClaimWithoutAuthorityTerminal() {
		val initial = task8SettledCurlSourceState()
		val fixture = task8CurlAuthorityFixture(
			initialState = initial,
			rejectClaims = true
		)
		val gestureId = 202L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)

		val result = fixture.controller.onPageTouchEvent(down, gestureId)

		assertEquals(ReaderPageCurlDispatchResult.TerminalPublished, result)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlClaimed
		})
		assertEquals(0, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertEquals(initial, fixture.store.state)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun acceptedTouchClaimRejectsStaleTailAndPublishesExactTerminalOnce() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val gestureId = 203L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		assertEquals(
			ReaderPageCurlDispatchResult.Accepted,
			fixture.controller.onPageTouchEvent(down, gestureId)
		)
		assertIs<ReaderPresentationAuthority.CurlGesture>(
			fixture.store.state?.authority
		)

		val staleCancel = task8MotionEvent(MotionEvent.ACTION_CANCEL)
		assertEquals(
			ReaderPageCurlDispatchResult.TerminalPublished,
			fixture.controller.onPageTouchEvent(staleCancel, gestureId + 1L)
		)
		assertTrue(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		assertTrue(fixture.store.localTerminalGestureIds.isEmpty())
		assertEquals(0, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})

		assertEquals(
			ReaderPageCurlDispatchResult.TerminalPublished,
			fixture.controller.onPageTouchEvent(staleCancel, gestureId + 1L)
		)
		assertTrue(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		assertTrue(fixture.store.localTerminalGestureIds.isEmpty())

		val exactCancel = task8MotionEvent(MotionEvent.ACTION_CANCEL)
		assertEquals(
			ReaderPageCurlDispatchResult.Accepted,
			fixture.controller.onPageTouchEvent(exactCancel, gestureId)
		)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlClaimed
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
			fixture.store.state?.authority
		)
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))

		fixture.controller.onPageTouchEvent(exactCancel, gestureId)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		down.recycle()
		staleCancel.recycle()
		exactCancel.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9CurlRetryRequestsCorrelatedCurrentLiveSnapshotRatherThanNavigation() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val controller = fixture.controller
		@Suppress("UNCHECKED_CAST")
		val webView = (controller.javaClass.task7Field("webViewProvider").get(controller) as () -> WebView?)()!!
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		activity.get().setContentView(webView.parent as View)
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(requireNotNull(fixture.store.state?.binding)) { decision ->
				controller.synchronizePresentationDecision(decision)
			}, onEvent = fixture.store::publish
		)
		try {
			assertEquals(ReaderPageTurnStartResult.Settling,
				controller.start(222L, PageChange.NEXT) { _, _ -> true })
			controller.cancelGesture(222L)
			bridge.update(readerPresentationDecision(requireNotNull(fixture.store.state)))
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			val retry = requireNotNull(fixture.store.publish(ReaderPresentationEvent.Retry))
			bridge.update(readerPresentationDecision(retry.postState))
			controller.retryPreparation(1L)
			val script = assertNotNull(org.robolectric.Shadows.shadowOf(webView).lastEvaluatedJavascript)
			assertTrue(script.contains("diagnosticLocationSnapshot"))
			assertFalse(script.contains("goToVisualPage"))
			val command = org.json.JSONObject(script.substringAfter("dispatch?.(").substringBeforeLast(")"))
			val correlation = command.getString("reason")
			val retained = readerPresentationDecision(retry.postState).frameOwner
			val retiredAck = paige.navic.reader.ReaderPageTurnSettlementAck("retired", 0,
				"critical-4-session", 0L, 301L)
			controller.synchronizeVisualPageIndex(0, "$correlation-stale", retiredAck)
			assertEquals(1, controller.javaClass.task7Field("currentOrdinal").getInt(controller))
			controller.synchronizeVisualPageIndex(0, correlation, retiredAck)
			assertEquals(0, controller.javaClass.task7Field("currentOrdinal").getInt(controller))
			assertEquals(retained, readerPresentationDecision(requireNotNull(fixture.store.state)).frameOwner)
			assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
			controller.synchronizeVisualPageIndex(2, correlation, retiredAck)
			assertEquals(0, controller.javaClass.task7Field("currentOrdinal").getInt(controller))
		} finally {
			bridge.dispose()
			activity.pause().stop().destroy()
		}
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9DispatchedTimeoutRecoveryUsesSourceReportedDestination() =
		task9DispatchedTimeoutRecovery(suspendBeforeObservation = false)

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9DispatchedRecoveryRestoreReissuesSnapshotWithinRemainingDeadline() =
		task9DispatchedTimeoutRecovery(suspendBeforeObservation = true)

	private fun task9DispatchedTimeoutRecovery(suspendBeforeObservation: Boolean) {
		var releases = 0
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState(),
			onActiveRasterReleased = { releases += 1 })
		val controller = fixture.controller
		@Suppress("UNCHECKED_CAST")
		val webView = (controller.javaClass.task7Field("webViewProvider").get(controller) as () -> WebView?)()!!
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		activity.get().setContentView(webView.parent as View)
		assertTrue(webView.isAttachedToWindow)
		// The imported synthetic raster is at window origin. Keep the attached
		// fixture host in that same coordinate space, outside activity chrome.
		val attachedHost = webView.parent as View
		val hostOrigin = IntArray(2).also(attachedHost::getLocationInWindow)
		attachedHost.translationX = -hostOrigin[0].toFloat()
		attachedHost.translationY = -hostOrigin[1].toFloat()
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(requireNotNull(fixture.store.state?.binding)) { decision ->
				controller.synchronizePresentationDecision(decision)
			}, onEvent = fixture.store::publish
		)
		// The shared fixture imports renderer generation 301. The next real submission
		// must use its successor rather than the controller's untouched initial counter.
		controller.javaClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		// Import the initial state's resolved profile through the production publisher.
		val sourceBinding = requireNotNull(fixture.store.state?.binding)
		var reportedDeck: ReaderPagePreparedActiveDeck? = null
		fixture.preparedDeckRelay.sink = { reportedDeck = it }
		controller.javaClass.task7Field("nextRasterProfileEpoch")
			.setLong(controller, sourceBinding.profileGeneration)
		controller.javaClass.task7Method("publishRasterProfileEpoch", ReaderPlayLikeCurlRasterProfile::class.java)
			.invoke(controller, controller.javaClass.task7Field("requestedProfile").get(controller))
		val preparation = ReaderPageRasterPreparationController(
			host = attachedHost as android.view.ViewGroup, webViewProvider = { webView })
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		try {
			assertEquals(ReaderPageCurlDispatchResult.Accepted, controller.onPageTouchEvent(down, 221L))
			val surface = controller.surfaceView
			task8BeginSurfaceSettlement(surface)
			surface.pageSurfaceListener.onSettlementStarted(221L, 301L, "source", "target", PageChange.NEXT)
			val pendingGeneration = assertNotNull(
				controller.javaClass.task7Field("pendingDeckGenerationId").get(controller) as Long?
			)
			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE).invoke(surface, pendingGeneration)
			surface.pageSurfaceListener.onSettlementCompleted(221L, 301L, "target", 2, PageChange.NEXT)
			val queue = controller.javaClass.task7Field("relocationQueue").get(controller) as ReaderPageRelocationQueue
			val request = assertNotNull(queue.head())
			assertTrue(queue.hasDispatchedHead())
			val pending = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertEquals(ReaderCurlSettlementStage.AwaitingFoliate,
				assertIs<ReaderPresentationAuthority.CurlSettlementPending>(pending.authority).stage)
			val retained = pending.frameOwner
			val alpha = surface.alpha
			bridge.update(pending)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			val failed = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(failed.diagnosticPresentation).reason)
			assertEquals(retained, failed.frameOwner)
			assertEquals(alpha, surface.alpha)
			assertTrue(task8SurfaceOwnsGeneration(surface, 301L))
			assertEquals(0, releases)
			assertEquals(0, queue.occupiedCount())
			val oldAck = paige.navic.reader.ReaderPageTurnSettlementAck(request.token.value,
				request.destinationOrdinal, request.foliateSessionId, request.rasterGeneration, request.textureGeneration)
			controller.synchronizeVisualPageIndex(request.destinationOrdinal, "late", oldAck)
			assertEquals(failed, readerPresentationDecision(requireNotNull(fixture.store.state)))
			assertEquals(alpha, surface.alpha)

			val retry = requireNotNull(fixture.store.publish(ReaderPresentationEvent.Retry))
			bridge.update(readerPresentationDecision(retry.postState))
			var freshGeneration: Long? = null
			assertTrue(controller.requestPresentationRecoverySnapshot {
				freshGeneration = preparation.retryPreparation(sourceBinding.preparationGeneration)
				controller.retryPreparation(assertNotNull(freshGeneration))
			})
			val script = assertNotNull(org.robolectric.Shadows.shadowOf(webView).lastEvaluatedJavascript)
			var correlation = org.json.JSONObject(script.substringAfter("dispatch?.(").substringBeforeLast(")"))
				.getString("reason")
			if (suspendBeforeObservation) {
				val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				looper.idleFor(java.time.Duration.ofSeconds(4))
				bridge.update(readerPresentationDecision(requireNotNull(fixture.store.publish(
					ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))).postState))
				controller.onHostResumedChanged(false)
				controller.onHostWindowHidden()
				preparation.invalidate("window-hidden")
				looper.idleFor(java.time.Duration.ofSeconds(30))
				assertNull(fixture.store.state?.failure)
				assertFalse(controller.stagePresentationRecoveryObservation(0, correlation, request.foliateSessionId))
				bridge.update(readerPresentationDecision(requireNotNull(fixture.store.publish(
					ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored))).postState))
				controller.onHostResumedChanged(true)
				controller.onHostContentReady()
				val restoredCorrelation = assertNotNull(
					(webView as Task9RecoveryCommandWebView).recoveryReasons.lastOrNull())
				assertFalse(restoredCorrelation == correlation,
					"Restore must issue a fresh physical snapshot rather than coalescing the invalidated request")
				assertFalse(controller.stagePresentationRecoveryObservation(0, correlation, request.foliateSessionId))
				correlation = restoredCorrelation
				assertEquals(readerPresentationDecision(retry.postState).pendingTransitionToken,
					readerPresentationDecision(requireNotNull(fixture.store.state)).pendingTransitionToken)
			}
			assertNull(freshGeneration)
			assertFalse(controller.stagePresentationRecoveryObservation(0, correlation, "stale-session"))
			assertFalse(controller.stagePresentationRecoveryObservation(-1, correlation, request.foliateSessionId))
			assertFalse(controller.stagePresentationRecoveryObservation(0, "$correlation-stale", request.foliateSessionId))
			assertTrue(controller.stagePresentationRecoveryObservation(0, correlation, request.foliateSessionId))
			val profileEpoch = controller.javaClass.task7Field("publishedRasterProfileEpoch").get(controller) as Long?
			val observed = assertNotNull(readerPresentationHostBinding(ReaderPresentationHostBindingSnapshot(
				pageTurnCanvasEnabled = true, windowVisible = true,
				foliateSessionId = request.foliateSessionId,
				publicationGeneration = sourceBinding.publicationGeneration,
				viewportGeneration = sourceBinding.viewportGeneration,
				viewportWidth = 1200, viewportHeight = 800,
				profileIdentity = profileEpoch?.let(ReaderPresentationHostProfileIdentity::Resolved)
					?: ReaderPresentationHostProfileIdentity.Provisional,
				destinationCommitIdentity = ReaderDestinationCommitIdentity(request.foliateSessionId,
					assertNotNull(sourceBinding.destinationCommitIdentity).commitSequence + 1L),
				preparationGeneration = assertNotNull(sourceBinding.preparationGeneration),
				visualPageIndex = 0, preparedDeck = reportedDeck, preparedDeckAdmitted = true
			)))
			val reporter = ReaderPresentationBindingReporter()
			val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(reporter.update(
				confirmedTargetBinding = fixture.store.state?.binding, currentBinding = observed,
				publicationOpenPending = false, relocationPending = true
			), "The real host binding after timeout must admit the correlated source observation")
			assertNull(relocation.acknowledgement)
			val step = reporter.dispatch(ReaderController(ReaderControllerState(
				readerSessionGeneration = 1L, presentation = requireNotNull(fixture.store.state))), relocation)
			fixture.store.state = step.controller.state.presentation
			assertEquals(observed, reporter.lastReportedBinding)
			bridge.update(readerPresentationDecision(step.controller.state.presentation))
			controller.synchronizeVisualPageIndex(0, correlation, oldAck)
			assertEquals(assertNotNull(sourceBinding.preparationGeneration) + 1L, freshGeneration)
			assertFalse(controller.stagePresentationRecoveryObservation(2, correlation, request.foliateSessionId))
			if (suspendBeforeObservation) {
				val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				looper.idleFor(java.time.Duration.ofSeconds(5))
				assertNull(fixture.store.state?.failure)
				looper.idleFor(java.time.Duration.ofSeconds(1))
				assertEquals(ReaderPresentationFailureReason.TimedOut, fixture.store.state?.failure?.reason)
			}
			controller.onHostSizeChanged()
			assertNull(controller.javaClass.task7Field("publishedRasterProfileEpoch").get(controller),
				"A real geometry replacement must still invalidate the preserved profile")
		} finally {
			preparation.destroy()
			bridge.dispose()
			down.recycle()
			activity.pause().stop().destroy()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerDefersRecoveryPublicationUntilSourceIngressAndStillAdmitsToc() =
		task9ActualViewerRecoveryPublication(recoveryObservation = true, targetOrdinal = 1)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerSameOrdinalTocRetiresRecoveryAndContinues() =
		task9ActualViewerRecoveryPublication(recoveryObservation = false, targetOrdinal = 1)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerChangedOrdinalTocRetiresRecoveryAndContinues() =
		task9ActualViewerRecoveryPublication(recoveryObservation = false, targetOrdinal = 0)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerSameOrdinalTocRequiresActualNativeSuccessor() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerChangedOrdinalTocRequiresActualNativeSuccessor() =
		task9ActualViewerRecoveryPublication(false, 0, nativePipeline = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerRetainedCurlProjectionThroughRetry() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, projectionOnly = true)

	private enum class Task9LivenessBoundary { MissingSource, RejectedSource, MissingFrame, HiddenFrame }

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerMissingSourceTimesOutAndCanRetryCancel() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.MissingSource)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerRejectedSourceTimesOutAndCanRetryCancel() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.RejectedSource)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerMissingNativeFrameTimesOutAndCanRetryCancel() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.MissingFrame)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerWindowRestoreResumesRemainingFrameDeadline() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.HiddenFrame)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerRetainedSelectedFailureRejectsLateNativeSuccessor() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, failRetainedSelected = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun task9ActualViewerPhysicalRuntimeDrainsAndSessionRejectsClosedCaptureTail() {
		val runtime = Task9ControlledPassiveRuntime()
		val geometry = ReaderPassiveRasterGeometry(8, 12, 0, 0, 8, 12)
		val issuer = ReaderPassiveRasterManifestIssuer()
		val commit = issuer.replaceCanonicalCommit(ReaderPassiveRasterCanonicalCommit(
			1L, "task9-source", 1L, "task9-commit", "task9-profile", "task9-pagination",
			"task9-layout", "task9-decoration", geometry, 0L))
		val manifest = assertNotNull(issuer.issue(commit, "task9-target", 1))
		var cancelled = 0
		var drained = 0
		runtime.commit(manifest, manifest.opaqueCaptureTarget, 1L) { assertNull(it); cancelled++ }
		runtime.cancelActiveCommit { drained++ }
		runtime.cancelActiveCommit { drained++ }
		assertEquals(1, cancelled)
		assertEquals(2, drained)
		assertEquals(0, runtime.pendingCallbackCount)
		assertTrue(runtime.commits.tryReceive().isFailure)
		runtime.pause()
		assertFalse(runtime.isReady)
		runtime.capture(geometry) { assertNull(it); cancelled++ }
		assertEquals(2, cancelled)
		runtime.resume()
		assertTrue(runtime.isReady)
		var releases = 0
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			releases++
			bitmap.recycle()
		}
		assertTrue(session.commit(manifest) { committed ->
			assertTrue(assertNotNull(committed).capture { capture -> assertNull(capture) })
		})
		runtime.completeCommit(assertNotNull(runtime.commits.tryReceive().getOrNull()))
		val oldCapture = assertNotNull(runtime.captures.tryReceive().getOrNull())
		assertEquals(geometry, oldCapture.geometry)
		session.close()
		assertTrue(runtime.isRetired)
		assertEquals(0, runtime.pendingCallbackCount)
		runtime.resume()
		assertFalse(runtime.isReady)
		// A callback copied by the test is not a physical owner after cancellation.
		val staleBitmap = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888)
		oldCapture.callback(staleBitmap)
		assertEquals(1, releases)
		assertTrue(staleBitmap.isRecycled)
		session.close()
		assertEquals(1, releases)
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private fun task9ActualViewerRecoveryPublication(
		recoveryObservation: Boolean,
		targetOrdinal: Int,
		nativePipeline: Boolean = false,
		projectionOnly: Boolean = false,
		failRetainedSelected: Boolean = false,
		livenessBoundary: Task9LivenessBoundary? = null
	) = runTest {
		val androidMainThread = Thread.currentThread()
		assertSame(android.os.Looper.getMainLooper().thread, androidMainThread)
		val queuedMain = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
		Dispatchers.setMain(object : kotlinx.coroutines.MainCoroutineDispatcher() {
			override val immediate get() = this
			override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext) =
				Thread.currentThread() !== androidMainThread
			override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) =
				queuedMain.dispatch(context, block)
		})
		try {
			val sourceSession = "critical-4-session"
			val initialOrdinal = 1
			val activity = Robolectric.buildActivity(Activity::class.java).setup()
			val (viewerClass, viewer) = task7Viewer(activity.get())
			val webView = Task9RecoveryCommandWebView(activity.get())
			activity.get().setContentView(viewer)
			viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(viewer, webView)
			val requestedWidth = if (nativePipeline) 800 else 1200
			val requestedHeight = if (nativePipeline) 1200 else 800
			viewer.measure(View.MeasureSpec.makeMeasureSpec(requestedWidth, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(requestedHeight, View.MeasureSpec.EXACTLY))
			viewer.layout(0, 0, requestedWidth, requestedHeight)
			val controller = viewerClass.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
			val preparation = viewerClass.task7Field("pageRasterPreparationController").get(viewer) as ReaderPageRasterPreparationController
			val reporter = viewerClass.task7Field("presentationBindingReporter").get(viewer) as ReaderPresentationBindingReporter
			val runtime = Task9ControlledPassiveRuntime()
			val rendererReleaseCounts = mutableMapOf<Long, Int>()
			val rendererReleaseReasons = mutableMapOf<Long, karacken.curl.DeckReleaseReason>()
			val producedRendererGenerations = mutableListOf<Long>()
			if (nativePipeline) {
				// Observe leases before surface initialization: replacing a listener later
				// replays cached capabilities and would introduce an extra refresh edge.
				assertNull(controller.surfaceView.renderCapabilities)
				val originalListener = controller.surfaceView.pageSurfaceListener
				// Kotlin delegation does not forward Java default interface methods.
				// Explicitly forward the entire boundary, including overloads.
				controller.surfaceView.pageSurfaceListener = object : karacken.curl.PageSurfaceListener {
					override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) = originalListener.onCapabilitiesAvailable(capabilities)
					override fun onDeckPrepared(generationId: Long) = originalListener.onDeckPrepared(generationId)
					override fun onDeckRejected(generationId: Long, reason: karacken.curl.DeckRejectionReason) = originalListener.onDeckRejected(generationId, reason)
					override fun onDeckSubmissionCapacityAvailable() = originalListener.onDeckSubmissionCapacityAvailable()
					override fun onRendererAvailabilityRestored() = originalListener.onRendererAvailabilityRestored()
					override fun onPageOverlayUpdateCapacityAvailable(applied: Boolean) = originalListener.onPageOverlayUpdateCapacityAvailable(applied)
					override fun onPageOverlayStateInvalidated() = originalListener.onPageOverlayStateInvalidated()
					override fun onRenderFailure(failure: RenderFailure) = originalListener.onRenderFailure(failure)
					override fun onGestureRejected(generationId: Long, reason: karacken.curl.GestureRejectionReason) = originalListener.onGestureRejected(generationId, reason)
					override fun onGestureRejected(gestureId: Long, generationId: Long, reason: karacken.curl.GestureRejectionReason) = originalListener.onGestureRejected(gestureId, generationId, reason)
					override fun onGestureRejected(gestureId: Long, generationId: Long, reason: karacken.curl.GestureRejectionReason, pageChange: PageChange) = originalListener.onGestureRejected(gestureId, generationId, reason, pageChange)
					override fun onGestureCancelled(gestureId: Long, generationId: Long) = originalListener.onGestureCancelled(gestureId, generationId)
					override fun onSettlementStarted(generationId: Long, source: String, target: String, pageChange: PageChange) = originalListener.onSettlementStarted(generationId, source, target, pageChange)
					override fun onSettlementStarted(gestureId: Long, generationId: Long, source: String, target: String, pageChange: PageChange) = originalListener.onSettlementStarted(gestureId, generationId, source, target, pageChange)
					override fun onSettlementCompleted(generationId: Long, current: String, ordinal: Int, pageChange: PageChange) = originalListener.onSettlementCompleted(generationId, current, ordinal, pageChange)
					override fun onSettlementCompleted(gestureId: Long, generationId: Long, current: String, ordinal: Int, pageChange: PageChange) = originalListener.onSettlementCompleted(gestureId, generationId, current, ordinal, pageChange)
					override fun onSettlementCancelled(generationId: Long, current: String) = originalListener.onSettlementCancelled(generationId, current)
					override fun onSettlementCancelled(gestureId: Long, generationId: Long, current: String) = originalListener.onSettlementCancelled(gestureId, generationId, current)
					override fun onDeckReleased(generationId: Long, reason: karacken.curl.DeckReleaseReason) {
						rendererReleaseCounts[generationId] = rendererReleaseCounts.getOrDefault(generationId, 0) + 1
						rendererReleaseReasons[generationId] = reason
						originalListener.onDeckReleased(generationId, reason)
					}
				}
			}
			var donor: Task8CurlAuthorityFixture? = null
			var sustainedBridge: ReaderPresentationHostBridge? = null
			val deadlines = HostBridgeDeadlineScheduler()
			var nowMillis = 0L
			var deadlineDelayMillis = 0L
			val timedScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					deadlineDelayMillis = delayMillis
					return deadlines.postDelayed(action, delayMillis)
				}
				override fun removeCallbacks(action: Runnable) = deadlines.removeCallbacks(action)
			}
			var bodyFailure: Throwable? = null
			val bundle = controller.javaClass.task7Field("bundleSource").get(controller) as ReaderPageTurnBundleSource
			val rasterProofReady = kotlinx.coroutines.CompletableDeferred<Long>()
			@Suppress("UNCHECKED_CAST")
			val originalRasterProofReady = preparation.javaClass.task7Field("onRasterProofReady")
				.get(preparation) as (Long) -> Unit
			preparation.javaClass.task7Field("onRasterProofReady").set(preparation, { generation: Long ->
				originalRasterProofReady(generation)
				rasterProofReady.complete(generation)
			})
			try {
			// Seed only pre-publication opaque inputs. Complete receiver startup before
			// constructing a predecessor from its actual settled viewport identity.
			viewerClass.task7Field("presentationPublicationGeneration").setLong(viewer, 0L)
			viewerClass.task7Field("presentationViewportGeneration").setLong(viewer, 2L)
			viewerClass.task7Field("pageTurnFoliateSessionId").set(viewer, sourceSession)
			viewerClass.task7Field("pageTurnVisualPageIndex").set(viewer, initialOrdinal)
			viewerClass.task7Method("setPageTurnCanvasEnabled", java.lang.Boolean.TYPE, Function0::class.java)
				.invoke(viewer, true, { })
			assertTrue(viewerClass.task7Field("pageTurnCanvasEnabled").getBoolean(viewer),
				"The actual viewer fixture must admit canvas mode")
			if (nativePipeline) {
				// Admit the real empty-renderer ownership baseline before importing
				// the already-presented predecessor or any synthetic cache entries.
				task8PrepareSurface(controller.surfaceView)
				controller.surfaceView.surfaceCreated(controller.surfaceView.holder)
				ShadowLooper.runUiThreadTasks()
				assertTrue(viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer),
					"Actual empty-renderer cold baseline must be admitted before predecessor installation")
			}
			controller.setFoliateSessionId(sourceSession)
			val visualLocation = viewerClass.task7Method("setPageTurnVisualLocation", Integer::class.java,
				String::class.java, String::class.java, ReaderPageTurnSettlementAck::class.java)
			visualLocation.invoke(viewer, initialOrdinal, "initial-source", sourceSession, null)
			assertEquals(initialOrdinal, viewerClass.task7Field("pageTurnVisualPageIndex").get(viewer))
			assertEquals(initialOrdinal, controller.javaClass.task7Field("currentOrdinal").getInt(controller),
				"Receiving controller must agree with initial source before predecessor installation")
			val viewportWidth = viewer.width
			val viewportHeight = viewer.height
			if (nativePipeline) {
				webView.measure(View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY))
				webView.layout(0, 0, viewportWidth, viewportHeight)
				assertEquals(viewportWidth, webView.width, "Source WebView must match settled receiver width")
				assertEquals(viewportHeight, webView.height, "Source WebView must match settled receiver height")
				// The synthetic worker captures the full frame at window origin, outside
				// Activity chrome. Align the real host before constructing either proof.
				val viewerOrigin = IntArray(2).also(viewer::getLocationInWindow)
				viewer.translationX = -viewerOrigin[0].toFloat()
				viewer.translationY = -viewerOrigin[1].toFloat()
			}
			val geometry = ReaderPassiveRasterGeometry(webView.width, webView.height,
				0, 0, webView.width, webView.height)
			val adapter = ReaderPassiveRasterPreparationAdapter(
				ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
					if (!bitmap.isRecycled) bitmap.recycle()
				}, ReaderPageLivePassiveRasterManifestPort { webView }, bundle,
				viewerClass.task7Field("passiveRasterCaptureEpoch").getLong(viewer))
			// Install one physical worker at the existing port boundary. The controller's
			// production provider reads this very adapter; there is no second fake port.
			viewerClass.task7Field("passiveRasterPreparationAdapter").set(viewer, adapter)
			viewerClass.task7Field("passiveRasterPreparationGeometry").set(viewer, geometry)
			@Suppress("UNCHECKED_CAST")
			val actualPortProvider = preparation.javaClass.task7Field("passiveRasterPreparationPortProvider")
				.get(preparation) as () -> ReaderPassiveRasterPreparationPort?
			assertSame(adapter, actualPortProvider())
			val viewportGeneration = viewerClass.task7Field("presentationViewportGeneration").getLong(viewer)
			val origin = IntArray(2).also(controller.surfaceView::getLocationInWindow)
			val predecessorRect = if (nativePipeline) ReaderPlayLikeCurlPhysicalRect(
				origin[0], origin[1], origin[0] + viewportWidth, origin[1] + viewportHeight
			) else ReaderPlayLikeCurlPhysicalRect(0, 0, 2, 2)
			val source = task8CurlAuthorityFixture(
				task8SettledCurlSourceState(viewportGeneration, viewportWidth, viewportHeight),
				sourceIdentity = if (nativePipeline) "#${Int.MIN_VALUE}" else sourceSession,
				viewportWidth = viewportWidth, viewportHeight = viewportHeight, physicalRect = predecessorRect
			).also { donor = it }
			val initialState = assertNotNull(source.store.state)
			val binding = assertNotNull(initialState.binding)
			val predecessorProof = assertIs<ReaderPresentationAuthority.SettledNativePage>(initialState.authority).frame.proof
			assertEquals(viewportGeneration, predecessorProof.binding.viewportGeneration)
			assertEquals(viewportWidth, predecessorProof.viewportWidth)
			assertEquals(viewportHeight, predecessorProof.viewportHeight)
			var common = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = initialState))
			webView.sourceBinding = {
				assertSame(androidMainThread, Thread.currentThread())
				assertSame(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
				assertNotNull(common.state.presentation.binding)
			}
			val retainedProjectionCallers = mutableListOf<String>()
			if (projectionOnly) {
				@Suppress("UNCHECKED_CAST")
				val originalReadiness = controller.javaClass.task7Field("onReadinessStateChange")
					.get(controller) as (ReaderPageRendererReadinessState) -> Unit
				controller.javaClass.task7Field("onReadinessStateChange").set(controller,
					{ state: ReaderPageRendererReadinessState ->
						originalReadiness(state)
						if (readerPresentationDecision(common.state.presentation).frameOwner is ReaderPresentationFrameOwner.Curl) {
							val callers = Throwable().stackTrace.filter { it.className == controller.javaClass.name }
								.map { it.methodName }.filter { it in setOf("invalidate", "synchronizeVisualPageIndex", "cancelGesture", "refreshPreparedDeck") }
							if (callers.isNotEmpty()) retainedProjectionCallers += "${callers.joinToString("/")} alpha=${controller.surfaceView.alpha}"
						}
					})
			}
			val events = mutableListOf<ReaderPresentationEvent>()
			val publish: (ReaderPresentationEvent) -> paige.navic.reader.ReaderPresentationEventReceipt? = { event ->
				events += event
				val step = common.onPresentationEvent(event)
				common = step.controller
				step.presentationReceipt
			}
			val apply = viewerClass.task7Method("applyPresentationDecision",
				paige.navic.reader.ReaderPresentationDecision::class.java, ReaderRendererLossCancellationIdentity::class.java)
			val applyFrame = viewerClass.task7Method("applyPresentationFrameOwner", ReaderPresentationDecision::class.java)
			val commitHost = object : ReaderPresentationCommitHost {
				override val isAttachedToWindow get() = viewer.isAttachedToWindow
				override val currentPresentationBinding get() =
					viewerClass.task7Method("currentPresentationBinding").invoke(viewer) as ReaderPresentationBinding?
				override val currentShellCoverGeneration: Long? get() = null
				override val shellCoverSelected get() = false
				override val measuredViewportWidth get() = viewer.width
				override val measuredViewportHeight get() = viewer.height
				override fun prepareOpaqueShellCover(coverGeneration: Long) = error("No shell transition on retained Curl route")
				override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = error("No shell preparation to cancel")
				override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = error("No shell preparation to complete")
				override fun registerShellCoverDrawListener(onDraw: () -> Unit): ReaderPresentationDrawRegistration =
					error("No shell draw on retained Curl route")
				override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = error("No shell animation on retained Curl route")
				override fun applyPresentationFrameOwner(decision: ReaderPresentationDecision) {
					applyFrame.invoke(viewer, decision)
				}
			}
			sustainedBridge = ReaderPresentationHostBridge(commitHost,
				transitionTimeoutScheduler = timedScheduler, transitionNowMillis = { nowMillis }) { event ->
				viewerClass.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java)
					.invoke(viewer, event) as ReaderPresentationEventReceipt?
			}
			val onEffect: (ReaderPresentationHostEffect) -> Unit = { effect ->
				apply.invoke(viewer, effect.decision, effect.rendererLossCancellationIdentity)
				sustainedBridge?.update(effect.decision)
			}
			val setDecision = viewerClass.task7Method("setPresentationDecision",
				paige.navic.reader.ReaderPresentationDecision::class.java, ReaderPresentationState::class.java,
				paige.navic.reader.ReaderPresentationReceiptVersion::class.java, java.lang.Boolean.TYPE,
				ReaderDestinationCommitIdentity::class.java, Function1::class.java, Function1::class.java)
			fun updateDestination(destination: ReaderDestinationCommitIdentity?) {
				setDecision.invoke(viewer, readerPresentationDecision(common.state.presentation), common.state.presentation,
					common.presentationVersion, false, destination, publish, onEffect)
			}
			// Establish the already-presented source deck before the actual publication
			// bootstrap. A provisional publication here would create a different startup request.
			val sourceProfile = source.controller.javaClass.task7Field("requestedProfile").get(source.controller)
			for (profileController in listOf(source.controller, controller)) {
				profileController.javaClass.task7Field("nextRasterProfileEpoch").setLong(profileController, binding.profileGeneration)
				profileController.javaClass.task7Method("publishRasterProfileEpoch", ReaderPlayLikeCurlRasterProfile::class.java)
					.invoke(profileController, sourceProfile)
			}
			var predecessorReleases = 0
			if (nativePipeline) {
				controller.javaClass.task7Field("requestedProfile").set(controller, sourceProfile)
				controller.surfaceView.layout(0, 0, viewportWidth, viewportHeight)
				assertEquals(predecessorProof.viewportWidth, controller.surfaceView.width)
				assertEquals(predecessorProof.viewportHeight, controller.surfaceView.height)
				assertEquals(binding.viewportGeneration,
					viewerClass.task7Field("presentationViewportGeneration").getLong(viewer))
				task8SubmitRecoveredDeck(controller, 301L, ReaderDeckSubmissionRole.Active,
					sourceProfile as ReaderPlayLikeCurlRasterProfile, { predecessorReleases += 1 }, true,
					physicalRect = predecessorRect)
				controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
					.invoke(controller.surfaceView, 301L)
				controller.javaClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
				assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
			} else {
				source.preparedDeckRelay.sink = { deck ->
					viewerClass.task7Method("onPreparedActiveDeckChanged", ReaderPagePreparedActiveDeck::class.java)
						.invoke(viewer, deck)
				}
				source.controller.javaClass.task7Method("publishPreparedActiveDeck", Int::class.javaPrimitiveType!!)
					.invoke(source.controller, source.controller.javaClass.task7Field("currentOrdinal").getInt(source.controller))
			}
			viewerClass.task7Method("preparePresentationEpoch",
				paige.navic.reader.ReaderPresentationReceiptVersion::class.java, ReaderPresentationState::class.java,
				java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
				.invoke(viewer, common.presentationVersion, common.state.presentation, false, true, false)
			assertTrue(reporter.bindPublication(binding))
				updateDestination(binding.destinationCommitIdentity)
				assertEquals(binding, reporter.lastReportedBinding)
				assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority)
				val dispatchEvent = viewerClass.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java)
				fun deliver(event: ReaderPresentationEvent) = assertNotNull(
					dispatchEvent.invoke(viewer, event) as ReaderPresentationEventReceipt?)
				val gestureController = if (nativePipeline) controller else source.controller
				if (nativePipeline) {
					// Import the source fixture's already-ready predecessor safety policy,
					// before the gesture/timeout/Retry route under test begins.
					controller.setPageOperationPolicy(source.controller.javaClass.task7Field("pageOperationPolicy")
						.get(source.controller) as paige.navic.reader.ReaderPageOperationPolicy)
					assertTrue(controller.isAvailable, "Imported predecessor must admit the actual receiving gesture")
				}
				assertEquals(ReaderPageTurnStartResult.Settling,
					gestureController.start(225L, PageChange.NEXT) { _, _ -> true })
				if (!nativePipeline) deliver(assertIs<ReaderPresentationEvent.CurlClaimed>(source.store.presentationEvents.last()))
				gestureController.cancelGesture(225L)
				if (!nativePipeline) deliver(assertIs<ReaderPresentationEvent.CurlTerminal>(source.store.presentationEvents.last()))
				val retained = assertIs<ReaderPresentationFrameOwner.Curl>(
					readerPresentationDecision(common.state.presentation).frameOwner)
				@Suppress("UNCHECKED_CAST")
				val preRetryRendererGenerations = (controller.javaClass.task7Field("generationOwners")
					.get(controller) as Map<Long, Any>).keys.toSet()
				if (nativePipeline) {
					@Suppress("UNCHECKED_CAST")
					val preRetryFences = controller.javaClass.task7Field("generationCallbackFences")
						.get(controller) as Map<Long, ReaderAcceptedDeckCallbackFence>
					// The actual tap synchronously submits pending material before its claim.
					// It belongs to the predecessor generation, not the later Retry output.
					for (generation in preRetryRendererGenerations - 301L) {
						val fence = assertNotNull(preRetryFences[generation])
						assertEquals(binding.preparationGeneration, fence.binding.preparationGeneration)
						assertEquals(binding.profileGeneration, fence.binding.profileGeneration)
					}
				}
				assertNotNull(sustainedBridge).update(readerPresentationDecision(common.state.presentation))
				deadlines.runPending()
				val retry = deliver(ReaderPresentationEvent.Retry)
				if (projectionOnly) {
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					assertFalse(controller.ownsInlineRasterShieldPresentation)
					assertEquals(1f, controller.surfaceView.alpha,
						"The actual bridge must project the common-retained Curl frame through Retry")
					assertTrue(deadlines.hasPending)
				}
				val retryEffect = assertIs<ReaderPresentationEffect.RetryPreparation>(retry.effects.single())
				val request = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
					common.state.presentation.authority)
				assertEquals(retained, request.retainedFrame)
				assertEquals(retryEffect.token, request.nativePresentationRequest?.token)
				assertEquals(binding.preparationGeneration,
					request.nativePresentationRequest?.retryAfterPreparationGeneration)
				assertEquals(retryEffect.binding,
					assertIs<ReaderPresentationAuthority.BlockingPreparation>(common.state.presentation.authority)
						.nativePresentationRequest?.binding,
					"Uncorrelated host identity publication must not retire the pending Retry effect")
				assertEquals(readerPresentationDecision(common.state.presentation),
					viewerClass.task7Field("presentationDecision").get(viewer),
					"The viewer must apply the actual current common receipt before handling Retry")
				val effectQueue = paige.navic.reader.ReaderPresentationEffectQueue()
				var retryAdmissions = 0
				val effectHandler = ReaderPresentationEffectHandler(
					retryPreparation = { effect ->
						retryAdmissions += 1
						viewerClass.task7Method("retryPreparation", ReaderPresentationEffect.RetryPreparation::class.java)
							.invoke(viewer, effect) as Boolean
					}, releaseStalePresentation = { false })
				effectQueue.retain(retry.effects)
				effectHandler.deliver(effectQueue.pendingEffects(), readerPresentationDecision(common.state.presentation)) {
					assertTrue(effectQueue.acknowledge(it))
				}
				assertEquals(1, retryAdmissions)
				assertTrue(effectQueue.pendingEffects().isEmpty())
				fun assertRetainedProjection(stage: String) {
					if (!projectionOnly) return
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					assertFalse(controller.ownsInlineRasterShieldPresentation)
					assertEquals(1f, controller.surfaceView.alpha,
						"Selected Curl projection lost at $stage; token=${retained.frame.token.value} " +
							"texture=${retained.frame.textureGeneration} callers=$retainedProjectionCallers")
				}
				assertRetainedProjection("retry-effect-delivery")
				val correlation = assertNotNull(webView.recoveryReasons.lastOrNull())
				val retryDeadlinePosts = deadlines.postCount
				fun verifyTimeoutRetryCancel(lateTail: () -> Unit) {
					assertTrue(deadlines.hasPending)
					val pendingToken = readerPresentationDecision(common.state.presentation).pendingTransitionToken
					nowMillis += deadlineDelayMillis
					deadlines.runPending()
					ShadowLooper.runUiThreadTasks()
					val failed = common.state
					val failedDecision = readerPresentationDecision(failed.presentation)
					val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(failedDecision.diagnosticPresentation)
					assertEquals(ReaderPresentationFailureReason.TimedOut, diagnostic.reason)
					assertTrue(diagnostic.retryable && diagnostic.cancellable)
					assertEquals(retained, failedDecision.frameOwner)
					assertEquals(0, predecessorReleases)
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					assertEquals(1f, controller.surfaceView.alpha)
					assertFalse(deadlines.hasPending)
					lateTail()
					ShadowLooper.runUiThreadTasks()
					assertEquals(failed, common.state, "Late physical/source completion must not revive timeout")
					val fresh = deliver(ReaderPresentationEvent.Retry)
					val freshToken = assertNotNull(readerPresentationDecision(fresh.postState).pendingTransitionToken)
					assertTrue(freshToken.value > assertNotNull(pendingToken).value)
					assertEquals(retained, readerPresentationDecision(fresh.postState).frameOwner)
					effectQueue.retain(fresh.effects)
					effectHandler.deliver(effectQueue.pendingEffects(), readerPresentationDecision(common.state.presentation)) {
						assertTrue(effectQueue.acknowledge(it))
					}
					assertTrue(effectQueue.pendingEffects().isEmpty())
					assertTrue(deadlines.hasPending)
					val canRestoreSemanticFrame = common.state.presentation.binding == retained.frame.binding
					assertEquals(livenessBoundary == Task9LivenessBoundary.MissingSource ||
						livenessBoundary == Task9LivenessBoundary.RejectedSource, canRestoreSemanticFrame,
						"Only admitted source/native continuation may replace the retained semantic binding")
					deliver(ReaderPresentationEvent.Cancel)
					ShadowLooper.runUiThreadTasks()
					assertFalse(deadlines.hasPending)
					val cancelledDecision = readerPresentationDecision(common.state.presentation)
					assertNull(cancelledDecision.pendingTransitionToken)
					assertEquals(if (canRestoreSemanticFrame) retained else ReaderPresentationFrameOwner.Neutral,
						cancelledDecision.frameOwner)
					val cancelledFailure = assertIs<ReaderDiagnosticPresentation.Failure>(cancelledDecision.diagnosticPresentation)
					assertTrue(cancelledFailure.retryable)
					assertFalse(cancelledFailure.cancellable)
					if (!canRestoreSemanticFrame) {
						assertEquals(ReaderPresentationAuthority.Unavailable, cancelledDecision.authority)
						assertEquals(ReaderPresentationFailureReason.NativePresentationUnavailable, cancelledFailure.reason)
					}
					val cancelled = common.state
					lateTail()
					ShadowLooper.runUiThreadTasks()
					assertEquals(cancelled, common.state)
					assertEquals(if (canRestoreSemanticFrame) 0 else 1, predecessorReleases)
				}
				if (livenessBoundary == Task9LivenessBoundary.MissingSource ||
					livenessBoundary == Task9LivenessBoundary.RejectedSource) {
					producedRendererGenerations += preRetryRendererGenerations - 301L
					if (livenessBoundary == Task9LivenessBoundary.RejectedSource) {
						val beforeRejected = common.state
						visualLocation.invoke(viewer, targetOrdinal, "$correlation-stale", binding.foliateSessionId, null)
						assertEquals(beforeRejected, common.state)
						assertTrue(controller.awaitingPresentationRecoverySnapshot)
					}
					assertEquals(2, retryDeadlinePosts)
					verifyTimeoutRetryCancel {
						visualLocation.invoke(viewer, targetOrdinal, correlation, binding.foliateSessionId, null)
					}
					return@runTest
				}
				if (nativePipeline) {
					webView.retainPlanCallbacks = true
					val bundle = controller.javaClass.task7Field("bundleSource").get(controller) as ReaderPageTurnBundleSource
					val origin = IntArray(2).also(controller.surfaceView::getLocationInWindow)
					assertEquals(0, origin[0])
					assertEquals(0, origin[1])
					val quality = bundle.javaClass.task7Field("bitmapQuality").get(bundle) as ReaderPageBitmapQuality
					val bitmapWidth = readerPageTurnAnimationBitmapDimension(viewportWidth, quality)
					val bitmapHeight = readerPageTurnAnimationBitmapDimension(viewportHeight, quality)
					for (ordinal in listOf(targetOrdinal)) {
						assertNotNull(bundle.cacheCurrentSnapshot(ordinal, ReaderPageTurnTransitionKind.PortraitSlide,
							ReaderPageTurnCaptureResult(
								Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) },
								android.graphics.Rect(origin[0], origin[1], origin[0] + viewportWidth, origin[1] + viewportHeight),
								paige.navic.reader.ReaderPageTurnCaptureGeometry(viewportWidth.toDouble(), viewportHeight.toDouble(),
									paige.navic.reader.ReaderPageTurnLayoutMode.Single,
									listOf(paige.navic.reader.ReaderPageTurnPageRect(paige.navic.reader.ReaderPageTurnPageRole.Full,
										0.0, 0.0, viewportWidth.toDouble(), viewportHeight.toDouble()))), 1L)))
					}
					val reference = assertNotNull(bundle.retainedCurrentLayoutSnapshot(targetOrdinal,
						ReaderPageTurnTransitionKind.PortraitSlide))
					try {
						assertEquals(quality, reference.key.bitmapQuality)
						assertEquals(bitmapWidth, reference.bitmap.width)
						assertEquals(bitmapHeight, reference.bitmap.height)
						assertEquals(geometry.captureLeft, reference.surfaceRectInWindow.left)
						assertEquals(geometry.captureTop, reference.surfaceRectInWindow.top)
						assertEquals(geometry.captureRight, reference.surfaceRectInWindow.right)
						assertEquals(geometry.captureBottom, reference.surfaceRectInWindow.bottom)
						val hydrated = kotlinx.coroutines.CompletableDeferred<ReaderPageSlideSnapshot?>()
						val hydration = bundle.hydrateSnapshot(webView, targetOrdinal,
							ReaderPageTurnTransitionKind.PortraitSlide, reference, onHydrated = hydrated::complete)
						try { assertNotNull(hydrated.await()).release() } finally { hydration.cancel() }
					} finally { reference.release() }
					bundle.initializeRasterCache(webView)
					viewerClass.task7Method("setPageTurnPaginationStatus", String::class.java).invoke(viewer, "ready")
					controller.surfaceView.pageSurfaceListener.onCapabilitiesAvailable(RenderCapabilities(4096, 8L * 1024L * 1024L))
					assertTrue(webView.planCallbacks.isEmpty(), "Pending source must gate the actual plan producer")
				}
				assertRetainedProjection("raster-reference-and-pagination-setup")
				val destination = ReaderDestinationCommitIdentity(binding.foliateSessionId,
					assertNotNull(binding.destinationCommitIdentity).commitSequence + 1L)
				events.clear()
				updateDestination(destination)
				assertTrue(events.none { it is ReaderPresentationEvent.FoliateRelocated },
					"Compose model publication must wait for source ingress during correlated recovery")
				val snapshotCount = webView.recoveryReasons.size
				var checkingSourceContinuation = false
				val reentrantPrewarmGenerations = mutableListOf<Long>()
				if (!recoveryObservation) {
					@Suppress("UNCHECKED_CAST")
					val originalPrewarm = preparation.javaClass.task7Field("onRequestPrewarm").get(preparation) as () -> Unit
					preparation.javaClass.task7Field("onRequestPrewarm").set(preparation, {
						if (checkingSourceContinuation) {
							reentrantPrewarmGenerations += preparation.javaClass.task7Field("preparationGeneration").getLong(preparation)
							controller.onHostContentReady()
							assertTrue(controller.awaitingPresentationRecoverySnapshot,
								"Source continuation must fence reentrant work until fresh generation adoption")
							assertFalse(preparation.prewarmAdjacent(), "Reentrant prewarm must not start old-generation work")
						}
						originalPrewarm()
					})
				}
				visualLocation.invoke(viewer, targetOrdinal, "$correlation-stale", binding.foliateSessionId, null)
				assertTrue(events.none { it is ReaderPresentationEvent.FoliateRelocated })
				fun recoveryScalars(): String {
					val decision = controller.javaClass.task7Field("commonPresentationDecision").get(controller) as ReaderPresentationDecision
					val recovery = (decision.authority as? ReaderPresentationAuthority.BlockingPreparation)?.nativePresentationRequest
					val snapshot = controller.javaClass.task7Field("presentationRecoverySnapshot").get(controller)
					return "ordinal=$targetOrdinal authority=${decision.authority.javaClass.simpleName}" +
						" token=${decision.pendingTransitionToken?.value} retryFloor=${recovery?.retryAfterPreparationGeneration}" +
						" failure=${(decision.diagnosticPresentation as? ReaderDiagnosticPresentation.Failure)?.reason}" +
						" lifecycle=${decision.lifecycle} snapshot=${snapshot != null}" +
						" requestGeneration=${controller.javaClass.task7Field("requestGeneration").getLong(controller)}" +
						" currentOrdinal=${controller.javaClass.task7Field("currentOrdinal").getInt(controller)}" +
						" sessionRelocation=${controller.javaClass.task7Field("foliateSessionRelocationPending").getBoolean(controller)}" +
						" profile=${controller.javaClass.task7Field("publishedRasterProfileEpoch").get(controller)}" +
						" targetProfile=${decision.targetBinding?.profileGeneration}" +
						" targetPreparation=${decision.targetBinding?.preparationGeneration}" +
						" preparation=${preparation.javaClass.task7Field("preparationGeneration").getLong(preparation)}"
				}
				val beforeSource = recoveryScalars()
				checkingSourceContinuation = true
				visualLocation.invoke(viewer, targetOrdinal, if (recoveryObservation) correlation else "toc",
					binding.foliateSessionId, null)
				checkingSourceContinuation = false
				assertRetainedProjection("fresh-source-continuation")
				val afterSource = recoveryScalars()
				assertEquals(destination, common.state.presentation.binding?.destinationCommitIdentity)
				assertTrue(events.any { it is ReaderPresentationEvent.FoliateRelocated })
				assertEquals(assertNotNull(binding.preparationGeneration) + 1L,
					preparation.javaClass.task7Field("preparationGeneration").getLong(preparation),
					"Admitted source must continue one fresh attempt; before=[$beforeSource] after=[$afterSource]")
				if (!recoveryObservation) {
					assertTrue(reentrantPrewarmGenerations.contains(assertNotNull(binding.preparationGeneration) + 1L),
						"The actual allocator's prewarm callback must exercise the reentrant fence")
					assertFalse(controller.matchesPresentationRecoverySnapshot(correlation, binding.foliateSessionId))
					controller.onHostContentReady()
					assertEquals(snapshotCount, webView.recoveryReasons.size,
						"Already admitted external source must not dispatch a redundant snapshot")
					val acceptedState = common.state
					val acceptedScalars = recoveryScalars()
					visualLocation.invoke(viewer, 2, correlation, binding.foliateSessionId, null)
					assertEquals(acceptedState, common.state)
					assertEquals(acceptedScalars, recoveryScalars(), "Retired diagnostic must have no continuation effects")
					assertEquals(destination.commitSequence,
						common.state.presentation.binding?.destinationCommitIdentity?.commitSequence)
				}
				assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
				if (nativePipeline) {
					val callbacks = webView.planCallbacks.toList()
					webView.planCallbacks.clear()
					val planCallback = assertNotNull(callbacks.lastOrNull(), "Fresh source must reach the actual Foliate plan producer")
					val targets = readerPageRasterBlockingWindow(targetOrdinal, 1, 3).joinToString(",") { ordinal ->
						val priority = when (ordinal) {
							targetOrdinal -> "current"
							targetOrdinal + 1 -> "next-transition"
							targetOrdinal - 1 -> "previous-transition"
							else -> "current-chapter"
						}
						val authority = if (ordinal == targetOrdinal) "CurrentLive" else "OffscreenPassive"
						"""{"pageIndex":$ordinal,"priority":"$priority","authority":"$authority"}"""
					}
					val plan = """{"context":{"centerPageIndex":$targetOrdinal,"pageCount":3,"layoutMode":"single","step":1,"currentChapterIndex":0,"currentChapterPageStartIndex":0,"currentChapterPageCount":3},"targets":[$targets]}"""
					val parsedPlan = assertNotNull(readerPageRasterPreparationPlan(plan), "Synthetic source plan must satisfy the real parser")
					assertNotNull(parsedPlan.blockingTargetsOrNull(), "Synthetic source plan must contain the full required chapter/window")
					assertTrue(readerPageRasterCalibrationTargets(parsedPlan.targets).isNotEmpty())
					assertTrue(viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer))
					val profileResolved = kotlinx.coroutines.CompletableDeferred<Unit>()
					@Suppress("UNCHECKED_CAST")
					val originalProfile = controller.javaClass.task7Field("onRasterProfileEpochChanged")
						.get(controller) as (Long?) -> Unit
					controller.javaClass.task7Field("onRasterProfileEpochChanged").set(controller, { epoch: Long? ->
						originalProfile(epoch)
						if (epoch != null) profileResolved.complete(Unit)
					})
					assertTrue(adapter.isAvailable)
					assertSame(adapter, actualPortProvider())
					assertEquals(0, runtime.pendingCallbackCount)
					assertFalse(preparation.shouldSuppressViewerContentInput)
					assertFalse(controller.awaitingPresentationRecoverySnapshot)
					assertTrue(webView.isAttachedToWindow)
					assertEquals(targetOrdinal, preparation.javaClass.task7Field("currentVisualPageIndex").get(preparation))
					assertEquals(assertNotNull(binding.preparationGeneration) + 1L,
						preparation.javaClass.task7Field("preparationGeneration").getLong(preparation))
					val bundle = controller.javaClass.task7Field("bundleSource").get(controller) as ReaderPageTurnBundleSource
					assertSame(bundle, preparation.javaClass.task7Field("bundleSource").get(preparation))
					assertNotNull(bundle.retainedCurrentLayoutSnapshot(targetOrdinal, ReaderPageTurnTransitionKind.PortraitSlide),
						"Actual current-generation cache reference must be available before the worker plan").release()
					assertTrue(webView.width > 0 && webView.height > 0 && webView.width < webView.height * 1.12f,
						"Actual receiver WebView must match the single-page source plan; " +
							"source=${webView.width}x${webView.height} host=${viewer.width}x${viewer.height}")
					planCallback.onReceiveValue(plan)
					// Active pagination readiness is an output of the actual profile producer,
					// not a prerequisite while its retained source-plan callback is unanswered.
					if (viewerClass.task7Field("rasterProfileEpoch").get(viewer) == null) {
						withContext(Dispatchers.Default) { withTimeout(5_000L) { profileResolved.await() } }
					}
					assertTrue(viewerClass.task7Field("rasterPaginationReady").getBoolean(viewer))
					assertTrue(preparation.prewarmAdjacent(), "Admitted source must start fresh raster preparation; " +
						"coldAdmitted=${viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer)} " +
						"paginationReady=${viewerClass.task7Field("rasterPaginationReady").getBoolean(viewer)} " +
						"awaitingSource=${controller.awaitingPresentationRecoverySnapshot} " +
						"attached=${webView.isAttachedToWindow} passiveAvailable=${adapter.isAvailable} " +
						"destroyed=${preparation.javaClass.task7Field("destroyed").getBoolean(preparation)} " + recoveryScalars())
					val preparationPlan = assertNotNull(webView.planCallbacks.lastOrNull(), "Actual raster worker must query its source plan")
					webView.planCallbacks.clear()
					preparationPlan.onReceiveValue(plan)
					var lastBatchFailure: ReaderPageRasterBatchOutcome.Failed? = null
					for (ordinal in parsedPlan.targets.filter { it.authority == ReaderPageRasterTargetAuthority.OffscreenPassive }
						.map { it.pageIndex }) {
						val pendingCommit = withContext(Dispatchers.Default) {
							kotlinx.coroutines.withTimeoutOrNull(5_000L) { runtime.commits.receive() }
						}
						val state = viewerClass.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState
						val batch = adapter.javaClass.task7Field("activeBatch").get(adapter)
						val commit = assertNotNull(pendingCommit, "Physical commit missing; ordinal=$ordinal " +
							"phase=${state.phase} completed=${state.completedCount}/${state.requiredCount} " +
							"batch=${batch != null} targetIndex=${batch?.javaClass?.task7Field("targetIndex")?.get(batch)} " +
							"manifestRequests=${webView.manifestRequests} descriptorRequests=${webView.descriptorRequests} " +
							"staged=${bundle.ownershipMetrics().stagedPublications} " +
							"callbacks=${bundle.ownershipMetrics().pendingPublicationCallbacks} " +
							"admissionFailure=${lastBatchFailure?.passiveRasterRejection} " +
							"publicationResult=${lastBatchFailure?.persistentPublicationResult} " +
							"writeFailure=${lastBatchFailure?.persistentWriteFailureReason} " + recoveryScalars())
						val actualBatch = assertNotNull(batch)
						val completionField = actualBatch.javaClass.task7Field("onComplete")
						@Suppress("UNCHECKED_CAST")
						val originalCompletion = completionField.get(actualBatch) as (ReaderPageRasterBatchOutcome) -> Unit
						completionField.set(actualBatch, { outcome: ReaderPageRasterBatchOutcome ->
							lastBatchFailure = outcome as? ReaderPageRasterBatchOutcome.Failed
							originalCompletion(outcome)
						})
						assertEquals(ordinal, commit.manifest.visualPageOrdinal)
						assertEquals(bundle.currentGeneration(), commit.manifest.rasterGeneration)
						assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
						assertEquals(0, predecessorReleases)
						runtime.completeCommit(commit)
						val capture = withContext(Dispatchers.Default) {
							withTimeout(5_000L) { runtime.captures.receive() }
						}
						assertEquals(geometry, capture.geometry)
						runtime.completeCapture(capture)
					}
					assertEquals(assertNotNull(binding.preparationGeneration) + 1L,
						withContext(Dispatchers.Default) { withTimeout(5_000L) { rasterProofReady.await() } })
					assertEquals(0, runtime.pendingCallbackCount)
					val proofPlans = webView.planCallbacks.toList()
					webView.planCallbacks.clear()
					proofPlans.lastOrNull()?.onReceiveValue(plan)
					withContext(Dispatchers.Default) {
						withTimeout(5_000L) {
							(controller.javaClass.task7Field("rasterJob").get(controller) as Job).children.toList().joinAll()
						}
					}
					@Suppress("UNCHECKED_CAST")
					val owners = controller.javaClass.task7Field("generationOwners").get(controller) as Map<Long, Any>
					val activation = controller.javaClass.task7Field("lastActivationTrace").get(controller) as String?
					val activationEnums = Regex("(?:event|detail)=[a-zA-Z-]+")
						.findAll(activation.orEmpty()).map { it.value }.toList()
					val successorGeneration = assertNotNull(
						controller.javaClass.task7Field("activeDeckGenerationId").get(controller) as Long?,
						"Actual plan/raster pipeline must select a successor renderer generation; " +
							"generations=${owners.keys.sorted()} proofPlans=${proofPlans.size} remainingPlans=${webView.planCallbacks.size} " +
							"activation=$activationEnums " + recoveryScalars())
					assertTrue(successorGeneration != 301L && owners.containsKey(successorGeneration))
					producedRendererGenerations += owners.keys.filter { it != 301L }.sorted()
					@Suppress("UNCHECKED_CAST")
					val fences = controller.javaClass.task7Field("generationCallbackFences").get(controller)
						as Map<Long, ReaderAcceptedDeckCallbackFence>
					val fenceScalars = "selected=$successorGeneration owners=${owners.keys.sorted()} " +
						"fences=${fences.map { (generation, fence) -> listOf(generation, fence.presentationToken?.value, fence.binding.preparationGeneration, fence.binding.profileGeneration, fence.binding.rasterGeneration, fence.binding.textureGeneration) }} " +
						"prepared=${controller.javaClass.task7Field("preparedDeckGenerations").get(controller)} " +
						"releases=$rendererReleaseCounts " + recoveryScalars()
					val retryRendererGenerations = producedRendererGenerations - preRetryRendererGenerations
					assertTrue(successorGeneration in retryRendererGenerations,
						"Selected successor must be produced after Retry, not imported/preclaim material; $fenceScalars")
					for (generation in retryRendererGenerations) {
						val fence = assertNotNull(fences[generation], "Actual produced callback fence missing; generation=$generation $fenceScalars")
						assertEquals(retryEffect.token, fence.presentationToken, "Actual produced callback token; generation=$generation $fenceScalars")
						assertEquals(assertNotNull(common.state.presentation.binding).copy(
							preparationGeneration = assertNotNull(binding.preparationGeneration) + 1L,
							rasterGeneration = bundle.currentGeneration(), textureGeneration = generation), fence.binding)
					}
					// Drain only already-produced main callbacks before injecting any prepared
					// callback, to distinguish submission-time release from stale-tail handling.
					fun materialOwnershipScalars(): String {
						val leases = controller.surfaceView.javaClass.task7Field("leaseRegistry").get(controller.surfaceView)
						val inlineShield = controller.javaClass.task7Field("inlineRasterShield").get(controller) as ReaderPageInlineRasterShield
						val owner = readerPresentationDecision(common.state.presentation).frameOwner as? ReaderPresentationFrameOwner.Curl
						return "owners=${owners.keys.sorted()} leases=${leases.javaClass.task7Method("size").invoke(leases)} " +
							"lease301=${task8SurfaceOwnsGeneration(controller.surfaceView, 301L)} " +
							"leaseSelected=${task8SurfaceOwnsGeneration(controller.surfaceView, successorGeneration)} " +
							"staticShield=${preparation.hasStaticRasterShieldOwnership()} inlineShield=${inlineShield.ownsPresentation()} " +
							"commonFrameToken=${owner?.frame?.token?.value} commonTexture=${owner?.frame?.textureGeneration} " +
							"surfaceAlpha=${controller.surfaceView.alpha} inlineAlpha=${controller.inlineRasterShieldView.alpha}"
					}
					assertRetainedProjection("fresh-renderer-submission")
					if (projectionOnly) return@runTest
					val beforeReleaseDelivery = materialOwnershipScalars()
					ShadowLooper.runUiThreadTasks()
					val afterReleaseDelivery = materialOwnershipScalars()
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertEquals(0, predecessorReleases,
						"Predecessor must survive actual fresh submission before any prepared/frame proof; " +
							"before=[$beforeReleaseDelivery] after=[$afterReleaseDelivery] " +
							"releases=$rendererReleaseCounts reasons=$rendererReleaseReasons " + recoveryScalars())
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					val obsoleteGenerations = producedRendererGenerations.filter { it != successorGeneration }
					for (obsolete in obsoleteGenerations) {
						controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
							.invoke(controller.surfaceView, obsolete)
						ShadowLooper.runUiThreadTasks()
						assertEquals(1, rendererReleaseCounts[obsolete])
						assertFalse(owners.containsKey(obsolete))
						assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, obsolete))
						assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
						assertEquals(0, predecessorReleases)
						assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
						assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, successorGeneration))
						controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
							.invoke(controller.surfaceView, obsolete)
						assertEquals(1, rendererReleaseCounts[obsolete])
					}
					assertNull(rendererReleaseCounts[successorGeneration])
					controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, successorGeneration)
					ShadowLooper.runUiThreadTasks()
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertEquals(0, predecessorReleases)
					val candidate = assertNotNull(viewerClass.task7Method("currentNativePagePresentationCandidateOrNull")
						.invoke(viewer) as ReaderNativePagePresentationCandidate?,
						"Actual fresh raster/deck outputs must reach the native publisher candidate")
					assertEquals(successorGeneration, candidate.binding.textureGeneration)
					assertEquals(assertNotNull(binding.preparationGeneration) + 1L, candidate.binding.preparationGeneration)
					val physicalRenderer = controller.surfaceView.javaClass.task7Field("renderer").get(controller.surfaceView)
					val physicalActive = physicalRenderer.javaClass.task7Field("activeDeck").get(physicalRenderer) as karacken.curl.PageDeck<*>
					assertEquals(successorGeneration, physicalActive.generationId,
						"The actual publisher's authorized queue must activate the candidate before its proof draw")
					val physicalFront = physicalRenderer.javaClass.task7Field("portraitFrontResource").get(physicalRenderer) as karacken.curl.PageImage<*>
					assertEquals(successorGeneration, physicalFront.generationId)
					val physicalRetained = physicalRenderer.javaClass.task7Field("replacementDeck").get(physicalRenderer) as karacken.curl.PageDeck<*>
					assertEquals(301L, physicalRetained.generationId)
					val frames = controller.surfaceView.javaClass.task7Field("presentedFrameRequest").get(controller.surfaceView)
					val completion = frames.javaClass.task7Method("markRendered").invoke(frames) as Long
					if (livenessBoundary == Task9LivenessBoundary.MissingFrame ||
						livenessBoundary == Task9LivenessBoundary.HiddenFrame) {
						assertEquals(2, retryDeadlinePosts)
						assertEquals(retryDeadlinePosts, deadlines.postCount,
							"Source/profile/raster/deck continuation must retain the original Retry deadline")
						if (livenessBoundary == Task9LivenessBoundary.HiddenFrame) {
							val token = readerPresentationDecision(common.state.presentation).pendingTransitionToken
							nowMillis += 4_000L
							viewerClass.task7Method("onWindowVisibilityChanged", Integer.TYPE).invoke(viewer, View.INVISIBLE)
							ShadowLooper.runUiThreadTasks()
							assertFalse(deadlines.hasPending)
							assertEquals(ReaderPresentationLifecycleState.Background, common.state.presentation.lifecycle)
							assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
							assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
							assertEquals(0, predecessorReleases)
							nowMillis += 40_000L
							controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, completion)
							assertNull(common.state.presentation.failure)
							assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
							viewerClass.task7Method("onWindowVisibilityChanged", Integer.TYPE).invoke(viewer, View.VISIBLE)
							ShadowLooper.runUiThreadTasks()
							assertEquals(ReaderPresentationLifecycleState.Foreground, common.state.presentation.lifecycle)
							assertEquals(token, readerPresentationDecision(common.state.presentation).pendingTransitionToken)
							assertEquals(6_000L, deadlineDelayMillis)
							assertEquals(retryDeadlinePosts + 1, deadlines.postCount)
						}
						verifyTimeoutRetryCancel {
							controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, completion)
							callbacks.forEach { it.onReceiveValue(plan) }
						}
						return@runTest
					}
					if (failRetainedSelected) {
						val failedRequest = assertIs<ReaderPresentationAuthority.BlockingPreparation>(common.state.presentation.authority)
							.nativePresentationRequest
						assertEquals(candidate.binding, assertNotNull(failedRequest).binding)
						assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
						assertTrue(controller.javaClass.task7Method("generationBacksCommonPresentation", java.lang.Long.TYPE)
							.invoke(controller, 301L) as Boolean)
						assertFalse(controller.javaClass.task7Field("activeDeckGenerationId").get(controller) == 301L)
						assertFalse(controller.javaClass.task7Field("pendingDeckGenerationId").get(controller) == 301L)
						@Suppress("UNCHECKED_CAST")
						val failedDeck = physicalRetained as PageDeck<Bitmap>
						failedDeck.pages.first { !it.isFiller }.content.recycle()
						assertFalse(physicalRenderer.javaClass.task7Method("rehydrateDeck",
							PageDeck::class.java, PageDeck::class.java, PageDeck::class.java)
							.invoke(physicalRenderer, failedDeck, physicalActive, failedDeck) as Boolean)
						ShadowLooper.runUiThreadTasks()
						val failureDecision = readerPresentationDecision(common.state.presentation)
						val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(failureDecision.diagnosticPresentation,
							"Actual selected retained-material failure must reach current authority")
						assertEquals(ReaderPresentationFailureReason.RendererLost, diagnostic.reason)
						assertTrue(diagnostic.retryable)
						assertFalse(diagnostic.cancellable)
						assertEquals(1, events.count {
							it is ReaderPresentationEvent.Lifecycle && it.event == ReaderPresentationLifecycleEvent.RendererLost
						})
						assertEquals(ReaderPresentationFrameOwner.Neutral, failureDecision.frameOwner)
						assertEquals(candidate.binding, common.state.presentation.binding)
						assertEquals(failedRequest, assertIs<ReaderPresentationAuthority.BlockingPreparation>(failureDecision.authority)
							.nativePresentationRequest)
						assertEquals(1, events.filterIsInstance<ReaderPresentationEvent.BindingReplaced>().count {
							it.previousBinding == retained.frame.binding &&
								it.binding == retained.frame.binding.copy(rasterGeneration = null, textureGeneration = null)
						})
						assertEquals(1, predecessorReleases)
						assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
						assertFalse(owners.containsKey(301L))
						val failed = common.state
						controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
							.invoke(controller.surfaceView, completion)
						callbacks.forEach { it.onReceiveValue(plan) }
						ShadowLooper.runUiThreadTasks()
						assertEquals(failed, common.state)
						assertEquals(1, predecessorReleases)
						return@runTest
					}
					controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, completion)
					assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority)
					// Selection moves synchronously; the renderer terminal is posted to main.
					ShadowLooper.runUiThreadTasks()
					assertEquals(1, predecessorReleases)
					val successor = common.state
					callbacks.forEach { it.onReceiveValue(plan) }
					controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, completion)
					assertEquals(successor, common.state)
					assertEquals(1, predecessorReleases)
				}
			} catch (failure: Throwable) {
				bodyFailure = failure
				throw failure
			} finally {
				sustainedBridge?.dispose()
				assertFalse(deadlines.hasPending)
				try {
					controller.surfaceView.detach()
					donor?.controller?.surfaceView?.detach()
					if (nativePipeline) controller.surfaceView.surfaceDestroyed(controller.surfaceView.holder)
					viewerClass.task7Method("closeReader").invoke(viewer)
					val owners = listOfNotNull(preparation.destroy(), controller.destroy(), bundle.close(),
						donor?.controller?.destroy())
					withContext(Dispatchers.Default) { withTimeout(5_000L) { owners.forEach { it.await() } } }
					ShadowLooper.runUiThreadTasks()
					assertEquals(0, controller.surfaceView.pendingCallbackCount)
					assertEquals(0, controller.applicationOwnershipMetrics().pendingVisualCallbacks)
					for (generation in producedRendererGenerations) {
						assertEquals(1, rendererReleaseCounts[generation])
						assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, generation))
					}
					assertTrue(runtime.isRetired)
					assertEquals(0, runtime.pendingCallbackCount)
					assertEquals(0, bundle.ownershipMetrics().pendingPublicationCallbacks)
					assertTrue(runtime.deliveredBitmaps.all { it.isRecycled })
				} catch (cleanupFailure: Throwable) {
					val original = bodyFailure
					if (original == null) throw cleanupFailure
					if (original !== cleanupFailure) original.addSuppressed(cleanupFailure)
				} finally {
					runtime.destroy()
					webView.sourceBinding = null
					webView.planCallbacks.clear()
					activity.pause().stop().destroy()
				}
			}
		} finally {
			Dispatchers.resetMain()
		}
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9RealCurlSettlementTimeoutRetainsRendererAndBoundsFreshRecovery() {
		var releases = 0
		val fixture = task8CurlAuthorityFixture(
			task8SettledCurlSourceState(),
			onActiveRasterReleased = { releases += 1 }
		)
		val controller = fixture.controller
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(requireNotNull(fixture.store.state?.binding)) { decision ->
				controller.synchronizePresentationDecision(decision)
			},
			onEvent = fixture.store::publish
		)
		try {
			assertEquals(ReaderPageTurnStartResult.Settling,
				controller.start(220L, PageChange.NEXT) { _, _ -> true })
			controller.cancelGesture(220L)
			val pending = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertIs<ReaderPresentationAuthority.CurlSettlementPending>(pending.authority)
			val material = pending.frameOwner
			val alpha = controller.surfaceView.alpha
			bridge.update(pending)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			val timedOut = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(timedOut.diagnosticPresentation).reason)
			assertEquals(material, timedOut.frameOwner)
			assertEquals(alpha, controller.surfaceView.alpha)
			assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
			assertEquals(0, releases)
			val retryReceipt = requireNotNull(fixture.store.publish(ReaderPresentationEvent.Retry))
			val retry = readerPresentationDecision(retryReceipt.postState)
			assertIs<ReaderPresentationAuthority.BlockingPreparation>(retry.authority)
			assertEquals(material, retry.frameOwner)
			bridge.update(retry)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(
					readerPresentationDecision(requireNotNull(fixture.store.state)).diagnosticPresentation).reason)
			assertEquals(0, releases)
			assertEquals(0, fixture.store.presentationEvents.count {
				it is ReaderPresentationEvent.Lifecycle &&
					it.event == ReaderPresentationLifecycleEvent.RendererLost
			})
		} finally { bridge.dispose() }
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun acceptedTapClaimPublishesItsExactCancellationTerminalOnce() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val gestureId = 204L
		val tapTerminals = mutableListOf<ReaderPageGestureTerminalOutcome>()

		assertEquals(
			ReaderPageTurnStartResult.Settling,
			fixture.controller.start(gestureId, PageChange.NEXT) { outcome, _ ->
				tapTerminals += outcome
				true
			}
		)
		assertIs<ReaderPresentationAuthority.CurlGesture>(
			fixture.store.state?.authority
		)

		fixture.controller.cancelGesture(gestureId)

		assertEquals(listOf(ReaderPageGestureTerminalOutcome.CancelledByUser), tapTerminals)
		assertTrue(fixture.store.localTerminalGestureIds.isEmpty())
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlClaimed
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
			fixture.store.state?.authority
		)
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun currentRendererFailureRetainsBridgeSelectedCurlWhileCleaningGestureOnce() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val recoveredGenerations = fixture.controller.javaClass
			.task7Field("recoveredDeckGenerations")
			.get(fixture.controller) as MutableSet<Long>
		assertTrue(recoveredGenerations.remove(301L))
		val projection = task8BindRendererFailureProjection(fixture)
		val gestureId = 206L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		assertEquals(
			ReaderPageCurlDispatchResult.Accepted,
			fixture.controller.onPageTouchEvent(down, gestureId)
		)
		assertEquals(1f, fixture.controller.surfaceView.alpha)

		task8DeliverSurfaceRenderFailure(
			fixture.controller.surfaceView,
			RenderFailure(
				301L,
				true,
				RenderFailureReason.TEXTURE_UPLOAD,
				"critical-5-current",
				null
			)
		)

		val decision = readerPresentationDecision(requireNotNull(fixture.store.state))
		val retained = assertIs<ReaderPresentationFrameOwner.Curl>(decision.frameOwner)
		assertEquals(gestureId, retained.frame.token.value)
		val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(
			decision.diagnosticPresentation
		)
		assertEquals(ReaderPresentationFailureReason.RendererLost, diagnostic.reason)
		assertEquals(decision, projection.projectedDecisions.last())
		assertEquals(1f, fixture.controller.surfaceView.alpha)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.Lifecycle &&
				it.event == ReaderPresentationLifecycleEvent.RendererLost
		})
		assertEquals(0, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.LiveEngineExposureCommitted
		})
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		assertTrue(task8SurfaceOwnsGeneration(fixture.controller.surfaceView, 301L))
		projection.bridge.dispose()
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun destructiveActiveRendererRehydrationProjectsNeutralAfterActualRelease() {
		var rasterReleaseCount = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			onActiveRasterReleased = { rasterReleaseCount += 1 }
		)
		val surface = fixture.controller.surfaceView
		val recoveredGenerations = fixture.controller.javaClass
			.task7Field("recoveredDeckGenerations")
			.get(fixture.controller) as MutableSet<Long>
		assertTrue(recoveredGenerations.remove(301L))
		val attachedHost = FrameLayout(RuntimeEnvironment.getApplication()).apply {
			addView(surface)
		}
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		activityController.get().setContentView(attachedHost)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertTrue(surface.isAttachedToWindow)
		val projection = task8BindRendererFailureProjection(fixture)
		assertEquals(1f, surface.alpha)

		task8FailActiveRendererRehydration(surface)

		assertFalse(task8SurfaceOwnsGeneration(surface, 301L))
		val owners = fixture.controller.javaClass.task7Field("generationOwners")
			.get(fixture.controller) as MutableMap<Long, Any>
		assertFalse(owners.containsKey(301L))
		assertEquals(1, rasterReleaseCount)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.Lifecycle &&
				it.event == ReaderPresentationLifecycleEvent.RendererLost
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.BindingReplaced
		})
		val decision = readerPresentationDecision(requireNotNull(fixture.store.state))
		val blocking = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			decision.authority
		)
		assertEquals(ReaderPresentationFrameOwner.Neutral, blocking.retainedFrame)
		assertEquals(ReaderPresentationFrameOwner.Neutral, decision.frameOwner)
		assertNull(decision.targetBinding?.rasterGeneration)
		assertNull(decision.targetBinding?.textureGeneration)
		val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(
			decision.diagnosticPresentation
		)
		assertEquals(ReaderPresentationFailureReason.RendererLost, diagnostic.reason)
		assertEquals(decision, projection.projectedDecisions.last())
		assertEquals(0f, surface.alpha)
		projection.bridge.dispose()
		activityController.pause().stop().destroy()
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun rendererLossNeutralOwnerHidesReleasedMaterialOnlyThroughBridgeProjection() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val applyFrameOwner = viewerClass.task7Method(
			"applyPresentationFrameOwner",
			paige.navic.reader.ReaderPresentationDecision::class.java
		)
		val controller = viewerClass.task7Field("playLikeCurlController").get(viewer) as
			ReaderPlayLikeCurlFoliateController
		controller.surfaceView.alpha = 1f
		controller.inlineRasterShieldView.alpha = 1f
		val deckless = requireNotNull(task8SettledCurlSourceState().binding).copy(
			rasterGeneration = null,
			textureGeneration = null
		)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral
			),
			binding = deckless,
			failure = ReaderDiagnosticPresentation.Failure(
				reason = ReaderPresentationFailureReason.RendererLost,
				retryable = true,
				cancellable = false
			)
		)
		val decision = readerPresentationDecision(state)
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(deckless) { projected ->
				applyFrameOwner.invoke(viewer, projected)
			},
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(Task7VisualHandoffHost()),
			liveEngineExposureRequired = { false }
		) { event -> readerTestPresentationReceipt(event, state) }

		bridge.update(decision)

		assertEquals(0f, controller.surfaceView.alpha)
		assertEquals(0f, controller.inlineRasterShieldView.alpha)
		bridge.dispose()
		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun staleRendererFailureReleasesOnlyStaleControllerOwnershipWithoutHidingCurrentFrame() {
		val initial = task8SettledCurlSourceState()
		val fixture = task8CurlAuthorityFixture(initial)
		val projection = task8BindRendererFailureProjection(fixture)
		val controllerClass = fixture.controller.javaClass
		val activePages = checkNotNull(
			controllerClass.task7Field("activePages").get(fixture.controller)
		)
		val generations = activePages.javaClass.task7Field("generations")
			.get(activePages) as MutableSet<Long>
		val owners = controllerClass.task7Field("generationOwners")
			.get(fixture.controller) as MutableMap<Long, Any>
		val staleGeneration = 302L
		generations += staleGeneration
		owners[staleGeneration] = activePages
		val failure = RenderFailure(
			staleGeneration,
			true,
			RenderFailureReason.TEXTURE_UPLOAD,
			"critical-5-stale",
			null
		)

		fixture.controller.surfaceView.pageSurfaceListener.onRenderFailure(failure)
		assertFalse(owners.containsKey(staleGeneration))
		assertFalse(generations.contains(staleGeneration))
		fixture.controller.surfaceView.pageSurfaceListener.onRenderFailure(failure)

		assertEquals(initial, fixture.store.state)
		assertTrue(fixture.store.presentationEvents.isEmpty())
		assertEquals(1f, fixture.controller.surfaceView.alpha)
		assertEquals(1, projection.projectedDecisions.size)
		assertTrue(owners.containsKey(301L))
		assertTrue(task8SurfaceOwnsGeneration(fixture.controller.surfaceView, 301L))
		projection.bridge.dispose()
	}

	@Test
	fun exactCurlRelocationSkipsLiveExposureAndReleasesTransportBeforeNativeProof() {
		val queue = ReaderPageRelocationQueue()
		val first = enqueueTask7Relocation(
			queue = queue,
			gestureId = 81L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "curl-native-settlement-session"
		)
		val second = enqueueTask7Relocation(
			queue = queue,
			gestureId = 82L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			foliateSessionId = first.foliateSessionId
		)
		assertEquals(first, queue.commandToDispatch())
		val sourceBinding = ReaderPresentationBinding(
			foliateSessionId = first.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.sourceOrdinal.toLong()
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val destinationBinding = sourceBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.destinationOrdinal.toLong()
			),
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val acknowledgement = ReaderPageTurnSettlementAck(
			token = first.token.value,
			pageIndex = first.destinationOrdinal,
			foliateSessionId = first.foliateSessionId,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val sourceProof = ReaderNativePagePresentationProof(
			binding = sourceBinding,
			transitionToken = null,
			presentedFrame = 80L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
			textureGeneration = requireNotNull(sourceBinding.textureGeneration)
		)
		val settled = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(sourceProof)
			),
			binding = sourceBinding
		)
		val claimed = readerPresentationReduce(
			settled,
			requireNotNull(
				readerCurlClaimEvent(
					readerPresentationDecision(settled),
					first.gestureId
				)
			)
		).state
		val awaitingFoliate = readerPresentationReduce(
			claimed,
			ReaderPresentationEvent.CurlTerminal(
				token = ReaderPresentationToken(first.gestureId),
				binding = sourceBinding,
				expectedAcknowledgement = acknowledgement
			)
		).state
		val presentation = Task7PresentationStore(awaitingFoliate)
		val foregroundOwnership = ReaderForegroundWebViewOwnership()
		val transportDispatches = mutableListOf<ReaderPageRelocationRequest>()
		val liveDispatch = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = foregroundOwnership,
			isDispatchCurrent = { request ->
				request == queue.head() && queue.hasInFlightHead()
			},
			dispatchExact = { request, _ ->
				transportDispatches += request
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, _ -> }
		)
		val firstClaim = foregroundOwnership.acquireLive(first.gestureId)
		val secondClaim = foregroundOwnership.acquireLive(second.gestureId)
		assertTrue(liveDispatch.transfer(first, firstClaim))
		assertTrue(liveDispatch.transfer(second, secondClaim))
		assertTrue(liveDispatch.dispatch(first))
		var currentWebViewOrdinal = first.sourceOrdinal
		val webViewRequests = mutableListOf<ReaderPresentationEvent.WebViewHandoffRequested>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val hostEffects = mutableListOf<ReaderPresentationFrameOwner>()
		val visualHost = Task7VisualHandoffHost()
		val commitHost = FakeReaderPresentationCommitHost(sourceBinding) { decision ->
			hostEffects += decision.frameOwner
		}
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = visualHost,
			currentState = {
				ReaderPageRelocationVisualState(
					attached = true,
					resumed = true,
					foliateSessionId = first.foliateSessionId,
					webViewOrdinal = currentWebViewOrdinal,
					rasterGeneration = first.rasterGeneration,
					textureGeneration = first.textureGeneration
				)
			},
			dispatch = { request ->
				check(liveDispatch.dispatch(request))
			},
			publishRecovery = { request, reason ->
				recoveries += reason
				liveDispatch.fail(
					request,
					ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
				)
			},
			finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
			validateContent = { _, _ ->
				error("Curl settlement must not validate WebView content")
			},
			requestPresentationHandoff = {
				val event = ReaderPresentationEvent.WebViewHandoffRequested(
					ReaderLiveEngineHandoffDirection.NativeToLiveEngine
				)
				webViewRequests += event
				val receipt = presentation.publish(event)
				if (!receipt.authorizes(event)) {
					null
				} else {
					(readerPresentationDecision(receipt.postState).requiredTransition as?
						ReaderRequiredTransition.ExposeLiveEngine)
				}
			},
			commitLiveEngineExposure = { proof ->
				presentation.publish(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
			},
			publishLiveEngineHandoffTerminal = presentation::publish,
			onCompleted = { request ->
				completed += request
				check(liveDispatch.complete(request))
			}
		)
		val bridge = ReaderPresentationHostBridge(
			host = commitHost,
			liveEngineExposureRequired = { false },
			onEvent = presentation::publish
		)
		val bindingReporter = ReaderPresentationBindingReporter()
		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			bindingReporter.update(
				confirmedTargetBinding = sourceBinding,
				currentBinding = destinationBinding,
				publicationOpenPending = false,
				relocationPending = true,
				relocationAcknowledgement = acknowledgement
			)
		)
		currentWebViewOrdinal = first.destinationOrdinal
		val relocationReceipt = presentation.publish(relocation)
		assertEquals(ReaderPresentationEventDisposition.Accepted, relocationReceipt.disposition)
		val awaitingNativeDecision = readerPresentationDecision(presentation.state)
		commitHost.currentBinding = destinationBinding
		bridge.update(awaitingNativeDecision)
		val awaitingNative = assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
			awaitingNativeDecision.authority
		)
		assertEquals(ReaderCurlSettlementStage.AwaitingNativePresentation, awaitingNative.stage)
		val nativeTransition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			awaitingNativeDecision.requiredTransition
		)
		assertTrue(
			queue.acknowledge(
				first.token.value,
				first.destinationOrdinal,
				first.foliateSessionId,
				first.rasterGeneration,
				first.textureGeneration
			)
		)

		val settledByAuthority =
			coordinator.synchronizeCommonPresentationDecision(awaitingNativeDecision)
		val acknowledged = settledByAuthority || coordinator.onAcknowledged(first)
		if (!acknowledged) {
			liveDispatch.fail(
				first,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
		}

		assertTrue(
			webViewRequests.isEmpty(),
			"Exact curl acknowledgement requested obsolete WebView exposure"
		)
		assertTrue(recoveries.isEmpty())
		assertEquals(listOf(first), completed)
		assertEquals(second, queue.head())
		assertFalse(queue.hasDispatchedHead())
		assertEquals(listOf(first), transportDispatches)
		assertEquals(1, foregroundOwnership.snapshot().liveClaims)
		assertFalse(liveDispatch.isCurrent(second))
		assertEquals(awaitingNative.retainedFrame, awaitingNativeDecision.frameOwner)
		assertIs<ReaderPresentationFrameOwner.Curl>(hostEffects.last())
		assertFalse(coordinator.synchronizeCommonPresentationDecision(awaitingNativeDecision))
		assertEquals(listOf(first), completed)
		assertFalse(queue.hasDispatchedHead())
		assertEquals(listOf(first), transportDispatches)

		var nativeCandidate = ReaderNativePagePresentationCandidate(
			binding = destinationBinding,
			transitionToken = ReaderPresentationToken(nativeTransition.token.value + 1L),
			visualPageIndex = first.destinationOrdinal,
			viewportWidth = 1200,
			viewportHeight = 800,
			preparationFacts = ReaderPagePreparationFacts(
				phase = ReaderPagePreparationPhase.Ready,
				generation = requireNotNull(destinationBinding.preparationGeneration)
			)
		)
		val frameSource = Task8PresentedFrameSource()
		val nativeReceipts = mutableListOf<ReaderPresentationEventReceipt>()
		val nativePublisher = ReaderNativePagePresentationPublisher(
			frameSource = frameSource,
			currentCandidate = { nativeCandidate },
			onEvent = { event ->
				presentation.publish(event).also(nativeReceipts::add)
			}
		)
		nativePublisher.update()
		frameSource.presentNext()

		val staleNativeReceipt = nativeReceipts.single()
		assertEquals(ReaderPresentationEventDisposition.Stale, staleNativeReceipt.disposition)
		val afterStaleProof = readerPresentationDecision(presentation.state)
		assertFalse(coordinator.synchronizeCommonPresentationDecision(afterStaleProof))
		bridge.update(afterStaleProof)
		assertFalse(queue.hasDispatchedHead())
		assertEquals(listOf(first), transportDispatches)
		assertIs<ReaderPresentationFrameOwner.Curl>(hostEffects.last())

		nativeCandidate = nativeCandidate.copy(transitionToken = nativeTransition.token)
		nativePublisher.update()
		frameSource.presentNext()
		val nativeReceipt = nativeReceipts.last()
		val nativeEvent = assertIs<ReaderPresentationEvent.NativePagePresented>(nativeReceipt.event)
		val nativeProof = nativeEvent.proof
		assertEquals(ReaderPresentationEventDisposition.Accepted, nativeReceipt.disposition)
		val nativeDecision = readerPresentationDecision(presentation.state)
		assertTrue(coordinator.synchronizeCommonPresentationDecision(nativeDecision))
		bridge.update(nativeDecision)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentation.state.authority)
		assertEquals(second, queue.head())
		assertTrue(queue.hasDispatchedHead())
		assertEquals(listOf(first, second), transportDispatches)
		assertEquals(1, foregroundOwnership.snapshot().liveClaims)
		assertTrue(liveDispatch.isCurrent(second))
		assertEquals(listOf(first), completed)
		assertEquals(
			ReaderPresentationFrameOwner.NativePage(nativeProof),
			hostEffects.last()
		)

		val duplicateNativeReceipt = presentation.publish(nativeEvent)
		assertEquals(
			ReaderPresentationEventDisposition.Idempotent,
			duplicateNativeReceipt.disposition
		)
		assertFalse(
			coordinator.synchronizeCommonPresentationDecision(
				readerPresentationDecision(presentation.state)
			)
		)
		assertEquals(listOf(first, second), transportDispatches)
		assertEquals(listOf(first), completed)
		assertEquals(1, foregroundOwnership.snapshot().liveClaims)
		assertTrue(liveDispatch.complete(second))
		assertEquals(0, foregroundOwnership.snapshot().liveClaims)
		nativePublisher.dispose()
		coordinator.close()
		bridge.dispose()
		foregroundOwnership.close()
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun nativeToLiveModeToggleRetainsNativePresentationUntilExactLiveReceipt() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val setCanvasEnabled = viewerClass.task7Method(
			"setPageTurnCanvasEnabled",
			requireNotNull(Boolean::class.javaPrimitiveType)
		)
		val requiresLiveHandoff = viewerClass.task7Method(
			"requiresLiveEngineExposureHandoff"
		)
		val applyFrameOwner = viewerClass.task7Method(
			"applyPresentationFrameOwner",
			paige.navic.reader.ReaderPresentationDecision::class.java
		)
		val controller = viewerClass.getDeclaredField("playLikeCurlController").run {
			isAccessible = true
			get(viewer)
		}
		val controllerEnabled = controller.javaClass.getDeclaredField("enabled").apply {
			isAccessible = true
		}
		fun nativeControllerEnabled(): Boolean = controllerEnabled.getBoolean(controller)

		val binding = ReaderPresentationBinding(
			"mode-toggle-session", 1L, 2L, 3L,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 7L, 1200, 800, 4L, 5L
		)
		var presentation = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 8L
		)
		val visualHost = Task7VisualHandoffHost()
		val events = mutableListOf<ReaderPresentationEvent>()
		val deadlines = HostBridgeDeadlineScheduler()
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(binding) { decision ->
				applyFrameOwner.invoke(viewer, decision)
			},
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(visualHost),
			transitionTimeoutScheduler = deadlines,
			transitionNowMillis = { 0L },
			liveEngineExposureRequired = { requiresLiveHandoff.invoke(viewer) as Boolean }
		) { event ->
			events += event
			presentation = readerPresentationReduce(presentation, event).state
			readerTestPresentationReceipt(event, presentation)
		}

		setCanvasEnabled.invoke(viewer, true)
		assertTrue(nativeControllerEnabled())
		bridge.update(readerPresentationDecision(presentation))
		assertTrue(events.isEmpty())

		setCanvasEnabled.invoke(viewer, false)
		bridge.update(readerPresentationDecision(presentation))

		assertIs<ReaderPresentationEvent.WebViewHandoffRequested>(events.single())
		val pending = readerPresentationDecision(presentation)
		assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(pending.authority)
		assertIs<ReaderPresentationFrameOwner.NativePage>(pending.frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(pending.inputPolicy)
		assertTrue(nativeControllerEnabled())

		assertEquals(0, visualHost.delayedPostCount)
		deadlines.runPending()
		val failed = readerPresentationDecision(presentation)
		assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(failed.authority)
		assertIs<ReaderPresentationFrameOwner.NativePage>(failed.frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(failed.inputPolicy)
		assertTrue(nativeControllerEnabled())
		assertFalse(visualHost.takeVisual().deliver())

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		bridge.update(readerPresentationDecision(presentation))
		visualHost.deliverVisual()
		visualHost.presentFrame()

		assertIs<ReaderPresentationAuthority.LiveEngineExposed>(presentation.authority)
		assertFalse(nativeControllerEnabled())
		bridge.dispose()
		viewerClass.getDeclaredMethod("closeReader").apply { isAccessible = true }.invoke(viewer)
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun nativeModeRequestArmsWholeHandbackBeforeNativePreparationStarts() {
		val (viewerClass, viewer) = task7Viewer(RuntimeEnvironment.getApplication())
		val fixture = BridgeFixture()
		var presentation = fixture.liveEngineExposedState(presentedFrameSequence = 24L)
		viewerClass.task7SetPresentationDecision(viewer, readerPresentationDecision(presentation))
		fixture.host.currentBinding = fixture.binding.copy(
			rasterGeneration = null,
			textureGeneration = null,
			preparationGeneration = 8L
		)
		var nativePreparationEnabledWhenHandoffRequested: Boolean? = null
		var nativePreparationEnabledWhenDeadlineScheduled: Boolean? = null
		val deadlines = HostBridgeDeadlineScheduler()
		val scheduledDelays = mutableListOf<Long>()
		val bridge = ReaderPresentationHostBridge(
			host = fixture.host,
			transitionTimeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					nativePreparationEnabledWhenDeadlineScheduled = viewerClass.task7CanvasEnabled(viewer)
					scheduledDelays += delayMillis
					return deadlines.postDelayed(action, delayMillis)
				}
				override fun removeCallbacks(action: Runnable) = deadlines.removeCallbacks(action)
			},
			transitionNowMillis = { 0L },
			liveEngineExposureRequired = {
				viewerClass.task7Method("requiresLiveEngineExposureHandoff")
					.invoke(viewer) as Boolean
			}
		) { event ->
			if (event is ReaderPresentationEvent.WebViewHandoffRequested) {
				nativePreparationEnabledWhenHandoffRequested =
					viewerClass.task7CanvasEnabled(viewer)
			}
			val reduction = readerPresentationReduce(presentation, event)
			presentation = reduction.state
			viewerClass.task7SetPresentationDecision(viewer, reduction.decision)
			readerTestPresentationReceipt(event, reduction.state)
		}
		val admitNativeMode = {
			bridge.update(readerPresentationDecision(presentation))
		}

		viewerClass.task7Method(
			"setPageTurnCanvasEnabled",
			requireNotNull(Boolean::class.javaPrimitiveType),
			Function0::class.java
		).invoke(viewer, true, admitNativeMode)

		val pending = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
			presentation.authority
		)
		assertTrue(viewerClass.task7CanvasEnabled(viewer))
		assertIs<ReaderRequiredTransition.PresentNativePage>(readerPresentationDecision(presentation).requiredTransition)
		assertEquals(listOf(10_000L), scheduledDelays)
		assertEquals(false, nativePreparationEnabledWhenDeadlineScheduled)
		assertTrue(deadlines.hasPending)
		assertEquals(false, nativePreparationEnabledWhenHandoffRequested)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(pending.retainedFrame)
		deadlines.runPending()
		assertEquals(ReaderPresentationFailureReason.TimedOut, presentation.failure?.reason)
		assertEquals(pending.retainedFrame, readerPresentationDecision(presentation).frameOwner)
		assertFalse(deadlines.hasPending)
		viewerClass.task7Method("closeReader").invoke(viewer)
		bridge.dispose()
	}

	@Test
	fun retryThenExactCancelReleasesCommonRelocationAndDispatchesNextOnce() {
		val queue = ReaderPageRelocationQueue()
		val first = enqueueTask7Relocation(queue, 61L, 3, 4, "cancel-session")
		val second = enqueueTask7Relocation(queue, 62L, 4, 5, "cancel-session")
		acknowledgeTask7Relocation(queue, first)
		val binding = ReaderPresentationBinding(
			first.foliateSessionId, 2L, 3L, 4L,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration,
			preparationGeneration = 7L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 8L, 1200, 800,
			first.rasterGeneration, first.textureGeneration
		)
		var presentation = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 63L
		)
		fun publish(event: ReaderPresentationEvent) = readerTestPresentationReceipt(
			event = event,
			postState = readerPresentationReduce(presentation, event).also {
				presentation = it.state
			}.state
		)
		val host = Task7VisualHandoffHost()
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = {
				ReaderPageRelocationVisualState(
					true, true, first.foliateSessionId, first.destinationOrdinal,
					first.rasterGeneration, first.textureGeneration
				)
			},
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
			validateContent = { _, validated ->
				validated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			requestPresentationHandoff = {
				publish(
					ReaderPresentationEvent.WebViewHandoffRequested(
						ReaderLiveEngineHandoffDirection.NativeToLiveEngine
					)
				)
				readerPresentationDecision(presentation).requiredTransition as
					ReaderRequiredTransition.ExposeLiveEngine
			},
			commitLiveEngineExposure = { proof ->
				publish(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
			},
			publishLiveEngineHandoffTerminal = ::publish,
			onCompleted = completed::add
		)

		assertTrue(coordinator.onAcknowledged(first))
		host.timeOut()
		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		val retry = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertTrue(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertFalse(host.takeVisual().deliver())
		val cancelled = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.LiveEngineHandoffCancelled(
				direction = retry.direction,
				token = retry.token,
				binding = retry.binding
			)
		)
		presentation = cancelled.state

		assertTrue(coordinator.synchronizeCommonPresentationDecision(cancelled.decision))
		assertFalse(coordinator.synchronizeCommonPresentationDecision(cancelled.decision))
		assertEquals(1, coordinator.pendingCallbackCount())
		assertFalse(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertEquals(listOf(first), completed)
		assertEquals(second, queue.head())
		assertTrue(queue.hasDispatchedHead())
		assertEquals(listOf(second), dispatched)
		assertFalse(host.takeVisual().deliver())
		assertEquals(0, coordinator.pendingCallbackCount())
	}

	@Test
	fun coldNonCanvasPublicationCommitsOpaqueShellBeforeLiveEngineHandoff() {
		val binding = ReaderPresentationBinding(
			"cold-session", 1L, 2L, 3L,
			preparationGeneration = 4L
		)
		var presentation = ReaderPresentationState(nextTokenValue = 5L)
		val unboundDecision = readerPresentationDecision(presentation)
		assertNull(unboundDecision.targetBinding)
		assertIs<ReaderPresentationAuthority.Unavailable>(unboundDecision.authority)
		assertEquals(ReaderPresentationFrameOwner.Neutral, unboundDecision.frameOwner)

		val hostSource = hostFile.readText()
		val factorySource = hostSource
			.substringAfter("factory = { context ->")
			.substringBefore("update = { root ->")
		assertTrue(
			factorySource.indexOf("setPresentationDecision(") in
				0 until factorySource.indexOf("setViewerContent("),
			"The atomic neutral predecessor must be projected before viewer installation"
		)
		val nativeVisibilitySource = hostSource
			.substringAfter("private fun updateNativeCoverVisibilityLayers(")
			.substringBefore("fun setOnStartupShellPrepared")
		assertContains(nativeVisibilitySource, "neutralPredecessor")

		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val viewerContentContainer = viewer.getChildAt(0) as FrameLayout
		val shellCoverClass = Class.forName(
			"paige.navic.ui.screens.reader.KomikkuReaderNativeShellCoverView"
		)
		val shellCoverView = shellCoverClass.getDeclaredConstructor(Context::class.java).run {
			isAccessible = true
			newInstance(context) as View
		}
		viewerClass.task7Method("setShellCoverView", View::class.java).invoke(viewer, shellCoverView)
		shellCoverView.visibility = View.VISIBLE
		assertEquals(viewer.childCount - 1, viewer.indexOfChild(shellCoverView))

		val liveWebView = WebView(context)
		viewerContentContainer.addView(liveWebView)
		assertTrue(viewerContentContainer.indexOfChild(liveWebView) >= 0)
		assertEquals(View.VISIBLE, shellCoverView.visibility)
		assertTrue(
			viewer.indexOfChild(viewerContentContainer) < viewer.indexOfChild(shellCoverView)
		)
		val shellCoverSource = hostSource
			.substringAfter("private class KomikkuReaderNativeShellCoverView")
			.substringBefore("private data class NativeReaderShellCoverGeometry")
		assertContains(shellCoverSource, "canvas.drawColor(Color.rgb(16, 14, 10))")

		val prepareShellCover = viewerClass.task7Method("prepareShellCoverForCommit", View::class.java)
		val cancelShellCover = viewerClass.task7Method(
			"cancelShellCoverCommitPreparation",
			View::class.java
		)
		val selectShellCover = viewerClass.task7Method(
			"selectShellCover",
			View::class.java,
			requireNotNull(Boolean::class.javaPrimitiveType)
		)
		val presentationDecisionField = viewerClass.getDeclaredField(
			"presentationDecision"
		).apply { isAccessible = true }
		val appliedOwners = mutableListOf<ReaderPresentationFrameOwner>()
		var currentBinding: ReaderPresentationBinding? = null
		var preparedCoverGeneration: Long? = null
		var coverSelected = false
		var drawListener: (() -> Unit)? = null
		val animationFrames = ArrayDeque<() -> Unit>()
		val commitHost = object : ReaderPresentationCommitHost {
			override val isAttachedToWindow = true
			override val currentPresentationBinding: ReaderPresentationBinding?
				get() = currentBinding
			override val currentShellCoverGeneration: Long?
				get() = preparedCoverGeneration
			override val shellCoverSelected: Boolean
				get() = coverSelected
			override val measuredViewportWidth = 1200
			override val measuredViewportHeight = 800
			override fun prepareOpaqueShellCover(coverGeneration: Long) {
				preparedCoverGeneration = coverGeneration
				prepareShellCover.invoke(viewer, shellCoverView)
			}
			override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) {
				if (preparedCoverGeneration == coverGeneration) {
					preparedCoverGeneration = null
					cancelShellCover.invoke(viewer, shellCoverView)
				}
			}
			override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) {
				if (preparedCoverGeneration == coverGeneration) {
					preparedCoverGeneration = null
				}
			}
			override fun registerShellCoverDrawListener(
				onDraw: () -> Unit
			): ReaderPresentationDrawRegistration {
				drawListener = onDraw
				return ReaderPresentationDrawRegistration { drawListener = null }
			}
			override fun postShellCoverAnimationFrame(onFrame: () -> Unit) {
				animationFrames.addLast(onFrame)
			}
			override fun applyPresentationFrameOwner(
				decision: paige.navic.reader.ReaderPresentationDecision
			) {
				presentationDecisionField.set(viewer, decision)
				val application = readerNativePresentationApplication(decision)
				val neutralPredecessor =
					decision.authority == ReaderPresentationAuthority.Unavailable &&
						decision.frameOwner == ReaderPresentationFrameOwner.Neutral
				shellCoverView.visibility = if (
					application.layers.shellCover || neutralPredecessor
				) View.VISIBLE else View.GONE
				appliedOwners += decision.frameOwner
			}
		}
		val visualHost = Task7VisualHandoffHost()
		val visualHandoff = ReaderWebViewVisualHandoff(visualHost)
		val events = mutableListOf<ReaderPresentationEvent>()
		val bridge = ReaderPresentationHostBridge(
			host = commitHost,
			liveEngineVisualHandoff = visualHandoff,
			liveEngineExposureRequired = { true }
		) { event ->
			events += event
			if (event is ReaderPresentationEvent.ShellCoverCommitted) {
				coverSelected = true
				selectShellCover.invoke(viewer, shellCoverView, true)
			}
			presentation = readerPresentationReduce(presentation, event).state
			readerTestPresentationReceipt(event, presentation)
		}

		bridge.update(unboundDecision)
		assertTrue(events.isEmpty())
		assertEquals(View.VISIBLE, shellCoverView.visibility)

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.PublicationOpened(binding)
		).state
		currentBinding = binding
		bridge.update(readerPresentationDecision(presentation))

		assertIs<ReaderPresentationEvent.ShellCoverRequested>(events.single())
		assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(presentation.authority)
		assertEquals(listOf(ReaderPresentationFrameOwner.Neutral), appliedOwners.distinct())
		assertEquals(View.VISIBLE, shellCoverView.visibility)
		val pendingDecision = readerPresentationDecision(presentation)
		assertFalse(
			readerViewerActionIsAdmitted(
				pendingDecision.inputPolicy,
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
			)
		)
		assertFalse(events.any { it is ReaderPresentationEvent.WebViewHandoffRequested })
		assertNotNull(drawListener).invoke()
		animationFrames.removeFirst().invoke()
		assertIs<ReaderPresentationEvent.ShellCoverCommitted>(events.last())
		assertIs<ReaderPresentationAuthority.ShellCover>(presentation.authority)
		assertFalse(events.any { it is ReaderPresentationEvent.WebViewHandoffRequested })

		bridge.update(readerPresentationDecision(presentation))
		val request = assertIs<ReaderPresentationEvent.WebViewHandoffRequested>(events.last())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, request.direction)
		assertIs<ReaderPresentationFrameOwner.ShellCover>(appliedOwners.last())
		visualHost.deliverVisual()
		visualHost.presentFrame()
		assertIs<ReaderPresentationEvent.LiveEngineExposureCommitted>(events.last())
		assertIs<ReaderPresentationAuthority.LiveEngineExposed>(presentation.authority)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(appliedOwners.last())
		assertEquals(View.GONE, shellCoverView.visibility)
		bridge.dispose()
		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	fun nativeHandbackDeadlineFailsVisibleAndRetryUsesFreshToken() {
		val binding = ReaderPresentationBinding(
			"handback-session", 1L, 2L, 3L,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 7L, 1200, 800, 4L, 5L
		)
		var presentation = ReaderPresentationState(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 8L
		)
		val exposure = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
		)
		val exposureToken = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			exposure.decision.requiredTransition
		).token
		presentation = readerPresentationReduce(
			exposure.state,
			ReaderPresentationEvent.LiveEngineExposureCommitted(
				ReaderLiveEnginePresentationProof(exposureToken, binding, 9L)
			)
		).state
		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.LiveEngineToNative
			)
		).state
		val first = assertIs<ReaderRequiredTransition.PresentNativePage>(
			readerPresentationDecision(presentation).requiredTransition
		)
		var candidate = ReaderNativePagePresentationCandidate(
			binding, first.token, 4, 1200, 800,
			ReaderPagePreparationFacts(
				phase = ReaderPagePreparationPhase.Ready,
				generation = 6L
			),
			handoffDirection = ReaderLiveEngineHandoffDirection.LiveEngineToNative
		)
		val frames = object : ReaderNativePagePresentedFrameSource {
			var requests = 0
			var cancels = 0
			private val callbacks = mutableMapOf<Long, (Long) -> Unit>()
			override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
				val requestId = (++requests).toLong()
				callbacks[requestId] = onPresented
				return requestId
			}
			override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
				cancels += 1
				callbacks.remove(requestId)
				return true
			}
			fun present(requestId: Long) {
				callbacks.remove(requestId)?.invoke(requestId)
			}
		}
		val deadlines = object : ReaderPageRelocationDispatchTimeoutScheduler {
			var pending: Runnable? = null
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
				pending = action
				return true
			}
			override fun removeCallbacks(action: Runnable) {
				if (pending === action) pending = null
			}
		}
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = frames,
			currentCandidate = { candidate },
			currentHandoffTransition = {
				readerPresentationDecision(presentation).requiredTransition as?
					ReaderRequiredTransition.PresentNativePage
			},
			handoffTimeoutScheduler = deadlines,
			handoffTimeoutMillis = 1_000L
		) { event ->
			events += event
			presentation = readerPresentationReduce(presentation, event).state
			readerTestPresentationReceipt(event, presentation)
		}

		publisher.update()
		requireNotNull(deadlines.pending).run()

		assertEquals(1, frames.cancels)
		val timedOut = assertIs<ReaderPresentationEvent.LiveEngineHandoffTimedOut>(events.single())
		assertEquals(ReaderLiveEngineHandoffDirection.LiveEngineToNative, timedOut.direction)
		assertEquals(first.token, timedOut.token)
		assertEquals(first.binding, timedOut.binding)
		assertEquals(
			ReaderPresentationFailureReason.TimedOut,
			assertIs<ReaderDiagnosticPresentation.Failure>(presentation.failure).reason
		)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(
			readerPresentationDecision(presentation).frameOwner
		)
		val cancelled = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.LiveEngineHandoffCancelled(
				direction = requireNotNull(first.direction),
				token = first.token,
				binding = first.binding
			)
		)
		assertIs<ReaderPresentationAuthority.LiveEngineExposed>(cancelled.state.authority)
		assertNull(cancelled.state.failure)

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		val retry = assertIs<ReaderRequiredTransition.PresentNativePage>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertFalse(retry.token == first.token)
		candidate = candidate.copy(transitionToken = retry.token)
		publisher.update()
		assertEquals(2, frames.requests)
		assertNotNull(deadlines.pending)

		frames.present(2L)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentation.authority)
		candidate = candidate.copy(transitionToken = null, handoffDirection = null)
		publisher.update()
		assertEquals(2, frames.requests)

		candidate = candidate.copy(
			binding = binding.copy(viewportGeneration = 3L),
			visualPageIndex = 5
		)
		publisher.update()
		assertEquals(3, frames.requests)
	}

	@Test
	fun webViewOwnerReplacementCannotCombineAVisualProofWithBConnection() {
		val host = Task7VisualHandoffHost()
		val binding = ReaderPresentationBinding(
			"owner-session", 1L, 2L, 3L,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val results = mutableListOf<ReaderPresentationWebViewVisualHandoffResult>()
		val handoff = ReaderWebViewVisualHandoff(host)
		handoff.await(ReaderPresentationToken(7L), binding, results::add)
		val ownerAVisual = host.takeVisual()
		assertTrue(ownerAVisual.deliver())
		host.ownerGeneration = 2L
		host.presentFrame()

		assertEquals(
			ReaderWebViewVisualHandoffFailure.Invalidated,
			assertIs<ReaderPresentationWebViewVisualHandoffResult.Failed>(results.single()).reason
		)
		assertFalse(ownerAVisual.deliver())

		handoff.await(ReaderPresentationToken(8L), binding, results::add)
		host.deliverVisual()
		host.presentFrame()
		val ready = assertIs<ReaderPresentationWebViewVisualHandoffResult.Ready>(results.last())
		assertEquals(ReaderPresentationToken(8L), ready.token)
		assertEquals(binding, ready.binding)
	}

	@Test
	fun nativeHostApplicationKeepsLayerPreparationDiagnosticAndInputOnOneDecision() {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "application-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = "application-session",
				commitSequence = 4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val coverProof = ReaderShellCoverCommitProof(
			token = ReaderPresentationToken(8L),
			binding = binding,
			coverGeneration = 9L,
			presentedFrame = 10L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
		val decision = readerPresentationReduce(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding,
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Preparing,
					generation = 7L,
					completedCount = 1,
					requiredCount = 4,
					readiness = ReaderPageReadinessState(
						textureDeck = ReaderTextureDeckState.Preparing,
						interaction = ReaderPageInteractionState.BlockingInitialPreparation
					)
				),
				nextTokenValue = 11L
			),
			ReaderPresentationEvent.ShellCoverDismissalRequested
		).decision

		val application = readerNativePresentationApplication(decision)

		assertSame(decision, application.decision)
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = true,
				preparationShield = false
			),
			application.layers
		)
		assertEquals(decision.preparationPresentation, application.preparation)
		assertEquals(decision.diagnosticPresentation, application.diagnostic)
		assertEquals(decision.inputPolicy, application.inputPolicy)
		assertIs<ReaderPreparationPresentation.Blocking>(application.preparation)
		assertEquals(ReaderDiagnosticPresentation.Hidden, application.diagnostic)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, application.inputPolicy)
	}

	@Test
	fun unavailableRawCoverIntentCannotSelectOrExposeAShellCover() {
		val unavailableDecision = readerPresentationDecision(ReaderPresentationState())
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationApplication(unavailableDecision).layers
		)

		val binding = ReaderPresentationBinding(
			foliateSessionId = "native-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = null,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 7L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		val nativeDecision = readerPresentationDecision(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(nativeProof)
				),
				binding = binding,
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					generation = 6L,
					readiness = ReaderPageReadinessState(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = ReaderPageInteractionState.Ready
					)
				)
			)
		)

		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationApplication(nativeDecision).layers
		)
	}

	@Test
	fun chromeOnlyUsesAChromeGestureRouteThatNeverDispatchesToPageCurlOrContent() {
		val source = hostFile.readText()
		val modeSelector = source
			.substringAfter("internal fun readerPagePhysicalDispatchMode(")
			.substringBefore("internal interface ReaderPagePhysicalDispatchTarget")
		val physicalDispatch = source
			.substringAfter("internal fun readerDispatchPagePhysicalEvent(")
			.substringBefore("internal data class ReaderLegacyLivePointerContext")
		val chromeTarget = source
			.substringAfter("override fun dispatchChromeOnly(event: MotionEvent): Boolean =")
			.substringBefore("override fun dispatchDenied(event: MotionEvent)")
		val shellCoverTarget = source
			.substringAfter("override fun dispatchShellCover(event: MotionEvent): Boolean =")
			.substringBefore("override fun dispatchLiveEngine(event: MotionEvent)")
		val shellCoverDispatch = source
			.substringAfter("private fun dispatchShellCoverPointerEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchLegacyReaderPointerEvent")

		assertContains(modeSelector, "ReaderPresentationInputPolicy.ChromeOnly ->")
		assertContains(modeSelector, "ReaderPagePhysicalDispatchMode.ChromeOnly")
		assertContains(
			physicalDispatch,
			"ReaderPagePhysicalDispatchMode.ChromeOnly -> target.dispatchChromeOnly(event)"
		)
		assertContains(chromeTarget, "dispatchChromeOnlyPointerEvent(event)")
		assertFalse(chromeTarget.contains("dispatchPlayLikeCurlPointerEvent"))
		assertFalse(chromeTarget.contains("dispatchLegacyReaderPointerEvent"))
		assertFalse(chromeTarget.contains("viewerContentContainer.dispatchTouchEvent"))
		assertContains(shellCoverTarget, "dispatchShellCoverPointerEvent(event)")
		assertContains(shellCoverDispatch, "shellCoverView?.dispatchTouchEvent(event)")
		assertFalse(shellCoverDispatch.contains("viewerContentContainer.dispatchTouchEvent"))
		assertFalse(shellCoverDispatch.contains("super.dispatchTouchEvent"))
	}

	@Test
	fun startupShellHandoffIsOneShotAcrossAnOrdinaryReturnedCover() {
		val gate = ReaderStartupShellHandoffGate()
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		val attempt = assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
		var prepared = 0
		var rejected = 0

		gate.completeAttempt(
			attempt = attempt,
			shellVisible = true,
			canvasEnabled = true,
			rasterPhase = ReaderPagePreparationPhase.Ready,
			textureDeck = ReaderTextureDeckState.Ready,
			onPrepared = { prepared += 1 },
			onRejected = { rejected += 1 }
		)

		assertEquals(1, prepared)
		assertEquals(0, rejected)
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		assertTrue(gate.consumePreparedHandoff())
		assertFalse(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		assertNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)

		gate.resetForNewViewer()
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
	}

	@Test
	fun nonCanvasStartupDismissalRetiresLaterCanvasHandoff() {
		val gate = ReaderStartupShellHandoffGate()

		assertFalse(
			gate.consumesCanvasShellPageAction(
				shellVisible = true,
				canvasEnabled = false
			)
		)
		assertFalse(gate.consumePreparedHandoff())
		assertFalse(
			gate.consumesCanvasShellPageAction(
				shellVisible = true,
				canvasEnabled = true
			)
		)
		assertNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
	}

	@Test
	fun regressedReadinessRejectsCommittedStartupShieldAndPermitsRetry() {
		val gate = ReaderStartupShellHandoffGate()
		val attempt = assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
		var prepared = 0
		var staleShieldDismissed = 0

		gate.completeAttempt(
			attempt = attempt,
			shellVisible = true,
			canvasEnabled = true,
			rasterPhase = ReaderPagePreparationPhase.Preparing,
			textureDeck = ReaderTextureDeckState.Preparing,
			onPrepared = { prepared += 1 },
			onRejected = { staleShieldDismissed += 1 }
		)

		assertEquals(0, prepared)
		assertEquals(1, staleShieldDismissed)
		assertFalse(gate.attemptInFlight)
		assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
	}

	@Test
	fun hostPreparationFactsStayBlockedUntilPublicationContentBecomesReady() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Idle,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val pending = readerHostPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = false,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)
		val ready = readerHostPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)

		assertEquals(ReaderPagePreparationPhase.Idle, pending.phase)
		assertEquals(
			ReaderPageInteractionState.BlockingInitialPreparation,
			pending.readiness.interaction
		)
		assertEquals(ReaderPagePreparationPhase.Ready, ready.phase)
		assertEquals(ReaderTextureDeckState.Ready, ready.readiness.textureDeck)
		assertEquals(ReaderPageInteractionState.Ready, ready.readiness.interaction)
	}

	@Test
	fun enabledCanvasMergesRendererReadinessIntoRawPreparationFacts() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 2,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val merged = readerHostPagePreparationState(
			pageTurnCanvasEnabled = true,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(ReaderPagePreparationPhase.Preparing, merged.phase)
		assertEquals(ReaderTextureDeckState.Preparing, merged.readiness.textureDeck)
		assertEquals(ReaderPageInteractionState.BlockingInitialPreparation, merged.readiness.interaction)
		assertEquals(1, merged.completedCount)
	}

	@Test
	fun canvasAndContentReadinessTransitionsRepublishPreparationFacts() {
		val source = hostFile.readText()
		val canvasTransition = source
			.substringAfterLast("fun setPageTurnCanvasEnabled(enabled: Boolean)")
			.substringBefore("fun setPageTurnReadingDirection(")
		val contentTransition = source
			.substringAfterLast("fun setPageTurnContentReadyKey(contentReadyKey: String?)")
			.substringBefore("fun setPageTurnPaginationStatus(")

		assertContains(canvasTransition, "publishPagePreparationFacts()")
		assertContains(contentTransition, "publishPagePreparationFacts()")
		assertTrue(
			contentTransition.indexOf("publishPagePreparationFacts()") <
				contentTransition.indexOf("if (contentReadyKey == null) return")
		)
	}

	@Test
	fun busyFeedbackUsesTheOneTerminalPublisherWithoutInterceptingPreparationGestures() {
		val source = hostFile.readText()

		assertContains(source, "readerPageGestureShouldShowBusyFeedback(outcome)")
		assertContains(source, "onRendererBusyGestureRejected()")
		assertFalse(source.contains("composeOverlay.isClickable = visible"))
	}

	@Test
	fun busyFeedbackClearsOnlyAfterActualRendererPointerAdmission() {
		val source = hostFile.readText()

		assertContains(
			source,
			"fun canAcceptNewPointer(): Boolean = playLikeCurlController.isAvailable"
		)
		assertContains(source, "currentNativeFrameRoot?.canAcceptNewPointer() == true")
		assertFalse(
			source.contains(
				"pageOperationPolicy.newPointer == ReaderPageNewPointerDecision.Accept"
			)
		)
	}

	@Test
	fun busyFeedbackUsesANonTouchableWindowAboveTheOnTopCurlSurface() {
		val hostSource = hostFile.readText()
		val popupSource = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderRendererBusyPopup.android.kt"
		).readText()

		assertContains(hostSource, "ReaderRendererBusyPopup(")
		assertContains(popupSource, "PopupWindow(")
		assertContains(popupSource, "isFocusable = false")
		assertContains(popupSource, "isTouchable = false")
		assertContains(
			popupSource,
			"windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL"
		)
		assertFalse(hostSource.contains("androidx.compose.ui.window.Popup"))
	}

	@Test
	fun retainedValidatedPresentationSurvivesOnlyTransientRendererReadiness() {
		val ownership = ReaderRetainedValidatedPresentationOwnership()

		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Settling)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Empty)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = true))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Failed)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
	}

	@Test
	fun busyFeedbackMinimumTimerIsOwnedByFullyVisibleNestedOverlay() {
		val source = hostFile.readText()

		assertContains(
			source,
			"val rendererBusyFeedbackVisibility = remember { MutableTransitionState(false) }"
		)
		assertContains(source, "fullyVisibleRejectionToken = activeToken")
		assertContains(
			source,
			"readerRendererBusyFeedbackCanStartMinimumTimer("
		)
		assertFalse(source.contains("var rendererBusyFeedbackVisible by remember"))
	}

	@Test
	fun neutralBlockingDecisionUsesOnlyTheFailClosedPreparationShield() {
		val decision = readerPresentationDecision(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.BlockingPreparation(
					retainedFrame = ReaderPresentationFrameOwner.Neutral
				),
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Preparing,
					generation = 1L,
					completedCount = 0,
					requiredCount = 0,
					readiness = ReaderPageReadinessState(
						interaction = ReaderPageInteractionState.BlockingInitialPreparation
					)
				)
			)
		)

		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = true
			),
			readerNativePresentationApplication(decision).layers
		)
	}

	@Test
	fun delayedNativeProofCannotLeaveReporterOnRejectedPartialBasis() {
		val completeA = ReaderPresentationBinding(
			foliateSessionId = "delayed-proof-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"delayed-proof-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val partialB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"delayed-proof-session",
				5L
			),
			rasterGeneration = null,
			textureGeneration = null
		)
		val completeB = partialB.copy(rasterGeneration = 8L, textureGeneration = 9L)
		val request = paige.navic.reader.ReaderNativePagePresentationRequest(
			ReaderPresentationToken(10L),
			completeA
		)
		val cachedState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral,
				request
			),
			binding = completeA,
			nextTokenValue = 11L
		)
		val reporter = ReaderPresentationBindingReporter()
		reporter.update(completeA, completeA, false, false)
		val proofA = ReaderNativePagePresentationProof(
			binding = completeA,
			transitionToken = request.token,
			presentedFrame = 11L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 5L,
			textureGeneration = 6L
		)
		val settled = reporter.dispatch(
			ReaderController(
				ReaderControllerState(
					readerSessionGeneration = 5L,
					presentation = cachedState
				)
			),
			ReaderPresentationEvent.NativePagePresented(proofA)
		)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(
			settled.controller.state.presentation.authority
		)

		val partialEvent = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(completeA, partialB, false, true)
		)
		val rejected = reporter.dispatch(settled.controller, partialEvent)
		assertEquals(completeA, rejected.controller.state.presentation.binding)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(
			rejected.controller.state.presentation.authority
		)

		val completeEvent = assertNotNull(
			reporter.update(completeA, completeB, false, true)
		)
		assertIs<ReaderPresentationEvent.FoliateRelocated>(completeEvent)
		assertEquals(completeB, completeEvent.binding)
	}

	@Test
	fun productionBindingReporterUsesReducerReceiptsAndAtomicCombinedReplacement() {
		val session = "fixture-session"
		val bindingA = ReaderPresentationBinding(
			foliateSessionId = session,
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 1L),
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val combinedB = bindingA.copy(
			viewportGeneration = 7L,
			profileGeneration = 8L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 2L),
			rasterGeneration = 9L,
			textureGeneration = 10L,
			preparationGeneration = 11L
		)
		val pureDestinationC = combinedB.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 3L),
			rasterGeneration = 12L,
			textureGeneration = 13L,
			preparationGeneration = 14L
		)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 1L)
		)

		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(null, bindingA, true, false)
		)
		assertNull(reporter.lastReportedBinding)
		controller = reporter.dispatch(controller, opened).controller
		assertEquals(bindingA, reporter.lastReportedBinding)
		assertNull(reporter.update(null, bindingA, false, false))

		val request = assertNotNull(
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation).nativePresentationRequest
		)
		val proofA = ReaderNativePagePresentationProof(
			binding = bindingA,
			transitionToken = request.token,
			presentedFrame = 1L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		controller = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proofA)
		).controller
		val combinedEvent = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(bindingA, combinedB, false, true)
		)
		assertEquals(bindingA, combinedEvent.previousBinding)
		assertEquals(combinedB, combinedEvent.binding)
		assertEquals(bindingA, reporter.lastReportedBinding)

		val replacement = reporter.dispatch(controller, combinedEvent)
		controller = replacement.controller
		assertEquals(ReaderPresentationAuthority.Unavailable, controller.state.presentation.authority)
		assertEquals(
			listOf(
				paige.navic.reader.ReaderPresentationEffect.ReleaseStalePresentation(
					request.token,
					bindingA
				)
			),
			replacement.presentationEffects
		)
		assertEquals(combinedB, reporter.lastReportedBinding)
		assertNull(reporter.update(bindingA, combinedB, false, false))
		assertEquals(combinedB, reporter.lastReportedBinding)
		assertNull(reporter.update(combinedB, combinedB, false, false))

		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(combinedB, pureDestinationC, false, true)
		)
		assertEquals(pureDestinationC, relocation.binding)
	}

	@Test
	fun bindingReporterPublishesPartialOpenThenTypedExactCompletion() {
		val partial = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture-session", 1L),
			preparationGeneration = 6L
		)
		val complete = partial.copy(rasterGeneration = 7L, textureGeneration = 8L)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 9L)
		)

		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(
				confirmedTargetBinding = null,
				currentBinding = partial,
				publicationOpenPending = true,
				relocationPending = false
			)
		)
		controller = reporter.dispatch(controller, opened).controller
		assertEquals(partial, reporter.lastReportedBinding)
		assertNull(reporter.update(null, partial, false, false))

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(
				confirmedTargetBinding = partial,
				currentBinding = complete,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(partial, completion.previousBinding)
		assertEquals(complete, completion.binding)
		assertEquals(partial, reporter.lastReportedBinding)
		controller = reporter.dispatch(controller, completion).controller
		assertEquals(complete, controller.state.presentation.binding)
		assertNull(reporter.update(partial, complete, false, false))
		assertEquals(complete, reporter.lastReportedBinding)
	}

	@Test
	fun bindingReporterCompletesResolvedProfileFromAuthoritativeReceiptBeforeComposeEcho() {
		val provisional = ReaderPresentationBinding(
			foliateSessionId = "profile-race-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 0L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"profile-race-session",
				3L
			),
			preparationGeneration = 4L
		)
		val resolved = provisional.copy(profileGeneration = 5L)
		val complete = resolved.copy(rasterGeneration = 6L, textureGeneration = 7L)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 2L)
		)
		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(null, provisional, true, false)
		)
		controller = reporter.dispatch(controller, opened).controller

		val replacement = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(provisional, resolved, false, false)
		)
		assertEquals(provisional, replacement.previousBinding)
		assertEquals(resolved, replacement.binding)
		controller = reporter.dispatch(controller, replacement).controller
		assertEquals(resolved, reporter.lastReportedBinding)
		assertNull(reporter.update(provisional, resolved, false, false))

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(provisional, complete, false, false)
		)
		assertEquals(resolved, completion.previousBinding)
		assertEquals(complete, completion.binding)
		controller = reporter.dispatch(controller, completion).controller
		assertEquals(complete, controller.state.presentation.binding)
		assertEquals(complete, reporter.lastReportedBinding)
		assertNull(reporter.update(provisional, complete, false, false))
		assertNull(reporter.update(complete, complete, false, false))
	}

	@Test
	fun bindingReporterAdvancesOnlyThroughNewerAuthoritativeReceipts() {
		val provisional = ReaderPresentationBinding(
			foliateSessionId = "pending-basis-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 0L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-basis-session",
				3L
			),
			preparationGeneration = 4L
		)
		val resolved = provisional.copy(profileGeneration = 5L)
		val relocated = resolved.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-basis-session",
				4L
			)
		)
		val complete = relocated.copy(rasterGeneration = 6L, textureGeneration = 7L)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 3L)
		)
		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(null, provisional, true, false)
		)
		controller = reporter.dispatch(controller, opened).controller

		val replacement = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(provisional, resolved, false, false)
		)
		controller = reporter.dispatch(controller, replacement).controller
		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(provisional, relocated, false, true)
		)
		assertEquals(relocated, relocation.binding)
		controller = reporter.dispatch(controller, relocation).controller
		assertNull(reporter.update(provisional, relocated, false, true))
		assertNull(reporter.update(provisional, resolved, false, true))

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(provisional, complete, false, false)
		)
		assertEquals(relocated, completion.previousBinding)
		assertEquals(complete, completion.binding)
		controller = reporter.dispatch(controller, completion).controller
		assertEquals(complete, controller.state.presentation.binding)

		val otherSession = complete.copy(
			foliateSessionId = "authoritative-session",
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"authoritative-session",
				1L
			),
			rasterGeneration = null,
			textureGeneration = null
		)
		assertNull(reporter.update(otherSession, otherSession, false, false))
		assertNull(reporter.update(otherSession, complete, false, false))
		assertEquals(complete, reporter.lastReportedBinding)

		reporter.reset()
		assertNull(reporter.update(null, complete, false, false))
		assertNull(reporter.lastReportedBinding)
	}

	@Test
	fun bindingReporterNeverUsesARejectedOneSidedRendererFactAsItsBasis() {
		val confirmed = ReaderPresentationBinding(
			foliateSessionId = "rejected-basis-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"rejected-basis-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val oneSided = confirmed.copy(
			viewportGeneration = 8L,
			rasterGeneration = 9L,
			textureGeneration = null
		)
		val complete = oneSided.copy(textureGeneration = 10L)
		val reporter = ReaderPresentationBindingReporter()

		reporter.update(confirmed, confirmed, false, false)
		assertNull(reporter.update(confirmed, oneSided, false, false))
		val replacement = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(confirmed, complete, false, false)
		)
		assertEquals(confirmed, replacement.previousBinding)
		assertEquals(complete, replacement.binding)
	}

	@Test
	fun pendingCompleteReporterRelocationIsAcceptedByNeutralPreparationReducer() {
		val partialA = ReaderPresentationBinding(
			foliateSessionId = "pending-complete-relocation-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-complete-relocation-session",
				4L
			),
			preparationGeneration = 5L
		)
		val completeA = partialA.copy(rasterGeneration = 6L, textureGeneration = 7L)
		val completeB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-complete-relocation-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 6L,
				presentation = readerPresentationReduce(
					readerPresentationReduce(
						ReaderPresentationState(nextTokenValue = 10L),
						ReaderPresentationEvent.PublicationOpened(partialA)
					).state,
					ReaderPresentationEvent.NativePageRequested
				).state
			)
		)
		controller = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePageRequested
		).controller

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(partialA, completeA, false, false)
		)
		controller = reporter.dispatch(controller, completion).controller
		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(partialA, completeB, false, true)
		)
		controller = reporter.dispatch(controller, relocation).controller

		val request = assertNotNull(
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation)
				.nativePresentationRequest
		)
		assertEquals(ReaderPresentationToken(10L), request.token)
		assertEquals(completeB, request.binding)
		assertEquals(completeB, controller.state.presentation.binding)

		val proofB = ReaderNativePagePresentationProof(
			binding = completeB,
			transitionToken = request.token,
			presentedFrame = 11L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val presented = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proofB)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proofB)
			),
			presented.controller.state.presentation.authority
		)
		assertEquals(
			listOf(
				paige.navic.reader.ReaderPresentationEffect.ReleaseStalePresentation(
					ReaderPresentationToken(10L),
					completeA
				)
			),
			presented.presentationEffects
		)
		assertTrue(presented.controller.state.presentation.rendererCleanupOwnership.isEmpty())
	}

	@Test
	fun confirmedCompleteReporterRetriesRelocationUntilAuthoritativeReceipt() {
		val completeA = ReaderPresentationBinding(
			foliateSessionId = "confirmed-complete-relocation-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"confirmed-complete-relocation-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val completeB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"confirmed-complete-relocation-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val reporter = ReaderPresentationBindingReporter()
		val request = paige.navic.reader.ReaderNativePagePresentationRequest(
			ReaderPresentationToken(10L),
			completeA
		)
		val initialState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral,
				request
			),
			binding = completeA,
			nextTokenValue = 11L
		)
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 7L,
				presentation = initialState
			)
		)
		reporter.update(completeA, completeA, false, false)

		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(completeA, completeB, false, true)
		)
		assertEquals(
			relocation,
			reporter.update(completeA, completeB, false, true)
		)
		controller = reporter.dispatch(controller, relocation).controller
		assertEquals(completeB, controller.state.presentation.binding)
		assertEquals(
			request.copy(binding = completeB),
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation).nativePresentationRequest
		)
		assertNull(reporter.update(completeA, completeB, false, true))
		assertEquals(completeB, reporter.lastReportedBinding)
		val proofB = ReaderNativePagePresentationProof(
			binding = completeB,
			transitionToken = request.token,
			presentedFrame = 12L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val presented = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proofB)
		)
		assertEquals(
			listOf(
				paige.navic.reader.ReaderPresentationEffect.ReleaseStalePresentation(
					request.token,
					completeA
				)
			),
			presented.presentationEffects
		)
		assertTrue(presented.controller.state.presentation.rendererCleanupOwnership.isEmpty())
	}

	@Test
	fun completeReporterRelocationFencesReverseAndForeignAuthorityFacts() {
		val completeA = ReaderPresentationBinding(
			foliateSessionId = "fenced-complete-relocation-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"fenced-complete-relocation-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val completeB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"fenced-complete-relocation-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val otherSession = "foreign-complete-relocation-session"
		val rejected = listOf(
			completeB.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					"fenced-complete-relocation-session",
					3L
				)
			),
			completeB.copy(
				foliateSessionId = otherSession,
				destinationCommitIdentity = ReaderDestinationCommitIdentity(otherSession, 5L)
			),
			completeB.copy(publicationGeneration = 2L),
			completeB.copy(viewportGeneration = 1L),
			completeB.copy(profileGeneration = 2L)
		)

		rejected.forEach { candidate ->
			val reporter = ReaderPresentationBindingReporter()
			reporter.update(completeA, completeA, false, false)
			assertNull(reporter.update(completeA, candidate, false, true))
			assertNull(reporter.lastReportedBinding)
		}
		listOf(
			completeB.copy(viewportGeneration = 3L),
			completeB.copy(profileGeneration = 4L)
		).forEach { replacement ->
			val reporter = ReaderPresentationBindingReporter()
			reporter.update(completeA, completeA, false, false)
			assertIs<ReaderPresentationEvent.BindingReplaced>(
				reporter.update(completeA, replacement, false, true)
			)
		}
	}

	@Test
	fun bindingReporterTransitionTableAdvancesOnlyThroughReducerReceipts() {
		val requestToken = ReaderPresentationToken(501L)
		fun stateFor(binding: ReaderPresentationBinding, coverBacked: Boolean): ReaderPresentationState {
			val logicalBinding = binding.copy(rasterGeneration = null, textureGeneration = null)
			val retainedFrame = if (coverBacked) {
				ReaderPresentationFrameOwner.ShellCover(
					ReaderShellCoverCommitProof(
						token = ReaderPresentationToken(500L),
						binding = logicalBinding,
						coverGeneration = 502L,
						presentedFrame = 503L,
						viewportWidth = 1200,
						viewportHeight = 800
					)
				)
			} else {
				ReaderPresentationFrameOwner.Neutral
			}
			return ReaderPresentationState(
				authority = ReaderPresentationAuthority.BlockingPreparation(
					retainedFrame = retainedFrame,
					nativePresentationRequest =
						paige.navic.reader.ReaderNativePagePresentationRequest(
							requestToken,
							binding
						)
				),
				binding = binding,
				nextTokenValue = 502L
			)
		}

		listOf(false, true).forEach { coverBacked ->
			listOf(false, true).forEach { pendingReporterBasis ->
				listOf(false, true).forEach { currentComplete ->
					listOf(false, true).forEach { destinationSuccessor ->
						listOf(false, true).forEach { incomingComplete ->
							val cell = "cover=$coverBacked pending=$pendingReporterBasis " +
								"currentComplete=$currentComplete successor=$destinationSuccessor " +
								"incomingComplete=$incomingComplete"
							val partialCurrent = ReaderPresentationBinding(
								foliateSessionId = "reporter-transition-matrix-session",
								publicationGeneration = 1L,
								viewportGeneration = 2L,
								profileGeneration = 3L,
								destinationCommitIdentity = ReaderDestinationCommitIdentity(
									"reporter-transition-matrix-session",
									4L
								),
								preparationGeneration = 7L
							)
							val current = if (currentComplete) {
								partialCurrent.copy(rasterGeneration = 5L, textureGeneration = 6L)
							} else {
								partialCurrent
							}
							val incomingPartial = if (destinationSuccessor) {
								partialCurrent.copy(
									destinationCommitIdentity = ReaderDestinationCommitIdentity(
										"reporter-transition-matrix-session",
										5L
									)
								)
							} else if (currentComplete) {
								partialCurrent
							} else {
								partialCurrent.copy(preparationGeneration = 8L)
							}
							val incoming = if (incomingComplete) {
								when {
									!destinationSuccessor && !currentComplete -> current.copy(
										rasterGeneration = 8L,
										textureGeneration = 9L
									)
									!destinationSuccessor -> current.copy(preparationGeneration = 8L)
									else -> incomingPartial.copy(
										rasterGeneration = 8L,
										textureGeneration = 9L
									)
								}
							} else {
								incomingPartial
							}
							val reporter = ReaderPresentationBindingReporter()
							val confirmedBinding = if (pendingReporterBasis) {
								if (currentComplete) {
									partialCurrent
								} else {
									partialCurrent.copy(profileGeneration = 2L)
								}
							} else {
								current
							}
							val confirmedState = stateFor(confirmedBinding, coverBacked)
							var controller = ReaderController(
								ReaderControllerState(
									readerSessionGeneration = 4L,
									presentation = confirmedState
								)
							)
							controller = reporter.dispatch(
								controller,
								ReaderPresentationEvent.NativePageRequested
							).controller
							if (pendingReporterBasis) {
								val seed = assertNotNull(
									reporter.update(
										confirmedBinding,
										current,
										false,
										false
									),
									cell
								)
								controller = reporter.dispatch(controller, seed).controller
								assertEquals(current, controller.state.presentation.binding, cell)
							}

							val event = reporter.update(
								confirmedBinding,
								incoming,
								false,
								destinationSuccessor
							)
							val accepted = destinationSuccessor || !currentComplete || incomingComplete
							if (!accepted) {
								assertNull(event, cell)
							} else {
								val emitted = assertNotNull(event, cell)
								controller = reporter.dispatch(controller, emitted).controller
								assertEquals(incoming, controller.state.presentation.binding, cell)
								assertEquals(
									requestToken,
									(controller.state.presentation.authority as
										ReaderPresentationAuthority.BlockingPreparation)
										.nativePresentationRequest?.token,
									cell
								)
								assertNull(
									reporter.update(
										confirmedBinding,
										incoming,
										false,
										destinationSuccessor
									),
									cell
								)
								assertEquals(incoming, reporter.lastReportedBinding, cell)
							}
						}
					}
				}
			}
		}
	}

	@Test
	fun bindingReporterDirectCompleteRelocationSettlesTheReducerWithoutRetry() {
		val partial = ReaderPresentationBinding(
			foliateSessionId = "direct-complete-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"direct-complete-session",
				4L
			),
			preparationGeneration = 5L
		)
		val complete = partial.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"direct-complete-session",
				5L
			),
			rasterGeneration = 6L,
			textureGeneration = 7L
		)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 8L,
				presentation = readerPresentationReduce(
					readerPresentationReduce(
						ReaderPresentationState(nextTokenValue = 8L),
						ReaderPresentationEvent.PublicationOpened(partial)
					).state,
					ReaderPresentationEvent.NativePageRequested
				).state
			)
		)
		controller = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePageRequested
		).controller

		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(partial, complete, false, true)
		)
		controller = reporter.dispatch(controller, relocation).controller
		assertNull(reporter.update(partial, complete, false, false))
		assertEquals(complete, reporter.lastReportedBinding)
		val request = requireNotNull(
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation)
				.nativePresentationRequest
		)
		assertEquals(ReaderPresentationToken(8L), request.token)
		assertEquals(complete, request.binding)

		val proof = ReaderNativePagePresentationProof(
			binding = complete,
			transitionToken = request.token,
			presentedFrame = 9L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 6L,
			textureGeneration = 7L
		)
		val presented = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proof)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			presented.controller.state.presentation.authority
		)
		assertTrue(presented.presentationEffects.isEmpty())
	}

	@Test
	fun coldCanvasBindingIsPartialUntilAnExactPreparedDeckArrives() {
		val destination = ReaderDestinationCommitIdentity("fixture-session", 4L)
		val snapshot = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = destination,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)
		val partial = assertNotNull(readerPresentationHostBinding(snapshot))

		assertEquals("fixture-session", partial.foliateSessionId)
		assertEquals(3L, partial.profileGeneration)
		assertEquals(destination, partial.destinationCommitIdentity)
		assertEquals(6L, partial.preparationGeneration)
		assertNull(partial.rasterGeneration)
		assertNull(partial.textureGeneration)

		val exactDeck = ReaderPagePreparedActiveDeck(
			rasterProfileEpoch = 3L,
			rasterEpoch = 8L,
			sourceCenterPageIndex = 7,
			generationId = 9L,
			preparationGeneration = 6L
		)
		assertEquals(
			partial.copy(rasterGeneration = 8L, textureGeneration = 9L),
			readerPresentationHostBinding(
				snapshot.copy(preparedDeck = exactDeck, preparedDeckAdmitted = true)
			)
		)
	}

	@Test
	fun staleLateDeckNeverCompletesOrFabricatesRendererIdentity() {
		val base = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = true
		)
		val staleDecks = listOf(
			ReaderPagePreparedActiveDeck(4L, 8L, 7, 9L, 6L),
			ReaderPagePreparedActiveDeck(3L, 8L, 8, 9L, 6L),
			ReaderPagePreparedActiveDeck(3L, 8L, 7, 9L, 5L)
		)

		staleDecks.forEach { staleDeck ->
			val binding = assertNotNull(
				readerPresentationHostBinding(base.copy(preparedDeck = staleDeck))
			)
			assertNull(binding.rasterGeneration)
			assertNull(binding.textureGeneration)
		}
		val unadmitted = assertNotNull(
			readerPresentationHostBinding(
				base.copy(
					preparedDeck = ReaderPagePreparedActiveDeck(3L, 8L, 7, 9L, 6L),
					preparedDeckAdmitted = false
				)
			)
		)
		assertNull(unadmitted.rasterGeneration)
		assertNull(unadmitted.textureGeneration)
	}

	@Test
	fun provisionalProfileBootstrapIsReportableWhileUnknownFactsStayFenced() {
		val completeFacts = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)

		assertNull(readerPresentationHostBinding(completeFacts.copy(foliateSessionId = null)))
		assertNull(readerPresentationHostBinding(completeFacts.copy(viewportWidth = 0)))
		val provisional = assertNotNull(
			readerPresentationHostBinding(
				completeFacts.copy(
					profileIdentity = ReaderPresentationHostProfileIdentity.Provisional
				)
			)
		)
		assertEquals(0L, provisional.profileGeneration)
		assertNull(provisional.rasterGeneration)
		assertNull(provisional.textureGeneration)
		assertNotNull(readerPresentationHostBinding(completeFacts))
	}

	@Test
	fun hostUsesTypedProfileIdentityAndRetriesBootstrapAfterFailure() {
		val bindingSource = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPresentationHostBinding.android.kt"
		).readText()
		val retry = hostFile.readText()
			.substringAfter("fun retryPreparation(effect: ReaderPresentationEffect.RetryPreparation)")
			.substringBefore("private fun onPreparedActiveDeckChanged")

		assertContains(bindingSource, "sealed interface ReaderPresentationHostProfileIdentity")
		assertContains(bindingSource, "data object Provisional")
		assertContains(bindingSource, "data class Resolved")
		assertFalse(bindingSource.contains("val profileGeneration: Long?"))
		assertContains(retry, "if (rasterProfileEpoch == null)")
		assertContains(retry, "requestPageTurnPrewarmWhenReady()")
	}

	@Test
	fun nonCanvasModeNeverBypassesPresentationPolicyToDirectWebView() {
		val snapshot = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = false,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)

		assertNotNull(readerPresentationHostBinding(snapshot))
		val cold = coldLegacyLiveCompatibilityContext()
		assertEquals(
			ReaderPagePhysicalDispatchMode.Denied,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = false,
				presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly,
				legacyLiveCompatibilityContext = cold
			)
		)
		assertEquals(
			ReaderPagePhysicalDispatchMode.Denied,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = false,
				presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly
			)
		)
		val expectedByPolicy = listOf(
			ReaderPresentationInputPolicy.ChromeOnly to ReaderPagePhysicalDispatchMode.ChromeOnly,
			ReaderPresentationInputPolicy.ShellCover to ReaderPagePhysicalDispatchMode.ShellCover,
			ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(1L)) to
				ReaderPagePhysicalDispatchMode.PlayLikeCurl,
			ReaderPresentationInputPolicy.NativePage(
				paige.navic.reader.readerPageOperationPolicy(ReaderPageReadinessState())
			) to ReaderPagePhysicalDispatchMode.Denied,
			ReaderPresentationInputPolicy.LiveEngine to ReaderPagePhysicalDispatchMode.LiveEngine
		)
		expectedByPolicy.forEach { (policy, expected) ->
			assertEquals(
				expected,
				readerPagePhysicalDispatchMode(
					pageTurnCanvasEnabled = false,
					presentationInputPolicy = policy
				),
				"policy=$policy"
			)
		}
		listOf(
			ReaderPresentationLifecycleState.Background,
			ReaderPresentationLifecycleState.Destroyed
		).forEach { lifecycle ->
			assertEquals(
				ReaderPagePhysicalDispatchMode.Denied,
				readerPagePhysicalDispatchMode(
					pageTurnCanvasEnabled = false,
					presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
					legacyLiveCompatibilityContext =
						ReaderLegacyLiveCompatibilityContext.Denied(lifecycle)
				)
			)
		}
	}

	@Test
	fun physicalDispatchInvokesOnlyTheSelectedTypedTarget() {
		val calls = mutableListOf<String>()
		val target = object : ReaderPagePhysicalDispatchTarget {
			override fun dispatchCueMap(event: MotionEvent) = calls.add("cue-map")
			override fun dispatchChromeOnly(event: MotionEvent) = calls.add("chrome-only")
			override fun dispatchDenied(event: MotionEvent) = calls.add("denied")
			override fun dispatchLegacy(event: MotionEvent) = calls.add("legacy")
			override fun dispatchLegacyLive(event: MotionEvent) = calls.add("legacy-live")
			override fun dispatchPlayLikeCurl(event: MotionEvent) = calls.add("play-like-curl")
			override fun dispatchShellCover(event: MotionEvent) = calls.add("shell-cover")
			override fun dispatchLiveEngine(event: MotionEvent) = calls.add("live-engine")
		}
		val expectedByMode = mapOf(
			ReaderPagePhysicalDispatchMode.CueMap to "cue-map",
			ReaderPagePhysicalDispatchMode.ChromeOnly to "chrome-only",
			ReaderPagePhysicalDispatchMode.Denied to "denied",
			ReaderPagePhysicalDispatchMode.Legacy to "legacy",
			ReaderPagePhysicalDispatchMode.LegacyLive to "legacy-live",
			ReaderPagePhysicalDispatchMode.PlayLikeCurl to "play-like-curl",
			ReaderPagePhysicalDispatchMode.ShellCover to "shell-cover",
			ReaderPagePhysicalDispatchMode.LiveEngine to "live-engine"
		)
		val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
		try {
			expectedByMode.forEach { (mode, expected) ->
				calls.clear()
				assertTrue(
					readerDispatchPagePhysicalEvent(mode, event, target) {
						calls.add("fallback")
					}
				)
				assertEquals(listOf(expected), calls, "mode=$mode")
			}
			calls.clear()
			assertTrue(
				readerDispatchPagePhysicalEvent(null, event, target) {
					calls.add("fallback")
				}
			)
			assertEquals(listOf("fallback"), calls)
		} finally {
			event.recycle()
		}
	}

	@Test
	fun legacyLivePointerStreamRevokesChangedContextOnceAndSuppressesLateTerminal() {
		val stream = ReaderLegacyLivePointerStream()
		val initial = legacyLivePointerContext()

		stream.begin(ReaderPagePhysicalDispatchMode.LegacyLive, initial)

		assertFalse(stream.revokeIfContextChanged(initial))
		assertTrue(stream.revokeIfContextChanged(initial.copy(shellCoverVisible = true)))
		assertFalse(stream.revokeIfContextChanged(initial.copy(shellCoverVisible = true)))
		assertFalse(stream.revoke())
		assertTrue(stream.suppressesOriginalTerminal)
		stream.finish()
		assertFalse(stream.suppressesOriginalTerminal)
		assertFalse(stream.revoke())
	}

	@Test
	fun nonLegacyPointerStreamsNeverRevokeLegacyDelivery() {
		ReaderPagePhysicalDispatchMode.entries
			.filterNot { it == ReaderPagePhysicalDispatchMode.LegacyLive }
			.forEach { mode ->
				val stream = ReaderLegacyLivePointerStream()
				val context = legacyLivePointerContext()

				stream.begin(mode, context)

				assertFalse(stream.revokeIfContextChanged(context.copy(shellCoverVisible = true)))
				assertFalse(stream.revoke())
				assertFalse(stream.suppressesOriginalTerminal)
			}
	}

	@Test
	fun physicalDispatchUsesTypedTargetsAndCancelsChangedLegacyStreams() {
		val source = hostFile.readText()
		val physicalDispatch = source
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchLegacyLivePointerEvent")
		val viewerContainer = source
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
		val viewerReplacement = viewerContainer
			.substringAfter("fun replaceViewerContent(viewerView: View)")
			.substringBefore("fun detachViewerContent(viewerView: View?)")
		val compatibilityUpdate = viewerContainer
			.substringAfter("fun setLegacyLiveCompatibilityContext(")
			.substringBefore("fun setPageTurnCanvasEnabled(")
		val canvasUpdate = viewerContainer
			.substringAfter("fun setPageTurnCanvasEnabled(")
			.substringBefore("fun setPageTurnReadingDirection(")
		val decisionUpdate = viewerContainer
			.substringAfter("fun setPresentationDecision(")
			.substringBefore("fun releaseStalePresentation(")
		val authoritativeDecisionApplication = viewerContainer
			.substringAfter("fun applyPresentationDecision(")
			.substringBefore("private fun setLocalPageSafetyPolicy(")
		val shellCoverUpdate = viewerContainer
			.substringAfter("fun setShellCoverVisible(")
			.substringBefore("fun setPageOperationPolicy(")
		val physicalClose = viewerContainer
			.substringAfter("private fun closePhysicalPointerDelivery()")
			.substringBefore("private fun teardownTask4Resources()")

		assertContains(source, "ReaderPagePhysicalDispatchMode.Denied")
		assertContains(source, "ReaderPagePhysicalDispatchMode.ShellCover")
		assertContains(source, "ReaderPagePhysicalDispatchMode.LegacyLive")
		assertContains(source, "legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext")
		assertContains(source, "internal interface ReaderPagePhysicalDispatchTarget")
		assertContains(source, "internal fun readerDispatchPagePhysicalEvent(")
		assertContains(source, "internal class ReaderLegacyLivePointerStream")
		assertContains(source, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(source, "physicalDispatchMode = ReaderPagePhysicalDispatchMode.Denied")
		assertContains(source, "action = MotionEvent.ACTION_CANCEL")
		assertContains(physicalDispatch, "legacyLivePointerStream.begin(")
		assertContains(physicalDispatch, "legacyLivePointerStream.suppressesOriginalTerminal")
		assertContains(physicalDispatch, "legacyLivePointerStream.finish()")
		assertContains(viewerReplacement, "cancelLegacyLivePointerStream()")
		assertContains(compatibilityUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(canvasUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(
			decisionUpdate,
			"presentationReceiptDispatcher.synchronizeComposeModel("
		)
		assertContains(authoritativeDecisionApplication, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(shellCoverUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(physicalClose, "cancelLegacyLivePointerStream()")
		assertContains(physicalClose, "legacyLivePointerStream.suppressesOriginalTerminal")
		assertFalse(
			source.contains(
				"if (!pageTurnCanvasEnabled) return ReaderPagePhysicalDispatchMode.LiveEngine"
			)
		)
	}

	@Test
	fun shellCoverIsDrawnBehindPredecessorUntilCommittedAuthoritySelectsIt() {
		val source = hostFile.readText()
		val preparation = source
			.substringAfter("fun prepareShellCoverForCommit(")
			.substringBefore("fun cancelShellCoverCommitPreparation(")
		val finalSelection = source
			.substringAfter("fun selectShellCover(")
			.substringBefore("fun setVerticalPageDragPreview(")
		val proofRetention = source
			.substringAfter("fun setShellCoverVisible(")
			.substringBefore("fun setPageOperationPolicy(")

		assertContains(preparation, "addView(shellCoverView, 0")
		assertContains(preparation, "shellCoverView.visibility = VISIBLE")
		assertContains(preparation, "shellCoverView.isClickable = false")
		assertFalse(preparation.contains("invalidate(\"shell-cover-visible\")"))
		assertContains(finalSelection, "shellCoverView.bringToFront()")
		assertContains(finalSelection, "preserveNativePresentationProof")
		assertContains(
			proofRetention,
			"if (visibilityChanged && visible && !preserveNativePresentationProof)"
		)
		assertContains(proofRetention, "playLikeCurlController.invalidate(\"shell-cover-visible\")")
		assertContains(
			proofRetention,
			"pageRasterPreparationController.invalidate(\"shell-cover-visible\")"
		)
	}

	private fun Class<*>.task7SetPresentationDecision(
		viewer: Any,
		decision: paige.navic.reader.ReaderPresentationDecision
	) = task7Field("presentationDecision").set(viewer, decision)

	private fun Class<*>.task7CanvasEnabled(viewer: Any): Boolean =
		task7Field("pageTurnCanvasEnabled").getBoolean(viewer)

	private fun Class<*>.task7PendingNativeHandoff(
		viewer: Any
	): ReaderRequiredTransition.PresentNativePage? {
		val publisher = (task7Field("nativePagePresentationPublisher\$delegate")
			.get(viewer) as Lazy<*>).value ?: return null
		val timeout = publisher.javaClass.task7Field("pendingHandoffTimeout")
			.get(publisher) ?: return null
		return timeout.javaClass.task7Field("transition").get(timeout) as?
			ReaderRequiredTransition.PresentNativePage
	}

	private class Task8CurlAuthorityStore(
		initialState: ReaderPresentationState?,
		private val rejectClaims: Boolean,
		val acceptLocalTerminals: Boolean
	) {
		var state = initialState
		val presentationEvents = mutableListOf<ReaderPresentationEvent>()
		val localTerminalGestureIds = mutableListOf<Long>()

		fun publish(event: ReaderPresentationEvent): ReaderPresentationEventReceipt? {
			presentationEvents += event
			val current = state ?: return null
			if (rejectClaims && event is ReaderPresentationEvent.CurlClaimed) {
				return readerTestPresentationReceipt(
					event,
					current,
					ReaderPresentationEventDisposition.Rejected
				)
			}
			val reduction = readerPresentationReduce(current, event)
			state = reduction.state
			return readerTestPresentationReceipt(
				event,
				reduction.state,
				reduction.disposition
			)
		}
	}

	private class Task8UnsafeLifecycleRelay {
		var sink: (ReaderPageHostLifecycleEvent) -> Unit = {}

		fun dispatch(event: ReaderPageHostLifecycleEvent) {
			sink(event)
		}
	}

	private class Task8PreparedDeckRelay {
		var sink: (ReaderPagePreparedActiveDeck?) -> Unit = {}

		fun dispatch(deck: ReaderPagePreparedActiveDeck?) {
			sink(deck)
		}
	}

	private class Task8PresentedFrameSource : ReaderNativePagePresentedFrameSource {
		private var nextRequestId = 1L
		private val callbacks = linkedMapOf<Long, (Long) -> Unit>()

		override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long =
			nextRequestId++.also { requestId -> callbacks[requestId] = onPresented }

		override fun cancelPresentedFrameRequest(requestId: Long): Boolean =
			callbacks.remove(requestId) != null

		fun presentNext() {
			val next = callbacks.entries.first()
			callbacks.remove(next.key)
			next.value(next.key)
		}
	}

	private data class Task8CurlAuthorityFixture(
		val controller: ReaderPlayLikeCurlFoliateController,
		val store: Task8CurlAuthorityStore,
		val unsafeLifecycleRelay: Task8UnsafeLifecycleRelay,
		val preparedDeckRelay: Task8PreparedDeckRelay
	)

	private fun task8SettledCurlSourceState(
		viewportGeneration: Long = 2L,
		viewportWidth: Int = 1200,
		viewportHeight: Int = 800
	): ReaderPresentationState {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "critical-4-session",
			publicationGeneration = 1L,
			viewportGeneration = viewportGeneration,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"critical-4-session",
				1L
			),
			rasterGeneration = 0L,
			textureGeneration = 301L,
			preparationGeneration = 0L
		)
		val proof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 4L,
			viewportWidth = viewportWidth,
			viewportHeight = viewportHeight,
			rasterGeneration = 0L,
			textureGeneration = 301L
		)
		return ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			binding = binding
		)
	}

	private fun task8CurlAuthorityFixture(
		initialState: ReaderPresentationState?,
		rejectClaims: Boolean = false,
		acceptLocalTerminals: Boolean = true,
		onActiveRasterReleased: () -> Unit = {},
		sourceIdentity: String = "critical-4-session",
		viewportWidth: Int = 1200,
		viewportHeight: Int = 800,
		physicalRect: ReaderPlayLikeCurlPhysicalRect = ReaderPlayLikeCurlPhysicalRect(0, 0, 2, 2)
	): Task8CurlAuthorityFixture {
		val context = RuntimeEnvironment.getApplication()
		val host = FrameLayout(context).apply { layout(0, 0, viewportWidth, viewportHeight) }
		val webView = Task9RecoveryCommandWebView(context).also(host::addView)
		val store = Task8CurlAuthorityStore(
			initialState,
			rejectClaims,
			acceptLocalTerminals
		)
		val unsafeLifecycleRelay = Task8UnsafeLifecycleRelay()
		val preparedDeckRelay = Task8PreparedDeckRelay()
		val controller = ReaderPlayLikeCurlFoliateController(
			host = host,
			webViewProvider = { webView },
			bundleSource = ReaderPageTurnBundleSource(),
			onRequestPrewarm = {},
			onRequestRasterRepair = { _, _ -> },
			onGestureTerminal = { gestureId, _, _ ->
				store.localTerminalGestureIds += gestureId
				store.acceptLocalTerminals
			},
			onPresentationEvent = store::publish,
			onUnsafeLifecycleEvent = unsafeLifecycleRelay::dispatch,
			onPreparedActiveDeckChanged = preparedDeckRelay::dispatch
		)
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = sourceIdentity,
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 3,
			rasterGeneration = 0L
		)
		val controllerClass = controller.javaClass
		controllerClass.task7Field("enabled").setBoolean(controller, true)
		controllerClass.task7Field("attached").setBoolean(controller, true)
		controllerClass.task7Field("currentFoliateSessionId")
			.set(controller, "critical-4-session")
		controllerClass.task7Field("requestedProfile").set(controller, profile)
		controllerClass.task7Field("currentOrdinal").setInt(controller, 1)
		controller.setPageOperationPolicy(
			readerPageOperationPolicy(
				ReaderPageReadinessState(
					textureDeck = ReaderTextureDeckState.Ready,
					interaction = ReaderPageInteractionState.Ready
				)
			)
		)
		initialState?.let {
			controller.synchronizePresentationDecision(readerPresentationDecision(it))
		}
		val surface = controller.surfaceView
		task8PrepareSurface(surface)
		surface.layout(0, 0, viewportWidth, viewportHeight)
		task8SubmitRecoveredDeck(
			controller = controller,
			generationId = 301L,
			role = ReaderDeckSubmissionRole.Active,
			profile = profile,
			onRasterReleased = onActiveRasterReleased,
			populateRasterWindow = true,
			physicalRect = physicalRect
		)
		surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
			.invoke(surface, 301L)
		assertTrue(controller.isAvailable)
		return Task8CurlAuthorityFixture(
			controller,
			store,
			unsafeLifecycleRelay,
			preparedDeckRelay
		)
	}

	private data class Task8RendererFailureProjection(
		val bridge: ReaderPresentationHostBridge,
		val projectedDecisions: List<ReaderPresentationDecision>
	)

	private fun task8BindRendererFailureProjection(
		fixture: Task8CurlAuthorityFixture
	): Task8RendererFailureProjection {
		val binding = checkNotNull(fixture.store.state?.binding)
		val initialState = checkNotNull(fixture.store.state)
		var reporterController = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 1L,
				presentation = initialState
			)
		)
		val bindingReporter = ReaderPresentationBindingReporter().also { reporter ->
			reporter.reset(
				expectedReaderSessionGeneration = 1L,
				minimumComposeVersion = reporterController.presentationVersion,
				initialPresentationState = initialState
			)
			check(reporter.bindPublication(binding))
		}
		val lifecycleDelivery = ReaderPresentationLifecycleDelivery().also { delivery ->
			delivery.reset(reporterController.presentationVersion, observedWindowVisible = null)
			check(delivery.bindPublication(binding.publicationIdentity))
		}
		val receiptDispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter,
			lifecycleDelivery
		) { _, _ -> }
		fun dispatchThroughProductionReporter(
			event: ReaderPresentationEvent
		): ReaderPresentationEventReceipt? = receiptDispatcher.dispatch(event) { dispatched ->
			val step = reporterController.onPresentationEvent(dispatched)
			reporterController = step.controller
			step.presentationReceipt
		}
		val initialProof = assertIs<ReaderPresentationAuthority.SettledNativePage>(
			initialState.authority
		).frame.proof
		checkNotNull(
			dispatchThroughProductionReporter(
				ReaderPresentationEvent.NativePagePresented(initialProof)
			)
		)
		val projected = mutableListOf<ReaderPresentationDecision>()
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(binding) { decision ->
				projected += decision
				when (decision.frameOwner) {
					is ReaderPresentationFrameOwner.NativePage ->
						fixture.controller.surfaceView.alpha = 1f
					is ReaderPresentationFrameOwner.LiveEngine,
					ReaderPresentationFrameOwner.Neutral ->
						fixture.controller.surfaceView.alpha = 0f
					else -> Unit
				}
			},
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(Task7VisualHandoffHost()),
			liveEngineExposureRequired = { false },
			onEvent = fixture.store::publish
		)
		bridge.update(readerPresentationDecision(checkNotNull(fixture.store.state)))
		fixture.unsafeLifecycleRelay.sink = { hostEvent ->
			val lifecycleEvent = ReaderPresentationEvent.Lifecycle(
				ReaderPresentationLifecycleEvent.RendererLost
			)
			lifecycleDelivery.observe(ReaderPresentationLifecycleEvent.RendererLost)
			checkNotNull(dispatchThroughProductionReporter(lifecycleEvent))
			val receipt = checkNotNull(fixture.store.publish(lifecycleEvent))
			check(receipt.authorizes(lifecycleEvent))
			val decision = readerPresentationDecision(receipt.postState)
			fixture.controller.synchronizePresentationDecision(decision)
			bridge.update(decision)
			fixture.controller.cancelActiveGesture(hostEvent.cancellationReason())
		}
		fixture.preparedDeckRelay.sink = { deck ->
			if (deck == null) {
				val previous = checkNotNull(bindingReporter.lastReportedBinding)
				val current = previous.copy(
					rasterGeneration = null,
					textureGeneration = null
				)
				val event = checkNotNull(
					bindingReporter.update(
						confirmedTargetBinding = fixture.store.state?.binding,
						currentBinding = current,
						publicationOpenPending = false,
						relocationPending = false
					)
				)
				fixture.store.publish(event)?.takeIf { it.authorizes(event) }?.let { receipt ->
					val decision = readerPresentationDecision(receipt.postState)
					fixture.controller.synchronizePresentationDecision(decision)
					bridge.update(decision)
				}
			}
		}
		return Task8RendererFailureProjection(bridge, projected)
	}

	private fun task8DeliverSurfaceRenderFailure(
		surface: karacken.curl.PageSurfaceView,
		failure: RenderFailure
	) {
		surface.javaClass.task7Method("handleRenderFailure", RenderFailure::class.java)
			.invoke(surface, failure)
	}

	@Suppress("UNCHECKED_CAST")
	private fun task8FailActiveRendererRehydration(
		surface: karacken.curl.PageSurfaceView
	) {
		val renderer = surface.javaClass.task7Field("renderer").get(surface)
		val activeDeck = checkNotNull(
			renderer.javaClass.task7Field("activeDeck").get(renderer)
		) as PageDeck<Bitmap>
		activeDeck.pages.first { !it.isFiller }.content.recycle()
		assertFalse(
			renderer.javaClass.task7Method(
				"rehydrateDeck",
				PageDeck::class.java,
				PageDeck::class.java,
				PageDeck::class.java
			).invoke(renderer, activeDeck, activeDeck, null) as Boolean
		)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
	}

	private fun task8MotionEvent(action: Int): MotionEvent = MotionEvent.obtain(
		100L,
		101L,
		action,
		1f,
		1f,
		0
	)

	private fun task8RelocationOccupiedCount(
		controller: ReaderPlayLikeCurlFoliateController
	): Int {
		val queue = controller.javaClass.task7Field("relocationQueue").get(controller)
		return queue.javaClass.task7Method("occupiedCount").invoke(queue) as Int
	}

	private fun task8SurfaceGestureAccepted(surface: karacken.curl.PageSurfaceView): Boolean =
		surface.javaClass.task7Field("gestureAccepted").getBoolean(surface)

	private fun task7Viewer(context: Context): Pair<Class<*>, FrameLayout> {
		val viewerClass = Class.forName(
			"paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer"
		)
		val viewer = viewerClass.getDeclaredConstructor(Context::class.java).run {
			isAccessible = true
			newInstance(context) as FrameLayout
		}
		return viewerClass to viewer
	}

	private fun Class<*>.task7Field(name: String) =
		getDeclaredField(name).apply { isAccessible = true }

	private fun Class<*>.task7Method(name: String, vararg parameters: Class<*>) =
		getDeclaredMethod(name, *parameters).apply { isAccessible = true }

	private fun task8PrepareSurface(surface: karacken.curl.PageSurfaceView) {
		val capabilities = RenderCapabilities(4_096, 8L * 1024L * 1024L)
		surface.javaClass.task7Field("attached").setBoolean(surface, true)
		surface.javaClass.task7Field("surfaceVisible").setBoolean(surface, true)
		surface.javaClass.task7Field("renderCapabilities").set(surface, capabilities)
		val renderer = surface.javaClass.task7Field("renderer").get(surface)
		renderer.javaClass.task7Field("maxTextureSize").setInt(renderer, 4_096)
		renderer.javaClass.task7Field("gpuBudgetBytes")
			.setLong(renderer, capabilities.gpuBudgetBytes)
	}

	private fun task8SubmitRecoveredDeck(
		controller: ReaderPlayLikeCurlFoliateController,
		generationId: Long,
		role: ReaderDeckSubmissionRole,
		profile: ReaderPlayLikeCurlRasterProfile,
		onRasterReleased: () -> Unit,
		populateRasterWindow: Boolean = false,
		physicalRect: ReaderPlayLikeCurlPhysicalRect = ReaderPlayLikeCurlPhysicalRect(0, 0, 2, 2)
	): Any {
		val controllerClass = controller.javaClass
		val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
			eraseColor(Color.WHITE)
			setHasAlpha(false)
		}
		val rasterImage = ReaderPlayLikeCurlRasterImage(
			bitmap = bitmap,
			paperColorArgb = Color.WHITE,
			layout = ReaderPlayLikeCurlRasterLayout(
				surfaceRectInWindow = physicalRect,
				fullLeafRect = ReaderPlayLikeCurlPhysicalRect(0, 0, physicalRect.width, physicalRect.height),
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = null
			),
			leaf = ReaderPlayLikeCurlFoliateLeaf.Full
		)
		val rasterDeck = ReaderPlayLikeCurlRasterDeck(
			profile = profile,
			values = if (populateRasterWindow) {
				(0 until profile.pageCount).associateWith { rasterImage }
			} else {
				emptyMap()
			},
			releaseOwnership = {
				if (!bitmap.isRecycled) bitmap.recycle()
				onRasterReleased()
			}
		)
		val preparedPagesClass = controllerClass.declaredClasses.single {
			it.simpleName == "PreparedPages"
		}
		val preparedPages = preparedPagesClass.declaredConstructors.single().run {
			isAccessible = true
			newInstance(profile, rasterDeck, 1)
		}
		val generations = preparedPagesClass.task7Field("generations")
			.get(preparedPages) as MutableSet<Long>
		val owners = controllerClass.task7Field("generationOwners")
			.get(controller) as MutableMap<Long, Any>
		val preparedPageSets = controllerClass.task7Field("preparedPageSets")
			.get(controller) as MutableSet<Any>
		val builtRecoveredDecks = controllerClass.task7Field("builtRecoveredDecks")
			.get(controller) as MutableMap<Long, Any>
		generations += generationId
		preparedPageSets += preparedPages
		owners[generationId] = preparedPages
		val displayRect = PageDisplayRect(0, 0, 2, 2)
		val material = PageMaterial(
			generationId,
			Color.WHITE,
			Color.WHITE,
			Color.GRAY,
			Color.WHITE,
			PageLeafRole.FULL,
			displayRect,
			displayRect,
			null,
			1
		)
		fun page(ordinal: Int) = PageImage(
			generationId,
			"recovered-surface-$generationId-$ordinal",
			ordinal,
			2,
			2,
			displayRect,
			bitmap,
			material
		)
		val pageDeck = PortraitPageDeck(
			page(0),
			page(1),
			page(2),
			true,
			true
		)
		val builtRecoveredDeckClass = controllerClass.declaredClasses.single {
			it.simpleName == "BuiltRecoveredDeck"
		}
		val builtRecoveredDeck = builtRecoveredDeckClass.declaredConstructors.single().run {
			isAccessible = true
			newInstance(preparedPages, 1, pageDeck)
		}
		builtRecoveredDecks[generationId] = builtRecoveredDeck
		assertEquals(
			ReaderPageRecoveredDeckSubmissionResult.Accepted,
			controller.submitRecoveredDeck(generationId, role)
		)
		return preparedPages
	}

	private fun task8BeginSurfaceSettlement(surface: karacken.curl.PageSurfaceView) {
		val coordinator = surface.javaClass.task7Field("deckCoordinator").get(surface)
		coordinator.javaClass.task7Method("beginSettlement").invoke(coordinator)
	}

	private fun task8SurfaceOwnsGeneration(
		surface: karacken.curl.PageSurfaceView,
		generationId: Long
	): Boolean {
		val leases = surface.javaClass.task7Field("leaseRegistry").get(surface)
		return leases.javaClass.task7Method("contains", java.lang.Long.TYPE)
			.invoke(leases, generationId) as Boolean
	}

	private fun task7CommonRelocationCoordinator(
		queue: ReaderPageRelocationQueue,
		request: ReaderPageRelocationRequest,
		host: ReaderWebViewVisualHandoffHost,
		publish: (ReaderPresentationEvent) -> paige.navic.reader.ReaderPresentationEventReceipt,
		requestPresentationEvent: (
			ReaderPresentationEvent
		) -> paige.navic.reader.ReaderPresentationEventReceipt = publish,
		publishRecovery: (
			ReaderPageRelocationRequest,
			ReaderWebViewVisualHandoffFailure
		) -> Unit = { _, reason -> error("Unexpected recovery: $reason") },
		publishLiveEngineHandoffTerminal: (
			ReaderPresentationEvent
		) -> paige.navic.reader.ReaderPresentationEventReceipt? = publish,
		presentedFrameSequenceSource: ((ReaderPresentationBinding) -> Long)? = null
	): ReaderPageRelocationVisualHandoffCoordinator =
		ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = {
				ReaderPageRelocationVisualState(
					true, true, request.foliateSessionId, request.destinationOrdinal,
					request.rasterGeneration, request.textureGeneration
				)
			},
			dispatch = { error("Unexpected later relocation") },
			publishRecovery = publishRecovery,
			finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
			validateContent = { _, validated ->
				validated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			requestPresentationHandoff = {
				val event = ReaderPresentationEvent.WebViewHandoffRequested(
					ReaderLiveEngineHandoffDirection.NativeToLiveEngine
				)
				readerPresentationDecision(
					requestPresentationEvent(event).postState
				).requiredTransition as ReaderRequiredTransition.ExposeLiveEngine
			},
			commitLiveEngineExposure = { proof ->
				publish(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
			},
			publishLiveEngineHandoffTerminal = publishLiveEngineHandoffTerminal,
			presentedFrameSequenceSource = presentedFrameSequenceSource
		)

	private fun enqueueTask7Relocation(
		queue: ReaderPageRelocationQueue,
		gestureId: Long,
		sourceOrdinal: Int,
		destinationOrdinal: Int,
		foliateSessionId: String
	): ReaderPageRelocationRequest {
		val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId)
		).reservation
		return assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				reservation = reservation,
				rasterGeneration = 10L,
				textureGeneration = 20L,
				sourceOrdinal = sourceOrdinal,
				destinationOrdinal = destinationOrdinal,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = foliateSessionId
			)
		).request
	}

	private fun acknowledgeTask7Relocation(
		queue: ReaderPageRelocationQueue,
		request: ReaderPageRelocationRequest
	) {
		assertEquals(request, queue.commandToDispatch())
		assertTrue(
			queue.acknowledge(
				request.token.value,
				request.destinationOrdinal,
				request.foliateSessionId,
				request.rasterGeneration,
				request.textureGeneration
			)
		)
	}

	private fun ReaderPresentationBindingReporter.dispatch(
		controller: ReaderController,
		event: ReaderPresentationEvent
	): ReaderControllerStep {
		if (expectedReaderSessionGeneration == null) {
			reset(
				expectedReaderSessionGeneration = controller.state.readerSessionGeneration,
				minimumComposeVersion = controller.presentationVersion,
				initialPresentationState = controller.state.presentation,
				initialShellCoverVisible = controller.state.shellCoverVisible
			)
		}
		val epoch = captureEpoch()
		val step = controller.onPresentationEvent(event)
		val receipt = assertNotNull(step.presentationReceipt)
		val publicationBinding = receipt.postState.binding
			?: controller.state.presentation.binding
		assertTrue(bindPublication(assertNotNull(publicationBinding)))
		assertTrue(consumeReceipt(epoch, event, receipt))
		return step
	}

	private fun legacyLivePointerContext() = ReaderLegacyLivePointerContext(
		pageTurnCanvasEnabled = false,
		presentationDecision = null,
		compatibilityContext = coldLegacyLiveCompatibilityContext(),
		shellCoverVisible = false
	)

	private fun coldLegacyLiveCompatibilityContext() =
		ReaderLegacyLiveCompatibilityContext.ColdSession(
			identity = ReaderLegacyLiveCompatibilityIdentity(
				readerSessionGeneration = 1L,
				publication = ReaderPublicationIdentity(
					bookId = "book-1",
					title = "Book",
					resourceHref = "book.epub",
					kind = ReaderPublicationKind.Ebook,
					format = ReaderPublicationFormat.Epub
				),
				foliateSessionId = null,
				destinationCommitIdentity = null
			),
			lifecycle = ReaderPresentationLifecycleState.Foreground
		)
}

private class Task9ControlledPassiveRuntime : ReaderPassiveRasterRuntimePort<Bitmap> {
	class Commit(val manifest: ReaderPassiveRasterCaptureManifest, val sequence: Long,
		val callback: (ReaderPassiveRasterCaptureReceipt?) -> Unit)
	class Capture(val geometry: ReaderPassiveRasterGeometry, val callback: (Bitmap?) -> Unit)
	override val passiveSessionId = "task9-physical-worker"
	private var paused = false
	override var isRetired = false
		private set
	override val isReady get() = !paused && !isRetired
	private var pendingCommit: Commit? = null
	private var pendingCapture: Capture? = null
	val commits = kotlinx.coroutines.channels.Channel<Commit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
	val captures = kotlinx.coroutines.channels.Channel<Capture>(kotlinx.coroutines.channels.Channel.UNLIMITED)
	val deliveredBitmaps = mutableListOf<Bitmap>()
	val pendingCallbackCount get() = (if (pendingCommit != null) 1 else 0) +
		(if (pendingCapture != null) 1 else 0)

	override fun commit(manifest: ReaderPassiveRasterCaptureManifest, captureTarget: String,
		passiveCommitSequence: Long, onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit) {
		if (!isReady) { onCommitted(null); return }
		check(pendingCallbackCount == 0)
		check(manifest.opaqueCaptureTarget == captureTarget)
		val request = Commit(manifest, passiveCommitSequence, onCommitted)
		pendingCommit = request
		check(commits.trySend(request).isSuccess)
	}

	fun completeCommit(request: Commit) {
		check(isReady && pendingCommit === request)
		pendingCommit = null
		commits.tryReceive()
		val manifest = request.manifest
		// Same physical receipt construction as SuccessfulPassiveBitmapRuntime:
		// echo only the actual session-issued manifest and commit correlation.
		request.callback(ReaderPassiveRasterCaptureReceipt(
			passiveSessionId = passiveSessionId,
			echoedManifestSequence = manifest.manifestSequence,
			echoedCaptureEpoch = manifest.captureEpoch,
			echoedLiveFoliateSessionId = manifest.liveFoliateSessionId,
			echoedPublicationSessionGeneration = manifest.publicationSessionGeneration,
			echoedDestinationCommitToken = manifest.destinationCommitToken,
			observedCaptureTarget = manifest.opaqueCaptureTarget,
			observedVisualPageOrdinal = manifest.visualPageOrdinal,
			observedRasterProfileKey = manifest.rasterProfileKey,
			observedPaginationFingerprint = manifest.paginationFingerprint,
			observedLayoutFingerprint = manifest.layoutFingerprint,
			observedDecorationFingerprint = manifest.decorationFingerprint,
			observedViewportAndCaptureGeometry = manifest.viewportAndCaptureGeometry,
			echoedRasterGeneration = manifest.rasterGeneration,
			passiveCommitSequence = request.sequence))
	}

	override fun capture(geometry: ReaderPassiveRasterGeometry, onCaptured: (Bitmap?) -> Unit) {
		if (!isReady) { onCaptured(null); return }
		check(pendingCallbackCount == 0)
		val request = Capture(geometry, onCaptured)
		pendingCapture = request
		check(captures.trySend(request).isSuccess)
	}

	fun completeCapture(request: Capture) {
		check(isReady && pendingCapture === request)
		pendingCapture = null
		captures.tryReceive()
		val bitmap = Bitmap.createBitmap(request.geometry.captureWidth,
			request.geometry.captureHeight, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
		deliveredBitmaps += bitmap
		request.callback(bitmap)
	}

	override fun cancelActiveCommit(onDrained: () -> Unit) {
		val commit = pendingCommit
		val capture = pendingCapture
		pendingCommit = null
		pendingCapture = null
		while (commits.tryReceive().isSuccess) { }
		while (captures.tryReceive().isSuccess) { }
		try {
			try { commit?.callback?.invoke(null) } finally { capture?.callback?.invoke(null) }
		} finally { onDrained() }
	}
	override fun pause() {
		paused = true
		cancelActiveCommit { }
	}
	override fun resume() { if (!isRetired) paused = false }
	override fun destroy() {
		if (isRetired) return
		isRetired = true
		cancelActiveCommit { }
		commits.close()
		captures.close()
	}
}

private class Task9RecoveryCommandWebView(context: Context) : WebView(context) {
	// Robolectric's stub provider omits Chromium's superclass-frame delegation.
	// The hidden Android method is absent from public SDK compile stubs.
	protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
		Shadow.directlyOn<Boolean, View>(
			this, View::class.java, "setFrame",
			ClassParameter.from(Int::class.javaPrimitiveType!!, left),
			ClassParameter.from(Int::class.javaPrimitiveType!!, top),
			ClassParameter.from(Int::class.javaPrimitiveType!!, right),
			ClassParameter.from(Int::class.javaPrimitiveType!!, bottom)
		)

	var sourceBinding: (() -> ReaderPresentationBinding)? = null
	var manifestRequests = 0
		private set
	var descriptorRequests = 0
		private set
	// Synthetic Foliate input, using the existing prototype manifest/descriptor schema.
	// Geometry and correlations come from this receiver and the actual bridge request.
	private fun descriptor(ordinal: Int) = org.json.JSONObject().apply {
		put("publicationUrl", "task9-synthetic-publication")
		put("paginationFingerprint", "task9-pagination-$width-$height")
		put("layoutFingerprint", "task9-layout-$width-$height")
		put("decorationFingerprint", "task9-undecorated")
		put("viewportWidth", width)
		put("viewportHeight", height)
		put("pageCount", 3)
		put("spineIndex", 0)
		put("href", "task9-synthetic-chapter")
		put("chapterPageIndex", ordinal)
		put("chapterPageCount", 3)
		put("visualPageOrdinal", ordinal)
	}

	private fun manifest(arguments: List<String>): String {
		val binding = checkNotNull(sourceBinding).invoke()
		val ordinal = arguments[1].toInt()
		val descriptor = descriptor(ordinal)
		val json = org.json.JSONObject().apply {
			put("captureEpoch", arguments[2].toLong())
			put("rasterGeneration", arguments[3].toLong())
			put("liveFoliateSessionId", binding.foliateSessionId)
			put("publicationSessionGeneration", binding.publicationGeneration)
			put("destinationCommitToken", "task9-commit-${checkNotNull(binding.destinationCommitIdentity).commitSequence}")
			put("rasterProfileKey", "task9-profile-$width-$height")
			for (key in listOf("paginationFingerprint", "layoutFingerprint", "decorationFingerprint")) {
				put(key, descriptor.getString(key))
			}
			put("viewportAndCaptureGeometry", org.json.JSONObject().apply {
				put("viewportWidth", width); put("viewportHeight", height)
				put("captureLeft", 0); put("captureTop", 0)
				put("captureRight", width); put("captureBottom", height)
			})
			put("opaqueCaptureTarget", "task9-target-$ordinal")
			put("visualPageOrdinal", ordinal)
			put("profileAuthority", ReaderPassiveRasterProfileAuthority.LiveRealized.serializedValue)
			put("rasterDescriptor", descriptor)
		}
		return org.json.JSONObject.quote(json.toString())
	}

	val recoveryReasons = mutableListOf<String>()
	var retainPlanCallbacks = false
	val planCallbacks = mutableListOf<android.webkit.ValueCallback<String>>()
	override fun evaluateJavascript(script: String, resultCallback: android.webkit.ValueCallback<String>?) {
		if (sourceBinding != null && script.contains("pageTurnPassiveRasterManifestInputs")) {
			manifestRequests++
			val arguments = checkNotNull(Regex("""\((\d+), (\d+), (\d+), (\d+)\)""").find(script)).groupValues
			resultCallback?.onReceiveValue(manifest(arguments))
			return
		}
		if (sourceBinding != null && (script.contains("pageTurnRasterDescriptor") ||
			script.contains("pageTurnPassiveRasterDescriptor"))) {
			descriptorRequests++
			val ordinal = checkNotNull(Regex("""\((\d+)\)""").find(script)).groupValues[1].toInt()
			resultCallback?.onReceiveValue(org.json.JSONObject.quote(descriptor(ordinal).toString()))
			return
		}
		if (retainPlanCallbacks && script.contains("pageTurnRasterPreparationPlan") && resultCallback != null) {
			planCallbacks += resultCallback
			return
		}
		if (script.startsWith("window.NavicReaderBridge?.dispatch?.(")) {
			val command = runCatching {
				org.json.JSONObject(script.substringAfter("dispatch?.(").substringBeforeLast(")"))
			}.getOrNull()
			if (command?.optString("type") == "diagnosticLocationSnapshot") {
				command.optString("reason").takeIf { it.startsWith("presentation-recovery-") }
					?.let(recoveryReasons::add)
			}
		}
		super.evaluateJavascript(script, resultCallback)
	}
}

// The host-test dependency lacks lifecycle-runtime's generated R class. Model
// the ordinary no-owner attachment without replacing any Navic lifecycle route.
@Implements(className = "androidx.lifecycle.ViewTreeLifecycleOwner", isInAndroidSdk = false)
internal class Task9NoViewTreeLifecycleOwnerShadow {
	companion object {
		@Implementation
		@JvmStatic
		fun get(view: View): androidx.lifecycle.LifecycleOwner? = null
	}
}

@Implements(GLSurfaceView::class)
internal class Task8ImmediateGlSurfaceViewShadow : ShadowGLSurfaceView() {
	@Implementation
	fun queueEvent(action: Runnable) {
		action.run()
	}

	@Implementation
	fun requestRender() = Unit
}

private class Task7PresentationStore(initial: ReaderPresentationState) {
	var state: ReaderPresentationState = initial

	fun publish(
		event: ReaderPresentationEvent
	): paige.navic.reader.ReaderPresentationEventReceipt {
		val reduction = readerPresentationReduce(state, event)
		state = reduction.state
		return readerTestPresentationReceipt(event, reduction.state, reduction.disposition)
	}
}

private class Task7ProductionCompositionFixture(
	val queue: ReaderPageRelocationQueue,
	val request: ReaderPageRelocationRequest,
	initialState: ReaderPresentationState
) {
	var controller = ReaderController(
		state = ReaderControllerState(
			readerSessionGeneration = 1L,
			presentation = initialState
		)
	)
		private set
	val visualHost = Task7VisualHandoffHost()
	val rootHandoff = ReaderWebViewVisualHandoff(visualHost)
	val rootDeadlines = HostBridgeDeadlineScheduler()
	val receipts = mutableListOf<ReaderPresentationEventReceipt>()
	val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
	val releasedClaims = mutableListOf<ReaderPageRelocationRequest>()
	private val commitHost = FakeReaderPresentationCommitHost(requireNotNull(initialState.binding))
	private var currentWebViewOrdinal = request.sourceOrdinal
	private val initialVersion = controller.presentationVersion
	private val bindingReporter = ReaderPresentationBindingReporter().also {
		it.reset(
			expectedReaderSessionGeneration = 1L,
			minimumComposeVersion = initialVersion,
			initialPresentationState = initialState
		)
		check(it.bindPublication(requireNotNull(initialState.binding)))
	}
	private val lifecycleDelivery = ReaderPresentationLifecycleDelivery().also {
		it.reset(initialVersion, observedWindowVisible = null)
		check(it.bindPublication(requireNotNull(initialState.binding).publicationIdentity))
	}
	private val dispatcher: ReaderPresentationReceiptDispatcher
	private val rootBridge = ReaderPresentationHostBridge(
		host = commitHost,
		liveEngineVisualHandoff = rootHandoff,
		liveEngineExposureRequired = { true },
		transitionTimeoutScheduler = rootDeadlines,
		transitionNowMillis = { 0L },
		onEvent = ::publishOrNull
	)
	val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
		queue = queue,
		host = visualHost,
		currentState = {
			ReaderPageRelocationVisualState(
				true, true, request.foliateSessionId, currentWebViewOrdinal,
				request.rasterGeneration, request.textureGeneration
			)
		},
		dispatch = { error("Unexpected later relocation") },
		publishRecovery = { _, reason -> recoveries += reason },
		finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
		validateContent = { _, validated ->
			validated(ReaderPageRelocationContentValidationResult.Accepted)
			ReaderPageRelocationContentValidationHandle.Completed
		},
		requestPresentationHandoff = { acknowledged ->
			val event = ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
			val receipt = publishOrNull(event)
			if (!receipt.authorizes(event)) {
				null
			} else {
				(readerPresentationDecision(requireNotNull(receipt).postState).requiredTransition as?
					ReaderRequiredTransition.ExposeLiveEngine)?.takeIf {
					it.binding.foliateSessionId == acknowledged.foliateSessionId &&
						it.binding.rasterGeneration == acknowledged.rasterGeneration &&
						it.binding.textureGeneration == acknowledged.textureGeneration
				}
			}
		},
		commitLiveEngineExposure = { proof ->
			publishOrNull(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
		},
		publishLiveEngineHandoffTerminal = ::publishOrNull,
		onCompleted = releasedClaims::add
	)

	init {
		dispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter = bindingReporter,
			lifecycleDelivery = lifecycleDelivery,
			applyHostEffect = { effect ->
				effect.decision.targetBinding?.let { commitHost.currentBinding = it }
				val transition = effect.decision.requiredTransition as?
					ReaderRequiredTransition.ExposeLiveEngine
				if (transition?.direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine) {
					coordinator.synchronizeCommonPresentationHandoff(transition)
				} else {
					coordinator.synchronizeCommonPresentationDecision(effect.decision)
				}
				rootBridge.update(effect.decision)
			}
		)
	}

	fun publish(event: ReaderPresentationEvent): ReaderPresentationEventReceipt =
		requireNotNull(publishOrNull(event))

	fun setPageTurnVisualLocation(
		binding: ReaderPresentationBinding
	): ReaderPresentationEventReceipt {
		val relocation = ReaderPresentationEvent.FoliateRelocated(
			binding,
			acknowledgement = null
		)
		currentWebViewOrdinal = request.destinationOrdinal
		val receipt = publish(relocation)
		check(acknowledge())
		return receipt
	}

	private fun acknowledge(): Boolean {
		check(
			queue.acknowledge(
				request.token.value,
				request.destinationOrdinal,
				request.foliateSessionId,
				request.rasterGeneration,
				request.textureGeneration
			)
		)
		return coordinator.onAcknowledged(request)
	}

	fun deliverRootFrame() {
		visualHost.deliverVisual()
		visualHost.presentFrame()
	}

	fun close() = rootBridge.dispose()

	private fun publishOrNull(event: ReaderPresentationEvent): ReaderPresentationEventReceipt? =
		dispatcher.dispatch(event) { dispatched ->
			val step = controller.onPresentationEvent(dispatched)
			controller = step.controller
			requireNotNull(step.presentationReceipt).also(receipts::add)
		}
}

private class Task7VisualHandoffHost : ReaderWebViewVisualHandoffHost {
	var attached = true
	override val isAttachedToWindow: Boolean get() = attached
	override val visualStateOwnerGeneration: Long get() = ownerGeneration
	var ownerGeneration = 1L
	var visualPostCount = 0
		private set
	var delayedPostCount = 0
		private set
	private val visuals = ArrayDeque<ReaderWebViewVisualDeliveryCell>()
	private val frames = ArrayDeque<() -> Unit>()
	private val delays = ArrayDeque<() -> Unit>()

	override fun synchronizeVisualStateOwner() = Unit

	override fun abandonVisualStateCallbacks() {
		while (visuals.isNotEmpty()) visuals.removeFirst().abandonPhysicalOwnership()
	}

	override fun postVisualStateCallback(
		relocationToken: String,
		handoffAttemptId: Long,
		registration: ReaderWebViewVisualDeliveryCell
	) {
		visualPostCount += 1
		visuals.addLast(registration)
	}

	override fun postOnAnimation(action: () -> Unit) {
		frames.addLast(action)
	}

	override fun postDelayed(delayMillis: Long, action: () -> Unit) {
		delayedPostCount += 1
		delays.addLast(action)
	}

	override fun removeCallbacks(action: () -> Unit) {
		frames.remove(action)
		delays.remove(action)
	}

	fun takeVisual(): ReaderWebViewVisualDeliveryCell = visuals.removeFirst()

	fun deliverVisual() {
		check(takeVisual().deliver())
	}

	fun presentFrame() {
		frames.removeFirst().invoke()
	}

	fun timeOut() {
		delays.removeFirst().invoke()
	}
}
