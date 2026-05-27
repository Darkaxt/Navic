package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumeZeroPausePolicyTest {
	@Test
	fun pausesOnlyWhenEnabledPlayingAndMediaVolumeIsZero() {
		assertTrue(
			shouldPausePlaybackWhenVolumeZero(
				pausePlaybackOnVolumeZero = true,
				isPlaying = true,
				volume = 0
			)
		)
		assertFalse(
			shouldPausePlaybackWhenVolumeZero(
				pausePlaybackOnVolumeZero = false,
				isPlaying = true,
				volume = 0
			)
		)
		assertFalse(
			shouldPausePlaybackWhenVolumeZero(
				pausePlaybackOnVolumeZero = true,
				isPlaying = false,
				volume = 0
			)
		)
		assertFalse(
			shouldPausePlaybackWhenVolumeZero(
				pausePlaybackOnVolumeZero = true,
				isPlaying = true,
				volume = 1
			)
		)
	}

	@Test
	fun resumesOnlyWhenEnabledPausedByZeroVolumeAndMediaVolumeIsRestored() {
		assertTrue(
			shouldResumePlaybackAfterVolumeRestored(
				pausePlaybackOnVolumeZero = true,
				pausedByZeroVolume = true,
				volume = 1
			)
		)
		assertFalse(
			shouldResumePlaybackAfterVolumeRestored(
				pausePlaybackOnVolumeZero = false,
				pausedByZeroVolume = true,
				volume = 1
			)
		)
		assertFalse(
			shouldResumePlaybackAfterVolumeRestored(
				pausePlaybackOnVolumeZero = true,
				pausedByZeroVolume = false,
				volume = 1
			)
		)
		assertFalse(
			shouldResumePlaybackAfterVolumeRestored(
				pausePlaybackOnVolumeZero = true,
				pausedByZeroVolume = true,
				volume = 0
			)
		)
	}
}
