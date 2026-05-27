package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import paige.navic.domain.models.DomainLidaClip
import paige.navic.util.core.Logger
import paige.navic.util.core.synchronized
import kotlin.time.Clock

private const val TAG = "LidaClipsRepository"
internal const val LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE = "Enter the LidaClips URL first."
internal const val LIDA_CLIPS_BASE_URL_INVALID_SCHEME_MESSAGE =
	"LidaClips URL must start with http:// or https://."
private const val LIDA_CLIPS_LOOKUP_CACHE_MAX_AGE_MILLIS = 10 * 60 * 1000L

class LidaClipsRepository(
	private val preferenceManager: PreferenceManager
) {
	private val lookupCache = LidaClipsLookupCache()
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

	suspend fun testConnection(): LidaClipsConnectionResult {
		val baseUrlError = lidaClipsBaseUrlConfigurationError(preferenceManager.lidaClipsBaseUrl)
		if (baseUrlError != null) return LidaClipsConnectionResult.Failed(baseUrlError)
		val baseUrl = configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl)
			?: return LidaClipsConnectionResult.Failed(LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE)

		return try {
			val ping = client.get(lidaClipsEndpoint(baseUrl, "api/v1/ping")) {
				accept(ContentType.Application.Json)
			}
			if (!ping.status.isSuccess()) {
				return LidaClipsConnectionResult.Failed("Ping returned HTTP ${ping.status.value}")
			}

			val health = client.get(lidaClipsEndpoint(baseUrl, "api/v1/health")) {
				accept(ContentType.Application.Json)
				preferenceManager.lidaClipsRequestHeadersMap().forEach { (key, value) ->
					header(key, value)
				}
			}

			when {
				health.status.isSuccess() -> LidaClipsConnectionResult.Connected
				health.status == HttpStatusCode.Unauthorized -> LidaClipsConnectionResult.Unauthorized
				else -> LidaClipsConnectionResult.Failed(
					"Health check returned HTTP ${health.status.value}"
				)
			}
		} catch (e: Exception) {
			Logger.w(TAG, "LidaClips connection test failed", e)
			LidaClipsConnectionResult.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
		}
	}

	suspend fun prefetchClipByNavidromeSongId(songId: String) {
		findClipByNavidromeSongId(songId)
	}

	suspend fun getServiceStatus(): Result<LidaClipsServiceStatus> {
		val baseUrlError = lidaClipsBaseUrlConfigurationError(preferenceManager.lidaClipsBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl)
			?: return Result.failure(IllegalStateException(LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()

		return runCatching {
			lidaClipsServiceStatus(
				dashboard = fetchServiceDashboard(baseUrl, requestHeaders),
				control = fetchServiceControl(baseUrl, requestHeaders),
				health = fetchServiceHealth(baseUrl, requestHeaders)
			)
		}.onFailure { error ->
			Logger.w(TAG, "LidaClips service status failed", error)
		}
	}

	suspend fun setSyncPaused(syncPaused: Boolean): Result<LidaClipsServiceStatus> {
		val baseUrlError = lidaClipsBaseUrlConfigurationError(preferenceManager.lidaClipsBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl)
			?: return Result.failure(IllegalStateException(LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()

		return runCatching {
			val control = updateServiceControl(baseUrl, requestHeaders, syncPaused)
			lidaClipsServiceStatus(
				dashboard = fetchServiceDashboard(baseUrl, requestHeaders),
				control = control,
				health = fetchServiceHealth(baseUrl, requestHeaders)
			)
		}.onFailure { error ->
			Logger.w(TAG, "LidaClips sync control failed", error)
		}
	}

	suspend fun findClipByNavidromeSongId(
		songId: String,
		forceRefresh: Boolean = false
	): Result<DomainLidaClip?> {
		val baseUrlError = lidaClipsBaseUrlConfigurationError(preferenceManager.lidaClipsBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl)
			?: return Result.failure(IllegalStateException(LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
		val cacheKey = lidaClipsLookupCacheKey(baseUrl, requestHeaders, songId)

		lookupCache.get(cacheKey, bypass = forceRefresh)?.let { cached ->
			return Result.success(cached.clip)
		}

		return fetchClipByNavidromeSongId(baseUrl, requestHeaders, songId)
			.onSuccess { clip -> lookupCache.put(cacheKey, clip) }
			.onFailure { error ->
				Logger.w(TAG, "Clip lookup failed for Navidrome song $songId", error)
			}
	}

	private suspend fun fetchClipByNavidromeSongId(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		songId: String
	): Result<DomainLidaClip?> = runCatching {
		val response = client.get(lidaClipsNavidromeClipUrl(baseUrl, songId)) {
			accept(ContentType.Application.Json)
			requestHeaders.forEach { (key, value) ->
				header(key, value)
			}
		}

		when {
			response.status == HttpStatusCode.NotFound -> null
			response.status.isSuccess() -> response.body<LidaClipsClipEnvelope>().clip
				?.toDomainModel(baseUrl)
			else -> error("LidaClips returned HTTP ${response.status.value}")
		}
	}

	private suspend fun fetchServiceDashboard(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): LidaClipsDashboardDto {
		val response = client.get(lidaClipsEndpoint(baseUrl, "api/v1/dashboard")) {
			accept(ContentType.Application.Json)
			requestHeaders.forEach { (key, value) ->
				header(key, value)
			}
		}
		if (!response.status.isSuccess()) {
			error("LidaClips dashboard returned HTTP ${response.status.value}")
		}
		return response.body()
	}

	private suspend fun fetchServiceControl(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): LidaClipsControlDto {
		val response = client.get(lidaClipsEndpoint(baseUrl, "api/v1/control")) {
			accept(ContentType.Application.Json)
			requestHeaders.forEach { (key, value) ->
				header(key, value)
			}
		}
		if (!response.status.isSuccess()) {
			error("LidaClips control returned HTTP ${response.status.value}")
		}
		return response.body()
	}

	private suspend fun fetchServiceHealth(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): LidaClipsHealthDto {
		val response = client.get(lidaClipsEndpoint(baseUrl, "api/v1/health")) {
			accept(ContentType.Application.Json)
			requestHeaders.forEach { (key, value) ->
				header(key, value)
			}
		}
		if (!response.status.isSuccess() && response.status != HttpStatusCode.ServiceUnavailable) {
			error("LidaClips health returned HTTP ${response.status.value}")
		}
		return response.body()
	}

	private suspend fun updateServiceControl(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		syncPaused: Boolean
	): LidaClipsControlDto {
		val response = client.post(lidaClipsEndpoint(baseUrl, "api/v1/control")) {
			accept(ContentType.Application.Json)
			header("Content-Type", ContentType.Application.Json.toString())
			requestHeaders.forEach { (key, value) ->
				header(key, value)
			}
			setBody(LidaClipsControlRequestDto(syncPaused))
		}
		if (!response.status.isSuccess()) {
			error("LidaClips control update returned HTTP ${response.status.value}")
		}
		return response.body()
	}
}

sealed interface LidaClipsConnectionResult {
	data object Connected : LidaClipsConnectionResult
	data object Unauthorized : LidaClipsConnectionResult
	data class Failed(val message: String) : LidaClipsConnectionResult
}

data class LidaClipsServiceStatus(
	val activeClips: Int,
	val officialClips: Int,
	val fallbackClips: Int,
	val syncPaused: Boolean,
	val syncRunning: Boolean,
	val health: LidaClipsHealthStatus = LidaClipsHealthStatus(),
	val recentFailures: List<LidaClipsRecentFailure> = emptyList()
)

data class LidaClipsHealthStatus(
	val status: String = "unknown",
	val checks: List<LidaClipsHealthCheck> = emptyList()
)

data class LidaClipsHealthCheck(
	val name: String,
	val ok: Boolean,
	val error: String?,
	val address: String?,
	val path: String?,
	val skipped: Boolean
)

data class LidaClipsRecentFailure(
	val lidarrTrackId: Int?,
	val artist: String?,
	val album: String?,
	val track: String?,
	val reason: String?,
	val retryAfter: String?,
	val updatedAt: String?
)

@Serializable
internal data class LidaClipsDashboardDto(
	@SerialName("active_clips") val activeClips: Int = 0,
	@SerialName("official_clips") val officialClips: Int = 0,
	@SerialName("fallback_clips") val fallbackClips: Int = 0,
	@SerialName("sync_paused") val syncPaused: Boolean = false,
	@SerialName("recent_failures") val recentFailures: List<LidaClipsRecentFailureDto> = emptyList()
)

@Serializable
internal data class LidaClipsRecentFailureDto(
	@SerialName("lidarr_track_id") val lidarrTrackId: Int? = null,
	val artist: String? = null,
	val album: String? = null,
	val track: String? = null,
	val reason: String? = null,
	@SerialName("retry_after") val retryAfter: String? = null,
	@SerialName("updated_at") val updatedAt: String? = null
) {
	fun toDomainModel() = LidaClipsRecentFailure(
		lidarrTrackId = lidarrTrackId,
		artist = artist,
		album = album,
		track = track,
		reason = reason,
		retryAfter = retryAfter,
		updatedAt = updatedAt
	)
}

@Serializable
internal data class LidaClipsHealthDto(
	val status: String = "unknown",
	val checks: Map<String, LidaClipsHealthCheckDto> = emptyMap()
) {
	fun toDomainModel() = LidaClipsHealthStatus(
		status = status,
		checks = checks.toSortedMap().map { (name, check) ->
			check.toDomainModel(name)
		}
	)
}

@Serializable
internal data class LidaClipsHealthCheckDto(
	val ok: Boolean = false,
	val error: String? = null,
	val address: String? = null,
	val path: String? = null,
	val skipped: Boolean = false
) {
	fun toDomainModel(name: String) = LidaClipsHealthCheck(
		name = name,
		ok = ok,
		error = error,
		address = address,
		path = path,
		skipped = skipped
	)
}

@Serializable
internal data class LidaClipsControlDto(
	@SerialName("sync_paused") val syncPaused: Boolean = false,
	@SerialName("sync_running") val syncRunning: Boolean = false
)

@Serializable
internal data class LidaClipsControlRequestDto(
	@SerialName("sync_paused") val syncPaused: Boolean
)

internal fun lidaClipsServiceStatus(
	dashboard: LidaClipsDashboardDto,
	control: LidaClipsControlDto,
	health: LidaClipsHealthDto = LidaClipsHealthDto()
): LidaClipsServiceStatus =
	LidaClipsServiceStatus(
		activeClips = dashboard.activeClips,
		officialClips = dashboard.officialClips,
		fallbackClips = dashboard.fallbackClips,
		syncPaused = control.syncPaused,
		syncRunning = control.syncRunning,
		health = health.toDomainModel(),
		recentFailures = dashboard.recentFailures.map { it.toDomainModel() }
	)

internal fun lidaClipsEndpoint(baseUrl: String, path: String): String =
	"${normalizeLidaClipsBaseUrl(baseUrl)}/${path.trim().trimStart('/')}"

internal fun configuredLidaClipsBaseUrl(baseUrl: String): String? =
	baseUrl.trim().trimEnd('/').takeIf {
		it.isNotEmpty() && it.hasSupportedHttpScheme()
	}

internal fun lidaClipsBaseUrlConfigurationError(baseUrl: String): String? {
	val trimmed = baseUrl.trim().trimEnd('/')
	return when {
		trimmed.isEmpty() -> LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE
		!trimmed.hasSupportedHttpScheme() -> LIDA_CLIPS_BASE_URL_INVALID_SCHEME_MESSAGE
		else -> null
	}
}

internal fun lidaClipsNavidromeClipUrl(baseUrl: String, songId: String): String =
	lidaClipsEndpoint(
		baseUrl = baseUrl,
		path = "api/v1/navidrome/${encodePathSegment(songId)}/clip"
	)

internal fun lidaClipsRequestHeaders(apiKey: String): Map<String, String> {
	val trimmed = apiKey.trim()
	return if (trimmed.isEmpty()) emptyMap() else mapOf("X-Api-Key" to trimmed)
}

internal fun lidaClipsStreamRequestHeaders(
	baseUrl: String,
	streamUrl: String,
	requestHeaders: Map<String, String>
): Map<String, String> {
	val streamOrigin = httpOrigin(streamUrl) ?: return emptyMap()
	val baseOrigin = configuredLidaClipsBaseUrl(baseUrl)?.let(::httpOrigin) ?: return emptyMap()
	if (streamOrigin != baseOrigin) return emptyMap()

	return requestHeaders
		.map { (key, value) -> key.trim() to value.trim() }
		.filter { (key, value) -> key.isNotEmpty() && value.isNotEmpty() }
		.toMap()
}

internal data class LidaClipsLookupCacheKey(
	val baseUrl: String,
	val requestHeaders: List<Pair<String, String>>,
	val songId: String
)

internal fun lidaClipsLookupCacheKey(
	baseUrl: String,
	requestHeaders: Map<String, String>,
	songId: String
): LidaClipsLookupCacheKey =
	LidaClipsLookupCacheKey(
		baseUrl = normalizeLidaClipsBaseUrl(baseUrl),
		requestHeaders = requestHeaders.entries
			.map { it.key.trim() to it.value.trim() }
			.filter { (key, value) -> key.isNotEmpty() && value.isNotEmpty() }
			.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }),
		songId = songId
	)

internal class LidaClipsLookupCache(
	private val maxAgeMillis: Long = LIDA_CLIPS_LOOKUP_CACHE_MAX_AGE_MILLIS,
	private val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
	data class Hit(val clip: DomainLidaClip?)
	private data class Entry(
		val clip: DomainLidaClip?,
		val createdAtMillis: Long
	)

	private val lock = Any()
	private val clips = mutableMapOf<LidaClipsLookupCacheKey, Entry>()

	fun get(
		key: LidaClipsLookupCacheKey,
		bypass: Boolean = false
	): Hit? = synchronized(lock) {
		if (bypass) return@synchronized null
		val entry = clips[key] ?: return@synchronized null
		if (entry.isExpired(currentTimeMillis())) {
			clips.remove(key)
			null
		} else {
			Hit(entry.clip)
		}
	}

	fun put(key: LidaClipsLookupCacheKey, clip: DomainLidaClip?) {
		synchronized(lock) {
			clips[key] = Entry(clip, currentTimeMillis())
		}
	}

	private fun Entry.isExpired(nowMillis: Long): Boolean =
		nowMillis - createdAtMillis > maxAgeMillis
}

internal fun resolveLidaClipsStreamUrl(
	baseUrl: String,
	clipId: Int,
	streamUrl: String?
): String {
	val trimmed = streamUrl?.trim()
	return when {
		trimmed.isNullOrEmpty() -> lidaClipsEndpoint(baseUrl, "api/v1/stream/$clipId")
		trimmed.hasSupportedHttpScheme() -> trimmed
		trimmed.startsWith("/") -> "${normalizeLidaClipsBaseUrl(baseUrl)}$trimmed"
		else -> lidaClipsEndpoint(baseUrl, trimmed)
	}
}

private data class HttpOrigin(
	val scheme: String,
	val host: String,
	val port: Int
)

private fun httpOrigin(url: String): HttpOrigin? {
	val trimmed = url.trim()
	val schemeSeparator = trimmed.indexOf("://")
	if (schemeSeparator <= 0) return null

	val scheme = trimmed.substring(0, schemeSeparator).lowercase()
	if (scheme != "http" && scheme != "https") return null

	val authority = trimmed
		.drop(schemeSeparator + 3)
		.takeWhile { it != '/' && it != '?' && it != '#' }
		.substringAfterLast('@')
	if (authority.isBlank()) return null

	val host: String
	val portText: String?
	if (authority.startsWith("[")) {
		val closingBracket = authority.indexOf(']')
		if (closingBracket == -1) return null

		host = authority.substring(1, closingBracket)
		portText = authority
			.drop(closingBracket + 1)
			.takeIf { it.startsWith(":") }
			?.drop(1)
	} else {
		val firstColon = authority.indexOf(':')
		val lastColon = authority.lastIndexOf(':')
		if (firstColon != -1 && firstColon != lastColon) return null

		host = if (lastColon == -1) authority else authority.substring(0, lastColon)
		portText = if (lastColon == -1) null else authority.substring(lastColon + 1)
	}

	val normalizedHost = host.trim().trimEnd('.').lowercase().takeIf { it.isNotEmpty() }
		?: return null
	val explicitPortText = portText?.takeIf { it.isNotBlank() }
	val port = explicitPortText?.toIntOrNull()
		?: if (explicitPortText == null) when (scheme) {
			"http" -> 80
			else -> 443
		} else return null

	return HttpOrigin(scheme, normalizedHost, port)
}

private fun normalizeLidaClipsBaseUrl(baseUrl: String): String =
	configuredLidaClipsBaseUrl(baseUrl)
		?: error(lidaClipsBaseUrlConfigurationError(baseUrl)
			?: LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE)

private fun String.hasSupportedHttpScheme(): Boolean =
	startsWith("http://", ignoreCase = true) ||
		startsWith("https://", ignoreCase = true)

private fun encodePathSegment(value: String): String {
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

@Serializable
private data class LidaClipsClipEnvelope(
	val clip: LidaClipsClipDto? = null
)

@Serializable
private data class LidaClipsClipDto(
	val id: Int,
	@SerialName("navidrome_song_id") val navidromeSongId: String? = null,
	val title: String? = null,
	val artist: String? = null,
	val album: String? = null,
	val track: String? = null,
	val duration: Int? = null,
	@SerialName("mime_type") val mimeType: String? = null,
	val score: Float? = null,
	@SerialName("quality_tier") val qualityTier: String? = null,
	@SerialName("stream_url") val streamUrl: String? = null,
	@SerialName("file_name") val fileName: String? = null
) {
	fun toDomainModel(baseUrl: String) = DomainLidaClip(
		id = id,
		navidromeSongId = navidromeSongId,
		title = title ?: fileName ?: "Music video",
		artist = artist,
		album = album,
		track = track,
		durationSeconds = duration,
		mimeType = mimeType,
		score = score,
		qualityTier = qualityTier,
		fileName = fileName,
		streamUrl = resolveLidaClipsStreamUrl(baseUrl, id, streamUrl)
	)
}
