package paige.navic.reader

// Existing behavior tests use action-shaped helpers so they remain focused on reader semantics.
internal fun ReaderCoordinator.open(request: ReaderEngineOpenRequest) = dispatch { open(request) }
internal fun ReaderCoordinator.onViewerAction(action: ReaderViewerAction) = dispatch { onViewerAction(action) }
internal fun ReaderCoordinator.onBack() = dispatchBack { onBack() }
internal fun ReaderCoordinator.onNavigateBack() = dispatchBack { onNavigateBack() }
internal fun ReaderCoordinator.onEngineEvent(event: ReaderEngineEvent) = dispatch { onEngineEvent(event) }
internal fun ReaderCoordinator.search(query: String) = dispatch { search(query) }
internal fun ReaderCoordinator.updateSearchInput(query: String) = dispatch { updateSearchInput(query) }
internal fun ReaderCoordinator.clearSearch() = dispatch { clearSearch() }
internal fun ReaderCoordinator.navigateToSearchResult(result: ReaderSearchResult) =
	dispatch { navigateToSearchResult(result) }
internal fun ReaderCoordinator.navigateToBookmark(bookmark: ReaderBookmark) =
	dispatch { navigateToBookmark(bookmark) }
internal fun ReaderCoordinator.navigateToAnnotation(annotation: ReaderAnnotation) =
	dispatch { navigateToAnnotation(annotation) }
internal fun ReaderCoordinator.navigateTo(locator: ReaderLocator) = dispatch { navigateTo(locator) }
internal fun ReaderCoordinator.navigateToChapterPage(pageIndex: Int) =
	dispatch { navigateToChapterPage(pageIndex) }
internal fun ReaderCoordinator.navigateToPreviousChapter() = dispatch { navigateToPreviousChapter() }
internal fun ReaderCoordinator.navigateToNextChapter() = dispatch { navigateToNextChapter() }
internal fun ReaderCoordinator.applyMediaOverlay(fragment: ReaderOverlayFragment) =
	dispatch { applyMediaOverlay(fragment) }
internal fun ReaderCoordinator.updateMediaOverlayProgress(fragment: ReaderOverlayFragment) =
	dispatch { updateMediaOverlayProgress(fragment) }
internal fun ReaderCoordinator.onReadaloudPlaybackState(playbackState: ReaderReadaloudPlaybackUiState) =
	dispatch { onReadaloudPlaybackState(playbackState) }
internal fun ReaderCoordinator.loadWhispersyncSidecar(sidecar: WhispersyncSidecar) =
	dispatch { loadWhispersyncSidecar(sidecar) }
internal fun ReaderCoordinator.reportWhispersyncLoadFailure(
	message: ReaderWhispersyncStatusMessage,
	detail: String? = null
) = dispatch { reportWhispersyncLoadFailure(message, detail) }
internal fun ReaderCoordinator.repairWhispersyncMismatch() = dispatch { repairWhispersyncMismatch() }
internal fun ReaderCoordinator.openWhispersyncPlayerDialog() = dispatch { openWhispersyncPlayerDialog() }
internal fun ReaderCoordinator.addSelectionHighlight(color: String = DefaultReaderHighlightColor) =
	dispatch { addSelectionHighlight(color) }
internal fun ReaderCoordinator.startSelectionNote() = dispatch { startSelectionNote() }
internal fun ReaderCoordinator.saveSelectionNote(note: String) = dispatch { saveSelectionNote(note) }
internal fun ReaderCoordinator.dismissSelectionActions() = dispatch { dismissSelectionActions() }
internal fun ReaderCoordinator.dismissSelectionNote() = dispatch { dismissSelectionNote() }
internal fun ReaderCoordinator.updateSelectionNoteDraft(note: String) =
	dispatch { updateSelectionNoteDraft(note) }
internal fun ReaderCoordinator.restoreProcessState(snapshot: ReaderProcessStateSnapshot) =
	dispatch { restoreProcessState(snapshot) }
internal fun ReaderCoordinator.dismissAnnotationPopup() = dispatch { dismissAnnotationPopup() }
internal fun ReaderCoordinator.dismissFootnotePopup() = dispatch { dismissFootnotePopup() }
internal fun ReaderCoordinator.dismissExternalLinkPrompt() = dispatch { dismissExternalLinkPrompt() }
internal fun ReaderCoordinator.toggleCurrentBookmark() = dispatch { toggleCurrentBookmark() }
internal fun ReaderCoordinator.clearMediaOverlay(fragmentId: String? = null) =
	dispatch { clearMediaOverlay(fragmentId) }
internal fun ReaderCoordinator.applySettings(settings: ReaderSettings) = dispatch { applySettings(settings) }
internal fun ReaderCoordinator.openContentsDialog() = dispatch { openContentsDialog() }
internal fun ReaderCoordinator.openSearchDialog() = dispatch { openSearchDialog() }
internal fun ReaderCoordinator.openSettingsDialog() = dispatch { openSettingsDialog() }
internal fun ReaderCoordinator.showMenus() = dispatch { showMenus() }
internal fun ReaderCoordinator.hideMenus() = dispatch { hideMenus() }
internal fun ReaderCoordinator.closeDialog() = dispatch { closeDialog() }
internal fun ReaderCoordinator.closeSearchDialog() = dispatch { closeSearchDialog() }
