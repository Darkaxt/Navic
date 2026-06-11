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

class BinderyAvailabilityPolicyTest {
	@Test
	fun availabilityStatusUsesMusicOwnershipDotSemantics() {
		assertEquals(
			AurralOwnershipStatus.Owned,
			BinderyCatalogCard.Book(
				id = "book-owned",
				title = "Owned",
				subtitle = "Author",
				imageUrl = null,
				availability = paige.navic.domain.repositories.BinderyAvailability(
					owned = true,
					complete = true,
					ownedFormats = listOf("ebook", "audiobook"),
					ownedLanguages = listOf("eng")
				)
			).availabilityStatus()
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			BinderyCatalogCard.Book(
				id = "book-partial",
				title = "Partial",
				subtitle = "Author",
				imageUrl = null,
				availability = paige.navic.domain.repositories.BinderyAvailability(
					owned = true,
					complete = false,
					ownedFormats = listOf("ebook"),
					ownedLanguages = listOf("eng")
				)
			).availabilityStatus()
		)
		assertEquals(
			AurralOwnershipStatus.Missing,
			BinderyCatalogCard.Book(
				id = "book-1",
				title = "Missing",
				subtitle = "Author",
				imageUrl = null,
				availability = paige.navic.domain.repositories.BinderyAvailability(owned = false)
			).availabilityStatus()
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			BinderyCatalogCard.Book(
				id = "book-2",
				title = "Legacy concrete ebook",
				subtitle = "Author",
				imageUrl = null,
				links = listOf(
					BinderyLink(
						href = "/opds/books/2/resources/ebook",
						rel = listOf("http://opds-spec.org/acquisition"),
						type = "application/epub+zip"
					)
				)
			).availabilityStatus()
		)
		assertEquals(
			AurralOwnershipStatus.Missing,
			BinderyCatalogCard.Book(
				id = "book-3",
				title = "Request-only metadata",
				subtitle = "Author",
				imageUrl = null,
				links = listOf(
					BinderyLink(
						href = "/opds/books/3/download",
						rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
						type = "application/json"
					)
				)
			).availabilityStatus()
		)
	}

	@Test
	fun catalogPublicationReadingOrderContributesToBookAvailabilityWithoutFindings() {
		val row = BinderyHubCatalogRow(
			row = BinderyHubRow(
				kind = BinderyHubRowKind.RecentlyAdded,
				path = "/opds/recent",
				title = "Recently Added"
			),
			catalog = BinderyCatalog(
				title = "Recently Added",
				publications = listOf(
					BinderyPublication(
						id = "urn:bindery:book:3913",
						title = "The Maps of Middle-Earth",
						author = "J.R.R. Tolkien",
						readingOrder = listOf(
							BinderyReadingOrderItem(
								href = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
								title = "The Maps of Middle-Earth",
								type = "application/epub+zip",
								properties = mapOf("language" to "eng")
							)
						)
					)
				)
			)
		)

		val card = row.cards(languageFilter = "eng").single()
		assertEquals("The Maps of Middle-Earth", card.title)
		assertEquals(AurralOwnershipStatus.Partial, card.availabilityStatus("eng"))
		assertTrue(card.hasAvailableContent("eng"))
	}

	@Test
	fun bookAvailabilityRequiresBothFormatsInTheSameLanguage() {
		val crossLanguageAvailability = paige.navic.domain.repositories.BinderyAvailability(
			owned = true,
			complete = true,
			ownedFormats = listOf("ebook", "audiobook"),
			ownedLanguages = listOf("eng", "spa"),
			ownedCombinations = listOf(
				paige.navic.domain.repositories.BinderyAvailabilityCombination(
					format = "ebook",
					language = "eng"
				),
				paige.navic.domain.repositories.BinderyAvailabilityCombination(
					format = "audiobook",
					language = "spa"
				)
			)
		)

		assertEquals(
			AurralOwnershipStatus.Partial,
			BinderyCatalogCard.Book(
				id = "book-cross-language",
				title = "Cross-language",
				subtitle = "Author",
				imageUrl = null,
				availability = crossLanguageAvailability
			).availabilityStatus(languageFilter = "eng")
		)
	}

