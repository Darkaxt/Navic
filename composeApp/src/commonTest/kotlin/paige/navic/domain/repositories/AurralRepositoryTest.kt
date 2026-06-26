package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistExternalLink
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralConfirmationType

class AurralRepositoryTest {
	@Test
	fun aurralEndpointNormalizesBaseUrlAndPath() {
		assertEquals(
			"https://aurral.example.com/api/health",
			aurralEndpoint(" https://aurral.example.com/ ", "/api/health")
		)
		assertEquals(
			"https://aurral.example.com/aurral/api/health",
			aurralEndpoint(" https://aurral.example.com/aurral/ ", "api/health")
		)
	}

	@Test
	fun aurralEndpointRequiresConfiguredBaseUrl() {
		assertNull(configuredAurralBaseUrl(" "))
		assertEquals(
			"https://aurral.example.com",
			configuredAurralBaseUrl(" https://aurral.example.com/ ")
		)

		val error = assertFailsWith<IllegalStateException> {
			aurralEndpoint(" ", "/api/health")
		}
		assertEquals(AURRAL_BASE_URL_REQUIRED_MESSAGE, error.message)
	}

	@Test
	fun aurralEndpointRequiresHttpOrHttpsBaseUrl() {
		assertNull(configuredAurralBaseUrl("aurral.example.com"))

		val error = assertFailsWith<IllegalStateException> {
			aurralEndpoint("aurral.example.com", "/api/health")
		}
		assertEquals(AURRAL_BASE_URL_INVALID_SCHEME_MESSAGE, error.message)
	}

	@Test
	fun aurralEndpointRequiresBaseUrlHostWithoutCredentialsQueryOrFragment() {
		assertNull(configuredAurralBaseUrl("https:///api"))
		assertNull(configuredAurralBaseUrl("https://?debug=true"))
		assertNull(configuredAurralBaseUrl("https://aurral.example.com?debug=true"))
		assertNull(configuredAurralBaseUrl("https://aurral.example.com#setup"))
		assertNull(configuredAurralBaseUrl("https://user:pass@aurral.example.com"))
		assertEquals(
			"https://aurral.example.com/aurral",
			configuredAurralBaseUrl(" https://aurral.example.com/aurral/ ")
		)

		val error = assertFailsWith<IllegalStateException> {
			aurralEndpoint("https://?debug=true", "/api/health")
		}
		assertEquals(AURRAL_BASE_URL_INVALID_HOST_MESSAGE, error.message)
	}

	@Test
	fun aurralEndpointRequiresValidBaseUrlPort() {
		assertNull(configuredAurralBaseUrl("https://aurral.example.com:"))
		assertNull(configuredAurralBaseUrl("https://aurral.example.com:bad"))
		assertNull(configuredAurralBaseUrl("https://aurral.example.com:0"))
		assertNull(configuredAurralBaseUrl("https://aurral.example.com:65536"))
		assertNull(configuredAurralBaseUrl("http://[::1]:bad"))
		assertEquals(
			"https://aurral.example.com:8443/aurral",
			configuredAurralBaseUrl(" https://aurral.example.com:8443/aurral/ ")
		)
		assertEquals(
			"http://[::1]:8080/aurral",
			configuredAurralBaseUrl(" http://[::1]:8080/aurral/ ")
		)

		val error = assertFailsWith<IllegalStateException> {
			aurralEndpoint("https://aurral.example.com:bad", "/api/health")
		}
		assertEquals(AURRAL_BASE_URL_INVALID_HOST_MESSAGE, error.message)
	}

	@Test
	fun aurralBasicAuthHeadersIncludeTrimmedCredentialsOnlyWhenBothPresent() {
		assertEquals(
			mapOf("Authorization" to "Basic dXNlcjpwYXNz"),
			aurralBasicAuthHeaders(" user ", " pass ")
		)
		assertEquals(emptyMap(), aurralBasicAuthHeaders("", "pass"))
		assertEquals(emptyMap(), aurralBasicAuthHeaders("user", " "))
	}

	@Test
	fun aurralBearerAuthHeadersIncludeTrimmedTokenOnlyWhenPresent() {
		assertEquals(
			mapOf("Authorization" to "Bearer session-token"),
			aurralBearerAuthHeaders(" session-token ")
		)
		assertEquals(emptyMap(), aurralBearerAuthHeaders(" "))
		assertEquals(emptyMap(), aurralBearerAuthHeaders(null))
	}

	@Test
	fun aurralFlowStreamUrlUsesBearerQueryToken() {
		assertEquals(
			"https://aurral.example.com/api/weekly-flow/stream/job-123?token=session-token",
			aurralFlowStreamUrl(
				baseUrl = "https://aurral.example.com",
				jobId = " job-123 ",
				sessionToken = " session-token "
			)
		)
	}

	@Test
	fun aurralFlowArtworkUrlUsesBearerQueryTokenAndBasePath() {
		assertEquals(
			"https://aurral.example.com/aurral/api/weekly-flow/artwork/playlist-1?token=session-token",
			aurralFlowArtworkUrl(
				baseUrl = "https://aurral.example.com/aurral",
				playlistId = " playlist-1 ",
				sessionToken = " session-token "
			)
		)
	}

	@Test
	fun aurralFlowStreamUrlCanUseShortStreamToken() {
		assertEquals(
			"https://aurral.example.com/api/weekly-flow/stream/job-123?st=stream-token",
			aurralFlowStreamTokenUrl(
				baseUrl = "https://aurral.example.com",
				jobId = " job-123 ",
				streamToken = " stream-token "
			)
		)
		assertNull(aurralFlowStreamTokenUrl("https://aurral.example.com", "", "stream-token"))
		assertNull(aurralFlowStreamTokenUrl("https://aurral.example.com", "job-123", ""))
	}

	@Test
	fun aurralFlowMediaUrlsEncodePathAndTokenValues() {
		assertEquals(
			"https://aurral.example.com/api/weekly-flow/stream/job%201%2Fdemo?token=token%20value%2Fplus",
			aurralFlowStreamUrl(
				baseUrl = "https://aurral.example.com",
				jobId = "job 1/demo",
				sessionToken = "token value/plus"
			)
		)
	}

	@Test
	fun aurralFlowMediaUrlsRequireIdTokenAndConfiguredBaseUrl() {
		assertNull(aurralFlowStreamUrl("https://aurral.example.com", "", "session-token"))
		assertNull(aurralFlowStreamUrl("https://aurral.example.com", "job-123", ""))
		assertNull(aurralFlowStreamUrl("aurral.example.com", "job-123", "session-token"))
		assertNull(aurralFlowArtworkUrl("https://aurral.example.com", "", "session-token"))
		assertNull(aurralFlowArtworkUrl("https://aurral.example.com", "playlist-1", ""))
		assertNull(aurralFlowArtworkUrl("aurral.example.com", "playlist-1", "session-token"))
	}

	@Test
	fun aurralAbsoluteImageUrlResolvesImageProxyPathsAgainstConfiguredBaseUrl() {
		assertEquals(
			"https://aurral.example.com/aurral/api/image-proxy/cover.webp",
			aurralAbsoluteImageUrl(
				baseUrl = "https://aurral.example.com/aurral/",
				imageUrl = "/api/image-proxy/cover.webp"
			)
		)
		assertEquals(
			"https://cdn.example.com/cover.webp",
			aurralAbsoluteImageUrl(
				baseUrl = "https://aurral.example.com",
				imageUrl = "https://cdn.example.com/cover.webp"
			)
		)
		assertNull(aurralAbsoluteImageUrl("aurral.example.com", "/api/image-proxy/cover.webp"))
		assertNull(aurralAbsoluteImageUrl("https://aurral.example.com", " "))
	}

	@Test
	fun aurralRequestHeadersForUrlOnlyUsesHeadersForAurralHostedImages() {
		val headers = mapOf("Authorization" to "Basic secret")

		assertEquals(
			headers,
			aurralRequestHeadersForUrl(
				baseUrl = "https://aurral.example.com/aurral",
				imageUrl = "https://aurral.example.com/aurral/api/image-proxy/cover.webp",
				requestHeaders = headers
			)
		)
		assertEquals(
			emptyMap(),
			aurralRequestHeadersForUrl(
				baseUrl = "https://aurral.example.com/aurral",
				imageUrl = "https://cdn.example.com/cover.webp",
				requestHeaders = headers
			)
		)
		assertEquals(
			emptyMap(),
			aurralRequestHeadersForUrl(
				baseUrl = "aurral.example.com",
				imageUrl = "https://aurral.example.com/api/image-proxy/cover.webp",
				requestHeaders = headers
			)
		)
	}

	@Test
	fun aurralConnectionResultClassifiesReachabilityAndAuthFailures() {
		assertEquals(
			AurralConnectionResult.Connected,
			aurralConnectionResult("Aurral health", HttpStatusCode.OK)
		)
		assertEquals(
			AurralConnectionResult.Unauthorized,
			aurralConnectionResult("Aurral health", HttpStatusCode.Unauthorized)
		)
		assertEquals(
			AurralConnectionResult.Forbidden,
			aurralConnectionResult("Aurral health", HttpStatusCode.Forbidden)
		)
		assertEquals(
			AurralConnectionResult.Failed("Aurral health returned HTTP 500"),
			aurralConnectionResult("Aurral health", HttpStatusCode.InternalServerError)
		)
	}

