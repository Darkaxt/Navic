package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import karacken.curl.PageChange
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import java.util.UUID
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderLegacyLiveCompatibilityContext
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPageDragPreviewPhase
import paige.navic.reader.ReaderPageGestureLifecycle
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPageOperationPolicy
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePointerOwnership
import paige.navic.reader.ReaderPagePointerRoute
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderPendingPresentationEffect
import paige.navic.reader.ReaderPreparationPresentation
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEffect
import paige.navic.reader.ReaderPresentationEffectIdentity
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderRendererBusyFeedbackMaximumMillis
import paige.navic.reader.ReaderTapZoneAction
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderWhispersyncCueMapHoldOutcome
import paige.navic.reader.ReaderWhispersyncCueMapState
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.readerNativeReaderSwipeAction
import paige.navic.reader.readerPageGestureShouldShowBusyFeedback
import paige.navic.reader.readerPagePreparationState
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.reader.readerPublicationCacheRoot
import paige.navic.reader.readerRendererBusyFeedbackCanStartMinimumTimer
import paige.navic.reader.readerRendererBusyFeedbackReadyDelayMillis
import paige.navic.reader.readerShellCoverSwipeAction
import paige.navic.reader.readerTapZonePageTurnDirectionFor
import paige.navic.reader.retainsPresentationIdentity
import paige.navic.reader.toPresentationFacts
import paige.navic.reader.withRendererReadiness
import paige.navic.util.core.Logger
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val KomikkuReaderNativeFrameHostTag = "KomikkuReaderNativeFrameHost"
private const val PageTurnPrewarmRequiredStableFrames = 2
private const val AndroidGestureDoubleTapMinTimeMillis = 40L
private val ReaderPageDiagnosticSessionIds = AtomicLong()

internal class ReaderPassiveRasterRendererLossFence {
	private var currentIdentity: Any? = null

	fun replace(identity: Any) {
		currentIdentity = identity
	}

	fun isCurrent(identity: Any): Boolean = currentIdentity === identity

	fun clear() {
		currentIdentity = null
	}
}

internal data class ReaderNativePresentationLayerVisibility(
	val shellCover: Boolean,
	val preparationShield: Boolean
)

internal data class ReaderNativePresentationApplication(
	val decision: ReaderPresentationDecision,
	val layers: ReaderNativePresentationLayerVisibility
) {
	val inputPolicy: ReaderPresentationInputPolicy
		get() = decision.inputPolicy
	val preparation: ReaderPreparationPresentation
		get() = decision.preparationPresentation
	val diagnostic: ReaderDiagnosticPresentation
		get() = decision.diagnosticPresentation
}

internal fun readerNativePresentationApplication(
	decision: ReaderPresentationDecision
): ReaderNativePresentationApplication {
	val shellCover = decision.layer == ReaderPresentationLayer.ShellCover
	return ReaderNativePresentationApplication(
		decision = decision,
		layers = ReaderNativePresentationLayerVisibility(
			shellCover = shellCover,
			preparationShield =
				!shellCover &&
					decision.layer == ReaderPresentationLayer.Neutral &&
					decision.preparationPresentation is ReaderPreparationPresentation.Blocking
		)
	)
}

internal fun readerPresentationBindingEvent(
	lastReportedBinding: ReaderPresentationBinding?,
	currentBinding: ReaderPresentationBinding,
	publicationOpenPending: Boolean,
	relocationPending: Boolean
): ReaderPresentationEvent? {
	if (publicationOpenPending || relocationPending) return null
	val previousBinding = lastReportedBinding ?: return null
	if (previousBinding == currentBinding) return null
	if (previousBinding.foliateSessionId != currentBinding.foliateSessionId) return null
	if (previousBinding.publicationGeneration != currentBinding.publicationGeneration) return null
	if (currentBinding.isExactHostRendererCompletionOf(previousBinding)) {
		return ReaderPresentationEvent.BindingCompleted(previousBinding, currentBinding)
	}
	if (
		previousBinding.destinationCommitIdentity != currentBinding.destinationCommitIdentity
	) return null
	return ReaderPresentationEvent.BindingReplaced(previousBinding, currentBinding)
}

private fun ReaderPresentationBinding.isExactHostRendererCompletionOf(
	previousBinding: ReaderPresentationBinding
): Boolean = previousBinding.rasterGeneration == null &&
	previousBinding.textureGeneration == null &&
	rasterGeneration != null &&
	textureGeneration != null &&
	foliateSessionId == previousBinding.foliateSessionId &&
	publicationGeneration == previousBinding.publicationGeneration &&
	viewportGeneration == previousBinding.viewportGeneration &&
	profileGeneration == previousBinding.profileGeneration &&
	destinationCommitIdentity == previousBinding.destinationCommitIdentity &&
	preparationGeneration == previousBinding.preparationGeneration

private fun ReaderPresentationBinding.hasAnyHostRendererIdentity(): Boolean =
	rasterGeneration != null || textureGeneration != null

private fun ReaderPresentationBinding.hasCompleteHostRendererIdentity(): Boolean =
	rasterGeneration != null && textureGeneration != null

private fun ReaderPresentationBinding.isCausalHostDestinationSuccessorOf(
	previousBinding: ReaderPresentationBinding
): Boolean {
	val previousDestination = previousBinding.destinationCommitIdentity ?: return false
	val destination = destinationCommitIdentity ?: return false
	return foliateSessionId == previousBinding.foliateSessionId &&
		publicationGeneration == previousBinding.publicationGeneration &&
		viewportGeneration == previousBinding.viewportGeneration &&
		profileGeneration == previousBinding.profileGeneration &&
		(
			preparationGeneration == previousBinding.preparationGeneration ||
				previousBinding.hasCompleteHostRendererIdentity() &&
				hasCompleteHostRendererIdentity() &&
				preparationGeneration != null
		) &&
		destination.commitSequence > previousDestination.commitSequence &&
		(!hasAnyHostRendererIdentity() || hasCompleteHostRendererIdentity())
}

private fun ReaderPresentationBinding.isCausalPendingPartialReplacementOf(
	previousBinding: ReaderPresentationBinding
): Boolean = !previousBinding.hasAnyHostRendererIdentity() &&
	!hasAnyHostRendererIdentity() &&
	foliateSessionId == previousBinding.foliateSessionId &&
	publicationGeneration == previousBinding.publicationGeneration &&
	destinationCommitIdentity == previousBinding.destinationCommitIdentity &&
	preparationGeneration == previousBinding.preparationGeneration &&
	viewportGeneration >= previousBinding.viewportGeneration &&
	profileGeneration >= previousBinding.profileGeneration &&
	(
		viewportGeneration > previousBinding.viewportGeneration ||
			profileGeneration > previousBinding.profileGeneration
	)

private fun ReaderPresentationBinding.isSafeHostBindingReplacementOf(
	previousBinding: ReaderPresentationBinding
): Boolean {
	if (
		this == previousBinding ||
		foliateSessionId != previousBinding.foliateSessionId ||
		publicationGeneration != previousBinding.publicationGeneration
	) return false
	if (!previousBinding.hasAnyHostRendererIdentity() && !hasAnyHostRendererIdentity()) {
		return true
	}
	if (
		!previousBinding.hasCompleteHostRendererIdentity() ||
		!hasCompleteHostRendererIdentity() ||
		preparationGeneration == null
	) return false
	val previousDestination = previousBinding.destinationCommitIdentity
	val destination = destinationCommitIdentity
	if (destination == previousDestination) return true
	return (viewportGeneration != previousBinding.viewportGeneration ||
		profileGeneration != previousBinding.profileGeneration) &&
		previousDestination != null &&
		destination != null &&
		destination.commitSequence > previousDestination.commitSequence
}

internal class ReaderPresentationBindingReporter {
	var lastReportedBinding: ReaderPresentationBinding? = null
		private set
	private var pendingBinding: ReaderPresentationBinding? = null

	fun reset() {
		lastReportedBinding = null
		pendingBinding = null
	}

	fun update(
		confirmedTargetBinding: ReaderPresentationBinding?,
		currentBinding: ReaderPresentationBinding,
		publicationOpenPending: Boolean,
		relocationPending: Boolean
	): ReaderPresentationEvent? {
		if (confirmedTargetBinding != null && confirmedTargetBinding != lastReportedBinding) {
			lastReportedBinding = confirmedTargetBinding
			pendingBinding = null
		}
		if (currentBinding == lastReportedBinding || currentBinding == pendingBinding) return null
		val reportingBasis = pendingBinding ?: lastReportedBinding
		val event = when {
			publicationOpenPending -> ReaderPresentationEvent.PublicationOpened(currentBinding)
			reportingBasis == null -> null
			reportingBasis.foliateSessionId != currentBinding.foliateSessionId -> null
			reportingBasis.publicationGeneration != currentBinding.publicationGeneration -> null
			currentBinding.isExactHostRendererCompletionOf(reportingBasis) ->
				ReaderPresentationEvent.BindingCompleted(
					previousBinding = reportingBasis,
					binding = currentBinding
				)
			currentBinding.isCausalHostDestinationSuccessorOf(reportingBasis) ->
				ReaderPresentationEvent.FoliateRelocated(
					binding = currentBinding,
					acknowledgement = null
				).takeIf { relocationPending }
			pendingBinding != null &&
				!currentBinding.isCausalPendingPartialReplacementOf(reportingBasis) -> null
			currentBinding.isSafeHostBindingReplacementOf(reportingBasis) ->
				ReaderPresentationEvent.BindingReplaced(reportingBasis, currentBinding)
			else -> null
		}
		if (event != null) pendingBinding = currentBinding
		return event
	}
}

internal class ReaderPresentationEffectHandler(
	private val retryPreparation: (
		ReaderPresentationEffect.RetryPreparation
	) -> Boolean = { false },
	private val releaseStalePresentation: (
		ReaderPresentationEffect.ReleaseStalePresentation
	) -> Boolean
) {
	private val handled = mutableSetOf<ReaderPresentationEffectIdentity>()

	fun deliver(
		effects: List<ReaderPendingPresentationEffect>,
		decision: ReaderPresentationDecision,
		onHandled: (ReaderPresentationEffectIdentity) -> Unit
	): Boolean {
		val attempted = mutableSetOf<ReaderPresentationEffectIdentity>()
		var allHandled = true
		effects.forEach { pending ->
			if (pending.identity in handled || !attempted.add(pending.identity)) return@forEach
			when (val effect = pending.effect) {
				is ReaderPresentationEffect.ReleaseStalePresentation -> {
					if (
						decision.retainsPresentationIdentity(effect.token, effect.binding)
					) {
						allHandled = false
						return@forEach
					}
					val released = decision.targetsRendererDeckAlias(effect.binding) || try {
						releaseStalePresentation(effect)
					} catch (_: Throwable) {
						false
					}
					if (!released) {
						allHandled = false
						return@forEach
					}
				}
				is ReaderPresentationEffect.RetryPreparation -> {
					val currentRetryToken = when (val transition = decision.requiredTransition) {
						is ReaderRequiredTransition.CommitShellCover -> transition.token
						is ReaderRequiredTransition.PresentNativePage -> transition.token
						is ReaderRequiredTransition.ExposeLiveEngine -> transition.token
						ReaderRequiredTransition.None -> null
					}
					val diagnostic = decision.diagnosticPresentation
					val retryStillCurrent =
						decision.targetBinding == effect.binding &&
						currentRetryToken == effect.token &&
							diagnostic is ReaderDiagnosticPresentation.Failure &&
							diagnostic.reason == ReaderPresentationFailureReason.PreparationFailed &&
							diagnostic.retryable
					val retried = !retryStillCurrent || try {
						retryPreparation(effect)
					} catch (_: Throwable) {
						false
					}
					if (!retried) {
						allHandled = false
						return@forEach
					}
				}
			}
			val acknowledged = try {
				onHandled(pending.identity)
				true
			} catch (_: Throwable) {
				false
			}
			if (acknowledged) {
				handled += pending.identity
			} else {
				allHandled = false
			}
		}
		return allHandled
	}
}

internal class ReaderPresentationViewerReplacementFence {
	private var rejectedRasterProfileEpoch: Long? = null

	var isPending: Boolean = false
		private set

	fun begin(rasterProfileEpoch: Long?) {
		isPending = true
		observeRasterProfileEpoch(rasterProfileEpoch)
	}

	fun observeRasterProfileEpoch(rasterProfileEpoch: Long?) {
		if (!isPending || rasterProfileEpoch == null) return
		rejectedRasterProfileEpoch = maxOf(
			rejectedRasterProfileEpoch ?: rasterProfileEpoch,
			rasterProfileEpoch
		)
	}

	fun completeAfterViewerInvalidation() {
		isPending = false
	}

	fun admits(deck: ReaderPagePreparedActiveDeck): Boolean {
		if (isPending) return false
		val rejectedEpoch = rejectedRasterProfileEpoch ?: return true
		return deck.rasterProfileEpoch > rejectedEpoch
	}
}

internal class ReaderStartupShellHandoffGate {
	private var nextAttempt = 0L
	private var activeAttempt: Long? = null
	private var eligible = true
	private var preparedHandoff = false

	val attemptInFlight: Boolean
		get() = activeAttempt != null

	fun consumesCanvasShellPageAction(
		shellVisible: Boolean,
		canvasEnabled: Boolean
	): Boolean = (eligible || preparedHandoff) && shellVisible && canvasEnabled

	fun beginAttempt(
		shellVisible: Boolean,
		canvasEnabled: Boolean,
		rasterPhase: ReaderPagePreparationPhase,
		textureDeck: ReaderTextureDeckState
	): Long? {
		if (
			!eligible ||
			activeAttempt != null ||
			!shellVisible ||
			!canvasEnabled ||
			rasterPhase != ReaderPagePreparationPhase.Ready ||
			textureDeck != ReaderTextureDeckState.Ready
		) {
			return null
		}
		val attempt = Math.incrementExact(nextAttempt)
		nextAttempt = attempt
		activeAttempt = attempt
		return attempt
	}

	fun completeAttempt(
		attempt: Long,
		shellVisible: Boolean,
		canvasEnabled: Boolean,
		rasterPhase: ReaderPagePreparationPhase,
		textureDeck: ReaderTextureDeckState,
		presentationCommitted: Boolean = true,
		onPrepared: () -> Unit,
		onRejected: () -> Unit
	) {
		if (activeAttempt != attempt) return
		activeAttempt = null
		val current = presentationCommitted &&
			eligible &&
			shellVisible &&
			canvasEnabled &&
			rasterPhase == ReaderPagePreparationPhase.Ready &&
			textureDeck == ReaderTextureDeckState.Ready
		if (!current) {
			onRejected()
			return
		}
		eligible = false
		preparedHandoff = true
		onPrepared()
	}

	fun rejectAttempt(attempt: Long) {
		if (activeAttempt == attempt) activeAttempt = null
	}

	fun consumePreparedHandoff(): Boolean {
		eligible = false
		if (!preparedHandoff) return false
		preparedHandoff = false
		return true
	}

	fun resetForNewViewer() {
		activeAttempt = null
		eligible = true
		preparedHandoff = false
	}

	fun close() {
		activeAttempt = null
		eligible = false
		preparedHandoff = false
	}
}

internal class ReaderRetainedValidatedPresentationOwnership {
	private var retainedRendererPresentation = false

	fun onRendererReadinessChanged(textureDeck: ReaderTextureDeckState) {
		retainedRendererPresentation = when (textureDeck) {
			ReaderTextureDeckState.Ready -> true
			ReaderTextureDeckState.Preparing,
			ReaderTextureDeckState.Settling -> retainedRendererPresentation
			ReaderTextureDeckState.Empty,
			ReaderTextureDeckState.Failed -> false
		}
	}

	fun hasPresentation(staticRasterShieldOwnership: Boolean): Boolean =
		retainedRendererPresentation || staticRasterShieldOwnership
}

internal fun readerWhispersyncAuthorityRestorationRequested(
	previousActive: Boolean,
	previousAnchorAvailable: Boolean,
	active: Boolean,
	anchorAvailable: Boolean
): Boolean =
	active &&
		!anchorAvailable &&
		(!previousActive || previousAnchorAvailable)

internal fun readerHostPagePreparationState(
	pageTurnCanvasEnabled: Boolean,
	pageTurnContentReady: Boolean,
	rasterState: ReaderPagePreparationState,
	rendererState: ReaderPageRendererReadinessState
): ReaderPagePreparationState = when {
	!pageTurnContentReady -> readerPagePreparationState(
		phase = ReaderPagePreparationPhase.Idle,
		requiredCount = 0,
		completedCount = 0,
		interactiveRequiredCount = 0,
		interactiveCompletedCount = 0,
		readiness = ReaderPageReadinessState(
			interaction = ReaderPageInteractionState.BlockingInitialPreparation
		)
	)
	pageTurnCanvasEnabled -> rasterState.withRendererReadiness(rendererState)
	else -> readerPagePreparationState(
		phase = ReaderPagePreparationPhase.Ready,
		requiredCount = 0,
		completedCount = 0,
		interactiveRequiredCount = 0,
		interactiveCompletedCount = 0,
		readiness = ReaderPageReadinessState(
			textureDeck = ReaderTextureDeckState.Ready,
			interaction = ReaderPageInteractionState.Ready
		)
	)
}

internal fun dispatchClaimedReaderPageCurlEvent(
	event: MotionEvent,
	dispatch: (MotionEvent) -> ReaderPageCurlDispatchResult
): ReaderPageCurlDispatchResult {
	if (event.actionMasked != MotionEvent.ACTION_UP) return dispatch(event)
	val moveEvent = MotionEvent.obtain(event)
	moveEvent.action = MotionEvent.ACTION_MOVE
	return try {
		when (val moveResult = dispatch(moveEvent)) {
			ReaderPageCurlDispatchResult.Accepted -> dispatch(event)
			ReaderPageCurlDispatchResult.TerminalPublished -> moveResult
		}
	} finally {
		moveEvent.recycle()
	}
}

