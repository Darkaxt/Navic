package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import paige.navic.reader.ReaderPageDragPreviewPhase

@Composable
actual fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	chromeOverlayVisible: Boolean,
	shellCoverVisible: Boolean,
	shellCoverUrl: String?,
	shellCoverTitle: String,
	coverBackdropEnabled: Boolean,
	viewerKey: ReaderViewerKey,
	grayscaleEnabled: Boolean,
	invertedColors: Boolean,
	verticalPageDragPreview: Boolean,
	pageTurnCanvasEnabled: Boolean,
	pageTurnSnapshotKey: Int,
	pageTurnVisualPageIndex: Int?,
	onViewerAction: (KomikkuNavigationRegion) -> Unit,
	onReadableDragPreview: (deltaX: Float, deltaY: Float, viewWidth: Int, viewHeight: Int, phase: ReaderPageDragPreviewPhase) -> Unit,
	onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit,
	modifier: Modifier,
	viewerContent: @Composable () -> Unit,
	composeOverlay: @Composable () -> Unit
) {
	Box(modifier = modifier) {
		viewerContent()
		composeOverlay()
	}
}