	@Test
	fun bookAvailabilityUsesSelectedLanguageAsTheOnlyGreenPipeline() {
		val spanishCompleteEnglishEbookOnly = paige.navic.domain.repositories.BinderyAvailability(
			owned = true,
			complete = true,
			ownedFormats = listOf("ebook", "audiobook"),
			ownedLanguages = listOf("eng", "spa"),
			ownedCombinations = listOf(
				paige.navic.domain.repositories.BinderyAvailabilityCombination(
					format = "ebook",
					language = "eng"
				),
				paige.navic.domain.repositories.BinderyAvailabilityCombination(
					format = "ebook",
					language = "spa"
				),
				paige.navic.domain.repositories.BinderyAvailabilityCombination(
					format = "audiobook",
					language = "spa"
				)
			)
		)
		val englishMissingSpanishComplete = paige.navic.domain.repositories.BinderyAvailability(
			owned = true,
			complete = true,
			ownedFormats = listOf("ebook", "audiobook"),
			ownedLanguages = listOf("eng", "spa"),
			ownedCombinations = listOf(
				paige.navic.domain.repositories.BinderyAvailabilityCombination(
					format = "ebook",
					language = "spa"
				),
				paige.navic.domain.repositories.BinderyAvailabilityCombination(
					format = "audiobook",
					language = "spa"
				)
			)
		)

		assertEquals(
			AurralOwnershipStatus.Partial,
			spanishCompleteEnglishEbookOnly.toBookOwnershipStatus(languageFilter = "eng")
		)
		assertEquals(
			AurralOwnershipStatus.Owned,
			spanishCompleteEnglishEbookOnly.toBookOwnershipStatus(languageFilter = "spa")
		)
		assertEquals(
			AurralOwnershipStatus.Missing,
			englishMissingSpanishComplete.toBookOwnershipStatus(languageFilter = "eng")
		)
	}

	@Test
	fun aggregateOwnedCompleteDoesNotOverrideSelectedLanguageMediaMissing() {
		val noEnglishMedia = paige.navic.domain.repositories.BinderyAvailability(
			owned = true,
			complete = true,
			ownedBooks = 1,
			missingBooks = 0,
			totalBooks = 1,
			ownedFormats = listOf("ebook", "audiobook"),
			ownedLanguages = listOf("spa")
		)

		assertEquals(
			AurralOwnershipStatus.Missing,
			noEnglishMedia.toBookOwnershipStatus(languageFilter = "eng")
		)
		assertEquals(
			AurralOwnershipStatus.Owned,
			noEnglishMedia.toBookOwnershipStatus(languageFilter = "spa")
		)
	}

	@Test
	fun ambiguousAggregateFormatsCannotMakeSelectedLanguageGreen() {
		val ambiguousMultiLanguageMedia = BinderyAvailability(
			owned = true,
			complete = true,
			ownedBooks = 2,
			missingBooks = 0,
			totalBooks = 2,
			ownedFormats = listOf("ebook", "audiobook"),
			ownedLanguages = listOf("eng", "spa"),
			languages = listOf("eng", "spa"),
			mode = "any"
		)

		assertEquals(
			AurralOwnershipStatus.Partial,
			ambiguousMultiLanguageMedia.toBookOwnershipStatus(languageFilter = "eng")
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			BinderyCatalogCard.Book(
				id = "book-ambiguous",
				title = "Ambiguous",
				subtitle = "Author",
				imageUrl = null,
				availability = ambiguousMultiLanguageMedia
			).availabilityStatus(languageFilter = "eng")
		)
	}

