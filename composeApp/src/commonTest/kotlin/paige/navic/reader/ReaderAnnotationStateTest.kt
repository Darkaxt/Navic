package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