@Composable
actual fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	chromeOverlayVisible: Boolean,
	presentationDecision: ReaderPresentationDecision,
	legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext,
	presentationEffects: List<ReaderPendingPresentationEffect>,
	onPresentationEffectHandled: (ReaderPresentationEffectIdentity) -> Unit,
	onPresentationEvent: (ReaderPresentationEvent) -> Unit,
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
	onViewerAction: (KomikkuNavigationRegion) -> Unit,
	onPageTurnBoundary: (ReaderPageTurnDirection) -> Unit,
	onReadableDragPreview: (deltaX: Float, deltaY: Float, viewWidth: Int, viewHeight: Int, phase: ReaderPageDragPreviewPhase) -> Unit,
	onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit,
	modifier: Modifier,
	viewerContent: @Composable () -> Unit,
	composeOverlay: @Composable () -> Unit
) {
	val currentViewerContent by rememberUpdatedState(viewerContent)
	val currentComposeOverlay by rememberUpdatedState(composeOverlay)
	val currentOnViewerAction by rememberUpdatedState(onViewerAction)
	val currentOnPageTurnBoundary by rememberUpdatedState(onPageTurnBoundary)
	val currentOnReadableDragPreview by rememberUpdatedState(onReadableDragPreview)
	val currentOnContentLongPress by rememberUpdatedState(onContentLongPress)
	val currentOnWhispersyncCueMapHoldOutcome by rememberUpdatedState(onWhispersyncCueMapHoldOutcome)
	val currentOnWhispersyncCueMapSeekRequested by rememberUpdatedState(onWhispersyncCueMapSeekRequested)
	val currentOnStartupShellPrepared by rememberUpdatedState(onStartupShellPrepared)
	val currentOnPresentationEffectHandled by rememberUpdatedState(onPresentationEffectHandled)
	val currentOnPresentationEvent by rememberUpdatedState(onPresentationEvent)
	var rendererBusyFeedbackToken by remember { mutableLongStateOf(0L) }
	var nativeFrameRoot by remember { mutableStateOf<KomikkuReaderNativeFrameRoot?>(null) }
	val hostedComposeOverlay: @Composable () -> Unit = {
		val rendererBusyFeedbackVisibility = remember { MutableTransitionState(false) }
		var fullyVisibleRejectionToken by remember { mutableLongStateOf(0L) }
		val currentNativeFrameRoot by rememberUpdatedState(nativeFrameRoot)
		val activeToken = rendererBusyFeedbackToken

		LaunchedEffect(activeToken) {
			if (activeToken == 0L) return@LaunchedEffect
			rendererBusyFeedbackVisibility.targetState = true
			while (
				activeToken == rendererBusyFeedbackToken &&
				(
					!rendererBusyFeedbackVisibility.isIdle ||
					!rendererBusyFeedbackVisibility.currentState ||
					!rendererBusyFeedbackVisibility.targetState
				)
			) {
				withFrameNanos { }
			}
			if (activeToken == rendererBusyFeedbackToken) {
				fullyVisibleRejectionToken = activeToken
			}
		}

		LaunchedEffect(activeToken, fullyVisibleRejectionToken) {
			if (!readerRendererBusyFeedbackCanStartMinimumTimer(
					activeRejectionToken = activeToken,
					fullyVisibleRejectionToken = fullyVisibleRejectionToken
				)
			) return@LaunchedEffect

			var elapsedMillis = 0L
			val readyDelayMillis = readerRendererBusyFeedbackReadyDelayMillis(elapsedMillis)
			delay(readyDelayMillis)
			elapsedMillis += readyDelayMillis
			while (
				activeToken == rendererBusyFeedbackToken &&
				fullyVisibleRejectionToken == activeToken &&
				rendererBusyFeedbackVisibility.targetState
			) {
				if (currentNativeFrameRoot?.canAcceptNewPointer() == true) {
					rendererBusyFeedbackVisibility.targetState = false
					return@LaunchedEffect
				}
				if (elapsedMillis >= ReaderRendererBusyFeedbackMaximumMillis) {
					rendererBusyFeedbackVisibility.targetState = false
					return@LaunchedEffect
				}
				val nextDelayMillis = minOf(
					50L,
					ReaderRendererBusyFeedbackMaximumMillis - elapsedMillis
				)
				delay(nextDelayMillis)
				elapsedMillis += nextDelayMillis
			}
		}

		Box(modifier = Modifier.fillMaxSize()) {
			currentComposeOverlay()
		}
		ReaderRendererBusyPopup(
			visibleState = rendererBusyFeedbackVisibility,
			bottomOffset = 96.dp
		)
	}
	val onRendererBusyGestureRejected = {
		rendererBusyFeedbackToken += 1L
	}

	AndroidView(
		modifier = modifier,
		factory = { context ->
			KomikkuReaderNativeFrameRoot(context).apply {
				nativeFrameRoot = this
				setOnStartupShellPrepared { currentOnStartupShellPrepared() }
				setViewerContent(viewerKey) { currentViewerContent() }
				setComposeOverlay(hostedComposeOverlay)
				setOnRendererBusyGestureRejected(onRendererBusyGestureRejected)
				setChromeOverlayVisible(chromeOverlayVisible)
				setShellCover(
					shellCoverUrl,
					shellCoverTitle,
					coverBackdropEnabled
				)
				setLegacyLiveCompatibilityContext(legacyLiveCompatibilityContext)
				setPresentationDecision(
					presentationDecision,
					destinationCommitIdentity,
					viewerKey
				) { event -> currentOnPresentationEvent(event) }
				handlePresentationEffects(
					presentationEffects,
					presentationDecision
				) { identity -> currentOnPresentationEffectHandled(identity) }
				setViewerLayerPaint(grayscaleEnabled, invertedColors)
				setVerticalPageDragPreview(verticalPageDragPreview)
				setPageTurnBitmapQuality(pageTurnBitmapQuality)
				setPageTurnCanvasEnabled(pageTurnCanvasEnabled)
				setPageTurnReadingDirection(pageTurnReadingDirection)
				setPageTurnSnapshotKey(pageTurnSnapshotKey)
				setPageTurnContentReadyKey(pageTurnContentReadyKey)
				setPageTurnPaginationStatus(pageTurnPaginationStatus)
				pageTurnFoliateSessionId?.let { sessionId ->
					setPageTurnVisualLocation(
						pageTurnVisualPageIndex,
						pageTurnVisualLocationReason,
						sessionId,
						pageTurnSettlementAck
					)
				}
				setWhispersyncOverlay(
					whispersyncOverlayActive,
					whispersyncAnchorReceipt,
					whispersyncHighlightColorArgb
				)
				setWhispersyncCueMap(
					whispersyncCueMapState,
					onHoldOutcome = { sourceOrdinal, outcome ->
						currentOnWhispersyncCueMapHoldOutcome(sourceOrdinal, outcome)
					},
					onSeekRequested = { sourceOrdinal ->
						currentOnWhispersyncCueMapSeekRequested(sourceOrdinal)
					}
				)
				setOnViewerAction { action -> currentOnViewerAction(action) }
				setOnPageTurnBoundary { direction -> currentOnPageTurnBoundary(direction) }
				setOnReadableDragPreview { deltaX, deltaY, width, height, phase ->
					currentOnReadableDragPreview(deltaX, deltaY, width, height, phase)
				}
				setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
			}
		},
		update = { root ->
			root.setOnStartupShellPrepared { currentOnStartupShellPrepared() }
			root.setNavigation(navigator)
			root.setNavigationOverlayVisible(navigationOverlayVisible)
			root.setChromeOverlayVisible(chromeOverlayVisible)
			root.setShellCover(
				shellCoverUrl,
				shellCoverTitle,
				coverBackdropEnabled
			)
			root.setViewerLayerPaint(grayscaleEnabled, invertedColors)
			root.setVerticalPageDragPreview(verticalPageDragPreview)
			root.setPageTurnBitmapQuality(pageTurnBitmapQuality)
			root.setPageTurnCanvasEnabled(pageTurnCanvasEnabled)
			root.setPageTurnReadingDirection(pageTurnReadingDirection)
			root.setPageTurnSnapshotKey(pageTurnSnapshotKey)
			root.setPageTurnContentReadyKey(pageTurnContentReadyKey)
			root.setPageTurnPaginationStatus(pageTurnPaginationStatus)
			root.setLegacyLiveCompatibilityContext(legacyLiveCompatibilityContext)
			root.setPresentationDecision(
				presentationDecision,
				destinationCommitIdentity,
				viewerKey
			) { event -> currentOnPresentationEvent(event) }
			root.handlePresentationEffects(
				presentationEffects,
				presentationDecision
			) { identity -> currentOnPresentationEffectHandled(identity) }
			pageTurnFoliateSessionId?.let { sessionId ->
				root.setPageTurnVisualLocation(
					pageTurnVisualPageIndex,
					pageTurnVisualLocationReason,
					sessionId,
					pageTurnSettlementAck
				)
			}
			root.setWhispersyncOverlay(
				whispersyncOverlayActive,
				whispersyncAnchorReceipt,
				whispersyncHighlightColorArgb
			)
			root.setWhispersyncCueMap(
				whispersyncCueMapState,
				onHoldOutcome = { sourceOrdinal, outcome ->
					currentOnWhispersyncCueMapHoldOutcome(sourceOrdinal, outcome)
				},
				onSeekRequested = { sourceOrdinal ->
					currentOnWhispersyncCueMapSeekRequested(sourceOrdinal)
				}
			)
			root.setViewerContent(viewerKey) { currentViewerContent() }
			root.setComposeOverlay(hostedComposeOverlay)
			root.setOnRendererBusyGestureRejected(onRendererBusyGestureRejected)
			root.setOnViewerAction { action -> currentOnViewerAction(action) }
			root.setOnPageTurnBoundary { direction -> currentOnPageTurnBoundary(direction) }
			root.setOnReadableDragPreview { deltaX, deltaY, width, height, phase ->
				currentOnReadableDragPreview(deltaX, deltaY, width, height, phase)
			}
			root.setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
		},
		onRelease = { root ->
			if (nativeFrameRoot === root) nativeFrameRoot = null
			root.closeReader()
		}
	)
}

private class ReaderNestedComposeDisposalQueue {
	private val handler = Handler(Looper.getMainLooper())
	private val pending = linkedSetOf<ComposeView>()

	fun enqueue(view: ComposeView?) {
		if (view == null || !pending.add(view)) return
		val accepted = handler.post {
			pending.remove(view)
			view.setViewCompositionStrategy(
				ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
			)
			view.disposeComposition()
		}
		if (!accepted) pending.remove(view)
	}
}

private class KomikkuReaderNativeFrameRoot(context: Context) : FrameLayout(context) {
	private val readerContainer = FrameLayout(context)
	private val viewerContainer = KomikkuReaderNativeViewerContainer(context)
	private val shellCoverView = KomikkuReaderNativeShellCoverView(context)
	private val pagePreparationShieldView = View(context).apply {
		setBackgroundColor(Color.rgb(32, 35, 41))
		isClickable = false
		isFocusable = false
		visibility = GONE
	}
	private val navigationOverlay = KomikkuReaderNativeNavigationOverlayView(context)
	private val composeOverlay = ComposeView(context)
	private val nestedCompositionDisposalQueue = ReaderNestedComposeDisposalQueue()
	private var currentViewerKey: ReaderViewerKey? = null
	private var currentViewerComposeView: ComposeView? = null
	private var shellCoverVisible: Boolean = false
	private var presentationViewerKey: ReaderViewerKey? = null
	private var presentationDecision: ReaderPresentationDecision? = null
	private var lastNativeCoverVisibilityTrace: String? = null
	private val presentationEffectHandler = ReaderPresentationEffectHandler(
		releaseStalePresentation = viewerContainer::releaseStalePresentation,
		retryPreparation = viewerContainer::retryPreparation
	)
	private val shellCoverLayerController = ReaderShellCoverLayerController(
		onPrepareBehindPredecessor = {
			viewerContainer.prepareShellCoverForCommit(shellCoverView)
		},
		onHidePreparedCover = {
			viewerContainer.cancelShellCoverCommitPreparation(shellCoverView)
		},
		onSelectCover = {
			viewerContainer.selectShellCover(
				shellCoverView,
				preserveNativePresentationProof = true
			)
		},
		onInvalidateRasterDeck = viewerContainer::invalidateShellCoverRasterDeck,
		onInvalidatePreparation = viewerContainer::invalidateShellCoverPreparation
	)
	private var preparedShellCoverGeneration: Long? = null
	private var onPresentationEvent: (ReaderPresentationEvent) -> Unit = {}
	private val presentationCommitHost = object : ReaderPresentationCommitHost {
		override val isAttachedToWindow: Boolean
			get() = shellCoverView.isAttachedToWindow
		override val currentPresentationBinding: ReaderPresentationBinding?
			get() = viewerContainer.currentPresentationBinding()
		override val currentShellCoverGeneration: Long?
			get() = preparedShellCoverGeneration
		override val shellCoverSelected: Boolean
			get() = shellCoverVisible
		override val measuredViewportWidth: Int
			get() = shellCoverView.width
		override val measuredViewportHeight: Int
			get() = shellCoverView.height

		override fun applyPresentationDecision(decision: ReaderPresentationDecision) {
			this@KomikkuReaderNativeFrameRoot.applyPresentationDecision(decision)
		}

		override fun prepareOpaqueShellCover(coverGeneration: Long) {
			preparedShellCoverGeneration = coverGeneration
			shellCoverLayerController.prepareCoverBehindPredecessor()
			updateNativeCoverVisibility()
		}

		override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) {
			if (preparedShellCoverGeneration != coverGeneration) return
			preparedShellCoverGeneration = null
			shellCoverLayerController.hidePreparedCover()
			updateNativeCoverVisibility()
		}

		override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) {
			if (preparedShellCoverGeneration != coverGeneration) return
			preparedShellCoverGeneration = null
			updateNativeCoverVisibility()
		}

		override fun registerShellCoverDrawListener(
			onDraw: () -> Unit
		): ReaderPresentationDrawRegistration {
			val observer = shellCoverView.viewTreeObserver
			var drawDispatchActive = false
			val listener = ViewTreeObserver.OnDrawListener {
				drawDispatchActive = true
				try {
					onDraw()
				} finally {
					drawDispatchActive = false
				}
			}
			var removed = false
			observer.addOnDrawListener(listener)
			return ReaderPresentationDrawRegistration {
				if (removed) return@ReaderPresentationDrawRegistration
				removed = true
				val removeListener = {
					val currentObserver = if (observer.isAlive) {
						observer
					} else {
						shellCoverView.viewTreeObserver
					}
					if (currentObserver.isAlive) {
						currentObserver.removeOnDrawListener(listener)
					}
				}
				if (drawDispatchActive) {
					shellCoverView.post { removeListener() }
				} else {
					removeListener()
				}
			}
		}

		override fun postShellCoverAnimationFrame(onFrame: () -> Unit) {
			shellCoverView.postOnAnimation(onFrame)
		}
	}
	private val presentationHostBridge = ReaderPresentationHostBridge(
		presentationCommitHost
	) { event -> onPresentationEvent(event) }

	init {
		setBackgroundColor(Color.rgb(32, 35, 41))
		shellCoverView.onGeometryChanged = {
			presentationDecision?.let(presentationHostBridge::update)
		}

		viewerContainer.setShellCoverView(shellCoverView)
		readerContainer.addView(
			viewerContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		readerContainer.addView(
			pagePreparationShieldView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)

		navigationOverlay.isClickable = false
		navigationOverlay.isFocusable = false
		navigationOverlay.visibility = GONE
		shellCoverView.isClickable = false
		shellCoverView.isFocusable = false
		shellCoverView.visibility = GONE
		composeOverlay.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
		)
		composeOverlay.isClickable = false
		composeOverlay.isFocusable = false
		composeOverlay.elevation = 32f
		composeOverlay.translationZ = 32f

		addView(
			readerContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			navigationOverlay,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			composeOverlay,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun setNavigation(navigator: KomikkuReaderNavigator) {
		viewerContainer.navigator = navigator
		navigationOverlay.setNavigation(navigator)
	}

	fun setNavigationOverlayVisible(visible: Boolean) {
		navigationOverlay.visibility = if (visible) VISIBLE else GONE
	}

	fun setChromeOverlayVisible(visible: Boolean) {
		viewerContainer.chromeOverlayVisible = visible
		if (visible) viewerContainer.cancelWhispersyncCueMapForChrome()
	}

	fun setPresentationDecision(
		decision: ReaderPresentationDecision,
		destinationCommitIdentity: ReaderDestinationCommitIdentity?,
		viewerKey: ReaderViewerKey,
		onEvent: (ReaderPresentationEvent) -> Unit
	) {
		if (presentationViewerKey != viewerKey) {
			presentationViewerKey = viewerKey
			viewerContainer.onPresentationPublicationChanged(
				viewerReplacementPending = currentViewerKey != viewerKey
			)
		}
		onPresentationEvent = onEvent
		viewerContainer.setPresentationDecision(
			decision = decision,
			destinationCommitIdentity = destinationCommitIdentity,
			onEvent = onEvent
		)
		presentationHostBridge.update(decision)
	}

	fun handlePresentationEffects(
		effects: List<ReaderPendingPresentationEffect>,
		decision: ReaderPresentationDecision,
		onHandled: (ReaderPresentationEffectIdentity) -> Unit
	) {
		presentationEffectHandler.deliver(effects, decision, onHandled)
	}

	fun setShellCover(
		coverUrl: String?,
		title: String,
		coverBackdropEnabled: Boolean
	) {
		shellCoverView.setShellCover(
			coverUrl = coverUrl,
			title = title,
			coverBackdropEnabled = coverBackdropEnabled
		)
	}

	private fun applyPresentationDecision(decision: ReaderPresentationDecision) {
		val application = readerNativePresentationApplication(decision)
		presentationDecision = application.decision
		shellCoverVisible = application.layers.shellCover
		viewerContainer.applyPresentationDecision(application.decision)
		if (application.layers.shellCover) {
			shellCoverLayerController.selectCover(
				preserveNativePresentationProof = true
			)
		} else {
			shellCoverLayerController.coverHidden()
			viewerContainer.setShellCoverVisible(
				visible = false,
				preserveNativePresentationProof = true
			)
		}
		updateNativeCoverVisibility(application)
	}

	private fun updateNativeCoverVisibility(
		application: ReaderNativePresentationApplication? = presentationDecision?.let(
			::readerNativePresentationApplication
		)
	) {
		val layers = application?.layers
			?: ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			)
		val shellCoverPrepared = preparedShellCoverGeneration != null
		shellCoverView.visibility =
			if (layers.shellCover || shellCoverPrepared) VISIBLE else GONE
		shellCoverView.isClickable =
			layers.shellCover &&
				application?.inputPolicy == ReaderPresentationInputPolicy.ShellCover
		pagePreparationShieldView.visibility =
			if (layers.preparationShield) VISIBLE else GONE
		val trace =
			"authority=${application?.decision?.authority?.let { it::class.simpleName } ?: "None"} " +
				"layer=${application?.decision?.layer?.name ?: "None"} " +
				"shell=${layers.shellCover} coverPrepared=$shellCoverPrepared " +
				"preparationShield=${layers.preparationShield}"
		if (lastNativeCoverVisibilityTrace != trace) {
			lastNativeCoverVisibilityTrace = trace
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader native presentation layers $trace"
			)
		}
	}

	fun setOnStartupShellPrepared(onPrepared: () -> Unit) {
		viewerContainer.onStartupShellPrepared = onPrepared
	}

	fun setViewerLayerPaint(grayscaleEnabled: Boolean, invertedColors: Boolean) {
		val paint = if (grayscaleEnabled || invertedColors) {
			getCombinedReaderLayerPaint(grayscale = grayscaleEnabled, invertedColors = invertedColors)
		} else {
			null
		}
		viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
		composeOverlay.bringToFront()
	}

	fun setComposeOverlay(content: @Composable () -> Unit) {
		composeOverlay.setContent(content)
		composeOverlay.bringToFront()
	}

	fun setOnRendererBusyGestureRejected(onRejected: () -> Unit) {
		viewerContainer.onRendererBusyGestureRejected = onRejected
	}

	fun setOnViewerAction(onAction: (KomikkuNavigationRegion) -> Unit) {
		viewerContainer.onAction = onAction
	}

	fun setOnPageTurnBoundary(onBoundary: (ReaderPageTurnDirection) -> Unit) {
		viewerContainer.onPageTurnBoundary = onBoundary
	}

	fun setVerticalPageDragPreview(verticalPageDragPreview: Boolean) {
		viewerContainer.setVerticalPageDragPreview(verticalPageDragPreview)
	}

	fun setLegacyLiveCompatibilityContext(
		context: ReaderLegacyLiveCompatibilityContext
	) {
		viewerContainer.setLegacyLiveCompatibilityContext(context)
	}

	fun setPageTurnCanvasEnabled(enabled: Boolean) {
		viewerContainer.setPageTurnCanvasEnabled(enabled)
	}

	fun setPageTurnReadingDirection(direction: String?) {
		viewerContainer.setPageTurnReadingDirection(direction)
	}

	fun setPageTurnBitmapQuality(value: String?) {
		viewerContainer.setPageTurnBitmapQuality(value)
	}

	fun setPageTurnSnapshotKey(snapshotKey: Int) {
		viewerContainer.setPageTurnSnapshotKey(snapshotKey)
	}

	fun setPageTurnContentReadyKey(contentReadyKey: String?) {
		viewerContainer.setPageTurnContentReadyKey(contentReadyKey)
	}

	fun setPageTurnPaginationStatus(status: String?) {
		viewerContainer.setPageTurnPaginationStatus(status)
	}

	fun setPageTurnVisualLocation(
		pageIndex: Int?,
		reason: String?,
		foliateSessionId: String,
		acknowledgement: ReaderPageTurnSettlementAck?
	) {
		viewerContainer.setPageTurnVisualLocation(
			pageIndex,
			reason,
			foliateSessionId,
			acknowledgement
		)
		presentationDecision?.let(presentationHostBridge::update)
	}

	fun setWhispersyncOverlay(
		active: Boolean,
		receipt: ReaderWhispersyncAnchorReceipt?,
		highlightColorArgb: Int
	) {
		viewerContainer.setWhispersyncOverlay(active, receipt, highlightColorArgb)
	}

	fun setWhispersyncCueMap(
		state: ReaderWhispersyncCueMapState,
		onHoldOutcome: (Int, ReaderWhispersyncCueMapHoldOutcome) -> Unit,
		onSeekRequested: (Int) -> Unit
	) {
		viewerContainer.setWhispersyncCueMap(state, onHoldOutcome, onSeekRequested)
	}

	fun setOnReadableDragPreview(
		onReadableDragPreview: (
			deltaX: Float,
			deltaY: Float,
			viewWidth: Int,
			viewHeight: Int,
			phase: ReaderPageDragPreviewPhase
		) -> Unit
	) {
		viewerContainer.onReadableDragPreview = onReadableDragPreview
	}

	fun setOnContentLongPress(onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit) {
		viewerContainer.onContentLongPress = onContentLongPress
	}

	fun setViewerContent(viewerKey: ReaderViewerKey, content: @Composable () -> Unit) {
		if (currentViewerKey != viewerKey || currentViewerComposeView == null) {
			val previousViewer = currentViewerComposeView
			val viewerView = ComposeView(context).apply {
				setViewCompositionStrategy(
					ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
				)
				setContent(content)
			}
			viewerContainer.replaceViewerContent(viewerView)
			viewerContainer.completePresentationViewerReplacement()
			currentViewerComposeView = viewerView
			currentViewerKey = viewerKey
			nestedCompositionDisposalQueue.enqueue(previousViewer)
		} else {
			currentViewerComposeView?.setContent(content)
		}
	}

	fun canAcceptNewPointer(): Boolean = viewerContainer.canAcceptNewPointer()

	fun closeReader() {
		presentationHostBridge.dispose()
		viewerContainer.closeReader()
		detachNestedCompositionViews()
		scheduleNestedCompositionDisposal()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		presentationDecision?.let(presentationHostBridge::update)
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		presentationDecision?.let(presentationHostBridge::update)
	}

	override fun onDetachedFromWindow() {
		presentationHostBridge.onHostDetached()
		super.onDetachedFromWindow()
		scheduleNestedCompositionDisposal()
	}

	private fun detachNestedCompositionViews() {
		viewerContainer.detachViewerContent(currentViewerComposeView)
		(composeOverlay.parent as? ViewGroup)?.removeView(composeOverlay)
	}

	private fun scheduleNestedCompositionDisposal() {
		nestedCompositionDisposalQueue.enqueue(currentViewerComposeView)
		nestedCompositionDisposalQueue.enqueue(composeOverlay)
		currentViewerComposeView = null
		currentViewerKey = null
	}
}

