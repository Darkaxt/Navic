package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflinePlaybackFallbackPolicyTest {
	@Test
	fun currentLocalMediaWinsWithoutChangingQueuePosition() {
		assertEquals(
			OfflinePlaybackFallbackResolution.KeepCurrent,
			resolveOfflinePlaybackFallback(
				currentIndex = 1,
				queueSongIds = listOf("before", "current", "next"),
				upcomingIndexes = listOf(2),
				availableSongIds = setOf("next"),
				currentUsesLocalFile = true
			)
		)
	}

	@Test
	fun downloadedCurrentMediaWinsBeforeUpcomingFallback() {
		assertEquals(
			OfflinePlaybackFallbackResolution.KeepCurrent,
			resolveOfflinePlaybackFallback(
				currentIndex = 1,
				queueSongIds = listOf("before", "current", "next"),
				upcomingIndexes = listOf(2),
				availableSongIds = setOf("current", "next"),
				currentUsesLocalFile = false
			)
		)
	}

	@Test
	fun firstCachedItemInMedia3UpcomingOrderWins() {
		assertEquals(
			OfflinePlaybackFallbackResolution.PlayUpcoming(3),
			resolveOfflinePlaybackFallback(
				currentIndex = 0,
				queueSongIds = listOf("current", "linear-next", "uncached", "shuffle-next"),
				upcomingIndexes = listOf(3, 2, 1),
				availableSongIds = setOf("linear-next", "shuffle-next"),
				currentUsesLocalFile = false
			)
		)
	}

	@Test
	fun invalidAndRepeatedIndexesAreIgnored() {
		assertEquals(
			OfflinePlaybackFallbackResolution.PlayUpcoming(2),
			resolveOfflinePlaybackFallback(
				currentIndex = 0,
				queueSongIds = listOf("current", "missing", "ready"),
				upcomingIndexes = listOf(-1, 0, 99, 2),
				availableSongIds = setOf("ready"),
				currentUsesLocalFile = false
			)
		)
	}

	@Test
	fun noVerifiedCachedCandidateHoldsTheOriginalItem() {
		assertEquals(
			OfflinePlaybackFallbackResolution.Hold,
			resolveOfflinePlaybackFallback(
				currentIndex = 2,
				queueSongIds = listOf("before", "before-two", "current", "next"),
				upcomingIndexes = listOf(3),
				availableSongIds = emptySet(),
				currentUsesLocalFile = false
			)
		)
	}
}
