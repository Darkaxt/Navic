package paige.navic.reader

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.manager.PreferenceManager

class ReaderListeningSettingsTest {
	@Test
	fun listeningSettingsNormalizePersistedValues() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerWhispersyncListeningEnabled = true
		preferences.readerWhispersyncPlaybackSpeed = 8f
		preferences.readerWhispersyncHighlightLeadMs = 9_000
		preferences.readerWhispersyncHighlightColorArgb = 0x6642A5F5
		preferences.readerWhispersyncHighlightLoading = "future"
		preferences.readerWhispersyncHighlightStyle = "neon"

		assertEquals(
			defaultReaderListeningSettings().copy(
				listeningEnabled = true,
				playbackSpeed = MaxReaderWhispersyncPlaybackSpeed,
				highlightLeadMs = MaxReaderWhispersyncHighlightLeadMs,
				highlightColorArgb = 0x6642A5F5,
				highlightLoading = ReaderWhispersyncHighlightLoading.CurrentCue,
				highlightStyle = ReaderWhispersyncHighlightStyle.Selection
			),
			preferences.readerListeningSettings()
		)
	}

	@Test
	fun listeningSettingsPersistGloballyWithoutChangingReaderBookSettings() {
		val preferences = PreferenceManager(MapSettings())

		preferences.setReaderBookSettings(
			bookId = "book-1",
			settings = ReaderSettings(whispersyncHighlightLeadMs = -250)
		)
		preferences.setReaderListeningSettings(
			defaultReaderListeningSettings().copy(
				highlightLeadMs = 1_250,
				highlightLoading = ReaderWhispersyncHighlightLoading.PersistentPlayedText,
				highlightStyle = ReaderWhispersyncHighlightStyle.Marker
			)
		)

		assertEquals(1_250, preferences.readerListeningSettings().highlightLeadMs)
		assertEquals(
			ReaderWhispersyncHighlightLoading.PersistentPlayedText,
			preferences.readerListeningSettings().highlightLoading
		)
		assertEquals(ReaderWhispersyncHighlightStyle.Marker, preferences.readerListeningSettings().highlightStyle)
		assertEquals(-250, preferences.readerBookSettings("book-1")?.whispersyncHighlightLeadMs)
	}
}
