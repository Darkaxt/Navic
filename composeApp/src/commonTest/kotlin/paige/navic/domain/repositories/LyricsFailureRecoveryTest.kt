package paige.navic.domain.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.nowPlaying.viewmodels.shouldStartLyricsLookup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LyricsFailureRecoveryTest {
	@Test
	fun providerFailureRetriesOnSameSongResumeThenStopsAfterSuccess() = runBlocking {
		val failure = IllegalStateException("Provider unavailable")
		var calls = 0
		val thrown = assertFailsWith<IllegalStateException> {
			fetchFirstAvailableLyrics<Int, String>(listOf(1, 2)) { calls++; throw failure }
		}
		assertSame(failure, thrown)
		val failed: UiState<Boolean> = UiState.Error(thrown, false)
		assertFalse(lookup(failed, resuming = false))
		assertFalse(lookup(failed, resuming = true, active = false))
		assertEquals(2, calls)
		assertTrue(lookup(failed, resuming = true))
		val recovered = fetchFirstAvailableLyrics(listOf(1, 2)) { calls++; "lyrics" }
		assertEquals("lyrics", recovered)
		assertEquals(3, calls)
		assertFalse(lookup(UiState.Success(true), resuming = true))
	}

	@Test
	fun genuineAbsenceDoesNotPollOnProgressOrResume() = runBlocking {
		var calls = 0
		assertNull(fetchFirstAvailableLyrics<Int, String>(listOf(1, 2)) { calls++; null })
		assertEquals(2, calls)
		repeat(3) {
			assertFalse(lookup(UiState.Success(false), resuming = false))
			assertFalse(lookup(UiState.Success(false), resuming = true))
		}
	}

	@Test
	fun oneEmptyProviderDoesNotEraseAnotherProvidersUnresolvedFailure() = runBlocking {
		assertFailsWith<IllegalStateException> {
			fetchFirstAvailableLyrics<Int, String>(listOf(1, 2)) {
				if (it == 1) error("Transport failed") else null
			}
		}
		Unit
	}

	@Test
	fun cachedFallbackSurvivesFailedProvidersButFreshLyricsWin() = runBlocking {
		assertEquals("cached", fetchFirstAvailableLyrics(listOf(1), "cached") { error("Unavailable") })
		assertEquals("fresh", fetchFirstAvailableLyrics(listOf(1), "cached") { "fresh" })
	}

	@Test
	fun cancellationDoesNotTryAnotherProviderOrReturnCachedLyrics() = runBlocking {
		val cancelled = CancellationException("Cancelled lookup")
		var calls = 0
		assertSame(cancelled, assertFailsWith<CancellationException> {
			fetchFirstAvailableLyrics(listOf(1, 2), "cached") { calls++; throw cancelled }
		})
		assertEquals(1, calls)
	}

	@Test
	fun hiddenUnstartedLookupRunsWhenVisible() {
		assertFalse(lookup(UiState.Loading(false), resuming = false, active = false, songId = null))
		assertTrue(lookup(UiState.Loading(false), resuming = true, songId = null))
	}

	private fun lookup(
		state: UiState<Boolean>,
		resuming: Boolean,
		active: Boolean = true,
		songId: String? = "song"
	) = shouldStartLyricsLookup(
		currentSongId = songId,
		requestedSongId = "song",
		lyricsState = state,
		previousProgress = 0.2f,
		currentProgress = 0.3f,
		resuming = resuming,
		canRequest = active
	)
}
