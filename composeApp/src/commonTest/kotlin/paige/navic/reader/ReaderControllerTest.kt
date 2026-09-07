package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind

class ReaderControllerTest {
	@Test
	fun openPublicationMountsEngineWithoutGivingEngineChromeOwnership() {
		val publication = ReaderPublicationIdentity(
			bookId = "book-1",
			resourceHref = "publication.epub",
			kind = ReaderPublicationKind.Ebook,
			format = ReaderPublicationFormat.Epub
		)
		val settings = defaultReaderSettings().copy(
			theme = ReaderSepiaTheme,
			fontSizePercent = 117,
			tapZone = ReaderTapZoneLShaped
		).normalizedReaderSettings()
		val startLocator = ReaderLocator(
			href = "chapter-01.xhtml",
			cfi = "epubcfi(/6/2!/4/1:0)",
			progress = 0.12,
			pageIndex = 0,
			pageCount = 411
		)
		val request = ReaderEngineOpenRequest(
			publication = publication,
			url = "https://appassets.androidplatform.net/reader-cache/book-1/publication.epub",
			mediaOverlayEnabled = false,
			externalShellCover = true,
			startLocator = startLocator,
			settings = settings,
			nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
			canReturnToShellCover = true
		)

		val step = ReaderController().open(request)

		assertEquals(publication, step.controller.state.publication)
		assertEquals(ReaderPublicationFormat.Epub, step.controller.state.activeEngine)
		assertEquals(settings, step.controller.state.chrome.settings)
		assertEquals(startLocator, step.controller.state.chrome.currentLocator)
		assertTrue(step.controller.state.shellCoverVisible)
		assertFalse(step.controller.state.menuVisible)
		assertNull(step.controller.state.lastContentActionClaim)
		assertEquals(
			listOf(ReaderEngineCommand.OpenPublication(request.copy(settings = settings))),
			step.engineCommands
		)
	}

