package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.random.Random

class PlaybackQueuePolicyTest {
	@Test
	fun collectionShuffleGeneratesShuffledQueueAndEnablesPlayerShuffle() {
		assertEquals(
			CollectionShuffleQueueOrder.Shuffled,
			collectionShufflePlaybackPlan().queueOrder
		)
		assertEquals(
			true,
			collectionShufflePlaybackPlan().enablePlayerShuffle
		)
	}

	@Test
	fun orderedCollectionGenerationPreservesSourceIndexZero() {
		val songs = listOf("first", "second", "third")

		assertEquals(
			songs,
			collectionPlaybackOrder(
				items = songs,
				shuffleEnabled = false,
				random = ZeroRandom
			)
		)
	}

	@Test
	fun shuffledCollectionGenerationChangesFirstSongWithoutLosingEntries() {
		val songs = listOf("first", "second", "third")

		val generated = collectionPlaybackOrder(
			items = songs,
			shuffleEnabled = true,
			random = ZeroRandom
		)

		assertNotEquals("first", generated.first())
		assertEquals(songs.toSet(), generated.toSet())
		assertEquals(songs.size, generated.size)
	}

	@Test
	fun shuffledCollectionGenerationHandlesEmptyAndSingleItemLists() {
		assertEquals(
			emptyList(),
			collectionPlaybackOrder(emptyList<String>(), shuffleEnabled = true, random = ZeroRandom)
		)
		assertEquals(
			listOf("only"),
			collectionPlaybackOrder(listOf("only"), shuffleEnabled = true, random = ZeroRandom)
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

	private object ZeroRandom : Random() {
		override fun nextBits(bitCount: Int): Int = 0
	}
}
