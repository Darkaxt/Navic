package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.util.core.Logger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "AurralRepository"
internal const val AURRAL_BASE_URL_REQUIRED_MESSAGE = "Enter the Aurral URL first."
internal const val AURRAL_BASE_URL_INVALID_SCHEME_MESSAGE =
	"Aurral URL must start with http:// or https://."
internal const val AURRAL_BASE_URL_INVALID_HOST_MESSAGE =
	"Aurral URL must include a host and cannot include credentials, a query, or a fragment."

class AurralRepository(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient = KtorAurralApiClient()
) {
	suspend fun testConnection(): AurralConnectionResult {
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return AurralConnectionResult.Failed(baseUrlError)
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return AurralConnectionResult.Failed(AURRAL_BASE_URL_REQUIRED_MESSAGE)
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return try {
			apiClient.testConnection(baseUrl, requestHeaders)
		} catch (e: Exception) {
			Logger.w(TAG, "Aurral connection test failed", e)
			AurralConnectionResult.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
		}
	}

	suspend fun getServiceStatus(): Result<AurralServiceStatus> {
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.fetchServiceStatus(baseUrl, requestHeaders)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral service status failed", error)
		}
	}

	suspend fun getArtistEnrichment(artist: DomainArtist): Result<AurralArtistEnrichment?> {
		if (!preferenceManager.aurralEnabled) return Result.success(null)
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.fetchArtistEnrichment(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artist.name
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist enrichment failed for ${artist.name}", error)
		}
	}

	suspend fun requestAlbum(
		artist: DomainArtist,
		releaseGroup: AurralReleaseGroup
	): Result<Unit> {
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val albumMbid = releaseGroup.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album MusicBrainz ID is required."))
		val albumName = releaseGroup.title.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album title is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
		val payload = AurralAlbumRequestPayload(
			albumMbid = albumMbid,
			albumName = albumName,
			artistMbid = artistMbid,
			artistName = artist.name,
			triggerSearch = true
		)

		return runCatching {
			apiClient.requestAlbum(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album request failed for $albumName", error)
		}
	}

	suspend fun monitorArtist(artist: DomainArtist): Result<Unit> {
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
		val payload = AurralArtistMonitorPayload(
			foreignArtistId = artistMbid,
			artistName = artistName,
			monitorOption = "all",
			monitored = true
		)

		return runCatching {
			apiClient.monitorArtist(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				payload = payload
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist monitoring failed for $artistName", error)
		}
	}

	suspend fun createFlow(
		name: String,
		size: Int,
		scheduleDay: Int = currentAurralScheduleDay()
	): Result<AurralFlowActionResult> {
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
		val payload = runCatching {
			aurralDefaultFlowCreatePayload(
				name = name,
				size = size,
				scheduleDay = scheduleDay
			)
		}.getOrElse { error ->
			return Result.failure(IllegalStateException(error.message ?: "Flow details are invalid."))
		}

		return runCatching {
			apiClient.createFlow(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow creation failed for ${payload.name}", error)
		}
	}

	suspend fun setFlowEnabled(
		flowId: String,
		enabled: Boolean
	): Result<AurralFlowActionResult> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.setFlowEnabled(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				enabled = enabled
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow enable update failed for $trimmedFlowId", error)
		}
	}

	suspend fun startFlow(
		flowId: String,
		limit: Int
	): Result<AurralFlowActionResult> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		val safeLimit = limit.takeIf { it > 0 } ?: 30
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.startFlow(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				limit = safeLimit
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow start failed for $trimmedFlowId", error)
		}
	}

	suspend fun getFlowPlayableSongs(
		flowId: String,
		limit: Int = 200
	): Result<List<DomainSong>> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		val safeLimit = limit.takeIf { it > 0 } ?: 200
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			val readyJobs = apiClient.fetchFlowJobs(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				limit = safeLimit
			).filter { it.status.equals("done", ignoreCase = true) }
			if (readyJobs.isEmpty()) return@runCatching emptyList()

			val sessionToken = aurralLoginSessionToken(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders
			)
			val streamToken = if (sessionToken == null && requestHeaders.isNotEmpty()) {
				runCatching {
					apiClient.fetchStreamToken(baseUrl, requestHeaders)?.token?.trim()?.takeIf { it.isNotEmpty() }
				}.getOrNull()
			} else {
				null
			}
			val allowUnauthenticatedStream = sessionToken == null &&
				streamToken == null &&
				requestHeaders.isEmpty()

			readyJobs.mapNotNull { job ->
				job.toDomainSong(
					baseUrl = baseUrl,
					sessionToken = sessionToken,
					streamToken = streamToken,
					allowUnauthenticatedStream = allowUnauthenticatedStream
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow playable songs failed for $trimmedFlowId", error)
		}
	}

	private suspend fun aurralLoginSessionToken(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): String? {
		val username = preferenceManager.aurralUsername.trim().takeIf { it.isNotEmpty() }
			?: return null
		val password = preferenceManager.aurralPassword.trim().takeIf { it.isNotEmpty() }
			?: return null
		return runCatching {
			apiClient.login(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				username = username,
				password = password
			)?.token?.trim()?.takeIf { it.isNotEmpty() }
		}.getOrNull()
	}

	suspend fun getReleaseGroupCoverImageUrl(
		releaseGroup: AurralReleaseGroup,
		artistName: String
	): Result<String?> {
		val releaseGroupMbid = releaseGroup.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val albumTitle = releaseGroup.title.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.fetchReleaseGroupCoverImageUrl(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				releaseGroupMbid = releaseGroupMbid,
				artistName = artistName.trim(),
				albumTitle = albumTitle
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral release group cover failed for $albumTitle", error)
		}
	}
}