	@Test
	fun aurralServiceStatusUsesHealthAuthFlowAndRequestCounts() {
		val status = aurralServiceStatus(
			health = AurralHealthDto(
				status = "ok",
				appVersion = "1.2.3",
				authRequired = true,
				lidarrConfigured = true,
				discovery = AurralDiscoveryDto(
					recommendationsCount = 18,
					isUpdating = true
				)
			),
			authMe = AurralAuthMeDto(
				user = AurralUserDto(
					username = "darka",
					role = "admin",
					permissions = AurralPermissionsDto(
						accessFlow = true,
						addArtist = true,
						addAlbum = false
					)
				)
			),
			weeklyFlow = AurralWeeklyFlowStatusDto(
				flows = listOf(
					AurralFlowDto(id = "flow-1", name = "Focus", enabled = true),
					AurralFlowDto(id = "flow-2", name = "Training", enabled = false)
				),
				sharedPlaylists = listOf(
					AurralSharedPlaylistDto(id = "shared-1", name = "Shared Focus")
				),
				stats = AurralFlowStatsDto(total = 6, pending = 1, downloading = 2, done = 3, failed = 0),
				hint = AurralFlowHintDto(phase = "downloading", message = "Downloading track")
			),
			requests = listOf(
				AurralRequestDto(
					id = "request-1",
					type = "album",
					albumId = "101",
					albumMbid = "album-mbid-1",
					albumName = "Queued Album",
					artistId = "201",
					artistMbid = "artist-mbid-1",
					artistName = "Queued Artist",
					status = "processing",
					requestedAt = "2026-05-31T00:00:00Z",
					inQueue = true
				),
				AurralRequestDto(
					id = "request-2",
					type = "album",
					mbid = "available-mbid",
					name = "Available Album",
					artistName = "Available Artist",
					status = "available"
				)
			)
		)

		assertEquals("ok", status.healthStatus)
		assertEquals("1.2.3", status.appVersion)
		assertTrue(status.authRequired)
		assertEquals("darka", status.username)
		assertEquals("admin", status.role)
		assertTrue(status.accessFlow)
		assertTrue(status.addArtist)
		assertEquals(false, status.addAlbum)
		assertTrue(status.lidarrConfigured)
		assertEquals(18, status.discoveryRecommendationsCount)
		assertTrue(status.discoveryUpdating)
		assertEquals(2, status.flowsCount)
		assertEquals(1, status.enabledFlowsCount)
		assertEquals(1, status.sharedPlaylistsCount)
		assertEquals(2, status.requestsCount)
		assertEquals(
			listOf(
				AurralAcquisitionQueueItem(
					id = "request-1",
					type = "album",
					albumId = "101",
					albumMbid = "album-mbid-1",
					albumName = "Queued Album",
					artistId = "201",
					artistMbid = "artist-mbid-1",
					artistName = "Queued Artist",
					status = "processing",
					requestedAt = "2026-05-31T00:00:00Z",
					inQueue = true
				),
				AurralAcquisitionQueueItem(
					id = "request-2",
					type = "album",
					albumId = null,
					albumMbid = "available-mbid",
					albumName = "Available Album",
					artistId = null,
					artistMbid = null,
					artistName = "Available Artist",
					status = "available",
					requestedAt = null,
					inQueue = false
				)
			),
			status.acquisitionQueue
		)
		assertEquals(6, status.flowTracksTotal)
		assertEquals(1, status.flowTracksPending)
		assertEquals(2, status.flowTracksDownloading)
		assertEquals(3, status.flowTracksDone)
		assertEquals(0, status.flowTracksFailed)
		assertEquals("downloading", status.flowPhase)
		assertEquals("Downloading track", status.flowMessage)
	}

	@Test
	fun aurralServiceStatusKeepsFlowSummariesStatsAndCapabilities() {
		val status = aurralServiceStatus(
			health = AurralHealthDto(status = "ok"),
			authMe = AurralAuthMeDto(
				user = AurralUserDto(
					username = "darka",
					permissions = AurralPermissionsDto(accessFlow = true)
				)
			),
			weeklyFlow = AurralWeeklyFlowStatusDto(
				flows = listOf(
					AurralFlowDto(
						id = "flow-1",
						name = "Training",
						enabled = true,
						size = 30,
						nextRunAt = 1780100000000,
						mix = AurralFlowMixDto(discover = 34, mix = 33, trending = 33, focus = 0),
						scheduleDays = listOf(1, 3),
						scheduleTime = "06:00"
					),
					AurralFlowDto(
						id = "flow-2",
						name = "Recovery",
						enabled = false,
						size = 15,
						mix = AurralFlowMixDto(discover = 0, mix = 0, trending = 0, focus = 100),
						tags = listOf("ambient"),
						relatedArtists = listOf("Tycho")
					)
				),
				flowStats = mapOf(
					"flow-1" to AurralFlowStatsDto(total = 7, pending = 2, downloading = 1, done = 4),
					"flow-2" to AurralFlowStatsDto(total = 0)
				),
				capabilities = AurralFlowCapabilitiesDto(
					lastfmRequired = false,
					availableSources = listOf("discover", "mix", "trending", "focus")
				)
			),
			requests = emptyList()
		)

		assertEquals(
			listOf(
				AurralFlowSummary(
					id = "flow-1",
					name = "Training",
					enabled = true,
					size = 30,
					nextRunAt = 1780100000000,
					mix = AurralFlowMix(discover = 34, mix = 33, trending = 33, focus = 0),
					scheduleDays = listOf(1, 3),
					scheduleTime = "06:00",
					stats = AurralFlowStats(total = 7, pending = 2, downloading = 1, done = 4)
				),
				AurralFlowSummary(
					id = "flow-2",
					name = "Recovery",
					enabled = false,
					size = 15,
					mix = AurralFlowMix(discover = 0, mix = 0, trending = 0, focus = 100),
					tags = listOf("ambient"),
					relatedArtists = listOf("Tycho"),
					stats = AurralFlowStats(total = 0)
				)
			),
			status.flows
		)
		assertEquals(
			AurralFlowCapabilities(
				lastfmRequired = false,
				availableSources = listOf("discover", "mix", "trending", "focus")
			),
			status.flowCapabilities
		)
	}

	@Test
	fun aurralDiscoverySummaryNormalizesArtistRows() {
		val summary = aurralDiscoverySummary(
			baseUrl = "https://aurral.example.com/aurral",
			response = AurralDiscoveryResponseDto(
				recommendations = listOf(
					AurralDiscoverArtistDto(
						id = "artist-mbid",
						name = "Alex Warren",
						image = "/api/images/artists/artist-mbid",
						tags = listOf("pop", "training"),
						matchedTags = listOf("training"),
						sourceArtist = "Benson Boone",
						discoveryTier = "balanced"
					),
					AurralDiscoverArtistDto(id = null, name = "Missing Id")
				),
				globalTop = listOf(
					AurralDiscoverArtistDto(
						mbid = "global-mbid",
						name = "IU",
						imageUrl = "https://img.example.com/iu.jpg",
						sourceType = "global"
					)
				),
				topTags = listOf("pop", "k-pop"),
				isUpdating = true,
				stale = true,
				provider = "lastfm",
				discoveryMode = "balanced"
			)
		)

		assertTrue(summary.isUpdating)
		assertTrue(summary.stale)
		assertEquals("lastfm", summary.provider)
		assertEquals("balanced", summary.discoveryMode)
		assertEquals(listOf("pop", "k-pop"), summary.topTags)
		assertEquals(1, summary.recommendations.size)
		assertEquals(
			AurralDiscoverArtist(
				id = "artist-mbid",
				name = "Alex Warren",
				imageUrl = "https://aurral.example.com/aurral/api/images/artists/artist-mbid",
				tags = listOf("pop", "training"),
				matchedTags = listOf("training"),
				reason = "Similar to Benson Boone",
				sourceType = null,
				discoveryTier = "balanced"
			),
			summary.recommendations.single()
		)
		assertEquals(
			AurralDiscoverArtist(
				id = "global-mbid",
				name = "IU",
				imageUrl = "https://img.example.com/iu.jpg",
				sourceType = "global",
				detailsIdVerified = true
			),
			summary.globalTop.single()
		)
	}

	@Test
	fun aurralDiscoverySummaryPrefersVerifiedArtistIdOverGenericId() {
		val summary = aurralDiscoverySummary(
			baseUrl = "https://aurral.example.com",
			response = AurralDiscoveryResponseDto(
				recommendations = listOf(
					AurralDiscoverArtistDto(
						id = "internal-artist-row-id",
						mbid = "musicbrainz-artist-mbid",
						name = "Naoshi Mizuta"
					)
				)
			)
		)

		assertEquals("musicbrainz-artist-mbid", summary.recommendations.single().id)
		assertTrue(summary.recommendations.single().detailsIdVerified)
	}

