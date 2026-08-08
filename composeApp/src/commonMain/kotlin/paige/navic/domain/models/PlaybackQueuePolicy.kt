package paige.navic.domain.models

import kotlin.random.Random

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
		queueOrder = CollectionShuffleQueueOrder.Shuffled,
		enablePlayerShuffle = true
	)

fun <T> collectionPlaybackOrder(
	items: List<T>,
	shuffleEnabled: Boolean,
	random: Random = Random.Default
): List<T> =
	if (shuffleEnabled && items.size > 1) {
		items.shuffled(random)
	} else {
		items
	}

fun playbackArtworkPrefetchIndexes(
	upcomingIndexes: List<Int>,
	upNextCount: Int
): List<Int> =
	if (upNextCount <= 0) {
		emptyList()
	} else {
		upcomingIndexes.take(upNextCount)
	}
