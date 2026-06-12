package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderImportedFontTest {
	@Test
	fun importedFontMetadataNormalizesFamilyAndExtension() {
		assertEquals("Storyteller Serif", readerImportedFontFamilyFromDisplayName("Storyteller Serif!!.ttf"))
		assertEquals("Imported Font", readerImportedFontFamilyFromDisplayName("!!.woff2"))
		assertEquals("woff2", readerImportedFontExtension("Storyteller.WOFF2", null))
		assertEquals("ttf", readerImportedFontExtension("download", "font/ttf"))
		assertEquals("otf", readerImportedFontExtension(null, "font/otf"))
		assertEquals("woff", readerImportedFontExtension(null, "application/font-woff"))
	}

	@Test
	fun importedFontMetadataRejectsUnsupportedFiles() {
		assertNull(readerImportedFontExtension("book.epub", "application/epub+zip"))
		assertNull(readerImportedFontExtension("cover.png", "image/png"))
		assertNull(readerImportedFontExtension("font.exe", "application/octet-stream"))
	}
}
