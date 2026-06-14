package paige.navic.reader

sealed interface ReaderViewerAction {
	data object Menu : ReaderViewerAction

	data class TurnPage(
		val direction: ReaderPageTurnDirection
	) : ReaderViewerAction

	data class ScrollViewport(
		val direction: ReaderViewportScrollDirection
	) : ReaderViewerAction
}
