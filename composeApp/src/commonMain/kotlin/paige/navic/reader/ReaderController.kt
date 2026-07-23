package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress
import kotlin.math.roundToLong

data class ReaderControllerStep(
	val controller: ReaderController,
	val engineCommands: List<ReaderEngineCommand> = emptyList(),
	val progressToSave: BinderyReadingProgress? = null,
	val whispersyncAudioSeekTarget: WhispersyncAudioSeekTarget? = null,
	val readaloudPlaybackCommand: ReaderReadaloudPlaybackCommand? = null
)

data class ReaderControllerBackStep(
	val controller: ReaderController,
	val engineCommands: List<ReaderEngineCommand> = emptyList(),
	val handled: Boolean = true,
	val readaloudPlaybackCommand: ReaderReadaloudPlaybackCommand? = null
)

data class ReaderController(
	val state: ReaderControllerState = ReaderControllerState(),
	private val progressSaveGate: ReaderProgressSaveGate = ReaderProgressSaveGate()
) {
	fun open(request: ReaderEngineOpenRequest): ReaderControllerStep {
		val normalizedRequest = request.copy(settings = request.settings.normalizedReaderSettings())
		return ReaderControllerStep(
			controller = copy(
				progressSaveGate = progressSaveGate.reset(),
				state = state.copy(
					publication = normalizedRequest.publication,
					activeEngine = normalizedRequest.publication.format,
					chrome = state.chrome.copy(
						currentLocator = normalizedRequest.startLocator,
						settings = normalizedRequest.settings
					),
					chapterProgress = normalizedRequest.startLocator
						?.let { state.chapterProgress.updatedFrom(it, tocTitle = null) }
						?: ReaderChapterProgressState(),
					loadedDocument = null,
					lastLinkInteraction = null,
					externalLinkPrompt = null,
					lastAnnotationInteraction = null,
					annotationPopup = null,
					footnotePopup = null,
					lastOverlayInteraction = null,
					shellCoverVisible = !normalizedRequest.nativeShellCoverUrl.isNullOrBlank(),
					nativeShellCoverUrl = normalizedRequest.nativeShellCoverUrl,
					nativeShellCoverReturnLocatorKey = null,
					canReturnToShellCover = normalizedRequest.canReturnToShellCover,
					menuVisible = false,
					dialog = null,
					search = ReaderSearchState(),
					selection = null,
					selectionNoteDraft = null,
					paginationProfile = ReaderPaginationProfileStatus(),
					foliateSessionId = null,
					pageTurnSettlementAck = null,
					whispersync = ReaderWhispersyncSessionState(),
					activeMediaOverlay = null,
					audioMetadataLabel = null,
					lastContentActionClaim = null
				)
			),
			engineCommands = listOf(ReaderEngineCommand.OpenPublication(normalizedRequest))
		)
	}

	fun onEngineEvent(event: ReaderEngineEvent): ReaderControllerStep {
		if (event.requiredCapability?.let(state::supportsReaderEngineCapability) == false) {
			return ReaderControllerStep(this)
		}
		return when (event) {
			ReaderEngineEvent.PublicationReady -> {
				val decision = progressSaveGate.onEngineEvent(event)
				ReaderControllerStep(copy(progressSaveGate = decision.state))
			}
			is ReaderEngineEvent.Relocated -> {
				val decision = if (event.locator.isWhispersyncAudioFollowRelocation()) {
					ReaderProgressSaveDecision(state = progressSaveGate)
				} else {
					progressSaveGate.onEngineEvent(event)
				}
				val reduction = ReaderProgressReducer.onRelocated(state, event, decision)
				ReaderControllerStep(
					controller = copy(
						progressSaveGate = decision.state,
						state = reduction.state
					),
					progressToSave = reduction.progressToSave
				)
			}
			is ReaderEngineEvent.TocItemChanged -> ReaderControllerStep(
				copy(
					state = state.copy(
						chrome = state.chrome.onTocItemChanged(event.title)
					)
				)
			)
			is ReaderEngineEvent.PaginationProfileStatusChanged -> ReaderControllerStep(
				copy(state = state.copy(paginationProfile = event.profile))
			)
			is ReaderEngineEvent.ContentActionClaimed -> ReaderControllerStep(
				copy(state = state.copy(lastContentActionClaim = event.claim))
			)
			is ReaderEngineEvent.InternalLinkRequested -> ReaderControllerStep(
				copy(
					state = state.copy(
						lastLinkInteraction = ReaderLinkInteraction.Internal(
							href = event.href,
							prevented = event.prevented,
							source = event.source
						)
					)
				)
			)
			is ReaderEngineEvent.ExternalLinkOpened -> ReaderOverlayReducer.onExternalLink(this, event)
			is ReaderEngineEvent.AnnotationClicked -> ReaderAnnotationReducer.onClicked(this, event)
			is ReaderEngineEvent.AnnotationDrawn -> ReaderAnnotationReducer.onDrawn(this, event)
			is ReaderEngineEvent.OverlayCreated -> ReaderControllerStep(
				copy(
					state = state.copy(
						lastOverlayInteraction = ReaderOverlayInteraction.Created(index = event.index)
					)
				)
			)
			is ReaderEngineEvent.DocLoaded -> ReaderProgressReducer.onDocumentLoaded(this, event)
			is ReaderEngineEvent.FootnoteOpened -> ReaderOverlayReducer.onFootnoteOpened(this, event)
			ReaderEngineEvent.FootnoteClose -> ReaderOverlayReducer.onFootnoteClosed(this)
			is ReaderEngineEvent.PullUp -> ReaderControllerStep(
				copy(
					state = state.copy(
						lastOverlayInteraction = ReaderOverlayInteraction.PullUp,
						menuVisible = state.menuVisible
					)
				)
			)
			is ReaderEngineEvent.VisibleTextRange ->
				ReaderWhispersyncReducer.onVisibleTextRange(this, event)
			is ReaderEngineEvent.TextPoint -> ReaderWhispersyncReducer.onTextPoint(this, event)
			is ReaderEngineEvent.SearchResults -> ReaderSearchReducer.onResults(this, event)
			is ReaderEngineEvent.Toc -> ReaderControllerStep(
				copy(state = state.copy(toc = event.items))
			)
			is ReaderEngineEvent.SelectionChanged -> ReaderSelectionReducer.onChanged(this, event)
			ReaderEngineEvent.SelectionCleared -> ReaderSelectionReducer.clear(this)
			is ReaderEngineEvent.MediaOverlayActive -> ReaderOverlayReducer.onActive(this, event)
			is ReaderEngineEvent.MediaOverlayInactive -> ReaderOverlayReducer.onInactive(this, event)
			is ReaderEngineEvent.Error -> ReaderControllerStep(
				copy(
					state = state.copy(
						errorMessage = event.message,
						errorCode = event.code
					)
				)
			)
		}
	}

	fun search(query: String): ReaderControllerStep = ReaderSearchReducer.search(this, query)

	fun updateSearchInput(query: String): ReaderControllerStep =
		ReaderSearchReducer.updateInput(this, query)

	fun clearSearch(): ReaderControllerStep = ReaderSearchReducer.clear(this)

	fun navigateToSearchResult(result: ReaderSearchResult): ReaderControllerStep {
		val cfi = result.cfi.normalizedReaderSelectionValue()
		val href = result.href.normalizedReaderSelectionValue()
		if (cfi == null && href == null) return ReaderControllerStep(this)
		return navigateTo(
			ReaderLocator(
				href = href,
				cfi = cfi
			)
		)
	}

	fun navigateTo(locator: ReaderLocator): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(ReaderEngineCommand.NavigateTo(locator))
		)

	fun navigateToBookmark(bookmark: ReaderBookmark): ReaderControllerStep =
		navigateToSavedMark(bookmark.toLocator())

	fun navigateToAnnotation(annotation: ReaderAnnotation): ReaderControllerStep =
		navigateToSavedMark(annotation.toLocator())

	fun navigateToChapterPage(pageIndex: Int): ReaderControllerStep =
		ReaderProgressReducer.navigateToChapterPage(this, pageIndex)

	fun navigateToPreviousChapter(): ReaderControllerStep =
		navigateToAdjacentTocChapter(direction = -1)

	fun navigateToNextChapter(): ReaderControllerStep =
		navigateToAdjacentTocChapter(direction = 1)

	private fun navigateToAdjacentTocChapter(direction: Int): ReaderControllerStep =
		ReaderProgressReducer.navigateToAdjacentChapter(this, direction)

	private fun navigateToSavedMark(locator: ReaderLocator): ReaderControllerStep =
		ReaderControllerStep(
			controller = copy(
				state = state.copy(
					dialog = null,
					menuVisible = true
				)
			),
			engineCommands = listOf(ReaderEngineCommand.NavigateTo(locator))
		)

	fun applyMediaOverlay(fragment: ReaderOverlayFragment): ReaderControllerStep =
		ReaderOverlayReducer.apply(this, fragment)

	fun updateMediaOverlayProgress(fragment: ReaderOverlayFragment): ReaderControllerStep =
		ReaderOverlayReducer.updateProgress(this, fragment)

	fun onReadaloudPlaybackState(playbackState: ReaderReadaloudPlaybackUiState): ReaderControllerStep =
		ReaderWhispersyncReducer.onReadaloudPlaybackState(this, playbackState)

	fun loadWhispersyncSidecar(sidecar: WhispersyncSidecar): ReaderControllerStep =
		ReaderWhispersyncReducer.loadSidecar(this, sidecar)

	fun reportWhispersyncLoadFailure(
		message: ReaderWhispersyncStatusMessage,
		detail: String? = null
	): ReaderControllerStep =
		ReaderWhispersyncReducer.reportLoadFailure(this, message, detail)

	fun repairWhispersyncMismatch(): ReaderControllerStep =
		ReaderWhispersyncReducer.repairMismatch(this)

	fun addSelectionHighlight(color: String = DefaultReaderHighlightColor): ReaderControllerStep =
		ReaderSelectionReducer.addHighlight(this, color)

	fun startSelectionNote(): ReaderControllerStep = ReaderSelectionReducer.startNote(this)

	fun saveSelectionNote(note: String): ReaderControllerStep =
		ReaderSelectionReducer.saveNote(this, note)

	fun dismissSelectionActions(): ReaderControllerStep = ReaderSelectionReducer.dismissActions(this)

	fun dismissSelectionNote(): ReaderControllerStep = ReaderSelectionReducer.dismissNote(this)

	fun updateSelectionNoteDraft(note: String): ReaderControllerStep =
		ReaderSelectionReducer.updateNoteDraft(this, note)

	fun dismissAnnotationPopup(): ReaderControllerStep = ReaderAnnotationReducer.dismissPopup(this)

	fun dismissFootnotePopup(): ReaderControllerStep = ReaderOverlayReducer.onFootnoteClosed(this)

	fun dismissExternalLinkPrompt(): ReaderControllerStep =
		ReaderOverlayReducer.dismissExternalLink(this)

	fun toggleCurrentBookmark(): ReaderControllerStep {
		val publication = state.publication ?: return ReaderControllerStep(this)
		val nextBookmarks = state.bookmarks.toggleBookmark(
			bookId = publication.bookId,
			bookTitle = publication.title,
			locator = state.chrome.currentLocator,
			sectionTitle = state.chrome.currentSectionTitle
		)
		return if (nextBookmarks == state.bookmarks) {
			ReaderControllerStep(this)
		} else {
			ReaderControllerStep(copy(state = state.copy(bookmarks = nextBookmarks)))
		}
	}

	fun clearMediaOverlay(fragmentId: String? = null): ReaderControllerStep =
		ReaderOverlayReducer.clear(this, fragmentId)

	fun onViewerAction(action: ReaderViewerAction): ReaderControllerStep {
		val controller = if (state.lastContentActionClaim != null) {
			copy(state = state.copy(lastContentActionClaim = null))
		} else {
			this
		}

		if (controller.state.shellCoverVisible) {
			return controller.onShellCoverViewerAction(action)
		}

		return when (action) {
			ReaderViewerAction.Menu -> ReaderControllerStep(
				controller.copy(
					state = controller.state.copy(
						menuVisible = !controller.state.menuVisible
					)
				)
			)
			is ReaderViewerAction.TurnPage -> controller.turnPage(action.direction)
			is ReaderViewerAction.PreviewPageDrag -> controller.previewPageDrag(action)
			is ReaderViewerAction.ScrollViewport -> controller.scrollViewport(action.direction)
			is ReaderViewerAction.NavigateTo -> controller.navigateTo(action.locator)
			is ReaderViewerAction.ContentLongPressAt -> controller.contentLongPressAt(action)
		}
	}

	fun onBack(): ReaderControllerBackStep {
		val closeOverlay = when {
			state.dialog == ReaderControllerDialog.Search -> closeSearchDialog()
			state.dialog != null -> closeDialog()
			state.selectionNoteDraft != null -> dismissSelectionNote()
			state.selectionActions.visible -> dismissSelectionActions()
			state.annotationPopup?.visible == true -> dismissAnnotationPopup()
			state.footnotePopup?.visible == true -> dismissFootnotePopup()
			state.externalLinkPrompt != null -> dismissExternalLinkPrompt()
			state.menuVisible -> hideMenus()
			else -> null
		}
		if (closeOverlay != null) return closeOverlay.asBackStep(handled = true)

		return returnToShellCoverBeforeLeaving()
	}

	fun onNavigateBack(): ReaderControllerBackStep {
		val closeOverlay = when {
			state.dialog == ReaderControllerDialog.Search -> closeSearchDialog()
			state.dialog != null -> closeDialog()
			state.selectionNoteDraft != null -> dismissSelectionNote()
			state.selectionActions.visible -> dismissSelectionActions()
			state.annotationPopup?.visible == true -> dismissAnnotationPopup()
			state.footnotePopup?.visible == true -> dismissFootnotePopup()
			state.externalLinkPrompt != null -> dismissExternalLinkPrompt()
			else -> null
		}
		if (closeOverlay != null) return closeOverlay.asBackStep(handled = true)

		return returnToShellCoverBeforeLeaving()
	}

	private fun returnToShellCoverBeforeLeaving(): ReaderControllerBackStep {
		if (
			!state.shellCoverVisible &&
			state.canReturnToShellCover &&
			!state.nativeShellCoverUrl.isNullOrBlank()
		) {
			return ReaderControllerBackStep(
				controller = copy(
					state = state.copy(
						shellCoverVisible = true,
						menuVisible = false,
						dialog = null
					)
				),
				handled = true,
				readaloudPlaybackCommand = state.shellCoverReadaloudResetCommand()
			)
		}

		return ReaderControllerBackStep(
			controller = this,
			handled = false
		)
	}

	fun applySettings(settings: ReaderSettings): ReaderControllerStep {
		val normalized = settings.normalizedReaderSettings()
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.copy(settings = normalized),
					nativeShellCoverReturnLocatorKey = null
				)
			),
			engineCommands = listOf(
				ReaderEngineCommand.ApplySettings(normalized)
			)
		)
	}

	fun openContentsDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.Contents)

	fun openSearchDialog(): ReaderControllerStep =
		if (state.supportsReaderEngineCapability(ReaderEngineCapability.Search)) {
			openDialog(ReaderControllerDialog.Search)
		} else {
			ReaderControllerStep(this)
		}

	fun openSettingsDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.Settings)

	fun openWhispersyncPlayerDialog(): ReaderControllerStep =
		if (state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
			openDialog(ReaderControllerDialog.WhispersyncPlayer)
		} else {
			ReaderControllerStep(this)
		}

	private fun openDialog(dialog: ReaderControllerDialog): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					dialog = dialog,
					menuVisible = true
				)
			)
		)

	fun showMenus(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(menuVisible = true)))

	fun hideMenus(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(menuVisible = false)))

	fun closeDialog(): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					dialog = null,
					menuVisible = true
				)
			)
		)

	fun closeSearchDialog(): ReaderControllerStep =
		ReaderControllerStep(
			controller = copy(
				state = state.copy(
					dialog = null,
					menuVisible = true,
					search = ReaderSearchState()
				)
			),
			engineCommands = listOf(ReaderEngineCommand.ClearSearch)
		)

	private fun onShellCoverViewerAction(action: ReaderViewerAction): ReaderControllerStep {
		return when {
			action == ReaderViewerAction.Menu -> ReaderControllerStep(
				copy(state = state.copy(menuVisible = !state.menuVisible))
			)
			action == ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next) ||
				action == ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down) -> ReaderControllerStep(
				copy(state = state.copy(shellCoverVisible = false, menuVisible = false))
			)
			else -> ReaderControllerStep(this)
		}
	}

	private fun turnPage(direction: ReaderPageTurnDirection): ReaderControllerStep =
		if (
			direction == ReaderPageTurnDirection.Previous &&
			state.canReturnToShellCover &&
			state.nativeShellCoverReturnLocatorKey == readerNativeShellCoverReturnLocatorKey(state.chrome.currentLocator) &&
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = state.nativeShellCoverUrl,
				shellCoverVisible = state.shellCoverVisible,
				locator = state.chrome.currentLocator
			)
		) {
			ReaderControllerStep(
				copy(
					state = state.copy(
						shellCoverVisible = true,
						nativeShellCoverReturnLocatorKey = readerNativeShellCoverReturnLocatorKey(state.chrome.currentLocator),
						menuVisible = false,
						dialog = null
					)
				),
				readaloudPlaybackCommand = state.shellCoverReadaloudResetCommand()
			)
		} else {
			ReaderControllerStep(
				controller = this,
				engineCommands = listOf(ReaderEngineCommand.TurnPage(direction))
			)
		}

	private fun scrollViewport(direction: ReaderViewportScrollDirection): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(ReaderEngineCommand.ScrollViewport(direction))
		)

	private fun previewPageDrag(action: ReaderViewerAction.PreviewPageDrag): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(
				ReaderEngineCommand.PreviewPageDrag(
					deltaX = action.deltaX,
					deltaY = action.deltaY,
					viewWidth = action.viewWidth,
					viewHeight = action.viewHeight,
					phase = action.phase
				)
			)
		)

	private fun contentLongPressAt(action: ReaderViewerAction.ContentLongPressAt): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(
				ReaderEngineCommand.ContentLongPressAt(
					x = action.x,
					y = action.y,
					viewWidth = action.viewWidth,
					viewHeight = action.viewHeight,
					selectText = !state.whispersyncOwnsTextSelection()
				)
			)
	)
}

