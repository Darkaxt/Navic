package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_artwork_source_aurral_first
import navic.composeapp.generated.resources.option_artwork_source_external_cover_first
import navic.composeapp.generated.resources.option_artwork_source_native_cover_first
import navic.composeapp.generated.resources.option_artwork_source_native_first
import navic.composeapp.generated.resources.option_artwork_source_native_only
import org.jetbrains.compose.resources.StringResource

enum class ArtworkSourcePriority(
	val displayName: StringResource,
	val coverDisplayName: StringResource = displayName
) {
	AurralFirst(
		Res.string.option_artwork_source_aurral_first,
		Res.string.option_artwork_source_external_cover_first
	),
	NativeFirst(
		Res.string.option_artwork_source_native_first,
		Res.string.option_artwork_source_native_cover_first
	),
	NativeOnly(Res.string.option_artwork_source_native_only)
}
