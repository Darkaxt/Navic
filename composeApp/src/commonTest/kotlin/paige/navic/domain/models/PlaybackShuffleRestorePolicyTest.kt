package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackShuffleRestorePolicyTest {
	@Test
	fun placesPreviouslyPlayedItemsBeforeCurrentAndPreservesUpcomingOrder() {
		assertEquals(
			listOf(1, 4, 2, 3, 0),
			restoredShuffleOrder(
				itemCount = 5,
				currentIndex = 2,
				upcomingIndexes = listOf(3, 0)
			)
		)
	}

	@Test
	fun preservesFullRepeatAllCycleAfterCurrentItem() {
		assertEquals(
			listOf(2, 4, 1, 3, 0),
			restoredShuffleOrder(
				itemCount = 5,
				currentIndex = 2,
				upcomingIndexes = listOf(4, 1, 3, 0)
			)
		)
	}

	@Test
	fun rejectsMalformedPersistedIndexes() {
		assertNull(restoredShuffleOrder(4, 1, listOf(2, 2)))
		assertNull(restoredShuffleOrder(4, 1, listOf(4)))
		assertNull(restoredShuffleOrder(4, 1, listOf(1, 2)))
		assertNull(restoredShuffleOrder(0, 0, emptyList()))
	}
}
