package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_repeat
import navic.composeapp.generated.resources.action_shuffle
import navic.composeapp.generated.resources.option_media_notification_action_disabled
import org.jetbrains.compose.resources.StringResource

enum class MediaNotificationAction(val displayName: StringResource) {
	Disabled(Res.string.option_media_notification_action_disabled),
	Shuffle(Res.string.action_shuffle),
	Repeat(Res.string.action_repeat)
}
