package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderBookmarkStateTest {
	@Test
	fun togglesCurrentLocatorBookmarkAndDedupesByBookAndCfi() {
		val locator = ReaderLocator(
			href = "EPUB/Text/chapter-01.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progress = 0.24
		)

		val added = ReaderBookmarkState().toggleBookmark(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			locator = locator,
			sectionTitle = "Chapter 1"
		)
		val removed = added.toggleBookmark(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			locator = locator,
			sectionTitle = "Chapter 1"
		)

		assertTrue(added.isBookmarked("book-1", locator))
		assertEquals(1, added.bookmarks.size)
		assertEquals("Chapter 1", added.bookmarks.single().sectionTitle)
		assertFalse(removed.isBookmarked("book-1", locator))
		assertEquals(emptyList(), removed.bookmarks)
	}

	@Test
	fun bookmarkJsonRoundTripKeepsBookLocatorAndSectionLabels() {
		val state = ReaderBookmarkState().toggleBookmark(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			locator = ReaderLocator(
				href = "EPUB/Text/chapter-02.xhtml",
				cfi = "epubcfi(/6/10!/4/1:0)",
				progress = 0.42
			),
			sectionTitle = "Chapter 2"
		)

		val decoded = ReaderBookmarkState(decodeReaderBookmarks(encodeReaderBookmarks(state.bookmarks)))

		assertEquals(state, decoded)
		assertEquals(emptyList(), decodeReaderBookmarks("not-json"))
	}
}
