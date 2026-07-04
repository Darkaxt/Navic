package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress

data class ReaderCoordinatorStep(
	val coordinator: ReaderCoordinator,
	val progressToSave: BinderyReadingProgress? = null,
	val whispersyncAudioSeekTarget: WhispersyncAudioSeekTarget? = null,
	val readaloudPlaybackCommand: ReaderReadaloudPlaybackCommand? = null
)

data class ReaderCoordinatorBackStep(
	val coordinator: ReaderCoordinator,
	val handled: Boolean,
	val readaloudPlaybackCommand: ReaderReadaloudPlaybackCommand? = null
)

data class ReaderCoordinator(
	val controller: ReaderController = ReaderController(),
	val engineAdapters: Map<ReaderPublicationFormat, ReaderEngine> = mapOf(
		ReaderPublicationFormat.Epub to FoliateEpubEngineAdapter(),
		ReaderPublicationFormat.Pdf to FoliatePdfEngineAdapter(),
		ReaderPublicationFormat.Azw3 to FoliatePublicationEngineAdapter(ReaderPublicationFormat.Azw3),
		ReaderPublicationFormat.Mobi to FoliatePublicationEngineAdapter(ReaderPublicationFormat.Mobi),
		ReaderPublicationFormat.Cbz to FoliatePublicationEngineAdapter(ReaderPublicationFormat.Cbz),
		ReaderPublicationFormat.Fb2 to FoliatePublicationEngineAdapter(ReaderPublicationFormat.Fb2)
	),
	val viewState: ReaderEngineViewState = ReaderEngineViewState.Empty
) {
	fun open(request: ReaderEngineOpenRequest): ReaderCoordinatorStep =
		applyControllerStep(controller.open(request))

	fun onViewerAction(action: ReaderViewerAction): ReaderCoordinatorStep =
		applyControllerStep(controller.onViewerAction(action))

	fun onBack(): ReaderCoordinatorBackStep =
		applyControllerBackStep(controller.onBack())

	fun onNavigateBack(): ReaderCoordinatorBackStep =
		applyControllerBackStep(controller.onNavigateBack())

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

	fun navigateToBookmark(bookmark: ReaderBookmark): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToBookmark(bookmark))

	fun navigateToAnnotation(annotation: ReaderAnnotation): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToAnnotation(annotation))

	fun navigateTo(locator: ReaderLocator): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateTo(locator))

	fun navigateToChapterPage(pageIndex: Int): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToChapterPage(pageIndex))

	fun navigateToPreviousChapter(): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToPreviousChapter())

	fun navigateToNextChapter(): ReaderCoordinatorStep =
		applyControllerStep(controller.navigateToNextChapter())

	fun applyMediaOverlay(fragment: ReaderOverlayFragment): ReaderCoordinatorStep =
		applyControllerStep(controller.applyMediaOverlay(fragment))

	fun updateMediaOverlayProgress(fragment: ReaderOverlayFragment): ReaderCoordinatorStep =
		applyControllerStep(controller.updateMediaOverlayProgress(fragment))

	fun onReadaloudEngineCommand(command: ReaderEngineCommand): ReaderCoordinatorStep =
		when (command) {
			is ReaderEngineCommand.ApplyMediaOverlay -> applyMediaOverlay(command.fragment)
			is ReaderEngineCommand.UpdateMediaOverlayProgress -> updateMediaOverlayProgress(command.fragment)
			ReaderEngineCommand.ClearMediaOverlay -> clearMediaOverlay()
			else -> ReaderCoordinatorStep(this)
		}

	fun onReadaloudPlaybackState(playbackState: ReaderReadaloudPlaybackUiState): ReaderCoordinatorStep =
		applyControllerStep(controller.onReadaloudPlaybackState(playbackState))

	fun loadWhispersyncSidecar(sidecar: WhispersyncSidecar): ReaderCoordinatorStep =
		applyControllerStep(controller.loadWhispersyncSidecar(sidecar))

	fun reportWhispersyncLoadFailure(label: String, detail: String? = null): ReaderCoordinatorStep =
		applyControllerStep(controller.reportWhispersyncLoadFailure(label = label, detail = detail))

	fun repairWhispersyncMismatch(): ReaderCoordinatorStep =
		applyControllerStep(controller.repairWhispersyncMismatch())

	fun openWhispersyncPlayerDialog(): ReaderCoordinatorStep =
		applyControllerStep(controller.openWhispersyncPlayerDialog())

	fun addSelectionHighlight(color: String = DefaultReaderHighlightColor): ReaderCoordinatorStep =
		applyControllerStep(controller.addSelectionHighlight(color))

	fun startSelectionNote(): ReaderCoordinatorStep =
		applyControllerStep(controller.startSelectionNote())

	fun saveSelectionNote(note: String): ReaderCoordinatorStep =
		applyControllerStep(controller.saveSelectionNote(note))

	fun dismissSelectionActions(): ReaderCoordinatorStep =
		applyControllerStep(controller.dismissSelectionActions())

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
			progressToSave = step.progressToSave,
			whispersyncAudioSeekTarget = step.whispersyncAudioSeekTarget,
			readaloudPlaybackCommand = step.readaloudPlaybackCommand
		)
	}

	private fun applyControllerBackStep(step: ReaderControllerBackStep): ReaderCoordinatorBackStep {
		var next = copy(controller = step.controller)
		step.engineCommands.forEach { command ->
			next = next.applyEngineCommand(command)
		}
		return ReaderCoordinatorBackStep(
			coordinator = next,
			handled = step.handled,
			readaloudPlaybackCommand = step.readaloudPlaybackCommand
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
