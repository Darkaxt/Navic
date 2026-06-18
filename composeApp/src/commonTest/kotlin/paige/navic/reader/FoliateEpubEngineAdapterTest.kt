package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FoliateEpubEngineAdapterTest {
	@Test
	fun opensWebViewPublicationFromControllerOpenRequest() {
		val request = hobbitOpenRequest()

		val step = FoliateEpubEngineAdapter().onCommand(ReaderEngineCommand.OpenPublication(request))
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(step.viewState)

		assertEquals(ReaderPublicationFormat.Epub, step.engine.format)
		assertEquals("https://appassets.androidplatform.net/reader-cache/book-1/publication.epub", viewState.publicationUrl)
		assertEquals("The Hobbit", viewState.title)
		assertEquals(ReaderPublicationKind.Ebook, viewState.kind)
		assertEquals(false, viewState.mediaOverlayEnabled)
		assertEquals(true, viewState.externalShellCover)
		assertEquals("https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg", viewState.nativeShellCoverUrl)
		assertEquals(true, viewState.canReturnToShellCover)
		assertEquals(request.startLocator, viewState.startLocator)
		assertEquals(request.settings.normalizedReaderSettings(), viewState.settings)
		assertNull(viewState.command)
		assertEquals(0L, viewState.commandKey)
	}

	@Test
	fun dispatchesBridgeCommandsThroughCurrentWebViewStateWithMonotonicKeys() {
		val opened = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(hobbitOpenRequest()))
			.engine

		val next = opened.onCommand(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Next))
		val settings = next.engine.onCommand(
			ReaderEngineCommand.ApplySettings(defaultReaderSettings().copy(fontSizePercent = 132))
		)
		val seek = settings.engine.onCommand(ReaderEngineCommand.NavigateTo(ReaderLocator(progress = 0.5)))
		val nextViewState = assertIs<ReaderEngineViewState.WebViewPublication>(next.viewState)
		val settingsViewState = assertIs<ReaderEngineViewState.WebViewPublication>(settings.viewState)
		val seekViewState = assertIs<ReaderEngineViewState.WebViewPublication>(seek.viewState)

		assertEquals(ReaderBridgeCommand.NextPage, nextViewState.bridgeCommand())
		assertEquals(1L, nextViewState.commandKey)
		assertEquals(
			ReaderBridgeCommand.ApplySettings(defaultReaderSettings().copy(fontSizePercent = 132).normalizedReaderSettings()),
			settingsViewState.bridgeCommand()
		)
		assertEquals(2L, settingsViewState.commandKey)
		assertEquals(ReaderBridgeCommand.GoToProgress(0.5), seekViewState.bridgeCommand())
		assertEquals(3L, seekViewState.commandKey)
	}

	@Test
	fun dispatchesTypedEngineCapabilitiesAsFoliateBridgeCommands() {
		val opened = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(hobbitOpenRequest()))
			.engine

		val search = opened.onCommand(ReaderEngineCommand.Search("dragon"))
		val clearSearch = search.engine.onCommand(ReaderEngineCommand.ClearSearch)
		val navigateToCfi = clearSearch.engine.onCommand(
			ReaderEngineCommand.NavigateTo(
				ReaderLocator(
					href = "chapter-01.xhtml",
					cfi = "epubcfi(/6/8!/4/2:12)",
					progress = 0.45
				)
			)
		)
		val navigateToHref = navigateToCfi.engine.onCommand(
			ReaderEngineCommand.NavigateTo(ReaderLocator(href = "chapter-02.xhtml"))
		)
		val navigateToProgress = navigateToHref.engine.onCommand(
			ReaderEngineCommand.NavigateTo(ReaderLocator(progress = 0.75))
		)
		val fragment = ReaderOverlayFragment(
			resourceHref = "audio/chapter-01.mp3",
			fragmentId = "clip-1",
			label = "Chapter 1"
		)
		val overlay = navigateToProgress.engine.onCommand(ReaderEngineCommand.ApplyMediaOverlay(fragment))
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
		val highlights = overlay.engine.onCommand(ReaderEngineCommand.ApplyAnnotations(listOf(annotation)))
		val clearOverlay = highlights.engine.onCommand(ReaderEngineCommand.ClearMediaOverlay)

		assertEquals(ReaderBridgeCommand.Search("dragon"), assertIs<ReaderEngineViewState.WebViewPublication>(search.viewState).bridgeCommand())
		assertEquals(ReaderBridgeCommand.ClearSearch, assertIs<ReaderEngineViewState.WebViewPublication>(clearSearch.viewState).bridgeCommand())
		assertEquals(
			ReaderBridgeCommand.GoToCfi("epubcfi(/6/8!/4/2:12)"),
			assertIs<ReaderEngineViewState.WebViewPublication>(navigateToCfi.viewState).bridgeCommand()
		)
		assertEquals(
			ReaderBridgeCommand.GoToHref("chapter-02.xhtml"),
			assertIs<ReaderEngineViewState.WebViewPublication>(navigateToHref.viewState).bridgeCommand()
		)
		assertEquals(
			ReaderBridgeCommand.GoToProgress(0.75),
			assertIs<ReaderEngineViewState.WebViewPublication>(navigateToProgress.viewState).bridgeCommand()
		)
		assertEquals(5L, assertIs<ReaderEngineViewState.WebViewPublication>(navigateToProgress.viewState).commandKey)
		assertEquals(
			ReaderBridgeCommand.ApplyOverlayFragment(fragment),
			assertIs<ReaderEngineViewState.WebViewPublication>(overlay.viewState).bridgeCommand()
		)
		assertEquals(6L, assertIs<ReaderEngineViewState.WebViewPublication>(overlay.viewState).commandKey)
		assertEquals(
			ReaderBridgeCommand.ApplyHighlights(listOf(annotation)),
			assertIs<ReaderEngineViewState.WebViewPublication>(highlights.viewState).bridgeCommand()
		)
		assertEquals(7L, assertIs<ReaderEngineViewState.WebViewPublication>(highlights.viewState).commandKey)
		assertEquals(
			ReaderBridgeCommand.ClearOverlay,
			assertIs<ReaderEngineViewState.WebViewPublication>(clearOverlay.viewState).bridgeCommand()
		)
		assertEquals(8L, assertIs<ReaderEngineViewState.WebViewPublication>(clearOverlay.viewState).commandKey)
	}

	@Test
	fun dispatchesChapterLocalRailSeekAsFoliateChapterProgressCommand() {
		val opened = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(hobbitOpenRequest()))
			.engine

		val seek = opened.onCommand(
			ReaderEngineCommand.NavigateTo(
				ReaderLocator(
					href = "chapter-01.xhtml",
					chapterProgress = 0.375,
					chapterPageIndex = 3,
					chapterPageCount = 9
				)
			)
		)

		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(seek.viewState)
		assertEquals(
			ReaderBridgeCommand.GoToChapterProgress(
				href = "chapter-01.xhtml",
				progress = 0.375
			),
			viewState.bridgeCommand()
		)
	}

	@Test
	fun dispatchesTypedViewportScrollAsRendererScrollCommand() {
		val opened = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(hobbitOpenRequest()))
			.engine

		val preview = opened.onCommand(
			ReaderEngineCommand.PreviewPageDrag(
				deltaX = -184.0,
				viewWidth = 1440.0,
				phase = ReaderPageDragPreviewPhase.Update
			)
		)
		val down = preview.engine.onCommand(ReaderEngineCommand.ScrollViewport(ReaderViewportScrollDirection.Down))
		val up = down.engine.onCommand(ReaderEngineCommand.ScrollViewport(ReaderViewportScrollDirection.Up))

		assertEquals(
			ReaderBridgeCommand.PreviewPageDrag(
				deltaX = -184.0,
				viewWidth = 1440.0,
				phase = ReaderPageDragPreviewPhase.Update
			),
			assertIs<ReaderEngineViewState.WebViewPublication>(preview.viewState).bridgeCommand()
		)
		assertEquals(1L, assertIs<ReaderEngineViewState.WebViewPublication>(preview.viewState).commandKey)
		assertEquals(
			ReaderBridgeCommand.ScrollViewport(ReaderViewportScrollDirection.Down),
			assertIs<ReaderEngineViewState.WebViewPublication>(down.viewState).bridgeCommand()
		)
		assertEquals(2L, assertIs<ReaderEngineViewState.WebViewPublication>(down.viewState).commandKey)
		assertEquals(
			ReaderBridgeCommand.ScrollViewport(ReaderViewportScrollDirection.Up),
			assertIs<ReaderEngineViewState.WebViewPublication>(up.viewState).bridgeCommand()
		)
		assertEquals(3L, assertIs<ReaderEngineViewState.WebViewPublication>(up.viewState).commandKey)
	}

	@Test
	fun dispatchesTypedContentLongPressAsRendererContentCommand() {
		val opened = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(hobbitOpenRequest()))
			.engine

		val step = opened.onCommand(
			ReaderEngineCommand.ContentLongPressAt(
				x = 250.0,
				y = 500.0,
				viewWidth = 500.0,
				viewHeight = 1000.0
			)
		)

		assertEquals(
			ReaderBridgeCommand.ContentLongPressAt(
				x = 250.0,
				y = 500.0,
				viewWidth = 500.0,
				viewHeight = 1000.0
			),
			assertIs<ReaderEngineViewState.WebViewPublication>(step.viewState).bridgeCommand()
		)
		assertEquals(1L, assertIs<ReaderEngineViewState.WebViewPublication>(step.viewState).commandKey)
	}

	@Test
	fun mapsBridgeEventsToEngineEventsWithoutLettingBridgeOwnChrome() {
		val locator = ReaderLocator(
			href = "chapter-01.xhtml",
			cfi = "epubcfi(/6/4!/4/2:0)",
			progress = 0.25,
			pageIndex = 5,
			pageCount = 411,
			rangeCfi = "epubcfi(/6/4!/4/2:0,/1:0,/1:14)",
			reason = "page",
			fraction = 0.5,
			size = 0.1,
			tocItemLabel = "Chapter 1",
			pageItemLabel = "Page 8"
		)
		val adapter = FoliateEpubEngineAdapter()

		assertEquals(
			ReaderEngineEvent.PublicationReady,
			adapter.onBridgeHostEvent(ReaderBridgeEvent.PublicationReady)
		)
		assertEquals(
			ReaderEngineEvent.Relocated(locator = locator, tocTitle = "Chapter 1"),
			adapter.onBridgeHostEvent(ReaderBridgeEvent.LocationChanged(locator, tocTitle = "Chapter 1"))
		)
		assertEquals(
			ReaderEngineEvent.TocItemChanged(href = "chapter-01.xhtml", title = "Chapter 1"),
			adapter.onBridgeHostEvent(ReaderBridgeEvent.TocItemChanged(href = "chapter-01.xhtml", title = "Chapter 1"))
		)
		assertEquals(
			ReaderEngineEvent.ContentActionClaimed(ReaderContentAction.Link),
			adapter.onBridgeHostEvent(ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.Link))
		)
		assertEquals(
			ReaderEngineEvent.ContentActionClaimed(ReaderContentAction.Image),
			adapter.onBridgeHostEvent(ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.Image))
		)
		assertEquals(
			ReaderEngineEvent.InternalLinkRequested(
				href = "chapter-02.xhtml#door",
				prevented = true,
				source = "native-short-tap"
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.InternalLinkRequested(
					href = "chapter-02.xhtml#door",
					prevented = true,
					source = "native-short-tap"
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.InternalLinkRequested(
				href = "chapter-03.xhtml#hall",
				prevented = false,
				source = "content-long-press-command"
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.InternalLinkRequested(
					href = "chapter-03.xhtml#hall",
					prevented = false,
					source = "content-long-press-command"
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.ExternalLinkOpened(
				href = "https://example.test/notes",
				anchorHref = "../Text/chapter-01.xhtml#note"
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.ExternalLink(
					href = "https://example.test/notes",
					anchorHref = "../Text/chapter-01.xhtml#note"
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.SelectionCleared,
			adapter.onBridgeHostEvent(ReaderBridgeEvent.SelectionCleared)
		)
		assertEquals(
			ReaderEngineEvent.AnnotationClicked(
				value = "epubcfi(/6/8!/4/2:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.AnnotationClick(
					value = "epubcfi(/6/8!/4/2:12)",
					index = 3,
					rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.AnnotationDrawn(
				value = "epubcfi(/6/8!/4/2:12)",
				index = 3,
				rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.AnnotationDrawn(
					value = "epubcfi(/6/8!/4/2:12)",
					index = 3,
					rangeCfi = "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.OverlayCreated(index = 3),
			adapter.onBridgeHostEvent(ReaderBridgeEvent.OverlayCreated(index = 3))
		)
		assertEquals(
			ReaderEngineEvent.DocLoaded(
				index = 3,
				href = "EPUB/Text/chapter-01.xhtml",
				title = "Chapter 1",
				sectionId = "chapter-01"
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.LoadDoc(
					index = 3,
					href = "EPUB/Text/chapter-01.xhtml",
					title = "Chapter 1",
					sectionId = "chapter-01"
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.NavigationStateChanged(canGoBack = true, canGoForward = false),
			adapter.onBridgeHostEvent(ReaderBridgeEvent.PushState(canGoBack = true, canGoForward = false))
		)
		assertEquals(
			ReaderEngineEvent.FootnoteClose,
			adapter.onBridgeHostEvent(ReaderBridgeEvent.FootnoteClose)
		)
		assertEquals(
			ReaderEngineEvent.PullUp,
			adapter.onBridgeHostEvent(ReaderBridgeEvent.PullUp)
		)
		assertEquals(
			ReaderEngineEvent.Error(message = "Failed", code = "open"),
			adapter.onBridgeHostEvent(ReaderBridgeEvent.Error(message = "Failed", code = "open"))
		)
		assertEquals(
			ReaderEngineEvent.SearchResults(
				query = "party",
				results = listOf(ReaderSearchResult(id = "result-1", href = "chapter-01.xhtml"))
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.SearchResults(
					query = "party",
					results = listOf(ReaderSearchResult(id = "result-1", href = "chapter-01.xhtml"))
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.SelectionChanged(
				text = "selected",
				cfi = "epubcfi(/6/8!/4/2:12)",
				href = "chapter-01.xhtml",
				footnote = true,
				contextText = "The selected sentence and its surrounding context.",
				posLeft = 10.5,
				posTop = 20.25,
				posRight = 120.75,
				posBottom = 140.0
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.SelectionChanged(
					text = "selected",
					cfi = "epubcfi(/6/8!/4/2:12)",
					href = "chapter-01.xhtml",
					footnote = true,
					contextText = "The selected sentence and its surrounding context.",
					posLeft = 10.5,
					posTop = 20.25,
					posRight = 120.75,
					posBottom = 140.0
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.MediaOverlayActive(
				ReaderOverlayFragment(
					resourceHref = "audio/chapter-01.mp3",
					fragmentId = "clip-1",
					label = "Chapter 1"
				)
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.OverlayFragmentActive(
					ReaderOverlayFragment(
						resourceHref = "audio/chapter-01.mp3",
						fragmentId = "clip-1",
						label = "Chapter 1"
					)
				)
			)
		)
		assertEquals(
			ReaderEngineEvent.PaginationProfileStatusChanged(
				ReaderPaginationProfileStatus(
					status = "measuring",
					completedSections = 2,
					totalSections = 6
				)
			),
			adapter.onBridgeHostEvent(
				ReaderBridgeEvent.PaginationProfileStatusChanged(
					ReaderPaginationProfileStatus(
						status = "measuring",
						completedSections = 2,
						totalSections = 6
					)
				)
			)
		)
		assertNull(
			adapter.onBridgeHostEvent(ReaderBridgeEvent.CenterTap),
			"Komikku-native reader navigation owns menu taps; Foliate bridge center taps must not own chrome."
		)
	}

	@Test
	fun mapsBridgeContentClaimsWithMetadataToEngineEvents() {
		val claim = ReaderContentActionClaim(
			action = ReaderContentAction.Link,
			source = "link",
			href = "EPUB/Text/chapter-02.xhtml#door",
			text = "Chapter II"
		)

		assertEquals(
			ReaderEngineEvent.ContentActionClaimed(claim),
			FoliateEpubEngineAdapter().onBridgeHostEvent(ReaderBridgeEvent.ContentTapHandled(claim))
		)
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
			mediaOverlayEnabled = false,
			externalShellCover = true,
			startLocator = ReaderLocator(
				href = "chapter-01.xhtml",
				cfi = "epubcfi(/6/2!/4/1:0)",
				progress = 0.12,
				pageIndex = 0,
				pageCount = 411
			),
			settings = defaultReaderSettings().copy(
				theme = ReaderSepiaTheme,
				fontSizePercent = 117,
				tapZone = ReaderTapZoneLShaped
			),
			nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg",
			canReturnToShellCover = true
		)

	private fun ReaderEngineViewState.WebViewPublication.bridgeCommand(): ReaderBridgeCommand? =
		(command as? ReaderEngineHostCommand.FoliateBridge)?.command

	private fun FoliateEpubEngineAdapter.onBridgeHostEvent(event: ReaderBridgeEvent): ReaderEngineEvent? =
		onHostEvent(ReaderEngineHostEvent.FoliateBridge(event))
}
