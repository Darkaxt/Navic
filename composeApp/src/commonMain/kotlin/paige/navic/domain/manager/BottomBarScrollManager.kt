package paige.navic.domain.manager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

// TODO: replace this because it's kind of finnicky
class BottomBarScrollManager(val thresholdPx: Float) {
	var isTriggered by mutableStateOf(false)
	private var accumulator = 0f
	private val backToTopHandlers = mutableListOf<() -> Boolean>()

	fun reset() {
		isTriggered = false
		accumulator = 0f
	}

	fun registerBackToTopHandler(handler: () -> Boolean) {
		if (!backToTopHandlers.contains(handler)) {
			backToTopHandlers += handler
		}
	}

	fun unregisterBackToTopHandler(handler: () -> Boolean) {
		backToTopHandlers.remove(handler)
	}

	fun tryHandleBackToTop(): Boolean {
		val handledByScreen = backToTopHandlers.asReversed().any { it() }
		if (handledByScreen) {
			reset()
			return true
		}
		if (isTriggered) {
			reset()
			return true
		}
		return false
	}

	val connection = object : NestedScrollConnection {
		override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
			val delta = available.y
			accumulator += delta

			if (accumulator < -thresholdPx && !isTriggered) {
				isTriggered = true
				accumulator = 0f
			} else if (accumulator > thresholdPx && isTriggered) {
				isTriggered = false
				accumulator = 0f
			}

			if ((delta > 0 && accumulator < 0) || (delta < 0 && accumulator > 0)) {
				accumulator = 0f
			}
			return Offset.Zero
		}
	}
}
