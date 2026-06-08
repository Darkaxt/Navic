package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind

class ReaderProgressSyncTest {
	@Test
	fun binderyProgressBuildsReaderStartLocatorWithCfiPreferredOverHrefFragment() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			alias = "darko",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			textHref = "EPUB/Text/chapter-03.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			fragmentId = "p-42",
			progressFraction = 0.34
		)

		assertEquals(
			ReaderLocator(
				href = "EPUB/Text/chapter-03.xhtml#p-42",
				cfi = "epubcfi(/6/8!/4/1:0)",
				progress = 0.34
			),
			progress.toReaderStartLocator()
		)
	}

	@Test
	fun binderyProgressOnlyBuildsReaderStartLocatorForMatchingResourceAndKind() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progressFraction = 0.34
		)

		assertEquals(
			ReaderLocator(cfi = "epubcfi(/6/8!/4/1:0)", progress = 0.34),
			progress.toReaderStartLocatorFor(
				resourceHref = "/opds/books/3693/resources/ebook-1",
				kind = ReaderPublicationKind.Ebook
			)
		)
		assertEquals(
			null,
			progress.toReaderStartLocatorFor(
				resourceHref = "/opds/books/3693/resources/readaloud-1",
				kind = ReaderPublicationKind.Readaloud
			)
		)
	}

	@Test
	fun readerLocatorSavesEbookProgressAsCfiTextHrefAndFragment() {
		val progress = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml#note-9",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.62
		).toBinderyReadingProgress(
			bookId = "3693",
			resourceHref = "/opds/books/3693/resources/ebook-1",
			kind = ReaderPublicationKind.Ebook,
			alias = "darko"
		)

		assertEquals(
			BinderyReadingProgress(
				bookId = "3693",
				alias = "darko",
				kind = BinderyReadingProgressKind.Ebook,
				resourceHref = "/opds/books/3693/resources/ebook-1",
				textHref = "EPUB/Text/chapter-04.xhtml",
				cfi = "epubcfi(/6/10!/4/3:12)",
				fragmentId = "note-9",
				progressFraction = 0.62
			),
			progress
		)
	}

	@Test
	fun readerLocatorSavesReadaloudProgressAsReadaloudKindAndClampsFraction() {
		val progress = ReaderLocator(
			href = "chapter-01.xhtml",
			progress = 1.4
		).toBinderyReadingProgress(
			bookId = "3693",
			resourceHref = "/opds/books/3693/resources/readaloud-1",
			kind = ReaderPublicationKind.Readaloud,
			alias = null
		)

		assertEquals(
			BinderyReadingProgress(
				bookId = "3693",
				kind = BinderyReadingProgressKind.Readaloud,
				resourceHref = "/opds/books/3693/resources/readaloud-1",
				textHref = "chapter-01.xhtml",
				progressFraction = 1.0
			),
			progress
		)
	}
}