private class KomikkuReaderNativeShellCoverView(context: Context) : View(context) {
	private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val backdropImagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
		alpha = 124
		colorFilter = ColorMatrixColorFilter(
			ColorMatrix().apply {
				setSaturation(0.78f)
			}
		)
	}
	private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(142, 6, 5, 4)
	}
	private val source = Rect()
	private val destination = RectF()
	private val backdropDestination = RectF()
	private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		textSize = 42f
	}
	private var coverUrl: String? = null
	private var title: String = ""
	private var coverBackdropEnabled: Boolean = true
	private var bitmap: Bitmap? = null
	var onGeometryChanged: () -> Unit = {}

	fun setShellCover(coverUrl: String?, title: String, coverBackdropEnabled: Boolean) {
		if (this.coverUrl == coverUrl && this.title == title && this.coverBackdropEnabled == coverBackdropEnabled) return
		this.coverUrl = coverUrl
		this.title = title
		this.coverBackdropEnabled = coverBackdropEnabled
		bitmap = coverUrl
			?.let { context.readerShellCoverFileFor(it) }
			?.absolutePath
			?.let { path -> BitmapFactory.decodeFile(path) }
		invalidate()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		if (w != oldw || h != oldh) onGeometryChanged()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(Color.rgb(16, 14, 10))
		val currentBitmap = bitmap
		if (currentBitmap == null || currentBitmap.width <= 0 || currentBitmap.height <= 0) {
			val shellGeometry = resolveNativeReaderShellCoverGeometry(
				viewWidth = width,
				viewHeight = height,
				bitmapWidth = 720,
				bitmapHeight = 1000
			)
			canvas.drawText(title.ifBlank { "Cover" }, width / 2f, height / 2f, titlePaint)
			return
		}
		val shellGeometry = resolveNativeReaderShellCoverGeometry(
			viewWidth = width,
			viewHeight = height,
			bitmapWidth = currentBitmap.width,
			bitmapHeight = currentBitmap.height
		)
		if (coverBackdropEnabled) {
			drawDiffuseCoverBackdrop(canvas, currentBitmap, shellGeometry)
		}
		drawContainedNativeShellCover(canvas, currentBitmap, shellGeometry)
	}

	private fun drawDiffuseCoverBackdrop(
		canvas: Canvas,
		currentBitmap: Bitmap,
		shellGeometry: NativeReaderShellCoverGeometry
	) {
		val backdropRect = shellGeometry.backdropRect
		val scale = max(
			backdropRect.width() / currentBitmap.width.toFloat(),
			backdropRect.height() / currentBitmap.height.toFloat()
		)
		val drawWidth = currentBitmap.width * scale
		val drawHeight = currentBitmap.height * scale
		val left = backdropRect.centerX() - drawWidth / 2f
		val top = backdropRect.centerY() - drawHeight / 2f
		backdropDestination.set(left, top, left + drawWidth, top + drawHeight)
		source.set(0, 0, currentBitmap.width, currentBitmap.height)
		val checkpoint = canvas.save()
		canvas.clipRect(backdropRect)
		canvas.drawBitmap(currentBitmap, source, backdropDestination, backdropImagePaint)
		canvas.restoreToCount(checkpoint)
		canvas.drawRect(backdropRect, dimPaint)
	}

	private fun drawContainedNativeShellCover(
		canvas: Canvas,
		currentBitmap: Bitmap,
		shellGeometry: NativeReaderShellCoverGeometry
	) {
		destination.set(shellGeometry.foregroundImageRect)
		canvas.drawBitmap(currentBitmap, null, destination, imagePaint)
	}
}

private data class NativeReaderShellCoverGeometry(
	val backdropRect: RectF,
	val foregroundRect: RectF,
	val foregroundImageRect: RectF
)

private fun resolveNativeReaderShellCoverGeometry(
	viewWidth: Int,
	viewHeight: Int,
	bitmapWidth: Int,
	bitmapHeight: Int
): NativeReaderShellCoverGeometry {
	val resolvedWidth = max(1, viewWidth).toFloat()
	val resolvedHeight = max(1, viewHeight).toFloat()
	val foregroundRect = nativeShellCoverForegroundRect(
		viewWidth = resolvedWidth,
		viewHeight = resolvedHeight,
		bitmapWidth = max(1, bitmapWidth).toFloat(),
		bitmapHeight = max(1, bitmapHeight).toFloat()
	)
	return NativeReaderShellCoverGeometry(
		backdropRect = RectF(0f, 0f, resolvedWidth, resolvedHeight),
		foregroundRect = foregroundRect,
		foregroundImageRect = foregroundRect
	)
}

private fun nativeShellCoverForegroundRect(
	viewWidth: Float,
	viewHeight: Float,
	bitmapWidth: Float,
	bitmapHeight: Float
): RectF {
	val landscape = viewWidth > viewHeight
	val maxWidth = if (landscape) viewWidth * 0.38f else viewWidth * 0.72f
	val maxHeight = if (landscape) viewHeight * 0.86f else viewHeight * 0.78f
	val scale = min(
		maxWidth / bitmapWidth,
		maxHeight / bitmapHeight
	)
	val drawWidth = bitmapWidth * scale
	val drawHeight = bitmapHeight * scale
	val left = (viewWidth - drawWidth) / 2f
	val top = (viewHeight - drawHeight) / 2f
	return RectF(left, top, left + drawWidth, top + drawHeight)
}

private fun getCombinedReaderLayerPaint(grayscale: Boolean, invertedColors: Boolean): Paint =
	Paint().apply {
		colorFilter = ColorMatrixColorFilter(
			ColorMatrix().apply {
				if (grayscale) {
					setSaturation(0f)
				}
				if (invertedColors) {
					postConcat(
						ColorMatrix(
							floatArrayOf(
								-1f, 0f, 0f, 0f, 255f,
								0f, -1f, 0f, 0f, 255f,
								0f, 0f, -1f, 0f, 255f,
								0f, 0f, 0f, 1f, 0f
							)
						)
					)
				}
			}
		)
	}

private fun Context.readerShellCoverFileFor(coverUrl: String): File? {
	val expectedPrefix = "${ReaderWebRuntime.AssetLoaderOrigin}$ReaderPublicationCachePathPrefix"
	if (!coverUrl.startsWith(expectedPrefix)) return null
	val relativePath = coverUrl
		.removePrefix(expectedPrefix)
		.substringBefore("?")
		.substringBefore("#")
		.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
	val file = File(readerPublicationCacheRoot(this), relativePath)
	return file.takeIf { it.isFile }
}

private data class ReaderPageGestureDiagnosticContext(
	val startedAtMillis: Long,
	val downX: Float,
	var owner: ReaderPagePointerOwnership,
	var physicalDirection: ReaderPagePhysicalDirection? = null,
	var logicalDirection: ReaderPageTurnDirection? = null
)

internal enum class ReaderPagePhysicalDispatchMode {
	CueMap,
	ChromeOnly,
	Denied,
	Legacy,
	LegacyLive,
	PlayLikeCurl,
	ShellCover,
	LiveEngine
}

internal fun readerPagePhysicalDispatchMode(
	pageTurnCanvasEnabled: Boolean,
	presentationInputPolicy: ReaderPresentationInputPolicy,
	legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext =
		ReaderLegacyLiveCompatibilityContext.Denied(),
	nativePageUsesLegacyRoute: Boolean = false
): ReaderPagePhysicalDispatchMode {
	if (
		legacyLiveCompatibilityContext.lifecycle !=
		ReaderPresentationLifecycleState.Foreground
	) {
		return ReaderPagePhysicalDispatchMode.Denied
	}
	if (!pageTurnCanvasEnabled) {
		return when (presentationInputPolicy) {
			ReaderPresentationInputPolicy.RecoveryOnly -> if (
				legacyLiveCompatibilityContext is
				ReaderLegacyLiveCompatibilityContext.ColdSession
			) {
				ReaderPagePhysicalDispatchMode.LegacyLive
			} else {
				ReaderPagePhysicalDispatchMode.Denied
			}
			ReaderPresentationInputPolicy.ChromeOnly ->
				ReaderPagePhysicalDispatchMode.ChromeOnly
			ReaderPresentationInputPolicy.ShellCover ->
				ReaderPagePhysicalDispatchMode.ShellCover
			is ReaderPresentationInputPolicy.ClaimedCurl ->
				ReaderPagePhysicalDispatchMode.PlayLikeCurl
			is ReaderPresentationInputPolicy.NativePage,
			ReaderPresentationInputPolicy.LiveEngine ->
				ReaderPagePhysicalDispatchMode.Denied
		}
	}
	return when (presentationInputPolicy) {
		ReaderPresentationInputPolicy.ShellCover -> ReaderPagePhysicalDispatchMode.ShellCover
		is ReaderPresentationInputPolicy.NativePage -> if (nativePageUsesLegacyRoute) {
			ReaderPagePhysicalDispatchMode.Legacy
		} else {
			ReaderPagePhysicalDispatchMode.PlayLikeCurl
		}
		ReaderPresentationInputPolicy.LiveEngine -> ReaderPagePhysicalDispatchMode.LiveEngine
		ReaderPresentationInputPolicy.ChromeOnly -> ReaderPagePhysicalDispatchMode.ChromeOnly
		ReaderPresentationInputPolicy.RecoveryOnly,
		is ReaderPresentationInputPolicy.ClaimedCurl -> ReaderPagePhysicalDispatchMode.PlayLikeCurl
	}
}

internal interface ReaderPagePhysicalDispatchTarget {
	fun dispatchCueMap(event: MotionEvent): Boolean
	fun dispatchChromeOnly(event: MotionEvent): Boolean
	fun dispatchDenied(event: MotionEvent): Boolean
	fun dispatchLegacy(event: MotionEvent): Boolean
	fun dispatchLegacyLive(event: MotionEvent): Boolean
	fun dispatchPlayLikeCurl(event: MotionEvent): Boolean
	fun dispatchShellCover(event: MotionEvent): Boolean
	fun dispatchLiveEngine(event: MotionEvent): Boolean
}

internal fun readerDispatchPagePhysicalEvent(
	mode: ReaderPagePhysicalDispatchMode?,
	event: MotionEvent,
	target: ReaderPagePhysicalDispatchTarget,
	fallback: (MotionEvent) -> Boolean
): Boolean = when (mode) {
	ReaderPagePhysicalDispatchMode.CueMap -> target.dispatchCueMap(event)
	ReaderPagePhysicalDispatchMode.ChromeOnly -> target.dispatchChromeOnly(event)
	ReaderPagePhysicalDispatchMode.Denied -> target.dispatchDenied(event)
	ReaderPagePhysicalDispatchMode.Legacy -> target.dispatchLegacy(event)
	ReaderPagePhysicalDispatchMode.LegacyLive -> target.dispatchLegacyLive(event)
	ReaderPagePhysicalDispatchMode.PlayLikeCurl -> target.dispatchPlayLikeCurl(event)
	ReaderPagePhysicalDispatchMode.ShellCover -> target.dispatchShellCover(event)
	ReaderPagePhysicalDispatchMode.LiveEngine -> target.dispatchLiveEngine(event)
	null -> fallback(event)
}

internal data class ReaderLegacyLivePointerContext(
	val pageTurnCanvasEnabled: Boolean,
	val presentationDecision: ReaderPresentationDecision?,
	val compatibilityContext: ReaderLegacyLiveCompatibilityContext,
	val shellCoverVisible: Boolean
)

internal class ReaderLegacyLivePointerStream {
	private var activeContext: ReaderLegacyLivePointerContext? = null
	private var revoked = false

	fun begin(
		mode: ReaderPagePhysicalDispatchMode,
		context: ReaderLegacyLivePointerContext
	) {
		activeContext = context.takeIf {
			mode == ReaderPagePhysicalDispatchMode.LegacyLive
		}
		revoked = false
	}

	fun revokeIfContextChanged(context: ReaderLegacyLivePointerContext): Boolean {
		val active = activeContext ?: return false
		return if (!revoked && context != active) {
			revoked = true
			true
		} else {
			false
		}
	}

	fun revoke(): Boolean {
		if (activeContext == null || revoked) return false
		revoked = true
		return true
	}

	val suppressesOriginalTerminal: Boolean
		get() = revoked

	fun finish() {
		activeContext = null
		revoked = false
	}
}

