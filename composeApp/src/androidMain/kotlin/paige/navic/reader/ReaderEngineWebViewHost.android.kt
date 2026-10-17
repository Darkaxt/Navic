package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderBridgeDispatchCommand
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderCurrentDestinationContinuation
import paige.navic.reader.ReaderCurrentDestinationQueryOutcome
import paige.navic.reader.ReaderCurrentDestinationQueryFailure
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticMutationStartEvidence
import paige.navic.reader.ReaderSemanticTransportBindingResult
import paige.navic.reader.ReaderSemanticTransportRetirement
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderEngineHostCommand
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderJavascriptBridge
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderRawTextProvenanceDescriptor
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderUnboundFoliateSessionId
import paige.navic.reader.ReaderWebCommandDispatchState
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.commandsForReadyReaderRuntime
import paige.navic.reader.readerManagedStorageRoot
import paige.navic.reader.readerPageRasterSnapshotKey
import paige.navic.reader.shouldDispatchReaderCommandsToWebRuntime
import paige.navic.util.core.Logger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val ReaderEngineWebViewHostTag = "ReaderEngineWebViewHost"

/** Geometry/destination barriers only. Continuing decoration/provenance/query input
 * must not require an empty command queue to authenticate the original source. */
private fun ReaderBridgeCommand.affectsResolvedLayoutSource(): Boolean = when (this) {
	is ReaderBridgeCommand.OpenPublication, is ReaderBridgeCommand.GoToVisualPage, is ReaderBridgeCommand.GoToLocator,
	is ReaderBridgeCommand.GoToCfi, is ReaderBridgeCommand.GoToHref,
	is ReaderBridgeCommand.GoToProgress, is ReaderBridgeCommand.GoToChapterProgress,
	ReaderBridgeCommand.NextPage, is ReaderBridgeCommand.CausalNextPage,
	ReaderBridgeCommand.PreviousPage, is ReaderBridgeCommand.CausalPreviousPage,
	is ReaderBridgeCommand.PreviewPageDrag, is ReaderBridgeCommand.ScrollViewport,
	is ReaderBridgeCommand.ContentLongPressAt, is ReaderBridgeCommand.ApplySettings -> true
	is ReaderBridgeCommand.ResolveCurrentDestination, is ReaderBridgeCommand.ApplyHighlight,
	is ReaderBridgeCommand.ApplyHighlights, is ReaderBridgeCommand.RequestVisibleTextRange,
	is ReaderBridgeCommand.InstallRawTextProvenance, is ReaderBridgeCommand.ValidateWhispersyncCanonicalCues,
	is ReaderBridgeCommand.ApplyOverlayFragment, is ReaderBridgeCommand.UpdateOverlayFragmentProgress,
	is ReaderBridgeCommand.ReplaceWhispersyncCueMap, is ReaderBridgeCommand.CancelWhispersyncCueMapHold,
	is ReaderBridgeCommand.ClearOverlayPresentation, ReaderBridgeCommand.ClearOverlay,
	is ReaderBridgeCommand.Search, ReaderBridgeCommand.ClearSearch -> false
}

internal object ReaderEngineLogProjector {
	private const val RedactedConsoleDiagnostic = "[redacted-reader-console]"
	private const val NavicReaderConsolePrefix = "[NavicReader] "
	private val approvedConsoleDiagnostics = setOf(
		"module-loaded",
		"dispatch",
		"post",
		"bridge-unavailable",
		"post-failed",
		"reportError"
	)

	fun command(dispatch: ReaderBridgeDispatchCommand): String =
		"Dispatching reader engine command: ${dispatch.command.type}"

	fun event(event: ReaderBridgeEvent): String =
		"Reader bridge event: ${event.diagnosticName()}"

	@Suppress("UNUSED_PARAMETER")
	fun console(level: String, message: String?, sourceId: String?): String {
		val diagnostic = message
			?.takeIf { it.startsWith(NavicReaderConsolePrefix) }
			?.removePrefix(NavicReaderConsolePrefix)
			?.substringBefore(' ')
			?.takeIf(approvedConsoleDiagnostics::contains)
			?: RedactedConsoleDiagnostic
		return "Reader console ${level.diagnosticName()}: $diagnostic"
	}

	private fun String.diagnosticName(): String =
		when (this) {
			"TIP", "LOG", "WARNING", "ERROR", "DEBUG" -> this
			else -> "UNKNOWN"
		}

	private fun ReaderBridgeEvent.diagnosticName(): String =
		when (this) {
			ReaderBridgeEvent.Ready -> "ready"
			is ReaderBridgeEvent.CommandAcknowledged -> "commandAck"
			is ReaderBridgeEvent.CommandFailed -> "commandFailed"
			ReaderBridgeEvent.PublicationReady -> "publicationReady"
			ReaderBridgeEvent.CenterTap -> "readerCenterTap"
			is ReaderBridgeEvent.ContentTapHandled -> "contentTapHandled"
			is ReaderBridgeEvent.InternalLinkRequested -> "internalLink"
			is ReaderBridgeEvent.ExternalLink -> "externalLink"
			is ReaderBridgeEvent.LocationChanged -> "locationChanged"
			is ReaderBridgeEvent.CurrentDestinationResolved -> "currentDestinationResolved"
			is ReaderBridgeEvent.DuplicatePageSuspected -> "duplicatePageSuspected"
			is ReaderBridgeEvent.CfiChanged -> "cfiChanged"
			is ReaderBridgeEvent.TocItemChanged -> "tocItemChanged"
			is ReaderBridgeEvent.PaginationProfileStatusChanged -> "paginationProfileStatus"
			is ReaderBridgeEvent.SelectionChanged -> "selectionChanged"
			ReaderBridgeEvent.SelectionCleared -> "selectionCleared"
			is ReaderBridgeEvent.AnnotationClick -> "annotationClick"
			is ReaderBridgeEvent.AnnotationDrawn -> "annotationDrawn"
			is ReaderBridgeEvent.OverlayCreated -> "overlayCreated"
			is ReaderBridgeEvent.LoadDoc -> "loadDoc"
			is ReaderBridgeEvent.FootnoteOpen -> "footnoteOpen"
			ReaderBridgeEvent.FootnoteClose -> "footnoteClose"
			is ReaderBridgeEvent.PullUp -> "pullUp"
			is ReaderBridgeEvent.VisibleTextRange -> "visibleTextRange"
			is ReaderBridgeEvent.TextPoint -> "textPoint"
			is ReaderBridgeEvent.WhispersyncCueMapRendered -> "whispersyncCueMapRendered"
			is ReaderBridgeEvent.WhispersyncCueMapSeekRequested -> "whispersyncCueMapSeekRequested"
			is ReaderBridgeEvent.WhispersyncCueMapHoldOutcome -> "whispersyncCueMapHoldOutcome"
			is ReaderBridgeEvent.RawTextProvenanceStatusChanged -> "rawTextProvenanceStatus"
			is ReaderBridgeEvent.WhispersyncCanonicalPreflightResult -> "whispersyncCanonicalPreflightResult"
			is ReaderBridgeEvent.OverlayFragmentActive -> "overlayFragmentActive"
			is ReaderBridgeEvent.OverlayFragmentInactive -> "overlayFragmentInactive"
			is ReaderBridgeEvent.SearchResults -> "searchResults"
			is ReaderBridgeEvent.Toc -> "toc"
			is ReaderBridgeEvent.Error -> "error"
		}
}

internal interface ReaderEnginePhysicalDestructionSource {
	fun bindPhysicalDestruction(onDestroyed: (WebView) -> Unit): Boolean
}

