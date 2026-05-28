package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_progress_width_big
import navic.composeapp.generated.resources.option_now_playing_progress_width_biggest
import navic.composeapp.generated.resources.option_now_playing_progress_width_expanded
import navic.composeapp.generated.resources.option_now_playing_progress_width_medium
import navic.composeapp.generated.resources.option_now_playing_progress_width_small
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingProgressWidth(val displayName: StringResource) {
	Small(Res.string.option_now_playing_progress_width_small),
	Medium(Res.string.option_now_playing_progress_width_medium),
	Big(Res.string.option_now_playing_progress_width_big),
	Biggest(Res.string.option_now_playing_progress_width_biggest),
	Expanded(Res.string.option_now_playing_progress_width_expanded)
}
