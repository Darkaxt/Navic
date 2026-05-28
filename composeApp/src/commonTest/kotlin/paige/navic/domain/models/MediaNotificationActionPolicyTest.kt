package paige.navic.domain.models

import paige.navic.domain.models.settings.MediaNotificationAction
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaNotificationActionPolicyTest {
	@Test
	fun mediaNotificationActionsOmitDisabledSlots() {
		assertEquals(
			emptyList(),
			mediaNotificationActions(
				firstAction = MediaNotificationAction.Disabled,
				secondAction = MediaNotificationAction.Disabled
			)
		)
	}

	@Test
	fun mediaNotificationActionsKeepConfiguredSlotOrder() {
		assertEquals(
			listOf(MediaNotificationAction.Shuffle, MediaNotificationAction.Repeat),
			mediaNotificationActions(
				firstAction = MediaNotificationAction.Shuffle,
				secondAction = MediaNotificationAction.Repeat
			)
		)
	}

	@Test
	fun mediaNotificationActionsRemoveDuplicateSlots() {
		assertEquals(
			listOf(MediaNotificationAction.Repeat),
			mediaNotificationActions(
				firstAction = MediaNotificationAction.Repeat,
				secondAction = MediaNotificationAction.Repeat
			)
		)
	}
}
