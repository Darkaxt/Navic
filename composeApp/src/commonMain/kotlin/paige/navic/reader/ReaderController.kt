package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress

data class ReaderControllerStep(
	val controller: ReaderController,
	val engineCommands: List<ReaderEngineCommand> = emptyList(),
	val progressToSave: BinderyReadingProgress? = null,
	val whispersyncAudioSeekTarget: WhispersyncAudioSeekTarget? = null,
	val readaloudPlaybackCommand: ReaderReadaloudPlaybackCommand? = null,
	val readaloudReaderInteraction: ReaderReadaloudReaderInteraction? = null,
	val presentationEffects: List<ReaderPresentationEffect> = emptyList(),
	val presentationReceipt: ReaderPresentationEventReceipt? = null
)

data class ReaderControllerBackStep(
	val controller: ReaderController,
	val engineCommands: List<ReaderEngineCommand> = emptyList(),
	val handled: Boolean = true,
	val readaloudPlaybackCommand: ReaderReadaloudPlaybackCommand? = null,
	val presentationEffects: List<ReaderPresentationEffect> = emptyList(),
	val presentationReceipt: ReaderPresentationEventReceipt? = null
)

data class ReaderController(
	val state: ReaderControllerState = ReaderControllerState(),
	private val progressSaveGate: ReaderProgressSaveGate = ReaderProgressSaveGate(),
	internal val presentationEventSequence: Long = 0L,
	internal val presentationPublicationIdentity: ReaderPresentationPublicationIdentity? =
		state.presentation.binding?.publicationIdentity
) {
	val presentationVersion: ReaderPresentationReceiptVersion
		get() = ReaderPresentationReceiptVersion(
			readerSessionGeneration = state.readerSessionGeneration,
			publicationIdentity = presentationPublicationIdentity,
			eventSequence = presentationEventSequence
		)

	fun open(request: ReaderEngineOpenRequest): ReaderControllerStep {
		val normalizedRequest = request.copy(settings = request.settings.normalizedReaderSettings())
		return ReaderControllerStep(
			controller = copy(
				progressSaveGate = progressSaveGate.reset(),
				presentationEventSequence = 0L,
				presentationPublicationIdentity = null,
				state = state.copy(
					readerSessionGeneration = Math.incrementExact(state.readerSessionGeneration),
					publication = normalizedRequest.publication,
					activeEngine = normalizedRequest.publication.format,
					presentation = ReaderPresentationState(
						nextTokenValue = state.presentation.nextTokenValue
					),
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
					destinationCommitIdentity = null,
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

	fun onPresentationEvent(event: ReaderPresentationEvent): ReaderControllerStep =
		ReaderPresentationControllerReducer.onPresentationEvent(this, event)

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
				val settlementReceipt = event.pageTurnSettlementReceiptOrNull()
				val reduction = ReaderProgressReducer.onRelocated(
					state = state,
					event = event,
					decision = decision,
					settlementReceipt = settlementReceipt
				)
				val shellCoverDismissed = state.shellCoverVisible && !reduction.state.shellCoverVisible
				val reducedController = copy(
					progressSaveGate = decision.state,
					state = reduction.state
				)
				val whispersyncStep = ReaderWhispersyncReducer.onDestinationChanged(
					step = ReaderWhispersyncReducer.onRelocated(
						controller = reducedController,
						event = event,
						settlementReceipt = settlementReceipt
					),
					destinationReplaced = reduction.state.destinationCommitIdentity !=
						state.destinationCommitIdentity
				)
				ReaderControllerStep(
					controller = whispersyncStep.controller,
					engineCommands = listOfNotNull(
						ReaderEngineCommand.RequestVisibleTextRange("shell-cover-dismissed")
							.takeIf {
								shellCoverDismissed &&
									reduction.state.supportsReaderEngineCapability(
										ReaderEngineCapability.MediaOverlay
									)
							}
					) + whispersyncStep.engineCommands,
					progressToSave = reduction.progressToSave,
					whispersyncAudioSeekTarget = whispersyncStep.whispersyncAudioSeekTarget,
					readaloudPlaybackCommand = whispersyncStep.readaloudPlaybackCommand,
					readaloudReaderInteraction = whispersyncStep.readaloudReaderInteraction
				)
			}
			is ReaderEngineEvent.TocItemChanged -> ReaderControllerStep(
				copy(
					state = state.copy(
						chrome = state.chrome.onTocItemChanged(event.title)
					)
				)
			)
			is ReaderEngineEvent.PaginationProfileStatusChanged ->
				ReaderWhispersyncReducer.onPaginationProfileStatusChanged(this, event)
			is ReaderEngineEvent.SettingsPresentationCommitted ->
				ReaderWhispersyncReducer.onSettingsPresentationCommitted(this, event)
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
			is ReaderEngineEvent.RawTextProvenanceStatusChanged ->
				ReaderWhispersyncReducer.onRawTextProvenanceStatusChanged(this, event)
			is ReaderEngineEvent.WhispersyncCanonicalPreflightResult ->
				ReaderWhispersyncReducer.onCanonicalPreflightResult(this, event)
			is ReaderEngineEvent.VisibleTextRange ->
				ReaderWhispersyncReducer.onVisibleTextRange(this, event)
			is ReaderEngineEvent.TextPoint -> ReaderWhispersyncReducer.onTextPoint(this, event)
			is ReaderEngineEvent.WhispersyncCueMapRendered ->
				ReaderWhispersyncReducer.onCueMapRendered(this, event)
			is ReaderEngineEvent.WhispersyncCueMapSeekRequested ->
				ReaderWhispersyncReducer.onCueMapSeekRequested(this, event)
			is ReaderEngineEvent.WhispersyncCueMapHoldOutcome ->
				ReaderWhispersyncReducer.onCueMapHoldOutcome(this, event)
			is ReaderEngineEvent.SearchResults -> ReaderSearchReducer.onResults(this, event)
			is ReaderEngineEvent.Toc -> ReaderControllerStep(
				copy(state = state.copy(toc = event.items))
			)
			is ReaderEngineEvent.SelectionChanged -> ReaderSelectionReducer.onChanged(this, event).copy(
				readaloudReaderInteraction = event.href
					?.takeIf { it.isNotBlank() }
					?.let(ReaderReadaloudReaderInteraction::ExplicitSelection)
			)
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

	fun navigateTo(locator: ReaderLocator): ReaderControllerStep {
		val controller = ReaderWhispersyncReducer.reserveUserNavigation(
			controller = this,
			requiresPageTurnSettlement = false
		)
		return ReaderControllerStep(
			controller = controller,
			engineCommands = listOf(
				ReaderEngineCommand.NavigateTo(
					locator = locator,
					causalSequence = controller.pendingWhispersyncCausalSequence()
				)
			),
			readaloudPlaybackCommand = state.whispersync.navigationPauseCommand()
		)
	}

	fun navigateToBookmark(bookmark: ReaderBookmark): ReaderControllerStep =
		navigateToSavedMark(bookmark.toLocator())

	fun navigateToAnnotation(annotation: ReaderAnnotation): ReaderControllerStep =
		navigateToSavedMark(annotation.toLocator())

	fun navigateToChapterPage(pageIndex: Int): ReaderControllerStep =
		ReaderProgressReducer.navigateToChapterPage(this, pageIndex)
			.withWhispersyncUserNavigation(
				pauseCommand = state.whispersync.navigationPauseCommand()
			)

	fun navigateToPreviousChapter(): ReaderControllerStep =
		navigateToAdjacentTocChapter(direction = -1)

	fun navigateToNextChapter(): ReaderControllerStep =
		navigateToAdjacentTocChapter(direction = 1)

	private fun navigateToAdjacentTocChapter(direction: Int): ReaderControllerStep =
		ReaderProgressReducer.navigateToAdjacentChapter(this, direction)
			.withWhispersyncUserNavigation(
				pauseCommand = state.whispersync.navigationPauseCommand()
			)

	private fun navigateToSavedMark(locator: ReaderLocator): ReaderControllerStep {
		val controller = ReaderWhispersyncReducer.reserveUserNavigation(
			controller = copy(
				state = state.copy(
					dialog = null,
					menuVisible = true
				)
			),
			requiresPageTurnSettlement = false
		)
		return ReaderControllerStep(
			controller = controller,
			engineCommands = listOf(
				ReaderEngineCommand.NavigateTo(
					locator = locator,
					causalSequence = controller.pendingWhispersyncCausalSequence()
				)
			),
			readaloudPlaybackCommand = state.whispersync.navigationPauseCommand()
		)
	}

	fun installRawTextProvenance(
		descriptor: ReaderRawTextProvenanceDescriptor,
		forceDispatch: Boolean = false
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
			engineCommands = listOf(
				ReaderEngineCommand.InstallRawTextProvenance(
					descriptor = descriptor,
					forceDispatch = forceDispatch
				)
			)
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

	fun onWhispersyncPlaybackCommand(
		command: ReaderReadaloudPlaybackCommand
	): ReaderControllerStep = ReaderWhispersyncReducer.onPlaybackCommand(this, command)

	fun loadWhispersyncSidecar(sidecar: WhispersyncSidecar): ReaderControllerStep {
		val currentWhispersync = state.whispersync
		if (
			sidecar.coordinateBasis != null &&
			currentWhispersync.sidecar?.coordinateBasis != null &&
			currentWhispersync.canonicalGenerationState != ReaderWhispersyncCanonicalGenerationState.Open &&
			currentWhispersync.sidecar.revisionDigest == sidecar.revisionDigest
		) {
			return ReaderControllerStep(this)
		}
		var installedController = this
		val installCommands = mutableListOf<ReaderEngineCommand>()
		val currentDescriptorsById = currentWhispersync.sidecar
			?.referencedRawTextProvenanceDescriptors()
			.orEmpty()
			.associateBy(ReaderRawTextProvenanceDescriptor::id)
		sidecar.referencedRawTextProvenanceDescriptors().forEach { descriptor ->
			val unchangedProof = currentDescriptorsById[descriptor.id] == descriptor
			val currentStatus = installedController.state.rawTextProvenanceById[descriptor.id]?.status
			if (unchangedProof && currentStatus == RawTextProvenanceStatus.Ready) {
				return@forEach
			}
			val install = installedController.installRawTextProvenance(
				descriptor = descriptor,
				forceDispatch = unchangedProof
			)
			installedController = install.controller
			installCommands += install.engineCommands
		}
		val loaded = ReaderWhispersyncReducer.loadSidecar(installedController, sidecar)
		return loaded.copy(engineCommands = installCommands + loaded.engineCommands)
	}

	fun toggleWhispersyncCueMap(): ReaderControllerStep =
		ReaderWhispersyncReducer.toggleCueMap(this)

	fun cancelWhispersyncCueMapHold(
		reason: ReaderWhispersyncCueMapHoldOutcome
	): ReaderControllerStep = ReaderWhispersyncReducer.cancelCueMapHold(this, reason)

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

	fun onViewerAction(
		action: ReaderViewerAction,
		legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext =
			ReaderLegacyLiveCompatibilityContext.Denied()
	): ReaderControllerStep = ReaderPresentationControllerReducer.onViewerAction(
		this,
		action,
		legacyLiveCompatibilityContext
	)

	fun onPageTurnBoundary(direction: ReaderPageTurnDirection): ReaderControllerStep =
		if (
			direction == ReaderPageTurnDirection.Previous &&
			state.canReturnToShellCover &&
			!state.shellCoverVisible &&
			!state.nativeShellCoverUrl.isNullOrBlank()
		) {
			ReaderOverlayReducer.showNativeShellCover(this)
		} else {
			ReaderWhispersyncReducer.reserveUserNavigation(this).let { controller ->
				ReaderControllerStep(
					controller = controller,
					engineCommands = listOf(
						ReaderEngineCommand.TurnPage(
							direction = direction,
							causalSequence = controller.pendingWhispersyncCausalSequence()
						)
					),
					readaloudPlaybackCommand = state.whispersync.navigationPauseCommand()
				)
			}
		}

	fun onBack(): ReaderControllerBackStep =
		ReaderOverlayReducer.onBack(this, includeMenu = true)

	fun onNavigateBack(): ReaderControllerBackStep =
		ReaderOverlayReducer.onBack(this, includeMenu = false)

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

}
