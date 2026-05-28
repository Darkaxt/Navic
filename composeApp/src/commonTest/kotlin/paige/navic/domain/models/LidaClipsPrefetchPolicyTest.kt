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
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = " ",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		))

		val key = nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu/",
			apiKey = " secret ",
			songId = "song-1",
			lastPrefetchKey = null
		)

		assertEquals(
			key,
			nextLidaClipsPrefetchKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "secret",
				songId = "song-1",
				lastPrefetchKey = null
			)
		)
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = key
		))
		assertEquals(
			false,
			key == nextLidaClipsPrefetchKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "new-secret",
				songId = "song-1",
				lastPrefetchKey = key
			)
		)
	}

	@Test
	fun lidaClipsPrefetchKeyDoesNotExposeRawApiKey() {
		val key = nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		)

		assertEquals(false, key?.contains("secret") == true)
		assertEquals(false, key?.contains("X-Api-Key") == true)
		assertEquals(
			key,
			nextLidaClipsPrefetchKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = " secret ",
				songId = "song-1",
				lastPrefetchKey = null
			)
		)
		assertEquals(
			false,
			key == nextLidaClipsPrefetchKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "different-secret",
				songId = "song-1",
				lastPrefetchKey = null
			)
		)
	}

	@Test
	fun lidaClipsPrefetchRequiresHttpOrHttpsBaseUrl() {
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		))
	}

	@Test
	fun lidaClipsPrefetchRequiresBaseUrlHostWithoutQueryOrFragment() {
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https:///api",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		))
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://?debug=true",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		))
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu?debug=true",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		))
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu#setup",
				userActionEnabled = true
			)
		)
	}

	@Test
	fun lidaClipsPrefetchAllowsSameKeyAfterFreshnessWindow() {
		val key = nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		)

		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = key,
			lastPrefetchTimeMillis = 1_000L,
			currentTimeMillis = 1_000L,
			refreshAfterMillis = 1_000L
		))
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = key,
			lastPrefetchTimeMillis = 1_000L,
			currentTimeMillis = 2_000L,
			refreshAfterMillis = 1_000L
		))
		assertEquals(
			key,
			nextLidaClipsPrefetchKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "secret",
				songId = "song-1",
				lastPrefetchKey = key,
				lastPrefetchTimeMillis = 1_000L,
				currentTimeMillis = 2_001L,
				refreshAfterMillis = 1_000L
			)
		)
	}

	@Test
	fun musicVideoActionDependsOnConfiguration() {
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = false,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = " ",
				userActionEnabled = true
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "clips.remaxku.eu",
				userActionEnabled = true
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = false
			)
		)
		assertEquals(
			true,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true
			)
		)
	}
}
