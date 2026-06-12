package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EbookReaderSettingsPolicyTest {
	@Test
	fun appLevelEbookSettingsExposePdfImageDefaults() {
		val ids = ebookReaderSettingDescriptors().map { descriptor -> descriptor.id }

		assertTrue("ebooks.pdf-fit" in ids)
		assertTrue("ebooks.pdf-crop-borders" in ids)
		assertTrue("ebooks.pdf-page-gap" in ids)
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
