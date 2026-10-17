package paige.navic.ui.screens.reader

internal enum class ReaderWebViewDiagnosticSource { Engine, PassiveRaster }
internal enum class ReaderWebViewDiagnosticPhase {
	RuntimeLoad, Initialization, Ready, Query, Commit, PixelCopy, Retirement, Cleanup
}
internal enum class ReaderWebViewDiagnosticState { Started, Completed, Rejected, Failed, Cancelled, Retired }
internal enum class ReaderWebViewDiagnosticReason {
	None, RendererGone, RuntimeReadyPollBudget, CommitPollBudget, WebViewError, HttpError,
	Cancelled, CleanupThrew, PixelCopyResult, PixelCopySubmissionThrew,
	CommitUnavailable, RuntimeLoadThrew, JavascriptSubmissionThrew, OperationIdInvalid, OperationFailed,
	ReceiptRejected, Destroyed, Paused, NotReady, GeometryMismatch, UnsupportedPlatform,
	StaleVisualState, ViewSizeMismatch, WindowBounds, BitmapAllocation, QueryRejected,
	QueryFailed, RuntimeRetired, PublicationReplaced, Closed, SubmissionRejected, SubmissionThrew
}

/** Fixed categories and primitive platform results only: never payloads, identities or exceptions. */
internal object ReaderWebViewPhaseDiagnostic {
	fun format(
		source: ReaderWebViewDiagnosticSource,
		phase: ReaderWebViewDiagnosticPhase,
		state: ReaderWebViewDiagnosticState,
		reason: ReaderWebViewDiagnosticReason,
		elapsedMs: Long,
		code: Int? = null,
		mainFrame: Boolean? = null,
		didCrash: Boolean? = null,
		priorityAtExit: Int? = null
	): String = buildString {
		append("reader-webview-phase source=").append(source.name)
		append(" phase=").append(phase.name)
		append(" state=").append(state.name)
		append(" reason=").append(reason.name)
		append(" elapsedMs=").append(elapsedMs.coerceAtLeast(0L))
		if (code != null) append(" code=").append(code)
		if (mainFrame != null) append(" mainFrame=").append(mainFrame)
		if (didCrash != null) append(" didCrash=").append(didCrash)
		if (priorityAtExit != null) append(" priorityAtExit=").append(priorityAtExit)
	}
}
