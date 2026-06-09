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

class BinderyCatalogDisplayPolicyTest {
	@Test
	fun tabPathsUseCanonicalOpdsCatalogRoutes() {
		assertEquals("/opds/formats/audiobook", BinderyCatalogTab.Audiobooks.path)
		assertEquals("/opds/books", BinderyCatalogTab.Books.path)
		assertEquals("/opds/collections", BinderyCatalogTab.Collections.path)
		assertEquals("/opds/authors", BinderyCatalogTab.Authors.path)
		assertEquals("/opds/findings", BinderyCatalogTab.Findings.path)
	}

	@Test
	fun bookLikeCatalogTabsUseFiveItemInitialPages() {
		assertEquals("/opds/formats/audiobook?limit=5", BinderyCatalogTab.Audiobooks.initialCatalogPath())
		assertEquals("/opds/books?limit=5", BinderyCatalogTab.Books.initialCatalogPath())
		assertEquals("/opds/collections", BinderyCatalogTab.Collections.initialCatalogPath())
		assertEquals("/opds/authors", BinderyCatalogTab.Authors.initialCatalogPath())
		assertEquals("/opds/findings?limit=5", BinderyCatalogTab.Findings.initialCatalogPath())
	}

	@Test
	fun binderyBookGridColumnsClampToAudiobookDensityRange() {
		assertEquals(5, normalizedBinderyBookGridColumns(1))
		assertEquals(5, normalizedBinderyBookGridColumns(4))
		assertEquals(5, normalizedBinderyBookGridColumns(5))
		assertEquals(7, normalizedBinderyBookGridColumns(7))
		assertEquals(8, normalizedBinderyBookGridColumns(12))
	}

	@Test
	fun carouselCardWidthFitsConfiguredVisibleCountInsideAvailableWidth() {
		assertEquals(138, binderyCarouselCardWidthDp(columns = 5, availableWidthDp = 768))
		assertEquals(113, binderyCarouselCardWidthDp(columns = 6, availableWidthDp = 768))
		assertEquals(96, binderyCarouselCardWidthDp(columns = 8, availableWidthDp = 820))
		assertEquals(96, binderyCarouselCardWidthDp(columns = 8, availableWidthDp = 320))
	}

	@Test
	fun rawBookLikeCatalogPathsUseFiveItemInitialPagesUnlessAlreadyLimited() {
		assertEquals("/opds/books?limit=5", binderyInitialCatalogPath("/opds/books"))
		assertEquals("/opds/books?sort=title&limit=5", binderyInitialCatalogPath("/opds/books?sort=title"))
		assertEquals("/opds/books?limit=20", binderyInitialCatalogPath("/opds/books?limit=20"))
		assertEquals("/opds/findings?limit=5", binderyInitialCatalogPath("/opds/findings"))
		assertEquals("/opds/recent", binderyInitialCatalogPath("/opds/recent"))
	}

	@Test
	fun languageAvailabilityFilteringAddsOwnedConstraintOnlyForCatalogLists() {
		assertEquals(
			"/opds/books?limit=5&owned=1&formats=ebook,audiobook&languages=eng&coverage=any",
			binderyAvailabilityFilteredCatalogPath("/opds/books?limit=5", "eng")
		)
		assertEquals(
			"/opds/books?limit=5&owned=1&formats=ebook,audiobook&languages=eng&coverage=any",
			binderyAvailabilityFilteredCatalogPath(
				"/opds/books?limit=5&owned=1&formats=ebook&languages=spa&coverage=all",
				"eng"
			)
		)
		assertEquals(
			"/opds/authors/28?languages=eng&coverage=any",
			binderyAvailabilityFilteredCatalogPath(
				path = "/opds/authors/28",
				languageFilter = "eng",
				mode = BinderyAvailabilityQueryMode.Detail
			)
		)
		assertEquals(
			"/opds/books?limit=5",
			binderyAvailabilityFilteredCatalogPath("/opds/books?limit=5", "all")
		)
	}

