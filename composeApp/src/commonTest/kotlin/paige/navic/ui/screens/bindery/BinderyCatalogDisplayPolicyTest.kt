package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyPublication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinderyCatalogDisplayPolicyTest {
	@Test
	fun tabPathsUseCanonicalOpdsCatalogRoutes() {
		assertEquals("/opds/formats/audiobook", BinderyCatalogTab.Audiobooks.path)
		assertEquals("/opds/books", BinderyCatalogTab.Books.path)
		assertEquals("/opds/collections", BinderyCatalogTab.Collections.path)
		assertEquals("/opds/authors", BinderyCatalogTab.Authors.path)
	}

	@Test
	fun bookLikeCatalogTabsUseFiveItemInitialPages() {
		assertEquals("/opds/formats/audiobook?limit=5", BinderyCatalogTab.Audiobooks.initialCatalogPath())
		assertEquals("/opds/books?limit=5", BinderyCatalogTab.Books.initialCatalogPath())
		assertEquals("/opds/collections", BinderyCatalogTab.Collections.initialCatalogPath())
		assertEquals("/opds/authors", BinderyCatalogTab.Authors.initialCatalogPath())
	}

	@Test
	fun rawBookLikeCatalogPathsUseFiveItemInitialPagesUnlessAlreadyLimited() {
		assertEquals("/opds/books?limit=5", binderyInitialCatalogPath("/opds/books"))
		assertEquals("/opds/books?sort=title&limit=5", binderyInitialCatalogPath("/opds/books?sort=title"))
		assertEquals("/opds/books?limit=20", binderyInitialCatalogPath("/opds/books?limit=20"))
		assertEquals("/opds/recent", binderyInitialCatalogPath("/opds/recent"))
	}

	@Test
	fun catalogNextPagePathUsesOpdsNextRelation() {
		val catalog = BinderyCatalog(
			title = "Books",
			links = listOf(
				BinderyLink(href = "/opds/books?limit=5", rel = listOf("self")),
				BinderyLink(href = "/opds/books?after=book-5&limit=5", rel = listOf("next"))
			)
		)

		assertEquals("/opds/books?after=book-5&limit=5", catalog.nextPagePath())
	}

	@Test
	fun catalogAppendPageKeepsCurrentTitleAndUsesNextPageLinks() {
		val firstPage = BinderyCatalog(
			title = "Books",
			links = listOf(BinderyLink(href = "/opds/books?after=book-5&limit=5", rel = listOf("next"))),
			publications = listOf(BinderyPublication(id = "book-1", title = "Book 1"))
		)
		val secondPage = BinderyCatalog(
			title = "Books Page 2",
			links = listOf(BinderyLink(href = "/opds/books?after=book-10&limit=5", rel = listOf("next"))),
			publications = listOf(BinderyPublication(id = "book-6", title = "Book 6"))
		)

		assertEquals(
			BinderyCatalog(
				title = "Books",
				links = secondPage.links,
				publications = listOf(
					BinderyPublication(id = "book-1", title = "Book 1"),
					BinderyPublication(id = "book-6", title = "Book 6")
				)
			),
			firstPage.appendCatalogPage(secondPage)
		)
	}

	@Test
	fun bookCardsUsePortraitCoverPolicy() {
		assertEquals(
			BinderyCatalogCardVisualPolicy(
				coverAspectRatio = 2f / 3f,
				imageContentScaleFit = true
			),
			binderyCatalogCardVisualPolicy(
				BinderyCatalogCard.Book(
					id = "book-1",
					title = "Dune",
					subtitle = "Frank Herbert",
					imageUrl = "/opds/books/book-1/cover.jpg"
				)
			)
		)
		assertEquals(
			BinderyCatalogCardVisualPolicy(),
			binderyCatalogCardVisualPolicy(
				BinderyCatalogCard.Link(
					id = "/opds/authors/frank-herbert",
					title = "Frank Herbert",
					subtitle = "Author",
					path = "/opds/authors/frank-herbert"
				)
			)
		)
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
				BinderyLink(
					href = "/opds/authors/frank-herbert",
					title = "Frank Herbert",
					images = listOf(BinderyLink(href = "/opds/authors/frank-herbert/cover"))
				),
				BinderyLink(href = "/opds/authors/ursula-le-guin", title = "Ursula K. Le Guin")
			)
		)

		assertEquals(
			listOf(
				BinderyCatalogCard.Link(
					id = "/opds/authors/frank-herbert",
					title = "Frank Herbert",
					subtitle = "Author",
					path = "/opds/authors/frank-herbert",
					imageUrl = "/opds/authors/frank-herbert/cover"
				),
				BinderyCatalogCard.Link(
					id = "/opds/authors/ursula-le-guin",
					title = "Ursula K. Le Guin",
					subtitle = "Author",
					path = "/opds/authors/ursula-le-guin",
					imageUrl = null
				)
			),
			binderyCatalogCards(catalog, BinderyCatalogTab.Authors)
		)
	}

	@Test
	fun collectionCardsUseOpdsImagesBeforePropertyFallbacks() {
		val catalog = BinderyCatalog(
			title = "Collections",
			navigation = listOf(
				BinderyLink(
					href = "/opds/collections/1",
					title = "The Stormlight Archive",
					images = listOf(BinderyLink(href = "/opds/collections/1/cover")),
					properties = mapOf("image" to "/opds/collections/1/property-cover")
				),
				BinderyLink(
					href = "/opds/collections/2",
					title = "Mistborn",
					properties = mapOf("cover" to "/opds/collections/2/property-cover")
				)
			)
		)

		assertEquals(
			listOf(
				BinderyCatalogCard.Link(
					id = "/opds/collections/1",
					title = "The Stormlight Archive",
					subtitle = "Collection",
					path = "/opds/collections/1",
					imageUrl = "/opds/collections/1/cover"
				),
				BinderyCatalogCard.Link(
					id = "/opds/collections/2",
					title = "Mistborn",
					subtitle = "Collection",
					path = "/opds/collections/2",
					imageUrl = "/opds/collections/2/property-cover"
				)
			),
			binderyCatalogCards(catalog, BinderyCatalogTab.Collections)
		)
	}

	@Test
	fun collectionLinksWithoutOpdsArtworkNeedDetailArtworkResolution() {
		assertTrue(
			BinderyCatalogCard.Link(
				id = "/opds/collections/1",
				title = "The Stormlight Archive",
				subtitle = "Collection",
				path = "/opds/collections/1"
			).needsDetailArtworkResolution()
		)
		assertFalse(
			BinderyCatalogCard.Link(
				id = "/opds/collections/1",
				title = "The Stormlight Archive",
				subtitle = "Collection",
				path = "/opds/collections/1",
				imageUrl = "/opds/collections/1/cover"
			).needsDetailArtworkResolution()
		)
		assertFalse(
			BinderyCatalogCard.Link(
				id = "/opds/authors/frank-herbert",
				title = "Frank Herbert",
				subtitle = "Author",
				path = "/opds/authors/frank-herbert"
			).needsDetailArtworkResolution()
		)
	}

	@Test
	fun collectionDetailArtworkUsesFirstPublicationCoverAsFallback() {
		val catalog = BinderyCatalog(
			title = "The Stormlight Archive",
			publications = listOf(
				BinderyPublication(id = "way-of-kings", title = "The Way of Kings"),
				BinderyPublication(
					id = "words-of-radiance",
					title = "Words of Radiance",
					images = listOf(BinderyLink(href = "/opds/books/words-of-radiance/cover"))
				)
			)
		)

		assertEquals("/opds/books/words-of-radiance/cover", catalog.firstPublicationImageHref())
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
				BinderyLink(href = "/opds/collections", title = "Collections"),
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
					path = "/opds/collections",
					title = "Collections"
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

	@Test
	fun uiFeedsLoadOnlyWhenBinderyIsEnabledAndConfigured() {
		assertTrue(
			shouldLoadBinderyUi(
				binderyEnabled = true,
				opdsBaseUrl = "https://bindery.example.com/opds",
				apiKey = "token"
			)
		)
		assertFalse(
			shouldLoadBinderyUi(
				binderyEnabled = false,
				opdsBaseUrl = "https://bindery.example.com/opds",
				apiKey = "token"
			)
		)
		assertFalse(
			shouldLoadBinderyUi(
				binderyEnabled = true,
				opdsBaseUrl = "",
				apiKey = "token"
			)
		)
		assertFalse(
			shouldLoadBinderyUi(
				binderyEnabled = true,
				opdsBaseUrl = "https://bindery.example.com/opds",
				apiKey = ""
			)
		)
	}
}
