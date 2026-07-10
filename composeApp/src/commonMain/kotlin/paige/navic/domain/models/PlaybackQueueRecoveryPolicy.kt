package paige.navic.domain.models

fun firstPlayableUpcomingIndex(
	currentIndex: Int,
	queueSongIds: List<String>,
	availableSongIds: Set<String>
): Int? =
	queueSongIds
		.asSequence()
		.drop(currentIndex + 1)
		.withIndex()
		.firstOrNull { (_, songId) -> songId in availableSongIds }
		?.let { (offset, _) -> currentIndex + 1 + offset }

fun playbackFailureTargetIndex(
	skipMediaOnError: Boolean,
	nextPlayableIndex: Int?
): Int? =
	nextPlayableIndex?.takeIf {
		shouldSkipMediaAfterPlaybackError(
			skipMediaOnError = skipMediaOnError,
			hasNextMediaItem = true
		)
	}
