package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
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

private const val TAG = "LidaClipsRepository"

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
		val baseUrl = preferenceManager.lidaClipsBaseUrl
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

	suspend fun findClipByNavidromeSongId(songId: String): Result<DomainLidaClip?> {
		val baseUrl = preferenceManager.lidaClipsBaseUrl
		val requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
		val cacheKey = lidaClipsLookupCacheKey(baseUrl, requestHeaders, songId)

		lookupCache.get(cacheKey)?.let { cached ->
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
}

sealed interface LidaClipsConnectionResult {
	data object Connected : LidaClipsConnectionResult
	data object Unauthorized : LidaClipsConnectionResult
	data class Failed(val message: String) : LidaClipsConnectionResult
}

internal fun lidaClipsEndpoint(baseUrl: String, path: String): String =
	"${normalizeLidaClipsBaseUrl(baseUrl)}/${path.trim().trimStart('/')}"

internal fun lidaClipsNavidromeClipUrl(baseUrl: String, songId: String): String =
	lidaClipsEndpoint(
		baseUrl = baseUrl,
		path = "api/v1/navidrome/${encodePathSegment(songId)}/clip"
	)

internal fun lidaClipsRequestHeaders(apiKey: String): Map<String, String> {
	val trimmed = apiKey.trim()
	return if (trimmed.isEmpty()) emptyMap() else mapOf("X-Api-Key" to trimmed)
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

internal class LidaClipsLookupCache {
	data class Hit(val clip: DomainLidaClip?)

	private val lock = Any()
	private val clips = mutableMapOf<LidaClipsLookupCacheKey, DomainLidaClip?>()

	fun get(key: LidaClipsLookupCacheKey): Hit? = synchronized(lock) {
		if (clips.containsKey(key)) Hit(clips[key]) else null
	}

	fun put(key: LidaClipsLookupCacheKey, clip: DomainLidaClip?) {
		synchronized(lock) {
			clips[key] = clip
		}
	}
}

internal fun resolveLidaClipsStreamUrl(
	baseUrl: String,
	clipId: Int,
	streamUrl: String?
): String {
	val trimmed = streamUrl?.trim()
	return when {
		trimmed.isNullOrEmpty() -> lidaClipsEndpoint(baseUrl, "api/v1/stream/$clipId")
		trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
		trimmed.startsWith("/") -> "${normalizeLidaClipsBaseUrl(baseUrl)}$trimmed"
		else -> lidaClipsEndpoint(baseUrl, trimmed)
	}
}

private fun normalizeLidaClipsBaseUrl(baseUrl: String): String =
	baseUrl.trim().trimEnd('/')

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
