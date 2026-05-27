package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_song_swipe_action_add_to_queue
import navic.composeapp.generated.resources.option_song_swipe_action_disabled
import navic.composeapp.generated.resources.option_song_swipe_action_play_next
import org.jetbrains.compose.resources.StringResource

enum class SongSwipeAction(val displayName: StringResource) {
	AddToQueue(Res.string.option_song_swipe_action_add_to_queue),
	PlayNext(Res.string.option_song_swipe_action_play_next),
	Disabled(Res.string.option_song_swipe_action_disabled)
}
