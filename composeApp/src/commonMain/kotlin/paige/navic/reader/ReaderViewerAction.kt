package paige.navic.reader

sealed interface ReaderViewerAction {
	data object Menu : ReaderViewerAction

	data object NativeShellPrepared : ReaderViewerAction

	/** [withAudio]: the read-along transport's Prev/Next, which carry the narration to the new page. */
	data class TurnPage(
		val direction: ReaderPageTurnDirection,
		val withAudio: Boolean = false
	) : ReaderViewerAction

	data class PreviewPageDrag(
		val deltaX: Double,
		val deltaY: Double = 0.0,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null,
		val phase: ReaderPageDragPreviewPhase = ReaderPageDragPreviewPhase.Update
	) : ReaderViewerAction

	data class ScrollViewport(
		val direction: ReaderViewportScrollDirection
	) : ReaderViewerAction

	data class NavigateTo(
		val locator: ReaderLocator
	) : ReaderViewerAction

	data class ContentLongPressAt(
		val x: Double,
		val y: Double,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null
	) : ReaderViewerAction
}
