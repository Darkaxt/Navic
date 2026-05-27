package paige.navic.domain.models

import paige.navic.domain.models.settings.QueueSwipeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueSwipeActionPolicyTest {
	@Test
	fun disabledQueueSwipeActionsAlwaysReturnDisabled() {
		assertEquals(
			QueueSwipeAction.Disabled,
			queueSwipeActionForDirection(
				enabled = false,
				startToEndAction = QueueSwipeAction.RemoveFromQueue,
				endToStartAction = QueueSwipeAction.PlayNext,
				direction = SongSwipeDirection.StartToEnd
			)
		)
		assertEquals(
			QueueSwipeAction.Disabled,
			queueSwipeActionForDirection(
				enabled = false,
				startToEndAction = QueueSwipeAction.RemoveFromQueue,
				endToStartAction = QueueSwipeAction.PlayNext,
				direction = SongSwipeDirection.EndToStart
			)
		)
	}

	@Test
	fun queueSwipeDirectionsUseConfiguredActions() {
		assertEquals(
			QueueSwipeAction.RemoveFromQueue,
			queueSwipeActionForDirection(
				enabled = true,
				startToEndAction = QueueSwipeAction.RemoveFromQueue,
				endToStartAction = QueueSwipeAction.PlayNext,
				direction = SongSwipeDirection.StartToEnd
			)
		)
		assertEquals(
			QueueSwipeAction.PlayNext,
			queueSwipeActionForDirection(
				enabled = true,
				startToEndAction = QueueSwipeAction.RemoveFromQueue,
				endToStartAction = QueueSwipeAction.PlayNext,
				direction = SongSwipeDirection.EndToStart
			)
		)
	}

	@Test
	fun playNextTargetMovesQueuedSongAfterTheCurrentSong() {
		assertEquals(3, queuePlayNextTargetIndex(fromIndex = 1, currentIndex = 3, queueSize = 6))
		assertEquals(4, queuePlayNextTargetIndex(fromIndex = 5, currentIndex = 3, queueSize = 6))
		assertNull(queuePlayNextTargetIndex(fromIndex = 3, currentIndex = 3, queueSize = 6))
		assertNull(queuePlayNextTargetIndex(fromIndex = 8, currentIndex = 3, queueSize = 6))
		assertNull(queuePlayNextTargetIndex(fromIndex = 1, currentIndex = -1, queueSize = 6))
	}
}
