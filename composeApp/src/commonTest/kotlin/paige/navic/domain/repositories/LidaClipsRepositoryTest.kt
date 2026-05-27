package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
