package paige.navic.ui.screens.bindery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.BinderySyncPair
import paige.navic.domain.repositories.BinderyWhispersyncArtifact
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.WhispersyncAudioSeekTarget
import paige.navic.reader.WhispersyncSegment
import paige.navic.reader.encodeReaderReadingProgress
import paige.navic.ui.navigation.Screen

class BinderyContinueShelfPolicyTest {
	@Test
	fun continueListeningItemsUseCachedAudiobookDetailAndProgressRoute() {
		val progressJson = binderyAudiobookProgressJsonWithUpdate(
			json = "",
			progress = BinderyAudiobookPlaybackProgress(
				bookId = "3816",
				versionRowId = "88",
				trackIndex = 1,
				mediaId = "readaloud:chapter-02",
				positionMs = 600_000L,
				durationMs = 1_800_000L,
				updatedAtMs = 200L
			)
		)

		val items = binderyContinueListeningItems(
			progresses = binderyAudiobookProgressEntries(progressJson),
			manifestsByBookId = mapOf(
				"3816" to BinderyManifest(
					id = "urn:bindery:book:3816",
					title = "The Hobbit",
					images = listOf(BinderyLink(href = "/opds/books/3816/cover"))
				)
			),
			audiobookDetailsById = mapOf(
				"88" to BinderyAudiobookVersion(
					id = 88,
					bookId = 3816,
					title = "The Hobbit",
					narrator = "Andy Serkis",
					coverUrl = "/api/v1/audiobooks/88/cover"
				)
			)
		)

		assertEquals(
			listOf(
				BinderyContinueListeningItem(
					key = "continue-listening:3816:88",
					bookId = "3816",
					audiobookId = "88",
					title = "The Hobbit",
					subtitle = "Andy Serkis / Track 2 / 10m",
					imageHref = "/api/v1/audiobooks/88/cover",
					updatedAtMs = 200L,
					destination = Screen.BinderyAudiobookPlayer(
						bookId = "3816",
						title = "The Hobbit",
						audiobookId = "88"
					)
				)
			),
			items
		)
	}

	@Test
	fun continueListeningItemsIncludeWhispersyncCompanionProgressWhenPlaybackProgressIsMissing() {
		val companionJson = binderyWhispersyncCompanionProgressJsonWithUpdate(
			json = "",
			progress = BinderyWhispersyncCompanionProgress(
				bookId = "3816",
				ebookResourceHref = "/opds/books/3816/resources/ebook-435",
				audiobookId = "69",
				audiobookBookFileId = "694",
				artifactId = "3",
				progressFraction = 0.62,
				updatedAtMs = 300L
			)
		)

		val items = binderyContinueListeningItems(
			progresses = emptyList(),
			companionProgresses = binderyWhispersyncCompanionProgressEntries(companionJson),
			manifestsByBookId = mapOf(
				"3816" to BinderyManifest(id = "urn:bindery:book:3816", title = "The Hobbit")
			),
			audiobookDetailsById = mapOf("69" to andySerkis())
		)

		assertEquals(1, items.size)
		assertEquals("continue-listening:3816:69", items.single().key)
		assertEquals("The Hobbit", items.single().title)
		assertEquals("Andy Serkis / Whispersync / 62%", items.single().subtitle)
		assertEquals(
			Screen.BinderyAudiobookPlayer(
				bookId = "3816",
				title = "The Hobbit",
				audiobookId = "69"
			),
			items.single().destination
		)
	}

	@Test
	fun continueListeningItemsPreferRealPlaybackProgressOverCompanionProgress() {
		val playback = BinderyAudiobookPlaybackProgress(
			bookId = "3816",
			versionRowId = "69",
			trackIndex = 2,
			positionMs = 900_000L,
			durationMs = 1_800_000L,
			updatedAtMs = 200L
		)
		val companion = BinderyWhispersyncCompanionProgress(
			bookId = "3816",
			ebookResourceHref = "/opds/books/3816/resources/ebook-435",
			audiobookId = "69",
			audiobookBookFileId = "694",
			artifactId = "3",
			progressFraction = 0.62,
			updatedAtMs = 300L
		)

		val items = binderyContinueListeningItems(
			progresses = listOf(playback),
			companionProgresses = listOf(companion),
			manifestsByBookId = mapOf(
				"3816" to BinderyManifest(id = "urn:bindery:book:3816", title = "The Hobbit")
			),
			audiobookDetailsById = mapOf("69" to andySerkis())
		)

		assertEquals(1, items.size)
		assertEquals("Andy Serkis / Track 3 / 15m", items.single().subtitle)
	}

