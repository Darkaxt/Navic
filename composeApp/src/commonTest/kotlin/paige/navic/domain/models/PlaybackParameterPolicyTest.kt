package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackParameterPolicyTest {
	@Test
	fun playbackSpeedIsSnappedAndClampedToSupportedRange() {
		assertEquals(0.5f, normalizedPlaybackSpeed(0.1f))
		assertEquals(0.73f, normalizedPlaybackSpeed(0.734f))
		assertEquals(2.0f, normalizedPlaybackSpeed(2.4f))
	}

	@Test
	fun playbackPitchIsSnappedAndClampedToSupportedRange() {
		assertEquals(0.5f, normalizedPlaybackPitch(0.1f))
		assertEquals(0.84f, normalizedPlaybackPitch(0.844f))
		assertEquals(2.0f, normalizedPlaybackPitch(2.4f))
	}
}
