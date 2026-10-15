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
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderCancelIntent
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderTransitionCapabilityKind
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionGestureId
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.readerTransitionIntentForViewerAction
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderRawTextProvenanceDescriptor
import paige.navic.reader.ReaderSemanticExecutableRequest
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderSemanticSynchronizationIntent
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderReadaloudReaderInteraction
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderWhispersyncCueMapHoldOutcome
import paige.navic.reader.ReaderWhispersyncCueMapState
import paige.navic.reader.WordSyncPublicationVerifier
import paige.navic.ui.navigation.Screen

internal enum class ReaderTransitionGatewayMode { Shadow, Preparing, Activated }

internal fun interface ReaderTransitionGatewayRegistration {
	fun close()
}

internal class ReaderTransitionGatewaySemanticEpoch internal constructor(internal val attachmentToken: Long)

internal class ReaderTransitionGateway {
	private var explicitMode = ReaderTransitionGatewayMode.Shadow
	private var attachment: Attachment? = null
	private var nextAttachmentToken = 1L
	private var nextObservedSemanticRequestHandle = 1L
	private var retiredNormalSessionGeneration = 0L

	internal fun admitsNormalSession(readerSessionGeneration: Long): Boolean =
		readerSessionGeneration > retiredNormalSessionGeneration

	/** Normal composition disposal is terminal for that session even when a queued
	 * ComposeView layout recreates the outer composition with the same model. This
	 * does not change reversible pre-install protocol cancellation. */
	internal fun retireNormalProductionCandidate(candidate: ReaderTransitionGatewayCandidate) {
		if (candidate.gateway !== this || !isSelectedCandidate(candidate)) return
		retiredNormalSessionGeneration = maxOf(retiredNormalSessionGeneration, candidate.readerSessionGeneration)
		if (!candidate.installationSource.hasTransferredCandidate(candidate.epoch)) {
			explicitMode = ReaderTransitionGatewayMode.Preparing
		}
		candidate.close()
	}

	val mode: ReaderTransitionGatewayMode
		get() {
			val candidate = attachment?.candidate ?: return explicitMode
			return if (candidate.installationSource.ownsInstalledCandidate(
				candidate.epoch, candidate.readerSessionGeneration, candidate.coordinatorEpoch
			)) ReaderTransitionGatewayMode.Activated else ReaderTransitionGatewayMode.Preparing
		}

	internal fun reserveActivatedCandidate(
		readerSessionGeneration: Long,
		coordinatorEpoch: Long,
		installationSource: ReaderTransitionGatewayInstallationSource,
		enqueue: (ReaderTransitionFact) -> Unit,
		enqueueReceipt: (ReaderPresentationEventReceipt) -> Unit,
		registerSemanticRequest: (ReaderSemanticExecutableRequest) -> ReaderSemanticRequestHandle,
		discardSemanticReservation: () -> Unit,
		retireSemanticRequests: () -> Unit
	): ReaderTransitionGatewayCandidate? {
		if (attachment != null || !installationSource.awaitingInstallation ||
			readerSessionGeneration <= 0L || coordinatorEpoch <= 0L || nextAttachmentToken == Long.MAX_VALUE
		) return null
		val token = nextAttachmentToken++
		val epoch = ReaderTransitionGatewaySemanticEpoch(token)
		val candidate = ReaderTransitionGatewayCandidate(
			this, epoch, readerSessionGeneration, coordinatorEpoch, installationSource
		) {
			val owned = attachment?.takeIf { it.token == token && it.candidate?.epoch === epoch }
				?: return@ReaderTransitionGatewayCandidate
			val transferred = installationSource.hasTransferredCandidate(epoch)
			// Fence this exact attachment before any external retirement or terminal callback.
			attachment = null
			if (transferred) explicitMode = ReaderTransitionGatewayMode.Activated
			try {
				if (transferred) owned.enqueue(ReaderTransitionFact.PublicationClosed(null))
			} finally {
				try { discardSemanticReservation() }
				finally { if (transferred) retireSemanticRequests() }
			}
		}
		attachment = Attachment(token, enqueue, enqueueReceipt, registerSemanticRequest, candidate)
		return candidate
	}

	internal fun isSelectedCandidate(candidate: ReaderTransitionGatewayCandidate): Boolean =
		attachment?.candidate === candidate

	internal fun captureSelectedSemanticEpoch(): ReaderTransitionGatewaySemanticEpoch? =
		attachment?.candidate?.epoch ?: captureActivatedSemanticEpoch()

	internal fun captureActivatedSemanticEpoch(): ReaderTransitionGatewaySemanticEpoch? =
		attachment?.takeIf { mode == ReaderTransitionGatewayMode.Activated && it.registerSemanticRequest != null }
			?.let { it.candidate?.epoch ?: ReaderTransitionGatewaySemanticEpoch(it.token) }
	internal fun isCurrentActivatedSemanticEpoch(epoch: ReaderTransitionGatewaySemanticEpoch): Boolean =
		mode == ReaderTransitionGatewayMode.Activated && attachment?.token == epoch.attachmentToken &&
			attachment?.registerSemanticRequest != null

