package paige.navic.domain.models

fun shouldSkipMediaAfterPlaybackError(
	skipMediaOnError: Boolean,
	hasNextMediaItem: Boolean
): Boolean = skipMediaOnError && hasNextMediaItem

fun shouldHandlePlaybackErrorVisibly(
	playWhenReady: Boolean,
	isUiPaused: Boolean,
	hasPendingSourceErrorRecovery: Boolean
): Boolean =
	playWhenReady || !isUiPaused || hasPendingSourceErrorRecovery
