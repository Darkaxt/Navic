package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQueuePolicyTest {
	@Test
	fun collectionShuffleUsesCanonicalQueueAndPlayerShuffleState() {
		assertEquals(
			CollectionShuffleQueueOrder.Canonical,
			collectionShufflePlaybackPlan().queueOrder
		)
		assertEquals(
			true,
			collectionShufflePlaybackPlan().enablePlayerShuffle
		)
	}

	@Test
	fun playbackPrefetchUsesVisibleUpNextWindowOnly() {
		assertEquals(
			listOf(2, 3, 4),
			playbackPrefetchIndexes(
				upcomingIndexes = listOf(2, 3, 4, 5),
				upNextCount = 3
			)
		)
	}

	@Test
	fun playbackPrefetchIgnoresDisabledOrNegativeWindows() {
		assertEquals(
			emptyList(),
			playbackPrefetchIndexes(
				upcomingIndexes = listOf(2, 3),
				upNextCount = 0
			)
		)
		assertEquals(
			emptyList(),
			playbackPrefetchIndexes(
				upcomingIndexes = listOf(2, 3),
				upNextCount = -1
			)
		)
	}
}