interface AurralApiClient {
	suspend fun testConnection(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralConnectionResult

	suspend fun fetchServiceStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus

	suspend fun fetchArtistEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment

	suspend fun requestAlbum(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralAlbumRequestPayload
	)

	suspend fun monitorArtist(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		payload: AurralArtistMonitorPayload
	)

	suspend fun fetchReleaseGroupCoverImageUrl(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		artistName: String,
		albumTitle: String
	): String?

	suspend fun createFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralFlowCreatePayload
	): AurralFlowActionResult = error("Aurral Flow creation is not supported by this client.")

	suspend fun setFlowEnabled(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		enabled: Boolean
	): AurralFlowActionResult = error("Aurral Flow updates are not supported by this client.")

	suspend fun startFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): AurralFlowActionResult = error("Aurral Flow starts are not supported by this client.")

	suspend fun fetchFlowJobs(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): List<AurralFlowJobDto> = error("Aurral Flow jobs are not supported by this client.")

	suspend fun login(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		username: String,
		password: String
	): AurralAuthSessionDto? = null

	suspend fun fetchStreamToken(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralStreamTokenDto? = null
}

private class KtorAurralApiClient : AurralApiClient {
	private val client = HttpClient {
		install(HttpTimeout) {
			requestTimeoutMillis = 30000
			connectTimeoutMillis = 30000
			socketTimeoutMillis = 30000
		}
		install(ContentNegotiation) {
			json(
				Json {
					ignoreUnknownKeys = true
					isLenient = true
				}
			)
		}
	}

	override suspend fun testConnection(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralConnectionResult {
		val response = client.get(aurralEndpoint(baseUrl, "api/health")) {
			aurralJsonRequest(requestHeaders)
		}
		return aurralConnectionResult("Aurral health", response.status)
	}

	override suspend fun fetchServiceStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus {
		val healthResponse = client.get(aurralEndpoint(baseUrl, "api/health")) {
			aurralJsonRequest(requestHeaders)
		}
		if (!healthResponse.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral health", healthResponse.status))
		}
		val health = healthResponse.body<AurralHealthDto>()

		val authMe = fetchAuthMe(baseUrl, requestHeaders)
		val weeklyFlow = fetchWeeklyFlowStatus(baseUrl, requestHeaders)
		val requests = fetchRequests(baseUrl, requestHeaders)

		return aurralServiceStatus(
			health = health,
			authMe = authMe,
			weeklyFlow = weeklyFlow,
			requests = requests
		)
	}

	override suspend fun fetchArtistEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment {
		val details = fetchArtistDetails(
			baseUrl = baseUrl,
			requestHeaders = requestHeaders,
			artistMbid = artistMbid,
			artistName = artistName
		)
		val preview = fetchArtistPreview(
			baseUrl = baseUrl,
			requestHeaders = requestHeaders,
			artistMbid = artistMbid,
			artistName = artistName
		)
		val similar = fetchSimilarArtists(
			baseUrl = baseUrl,
			requestHeaders = requestHeaders,
			artistMbid = artistMbid,
			artistName = artistName
		)
		val requests = fetchRequests(baseUrl, requestHeaders)

		return aurralArtistEnrichment(
			baseUrl = baseUrl,
			details = details,
			preview = preview,
			similar = similar,
			requests = requests
		)
	}

	private suspend fun fetchArtistDetails(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistDetailsDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${encodeUrlComponent(artistMbid)}")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
			parameter("mode", "core")
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral artist details", response.status))
		}
		return response.body()
	}

