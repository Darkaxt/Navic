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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import java.lang.ref.WeakReference
import java.util.UUID
import paige.navic.reader.ReaderCurrentDestinationQueryOutcome
import paige.navic.reader.ReaderCurrentDestinationQueryFailure
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderSemanticExecutableRequest
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticAsyncFailure
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
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderNativePagePresentationRequest
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationPublicationIdentity
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderRendererBusyFeedbackMaximumMillis
import paige.navic.reader.ReaderTapZoneAction
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionInputLease
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionFrameTargetSpecification
import paige.navic.reader.ReaderTransitionFrameGeometry
import paige.navic.reader.ReaderNativePageHostTokenState
import paige.navic.reader.ReaderPlayLikeCurlDeckTargetIdentity
import paige.navic.reader.ReaderBootstrapNativePageIntent
import paige.navic.reader.ReaderOwnerAndInputPublicationResult
import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderWhispersyncCueMapHoldOutcome
import paige.navic.reader.ReaderWhispersyncCueMapState
import paige.navic.reader.ReaderPresentationRetryBindingAttribution
import paige.navic.reader.admitsCurrentCoverRetryBindingReplacement
import paige.navic.reader.admitsExactSelectedRendererRemoval
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.publicationIdentity
import paige.navic.reader.readerNativeReaderSwipeAction
import paige.navic.reader.readerPresentationBindingEventCandidate
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationEventTransition
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

private object ReaderNativeHostProductionActivatedPortCapability :
	ReaderProductionActivatedPortCapability

internal class ReaderProductionActivatedSessionPorts private constructor(
	val gateway: ReaderActivatedGatewayPort,
	val semantic: ReaderSemanticCommandPort,
	val materialAllocation: ReaderMaterialGenerationAllocationPort,
	val raster: ReaderActivatedRasterPreparationPort,
	val deck: ReaderActivatedDeckPort,
	val frame: ReaderFramePresentationPort,
	val ownerAndInput: ReaderOwnerAndInputPublicationPort,
	val inputSafety: ReaderInputLeasePort,
	val resources: ReaderTransitionResourcePort,
	val releaseSink: ReaderReleaseOnlySinkPort,
	val lifecycleFacts: ReaderTask6LifecycleFactPort,
	val factOnlyTimer: ReaderTask6FactOnlyTimerPort,
	private val productionCapability: ReaderProductionActivatedPortCapability
) {
	init {
		require(productionCapability === ReaderNativeHostProductionActivatedPortCapability)
	}

	companion object {
		internal fun mint(
			gateway: ReaderActivatedGatewayPort,
			semantic: ReaderSemanticCommandPort,
			materialAllocation: ReaderMaterialGenerationAllocationPort,
			raster: ReaderActivatedRasterPreparationPort,
			deck: ReaderActivatedDeckPort,
			frame: ReaderFramePresentationPort,
			ownerAndInput: ReaderOwnerAndInputPublicationPort,
			inputSafety: ReaderInputLeasePort,
			resources: ReaderTransitionResourcePort,
			releaseSink: ReaderReleaseOnlySinkPort,
			lifecycleFacts: ReaderTask6LifecycleFactPort,
			factOnlyTimer: ReaderTask6FactOnlyTimerPort,
			productionCapability: ReaderProductionActivatedPortCapability
		): ReaderProductionActivatedSessionPorts = ReaderProductionActivatedSessionPorts(
			gateway = gateway,
			semantic = semantic,
			materialAllocation = materialAllocation,
			raster = raster,
			deck = deck,
			frame = frame,
			ownerAndInput = ownerAndInput,
			inputSafety = inputSafety,
			resources = resources,
			releaseSink = releaseSink,
			lifecycleFacts = lifecycleFacts,
			factOnlyTimer = factOnlyTimer,
			productionCapability = productionCapability
		)
	}
}

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
	return readerPresentationBindingEventCandidate(
		previousBinding = previousBinding,
		currentBinding = currentBinding,
		relocationPending = false
	)
}

internal enum class ReaderPresentationEpochPreparation {
	Rejected,
	Current,
	Replaced
}

internal data class ReaderPresentationReceiptAdmission(
	val receipt: ReaderPresentationEventReceipt,
	val postShellCoverVisible: Boolean
)

internal class ReaderPresentationBindingReporter : ReaderDeckAdmissionLeaseHost {
	var lastReportedBinding: ReaderPresentationBinding? = null
		private set
	var expectedReaderSessionGeneration: Long? = null
		private set
	private var expectedPublicationIdentity: ReaderPresentationPublicationIdentity? = null
	private var hostEpoch: Long = 1L
	private var authoritativeVersion: ReaderPresentationReceiptVersion? = null
	private var minimumComposeVersion: ReaderPresentationReceiptVersion? = null
	private var authoritativeState: ReaderPresentationState? = null
	private var authoritativeShellCoverVisible: Boolean = false
	private var authoritativeLifecycle: ReaderPresentationLifecycleState =
		ReaderPresentationLifecycleState.Foreground
	private var nextDeckAdmissionId = 0L

	fun reset(
		expectedReaderSessionGeneration: Long? = null,
		minimumComposeVersion: ReaderPresentationReceiptVersion? = null,
		initialPresentationState: ReaderPresentationState? = null,
		initialShellCoverVisible: Boolean = false
	) {
		require(expectedReaderSessionGeneration == null || expectedReaderSessionGeneration >= 0L)
		require(
			minimumComposeVersion == null ||
				minimumComposeVersion.readerSessionGeneration == expectedReaderSessionGeneration
		)
		hostEpoch = Math.incrementExact(hostEpoch)
		lastReportedBinding = null
		this.expectedReaderSessionGeneration = expectedReaderSessionGeneration
		expectedPublicationIdentity = null
		authoritativeVersion = null
		this.minimumComposeVersion = minimumComposeVersion
		authoritativeState = initialPresentationState
		authoritativeShellCoverVisible = initialShellCoverVisible
		authoritativeLifecycle = initialPresentationState?.lifecycle
			?: ReaderPresentationLifecycleState.Foreground
	}

	fun bindPublication(binding: ReaderPresentationBinding): Boolean {
		if (expectedReaderSessionGeneration == null) return false
		val identity = binding.publicationIdentity
		val expected = expectedPublicationIdentity
		if (expected != null && expected != identity) return false
		expectedPublicationIdentity = identity
		if (authoritativeState == null) {
			authoritativeState = ReaderPresentationState(binding = binding)
		}
		return true
	}

	fun admitsComposeHostUpdate(
		version: ReaderPresentationReceiptVersion,
		publicationChanged: Boolean
	): Boolean {
		val expectedSession = expectedReaderSessionGeneration ?: return true
		if (version.readerSessionGeneration != expectedSession) {
			return version.readerSessionGeneration > expectedSession
		}
		val minimumSequence = maxOf(
			authoritativeVersion?.eventSequence ?: -1L,
			minimumComposeVersion?.eventSequence ?: -1L
		)
		if (version.eventSequence < minimumSequence) return false
		return publicationChanged ||
			expectedPublicationIdentity == null ||
			expectedPublicationIdentity == version.publicationIdentity
	}

	fun consumeComposeVersion(version: ReaderPresentationReceiptVersion): Boolean =
		consumeComposeModel(
			version = version,
			state = authoritativeState,
			shellCoverVisible = authoritativeShellCoverVisible
		)

	fun consumeComposeModel(
		version: ReaderPresentationReceiptVersion,
		state: ReaderPresentationState?,
		shellCoverVisible: Boolean
	): Boolean {
		val expectedSession = expectedReaderSessionGeneration ?: return false
		val expectedPublication = expectedPublicationIdentity
		if (
			version.readerSessionGeneration != expectedSession ||
			version.publicationIdentity != expectedPublication ||
			version.eventSequence < (minimumComposeVersion?.eventSequence ?: -1L) ||
			version.eventSequence <= (authoritativeVersion?.eventSequence ?: -1L)
		) {
			return false
		}
		authoritativeVersion = version
		if (state != null) {
			authoritativeState = state
			authoritativeShellCoverVisible = shellCoverVisible
			authoritativeLifecycle = state.lifecycle
		}
		return true
	}

	fun matchesAuthoritativePresentationVersion(
		version: ReaderPresentationReceiptVersion
	): Boolean = authoritativeVersion == version

	/** Initial semantic authority is the genuine unbound Compose model, never its minimum
	 * version floor or an already-bound confirmation context relabelled as Neutral. */
	fun captureInitialSemanticContextVersionOrNull(): ReaderPresentationReceiptVersion? {
		val version = authoritativeVersion ?: return null
		val state = authoritativeState ?: return null
		if (expectedReaderSessionGeneration != version.readerSessionGeneration ||
			expectedPublicationIdentity != null || version.publicationIdentity != null ||
			lastReportedBinding != null || state.binding != null ||
			state.authority != ReaderPresentationAuthority.Unavailable ||
			state.rendererCleanupOwnership.isNotEmpty() || state.rendererSuccessorReceipt != null ||
			authoritativeLifecycle != ReaderPresentationLifecycleState.Foreground ||
			state.lifecycle != ReaderPresentationLifecycleState.Foreground ||
			readerPresentationDecision(state).frameOwner != ReaderPresentationFrameOwner.Neutral) return null
		return version
	}

	/** Consume the original command receipt on the original reporter, without legacy
	 * physical effects or a second receipt authority. */
	fun consumeInitialSemanticReceipt(
		epoch: Long,
		version: ReaderPresentationReceiptVersion,
		origin: ReaderPresentationEventOrigin.SemanticCommand,
		receipt: ReaderPresentationEventReceipt
	): Boolean {
		if (epoch != hostEpoch || captureInitialSemanticContextVersionOrNull() != version ||
			receipt.preVersion != version || receipt.origin !== origin ||
			origin.transitionId.operation != paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage ||
			origin.transitionId.expectedBinding !is paige.navic.reader.ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ||
			receipt.event !is ReaderPresentationEvent.FoliateRelocated || receipt.effects.isNotEmpty()) return false
		val semantic = receipt.semanticReceipt ?: return false
		if (semantic.transitionId != origin.transitionId || receipt.postState.binding != semantic.binding) return false
		val transition = readerPresentationEventTransition(checkNotNull(authoritativeState), version,
			authoritativeShellCoverVisible, receipt.event, origin)
		if (transition.receipt != receipt || !bindPublication(semantic.binding)) return false
		commitReceipt(ReaderPresentationReceiptAdmission(receipt, transition.shellCoverVisible))
		return true
	}

	fun captureSemanticContextVersionOrNull(binding: ReaderPresentationBinding): ReaderPresentationReceiptVersion? {
		val version = authoritativeVersion ?: return null
		val state = authoritativeState ?: return null
		if (expectedReaderSessionGeneration != version.readerSessionGeneration ||
			expectedPublicationIdentity != binding.publicationIdentity || version.publicationIdentity != binding.publicationIdentity ||
			lastReportedBinding != binding || state.binding != binding ||
			authoritativeLifecycle != ReaderPresentationLifecycleState.Foreground || state.lifecycle != ReaderPresentationLifecycleState.Foreground) return null
		return version
	}

	fun matchesAuthoritativePresentationDecision(decision: ReaderPresentationDecision): Boolean =
		authoritativeState?.let(::readerPresentationDecision) == decision

	override fun reserve(request: ReaderDeckAdmissionRequest): ReaderDeckAdmission? {
		val admissionId = Math.incrementExact(nextDeckAdmissionId)
		val capability = readerDeckAdmissionCapabilityOrNull(
			authority = captureDeckAdmissionAuthorityOrNull(),
			admissionId = admissionId,
			request = request
		) ?: return null
		nextDeckAdmissionId = admissionId
		return ReaderDeckAdmission(capability)
	}

	override fun currency(
		admission: ReaderDeckAdmission,
		observedDecision: ReaderPresentationDecision?
	): ReaderDeckAdmissionCurrency = readerDeckAdmissionAuthorityCurrency(
		authority = captureDeckAdmissionAuthorityOrNull(),
		admission = admission,
		observedDecision = observedDecision
	)

	override fun isCurrent(admission: ReaderDeckAdmissionCapability): Boolean =
		readerDeckAdmissionAuthorityMatches(
			authority = captureDeckAdmissionAuthorityOrNull(),
			admission = admission
		)

	override fun isOwnerCurrent(admission: ReaderDeckAdmissionCapability): Boolean =
		readerDeckAdmissionOwnerAuthorityMatches(
			authority = captureDeckAdmissionAuthorityOrNull(),
			admission = admission
		)

	private fun captureDeckAdmissionAuthorityOrNull(): ReaderDeckAdmissionAuthoritySnapshot? {
		val viewerGeneration = expectedReaderSessionGeneration ?: return null
		val version = authoritativeVersion ?: return null
		val state = authoritativeState ?: return null
		val binding = lastReportedBinding ?: return null
		if (
			authoritativeLifecycle != ReaderPresentationLifecycleState.Foreground ||
			state.lifecycle != ReaderPresentationLifecycleState.Foreground ||
			state.binding != binding ||
			expectedPublicationIdentity != binding.publicationIdentity ||
			version.readerSessionGeneration != viewerGeneration ||
			version.publicationIdentity != binding.publicationIdentity
		) return null
		return ReaderDeckAdmissionAuthoritySnapshot(
			hostEpoch = hostEpoch,
			authorityVersion = version,
			viewerGeneration = viewerGeneration,
			lifecycle = authoritativeLifecycle,
			bindingSeed = binding,
			presentationToken = readerPresentationDecision(state)
				.rendererCallbackTokenOrNull(),
			decision = readerPresentationDecision(state),
			rendererSuccessorReceipt = state.rendererSuccessorReceipt
		)
	}

	fun captureEpoch(): Long = hostEpoch

	fun classifyReceipt(
		epoch: Long,
		expectedEvent: ReaderPresentationEvent,
		receipt: ReaderPresentationEventReceipt?
	): ReaderPresentationReceiptAdmission? {
		if (receipt == null || epoch != hostEpoch || receipt.event != expectedEvent) return null
		val preState = authoritativeState ?: return null
		val preVersion = authoritativeVersion ?: minimumComposeVersion ?: return null
		val transition = readerPresentationEventTransition(
			preState = preState,
			preVersion = preVersion,
			shellCoverVisible = authoritativeShellCoverVisible,
			event = expectedEvent
		)
		if (receipt != transition.receipt) return null
		return ReaderPresentationReceiptAdmission(
			receipt = receipt,
			postShellCoverVisible = transition.shellCoverVisible
		)
	}

	fun commitReceipt(admission: ReaderPresentationReceiptAdmission) {
		val receipt = admission.receipt
		authoritativeVersion = receipt.version
		authoritativeState = receipt.postState
		authoritativeShellCoverVisible = admission.postShellCoverVisible
		lastReportedBinding = receipt.postState.binding
		authoritativeLifecycle = receipt.postState.lifecycle
	}

	fun consumeReceipt(
		epoch: Long,
		expectedEvent: ReaderPresentationEvent,
		receipt: ReaderPresentationEventReceipt?
	): Boolean {
		if (expectedEvent is ReaderPresentationEvent.Lifecycle) return false
		val admission = classifyReceipt(epoch, expectedEvent, receipt) ?: return false
		commitReceipt(admission)
		return true
	}

	fun update(
		confirmedTargetBinding: ReaderPresentationBinding?,
		currentBinding: ReaderPresentationBinding,
		publicationOpenPending: Boolean,
		relocationPending: Boolean,
		relocationAcknowledgement: ReaderPageTurnSettlementAck? = null,
		retryBindingReplacement: ReaderPresentationEvent.BindingReplaced? = null
	): ReaderPresentationEvent? {
		if (authoritativeLifecycle != ReaderPresentationLifecycleState.Foreground) return null
		if (currentBinding == lastReportedBinding) return null
		if (publicationOpenPending) {
			return ReaderPresentationEvent.PublicationOpened(currentBinding)
		}
		val previousBinding = lastReportedBinding ?: confirmedTargetBinding ?: return null
		if (currentBinding == previousBinding) return null
		if (retryBindingReplacement?.previousBinding == previousBinding &&
			retryBindingReplacement.binding == currentBinding &&
			authoritativeState?.admitsCurrentCoverRetryBindingReplacement(retryBindingReplacement) == true
		) return retryBindingReplacement
		if (
			authoritativeState?.admitsExactSelectedRendererRemoval(
				previousBinding = previousBinding,
				binding = currentBinding
			) == true
		) {
			return ReaderPresentationEvent.BindingReplaced(previousBinding, currentBinding)
		}
		return readerPresentationBindingEventCandidate(
			previousBinding = previousBinding,
			currentBinding = currentBinding,
			relocationPending = relocationPending,
			relocationAcknowledgement = relocationAcknowledgement
		)
	}
}

