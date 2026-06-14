package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind

class ReaderCoordinatorTest {
	@Test
	fun openPublicationRoutesControllerCommandThroughEpubAdapter() {
		val request = ReaderEngineOpenRequest(
			publication = ReaderPublicationIdentity(
				bookId = "book-1",
				title = "The Hobbit",
				resourceHref = "publication.epub",
				kind = ReaderPublicationKind.Ebook,
				format = ReaderPublicationFormat.Epub
			),
			url = "https://appassets.androidplatform.net/reader-cache/book-1/publication.epub",
			mediaOverlayEnabled = true,
			externalShellCover = true,
			startLocator = ReaderLocator(
				href = "chapter-01.xhtml",
				cfi = "epubcfi(/6/2!/4/1:0)",
				progress = 0.1
			),
			settings = defaultReaderSettings(),
			nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
			canReturnToShellCover = true
		)

		val step = ReaderCoordinator().open(request)
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.coordinator.viewState)

		assertEquals(request.publication, step.coordinator.controller.state.publication)
		assertEquals(ReaderPublicationFormat.Epub, step.coordinator.controller.state.activeEngine)
		assertEquals(request.url, viewState.publicationUrl)
		assertEquals(request.nativeShellCoverUrl, viewState.nativeShellCoverUrl)
		assertEquals(request.startLocator, viewState.startLocator)
	}

	@Test
	fun viewerActionsDispatchThroughCurrentEngineAdapter() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator

		val step = opened.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.coordinator.viewState)

		assertEquals(ReaderBridgeCommand.NextPage, viewState.bridgeCommand())
		assertEquals(1L, viewState.commandKey)
	}

	@Test
	fun searchRoutesThroughControllerAndCurrentEngineAdapter() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator

		val step = opened.search("  hobbit-hole  ")
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.coordinator.viewState)

		assertEquals(ReaderSearchState(query = "hobbit-hole", active = true), step.coordinator.controller.state.search)
		assertEquals(ReaderBridgeCommand.Search("hobbit-hole"), viewState.bridgeCommand())
		assertEquals(1L, viewState.commandKey)
	}

	@Test
	fun progressNavigationRoutesThroughControllerAndCurrentEngineAdapter() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator

		val step = opened.navigateTo(ReaderLocator(progress = 0.42))
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.coordinator.viewState)

		assertEquals(ReaderBridgeCommand.GoToProgress(0.42), viewState.bridgeCommand())
		assertEquals(1L, viewState.commandKey)
	}

	@Test
	fun mediaOverlayCommandsRouteThroughControllerAndCurrentEngineAdapter() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator
		val fragment = ReaderOverlayFragment(
			resourceHref = "audio/chapter-01.mp3",
			fragmentId = "clip-1",
			label = "Chapter 1"
		)

		val applied = opened.applyMediaOverlay(fragment)
		val appliedViewState = assertIs<ReaderEngineViewState.WebViewPublication>(applied.coordinator.viewState)
		val cleared = applied.coordinator.clearMediaOverlay(fragmentId = "clip-1")
		val clearedViewState = assertIs<ReaderEngineViewState.WebViewPublication>(cleared.coordinator.viewState)

		assertEquals(fragment, applied.coordinator.controller.state.activeMediaOverlay)
		assertEquals("Chapter 1", applied.coordinator.controller.state.audioMetadataLabel)
		assertEquals(ReaderBridgeCommand.ApplyOverlayFragment(fragment), appliedViewState.bridgeCommand())
		assertEquals(1L, appliedViewState.commandKey)
		assertNull(cleared.coordinator.controller.state.activeMediaOverlay)
		assertNull(cleared.coordinator.controller.state.audioMetadataLabel)
		assertEquals(ReaderBridgeCommand.ClearOverlay, clearedViewState.bridgeCommand())
		assertEquals(2L, clearedViewState.commandKey)
	}

	@Test
	fun selectionHighlightsRouteThroughControllerAndCurrentEngineAdapter() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator
		val located = opened.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(
					href = "chapter-01.xhtml",
					cfi = "epubcfi(/6/8!/4/1:0)",
					progress = 0.24
				),
				tocTitle = "Chapter 1"
			)
		).coordinator
		val selected = located.onEngineEvent(
			ReaderEngineEvent.SelectionChanged(
				text = "The highlighted sentence",
				cfi = "epubcfi(/6/8!/4/1:12)",
				href = "chapter-01.xhtml"
			)
		).coordinator

		val step = selected.addSelectionHighlight(color = "#ffcc66")
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.coordinator.viewState)
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

		assertEquals(ReaderAnnotationState(listOf(annotation)), step.coordinator.controller.state.annotations)
		assertEquals(ReaderBridgeCommand.ApplyHighlights(listOf(annotation)), viewState.bridgeCommand())
		assertEquals(1L, viewState.commandKey)
	}

	@Test
	fun currentBookmarkTogglesRouteThroughControllerWithoutEngineBridgeCommands() {
		val locator = ReaderLocator(
			href = "chapter-01.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progress = 0.24
		)
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator
		val located = opened.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = locator,
				tocTitle = "Chapter 1"
			)
		).coordinator

		val step = located.toggleCurrentBookmark()
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.coordinator.viewState)
		val bookmark = ReaderBookmark(
			id = "book-1|epubcfi(/6/8!/4/1:0)",
			bookId = "book-1",
			bookTitle = "The Hobbit",
			href = "chapter-01.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progress = 0.24,
			sectionTitle = "Chapter 1"
		)

		assertEquals(ReaderBookmarkState(listOf(bookmark)), step.coordinator.controller.state.bookmarks)
		assertTrue(step.coordinator.controller.state.currentLocationBookmarked)
		assertNull(viewState.command)
		assertEquals(0L, viewState.commandKey)
	}

	@Test
	fun relocationsRouteProgressSaveIntentThroughControllerWithoutEngineBridgeCommands() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator
		val ready = opened.onEngineEvent(ReaderEngineEvent.PublicationReady).coordinator
		val startupCover = ready.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(href = "EPUB/Text/cover.xhtml", progress = 0.0),
				tocTitle = "Cover"
			)
		).coordinator
		val resumedLocator = ReaderLocator(
			href = "EPUB/Text/chapter-04.xhtml#p9",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progress = 0.62,
			pageIndex = 12,
			pageCount = 411
		)

		val resumed = startupCover.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = resumedLocator,
				tocTitle = "Chapter 4"
			)
		)
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(resumed.coordinator.viewState)
		val expectedProgress = BinderyReadingProgress(
			bookId = "book-1",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "publication.epub",
			textHref = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			fragmentId = "p9",
			progressFraction = 0.62
		)

		assertEquals(expectedProgress, resumed.progressToSave)
		assertEquals(
			expectedProgress,
			resumed.coordinator.controller.state.readingProgress.progressFor(
				bookId = "book-1",
				resourceHref = "publication.epub",
				kind = ReaderPublicationKind.Ebook
			)
		)
		assertNull(viewState.command)
		assertEquals(0L, viewState.commandKey)
	}

	@Test
	fun activeEngineIsSelectedThroughAdapterContractInsteadOfFoliateSpecialCase() {
		val fakePdfAdapter = RecordingReaderEngineAdapter(format = ReaderPublicationFormat.Pdf)
		val pdfRequest = hobbitOpenRequest().let { request ->
			request.copy(
				publication = request.publication.copy(format = ReaderPublicationFormat.Pdf),
				url = "https://appassets.androidplatform.net/reader-cache/book-1/publication.pdf"
			)
		}

		val opened = ReaderCoordinator(
			engineAdapters = mapOf(ReaderPublicationFormat.Pdf to fakePdfAdapter)
		).open(pdfRequest).coordinator
		val activeAdapter = assertIs<RecordingReaderEngineAdapter>(
			opened.engineAdapters[ReaderPublicationFormat.Pdf]
		)
		val bridged = opened.onFoliateHostEvent(ReaderBridgeEvent.PublicationReady).coordinator

		assertEquals(
			listOf(ReaderEngineCommand.OpenPublication(pdfRequest.copy(settings = pdfRequest.settings.normalizedReaderSettings()))),
			activeAdapter.commands
		)
		assertEquals("pdf adapter event", bridged.controller.state.errorMessage)
		assertEquals("pdf_fake", bridged.controller.state.errorCode)
	}

	@Test
	fun pdfPublicationRoutesThroughDefaultPdfEngineAdapter() {
		val pdfRequest = hobbitOpenRequest().let { request ->
			request.copy(
				publication = request.publication.copy(
					resourceHref = "publication.pdf",
					format = ReaderPublicationFormat.Pdf
				),
				url = "https://appassets.androidplatform.net/reader-cache/book-1/publication.pdf"
			)
		}

		val opened = ReaderCoordinator().open(pdfRequest).coordinator
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(opened.viewState)
		val next = opened.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)).coordinator
		val nextViewState = assertIs<ReaderEngineViewState.WebViewPublication>(next.viewState)

		assertEquals(ReaderPublicationFormat.Pdf, opened.controller.state.activeEngine)
		assertIs<FoliatePdfEngineAdapter>(opened.engineAdapters[ReaderPublicationFormat.Pdf])
		assertEquals(ReaderPublicationFormat.Pdf, opened.engineAdapters[ReaderPublicationFormat.Pdf]?.format)
		assertEquals(pdfRequest.url, viewState.publicationUrl)
		assertEquals(ReaderBridgeCommand.NextPage, nextViewState.bridgeCommand())
		assertEquals(1L, nextViewState.commandKey)
	}

	@Test
	fun nextFromControllerOwnedShellCoverOnlyDismissesCover() {
		val opened = ReaderCoordinator().open(
			hobbitOpenRequest().copy(
				externalShellCover = true,
				nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
				canReturnToShellCover = true
			)
		).coordinator

		val step = opened.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.coordinator.viewState)

		assertEquals(false, step.coordinator.controller.state.shellCoverVisible)
		assertNull(viewState.command)
		assertEquals(0L, viewState.commandKey)
	}

	@Test
	fun bridgeEventsFlowFromAdapterIntoControllerWithoutBridgeOwningMenu() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator
		val locator = ReaderLocator(
			href = "chapter-02.xhtml",
			cfi = "epubcfi(/6/8!/4/1:0)",
			progress = 0.25,
			pageIndex = 12,
			pageCount = 411
		)

		val relocated = opened.onFoliateHostEvent(
			ReaderBridgeEvent.LocationChanged(locator = locator, tocTitle = "Chapter 2")
		).coordinator
		val ignoredCenterTap = relocated.onFoliateHostEvent(ReaderBridgeEvent.CenterTap).coordinator
		val contentClaimed = ignoredCenterTap.onFoliateHostEvent(
			ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.Link)
		).coordinator
		val suppressedMenu = contentClaimed.onViewerAction(ReaderViewerAction.Menu).coordinator

		assertEquals(locator, relocated.controller.state.chrome.currentLocator)
		assertEquals("Chapter 2", relocated.controller.state.chrome.currentSectionTitle)
		assertEquals(false, ignoredCenterTap.controller.state.menuVisible)
		assertEquals(ReaderContentAction.Link, contentClaimed.controller.state.lastContentActionClaim?.action)
		assertEquals(false, suppressedMenu.controller.state.menuVisible)
		assertNull(suppressedMenu.controller.state.lastContentActionClaim)
	}

	@Test
	fun bridgeCapabilityEventsUpdateControllerInsteadOfWebViewOwningState() {
		val opened = ReaderCoordinator().open(hobbitOpenRequest()).coordinator
		val searchResults = listOf(
			ReaderSearchResult(
				id = "result-1",
				href = "chapter-01.xhtml",
				excerpt = "hobbit-hole"
			)
		)
		val fragment = ReaderOverlayFragment(
			resourceHref = "audio/chapter-01.mp3",
			fragmentId = "clip-1",
			label = "Chapter 1"
		)

		val searched = opened.onFoliateHostEvent(
			ReaderBridgeEvent.SearchResults(query = "hobbit", results = searchResults)
		).coordinator
		val overlay = searched.onFoliateHostEvent(ReaderBridgeEvent.OverlayFragmentActive(fragment)).coordinator

		assertEquals(ReaderSearchState("hobbit", searchResults, active = true), searched.controller.state.search)
		assertEquals(fragment, overlay.controller.state.activeMediaOverlay)
		assertEquals("Chapter 1", overlay.controller.state.audioMetadataLabel)
	}

	@Test
	fun bottomBarDialogsRouteThroughControllerWithoutEngineCommands() {
		val coordinator = ReaderCoordinator().open(hobbitOpenRequest()).coordinator

		val contents = coordinator.openContentsDialog().coordinator
		val readingMode = contents.openReadingModeDialog().coordinator
		val settings = readingMode.openSettingsDialog().coordinator

		assertEquals(ReaderControllerDialog.Contents, contents.controller.state.dialog)
		assertEquals(ReaderControllerDialog.ReadingMode, readingMode.controller.state.dialog)
		assertEquals(ReaderControllerDialog.Settings, settings.controller.state.dialog)
		assertEquals(coordinator.viewState, contents.viewState)
		assertEquals(contents.viewState, readingMode.viewState)
		assertEquals(readingMode.viewState, settings.viewState)
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

	private data class RecordingReaderEngineAdapter(
		override val format: ReaderPublicationFormat,
		val commands: List<ReaderEngineCommand> = emptyList()
	) : ReaderEngine {
		override fun onCommand(command: ReaderEngineCommand): ReaderEngineStep =
			ReaderEngineStep(
				engine = copy(commands = commands + command),
				viewState = ReaderEngineViewState.Empty
			)

		override fun onHostEvent(event: ReaderEngineHostEvent): ReaderEngineEvent? =
			if (event is ReaderEngineHostEvent.FoliateBridge && event.event == ReaderBridgeEvent.PublicationReady) {
				ReaderEngineEvent.Error(
					message = "pdf adapter event",
					code = "pdf_fake"
				)
			} else {
				null
			}
	}

	private fun ReaderEngineViewState.WebViewPublication.bridgeCommand(): ReaderBridgeCommand? =
		(command as? ReaderEngineHostCommand.FoliateBridge)?.command

	private fun ReaderCoordinator.onFoliateHostEvent(event: ReaderBridgeEvent): ReaderCoordinatorStep =
		onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(event))
}
