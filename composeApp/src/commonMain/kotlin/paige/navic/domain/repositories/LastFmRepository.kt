package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.models.LastFmTopTrack
import paige.navic.util.core.Logger

private const val LASTFM_BASE_URL = "https://ws.audioscrobbler.com/2.0/"
private const val TAG = "LastFmRepository"

class LastFmRepository(
	private val preferenceManager: PreferenceManager,
	private val apiClient: LastFmApiClient = KtorLastFmApiClient()
) {
	suspend fun testConnection(): LastFmConnectionResult {
		if (!preferenceManager.lastFmEnabled) return LastFmConnectionResult.Disabled
		val apiKey = preferenceManager.lastFmApiKey.trim()
		if (apiKey.isEmpty()) return LastFmConnectionResult.MissingApiKey

		return runCatching {
			apiClient.probeService(apiKey)
		}.fold(
			onSuccess = { probe ->
				preferenceManager.markIntegrationServiceAvailable(IntegrationService.LastFm)
				LastFmConnectionResult.Connected(sampleArtistCount = probe.sampleArtistCount)
			},
			onFailure = { error ->
				preferenceManager.markIntegrationServiceDown(IntegrationService.LastFm)
				if ((error as? LastFmApiException)?.code in invalidApiKeyErrorCodes) {
					LastFmConnectionResult.InvalidApiKey
				} else {
					Logger.w(TAG, "Last.fm connection test failed", error)
					LastFmConnectionResult.Failed(error.message ?: error::class.simpleName ?: "Unknown error")
				}
			}
		)
	}

	suspend fun getServiceStatus(): Result<LastFmServiceStatus> {
		val apiKey = preferenceManager.lastFmApiKey.trim()
		if (!preferenceManager.lastFmEnabled) {
			return Result.success(
				LastFmServiceStatus(
					enabled = false,
					apiKeyConfigured = apiKey.isNotEmpty(),
					artistTopTracksEnabled = false
				)
			)
		}
		if (apiKey.isEmpty()) {
			return Result.success(
				LastFmServiceStatus(
					enabled = true,
					apiKeyConfigured = false,
					artistTopTracksEnabled = false
				)
			)
		}

		return runCatching {
			val probe = apiClient.probeService(apiKey)
			LastFmServiceStatus(
				enabled = true,
				apiKeyConfigured = true,
				artistTopTracksEnabled = true,
				sampleArtistCount = probe.sampleArtistCount
			)
		}.onFailure { error ->
			Logger.w(TAG, "Last.fm service status failed", error)
		}.recordLastFmAvailability()
	}

	suspend fun getArtistTopTracks(
		artistName: String,
		artistMbid: String?,
		limit: Int = 12
	): Result<List<LastFmTopTrack>> {
		if (!preferenceManager.lastFmEnabled) return Result.success(emptyList())
		val apiKey = preferenceManager.lastFmApiKey.trim()
		if (apiKey.isEmpty()) return Result.success(emptyList())
		val safeArtistName = artistName.trim()
		val safeArtistMbid = artistMbid?.trim()?.takeIf { it.isNotEmpty() }
		if (safeArtistName.isEmpty() && safeArtistMbid == null) return Result.success(emptyList())

		return runCatching {
			apiClient.fetchArtistTopTracks(
				apiKey = apiKey,
				artistName = safeArtistName.takeIf { it.isNotEmpty() },
				artistMbid = safeArtistMbid,
				limit = limit.coerceAtLeast(0)
			)
		}.onFailure { error ->
			Logger.w(TAG, "Last.fm top tracks failed for $artistName", error)
		}.recordLastFmAvailability()
	}

	private fun <T> Result<T>.recordLastFmAvailability(): Result<T> =
		onSuccess {
			preferenceManager.markIntegrationServiceAvailable(IntegrationService.LastFm)
		}.onFailure {
			preferenceManager.markIntegrationServiceDown(IntegrationService.LastFm)
		}
}

interface LastFmApiClient {
	suspend fun fetchArtistTopTracks(
		apiKey: String,
		artistName: String?,
		artistMbid: String?,
		limit: Int
	): List<LastFmTopTrack>

	suspend fun probeService(apiKey: String): LastFmServiceProbe
}