	@Test
	fun engineRelocationFeedsControllerChromeWithoutEmittingEngineCommands() {
		val controller = ReaderController().open(
			ReaderEngineOpenRequest(
				publication = ReaderPublicationIdentity(
					bookId = "book-1",
					resourceHref = "publication.epub",
					format = ReaderPublicationFormat.Epub
				),
				url = "https://appassets.androidplatform.net/reader-cache/book-1/publication.epub",
				settings = defaultReaderSettings()
			)
		).controller
		val locator = ReaderLocator(
			href = "chapter-01.xhtml",
			cfi = "epubcfi(/6/4!/4/2:0)",
			progress = 0.25,
			pageIndex = 5,
			pageCount = 411
		)

		val step = controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = locator,
				tocTitle = "Chapter 1"
			)
		)

		assertEquals(locator, step.controller.state.chrome.currentLocator)
		assertEquals("Chapter 1", step.controller.state.chrome.currentSectionTitle)
		assertEquals("Page 6 of 411 • 25%", step.controller.state.chrome.progressLabel)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun relocationAcknowledgementRequiresCurrentSessionAndResetsOnSessionOrPublicationChange() {
		val acknowledged = ReaderController().onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(pageIndex = 7),
				foliateSessionId = "session-a",
				pageTurnSettleToken = "settle-1",
				pageTurnSettleSessionId = "session-a",
				pageTurnSettleRasterGeneration = 11L,
				pageTurnSettleTextureGeneration = 13L
			)
		).controller
		assertEquals("session-a", acknowledged.state.foliateSessionId)
		assertEquals(
			ReaderPageTurnSettlementAck(
				token = "settle-1",
				pageIndex = 7,
				foliateSessionId = "session-a",
				rasterGeneration = 11L,
				textureGeneration = 13L
			),
			acknowledged.state.pageTurnSettlementAck
		)

		val mismatched = ReaderController().onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(pageIndex = 8),
				foliateSessionId = "session-a",
				pageTurnSettleToken = "settle-2",
				pageTurnSettleSessionId = "session-b",
				pageTurnSettleRasterGeneration = 17L,
				pageTurnSettleTextureGeneration = 19L
			)
		).controller
		assertNull(mismatched.state.pageTurnSettlementAck)

		val changedSession = acknowledged.copy(
			state = acknowledged.state.copy(
				activeMediaOverlayAnchorReceipt = testAnchorReceipt("session-a")
			)
		).onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(pageIndex = 9),
				foliateSessionId = "session-b"
			)
		).controller
		assertEquals("session-b", changedSession.state.foliateSessionId)
		assertNull(changedSession.state.pageTurnSettlementAck)
		assertNull(changedSession.state.activeMediaOverlayAnchorReceipt)

		val reopened = acknowledged.open(hobbitOpenRequest()).controller
		assertNull(reopened.state.foliateSessionId)
		assertNull(reopened.state.pageTurnSettlementAck)
	}

	@Test
	fun untaggedSameSessionRelocationClearsCompletedPageTurnAcknowledgement() {
		val acknowledged = ReaderController().onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(
					href = "OEBPS/Text/title.xhtml",
					pageIndex = 3,
					pageCount = 121
				),
				foliateSessionId = "session-a",
				pageTurnSettleToken = "settle-1",
				pageTurnSettleSessionId = "session-a",
				pageTurnSettleRasterGeneration = 11L,
				pageTurnSettleTextureGeneration = 13L
			)
		).controller
		val chapterOneLocator = ReaderLocator(
			href = "OEBPS/Text/chapter-01.xhtml",
			pageIndex = 10,
			pageCount = 121
		)

		val relocated = acknowledged.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = chapterOneLocator,
				foliateSessionId = "session-a",
				tocTitle = "Chapter 1"
			)
		).controller

		assertEquals(chapterOneLocator, relocated.state.chrome.currentLocator)
		assertEquals("Chapter 1", relocated.state.chrome.currentSectionTitle)
		assertNull(relocated.state.pageTurnSettlementAck)
	}

	@Test
	fun unrelatedReadableRelocationDoesNotDismissControllerOwnedShellCover() {
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val step = controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(
					href = "OEBPS/xhtml/chapter4.xhtml",
					progress = 0.28,
					pageIndex = 42,
					pageCount = 373,
					reason = "chapter-progress-seek"
				),
				tocTitle = "Chapter 4"
			)
		)

		assertTrue(step.controller.state.shellCoverVisible)
		assertFalse(step.controller.state.menuVisible)
		assertEquals("Chapter 4", step.controller.state.chrome.currentSectionTitle)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun shellCoverNextReassertsResumeLocatorBeforeDismissingCover() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/xhtml/chapter4.xhtml",
			progress = 0.28,
			pageIndex = 42,
			pageCount = 373
		)
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val requested = controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)

		assertTrue(requested.controller.state.shellCoverVisible)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					resumeLocator,
					relocationReason = "shell-cover-dismiss:1"
				)
			),
			requested.engineCommands
		)
		assertEquals(
			1L,
			requested.controller.state.pendingShellCoverDismissal?.requestId
		)

		val acknowledged = requested.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = resumeLocator.copy(reason = "shell-cover-dismiss:1"),
				tocTitle = "Chapter 4"
			)
		)

		assertFalse(acknowledged.controller.state.shellCoverVisible)
		assertFalse(acknowledged.controller.state.menuVisible)
		assertNull(acknowledged.controller.state.pendingShellCoverDismissal)
		assertEquals(
			listOf(ReaderEngineCommand.RequestVisibleTextRange("shell-cover-dismissed")),
			acknowledged.engineCommands
		)
	}

	@Test
	fun committedNativeShellPresentationHidesCoverWithoutRelocation() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/xhtml/chapter4.xhtml",
			progress = 0.28,
			pageIndex = 42,
			pageCount = 373
		)
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl =
					"https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val dismissed = controller.onViewerAction(
			ReaderViewerAction.NativeShellPrepared
		)

		assertFalse(dismissed.controller.state.shellCoverVisible)
		assertFalse(dismissed.controller.state.menuVisible)
		assertNull(dismissed.controller.state.pendingShellCoverDismissal)
		assertEquals(
			readerNativeShellCoverReturnLocatorKey(resumeLocator),
			dismissed.controller.state.nativeShellCoverReturnLocatorKey
		)
		assertTrue(
			dismissed.engineCommands.none { command ->
				command is ReaderEngineCommand.NavigateTo
			}
		)
	}

	@Test
	fun shellCoverDismissalRejectsTokenMatchedRelocationForWrongLocator() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/xhtml/chapter4.xhtml",
			progress = 0.28,
			pageIndex = 42,
			pageCount = 373
		)
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val requested = controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)

		val unrelated = requested.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = resumeLocator.copy(
					pageIndex = 43,
					reason = "shell-cover-dismiss:1"
				),
				tocTitle = "Chapter 4"
			)
		)

		assertTrue(unrelated.controller.state.shellCoverVisible)
		assertNotNull(unrelated.controller.state.pendingShellCoverDismissal)
		assertEquals(emptyList(), unrelated.engineCommands)
	}

	@Test
	fun shellCoverDismissalRejectsTokenMatchedRelocationFromWrongSession() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/xhtml/chapter4.xhtml",
			progress = 0.28,
			pageIndex = 42,
			pageCount = 373
		)
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val sessionBound = opened.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = resumeLocator.copy(reason = "initial-resume"),
				tocTitle = "Chapter 4"
			)
		).controller
		val requested = sessionBound.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)

		val staleSession = requested.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-b",
				locator = resumeLocator.copy(reason = "shell-cover-dismiss:1"),
				tocTitle = "Chapter 4"
			)
		)

		assertEquals(
			"session-a",
			requested.controller.state.pendingShellCoverDismissal?.foliateSessionId
		)
		assertTrue(staleSession.controller.state.shellCoverVisible)
		assertNotNull(staleSession.controller.state.pendingShellCoverDismissal)
		assertEquals(emptyList(), staleSession.engineCommands)
	}

	@Test
	fun shellCoverNextWithoutFoliateNavigationIdentityStaysCovered() {
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = ReaderLocator(pageIndex = 42, pageCount = 373),
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val requested = controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)

		assertTrue(requested.controller.state.shellCoverVisible)
		assertEquals(emptyList(), requested.engineCommands)
	}

	@Test
	fun startupReadableRelocationDoesNotDismissControllerOwnedShellCover() {
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val step = controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(
					href = "OEBPS/xhtml/chapter4.xhtml",
					progress = 0.28,
					pageIndex = 42,
					pageCount = 373,
					reason = "relocate-committed"
				),
				tocTitle = "Chapter 4"
			)
		)

		assertTrue(step.controller.state.shellCoverVisible)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun paginationProfileReadyRelocationDoesNotDismissControllerOwnedShellCover() {
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val step = controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(
					href = "OEBPS/xhtml/chapter4.xhtml",
					progress = 0.28,
					pageIndex = 42,
					pageCount = 373,
					reason = "pagination-profile-ready"
				),
				tocTitle = "Chapter 4"
			)
		)

		assertTrue(step.controller.state.shellCoverVisible)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun paginationProfileStatusFeedsControllerStateWithoutNavigationCommands() {
		val profile = ReaderPaginationProfileStatus(
			status = "measuring",
			fingerprint = "navic-pagination-v1:123",
			completedSections = 2,
			totalSections = 6,
			pageCount = 392
		)

		val step = ReaderController().onEngineEvent(
			ReaderEngineEvent.PaginationProfileStatusChanged(profile)
		)

		assertEquals(profile, step.controller.state.paginationProfile)
		assertEquals("Measuring pages 2/6", step.controller.state.paginationProfile.label)
		assertEquals(2f / 6f, step.controller.state.paginationProfile.progressFraction)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun loadedDocumentEventsFeedControllerStateWithoutNavigationCommands() {
		val event = ReaderEngineEvent.DocLoaded(
			index = 3,
			href = "EPUB/Text/chapter-01.xhtml",
			title = "Chapter 1",
			sectionId = "chapter-01"
		)

		val step = ReaderController().onEngineEvent(event)

		assertEquals(
			ReaderLoadedDocument(
				index = 3,
				href = "EPUB/Text/chapter-01.xhtml",
				title = "Chapter 1",
				sectionId = "chapter-01"
			),
			step.controller.state.loadedDocument
		)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded() {
		val internalLink = ReaderController().onEngineEvent(
			ReaderEngineEvent.InternalLinkRequested(
				href = "chapter-02.xhtml#door",
				prevented = true,
				source = "native-short-tap"
			)
		).controller
		val externalLink = internalLink.onEngineEvent(
			ReaderEngineEvent.ExternalLinkOpened(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			)
		).controller
		val annotationClicked = externalLink.onEngineEvent(
			ReaderEngineEvent.AnnotationClicked(
				value = "epubcfi(/6/8!/4/2:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			)
		).controller
		val annotationDrawn = annotationClicked.onEngineEvent(
			ReaderEngineEvent.AnnotationDrawn(
				value = "epubcfi(/6/8!/4/2:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			)
		).controller
		val overlayCreated = annotationDrawn.onEngineEvent(
			ReaderEngineEvent.OverlayCreated(index = 4)
		).controller
		val footnoteClosed = overlayCreated.onEngineEvent(ReaderEngineEvent.FootnoteClose).controller
		val pullUp = footnoteClosed.onEngineEvent(ReaderEngineEvent.PullUp())

		assertEquals(
			ReaderLinkInteraction.Internal(
				href = "chapter-02.xhtml#door",
				prevented = true,
				source = "native-short-tap"
			),
			internalLink.state.lastLinkInteraction
		)
		assertEquals(
			ReaderLinkInteraction.External(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			),
			externalLink.state.lastLinkInteraction
		)
		assertEquals(
			ReaderAnnotationInteraction(
				kind = ReaderAnnotationInteractionKind.Clicked,
				value = "epubcfi(/6/8!/4/2:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			),
			annotationClicked.state.lastAnnotationInteraction
		)
		assertEquals(
			ReaderAnnotationInteraction(
				kind = ReaderAnnotationInteractionKind.Drawn,
				value = "epubcfi(/6/8!/4/2:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			),
			annotationDrawn.state.lastAnnotationInteraction
		)
		assertEquals(
			ReaderOverlayInteraction.Created(index = 4),
			overlayCreated.state.lastOverlayInteraction
		)
		assertEquals(
			ReaderOverlayInteraction.FootnoteClosed,
			footnoteClosed.state.lastOverlayInteraction
		)
		assertEquals(
			ReaderOverlayInteraction.PullUp,
			pullUp.controller.state.lastOverlayInteraction
		)
		assertFalse(pullUp.controller.state.menuVisible)
		assertEquals(emptyList(), pullUp.engineCommands)
	}

	@Test
	fun scrolledEdgePullUpRecordsBridgeParityWithoutOpeningReaderMenu() {
		val controller = ReaderController().onViewerAction(ReaderViewerAction.Menu).controller
			.onViewerAction(ReaderViewerAction.Menu).controller

		val step = controller.onEngineEvent(
			ReaderEngineEvent.PullUp(source = ReaderPullUpSourceScrolledEdgeSwipe)
		)

		assertEquals(
			ReaderOverlayInteraction.PullUp,
			step.controller.state.lastOverlayInteraction
		)
		assertFalse(step.controller.state.menuVisible)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun ambiguousPullUpRecordsBridgeParityWithoutOpeningReaderMenu() {
		val controller = ReaderController()

		val step = controller.onEngineEvent(ReaderEngineEvent.PullUp())

		assertEquals(
			ReaderOverlayInteraction.PullUp,
			step.controller.state.lastOverlayInteraction
		)
		assertFalse(step.controller.state.menuVisible)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun externalLinksOpenControllerOwnedExternalLinkPrompt() {
		val step = ReaderController().onEngineEvent(
			ReaderEngineEvent.ExternalLinkOpened(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			)
		)

		assertEquals(
			ReaderLinkInteraction.External(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			),
			step.controller.state.lastLinkInteraction
		)
		assertEquals(
			ReaderExternalLinkPromptState(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			),
			step.controller.state.externalLinkPrompt
		)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun dismissExternalLinkPromptClearsOnlyTheVisiblePrompt() {
		val controller = ReaderController().onEngineEvent(
			ReaderEngineEvent.ExternalLinkOpened(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			)
		).controller

		val dismissed = controller.dismissExternalLinkPrompt()

		assertNull(dismissed.controller.state.externalLinkPrompt)
		assertEquals(
			ReaderLinkInteraction.External(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			),
			dismissed.controller.state.lastLinkInteraction
		)
		assertEquals(emptyList(), dismissed.engineCommands)
	}

	@Test
	fun footnoteOpenShowsControllerOwnedFootnotePopupAndCloseClearsIt() {
		val opened = ReaderController().onEngineEvent(
			ReaderEngineEvent.FootnoteOpened(
				href = "Text/chapter-01.xhtml#fn1",
				text = "This is the footnote body.",
				noteType = "footnote",
				hidden = true
			)
		)

		assertEquals(
			ReaderFootnotePopupState(
				href = "Text/chapter-01.xhtml#fn1",
				text = "This is the footnote body.",
				noteType = "footnote",
				hidden = true
			),
			opened.controller.state.footnotePopup
		)
		assertEquals(
			ReaderOverlayInteraction.FootnoteOpened(
				href = "Text/chapter-01.xhtml#fn1",
				noteType = "footnote"
			),
			opened.controller.state.lastOverlayInteraction
		)
		assertEquals(emptyList(), opened.engineCommands)

		val closed = opened.controller.onEngineEvent(ReaderEngineEvent.FootnoteClose)

		assertNull(closed.controller.state.footnotePopup)
		assertEquals(
			ReaderOverlayInteraction.FootnoteClosed,
			closed.controller.state.lastOverlayInteraction
		)
		assertEquals(emptyList(), closed.engineCommands)
	}

	@Test
	fun annotationClicksOpenControllerOwnedAnnotationPopup() {
		val event = ReaderEngineEvent.AnnotationClicked(
			value = "A saved note",
			index = 3,
			rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
		)

		val step = ReaderController().onEngineEvent(event)

		assertEquals(
			ReaderAnnotationInteraction(
				kind = ReaderAnnotationInteractionKind.Clicked,
				value = "A saved note",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			),
			step.controller.state.lastAnnotationInteraction
		)
		assertEquals(
			ReaderAnnotationPopupState(
				value = "A saved note",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			),
			step.controller.state.annotationPopup
		)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun dismissAnnotationPopupClearsOnlyTheVisiblePopup() {
		val controller = ReaderController().onEngineEvent(
			ReaderEngineEvent.AnnotationClicked(
				value = "A saved note",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			)
		).controller

		val dismissed = controller.dismissAnnotationPopup()

		assertNull(dismissed.controller.state.annotationPopup)
		assertEquals(
			ReaderAnnotationInteraction(
				kind = ReaderAnnotationInteractionKind.Clicked,
				value = "A saved note",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			),
			dismissed.controller.state.lastAnnotationInteraction
		)
		assertEquals(emptyList(), dismissed.engineCommands)
	}

	@Test
	fun engineRelocationFeedsChapterLocalProgressForKomikkuRail() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
		val locator = ReaderLocator(
			href = "chapter-01.xhtml",
			progress = 0.25,
			pageIndex = 42,
			pageCount = 411,
			chapterProgress = 0.3,
			chapterPageIndex = 5,
			chapterPageCount = 20
		)

		val step = controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = locator,
				tocTitle = "Chapter 1"
			)
		)

		assertEquals(locator, step.controller.state.chrome.currentLocator)
		assertEquals(
			ReaderChapterProgressState(
				href = "chapter-01.xhtml",
				title = "Chapter 1",
				pageIndex = 5,
				pageCount = 20,
				progress = 0.3
			),
			step.controller.state.chapterProgress
		)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun engineRelocationDoesNotFeedKomikkuRailFromGlobalPageModel() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
		val locator = ReaderLocator(
			href = "chapter-14.xhtml",
			progress = 0.84,
			pageIndex = 1,
			pageCount = 1748
		)

		val step = controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = locator,
				tocTitle = "Chapter XIV: Fire and Water"
			)
		)

		assertEquals(locator, step.controller.state.chrome.currentLocator)
		assertEquals(
			ReaderChapterProgressState(
				href = "chapter-14.xhtml",
				title = "Chapter XIV: Fire and Water",
				pageIndex = 0,
				pageCount = 1,
				progress = 0.0
			),
			step.controller.state.chapterProgress
		)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun chapterHrefChangeWithoutLocalPageMetadataClearsStaleRailPages() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "chapter-13.xhtml",
						chapterProgress = 1.0,
						chapterPageIndex = 11,
						chapterPageCount = 12
					),
					tocTitle = "Chapter XIII"
				)
			).controller

		val step = controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(
					href = "chapter-14.xhtml",
					progress = 0.84,
					pageIndex = 513,
					pageCount = 1748
				),
				tocTitle = "Chapter XIV: Fire and Water"
			)
		)

		assertEquals(
			ReaderChapterProgressState(
				href = "chapter-14.xhtml",
				title = "Chapter XIV: Fire and Water",
				pageIndex = 0,
				pageCount = 1,
				progress = 0.0
			),
			step.controller.state.chapterProgress
		)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun engineRelocationsBuildControllerOwnedProgressSnapshotsAfterPublicationReady() {
		val opened = ReaderController().open(hobbitOpenRequest()).controller
		val ready = opened.onEngineEvent(ReaderEngineEvent.PublicationReady).controller
		val startupCover = ready.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.0),
				tocTitle = "Cover"
			)
		)
		val resumedLocator = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml#p9",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.62,
			pageIndex = 12,
			pageCount = 411
		)
		val resumed = startupCover.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = resumedLocator,
				tocTitle = "Chapter 4"
			)
		)
		val laterCover = resumed.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.0),
				tocTitle = "Cover"
			)
		)
		val expectedProgress = BinderyReadingProgress(
			bookId = "book-1",
			kind = BinderyReadingProgressKind.Ebook,
			resourceKey = "publication.epub",
			href = "publication.epub",
			resourceHref = "publication.epub",
			textHref = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			fragmentId = "p9",
			progressFraction = 0.62
		)
		assertEquals(null, startupCover.progressToSave)
		assertEquals(
			null,
			startupCover.controller.state.readingProgress.progressFor(
				bookId = "book-1",
				resourceHref = "publication.epub",
				kind = ReaderPublicationKind.Ebook
			)
		)
		assertEquals(expectedProgress, resumed.progressToSave)
		assertEquals(
			expectedProgress,
			resumed.controller.state.readingProgress.progressFor(
				bookId = "book-1",
				resourceHref = "publication.epub",
				kind = ReaderPublicationKind.Ebook
			)
		)
		assertEquals(resumedLocator, resumed.controller.state.chrome.currentLocator)
		assertEquals("Chapter 4", resumed.controller.state.chrome.currentSectionTitle)
		assertEquals(emptyList(), resumed.engineCommands)
		assertEquals(null, laterCover.progressToSave)
		assertEquals(
			expectedProgress,
			laterCover.controller.state.readingProgress.progressFor(
				bookId = "book-1",
				resourceHref = "publication.epub",
				kind = ReaderPublicationKind.Ebook
			)
		)
	}

	@Test
	fun contentActionClaimsDoNotSuppressNativeViewerActions() {
		val claimed = ReaderController().onEngineEvent(
			ReaderEngineEvent.ContentActionClaimed(ReaderContentAction.Image)
		).controller

		val toggled = claimed.onViewerAction(ReaderViewerAction.Menu)
		val next = toggled.controller.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))

		assertEquals(ReaderContentAction.Image, claimed.state.lastContentActionClaim?.action)
		assertTrue(toggled.controller.state.menuVisible)
		assertNull(toggled.controller.state.lastContentActionClaim)
		assertEquals(
			listOf(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Next, causalSequence = 1L)),
			next.engineCommands
		)
	}

	@Test
	fun previousFromFirstReadablePageReturnsToNativeCoverInsteadOfSuppressedWebViewCover() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/Text/sinopsis.xhtml",
			progress = 0.004370907849029098,
			pageIndex = 1,
			pageCount = 270
		)
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val readable = opened
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/sinopsis.xhtml",
						progress = 0.004370907849029098,
						pageIndex = 1,
						pageCount = 270,
						reason = "shell-cover-dismiss:1"
					),
					tocTitle = "Alcatraz versus the Evil Librarians"
				)
			).controller

		val previous = readable.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous))

		assertTrue(previous.controller.state.shellCoverVisible)
		assertFalse(previous.controller.state.menuVisible)
		assertEquals(emptyList(), previous.engineCommands)
	}

	@Test
	fun rendererConfirmedPreviousBoundaryReturnsFromLandscapeOrdinalZeroToNativeCover() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/Text/sinopsis.xhtml",
			progress = 0.004370907849029098,
			pageIndex = 1,
			pageCount = 270
		)
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val ordinalOne = opened
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/sinopsis.xhtml",
						progress = 0.004370907849029098,
						pageIndex = 1,
						pageCount = 270,
						reason = "shell-cover-dismiss:1"
					),
					tocTitle = "Synopsis"
				)
			).controller
		val ordinalZero = ordinalOne.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(
					href = "OEBPS/Text/sinopsis.xhtml",
					pageIndex = 0
				),
				tocTitle = "Synopsis"
			)
		).controller

		val boundary = ordinalZero.onPageTurnBoundary(ReaderPageTurnDirection.Previous)

		assertTrue(boundary.controller.state.shellCoverVisible)
		assertFalse(boundary.controller.state.menuVisible)
		assertEquals(emptyList(), boundary.engineCommands)

		val forward = boundary.controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val returnLocator = requireNotNull(
			boundary.controller.state.chrome.currentLocator
		)

		assertTrue(forward.controller.state.shellCoverVisible)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					returnLocator,
					relocationReason = "shell-cover-dismiss:2"
				)
			),
			forward.engineCommands
		)

		val acknowledged = forward.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = returnLocator.copy(reason = "shell-cover-dismiss:2"),
				tocTitle = "Synopsis"
			)
		)
		val nextReadable = acknowledged.controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)

		assertFalse(acknowledged.controller.state.shellCoverVisible)
		assertEquals(
			listOf(ReaderEngineCommand.RequestVisibleTextRange("shell-cover-dismissed")),
			acknowledged.engineCommands
		)
		assertEquals(
			listOf(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Next, causalSequence = 1L)),
			nextReadable.engineCommands
		)
	}

	@Test
	fun previousFromFrontmatterStartReturnsToNativeCoverEvenWhenGlobalPageIndexIsPastOne() {
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val frontmatter = opened
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/sinopsis.xhtml",
						progress = 0.0007927082115140601,
						pageIndex = 10,
						pageCount = 1534,
						chapterProgress = 0.0,
						chapterPageIndex = 0,
						chapterPageCount = 2,
						reason = "shell-cover-dismiss:1"
					),
					tocTitle = "Synopsis"
				)
			).controller

		val previous = frontmatter.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous))

		assertTrue(previous.controller.state.shellCoverVisible)
		assertFalse(previous.controller.state.menuVisible)
		assertEquals(emptyList(), previous.engineCommands)
	}

	@Test
	fun previousFromLaterFrontmatterPageUsesEngineInsteadOfJumpingBackToNativeCover() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/Text/sinopsis.xhtml",
			progress = 0.0007927082115140601,
			pageIndex = 10,
			pageCount = 1534,
			chapterProgress = 0.0,
			chapterPageIndex = 0,
			chapterPageCount = 2
		)
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val firstFrontmatter = opened
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/sinopsis.xhtml",
						progress = 0.0007927082115140601,
						pageIndex = 10,
						pageCount = 1534,
						chapterProgress = 0.0,
						chapterPageIndex = 0,
						chapterPageCount = 2,
						reason = "shell-cover-dismiss:1"
					),
					tocTitle = "Synopsis"
				)
			).controller
		val laterFrontmatter = firstFrontmatter
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/TitlePage-01.xhtml",
						progress = 0.0024865680920030196,
						pageIndex = 13,
						pageCount = 1534,
						chapterProgress = 0.0,
						chapterPageIndex = 0,
						chapterPageCount = 2
					),
					tocTitle = null
				)
			).controller

		val previous = laterFrontmatter.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous))

		assertFalse(previous.controller.state.shellCoverVisible)
		assertEquals(
			listOf(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Previous, causalSequence = 2L)),
			previous.engineCommands,
			"Only the first eligible EPUB-side locator after dismissing the native cover may reclaim the native cover."
		)
	}

	@Test
	fun readerBackFromReadablePageReturnsToNativeCoverBeforeLeavingReader() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/Text/chapter-01.xhtml",
			progress = 0.12,
			pageIndex = 166,
			pageCount = 229,
			chapterPageIndex = 8,
			chapterPageCount = 14
		)
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val readable = opened
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/chapter-01.xhtml",
						progress = 0.12,
						pageIndex = 166,
						pageCount = 229,
						chapterPageIndex = 8,
						chapterPageCount = 14,
						reason = "shell-cover-dismiss:1"
					),
					tocTitle = "Chapter I: An Unexpected Party"
				)
			).controller

		val back = readable.onBack()

		assertTrue(back.handled)
		assertTrue(back.controller.state.shellCoverVisible)
		assertFalse(back.controller.state.menuVisible)
		assertNull(back.controller.state.dialog)
		assertEquals(emptyList(), back.engineCommands)
	}

	@Test
	fun readerBackToNativeCoverStopsWhispersyncAudiobookPlayback() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/Text/chapter-01.xhtml",
			progress = 0.12
		)
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val readable = opened
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/chapter-01.xhtml",
						progress = 0.12,
						reason = "shell-cover-dismiss:1"
					),
					tocTitle = "Chapter 1"
				)
			).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onReadaloudPlaybackState(
				ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = true,
					trackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					positionMs = 5_500L,
					durationMs = 8_000L
				)
			).controller

		val back = readable.onBack()

		assertTrue(back.handled)
		assertTrue(back.controller.state.shellCoverVisible)
		assertEquals(ReaderReadaloudPlaybackCommand.StopAndReset, back.readaloudPlaybackCommand)
	}

	@Test
	fun readerAppBarBackFromVisibleChromeReturnsToNativeCoverBeforeLeavingReader() {
		val resumeLocator = ReaderLocator(
			href = "OEBPS/Text/chapter-01.xhtml",
			progress = 0.12,
			pageIndex = 166,
			pageCount = 229,
			chapterPageIndex = 8,
			chapterPageCount = 14
		)
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				startLocator = resumeLocator,
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
		val readable = opened
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "OEBPS/Text/chapter-01.xhtml",
						progress = 0.12,
						pageIndex = 166,
						pageCount = 229,
						chapterPageIndex = 8,
						chapterPageCount = 14,
						reason = "shell-cover-dismiss:1"
					),
					tocTitle = "Chapter I: An Unexpected Party"
				)
			).controller
			.onViewerAction(ReaderViewerAction.Menu)
			.controller

		val back = readable.onNavigateBack()

		assertTrue(back.handled)
		assertTrue(back.controller.state.shellCoverVisible)
		assertFalse(back.controller.state.menuVisible)
		assertNull(back.controller.state.dialog)
		assertEquals(emptyList(), back.engineCommands)
	}

	@Test
	fun readerBackFromNativeCoverFallsThroughToAppNavigation() {
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val back = opened.onBack()

		assertFalse(back.handled)
		assertTrue(back.controller.state.shellCoverVisible)
		assertEquals(emptyList(), back.engineCommands)
	}

	@Test
	fun contentActionClaimsKeepMetadataInControllerState() {
		val claim = ReaderContentActionClaim(
			action = ReaderContentAction.Link,
			source = "link",
			href = "EPUB/Text/chapter-02.xhtml#door",
			text = "Chapter II",
			x = 120.0,
			y = 240.0
		)

		val claimed = ReaderController().onEngineEvent(
			ReaderEngineEvent.ContentActionClaimed(claim)
		).controller
		val toggled = claimed.onViewerAction(ReaderViewerAction.Menu)

		assertEquals(claim, claimed.state.lastContentActionClaim)
		assertNull(toggled.controller.state.lastContentActionClaim)
		assertTrue(toggled.controller.state.menuVisible)
	}

	@Test
	fun viewerScrollActionsAreControllerOwnedAndForwardedAsEngineCapability() {
		val down = ReaderController().onViewerAction(
			ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down)
		)
		val up = ReaderController().onViewerAction(
			ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Up)
		)

		assertEquals(
			listOf(ReaderEngineCommand.ScrollViewport(ReaderViewportScrollDirection.Down, causalSequence = 1L)),
			down.engineCommands
		)
		assertEquals(
			listOf(ReaderEngineCommand.ScrollViewport(ReaderViewportScrollDirection.Up, causalSequence = 1L)),
			up.engineCommands
		)
	}

	@Test
	fun viewerReadableDragPreviewIsControllerOwnedAndForwardedAsEngineCapability() {
		val step = ReaderController().onViewerAction(
			ReaderViewerAction.PreviewPageDrag(
				deltaX = -184.0,
				deltaY = -96.0,
				viewWidth = 1440.0,
				viewHeight = 2200.0,
				phase = ReaderPageDragPreviewPhase.Update
			)
		)

		assertFalse(step.controller.state.menuVisible)
		assertEquals(
			listOf(
				ReaderEngineCommand.PreviewPageDrag(
					deltaX = -184.0,
					deltaY = -96.0,
					viewWidth = 1440.0,
					viewHeight = 2200.0,
					phase = ReaderPageDragPreviewPhase.Update
				)
			),
			step.engineCommands
		)
	}

	@Test
	fun viewerLongPressContentActionsAreControllerOwnedAndForwardedAsEngineCapability() {
		val action = ReaderViewerAction.ContentLongPressAt(
			x = 250.0,
			y = 500.0,
			viewWidth = 500.0,
			viewHeight = 1000.0
		)

		val step = ReaderController().onViewerAction(action)

		assertFalse(step.controller.state.menuVisible)
		assertEquals(
			listOf(
				ReaderEngineCommand.ContentLongPressAt(
					x = 250.0,
					y = 500.0,
					viewWidth = 500.0,
					viewHeight = 1000.0,
					selectText = true
				)
			),
			step.engineCommands
		)
	}

	@Test
	fun viewerLongPressContentActionsUseTextPointOnlyWhileWhispersyncAudiobookIsActive() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onReadaloudPlaybackState(
				ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = true,
					trackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					positionMs = 5_500L,
					durationMs = 8_000L
				)
			).controller

		val step = controller.onViewerAction(
			ReaderViewerAction.ContentLongPressAt(
				x = 250.0,
				y = 500.0,
				viewWidth = 500.0,
				viewHeight = 1000.0
			)
		)

		assertEquals(
			listOf(
				ReaderEngineCommand.ContentLongPressAt(
					x = 250.0,
					y = 500.0,
					viewWidth = 500.0,
					viewHeight = 1000.0,
					selectText = false,
					causalSequence = 1L
				)
			),
			step.engineCommands
		)
		assertFalse(step.controller.state.selectionActions.visible)
	}

	@Test
	fun viewerLongPressContentActionsAreIgnoredWhileNativeShellCoverOwnsTheSurface() {
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller

		val step = opened.onViewerAction(
			ReaderViewerAction.ContentLongPressAt(
				x = 250.0,
				y = 500.0,
				viewWidth = 500.0,
				viewHeight = 1000.0
			)
		)

		assertTrue(step.controller.state.shellCoverVisible)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun applySettingsKeepsControllerAsOwnerAndForwardsNormalizedSettingsToEngine() {
		val unnormalized = ReaderSettings(
			fontSizePercent = 500,
			lineHeight = 0.4,
			theme = "unknown-theme",
			flowMode = ReaderFlowScrolled,
			paged = true,
			tapZone = ReaderTapZoneKindle,
			publisherStyles = true
		)

		val step = ReaderController().applySettings(unnormalized)
		val normalized = unnormalized.normalizedReaderSettings()

		assertEquals(normalized, step.controller.state.chrome.settings)
		assertEquals(
			listOf(ReaderEngineCommand.ApplySettings(normalized)),
			step.engineCommands
		)
	}

	@Test
	fun applySettingsKeepsWhispersyncLeadChangeOutOfWebViewPagination() {
		val current = defaultReaderSettings()
		val controller = ReaderController(
			state = ReaderControllerState(
				chrome = ReaderChromeState(settings = current)
			)
		)
		val updated = current.copy(whispersyncHighlightLeadMs = 1_500)

		val step = controller.applySettings(updated)

		assertEquals(updated, step.controller.state.chrome.settings)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun applySettingsKeepsHostOnlyChangesOutOfWebView() {
		val current = defaultReaderSettings()
		val controller = ReaderController(
			state = ReaderControllerState(
				chrome = ReaderChromeState(settings = current)
			)
		)
		val updates = mapOf(
			"unchanged" to current,
			"dim overlay" to current.copy(dimOverlayPercent = 40),
			"color filter" to current.copy(colorFilterEnabled = true),
			"grayscale" to current.copy(grayscaleEnabled = true),
			"inverted colors" to current.copy(invertedColors = true),
			"orientation" to current.copy(orientation = ReaderOrientationLandscape),
			"navigation rail" to current.copy(navBarType = ReaderNavBarTypeBottom),
			"tap-zone inversion" to current.copy(tapZoneInvertMode = ReaderTapZoneInvertBoth),
			"bitmap quality" to current.copy(pageBitmapQuality = ReaderPageBitmapQuality.High.persistedValue),
			"fullscreen" to current.copy(fullscreen = false),
			"keep screen on" to current.copy(keepScreenOn = true),
			"readaloud sync" to current.copy(readaloudSyncEnabled = false),
			"volume keys" to current.copy(volumeKeyPageTurns = true),
			"WebView debugging" to current.copy(webContentsDebuggingEnabled = true)
		)

		updates.forEach { (name, updated) ->
			val step = controller.applySettings(updated)

			assertEquals(updated, step.controller.state.chrome.settings, name)
			assertEquals(emptyList(), step.engineCommands, name)
		}
	}

	@Test
	fun applySettingsStillForwardsWebViewPresentationChanges() {
		val current = defaultReaderSettings()
		val controller = ReaderController(
			state = ReaderControllerState(
				chrome = ReaderChromeState(settings = current)
			)
		)
		val updates = listOf(
			current.copy(theme = ReaderDarkTheme),
			current.copy(paperTextureEnabled = !checkNotNull(current.paperTextureEnabled)),
			current.copy(tapZone = ReaderTapZoneKindle),
			current.copy(whispersyncHighlightColorArgb = 0x66112233)
		)

		updates.forEach { updated ->
			val normalized = updated.normalizedReaderSettings()
			val step = controller.applySettings(updated)

			assertEquals(
				listOf(ReaderEngineCommand.ApplySettings(normalized)),
				step.engineCommands
			)
		}
	}

	@Test
	fun applySettingsPublishesRasterKeyOnlyAfterHostPresentationCommit() {
		val current = defaultReaderSettings()
		val controller = ReaderController(
			state = ReaderControllerState(
				chrome = ReaderChromeState(settings = current),
				readerSettingsPresentationSnapshotKey = current.readerPageRasterSnapshotKey()
			)
		)
		val updated = current.copy(theme = ReaderDarkTheme)

		val pending = controller.applySettings(updated)

		assertEquals(
			current.readerPageRasterSnapshotKey(),
			pending.controller.state.readerSettingsPresentationSnapshotKey
		)
		val committed = pending.controller.onEngineEvent(
			ReaderEngineEvent.SettingsPresentationCommitted(updated.readerPageRasterSnapshotKey())
		)
		assertEquals(
			updated.readerPageRasterSnapshotKey(),
			committed.controller.state.readerSettingsPresentationSnapshotKey
		)
	}

	@Test
	fun settingsDialogVisibilityIsControllerOwnedLikeKomikkuReaderSettingsDialog() {
		val contents = ReaderController().openContentsDialog()
		val settings = contents.controller.openSettingsDialog()
		val dismissed = settings.controller.closeDialog()

		assertEquals(ReaderControllerDialog.Contents, contents.controller.state.dialog)
		assertTrue(contents.controller.state.menuVisible)
		assertEquals(emptyList(), contents.engineCommands)
		assertEquals(ReaderControllerDialog.Settings, settings.controller.state.dialog)
		assertTrue(settings.controller.state.menuVisible)
		assertEquals(emptyList(), settings.engineCommands)
		assertNull(dismissed.controller.state.dialog)
		assertTrue(dismissed.controller.state.menuVisible)
		assertEquals(emptyList(), dismissed.engineCommands)
	}

	@Test
	fun whispersyncPlayerDialogIsControllerOwnedLikeKomikkuReaderChrome() {
		val step = ReaderController().openWhispersyncPlayerDialog()
		val dismissed = step.controller.closeDialog()

		assertEquals(ReaderControllerDialog.WhispersyncPlayer, step.controller.state.dialog)
		assertTrue(step.controller.state.menuVisible)
		assertEquals(emptyList(), step.engineCommands)
		assertNull(dismissed.controller.state.dialog)
		assertTrue(dismissed.controller.state.menuVisible)
		assertEquals(emptyList(), dismissed.engineCommands)
	}

	@Test
	fun readerSettingsDialogHasSingleControllerRoute() {
		assertTrue(
			ReaderControllerDialog.entries.none { it.name == "ReadingMode" },
			"Reader settings must have one controller dialog route; duplicate settings modes recreate the old docked-options surface."
		)
	}

	@Test
	fun settingsDialogCanHideReaderChromeForKomikkuCustomFilterWithoutClosingDialog() {
		val settings = ReaderController().openSettingsDialog().controller

		val hidden = settings.hideMenus()
		val shown = hidden.controller.showMenus()

		assertEquals(ReaderControllerDialog.Settings, hidden.controller.state.dialog)
		assertFalse(hidden.controller.state.menuVisible)
		assertEquals(emptyList(), hidden.engineCommands)
		assertEquals(ReaderControllerDialog.Settings, shown.controller.state.dialog)
		assertTrue(shown.controller.state.menuVisible)
		assertEquals(emptyList(), shown.engineCommands)
	}

	@Test
	fun searchIsControllerOwnedAndForwardedAsEngineCapability() {
		val controller = ReaderController()

		val step = controller.search("  unexpected party  ")

		assertEquals(
			ReaderSearchState(
				query = "unexpected party",
				results = emptyList(),
				active = true,
				progress = 0.0
			),
			step.controller.state.search
		)
		assertEquals(
			listOf(ReaderEngineCommand.Search("unexpected party")),
			step.engineCommands
		)
	}

	@Test
	fun searchDialogAndClearSearchAreControllerOwned() {
		val controller = ReaderController()
			.openSearchDialog().controller
			.search("  unexpected party  ").controller

		val cleared = controller.closeSearchDialog()

		assertEquals(ReaderControllerDialog.Search, controller.state.dialog)
		assertEquals(ReaderSearchState(query = "unexpected party", active = true, progress = 0.0), controller.state.search)
		assertNull(cleared.controller.state.dialog)
		assertEquals(ReaderSearchState(), cleared.controller.state.search)
		assertEquals(listOf(ReaderEngineCommand.ClearSearch), cleared.engineCommands)
	}

	@Test
	fun streamedSearchProgressUpdatesControllerWithoutWaitingForCompletion() {
		val partialResults = listOf(
			ReaderSearchResult(
				id = "result-1",
				href = "chapter-01.xhtml",
				excerpt = "unexpected party"
			)
		)

		val searching = ReaderController().search("unexpected").controller
		val partial = searching.onEngineEvent(
			ReaderEngineEvent.SearchResults(
				query = "unexpected",
				results = partialResults,
				progress = 0.42,
				complete = false
			)
		).controller
		val complete = partial.onEngineEvent(
			ReaderEngineEvent.SearchResults(
				query = "unexpected",
				results = partialResults,
				progress = 1.0,
				complete = true
			)
		).controller

		assertEquals(ReaderSearchState(query = "unexpected", active = true, progress = 0.0), searching.state.search)
		assertEquals(
			ReaderSearchState("unexpected", partialResults, active = true, progress = 0.42, complete = false),
			partial.state.search
		)
		assertEquals(
			ReaderSearchState("unexpected", partialResults, active = true, progress = 1.0, complete = true),
			complete.state.search
		)
	}

	@Test
	fun searchResultNavigationIsControllerOwned() {
		val result = ReaderSearchResult(
			id = "result-1",
			cfi = " epubcfi(/6/8!/4/2:12) ",
			href = " chapter-01.xhtml ",
			excerpt = "unexpected party"
		)

		val step = ReaderController().navigateToSearchResult(result)

		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(
						href = "chapter-01.xhtml",
						cfi = "epubcfi(/6/8!/4/2:12)"
					),
					causalSequence = 1L
				)
			),
			step.engineCommands
		)
	}

	@Test
	fun savedBookmarkNavigationIsControllerOwned() {
		val bookmark = ReaderBookmark(
			id = "book-1|epubcfi(/6/8!/4/1:0)",
			bookId = "book-1",
			bookTitle = "The Hobbit",
			href = " chapter-01.xhtml ",
			cfi = " epubcfi(/6/8!/4/1:0) ",
			progress = 0.24,
			sectionTitle = "Chapter 1"
		)

		val step = ReaderController()
			.openContentsDialog().controller
			.navigateToBookmark(bookmark)

		assertNull(step.controller.state.dialog)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(
						href = "chapter-01.xhtml",
						cfi = "epubcfi(/6/8!/4/1:0)",
						progress = 0.24
					),
					causalSequence = 1L
				)
			),
			step.engineCommands
		)
	}

	@Test
	fun savedAnnotationNavigationIsControllerOwned() {
		val annotation = ReaderAnnotation(
			id = "book-1|epubcfi(/6/8!/4/1:12)",
			bookId = "book-1",
			bookTitle = "The Hobbit",
			href = " chapter-01.xhtml ",
			cfi = " epubcfi(/6/8!/4/1:12) ",
			text = "The note sentence",
			note = "Remember this later",
			sectionTitle = "Chapter 1"
		)

		val step = ReaderController()
			.openContentsDialog().controller
			.navigateToAnnotation(annotation)

		assertNull(step.controller.state.dialog)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(
						href = "chapter-01.xhtml",
						cfi = "epubcfi(/6/8!/4/1:12)"
					),
					causalSequence = 1L
				)
			),
			step.engineCommands
		)
	}

	@Test
	fun navigateToIsControllerOwnedAndForwardedAsEngineCapability() {
		val locator = ReaderLocator(progress = 0.42)

		val step = ReaderController().navigateTo(locator)

		assertEquals(
			listOf(ReaderEngineCommand.NavigateTo(locator, causalSequence = 1L)),
			step.engineCommands
		)
	}

	@Test
	fun viewerMoveToPageActionIsForwardedAsEngineNavigationCapability() {
		val locator = ReaderLocator(
			href = "EPUB/Text/chapter-02.xhtml",
			cfi = "epubcfi(/6/8!/4/2:16)",
			progress = 0.34
		)

		val step = ReaderController().onViewerAction(ReaderViewerAction.NavigateTo(locator))

		assertEquals(
			listOf(ReaderEngineCommand.NavigateTo(locator, causalSequence = 1L)),
			step.engineCommands,
			"ReaderController must translate Komikku viewer movement into engine navigation without giving the engine shell ownership."
		)
	}

	@Test
	fun chapterNavigatorArrowsNavigateAdjacentTocEntriesInsteadOfTurningPages() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Toc(
					listOf(
						ReaderTocItem(
							id = "chapter-1",
							title = "Chapter 1",
							href = "EPUB/Text/chapter-01.xhtml"
						),
						ReaderTocItem(
							id = "chapter-2",
							title = "Chapter 2",
							href = "EPUB/Text/chapter-02.xhtml"
						),
						ReaderTocItem(
							id = "chapter-3",
							title = "Chapter 3",
							href = "EPUB/Text/chapter-03.xhtml#start"
						)
					)
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "EPUB/Text/chapter-02.xhtml#p3",
						chapterPageIndex = 3,
						chapterPageCount = 12
					),
					tocTitle = "Chapter 2"
				)
			).controller

		val previous = controller.navigateToPreviousChapter()
		val next = controller.navigateToNextChapter()

		assertTrue(controller.state.canNavigateToPreviousChapter)
		assertTrue(controller.state.canNavigateToNextChapter)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(href = "EPUB/Text/chapter-01.xhtml"),
					causalSequence = 1L
				)
			),
			previous.engineCommands
		)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(href = "EPUB/Text/chapter-03.xhtml#start"),
					causalSequence = 1L
				)
			),
			next.engineCommands
		)
	}

	@Test
	fun loadedDocumentBecomesChapterNavigationAnchorBeforeRelocationCatchesUp() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Toc(
					listOf(
						ReaderTocItem(
							id = "foreword",
							title = "Foreword",
							href = "EPUB/Text/foreword.xhtml"
						),
						ReaderTocItem(
							id = "chapter-1",
							title = "Chapter 1",
							href = "EPUB/Text/chapter-01.xhtml"
						),
						ReaderTocItem(
							id = "chapter-2",
							title = "Chapter 2",
							href = "EPUB/Text/chapter-02.xhtml"
						)
					)
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "EPUB/Text/foreword.xhtml",
						chapterPageIndex = 3,
						chapterPageCount = 4,
						chapterProgress = 1.0
					),
					tocTitle = "Foreword"
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.DocLoaded(
					index = 1,
					href = "EPUB/Text/chapter-01.xhtml",
					title = "Chapter 1",
					sectionId = "chapter-1"
				)
			).controller

		val previous = controller.navigateToPreviousChapter()
		val next = controller.navigateToNextChapter()

		assertEquals("EPUB/Text/chapter-01.xhtml", controller.state.chapterProgress.href)
		assertEquals("Chapter 1", controller.state.chapterProgress.title)
		assertEquals(0, controller.state.chapterProgress.pageIndex)
		assertEquals(1, controller.state.chapterProgress.pageCount)
		assertEquals(0.0, controller.state.chapterProgress.progress)
		assertTrue(controller.state.canNavigateToPreviousChapter)
		assertTrue(controller.state.canNavigateToNextChapter)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(href = "EPUB/Text/foreword.xhtml"),
					causalSequence = 1L
				)
			),
			previous.engineCommands
		)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(href = "EPUB/Text/chapter-02.xhtml"),
					causalSequence = 1L
				)
			),
			next.engineCommands
		)
	}

	@Test
	fun loadedDocumentPreventsChapterPageSeekFromTargetingPreviousSection() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "EPUB/Text/foreword.xhtml",
						chapterPageIndex = 3,
						chapterPageCount = 4,
						chapterProgress = 1.0
					),
					tocTitle = "Foreword"
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.DocLoaded(
					index = 1,
					href = "EPUB/Text/chapter-01.xhtml",
					title = "Chapter 1",
					sectionId = "chapter-1"
				)
			).controller

		val seek = controller.navigateToChapterPage(2)

		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(
						href = "EPUB/Text/chapter-01.xhtml",
						chapterProgress = 0.0,
						chapterPageIndex = 0,
						chapterPageCount = 1
					),
					causalSequence = 1L
				)
			),
			seek.engineCommands
		)
	}

	@Test
	fun chapterNavigatorArrowsDisableAtTocBounds() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Toc(
					listOf(
						ReaderTocItem(
							id = "chapter-1",
							title = "Chapter 1",
							href = "EPUB/Text/chapter-01.xhtml"
						),
						ReaderTocItem(
							id = "chapter-2",
							title = "Chapter 2",
							href = "EPUB/Text/chapter-02.xhtml"
						)
					)
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(href = "EPUB/Text/chapter-01.xhtml"),
					causalSequence = 1L,
					tocTitle = "Chapter 1"
				)
			).controller

		val previous = controller.navigateToPreviousChapter()

		assertFalse(controller.state.canNavigateToPreviousChapter)
		assertTrue(controller.state.canNavigateToNextChapter)
		assertEquals(emptyList(), previous.engineCommands)
	}

	@Test
	fun mediaOverlayCommandsAreControllerOwnedAndForwardedAsEngineCapabilities() {
		val fragment = ReaderOverlayFragment(
			resourceHref = "audio/chapter-01.mp3",
			fragmentId = "clip-1",
			textHref = "chapter-01.xhtml#p1",
			clipBeginSeconds = 12.0,
			clipEndSeconds = 18.5,
			label = "Chapter 1, paragraph 1"
		)

		val applied = ReaderController().applyMediaOverlay(fragment)
		val cleared = applied.controller.clearMediaOverlay(fragmentId = "clip-1")

		assertEquals(fragment, applied.controller.state.activeMediaOverlay)
		assertEquals("Chapter 1, paragraph 1", applied.controller.state.audioMetadataLabel)
		assertEquals(
			listOf(ReaderEngineCommand.ApplyMediaOverlay(fragment)),
			applied.engineCommands
		)
		assertNull(cleared.controller.state.activeMediaOverlay)
		assertNull(cleared.controller.state.audioMetadataLabel)
		assertEquals(
			listOf(ReaderEngineCommand.ClearMediaOverlay),
			cleared.engineCommands
		)
	}

	@Test
	fun readaloudPlaybackStateIsControllerOwnedAndDoesNotEmitEngineCommands() {
		val playback = ReaderReadaloudPlaybackUiState(
			isAvailable = true,
			isPlaying = true,
			trackIndex = 2,
			positionMs = 42_000L,
			durationMs = 120_000L,
			playbackSpeed = 1.25f,
			activeAudioLabel = "Chapter 3 / Paragraph 4",
			activeAudioMetadata = ReadaloudPlaybackMetadataLabels(
				chapterLabel = "Chapter 3",
				sectionLabel = "Paragraph 4",
				narratorLabel = "Narrator"
			),
			syncEnabled = true
		)

		val step = ReaderController().onReadaloudPlaybackState(playback)

		assertEquals(playback, step.controller.state.chrome.readaloudPlayback)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun whispersyncStartAwaitsPositiveOverlayActivationBeforeSeekingAudio() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "Text/chapter1.xhtml",
						progress = 0.12,
						pageIndex = 4,
						pageCount = 100
					)
				)
			).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller

		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)"
			)
		)
		assertTrue(prepared.engineCommands.isEmpty())
		assertNull(prepared.whispersyncAudioSeekTarget)
		assertEquals(
			5_000L,
			prepared.controller.state.whispersync.preparedVisibleTarget
				?.audioSeekTarget?.positionMs
		)

		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)

		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		)
		val requestId = requireNotNull(overlay.fragment.overlayRequestId)
		assertEquals("seg-2", overlay.fragment.fragmentId)
		assertEquals("Second sentence", overlay.fragment.label)
		assertNull(pending.whispersyncAudioSeekTarget)
		assertEquals(
			5_000L,
			pending.controller.state.whispersync.pendingAudioSeek?.target?.positionMs
		)
		assertEquals(
			"Audio/chapter01.m4b",
			pending.controller.state.whispersync.pendingAudioSeek?.target?.audioResource
		)
		assertEquals(
			ReaderWhispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)",
				destinationCommitIdentity = whispersyncTestDestination
			),
			pending.controller.state.whispersync.visibleTextRange
		)
		assertNull(pending.controller.state.activeMediaOverlay)
		assertNull(pending.controller.state.audioMetadataLabel)

		val repeated = pending.controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "/Text/chapter1.xhtml",
				visibleStart = 85,
				visibleEnd = 130
			)
		)
		assertEquals(emptyList(), repeated.engineCommands)
		assertNull(repeated.whispersyncAudioSeekTarget)

		val missingReceipt = repeated.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(overlay.fragment)
		)
		assertEquals(5_000L, missingReceipt.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(overlay.fragment, missingReceipt.controller.state.activeMediaOverlay)
		assertNull(missingReceipt.controller.state.activeMediaOverlayAnchorReceipt)
		assertEquals(
			requestId,
			missingReceipt.controller.state.whispersync.sync.confirmedOverlayRequestId
		)

		val receipt = ReaderWhispersyncAnchorReceipt(
			foliateSessionId = "session-a",
			destinationCommitToken = "settled-4",
			visualPageOrdinal = 4,
			spineIndex = 0,
			rasterGeneration = 12L,
			textureGeneration = 13L,
			presentationMutationGeneration = 3L,
			presentationSequence = 4L,
			anchorGeneration = 5L,
			boundarySequence = requestId,
			layoutGeneration = 14L,
			viewGeneration = 15L,
			commitSequence = 16L,
			committedSpineIndex = 0,
			committedChapterPageIndex = 0,
			committedChapterPageCount = 1,
			paginationFingerprint = "pagination",
			layoutFingerprint = "layout",
			readerSettingsRasterKey = "settings",
			captureGeometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 600.0,
				viewportHeight = 800.0,
				mode = ReaderPageTurnLayoutMode.Single,
				pages = listOf(
					ReaderPageTurnPageRect(
						ReaderPageTurnPageRole.Full,
						30.0,
						0.0,
						540.0,
						800.0
					)
				)
			),
			pageLocalRects = listOf(
				ReaderWhispersyncPageLocalRect(
					ReaderPageTurnPageRole.Full,
					18.0,
					40.0,
					62.0,
					24.0
				)
			)
		)
		val confirmed = repeated.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(overlay.fragment, receipt)
		)
		assertEquals(5_000L, confirmed.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(
			"Audio/chapter01.m4b",
			confirmed.whispersyncAudioSeekTarget?.audioResource
		)
		assertNotNull(confirmed.progressToSave)
		assertEquals(overlay.fragment, confirmed.controller.state.activeMediaOverlay)
		assertEquals(receipt, confirmed.controller.state.activeMediaOverlayAnchorReceipt)
		assertEquals("Second sentence", confirmed.controller.state.audioMetadataLabel)
		assertEquals(
			requestId,
			confirmed.controller.state.whispersync.sync.confirmedOverlayRequestId
		)
		assertNull(confirmed.controller.state.whispersync.pendingAudioSeek?.target)

		val duplicate = confirmed.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(overlay.fragment)
		)
		assertNull(duplicate.whispersyncAudioSeekTarget)
		assertEquals(overlay.fragment, duplicate.controller.state.activeMediaOverlay)
		assertEquals(receipt, duplicate.controller.state.activeMediaOverlayAnchorReceipt)
	}

	@Test
	fun exactWordSyncCanKeepCoarsePlaybackPulseFromAdvancingTheOverlay() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val requested = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val confirmed = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(requested)
		).controller
		val playback = ReaderReadaloudPlaybackUiState(
			isAvailable = true,
			isPlaying = true,
			trackIndex = 0,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 5_500L,
			durationMs = 8_000L
		)

		val step = confirmed.onReadaloudPlaybackState(
			playbackState = playback,
			publishOverlayProgress = false
		)

		assertEquals(playback, step.controller.state.chrome.readaloudPlayback)
		assertEquals(requested, step.controller.state.activeMediaOverlay)
		assertTrue(step.engineCommands.isEmpty())
	}

	@Test
	fun exactWordSyncCanKeepCoarseCueChangeFromAdvancingOrPausing() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 10,
				visibleEnd = 42
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val requested = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val confirmed = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(requested)
		).controller
		val playback = ReaderReadaloudPlaybackUiState(
			isAvailable = true,
			isPlaying = true,
			trackIndex = 0,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 5_500L,
			durationMs = 8_000L
		)

		val step = confirmed.onReadaloudPlaybackState(
			playbackState = playback,
			publishOverlayProgress = false
		)

		assertEquals(playback, step.controller.state.chrome.readaloudPlayback)
		assertEquals(requested, step.controller.state.activeMediaOverlay)
		assertTrue(step.engineCommands.isEmpty())
		assertNull(step.readaloudPlaybackCommand)
	}

	@Test
	fun whispersyncMismatchedOverlayActivationIsIgnored() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val requested = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val stale = requested.copy(
			overlayRequestId = requireNotNull(requested.overlayRequestId) + 1L,
			label = "Stale page cue"
		)

		val ignored = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(stale)
		)

		assertEquals(pending.controller, ignored.controller)
		assertNull(ignored.whispersyncAudioSeekTarget)
	}

	@Test
	fun whispersyncMatchingOverlayRejectionClearsCueAndPausesPlayback() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val requested = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val confirmed = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(requested)
		).controller
		val playing = confirmed.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		).controller

		val rejected = playing.onEngineEvent(
			ReaderEngineEvent.MediaOverlayInactive(
				fragmentId = requested.fragmentId,
				overlayRequestId = requested.overlayRequestId,
				reason = "paint-rejected"
			)
		)

		assertNull(rejected.controller.state.whispersync.sync.activeCueKey)
		assertNull(rejected.controller.state.whispersync.sync.activeOverlayRequestId)
		assertNull(rejected.controller.state.whispersync.sync.confirmedOverlayRequestId)
		assertNull(rejected.controller.state.whispersync.pendingAudioSeek?.target)
		assertNull(rejected.controller.state.activeMediaOverlay)
		assertNull(rejected.controller.state.audioMetadataLabel)
		assertEquals(
			ReaderReadaloudPlaybackCommand.Pause,
			rejected.readaloudPlaybackCommand
		)
	}

	@Test
	fun whispersyncStaleOverlayRejectionIsIgnored() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val requested = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val confirmed = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(requested)
		).controller

		val ignored = confirmed.onEngineEvent(
			ReaderEngineEvent.MediaOverlayInactive(
				fragmentId = requested.fragmentId,
				overlayRequestId = requireNotNull(requested.overlayRequestId) + 1L,
				reason = "stale-progress-request"
			)
		)

		assertEquals(confirmed, ignored.controller)
		assertNull(ignored.readaloudPlaybackCommand)
	}

	@Test
	fun whispersyncUnscopedOverlayRejectionCannotClearTrackedRequest() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val requested = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val confirmed = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(requested)
		).controller
		val playing = confirmed.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		).controller

		val ignored = playing.onEngineEvent(
			ReaderEngineEvent.MediaOverlayInactive(reason = "document-loaded")
		)

		assertEquals(playing, ignored.controller)
		assertNull(ignored.readaloudPlaybackCommand)
	}

	@Test
	fun confirmedTextPointRequiresOneShotExplicitSelectionAuthority() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val requested = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val confirmed = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(requested)
		).controller
		val reserved = confirmed.onViewerAction(
			ReaderViewerAction.ContentLongPressAt(10.0, 20.0, 100.0, 100.0)
		)

		val causalSequence = requireNotNull(
			reserved.controller.state.whispersync.pendingCausalIntent?.sequence
		)
		val selected = reserved.controller.onEngineEvent(
			ReaderEngineEvent.TextPoint(
				textHref = "Text/chapter1.xhtml",
				textOffset = 90,
				causalSequence = causalSequence,
				destinationCommitIdentity = whispersyncTestDestination
			)
		)
		assertEquals(emptyList(), selected.engineCommands)
		assertEquals(5_000L, selected.whispersyncAudioSeekTarget?.positionMs)

		val repeated = selected.controller.onEngineEvent(
			ReaderEngineEvent.TextPoint(
				textHref = "Text/chapter1.xhtml",
				textOffset = 90,
				causalSequence = causalSequence,
				destinationCommitIdentity = whispersyncTestDestination
			)
		)
		assertEquals(emptyList(), repeated.engineCommands)
		assertNull(repeated.whispersyncAudioSeekTarget)
		assertNull(repeated.controller.state.whispersync.pendingAudioSeek?.target)
	}

	@Test
	fun shellCoverVisibleTextRangeCannotApplyOverlayOrSeekAudio() {
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl =
					"https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller

		val hiddenRange = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				source = "initial-resume"
			)
		)

		assertTrue(hiddenRange.controller.state.shellCoverVisible)
		assertEquals(emptyList(), hiddenRange.engineCommands)
		assertNull(hiddenRange.whispersyncAudioSeekTarget)
		assertNull(hiddenRange.controller.state.whispersync.pendingAudioSeek?.target)
		assertNull(hiddenRange.controller.state.whispersync.sync.activeCueKey)
		assertNull(hiddenRange.controller.state.activeMediaOverlay)
	}

	@Test
	fun shellCoverDelayedTextPointCannotApplyOverlayOrSeekAudio() {
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl =
					"https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller

		val delayed = controller.onEngineEvent(
			ReaderEngineEvent.TextPoint(
				textHref = "Text/chapter1.xhtml",
				textOffset = 82,
				source = "native-long-press-command"
			)
		)

		assertEquals(controller, delayed.controller)
		assertEquals(emptyList(), delayed.engineCommands)
		assertNull(delayed.whispersyncAudioSeekTarget)
		assertNull(delayed.controller.state.whispersync.pendingAudioSeek)
		assertNull(delayed.controller.state.whispersync.sync.activeOverlayRequestId)
	}

	@Test
	fun shellCoverStopsLatePlayingCallbackWithoutApplyingOverlay() {
		val controller = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl =
					"https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller

		val delayed = controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		)

		assertEquals(emptyList(), delayed.engineCommands)
		assertEquals(ReaderReadaloudPlaybackCommand.StopAndReset, delayed.readaloudPlaybackCommand)
		assertFalse(delayed.controller.state.chrome.readaloudPlayback.isPlaying)
		assertNull(delayed.controller.state.whispersync.sync.activeOverlayRequestId)
	}

	@Test
	fun returningToShellCoverClearsPendingOverlayBeforeFreshReadableRange() {
		val readableLocator = ReaderLocator(
			href = "Text/chapter1.xhtml",
			progress = 0.12,
			pageIndex = 4,
			pageCount = 100,
			reason = "shell-cover-dismiss:1"
		)
		val readable = ReaderController().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				startLocator = readableLocator,
				nativeShellCoverUrl =
					"https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).controller
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = readableLocator,
				tocTitle = "Chapter 1"
			)
		).controller.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = readable.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140
			)
		)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val firstFragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val firstRequest = firstFragment.overlayRequestId

		val covered = pending.controller.onBack()

		assertTrue(covered.controller.state.shellCoverVisible)
		assertEquals(
			listOf(ReaderEngineCommand.ClearMediaOverlay),
			covered.engineCommands
		)
		assertNull(covered.controller.state.whispersync.sync.activeCueKey)
		assertNull(covered.controller.state.whispersync.pendingAudioSeek?.target)

		val delayedActive = covered.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(firstFragment)
		)
		assertEquals(covered.controller, delayedActive.controller)
		assertEquals(emptyList(), delayedActive.engineCommands)
		assertNull(delayedActive.whispersyncAudioSeekTarget)

		val forward = covered.controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val acknowledged = forward.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = readableLocator.copy(reason = "shell-cover-dismiss:2"),
				tocTitle = "Chapter 1"
			)
		)
		assertEquals(
			listOf(ReaderEngineCommand.RequestVisibleTextRange("shell-cover-dismissed")),
			acknowledged.engineCommands
		)

		val freshPrepared = acknowledged.controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				source = "shell-cover-dismissed"
			)
		)
		val fresh = freshPrepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val freshRequest = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			fresh.engineCommands.single()
		).fragment.overlayRequestId
		assertTrue(requireNotNull(freshRequest) > requireNotNull(firstRequest))
		assertNull(fresh.whispersyncAudioSeekTarget)
	}

	@Test
	fun cueMapHoldReseeksTheAlreadyActiveCueForPlayingAndPausedTransport() {
		val enabled = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val exactBoundary = enabled.copy(
			state = enabled.state.copy(
				chrome = enabled.state.chrome.copy(
					settings = enabled.state.chrome.settings.copy(whispersyncHighlightLeadMs = 0)
				)
			)
		)
		val playingStep = exactBoundary.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 5_000L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		val activeFragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			playingStep.engineCommands.first { it is ReaderEngineCommand.ApplyMediaOverlay }
		).fragment
		val active = playingStep.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(activeFragment)
		).controller
		val paused = active.copy(
			state = active.state.copy(
				chrome = active.state.chrome.copy(
					readaloudPlayback = active.state.chrome.readaloudPlayback.copy(isPlaying = false)
				),
				whispersync = active.state.whispersync.copy(
					transportPhase = ReaderWhispersyncTransportPhase.Ready,
					userPaused = true
				)
			)
		)

		listOf("playing" to active, "paused" to paused).forEach { (name, controller) ->
			val cueMap = controller.state.whispersync.cueMap
			val selected = controller.onEngineEvent(
				ReaderEngineEvent.WhispersyncCueMapSeekRequested(
					sourceOrdinal = 1,
					revisionDigest = "5f04c2a19e7d",
					presentationGeneration = cueMap.presentationGeneration,
					destinationCommitIdentity = whispersyncTestDestination
				)
			)

			assertEquals(5_000L, selected.whispersyncAudioSeekTarget?.positionMs, name)
			assertEquals(1, selected.controller.state.whispersync.cueMap.requestedSourceOrdinal, name)
			assertTrue(selected.controller.state.whispersync.cueMap.transportAcknowledgementPending, name)
			assertEquals(activeFragment, selected.controller.state.activeMediaOverlay, name)
			assertTrue(
				selected.engineCommands.none { it is ReaderEngineCommand.ApplyMediaOverlay },
				name
			)
			assertEquals(
				1,
				selected.engineCommands.count { it is ReaderEngineCommand.ReplaceWhispersyncCueMap },
				name
			)
		}
	}

	@Test
	fun sourceOrdinalMatchingRoundsFractionalMillisecondBoundaries() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "fractional-boundary",
					audioResource = "Audio/chapter01.m4b",
					startMs = 1_001L,
					endMs = 2_001L,
					textHref = "Text/chapter1.xhtml",
					textStart = 10,
					textEnd = 42,
					sourceOrdinal = 7
				)
			)
		)
		val fragment = ReaderOverlayFragment(
			resourceHref = "Audio/chapter01.m4b",
			textHref = "Text/chapter1.xhtml",
			clipBeginSeconds = 1.001,
			clipEndSeconds = 2.001,
			textStart = 10,
			textEnd = 42
		)

		assertEquals(7, timeline.sourceOrdinalFor(fragment))
	}

	@Test
	fun cueMapTransportAcknowledgementRequiresRequestedTargetPositionAndGeneration() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val selected = controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapSeekRequested(
				sourceOrdinal = 1,
				revisionDigest = "5f04c2a19e7d",
				presentationGeneration = controller.state.whispersync.cueMap.presentationGeneration,
				destinationCommitIdentity = whispersyncTestDestination
			)
		).controller

		val stalePosition = selected.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 7_500L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		assertTrue(stalePosition.controller.state.whispersync.cueMap.transportAcknowledgementPending)
		assertTrue(
			stalePosition.controller.state.whispersync.cueMap.transitionTrail.none {
				it.outcome == ReaderWhispersyncCueMapTransitionOutcome.TransportAcknowledged
			}
		)

		val staleGenerationController = selected.copy(
			state = selected.state.copy(
				whispersync = selected.state.whispersync.copy(
					cueMap = selected.state.whispersync.cueMap.copy(
						presentationGeneration =
							selected.state.whispersync.cueMap.presentationGeneration + 1L
					)
				)
			)
		)
		val staleGeneration = staleGenerationController.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 5_000L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		assertTrue(staleGeneration.controller.state.whispersync.cueMap.transportAcknowledgementPending)

		val acknowledged = stalePosition.controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 5_000L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		assertFalse(acknowledged.controller.state.whispersync.cueMap.transportAcknowledgementPending)
		assertEquals(
			ReaderWhispersyncTransportPhase.Ready,
			acknowledged.controller.state.whispersync.transportPhase
		)
		val playbackControl = readerWhispersyncPlaybackControlState(
			status = acknowledged.controller.state.whispersync.status,
			playbackState = acknowledged.controller.state.chrome.readaloudPlayback,
			hasPreparedVisibleTarget =
				acknowledged.controller.state.whispersync.preparedVisibleTarget != null,
			transportPhase = acknowledged.controller.state.whispersync.transportPhase
		)
		assertFalse(playbackControl.loading)
		assertTrue(playbackControl.enabled)
		assertEquals(ReaderWhispersyncPlaybackControlDescription.Play, playbackControl.contentDescription)
	}

	@Test
	fun overlayEventsDoNotEmitRedundantCueMapReplacementWhenPresentationIsUnchanged() {
		val enabled = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val playback = enabled.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 5_500L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		val fragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			playback.engineCommands.first { it is ReaderEngineCommand.ApplyMediaOverlay }
		).fragment
		val receipt = testAnchorReceipt("session-a")
		val confirmed = playback.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(fragment, receipt)
		).controller

		val repeatedActive = confirmed.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(fragment, receipt)
		)
		assertTrue(
			repeatedActive.engineCommands.none { it is ReaderEngineCommand.ReplaceWhispersyncCueMap }
		)
		val progress = repeatedActive.controller.updateMediaOverlayProgress(
			fragment.copy(textProgressEnd = 120, textProgressFraction = 0.5)
		)
		assertTrue(progress.engineCommands.none { it is ReaderEngineCommand.ReplaceWhispersyncCueMap })

		val disabled = repeatedActive.controller.toggleWhispersyncCueMap().controller
		val disabledActive = disabled.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(fragment, receipt)
		)
		assertTrue(
			disabledActive.engineCommands.none { it is ReaderEngineCommand.ReplaceWhispersyncCueMap }
		)
	}

	@Test
	fun overlayProgressProjectsCueMapPresentationOnlyWhenCueMapStateChanges() {
		val enabled = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val playback = enabled.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 5_500L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		val fragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			playback.engineCommands.first { it is ReaderEngineCommand.ApplyMediaOverlay }
		).fragment
		val event = ReaderEngineEvent.MediaOverlayActive(
			fragment = fragment,
			anchorReceipt = testAnchorReceipt("session-a")
		)
		var changedProjectionCount = 0
		var changedSourceOrdinalScanCount = 0

		val changed = ReaderOverlayReducer.onActive(
			controller = playback.controller,
			event = event,
			resolveCueMapSourceOrdinal = { timeline, overlayFragment ->
				changedSourceOrdinalScanCount += 1
				timeline?.sourceOrdinalFor(overlayFragment)
			},
			projectCueMapPresentation = { cueMap, state ->
				changedProjectionCount += 1
				cueMap.presentation(state)
			}
		)

		assertEquals(1, changedSourceOrdinalScanCount)
		assertEquals(1, changedProjectionCount)
		assertEquals(
			1,
			changed.engineCommands.count { it is ReaderEngineCommand.ReplaceWhispersyncCueMap }
		)
		var unchangedProjectionCount = 0
		var unchangedSourceOrdinalScanCount = 0

		val unchanged = ReaderOverlayReducer.onActive(
			controller = changed.controller,
			event = event,
			resolveCueMapSourceOrdinal = { timeline, overlayFragment ->
				unchangedSourceOrdinalScanCount += 1
				timeline?.sourceOrdinalFor(overlayFragment)
			},
			projectCueMapPresentation = { cueMap, state ->
				unchangedProjectionCount += 1
				cueMap.presentation(state)
			}
		)

		assertEquals(0, unchangedSourceOrdinalScanCount)
		assertEquals(0, unchangedProjectionCount)
		assertTrue(
			unchanged.engineCommands.none { it is ReaderEngineCommand.ReplaceWhispersyncCueMap }
		)

		val disabled = changed.controller.toggleWhispersyncCueMap().controller
		var disabledProjectionCount = 0
		var disabledSourceOrdinalScanCount = 0
		val disabledUnchanged = ReaderOverlayReducer.onActive(
			controller = disabled,
			event = event,
			resolveCueMapSourceOrdinal = { timeline, overlayFragment ->
				disabledSourceOrdinalScanCount += 1
				timeline?.sourceOrdinalFor(overlayFragment)
			},
			projectCueMapPresentation = { cueMap, state ->
				disabledProjectionCount += 1
				cueMap.presentation(state)
			}
		)

		assertEquals(0, disabledSourceOrdinalScanCount)
		assertEquals(0, disabledProjectionCount)
		assertTrue(
			disabledUnchanged.engineCommands.none {
				it is ReaderEngineCommand.ReplaceWhispersyncCueMap
			}
		)
	}

	@Test
	fun cueMapRenderedRejectsMarkerGeometryOutsideCurrentVisibleCueSet() {
		val enabled = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val presentation = requireNotNull(enabled.state.whispersync.cueMap.presentation(enabled.state))

		val rejected = enabled.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapRendered(
				sourceOrdinalsInDomReadingOrder = listOf(1, 0),
				revisionDigest = presentation.revisionDigest,
				presentationGeneration = presentation.presentationGeneration,
				destinationCommitIdentity = whispersyncTestDestination,
				markerReceipts = listOf(
					ReaderWhispersyncCueMapMarkerReceipt(
						sourceOrdinal = 99,
						prepared = false,
						requested = false,
						audioActive = false,
						renderedHighlight = false,
						anchorReceipt = testAnchorReceipt("session-a").copy(boundarySequence = 99L)
					)
				)
			)
		)

		assertNull(rejected.controller.state.whispersync.cueMap.geometryReceipt)
		assertEquals(emptyList(), rejected.controller.state.whispersync.cueMap.sourceOrdinalsInDomReadingOrder)
	}

	@Test
	fun sidecarRevisionReplacementDropsOldCueMapTransitionEvidenceEvenWhenDisabled() {
		val enabled = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val presentation = requireNotNull(
			enabled.state.whispersync.cueMap.presentation(enabled.state)
		)
		val rendered = enabled.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapRendered(
				sourceOrdinalsInDomReadingOrder = listOf(1, 0),
				revisionDigest = presentation.revisionDigest,
				presentationGeneration = presentation.presentationGeneration,
				destinationCommitIdentity = whispersyncTestDestination
			)
		).controller
		val disabled = rendered.toggleWhispersyncCueMap().controller
		val oldGeneration = disabled.state.whispersync.cueMap.presentationGeneration
		val replacementDigest = "aaaaaaaaaaaa"

		val replaced = disabled.loadWhispersyncSidecar(
			testWhispersyncSidecar().copy(revisionDigest = replacementDigest)
		).controller
		val cueMap = replaced.state.whispersync.cueMap
		val diagnostic = cueMap.productionDiagnosticSurface(replacementDigest)

		assertTrue(cueMap.presentationGeneration > oldGeneration)
		assertTrue(cueMap.transitionTrail.all { it.revisionDigest == replacementDigest })
		assertEquals(emptyList(), diagnostic.sourceOrdinals)
		assertEquals(
			listOf("g${cueMap.presentationGeneration}:-:-:replace"),
			diagnostic.tokens
		)
	}

	@Test
	fun productionCueMapIsOptInAndHoldCompletionSeeksOneExactVisibleSourceOrdinal() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller

		assertFalse(controller.state.whispersync.cueMap.enabled)
		val enabled = controller.toggleWhispersyncCueMap()
		val presentation = assertIs<ReaderEngineCommand.ReplaceWhispersyncCueMap>(
			enabled.engineCommands.single()
		).presentation
		assertTrue(enabled.controller.state.whispersync.cueMap.enabled)
		assertEquals(listOf(0, 1), presentation.cues.map(ReaderWhispersyncCueMapCue::sourceOrdinal))
		assertEquals("5f04c2a19e7d", presentation.revisionDigest)
		assertEquals(0, presentation.preparedSourceOrdinal)
		assertNull(presentation.requestedSourceOrdinal)

		val request = ReaderEngineEvent.WhispersyncCueMapSeekRequested(
			sourceOrdinal = 1,
			revisionDigest = presentation.revisionDigest,
			presentationGeneration = presentation.presentationGeneration,
			destinationCommitIdentity = whispersyncTestDestination
		)
		val selected = enabled.controller.onEngineEvent(request)

		assertEquals(5_000L, selected.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(1, selected.controller.state.whispersync.cueMap.requestedSourceOrdinal)
		assertTrue(selected.controller.state.whispersync.cueMap.transportAcknowledgementPending)
		assertIs<ReaderEngineCommand.ApplyMediaOverlay>(selected.engineCommands.first())
		val requestedPresentation = assertIs<ReaderEngineCommand.ReplaceWhispersyncCueMap>(
			selected.engineCommands.last()
		).presentation
		assertEquals(1, requestedPresentation.requestedSourceOrdinal)
		assertTrue(requestedPresentation.transportAcknowledgementPending)

		val repeated = selected.controller.onEngineEvent(request)
		assertNull(repeated.whispersyncAudioSeekTarget)
		assertTrue(repeated.engineCommands.isEmpty())
	}

	@Test
	fun pausedTransportPositionAcknowledgesCueSeekWithoutAudioActiveStyling() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val cueMap = controller.state.whispersync.cueMap
		val selected = controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapSeekRequested(
				sourceOrdinal = 1,
				revisionDigest = "5f04c2a19e7d",
				presentationGeneration = cueMap.presentationGeneration,
				destinationCommitIdentity = whispersyncTestDestination
			)
		).controller

		val acknowledged = selected.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 5_000L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		val presentation = assertIs<ReaderEngineCommand.ReplaceWhispersyncCueMap>(
			acknowledged.engineCommands.last()
		).presentation

		assertFalse(acknowledged.controller.state.whispersync.cueMap.transportAcknowledgementPending)
		assertNull(acknowledged.controller.state.whispersync.cueMap.audioActiveSourceOrdinal)
		assertFalse(presentation.transportAcknowledgementPending)
		assertNull(presentation.audioActiveSourceOrdinal)
		assertTrue(
			acknowledged.controller.state.whispersync.cueMap.transitionTrail.any {
				it.sourceOrdinal == 1 &&
					it.outcome == ReaderWhispersyncCueMapTransitionOutcome.TransportAcknowledged
			}
		)
	}

	@Test
	fun acknowledgedStoppedCueMapSeekRemainsStartAuthority() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val initialPrepared = requireNotNull(
			controller.state.whispersync.preparedVisibleTarget
		)
		assertEquals(0, initialPrepared.audioSeekTarget.segment.sourceOrdinal)
		val cueMap = controller.state.whispersync.cueMap
		val selected = controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapSeekRequested(
				sourceOrdinal = 1,
				revisionDigest = "5f04c2a19e7d",
				presentationGeneration = cueMap.presentationGeneration,
				destinationCommitIdentity = whispersyncTestDestination
			)
		)
		val selectedOverlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			selected.engineCommands.first()
		).fragment

		val acknowledged = selected.controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				audioResource = "Audio/chapter01.m4b",
				trackIndex = 0,
				positionMs = 5_000L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)
		val promoted = requireNotNull(
			acknowledged.controller.state.whispersync.preparedVisibleTarget
		)
		assertEquals(1, promoted.audioSeekTarget.segment.sourceOrdinal)
		assertEquals(5_000L, promoted.audioSeekTarget.positionMs)
		assertEquals(
			initialPrepared.preparationGeneration + 1L,
			promoted.preparationGeneration
		)

		val start = acknowledged.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val rearmedOverlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			start.engineCommands.single()
		).fragment
		assertEquals(selectedOverlay.fragmentId, rearmedOverlay.fragmentId)
		assertEquals(selectedOverlay.overlayRequestId, rearmedOverlay.overlayRequestId)
		assertEquals(5_000L, start.controller.state.whispersync.pendingAudioSeek?.target?.positionMs)
		assertNull(start.whispersyncAudioSeekTarget)
		assertNull(start.readaloudPlaybackCommand)

		val confirmed = start.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(rearmedOverlay)
		)
		assertEquals(1, confirmed.whispersyncAudioSeekTarget?.segment?.sourceOrdinal)
		assertEquals(5_000L, confirmed.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, confirmed.readaloudPlaybackCommand)
	}

	@Test
	fun unchangedCueMapPresentationDoesNotEmitReplacementCommand() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller

		val unchanged = controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				audioResource = null,
				positionMs = 0L,
				playbackSpeed = 1f,
				syncEnabled = true
			)
		)

		assertTrue(unchanged.engineCommands.none { it is ReaderEngineCommand.ReplaceWhispersyncCueMap })
	}

	@Test
	fun cueMapTransportAcknowledgementUsesExactTrackWhenPlanAndSidecarResourcesDiffer() {
		val digest = "5f04c2a19e7d"
		val requested = ReaderWhispersyncCueMapState()
			.toggled(digest)
			.requested(
				sourceOrdinal = 1,
				revisionDigest = digest,
				audioResource = "Audio/chapter01.m4b",
				audioTrackIndex = 0,
				positionMs = 5_000L
			)

		val wrongTrack = requested.transportAcknowledged(
			sourceOrdinal = 1,
			revisionDigest = digest,
			audioResource = "bindery-generated-resource",
			audioTrackIndex = 1,
			positionMs = 5_000L
		)
		assertTrue(wrongTrack.transportAcknowledgementPending)

		val acknowledged = wrongTrack.transportAcknowledged(
			sourceOrdinal = 1,
			revisionDigest = digest,
			audioResource = "bindery-generated-resource",
			audioTrackIndex = 0,
			positionMs = 5_000L
		)
		assertFalse(acknowledged.transportAcknowledgementPending)
	}

	@Test
	fun cueMapTrailRetainsTypedEvidenceAcrossRepeatedSameCueProgress() {
		val digest = "5f04c2a19e7d"
		var cueMap = ReaderWhispersyncCueMapState().toggled(digest)
		cueMap = cueMap.rendered(listOf(1, 0), digest)
		cueMap = cueMap.requested(
			sourceOrdinal = 1,
			revisionDigest = digest,
			audioResource = "Audio/chapter01.m4b",
			audioTrackIndex = 0,
			positionMs = 5_000L
		)
		cueMap = cueMap.transportAcknowledged(
			sourceOrdinal = 1,
			revisionDigest = digest,
			audioResource = "Audio/chapter01.m4b",
			audioTrackIndex = 0,
			positionMs = 5_000L
		)
		cueMap = cueMap.renderedHighlight(1, digest)
		cueMap = cueMap.holdOutcome(
			sourceOrdinal = 1,
			revisionDigest = digest,
			outcome = ReaderWhispersyncCueMapHoldOutcome.CancelledMovement
		)
		repeat(ReaderWhispersyncCueMapTransitionLimit * 2) {
			cueMap = cueMap.audioActive(1, digest)
		}

		assertEquals(
			listOf(1, 0),
			cueMap.transitionTrail
				.filter { it.state == ReaderWhispersyncCueMapMarkerState.Mapped }
				.mapNotNull(ReaderWhispersyncCueMapTransition::sourceOrdinal)
		)
		assertTrue(cueMap.transitionTrail.any {
			it.outcome == ReaderWhispersyncCueMapTransitionOutcome.SeekRequested
		})
		assertTrue(cueMap.transitionTrail.any {
			it.outcome == ReaderWhispersyncCueMapTransitionOutcome.Rendered
		})
		assertEquals(
			ReaderWhispersyncCueMapHoldOutcome.CancelledMovement,
			cueMap.transitionTrail.single {
				it.outcome == ReaderWhispersyncCueMapTransitionOutcome.HoldCancelled
			}.holdOutcome
		)
		val diagnostic = cueMap.productionDiagnosticSurface(digest)
		assertTrue("g1:1:r:seek" in diagnostic.tokens)
		assertTrue("g1:1:h:render" in diagnostic.tokens)
		assertTrue("g1:1:-:cancelled-movement" in diagnostic.tokens)
		assertTrue(diagnostic.label.contains("g1:1:r:seek"))
		assertTrue(diagnostic.tokens.size <= ReaderWhispersyncCueMapTransitionLimit)
	}

	@Test
	fun cueMapGenerationReplacementRejectsOldHoldAndKeepsBoundedOrdinalOnlyEvidence() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 0,
					visibleEnd = 160
				)
			).controller
			.toggleWhispersyncCueMap().controller
		val original = requireNotNull(controller.state.whispersync.cueMap.presentation(controller.state))

		val rendered = controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapRendered(
				sourceOrdinalsInDomReadingOrder = listOf(1, 0),
				revisionDigest = original.revisionDigest,
				presentationGeneration = original.presentationGeneration,
				destinationCommitIdentity = whispersyncTestDestination
			)
		).controller
		val replaced = rendered.onEngineEvent(
			ReaderEngineEvent.SettingsPresentationCommitted(snapshotKey = 42)
		)
		val replacement = assertIs<ReaderEngineCommand.ReplaceWhispersyncCueMap>(
			replaced.engineCommands.single()
		).presentation
		assertTrue(replacement.presentationGeneration > original.presentationGeneration)
		assertEquals(listOf(1, 0), replaced.controller.state.whispersync.cueMap.transitionTrail
			.filter { it.state == ReaderWhispersyncCueMapMarkerState.Mapped }
			.takeLast(2)
			.mapNotNull(ReaderWhispersyncCueMapTransition::sourceOrdinal))
		assertTrue(replaced.controller.state.whispersync.cueMap.transitionTrail.size <= 32)
		assertFalse(replaced.controller.state.whispersync.cueMap.transitionTrail.toString().contains("chapter1"))
		assertFalse(replaced.controller.state.whispersync.cueMap.transitionTrail.toString().contains("book-1"))
		val diagnosticSurface = replaced.controller.state.whispersync.cueMap.productionDiagnosticSurface(
			revisionDigest = replacement.revisionDigest
		)
		assertEquals("5f04c2a19e7d", diagnosticSurface.revisionDigest)
		assertEquals(listOf(1, 0), diagnosticSurface.sourceOrdinals)
		assertEquals(
			listOf("g1:1:m:project", "g1:0:m:project", "g2:-:-:replace"),
			diagnosticSurface.tokens.takeLast(3)
		)
		assertEquals(
			"cue-map 5f04c2a19e7d · g1:-:-:replace→g1:1:m:project→g1:0:m:project→g2:-:-:replace",
			diagnosticSurface.label
		)
		assertTrue(diagnosticSurface.sourceOrdinals.size <= 32)

		val stale = replaced.controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapSeekRequested(
				sourceOrdinal = 1,
				revisionDigest = original.revisionDigest,
				presentationGeneration = original.presentationGeneration,
				destinationCommitIdentity = whispersyncTestDestination
			)
		)
		assertNull(stale.whispersyncAudioSeekTarget)
		assertTrue(stale.engineCommands.isEmpty())
	}

	@Test
	fun explicitWhispersyncTextPointFeedsControllerSyncAndAudioSeekTarget() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val prepared = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 10,
				visibleEnd = 42
			)
		)
		val start = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val first = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			start.engineCommands.single()
		).fragment
		val enabled = start.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(first)
		).controller
		val reserved = enabled.onViewerAction(
			ReaderViewerAction.ContentLongPressAt(10.0, 20.0, 100.0, 100.0)
		)

		val causalSequence = requireNotNull(
			reserved.controller.state.whispersync.pendingCausalIntent?.sequence
		)
		val step = reserved.controller.onEngineEvent(
			ReaderEngineEvent.TextPoint(
				textHref = "Text/chapter1.xhtml",
				textOffset = 82,
				rangeCfi = "epubcfi(/6/2!/4/4,/1:2,/1:10)",
				source = "native-long-press-command",
				causalSequence = causalSequence,
				destinationCommitIdentity = whispersyncTestDestination
			)
		)

		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(step.engineCommands.single())
		assertEquals("seg-2", overlay.fragment.fragmentId)
		assertNotNull(overlay.fragment.overlayRequestId)
		assertEquals(5_000L, step.whispersyncAudioSeekTarget?.positionMs)
		assertNull(step.controller.state.whispersync.pendingAudioSeek)
		assertNull(step.controller.state.audioMetadataLabel)
		assertNull(step.controller.state.activeMediaOverlay)
	}

	@Test
	fun loadingWhispersyncSidecarPreparesExistingVisibleTextRangeWithoutSeeking() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination()
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 80,
					visibleEnd = 140,
					rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)"
				)
			).controller

		val step = controller.loadWhispersyncSidecar(testWhispersyncSidecar())

		assertTrue(step.engineCommands.isEmpty())
		assertNull(step.whispersyncAudioSeekTarget)
		assertEquals(
			5_000L,
			step.controller.state.whispersync.preparedVisibleTarget
				?.audioSeekTarget?.positionMs
		)
		assertNull(step.controller.state.whispersync.pendingAudioSeek)
		assertEquals(
			ReaderWhispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)",
				destinationCommitIdentity = whispersyncTestDestination
			),
			step.controller.state.whispersync.visibleTextRange
		)
		assertEquals(
			ReaderWhispersyncTransportPhase.Ready,
			step.controller.state.whispersync.transportPhase
		)
		assertNull(step.controller.state.activeMediaOverlay)
		assertNull(step.controller.state.audioMetadataLabel)
	}

	@Test
	fun pausedAudiobookPositionDoesNotClearPreparedVisibleTarget() {
		val synced = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 80,
					visibleEnd = 140,
					rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)"
				)
			).controller
		assertEquals(
			5_000L,
			synced.state.whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs
		)
		assertNull(synced.state.whispersync.sync.engineCommand)

		val paused = synced.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				trackIndex = 0,
				audioResource = "Audio/chapter99.m4b",
				positionMs = 393_734L,
				durationMs = 1_000_000L
			)
		)

		assertEquals(emptyList(), paused.engineCommands)
		assertNull(paused.controller.state.activeMediaOverlay)
		assertNull(paused.controller.state.audioMetadataLabel)
		assertEquals(ReaderWhispersyncStatusKind.Ready, paused.controller.state.whispersync.status.kind)
		assertEquals(
			5_000L,
			paused.controller.state.whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs
		)
	}

	@Test
	fun loadedWhispersyncSidecarExposesControllerOwnedReadyStatus() {
		val step = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar())

		assertEquals(
			ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Ready,
				message = ReaderWhispersyncStatusMessage.Ready,
				syncedSegmentCount = 2
			),
			step.controller.state.whispersync.status
		)
	}

	@Test
	fun whispersyncLoadFailureSurfacesControllerOwnedAttentionStatus() {
		val step = ReaderController()
			.open(hobbitOpenRequest()).controller
			.reportWhispersyncLoadFailure(
				message = ReaderWhispersyncStatusMessage.AudioUnavailable,
				detail = null
			)

		assertEquals(
			ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.LoadFailed,
				message = ReaderWhispersyncStatusMessage.AudioUnavailable
			),
			step.controller.state.whispersync.status
		)
		assertEquals(true, step.controller.state.whispersync.status.requiresAttention)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun visibleTextRangePreparesTargetWithoutEnteringSeekingStatus() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller

		val step = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)"
			)
		)

		assertEquals(
			ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Ready,
				message = ReaderWhispersyncStatusMessage.Ready,
				syncedSegmentCount = 2
			),
			step.controller.state.whispersync.status
		)
		assertEquals(
			5_000L,
			step.controller.state.whispersync.preparedVisibleTarget
				?.audioSeekTarget?.positionMs
		)
		assertNull(step.whispersyncAudioSeekTarget)
		assertTrue(step.engineCommands.isEmpty())
	}

	@Test
	fun visibleTextRangeWithoutCueClearsPreparedTargetWithoutOverlayCommands() {
		val synced = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 80,
					visibleEnd = 140
				)
			).controller
		assertNotNull(synced.state.whispersync.preparedVisibleTarget)
		assertNull(synced.state.whispersync.pendingAudioSeek)
		assertNull(synced.state.activeMediaOverlay)

		val step = synced.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/appendix.xhtml",
				visibleStart = 1,
				visibleEnd = 60
			)
		)

		assertEquals(
			ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Ready,
				message = ReaderWhispersyncStatusMessage.Ready,
				syncedSegmentCount = 2
			),
			step.controller.state.whispersync.status
		)
		assertNull(step.controller.state.whispersync.pendingAudioSeek)
		assertNull(step.controller.state.whispersync.preparedVisibleTarget)
		assertNull(step.controller.state.activeMediaOverlay)
		assertNull(step.controller.state.audioMetadataLabel)
		assertEquals(emptyList(), step.engineCommands)
		assertNull(step.whispersyncAudioSeekTarget)
	}

	@Test
	fun audiobookPlaybackOutsideTimelineSurfacesNeutralNoActiveCueStatus() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller

		val step = controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter99.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		)

		assertEquals(
			ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.NoActiveCue,
				message = ReaderWhispersyncStatusMessage.NoActiveCue,
				audioResource = "Audio/chapter99.m4b",
				positionMs = 5_500L
			),
			step.controller.state.whispersync.status
		)

		val unscopedInactive = step.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayInactive(reason = "document-loaded")
		)
		assertNull(unscopedInactive.readaloudPlaybackCommand)
		assertTrue(unscopedInactive.controller.state.chrome.readaloudPlayback.isPlaying)
		assertEquals(step.controller.state.whispersync, unscopedInactive.controller.state.whispersync)
	}

	@Test
	fun repairWhispersyncMismatchReusesPreparedVisibleTargetWithoutSeeking() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val synced = controller.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)"
			)
		).controller
		val mismatched = synced.withRepairableWhispersyncMismatch()

		val step = mismatched.repairWhispersyncMismatch()

		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(step.engineCommands.single())
		assertEquals("seg-2", overlay.fragment.fragmentId)
		assertEquals("Second sentence", overlay.fragment.label)
		assertNull(step.whispersyncAudioSeekTarget)
		assertNull(step.controller.state.whispersync.pendingAudioSeek)
		assertEquals(
			"Audio/chapter01.m4b",
			step.controller.state.whispersync.preparedVisibleTarget
				?.audioSeekTarget?.audioResource
		)
		assertEquals(
			5_000L,
			step.controller.state.whispersync.preparedVisibleTarget
				?.audioSeekTarget?.positionMs
		)
		assertEquals(
			ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Ready,
				message = ReaderWhispersyncStatusMessage.Ready,
				syncedSegmentCount = 2
			),
			step.controller.state.whispersync.status
		)
		assertNull(step.controller.state.activeMediaOverlay)
		assertNull(step.controller.state.audioMetadataLabel)
	}

	@Test
	fun repairWhispersyncMismatchNoopsWithoutVisibleTextRangeSeekTarget() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val mismatched = controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter99.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		).controller

		val step = mismatched.repairWhispersyncMismatch()

		assertEquals(mismatched, step.controller)
		assertEquals(emptyList(), step.engineCommands)
		assertNull(step.whispersyncAudioSeekTarget)
	}

	@Test
	fun repairWhispersyncMismatchNoopsForLoadFailureStatus() {
		val visible = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 80,
					visibleEnd = 140
				)
			).controller
		val failed = visible.reportWhispersyncLoadFailure(
			message = ReaderWhispersyncStatusMessage.AudioUnavailable,
			detail = null
		).controller

		val step = failed.repairWhispersyncMismatch()

		assertEquals(failed, step.controller)
		assertEquals(emptyList(), step.engineCommands)
		assertNull(step.whispersyncAudioSeekTarget)
	}

	@Test
	fun whispersyncAudiobookPlaybackStateFeedsControllerHighlightOverlay() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller

		val step = controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		)

		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(step.engineCommands.single())
		assertEquals("seg-2", overlay.fragment.fragmentId)
		assertEquals("Second sentence", overlay.fragment.label)
		assertNull(step.controller.state.activeMediaOverlay)
		assertNull(step.controller.state.audioMetadataLabel)
		assertNull(step.whispersyncAudioSeekTarget)
	}

	@Test
	fun whispersyncAudiobookPlaybackSuppressesNormalTextSelectionActions() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onReadaloudPlaybackState(
				ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = true,
					trackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					positionMs = 5_500L,
					durationMs = 8_000L
				)
			).controller

		val step = controller.onEngineEvent(
			ReaderEngineEvent.SelectionChanged(
				text = "selected word",
				cfi = "epubcfi(/6/2!/4/4,/1:2,/1:10)",
				href = "Text/chapter1.xhtml"
			)
		)

		assertNull(step.controller.state.selection)
		assertFalse(step.controller.state.selectionActions.visible)
	}

	@Test
	fun playbackDrivenOverlayConfirmationDoesNotSeekAudiobookAgain() {
		val controller = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
		val playbackStep = controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		)
		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			playbackStep.engineCommands.single()
		).fragment

		val confirmed = playbackStep.controller.onEngineEvent(ReaderEngineEvent.MediaOverlayActive(overlay))

		assertEquals(emptyList(), confirmed.engineCommands)
		assertNull(confirmed.whispersyncAudioSeekTarget)
		assertEquals(overlay, confirmed.controller.state.activeMediaOverlay)
		assertEquals("Second sentence", confirmed.controller.state.audioMetadataLabel)
		assertEquals(ReaderWhispersyncStatusKind.Playing, confirmed.controller.state.whispersync.status.kind)
	}

	@Test
	fun pausingAfterTransientSeekClearsPlaybackDrivenOverlayAndStatus() {
		val pending = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onReadaloudPlaybackState(
				ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = true,
					trackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					positionMs = 5_500L,
					durationMs = 8_000L
				)
			)
		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val receipt = ReaderWhispersyncAnchorReceipt(
			foliateSessionId = "session-a",
			destinationCommitToken = "settled-4",
			visualPageOrdinal = 4,
			spineIndex = 0,
			rasterGeneration = 12L,
			textureGeneration = 13L,
			presentationMutationGeneration = 3L,
			presentationSequence = 4L,
			anchorGeneration = 5L,
			boundarySequence = requireNotNull(overlay.overlayRequestId),
			layoutGeneration = 14L,
			viewGeneration = 15L,
			commitSequence = 16L,
			committedSpineIndex = 0,
			committedChapterPageIndex = 0,
			committedChapterPageCount = 1,
			paginationFingerprint = "pagination",
			layoutFingerprint = "layout",
			readerSettingsRasterKey = "settings",
			captureGeometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 600.0,
				viewportHeight = 800.0,
				mode = ReaderPageTurnLayoutMode.Single,
				pages = listOf(
					ReaderPageTurnPageRect(
						ReaderPageTurnPageRole.Full,
						30.0,
						0.0,
						540.0,
						800.0
					)
				)
			),
			pageLocalRects = listOf(
				ReaderWhispersyncPageLocalRect(
					ReaderPageTurnPageRole.Full,
					18.0,
					40.0,
					62.0,
					24.0
				)
			)
		)
		val playing = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(overlay, receipt)
		).controller
		assertEquals(ReaderWhispersyncStatusKind.Playing, playing.state.whispersync.status.kind)
		assertNotNull(playing.state.activeMediaOverlay)
		assertEquals(receipt, playing.state.activeMediaOverlayAnchorReceipt)

		val seeking = playing.copy(
			state = playing.state.copy(
				whispersync = playing.state.whispersync.copy(
					status = ReaderWhispersyncStatus(
						kind = ReaderWhispersyncStatusKind.SeekingAudio,
						message = ReaderWhispersyncStatusMessage.SeekingAudio
					)
				)
			)
		)
		val paused = seeking.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		)

		assertEquals(listOf(ReaderEngineCommand.ClearMediaOverlay), paused.engineCommands)
		assertNull(paused.controller.state.activeMediaOverlay)
		assertNull(paused.controller.state.activeMediaOverlayAnchorReceipt)
		assertNull(paused.controller.state.audioMetadataLabel)
		assertEquals(
			ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				message = ReaderWhispersyncStatusMessage.Paused,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L
			),
			paused.controller.state.whispersync.status
		)
	}

	@Test
	fun audioFollowVisibleRangeDoesNotSeekAudiobookBackToReaderViewport() {
		val pending = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onReadaloudPlaybackState(
				ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = true,
					trackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					positionMs = 5_500L,
					durationMs = 8_000L
				)
			)
		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val playing = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(overlay)
		).controller
		val audioOverlay = playing.state.activeMediaOverlay

		val step = playing.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 10,
				visibleEnd = 42,
				source = "media-overlay-follow"
			)
		)

		assertEquals(emptyList(), step.engineCommands)
		assertNull(step.whispersyncAudioSeekTarget)
		assertEquals(audioOverlay, step.controller.state.activeMediaOverlay)
		assertEquals("Second sentence", step.controller.state.audioMetadataLabel)
		assertEquals(
			ReaderWhispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 10,
				visibleEnd = 42,
				source = "media-overlay-follow",
				destinationCommitIdentity = whispersyncTestDestination
			),
			step.controller.state.whispersync.visibleTextRange
		)
		assertEquals(ReaderWhispersyncStatusKind.Playing, step.controller.state.whispersync.status.kind)
	}

	@Test
	fun pageSpanningWhispersyncCueKeepsPlayingUntilTheActiveSentenceFinishes() {
		val prepared = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 100,
					visibleEnd = 120,
					source = "reader-visible-page"
				)
			)
		val pending = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			pending.engineCommands.single()
		).fragment
		val confirmed = pending.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(overlay)
		).controller

		val step = confirmed.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 7_500L,
				durationMs = 8_000L
			)
		)

		assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(step.engineCommands.single())
		assertNull(step.readaloudPlaybackCommand)
		assertNotNull(step.controller.state.activeMediaOverlay)
		assertEquals(ReaderWhispersyncStatusKind.Playing, step.controller.state.whispersync.status.kind)
	}

	@Test
	fun playbackCueOutsideVisiblePageStopsWhispersyncInsteadOfFollowingNextChapter() {
		val visible = ReaderController()
			.open(hobbitOpenRequest()).controller
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				whispersyncVisibleTextRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 10,
					visibleEnd = 42,
					source = "reader-visible-page"
				)
			).controller

		val step = visible.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				trackIndex = 0,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 5_500L,
				durationMs = 8_000L
			)
		)

		assertEquals(listOf(ReaderEngineCommand.ClearMediaOverlay), step.engineCommands)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, step.readaloudPlaybackCommand)
		assertNull(step.whispersyncAudioSeekTarget)
		assertNull(step.controller.state.activeMediaOverlay)
		assertNull(step.controller.state.audioMetadataLabel)
		assertEquals(ReaderWhispersyncStatusKind.NoActiveCue, step.controller.state.whispersync.status.kind)
		assertEquals(
			ReaderWhispersyncStatusMessage.VisiblePageEnded,
			step.controller.state.whispersync.status.message
		)
	}

	@Test
	fun audioFollowRelocationDoesNotOverwriteReaderOwnedProgress() {
		val opened = ReaderController().open(hobbitOpenRequest()).controller
		val ready = opened.onEngineEvent(ReaderEngineEvent.PublicationReady).controller
		val synced = ready
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onReadaloudPlaybackState(
				ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = true,
					trackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					positionMs = 5_500L,
					durationMs = 8_000L
				)
			).controller
		val saved = synced.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(
					href = "Text/chapter1.xhtml#reader",
					progress = 0.42,
					reason = "relocate-committed"
				),
				tocTitle = "Chapter 1"
			)
		)

		val audioFollow = saved.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = ReaderLocator(
					href = "Text/chapter1.xhtml#seg-2",
					progress = 0.09,
					reason = "media-overlay-follow"
				),
				tocTitle = "Chapter 1"
			)
		)

		assertEquals(0.42, saved.progressToSave?.progressFraction)
		assertNull(audioFollow.progressToSave)
		assertEquals(
			saved.controller.state.readingProgress,
			audioFollow.controller.state.readingProgress
		)
		assertEquals(
			saved.controller.state.whispersync.pendingAudioSeek?.target,
			audioFollow.controller.state.whispersync.pendingAudioSeek?.target
		)
	}

	@Test
	fun confirmedWhispersyncStartExportsCurrentLocatorForExactCompanionSave() {
		val opened = ReaderController().open(hobbitOpenRequest()).controller
		val ready = opened.onEngineEvent(ReaderEngineEvent.PublicationReady).controller
		val located = ready
			.withWhispersyncTestDestination().loadWhispersyncSidecar(testWhispersyncSidecar()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "Text/chapter1.xhtml#reader",
						cfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)",
						progress = 0.42,
						reason = "whispersync-companion-progress-cue"
					),
					tocTitle = "Chapter 1"
				)
			).controller

		val prepared = located.onEngineEvent(
			whispersyncVisibleTextRange(
				textHref = "Text/chapter1.xhtml",
				visibleStart = 80,
				visibleEnd = 140,
				source = "whispersync-companion-progress-cue"
			)
		)
		assertNull(prepared.progressToSave)
		assertNull(prepared.whispersyncAudioSeekTarget)
		val start = prepared.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val overlay = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			start.engineCommands.single()
		).fragment
		val step = start.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(overlay)
		)

		assertEquals("Audio/chapter01.m4b", step.whispersyncAudioSeekTarget?.audioResource)
		assertEquals(5_000L, step.whispersyncAudioSeekTarget?.positionMs)
		assertNull(step.controller.state.whispersync.pendingAudioSeek)
		assertEquals(
			BinderyReadingProgress(
				bookId = "book-1",
				kind = BinderyReadingProgressKind.Ebook,
				resourceKey = "publication.epub",
				href = "publication.epub",
				resourceHref = "publication.epub",
				textHref = "Text/chapter1.xhtml",
				cfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)",
				fragmentId = "reader",
				progressFraction = 0.42
			),
			step.progressToSave
		)
	}

	@Test
	fun selectionActionStateIsControllerOwnedAndClearedByEngine() {
		val opened = ReaderController().open(hobbitOpenRequest()).controller
		val textOnly = opened.onEngineEvent(
			ReaderEngineEvent.SelectionChanged(
				text = "  copy only  ",
				cfi = "  ",
				href = "chapter-01.xhtml"
			)
		).controller
		val actionable = opened.onEngineEvent(
			ReaderEngineEvent.SelectionChanged(
				text = "  In a hole in the ground  ",
				cfi = " epubcfi(/6/8!/4/2:0) ",
				href = " chapter-01.xhtml "
			)
		).controller
		val draftingNote = actionable.startSelectionNote().controller
		val cleared = draftingNote.onEngineEvent(ReaderEngineEvent.SelectionCleared).controller

		assertEquals(ReaderSelectionActionState(), opened.state.selectionActions)
		assertEquals(
			ReaderSelectionActionState(
				selectedText = "copy only",
				selectedHref = "chapter-01.xhtml",
				canCopy = true
			),
			textOnly.state.selectionActions
		)
		assertEquals(
			ReaderSelectionActionState(
				selectedText = "In a hole in the ground",
				selectedCfi = "epubcfi(/6/8!/4/2:0)",
				selectedHref = "chapter-01.xhtml",
				canCopy = true,
				canHighlight = true,
				canNote = true
			),
			actionable.state.selectionActions
		)
		assertEquals(ReaderSelectionActionState(), cleared.state.selectionActions)
		assertNull(cleared.state.selection)
		assertNull(cleared.state.selectionNoteDraft)
	}

	@Test
	fun selectionNotesStartNativeDraftWithoutEngineCommands() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "chapter-01.xhtml",
						cfi = "epubcfi(/6/8!/4/1:0)",
						progress = 0.24
					),
					tocTitle = "Chapter 1"
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.SelectionChanged(
					text = " The note sentence ",
					cfi = " epubcfi(/6/8!/4/1:12) ",
					href = " chapter-01.xhtml "
				)
			).controller

		val step = controller.startSelectionNote()

		assertEquals(
			ReaderSelectionNoteDraft(
				bookId = "book-1",
				bookTitle = "The Hobbit",
				text = "The note sentence",
				cfi = "epubcfi(/6/8!/4/1:12)",
				href = "chapter-01.xhtml",
				sectionTitle = "Chapter 1"
			),
			step.controller.state.selectionNoteDraft
		)
		assertNull(step.controller.state.selection)
		assertEquals(ReaderSelectionActionState(), step.controller.state.selectionActions)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun selectionActionsDismissAfterCopyWithoutEngineCommands() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.SelectionChanged(
					text = " The copied sentence ",
					cfi = " epubcfi(/6/8!/4/1:16) ",
					href = " chapter-01.xhtml "
				)
			).controller

		val step = controller.dismissSelectionActions()

		assertNull(step.controller.state.selection)
		assertEquals(ReaderSelectionActionState(), step.controller.state.selectionActions)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun selectionNotesSaveAsAnnotationsAndClearDraft() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "chapter-01.xhtml",
						cfi = "epubcfi(/6/8!/4/1:0)",
						progress = 0.24
					),
					tocTitle = "Chapter 1"
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.SelectionChanged(
					text = " The note sentence ",
					cfi = " epubcfi(/6/8!/4/1:12) ",
					href = " chapter-01.xhtml "
				)
			).controller
			.startSelectionNote().controller

		val step = controller.saveSelectionNote("  Remember this later  ")
		val annotation = ReaderAnnotation(
			id = "book-1|epubcfi(/6/8!/4/1:12)",
			bookId = "book-1",
			bookTitle = "The Hobbit",
			cfi = "epubcfi(/6/8!/4/1:12)",
			text = "The note sentence",
			href = "chapter-01.xhtml",
			color = DefaultReaderHighlightColor,
			note = "Remember this later",
			sectionTitle = "Chapter 1"
		)

		assertEquals(ReaderAnnotationState(listOf(annotation)), step.controller.state.annotations)
		assertNull(step.controller.state.selection)
		assertEquals(ReaderSelectionActionState(), step.controller.state.selectionActions)
		assertNull(step.controller.state.selectionNoteDraft)
		assertEquals(
			listOf(ReaderEngineCommand.ApplyAnnotations(listOf(annotation))),
			step.engineCommands
		)
	}

	@Test
	fun annotationClicksResolveSavedNoteBodyFromControllerStore() {
		val annotated = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "chapter-01.xhtml",
						cfi = "epubcfi(/6/8!/4/1:0)",
						progress = 0.24
					),
					tocTitle = "Chapter 1"
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.SelectionChanged(
					text = " The note sentence ",
					cfi = " epubcfi(/6/8!/4/1:12) ",
					href = " chapter-01.xhtml "
				)
			).controller
			.startSelectionNote().controller
			.saveSelectionNote("  Remember this later  ").controller

		val step = annotated.onEngineEvent(
			ReaderEngineEvent.AnnotationClicked(
				value = "epubcfi(/6/8!/4/1:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/1:12,/1:0,/1:8)"
			)
		)

		assertEquals(
			ReaderAnnotationPopupState(
				value = "epubcfi(/6/8!/4/1:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/1:12,/1:0,/1:8)",
				text = "The note sentence",
				note = "Remember this later",
				color = DefaultReaderHighlightColor
			),
			step.controller.state.annotationPopup
		)
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun selectionHighlightsAreControllerOwnedAndForwardedAsEngineCapabilities() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = ReaderLocator(
						href = "chapter-01.xhtml",
						cfi = "epubcfi(/6/8!/4/1:0)",
						progress = 0.24
					),
					tocTitle = "Chapter 1"
				)
			).controller
			.onEngineEvent(
				ReaderEngineEvent.SelectionChanged(
					text = " The highlighted sentence ",
					cfi = " epubcfi(/6/8!/4/1:12) ",
					href = " chapter-01.xhtml "
				)
			).controller

		val step = controller.addSelectionHighlight(color = "#ffcc66")
		val annotation = ReaderAnnotation(
			id = "book-1|epubcfi(/6/8!/4/1:12)",
			bookId = "book-1",
			bookTitle = "The Hobbit",
			cfi = "epubcfi(/6/8!/4/1:12)",
			text = "The highlighted sentence",
			href = "chapter-01.xhtml",
			color = "#ffcc66",
			sectionTitle = "Chapter 1"
		)
		val duplicate = step.controller.addSelectionHighlight(color = "#ffcc66")

		assertEquals(ReaderAnnotationState(listOf(annotation)), step.controller.state.annotations)
		assertNull(step.controller.state.selection)
		assertEquals(ReaderSelectionActionState(), step.controller.state.selectionActions)
		assertEquals(
			listOf(ReaderEngineCommand.ApplyAnnotations(listOf(annotation))),
			step.engineCommands
		)
		assertEquals(step.controller.state.annotations, duplicate.controller.state.annotations)
		assertEquals(emptyList(), duplicate.engineCommands)
	}

	@Test
	fun currentBookmarksAreControllerOwnedAndDoNotEmitEngineCommands() {
		val locator = ReaderLocator(
			href = "chapter-01.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progress = 0.24
		)
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
					foliateSessionId = "session-a",
					locator = locator,
					tocTitle = "Chapter 1"
				)
			).controller

		val added = controller.toggleCurrentBookmark()
		val bookmark = ReaderBookmark(
			id = "book-1|epubcfi(/6/8!/4/1:0)",
			bookId = "book-1",
			bookTitle = "The Hobbit",
			href = "chapter-01.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progress = 0.24,
			sectionTitle = "Chapter 1"
		)
		val movedAway = added.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				foliateSessionId = "session-a",
				locator = locator.copy(cfi = "epubcfi(/6/10!/4/1:0)"),
				tocTitle = "Chapter 1"
			)
		).controller
		val removed = added.controller.toggleCurrentBookmark()

		assertTrue(controller.state.canBookmarkCurrentLocation)
		assertFalse(controller.state.currentLocationBookmarked)
		assertEquals(ReaderBookmarkState(listOf(bookmark)), added.controller.state.bookmarks)
		assertTrue(added.controller.state.currentLocationBookmarked)
		assertEquals(emptyList(), added.engineCommands)
		assertFalse(movedAway.state.currentLocationBookmarked)
		assertEquals(ReaderBookmarkState(emptyList()), removed.controller.state.bookmarks)
		assertFalse(removed.controller.state.currentLocationBookmarked)
		assertEquals(emptyList(), removed.engineCommands)
	}

	@Test
	fun engineCapabilityEventsFeedControllerStateWithoutOwningChrome() {
		val controller = ReaderController()
		val searchResults = listOf(
			ReaderSearchResult(
				id = "result-1",
				cfi = "epubcfi(/6/8!/4/2:12)",
				excerpt = "unexpected party",
				sectionTitle = "Chapter 1"
			)
		)
		val toc = listOf(
			ReaderTocItem(id = "chapter-1", title = "Chapter 1", href = "chapter-01.xhtml")
		)
		val fragment = ReaderOverlayFragment(
			resourceHref = "audio/chapter-01.mp3",
			fragmentId = "clip-1",
			textHref = "chapter-01.xhtml#p1",
			clipBeginSeconds = 12.0,
			clipEndSeconds = 18.5,
			label = "Chapter 1, paragraph 1"
		)

		val searched = controller.onEngineEvent(
			ReaderEngineEvent.SearchResults(query = "unexpected", results = searchResults)
		).controller
		val withToc = searched.onEngineEvent(ReaderEngineEvent.Toc(toc)).controller
		val selected = withToc.onEngineEvent(
			ReaderEngineEvent.SelectionChanged(
				text = "In a hole in the ground",
				cfi = "epubcfi(/6/8!/4/2:0)",
				href = "chapter-01.xhtml",
				footnote = true,
				contextText = "In a hole in the ground there lived a hobbit.",
				posLeft = 10.5,
				posTop = 20.25,
				posRight = 120.75,
				posBottom = 140.0
			)
		).controller
		val overlay = selected.onEngineEvent(ReaderEngineEvent.MediaOverlayActive(fragment)).controller
		val inactive = overlay.onEngineEvent(ReaderEngineEvent.MediaOverlayInactive(fragmentId = "clip-1"))

		assertEquals(ReaderSearchState("unexpected", searchResults, active = true), searched.state.search)
		assertEquals(toc, withToc.state.toc)
		assertEquals(
			ReaderSelection(
				text = "In a hole in the ground",
				cfi = "epubcfi(/6/8!/4/2:0)",
				href = "chapter-01.xhtml",
				footnote = true,
				contextText = "In a hole in the ground there lived a hobbit.",
				posLeft = 10.5,
				posTop = 20.25,
				posRight = 120.75,
				posBottom = 140.0
			),
			selected.state.selection
		)
		assertEquals(fragment, overlay.state.activeMediaOverlay)
		assertEquals("Chapter 1, paragraph 1", overlay.state.audioMetadataLabel)
		assertNull(inactive.controller.state.activeMediaOverlay)
		assertNull(inactive.controller.state.audioMetadataLabel)
		assertEquals(emptyList(), inactive.engineCommands)
	}

	private val whispersyncTestDestination = ReaderDestinationCommitIdentity("session-a", 1L)

	private fun ReaderController.withWhispersyncTestDestination(): ReaderController =
		copy(
			state = state.copy(
				foliateSessionId = whispersyncTestDestination.foliateSessionId,
				destinationCommitIdentity = whispersyncTestDestination
			)
		)

	private fun whispersyncVisibleTextRange(
		textHref: String,
		visibleStart: Int,
		visibleEnd: Int,
		rangeCfi: String? = null,
		source: String? = null,
		causalSequence: Long? = null,
		destinationCommitIdentity: ReaderDestinationCommitIdentity = whispersyncTestDestination
	): ReaderEngineEvent.VisibleTextRange = ReaderEngineEvent.VisibleTextRange(
		textHref = textHref,
		visibleStart = visibleStart,
		visibleEnd = visibleEnd,
		rangeCfi = rangeCfi,
		source = source,
		causalSequence = causalSequence,
		destinationCommitIdentity = destinationCommitIdentity
	)

	private fun testAnchorReceipt(foliateSessionId: String) = ReaderWhispersyncAnchorReceipt(
		foliateSessionId = foliateSessionId,
		destinationCommitToken = "settled",
		visualPageOrdinal = 4,
		spineIndex = 0,
		rasterGeneration = 12L,
		textureGeneration = 13L,
		presentationMutationGeneration = 3L,
		presentationSequence = 4L,
		anchorGeneration = 5L,
		boundarySequence = 6L,
		layoutGeneration = 14L,
		viewGeneration = 15L,
		commitSequence = 16L,
		committedSpineIndex = 0,
		committedChapterPageIndex = 0,
		committedChapterPageCount = 1,
		paginationFingerprint = "pagination",
		layoutFingerprint = "layout",
		readerSettingsRasterKey = "settings",
		captureGeometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 600.0,
			viewportHeight = 800.0,
			mode = ReaderPageTurnLayoutMode.Single,
			pages = listOf(
				ReaderPageTurnPageRect(
					ReaderPageTurnPageRole.Full,
					30.0,
					0.0,
					540.0,
					800.0
				)
			)
		),
		pageLocalRects = listOf(
			ReaderWhispersyncPageLocalRect(
				ReaderPageTurnPageRole.Full,
				18.0,
				40.0,
				62.0,
				24.0
			)
		)
	)

	private fun hobbitOpenRequest(): ReaderEngineOpenRequest =
		ReaderEngineOpenRequest(
			publication = ReaderPublicationIdentity(
				bookId = "book-1",
				title = "The Hobbit",
				resourceHref = "publication.epub",
				kind = ReaderPublicationKind.Ebook,
				format = ReaderPublicationFormat.Epub
			),
			url = "https://appassets.androidplatform.net/reader-cache/book-1/publication.epub",
			settings = defaultReaderSettings()
		)

	private fun testWhispersyncSidecar(): WhispersyncSidecar =
		WhispersyncSidecar(
			artifactId = "artifact-3",
			ebookBookFileId = "3913",
			audiobookBookFileId = "694",
			revisionDigest = "5f04c2a19e7d",
			timeline = WhispersyncTimeline(
				segments = listOf(
					WhispersyncSegment(
						id = "a",
						audioResource = "Audio/chapter01.m4b",
						startMs = 1_250,
						endMs = 3_500,
						textHref = "Text/chapter1.xhtml",
						fragmentId = "seg-1",
						textStart = 10,
						textEnd = 42,
						label = "Opening sentence",
						sourceOrdinal = 0
					),
					WhispersyncSegment(
						id = "b",
						audioResource = "Audio/chapter01.m4b",
						startMs = 5_000,
						endMs = 8_000,
						textHref = "Text/chapter1.xhtml",
						fragmentId = "seg-2",
						rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)",
						textStart = 80,
						textEnd = 140,
						label = "Second sentence",
						sourceOrdinal = 1
					)
				)
			)
		)

	private fun ReaderController.withRepairableWhispersyncMismatch(): ReaderController =
		copy(
			state = state.copy(
				whispersync = state.whispersync.copy(
					status = ReaderWhispersyncStatus(
						kind = ReaderWhispersyncStatusKind.Mismatch,
						message = ReaderWhispersyncStatusMessage.Mismatch,
						detail = "Audio/chapter99.m4b",
						audioResource = "Audio/chapter99.m4b",
						positionMs = 5_500L
					)
				)
			)
		)
}