	private suspend fun fetchArtistPreview(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistPreviewDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${encodeUrlComponent(artistMbid)}/preview")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
		}
		return when {
			response.status.isSuccess() -> response.body()
			response.status == HttpStatusCode.Unauthorized -> AurralArtistPreviewDto()
			response.status == HttpStatusCode.Forbidden -> AurralArtistPreviewDto()
			else -> AurralArtistPreviewDto()
		}
	}

	private suspend fun fetchSimilarArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralSimilarArtistsDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${encodeUrlComponent(artistMbid)}/similar")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
			parameter("limit", 20)
		}
		return when {
			response.status.isSuccess() -> response.body()
			response.status == HttpStatusCode.Unauthorized -> AurralSimilarArtistsDto()
			response.status == HttpStatusCode.Forbidden -> AurralSimilarArtistsDto()
			else -> AurralSimilarArtistsDto()
		}
	}

	private suspend fun fetchAuthMe(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralAuthMeDto? {
		val response = client.get(aurralEndpoint(baseUrl, "api/auth/me")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral auth", response.status))
		}
	}

	private suspend fun fetchWeeklyFlowStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralWeeklyFlowStatusDto? {
		val response = client.get(aurralEndpoint(baseUrl, "api/weekly-flow/status?includeJobs=false")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral Flow status", response.status))
		}
	}

	private suspend fun fetchRequests(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralRequestDto> {
		val response = client.get(aurralEndpoint(baseUrl, "api/requests")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> emptyList()
			response.status == HttpStatusCode.Forbidden -> emptyList()
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral requests", response.status))
		}
	}

	override suspend fun requestAlbum(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralAlbumRequestPayload
	) {
		val response = client.post(aurralEndpoint(baseUrl, "api/library/albums/request")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(payload)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral album request", response.status))
		}
	}

	override suspend fun createFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralFlowCreatePayload
	): AurralFlowActionResult {
		val response = client.post(aurralEndpoint(baseUrl, "api/weekly-flow/flows")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(payload)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral Flow creation", response.status))
		}
		return response.body<AurralFlowActionDto>().toResult()
	}

	override suspend fun setFlowEnabled(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		enabled: Boolean
	): AurralFlowActionResult {
		val response = client.put(
			aurralEndpoint(baseUrl, "api/weekly-flow/flows/${encodeUrlComponent(flowId)}/enabled")
		) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(AurralFlowEnabledPayload(enabled))
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral Flow update", response.status))
		}
		return response.body<AurralFlowActionDto>().toResult()
	}

	override suspend fun startFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): AurralFlowActionResult {
		val response = client.post(
			aurralEndpoint(baseUrl, "api/weekly-flow/start/${encodeUrlComponent(flowId)}")
		) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(AurralFlowStartPayload(limit))
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral Flow start", response.status))
		}
		return response.body<AurralFlowActionDto>().toResult()
	}

	override suspend fun fetchFlowJobs(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): List<AurralFlowJobDto> {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/weekly-flow/jobs/${encodeUrlComponent(flowId)}")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("limit", limit)
		}
		return when {
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral Flow jobs", response.status))
		}
	}

	override suspend fun login(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		username: String,
		password: String
	): AurralAuthSessionDto? {
		val response = client.post(aurralEndpoint(baseUrl, "api/auth/login")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(AurralLoginPayload(username = username, password = password))
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral login", response.status))
		}
	}

	override suspend fun fetchStreamToken(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralStreamTokenDto? {
		val response = client.post(aurralEndpoint(baseUrl, "api/health/stream-token")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral stream token", response.status))
		}
	}

	override suspend fun monitorArtist(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		payload: AurralArtistMonitorPayload
	) {
		val artistEndpoint = aurralEndpoint(baseUrl, "api/library/artists/${encodeUrlComponent(artistMbid)}")
		val existingResponse = client.get(artistEndpoint) {
			aurralJsonRequest(requestHeaders)
		}
		if (existingResponse.status.isSuccess()) {
			val updateResponse = client.put(artistEndpoint) {
				aurralJsonRequest(requestHeaders)
				header("Content-Type", ContentType.Application.Json.toString())
				setBody(payload.toMonitoringUpdatePayload())
			}
			if (updateResponse.status.isSuccess()) return
			error(aurralHttpErrorMessage("Aurral artist monitoring", updateResponse.status))
		}
		if (existingResponse.status != HttpStatusCode.NotFound) {
			error(aurralHttpErrorMessage("Aurral artist lookup", existingResponse.status))
		}

		val addResponse = client.post(aurralEndpoint(baseUrl, "api/library/artists")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(payload)
		}
		if (!addResponse.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral artist add", addResponse.status))
		}
	}

	override suspend fun fetchReleaseGroupCoverImageUrl(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		artistName: String,
		albumTitle: String
	): String? {
		val response = client.get(
			aurralEndpoint(
				baseUrl,
				"api/artists/release-group/${encodeUrlComponent(releaseGroupMbid)}/cover"
			)
		) {
			aurralJsonRequest(requestHeaders)
			artistName.takeIf { it.isNotBlank() }?.let { parameter("artistName", it) }
			albumTitle.takeIf { it.isNotBlank() }?.let { parameter("albumTitle", it) }
		}
		return when {
			response.status.isSuccess() -> response
				.body<AurralReleaseGroupCoverDto>()
				.images
				.firstNotNullOfOrNull { image -> aurralAbsoluteImageUrl(baseUrl, image.image) }
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			else -> null
		}
	}
}

private fun HttpRequestBuilder.aurralJsonRequest(
	requestHeaders: Map<String, String> = emptyMap()
) {
	accept(ContentType.Application.Json)
	requestHeaders.forEach { (key, value) ->
		header(key, value)
	}
}

sealed interface AurralConnectionResult {
	data object Connected : AurralConnectionResult
	data object Unauthorized : AurralConnectionResult
	data object Forbidden : AurralConnectionResult
	data class Failed(val message: String) : AurralConnectionResult
}

data class AurralServiceStatus(
	val healthStatus: String = "unknown",
	val appVersion: String? = null,
	val authRequired: Boolean = false,
	val username: String? = null,
	val role: String? = null,
	val accessFlow: Boolean = false,
	val addArtist: Boolean = false,
	val addAlbum: Boolean = false,
	val changeMonitoring: Boolean = false,
	val lidarrConfigured: Boolean = false,
	val discoveryRecommendationsCount: Int = 0,
	val discoveryUpdating: Boolean = false,
	val flowsCount: Int = 0,
	val enabledFlowsCount: Int = 0,
	val sharedPlaylistsCount: Int = 0,
	val requestsCount: Int = 0,
	val flowTracksTotal: Int = 0,
	val flowTracksPending: Int = 0,
	val flowTracksDownloading: Int = 0,
	val flowTracksDone: Int = 0,
	val flowTracksFailed: Int = 0,
	val flowPhase: String? = null,
	val flowMessage: String? = null,
	val flows: List<AurralFlowSummary> = emptyList(),
	val flowCapabilities: AurralFlowCapabilities = AurralFlowCapabilities(),
	val acquisitionQueue: List<AurralAcquisitionQueueItem> = emptyList()
)

data class AurralFlowSummary(
	val id: String,
	val name: String,
	val enabled: Boolean,
	val size: Int = 30,
	val nextRunAt: Long? = null,
	val mix: AurralFlowMix = AurralFlowMix(),
	val tags: List<String> = emptyList(),
	val relatedArtists: List<String> = emptyList(),
	val scheduleDays: List<Int> = emptyList(),
	val scheduleTime: String = "00:00",
	val stats: AurralFlowStats = AurralFlowStats()
)

@Serializable
data class AurralFlowMix(
	val discover: Int = 34,
	val mix: Int = 33,
	val trending: Int = 33,
	val focus: Int = 0
)

data class AurralFlowStats(
	val total: Int = 0,
	val pending: Int = 0,
	val downloading: Int = 0,
	val done: Int = 0,
	val failed: Int = 0
)

