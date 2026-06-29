package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyAvailability
import paige.navic.domain.repositories.BinderyAvailabilityCombination
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyFindingFile
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.repositories.BinderySyncPair
import paige.navic.domain.repositories.BinderyWhispersyncArtifact
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
	fun carouselCardWidthMatchesMusicShelfWidthInsteadOfScalingWithWindow() {
		assertEquals(150, binderyCarouselCardWidthDp(columns = 5, availableWidthDp = 768))
		assertEquals(150, binderyCarouselCardWidthDp(columns = 8, availableWidthDp = 1600))
		assertEquals(150, binderyCarouselCardWidthDp(columns = 8, availableWidthDp = 320))
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
	fun bookCardsExposeWhispersyncBadgeOnlyForReadyPairsWithArtifactHref() {
		val cards = binderyCatalogCards(
			BinderyCatalog(
				title = "Books",
				publications = listOf(
					BinderyPublication(
						id = "urn:bindery:book:3809",
						title = "Bastille vs. the Evil Librarians",
						sync = BinderyBookSync(
							whispersyncStatus = "ready",
							syncPairs = listOf(
								BinderySyncPair(
									bookId = 3809,
									ebookBookFileId = 633,
									audiobookBookFileId = 426,
									whispersync = BinderyWhispersyncArtifact(
										status = "ready",
										artifactId = 12,
										artifactHref = "/opds/books/3809/sync/12"
									)
								)
							)
						)
					),
					BinderyPublication(
						id = "urn:bindery:book:3810",
						title = "Summary Ready Only",
						sync = BinderyBookSync(whispersyncStatus = "ready")
					),
					BinderyPublication(
						id = "urn:bindery:book:3811",
						title = "Pending Pair",
						sync = BinderyBookSync(
							whispersyncStatus = "pending",
							syncPairs = listOf(
								BinderySyncPair(
									bookId = 3811,
									ebookBookFileId = 700,
									audiobookBookFileId = 701,
									whispersync = BinderyWhispersyncArtifact(
										status = "pending",
										artifactHref = "/opds/books/3811/sync/2"
									)
								)
							)
						)
					),
					BinderyPublication(
						id = "urn:bindery:book:3812",
						title = "Ready Pair Missing Artifact",
						sync = BinderyBookSync(
							whispersyncStatus = "ready",
							syncPairs = listOf(
								BinderySyncPair(
									bookId = 3812,
									ebookBookFileId = 800,
									audiobookBookFileId = 801,
									whispersync = BinderyWhispersyncArtifact(
										status = "ready",
										artifactHref = null
									)
								)
							)
						)
					)
				)
			),
			tab = BinderyCatalogTab.Books
		).filterIsInstance<BinderyCatalogCard.Book>()

		assertTrue(cards.first { it.id == "urn:bindery:book:3809" }.hasActionableWhispersync)
		assertFalse(cards.first { it.id == "urn:bindery:book:3810" }.hasActionableWhispersync)
		assertFalse(cards.first { it.id == "urn:bindery:book:3811" }.hasActionableWhispersync)
		assertFalse(cards.first { it.id == "urn:bindery:book:3812" }.hasActionableWhispersync)
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

}