internal class ReaderEngineWebView(context: Context) : WebView(context), ReaderEngineCurrentDestinationQuerySource,
	ReaderEnginePhysicalDestructionSource {
	private var physicalDestructionConfirmation: ((WebView) -> Unit)? = null
	private var physicalDestructionInProgress = false
	private var physicallyDestroyed = false
	override fun bindPhysicalDestruction(onDestroyed: (WebView) -> Unit): Boolean {
		if (physicallyDestroyed || physicalDestructionInProgress) return false
		val existing = physicalDestructionConfirmation
		if (existing != null) return existing === onDestroyed
		physicalDestructionConfirmation = onDestroyed
		return true
	}

	override fun destroy() {
		if (physicallyDestroyed || physicalDestructionInProgress) return
		physicalDestructionInProgress = true
		try {
			super.destroy()
			physicallyDestroyed = true
		} finally {
			physicalDestructionInProgress = false
		}
		clearCurrentDestinationQueryDelegate()
		runtimeInitializationVisibilityListener = null
		windowVisibilityListener = null
		val confirmation = physicalDestructionConfirmation
		physicalDestructionConfirmation = null
		confirmation?.invoke(this)
	}

	var runtimeInitializationVisibilityListener: ((Int) -> Unit)? = null
	override fun createInitialDestinationSemanticRequest(
		reservation: ReaderReservedNeutralBootstrapRequest,
		version: paige.navic.reader.ReaderPresentationReceiptVersion,
		publicationGeneration: Long,
		viewportGeneration: Long,
		profileGeneration: Long,
		isAdmissionCurrent: () -> Boolean,
		ingress: (paige.navic.reader.ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> paige.navic.reader.ReaderPresentationEventReceipt?
	): paige.navic.reader.ReaderSemanticExecutableRequest = readerEngineInitialDestinationSemanticRequest(
		this, reservation, version, publicationGeneration, viewportGeneration, profileGeneration, isAdmissionCurrent, ingress = ingress
	)
	override fun createInitialDestinationConstructionSemanticRequest(
		reservation: ReaderReservedNeutralBootstrapRequest,
		version: paige.navic.reader.ReaderPresentationReceiptVersion,
		publicationGeneration: Long,
		viewportGeneration: Long,
		profileGeneration: Long,
		isAdmissionCurrent: () -> Boolean,
		constructionCustody: ReaderEngineInitialDestinationConstructionCustody,
		ingress: (paige.navic.reader.ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> paige.navic.reader.ReaderPresentationEventReceipt?
	): paige.navic.reader.ReaderSemanticExecutableRequest = readerEngineInitialDestinationSemanticRequest(
		this, reservation, version, publicationGeneration, viewportGeneration, profileGeneration, isAdmissionCurrent, constructionCustody, ingress
	)
	// Cancellation forwarding only: authority remains in the original Curl cell/ledger token.
	private var installedTurnRetirement: ReaderEngineInstalledPageTurnContinuation? = null
	fun connectInstalledTurnRetirement(token: ReaderEngineInstalledPageTurnContinuation): Boolean {
		if (runtimeRetired || installedTurnRetirement != null) return false
		installedTurnRetirement = token
		return true
	}
	override fun createInstalledPageTurnSemanticRequest(source: ReaderInstalledCurlGestureSource,
		isAdmissionCurrent: () -> Boolean, ingress: ReaderBoundPageTurnSemanticIngress): paige.navic.reader.ReaderSemanticExecutableRequest =
		readerEngineInstalledPageTurnSemanticRequest(this, source, isAdmissionCurrent, ingress)
	override fun submitInstalledPageTurn(continuation: ReaderEngineInstalledPageTurnContinuation,
		dispatch: ReaderBridgeDispatchCommand): Boolean = !runtimeRetired && installedTurnRetirement === continuation &&
		currentDestinationQueryDelegate?.submitInstalledPageTurn(continuation, dispatch) == true
	override fun retireInstalledPageTurnExact(continuation: ReaderEngineInstalledPageTurnContinuation) {
		if (installedTurnRetirement === continuation) installedTurnRetirement = null
		currentDestinationQueryDelegate?.retireInstalledPageTurnExact(continuation)
	}

	fun retireInstalledTurnTransport() {
		val installed = installedTurnRetirement
		installedTurnRetirement = null
		installed?.retire()
	}

	var currentDestinationQueryDelegate: ReaderEngineCurrentDestinationQuerySource? = null
	var runtimeRetired = false
		private set
	fun clearCurrentDestinationQueryDelegate() {
		runtimeRetired = true
		val installed = installedTurnRetirement
		installedTurnRetirement = null
		installed?.retire()
		currentDestinationQueryDelegate = null
	}
	override fun queryInitialDestination(
		origin: ReaderPresentationEventOrigin.SemanticCommand,
		context: ReaderSemanticMutationStartEvidence,
		isAdmissionCurrent: () -> Boolean,
		constructionCustody: ReaderEngineInitialDestinationConstructionCustody?,
		onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
	): ReaderSemanticExecutableResult = currentDestinationQueryDelegate
		?.queryInitialDestination(origin, context, isAdmissionCurrent, constructionCustody, onOutcome)
		?: ReaderSemanticExecutableResult.Rejected
	override fun queryCurrentDestination(
		origin: ReaderPresentationEventOrigin.SemanticCommand,
		context: ReaderSemanticMutationStartEvidence,
		isAdmissionCurrent: () -> Boolean,
		onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
	): ReaderSemanticExecutableResult = currentDestinationQueryDelegate
		?.queryCurrentDestination(origin, context, isAdmissionCurrent, onOutcome)
		?: ReaderSemanticExecutableResult.Rejected
	override fun queryAllocationLayout(
		command: ReaderTransitionCommand.AllocateMaterialBinding,
		isAdmissionCurrent: () -> Boolean,
		onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
	): ReaderSemanticTransportRetirement? = currentDestinationQueryDelegate
		?.queryAllocationLayout(command, isAdmissionCurrent, onOutcome)
	override fun admitsCurrentDestinationSession(foliateSessionId: String): Boolean =
		currentDestinationQueryDelegate?.admitsCurrentDestinationSession(foliateSessionId) == true
	override fun captureResolvedLayoutCurrency(): (() -> Boolean)? =
		currentDestinationQueryDelegate?.captureResolvedLayoutCurrency()
	override fun retireCurrentDestinationQueryExact(continuation: ReaderCurrentDestinationContinuation) {
		currentDestinationQueryDelegate?.retireCurrentDestinationQueryExact(continuation)
	}
	override fun closeCurrentDestinationQueries() {
		val delegate = currentDestinationQueryDelegate ?: return
		clearCurrentDestinationQueryDelegate() // Fence admission before any external continuation.
		delegate.closeCurrentDestinationQueries()
	}
	var windowVisibilityListener: ((Int) -> Unit)? = null
	private var foregroundAttachmentHost: ReaderForegroundWebViewAttachmentHost? = null
	private var foregroundSource: ReaderForegroundWebViewSource? = null
	var runtimeInitializationCompleted = false
		private set
	fun completeRuntimeInitialization() {
		if (runtimeRetired || runtimeInitializationCompleted) return
		runtimeInitializationCompleted = true
		foregroundSource?.runtimeInitializationCompleted()
	}

	override fun onAttachedToWindow() {
		val host = findReaderForegroundWebViewAttachmentHost()
		foregroundAttachmentHost = host
		if (host == null) super.onAttachedToWindow()
		else host.withForegroundWebViewAttachment(this) { exactSource ->
			foregroundSource = exactSource
			super.onAttachedToWindow()
			if (runtimeInitializationCompleted && !runtimeRetired) exactSource?.runtimeInitializationCompleted()
		}
	}

	override fun onDetachedFromWindow() {
		val source = foregroundSource
		if (source == null) super.onDetachedFromWindow()
		else source.physicalSourceReturn { super.onDetachedFromWindow() }
	}

	override fun onWindowVisibilityChanged(visibility: Int) {
		super.onWindowVisibilityChanged(visibility)
		// Construction can finish while installed presentation consequences stay fenced.
		if (!runtimeRetired && !runtimeInitializationCompleted) {
			runtimeInitializationVisibilityListener?.invoke(visibility)
		}
		// Recheck the immutable Native installation authority after framework work.
		// This fence changes no runtime start/recovery/command policy.
		if (foregroundAttachmentHost?.foregroundLegacyConsequencesOpen == false ||
			foregroundSource?.legacyConsequencesOpen == false) return
		windowVisibilityListener?.invoke(visibility)
	}
}

private fun View.findReaderForegroundWebViewAttachmentHost(): ReaderForegroundWebViewAttachmentHost? {
	var candidate: View? = this
	while (candidate != null) {
		if (candidate is ReaderForegroundWebViewAttachmentHost) return candidate
		candidate = candidate.parent as? View
	}
	return null
}

private class ActiveReaderSettingsWebViewMutation(
	val commandId: String,
	val runtimeGeneration: Int,
	val mutation: ReaderSettingsWebViewMutation,
	val boundary: ReaderSettingsWebViewPhysicalBoundary
)

/** The composable's actual JS/visual primitives, with separately parked unstarted phases. */
internal class ReaderSettingsWebViewPhysicalBoundary(
	private val webView: WebView,
	private val mutation: ReaderSettingsWebViewMutation,
	private val isHostCurrent: () -> Boolean
) {
	private var javascriptStarted = false
	private var visualStarted = false

	fun dispatch(script: String): Boolean {
		if (javascriptStarted) return false
		val current = isHostCurrent() // External host authority may freeze reentrantly.
		if (!current) { mutation.cancel(); return false }
		val started = mutation.invokeMutation {
			javascriptStarted = true
			webView.evaluateJavascript(script, null)
		}
		if (!started) mutation.deferPhase { dispatch(script) }
		return started
	}

	fun acknowledge(
		sequence: Long,
		snapshotKey: Int,
		onCommitted: () -> Unit,
		dispatchNext: () -> Unit,
		isVisualCurrent: () -> Boolean = { true }
	): Boolean {
		if (!javascriptStarted || visualStarted) return false
		val current = isHostCurrent()
		if (!current) { mutation.cancel(); return false }
		val registered = mutation.invokeMutationWithResult<Long>({ result ->
			visualStarted = true
			webView.postVisualStateCallback(sequence, object : WebView.VisualStateCallback() {
				override fun onComplete(requestId: Long) { result(requestId) }
			})
		}) { requestId ->
			val currentResult = isHostCurrent() && isVisualCurrent()
			if (requestId != sequence || !currentResult) mutation.cancel()
			else mutation.completePresentation(snapshotKey, onCommitted, dispatchNext)
		}
		if (!registered) mutation.deferPhase { acknowledge(sequence, snapshotKey, onCommitted, dispatchNext, isVisualCurrent) }
		return registered
	}
}

private object ReaderWebViewReleaseQueue {
	private val handler = Handler(Looper.getMainLooper())

	fun enqueue(release: () -> Unit) {
		handler.post { release() }
	}
}

@Composable
actual fun ReaderEngineWebViewHost(
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
	rawTextProvenanceDescriptors: List<ReaderRawTextProvenanceDescriptor>,
	command: ReaderEngineHostCommand?,
	commandKey: Long,
	onEvent: (ReaderEngineHostEvent) -> Unit,
	modifier: Modifier
) {
	val context = LocalContext.current
	val readerAssetLoader = remember(context) {
		WebViewAssetLoader.Builder()
			.setDomain(ReaderWebRuntime.AssetLoaderDomain)
			.addPathHandler(
				ReaderWebRuntime.AssetLoaderAssetsPathPrefix,
				WebViewAssetLoader.AssetsPathHandler(context)
			)
			.addPathHandler(
				ReaderPublicationCachePathPrefix,
				WebViewAssetLoader.InternalStoragePathHandler(context, readerManagedStorageRoot(context))
			)
			.build()
	}
	val currentOnEvent by rememberUpdatedState(onEvent)
	val publicationKey = remember(
		publicationUrl,
		mediaOverlayEnabled,
		externalShellCover,
		suppressWebShellCover,
		nativeShellCoverTint,
		startCfi,
		startHref,
		startProgress
	) {
		listOf(
			publicationUrl,
			mediaOverlayEnabled.toString(),
			externalShellCover.toString(),
			suppressWebShellCover.toString(),
			nativeShellCoverTint.orEmpty(),
			startCfi.orEmpty(),
			startHref.orEmpty(),
			startProgress?.toString().orEmpty()
		).joinToString("|")
	}
	val openCommand = remember(
		publicationUrl,
		mediaOverlayEnabled,
		externalShellCover,
		suppressWebShellCover,
		nativeShellCoverTint,
		settings,
		startCfi,
		startHref,
		startProgress
	) {
		ReaderBridgeCommand.OpenPublication(
			url = publicationUrl,
			foliateSessionId = ReaderUnboundFoliateSessionId,
			mediaOverlayEnabled = mediaOverlayEnabled,
			externalShellCover = externalShellCover,
			suppressWebShellCover = suppressWebShellCover,
			nativeShellCoverTint = nativeShellCoverTint,
			startLocator = ReaderLocator(
				cfi = startCfi,
				href = startHref,
				progress = startProgress
			).takeIf { it.cfi != null || it.href != null || it.progress != null },
			settings = settings.copy(nativeTapZones = true)
		)
	}
	val currentPublicationKey by rememberUpdatedState(publicationKey)
	val currentOpenCommand by rememberUpdatedState(openCommand)
	val currentRawTextProvenanceDescriptors by rememberUpdatedState(rawTextProvenanceDescriptors)
	val currentCommands by rememberUpdatedState(command.toReaderBridgeCommandsWithEngineNativeTapZones())
	val currentCommandKey by rememberUpdatedState(commandKey)
	var webView by remember { mutableStateOf<WebView?>(null) }
	var webViewGeneration by remember { mutableStateOf(0) }
	var commandDispatchState by remember { mutableStateOf(ReaderWebCommandDispatchState()) }
	val settingsMutationRequestSequence = remember { AtomicLong(0L) }
	val settingsVisualStateSequence = remember { AtomicLong(0L) }
	val activeSettingsMutation = remember {
		AtomicReference<ActiveReaderSettingsWebViewMutation?>(null)
	}
	var readerRuntimeReady by remember { mutableStateOf(false) }
	val runtimeRecovery = remember { ReaderEngineRuntimeRecovery() }
	val runtimeLoadStartedAtMillis = remember { AtomicLong(0L) }
	fun phaseDiagnostic(
		phase: ReaderWebViewDiagnosticPhase,
		state: ReaderWebViewDiagnosticState,
		reason: ReaderWebViewDiagnosticReason = ReaderWebViewDiagnosticReason.None,
		startedAtMillis: Long = runtimeLoadStartedAtMillis.get()
	) {
		runCatching {
			Logger.i(ReaderEngineWebViewHostTag, ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.Engine, phase, state, reason,
				SystemClock.elapsedRealtime() - startedAtMillis))
		}
	}
	// Only a local linearization fence: dispatch custody remains in the existing ledger.
	val queryCallbackDepth = remember { AtomicInteger(0) }
	// One dynamically scoped delivery on the original callback path, not a result
	// ledger. Failed/retirement callbacks cannot capture a successful source scope.
	val resolvedDestinationDelivery = remember { AtomicReference<ReaderBridgeEvent.CurrentDestinationResolved?>(null) }
	// Source provenance on this original Engine, not another command owner. A consumed
	// navigation cannot become current again merely because decoration replaced its key.
	val resolvedLayoutMutation = remember { AtomicReference(Any()) }
	val resolvedLayoutDestination = remember {
		AtomicReference<paige.navic.reader.ReaderDestinationCommitIdentity?>(null)
	}
	fun observeResolvedLayoutDestination(destination: paige.navic.reader.ReaderDestinationCommitIdentity?) {
		if (resolvedLayoutDestination.getAndSet(destination) != destination) resolvedLayoutMutation.set(Any())
	}

	fun deliverQueryOutcome(continuation: ReaderCurrentDestinationContinuation, outcome: ReaderCurrentDestinationQueryOutcome) {
		(outcome as? ReaderCurrentDestinationQueryOutcome.Resolved)?.event?.let {
			observeResolvedLayoutDestination(it.destinationCommitIdentity)
		}
		queryCallbackDepth.incrementAndGet()
		val previousDelivery = resolvedDestinationDelivery.getAndSet((outcome as? ReaderCurrentDestinationQueryOutcome.Resolved)?.event)
		try { continuation.complete(outcome) }
		finally {
			resolvedDestinationDelivery.set(previousDelivery)
			queryCallbackDepth.decrementAndGet()
		}
	}

	fun retireQueries(generation: Int?, reason: ReaderCurrentDestinationQueryFailure) {
		val previous = commandDispatchState
		if (generation != null && previous.runtimeGeneration != generation) return
		val retired = previous.pendingCommands.filter { it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination }
		val retiredTurns = previous.pendingCommands.mapNotNull { it.installedPageTurnContinuation }
		commandDispatchState = previous.closeCurrentDestinationQueries().closeInstalledPageTurns() // Bulk custody before any callback.
		queryCallbackDepth.incrementAndGet()
		try {
			retiredTurns.forEach { it.complete(null) }
			retired.forEach { pending -> pending.currentDestinationContinuation?.complete(ReaderCurrentDestinationQueryOutcome.Failed(reason)) }
		} finally { queryCallbackDepth.decrementAndGet() }
	}

	fun queryContextIsCurrent(token: ReaderEngineCurrentDestinationContinuation, target: WebView): Boolean =
		token.active && token.belongsTo(target) && webView === target &&
			(target as? ReaderEngineWebView)?.runtimeRetired == false &&
			webViewGeneration == token.expectedGeneration && (readerRuntimeReady || token.initialBootstrap) &&
			target.url == ReaderWebRuntime.entrypointUrl &&
			currentPublicationKey == token.expectedPublicationKey &&
			commandDispatchState.publicationKey == token.expectedPublicationKey &&
			commandDispatchState.runtimeGeneration == token.expectedGeneration &&
			commandDispatchState.foliateSessionId == token.expectedFoliateSessionId

	fun retireReplacedPublicationQueries() {
		if (commandDispatchState.publicationKey != null && commandDispatchState.publicationKey != currentPublicationKey) {
			(webView as? ReaderEngineWebView)?.retireInstalledTurnTransport()
			retireQueries(null, ReaderCurrentDestinationQueryFailure.PublicationReplaced)
		}
	}

	fun restartInterruptedReaderRuntime(
		generation: Int,
		retireGeneration: () -> Boolean
	) {
		val interruptedView = webView
		if (generation != webViewGeneration || !retireGeneration()) return
		if (generation != webViewGeneration || webView !== interruptedView || interruptedView == null) return
		runtimeRecovery.reset()
		readerRuntimeReady = false
		Logger.i(
			ReaderEngineWebViewHostTag,
			"Restarting interrupted reader runtime after window visibility restored"
		)
		webViewGeneration += 1
	}

	fun cancelActiveSettingsMutation(runtimeGeneration: Int? = null) {
		while (true) {
			val active = activeSettingsMutation.get() ?: return
			if (
				runtimeGeneration != null &&
				active.runtimeGeneration != runtimeGeneration
			) return
			if (activeSettingsMutation.compareAndSet(active, null)) {
				active.mutation.cancel()
				return
			}
		}
	}

	fun WebView.dispatchSettingsCommand(
		dispatch: ReaderBridgeDispatchCommand
	) {
		val targetView = this
		val expectedGeneration = webViewGeneration
		val ownershipHost = findReaderSettingsWebViewMutationHost()
		if (ownershipHost == null) {
			Logger.w(
				ReaderEngineWebViewHostTag,
				"Reader settings mutation has no foreground ownership host"
			)
			return
		}
		val requestId = settingsMutationRequestSequence.incrementAndGet()
		ownershipHost.acquireSettingsMutation(requestId) { readiness ->
			when (readiness) {
				is ReaderSettingsWebViewMutationReadiness.Ready -> {
					if (
						expectedGeneration != webViewGeneration ||
						webView !== targetView ||
						commandDispatchState.acknowledgedCommand(dispatch.id) !=
							dispatch.command
					) {
						readiness.mutation.cancel()
						return@acquireSettingsMutation
					}
					val boundary = ReaderSettingsWebViewPhysicalBoundary(targetView, readiness.mutation) {
						expectedGeneration == webViewGeneration && webView === targetView &&
							activeSettingsMutation.get()?.mutation === readiness.mutation &&
							commandDispatchState.acknowledgedCommand(dispatch.id) == dispatch.command
					}
					val active = ActiveReaderSettingsWebViewMutation(
						commandId = dispatch.id,
						runtimeGeneration = expectedGeneration,
						mutation = readiness.mutation,
						boundary = boundary
					)
					if (!activeSettingsMutation.compareAndSet(null, active)) {
						readiness.mutation.cancel()
						return@acquireSettingsMutation
					}
					Logger.i(
						ReaderEngineWebViewHostTag,
						ReaderEngineLogProjector.command(dispatch)
					)
					val script = ReaderWebRuntime.commandScript(dispatch)
					try { boundary.dispatch(script) }
					catch (failure: Throwable) {
						activeSettingsMutation.compareAndSet(active, null)
						readiness.mutation.cancel()
						throw failure
					}
				}
				is ReaderSettingsWebViewMutationReadiness.Rejected -> {
					Logger.w(
						ReaderEngineWebViewHostTag,
						"Reader settings mutation foreground ownership was rejected"
					)
				}
			}
		}
	}

	fun WebView.dispatchReadyReaderCommands() {
		if (webView !== this || (this as? ReaderEngineWebView)?.runtimeRetired == true || queryCallbackDepth.get() != 0) return
		retireReplacedPublicationQueries()
		// Retirement callbacks can close or replace this exact view before projection.
		if (webView !== this || (this as? ReaderEngineWebView)?.runtimeRetired == true || queryCallbackDepth.get() != 0) return
		if (
			!shouldDispatchReaderCommandsToWebRuntime(
				runtimeReady = readerRuntimeReady && (this as? ReaderEngineWebView)?.runtimeInitializationCompleted == true,
				currentUrl = url,
				entrypointUrl = ReaderWebRuntime.entrypointUrl
			)
		) {
			Logger.i(
				ReaderEngineWebViewHostTag,
				"Skipping reader command dispatch: ready=$readerRuntimeReady entrypoint=${url == ReaderWebRuntime.entrypointUrl}"
			)
			return
		}
		val dispatchGeneration = webViewGeneration
		val dispatchPublicationKey = currentPublicationKey
		val publicationChanged = commandDispatchState.publicationKey != dispatchPublicationKey
		val step = commandDispatchState.commandsForReadyReaderRuntime(
			runtimeGeneration = dispatchGeneration,
			publicationKey = dispatchPublicationKey,
			openCommand = currentOpenCommand,
			commands = currentCommands,
			commandKey = currentCommandKey,
			rawTextProvenanceDescriptors = currentRawTextProvenanceDescriptors
		)
		commandDispatchState = step.state
		queryCallbackDepth.incrementAndGet()
		try {
			step.retiredInstalledPageTurnContinuations.forEach { it.complete(null) }
			step.retiredCurrentDestinationContinuations.forEach {
				it.complete(ReaderCurrentDestinationQueryOutcome.Failed(if (publicationChanged)
					ReaderCurrentDestinationQueryFailure.PublicationReplaced else ReaderCurrentDestinationQueryFailure.RuntimeRetired))
			}
		} finally { queryCallbackDepth.decrementAndGet() }
		step.commands.forEach { dispatch ->
			if (webView !== this || (this as? ReaderEngineWebView)?.runtimeRetired == true ||
				webViewGeneration != dispatchGeneration || currentPublicationKey != dispatchPublicationKey ||
				commandDispatchState.publicationKey != dispatchPublicationKey ||
				commandDispatchState.pendingCommands.firstOrNull()?.dispatch?.id != dispatch.id) return@forEach
			val token = commandDispatchState.pendingCommands.firstOrNull()?.currentDestinationContinuation as? ReaderEngineCurrentDestinationContinuation
			if (token != null && (!token.admits() || !queryContextIsCurrent(token, this) ||
				commandDispatchState.pendingCommands.firstOrNull()?.currentDestinationContinuation !== token)) {
				commandDispatchState = commandDispatchState.retireCurrentDestinationQueryExact(token)
				deliverQueryOutcome(token, ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.SubmissionRejected))
				webView?.dispatchReadyReaderCommands()
				return@forEach
			}
			val installedTurn = commandDispatchState.pendingCommands.firstOrNull()?.installedPageTurnContinuation as? ReaderEngineInstalledPageTurnContinuation
			if (installedTurn != null) {
				val script = ReaderWebRuntime.commandScript(dispatch)
				try {
					if (!installedTurn.invokeNavigation {
						resolvedLayoutMutation.set(Any())
						evaluateJavascript(script, null)
					}) installedTurn.fail()
				} catch (_: Throwable) { installedTurn.fail(paige.navic.reader.ReaderSemanticAsyncFailure.Threw) }
				return@forEach
			}
			if (dispatch.command.affectsResolvedLayoutSource()) resolvedLayoutMutation.set(Any())
			if (dispatch.command is ReaderBridgeCommand.ApplySettings) {
				dispatchSettingsCommand(dispatch)
			} else {
				Logger.i(
					ReaderEngineWebViewHostTag,
					ReaderEngineLogProjector.command(dispatch)
				)
				try {
					evaluateJavascript(ReaderWebRuntime.commandScript(dispatch), null)
				} catch (failure: Throwable) {
					if (token == null) throw failure
					commandDispatchState = commandDispatchState.retireCurrentDestinationQueryExact(token)
					deliverQueryOutcome(token, ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.SubmissionThrew))
					webView?.dispatchReadyReaderCommands()
				}
			}
		}
	}

	fun WebView.commitSettingsPresentation(
		settings: ReaderSettings,
		active: ActiveReaderSettingsWebViewMutation
	) {
		val sequence = settingsVisualStateSequence.incrementAndGet()
		val snapshotKey = settings.readerPageRasterSnapshotKey()
		active.boundary.acknowledge(
			sequence = sequence,
			snapshotKey = snapshotKey,
			onCommitted = {
				commandDispatchState = commandDispatchState.acknowledge(active.commandId)
				currentOnEvent(ReaderEngineHostEvent.SettingsPresentationCommitted(snapshotKey))
			},
			dispatchNext = {
				if (activeSettingsMutation.compareAndSet(active, null)) dispatchReadyReaderCommands()
			},
			isVisualCurrent = { settingsVisualStateSequence.get() == sequence }
		)
	}

	fun handleReaderBridgeEvent(event: ReaderBridgeEvent) {
		Logger.i(ReaderEngineWebViewHostTag, ReaderEngineLogProjector.event(event))
		val installedHead = commandDispatchState.pendingCommands.firstOrNull()?.dispatch?.command is ReaderBridgeCommand.GoToVisualPage
		if ((event is ReaderBridgeEvent.LocationChanged && (installedHead || event.commandId?.startsWith("reader-turn-") == true)) ||
			(event is ReaderBridgeEvent.CommandFailed && (installedHead || event.commandId.startsWith("reader-turn-")))) {
			if (commandDispatchState.publicationKey != currentPublicationKey) return
			val previous = commandDispatchState
			val consumed = previous.consumeInstalledPageTurnResult(event, webViewGeneration)
			commandDispatchState = consumed.state // Exact head consumed BEFORE external model/progress/WordSync.
			consumed.event?.let { observeResolvedLayoutDestination(it.destinationCommitIdentity) }
			queryCallbackDepth.incrementAndGet()
			try { consumed.continuation?.complete(consumed.event) }
			finally { queryCallbackDepth.decrementAndGet() }
			if (consumed.state !== previous) webView?.dispatchReadyReaderCommands()
			return // Wrong, duplicate and retired installed settlements never enter ordinary key/model replay.
		}
		if (
			event is ReaderBridgeEvent.CurrentDestinationResolved ||
			(event is ReaderBridgeEvent.CommandFailed &&
				commandDispatchState.acknowledgedCommand(event.commandId) is ReaderBridgeCommand.ResolveCurrentDestination)
		) {
			// This private read-only transport result never enters the generic presentation route.
			// Persist actual dispatched-head consumption before any successor dispatch can reenter.
			if (commandDispatchState.publicationKey != currentPublicationKey) return
			val previous = commandDispatchState
			val step = previous.consumeCurrentDestinationResult(event, webViewGeneration)
			commandDispatchState = step.state
			val continuation = step.continuation
			val outcome = step.outcome
			if (continuation != null && outcome != null) {
				val token = continuation as? ReaderEngineCurrentDestinationContinuation
				val target = webView
				val admitted = outcome !is ReaderCurrentDestinationQueryOutcome.Resolved || token == null ||
					(token.admits() && target != null && queryContextIsCurrent(token, target))
				val allocation = token?.allocationCommand
				// The original ledger already correlated the actually dispatched head/session.
				// Only wire semantic destination and validated numeric layout are compared here;
				// Native generations/physical installation remain in token.admits().
				val allocationMatches = allocation == null || outcome !is ReaderCurrentDestinationQueryOutcome.Resolved ||
					(outcome.event.foliateSessionId == allocation.binding.foliateSessionId &&
						outcome.event.destinationCommitIdentity == allocation.binding.destinationCommitIdentity &&
						outcome.event.resolvedLayout != null)
				val delivered = when {
					!admitted || !allocationMatches -> ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.SubmissionRejected)
					outcome is ReaderCurrentDestinationQueryOutcome.Resolved -> ReaderCurrentDestinationQueryOutcome.Resolved(outcome.event, token)
					else -> outcome
				}
				deliverQueryOutcome(continuation, delivered)
			}
			// Never use the old step's successor after external callback reentrancy.
			if (step.state !== previous) webView?.dispatchReadyReaderCommands()
			return
		}
		when (event) {
			ReaderBridgeEvent.Ready -> {
				runtimeRecovery.onRuntimeReady()
				readerRuntimeReady = true
				phaseDiagnostic(ReaderWebViewDiagnosticPhase.Ready, ReaderWebViewDiagnosticState.Completed)
				webView?.dispatchReadyReaderCommands()
			}
			is ReaderBridgeEvent.CommandAcknowledged -> {
				val acknowledged = commandDispatchState.acknowledgedCommand(event.commandId)
				val targetView = webView
				if (acknowledged is ReaderBridgeCommand.ApplySettings) {
					val active = activeSettingsMutation.get()
						?.takeIf { it.commandId == event.commandId }
					if (targetView != null && active != null) {
						targetView.commitSettingsPresentation(
							acknowledged.settings,
							active
						)
					}
				} else {
					commandDispatchState =
						commandDispatchState.acknowledge(event.commandId)
					targetView?.dispatchReadyReaderCommands()
				}
				return
			}
			is ReaderBridgeEvent.CommandFailed -> {
				val failed = commandDispatchState.acknowledgedCommand(event.commandId)
					?: return
				val failedGeneration = webViewGeneration
				val failedView = webView
				runtimeRecovery.reset()
				readerRuntimeReady = false
				(failedView as? ReaderEngineWebView)?.clearCurrentDestinationQueryDelegate()
				retireQueries(failedGeneration, ReaderCurrentDestinationQueryFailure.RuntimeRetired)
				if (failed is ReaderBridgeCommand.ApplySettings) {
					val active = activeSettingsMutation.get()
						?.takeIf { it.commandId == event.commandId }
					if (
						active != null &&
						activeSettingsMutation.compareAndSet(active, null)
					) {
						active.mutation.cancel()
					}
				}
				if (failedView != null && webView === failedView && webViewGeneration == failedGeneration) webViewGeneration += 1
				return
			}
			is ReaderBridgeEvent.LocationChanged -> {
				if (event.foliateSessionId == commandDispatchState.foliateSessionId) {
					observeResolvedLayoutDestination(event.destinationCommitIdentity)
				}
				commandDispatchState = commandDispatchState.observeLocator(event.locator)
			}
			else -> Unit
		}
		currentOnEvent(ReaderEngineHostEvent.FoliateBridge(event))
	}

	key(webViewGeneration) {
		val generation = webViewGeneration
		val generationDisposed = remember(generation) { AtomicBoolean(false) }
		val generationReleased = remember(generation) { AtomicBoolean(false) }
		val generationWebView = remember(generation) { AtomicReference<WebView?>(null) }
		val bridge = remember(generation) {
			ReaderJavascriptBridge(
				onEvent = bridgeEvent@{ event ->
					val targetView = generationWebView.get() ?: return@bridgeEvent
					targetView.post {
						if (
							!generationDisposed.get() &&
							generation == webViewGeneration &&
							webView === targetView
						) {
							handleReaderBridgeEvent(event)
						}
					}
				}
			)
		}
		val retireGeneration: () -> Boolean = {
			if (generationDisposed.compareAndSet(false, true)) {
				bridge.deactivate()
				(generationWebView.get() as? ReaderEngineWebView)?.clearCurrentDestinationQueryDelegate()
				if (generation == webViewGeneration && webView === generationWebView.get()) readerRuntimeReady = false
				retireQueries(generation, ReaderCurrentDestinationQueryFailure.RuntimeRetired)
				cancelActiveSettingsMutation(generation)
				true
			} else {
				false
			}
		}
		val releaseGeneration: (WebView?) -> Boolean = { requestedView ->
			retireGeneration()
			if (!generationReleased.compareAndSet(false, true)) {
				false
			} else {
				val targetView = generationWebView.getAndSet(null) ?: requestedView
				targetView?.removeJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)
				if (webView === targetView) webView = null
				targetView?.destroy()
				true
			}
		}

		val runtimeStartGate = remember(generation) { ReaderEngineRuntimeStartGate() }
		fun WebView.startReaderRuntimeIfVisible() {
			if (
				generationDisposed.get() ||
				generation != webViewGeneration ||
				webView !== this ||
				!runtimeStartGate.startIfVisible(windowVisibility == View.VISIBLE)
			) return
			runtimeRecovery.onRuntimeLoadStarted()
			runtimeLoadStartedAtMillis.set(SystemClock.elapsedRealtime())
			phaseDiagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Started)
			try {
				ReaderWebRuntime.configure(
					this,
					bridge,
					enableDebugging = settings.webContentsDebuggingEnabled == true
				)
			} catch (failure: Throwable) {
				phaseDiagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Failed,
					ReaderWebViewDiagnosticReason.RuntimeLoadThrew)
				throw failure
			}
		}

		DisposableEffect(bridge, generation) {
			onDispose {
				retireGeneration()
			}
		}

		AndroidView(
			modifier = modifier,
			factory = {
				ReaderEngineWebView(context).apply {
					webView = this
					generationWebView.set(this)
					val queryView = this
					fun querySourceIsCurrent(allowUnreadyInitial: Boolean = false): Boolean = Looper.myLooper() == Looper.getMainLooper() &&
						!generationDisposed.get() && !queryView.runtimeRetired && webView === queryView && generation == webViewGeneration &&
						(readerRuntimeReady || allowUnreadyInitial) && queryView.url == ReaderWebRuntime.entrypointUrl &&
						commandDispatchState.runtimeGeneration == generation && commandDispatchState.publicationKey == currentPublicationKey &&
						commandDispatchState.foliateSessionId != null && commandDispatchState.foliateSessionId != ReaderUnboundFoliateSessionId
					currentDestinationQueryDelegate = object : ReaderEngineCurrentDestinationQuerySource {
						override fun submitInstalledPageTurn(continuation: ReaderEngineInstalledPageTurnContinuation,
							dispatch: ReaderBridgeDispatchCommand): Boolean {
							if (!querySourceIsCurrent() || !continuation.admits() ||
								!querySourceIsCurrent()) return false
							val next = commandDispatchState.enqueueInstalledPageTurn(dispatch, continuation) ?: return false
							commandDispatchState = next
							queryView.dispatchReadyReaderCommands()
							return true
						}
						override fun retireInstalledPageTurnExact(continuation: ReaderEngineInstalledPageTurnContinuation) {
							if (Looper.myLooper() != Looper.getMainLooper()) {
								Handler(Looper.getMainLooper()).post { queryView.retireInstalledPageTurnExact(continuation) }
								return
							}
							if (generation != webViewGeneration || webView !== queryView) return
							val previous = commandDispatchState
							commandDispatchState = previous.retireInstalledPageTurnExact(continuation)
							if (commandDispatchState !== previous && !queryView.runtimeRetired) queryView.dispatchReadyReaderCommands()
						}

						override fun admitsCurrentDestinationSession(foliateSessionId: String): Boolean =
							querySourceIsCurrent() && commandDispatchState.foliateSessionId == foliateSessionId

						override fun captureResolvedLayoutCurrency(): (() -> Boolean)? {
							val delivery = resolvedDestinationDelivery.get() ?: return null
							if (queryCallbackDepth.get() == 0 || !querySourceIsCurrent() ||
								delivery.foliateSessionId != commandDispatchState.foliateSessionId) return null
							val publication = currentPublicationKey
							val session = delivery.foliateSessionId
							val locator = commandDispatchState.lastKnownLocator
							val settings = currentOpenCommand.settings
							val settingsRequest = settingsMutationRequestSequence.get()
							val settingsVisual = settingsVisualStateSequence.get()
							val capturedCommandKey = commandDispatchState.lastCommandKey
							val mutation = resolvedLayoutMutation.get()
							val destination = delivery.destinationCommitIdentity
							fun current(): Boolean = querySourceIsCurrent() && queryView.isAttachedToWindow &&
								resolvedLayoutMutation.get() === mutation && resolvedLayoutDestination.get() == destination &&
								currentPublicationKey == publication && commandDispatchState.foliateSessionId == session &&
								commandDispatchState.lastKnownLocator === locator && currentOpenCommand.settings == settings &&
								settingsMutationRequestSequence.get() == settingsRequest &&
								settingsVisualStateSequence.get() == settingsVisual && activeSettingsMutation.get() == null &&
								commandDispatchState.pendingCommands.none { it.dispatch.command.affectsResolvedLayoutSource() } &&
								(currentCommandKey == capturedCommandKey || currentCommands.none { it.affectsResolvedLayoutSource() })
							// No token/admission/executor currency is retained or revived here.
							return if (current()) ::current else null
						}

						override fun queryInitialDestination(
							origin: ReaderPresentationEventOrigin.SemanticCommand,
							context: ReaderSemanticMutationStartEvidence,
							isAdmissionCurrent: () -> Boolean,
							constructionCustody: ReaderEngineInitialDestinationConstructionCustody?,
							onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
						): ReaderSemanticExecutableResult {
							if (Looper.myLooper() != Looper.getMainLooper() || generationDisposed.get() || queryView.runtimeRetired ||
								webView !== queryView || generation != webViewGeneration || queryView.url != ReaderWebRuntime.entrypointUrl ||
								origin.transitionId.operation != paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage ||
								origin.transitionId.expectedBinding !is paige.navic.reader.ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ||
								!isAdmissionCurrent()) return ReaderSemanticExecutableResult.Rejected
							val publicationChanged = commandDispatchState.publicationKey != currentPublicationKey
							// The original opening pipeline, with dispatch disabled until actual Ready.
							val prepared = commandDispatchState.commandsForReadyReaderRuntime(
								generation, currentPublicationKey, currentOpenCommand, currentCommands,
								currentCommandKey, currentRawTextProvenanceDescriptors, dispatchHead = false)
							commandDispatchState = prepared.state
							queryCallbackDepth.incrementAndGet()
							try {
								prepared.retiredCurrentDestinationContinuations.forEach {
									it.complete(ReaderCurrentDestinationQueryOutcome.Failed(if (publicationChanged)
										ReaderCurrentDestinationQueryFailure.PublicationReplaced else ReaderCurrentDestinationQueryFailure.RuntimeRetired))
								}
							} finally { queryCallbackDepth.decrementAndGet() }
							return submitDestinationQuery(origin, context, isAdmissionCurrent, onOutcome, initialBootstrap = true,
								constructionCustody = constructionCustody)
						}

						override fun queryCurrentDestination(
							origin: ReaderPresentationEventOrigin.SemanticCommand,
							context: ReaderSemanticMutationStartEvidence,
							isAdmissionCurrent: () -> Boolean,
							onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
						): ReaderSemanticExecutableResult = submitDestinationQuery(origin, context, isAdmissionCurrent, onOutcome, initialBootstrap = false)

						override fun queryAllocationLayout(
							command: ReaderTransitionCommand.AllocateMaterialBinding,
							isAdmissionCurrent: () -> Boolean,
							onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
						): ReaderSemanticTransportRetirement? {
							if (!querySourceIsCurrent() || command.binding.foliateSessionId != commandDispatchState.foliateSessionId ||
								command.binding.destinationCommitIdentity == null) return null
							val transition = command.transitionId
							// Retrying the same allocation retains its authority, not its retired wire attempt.
							val id = "reader-allocation-query-${commandDispatchState.publicationSequence}-$generation-${transition.readerSessionGeneration}-${transition.coordinatorEpoch}-${transition.sequence}-${UUID.randomUUID()}"
							val token = ReaderEngineCurrentDestinationContinuation(queryView, id, generation, currentPublicationKey,
								command.binding.foliateSessionId, isAdmissionCurrent, onOutcome, allocationCommand = command)
							// Allocation already has its actual binding; no semantic context/slot is rebound.
							return if (enqueueDestinationQuery(token)) token else null
						}

						private fun submitDestinationQuery(
							origin: ReaderPresentationEventOrigin.SemanticCommand,
							context: ReaderSemanticMutationStartEvidence,
							isAdmissionCurrent: () -> Boolean,
							onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit,
							initialBootstrap: Boolean,
							constructionCustody: ReaderEngineInitialDestinationConstructionCustody? = null
						): ReaderSemanticExecutableResult {
							if (!querySourceIsCurrent(initialBootstrap)) {
								phaseDiagnostic(ReaderWebViewDiagnosticPhase.Query, ReaderWebViewDiagnosticState.Rejected,
									ReaderWebViewDiagnosticReason.QueryRejected, SystemClock.elapsedRealtime())
								return ReaderSemanticExecutableResult.Rejected
							}
							val session = checkNotNull(commandDispatchState.foliateSessionId)
							val id = "reader-query-${commandDispatchState.publicationSequence}-$generation-${origin.transitionId.readerSessionGeneration}-${origin.transitionId.coordinatorEpoch}-${origin.transitionId.sequence}-${origin.slotId.value}"
							val queryStartedAtMillis = SystemClock.elapsedRealtime()
							phaseDiagnostic(ReaderWebViewDiagnosticPhase.Query, ReaderWebViewDiagnosticState.Started,
								startedAtMillis = queryStartedAtMillis)
							val token = ReaderEngineCurrentDestinationContinuation(queryView, id, generation, currentPublicationKey, session,
								isAdmissionCurrent, { outcome ->
									val reason = when (outcome) {
										is ReaderCurrentDestinationQueryOutcome.Resolved -> ReaderWebViewDiagnosticReason.None
										is ReaderCurrentDestinationQueryOutcome.Failed -> when (outcome.reason) {
											ReaderCurrentDestinationQueryFailure.QueryCommandFailed -> ReaderWebViewDiagnosticReason.QueryFailed
											ReaderCurrentDestinationQueryFailure.RuntimeRetired -> ReaderWebViewDiagnosticReason.RuntimeRetired
											ReaderCurrentDestinationQueryFailure.PublicationReplaced -> ReaderWebViewDiagnosticReason.PublicationReplaced
											ReaderCurrentDestinationQueryFailure.Closed -> ReaderWebViewDiagnosticReason.Closed
											ReaderCurrentDestinationQueryFailure.SubmissionRejected -> ReaderWebViewDiagnosticReason.SubmissionRejected
											ReaderCurrentDestinationQueryFailure.SubmissionThrew -> ReaderWebViewDiagnosticReason.SubmissionThrew
										}
									}
									phaseDiagnostic(ReaderWebViewDiagnosticPhase.Query,
										if (outcome is ReaderCurrentDestinationQueryOutcome.Resolved) ReaderWebViewDiagnosticState.Completed
										else ReaderWebViewDiagnosticState.Failed,
										reason, queryStartedAtMillis)
									onOutcome(outcome)
								}, initialBootstrap)
							val binding = try {
								constructionCustody?.adoptQuery(origin, context, token) ?: context.bindTransportRetirement(origin, token)
							}
							catch (failure: Throwable) {
								phaseDiagnostic(ReaderWebViewDiagnosticPhase.Query, ReaderWebViewDiagnosticState.Failed,
									ReaderWebViewDiagnosticReason.SubmissionThrew, queryStartedAtMillis)
								token.retire(); throw failure
							}
							when (binding) {
								ReaderSemanticTransportBindingResult.Rejected -> {
									token.retire()
									phaseDiagnostic(ReaderWebViewDiagnosticPhase.Query, ReaderWebViewDiagnosticState.Rejected,
										ReaderWebViewDiagnosticReason.SubmissionRejected, queryStartedAtMillis)
									return ReaderSemanticExecutableResult.Rejected
								}
								ReaderSemanticTransportBindingResult.RetiredAndDisposed -> {
									phaseDiagnostic(ReaderWebViewDiagnosticPhase.Query, ReaderWebViewDiagnosticState.Cancelled,
										ReaderWebViewDiagnosticReason.RuntimeRetired, queryStartedAtMillis)
									return ReaderSemanticExecutableResult.Rejected
								}
								ReaderSemanticTransportBindingResult.Bound -> Unit
							}
							// Binding and external admission can reenter; the exact original context/token remains authoritative.
							return if (enqueueDestinationQuery(token)) ReaderSemanticExecutableResult.Accepted
								else {
									phaseDiagnostic(ReaderWebViewDiagnosticPhase.Query, ReaderWebViewDiagnosticState.Rejected,
										ReaderWebViewDiagnosticReason.SubmissionRejected, queryStartedAtMillis)
									ReaderSemanticExecutableResult.Rejected
								}
						}

						private fun enqueueDestinationQuery(token: ReaderEngineCurrentDestinationContinuation): Boolean {
							if (!token.admits() || !queryContextIsCurrent(token, queryView)) {
								token.retire(); return false
							}
							val next = commandDispatchState.enqueueCurrentDestinationQuery(
								ReaderBridgeDispatchCommand(token.commandId, ReaderBridgeCommand.ResolveCurrentDestination(token.expectedFoliateSessionId)), token,
								requiresDispatchedOpening = token.initialBootstrap)
							if (next == null) { token.retire(); return false }
							commandDispatchState = next
							queryView.dispatchReadyReaderCommands()
							return true
						}

						override fun retireCurrentDestinationQueryExact(continuation: ReaderCurrentDestinationContinuation) {
							if (Looper.myLooper() != Looper.getMainLooper()) {
								Handler(Looper.getMainLooper()).post { queryView.retireCurrentDestinationQueryExact(continuation) }
								return
							}
							if (generationDisposed.get() || generation != webViewGeneration || webView !== queryView) return
							val previous = commandDispatchState
							commandDispatchState = previous.retireCurrentDestinationQueryExact(continuation)
							if (commandDispatchState !== previous) webView?.dispatchReadyReaderCommands()
						}

						override fun closeCurrentDestinationQueries() {
							generationDisposed.set(true)
							bridge.deactivate()
							if (generation == webViewGeneration && webView === queryView) readerRuntimeReady = false
							retireQueries(generation, ReaderCurrentDestinationQueryFailure.Closed)
							cancelActiveSettingsMutation(generation)
						}
					}
					runtimeInitializationVisibilityListener = { visibility ->
						if (visibility == View.VISIBLE) startReaderRuntimeIfVisible()
					}
					windowVisibilityListener = { visibility ->
						val visible = visibility == View.VISIBLE
						if (runtimeRecovery.onWindowVisibilityChanged(visible)) {
							restartInterruptedReaderRuntime(
								generation = generation,
								retireGeneration = retireGeneration
							)
						} else if (visible) {
							startReaderRuntimeIfVisible()
						}
					}
					isLongClickable = false
					setOnLongClickListener {
						Logger.i(
							ReaderEngineWebViewHostTag,
							"Reader WebView native long-click suppressed; native frame owns selection actions"
						)
						true
					}
					webChromeClient = object : WebChromeClient() {
						override fun onConsoleMessage(message: ConsoleMessage): Boolean {
							val logMessage = ReaderEngineLogProjector.console(
								level = message.messageLevel().name,
								message = message.message(),
								sourceId = message.sourceId()
							)
							when (message.messageLevel()) {
								ConsoleMessage.MessageLevel.ERROR -> Logger.e(ReaderEngineWebViewHostTag, logMessage)
								ConsoleMessage.MessageLevel.WARNING -> Logger.w(ReaderEngineWebViewHostTag, logMessage)
								else -> Logger.i(ReaderEngineWebViewHostTag, logMessage)
							}
							return true
						}
					}
					webViewClient = object : WebViewClient() {
						override fun shouldInterceptRequest(
							view: WebView,
							request: WebResourceRequest
						): WebResourceResponse? =
							readerAssetLoader.shouldInterceptRequest(request.url)
								?: super.shouldInterceptRequest(view, request)

						override fun onPageFinished(view: WebView, url: String?) {
							val engineView = view as? ReaderEngineWebView ?: return
							if (generationDisposed.get() || generation != webViewGeneration ||
								webView !== view || generationWebView.get() !== view || engineView.runtimeRetired ||
								url != ReaderWebRuntime.entrypointUrl || view.url != ReaderWebRuntime.entrypointUrl) return
							// Native synchronously queues the authentic opening/query before Ready dispatch.
							val initializedBefore = engineView.runtimeInitializationCompleted
							engineView.completeRuntimeInitialization()
							if (!initializedBefore && engineView.runtimeInitializationCompleted) {
								phaseDiagnostic(ReaderWebViewDiagnosticPhase.Initialization, ReaderWebViewDiagnosticState.Completed)
							}
							view.dispatchReadyReaderCommands()
						}

						override fun onReceivedError(
							view: WebView,
							request: WebResourceRequest,
							error: WebResourceError
						) {
							Logger.e(
								ReaderEngineWebViewHostTag,
								"Reader engine WebView error main=${request.isForMainFrame} " +
									"code=${error.errorCode}"
							)
						}

						override fun onReceivedHttpError(
							view: WebView,
							request: WebResourceRequest,
							errorResponse: WebResourceResponse
						) {
							Logger.w(
								ReaderEngineWebViewHostTag,
								"Reader engine WebView HTTP error main=${request.isForMainFrame} " +
									"status=${errorResponse.statusCode}"
							)
						}

						override fun onRenderProcessGone(
							view: WebView,
							detail: RenderProcessGoneDetail
						): Boolean {
							if (
								generationDisposed.get() ||
								generation != webViewGeneration ||
								webView !== view
							) return true
							Logger.e(
								ReaderEngineWebViewHostTag,
								"Reader engine WebView render process gone didCrash=${detail.didCrash()} " +
									"priorityAtExit=${detail.rendererPriorityAtExit()}"
							)
							runtimeRecovery.reset()
							readerRuntimeReady = false
							val retired = retireGeneration()
							currentOnEvent(
								ReaderEngineHostEvent.FoliateBridge(
									ReaderBridgeEvent.Error(
										message = "Reader WebView renderer stopped.",
										code = "webview_render_process_gone"
									)
								)
							)
							if (retired && generation == webViewGeneration && webView === view) {
								webViewGeneration += 1
							}
							return true
						}
					}
					post { startReaderRuntimeIfVisible() }
				}
			},
			onRelease = { view ->
				retireGeneration()
				view.runtimeInitializationVisibilityListener = null
				view.windowVisibilityListener = null
				ReaderWebViewReleaseQueue.enqueue {
					releaseGeneration(view)
				}
			},
			update = { view ->
				// Publication retirement is independent of runtime readiness/the dispatch gate.
				if (generation == webViewGeneration && webView === view) retireReplacedPublicationQueries()
				view.keepScreenOn = settings.keepScreenOn == true
				ReaderWebRuntime.setWebContentsDebuggingEnabled(settings.webContentsDebuggingEnabled == true)
				if (
					shouldDispatchReaderCommandsToWebRuntime(
						runtimeReady = readerRuntimeReady,
						currentUrl = view.url,
						entrypointUrl = ReaderWebRuntime.entrypointUrl
					)
				) {
					view.dispatchReadyReaderCommands()
				}
			}
		)
	}
}

private fun View.findReaderSettingsWebViewMutationHost():
	ReaderSettingsWebViewMutationHost? {
	var candidate: View? = this
	while (candidate != null) {
		if (candidate is ReaderSettingsWebViewMutationHost) return candidate
		candidate = candidate.parent as? View
	}
	return null
}

private fun ReaderEngineHostCommand?.toReaderBridgeCommandsWithEngineNativeTapZones(): List<ReaderBridgeCommand> =
	when (this) {
		is ReaderEngineHostCommand.FoliateBridge -> listOf(command.withEngineNativeTapZones())
		is ReaderEngineHostCommand.FoliateBridgeSequence -> commands.map { it.withEngineNativeTapZones() }
		null -> emptyList()
	}

private fun ReaderBridgeCommand.withEngineNativeTapZones(): ReaderBridgeCommand =
	when (this) {
		is ReaderBridgeCommand.OpenPublication -> copy(settings = (settings ?: ReaderSettings()).copy(nativeTapZones = true))
		is ReaderBridgeCommand.ApplySettings -> copy(settings = settings.copy(nativeTapZones = true))
		else -> this
	}
