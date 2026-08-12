package paige.navic.reader

data class ReaderSelection(
	val text: String? = null,
	val cfi: String? = null,
	val href: String? = null,
	val footnote: Boolean? = null,
	val contextText: String? = null,
	val posLeft: Double? = null,
	val posTop: Double? = null,
	val posRight: Double? = null,
	val posBottom: Double? = null
)

data class ReaderSelectionActionState(
	val selectedText: String? = null,
	val selectedCfi: String? = null,
	val selectedHref: String? = null,
	val canCopy: Boolean = false,
	val canHighlight: Boolean = false,
	val canNote: Boolean = false
) {
	val visible: Boolean
		get() = canCopy || canHighlight || canNote
}

data class ReaderSelectionNoteDraft(
	val bookId: String,
	val bookTitle: String,
	val text: String,
	val cfi: String,
	val href: String? = null,
	val sectionTitle: String? = null,
	val note: String = ""
)

internal fun String?.normalizedReaderSelectionValue(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

internal fun ReaderControllerState.whispersyncOwnsTextSelection(): Boolean =
	whispersync.available &&
		chrome.readaloudPlayback.isAvailable &&
		chrome.readaloudPlayback.syncEnabled

internal object ReaderSelectionReducer {
	fun onChanged(
		controller: ReaderController,
		event: ReaderEngineEvent.SelectionChanged
	): ReaderControllerStep {
		val state = controller.state
		if (state.whispersyncOwnsTextSelection()) {
			return ReaderControllerStep(
				controller.copy(state = state.copy(selection = null, selectionNoteDraft = null))
			)
		}
		return ReaderControllerStep(
			controller.copy(
				state = state.copy(
					selection = ReaderSelection(
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
				)
			)
		)
	}

	fun clear(controller: ReaderController): ReaderControllerStep =
		ReaderControllerStep(
			controller.copy(
				state = controller.state.copy(selection = null, selectionNoteDraft = null)
			)
		)

	fun addHighlight(controller: ReaderController, color: String): ReaderControllerStep {
		val state = controller.state
		val publication = state.publication ?: return ReaderControllerStep(controller)
		val selection = state.selection ?: return ReaderControllerStep(controller)
		val nextAnnotations = state.annotations.addSelectionHighlight(
			bookId = publication.bookId,
			bookTitle = publication.title,
			selectionText = selection.text,
			selectionCfi = selection.cfi,
			selectionHref = selection.href,
			sectionTitle = state.chrome.currentSectionTitle,
			color = color
		)
		if (nextAnnotations == state.annotations) return ReaderControllerStep(controller)
		return ReaderControllerStep(
			controller = controller.copy(
				state = state.copy(annotations = nextAnnotations, selection = null)
			),
			engineCommands = listOf(
				ReaderEngineCommand.ApplyAnnotations(nextAnnotations.annotationsForBook(publication.bookId))
			)
		)
	}

	fun startNote(controller: ReaderController): ReaderControllerStep {
		val state = controller.state
		val publication = state.publication ?: return ReaderControllerStep(controller)
		val selectionActions = state.selectionActions
		val selectedText = selectionActions.selectedText ?: return ReaderControllerStep(controller)
		val selectedCfi = selectionActions.selectedCfi ?: return ReaderControllerStep(controller)
		return ReaderControllerStep(
			controller.copy(
				state = state.copy(
					selection = null,
					selectionNoteDraft = ReaderSelectionNoteDraft(
						bookId = publication.bookId,
						bookTitle = publication.title,
						text = selectedText,
						cfi = selectedCfi,
						href = selectionActions.selectedHref,
						sectionTitle = state.chrome.currentSectionTitle?.trim()?.takeIf { it.isNotEmpty() }
					)
				)
			)
		)
	}

	fun saveNote(controller: ReaderController, note: String): ReaderControllerStep {
		val state = controller.state
		val publication = state.publication ?: return ReaderControllerStep(controller)
		val draft = state.selectionNoteDraft ?: return ReaderControllerStep(controller)
		val nextAnnotations = state.annotations.addSelectionNote(draft = draft, note = note)
		if (nextAnnotations == state.annotations) return ReaderControllerStep(controller)
		return ReaderControllerStep(
			controller = controller.copy(
				state = state.copy(
					annotations = nextAnnotations,
					selection = null,
					selectionNoteDraft = null
				)
			),
			engineCommands = listOf(
				ReaderEngineCommand.ApplyAnnotations(nextAnnotations.annotationsForBook(publication.bookId))
			)
		)
	}

	fun dismissActions(controller: ReaderController): ReaderControllerStep =
		ReaderControllerStep(controller.copy(state = controller.state.copy(selection = null)))

	fun dismissNote(controller: ReaderController): ReaderControllerStep =
		ReaderControllerStep(controller.copy(state = controller.state.copy(selectionNoteDraft = null)))

	fun updateNoteDraft(controller: ReaderController, note: String): ReaderControllerStep {
		val draft = controller.state.selectionNoteDraft ?: return ReaderControllerStep(controller)
		if (draft.note == note) return ReaderControllerStep(controller)
		return ReaderControllerStep(
			controller.copy(
				state = controller.state.copy(selectionNoteDraft = draft.copy(note = note))
			)
		)
	}
}