	@Test
	fun readerProgressCreatesWhispersyncCompanionProgressForSelectedAudiobook() {
		val reader = Screen.Reader(
			title = "The Hobbit",
			publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
			bookId = "3816",
			resourceHref = "/opds/books/3816/resources/ebook-435",
			kind = ReaderPublicationKind.Ebook,
			whispersyncArtifactId = "3",
			whispersyncAudiobookId = "69",
			whispersyncAudiobookBookFileId = "694",
			whispersyncAudiobookTitle = "Andy Serkis"
		)
		val progress = BinderyReadingProgress(
			bookId = "3816",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3816/resources/ebook-435",
			progressFraction = 0.62
		)

		assertEquals(
			BinderyWhispersyncCompanionProgress(
				bookId = "3816",
				ebookResourceHref = "/opds/books/3816/resources/ebook-435",
				audiobookId = "69",
				audiobookBookFileId = "694",
				artifactId = "3",
				progressFraction = 0.62,
				updatedAtMs = 500L
			),
			binderyWhispersyncCompanionProgressForReader(
				reader = reader,
				progress = progress,
				updatedAtMs = 500L
			)
		)
	}

	@Test
	fun readerProgressCreatesWhispersyncCompanionProgressWithExactAudioTarget() {
		val reader = Screen.Reader(
			title = "The Hobbit",
			publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
			bookId = "3816",
			resourceHref = "/opds/books/3816/resources/ebook-435",
			kind = ReaderPublicationKind.Ebook,
			whispersyncArtifactId = "3",
			whispersyncAudiobookId = "69",
			whispersyncAudiobookBookFileId = "694",
			whispersyncAudiobookTitle = "Andy Serkis"
		)
		val progress = BinderyReadingProgress(
			bookId = "3816",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3816/resources/ebook-435",
			progressFraction = 0.62
		)
		val audioTarget = WhispersyncAudioSeekTarget(
			audioResource = "Audio/chapter-09.m4b",
			positionMs = 42_500L,
			segment = WhispersyncSegment(
				id = "seg-88",
				audioResource = "Audio/chapter-09.m4b",
				startMs = 42_500L,
				endMs = 45_000L,
				textHref = "Text/chapter-09.xhtml",
				textStart = 1200,
				textEnd = 1280,
				label = "Chapter IX"
			)
		)

		assertEquals(
			BinderyWhispersyncCompanionProgress(
				bookId = "3816",
				ebookResourceHref = "/opds/books/3816/resources/ebook-435",
				audiobookId = "69",
				audiobookBookFileId = "694",
				artifactId = "3",
				progressFraction = 0.62,
				audioResource = "Audio/chapter-09.m4b",
				audioPositionMs = 42_500L,
				updatedAtMs = 500L
			),
			binderyWhispersyncCompanionProgressForReader(
				reader = reader,
				progress = progress,
				updatedAtMs = 500L,
				audioSeekTarget = audioTarget
			)
		)
	}

