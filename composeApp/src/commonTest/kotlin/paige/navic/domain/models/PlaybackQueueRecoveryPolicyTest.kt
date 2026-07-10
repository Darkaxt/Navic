package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

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
	fun playbackFailureAdvancesOnlyWhenPreferenceAllowsIt() {
		assertEquals(
			3,
			playbackFailureTargetIndex(
				skipMediaOnError = true,
				nextPlayableIndex = 3
			)
		)
		assertEquals(
			null,
			playbackFailureTargetIndex(
				skipMediaOnError = false,
				nextPlayableIndex = 3
			)
		)
		assertEquals(
			null,
			playbackFailureTargetIndex(
				skipMediaOnError = true,
				nextPlayableIndex = null
			)
		)
	}

	@Test
	fun playbackFailureNeverInventsAQueueTarget() {
		assertEquals(
			null,
			playbackFailureTargetIndex(
				skipMediaOnError = true,
				nextPlayableIndex = null
			)
		)
	}
}
