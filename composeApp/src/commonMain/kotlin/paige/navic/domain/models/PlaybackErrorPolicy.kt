package paige.navic.domain.models

fun shouldSkipMediaAfterPlaybackError(
	skipMediaOnError: Boolean,
	hasNextMediaItem: Boolean
): Boolean = skipMediaOnError && hasNextMediaItem
