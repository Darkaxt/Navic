package paige.navic.ui.core

import paige.navic.domain.models.normalizedPlaybackPitch
import paige.navic.domain.models.normalizedPlaybackSpeed

fun restoredPlayerStateForPreferences(
	restoredState: PlayerUiState,
	persistentQueue: Boolean,
	resumePlaybackOnStartup: Boolean
): PlayerUiState? {
	if (!persistentQueue) return null

	return restoredState.copy(
		isPaused = !resumePlaybackOnStartup,
		isLoading = false,
		playbackSpeed = normalizedPlaybackSpeed(restoredState.playbackSpeed),
		playbackPitch = normalizedPlaybackPitch(restoredState.playbackPitch)
	)
}
