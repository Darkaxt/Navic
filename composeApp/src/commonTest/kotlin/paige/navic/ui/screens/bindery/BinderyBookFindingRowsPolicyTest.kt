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

class BinderyBookFindingRowsPolicyTest {
	@Test
	fun bookFindingRowsSplitAudiobooksAndEbooksAndSortByContentQuality() {
		val catalog = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				BinderyPublication(
					id = "urn:bindery:finding:mp3",
					title = "The Hobbit - Radio Drama",
					finding = BinderyFindingMetadata(
						findingId = "mp3",
						mediaType = "audiobook",
						format = "mp3",
						language = "eng",
						provider = "Internet Archive",
						narrator = "Radio Drama Cast",
						fileCount = 19,
						sizeBytes = 175_000_000,
						bitrateBps = 96_000,
						availabilityStatus = "available"
					)
				),
				BinderyPublication(
					id = "urn:bindery:finding:m4b",
					title = "The Hobbit - Unabridged",
					finding = BinderyFindingMetadata(
						findingId = "m4b",
						mediaType = "audiobook",
						format = "m4b",
						language = "eng",
						provider = "Audible",
						narrator = "Rob Inglis",
						fileCount = 1,
						sizeBytes = 1_000_000_000,
						bitrateBps = 224_000,
						availabilityStatus = "available"
					)
				),
				BinderyPublication(
					id = "urn:bindery:finding:pdf",
					title = "The Hobbit PDF",
					finding = BinderyFindingMetadata(
						findingId = "pdf",
						mediaType = "ebook",
						format = "pdf",
						language = "eng",
						publisher = "Houghton",
						sizeBytes = 26_000_000,
						availabilityStatus = "available"
					)
				),
				BinderyPublication(
					id = "urn:bindery:finding:epub",
					title = "The Hobbit EPUB",
					finding = BinderyFindingMetadata(
						findingId = "epub",
						mediaType = "ebook",
						format = "epub",
						language = "eng",
						publisher = "HarperCollins",
						sizeBytes = 4_000_000,
						availabilityStatus = "available"
					)
				)
			)
		)

		val rows = binderyBookFindingRows(catalog)

		assertEquals(
			listOf("m4b", "mp3"),
			rows.audiobooks.map { row -> row.id }
		)
		assertEquals(
			listOf("pdf", "epub"),
			rows.ebooks.map { row -> row.id }
		)
		assertTrue(rows.audiobooks.first().subtitle.orEmpty().contains("Audible"))
		assertTrue(rows.ebooks.first().subtitle.orEmpty().contains("Houghton"))
	}

	@Test
	fun ebookFindingRowsSuppressAudioOnlyMetadataDefaults() {
		val catalog = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				BinderyPublication(
					id = "urn:bindery:finding:ebook",
					title = "The Maps of Middle-Earth EPUB",
					finding = BinderyFindingMetadata(
						findingId = "ebook",
						mediaType = "ebook",
						format = "epub",
						language = "eng",
						publisher = "Houghton",
						fileCount = 1,
						sizeBytes = 26_000_000,
						bitrateBps = 0,
						sampleRateHz = 0,
						availabilityStatus = "imported"
					)
				)
			)
		)

		val subtitle = binderyBookFindingRows(catalog).ebooks.single().subtitle.orEmpty()

		assertTrue(subtitle.contains("Houghton"))
		assertTrue(subtitle.contains("Epub"))
		assertFalse(subtitle.contains("kbps"))
		assertFalse(subtitle.contains("kHz"))
		assertFalse(subtitle.contains("Hz"))
	}

	@Test
	fun bookFindingRowsOnlyShowAvailableFindings() {
		val availableDownload = BinderyLink(
			href = "/opds/findings/downloadable/download",
			title = "Request download",
			rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
			type = "application/json"
		)
		val catalog = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				BinderyPublication(
					id = "urn:bindery:finding:imported",
					title = "Imported EPUB",
					finding = BinderyFindingMetadata(
						findingId = "imported",
						mediaType = "ebook",
						format = "epub",
						language = "eng",
						availabilityStatus = "imported"
					)
				),
				BinderyPublication(
					id = "urn:bindery:finding:downloadable",
					title = "Downloadable MP3",
					finding = BinderyFindingMetadata(
						findingId = "downloadable",
						mediaType = "audiobook",
						format = "mp3",
						language = "eng",
						availabilityStatus = "available"
					),
					links = listOf(availableDownload)
				),
				BinderyPublication(
					id = "urn:bindery:finding:unknown",
					title = "Unknown EPUB",
					finding = BinderyFindingMetadata(
						findingId = "unknown",
						mediaType = "ebook",
						format = "epub",
						language = "eng",
						availabilityStatus = "unknown"
					),
					links = listOf(
						BinderyLink(
							href = "/opds/findings/unknown/download",
							title = "Request download",
							rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
							type = "application/json"
						)
					)
				),
				BinderyPublication(
					id = "urn:bindery:finding:metadata-only",
					title = "Metadata-only PDF",
					finding = BinderyFindingMetadata(
						findingId = "metadata-only",
						mediaType = "ebook",
						format = "pdf",
						language = "eng"
					)
				)
			)
		)

		val rows = binderyBookFindingRows(catalog)

		assertEquals(listOf("downloadable"), rows.audiobooks.map { it.id })
		assertEquals(listOf("imported"), rows.ebooks.map { it.id })
	}

	@Test
	fun recentlyAddedHubRowsHideBooksWithoutExplicitAvailability() {
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
						id = "urn:bindery:book:request-only",
						title = "Request-only metadata",
						links = listOf(
							BinderyLink(
								href = "/opds/books/request-only/download",
								rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
								type = "application/json"
							)
						)
					),
					BinderyPublication(
						id = "urn:bindery:book:loose-acquisition",
						title = "Concrete ebook without findings",
						links = listOf(
							BinderyLink(
								href = "/opds/books/loose-acquisition/resources/ebook",
								rel = listOf("http://opds-spec.org/acquisition"),
								type = "application/epub+zip"
							)
						)
					),
					BinderyPublication(
						id = "urn:bindery:book:aggregate-only",
						title = "Aggregate-only ownership",
						availability = paige.navic.domain.repositories.BinderyAvailability(
							owned = true,
							complete = true,
							ownedBooks = 1,
							missingBooks = 0,
							totalBooks = 1
						)
					),
					BinderyPublication(
						id = "urn:bindery:book:owned",
						title = "Explicitly owned book",
						availability = paige.navic.domain.repositories.BinderyAvailability(
							owned = true,
							complete = true,
							ownedFormats = listOf("ebook", "audiobook"),
							ownedLanguages = listOf("eng"),
							missingBooks = 0
						)
					)
				)
			)
		)

		assertEquals(
			listOf("Concrete ebook without findings", "Explicitly owned book"),
			row.cards().map { it.title }
		)
		assertEquals(
			listOf(AurralOwnershipStatus.Partial, AurralOwnershipStatus.Owned),
			row.cards().map { it.availabilityStatus() }
		)
	}

	@Test
	fun binderyUiKeysStayUniqueWhenProviderFieldsAreBlank() {
		assertNotEquals(
			binderyUiStableKey("file", 0, "", null),
			binderyUiStableKey("file", 1, "", null)
		)
		assertNotEquals(
			binderyFindingFileRowKey(BinderyFindingFile(), 0),
			binderyFindingFileRowKey(BinderyFindingFile(), 1)
		)
		assertNotEquals(
			binderyFindingMappingRowKey(BinderyFindingMapping(), 0),
			binderyFindingMappingRowKey(BinderyFindingMapping(), 1)
		)
	}

	@Test
	fun findingDetailMappingsCollapseSelectedImportedDuplicatesForSameBook() {
		val selected = BinderyFindingMapping(
			id = "selected",
			bookId = "3816",
			bookTitle = "The Hobbit",
			authorName = "J.R.R. Tolkien",
			confidence = 100.0,
			mediaType = "ebook",
			targetLanguage = "eng",
			acquisitionStatus = "selected",
			selectedBytes = 7_214_203,
			bookFileId = "0",
			sourceCatalogCandidateId = "17"
		)
		val imported = BinderyFindingMapping(
			id = "imported",
			bookId = "3816",
			bookTitle = "The Hobbit",
			authorName = "J.R.R. Tolkien",
			confidence = 0.0,
			mediaType = "ebook",
			targetLanguage = "eng",
			acquisitionStatus = "imported",
			selectedBytes = 0,
			bookFileId = "765",
			sourceCatalogCandidateId = "0"
		)
		val otherLanguage = imported.copy(
			id = "spanish",
			targetLanguage = "spa"
		)

		val rows = listOf(imported, selected, otherLanguage).collapsedForBinderyFindingDetail()

		assertEquals(listOf(selected, otherLanguage), rows)
	}

}
