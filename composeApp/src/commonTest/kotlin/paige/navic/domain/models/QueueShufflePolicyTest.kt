package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class QueueShufflePolicyTest {
	@Test
	fun queueShuffleLimitKeepsAllSongsWhenUnlimited() {
		assertEquals(
			listOf("a", "b", "c"),
			limitQueueShuffle(listOf("a", "b", "c"), limit = 0)
		)
	}

	@Test
	fun queueShuffleLimitTakesConfiguredNumberAfterShuffleOrdering() {
		assertEquals(
			listOf("a", "b"),
			limitQueueShuffle(listOf("a", "b", "c"), limit = 2)
		)
	}

	@Test
	fun queueShuffleLimitClampsNegativeLimitsToUnlimited() {
		assertEquals(
			listOf("a", "b", "c"),
			limitQueueShuffle(listOf("a", "b", "c"), limit = -1)
		)
	}
}
