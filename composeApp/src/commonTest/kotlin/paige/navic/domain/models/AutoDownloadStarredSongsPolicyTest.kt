package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoDownloadStarredSongsPolicyTest {
	@Test
	fun autoDownloadOnlyRunsForOnlineNewlyStarredSongs() {
		assertTrue(
			shouldAutoDownloadStarredSong(
				autoDownloadStarredSongs = true,
				isStarring = true,
				isOnline = true,
				isDownloaded = false
			)
		)

		assertFalse(
			shouldAutoDownloadStarredSong(
				autoDownloadStarredSongs = false,
				isStarring = true,
				isOnline = true,
				isDownloaded = false
			)
		)
		assertFalse(
			shouldAutoDownloadStarredSong(
				autoDownloadStarredSongs = true,
				isStarring = false,
				isOnline = true,
				isDownloaded = false
			)
		)
		assertFalse(
			shouldAutoDownloadStarredSong(
				autoDownloadStarredSongs = true,
				isStarring = true,
				isOnline = false,
				isDownloaded = false
			)
		)
		assertFalse(
			shouldAutoDownloadStarredSong(
				autoDownloadStarredSongs = true,
				isStarring = true,
				isOnline = true,
				isDownloaded = true
			)
		)
	}
}
