package paige.navic.ui.core

fun restoredPlayerStateForPreferences(
	restoredState: PlayerUiState,
	persistentQueue: Boolean,
	resumePlaybackOnStartup: Boolean
): PlayerUiState? {
	if (!persistentQueue) return null

	return restoredState.copy(
		isPaused = !resumePlaybackOnStartup,
		isLoading = false
	)
}
