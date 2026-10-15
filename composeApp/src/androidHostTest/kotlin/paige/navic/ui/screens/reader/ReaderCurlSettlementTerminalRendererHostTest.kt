package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Looper
import android.view.MotionEvent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.nio.Buffer
import java.time.Duration
import java.util.ArrayDeque
import karacken.curl.PageChange
import karacken.curl.PageDeck
import karacken.curl.PageDisplayRect
import karacken.curl.PageImage
import karacken.curl.PageLeafRole
import karacken.curl.PageMaterial
import karacken.curl.PageSurfaceDeckSubmissionResult
import karacken.curl.PageSurfaceListener
import karacken.curl.PageSurfaceView
import karacken.curl.PlayLikeCurlModel
import karacken.curl.PortraitPageDeck
import org.junit.Before
import org.junit.After
import org.robolectric.shadows.ShadowChoreographer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * ORIGINAL public surface/renderer control-flow bench, not a renderer replacement.
 * Only the public GL driver responses and queue/pulse scheduling are controlled.
 * No private state is read or seeded; no completion, readiness, draw receipt or lease is minted here.
 * A successful host draw says nothing about EGL, compositor latch or no-flashback on a display.
 * Missing proposed API is a fixed PREREQUISITE failure, not behavioral qualification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], shadows = [CurlTerminalGles::class, CurlTerminalUploads::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderCurlSettlementTerminalRendererHostTest {
    @Before fun resetDriver() {
        CurlTerminalGles.reset()
        // Robolectric 4.16.1 auto-advances vsync by default even in PAUSED looper mode.
        // Hold the PUBLIC platform frame boundary, not the original animator or renderer.
        ShadowChoreographer.setPaused(true)
    }
    @After fun restorePlatformFrames() = ShadowChoreographer.setPaused(false)

    @Test fun originalPublicInitializationUploadAndOrdinaryPresentedFrameReachRealDrawBody() {
        Bench().use { bench ->
            assertTrue(bench.surface.rendererGeneration > 0L, "CURL_FIXTURE_ORIGINAL_CONTEXT")
            assertEquals(3, CurlTerminalGles.uploads)
            assertEquals(0, bench.probe.completions.size)
            var callbacks = 0
            val request = bench.surface.requestNextPresentedFrame { callbacks++ }
            assertTrue(request != PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID)
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks, "CURL_FIXTURE_UNARMED_ORDINARY_DRAW")
            bench.gl()
            bench.draw()
            assertEquals(0, callbacks, "CURL_FIXTURE_DRAW_IS_NOT_MAIN_CALLBACK")
            bench.main()
            assertEquals(0, callbacks, "CURL_FIXTURE_MAIN_POST_IS_NOT_ANIMATION_PULSE")
            bench.pulse()
            assertEquals(1, callbacks)
            assertEquals(0, bench.surface.pendingCallbackCount)
            assertEquals(0, bench.probe.failures)
        }
    }

    @Test fun actualPartialDrawFailureDoesNotCompleteOriginalPresentedFrameRequest() {
        Bench().use { bench ->
            var callbacks = 0
            bench.surface.requestNextPresentedFrame { callbacks++ }
            bench.gl()
            CurlTerminalGles.failDrawAt = CurlTerminalGles.draws + 2
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks, "CURL_FIXTURE_FAILED_DRAW_NO_CALLBACK")
            assertEquals(1, bench.probe.failures)
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun originalPublicTurnCompletesOnlyThroughOriginalAnimatorAndSourceListener() {
        Bench().use { bench ->
            assertTrue(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.submit(TARGET, DESTINATION)
            bench.gl(); bench.main()
            assertEquals(PageChange.NEXT, bench.probe.lastStartedChange)
            assertEquals(0, bench.probe.completedCallbacks, "CURL_FIXTURE_START_IS_NOT_SETTLEMENT_COMPLETION")
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, bench.probe.completedCallbacks, "CURL_FIXTURE_DRAW_IS_NOT_ANIMATOR_COMPLETION")
            bench.finishAnimation()
            assertEquals(1, bench.probe.completedCallbacks, "CURL_FIXTURE_ORIGINAL_ANIMATOR_AND_LISTENER")
            assertFalse(bench.surface.isSettlementRunning)
        }
    }

    @Test fun pausedPlatformFramesAdvanceOriginalAnimatorWithoutGlDrawOrPresentedPulse() {
        CompletionApi.require()
        Bench().use { bench ->
            assertTrue(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.submit(TARGET, DESTINATION)
            bench.gl(); bench.main()
            val rendersAtStart = bench.surface.renderRequests
            val drawsAtStart = CurlTerminalGles.draws
            assertTrue(ShadowChoreographer.getNextVsyncTimeNanos() > 0L, "CURL_FIXTURE_ORIGINAL_VSYNC_SCHEDULED")
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(ShadowChoreographer.getFrameDelay())
            bench.main()
            assertTrue(bench.surface.renderRequests > rendersAtStart, "CURL_FIXTURE_ORIGINAL_ANIMATOR_FRAME_UPDATE")
            assertTrue(bench.surface.isSettlementRunning)
            assertEquals(0, bench.probe.completedCallbacks, "CURL_FIXTURE_FIRST_VSYNC_IS_NOT_COMPLETION")
            bench.finishAnimation()
            assertEquals(1, bench.probe.completedCallbacks, "CURL_FIXTURE_ORIGINAL_ANIMATOR_END_WITHOUT_DRAW")
            bench.probe.singleCompletion()
            assertEquals(drawsAtStart, CurlTerminalGles.draws, "CURL_FIXTURE_ANIMATOR_END_IS_NOT_DRAW")
            assertEquals(0, bench.probe.sourceReleases, "CURL_FIXTURE_ANIMATOR_END_RETAINS_SOURCE_BEFORE_GL")
            assertFalse(bench.surface.isSettlementRunning)
        }
    }

    @Test fun privateProducerCompletionAuthenticatesOriginalTurnAndBothRendererContexts() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val context = bench.surface.rendererGeneration
            assertTrue(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.submit(TARGET, DESTINATION)
            bench.gl(); bench.main()
            assertEquals(0, bench.probe.completions.size, "CURL_PRODUCER_NOT_START_OR_PREPARATION")
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, bench.probe.completions.size, "CURL_PRODUCER_NOT_ORDINARY_ANIMATION_DRAW")
            bench.finishAnimation()
            val completion = bench.probe.singleCompletion()
            assertSame(api.type, completion.javaClass)
            assertEquals(SOURCE, api.long(completion, "getSourceGenerationId"))
            assertEquals(GESTURE, api.long(completion, "getGestureId"))
            assertEquals(DESTINATION, api.ordinal(completion))
            assertEquals(PageChange.NEXT, api.value(completion, "getPageChange"))
            assertEquals(context, api.long(completion, "getRendererGenerationAtStart"))
            assertEquals(context, api.long(completion, "getRendererGenerationAtCompletion"))
            assertTrue(api.long(completion, "getSequence") > 0L)
            assertEquals(1, bench.probe.completedCallbacks, "CURL_PRODUCER_ONE_ORIGINAL_SOURCE_CALLBACK")
        }
    }

    @Test fun replacingListenerCannotStealCompletionFromOriginalSourceLease() {
        CompletionApi.require()
        Bench().use { bench ->
            val other = Probe()
            assertTrue(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.surface.setPageSurfaceListener(other.listener)
            bench.submit(TARGET, DESTINATION)
            bench.gl(); bench.main(); bench.finishAnimation()
            assertEquals(1, bench.probe.completions.size, "CURL_PRODUCER_ORIGINAL_LEASE_LISTENER")
            assertEquals(0, other.completions.size)
            assertEquals(1, other.prepared)
        }
    }

    @Test fun newListenerDefaultCallsOriginalLegacyListenerExactlyOnce() {
        CompletionApi.require()
        var completed = 0
        val legacy = object : PageSurfaceListener {
            override fun onSettlementCompleted(gestureId: Long, generationId: Long,
                currentLogicalPageId: String, currentOrdinal: Int, pageChange: PageChange) {
                assertEquals(GESTURE, gestureId)
                assertEquals(SOURCE, generationId)
                assertEquals(DESTINATION, currentOrdinal)
                assertEquals(PageChange.NEXT, pageChange)
                completed++
            }
        }
        Bench(legacy).use { bench ->
            assertTrue(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.submit(TARGET, DESTINATION)
            bench.gl(); bench.main(); bench.finishAnimation()
            assertEquals(1, completed, "CURL_PRODUCER_LEGACY_DEFAULT_ONCE")
        }
    }

    @Test fun cancelledActualAnimatorCannotMintCompletionOrReplayItOnLatePulse() {
        CompletionApi.require()
        Bench().use { bench ->
            assertTrue(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.surface.cancelGesture(GESTURE)
            bench.finishAnimation(); bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, bench.probe.cancelled)
            assertEquals(0, bench.probe.completions.size, "CURL_PRODUCER_CANCELLED_NO_COMPLETION")
            assertEquals(0, bench.probe.completedCallbacks)
        }
    }

    @Test fun originalStartedListenerCancellationCannotRestartAnimatorOrMintCompletion() {
        CompletionApi.require()
        lateinit var surface: PageSurfaceView
        var cancelled = 0
        var completed = 0
        val listener = object : PageSurfaceListener {
            override fun onSettlementStarted(gestureId: Long, generationId: Long,
                sourceLogicalPageId: String, targetLogicalPageId: String, pageChange: PageChange) {
                surface.cancelGesture(gestureId)
            }
            override fun onSettlementCancelled(gestureId: Long, generationId: Long, currentLogicalPageId: String) {
                cancelled++
            }
            override fun onSettlementCompleted(gestureId: Long, generationId: Long,
                currentLogicalPageId: String, currentOrdinal: Int, pageChange: PageChange) {
                completed++
            }
        }
        Bench(listener).use { bench ->
            surface = bench.surface
            assertTrue(surface.turn(PageChange.NEXT, GESTURE))
            val rendersAfterCancellation = bench.surface.renderRequests
            bench.finishAnimation()
            assertEquals(rendersAfterCancellation, bench.surface.renderRequests, "CURL_PRODUCER_CANCELLED_ANIMATOR_NOT_RESTARTED")
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, cancelled)
            assertEquals(0, completed, "CURL_PRODUCER_STARTED_CALLBACK_CANCEL_NO_RESTART")
            assertFalse(surface.isSettlementRunning)
        }
    }

    @Test fun rejectedOriginalTurnCannotMintCompletion() {
        CompletionApi.require()
        Bench(nextAvailable = false).use { bench ->
            assertFalse(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.finishAnimation(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, bench.probe.rejected)
            assertEquals(0, bench.probe.completions.size, "CURL_PRODUCER_REJECTED_NO_COMPLETION")
        }
    }

    @Test fun legacyUntaggedTurnCannotMintInstalledGestureCompletion() {
        CompletionApi.require()
        Bench().use { bench ->
            assertTrue(bench.surface.turn(PageChange.NEXT))
            bench.submit(TARGET, DESTINATION)
            bench.gl(); bench.main(); bench.finishAnimation()
            assertEquals(1, bench.probe.completedCallbacks)
            assertEquals(0, bench.probe.completions.size, "CURL_PRODUCER_UNOBSERVED_GESTURE_NO_COMPLETION")
        }
    }

    @Test fun actualSlowTouchSnapbackWithNoneDoesNotMintNavigatingCompletion() {
        CompletionApi.require()
        Bench().use { bench ->
            val distance = android.view.ViewConfiguration.get(bench.surface.context).scaledTouchSlop + 1f
            assertTrue(distance < WIDTH / 2f, "CURL_FIXTURE_REAL_DRAG_ABOVE_PLATFORM_SLOP")
            bench.touch(MotionEvent.ACTION_DOWN, 60f, 0L)
            bench.touch(MotionEvent.ACTION_MOVE, 60f - distance, 1_000L)
            bench.touch(MotionEvent.ACTION_UP, 60f - distance, 2_000L)
            bench.finishAnimation()
            assertEquals(PageChange.NONE, bench.probe.lastStartedChange, "CURL_FIXTURE_REAL_NONE_SETTLEMENT")
            assertEquals(1, bench.probe.completedCallbacks)
            assertEquals(0, bench.probe.completions.size, "CURL_PRODUCER_NONE_NO_COMPLETION")
        }
    }

    @Test fun explicitSuccessorRequestRequiresSuccessfulExactDrawAndOriginalMainPulse() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var callbacks = 0
            val request = api.request(bench.surface, completion, TARGET) { callbacks++ }
            assertTrue(request != PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID, "CURL_TERMINAL_EXPLICIT_TARGET_ADMITTED")
            assertEquals(0, callbacks, "CURL_TERMINAL_ANIMATION_END_IS_NOT_DRAW")
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks, "CURL_TERMINAL_SOURCE_DRAW_BEFORE_ACTIVATION_IS_NOT_SUCCESSOR")
            bench.gl()
            assertEquals(0, callbacks, "CURL_TERMINAL_ACTIVATION_REQUEST_RENDER_IS_NOT_DRAW")
            val draws = CurlTerminalGles.draws
            bench.draw()
            assertTrue(CurlTerminalGles.draws > draws, "CURL_TERMINAL_ORIGINAL_DRAW_BODY_ENTERED")
            assertEquals(0, callbacks)
            bench.main()
            assertEquals(0, callbacks)
            bench.pulse()
            assertEquals(1, callbacks, "CURL_TERMINAL_EXACT_DRAW_MAIN_DELIVERY")
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, callbacks, "CURL_TERMINAL_ONESHOT_DELIVERY")
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun ordinarySharedWaiterDoesNotPreventTypedCandidateActivation() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var ordinary = 0
            var terminal = 0
            bench.surface.requestNextPresentedFrame { ordinary++ }
            api.request(bench.surface, completion, TARGET) { terminal++ }
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, ordinary)
            assertEquals(1, terminal, "CURL_TERMINAL_ARM_BOOLEAN_IS_NOT_ADMISSION")
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun partialOriginalSuccessorDrawFailureCannotDeliverTerminalCallback() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var callbacks = 0
            api.request(bench.surface, completion, TARGET) { callbacks++ }
            bench.gl()
            CurlTerminalGles.failDrawAt = CurlTerminalGles.draws + 2
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks, "CURL_TERMINAL_PARTIAL_DRAW_NOT_SUCCESS")
            assertEquals(1, bench.probe.failures)
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun foreignOriginalCompletionCannotBeAuthenticatedBySequenceAlone() {
        val api = CompletionApi.require()
        Bench().use { first ->
            val foreign = first.settle()
            Bench().use { second ->
                val own = second.settle()
                // Matching public provenance is not original producer identity; a sequence is not a capability.
                assertEquals(api.long(foreign, "getSourceGenerationId"), api.long(own, "getSourceGenerationId"))
                assertEquals(api.long(foreign, "getGestureId"), api.long(own, "getGestureId"))
                assertEquals(api.ordinal(foreign), api.ordinal(own))
                var callbacks = 0
                assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                    api.request(second.surface, foreign, TARGET) { callbacks++ }, "CURL_TERMINAL_FOREIGN_PRODUCER_DENIED")
                second.gl(); second.draw(); second.main(); second.pulse()
                assertEquals(0, callbacks)
            }
        }
    }

    @Test fun explicitUnknownTargetIsDeniedWithoutDirectionDerivedFallback() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var callbacks = 0
            assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                api.request(bench.surface, completion, 91L) { callbacks++ }, "CURL_TERMINAL_EXPLICIT_UNKNOWN_TARGET_DENIED")
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks)
        }
    }

    @Test fun preparedExplicitTargetWithWrongObservedOrdinalCannotCertifySettlement() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle(targetOrdinal = 23)
            var callbacks = 0
            assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                api.request(bench.surface, completion, TARGET) { callbacks++ }, "CURL_TERMINAL_WRONG_TARGET_ORDINAL_DENIED")
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks)
        }
    }

    @Test fun duplicateIssuanceAndCancelledRecordCannotCreateAnotherPhysicalRequest() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var callbacks = 0
            val first = api.request(bench.surface, completion, TARGET) { callbacks++ }
            assertTrue(first != PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID)
            assertTrue(api.current(bench.surface, completion), "CURL_TERMINAL_ISSUANCE_RETAINS_RESOLVER_AUTHORITY")
            assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                api.request(bench.surface, completion, TARGET) { callbacks++ }, "CURL_TERMINAL_DUPLICATE_ISSUANCE_DENIED")
            assertTrue(api.current(bench.surface, completion), "CURL_TERMINAL_DUPLICATE_NO_EFFECT")
            assertTrue(bench.surface.cancelPresentedFrameRequest(first))
            assertFalse(api.current(bench.surface, completion), "CURL_TERMINAL_CANCEL_RETIRES_AUTHORITY")
            assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                api.request(bench.surface, completion, TARGET) { callbacks++ }, "CURL_TERMINAL_CANCELLED_RECORD_NOT_REISSUED")
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks)
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun completionFromPriorRendererContextCannotEnterTerminalRequest() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            bench.recreateContext()
            var callbacks = 0
            assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                api.request(bench.surface, completion, TARGET) { callbacks++ }, "CURL_TERMINAL_OLD_CONTEXT_ADMISSION_DENIED")
        }
    }

    @Test fun rendererContextChangeDuringPhysicalSettlementCannotYieldUsableCompletion() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            assertTrue(bench.surface.turn(PageChange.NEXT, GESTURE))
            bench.submit(TARGET, DESTINATION); bench.gl(); bench.main()
            bench.recreateContext()
            bench.finishAnimation()
            assertTrue(bench.probe.completions.size <= 1, "CURL_PRODUCER_CONTEXT_CHANGE_NO_DUPLICATE")
            var callbacks = 0
            bench.probe.completions.singleOrNull()?.let { completion ->
                assertTrue(api.long(completion, "getRendererGenerationAtStart") !=
                    api.long(completion, "getRendererGenerationAtCompletion"))
                assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                    api.request(bench.surface, completion, TARGET) { callbacks++ }, "CURL_TERMINAL_START_COMPLETION_CONTEXT_MISMATCH_DENIED")
            }
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks)
        }
    }

    @Test fun cancelBeforeGlAfterArmAndAfterDrawBeforeMainEachSuppressOriginalCallback() {
        val api = CompletionApi.require()
        for (phase in CancelPhase.entries) {
            Bench().use { bench ->
                val completion = bench.settle()
                var callbacks = 0
                val request = api.request(bench.surface, completion, TARGET) { callbacks++ }
                assertTrue(request != PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID)
                if (phase != CancelPhase.BEFORE_GL) bench.gl()
                if (phase == CancelPhase.AFTER_DRAW) bench.draw()
                assertTrue(bench.surface.cancelPresentedFrameRequest(request), "CURL_TERMINAL_CANCEL_EXACT_PHASE")
                bench.gl(); bench.main(); bench.pulse(); bench.draw(); bench.main(); bench.pulse()
                assertEquals(0, callbacks, "CURL_TERMINAL_CANCELLED_LATE_CALLBACK_SUPPRESSED")
                assertEquals(0, bench.surface.pendingCallbackCount)
            }
        }
    }

    @Test fun contextInvalidationAtGlDrawAndMainBoundariesEachSuppressesTerminalCallback() {
        val api = CompletionApi.require()
        for (phase in CancelPhase.entries) {
            Bench().use { bench ->
                val completion = bench.settle()
                var callbacks = 0
                api.request(bench.surface, completion, TARGET) { callbacks++ }
                if (phase != CancelPhase.BEFORE_GL) bench.gl()
                if (phase == CancelPhase.AFTER_DRAW) bench.draw()
                bench.recreateContext()
                bench.gl(); bench.draw(); bench.main(); bench.pulse()
                assertEquals(0, callbacks, "CURL_TERMINAL_CONTEXT_RECHECK_ALL_BOUNDARIES")
                assertEquals(0, bench.surface.pendingCallbackCount)
            }
        }
    }

    @Test fun targetReleaseAtGlDrawAndMainBoundariesEachSuppressesTerminalCallback() {
        val api = CompletionApi.require()
        for (phase in CancelPhase.entries) {
            Bench().use { bench ->
                val completion = bench.settle()
                var callbacks = 0
                api.request(bench.surface, completion, TARGET) { callbacks++ }
                if (phase != CancelPhase.BEFORE_GL) bench.gl()
                if (phase == CancelPhase.AFTER_DRAW) bench.draw()
                bench.surface.releaseDeck(TARGET)
                bench.gl(); bench.draw(); bench.main(); bench.pulse()
                assertEquals(0, callbacks, "CURL_TERMINAL_TARGET_RELEASE_RECHECK_ALL_BOUNDARIES")
                assertEquals(0, bench.surface.pendingCallbackCount)
            }
        }
    }

    @Test fun sourceRetirementAtGlDrawAndMainBoundariesEachSuppressesTerminalCallback() {
        val api = CompletionApi.require()
        for (phase in CancelPhase.entries) {
            Bench().use { bench ->
                val completion = bench.settle()
                var callbacks = 0
                api.request(bench.surface, completion, TARGET) { callbacks++ }
                if (phase != CancelPhase.BEFORE_GL) bench.gl()
                if (phase == CancelPhase.AFTER_DRAW) bench.draw()
                bench.surface.setSelectedFrameGeneration(TARGET)
                bench.gl(); bench.main()
                assertEquals(1, bench.probe.sourceReleases, "CURL_FIXTURE_GENUINE_SOURCE_LEASE_RELEASE")
                bench.draw(); bench.main(); bench.pulse()
                assertEquals(0, callbacks, "CURL_TERMINAL_SOURCE_RELEASE_RECHECK_ALL_BOUNDARIES")
                assertEquals(0, bench.surface.pendingCallbackCount)
            }
        }
    }

    @Test fun genuineSourceRetirementBeforeAdmissionInvalidatesItsCompletion() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            bench.gl()
            bench.surface.setSelectedFrameGeneration(TARGET)
            bench.gl(); bench.main()
            assertEquals(1, bench.probe.sourceReleases, "CURL_FIXTURE_GENUINE_SOURCE_LEASE_RELEASE")
            var callbacks = 0
            assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                api.request(bench.surface, completion, TARGET) { callbacks++ }, "CURL_TERMINAL_RETIRED_SOURCE_DENIED")
        }
    }

    @Test fun newerOriginalSelectionSurvivesObsoleteCancellationAndStaleMainPulse() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var stale = 0
            val obsolete = api.request(bench.surface, completion, TARGET) { stale++ }
            bench.gl(); bench.draw() // Genuine draw; leave original main callback pending.
            bench.surface.setSelectedFrameGeneration(TARGET)
            bench.gl(); bench.main()
            // Source invalidation may already have cancelled the obsolete request.
            bench.surface.cancelPresentedFrameRequest(obsolete)
            bench.gl()
            var current = 0
            bench.surface.requestNativePagePresentedFrame(TARGET) { current++ }
            bench.gl()
            bench.pulse() // Original obsolete terminal pulse cannot undo the newer candidate.
            assertEquals(0, stale, "CURL_TERMINAL_STALE_MAIN_CALLBACK_DENIED")
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, current, "CURL_TERMINAL_OBSOLETE_ROLLBACK_CANNOT_UNDO_NEW_SELECTION")
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun originalCallbackRecheckCanConsumeAuthenticRecordBeforeSourceReceiptRetirement() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var callbacks = 0
            api.request(bench.surface, completion, TARGET) {
                assertEquals(0, bench.probe.sourceReleases, "CURL_TERMINAL_SOURCE_RETAINED_DURING_CALLBACK")
                assertEquals(SOURCE, api.long(completion, "getSourceGenerationId"))
                assertTrue(api.current(bench.surface, completion), "CURL_TERMINAL_AUTHENTIC_CURRENT_DURING_CALLBACK")
                assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                    api.request(bench.surface, completion, TARGET) { callbacks++ }, "CURL_TERMINAL_CONSUMED_BEFORE_REENTRANCY")
                assertEquals(DESTINATION, api.ordinal(completion))
                callbacks++
                // This is the public selection effect of the receipt consumer, not a fabricated receipt.
                bench.surface.setSelectedFrameGeneration(TARGET)
            }
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, callbacks)
            bench.gl(); bench.main()
            assertEquals(1, bench.probe.sourceReleases)
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun typedRequestUsesOriginalThreeCallbackBoundAndDoesNotAllocateSecondRegistry() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var ordinary = 0
            repeat(2) { bench.surface.requestNextPresentedFrame { ordinary++ } }
            var terminal = 0
            val terminalRequest = api.request(bench.surface, completion, TARGET) { terminal++ }
            assertTrue(terminalRequest != PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID)
            assertEquals(3, bench.surface.pendingCallbackCount, "CURL_TERMINAL_ORIGINAL_SHARED_CALLBACK_BOUND")
            var capacityDenied = false
            try { bench.surface.requestNextPresentedFrame { ordinary++ } }
            catch (_: IllegalStateException) { capacityDenied = true }
            assertTrue(capacityDenied, "CURL_TERMINAL_NO_SECOND_CALLBACK_REGISTRY")
            assertTrue(bench.surface.pendingCallbackCount <= bench.surface.pendingCallbackLimit)
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(2, ordinary)
            assertEquals(1, terminal)
            assertEquals(0, bench.surface.pendingCallbackCount)
        }
    }

    @Test fun rejectedOriginalMainPostRetiresTypedCandidateWithoutDeliveringReceipt() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var callbacks = 0
            api.request(bench.surface, completion, TARGET) { callbacks++ }
            bench.gl()
            bench.surface.rejectNextMainPost = true
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(0, callbacks, "CURL_TERMINAL_REJECTED_MAIN_POST_NO_CALLBACK")
            assertEquals(0, bench.surface.pendingCallbackCount)
            assertFalse(api.current(bench.surface, completion), "CURL_TERMINAL_REJECTED_MAIN_POST_RETIRES_RECORD")
        }
    }

    @Test fun ordinaryDrawAlreadyArmedCannotSubstituteForTypedSuccessorDraw() {
        val api = CompletionApi.require()
        Bench().use { bench ->
            val completion = bench.settle()
            var ordinary = 0
            bench.surface.requestNextPresentedFrame { ordinary++ }
            bench.gl()
            var terminal = 0
            api.request(bench.surface, completion, TARGET) { terminal++ }
            bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, ordinary)
            assertEquals(0, terminal, "CURL_TERMINAL_ORDINARY_DRAW_NOT_TYPED_ARM")
            bench.gl(); bench.draw(); bench.main(); bench.pulse()
            assertEquals(1, terminal, "CURL_TERMINAL_ORIGINAL_SUCCESSOR_DRAW_REQUIRED")
        }
    }

    @Test fun unissuedAuthenticCompletionIsRetiredByOriginalCancelAndDetach() {
        val api = CompletionApi.require()
        repeat(2) { phase ->
            Bench().use { bench ->
                val completion = bench.settle()
                assertTrue(api.current(bench.surface, completion), "CURL_TERMINAL_ORIGINAL_RECORD_CURRENT")
                if (phase == 0) bench.surface.cancelGesture(GESTURE) else bench.surface.detach()
                assertFalse(api.current(bench.surface, completion), "CURL_TERMINAL_ORIGINAL_LIFECYCLE_RETIRES_RECORD")
                assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
                    api.request(bench.surface, completion, TARGET) {}, "CURL_TERMINAL_RETIRED_UNISSUED_DENIED")
            }
        }
    }

    private enum class CancelPhase { BEFORE_GL, AFTER_ARM, AFTER_DRAW }

    /** Public API lookup only. This never invokes constructors or substitutes an ordinary frame API. */
    private class CompletionApi private constructor(val type: Class<*>, private val request: Method) {
        fun request(surface: PageSurfaceView, completion: Any, target: Long, callback: () -> Unit): Long =
            invoke(request, surface, completion, target, Runnable(callback)) as Long
        fun current(surface: PageSurfaceView, completion: Any): Boolean =
            invoke(publicMethod(PageSurfaceView::class.java, "isCurlSettlementCompletionCurrent", type), surface, completion) as Boolean
        fun value(completion: Any, getter: String): Any? = invoke(publicMethod(type, getter), completion)
        fun long(completion: Any, getter: String): Long = value(completion, getter) as Long
        fun ordinal(completion: Any): Int = value(completion, "getCurrentOrdinal") as Int

        companion object {
            fun require(): CompletionApi {
                val type = try { Class.forName("karacken.curl.PageSurfaceView\$CurlSettlementCompletion") }
                catch (_: ClassNotFoundException) { throw AssertionError("CURL_PREREQUISITE_COMPLETION_API_MISSING") }
                assertTrue(Modifier.isPublic(type.modifiers) && Modifier.isFinal(type.modifiers), "CURL_COMPLETION_PUBLIC_FINAL")
                assertTrue(type.declaredConstructors.isNotEmpty() &&
                    type.declaredConstructors.all { Modifier.isPrivate(it.modifiers) }, "CURL_COMPLETION_PRIVATE_PRODUCER")
                val request = publicMethod(PageSurfaceView::class.java,
                    "requestCurlSettlementTerminalPresentedFrame", type, java.lang.Long.TYPE, Runnable::class.java)
                val listener = publicMethod(PageSurfaceListener::class.java, "onSettlementCompleted",
                    java.lang.Long.TYPE, java.lang.Long.TYPE, String::class.java, Integer.TYPE, PageChange::class.java, type)
                assertTrue(listener.isDefault, "CURL_COMPLETION_ORIGINAL_LISTENER_DEFAULT")
                return CompletionApi(type, request)
            }

            private fun publicMethod(type: Class<*>, name: String, vararg arguments: Class<*>): Method =
                try { type.getMethod(name, *arguments) }
                catch (_: NoSuchMethodException) { throw AssertionError("CURL_PREREQUISITE_COMPLETION_API_MISSING") }

            private fun invoke(method: Method, receiver: Any, vararg arguments: Any): Any? =
                try { method.invoke(receiver, *arguments) }
                catch (failure: InvocationTargetException) { throw failure.targetException }
        }
    }

    /** The only stored completion comes from the ORIGINAL listener's proposed public overload. */
    private class Probe {
        val completions = mutableListOf<Any>()
        var completedCallbacks = 0
        var prepared = 0
        var cancelled = 0
        var rejected = 0
        var failures = 0
        var sourceReleases = 0
        var lastStartedChange: PageChange? = null
        val listener = Proxy.newProxyInstance(PageSurfaceListener::class.java.classLoader,
            arrayOf(PageSurfaceListener::class.java)) { proxy, method, values ->
            when (method.name) {
                "onSettlementStarted" -> lastStartedChange = values?.last() as PageChange
                "onSettlementCompleted" -> {
                    completedCallbacks++
                    if (method.parameterCount == 6) values?.get(5)?.let(completions::add)
                }
                "onDeckPrepared" -> prepared++
                "onSettlementCancelled" -> cancelled++
                "onGestureRejected" -> rejected++
                "onRenderFailure" -> failures++
                "onDeckReleased" -> if (values?.get(0) == SOURCE) sourceReleases++
                "toString" -> return@newProxyInstance "synthetic-curl-listener"
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "equals" -> return@newProxyInstance proxy === values?.get(0)
            }
            null
        } as PageSurfaceListener

        fun singleCompletion(): Any {
            assertEquals(1, completions.size, "CURL_PRODUCER_ORIGINAL_COMPLETION_REQUIRED")
            return completions.single()
        }
    }

    private class Bench(sourceListener: PageSurfaceListener? = null, nextAvailable: Boolean = true) : AutoCloseable {
        val probe = Probe()
        val surface = ControlledSurface(RuntimeEnvironment.getApplication())
        private val bitmaps = mutableListOf<Bitmap>()
        init {
            surface.setPageSurfaceListener(sourceListener ?: probe.listener)
            surface.registerMainTerminalExecutor { action -> action.run(); true }
            surface.layout(0, 0, WIDTH, HEIGHT)
            surface.attach()
            surface.capturedRenderer.onSurfaceCreated(null, null)
            surface.capturedRenderer.onSurfaceChanged(null, WIDTH, HEIGHT)
            gl(); main()
            assertNotNull(surface.renderCapabilities, "CURL_FIXTURE_ORIGINAL_CAPABILITIES")
            submit(SOURCE, 4, nextAvailable)
            gl(); main()
            surface.setSelectedFrameGeneration(SOURCE)
            gl()
            assertEquals(0, probe.failures, "CURL_FIXTURE_ORIGINAL_PREPARATION")
            assertEquals(4, surface.currentPosition)
        }

        fun submit(generation: Long, center: Int, nextAvailable: Boolean = true) {
            val rect = PageDisplayRect(0, 0, WIDTH, HEIGHT)
            val paper = PageMaterial(generation, -1, -1, 0xFF000000.toInt(), -1,
                PageLeafRole.FULL, rect, rect, null, 1)
            fun page(ordinal: Int): PageImage<Bitmap> {
                val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                bitmap.setHasAlpha(false)
                bitmaps += bitmap
                return PageImage(generation, "synthetic-curl-leaf-$ordinal", ordinal, 4, 4, rect, bitmap, paper)
            }
            val deck: PageDeck<Bitmap> = PortraitPageDeck(page(center - 1), page(center), page(center + 1), true, nextAvailable)
            assertEquals(PageSurfaceDeckSubmissionResult.Status.ACCEPTED,
                surface.submitDeckWithResult(deck).status, "CURL_FIXTURE_ORIGINAL_DECK_ACCEPTED")
        }

        fun settle(targetOrdinal: Int = DESTINATION): Any {
            assertTrue(surface.turn(PageChange.NEXT, GESTURE), "CURL_FIXTURE_ORIGINAL_TURN_ACCEPTED")
            submit(TARGET, targetOrdinal)
            gl(); main()
            finishAnimation()
            // Leave the genuine promotion GL command queued to exercise admission before activation.
            return probe.singleCompletion()
        }
        fun finishAnimation() {
            val looper = Shadows.shadowOf(Looper.getMainLooper())
            val frameDelay = ShadowChoreographer.getFrameDelay()
            val frameMillis = frameDelay.toMillis()
            assertTrue(frameMillis in 1L..64L, "CURL_FIXTURE_BOUNDED_PLATFORM_FRAME_DELAY")
            val budgetMillis = PlayLikeCurlModel.SETTLEMENT_DURATION_MILLIS + 64L
            // 4.16.1 ShadowDisplayEventReceiver schedules paused vsync through a clock listener,
            // not a future looper message. ShadowPausedLooper.idleFor's single end-time jump
            // therefore delivers only the first animator frame; it starts, not ends, the animation.
            // Advance public platform frames individually within the unchanged elapsed-time budget.
            repeat((budgetMillis / frameMillis).toInt()) { looper.idleFor(frameDelay) }
            looper.idleFor(Duration.ofMillis(budgetMillis % frameMillis))
            main()
        }
        fun touch(action: Int, x: Float, elapsed: Long) {
            val event = MotionEvent.obtain(100L, 100L + elapsed, action, x, 30f, 0)
            try { assertTrue(surface.onPageTouchEvent(event, GESTURE)) } finally { event.recycle() }
        }
        fun recreateContext() {
            surface.capturedRenderer.onSurfaceCreated(null, null)
            surface.capturedRenderer.onSurfaceChanged(null, WIDTH, HEIGHT)
            main()
        }
        fun gl() = surface.glQueue.drain()
        fun draw() = surface.capturedRenderer.onDrawFrame(null)
        fun main() {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            surface.mainQueue.drain()
        }
        fun pulse() = surface.pulseQueue.drain()
        override fun close() {
            surface.cancelGesture()
            surface.setSelectedFrameGeneration(-1L)
            gl(); main()
            surface.dispose()
            gl(); main(); pulse()
            // Terminal disposal has ended all synthetic input leases; these are not retained output.
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /** Capture only the PUBLIC setRenderer argument; execute that original renderer unchanged. */
    private class ControlledSurface(context: Context) : PageSurfaceView(context) {
        lateinit var capturedRenderer: GLSurfaceView.Renderer
        val glQueue = ArrayDeque<Runnable>()
        val mainQueue = ArrayDeque<Runnable>()
        val pulseQueue = ArrayDeque<Runnable>()
        var rejectNextMainPost = false
        var renderRequests = 0
        override fun setRenderer(renderer: GLSurfaceView.Renderer) { capturedRenderer = renderer }
        override fun setRenderMode(mode: Int) = Unit
        override fun setPreserveEGLContextOnPause(preserve: Boolean) = Unit
        override fun onResume() = Unit
        override fun onPause() = Unit
        override fun queueEvent(action: Runnable) { glQueue.addLast(action) }
        override fun requestRender() { renderRequests++ }
        override fun post(action: Runnable): Boolean {
            if (rejectNextMainPost) { rejectNextMainPost = false; return false }
            mainQueue.addLast(action); return true
        }
        override fun postOnAnimation(action: Runnable) { pulseQueue.addLast(action) }
    }

    companion object {
        private const val SOURCE = 11L
        private const val TARGET = 29L // Deliberately not a direction-derived successor generation.
        private const val GESTURE = 71L
        private const val DESTINATION = 5
        private const val WIDTH = 80
        private const val HEIGHT = 60
        private fun ArrayDeque<Runnable>.drain() {
            var executed = 0
            while (isNotEmpty()) {
                assertTrue(++executed <= 64, "CURL_FIXTURE_BOUNDED_PLATFORM_QUEUE")
                removeFirst().run()
            }
        }
    }
}

/** Controlled host driver answers only; never an EGL or display-success claim. */
@Implements(value = GLES20::class, callThroughByDefault = false)
class CurlTerminalGles {
    companion object {
        var draws = 0
        var uploads = 0
        var failDrawAt = -1
        private var nextObject = 1
        fun reset() { draws = 0; uploads = 0; failDrawAt = -1; nextObject = 1 }
        @JvmStatic @Implementation fun glCreateShader(type: Int): Int = nextObject++
        @JvmStatic @Implementation fun glCreateProgram(): Int = nextObject++
        @JvmStatic @Implementation fun glGetAttribLocation(program: Int, name: String): Int = 1
        @JvmStatic @Implementation fun glGetUniformLocation(program: Int, name: String): Int = 1
        @JvmStatic @Implementation fun glGetShaderiv(shader: Int, name: Int, result: IntArray, offset: Int) {
            result[offset] = GLES20.GL_TRUE
        }
        @JvmStatic @Implementation fun glGetProgramiv(program: Int, name: Int, result: IntArray, offset: Int) {
            result[offset] = GLES20.GL_TRUE
        }
        @JvmStatic @Implementation fun glGetIntegerv(name: Int, result: IntArray, offset: Int) { result[offset] = 4096 }
        @JvmStatic @Implementation fun glGenBuffers(count: Int, result: IntArray, offset: Int) {
            repeat(count) { result[offset + it] = nextObject++ }
        }
        @JvmStatic @Implementation fun glGenTextures(count: Int, result: IntArray, offset: Int) {
            repeat(count) { result[offset + it] = nextObject++ }
        }
        @JvmStatic @Implementation fun glGetError(): Int = GLES20.GL_NO_ERROR
        @JvmStatic @Implementation fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) = draw()
        @JvmStatic @Implementation fun glDrawElements(mode: Int, count: Int, type: Int, indices: Buffer) = draw()
        private fun draw() {
            draws++
            if (draws == failDrawAt) throw IllegalStateException("synthetic-draw-boundary")
        }
    }
}

@Implements(value = GLUtils::class, callThroughByDefault = false)
class CurlTerminalUploads {
    companion object {
        @JvmStatic @Implementation fun texImage2D(target: Int, level: Int, bitmap: Bitmap, border: Int) {
            CurlTerminalGles.uploads++
        }
    }
}