internal fun ReaderControllerState.whispersyncOwnsTextSelection(): Boolean =
	whispersync.available &&
		chrome.readaloudPlayback.isAvailable &&
		chrome.readaloudPlayback.syncEnabled

private fun ReaderControllerState.shellCoverReadaloudResetCommand(): ReaderReadaloudPlaybackCommand? =
	if (chrome.readaloudPlayback.isAvailable) {
		ReaderReadaloudPlaybackCommand.StopAndReset
	} else {
		null
	}

internal fun readerNativeShellCoverReturnLocatorKey(locator: ReaderLocator?): String? {
	locator ?: return null
	val href = locator.href
		?.trim()
		?.replace('\\', '/')
		.orEmpty()
	val pageIndex = locator.pageIndex?.takeIf { it >= 0 }?.toString().orEmpty()
	val pageCount = locator.pageCount?.takeIf { it > 0 }?.toString().orEmpty()
	val chapterPageIndex = locator.chapterPageIndex?.takeIf { it >= 0 }?.toString().orEmpty()
	val chapterPageCount = locator.chapterPageCount?.takeIf { it > 0 }?.toString().orEmpty()
	return listOf(
		href.substringBefore('#').substringBefore('?'),
		pageIndex,
		pageCount,
		chapterPageIndex,
		chapterPageCount
	).joinToString("|")
}

