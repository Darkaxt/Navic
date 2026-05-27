package paige.navic.domain.models

fun shouldPausePlaybackWhenVolumeZero(
	pausePlaybackOnVolumeZero: Boolean,
	isPlaying: Boolean,
	volume: Int
): Boolean =
	pausePlaybackOnVolumeZero &&
		isPlaying &&
		volume <= 0

fun shouldResumePlaybackAfterVolumeRestored(
	pausePlaybackOnVolumeZero: Boolean,
	pausedByZeroVolume: Boolean,
	volume: Int
): Boolean =
	pausePlaybackOnVolumeZero &&
		pausedByZeroVolume &&
		volume > 0
