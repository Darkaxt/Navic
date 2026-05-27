package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioFadePolicyTest {
	@Test
	fun audioFadeDurationUsesPositivePreferenceValuesOnly() {
		assertEquals(0L, audioFadeDurationMs(-250))
		assertEquals(0L, audioFadeDurationMs(0))
		assertEquals(500L, audioFadeDurationMs(500))
	}

	@Test
	fun audioFadeRunsOnlyWhenEnabledAndPlaybackStateWillChange() {
		assertFalse(shouldFadePlaybackCommand(audioFadeDurationMs = 0, alreadyInTargetState = false))
		assertFalse(shouldFadePlaybackCommand(audioFadeDurationMs = 500, alreadyInTargetState = true))
		assertTrue(shouldFadePlaybackCommand(audioFadeDurationMs = 500, alreadyInTargetState = false))
	}
}
