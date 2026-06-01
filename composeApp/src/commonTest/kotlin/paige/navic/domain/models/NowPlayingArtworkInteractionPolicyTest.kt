package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingArtworkTapAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NowPlayingArtworkInteractionPolicyTest {
	@Test
	fun opensLyricsOnlyWhenTapSettingIsEnabledAndSongExists() {
		assertTrue(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = true,
				showNowPlayingArtwork = true,
				hasCurrentSong = true,
				hasResolvedLyrics = true
			)
		)
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = false,
				showNowPlayingArtwork = true,
				hasCurrentSong = true,
				hasResolvedLyrics = true
			)
		)
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = true,
				showNowPlayingArtwork = true,
				hasCurrentSong = false,
				hasResolvedLyrics = true
			)
		)
		assertFalse(
			shouldOpenLyricsFromNowPlayingArtworkTap(
				tapArtworkForLyrics = true,
				showNowPlayingArtwork = true,
				hasCurrentSong = true,
				hasResolvedLyrics = false
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

	@Test
	fun artworkTapDestinationRequiresArtworkAndCurrentSong() {
		assertNull(
			nowPlayingArtworkTapDestination(
				configuredAction = NowPlayingArtworkTapAction.TrackInfo,
				legacyTapArtworkForLyrics = false,
				showNowPlayingArtwork = false,
				hasCurrentSong = true
			)
		)
		assertNull(
			nowPlayingArtworkTapDestination(
				configuredAction = NowPlayingArtworkTapAction.TrackInfo,
				legacyTapArtworkForLyrics = false,
				showNowPlayingArtwork = true,
				hasCurrentSong = false
			)
		)
	}

	@Test
	fun artworkTapDestinationFollowsConfiguredAction() {
		assertNull(
			nowPlayingArtworkTapDestination(
				configuredAction = NowPlayingArtworkTapAction.Disabled,
				legacyTapArtworkForLyrics = false,
				showNowPlayingArtwork = true,
				hasCurrentSong = true
			)
		)
		assertEquals(
			NowPlayingArtworkTapDestination.Lyrics,
			nowPlayingArtworkTapDestination(
				configuredAction = NowPlayingArtworkTapAction.Lyrics,
				legacyTapArtworkForLyrics = false,
				showNowPlayingArtwork = true,
				hasCurrentSong = true,
				hasResolvedLyrics = true
			)
		)
		assertNull(
			nowPlayingArtworkTapDestination(
				configuredAction = NowPlayingArtworkTapAction.Lyrics,
				legacyTapArtworkForLyrics = false,
				showNowPlayingArtwork = true,
				hasCurrentSong = true,
				hasResolvedLyrics = false
			)
		)
		assertEquals(
			NowPlayingArtworkTapDestination.TrackInfo,
			nowPlayingArtworkTapDestination(
				configuredAction = NowPlayingArtworkTapAction.TrackInfo,
				legacyTapArtworkForLyrics = false,
				showNowPlayingArtwork = true,
				hasCurrentSong = true
			)
		)
	}

	@Test
	fun legacyTapArtworkForLyricsMapsToLyricsWhenNewActionIsUnset() {
		assertEquals(
			NowPlayingArtworkTapAction.Lyrics,
			effectiveNowPlayingArtworkTapAction(
				configuredAction = NowPlayingArtworkTapAction.Disabled,
				legacyTapArtworkForLyrics = true
			)
		)
		assertEquals(
			NowPlayingArtworkTapAction.TrackInfo,
			effectiveNowPlayingArtworkTapAction(
				configuredAction = NowPlayingArtworkTapAction.TrackInfo,
				legacyTapArtworkForLyrics = true
			)
		)
	}

	@Test
	fun nowPlayingLyricsActionRequiresResolvedLyrics() {
		assertTrue(
			shouldShowNowPlayingLyricsAction(
				userActionEnabled = true,
				hasCurrentSong = true,
				hasResolvedLyrics = true
			)
		)
		assertFalse(
			shouldShowNowPlayingLyricsAction(
				userActionEnabled = false,
				hasCurrentSong = true,
				hasResolvedLyrics = true
			)
		)
		assertFalse(
			shouldShowNowPlayingLyricsAction(
				userActionEnabled = true,
				hasCurrentSong = false,
				hasResolvedLyrics = true
			)
		)
		assertFalse(
			shouldShowNowPlayingLyricsAction(
				userActionEnabled = true,
				hasCurrentSong = true,
				hasResolvedLyrics = false
			)
		)
	}

	@Test
	fun musicBrainzInfoActionIsHiddenWhenArtworkTapAlreadyOpensTrackInfo() {
		assertFalse(
			shouldShowNowPlayingMusicBrainzInfoAction(
				fallbackEnabled = true,
				hasCurrentSong = true,
				artworkTapDestination = NowPlayingArtworkTapDestination.TrackInfo
			)
		)
		assertTrue(
			shouldShowNowPlayingMusicBrainzInfoAction(
				fallbackEnabled = true,
				hasCurrentSong = true,
				artworkTapDestination = NowPlayingArtworkTapDestination.Lyrics
			)
		)
		assertTrue(
			shouldShowNowPlayingMusicBrainzInfoAction(
				fallbackEnabled = true,
				hasCurrentSong = true,
				artworkTapDestination = null
			)
		)
		assertFalse(
			shouldShowNowPlayingMusicBrainzInfoAction(
				fallbackEnabled = false,
				hasCurrentSong = true,
				artworkTapDestination = null
			)
		)
		assertFalse(
			shouldShowNowPlayingMusicBrainzInfoAction(
				fallbackEnabled = true,
				hasCurrentSong = false,
				artworkTapDestination = null
			)
		)
	}
}
