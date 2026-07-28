package paige.navic.ui.screens.reader

internal enum class ReaderPagePaginationReadiness {
	Loading,
	Cached,
	Ready,
	Failed
}

internal val ReaderPagePaginationReadiness.isReadyForRasterization: Boolean
	get() = this == ReaderPagePaginationReadiness.Cached ||
		this == ReaderPagePaginationReadiness.Ready

internal fun readerPageActivePaginationReadiness(
	profileAvailable: Boolean,
	readiness: ReaderPagePaginationReadiness
): ReaderPagePaginationReadiness = if (profileAvailable) {
	readiness
} else {
	ReaderPagePaginationReadiness.Loading
}

internal fun readerPagePaginationReadiness(status: String?): ReaderPagePaginationReadiness =
	when (status?.trim()?.lowercase()) {
		"cached" -> ReaderPagePaginationReadiness.Cached
		"ready" -> ReaderPagePaginationReadiness.Ready
		"failed" -> ReaderPagePaginationReadiness.Failed
		else -> ReaderPagePaginationReadiness.Loading
	}

internal data class ReaderPageLayoutSignature(
	val widthPx: Int,
	val heightPx: Int,
	val layoutDirection: Int,
	val rasterProfileEpoch: Long
)

internal class ReaderPageRasterHostEventBridge(
	private val publish: (ReaderPageRasterRetryEvent) -> Unit
) {
	private var contentReadyKey: String? = null
	private var previousLayoutSignature: ReaderPageLayoutSignature? = null
	private var layoutStable = false
	private var paginationReady = false
	private var webViewAttached = false
	private var readerResumed = false

	fun contentReadyKeyChanged(key: String?) {
		if (key == contentReadyKey) return
		contentReadyKey = key
		if (key != null) publish(ReaderPageRasterRetryEvent.ContentReady)
	}

	fun layoutStabilityInvalidated() {
		previousLayoutSignature = null
		layoutStable = false
	}

	fun layoutSignatureMeasured(signature: ReaderPageLayoutSignature) {
		if (signature != previousLayoutSignature) {
			layoutStable = false
		} else if (!layoutStable) {
			layoutStable = true
			publish(ReaderPageRasterRetryEvent.LayoutStable)
		}
		previousLayoutSignature = signature
	}

	fun paginationReadinessChanged(readiness: ReaderPagePaginationReadiness) {
		val ready = readiness.isReadyForRasterization
		if (ready && !paginationReady) {
			publish(ReaderPageRasterRetryEvent.PaginationReady)
		}
		paginationReady = ready
	}

	fun webViewAttachmentChanged(attached: Boolean) {
		if (attached && !webViewAttached) {
			publish(ReaderPageRasterRetryEvent.WebViewAttached)
		}
		webViewAttached = attached
	}

	fun lifecycleResumedChanged(resumed: Boolean) {
		if (resumed && !readerResumed) {
			publish(ReaderPageRasterRetryEvent.ReaderResumed)
		}
		readerResumed = resumed
	}

	fun reset() {
		contentReadyKey = null
		previousLayoutSignature = null
		layoutStable = false
		paginationReady = false
		webViewAttached = false
		readerResumed = false
	}
}

internal class ReaderPageRasterHostEventController(
	onRetryEvent: (ReaderPageRasterRetryEvent) -> Unit,
	private val cancelAllDeferredRetries: () -> Unit,
	private val onWebViewAttachmentChanged: (Boolean) -> Unit = {}
) {
	private val bridge = ReaderPageRasterHostEventBridge(onRetryEvent)

	fun contentReadyKeyChanged(key: String?) = bridge.contentReadyKeyChanged(key)

	fun layoutStabilityInvalidated() = bridge.layoutStabilityInvalidated()

	fun layoutSignatureMeasured(signature: ReaderPageLayoutSignature) =
		bridge.layoutSignatureMeasured(signature)

	fun paginationReadinessChanged(readiness: ReaderPagePaginationReadiness) =
		bridge.paginationReadinessChanged(readiness)

	fun webViewAttachmentChanged(attached: Boolean) {
		bridge.webViewAttachmentChanged(attached)
		onWebViewAttachmentChanged(attached)
	}

	fun lifecycleResumedChanged(resumed: Boolean) =
		bridge.lifecycleResumedChanged(resumed)

	fun close() {
		onWebViewAttachmentChanged(false)
		bridge.reset()
		cancelAllDeferredRetries()
	}
}
