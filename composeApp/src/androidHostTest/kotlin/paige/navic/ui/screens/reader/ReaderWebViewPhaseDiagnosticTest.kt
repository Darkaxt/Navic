package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ReaderWebViewPhaseDiagnosticTest {
	@Test
	fun phaseMessageContainsOnlyFixedCategoriesAndNonnegativeElapsedTime() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Commit state=Completed " +
				"reason=None elapsedMs=37",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.Commit,
				ReaderWebViewDiagnosticState.Completed,
				ReaderWebViewDiagnosticReason.None,
				37L
			)
		)
		assertEquals(
			"reader-webview-phase source=Engine phase=RuntimeLoad state=Started " +
				"reason=None elapsedMs=0",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.Engine,
				ReaderWebViewDiagnosticPhase.RuntimeLoad,
				ReaderWebViewDiagnosticState.Started,
				ReaderWebViewDiagnosticReason.None,
				-5L
			)
		)
	}

	@Test
	fun genuineRendererLossPreservesOnlyPlatformCrashAndPriorityPrimitives() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Retirement state=Retired " +
				"reason=RendererGone elapsedMs=12 didCrash=true priorityAtExit=1",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.Retirement,
				ReaderWebViewDiagnosticState.Retired,
				ReaderWebViewDiagnosticReason.RendererGone,
				12L,
				didCrash = true,
				priorityAtExit = 1
			)
		)
	}

	@Test
	fun pollExhaustionDoesNotClaimRendererCrashOrShareItsReason() {
		val readyPoll = retirement(ReaderWebViewDiagnosticReason.RuntimeReadyPollBudget)
		val commitPoll = retirement(ReaderWebViewDiagnosticReason.CommitPollBudget)
		val rendererGone = retirement(ReaderWebViewDiagnosticReason.RendererGone)
		assertNotEquals(readyPoll, commitPoll)
		assertNotEquals(readyPoll, rendererGone)
		assertFalse(readyPoll.contains("didCrash="))
		assertFalse(commitPoll.contains("priorityAtExit="))
	}

	@Test
	fun numericWebErrorsNeverRequireUrlDescriptionOrExceptionInputs() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=RuntimeLoad state=Failed " +
				"reason=WebViewError elapsedMs=9 code=-2 mainFrame=true",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.RuntimeLoad,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.WebViewError,
				9L,
				code = -2,
				mainFrame = true
			)
		)
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=RuntimeLoad state=Failed " +
				"reason=HttpError elapsedMs=10 code=503 mainFrame=false",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.RuntimeLoad,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.HttpError,
				10L,
				code = 503,
				mainFrame = false
			)
		)
	}

	@Test
	fun commitCancellationAndCleanupFailureRemainDifferentFromSuccess() {
		val cancelled = ReaderWebViewPhaseDiagnostic.format(
			ReaderWebViewDiagnosticSource.PassiveRaster,
			ReaderWebViewDiagnosticPhase.Commit,
			ReaderWebViewDiagnosticState.Cancelled,
			ReaderWebViewDiagnosticReason.Cancelled,
			25L
		)
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Commit state=Cancelled " +
				"reason=Cancelled elapsedMs=25",
			cancelled
		)
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Cleanup state=Failed " +
				"reason=CleanupThrew elapsedMs=3",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.Cleanup,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.CleanupThrew,
				3L
			)
		)
		assertFalse(cancelled.contains("state=Completed"))
	}

	@Test
	fun pixelCopyCompletionReportsActualNumericResultAndExistingLatency() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=PixelCopy state=Failed " +
				"reason=PixelCopyResult elapsedMs=18 code=3",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.PixelCopy,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.PixelCopyResult,
				18L,
				code = 3
			)
		)
	}

	private fun retirement(reason: ReaderWebViewDiagnosticReason): String =
		ReaderWebViewPhaseDiagnostic.format(
			ReaderWebViewDiagnosticSource.PassiveRaster,
			ReaderWebViewDiagnosticPhase.Retirement,
			ReaderWebViewDiagnosticState.Retired,
			reason,
			48L
		)
}