	@Test
	fun binderySearchPathsDoNotRestrictResultsToOwnedEntries() {
		assertEquals(
			"/opds/search?q=Sanderson&limit=200&languages=eng&coverage=any",
			binderySearchCatalogPath(
				path = "/opds/search?q=Sanderson&limit=200",
				languageFilter = "eng"
			)
		)
		assertEquals(
			"/opds/discover/authors?q=Sanderson",
			binderyDiscoverAuthorsPath("Sanderson")
		)
	}

	@Test
	fun subjectLabelsOpenAudiobookSearchWithTrimmedSubjectQuery() {
		assertEquals(
			Screen.Search(
				nested = true,
				scope = SearchScope.Audiobooks,
				initialQuery = "Epic Fantasy"
			),
			binderySubjectSearchDestination(" Epic Fantasy ")
		)
		assertEquals(null, binderySubjectSearchDestination(" "))
	}

	@Test
	fun catalogCardsPreserveOpdsActionLinksButDoNotPromoteBookDownloads() {
		val monitor = BinderyLink(
			href = "/opds/discover/authors/hc%3Apeter-sanderson/monitor",
			title = "Monitor author",
			rel = listOf(BINDERY_MONITOR_REL),
			type = "application/json"
		)
		val download = BinderyLink(
			href = "/opds/books/3693/download",
			title = "Request download",
			rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
			type = "application/json"
		)
		val bookCard = binderyCatalogCards(
			BinderyCatalog(
				title = "Search",
				publications = listOf(
					BinderyPublication(
						id = "urn:bindery:book:3693",
						title = "Alcatraz versus the Evil Librarians",
						author = "Brandon Sanderson",
						links = listOf(download)
					)
				)
			),
			BinderyCatalogTab.Books
		).single() as BinderyCatalogCard.Book
		val authorCard = binderyCatalogCards(
			BinderyCatalog(
				title = "Author Search",
				navigation = listOf(
					BinderyLink(
						href = "/opds/discover/authors/hc%3Apeter-sanderson",
						title = "Peter Sanderson",
						images = emptyList(),
						properties = emptyMap()
					)
				),
				links = listOf(monitor)
			),
			BinderyCatalogTab.Authors
		).single() as BinderyCatalogCard.Link

		assertEquals(download, bookCard.downloadRequestAction)
		assertEquals(null, bookCard.primaryAction())
		assertEquals(monitor, authorCard.monitorAction)
	}

	@Test
	fun findingCardsOpenNativeFindingDetailScreensAndPreserveActionLinks() {
		val download = BinderyLink(
			href = "/api/v1/findings/894/acquire",
			title = "Request download",
			rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
			type = "application/json"
		)
		val findingCard = binderyCatalogCards(
			BinderyCatalog(
				title = "Findings",
				publications = listOf(
					BinderyPublication(
						id = "urn:bindery:finding:894",
						title = "J.R.R. Tolkien - The Hobbit.epub",
						author = "J.R.R. Tolkien",
						finding = BinderyFindingMetadata(
							findingId = "894",
							mediaType = "ebook",
							language = "eng",
							format = "epub",
							availabilityStatus = "imported"
						),
						images = listOf(BinderyLink(href = "/opds/books/3816/cover")),
						links = listOf(
							BinderyLink(
								href = "/opds/findings/894",
								rel = listOf("self"),
								type = "application/opds-publication+json"
							),
							download
						)
					)
				)
			),
			BinderyCatalogTab.Findings
		).single() as BinderyCatalogCard.Finding

		assertEquals("894", findingCard.id)
		assertEquals("J.R.R. Tolkien - The Hobbit.epub", findingCard.title)
		assertEquals("Ebook / ENG / EPUB / Imported", findingCard.subtitle)
		assertEquals("/opds/findings/894", findingCard.path)
		assertEquals("/opds/books/3816/cover", findingCard.imageUrl)
		assertEquals(download, findingCard.downloadRequestAction)
		assertEquals(
			Screen.BinderyFinding("/opds/findings/894", "J.R.R. Tolkien - The Hobbit.epub"),
			binderyDestinationForCard(findingCard)
		)
	}

