package paige.navic.ui.screens.bindery

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceMetadata
import paige.navic.reader.ReadaloudPlaybackPosition

class BinderyAudiobookPlayerPolicyTest {
	@Test
	fun mainTransportUsesTimeSkipsAroundPlayPause() {
		assertEquals(
			listOf(
				BinderyAudiobookTransportControl.SeekBackward30,
				BinderyAudiobookTransportControl.SeekBackward10,
				BinderyAudiobookTransportControl.PlayPause,
				BinderyAudiobookTransportControl.SeekForward10,
				BinderyAudiobookTransportControl.SeekForward30
			),
			binderyAudiobookTransportControls()
		)
	}

	@Test
	fun selectedEditionFiltersChapterIndexByBookFileId() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			readingOrder = listOf(
				audioItem("one-a", "Book One - Chapter 1", "file-one"),
				audioItem("two-a", "Book Two - Chapter 1", "file-two"),
				audioItem("one-b", "Book One - Chapter 2", "file-one"),
				BinderyReadingOrderItem(
					href = "cover.jpg",
					title = "Cover",
					type = "image/jpeg"
				)
			)
		)

		val chapters = binderyAudiobookChapters(
			manifest = manifest,
			versionRowId = "audiobook:file-one"
		)

		assertEquals(listOf("Book One - Chapter 1", "Book One - Chapter 2"), chapters.map { it.title })
		assertEquals(listOf(0, 1), chapters.map { it.index })
		assertEquals(listOf("one-a", "one-b"), chapters.map { it.href })
	}

	@Test
	fun chapterIndexFallsBackToAllAudioWhenEditionIsUnknown() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			readingOrder = listOf(
				audioItem("one-a", "Chapter 1", "file-one"),
				audioItem("two-a", "Chapter 2", "file-two")
			)
		)

		val chapters = binderyAudiobookChapters(
			manifest = manifest,
			versionRowId = "audiobook:missing"
		)

		assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it.title })
	}

	@Test
	fun playbackPlanUsesRememberedPositionForSelectedEdition() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			readingOrder = listOf(
				audioItem("one-a", "Book One - Chapter 1", "file-one"),
				audioItem("one-b", "Book One - Chapter 2", "file-one"),
				audioItem("two-a", "Book Two - Chapter 1", "file-two")
			)
		)

		val plan = binderyAudiobookPlaybackPlan(
			manifest = manifest,
			versionRowId = "audiobook:file-one",
			opdsBaseUrl = "https://bindery.test/opds",
			requestHeaders = emptyMap(),
			resumeProgress = BinderyAudiobookPlaybackProgress(
				bookId = "book-1",
				versionRowId = "audiobook:file-one",
				trackIndex = 1,
				mediaId = "readaloud:one-b",
				positionMs = 42_000L,
				durationMs = 60_000L,
				updatedAtMs = 100L
			)
		)

		assertEquals(1, plan.startTrackIndex)
		assertEquals(42_000L, plan.startPositionMs)
	}

	@Test
	fun playbackPlanUsesRouteBookIdForAudiobookScopedManifestProgress() {
		val manifest = BinderyManifest(
			id = "audiobook-69",
			title = "Book",
			readingOrder = listOf(
				audioItem("one-a", "Book One - Chapter 1", "file-one"),
				audioItem("one-b", "Book One - Chapter 2", "file-one")
			)
		)

		val plan = binderyAudiobookPlaybackPlan(
			manifest = manifest,
			versionRowId = "69",
			opdsBaseUrl = "https://bindery.test/opds",
			requestHeaders = emptyMap(),
			resumeProgress = BinderyAudiobookPlaybackProgress(
				bookId = "book-1",
				versionRowId = "69",
				trackIndex = 1,
				mediaId = "readaloud:one-b",
				positionMs = 42_000L,
				durationMs = 60_000L,
				updatedAtMs = 100L
			),
			progressBookId = "book-1"
		)

		assertEquals(1, plan.startTrackIndex)
		assertEquals(42_000L, plan.startPositionMs)
	}

	@Test
	fun progressStoreKeepsIndependentPositionsPerEdition() {
		val first = BinderyAudiobookPlaybackProgress(
			bookId = "book-1",
			versionRowId = "audiobook:file-one",
			trackIndex = 0,
			mediaId = "readaloud:one-a",
			positionMs = 5_000L,
			durationMs = 60_000L,
			updatedAtMs = 100L
		)
		val updatedFirst = first.copy(positionMs = 15_000L, updatedAtMs = 200L)
		val second = BinderyAudiobookPlaybackProgress(
			bookId = "book-1",
			versionRowId = "audiobook:file-two",
			trackIndex = 0,
			mediaId = "readaloud:two-a",
			positionMs = 25_000L,
			durationMs = 60_000L,
			updatedAtMs = 300L
		)

		val stored = binderyAudiobookProgressJsonWithUpdate(
			binderyAudiobookProgressJsonWithUpdate(
				binderyAudiobookProgressJsonWithUpdate("", first),
				second
			),
			updatedFirst
		)

		assertEquals(
			updatedFirst,
			binderyAudiobookSavedProgress(stored, "book-1", "audiobook:file-one")
		)
		assertEquals(
			second,
			binderyAudiobookSavedProgress(stored, "book-1", "audiobook:file-two")
		)
	}

	@Test
	fun finalTrackFinishedPositionRestartsAudiobook() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			readingOrder = listOf(
				audioItem("one-a", "Book One - Chapter 1", "file-one"),
				audioItem("one-b", "Book One - Chapter 2", "file-one")
			)
		)

		val plan = binderyAudiobookPlaybackPlan(
			manifest = manifest,
			versionRowId = "audiobook:file-one",
			opdsBaseUrl = "https://bindery.test/opds",
			requestHeaders = emptyMap(),
			resumeProgress = BinderyAudiobookPlaybackProgress(
				bookId = "book-1",
				versionRowId = "audiobook:file-one",
				trackIndex = 1,
				mediaId = "readaloud:one-b",
				positionMs = 59_500L,
				durationMs = 60_000L,
				updatedAtMs = 100L
			)
		)

		assertEquals(0, plan.startTrackIndex)
		assertEquals(0L, plan.startPositionMs)
	}

	@Test
	fun playbackPositionBecomesEditionProgress() {
		val progress = binderyAudiobookProgressForPosition(
			bookId = "book-1",
			versionRowId = "audiobook:file-one",
			position = ReadaloudPlaybackPosition(
				sessionId = "book-1",
				trackIndex = 2,
				mediaId = "readaloud:chapter-3",
				positionMs = 12_345L,
				durationMs = 60_000L,
				isPlaying = true,
				playbackSpeed = 1.25f
			),
			updatedAtMs = 500L
		)

		assertEquals(
			BinderyAudiobookPlaybackProgress(
				bookId = "book-1",
				versionRowId = "audiobook:file-one",
				trackIndex = 2,
				mediaId = "readaloud:chapter-3",
				positionMs = 12_345L,
				durationMs = 60_000L,
				updatedAtMs = 500L
			),
			progress
		)
	}

	@Test
	fun coverHrefPrefersAssociatedAudiobookFindingCover() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:book-1",
			title = "Book",
			images = listOf(BinderyLink(href = "/opds/books/book-1/cover")),
			readingOrder = listOf(audioItem("one-a", "Chapter 1", "file-one"))
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				findingPublication(
					findingId = "wrong",
					bookId = "book-1",
					bookFileId = "other-file",
					coverUrl = "/opds/findings/wrong/cover"
				),
				findingPublication(
					findingId = "match",
					bookId = "/opds/books/book-1",
					bookFileId = "file-one",
					coverUrl = "/opds/findings/match/cover"
				)
			)
		)

		assertEquals(
			"/opds/findings/match/cover",
			binderyAudiobookCoverHref(
				manifest = manifest,
				versionRowId = "audiobook:file-one",
				findingsCatalog = findings
			)
		)
	}

	@Test
	fun coverHrefUsesVersionRowBookFileIdWhenManifestHasNoReadingOrder() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:book-1",
			title = "Book",
			images = listOf(BinderyLink(href = "/opds/books/book-1/cover")),
			readingOrder = emptyList()
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				findingPublication(
					findingId = "match",
					bookId = "book-1",
					bookFileId = "file-one",
					coverUrl = "/opds/findings/match/cover"
				)
			)
		)

		assertEquals(
			"/opds/findings/match/cover",
			binderyAudiobookCoverHref(
				manifest = manifest,
				versionRowId = "audiobook:file-one",
				findingsCatalog = findings
			)
		)
	}

	@Test
	fun coverSelectionExposesAssociatedAudiobookBayFindingForProviderArtwork() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:book-1",
			title = "Book",
			images = listOf(BinderyLink(href = "/opds/books/book-1/cover")),
			readingOrder = emptyList()
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				findingPublication(
					findingId = "match",
					bookId = "book-1",
					bookFileId = "file-one",
					coverUrl = "https://assets.hardcover.app/edition/book-cover.jpg",
					providerKind = "audiobookbay",
					sourceUrl = "https://audiobookbay.lu/abss/the-hobbit/"
				)
			)
		)

		val selection = binderyAudiobookCoverSelection(
			manifest = manifest,
			versionRowId = "audiobook:file-one",
			findingsCatalog = findings
		)

		assertEquals("match", selection.finding?.findingId)
		assertEquals("audiobookbay", selection.finding?.providerKind)
		assertEquals("https://audiobookbay.lu/abss/the-hobbit/", selection.finding?.sourceUrl)
		assertEquals("https://assets.hardcover.app/edition/book-cover.jpg", selection.fallbackCoverHref)
	}

	@Test
	fun coverHrefFallsBackToFindingPublicationImageThenBookCover() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			images = listOf(BinderyLink(href = "/opds/books/book-1/cover")),
			readingOrder = listOf(audioItem("one-a", "Chapter 1", "file-one"))
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				findingPublication(
					findingId = "match",
					bookId = "book-1",
					bookFileId = "file-one",
					coverUrl = null,
					imageHref = "/opds/findings/match/image"
				)
			)
		)

		assertEquals(
			"/opds/findings/match/image",
			binderyAudiobookCoverHref(
				manifest = manifest,
				versionRowId = "audiobook:file-one",
				findingsCatalog = findings
			)
		)
		assertEquals(
			"/opds/books/book-1/cover",
			binderyAudiobookCoverHref(
				manifest = manifest,
				versionRowId = "audiobook:file-one",
				findingsCatalog = null
			)
		)
	}

	@Test
	fun coverHrefUsesSameImagePriorityAsFindingDetail() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			images = listOf(BinderyLink(href = "/opds/books/book-1/cover")),
			readingOrder = listOf(audioItem("one-a", "Chapter 1", "file-one"))
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				findingPublication(
					findingId = "match",
					bookId = "book-1",
					bookFileId = "file-one",
					coverUrl = "https://assets.hardcover.app/edition/book-fallback.jpg",
					imageHref = "/opds/findings/match/cover"
				)
			)
		)

		assertEquals(
			"/opds/findings/match/cover",
			binderyAudiobookCoverHref(
				manifest = manifest,
				versionRowId = "audiobook:file-one",
				findingsCatalog = findings
			)
		)
	}

	@Test
	fun coverHrefIgnoresUnrelatedOrNonAudiobookFindings() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			images = listOf(BinderyLink(href = "/opds/books/book-1/cover")),
			readingOrder = listOf(audioItem("one-a", "Chapter 1", "file-one"))
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				findingPublication(
					findingId = "ebook",
					bookId = "book-1",
					bookFileId = "file-one",
					coverUrl = "/opds/findings/ebook/cover",
					mediaType = "ebook"
				),
				findingPublication(
					findingId = "other-book",
					bookId = "book-2",
					bookFileId = "file-one",
					coverUrl = "/opds/findings/other-book/cover"
				)
			)
		)

		assertEquals(
			"/opds/books/book-1/cover",
			binderyAudiobookCoverHref(
				manifest = manifest,
				versionRowId = "audiobook:file-one",
				findingsCatalog = findings
			)
		)
	}

	@Test
	fun coverHrefCanMatchAssociatedFindingUsingRouteBookId() {
		val manifest = BinderyManifest(
			id = null,
			title = "Book",
			images = listOf(BinderyLink(href = "/opds/books/book-1/cover")),
			readingOrder = listOf(audioItem("one-a", "Chapter 1", "file-one"))
		)
		val findings = BinderyCatalog(
			title = "Findings",
			publications = listOf(
				findingPublication(
					findingId = "match",
					bookId = "book-1",
					bookFileId = "file-one",
					coverUrl = "/opds/findings/match/cover"
				)
			)
		)

		assertEquals(
			"/opds/findings/match/cover",
			binderyAudiobookCoverHref(
				manifest = manifest,
				versionRowId = "audiobook:file-one",
				findingsCatalog = findings,
				routeBookId = "book-1"
			)
		)
	}

	private fun audioItem(
		href: String,
		title: String,
		bookFileId: String
	): BinderyReadingOrderItem =
		BinderyReadingOrderItem(
			href = href,
			title = title,
			type = "audio/mpeg",
			durationSeconds = 60.0,
			properties = mapOf("bookFileId" to bookFileId),
			metadata = BinderyResourceMetadata(resourceKey = href)
		)

	private fun findingPublication(
		findingId: String,
		bookId: String,
		bookFileId: String,
		coverUrl: String?,
		imageHref: String? = null,
		mediaType: String = "audiobook",
		providerKind: String? = null,
		sourceUrl: String? = null
	): BinderyPublication =
		BinderyPublication(
			id = "urn:bindery:finding:$findingId",
			title = "Finding $findingId",
			images = imageHref?.let { listOf(BinderyLink(href = it)) }.orEmpty(),
			finding = BinderyFindingMetadata(
				findingId = findingId,
				mediaType = mediaType,
				providerKind = providerKind,
				sourceUrl = sourceUrl,
				coverUrl = coverUrl,
				mappings = listOf(
					BinderyFindingMapping(
						bookId = bookId,
						bookFileId = bookFileId,
						mediaType = mediaType
					)
				)
			)
		)
}
