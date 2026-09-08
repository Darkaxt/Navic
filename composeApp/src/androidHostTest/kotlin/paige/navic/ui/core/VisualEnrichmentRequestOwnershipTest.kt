package paige.navic.ui.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.repositories.fetchFirstAvailableLyrics
import paige.navic.ui.screens.artist.viewmodels.ArtistState
import paige.navic.ui.screens.artist.viewmodels.withAurralCoreFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VisualEnrichmentRequestOwnershipTest {
	@Test
	fun siblingCompletionsRebaseOnCurrentStateInEitherOrder() = runTest {
		for (previewFirst in listOf(true, false)) {
			val owner = EnrichmentRequestOwner()
			val state = MutableStateFlow(artistState().copy(aurralSimilarArtistsLoading = true))
			val previewReady = CompletableDeferred<Unit>()
			val similarReady = CompletableDeferred<Unit>()
			owner.launch(this) {
				launch {
					previewReady.await()
					owner.update(state) { current ->
						current.copy(
							aurralPreviewTracksLoading = false,
							aurralPreviewTracks = listOf(AurralPreviewTrack("track", "Preview"))
						)
					}
				}
				launch {
					similarReady.await()
					owner.update(state) { current ->
						current.copy(aurralSimilarArtistsLoading = false, aurralSimilarArtistsError = "similar failed")
					}
				}
			}
			runCurrent()
			if (previewFirst) previewReady.complete(Unit) else similarReady.complete(Unit)
			runCurrent()
			if (previewFirst) similarReady.complete(Unit) else previewReady.complete(Unit)
			runCurrent()
			assertFalse(state.value.aurralPreviewTracksLoading)
			assertFalse(state.value.aurralSimilarArtistsLoading)
			assertEquals(listOf(AurralPreviewTrack("track", "Preview")), state.value.aurralPreviewTracks)
			assertEquals("similar failed", state.value.aurralSimilarArtistsError)
		}
	}

	@Test
	fun hidingLyricsStopsFallbackAndResumeCanFinishTheSameSong() = runTest {
		val owner = EnrichmentRequestOwner()
		val firstProvider = CompletableDeferred<Unit>()
		val calls = mutableListOf<Int>()
		var result: String? = null
		owner.launch(this) {
			val lyrics = fetchFirstAvailableLyrics<Int, String>(listOf(1, 2)) { provider ->
				calls += provider
				withContext(NonCancellable) { firstProvider.await() }
				null
			}
			owner.commit { result = lyrics }
		}
		runCurrent()
		assertTrue(owner.cancel())
		assertEquals(listOf(1), calls)
		assertNull(result)
		owner.launch(this) { owner.commit { result = "same-song lyrics" } }
		runCurrent()
		firstProvider.complete(Unit)
		runCurrent()
		assertEquals(listOf(1), calls)
		assertEquals("same-song lyrics", result)
		assertFalse(owner.cancel())
		assertEquals("same-song lyrics", result)
	}

	@Test
	fun oldFailedCoreCannotTerminateNewRefreshEvenIfCancellationIsSwallowed() = runTest {
		val owner = EnrichmentRequestOwner()
		val oldCore = CompletableDeferred<Unit>()
		val newCore = CompletableDeferred<Unit>()
		var state = artistState()
		owner.launch(this) {
			withContext(NonCancellable) {
				oldCore.await()
				owner.commit { state = state.withAurralCoreFailure("obsolete failure") }
			}
		}
		runCurrent()
		owner.launch(this) {
			owner.commit { state = state.copy(aurralArtistBio = "new cached profile") }
			newCore.await()
			owner.commit { state = state.copy(aurralProfileLoading = false) }
		}
		runCurrent()
		oldCore.complete(Unit)
		runCurrent()
		assertTrue(state.aurralProfileLoading)
		assertTrue(state.aurralPreviewTracksLoading)
		assertEquals("new cached profile", state.aurralArtistBio)
		assertNull(state.aurralError)
		newCore.complete(Unit)
		runCurrent()
		assertFalse(state.aurralProfileLoading)
	}

	@Test
	fun childSectionPatchesCannotEscapeSupersededRefreshOwnership() = runTest {
		val owner = EnrichmentRequestOwner()
		val oldSection = CompletableDeferred<Unit>()
		val patches = mutableListOf<String>()
		owner.launch(this) {
			launch {
				withContext(NonCancellable) {
					oldSection.await()
					owner.commit { patches += "old preview" }
					owner.commit { patches += "old cover" }
					owner.commit { patches += "old ownership" }
				}
			}
		}
		runCurrent()
		owner.launch(this) { owner.commit { patches += "new core" } }
		runCurrent()
		oldSection.complete(Unit)
		runCurrent()
		assertEquals(listOf("new core"), patches)
	}

	@Test
	fun disablingBeforeDispatchPreventsWorkAndCurrentFailureStillTerminatesSections() = runTest {
		val owner = EnrichmentRequestOwner()
		owner.launch(this) { error("Hidden request started") }
		owner.cancel()
		runCurrent()
		var state = artistState()
		owner.launch(this) { owner.commit { state = state.withAurralCoreFailure("current failure") } }
		runCurrent()
		assertFalse(state.aurralProfileLoading)
		assertFalse(state.aurralPreviewTracksLoading)
		assertEquals("current failure", state.aurralError)
	}

	private fun artistState() = ArtistState(
		artist = DomainArtist("artist", "Artist"), albums = emptyList(), topSongs = emptyList(),
		aurralProfileLoading = true, aurralPreviewTracksLoading = true
	)
}
