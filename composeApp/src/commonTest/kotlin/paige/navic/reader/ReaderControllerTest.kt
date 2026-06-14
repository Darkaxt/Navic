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
		val expectedLaterCoverProgress = BinderyReadingProgress(
			bookId = "book-1",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "publication.epub",
			textHref = "EPUB/Text/cover.xhtml",
			progressFraction = 0.0
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
		assertEquals(expectedLaterCoverProgress, laterCover.progressToSave)
	}

	@Test
	fun contentActionClaimsSuppressOnlyTheNextReaderMenuAction() {
		val claimed = ReaderController().onEngineEvent(
			ReaderEngineEvent.ContentActionClaimed(ReaderContentAction.Image)
		).controller

		val suppressed = claimed.onViewerAction(ReaderViewerAction.Menu)
		val toggled = suppressed.controller.onViewerAction(ReaderViewerAction.Menu)
		val next = toggled.controller.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))

		assertEquals(ReaderContentAction.Image, claimed.state.lastContentActionClaim?.action)
		assertFalse(suppressed.controller.state.menuVisible)
		assertNull(suppressed.controller.state.lastContentActionClaim)
		assertEquals(emptyList(), suppressed.engineCommands)
		assertTrue(toggled.controller.state.menuVisible)
		assertEquals(
			listOf(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Next)),
			next.engineCommands
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
		val suppressed = claimed.onViewerAction(ReaderViewerAction.Menu)

		assertEquals(claim, claimed.state.lastContentActionClaim)
		assertNull(suppressed.controller.state.lastContentActionClaim)
		assertFalse(suppressed.controller.state.menuVisible)
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
		val readingMode = contents.controller.openReadingModeDialog()
		val settings = readingMode.controller.openSettingsDialog()
		val dismissed = settings.controller.closeDialog()

		assertEquals(ReaderControllerDialog.Contents, contents.controller.state.dialog)
		assertTrue(contents.controller.state.menuVisible)
		assertEquals(emptyList(), contents.engineCommands)
		assertEquals(ReaderControllerDialog.ReadingMode, readingMode.controller.state.dialog)
		assertTrue(readingMode.controller.state.menuVisible)
		assertEquals(emptyList(), readingMode.engineCommands)
		assertEquals(ReaderControllerDialog.Settings, settings.controller.state.dialog)
		assertTrue(settings.controller.state.menuVisible)
		assertEquals(emptyList(), settings.engineCommands)
		assertNull(dismissed.controller.state.dialog)
		assertTrue(dismissed.controller.state.menuVisible)
		assertEquals(emptyList(), dismissed.engineCommands)
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
	fun navigateToIsControllerOwnedAndForwardedAsEngineCapability() {
		val locator = ReaderLocator(progress = 0.42)

		val step = ReaderController().navigateTo(locator)

		assertEquals(
			listOf(ReaderEngineCommand.NavigateTo(locator)),
			step.engineCommands
		)
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
				href = "chapter-01.xhtml"
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
				href = "chapter-01.xhtml"
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
