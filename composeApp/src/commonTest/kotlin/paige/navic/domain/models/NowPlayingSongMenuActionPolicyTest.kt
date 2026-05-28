package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingSongMenuActionPolicyTest {
	@Test
	fun startRadioActionRequiresUserSettingAndNonRadioSong() {
		assertTrue(shouldShowNowPlayingStartRadioAction(userActionEnabled = true, songId = "song-1"))
		assertFalse(shouldShowNowPlayingStartRadioAction(userActionEnabled = false, songId = "song-1"))
		assertFalse(shouldShowNowPlayingStartRadioAction(userActionEnabled = true, songId = "radio_1"))
		assertFalse(shouldShowNowPlayingStartRadioAction(userActionEnabled = true, songId = null))
	}

	@Test
	fun discoverQueueActionRequiresUserSettingAndUpcomingQueueItems() {
		assertTrue(shouldShowNowPlayingDiscoverQueueAction(userActionEnabled = true, hasUpcomingSongs = true))
		assertFalse(shouldShowNowPlayingDiscoverQueueAction(userActionEnabled = false, hasUpcomingSongs = true))
		assertFalse(shouldShowNowPlayingDiscoverQueueAction(userActionEnabled = true, hasUpcomingSongs = false))
	}

	@Test
	fun addToPlaylistActionRequiresUserSettingAndSong() {
		assertTrue(shouldShowNowPlayingAddToPlaylistAction(userActionEnabled = true, songId = "song-1"))
		assertFalse(shouldShowNowPlayingAddToPlaylistAction(userActionEnabled = false, songId = "song-1"))
		assertFalse(shouldShowNowPlayingAddToPlaylistAction(userActionEnabled = true, songId = null))
	}

	@Test
	fun downloadActionRequiresUserSettingAndNonRadioSong() {
		assertTrue(shouldShowNowPlayingDownloadAction(userActionEnabled = true, songId = "song-1"))
		assertFalse(shouldShowNowPlayingDownloadAction(userActionEnabled = false, songId = "song-1"))
		assertFalse(shouldShowNowPlayingDownloadAction(userActionEnabled = true, songId = "radio_1"))
		assertFalse(shouldShowNowPlayingDownloadAction(userActionEnabled = true, songId = null))
	}

	@Test
	fun moreActionFollowsUserSetting() {
		assertTrue(shouldShowNowPlayingMoreAction(userActionEnabled = true))
		assertFalse(shouldShowNowPlayingMoreAction(userActionEnabled = false))
	}
}
