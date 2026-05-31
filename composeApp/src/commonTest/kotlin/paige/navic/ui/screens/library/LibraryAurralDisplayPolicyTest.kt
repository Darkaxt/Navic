package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary

class LibraryAurralDisplayPolicyTest {
	@Test
	fun libraryAlbumRowsUseAurralRequestsOnlyWhenHubIsConfigured() {
		val requests = listOf(
			AurralAlbumRequest(
				albumMbid = "album-mbid",
				albumName = "Album",
				artistName = "Artist",
				status = "downloading"
			)
		)

		assertEquals(
			requests,
			libraryAlbumAurralRequests(
				showAurralHub = true,
				requests = requests
			)
		)
		assertEquals(
			emptyList(),
			libraryAlbumAurralRequests(
				showAurralHub = false,
				requests = requests
			)
		)
	}

	@Test
	fun libraryDiscoverRowsUseAurralDiscoveryOnlyWhenConfigured() {
		val discovery = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(id = "artist-1", name = "Artist 1"),
				AurralDiscoverArtist(id = "artist-2", name = "Artist 2")
			),
			globalTop = listOf(
				AurralDiscoverArtist(id = "artist-2", name = "Duplicate Artist 2"),
				AurralDiscoverArtist(id = "artist-3", name = "Artist 3")
			)
		)

		assertEquals(
			listOf("artist-1", "artist-2", "artist-3"),
			libraryAurralDiscoverArtists(
				aurralConfigured = true,
				discovery = discovery,
				limit = 4
			).map { it.id }
		)
		assertEquals(
			emptyList(),
			libraryAurralDiscoverArtists(
				aurralConfigured = false,
				discovery = discovery,
				limit = 4
			)
		)
	}
}
