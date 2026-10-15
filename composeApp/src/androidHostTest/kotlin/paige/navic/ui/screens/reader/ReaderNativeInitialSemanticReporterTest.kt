package paige.navic.ui.screens.reader

import kotlin.test.*
import paige.navic.reader.*

/** Real reporter/model authority only; not Foliate/layout or Native activation proof. */
class ReaderNativeInitialSemanticReporterTest {
    @Test fun genuineControllerOpenVersionCanBeCapturedWithoutBoundNativePredecessor() {
        val opened = opened()
        val reporter = reporter(opened)
        assertNull(reporter.lastReportedBinding)
        assertEquals(opened.presentationVersion, capture(reporter))
        assertNull(reporter.lastReportedBinding)
    }
    @Test fun inheritedBindingAndSeedCannotBeRelabelledInitial() {
        val opened = opened()
        val reporter = reporter(opened)
        val binding = ReaderPresentationBinding("public-synthetic-bound", 1L, 1L, 1L,
            ReaderDestinationCommitIdentity("public-synthetic-bound", 1L))
        assertTrue(reporter.bindPublication(binding))
        assertNull(capture(reporter))
    }
    @Test fun backgroundAuthorityCannotSupplyInitialVersion() {
        val opened = opened()
        val reporter = ReaderPresentationBindingReporter()
        reporter.reset(opened.state.readerSessionGeneration, opened.presentationVersion,
            opened.state.presentation.copy(lifecycle = ReaderPresentationLifecycleState.Background))
        reporter.consumeComposeModel(opened.presentationVersion,
            opened.state.presentation.copy(lifecycle = ReaderPresentationLifecycleState.Background), false)
        assertNull(capture(reporter))
    }
    @Test fun reporterWithoutActualComposeAuthorityCannotCaptureMinimumAsInitialVersion() {
        val opened = opened()
        val reporter = ReaderPresentationBindingReporter()
        reporter.reset(opened.state.readerSessionGeneration, opened.presentationVersion, opened.state.presentation)
        assertNull(capture(reporter))
    }
    @Test fun obsoleteComposeVersionCannotReopenCapturedInitialContextAfterReporterReset() {
        val first = opened()
        val reporter = reporter(first)
        val oldEpoch = reporter.captureEpoch()
        val next = first.open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-next-reporter", resourceHref = "next.epub"),
            url = "https://example.invalid/next.epub")).controller
        reporter.reset(next.state.readerSessionGeneration, next.presentationVersion, next.state.presentation)
        assertTrue(reporter.captureEpoch() != oldEpoch)
        assertFalse(reporter.consumeComposeModel(first.presentationVersion, first.state.presentation, false))
        assertNull(capture(reporter))
        assertTrue(reporter.consumeComposeModel(next.presentationVersion, next.state.presentation, false))
        assertEquals(next.presentationVersion, capture(reporter))
    }
    private fun opened(): ReaderController = ReaderController().open(ReaderEngineOpenRequest(
        publication = ReaderPublicationIdentity("public-synthetic-initial-reporter", resourceHref = "fixture.epub"),
        url = "https://example.invalid/fixture.epub")).controller
    private fun reporter(opened: ReaderController): ReaderPresentationBindingReporter = ReaderPresentationBindingReporter().also {
        it.reset(opened.state.readerSessionGeneration, opened.presentationVersion, opened.state.presentation)
        assertTrue(it.consumeComposeModel(opened.presentationVersion, opened.state.presentation, false))
    }
    private fun capture(reporter: ReaderPresentationBindingReporter): ReaderPresentationReceiptVersion? {
        val method = reporter.javaClass.declaredMethods.singleOrNull { it.name == "captureInitialSemanticContextVersionOrNull" }
        assertNotNull(method, "The real Native reporter must capture an unbound initial version separately from bound confirmation")
        return method.apply { isAccessible = true }.invoke(reporter) as? ReaderPresentationReceiptVersion
    }
}