	@Test
	fun englishEbookOnlyAvailabilityStaysYellowAcrossCatalogCards() {
		val englishEbookOnly = BinderyAvailability(
			owned = true,
			complete = false,
			formats = listOf("ebook", "audiobook"),
			ownedFormats = listOf("ebook"),
			ownedLanguages = listOf("eng"),
			ownedCombinations = listOf(
				BinderyAvailabilityCombination(format = "ebook", language = "eng")
			),
			languages = listOf("eng"),
			mode = "any"
		)
		val book = BinderyCatalogCard.Book(
			id = "book-ebook-only",
			title = "The Maps of Middle-Earth",
			subtitle = "J.R.R. Tolkien",
			imageUrl = null,
			availability = englishEbookOnly
		)
		val link = BinderyCatalogCard.Link(
			id = "/opds/collections/1",
			title = "Middle-earth",
			subtitle = "Collection",
			path = "/opds/collections/1",
			availability = englishEbookOnly
		)

		assertEquals(AurralOwnershipStatus.Partial, englishEbookOnly.toBookOwnershipStatus("eng"))
		assertEquals(AurralOwnershipStatus.Partial, book.availabilityStatus("eng"))
		assertEquals(AurralOwnershipStatus.Partial, link.availabilityStatus("eng"))
		assertEquals(true, book.hasAvailableContent("eng"))
		assertEquals(1f, englishEbookOnly.availabilityAlpha("eng"))
	}

	@Test
	fun concreteAcquisitionLinksContributeToBookAvailabilityWhenAvailabilityIsMissing() {
		val englishEpub = BinderyLink(
			href = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
			rel = listOf("http://opds-spec.org/acquisition"),
			type = "application/epub+zip",
			properties = mapOf(
				"format" to "ebook",
				"language" to "eng"
			)
		)
		val englishAudiobook = BinderyLink(
			href = "/opds/books/3913/resources/audiobook-1",
			rel = listOf("http://opds-spec.org/acquisition"),
			type = "audio/mpeg",
			properties = mapOf(
				"format" to "audiobook",
				"language" to "eng"
			)
		)
		val spanishEpub = BinderyLink(
			href = "/opds/books/3913/resources/ebook-spanish",
			rel = listOf("http://opds-spec.org/acquisition"),
			type = "application/epub+zip",
			properties = mapOf(
				"format" to "ebook",
				"language" to "spa"
			)
		)
		val ebookOnly = BinderyCatalogCard.Book(
			id = "urn:bindery:book:3913",
			title = "The Maps of Middle-Earth",
			subtitle = "J.R.R. Tolkien",
			imageUrl = null,
			links = listOf(englishEpub)
		)
		val complete = ebookOnly.copy(
			id = "urn:bindery:book:complete",
			links = listOf(englishEpub, englishAudiobook)
		)
		val otherLanguageOnly = ebookOnly.copy(
			id = "urn:bindery:book:spanish",
			links = listOf(spanishEpub)
		)

		assertEquals(AurralOwnershipStatus.Partial, ebookOnly.availabilityStatus("eng"))
		assertEquals(true, ebookOnly.hasAvailableContent("eng"))
		assertEquals(AurralOwnershipStatus.Owned, complete.availabilityStatus("eng"))
		assertEquals(AurralOwnershipStatus.Missing, otherLanguageOnly.availabilityStatus("eng"))
		assertEquals(AurralOwnershipStatus.Partial, otherLanguageOnly.availabilityStatus("spa"))
	}

	@Test
	fun publicationRowsUseTheSameConcreteMediaAvailabilityPipelineAsBookCards() {
		val englishEpub = BinderyLink(
			href = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
			rel = listOf("http://opds-spec.org/acquisition"),
			type = "application/epub+zip",
			properties = mapOf("language" to "eng")
		)
		val englishAudiobook = BinderyReadingOrderItem(
			href = "/opds/books/3913/resources/audiobook-1",
			title = "The Maps of Middle-Earth",
			type = "audio/mpeg",
			properties = mapOf("language" to "eng")
		)
		val requestOnly = BinderyLink(
			href = "/opds/books/3913/download",
			rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
			type = "application/json"
		)
		val ebookOnly = BinderyPublication(
			id = "urn:bindery:book:3913",
			title = "The Maps of Middle-Earth",
			author = "J.R.R. Tolkien",
			links = listOf(englishEpub)
		)
		val complete = ebookOnly.copy(
			readingOrder = listOf(englishAudiobook)
		)
		val downloadRequestOnly = ebookOnly.copy(
			id = "urn:bindery:book:request-only",
			links = listOf(requestOnly),
			readingOrder = emptyList()
		)

		assertEquals(AurralOwnershipStatus.Partial, ebookOnly.availabilityStatus("eng"))
		assertEquals(1f, ebookOnly.availabilityAlpha("eng"))
		assertEquals(true, ebookOnly.hasAvailableContent("eng"))
		assertEquals(AurralOwnershipStatus.Owned, complete.availabilityStatus("eng"))
		assertEquals(AurralOwnershipStatus.Missing, downloadRequestOnly.availabilityStatus("eng"))
		assertEquals(0.42f, downloadRequestOnly.availabilityAlpha("eng"))
		assertEquals(false, downloadRequestOnly.hasAvailableContent("eng"))
	}

