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
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainArtist
import paige.navic.util.core.Logger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
	val flowMessage: String? = null
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
	@SerialName("addAlbum") val addAlbum: Boolean = false
)

@Serializable
internal data class AurralWeeklyFlowStatusDto(
	val flows: List<AurralFlowDto> = emptyList(),
	@SerialName("sharedPlaylists") val sharedPlaylists: List<AurralSharedPlaylistDto> = emptyList(),
	val stats: AurralFlowStatsDto = AurralFlowStatsDto(),
	val hint: AurralFlowHintDto? = null
)

@Serializable
internal data class AurralFlowDto(
	val id: String? = null,
	val name: String? = null,
	val enabled: Boolean = false
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
internal data class AurralFlowHintDto(
	val phase: String? = null,
	val message: String? = null
)

@Serializable
internal data class AurralRequestDto(
	val id: String? = null,
	@SerialName("albumId") val albumId: String? = null,
	@SerialName("albumMbid") val albumMbid: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	val status: String? = null
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
internal data class AurralSimilarArtistDto(
	val id: String? = null,
	val name: String? = null,
	val image: String? = null,
	val imageUrl: String? = null,
	val match: Int? = null
)

internal fun aurralServiceStatus(
	health: AurralHealthDto,
	authMe: AurralAuthMeDto?,
	weeklyFlow: AurralWeeklyFlowStatusDto?,
	requests: List<AurralRequestDto>
): AurralServiceStatus {
	val user = authMe?.user ?: health.user
	val stats = weeklyFlow?.stats ?: AurralFlowStatsDto()
	return AurralServiceStatus(
		healthStatus = health.status,
		appVersion = health.appVersion,
		authRequired = health.authRequired,
		username = user?.username,
		role = user?.role,
		accessFlow = user?.permissions?.accessFlow == true,
		addArtist = user?.permissions?.addArtist == true,
		addAlbum = user?.permissions?.addAlbum == true,
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
		flowMessage = weeklyFlow?.hint?.message
	)
}

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
				albumMbid = request.albumMbid,
				albumName = request.albumName,
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
