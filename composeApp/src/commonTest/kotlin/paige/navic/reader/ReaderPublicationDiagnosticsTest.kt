package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReaderPublicationDiagnosticsTest {
	@Test
	fun resourceLogLabelClassifiesAbsoluteUrlsWithoutRetainingIdentifiers() {
		val forbiddenMarkers = listOf(
			"synthetic-reader.invalid",
			"SYNTHETIC_BOOK_ID",
			"SYNTHETIC_RESOURCE_ID",
			"SYNTHETIC_ACTION_ID"
		)
		val label = readerPublicationResourceLogLabel(
			"https://synthetic-reader.invalid/books/SYNTHETIC_BOOK_ID/" +
				"resources/SYNTHETIC_RESOURCE_ID?action=SYNTHETIC_ACTION_ID#chapter"
		)

		assertEquals("absolute-url", label)
		forbiddenMarkers.forEach { marker -> assertFalse(label.contains(marker)) }
	}

	@Test
	fun resourceLogLabelClassifiesRelativePathsWithoutRetainingIdentifiers() {
		val label = readerPublicationResourceLogLabel(
			"/books/SYNTHETIC_BOOK_ID/resources/SYNTHETIC_RESOURCE_ID"
		)

		assertEquals("relative-path", label)
		assertFalse(label.contains("SYNTHETIC_BOOK_ID"))
		assertFalse(label.contains("SYNTHETIC_RESOURCE_ID"))
	}

	@Test
	fun resourceLogLabelHandlesBlankValues() {
		assertEquals("<blank>", readerPublicationResourceLogLabel("  "))
	}

	@Test
	fun viewerActionLogValueDoesNotRetainLocatorIdentifiers() {
		val label = ReaderViewerAction.NavigateTo(
			ReaderLocator(href = "/books/SYNTHETIC_BOOK_ID/SYNTHETIC_RESOURCE_ID.xhtml")
		).toString()

		assertEquals("NavigateTo", label)
		assertFalse(label.contains("SYNTHETIC_BOOK_ID"))
		assertFalse(label.contains("SYNTHETIC_RESOURCE_ID"))
	}
}