internal fun readerExplicitReadableRelocationDismissesNativeShellCover(
	shellCoverVisible: Boolean,
	locator: ReaderLocator
): Boolean {
	if (!shellCoverVisible) return false
	val reason = locator.reason?.trim().orEmpty()
	if (
		reason.isBlank() ||
		reason == "relocate-committed" ||
		reason == "initial-resume" ||
		reason.startsWith("pagination-profile-")
	) return false
	val href = locator.href?.trim().orEmpty()
	return href.isBlank() || !readerHrefLooksLikeNativeShellCoverBoundary(href)
}

private fun ReaderControllerStep.asBackStep(handled: Boolean): ReaderControllerBackStep =
	ReaderControllerBackStep(
		controller = controller,
		engineCommands = engineCommands,
		handled = handled
	)

internal fun ReaderEngineEvent.VisibleTextRange.isWhispersyncAudioFollowRange(): Boolean =
	source.equals("media-overlay-follow", ignoreCase = true)

internal fun ReaderOverlayFragment.isOutsideWhispersyncVisibleRange(
	visibleRange: ReaderWhispersyncVisibleTextRange?
): Boolean {
	visibleRange ?: return false
	val fragmentHref = textHref?.trim()?.takeIf { it.isNotEmpty() }
	val visibleHref = visibleRange.textHref.trim().takeIf { it.isNotEmpty() }
	if (
		fragmentHref != null &&
		visibleHref != null &&
		readerTocHrefKey(fragmentHref) != readerTocHrefKey(visibleHref)
	) {
		return true
	}
	val start = textStart ?: return false
	val end = textEnd ?: return false
	if (end <= start) return false
	return end <= visibleRange.visibleStart || start >= visibleRange.visibleEnd
}

