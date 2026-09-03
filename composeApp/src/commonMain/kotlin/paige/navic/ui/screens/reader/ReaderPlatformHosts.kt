package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderEngineHostCommand
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderLegacyLiveCompatibilityContext
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderPageDragPreviewPhase
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPendingPresentationEffect
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEffectIdentity
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderRawTextProvenanceDescriptor
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderReadaloudReaderInteraction
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderWhispersyncCueMapHoldOutcome
import paige.navic.reader.ReaderWhispersyncCueMapState
import paige.navic.reader.WordSyncPublicationVerifier
import paige.navic.ui.navigation.Screen

@Composable
expect fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	chromeOverlayVisible: Boolean,
	presentationDecision: ReaderPresentationDecision,
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
	modifier: Modifier = Modifier,
	viewerContent: @Composable () -> Unit,
	composeOverlay: @Composable () -> Unit
)

@Composable
expect fun ReaderEngineWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
	externalShellCover: Boolean,
	suppressWebShellCover: Boolean,
	nativeShellCoverTint: String?,
	settings: ReaderSettings,
	startCfi: String?,
	startHref: String?,
	startProgress: Double?,
	rawTextProvenanceDescriptors: List<ReaderRawTextProvenanceDescriptor> = emptyList(),
	command: ReaderEngineHostCommand? = null,
	commandKey: Long = 0L,
	onEvent: (ReaderEngineHostEvent) -> Unit,
	modifier: Modifier = Modifier
)

@Composable
expect fun ReaderOrientationEffect(orientation: String?)

@Composable
expect fun ReaderSystemBarsEffect(
	fullscreen: Boolean,
	systemBarsVisible: Boolean
)

@Composable
expect fun KomikkuAdaptiveSheet(
	onDismissRequest: () -> Unit,
	dimAmount: Float = 0.5f,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
)

@Composable
@ReadOnlyComposable
expect fun komikkuReaderIsTabletUi(): Boolean

@Composable
expect fun ReaderPublicationRuntimeHost(
	reader: Screen.Reader,
	onPublicationReady: (
		String,
		String?,
		String?,
		BinderyReadingProgress?,
		WordSyncPublicationVerifier?
	) -> Unit,
	onError: (String) -> Unit
)

@Composable
expect fun ReaderReadaloudRuntimeHost(
	reader: Screen.Reader,
	readaloudSyncEnabled: Boolean,
	readerInteraction: ReaderReadaloudReaderInteraction?,
	readerInteractionKey: Long,
	onPublicationReady: (String) -> Unit,
	onEngineCommand: (ReaderEngineCommand, Long) -> Unit,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit
)
