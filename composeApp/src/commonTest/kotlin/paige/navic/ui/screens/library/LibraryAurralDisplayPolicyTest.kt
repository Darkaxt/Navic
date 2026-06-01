package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionKind
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionRow
import paige.navic.ui.core.UiState

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

	@Test
	fun libraryAurralCollectionRowsExposeEachConfiguredAurralBucket() {
		val discovery = AurralDiscoverySummary(
			recentlyAdded = listOf(AurralDiscoverArtist(id = "recent-artist", name = "Recently Added Artist")),
			recommendations = (1..12).map { index ->
				AurralDiscoverArtist(id = "seed-$index", name = "Seed $index")
			} + listOf(
				AurralDiscoverArtist(id = "recommended-1", name = "Recommended 1", matchedTags = listOf("soundtrack")),
				AurralDiscoverArtist(id = "recommended-2", name = "Recommended 2", matchedTags = listOf("soundtrack")),
				AurralDiscoverArtist(id = "recommended-3", name = "Recommended 3", matchedTags = listOf("soundtrack")),
				AurralDiscoverArtist(id = "recommended-4", name = "Recommended 4", matchedTags = listOf("soundtrack"))
			),
			basedOn = listOf(AurralDiscoverArtist(id = "based-on", name = "Based On")),
			globalTop = listOf(AurralDiscoverArtist(id = "global", name = "Global")),
			topTags = listOf("soundtrack", "instrumental"),
			topGenres = listOf("soundtrack"),
			recentReleases = listOf(
				AurralAlbumSearchItem(
					id = "release",
					title = "Recent Release",
					artistName = "Artist",
					artistMbid = "artist-mbid"
				)
			)
		)

		val rows = libraryAurralCollectionRows(
			aurralConfigured = true,
			discovery = discovery,
			limit = 8
		)

		assertEquals(
			listOf(
				"RecentlyAddedArtists",
				AurralDiscoveryCollectionKind.RecentReleases,
				AurralDiscoveryCollectionKind.RecommendedArtists,
				AurralDiscoveryCollectionKind.BasedOnArtists,
				AurralDiscoveryCollectionKind.GlobalTopArtists,
				"GenreArtists",
				"TopTags"
			).map { it.toString() },
			rows.map { it.kind.toString() }
		)
		assertEquals(
			listOf("release"),
			(rows[1] as AurralDiscoveryCollectionRow.Albums).albums.map { it.id }
		)
		assertEquals(
			emptyList(),
			libraryAurralCollectionRows(
				aurralConfigured = false,
				discovery = discovery,
				limit = 8
			)
		)
	}

	@Test
	fun libraryShowsAurralLoadingPlaceholderOnlyWithoutCachedRows() {
		assertEquals(
			true,
			libraryAurralLoadingPlaceholderVisible(UiState.Loading(emptyList()))
		)
		assertEquals(
			false,
			libraryAurralLoadingPlaceholderVisible(
				UiState.Loading(
					listOf(
						AurralDiscoveryCollectionRow.Artists(
							kind = AurralDiscoveryCollectionKind.RecommendedArtists,
							artists = listOf(AurralDiscoverArtist(id = "artist", name = "Artist"))
						)
					)
				)
			)
		)
		assertEquals(
			false,
			libraryAurralLoadingPlaceholderVisible(UiState.Success(emptyList()))
		)
	}
}
