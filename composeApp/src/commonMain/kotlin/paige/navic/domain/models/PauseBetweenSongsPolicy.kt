package paige.navic.domain.models

fun shouldPauseBetweenSongsAfterTransition(
	pauseBetweenSongsSeconds: Int,
	isAutomaticTransition: Boolean,
	isPlaying: Boolean,
	hasMediaItem: Boolean
): Boolean =
	pauseBetweenSongsSeconds > 0 &&
		isAutomaticTransition &&
		isPlaying &&
		hasMediaItem

fun pauseBetweenSongsDelayMs(pauseBetweenSongsSeconds: Int): Long =
	pauseBetweenSongsSeconds.coerceAtLeast(0) * 1000L
