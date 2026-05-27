package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidaClipMusicPlaybackPolicyTest {
	@Test
	fun pausesOnlyWhenSettingIsEnabledAndMusicIsPlaying() {
		assertTrue(
			shouldPauseMusicForLidaClip(
				pauseMusicPlayback = true,
				hasCurrentSong = true,
				musicIsPaused = false
			)
		)
		assertFalse(
			shouldPauseMusicForLidaClip(
				pauseMusicPlayback = false,
				hasCurrentSong = true,
				musicIsPaused = false
			)
		)
		assertFalse(
			shouldPauseMusicForLidaClip(
				pauseMusicPlayback = true,
				hasCurrentSong = false,
				musicIsPaused = false
			)
		)
		assertFalse(
			shouldPauseMusicForLidaClip(
				pauseMusicPlayback = true,
				hasCurrentSong = true,
				musicIsPaused = true
			)
		)
	}

	@Test
	fun resumesOnlyTheSameSongThatWasPausedForTheClip() {
		assertTrue(
			shouldResumeMusicAfterLidaClip(
				pauseMusicPlayback = true,
				pausedSongId = "song-1",
				currentSongId = "song-1",
				musicIsPaused = true
			)
		)
		assertFalse(
			shouldResumeMusicAfterLidaClip(
				pauseMusicPlayback = false,
				pausedSongId = "song-1",
				currentSongId = "song-1",
				musicIsPaused = true
			)
		)
		assertFalse(
			shouldResumeMusicAfterLidaClip(
				pauseMusicPlayback = true,
				pausedSongId = null,
				currentSongId = "song-1",
				musicIsPaused = true
			)
		)
		assertFalse(
			shouldResumeMusicAfterLidaClip(
				pauseMusicPlayback = true,
				pausedSongId = "song-1",
				currentSongId = "song-2",
				musicIsPaused = true
			)
		)
		assertFalse(
			shouldResumeMusicAfterLidaClip(
				pauseMusicPlayback = true,
				pausedSongId = "song-1",
				currentSongId = "song-1",
				musicIsPaused = false
			)
		)
	}

	@Test
	fun lidaClipAudioFocusHandlingFollowsPlaybackPreference() {
		assertTrue(shouldHandleLidaClipAudioFocus(respectAudioFocus = true))
		assertFalse(shouldHandleLidaClipAudioFocus(respectAudioFocus = false))
	}
}
