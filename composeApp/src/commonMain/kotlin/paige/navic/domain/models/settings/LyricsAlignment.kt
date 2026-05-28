package paige.navic.domain.models.settings

import androidx.compose.ui.text.style.TextAlign
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_lyrics_alignment_auto
import navic.composeapp.generated.resources.option_lyrics_alignment_center
import navic.composeapp.generated.resources.option_lyrics_alignment_end
import navic.composeapp.generated.resources.option_lyrics_alignment_start
import org.jetbrains.compose.resources.StringResource

enum class LyricsAlignment(
	val displayName: StringResource
) {
	Auto(Res.string.option_lyrics_alignment_auto),
	Start(Res.string.option_lyrics_alignment_start),
	Center(Res.string.option_lyrics_alignment_center),
	End(Res.string.option_lyrics_alignment_end);

	fun textAlign(isRtl: Boolean): TextAlign = when (this) {
		Auto -> if (isRtl) TextAlign.End else TextAlign.Start
		Start -> TextAlign.Start
		Center -> TextAlign.Center
		End -> TextAlign.End
	}
}
