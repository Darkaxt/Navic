package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EbookReaderSettingsPolicyTest {
	@Test
	fun readerThemeOptionsExposeAgedPaperWithoutReplacingSepia() {
		val themes = ReaderThemeOption.entries.map { option -> option.theme }

		assertTrue("aged-paper" in themes)
		assertTrue("sepia" in themes)
	}

	@Test
	fun appLevelEbookSettingsExposePdfImageDefaults() {
		val ids = ebookReaderSettingDescriptors().map { descriptor -> descriptor.id }

		assertTrue("ebooks.paper-texture" in ids)
		assertTrue("ebooks.page-edges" in ids)
		assertTrue("ebooks.paper-stains" in ids)
		assertTrue("ebooks.cover-backdrop" in ids)
		assertTrue("ebooks.pdf-fit" in ids)
		assertTrue("ebooks.pdf-crop-borders" in ids)
		assertTrue("ebooks.pdf-page-gap" in ids)
	}

	@Test
	fun paperSurfaceSettingsAreSearchableAsReaderAppearanceControls() {
		val rows = ebookReaderSettingDescriptors()
			.map { descriptor -> descriptor.toSearchEntry(path = "Settings > Ebooks") }

		assertEquals(
			listOf("ebooks.paper-texture", "ebooks.page-edges", "ebooks.paper-stains", "ebooks.cover-backdrop"),
			filteredSettingsSearchEntries(rows, "paper cover").map { entry -> entry.id }
		)
	}

	@Test
	fun pdfImageSettingsAreSearchableAsPdfAndImageControls() {
		val pdfRows = ebookReaderSettingDescriptors()
			.filter { descriptor -> descriptor.id.startsWith("ebooks.pdf-") }
			.map { descriptor -> descriptor.toSearchEntry(path = "Settings > Ebooks") }

		assertEquals(
			listOf("ebooks.pdf-fit", "ebooks.pdf-crop-borders", "ebooks.pdf-page-gap"),
			filteredSettingsSearchEntries(pdfRows, "pdf image").map { entry -> entry.id }
		)
	}
}
