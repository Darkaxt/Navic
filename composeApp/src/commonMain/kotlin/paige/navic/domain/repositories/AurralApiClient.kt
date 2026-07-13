package paige.navic.domain.repositories

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.OptionalIntegrationHttpFailure
import paige.navic.data.remote.NetworkClientFactory
import paige.navic.util.core.Logger

private const val TAG = "AurralApiClient"

interface AurralApiClient {
	suspend fun testConnection(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralConnectionResult

	suspend fun fetchServiceStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus

	suspend fun fetchActivityStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus =
		fetchServiceStatus(baseUrl, requestHeaders)

	suspend fun fetchDiscovery(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralDiscoverySummary = error("Aurral discovery is not supported by this client.")

	suspend fun fetchDiscoveryBase(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralDiscoverySummary = fetchDiscovery(baseUrl, requestHeaders)

	suspend fun fetchRecentlyAddedArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> = fetchDiscovery(baseUrl, requestHeaders).recentlyAdded

	suspend fun fetchRecentReleases(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralAlbumSearchItem> = fetchDiscovery(baseUrl, requestHeaders).recentReleases

	suspend fun fetchLibraryArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> = emptyList()

	suspend fun searchArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralArtistSearchRequest
	): AurralArtistSearchResult = error("Aurral artist search is not supported by this client.")

	suspend fun searchAlbums(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralAlbumSearchRequest
	): AurralAlbumSearchResult = error("Aurral album search is not supported by this client.")

	suspend fun fetchAlbumTracks(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		libraryAlbumId: String?
	): List<AurralAlbumTrackItem> = error("Aurral album tracks are not supported by this client.")

	suspend fun fetchArtistEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment

	suspend fun fetchArtistCoreEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment = fetchArtistEnrichment(
		baseUrl = baseUrl,
		requestHeaders = requestHeaders,
		artistMbid = artistMbid,
		artistName = artistName
	)

	suspend fun fetchArtistPreviewTracks(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): List<AurralPreviewTrack> = fetchArtistEnrichment(
		baseUrl = baseUrl,
		requestHeaders = requestHeaders,
		artistMbid = artistMbid,
		artistName = artistName
	).previewTracks

	suspend fun fetchArtistSimilarArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): List<AurralSimilarArtist> = fetchArtistEnrichment(
		baseUrl = baseUrl,
		requestHeaders = requestHeaders,
		artistMbid = artistMbid,
		artistName = artistName
	).similarArtists

	suspend fun fetchAlbumRequests(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralAlbumRequest> = emptyList()

	suspend fun fetchLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String
	): Boolean?

	suspend fun requestAlbum(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralAlbumRequestPayload
	)

	suspend fun cancelAcquisitionRequest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		target: AurralAcquisitionDeleteTarget
	): Unit = error("Aurral request cancellation is not supported by this client.")

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

internal class KtorAurralApiClient(
	networkClientFactory: NetworkClientFactory = NetworkClientFactory()
) : AurralApiClient {
	private val client = networkClientFactory.create(json = AURRAL_JSON)

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

	override suspend fun fetchActivityStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus {
		val authMe = fetchAuthMe(baseUrl, requestHeaders)
		val weeklyFlow = fetchWeeklyFlowStatus(baseUrl, requestHeaders)
		val requests = fetchRequests(baseUrl, requestHeaders)

		return aurralServiceStatus(
			health = AurralHealthDto(),
			authMe = authMe,
			weeklyFlow = weeklyFlow,
			requests = requests
		)
	}

	override suspend fun fetchDiscovery(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralDiscoverySummary {
		val discovery = fetchDiscoveryBase(baseUrl, requestHeaders)
		return coroutineScope {
			val recentlyAdded = async { fetchRecentlyAddedArtists(baseUrl, requestHeaders) }
			val recentReleases = async { fetchRecentReleases(baseUrl, requestHeaders) }
			discovery.copy(
				recentlyAdded = recentlyAdded.await(),
				recentReleases = recentReleases.await()
			)
		}
	}

	override suspend fun fetchDiscoveryBase(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralDiscoverySummary {
		val response = client.get(aurralEndpoint(baseUrl, "api/discover")) {
			aurralJsonRequest(requestHeaders)
		}
		if (!response.status.isSuccess()) throw AurralApiException(
			response.status,
			aurralHttpErrorMessage("Aurral Discover", response.status)
		)
		val discovery = response.body<AurralDiscoveryResponseDto>()
		return aurralDiscoverySummary(
			baseUrl = baseUrl,
			response = discovery,
			recentlyAdded = emptyList(),
			recentReleases = emptyList()
		)
	}

	override suspend fun fetchRecentlyAddedArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> {
		val response = client.get(aurralEndpoint(baseUrl, "api/library/recent")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> aurralRecentlyAddedArtists(
				baseUrl = baseUrl,
				response = response.body()
			)
			else -> throw AurralApiException(
				response.status,
				aurralHttpErrorMessage("Aurral recently added", response.status)
			)
		}
	}

	override suspend fun fetchLibraryArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> {
		val response = client.get(aurralEndpoint(baseUrl, "api/library/artists")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> aurralLibraryArtists(
				baseUrl = baseUrl,
				response = response.body()
			)
			else -> throw AurralApiException(
				response.status,
				aurralHttpErrorMessage("Aurral library artists", response.status)
			)
		}
	}

	override suspend fun fetchRecentReleases(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralAlbumSearchItem> {
		val response = client.get(aurralEndpoint(baseUrl, "api/library/recent-releases")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> aurralRecentReleases(
				baseUrl = baseUrl,
				response = response.body()
			)
			else -> throw AurralApiException(
				response.status,
				aurralHttpErrorMessage("Aurral recent releases", response.status)
			)
		}
	}

	override suspend fun searchArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralArtistSearchRequest
	): AurralArtistSearchResult {
		val response = client.get(aurralEndpoint(baseUrl, "api/search/artists")) {
			aurralJsonRequest(requestHeaders)
			parameter("query", request.query)
			parameter("limit", request.limit)
			parameter("offset", request.offset)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral artist search", response.status))
		}
		return aurralArtistSearchResult(
			baseUrl = baseUrl,
			query = request.query,
			response = response.body()
		)
	}

	override suspend fun searchAlbums(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralAlbumSearchRequest
	): AurralAlbumSearchResult {
		val response = client.get(aurralEndpoint(baseUrl, "api/search")) {
			aurralJsonRequest(requestHeaders)
			parameter("q", request.query)
			parameter("scope", "album")
			parameter("limit", request.limit)
			parameter("offset", request.offset)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral album search", response.status))
		}
		return aurralAlbumSearchResult(
			baseUrl = baseUrl,
			query = request.query,
			response = response.body()
		)
	}

	override suspend fun fetchAlbumTracks(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		libraryAlbumId: String?
	): List<AurralAlbumTrackItem> {
		if (libraryAlbumId != null) {
			val libraryTracks = fetchAlbumTracksOrNull(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				path = "api/library/tracks",
				parameters = mapOf(
					"albumId" to libraryAlbumId,
					"releaseGroupMbid" to releaseGroupMbid
				),
				operation = "Aurral library album tracks"
			)
			if (!libraryTracks.isNullOrEmpty()) return libraryTracks
		}
		return fetchAlbumTracksOrNull(
			baseUrl = baseUrl,
			requestHeaders = requestHeaders,
			path = "api/artists/release-group/${aurralEncodeUrlComponent(releaseGroupMbid)}/tracks",
			parameters = emptyMap(),
			operation = "Aurral release-group tracks"
		).orEmpty()
	}

	private suspend fun fetchAlbumTracksOrNull(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String,
		parameters: Map<String, String>,
		operation: String
	): List<AurralAlbumTrackItem>? {
		val response = client.get(aurralEndpoint(baseUrl, path)) {
			aurralJsonRequest(requestHeaders)
			parameters.forEach { (key, value) -> parameter(key, value) }
		}
		return when {
			response.status.isSuccess() -> aurralAlbumTrackItems(
				decodeAurralAlbumTracks(response.bodyAsText())
			)
			response.status == HttpStatusCode.NotFound -> null
			response.status == HttpStatusCode.Unauthorized -> emptyList()
			response.status == HttpStatusCode.Forbidden -> emptyList()
			else -> error(aurralHttpErrorMessage(operation, response.status))
		}
	}

	override suspend fun fetchArtistEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment = coroutineScope {
		val details = async {
			fetchArtistDetails(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		}
		val preview = async {
			fetchArtistPreview(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		}
		val similar = async {
			fetchSimilarArtists(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		}
		val requests = async { fetchRequests(baseUrl, requestHeaders) }

		aurralArtistEnrichment(
			baseUrl = baseUrl,
			details = details.await(),
			preview = preview.await(),
			similar = similar.await(),
			requests = requests.await()
		)
	}

	override suspend fun fetchArtistCoreEnrichment(
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
		return aurralArtistEnrichment(
			baseUrl = baseUrl,
			details = details,
			preview = AurralArtistPreviewDto(),
			similar = AurralSimilarArtistsDto(),
			requests = emptyList()
		)
	}

	override suspend fun fetchArtistPreviewTracks(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): List<AurralPreviewTrack> =
		aurralPreviewTracks(
			fetchArtistPreview(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		)

	override suspend fun fetchArtistSimilarArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): List<AurralSimilarArtist> =
		aurralSimilarArtists(
			baseUrl = baseUrl,
			similar = fetchSimilarArtists(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		)

	override suspend fun fetchAlbumRequests(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralAlbumRequest> =
		aurralAlbumRequests(fetchRequests(baseUrl, requestHeaders))

	override suspend fun fetchLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String
	): Boolean? {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/library/artists/${aurralEncodeUrlComponent(artistMbid)}")
		) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> response.body<AurralLibraryArtistDto>().monitored ?: false
			response.status == HttpStatusCode.NotFound -> false
			else -> error(aurralHttpErrorMessage("Aurral library artist lookup", response.status))
		}
	}

	private suspend fun fetchArtistDetails(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistDetailsDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${aurralEncodeUrlComponent(artistMbid)}")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
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
			aurralEndpoint(baseUrl, "api/artists/${aurralEncodeUrlComponent(artistMbid)}/preview")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
		}
		return when {
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral artist preview", response.status))
		}
	}

	private suspend fun fetchSimilarArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralSimilarArtistsDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${aurralEncodeUrlComponent(artistMbid)}/similar")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
			parameter("limit", 20)
		}
		return when {
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral similar artists", response.status))
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

	override suspend fun cancelAcquisitionRequest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		target: AurralAcquisitionDeleteTarget
	) {
		val path = when (target) {
			is AurralAcquisitionDeleteTarget.Album -> {
				"api/requests/album/${aurralEncodeUrlComponent(target.albumId)}"
			}
			is AurralAcquisitionDeleteTarget.Artist -> {
				"api/requests/${aurralEncodeUrlComponent(target.artistMbid)}"
			}
		}
		val response = client.delete(aurralEndpoint(baseUrl, path)) {
			aurralJsonRequest(requestHeaders)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral request cancellation", response.status))
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
			aurralEndpoint(baseUrl, "api/weekly-flow/flows/${aurralEncodeUrlComponent(flowId)}/enabled")
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
			aurralEndpoint(baseUrl, "api/weekly-flow/start/${aurralEncodeUrlComponent(flowId)}")
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
			aurralEndpoint(baseUrl, "api/weekly-flow/jobs/${aurralEncodeUrlComponent(flowId)}")
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
		val artistEndpoint = aurralEndpoint(baseUrl, "api/library/artists/${aurralEncodeUrlComponent(artistMbid)}")
		val existingResponse = client.get(artistEndpoint) {
			aurralJsonRequest(requestHeaders)
		}
		if (existingResponse.status.isSuccess()) {
			val updateResponse = client.put(artistEndpoint) {
				aurralJsonRequest(requestHeaders)
				header("Content-Type", ContentType.Application.Json.toString())
				setBody(payload.toMonitoringUpdatePayload())
			}
			if (updateResponse.status.isSuccess()) {
				return
			}
			error(aurralHttpErrorMessage("Aurral artist monitoring", updateResponse.status))
		}
		if (existingResponse.status != HttpStatusCode.NotFound) {
			error(aurralHttpErrorMessage("Aurral artist lookup", existingResponse.status))
		}
		if (!payload.monitored) {
			return
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
				"api/artists/release-group/${aurralEncodeUrlComponent(releaseGroupMbid)}/cover"
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

class AurralApiException(
	val status: HttpStatusCode,
	message: String
) : IllegalStateException(message), OptionalIntegrationHttpFailure {
	override val statusCode: Int = status.value
}

private fun HttpRequestBuilder.aurralJsonRequest(
	requestHeaders: Map<String, String> = emptyMap()
) {
	accept(ContentType.Application.Json)
	requestHeaders.forEach { (key, value) ->
		header(key, value)
	}
}

@Serializable
private data class AurralFlowEnabledPayload(
	val enabled: Boolean
)

@Serializable
private data class AurralFlowStartPayload(
	val limit: Int
)

@Serializable
private data class AurralLoginPayload(
	val username: String,
	val password: String
)

@Serializable
private data class AurralArtistMonitoringUpdatePayload(
	val monitored: Boolean = true,
	val monitorOption: String = "all"
)

private fun AurralArtistMonitorPayload.toMonitoringUpdatePayload(): AurralArtistMonitoringUpdatePayload =
	AurralArtistMonitoringUpdatePayload(
		monitored = monitored,
		monitorOption = monitorOption
	)
