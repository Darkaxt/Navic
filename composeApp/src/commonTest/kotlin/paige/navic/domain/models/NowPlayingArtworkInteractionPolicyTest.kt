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
				hasCurrentSong = true
			)
		)
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = false,
				hasCurrentSong = true
			)
		)
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = true,
				hasCurrentSong = false
			)
		)
	}
}
