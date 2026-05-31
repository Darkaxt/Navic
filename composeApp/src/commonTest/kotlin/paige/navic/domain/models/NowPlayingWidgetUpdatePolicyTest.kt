package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingWidgetUpdatePolicyTest {
	@Test
	fun widgetRefreshesWhenPlaybackItemChangesEvenIfPlayingStateDoesNot() {
		assertTrue(
			shouldSendNowPlayingWidgetUpdate(
				previousSongId = "one",
				currentSongId = "two",
				previousIsPlaying = true,
				currentIsPlaying = true
			)
		)
	}

	@Test
	fun widgetRefreshesWhenPlayingStateChangesForSameTrack() {
		assertTrue(
			shouldSendNowPlayingWidgetUpdate(
				previousSongId = "one",
				currentSongId = "one",
				previousIsPlaying = false,
				currentIsPlaying = true
			)
		)
	}

	@Test
	fun widgetDoesNotRefreshForUnchangedTrackAndPlayingState() {
		assertFalse(
			shouldSendNowPlayingWidgetUpdate(
				previousSongId = "one",
				currentSongId = "one",
				previousIsPlaying = true,
				currentIsPlaying = true
			)
		)
	}
}
