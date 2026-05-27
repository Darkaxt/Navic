package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PauseBetweenSongsPolicyTest {
	@Test
	fun pausesOnlyForAutomaticTransitionsWhilePlayingWithANewMediaItem() {
		assertTrue(
			shouldPauseBetweenSongsAfterTransition(
				pauseBetweenSongsSeconds = 5,
				isAutomaticTransition = true,
				isPlaying = true,
				hasMediaItem = true
			)
		)
		assertFalse(
			shouldPauseBetweenSongsAfterTransition(
				pauseBetweenSongsSeconds = 0,
				isAutomaticTransition = true,
				isPlaying = true,
				hasMediaItem = true
			)
		)
		assertFalse(
			shouldPauseBetweenSongsAfterTransition(
				pauseBetweenSongsSeconds = 5,
				isAutomaticTransition = false,
				isPlaying = true,
				hasMediaItem = true
			)
		)
		assertFalse(
			shouldPauseBetweenSongsAfterTransition(
				pauseBetweenSongsSeconds = 5,
				isAutomaticTransition = true,
				isPlaying = false,
				hasMediaItem = true
			)
		)
		assertFalse(
			shouldPauseBetweenSongsAfterTransition(
				pauseBetweenSongsSeconds = 5,
				isAutomaticTransition = true,
				isPlaying = true,
				hasMediaItem = false
			)
		)
	}

	@Test
	fun delayIsClampedToNonNegativeMilliseconds() {
		assertEquals(0L, pauseBetweenSongsDelayMs(-5))
		assertEquals(0L, pauseBetweenSongsDelayMs(0))
		assertEquals(5_000L, pauseBetweenSongsDelayMs(5))
	}
}
