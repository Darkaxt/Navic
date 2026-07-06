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

fun shouldReplayLastPlayable(
	hasLastPlayable: Boolean,
	hasPlayableUpcoming: Boolean,
	hasDeferredDownloads: Boolean
): Boolean =
	hasLastPlayable &&
		!hasPlayableUpcoming &&
		hasDeferredDownloads

fun recoveredDownloadTargetIndex(
	currentIndex: Int,
	queueSize: Int
): Int =
	(currentIndex + 1).coerceIn(0, queueSize)
