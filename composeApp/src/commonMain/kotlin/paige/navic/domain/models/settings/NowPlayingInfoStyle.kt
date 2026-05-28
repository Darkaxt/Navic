package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_info_style_album_and_artist
import navic.composeapp.generated.resources.option_now_playing_info_style_essential
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingInfoStyle(val displayName: StringResource) {
	Essential(Res.string.option_now_playing_info_style_essential),
	AlbumAndArtist(Res.string.option_now_playing_info_style_album_and_artist)
}
