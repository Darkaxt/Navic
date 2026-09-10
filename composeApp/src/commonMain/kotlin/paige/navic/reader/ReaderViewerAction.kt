package paige.navic.reader

sealed interface ReaderViewerAction {
	data object Menu : ReaderViewerAction

	data object NativeShellPrepared : ReaderViewerAction

	data class TurnPage(
		val direction: ReaderPageTurnDirection
	) : ReaderViewerAction

	data class PreviewPageDrag(
		val deltaX: Double,
		val deltaY: Double = 0.0,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null,
		val phase: ReaderPageDragPreviewPhase = ReaderPageDragPreviewPhase.Update
	) : ReaderViewerAction {
		override fun toString(): String = "PreviewPageDrag(phase=$phase)"
	}

	data class ScrollViewport(
		val direction: ReaderViewportScrollDirection
	) : ReaderViewerAction

	data class NavigateTo(
		val locator: ReaderLocator
	) : ReaderViewerAction {
		override fun toString(): String = "NavigateTo"
	}

	data class ContentLongPressAt(
		val x: Double,
		val y: Double,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null
	) : ReaderViewerAction {
		override fun toString(): String = "ContentLongPressAt"
	}
}