	fun observedSemanticRequestHandle(): ReaderSemanticRequestHandle {
		check(mode == ReaderTransitionGatewayMode.Shadow) {
			"Activated semantic requests require executable registry admission"
		}
		val value = nextObservedSemanticRequestHandle
		check(value < Long.MAX_VALUE) { "Reader semantic request handle sequence exhausted" }
		nextObservedSemanticRequestHandle += 1L
		return ReaderSemanticRequestHandle(value)
	}

	fun attachShadow(
		enqueue: (ReaderTransitionFact) -> Unit,
		enqueueReceipt: (ReaderPresentationEventReceipt) -> Unit = {}
	): ReaderTransitionGatewayRegistration {
		check(mode == ReaderTransitionGatewayMode.Shadow) {
			"A selected reader transition gateway cannot reopen legacy routing"
		}
		check(attachment == null) { "Reader transition shadow gateway is already attached" }
		val token = nextAttachmentToken++
		attachment = Attachment(token, enqueue, enqueueReceipt, null)
		return ReaderTransitionGatewayRegistration {
			if (attachment?.token == token) attachment = null
		}
	}

	fun attachActivated(
		enqueue: (ReaderTransitionFact) -> Unit,
		enqueueReceipt: (ReaderPresentationEventReceipt) -> Unit = {},
		retireSemanticRequests: () -> Unit = {},
		registerSemanticRequest: (ReaderSemanticExecutableRequest) -> ReaderSemanticRequestHandle
	): ReaderTransitionGatewayRegistration {
		check(attachment == null) { "Reader transition gateway is already attached" }
		val token = nextAttachmentToken++
		val candidate = Attachment(token, enqueue, enqueueReceipt, registerSemanticRequest)
		attachment = candidate
		explicitMode = ReaderTransitionGatewayMode.Activated
		return ReaderTransitionGatewayRegistration {
			if (attachment?.token == token) {
				// Detach first: terminal callbacks cannot admit another request through this owner.
				attachment = null
				try { candidate.enqueue(ReaderTransitionFact.PublicationClosed(null)) }
				finally { retireSemanticRequests() }
			}
		}
	}

	fun enqueue(fact: ReaderTransitionFact) {
		if (mode != ReaderTransitionGatewayMode.Preparing) attachment?.enqueue?.invoke(fact)
	}

	fun registerSemanticRequest(request: ReaderSemanticExecutableRequest): ReaderSemanticRequestHandle {
		check(mode == ReaderTransitionGatewayMode.Activated) {
			"Executable semantic requests are admitted only after activation"
		}
		return checkNotNull(attachment?.registerSemanticRequest) {
			"Activated semantic request registry is unavailable"
		}(request)
	}

	fun observeLegacyPresentationEvent(event: ReaderPresentationEvent): Boolean {
		if (mode != ReaderTransitionGatewayMode.Shadow) return false
		event.toShadowTransitionFactOrNull()?.let(::enqueue)
		return true
	}

	fun observePresentationReceipt(receipt: ReaderPresentationEventReceipt?) {
		if (mode != ReaderTransitionGatewayMode.Preparing) receipt?.let { attachment?.enqueueReceipt?.invoke(it) }
	}

	private data class Attachment(
		val token: Long,
		val enqueue: (ReaderTransitionFact) -> Unit,
		val enqueueReceipt: (ReaderPresentationEventReceipt) -> Unit,
		val registerSemanticRequest: ((ReaderSemanticExecutableRequest) -> ReaderSemanticRequestHandle)?,
		val candidate: ReaderTransitionGatewayCandidate? = null
	)
}

internal inline fun <T> ReaderTransitionGateway.dispatchBeforeLegacy(
	fact: ReaderTransitionFact?,
	legacyDispatch: () -> T
): T {
	check(mode == ReaderTransitionGatewayMode.Shadow) { "Selected routing requires an explicit inert result" }
	fact?.let(::enqueue)
	return legacyDispatch()
}

internal inline fun <T> ReaderTransitionGateway.dispatchBeforeLegacy(
	fact: ReaderTransitionFact?,
	selectedResult: () -> T,
	legacyDispatch: () -> T
): T = when (mode) {
	ReaderTransitionGatewayMode.Shadow -> dispatchBeforeLegacy(fact, legacyDispatch)
	ReaderTransitionGatewayMode.Preparing -> selectedResult()
	ReaderTransitionGatewayMode.Activated -> {
		fact?.let(::enqueue)
		selectedResult()
	}
}

internal inline fun <T> ReaderTransitionGateway.dispatchActivatedOrLegacy(
	fact: ReaderTransitionFact,
	activatedResult: () -> T,
	legacyDispatch: () -> T
): T = dispatchBeforeLegacy(fact, activatedResult, legacyDispatch)

