package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind

class ReaderProgressSyncTest {
	@Test
	fun progressSaveGateIgnoresStartupRelocationsUntilPublicationReady() {
		val startupLocator = ReaderLocator(href = "cover.xhtml", progress = 0.0)
		val resumedLocator = ReaderLocator(href = "chapter-04.xhtml", progress = 0.62)
		val initial = ReaderProgressSaveGate()
		val startup = initial.onReaderEvent(ReaderBridgeEvent.LocationChanged(startupLocator))
		val ready = startup.state.onReaderEvent(ReaderBridgeEvent.PublicationReady)
		val resumed = ready.state.onReaderEvent(ReaderBridgeEvent.LocationChanged(resumedLocator))

		assertEquals(null, startup.locatorToSave)
		assertEquals(false, startup.state.publicationReady)
		assertEquals(null, ready.locatorToSave)
		assertEquals(true, ready.state.publicationReady)
		assertEquals(resumedLocator, resumed.locatorToSave)
		assertEquals(true, resumed.state.publicationReady)
	}

	@Test
	fun progressSaveGateIgnoresFirstPostReadyCoverPlaceholderBeforeSavingResumeLocation() {
		val coverLocator = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.0)
		val resumedLocator = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.62
		)
		val ready = ReaderProgressSaveGate()
			.onReaderEvent(ReaderBridgeEvent.PublicationReady)
			.state
		val startupCover = ready.onReaderEvent(ReaderBridgeEvent.LocationChanged(coverLocator))
		val resumed = startupCover.state.onReaderEvent(ReaderBridgeEvent.LocationChanged(resumedLocator))
		val laterCover = resumed.state.onReaderEvent(ReaderBridgeEvent.LocationChanged(coverLocator))

		assertEquals(null, startupCover.locatorToSave)
		assertEquals(resumedLocator, resumed.locatorToSave)
		assertEquals(coverLocator, laterCover.locatorToSave)
	}

	@Test
	fun progressSaveGateIgnoresRepeatedPostReadyCoverPlaceholdersBeforeSavingResumeLocation() {
		val coverLocator = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.0)
		val navLocator = ReaderLocator(href = "EPUB/nav.xhtml", progress = 0.01)
		val resumedLocator = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.62
		)
		val ready = ReaderProgressSaveGate()
			.onReaderEvent(ReaderBridgeEvent.PublicationReady)
			.state

		val startupCover = ready.onReaderEvent(ReaderBridgeEvent.LocationChanged(coverLocator))
		val startupNav = startupCover.state.onReaderEvent(ReaderBridgeEvent.LocationChanged(navLocator))
		val resumed = startupNav.state.onReaderEvent(ReaderBridgeEvent.LocationChanged(resumedLocator))
		val laterCover = resumed.state.onReaderEvent(ReaderBridgeEvent.LocationChanged(coverLocator))

		assertEquals(null, startupCover.locatorToSave)
		assertEquals(null, startupNav.locatorToSave)
		assertEquals(resumedLocator, resumed.locatorToSave)
		assertEquals(coverLocator, laterCover.locatorToSave)
	}

	@Test
	fun progressSaveGateTreatsLowProgressCoverHrefAsStartupPlaceholder() {
		val coverLocator = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.012)
		val resumedLocator = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.62
		)
		val ready = ReaderProgressSaveGate()
			.onReaderEvent(ReaderBridgeEvent.PublicationReady)
			.state
		val startupCover = ready.onReaderEvent(ReaderBridgeEvent.LocationChanged(coverLocator))
		val resumed = startupCover.state.onReaderEvent(ReaderBridgeEvent.LocationChanged(resumedLocator))

		assertEquals(null, startupCover.locatorToSave)
		assertEquals(resumedLocator, resumed.locatorToSave)
	}

	@Test
	fun progressSaveGateResetsForNewPublication() {
		val gate = ReaderProgressSaveGate(publicationReady = true)
		val reset = gate.reset()

		assertEquals(false, reset.publicationReady)
		assertEquals(
			null,
			reset.onReaderEvent(
				ReaderBridgeEvent.LocationChanged(ReaderLocator(href = "cover.xhtml", progress = 0.0))
			).locatorToSave
		)
	}

	@Test
	fun binderyProgressBuildsReaderStartLocatorWithCfiPreferredOverHrefFragment() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			alias = "darko",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			textHref = "EPUB/Text/chapter-03.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			fragmentId = "p-42",
			progressFraction = 0.34
		)

		assertEquals(
			ReaderLocator(
				href = "EPUB/Text/chapter-03.xhtml#p-42",
				cfi = "epubcfi(/6/8!/4/1:0)",
				progress = 0.34
			),
			progress.toReaderStartLocator()
		)
	}

	@Test
	fun binderyProgressOnlyBuildsReaderStartLocatorForMatchingResourceAndKind() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progressFraction = 0.34
		)

		assertEquals(
			ReaderLocator(cfi = "epubcfi(/6/8!/4/1:0)", progress = 0.34),
			progress.toReaderStartLocatorFor(
				resourceHref = "/opds/books/3693/resources/ebook-1",
				kind = ReaderPublicationKind.Ebook
			)
		)
		assertEquals(
			null,
			progress.toReaderStartLocatorFor(
				resourceHref = "/opds/books/3693/resources/readaloud-1",
				kind = ReaderPublicationKind.Readaloud
			)
		)
	}

	@Test
	fun binderyProgressMatchesAbsoluteAndRelativeResourceHrefsByCanonicalPath() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "https://bindery.local/opds/books/3693/resources/ebook-1?download=1#ignored",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progressFraction = 0.34
		)

		assertEquals(
			ReaderLocator(cfi = "epubcfi(/6/8!/4/1:0)", progress = 0.34),
			progress.toReaderStartLocatorFor(
				resourceHref = "/opds/books/3693/resources/ebook-1",
				kind = ReaderPublicationKind.Ebook
			)
		)
	}

	@Test
	fun progressOnlyLocatorCanResumeFixedLayoutPublications() {
		val progress = BinderyReadingProgress(
			bookId = "3816",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3816/resources/ebook-abb-pdf",
			progressFraction = 0.42
		)

		assertEquals(
			ReaderLocator(progress = 0.42),
			progress.toReaderStartLocator()
		)
	}

	@Test
	fun readerLocatorSavesEbookProgressAsCfiTextHrefAndFragment() {
		val progress = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml#note-9",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.62
		).toBinderyReadingProgress(
			bookId = "3693",
			resourceHref = "/opds/books/3693/resources/ebook-1",
			kind = ReaderPublicationKind.Ebook,
			alias = "darko"
		)

		assertEquals(
			BinderyReadingProgress(
				bookId = "3693",
				alias = "darko",
				kind = BinderyReadingProgressKind.Ebook,
				resourceHref = "/opds/books/3693/resources/ebook-1",
				textHref = "EPUB/Text/chapter-04.xhtml",
				cfi = "epubcfi(/6/10!/4/3:12)",
				fragmentId = "note-9",
				progressFraction = 0.62
			),
			progress
		)
	}

	@Test
	fun readerLocatorSavesReadaloudProgressAsReadaloudKindAndClampsFraction() {
		val progress = ReaderLocator(
			href = "chapter-01.xhtml",
			progress = 1.4
		).toBinderyReadingProgress(
			bookId = "3693",
			resourceHref = "/opds/books/3693/resources/readaloud-1",
			kind = ReaderPublicationKind.Readaloud,
			alias = null
		)

		assertEquals(
			BinderyReadingProgress(
				bookId = "3693",
				kind = BinderyReadingProgressKind.Readaloud,
				resourceHref = "/opds/books/3693/resources/readaloud-1",
				textHref = "chapter-01.xhtml",
				progressFraction = 1.0
			),
			progress
		)
	}

	@Test
	fun localReadingProgressStateStoresLatestLocatorForPublication() {
		val first = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			textHref = "EPUB/Text/chapter-01.xhtml",
			cfi = "epubcfi(/6/2!/4/1:0)",
			progressFraction = 0.1
		)
		val latest = first.copy(
			textHref = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progressFraction = 0.62
		)
		val state = ReaderReadingProgressState()
			.upsert(first)
			.upsert(latest)

		assertEquals(1, state.progresses.size)
		assertEquals(
			ReaderLocator(
				href = "EPUB/Text/chapter-04.xhtml",
				cfi = "epubcfi(/6/10!/4/3:12)",
				progress = 0.62
			),
			state.progressFor(
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-1",
				kind = ReaderPublicationKind.Ebook
			)?.toReaderStartLocator()
		)
	}

	@Test
	fun localReadingProgressStateMatchesEquivalentResourceUrlForms() {
		val state = ReaderReadingProgressState(
			listOf(
				BinderyReadingProgress(
					bookId = "3693",
					kind = BinderyReadingProgressKind.Ebook,
					resourceHref = "https://bindery.local/opds/books/3693/resources/ebook-1?download=1",
					cfi = "epubcfi(/6/8!/4/1:0)",
					progressFraction = 0.34
				)
			)
		)

		assertEquals(
			ReaderLocator(cfi = "epubcfi(/6/8!/4/1:0)", progress = 0.34),
			state.progressFor(
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-1",
				kind = ReaderPublicationKind.Ebook
			)?.toReaderStartLocator()
		)
	}

	@Test
	fun remoteProgressFallsBackToProgressOnlyForSameBookKindWhenResourceChanges() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-old",
			textHref = "EPUB/Text/chapter-09.xhtml",
			cfi = "epubcfi(/6/18!/4/3:12)",
			progressFraction = 0.62
		)

		assertEquals(
			ReaderLocator(progress = 0.62),
			progress.toReaderStartLocatorForReader(
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-new",
				kind = ReaderPublicationKind.Ebook
			)
		)
	}

	@Test
	fun remoteProgressFallbackDoesNotCrossReaderKinds() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Readaloud,
			resourceHref = "/opds/books/3693/resources/readaloud-old",
			progressFraction = 0.62
		)

		assertEquals(
			null,
			progress.toReaderStartLocatorForReader(
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-new",
				kind = ReaderPublicationKind.Ebook
			)
		)
	}

	@Test
	fun localReadingProgressStatePrefersExactResourceOverSameBookFallback() {
		val fallback = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-old",
			cfi = "epubcfi(/6/4!/4/1:0)",
			progressFraction = 0.25
		)
		val exact = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-new",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progressFraction = 0.64
		)
		val state = ReaderReadingProgressState(listOf(fallback, exact))

		assertEquals(
			ReaderLocator(cfi = "epubcfi(/6/10!/4/3:12)", progress = 0.64),
			state.startLocatorFor(
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-new",
				kind = ReaderPublicationKind.Ebook
			)
		)
	}

	@Test
	fun bestReaderStartLocatorPrefersLocalProgressOverRemoteCoverPlaceholder() {
		val remote = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.0)
		val local = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.42
		)

		assertEquals(local, bestReaderStartLocator(remoteStartLocator = remote, localStartLocator = local))
	}

	@Test
	fun bestReaderStartLocatorPrefersLocalProgressWhenItIsClearlyAheadOfRemote() {
		val remote = ReaderLocator(
			href = "EPUB/Text/chapter-02.xhtml",
			cfi = "epubcfi(/6/6!/4/3:12)",
			progress = 0.21
		)
		val local = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.42
		)

		assertEquals(local, bestReaderStartLocator(remoteStartLocator = remote, localStartLocator = local))
	}

	@Test
	fun bestReaderStartLocatorKeepsRemoteWhenItIsAsRecentAsLocalProgress() {
		val remote = ReaderLocator(
			href = "EPUB/Text/chapter-06.xhtml",
			cfi = "epubcfi(/6/14!/4/3:12)",
			progress = 0.64
		)
		val local = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.42
		)

		assertEquals(remote, bestReaderStartLocator(remoteStartLocator = remote, localStartLocator = local))
	}

	@Test
	fun bestReaderStartLocatorKeepsPreciseRemoteLocatorWithoutProgressWhenItIsNotCoverLike() {
		val remote = ReaderLocator(
			href = "EPUB/Text/chapter-06.xhtml",
			cfi = "epubcfi(/6/14!/4/3:12)"
		)
		val local = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml",
			progress = 0.42
		)

		assertEquals(remote, bestReaderStartLocator(remoteStartLocator = remote, localStartLocator = local))
	}

	@Test
	fun localReadingProgressStateFallsBackToSameBookProgressOnlyWithoutOldHrefCfi() {
		val state = ReaderReadingProgressState(
			listOf(
				BinderyReadingProgress(
					bookId = "3693",
					kind = BinderyReadingProgressKind.Ebook,
					resourceHref = "/opds/books/3693/resources/ebook-old",
					textHref = "EPUB/Text/chapter-09.xhtml",
					cfi = "epubcfi(/6/18!/4/3:12)",
					progressFraction = 0.62
				)
			)
		)

		assertEquals(
			ReaderLocator(progress = 0.62),
			state.startLocatorFor(
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-new",
				kind = ReaderPublicationKind.Ebook
			)
		)
	}

	@Test
	fun localReadingProgressFallbackRequiresSavedProgressFraction() {
		val state = ReaderReadingProgressState(
			listOf(
				BinderyReadingProgress(
					bookId = "3693",
					kind = BinderyReadingProgressKind.Ebook,
					resourceHref = "/opds/books/3693/resources/ebook-old",
					textHref = "EPUB/Text/chapter-09.xhtml",
					cfi = "epubcfi(/6/18!/4/3:12)"
				)
			)
		)

		assertEquals(
			null,
			state.startLocatorFor(
				bookId = "3693",
				resourceHref = "/opds/books/3693/resources/ebook-new",
				kind = ReaderPublicationKind.Ebook
			)
		)
	}

	@Test
	fun localReadingProgressJsonRoundTripsAndIgnoresInvalidPayloads() {
		val progress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			cfi = "epubcfi(/6/8!/4/1:0)"
		)

		val decoded = decodeReaderReadingProgress(encodeReaderReadingProgress(listOf(progress)))

		assertEquals(listOf(progress), decoded)
		assertEquals(emptyList(), decodeReaderReadingProgress("not-json"))
	}
}
