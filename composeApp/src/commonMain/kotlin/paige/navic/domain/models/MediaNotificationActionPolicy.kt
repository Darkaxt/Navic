package paige.navic.domain.models

import paige.navic.domain.models.settings.MediaNotificationAction

fun mediaNotificationActions(
	firstAction: MediaNotificationAction,
	secondAction: MediaNotificationAction
): List<MediaNotificationAction> =
	listOf(firstAction, secondAction)
		.filterNot { it == MediaNotificationAction.Disabled }
		.distinct()
