package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingQueueGesturePolicyTest {
	@Test
	fun swipeUpQueueGestureDefaultsToInactive() {
		assertFalse(
			shouldOpenQueueFromNowPlayingControlsSwipeUp(
				enabled = false,
				hasCurrentSong = true,
				accumulatedVerticalDragPx = -NowPlayingControlsOpenQueueSwipeThresholdPx
			)
		)
	}

	@Test
	fun swipeUpQueueGestureRequiresCurrentSong() {
		assertFalse(
			shouldOpenQueueFromNowPlayingControlsSwipeUp(
				enabled = true,
				hasCurrentSong = false,
				accumulatedVerticalDragPx = -NowPlayingControlsOpenQueueSwipeThresholdPx
			)
		)
	}

	@Test
	fun swipeUpQueueGestureRequiresThreshold() {
		assertFalse(
			shouldOpenQueueFromNowPlayingControlsSwipeUp(
				enabled = true,
				hasCurrentSong = true,
				accumulatedVerticalDragPx = -NowPlayingControlsOpenQueueSwipeThresholdPx + 1f
			)
		)
	}

	@Test
	fun swipeUpQueueGestureOpensQueueAtThreshold() {
		assertTrue(
			shouldOpenQueueFromNowPlayingControlsSwipeUp(
				enabled = true,
				hasCurrentSong = true,
				accumulatedVerticalDragPx = -NowPlayingControlsOpenQueueSwipeThresholdPx
			)
		)
	}
}