private class KtorLastFmApiClient : LastFmApiClient {
	private val client = HttpClient {
		install(HttpTimeout) {
			requestTimeoutMillis = 15_000
			connectTimeoutMillis = 10_000
			socketTimeoutMillis = 15_000
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

	override suspend fun fetchArtistTopTracks(
		apiKey: String,
		artistName: String?,
		artistMbid: String?,
		limit: Int
	): List<LastFmTopTrack> {
		val response = client.get(LASTFM_BASE_URL) {
			parameter("method", "artist.getTopTracks")
			parameter("api_key", apiKey)
			parameter("format", "json")
			parameter("limit", limit)
			parameter("autocorrect", 1)
			if (artistMbid != null) {
				parameter("mbid", artistMbid)
			} else {
				parameter("artist", artistName.orEmpty())
			}
		}
		if (!response.status.isSuccess()) {
			error("Last.fm returned ${response.status.value}")
		}
		return response.body<LastFmTopTracksResponseDto>().toTopTracks()
	}

	override suspend fun probeService(apiKey: String): LastFmServiceProbe {
		val response = client.get(LASTFM_BASE_URL) {
			parameter("method", "chart.getTopArtists")
			parameter("api_key", apiKey)
			parameter("format", "json")
			parameter("limit", 1)
		}
		if (!response.status.isSuccess()) {
			error("Last.fm returned ${response.status.value}")
		}
		return response.body<LastFmChartTopArtistsResponseDto>().toServiceProbe()
	}
}

private val invalidApiKeyErrorCodes = setOf(10, 26)

sealed interface LastFmConnectionResult {
	data object Disabled : LastFmConnectionResult
	data object MissingApiKey : LastFmConnectionResult
	data object InvalidApiKey : LastFmConnectionResult
	data class Connected(val sampleArtistCount: Int) : LastFmConnectionResult
	data class Failed(val message: String) : LastFmConnectionResult
}

data class LastFmServiceStatus(
	val enabled: Boolean = true,
	val apiKeyConfigured: Boolean,
	val artistTopTracksEnabled: Boolean,
	val sampleArtistCount: Int? = null
)

data class LastFmServiceProbe(
	val sampleArtistCount: Int
)

class LastFmApiException(
	val code: Int?,
	detail: String
) : IllegalStateException(detail)

@Serializable
internal data class LastFmTopTracksResponseDto(
	@SerialName("toptracks") val topTracks: LastFmTopTracksDto? = null,
	val error: Int? = null,
	val message: String? = null
)

@Serializable
internal data class LastFmChartTopArtistsResponseDto(
	@SerialName("artists") val artists: LastFmChartArtistsDto? = null,
	val error: Int? = null,
	val message: String? = null
)

@Serializable
internal data class LastFmChartArtistsDto(
	val artist: List<LastFmChartArtistDto> = emptyList()
)

@Serializable
internal data class LastFmChartArtistDto(
	val name: String? = null
)

@Serializable
internal data class LastFmTopTracksDto(
	val track: List<LastFmTopTrackDto> = emptyList()
)

@Serializable
internal data class LastFmTopTrackDto(
	val name: String? = null,
	val playcount: String? = null,
	val url: String? = null,
	@SerialName("@attr") val attr: LastFmTopTrackAttrDto? = null
)

@Serializable
internal data class LastFmTopTrackAttrDto(
	val rank: String? = null
)

internal fun LastFmTopTracksResponseDto.toTopTracks(): List<LastFmTopTrack> {
	error?.let { code ->
		val detail = message?.trim()?.takeIf { it.isNotEmpty() } ?: "Last.fm error $code"
		throw LastFmApiException(code = code, detail = detail)
	}
	return topTracks?.track.orEmpty().mapIndexedNotNull { index, track ->
		val name = track.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
		LastFmTopTrack(
			name = name,
			rank = track.attr?.rank?.toIntOrNull() ?: (index + 1),
			playCount = track.playcount?.toLongOrNull(),
			url = track.url?.trim()?.takeIf { it.isNotEmpty() }
		)
	}
}

internal fun LastFmChartTopArtistsResponseDto.toServiceProbe(): LastFmServiceProbe {
	error?.let { code ->
		val detail = message?.trim()?.takeIf { it.isNotEmpty() } ?: "Last.fm error $code"
		throw LastFmApiException(code = code, detail = detail)
	}
	return LastFmServiceProbe(
		sampleArtistCount = artists?.artist.orEmpty().count { !it.name.isNullOrBlank() }
	)
}
