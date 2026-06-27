package paige.navic.ui.screens.nowPlaying.viewmodels

import paige.navic.ui.core.UiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingLyricsLookupPolicyTest {
	@Test
	fun startsLookupWhenSongChanges() {
		assertTrue(
			shouldStartLyricsLookup(
				currentSongId = "old-song",
				requestedSongId = "new-song",
				lyricsState = UiState.Success(false),
				previousProgress = 0.7f,
				currentProgress = 0.2f
			)
		)
	}

	@Test
	fun doesNotDuplicateLookupWhileLoading() {
		assertFalse(
			shouldStartLyricsLookup(
				currentSongId = "song-1",
				requestedSongId = "song-1",
				lyricsState = UiState.Loading(false),
				previousProgress = 0.5f,
				currentProgress = 0.1f
			)
		)
	}

	@Test
	fun keepsResolvedLyricsWithoutRecheckingSameSong() {
		assertFalse(
			shouldStartLyricsLookup(
				currentSongId = "song-1",
				requestedSongId = "song-1",
				lyricsState = UiState.Success(true),
				previousProgress = 0.8f,
				currentProgress = 0.1f
			)
		)
	}

	@Test
	fun doesNotRecheckUnresolvedLyricsOnNormalProgressUpdates() {
		assertFalse(
			shouldStartLyricsLookup(
				currentSongId = "song-1",
				requestedSongId = "song-1",
				lyricsState = UiState.Success(false),
				previousProgress = 0.2f,
				currentProgress = 0.3f
			)
		)
	}

	@Test
	fun rechecksUnresolvedLyricsWhenSameSongRestartsOrSeeksBack() {
		assertTrue(
			shouldStartLyricsLookup(
				currentSongId = "song-1",
				requestedSongId = "song-1",
				lyricsState = UiState.Success(false),
				previousProgress = 0.8f,
				currentProgress = 0.1f
			)
		)
		assertTrue(
			shouldStartLyricsLookup(
				currentSongId = "song-1",
				requestedSongId = "song-1",
				lyricsState = UiState.Error(Exception("server returned no lyrics"), false),
				previousProgress = 0.8f,
				currentProgress = 0.1f
			)
		)
	}
}
