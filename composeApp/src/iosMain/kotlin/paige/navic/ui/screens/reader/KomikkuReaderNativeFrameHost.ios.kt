package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderLegacyLiveCompatibilityContext
import paige.navic.reader.ReaderPageDragPreviewPhase
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPendingPresentationEffect
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEffect
import paige.navic.reader.ReaderPresentationEffectIdentity
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderWhispersyncCueMapHoldOutcome
import paige.navic.reader.ReaderWhispersyncCueMapState

@Composable
actual fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	chromeOverlayVisible: Boolean,
	presentationDecision: ReaderPresentationDecision,
	presentationState: ReaderPresentationState,
	presentationVersion: ReaderPresentationReceiptVersion,
	presentationShellCoverVisible: Boolean,
	legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext,
	presentationEffects: List<ReaderPendingPresentationEffect>,
	onPresentationEffectHandled: (ReaderPresentationEffectIdentity) -> Unit,
	onPresentationEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?,
	destinationCommitIdentity: ReaderDestinationCommitIdentity?,
	shellCoverUrl: String?,
	shellCoverTitle: String,
	coverBackdropEnabled: Boolean,
	viewerKey: ReaderViewerKey,
	grayscaleEnabled: Boolean,
	invertedColors: Boolean,
	verticalPageDragPreview: Boolean,
	pageTurnCanvasEnabled: Boolean,
	pageTurnReadingDirection: String?,
	pageTurnBitmapQuality: String?,
	pageTurnSnapshotKey: Int,
	pageTurnContentReadyKey: String?,
	pageTurnPaginationStatus: String?,
	pageTurnVisualPageIndex: Int?,
	pageTurnVisualLocationReason: String?,
	pageTurnFoliateSessionId: String?,
	pageTurnSettlementAck: ReaderPageTurnSettlementAck?,
	whispersyncOverlayActive: Boolean,
	whispersyncAnchorReceipt: ReaderWhispersyncAnchorReceipt?,
	whispersyncHighlightColorArgb: Int,
	whispersyncCueMapState: ReaderWhispersyncCueMapState,
	onWhispersyncCueMapHoldOutcome: (Int, ReaderWhispersyncCueMapHoldOutcome) -> Unit,
	onWhispersyncCueMapSeekRequested: (Int) -> Unit,
	onStartupShellPrepared: () -> Unit,
	onViewerAction: (KomikkuNavigationRegion) -> ReaderPresentationEventReceipt?,
	onPageTurnBoundary: (ReaderPageTurnDirection) -> ReaderPresentationEventReceipt?,
	onReadableDragPreview: (deltaX: Float, deltaY: Float, viewWidth: Int, viewHeight: Int, phase: ReaderPageDragPreviewPhase) -> Unit,
	onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit,
	modifier: Modifier,
	viewerContent: @Composable () -> Unit,
	composeOverlay: @Composable () -> Unit
) {
	val handledPresentationEffects = remember {
		mutableSetOf<ReaderPresentationEffectIdentity>()
	}
	LaunchedEffect(presentationEffects) {
		presentationEffects.forEach { pending ->
			when (pending.effect) {
				is ReaderPresentationEffect.ReleaseStalePresentation -> if (
					pending.identity !in handledPresentationEffects
				) {
					// iOS owns no Android renderer deck, so this release is a proven no-op here.
					onPresentationEffectHandled(pending.identity)
					handledPresentationEffects += pending.identity
				}
				is ReaderPresentationEffect.RetryPreparation -> if (
					pending.identity !in handledPresentationEffects
				) {
					onPresentationEffectHandled(pending.identity)
					handledPresentationEffects += pending.identity
				}
			}
		}
	}
	Box(modifier = modifier) {
		viewerContent()
		composeOverlay()
	}
}
