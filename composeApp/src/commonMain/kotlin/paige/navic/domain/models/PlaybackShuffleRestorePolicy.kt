package paige.navic.domain.models

fun restoredShuffleOrder(
	itemCount: Int,
	currentIndex: Int,
	upcomingIndexes: List<Int>,
	shuffleOrder: List<Int>? = null
): List<Int>? {
	if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null
	if (shuffleOrder != null) {
		return shuffleOrder.takeIf {
			it.size == itemCount && it.toSet() == (0 until itemCount).toSet()
		}
	}
	if (upcomingIndexes.any { it !in 0 until itemCount || it == currentIndex }) return null
	if (upcomingIndexes.distinct().size != upcomingIndexes.size) return null

	val reserved = upcomingIndexes.toSet() + currentIndex
	val previouslyPlayed = (0 until itemCount).filterNot(reserved::contains)
	return previouslyPlayed + currentIndex + upcomingIndexes
}
