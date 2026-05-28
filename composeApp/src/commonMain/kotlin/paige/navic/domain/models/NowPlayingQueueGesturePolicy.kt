package paige.navic.domain.models

const val NowPlayingControlsOpenQueueSwipeThresholdPx = 64f

fun shouldOpenQueueFromNowPlayingControlsSwipeUp(
	enabled: Boolean,
	hasCurrentSong: Boolean,
	accumulatedVerticalDragPx: Float
): Boolean =
	enabled &&
		hasCurrentSong &&
		accumulatedVerticalDragPx <= -NowPlayingControlsOpenQueueSwipeThresholdPx
