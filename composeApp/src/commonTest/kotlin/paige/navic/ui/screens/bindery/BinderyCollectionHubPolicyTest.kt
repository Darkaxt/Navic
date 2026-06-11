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

class BinderyCollectionHubPolicyTest {
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
				BinderyLink(href = "/opds/findings", title = "Findings"),
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
