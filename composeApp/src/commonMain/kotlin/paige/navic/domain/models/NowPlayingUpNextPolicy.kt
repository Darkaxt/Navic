package paige.navic.domain.models

fun <Item> nowPlayingUpNextItems(
	queue: List<Item>,
	currentIndex: Int,
	maxCount: Int
): List<Item> {
	if (maxCount <= 0 || currentIndex !in queue.indices) return emptyList()

	return queue.drop(currentIndex + 1).take(maxCount)
}

fun shouldShowNowPlayingUpNextArtwork(
	showNowPlayingUpNext: Boolean,
	showNowPlayingUpNextArtwork: Boolean
): Boolean = showNowPlayingUpNext && showNowPlayingUpNextArtwork
