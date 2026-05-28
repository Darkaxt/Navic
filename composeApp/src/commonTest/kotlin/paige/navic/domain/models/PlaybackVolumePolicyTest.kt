package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackVolumePolicyTest {
	@Test
	fun playbackVolumePercentIsClampedToPlayerVolumeRange() {
		assertEquals(0f, playbackVolumeMultiplier(-10, 1f))
		assertEquals(0f, playbackVolumeMultiplier(0, 1f))
		assertEquals(0.5f, playbackVolumeMultiplier(50, 1f))
		assertEquals(1f, playbackVolumeMultiplier(100, 1f))
		assertEquals(1f, playbackVolumeMultiplier(150, 1f))
	}

	@Test
	fun playbackVolumeCombinesWithReplayGainMultiplier() {
		assertEquals(0.25f, playbackVolumeMultiplier(50, 0.5f))
		assertEquals(0.5f, playbackVolumeMultiplier(100, 0.5f))
		assertEquals(0f, playbackVolumeMultiplier(50, -1f))
		assertEquals(1f, playbackVolumeMultiplier(100, 2f))
	}
}
