package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_play_next
import navic.composeapp.generated.resources.action_remove_from_queue
import navic.composeapp.generated.resources.option_song_swipe_action_disabled
import org.jetbrains.compose.resources.StringResource

enum class QueueSwipeAction(val displayName: StringResource) {
	RemoveFromQueue(Res.string.action_remove_from_queue),
	PlayNext(Res.string.action_play_next),
	Disabled(Res.string.option_song_swipe_action_disabled)
}