	@Test
	fun findingRowsPreserveDownloadMetadataForFindingDetailButDoNotExposeBookPageAction() {
		val download = BinderyLink(
			href = "/api/v1/findings/894/acquire",
			title = "Request download",
			rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL),
			type = "application/json"
		)
		val catalog = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				BinderyPublication(
					id = "urn:bindery:finding:894",
					title = "J.R.R. Tolkien - The Hobbit.epub",
					finding = BinderyFindingMetadata(
						findingId = "894",
						mediaType = "ebook",
						language = "eng",
						format = "epub",
						availabilityStatus = "imported"
					),
					links = listOf(
						BinderyLink(
							href = "/opds/findings/894",
							rel = listOf("self"),
							type = "application/opds-publication+json"
						),
						download
					)
				)
			)
		)

		val row = binderyBookFindingRows(catalog).ebooks.single()

		assertEquals(BinderyBookFindingRowAction.Play, row.readerAction)
		assertEquals(null, row.readerOpdsAction)
		assertEquals(download, row.card.downloadRequestAction)
		assertEquals(BinderyOpdsActionType.DownloadRequest, row.card.primaryAction()?.type)
	}

	@Test
	fun downloadRequestActionsUsePlayPresentationInBinderyUi() {
		assertEquals(BinderyOpdsActionPresentation.Add, BinderyOpdsActionType.Monitor.presentation())
		assertEquals(BinderyOpdsActionPresentation.Hide, BinderyOpdsActionType.Unmonitor.presentation())
		assertEquals(BinderyOpdsActionPresentation.Play, BinderyOpdsActionType.DownloadRequest.presentation())
	}

	@Test
	fun bookPublicationsAreNotPromotedToFindingRoutesByCatalogTitle() {
		val cards = binderyCatalogCards(
			BinderyCatalog(
				title = "Findings",
				publications = listOf(
					BinderyPublication(
						id = "urn:bindery:book:3913",
						title = "The Maps of Middle-Earth",
						author = "J.R.R. Tolkien",
						links = listOf(
							BinderyLink(
								href = "/opds/books/3913",
								rel = listOf("self"),
								type = "application/opds-publication+json"
							)
						)
					)
				)
			),
			tab = null
		)

		val card = cards.single() as BinderyCatalogCard.Book
		assertEquals("urn:bindery:book:3913", card.id)
		assertEquals(
			Screen.BinderyBook("3913", "The Maps of Middle-Earth"),
			binderyDestinationForCard(card)
		)
	}

	@Test
	fun malformedFindingRowsWithoutFindingIdentityAreIgnored() {
		val cards = binderyCatalogCards(
			BinderyCatalog(
				title = "Findings",
				publications = listOf(
					BinderyPublication(
						id = "urn:bindery:book:3913",
						title = "The Maps of Middle-Earth",
						author = "J.R.R. Tolkien"
					)
				)
			),
			BinderyCatalogTab.Findings
		)

		assertTrue(cards.isEmpty())
	}

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

	@Test
	fun bookVersionRowsAggregateAudioAndListEbooks() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3693",
			title = "Alcatraz versus the Evil Librarians",
			links = listOf(
				BinderyLink(
					href = "/opds/books/3693/resources/ebook-1",
					title = "Alcatraz EPUB",
					type = "application/epub+zip",
					rel = listOf("http://opds-spec.org/acquisition"),
					properties = mapOf(
						"kind" to "ebook",
						"size" to "431666"
					)
				)
			),
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3693/resources/audio-1",
					title = "Part 01",
					type = "audio/mpeg",
					durationSeconds = 1800.0,
					sizeBytes = 1048576,
					properties = mapOf("relativePath" to "Part 01.mp3")
				),
				BinderyReadingOrderItem(
					href = "/opds/books/3693/resources/audio-2",
					title = "Part 02",
					type = "audio/mpeg",
					durationSeconds = 1861.0,
					sizeBytes = 524288,
					properties = mapOf("relativePath" to "Part 02.mp3")
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Alcatraz Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-1",
					title = "Alcatraz EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 431666
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/audio-1",
					title = "Part 01",
					type = "audio/mpeg",
					kind = "audio",
					durationSeconds = 1800.0,
					sizeBytes = 1048576,
					properties = mapOf("relativePath" to "Part 01.mp3")
				)
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "audiobook",
					kind = BinderyBookVersionKind.Audiobook,
					title = "Audiobook",
					subtitle = "MP3 / 2 parts / 1h 1m 1s / 1.5 MB"
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-1",
					kind = BinderyBookVersionKind.Ebook,
					title = "EPUB",
					subtitle = "421.54 KB"
				)
			),
			binderyBookVersionRows(manifest, resources)
		)
	}

	@Test
	fun bookVersionRowsAttachCurrentBookFindingToMatchingConcreteResource() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3880",
			title = "Leaf by Niggle",
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3880/resources/audio-89fb6c8269e08bd7a52e",
					title = "02 Leaf by Niggle",
					type = "audio/mpeg",
					durationSeconds = 2069.742,
					sizeBytes = 33118504,
					properties = mapOf(
						"bookFileId" to "765",
						"format" to "audiobook",
						"language" to "eng",
						"narrator" to "Derek Jacobi",
						"provider" to "AudioBook Bay"
					)
				)
			)
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				BinderyPublication(
					id = "urn:bindery:finding:17",
					title = "Tales from the Perilous Realm - J.R.R. Tolkien Audiobook [MP3]",
					finding = BinderyFindingMetadata(
						findingId = "17",
						mediaType = "audiobook",
						format = "mp3",
						language = "eng",
						narrator = "Derek Jacobi",
						availabilityStatus = "imported",
						mappings = listOf(
							BinderyFindingMapping(
								id = "52018",
								bookId = "3880",
								bookTitle = "Leaf by Niggle",
								mediaType = "audiobook",
								targetLanguage = "eng",
								acquisitionStatus = "imported",
								bookFileId = "765",
								sourceCatalogCandidateId = "17"
							),
							BinderyFindingMapping(
								id = "65058",
								bookId = "3735",
								bookTitle = "Tales from the Perilous Realm",
								mediaType = "ebook",
								targetLanguage = "eng",
								acquisitionStatus = "imported",
								bookFileId = "799",
								sourceCatalogCandidateId = "0"
							)
						)
					),
					links = listOf(
						BinderyLink(
							href = "/opds/findings/17",
							rel = listOf("self"),
							type = "application/opds-publication+json"
						)
					)
				)
			)
		)

		val row = binderyBookVersionRows(
			manifest = manifest,
			resourceCatalog = null,
			languageFilter = "eng",
			findingsCatalog = findings
		).single()

		assertEquals(BinderyBookVersionKind.Audiobook, row.kind)
		assertEquals("audiobook:765", row.id)
		assertEquals("17", row.finding?.id)
		assertEquals("/opds/findings/17", row.finding?.path)
	}

	@Test
	fun bookVersionRowsDoNotCreateRowsFromFindingsWithoutConcreteResources() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3880",
			title = "Leaf by Niggle"
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				BinderyPublication(
					id = "urn:bindery:finding:17",
					title = "Tales from the Perilous Realm - J.R.R. Tolkien Audiobook [MP3]",
					finding = BinderyFindingMetadata(
						findingId = "17",
						mediaType = "audiobook",
						format = "mp3",
						language = "eng",
						availabilityStatus = "imported",
						mappings = listOf(
							BinderyFindingMapping(
								bookId = "3880",
								bookTitle = "Leaf by Niggle",
								mediaType = "audiobook",
								targetLanguage = "eng",
								acquisitionStatus = "imported",
								bookFileId = "765"
							)
						)
					),
					links = listOf(BinderyLink(href = "/opds/findings/17", type = "application/opds+json"))
				)
			)
		)

		assertTrue(
			binderyBookVersionRows(
				manifest = manifest,
				resourceCatalog = null,
				languageFilter = "eng",
				findingsCatalog = findings
			).isEmpty()
		)
	}

	@Test
	fun bookVersionRowsPreferReadaloudWithoutHidingStandaloneFormats() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3693",
			title = "Alcatraz versus the Evil Librarians",
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3693/resources/audio-1",
					title = "Part 01",
					type = "audio/mpeg",
					durationSeconds = 1800.0,
					sizeBytes = 1048576,
					properties = mapOf("relativePath" to "Part 01.mp3")
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Alcatraz Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3693/resources/readaloud-1",
					title = "Alcatraz Storyteller EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 3145728,
					properties = mapOf(
						"provider" to "Storyteller",
						"format" to "epub",
						"mediaOverlay" to "true",
						"relativePath" to "Alcatraz.readaloud.epub"
					)
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-1",
					title = "Alcatraz EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 431666,
					properties = mapOf("format" to "epub")
				)
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/readaloud-1",
					kind = BinderyBookVersionKind.Readaloud,
					title = "Readaloud",
					subtitle = "Storyteller / EPUB / 3.0 MB"
				),
				BinderyBookVersionRow(
					id = "audiobook",
					kind = BinderyBookVersionKind.Audiobook,
					title = "Audiobook",
					subtitle = "MP3 / 1 part / 30m 0s / 1.0 MB"
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-1",
					kind = BinderyBookVersionKind.Ebook,
					title = "EPUB",
					subtitle = "421.54 KB"
				)
			),
			binderyBookVersionRows(manifest, resources)
		)
	}

	@Test
	fun bookVersionRoutingKeepsReadaloudSeparateFromEbookAndAudiobookActions() {
		assertEquals(
			BinderyBookVersionRoutingAction.OpenReadaloud,
			BinderyBookVersionRow(
				id = "/opds/books/3693/resources/readaloud-1",
				kind = BinderyBookVersionKind.Readaloud,
				title = "Readaloud",
				subtitle = null
			).routingAction()
		)
		assertEquals(
			BinderyBookVersionRoutingAction.OpenEbook,
			BinderyBookVersionRow(
				id = "/opds/books/3693/resources/ebook-1",
				kind = BinderyBookVersionKind.Ebook,
				title = "EPUB",
				subtitle = null
			).routingAction()
		)
		assertEquals(
			BinderyBookVersionRoutingAction.OpenAudiobook,
			BinderyBookVersionRow(
				id = "audiobook",
				kind = BinderyBookVersionKind.Audiobook,
				title = "Audiobook",
				subtitle = null
			).routingAction()
		)
	}

	@Test
	fun ebookAndReadaloudVersionRowsOpenNativeReaderRoutes() {
		assertEquals(
			Screen.Reader(
				title = "Alcatraz versus the Evil Librarians",
				publicationUrl = "https://bindery.local/opds/books/3693/resources/readaloud-1",
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/readaloud-1",
				kind = ReaderPublicationKind.Readaloud,
				mediaOverlayEnabled = true
			),
			binderyReaderDestinationForVersionRow(
				row = BinderyBookVersionRow(
					id = "/opds/books/3693/resources/readaloud-1",
					kind = BinderyBookVersionKind.Readaloud,
					title = "Readaloud",
					subtitle = null
				),
				bookId = "3693",
				bookTitle = "Alcatraz versus the Evil Librarians",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
		assertEquals(
			Screen.Reader(
				title = "Alcatraz versus the Evil Librarians",
				publicationUrl = "https://bindery.local/opds/books/3693/resources/ebook-1",
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-1",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false
			),
			binderyReaderDestinationForVersionRow(
				row = BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-1",
					kind = BinderyBookVersionKind.Ebook,
					title = "EPUB",
					subtitle = null
				),
				bookId = "3693",
				bookTitle = "Alcatraz versus the Evil Librarians",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
		assertEquals(
			null,
			binderyReaderDestinationForVersionRow(
				row = BinderyBookVersionRow(
					id = "audiobook",
					kind = BinderyBookVersionKind.Audiobook,
					title = "Audiobook",
					subtitle = null
				),
				bookId = "3693",
				bookTitle = "Alcatraz versus the Evil Librarians",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun bookVersionRowsFallBackToAudioResourcesWhenManifestHasNoReadingOrder() {
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/1/resources/audio-1",
					title = "Track 01",
					type = "audio/mpeg",
					kind = "audio",
					durationSeconds = 30.0,
					sizeBytes = 1024,
					properties = mapOf("relativePath" to "Track 01.mp3")
				)
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "audiobook",
					kind = BinderyBookVersionKind.Audiobook,
					title = "Audiobook",
					subtitle = "MP3 / 1 part / 30s / 1.0 KB"
				)
			),
			binderyBookVersionRows(BinderyManifest(id = "urn:bindery:book:1", title = "Book"), resources)
		)
	}

	@Test
	fun ebookVersionRowsUsePublisherFormatSizeAndSortByQuality() {
		val resources = BinderyResourceCatalog(
			title = "Alcatraz Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-small-publisher",
					title = "[ePubLibre] Alcatraz versus the Evil Librarians - Brandon Sanderson",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 431666,
					properties = mapOf(
						"relativePath" to "[ePubLibre] Alcatraz versus the Evil Librarians - Brandon Sanderson.EPUB"
					)
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-unbracketed",
					title = "Alcatraz versus the Evil Librarians - Brandon Sanderson",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 261589,
					properties = mapOf(
						"relativePath" to "Alcatraz versus the Evil Librarians - Brandon Sanderson.EPUB"
					)
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-large-publisher",
					title = "[ePubLibre] Alcatraz versus the Evil Librarians - Brandon Sanderson",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 5296480,
					properties = mapOf(
						"relativePath" to "[ePubLibre] Alcatraz versus the Evil Librarians - Brandon Sanderson.EPUB"
					)
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-large-pdf",
					title = "Alcatraz PDF",
					type = "application/pdf",
					kind = "ebook",
					sizeBytes = 6_000_000,
					properties = mapOf(
						"relativePath" to "Alcatraz versus the Evil Librarians - Brandon Sanderson.PDF"
					)
				)
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-large-pdf",
					kind = BinderyBookVersionKind.Ebook,
					title = "PDF",
					subtitle = "5.72 MB",
					format = ReaderPublicationFormat.Pdf
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-large-publisher",
					kind = BinderyBookVersionKind.Ebook,
					title = "ePubLibre",
					subtitle = "EPUB / 5.05 MB"
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-small-publisher",
					kind = BinderyBookVersionKind.Ebook,
					title = "ePubLibre",
					subtitle = "EPUB / 421.54 KB"
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-unbracketed",
					kind = BinderyBookVersionKind.Ebook,
					title = "EPUB",
					subtitle = "255.45 KB"
				)
			),
			binderyBookVersionRows(BinderyManifest(id = "urn:bindery:book:3693", title = "Alcatraz"), resources)
		)
	}

	@Test
	fun versionRowsFilterByLanguageAndShowProviderPublisherFields() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3693",
			title = "Alcatraz",
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3693/resources/audio-eng",
					title = "Part 01",
					type = "audio/mpeg",
					sizeBytes = 3072,
					properties = mapOf(
						"kind" to "audio",
						"language" to "eng",
						"format" to "mp3",
						"publisher" to "Macmillan Audio",
						"provider" to "Audible",
						"narrator" to "Michael Kramer"
					)
				),
				BinderyReadingOrderItem(
					href = "/opds/books/3693/resources/audio-spa",
					title = "Parte 01",
					type = "audio/mpeg",
					sizeBytes = 4096,
					properties = mapOf(
						"kind" to "audio",
						"language" to "spa",
						"format" to "mp3",
						"publisher" to "Spanish Audio"
					)
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-eng",
					title = "Alcatraz EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 1024,
					properties = mapOf(
						"language" to "eng",
						"format" to "epub",
						"publisher" to "Tor",
						"provider" to "Hardcover"
					)
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-spa",
					title = "Alcatraz EPUB ES",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 2048,
					properties = mapOf(
						"language" to "spa",
						"format" to "epub",
						"publisher" to "Spanish Books"
					)
				)
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "audiobook",
					kind = BinderyBookVersionKind.Audiobook,
					title = "Audiobook",
					subtitle = "Audible / Macmillan Audio / Michael Kramer / MP3 / 1 part / 3.0 KB"
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-eng",
					kind = BinderyBookVersionKind.Ebook,
					title = "Tor",
					subtitle = "Hardcover / EPUB / 1.0 KB"
				)
			),
			binderyBookVersionRows(manifest, resources, "eng")
		)
	}

	@Test
	fun resourceOnlyBookStillProducesVisibleEbookVersion() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3913",
			title = "The Maps of Middle-Earth"
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
					title = "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 8_210_367,
					properties = mapOf(
						"format" to "ebook",
						"language" to "eng",
						"publisher" to "The Tolkien Estate Limited",
						"relativePath" to "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien.EPUB"
					)
				)
			)
		)

		val groups = binderyBookVersionGroups(binderyBookVersionRows(manifest, resources, "eng"))

		assertTrue(groups.audiobooks.isEmpty())
		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
					kind = BinderyBookVersionKind.Ebook,
					title = "The Tolkien Estate Limited",
					subtitle = "EPUB / 7.83 MB"
				)
			),
			groups.ebooks
		)
		assertFalse(groups.isEmpty)
	}

	@Test
	fun ebookVersionRowsUseRelativePathAsIdentifierWhenPublisherIsMissing() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3816",
			title = "The Hobbit"
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3816/resources/ebook-abb-pdf",
					title = "PDF",
					type = "application/pdf",
					kind = "ebook",
					sizeBytes = 7_340_032,
					properties = mapOf(
						"provider" to "AudioBook Bay",
						"format" to "pdf",
						"language" to "eng",
						"relativePath" to "01 - The Hobbit The Hobbit (Illustrated Edition by Alan Lee).pdf"
					)
				)
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "/opds/books/3816/resources/ebook-abb-pdf",
					kind = BinderyBookVersionKind.Ebook,
					title = "01 - The Hobbit The Hobbit (Illustrated Edition by Alan Lee)",
					subtitle = "AudioBook Bay / PDF / 7.0 MB",
					format = ReaderPublicationFormat.Pdf
				)
			),
			binderyBookVersionRows(manifest, resources, "eng")
		)
	}

	@Test
	fun pdfVersionRowsPreservePdfPublicationFormatForReaderRoutes() {
		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-abb-pdf",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-abb-pdf",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false,
				publicationFormat = ReaderPublicationFormat.Pdf
			),
			binderyReaderDestinationForVersionRow(
				row = BinderyBookVersionRow(
					id = "/opds/books/3816/resources/ebook-abb-pdf",
					kind = BinderyBookVersionKind.Ebook,
					title = "PDF",
					subtitle = "AudioBook Bay / PDF / 7.0 MB",
					format = ReaderPublicationFormat.Pdf
				),
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun bookVersionRowsCollapseDuplicateEbookLinksAfterFindingEnrichment() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3816",
			title = "The Hobbit",
			links = listOf(
				BinderyLink(
					href = "/opds/books/3816/resources/ebook-abb-pdf/download",
					rel = listOf("http://opds-spec.org/acquisition"),
					type = "application/pdf",
					properties = mapOf(
						"provider" to "AudioBook Bay",
						"format" to "pdf",
						"kind" to "ebook",
						"language" to "eng",
						"size" to "7214203",
						"relativePath" to "01 - The Hobbit Movie Books The Hobbit - Digital booklet - Batttle Of Five Armies.pdf"
					)
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3816/resources/ebook-abb-pdf",
					title = "PDF",
					type = "application/pdf",
					kind = "ebook",
					sizeBytes = 7_214_203,
					properties = mapOf(
						"provider" to "AudioBook Bay",
						"format" to "pdf",
						"language" to "eng",
						"bookFileId" to "765",
						"relativePath" to "01 - The Hobbit Movie Books The Hobbit - Digital booklet - Batttle Of Five Armies.pdf"
					)
				)
			)
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				BinderyPublication(
					id = "urn:bindery:finding:17",
					title = "The Hobbit - Digital booklet",
					finding = BinderyFindingMetadata(
						findingId = "17",
						mediaType = "ebook",
						format = "pdf",
						language = "eng",
						availabilityStatus = "imported",
						mappings = listOf(
							BinderyFindingMapping(
								bookId = "3816",
								bookTitle = "The Hobbit",
								mediaType = "ebook",
								targetLanguage = "eng",
								acquisitionStatus = "imported",
								bookFileId = "765"
							)
						)
					),
					links = listOf(BinderyLink(href = "/opds/findings/17", type = "application/opds+json"))
				)
			)
		)

		val rows = binderyBookVersionRows(
			manifest = manifest,
			resourceCatalog = resources,
			languageFilter = "eng",
			findingsCatalog = findings
		)

		assertEquals(1, rows.size)
		assertEquals("17", rows.single().finding?.id)
		assertEquals(
			"01 - The Hobbit Movie Books The Hobbit - Digital booklet - Batttle Of Five Armies",
			rows.single().title
		)
	}

	@Test
	fun ebookManifestReadingOrderDoesNotCreateFakeAudiobookVersion() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3913",
			title = "The Maps of Middle-Earth",
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
					title = "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien",
					type = "application/epub+zip",
					sizeBytes = 8_210_367,
					properties = mapOf(
						"format" to "ebook",
						"kind" to "ebook",
						"language" to "eng",
						"publisher" to "The Tolkien Estate Limited",
						"relativePath" to "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien.EPUB"
					)
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
					title = "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien",
					type = "application/epub+zip",
					kind = "ebook",
					sizeBytes = 8_210_367,
					properties = mapOf(
						"format" to "ebook",
						"language" to "eng",
						"publisher" to "The Tolkien Estate Limited",
						"relativePath" to "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien.EPUB"
					)
				)
			)
		)

		val groups = binderyBookVersionGroups(binderyBookVersionRows(manifest, resources, "eng"))

		assertTrue(groups.audiobooks.isEmpty())
		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
					kind = BinderyBookVersionKind.Ebook,
					title = "The Tolkien Estate Limited",
					subtitle = "EPUB / 7.83 MB"
				)
			),
			groups.ebooks
		)
	}

	@Test
	fun ebookManifestReadingOrderStillShowsVersionWhenResourcesAreUnavailable() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3913",
			title = "The Maps of Middle-Earth",
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
					title = "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien",
					type = "application/epub+zip",
					sizeBytes = 8_210_367,
					properties = mapOf(
						"format" to "ebook",
						"kind" to "ebook",
						"language" to "eng",
						"publisher" to "The Tolkien Estate Limited",
						"relativePath" to "[The Tolkien Estate Limited] The Shaping of Middle-Earth - J.R.R. Tolkien.EPUB"
					)
				)
			)
		)

		val groups = binderyBookVersionGroups(binderyBookVersionRows(manifest, null, "eng"))

		assertTrue(groups.audiobooks.isEmpty())
		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
					kind = BinderyBookVersionKind.Ebook,
					title = "The Tolkien Estate Limited",
					subtitle = "EPUB / 7.83 MB"
				)
			),
			groups.ebooks
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
