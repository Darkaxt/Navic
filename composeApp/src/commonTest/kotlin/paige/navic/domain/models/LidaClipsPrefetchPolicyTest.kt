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
	fun lidaClipsPrefetchAllowsSameKeyAfterFreshnessWindow() {
		val key = "https://clips.remaxku.eu|secret|song-1"

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
	fun musicVideoActionIsHiddenOnlyForKnownMissingClips() {
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = false,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = " ",
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "clips.remaxku.eu",
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = false,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			true,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Unknown
			)
		)
		assertEquals(
			true,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Unavailable
			)
		)
	}
}