private class KomikkuReaderNativeViewerContainer(context: Context) :
	FrameLayout(context),
	ReaderSettingsWebViewMutationHost {
	private val viewerContentContainer = FrameLayout(context)
	private val whispersyncCueMapView = ReaderWhispersyncCueMapNativeView(context)
	private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	private val readablePageDragSlopPx = ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()
	private val shellCoverNavigator = KomikkuReaderNavigator(KomikkuRightAndLeftNavigation())
	var navigator: KomikkuReaderNavigator = KomikkuReaderNavigator(KomikkuDisabledNavigation())
	var onAction: (KomikkuNavigationRegion) -> Unit = {}
	var onStartupShellPrepared: () -> Unit = {}
	var onPageTurnBoundary: (ReaderPageTurnDirection) -> Unit = {}
	var onRendererBusyGestureRejected: () -> Unit = {}
	private var verticalPageDragPreview: Boolean = false
	var chromeOverlayVisible: Boolean = false
	var onReadableDragPreview: (
		deltaX: Float,
		deltaY: Float,
		viewWidth: Int,
		viewHeight: Int,
		phase: ReaderPageDragPreviewPhase
	) -> Unit = { _, _, _, _, _ -> }
	var onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit = { _, _, _, _ -> }
	private var shellCoverView: View? = null
	private var swipeStartX: Float = 0f
	private var swipeStartY: Float = 0f
	private var horizontalSwipeDispatched: Boolean = false
	private var shellCoverDragDiagnosticLogged: Boolean = false
	private var nativeDragPreviewDiagnosticLogged: Boolean = false
	private var nativeTapCandidate: Boolean = false
	private var nativeTapCancelledByDrag: Boolean = false
	private var nativeTapLongConfirmed: Boolean = false
	private var nativeSwipeIntercepted: Boolean = false
	private var playLikeCurlGestureOwned: Boolean = false
	private var retainedContentDown: MotionEvent? = null
	private var shouldDispatchToViewerContent: Boolean = false
	private val shouldSuppressViewerContentInput: Boolean
		get() = playLikeCurlController.shouldSuppressViewerContentInput ||
			pageRasterPreparationController.shouldSuppressViewerContentInput
	private var physicalDispatchMode: ReaderPagePhysicalDispatchMode? = null
	private val legacyLivePointerStream = ReaderLegacyLivePointerStream()
	private var legacyLivePointerDown: MotionEvent? = null
	private var legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext =
		ReaderLegacyLiveCompatibilityContext.Denied()
	private var pageTurnCanvasEnabled: Boolean = false
	private var pageTurnReadingDirection: String? = null
	private var pageTurnBitmapQuality = ReaderPageBitmapQuality.Balanced
	private var pageTurnSnapshotKey: Int = Int.MIN_VALUE
	private var pageTurnContentReadyKey: String? = null
	private var pageTurnPaginationStatus: String? = null
	private var pageTurnVisualPageIndex: Int? = null
	private var pageTurnVisualLocationReason: String? = null
	private var pageTurnFoliateSessionId: String? = null
	private var pageTurnSettlementAck: ReaderPageTurnSettlementAck? = null
	private var presentationDestinationCommitIdentity: ReaderDestinationCommitIdentity? = null
	private var presentationDecision: ReaderPresentationDecision? = null
	private var onPresentationEvent: (ReaderPresentationEvent) -> Unit = {}
	private val nativePagePresentationPublisher by lazy {
		ReaderNativePagePresentationPublisher(
			frameSource = playLikeCurlController.presentedFrameSource,
			currentCandidate = ::currentNativePagePresentationCandidateOrNull
		) { event ->
			check(event is ReaderPresentationEvent.NativePagePresented)
			onPresentationEvent(event)
		}
	}
	private val presentationBindingReporter = ReaderPresentationBindingReporter()
	private var presentationPublicationGeneration = 0L
	private var presentationViewportGeneration = 0L
	private var presentationPublicationOpenPending = false
	private var presentationRelocationPending = false
	private val presentationViewerReplacementFence = ReaderPresentationViewerReplacementFence()
	private var lastReportedPresentationFacts: Pair<ReaderPresentationBinding, ReaderPagePreparationFacts>? = null
	private var lastPresentationWindowVisible: Boolean? = null
	private var preparedActiveDeck: ReaderPagePreparedActiveDeck? = null
	private var whispersyncOverlayActive = false
	private var whispersyncAnchorAvailable = false
	private var destinationDeckPrewarmPending = false
	private var shellCoverVisible: Boolean = false
	private val startupShellHandoff = ReaderStartupShellHandoffGate()
	private var presentationInputPolicy: ReaderPresentationInputPolicy =
		ReaderPresentationInputPolicy.RecoveryOnly
	private var localPageSafetyPolicy = readerPageOperationPolicy(ReaderPageReadinessState())
	private var pageTurnPrewarmLayoutListener: ViewTreeObserver.OnPreDrawListener? = null
	private var pageTurnPrewarmLayoutSignature: ReaderPageLayoutSignature? = null
	private var pageTurnPrewarmStableFrameCount: Int = 0
	private var rasterProfileEpoch: Long? = null
	private var rasterPaginationReady = false
	private var latestRasterPreparationState = ReaderPagePreparationState()
	private var latestRendererReadinessState = ReaderPageRendererReadinessState()
	private val retainedValidatedPresentationOwnership =
		ReaderRetainedValidatedPresentationOwnership()
	private val ownershipMainHandler = Handler(Looper.getMainLooper())
	private val ownershipRetryPosted = AtomicBoolean()
	private val readerDiagnosticSession = ReaderPageDiagnosticSessionIds.incrementAndGet()
	private val readerRuntimeDiagnostics = ReaderPageRuntimeDiagnostics(
		readerSession = readerDiagnosticSession,
		emit = { message -> Logger.i(KomikkuReaderNativeFrameHostTag, message) }
	)
	private val gestureDiagnostics = mutableMapOf<Long, ReaderPageGestureDiagnosticContext>()
	private val pendingOwnershipDiagnosticPhases = linkedSetOf<ReaderPageOwnershipPhase>()
	private var ownershipDiagnosticInFlight = false
	private var ownershipDiagnosticRetryPending = false
	private var coldOwnershipAdmitted = false
	private val applicationOwnershipEpoch = ReaderPageApplicationOwnershipEpoch {
		scheduleApplicationOwnershipRetry()
	}
	private val qaFaultRegistry = ReaderPageQaFaultRegistry(
		eventSink = ReaderPageQaFaultEventSink { event ->
			Logger.i(
				"ReaderPageQaFault",
				ReaderPageDiagnostic.qaFault(readerDiagnosticSession, event)
			)
		},
		onOwnershipMutated = applicationOwnershipEpoch::ownerMutationCommitted
	)
	private val qaFaultRegistration = ReaderPageQaFaultControl.attach(qaFaultRegistry)
	private val pageTurnBundleSource = ReaderPageTurnBundleSource(
		diagnostics = readerRuntimeDiagnostics,
		qaFaultRegistry = qaFaultRegistry,
		onOwnershipMutated = applicationOwnershipEpoch::ownerMutationCommitted
	)
	private var passiveRasterPreparationGeometry: ReaderPassiveRasterGeometry? = null
	private var passiveRasterPreparationAdapter: ReaderPassiveRasterPreparationAdapter? = null
	private val passiveRasterRendererLossFence = ReaderPassiveRasterRendererLossFence()
	private var passiveRasterCaptureEpoch = 0L
	private val foregroundWebViewOwnership = ReaderForegroundWebViewOwnership(
		onPassiveMutationReleased = ::onForegroundWebViewPassiveMutationReleased
	)
	private val settingsWebViewMutationCoordinator =
		ReaderSettingsWebViewMutationCoordinator(
			ownership = foregroundWebViewOwnership,
			onSnapshotCommitted = ::setPageTurnSnapshotKey
		)
	private val pagePointerRouter = ReaderPagePointerRouter(
		lifecycle = ReaderPageGestureLifecycle(),
		onStarted = { gestureId, downX, _ ->
			check(
				gestureDiagnostics.put(
					gestureId,
					ReaderPageGestureDiagnosticContext(
						startedAtMillis = SystemClock.uptimeMillis(),
						downX = downX,
						owner = ReaderPagePointerOwnership.Pending
					)
				) == null
			) { "Gesture diagnostics already own gesture $gestureId" }
			ReaderPageQaInputControl.consume()?.let { requestId ->
				Logger.i(
					"ReaderPageQaInput",
					"reader-qa-input requestId=$requestId state=Admitted " +
						"accepted=true session=$readerDiagnosticSession gestureId=$gestureId"
				)
			}
		},
		publishTerminal = { gestureId, outcome ->
			emitGestureDiagnostic(gestureId, outcome)
		}
	)
	private val pageInputSettlementHostController: ReaderPageInputSettlementHostController =
		ReaderPageInputSettlementHostController(
		initialPresentationInputPolicy = presentationInputPolicy,
		initialLocalSafetyPolicy = localPageSafetyPolicy,
		pointerRouter = pagePointerRouter,
		cancellationPort = object : ReaderPageHostCancellationPort {
			override fun cancelForPointerInterruption(gestureId: Long) {
				dispatchContentCancel()
				if (playLikeCurlGestureOwned) {
					playLikeCurlController.cancelGestureAfterHostTerminal(gestureId)
				} else {
					cancelReadableViewerDragPreview()
				}
				playLikeCurlGestureOwned = false
				recycleRetainedContentDown()
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}

			override fun clearCompletedPointerOwnership(gestureId: Long) {
				playLikeCurlGestureOwned = false
				recycleRetainedContentDown()
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}

			override fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason) {
				playLikeCurlController.cancelActiveGesture(reason)
				playLikeCurlGestureOwned = false
			}

			override fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason) {
				this@KomikkuReaderNativeViewerContainer.cancelReadableViewerDragPreview()
			}

			override fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason) {
				clearPlayLikeCurlNativeTapState(reason)
			}

			override fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason) {
				this@KomikkuReaderNativeViewerContainer.clearSwipeTouchState()
			}
		},
		chromeToggleTarget = ::isChromeToggleTarget,
		onChromeToggle = {
			logReaderTapAction(KomikkuNavigationRegion.MENU)
			onAction(KomikkuNavigationRegion.MENU)
		},
		chromeTapTimeoutMillis = ViewConfiguration.getLongPressTimeout().toLong(),
		publishLifecycleCancellation = { gestureId, reason ->
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				ReaderPageDiagnostic.lifecycleCancellation(
					readerDiagnosticSession,
					gestureId,
					reason
				)
			)
		}
	)
	private val playLikeCurlController: ReaderPlayLikeCurlFoliateController =
		ReaderPlayLikeCurlFoliateController(
		host = this,
		webViewProvider = { viewerContentContainer.findDescendantWebView() },
		foregroundWebViewOwnership = foregroundWebViewOwnership,
		bundleSource = pageTurnBundleSource,
		diagnostics = readerRuntimeDiagnostics,
		qaFaultRegistry = qaFaultRegistry,
		hasStaticRasterShieldOwnership = {
			pageRasterPreparationController.hasStaticRasterShieldOwnership()
		},
		onRequestPrewarm = ::requestPageTurnPrewarmWhenReady,
		onCanonicalLiveCommitIssued = ::onCanonicalLiveCommitIssued,
		onCanonicalLiveCommitRecoveryFailed = ::onCanonicalLiveCommitRecoveryFailed,
		onAttachRasterRepairQaFault = ::attachPageRasterRepairQaFault,
		onRequestRasterRepair = ::requestPageRasterRepair,
		onGestureTerminal = { gestureId, outcome, detail ->
			completePageGesture(gestureId, outcome, detail)
		},
		onBoundaryTurn = { direction ->
			onPageTurnBoundary(direction)
		},
		onRasterProfileEpochChanged = ::onRasterProfileEpochChanged,
		onProtectedRasterSourcePageIndicesChanged = {
			pageRasterPreparationController.onProtectedRasterSourcePageIndicesChanged(it)
		},
		onPreparedActiveDeckChanged = ::onPreparedActiveDeckChanged,
		onPaginationReadinessChanged = ::onPaginationReadinessChanged,
		onProfileBootstrapFailed = {
			removePageTurnPrewarmLayoutListener()
			pageRasterPreparationController.onProfileBootstrapFailed()
		},
		onReadinessStateChange = ::onRendererReadinessChanged,
		onViewerContentInputSuppressed = ::suppressViewerContentPointerStream,
		onUnsafeLifecycleEvent = { event ->
			require(
				event == ReaderPageHostLifecycleEvent.UnsafeContextLost ||
					event == ReaderPageHostLifecycleEvent.GlFailed
			)
			dispatchPageHostLifecycleEvent(event)
		},
		onOwnershipMutated = applicationOwnershipEpoch::ownerMutationCommitted,
		onOwnershipAvailabilityEdge = ::retryOwnershipAdmission,
		onOwnershipDiagnosticRequested = ::requestOwnershipDiagnostic
	)
	private val ownershipProbe = ReaderPageOwnershipProbe(
		applicationSnapshot = ::captureApplicationOwnershipSnapshot,
		rendererHost = playLikeCurlController
	)
	private val coldOwnershipAdmission = ReaderPageColdOwnershipAdmission(
		ownershipProbe = ownershipProbe,
		rendererHost = playLikeCurlController,
		acceptsColdBaseline = { snapshot ->
			emitOwnershipDiagnostic(
				ReaderPageOwnershipPhase.ColdStartBaseline,
				snapshot
			)
			snapshot.withinBounds() && snapshot.isClosedBaseline()
		},
		onUnavailable = { reason ->
			emitOwnershipUnavailable(
				ReaderPageOwnershipPhase.ColdStartBaseline,
				reason
			)
		},
		onAdmitted = {
			coldOwnershipAdmitted = true
			requestPageTurnPrewarmWhenReady()
		},
		onCallbackCapacityAvailable = ::retryOwnershipDiagnostics
	)
	private val tapTurnController = ReaderPageTapTurnControllerFacade(
		port = playLikeCurlController,
		publishTerminal = ::completePageGesture
	)
	private val pageRasterPreparationController: ReaderPageRasterPreparationController =
		ReaderPageRasterPreparationController(
		host = this,
		webViewProvider = { viewerContentContainer.findDescendantWebView() },
		bundleSource = pageTurnBundleSource,
		diagnostics = readerRuntimeDiagnostics,
		qaFaultRegistry = qaFaultRegistry,
		passiveRasterPreparationPortProvider = { passiveRasterPreparationAdapter },
		onPassiveRasterMemoryPressure = ::onPassiveRasterMemoryPressure,
		onPresentationLifecycleEvent = ::reportPresentationLifecycleEvent,
		fenceCallbacks = {
			ReaderPageQaFaultControl.detach(qaFaultRegistration)
			qaFaultRegistry.closeAndDrain()
		},
		closeRendererAndAdapter = {
			closePassiveRasterPreparationAdapter()
			playLikeCurlController.destroyAndJoin()
		},
		onRequestPrewarm = ::requestPageTurnPrewarmWhenReady,
		canStartPreparation = { coldOwnershipAdmitted && rasterPaginationReady },
		shouldPreserveCurrentPresentation = {
			shellCoverVisible ||
				presentationDecision?.layer?.let { it != ReaderPresentationLayer.Neutral } == true
		},
		onAwaitHostEvent = { reason, deferredSessionId ->
			if (reason == ReaderPageRasterDeferralReason.LayoutUnstable) {
				pageRasterHostEventController.layoutStabilityInvalidated()
			}
			if (
				reason == ReaderPageRasterDeferralReason.LayoutUnstable ||
				reason == ReaderPageRasterDeferralReason.WebViewDetached ||
				reason == ReaderPageRasterDeferralReason.PassiveHostUnavailable
			) {
				requestPageTurnPrewarmWhenReady()
			}
			if (
				reason == ReaderPageRasterDeferralReason.CanonicalLiveCommitUnavailable
			) {
				playLikeCurlController.onPassiveManifestAuthorityUnavailable(deferredSessionId)
			}
		},
		onRasterProofReady = playLikeCurlController::onRasterProofReady,
		onPreparationStateChange = { state ->
			latestRasterPreparationState = state
			playLikeCurlController.onPreparationStateChanged(state)
			reportPresentationIdentityIfAvailable()
			publishPagePreparationFacts()
			commitStartupShellPresentationIfReady()
		}
	)
	private val pageRasterHostEventController: ReaderPageRasterHostEventController =
		ReaderPageRasterHostEventController(
			onRetryEvent = pageRasterPreparationController::onRetryEvent,
			cancelAllDeferredRetries = pageRasterPreparationController::cancelAllDeferredRetries,
			onWebViewAttachmentChanged = { attached ->
				pageRasterPreparationController.onWebViewAttachmentChanged(attached)
				playLikeCurlController.onWebViewAttachmentChanged(attached)
			}
		)

	private fun scheduleApplicationOwnershipRetry() {
		if (!ownershipRetryPosted.compareAndSet(false, true)) return
		val accepted = ownershipMainHandler.post {
			ownershipRetryPosted.set(false)
			retryOwnershipAdmission()
			retryOwnershipDiagnostics()
		}
		if (!accepted) ownershipRetryPosted.set(false)
	}

	private fun retryOwnershipAdmission() {
		if (
			task4ResourceTeardownStarted ||
			!pageTurnCanvasEnabled ||
			!isAttachedToWindow
		) return
		coldOwnershipAdmission.retryOnOwnershipEdge()
	}

	private fun requestOwnershipDiagnostic(phase: ReaderPageOwnershipPhase) {
		if (
			task4ResourceTeardownStarted ||
			phase == ReaderPageOwnershipPhase.ColdStartBaseline ||
			phase == ReaderPageOwnershipPhase.AfterClose
		) return
		pendingOwnershipDiagnosticPhases += phase
		retryOwnershipDiagnostics()
	}

	private fun retryOwnershipDiagnostics() {
		if (task4ResourceTeardownStarted || pendingOwnershipDiagnosticPhases.isEmpty()) {
			return
		}
		if (ownershipDiagnosticInFlight) {
			ownershipDiagnosticRetryPending = true
			return
		}
		val phase = pendingOwnershipDiagnosticPhases.first()
		ownershipDiagnosticInFlight = true
		ownershipDiagnosticRetryPending = false
		ownershipProbe.request { result ->
			ownershipDiagnosticInFlight = false
			result.fold(
				onSuccess = { snapshot ->
					pendingOwnershipDiagnosticPhases.remove(phase)
					emitOwnershipDiagnostic(phase, snapshot)
				},
				onFailure = { unavailable ->
					emitOwnershipUnavailable(
						phase,
						(unavailable as ReaderPageOwnershipUnavailableException).reason
					)
				}
			)
			if (
				ownershipDiagnosticRetryPending ||
					(result.isSuccess && pendingOwnershipDiagnosticPhases.isNotEmpty())
			) {
				ownershipDiagnosticRetryPending = false
				retryOwnershipDiagnostics()
			}
		}
	}

	private fun captureApplicationOwnershipSnapshot():
		ReaderPageApplicationOwnershipSnapshot? =
		applicationOwnershipEpoch.captureStable { ownershipEpoch ->
			val controller = playLikeCurlController.applicationOwnershipMetrics()
			val residency = controller.rasterResidency
			val bundle = pageTurnBundleSource.ownershipMetrics()
			val cache = bundle.rasterCache
			val relocation = controller.relocation
			val foreground = foregroundWebViewOwnership.snapshot()
			check(residency.pendingValueReleases <= residency.uniqueDecodedBitmaps)
			check(cache.pendingDecodedReleases <= cache.uniqueDecodedBitmaps)
			check(cache.activeEncodePins >= cache.encodePinnedIdentities)
			check(relocation.occupied == relocation.reserved + relocation.queued)
			ReaderPageApplicationOwnershipSnapshot(
				ownershipEpoch = ownershipEpoch,
				adapterResidents = residency.residentEntries,
				adapterResidentLimit = residency.residentEntryLimit,
				adapterDecodedBitmaps = residency.uniqueDecodedBitmaps,
				adapterDecodedBitmapLimit = residency.uniqueDecodedBitmapLimit,
				cacheDecodedBitmaps = cache.uniqueDecodedBitmaps,
				cacheDecodedBitmapLimit = cache.uniqueDecodedBitmapLimit,
				stagedPublications = bundle.stagedPublications,
				stagedPublicationLimit = bundle.stagedPublicationLimit,
				pendingCallbacks =
					bundle.pendingPublicationCallbacks +
						controller.pendingVisualCallbacks +
						qaFaultRegistry.pendingCallbackCount(),
				pendingCallbackLimit =
					bundle.pendingPublicationCallbackLimit +
						controller.pendingVisualCallbackLimit +
						qaFaultRegistry.pendingCallbackLimit,
				foregroundPassiveOwners = foreground.passiveOwners,
				foregroundPassiveOwnerLimit = 1,
				foregroundLiveClaims = foreground.liveClaims,
				foregroundLiveClaimLimit = relocation.capacity,
				foregroundRestorationCallbacks = foreground.restorationCallbacks,
				foregroundRestorationCallbackLimit = 1,
				relocationReservations = relocation.reserved,
				queuedRelocations = relocation.queued,
				relocationTokens = relocation.occupied,
				relocationTokenLimit = relocation.capacity
			)
		}

	private fun emitOwnershipDiagnostic(
		phase: ReaderPageOwnershipPhase,
		snapshot: ReaderPageOwnershipSnapshot
	) {
		val cacheMetrics = pageTurnBundleSource.rasterCacheMetrics()
		if (phase == ReaderPageOwnershipPhase.AfterClose) {
			check(cacheMetrics.activeEncodePins == 0)
			check(cacheMetrics.encodePinnedIdentities == 0)
		}
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.ownership(readerDiagnosticSession, phase, snapshot)
		)
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.residency(
				readerDiagnosticSession,
				playLikeCurlController.rasterResidencyMetrics()
			)
		)
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.rasterCache(
				readerDiagnosticSession,
				phase,
				cacheMetrics
			)
		)
	}

	private fun emitOwnershipUnavailable(
		phase: ReaderPageOwnershipPhase,
		reason: ReaderPageOwnershipUnavailableReason
	) {
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.ownershipUnavailable(
				readerDiagnosticSession,
				phase,
				reason
			)
		)
	}

	private fun onPaginationReadinessChanged(readiness: ReaderPagePaginationReadiness) {
		updateRasterPaginationReadiness(
			readerPageActivePaginationReadiness(
				profileAvailable = rasterProfileEpoch != null,
				readiness = readiness
			)
		)
	}

	private fun updateRasterPaginationReadiness(
		readiness: ReaderPagePaginationReadiness
	) {
		val wasReady = rasterPaginationReady
		rasterPaginationReady = readiness.isReadyForRasterization
		pageRasterHostEventController.paginationReadinessChanged(readiness)
		if (
			wasReady &&
			!rasterPaginationReady &&
			readiness != ReaderPagePaginationReadiness.Failed
		) {
			pageRasterPreparationController.onPaginationReadinessLost()
		}
		when {
			readiness == ReaderPagePaginationReadiness.Failed -> {
				removePageTurnPrewarmLayoutListener()
				pageRasterPreparationController.onPaginationBootstrapFailed()
			}
			rasterPaginationReady -> requestPageTurnPrewarmWhenReady()
		}
	}

	private fun clearDestinationDeckPrewarm() {
		destinationDeckPrewarmPending = false
		preparedActiveDeck = null
	}

	private fun onRasterProfileEpochChanged(epoch: Long?) {
		presentationViewerReplacementFence.observeRasterProfileEpoch(epoch)
		clearDestinationDeckPrewarm()
		rasterProfileEpoch = epoch
		val activeReadiness = readerPageActivePaginationReadiness(
			profileAvailable = epoch != null,
			readiness = readerPagePaginationReadiness(pageTurnPaginationStatus)
		)
		pageRasterPreparationController.onRasterProfileEpochChanged(epoch)
		updateRasterPaginationReadiness(activeReadiness)
		if (epoch == null && !task4ResourceTeardownStarted) {
			removePageTurnPrewarmLayoutListener()
			requestPageTurnPrewarmWhenReady()
		}
		reportPresentationIdentityIfAvailable()
	}

	private fun attachPageRasterRepairQaFault(
		pageIndex: Int,
		correlation: ReaderPageQaFaultCorrelation
	) {
		pageRasterPreparationController.attachRasterRepairQaFault(
			pageIndex,
			correlation
		)
	}

	private fun requestPageRasterRepair(
		pageIndex: Int,
		onComplete: (ReaderPageRasterRepairResult) -> Unit
	) {
		pageRasterPreparationController.repairRasterPage(pageIndex, onComplete)
	}

	private fun onRendererReadinessChanged(state: ReaderPageRendererReadinessState) {
		retainedValidatedPresentationOwnership.onRendererReadinessChanged(state.textureDeck)
		latestRendererReadinessState = state
		reportPresentationIdentityIfAvailable()
		publishPagePreparationFacts()
		commitStartupShellPresentationIfReady()
	}

	private fun commitStartupShellPresentationIfReady() {
		val attempt = startupShellHandoff.beginAttempt(
			shellVisible = shellCoverVisible,
			canvasEnabled = pageTurnCanvasEnabled,
			rasterPhase = latestRasterPreparationState.phase,
			textureDeck = latestRendererReadinessState.textureDeck
		) ?: return
		val started = playLikeCurlController.presentStartupShellCurrentPage { committed ->
			startupShellHandoff.completeAttempt(
				attempt = attempt,
				shellVisible = shellCoverVisible,
				canvasEnabled = pageTurnCanvasEnabled,
				rasterPhase = latestRasterPreparationState.phase,
				textureDeck = latestRendererReadinessState.textureDeck,
				presentationCommitted = committed,
				onPrepared = { onStartupShellPrepared() },
				onRejected = {
					playLikeCurlController.dismissStartupShellPresentation()
				}
			)
		}
		if (!started) startupShellHandoff.rejectAttempt(attempt)
	}

	private fun consumeStartupShellPreparedHandoff(): Boolean =
		startupShellHandoff.consumePreparedHandoff()

	private fun publishPagePreparationFacts() {
		val state = readerHostPagePreparationState(
			pageTurnCanvasEnabled = pageTurnCanvasEnabled,
			pageTurnContentReady = pageTurnContentReadyKey != null,
			rasterState = latestRasterPreparationState,
			rendererState = latestRendererReadinessState
		)
		setLocalPageSafetyPolicy(readerPageOperationPolicy(state.readiness))
		reportPresentationPreparationFacts(state)
	}

	init {
		descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		addView(
			viewerContentContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			playLikeCurlController.inlineRasterShieldView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			playLikeCurlController.surfaceView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			whispersyncCueMapView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun hasValidatedRasterPresentation(): Boolean =
		retainedValidatedPresentationOwnership.hasPresentation(
			staticRasterShieldOwnership =
				pageRasterPreparationController.hasStaticRasterShieldOwnership()
		)

	fun setShellCoverView(shellCoverView: View) {
		this.shellCoverView = shellCoverView
		(shellCoverView.parent as? ViewGroup)?.removeView(shellCoverView)
		addView(
			shellCoverView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun prepareShellCoverForCommit(shellCoverView: View) {
		if (shellCoverView.parent === this && indexOfChild(shellCoverView) != 0) {
			val layoutParams = shellCoverView.layoutParams
			removeView(shellCoverView)
			addView(shellCoverView, 0, layoutParams)
		}
		shellCoverView.visibility = VISIBLE
		shellCoverView.isClickable = false
		shellCoverView.invalidate()
	}

	fun cancelShellCoverCommitPreparation(shellCoverView: View) {
		if (!shellCoverVisible) shellCoverView.visibility = GONE
	}

	fun selectShellCover(
		shellCoverView: View,
		preserveNativePresentationProof: Boolean
	) {
		shellCoverView.bringToFront()
		shellCoverView.visibility = VISIBLE
		setShellCoverVisible(
			visible = true,
			preserveNativePresentationProof = preserveNativePresentationProof
		)
	}

	fun invalidateShellCoverRasterDeck() {
		removePageTurnPrewarmLayoutListener()
		playLikeCurlController.invalidate("shell-cover-visible")
	}

	fun invalidateShellCoverPreparation() {
		pageRasterPreparationController.invalidate("shell-cover-visible")
	}

	fun replaceViewerContent(viewerView: View) {
		cancelLegacyLivePointerStream()
		startupShellHandoff.resetForNewViewer()
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.RendererReplaced
		)
		playLikeCurlController.invalidate("viewer-replaced")
		pageRasterPreparationController.invalidate(
			reason = "viewer-replaced",
			clearVisualPageIndex = true
		)
		removePageTurnPrewarmLayoutListener()
		pageRasterHostEventController.webViewAttachmentChanged(false)
		viewerContentContainer.removeAllViews()
		viewerContentContainer.addView(
			viewerView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		pageRasterHostEventController.webViewAttachmentChanged(
			viewerContentContainer.findDescendantWebView()?.isAttachedToWindow == true
		)
		requestPageTurnPrewarmWhenReady()
	}

	fun detachViewerContent(viewerView: View?) {
		if (viewerView?.parent === viewerContentContainer) {
			viewerContentContainer.removeView(viewerView)
		}
	}

	fun setVerticalPageDragPreview(value: Boolean) {
		if (verticalPageDragPreview == value) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		verticalPageDragPreview = value
	}

	fun setLegacyLiveCompatibilityContext(
		context: ReaderLegacyLiveCompatibilityContext
	) {
		if (legacyLiveCompatibilityContext == context) return
		legacyLiveCompatibilityContext = context
		cancelLegacyLivePointerStreamIfContextChanged()
	}

	fun setPageTurnCanvasEnabled(enabled: Boolean) {
		val supported = enabled && pageTurnBundleSource.isAvailable
		if (pageTurnCanvasEnabled == supported) {
			publishPagePreparationFacts()
			return
		}
		if (!supported) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.CanvasDisabled
			)
		}
		pageTurnCanvasEnabled = supported
		cancelLegacyLivePointerStreamIfContextChanged()
		playLikeCurlController.setEnabled(supported)
		publishPagePreparationFacts()
		if (supported) {
			requestPageTurnPrewarmWhenReady()
			commitStartupShellPresentationIfReady()
		} else {
			removePageTurnPrewarmLayoutListener()
			pageRasterPreparationController.invalidate("canvas-disabled")
		}
	}

	fun setPageTurnReadingDirection(direction: String?) {
		val normalized = direction?.trim()?.lowercase()
		if (pageTurnReadingDirection == normalized) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		pageTurnReadingDirection = normalized
		playLikeCurlController.invalidate(
			reason = "reading-direction",
			profileRegeneration = true
		)
		pageRasterPreparationController.invalidate("reading-direction")
		requestPageTurnPrewarmWhenReady()
	}

	fun setPageTurnBitmapQuality(value: String?) {
		val normalized = normalizeReaderPageBitmapQuality(value)
		if (pageTurnBitmapQuality == normalized) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		pageTurnBitmapQuality = normalized
		playLikeCurlController.updateBitmapQuality(normalized.persistedValue)
		pageRasterPreparationController.updateBitmapQuality(normalized.persistedValue)
	}

	override fun acquireSettingsMutation(
		requestId: Long,
		onReadiness: (ReaderSettingsWebViewMutationReadiness) -> Unit
	) {
		settingsWebViewMutationCoordinator.acquireSettingsMutation(
			requestId,
			onReadiness
		)
	}

	fun setPageTurnSnapshotKey(snapshotKey: Int) {
		if (pageTurnSnapshotKey == snapshotKey) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		pageTurnSnapshotKey = snapshotKey
		playLikeCurlController.setSnapshotKey(snapshotKey)
		pageRasterPreparationController.invalidate("settings-changed")
		requestPageTurnPrewarmWhenReady()
	}

	fun setPageTurnContentReadyKey(contentReadyKey: String?) {
		if (pageTurnContentReadyKey == contentReadyKey) {
			publishPagePreparationFacts()
			return
		}
		pageTurnContentReadyKey = contentReadyKey
		pageRasterHostEventController.contentReadyKeyChanged(contentReadyKey)
		publishPagePreparationFacts()
		if (contentReadyKey == null) return
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Page-turn pagination content ready key=${contentReadyKey.hashCode()}"
		)
		playLikeCurlController.onHostContentReady()
	}

	fun setPageTurnPaginationStatus(status: String?) {
		if (pageTurnPaginationStatus == status) return
		pageTurnPaginationStatus = status
		playLikeCurlController.updatePaginationReadiness(
			readerPagePaginationReadiness(status)
		)
	}

	fun setWhispersyncOverlay(
		active: Boolean,
		receipt: ReaderWhispersyncAnchorReceipt?,
		highlightColorArgb: Int
	) {
		val anchorAvailable = receipt != null
		val requestAuthority = readerWhispersyncAuthorityRestorationRequested(
			previousActive = whispersyncOverlayActive,
			previousAnchorAvailable = whispersyncAnchorAvailable,
			active = active,
			anchorAvailable = anchorAvailable
		)
		whispersyncOverlayActive = active
		whispersyncAnchorAvailable = anchorAvailable
		playLikeCurlController.setWhispersyncOverlay(
			receipt,
			highlightColorArgb
		)
		if (requestAuthority) {
			playLikeCurlController.onWhispersyncOverlayAnchorUnavailable()
		}
	}

	fun setWhispersyncCueMap(
		state: ReaderWhispersyncCueMapState,
		onHoldOutcome: (Int, ReaderWhispersyncCueMapHoldOutcome) -> Unit,
		onSeekRequested: (Int) -> Unit
	) {
		whispersyncCueMapView.onHoldOutcome = onHoldOutcome
		whispersyncCueMapView.onSeekRequested = onSeekRequested
		whispersyncCueMapView.setPresentation(state)
	}

	fun currentPresentationBinding(): ReaderPresentationBinding? =
		currentPresentationBindingOrNull()

	fun setPresentationDecision(
		decision: ReaderPresentationDecision,
		destinationCommitIdentity: ReaderDestinationCommitIdentity?,
		onEvent: (ReaderPresentationEvent) -> Unit
	) {
		onPresentationEvent = onEvent
		presentationDecision = decision
		cancelLegacyLivePointerStreamIfContextChanged()
		if (presentationDestinationCommitIdentity != destinationCommitIdentity) {
			presentationDestinationCommitIdentity = destinationCommitIdentity
			presentationRelocationPending = presentationBindingReporter.lastReportedBinding != null
		}
		reportPresentationIdentityIfAvailable()
	}

	fun releaseStalePresentation(
		effect: ReaderPresentationEffect.ReleaseStalePresentation
	): Boolean = playLikeCurlController.releaseStalePresentationDeck(effect.binding)

	fun onPresentationPublicationChanged(viewerReplacementPending: Boolean) {
		if (viewerReplacementPending) {
			presentationViewerReplacementFence.begin(rasterProfileEpoch)
		}
		presentationPublicationGeneration = Math.incrementExact(presentationPublicationGeneration)
		presentationPublicationOpenPending = true
		presentationRelocationPending = false
		presentationBindingReporter.reset()
		lastReportedPresentationFacts = null
		nativePagePresentationPublisher.update()
	}

	fun completePresentationViewerReplacement() {
		presentationViewerReplacementFence.completeAfterViewerInvalidation()
	}

	private fun reportPresentationLifecycleEvent(event: ReaderPresentationLifecycleEvent) {
		when (event) {
			ReaderPresentationLifecycleEvent.VisibilityLost ->
				reportPresentationWindowVisibility(visible = false)
			ReaderPresentationLifecycleEvent.VisibilityRestored ->
				reportPresentationWindowVisibility(visible = true)
			else -> onPresentationEvent(ReaderPresentationEvent.Lifecycle(event))
		}
	}

	private fun reportPresentationWindowVisibility(visible: Boolean) {
		if (lastPresentationWindowVisible == visible) return
		lastPresentationWindowVisible = visible
		onPresentationEvent(
			ReaderPresentationEvent.Lifecycle(
				readerPresentationLifecycleEventForWindowVisibility(visible)
			)
		)
		if (visible) {
			reportPresentationIdentityIfAvailable()
		} else {
			nativePagePresentationPublisher.update()
		}
	}

	private fun currentPresentationBindingOrNull(): ReaderPresentationBinding? =
		readerPresentationHostBinding(
			ReaderPresentationHostBindingSnapshot(
				pageTurnCanvasEnabled = pageTurnCanvasEnabled,
				windowVisible = lastPresentationWindowVisible,
				foliateSessionId = pageTurnFoliateSessionId,
				publicationGeneration = presentationPublicationGeneration,
				viewportGeneration = presentationViewportGeneration,
				viewportWidth = width,
				viewportHeight = height,
				profileIdentity = rasterProfileEpoch?.let(
					ReaderPresentationHostProfileIdentity::Resolved
				) ?: ReaderPresentationHostProfileIdentity.Provisional,
				destinationCommitIdentity = presentationDestinationCommitIdentity,
				preparationGeneration = latestRasterPreparationState.preparationGeneration,
				visualPageIndex = pageTurnVisualPageIndex,
				preparedDeck = preparedActiveDeck,
				preparedDeckAdmitted = preparedActiveDeck?.let(
					presentationViewerReplacementFence::admits
				) == true
			)
		)

	private fun reportPresentationIdentityIfAvailable() {
		val binding = currentPresentationBindingOrNull()
		if (binding == null) {
			nativePagePresentationPublisher.update()
			return
		}
		val bindingEvent = presentationBindingReporter.update(
			confirmedTargetBinding = presentationDecision?.targetBinding,
			currentBinding = binding,
			publicationOpenPending = presentationPublicationOpenPending,
			relocationPending = presentationRelocationPending
		)
		if (bindingEvent != null) {
			when (bindingEvent) {
				is ReaderPresentationEvent.PublicationOpened -> {
					presentationPublicationOpenPending = false
					presentationRelocationPending = false
				}
				is ReaderPresentationEvent.BindingReplaced,
				is ReaderPresentationEvent.FoliateRelocated ->
					presentationRelocationPending = false
				else -> Unit
			}
			lastReportedPresentationFacts = null
			onPresentationEvent(bindingEvent)
		}
		reportPresentationPreparationFacts(latestRasterPreparationState)
		reportNativePagePresentationIfAvailable()
	}

	private fun reportPresentationPreparationFacts(state: ReaderPagePreparationState) {
		val binding = currentPresentationBindingOrNull() ?: return
		if (presentationBindingReporter.lastReportedBinding != binding) return
		val facts = state.toPresentationFacts()
		val report = binding to facts
		if (lastReportedPresentationFacts == report) return
		lastReportedPresentationFacts = report
		onPresentationEvent(
			facts.failure?.let { reason ->
				ReaderPresentationEvent.PreparationFailed(
					binding = binding,
					facts = facts,
					reason = reason,
					cancellable = false
				)
			} ?: ReaderPresentationEvent.PreparationReported(
				binding = binding,
				facts = facts
			)
		)
	}

	private fun reportNativePagePresentationIfAvailable() {
		nativePagePresentationPublisher.update()
	}

	private fun currentNativePagePresentationCandidateOrNull(): ReaderNativePagePresentationCandidate? {
		val binding = currentPresentationBindingOrNull()
		if (presentationBindingReporter.lastReportedBinding != binding) return null
		val deck = preparedActiveDeck
		val transition = presentationDecision?.requiredTransition as?
			ReaderRequiredTransition.PresentNativePage
		val transitionToken = transition?.token?.takeIf { transition.binding == binding }
		return ReaderNativePagePresentationHostSnapshot(
			binding = binding,
			transitionToken = transitionToken,
			deck = deck,
			preparationFacts = latestRasterPreparationState.toPresentationFacts(),
			visualPageIndex = pageTurnVisualPageIndex,
			viewportWidth = width,
			viewportHeight = height,
			hostAttached = isAttachedToWindow,
			windowVisible = lastPresentationWindowVisible != false,
			viewerReplacementAdmitted =
				deck != null && presentationViewerReplacementFence.admits(deck),
			rendererDeckReady =
				latestRendererReadinessState.textureDeck == ReaderTextureDeckState.Ready,
			nativePresentationVisible =
				hasValidatedRasterPresentation() && (!shellCoverVisible || transitionToken != null),
			shellCoverSelected = shellCoverVisible
		).currentCandidateOrNull()
	}

	fun applyPresentationDecision(decision: ReaderPresentationDecision) {
		presentationDecision = decision
		presentationInputPolicy = decision.inputPolicy
		cancelLegacyLivePointerStreamIfContextChanged()
		updateInputSettlementPolicies()
	}

	private fun setLocalPageSafetyPolicy(policy: ReaderPageOperationPolicy) {
		localPageSafetyPolicy = policy
		updateInputSettlementPolicies()
		playLikeCurlController.setPageOperationPolicy(policy)
	}

	private fun updateInputSettlementPolicies() {
		pageInputSettlementHostController.updateInputPolicies(
			presentationInputPolicy = presentationInputPolicy,
			localSafetyPolicy = localPageSafetyPolicy,
			nativeTapContinuationIdentity = presentationDecision?.let { decision ->
				readerNativeTapContinuationIdentity(decision, localPageSafetyPolicy)
			}
		)
	}

	fun cancelWhispersyncCueMapForChrome() {
		whispersyncCueMapView.cancelForChrome()
	}

	fun setPageTurnVisualLocation(
		pageIndex: Int?,
		reason: String?,
		currentFoliateSessionId: String,
		acknowledgement: ReaderPageTurnSettlementAck?
	) {
		require(currentFoliateSessionId.isNotBlank())
		val normalized = pageIndex?.takeIf { it >= 0 }
		if (
			pageTurnVisualPageIndex == normalized &&
			pageTurnVisualLocationReason == reason &&
			pageTurnFoliateSessionId == currentFoliateSessionId &&
			pageTurnSettlementAck == acknowledgement
		) {
			reportPresentationIdentityIfAvailable()
			return
		}

		val sessionChanged =
			pageTurnFoliateSessionId != null &&
				pageTurnFoliateSessionId != currentFoliateSessionId
		if (sessionChanged) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.ExternalRelocation
			)
		}
		pageTurnFoliateSessionId = currentFoliateSessionId
		playLikeCurlController.setFoliateSessionId(currentFoliateSessionId)
		val origin = if (sessionChanged) {
			ReaderPageVisualLocationOrigin.External
		} else {
			playLikeCurlController.visualLocationOrigin(
				normalized,
				acknowledgement
			)
		}
		if (!sessionChanged && origin == ReaderPageVisualLocationOrigin.External) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.ExternalRelocation
			)
		}
		pageTurnVisualPageIndex = normalized
		pageTurnVisualLocationReason = reason
		pageTurnSettlementAck = acknowledgement
		presentationRelocationPending = presentationBindingReporter.lastReportedBinding != null
		reportPresentationIdentityIfAvailable()
		playLikeCurlController.synchronizeVisualPageIndex(
			normalized,
			reason,
			acknowledgement
		)
		if (origin != ReaderPageVisualLocationOrigin.StaleAcknowledgement) {
			pageRasterPreparationController.synchronizeVisualPageIndex(normalized, reason)
		}
		commitStartupShellPresentationIfReady()
	}

	fun setShellCoverVisible(
		visible: Boolean,
		preserveNativePresentationProof: Boolean
	) {
		if (shellCoverVisible == visible) return
		val preservePreparedHandoff = !visible && consumeStartupShellPreparedHandoff()
		if (visible) {
			dispatchPageHostLifecycleEvent(
				event = ReaderPageHostLifecycleEvent.ShellCoverShown,
				preserveDestinationDeck = preserveNativePresentationProof
			)
		}
		shellCoverVisible = visible
		cancelLegacyLivePointerStreamIfContextChanged()
		if (visible && !preserveNativePresentationProof) {
			removePageTurnPrewarmLayoutListener()
			playLikeCurlController.invalidate("shell-cover-visible")
			pageRasterPreparationController.invalidate("shell-cover-visible")
		} else if (
			!visible &&
			!preserveNativePresentationProof &&
			!preservePreparedHandoff
		) {
			pageRasterPreparationController.invalidateCurrentVisualSnapshot("shell-cover-hidden")
		}
		requestPageTurnPrewarmWhenReady()
		commitStartupShellPresentationIfReady()
		if (!visible) {
			reportPresentationIdentityIfAvailable()
		} else {
			nativePagePresentationPublisher.update()
		}
	}

	fun retryPreparation(effect: ReaderPresentationEffect.RetryPreparation): Boolean {
		if (currentPresentationBindingOrNull() != effect.binding) return true
		val webView = viewerContentContainer.findDescendantWebView()
		if (passiveRasterPreparationAdapter?.isAvailable != true && webView != null) {
			closePassiveRasterPreparationAdapter()
			replacePassiveRasterPreparationAdapter(webView)
		}
		val preparationGeneration =
			pageRasterPreparationController.retryPreparation() ?: return false
		playLikeCurlController.retryPreparation(preparationGeneration)
		if (rasterProfileEpoch == null) {
			requestPageTurnPrewarmWhenReady()
		}
		return true
	}

	private fun onPreparedActiveDeckChanged(deck: ReaderPagePreparedActiveDeck?) {
		val previous = preparedActiveDeck
		val ownership = foregroundWebViewOwnership.snapshot()
		preparedActiveDeck = deck
		reportPresentationIdentityIfAvailable()
		if (
			deck != null &&
			previous != null &&
			previous != deck &&
			(
				ownership.liveClaims > 0 ||
				ownership.restorationCallbacks > 0
			)
		) {
			destinationDeckPrewarmPending = true
		}
		pageRasterPreparationController.onPreparedActiveDeckChanged(deck)
		resumeDestinationDeckPrewarmIfReady()
	}

	private fun resumeDestinationDeckPrewarmIfReady() {
		if (!destinationDeckPrewarmPending) return
		if (preparedActiveDeck == null) return
		destinationDeckPrewarmPending = false
		requestPageTurnPrewarmWhenReady()
	}

	private fun onForegroundWebViewPassiveMutationReleased() {
		if (task4ResourceTeardownStarted) return
		playLikeCurlController.onForegroundWebViewPassiveMutationReleased()
	}

	private fun onPassiveRasterMemoryPressure(reason: String) {
		if (task4ResourceTeardownStarted) return
		closePassiveRasterPreparationAdapter()
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Passive raster session retired reason=$reason"
		)
	}

	private fun onPassiveRasterPreparationAvailable() {
		if (task4ResourceTeardownStarted) return
		pageRasterPreparationController.onPassiveRasterPreparationAvailable()
	}

	private fun onCanonicalLiveCommitIssued(): Boolean {
		if (task4ResourceTeardownStarted) return false
		return pageRasterPreparationController.onCanonicalLiveCommitIssued()
	}

	private fun onCanonicalLiveCommitRecoveryFailed(
		deferredSessionId: Long
	): Boolean {
		if (task4ResourceTeardownStarted) return false
		return pageRasterPreparationController.onCanonicalLiveCommitRecoveryFailed(
			deferredSessionId
		)
	}

	private fun replacePassiveRasterPreparationAdapter(webView: WebView) {
		if (webView.width <= 0 || webView.height <= 0) return
		val geometry = ReaderPassiveRasterGeometry(
			viewportWidth = webView.width,
			viewportHeight = webView.height,
			captureLeft = 0,
			captureTop = 0,
			captureRight = webView.width,
			captureBottom = webView.height
		)
		if (
			passiveRasterPreparationGeometry == geometry &&
			passiveRasterPreparationAdapter?.isRetired == false
		) return
		closePassiveRasterPreparationAdapter()
		val activity = context as? Activity ?: return
		passiveRasterCaptureEpoch = if (passiveRasterCaptureEpoch == Long.MAX_VALUE) {
			0L
		} else {
			passiveRasterCaptureEpoch + 1L
		}
		val runtimeIdentity = Any()
		val runtime = ReaderPassiveRasterWebViewHost(
			activity = activity,
			passiveSessionId = UUID.randomUUID().toString(),
			viewportGeometry = geometry,
			onRendererGone = {
				post {
					if (!passiveRasterRendererLossFence.isCurrent(runtimeIdentity)) {
						return@post
					}
					closePassiveRasterPreparationAdapter()
					requestPageTurnPrewarmWhenReady()
				}
			}
		)
		val session = ReaderPassiveRasterPrototypeSession(
			runtime = runtime,
			releaseRaster = { bitmap: Bitmap ->
				bitmap.takeUnless { it.isRecycled }?.recycle()
			}
		)
		passiveRasterPreparationAdapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = ReaderPageLivePassiveRasterManifestPort {
				viewerContentContainer.findDescendantWebView()
			},
			bundleSource = pageTurnBundleSource,
			initialCaptureEpoch = passiveRasterCaptureEpoch
		)
		passiveRasterRendererLossFence.replace(runtimeIdentity)
		passiveRasterPreparationGeometry = geometry
		if (observedHostLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) {
			passiveRasterPreparationAdapter?.pause()
		} else if (passiveRasterPreparationAdapter?.isAvailable == true) {
			onPassiveRasterPreparationAvailable()
		}
	}

	private fun closePassiveRasterPreparationAdapter() {
		passiveRasterRendererLossFence.clear()
		passiveRasterPreparationAdapter?.close()
		passiveRasterPreparationAdapter = null
		passiveRasterPreparationGeometry = null
	}

	private fun requestPageTurnPrewarmWhenReady() {
		if (
			task4ResourceTeardownStarted ||
			!pageTurnCanvasEnabled ||
			!isAttachedToWindow
		) return
		if (!coldOwnershipAdmitted) {
			coldOwnershipAdmission.requestColdBaseline()
			return
		}
		if (
			pageTurnVisualPageIndex == null ||
			pageTurnPrewarmLayoutListener != null
		) return
		pageTurnPrewarmLayoutSignature = null
		pageTurnPrewarmStableFrameCount = 0
		val listener = ViewTreeObserver.OnPreDrawListener {
			if (
				task4ResourceTeardownStarted ||
				!pageTurnCanvasEnabled ||
				!isAttachedToWindow
			) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			val webView = viewerContentContainer.findDescendantWebView()
			pageRasterHostEventController.webViewAttachmentChanged(
				webView?.isAttachedToWindow == true
			)
			if (
				webView == null ||
				!webView.isAttachedToWindow ||
				width <= 0 ||
				height <= 0 ||
				webView.width <= 0 ||
				webView.height <= 0 ||
				isLayoutRequested ||
				webView.isLayoutRequested
			) return@OnPreDrawListener true
			val profileEpoch = rasterProfileEpoch
			val signature = pageTurnPrewarmLayoutSignature(webView, profileEpoch ?: 0L)
			if (profileEpoch != null) {
				pageRasterHostEventController.layoutSignatureMeasured(signature)
			}
			if (signature == pageTurnPrewarmLayoutSignature) {
				pageTurnPrewarmStableFrameCount += 1
			} else {
				pageTurnPrewarmLayoutSignature = signature
				pageTurnPrewarmStableFrameCount = 1
			}
			if (pageTurnPrewarmStableFrameCount < PageTurnPrewarmRequiredStableFrames) {
				postInvalidateOnAnimation()
				return@OnPreDrawListener true
			}
			if (pageTurnPrewarmStableFrameCount == PageTurnPrewarmRequiredStableFrames) {
				playLikeCurlController.onHostContentReady()
			}
			replacePassiveRasterPreparationAdapter(webView)
			if (passiveRasterPreparationAdapter?.isAvailable != true) {
				postInvalidateOnAnimation()
				return@OnPreDrawListener true
			}
			onPassiveRasterPreparationAvailable()
			if (profileEpoch == null) {
				postInvalidateOnAnimation()
				return@OnPreDrawListener true
			}
			if (!rasterPaginationReady) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (pageRasterPreparationController.prewarmAdjacent()) {
				removePageTurnPrewarmLayoutListener()
			}
			true
		}
		pageTurnPrewarmLayoutListener = listener
		viewTreeObserver.addOnPreDrawListener(listener)
		postInvalidateOnAnimation()
	}

	private fun pageTurnPrewarmLayoutSignature(
		webView: WebView,
		profileEpoch: Long
	): ReaderPageLayoutSignature = ReaderPageLayoutSignature(
		widthPx = webView.width,
		heightPx = webView.height,
		layoutDirection = layoutDirection,
		rasterProfileEpoch = profileEpoch
	)

	private fun removePageTurnPrewarmLayoutListener() {
		val listener = pageTurnPrewarmLayoutListener ?: return
		pageTurnPrewarmLayoutListener = null
		pageTurnPrewarmLayoutSignature = null
		pageTurnPrewarmStableFrameCount = 0
		if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(listener)
	}

	private var finalHostLifecycleEvent: ReaderPageHostLifecycleEvent? = null
	private var physicalPointerDeliveryClosed = false
	private var task4ResourceTeardownStarted = false
	private var task4Teardown: Deferred<Unit>? = null
	private var observedHostLifecycle: Lifecycle? = null

	private val hostLifecycleObserver = object : DefaultLifecycleObserver {
		override fun onResume(owner: LifecycleOwner) {
			passiveRasterPreparationAdapter?.resume()
			pageRasterHostEventController.lifecycleResumedChanged(true)
			playLikeCurlController.onHostResumedChanged(true)
			requestPageTurnPrewarmWhenReady()
		}

		override fun onPause(owner: LifecycleOwner) {
			passiveRasterPreparationAdapter?.pause()
			pageRasterHostEventController.lifecycleResumedChanged(false)
			playLikeCurlController.onHostResumedChanged(false)
		}

		override fun onStop(owner: LifecycleOwner) {
			passiveRasterPreparationAdapter?.pause()
			pageRasterHostEventController.lifecycleResumedChanged(false)
			playLikeCurlController.onHostResumedChanged(false)
		}

		override fun onDestroy(owner: LifecycleOwner) {
			closePassiveRasterPreparationAdapter()
			pageRasterHostEventController.lifecycleResumedChanged(false)
			playLikeCurlController.onHostResumedChanged(false)
			beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.Destroyed)
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		observedHostLifecycle = findViewTreeLifecycleOwner()?.lifecycle
			?.also { lifecycle ->
				lifecycle.addObserver(hostLifecycleObserver)
				val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
				pageRasterHostEventController.lifecycleResumedChanged(resumed)
				playLikeCurlController.onHostResumedChanged(resumed)
			}
		pageRasterHostEventController.webViewAttachmentChanged(
			viewerContentContainer.findDescendantWebView()?.isAttachedToWindow == true
		)
		playLikeCurlController.onHostAttached()
		reportPresentationIdentityIfAvailable()
		requestPageTurnPrewarmWhenReady()
	}

	private fun isChromeToggleTarget(x: Float, y: Float): Boolean {
		if (width <= 0 || height <= 0) return false
		val point = KomikkuPoint(
			x = (x / width.toFloat()).coerceIn(0f, 1f),
			y = (y / height.toFloat()).coerceIn(0f, 1f)
		)
		return navigator.getAction(point) == KomikkuNavigationRegion.MENU
	}

	private val legacyGestureDetector = KomikkuGestureDetectorWithLongTap(
		context,
		object : KomikkuGestureDetectorWithLongTap.Listener() {
			override fun onDown(event: MotionEvent): Boolean = true

			override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
				onLegacySingleTapConfirmed(event)

			override fun onDoubleTap(event: MotionEvent): Boolean =
				onLegacySingleTapConfirmed(event)

			override fun onDoubleTapEvent(event: MotionEvent): Boolean =
				if (event.actionMasked == MotionEvent.ACTION_UP) {
					onLegacySingleTapConfirmed(event)
				} else {
					true
				}

			override fun onLongTapConfirmed(event: MotionEvent) {
				nativeTapLongConfirmed = true
				logReaderLongTap()
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
				onContentLongPress(event.x, event.y, width, height)
			}
		}
	)

	private val playLikeCurlGestureDetector = KomikkuGestureDetectorWithLongTap(
		context,
		object : KomikkuGestureDetectorWithLongTap.Listener() {
			override fun onDown(event: MotionEvent): Boolean = true

			override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
				onPlayLikeCurlSingleTapConfirmed(downTimeMillis = event.downTime)

			override fun onSingleTapSuperseded(event: MotionEvent): Boolean =
				onPlayLikeCurlSingleTapConfirmed(downTimeMillis = event.downTime)

			override fun onDoubleTap(event: MotionEvent): Boolean =
				onPlayLikeCurlFirstDoubleTapConfirmed()

			override fun onDoubleTapEvent(event: MotionEvent): Boolean =
				if (event.actionMasked == MotionEvent.ACTION_UP) {
					onPlayLikeCurlSingleTapConfirmed(downTimeMillis = event.downTime)
				} else {
					true
				}

			override fun onLongTapConfirmed(event: MotionEvent) {
				val dispatch = pageInputSettlementHostController.claimContentAction(
					downTimeMillis = event.downTime
				)
				if (dispatch.route != ReaderPagePointerRoute.Content) return
				nativeTapLongConfirmed = true
				revokeViewerContentPointerStreamIfSuppressed(event)
				if (shouldSuppressViewerContentInput) return
				logReaderLongTap()
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
				onContentLongPress(event.x, event.y, width, height)
			}
		}
	)

	private fun logReaderTapAction(action: KomikkuNavigationRegion) {
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native tap action=$action"
		)
	}

	private fun logReaderLongTap() {
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native long tap"
		)
	}

	private fun onLegacySingleTapConfirmed(event: MotionEvent): Boolean {
		if (width <= 0 || height <= 0) return false
		val point = KomikkuPoint(
			x = (event.x / width.toFloat()).coerceIn(0f, 1f),
			y = (event.y / height.toFloat()).coerceIn(0f, 1f)
		)
		val action = if (shellCoverVisible) {
			shellCoverNavigator.getAction(point)
		} else {
			navigator.getAction(point)
		}
		logReaderTapAction(action)
		if (chromeOverlayVisible && action != KomikkuNavigationRegion.MENU) return true
		dispatchLegacySingleTapAction(action)
		return true
	}

	private fun onPlayLikeCurlSingleTapConfirmed(downTimeMillis: Long): Boolean {
		val tap = pageInputSettlementHostController.takeDelayedTap(
			downTimeMillis = downTimeMillis
		) ?: return false
		return dispatchPlayLikeCurlDelayedTap(tap)
	}

	private fun onPlayLikeCurlFirstDoubleTapConfirmed(): Boolean {
		val tap = pageInputSettlementHostController.takeOldestDelayedTap()
			?: return false
		return dispatchPlayLikeCurlDelayedTap(tap)
	}

	private fun dispatchPlayLikeCurlDelayedTap(
		tap: ReaderPageContentGestureToken
	): Boolean {
		if (width <= 0 || height <= 0) {
			completeHostGesture(
				tap.gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser
			)
			return false
		}
		val point = KomikkuPoint(
			x = (tap.x / width.toFloat()).coerceIn(0f, 1f),
			y = (tap.y / height.toFloat()).coerceIn(0f, 1f)
		)
		val action = navigator.getAction(point)
		logReaderTapAction(action)
		if (chromeOverlayVisible && action != KomikkuNavigationRegion.MENU) {
			completeHostDelayedTap(
				tap.gestureId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction
			)
			return true
		}
		when (
			val result = dispatchPlayLikeCurlSingleTapAction(
				action = action,
				gestureId = tap.gestureId
			)
		) {
			ReaderPageTapDispatchResult.Settling,
			ReaderPageTapDispatchResult.TerminalPublished -> Unit
			is ReaderPageTapDispatchResult.CompleteInHost -> {
				completeHostDelayedTap(
					tap.gestureId,
					result.outcome
				)
			}
		}
		return true
	}

	private fun dispatchLegacySingleTapAction(action: KomikkuNavigationRegion) {
		if (
			canvasShellTransitionConsumesPageAction() &&
			action != KomikkuNavigationRegion.MENU
		) {
			return
		}
		if (action != KomikkuNavigationRegion.MENU) {
			onAction(action)
			return
		}
		if (shellCoverVisible) {
			onAction(action)
			return
		}
		onAction(KomikkuNavigationRegion.MENU)
	}

	private fun playLikeCurlPageChangeFor(action: KomikkuNavigationRegion): PageChange? {
		val direction = when (action) {
			KomikkuNavigationRegion.NEXT -> ReaderPageTurnDirection.Next
			KomikkuNavigationRegion.PREV -> ReaderPageTurnDirection.Previous
			KomikkuNavigationRegion.RIGHT -> readerTapZonePageTurnDirectionFor(
				ReaderTapZoneAction.Right,
				pageTurnReadingDirection
			)
			KomikkuNavigationRegion.LEFT -> readerTapZonePageTurnDirectionFor(
				ReaderTapZoneAction.Left,
				pageTurnReadingDirection
			)
			KomikkuNavigationRegion.MENU -> null
		}
		return when (direction) {
			ReaderPageTurnDirection.Next -> PageChange.NEXT
			ReaderPageTurnDirection.Previous -> PageChange.PREVIOUS
			null -> null
		}
	}

	private fun dispatchPlayLikeCurlSingleTapAction(
		action: KomikkuNavigationRegion,
		gestureId: Long
	): ReaderPageTapDispatchResult {
		val pageChange = playLikeCurlPageChangeFor(action)
		if (pageChange != null) {
			return when (tapTurnController.turn(gestureId, pageChange)) {
				ReaderPageTurnStartResult.Settling ->
					ReaderPageTapDispatchResult.Settling
				is ReaderPageTurnStartResult.TerminalPublished ->
					ReaderPageTapDispatchResult.TerminalPublished
			}
		}

		onAction(action)
		return ReaderPageTapDispatchResult.CompleteInHost(
			ReaderPageGestureTerminalOutcome.CompletedTapAction
		)
	}

	override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
		if (physicalDispatchMode == ReaderPagePhysicalDispatchMode.PlayLikeCurl) {
			return false
		}
		return interceptLegacyReaderPointerEvent(event)
	}

	private fun interceptLegacyReaderPointerEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				nativeTapCandidate = true
				nativeTapCancelledByDrag = false
				nativeTapLongConfirmed = false
				nativeSwipeIntercepted = false
				swipeStartX = event.x
				swipeStartY = event.y
				if (!shellCoverVisible) return true
				return false
			}
			MotionEvent.ACTION_MOVE -> {
				if (nativeTapMovedBeyondSlop(event.x, event.y)) {
					nativeTapCandidate = false
				}
				if (
					!horizontalSwipeDispatched &&
					nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)
				) {
					nativeSwipeIntercepted = true
					return true
				}
				return false
			}
			MotionEvent.ACTION_UP -> return nativeSwipeIntercepted
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				clearLegacyNativeTapState()
				return false
			}
			else -> return false
		}
	}

	override fun onTouchEvent(event: MotionEvent): Boolean = true

	private fun allowsCueMapInput(): Boolean = when (presentationInputPolicy) {
		ReaderPresentationInputPolicy.RecoveryOnly,
		ReaderPresentationInputPolicy.ChromeOnly,
		is ReaderPresentationInputPolicy.ClaimedCurl -> false
		ReaderPresentationInputPolicy.ShellCover,
		is ReaderPresentationInputPolicy.NativePage,
		ReaderPresentationInputPolicy.LiveEngine -> true
	}

	private fun pagePhysicalDispatchMode(): ReaderPagePhysicalDispatchMode =
		readerPagePhysicalDispatchMode(
			pageTurnCanvasEnabled = pageTurnCanvasEnabled,
			presentationInputPolicy = presentationInputPolicy,
			legacyLiveCompatibilityContext = legacyLiveCompatibilityContext,
			nativePageUsesLegacyRoute =
				presentationInputPolicy is ReaderPresentationInputPolicy.NativePage &&
					pageInputSettlementHostController.newPointerDecision() is
						ReaderPageNewPointerDecision.Accept &&
					!shouldUsePlayLikeCurlPointerRouter()
		)

	private fun shouldUsePlayLikeCurlPointerRouter(): Boolean =
		pageTurnCanvasEnabled && !verticalPageDragPreview

	private val physicalDispatchTarget = object : ReaderPagePhysicalDispatchTarget {
		override fun dispatchCueMap(event: MotionEvent): Boolean =
			whispersyncCueMapView.dispatchCuePointerEvent(event)

		override fun dispatchChromeOnly(event: MotionEvent): Boolean =
			dispatchChromeOnlyPointerEvent(event)

		override fun dispatchDenied(event: MotionEvent): Boolean = true

		override fun dispatchLegacy(event: MotionEvent): Boolean =
			dispatchLegacyReaderPointerEvent(event)

		override fun dispatchLegacyLive(event: MotionEvent): Boolean =
			dispatchLegacyLivePointerEvent(event)

		override fun dispatchPlayLikeCurl(event: MotionEvent): Boolean =
			dispatchPlayLikeCurlPointerEvent(event)

		override fun dispatchShellCover(event: MotionEvent): Boolean =
			dispatchShellCoverPointerEvent(event)

		override fun dispatchLiveEngine(event: MotionEvent): Boolean =
			viewerContentContainer.dispatchTouchEvent(event)
	}

	private fun legacyLivePointerContext() = ReaderLegacyLivePointerContext(
		pageTurnCanvasEnabled = pageTurnCanvasEnabled,
		presentationDecision = presentationDecision,
		compatibilityContext = legacyLiveCompatibilityContext,
		shellCoverVisible = shellCoverVisible
	)

	private fun cancelLegacyLivePointerStreamIfContextChanged() {
		if (!legacyLivePointerStream.revokeIfContextChanged(legacyLivePointerContext())) return
		cancelLegacyLivePointerStream(revoked = true)
	}

	private fun cancelLegacyLivePointerStream() {
		if (!legacyLivePointerStream.revoke()) return
		cancelLegacyLivePointerStream(revoked = true)
	}

	private fun cancelLegacyLivePointerStream(revoked: Boolean) {
		check(revoked)
		legacyLivePointerDown?.let { down ->
			val cancel = MotionEvent.obtain(down).apply {
				action = MotionEvent.ACTION_CANCEL
			}
			try {
				viewerContentContainer.dispatchTouchEvent(cancel)
			} finally {
				cancel.recycle()
			}
		}
		legacyLivePointerDown?.recycle()
		legacyLivePointerDown = null
		physicalDispatchMode = ReaderPagePhysicalDispatchMode.Denied
	}

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		if (event.actionMasked == MotionEvent.ACTION_DOWN) {
			check(physicalDispatchMode == null) {
				"A physical pointer dispatch mode is already active"
			}
			val context = legacyLivePointerContext()
			physicalDispatchMode = when {
				allowsCueMapInput() &&
					whispersyncCueMapView.hitTest(event.x, event.y) != null ->
					ReaderPagePhysicalDispatchMode.CueMap
				else -> pagePhysicalDispatchMode()
			}
			legacyLivePointerStream.begin(
				mode = checkNotNull(physicalDispatchMode),
				context = context
			)
			pageRasterPreparationController.onPointerInteractionChanged(true)
		}

		val terminal =
			event.actionMasked == MotionEvent.ACTION_UP ||
				event.actionMasked == MotionEvent.ACTION_CANCEL
		val handled = if (
			terminal && legacyLivePointerStream.suppressesOriginalTerminal
		) {
			true
		} else {
			readerDispatchPagePhysicalEvent(
				mode = physicalDispatchMode,
				event = event,
				target = physicalDispatchTarget,
				fallback = { fallbackEvent -> super.dispatchTouchEvent(fallbackEvent) }
			)
		}

		if (terminal) {
			pageRasterPreparationController.onPointerInteractionChanged(false)
			legacyLivePointerDown?.recycle()
			legacyLivePointerDown = null
			legacyLivePointerStream.finish()
			physicalDispatchMode = null
		}
		return handled
	}

	private fun dispatchLegacyLivePointerEvent(event: MotionEvent): Boolean {
		if (event.actionMasked == MotionEvent.ACTION_DOWN) {
			legacyLivePointerDown?.recycle()
			legacyLivePointerDown = MotionEvent.obtain(event)
		}
		return viewerContentContainer.dispatchTouchEvent(event)
	}

	private fun dispatchShellCoverPointerEvent(event: MotionEvent): Boolean {
		shellCoverView?.dispatchTouchEvent(event)
		handleSwipeTouchEvent(event)
		if (
			!horizontalSwipeDispatched &&
			!nativeSwipeIntercepted &&
			!nativeTapCancelledByDrag
		) {
			legacyGestureDetector.onTouchEvent(event)
		}
		if (
			event.actionMasked == MotionEvent.ACTION_UP ||
			event.actionMasked == MotionEvent.ACTION_CANCEL
		) {
			clearLegacyNativeTapState()
		}
		return true
	}

	private fun dispatchLegacyReaderPointerEvent(event: MotionEvent): Boolean {
		val handled = super.dispatchTouchEvent(event)
		handleSwipeTouchEvent(event)
		if (
			!horizontalSwipeDispatched &&
			!nativeSwipeIntercepted &&
			!nativeTapCancelledByDrag
		) {
			legacyGestureDetector.onTouchEvent(event)
		}
		val consumed = handled || nativeSwipeIntercepted || horizontalSwipeDispatched
		if (
			event.actionMasked == MotionEvent.ACTION_UP ||
			event.actionMasked == MotionEvent.ACTION_CANCEL
		) {
			clearLegacyNativeTapState()
		}
		return consumed
	}

	private fun dispatchChromeOnlyPointerEvent(event: MotionEvent): Boolean {
		val pointerEvent = readerPageHostPointerEvent(event) ?: return true
		return pageInputSettlementHostController.dispatchChromeOnlyPointer(pointerEvent)
	}

	private fun readerPageHostPointerEvent(event: MotionEvent): ReaderPageHostPointerEvent? =
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> ReaderPageHostPointerEvent.Down(
				x = event.x,
				y = event.y,
				downTimeMillis = event.downTime
			)
			MotionEvent.ACTION_MOVE -> ReaderPageHostPointerEvent.Move(
				x = event.x,
				y = event.y,
				touchSlop = touchSlopPx
			)
			MotionEvent.ACTION_UP -> ReaderPageHostPointerEvent.PositionedUp(
				x = event.x,
				y = event.y,
				touchSlop = touchSlopPx,
				eventTimeMillis = event.eventTime
			)
			MotionEvent.ACTION_CANCEL -> ReaderPageHostPointerEvent.Cancel
			MotionEvent.ACTION_POINTER_DOWN ->
				ReaderPageHostPointerEvent.SecondaryPointerDown
			MotionEvent.ACTION_POINTER_UP ->
				ReaderPageHostPointerEvent.SecondaryPointerUp
			else -> null
		}

	private fun dispatchPlayLikeCurlPointerEvent(event: MotionEvent): Boolean {
		val pointerDispatch = readerPageHostPointerEvent(event)?.let(
			pageInputSettlementHostController::dispatchPointer
		)
		return if (pointerDispatch != null) {
			applyPointerRoute(event, pointerDispatch)
		} else if (shouldDispatchToViewerContent) {
			viewerContentContainer.dispatchTouchEvent(event)
		} else {
			true
		}
	}

	private fun applyPointerRoute(
		event: MotionEvent,
		dispatch: ReaderPageHostPointerDispatchResult
	): Boolean {
		updateGestureDiagnostic(event, dispatch)
		return when (val route = dispatch.route) {
		ReaderPagePointerRoute.Content -> {
			if (event.actionMasked == MotionEvent.ACTION_DOWN) {
				retainedContentDown?.recycle()
				retainedContentDown = MotionEvent.obtain(event)
				shouldDispatchToViewerContent =
					!shouldSuppressViewerContentInput
			}
			revokeViewerContentPointerStreamIfSuppressed(event)
			if (shouldDispatchToViewerContent) {
				viewerContentContainer.dispatchTouchEvent(event)
			}
			playLikeCurlGestureDetector.onTouchEvent(event)
			if (
				event.actionMasked == MotionEvent.ACTION_UP ||
				event.actionMasked == MotionEvent.ACTION_CANCEL
			) {
				recycleRetainedContentDown()
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}
			true
		}
		is ReaderPagePointerRoute.ContentTerminal -> {
			revokeViewerContentPointerStreamIfSuppressed(event)
			val handled = if (shouldDispatchToViewerContent) {
				viewerContentContainer.dispatchTouchEvent(event)
			} else {
				true
			}
			playLikeCurlGestureDetector.onTouchEvent(event)
			completeHostGesture(
				route.gestureId,
				route.outcome
			)
			recycleRetainedContentDown()
			clearPlayLikeCurlPointerTapFlagsAfterUp()
			handled
		}
		is ReaderPagePointerRoute.ClaimCurl -> {
			whispersyncCueMapView.cancelForCurl()
			dispatchContentCancel(event)
			val originalDown = checkNotNull(retainedContentDown) {
				"Curl claim has no retained content DOWN"
			}
			val downResult = playLikeCurlController.onPageTouchEvent(
				originalDown,
				route.gestureId
			)
			recycleRetainedContentDown()
			when (downResult) {
				ReaderPageCurlDispatchResult.Accepted -> {
					val moveResult = dispatchClaimedReaderPageCurlEvent(event) {
						dispatchedEvent ->
						playLikeCurlController.onPageTouchEvent(
							dispatchedEvent,
							route.gestureId
						)
					}
					when (moveResult) {
						ReaderPageCurlDispatchResult.Accepted -> {
							if (
								event.actionMasked == MotionEvent.ACTION_UP ||
								event.actionMasked == MotionEvent.ACTION_CANCEL
							) {
								playLikeCurlGestureOwned = false
								clearPlayLikeCurlPointerTapFlagsAfterUp()
							} else {
								playLikeCurlGestureOwned = true
							}
						}
						ReaderPageCurlDispatchResult.TerminalPublished -> {
							playLikeCurlGestureOwned = false
							clearPlayLikeCurlPointerTapFlagsAfterUp()
						}
					}
				}
				ReaderPageCurlDispatchResult.TerminalPublished -> {
					playLikeCurlGestureOwned = false
					clearPlayLikeCurlPointerTapFlagsAfterUp()
				}
			}
			true
		}
		is ReaderPagePointerRoute.Curl -> {
			playLikeCurlController.onPageTouchEvent(event, route.gestureId)
			if (
				event.actionMasked == MotionEvent.ACTION_UP ||
				event.actionMasked == MotionEvent.ACTION_CANCEL
			) {
				playLikeCurlGestureOwned = false
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}
			true
		}
		is ReaderPagePointerRoute.Terminal -> {
			dispatchContentCancel(event)
			recycleRetainedContentDown()
			playLikeCurlGestureOwned = false
			true
		}
		ReaderPagePointerRoute.Consume -> true
		ReaderPagePointerRoute.Ignore -> true
		}
	}

	private fun updateGestureDiagnostic(
		event: MotionEvent,
		dispatch: ReaderPageHostPointerDispatchResult
	) {
		val gestureId = dispatch.gestureId ?: return
		val context = gestureDiagnostics[gestureId] ?: return
		when (dispatch.route) {
			ReaderPagePointerRoute.Content,
			is ReaderPagePointerRoute.ContentTerminal ->
				context.owner = ReaderPagePointerOwnership.Content
			is ReaderPagePointerRoute.ClaimCurl,
			is ReaderPagePointerRoute.Curl -> {
				context.owner = ReaderPagePointerOwnership.Curl
				if (context.physicalDirection == null && event.x != context.downX) {
					val physical = if (event.x < context.downX) {
						ReaderPagePhysicalDirection.Left
					} else {
						ReaderPagePhysicalDirection.Right
					}
					context.physicalDirection = physical
					context.logicalDirection = when {
						pageTurnReadingDirection == "rtl" &&
							physical == ReaderPagePhysicalDirection.Left ->
							ReaderPageTurnDirection.Previous
						pageTurnReadingDirection == "rtl" ->
							ReaderPageTurnDirection.Next
						physical == ReaderPagePhysicalDirection.Left ->
							ReaderPageTurnDirection.Next
						else -> ReaderPageTurnDirection.Previous
					}
				}
			}
			else -> Unit
		}
	}

	private fun revokeViewerContentPointerStreamIfSuppressed(source: MotionEvent) {
		if (!shouldSuppressViewerContentInput) return
		suppressViewerContentPointerStream(source)
	}

	private fun suppressViewerContentPointerStream() {
		suppressViewerContentPointerStream(source = null)
	}

	private fun suppressViewerContentPointerStream(source: MotionEvent?) {
		if (!shouldDispatchToViewerContent) return
		val retainedDown = retainedContentDown
		if (retainedDown == null) {
			shouldDispatchToViewerContent = false
			return
		}
		shouldDispatchToViewerContent = false
		val cancel = MotionEvent.obtain(source ?: retainedDown).apply {
			action = MotionEvent.ACTION_CANCEL
		}
		try {
			viewerContentContainer.dispatchTouchEvent(cancel)
		} finally {
			cancel.recycle()
		}
	}

	private fun dispatchContentCancel(source: MotionEvent? = null) {
		val retainedDown = retainedContentDown ?: return
		suppressViewerContentPointerStream(source)
		val cancel = MotionEvent.obtain(source ?: retainedDown).apply {
			action = MotionEvent.ACTION_CANCEL
		}
		try {
			playLikeCurlGestureDetector.cancelForDrag(cancel)
		} finally {
			cancel.recycle()
		}
	}

	private fun recycleRetainedContentDown() {
		retainedContentDown?.recycle()
		retainedContentDown = null
		shouldDispatchToViewerContent = false
	}

	private fun clearPlayLikeCurlPointerTapFlagsAfterUp() {
		nativeTapCandidate = false
		nativeTapLongConfirmed = false
	}

	private fun handleSwipeTouchEvent(event: MotionEvent) {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				swipeStartX = event.x
				swipeStartY = event.y
				horizontalSwipeDispatched = false
				nativeTapCancelledByDrag = false
				shellCoverDragDiagnosticLogged = false
				nativeDragPreviewDiagnosticLogged = false
			}
			MotionEvent.ACTION_MOVE -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy, event)
					logReaderDragCandidate(dx, dy)
					if (nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)) {
						nativeTapCancelledByDrag = true
						if (canvasShellTransitionConsumesPageAction()) {
						} else if (shellCoverVisible) {
							updateShellCoverDragOffset(dx)
						} else {
							updateReadableViewerDragOffset(dx, dy, ReaderPageDragPreviewPhase.Update)
							logReaderReadableDragPreview(dx, dy)
						}
					}
				}
			}
			MotionEvent.ACTION_UP -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy, event)
					logReaderDragCandidate(dx, dy)
					if (nativeTapCancelledByDrag || nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)) {
						if (canvasShellTransitionConsumesPageAction()) {
							dispatchHorizontalSwipeViewerAction(
								deltaX = dx,
								deltaY = dy
							)
						} else if (shellCoverVisible) {
							updateShellCoverDragOffset(dx)
							dispatchHorizontalSwipeViewerAction(
								deltaX = dx,
								deltaY = dy
							)
						} else {
							logReaderReadableDragPreview(dx, dy)
							val readableSwipeAction = readableSwipeAction(
								deltaX = dx,
								deltaY = dy,
								thresholdPx = readableDragActivationSlopPx()
							)
							updateReadableViewerDragOffset(
								deltaX = dx,
								deltaY = dy,
								phase = if (readableSwipeAction != null) {
									ReaderPageDragPreviewPhase.Release
								} else {
									ReaderPageDragPreviewPhase.Cancel
								}
							)
							dispatchHorizontalSwipeViewerAction(
								deltaX = dx,
								deltaY = dy
							)
						}
					}
				}
				clearSwipeTouchState()
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				if (!shellCoverVisible) {
					cancelReadableViewerDragPreview()
				}
				clearSwipeTouchState()
			}
		}
	}

	private fun canvasShellTransitionConsumesPageAction(): Boolean =
		startupShellHandoff.consumesCanvasShellPageAction(
			shellVisible = shellCoverVisible,
			canvasEnabled = pageTurnCanvasEnabled
		)

	private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {
		val thresholdPx = readerSwipeThresholdPx(shellCoverVisible)
		val action = if (shellCoverVisible) {
			readerShellCoverSwipeAction(deltaX, deltaY, thresholdPx)
		} else {
			readableSwipeAction(deltaX, deltaY, thresholdPx)
		} ?: return false
		horizontalSwipeDispatched = true
		nativeTapCandidate = false
		nativeTapCancelledByDrag = true
		nativeSwipeIntercepted = true
		if (canvasShellTransitionConsumesPageAction()) return true
		if (shellCoverVisible) {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover swipe action=$action dx=$deltaX dy=$deltaY threshold=$thresholdPx"
			)
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover command action=$action"
			)
		} else {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader native readable swipe action=$action dx=$deltaX dy=$deltaY threshold=$thresholdPx"
			)
		}
		if (shellCoverVisible) {
			when (action) {
				ReaderTapZoneAction.Right -> onAction(KomikkuNavigationRegion.NEXT)
				ReaderTapZoneAction.Left -> onAction(KomikkuNavigationRegion.PREV)
				else -> return false
			}
		} else {
			when (action) {
				ReaderTapZoneAction.Right -> onAction(KomikkuNavigationRegion.RIGHT)
				ReaderTapZoneAction.Left -> onAction(KomikkuNavigationRegion.LEFT)
				else -> return false
			}
		}
		return true
	}

	private fun updateShellCoverDragOffset(deltaX: Float) {
		shellCoverView?.translationX = deltaX
	}

	private fun updateReadableViewerDragOffset(
		deltaX: Float,
		deltaY: Float,
		phase: ReaderPageDragPreviewPhase
	) {
		onReadableDragPreview(deltaX, deltaY, width, height, phase)
	}

	private fun cancelReadableViewerDragPreview() {
		onReadableDragPreview(0f, 0f, width, height, ReaderPageDragPreviewPhase.Cancel)
	}

	private fun logReaderDragCandidate(deltaX: Float, deltaY: Float) {
		val thresholdPx = readerSwipeThresholdPx(shellCoverVisible = shellCoverVisible)
		val magnitude = if (shellCoverVisible || !verticalPageDragPreview) {
			abs(deltaX)
		} else {
			abs(deltaY)
		}
		if (shellCoverDragDiagnosticLogged || magnitude <= thresholdPx) return
		shellCoverDragDiagnosticLogged = true
		val label = if (shellCoverVisible) {
			"Reader shell cover drag candidate"
		} else {
			"Reader native drag candidate"
		}
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"$label dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
		)
	}

	private fun logReaderReadableDragPreview(deltaX: Float, deltaY: Float) {
		val magnitude = if (verticalPageDragPreview) abs(deltaY) else abs(deltaX)
		if (nativeDragPreviewDiagnosticLogged || magnitude <= touchSlopPx) return
		nativeDragPreviewDiagnosticLogged = true
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native drag preview dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
		)
	}

	private fun dispatchPageHostLifecycleEvent(
		event: ReaderPageHostLifecycleEvent,
		preserveDestinationDeck: Boolean = false
	): List<Long> {
		val cancelledGestures = readerDispatchPageHostLifecycleEvent(
			event = event,
			preserveDestinationDeck = preserveDestinationDeck,
			clearDestinationDeckPrewarm = ::clearDestinationDeckPrewarm,
			dispatchInputLifecycle = pageInputSettlementHostController::onLifecycleEvent
		)
		nativePagePresentationPublisher.update()
		return cancelledGestures
	}

	private fun completeHostGesture(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean = pageInputSettlementHostController.complete(
		gestureId,
		outcome
	)

	private fun emitGestureDiagnostic(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	) {
		val context = gestureDiagnostics.remove(gestureId)
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.gesture(
				readerSession = readerDiagnosticSession,
				gestureId = gestureId,
				outcome = outcome,
				owner = context?.owner ?: ReaderPagePointerOwnership.Terminal,
				rasterGeneration = playLikeCurlController.diagnosticRasterGeneration(),
				textureGeneration = playLikeCurlController.diagnosticTextureGeneration(),
				physicalDirection = context?.physicalDirection,
				logicalDirection = context?.logicalDirection,
				durationMs = context?.let {
					(SystemClock.uptimeMillis() - it.startedAtMillis).coerceAtLeast(0L)
				} ?: 0L
			)
		)
		if (readerPageGestureShouldShowBusyFeedback(outcome)) {
			onRendererBusyGestureRejected()
		}
	}

	private fun completeHostDelayedTap(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean = pageInputSettlementHostController.completeDelayedTap(
		gestureId,
		outcome
	)

	private fun completePageGesture(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	): Boolean {
		val won = completeHostGesture(
			gestureId,
			outcome
		)
		logGestureTerminal(
			gestureId = gestureId,
			outcome = outcome,
			detail = detail,
			won = won
		)
		return won
	}

	private fun logGestureTerminal(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail,
		won: Boolean
	) {
		val message = "Reader gesture terminal gestureId=$gestureId " +
			"outcome=$outcome won=$won detail=$detail"
		if (won) {
			Logger.i(KomikkuReaderNativeFrameHostTag, message)
		} else {
			Logger.w(
				KomikkuReaderNativeFrameHostTag,
				"Reader gesture terminal replay $message"
			)
		}
	}

	private fun cancelPendingLongTapForDrag(deltaX: Float, deltaY: Float, event: MotionEvent) {
		if (abs(deltaX) <= touchSlopPx && abs(deltaY) <= touchSlopPx) return
		if (!nativeTapCancelledByDrag) legacyGestureDetector.cancelForDrag(event)
		nativeTapCandidate = false
		nativeTapCancelledByDrag = true
	}

	private fun nativeTapMovedBeyondSlop(x: Float, y: Float): Boolean =
		abs(x - swipeStartX) > touchSlopPx || abs(y - swipeStartY) > touchSlopPx

	private fun nativeHorizontalSwipeMovedBeyondSlop(x: Float, y: Float): Boolean =
		if (shellCoverVisible) {
			readerShellCoverSwipeAction(
				deltaX = x - swipeStartX,
				deltaY = y - swipeStartY,
				thresholdPx = touchSlopPx
			) != null
		} else {
			readableSwipeAction(
				deltaX = x - swipeStartX,
				deltaY = y - swipeStartY,
				thresholdPx = readableDragActivationSlopPx()
			) != null
		}

	private fun readableDragActivationSlopPx(): Float = when {
		pageTurnCanvasEnabled && !verticalPageDragPreview -> touchSlopPx
		else -> readablePageDragSlopPx
	}

	private fun readableSwipeAction(
		deltaX: Float,
		deltaY: Float,
		thresholdPx: Float
	): ReaderTapZoneAction? =
		readerNativeReaderSwipeAction(
			deltaX = deltaX,
			deltaY = deltaY,
			thresholdPx = thresholdPx,
			verticalPageDragPreview = verticalPageDragPreview
		)

	private fun readerSwipeThresholdPx(shellCoverVisible: Boolean): Float =
		if (shellCoverVisible) {
			touchSlopPx
		} else {
			readablePageDragSlopPx
		}

	private fun clearLegacyNativeTapState(
		reason: ReaderPageLifecycleCancellationReason? = null
	) {
		if (reason == null) {
			legacyGestureDetector.cancelPendingLongTap()
		} else {
			legacyGestureDetector.cancel()
		}
		nativeTapCandidate = false
		nativeTapCancelledByDrag = false
		nativeTapLongConfirmed = false
		nativeSwipeIntercepted = false
	}

	private fun clearPlayLikeCurlNativeTapState(
		reason: ReaderPageLifecycleCancellationReason
	) {
		dispatchContentCancel()
		playLikeCurlGestureDetector.cancel()
		recycleRetainedContentDown()
		clearPlayLikeCurlPointerTapFlagsAfterUp()
		nativeTapCancelledByDrag = false
		nativeSwipeIntercepted = false
	}

	private fun clearSwipeTouchState() {
		shellCoverView?.translationX = 0f
		horizontalSwipeDispatched = false
		swipeStartX = 0f
		swipeStartY = 0f
		nativeDragPreviewDiagnosticLogged = false
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
			presentationViewportGeneration = Math.incrementExact(presentationViewportGeneration)
			reportPresentationIdentityIfAvailable()
		}
		if (oldw <= 0 || oldh <= 0 || (w == oldw && h == oldh)) return
		closePassiveRasterPreparationAdapter()
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ViewportChanged
		)
		playLikeCurlController.onHostSizeChanged()
		pageRasterPreparationController.invalidate("size-changed")
		requestPageTurnPrewarmWhenReady()
	}

	override fun onWindowVisibilityChanged(visibility: Int) {
		super.onWindowVisibilityChanged(visibility)
		reportPresentationWindowVisibility(visible = visibility == VISIBLE)
		if (!pageTurnCanvasEnabled) return
		if (visibility == VISIBLE) {
			val resumed =
				observedHostLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
			if (resumed) passiveRasterPreparationAdapter?.resume()
			pageRasterHostEventController.lifecycleResumedChanged(resumed)
			playLikeCurlController.onHostResumedChanged(resumed)
			requestPageTurnPrewarmWhenReady()
			return
		}
		passiveRasterPreparationAdapter?.pause()
		pageRasterHostEventController.lifecycleResumedChanged(false)
		playLikeCurlController.onHostResumedChanged(false)
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.WindowHidden
		)
		removePageTurnPrewarmLayoutListener()
		playLikeCurlController.onHostWindowHidden()
		pageRasterPreparationController.invalidate("window-hidden")
	}

	override fun onDetachedFromWindow() {
		closePassiveRasterPreparationAdapter()
		pageRasterHostEventController.webViewAttachmentChanged(false)
		pageRasterHostEventController.lifecycleResumedChanged(false)
		playLikeCurlController.onHostResumedChanged(false)
		beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.Detached)
		closePhysicalPointerDelivery()
		teardownTask4Resources()
		observedHostLifecycle?.removeObserver(hostLifecycleObserver)
		observedHostLifecycle = null
		super.onDetachedFromWindow()
	}

	fun canAcceptNewPointer(): Boolean = playLikeCurlController.isAvailable

	fun closeReader() {
		beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.ReaderClosed)
		closePhysicalPointerDelivery()
		teardownTask4Resources()
	}

	private fun beginFinalHostLifecycle(event: ReaderPageHostLifecycleEvent) {
		require(event in readerPageFinalHostLifecycleEvents) {
			"Non-final host event passed to final lifecycle gate: $event"
		}
		if (finalHostLifecycleEvent != null) return
		finalHostLifecycleEvent = event
		startupShellHandoff.close()
		val reason = event.cancellationReason()
		dispatchPageHostLifecycleEvent(event)
		clearLegacyNativeTapState(reason)
	}

	private fun closePhysicalPointerDelivery() {
		if (physicalPointerDeliveryClosed) return
		physicalPointerDeliveryClosed = true
		val event = checkNotNull(finalHostLifecycleEvent)
		val reason = event.cancellationReason()
		pageInputSettlementHostController.abandonPhysicalPointerStream(reason)
		cancelLegacyLivePointerStream()
		if (!legacyLivePointerStream.suppressesOriginalTerminal) {
			physicalDispatchMode = null
		}
	}

	private fun teardownTask4Resources() {
		if (task4ResourceTeardownStarted) return
		task4ResourceTeardownStarted = true
		nativePagePresentationPublisher.dispose()
		coldOwnershipAdmission.close()
		removePageTurnPrewarmLayoutListener()
		pageRasterHostEventController.close()
		val teardown = pageRasterPreparationController.destroy()
		task4Teardown = teardown
		teardown.invokeOnCompletion { failure ->
			val ownershipCloseFailure =
				runCatching(foregroundWebViewOwnership::close).exceptionOrNull()
			ownershipMainHandler.post {
				if (failure == null && ownershipCloseFailure == null) {
					ownershipProbe.request { result ->
						result.fold(
							onSuccess = { snapshot ->
								emitOwnershipDiagnostic(
									ReaderPageOwnershipPhase.AfterClose,
									snapshot
								)
							},
							onFailure = { unavailable ->
								emitOwnershipUnavailable(
									ReaderPageOwnershipPhase.AfterClose,
									(unavailable as
										ReaderPageOwnershipUnavailableException).reason
								)
							}
						)
					}
					return@post
				}
				val terminalFailure = failure ?: checkNotNull(ownershipCloseFailure)
				val typedFailure = terminalFailure as? ReaderPageTeardownException
					?: ReaderPageTeardownException(
						ReaderPageTeardownStage.BundleOwners,
						cause = terminalFailure
					)
				if (failure != null && ownershipCloseFailure != null && ownershipCloseFailure !== failure) {
					typedFailure.addSuppressed(ownershipCloseFailure)
				}
				Logger.e(
					KomikkuReaderNativeFrameHostTag,
					ReaderPageDiagnostic.teardownFailure(
						readerDiagnosticSession,
						typedFailure
					)
				)
			}
		}
	}
}

