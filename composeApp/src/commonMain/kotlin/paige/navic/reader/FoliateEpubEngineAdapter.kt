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

data class FoliatePublicationEngineAdapter(
	private val publicationFormat: ReaderPublicationFormat,
	private val currentViewState: ReaderEngineViewState.WebViewPublication? = null,
	private val currentCommandKey: Long = 0L
) : FoliateWebViewEngineAdapter(
	format = publicationFormat,
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

	override fun onCommand(command: ReaderEngineCommand): ReaderEngineStep {
		if (!supports(command)) {
			return ReaderEngineStep(
				engine = this,
				viewState = currentViewState ?: ReaderEngineViewState.Empty
			)
		}
		return when (command) {
			is ReaderEngineCommand.OpenPublication -> open(command.request)
			is ReaderEngineCommand.NavigateTo -> navigateTo(
				locator = command.locator,
				relocationReason = command.relocationReason,
				causalSequence = command.causalSequence
			)
			is ReaderEngineCommand.Search -> dispatch(ReaderBridgeCommand.Search(command.query))
			ReaderEngineCommand.ClearSearch -> dispatch(ReaderBridgeCommand.ClearSearch)
			is ReaderEngineCommand.TurnPage -> turnPage(
				direction = command.direction,
				causalSequence = command.causalSequence
			)
			is ReaderEngineCommand.PreviewPageDrag -> dispatch(
				ReaderBridgeCommand.PreviewPageDrag(
					deltaX = command.deltaX,
					deltaY = command.deltaY,
					viewWidth = command.viewWidth,
					viewHeight = command.viewHeight,
					phase = command.phase
				)
			)
			is ReaderEngineCommand.ScrollViewport -> scrollViewport(
				direction = command.direction,
				causalSequence = command.causalSequence
			)
			is ReaderEngineCommand.ContentLongPressAt -> dispatch(
				ReaderBridgeCommand.ContentLongPressAt(
					x = command.x,
					y = command.y,
					viewWidth = command.viewWidth,
					viewHeight = command.viewHeight,
					selectText = command.selectText,
					causalSequence = command.causalSequence
				)
			)
			is ReaderEngineCommand.ApplySettings -> dispatch(
				ReaderBridgeCommand.ApplySettings(command.settings.normalizedReaderSettings())
			)
			is ReaderEngineCommand.ApplyAnnotations -> dispatch(
				ReaderBridgeCommand.ApplyHighlights(command.annotations)
			)
			is ReaderEngineCommand.RequestVisibleTextRange -> dispatch(
				ReaderBridgeCommand.RequestVisibleTextRange(command.source)
			)
			is ReaderEngineCommand.InstallRawTextProvenance -> retainRawTextProvenance(command.descriptor)
			is ReaderEngineCommand.ApplyMediaOverlay -> dispatch(
				ReaderBridgeCommand.ApplyOverlayFragment(command.fragment)
			)
			is ReaderEngineCommand.UpdateMediaOverlayProgress -> dispatch(
				ReaderBridgeCommand.UpdateOverlayFragmentProgress(command.fragment)
			)
			is ReaderEngineCommand.ReplaceWhispersyncCueMap -> dispatch(
				ReaderBridgeCommand.ReplaceWhispersyncCueMap(command.presentation)
			)
			is ReaderEngineCommand.CancelWhispersyncCueMapHold -> dispatch(
				ReaderBridgeCommand.CancelWhispersyncCueMapHold(command.reason)
			)
			is ReaderEngineCommand.ClearMediaOverlayPresentation -> dispatch(
				ReaderBridgeCommand.ClearOverlayPresentation(
					overlayRequestId = command.overlayRequestId,
					clearedThroughBoundarySequence = command.clearedThroughBoundarySequence
				)
			)
			ReaderEngineCommand.ClearMediaOverlay -> dispatch(ReaderBridgeCommand.ClearOverlay)
		}
	}

	override fun onHostEvent(event: ReaderEngineHostEvent): ReaderEngineEvent? =
		when (event) {
			is ReaderEngineHostEvent.FoliateBridge -> onBridgeEvent(event.event)
			is ReaderEngineHostEvent.SettingsPresentationCommitted ->
				ReaderEngineEvent.SettingsPresentationCommitted(event.snapshotKey)
		}

	private fun onBridgeEvent(event: ReaderBridgeEvent): ReaderEngineEvent? {
		val engineEvent = when (event) {
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
				foliateSessionId = event.foliateSessionId,
				tocTitle = event.tocTitle,
				pageTurnSettleToken = event.pageTurnSettleToken,
				pageTurnSettleSessionId = event.pageTurnSettleSessionId,
				pageTurnSettleRasterGeneration = event.pageTurnSettleRasterGeneration,
				pageTurnSettleTextureGeneration = event.pageTurnSettleTextureGeneration,
				causalSequence = event.causalSequence,
				destinationCommitIdentity = event.destinationCommitIdentity
			)
			is ReaderBridgeEvent.TocItemChanged -> ReaderEngineEvent.TocItemChanged(
				href = event.href,
				title = event.title
			)
			is ReaderBridgeEvent.PaginationProfileStatusChanged ->
				ReaderEngineEvent.PaginationProfileStatusChanged(event.profile)
			is ReaderBridgeEvent.SearchResults -> ReaderEngineEvent.SearchResults(
				query = event.query,
				results = event.results,
				progress = event.progress,
				complete = event.complete
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
				rangeCfi = event.rangeCfi,
				source = event.source,
				rawProvenanceId = event.rawProvenanceId,
				rawSpineIndex = event.rawSpineIndex,
				rawByteStart = event.rawByteStart,
				rawByteEnd = event.rawByteEnd,
				causalSequence = event.causalSequence,
				destinationCommitIdentity = event.destinationCommitIdentity
			)
			is ReaderBridgeEvent.TextPoint -> ReaderEngineEvent.TextPoint(
				textHref = event.textHref,
				textOffset = event.textOffset,
				rangeCfi = event.rangeCfi,
				source = event.source,
				rawProvenanceId = event.rawProvenanceId,
				rawByteOffset = event.rawByteOffset,
				causalSequence = event.causalSequence,
				destinationCommitIdentity = event.destinationCommitIdentity
			)
			is ReaderBridgeEvent.WhispersyncCueMapRendered ->
				ReaderEngineEvent.WhispersyncCueMapRendered(
					sourceOrdinalsInDomReadingOrder = event.sourceOrdinalsInDomReadingOrder,
					revisionDigest = event.revisionDigest,
					presentationGeneration = event.presentationGeneration,
					destinationCommitIdentity = event.destinationCommitIdentity
				)
			is ReaderBridgeEvent.WhispersyncCueMapSeekRequested ->
				ReaderEngineEvent.WhispersyncCueMapSeekRequested(
					sourceOrdinal = event.sourceOrdinal,
					revisionDigest = event.revisionDigest,
					presentationGeneration = event.presentationGeneration,
					destinationCommitIdentity = event.destinationCommitIdentity
				)
			is ReaderBridgeEvent.WhispersyncCueMapHoldOutcome ->
				ReaderEngineEvent.WhispersyncCueMapHoldOutcome(
					sourceOrdinal = event.sourceOrdinal,
					revisionDigest = event.revisionDigest,
					presentationGeneration = event.presentationGeneration,
					outcome = event.outcome
				)
			is ReaderBridgeEvent.RawTextProvenanceStatusChanged ->
				ReaderEngineEvent.RawTextProvenanceStatusChanged(
					provenanceId = event.provenanceId,
					status = event.status,
					reason = event.reason
				)
			is ReaderBridgeEvent.OverlayFragmentActive -> ReaderEngineEvent.MediaOverlayActive(
				fragment = event.fragment,
				anchorReceipt = event.anchorReceipt
			)
			is ReaderBridgeEvent.OverlayFragmentInactive -> ReaderEngineEvent.MediaOverlayInactive(
				fragmentId = event.fragmentId,
				overlayRequestId = event.overlayRequestId,
				coordinateMode = event.coordinateMode,
				reason = event.reason
			)
			is ReaderBridgeEvent.Error -> ReaderEngineEvent.Error(
				message = event.message,
				code = event.code
			)
			else -> null
		}
		return engineEvent?.takeIf(::supports)
	}

	private fun open(request: ReaderEngineOpenRequest): ReaderEngineStep {
		val viewState = ReaderEngineViewState.WebViewPublication(
			publicationUrl = request.url,
			title = request.publication.title,
			kind = request.publication.kind,
			mediaOverlayEnabled = request.mediaOverlayEnabled,
			externalShellCover = request.externalShellCover,
			suppressWebShellCover = request.suppressWebShellCover,
			nativeShellCoverUrl = request.nativeShellCoverUrl,
			nativeShellCoverTint = request.nativeShellCoverTint,
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

	private fun navigateTo(
		locator: ReaderLocator,
		relocationReason: String?,
		causalSequence: Long?
	): ReaderEngineStep {
		val bridgeCommand = relocationReason
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.let { reason -> ReaderBridgeCommand.GoToLocator(locator, reason, causalSequence) }
			?: when {
			!locator.cfi.isNullOrBlank() -> ReaderBridgeCommand.GoToCfi(locator.cfi, causalSequence)
			!locator.href.isNullOrBlank() && locator.chapterProgress != null ->
				ReaderBridgeCommand.GoToChapterProgress(
					href = locator.href,
					progress = locator.chapterProgress,
					chapterPageIndex = locator.chapterPageIndex,
					chapterPageCount = locator.chapterPageCount,
					causalSequence = causalSequence
				)
			!locator.href.isNullOrBlank() -> ReaderBridgeCommand.GoToHref(locator.href, causalSequence)
			locator.progress != null -> ReaderBridgeCommand.GoToProgress(locator.progress, causalSequence)
			else -> null
		}
		return bridgeCommand?.let(::dispatch) ?: ReaderEngineStep(
			engine = this,
			viewState = currentViewState ?: ReaderEngineViewState.Empty
		)
	}

	private fun turnPage(
		direction: ReaderPageTurnDirection,
		causalSequence: Long?
	): ReaderEngineStep = dispatch(
		when (direction) {
			ReaderPageTurnDirection.Previous -> causalSequence
				?.let(ReaderBridgeCommand::CausalPreviousPage)
				?: ReaderBridgeCommand.PreviousPage
			ReaderPageTurnDirection.Next -> causalSequence
				?.let(ReaderBridgeCommand::CausalNextPage)
				?: ReaderBridgeCommand.NextPage
		}
	)

	private fun scrollViewport(
		direction: ReaderViewportScrollDirection,
		causalSequence: Long?
	): ReaderEngineStep = dispatch(ReaderBridgeCommand.ScrollViewport(direction, causalSequence))

	private fun retainRawTextProvenance(
		descriptor: ReaderRawTextProvenanceDescriptor
	): ReaderEngineStep {
		val nextViewState = currentViewState?.copy(
			rawTextProvenanceDescriptors =
				currentViewState.rawTextProvenanceDescriptors.filterNot { it.id == descriptor.id } + descriptor
		) ?: return ReaderEngineStep(this)
		return ReaderEngineStep(
			engine = copyEngine(nextViewState, currentCommandKey),
			viewState = nextViewState
		)
	}

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
