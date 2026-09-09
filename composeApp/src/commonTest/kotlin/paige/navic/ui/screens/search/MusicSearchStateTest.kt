package paige.navic.ui.screens.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.ui.core.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MusicSearchStateTest {
	private val artist = DomainArtist("koji", "Koji Kondo", albumCount = 12)
	private val discovered = AurralDiscoverArtist("mbid-koji", "Koji Kondo")

	@Test
	fun libraryArtistIsVisibleWhileBothAurralSearchesAndNavidromeArePending() = runTest {
		var state = MusicSearchState()
		val pending = CompletableDeferred<List<Any>>()
		val job = backgroundScope.launch {
			musicSearchStates(MutableStateFlow("Koji Kondo")) {
				mapOf(
					MusicSearchSource.Library to { listOf(artist) },
					MusicSearchSource.Navidrome to { pending.await() },
					MusicSearchSource.AurralArtists to { pending.await() },
					MusicSearchSource.AurralAlbums to { pending.await() }
				)
			}.collect { state = it }
		}
		runCurrent()
		advanceTimeBy(300)
		runCurrent()
		assertEquals(listOf(artist), state.results)
		assertEquals(3, state.pending.size)
		assertIs<UiState.Success<*>>(state.contentState(SearchCategory.ARTISTS))
		job.cancelAndJoin()
	}

	@Test
	fun aurralArtistIsVisibleBeforeLibraryOrAlbumRequestsComplete() = runTest {
		var state = MusicSearchState()
		val pending = CompletableDeferred<List<Any>>()
		val job = backgroundScope.launch {
			musicSearchStates(MutableStateFlow("Koji Kondo")) {
				mapOf(
					MusicSearchSource.Library to { pending.await() },
					MusicSearchSource.AurralArtists to { listOf(discovered) },
					MusicSearchSource.AurralAlbums to { pending.await() }
				)
			}.collect { state = it }
		}
		runCurrent()
		advanceTimeBy(300)
		runCurrent()
		assertEquals(listOf(discovered), state.results)
		assertIs<UiState.Success<*>>(state.contentState(SearchCategory.ARTISTS))
		job.cancelAndJoin()
	}

	@Test
	fun providerFailurePreservesOtherResultsAndLeavesRetryableError() = runTest {
		var state = MusicSearchState()
		val failure = IllegalStateException("service unavailable")
		val job = backgroundScope.launch {
			musicSearchStates(MutableStateFlow("Koji")) {
				mapOf(
					MusicSearchSource.Library to { listOf(artist) },
					MusicSearchSource.AurralArtists to { throw failure }
				)
			}.collect { state = it }
		}
		runCurrent()
		advanceTimeBy(300)
		runCurrent()
		assertEquals(listOf(artist), state.results)
		assertEquals(failure, state.failure(SearchCategory.ARTISTS)?.error)
		assertFalse(state.isLoading(SearchCategory.ALL))
		assertIs<UiState.Success<*>>(state.contentState(SearchCategory.ARTISTS))
		job.cancelAndJoin()
	}

	@Test
	fun emptyCategoryWaitsOnlyForRelevantSourcesAndFailuresAreNotAbsence() {
		val pending = MusicSearchState("Koji", setOf(MusicSearchSource.AurralAlbums))
		assertIs<UiState.Loading<*>>(pending.contentState(SearchCategory.ALBUMS))
		assertIs<UiState.Success<*>>(pending.contentState(SearchCategory.ARTISTS))
		val failed = pending.copy(pending = emptySet(), completed = mapOf(
			MusicSearchSource.AurralAlbums to UiState.Error(IllegalStateException("failed"))
		))
		assertIs<UiState.Error<*>>(failed.contentState(SearchCategory.ALBUMS))
		assertIs<UiState.Success<*>>(failed.contentState(SearchCategory.ARTISTS))
		val empty = failed.copy(completed = mapOf(MusicSearchSource.AurralAlbums to UiState.Success(emptyList())))
		assertIs<UiState.Success<*>>(empty.contentState(SearchCategory.ALL))
		assertTrue(empty.results.isEmpty())
	}

	@Test
	fun remoteMetadataWinsIdentityCollisionRegardlessOfCacheCompletionOrder() {
		val cached = artist.copy(albumCount = 0)
		for (sources in listOf(
			linkedMapOf(MusicSearchSource.Library to UiState.Success(listOf(cached)), MusicSearchSource.Navidrome to UiState.Success(listOf(artist))),
			linkedMapOf(MusicSearchSource.Navidrome to UiState.Success(listOf(artist)), MusicSearchSource.Library to UiState.Success(listOf(cached)))
		)) {
			assertEquals(listOf(artist), MusicSearchState("Koji", completed = sources).results)
		}
	}

	@Test
	fun newInputCancelsOldRequestBeforeTheNewDebounceExpiresEvenIfProviderSwallowsCancellation() = runTest {
		val queries = MutableStateFlow("old")
		val cancelled = CompletableDeferred<Unit>()
		val states = mutableListOf<MusicSearchState>()
		var newRequests = 0
		val job = backgroundScope.launch {
			musicSearchStates(queries) { query ->
				mapOf(MusicSearchSource.Library to {
					if (query == "old") {
						try { awaitCancellation() } catch (_: CancellationException) { cancelled.complete(Unit) }
						listOf(artist.copy(id = "old"))
					} else {
						newRequests++
						listOf(artist)
					}
				})
			}.collect { states += it }
		}
		runCurrent()
		advanceTimeBy(300)
		runCurrent()
		queries.value = "Koji Kondo"
		runCurrent()
		assertTrue(cancelled.isCompleted)
		assertEquals("Koji Kondo", states.last().query)
		assertEquals(0, newRequests)
		assertTrue(states.all { state -> state.results.none { (it as DomainArtist).id == "old" } })
		advanceTimeBy(300)
		runCurrent()
		assertEquals(listOf(artist), states.last().results)
		job.cancelAndJoin()
	}

	@Test
	fun cancelledCleanupDoesNotBlockNewQueryOrClearButRemainsOwned() = runTest {
		val queries = MutableStateFlow("old")
		val releaseCleanup = CompletableDeferred<Unit>()
		val cleanupStarted = CompletableDeferred<Unit>()
		var state = MusicSearchState()
		val job = backgroundScope.launch {
			musicSearchStates(queries) { query ->
				mapOf(MusicSearchSource.Library to {
					if (query == "old") {
						try { awaitCancellation() } finally {
							withContext(NonCancellable) {
								cleanupStarted.complete(Unit)
								releaseCleanup.await()
							}
						}
					} else listOf(artist)
				})
			}.collect { state = it }
		}
		try {
			runCurrent()
			advanceTimeBy(300)
			runCurrent()
			queries.value = "Koji Kondo"
			runCurrent()
			assertTrue(cleanupStarted.isCompleted)
			assertEquals("Koji Kondo", state.query)
			advanceTimeBy(300)
			runCurrent()
			assertEquals(listOf(artist), state.results)
			queries.value = ""
			runCurrent()
			assertEquals(MusicSearchState(), state)
			job.cancel()
			runCurrent()
			assertFalse(job.isCompleted, "The old cleanup must still be lifecycle-owned")
		} finally {
			releaseCleanup.complete(Unit)
			job.cancelAndJoin()
		}
	}

	@Test
	fun disabledOrOfflineSourceIsRecheckedAfterDebounceWithoutNetworkCalls() = runTest {
		var eligible = true
		var calls = 0
		var state = MusicSearchState()
		val job = backgroundScope.launch {
			musicSearchStates(MutableStateFlow("Koji")) {
				mapOf(
					MusicSearchSource.Library to { listOf(artist) },
					MusicSearchSource.Navidrome to eligibleMusicSearchRequest({ eligible }) { calls++; emptyList() },
					MusicSearchSource.AurralArtists to eligibleMusicSearchRequest({ eligible }) { calls++; emptyList() }
				)
			}.collect { state = it }
		}
		runCurrent()
		eligible = false
		advanceTimeBy(300)
		runCurrent()
		assertEquals(0, calls)
		assertEquals(listOf(artist), state.results)
		assertTrue(state.pending.isEmpty())
		job.cancelAndJoin()
	}

	@Test
	fun clearingQueryCancelsPendingWorkAndResetsHistorySurfaceImmediately() = runTest {
		val queries = MutableStateFlow("Koji")
		var state = MusicSearchState()
		val cancelled = CompletableDeferred<Unit>()
		val job = backgroundScope.launch {
			musicSearchStates(queries) {
				mapOf(MusicSearchSource.Library to {
					try { awaitCancellation() } finally { cancelled.complete(Unit) }
				})
			}.collect { state = it }
		}
		runCurrent()
		advanceTimeBy(300)
		runCurrent()
		queries.value = " "
		runCurrent()
		assertTrue(cancelled.isCompleted)
		assertEquals(MusicSearchState(), state)
		job.cancelAndJoin()
	}

	@Test
	fun sameQueryCanBeRetriedAfterFailure() = runTest {
		val queries = MutableSharedFlow<String>()
		var state = MusicSearchState()
		var attempts = 0
		val job = backgroundScope.launch {
			musicSearchStates(queries) {
				mapOf(MusicSearchSource.Library to {
					attempts++
					if (attempts == 1) error("failed")
					listOf(artist)
				})
			}.collect { state = it }
		}
		runCurrent()
		queries.emit("Koji")
		advanceTimeBy(300)
		runCurrent()
		assertIs<UiState.Error<*>>(state.contentState(SearchCategory.ALL))
		queries.emit("Koji")
		advanceTimeBy(300)
		runCurrent()
		assertEquals(2, attempts)
		assertEquals(listOf(artist), state.results)
		job.cancelAndJoin()
	}
}