private fun ReaderPresentationEvent.hasBindingTransitionTarget(): Boolean =
	this is ReaderPresentationEvent.BindingCompleted ||
		this is ReaderPresentationEvent.BindingReplaced ||
		this is ReaderPresentationEvent.FoliateRelocated

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
					val request = (decision.authority as? ReaderPresentationAuthority.BlockingPreparation)
						?.nativePresentationRequest
					val retryStillCurrent =
						decision.targetBinding == effect.binding &&
						request?.binding == effect.binding &&
						request.token == effect.token &&
						request.retryAfterPreparationGeneration != null &&
						decision.diagnosticPresentation == ReaderDiagnosticPresentation.Hidden
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
	): Boolean = eligible && shellVisible && canvasEnabled

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
	val sessionGeneration = presentationVersion.readerSessionGeneration
	val shadowTransitionGateway = checkNotNull(LocalReaderTransitionGateway.current)
	if (!shadowTransitionGateway.admitsNormalSession(sessionGeneration)) return
	val activatedSessionStore = remember(sessionGeneration) { ReaderActivatedSessionSnapshotStore() }
	var nativeFrameRoot by remember(sessionGeneration) { mutableStateOf<KomikkuReaderNativeFrameRoot?>(null) }
	val semanticRequests = remember(activatedSessionStore, sessionGeneration) {
		ReaderSemanticExecutableRequestRegistry(sessionGeneration, activationStore = activatedSessionStore)
	}
	val semanticExecutor = remember(semanticRequests) { ReaderSemanticCommandExecutor(semanticRequests) }
	// Select before AndroidView construction or any competing legacy presentation effect.
	// Selection is private bootstrap custody, never semantic/layout/frame readiness.
	val productionCandidate = remember(shadowTransitionGateway, activatedSessionStore, sessionGeneration) {
		checkNotNull(shadowTransitionGateway.reserveActivatedCandidate(
			readerSessionGeneration = sessionGeneration,
			coordinatorEpoch = 1L,
			installationSource = activatedSessionStore,
			enqueue = { fact -> checkNotNull(nativeFrameRoot).enqueueActivatedFact(fact) },
			enqueueReceipt = { receipt -> checkNotNull(nativeFrameRoot).enqueueActivatedReceipt(receipt) },
			registerSemanticRequest = semanticRequests::register,
			discardSemanticReservation = semanticRequests::discardCandidateReservations,
			retireSemanticRequests = semanticExecutor::clear
		))
	}
	DisposableEffect(productionCandidate) {
		onDispose {
			shadowTransitionGateway.retireNormalProductionCandidate(productionCandidate)
			semanticExecutor.clear()
		}
	}
	val currentShadowTransitionGateway by rememberUpdatedState(shadowTransitionGateway)
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
	val currentSemanticReceiptIngress by rememberUpdatedState(LocalReaderSemanticReceiptIngress.current)
	val currentInitialSemanticReceiptIngress by rememberUpdatedState(LocalReaderInitialSemanticReceiptIngress.current)
	val currentInstalledPageTurnModelIngress by rememberUpdatedState(LocalReaderInstalledPageTurnModelIngress.current)
	val currentInstalledPhysicalPublicationIngress by rememberUpdatedState(LocalReaderInstalledPhysicalPublicationIngress.current)
	val currentReaderCompositionLocals by rememberUpdatedState(currentCompositionLocalContext)
	@Composable
	fun ReaderNestedContent(content: @Composable () -> Unit) {
		CompositionLocalProvider(currentReaderCompositionLocals) {
			CompositionLocalProvider(
				LocalReaderTransitionGateway provides currentShadowTransitionGateway,
				LocalReaderSemanticReceiptIngress provides currentSemanticReceiptIngress,
				LocalReaderInitialSemanticReceiptIngress provides currentInitialSemanticReceiptIngress,
				LocalReaderInstalledPageTurnModelIngress provides currentInstalledPageTurnModelIngress
			) { content() }
		}
	}
	var rendererBusyFeedbackToken by remember { mutableLongStateOf(0L) }
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

	key(sessionGeneration) {
	AndroidView(
		modifier = modifier,
		factory = { context ->
			KomikkuReaderNativeFrameRoot(context, activatedSessionStore).apply {
				nativeFrameRoot = this
				setOnStartupShellPrepared { currentOnStartupShellPrepared() }
				setComposeOverlay { ReaderNestedContent(hostedComposeOverlay) }
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
					presentationState,
					presentationVersion,
					presentationShellCoverVisible,
					destinationCommitIdentity,
					viewerKey
				) { event ->
					if (currentShadowTransitionGateway.observeLegacyPresentationEvent(event)) {
						currentOnPresentationEvent(event)
					} else {
						null
					}
				}
				setInitialSemanticReceiptIngress(presentationVersion, currentInitialSemanticReceiptIngress)
				setInstalledPageTurnModelIngress(currentInstalledPageTurnModelIngress)
				setInstalledPhysicalPublicationIngress(currentInstalledPhysicalPublicationIngress)
				prepareNormalProductionCandidate(productionCandidate, semanticRequests, semanticExecutor)
				setViewerContent(viewerKey) { ReaderNestedContent(currentViewerContent) }
				handlePresentationEffects(
					presentationEffects,
					presentationVersion
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
				presentationState,
				presentationVersion,
				presentationShellCoverVisible,
				destinationCommitIdentity,
				viewerKey
			) { event ->
				if (currentShadowTransitionGateway.observeLegacyPresentationEvent(event)) {
					currentOnPresentationEvent(event)
				} else {
					null
				}
			}
			root.setInitialSemanticReceiptIngress(presentationVersion, currentInitialSemanticReceiptIngress)
			root.setInstalledPageTurnModelIngress(currentInstalledPageTurnModelIngress)
			root.setInstalledPhysicalPublicationIngress(currentInstalledPhysicalPublicationIngress)
			root.handlePresentationEffects(
				presentationEffects,
				presentationVersion
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
			root.setViewerContent(viewerKey) { ReaderNestedContent(currentViewerContent) }
			root.setComposeOverlay { ReaderNestedContent(hostedComposeOverlay) }
			root.setOnRendererBusyGestureRejected(onRendererBusyGestureRejected)
			root.setOnViewerAction { action -> currentOnViewerAction(action) }
			root.setOnPageTurnBoundary { direction -> currentOnPageTurnBoundary(direction) }
			root.setOnReadableDragPreview { deltaX, deltaY, width, height, phase ->
				currentOnReadableDragPreview(deltaX, deltaY, width, height, phase)
			}
			root.setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
		},
			onRelease = { root ->
				root.closeReader()
				if (nativeFrameRoot === root) nativeFrameRoot = null
			}
		)
	}
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

/** Temporary source-static legacy invocation diagnostic, not material or readiness authority. */
internal enum class ReaderOriginalViewerContentOperation {
	CaptureReplacement, BeginReturn, ComposeConstruction, CompositionStrategy, SetContent,
	CancelPointer, ReplacementAdmission, ReplacementLifecycle, CurlInvalidation, RasterInvalidation,
	RemovePrewarmListener, ReportDetached, RetireCaptured, ReserveInstallation, MountInstallation,
	ReportAttached, RequestPrewarm, AcceptReplacement, CompleteReplacement, ReplacementCleanup, EndReturn
}

/** One enum cell for one original Root invocation, shared with its native replacement only. */
internal class ReaderOriginalViewerContentOperationWitness {
	var failure: ReaderOriginalViewerContentOperation? = null
		private set

	fun record(operation: ReaderOriginalViewerContentOperation) {
		if (failure == null) failure = operation
	}
}

private class KomikkuReaderNativeFrameRoot(
	context: Context,
	private val activatedSessionStore: ReaderActivatedSessionSnapshotStore
) : FrameLayout(context) {
	private val readerContainer = FrameLayout(context)
	private val viewerContainer = KomikkuReaderNativeViewerContainer(context, activatedSessionStore)
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
	private var originalViewerContentOperationWitness: ReaderOriginalViewerContentOperationWitness? = null
	fun originalViewerContentOperationFailure(): ReaderOriginalViewerContentOperation? =
		originalViewerContentOperationWitness?.failure
	private var initialSemanticIngress: Pair<ReaderPresentationReceiptVersion, ReaderInitialSemanticReceiptIngress>? = null
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
	private val presentationHostEffectApplier = ReaderPresentationHostEffectApplier(
		commitHostModel = { application ->
			presentationDecision = application.decision
			shellCoverVisible = application.layers.shellCover
		},
		applyViewerDecision = viewerContainer::applyPresentationDecision,
		applyShellCoverLayer = { application ->
			if (application.layers.shellCover) {
				shellCoverLayerController.selectCover(
					preserveNativePresentationProof = true
				)
			} else {
				shellCoverLayerController.coverHidden()
			}
		},
		applyViewerCoverVisibility = { application ->
			if (!application.layers.shellCover) {
				viewerContainer.setShellCoverVisible(
					visible = false,
					preserveNativePresentationProof = true
				)
			}
		},
		applyNativeCoverVisibility = ::updateNativeCoverVisibility
	)
	private var preparedShellCoverGeneration: Long? = null
	private val presentationWebViewVisualHandoff = ReaderWebViewVisualHandoff(
		host = ReaderWebViewVisualHandoffHostAdapter(viewerContainer::currentLiveWebView),
		presentedFrameSequenceSource = viewerContainer::nextLiveEnginePresentedFrameSequence
	)
	private val presentationCommitHost = object : ReaderPresentationCommitHost {
		override val liveMaterialWitnessRequired: Boolean get() = true
		override fun captureLiveFrameInstallation() = viewerContainer.captureLiveFrameInstallation()
		override fun confirmLiveFramePresentation(installation: ReaderFramePhysicalOwnershipAdapter.LiveInstallation,
			witness: ReaderLiveEnginePhysicalWitness, owner: paige.navic.reader.ReaderPresentationFrameOwner.LiveEngine): Boolean =
			viewerContainer.confirmLiveFramePresentation(installation, witness, owner)
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

		override fun applyPresentationFrameOwner(decision: ReaderPresentationDecision) {
			viewerContainer.applyPresentationFrameOwner(decision)
		}
	}
	private val presentationHostBridge = ReaderPresentationHostBridge(
		host = presentationCommitHost,
		liveEngineVisualHandoff = presentationWebViewVisualHandoff,
		liveEngineExposureRequired = viewerContainer::requiresLiveEngineExposureHandoff,
		tokenAllocator = viewerContainer.frameSourceTokenAllocator
	) { event -> viewerContainer.dispatchPresentationEvent(event) }

	/** Borrow the original owner for package composition; this does not install a session. */
	val factOnlyTimerPort: ReaderTask6FactOnlyTimerPort
		get() = presentationHostBridge.factOnlyTimerPort

	fun finalizeActivatedDeadlineHandoff(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult =
		presentationHostBridge.finalizeActivatedDeadlineHandoff(domain)

	private val frameOrHandoffPhysicalSource by lazy { viewerContainer.createFrameOrHandoffSource(presentationHostBridge) }
	private val originalPhysicalInventoryCapture = ReaderOriginalPhysicalInventoryCapture(
		admissionEpoch = viewerContainer::physicalInventoryAdmissionEpoch,
		freezeOriginalOwners = { domain, executor ->
			val native = viewerContainer.freezeOriginalPhysicalInventoryOwners(domain, executor)
			if (native != ReaderPortCommandResult.Accepted) native
			else frameOrHandoffPhysicalSource.freezeForTransitionActivation(domain)
		},
		captureOriginalOwners = { domain, executor ->
			val native = viewerContainer.snapshotOriginalPhysicalInventoryOwners(domain, executor)
			val frame = frameOrHandoffPhysicalSource.snapshotConnectedFrozenOwnership()
			val deadline = presentationHostBridge.snapshotConnectedFrozenDeadlineOwnership()
			if (native == null || frame == null || deadline == null) null
			else native + listOf(frame, deadline)
		},
		originalOwnersRestored = { executor -> viewerContainer.originalPhysicalAdmissionsRestored(executor) &&
			frameOrHandoffPhysicalSource.originalLegacyAdmissionsRestored }
	)
	private var nextOriginalCheckpointId = 1L
	private var normalProductionCandidate: ReaderTransitionGatewayCandidate? = null
	private var normalSemanticRequests: ReaderSemanticExecutableRequestRegistry? = null
	private var normalSemanticExecutor: ReaderSemanticCommandExecutor? = null
	private var normalOriginalPort: ReaderLegacyFreezeAndInventoryPort? = null
	private var activatedTransitionCoordinator: ReaderResumableTransitionCoordinator? = null
	private var normalActivationCoordinator: ReaderSessionActivationCoordinator? = null
	private var normalViewerContent: Pair<ReaderViewerKey, @Composable () -> Unit>? = null
	private var readerClosed = false

	/** Attachment, measured child geometry and the genuine initial Compose ingress are
	 * prerequisite edges, not readiness proofs. The original activation state guards both
	 * immediate and asynchronous completion; no layout tick retries an admitted attempt. */
	private fun startNormalProductionSessionIfEligible() {
		if (readerClosed || normalActivationCoordinator != null || !isAttachedToWindow ||
			width <= 0 || height <= 0 || viewerContainer.width <= 0 || viewerContainer.height <= 0 ||
			initialSemanticIngress == null) return
		val candidate = normalProductionCandidate?.takeIf { it.isSelected } ?: return
		val (viewerKey, content) = normalViewerContent ?: return
		if (viewerKey != presentationViewerKey || !viewerContainer.hasInitialCommandContext()) return
		val requests = checkNotNull(normalSemanticRequests)
		val executor = checkNotNull(normalSemanticExecutor)
		val domain = ReaderLegacyPhysicalDomain(candidate.readerSessionGeneration,
			ReaderLegacyFreezeToken(candidate.coordinatorEpoch))
		val ledger = ReaderTransitionReleaseLedger()
		val inputSafety = object : ReaderInputLeasePort {
			override fun narrowOrVeto(lease: ReaderTransitionInputLease): ReaderTransitionInputLease =
				viewerContainer.narrowInstalledInputLease(null, lease)

			override fun narrowOrVeto(command: ReaderTransitionCommand,
				lease: ReaderTransitionInputLease): ReaderTransitionInputLease {
				val coordinator = activatedTransitionCoordinator ?: return ReaderTransitionInputLease.None
				val currency = coordinator.capturePublicationCommandCurrency(command)
					?: return ReaderTransitionInputLease.None
				val requested = when (command) {
					is ReaderTransitionCommand.CommitOwnerAndInputLease -> command.requestedLease
					is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> command.requestedLease
					else -> return ReaderTransitionInputLease.None
				}
				if (lease != requested || !currency()) return ReaderTransitionInputLease.None
				val narrowed = viewerContainer.narrowInstalledInputLease(command, lease)
				return if (activatedTransitionCoordinator === coordinator && currency() && candidate.isSelected)
					narrowed else ReaderTransitionInputLease.None
			}
		}
		val publication = ReaderOwnerAndInputPublicationBarrier(
			narrowOrVeto = inputSafety::narrowOrVeto,
			publishAtomically = viewerContainer::canPublishInstalledOwnerAndInput)
		val resources = viewerContainer.createInstalledResourcePort(frameOrHandoffPhysicalSource, executor)
		val releaseSink = object : ReaderReleaseOnlySinkPort {
			override fun observe(fact: ReaderTransitionFact.ResourceObserved): ReaderPortCommandResult {
				enqueueActivatedFact(fact)
				return ReaderPortCommandResult.Accepted
			}
			override fun observeLegacy(imported: ReaderImportedLegacyResourceRegistration): ReaderPortCommandResult =
				if (ledger.importedFor(imported.registration) == imported) ReaderPortCommandResult.Accepted
				else ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
			override fun confirm(fact: ReaderTransitionFact.ResourceReleased): ReaderPortCommandResult {
				enqueueActivatedFact(fact)
				return ReaderPortCommandResult.Accepted
			}
			override fun confirmLegacy(physicalIdentity: ReaderLegacyPhysicalIdentity,
				fact: ReaderTransitionFact.ResourceReleased): ReaderPortCommandResult =
				if (ledger.confirmLegacyReleased(physicalIdentity, fact)) ReaderPortCommandResult.Accepted
				else ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
			override fun release(command: ReaderTransitionCommand.ReleaseResource): ReaderPortCommandResult =
				resources.release(command) { enqueueActivatedFact(it) }
		}
		val ports = ReaderProductionActivatedSessionPorts.mint(
			gateway = ReaderSnapshotBackedActivatedGatewayPort(candidate),
			semantic = executor,
			materialAllocation = viewerContainer.createOriginalInitialMaterialAllocationPort(candidate,
				{ command -> activatedTransitionCoordinator?.materialCommandIsCurrent(command) == true }, ::enqueueActivatedFact),
			raster = ReaderActivatedRasterPreparationPort { command, onFact ->
				viewerContainer.prepareInstalledRaster(command, domain,
					{ lease -> activatedTransitionCoordinator?.materialCommandIsCurrent(lease) == true }, onFact)
			},
			deck = ReaderActivatedDeckPort { command, onFact ->
				viewerContainer.reserveInstalledDeck(command, onFact) { specification, key ->
					checkNotNull(activatedTransitionCoordinator).enqueueFrameTargetDeclaration(specification, key)
				}
			},
			frame = viewerContainer.createInstalledFramePort(),
			ownerAndInput = object : ReaderOwnerAndInputPublicationPort {
				override fun publish(command: ReaderTransitionCommand.CommitOwnerAndInputLease) = publication.publish(command)
				override fun publish(command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease) = publication.publish(command)
			},
			inputSafety = inputSafety,
			resources = resources,
			releaseSink = releaseSink,
			lifecycleFacts = ReaderTask6LifecycleFactPort { fact ->
				val runtime = activatedTransitionCoordinator
				if (runtime == null) ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.ActivationPrerequisiteMissing)
				else {
					runtime.enqueue(when (fact.kind) {
						ReaderTask6LifecycleSafetyKind.VisibilityLost -> ReaderTransitionFact.VisibilityChanged(null, false)
						ReaderTask6LifecycleSafetyKind.HostDetached ->
							ReaderTransitionFact.ResourceLost(null, paige.navic.reader.ReaderTransitionCapabilityKind.Host)
						ReaderTask6LifecycleSafetyKind.WebViewDetached ->
							ReaderTransitionFact.ResourceLost(null, paige.navic.reader.ReaderTransitionCapabilityKind.WebView)
						ReaderTask6LifecycleSafetyKind.RendererUnavailable ->
							ReaderTransitionFact.ResourceLost(null, paige.navic.reader.ReaderTransitionCapabilityKind.Renderer)
						ReaderTask6LifecycleSafetyKind.PublicationClosed -> ReaderTransitionFact.PublicationClosed(null)
					})
					ReaderPortCommandResult.Accepted
				}
			},
			factOnlyTimer = factOnlyTimerPort,
			productionCapability = ReaderNativeHostProductionActivatedPortCapability)
		val activation = ReaderSessionActivationCoordinator(
			readerSessionGeneration = candidate.readerSessionGeneration,
			coordinatorEpoch = candidate.coordinatorEpoch,
			legacy = checkNotNull(normalOriginalPort),
			installationBarrier = ReaderActivatedSessionInstallationBarrier(activatedSessionStore),
			narrowInitialLease = { requested -> viewerContainer.originalNeutralCheckpointLease() ?: ReaderInitialPresentationInputLease.None },
			reserveNeutralBootstrap = {
				var reservation: ReaderReservedNeutralBootstrapRequest? = null
				val request: ReaderSemanticExecutableRequest = { origin, context, onReceipt ->
					createComposedInitialDestinationBootstrapRequest(viewerKey, candidate,
						checkNotNull(reservation), content)(origin, context, onReceipt)
				}
				requests.reserveNeutralBootstrap(domain, candidate, request).also { reservation = it }
			},
			discardNeutralBootstrap = { requests.discardNeutralBootstrap(it) },
			releaseLedger = ledger,
			onInstalledBeforeFinalization = {
				val installed = checkNotNull(activatedSessionStore.snapshot)
				activatedTransitionCoordinator = ReaderResumableTransitionCoordinator(
					ReaderActivatedTransitionPorts(ports), ReaderTransitionMode.Active, installed.journal,
					installed.releaseLedger, activationState = { checkNotNull(normalActivationCoordinator).state },
					onAcceptedPublication = { command, applied ->
						if (viewerContainer.refreshInstalledPresentationConsumers(command, applied)) {
							viewerContainer.installedPhysicalPublicationIngress?.invoke(command, applied)
						}
					})
				viewerContainer.bindInstalledPublicationReaders(
					readPublication = { publication.snapshot },
					commandCurrency = { candidate.isSelected &&
						activatedTransitionCoordinator?.snapshot()?.releaseOnlySink == false })
			},
			onActivated = {
				viewerContainer.activateInstalledFrameCommands()
				val reservation = checkNotNull(activatedSessionStore.reservedNeutralBootstrap)
				check(ports.gateway.routeIntent(ReaderTransitionFact.Intent(null,
					ReaderBootstrapNativePageIntent(reservation.handle))) == ReaderPortCommandResult.Accepted)
			})
		normalActivationCoordinator = activation // Retain before original drain/finalization can call out.
		activation.activate(ports, checkNotNull(viewerContainer.originalNeutralCheckpointLease()))
	}


	fun prepareNormalProductionCandidate(candidate: ReaderTransitionGatewayCandidate,
		requests: ReaderSemanticExecutableRequestRegistry, executor: ReaderSemanticCommandExecutor) {
		check(candidate.isSelected && candidate.installationSource === activatedSessionStore &&
			candidate.readerSessionGeneration == requests.readerSessionGeneration)
		normalProductionCandidate?.let { previous ->
			check(previous === candidate && normalSemanticRequests === requests && normalSemanticExecutor === executor)
			return
		}
		normalProductionCandidate = candidate
		normalSemanticRequests = requests
		normalSemanticExecutor = executor
		val domain = ReaderLegacyPhysicalDomain(candidate.readerSessionGeneration, ReaderLegacyFreezeToken(candidate.coordinatorEpoch))
		// Borrow and fence all sixteen ORIGINAL sources before ordinary viewer/settings
		// effects can start. No inventory, registration or selection is a readiness ACK.
		normalOriginalPort = createOriginalLegacyFreezeAndInventoryPort(domain, executor).also { it.freeze() }
	}

	fun enqueueActivatedFact(fact: ReaderTransitionFact) {
		checkNotNull(activatedTransitionCoordinator).enqueue(fact)
	}

	fun enqueueActivatedReceipt(receipt: ReaderPresentationEventReceipt) {
		checkNotNull(activatedTransitionCoordinator).enqueue(receipt)
	}

	fun createOriginalLegacyFreezeAndInventoryPort(domain: ReaderLegacyPhysicalDomain,
		executor: ReaderSemanticCommandExecutor): ReaderLegacyFreezeAndInventoryPort {
		check(nextOriginalCheckpointId < Long.MAX_VALUE)
		val id = ReaderLegacyRestorationCheckpointId(nextOriginalCheckpointId++)
		val frame = ReaderOriginalRestorationGroup(
			setOf(ReaderLegacyInventorySource.FrameOrHandoff, ReaderLegacyInventorySource.DeadlineRegistration),
			{ frameOrHandoffPhysicalSource.restoreAfterTransitionActivation(domain) },
			{ frameOrHandoffPhysicalSource.originalLegacyAdmissionsRestored }, {
				val epoch = viewerContainer.physicalInventoryAdmissionEpoch(domain)
				val currency: () -> Boolean = { epoch != null && viewerContainer.physicalInventoryAdmissionEpoch(domain) == epoch }
				currency
			})
		return ReaderOriginalLegacyFreezeAndInventoryPort(domain, executor, originalPhysicalInventoryCapture,
			activatedSessionStore, id, viewerContainer.originalRestorationGroups(domain, executor) + frame,
			captureNeutralLease = {
				if (presentationDecision?.frameOwner == ReaderPresentationFrameOwner.Neutral && presentationDecision?.targetBinding == null)
					viewerContainer.originalNeutralCheckpointLease() else null
			}, drainOriginal = { identity, confirmation -> drainPhysicalSourceForTransitionActivation(domain, identity, confirmation) },
			finalizeOriginal = { selected, confirmed ->
				val native = viewerContainer.finalizeOriginalPhysicalInventoryOwners(domain, executor, selected, confirmed)
				if (native != ReaderPortCommandResult.Accepted) native
				else {
					val frameSelected = selected?.takeIf { it.physicalIdentity.source == ReaderLegacyInventorySource.FrameOrHandoff }
					val frame = frameOrHandoffPhysicalSource.finalizeActivatedHandoff(domain.freezeToken, frameSelected,
						confirmed.filterTo(linkedSetOf()) { it.source == ReaderLegacyInventorySource.FrameOrHandoff })
					if (frame != ReaderPortCommandResult.Accepted) frame
					else presentationHostBridge.finalizeActivatedDeadlineHandoff(domain).also { result ->
						if (result == ReaderPortCommandResult.Accepted) originalPhysicalInventoryCapture.retire()
					}
				}
			})
	}

	fun freezePhysicalSourcesForTransitionActivation(domain: ReaderLegacyPhysicalDomain,
		executor: ReaderSemanticCommandExecutor): ReaderPortCommandResult =
		originalPhysicalInventoryCapture.freeze(domain, executor)

	fun snapshotPhysicalSourcesForTransitionActivation(domain: ReaderLegacyPhysicalDomain): ReaderLegacyResourceInventory =
		originalPhysicalInventoryCapture.snapshot(domain)

	private val transitionMainHandler = Handler(Looper.getMainLooper())
	fun drainPhysicalSourceForTransitionActivation(domain: ReaderLegacyPhysicalDomain,
		identity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult {
		if (identity.domain != domain) return ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource)
		val executor = originalPhysicalInventoryCapture.executorForFrozenDomain(domain)
			?: return ReaderPortCommandResult.Rejected(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource)
		val originalConfirmation: (ReaderLegacyPhysicalIdentity) -> Unit = { exact ->
			// Worker retirement is original physical evidence. Only its delivery moves
			// to the existing Android/main activation loop; never manufacture an ACK.
			if (Looper.myLooper() == Looper.getMainLooper()) onConfirmed(exact)
			else transitionMainHandler.post { onConfirmed(exact) }
		}
		return when (identity.source) {
			ReaderLegacyInventorySource.FrameOrHandoff -> frameOrHandoffPhysicalSource.drainFrozenOwnership(identity, originalConfirmation)
			ReaderLegacyInventorySource.DeadlineRegistration -> presentationHostBridge.drainFrozenOwnership(identity, originalConfirmation)
			else -> viewerContainer.drainOriginalPhysicalInventoryOwner(identity, executor, originalConfirmation)
		}
	}

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
		state: ReaderPresentationState,
		version: ReaderPresentationReceiptVersion,
		presentationShellCoverVisible: Boolean,
		destinationCommitIdentity: ReaderDestinationCommitIdentity?,
		viewerKey: ReaderViewerKey,
		onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
	) {
		val viewerChanged = presentationViewerKey != viewerKey
		when (
			viewerContainer.preparePresentationEpoch(
				version = version,
				state = state,
				shellCoverVisible = presentationShellCoverVisible,
				publicationChanged = viewerChanged,
				viewerReplacementPending = currentViewerKey != viewerKey
			)
		) {
			ReaderPresentationEpochPreparation.Rejected -> return
			ReaderPresentationEpochPreparation.Replaced -> presentationHostBridge.onHostDetached()
			ReaderPresentationEpochPreparation.Current -> Unit
		}
		if (viewerChanged) presentationViewerKey = viewerKey
		viewerContainer.setPresentationDecision(
			decision = decision,
			state = state,
			version = version,
			presentationShellCoverVisible = presentationShellCoverVisible,
			destinationCommitIdentity = destinationCommitIdentity,
			onEvent = onEvent,
			onAuthoritativeHostEffect = presentationHostEffectApplier::apply
		)
		presentationDecision?.let(presentationHostBridge::update)
	}

	fun handlePresentationEffects(
		effects: List<ReaderPendingPresentationEffect>,
		version: ReaderPresentationReceiptVersion,
		onHandled: (ReaderPresentationEffectIdentity) -> Unit
	) {
		if (!viewerContainer.matchesAuthoritativePresentationVersion(version)) return
		val authoritativeDecision = presentationDecision ?: return
		presentationEffectHandler.deliver(effects, authoritativeDecision, onHandled)
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

	private fun updateNativeCoverVisibility(application: ReaderNativePresentationApplication) {
		updateNativeCoverVisibilityLayers(application)
		presentationHostBridge.update(application.decision)
	}

	private fun updateNativeCoverVisibility() {
		updateNativeCoverVisibilityLayers(
			presentationDecision?.let(::readerNativePresentationApplication)
		)
	}

	private fun updateNativeCoverVisibilityLayers(
		application: ReaderNativePresentationApplication?
	) {
		val layers = application?.layers
			?: ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			)
		val shellCoverPrepared = preparedShellCoverGeneration != null
		val neutralPredecessor = application?.decision?.let { decision ->
			decision.authority == ReaderPresentationAuthority.Unavailable &&
				decision.frameOwner == ReaderPresentationFrameOwner.Neutral
		} == true
		shellCoverView.visibility =
			if (layers.shellCover || shellCoverPrepared || neutralPredecessor) {
				VISIBLE
			} else {
				GONE
			}
		shellCoverView.isClickable =
			layers.shellCover &&
				application?.inputPolicy == ReaderPresentationInputPolicy.ShellCover
		pagePreparationShieldView.visibility =
			if (layers.preparationShield) VISIBLE else GONE
		val trace =
			"authority=${application?.decision?.authority?.let { it::class.simpleName } ?: "None"} " +
				"layer=${application?.decision?.layer?.name ?: "None"} " +
				"shell=${layers.shellCover} coverPrepared=$shellCoverPrepared " +
				"neutralPredecessor=$neutralPredecessor " +
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

	fun setOnViewerAction(onAction: (KomikkuNavigationRegion) -> ReaderPresentationEventReceipt?) {
		viewerContainer.setOnViewerAction(onAction)
	}

	fun setOnPageTurnBoundary(onBoundary: (ReaderPageTurnDirection) -> ReaderPresentationEventReceipt?) {
		viewerContainer.setOnPageTurnBoundary(onBoundary)
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
		viewerContainer.setPageTurnCanvasEnabled(enabled) {
			presentationDecision?.let(presentationHostBridge::update)
		}
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

	fun setInstalledPhysicalPublicationIngress(ingress: ReaderInstalledPhysicalPublicationIngress?) {
		viewerContainer.installedPhysicalPublicationIngress = ingress
	}

	fun setInstalledPageTurnModelIngress(ingress: ReaderInstalledPageTurnModelIngress?) {
		viewerContainer.installedPageTurnModelIngress = ingress
		viewerContainer.installedPointerTurnRoute = route@{ direction, gestureId ->
			val candidate = normalProductionCandidate?.takeIf { it.isSelected } ?: return@route false
			val requests = normalSemanticRequests ?: return@route false
			viewerContainer.beginInstalledPointerSemanticTurn(candidate, requests, direction, gestureId)
		}
	}

	fun setInitialSemanticReceiptIngress(
		version: ReaderPresentationReceiptVersion,
		ingress: ReaderInitialSemanticReceiptIngress?
	) {
		if (!viewerContainer.matchesAuthoritativePresentationVersion(version)) return
		initialSemanticIngress = if (version.publicationIdentity == null) ingress?.let { version to it } else null
		startNormalProductionSessionIfEligible()
	}

	/** Capture the distinct initial Compose ingress for this actual command invocation.
	 * The existing Native admission still owns reservation, epoch, construction and query custody. */
	fun createComposedInitialDestinationBootstrapRequest(
		viewerKey: ReaderViewerKey,
		candidate: ReaderTransitionGatewayCandidate,
		reservation: ReaderReservedNeutralBootstrapRequest,
		content: @Composable () -> Unit
	): ReaderSemanticExecutableRequest = request@{ origin, context, onReceipt ->
		val captured = initialSemanticIngress ?: return@request ReaderSemanticExecutableResult.Rejected
		val version = captured.first
		if (version.publicationIdentity != null || version.readerSessionGeneration != origin.transitionId.readerSessionGeneration ||
			!viewerContainer.matchesAuthoritativePresentationVersion(version)) return@request ReaderSemanticExecutableResult.Rejected
		val pureInitialIngress: ReaderInitialSemanticReceiptIngress = { event, semanticOrigin ->
			if (initialSemanticIngress?.first != version || semanticOrigin !== origin ||
				!viewerContainer.matchesAuthoritativePresentationVersion(version)) null
			else captured.second(event, semanticOrigin)
		}
		createInitialDestinationBootstrapRequest(viewerKey, candidate, reservation, content, pureInitialIngress)(origin, context, onReceipt)
	}

	/** Original Root custody for the command-created Compose installation. This is not
	 * legacy replacement, semantic confirmation, or physical presentation acceptance. */
	fun createInitialDestinationBootstrapRequest(
		viewerKey: ReaderViewerKey,
		candidate: ReaderTransitionGatewayCandidate,
		reservation: ReaderReservedNeutralBootstrapRequest,
		content: @Composable () -> Unit,
		ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
	): ReaderSemanticExecutableRequest = request@{ origin, context, onReceipt ->
		if (!isAttachedToWindow || currentViewerComposeView != null || currentViewerKey != null)
			return@request ReaderSemanticExecutableResult.Rejected
		var constructed: ComposeView? = null
		val create: () -> View = {
			check(currentViewerComposeView == null && currentViewerKey == null)
			ComposeView(this.context).also { incoming ->
				constructed = incoming
				// Root close/disposal must find this exact view before setContent or addView
				// can synchronously invoke external construction/lifecycle work.
				currentViewerComposeView = incoming
				currentViewerKey = viewerKey
				incoming.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
				incoming.setContent(content)
			}
		}
		val request = viewerContainer.createInitialDestinationBootstrapRequest(candidate, reservation, create, ingress)
		try { request(origin, context, onReceipt) }
		finally {
			constructed?.takeIf { !viewerContainer.hasViewerMaterial(it) }?.let { incoming ->
				// Uncertain physical removal remains owned; never dispose a different or
				// still-held installation merely because the executable was rejected.
				if (currentViewerComposeView === incoming) {
					currentViewerComposeView = null
					currentViewerKey = null
				}
				nestedCompositionDisposalQueue.enqueue(incoming)
			}
		}
	}

	fun setViewerContent(viewerKey: ReaderViewerKey, content: @Composable () -> Unit) {
		originalViewerContentOperationWitness = null
		if (normalProductionCandidate != null) {
			if (readerClosed || presentationViewerKey != viewerKey) return
			normalViewerContent = viewerKey to content
			// Refresh the original command-created composition; never reopen replacement.
			if (currentViewerKey == viewerKey) currentViewerComposeView?.setContent(content)
			startNormalProductionSessionIfEligible()
			return
		}
		val witness = ReaderOriginalViewerContentOperationWitness()
		originalViewerContentOperationWitness = witness
		var operation = ReaderOriginalViewerContentOperation.CaptureReplacement
		try {
			// Capture both public and exact inner identities before construction/setContent can call out.
			val previousViewer = currentViewerComposeView
			val previousKey = currentViewerKey
			val capture = viewerContainer.captureViewerReplacement(
				replacementRequested = previousKey != viewerKey || previousViewer == null
			) ?: return
			capture.originalOperationWitness = witness
			operation = ReaderOriginalViewerContentOperation.BeginReturn
			val sourceReturn = viewerContainer.beginViewerContentReturn()
			var viewerView: ComposeView? = null
			var adopted = false
			var innerEntered = false
			var failure: Throwable? = null
			try {
				if (previousKey == viewerKey && previousViewer != null) {
					operation = ReaderOriginalViewerContentOperation.SetContent
					previousViewer.setContent(content)
					return
				}
				operation = ReaderOriginalViewerContentOperation.ComposeConstruction
				viewerView = ComposeView(context).apply {
					operation = ReaderOriginalViewerContentOperation.CompositionStrategy
					setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
					operation = ReaderOriginalViewerContentOperation.SetContent
					setContent(content)
				}
				innerEntered = true
				// The original native call records its own first operation on this same cell.
				adopted = viewerContainer.replaceCapturedViewerContent(capture, viewerView)
				operation = ReaderOriginalViewerContentOperation.AcceptReplacement
				if (!adopted || !viewerContainer.acceptedViewerReplacement(capture, viewerView)) return
				operation = ReaderOriginalViewerContentOperation.CompleteReplacement
				viewerContainer.completePresentationViewerReplacement()
				operation = ReaderOriginalViewerContentOperation.AcceptReplacement
				if (!viewerContainer.acceptedViewerReplacement(capture, viewerView) ||
					currentViewerComposeView !== previousViewer || currentViewerKey != previousKey) return
				currentViewerComposeView = viewerView
				currentViewerKey = viewerKey
				operation = ReaderOriginalViewerContentOperation.ReplacementCleanup
				nestedCompositionDisposalQueue.enqueue(previousViewer)
			} catch (original: Throwable) {
				witness.record(operation)
				failure = original
			} finally {
				try {
					operation = ReaderOriginalViewerContentOperation.ReplacementCleanup
					if (!adopted && previousKey != viewerKey) {
						operation = ReaderOriginalViewerContentOperation.RetireCaptured
						val cleanup = if (innerEntered) null else viewerContainer.retireCapturedViewerReplacement(capture)
						if (cleanup != null) witness.record(operation)
						operation = ReaderOriginalViewerContentOperation.ReplacementCleanup
						if (failure == null) failure = cleanup else if (cleanup != null && cleanup !== failure) failure!!.addSuppressed(cleanup)
						if (previousViewer != null && !viewerContainer.hasViewerMaterial(previousViewer)) {
							if (currentViewerComposeView === previousViewer && currentViewerKey == previousKey) {
								currentViewerComposeView = null; currentViewerKey = null
							}
							nestedCompositionDisposalQueue.enqueue(previousViewer)
						}
					}
					viewerView?.takeIf { currentViewerComposeView !== it && !viewerContainer.hasViewerMaterial(it) }
						?.let(nestedCompositionDisposalQueue::enqueue)
				} catch (cleanup: Throwable) {
					witness.record(operation)
					if (failure == null) failure = cleanup else if (cleanup !== failure) failure!!.addSuppressed(cleanup)
				} finally {
					operation = ReaderOriginalViewerContentOperation.EndReturn
					viewerContainer.endViewerContentReturn(sourceReturn)
				}
				failure?.let { throw it }
			}
		} catch (original: Throwable) {
			witness.record(operation)
			throw original
		} finally {
			// A reentrant call cannot leave its different invocation's cell on this Root.
			originalViewerContentOperationWitness = witness
		}
	}
	fun canAcceptNewPointer(): Boolean = viewerContainer.canAcceptNewPointer()

	fun closeReader() {
		if (readerClosed) return
		readerClosed = true
		normalViewerContent = null
		normalProductionCandidate?.let { it.gateway.retireNormalProductionCandidate(it) }
		normalActivationCoordinator?.closeToReleaseOnly()
		normalSemanticExecutor?.clear()
		initialSemanticIngress = null
		originalPhysicalInventoryCapture.retire()
		viewerContainer.closeCurrentDestinationQueries()
		presentationHostBridge.dispose()
		viewerContainer.closeReader()
		detachNestedCompositionViews()
		scheduleNestedCompositionDisposal()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		presentationDecision?.let(presentationHostBridge::update)
		startNormalProductionSessionIfEligible()
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		startNormalProductionSessionIfEligible()
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
		if (
			legacyLiveCompatibilityContext is ReaderLegacyLiveCompatibilityContext.ColdSession &&
			presentationInputPolicy == ReaderPresentationInputPolicy.RecoveryOnly
		) return ReaderPagePhysicalDispatchMode.Denied
		return when (presentationInputPolicy) {
			ReaderPresentationInputPolicy.RecoveryOnly -> ReaderPagePhysicalDispatchMode.Denied
			ReaderPresentationInputPolicy.ChromeOnly ->
				ReaderPagePhysicalDispatchMode.ChromeOnly
			ReaderPresentationInputPolicy.ShellCover ->
				ReaderPagePhysicalDispatchMode.ShellCover
			is ReaderPresentationInputPolicy.ClaimedCurl ->
				ReaderPagePhysicalDispatchMode.PlayLikeCurl
			is ReaderPresentationInputPolicy.NativePage ->
				ReaderPagePhysicalDispatchMode.Denied
			ReaderPresentationInputPolicy.LiveEngine ->
				ReaderPagePhysicalDispatchMode.LiveEngine
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
			mode == ReaderPagePhysicalDispatchMode.LegacyLive ||
				mode == ReaderPagePhysicalDispatchMode.LiveEngine
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

private data class DeferredVisibilityRestorePreparation(
	val request: ReaderNativePagePresentationRequest,
	val preparationFloor: Long
)

// Android-only physical handoff; it carries the exact Native-owned registration,
// not a runtime-generation or activation authority.
internal interface ReaderForegroundWebViewSource {
	val legacyConsequencesOpen: Boolean
	fun physicalSourceReturn(action: () -> Unit)
	fun runtimeInitializationCompleted() = Unit
}

internal interface ReaderForegroundWebViewAttachmentHost {
	val foregroundLegacyConsequencesOpen: Boolean
	fun withForegroundWebViewAttachment(webView: WebView, attach: (ReaderForegroundWebViewSource?) -> Unit)
}

private class KomikkuReaderNativeViewerContainer(
	context: Context,
	private val activatedSessionStore: ReaderActivatedSessionSnapshotStore
) : FrameLayout(context), ReaderSettingsWebViewMutationHost, ReaderForegroundWebViewAttachmentHost {
	constructor(context: Context) : this(context, ReaderActivatedSessionSnapshotStore())

	private val legacyLifecycleConsequencesOpen: Boolean
		get() = activatedSessionStore.legacyConsequencesOpen
	private val viewerContentContainer = FrameLayout(context)
	private val liveEnginePresentedFrameSequenceAuthority =
		ReaderLiveEnginePresentedFrameSequenceAuthority()
	private val whispersyncCueMapView = ReaderWhispersyncCueMapNativeView(context)
	private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	private val readablePageDragSlopPx = ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()
	private val shellCoverNavigator = KomikkuReaderNavigator(KomikkuRightAndLeftNavigation())
	var navigator: KomikkuReaderNavigator = KomikkuReaderNavigator(KomikkuDisabledNavigation())
	private var onAction: (KomikkuNavigationRegion) -> ReaderPresentationEventReceipt? = { null }
	var onStartupShellPrepared: () -> Unit = {}
	private var onPageTurnBoundary: (ReaderPageTurnDirection) -> ReaderPresentationEventReceipt? = { null }
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
	private var pageTurnCanvasRequested: Boolean = false
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
	private var composeDestinationCommitIdentity: ReaderDestinationCommitIdentity? = null
	private var presentationDecision: ReaderPresentationDecision? = null
	private data class CoverRetryBindingCorrelation(
		val effect: ReaderPresentationEffect.RetryPreparation,
		val hostEpoch: Long,
		val preparationGeneration: Long,
		val profileGeneration: Long
	)
	private var coverRetryBindingCorrelation: CoverRetryBindingCorrelation? = null
	private var onPresentationEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt? = { null }
	private var onAuthoritativePresentationHostEffect:
		(ReaderPresentationHostEffect) -> Unit = {}
	private var nativePresentationDispatchDepth = 0
	private val nativePagePresentationPublisher by lazy {
		ReaderNativePagePresentationPublisher(
			frameSource = object : ReaderNativePagePresentedFrameSource {
				override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long =
					playLikeCurlController.presentedFrameSource.requestNextPresentedFrame(onPresented)
				override fun requestCandidatePresentedFrame(candidate: ReaderNativePagePresentationCandidate,
					onPresented: (Long) -> Unit): Long =
					playLikeCurlController.presentedFrameSource.requestCandidatePresentedFrame(candidate, onPresented)
				override fun requestCurlSettlementTerminalPresentedFrame(
					candidate: ReaderCurlSettlementTerminalPresentationCandidate,
					onPresented: (Long) -> Unit): Long =
					playLikeCurlController.requestInstalledCurlTerminalPresentedFrame(candidate, onPresented)
				override fun cancelPresentedFrameRequest(requestId: Long): Boolean =
					playLikeCurlController.presentedFrameSource.cancelPresentedFrameRequest(requestId)
			},
			tokenAllocator = frameTokenAllocator,
			currentCandidate = ::currentNativePagePresentationCandidateOrNull,
			// The presentation bridge owns the whole foreground-time transaction
			// deadline, including preparation before this publisher has a candidate.
			currentHandoffTransition = {
				presentationDecision?.authoritativeLiveEngineToNativeTransitionOrNull()
			}
		) { event ->
			check(
				event is ReaderPresentationEvent.NativePagePresented ||
					event is ReaderPresentationEvent.LiveEngineHandoffTimedOut ||
					event is ReaderPresentationEvent.LiveEngineExposureFailed
			)
			val nativeContinuation = (event as? ReaderPresentationEvent.NativePagePresented)?.let(
				playLikeCurlController::initialLiveNativeReceiptContinuation
			)
			val epoch = presentationBindingReporter.captureEpoch()
			nativePresentationDispatchDepth += 1
			val receipt = try {
				dispatchPresentationEvent(event)
			} finally {
				nativePresentationDispatchDepth -= 1
			}
			// Common mutation is not acceptance: the original host dispatcher owns this floor.
			if (receipt != null && receipt.authorizes(event) &&
				epoch == presentationBindingReporter.captureEpoch() &&
				presentationBindingReporter.matchesAuthoritativePresentationVersion(receipt.version)
			) {
				nativeContinuation?.invoke(epoch, receipt)
			}
			receipt
		}
	}
	private val presentationBindingReporter = ReaderPresentationBindingReporter()
	private val presentationLifecycleDelivery = ReaderPresentationLifecycleDelivery(
		activatedSessionStore = activatedSessionStore
	)
	override val foregroundLegacyConsequencesOpen: Boolean
		get() = legacyLifecycleConsequencesOpen

	private var foregroundWebViewRegistration: ForegroundWebViewRegistration? = null

	private inner class ForegroundWebViewRegistration(
		val webView: WebView,
		val mount: View
	) : ReaderForegroundWebViewSource {
		var physicallyDetached = false
		var removalFailure: Throwable? = null
		override val legacyConsequencesOpen: Boolean
			get() = legacyLifecycleConsequencesOpen

		private val listener = object : View.OnAttachStateChangeListener {
			override fun onViewAttachedToWindow(view: View) {
				if (view !== webView) return
				physicallyDetached = false
				registration.callback { }
			}
			override fun onViewDetachedFromWindow(view: View) {
				if (view !== webView) return
				physicallyDetached = true
				// The captured registration remains valid after its parent is removed.
				// Neither descendant rediscovery nor a mutable latest-root lookup is used.
				registration.callback(installedSafetyObservation = {
					reportPresentationLifecycleEvent(ReaderPresentationLifecycleEvent.WebViewDetached)
				}) { }
			}
		}
		val registration: ReaderPresentationLifecycleDelivery.RegisteredRoot = presentationLifecycleDelivery.registeredRoot(
			registerPhysical = { webView.addOnAttachStateChangeListener(listener) },
			unregisterPhysical = {
				try { webView.removeOnAttachStateChangeListener(listener) }
				catch (failure: Throwable) { removalFailure = failure; throw failure }
			},
			isValid = { foregroundWebViewRegistration === this &&
				finalHostLifecycleEvent == null && !task4ResourceTeardownStarted }
		)

		override fun runtimeInitializationCompleted() {
			physicalSourceReturn {
				val lease = currentFrameInstallation
				if (foregroundWebViewRegistration === this && lease?.wrapper === mount && lease.owned &&
					lease.commandCurrency != null && finalHostLifecycleEvent == null && !task4ResourceTeardownStarted) {
					initialDestinationConstruction?.runtimeInitialized(webView)
				}
			}
		}
		override fun physicalSourceReturn(action: () -> Unit) {
			val owner = presentationLifecycleDelivery.beginRegisteredRootReturn()
			try { action() }
			finally { if (owner != null) presentationLifecycleDelivery.endPhysicalDelivery(owner) }
		}
	}

	override fun withForegroundWebViewAttachment(
		webView: WebView,
		attach: (ReaderForegroundWebViewSource?) -> Unit
	) {
		val sourceReturn = presentationLifecycleDelivery.beginRegisteredRootReturn()
		var exact: ForegroundWebViewRegistration? = null
		try {
			// Resolve only the genuine ancestor chain of this actual engine, before
			// framework attachment can synchronously invoke external work.
			var mount: View = webView
			while (mount.parent !== viewerContentContainer) {
				mount = mount.parent as? View ?: return
			}
			if (finalHostLifecycleEvent != null || task4ResourceTeardownStarted) return
			val predecessor = foregroundWebViewRegistration
			if (predecessor != null && (predecessor.webView !== webView || predecessor.mount !== mount)) {
				if (!predecessor.physicallyDetached || !predecessor.registration.remove(permanent = true)) return
				foregroundWebViewRegistration = null
			}
			exact = foregroundWebViewRegistration ?: ForegroundWebViewRegistration(webView, mount).also {
				foregroundWebViewRegistration = it
			}
			val destructionSource = webView as? ReaderEnginePhysicalDestructionSource
			if (destructionSource?.bindPhysicalDestruction(originalEngineDestructionConfirmation) == false ||
				(destructionSource == null && currentFrameInstallation?.commandCurrency != null)) {
				// Original material/listener custody remains retained. Reject the command's
				// continuation, not the physical installation or somebody else's consumer.
				initialDestinationConstruction?.fail(ReaderSemanticAsyncFailure.Rejected)
				return
			}
			if (presentationLifecycleDelivery.registeredRootAdmissionOpen) exact.registration.register()
			else exact.registration.registerInstalledSafetyIngress()
		} finally {
			try { attach(exact) }
			finally { if (sourceReturn != null) presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn) }
		}
	}

	private var deferredVisibilityRestorePreparation: DeferredVisibilityRestorePreparation? = null
	private var visibilityRestorePreparationRetryInProgress = false
	private val presentationReceiptDispatcher = ReaderPresentationReceiptDispatcher(
		bindingReporter = presentationBindingReporter,
		lifecycleDelivery = presentationLifecycleDelivery,
		applyHostEffect = { effect ->
			onAuthoritativePresentationHostEffect(effect)
		}
	)
	private var rendererLossEpoch = 0L
	private var pendingRendererLossCancellationIdentity:
		ReaderRendererLossCancellationIdentity? = null
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
	// Latest Compose observations only. Installed overlay consumers must authenticate
	// the canonical receipt against the original captured/displayed deck before use.
	private var whispersyncAnchorReceipt: ReaderWhispersyncAnchorReceipt? = null
	private var whispersyncHighlightColorArgb = 0
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
	// One stable original consumer, including repeat attachment of the same Engine.
	// The Engine publishes only after super.destroy returns; detach and logical
	// cancellation are not destruction and must never retire its descriptor callbacks.
	private val originalEngineDestructionConfirmation: (WebView) -> Unit = { exactView ->
		pageTurnBundleSource.onOriginalEngineDestroyed(exactView)
	}
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

			override fun cancelRendererWork(reason: ReaderPageLifecycleCancellationReason) {
				clearDestinationDeckPrewarm()
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
		presentedFrameSequenceSource = ::nextLiveEnginePresentedFrameSequence,
		presentationHostEpoch = presentationBindingReporter::captureEpoch,
		deckAdmissionHost = presentationBindingReporter.selectedForProductionDeckAdmission(),
		initialLiveNativeProofIsCurrent = ::initialLiveNativeProofIsCurrent,
		initialLiveDecisionIsAuthoritative = presentationBindingReporter::matchesAuthoritativePresentationDecision,
		readInstalledOwnerAndInput = { readInstalledPublication?.invoke().takeIf {
			installedCommandCurrency?.invoke() == true && finalHostLifecycleEvent == null && !task4ResourceTeardownStarted
		} },
		bundleSource = pageTurnBundleSource,
		diagnostics = readerRuntimeDiagnostics,
		qaFaultRegistry = qaFaultRegistry,
		hasStaticRasterShieldOwnership = {
			pageRasterPreparationController.hasStaticRasterShieldOwnership()
		},
		onRequestPrewarm = ::requestPageTurnPrewarmWhenReady,
		onCanonicalLiveCommitIssued = ::onCanonicalLiveCommitIssued,
		onCanonicalLiveCommitRecoveryFailed = ::onCanonicalLiveCommitRecoveryFailed,
			onPresentationEvent = ::dispatchPresentationEvent,
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
			if (!legacyLifecycleConsequencesOpen) {
				reportPresentationLifecycleEvent(ReaderPresentationLifecycleEvent.RendererLost)
				return@ReaderPlayLikeCurlFoliateController
			}
			rendererLossEpoch = Math.incrementExact(rendererLossEpoch)
			val cancellationIdentity = ReaderRendererLossCancellationIdentity(
				presentationEpoch = presentationBindingReporter.captureEpoch(),
				rendererLossEpoch = rendererLossEpoch,
				reason = event.cancellationReason()
			)
			pendingRendererLossCancellationIdentity = cancellationIdentity
			reportPresentationLifecycleEvent(
				ReaderPresentationLifecycleEvent.RendererLost
			)
			if (!legacyLifecycleConsequencesOpen) return@ReaderPlayLikeCurlFoliateController
			dispatchPageHostLifecycleEvent(
				event = event,
				rendererLossCancellationIdentity = cancellationIdentity
			)
			if (!presentationLifecycleDelivery.hasPendingRendererLoss) {
				pendingRendererLossCancellationIdentity = null
			}
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
		presentationLifecycleDelivery = presentationLifecycleDelivery,
		activatedSessionStore = activatedSessionStore,
		fenceCallbacks = {
			ReaderPageQaFaultControl.detach(qaFaultRegistration)
			qaFaultRegistry.closeAndDrain()
		},
		closeRendererAndAdapter = {
			closePassiveRasterPreparationAdapter()
			playLikeCurlController.destroyAndJoin()
		},
		onRequestPrewarm = ::requestPageTurnPrewarmWhenReady,
		canStartPreparation = {
			coldOwnershipAdmitted && rasterPaginationReady &&
				!playLikeCurlController.awaitingPresentationRecoverySnapshot
		},
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
				if (!legacyLifecycleConsequencesOpen) return@ReaderPageRasterHostEventController
				pageRasterPreparationController.onWebViewAttachmentChanged(attached)
				if (!legacyLifecycleConsequencesOpen) return@ReaderPageRasterHostEventController
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
		val retry = currentCoverRetryBindingCorrelationOrNull()
		// The producer publishes this synchronously after its request-generation
		// fence. Record that exact epoch before its preparation callback emits Idle.
		coverRetryBindingCorrelation = if (epoch != null) retry?.copy(profileGeneration = epoch) else null
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
		if (presentationDecision?.frameOwner == ReaderPresentationFrameOwner.Neutral) {
			shellCoverView.bringToFront()
		} else if (shellCoverView.parent === this && indexOfChild(shellCoverView) != 0) {
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

	private val frameTokenAllocator = ReaderLegacySourceLocalTokenAllocator()
	val frameSourceTokenAllocator: ReaderLegacySourceLocalTokenAllocator get() = frameTokenAllocator
	private var composedFrameSource: ReaderFrameOrHandoffPhysicalSource? = null
	fun physicalInventoryAdmissionEpoch(domain: ReaderLegacyPhysicalDomain): Long? {
		if (Looper.myLooper() != Looper.getMainLooper() ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted ||
			presentationBindingReporter.expectedReaderSessionGeneration != domain.readerSessionGeneration) return null
		activatedSessionStore.snapshot?.let { installed ->
			val gateway = (installed.portAuthority as? ReaderActivatedSessionPortAuthority.Production)?.ports?.gateway
				as? ReaderSnapshotBackedActivatedGatewayPort ?: return null
			if (!gateway.candidate.isSelected || gateway.candidate.installationSource !== activatedSessionStore ||
				!activatedSessionStore.ownsInstalledCandidate(gateway.candidate.epoch, domain.readerSessionGeneration,
					gateway.candidate.coordinatorEpoch)) return null
		}
		return presentationBindingReporter.captureEpoch()
	}

	fun freezeOriginalPhysicalInventoryOwners(domain: ReaderLegacyPhysicalDomain,
		executor: ReaderSemanticCommandExecutor): ReaderPortCommandResult {
		if (physicalInventoryAdmissionEpoch(domain) == null) return ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource)
		for (freeze in listOf(presentationLifecycleDelivery::freezeForTransitionActivation,
			playLikeCurlController::freezeDeckOwnershipForTransitionActivation,
			pageRasterPreparationController::freezeRasterPreparationOwnershipForTransitionActivation,
			pageTurnBundleSource::freezeForTransitionActivation,
			foregroundWebViewOwnership::freezeForTransitionActivation,
			executor::freezeForTransitionActivation,
			pageInputSettlementHostController::freezeForTransitionActivation)) {
			val result = freeze(domain)
			if (result != ReaderPortCommandResult.Accepted) return result
		}
		return ReaderPortCommandResult.Accepted
	}

	fun snapshotOriginalPhysicalInventoryOwners(domain: ReaderLegacyPhysicalDomain,
		executor: ReaderSemanticCommandExecutor): List<ReaderLegacyConnectedSourceInventory>? {
		if (physicalInventoryAdmissionEpoch(domain) == null) return null
		val deck = playLikeCurlController.snapshotConnectedFrozenDeckOwnership() ?: return null
		val preparation = pageRasterPreparationController.snapshotFrozenRasterPreparationOwnership() ?: return null
		val bundle = pageTurnBundleSource.snapshotFullyConnectedFrozenOwnership() ?: return null
		val foreground = foregroundWebViewOwnership.snapshotConnectedFrozenOwnership() ?: return null
		val semantic = executor.snapshotConnectedFrozenOwnership() ?: return null
		val lifecycle = presentationLifecycleDelivery.snapshotConnectedFrozenOwnership() ?: return null
		val input = pageInputSettlementHostController.snapshotConnectedFrozenOwnership() ?: return null
		return listOf(deck, preparation, foreground, semantic, lifecycle, input) + bundle
	}

	fun drainOriginalPhysicalInventoryOwner(identity: ReaderLegacyPhysicalIdentity,
		executor: ReaderSemanticCommandExecutor,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult = when (identity.source) {
		ReaderLegacyInventorySource.Deck -> playLikeCurlController.drainFrozenDeckOwnership(identity, onConfirmed)
		ReaderLegacyInventorySource.RasterPreparation -> pageRasterPreparationController.drainFrozenRasterPreparationOwnership(identity, onConfirmed)
		ReaderLegacyInventorySource.RasterSnapshotCache,
		ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
		ReaderLegacyInventorySource.RasterHydration,
		ReaderLegacyInventorySource.RasterPublication,
		ReaderLegacyInventorySource.RasterGenerationAndPersistence,
		ReaderLegacyInventorySource.RasterCaptureAndVisualState,
		ReaderLegacyInventorySource.RasterLiveValidation,
		ReaderLegacyInventorySource.RasterStoreAndCache -> pageTurnBundleSource.drainFrozenOwnership(identity, onConfirmed)
		ReaderLegacyInventorySource.ForegroundWebViewOwnership -> foregroundWebViewOwnership.drainFrozenOwnership(identity, onConfirmed)
		ReaderLegacyInventorySource.SemanticCommandSlot -> executor.drainFrozenOwnership(identity, onConfirmed)
		ReaderLegacyInventorySource.LifecycleDelivery -> presentationLifecycleDelivery.drainFrozenOwnership(identity, onConfirmed)
		ReaderLegacyInventorySource.Input -> pageInputSettlementHostController.drainFrozenOwnership(identity, onConfirmed)
		ReaderLegacyInventorySource.FrameOrHandoff,
		ReaderLegacyInventorySource.DeadlineRegistration -> ReaderPortCommandResult.Rejected(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource)
	}

	fun finalizeOriginalPhysicalInventoryOwners(domain: ReaderLegacyPhysicalDomain, executor: ReaderSemanticCommandExecutor,
		selected: ReaderImportedLegacyResourceRegistration?, confirmed: Set<ReaderLegacyPhysicalIdentity>): ReaderPortCommandResult {
		if (physicalInventoryAdmissionEpoch(domain) == null || snapshotOriginalPhysicalInventoryOwners(domain, executor) == null)
			return ReaderPortCommandResult.Rejected(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource)
		for (finalize in listOf<() -> ReaderPortCommandResult>(
			{ pageInputSettlementHostController.finalizeActivatedHandoff(domain) },
			{ playLikeCurlController.finalizeDeckOwnershipAfterTransitionActivation(domain.freezeToken, selected, confirmed) },
			{ pageRasterPreparationController.finalizeRasterPreparationOwnershipAfterTransitionActivation(domain) },
			{ pageTurnBundleSource.finalizeActivatedHandoff(domain) },
			{ foregroundWebViewOwnership.finalizeActivatedHandoff(domain) },
			{ executor.finalizeActivatedHandoff(domain) },
			{ presentationLifecycleDelivery.finalizeActivatedHandoff(domain) }
		)) {
			val result = finalize()
			if (result != ReaderPortCommandResult.Accepted) return result
		}
		return ReaderPortCommandResult.Accepted
	}

	fun originalNeutralCheckpointLease(): paige.navic.reader.ReaderInitialPresentationInputLease? {
		val decision = presentationDecision ?: return null
		if (presentationBindingReporter.lastReportedBinding != null || decision.targetBinding != null ||
			decision.frameOwner != ReaderPresentationFrameOwner.Neutral) return null
		return pageInputSettlementHostController.originalIdentityFreeInitialLease()
	}

	fun originalPhysicalAdmissionsRestored(executor: ReaderSemanticCommandExecutor): Boolean =
		playLikeCurlController.originalLegacyDeckAdmissionRestored &&
			pageRasterPreparationController.originalLegacyPreparationAdmissionRestored &&
			pageTurnBundleSource.originalLegacyAdmissionsRestored && foregroundWebViewOwnership.originalLegacyAdmissionRestored &&
			executor.originalLegacyAdmissionRestored && presentationLifecycleDelivery.originalLegacyAdmissionRestored &&
			pageInputSettlementHostController.originalLegacyAdmissionRestored

	fun originalRestorationGroups(domain: ReaderLegacyPhysicalDomain,
		executor: ReaderSemanticCommandExecutor): List<ReaderOriginalRestorationGroup> {
		fun captureLifetime(): () -> Boolean {
			val epoch = physicalInventoryAdmissionEpoch(domain)
			return { epoch != null && physicalInventoryAdmissionEpoch(domain) == epoch }
		}
		return listOf(
			ReaderOriginalRestorationGroup(setOf(ReaderLegacyInventorySource.Deck),
				{ playLikeCurlController.restoreDeckOwnershipAfterTransitionActivation(domain) },
				{ playLikeCurlController.originalLegacyDeckAdmissionRestored }, ::captureLifetime),
			ReaderOriginalRestorationGroup(setOf(ReaderLegacyInventorySource.RasterPreparation),
				{ pageRasterPreparationController.restoreRasterPreparationOwnershipAfterTransitionActivation(domain) },
				{ pageRasterPreparationController.originalLegacyPreparationAdmissionRestored }, ::captureLifetime),
			ReaderOriginalRestorationGroup(ReaderPageTurnBundleInventorySources.toSet(),
				{ pageTurnBundleSource.restoreAfterTransitionActivation(domain) },
				{ pageTurnBundleSource.originalLegacyAdmissionsRestored }, {
					val lifetime = captureLifetime()
					val backends = pageTurnBundleSource.captureOriginalRestorationCurrency()
					val currency: () -> Boolean = { lifetime() && backends() }
					currency
				}),
			ReaderOriginalRestorationGroup(setOf(ReaderLegacyInventorySource.ForegroundWebViewOwnership),
				{ foregroundWebViewOwnership.restoreAfterTransitionActivation(domain) },
				{ foregroundWebViewOwnership.originalLegacyAdmissionRestored }, ::captureLifetime),
			ReaderOriginalRestorationGroup(setOf(ReaderLegacyInventorySource.SemanticCommandSlot),
				{ executor.restoreAfterTransitionActivation(domain) }, { executor.originalLegacyAdmissionRestored }, ::captureLifetime),
			ReaderOriginalRestorationGroup(setOf(ReaderLegacyInventorySource.LifecycleDelivery),
				{ presentationLifecycleDelivery.restoreAfterTransitionActivation(domain) },
				{ presentationLifecycleDelivery.originalLegacyAdmissionRestored }, ::captureLifetime),
			ReaderOriginalRestorationGroup(setOf(ReaderLegacyInventorySource.Input),
				{ pageInputSettlementHostController.restoreAfterTransitionActivation(domain) },
				{ pageInputSettlementHostController.originalLegacyAdmissionRestored }, ::captureLifetime)
		)
	}

	fun createFrameOrHandoffSource(bridge: ReaderPresentationHostBridge): ReaderFrameOrHandoffPhysicalSource {
		check(bridge.usesFrameTokenAllocator(frameTokenAllocator))
		val existing = composedFrameSource
		if (existing != null) { check(existing.usesBridge(bridge)); return existing }
		return ReaderFrameOrHandoffPhysicalSource(framePhysicalOwnership, nativePagePresentationPublisher, bridge)
			.also { composedFrameSource = it }
	}
	private val framePhysicalOwnership = ReaderFramePhysicalOwnershipAdapter(
		tokenAllocator = frameTokenAllocator,
		physicalReturn = { action ->
			val invocation = presentationLifecycleDelivery.beginRegisteredRootReturn()
			try { action() }
			finally { if (invocation != null) presentationLifecycleDelivery.endPhysicalDelivery(invocation) }
		},
		returnsInFlight = { presentationLifecycleDelivery.hasPhysicalReturnsInFlight }
	)
	private var currentFrameInstallation: ReaderFramePhysicalOwnershipAdapter.Lease? = null
	private var initialDestinationConstruction: ReaderEngineInitialDestinationConstructionCustody? = null
	// Temporary original witness only. No geometry, callback or admission authority is retained.
	private var initialGeometryWitnessBoundary = ReaderInitialGeometryWitnessBoundary.OutsideFixture
	fun captureLiveFrameInstallation(): ReaderFramePhysicalOwnershipAdapter.LiveInstallation? {
		val lease = currentFrameInstallation ?: return null
		val engine = currentLiveWebView() ?: return null
		val installed = activatedSessionStore.snapshot
		val serial = viewerReplacementSerial
		return framePhysicalOwnership.captureLiveInstallation(lease, engine,
			currency = { activatedSessionStore.snapshot === installed && serial == viewerReplacementSerial &&
				legacyLifecycleConsequencesOpen && finalHostLifecycleEvent == null && !task4ResourceTeardownStarted &&
				currentFrameInstallation === lease && currentLiveWebView() === engine },
			displayed = { viewerContentContainer.isShown && lease.wrapper.isShown && engine.isShown &&
				viewerContentContainer.alpha > 0f && lease.wrapper.alpha > 0f && engine.alpha > 0f })
	}
	fun confirmLiveFramePresentation(installation: ReaderFramePhysicalOwnershipAdapter.LiveInstallation,
		witness: ReaderLiveEnginePhysicalWitness, owner: paige.navic.reader.ReaderPresentationFrameOwner.LiveEngine): Boolean =
		framePhysicalOwnership.acceptLivePresentation(installation, witness, owner)
	private var viewerReplacementSerial = 0L
	internal class ViewerReplacementCapture internal constructor(
		internal val serial: Long,
		internal val deliveryEpoch: Long,
		internal val materialEpoch: Long,
		internal val installed: ReaderActivatedSessionSnapshot?,
		internal val material: ReaderFramePhysicalOwnershipAdapter.Lease?,
		internal val listener: Any?
	) {
		// Borrow Root's invocation-local diagnostic cell; no admission or custody reads it.
		internal var originalOperationWitness: ReaderOriginalViewerContentOperationWitness? = null
		override fun toString(): String = "ReaderViewerReplacementCapture(<redacted>)"
	}
	fun captureViewerReplacement(): ViewerReplacementCapture? =
		captureViewerReplacement(replacementRequested = true)

	fun captureViewerReplacement(replacementRequested: Boolean): ViewerReplacementCapture? {
		if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmissionOpen || !framePhysicalOwnership.admissionOpen) return null
		// Refreshing the same ComposeView is not a new physical installation.
		if (replacementRequested) viewerReplacementSerial = Math.incrementExact(viewerReplacementSerial)
		return ViewerReplacementCapture(viewerReplacementSerial, presentationLifecycleDelivery.capturePhysicalAdmission(),
			framePhysicalOwnership.captureEpoch(), activatedSessionStore.snapshot, currentFrameInstallation, foregroundWebViewRegistration)
	}
	private fun admitsReplacement(capture: ViewerReplacementCapture): Boolean =
		capture.serial == viewerReplacementSerial && activatedSessionStore.snapshot === capture.installed &&
			legacyLifecycleConsequencesOpen && presentationLifecycleDelivery.registeredRootAdmits(capture.deliveryEpoch) &&
			framePhysicalOwnership.admits(capture.materialEpoch)
	fun acceptedViewerReplacement(capture: ViewerReplacementCapture, incoming: View): Boolean =
		admitsReplacement(capture) && currentFrameInstallation?.let { it.wrapper === incoming && it.owned && it.state == ReaderLegacyResourceState.Registered } == true
	fun beginViewerContentReturn(): ReaderExactPhysicalOwnerRegistry.Owner? = presentationLifecycleDelivery.beginRegisteredRootReturn()
	fun endViewerContentReturn(owner: ReaderExactPhysicalOwnerRegistry.Owner?) {
		if (owner != null) presentationLifecycleDelivery.endPhysicalDelivery(owner)
	}
	fun hasViewerMaterial(view: View): Boolean = framePhysicalOwnership.hasInstallation(view)
	fun retireCapturedViewerReplacement(capture: ViewerReplacementCapture): Throwable? {
		var failure: Throwable? = null
		capture.material?.let { lease ->
			val result = framePhysicalOwnership.retire(lease)
			failure = result.failure
			if (result.completed && currentFrameInstallation === lease) currentFrameInstallation = null
		}
		val exact = capture.listener as? ForegroundWebViewRegistration
		if (exact != null) {
			exact.removalFailure = null
			val removed = exact.registration.remove(permanent = true)
			val later = exact.removalFailure
			if (failure == null) failure = later else if (later != null && later !== failure) failure!!.addSuppressed(later)
			if (removed && foregroundWebViewRegistration === exact) foregroundWebViewRegistration = null
		}
		return failure
	}
	/** Coordinator bootstrap material, with exact original-container custody and no legacy replacement. */
	fun mountNeutralSemanticViewerContent(
		viewerView: View,
		candidate: ReaderTransitionGatewayCandidate,
		origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand,
		context: paige.navic.reader.ReaderSemanticMutationStartEvidence
	): Boolean {
		if (finalHostLifecycleEvent != null || task4ResourceTeardownStarted ||
			currentFrameInstallation?.let { it.owned || it.operating } == true) return false
		lateinit var lease: ReaderFramePhysicalOwnershipAdapter.Lease
		lease = framePhysicalOwnership.reserveNeutralSemanticInstallation(
			viewerContentContainer, viewerView, candidate, activatedSessionStore, origin, context
		) { currentFrameInstallation === lease && finalHostLifecycleEvent == null && !task4ResourceTeardownStarted }
			?: return false
		// Retain before addView: reentrant detach/close must find this exact captured material.
		currentFrameInstallation = lease
		return try {
			framePhysicalOwnership.mount(lease)
		} finally {
			if (!lease.owned && currentFrameInstallation === lease) currentFrameInstallation = null
		}
	}

	fun replaceViewerContent(viewerView: View): Boolean {
		val capture = captureViewerReplacement() ?: return false
		return replaceCapturedViewerContent(capture, viewerView)
	}
	fun replaceCapturedViewerContent(capture: ViewerReplacementCapture, viewerView: View): Boolean {
		val witness = capture.originalOperationWitness
		var operation = ReaderOriginalViewerContentOperation.BeginReturn
		try {
			val sourceReturn = presentationLifecycleDelivery.beginRegisteredRootReturn()
			var failure: Throwable? = null
			var prefixComplete = false
			try {
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture)) return false
				operation = ReaderOriginalViewerContentOperation.CancelPointer
				cancelLegacyLivePointerStream()
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture)) return false
				operation = ReaderOriginalViewerContentOperation.ReplacementLifecycle
				startupShellHandoff.resetForNewViewer()
				dispatchPageHostLifecycleEvent(ReaderPageHostLifecycleEvent.RendererReplaced)
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture)) return false
				operation = ReaderOriginalViewerContentOperation.CurlInvalidation
				playLikeCurlController.invalidate("viewer-replaced", legacyConsequencesAdmitted = { admitsReplacement(capture) })
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture)) return false
				operation = ReaderOriginalViewerContentOperation.RasterInvalidation
				pageRasterPreparationController.invalidate(reason = "viewer-replaced", clearVisualPageIndex = true)
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture)) return false
				operation = ReaderOriginalViewerContentOperation.RemovePrewarmListener
				removePageTurnPrewarmLayoutListener()
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture)) return false
				operation = ReaderOriginalViewerContentOperation.ReportDetached
				pageRasterHostEventController.webViewAttachmentChanged(false)
				prefixComplete = true
			} catch (original: Throwable) {
				witness?.record(operation)
				failure = original
			} finally {
				try {
					operation = ReaderOriginalViewerContentOperation.RetireCaptured
					val cleanup = retireCapturedViewerReplacement(capture)
					if (cleanup != null) witness?.record(operation)
					operation = ReaderOriginalViewerContentOperation.ReplacementCleanup
					if (failure == null) failure = cleanup else if (cleanup != null && cleanup !== failure) failure!!.addSuppressed(cleanup)
				} catch (cleanup: Throwable) {
					witness?.record(operation)
					if (failure == null) failure = cleanup else if (cleanup !== failure) failure!!.addSuppressed(cleanup)
				}
				if (!prefixComplete || failure != null) {
					if (sourceReturn != null) {
						operation = ReaderOriginalViewerContentOperation.EndReturn
						presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn)
					}
				}
				// A return in the prefix still owes exact mandatory cleanup and its original failure.
				if (!prefixComplete && failure != null) throw failure!!
			}
			try {
				failure?.let { throw it }
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture)) return false
				lateinit var lease: ReaderFramePhysicalOwnershipAdapter.Lease
				operation = ReaderOriginalViewerContentOperation.ReserveInstallation
				lease = framePhysicalOwnership.reserve(viewerContentContainer, viewerView) { currentFrameInstallation === lease } ?: return false
				currentFrameInstallation = lease
				operation = ReaderOriginalViewerContentOperation.MountInstallation
				if (!framePhysicalOwnership.mount(lease)) return false
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture) || currentFrameInstallation !== lease) return false
				operation = ReaderOriginalViewerContentOperation.ReportAttached
				pageRasterHostEventController.webViewAttachmentChanged(viewerContentContainer.findDescendantWebView()?.isAttachedToWindow == true)
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				if (!admitsReplacement(capture) || currentFrameInstallation !== lease) return false
				operation = ReaderOriginalViewerContentOperation.RequestPrewarm
				requestPageTurnPrewarmWhenReady()
				operation = ReaderOriginalViewerContentOperation.ReplacementAdmission
				return admitsReplacement(capture) && currentFrameInstallation === lease
			} catch (original: Throwable) {
				// Record before native finally can throw; cleanup must not relabel this invocation.
				witness?.record(operation)
				throw original
			} finally {
				if (prefixComplete && failure == null && sourceReturn != null) {
					operation = ReaderOriginalViewerContentOperation.EndReturn
					presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn)
				}
			}
		} catch (original: Throwable) {
			witness?.record(operation)
			throw original
		}
	}

	fun detachViewerContent(viewerView: View?) {
		val sourceReturn = presentationLifecycleDelivery.beginRegisteredRootReturn()
		val exact = currentFrameInstallation?.takeIf { it.wrapper === viewerView }
		val listener = foregroundWebViewRegistration?.takeIf { it.mount === viewerView }
		try {
			val capture = ViewerReplacementCapture(viewerReplacementSerial, presentationLifecycleDelivery.capturePhysicalAdmission(),
				framePhysicalOwnership.captureEpoch(), activatedSessionStore.snapshot, exact, listener)
			retireCapturedViewerReplacement(capture)?.let { throw it }
		} finally {
			if (sourceReturn != null) presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn)
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

	fun requiresLiveEngineExposureHandoff(): Boolean = !pageTurnCanvasRequested

	fun setPageTurnCanvasEnabled(enabled: Boolean) {
		setPageTurnCanvasEnabled(enabled) {}
	}

	fun setPageTurnCanvasEnabled(
		enabled: Boolean,
		onModeIntentChanged: () -> Unit
	) {
		val supported = enabled && pageTurnBundleSource.isAvailable
		if (pageTurnCanvasRequested == supported) {
			publishPagePreparationFacts()
			return
		}
		pageTurnCanvasRequested = supported
		onModeIntentChanged()
		if (supported) nativePagePresentationPublisher.update()
		if (supported || !pageTurnCanvasEnabled) {
			applyActivePageTurnCanvasMode(supported)
		} else {
			publishPagePreparationFacts()
		}
	}

	private fun applyActivePageTurnCanvasMode(enabled: Boolean) {
		if (pageTurnCanvasEnabled == enabled) {
			publishPagePreparationFacts()
			return
		}
		if (!enabled) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.CanvasDisabled
			)
		}
		pageTurnCanvasEnabled = enabled
		cancelLegacyLivePointerStreamIfContextChanged()
		playLikeCurlController.setEnabled(enabled)
		publishPagePreparationFacts()
		if (enabled) {
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
		whispersyncAnchorReceipt = receipt
		whispersyncHighlightColorArgb = highlightColorArgb
		// Observing a receipt is neither a canonical capture proof nor displayed-deck
		// authority. Installed decoration consumes the separate original publication;
		// never reopen the frozen legacy overlay/restoration writers.
		if (readInstalledPublication != null) {
			refreshInstalledWhispersyncConsumer()
			return
		}
		if (!legacyLifecycleConsequencesOpen) return
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

	/** Authenticate before constructor effects, then retain one transport through actual
	 * Engine configure completion and query custody. No host drain or synthetic Ready. */
	fun createInitialDestinationBootstrapRequest(
		candidate: ReaderTransitionGatewayCandidate,
		reservation: ReaderReservedNeutralBootstrapRequest,
		create: () -> View,
		ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
	): ReaderSemanticExecutableRequest = request@{ origin, context, onReceipt ->
		if (Looper.myLooper() != Looper.getMainLooper() || !isAttachedToWindow || windowVisibility != View.VISIBLE ||
			lastPresentationWindowVisible != true || finalHostLifecycleEvent != null || task4ResourceTeardownStarted ||
			width <= 0 || height <= 0 || currentFrameInstallation?.let { it.owned || it.operating } == true ||
			initialDestinationConstruction?.active == true) return@request ReaderSemanticExecutableResult.Rejected
		val installed = activatedSessionStore.snapshot?.takeIf { it.commandEgressOpen }
			?: return@request ReaderSemanticExecutableResult.Rejected
		val neutral = installed.initialDecision?.origin as? paige.navic.reader.ReaderInitialCommittedPresentationOrigin.Neutral
			?: return@request ReaderSemanticExecutableResult.Rejected
		val id = origin.transitionId
		val version = presentationBindingReporter.captureInitialSemanticContextVersionOrNull()
			?: return@request ReaderSemanticExecutableResult.Rejected
		fun current(): Boolean = activatedSessionStore.snapshot === installed && installed.commandEgressOpen &&
			installed.reservedNeutralBootstrap === reservation && candidate.installationSource === activatedSessionStore &&
			candidate.isSelected && activatedSessionStore.ownsInstalledCandidate(candidate.epoch, id.readerSessionGeneration, id.coordinatorEpoch) &&
			reservation.readerSessionGeneration == neutral.readerSessionGeneration && id.readerSessionGeneration == neutral.readerSessionGeneration &&
			id.coordinatorEpoch == neutral.coordinatorEpoch && id.operation == paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage &&
			id.expectedBinding is paige.navic.reader.ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial &&
			version.readerSessionGeneration == id.readerSessionGeneration &&
			presentationBindingReporter.captureInitialSemanticContextVersionOrNull() == version &&
			finalHostLifecycleEvent == null && !task4ResourceTeardownStarted && context.isExecutionCurrent(origin, reservation.handle)
		if (!current()) return@request ReaderSemanticExecutableResult.Rejected
		lateinit var custody: ReaderEngineInitialDestinationConstructionCustody
		custody = ReaderEngineInitialDestinationConstructionCustody(origin, context) initialized@{ engine ->
			if (!custody.active || !current()) { custody.fail(ReaderSemanticAsyncFailure.Rejected); return@initialized }
			val captured = captureInitialDestinationSemanticContext(candidate, reservation, origin, context)
			if (captured == null || captured.engine.get() !== engine) {
				custody.fail(ReaderSemanticAsyncFailure.Rejected); return@initialized
			}
			custody.initialGeometryScopeCaptured = true // Diagnostic ordering only; never rearm or recapture.
			val source = engine as? ReaderEngineCurrentDestinationQuerySource
				?: run { custody.fail(ReaderSemanticAsyncFailure.Rejected); return@initialized }
			val request = source.createInitialDestinationConstructionSemanticRequest(reservation, captured.version,
				captured.publicationGeneration, captured.viewportGeneration, captured.profileGeneration,
				{
					if (custody.initialAdmissionEvaluationActive)
						custody.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ConstructionInactive
					custody.active && initialDestinationSemanticContextIsCurrent(captured, candidate, reservation, origin, context, custody)
				},
				custody, ingress)
			val receiveInitialReceipt: (ReaderPresentationEventReceipt) -> Unit = receive@{ receipt ->
				val capture = custody.takeResolvedCapture(receipt)
				val layout = capture?.first
				val captureBasis = capture?.second
				if (!initialDestinationSemanticContextIsCurrent(captured, candidate, reservation, origin, context) ||
					!presentationBindingReporter.consumeInitialSemanticReceipt(captured.reporterEpoch, captured.version, origin, receipt)) {
					custody.fail(ReaderSemanticAsyncFailure.Rejected)
					return@receive
				}
				val binding = receipt.semanticReceipt?.binding
				val sourceCurrency = source.captureResolvedLayoutCurrency()
				if (layout != null && captureBasis != null && binding != null && sourceCurrency != null) captured.installation.initialResolvedLayout =
					ReaderNativeInitialResolvedLayout(captured, id, binding, layout, receipt.version, sourceCurrency, captureBasis)
				// Original executor owns FIFO forwarding and retirement. Retaining numeric
				// metadata is neither material allocation nor physical presentation.
				onReceipt(receipt)
			}
			if (request(origin, context, receiveInitialReceipt) != ReaderSemanticExecutableResult.Accepted)
				custody.fail(ReaderSemanticAsyncFailure.Rejected)
		}
		when (custody.bindExecutor()) {
			paige.navic.reader.ReaderSemanticTransportBindingResult.Bound -> Unit
			paige.navic.reader.ReaderSemanticTransportBindingResult.Rejected -> {
				custody.retire(); return@request ReaderSemanticExecutableResult.Rejected
			}
			paige.navic.reader.ReaderSemanticTransportBindingResult.RetiredAndDisposed -> return@request ReaderSemanticExecutableResult.Rejected
		}
		if (!custody.active || !current()) { custody.retire(); return@request ReaderSemanticExecutableResult.Rejected }
		initialDestinationConstruction = custody // Retain before constructors/addView can reenter close.
		try {
			context.mutationStarted() // Compose construction may allocate/register actual physical owners.
			if (!custody.active || !current()) return@request ReaderSemanticExecutableResult.Rejected
			val wrapper = create()
			if (!custody.active || !current() || !mountNeutralSemanticViewerContent(wrapper, candidate, origin, context)) {
				custody.retire(); return@request ReaderSemanticExecutableResult.Rejected
			}
			custody.mountCompleted() // Only releases an already-observed exact entrypoint completion.
			ReaderSemanticExecutableResult.Accepted
		} catch (failure: Throwable) {
			custody.retire()
			throw failure // The original executor classifies the actual mutation boundary.
		}
	}

	private fun captureInitialDestinationSemanticContext(
		candidate: ReaderTransitionGatewayCandidate,
		reservation: ReaderReservedNeutralBootstrapRequest,
		origin: ReaderPresentationEventOrigin.SemanticCommand,
		context: paige.navic.reader.ReaderSemanticMutationStartEvidence,
		admissionWitness: ReaderEngineInitialDestinationConstructionCustody? = null
	): ReaderNativeInitialDestinationSemanticContext? {
		val witness = admissionWitness?.takeIf { it.initialAdmissionEvaluationActive }
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.NativePhysicalEligibility
		if (Looper.myLooper() != Looper.getMainLooper() || !isAttachedToWindow ||
			windowVisibility != View.VISIBLE || lastPresentationWindowVisible != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted || width <= 0 || height <= 0) return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.CandidateReservationTransitionEligibility
		val installed = activatedSessionStore.snapshot?.takeIf { it.commandEgressOpen } ?: return null
		val neutral = installed.initialDecision?.origin as? paige.navic.reader.ReaderInitialCommittedPresentationOrigin.Neutral
			?: return null
		val id = origin.transitionId
		if (installed.reservedNeutralBootstrap !== reservation || candidate.installationSource !== activatedSessionStore ||
			!candidate.isSelected || !activatedSessionStore.ownsInstalledCandidate(candidate.epoch, id.readerSessionGeneration, id.coordinatorEpoch) ||
			reservation.readerSessionGeneration != neutral.readerSessionGeneration || id.readerSessionGeneration != neutral.readerSessionGeneration ||
			id.coordinatorEpoch != neutral.coordinatorEpoch || id.operation != paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage ||
			id.expectedBinding !is paige.navic.reader.ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial) return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ExecutionAtActivation
		if (!context.isExecutionCurrent(origin, reservation.handle)) return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.InstallationStructure
		val lease = currentFrameInstallation ?: return null
		if (!lease.owned || lease.operating || lease.permanent || !lease.addInvoked ||
			lease.state != ReaderLegacyResourceState.Registered || lease.owner != null || lease.binding != null || lease.imported != null ||
			lease.container !== viewerContentContainer || lease.wrapper.parent !== viewerContentContainer ||
			!lease.wrapper.isAttachedToWindow || lease.beforeCommandAdd == null) return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.InstallationCurrentness
		if (!lease.isCurrent()) return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.InstallationCurrency
		val commandCurrent = try { lease.commandCurrency?.invoke() == true }
		finally {
			// The original currency closure records its actually evaluated nested branch.
			// This also retains that category if the predicate throws; no second invocation.
			witness?.initialAdmissionEvaluatedStage = lease.initialAdmissionCurrencyStage
		}
		if (!commandCurrent) return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.EngineAncestry
		val engine = currentLiveWebView() ?: return null
		if (engine !is ReaderEngineCurrentDestinationQuerySource || !engine.isAttachedToWindow) return null
		// The measured Engine must belong to the original command-created wrapper.
		var ancestor: View = engine
		while (ancestor !== lease.wrapper) ancestor = ancestor.parent as? View ?: return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ReporterVersion
		val version = presentationBindingReporter.captureInitialSemanticContextVersionOrNull() ?: return null
		val profile = rasterProfileEpoch ?: 0L // Existing truthful provisional identity, not resolved readiness.
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ReporterGenerationValidity
		if (version.readerSessionGeneration != id.readerSessionGeneration || presentationPublicationGeneration <= 0L ||
			presentationViewportGeneration <= 0L || profile < 0L) return null
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.ExecutionFinal
		if (!context.isExecutionCurrent(origin, reservation.handle)) return null
		return ReaderNativeInitialDestinationSemanticContext(WeakReference(engine), lease, presentationBindingReporter.captureEpoch(),
			version, width, height, presentationPublicationGeneration, presentationViewportGeneration, profile, installed)
	}

	private fun initialDestinationSemanticContextIsCurrent(
		captured: ReaderNativeInitialDestinationSemanticContext,
		candidate: ReaderTransitionGatewayCandidate,
		reservation: ReaderReservedNeutralBootstrapRequest,
		origin: ReaderPresentationEventOrigin.SemanticCommand,
		context: paige.navic.reader.ReaderSemanticMutationStartEvidence,
		admissionWitness: ReaderEngineInitialDestinationConstructionCustody? = null
	): Boolean {
		val current = captureInitialDestinationSemanticContext(candidate, reservation, origin, context, admissionWitness) ?: return false
		val witness = admissionWitness?.takeIf { it.initialAdmissionEvaluationActive }
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.CapturedScopeIdentity
		if (current.engine.get() !== captured.engine.get() || current.installation !== captured.installation ||
			current.installedSnapshot !== captured.installedSnapshot) return false
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.CapturedScopeVersion
		if (current.reporterEpoch != captured.reporterEpoch || current.version != captured.version) return false
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.CapturedScopeDimensions
		if (current.width != captured.width || current.height != captured.height) return false
		witness?.initialAdmissionEvaluatedStage = ReaderInitialAdmissionStage.CapturedScopeGenerations
		return current.publicationGeneration == captured.publicationGeneration && current.viewportGeneration == captured.viewportGeneration &&
			current.profileGeneration == captured.profileGeneration
	}

	/** Post-semantic source authentication on the original installation. This does not
	 * revive consumed executor currency or promote Registered material to display-ready. */
	fun captureInitialResolvedLayoutForAllocation(
		candidate: ReaderTransitionGatewayCandidate,
		command: paige.navic.reader.ReaderTransitionCommand.AllocateMaterialBinding
	): ReaderNativeInitialResolvedLayout? {
		if (Looper.myLooper() != Looper.getMainLooper() || !isAttachedToWindow ||
			windowVisibility != View.VISIBLE || lastPresentationWindowVisible != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted) return null
		val lease = currentFrameInstallation ?: return null
		val resolved = lease.initialResolvedLayout ?: return null
		val captured = resolved.scope
		val installed = activatedSessionStore.snapshot ?: return null
		val id = command.transitionId
		if (installed !== captured.installedSnapshot || !installed.commandEgressOpen ||
			candidate.installationSource !== activatedSessionStore || !candidate.isSelected ||
			!activatedSessionStore.ownsInstalledCandidate(candidate.epoch, id.readerSessionGeneration, id.coordinatorEpoch) ||
			resolved.transitionId != id || resolved.binding != command.binding ||
			id.operation != paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage ||
			id.expectedBinding !is paige.navic.reader.ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ||
			lease !== captured.installation || !lease.owned || lease.operating || lease.permanent || !lease.addInvoked ||
			lease.state != ReaderLegacyResourceState.Registered || lease.binding != null || lease.owner != null || lease.imported != null ||
			lease.beforeCommandAdd == null || !framePhysicalOwnership.hasInstallation(lease.wrapper) ||
			lease.container !== viewerContentContainer || lease.wrapper.parent !== viewerContentContainer || !lease.wrapper.isAttachedToWindow ||
			width != captured.width || height != captured.height || width <= 0 || height <= 0 ||
			presentationPublicationGeneration != captured.publicationGeneration || presentationViewportGeneration != captured.viewportGeneration ||
			(rasterProfileEpoch ?: 0L) != captured.profileGeneration ||
			presentationBindingReporter.captureEpoch() != captured.reporterEpoch ||
			presentationBindingReporter.captureSemanticContextVersionOrNull(command.binding) != resolved.version) return null
		val engine = captured.engine.get() ?: return null
		if (engine !== currentLiveWebView() || !engine.isAttachedToWindow) return null
		var ancestor: View = engine
		while (ancestor !== lease.wrapper) ancestor = ancestor.parent as? View ?: return null
		return resolved.takeIf { it.sourceIsCurrent() }
	}

	var installedPhysicalPublicationIngress: ReaderInstalledPhysicalPublicationIngress? = null
	var installedPageTurnModelIngress: ReaderInstalledPageTurnModelIngress? = null
	var installedPointerTurnRoute: ((ReaderPageTurnDirection, Long) -> Boolean)? = null

	private fun admitInstalledPointerTurn(event: MotionEvent, originalDown: MotionEvent, gestureId: Long): Boolean {
		if (activatedSessionStore.legacyConsequencesOpen) return true
		val movedLeft = event.x < originalDown.x
		if (event.x == originalDown.x) return false
		val next = movedLeft != (pageTurnReadingDirection == "rtl")
		return installedPointerTurnRoute?.invoke(if (next) ReaderPageTurnDirection.Next else ReaderPageTurnDirection.Previous,
			gestureId) == true
	}

	fun beginInstalledPointerSemanticTurn(candidate: ReaderTransitionGatewayCandidate,
		requests: ReaderSemanticExecutableRequestRegistry, direction: ReaderPageTurnDirection, gestureId: Long): Boolean {
		val ingress = installedPageTurnModelIngress ?: return false
		val publication = readInstalledPublication?.invoke() ?: return false
		if (!candidate.isSelected || installedCommandCurrency?.invoke() != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted || !isAttachedToWindow ||
			windowVisibility != VISIBLE || publication.owner !is ReaderPresentationFrameOwner.NativePage) return false
		val deck = playLikeCurlController.installedDeckFor(publication.resource.key) ?: return false
		val engine = currentLiveWebView() as? ReaderEngineWebView ?: return false
		var originalSource: ReaderInstalledCurlGestureSource? = null
		val handle = requests.register { origin, context, onReceipt ->
			val source = originalSource ?: return@register ReaderSemanticExecutableResult.Rejected
			engine.createInstalledPageTurnSemanticRequest(source,
				{ candidate.isSelected && installedCommandCurrency?.invoke() == true && source.work.installationIsCurrent() },
				ReaderBoundPageTurnSemanticIngress { observedSource, observedOrigin, event ->
					if (observedSource !== source || observedOrigin !== origin || !candidate.isSelected) null
					else ingress(event, source.deck.binding, observedOrigin)
				})(origin, context, onReceipt)
		}
		val intent = paige.navic.reader.ReaderPageTurnIntent(direction, paige.navic.reader.ReaderTransitionGestureId(gestureId), handle)
		if (playLikeCurlController.beginInstalledCurlGesture(deck, intent) { observation ->
			if (observation.stage != ReaderInstalledCurlSettlementStage.Started) {
				val outcome = when {
					observation.stage == ReaderInstalledCurlSettlementStage.Rejected -> ReaderPageGestureTerminalOutcome.FailedRenderer
					observation.stage == ReaderInstalledCurlSettlementStage.Cancelled || observation.pageChange == PageChange.NONE -> ReaderPageGestureTerminalOutcome.CancelledByUser
					observation.pageChange == PageChange.NEXT -> ReaderPageGestureTerminalOutcome.CommittedForward
					else -> ReaderPageGestureTerminalOutcome.CommittedBackward
				}
				completeHostGesture(gestureId, outcome)
			}
		} != ReaderPortCommandResult.Accepted) { requests.retire(handle); return false }
		originalSource = playLikeCurlController.originalInstalledCurlGestureSource(deck, intent)
		if (originalSource == null || ReaderSnapshotBackedActivatedGatewayPort(candidate).routeIntent(ReaderTransitionFact.Intent(null, intent)) != ReaderPortCommandResult.Accepted) {
			playLikeCurlController.discardUnstartedInstalledCurlGesture(deck, intent)
			requests.retire(handle)
			return false
		}
		return true
	}

	private var readInstalledPublication: (() -> ReaderOwnerAndInputPublicationSnapshot?)? = null
	private var installedCommandCurrency: (() -> Boolean)? = null
	private class InstalledRasterInvocation(
		val work: ReaderActivatedRasterWork,
		val plan: ReaderPageRasterPreparationPlan,
		val callbacks: ReaderRasterLeaseFactEmitter
	)
	// References to the original command only. Preparation/readiness/release authority
	// belongs to the same original controller lease, including its pre-start interval.
	private var pendingInstalledRaster: InstalledRasterInvocation? = null

	private class InstalledMaterialAllocationInvocation(val command: ReaderTransitionCommand.AllocateMaterialBinding) {
		var query: paige.navic.reader.ReaderSemanticTransportRetirement? = null
		var queryDelivered = false
		var planHandled = false
	}
	// Invocation admission only: the original Engine continuation/FIFO owns its query.
	private var pendingInstalledMaterialAllocation: InstalledMaterialAllocationInvocation? = null

	private fun retireInstalledMaterialAllocation(invocation: InstalledMaterialAllocationInvocation) {
		if (pendingInstalledMaterialAllocation !== invocation) return
		pendingInstalledMaterialAllocation = null // Fence the original plan callback before outward retirement.
		val query = invocation.query
		invocation.query = null
		query?.retire()
	}

	fun hasInitialCommandContext(): Boolean = finalHostLifecycleEvent == null && !task4ResourceTeardownStarted &&
		presentationBindingReporter.captureInitialSemanticContextVersionOrNull() != null &&
		currentFrameInstallation == null && context is Activity

	fun bindInstalledPublicationReaders(
		readPublication: () -> ReaderOwnerAndInputPublicationSnapshot?,
		commandCurrency: () -> Boolean
	): ReaderPortCommandResult {
		if (readInstalledPublication != null) return installedRejected()
		readInstalledPublication = readPublication
		installedCommandCurrency = commandCurrency
		return pageInputSettlementHostController.activateInstalledInput(readPublication) {
			activatedSessionStore.snapshot?.initialDecision?.origin.takeIf { commandCurrency() }
		}
	}

	fun narrowInstalledInputLease(command: ReaderTransitionCommand?, lease: ReaderTransitionInputLease): ReaderTransitionInputLease {
		fun current(): Boolean = Looper.myLooper() == Looper.getMainLooper() && installedCommandCurrency?.invoke() == true &&
			finalHostLifecycleEvent == null && !task4ResourceTeardownStarted &&
			isAttachedToWindow && windowVisibility == VISIBLE && lastPresentationWindowVisible == true
		if (!current()) return ReaderTransitionInputLease.None
		return when (lease) {
			is ReaderTransitionInputLease.NativePage -> {
				// The original command identifies the prepared readable successor BEFORE
				// its sole atomic publication. A requested lease alone is never evidence.
				val owner: ReaderPresentationFrameOwner
				val binding: ReaderPresentationBinding
				val resource: paige.navic.reader.ReaderTransitionResourceRegistration
				when (command) {
					is ReaderTransitionCommand.CommitOwnerAndInputLease -> {
						owner = command.owner; binding = command.binding; resource = command.preparedFrameResource
					}
					is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> {
						owner = command.retainedOwner; binding = command.retainedBinding; resource = command.retainedResource
					}
					else -> return ReaderTransitionInputLease.ChromeOnly
				}
				val transitionId = command.transitionId ?: return ReaderTransitionInputLease.ChromeOnly
				val native = owner as? ReaderPresentationFrameOwner.NativePage ?: return ReaderTransitionInputLease.ChromeOnly
				val domain = playLikeCurlController.activatedMaterialDomain ?: return ReaderTransitionInputLease.ChromeOnly
				val installed = activatedSessionStore.snapshot ?: return ReaderTransitionInputLease.ChromeOnly
				val initial = installed.initialDecision?.origin ?: return ReaderTransitionInputLease.ChromeOnly
				val engine = currentLiveWebView() as? ReaderEngineWebView ?: return ReaderTransitionInputLease.ChromeOnly
				if (!installed.commandEgressOpen || installedPhysicalPublicationIngress == null || installedPageTurnModelIngress == null ||
					installedPointerTurnRoute == null || !engine.isAttachedToWindow || !engine.admitsCurrentDestinationSession(binding.foliateSessionId) ||
					pageRasterPreparationController.activatedMaterialDomain != domain || pageTurnBundleSource.activatedMaterialDomain != domain ||
					domain.readerSessionGeneration != transitionId.readerSessionGeneration ||
					initial.readerSessionGeneration != transitionId.readerSessionGeneration || initial.coordinatorEpoch != transitionId.coordinatorEpoch ||
					resource.retirementOrder.readerSessionGeneration != transitionId.readerSessionGeneration ||
					resource.retirementOrder.coordinatorEpoch != transitionId.coordinatorEpoch || resource.key.kind != ReaderTransitionResourceKind.Deck ||
					binding != lease.binding || native.proof.binding != lease.binding ||
					native.proof.textureGeneration != lease.textureGeneration || binding.textureGeneration != lease.textureGeneration)
					return ReaderTransitionInputLease.ChromeOnly
				val deck = playLikeCurlController.installedDeckFor(resource.key) ?: return ReaderTransitionInputLease.ChromeOnly
				val candidate = playLikeCurlController.installedNativeCandidate(deck, native.proof.transitionToken, width, height)
					?: return ReaderTransitionInputLease.ChromeOnly
				fun candidateCurrent(): Boolean = current() && activatedSessionStore.snapshot === installed && currentLiveWebView() === engine &&
					engine.isAttachedToWindow && engine.admitsCurrentDestinationSession(binding.foliateSessionId) &&
					playLikeCurlController.installedDeckFor(resource.key) === deck && deck.binding == binding &&
					playLikeCurlController.installedNativeCandidate(deck, native.proof.transitionToken, width, height) == candidate &&
					candidate.binding == native.proof.binding && candidate.viewportWidth == native.proof.viewportWidth &&
					candidate.viewportHeight == native.proof.viewportHeight
				if (!candidateCurrent()) return ReaderTransitionInputLease.ChromeOnly
				// Readiness only updates the original local veto, never an owner/policy grant.
				val safety = readerPageOperationPolicy(candidate.preparationFacts.readiness)
				pageInputSettlementHostController.updateInstalledLocalSafety(safety)
				if (!candidateCurrent()) return ReaderTransitionInputLease.ChromeOnly
				val narrowed = pageInputSettlementHostController.narrowOrVeto(lease)
				if (candidateCurrent()) narrowed else ReaderTransitionInputLease.ChromeOnly
			}
			is ReaderTransitionInputLease.ClaimedGesture -> {
				// The original physical stream alone can retain its claim, never grant a
				// new pointer or upgrade the retained publication to Native input.
				val publication = readInstalledPublication?.invoke() ?: return ReaderTransitionInputLease.ChromeOnly
				val retained = command as? ReaderTransitionCommand.PublishRetainedOwnerAndInputLease
					?: return ReaderTransitionInputLease.ChromeOnly
				val expected = lease.transitionId.expectedBinding as? paige.navic.reader.ReaderExpectedPresentationBinding.SemanticSuccessor
				if (expected?.predecessor != publication.binding || retained.transitionId != lease.transitionId ||
					retained.retainedOwner != publication.owner || retained.retainedBinding != publication.binding ||
					retained.retainedResource != publication.resource || publication.owner !is ReaderPresentationFrameOwner.NativePage ||
					publication.resource.retirementOrder.readerSessionGeneration != lease.transitionId.readerSessionGeneration ||
					publication.resource.retirementOrder.coordinatorEpoch != lease.transitionId.coordinatorEpoch ||
					playLikeCurlController.installedDeckFor(publication.resource.key)?.binding != publication.binding)
					ReaderTransitionInputLease.ChromeOnly
				else {
					val narrowed = pageInputSettlementHostController.narrowOrVeto(lease)
					if (current() && readInstalledPublication?.invoke() === publication) narrowed else ReaderTransitionInputLease.ChromeOnly
				}
			}
			ReaderTransitionInputLease.None, ReaderTransitionInputLease.ChromeOnly -> lease
			else -> ReaderTransitionInputLease.ChromeOnly
		}
	}

	fun canPublishInstalledOwnerAndInput(publication: ReaderOwnerAndInputPublicationSnapshot): Boolean {
		if (Looper.myLooper() != Looper.getMainLooper() || installedCommandCurrency?.invoke() != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted || !isAttachedToWindow || windowVisibility != VISIBLE)
			return false
		val deck = playLikeCurlController.installedDeckFor(publication.resource.key) ?: return false
		if (deck.binding != publication.binding) return false
		val native = publication.owner as? ReaderPresentationFrameOwner.NativePage ?: return false
		val candidate = playLikeCurlController.installedNativeCandidate(deck, native.proof.transitionToken, width, height)
		return candidate != null && native.proof.binding == candidate.binding &&
			native.proof.viewportWidth == candidate.viewportWidth && native.proof.viewportHeight == candidate.viewportHeight
	}

	/** Only the ORIGINAL accepted FIFO row projects physical owner/input. Recomposition
	 * and generic observations may refresh decoration, never borrow an unaccepted barrier. */
	fun refreshInstalledPresentationConsumers(command: ReaderTransitionCommand,
		applied: ReaderOwnerAndInputPublicationResult.Applied): Boolean {
		fun rejected(): Boolean {
			presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly
			pageInputSettlementHostController.updateInstalledLocalSafety(readerPageOperationPolicy(ReaderPageReadinessState()))
			return false
		}
		val readers = readInstalledPublication ?: return rejected()
		val resource = when (command) {
			is ReaderTransitionCommand.CommitOwnerAndInputLease -> command.preparedFrameResource
			is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> command.retainedResource
			else -> return rejected()
		}
		// Carry the ORIGINAL committed barrier object. Reading it only validates this
		// accepted command/result; structural equality cannot replace Curl's identity guard.
		val publication = readers()
		val acceptedContextMatches = when (command) {
			is ReaderTransitionCommand.CommitOwnerAndInputLease ->
				applied.publicationIdentity == command.publicationIdentity && applied.publishedOwner == command.owner &&
					applied.publishedBinding == command.binding && applied.subject ==
					paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(command.targetHandle, resource)
			is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease ->
				applied.publicationIdentity == command.publicationIdentity && applied.publishedOwner == command.retainedOwner &&
					applied.publishedBinding == command.retainedBinding && applied.subject ==
					paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Retained(resource)
			else -> false
		}
		if (Looper.myLooper() != Looper.getMainLooper() || installedCommandCurrency?.invoke() != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted || !isAttachedToWindow || windowVisibility != VISIBLE ||
			!acceptedContextMatches || applied.transitionId != command.transitionId || publication == null ||
			publication.owner != applied.publishedOwner || publication.binding != applied.publishedBinding ||
			publication.resource != resource || publication.lease != applied.finalPhysicalInputLease) return rejected()
		if (playLikeCurlController.onInstalledOwnerAndInputPublished(publication) != ReaderPortCommandResult.Accepted)
			return rejected()
		presentationInputPolicy = paige.navic.reader.readerTransitionPresentationInputPolicy(
			publication.owner, publication.binding, publication.lease)
		// Decoration is independently readable from the committed displayed deck even
		// when another preparation/native-candidate command cannot currently progress.
		refreshInstalledWhispersyncConsumer()
		val deck = playLikeCurlController.installedDeckFor(publication.resource.key) ?: return rejected()
		val candidate = playLikeCurlController.installedNativeCandidate(deck,
			(publication.owner as? ReaderPresentationFrameOwner.NativePage)?.proof?.transitionToken, width, height)
			?: return rejected()
		val nativeProof = (publication.owner as? ReaderPresentationFrameOwner.NativePage)?.proof
		if (deck.binding != publication.binding || candidate.binding != publication.binding ||
			(nativeProof != null && (nativeProof.transitionToken != candidate.transitionToken ||
				nativeProof.viewportWidth != candidate.viewportWidth || nativeProof.viewportHeight != candidate.viewportHeight)) ||
			readers() !== publication || installedCommandCurrency?.invoke() != true) return rejected()
		localPageSafetyPolicy = readerPageOperationPolicy(candidate.preparationFacts.readiness)
		pageInputSettlementHostController.updateInstalledLocalSafety(localPageSafetyPolicy)
		playLikeCurlController.surfaceView.alpha = 1f
		playLikeCurlController.inlineRasterShieldView.alpha = 0f
		playLikeCurlController.surfaceView.bringToFront()
		shellCoverView?.visibility = GONE
		whispersyncCueMapView.bringToFront()
		// Outward owning projection remains on the original accepted-publication closure,
		// never a generic observation or a sampled publication reader.
		if (readers() !== publication || installedCommandCurrency?.invoke() != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted || !isAttachedToWindow || windowVisibility != VISIBLE)
			return rejected()
		return true
	}

	/** Display authority comes from the sole atomic owner publication AND B's original
	 * displayed-deck record. Incoming word anchors cannot certify their own display.
	 * Optional preparation/semantic progress does not make this record disposable. */
	private fun refreshInstalledWhispersyncConsumer(): ReaderPortCommandResult {
		if (Looper.myLooper() != Looper.getMainLooper() || installedCommandCurrency?.invoke() != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted || !isAttachedToWindow || windowVisibility != VISIBLE)
			return installedRejected()
		val publication = readInstalledPublication?.invoke() ?: return installedRejected()
		val deck = playLikeCurlController.installedDeckFor(publication.resource.key) ?: return installedRejected()
		if (deck.binding != publication.binding) return installedRejected()
		val association = playLikeCurlController.installedDecorationAssociationFor(deck) ?: return installedRejected()
		val proof = association.displayedProof
		val ownerMatches = when (val owner = publication.owner) {
			is ReaderPresentationFrameOwner.NativePage -> owner.proof == proof
			is ReaderPresentationFrameOwner.Curl -> owner.frame.binding == proof.binding &&
				owner.frame.presentedFrame == proof.presentedFrame &&
				owner.frame.rasterGeneration == proof.rasterGeneration && owner.frame.textureGeneration == proof.textureGeneration
			else -> false
		}
		if (!ownerMatches) return installedRejected()
		return playLikeCurlController.setInstalledWhispersyncOverlay(deck, proof,
			whispersyncAnchorReceipt.takeIf { whispersyncOverlayActive }, whispersyncHighlightColorArgb)
	}

	fun activateInstalledFrameCommands() {
		nativePagePresentationPublisher.activateCommandOnly { specification ->
			if (installedCommandCurrency?.invoke() != true || finalHostLifecycleEvent != null || task4ResourceTeardownStarted ||
				!isAttachedToWindow || windowVisibility != VISIBLE ||
				playLikeCurlController.surfaceView.rendererGeneration != specification.deckTarget.rendererGeneration)
				return@activateCommandOnly null
			val key = ReaderTransitionResourceKey(specification.transitionId, ReaderTransitionResourceKind.Deck,
				specification.deckTarget.deckGeneration)
			val deck = playLikeCurlController.installedDeckFor(key) ?: return@activateCommandOnly null
			val token = when (val hostToken = specification.hostToken) {
				ReaderNativePageHostTokenState.AuthoritativeAbsent -> null
				is ReaderNativePageHostTokenState.Present -> paige.navic.reader.ReaderPresentationToken(hostToken.token.value)
			}
			playLikeCurlController.installedNativeCandidate(deck, token, width, height)
		}
		// Typed terminal admission shares the ORIGINAL publisher, cancellation and frame
		// custody. Only a genuine source completion joined to its exact successor is eligible;
		// an ordinary Native frame is never a fallback.
		nativePagePresentationPublisher.activateCurlTerminalCommands { specification ->
			if (installedCommandCurrency?.invoke() != true || finalHostLifecycleEvent != null || task4ResourceTeardownStarted ||
				!isAttachedToWindow || windowVisibility != VISIBLE)
				return@activateCurlTerminalCommands null
			playLikeCurlController.installedCurlTerminalCandidate(specification)
		}
	}

	fun createInstalledFramePort(): ReaderFramePresentationPort = object : ReaderFramePresentationPort {
		override fun prepareTarget(command: ReaderTransitionCommand.PrepareFrameTarget,
			onFact: (ReaderTransitionFact) -> Unit) = nativePagePresentationPublisher.prepareTarget(command, onFact)
		override fun present(command: ReaderTransitionCommand.RequestFramePresentation,
			onFact: (ReaderTransitionFact) -> Unit) = nativePagePresentationPublisher.present(command, onFact)
	}

	fun prepareInstalledRaster(command: ReaderTransitionCommand.RequestRasterPreparation,
		domain: ReaderLegacyPhysicalDomain, commandIsCurrent: (ReaderRasterPreparationLease) -> Boolean,
		onFact: (ReaderTransitionFact) -> Unit): ReaderPortCommandResult {
		if (pendingInstalledRaster != null || installedCommandCurrency?.invoke() != true) return installedRejected()
		val allocation = command.allocation ?: return installedRejected()
		val lease = ReaderRasterPreparationLease(command.transitionId, command.binding, allocation.preparationGeneration,
			allocation.rasterGeneration, ReaderTransitionResourceKey(command.transitionId, ReaderTransitionResourceKind.Raster,
				allocation.rasterGeneration), allocation)
		val installed = activatedSessionStore.snapshot ?: return installedRejected()
		val initial = command.transitionId.operation == paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage &&
			command.transitionId.expectedBinding is paige.navic.reader.ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial
		val resolved = if (initial) currentFrameInstallation?.initialResolvedLayout else null
		if (initial && (resolved?.captureBasis == null || resolved.transitionId != command.transitionId ||
			allocation.requestedBinding != resolved.binding || allocation.allocatedBinding != command.binding ||
			resolved.captureBasis.destinationCommitIdentity != command.binding.destinationCommitIdentity ||
			resolved.captureBasis.resolvedLayout != resolved.layout)) return installedRejected()
		fun sourceCurrent(): Boolean {
			if (!initial) return true
			val original = resolved ?: return false
			val captured = original.scope
			val installation = captured.installation
			val engine = captured.engine.get() ?: return false
			if (Looper.myLooper() != Looper.getMainLooper() || !isAttachedToWindow || windowVisibility != VISIBLE ||
				lastPresentationWindowVisible != true || finalHostLifecycleEvent != null || task4ResourceTeardownStarted ||
				activatedSessionStore.snapshot !== captured.installedSnapshot || installed !== captured.installedSnapshot ||
				currentFrameInstallation !== installation || installation.initialResolvedLayout !== original ||
				!installation.owned || installation.operating || installation.permanent || !installation.addInvoked ||
				installation.imported != null || installation.state != ReaderLegacyResourceState.Registered ||
				installation.owner != null || installation.binding != null || installation.beforeCommandAdd == null ||
				!framePhysicalOwnership.hasInstallation(installation.wrapper) || installation.container !== viewerContentContainer ||
				installation.wrapper.parent !== viewerContentContainer || !installation.wrapper.isAttachedToWindow ||
				engine !== currentLiveWebView() || !engine.isAttachedToWindow ||
				width != captured.width || height != captured.height || width <= 0 || height <= 0 ||
				presentationPublicationGeneration != captured.publicationGeneration || presentationViewportGeneration != captured.viewportGeneration ||
				(rasterProfileEpoch ?: 0L) != captured.profileGeneration ||
				presentationBindingReporter.captureEpoch() != captured.reporterEpoch ||
				presentationBindingReporter.captureSemanticContextVersionOrNull(original.binding) != original.version) return false
			var ancestor: View = engine
			while (ancestor !== installation.wrapper) ancestor = ancestor.parent as? View ?: return false
			return original.sourceIsCurrent()
		}
		val work = ReaderActivatedRasterWork(domain, activatedSessionStore, installed, lease, resolved?.captureBasis,
			mainThreadSourceIsCurrent = if (initial) ::sourceCurrent else null) {
			commandIsCurrent(lease)
		}
		val plan = playLikeCurlController.activatedPreparationPlan(work) ?: return installedRejected()
		val engine = currentLiveWebView() ?: return installedRejected()
		val invocation = InstalledRasterInvocation(work, plan, ReaderRasterLeaseFactEmitter(lease, onFact))
		pendingInstalledRaster = invocation // References precede construction/callback reentry, not a second lease.
		if (!ensureInstalledPassiveRasterAdapter(engine, work)) {
			if (pendingInstalledRaster === invocation) pendingInstalledRaster = null
			return installedRejected()
		}
		// Call exactly once, even before availability. The original preparation controller
		// registers this same work before awaitInstalledReadiness in its physical-start
		// closure; the genuine adapter-ready edge resumes it, not Native polling/retry.
		val result = pageRasterPreparationController.prepareInstalled(work, plan,
			onReady = {
				if (pendingInstalledRaster === invocation) pendingInstalledRaster = null
				// Detach invocation admission only. The keyed work remains readable and
				// owned until its original physical return, including reentrant deck use.
				if (work.isCurrent()) invocation.callbacks.onProven(lease)
			},
			onFailed = {
				if (pendingInstalledRaster === invocation) pendingInstalledRaster = null
				if (work.isCurrent()) invocation.callbacks.onFailed(lease, ReaderTransitionFailureReason.PortRejected)
			})
		if (result == ReaderPortCommandResult.Accepted) invocation.callbacks.registerResource()
		else if (pageRasterPreparationController.installedWorkFor(lease.resourceKey) !== work &&
			pendingInstalledRaster === invocation) pendingInstalledRaster = null
		return result
	}

	fun reserveInstalledDeck(command: ReaderTransitionCommand.ReserveDeck,
		onFact: (ReaderTransitionFact) -> Unit,
		declareTarget: (ReaderTransitionFrameTargetSpecification, ReaderTransitionResourceKey) -> ReaderPortCommandResult
	): ReaderPortCommandResult {
		val raster = command.binding.rasterGeneration ?: return installedRejected()
		val rasterKey = ReaderTransitionResourceKey(command.transitionId, ReaderTransitionResourceKind.Raster, raster)
		val work = pageRasterPreparationController.installedWorkFor(rasterKey)
			?.takeIf { it.lease.resourceKey == rasterKey && it.lease.binding == command.binding &&
				it.lease.allocation == command.allocation && it.isCurrent() } ?: return installedRejected()
		val texture = command.binding.textureGeneration ?: return installedRejected()
		val deck = readerDeckLeaseOrNull(command,
			ReaderTransitionResourceKey(command.transitionId, ReaderTransitionResourceKind.Deck, texture)) ?: return installedRejected()
		return playLikeCurlController.reserveInstalledDeck(work, deck) { fact ->
			onFact(fact)
			if (fact is ReaderTransitionFact.DeckPrepared && work.isCurrent()) {
				val renderer = playLikeCurlController.surfaceView.rendererGeneration
				val candidate = playLikeCurlController.installedNativeCandidate(deck, null, width, height)
				if (renderer <= 0L || candidate == null) {
					onFact(ReaderTransitionFact.CommandRejected(command.transitionId,
						paige.navic.reader.ReaderTransitionCommandStage.DeckReservation,
						paige.navic.reader.ReaderTransitionCommandRejectionReason.DeckReservationRejected))
					return@reserveInstalledDeck
				}
				onFact(ReaderTransitionFact.RendererGenerationReady(command.transitionId, renderer))
				val deckTarget = ReaderPlayLikeCurlDeckTargetIdentity(renderer, texture, command.role)
				val geometry = ReaderTransitionFrameGeometry(deck.binding.viewportGeneration, deck.binding.profileGeneration,
					0, 0, candidate.viewportWidth, candidate.viewportHeight)
				val specification = if (command.transitionId.operation == paige.navic.reader.ReaderTransitionOperation.CurlClaimAndSettlement) {
					playLikeCurlController.installedCurlTerminalSpecification(deck, deckTarget, geometry) ?: return@reserveInstalledDeck
				} else ReaderTransitionFrameTargetSpecification.NativePage(command.transitionId,
					command.transitionId.readerSessionGeneration, deck.binding.publicationGeneration, deck.binding, checkNotNull(deck.allocation),
					ReaderNativePageHostTokenState.AuthoritativeAbsent, deckTarget, geometry, command.transitionId.sequence)
				declareTarget(specification, deck.resourceKey)
			}
		}
	}

	fun createInstalledResourcePort(frameSource: ReaderFrameOrHandoffPhysicalSource,
		executor: ReaderSemanticCommandExecutor): ReaderTransitionResourcePort = object : ReaderTransitionResourcePort {
		override fun release(command: ReaderTransitionCommand.ReleaseResource,
			onFact: (ReaderTransitionFact.ResourceReleased) -> Unit): ReaderPortCommandResult {
			val returned = { onFact(ReaderTransitionFact.ResourceReleased(command.identity)) }
			return when (command.key.kind) {
				ReaderTransitionResourceKind.Raster -> {
					val work = pageRasterPreparationController.installedWorkFor(command.key)
						?: playLikeCurlController.installedWorkFor(command.key) ?: return installedRejected()
					val invocation = pendingInstalledRaster?.takeIf { it.work === work }
					work.retire()
					// The same original lease owns waiting readiness and running preparation.
					// Retirement alone never confirms physical callback/removal/destruction.
					pageRasterPreparationController.releaseInstalled(work) {
						if (invocation != null && pendingInstalledRaster === invocation) pendingInstalledRaster = null
						returned()
					}
				}
				ReaderTransitionResourceKind.Deck -> {
					val deck = playLikeCurlController.installedDeckFor(command.key) ?: return installedRejected()
					playLikeCurlController.releaseInstalledDeck(deck, returned)
				}
				ReaderTransitionResourceKind.FrameHandoff, ReaderTransitionResourceKind.CallbackRegistration -> installedRejected()
			}
		}
		override fun releaseLegacy(command: ReaderTransitionCommand.ReleaseResource,
			imported: ReaderImportedLegacyResourceRegistration,
			onConfirmed: (ReaderLegacyPhysicalIdentity, ReaderTransitionFact.ResourceReleased) -> Unit): ReaderPortCommandResult =
			when (imported.physicalIdentity.source) {
				ReaderLegacyInventorySource.FrameOrHandoff -> frameSource.releaseLegacy(command, imported, onConfirmed)
				ReaderLegacyInventorySource.Deck -> playLikeCurlController.releaseActivatedDeckOwnership(imported) { identity ->
					onConfirmed(identity, ReaderTransitionFact.ResourceReleased(command.identity))
				}
				else -> installedRejected()
			}
		override fun cancelOwnedWork(command: ReaderTransitionCommand.CancelOwnedWork): ReaderPortCommandResult {
			pendingInstalledMaterialAllocation?.takeIf { it.command.transitionId == command.transitionId }
				?.let(::retireInstalledMaterialAllocation)
			// Fence the exact admission slot before publisher/executor/query retirement
			// can reenter. Logical retirement cannot return its keyed physical material.
			val pending = pendingInstalledRaster?.takeIf { it.work.lease.transitionId == command.transitionId }
			if (pending != null && pendingInstalledRaster === pending) pendingInstalledRaster = null
			pending?.work?.retire()
			val frameResult = nativePagePresentationPublisher.cancelOwnedWork(command.transitionId)
			executor.retireTransition(command.transitionId)
			if (command.cleanupKey != null) closeCurrentDestinationQueries()
			return frameResult // Only the original release callbacks confirm physical removal.
		}
	}

	private fun installedRejected() = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.StaleProof)

	fun createOriginalInitialMaterialAllocationPort(
		candidate: ReaderTransitionGatewayCandidate,
		isCommandCurrent: (paige.navic.reader.ReaderTransitionCommand.AllocateMaterialBinding) -> Boolean,
		enqueue: (ReaderTransitionFact) -> Unit
	): ReaderMaterialGenerationAllocationPort = ReaderMaterialGenerationAllocationPort allocate@{ command, onFact ->
		if (pendingInstalledMaterialAllocation != null) return@allocate installedRejected()
		val domain = pageRasterPreparationController.activatedMaterialDomain ?: return@allocate installedRejected()
		val installed = activatedSessionStore.snapshot ?: return@allocate installedRejected()
		val installation = currentFrameInstallation ?: return@allocate installedRejected()
		val engine = currentLiveWebView() ?: return@allocate installedRejected()
		val source = engine as? ReaderEngineCurrentDestinationQuerySource ?: return@allocate installedRejected()
		val epoch = presentationBindingReporter.captureEpoch()
		val measuredWidth = width
		val measuredHeight = height
		val publicationGeneration = presentationPublicationGeneration
		val viewportGeneration = presentationViewportGeneration
		val profile = rasterProfileEpoch
		fun physicalCurrent(): Boolean {
			val initial = installed.initialDecision?.origin ?: return false
			if (Looper.myLooper() != Looper.getMainLooper() || !isAttachedToWindow || windowVisibility != VISIBLE ||
				lastPresentationWindowVisible != true || finalHostLifecycleEvent != null || task4ResourceTeardownStarted ||
				activatedSessionStore.snapshot !== installed || !installed.commandEgressOpen ||
				candidate.installationSource !== activatedSessionStore || !candidate.isSelected ||
				!activatedSessionStore.ownsInstalledCandidate(candidate.epoch, command.transitionId.readerSessionGeneration, command.transitionId.coordinatorEpoch) ||
				initial.readerSessionGeneration != command.transitionId.readerSessionGeneration || initial.coordinatorEpoch != command.transitionId.coordinatorEpoch ||
				pageRasterPreparationController.activatedMaterialDomain != domain || pageTurnBundleSource.activatedMaterialDomain != domain ||
				playLikeCurlController.activatedMaterialDomain != domain || domain.readerSessionGeneration != command.transitionId.readerSessionGeneration ||
				currentFrameInstallation !== installation || !installation.owned || installation.operating || installation.permanent ||
				!framePhysicalOwnership.hasInstallation(installation.wrapper) || installation.container !== viewerContentContainer ||
				installation.wrapper.parent !== viewerContentContainer || !installation.wrapper.isAttachedToWindow ||
				currentLiveWebView() !== engine || !engine.isAttachedToWindow || !source.admitsCurrentDestinationSession(command.binding.foliateSessionId) ||
				width != measuredWidth || height != measuredHeight || width <= 0 || height <= 0 ||
				presentationBindingReporter.captureEpoch() != epoch || presentationPublicationGeneration != publicationGeneration ||
				presentationViewportGeneration != viewportGeneration || rasterProfileEpoch != profile ||
				command.binding.publicationGeneration != publicationGeneration || command.binding.viewportGeneration != viewportGeneration ||
				command.binding.destinationCommitIdentity == null || command.binding.preparationGeneration != null ||
				command.binding.rasterGeneration != null || command.binding.textureGeneration != null) return false
			var ancestor: View = engine
			while (ancestor !== installation.wrapper) ancestor = ancestor.parent as? View ?: return false
			return isCommandCurrent(command)
		}
		if (!physicalCurrent()) return@allocate installedRejected()
		val initialCommand = command.transitionId.operation == paige.navic.reader.ReaderTransitionOperation.BootstrapNativePage &&
			command.transitionId.expectedBinding is paige.navic.reader.ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial
		val resolved = if (initialCommand) captureInitialResolvedLayoutForAllocation(candidate, command) else null
		if (initialCommand && resolved == null) return@allocate installedRejected()
		val invocation = InstalledMaterialAllocationInvocation(command)
		pendingInstalledMaterialAllocation = invocation // Retain BEFORE query or Javascript may reenter cancel/close.
		fun current(): Boolean = pendingInstalledMaterialAllocation === invocation && physicalCurrent() &&
			pendingInstalledMaterialAllocation === invocation
		fun reject(reason: paige.navic.reader.ReaderTransitionCommandRejectionReason =
			paige.navic.reader.ReaderTransitionCommandRejectionReason.MaterialAllocationRejected) {
			if (!current()) { retireInstalledMaterialAllocation(invocation); return }
			retireInstalledMaterialAllocation(invocation)
			if (physicalCurrent()) enqueue(ReaderTransitionFact.CommandRejected(command.transitionId,
				paige.navic.reader.ReaderTransitionCommandStage.MaterialAllocation, reason))
		}
		fun requestPlan(layout: paige.navic.reader.ReaderFoliateResolvedLayout, layoutCurrent: () -> Boolean) {
			fun planCurrent(): Boolean = current() && layoutCurrent() && current()
			if (!planCurrent()) { retireInstalledMaterialAllocation(invocation); return }
			engine.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(null) ?? null)"
			) callback@{ encoded ->
				if (invocation.planHandled) return@callback
				invocation.planHandled = true
				try {
					if (!planCurrent()) { retireInstalledMaterialAllocation(invocation); return@callback }
					val plan = runCatching { readerPageRasterPreparationPlan(encoded) }.getOrNull()
					if (plan == null || plan.resolvedLayout != layout || plan.currentChapterIndex != layout.sectionIndex ||
						plan.currentChapterPageStartIndex < 0 || plan.currentChapterPageCount != layout.pageCount ||
						plan.centerPageIndex.toLong() - plan.currentChapterPageStartIndex.toLong() != layout.pageIndex.toLong() ||
						plan.currentChapterPageStartIndex.toLong() + plan.currentChapterPageCount.toLong() > plan.pageCount.toLong() ||
						plan.blockingTargetsOrNull() == null) {
						reject(); return@callback
					}
					// Same complete initial/successor validation, same original counter owners and order.
					// Reservations never manufacture readiness, presentation, a deck or an ACK.
					if (!planCurrent()) { retireInstalledMaterialAllocation(invocation); return@callback }
					val preparation = pageRasterPreparationController.reserveActivatedPreparationGeneration(command, domain)
					if (!planCurrent()) { retireInstalledMaterialAllocation(invocation); return@callback }
					val raster = preparation?.let { pageTurnBundleSource.reserveActivatedRasterGeneration(command, domain) }
					if (!planCurrent()) { retireInstalledMaterialAllocation(invocation); return@callback }
					val allocation = if (preparation != null && raster != null)
						playLikeCurlController.reserveActivatedMaterialBinding(command, domain, engine, plan, preparation, raster) else null
					if (allocation == null) { reject(); return@callback }
					if (!planCurrent()) { retireInstalledMaterialAllocation(invocation); return@callback }
					retireInstalledMaterialAllocation(invocation) // Consumed before outward coordinator callbacks.
					if (!physicalCurrent() || !layoutCurrent()) return@callback
					// Scalar qualification belongs only to initial bootstrap. Successor/retry
					// profile association is carried by this same original allocation.
					if (initialCommand) enqueue(ReaderTransitionFact.PaginationProfileReady(command.transitionId,
						allocation.allocatedBinding.profileGeneration))
					if (physicalCurrent() && layoutCurrent()) onFact(ReaderTransitionFact.MaterialBindingAllocated(command.transitionId, allocation))
				} catch (_: Throwable) {
					reject(paige.navic.reader.ReaderTransitionCommandRejectionReason.CommandThrew)
				}
			}
		}
		try {
			if (resolved != null) {
				// This remains genuinely initial-only; never reuse its retired query for a successor.
				requestPlan(resolved.layout) { captureInitialResolvedLayoutForAllocation(candidate, command) === resolved }
			} else {
				if (!current()) { retireInstalledMaterialAllocation(invocation); return@allocate installedRejected() }
				val retirement = source.queryAllocationLayout(command, ::current) outcome@{ outcome ->
					if (invocation.queryDelivered) return@outcome
					if (!current()) { retireInstalledMaterialAllocation(invocation); return@outcome }
					invocation.queryDelivered = true
					try {
						val event = (outcome as? ReaderCurrentDestinationQueryOutcome.Resolved)?.event
						val layout = event?.resolvedLayout
						// Capture ONLY in the original successful delivery. Its lifetime is not
						// the consumed query token, the semantic slot or the invocation admission.
						val layoutCurrency = if (event != null) source.captureResolvedLayoutCurrency() else null
						if (event == null || layout == null || layoutCurrency == null ||
							event.foliateSessionId != command.binding.foliateSessionId ||
							event.destinationCommitIdentity != command.binding.destinationCommitIdentity || !current()) {
							reject(); return@outcome
						}
						requestPlan(layout, layoutCurrency)
					} catch (_: Throwable) {
						reject(paige.navic.reader.ReaderTransitionCommandRejectionReason.CommandThrew)
					}
				}
				if (!current()) {
					retireInstalledMaterialAllocation(invocation)
					retirement?.retire()
				}
				else if (retirement == null) { retireInstalledMaterialAllocation(invocation); return@allocate installedRejected() }
				else invocation.query = retirement
			}
		} catch (failure: Throwable) {
			retireInstalledMaterialAllocation(invocation)
			throw failure // Original command adapter accounts the actual throw; no invented return.
		}
		ReaderPortCommandResult.Accepted
	}

	fun createInitialDestinationSemanticRequest(
		candidate: ReaderTransitionGatewayCandidate,
		reservation: ReaderReservedNeutralBootstrapRequest,
		ingress: (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
	): ReaderSemanticExecutableRequest = request@{ origin, context, onReceipt ->
		val captured = captureInitialDestinationSemanticContext(candidate, reservation, origin, context)
			?: return@request ReaderSemanticExecutableResult.Rejected
		val source = captured.engine.get() as? ReaderEngineCurrentDestinationQuerySource
			?: return@request ReaderSemanticExecutableResult.Rejected
		val request = source.createInitialDestinationSemanticRequest(reservation, captured.version,
			captured.publicationGeneration, captured.viewportGeneration, captured.profileGeneration,
			{ initialDestinationSemanticContextIsCurrent(captured, candidate, reservation, origin, context) }, ingress)
		request(origin, context, onReceipt)
	}

	private fun captureCurrentDestinationSemanticContext(
		origin: ReaderPresentationEventOrigin.SemanticCommand
	): ReaderNativeCurrentDestinationSemanticContext? {
		if (Looper.myLooper() != Looper.getMainLooper() || !isAttachedToWindow ||
			windowVisibility != View.VISIBLE || lastPresentationWindowVisible != true ||
			finalHostLifecycleEvent != null || task4ResourceTeardownStarted || width <= 0 || height <= 0) return null
		val installed = activatedSessionStore.snapshot?.takeIf { it.commandEgressOpen } ?: return null
		val initial = installed.initialDecision?.origin ?: return null
		if (initial.readerSessionGeneration != origin.transitionId.readerSessionGeneration ||
			initial.coordinatorEpoch != origin.transitionId.coordinatorEpoch) return null
		val engine = currentLiveWebView() ?: return null
		val source = engine as? ReaderEngineCurrentDestinationQuerySource ?: return null
		val binding = currentPresentationBindingOrNull() ?: return null
		val profile = rasterProfileEpoch?.takeIf { it > 0L } ?: return null
		val page = pageTurnVisualPageIndex?.takeIf { it >= 0 } ?: return null
		if (presentationPublicationGeneration <= 0L || presentationViewportGeneration <= 0L ||
			binding.publicationGeneration != presentationPublicationGeneration ||
			binding.viewportGeneration != presentationViewportGeneration || binding.profileGeneration != profile ||
			binding.destinationCommitIdentity == null ||
			(binding.rasterGeneration == null) != (binding.textureGeneration == null) ||
			!source.admitsCurrentDestinationSession(binding.foliateSessionId)) return null
		val version = presentationBindingReporter.captureSemanticContextVersionOrNull(binding) ?: return null
		if (version.readerSessionGeneration != origin.transitionId.readerSessionGeneration) return null
		return ReaderNativeCurrentDestinationSemanticContext(WeakReference(engine), presentationBindingReporter.captureEpoch(),
			version, binding, width, height, page, profile, installed)
	}

	private fun currentDestinationSemanticContextIsCurrent(
		captured: ReaderNativeCurrentDestinationSemanticContext,
		origin: ReaderPresentationEventOrigin.SemanticCommand
	): Boolean {
		val current = captureCurrentDestinationSemanticContext(origin) ?: return false
		return current.engine.get() === captured.engine.get() && current.installedSnapshot === captured.installedSnapshot &&
			current.reporterEpoch == captured.reporterEpoch && current.version == captured.version &&
			current.binding == captured.binding && current.width == captured.width && current.height == captured.height &&
			current.visualPageIndex == captured.visualPageIndex && current.profileEpoch == captured.profileEpoch
	}

	fun createCurrentDestinationSemanticRequest(
		gateway: ReaderTransitionGateway,
		ingress: ReaderSemanticReceiptIngress
	): ReaderSemanticExecutableRequest {
		// Capture this attachment, never whichever successor happens to be current later.
		val epoch = gateway.captureActivatedSemanticEpoch()
		return request@{ origin, context, onReceipt ->
			if (epoch == null || !gateway.isCurrentActivatedSemanticEpoch(epoch)) return@request ReaderSemanticExecutableResult.Rejected
			val captured = captureCurrentDestinationSemanticContext(origin) ?: return@request ReaderSemanticExecutableResult.Rejected
			val source = captured.engine.get() as? ReaderEngineCurrentDestinationQuerySource ?: return@request ReaderSemanticExecutableResult.Rejected
			val isCurrent = { gateway.isCurrentActivatedSemanticEpoch(epoch) && currentDestinationSemanticContextIsCurrent(captured, origin) }
			source.queryCurrentDestination(origin, context, isCurrent) outcome@{ outcome ->
				when (outcome) {
					is ReaderCurrentDestinationQueryOutcome.Failed -> context.reportAsyncFailure(origin,
						if (outcome.reason == ReaderCurrentDestinationQueryFailure.SubmissionThrew) ReaderSemanticAsyncFailure.Threw
						else ReaderSemanticAsyncFailure.Rejected)
					is ReaderCurrentDestinationQueryOutcome.Resolved -> {
						val token = outcome.carrier as? ReaderEngineCurrentDestinationContinuation ?: run {
							context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Rejected); return@outcome
						}
						if (token.retired) return@outcome
						val event = outcome.event
						if (!isCurrent() || event.foliateSessionId != captured.binding.foliateSessionId ||
							event.destinationCommitIdentity != captured.binding.destinationCommitIdentity ||
							event.locator.pageIndex != captured.visualPageIndex) {
							context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Rejected); return@outcome
						}
						// Observe the exact captured destination without replaying relocation or renderer lineage.
						val confirmation = ReaderPresentationEvent.CurrentDestinationConfirmed(
							captured.binding, captured.version, origin
						)
						val receipt = try { ingress(confirmation, origin) } catch (_: Throwable) {
							if (!token.retired) context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Threw)
							return@outcome
						}
						// Cancellation during model ingress owns this same token, even after its callback cell was consumed.
						if (token.retired) return@outcome
						val semantic = receipt?.semanticReceipt
						if (!isCurrent() || receipt == null || receipt.origin !== origin || receipt.event !== confirmation ||
							receipt.preVersion != captured.version || receipt.effects.isNotEmpty() ||
							receipt.version.readerSessionGeneration != origin.transitionId.readerSessionGeneration ||
							receipt.version.publicationIdentity != captured.binding.publicationIdentity ||
							semantic?.transitionId != origin.transitionId || semantic.binding != captured.binding) {
							// Only the genuine exact confirmation can supply a semantic receipt.
							context.reportAsyncFailure(origin, ReaderSemanticAsyncFailure.Rejected); return@outcome
						}
						onReceipt(receipt)
					}
				}
			}
		}
	}

	private fun closeInitialDestinationConstruction() {
		val construction = initialDestinationConstruction
		initialDestinationConstruction = null // Fence the exact cell before external retirement.
		construction?.fail(ReaderSemanticAsyncFailure.Rejected)
	}
	fun closeCurrentDestinationQueries() {
		pendingInstalledMaterialAllocation?.let(::retireInstalledMaterialAllocation)
		closeInitialDestinationConstruction()
		(currentLiveWebView() as? ReaderEngineCurrentDestinationQuerySource)?.closeCurrentDestinationQueries()
	}

	fun currentPresentationBinding(): ReaderPresentationBinding? =
		currentPresentationBindingOrNull()

	fun currentLiveWebView(): WebView? = viewerContentContainer.findDescendantWebView()

	fun nextLiveEnginePresentedFrameSequence(binding: ReaderPresentationBinding): Long =
		liveEnginePresentedFrameSequenceAuthority.next(binding)

	fun applyPresentationFrameOwner(decision: ReaderPresentationDecision) {
		when (decision.frameOwner) {
			is paige.navic.reader.ReaderPresentationFrameOwner.LiveEngine -> {
				if (!pageTurnCanvasRequested && pageTurnCanvasEnabled) {
					applyActivePageTurnCanvasMode(false)
				}
				playLikeCurlController.surfaceView.animate().cancel()
				playLikeCurlController.surfaceView.alpha = 0f
				playLikeCurlController.inlineRasterShieldView.animate().cancel()
				playLikeCurlController.inlineRasterShieldView.alpha = 0f
				viewerContentContainer.bringToFront()
			}
			is paige.navic.reader.ReaderPresentationFrameOwner.NativePage -> {
				if (playLikeCurlController.ownsInlineRasterShieldPresentation) {
					playLikeCurlController.surfaceView.alpha = 0f
					playLikeCurlController.inlineRasterShieldView.alpha = 1f
					playLikeCurlController.inlineRasterShieldView.bringToFront()
				} else {
					playLikeCurlController.inlineRasterShieldView.alpha = 0f
					playLikeCurlController.surfaceView.alpha = 1f
					playLikeCurlController.surfaceView.bringToFront()
				}
			}
			paige.navic.reader.ReaderPresentationFrameOwner.Neutral -> {
				playLikeCurlController.surfaceView.animate().cancel()
				playLikeCurlController.surfaceView.alpha = 0f
				playLikeCurlController.inlineRasterShieldView.animate().cancel()
				playLikeCurlController.inlineRasterShieldView.alpha = 0f
			}
			else -> return
		}
		whispersyncCueMapView.bringToFront()
	}

	fun matchesAuthoritativePresentationVersion(
		version: ReaderPresentationReceiptVersion
	): Boolean = presentationBindingReporter.matchesAuthoritativePresentationVersion(version)

	fun setOnViewerAction(
		callback: (KomikkuNavigationRegion) -> ReaderPresentationEventReceipt?
	) {
		onAction = { action ->
			val epoch = presentationBindingReporter.captureEpoch()
			val receipt = callback(action)
			consumeReturnedReceipt(epoch, receipt)
		}
	}

	fun setOnPageTurnBoundary(
		callback: (ReaderPageTurnDirection) -> ReaderPresentationEventReceipt?
	) {
		onPageTurnBoundary = { direction ->
			val epoch = presentationBindingReporter.captureEpoch()
			val receipt = callback(direction)
			consumeReturnedReceipt(epoch, receipt)
		}
	}

	fun dispatchPresentationEvent(
		event: ReaderPresentationEvent
	): ReaderPresentationEventReceipt? = presentationReceiptDispatcher.dispatch(
		event = event,
		rendererLossCancellationIdentity = pendingRendererLossCancellationIdentity.takeIf {
			event is ReaderPresentationEvent.Lifecycle &&
				event.event == ReaderPresentationLifecycleEvent.RendererLost
		},
		onEvent = onPresentationEvent
	)

	private fun consumeReturnedReceipt(
		epoch: Long,
		receipt: ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? = presentationReceiptDispatcher.consumeReturned(epoch, receipt)

	fun setPresentationDecision(
		decision: ReaderPresentationDecision,
		state: ReaderPresentationState,
		version: ReaderPresentationReceiptVersion,
		presentationShellCoverVisible: Boolean,
		destinationCommitIdentity: ReaderDestinationCommitIdentity?,
		onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?,
		onAuthoritativeHostEffect: (ReaderPresentationHostEffect) -> Unit
	) {
		onPresentationEvent = onEvent
		onAuthoritativePresentationHostEffect = onAuthoritativeHostEffect
		presentationLifecycleDelivery.advanceComposeVersion(version)
		presentationReceiptDispatcher.synchronizeComposeModel(
			epoch = presentationBindingReporter.captureEpoch(),
			version = version,
			state = state,
			shellCoverVisible = presentationShellCoverVisible,
			decision = decision
		)
		composeDestinationCommitIdentity = destinationCommitIdentity
		// Compose carries the destination before its visual-location ingress. Recovery
		// and dispatched native initial-live work need that ingress to correlate the
		// source before reporting; readiness must still see the admitted predecessor.
		if (!playLikeCurlController.awaitingPresentationRecoverySnapshot &&
			!playLikeCurlController.awaitingInitialLiveNativeSource
		) {
			adoptComposeDestinationCommitIdentity()
		}
		reportPresentationIdentityIfAvailable()
		retryPresentationLifecycleDelivery()
	}

	private fun adoptComposeDestinationCommitIdentity() {
		if (presentationDestinationCommitIdentity != composeDestinationCommitIdentity) {
			presentationDestinationCommitIdentity = composeDestinationCommitIdentity
			presentationRelocationPending = presentationBindingReporter.lastReportedBinding != null
		}
	}

	fun releaseStalePresentation(
		effect: ReaderPresentationEffect.ReleaseStalePresentation
	): Boolean = playLikeCurlController.releaseStalePresentationDeck(effect.binding)

	fun preparePresentationEpoch(
		version: ReaderPresentationReceiptVersion,
		state: ReaderPresentationState,
		shellCoverVisible: Boolean,
		publicationChanged: Boolean,
		viewerReplacementPending: Boolean
	): ReaderPresentationEpochPreparation {
		if (!presentationBindingReporter.admitsComposeHostUpdate(version, publicationChanged)) {
			return ReaderPresentationEpochPreparation.Rejected
		}
		val expectedSession = presentationBindingReporter.expectedReaderSessionGeneration
		if (
			expectedSession == version.readerSessionGeneration &&
			!publicationChanged
		) {
			return ReaderPresentationEpochPreparation.Current
		}
		if (viewerReplacementPending) {
			presentationViewerReplacementFence.begin(rasterProfileEpoch)
		}
		presentationPublicationGeneration = Math.incrementExact(presentationPublicationGeneration)
		presentationPublicationOpenPending = true
		presentationRelocationPending = false
		presentationBindingReporter.reset(
			expectedReaderSessionGeneration = version.readerSessionGeneration,
			minimumComposeVersion = version,
			initialPresentationState = state,
			initialShellCoverVisible = shellCoverVisible
		)
		playLikeCurlController.onPresentationHostEpochChanged()
		presentationLifecycleDelivery.reset(
			version = version,
			observedWindowVisible = lastPresentationWindowVisible,
			initialLifecycle = state.lifecycle
		)
		deferredVisibilityRestorePreparation = null
		pendingRendererLossCancellationIdentity = null
		lastReportedPresentationFacts = null
		nativePagePresentationPublisher.update()
		return ReaderPresentationEpochPreparation.Replaced
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
			else -> {
				presentationLifecycleDelivery.observe(event)
				retryPresentationLifecycleDelivery()
			}
		}
	}

	private fun retryPresentationLifecycleDelivery(): ReaderPresentationEventReceipt? {
		val receipt = presentationLifecycleDelivery.retry(::dispatchPresentationEvent)
		if (!legacyLifecycleConsequencesOpen) return receipt
		captureDeferredVisibilityRestorePreparation(receipt)
		if (!legacyLifecycleConsequencesOpen) return receipt
		retryDeferredVisibilityRestorePreparation()
		if (!legacyLifecycleConsequencesOpen) return receipt
		if (!presentationLifecycleDelivery.hasPendingRendererLoss) {
			pendingRendererLossCancellationIdentity = null
		}
		return receipt
	}

	private fun captureDeferredVisibilityRestorePreparation(
		receipt: ReaderPresentationEventReceipt?
	) {
		val event = (receipt?.event as? ReaderPresentationEvent.Lifecycle)?.event ?: return
		if (event == ReaderPresentationLifecycleEvent.PublicationClosed) {
			deferredVisibilityRestorePreparation = null
			return
		}
		if (event != ReaderPresentationLifecycleEvent.VisibilityRestored) return
		val state = receipt.postState
		val request = (state.authority as? ReaderPresentationAuthority.BlockingPreparation)
			?.nativePresentationRequest
		val floor = request?.retryAfterPreparationGeneration
		deferredVisibilityRestorePreparation = if (
			request?.binding == state.binding && floor != null
		) {
			DeferredVisibilityRestorePreparation(request, floor)
		} else {
			null
		}
	}

	private fun retryDeferredVisibilityRestorePreparation() {
		if (!legacyLifecycleConsequencesOpen) return
		val obligation = deferredVisibilityRestorePreparation ?: return
		if (latestRasterPreparationState.preparationGeneration > obligation.preparationFloor) {
			deferredVisibilityRestorePreparation = null
			return
		}
		val decision = presentationDecision
		val request = (decision?.authority as? ReaderPresentationAuthority.BlockingPreparation)
			?.nativePresentationRequest
		if (
			decision?.lifecycle != ReaderPresentationLifecycleState.Foreground ||
			decision.targetBinding != obligation.request.binding ||
			request != obligation.request
		) {
			deferredVisibilityRestorePreparation = null
			return
		}
		if (visibilityRestorePreparationRetryInProgress) return
		visibilityRestorePreparationRetryInProgress = true
		try {
			retryCurrentPreparation(obligation.preparationFloor)
		} finally {
			visibilityRestorePreparationRetryInProgress = false
		}
		if (
			deferredVisibilityRestorePreparation == obligation &&
			latestRasterPreparationState.preparationGeneration > obligation.preparationFloor
		) {
			deferredVisibilityRestorePreparation = null
		}
	}

	private fun reportPresentationWindowVisibility(visible: Boolean) {
		val visibilityChanged = lastPresentationWindowVisible != visible
		lastPresentationWindowVisible = visible
		val event = readerPresentationLifecycleEventForWindowVisibility(visible)
		presentationLifecycleDelivery.observe(event)
		retryPresentationLifecycleDelivery()
		if (!legacyLifecycleConsequencesOpen || !visibilityChanged) return
		if (visible) {
			reportPresentationIdentityIfAvailable()
		} else {
			nativePagePresentationPublisher.update()
		}
	}

	private fun currentPresentationBindingOrNull(
		preparedDeck: ReaderPagePreparedActiveDeck? = preparedActiveDeck
	): ReaderPresentationBinding? =
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
				preparedDeck = preparedDeck,
				preparedDeckAdmitted = preparedDeck?.let(
					presentationViewerReplacementFence::admits
				) == true
			)
		)

	private fun reportPresentationIdentityIfAvailable(
		relocationAcknowledgement: ReaderPageTurnSettlementAck? = null
	) {
		if (!legacyLifecycleConsequencesOpen) return
		restoreMissingPreparedDeckAtReady(latestRasterPreparationState)
		if (!legacyLifecycleConsequencesOpen) return
		val binding = currentPresentationBindingOrNull()
		if (binding == null) {
			nativePagePresentationPublisher.update()
			retryPresentationLifecycleDelivery()
			return
		}
		if (!presentationBindingReporter.bindPublication(binding)) {
			nativePagePresentationPublisher.update()
			return
		}
		if (!presentationLifecycleDelivery.bindPublication(binding.publicationIdentity)) {
			nativePagePresentationPublisher.update()
			return
		}
		val bindingEvent = presentationBindingReporter.update(
			confirmedTargetBinding = presentationDecision?.targetBinding,
			currentBinding = binding,
			publicationOpenPending = presentationPublicationOpenPending,
			relocationPending = presentationRelocationPending,
			relocationAcknowledgement = relocationAcknowledgement,
			retryBindingReplacement = currentCoverRetryBindingReplacementOrNull(binding)
		)
		if (bindingEvent != null) {
			val receipt = dispatchPresentationEvent(bindingEvent)
			if (!legacyLifecycleConsequencesOpen) return
			if (
				receipt.authorizes(bindingEvent) &&
				receipt?.postState?.binding == binding
			) {
				if (bindingEvent is ReaderPresentationEvent.PublicationOpened) {
					presentationPublicationOpenPending = false
				}
				if (bindingEvent.hasBindingTransitionTarget()) {
					presentationRelocationPending = false
				}
				lastReportedPresentationFacts = null
			}
		}
		reportPresentationPreparationFacts(latestRasterPreparationState)
		if (!legacyLifecycleConsequencesOpen) return
		reportNativePagePresentationIfAvailable()
		if (!legacyLifecycleConsequencesOpen) return
		retryPresentationLifecycleDelivery()
	}

	private fun reportPresentationPreparationFacts(state: ReaderPagePreparationState) {
		val binding = currentPresentationBindingOrNull() ?: return
		if (presentationBindingReporter.lastReportedBinding != binding) return
		val facts = state.toPresentationFacts()
		val report = binding to facts
		if (lastReportedPresentationFacts == report) return
		val event = facts.failure?.let { reason ->
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
		if (dispatchPresentationEvent(event).authorizes(event)) {
			lastReportedPresentationFacts = report
		}
	}

	private fun reportNativePagePresentationIfAvailable() {
		nativePagePresentationPublisher.update()
	}

	private fun initialLiveNativeProofIsCurrent(
		epoch: Long,
		proof: ReaderNativePagePresentationProof
	): Boolean {
		if (nativePresentationDispatchDepth != 0 || epoch != presentationBindingReporter.captureEpoch()) return false
		val decision = presentationDecision ?: return false
		val candidate = currentNativePagePresentationCandidateOrNull() ?: return false
		return decision.lifecycle == ReaderPresentationLifecycleState.Foreground &&
			presentationBindingReporter.matchesAuthoritativePresentationDecision(decision) &&
			(decision.authority as? ReaderPresentationAuthority.SettledNativePage)?.frame?.proof == proof &&
			(decision.frameOwner as? ReaderPresentationFrameOwner.NativePage)?.proof == proof &&
			decision.targetBinding == proof.binding &&
			presentationBindingReporter.lastReportedBinding == proof.binding &&
			candidate.binding == proof.binding &&
			candidate.viewportWidth == proof.viewportWidth && candidate.viewportHeight == proof.viewportHeight &&
			decision.requiredTransition == ReaderRequiredTransition.None &&
			decision.pendingTransitionToken == null &&
			decision.layer == ReaderPresentationLayer.NativePage &&
			decision.inputPolicy is ReaderPresentationInputPolicy.NativePage
	}

	private fun currentNativePagePresentationCandidateOrNull(): ReaderNativePagePresentationCandidate? {
		// Failed common work is ineligible, not a new tokenless presentation. Returning
		// no candidate lets the publisher cancel its physical request and roll back.
		if (presentationDecision?.diagnosticPresentation is ReaderDiagnosticPresentation.Failure) return null
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
			shellCoverSelected = shellCoverVisible,
			handoffDirection = transition?.direction
		).currentCandidateOrNull()
	}

	fun applyPresentationDecision(
		decision: ReaderPresentationDecision,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity? = null
	) {
		presentationDecision = decision
		if (readInstalledPublication != null) {
			// Owning semantic recomposition is not an owner/input publication ACK.
			refreshInstalledWhispersyncConsumer()
			return // The common model is an observation, not a second installed owner writer.
		}
		currentCoverRetryBindingCorrelationOrNull()
		playLikeCurlController.synchronizePresentationDecision(decision)
		presentationInputPolicy = decision.inputPolicy
		cancelLegacyLivePointerStreamIfContextChanged()
		updateInputSettlementPolicies(rendererLossCancellationIdentity)
	}

	private fun setLocalPageSafetyPolicy(policy: ReaderPageOperationPolicy) {
		localPageSafetyPolicy = policy
		updateInputSettlementPolicies()
		playLikeCurlController.setPageOperationPolicy(policy)
	}

	private fun updateInputSettlementPolicies(
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity? = null
	) {
		if (readInstalledPublication != null) {
			pageInputSettlementHostController.updateInstalledLocalSafety(localPageSafetyPolicy,
				rendererLossCancellationIdentity)
			return
		}
		pageInputSettlementHostController.updateInputPolicies(
			presentationInputPolicy = presentationInputPolicy,
			localSafetyPolicy = localPageSafetyPolicy,
			nativeTapContinuationIdentity = presentationDecision?.let { decision ->
				readerNativeTapContinuationIdentity(decision, localPageSafetyPolicy)
			},
			rendererLossCancellationIdentity = rendererLossCancellationIdentity
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
		if (!legacyLifecycleConsequencesOpen) {
			// Data-only ingress. An observed ordinal/ACK cannot settle an installed
			// turn, select a deck or revive recovery/prewarm/legacy Curl publication.
			pageTurnVisualPageIndex = normalized
			pageTurnVisualLocationReason = reason
			pageTurnFoliateSessionId = currentFoliateSessionId
			pageTurnSettlementAck = acknowledgement
			return
		}
		if (playLikeCurlController.isPresentationRecoverySnapshotReason(reason)) {
			if (normalized == null || reason == null ||
				!playLikeCurlController.stagePresentationRecoveryObservation(
					normalized, reason, currentFoliateSessionId
				)
			) return
			adoptComposeDestinationCommitIdentity()
			pageTurnVisualPageIndex = normalized
			pageTurnVisualLocationReason = reason
			pageTurnSettlementAck = null
			presentationRelocationPending = presentationBindingReporter.lastReportedBinding != null
			// Publish the source destination through the normal receipt-fenced reporter
			// before allowing either worker to prepare a fresh generation.
			reportPresentationIdentityIfAvailable()
			val binding = currentPresentationBindingOrNull() ?: return
			if (presentationBindingReporter.lastReportedBinding != binding ||
				presentationDecision?.targetBinding != binding
			) return
			pageRasterPreparationController.synchronizeVisualPageIndex(normalized, reason)
			playLikeCurlController.synchronizeVisualPageIndex(normalized, reason, acknowledgement)
			return
		}
		if (normalized != null && playLikeCurlController.continuePresentationRecoveryFromExternalObservation(
			normalized, currentFoliateSessionId, acknowledgement
		) {
			synchronizeOrdinaryPageTurnVisualLocation(normalized, reason, currentFoliateSessionId,
				acknowledgement, requireRecoveryAdmission = true)
		}) return
		synchronizeOrdinaryPageTurnVisualLocation(normalized, reason, currentFoliateSessionId,
			acknowledgement, requireRecoveryAdmission = false)
	}

	private fun synchronizeOrdinaryPageTurnVisualLocation(
		normalized: Int?,
		reason: String?,
		currentFoliateSessionId: String,
		acknowledgement: ReaderPageTurnSettlementAck?,
		requireRecoveryAdmission: Boolean
	): Boolean {
		if (playLikeCurlController.awaitingInitialLiveNativeSource &&
			composeDestinationCommitIdentity != presentationDestinationCommitIdentity
		) {
			// Source correlation precedes both duplicate-input filtering and cleanup.
			adoptComposeDestinationCommitIdentity()
			playLikeCurlController.stageInitialLiveNativeSource(
				currentPresentationBindingOrNull(), normalized, currentFoliateSessionId, acknowledgement
			)
			reportPresentationIdentityIfAvailable()
		}
		if (
			!requireRecoveryAdmission &&
			pageTurnVisualPageIndex == normalized &&
			pageTurnVisualLocationReason == reason &&
			pageTurnFoliateSessionId == currentFoliateSessionId &&
			pageTurnSettlementAck == acknowledgement
		) {
			reportPresentationIdentityIfAvailable()
			return true
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
		// External input cleanup still clears the host's cached observation. Capture
		// only exact, already-owned material; never carry this across an async turn.
		val reconciliationDecision = presentationDecision
		val reconciliationBinding = reconciliationDecision?.targetBinding
		val reconciliationEpoch = presentationBindingReporter.captureEpoch()
		val reconciliationDeck = preparedActiveDeck?.takeIf { deck ->
			!sessionChanged && !requireRecoveryAdmission && acknowledgement == null &&
				origin == ReaderPageVisualLocationOrigin.External &&
				normalized == pageTurnVisualPageIndex &&
				reconciliationDecision != null && reconciliationBinding != null &&
				presentationBindingReporter.lastReportedBinding == reconciliationBinding &&
				currentPresentationBindingOrNull() == reconciliationBinding &&
				playLikeCurlController.canReconcilePreparedActiveDeck(
					deck, normalized, currentFoliateSessionId, reconciliationDecision
				)
		}
		if (!sessionChanged && origin == ReaderPageVisualLocationOrigin.External) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.ExternalRelocation
			)
		}
		if (origin != ReaderPageVisualLocationOrigin.StaleAcknowledgement) {
			adoptComposeDestinationCommitIdentity()
		}
		pageTurnVisualPageIndex = normalized
		pageTurnVisualLocationReason = reason
		pageTurnSettlementAck = acknowledgement
		presentationRelocationPending = presentationBindingReporter.lastReportedBinding != null
		playLikeCurlController.stageCurrentWebViewOrdinal(normalized, origin)
		reportPresentationIdentityIfAvailable(
			relocationAcknowledgement = readerPageVisualLocationRelocationAcknowledgement(
				origin,
				acknowledgement
			)
		)
		if (requireRecoveryAdmission) {
			val binding = currentPresentationBindingOrNull() ?: return false
			if (presentationBindingReporter.lastReportedBinding != binding ||
				presentationDecision?.targetBinding != binding
			) return false
		}
		playLikeCurlController.synchronizeVisualPageIndex(
			normalized,
			reason,
			acknowledgement
		)
		if (origin != ReaderPageVisualLocationOrigin.StaleAcknowledgement) {
			pageRasterPreparationController.synchronizeVisualPageIndex(normalized, reason)
		}
		if (reconciliationDeck != null && reconciliationDecision != null &&
			preparedActiveDeck == null &&
			presentationDecision == reconciliationDecision &&
			presentationBindingReporter.captureEpoch() == reconciliationEpoch &&
			presentationBindingReporter.lastReportedBinding == reconciliationBinding &&
			currentPresentationBindingOrNull(reconciliationDeck) == reconciliationBinding
		) {
			// The candidate above is comparison-only. The actual owner must revalidate
			// before its existing observer can restore the host record and report facts.
			playLikeCurlController.reconcilePreparedActiveDeck(
				reconciliationDeck, normalized, currentFoliateSessionId, reconciliationDecision
			)
		}
		commitStartupShellPresentationIfReady()
		return true
	}

	fun setShellCoverVisible(
		visible: Boolean,
		preserveNativePresentationProof: Boolean
	) {
		val visibilityChanged = shellCoverVisible != visible
		val preservePreparedHandoff =
			visibilityChanged && !visible && consumeStartupShellPreparedHandoff()
		if (visibilityChanged && visible) {
			dispatchPageHostLifecycleEvent(
				event = ReaderPageHostLifecycleEvent.ShellCoverShown,
				preserveDestinationDeck = preserveNativePresentationProof
			)
		}
		shellCoverVisible = visible
		cancelLegacyLivePointerStreamIfContextChanged()
		if (visibilityChanged && visible && !preserveNativePresentationProof) {
			removePageTurnPrewarmLayoutListener()
			playLikeCurlController.invalidate("shell-cover-visible")
			pageRasterPreparationController.invalidate("shell-cover-visible")
		} else if (
			visibilityChanged &&
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

	private fun currentCoverRetryBindingCorrelationOrNull(): CoverRetryBindingCorrelation? {
		val correlation = coverRetryBindingCorrelation ?: return null
		val decision = presentationDecision
		val pending = decision?.authority as? ReaderPresentationAuthority.BlockingPreparation
		val request = pending?.nativePresentationRequest
		val effect = correlation.effect
		val current = correlation.hostEpoch == presentationBindingReporter.captureEpoch() &&
			decision?.lifecycle == ReaderPresentationLifecycleState.Foreground &&
			decision.diagnosticPresentation == ReaderDiagnosticPresentation.Hidden &&
			pending?.retainedFrame is ReaderPresentationFrameOwner.ShellCover &&
			effect.token != null && request?.token == effect.token &&
			request.binding == effect.binding && decision.targetBinding == effect.binding &&
			effect.binding.preparationGeneration != null &&
			request.retryAfterPreparationGeneration == effect.binding.preparationGeneration &&
			correlation.preparationGeneration > effect.binding.preparationGeneration &&
			effect.binding.rasterGeneration != null && effect.binding.textureGeneration != null
		if (!current) coverRetryBindingCorrelation = null
		return correlation.takeIf { current }
	}

	private fun currentCoverRetryBindingReplacementOrNull(
		binding: ReaderPresentationBinding
	): ReaderPresentationEvent.BindingReplaced? {
		val correlation = currentCoverRetryBindingCorrelationOrNull() ?: return null
		val effect = correlation.effect
		if (binding != effect.binding.copy(
			preparationGeneration = correlation.preparationGeneration,
			profileGeneration = correlation.profileGeneration,
			rasterGeneration = null, textureGeneration = null
		)) return null
		return ReaderPresentationEvent.BindingReplaced(effect.binding, binding,
			ReaderPresentationRetryBindingAttribution(
				checkNotNull(effect.token), correlation.preparationGeneration, correlation.profileGeneration))
	}

	fun retryPreparation(effect: ReaderPresentationEffect.RetryPreparation): Boolean {
		val pending = presentationDecision?.authority as? ReaderPresentationAuthority.BlockingPreparation
		if (pending?.retainedFrame is ReaderPresentationFrameOwner.Curl &&
			pending.nativePresentationRequest?.token == effect.token &&
			pending.nativePresentationRequest?.binding == effect.binding
		) {
			return playLikeCurlController.requestPresentationRecoverySnapshot {
				val current = (presentationDecision?.authority as? ReaderPresentationAuthority.BlockingPreparation)
					?.nativePresentationRequest
				if (current?.token == effect.token) {
					retryCurrentPreparation(effect.binding.preparationGeneration)
				}
			}
		}
		val physicalBinding = currentPresentationBindingOrNull()
		if (physicalBinding != effect.binding) {
			val decision = presentationDecision
			val request = pending?.nativePresentationRequest
			// A suppressed null-deck observation leaves the complete common target
			// intact. Its exact current cover Retry must still reach fresh allocation.
			val currentCoverRetryWithoutDeck =
				decision?.lifecycle == ReaderPresentationLifecycleState.Foreground &&
					decision.diagnosticPresentation == ReaderDiagnosticPresentation.Hidden &&
					decision.targetBinding == effect.binding &&
					pending?.retainedFrame is ReaderPresentationFrameOwner.ShellCover &&
					effect.token != null && request?.token == effect.token &&
					request.binding == effect.binding &&
					effect.binding.preparationGeneration != null &&
					request.retryAfterPreparationGeneration == effect.binding.preparationGeneration &&
					effect.binding.rasterGeneration != null && effect.binding.textureGeneration != null &&
					physicalBinding != null && physicalBinding == effect.binding.copy(
						rasterGeneration = null, textureGeneration = null)
			if (!currentCoverRetryWithoutDeck) return true
		}
		return retryCurrentPreparation(effect.binding.preparationGeneration, effect)
	}

	private fun retryCurrentPreparation(
		expectedGeneration: Long?,
		retryEffect: ReaderPresentationEffect.RetryPreparation? = null
	): Boolean {
		val webView = viewerContentContainer.findDescendantWebView()
		if (passiveRasterPreparationAdapter?.isAvailable != true && webView != null) {
			closePassiveRasterPreparationAdapter()
			replacePassiveRasterPreparationAdapter(webView)
		}
		val preparationGeneration =
			pageRasterPreparationController.retryPreparation(expectedGeneration) ?: return false
		// Bind the successful allocation before renderer Retry can synchronously
		// resolve a profile or publish preparation/deck identity. Never retag a tail.
		coverRetryBindingCorrelation = retryEffect?.let {
			CoverRetryBindingCorrelation(it, presentationBindingReporter.captureEpoch(),
				preparationGeneration, it.binding.profileGeneration)
		}
		currentCoverRetryBindingCorrelationOrNull()
		playLikeCurlController.retryPreparation(preparationGeneration)
		if (rasterProfileEpoch == null) {
			requestPageTurnPrewarmWhenReady()
		}
		return true
	}

	private fun restoreMissingPreparedDeckAtReady(state: ReaderPagePreparationState) {
		if (state.phase != ReaderPagePreparationPhase.Ready || preparedActiveDeck != null) return
		val decision = presentationDecision ?: return
		val blocking = decision.authority as? ReaderPresentationAuthority.BlockingPreparation ?: return
		val cover = blocking.retainedFrame as? ReaderPresentationFrameOwner.ShellCover ?: return
		val request = blocking.nativePresentationRequest ?: return
		val binding = decision.targetBinding ?: return
		val epoch = presentationBindingReporter.captureEpoch()
		if (request.binding != binding || request.token != decision.pendingTransitionToken ||
			binding.rasterGeneration == null || binding.textureGeneration == null ||
			binding.preparationGeneration != state.preparationGeneration ||
			(request.retryAfterPreparationGeneration?.let { state.preparationGeneration <= it } == true)
		) return
		playLikeCurlController.publishMissingPreparedDeckAtReady(
			pageIndex = pageTurnVisualPageIndex,
			foliateSessionId = pageTurnFoliateSessionId ?: return,
			decision = decision,
			hostAdmits = { deck ->
				!task4ResourceTeardownStarted && pageTurnCanvasEnabled && isAttachedToWindow &&
					lastPresentationWindowVisible != false &&
					!presentationPublicationOpenPending && preparedActiveDeck == null &&
					latestRasterPreparationState == state && presentationDecision == decision &&
					decision.lifecycle == ReaderPresentationLifecycleState.Foreground &&
					decision.diagnosticPresentation !is ReaderDiagnosticPresentation.Failure &&
					decision.frameOwner == cover && decision.layer == ReaderPresentationLayer.ShellCover &&
					presentationBindingReporter.captureEpoch() == epoch &&
					presentationBindingReporter.matchesAuthoritativePresentationDecision(decision) &&
					presentationBindingReporter.lastReportedBinding == binding &&
					presentationViewerReplacementFence.admits(deck) &&
					currentPresentationBindingOrNull(deck) == binding
			}
		)
	}

	private fun onPreparedActiveDeckChanged(deck: ReaderPagePreparedActiveDeck?) {
		val previous = preparedActiveDeck
		val ownership = foregroundWebViewOwnership.snapshot()
		val epoch = presentationBindingReporter.captureEpoch()
		preparedActiveDeck = deck
		reportPresentationIdentityIfAvailable()
		// Reporting may synchronously replace the viewer or authority epoch.
		if (preparedActiveDeck !== deck || presentationBindingReporter.captureEpoch() != epoch) return
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
		if (!legacyLifecycleConsequencesOpen) return
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
		lateinit var originalAdapter: ReaderPassiveRasterPreparationAdapter
		val runtime = ReaderPassiveRasterWebViewHost(
			activity = activity,
			passiveSessionId = UUID.randomUUID().toString(),
			viewportGeometry = geometry,
			onRuntimeReady = {
				post {
					originalAdapter.onOriginalRuntimeReady()
					if (
						!passiveRasterRendererLossFence.isCurrent(runtimeIdentity) ||
						passiveRasterPreparationAdapter?.isAvailable != true
					) {
						return@post
					}
					requestPageTurnPrewarmWhenReady()
				}
			},
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
		originalAdapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = ReaderPageLivePassiveRasterManifestPort {
				viewerContentContainer.findDescendantWebView()
			},
			bundleSource = pageTurnBundleSource,
			initialCaptureEpoch = passiveRasterCaptureEpoch,
			onCancellationDrained = {
				post {
					if (
						!passiveRasterRendererLossFence.isCurrent(runtimeIdentity) ||
						passiveRasterPreparationAdapter?.isAvailable != true
					) {
						return@post
					}
					requestPageTurnPrewarmWhenReady()
				}
			}
		)
		passiveRasterPreparationAdapter = originalAdapter
		passiveRasterRendererLossFence.replace(runtimeIdentity)
		passiveRasterPreparationGeometry = geometry
		val hostResumed = observedHostLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
		// The external lifecycle getter may install the shared snapshot after
		// adapter assignment; the caller's later fence cannot undo a real pause.
		if (!legacyLifecycleConsequencesOpen) return
		if (!hostResumed) {
			passiveRasterPreparationAdapter?.pause()
		} else {
			val passiveAvailable = passiveRasterPreparationAdapter?.isAvailable == true
			if (!legacyLifecycleConsequencesOpen || !passiveAvailable) return
			onPassiveRasterPreparationAvailable()
		}
	}

	private fun ensureInstalledPassiveRasterAdapter(webView: WebView, work: ReaderActivatedRasterWork): Boolean {
		if (!work.isCurrent() || webView.width <= 0 || webView.height <= 0) return false
		val geometry = ReaderPassiveRasterGeometry(webView.width, webView.height, 0, 0, webView.width, webView.height)
		if (passiveRasterPreparationGeometry == geometry && passiveRasterPreparationAdapter?.isRetired == false) return true
		closePassiveRasterPreparationAdapter()
		val activity = context as? Activity ?: return false
		passiveRasterCaptureEpoch = if (passiveRasterCaptureEpoch == Long.MAX_VALUE) 0L else passiveRasterCaptureEpoch + 1L
		val runtimeIdentity = Any()
		lateinit var originalAdapter: ReaderPassiveRasterPreparationAdapter
		val runtime = ReaderPassiveRasterWebViewHost(activity = activity,
			passiveSessionId = UUID.randomUUID().toString(), viewportGeometry = geometry,
			onRuntimeReady = {
				// The genuine source edge goes to this ORIGINAL adapter even if Native
				// has since retired/replaced it. Its exact readiness custody decides
				// currentness and owes any executing consumer's physical return.
				post { originalAdapter.onOriginalRuntimeReady() }
			}, onRendererGone = {
				post {
					if (!passiveRasterRendererLossFence.isCurrent(runtimeIdentity)) return@post
					val invocation = pendingInstalledRaster
					if (invocation != null && pendingInstalledRaster === invocation) pendingInstalledRaster = null
					// Adapter close may synchronously forward the old terminal. Its admission
					// slot is already detached; only original release can return its resources.
					closePassiveRasterPreparationAdapter()
					if (invocation?.work?.isCurrent() == true) invocation.callbacks.onFailed(invocation.work.lease,
						ReaderTransitionFailureReason.PortRejected)
				}
			})
		val session = ReaderPassiveRasterPrototypeSession(runtime = runtime,
			releaseRaster = { bitmap: Bitmap -> bitmap.takeUnless { it.isRecycled }?.recycle() })
		originalAdapter = ReaderPassiveRasterPreparationAdapter(session = session,
			liveManifestPort = ReaderPageLivePassiveRasterManifestPort { viewerContentContainer.findDescendantWebView() },
			bundleSource = pageTurnBundleSource, initialCaptureEpoch = passiveRasterCaptureEpoch)
		passiveRasterPreparationAdapter = originalAdapter
		passiveRasterRendererLossFence.replace(runtimeIdentity)
		passiveRasterPreparationGeometry = geometry
		return work.isCurrent()
	}

	private fun closePassiveRasterPreparationAdapter() {
		passiveRasterRendererLossFence.clear()
		passiveRasterPreparationAdapter?.close()
		passiveRasterPreparationAdapter = null
		passiveRasterPreparationGeometry = null
	}

	private fun requestPageTurnPrewarmWhenReady() {
		if (!legacyLifecycleConsequencesOpen) return
		if (
			task4ResourceTeardownStarted ||
			!pageTurnCanvasEnabled ||
			!isAttachedToWindow
		) return
		if (!legacyLifecycleConsequencesOpen) return
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
			// An accepted listener may outlive installation. Removal is physical
			// cleanup, not permission to restart/replay legacy preparation.
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (
				task4ResourceTeardownStarted ||
				!pageTurnCanvasEnabled ||
				!isAttachedToWindow ||
				observedHostLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true
			) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			val webView = viewerContentContainer.findDescendantWebView()
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			val attached = webView?.isAttachedToWindow == true
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			pageRasterHostEventController.webViewAttachmentChanged(attached)
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (
				webView == null ||
				!attached ||
				width <= 0 ||
				height <= 0 ||
				webView.width <= 0 ||
				webView.height <= 0 ||
				isLayoutRequested ||
				webView.isLayoutRequested
			) return@OnPreDrawListener true
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			val profileEpoch = rasterProfileEpoch
			val signature = pageTurnPrewarmLayoutSignature(webView, profileEpoch ?: 0L)
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (profileEpoch != null) {
				pageRasterHostEventController.layoutSignatureMeasured(signature)
				if (!legacyLifecycleConsequencesOpen) {
					removePageTurnPrewarmLayoutListener()
					return@OnPreDrawListener true
				}
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
				if (!legacyLifecycleConsequencesOpen) {
					removePageTurnPrewarmLayoutListener()
					return@OnPreDrawListener true
				}
			}
			replacePassiveRasterPreparationAdapter(webView)
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			val passiveAvailable = passiveRasterPreparationAdapter?.isAvailable == true
			if (!legacyLifecycleConsequencesOpen || !passiveAvailable) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			onPassiveRasterPreparationAvailable()
			if (!legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (profileEpoch != rasterProfileEpoch) {
				removePageTurnPrewarmLayoutListener()
				requestPageTurnPrewarmWhenReady()
				return@OnPreDrawListener true
			}
			if (rasterProfileEpoch == null) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (!rasterPaginationReady) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			if (pageRasterPreparationController.prewarmAdjacent() || !legacyLifecycleConsequencesOpen) {
				removePageTurnPrewarmLayoutListener()
			}
			true
		}
		pageTurnPrewarmLayoutListener = listener
		val observer = viewTreeObserver
		if (!legacyLifecycleConsequencesOpen) {
			removePageTurnPrewarmLayoutListener()
			return
		}
		observer.addOnPreDrawListener(listener)
		if (!legacyLifecycleConsequencesOpen) {
			removePageTurnPrewarmLayoutListener()
			return
		}
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
	private var hostLifecycleRegistration: ReaderPresentationLifecycleDelivery.RegisteredRoot? = null

	private val hostLifecycleObserver = object : DefaultLifecycleObserver {
		override fun onCreate(owner: LifecycleOwner) {
			hostLifecycleRegistration?.callback { }
		}

		override fun onStart(owner: LifecycleOwner) {
			hostLifecycleRegistration?.callback { }
		}

		override fun onResume(owner: LifecycleOwner) {
			hostLifecycleRegistration?.callback { admission ->
				if (!legacyLifecycleConsequencesOpen) return@callback
				passiveRasterPreparationAdapter?.resume()
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				pageRasterHostEventController.lifecycleResumedChanged(true)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				playLikeCurlController.onHostResumedChanged(true)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				requestPageTurnPrewarmWhenReady()
			}
		}

		override fun onPause(owner: LifecycleOwner) {
			hostLifecycleRegistration?.callback { admission ->
				if (!legacyLifecycleConsequencesOpen) return@callback
				removePageTurnPrewarmLayoutListener()
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				passiveRasterPreparationAdapter?.pause()
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				pageRasterHostEventController.lifecycleResumedChanged(false)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				playLikeCurlController.onHostResumedChanged(false)
			}
		}

		override fun onStop(owner: LifecycleOwner) {
			hostLifecycleRegistration?.callback { admission ->
				if (!legacyLifecycleConsequencesOpen) return@callback
				removePageTurnPrewarmLayoutListener()
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				passiveRasterPreparationAdapter?.pause()
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				pageRasterHostEventController.lifecycleResumedChanged(false)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				playLikeCurlController.onHostResumedChanged(false)
			}
		}

		override fun onDestroy(owner: LifecycleOwner) {
			val registration = hostLifecycleRegistration ?: return
			registration.callback(onPhysicalReturn = {
				// Framework destruction is physically terminal even while legacy admission
				// is frozen. Remove before LifecycleRegistry drops its table, retaining
				// this callback/source return through any reentrant removal callback.
				presentationLifecycleDelivery.closeRegisteredRoots()
				registration.remove(permanent = true)
			}) { admission ->
				if (!legacyLifecycleConsequencesOpen) {
					beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.Destroyed)
					return@callback
				}
				closePassiveRasterPreparationAdapter()
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				pageRasterHostEventController.lifecycleResumedChanged(false)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				playLikeCurlController.onHostResumedChanged(false)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return@callback
				beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.Destroyed)
			}
		}
	}

	override fun onAttachedToWindow() {
		val sourceReturn = presentationLifecycleDelivery.beginRegisteredRootReturn()
		val admission = presentationLifecycleDelivery.capturePhysicalAdmission()
		try {
			super.onAttachedToWindow()
			if (!presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			val lifecycleOwner = findViewTreeLifecycleOwner()
			if (!presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			val lifecycle = lifecycleOwner?.lifecycle
			if (!presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			if (observedHostLifecycle !== lifecycle) {
				if (hostLifecycleRegistration?.remove() == false) return
				if (!presentationLifecycleDelivery.registeredRootAdmits(admission)) return
				observedHostLifecycle = lifecycle
				hostLifecycleRegistration = lifecycle?.let { exactLifecycle ->
					presentationLifecycleDelivery.registeredRoot(
						registerPhysical = { exactLifecycle.addObserver(hostLifecycleObserver) },
						unregisterPhysical = { exactLifecycle.removeObserver(hostLifecycleObserver) },
						isValid = { observedHostLifecycle === exactLifecycle &&
							finalHostLifecycleEvent == null && !task4ResourceTeardownStarted }
					)
				}
			}
			if (lifecycle != null) {
				if (hostLifecycleRegistration?.register() != true) return
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
				val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
				pageRasterHostEventController.lifecycleResumedChanged(resumed)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
				playLikeCurlController.onHostResumedChanged(resumed)
				if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			}
			// Physical registration above is required independently of legacy authority,
			// including the no-LifecycleOwner path. Every recipient/getter may install.
			if (!legacyLifecycleConsequencesOpen) return
			val webView = viewerContentContainer.findDescendantWebView()
			if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			val webViewAttached = webView?.isAttachedToWindow == true
			if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			pageRasterHostEventController.webViewAttachmentChanged(webViewAttached)
			if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			playLikeCurlController.onHostAttached()
			if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			reportPresentationIdentityIfAvailable()
			if (!legacyLifecycleConsequencesOpen || !presentationLifecycleDelivery.registeredRootAdmits(admission)) return
			requestPageTurnPrewarmWhenReady()
		} finally {
			if (sourceReturn != null) presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn)
		}
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
		ReaderPresentationInputPolicy.ShellCover,
		is ReaderPresentationInputPolicy.ClaimedCurl -> false
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
			dispatchLegacyLivePointerEvent(event)
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
		val down = legacyLivePointerDown
		legacyLivePointerDown = null
		physicalDispatchMode = ReaderPagePhysicalDispatchMode.Denied
		try {
			down?.let {
				val cancel = MotionEvent.obtain(it).apply {
					action = MotionEvent.ACTION_CANCEL
				}
				try {
					viewerContentContainer.dispatchTouchEvent(cancel)
				} finally {
					cancel.recycle()
				}
			}
		} finally {
			down?.recycle()
		}
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
			if (!admitInstalledPointerTurn(event, originalDown, route.gestureId)) {
				recycleRetainedContentDown()
				completeHostGesture(route.gestureId, ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable)
				return true
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
				"Reader shell cover swipe action=$action"
			)
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover command action=$action"
			)
		} else {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader native readable swipe action=$action"
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
			label
		)
	}

	private fun logReaderReadableDragPreview(deltaX: Float, deltaY: Float) {
		val magnitude = if (verticalPageDragPreview) abs(deltaY) else abs(deltaX)
		if (nativeDragPreviewDiagnosticLogged || magnitude <= touchSlopPx) return
		nativeDragPreviewDiagnosticLogged = true
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native drag preview"
		)
	}

	private fun dispatchPageHostLifecycleEvent(
		event: ReaderPageHostLifecycleEvent,
		preserveDestinationDeck: Boolean = false,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity? = null
	): List<Long> {
		if (!legacyLifecycleConsequencesOpen) return emptyList()
		val cancelledGestures = readerDispatchPageHostLifecycleEvent(
			event = event,
			preserveDestinationDeck = preserveDestinationDeck ||
				rendererLossCancellationIdentity != null,
			clearDestinationDeckPrewarm = ::clearDestinationDeckPrewarm,
			dispatchInputLifecycle = { lifecycleEvent ->
				if (!legacyLifecycleConsequencesOpen) emptyList() else
					pageInputSettlementHostController.onLifecycleEvent(
						event = lifecycleEvent,
						rendererLossCancellationIdentity = rendererLossCancellationIdentity
					)
			}
		)
		if (!legacyLifecycleConsequencesOpen) return cancelledGestures
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
		// Temporary original event witness: observe this real callback before lifecycle
		// consequences can reenter admission. Do not retain dimensions or change currentness.
		if (w != oldw || h != oldh) initialDestinationConstruction?.let { custody ->
			if (custody.initialGeometryScopeCaptured && custody.active && custody.initialGeometryFirstChange == null)
				custody.initialGeometryFirstChange = initialGeometryWitnessBoundary
		}
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
		if (!legacyLifecycleConsequencesOpen || !pageTurnCanvasEnabled) return
		if (visibility == VISIBLE) {
			val resumed =
				observedHostLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
			if (!legacyLifecycleConsequencesOpen) return
			if (resumed) passiveRasterPreparationAdapter?.resume()
			if (!legacyLifecycleConsequencesOpen) return
			pageRasterHostEventController.lifecycleResumedChanged(resumed)
			if (!legacyLifecycleConsequencesOpen) return
			playLikeCurlController.onHostResumedChanged(resumed)
			if (!legacyLifecycleConsequencesOpen) return
			requestPageTurnPrewarmWhenReady()
			return
		}
		passiveRasterPreparationAdapter?.pause()
		if (!legacyLifecycleConsequencesOpen) return
		pageRasterHostEventController.lifecycleResumedChanged(false)
		if (!legacyLifecycleConsequencesOpen) return
		playLikeCurlController.onHostResumedChanged(false)
		if (!legacyLifecycleConsequencesOpen) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.WindowHidden
		)
		if (!legacyLifecycleConsequencesOpen) return
		removePageTurnPrewarmLayoutListener()
		if (!legacyLifecycleConsequencesOpen) return
		playLikeCurlController.onHostWindowHidden()
		if (!legacyLifecycleConsequencesOpen) return
		pageRasterPreparationController.invalidate("window-hidden")
	}

	override fun onDetachedFromWindow() {
		val sourceReturn = presentationLifecycleDelivery.beginRegisteredRootReturn()
		try {
			reportPresentationLifecycleEvent(ReaderPresentationLifecycleEvent.HostDetached)
			if (legacyLifecycleConsequencesOpen) closePassiveRasterPreparationAdapter()
			if (legacyLifecycleConsequencesOpen) pageRasterHostEventController.webViewAttachmentChanged(false)
			if (legacyLifecycleConsequencesOpen) pageRasterHostEventController.lifecycleResumedChanged(false)
			if (legacyLifecycleConsequencesOpen) playLikeCurlController.onHostResumedChanged(false)
			beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.Detached)
			closePhysicalPointerDelivery()
			teardownTask4Resources()
			check(hostLifecycleRegistration?.remove(permanent = true) != false) { "Lifecycle registration removal failed" }
			observedHostLifecycle = null
			super.onDetachedFromWindow()
		} finally {
			if (sourceReturn != null) presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn)
		}
	}

	fun canAcceptNewPointer(): Boolean {
		if (readInstalledPublication == null) return playLikeCurlController.isAvailable
		val publication = readInstalledPublication?.invoke() ?: return false
		if (installedCommandCurrency?.invoke() != true) return false
		val policy = paige.navic.reader.readerTransitionPresentationInputPolicy(publication.owner,
			publication.binding, publication.lease)
		return paige.navic.reader.readerPageNewPointerDecision(policy, localPageSafetyPolicy) == ReaderPageNewPointerDecision.Accept
	}

	fun closeReader() {
		closeCurrentDestinationQueries()
		val sourceReturn = presentationLifecycleDelivery.beginRegisteredRootReturn()
		try {
			beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.ReaderClosed)
			closePhysicalPointerDelivery()
			teardownTask4Resources()
		} finally {
			if (sourceReturn != null) presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn)
		}
	}

	private fun beginFinalHostLifecycle(event: ReaderPageHostLifecycleEvent) {
		val sourceReturn = presentationLifecycleDelivery.beginRegisteredRootReturn()
		val exactCommandMaterial = currentFrameInstallation?.takeIf { it.commandCurrency != null }
		var failure: Throwable? = null
		fun captureFailure(next: Throwable) {
			val first = failure
			if (first == null) failure = next else if (first !== next) first.addSuppressed(next)
		}
		try {
			require(event in readerPageFinalHostLifecycleEvents) {
				"Non-final host event passed to final lifecycle gate: $event"
			}
			if (finalHostLifecycleEvent != null) {
				retryPresentationLifecycleDelivery()
				return
			}
			finalHostLifecycleEvent = event
			closeInitialDestinationConstruction()
			startupShellHandoff.close()
			presentationLifecycleDelivery.observe(
				ReaderPresentationLifecycleEvent.PublicationClosed
			)
			retryPresentationLifecycleDelivery()
			if (!legacyLifecycleConsequencesOpen) return
			val reason = event.cancellationReason()
			dispatchPageHostLifecycleEvent(event)
			if (!legacyLifecycleConsequencesOpen) return
			clearLegacyNativeTapState(reason)
		} catch (next: Throwable) {
			captureFailure(next)
		} finally {
			// Logical already-final/rejected/unknown delivery cannot skip either exact removal.
			try { foregroundWebViewRegistration?.registration?.remove(permanent = true) }
			catch (next: Throwable) { captureFailure(next) }
			try {
				exactCommandMaterial?.let { lease ->
					val result = framePhysicalOwnership.retire(lease)
					if (result.completed && currentFrameInstallation === lease) currentFrameInstallation = null
					result.failure?.let(::captureFailure)
				}
			} catch (next: Throwable) { captureFailure(next) }
			try { if (sourceReturn != null) presentationLifecycleDelivery.endPhysicalDelivery(sourceReturn) }
			catch (next: Throwable) { captureFailure(next) }
			failure?.let { throw it }
		}
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
				retryPresentationLifecycleDelivery()
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
