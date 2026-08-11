package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress

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
					pendingShellCoverDismissal = null,
					menuVisible = false,
					dialog = null,
					search = ReaderSearchState(),
					selection = null,
					selectionNoteDraft = null,
					paginationProfile = ReaderPaginationProfileStatus(),
					readerSettingsPresentationSnapshotKey =
						normalizedRequest.settings.readerPageRasterSnapshotKey(),
					foliateSessionId = null,
					pageTurnSettlementAck = null,
					whispersync = ReaderWhispersyncSessionState(),
					rawTextProvenanceById = emptyMap(),
					activeMediaOverlay = null,
					activeMediaOverlayAnchorReceipt = null,
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
				val shellCoverDismissed = state.shellCoverVisible && !reduction.state.shellCoverVisible
				ReaderControllerStep(
					controller = copy(
						progressSaveGate = decision.state,
						state = reduction.state
					),
					engineCommands = listOfNotNull(
						ReaderEngineCommand.RequestVisibleTextRange("shell-cover-dismissed")
							.takeIf {
								shellCoverDismissed &&
									reduction.state.supportsReaderEngineCapability(
										ReaderEngineCapability.MediaOverlay
									)
							}
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
			is ReaderEngineEvent.SettingsPresentationCommitted -> ReaderControllerStep(
				copy(
					state = state.copy(
						readerSettingsPresentationSnapshotKey = event.snapshotKey
					)
				)
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
			is ReaderEngineEvent.RawTextProvenanceStatusChanged -> {
				if (event.provenanceId !in state.rawTextProvenanceById) {
					ReaderControllerStep(this)
				} else {
					ReaderControllerStep(
						copy(
							state = state.copy(
								rawTextProvenanceById = state.rawTextProvenanceById + (
									event.provenanceId to RawTextProvenanceState(event.status, event.reason)
								)
							)
						)
					)
				}
			}
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

	fun installRawTextProvenance(
		descriptor: ReaderRawTextProvenanceDescriptor
	): ReaderControllerStep {
		if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
			return ReaderControllerStep(this)
		}
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					rawTextProvenanceById = state.rawTextProvenanceById + (
						descriptor.id to RawTextProvenanceState(RawTextProvenanceStatus.Pending)
					)
				)
			),
			engineCommands = listOf(ReaderEngineCommand.InstallRawTextProvenance(descriptor))
		)
	}

	fun applyMediaOverlay(fragment: ReaderOverlayFragment): ReaderControllerStep =
		ReaderOverlayReducer.apply(this, fragment)

	fun updateMediaOverlayProgress(fragment: ReaderOverlayFragment): ReaderControllerStep =
		ReaderOverlayReducer.updateProgress(this, fragment)

	fun onReadaloudPlaybackState(
		playbackState: ReaderReadaloudPlaybackUiState,
		publishOverlayProgress: Boolean = true
	): ReaderControllerStep = ReaderWhispersyncReducer.onReadaloudPlaybackState(
		controller = this,
		playbackState = playbackState,
		publishOverlayProgress = publishOverlayProgress
	)

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
			ReaderViewerAction.NativeShellPrepared -> ReaderControllerStep(controller)
			is ReaderViewerAction.TurnPage -> controller.turnPage(action.direction)
			is ReaderViewerAction.PreviewPageDrag -> controller.previewPageDrag(action)
			is ReaderViewerAction.ScrollViewport -> controller.scrollViewport(action.direction)
			is ReaderViewerAction.NavigateTo -> controller.navigateTo(action.locator)
			is ReaderViewerAction.ContentLongPressAt -> controller.contentLongPressAt(action)
		}
	}

	fun onPageTurnBoundary(direction: ReaderPageTurnDirection): ReaderControllerStep =
		if (
			direction == ReaderPageTurnDirection.Previous &&
			state.canReturnToShellCover &&
			!state.shellCoverVisible &&
			!state.nativeShellCoverUrl.isNullOrBlank()
		) {
			showNativeShellCover()
		} else {
			ReaderControllerStep(
				controller = this,
				engineCommands = listOf(ReaderEngineCommand.TurnPage(direction))
			)
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
						dialog = null,
						whispersync = state.whispersync.forShellCoverPresentation(),
						activeMediaOverlay = null,
						activeMediaOverlayAnchorReceipt = null,
						audioMetadataLabel = null
					)
				),
				engineCommands = state.clearOverlayForShellCoverCommands(),
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
		val current = state.chrome.settings
		val requiresEngineApply =
			current.readerEngineSettingsProjection() != normalized.readerEngineSettingsProjection()
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.copy(settings = normalized),
					readerSettingsPresentationSnapshotKey =
						state.readerSettingsPresentationSnapshotKey
							?: current.readerPageRasterSnapshotKey(),
					nativeShellCoverReturnLocatorKey = null
				)
			),
			engineCommands = if (requiresEngineApply) {
				listOf(ReaderEngineCommand.ApplySettings(normalized))
			} else {
				emptyList()
			}
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
			action == ReaderViewerAction.NativeShellPrepared -> ReaderControllerStep(
				controller = copy(
					state = state.copy(
						shellCoverVisible = false,
						pendingShellCoverDismissal = null,
						nativeShellCoverReturnLocatorKey =
							readerNativeShellCoverReturnLocatorKey(state.chrome.currentLocator),
						menuVisible = false
					)
				),
				engineCommands = listOfNotNull(
					ReaderEngineCommand.RequestVisibleTextRange("shell-cover-dismissed")
						.takeIf {
							state.supportsReaderEngineCapability(
								ReaderEngineCapability.MediaOverlay
							)
						}
				)
			)
			action == ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next) ||
				action == ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down) -> {
				val locator = state.pendingShellCoverDismissal?.locator
					?: state.chrome.currentLocator
				val nextRequestId = state.shellCoverDismissalRequestSequence + 1L
				val dismissalRequest = locator
					?.takeIf(ReaderLocator::hasFoliateNavigationIdentity)
					?.let {
						ReaderShellCoverDismissalRequest(
							requestId = nextRequestId,
							locator = it,
							foliateSessionId = state.foliateSessionId
						)
					}
				ReaderControllerStep(
					controller = copy(
						state = state.copy(
							shellCoverDismissalRequestSequence = dismissalRequest
								?.requestId
								?: state.shellCoverDismissalRequestSequence,
							pendingShellCoverDismissal = dismissalRequest,
							menuVisible = false
						)
					),
					engineCommands = listOfNotNull(
						dismissalRequest?.let {
							ReaderEngineCommand.NavigateTo(
								locator = it.locator,
								relocationReason = readerShellCoverDismissalReason(it.requestId)
							)
						}
					)
				)
			}
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
			showNativeShellCover()
		} else {
			ReaderControllerStep(
				controller = this,
				engineCommands = listOf(ReaderEngineCommand.TurnPage(direction))
			)
		}

	private fun showNativeShellCover(): ReaderControllerStep =
		ReaderControllerStep(
			controller = copy(
				state = state.copy(
					shellCoverVisible = true,
					pendingShellCoverDismissal = null,
					nativeShellCoverReturnLocatorKey = readerNativeShellCoverReturnLocatorKey(
						state.chrome.currentLocator
					),
					menuVisible = false,
					dialog = null,
					whispersync = state.whispersync.forShellCoverPresentation(),
					activeMediaOverlay = null,
					activeMediaOverlayAnchorReceipt = null,
					audioMetadataLabel = null
				)
			),
			engineCommands = state.clearOverlayForShellCoverCommands(),
			readaloudPlaybackCommand = state.shellCoverReadaloudResetCommand()
		)

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

private fun ReaderControllerState.clearOverlayForShellCoverCommands(): List<ReaderEngineCommand> =
	listOfNotNull(
		ReaderEngineCommand.ClearMediaOverlay.takeIf {
			supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay) &&
				(
					whispersync.sync.activeOverlayRequestId != null ||
						activeMediaOverlay != null
				)
		}
	)

private fun ReaderWhispersyncSessionState.forShellCoverPresentation(): ReaderWhispersyncSessionState =
	copy(
		sync = sync.rejectOverlay(null),
		visibleTextRange = null,
		pendingAudioSeek = null,
		status = readerWhispersyncReadyStatus(timeline)
	)

private fun ReaderLocator.hasFoliateNavigationIdentity(): Boolean =
	!cfi.isNullOrBlank() ||
		!href.isNullOrBlank() ||
		progress?.isFinite() == true

private fun ReaderControllerStep.asBackStep(handled: Boolean): ReaderControllerBackStep =
	ReaderControllerBackStep(
		controller = controller,
		engineCommands = engineCommands,
		handled = handled
	)
