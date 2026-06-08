package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadaloudPositionPulseTest {
	@Test
	fun publishesRepeatedPositionTicksOnlyWhilePlaybackIsActive() {
		var playing = true
		var publishCount = 0
		val scheduler = ManualPulseScheduler()
		val pulse = ReadaloudPositionPulse(
			intervalMs = 250L,
			isPlaying = { playing },
			publishPosition = { publishCount += 1 },
			schedule = scheduler::schedule
		)

		pulse.update()
		assertEquals(1, scheduler.pendingCount)
		assertEquals(250L, scheduler.lastDelayMs)

		pulse.update()
		assertEquals(1, scheduler.pendingCount)

		scheduler.fireNext()
		assertEquals(1, publishCount)
		assertEquals(1, scheduler.pendingCount)

		playing = false
		pulse.update()
		assertEquals(0, scheduler.pendingCount)

		scheduler.fireNext()
		assertEquals(1, publishCount)
		assertEquals(0, scheduler.pendingCount)
	}

	@Test
	fun stopCancelsPendingPulseWithoutPublishing() {
		var publishCount = 0
		val scheduler = ManualPulseScheduler()
		val pulse = ReadaloudPositionPulse(
			isPlaying = { true },
			publishPosition = { publishCount += 1 },
			schedule = scheduler::schedule
		)

		pulse.update()
		pulse.stop()
		scheduler.fireNext()

		assertEquals(0, publishCount)
		assertEquals(0, scheduler.pendingCount)
	}

	private class ManualPulseScheduler {
		private var task: (() -> Unit)? = null
		var lastDelayMs: Long? = null
			private set
		val pendingCount: Int
			get() = if (task == null) 0 else 1

		fun schedule(delayMs: Long, action: () -> Unit): ReadaloudPositionPulseCancellation {
			lastDelayMs = delayMs
			task = action
			return ReadaloudPositionPulseCancellation {
				if (task === action) {
					task = null
				}
			}
		}

		fun fireNext() {
			val pending = task
			task = null
			pending?.invoke()
		}
	}
}
