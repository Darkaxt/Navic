package paige.navic.reader

enum class ReaderPageGestureTerminalOutcome {
	CommittedForward,
	CommittedBackward,
	CompletedTapAction,
	CancelledByUser,
	CancelledLifecycle,
	RejectedPreparing,
	RejectedSettling,
	RejectedDirection,
	RejectedBoundary,
	RejectedRendererUnavailable,
	FailedRenderer,
	FailedRecovery
}

internal const val ReaderRendererBusyFeedbackMinimumMillis = 2_000L
internal const val ReaderRendererBusyFeedbackMaximumMillis = 4_000L

internal fun readerPageGestureShouldShowBusyFeedback(
	outcome: ReaderPageGestureTerminalOutcome
): Boolean = when (outcome) {
	ReaderPageGestureTerminalOutcome.RejectedPreparing,
	ReaderPageGestureTerminalOutcome.RejectedSettling,
	ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable -> true
	else -> false
}

internal fun readerRendererBusyFeedbackCanStartMinimumTimer(
	activeRejectionToken: Long,
	fullyVisibleRejectionToken: Long
): Boolean =
	activeRejectionToken > 0L && activeRejectionToken == fullyVisibleRejectionToken

internal fun readerRendererBusyFeedbackReadyDelayMillis(
	elapsedSinceMostRecentRejectionMillis: Long
): Long = (
	ReaderRendererBusyFeedbackMinimumMillis -
		elapsedSinceMostRecentRejectionMillis.coerceAtLeast(0L)
	).coerceAtLeast(0L)

/**
 * Assigns pointer-sequence identities and enforces one terminal result per sequence.
 *
 * The lifecycle is owned and called by the reader's main-thread input boundary.
 */
class ReaderPageGestureLifecycle(
	private val historyLimit: Int = 128
) {
	private var nextGestureId = 1L
	private val activeGestures = linkedSetOf<Long>()
	private val terminalOutcomes = linkedMapOf<Long, ReaderPageGestureTerminalOutcome>()

	init {
		require(historyLimit > 0) { "historyLimit must be positive" }
	}

	fun beginGesture(): Long = nextGestureId++.also(activeGestures::add)

	fun completeGesture(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean {
		if (!activeGestures.remove(gestureId)) return false
		terminalOutcomes[gestureId] = outcome
		while (terminalOutcomes.size > historyLimit) {
			val oldest = terminalOutcomes.keys.first()
			terminalOutcomes.remove(oldest)
		}
		return true
	}

	fun isActive(gestureId: Long): Boolean = gestureId in activeGestures

	fun terminalOutcome(gestureId: Long): ReaderPageGestureTerminalOutcome? =
		terminalOutcomes[gestureId]
}
