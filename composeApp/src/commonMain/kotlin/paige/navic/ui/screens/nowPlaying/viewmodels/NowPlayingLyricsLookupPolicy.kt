package paige.navic.ui.screens.nowPlaying.viewmodels

import paige.navic.ui.core.UiState

internal fun shouldStartLyricsLookup(
	currentSongId: String?,
	requestedSongId: String,
	lyricsState: UiState<Boolean>,
	previousProgress: Float?,
	currentProgress: Float,
	resuming: Boolean = false,
	canRequest: Boolean = true
): Boolean {
	if (!canRequest) return false
	if (currentSongId != requestedSongId) return true
	if (lyricsState is UiState.Loading) return false
	if (lyricsState.data == true) return false
	if (resuming && lyricsState is UiState.Error) return true
	return previousProgress != null && currentProgress < previousProgress
}
