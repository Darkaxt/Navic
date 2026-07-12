package paige.navic.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlayerPersistencePolicyTest {
	@Test
	fun progressAndTransientPlaybackMetadataDoNotChangeTheDurableKey() {
		val initial = PlayerUiState(progress = .1f)
		val updated = initial.copy(
			progress = .9f,
			isLoading = true,
			playbackDownloadProgress = .5f,
			playbackBitrate = 320,
			playbackSampleRate = 48_000,
			playbackMimeType = "audio/flac"
		)

		assertEquals(initial.durablePlayerStateKey(), updated.durablePlayerStateKey())
	}

	@Test
	fun queueAndPlaybackControlChangesChangeTheDurableKey() {
		val initial = PlayerUiState(currentIndex = 0, isPaused = false)

		assertNotEquals(
			initial.durablePlayerStateKey(),
			initial.copy(currentIndex = 1).durablePlayerStateKey()
		)
		assertNotEquals(
			initial.durablePlayerStateKey(),
			initial.copy(isPaused = true).durablePlayerStateKey()
		)
		assertNotEquals(
			initial.durablePlayerStateKey(),
			initial.copy(isShuffleEnabled = true).durablePlayerStateKey()
		)
		assertNotEquals(
			initial.durablePlayerStateKey(),
			initial.copy(upcomingIndexes = listOf(2, 1)).durablePlayerStateKey()
		)
	}
}