	@Test
	fun downloadRequestOnlyDoesNotMakeBookAvailable() {
		val downloadRequestOnly = BinderyCatalogCard.Book(
			id = "urn:bindery:book:3913",
			title = "The Maps of Middle-Earth",
			subtitle = "J.R.R. Tolkien",
			imageUrl = null,
			links = listOf(
				BinderyLink(
					href = "/opds/books/3913/download",
					title = "Request download",
					rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
					type = "application/json"
				)
			)
		)

		assertEquals(AurralOwnershipStatus.Missing, downloadRequestOnly.availabilityStatus("eng"))
		assertEquals(false, downloadRequestOnly.hasAvailableContent("eng"))
	}

	@Test
	fun aggregateOnlyOwnershipDoesNotMakeBookAvailabilityGreen() {
		val aggregateOnly = BinderyAvailability(
			owned = true,
			complete = true,
			ownedBooks = 1,
			missingBooks = 0,
			totalBooks = 1
		)
		val card = BinderyCatalogCard.Book(
			id = "book-aggregate-only",
			title = "Aggregate-only ownership",
			subtitle = "Author",
			imageUrl = null,
			availability = aggregateOnly
		)

		assertEquals(AurralOwnershipStatus.Missing, aggregateOnly.toBookOwnershipStatus(languageFilter = "eng"))
		assertEquals(AurralOwnershipStatus.Missing, aggregateOnly.toBookOwnershipStatus())
		assertEquals(AurralOwnershipStatus.Missing, card.availabilityStatus(languageFilter = "eng"))
		assertEquals(false, card.hasAvailableContent(languageFilter = "eng"))
	}

	@Test
	fun catalogLinksUseTheSameSelectedLanguageMediaPipelineAsBooks() {
		val aggregateOnly = BinderyAvailability(
			owned = true,
			complete = true,
			ownedBooks = 12,
			missingBooks = 0,
			totalBooks = 12
		)
		val englishAudioOnly = BinderyAvailability(
			owned = true,
			complete = false,
			ownedCombinations = listOf(
				BinderyAvailabilityCombination(format = "audiobook", language = "eng")
			)
		)
		val englishComplete = BinderyAvailability(
			owned = true,
			complete = true,
			ownedCombinations = listOf(
				BinderyAvailabilityCombination(format = "ebook", language = "eng"),
				BinderyAvailabilityCombination(format = "audiobook", language = "eng")
			)
		)
		val aggregateOnlyLink = BinderyCatalogCard.Link(
			id = "/opds/collections/1",
			title = "Aggregate Only",
			subtitle = "Collection",
			path = "/opds/collections/1",
			availability = aggregateOnly
		)
		val audioOnlyLink = aggregateOnlyLink.copy(
			id = "/opds/collections/2",
			title = "Audio Only",
			path = "/opds/collections/2",
			availability = englishAudioOnly
		)
		val completeLink = aggregateOnlyLink.copy(
			id = "/opds/collections/3",
			title = "Complete",
			path = "/opds/collections/3",
			availability = englishComplete
		)

		assertEquals(AurralOwnershipStatus.Missing, aggregateOnlyLink.availabilityStatus("eng"))
		assertEquals(false, aggregateOnlyLink.hasAvailableContent("eng"))
		assertEquals(0.42f, aggregateOnly.availabilityAlpha("eng"))
		assertEquals(AurralOwnershipStatus.Partial, audioOnlyLink.availabilityStatus("eng"))
		assertEquals(AurralOwnershipStatus.Owned, completeLink.availabilityStatus("eng"))
	}

}
