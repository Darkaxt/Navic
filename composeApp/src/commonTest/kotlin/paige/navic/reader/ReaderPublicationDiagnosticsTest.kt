package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPublicationDiagnosticsTest {
	@Test
	fun resourceLogLabelRedactsQueryAndFragment() {
		val label = readerPublicationResourceLogLabel(
			" /opds/books/42/resources/book.epub?apiKey=secret&download=1#chapter "
		)

		assertEquals("/opds/books/42/resources/book.epub", label)
		assertFalse(label.contains("secret"))
		assertFalse(label.contains("apiKey"))
	}

	@Test
	fun resourceLogLabelHandlesBlankValues() {
		assertEquals("<blank>", readerPublicationResourceLogLabel("  "))
	}

	@Test
	fun resourceLogLabelCompactsLongValues() {
		val label = readerPublicationResourceLogLabel("/opds/" + "a".repeat(240) + "/book.epub")

		assertTrue(label.length <= 163)
		assertTrue(label.startsWith("/opds/"))
		assertTrue(label.endsWith("/book.epub"))
		assertTrue(label.contains("..."))
	}
}
