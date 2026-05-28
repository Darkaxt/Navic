package paige.navic.domain.models

const val NowPlayingArtworkRotationDurationMs = 8000

fun shouldRotateNowPlayingArtwork(
	enabled: Boolean,
	isPaused: Boolean,
	isActiveArtwork: Boolean,
	hasCoverArt: Boolean
): Boolean = enabled && !isPaused && isActiveArtwork && hasCoverArt
