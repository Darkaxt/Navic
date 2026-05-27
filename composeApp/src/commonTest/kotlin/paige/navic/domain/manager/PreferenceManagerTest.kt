package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferenceManagerTest {
	@Test
	fun serverRequestHeadersMapKeepsCustomHeadersWhenBasicAuthIsDisabled() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = """
			X-Proxy-User: training
			Authorization: Basic manual-token
		""".trimIndent()

		assertEquals(
			mapOf(
				"X-Proxy-User" to "training",
				"Authorization" to "Basic manual-token"
			),
			manager.serverRequestHeadersMap()
		)
	}

	@Test
	fun serverRequestHeadersMapAddsGeneratedBasicAuthWhenEnabled() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "X-Forwarded-Host: music.example.test"
		manager.reverseProxyBasicAuthEnabled = true
		manager.reverseProxyBasicAuthUsername = "traefik"
		manager.reverseProxyBasicAuthPassword = "secret"

		assertEquals(
			mapOf(
				"X-Forwarded-Host" to "music.example.test",
				"Authorization" to "Basic dHJhZWZpazpzZWNyZXQ="
			),
			manager.serverRequestHeadersMap()
		)
	}

	@Test
	fun generatedBasicAuthOverridesManualAuthorizationOnlyWhenEnabled() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "Authorization: Basic manual-token"
		manager.reverseProxyBasicAuthUsername = "traefik"
		manager.reverseProxyBasicAuthPassword = "secret"

		assertEquals("Basic manual-token", manager.serverRequestHeadersMap()["Authorization"])

		manager.reverseProxyBasicAuthEnabled = true

		assertEquals("Basic dHJhZWZpazpzZWNyZXQ=", manager.serverRequestHeadersMap()["Authorization"])
	}

	@Test
	fun generatedBasicAuthRemovesCaseInsensitiveManualAuthorization() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "authorization: Basic manual-token"
		manager.reverseProxyBasicAuthEnabled = true
		manager.reverseProxyBasicAuthUsername = "traefik"
		manager.reverseProxyBasicAuthPassword = "secret"

		assertEquals(
			mapOf("Authorization" to "Basic dHJhZWZpazpzZWNyZXQ="),
			manager.serverRequestHeadersMap()
		)
	}

	@Test
	fun generatedBasicAuthRequiresUsernameAndPassword() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "Authorization: Basic manual-token"
		manager.reverseProxyBasicAuthEnabled = true
		manager.reverseProxyBasicAuthUsername = "traefik"

		assertEquals("Basic manual-token", manager.serverRequestHeadersMap()["Authorization"])
	}

	@Test
	fun respectAudioFocusDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertTrue(manager.respectAudioFocus)
		manager.respectAudioFocus = false
		assertFalse(manager.respectAudioFocus)
	}

	@Test
	fun lidaClipsPreferencesDefaultToConfiguredServiceButDisabled() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.lidaClipsEnabled)
		assertEquals("", manager.lidaClipsBaseUrl)
		assertEquals(emptyMap(), manager.lidaClipsRequestHeadersMap())
		assertFalse(manager.lidaClipsPictureInPicture)
		assertFalse(manager.lidaClipsLandscapeVideoMode)
		assertTrue(manager.lidaClipsKeepScreenOn)
		manager.lidaClipsPictureInPicture = true
		manager.lidaClipsLandscapeVideoMode = true
		manager.lidaClipsKeepScreenOn = false
		assertTrue(manager.lidaClipsPictureInPicture)
		assertTrue(manager.lidaClipsLandscapeVideoMode)
		assertFalse(manager.lidaClipsKeepScreenOn)
	}

	@Test
	fun lidaClipsRequestHeadersMapIncludesTrimmedApiKey() {
		val manager = PreferenceManager(MapSettings())
		manager.lidaClipsApiKey = " secret "

		assertEquals(
			mapOf("X-Api-Key" to "secret"),
			manager.lidaClipsRequestHeadersMap()
		)
	}

	@Test
	fun kreateStylePlaybackTogglesDefaultToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.skipSilence)
		assertFalse(manager.skipMediaOnError)

		manager.skipSilence = true
		manager.skipMediaOnError = true

		assertTrue(manager.skipSilence)
		assertTrue(manager.skipMediaOnError)
	}

	@Test
	fun audioDeviceResumeDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.resumePlaybackOnAudioDeviceConnect)

		manager.resumePlaybackOnAudioDeviceConnect = true

		assertTrue(manager.resumePlaybackOnAudioDeviceConnect)
	}

	@Test
	fun volumeZeroPauseDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.pausePlaybackOnVolumeZero)

		manager.pausePlaybackOnVolumeZero = true

		assertTrue(manager.pausePlaybackOnVolumeZero)
	}

	@Test
	fun pauseBetweenSongsDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertEquals(0, manager.pauseBetweenSongsSeconds)

		manager.pauseBetweenSongsSeconds = 5

		assertEquals(5, manager.pauseBetweenSongsSeconds)
	}

	@Test
	fun smartRewindDefaultsToCurrentPreviousButtonBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertEquals(1, manager.smartRewindSeconds)

		manager.smartRewindSeconds = 3

		assertEquals(3, manager.smartRewindSeconds)
	}

	@Test
	fun pauseSearchHistoryDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.pauseSearchHistory)

		manager.pauseSearchHistory = true

		assertTrue(manager.pauseSearchHistory)
	}

	@Test
	fun nowPlayingActionVisibilityDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertTrue(manager.showNowPlayingLyricsAction)
		assertTrue(manager.showNowPlayingQueueAction)
		assertTrue(manager.showNowPlayingMusicVideoAction)

		manager.showNowPlayingLyricsAction = false
		manager.showNowPlayingQueueAction = false
		manager.showNowPlayingMusicVideoAction = false

		assertFalse(manager.showNowPlayingLyricsAction)
		assertFalse(manager.showNowPlayingQueueAction)
		assertFalse(manager.showNowPlayingMusicVideoAction)
	}

	@Test
	fun persistentQueueDefaultsToCurrentBehaviorWithStartupResumeDisabled() {
		val manager = PreferenceManager(MapSettings())

		assertTrue(manager.persistentQueue)
		assertFalse(manager.resumePlaybackOnStartup)

		manager.persistentQueue = false
		manager.resumePlaybackOnStartup = true

		assertFalse(manager.persistentQueue)
		assertTrue(manager.resumePlaybackOnStartup)
	}
}
