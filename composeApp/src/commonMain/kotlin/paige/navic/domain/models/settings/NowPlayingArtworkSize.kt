package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_artwork_size_big
import navic.composeapp.generated.resources.option_now_playing_artwork_size_biggest
import navic.composeapp.generated.resources.option_now_playing_artwork_size_expanded
import navic.composeapp.generated.resources.option_now_playing_artwork_size_medium
import navic.composeapp.generated.resources.option_now_playing_artwork_size_small
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingArtworkSize(
	val activePaddingDp: Int,
	val displayName: StringResource
) {
	Small(72, Res.string.option_now_playing_artwork_size_small),
	Medium(48, Res.string.option_now_playing_artwork_size_medium),
	Big(32, Res.string.option_now_playing_artwork_size_big),
	Biggest(16, Res.string.option_now_playing_artwork_size_biggest),
	Expanded(0, Res.string.option_now_playing_artwork_size_expanded)
}
