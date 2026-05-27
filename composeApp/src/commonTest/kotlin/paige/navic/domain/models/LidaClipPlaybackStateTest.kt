package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LidaClipPlaybackStateTest {
	@Test
	fun playbackErrorMessageUsesCodeBeforeMessageAndFallsBack() {
		assertEquals(
			"Video playback failed: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
			lidaClipPlaybackErrorMessage(
				errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
				message = "timeout"
			)
		)
		assertEquals(
			"Video playback failed: timeout",
			lidaClipPlaybackErrorMessage(
				errorCodeName = null,
				message = " timeout "
			)
		)
		assertEquals(
			"Video playback failed",
			lidaClipPlaybackErrorMessage(
				errorCodeName = null,
				message = null
			)
		)
	}

	@Test
	fun playbackRetryClearsErrorAndAdvancesRetryKey() {
		val failed = LidaClipPlaybackState()
			.onError("Video playback failed")

		assertEquals("Video playback failed", failed.errorMessage)
		assertEquals(0, failed.retryKey)

		val retrying = failed.onRetry()

		assertNull(retrying.errorMessage)
		assertEquals(1, retrying.retryKey)
		assertNull(retrying.onReady().errorMessage)
	}
}
