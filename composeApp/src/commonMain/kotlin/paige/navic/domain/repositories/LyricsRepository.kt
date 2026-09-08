package paige.navic.domain.repositories

import com.russhwolf.settings.Settings
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import paige.navic.data.database.dao.LyricDao
import paige.navic.data.database.entities.LyricEntity
import paige.navic.data.remote.NetworkClientFactory
import paige.navic.data.remote.NetworkJson
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.lyrics.LyricsConfig
import paige.navic.domain.models.lyrics.LyricsLine
import paige.navic.domain.models.lyrics.LyricsProvider
import paige.navic.domain.models.lyrics.LyricsResult
import paige.navic.domain.models.lyrics.normalizedLyricsConfig
import paige.navic.domain.parser.LyricsContentParser
import paige.navic.util.core.Logger
import kotlin.time.Duration.Companion.milliseconds

class LyricsRepository(
	private val lyricDao: LyricDao,
	private val settings: Settings,
	private val sessionManager: SessionManager,
	networkClientFactory: NetworkClientFactory = NetworkClientFactory()
) {

	private val client = networkClientFactory.create {
		install(HttpTimeout) {
			requestTimeoutMillis = 40000
			connectTimeoutMillis = 40000
			socketTimeoutMillis = 40000
		}
	}
	private val json = NetworkJson.compatible

	private fun getConfig(): LyricsConfig {
		val raw = settings.getStringOrNull(LyricsConfig.KEY)
		val config = try {
			if (raw != null) json.decodeFromString<LyricsConfig>(raw)
			else LyricsConfig()
		} catch (_: Exception) {
			LyricsConfig()
		}
		return normalizedLyricsConfig(config)
	}

	suspend fun fetchLyrics(song: DomainSong): LyricsResult? {
		val currentConfig = getConfig()
		var cachedResult: LyricsResult? = null
		try {
			val cached = lyricDao.getLyrics(song.id)
			currentCoroutineContext().ensureActive()
			if (cached != null) {
				val parsed = LyricsContentParser.parse(cached.rawContent)
				if (!parsed.isNullOrEmpty()) {
					cachedResult = LyricsResult(parsed, cached.provider, cached.rawContent)
					if (shouldUseCachedLyricsBeforeFetch(cached.provider, currentConfig.priority)) {
						return cachedResult
					}
				}
			}
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (_: Exception) {
		}

		return fetchFirstAvailableLyrics(currentConfig.priority, cachedResult) { provider ->
			var rawContentToCache: String? = null

			val parsedLyrics = when (provider) {
				LyricsProvider.LYRICS_PLUS -> {
					val raw = fetchRawLyricsPlus(song, currentConfig)
					rawContentToCache = raw
					raw?.let { LyricsContentParser.parse(it) }
				}

				LyricsProvider.LRCLIB -> {
					val raw = fetchRawLrcLib(song, currentConfig)
					rawContentToCache = raw
					raw?.let { LyricsContentParser.parse(it) }
				}

				LyricsProvider.SUBSONIC -> {
					val subsonicLyrics = sessionManager.withApi { it.getLyrics(song.id) }.firstOrNull()

					val lines = subsonicLyrics?.lines?.flatMap { line ->
						if (!subsonicLyrics.synced && line.value.contains("\n")) {
							line.value.lineSequence()
								.filter { it.isNotBlank() }
								.map { LyricsLine(time = null, text = it.trim()) }
								.toList()
						} else {
							val time =
								if (subsonicLyrics.synced) line.start?.milliseconds else null
							listOf(LyricsLine(time = time, text = line.value))
						}
					}

					val mergedLines = lines?.let { LyricsContentParser.mergeDuplicateSyncedLines(it) }
					if (!mergedLines.isNullOrEmpty()) {
						rawContentToCache = mergedLines.joinToString("\n") { l ->
							val t = l.time
							if (t != null) {
								val m = t.inWholeMinutes.toString().padStart(2, '0')
								val s = (t.inWholeSeconds % 60).toString().padStart(2, '0')
								val ms = ((t.inWholeMilliseconds % 1000) / 10).toString()
									.padStart(2, '0')
								l.text.lineSequence().joinToString("\n") { text ->
									"[$m:$s.$ms]$text"
								}
							} else l.text
						}
					}
					mergedLines
				}
			}

			if (!parsedLyrics.isNullOrEmpty()) {
				try {
					rawContentToCache?.let { content ->
						val entity = LyricEntity(
							songId = song.id,
							provider = provider,
							rawContent = content
						)
						lyricDao.insertLyrics(entity)
					}
				} catch (cancelled: CancellationException) {
					throw cancelled
				} catch (e: Exception) {
					Logger.e("LyricRepository", "Failed to cache lyrics for ${song.title}", e)
				}
				LyricsResult(parsedLyrics, provider, rawContentToCache)
			} else {
				null
			}
		}
	}

	private suspend fun fetchRawLrcLib(song: DomainSong, config: LyricsConfig): String? {
		val relaxedTrackName = relaxedLrcLibTrackName(song.title)
		val durationSeconds = lrcLibDurationSeconds(song.duration)
		return fetchFirstAvailableLyrics(listOf(true, false)) { search ->
			if (search) {
				val content = client.get(normalizedLrcLibSearchUrl(config.lrcLibBaseUrl)) {
					parameter("q", "$relaxedTrackName ${song.artistName}")
					accept(ContentType.Application.Json)
				}.lyricsContentOrNull()
				content?.let {
					selectLrcLibCandidate(
						candidates = json.decodeFromString<List<LrcLibCandidate>>(content),
						trackName = relaxedTrackName,
						artistName = song.artistName,
						albumName = song.albumTitle,
						durationSeconds = durationSeconds
					)?.let(json::encodeToString)
				}
			} else {
				client.get(lrcLibExactUrl(config.lrcLibBaseUrl)) {
					parameter("track_name", song.title)
					parameter("artist_name", song.artistName)
					parameter("album_name", song.albumTitle)
					parameter("duration", durationSeconds)
					accept(ContentType.Application.Json)
				}.lyricsContentOrNull()
			}
		}
	}

	private suspend fun fetchRawLyricsPlus(song: DomainSong, config: LyricsConfig): String? {
		return fetchFirstAvailableLyrics(config.lyricsPlusMirrors) { baseUrl ->
			client.get("$baseUrl/v2/lyrics/get") {
				parameter("title", song.title)
				parameter("artist", song.artistName)
				parameter("album", song.albumTitle)
				parameter("duration", song.duration)
				accept(ContentType.Application.Json)
			}.lyricsContentOrNull()
		}
	}
}

internal fun shouldUseCachedLyricsBeforeFetch(
	cachedProvider: LyricsProvider,
	priority: List<LyricsProvider>
): Boolean =
	priority.firstOrNull() == cachedProvider
