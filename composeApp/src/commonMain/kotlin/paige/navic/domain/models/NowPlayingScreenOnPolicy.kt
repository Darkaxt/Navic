package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingScreenOnMode

fun shouldKeepNowPlayingScreenOn(
	mode: NowPlayingScreenOnMode,
	hasActiveSong: Boolean,
	isPaused: Boolean,
	isExternalPowerConnected: Boolean
): Boolean {
	if (!hasActiveSong || isPaused) return false

	return when (mode) {
		NowPlayingScreenOnMode.Off -> false
		NowPlayingScreenOnMode.WhilePlayingAndCharging -> isExternalPowerConnected
		NowPlayingScreenOnMode.WhilePlaying -> true
	}
}