	@Test
	fun aurralArtistSearchResultTreatsUuidIdAsVerifiedMusicBrainzArtistId() {
		val result = aurralArtistSearchResult(
			baseUrl = "https://aurral.example.com",
			query = "John Powell",
			response = AurralArtistSearchResponseDto(
				artists = listOf(
					AurralDiscoverArtistDto(
						id = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
						name = "John Powell",
						imageUrl = "https://assets.example.com/john-powell.jpg"
					),
					AurralDiscoverArtistDto(
						id = "internal-artist-row-id",
						name = "Internal Artist"
					)
				)
			)
		)

		assertEquals("52bb713d-b0c9-4bf6-9f58-392388d5cc11", result.artists.first().id)
		assertTrue(result.artists.first().detailsIdVerified)
		assertEquals("internal-artist-row-id", result.artists.last().id)
		assertFalse(result.artists.last().detailsIdVerified)
	}

	@Test
	fun aurralDiscoverySummaryPreservesFallbackGenreSections() {
		val summary = aurralDiscoverySummary(
			baseUrl = "https://aurral.example.com/aurral",
			response = AurralDiscoveryResponseDto(
				fallbackGenres = listOf(
					AurralFallbackGenreSectionDto(
						name = "Soundtracks - Games",
						artists = listOf(
							AurralDiscoverArtistDto(
								id = "game-artist",
								name = "Game Artist",
								image = "/api/images/artists/game-artist"
							),
							AurralDiscoverArtistDto(id = null, name = "Missing Id")
						)
					)
				)
			)
		)

		assertEquals(
			listOf(
				AurralFallbackGenreSection(
					genre = "Soundtracks - Games",
					artists = listOf(
						AurralDiscoverArtist(
							id = "game-artist",
							name = "Game Artist",
							imageUrl = "https://aurral.example.com/aurral/api/images/artists/game-artist"
						)
					)
				)
			),
			summary.fallbackGenres
		)
	}

	@Test
	fun aurralDiscoverySummaryTurnsAlbumRecommendationsIntoArtistRowsWithRecommendedAlbums() {
		val summary = aurralDiscoverySummary(
			baseUrl = "https://aurral.example.com/aurral",
			response = AurralDiscoveryResponseDto(
				recommendations = listOf(
					AurralDiscoverArtistDto(
						type = "Album",
						mbid = "release-group-mbid",
						albumName = "Recommended Album",
						artistName = "Recommended Artist",
						artistMbid = "artist-mbid",
						releaseDate = "2026-04-03",
						primaryType = "Album",
						imageUrl = "/api/images/releases/release-group-mbid",
						status = "missing"
					)
				)
			)
		)

		val artist = summary.recommendations.single()
		assertEquals("artist-mbid", artist.id)
		assertEquals("Recommended Artist", artist.name)
		assertEquals("Recommended: Recommended Album", artist.reason)
		assertEquals(
			AurralAlbumSearchItem(
				id = "release-group-mbid",
				title = "Recommended Album",
				artistName = "Recommended Artist",
				artistMbid = "artist-mbid",
				releaseDate = "2026-04-03",
				primaryType = "Album",
				coverUrl = "https://aurral.example.com/aurral/api/images/releases/release-group-mbid",
				status = "missing"
			),
			artist.recommendedAlbums.single()
		)
	}

	@Test
	fun aurralRecentReleasesNormalizesAlbumRows() {
		assertEquals(
			listOf(
				AurralAlbumSearchItem(
					id = "release-group-mbid",
					title = "Recommended Album",
					artistName = "Recommended Artist",
					artistMbid = "artist-mbid",
					releaseDate = "2026-04-03",
					primaryType = "Album",
					coverUrl = "https://aurral.example.com/aurral/api/images/releases/release-group-mbid",
					status = "missing"
				)
			),
			aurralRecentReleases(
				baseUrl = "https://aurral.example.com/aurral",
				response = listOf(
					AurralAlbumSearchItemDto(
						mbid = "release-group-mbid",
						albumName = "Recommended Album",
						artistName = "Recommended Artist",
						artistMbid = "artist-mbid",
						releaseDate = "2026-04-03",
						primaryType = "Album",
						coverUrl = "/api/images/releases/release-group-mbid",
						status = "missing"
					)
				)
			)
		)
	}

	@Test
	fun aurralRecentReleasesAcceptsAlbumImageAliasesAsCoverUrls() {
		assertEquals(
			listOf(
				AurralAlbumSearchItem(
					id = "release-group-mbid",
					title = "Recommended Album",
					artistName = "Recommended Artist",
					artistMbid = "artist-mbid",
					coverUrl = "https://aurral.example.com/aurral/api/images/releases/release-group-mbid"
				)
			),
			aurralRecentReleases(
				baseUrl = "https://aurral.example.com/aurral",
				response = listOf(
					AurralAlbumSearchItemDto(
						mbid = "release-group-mbid",
						albumName = "Recommended Album",
						artistName = "Recommended Artist",
						artistMbid = "artist-mbid",
						imageUrl = "/api/images/releases/release-group-mbid"
					)
				)
			)
		)
	}

	@Test
	fun aurralAlbumTracksDecodeBareReleaseGroupArray() {
		val tracks = aurralAlbumTrackItems(
			decodeAurralAlbumTracks(
				"""
				[
				  {
				    "id": "1512f4c3-d609-4c8f-9c17-67de58a0eb3d",
				    "mbid": "1512f4c3-d609-4c8f-9c17-67de58a0eb3d",
				    "title": "FINAL FANTASY XIII-2 オーバーチュア",
				    "trackName": "FINAL FANTASY XIII-2 オーバーチュア",
				    "trackNumber": 1,
				    "position": 1,
				    "length": 132000
				  }
				]
				""".trimIndent()
			)
		)

		assertEquals(1, tracks.size)
		assertEquals("1512f4c3-d609-4c8f-9c17-67de58a0eb3d", tracks.single().recordingMbid)
		assertEquals("FINAL FANTASY XIII-2 オーバーチュア", tracks.single().title)
		assertEquals(1, tracks.single().trackNumber)
		assertEquals(132000, tracks.single().durationMs)
	}

	@Test
	fun aurralAlbumTracksDecodeWrappedLibraryTracksResponse() {
		val tracks = aurralAlbumTrackItems(
			decodeAurralAlbumTracks(
				"""
				{
				  "tracks": [
				    {
				      "id": "32950",
				      "albumId": "759",
				      "artistId": "81",
				      "mbid": "1512f4c3-d609-4c8f-9c17-67de58a0eb3d",
				      "trackName": "FINAL FANTASY XIII-2 オーバーチュア",
				      "trackNumber": 1,
				      "path": null,
				      "hasFile": true,
				      "size": 0,
				      "quality": null,
				      "status": "missing",
				      "requested": false
				    }
				  ]
				}
				""".trimIndent()
			)
		)

		assertEquals(1, tracks.size)
		assertEquals("32950", tracks.single().id)
		assertEquals("1512f4c3-d609-4c8f-9c17-67de58a0eb3d", tracks.single().recordingMbid)
		assertEquals("FINAL FANTASY XIII-2 オーバーチュア", tracks.single().title)
		assertEquals("missing", tracks.single().status)
		assertEquals(false, tracks.single().requested)
	}

