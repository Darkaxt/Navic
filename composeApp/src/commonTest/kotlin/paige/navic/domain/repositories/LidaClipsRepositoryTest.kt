package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
