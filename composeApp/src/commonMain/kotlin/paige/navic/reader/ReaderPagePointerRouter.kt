package paige.navic.reader

data class ReaderPagePointerBeginResult(
	val gestureId: Long,
	val route: ReaderPagePointerRoute
)

sealed interface ReaderPagePointerRoute {
	data object Content : ReaderPagePointerRoute
	data class ContentTerminal(
		val gestureId: Long,
		val outcome: ReaderPageGestureTerminalOutcome
	) : ReaderPagePointerRoute
	data object Consume : ReaderPagePointerRoute
	data class ClaimCurl(val gestureId: Long) : ReaderPagePointerRoute
	data class Curl(val gestureId: Long) : ReaderPagePointerRoute
	data class Terminal(
		val gestureId: Long,
		val outcome: ReaderPageGestureTerminalOutcome
	) : ReaderPagePointerRoute
	data object Ignore : ReaderPagePointerRoute
}

class ReaderPagePointerRouter(
	private val lifecycle: ReaderPageGestureLifecycle,
	private val onStarted: (
		gestureId: Long,
		downX: Float,
		downY: Float
	) -> Unit = { _, _, _ -> },
	private val publishTerminal: (
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	) -> Unit = { _, _ -> }
) {
	private val sequences = linkedMapOf<Long, ReaderPagePointerSequence>()
	private var activeGestureId: Long? = null
	private var consumedPointerStreamId: Long? = null
	private var curlClaimed = false

	fun begin(
		downX: Float,
		downY: Float,
		admission: ReaderPageNewPointerDecision
	): ReaderPagePointerBeginResult {
		check(activeGestureId == null && consumedPointerStreamId == null) {
			"A pointer sequence is already active"
		}
		val gestureId = lifecycle.beginGesture()
		sequences[gestureId] = ReaderPagePointerSequence(gestureId, downX, downY)
		activeGestureId = gestureId
		curlClaimed = false
		onStarted(gestureId, downX, downY)
		return when (admission) {
			ReaderPageNewPointerDecision.Accept ->
				ReaderPagePointerBeginResult(gestureId, ReaderPagePointerRoute.Content)
			is ReaderPageNewPointerDecision.Reject -> {
				check(complete(gestureId, admission.outcome))
				consumedPointerStreamId = gestureId
				ReaderPagePointerBeginResult(
					gestureId,
					ReaderPagePointerRoute.Terminal(gestureId, admission.outcome)
				)
			}
		}
	}

	fun move(x: Float, y: Float, touchSlop: Float): ReaderPagePointerRoute {
		if (consumedPointerStreamId != null) return ReaderPagePointerRoute.Consume
		val sequence = activeGestureId?.let(sequences::get)
			?: return ReaderPagePointerRoute.Ignore
		return when (sequence.moveTo(x, y, touchSlop)) {
			ReaderPagePointerOwnership.Pending,
			ReaderPagePointerOwnership.Content -> ReaderPagePointerRoute.Content
			ReaderPagePointerOwnership.Curl -> if (curlClaimed) {
				ReaderPagePointerRoute.Curl(sequence.gestureId)
			} else {
				curlClaimed = true
				ReaderPagePointerRoute.ClaimCurl(sequence.gestureId)
			}
			ReaderPagePointerOwnership.Terminal -> ReaderPagePointerRoute.Ignore
		}
	}

	fun claimContentAction(gestureId: Long): ReaderPagePointerRoute {
		val sequence = sequences[gestureId]
			?.takeIf { activeGestureId == gestureId }
			?: return ReaderPagePointerRoute.Ignore
		return if (sequence.claimContentAction()) {
			ReaderPagePointerRoute.Content
		} else {
			ReaderPagePointerRoute.Ignore
		}
	}

	fun pointerUp(gestureId: Long): ReaderPagePointerRoute {
		if (consumedPointerStreamId == gestureId) {
			consumedPointerStreamId = null
			return ReaderPagePointerRoute.Consume
		}
		val sequence = sequences[gestureId]
			?.takeIf { activeGestureId == gestureId }
			?: return ReaderPagePointerRoute.Ignore
		val resolvedContentOutcome = sequence.resolvedContentOutcome
		val route = when {
			sequence.ownership == ReaderPagePointerOwnership.Curl ->
				ReaderPagePointerRoute.Curl(gestureId)
			resolvedContentOutcome != null ->
				ReaderPagePointerRoute.ContentTerminal(gestureId, resolvedContentOutcome)
			sequence.ownership == ReaderPagePointerOwnership.Pending ->
				ReaderPagePointerRoute.Content
			else -> ReaderPagePointerRoute.ContentTerminal(
				gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser
			)
		}
		sequence.pointerUp()
		activeGestureId = null
		curlClaimed = false
		return route
	}

	fun isDelayedTapPending(gestureId: Long): Boolean =
		sequences[gestureId]?.pendingTapGestureId == gestureId

	fun completeDelayedTap(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean {
		require(
			outcome == ReaderPageGestureTerminalOutcome.CommittedForward ||
				outcome == ReaderPageGestureTerminalOutcome.CommittedBackward ||
				outcome == ReaderPageGestureTerminalOutcome.CompletedTapAction
		) { "Unsupported delayed tap outcome: $outcome" }
		sequences[gestureId]
			?.takeIf { it.pendingTapGestureId == gestureId }
			?: return false
		return complete(gestureId, outcome)
	}

	fun cancel(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean = complete(gestureId, outcome)

	fun interruptPhysicalStream(
		gestureId: Long,
		finalStreamEvent: Boolean
	): ReaderPagePointerRoute {
		if (consumedPointerStreamId == gestureId) {
			if (finalStreamEvent) consumedPointerStreamId = null
			return ReaderPagePointerRoute.Consume
		}
		val outcome = sequences[gestureId]
			?.resolvedContentOutcome
			?: ReaderPageGestureTerminalOutcome.CancelledByUser
		val completed = complete(gestureId, outcome)
		if (finalStreamEvent) {
			if (consumedPointerStreamId == gestureId) {
				consumedPointerStreamId = null
			}
		} else {
			consumedPointerStreamId = gestureId
		}
		return if (completed) {
			ReaderPagePointerRoute.Terminal(gestureId, outcome)
		} else {
			ReaderPagePointerRoute.Consume
		}
	}

	fun secondaryPointerUp(gestureId: Long): ReaderPagePointerRoute =
		if (consumedPointerStreamId == gestureId) {
			ReaderPagePointerRoute.Consume
		} else {
			ReaderPagePointerRoute.Ignore
		}

	fun cancelAll(outcome: ReaderPageGestureTerminalOutcome): List<Long> {
		val physicalGestureId = activeGestureId
		val cancelled = sequences.keys.toList().filter { gestureId ->
			complete(gestureId, outcome)
		}
		if (physicalGestureId != null && physicalGestureId in cancelled) {
			consumedPointerStreamId = physicalGestureId
		}
		return cancelled
	}

	fun abandonPhysicalPointerStream() {
		check(activeGestureId == null) {
			"An active pointer sequence must be terminal before abandonment"
		}
		consumedPointerStreamId = null
	}

	fun trackedSequenceCount(): Int = sequences.size

	fun complete(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean {
		val sequence = sequences[gestureId] ?: return false
		if (!lifecycle.completeGesture(gestureId, outcome)) return false
		check(sequence.complete(outcome)) {
			"Pointer sequence completed before lifecycle gate"
		}
		sequences.remove(gestureId)
		if (activeGestureId == gestureId) {
			activeGestureId = null
			consumedPointerStreamId = gestureId
			curlClaimed = false
		}
		publishTerminal(gestureId, outcome)
		return true
	}
}