	@Test
	fun repositoryDiscoveryUsesNormalizedBaseUrlAndBasicHeaders(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val apiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(
					AurralDiscoverArtist(id = "artist-mbid", name = "Artist")
				)
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val discovery = repository.getDiscovery().getOrThrow()

		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.discoveryBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.discoveryRequestHeaders
		)
		assertEquals("Artist", discovery.recommendations.single().name)
	}

	@Test
	fun repositoryDiscoveryHydratesMissingRecommendationImageFromExactArtistSearch(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val apiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(
					AurralDiscoverArtist(id = "weak-bond-mbid", name = "Bond", imageUrl = null)
				)
			),
			artistSearch = AurralArtistSearchResult(
				query = "Bond",
				count = 1,
				artists = listOf(
					AurralDiscoverArtist(
						id = "correct-bond-mbid",
						name = "BOND",
						imageUrl = "https://assets.example.com/bond.jpg"
					)
				)
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val discovery = repository.getDiscovery().getOrThrow()

		assertEquals("weak-bond-mbid", discovery.recommendations.single().id)
		assertEquals("https://assets.example.com/bond.jpg", discovery.recommendations.single().imageUrl)
		assertEquals(
			listOf(AurralArtistSearchRequest(query = "Bond", limit = 5, offset = 0)),
			apiClient.artistSearchRequests
		)
	}

	@Test
	fun repositoryLibraryDiscoverySkipsImageHydrationForFastRows(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val apiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(
					AurralDiscoverArtist(id = "weak-bond-mbid", name = "Bond", imageUrl = null)
				)
			),
			artistSearch = AurralArtistSearchResult(
				query = "Bond",
				count = 1,
				artists = listOf(
					AurralDiscoverArtist(
						id = "correct-bond-mbid",
						name = "BOND",
						imageUrl = "https://assets.example.com/bond.jpg"
					)
				)
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val discovery = repository.getLibraryDiscovery().getOrThrow()

		assertEquals("weak-bond-mbid", discovery.recommendations.single().id)
		assertNull(discovery.recommendations.single().imageUrl)
		assertEquals(emptyList(), apiClient.artistSearchRequests)
	}

	@Test
	fun artistEnrichmentSkipsSearchResultWithoutMusicBrainzArtistId(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
		}
		val apiClient = FakeAurralApiClient(
			artistSearch = AurralArtistSearchResult(
				query = "Naoshi Mizuta",
				count = 1,
				artists = listOf(
					AurralDiscoverArtist(
						id = "internal-naoshi-id",
						name = "Naoshi Mizuta"
					)
				)
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)
		val artist = DomainArtist(
			id = "name:Naoshi%20Mizuta",
			name = "Naoshi Mizuta",
			musicBrainzId = null
		)

		assertNull(repository.getArtistEnrichment(artist).getOrThrow())
		assertEquals(
			listOf(AurralArtistSearchRequest(query = "Naoshi Mizuta", limit = 5, offset = 0)),
			apiClient.artistSearchRequests
		)
		assertEquals(emptyList(), apiClient.artistEnrichmentRequests)
	}

	@Test
	fun repositoryDiscoveryCachesLibraryArtistsWithinTtl(): Unit = runBlocking {
		var nowMillis = 1_000L
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond"))
			),
			libraryArtists = listOf(AurralDiscoverArtist(id = "ARTIST-MBID", name = "BOND", monitored = true))
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient,
			nowMillis = { nowMillis }
		)

		assertEquals(true, repository.getLibraryDiscovery().getOrThrow().recommendations.single().monitored)
		nowMillis += AURRAL_LIBRARY_ARTISTS_CACHE_TTL.inWholeMilliseconds - 1
		assertEquals(true, repository.getLibraryDiscovery().getOrThrow().recommendations.single().monitored)

		assertEquals(listOf("https://aurral.example.com"), apiClient.libraryArtistsBaseUrls)

		nowMillis += 2
		repository.getLibraryDiscovery().getOrThrow()

		assertEquals(
			listOf("https://aurral.example.com", "https://aurral.example.com"),
			apiClient.libraryArtistsBaseUrls
		)
	}

	@Test
	fun repositoryDiscoveryPersistsCatalogMetadataAcrossRepositoryInstances(): Unit = runBlocking {
		var nowMillis = 1_000L
		val metadataCache = RecordingAurralMetadataCache()
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val firstApiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond"))
			),
			libraryArtists = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond", monitored = true))
		)
		val firstRepository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = firstApiClient,
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		)

		assertEquals(true, firstRepository.getLibraryDiscovery().getOrThrow().recommendations.single().monitored)

		nowMillis += 1_000L
		val secondApiClient = FakeAurralApiClient(
			discoveryFailure = IllegalStateException("Aurral offline"),
			libraryArtistsFailure = IllegalStateException("Aurral offline")
		)
		val secondRepository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = secondApiClient,
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		)

		val cachedDiscovery = secondRepository.getLibraryDiscovery().getOrThrow()

		assertEquals(true, cachedDiscovery.recommendations.single().monitored)
		assertEquals(emptyList(), secondApiClient.discoveryBaseUrls)
		assertEquals(emptyList(), secondApiClient.libraryArtistsBaseUrls)
	}

	@Test
	fun repositoryDiscoveryFallsBackToStaleCatalogMetadataWhenAurralIsDown(): Unit = runBlocking {
		var nowMillis = 1_000L
		val metadataCache = RecordingAurralMetadataCache()
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = FakeAurralApiClient(
				discovery = AurralDiscoverySummary(
					recommendations = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond"))
				)
			),
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		).getLibraryDiscovery().getOrThrow()

		nowMillis += AURRAL_METADATA_CACHE_FRESH_MILLIS + 1
		val offlineApiClient = FakeAurralApiClient(
			discoveryFailure = IllegalStateException("Aurral offline")
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = offlineApiClient,
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		)

		val cachedDiscovery = repository.getLibraryDiscovery().getOrThrow()

		assertEquals("Bond", cachedDiscovery.recommendations.single().name)
		assertEquals(listOf("https://aurral.example.com"), offlineApiClient.discoveryBaseUrls)
	}

	@Test
	fun repositoryArtistSearchPersistsMetadataAcrossRepositoryInstances(): Unit = runBlocking {
		var nowMillis = 1_000L
		val metadataCache = RecordingAurralMetadataCache()
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = FakeAurralApiClient(
				artistSearch = AurralArtistSearchResult(
					query = "Alex",
					count = 1,
					artists = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Alex Warren"))
				)
			),
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		).searchArtists("Alex").getOrThrow()

		nowMillis += 1_000L
		val offlineApiClient = FakeAurralApiClient(
			artistSearchFailure = IllegalStateException("Aurral offline")
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = offlineApiClient,
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		)

		val cachedSearch = repository.searchArtists("Alex").getOrThrow()

		assertEquals("Alex Warren", cachedSearch.artists.single().name)
		assertEquals(emptyList(), offlineApiClient.artistSearchRequests)
	}

	@Test
	fun repositoryAlbumTracksPersistMetadataAcrossRepositoryInstances(): Unit = runBlocking {
		var nowMillis = 1_000L
		val metadataCache = RecordingAurralMetadataCache()
		val album = AurralAlbumSearchItem(
			id = "release-group-mbid",
			title = "Ori",
			artistName = "Gareth Coker",
			artistMbid = "artist-mbid",
			libraryAlbumId = "album-1"
		)
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = FakeAurralApiClient(
				albumTracks = listOf(
					AurralAlbumTrackItem(
						id = "track-1",
						title = "Ori, Lost In the Storm",
						trackNumber = 1
					)
				)
			),
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		).getAlbumTracks(album).getOrThrow()

		nowMillis += 1_000L
		val offlineApiClient = FakeAurralApiClient(
			albumTracksFailure = IllegalStateException("Aurral offline")
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = offlineApiClient,
			nowMillis = { nowMillis },
			metadataCache = metadataCache
		)

		val cachedTracks = repository.getAlbumTracks(album).getOrThrow()

		assertEquals("Ori, Lost In the Storm", cachedTracks.single().title)
		assertEquals(emptyList(), offlineApiClient.albumTracksRequests)
	}

	@Test
	fun repositoryArtistEnrichmentUsesCachedLibraryMonitoringWithoutPerArtistLookup(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond"))
			),
			libraryArtists = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond", monitored = false)),
			artistEnrichment = AurralArtistEnrichment(
				artistMbid = "artist-mbid",
				artistName = "Bond",
				monitored = null
			),
			libraryArtistMonitoring = true
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		repository.getLibraryDiscovery().getOrThrow()
		val enrichment = repository.getArtistEnrichment(
			DomainArtist(id = "local-bond", name = "BOND", musicBrainzId = "artist-mbid")
		).getOrThrow()

		assertEquals(false, enrichment?.monitored)
		assertEquals(emptyList(), apiClient.libraryArtistMonitoringRequests)
	}

	@Test
	fun repositoryArtistEnrichmentResolvesMissingMbidByNameSearch(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistSearch = AurralArtistSearchResult(
				query = "Naoshi Mizuta",
				artists = listOf(
					AurralDiscoverArtist(id = "other-mbid", name = "Different Artist", detailsIdVerified = true),
					AurralDiscoverArtist(id = "naoshi-mbid", name = "Naoshi Mizuta", detailsIdVerified = true)
				)
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val enrichment = repository.getArtistEnrichment(
			DomainArtist(id = "aurral-name-naoshi-mizuta", name = "Naoshi Mizuta")
		).getOrThrow()

		assertEquals("naoshi-mbid", enrichment?.artistMbid)
		assertEquals("Naoshi Mizuta", enrichment?.artistName)
		assertEquals(
			listOf(AurralArtistSearchRequest(query = "Naoshi Mizuta", limit = 5, offset = 0)),
			apiClient.artistSearchRequests
		)
		assertEquals(listOf("naoshi-mbid"), apiClient.libraryArtistMonitoringRequests)
	}

	@Test
	fun repositoryArtistCoreEnrichmentUsesCoreEndpointWithoutFullSectionFetch(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistCoreEnrichment = AurralArtistEnrichment(
				artistMbid = "artist-mbid",
				artistName = "John Powell",
				bio = "Core profile",
				releaseGroups = listOf(AurralReleaseGroup(id = "httyd", title = "How to Train Your Dragon")),
				previewTracks = emptyList(),
				similarArtists = emptyList(),
				requests = emptyList()
			),
			libraryArtistMonitoring = false
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val enrichment = repository.getArtistCoreEnrichment(
			DomainArtist(id = "john-powell", name = "John Powell", musicBrainzId = "artist-mbid")
		).getOrThrow()

		assertEquals("Core profile", enrichment?.bio)
		assertEquals(listOf("artist-mbid" to "John Powell"), apiClient.artistCoreEnrichmentRequests)
		assertEquals(emptyList(), apiClient.artistEnrichmentRequests)
		assertEquals(false, enrichment?.monitored)
	}

	@Test
	fun repositoryArtistSectionsUseIndependentEndpointsWithoutFullEnrichmentFetch(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistPreviewTracks = listOf(AurralPreviewTrack(id = "test-drive", title = "Test Drive")),
			artistSimilarArtists = listOf(AurralSimilarArtist(id = "similar-mbid", name = "Hans Zimmer")),
			albumRequests = listOf(
				AurralAlbumRequest(
					albumMbid = "httyd",
					albumName = "How to Train Your Dragon",
					artistMbid = "artist-mbid",
					artistName = "John Powell",
					status = "requested"
				)
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)
		val artist = DomainArtist(id = "john-powell", name = "John Powell", musicBrainzId = "artist-mbid")

		assertEquals("Test Drive", repository.getArtistPreviewTracks(artist).getOrThrow().single().title)
		assertEquals("Hans Zimmer", repository.getArtistSimilarArtists(artist).getOrThrow().single().name)
		assertEquals("requested", repository.getArtistAlbumRequests(artist).getOrThrow().single().status)

		assertEquals(listOf("artist-mbid" to "John Powell"), apiClient.artistPreviewTrackRequests)
		assertEquals(listOf("artist-mbid" to "John Powell"), apiClient.artistSimilarArtistRequests)
		assertEquals(listOf("https://aurral.example.com"), apiClient.albumRequestBaseUrls)
		assertEquals(emptyList(), apiClient.artistEnrichmentRequests)
	}

	@Test
	fun repositoryMonitoringActionQueuesConfirmationWithoutOverridingCachedRows(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond"))
			),
			libraryArtists = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Bond", monitored = false))
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		assertEquals(false, repository.getLibraryDiscovery().getOrThrow().recommendations.single().monitored)

		repository.setArtistMonitoring(
			artist = DomainArtist(id = "local-bond", name = "BOND", musicBrainzId = "artist-mbid"),
			monitored = true
		).getOrThrow()

		assertEquals(false, repository.getLibraryDiscovery().getOrThrow().recommendations.single().monitored)
		assertEquals(AurralConfirmationStatus.Pending, repository.confirmationQueue.value.single().status)
		assertEquals(listOf("https://aurral.example.com"), apiClient.libraryArtistsBaseUrls)
	}

	@Test
	fun repositoryArtistSearchUsesNormalizedBaseUrlHeadersAndTrimmedQuery(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val apiClient = FakeAurralApiClient(
			artistSearch = AurralArtistSearchResult(
				query = "Alex",
				count = 1,
				artists = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Alex Warren"))
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val result = repository.searchArtists(" Alex ").getOrThrow()

		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.artistSearchBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.artistSearchRequestHeaders
		)
		assertEquals(listOf(AurralArtistSearchRequest(query = "Alex", limit = 12, offset = 0)), apiClient.artistSearchRequests)
		assertEquals("Alex Warren", result.artists.single().name)
	}

	@Test
	fun repositoryAlbumSearchUsesNormalizedBaseUrlHeadersAndTrimmedQuery(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val apiClient = FakeAurralApiClient(
			albumSearch = AurralAlbumSearchResult(
				query = "Celebrity",
				count = 1,
				albums = listOf(
					AurralAlbumSearchItem(
						id = "release-group-mbid",
						title = "Celebrity",
						artistName = "IU",
						artistMbid = "artist-mbid"
					)
				)
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val result = repository.searchAlbums(" Celebrity ").getOrThrow()

		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.albumSearchBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.albumSearchRequestHeaders
		)
		assertEquals(listOf(AurralAlbumSearchRequest(query = "Celebrity", limit = 12, offset = 0)), apiClient.albumSearchRequests)
		assertEquals("Celebrity", result.albums.single().title)
	}

	@Test
	fun repositoryMonitorDiscoveredArtistUsesAurralArtistPayload(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
			aurralUsername = "user"
			aurralPassword = "pass"
		}
		val apiClient = FakeAurralApiClient(
			libraryArtistMonitoring = false
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		repository.monitorDiscoveredArtist(
			AurralDiscoverArtist(id = "artist-mbid", name = "Alex Warren")
		).getOrThrow()

		assertEquals(listOf("https://aurral.example.com"), apiClient.monitorArtistBaseUrls)
		assertEquals(listOf("artist-mbid"), apiClient.monitorArtistIds)
		assertEquals(
			listOf(
				AurralArtistMonitorPayload(
					foreignArtistId = "artist-mbid",
					artistName = "Alex Warren",
					monitorOption = "all",
					monitored = true
				)
			),
			apiClient.monitorArtistPayloads
		)
		assertEquals(
			AurralConfirmationStatus.Pending,
			repository.confirmationQueue.value.single().status
		)
		assertEquals(
			AurralConfirmationType.ArtistMonitoring,
			repository.confirmationQueue.value.single().type
		)
		assertEquals(true, repository.confirmationQueue.value.single().expectedMonitored)
	}

	@Test
	fun repositoryUnmonitorsArtistWithNonePayload(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
			aurralUsername = "user"
			aurralPassword = "pass"
		}
		val apiClient = FakeAurralApiClient(
			libraryArtistMonitoring = false
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		repository.setArtistMonitoring(
			artist = DomainArtist(
				id = "artist-id",
				name = "Alex Warren",
				musicBrainzId = "artist-mbid"
			),
			monitored = false
		).getOrThrow()

		assertEquals(listOf("https://aurral.example.com"), apiClient.monitorArtistBaseUrls)
		assertEquals(listOf("artist-mbid"), apiClient.monitorArtistIds)
		assertEquals(
			listOf(
				AurralArtistMonitorPayload(
					foreignArtistId = "artist-mbid",
					artistName = "Alex Warren",
					monitorOption = "none",
					monitored = false
				)
			),
			apiClient.monitorArtistPayloads
		)
	}

	@Test
	fun repositoryCancelsAlbumAcquisitionByAlbumId(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com/aurral/"
			aurralUsername = "user"
			aurralPassword = "pass"
		}
		val apiClient = FakeAurralApiClient()
		val repository = AurralRepository(preferenceManager, apiClient)

		repository.cancelAcquisitionRequest(
			AurralAcquisitionQueueItem(
				id = "lidarr-history-10320",
				type = "album",
				albumId = "337",
				albumMbid = "album-mbid",
				albumName = "Time of Your Life",
				artistId = "artist-id",
				artistMbid = "artist-mbid",
				artistName = "2CELLOS",
				status = "processing",
				requestedAt = null,
				inQueue = false
			)
		).getOrThrow()

		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.cancelAcquisitionBaseUrls)
		val expectedTargets: List<AurralAcquisitionDeleteTarget> =
			listOf(AurralAcquisitionDeleteTarget.Album("337"))
		assertEquals(expectedTargets, apiClient.cancelAcquisitionTargets)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.cancelAcquisitionRequestHeaders
		)
	}

	@Test
	fun repositoryRetriesFailedAlbumAcquisitionWithAlbumRequestPayload(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient()
		val repository = AurralRepository(preferenceManager, apiClient)

		repository.retryAcquisitionRequest(
			AurralAcquisitionQueueItem(
				id = "request-1",
				type = "album",
				albumId = "337",
				albumMbid = "album-mbid",
				albumName = "Time of Your Life",
				artistId = "artist-id",
				artistMbid = "artist-mbid",
				artistName = "2CELLOS",
				status = "failed",
				requestedAt = null,
				inQueue = false
			)
		).getOrThrow()

		assertEquals(
			listOf(
				AurralAlbumRequestPayload(
					albumMbid = "album-mbid",
					albumName = "Time of Your Life",
					artistMbid = "artist-mbid",
					artistName = "2CELLOS",
					triggerSearch = true
				)
			),
			apiClient.requestAlbumPayloads
		)
	}

	@Test
	fun repositoryArtistEnrichmentSurfacesLidarrMonitoringState() {
		val enrichment = aurralArtistEnrichment(
			baseUrl = "https://aurral.example.com",
			details = AurralArtistDetailsDto(
				id = "artist-mbid",
				name = "Alex Warren",
				lidarrData = AurralArtistLidarrDataDto(monitored = true)
			),
			preview = AurralArtistPreviewDto(),
			similar = AurralSimilarArtistsDto(),
			requests = emptyList()
		)

		assertEquals(true, enrichment.monitored)
	}

	@Test
	fun repositoryArtistEnrichmentSurfacesFullAurralProfileFields() {
		val enrichment = aurralArtistEnrichment(
			baseUrl = "https://aurral.example.com",
			details = AurralArtistDetailsDto(
				id = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
				name = "John Powell",
				bio = "English score composer.",
				genres = listOf("Soundtrack", "Classical"),
				links = listOf(
					AurralExternalLinkDto(
						type = "musicbrainz",
						target = "https://musicbrainz.org/artist/52bb713d-b0c9-4bf6-9f58-392388d5cc11"
					)
				),
				relations = listOf(
					AurralRelationDto(
						type = "imdb",
						url = AurralRelationUrlDto(resource = "https://www.imdb.com/name/nm0694173/")
					)
				)
			),
			preview = AurralArtistPreviewDto(),
			similar = AurralSimilarArtistsDto(),
			requests = emptyList()
		)

		assertEquals("English score composer.", enrichment.bio)
		assertEquals(listOf("Soundtrack", "Classical"), enrichment.genres)
		assertEquals(
			listOf(
				AurralArtistExternalLink(type = "musicbrainz", url = "https://musicbrainz.org/artist/52bb713d-b0c9-4bf6-9f58-392388d5cc11"),
				AurralArtistExternalLink(type = "imdb", url = "https://www.imdb.com/name/nm0694173/")
			),
			enrichment.externalLinks
		)
	}

	@Test
	fun repositoryArtistEnrichmentNormalizesSimilarArtistImageUrls() {
		val enrichment = aurralArtistEnrichment(
			baseUrl = "https://aurral.example.com",
			details = AurralArtistDetailsDto(
				id = "artist-mbid",
				name = "IU"
			),
			preview = AurralArtistPreviewDto(),
			similar = AurralSimilarArtistsDto(
				artists = listOf(
					AurralSimilarArtistDto(
						id = "heize-mbid",
						name = "Heize",
						image = "/api/artists/heize/image",
						match = 82
					)
				)
			),
			requests = emptyList()
		)

		val artist = enrichment.similarArtists.single()

		assertEquals("https://aurral.example.com/api/artists/heize/image", artist.imageUrl)
		assertEquals(82, artist.matchPercent)
	}

	@Test
	fun repositoryArtistEnrichmentUsesLibraryMonitoringWhenDetailsDoNotIncludeLidarrState(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistEnrichment = AurralArtistEnrichment(
				artistMbid = "artist-mbid",
				artistName = "Bond",
				monitored = null
			),
			libraryArtistMonitoring = true
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val enrichment = repository.getArtistEnrichment(
			DomainArtist(id = "local-bond", name = "BOND", musicBrainzId = "artist-mbid")
		).getOrThrow()

		assertEquals(true, enrichment?.monitored)
		assertEquals(listOf("artist-mbid"), apiClient.libraryArtistMonitoringRequests)
	}

	@Test
	fun repositoryArtistEnrichmentTreatsMissingLibraryArtistAsVerifiedUnmonitored(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistEnrichment = AurralArtistEnrichment(
				artistMbid = "artist-mbid",
				artistName = "Bond",
				monitored = null
			),
			libraryArtistMonitoring = false
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val enrichment = repository.getArtistEnrichment(
			DomainArtist(id = "local-bond", name = "BOND", musicBrainzId = "artist-mbid")
		).getOrThrow()

		assertEquals(false, enrichment?.monitored)
	}

	@Test
	fun repositoryArtistEnrichmentSurfacesLibraryMonitoringAuthFailures(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistEnrichment = AurralArtistEnrichment(
				artistMbid = "artist-mbid",
				artistName = "Bond",
				monitored = null
			),
			libraryArtistMonitoringFailure = IllegalStateException("Aurral library artist lookup: HTTP 401 Unauthorized")
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val result = repository.getArtistEnrichment(
			DomainArtist(id = "local-bond", name = "BOND", musicBrainzId = "artist-mbid")
		)

		assertTrue(result.isFailure)
		assertEquals(
			"Aurral library artist lookup: HTTP 401 Unauthorized",
			result.exceptionOrNull()?.message
		)
	}

	@Test
	fun repositoryCachedArtistEnrichmentReadsProfileWithoutNetworkFetch(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient()
		val cache = RecordingAurralMetadataCache()
		val cachedProfile = AurralArtistEnrichment(
			artistMbid = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
			artistName = "John Powell",
			bio = "Cached biography",
			releaseGroups = listOf(
				AurralReleaseGroup(
					id = "how-to-train-your-dragon",
					title = "How to Train Your Dragon: Music From the Motion Picture"
				)
			),
			monitored = false
		)
		val baseUrl = "https://aurral.example.com"
		val path = aurralArtistEnrichmentCachePath(
			artistMbid = cachedProfile.artistMbid,
			artistName = cachedProfile.artistName
		)
		val cacheKey = aurralMetadataCacheKey(
			baseUrl = baseUrl,
			payloadType = AurralMetadataPayloadType.ArtistEnrichment,
			path = path
		)
		cache.put(
			AurralMetadataCacheRecord(
				cacheKey = cacheKey,
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.ArtistEnrichment,
				path = path,
				payloadJson = AURRAL_JSON.encodeToString(cachedProfile),
				updatedAtMillis = 0L
			)
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient,
			metadataCache = cache
		)

		val cached = repository.getCachedArtistEnrichment(
			DomainArtist(
				id = "john-powell",
				name = "John Powell",
				musicBrainzId = "52bb713d-b0c9-4bf6-9f58-392388d5cc11"
			)
		).getOrThrow()

		assertEquals(cachedProfile, cached)
		assertTrue(apiClient.artistEnrichmentRequests.isEmpty())
	}

	@Test
	fun repositoryKeepsOptimisticAlbumRequestForImmediateArtistRefresh(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistEnrichment = AurralArtistEnrichment(
				artistMbid = "artist-mbid",
				artistName = "Alex Warren",
				releaseGroups = listOf(AurralReleaseGroup(id = "album-mbid", title = "You'll Be Alright, Kid"))
			)
		)
		val repository = AurralRepository(preferenceManager, apiClient)
		val artist = DomainArtist(
			id = "artist-id",
			name = "Alex Warren",
			musicBrainzId = "artist-mbid"
		)

		repository.requestAlbum(
			artist = artist,
			releaseGroup = AurralReleaseGroup(id = "album-mbid", title = "You'll Be Alright, Kid")
		).getOrThrow()

		val request = repository.getArtistEnrichment(artist).getOrThrow()?.requests?.single()

		assertEquals(
			AurralAlbumRequest(
				albumMbid = "album-mbid",
				albumName = "You'll Be Alright, Kid",
				artistMbid = "artist-mbid",
				artistName = "Alex Warren",
				status = "requested"
			),
			request
		)
	}

	@Test
	fun repositoryReusesResolvedReleaseGroupCoverInArtistEnrichment(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			artistEnrichment = AurralArtistEnrichment(
				artistMbid = "artist-mbid",
				artistName = "Alex Warren",
				releaseGroups = listOf(AurralReleaseGroup(id = "album-mbid", title = "You'll Be Alright, Kid"))
			),
			releaseGroupCoverImageUrl = "https://aurral.example.com/covers/album.webp"
		)
		val repository = AurralRepository(preferenceManager, apiClient)
		val artist = DomainArtist(
			id = "artist-id",
			name = "Alex Warren",
			musicBrainzId = "artist-mbid"
		)

		assertEquals(
			"https://aurral.example.com/covers/album.webp",
			repository.getReleaseGroupCoverImageUrl(
				releaseGroup = AurralReleaseGroup(id = "album-mbid", title = "You'll Be Alright, Kid"),
				artistName = "Alex Warren"
			).getOrThrow()
		)

		assertEquals(
			"https://aurral.example.com/covers/album.webp",
			repository.getArtistEnrichment(artist).getOrThrow()?.releaseGroups?.single()?.coverUrl
		)
	}

	@Test
	fun aurralDefaultFlowCreatePayloadMatchesAurralWebDefaults() {
		assertEquals(
			AurralFlowCreatePayload(
				name = "Discover 2",
				size = 30,
				mix = AurralFlowMix(discover = 34, mix = 33, trending = 33, focus = 0),
				scheduleDays = listOf(3),
				scheduleTime = "00:00"
			),
			aurralDefaultFlowCreatePayload(
				name = " Discover 2 ",
				size = 30,
				scheduleDay = 3
			)
		)
	}

	@Test
	fun repositoryTestConnectionRequiresConfiguredBaseUrl(): Unit = runBlocking {
		val apiClient = FakeAurralApiClient()
		val repository = AurralRepository(
			preferenceManager = PreferenceManager(MapSettings()).apply {
				aurralEnabled = true
			},
			apiClient = apiClient
		)

		assertEquals(
			AurralConnectionResult.Failed(AURRAL_BASE_URL_REQUIRED_MESSAGE),
			repository.testConnection()
		)
		assertEquals(emptyList(), apiClient.connectionBaseUrls)
	}

	@Test
	fun disabledRepositoryDoesNotCallAurralApi(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = false
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(AurralDiscoverArtist(id = "artist-mbid", name = "Artist"))
			)
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient
		)
		val artist = DomainArtist(
			id = "artist-1",
			name = "Artist",
			musicBrainzId = "artist-mbid"
		)

		assertEquals(AurralConnectionResult.Failed(AURRAL_DISABLED_MESSAGE), repository.testConnection())
		assertEquals(AurralDiscoverySummary(), repository.getDiscovery().getOrThrow())
		assertEquals(AurralArtistSearchResult(), repository.searchArtists("Artist").getOrThrow())
		assertNull(repository.getArtistEnrichment(artist).getOrThrow())
		assertTrue(
			repository.requestAlbum(
				artist = artist,
				releaseGroup = AurralReleaseGroup(id = "album-mbid", title = "Album")
			).isFailure
		)
		assertTrue(repository.monitorArtist(artist).isFailure)

		assertEquals(emptyList(), apiClient.connectionBaseUrls)
		assertEquals(emptyList(), apiClient.discoveryBaseUrls)
		assertEquals(emptyList(), apiClient.artistSearchBaseUrls)
		assertEquals(emptyList(), apiClient.requestAlbumPayloads)
		assertEquals(emptyList(), apiClient.monitorArtistBaseUrls)
	}

	@Test
	fun disablingAurralClearsPendingConfirmationWork(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val apiClient = FakeAurralApiClient(
			libraryArtistMonitoring = false
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient,
			confirmationWorkerEnabled = true
		)
		val artist = DomainArtist(
			id = "artist-1",
			name = "Artist",
			musicBrainzId = "artist-mbid"
		)

		repository.monitorArtist(artist).getOrThrow()
		assertEquals(AurralConfirmationStatus.Pending, repository.confirmationQueue.value.single().status)

		preferenceManager.aurralEnabled = false

		assertEquals(emptyList(), repository.confirmationQueue.value)
		assertEquals(1, apiClient.monitorArtistBaseUrls.size)
	}

	@Test
	fun repositoryTestConnectionUsesNormalizedBaseUrlAndBasicHeaders(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val apiClient = FakeAurralApiClient(
			connectionResult = AurralConnectionResult.Connected
		)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient
		)

		assertEquals(AurralConnectionResult.Connected, repository.testConnection())
		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.connectionBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.connectionRequestHeaders
		)
	}

	@Test
	fun repositoryServiceStatusUsesNormalizedBaseUrlAndBasicHeaders(): Unit = runBlocking {
		val serviceStatus = AurralServiceStatus(
			healthStatus = "ok",
			appVersion = "1.2.3",
			authRequired = true,
			username = "darka",
			role = "admin",
			accessFlow = true,
			addArtist = true,
			addAlbum = true,
			lidarrConfigured = true,
			discoveryRecommendationsCount = 12,
			discoveryUpdating = false,
			flowsCount = 2,
			enabledFlowsCount = 1,
			sharedPlaylistsCount = 1,
			requestsCount = 3,
			flowTracksTotal = 4,
			flowTracksPending = 1,
			flowTracksDownloading = 1,
			flowTracksDone = 2,
			flowTracksFailed = 0,
			flowPhase = "idle",
			flowMessage = "Idle"
		)
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com/"
			aurralUsername = "user"
			aurralPassword = "pass"
		}
		val apiClient = FakeAurralApiClient(serviceStatus = serviceStatus)
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient
		)

		assertEquals(serviceStatus, repository.getServiceStatus().getOrThrow())
		assertEquals(listOf("https://aurral.example.com"), apiClient.statusBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.statusRequestHeaders
		)
	}

	@Test
	fun repositoryCreateFlowUsesNormalizedBaseUrlHeadersAndDefaultPayload(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com/aurral/"
			aurralUsername = "user"
			aurralPassword = "pass"
		}
		val apiClient = FakeAurralApiClient()
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient
		)

		val result = repository.createFlow(
			name = " Training ",
			size = 25,
			scheduleDay = 5
		).getOrThrow()

		assertTrue(result.success)
		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.createFlowBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.createFlowRequestHeaders
		)
		assertEquals(
			listOf(
				AurralFlowCreatePayload(
					name = "Training",
					size = 25,
					mix = AurralFlowMix(discover = 34, mix = 33, trending = 33, focus = 0),
					scheduleDays = listOf(5),
					scheduleTime = "00:00"
				)
			),
			apiClient.createFlowPayloads
		)
	}

	@Test
	fun repositoryFlowActionsUseNormalizedBaseUrlHeadersAndFlowIds(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com/"
			aurralUsername = "user"
			aurralPassword = "pass"
		}
		val apiClient = FakeAurralApiClient()
		val repository = AurralRepository(
			preferenceManager = preferenceManager,
			apiClient = apiClient
		)

		repository.setFlowEnabled(" flow-1 ", true).getOrThrow()
		repository.startFlow(" flow-1 ", limit = 30).getOrThrow()

		assertEquals(listOf("https://aurral.example.com"), apiClient.setFlowEnabledBaseUrls)
		assertEquals(listOf("flow-1" to true), apiClient.setFlowEnabledRequests)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.setFlowEnabledRequestHeaders
		)
		assertEquals(listOf("https://aurral.example.com"), apiClient.startFlowBaseUrls)
		assertEquals(listOf("flow-1" to 30), apiClient.startFlowRequests)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.startFlowRequestHeaders
		)
	}

	@Test
	fun repositoryFlowPlayableSongsUseDoneJobsAndSessionToken(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com/aurral/"
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val apiClient = FakeAurralApiClient(
			flowJobs = listOf(
				AurralFlowJobDto(
					id = "job-ready",
					artistName = "Alex Warren",
					trackName = "Heaven Without You",
					albumName = "You'll Be Alright, Kid",
					status = "done",
					playlistType = "flow-1",
					artistMbid = "artist-mbid",
					albumMbid = "album-mbid",
					trackMbid = "track-mbid",
					releaseYear = "2025",
					durationMs = 187000
				),
				AurralFlowJobDto(
					id = "job-pending",
					artistName = "Pending Artist",
					trackName = "Pending Track",
					status = "pending",
					playlistType = "flow-1"
				)
			),
			sessionToken = "session-token"
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val songs = repository.getFlowPlayableSongs(" flow-1 ").getOrThrow()

		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.loginBaseUrls)
		assertEquals(listOf("user" to "pass"), apiClient.loginRequests)
		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.fetchFlowJobsBaseUrls)
		assertEquals(listOf("flow-1" to 200), apiClient.fetchFlowJobsRequests)
		assertEquals(1, songs.size)
		val song = songs.first()
		assertEquals("${AurralFlowSongIdPrefix}job-ready", song.id)
		assertEquals("Heaven Without You", song.title)
		assertEquals("Alex Warren", song.artistName)
		assertEquals("You'll Be Alright, Kid", song.albumTitle)
		assertEquals("artist-mbid", song.artistId)
		assertEquals("album-mbid", song.albumId)
		assertEquals("track-mbid", song.musicBrainzId)
		assertEquals(2025, song.year)
		assertEquals(187000.milliseconds, song.duration)
		assertEquals(
			"https://aurral.example.com/aurral/api/weekly-flow/stream/job-ready?token=session-token",
			song.filePath
		)
	}

	@Test
	fun repositoryFlowPlayableSongsCanFallbackToShortStreamToken(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com/"
			aurralUsername = "user"
			aurralPassword = "pass"
		}
		val apiClient = FakeAurralApiClient(
			flowJobs = listOf(
				AurralFlowJobDto(
					id = "job-ready",
					artistName = "Artist",
					trackName = "Track",
					status = "done"
				)
			),
			sessionToken = null,
			streamToken = "stream-token"
		)
		val repository = AurralRepository(preferenceManager, apiClient)

		val songs = repository.getFlowPlayableSongs("flow-1").getOrThrow()

		assertEquals(
			"https://aurral.example.com/api/weekly-flow/stream/job-ready?st=stream-token",
			songs.single().filePath
		)
		assertEquals(listOf("https://aurral.example.com"), apiClient.streamTokenBaseUrls)
	}

	private class FakeAurralApiClient(
		private val connectionResult: AurralConnectionResult = AurralConnectionResult.Connected,
		private val serviceStatus: AurralServiceStatus = AurralServiceStatus(),
		private val discovery: AurralDiscoverySummary = AurralDiscoverySummary(),
		private val discoveryFailure: Exception? = null,
		private val libraryArtists: List<AurralDiscoverArtist> = discovery.libraryArtists,
		private val libraryArtistsFailure: Exception? = null,
		private val artistSearch: AurralArtistSearchResult = AurralArtistSearchResult(),
		private val artistSearchFailure: Exception? = null,
		private val albumSearch: AurralAlbumSearchResult = AurralAlbumSearchResult(),
		private val albumSearchFailure: Exception? = null,
		private val albumTracks: List<AurralAlbumTrackItem> = emptyList(),
		private val albumTracksFailure: Exception? = null,
		private val artistEnrichment: AurralArtistEnrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "Artist"
		),
		private val artistCoreEnrichment: AurralArtistEnrichment = artistEnrichment,
		private val artistPreviewTracks: List<AurralPreviewTrack> = artistEnrichment.previewTracks,
		private val artistSimilarArtists: List<AurralSimilarArtist> = artistEnrichment.similarArtists,
		private val albumRequests: List<AurralAlbumRequest> = artistEnrichment.requests,
		private val libraryArtistMonitoring: Boolean? = null,
		private val libraryArtistMonitoringFailure: Exception? = null,
		private val releaseGroupCoverImageUrl: String? = null,
		private val flowJobs: List<AurralFlowJobDto> = emptyList(),
		private val sessionToken: String? = null,
		private val streamToken: String? = null
	) : AurralApiClient {
		val connectionBaseUrls = mutableListOf<String>()
		val connectionRequestHeaders = mutableListOf<Map<String, String>>()
		val statusBaseUrls = mutableListOf<String>()
		val statusRequestHeaders = mutableListOf<Map<String, String>>()
		val discoveryBaseUrls = mutableListOf<String>()
		val discoveryRequestHeaders = mutableListOf<Map<String, String>>()
		val libraryArtistsBaseUrls = mutableListOf<String>()
		val libraryArtistsRequestHeaders = mutableListOf<Map<String, String>>()
		val artistSearchBaseUrls = mutableListOf<String>()
		val artistSearchRequestHeaders = mutableListOf<Map<String, String>>()
		val artistSearchRequests = mutableListOf<AurralArtistSearchRequest>()
		val albumSearchBaseUrls = mutableListOf<String>()
		val albumSearchRequestHeaders = mutableListOf<Map<String, String>>()
		val albumSearchRequests = mutableListOf<AurralAlbumSearchRequest>()
		val createFlowBaseUrls = mutableListOf<String>()
		val createFlowRequestHeaders = mutableListOf<Map<String, String>>()
		val createFlowPayloads = mutableListOf<AurralFlowCreatePayload>()
		val setFlowEnabledBaseUrls = mutableListOf<String>()
		val setFlowEnabledRequestHeaders = mutableListOf<Map<String, String>>()
		val setFlowEnabledRequests = mutableListOf<Pair<String, Boolean>>()
		val startFlowBaseUrls = mutableListOf<String>()
		val startFlowRequestHeaders = mutableListOf<Map<String, String>>()
		val startFlowRequests = mutableListOf<Pair<String, Int>>()
		val fetchFlowJobsBaseUrls = mutableListOf<String>()
		val fetchFlowJobsRequestHeaders = mutableListOf<Map<String, String>>()
		val fetchFlowJobsRequests = mutableListOf<Pair<String, Int>>()
		val loginBaseUrls = mutableListOf<String>()
		val loginRequestHeaders = mutableListOf<Map<String, String>>()
		val loginRequests = mutableListOf<Pair<String, String>>()
		val streamTokenBaseUrls = mutableListOf<String>()
		val streamTokenRequestHeaders = mutableListOf<Map<String, String>>()
		val monitorArtistBaseUrls = mutableListOf<String>()
		val monitorArtistRequestHeaders = mutableListOf<Map<String, String>>()
		val monitorArtistIds = mutableListOf<String>()
		val monitorArtistPayloads = mutableListOf<AurralArtistMonitorPayload>()
		val libraryArtistMonitoringRequests = mutableListOf<String>()
		val cancelAcquisitionBaseUrls = mutableListOf<String>()
		val cancelAcquisitionRequestHeaders = mutableListOf<Map<String, String>>()
		val cancelAcquisitionTargets = mutableListOf<AurralAcquisitionDeleteTarget>()
		val requestAlbumPayloads = mutableListOf<AurralAlbumRequestPayload>()
		val artistEnrichmentRequests = mutableListOf<Pair<String, String>>()
		val artistCoreEnrichmentRequests = mutableListOf<Pair<String, String>>()
		val artistPreviewTrackRequests = mutableListOf<Pair<String, String>>()
		val artistSimilarArtistRequests = mutableListOf<Pair<String, String>>()
		val albumRequestBaseUrls = mutableListOf<String>()
		val albumTracksRequests = mutableListOf<Pair<String, String?>>()

		override suspend fun testConnection(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): AurralConnectionResult {
			connectionBaseUrls += baseUrl
			connectionRequestHeaders += requestHeaders
			return connectionResult
		}

		override suspend fun fetchServiceStatus(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): AurralServiceStatus {
			statusBaseUrls += baseUrl
			statusRequestHeaders += requestHeaders
			return serviceStatus
		}

		override suspend fun fetchDiscovery(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): AurralDiscoverySummary {
			discoveryBaseUrls += baseUrl
			discoveryRequestHeaders += requestHeaders
			discoveryFailure?.let { throw it }
			return discovery
		}

		override suspend fun fetchLibraryArtists(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): List<AurralDiscoverArtist> {
			libraryArtistsBaseUrls += baseUrl
			libraryArtistsRequestHeaders += requestHeaders
			libraryArtistsFailure?.let { throw it }
			return libraryArtists
		}

		override suspend fun searchArtists(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			request: AurralArtistSearchRequest
		): AurralArtistSearchResult {
			artistSearchBaseUrls += baseUrl
			artistSearchRequestHeaders += requestHeaders
			artistSearchRequests += request
			artistSearchFailure?.let { throw it }
			return artistSearch
		}

		override suspend fun searchAlbums(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			request: AurralAlbumSearchRequest
		): AurralAlbumSearchResult {
			albumSearchBaseUrls += baseUrl
			albumSearchRequestHeaders += requestHeaders
			albumSearchRequests += request
			albumSearchFailure?.let { throw it }
			return albumSearch
		}

		override suspend fun fetchAlbumTracks(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			releaseGroupMbid: String,
			libraryAlbumId: String?
		): List<AurralAlbumTrackItem> {
			albumTracksRequests += releaseGroupMbid to libraryAlbumId
			albumTracksFailure?.let { throw it }
			return albumTracks
		}

		override suspend fun fetchArtistEnrichment(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String,
			artistName: String
		): AurralArtistEnrichment {
			artistEnrichmentRequests += artistMbid to artistName
			return artistEnrichment.copy(
				artistMbid = artistMbid,
				artistName = artistName
			)
		}

		override suspend fun fetchArtistCoreEnrichment(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String,
			artistName: String
		): AurralArtistEnrichment {
			artistCoreEnrichmentRequests += artistMbid to artistName
			return artistCoreEnrichment.copy(
				artistMbid = artistMbid,
				artistName = artistName
			)
		}

		override suspend fun fetchArtistPreviewTracks(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String,
			artistName: String
		): List<AurralPreviewTrack> {
			artistPreviewTrackRequests += artistMbid to artistName
			return artistPreviewTracks
		}

		override suspend fun fetchArtistSimilarArtists(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String,
			artistName: String
		): List<AurralSimilarArtist> {
			artistSimilarArtistRequests += artistMbid to artistName
			return artistSimilarArtists
		}

		override suspend fun fetchAlbumRequests(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): List<AurralAlbumRequest> {
			albumRequestBaseUrls += baseUrl
			return albumRequests
		}

		override suspend fun fetchLibraryArtistMonitoring(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String
		): Boolean? {
			libraryArtistMonitoringRequests += artistMbid
			libraryArtistMonitoringFailure?.let { throw it }
			return libraryArtistMonitoring
		}

		override suspend fun requestAlbum(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			payload: AurralAlbumRequestPayload
		) {
			requestAlbumPayloads += payload
		}

		override suspend fun cancelAcquisitionRequest(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			target: AurralAcquisitionDeleteTarget
		) {
			cancelAcquisitionBaseUrls += baseUrl
			cancelAcquisitionRequestHeaders += requestHeaders
			cancelAcquisitionTargets += target
		}

		override suspend fun monitorArtist(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String,
			payload: AurralArtistMonitorPayload
		) {
			monitorArtistBaseUrls += baseUrl
			monitorArtistRequestHeaders += requestHeaders
			monitorArtistIds += artistMbid
			monitorArtistPayloads += payload
		}

		override suspend fun fetchReleaseGroupCoverImageUrl(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			releaseGroupMbid: String,
			artistName: String,
			albumTitle: String
		): String? = releaseGroupCoverImageUrl

		override suspend fun createFlow(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			payload: AurralFlowCreatePayload
		): AurralFlowActionResult {
			createFlowBaseUrls += baseUrl
			createFlowRequestHeaders += requestHeaders
			createFlowPayloads += payload
			return AurralFlowActionResult(success = true, flowId = "flow-created")
		}

		override suspend fun setFlowEnabled(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			flowId: String,
			enabled: Boolean
		): AurralFlowActionResult {
			setFlowEnabledBaseUrls += baseUrl
			setFlowEnabledRequestHeaders += requestHeaders
			setFlowEnabledRequests += flowId to enabled
			return AurralFlowActionResult(success = true, flowId = flowId, enabled = enabled)
		}

		override suspend fun startFlow(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			flowId: String,
			limit: Int
		): AurralFlowActionResult {
			startFlowBaseUrls += baseUrl
			startFlowRequestHeaders += requestHeaders
			startFlowRequests += flowId to limit
			return AurralFlowActionResult(success = true, flowId = flowId, tracksQueued = limit)
		}

		override suspend fun fetchFlowJobs(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			flowId: String,
			limit: Int
		): List<AurralFlowJobDto> {
			fetchFlowJobsBaseUrls += baseUrl
			fetchFlowJobsRequestHeaders += requestHeaders
			fetchFlowJobsRequests += flowId to limit
			return flowJobs
		}

		override suspend fun login(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			username: String,
			password: String
		): AurralAuthSessionDto? {
			loginBaseUrls += baseUrl
			loginRequestHeaders += requestHeaders
			loginRequests += username to password
			return sessionToken?.let { AurralAuthSessionDto(token = it) }
		}

		override suspend fun fetchStreamToken(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): AurralStreamTokenDto? {
			streamTokenBaseUrls += baseUrl
			streamTokenRequestHeaders += requestHeaders
			return streamToken?.let { AurralStreamTokenDto(token = it) }
		}
	}

	private class RecordingAurralMetadataCache : AurralMetadataCache {
		val records = linkedMapOf<String, AurralMetadataCacheRecord>()
		val clearedBaseUrls = mutableListOf<String>()

		override suspend fun get(cacheKey: String): AurralMetadataCacheRecord? =
			records[cacheKey]

		override suspend fun put(record: AurralMetadataCacheRecord) {
			records[record.cacheKey] = record
		}

		override suspend fun clearBaseUrl(baseUrl: String) {
			clearedBaseUrls += baseUrl
			records.values.removeAll { it.baseUrl == baseUrl }
		}
	}
}