internal fun ReaderEngineCommand?.overlayFragmentOrNull(): ReaderOverlayFragment? =
	when (this) {
		is ReaderEngineCommand.ApplyMediaOverlay -> fragment
		is ReaderEngineCommand.UpdateMediaOverlayProgress -> fragment
		else -> null
	}

private fun ReaderLocator.isWhispersyncAudioFollowRelocation(): Boolean =
	reason.equals("media-overlay-follow", ignoreCase = true)

internal fun ReaderWhispersyncSessionState.audioSeekTargetForActiveOverlay(
	fragment: ReaderOverlayFragment
): WhispersyncAudioSeekTarget? {
	if (!available || !sync.syncEnabled) return null
	val clipBeginSeconds = fragment.clipBeginSeconds
		?.takeIf(Double::isFinite)
		?: return null
	val startMs = (clipBeginSeconds * 1000.0).roundToLong().coerceAtLeast(0L)
	val endMs = fragment.clipEndSeconds
		?.takeIf(Double::isFinite)
		?.let { (it * 1000.0).roundToLong().coerceAtLeast(startMs) }
		?: startMs
	val segment = WhispersyncSegment(
		id = fragment.fragmentId,
		audioResource = fragment.resourceHref,
		startMs = startMs,
		endMs = endMs,
		textHref = fragment.textHref?.trim().orEmpty(),
		fragmentId = fragment.fragmentId,
		textStart = fragment.textStart,
		textEnd = fragment.textEnd,
		label = fragment.label
	)
	return WhispersyncAudioSeekTarget(
		audioResource = segment.audioResource,
		positionMs = segment.startMs,
		segment = segment
	)
}
