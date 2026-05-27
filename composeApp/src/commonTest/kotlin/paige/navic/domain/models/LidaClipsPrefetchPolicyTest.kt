package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LidaClipsPrefetchPolicyTest {
	@Test
	fun lidaClipsPrefetchRequiresEnabledSongIdAndNewCacheKey() {
		assertNull(nextLidaClipsPrefetchKey(
			enabled = false,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		))
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = null,
			lastPrefetchKey = null
		))

		val key = nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu/",
			apiKey = " secret ",
			songId = "song-1",
			lastPrefetchKey = null
		)

		assertEquals("https://clips.remaxku.eu|secret|song-1", key)
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = key
		))
		assertEquals(
			"https://clips.remaxku.eu|new-secret|song-1",
			nextLidaClipsPrefetchKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "new-secret",
				songId = "song-1",
				lastPrefetchKey = key
			)
		)
	}
}
