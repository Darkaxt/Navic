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

class BinderyAuthorCollectionPolicyTest {
	@Test
	fun authorCardsPreserveNestedOpdsUnmonitorActionLinks() {
		val unmonitor = BinderyLink(
			href = "/opds/authors/28/unmonitor",
			title = "Unmonitor author",
			rel = listOf(BINDERY_UNMONITOR_REL),
			type = "application/json"
		)
		val authorCard = binderyCatalogCards(
			BinderyCatalog(
				title = "Authors",
				navigation = listOf(
					BinderyLink(
						href = "/opds/authors/28",
						title = "Brandon Sanderson",
						links = listOf(unmonitor)
					)
				)
			),
			BinderyCatalogTab.Authors
		).single() as BinderyCatalogCard.Link

		assertEquals(unmonitor, authorCard.unmonitorAction)
		assertEquals(BinderyOpdsActionType.Unmonitor, authorCard.primaryAction()?.type)
	}

	@Test
	fun authorDetailCatalogUsesNavigationUnmonitorAction() {
		val unmonitor = BinderyLink(
			href = "/opds/authors/28/unmonitor",
			title = "Unmonitor author",
			rel = listOf(BINDERY_UNMONITOR_REL),
			type = "application/json"
		)
		val catalog = BinderyCatalog(
			title = "Brandon Sanderson",
			navigation = listOf(
				unmonitor,
				BinderyLink(href = "/opds/authors/28/collections", title = "Collections")
			)
		)

		assertEquals(unmonitor, catalog.unmonitorAction)
		assertEquals(BinderyOpdsActionType.Unmonitor, catalog.primaryAction()?.type)
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
		assertEquals(
			BinderyCatalogCardVisualPolicy(
				coverAspectRatio = 2f / 3f,
				imageContentScaleFit = true
			),
			binderyCatalogCardVisualPolicy(
				BinderyCatalogCard.Finding(
					id = "894",
					title = "The Hobbit.epub",
					subtitle = "Ebook / ENG / EPUB",
					path = "/opds/findings/894",
					imageUrl = "/opds/books/3816/cover"
				)
			)
		)
	}

	@Test
	fun authorDetailPublicationsSortByPublishedYearAndHideUnknownDates() {
		val newest = BinderyPublication(id = "new", title = "New", published = "2023-01-01")
		val unknown = BinderyPublication(id = "unknown", title = "Unknown")
		val oldest = BinderyPublication(id = "old", title = "Old", published = "1999-04-10")

		assertEquals(
			listOf(oldest, newest),
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

}
