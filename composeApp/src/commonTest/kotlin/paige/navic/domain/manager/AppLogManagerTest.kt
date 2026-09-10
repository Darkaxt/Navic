package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import paige.navic.domain.models.PlaybackDiagnosticsLogTag
import paige.navic.util.core.AppLogLevel
import paige.navic.util.core.LoggerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
	fun enabledIssueLoggingPersistsStructuredRingEntriesWithoutThrowableContent() {
		val preferences = PreferenceManager(MapSettings()).apply {
			issueLoggingEnabled = true
		}
		val throwableMarker = "SYNTHETIC_THROWABLE_MARKER"
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
		manager.record(
			LoggerEvent(
				AppLogLevel.Warning,
				"MediaPlayer",
				"Playback error",
				IllegalStateException(throwableMarker)
			)
		)
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
		assertNull(manager.entries.value.first().throwable)
		assertFalse(preferences.issueLogJson.contains(throwableMarker))
		assertFalse(manager.exportText().contains(throwableMarker))

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
		assertFalse(restored.exportText().contains(throwableMarker))
	}

	@Test
	fun currentIssueLogDropsThrowableDiagnosticFieldImmediately() {
		val messageMarker = "SYNTHETIC_CURRENT_THROWABLE_CLASS_MARKER"
		val preferences = PreferenceManager(MapSettings()).apply {
			issueLoggingEnabled = true
		}
		val manager = AppLogManager(
			preferenceManager = preferences,
			clockMillis = { 1000L }
		)

		manager.record(
			LoggerEvent(
				level = AppLogLevel.Error,
				tag = "Reader",
				message = "  reader-render failed failureClass=$messageMarker  "
			)
		)

		assertEquals(AppLogLevel.Error, manager.entries.value.single().level)
		assertEquals("  reader-render failed  ", manager.entries.value.single().message)
		assertFalse(manager.entries.value.single().message.contains(messageMarker))
		assertFalse(manager.exportText().contains(messageMarker))
		assertFalse(preferences.issueLogJson.contains(messageMarker))
	}

	@Test
	fun restoredIssueLogDropsLegacyThrowableContent() {
		val throwableMarker = "SYNTHETIC_THROWABLE_MARKER"
		val messageMarker = "SYNTHETIC_THROWABLE_CLASS_MARKER"
		val preferences = PreferenceManager(MapSettings()).apply {
			issueLogJson =
				"""{"nextId":3,"entries":[{"id":1,"timestampMillis":1000,"level":"Warning","tag":"Reader","message":"reader-event state=failed","throwable":"$throwableMarker"},{"id":2,"timestampMillis":1001,"level":"Error","tag":"Reader","message":"reader-render failed failureClass=$messageMarker","throwable":null}]}"""
		}

		val manager = AppLogManager(
			preferenceManager = preferences,
			clockMillis = { 2000L }
		)

		assertTrue(manager.entries.value.all { it.throwable == null })
		assertEquals(listOf(AppLogLevel.Warning, AppLogLevel.Error), manager.entries.value.map { it.level })
		assertEquals(
			listOf("reader-event state=failed", "reader-render failed"),
			manager.entries.value.map { it.message }
		)
		assertFalse(manager.exportText().contains(throwableMarker))
		assertFalse(manager.exportText().contains(messageMarker))
		assertFalse(preferences.issueLogJson.contains(throwableMarker))
		assertFalse(preferences.issueLogJson.contains(messageMarker))
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
