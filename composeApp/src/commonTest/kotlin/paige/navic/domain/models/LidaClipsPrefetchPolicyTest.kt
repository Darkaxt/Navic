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

	@Test
	fun musicVideoActionIsHiddenOnlyForKnownMissingClips() {
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = false,
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				userActionEnabled = false,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			true,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Unknown
			)
		)
		assertEquals(
			true,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				userActionEnabled = true,
				clipAvailability = LidaClipAvailability.Unavailable
			)
		)
	}
}
