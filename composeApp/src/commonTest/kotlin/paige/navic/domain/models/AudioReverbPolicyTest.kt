package paige.navic.domain.models

import paige.navic.domain.models.settings.AudioReverbPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioReverbPolicyTest {
	@Test
	fun audioReverbUsesAndroidPresetValues() {
		assertEquals(0.toShort(), audioReverbPresetValue(AudioReverbPreset.Off))
		assertEquals(1.toShort(), audioReverbPresetValue(AudioReverbPreset.SmallRoom))
		assertEquals(2.toShort(), audioReverbPresetValue(AudioReverbPreset.MediumRoom))
		assertEquals(3.toShort(), audioReverbPresetValue(AudioReverbPreset.LargeRoom))
		assertEquals(4.toShort(), audioReverbPresetValue(AudioReverbPreset.MediumHall))
		assertEquals(5.toShort(), audioReverbPresetValue(AudioReverbPreset.LargeHall))
		assertEquals(6.toShort(), audioReverbPresetValue(AudioReverbPreset.Plate))
	}

	@Test
	fun audioReverbRequiresPresetAndAudioSession() {
		assertFalse(shouldEnableAudioReverb(AudioReverbPreset.Off, audioSessionId = 12))
		assertFalse(shouldEnableAudioReverb(AudioReverbPreset.SmallRoom, audioSessionId = null))
		assertFalse(shouldEnableAudioReverb(AudioReverbPreset.SmallRoom, audioSessionId = 0))
		assertTrue(shouldEnableAudioReverb(AudioReverbPreset.SmallRoom, audioSessionId = 12))
	}
}
