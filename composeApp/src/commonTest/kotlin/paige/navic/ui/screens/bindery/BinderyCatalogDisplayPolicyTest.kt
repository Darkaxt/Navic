package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyPublication
import kotlin.test.Test
import kotlin.test.assertEquals

class BinderyCatalogDisplayPolicyTest {
	@Test
	fun tabPathsUseCanonicalOpdsCatalogRoutes() {
		assertEquals("/opds/formats/audiobook", BinderyCatalogTab.Audiobooks.path)
		assertEquals("/opds/books", BinderyCatalogTab.Books.path)
		assertEquals("/opds/series", BinderyCatalogTab.Collections.path)
		assertEquals("/opds/authors", BinderyCatalogTab.Authors.path)
	}

	@Test
	fun booksUsePublicationsAsCards() {
		val catalog = BinderyCatalog(
			title = "Audiobooks",
			publications = listOf(
				BinderyPublication(
					id = "book-1",
					title = "Dune",
					author = "Frank Herbert",
					images = listOf(BinderyLink(href = "/opds/books/book-1/cover.jpg"))
				)
			),
			navigation = listOf(BinderyLink(href = "/opds/authors/frank-herbert", title = "Frank Herbert"))
		)

		assertEquals(
			listOf(
				BinderyCatalogCard.Book(
					id = "book-1",
					title = "Dune",
					subtitle = "Frank Herbert",
					imageUrl = "/opds/books/book-1/cover.jpg"
				)
			),
			binderyCatalogCards(catalog, BinderyCatalogTab.Books)
		)
	}

	@Test
	fun collectionsAndAuthorsUseNavigationEntriesAsCards() {
		val catalog = BinderyCatalog(
			title = "Authors",
			navigation = listOf(
				BinderyLink(href = "/opds/authors/frank-herbert", title = "Frank Herbert"),
				BinderyLink(href = "/opds/authors/ursula-le-guin", title = "Ursula K. Le Guin")
			)
		)

		assertEquals(
			listOf(
				BinderyCatalogCard.Link(
					id = "/opds/authors/frank-herbert",
					title = "Frank Herbert",
					subtitle = "Author",
					path = "/opds/authors/frank-herbert"
				),
				BinderyCatalogCard.Link(
					id = "/opds/authors/ursula-le-guin",
					title = "Ursula K. Le Guin",
					subtitle = "Author",
					path = "/opds/authors/ursula-le-guin"
				)
			),
			binderyCatalogCards(catalog, BinderyCatalogTab.Authors)
		)
	}
}
