package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BassBoostPolicyTest {
	@Test
	fun bassBoostStrengthPermilleUsesAndroidRange() {
		assertEquals(0.toShort(), bassBoostStrengthPermille(-100))
		assertEquals(0.toShort(), bassBoostStrengthPermille(0))
		assertEquals(500.toShort(), bassBoostStrengthPermille(500))
		assertEquals(1000.toShort(), bassBoostStrengthPermille(1200))
	}

	@Test
	fun bassBoostRequiresEnabledPreferenceAndAudioSession() {
		assertFalse(shouldEnableBassBoost(bassBoostEnabled = false, audioSessionId = 12))
		assertFalse(shouldEnableBassBoost(bassBoostEnabled = true, audioSessionId = null))
		assertFalse(shouldEnableBassBoost(bassBoostEnabled = true, audioSessionId = 0))
		assertTrue(shouldEnableBassBoost(bassBoostEnabled = true, audioSessionId = 12))
	}
}
