package paige.navic.domain.repositories

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.models.DomainLidaClip

class LidaClipsRepositoryTest {
	@Test
	fun lidaClipsEndpointNormalizesBaseUrlAndPath() {
		assertEquals(
			"https://clips.remaxku.eu/api/v1/ping",
			lidaClipsEndpoint(" https://clips.remaxku.eu/ ", "/api/v1/ping")
		)
	}

	@Test
	fun lidaClipsEndpointRequiresConfiguredBaseUrl() {
		assertEquals(null, configuredLidaClipsBaseUrl(" "))
		assertEquals("https://clips.remaxku.eu", configuredLidaClipsBaseUrl(" https://clips.remaxku.eu/ "))

		val error = assertFailsWith<IllegalStateException> {
			lidaClipsEndpoint(" ", "/api/v1/ping")
		}
		assertEquals(LIDA_CLIPS_BASE_URL_REQUIRED_MESSAGE, error.message)
	}

	@Test
	fun lidaClipsEndpointRequiresHttpOrHttpsBaseUrl() {
		assertEquals(null, configuredLidaClipsBaseUrl("clips.remaxku.eu"))

		val error = assertFailsWith<IllegalStateException> {
			lidaClipsEndpoint("clips.remaxku.eu", "/api/v1/ping")
		}
		assertEquals(LIDA_CLIPS_BASE_URL_INVALID_SCHEME_MESSAGE, error.message)
	}

	@Test
	fun lidaClipsNavidromeClipUrlEncodesSongIdAsPathSegment() {
		assertEquals(
			"https://clips.remaxku.eu/api/v1/navidrome/song%2042%2Fdemo/clip",
			lidaClipsNavidromeClipUrl("https://clips.remaxku.eu", "song 42/demo")
		)
	}

	@Test
	fun lidaClipsRequestHeadersIncludeTrimmedApiKeyOnlyWhenPresent() {
		assertEquals(
			mapOf("X-Api-Key" to "secret"),
			lidaClipsRequestHeaders(" secret ")
		)
		assertEquals(emptyMap(), lidaClipsRequestHeaders(" "))
	}

	@Test
	fun lidaClipsStreamUrlResolvesRelativeApiPath() {
		assertEquals(
			"https://clips.remaxku.eu/api/v1/stream/7",
			resolveLidaClipsStreamUrl(
				baseUrl = "https://clips.remaxku.eu/",
				clipId = 7,
				streamUrl = "/api/v1/stream/7"
			)
		)
	}

	@Test
	fun lidaClipsStreamUrlFallsBackToClipIdWhenResponseOmitsStreamUrl() {
		assertEquals(
			"https://clips.remaxku.eu/api/v1/stream/7",
			resolveLidaClipsStreamUrl(
				baseUrl = "https://clips.remaxku.eu",
				clipId = 7,
				streamUrl = null
			)
		)
	}

	@Test
	fun lidaClipsLookupCacheKeyNormalizesBaseUrlAndHeaders() {
		assertEquals(
			lidaClipsLookupCacheKey(
				baseUrl = "https://clips.remaxku.eu/",
				requestHeaders = mapOf("X-Api-Key" to "secret", "X-Trace" to "1"),
				songId = "song-1"
			),
			lidaClipsLookupCacheKey(
				baseUrl = " https://clips.remaxku.eu ",
				requestHeaders = mapOf("X-Trace" to "1", "X-Api-Key" to "secret"),
				songId = "song-1"
			)
		)
	}

	@Test
	fun lidaClipsLookupCacheDistinguishesApiKeysAndCachesMissingClips() {
		val cache = LidaClipsLookupCache()
		val firstKey = lidaClipsLookupCacheKey(
			baseUrl = "https://clips.remaxku.eu",
			requestHeaders = mapOf("X-Api-Key" to "first"),
			songId = "song-1"
		)
		val secondKey = lidaClipsLookupCacheKey(
			baseUrl = "https://clips.remaxku.eu",
			requestHeaders = mapOf("X-Api-Key" to "second"),
			songId = "song-1"
		)
		val clip = lidaClip()

		cache.put(firstKey, null)
		cache.put(secondKey, clip)

		assertEquals(LidaClipsLookupCache.Hit(null), cache.get(firstKey))
		assertEquals(LidaClipsLookupCache.Hit(clip), cache.get(secondKey))
		assertNull(cache.get(lidaClipsLookupCacheKey(
			baseUrl = "https://clips.remaxku.eu",
			requestHeaders = mapOf("X-Api-Key" to "first"),
			songId = "song-2"
		)))
	}

	@Test
	fun lidaClipsServiceStatusUsesDashboardCountsAndControlRuntimeState() {
		val status = lidaClipsServiceStatus(
			dashboard = LidaClipsDashboardDto(
				activeClips = 12,
				officialClips = 7,
				fallbackClips = 5,
				syncPaused = false,
				recentFailures = listOf(
					LidaClipsRecentFailureDto(
						lidarrTrackId = 42,
						artist = "Artist",
						album = "Album",
						track = "Track",
						reason = "no_video_found",
						retryAfter = "2026-05-28T10:30:00Z",
						updatedAt = "2026-05-27T10:30:00Z"
					)
				)
			),
			control = LidaClipsControlDto(
				syncPaused = true,
				syncRunning = true
			),
			health = LidaClipsHealthDto(
				status = "degraded",
				checks = mapOf(
					"database" to LidaClipsHealthCheckDto(ok = true),
					"youtube_proxy" to LidaClipsHealthCheckDto(
						ok = false,
						error = "proxy unavailable",
						address = "http://proxy:8888"
					)
				)
			)
		)

		assertEquals(12, status.activeClips)
		assertEquals(7, status.officialClips)
		assertEquals(5, status.fallbackClips)
		assertTrue(status.syncPaused)
		assertTrue(status.syncRunning)
		assertEquals("degraded", status.health.status)
		assertEquals(
			listOf(
				LidaClipsHealthCheck(
					name = "database",
					ok = true,
					error = null,
					address = null,
					path = null,
					skipped = false
				),
				LidaClipsHealthCheck(
					name = "youtube_proxy",
					ok = false,
					error = "proxy unavailable",
					address = "http://proxy:8888",
					path = null,
					skipped = false
				)
			),
			status.health.checks
		)
		assertEquals(
			listOf(
				LidaClipsRecentFailure(
					lidarrTrackId = 42,
					artist = "Artist",
					album = "Album",
					track = "Track",
					reason = "no_video_found",
					retryAfter = "2026-05-28T10:30:00Z",
					updatedAt = "2026-05-27T10:30:00Z"
				)
			),
			status.recentFailures
		)
	}

	@Test
	fun lidaClipsControlRequestBodyUsesBackendFieldName() {
		assertEquals(
			"""{"sync_paused":true}""",
			Json.encodeToString(LidaClipsControlRequestDto(syncPaused = true))
		)
	}

	private fun lidaClip() = DomainLidaClip(
		id = 7,
		navidromeSongId = "song-1",
		title = "Music video",
		artist = "Artist",
		album = "Album",
		track = null,
		durationSeconds = 180,
		mimeType = "video/mp4",
		score = 1f,
		qualityTier = "hd",
		fileName = "clip.mp4",
		streamUrl = "https://clips.remaxku.eu/api/v1/stream/7"
	)
}
