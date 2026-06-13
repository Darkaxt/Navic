package paige.navic.domain.models

import paige.navic.domain.models.lyrics.LyricsResult
import paige.navic.ui.core.UiState

fun shouldDismissLyricsPanel(
	hasCurrentSong: Boolean,
	lyricsState: UiState<LyricsResult?>
): Boolean {
	if (!hasCurrentSong) return true

	return lyricsState is UiState.Success && lyricsState.data?.lines.isNullOrEmpty()
}
