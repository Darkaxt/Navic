package paige.navic.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerRestorePolicyTest {
	@Test
	fun persistentQueueDisabledSkipsRestoringSavedPlayerState() {
		assertNull(
			restoredPlayerStateForPreferences(
				restoredState = PlayerUiState(progress = .5f),
				persistentQueue = false,
				resumePlaybackOnStartup = true
			)
		)
	}

	@Test
	fun persistentQueueRestoresPausedQueueByDefault() {
		val restored = restoredPlayerStateForPreferences(
			restoredState = PlayerUiState(isPaused = false, isLoading = true, progress = .25f),
			persistentQueue = true,
			resumePlaybackOnStartup = false
		)

		assertEquals(.25f, restored?.progress)
		assertTrue(restored!!.isPaused)
		assertFalse(restored.isLoading)
	}

	@Test
	fun resumeOnStartupRestoresQueueInPlayingState() {
		val restored = restoredPlayerStateForPreferences(
			restoredState = PlayerUiState(isPaused = true, isLoading = true),
			persistentQueue = true,
			resumePlaybackOnStartup = true
		)

		assertFalse(restored!!.isPaused)
		assertFalse(restored.isLoading)
	}
}