data class AurralFlowCapabilities(
	val lastfmRequired: Boolean = false,
	val availableSources: List<String> = emptyList(),
	val unavailableSources: Map<String, String> = emptyMap()
)

data class AurralFlowActionResult(
	val success: Boolean = false,
	val flowId: String? = null,
	val enabled: Boolean? = null,
	val tracksQueued: Int = 0,
	val reserveTracks: Int = 0,
	val jobIds: List<String> = emptyList(),
	val message: String? = null,
	val flow: AurralFlowSummary? = null
)

data class AurralAcquisitionQueueItem(
	val id: String,
	val type: String,
	val albumId: String?,
	val albumMbid: String?,
	val albumName: String,
	val artistId: String?,
	val artistMbid: String?,
	val artistName: String,
	val status: String,
	val requestedAt: String?,
	val inQueue: Boolean
)

@Serializable
data class AurralAlbumRequestPayload(
	val albumMbid: String,
	val albumName: String,
	val artistMbid: String,
	val artistName: String,
	val triggerSearch: Boolean = true
)

@Serializable
data class AurralArtistMonitorPayload(
	@SerialName("foreignArtistId") val foreignArtistId: String,
	val artistName: String,
	val monitorOption: String = "all",
	val monitored: Boolean = true
)

@Serializable
data class AurralFlowCreatePayload(
	val name: String,
	val size: Int,
	val mix: AurralFlowMix = AurralFlowMix(),
	val deepDive: Boolean = false,
	val tags: List<String> = emptyList(),
	@SerialName("relatedArtists") val relatedArtists: List<String> = emptyList(),
	@SerialName("scheduleDays") val scheduleDays: List<Int>,
	@SerialName("scheduleTime") val scheduleTime: String = "00:00"
)

@Serializable
private data class AurralFlowEnabledPayload(
	val enabled: Boolean
)

@Serializable
private data class AurralFlowStartPayload(
	val limit: Int
)

@Serializable
internal data class AurralLoginPayload(
	val username: String,
	val password: String
)

@Serializable
data class AurralAuthSessionDto(
	val token: String? = null,
	@SerialName("expiresAt") val expiresAt: String? = null
)

@Serializable
data class AurralStreamTokenDto(
	val token: String? = null,
	@SerialName("expiresIn") val expiresIn: Int? = null
)

@Serializable
data class AurralFlowJobDto(
	val id: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("trackName") val trackName: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	val status: String? = null,
	@SerialName("playlistType") val playlistType: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("albumMbid") val albumMbid: String? = null,
	@SerialName("trackMbid") val trackMbid: String? = null,
	@SerialName("releaseYear") val releaseYear: String? = null,
	@SerialName("durationMs") val durationMs: Long? = null,
	@SerialName("finalPath") val finalPath: String? = null
)

@Serializable
private data class AurralArtistMonitoringUpdatePayload(
	val monitored: Boolean = true,
	val monitorOption: String = "all"
)

@Serializable
internal data class AurralHealthDto(
	val status: String = "unknown",
	@SerialName("appVersion") val appVersion: String? = null,
	@SerialName("authRequired") val authRequired: Boolean = false,
	@SerialName("lidarrConfigured") val lidarrConfigured: Boolean = false,
	@SerialName("rootFolderConfigured") val rootFolderConfigured: Boolean = false,
	val discovery: AurralDiscoveryDto? = null,
	val user: AurralUserDto? = null
)

@Serializable
internal data class AurralDiscoveryDto(
	@SerialName("recommendationsCount") val recommendationsCount: Int = 0,
	@SerialName("isUpdating") val isUpdating: Boolean = false
)

@Serializable
internal data class AurralAuthMeDto(
	val user: AurralUserDto? = null,
	@SerialName("expiresAt") val expiresAt: String? = null
)

@Serializable
internal data class AurralUserDto(
	val id: Int? = null,
	val username: String? = null,
	val role: String? = null,
	val permissions: AurralPermissionsDto = AurralPermissionsDto()
)

@Serializable
internal data class AurralPermissionsDto(
	@SerialName("accessFlow") val accessFlow: Boolean = false,
	@SerialName("addArtist") val addArtist: Boolean = false,
	@SerialName("addAlbum") val addAlbum: Boolean = false,
	@SerialName("changeMonitoring") val changeMonitoring: Boolean = false
)

@Serializable
internal data class AurralWeeklyFlowStatusDto(
	val flows: List<AurralFlowDto> = emptyList(),
	@SerialName("sharedPlaylists") val sharedPlaylists: List<AurralSharedPlaylistDto> = emptyList(),
	@SerialName("flowStats") val flowStats: Map<String, AurralFlowStatsDto> = emptyMap(),
	val stats: AurralFlowStatsDto = AurralFlowStatsDto(),
	val capabilities: AurralFlowCapabilitiesDto = AurralFlowCapabilitiesDto(),
	val hint: AurralFlowHintDto? = null
)

@Serializable
internal data class AurralFlowDto(
	val id: String? = null,
	val name: String? = null,
	val enabled: Boolean = false,
	val size: Int? = null,
	@SerialName("nextRunAt") val nextRunAt: Long? = null,
	val mix: AurralFlowMixDto = AurralFlowMixDto(),
	val tags: List<String> = emptyList(),
	@SerialName("relatedArtists") val relatedArtists: List<String> = emptyList(),
	@SerialName("scheduleDays") val scheduleDays: List<Int> = emptyList(),
	@SerialName("scheduleTime") val scheduleTime: String? = null
)

@Serializable
internal data class AurralSharedPlaylistDto(
	val id: String? = null,
	val name: String? = null
)

@Serializable
internal data class AurralFlowStatsDto(
	val total: Int = 0,
	val pending: Int = 0,
	val downloading: Int = 0,
	val done: Int = 0,
	val failed: Int = 0
)

