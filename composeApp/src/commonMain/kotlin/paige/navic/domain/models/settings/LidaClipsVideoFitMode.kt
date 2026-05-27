package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_lida_clips_video_fit_crop
import navic.composeapp.generated.resources.option_lida_clips_video_fit_fit
import org.jetbrains.compose.resources.StringResource

enum class LidaClipsVideoFitMode(val displayName: StringResource) {
	Fit(Res.string.option_lida_clips_video_fit_fit),
	Crop(Res.string.option_lida_clips_video_fit_crop)
}
