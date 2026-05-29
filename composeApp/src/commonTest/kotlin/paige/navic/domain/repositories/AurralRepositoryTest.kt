package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.manager.PreferenceManager

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
				AurralRequestDto(id = "request-1"),
				AurralRequestDto(id = "request-2")
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
		assertEquals(6, status.flowTracksTotal)
		assertEquals(1, status.flowTracksPending)
		assertEquals(2, status.flowTracksDownloading)
		assertEquals(3, status.flowTracksDone)
		assertEquals(0, status.flowTracksFailed)
		assertEquals("downloading", status.flowPhase)
		assertEquals("Downloading track", status.flowMessage)
	}

	@Test
	fun repositoryTestConnectionRequiresConfiguredBaseUrl(): Unit = runBlocking {
		val apiClient = FakeAurralApiClient()
		val repository = AurralRepository(
			preferenceManager = PreferenceManager(MapSettings()),
			apiClient = apiClient
		)

		assertEquals(
			AurralConnectionResult.Failed(AURRAL_BASE_URL_REQUIRED_MESSAGE),
			repository.testConnection()
		)
		assertEquals(emptyList(), apiClient.connectionBaseUrls)
	}

	@Test
	fun repositoryTestConnectionUsesNormalizedBaseUrlAndBasicHeaders(): Unit = runBlocking {
		val preferenceManager = PreferenceManager(MapSettings()).apply {
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

	private class FakeAurralApiClient(
		private val connectionResult: AurralConnectionResult = AurralConnectionResult.Connected,
		private val serviceStatus: AurralServiceStatus = AurralServiceStatus()
	) : AurralApiClient {
		val connectionBaseUrls = mutableListOf<String>()
		val connectionRequestHeaders = mutableListOf<Map<String, String>>()
		val statusBaseUrls = mutableListOf<String>()
		val statusRequestHeaders = mutableListOf<Map<String, String>>()

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
	}
}
