package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress

data class ReaderCoordinatorStep(
	val coordinator: ReaderCoordinator,
	val progressToSave: BinderyReadingProgress? = null
)

data class ReaderCoordinator(
	val controller: ReaderController = ReaderController(),
	val engineAdapters: Map<ReaderPublicationFormat, ReaderEngine> = mapOf(
		ReaderPublicationFormat.Epub to FoliateEpubEngineAdapter(),
		ReaderPublicationFormat.Pdf to FoliatePdfEngineAdapter()
	),
	val viewState: ReaderEngineViewState = ReaderEngineViewState.Empty
) {
	fun open(request: ReaderEngineOpenRequest): ReaderCoordinatorStep =
		applyControllerStep(controller.open(request))

	fun onViewerAction(action: ReaderViewerAction): ReaderCoordinatorStep =
		applyControllerStep(controller.onViewerAction(action))

	fun onEngineEvent(event: ReaderEngineEvent): ReaderCoordinatorStep =
		applyControllerStep(controller.onEngineEvent(event))

	fun onEngineHostEvent(event: ReaderEngineHostEvent): ReaderCoordinatorStep {
		val engineEvent = controller.state.activeEngine
			?.let { format -> engineAdapters[format] }
			?.onHostEvent(event)
			?: return ReaderCoordinatorStep(this)
		return onEngineEvent(engineEvent)
	}

	fun search(query: String): ReaderCoordinatorStep =
		applyControllerStep(controller.search(query))

	fun clearSearch(): ReaderCoordinatorStep =
		applyControllerStep(controller.clearSearch())

	fun navigateToSearchResult(result: ReaderSearchResult): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToSearchResult(result))

	fun navigateTo(locator: ReaderLocator): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateTo(locator))

	fun navigateToChapterPage(pageIndex: Int): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToChapterPage(pageIndex))

	fun navigateToPreviousChapter(): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToPreviousChapter())

	fun navigateToNextChapter(): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToNextChapter())

	fun navigateHistoryBack(): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateHistoryBack())

	fun navigateHistoryForward(): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateHistoryForward())

	fun dismissHistoryNavigation(): ReaderCoordinatorStep =
		applyControllerStep(controller.dismissHistoryNavigation())

	fun applyMediaOverlay(fragment: ReaderOverlayFragment): ReaderCoordinatorStep =
		applyControllerStep(controller.applyMediaOverlay(fragment))

	fun onReadaloudEngineCommand(command: ReaderEngineCommand): ReaderCoordinatorStep =
		when (command) {
			is ReaderEngineCommand.ApplyMediaOverlay -> applyMediaOverlay(command.fragment)
			ReaderEngineCommand.ClearMediaOverlay -> clearMediaOverlay()
			else -> ReaderCoordinatorStep(this)
		}

	fun onReadaloudPlaybackState(playbackState: ReaderReadaloudPlaybackUiState): ReaderCoordinatorStep =
		applyControllerStep(controller.onReadaloudPlaybackState(playbackState))

	fun addSelectionHighlight(color: String = DefaultReaderHighlightColor): ReaderCoordinatorStep =
		applyControllerStep(controller.addSelectionHighlight(color))

	fun startSelectionNote(): ReaderCoordinatorStep =
		applyControllerStep(controller.startSelectionNote())

	fun saveSelectionNote(note: String): ReaderCoordinatorStep =
		applyControllerStep(controller.saveSelectionNote(note))

	fun dismissSelectionNote(): ReaderCoordinatorStep =
		applyControllerStep(controller.dismissSelectionNote())

	fun dismissAnnotationPopup(): ReaderCoordinatorStep =
		applyControllerStep(controller.dismissAnnotationPopup())

	fun dismissFootnotePopup(): ReaderCoordinatorStep =
		applyControllerStep(controller.dismissFootnotePopup())

	fun dismissExternalLinkPrompt(): ReaderCoordinatorStep =
		applyControllerStep(controller.dismissExternalLinkPrompt())

	fun toggleCurrentBookmark(): ReaderCoordinatorStep =
		applyControllerStep(controller.toggleCurrentBookmark())

	fun clearMediaOverlay(fragmentId: String? = null): ReaderCoordinatorStep =
		applyControllerStep(controller.clearMediaOverlay(fragmentId))

	fun applySettings(settings: ReaderSettings): ReaderCoordinatorStep =
		applyControllerStep(controller.applySettings(settings))

	fun openContentsDialog(): ReaderCoordinatorStep =
		applyControllerStep(controller.openContentsDialog())

	fun openReadingModeDialog(): ReaderCoordinatorStep =
		applyControllerStep(controller.openReadingModeDialog())

	fun openSearchDialog(): ReaderCoordinatorStep =
		applyControllerStep(controller.openSearchDialog())

	fun openSettingsDialog(): ReaderCoordinatorStep =
		applyControllerStep(controller.openSettingsDialog())

	fun showMenus(): ReaderCoordinatorStep =
		applyControllerStep(controller.showMenus())

	fun hideMenus(): ReaderCoordinatorStep =
		applyControllerStep(controller.hideMenus())

	fun closeDialog(): ReaderCoordinatorStep =
		applyControllerStep(controller.closeDialog())

	fun closeSearchDialog(): ReaderCoordinatorStep =
		applyControllerStep(controller.closeSearchDialog())

	private fun applyControllerStep(step: ReaderControllerStep): ReaderCoordinatorStep {
		var next = copy(controller = step.controller)
		step.engineCommands.forEach { command ->
			next = next.applyEngineCommand(command)
		}
		return ReaderCoordinatorStep(
			coordinator = next,
			progressToSave = step.progressToSave
		)
	}

	private fun applyEngineCommand(command: ReaderEngineCommand): ReaderCoordinator =
		controller.state.activeEngine
			?.let { format -> engineAdapters[format] }
			?.let { adapter ->
				val adapterStep = adapter.onCommand(command)
				copy(
					engineAdapters = engineAdapters + (adapterStep.engine.format to adapterStep.engine),
					viewState = adapterStep.viewState
				)
			}
			?: this
}
