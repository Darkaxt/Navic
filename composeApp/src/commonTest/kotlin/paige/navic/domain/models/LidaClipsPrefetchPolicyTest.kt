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
	fun lidaClipsPrefetchRejectsBaseUrlCredentials() {
		assertNull(nextLidaClipsPrefetchKey(
			enabled = true,
			baseUrl = "https://user:pass@clips.remaxku.eu",
			apiKey = "secret",
			songId = "song-1",
			lastPrefetchKey = null
		))
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://user:pass@clips.remaxku.eu",
				userActionEnabled = true
			)
		)
	}

	@Test
	fun lidaClipsPrefetchRequiresValidBaseUrlPort() {
		listOf(
			"https://clips.remaxku.eu:",
			"https://clips.remaxku.eu:bad",
			"https://clips.remaxku.eu:0",
			"https://clips.remaxku.eu:65536",
			"http://[::1]:bad"
		).forEach { baseUrl ->
			assertNull(nextLidaClipsPrefetchKey(
				enabled = true,
				baseUrl = baseUrl,
				apiKey = "secret",
				songId = "song-1",
				lastPrefetchKey = null
			))
		}

		assertEquals(
			true,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu:8443/lida",
				userActionEnabled = true
			)
		)
		assertEquals(
			true,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "http://[::1]:8080/lida",
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
				userActionEnabled = true,
				songId = "song-1"
			)
		)
		assertEquals(
			false,
			shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "radio_song-1"
			)
		)
	}

	@Test
	fun verifiedMusicVideoActionRequiresAvailableClip() {
		assertEquals(
			false,
			shouldShowVerifiedLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "song-1",
				clipAvailability = LidaClipAvailability.Unknown
			)
		)
		assertEquals(
			false,
			shouldShowVerifiedLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "song-1",
				clipAvailability = LidaClipAvailability.Unavailable
			)
		)
		assertEquals(
			true,
			shouldShowVerifiedLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "song-1",
				clipAvailability = LidaClipAvailability.Available
			)
		)
		assertEquals(
			false,
			shouldShowVerifiedLidaClipsMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "radio_song-1",
				clipAvailability = LidaClipAvailability.Available
			)
		)
	}

	@Test
	fun nowPlayingMusicVideoActionOpensClipScreenWhenConfiguredButNoClipResolved() {
		assertEquals(
			LidaClipsNowPlayingMusicVideoAction.OpenPlayer,
			lidaClipsNowPlayingMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "song-1",
				hasResolvedClip = false
			)
		)
		assertEquals(
			LidaClipsNowPlayingMusicVideoAction.ToggleArtworkClip,
			lidaClipsNowPlayingMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "song-1",
				hasResolvedClip = true
			)
		)
		assertEquals(
			null,
			lidaClipsNowPlayingMusicVideoAction(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				userActionEnabled = true,
				songId = "radio_song-1",
				hasResolvedClip = false
			)
		)
	}

	@Test
	fun offlineClipDownloadRequiresEnabledConfiguredIntegrationAndStableSongId() {
		assertEquals(
			false,
			shouldSaveLidaClipWithDownloadedMusic(
				lidaClipsEnabled = false,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				saveClipsWithDownloads = true,
				songId = "song-1"
			)
		)
		assertEquals(
			false,
			shouldSaveLidaClipWithDownloadedMusic(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "clips.remaxku.eu",
				saveClipsWithDownloads = true,
				songId = "song-1"
			)
		)
		assertEquals(
			false,
			shouldSaveLidaClipWithDownloadedMusic(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				saveClipsWithDownloads = false,
				songId = "song-1"
			)
		)
		assertEquals(
			false,
			shouldSaveLidaClipWithDownloadedMusic(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				saveClipsWithDownloads = true,
				songId = "radio_song-1"
			)
		)
		assertEquals(
			false,
			shouldSaveLidaClipWithDownloadedMusic(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				saveClipsWithDownloads = true,
				songId = "aurral_flow_1"
			)
		)
		assertEquals(
			true,
			shouldSaveLidaClipWithDownloadedMusic(
				lidaClipsEnabled = true,
				lidaClipsBaseUrl = "https://clips.remaxku.eu",
				saveClipsWithDownloads = true,
				songId = "song-1"
			)
		)
	}
}
