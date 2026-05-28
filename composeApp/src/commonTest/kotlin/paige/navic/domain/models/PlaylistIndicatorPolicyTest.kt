package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistIndicatorPolicyTest {
	@Test
	fun indicatorShowsOnlyForPlaylistSongsOutsidePlaylistScreens() {
		assertTrue(
			shouldShowPlaylistIndicator(
				userEnabled = true,
				isInPlaylist = true,
				isPlaylistScreen = false
			)
		)
		assertFalse(
			shouldShowPlaylistIndicator(
				userEnabled = true,
				isInPlaylist = false,
				isPlaylistScreen = false
			)
		)
		assertFalse(
			shouldShowPlaylistIndicator(
				userEnabled = true,
				isInPlaylist = true,
				isPlaylistScreen = true
			)
		)
	}

	@Test
	fun indicatorCanBeHiddenByUserSetting() {
		assertFalse(
			shouldShowPlaylistIndicator(
				userEnabled = false,
				isInPlaylist = true,
				isPlaylistScreen = false
			)
		)
	}
}
