package paige.navic.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationKind

class NavigationBackPolicyTest {
	@Test
	fun rootBinderyBookBackFallsBackToBooksInsteadOfEmptyingTheAppStack() {
		assertEquals(
			Screen.BinderyBooks,
			navicRootBackDestinationFor(Screen.BinderyBook(bookId = "3809", title = "Bastille"))
		)
	}

	@Test
	fun rootReaderBackFallsBackToItsBookDetailInsteadOfAndroidHome() {
		assertEquals(
			Screen.BinderyBook(bookId = "3809", title = "Bastille"),
			navicRootBackDestinationFor(
				Screen.Reader(
					title = "Bastille",
					publicationUrl = "https://bindery.local/api/v1/book/3809/file?bookFileId=426",
					bookId = "3809",
					resourceHref = "/api/v1/book/3809/file?bookFileId=426",
					kind = ReaderPublicationKind.Ebook,
					publicationFormat = ReaderPublicationFormat.Epub
				)
			)
		)
	}

	@Test
	fun rootCollectionAndAuthorBackFallsBackToTheirCatalogTabs() {
		assertEquals(
			Screen.BinderyCollections,
			navicRootBackDestinationFor(Screen.BinderyCollection(path = "/collections/folio", title = "Folio"))
		)
		assertEquals(
			Screen.BinderyAuthors,
			navicRootBackDestinationFor(Screen.BinderyAuthor(path = "/authors/tolkien", title = "Tolkien"))
		)
	}

	@Test
	fun rootTabBackHasNoSyntheticFallback() {
		assertNull(navicRootBackDestinationFor(Screen.Library()))
		assertNull(navicRootBackDestinationFor(Screen.Audiobooks))
		assertNull(navicRootBackDestinationFor(Screen.BinderyBooks))
	}
}