private fun View.findDescendantWebView(): WebView? {
	if (this is WebView) return this
	if (this !is ViewGroup) return null
	for (index in 0 until childCount) {
		getChildAt(index).findDescendantWebView()?.let { return it }
	}
	return null
}

internal class KomikkuGestureDetectorWithLongTap(
	context: Context,
	private val listener: Listener
) : GestureDetector(context, listener) {
	private val handler = Handler(Looper.getMainLooper())
	private val slop = ViewConfiguration.get(context).scaledTouchSlop
	private val doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
	private val longTapTime = ViewConfiguration.getLongPressTimeout().toLong()
	private val doubleTapTime = ViewConfiguration.getDoubleTapTimeout().toLong()
	private val doubleTapMinTime = AndroidGestureDoubleTapMinTimeMillis
	private var downX = 0f
	private var downY = 0f
	private var lastUp = 0L
	private var currentTapEligible = false
	private var previousTapEligible = false
	private var lastDownEvent: MotionEvent? = null
	private val longTapFn = Runnable {
		currentTapEligible = false
		lastDownEvent?.let(listener::onLongTapConfirmed)
	}

	fun cancelPendingLongTap() {
		handler.removeCallbacks(longTapFn)
	}

	fun cancel() {
		val event = lastDownEvent
		if (event == null) {
			resetTracking()
			return
		}
		cancelForDrag(event)
	}

	fun cancelForDrag(event: MotionEvent) {
		val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
		resetTracking()
		try {
			super.onTouchEvent(cancel)
		} finally {
			cancel.recycle()
		}
	}

	private fun resetTracking() {
		handler.removeCallbacks(longTapFn)
		currentTapEligible = false
		previousTapEligible = false
		lastDownEvent?.recycle()
		lastDownEvent = null
	}

	override fun onTouchEvent(ev: MotionEvent): Boolean {
		when (ev.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				val previousDown = lastDownEvent
				val elapsedSinceUp = ev.eventTime - lastUp
				val distanceX = previousDown?.let { ev.x - it.x } ?: 0f
				val distanceY = previousDown?.let { ev.y - it.y } ?: 0f
				val withinDoubleTapDistance =
					distanceX * distanceX + distanceY * distanceY <= doubleTapSlop * doubleTapSlop
				val isDoubleTapCandidate =
					previousTapEligible &&
						previousDown != null &&
						elapsedSinceUp in doubleTapMinTime..doubleTapTime &&
						withinDoubleTapDistance
				if (
					previousTapEligible &&
					previousDown != null &&
					elapsedSinceUp in 0L..doubleTapTime &&
					!isDoubleTapCandidate
				) {
					listener.onSingleTapSuperseded(previousDown)
				}
				previousDown?.recycle()
				lastDownEvent = MotionEvent.obtain(ev)
				currentTapEligible = true
				previousTapEligible = false
				if (!isDoubleTapCandidate) {
					downX = ev.x
					downY = ev.y
					handler.postDelayed(longTapFn, longTapTime)
				}
			}
			MotionEvent.ACTION_MOVE -> {
				if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) {
					currentTapEligible = false
					handler.removeCallbacks(longTapFn)
				}
			}
			MotionEvent.ACTION_UP -> {
				lastUp = ev.eventTime
				previousTapEligible = currentTapEligible
				currentTapEligible = false
				handler.removeCallbacks(longTapFn)
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				currentTapEligible = false
				previousTapEligible = false
				handler.removeCallbacks(longTapFn)
			}
		}
		return super.onTouchEvent(ev)
	}

	open class Listener : SimpleOnGestureListener() {
		open fun onSingleTapSuperseded(event: MotionEvent): Boolean = false

		open fun onLongTapConfirmed(event: MotionEvent) {
		}
	}
}

