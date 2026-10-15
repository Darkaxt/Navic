package paige.navic.ui.screens.reader

import android.os.Build
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class, Task398CanonicalStoragePathShadow::class])
class ReaderNativeCurrentDestinationAdmissionHostTest {
    @Test fun missingActualNativeContextRejectsBeforeQueryAndRetryUsesSameRequest() = runTest {
        withCurrentDestinationEngine { bench ->
            val f = CarrierSemanticFixture(); var ingressCalls = 0
            assertNull(bench.nativeBinding())
            val before = bench.lastScript()
            val request = bench.nativeRequest(f.gateway) { _, _ -> ingressCalls++; null }
            f.dispatch { call -> request(call.origin, call.context, call.onReceipt) }
            assertTrue(f.coordinator.snapshot().lastOutcome == ReaderTransitionOutcomeKind.Failed && !f.coordinator.snapshot().releaseOnlySink)
            assertEquals(0, f.executor.activeSlotCount); assertEquals(1, f.registry.activeHandleCount)
            f.retry()
            assertEquals(2, f.calls.size); assertEquals(1, f.handles.size)
            assertTrue(f.calls[0].origin != f.calls[1].origin)
            assertTrue(bench.lastScript() == before && bench.pending().pendingCommands.isEmpty())
            assertEquals(0, ingressCalls); assertEquals(0, f.receipts)
            assertFalse(f.calls.first().context.reportAsyncFailure(f.calls.first().origin, ReaderSemanticAsyncFailure.Rejected))
            f.close()
        }
    }

    @Test fun commonOriginIngressIsModelOnlyAndEqualBindingIsGenuineIdempotent() {
        val f = CarrierSemanticFixture()
        // Explicit common-policy scaffolding, not measured Native context or renderer authority.
        val binding = ReaderPresentationBinding("synthetic-model", 1L, 1L, 1L,
            ReaderDestinationCommitIdentity("synthetic-model", 1L), null, null, null)
        var model = ReaderCoordinator(controller = f.opened)
        model = model.onPresentationEvent(ReaderPresentationEvent.PublicationOpened(binding)).coordinator
        val originalWordSync = model.wordSync; val originalView = model.viewState
        var returned: ReaderCoordinatorStep? = null
        f.dispatch { call ->
            val destination = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic-model", 2L))
            val step = model.reduceSemanticDestination(ReaderPresentationEvent.FoliateRelocated(destination, null), call.origin)
            model = step.coordinator; returned = step
            step.presentationReceipt?.let(call.onReceipt)
            ReaderSemanticExecutableResult.Accepted
        }
        val step = assertNotNull(returned); val receipt = assertNotNull(step.presentationReceipt)
        assertTrue(receipt.origin === f.calls.single().origin)
        assertNotNull(receipt.semanticReceipt)
        assertTrue(receipt.effects.isEmpty() && step.presentationEffects.isEmpty() && step.wordSyncEffects.isEmpty())
        assertTrue(step.progressToSave == null && step.readaloudPlaybackCommand == null && step.readaloudReaderInteraction == null && step.whispersyncAudioSeekTarget == null)
        assertTrue(model.wordSync === originalWordSync && model.viewState === originalView)
        assertEquals(1, f.receipts)
        assertEquals(0, f.executor.activeSlotCount)
        var equalReceipt: ReaderPresentationEventReceipt? = null
        f.dispatch { call ->
            val equal = model.reduceSemanticDestination(ReaderPresentationEvent.FoliateRelocated(assertNotNull(model.controller.state.presentation.binding), null), call.origin)
            model = equal.coordinator; equalReceipt = equal.presentationReceipt
            ReaderSemanticExecutableResult.Rejected
        }
        val equal = assertNotNull(equalReceipt)
        assertTrue(equal.disposition == ReaderPresentationEventDisposition.Idempotent)
        assertNull(equal.semanticReceipt)
        assertEquals(1, f.receipts)
        f.close()
    }

    @Test fun actualOldOriginCannotMutateNextCommonControllerEpoch() {
        val f = CarrierSemanticFixture()
        val next = f.opened.open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity(bookId = "synthetic-next-carrier", resourceHref = "synthetic.epub"),
            url = "https://synthetic.invalid/engine-bench.epub")).controller
        val model = ReaderCoordinator(controller = next)
        var result: ReaderCoordinatorStep? = null
        f.dispatch { call ->
            val binding = ReaderPresentationBinding("synthetic-common-epoch", 1L, 1L, 1L,
                ReaderDestinationCommitIdentity("synthetic-common-epoch", 1L), null, null, null)
            result = model.reduceSemanticDestination(ReaderPresentationEvent.FoliateRelocated(binding, null), call.origin)
            ReaderSemanticExecutableResult.Rejected
        }
        val step = assertNotNull(result)
        assertTrue(step.coordinator === model && step.presentationReceipt == null && step.presentationEffects.isEmpty() && step.wordSyncEffects.isEmpty())
        assertEquals(0, f.receipts)
        f.close()
    }

    @Test fun capturedGatewayEpochCannotBorrowReplacementAttachment() {
        val f = CarrierSemanticFixture()
        val epoch = assertNotNull(f.gateway.captureActivatedSemanticEpoch())
        assertTrue(f.gateway.isCurrentActivatedSemanticEpoch(epoch))
        f.close()
        val nextRegistry = ReaderSemanticExecutableRequestRegistry(f.opened.state.readerSessionGeneration)
        val next = f.gateway.attachActivated(enqueue = {}, retireSemanticRequests = nextRegistry::clear, registerSemanticRequest = nextRegistry::register)
        assertFalse(f.gateway.isCurrentActivatedSemanticEpoch(epoch))
        val replacement = assertNotNull(f.gateway.captureActivatedSemanticEpoch())
        assertTrue(f.gateway.isCurrentActivatedSemanticEpoch(replacement))
        next.close()
    }
}
