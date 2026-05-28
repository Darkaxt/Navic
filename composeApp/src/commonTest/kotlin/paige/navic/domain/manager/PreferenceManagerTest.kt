package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import paige.navic.domain.models.settings.AudioReverbPreset
import paige.navic.domain.models.settings.AutoFillQueueSource
import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import paige.navic.domain.models.settings.QueueSwipeAction
import paige.navic.domain.models.settings.SongSwipeAction
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
		assertEquals(LidaClipsVideoFitMode.Fit, manager.lidaClipsVideoFitMode)
		manager.lidaClipsPictureInPicture = true
		manager.lidaClipsLandscapeVideoMode = true
		manager.lidaClipsKeepScreenOn = false
		manager.lidaClipsVideoFitMode = LidaClipsVideoFitMode.Crop
		assertTrue(manager.lidaClipsPictureInPicture)
		assertTrue(manager.lidaClipsLandscapeVideoMode)
		assertFalse(manager.lidaClipsKeepScreenOn)
		assertEquals(LidaClipsVideoFitMode.Crop, manager.lidaClipsVideoFitMode)
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
	fun audioFadeDefaultsToCurrentImmediatePauseResumeBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertEquals(0, manager.audioFadeDurationMs)

		manager.audioFadeDurationMs = 500

		assertEquals(500, manager.audioFadeDurationMs)
	}

	@Test
	fun bassBoostDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.bassBoostEnabled)
		assertEquals(500, manager.bassBoostStrength)

		manager.bassBoostEnabled = true
		manager.bassBoostStrength = 800

		assertTrue(manager.bassBoostEnabled)
		assertEquals(800, manager.bassBoostStrength)
	}

	@Test
	fun audioReverbDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertEquals(AudioReverbPreset.Off, manager.audioReverbPreset)

		manager.audioReverbPreset = AudioReverbPreset.MediumHall

		assertEquals(AudioReverbPreset.MediumHall, manager.audioReverbPreset)
	}

	@Test
	fun replayGainLoudnessBoostDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.replayGainLoudnessBoost)

		manager.replayGainLoudnessBoost = true

		assertTrue(manager.replayGainLoudnessBoost)
	}

	@Test
	fun pauseBetweenSongsDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertEquals(0, manager.pauseBetweenSongsSeconds)

		manager.pauseBetweenSongsSeconds = 5

		assertEquals(5, manager.pauseBetweenSongsSeconds)
	}

	@Test
	fun queueAutoFillDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.autoFillQueue)
		assertEquals(25, manager.autoFillQueueTargetSize)
		assertEquals(AutoFillQueueSource.RandomLibrary, manager.autoFillQueueSource)

		manager.autoFillQueue = true
		manager.autoFillQueueTargetSize = 50
		manager.autoFillQueueSource = AutoFillQueueSource.SimilarToCurrentSong

		assertTrue(manager.autoFillQueue)
		assertEquals(50, manager.autoFillQueueTargetSize)
		assertEquals(AutoFillQueueSource.SimilarToCurrentSong, manager.autoFillQueueSource)
	}

	@Test
	fun shakeToSkipDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.shakeToSkip)

		manager.shakeToSkip = true

		assertTrue(manager.shakeToSkip)
	}

	@Test
	fun volumeKeysSkipTracksDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.volumeKeysSkipTracks)

		manager.volumeKeysSkipTracks = true

		assertTrue(manager.volumeKeysSkipTracks)
	}

	@Test
	fun smartRewindDefaultsToCurrentPreviousButtonBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertEquals(1, manager.smartRewindSeconds)

		manager.smartRewindSeconds = 3

		assertEquals(3, manager.smartRewindSeconds)
	}

	@Test
	fun songSwipeActionsDefaultToCurrentGestures() {
		val manager = PreferenceManager(MapSettings())

		assertTrue(manager.songSwipeActionsEnabled)
		assertEquals(SongSwipeAction.AddToQueue, manager.songSwipeStartToEndAction)
		assertEquals(SongSwipeAction.PlayNext, manager.songSwipeEndToStartAction)

		manager.songSwipeActionsEnabled = false
		manager.songSwipeStartToEndAction = SongSwipeAction.Disabled
		manager.songSwipeEndToStartAction = SongSwipeAction.AddToQueue

		assertFalse(manager.songSwipeActionsEnabled)
		assertEquals(SongSwipeAction.Disabled, manager.songSwipeStartToEndAction)
		assertEquals(SongSwipeAction.AddToQueue, manager.songSwipeEndToStartAction)
	}

	@Test
	fun queueSwipeActionsDefaultToCurrentRemoveGestures() {
		val manager = PreferenceManager(MapSettings())

		assertTrue(manager.queueSwipeActionsEnabled)
		assertEquals(QueueSwipeAction.RemoveFromQueue, manager.queueSwipeStartToEndAction)
		assertEquals(QueueSwipeAction.RemoveFromQueue, manager.queueSwipeEndToStartAction)

		manager.queueSwipeActionsEnabled = false
		manager.queueSwipeStartToEndAction = QueueSwipeAction.Disabled
		manager.queueSwipeEndToStartAction = QueueSwipeAction.PlayNext

		assertFalse(manager.queueSwipeActionsEnabled)
		assertEquals(QueueSwipeAction.Disabled, manager.queueSwipeStartToEndAction)
		assertEquals(QueueSwipeAction.PlayNext, manager.queueSwipeEndToStartAction)
	}

	@Test
	fun pauseSearchHistoryDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.pauseSearchHistory)
		assertEquals("", manager.searchHistoryEntries)

		manager.pauseSearchHistory = true
		manager.searchHistoryEntries = "artist"

		assertTrue(manager.pauseSearchHistory)
		assertEquals("artist", manager.searchHistoryEntries)
	}

	@Test
	fun nowPlayingActionVisibilityDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertTrue(manager.showNowPlayingLyricsAction)
		assertTrue(manager.showNowPlayingQueueAction)
		assertTrue(manager.showNowPlayingMusicVideoAction)
		assertTrue(manager.showNowPlayingPlaybackSpeedAction)
		assertTrue(manager.showNowPlayingEqualizerAction)

		manager.showNowPlayingLyricsAction = false
		manager.showNowPlayingQueueAction = false
		manager.showNowPlayingMusicVideoAction = false
		manager.showNowPlayingPlaybackSpeedAction = false
		manager.showNowPlayingEqualizerAction = false

		assertFalse(manager.showNowPlayingLyricsAction)
		assertFalse(manager.showNowPlayingQueueAction)
		assertFalse(manager.showNowPlayingMusicVideoAction)
		assertFalse(manager.showNowPlayingPlaybackSpeedAction)
		assertFalse(manager.showNowPlayingEqualizerAction)
	}

	@Test
	fun tapArtworkForLyricsDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.tapArtworkForLyrics)

		manager.tapArtworkForLyrics = true

		assertTrue(manager.tapArtworkForLyrics)
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

	@Test
	fun autoDownloadStarredSongsDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertFalse(manager.autoDownloadStarredSongs)

		manager.autoDownloadStarredSongs = true

		assertTrue(manager.autoDownloadStarredSongs)
	}
}
