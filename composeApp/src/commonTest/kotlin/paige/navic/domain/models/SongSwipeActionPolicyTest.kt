package paige.navic.domain.models

import paige.navic.domain.models.settings.SongSwipeAction
import kotlin.test.Test
import kotlin.test.assertEquals

class SongSwipeActionPolicyTest {
	@Test
	fun disabledSwipeActionsAlwaysReturnDisabled() {
		assertEquals(
			SongSwipeAction.Disabled,
			songSwipeActionForDirection(
				enabled = false,
				startToEndAction = SongSwipeAction.AddToQueue,
				endToStartAction = SongSwipeAction.PlayNext,
				direction = SongSwipeDirection.StartToEnd
			)
		)
		assertEquals(
			SongSwipeAction.Disabled,
			songSwipeActionForDirection(
				enabled = false,
				startToEndAction = SongSwipeAction.AddToQueue,
				endToStartAction = SongSwipeAction.PlayNext,
				direction = SongSwipeDirection.EndToStart
			)
		)
	}

	@Test
	fun swipeDirectionsUseConfiguredActions() {
		assertEquals(
			SongSwipeAction.AddToQueue,
			songSwipeActionForDirection(
				enabled = true,
				startToEndAction = SongSwipeAction.AddToQueue,
				endToStartAction = SongSwipeAction.PlayNext,
				direction = SongSwipeDirection.StartToEnd
			)
		)
		assertEquals(
			SongSwipeAction.PlayNext,
			songSwipeActionForDirection(
				enabled = true,
				startToEndAction = SongSwipeAction.AddToQueue,
				endToStartAction = SongSwipeAction.PlayNext,
				direction = SongSwipeDirection.EndToStart
			)
		)
	}
}
