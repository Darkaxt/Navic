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
	fun artworkPrefetchUsesVisibleUpNextWindowOnly() {
		assertEquals(
			listOf(2, 3, 4),
			playbackArtworkPrefetchIndexes(
				upcomingIndexes = listOf(2, 3, 4, 5),
				upNextCount = 3
			)
		)
	}

	@Test
	fun artworkPrefetchIgnoresDisabledOrNegativeWindows() {
		assertEquals(
			emptyList(),
			playbackArtworkPrefetchIndexes(
				upcomingIndexes = listOf(2, 3),
				upNextCount = 0
			)
		)
		assertEquals(
			emptyList(),
			playbackArtworkPrefetchIndexes(
				upcomingIndexes = listOf(2, 3),
				upNextCount = -1
			)
		)
	}
}
