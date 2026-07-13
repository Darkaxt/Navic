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
	fun dispatch(action: ReaderController.() -> ReaderControllerStep): ReaderCoordinatorStep =
		applyControllerStep(controller.action())

	fun dispatchBack(action: ReaderController.() -> ReaderControllerBackStep): ReaderCoordinatorBackStep =
		applyControllerBackStep(controller.action())

	fun onEngineHostEvent(event: ReaderEngineHostEvent): ReaderCoordinatorStep {
		val engineEvent = controller.state.activeEngine
			?.let { format -> engineAdapters[format] }
			?.onHostEvent(event)
			?: return ReaderCoordinatorStep(this)
		return dispatch { onEngineEvent(engineEvent) }
	}

	fun onReadaloudEngineCommand(command: ReaderEngineCommand): ReaderCoordinatorStep =
		when (command) {
			is ReaderEngineCommand.ApplyMediaOverlay -> dispatch { applyMediaOverlay(command.fragment) }
			is ReaderEngineCommand.UpdateMediaOverlayProgress ->
				dispatch { updateMediaOverlayProgress(command.fragment) }
			ReaderEngineCommand.ClearMediaOverlay -> dispatch { clearMediaOverlay() }
			else -> ReaderCoordinatorStep(this)
		}

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
			?.takeIf { adapter -> adapter.supports(command) }
			?.let { adapter ->
				val adapterStep = adapter.onCommand(command)
				copy(
					engineAdapters = engineAdapters + (adapterStep.engine.format to adapterStep.engine),
					viewState = adapterStep.viewState
				)
			}
			?: this
}
