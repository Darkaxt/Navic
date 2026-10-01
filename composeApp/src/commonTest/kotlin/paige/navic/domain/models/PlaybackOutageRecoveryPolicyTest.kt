package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackOutageRecoveryPolicyTest {
	@Test
	fun healthyLocalPlaybackIsNotRestartedByRepeatedOutageObservations() {
		assertTrue(shouldKeepLocalPlaybackDuringOutage(true, false, false))
		assertFalse(shouldKeepLocalPlaybackDuringOutage(true, true, false))
		assertFalse(shouldKeepLocalPlaybackDuringOutage(true, false, true))
		assertFalse(shouldKeepLocalPlaybackDuringOutage(false, false, false))
	}

	@Test
	fun zeroPlayerPositionDoesNotReuseThePreviousTracksProgress() {
		assertEquals(0L, playbackRecoveryPositionMs(0L, true, 242_000L))
		assertEquals(51_000L, playbackRecoveryPositionMs(51_000L, false, 242_000L))
		assertEquals(0L, playbackRecoveryPositionMs(-1L, false, 242_000L))
		assertEquals(242_000L, playbackRecoveryPositionMs(-1L, true, 242_000L))
	}
}
