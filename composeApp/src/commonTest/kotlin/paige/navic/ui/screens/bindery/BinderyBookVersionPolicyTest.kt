package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyAvailability
import paige.navic.domain.repositories.BinderyAvailabilityCombination
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyFindingFile
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyPropertyBag
import paige.navic.domain.repositories.BinderyPropertyValue
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

class BinderyBookVersionPolicyTest {
	@Test
	fun bookVersionRowsPreferAudiobookVersionsOverManifestAudioRows() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3816",
			title = "The Hobbit",
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3816/resources/audio-old",
					title = "Old resource row",
					type = "audio/mpeg",
					properties = mapOf("bookFileId" to "791")
				)
			)
		)
		val audiobookVersions = listOf(
			BinderyAudiobookVersion(
				id = 88,
				bookId = 3816,
				bookFileId = 791,
				title = "The Hobbit",
				language = "English",
				narrator = "Rob Inglis, Martin Shaw, Derek Jacobi",
				editionType = "unabridged",
				durationMs = 79_139_972,
				sizeBytes = 1_803_441_713,
				resourceCount = 27,
				codec = "mixed"
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "audiobook-version:88",
					kind = BinderyBookVersionKind.Audiobook,
					title = "Rob Inglis, Martin Shaw, Derek Jacobi",
					subtitle = "Unabridged / MIXED / 27 parts / 21h 59m / 1.67 GB",
					audiobookId = "88",
					audiobookBookFileId = "791"
				)
			),
			binderyBookVersionRows(
				manifest = manifest,
				resourceCatalog = null,
				languageFilter = "eng",
				audiobookVersions = audiobookVersions
			)
		)
	}

	@Test
	fun audiobookVersionsUseCurrentBinderyQualitySort() {
		val rows = binderyBookVersionRows(
			manifest = BinderyManifest(id = "urn:bindery:book:3816", title = "The Hobbit"),
			resourceCatalog = null,
			languageFilter = "eng",
			audiobookVersions = listOf(
				BinderyAudiobookVersion(
					id = 20,
					bookId = 3816,
					bookFileId = 607,
					title = "The Hobbit",
					language = "eng",
					narrator = "Low score but large FLAC",
					codec = "flac",
					bitrateBps = 320_000,
					sampleRateHz = 48_000,
					durationMs = 10_000_000,
					sizeBytes = 2_000_000_000,
					qualityScore = 128_044.0
				),
				BinderyAudiobookVersion(
					id = 69,
					bookId = 3816,
					bookFileId = 694,
					title = "The Hobbit",
					language = "eng",
					narrator = "Current ready version",
					codec = "mp3",
					bitrateBps = 128_000,
					sampleRateHz = 44_100,
					durationMs = 9_000_000,
					sizeBytes = 500_000_000,
					qualityScore = 128_100.0
				)
			)
		)

		assertEquals(
			listOf("69", "20"),
			rows.filter { row -> row.kind == BinderyBookVersionKind.Audiobook }
				.mapNotNull { row -> row.audiobookId }
		)
	}

	@Test
	fun bookVersionRowsAttachReadyWhispersyncPairsToMatchingOppositeRows() {
		val resources = BinderyResourceCatalog(
			title = "The Hobbit Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3816/resources/ebook-435",
					title = "Houghton Mifflin Harcourt",
					type = "application/epub+zip",
					kind = "ebook",
					properties = mapOf(
						"bookFileId" to "435",
						"language" to "eng",
						"format" to "epub",
						"publisher" to "Houghton Mifflin Harcourt"
					)
				)
			)
		)
		val sync = BinderyBookSync(
			bookId = 3816,
			syncPairs = listOf(
				BinderySyncPair(
					bookId = 3816,
					ebookBookFileId = 435,
					audiobookBookFileId = 694,
					whispersync = BinderyWhispersyncArtifact(
						status = "ready",
						artifactId = 3,
						artifactHref = "/opds/books/3816/sync/3",
						score = .989,
						coverage = .96
					)
				),
				BinderySyncPair(
					bookId = 3816,
					ebookBookFileId = 435,
					audiobookBookFileId = 607,
					whispersync = BinderyWhispersyncArtifact(
						status = "pending",
						artifactId = 4
					)
				)
			)
		)

		val rows = binderyBookVersionRows(
			manifest = BinderyManifest(id = "urn:bindery:book:3816", title = "The Hobbit"),
			resourceCatalog = resources,
			languageFilter = "eng",
			audiobookVersions = listOf(
				BinderyAudiobookVersion(
					id = 69,
					bookId = 3816,
					bookFileId = 694,
					title = "The Hobbit",
					language = "English",
					narrator = "Andy Serkis"
				),
				BinderyAudiobookVersion(
					id = 20,
					bookId = 3816,
					bookFileId = 607,
					title = "The Hobbit",
					language = "English",
					narrator = "NPR Radio Drama Cast"
				)
			),
			bookSync = sync
		)

		val andy = rows.first { row -> row.audiobookId == "69" }
		val npr = rows.first { row -> row.audiobookId == "20" }
		val ebook = rows.first { row -> row.kind == BinderyBookVersionKind.Ebook }

		assertEquals(1, andy.syncMatches.size)
		assertEquals("Houghton Mifflin Harcourt", andy.syncMatches.single().oppositeTitle)
		assertEquals(96, andy.syncMatches.single().coveragePercent)
		assertEquals(99, andy.syncMatches.single().scorePercent)
		assertTrue(npr.syncMatches.isEmpty())
		assertEquals(1, ebook.syncMatches.size)
		assertEquals("Andy Serkis", ebook.syncMatches.single().oppositeTitle)
	}

	@Test
	fun bookVersionRowsUseManifestEmbeddedWhispersyncPairsWhenSyncEndpointIsUnavailable() {
		val resources = BinderyResourceCatalog(
			title = "Bastille Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3809/resources/ebook-633",
					title = "Bastille EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					properties = mapOf(
						"bookFileId" to "633",
						"language" to "eng",
						"format" to "epub"
					)
				)
			)
		)
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3809",
			title = "Bastille vs. the Evil Librarians",
			sync = BinderyBookSync(
				bookId = 3809,
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
		)

		val rows = binderyBookVersionRows(
			manifest = manifest,
			resourceCatalog = resources,
			languageFilter = "eng",
			audiobookVersions = listOf(
				BinderyAudiobookVersion(
					id = 44,
					bookId = 3809,
					bookFileId = 426,
					title = "Bastille vs. the Evil Librarians",
					language = "English",
					narrator = "Ramon de Ocampo"
				)
			),
			bookSync = null
		)

		val ebook = rows.first { row -> row.kind == BinderyBookVersionKind.Ebook }
		val audiobook = rows.first { row -> row.kind == BinderyBookVersionKind.Audiobook }
		assertEquals(1, ebook.syncMatches.size)
		assertEquals(1, audiobook.syncMatches.size)
		assertEquals("Ramon de Ocampo", ebook.syncMatches.single().oppositeTitle)
	}

	@Test
	fun ebookWhispersyncLaunchDestinationCarriesSelectedAudiobookContract() {
		val rows = hobbitVersionRowsWithWhispersync(status = "ready")
		val ebook = rows.first { row -> row.kind == BinderyBookVersionKind.Ebook }
		val match = ebook.whispersyncAudiobookLaunchMatches().single()

		assertEquals(BinderyWhispersyncAudiobookLaunchAction.OpenDirectly, ebook.whispersyncAudiobookLaunchAction())
		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-435",
				kind = ReaderPublicationKind.Ebook,
				publicationFormat = ReaderPublicationFormat.Epub,
				mediaOverlayEnabled = false,
				whispersyncSidecarUrl = "https://bindery.local/opds/books/3816/sync/3",
				whispersyncArtifactId = "3",
				whispersyncAudiobookId = "69",
				whispersyncAudiobookBookFileId = "694",
				whispersyncAudiobookTitle = "Andy Serkis"
			),
			binderyWhispersyncReaderDestinationForMatch(
				ebookRow = ebook,
				match = match,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun audiobookWhispersyncLaunchDestinationCarriesSelectedEbookContract() {
		val rows = hobbitVersionRowsWithWhispersync(status = "ready")
		val audiobook = rows.first { row -> row.kind == BinderyBookVersionKind.Audiobook }
		val match = audiobook.syncMatches.single()

		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-435",
				kind = ReaderPublicationKind.Ebook,
				publicationFormat = ReaderPublicationFormat.Epub,
				mediaOverlayEnabled = false,
				whispersyncSidecarUrl = "https://bindery.local/opds/books/3816/sync/3",
				whispersyncArtifactId = "3",
				whispersyncAudiobookId = "69",
				whispersyncAudiobookBookFileId = "694",
				whispersyncAudiobookTitle = "Andy Serkis"
			),
			binderyWhispersyncReaderDestinationForRowMatch(
				row = audiobook,
				match = match,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun genericWhispersyncLaunchDestinationKeepsEbookRowContract() {
		val rows = hobbitVersionRowsWithWhispersync(status = "ready")
		val ebook = rows.first { row -> row.kind == BinderyBookVersionKind.Ebook }
		val match = ebook.syncMatches.single()

		assertEquals(
			binderyWhispersyncReaderDestinationForMatch(
				ebookRow = ebook,
				match = match,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			),
			binderyWhispersyncReaderDestinationForRowMatch(
				row = ebook,
				match = match,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun multipleWhispersyncAudiobookLaunchCandidatesRequireChooser() {
		val rows = hobbitVersionRowsWithWhispersync(status = "ready", extraReadyAudiobook = true)
		val ebook = rows.first { row -> row.kind == BinderyBookVersionKind.Ebook }

		assertEquals(2, ebook.whispersyncAudiobookLaunchMatches().size)
		assertEquals(BinderyWhispersyncAudiobookLaunchAction.ChooseAudiobook, ebook.whispersyncAudiobookLaunchAction())
	}

	@Test
	fun embeddedReadyPairCanLaunchWithoutPreloadedAudiobookVersionRow() {
		val rows = binderyBookVersionRows(
			manifest = BinderyManifest(id = "urn:bindery:book:3816", title = "The Hobbit"),
			resourceCatalog = BinderyResourceCatalog(
				title = "Resources",
				resources = listOf(
					BinderyBookResource(
						href = "/opds/books/3816/resources/ebook-435",
						title = "The Hobbit EPUB",
						type = "application/epub+zip",
						kind = "ebook",
						properties = mapOf(
							"bookFileId" to "435",
							"format" to "epub",
							"language" to "eng"
						)
					)
				)
			),
			audiobookVersions = emptyList(),
			bookSync = BinderyBookSync(
				bookId = 3816,
				syncPairs = listOf(
					BinderySyncPair(
						bookId = 3816,
						ebookBookFileId = 435,
						audiobookBookFileId = 694,
						whispersync = BinderyWhispersyncArtifact(
							status = "ready",
							artifactId = null,
							artifactHref = "/opds/books/3816/sync/12"
						)
					)
				)
			)
		)
		val ebook = rows.single()
		val match = ebook.syncMatches.single()

		assertEquals("694", match.oppositeAudiobookBookFileId)
		assertEquals(BinderyWhispersyncAudiobookLaunchAction.OpenDirectly, ebook.whispersyncAudiobookLaunchAction())
		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-435",
				kind = ReaderPublicationKind.Ebook,
				publicationFormat = ReaderPublicationFormat.Epub,
				mediaOverlayEnabled = false,
				whispersyncSidecarUrl = "https://bindery.local/opds/books/3816/sync/12",
				whispersyncArtifactId = "12",
				whispersyncAudiobookId = null,
				whispersyncAudiobookBookFileId = "694",
				whispersyncAudiobookTitle = "Audiobook"
			),
			binderyWhispersyncReaderDestinationForMatch(
				ebookRow = ebook,
				match = match,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun pendingWhispersyncPairsDoNotCreateEbookLaunchCandidates() {
		val rows = hobbitVersionRowsWithWhispersync(status = "pending")
		val ebook = rows.first { row -> row.kind == BinderyBookVersionKind.Ebook }

		assertEquals(BinderyWhispersyncAudiobookLaunchAction.None, ebook.whispersyncAudiobookLaunchAction())
		assertTrue(ebook.whispersyncAudiobookLaunchMatches().isEmpty())
	}

	@Test
	fun readyWhispersyncPairsWithoutArtifactHrefDoNotCreateLaunchCandidates() {
		val rows = hobbitVersionRowsWithWhispersync(status = "ready", artifactHref = null)
		val ebook = rows.first { row -> row.kind == BinderyBookVersionKind.Ebook }
		val audiobook = rows.first { row -> row.kind == BinderyBookVersionKind.Audiobook }

		assertEquals(BinderyWhispersyncAudiobookLaunchAction.None, ebook.whispersyncAudiobookLaunchAction())
		assertTrue(ebook.syncMatches.isEmpty())
		assertTrue(audiobook.syncMatches.isEmpty())
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
	fun ebookVersionRowsKeepUnsupportedFormatsAsDownloadOnlyAndMapFoliateFormats() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3693",
			title = "Alcatraz"
		)
		val resources = BinderyResourceCatalog(
			title = "Alcatraz Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-epub",
					title = "Alcatraz EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					properties = mapOf("format" to "epub")
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-pdf",
					title = "Alcatraz PDF",
					type = "application/pdf",
					kind = "ebook",
					properties = mapOf("format" to "pdf")
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-azw3",
					title = "Alcatraz AZW3",
					type = "application/vnd.amazon.ebook",
					kind = "ebook",
					properties = mapOf("format" to "azw3")
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-mobi",
					title = "Alcatraz MOBI",
					type = "application/x-mobipocket-ebook",
					kind = "ebook",
					properties = mapOf("format" to "mobi")
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-cbz",
					title = "Alcatraz CBZ",
					type = "application/vnd.comicbook+zip",
					kind = "ebook",
					properties = mapOf("format" to "cbz")
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-fb2",
					title = "Alcatraz FB2",
					type = "application/x-fictionbook+xml",
					kind = "ebook",
					properties = mapOf("format" to "fb2")
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-txt",
					title = "Alcatraz TXT",
					type = "text/plain",
					kind = "ebook",
					properties = mapOf("format" to "txt")
				),
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-docx",
					title = "Alcatraz DOCX",
					type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
					kind = "ebook",
					properties = mapOf("format" to "docx")
				)
			)
		)

		assertEquals(
			listOf(
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-epub",
					kind = BinderyBookVersionKind.Ebook,
					title = "EPUB",
					subtitle = null
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-pdf",
					kind = BinderyBookVersionKind.Ebook,
					title = "PDF",
					subtitle = null,
					format = ReaderPublicationFormat.Pdf
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-azw3",
					kind = BinderyBookVersionKind.Ebook,
					title = "AZW3",
					subtitle = null,
					format = ReaderPublicationFormat.Azw3
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-mobi",
					kind = BinderyBookVersionKind.Ebook,
					title = "MOBI",
					subtitle = null,
					format = ReaderPublicationFormat.Mobi
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-cbz",
					kind = BinderyBookVersionKind.Ebook,
					title = "CBZ",
					subtitle = null,
					format = ReaderPublicationFormat.Cbz
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-fb2",
					kind = BinderyBookVersionKind.Ebook,
					title = "FB2",
					subtitle = null,
					format = ReaderPublicationFormat.Fb2
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-txt",
					kind = BinderyBookVersionKind.Ebook,
					title = "TXT",
					subtitle = null,
					readerSupported = false,
					downloadExtension = "txt"
				),
				BinderyBookVersionRow(
					id = "/opds/books/3693/resources/ebook-docx",
					kind = BinderyBookVersionKind.Ebook,
					title = "DOCX",
					subtitle = null,
					readerSupported = false,
					downloadExtension = "docx"
				)
			),
			binderyBookVersionRows(manifest, resources)
		)
	}

	@Test
	fun whispersyncReadyPairsOnlyAttachToEpubRows() {
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3693/resources/ebook-azw3",
					title = "Alcatraz AZW3",
					type = "application/vnd.amazon.ebook",
					kind = "ebook",
					properties = mapOf(
						"bookFileId" to "435",
						"format" to "azw3"
					)
				)
			)
		)
		val rows = binderyBookVersionRows(
			manifest = BinderyManifest(id = "urn:bindery:book:3693", title = "Alcatraz"),
			resourceCatalog = resources,
			audiobookVersions = listOf(
				BinderyAudiobookVersion(
					id = 69,
					bookId = 3693,
					bookFileId = 694,
					title = "Alcatraz",
					language = "English",
					narrator = "Narrator"
				)
			),
			bookSync = BinderyBookSync(
				bookId = 3693,
				syncPairs = listOf(
					BinderySyncPair(
						bookId = 3693,
						ebookBookFileId = 435,
						audiobookBookFileId = 694,
						whispersync = BinderyWhispersyncArtifact(
							status = "ready",
							artifactId = 3,
							artifactHref = "/opds/books/3693/sync/3"
						)
					)
				)
			)
		)

		assertTrue(rows.all { row -> row.syncMatches.isEmpty() })
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
	fun ebookVersionRowsCarryBinderyFullscreenCoverRenditionToReaderRoutes() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3816",
			title = "The Hobbit",
			images = listOf(
				BinderyLink(
					href = "/opds/books/3816/covers/fullscreen.webp",
					type = "image/webp",
					rel = listOf("cover", "fullscreen-cover")
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3816/resources/ebook-epub",
					title = "The Hobbit EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					properties = mapOf("format" to "epub")
				)
			)
		)

		val row = binderyBookVersionRows(manifest, resources).single()

		assertEquals("/opds/books/3816/covers/fullscreen.webp", row.fullscreenCoverHref)
		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-epub",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-epub",
				kind = ReaderPublicationKind.Ebook,
				publicationFormat = ReaderPublicationFormat.Epub,
				mediaOverlayEnabled = false,
				fullscreenCoverUrl = "https://bindery.local/opds/books/3816/covers/fullscreen.webp"
			),
			binderyReaderDestinationForVersionRow(
				row = row,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun ebookVersionRowsChooseClosestGeneratedFullscreenCoverVariantForReaderAspect() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3816",
			title = "The Hobbit",
			propertyValues = BinderyPropertyBag(
				mapOf(
					"fullscreenCoverVariants" to BinderyPropertyValue.ArrayValue(
						listOf(
							BinderyPropertyValue.ObjectValue(
								mapOf(
									"href" to BinderyPropertyValue.StringValue("/opds/books/3816/covers/phone.webp"),
									"width" to BinderyPropertyValue.NumberValue(1080.0, "1080"),
									"height" to BinderyPropertyValue.NumberValue(1920.0, "1920")
								)
							),
							BinderyPropertyValue.ObjectValue(
								mapOf(
									"href" to BinderyPropertyValue.StringValue("/opds/books/3816/covers/tablet.webp"),
									"width" to BinderyPropertyValue.NumberValue(1600.0, "1600"),
									"height" to BinderyPropertyValue.NumberValue(2560.0, "2560")
								)
							)
						)
					)
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3816/resources/ebook-epub",
					title = "The Hobbit EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					properties = mapOf("format" to "epub")
				)
			)
		)

		val row = binderyBookVersionRows(manifest, resources).single()

		assertEquals("/opds/books/3816/covers/phone.webp", row.fullscreenCoverHref)
		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-epub",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-epub",
				kind = ReaderPublicationKind.Ebook,
				publicationFormat = ReaderPublicationFormat.Epub,
				mediaOverlayEnabled = false,
				fullscreenCoverUrl = "https://bindery.local/opds/books/3816/covers/tablet.webp"
			),
			binderyReaderDestinationForVersionRow(
				row = row,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds",
				fullscreenCoverTargetAspectRatio = 1600.0 / 2560.0
			)
		)
	}

	@Test
	fun regularCoverImagesDoNotBecomeFullscreenShellCoverRoutes() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3816",
			title = "The Hobbit",
			images = listOf(
				BinderyLink(
					href = "/opds/books/3816/cover.jpg",
					type = "image/jpeg",
					rel = listOf("cover")
				)
			)
		)
		val resources = BinderyResourceCatalog(
			title = "Resources",
			resources = listOf(
				BinderyBookResource(
					href = "/opds/books/3816/resources/ebook-epub",
					title = "The Hobbit EPUB",
					type = "application/epub+zip",
					kind = "ebook",
					properties = mapOf("format" to "epub")
				)
			)
		)

		val row = binderyBookVersionRows(manifest, resources).single()

		assertEquals(null, row.fullscreenCoverHref)
		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-epub",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-epub",
				kind = ReaderPublicationKind.Ebook,
				publicationFormat = ReaderPublicationFormat.Epub,
				mediaOverlayEnabled = false
			),
			binderyReaderDestinationForVersionRow(
				row = row,
				bookId = "3816",
				bookTitle = "The Hobbit",
				opdsBaseUrl = "https://bindery.local/opds"
			)
		)
	}

	@Test
	fun unsupportedEbookRowsRouteToDownloadInsteadOfReader() {
		val row = BinderyBookVersionRow(
			id = "/opds/books/3816/resources/ebook-docx",
			kind = BinderyBookVersionKind.Ebook,
			title = "DOCX",
			subtitle = null,
			readerSupported = false,
			downloadExtension = "docx"
		)

		assertEquals(BinderyBookVersionRoutingAction.DownloadEbook, row.routingAction())
		assertEquals("DOCX.docx", row.downloadFileName())
		assertEquals(
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			row.downloadMimeType()
		)
		assertEquals(
			null,
			binderyReaderDestinationForVersionRow(
				row = row,
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

	private fun hobbitVersionRowsWithWhispersync(
		status: String,
		artifactHref: String? = "/opds/books/3816/sync/3",
		extraReadyAudiobook: Boolean = false
	): List<BinderyBookVersionRow> =
		binderyBookVersionRows(
			manifest = BinderyManifest(id = "urn:bindery:book:3816", title = "The Hobbit"),
			resourceCatalog = BinderyResourceCatalog(
				title = "The Hobbit Resources",
				resources = listOf(
					BinderyBookResource(
						href = "/opds/books/3816/resources/ebook-435",
						title = "Houghton Mifflin Harcourt",
						type = "application/epub+zip",
						kind = "ebook",
						properties = mapOf(
							"bookFileId" to "435",
							"language" to "eng",
							"format" to "epub",
							"publisher" to "Houghton Mifflin Harcourt"
						)
					)
				)
			),
			languageFilter = "eng",
			audiobookVersions = listOfNotNull(
				BinderyAudiobookVersion(
					id = 69,
					bookId = 3816,
					bookFileId = 694,
					title = "The Hobbit",
					language = "English",
					narrator = "Andy Serkis"
				),
				if (extraReadyAudiobook) {
					BinderyAudiobookVersion(
						id = 70,
						bookId = 3816,
						bookFileId = 695,
						title = "The Hobbit",
						language = "English",
						narrator = "Rob Inglis"
					)
				} else {
					null
				}
			),
			bookSync = BinderyBookSync(
				bookId = 3816,
				syncPairs = listOfNotNull(
					BinderySyncPair(
						bookId = 3816,
						ebookBookFileId = 435,
						audiobookBookFileId = 694,
						whispersync = BinderyWhispersyncArtifact(
							status = status,
							artifactId = 3,
							artifactHref = artifactHref,
							score = .989,
							coverage = .96
						)
					),
					if (extraReadyAudiobook) {
						BinderySyncPair(
							bookId = 3816,
							ebookBookFileId = 435,
							audiobookBookFileId = 695,
							whispersync = BinderyWhispersyncArtifact(
								status = "ready",
								artifactId = 4,
								artifactHref = "/opds/books/3816/sync/4",
								score = .932,
								coverage = .91
							)
						)
					} else {
						null
					}
				)
			)
		)

}
