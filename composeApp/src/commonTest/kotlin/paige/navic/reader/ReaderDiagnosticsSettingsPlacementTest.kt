package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderDiagnosticsSettingsPlacementTest {
	@Test
	fun tapZoneVisibilityIsDeveloperDiagnosticNotReaderSheetControl() {
		val readerSettingsDialog = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt").readText()
		val developerScreen = File("src/commonMain/kotlin/paige/navic/ui/screens/settings/DeveloperScreen.kt").readText()
		val searchDeveloperRows = File("src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchDeveloperRows.kt").readText()

		assertFalse(
			readerSettingsDialog.contains("Show tap zones") ||
				readerSettingsDialog.contains("showTapZones = settings.showTapZones"),
			"Tap-zone visibility is a diagnostics overlay. Keep it in Developer Options so the " +
				"Komikku reader settings sheet does not grow into a debug panel."
		)
		assertTrue(
			developerScreen.contains("readerShowTapZones") &&
				developerScreen.contains("option_ebook_reader_show_tap_zones"),
			"Developer Options must keep the tap-zone visibility switch."
		)
		assertTrue(
			searchDeveloperRows.contains("developer.show-tap-zones") &&
				searchDeveloperRows.contains("readerShowTapZones"),
			"Settings search must keep tap-zone visibility under Developer Options."
		)
	}
}
