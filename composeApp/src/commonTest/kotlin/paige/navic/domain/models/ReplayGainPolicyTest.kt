package paige.navic.domain.models

import paige.navic.domain.models.settings.ReplayGainMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplayGainPolicyTest {
	@Test
	fun replayGainDbSelectsModeSpecificGainWithFallbacks() {
		val replayGain = replayGain(trackGain = 4f, albumGain = -3f, fallbackGain = -1f)

		assertEquals(4f, replayGainDb(replayGain, ReplayGainMode.Track))
		assertEquals(-3f, replayGainDb(replayGain, ReplayGainMode.Album))
		assertNull(replayGainDb(replayGain, ReplayGainMode.Off))
		assertEquals(-1f, replayGainDb(replayGain(trackGain = null, fallbackGain = -1f), ReplayGainMode.Track))
	}

	@Test
	fun replayGainVolumeMultiplierPreservesCurrentBehaviorWhenLoudnessBoostIsOff() {
		assertEquals(
			1f,
			replayGainVolumeMultiplier(
				replayGain = replayGain(trackGain = 6f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = false
			),
			0.0001f
		)
		assertEquals(
			0.501f,
			replayGainVolumeMultiplier(
				replayGain = replayGain(trackGain = -6f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = false
			),
			0.001f
		)
	}

	@Test
	fun replayGainVolumeMultiplierLeavesPositiveGainForLoudnessBoostWhenEnabled() {
		assertEquals(
			1f,
			replayGainVolumeMultiplier(
				replayGain = replayGain(trackGain = 6f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = true
			),
			0.0001f
		)
		assertEquals(
			0.501f,
			replayGainVolumeMultiplier(
				replayGain = replayGain(trackGain = -6f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = true
			),
			0.001f
		)
	}

	@Test
	fun replayGainLoudnessBoostMillibelsRequiresEnabledPositiveGain() {
		assertNull(replayGainLoudnessBoostMillibels(null, ReplayGainMode.Track, loudnessBoostEnabled = true))
		assertNull(
			replayGainLoudnessBoostMillibels(
				replayGain = replayGain(trackGain = 6f),
				mode = ReplayGainMode.Off,
				loudnessBoostEnabled = true
			)
		)
		assertNull(
			replayGainLoudnessBoostMillibels(
				replayGain = replayGain(trackGain = 6f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = false
			)
		)
		assertNull(
			replayGainLoudnessBoostMillibels(
				replayGain = replayGain(trackGain = -6f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = true
			)
		)
		assertEquals(
			325,
			replayGainLoudnessBoostMillibels(
				replayGain = replayGain(trackGain = 3.25f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = true
			)
		)
	}

	@Test
	fun replayGainLoudnessBoostMillibelsClampsToSafeAndroidBoostRange() {
		assertEquals(
			2000,
			replayGainLoudnessBoostMillibels(
				replayGain = replayGain(trackGain = 50f),
				mode = ReplayGainMode.Track,
				loudnessBoostEnabled = true
			)
		)
	}

	private fun replayGain(
		trackGain: Float? = null,
		albumGain: Float? = null,
		baseGain: Float? = null,
		fallbackGain: Float? = null
	): DomainReplayGain = DomainReplayGain(
		albumGain = albumGain,
		albumPeak = null,
		trackGain = trackGain,
		trackPeak = null,
		baseGain = baseGain,
		fallbackGain = fallbackGain
	)
}
