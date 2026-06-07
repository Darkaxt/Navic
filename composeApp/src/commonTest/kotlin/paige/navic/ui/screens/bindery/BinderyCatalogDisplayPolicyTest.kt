package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
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
			"/opds/books?limit=5&owned=1&languages=eng&coverage=any",
			binderyAvailabilityFilteredCatalogPath("/opds/books?limit=5", "eng")
		)
		assertEquals(
			"/opds/books?limit=5&owned=1&languages=eng&coverage=any",
			binderyAvailabilityFilteredCatalogPath(
				"/opds/books?limit=5&owned=1&languages=spa&coverage=all",
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
	fun catalogCardsPreserveOpdsMonitorAndDownloadActionLinks() {
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
			listOf("epub", "pdf"),
			rows.ebooks.map { row -> row.id }
		)
		assertTrue(rows.audiobooks.first().subtitle.orEmpty().contains("Audible"))
		assertTrue(rows.ebooks.first().subtitle.orEmpty().contains("HarperCollins"))
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
	fun recentlyAddedHubRowsHideRequestOnlyBooks() {
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
						id = "urn:bindery:book:owned",
						title = "Owned ebook",
						links = listOf(
							BinderyLink(
								href = "/opds/books/owned/resources/ebook",
								rel = listOf("http://opds-spec.org/acquisition"),
								type = "application/epub+zip"
							)
						)
					)
				)
			)
		)

		assertEquals(
			listOf("Owned ebook"),
			row.cards.map { it.title }
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
			BinderyCatalogCard.Link(
				id = "/opds/collections/1",
				title = "Complete",
				subtitle = "Collection",
				path = "/opds/collections/1",
				availability = paige.navic.domain.repositories.BinderyAvailability(
					owned = true,
					complete = true,
					missingBooks = 0
				)
			).availabilityStatus()
		)
		assertEquals(
			AurralOwnershipStatus.Partial,
			BinderyCatalogCard.Link(
				id = "/opds/collections/2",
				title = "Partial",
				subtitle = "Collection",
				path = "/opds/collections/2",
				availability = paige.navic.domain.repositories.BinderyAvailability(
					owned = true,
					complete = false,
					ownedBooks = 2,
					missingBooks = 1
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
			AurralOwnershipStatus.Owned,
			BinderyCatalogCard.Book(
				id = "book-2",
				title = "Concrete ebook",
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
				)
			)
		)

		assertEquals(
			listOf(
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
