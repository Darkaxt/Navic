package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.ui.navigation.Screen
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
			BinderyCatalogCardVisualPolicy(
				coverAspectRatio = 2f / 3f,
				imageContentScaleFit = true
			),
			binderyCatalogCardVisualPolicy(
				BinderyCatalogCard.Link(
					id = "/opds/collections/mistborn",
					title = "Mistborn",
					subtitle = "Collection",
					path = "/opds/collections/mistborn"
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
	fun detailPublicationsSortByPublishedYearWithUnknownDatesLast() {
		val newest = BinderyPublication(id = "new", title = "New", published = "2023-01-01")
		val unknown = BinderyPublication(id = "unknown", title = "Unknown")
		val oldest = BinderyPublication(id = "old", title = "Old", published = "1999-04-10")

		assertEquals(
			listOf(oldest, newest, unknown),
			listOf(newest, unknown, oldest).sortedForBinderyDetail()
		)
	}

	@Test
	fun collectionDetailPublicationsPreferCollectionOrderMetadata() {
		val second = BinderyPublication(
			id = "second",
			title = "Second",
			published = "1999-01-01",
			properties = mapOf("collectionPositionSort" to "2")
		)
		val first = BinderyPublication(
			id = "first",
			title = "First",
			published = "2023-01-01",
			properties = mapOf("collectionPositionSort" to "1")
		)
		val unordered = BinderyPublication(
			id = "unordered",
			title = "Unordered",
			published = "2001-01-01"
		)

		assertEquals(
			listOf(first, second, unordered),
			listOf(second, unordered, first).sortedForBinderyCollectionDetail()
		)
	}

	@Test
	fun collectionSummaryHidesProviderAndDefaultSeriesNoise() {
		assertEquals(
			"4 books / 2009-2022",
			binderyCollectionSummaryText(
				mapOf(
					"collectionType" to "series",
					"memberCount" to "4",
					"startYear" to "2009",
					"endYear" to "2022",
					"sourceProvider" to "hardcover"
				)
			)
		)
	}

	@Test
	fun collectionSummaryKeepsNonSeriesCollectionTypes() {
		assertEquals(
			"Anthology / 2 books",
			binderyCollectionSummaryText(
				mapOf(
					"collectionType" to "anthology",
					"memberCount" to "2",
					"sourceProvider" to "hardcover"
				)
			)
		)
	}

	@Test
	fun collectionDetailHeroUsesDominantPortraitCoverSize() {
		assertEquals(180, binderyDetailCoverWidthDp(BinderyDetailKind.Collection))
		assertEquals(120, binderyDetailCoverWidthDp(BinderyDetailKind.Author))
	}

	@Test
	fun binderyAuthorAndCollectionLinksOpenDedicatedDetailScreens() {
		assertEquals(
			Screen.BinderyAuthor("/opds/authors/28", "Brandon Sanderson"),
			binderyDestinationForLink(
				BinderyCatalogCard.Link(
					id = "/opds/authors/28",
					title = "Brandon Sanderson",
					subtitle = "Author",
					path = "/opds/authors/28"
				)
			)
		)
		assertEquals(
			Screen.BinderyCollection("/opds/collections/9", "Mistborn"),
			binderyDestinationForLink(
				BinderyCatalogCard.Link(
					id = "/opds/collections/9",
					title = "Mistborn",
					subtitle = "Collection",
					path = "/opds/collections/9"
				)
			)
		)
		assertEquals(
			Screen.BinderyCatalog("/opds/recent", "Recently Added"),
			binderyDestinationForLink(
				BinderyCatalogCard.Link(
					id = "/opds/recent",
					title = "Recently Added",
					subtitle = "Catalog",
					path = "/opds/recent"
				)
			)
		)
	}

	@Test
	fun authorDetailCatalogExposesAdvertisedCollectionsLink() {
		val catalog = BinderyCatalog(
			title = "Brandon Sanderson",
			navigation = listOf(
				BinderyLink(
					href = "/opds/authors/28/collections",
					title = "Collections"
				)
			)
		)

		assertEquals(
			BinderyCatalogCard.Link(
				id = "/opds/authors/28/collections",
				title = "Collections",
				subtitle = "Collection",
				path = "/opds/authors/28/collections"
			),
			catalog.authorCollectionsLink()
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
					imageUrl = "/opds/collections/1/cover",
					properties = mapOf("image" to "/opds/collections/1/property-cover")
				),
				BinderyCatalogCard.Link(
					id = "/opds/collections/2",
					title = "Mistborn",
					subtitle = "Collection",
					path = "/opds/collections/2",
					imageUrl = "/opds/collections/2/property-cover",
					properties = mapOf("cover" to "/opds/collections/2/property-cover")
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
