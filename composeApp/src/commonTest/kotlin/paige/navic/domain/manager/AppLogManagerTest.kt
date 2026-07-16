package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import paige.navic.domain.models.PlaybackDiagnosticsLogTag
import paige.navic.util.core.AppLogLevel
import paige.navic.util.core.LoggerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLogManagerTest {
	@Test
	fun issueLoggingIsDisabledByDefaultAndDropsEntries() {
		val preferences = PreferenceManager(MapSettings())
		val manager = AppLogManager(
			preferenceManager = preferences,
			clockMillis = { 1000L }
		)

		manager.record(LoggerEvent(AppLogLevel.Warning, "MediaPlayer", "Playback error", null))

		assertEquals(false, preferences.issueLoggingEnabled)
		assertEquals(emptyList(), manager.entries.value)
		assertEquals("", preferences.issueLogJson)
	}

	@Test
	fun playbackDiagnosticsPersistWhenGeneralIssueLoggingIsDisabled() {
		val preferences = PreferenceManager(MapSettings())
		val manager = AppLogManager(
			preferenceManager = preferences,
			clockMillis = { 1000L }
		)

		manager.record(
			LoggerEvent(
				AppLogLevel.Info,
				PlaybackDiagnosticsLogTag,
				"recovery-pending songId=42",
				null
			)
		)

		assertEquals(listOf(PlaybackDiagnosticsLogTag), manager.entries.value.map { it.tag })
		assertTrue(preferences.issueLogJson.contains("recovery-pending"))
	}

	@Test
	fun enabledIssueLoggingPersistsStructuredRingEntries() {
		val preferences = PreferenceManager(MapSettings()).apply {
			issueLoggingEnabled = true
		}
		var now = 1000L
		val manager = AppLogManager(
			preferenceManager = preferences,
			clockMillis = {
				now += 250L
				now
			},
			maxEntries = 2
		)

		manager.record(LoggerEvent(AppLogLevel.Info, "SyncManager", "Sync started", null))
		manager.record(LoggerEvent(AppLogLevel.Warning, "MediaPlayer", "Playback error", IllegalStateException("decoder failed")))
		manager.record(LoggerEvent(AppLogLevel.Error, "ReadaloudPlayback", "Playback failed", null))

		assertEquals(
			listOf("MediaPlayer", "ReadaloudPlayback"),
			manager.entries.value.map { it.tag }
		)
		assertEquals(
			listOf(AppLogLevel.Warning, AppLogLevel.Error),
			manager.entries.value.map { it.level }
		)
		assertTrue(preferences.issueLogJson.contains("ReadaloudPlayback"))

		val restored = AppLogManager(
			preferenceManager = preferences,
			clockMillis = {
				now += 250L
				now
			},
			maxEntries = 2
		)

		assertEquals(manager.entries.value, restored.entries.value)
		assertTrue(restored.exportText().contains("W/MediaPlayer: Playback error"))
		assertTrue(restored.exportText().contains("IllegalStateException: decoder failed"))
	}

	@Test
	fun disablingIssueLoggingClearsPersistedEntries() {
		val preferences = PreferenceManager(MapSettings()).apply {
			issueLoggingEnabled = true
		}
		val manager = AppLogManager(
			preferenceManager = preferences,
			clockMillis = { 1000L }
		)
		manager.record(LoggerEvent(AppLogLevel.Info, "DownloadManager", "Queued download", null))

		manager.setEnabled(false)

		assertEquals(false, preferences.issueLoggingEnabled)
		assertEquals(emptyList(), manager.entries.value)
		assertEquals("", preferences.issueLogJson)
	}

	@Test
	fun disablingGeneralLoggingRetainsOnlyPlaybackDiagnostics() {
		val preferences = PreferenceManager(MapSettings()).apply {
			issueLoggingEnabled = true
		}
		val manager = AppLogManager(
			preferenceManager = preferences,
			clockMillis = { 1000L }
		)
		manager.record(LoggerEvent(AppLogLevel.Info, "DownloadManager", "Queued download", null))
		manager.record(
			LoggerEvent(
				AppLogLevel.Info,
				PlaybackDiagnosticsLogTag,
				"queue-selection origin=NowPlayingArtworkSwipe",
				null
			)
		)

		manager.setEnabled(false)

		assertEquals(false, preferences.issueLoggingEnabled)
		assertEquals(listOf(PlaybackDiagnosticsLogTag), manager.entries.value.map { it.tag })
		assertTrue(preferences.issueLogJson.contains("queue-selection"))

		manager.clear()

		assertEquals(emptyList(), manager.entries.value)
		assertEquals("", preferences.issueLogJson)
	}
}