@Serializable
internal data class AurralFlowMixDto(
	val discover: Int = 34,
	val mix: Int = 33,
	val trending: Int = 33,
	val focus: Int = 0
)

@Serializable
internal data class AurralFlowCapabilitiesDto(
	@SerialName("lastfmRequired") val lastfmRequired: Boolean = false,
	@SerialName("availableSources") val availableSources: List<String> = emptyList(),
	@SerialName("unavailableSources") val unavailableSources: Map<String, String> = emptyMap()
)

@Serializable
internal data class AurralFlowHintDto(
	val phase: String? = null,
	val message: String? = null
)

@Serializable
internal data class AurralRequestDto(
	val id: String? = null,
	val type: String? = null,
	val mbid: String? = null,
	val name: String? = null,
	@SerialName("albumId") val albumId: String? = null,
	@SerialName("albumMbid") val albumMbid: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	@SerialName("artistId") val artistId: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	val status: String? = null,
	@SerialName("requestedAt") val requestedAt: String? = null,
	@SerialName("inQueue") val inQueue: Boolean = false
)

@Serializable
internal data class AurralArtistDetailsDto(
	val id: String? = null,
	val name: String? = null,
	@SerialName("release-groups") val releaseGroups: List<AurralReleaseGroupDto> = emptyList()
)

@Serializable
internal data class AurralReleaseGroupDto(
	val id: String? = null,
	val title: String? = null,
	@SerialName("first-release-date") val firstReleaseDate: String? = null,
	@SerialName("primary-type") val primaryType: String? = null,
	@SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
	@SerialName("_coverUrl") val coverUrl: String? = null
)

@Serializable
internal data class AurralArtistPreviewDto(
	val tracks: List<AurralPreviewTrackDto> = emptyList()
)

@Serializable
internal data class AurralPreviewTrackDto(
	val id: String? = null,
	val title: String? = null,
	val album: String? = null,
	@SerialName("preview_url") val previewUrl: String? = null,
	@SerialName("duration_ms") val durationMs: Long? = null
)

@Serializable
internal data class AurralSimilarArtistsDto(
	val artists: List<AurralSimilarArtistDto> = emptyList()
)

@Serializable
internal data class AurralReleaseGroupCoverDto(
	val images: List<AurralReleaseGroupCoverImageDto> = emptyList()
)

@Serializable
internal data class AurralReleaseGroupCoverImageDto(
	val image: String? = null
)

@Serializable
internal data class AurralSimilarArtistDto(
	val id: String? = null,
	val name: String? = null,
	val image: String? = null,
	val imageUrl: String? = null,
	val match: Int? = null
)

@Serializable
internal data class AurralFlowActionDto(
	val success: Boolean = false,
	@SerialName("flowId") val flowId: String? = null,
	val enabled: Boolean? = null,
	@SerialName("tracksQueued") val tracksQueued: Int = 0,
	@SerialName("reserveTracks") val reserveTracks: Int = 0,
	@SerialName("jobIds") val jobIds: List<String> = emptyList(),
	val message: String? = null,
	val flow: AurralFlowDto? = null
)

internal fun aurralServiceStatus(
	health: AurralHealthDto,
	authMe: AurralAuthMeDto?,
	weeklyFlow: AurralWeeklyFlowStatusDto?,
	requests: List<AurralRequestDto>
): AurralServiceStatus {
	val user = authMe?.user ?: health.user
	val stats = weeklyFlow?.stats ?: AurralFlowStatsDto()
	val flows = weeklyFlow?.flows.orEmpty().mapNotNull { flow ->
		val id = flow.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		flow.toSummary(weeklyFlow?.flowStats?.get(id))
	}
	return AurralServiceStatus(
		healthStatus = health.status,
		appVersion = health.appVersion,
		authRequired = health.authRequired,
		username = user?.username,
		role = user?.role,
		accessFlow = user?.permissions?.accessFlow == true,
		addArtist = user?.permissions?.addArtist == true,
		addAlbum = user?.permissions?.addAlbum == true,
		changeMonitoring = user?.permissions?.changeMonitoring == true,
		lidarrConfigured = health.lidarrConfigured || health.rootFolderConfigured,
		discoveryRecommendationsCount = health.discovery?.recommendationsCount ?: 0,
		discoveryUpdating = health.discovery?.isUpdating == true,
		flowsCount = weeklyFlow?.flows.orEmpty().size,
		enabledFlowsCount = weeklyFlow?.flows.orEmpty().count { it.enabled },
		sharedPlaylistsCount = weeklyFlow?.sharedPlaylists.orEmpty().size,
		requestsCount = requests.size,
		flowTracksTotal = stats.total,
		flowTracksPending = stats.pending,
		flowTracksDownloading = stats.downloading,
		flowTracksDone = stats.done,
		flowTracksFailed = stats.failed,
		flowPhase = weeklyFlow?.hint?.phase,
		flowMessage = weeklyFlow?.hint?.message,
		flows = flows,
		flowCapabilities = weeklyFlow?.capabilities?.toCapabilities() ?: AurralFlowCapabilities(),
		acquisitionQueue = requests.mapNotNull(::aurralAcquisitionQueueItem)
	)
}

internal fun aurralDefaultFlowCreatePayload(
	name: String,
	size: Int,
	scheduleDay: Int
): AurralFlowCreatePayload {
	val trimmedName = name.trim().takeIf { it.isNotEmpty() }
		?: error("Flow name is required.")
	if (size <= 0) error("Flow size must be positive.")
	if (scheduleDay !in 0..6) error("Flow schedule day must be between 0 and 6.")
	return AurralFlowCreatePayload(
		name = trimmedName,
		size = size,
		mix = AurralFlowMix(discover = 34, mix = 33, trending = 33, focus = 0),
		scheduleDays = listOf(scheduleDay),
		scheduleTime = "00:00"
	)
}

