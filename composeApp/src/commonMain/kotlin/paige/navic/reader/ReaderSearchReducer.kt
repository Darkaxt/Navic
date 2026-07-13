package paige.navic.reader

data class ReaderSearchState(
	val query: String = "",
	val results: List<ReaderSearchResult> = emptyList(),
	val active: Boolean = false,
	val progress: Double? = null,
	val complete: Boolean = false
) {
	val searching: Boolean
		get() = active && !complete
}
internal object ReaderSearchReducer {
	fun search(controller: ReaderController, query: String): ReaderControllerStep {
		if (!controller.state.supportsReaderEngineCapability(ReaderEngineCapability.Search)) {
			return ReaderControllerStep(controller)
		}
		val normalized = query.trim()
		if (normalized.isBlank()) return clear(controller)
		return ReaderControllerStep(
			controller = controller.copy(
				state = controller.state.copy(
					search = ReaderSearchState(
						query = normalized,
						active = true,
						progress = 0.0
					)
				)
			),
			engineCommands = listOf(ReaderEngineCommand.Search(normalized))
		)
	}

	fun updateInput(controller: ReaderController, query: String): ReaderControllerStep {
		if (!controller.state.supportsReaderEngineCapability(ReaderEngineCapability.Search)) {
			return ReaderControllerStep(controller)
		}
		if (controller.state.search.query == query && !controller.state.search.active) {
			return ReaderControllerStep(controller)
		}
		return ReaderControllerStep(
			controller.copy(state = controller.state.copy(search = ReaderSearchState(query = query)))
		)
	}

	fun clear(controller: ReaderController): ReaderControllerStep =
		if (!controller.state.supportsReaderEngineCapability(ReaderEngineCapability.Search)) {
			ReaderControllerStep(controller)
		} else {
			ReaderControllerStep(
				controller = controller.copy(state = controller.state.copy(search = ReaderSearchState())),
				engineCommands = listOf(ReaderEngineCommand.ClearSearch)
			)
		}

	fun onResults(controller: ReaderController, event: ReaderEngineEvent.SearchResults): ReaderControllerStep =
		ReaderControllerStep(
			controller.copy(
				state = controller.state.copy(
					search = ReaderSearchState(
						query = event.query,
						results = event.results,
						active = event.query.isNotBlank(),
						progress = event.progress,
						complete = event.complete
					)
				)
			)
		)
}
