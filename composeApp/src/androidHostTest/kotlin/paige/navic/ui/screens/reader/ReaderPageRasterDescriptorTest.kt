package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReaderPageRasterDescriptorTest {
	@Test
	fun descriptorBuildsCompleteProfileAwareKey() {
		val descriptor = readerPageRasterDescriptorOrThrow(
			"""{
				"publicationUrl":"file:///books/alcatraz.epub",
				"paginationFingerprint":"pagination-a",
				"layoutFingerprint":"layout-a",
				"decorationFingerprint":"decoration-a",
				"viewportWidth":1440,
				"viewportHeight":900,
				"pageCount":110,
				"spineIndex":6,
				"href":"OEBPS/Text/authorsforeword.xhtml",
				"chapterPageIndex":2,
				"chapterPageCount":8,
				"visualPageOrdinal":7
			}"""
		)

		val key = descriptor.key(ReaderPageBitmapQuality.Balanced)

		assertEquals(6, key.spineIndex)
		assertEquals(2, key.chapterPageIndex)
		assertEquals(7, key.visualPageOrdinal)
		assertEquals(1440, key.viewportWidth)
		assertEquals(900, key.viewportHeight)
		assertEquals("pagination-a", key.paginationHash)
		assertEquals("layout-a", key.layoutHash)
		assertEquals("decoration-a", key.decorationHash)
		assertEquals(ReaderPageBitmapQuality.Balanced, key.quality)
		assertEquals(8, descriptor.chapterPageCount)
		assertEquals(110, descriptor.pageCount)
	}

	@Test
	fun qualityAndDecorationChangesCannotReuseRasterIdentity() {
		val base = readerPageRasterDescriptorOrThrow(descriptorJson("decoration-a"))
		val decorated = readerPageRasterDescriptorOrThrow(descriptorJson("decoration-b"))

		assertNotEquals(
			base.key(ReaderPageBitmapQuality.Balanced).digest,
			base.key(ReaderPageBitmapQuality.High).digest
		)
		assertNotEquals(
			base.key(ReaderPageBitmapQuality.Balanced).digest,
			decorated.key(ReaderPageBitmapQuality.Balanced).digest
		)
	}

	private fun descriptorJson(decoration: String) = """{
		"publicationUrl":"file:///books/alcatraz.epub",
		"paginationFingerprint":"pagination-a",
		"layoutFingerprint":"layout-a",
		"decorationFingerprint":"$decoration",
		"viewportWidth":1440,
		"viewportHeight":900,
		"pageCount":110,
		"spineIndex":6,
		"href":"OEBPS/Text/authorsforeword.xhtml",
		"chapterPageIndex":2,
		"chapterPageCount":8,
		"visualPageOrdinal":7
	}"""
}
