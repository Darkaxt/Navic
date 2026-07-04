package paige.navic.domain.models

const val NowPlayingRepeatOff = 0
const val NowPlayingRepeatOne = 1
const val NowPlayingRepeatAll = 2

enum class NowPlayingUpNextLayout {
	HorizontalRow,
	VerticalStack
}

fun <Item> nowPlayingUpNextItems(
	queue: List<Item>,
	currentIndex: Int,
	maxCount: Int,
	repeatMode: Int = NowPlayingRepeatOff,
	upcomingIndexes: List<Int> = emptyList()
): List<Item> {
	if (maxCount <= 0 || currentIndex !in queue.indices) return emptyList()

	val indexes = when {
		upcomingIndexes.isNotEmpty() -> upcomingIndexes
		repeatMode == NowPlayingRepeatOne -> listOf(currentIndex)
		repeatMode == NowPlayingRepeatAll -> repeatAllUpcomingIndexes(
			queueSize = queue.size,
			currentIndex = currentIndex,
			maxCount = maxCount
		)
		else -> ((currentIndex + 1)..queue.lastIndex).toList()
	}

	return indexes
		.asSequence()
		.filter { it in queue.indices }
		.take(maxCount)
		.map { queue[it] }
		.toList()
}

fun shouldShowNowPlayingUpNextArtwork(
	showNowPlayingUpNext: Boolean,
	showNowPlayingUpNextArtwork: Boolean
): Boolean = showNowPlayingUpNext && showNowPlayingUpNextArtwork

fun nowPlayingUpNextLayout(wideLandscape: Boolean): NowPlayingUpNextLayout =
	if (wideLandscape) NowPlayingUpNextLayout.VerticalStack else NowPlayingUpNextLayout.HorizontalRow

private fun repeatAllUpcomingIndexes(
	queueSize: Int,
	currentIndex: Int,
	maxCount: Int
): List<Int> {
	if (queueSize <= 1) return listOf(currentIndex)
	val previewCount = minOf(maxCount, queueSize - 1)
	return (1..previewCount).map { offset -> (currentIndex + offset) % queueSize }
}
