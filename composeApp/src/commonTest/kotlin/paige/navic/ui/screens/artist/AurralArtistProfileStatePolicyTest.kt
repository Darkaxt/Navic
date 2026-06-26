package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.domain.models.AurralArtistExternalLink
import paige.navic.domain.models.AurralArtistOwnershipAlbumRow
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.DomainArtist
import paige.navic.ui.screens.artist.viewmodels.ArtistState

class AurralArtistProfileStatePolicyTest {
	@Test
	fun verifiedUnmonitoredArtistKeepsMonitorActionVisibleAndEnabled() {
		val state = aurralArtistProfileUiState(
			state = artistState(aurralMonitored = false),
			aurralEnabled = true,
			monitoringInAurral = false,
			monitorPendingInAurral = false
		)

		assertEquals(AurralArtistMonitorUiState.VerifiedUnmonitored, state.monitor)
		assertTrue(state.monitorActionVisible)
		assertTrue(state.monitorActionEnabled)
	}

	@Test
	fun authOrConfigErrorKeepsKnownMbidActionVisibleButDisabled() {
		val state = aurralArtistProfileUiState(
			state = artistState(
				aurralMonitored = null,
				aurralArtistName = null,
				aurralError = "HTTP 401 Unauthorized"
			),
			aurralEnabled = true,
			monitoringInAurral = false,
			monitorPendingInAurral = false
		)

		assertEquals(AurralArtistMonitorUiState.Error, state.monitor)
		assertEquals(AurralArtistSectionUiState.Error, state.profile)
		assertTrue(state.monitorActionVisible)
		assertFalse(state.monitorActionEnabled)
	}

	@Test
	fun aurralFirstStateSeparatesProfileOwnershipPreviewSimilarAndRequests() {
		val state = aurralArtistProfileUiState(
			state = artistState(
				aurralMonitored = true,
				aurralArtistBio = "English score composer.",
				aurralArtistGenres = listOf("Soundtrack"),
				aurralArtistExternalLinks = listOf(
					AurralArtistExternalLink("musicbrainz", "https://musicbrainz.org/artist/52bb713d-b0c9-4bf6-9f58-392388d5cc11")
				),
				aurralOwnedOrPartialAlbums = listOf(ownershipRow("How to Train Your Dragon")),
				aurralMissingReleaseGroups = listOf(ownershipRow("Hubris", AurralOwnershipStatus.Missing)),
				aurralPreviewTracks = listOf(AurralPreviewTrack(id = "preview-1", title = "Test Drive")),
				aurralSimilarArtists = listOf(
					AurralSimilarArtistRow(
						artist = AurralSimilarArtist(id = "similar", name = "Hans Zimmer"),
						localArtistId = null,
						inLibrary = false,
						matchPercent = 82
					)
				),
				aurralAlbumRequests = listOf(
					AurralAlbumRequest(albumMbid = "hubris", albumName = "Hubris", status = "processing")
				)
			),
			aurralEnabled = true,
			monitoringInAurral = false,
			monitorPendingInAurral = false
		)

		assertEquals(AurralArtistSectionUiState.Ready, state.profile)
		assertEquals(AurralArtistSectionUiState.Ready, state.ownership)
		assertEquals(AurralArtistSectionUiState.Ready, state.previewTracks)
		assertEquals(AurralArtistSectionUiState.Ready, state.similarArtists)
		assertEquals(AurralArtistSectionUiState.Ready, state.requests)
		assertEquals(AurralArtistMonitorUiState.VerifiedMonitored, state.monitor)
	}

	@Test
	fun cachedProfileRowsRemainReadyWhileFreshRefreshIsLoading() {
		val state = aurralArtistProfileUiState(
			state = artistState(
				aurralLoading = true,
				aurralArtistName = "John Powell",
				aurralArtistBio = "Cached biography.",
				aurralOwnedOrPartialAlbums = listOf(ownershipRow("How to Train Your Dragon")),
				aurralMissingReleaseGroups = listOf(ownershipRow("Hubris", AurralOwnershipStatus.Missing))
			),
			aurralEnabled = true,
			monitoringInAurral = false,
			monitorPendingInAurral = false
		)

		assertEquals("John Powell", state.displayName)
		assertEquals(AurralArtistSectionUiState.Ready, state.profile)
		assertEquals(AurralArtistSectionUiState.Ready, state.ownership)
	}

	private fun artistState(
		aurralMonitored: Boolean? = null,
		aurralLoading: Boolean = false,
		aurralError: String? = null,
		aurralArtistName: String? = "John Powell",
		aurralArtistBio: String? = null,
		aurralArtistGenres: List<String> = emptyList(),
		aurralArtistExternalLinks: List<AurralArtistExternalLink> = emptyList(),
		aurralOwnedOrPartialAlbums: List<AurralArtistOwnershipAlbumRow> = emptyList(),
		aurralMissingReleaseGroups: List<AurralArtistOwnershipAlbumRow> = emptyList(),
		aurralPreviewTracks: List<AurralPreviewTrack> = emptyList(),
		aurralSimilarArtists: List<AurralSimilarArtistRow> = emptyList(),
		aurralAlbumRequests: List<AurralAlbumRequest> = emptyList()
	) = ArtistState(
		artist = DomainArtist(
			id = "john-powell",
			name = "John Powell",
			musicBrainzId = "52bb713d-b0c9-4bf6-9f58-392388d5cc11"
		),
		albums = emptyList(),
		topSongs = emptyList(),
		aurralMonitored = aurralMonitored,
		aurralArtistMbid = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
		aurralArtistName = aurralArtistName,
		aurralArtistBio = aurralArtistBio,
		aurralArtistGenres = aurralArtistGenres,
		aurralArtistExternalLinks = aurralArtistExternalLinks,
		aurralOwnedOrPartialAlbums = aurralOwnedOrPartialAlbums,
		aurralMissingReleaseGroups = aurralMissingReleaseGroups,
		aurralPreviewTracks = aurralPreviewTracks,
		aurralSimilarArtists = aurralSimilarArtists,
		aurralAlbumRequests = aurralAlbumRequests,
		aurralLoading = aurralLoading,
		aurralError = aurralError
	)

	private fun ownershipRow(
		title: String,
		ownershipStatus: AurralOwnershipStatus = AurralOwnershipStatus.Partial
	) = AurralArtistOwnershipAlbumRow(
		releaseGroup = null,
		localAlbum = null,
		title = title,
		year = "2010",
		coverUrl = null,
		requestStatus = null,
		requestable = ownershipStatus == AurralOwnershipStatus.Missing,
		ownershipStatus = ownershipStatus
	)
}
