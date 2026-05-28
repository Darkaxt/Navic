package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_technical_info_style_compact
import navic.composeapp.generated.resources.option_now_playing_technical_info_style_detailed
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingTechnicalInfoStyle(val displayName: StringResource) {
	Compact(Res.string.option_now_playing_technical_info_style_compact),
	Detailed(Res.string.option_now_playing_technical_info_style_detailed)
}
