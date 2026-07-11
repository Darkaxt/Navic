package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_screen_on_off
import navic.composeapp.generated.resources.option_now_playing_screen_on_playing
import navic.composeapp.generated.resources.option_now_playing_screen_on_playing_charging
import org.jetbrains.compose.resources.StringResource

// Persisted by ordinal: never reorder or insert between existing entries; future values are append-only.
enum class NowPlayingScreenOnMode(val displayName: StringResource) {
	Off(Res.string.option_now_playing_screen_on_off),
	WhilePlayingAndCharging(Res.string.option_now_playing_screen_on_playing_charging),
	WhilePlaying(Res.string.option_now_playing_screen_on_playing)
}