internal fun <T> ReaderTransitionGateway.dispatchSemanticActivatedOrLegacy(
	intent: (ReaderSemanticRequestHandle) -> ReaderSemanticSynchronizationIntent,
	request: ReaderSemanticExecutableRequest,
	activatedResult: () -> T,
	legacyDispatch: () -> T
): T {
	if (mode == ReaderTransitionGatewayMode.Preparing) return activatedResult()
	if (mode == ReaderTransitionGatewayMode.Activated) {
		val handle = registerSemanticRequest(request)
		enqueue(ReaderTransitionFact.Intent(null, intent(handle)))
		return activatedResult()
	}
	val observedIntent = intent(observedSemanticRequestHandle())
	return dispatchBeforeLegacy(ReaderTransitionFact.Intent(null, observedIntent), legacyDispatch)
}

internal inline fun <T> ReaderTransitionGateway.dispatchViewerActionBeforeLegacy(
	state: ReaderControllerState,
	action: ReaderViewerAction,
	gestureId: ReaderTransitionGestureId,
	onTransitionIntentRegistered: () -> Unit,
	legacyDispatch: () -> T
): T {
	val fact = readerTransitionIntentForViewerAction(
		state,
		action,
		gestureId,
		observedSemanticRequestHandle()
	)?.let { intent ->
		onTransitionIntentRegistered()
		ReaderTransitionFact.Intent(null, intent)
	}
	return dispatchBeforeLegacy(fact, legacyDispatch)
}

internal inline fun <T> ReaderTransitionGateway.dispatchViewerActionBeforeLegacy(
	state: ReaderControllerState,
	action: ReaderViewerAction,
	gestureId: ReaderTransitionGestureId,
	onTransitionIntentRegistered: () -> Unit,
	selectedResult: () -> T,
	legacyDispatch: () -> T
): T {
	// Chrome has no semantic request and must not borrow a Shadow handle to classify it.
	if (action == ReaderViewerAction.Menu) return legacyDispatch()
	if (mode != ReaderTransitionGatewayMode.Shadow) return selectedResult()
	return dispatchViewerActionBeforeLegacy(state, action, gestureId, onTransitionIntentRegistered, legacyDispatch)
}

internal inline fun ReaderTransitionGateway.observeReceiptBeforeEffects(
	receipt: ReaderPresentationEventReceipt?,
	retainEffects: () -> Unit
) {
	observePresentationReceipt(receipt)
	if (mode != ReaderTransitionGatewayMode.Preparing) retainEffects()
}

internal typealias ReaderInstalledPhysicalPublicationIngress = (paige.navic.reader.ReaderTransitionCommand, paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied) -> Unit
internal val LocalReaderInstalledPhysicalPublicationIngress = staticCompositionLocalOf<ReaderInstalledPhysicalPublicationIngress?> { null }
internal typealias ReaderInstalledPageTurnModelIngress = (paige.navic.reader.ReaderBridgeEvent.LocationChanged, paige.navic.reader.ReaderPresentationBinding, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
internal val LocalReaderInstalledPageTurnModelIngress = staticCompositionLocalOf<ReaderInstalledPageTurnModelIngress?> { null }
internal typealias ReaderInitialSemanticReceiptIngress = (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
internal val LocalReaderInitialSemanticReceiptIngress = staticCompositionLocalOf<ReaderInitialSemanticReceiptIngress?> { null }
internal typealias ReaderSemanticReceiptIngress = (ReaderPresentationEvent.CurrentDestinationConfirmed, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
internal val LocalReaderSemanticReceiptIngress = staticCompositionLocalOf<ReaderSemanticReceiptIngress?> { null }
internal val LocalReaderTransitionGateway = staticCompositionLocalOf<ReaderTransitionGateway?> { null }

private fun ReaderPresentationEvent.toShadowTransitionFactOrNull(): ReaderTransitionFact? = when (this) {
	is ReaderPresentationEvent.FoliateRelocated,
	is ReaderPresentationEvent.CurrentDestinationConfirmed -> null
	ReaderPresentationEvent.Retry -> ReaderTransitionFact.Retry(null)
	ReaderPresentationEvent.Cancel -> ReaderTransitionFact.Intent(null, ReaderCancelIntent)
	is ReaderPresentationEvent.Lifecycle -> when (event) {
		ReaderPresentationLifecycleEvent.VisibilityLost -> ReaderTransitionFact.VisibilityChanged(null, false)
		ReaderPresentationLifecycleEvent.VisibilityRestored -> ReaderTransitionFact.VisibilityChanged(null, true)
		ReaderPresentationLifecycleEvent.RendererLost ->
			ReaderTransitionFact.ResourceLost(null, ReaderTransitionCapabilityKind.Renderer)
		ReaderPresentationLifecycleEvent.PublicationClosed -> ReaderTransitionFact.PublicationClosed(null)
		ReaderPresentationLifecycleEvent.HostDetached,
		ReaderPresentationLifecycleEvent.WebViewDetached -> null
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
