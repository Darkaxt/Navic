package paige.navic.ui.screens.bindery

import paige.navic.reader.ReaderPublicationFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinderyPrefetchReaderDestinationsTest {
	private val opdsBaseUrl = "https://bindery.example.com/opds"
	private val aspectRatio = 0.62

	private val ebookRow = BinderyBookVersionRow(
		id = "/opds/books/7/resources/ebook-1",
		kind = BinderyBookVersionKind.Ebook,
		title = "EPUB",
		subtitle = null,
		ebookBookFileId = "11",
		fullscreenCoverHref = "/api/v1/books/7/generated/fullscreen-cover",
		syncMatches = listOf(
			BinderyWhispersyncMatch(
				oppositeTitle = "Narrator",
				oppositeKind = BinderyBookVersionKind.Audiobook,
				artifactId = "17",
				sidecarHref = "/api/v1/sync/artifacts/17",
				coveragePercent = 90,
				scorePercent = 95,
				oppositeAudiobookId = "5",
				oppositeAudiobookBookFileId = "13"
			)
		)
	)
	private val audiobookRow = BinderyBookVersionRow(
		id = "audiobook-version:5",
		kind = BinderyBookVersionKind.Audiobook,
		title = "Narrator",
		subtitle = null,
		audiobookId = "5",
		audiobookBookFileId = "13",
		syncMatches = listOf(
			BinderyWhispersyncMatch(
				oppositeTitle = "EPUB",
				oppositeKind = BinderyBookVersionKind.Ebook,
				artifactId = "17",
				sidecarHref = "/api/v1/sync/artifacts/17",
				coveragePercent = 90,
				scorePercent = 95,
				oppositeEbookResourceHref = "/opds/books/7/resources/ebook-1",
				oppositeEbookBookFileId = "11",
				oppositeEbookFormat = ReaderPublicationFormat.Epub
			)
		)
	)
	private val pdfRow = BinderyBookVersionRow(
		id = "/opds/books/7/resources/ebook-pdf",
		kind = BinderyBookVersionKind.Ebook,
		title = "PDF",
		subtitle = null,
		format = ReaderPublicationFormat.Pdf
	)
	private val readaloudRow = BinderyBookVersionRow(
		id = "/opds/books/7/resources/readaloud-1",
		kind = BinderyBookVersionKind.Readaloud,
		title = "Readaloud",
		subtitle = null
	)
	private val downloadOnlyRow = BinderyBookVersionRow(
		id = "/opds/books/7/resources/ebook-docx",
		kind = BinderyBookVersionKind.Ebook,
		title = "DOCX",
		subtitle = null,
		readerSupported = false,
		downloadExtension = "docx"
	)

	@Test
	fun prefetchDestinationsEqualEveryEbookTapPathDestination() {
		val destinations = binderyPrefetchReaderDestinations(
			rows = listOf(readaloudRow, audiobookRow, ebookRow, pdfRow, downloadOnlyRow),
			bookId = "7",
			bookTitle = "Book",
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = aspectRatio
		)

		val audiobookWhispersyncTap = binderyWhispersyncReaderDestinationForRowMatch(
			row = audiobookRow,
			match = audiobookRow.syncMatches.single(),
			bookId = "7",
			bookTitle = "Book",
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = aspectRatio
		)
		val ebookTap = binderyReaderDestinationForVersionRow(
			row = ebookRow,
			bookId = "7",
			bookTitle = "Book",
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = aspectRatio
		)
		val ebookWithAudiobookTap = binderyWhispersyncReaderDestinationForMatch(
			ebookRow = ebookRow,
			match = ebookRow.whispersyncAudiobookLaunchMatches().single(),
			bookId = "7",
			bookTitle = "Book",
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = aspectRatio
		)
		val pdfTap = binderyReaderDestinationForVersionRow(
			row = pdfRow,
			bookId = "7",
			bookTitle = "Book",
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = aspectRatio
		)

		assertEquals(
			listOfNotNull(audiobookWhispersyncTap, ebookTap, ebookWithAudiobookTap, pdfTap),
			destinations
		)
		assertEquals(4, destinations.size)
		assertTrue(destinations.none { it.resourceHref == readaloudRow.id || it.resourceHref == downloadOnlyRow.id })
		assertEquals(
			listOf(true, false, true, false),
			destinations.map { it.whispersyncSidecarUrl != null }
		)
	}

	@Test
	fun continueReadingPrefetchUsesTheLaunchDecisionDestinations() {
		val ebookDestination = binderyReaderDestinationForVersionRow(
			row = ebookRow,
			bookId = "7",
			bookTitle = "Book",
			opdsBaseUrl = opdsBaseUrl
		)!!
		val item = BinderyContinueReadingItem(
			key = "continue-reading:7:${ebookRow.id}",
			bookId = "7",
			resourceHref = ebookRow.id,
			title = "Book",
			subtitle = null,
			imageHref = null,
			updatedAtMs = 1L,
			row = ebookRow,
			ebookDestination = ebookDestination
		)

		val destinations = binderyContinueReadingPrefetchDestinations(
			items = listOf(item),
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = aspectRatio
		)

		val decision = binderyContinueReadingLaunchDecision(
			item = item,
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = aspectRatio
		) as BinderyContinueReadingLaunchDecision.AskWhispersync
		assertEquals(
			listOfNotNull(
				decision.ebookDestination,
				binderyContinueReadingWhispersyncDestination(
					decision = decision,
					match = decision.matches.single(),
					opdsBaseUrl = opdsBaseUrl,
					fullscreenCoverTargetAspectRatio = aspectRatio
				)
			),
			destinations
		)
		assertEquals(2, destinations.size)
	}
}
