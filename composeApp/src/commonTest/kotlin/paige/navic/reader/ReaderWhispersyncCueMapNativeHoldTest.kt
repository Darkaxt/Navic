package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWhispersyncCueMapNativeHoldTest {
	@Test
	fun oneSecondHoldCompletesOnceAndEarlyOrMovedPointersCancel() {
		val tracker = ReaderWhispersyncCueMapHoldTracker(
			holdDurationMs = 1_000L,
			touchSlopPx = 10f
		)

		assertTrue(tracker.begin(sourceOrdinal = 7, x = 40f, y = 60f, nowMillis = 100L))
		assertEquals(0.5f, tracker.progress(nowMillis = 600L))
		assertEquals(
			ReaderWhispersyncCueMapHoldOutcome.CancelledMovement,
			tracker.move(x = 51f, y = 60f)
		)
		assertFalse(tracker.active)

		assertTrue(tracker.begin(sourceOrdinal = 8, x = 40f, y = 60f, nowMillis = 1_000L))
		assertEquals(
			ReaderWhispersyncCueMapHoldOutcome.CancelledEarlyRelease,
			tracker.release(nowMillis = 1_999L)
		)
		assertFalse(tracker.active)

		assertTrue(tracker.begin(sourceOrdinal = 9, x = 40f, y = 60f, nowMillis = 3_000L))
		assertNull(tracker.advance(nowMillis = 3_999L))
		assertEquals(ReaderWhispersyncCueMapHoldOutcome.Completed, tracker.advance(nowMillis = 4_000L))
		assertNull(tracker.advance(nowMillis = 4_500L))
		assertEquals(9, tracker.sourceOrdinal)
		assertNull(tracker.release(nowMillis = 4_500L))
		assertFalse(tracker.active)
	}
}
