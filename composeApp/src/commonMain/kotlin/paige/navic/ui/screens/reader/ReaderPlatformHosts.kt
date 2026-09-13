package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderCancelIntent
import paige.navic.reader.ReaderTransitionCapabilityKind
import paige.navic.reader.ReaderTransitionFact
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

internal enum class ReaderTransitionGatewayMode { Shadow }

internal fun interface ReaderTransitionGatewayRegistration {
	fun close()
}

internal class ReaderTransitionGateway {
	val mode: ReaderTransitionGatewayMode = ReaderTransitionGatewayMode.Shadow
	private var attachment: Attachment? = null
	private var nextAttachmentToken = 1L

	fun attachShadow(enqueue: (ReaderTransitionFact) -> Unit): ReaderTransitionGatewayRegistration {
		check(attachment == null) { "Reader transition shadow gateway is already attached" }
		val token = nextAttachmentToken++
		attachment = Attachment(token, enqueue)
		return ReaderTransitionGatewayRegistration {
			if (attachment?.token == token) attachment = null
		}
	}

	fun enqueue(fact: ReaderTransitionFact) {
		attachment?.enqueue?.invoke(fact)
	}

	fun observeLegacyPresentationEvent(event: ReaderPresentationEvent) {
		event.toShadowTransitionFactOrNull()?.let(::enqueue)
	}

	private data class Attachment(
		val token: Long,
		val enqueue: (ReaderTransitionFact) -> Unit
	)
}

internal val LocalReaderTransitionGateway = staticCompositionLocalOf<ReaderTransitionGateway?> { null }

private fun ReaderPresentationEvent.toShadowTransitionFactOrNull(): ReaderTransitionFact? = when (this) {
	is ReaderPresentationEvent.FoliateRelocated ->
		ReaderTransitionFact.FoliateDestinationCommitted(null, binding)
	ReaderPresentationEvent.Retry -> ReaderTransitionFact.Retry(null)
	ReaderPresentationEvent.Cancel -> ReaderTransitionFact.Intent(null, ReaderCancelIntent)
	is ReaderPresentationEvent.Lifecycle -> when (event) {
		ReaderPresentationLifecycleEvent.VisibilityLost -> ReaderTransitionFact.VisibilityChanged(null, false)
		ReaderPresentationLifecycleEvent.VisibilityRestored -> ReaderTransitionFact.VisibilityChanged(null, true)
		ReaderPresentationLifecycleEvent.RendererLost ->
			ReaderTransitionFact.ResourceLost(null, ReaderTransitionCapabilityKind.Renderer)
		ReaderPresentationLifecycleEvent.PublicationClosed -> ReaderTransitionFact.PublicationClosed(null)
		is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
		is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure -> null
	}
	is ReaderPresentationEvent.PublicationOpened,
	is ReaderPresentationEvent.BindingCompleted,
	is ReaderPresentationEvent.BindingReplaced,
	ReaderPresentationEvent.NativePageRequested,
	is ReaderPresentationEvent.ShellCoverRequested,
	is ReaderPresentationEvent.ShellCoverCommitted,
	ReaderPresentationEvent.ShellCoverDismissalRequested,
	is ReaderPresentationEvent.ShellCoverFailed,
	is ReaderPresentationEvent.ShellCoverEntered,
	is ReaderPresentationEvent.NativePagePresented,
	is ReaderPresentationEvent.CurlClaimed,
	is ReaderPresentationEvent.CurlTerminal,
	is ReaderPresentationEvent.WebViewHandoffRequested,
	is ReaderPresentationEvent.LiveEngineExposureCommitted,
	is ReaderPresentationEvent.LiveEngineExposureFailed,
	is ReaderPresentationEvent.LiveEngineHandoffTimedOut,
	is ReaderPresentationEvent.LiveEngineHandoffCancelled,
	is ReaderPresentationEvent.PreparationReported,
	is ReaderPresentationEvent.PreparationFailed,
	is ReaderPresentationEvent.TimedOut -> null
}

@Composable
expect fun KomikkuReaderNativeFrameHost(
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
