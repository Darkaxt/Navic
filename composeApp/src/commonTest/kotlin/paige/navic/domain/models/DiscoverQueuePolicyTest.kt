package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverQueuePolicyTest {
	@Test
	fun discoverQueueRemovesOnlyUpcomingKnownSongs() {
		assertEquals(
			listOf(2, 4),
			discoverQueueRemovalIndexes(
				queueSongIds = listOf("previous", "current", "starred", "unknown", "playlist"),
				currentIndex = 1,
				knownSongIds = setOf("previous", "starred", "playlist")
			)
		)
	}

	@Test
	fun discoverQueueKeepsCurrentHistoryAndRadioItems() {
		assertEquals(
			emptyList(),
			discoverQueueRemovalIndexes(
				queueSongIds = listOf("known-history", "known-current", "radio_live", "unknown"),
				currentIndex = 1,
				knownSongIds = setOf("known-history", "known-current", "radio_live")
			)
		)
	}

	@Test
	fun discoverQueueDoesNothingWithoutValidCurrentSong() {
		assertEquals(
			emptyList(),
			discoverQueueRemovalIndexes(
				queueSongIds = listOf("known"),
				currentIndex = -1,
				knownSongIds = setOf("known")
			)
		)
		assertEquals(
			emptyList(),
			discoverQueueRemovalIndexes(
				queueSongIds = listOf("known"),
				currentIndex = 1,
				knownSongIds = setOf("known")
			)
		)
	}
}
