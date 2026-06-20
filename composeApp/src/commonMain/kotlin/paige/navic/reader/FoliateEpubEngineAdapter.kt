package paige.navic.reader

// Adapted from Anx Reader: tmp/references/anx-reader/lib/page/book_player/epub_player.dart:627-879
// (callback catalog, including translateText at 864)
// tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194 (relocation)
// :216-327 (link/image taxonomy)
// :335-397 (annotations)

data class FoliateEpubEngineAdapter(
	private val currentViewState: ReaderEngineViewState.WebViewPublication? = null,
	private val currentCommandKey: Long = 0L
) : FoliateWebViewEngineAdapter(
	format = ReaderPublicationFormat.Epub,
	currentViewState = currentViewState,
	currentCommandKey = currentCommandKey
) {
	override fun copyEngine(
		currentViewState: ReaderEngineViewState.WebViewPublication?,
		currentCommandKey: Long
	): FoliateWebViewEngineAdapter =
		copy(
			currentViewState = currentViewState,
			currentCommandKey = currentCommandKey
		)
}

data class FoliatePdfEngineAdapter(
	private val currentViewState: ReaderEngineViewState.WebViewPublication? = null,
	private val currentCommandKey: Long = 0L
) : FoliateWebViewEngineAdapter(
	format = ReaderPublicationFormat.Pdf,
	currentViewState = currentViewState,
	currentCommandKey = currentCommandKey
) {
	override fun copyEngine(
		currentViewState: ReaderEngineViewState.WebViewPublication?,
		currentCommandKey: Long
	): FoliateWebViewEngineAdapter =
		copy(
			currentViewState = currentViewState,
			currentCommandKey = currentCommandKey
		)
}

