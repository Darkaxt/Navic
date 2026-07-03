package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackErrorPolicyTest {
	@Test
	fun advancesOnlyWhenSkipOnErrorIsEnabledAndQueueHasNextItem() {
		assertTrue(shouldSkipMediaAfterPlaybackError(skipMediaOnError = true, hasNextMediaItem = true))
		assertFalse(shouldSkipMediaAfterPlaybackError(skipMediaOnError = false, hasNextMediaItem = true))
		assertFalse(shouldSkipMediaAfterPlaybackError(skipMediaOnError = true, hasNextMediaItem = false))
	}

	@Test
	fun restoredPausedPlaybackErrorsStaySilentUntilUserRequestsPlayback() {
		assertFalse(
			shouldHandlePlaybackErrorVisibly(
				playWhenReady = false,
				isUiPaused = true,
				hasPendingSourceErrorRecovery = false
			)
		)

		assertTrue(
			shouldHandlePlaybackErrorVisibly(
				playWhenReady = true,
				isUiPaused = true,
				hasPendingSourceErrorRecovery = false
			)
		)
		assertTrue(
			shouldHandlePlaybackErrorVisibly(
				playWhenReady = false,
				isUiPaused = false,
				hasPendingSourceErrorRecovery = false
			)
		)
		assertTrue(
			shouldHandlePlaybackErrorVisibly(
				playWhenReady = false,
				isUiPaused = true,
				hasPendingSourceErrorRecovery = true
			)
		)
	}
}
