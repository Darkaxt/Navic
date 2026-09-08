package paige.navic.ui.screens.artist

import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.ui.screens.artist.viewmodels.ArtistState
import paige.navic.ui.screens.artist.viewmodels.withAurralCoreFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralCoreFailureStateTest {
	@Test
	fun failedCoreTerminatesEveryBlockedSectionWithoutChangingIndependentWork() {
		val failed = loadingState().withAurralCoreFailure("Core unavailable")
		assertTerminalFailure(failed)
		assertTrue(failed.lastFmLoading)
		assertEquals(listOf(DomainArtist("similar", "Local similar")), failed.similarArtists)
		val ui = aurralArtistProfileUiState(failed, true, false, false)
		assertEquals(AurralArtistSectionUiState.Error, ui.profile)
		assertEquals(AurralArtistSectionUiState.Error, ui.ownership)
		assertEquals(AurralArtistSectionUiState.Error, ui.previewTracks)
		assertEquals(AurralArtistSectionUiState.Error, ui.similarArtists)
		assertEquals(AurralArtistSectionUiState.Error, ui.requests)
	}

	@Test
	fun failedRefreshRetainsCachedProfileAndMonitoring() {
		val cached = loadingState().copy(
			aurralArtistBio = "Cached biography",
			aurralArtistGenres = listOf("Rock"),
			aurralMonitored = true,
			aurralArtistImageUrl = "https://example.test/artist.jpg",
			aurralPreviewTracks = listOf(AurralPreviewTrack("track", "Cached preview")),
			aurralAlbumRequests = listOf(AurralAlbumRequest(albumName = "Cached request")),
			aurralSimilarArtists = listOf(AurralSimilarArtistRow(
				artist = AurralSimilarArtist("similar", "Cached similar"), localArtistId = null,
				inLibrary = false, matchPercent = 80
			))
		)
		val failed = cached.withAurralCoreFailure("Core unavailable")
		assertTerminalFailure(failed)
		assertEquals(cached.aurralArtistBio, failed.aurralArtistBio)
		assertEquals(cached.aurralArtistGenres, failed.aurralArtistGenres)
		assertEquals(cached.aurralArtistImageUrl, failed.aurralArtistImageUrl)
		assertEquals(cached.aurralMonitored, failed.aurralMonitored)
		assertEquals(cached.aurralPreviewTracks, failed.aurralPreviewTracks)
		assertEquals(cached.aurralAlbumRequests, failed.aurralAlbumRequests)
		assertEquals(cached.aurralSimilarArtists, failed.aurralSimilarArtists)
		val ui = aurralArtistProfileUiState(failed, true, false, false)
		assertEquals(AurralArtistSectionUiState.Ready, ui.profile)
		assertEquals(AurralArtistSectionUiState.Ready, ui.previewTracks)
		assertEquals(AurralArtistSectionUiState.Ready, ui.similarArtists)
		assertEquals(AurralArtistSectionUiState.Ready, ui.requests)
	}

	private fun assertTerminalFailure(state: ArtistState) {
		assertFalse(state.aurralLoading)
		assertFalse(state.aurralProfileLoading)
		assertFalse(state.aurralOwnershipLoading)
		assertFalse(state.aurralPreviewTracksLoading)
		assertFalse(state.aurralSimilarArtistsLoading)
		assertFalse(state.aurralRequestsLoading)
		assertEquals("Core unavailable", state.aurralError)
		assertEquals("Core unavailable", state.aurralProfileError)
		assertEquals("Core unavailable", state.aurralOwnershipError)
		assertEquals("Core unavailable", state.aurralPreviewTracksError)
		assertEquals("Core unavailable", state.aurralSimilarArtistsError)
		assertEquals("Core unavailable", state.aurralRequestsError)
	}

	private fun loadingState() = ArtistState(
		artist = DomainArtist("artist", "Artist"), albums = emptyList(), topSongs = emptyList(),
		similarArtists = listOf(DomainArtist("similar", "Local similar")),
		aurralLoading = true, aurralProfileLoading = true, aurralOwnershipLoading = true,
		aurralPreviewTracksLoading = true, aurralSimilarArtistsLoading = true,
		aurralRequestsLoading = true, lastFmLoading = true
	)
}
