package paige.navic.domain.models

enum class CollectionShuffleQueueOrder {
	Canonical,
	Shuffled
}

data class CollectionShufflePlaybackPlan(
	val queueOrder: CollectionShuffleQueueOrder,
	val enablePlayerShuffle: Boolean
)

fun collectionShufflePlaybackPlan(): CollectionShufflePlaybackPlan =
	CollectionShufflePlaybackPlan(
		queueOrder = CollectionShuffleQueueOrder.Canonical,
		enablePlayerShuffle = true
	)

fun playbackArtworkPrefetchIndexes(
	upcomingIndexes: List<Int>,
	upNextCount: Int
): List<Int> =
	if (upNextCount <= 0) {
		emptyList()
	} else {
		upcomingIndexes.take(upNextCount)
	}
