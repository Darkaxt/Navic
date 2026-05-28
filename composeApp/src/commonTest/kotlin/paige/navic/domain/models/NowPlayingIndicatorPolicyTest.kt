package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingIndicatorPolicyTest {
	@Test
	fun indicatorShowsOnlyForCurrentSongWhenEnabled() {
		assertTrue(shouldShowNowPlayingIndicator(userEnabled = true, isCurrentSong = true))
		assertFalse(shouldShowNowPlayingIndicator(userEnabled = true, isCurrentSong = false))
	}

	@Test
	fun indicatorCanBeHiddenByUserSetting() {
		assertFalse(shouldShowNowPlayingIndicator(userEnabled = false, isCurrentSong = true))
		assertFalse(shouldShowNowPlayingIndicator(userEnabled = false, isCurrentSong = false))
	}
}
