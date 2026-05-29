package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_lida_clips_background_video_blurred
import navic.composeapp.generated.resources.option_lida_clips_background_video_normal
import navic.composeapp.generated.resources.option_lida_clips_background_video_off
import org.jetbrains.compose.resources.StringResource

enum class LidaClipsBackgroundVideoMode(val displayName: StringResource) {
	Off(Res.string.option_lida_clips_background_video_off),
	Blurred(Res.string.option_lida_clips_background_video_blurred),
	Normal(Res.string.option_lida_clips_background_video_normal)
}
