package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReaderAnnotationStateTest {
	@Test
	fun addsSelectionHighlightAndDedupesByBookAndCfi() {
		val selection = ReaderBridgeEvent.SelectionChanged(
			text = "The highlighted sentence",
			cfi = "epubcfi(/6/8!/4/1:0)",
			href = "EPUB/Text/chapter-01.xhtml"
		)

		val added = ReaderAnnotationState().addSelectionHighlight(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			selection = selection,
			sectionTitle = "Chapter 1"
		)
		val duplicate = added.addSelectionHighlight(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			selection = selection,
			sectionTitle = "Chapter 1"
		)

		assertEquals(1, added.annotations.size)
		assertEquals(added, duplicate)
		assertEquals("The highlighted sentence", added.annotations.single().text)
		assertEquals(DefaultReaderHighlightColor, added.annotations.single().color)
	}

	@Test
	fun annotationJsonRoundTripKeepsCfiTextAndLabels() {
		val state = ReaderAnnotationState().addSelectionHighlight(
			bookId = "book-1",
			bookTitle = "Storyteller Book",
			selection = ReaderBridgeEvent.SelectionChanged(
				text = "A line worth saving",
				cfi = "epubcfi(/6/10!/4/1:0)",
				href = "EPUB/Text/chapter-02.xhtml"
			),
			sectionTitle = "Chapter 2"
		)

		val decoded = ReaderAnnotationState(decodeReaderAnnotations(encodeReaderAnnotations(state.annotations)))

		assertEquals(state, decoded)
		assertEquals(emptyList(), decodeReaderAnnotations("not-json"))
	}

	@Test
	fun noteAnnotationJsonRoundTripKeepsReaderNoteForMarkerAndPopup() {
		val state = ReaderAnnotationState().addSelectionNote(
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

		val decoded = ReaderAnnotationState(decodeReaderAnnotations(encodeReaderAnnotations(state.annotations)))
		val annotation = assertNotNull(decoded.annotations.singleOrNull())

		assertEquals(state, decoded)
		assertEquals("Remember this scene later", annotation.note)
		assertEquals("A sentence with a note", annotation.text)
		assertEquals("Chapter 3", annotation.sectionTitle)
	}
}
