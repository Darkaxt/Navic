package paige.navic.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.serialization.json.Json
import paige.navic.domain.models.restoredShuffleOrder

class PlayerPersistencePolicyTest {
	@Test
	fun taggedSavedQueueIsOnlyRestoredByItsOwner() {
		val state = PlayerUiState(playbackOwnerId = "A", currentIndex = 2, isShuffleEnabled = true)
		val saved = Json.decodeFromString<PlayerUiState>(Json.encodeToString(state))
		assertEquals(state, saved.forPlaybackOwner("A", null))
		assertEquals(null, saved.forPlaybackOwner("B", "B"))
		assertEquals(null, saved.forPlaybackOwner(null, "A"))
		assertNotEquals(state.durablePlayerStateKey(), state.copy(playbackOwnerId = "B").durablePlayerStateKey())
	}

	@Test
	fun untaggedLegacyQueueCannotBeAssignedToAnUnrelatedLogin() {
		val legacy = Json.decodeFromString<PlayerUiState>("""{"currentIndex":2}""")
		assertEquals("A", legacy.forPlaybackOwner("A", "A")?.playbackOwnerId)
		assertEquals(null, legacy.forPlaybackOwner("B", "A"))
		assertEquals(null, legacy.forPlaybackOwner("B", null))
	}

	@Test
	fun repeatOneRoundTripKeepsUnderlyingShuffleTraversal() {
		val order = listOf(3, 0, 2, 1)
		for (repeatMode in listOf(0, 1, 2)) {
			val state = PlayerUiState(
				currentIndex = 0,
				isShuffleEnabled = true,
				repeatMode = repeatMode,
				upcomingIndexes = if (repeatMode == 1) listOf(0) else listOf(2, 1),
				shuffleOrder = order
			)
			val restored = Json.decodeFromString<PlayerUiState>(Json.encodeToString(state))
			assertEquals(order, restoredShuffleOrder(4, restored.currentIndex, restored.upcomingIndexes, restored.shuffleOrder))
			assertEquals(listOf(2, 1), restored.shuffleOrder!!.dropWhile { it != 0 }.drop(1))
		}
	}

	@Test
	fun underlyingOrderChangesAreDurableEvenWhenRepeatOneProjectionDoesNotChange() {
		val state = PlayerUiState(currentIndex = 0, upcomingIndexes = listOf(0), repeatMode = 1,
			shuffleOrder = listOf(2, 0, 1))
		assertNotEquals(state.durablePlayerStateKey(), state.copy(shuffleOrder = listOf(1, 0, 2)).durablePlayerStateKey())
	}

	@Test
	fun legacyRepeatOneWithoutTraversalRemainsReadableAndRejectsInvalidProjection() {
		val state = Json.decodeFromString<PlayerUiState>("""{"currentIndex":1,"upcomingIndexes":[1],"repeatMode":1,"isShuffleEnabled":true}""")
		assertEquals(null, state.shuffleOrder)
		assertEquals(null, restoredShuffleOrder(3, state.currentIndex, state.upcomingIndexes, state.shuffleOrder))
	}

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
