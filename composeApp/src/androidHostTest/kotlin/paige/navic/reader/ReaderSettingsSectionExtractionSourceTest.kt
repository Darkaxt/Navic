package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ReaderSettingsSectionExtractionSourceTest {
	@Test
	fun settingsDialogDelegatesStableTabsToSectionFiles() {
		val dialog = sourceFile("ReaderSettingsDialog.kt").readText()
		val modePages = sourceFile("ReaderSettingsModePages.kt").readText()
		val generalPage = sourceFile("ReaderSettingsGeneralPage.kt").readText()
		val visualPages = sourceFile("ReaderSettingsVisualPages.kt").readText()

		assertContains(dialog, "KomikkuReadingSettingsPage(")
		assertContains(dialog, "KomikkuListeningSettingsPage(")
		assertContains(dialog, "KomikkuGeneralSettingsPage(")
		assertContains(dialog, "KomikkuPdfImageSettingsPage(")
		assertContains(dialog, "KomikkuCustomFilterSettingsPage(")
		assertContains(modePages, "internal fun KomikkuReadingSettingsPage(")
		assertContains(modePages, "internal fun KomikkuListeningSettingsPage(")
		assertContains(generalPage, "internal fun KomikkuGeneralSettingsPage(")
		assertContains(visualPages, "internal fun KomikkuPdfImageSettingsPage(")
		assertContains(visualPages, "internal fun KomikkuCustomFilterSettingsPage(")
		assertTrue(dialog.lineSequence().count() < 850, "The dialog shell must not absorb section implementations again")
	}

	private fun sourceFile(name: String): File =
		listOf(
			File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/$name"),
			File("../composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/$name")
		).firstOrNull(File::isFile) ?: error("Unable to locate $name")
}
