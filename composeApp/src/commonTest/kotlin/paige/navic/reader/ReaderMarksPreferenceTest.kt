package paige.navic.reader

import com.russhwolf.settings.MapSettings
import paige.navic.domain.manager.PreferenceManager
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderMarksPreferenceTest {
	@Test
	fun readerMarksRoundTripThroughPreferences() {
		val preferences = PreferenceManager(MapSettings())
		val annotationState = ReaderAnnotationState().addSelectionNote(
			draft = ReaderSelectionNoteDraft(
				bookId = "book-1",
				bookTitle = "Storyteller Book",
				text = "A sentence with a note",
				cfi = "epubcfi(/6/12!/4/1:0)",
				href = "EPUB/Text/chapter-03.xhtml",
				sectionTitle = "Chapter 3"
			),
			note = "Remember this scene later"
		)
		val bookmarkState = ReaderBookmarkState().toggleBookmark(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			locator = ReaderLocator(
				href = "EPUB/Text/chapter-04.xhtml",
				cfi = "epubcfi(/6/14!/4/1:0)",
				progress = 0.53
			),
			sectionTitle = "Chapter 4"
		)

		preferences.setReaderAnnotationState(annotationState)
		preferences.setReaderBookmarkState(bookmarkState)

		assertEquals(annotationState, preferences.readerAnnotationState())
		assertEquals(bookmarkState, preferences.readerBookmarkState())
	}

	@Test
	fun readerMarksPersistOnlyWhenControllerStoreChanges() {
		val preferences = PreferenceManager(MapSettings())
		val original = ReaderControllerState(
			annotations = ReaderAnnotationState(),
			bookmarks = ReaderBookmarkState()
		)
		val nextAnnotationState = ReaderAnnotationState().addSelectionHighlight(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			selectionText = "A highlighted sentence",
			selectionCfi = "epubcfi(/6/8!/4/1:0)",
			selectionHref = "EPUB/Text/chapter-01.xhtml",
			sectionTitle = "Chapter 1"
		)
		val nextBookmarkState = ReaderBookmarkState().toggleBookmark(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			locator = ReaderLocator(
				href = "EPUB/Text/chapter-02.xhtml",
				cfi = "epubcfi(/6/10!/4/1:0)",
				progress = 0.42
			),
			sectionTitle = "Chapter 2"
		)
		val updated = original.copy(
			annotations = nextAnnotationState,
			bookmarks = nextBookmarkState
		)

		preferences.persistReaderMarksIfChanged(previous = original, next = updated)

		assertEquals(nextAnnotationState, preferences.readerAnnotationState())
		assertEquals(nextBookmarkState, preferences.readerBookmarkState())
	}
}
