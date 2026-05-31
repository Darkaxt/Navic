package paige.navic.ui.screens.aurral

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.domain.repositories.AurralFlowCapabilities
import paige.navic.domain.repositories.AurralFlowStats
import paige.navic.domain.repositories.AurralFlowSummary
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.ui.navigation.Screen

class AurralHubDisplayPolicyTest {
	@Test
	fun discoverArtistsPreferRecommendationsThenGlobalTopAndCapRows() {
		val summary = AurralDiscoverySummary(
			recommendations = listOf(
				AurralDiscoverArtist(id = "r1", name = "Recommendation 1"),
				AurralDiscoverArtist(id = "r2", name = "Recommendation 2")
			),
			globalTop = listOf(
				AurralDiscoverArtist(id = "g1", name = "Global 1"),
				AurralDiscoverArtist(id = "g2", name = "Global 2")
			)
		)

		assertEquals(
			listOf("r1", "r2", "g1"),
			aurralHubDiscoverArtists(summary, limit = 3).map { it.id }
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
		releaseDate: String? = null
	) = AurralAlbumSearchItem(
		id = id,
		title = title,
		artistName = artistName,
		artistMbid = artistMbid,
		releaseDate = releaseDate
	)
}
