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

	@Test
	fun hubRowsUseOnlyAdvertisedRootCatalogRoutes() {
		val root = BinderyCatalog(
			title = "Bindery",
			navigation = listOf(
				BinderyLink(href = "/opds/books", title = "Books"),
				BinderyLink(href = "/opds/recent", title = "Recently Added"),
				BinderyLink(href = "/opds/wanted", title = "Wanted"),
				BinderyLink(href = "/opds/authors", title = "Authors"),
				BinderyLink(href = "/opds/series", title = "Series"),
				BinderyLink(href = "/opds/formats/audiobook", title = "Audiobooks")
			)
		)

		assertEquals(
			listOf(
				BinderyHubRow(
					kind = BinderyHubRowKind.RecentlyAdded,
					path = "/opds/recent",
					title = "Recently Added"
				),
				BinderyHubRow(
					kind = BinderyHubRowKind.Audiobooks,
					path = "/opds/formats/audiobook",
					title = "Audiobooks"
				),
				BinderyHubRow(
					kind = BinderyHubRowKind.Authors,
					path = "/opds/authors",
					title = "Authors"
				),
				BinderyHubRow(
					kind = BinderyHubRowKind.Collections,
					path = "/opds/series",
					title = "Series"
				),
				BinderyHubRow(
					kind = BinderyHubRowKind.Wanted,
					path = "/opds/wanted",
					title = "Wanted"
				)
			),
			binderyHubRows(root)
		)
	}

	@Test
	fun hubRowsCanAdoptFuturePopularGenresAndLastReadRoutes() {
		val root = BinderyCatalog(
			title = "Bindery",
			navigation = listOf(
				BinderyLink(href = "/opds/continue", title = "Last Read"),
				BinderyLink(href = "/opds/popular", title = "Most Popular"),
				BinderyLink(href = "/opds/genres", title = "Genres")
			)
		)

		assertEquals(
			listOf(
				BinderyHubRow(
					kind = BinderyHubRowKind.LastRead,
					path = "/opds/continue",
					title = "Last Read"
				),
				BinderyHubRow(
					kind = BinderyHubRowKind.MostPopular,
					path = "/opds/popular",
					title = "Most Popular"
				),
				BinderyHubRow(
					kind = BinderyHubRowKind.Genres,
					path = "/opds/genres",
					title = "Genres"
				)
			),
			binderyHubRows(root)
		)
	}
}
