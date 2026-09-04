package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderControllerStep
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
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderPreparationPresentation
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLayer
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
		val bridge = ReaderPresentationHostBridge(
			host = commitHost,
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(visualHost),
			liveEngineExposureRequired = { true },
			onEvent = presentation::publish
		)

		bridge.update(readerPresentationDecision(presentation.state))
		val existingVisualRequest = visualHost.takeVisual()
		assertEquals(1, visualHost.delayedPostCount)
		val ownerGeneration = visualHost.ownerGeneration

		presentation.state = pendingB
		commitHost.currentBinding = bindingB
		bridge.update(readerPresentationDecision(pendingB))

		assertEquals(1, visualHost.delayedPostCount, "Causal rebind restarted the whole deadline")
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
		assertEquals(1, fixture.visualHost.delayedPostCount)

		val relocation = ReaderPresentationEvent.FoliateRelocated(bindingB, acknowledgement = null)
		assertTrue(fixture.setPageTurnVisualLocation(bindingB).authorizes(relocation))

		assertEquals(
			1,
			fixture.visualHost.visualPostCount,
			"The joined root transaction registered a second visual callback"
		)
		assertEquals(
			1,
			fixture.visualHost.delayedPostCount,
			"The joined root transaction restarted its deadline"
		)
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
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(binding) { decision ->
				applyFrameOwner.invoke(viewer, decision)
			},
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(visualHost),
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

		visualHost.timeOut()
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
		val bridge = ReaderPresentationHostBridge(
			host = fixture.host,
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
		assertEquals(
			assertIs<ReaderRequiredTransition.PresentNativePage>(
				readerPresentationDecision(presentation).requiredTransition
			),
			assertNotNull(
				viewerClass.task7PendingNativeHandoff(viewer),
				"Whole live-to-native deadline was not armed before native preparation"
			)
		)
		assertEquals(false, nativePreparationEnabledWhenHandoffRequested)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(pending.retainedFrame)
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
