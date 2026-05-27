package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemEqualizerPolicyTest {
	@Test
	fun systemEqualizerUsesOnlyPositiveAudioSessionIds() {
		assertNull(systemEqualizerAudioSessionId(null))
		assertNull(systemEqualizerAudioSessionId(0))
		assertNull(systemEqualizerAudioSessionId(-1))
		assertEquals(42, systemEqualizerAudioSessionId(42))
	}
}
