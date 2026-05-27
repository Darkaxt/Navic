package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_off
import navic.composeapp.generated.resources.option_reverb_large_hall
import navic.composeapp.generated.resources.option_reverb_large_room
import navic.composeapp.generated.resources.option_reverb_medium_hall
import navic.composeapp.generated.resources.option_reverb_medium_room
import navic.composeapp.generated.resources.option_reverb_plate
import navic.composeapp.generated.resources.option_reverb_small_room
import org.jetbrains.compose.resources.StringResource

enum class AudioReverbPreset(
	val androidPresetValue: Int,
	val displayName: StringResource
) {
	Off(0, Res.string.option_off),
	SmallRoom(1, Res.string.option_reverb_small_room),
	MediumRoom(2, Res.string.option_reverb_medium_room),
	LargeRoom(3, Res.string.option_reverb_large_room),
	MediumHall(4, Res.string.option_reverb_medium_hall),
	LargeHall(5, Res.string.option_reverb_large_hall),
	Plate(6, Res.string.option_reverb_plate)
}
