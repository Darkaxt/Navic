package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_artwork_source_aurral_first
import navic.composeapp.generated.resources.option_artwork_source_native_first
import navic.composeapp.generated.resources.option_artwork_source_native_only
import org.jetbrains.compose.resources.StringResource

enum class ArtworkSourcePriority(val displayName: StringResource) {
	AurralFirst(Res.string.option_artwork_source_aurral_first),
	NativeFirst(Res.string.option_artwork_source_native_first),
	NativeOnly(Res.string.option_artwork_source_native_only)
}
