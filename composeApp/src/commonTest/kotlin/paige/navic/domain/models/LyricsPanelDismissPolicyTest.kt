package paige.navic.domain.models

import paige.navic.domain.models.lyrics.LyricsLine
import paige.navic.domain.models.lyrics.LyricsProvider
import paige.navic.domain.models.lyrics.LyricsResult
import paige.navic.ui.core.UiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsPanelDismissPolicyTest {
	@Test
	fun dismissesWhenThereIsNoCurrentSong() {
		assertTrue(
			shouldDismissLyricsPanel(
				hasCurrentSong = false,
				lyricsState = UiState.Loading()
			)
		)
	}

	@Test
	fun keepsPanelVisibleWhileLyricsAreResolving() {
		assertFalse(
			shouldDismissLyricsPanel(
				hasCurrentSong = true,
				lyricsState = UiState.Loading()
			)
		)
	}

	@Test
	fun dismissesWhenLyricsResolveEmpty() {
		assertTrue(
			shouldDismissLyricsPanel(
				hasCurrentSong = true,
				lyricsState = UiState.Success(null)
			)
		)
		assertTrue(
			shouldDismissLyricsPanel(
				hasCurrentSong = true,
				lyricsState = UiState.Success(
					LyricsResult(
						lines = emptyList(),
						provider = LyricsProvider.SUBSONIC
					)
				)
			)
		)
	}

	@Test
	fun keepsPanelVisibleWhenResolvedLyricsExist() {
		assertFalse(
			shouldDismissLyricsPanel(
				hasCurrentSong = true,
				lyricsState = UiState.Success(
					LyricsResult(
						lines = listOf(LyricsLine(text = "line")),
						provider = LyricsProvider.SUBSONIC
					)
				)
			)
		)
	}

	@Test
	fun keepsPanelVisibleForErrorsSoRetryCanRemainAvailable() {
		assertFalse(
			shouldDismissLyricsPanel(
				hasCurrentSong = true,
				lyricsState = UiState.Error(Exception("lyrics failed"))
			)
		)
	}
}