internal fun currentAurralScheduleDay(): Int {
	val isoDay = Clock.System.now()
		.toLocalDateTime(TimeZone.currentSystemDefault())
		.dayOfWeek
		.ordinal + 1
	return if (isoDay == 7) 0 else isoDay
}

private fun AurralFlowDto.toSummary(stats: AurralFlowStatsDto? = null): AurralFlowSummary? {
	val id = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Flow"
	val safeSize = size?.takeIf { it > 0 } ?: 30
	return AurralFlowSummary(
		id = id,
		name = name,
		enabled = enabled,
		size = safeSize,
		nextRunAt = nextRunAt,
		mix = mix.toMix(),
		tags = tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		relatedArtists = relatedArtists.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		scheduleDays = scheduleDays.filter { it in 0..6 },
		scheduleTime = scheduleTime?.trim()?.takeIf { it.isNotEmpty() } ?: "00:00",
		stats = stats.toStats()
	)
}

private fun AurralFlowMixDto.toMix(): AurralFlowMix =
	AurralFlowMix(
		discover = discover,
		mix = mix,
		trending = trending,
		focus = focus
	)

private fun AurralFlowStatsDto?.toStats(): AurralFlowStats =
	AurralFlowStats(
		total = this?.total ?: 0,
		pending = this?.pending ?: 0,
		downloading = this?.downloading ?: 0,
		done = this?.done ?: 0,
		failed = this?.failed ?: 0
	)

private fun AurralFlowCapabilitiesDto.toCapabilities(): AurralFlowCapabilities =
	AurralFlowCapabilities(
		lastfmRequired = lastfmRequired,
		availableSources = availableSources.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		unavailableSources = unavailableSources.mapNotNull { (key, value) ->
			val normalizedKey = key.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			val normalizedValue = value.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			normalizedKey to normalizedValue
		}.toMap()
	)

private fun AurralFlowActionDto.toResult(): AurralFlowActionResult =
	AurralFlowActionResult(
		success = success,
		flowId = flow?.id?.trim()?.takeIf { it.isNotEmpty() }
			?: flowId?.trim()?.takeIf { it.isNotEmpty() },
		enabled = enabled ?: flow?.enabled,
		tracksQueued = tracksQueued,
		reserveTracks = reserveTracks,
		jobIds = jobIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		message = message?.trim()?.takeIf { it.isNotEmpty() },
		flow = flow?.toSummary()
	)

private fun AurralFlowJobDto.toDomainSong(
	baseUrl: String,
	sessionToken: String?,
	streamToken: String?,
	allowUnauthenticatedStream: Boolean
): DomainSong? {
	val jobId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val title = trackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val streamUrl = when {
		!sessionToken.isNullOrBlank() -> aurralFlowStreamUrl(baseUrl, jobId, sessionToken)
		!streamToken.isNullOrBlank() -> aurralFlowStreamTokenUrl(baseUrl, jobId, streamToken)
		allowUnauthenticatedStream -> aurralFlowRawStreamUrl(baseUrl, jobId)
		else -> null
	} ?: return null
	val fileExtension = finalPath
		?.substringBefore('?')
		?.substringAfterLast('/')
		?.substringAfterLast('.', "")
		?.lowercase()
		?.takeIf { it.isNotEmpty() }
		?: "mp3"

	return DomainSong(
		id = "$AurralFlowSongIdPrefix$jobId",
		title = title,
		artistName = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Aurral",
		artistId = artistMbid?.trim()?.takeIf { it.isNotEmpty() } ?: "",
		albumTitle = albumName?.trim()?.takeIf { it.isNotEmpty() },
		albumId = albumMbid?.trim()?.takeIf { it.isNotEmpty() },
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = releaseYear?.trim()?.take(4)?.toIntOrNull(),
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = durationMs?.takeIf { it > 0 }?.milliseconds ?: 0.milliseconds,
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
		fileExtension = fileExtension,
		mimeType = fileExtension.toAurralAudioMimeType(),
		filePath = streamUrl,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = trackMbid?.trim()?.takeIf { it.isNotEmpty() },
		explicitStatus = DomainExplicitStatus.Unknown
	)
}

private fun String.toAurralAudioMimeType(): String =
	when (lowercase()) {
		"flac" -> "audio/flac"
		"m4a", "mp4" -> "audio/mp4"
		"ogg", "oga" -> "audio/ogg"
		"wav" -> "audio/wav"
		"aac" -> "audio/aac"
		else -> "audio/mpeg"
	}

internal fun aurralAcquisitionQueueItem(request: AurralRequestDto): AurralAcquisitionQueueItem? {
	val id = request.id?.trim()?.takeIf { it.isNotEmpty() }
		?: request.albumId?.trim()?.takeIf { it.isNotEmpty() }?.let { "album-$it" }
		?: request.resolvedAlbumMbid()?.let { "album-$it" }
		?: return null
	val albumName = request.resolvedAlbumName() ?: return null
	val artistName = request.artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Artist"
	val status = request.status?.trim()?.takeIf { it.isNotEmpty() } ?: "requested"
	return AurralAcquisitionQueueItem(
		id = id,
		type = request.type?.trim()?.takeIf { it.isNotEmpty() } ?: "album",
		albumId = request.albumId?.trim()?.takeIf { it.isNotEmpty() },
		albumMbid = request.resolvedAlbumMbid(),
		albumName = albumName,
		artistId = request.artistId?.trim()?.takeIf { it.isNotEmpty() },
		artistMbid = request.artistMbid?.trim()?.takeIf { it.isNotEmpty() },
		artistName = artistName,
		status = status,
		requestedAt = request.requestedAt?.trim()?.takeIf { it.isNotEmpty() },
		inQueue = request.inQueue
	)
}

