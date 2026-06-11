package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyAvailability
import paige.navic.domain.repositories.BinderyAvailabilityCombination
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyFindingFile
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.binderyCarouselCardWidthDp
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationKind
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BinderyCatalogRoutingPolicyTest {
	@Test
	fun genericBookPublicationsAndManifestsDoNotExposeDownloadAsPrimaryAction() {
		val download = BinderyLink(
			href = "/opds/books/3693/download",
			title = "Request download",
			rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
			type = "application/json"
		)

		assertEquals(
			null,
			BinderyPublication(
				id = "urn:bindery:book:3693",
				title = "Alcatraz",
				links = listOf(download)
			).primaryAction()
		)
		assertEquals(
			null,
			BinderyManifest(
				id = "urn:bindery:book:3693",
				title = "Alcatraz",
				links = listOf(download)
			).primaryAction()
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
	fun bookCardsOpenNativeBookDetailScreens() {
		assertEquals(
			Screen.BinderyBook(bookId = "3693", title = "Alcatraz versus the Evil Librarians"),
			binderyDestinationForBook(
				BinderyCatalogCard.Book(
					id = "urn:bindery:book:3693",
					title = "Alcatraz versus the Evil Librarians",
					subtitle = "Brandon Sanderson",
					imageUrl = "/opds/books/3693/cover"
				)
			)
		)
	}

	@Test
	fun genericBinderyCardsResolveNativeDestinationsForHubAndSearchRows() {
		assertEquals(
			Screen.BinderyBook(bookId = "3693", title = "Alcatraz versus the Evil Librarians"),
			binderyDestinationForCard(
				BinderyCatalogCard.Book(
					id = "/opds/books/3693",
					title = "Alcatraz versus the Evil Librarians",
					subtitle = "Brandon Sanderson",
					imageUrl = "/opds/books/3693/cover"
				)
			)
		)
		assertEquals(
			Screen.BinderyAuthor("/opds/authors/28", "Brandon Sanderson"),
			binderyDestinationForCard(
				BinderyCatalogCard.Link(
					id = "/opds/authors/28",
					title = "Brandon Sanderson",
					subtitle = "Author",
					path = "/opds/authors/28"
				)
			)
		)
	}

}
