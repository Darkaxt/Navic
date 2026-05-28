package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingArtworkInteractionPolicyTest {
	@Test
	fun opensLyricsOnlyWhenTapSettingIsEnabledAndSongExists() {
		assertTrue(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = true,
				showNowPlayingArtwork = true,
				hasCurrentSong = true
			)
		)
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = false,
				showNowPlayingArtwork = true,
				hasCurrentSong = true
			)
		)
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = true,
				showNowPlayingArtwork = true,
				hasCurrentSong = false
			)
		)
	}

	@Test
	fun artworkTapDoesNotOpenLyricsWhenArtworkIsHidden() {
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = true,
				showNowPlayingArtwork = false,
				hasCurrentSong = true
			)
		)
	}
}