private fun AurralRequestDto.resolvedAlbumMbid(): String? =
	albumMbid?.trim()?.takeIf { it.isNotEmpty() }
		?: mbid?.trim()?.takeIf { it.isNotEmpty() }

private fun AurralRequestDto.resolvedAlbumName(): String? =
	albumName?.trim()?.takeIf { it.isNotEmpty() }
		?: name?.trim()?.takeIf { it.isNotEmpty() }

private fun AurralArtistMonitorPayload.toMonitoringUpdatePayload(): AurralArtistMonitoringUpdatePayload =
	AurralArtistMonitoringUpdatePayload(
		monitored = monitored,
		monitorOption = monitorOption
	)

internal fun aurralArtistEnrichment(
	baseUrl: String,
	details: AurralArtistDetailsDto,
	preview: AurralArtistPreviewDto,
	similar: AurralSimilarArtistsDto,
	requests: List<AurralRequestDto>
): AurralArtistEnrichment {
	val artistMbid = details.id.orEmpty()
	val artistName = details.name.orEmpty()
	return AurralArtistEnrichment(
		artistMbid = artistMbid,
		artistName = artistName,
		releaseGroups = details.releaseGroups.mapNotNull { releaseGroup ->
			val id = releaseGroup.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val title = releaseGroup.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralReleaseGroup(
				id = id,
				title = title,
				firstReleaseDate = releaseGroup.firstReleaseDate,
				primaryType = releaseGroup.primaryType,
				secondaryTypes = releaseGroup.secondaryTypes,
				coverUrl = releaseGroup.coverUrl
			)
		},
		previewTracks = preview.tracks.mapNotNull { track ->
			val id = track.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val title = track.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralPreviewTrack(
				id = id,
				title = title,
				album = track.album,
				previewUrl = track.previewUrl,
				durationMs = track.durationMs
			)
		},
		similarArtists = similar.artists.mapNotNull { artist ->
			val id = artist.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val name = artist.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralSimilarArtist(
				id = id,
				name = name,
				imageUrl = artist.imageUrl ?: artist.image,
				matchPercent = artist.match
			)
		},
		requests = requests.map { request ->
			AurralAlbumRequest(
				albumMbid = request.resolvedAlbumMbid(),
				albumName = request.resolvedAlbumName(),
				artistMbid = request.artistMbid,
				artistName = request.artistName,
				status = request.status
			)
		}
	)
}

internal fun aurralEndpoint(baseUrl: String, path: String): String =
	"${normalizeAurralBaseUrl(baseUrl)}/${path.trim().trimStart('/')}"

internal fun configuredAurralBaseUrl(baseUrl: String): String? =
	normalizedAurralBaseUrl(baseUrl)?.value

internal data class NormalizedAurralBaseUrl(val value: String)

internal fun normalizedAurralBaseUrl(baseUrl: String): NormalizedAurralBaseUrl? {
	val trimmed = baseUrl.trim().trimEnd('/')
	val schemeSeparator = trimmed.indexOf("://")
	if (schemeSeparator <= 0) return null

	val scheme = trimmed.substring(0, schemeSeparator)
	if (!scheme.equals("http", ignoreCase = true) &&
		!scheme.equals("https", ignoreCase = true)
	) {
		return null
	}

	val afterScheme = trimmed.drop(schemeSeparator + 3)
	if (afterScheme.isBlank()) return null
	if (afterScheme.any { it == '?' || it == '#' }) return null

	val authority = afterScheme.takeWhile { it != '/' }
	if ('@' in authority) return null

	val host = parsedAurralUrlHostOrNull(authority) ?: return null

	return if (host.trim().trimEnd('.').isEmpty()) {
		null
	} else {
		NormalizedAurralBaseUrl(trimmed)
	}
}

internal fun aurralBaseUrlConfigurationError(baseUrl: String): String? {
	val trimmed = baseUrl.trim().trimEnd('/')
	return when {
		trimmed.isEmpty() -> AURRAL_BASE_URL_REQUIRED_MESSAGE
		!trimmed.hasSupportedHttpScheme() -> AURRAL_BASE_URL_INVALID_SCHEME_MESSAGE
		normalizedAurralBaseUrl(trimmed) == null -> AURRAL_BASE_URL_INVALID_HOST_MESSAGE
		else -> null
	}
}

@OptIn(ExperimentalEncodingApi::class)
internal fun aurralBasicAuthHeaders(username: String, password: String): Map<String, String> {
	val trimmedUsername = username.trim()
	val trimmedPassword = password.trim()
	if (trimmedUsername.isEmpty() || trimmedPassword.isEmpty()) return emptyMap()
	val credentials = "$trimmedUsername:$trimmedPassword"
	return mapOf("Authorization" to "Basic ${Base64.encode(credentials.encodeToByteArray())}")
}

