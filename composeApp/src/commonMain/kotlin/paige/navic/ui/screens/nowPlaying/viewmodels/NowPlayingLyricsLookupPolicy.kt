package paige.navic.ui.screens.nowPlaying.viewmodels

import paige.navic.ui.core.UiState

internal fun shouldStartLyricsLookup(
	currentSongId: String?,
	requestedSongId: String,
	lyricsState: UiState<Boolean>,
	previousProgress: Float?,
	currentProgress: Float
): Boolean {
	if (currentSongId != requestedSongId) return true
	if (lyricsState is UiState.Loading) return false
	if (lyricsState.data == true) return false
	return previousProgress != null && currentProgress < previousProgress
}