	@Test
	fun continueReadingItemsUseCachedBookVersionRowsAndReaderRoute() {
		val progressJson = encodeReaderReadingProgress(
			listOf(
				BinderyReadingProgress(
					bookId = "3816",
					kind = BinderyReadingProgressKind.Ebook,
					resourceHref = "/opds/books/3816/resources/ebook-435",
					progressFraction = 0.62
				)
			)
		)

		val items = binderyContinueReadingItems(
			progresses = paige.navic.reader.decodeReaderReadingProgress(progressJson),
			manifestsByBookId = mapOf(
				"3816" to BinderyManifest(
					id = "urn:bindery:book:3816",
					title = "The Hobbit",
					images = listOf(BinderyLink(href = "/opds/books/3816/cover"))
				)
			),
			resourcesByBookId = mapOf("3816" to hobbitResources()),
			audiobookVersionsByBookId = mapOf("3816" to listOf(andySerkis())),
			syncByBookId = mapOf("3816" to hobbitSync()),
			languageFilter = "eng",
			opdsBaseUrl = "https://bindery.local/opds"
		)

		assertEquals(1, items.size)
		assertEquals("continue-reading:3816:/opds/books/3816/resources/ebook-435", items.single().key)
		assertEquals("The Hobbit", items.single().title)
		assertEquals("Houghton Mifflin Harcourt / 62%", items.single().subtitle)
		assertEquals("/opds/books/3816/cover", items.single().imageHref)
		assertEquals(
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
				bookId = "3816",
				resourceHref = "/opds/books/3816/resources/ebook-435",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false
			),
			items.single().ebookDestination
		)
	}

	@Test
	fun continueReadingLaunchOpensPlainEbookWhenThereAreNoReadyMatches() {
		val item = continueReadingItem(sync = BinderyBookSync(bookId = 3816))

		assertEquals(
			BinderyContinueReadingLaunchDecision.OpenEbook(item.ebookDestination),
			binderyContinueReadingLaunchDecision(item)
		)
	}

	@Test
	fun continueReadingLaunchAsksWhenThereIsOneReadyWhispersyncMatch() {
		val item = continueReadingItem(sync = hobbitSync())
		val decision = assertIs<BinderyContinueReadingLaunchDecision.AskWhispersync>(
			binderyContinueReadingLaunchDecision(item)
		)

		assertEquals(item.ebookDestination, decision.ebookDestination)
		assertEquals(1, decision.matches.size)
		assertEquals("Andy Serkis", decision.matches.single().oppositeTitle)
	}

	@Test
	fun continueReadingLaunchAsksWithAllMatchesWhenThereAreMultipleReadyWhispersyncMatches() {
		val item = continueReadingItem(sync = hobbitSync(extraReadyAudiobook = true))
		val decision = assertIs<BinderyContinueReadingLaunchDecision.AskWhispersync>(
			binderyContinueReadingLaunchDecision(item)
		)

		assertEquals(item.ebookDestination, decision.ebookDestination)
		assertEquals(listOf("Andy Serkis", "Rob Inglis"), decision.matches.map { it.oppositeTitle })
	}

	private fun continueReadingItem(sync: BinderyBookSync): BinderyContinueReadingItem =
		binderyContinueReadingItems(
			progresses = listOf(
				BinderyReadingProgress(
					bookId = "3816",
					kind = BinderyReadingProgressKind.Ebook,
					resourceHref = "/opds/books/3816/resources/ebook-435",
					progressFraction = 0.62
				)
			),
			manifestsByBookId = mapOf(
				"3816" to BinderyManifest(id = "urn:bindery:book:3816", title = "The Hobbit")
			),
			resourcesByBookId = mapOf("3816" to hobbitResources()),
			audiobookVersionsByBookId = mapOf(
				"3816" to listOfNotNull(
					andySerkis(),
					if (sync.syncPairs.any { it.audiobookBookFileId == 695L }) {
						robInglis()
					} else {
						null
					}
				)
			),
			syncByBookId = mapOf("3816" to sync),
			languageFilter = "eng",
			opdsBaseUrl = "https://bindery.local/opds"
		).single()

	private fun hobbitResources(): BinderyResourceCatalog =
		BinderyResourceCatalog(
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

	private fun andySerkis(): BinderyAudiobookVersion =
		BinderyAudiobookVersion(
			id = 69,
			bookId = 3816,
			bookFileId = 694,
			title = "The Hobbit",
			language = "English",
			narrator = "Andy Serkis"
		)

	private fun robInglis(): BinderyAudiobookVersion =
		BinderyAudiobookVersion(
			id = 70,
			bookId = 3816,
			bookFileId = 695,
			title = "The Hobbit",
			language = "English",
			narrator = "Rob Inglis"
		)

	private fun hobbitSync(extraReadyAudiobook: Boolean = false): BinderyBookSync =
		BinderyBookSync(
			bookId = 3816,
			syncPairs = listOfNotNull(
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
}