internal fun aurralBearerAuthHeaders(token: String?): Map<String, String> {
	val trimmedToken = token?.trim().orEmpty()
	return if (trimmedToken.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $trimmedToken")
}

internal fun aurralFlowStreamUrl(
	baseUrl: String,
	jobId: String,
	sessionToken: String?
): String? {
	val trimmedJobId = jobId.trim()
	val trimmedToken = sessionToken?.trim().orEmpty()
	if (trimmedJobId.isEmpty() || trimmedToken.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/stream/${encodeUrlComponent(trimmedJobId)}"
	) + "?token=${encodeUrlComponent(trimmedToken)}"
}

internal fun aurralFlowStreamTokenUrl(
	baseUrl: String,
	jobId: String,
	streamToken: String?
): String? {
	val trimmedJobId = jobId.trim()
	val trimmedToken = streamToken?.trim().orEmpty()
	if (trimmedJobId.isEmpty() || trimmedToken.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/stream/${encodeUrlComponent(trimmedJobId)}"
	) + "?st=${encodeUrlComponent(trimmedToken)}"
}

private fun aurralFlowRawStreamUrl(
	baseUrl: String,
	jobId: String
): String? {
	val trimmedJobId = jobId.trim()
	if (trimmedJobId.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/stream/${encodeUrlComponent(trimmedJobId)}"
	)
}

internal fun aurralFlowArtworkUrl(
	baseUrl: String,
	playlistId: String,
	sessionToken: String?
): String? {
	val trimmedPlaylistId = playlistId.trim()
	val trimmedToken = sessionToken?.trim().orEmpty()
	if (trimmedPlaylistId.isEmpty() || trimmedToken.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/artwork/${encodeUrlComponent(trimmedPlaylistId)}"
	) + "?token=${encodeUrlComponent(trimmedToken)}"
}

internal fun aurralReleaseGroupCoverUrl(
	baseUrl: String,
	releaseGroupMbid: String,
	artistName: String,
	albumTitle: String
): String? {
	val trimmedReleaseGroupMbid = releaseGroupMbid.trim()
	if (trimmedReleaseGroupMbid.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	val query = listOfNotNull(
		artistName.trim().takeIf { it.isNotEmpty() }?.let {
			"artistName=${encodeUrlComponent(it)}"
		},
		albumTitle.trim().takeIf { it.isNotEmpty() }?.let {
			"albumTitle=${encodeUrlComponent(it)}"
		}
	).joinToString("&")
	val endpoint = aurralEndpoint(
		configuredBaseUrl,
		"api/artists/release-group/${encodeUrlComponent(trimmedReleaseGroupMbid)}/cover"
	)
	return if (query.isEmpty()) endpoint else "$endpoint?$query"
}

internal fun aurralAbsoluteImageUrl(
	baseUrl: String,
	imageUrl: String?
): String? {
	val trimmedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (
		trimmedImageUrl.startsWith("http://", ignoreCase = true) ||
		trimmedImageUrl.startsWith("https://", ignoreCase = true)
	) {
		return trimmedImageUrl
	}
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl)?.trimEnd('/') ?: return null
	return when {
		trimmedImageUrl.startsWith("/") -> configuredBaseUrl + trimmedImageUrl
		else -> "$configuredBaseUrl/${trimmedImageUrl.trimStart('/')}"
	}
}

internal fun aurralRequestHeadersForUrl(
	baseUrl: String,
	imageUrl: String?,
	requestHeaders: Map<String, String>
): Map<String, String> {
	if (requestHeaders.isEmpty()) return emptyMap()
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl)?.trimEnd('/') ?: return emptyMap()
	val trimmedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyMap()
	return if (trimmedImageUrl.startsWith("$configuredBaseUrl/", ignoreCase = true)) {
		requestHeaders
	} else {
		emptyMap()
	}
}

internal fun aurralConnectionResult(
	operation: String,
	status: HttpStatusCode
): AurralConnectionResult =
	when {
		status == HttpStatusCode.Unauthorized -> AurralConnectionResult.Unauthorized
		status == HttpStatusCode.Forbidden -> AurralConnectionResult.Forbidden
		status.isSuccess() -> AurralConnectionResult.Connected
		else -> AurralConnectionResult.Failed(aurralHttpErrorMessage(operation, status))
	}

internal fun aurralHttpErrorMessage(
	operation: String,
	status: HttpStatusCode
): String =
	when (status) {
		HttpStatusCode.Unauthorized -> "$operation unauthorized. Check the Aurral username and password."
		HttpStatusCode.Forbidden -> "$operation forbidden. Check the Aurral user permissions."
		else -> "$operation returned HTTP ${status.value}"
	}

private fun normalizeAurralBaseUrl(baseUrl: String): String =
	configuredAurralBaseUrl(baseUrl)
		?: error(aurralBaseUrlConfigurationError(baseUrl)
			?: AURRAL_BASE_URL_REQUIRED_MESSAGE)

private fun parsedAurralUrlHostOrNull(authority: String): String? {
	if (authority.isBlank()) return null

	val host: String
	val portText: String?
	if (authority.startsWith("[")) {
		val closingBracket = authority.indexOf(']')
		if (closingBracket == -1) return null

		host = authority.substring(1, closingBracket)
		val suffix = authority.drop(closingBracket + 1)
		if (suffix.isNotEmpty() && !suffix.startsWith(":")) return null
		portText = suffix.takeIf { it.isNotEmpty() }?.drop(1)
	} else {
		val firstColon = authority.indexOf(':')
		val lastColon = authority.lastIndexOf(':')
		if (firstColon != -1 && firstColon != lastColon) return null

		host = if (lastColon == -1) authority else authority.substring(0, lastColon)
		portText = if (lastColon == -1) null else authority.substring(lastColon + 1)
	}

	val port = portText?.toIntOrNull()
		?.takeIf { it in 1..65535 }
	if (portText != null && port == null) return null

	return host
}

private fun String.hasSupportedHttpScheme(): Boolean =
	startsWith("http://", ignoreCase = true) ||
		startsWith("https://", ignoreCase = true)

private fun encodeUrlComponent(value: String): String {
	val hex = "0123456789ABCDEF"
	return buildString {
		value.encodeToByteArray().forEach { byte ->
			val code = byte.toInt() and 0xff
			val char = code.toChar()
			if (
				char in 'A'..'Z' ||
				char in 'a'..'z' ||
				char in '0'..'9' ||
				char == '-' ||
				char == '.' ||
				char == '_' ||
				char == '~'
			) {
				append(char)
			} else {
				append('%')
				append(hex[code shr 4])
				append(hex[code and 0x0f])
			}
		}
	}
}
