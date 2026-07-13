package paige.navic.reader

enum class ReaderAnnotationInteractionKind {
	Clicked,
	Drawn
}

data class ReaderAnnotationInteraction(
	val kind: ReaderAnnotationInteractionKind,
	val value: String? = null,
	val index: Int? = null,
	val rangeCfi: String? = null
)

data class ReaderAnnotationPopupState(
	val value: String? = null,
	val index: Int? = null,
	val rangeCfi: String? = null,
	val text: String? = null,
	val note: String? = null,
	val color: String? = null
) {
	val visible: Boolean
		get() = !value.isNullOrBlank() ||
			index != null ||
			!rangeCfi.isNullOrBlank() ||
			!text.isNullOrBlank() ||
			!note.isNullOrBlank()
}

internal object ReaderAnnotationReducer {
	fun onClicked(
		controller: ReaderController,
		event: ReaderEngineEvent.AnnotationClicked
	): ReaderControllerStep {
		val state = controller.state
		val value = event.value?.trim()?.takeIf { it.isNotEmpty() }
		val rangeCfi = event.rangeCfi?.trim()?.takeIf { it.isNotEmpty() }
		val savedAnnotation = state.savedAnnotationForClick(value = value, rangeCfi = rangeCfi)
		val popup = ReaderAnnotationPopupState(
			value = value,
			index = event.index,
			rangeCfi = rangeCfi,
			text = savedAnnotation?.text?.trim()?.takeIf { it.isNotEmpty() },
			note = savedAnnotation?.note?.trim()?.takeIf { it.isNotEmpty() },
			color = savedAnnotation?.color?.trim()?.takeIf { it.isNotEmpty() }
		).takeIf { it.visible }
		return ReaderControllerStep(
			controller.copy(
				state = state.copy(
					lastAnnotationInteraction = ReaderAnnotationInteraction(
						kind = ReaderAnnotationInteractionKind.Clicked,
						value = event.value,
						index = event.index,
						rangeCfi = event.rangeCfi
					),
					annotationPopup = popup
				)
			)
		)
	}

	fun onDrawn(
		controller: ReaderController,
		event: ReaderEngineEvent.AnnotationDrawn
	): ReaderControllerStep = ReaderControllerStep(
		controller.copy(
			state = controller.state.copy(
				lastAnnotationInteraction = ReaderAnnotationInteraction(
					kind = ReaderAnnotationInteractionKind.Drawn,
					value = event.value,
					index = event.index,
					rangeCfi = event.rangeCfi
				)
			)
		)
	)

	fun dismissPopup(controller: ReaderController): ReaderControllerStep =
		ReaderControllerStep(
			controller.copy(state = controller.state.copy(annotationPopup = null))
		)

	private fun ReaderControllerState.savedAnnotationForClick(
		value: String?,
		rangeCfi: String?
	): ReaderAnnotation? {
		val bookId = publication?.bookId
		return annotations.annotations.firstOrNull { annotation ->
			(bookId == null || annotation.bookId == bookId) &&
				(annotation.cfi == value || annotation.cfi == rangeCfi)
		}
	}
}
