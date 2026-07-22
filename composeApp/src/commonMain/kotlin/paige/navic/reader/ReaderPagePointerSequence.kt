package paige.navic.reader

import kotlin.math.abs

enum class ReaderPagePointerOwnership { Pending, Curl, Content, Terminal }

class ReaderPagePointerSequence(
	val gestureId: Long,
	val downX: Float,
	val downY: Float
) {
	var ownership: ReaderPagePointerOwnership = ReaderPagePointerOwnership.Pending
		private set
	var pendingTapGestureId: Long? = null
		private set
	var resolvedContentOutcome: ReaderPageGestureTerminalOutcome? = null
		private set
	private var terminalOutcome: ReaderPageGestureTerminalOutcome? = null

	fun moveTo(x: Float, y: Float, touchSlop: Float): ReaderPagePointerOwnership {
		if (ownership != ReaderPagePointerOwnership.Pending) return ownership
		val dx = x - downX
		val dy = y - downY
		if (abs(dx) <= touchSlop && abs(dy) <= touchSlop) return ownership
		ownership = if (abs(dx) > abs(dy)) {
			ReaderPagePointerOwnership.Curl
		} else {
			ReaderPagePointerOwnership.Content
		}
		return ownership
	}

	fun claimContentAction(): Boolean {
		if (
			ownership != ReaderPagePointerOwnership.Pending &&
			ownership != ReaderPagePointerOwnership.Content
		) return false
		ownership = ReaderPagePointerOwnership.Content
		resolvedContentOutcome = ReaderPageGestureTerminalOutcome.CompletedTapAction
		return true
	}

	fun pointerUp() {
		if (
			ownership == ReaderPagePointerOwnership.Pending &&
			resolvedContentOutcome == null
		) {
			pendingTapGestureId = gestureId
		} else if (ownership != ReaderPagePointerOwnership.Terminal) {
			ownership = ReaderPagePointerOwnership.Terminal
		}
	}

	fun complete(outcome: ReaderPageGestureTerminalOutcome): Boolean {
		if (terminalOutcome != null) return false
		terminalOutcome = outcome
		pendingTapGestureId = null
		resolvedContentOutcome = null
		ownership = ReaderPagePointerOwnership.Terminal
		return true
	}
}
