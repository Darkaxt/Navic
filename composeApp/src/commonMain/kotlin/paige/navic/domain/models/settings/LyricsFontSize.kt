package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_lyrics_font_size_large
import navic.composeapp.generated.resources.option_lyrics_font_size_medium
import navic.composeapp.generated.resources.option_lyrics_font_size_small
import navic.composeapp.generated.resources.option_lyrics_font_size_xlarge
import org.jetbrains.compose.resources.StringResource

enum class LyricsFontSize(
	val sizeSp: Int,
	val displayName: StringResource
) {
	Small(26, Res.string.option_lyrics_font_size_small),
	Medium(32, Res.string.option_lyrics_font_size_medium),
	Large(38, Res.string.option_lyrics_font_size_large),
	ExtraLarge(44, Res.string.option_lyrics_font_size_xlarge)
}
