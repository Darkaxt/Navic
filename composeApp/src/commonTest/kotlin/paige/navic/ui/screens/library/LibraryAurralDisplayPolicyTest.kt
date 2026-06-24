package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionKind
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionRow
import paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry
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
			recentlyAdded = listOf(
				AurralDiscoverArtist(
					id = "recent-artist",
					name = "Recently Added Artist",
					imageUrl = "https://aurral.example.com/recent.jpg"
				)
			),
			recommendations = (1..12).map { index ->
				AurralDiscoverArtist(
					id = "seed-$index",
					name = "Seed $index",
					imageUrl = "https://aurral.example.com/seed-$index.jpg"
				)
			} + listOf(
				AurralDiscoverArtist(
					id = "recommended-1",
					name = "Recommended 1",
					imageUrl = "https://aurral.example.com/recommended-1.jpg",
					matchedTags = listOf("soundtrack")
				),
				AurralDiscoverArtist(
					id = "recommended-2",
					name = "Recommended 2",
					imageUrl = "https://aurral.example.com/recommended-2.jpg",
					matchedTags = listOf("soundtrack")
				),
				AurralDiscoverArtist(
					id = "recommended-3",
					name = "Recommended 3",
					imageUrl = "https://aurral.example.com/recommended-3.jpg",
					matchedTags = listOf("soundtrack")
				),
				AurralDiscoverArtist(
					id = "recommended-4",
					name = "Recommended 4",
					imageUrl = "https://aurral.example.com/recommended-4.jpg",
					matchedTags = listOf("soundtrack")
				)
			),
			basedOn = listOf(
				AurralDiscoverArtist(
					id = "based-on",
					name = "Based On",
					imageUrl = "https://aurral.example.com/based-on.jpg"
				)
			),
			globalTop = listOf(
				AurralDiscoverArtist(
					id = "global",
					name = "Global",
					imageUrl = "https://aurral.example.com/global.jpg"
				)
			),
			topTags = listOf("soundtrack", "instrumental"),
			topGenres = listOf("soundtrack"),
			recentReleases = listOf(
				AurralAlbumSearchItem(
					id = "release",
					title = "Recent Release",
					artistName = "Artist",
					artistMbid = "artist-mbid",
					coverUrl = "https://aurral.example.com/release.jpg"
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
	fun libraryAurralCollectionRowsExposeLiveAurralDiscoverShape() {
		val discovery = AurralDiscoverySummary(
			recentlyAdded = listOf(
				AurralDiscoverArtist(
					id = "recent",
					name = "Recently Added",
					imageUrl = "https://aurral.example.com/recent.jpg"
				)
			),
			recentReleases = listOf(
				AurralAlbumSearchItem(
					id = "recent-release",
					title = "Recent Release",
					artistName = "Release Artist",
					artistMbid = "release-artist",
					releaseDate = "2026-05-01",
					coverUrl = "https://aurral.example.com/recent-release.jpg"
				)
			),
			recommendations = listOf(
				AurralDiscoverArtist(
					id = "recommended-artist",
					name = "Recommended Artist",
					imageUrl = "https://aurral.example.com/recommended.jpg",
					tags = listOf("electronic"),
					matchedTags = listOf("electronic")
				)
			),
			globalTop = listOf(
				AurralDiscoverArtist(
					id = "global-artist",
					name = "Global Artist",
					imageUrl = "https://aurral.example.com/global.jpg",
					tags = listOf("classical"),
					matchedTags = listOf("classical")
				)
			),
			basedOn = listOf(
				AurralDiscoverArtist(
					id = "based-on",
					name = "Based On Artist",
					imageUrl = "https://aurral.example.com/based-on.jpg"
				)
			),
			topGenres = listOf("electronic", "classical"),
			topTags = listOf("electronic", "classical")
		)

		val rows = libraryAurralCollectionRows(
			aurralConfigured = true,
			discovery = discovery,
			limit = 8
		)

		assertEquals(
			listOf(
				AurralDiscoveryCollectionKind.RecentlyAddedArtists,
				AurralDiscoveryCollectionKind.RecentReleases,
				AurralDiscoveryCollectionKind.RecommendedArtists,
				AurralDiscoveryCollectionKind.BasedOnArtists,
				AurralDiscoveryCollectionKind.GlobalTopArtists,
				AurralDiscoveryCollectionKind.GenreArtists,
				AurralDiscoveryCollectionKind.GenreArtists,
				AurralDiscoveryCollectionKind.TopTags
			),
			rows.map { it.kind }
		)
	}

	@Test
	fun libraryAurralCollectionRowsKeepDiscoveryPreviewBounded() {
		val discovery = AurralDiscoverySummary(
			topGenres = (1..12).map { index -> "Genre $index" },
			topTags = (1..40).map { index -> "tag-$index" },
			recommendations = (1..12).map { index ->
				AurralDiscoverArtist(
					id = "artist-$index",
					name = "Artist $index",
					imageUrl = "https://aurral.example.com/artist-$index.jpg",
					matchedTags = listOf("Genre $index")
				)
			}
		)

		val rows = libraryAurralCollectionRows(
			aurralConfigured = true,
			discovery = discovery,
			limit = 8
		)
		val genreRows = rows.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()
			.filter { row -> row.kind == AurralDiscoveryCollectionKind.GenreArtists }
		val tagRow = rows.filterIsInstance<AurralDiscoveryCollectionRow.Tags>().single()

		assertEquals(
			3,
			genreRows.size,
			"Library should preview only a few Aurral genre rows instead of injecting every backend row at once."
		)
		assertEquals(
			(1..24).map { index -> "tag-$index" },
			tagRow.tags,
			"Library tag wall should stay bounded; the full tag set belongs in the Aurral hub."
		)
	}

	@Test
	fun libraryAurralCollectionRowsKeepFallbackArtworkOutOfLibraryRows() {
		val discovery = AurralDiscoverySummary(
			recentlyAdded = listOf(
				AurralDiscoverArtist(id = "fallback-artist", name = "Fallback Artist"),
				AurralDiscoverArtist(
					id = "image-artist",
					name = "Image Artist",
					imageUrl = "https://aurral.example.com/image.jpg"
				)
			),
			recentReleases = listOf(
				AurralAlbumSearchItem(
					id = "fallback-album",
					title = "Fallback Album",
					artistName = "Artist",
					artistMbid = "artist-mbid"
				),
				AurralAlbumSearchItem(
					id = "image-album",
					title = "Image Album",
					artistName = "Artist",
					artistMbid = "artist-mbid",
					coverUrl = "https://aurral.example.com/cover.jpg"
				)
			)
		)

		val rows = libraryAurralCollectionRows(
			aurralConfigured = true,
			discovery = discovery,
			limit = 8
		)

		assertEquals(
			listOf("image-artist"),
			(rows[0] as AurralDiscoveryCollectionRow.Artists).artists.map { it.id }
		)
		assertEquals(
			listOf("image-album"),
			(rows[1] as AurralDiscoveryCollectionRow.Albums).albums.map { it.id }
		)
	}

	@Test
	fun libraryAurralCollectionRowsPreferPersistentArtistPhotoCacheForBasedOnLibrary() {
		val discovery = AurralDiscoverySummary(
			basedOn = listOf(
				AurralDiscoverArtist(
					id = "artist-mbid",
					name = "IU",
					imageUrl = "https://navidrome.example.com/artist/iu.jpg"
				)
			),
			libraryArtists = listOf(
				AurralDiscoverArtist(
					id = "artist-mbid",
					name = "IU",
					imageUrl = "https://aurral.example.com/stale-iu.jpg",
					monitored = true
				)
			)
		)

		val rows = libraryAurralCollectionRows(
			aurralConfigured = true,
			discovery = discovery,
			artistPhotoCacheEntries = listOf(
				ArtistHeaderImageCacheEntry(
					artistId = "local-iu",
					sourceArtistId = "artist-mbid",
					name = "IU",
					normalizedName = "iu",
					imageUrl = "https://aurral.example.com/cache/iu.webp",
					source = "Aurral",
					updatedAtMillis = 2000L
				)
			),
			artistArtworkPriority = ArtworkSourcePriority.AurralFirst,
			externalArtworkEnabled = true
		)

		val basedOn = rows
			.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()
			.single { it.kind == AurralDiscoveryCollectionKind.BasedOnArtists }
			.artists
			.single()

		assertEquals("https://aurral.example.com/cache/iu.webp", basedOn.imageUrl)
		assertEquals(true, basedOn.monitored)
	}

	@Test
	fun libraryAurralCollectionRowsKeepCachedArtistPhotosWhenDiscoveryArtworkIsMissing() {
		val discovery = AurralDiscoverySummary(
			basedOn = listOf(
				AurralDiscoverArtist(
					id = "artist-mbid",
					name = "IU"
				)
			)
		)

		val rows = libraryAurralCollectionRows(
			aurralConfigured = true,
			discovery = discovery,
			artistPhotoCacheEntries = listOf(
				ArtistHeaderImageCacheEntry(
					artistId = null,
					sourceArtistId = "artist-mbid",
					name = "IU",
					normalizedName = "iu",
					imageUrl = "https://aurral.example.com/cache/iu.webp"
				)
			),
			artistArtworkPriority = ArtworkSourcePriority.AurralFirst,
			externalArtworkEnabled = true
		)

		val basedOn = rows
			.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()
			.single { it.kind == AurralDiscoveryCollectionKind.BasedOnArtists }
			.artists
			.single()

		assertEquals("https://aurral.example.com/cache/iu.webp", basedOn.imageUrl)
	}

	@Test
	fun localLibraryRowsShowOwnedDotsOnlyWhenDiscoveryIntegrationIsConfigured() {
		assertEquals(
			AurralOwnershipStatus.Owned,
			libraryLocalOwnershipStatus(aurralConfigured = true)
		)
		assertEquals(
			null,
			libraryLocalOwnershipStatus(aurralConfigured = false)
		)
	}

	@Test
	fun libraryAurralCollectionRowsStateKeepsEmptyLoadingPlaceholderWhileResolving() {
		val emptyLoadingRows = libraryAurralCollectionRowsState(
			aurralConfigured = true,
			discoveryState = UiState.Loading(null)
		)

		assertEquals(
			UiState.Loading(emptyList()),
			emptyLoadingRows
		)
		assertEquals(
			true,
			libraryAurralLoadingPlaceholderVisible(emptyLoadingRows)
		)
	}

	@Test
	fun libraryAurralCollectionRowsStateKeepsCachedRowsWhileLoading() {
		val discovery = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(
					id = "artist",
					name = "Artist",
					imageUrl = "https://aurral.example.com/artist.jpg"
				)
			)
		)
		val rows = libraryAurralCollectionRows(
			aurralConfigured = true,
			discovery = discovery
		)
		val loadingRows = libraryAurralCollectionRowsState(
			aurralConfigured = true,
			discoveryState = UiState.Loading(discovery)
		)

		assertEquals(UiState.Loading(rows), loadingRows)
		assertEquals(
			false,
			libraryAurralLoadingPlaceholderVisible(loadingRows)
		)
	}

	@Test
	fun libraryAurralCollectionRowsStateTreatsDiscoveryErrorsAsDegradedRows() {
		val discovery = AurralDiscoverySummary(
			recentlyAdded = listOf(
				AurralDiscoverArtist(
					id = "cached",
					name = "Cached",
					imageUrl = "https://aurral.example.com/cached.jpg"
				)
			)
		)

		val stateWithCachedData = libraryAurralCollectionRowsState(
			aurralConfigured = true,
			discoveryState = UiState.Error(Exception("Aurral Discover returned HTTP 404"), discovery)
		)
		val stateWithoutData = libraryAurralCollectionRowsState(
			aurralConfigured = true,
			discoveryState = UiState.Error(Exception("Aurral Discover returned HTTP 404"), null)
		)

		assertEquals(
			UiState.Success(
				libraryAurralCollectionRows(
					aurralConfigured = true,
					discovery = discovery
				)
			),
			stateWithCachedData
		)
		assertEquals(UiState.Success(emptyList()), stateWithoutData)
	}
}