sealed class FoliateWebViewEngineAdapter(
	override val format: ReaderPublicationFormat,
	private val currentViewState: ReaderEngineViewState.WebViewPublication? = null,
	private val currentCommandKey: Long = 0L
) : ReaderEngine {

	protected abstract fun copyEngine(
		currentViewState: ReaderEngineViewState.WebViewPublication?,
		currentCommandKey: Long
	): FoliateWebViewEngineAdapter

	override fun onCommand(command: ReaderEngineCommand): ReaderEngineStep =
		when (command) {
			is ReaderEngineCommand.OpenPublication -> open(command.request)
			is ReaderEngineCommand.NavigateTo -> navigateTo(command.locator)
			is ReaderEngineCommand.Search -> dispatch(ReaderBridgeCommand.Search(command.query))
			ReaderEngineCommand.ClearSearch -> dispatch(ReaderBridgeCommand.ClearSearch)
			is ReaderEngineCommand.TurnPage -> turnPage(command.direction)
			is ReaderEngineCommand.PreviewPageDrag -> dispatch(
				ReaderBridgeCommand.PreviewPageDrag(
					deltaX = command.deltaX,
					deltaY = command.deltaY,
					viewWidth = command.viewWidth,
					viewHeight = command.viewHeight,
					phase = command.phase
				)
			)
			is ReaderEngineCommand.ScrollViewport -> scrollViewport(command.direction)
			is ReaderEngineCommand.ContentLongPressAt -> dispatch(
				ReaderBridgeCommand.ContentLongPressAt(
					x = command.x,
					y = command.y,
					viewWidth = command.viewWidth,
					viewHeight = command.viewHeight
				)
			)
			is ReaderEngineCommand.NavigateHistory -> navigateHistory(command.direction)
			is ReaderEngineCommand.ApplySettings -> dispatch(
				ReaderBridgeCommand.ApplySettings(command.settings.normalizedReaderSettings())
			)
			is ReaderEngineCommand.ApplyAnnotations -> dispatch(
				ReaderBridgeCommand.ApplyHighlights(command.annotations)
			)
			is ReaderEngineCommand.ApplyMediaOverlay -> dispatch(
				ReaderBridgeCommand.ApplyOverlayFragment(command.fragment)
			)
			ReaderEngineCommand.ClearMediaOverlay -> dispatch(ReaderBridgeCommand.ClearOverlay)
		}

	override fun onHostEvent(event: ReaderEngineHostEvent): ReaderEngineEvent? =
		when (event) {
			is ReaderEngineHostEvent.FoliateBridge -> onBridgeEvent(event.event)
		}

	private fun onBridgeEvent(event: ReaderBridgeEvent): ReaderEngineEvent? =
		when (event) {
			ReaderBridgeEvent.PublicationReady -> ReaderEngineEvent.PublicationReady
			is ReaderBridgeEvent.ContentTapHandled -> ReaderEngineEvent.ContentActionClaimed(event.claim)
			is ReaderBridgeEvent.InternalLinkRequested -> ReaderEngineEvent.InternalLinkRequested(
				href = event.href,
				prevented = event.prevented,
				source = event.source
			)
			is ReaderBridgeEvent.ExternalLink -> ReaderEngineEvent.ExternalLinkOpened(
				href = event.href,
				anchorHref = event.anchorHref
			)
			is ReaderBridgeEvent.LocationChanged -> ReaderEngineEvent.Relocated(
				locator = event.locator,
				tocTitle = event.tocTitle
			)
			is ReaderBridgeEvent.TocItemChanged -> ReaderEngineEvent.TocItemChanged(
				href = event.href,
				title = event.title
			)
			is ReaderBridgeEvent.PaginationProfileStatusChanged ->
				ReaderEngineEvent.PaginationProfileStatusChanged(event.profile)
			is ReaderBridgeEvent.SearchResults -> ReaderEngineEvent.SearchResults(
				query = event.query,
				results = event.results
			)
			is ReaderBridgeEvent.Toc -> ReaderEngineEvent.Toc(event.items)
			is ReaderBridgeEvent.SelectionChanged -> ReaderEngineEvent.SelectionChanged(
				text = event.text,
				cfi = event.cfi,
				href = event.href,
				footnote = event.footnote,
				contextText = event.contextText,
				posLeft = event.posLeft,
				posTop = event.posTop,
				posRight = event.posRight,
				posBottom = event.posBottom
			)
			ReaderBridgeEvent.SelectionCleared -> ReaderEngineEvent.SelectionCleared
			is ReaderBridgeEvent.AnnotationClick -> ReaderEngineEvent.AnnotationClicked(
				value = event.value,
				index = event.index,
				rangeCfi = event.rangeCfi
			)
			is ReaderBridgeEvent.AnnotationDrawn -> ReaderEngineEvent.AnnotationDrawn(
				value = event.value,
				index = event.index,
				rangeCfi = event.rangeCfi
			)
			is ReaderBridgeEvent.OverlayCreated -> ReaderEngineEvent.OverlayCreated(index = event.index)
			is ReaderBridgeEvent.LoadDoc -> ReaderEngineEvent.DocLoaded(
				index = event.index,
				href = event.href,
				title = event.title,
				sectionId = event.sectionId
			)
			is ReaderBridgeEvent.PushState -> ReaderEngineEvent.NavigationStateChanged(
				canGoBack = event.canGoBack,
				canGoForward = event.canGoForward
			)
			is ReaderBridgeEvent.FootnoteOpen -> ReaderEngineEvent.FootnoteOpened(
				href = event.href,
				text = event.text,
				noteType = event.noteType,
				hidden = event.hidden
			)
			ReaderBridgeEvent.FootnoteClose -> ReaderEngineEvent.FootnoteClose
			is ReaderBridgeEvent.PullUp -> ReaderEngineEvent.PullUp(source = event.source)
			is ReaderBridgeEvent.VisibleTextRange -> ReaderEngineEvent.VisibleTextRange(
				textHref = event.textHref,
				visibleStart = event.visibleStart,
				visibleEnd = event.visibleEnd,
				rangeCfi = event.rangeCfi
			)
			is ReaderBridgeEvent.OverlayFragmentActive -> ReaderEngineEvent.MediaOverlayActive(event.fragment)
			is ReaderBridgeEvent.OverlayFragmentInactive -> ReaderEngineEvent.MediaOverlayInactive(event.fragmentId)
			is ReaderBridgeEvent.Error -> ReaderEngineEvent.Error(
				message = event.message,
				code = event.code
			)
			else -> null
		}

	private fun open(request: ReaderEngineOpenRequest): ReaderEngineStep {
		val viewState = ReaderEngineViewState.WebViewPublication(
			publicationUrl = request.url,
			title = request.publication.title,
			kind = request.publication.kind,
			mediaOverlayEnabled = request.mediaOverlayEnabled,
			externalShellCover = request.externalShellCover,
			nativeShellCoverUrl = request.nativeShellCoverUrl,
			canReturnToShellCover = request.canReturnToShellCover,
			settings = request.settings.normalizedReaderSettings(),
			startLocator = request.startLocator
		)
		return ReaderEngineStep(
			engine = copyEngine(
				currentViewState = viewState,
				currentCommandKey = 0L
			),
			viewState = viewState
		)
	}

	private fun navigateTo(locator: ReaderLocator): ReaderEngineStep {
		val bridgeCommand = when {
			!locator.cfi.isNullOrBlank() -> ReaderBridgeCommand.GoToCfi(locator.cfi)
			!locator.href.isNullOrBlank() && locator.chapterProgress != null ->
				ReaderBridgeCommand.GoToChapterProgress(
					href = locator.href,
					progress = locator.chapterProgress
				)
			!locator.href.isNullOrBlank() -> ReaderBridgeCommand.GoToHref(locator.href)
			locator.progress != null -> ReaderBridgeCommand.GoToProgress(locator.progress)
			else -> null
		}
		return bridgeCommand?.let(::dispatch) ?: ReaderEngineStep(
			engine = this,
			viewState = currentViewState ?: ReaderEngineViewState.Empty
		)
	}

	private fun turnPage(direction: ReaderPageTurnDirection): ReaderEngineStep =
		dispatch(
			when (direction) {
				ReaderPageTurnDirection.Previous -> ReaderBridgeCommand.PreviousPage
				ReaderPageTurnDirection.Next -> ReaderBridgeCommand.NextPage
			}
		)

	private fun scrollViewport(direction: ReaderViewportScrollDirection): ReaderEngineStep =
		dispatch(ReaderBridgeCommand.ScrollViewport(direction))

	private fun navigateHistory(direction: ReaderHistoryDirection): ReaderEngineStep =
		dispatch(
			when (direction) {
				ReaderHistoryDirection.Back -> ReaderBridgeCommand.HistoryBack
				ReaderHistoryDirection.Forward -> ReaderBridgeCommand.HistoryForward
			}
		)

	private fun dispatch(command: ReaderBridgeCommand): ReaderEngineStep {
		val nextCommandKey = currentCommandKey + 1L
		val nextViewState = currentViewState?.copy(
			command = ReaderEngineHostCommand.FoliateBridge(command),
			commandKey = nextCommandKey
		) ?: return ReaderEngineStep(this)
		return ReaderEngineStep(
			engine = copyEngine(
				currentViewState = nextViewState,
				currentCommandKey = nextCommandKey
			),
			viewState = nextViewState
		)
	}
}
