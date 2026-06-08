package paige.navic.reader

fun interface ReadaloudPositionPulseCancellation {
	fun cancel()
}

internal class ReadaloudPositionPulse(
	private val intervalMs: Long = DEFAULT_INTERVAL_MS,
	private val isPlaying: () -> Boolean,
	private val publishPosition: () -> Unit,
	private val schedule: (delayMs: Long, action: () -> Unit) -> ReadaloudPositionPulseCancellation
) {
	private var pending: ReadaloudPositionPulseCancellation? = null

	fun update() {
		if (isPlaying()) {
			start()
		} else {
			stop()
		}
	}

	fun stop() {
		pending?.cancel()
		pending = null
	}

	private fun start() {
		if (pending != null) return
		scheduleNext()
	}

	private fun scheduleNext() {
		pending = schedule(intervalMs) {
			pending = null
			if (isPlaying()) {
				publishPosition()
				scheduleNext()
			}
		}
	}

	companion object {
		const val DEFAULT_INTERVAL_MS: Long = 500L
	}
}
