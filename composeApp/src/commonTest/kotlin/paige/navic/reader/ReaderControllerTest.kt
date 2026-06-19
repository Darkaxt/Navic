package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
		val navigation = overlayCreated.onEngineEvent(
			ReaderEngineEvent.NavigationStateChanged(canGoBack = true, canGoForward = false)
		).controller
		val footnoteClosed = navigation.onEngineEvent(ReaderEngineEvent.FootnoteClose).controller
		val pullUp = footnoteClosed.onEngineEvent(ReaderEngineEvent.PullUp)

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
			ReaderEngineNavigationState(canGoBack = true, canGoForward = false, visible = true),
			navigation.state.engineNavigation
		)
		assertEquals(
			ReaderOverlayInteraction.FootnoteClosed,
			footnoteClosed.state.lastOverlayInteraction
		)
		assertEquals(
			ReaderOverlayInteraction.PullUp,
			pullUp.controller.state.lastOverlayInteraction
		)
		assertTrue(pullUp.controller.state.menuVisible)
		assertEquals(emptyList(), pullUp.engineCommands)
	}

	@Test
	fun pushStateShowsNativeHistoryCapsuleAndRoutesHistoryCommandsThroughEngine() {
		val controller = ReaderController().onEngineEvent(
			ReaderEngineEvent.NavigationStateChanged(canGoBack = true, canGoForward = true)
		).controller

		assertEquals(
			ReaderEngineNavigationState(canGoBack = true, canGoForward = true, visible = true),
			controller.state.engineNavigation
		)

		val back = controller.navigateHistoryBack()
		assertEquals(
			listOf(ReaderEngineCommand.NavigateHistory(ReaderHistoryDirection.Back)),
			back.engineCommands
		)

		val forward = controller.navigateHistoryForward()
		assertEquals(
			listOf(ReaderEngineCommand.NavigateHistory(ReaderHistoryDirection.Forward)),
			forward.engineCommands
		)

		val dismissed = controller.dismissHistoryNavigation()
		assertEquals(
			ReaderEngineNavigationState(canGoBack = true, canGoForward = true, visible = false),
			dismissed.controller.state.engineNavigation
		)
		assertEquals(emptyList(), dismissed.engineCommands)
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
	fun engineRelocationsBuildControllerOwnedProgressSnapshotsAfterPublicationReady() {
		val opened = ReaderController().open(hobbitOpenRequest()).controller
		val ready = opened.onEngineEvent(ReaderEngineEvent.PublicationReady).controller
		val startupCover = ready.onEngineEvent(
			ReaderEngineEvent.Relocated(
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
				locator = resumedLocator,
				tocTitle = "Chapter 4"
			)
		)
		val laterCover = resumed.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.0),
				tocTitle = "Cover"
			)
		)
		val expectedProgress = BinderyReadingProgress(
			bookId = "book-1",
			kind = BinderyReadingProgressKind.Ebook,
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
	fun contentActionClaimsSuppressOnlyTheNextNativeViewerAction() {
		val claimed = ReaderController().onEngineEvent(
			ReaderEngineEvent.ContentActionClaimed(ReaderContentAction.Image)
		).controller

		val toggled = claimed.onViewerAction(ReaderViewerAction.Menu)
		val next = toggled.controller.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))

		assertEquals(ReaderContentAction.Image, claimed.state.lastContentActionClaim?.action)
		assertFalse(toggled.controller.state.menuVisible)
		assertNull(toggled.controller.state.lastContentActionClaim)
		assertEquals(emptyList(), toggled.engineCommands)
		assertEquals(
			listOf(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Next)),
			next.engineCommands
		)
	}

	@Test
	fun previousFromFirstReadablePageReturnsToNativeCoverInsteadOfSuppressedWebViewCover() {
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
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
					locator = ReaderLocator(
						href = "OEBPS/Text/sinopsis.xhtml",
						progress = 0.004370907849029098,
						pageIndex = 1,
						pageCount = 270
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
					locator = ReaderLocator(
						href = "OEBPS/Text/sinopsis.xhtml",
						progress = 0.0007927082115140601,
						pageIndex = 10,
						pageCount = 1534,
						chapterProgress = 0.0,
						chapterPageIndex = 0,
						chapterPageCount = 2
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
		val opened = ReaderController().open(
			hobbitOpenRequest().copy(
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
					locator = ReaderLocator(
						href = "OEBPS/Text/sinopsis.xhtml",
						progress = 0.0007927082115140601,
						pageIndex = 10,
						pageCount = 1534,
						chapterProgress = 0.0,
						chapterPageIndex = 0,
						chapterPageCount = 2
					),
					tocTitle = "Synopsis"
				)
			).controller
		val laterFrontmatter = firstFrontmatter
			.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
			.controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
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
			listOf(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Previous)),
			previous.engineCommands,
			"Only the first eligible EPUB-side locator after dismissing the native cover may reclaim the native cover."
		)
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
		assertFalse(toggled.controller.state.menuVisible)
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
			listOf(ReaderEngineCommand.ScrollViewport(ReaderViewportScrollDirection.Down)),
			down.engineCommands
		)
		assertEquals(
			listOf(ReaderEngineCommand.ScrollViewport(ReaderViewportScrollDirection.Up)),
			up.engineCommands
		)
	}

	@Test
	fun viewerReadableDragPreviewIsControllerOwnedAndForwardedAsEngineCapability() {
		val step = ReaderController().onViewerAction(
			ReaderViewerAction.PreviewPageDrag(
				deltaX = -184.0,
				viewWidth = 1440.0,
				phase = ReaderPageDragPreviewPhase.Update
			)
		)

		assertFalse(step.controller.state.menuVisible)
		assertEquals(
			listOf(
				ReaderEngineCommand.PreviewPageDrag(
					deltaX = -184.0,
					viewWidth = 1440.0,
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
					viewHeight = 1000.0
				)
			),
			step.engineCommands
		)
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
				active = true
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
		assertEquals(ReaderSearchState(query = "unexpected party", active = true), controller.state.search)
		assertNull(cleared.controller.state.dialog)
		assertEquals(ReaderSearchState(), cleared.controller.state.search)
		assertEquals(listOf(ReaderEngineCommand.ClearSearch), cleared.engineCommands)
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
					)
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
			listOf(ReaderEngineCommand.NavigateTo(locator)),
			step.engineCommands
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
					ReaderLocator(href = "EPUB/Text/chapter-01.xhtml")
				)
			),
			previous.engineCommands
		)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(href = "EPUB/Text/chapter-03.xhtml#start")
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
					ReaderLocator(href = "EPUB/Text/foreword.xhtml")
				)
			),
			previous.engineCommands
		)
		assertEquals(
			listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(href = "EPUB/Text/chapter-02.xhtml")
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
					)
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
					locator = ReaderLocator(href = "EPUB/Text/chapter-01.xhtml"),
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
		val cleared = actionable.onEngineEvent(ReaderEngineEvent.SelectionCleared).controller

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
	}

	@Test
	fun selectionNotesStartNativeDraftWithoutEngineCommands() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
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
		assertEquals(emptyList(), step.engineCommands)
	}

	@Test
	fun selectionNotesSaveAsAnnotationsAndClearDraft() {
		val controller = ReaderController().open(hobbitOpenRequest()).controller
			.onEngineEvent(
				ReaderEngineEvent.Relocated(
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
}
