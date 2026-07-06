package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackQueueRecoveryPolicyTest {
	@Test
	fun firstPlayableUpcomingIndexSkipsUnavailableItems() {
		assertEquals(
			3,
			firstPlayableUpcomingIndex(
				currentIndex = 0,
				queueSongIds = listOf("current", "missing-a", "missing-b", "ready", "later"),
				availableSongIds = setOf("ready", "later")
			)
		)
	}

	@Test
	fun firstPlayableUpcomingIndexReturnsNullWhenNothingAfterCurrentIsPlayable() {
		assertEquals(
			null,
			firstPlayableUpcomingIndex(
				currentIndex = 1,
				queueSongIds = listOf("previous", "current", "missing"),
				availableSongIds = setOf("previous")
			)
		)
	}

	@Test
	fun queueRecoveryReplaysLastPlayableWhenNoPlayableCandidateExists() {
		assertTrue(
			shouldReplayLastPlayable(
				hasLastPlayable = true,
				hasPlayableUpcoming = false,
				hasDeferredDownloads = true
			)
		)

		assertFalse(
			shouldReplayLastPlayable(
				hasLastPlayable = true,
				hasPlayableUpcoming = true,
				hasDeferredDownloads = true
			)
		)

		assertFalse(
			shouldReplayLastPlayable(
				hasLastPlayable = false,
				hasPlayableUpcoming = false,
				hasDeferredDownloads = true
			)
		)
	}

	@Test
	fun downloadedDeferredItemMovesDirectlyAfterCurrentItem() {
		assertEquals(
			2,
			recoveredDownloadTargetIndex(
				currentIndex = 1,
				queueSize = 6
			)
		)

		assertEquals(
			4,
			recoveredDownloadTargetIndex(
				currentIndex = 5,
				queueSize = 4
			)
		)
	}
}