private class KomikkuReaderNativeNavigationOverlayView(context: Context) : View(context) {
	private var navigator: KomikkuReaderNavigator? = null
	private val regionPaint = Paint()
	private val textPaint = Paint().apply {
		textAlign = Paint.Align.CENTER
		color = Color.WHITE
		textSize = 48f
	}
	private val textBorderPaint = Paint().apply {
		textAlign = Paint.Align.CENTER
		color = Color.BLACK
		textSize = 48f
		style = Paint.Style.STROKE
		strokeWidth = 6f
	}

	fun setNavigation(navigator: KomikkuReaderNavigator) {
		this.navigator = navigator
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		navigator?.getRegions()?.forEach { region ->
			val rect = region.rectF
			val left = width * rect.left
			val top = height * rect.top
			val right = width * rect.right
			val bottom = height * rect.bottom
			regionPaint.color = region.type.colorArgb.toLong().toInt()
			canvas.drawRect(left, top, right, bottom, regionPaint)

			val centerX = left + (width * abs(rect.left - rect.right) / 2f)
			val centerY = top + (height * abs(rect.top - rect.bottom) / 2f)
			canvas.drawText(region.type.label, centerX, centerY, textBorderPaint)
			canvas.drawText(region.type.label, centerX, centerY, textPaint)
		}
	}
}
