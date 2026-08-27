package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPagePreparationPresentation

internal enum class ReaderPageRasterRetryEvent {
	ContentReady,
	LayoutStable,
	PaginationReady,
	WebViewAttached,
	ReaderResumed,
	PassiveHostAvailable,
	CanonicalLiveCommitIssued
}

internal class ReaderPageRasterDeferralPolicy(
	reason: ReaderPageRasterDeferralReason
) {
	val retryEvent: ReaderPageRasterRetryEvent = when (reason) {
		ReaderPageRasterDeferralReason.ContentNotReady ->
			ReaderPageRasterRetryEvent.ContentReady
		ReaderPageRasterDeferralReason.LayoutUnstable ->
			ReaderPageRasterRetryEvent.LayoutStable
		ReaderPageRasterDeferralReason.PaginationNotReady ->
			ReaderPageRasterRetryEvent.PaginationReady
		ReaderPageRasterDeferralReason.WebViewDetached ->
			ReaderPageRasterRetryEvent.WebViewAttached
		ReaderPageRasterDeferralReason.ReaderPaused ->
			ReaderPageRasterRetryEvent.ReaderResumed
		ReaderPageRasterDeferralReason.PassiveHostUnavailable ->
			ReaderPageRasterRetryEvent.PassiveHostAvailable
		ReaderPageRasterDeferralReason.CanonicalLiveCommitUnavailable ->
			ReaderPageRasterRetryEvent.CanonicalLiveCommitIssued
	}

	fun shouldRetry(event: ReaderPageRasterRetryEvent): Boolean = event == retryEvent
}

internal fun readerPageDeferredPresentation(
	hasPreparedDeck: Boolean
): ReaderPagePreparationPresentation = if (hasPreparedDeck) {
	ReaderPagePreparationPresentation.Hidden
} else {
	ReaderPagePreparationPresentation.Cover
}

internal class ReaderPageRasterDeferredRetryCoordinator {
	private data class DeferredRequest(
		val sessionId: Long,
		val reason: ReaderPageRasterDeferralReason,
		val policy: ReaderPageRasterDeferralPolicy,
		val onResumed: (Long) -> Unit,
		val retry: () -> Unit,
		val cancel: () -> Unit
	)

	private val eventVersions = LongArray(ReaderPageRasterRetryEvent.entries.size)
	private var deferred: DeferredRequest? = null

	fun observeVersion(reason: ReaderPageRasterDeferralReason): Long {
		val event = ReaderPageRasterDeferralPolicy(reason).retryEvent
		return eventVersions[event.ordinal]
	}

	fun defer(
		sessionId: Long,
		reason: ReaderPageRasterDeferralReason,
		observedVersion: Long,
		onResumed: (Long) -> Unit = {},
		retry: () -> Unit,
		cancel: () -> Unit
	) {
		val previous = deferred
		deferred = null
		previous?.cancel?.invoke()

		val policy = ReaderPageRasterDeferralPolicy(reason)
		val currentVersion = eventVersions[policy.retryEvent.ordinal]
		if (currentVersion > observedVersion) {
			onResumed(currentVersion)
			retry()
			return
		}
		deferred = DeferredRequest(
			sessionId = sessionId,
			reason = reason,
			policy = policy,
			onResumed = onResumed,
			retry = retry,
			cancel = cancel
		)
	}

	fun onRetryEvent(event: ReaderPageRasterRetryEvent): Boolean {
		val eventIndex = event.ordinal
		eventVersions[eventIndex] = Math.incrementExact(eventVersions[eventIndex])
		val current = deferred ?: return false
		if (!current.policy.shouldRetry(event)) return false
		deferred = null
		current.onResumed(eventVersions[eventIndex])
		current.retry()
		return true
	}

	fun hasDeferred(reason: ReaderPageRasterDeferralReason): Boolean =
		deferred?.reason == reason

	fun cancel(sessionId: Long): Boolean {
		val current = deferred ?: return false
		if (current.sessionId != sessionId) return false
		deferred = null
		current.cancel()
		return true
	}

	fun cancelAll() {
		val current = deferred ?: return
		deferred = null
		current.cancel()
	}

	fun deferredCount(): Int = if (deferred == null) 0 else 1
}
