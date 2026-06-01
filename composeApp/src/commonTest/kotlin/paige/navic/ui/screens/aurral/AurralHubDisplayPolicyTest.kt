package paige.navic.ui.screens.aurral

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import paige.navic.domain.models.AurralAcquisitionProgress
import paige.navic.domain.models.AurralMissingAlbumRow
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.aurralPreviewTrackOwnershipStatus
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralConfirmationType
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.domain.repositories.AurralFallbackGenreSection
import paige.navic.domain.repositories.AurralFlowCapabilities
import paige.navic.domain.repositories.AurralFlowStats
import paige.navic.domain.repositories.AurralFlowSummary
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.AurralMonitorActionState

class AurralHubDisplayPolicyTest {
	@Test
	fun hubDiscoveryCanRenderBeforeServiceStatusIsAvailable() {
		val summary = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(id = "artist-mbid", name = "Recommended Artist")
			)
		)

		assertTrue(aurralHubCanRenderDiscoveryWithoutStatus(summary))
		assertFalse(aurralHubCanRenderDiscoveryWithoutStatus(null))
		assertFalse(aurralHubCanRenderDiscoveryWithoutStatus(AurralDiscoverySummary()))
	}

	@Test
	fun discoverArtistsPreferRecommendationsThenGlobalTopAndCapRows() {
		val summary = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(id = "r1", name = "Recommendation 1"),
				AurralDiscoverArtist(id = "r2", name = "Recommendation 2")
			),
			recentReleases = listOf(
				albumSearchItem(
					id = "album-1",
					title = "Recommended Album",
					artistName = "Release Artist",
					artistMbid = "release-artist"
				)
			),
			globalTop = listOf(
				AurralDiscoverArtist(id = "g1", name = "Global 1"),
				AurralDiscoverArtist(id = "g2", name = "Global 2")
			)
		)

		assertEquals(
			listOf("r1", "r2", "release-artist"),
			aurralHubDiscoverArtists(summary, limit = 3).map { it.id }
		)
	}

	@Test
	fun discoverArtistsMergeAlbumRecommendationsIntoMatchingArtistRows() {
		val summary = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(id = "artist-mbid", name = "The Artist")
			),
			recentReleases = listOf(
				albumSearchItem(
					id = "release-1",
					title = "New Album",
					artistName = "The Artist",
					artistMbid = "artist-mbid",
					releaseDate = "2026-02-01"
				)
			)
		)

		val artist = aurralHubDiscoverArtists(summary, limit = 8).single()

		assertEquals("artist-mbid", artist.id)
		assertEquals(listOf("New Album"), artist.recommendedAlbums.map { it.title })
		assertEquals(
			listOf("New Album"),
			aurralRecommendedAlbumsForArtist(
				discovery = summary,
				artistMbid = " ARTIST-MBID ",
				artistName = "The Artist"
			).map { it.title }
		)
	}

	@Test
	fun discoverArtistRouteTrimsRequiredFieldsAndKeepsImage() {
		assertEquals(
			Screen.AurralArtist(
				artistMbid = "artist-mbid",
				artistName = "The Artist",
				imageUrl = "https://aurral.example.com/artist.jpg"
			),
			aurralArtistRoute(
				AurralDiscoverArtist(
					id = " artist-mbid ",
					name = " The Artist ",
					imageUrl = "https://aurral.example.com/artist.jpg"
				)
			)
		)
		assertNull(aurralArtistRoute(AurralDiscoverArtist(id = " ", name = "The Artist")))
		assertNull(aurralArtistRoute(AurralDiscoverArtist(id = "artist-mbid", name = " ")))
	}

	@Test
	fun discoverArtistRecommendationRouteUsesNativeArtistWhenKnown() {
		val localArtist = DomainArtist(
			id = "local-artist-id",
			name = "The Artist",
			musicBrainzId = "artist-mbid"
		)

		assertEquals(
			Screen.ArtistDetail("local-artist-id"),
			aurralArtistRecommendationRoute(
				artist = AurralDiscoverArtist(id = " ARTIST-MBID ", name = "Different Case"),
				localArtists = listOf(localArtist)
			)
		)
		assertEquals(
			Screen.AurralArtist(
				artistMbid = "new-artist-mbid",
				artistName = "New Artist",
				imageUrl = "https://aurral.example.com/new.jpg"
			),
			aurralArtistRecommendationRoute(
				artist = AurralDiscoverArtist(
					id = "new-artist-mbid",
					name = "New Artist",
					imageUrl = "https://aurral.example.com/new.jpg"
				),
				localArtists = listOf(localArtist)
			)
		)
	}

	@Test
	fun discoverArtistMonitorStateUsesOnlyVerifiedAurralMonitoring() {
		assertEquals(
			AurralMonitorActionState.Monitored,
			aurralDiscoverArtistMonitorActionState(
				AurralDiscoverArtist(id = "artist", name = "Artist", monitored = true)
			)
		)
		assertEquals(
			AurralMonitorActionState.NotMonitored,
			aurralDiscoverArtistMonitorActionState(
				AurralDiscoverArtist(id = "artist", name = "Artist", monitored = false)
			)
		)
		assertNull(
			aurralDiscoverArtistMonitorActionState(
				AurralDiscoverArtist(id = "artist", name = "Artist", monitored = null)
			)
		)
	}

	@Test
	fun discoverArtistMonitorStateShowsConfirmationQueuePending() {
		val queue = listOf(
			AurralConfirmationQueueItem(
				id = "artist-monitor:artist",
				type = AurralConfirmationType.ArtistMonitoring,
				status = AurralConfirmationStatus.Pending,
				title = "Artist",
				artistMbid = "artist",
				expectedMonitored = true,
				updatedAtMillis = 1L
			)
		)

		assertEquals(
			AurralMonitorActionState.PendingConfirmation,
			aurralDiscoverArtistMonitorActionState(
				artist = AurralDiscoverArtist(id = "artist", name = "Artist", monitored = false),
				confirmationQueue = queue
			)
		)
	}

	@Test
	fun discoverArtistsMergeVerifiedMonitoringFromLibraryArtistRows() {
		val summary = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(id = "artist-mbid", name = "Artist")
			),
			libraryArtists = listOf(
				AurralDiscoverArtist(
					id = "ARTIST-MBID",
					name = "Artist",
					monitored = true
				)
			)
		)

		assertEquals(
			true,
			aurralHubDiscoverArtists(summary).single().monitored
		)
	}

	@Test
	fun discoverArtistsTreatMissingLibraryMatchAsVerifiedNotMonitoredWhenLibraryRowsLoaded() {
		val summary = AurralDiscoverySummary(
			recommendations = listOf(AurralDiscoverArtist(id = "missing-mbid", name = "Missing")),
			libraryArtists = listOf(AurralDiscoverArtist(id = "known-mbid", name = "Known", monitored = true))
		)

		assertEquals(
			false,
			aurralHubDiscoverArtists(summary).single().monitored
		)
	}

	@Test
	fun discoveryCollectionRowsMergeVerifiedMonitoringFromLibraryArtistRows() {
		val summary = AurralDiscoverySummary(
			recentlyAdded = listOf(AurralDiscoverArtist(id = "recent-mbid", name = "Recent Artist")),
			recommendations = listOf(AurralDiscoverArtist(id = "recommendation-mbid", name = "Recommendation")),
			basedOn = listOf(AurralDiscoverArtist(id = "based-on-mbid", name = "Based On")),
			globalTop = listOf(AurralDiscoverArtist(id = "global-mbid", name = "Global")),
			fallbackGenres = listOf(
				AurralFallbackGenreSection(
					genre = "soundtrack",
					artists = listOf(AurralDiscoverArtist(id = "genre-mbid", name = "Genre Artist"))
				)
			),
			libraryArtists = listOf(
				AurralDiscoverArtist(id = "RECENT-MBID", name = "Recent Artist", monitored = true),
				AurralDiscoverArtist(id = "recommendation-mbid", name = "Recommendation", monitored = false),
				AurralDiscoverArtist(id = "based-on-mbid", name = "Based On", monitored = true),
				AurralDiscoverArtist(id = "global-mbid", name = "Global", monitored = false),
				AurralDiscoverArtist(id = "genre-mbid", name = "Genre Artist", monitored = true)
			)
		)

		val rows = aurralDiscoveryCollectionRows(summary, limit = 8)
			.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()

		assertEquals(
			listOf(true, false, true, false, true),
			rows.map { it.artists.single().monitored }
		)
	}

	@Test
	fun discoverCollectionArtistsMergeVerifiedMonitoringFromLibraryArtistRows() {
		val summary = AurralDiscoverySummary(
			recommendations = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Artist")),
			libraryArtists = listOf(AurralDiscoverArtist(id = "ARTIST-MBID", name = "Artist", monitored = true))
		)

		assertEquals(
			true,
			aurralDiscoverCollectionArtists(
				discovery = summary,
				kind = AurralDiscoveryCollectionKind.RecommendedArtists
			).single().monitored
		)
	}

	@Test
	fun localArtistMonitorStateUsesVerifiedAurralLibraryArtistRows() {
		val libraryArtists = listOf(
			AurralDiscoverArtist(
				id = "artist-mbid",
				name = "Different Display Name",
				monitored = true
			),
			AurralDiscoverArtist(
				id = "other-mbid",
				name = "Name Match",
				monitored = false
			)
		)

		assertEquals(
			AurralMonitorActionState.Monitored,
			aurralMonitorStateForLocalArtist(
				artist = DomainArtist(
					id = "local-id",
					name = "Artist",
					musicBrainzId = "ARTIST-MBID"
				),
				libraryArtists = libraryArtists
			)
		)
		assertEquals(
			AurralMonitorActionState.NotMonitored,
			aurralMonitorStateForLocalArtist(
				artist = DomainArtist(
					id = "local-id",
					name = "  Name   Match  "
				),
				libraryArtists = libraryArtists
			)
		)
		assertNull(
			aurralMonitorStateForLocalArtist(
				artist = DomainArtist(id = "local-id", name = "Unknown Artist"),
				libraryArtists = libraryArtists
			)
		)
	}

	@Test
	fun discoverArtistsExposeWhenMoreRowsAreAvailable() {
		val summary = AurralDiscoverySummary(
			recommendations = (1..9).map { index ->
				AurralDiscoverArtist(id = "artist-$index", name = "Artist $index")
			}
		)

		assertTrue(aurralHubDiscoverHasMore(summary, visibleLimit = 8))
		assertFalse(aurralHubDiscoverHasMore(summary, visibleLimit = 9))
	}

	@Test
	fun discoverListArtistsExposeFullMergedRecommendationSet() {
		val summary = AurralDiscoverySummary(
			recommendations = (1..12).map { index ->
				AurralDiscoverArtist(id = "artist-$index", name = "Artist $index")
			},
			recentReleases = listOf(
				albumSearchItem(
					id = "release-1",
					title = "Recommended Album",
					artistName = "Artist 5",
					artistMbid = "artist-5"
				),
				albumSearchItem(
					id = "release-new",
					title = "New Artist Album",
					artistName = "New Artist",
					artistMbid = "artist-new"
				)
			)
		)

		val artists = aurralDiscoverListArtists(summary)

		assertEquals(13, artists.size)
		assertEquals(
			listOf("Recommended Album"),
			artists.single { it.id == "artist-5" }.recommendedAlbums.map { it.title }
		)
		assertTrue(artists.any { it.id == "artist-new" && it.name == "New Artist" })
	}

	@Test
	fun discoverCollectionArtistsExposeTheSelectedAurralBucket() {
		val summary = AurralDiscoverySummary(
			recentlyAdded = listOf(AurralDiscoverArtist(id = "recent", name = "Recent")),
			recommendations = listOf(AurralDiscoverArtist(id = "recommended", name = "Recommended")),
			basedOn = listOf(AurralDiscoverArtist(id = "based-on", name = "Based On")),
			globalTop = listOf(AurralDiscoverArtist(id = "global", name = "Global")),
			recentReleases = listOf(
				albumSearchItem(
					id = "release-1",
					title = "Release Album",
					artistName = "Release Artist",
					artistMbid = "release-artist"
				)
			)
		)

		assertEquals(
			listOf("recent"),
			aurralDiscoverCollectionArtists(summary, AurralDiscoveryCollectionKind.RecentlyAddedArtists)
				.map { it.id }
		)
		assertEquals(
			listOf("recommended"),
			aurralDiscoverCollectionArtists(summary, AurralDiscoveryCollectionKind.RecommendedArtists)
				.map { it.id }
		)
		assertEquals(
			listOf("based-on"),
			aurralDiscoverCollectionArtists(summary, AurralDiscoveryCollectionKind.BasedOnArtists)
				.map { it.id }
		)
		assertEquals(
			listOf("global"),
			aurralDiscoverCollectionArtists(summary, AurralDiscoveryCollectionKind.GlobalTopArtists)
				.map { it.id }
		)
		assertEquals(
			listOf("release-artist"),
			aurralDiscoverCollectionArtists(summary, AurralDiscoveryCollectionKind.RecentReleases)
				.map { it.id }
		)
	}

	@Test
	fun discoverCollectionRouteUsesSpecificArtistCollectionScreens() {
		val row = AurralDiscoveryCollectionRow.Artists(
			kind = AurralDiscoveryCollectionKind.GlobalTopArtists,
			artists = listOf(AurralDiscoverArtist(id = "global", name = "Global"))
		)
		val tagRow = AurralDiscoveryCollectionRow.Artists(
			kind = AurralDiscoveryCollectionKind.GenreArtists,
			artists = listOf(AurralDiscoverArtist(id = "genre", name = "Genre")),
			tag = "soundtrack"
		)

		assertEquals(
			Screen.AurralDiscoverCollection("GlobalTopArtists"),
			aurralDiscoverCollectionRoute(row)
		)
		assertEquals(
			Screen.AurralDiscoverTag("soundtrack"),
			aurralDiscoverCollectionRoute(tagRow)
		)
		assertEquals(
			null,
			aurralDiscoverCollectionRoute(
				AurralDiscoveryCollectionRow.Tags(tags = listOf("soundtrack"))
			)
		)
	}

	@Test
	fun discoveryCollectionRowsDoNotCapTopTagsToPreviewLimit() {
		val summary = AurralDiscoverySummary(
			topTags = (1..12).map { "tag-$it" }
		)

		val rows = aurralDiscoveryCollectionRows(summary, limit = 8)

		assertEquals(
			(1..12).map { "tag-$it" },
			(rows.single() as AurralDiscoveryCollectionRow.Tags).tags
		)
	}

	@Test
	fun discoveryCollectionRowsKeepAurralBucketsSeparate() {
		val summary = AurralDiscoverySummary(
			recentlyAdded = listOf(
				AurralDiscoverArtist(id = "recent-artist", name = "Recently Added Artist")
			),
			recommendations = (1..12).map { index ->
				AurralDiscoverArtist(id = "seed-$index", name = "Seed $index")
			} + listOf(
				AurralDiscoverArtist(id = "recommended-1", name = "Recommended 1", tags = listOf("soundtrack")),
				AurralDiscoverArtist(id = "recommended-2", name = "Recommended 2", tags = listOf("soundtrack")),
				AurralDiscoverArtist(id = "recommended-3", name = "Recommended 3", tags = listOf("soundtrack")),
				AurralDiscoverArtist(id = "recommended-4", name = "Recommended 4", tags = listOf("soundtrack")),
				AurralDiscoverArtist(id = "recommended-5", name = "Recommended 5", tags = listOf("electronic"))
			),
			basedOn = listOf(
				AurralDiscoverArtist(id = "based-on-1", name = "Based On 1")
			),
			globalTop = listOf(
				AurralDiscoverArtist(id = "global-1", name = "Global 1")
			),
			topTags = listOf("soundtrack", "video game music"),
			topGenres = listOf("soundtrack", "electronic"),
			recentReleases = listOf(
				albumSearchItem(id = "release-2026", title = "Recent Release", releaseDate = "2026-01-01")
			)
		)

		val rows = aurralDiscoveryCollectionRows(summary, limit = 8)

		assertEquals(
			listOf(
				"RecentlyAddedArtists",
				AurralDiscoveryCollectionKind.RecentReleases,
				AurralDiscoveryCollectionKind.RecommendedArtists,
				AurralDiscoveryCollectionKind.BasedOnArtists,
				AurralDiscoveryCollectionKind.GlobalTopArtists,
				"GenreArtists",
				"GenreArtists",
				"TopTags"
			).map { it.toString() },
			rows.map { it.kind.toString() }
		)
		assertEquals(
			listOf("recent-artist"),
			(rows[0] as AurralDiscoveryCollectionRow.Artists).artists.map { it.id }
		)
		assertEquals(
			listOf("seed-1", "seed-2", "seed-3", "seed-4", "seed-5", "seed-6", "seed-7", "seed-8"),
			(rows[2] as AurralDiscoveryCollectionRow.Artists).artists.map { it.id }
		)
		assertEquals(
			listOf("release-2026"),
			(rows[1] as AurralDiscoveryCollectionRow.Albums).albums.map { it.id }
		)
		assertEquals(
			listOf("soundtrack", "electronic"),
			rows.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()
				.filter { it.kind == AurralDiscoveryCollectionKind.GenreArtists }
				.map { it.tag }
		)
	}

	@Test
	fun aurralDiscoveryCollectionRowsPreferBackendFallbackGenreSections() {
		val summary = AurralDiscoverySummary(
			recommendations = (1..12).map { index ->
				AurralDiscoverArtist(id = "seed-$index", name = "Seed $index")
			},
			topGenres = listOf("soundtrack"),
			fallbackGenres = listOf(
				AurralFallbackGenreSection(
					genre = "Soundtracks - Games",
					artists = listOf(
						AurralDiscoverArtist(id = "game-1", name = "Game 1"),
						AurralDiscoverArtist(id = "game-2", name = "Game 2"),
						AurralDiscoverArtist(id = "game-3", name = "Game 3"),
						AurralDiscoverArtist(id = "game-4", name = "Game 4")
					)
				)
			)
		)

		val genreRows = aurralDiscoveryCollectionRows(summary, limit = 8)
			.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()
			.filter { it.kind == AurralDiscoveryCollectionKind.GenreArtists }

		assertEquals(listOf("Soundtracks - Games"), genreRows.map { it.tag })
		assertEquals(
			listOf("game-1", "game-2", "game-3", "game-4"),
			genreRows.single().artists.map { it.id }
		)
	}

	@Test
	fun aurralDiscoveryCollectionRowsExposeEveryBackendFallbackGenreSection() {
		val summary = AurralDiscoverySummary(
			fallbackGenres = (1..6).map { index ->
				AurralFallbackGenreSection(
					genre = "Genre $index",
					artists = listOf(
						AurralDiscoverArtist(id = "genre-$index-artist", name = "Genre $index Artist")
					)
				)
			}
		)

		val genreRows = aurralDiscoveryCollectionRows(summary, limit = 8)
			.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()
			.filter { it.kind == AurralDiscoveryCollectionKind.GenreArtists }

		assertEquals(
			(1..6).map { "Genre $it" },
			genreRows.map { it.tag }
		)
	}

	@Test
	fun aurralDiscoveryCollectionRowsExposeEveryTopGenreWithMatchingArtists() {
		val topGenres = (1..6).map { "Genre $it" }
		val summary = AurralDiscoverySummary(
			recommendations = (1..12).map { index ->
				AurralDiscoverArtist(id = "seed-$index", name = "Seed $index")
			} + topGenres.mapIndexed { index, genre ->
				AurralDiscoverArtist(
					id = "genre-match-${index + 1}",
					name = "$genre Artist",
					matchedTags = listOf(genre)
				)
			},
			topGenres = topGenres
		)

		val genreRows = aurralDiscoveryCollectionRows(summary, limit = 8)
			.filterIsInstance<AurralDiscoveryCollectionRow.Artists>()
			.filter { it.kind == AurralDiscoveryCollectionKind.GenreArtists }

		assertEquals(
			topGenres,
			genreRows.map { it.tag }
		)
	}

	@Test
	fun localArtistAurralIdentityCandidatesPreferLocalMbidBeforeDiscoveryNameMatch() {
		val localArtist = DomainArtist(
			id = "local-bond",
			name = "BOND",
			musicBrainzId = "local-stale-mbid"
		)
		val discovery = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(
					id = "aurral-bond-mbid",
					name = "Bond",
					imageUrl = "https://assets.example.com/bond.jpg",
					recommendedAlbums = listOf(
						albumSearchItem(
							id = "release-1",
							title = "Recommended Bond Album",
							artistName = "Bond",
							artistMbid = "aurral-bond-mbid"
						)
					)
				)
			)
		)

		assertEquals(
			listOf(
				AurralArtistIdentity(mbid = "local-stale-mbid", name = "BOND"),
				AurralArtistIdentity(
					mbid = "aurral-bond-mbid",
					name = "Bond",
					imageUrl = "https://assets.example.com/bond.jpg"
				)
			),
			aurralArtistIdentityCandidatesForLocalArtist(discovery, localArtist)
		)
		assertEquals(
			AurralArtistIdentity(mbid = "local-stale-mbid", name = "BOND"),
			aurralArtistIdentityForLocalArtist(discovery, localArtist)
		)
		assertEquals(
			listOf("Recommended Bond Album"),
			aurralRecommendedAlbumsForLocalArtist(
				discovery = discovery,
				artist = localArtist
			).map { it.title }
		)
	}

	@Test
	fun searchArtistsDedupeByArtistIdAndCapRows() {
		assertEquals(
			listOf("artist-1", "artist-2"),
			aurralHubSearchArtists(
				listOf(
					AurralDiscoverArtist(id = "artist-1", name = "Artist 1"),
					AurralDiscoverArtist(id = " ARTIST-1 ", name = "Duplicate Artist 1"),
					AurralDiscoverArtist(id = "artist-2", name = "Artist 2"),
					AurralDiscoverArtist(id = "artist-3", name = "Artist 3")
				),
				limit = 2
			).map { it.id }
		)
	}

	@Test
	fun searchAlbumsDedupeSortByYearRecentToOldestAndCapRows() {
		assertEquals(
			listOf("release-2024", "release-2020-a", "release-2020-b", "release-unknown"),
			aurralHubSearchAlbums(
				listOf(
					albumSearchItem(id = "release-unknown", title = "No Date", releaseDate = null),
					albumSearchItem(id = "release-2020-b", title = "Beta", releaseDate = "2020-02-01"),
					albumSearchItem(id = "release-2024", title = "Recent", releaseDate = "2024-01-01"),
					albumSearchItem(id = " RELEASE-2024 ", title = "Duplicate Recent", releaseDate = "2024-05-01"),
					albumSearchItem(id = "release-2020-a", title = "Alpha", releaseDate = "2020")
				),
				limit = 4
			).map { it.id.trim() }
		)
	}

	@Test
	fun albumSearchRouteUsesNativeMissingAlbumPage() {
		assertEquals(
			Screen.AurralMissingAlbum(
				artistId = "artist-mbid",
				artistName = "IU",
				artistMbid = "artist-mbid",
				releaseGroupId = "release-mbid",
				title = "Celebrity",
				year = "2021",
				primaryType = "Single",
				coverUrl = "https://aurral.example.com/cover.jpg",
				requestStatus = "missing"
			),
			aurralAlbumSearchRoute(
				AurralAlbumSearchItem(
					id = " release-mbid ",
					title = " Celebrity ",
					artistName = " IU ",
					artistMbid = " artist-mbid ",
					releaseDate = "2021-01-27",
					primaryType = "Single",
					coverUrl = "https://aurral.example.com/cover.jpg",
					status = "missing"
				)
			)
		)
		assertNull(aurralAlbumSearchRoute(albumSearchItem(id = " ", title = "Album")))
		assertNull(aurralAlbumSearchRoute(albumSearchItem(id = "release", title = " ")))
		assertNull(aurralAlbumSearchRoute(albumSearchItem(id = "release", title = "Album", artistMbid = " ")))
	}

	@Test
	fun albumOwnershipStatusMapsOwnedRequestedAndMissingRows() {
		assertEquals(
			AurralOwnershipStatus.Owned,
			aurralSearchAlbumOwnershipStatus(
				albumSearchItem(
					id = "owned",
					title = "Owned",
					inLibrary = true
				)
			)
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			aurralSearchAlbumOwnershipStatus(
				albumSearchItem(
					id = "requested",
					title = "Requested",
					status = "processing"
				)
			)
		)
		assertEquals(
			AurralOwnershipStatus.Missing,
			aurralSearchAlbumOwnershipStatus(
				albumSearchItem(
					id = "missing",
					title = "Missing",
					status = "missing"
				)
			)
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			aurralMissingAlbumOwnershipStatus(
				missingAlbumRow(
					title = "Requested Missing",
					progress = AurralAcquisitionProgress(
						status = "requested",
						active = true,
						completed = false,
						failed = false
					)
				)
			)
		)
		assertEquals(AurralOwnershipStatus.Missing, aurralMissingAlbumOwnershipStatus(missingAlbumRow()))
	}

	@Test
	fun previewTrackOwnershipStatusMapsOwnedRequestedAndUnrequestedSongs() {
		assertEquals(
			AurralOwnershipStatus.Owned,
			aurralPreviewTrackOwnershipStatus(
				AurralPreviewTrack(
					id = "owned-track",
					title = "Owned",
					owned = true
				)
			)
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			aurralPreviewTrackOwnershipStatus(
				AurralPreviewTrack(
					id = "requested-track",
					title = "Requested",
					requested = true
				)
			)
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			aurralPreviewTrackOwnershipStatus(
				AurralPreviewTrack(
					id = "fallback-track",
					title = "Fallback"
				),
				fallbackAlbumStatus = AurralOwnershipStatus.Partial
			)
		)
		assertEquals(
			AurralOwnershipStatus.Missing,
			aurralPreviewTrackOwnershipStatus(
				AurralPreviewTrack(
					id = "unrequested-track",
					title = "Unrequested"
				)
			)
		)
	}

	@Test
	fun summaryCardsExposeDiscoveryRequestsAndFlows() {
		val cards = aurralHubSummaryCards(
			AurralServiceStatus(
				discoveryRecommendationsCount = 12,
				discoveryUpdating = true,
				requestsCount = 3,
				flowsCount = 3,
				enabledFlowsCount = 2,
				sharedPlaylistsCount = 1,
				flowTracksTotal = 10,
				flowTracksPending = 4,
				flowTracksDownloading = 2,
				flowTracksDone = 3,
				flowTracksFailed = 1,
				acquisitionQueue = listOf(
					queueItem("1", "processing"),
					queueItem("2", "available"),
					queueItem("3", "failed")
				)
			)
		)

		assertEquals(AurralHubSection.Discover, cards[0].section)
		assertEquals("12 recommendations", cards[0].value)
		assertEquals("updating", cards[0].detail)
		assertTrue(cards[0].active)

		assertEquals(AurralHubSection.Requests, cards[1].section)
		assertEquals("3 requests", cards[1].value)
		assertEquals("1 active, 1 ready, 1 failed", cards[1].detail)
		assertTrue(cards[1].active)

		assertEquals(AurralHubSection.Flows, cards[2].section)
		assertEquals("2 / 3 enabled", cards[2].value)
		assertEquals("10 tracks: 4 pending, 2 downloading, 3 ready, 1 failed; 1 shared playlist", cards[2].detail)
		assertTrue(cards[2].active)
	}

	@Test
	fun flowCreationRequiresFlowPermissionAndAvailableSources() {
		assertTrue(
			canCreateAurralFlow(
				AurralServiceStatus(
					accessFlow = true,
					flowCapabilities = AurralFlowCapabilities(
						availableSources = listOf("discover", "mix", "trending")
					)
				)
			)
		)
		assertFalse(
			canCreateAurralFlow(
				AurralServiceStatus(
					accessFlow = true,
					flowCapabilities = AurralFlowCapabilities(
						lastfmRequired = true,
						unavailableSources = mapOf("discover" to "Last.fm API key required")
					)
				)
			)
		)
		assertFalse(canCreateAurralFlow(AurralServiceStatus(accessFlow = false)))
	}

	@Test
	fun nextFlowNameAvoidsDuplicates() {
		assertEquals(
			"Discover 3",
			nextAurralFlowName(
				flows = listOf(
					AurralFlowSummary(id = "1", name = "Discover", enabled = false),
					AurralFlowSummary(id = "2", name = "Discover 2", enabled = false)
				),
				baseName = "Discover"
			)
		)
	}

	@Test
	fun flowDetailSummarizesStatsAndSchedule() {
		assertEquals(
			"30 tracks; 6 ready, 2 pending; Tue, Thu at 06:00",
			aurralFlowDetail(
				AurralFlowSummary(
					id = "flow-1",
					name = "Training",
					enabled = true,
					size = 30,
					scheduleDays = listOf(2, 4),
					scheduleTime = "06:00",
					stats = AurralFlowStats(total = 8, pending = 2, done = 6)
				)
			)
		)
	}

	@Test
	fun stationForFlowMatchesAurralStationDisplayNameOnly() {
		val station = playlist(id = "station-1", name = "[A]  Discover Mix")
		val regularPlaylist = playlist(id = "regular", name = "Discover Mix")
		val otherStation = playlist(id = "station-2", name = "[A] Different")

		assertEquals(
			station,
			aurralStationForFlow(
				flow = AurralFlowSummary(id = "flow", name = "discover   mix", enabled = true),
				playlists = listOf(regularPlaylist, otherStation, station)
			)
		)
		assertNull(
			aurralStationForFlow(
				flow = AurralFlowSummary(id = "flow", name = "Missing", enabled = true),
				playlists = listOf(regularPlaylist, otherStation, station)
			)
		)
	}

	@Test
	fun playableStationForFlowRequiresSongsOrRefreshableSongCount() {
		val flow = AurralFlowSummary(id = "flow", name = "Discover Mix", enabled = true)
		val emptyStation = playlist(id = "empty", name = "[A] Discover Mix", songCount = 0)
		val refreshableStation = playlist(id = "refreshable", name = "[A] Discover Mix", songCount = 4)
		val stationWithSongs = playlist(id = "songs", name = "[A] Discover Mix", songCount = 0, songs = listOf(song("song-1")))

		assertNull(aurralPlayableStationForFlow(flow, listOf(emptyStation)))
		assertEquals(refreshableStation, aurralPlayableStationForFlow(flow, listOf(refreshableStation)))
		assertEquals(stationWithSongs, aurralPlayableStationForFlow(flow, listOf(stationWithSongs)))
	}

	@Test
	fun directFlowPlaybackIsOfferedOnlyWhenNoPlayableStationAndJobsAreReady() {
		val readyFlow = AurralFlowSummary(
			id = "flow",
			name = "Discover Mix",
			enabled = true,
			stats = AurralFlowStats(done = 3)
		)
		val pendingFlow = readyFlow.copy(stats = AurralFlowStats(done = 0, pending = 3))
		val playableStation = playlist(id = "station", name = "[A] Discover Mix", songCount = 3)
		val emptyStation = playlist(id = "empty", name = "[A] Discover Mix", songCount = 0)

		assertTrue(shouldOfferAurralDirectFlowPlayback(readyFlow, listOf(emptyStation)))
		assertTrue(shouldOfferAurralDirectFlowPlayback(readyFlow, emptyList()))
		assertFalse(shouldOfferAurralDirectFlowPlayback(readyFlow, listOf(playableStation)))
		assertFalse(shouldOfferAurralDirectFlowPlayback(pendingFlow, listOf(emptyStation)))
		assertFalse(shouldOfferAurralDirectFlowPlayback(readyFlow.copy(enabled = false), listOf(emptyStation)))
	}

	private fun queueItem(
		id: String,
		status: String
	) = AurralAcquisitionQueueItem(
		id = id,
		type = "album",
		albumId = null,
		albumMbid = null,
		albumName = "Album $id",
		artistId = null,
		artistMbid = null,
		artistName = "Artist $id",
		status = status,
		requestedAt = null,
		inQueue = status == "processing"
	)

	private fun playlist(
		id: String,
		name: String,
		songCount: Int = 0,
		songs: List<DomainSong> = emptyList()
	) = DomainPlaylist(
		id = id,
		name = name,
		owner = "owner",
		comment = null,
		coverArtId = null,
		songCount = songCount,
		duration = 0.seconds,
		createdAt = Instant.DISTANT_PAST,
		modifiedAt = Instant.DISTANT_PAST,
		public = null,
		readOnly = null,
		allowedUsers = emptyList(),
		validUntil = null,
		songs = songs
	)

	private fun song(id: String) = DomainSong(
		id = id,
		title = "Song $id",
		artistName = "Artist",
		artistId = "artist",
		albumTitle = null,
		albumId = null,
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = null,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 30.seconds,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = "mp3",
		mimeType = "audio/mpeg",
		filePath = null,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown
	)

	private fun albumSearchItem(
		id: String,
		title: String,
		artistName: String = "Artist",
		artistMbid: String = "artist-mbid",
		releaseDate: String? = null,
		inLibrary: Boolean = false,
		status: String? = null
	) = AurralAlbumSearchItem(
		id = id,
		title = title,
		artistName = artistName,
		artistMbid = artistMbid,
		releaseDate = releaseDate,
		inLibrary = inLibrary,
		status = status
	)

	private fun missingAlbumRow(
		title: String = "Missing",
		progress: AurralAcquisitionProgress? = null
	) = AurralMissingAlbumRow(
		releaseGroup = AurralReleaseGroup(
			id = "release-$title",
			title = title
		),
		title = title,
		year = null,
		coverUrl = null,
		requestStatus = progress?.status,
		requestable = progress == null,
		acquisitionProgress = progress
	)
}
