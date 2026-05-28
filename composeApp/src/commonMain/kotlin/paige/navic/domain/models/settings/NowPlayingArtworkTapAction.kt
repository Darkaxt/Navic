package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_artwork_tap_action_disabled
import navic.composeapp.generated.resources.option_now_playing_artwork_tap_action_lyrics
import navic.composeapp.generated.resources.option_now_playing_artwork_tap_action_track_info
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingArtworkTapAction(
	val displayName: StringResource
) {
	Disabled(Res.string.option_now_playing_artwork_tap_action_disabled),
	Lyrics(Res.string.option_now_playing_artwork_tap_action_lyrics),
	TrackInfo(Res.string.option_now_playing_artwork_tap_action_track_info)
}
