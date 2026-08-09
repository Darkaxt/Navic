package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import karacken.curl.DeckRejectionReason
import karacken.curl.DeckReleaseReason
import karacken.curl.GestureRejectionReason
import karacken.curl.PageChange
import karacken.curl.PageDeck
import karacken.curl.PageImage
import karacken.curl.PageSurfaceDeckSubmissionResult
import karacken.curl.PageSurfaceDisposalResult
import karacken.curl.PageSurfaceDisposalStage
import karacken.curl.PageSurfaceListener
import karacken.curl.PageSurfaceOwnershipResult
import karacken.curl.PageSurfaceView
import karacken.curl.ReadingDirection
import karacken.curl.RenderCapabilities
import karacken.curl.RenderFailure
import karacken.curl.RenderFailureReason
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPageMaximumProtectedRasterEntriesPerLease
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPageOperationPolicy
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRelocationDrain
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.util.core.Logger

private const val ReaderPlayLikeCurlFoliateControllerTag = "ReaderPlayLikeCurlFoliate"
private const val ReaderPageLiveHandoffCrossfadeMillis = 200L
private const val MAX_RASTER_ADAPTER_OWNERS = 2

internal fun readerRenderFailureOwnsCurrentPresentation(
	generationId: Long,
	activeGenerationId: Long?,
	pendingGenerationId: Long?,
	reason: RenderFailureReason,
	isRecoverable: Boolean
): Boolean =
	(reason == RenderFailureReason.CONTEXT && !isRecoverable) ||
		generationId == activeGenerationId ||
		generationId == pendingGenerationId

private data class FailedLivePresentationGeneration(
	val rasterGeneration: Long,
	val textureGeneration: Long,
	val gestureId: Long
) {
	constructor(request: ReaderPageRelocationRequest) : this(
		request.rasterGeneration,
		request.textureGeneration,
		request.gestureId
	)

	fun matches(request: ReaderPageRelocationRequest): Boolean =
		rasterGeneration == request.rasterGeneration &&
			textureGeneration == request.textureGeneration
}

internal fun readerPageLivePresentationInteractionState(
	hasFailedLivePresentation: Boolean,
	proposed: ReaderPageInteractionState
): ReaderPageInteractionState = if (hasFailedLivePresentation) {
	ReaderPageInteractionState.Failed
} else {
	proposed
}

internal fun readerPageLivePresentationAvailable(
	hasFailedLivePresentation: Boolean,
	otherwiseAvailable: Boolean
): Boolean = !hasFailedLivePresentation && otherwiseAvailable

internal fun readerTerminalContentFailureRecoveryStillCurrent(
	destroyed: Boolean,
	failedGenerationMatches: Boolean,
	currentOrdinal: Int,
	destinationOrdinal: Int,
	hasNewerSurfacePresentationOwner: Boolean
): Boolean =
	!destroyed &&
		failedGenerationMatches &&
		currentOrdinal == destinationOrdinal &&
		!hasNewerSurfacePresentationOwner

internal fun readerCancelledGestureCanReleaseTerminalContentFailure(
	failedGestureId: Long?,
	cancelledGestureId: Long,
	currentOrdinal: Int,
	settledOrdinal: Int,
	presentedSurfaceGestureId: Long?
): Boolean =
	failedGestureId != null &&
		cancelledGestureId > failedGestureId &&
		currentOrdinal == settledOrdinal &&
		presentedSurfaceGestureId == cancelledGestureId

internal class ReaderPageLivePresentationRecoveryRequest {
	var pending: Boolean = false
		private set

	fun request() {
		pending = true
	}

	fun shouldForcePreparation(phase: ReaderPagePreparationPhase): Boolean =
		pending && phase == ReaderPagePreparationPhase.Ready

	fun claimPreparation(): Boolean {
		if (!pending) return false
		pending = false
		return true
	}

	fun clear() {
		pending = false
	}
}

internal data class ReaderPlayLikeCurlControllerOwnershipMetrics(
	val rasterResidency: ReaderPlayLikeCurlRasterResidencyMetrics,
	val pendingVisualCallbacks: Int,
	val pendingVisualCallbackLimit: Int,
	val relocation: paige.navic.reader.ReaderPageRelocationOwnershipSnapshot
)

internal enum class ReaderDeckSubmissionRole {
	Active,
	Pending
}

internal fun readerRecoveredDeckCancellationRoleMatches(
	expectedRole: ReaderDeckSubmissionRole,
	currentRole: ReaderDeckSubmissionRole
): Boolean = expectedRole == currentRole ||
	(expectedRole == ReaderDeckSubmissionRole.Pending &&
		currentRole == ReaderDeckSubmissionRole.Active)

internal fun recoverRejectedReaderSettlement(
	sourceOrdinal: Int,
	promotedGeneration: Long,
	rendererEnabled: Boolean,
	restoreSourceOrdinal: (Int) -> Unit,
	invalidateRenderer: (String) -> Unit,
	requestPrewarm: () -> Unit
) {
	require(sourceOrdinal >= 0)
	require(promotedGeneration >= 0L)
	restoreSourceOrdinal(sourceOrdinal)
	invalidateRenderer("settlement-terminal-rejected:$promotedGeneration")
	if (rendererEnabled) requestPrewarm()
}

internal data class ReaderPlayLikeCurlRasterRepairRecipient(
	val fence: ReaderPlayLikeCurlRasterRepairFence,
	val attempt: Int = 0,
	val qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
)

internal class ReaderPlayLikeCurlRasterRepairRegistry {
	private data class Operation(
		val token: Long,
		val recipients: MutableList<ReaderPlayLikeCurlRasterRepairRecipient>
	)

	private val operations = mutableMapOf<
		Pair<ReaderPlayLikeCurlRasterProfile, Int>,
		Operation
	>()
	private var nextOperationToken = 0L

	fun register(
		profile: ReaderPlayLikeCurlRasterProfile,
		sourcePageIndex: Int,
		recipient: ReaderPlayLikeCurlRasterRepairRecipient
	): Long? {
		val key = profile to sourcePageIndex
		val existing = operations[key]
		if (existing != null) {
			if (recipient !in existing.recipients) existing.recipients += recipient
			return null
		}
		val token = Math.incrementExact(nextOperationToken)
		nextOperationToken = token
		operations[key] = Operation(token, mutableListOf(recipient))
		return token
	}

	fun complete(
		profile: ReaderPlayLikeCurlRasterProfile,
		sourcePageIndex: Int,
		operationToken: Long
	): List<ReaderPlayLikeCurlRasterRepairRecipient>? {
		val key = profile to sourcePageIndex
		val operation = operations[key]?.takeIf { it.token == operationToken }
			?: return null
		operations.remove(key)
		return operation.recipients.toList()
	}

	fun isEmpty(): Boolean = operations.isEmpty()

	fun clear() {
		operations.clear()
	}
}

internal data class ReaderPlayLikeCurlRasterRepairFence(
	val profile: ReaderPlayLikeCurlRasterProfile,
	val destinationOrdinal: Int,
	val committedTurnVersion: Long,
	val protectedWindowVersion: Long,
	val protectedWindow: List<Int>
) {
	fun matches(
		profile: ReaderPlayLikeCurlRasterProfile?,
		destinationOrdinal: Int,
		committedTurnVersion: Long,
		protectedWindowVersion: Long,
		protectedWindow: List<Int>
	): Boolean =
		this.profile == profile &&
			this.destinationOrdinal == destinationOrdinal &&
			this.committedTurnVersion == committedTurnVersion &&
			this.protectedWindowVersion == protectedWindowVersion &&
			this.protectedWindow == protectedWindow
}

internal fun readerPlayLikeCurlRepairTargetMatches(
	repairedCenterOrdinal: Int,
	expectedSourceCenter: Int
): Boolean = repairedCenterOrdinal == expectedSourceCenter

internal sealed interface ReaderPageGestureTerminalDetail {
	data class RendererRejected(
		val generationId: Long,
		val reason: GestureRejectionReason
	) : ReaderPageGestureTerminalDetail

	data class RendererCancelled(val generationId: Long) : ReaderPageGestureTerminalDetail

	data class SettlementCompleted(
		val pageChange: PageChange,
		val ordinal: Int
	) : ReaderPageGestureTerminalDetail

	data class SettlementCancelled(val generationId: Long) : ReaderPageGestureTerminalDetail

	data class TouchRejected(val actionMasked: Int) : ReaderPageGestureTerminalDetail

	data class TapTurnUnavailable(val pageChange: PageChange) : ReaderPageGestureTerminalDetail

	data class RelocationCapacityUnavailable(
		val occupied: Int,
		val capacity: Int
	) : ReaderPageGestureTerminalDetail

	data class RelocationReservationProtocolFailure(
		val gestureId: Long
	) : ReaderPageGestureTerminalDetail

	data class TouchProtocolFailure(val actionMasked: Int) : ReaderPageGestureTerminalDetail

	data class TapTurnProtocolFailure(val pageChange: PageChange) : ReaderPageGestureTerminalDetail

	data class RelocationGenerationOrSessionDrift(
		val gestureId: Long
	) : ReaderPageGestureTerminalDetail

	data object ControllerCancelled : ReaderPageGestureTerminalDetail

	data class RenderFailed(
		val generationId: Long,
		val reason: RenderFailureReason
	) : ReaderPageGestureTerminalDetail

	data object RecoveryFailed : ReaderPageGestureTerminalDetail
}

internal sealed interface ReaderPageTurnStartResult {
	data object Settling : ReaderPageTurnStartResult

	data class TerminalPublished(
		val outcome: ReaderPageGestureTerminalOutcome,
		val detail: ReaderPageGestureTerminalDetail
	) : ReaderPageTurnStartResult
}

internal sealed interface ReaderPageCurlDispatchResult {
	data object Accepted : ReaderPageCurlDispatchResult
	data object TerminalPublished : ReaderPageCurlDispatchResult
}

internal sealed interface ReaderPageTapDispatchResult {
	data object Settling : ReaderPageTapDispatchResult
	data object TerminalPublished : ReaderPageTapDispatchResult

	data class CompleteInHost(
		val outcome: ReaderPageGestureTerminalOutcome
	) : ReaderPageTapDispatchResult
}

internal sealed interface ReaderPageRelocationExactDispatchResult {
	data object Dispatched : ReaderPageRelocationExactDispatchResult

	data class Rejected(
		val reason: ReaderPageRelocationDiagnosticRejectionReason
	) : ReaderPageRelocationExactDispatchResult {
		init {
			require(reason != ReaderPageRelocationDiagnosticRejectionReason.None)
		}
	}
}

internal class ReaderPageRelocationLiveDispatchCoordinator(
	private val foregroundWebViewOwnership: ReaderForegroundWebViewOwnership,
	private val isDispatchCurrent: (ReaderPageRelocationRequest) -> Boolean,
	private val dispatchExact: (
		ReaderPageRelocationRequest,
		ReaderForegroundWebViewMutationGeneration
	) -> ReaderPageRelocationExactDispatchResult,
	private val onRejected: (
		ReaderPageRelocationRequest,
		ReaderPageRelocationDiagnosticRejectionReason
	) -> Unit
) {
	private data class Entry(
		val request: ReaderPageRelocationRequest,
		val claim: ReaderForegroundWebViewLiveClaim
	)

	private val claims = linkedMapOf<String, Entry>()
	private val mutationGenerations =
		linkedMapOf<String, ReaderForegroundWebViewMutationGeneration>()
	private val pendingReadiness = mutableSetOf<String>()

	fun transfer(
		request: ReaderPageRelocationRequest,
		claim: ReaderForegroundWebViewLiveClaim
	): Boolean {
		if (claim.gestureId != request.gestureId) return false
		val token = request.token.value
		if (token in claims || token in mutationGenerations || token in pendingReadiness) {
			return false
		}
		claims[token] = Entry(request, claim)
		return true
	}

	fun dispatch(request: ReaderPageRelocationRequest): Boolean {
		val token = request.token.value
		val entry = claims[token]
		if (entry?.request != request) {
			if (isDispatchCurrent(request)) {
				onRejected(
					request,
					ReaderPageRelocationDiagnosticRejectionReason.OwnershipUnavailable
				)
			}
			return false
		}
		if (
			token in pendingReadiness ||
			token in mutationGenerations ||
			!isDispatchCurrent(request)
		) {
			return false
		}
		pendingReadiness += token
		try {
			foregroundWebViewOwnership.whenLiveReady(entry.claim) { readiness ->
				onReadiness(request, entry.claim, readiness)
			}
		} catch (_: Throwable) {
			pendingReadiness.remove(token)
			fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
		}
		return true
	}

	private fun onReadiness(
		request: ReaderPageRelocationRequest,
		claim: ReaderForegroundWebViewLiveClaim,
		readiness: ReaderForegroundWebViewLiveReadiness
	) {
		val token = request.token.value
		if (!pendingReadiness.remove(token)) return
		if (claims[token] != Entry(request, claim) || !isDispatchCurrent(request)) return
		when (readiness) {
			ReaderForegroundWebViewLiveReadiness.Ready -> dispatchReady(request, claim)
			is ReaderForegroundWebViewLiveReadiness.Failed,
			ReaderForegroundWebViewLiveReadiness.Invalidated -> fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
		}
	}

	private fun dispatchReady(
		request: ReaderPageRelocationRequest,
		claim: ReaderForegroundWebViewLiveClaim
	) {
		val token = request.token.value
		if (claims[token] != Entry(request, claim) || !isDispatchCurrent(request)) return
		val generation = foregroundWebViewOwnership.beginLiveMutation(claim)
		if (generation == null) {
			fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
			return
		}
		if (
			claims[token] != Entry(request, claim) ||
			!isDispatchCurrent(request) ||
			token in mutationGenerations
		) {
			return
		}
		mutationGenerations[token] = generation
		if (!foregroundWebViewOwnership.isCurrent(claim, generation)) {
			fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
			return
		}
		val result = try {
			dispatchExact(request, generation)
		} catch (_: Throwable) {
			ReaderPageRelocationExactDispatchResult.Rejected(
				ReaderPageRelocationDiagnosticRejectionReason.JavascriptDispatchFailed
			)
		}
		if (result is ReaderPageRelocationExactDispatchResult.Rejected) {
			fail(request, result.reason)
		}
	}

	fun mutationGeneration(
		request: ReaderPageRelocationRequest
	): ReaderForegroundWebViewMutationGeneration? {
		val entry = claims[request.token.value]?.takeIf { it.request == request }
			?: return null
		val generation = mutationGenerations[request.token.value] ?: return null
		return generation.takeIf {
			foregroundWebViewOwnership.isCurrent(entry.claim, generation)
		}
	}

	fun isCurrent(
		request: ReaderPageRelocationRequest,
		generation: ReaderForegroundWebViewMutationGeneration
	): Boolean {
		val entry = claims[request.token.value]?.takeIf { it.request == request }
			?: return false
		return mutationGenerations[request.token.value] == generation &&
			foregroundWebViewOwnership.isCurrent(entry.claim, generation)
	}

	fun isCurrent(request: ReaderPageRelocationRequest): Boolean =
		mutationGeneration(request) != null

	fun replace(
		original: ReaderPageRelocationRequest,
		replacement: ReaderPageRelocationRequest
	): Boolean {
		if (
			original.token == replacement.token ||
			original.gestureId != replacement.gestureId ||
			original.sourceOrdinal != replacement.sourceOrdinal ||
			original.destinationOrdinal != replacement.destinationOrdinal ||
			original.logicalDirection != replacement.logicalDirection ||
			original.foliateSessionId != replacement.foliateSessionId ||
			!isCurrent(original)
		) {
			return false
		}
		val originalToken = original.token.value
		val replacementToken = replacement.token.value
		if (
			replacementToken in claims ||
			replacementToken in mutationGenerations ||
			replacementToken in pendingReadiness
		) {
			return false
		}
		val entry = claims.remove(originalToken)?.takeIf { it.request == original }
			?: return false
		mutationGenerations.remove(originalToken)
		pendingReadiness.remove(originalToken)
		claims[replacementToken] = Entry(replacement, entry.claim)
		return true
	}

	fun complete(request: ReaderPageRelocationRequest): Boolean {
		val entry = remove(request) ?: return false
		foregroundWebViewOwnership.releaseLive(entry.claim)
		return true
	}

	fun fail(
		request: ReaderPageRelocationRequest,
		reason: ReaderPageRelocationDiagnosticRejectionReason
	): Boolean {
		require(reason != ReaderPageRelocationDiagnosticRejectionReason.None)
		val entry = remove(request) ?: return false
		foregroundWebViewOwnership.releaseLive(entry.claim)
		if (isDispatchCurrent(request)) onRejected(request, reason)
		return true
	}

	private fun remove(request: ReaderPageRelocationRequest): Entry? {
		val token = request.token.value
		val entry = claims[token]?.takeIf { it.request == request } ?: return null
		claims.remove(token)
		mutationGenerations.remove(token)
		pendingReadiness.remove(token)
		return entry
	}

	fun releaseAll() {
		val ownedClaims = claims.values.map { it.claim }.distinct()
		claims.clear()
		mutationGenerations.clear()
		pendingReadiness.clear()
		ownedClaims.forEach(foregroundWebViewOwnership::releaseLive)
	}
}

/**
 * Production bridge between Foliate's passive raster cache and the imported PlayLikeCurl surface.
 * Foliate remains the pagination authority; this controller owns only immutable raster leases,
 * deformation, and one exact visual-page settlement.
 */
internal class ReaderPlayLikeCurlFoliateController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val foregroundWebViewOwnership: ReaderForegroundWebViewOwnership =
		ReaderForegroundWebViewOwnership(),
	private val bundleSource: ReaderPageTurnBundleSource,
	private val diagnostics: ReaderPageRuntimeDiagnostics? = null,
	private val qaFaultRegistry: ReaderPageQaFaultRegistry? = null,
	private val onRequestPrewarm: () -> Unit,
	private val onAttachRasterRepairQaFault: (
		Int,
		ReaderPageQaFaultCorrelation
	) -> Unit = { _, _ -> },
	private val onRequestRasterRepair:
		(Int, (ReaderPageRasterRepairResult) -> Unit) -> Unit,
	private val onGestureTerminal: (
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	) -> Boolean,
	private val onBoundaryTurn: (ReaderPageTurnDirection) -> Unit = {},
	private val onRasterProfileEpochChanged: (Long?) -> Unit = {},
	private val onProtectedRasterSourcePageIndicesChanged: (Set<Int>) -> Unit = {},
	private val onPreparedActiveDeckChanged: (ReaderPagePreparedActiveDeck?) -> Unit = {},
	private val onPaginationReadinessChanged: (ReaderPagePaginationReadiness) -> Unit = {},
	private val onProfileBootstrapFailed: () -> Unit = {},
	private val onReadinessStateChange: (ReaderPageRendererReadinessState) -> Unit = {},
	private val onUnsafeLifecycleEvent: (ReaderPageHostLifecycleEvent) -> Unit = {},
	private val onViewerContentInputSuppressed: () -> Unit = {},
	private val hasStaticRasterShieldOwnership: () -> Boolean = { true },
	private val onOwnershipMutated: () -> Unit = {},
	private val onOwnershipAvailabilityEdge: () -> Unit = {},
	private val onOwnershipDiagnosticRequested: (ReaderPageOwnershipPhase) -> Unit = {}
) : ReaderPageTapTurnPort,
	ReaderPageDeckRecoveryHost,
	ReaderPageRendererOwnershipHost {
	private class PreparedPages(
		val profile: ReaderPlayLikeCurlRasterProfile,
		val deck: ReaderPlayLikeCurlRasterDeck<ReaderPlayLikeCurlRasterImage>,
		val centerOrdinal: Int
	) {
		val generations = mutableSetOf<Long>()
		var obsolete = false
	}

	private class PendingDecodedWorkingSetPrefetch(
		var gestureId: Long?,
		val sourceOrdinal: Int,
		val targetOrdinal: Int,
		val foliateSessionId: String,
		val profile: ReaderPlayLikeCurlRasterProfile,
		val expectedRequestGeneration: Long,
		val expectedCommittedTurnVersion: Long,
		val sourceProtectedWindowVersion: Long,
		val sourceProtectedWindow: List<Int>,
		val pageIndices: List<Int>
	) {
		var publicationAllowed = true
		var committed = false
		var transferredToRefill = false
		lateinit var publicationFence: ReaderPlayLikeCurlRasterPublicationFence
		lateinit var preparation:
			Deferred<ReaderPlayLikeCurlRasterDeck<ReaderPlayLikeCurlRasterImage>?>
	}

	private data class BuiltRecoveredDeck(
		val pages: PreparedPages,
		val ordinal: Int,
		val deck: PageDeck<Bitmap>
	)

	private data class RetainedInlineHandoffSnapshot(
		val request: ReaderPageRelocationRequest,
		val snapshot: ReaderPageSlideSnapshot
	)

	private val inlineRasterShield = ReaderPageInlineRasterShield(
		host = host,
		onOwnershipMutated = onOwnershipMutated,
		onPresentationOwnershipStarted = onViewerContentInputSuppressed
	)
	val inlineRasterShieldView: View
		get() = inlineRasterShield.view

	val ownsInlineRasterShieldPresentation: Boolean
		get() = inlineRasterShield.ownsPresentation()

	val shouldSuppressViewerContentInput: Boolean
		get() =
			ownsInlineRasterShieldPresentation ||
				retainsRejectedSurfaceInputShield ||
				failedLivePresentationGeneration != null

	val surfaceView = PageSurfaceView(host.context).apply {
		holder.setFormat(PixelFormat.TRANSLUCENT)
		setZOrderOnTop(true)
		setVisible(true)
		alpha = 0f
		visibility = View.VISIBLE
	}

	private val rasterResidencyBudget =
		ReaderPlayLikeCurlRasterResidencyBudget(
			residentEntryLimit =
				surfaceView.deckLeaseLimit *
					ReaderPageMaximumProtectedRasterEntriesPerLease,
			onCapacityAvailable = ::signalRasterCapacityAvailable
		)
	private val rasterJob = SupervisorJob()
	private val rasterScope = CoroutineScope(rasterJob + Dispatchers.Default)
	private val teardownJob = SupervisorJob()
	private val teardownScope = CoroutineScope(
		teardownJob + Dispatchers.Main.immediate
	)
	private val mainTerminalExecutor = ReaderMainTerminalActionExecutor(
		actionLimit = surfaceView.mainTerminalActionLimit,
		scope = teardownScope,
		onActionFailure = {
			if (!destroyed) {
				updateReadiness(
					interaction = ReaderPageInteractionState.Failed,
					reason = "main-terminal-action-failed"
				)
			}
		}
	)
	private val generationOwners = mutableMapOf<Long, PreparedPages>()
	private val generationRoles = mutableMapOf<Long, ReaderDeckSubmissionRole>()
	private val preparedDeckGenerations = mutableSetOf<Long>()
	private val deckDiagnosticTracker = diagnostics?.let(::ReaderPageDeckDiagnosticTracker)
	private val repairQaFaultCorrelations =
		mutableMapOf<Long, ReaderPageQaFaultCorrelation>()
	private val recoveredDeckGenerations = mutableSetOf<Long>()
	private val preparedPageSets = mutableSetOf<PreparedPages>()
	private val recoveredBuildOperations = mutableMapOf<
		Long,
		ReaderPageRecoveredDeckBuildOperation<
			ReaderPlayLikeCurlRasterDeck<ReaderPlayLikeCurlRasterImage>
		>
	>()
	private val builtRecoveredDecks = mutableMapOf<Long, BuiltRecoveredDeck>()
	private var rasterAdapter: ReaderPlayLikeCurlRasterAdapter<ReaderPlayLikeCurlRasterImage>? = null
	private val rasterAdapterOwners =
		ReaderPlayLikeCurlAdapterOwnerPool<
			ReaderPlayLikeCurlRasterAdapter<ReaderPlayLikeCurlRasterImage>
		>(ownerLimit = MAX_RASTER_ADAPTER_OWNERS)
	private val mainHandler = Handler(Looper.getMainLooper())
	private val relocationDispatchTimeout = ReaderPageRelocationDispatchTimeout(
		scheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean =
				mainHandler.postDelayed(action, delayMillis)

			override fun removeCallbacks(action: Runnable) {
				mainHandler.removeCallbacks(action)
			}
		},
		onTimeout = { request ->
			relocationLiveDispatchCoordinator.fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.AcknowledgementTimeout
			)
		}
	)
	private val rasterCapacityRefreshPosted = AtomicBoolean(false)
	private val recoveredDeckSubmissionRetryPosted = AtomicBoolean(false)
	private var rasterRetirementFailure: Throwable? = null
	private var disposedRasterResidencyMetrics: ReaderPlayLikeCurlRasterResidencyMetrics? = null
	private var ownershipCapacityListener: (() -> Unit)? = null
	private var ownershipCapacityRunnable: Runnable? = null
	private var foliateRasterLoader: ReaderPlayLikeCurlFoliateRasterLoader? = null
	private var activePages: PreparedPages? = null
	private var requestedProfile: ReaderPlayLikeCurlRasterProfile? = null
	private var publishedRasterProfile: ReaderPlayLikeCurlRasterProfile? = null
	private var publishedRasterProfileEpoch: Long? = null
	private var nextRasterProfileEpoch = 1L
	private var publishedPaginationReadiness = ReaderPagePaginationReadiness.Loading
	private var capabilitiesAvailable = false
	private var enabled = false
	private var attached = false
	private var destroyed = false
	private var readinessState = ReaderPageRendererReadinessState()
	private var pageOperationPolicy = readerPageOperationPolicy(
		ReaderPageReadinessState(
			textureDeck = readinessState.textureDeck,
			interaction = readinessState.interaction
		)
	)
	private var hasPreparedDeckBefore = false
	private var bitmapQuality = ReaderPageBitmapQuality.Balanced
	private var snapshotKey = Int.MIN_VALUE
	private var currentOrdinal = 0
	private var currentWebViewOrdinal: Int? = null
	private var authoritativeLocationReady = false
	private var currentFoliateSessionId: String? = null
	private var failedLivePresentationGeneration: FailedLivePresentationGeneration? = null
	private var retainsRejectedSurfaceInputShield = false
	private var retainedInlineHandoffSnapshot: RetainedInlineHandoffSnapshot? = null
	private val livePresentationRecoveryRequest =
		ReaderPageLivePresentationRecoveryRequest()
	private var foliateSessionRelocationPending = false
	private var hostResumed = false
	private var preparationPhase = ReaderPagePreparationPhase.Idle
	private var requestGeneration = 0L
	private var decodedRefillGeneration = 0L
	private var decodedRefillCenterOrdinal: Int? = null
	private var deferredDecodedRefillCenterOrdinal: Int? = null
	private var decodedWorkingSetPrefetch: PendingDecodedWorkingSetPrefetch? = null
	private var committedTurnVersion = 0L
	private var protectedWindowVersion = 0L
	private var currentProtectedWindow = emptyList<Int>()
	private val rasterRepairRequests = ReaderPlayLikeCurlRasterRepairRegistry()
	private val relocationQueue = ReaderPageRelocationQueue(
		onOwnershipMutated = onOwnershipMutated
	)
	private val relocationDiagnosticStarts = mutableMapOf<String, Long>()
	private val relocationQaFaultCorrelations =
		ReaderPageRelocationQaFaultCorrelationStore()
	private val handoffDiagnosticStarts = mutableMapOf<Long, Long>()
	private val handoffDiagnosticTargets = mutableMapOf<Long, Int>()
	private val staleHandoffDiagnosticStarts = mutableMapOf<Long, Long>()
	private val relocationGestureCoordinator =
		ReaderPageRelocationGestureCoordinator(
			queue = relocationQueue,
			foregroundWebViewOwnership = foregroundWebViewOwnership,
			onQueued = { request ->
				val startedAt = diagnostics?.now() ?: 0L
				relocationDiagnosticStarts[request.token.value] = startedAt
				emitRelocationDiagnostic(
					request,
					ReaderPageRelocationDiagnosticState.Queued
				)
			},
			onRejected = { request ->
				emitRelocationDiagnostic(
					request,
					ReaderPageRelocationDiagnosticState.Rejected,
					terminal = true,
					rejectionReason =
						ReaderPageRelocationDiagnosticRejectionReason.CommitPublicationFailed
				)
			}
		)
	private val relocationLiveDispatchCoordinator =
		ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = foregroundWebViewOwnership,
			isDispatchCurrent = ::relocationDispatchIsCurrent,
			dispatchExact = ::dispatchExactVisualPage,
			onRejected = ::rejectDispatchedRelocation
		)
	private val relocationVisualHandoffCoordinator:
		ReaderPageRelocationVisualHandoffCoordinator =
		ReaderPageRelocationVisualHandoffCoordinator(
			queue = relocationQueue,
			host = ReaderWebViewVisualHandoffHostAdapter(
				webViewProvider = webViewProvider,
				qaFaultRegistry = qaFaultRegistry,
				onQaFaultApplied = { token, handoffAttemptId, correlation ->
					val attached = relocationVisualHandoffCoordinator.attachQaFault(
						relocationToken = token,
						handoffAttemptId = handoffAttemptId,
						correlation = correlation
					)
					if (attached) relocationQaFaultCorrelations[token] = correlation
					attached
				}
			),
			currentState = ::relocationVisualState,
			dispatch = ::dispatchRelocation,
			publishRecovery = ::publishRelocationVisualRecovery,
			finalizePresentation = ::finalizeHandoffPresentation,
			validateContent = ::validateLivePresentation,
			canRecover = { qaFaultRegistry?.isClosed() != true },
			onOwnershipMutated = onOwnershipMutated,
			attemptEventSink =
				ReaderWebViewVisualHandoffAttemptEventSink(::onHandoffAttemptEvent),
			onAwaiting = { request ->
				emitRelocationDiagnostic(
					request,
					ReaderPageRelocationDiagnosticState.AwaitingVisualHandoff
				)
			},
			onCompleted = { request ->
				completeRelocationVisualHandoff(request)
			},
			onRejectedContentReleased = ::releaseTerminalContentFailure,
			onReplaced = ::replaceRelocationDiagnosticIdentity
		)
	private var nextDeckGeneration = 1L
	private var activeGestureId: Long? = null
	private val settlementMutationFence = ReaderPageSettlementMutationFence()
	private val hostOwnedTerminalGestureIds = mutableSetOf<Long>()
	private var presentedFrameRequestId: Long? = null
	private var presentedFrameGestureId: Long? = null
	private var presentedSurfaceGestureId: Long? = null
	private var tapTurnGestureId: Long? = null
	private var tapTurnTerminalSink: ((
		ReaderPageGestureTerminalOutcome,
		ReaderPageGestureTerminalDetail
	) -> Boolean)? = null
	private var activeDeckGenerationId: Long? = null
	private var pendingDeckGenerationId: Long? = null
	private var pendingDeckOrdinal: Int? = null
	private var lastActivationTrace: String? = null
	private val persistentRefillCoordinator = ReaderPagePersistentRefillCoordinator(
		protectedWindowForCenter = { centerOrdinal ->
			requestedProfile?.preparedPageIndices(centerOrdinal).orEmpty()
		},
		publishProtectedWindow = ::publishProtectedWindow,
		isDecoded = ::isLogicalRasterDecoded,
		hydratePersistent = { logicalOrdinal, fence, isStillCurrent ->
			foliateRasterLoader?.hydratePersistent(logicalOrdinal) {
				isStillCurrent(fence)
			} == true
		},
		requestRepair = ::requestLogicalRasterRepair
	)
	private val submissionCallbackFence = ReaderPageDeckSubmissionCallbackFence()
	private val deckRecoveryCoordinator = ReaderPageDeckRecoveryCoordinator(
		host = this,
		onStateChanged = ::onDeckRecoveryStateChanged,
		onRepairCancelled = { operation ->
			diagnostics?.repair(
				operation,
				ReaderPageRepairDiagnosticState.Cancelled,
				qaFaultCorrelation = qaFaultCorrelationForRepair(operation)
			)
			repairQaFaultCorrelations.remove(operation.attempt)
		},
		onStateObserverFailure = { failure ->
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"Deck recovery observer failed " +
					"failureClass=${failure::class.simpleName ?: "unknown"}"
			)
		}
	)

	init {
		surfaceView.registerMainTerminalExecutor(
			mainTerminalExecutor::execute
		)
		surfaceView.setPageSurfaceListener(object : PageSurfaceListener {
			override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) {
				capabilitiesAvailable = true
				onOwnershipAvailabilityEdge()
				logActivationState(
					event = "capabilities-available",
					detail = "maxTextureSize=${capabilities.maxTextureSize}"
				)
				refreshPreparedDeck()
			}

			override fun onDeckPrepared(generationId: Long) {
				val role = generationRoles[generationId] ?: return
				preparedDeckGenerations += generationId
				if (generationId == activeDeckGenerationId) {
					hasPreparedDeckBefore = true
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = preparedInteractionState(),
						reason = "deck-prepared:$generationId"
					)
					publishPreparedActiveDeck()
				} else if (
					generationId == pendingDeckGenerationId &&
					role == ReaderDeckSubmissionRole.Pending
				) {
					updateReadiness(
						pendingTextureDeck = ReaderTextureDeckState.Ready,
						reason = "pending-deck-prepared:$generationId"
					)
				}
				deckRecoveryCoordinator.onDeckPrepared(generationId)
				deckDiagnosticTracker?.prepared(
					generation = generationId,
					active = activeDeckGenerationId,
					pending = pendingDeckGenerationId
				)
				retryRelocationVisualHandoffForPreparedDeck(generationId)
				onOwnershipDiagnosticRequested(
					ReaderPageOwnershipPhase.PeakPreparation
				)
				logActivationState("deck-prepared", "generation=$generationId")
			}

			override fun onDeckRejected(generationId: Long, reason: DeckRejectionReason) {
				if (submissionCallbackFence.onDeckRejected(generationId, reason)) return
				val activeRejected = generationId == activeDeckGenerationId
				val pendingRejected = generationId == pendingDeckGenerationId
				releaseGeneration(generationId)
				val recoveryRejected = deckRecoveryCoordinator.onDeckRejected(generationId, reason)
				if (!recoveryRejected) {
					when {
						activeRejected -> updateReadiness(
							textureDeck = ReaderTextureDeckState.Failed,
							interaction = ReaderPageInteractionState.Failed,
							reason = "deck-rejected:$generationId:$reason"
						)
						pendingRejected -> updateReadiness(
							pendingTextureDeck = ReaderTextureDeckState.Failed,
							reason = "pending-deck-rejected:$generationId:$reason"
						)
					}
				}
				Logger.w(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl deck rejected generation=$generationId reason=$reason"
				)
			}

			override fun onDeckSubmissionCapacityAvailable() {
				deckRecoveryCoordinator.onDeckSubmissionCapacityAvailable()
			}

			override fun onDeckReleased(generationId: Long, reason: DeckReleaseReason) {
				releaseGeneration(generationId)
				deckRecoveryCoordinator.onDeckReleased(generationId)
			}

			override fun onGestureRejected(
				gestureId: Long,
				generationId: Long,
				reason: GestureRejectionReason,
				pageChange: PageChange
			) {
				val boundaryTurn = if (reason == GestureRejectionReason.BOUNDARY) {
					when (pageChange) {
						PageChange.PREVIOUS -> ReaderPageTurnDirection.Previous
						PageChange.NEXT -> ReaderPageTurnDirection.Next
						PageChange.NONE -> null
					}
				} else {
					null
				}
				val outcome = when (reason) {
					GestureRejectionReason.BOUNDARY ->
						ReaderPageGestureTerminalOutcome.RejectedBoundary
					GestureRejectionReason.SETTLEMENT_RUNNING ->
						ReaderPageGestureTerminalOutcome.RejectedSettling
					else ->
						ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
				}
				if (!finishGesture(
						gestureId,
						outcome,
						ReaderPageGestureTerminalDetail.RendererRejected(
							generationId,
							reason
						)
					)
				) {
					return
				}
				hideSurfaceAfterGesture(gestureId)
				boundaryTurn?.let(onBoundaryTurn)
			}

			override fun onGestureCancelled(gestureId: Long, generationId: Long) {
				if (!finishGesture(
						gestureId,
						ReaderPageGestureTerminalOutcome.CancelledByUser,
						ReaderPageGestureTerminalDetail.RendererCancelled(generationId)
					)
				) {
					return
				}
				hideSurfaceAfterGesture(gestureId)
			}

			override fun onSettlementStarted(
				gestureId: Long,
				generationId: Long,
				sourceLogicalPageId: String,
				targetLogicalPageId: String,
				pageChange: PageChange
			) {
				check(activeGestureId == gestureId) {
					"Settlement source does not own the active gesture"
				}
				settlementMutationFence.onSettlementStarted(
					gestureId = gestureId,
					sourceGenerationId = generationId
				)
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Settling,
					interaction = ReaderPageInteractionState.Settling,
					reason = "settlement-started:$gestureId"
				)
				val pages = activePages
				val targetOrdinal = pages?.let { prepared ->
					readerPlayLikeCurlSettlementTargetOrdinal(
						orientation = prepared.profile.orientation,
						currentOrdinal = currentOrdinal,
						pageCount = prepared.profile.pageCount,
						pageChange = pageChange,
						readerDirection = prepared.profile.readerDirection,
						spreadAnchorParity = prepared.profile.spreadAnchorParity
					)
				}
				if (pages != null && targetOrdinal != null) {
					submitLibraryDeck(
						pages = pages,
						ordinal = targetOrdinal,
						role = ReaderDeckSubmissionRole.Pending
					)
					prefetchDecodedWorkingSet(gestureId, targetOrdinal)
				}
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement started gestureId=$gestureId generation=$generationId " +
						"change=$pageChange"
				)
			}

			override fun onSettlementCompleted(
				gestureId: Long,
				generationId: Long,
				currentLogicalPageId: String,
				currentPageOrdinal: Int,
				pageChange: PageChange
			) {
				try {
					if (pageChange == PageChange.NONE) {
						if (!finishGesture(
								gestureId,
								ReaderPageGestureTerminalOutcome.CancelledByUser,
								ReaderPageGestureTerminalDetail.SettlementCompleted(
									pageChange,
									currentPageOrdinal
								)
							)
						) {
							return
						}
						releaseTerminalContentFailureAfterCancelledGesture(
							cancelledGestureId = gestureId,
							settledOrdinal = currentPageOrdinal
						)
						discardPendingDeck("settlement-none")
						Logger.i(
							ReaderPlayLikeCurlFoliateControllerTag,
							"PlayLikeCurl settlement completed generation=$generationId " +
								"ordinal=$currentPageOrdinal change=$pageChange exactDispatch=false"
						)
						hideSurfaceAfterGesture(gestureId)
						updateReadiness(
							textureDeck = ReaderTextureDeckState.Ready,
							interaction = preparedInteractionState(),
							reason = "settlement-completed-none:$gestureId"
						)
						return
					}

					val rasterGeneration = bundleSource.currentGeneration()
					val profile = activePages?.profile
					if (activeGestureId != gestureId) return
					if (
						activeDeckGenerationId != generationId ||
						profile?.rasterGeneration != rasterGeneration
					) {
						val detail =
							ReaderPageGestureTerminalDetail.RelocationGenerationOrSessionDrift(
								gestureId
							)
						discardDecodedWorkingSetPrefetch("settlement-generation-drift", gestureId)
						publishGestureTerminal(
							gestureId,
							ReaderPageGestureTerminalOutcome.FailedRecovery,
							detail
						)
						check(
							relocationGestureCoordinator.finish(
								gestureId,
								ReaderPageGestureTerminalOutcome.FailedRecovery,
								detail
							)
						) { "Drift terminal lost its relocation reservation" }
						return
					}

					val sourceOrdinal = currentOrdinal
					val promotedGeneration = promotePendingDeck(currentPageOrdinal)
					if (promotedGeneration == null) {
						finishGesture(
							gestureId,
							ReaderPageGestureTerminalOutcome.FailedRecovery,
							ReaderPageGestureTerminalDetail.RecoveryFailed
						)
						return
					}
					val direction = when (pageChange) {
						PageChange.NEXT -> ReaderPageTurnDirection.Next
						PageChange.PREVIOUS -> ReaderPageTurnDirection.Previous
						PageChange.NONE -> error("Non-committed settlement reached relocation")
					}
					val outcome = when (pageChange) {
						PageChange.NEXT -> ReaderPageGestureTerminalOutcome.CommittedForward
						PageChange.PREVIOUS -> ReaderPageGestureTerminalOutcome.CommittedBackward
						PageChange.NONE -> error("Non-committed settlement reached relocation")
					}
					val result = relocationGestureCoordinator.commit(
						gestureId = gestureId,
						settledSourceTextureGeneration = generationId,
						promotedRasterGeneration = rasterGeneration,
						promotedTextureGeneration = promotedGeneration,
						destinationOrdinal = currentPageOrdinal,
						logicalDirection = direction,
						currentFoliateSessionId = checkNotNull(currentFoliateSessionId),
						publishDriftTerminal = { driftOutcome, detail ->
							discardDecodedWorkingSetPrefetch(
								"settlement-commit-drift",
								gestureId
							)
							publishGestureTerminal(gestureId, driftOutcome, detail)
						},
						publishCommittedTerminal = {
							currentOrdinal = currentPageOrdinal
							committedTurnVersion = Math.incrementExact(committedTurnVersion)
							val destinationWindow = profile.preparedPageIndices(currentPageOrdinal)
							publishProtectedWindow(destinationWindow)
							commitDecodedWorkingSetPrefetch(
								gestureId,
								currentPageOrdinal,
								destinationWindow
							)
							gateForDecodedWorkingSetRefill(profile, destinationWindow)
							if (!hasDecodedWorkingSetForCurrentOrdinal()) {
								refillDecodedWorkingSet(currentPageOrdinal, "settlement-committed")
							} else {
								deferredDecodedRefillCenterOrdinal = null
								discardDecodedWorkingSetPrefetch(
									"settlement-committed-window-ready",
									gestureId
								)
							}
							publishGestureTerminal(
								gestureId,
								outcome,
								ReaderPageGestureTerminalDetail.SettlementCompleted(
									pageChange,
									currentPageOrdinal
								)
							)
						},
						dispatch = ::transferAndDispatchRelocation
					)
					if (result !is ReaderPageRelocationCommitResult.Published) {
						recoverRejectedSettlement(sourceOrdinal, promotedGeneration)
						return
					}

					Logger.i(
						ReaderPlayLikeCurlFoliateControllerTag,
						"PlayLikeCurl settlement completed generation=$generationId " +
							"ordinal=$currentPageOrdinal change=$pageChange exactDispatch=true"
					)
					schedulePersistentRefill(
						direction = direction,
						destinationOrdinal = currentPageOrdinal,
						expectedTurnVersion = committedTurnVersion
					)
				} finally {
					completeSettlementReconciliation(
						gestureId = gestureId,
						sourceGenerationId = generationId,
						retryDeferredRefresh = pageChange == PageChange.NONE
					)
				}
			}

			override fun onSettlementCancelled(
				gestureId: Long,
				generationId: Long,
				currentLogicalPageId: String
			) {
				try {
					if (!finishGesture(
							gestureId,
							ReaderPageGestureTerminalOutcome.CancelledByUser,
							ReaderPageGestureTerminalDetail.SettlementCancelled(generationId)
						)
					) {
						return
					}
					Logger.i(
						ReaderPlayLikeCurlFoliateControllerTag,
						"PlayLikeCurl settlement cancelled generation=$generationId"
					)
					discardPendingDeck("settlement-cancelled")
					if (
						readinessState.textureDeck != ReaderTextureDeckState.Failed &&
						readinessState.interaction != ReaderPageInteractionState.Failed
					) {
						updateReadiness(
							textureDeck = ReaderTextureDeckState.Ready,
							interaction = preparedInteractionState(),
							reason = "settlement-cancelled:$gestureId"
						)
					}
					hideSurfaceAfterGesture(gestureId)
				} finally {
					if (settlementMutationFence.hasUnreconciledSettlement) {
						completeSettlementReconciliation(
							gestureId = gestureId,
							sourceGenerationId = generationId,
							retryDeferredRefresh = true
						)
					}
				}
			}

			override fun onRenderFailure(failure: RenderFailure) {
				val generationId = failure.generationId
				val nonRecoverableContextFailure =
					failure.reason == RenderFailureReason.CONTEXT && !failure.isRecoverable
				if (!nonRecoverableContextFailure && generationId in recoveredDeckGenerations) {
					val recoveryRole = generationRoles[generationId]
					val coordinatorOwned =
						deckRecoveryCoordinator.ownsSubmittedGeneration(generationId)
					if (recoveryRole != null) {
						tombstoneSubmittedRecoveredDeck(generationId, recoveryRole)
					}
					if (coordinatorOwned) {
						deckRecoveryCoordinator.onDeckPreparationFailed(
							generationId,
							failure.reason.name
						)
					} else {
						publishUnownedRecoveredDeckFailure(
							generationId,
							recoveryRole,
							failure.reason
						)
					}
					Logger.e(
						ReaderPlayLikeCurlFoliateControllerTag,
						"Recovered PlayLikeCurl deck preparation failed " +
							"generation=$generationId reason=${failure.reason}"
					)
					return
				}
				if (!readerRenderFailureOwnsCurrentPresentation(
						generationId = generationId,
						activeGenerationId = activeDeckGenerationId,
						pendingGenerationId = pendingDeckGenerationId,
						reason = failure.reason,
						isRecoverable = failure.isRecoverable
					)
				) {
					releaseGeneration(generationId)
					Logger.w(
						ReaderPlayLikeCurlFoliateControllerTag,
						"Ignored superseded PlayLikeCurl render failure " +
							"generation=$generationId reason=${failure.reason}"
					)
					return
				}
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Failed,
					interaction = ReaderPageInteractionState.Failed,
					reason = "render-failure:${failure.generationId}:${failure.reason}"
				)
				finishActiveGesture(
					ReaderPageGestureTerminalOutcome.FailedRenderer,
					ReaderPageGestureTerminalDetail.RenderFailed(
						failure.generationId,
						failure.reason
					)
				)
				onUnsafeLifecycleEvent(
					if (
						failure.reason == RenderFailureReason.CONTEXT &&
						!failure.isRecoverable
					) {
						ReaderPageHostLifecycleEvent.UnsafeContextLost
					} else {
						ReaderPageHostLifecycleEvent.GlFailed
					}
				)
				hideSurface()
				Logger.e(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl render failure generation=${failure.generationId} reason=${failure.reason}"
				)
			}
		})
	}

	private fun hasDecodedWorkingSetForCurrentOrdinal(): Boolean {
		val profile = requestedProfile ?: return false
		val pages = activePages ?: return false
		return pages.profile == profile &&
			pages.deck.pageIndices.containsAll(profile.preparedPageIndices(currentOrdinal))
	}

	val isAvailable: Boolean
		get() = readerPageLivePresentationAvailable(
			hasFailedLivePresentation = failedLivePresentationGeneration != null,
			otherwiseAvailable = enabled && attached &&
				hasDecodedWorkingSetForCurrentOrdinal() &&
				deckRecoveryCoordinator.canAcceptPointer &&
				pageOperationPolicy.newPointer is ReaderPageNewPointerDecision.Accept
		)

	private val canPresentAcceptedGesture: Boolean
		get() = enabled && attached && (
			pageOperationPolicy.continueActivePointer ||
				pageOperationPolicy.continueSettlement
			)

	fun setPageOperationPolicy(policy: ReaderPageOperationPolicy) {
		pageOperationPolicy = policy
	}

	private fun unavailableGestureOutcome(): ReaderPageGestureTerminalOutcome =
		if (!hasDecodedWorkingSetForCurrentOrdinal()) {
			ReaderPageGestureTerminalOutcome.RejectedPreparing
		} else {
			(pageOperationPolicy.newPointer as? ReaderPageNewPointerDecision.Reject)?.outcome
				?: ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
		}

	fun updatePaginationReadiness(readiness: ReaderPagePaginationReadiness) {
		if (publishedPaginationReadiness == readiness) return
		publishedPaginationReadiness = readiness
		onPaginationReadinessChanged(readiness)
	}

	private fun publishRasterProfileEpoch(profile: ReaderPlayLikeCurlRasterProfile?) {
		if (profile == null) {
			publishedRasterProfile = null
			publishedRasterProfileEpoch = null
			notifyPreparedActiveDeckChanged(null)
			onProtectedRasterSourcePageIndicesChanged(emptySet())
			onRasterProfileEpochChanged(null)
			return
		}
		if (publishedRasterProfile == profile) return
		publishedRasterProfile = profile
		notifyPreparedActiveDeckChanged(null)
		val epoch = profile.let {
			val current = nextRasterProfileEpoch
			nextRasterProfileEpoch = Math.incrementExact(current)
			current
		}
		publishedRasterProfileEpoch = epoch
		onRasterProfileEpochChanged(epoch)
	}

	private fun publishPreparedActiveDeck(sourceOrdinal: Int = currentOrdinal) {
		val generationId = activeDeckGenerationId
		val pages = generationId?.let(generationOwners::get)
		val profileEpoch = publishedRasterProfileEpoch
		val prepared = if (
			generationId != null &&
			pages != null &&
			profileEpoch != null &&
			hasPreparedActiveDeckOwnership()
		) {
			ReaderPagePreparedActiveDeck(
				rasterProfileEpoch = profileEpoch,
				rasterEpoch = pages.profile.rasterGeneration,
				sourceCenterPageIndex = pages.profile.pageRequest(sourceOrdinal).sourcePageIndex,
				generationId = generationId
			)
		} else {
			null
		}
		notifyPreparedActiveDeckChanged(prepared)
	}

	private fun notifyPreparedActiveDeckChanged(deck: ReaderPagePreparedActiveDeck?) {
		try {
			onPreparedActiveDeckChanged(deck)
		} catch (failure: Throwable) {
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"Prepared active deck observer failed " +
					"failureClass=${failure::class.simpleName ?: "unknown"}"
			)
		}
	}

	fun setEnabled(value: Boolean) {
		if (enabled == value) return
		enabled = value
		logActivationState("enabled", "value=$value")
		if (value) {
			onRequestPrewarm()
			refreshPreparedDeck()
		} else {
			invalidate("disabled")
		}
	}

	fun updateBitmapQuality(value: String?) {
		val normalized = normalizeReaderPageBitmapQuality(value)
		if (bitmapQuality == normalized) return
		bitmapQuality = normalized
		invalidate(
			reason = "bitmap-quality-${normalized.persistedValue}",
			profileRegeneration = true
		)
		if (enabled) onRequestPrewarm()
	}

	fun setSnapshotKey(value: Int) {
		if (snapshotKey == value) return
		snapshotKey = value
		invalidate(
			reason = "snapshot-key",
			profileRegeneration = true
		)
		if (enabled) onRequestPrewarm()
	}

	fun onPreparationStateChanged(state: ReaderPagePreparationState) {
		setPageOperationPolicy(state.operationPolicy)
		preparationPhase = state.phase
		if (!enabled || destroyed) return
		logActivationState(
			event = "preparation-state",
			detail = buildString {
				append("phase=${state.phase}")
				append(" completed=${state.completedCount}/${state.requiredCount}")
				if (!state.error.isNullOrBlank()) append(" error=${state.error}")
			}
		)
		when (state.phase) {
			ReaderPagePreparationPhase.Ready -> {
				logActivationState("preparation-ready")
				if (readinessState.textureDeck == ReaderTextureDeckState.Ready) {
					updateReadiness(
						interaction = ReaderPageInteractionState.Ready,
						reason = "raster-preparation-ready"
					)
				}
				activeDeckGenerationId?.let(::retryRelocationVisualHandoffForPreparedDeck)
				if (
					livePresentationRecoveryRequest.shouldForcePreparation(state.phase)
				) {
					refreshPreparedDeck()
				} else if (activePages == null) {
					refreshPreparedDeck()
				} else {
					val deferredCenterOrdinal = deferredDecodedRefillCenterOrdinal
					deferredDecodedRefillCenterOrdinal = null
					deferredCenterOrdinal
						?.takeIf { centerOrdinal -> centerOrdinal == currentOrdinal }
						?.let { centerOrdinal ->
							refillDecodedWorkingSet(
								centerOrdinal,
								"raster-preparation-ready"
							)
						}
				}
			}
			ReaderPagePreparationPhase.Failed -> {
				updateReadiness(
					interaction = ReaderPageInteractionState.Failed,
					reason = "raster-preparation-failed"
				)
				logActivationState("refresh-gated", "preparation-failed")
			}
			ReaderPagePreparationPhase.Preparing -> {
				if (
					readinessState.textureDeck == ReaderTextureDeckState.Settling ||
					readinessState.interaction == ReaderPageInteractionState.Settling
				) {
					logActivationState("readiness-preserved", "raster-preparation-during-settlement")
				} else if (readinessState.textureDeck == ReaderTextureDeckState.Ready) {
					updateReadiness(
						interaction = if (
							decodedRefillCenterOrdinal != null ||
							deferredDecodedRefillCenterOrdinal != null
						) {
							ReaderPageInteractionState.RefillingWorkingSet
						} else {
							ReaderPageInteractionState.BackgroundPrefetch
						},
						reason = "raster-background-prefetch"
					)
				} else {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Preparing,
						interaction = blockingPreparationState(),
						reason = "raster-preparation-blocking"
					)
				}
			}
			ReaderPagePreparationPhase.Idle -> Unit
		}
	}

	fun onHostAttached() {
		if (destroyed || attached) return
		attached = true
		surfaceView.attach()
		logActivationState("host-attached")
		if (enabled) {
			onRequestPrewarm()
			refreshPreparedDeck()
			dispatchNextRelocation()
			retryRelocationVisualHandoffAttached()
		}
	}

	fun onHostSizeChanged() {
		if (!enabled || destroyed) return
		invalidate(
			reason = "size-changed",
			profileRegeneration = true
		)
		onRequestPrewarm()
	}

	fun onHostContentReady() {
		if (!enabled || destroyed) return
		logActivationState("host-content-ready")
		refreshPreparedDeck()
		dispatchNextRelocation()
		retryRelocationVisualHandoffAttached()
	}

	fun onWebViewAttachmentChanged(webViewAttached: Boolean) {
		if (!webViewAttached || !enabled || destroyed) return
		dispatchNextRelocation()
		retryRelocationVisualHandoffAttached()
	}

	fun onHostResumedChanged(resumed: Boolean) {
		if (hostResumed == resumed) return
		hostResumed = resumed
		if (resumed && enabled && !destroyed) {
			retryRelocationVisualHandoffResumed()
		}
	}

	fun onHostWindowHidden() {
		if (!enabled || destroyed) return
		invalidate(
			reason = "window-hidden",
			profileRegeneration = true
		)
	}

	fun onPageTouchEvent(
		event: MotionEvent,
		gestureId: Long
	): ReaderPageCurlDispatchResult {
		if (event.actionMasked != MotionEvent.ACTION_DOWN) {
			surfaceView.onPageTouchEvent(event, gestureId)
			if (event.actionMasked == MotionEvent.ACTION_MOVE) {
				revealSurfaceAfterNextPresentedFrame(gestureId)
			}
			return ReaderPageCurlDispatchResult.Accepted
		}

		activeGestureId = gestureId
		val metadata = relocationMetadata(gestureId)
		if (!isAvailable || metadata == null) {
			val detail = ReaderPageGestureTerminalDetail.TouchRejected(event.actionMasked)
			check(
				publishGestureTerminal(
					gestureId,
					unavailableGestureOutcome(),
					detail
				)
			) { "Unavailable touch terminal was not published" }
			hideSurfaceAfterGesture(gestureId)
			return ReaderPageCurlDispatchResult.TerminalPublished
		}

		return when (
			val result = relocationGestureCoordinator.start(
				metadata = metadata,
				protocolActionMasked = event.actionMasked,
				rendererAdmission = {
					surfaceView.onPageTouchEvent(event, gestureId)
				},
				publishTerminal = { outcome, detail ->
					publishGestureTerminal(gestureId, outcome, detail)
				}
			)
		) {
			ReaderPageRelocationStartResult.Admitted ->
				ReaderPageCurlDispatchResult.Accepted
			is ReaderPageRelocationStartResult.TerminalPublished -> {
				hideSurfaceAfterGesture(gestureId)
				ReaderPageCurlDispatchResult.TerminalPublished
			}
		}
	}

	override fun start(
		gestureId: Long,
		pageChange: PageChange,
		onTerminal: (
			ReaderPageGestureTerminalOutcome,
			ReaderPageGestureTerminalDetail
		) -> Boolean
	): ReaderPageTurnStartResult {
		check(tapTurnGestureId == null && tapTurnTerminalSink == null) {
			"A tap turn terminal sink is already installed"
		}
		tapTurnGestureId = gestureId
		tapTurnTerminalSink = onTerminal
		return try {
			startTapTurn(pageChange, gestureId)
		} catch (failure: Throwable) {
			if (tapTurnGestureId == gestureId) {
				tapTurnGestureId = null
				tapTurnTerminalSink = null
			}
			throw failure
		}
	}

	private fun relocationMetadata(
		gestureId: Long
	): ReaderPageRelocationReservationMetadata? {
		val sessionId = currentFoliateSessionId ?: return null
		val textureGeneration = activeDeckGenerationId ?: return null
		return ReaderPageRelocationReservationMetadata(
			gestureId = gestureId,
			sourceOrdinal = currentOrdinal,
			foliateSessionId = sessionId,
			reservedRasterGeneration = bundleSource.currentGeneration(),
			reservedTextureGeneration = textureGeneration
		)
	}

	private fun startTapTurn(
		pageChange: PageChange,
		gestureId: Long
	): ReaderPageTurnStartResult {
		activeGestureId = gestureId
		val metadata = relocationMetadata(gestureId)
		if (!isAvailable || metadata == null) {
			val outcome = unavailableGestureOutcome()
			val detail = ReaderPageGestureTerminalDetail.TapTurnUnavailable(pageChange)
			check(publishGestureTerminal(gestureId, outcome, detail)) {
				"Unavailable tap terminal was not published"
			}
			return ReaderPageTurnStartResult.TerminalPublished(outcome, detail)
		}

		return when (
			val result = relocationGestureCoordinator.start(
				metadata = metadata,
				protocolActionMasked = MotionEvent.ACTION_DOWN,
				rendererAdmission = {
					surfaceView.turn(pageChange, gestureId)
				},
				publishTerminal = { outcome, detail ->
					publishGestureTerminal(gestureId, outcome, detail)
				}
			)
		) {
			ReaderPageRelocationStartResult.Admitted -> {
				revealSurfaceAfterNextPresentedFrame(gestureId)
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl tap turn change=$pageChange accepted=true"
				)
				ReaderPageTurnStartResult.Settling
			}
			is ReaderPageRelocationStartResult.TerminalPublished -> {
				hideSurfaceAfterGesture(gestureId)
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl tap turn change=$pageChange accepted=false"
				)
				ReaderPageTurnStartResult.TerminalPublished(
					result.outcome,
					result.detail
				)
			}
		}
	}

	private fun revealSurfaceAfterNextPresentedFrame(gestureId: Long) {
		if (
			!canPresentAcceptedGesture ||
			activeGestureId != gestureId ||
			presentedFrameGestureId == gestureId
		) {
			return
		}
		surfaceView.animate().cancel()
		if (presentedSurfaceGestureId == gestureId) {
			surfaceView.alpha = 1f
			inlineRasterShield.dismiss()
			return
		}
		if (surfaceView.alpha != 0f) surfaceView.alpha = 0f
		presentedFrameRequestId?.let { requestId ->
			surfaceView.cancelPresentedFrameRequest(requestId)
		}
		presentedFrameRequestId = null
		presentedFrameGestureId = gestureId
		val requestId = try {
			surfaceView.requestNextPresentedFrame {
				val gestureStillOwnsReveal = presentedFrameGestureId == gestureId
				presentedFrameRequestId = null
				presentedFrameGestureId = null
				if (gestureStillOwnsReveal && enabled && attached && !destroyed) {
					presentedSurfaceGestureId = gestureId
					surfaceView.alpha = 1f
					inlineRasterShield.dismiss()
				}
			}
		} catch (failure: Throwable) {
			presentedFrameGestureId = null
			throw failure
		}
		if (requestId == PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID) {
			presentedFrameGestureId = null
			return
		}
		presentedFrameRequestId = requestId
	}

	fun cancelGestureAfterHostTerminal(gestureId: Long) {
		check(hostOwnedTerminalGestureIds.add(gestureId))
		try {
			cancelGesture(gestureId)
		} finally {
			hostOwnedTerminalGestureIds.remove(gestureId)
		}
	}

	fun cancelGesture(gestureId: Long) {
		activeGestureId = gestureId
		surfaceView.cancelGesture(gestureId)
		if (activeGestureId == gestureId) {
			finishGesture(
				gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser,
				ReaderPageGestureTerminalDetail.ControllerCancelled
			)
		}
		hideSurfaceAfterGesture(gestureId)
	}

	private fun cancelRendererWork(cancellationReason: ReaderPageLifecycleCancellationReason) {
		surfaceView.cancelGesture()
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl renderer work cancelled reason=$cancellationReason"
		)
	}

	fun cancelActiveGesture(cancellationReason: ReaderPageLifecycleCancellationReason) {
		val gestureId = activeGestureId
		if (gestureId != null) {
			cancelGesture(gestureId)
		} else {
			cancelRendererWork(cancellationReason)
			hideSurface()
		}
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl active gesture cancelled reason=$cancellationReason"
		)
	}

	fun setFoliateSessionId(sessionId: String) {
		require(sessionId.isNotBlank())
		val previous = currentFoliateSessionId
		if (previous == sessionId) return
		if (previous != null) {
			discardDecodedWorkingSetPrefetch("foliate-session-changed")
			relocationVisualHandoffCoordinator.cancelForQueueInvalidation()
			drainRelocationOwnership("foliate-session-changed")
			foliateSessionRelocationPending = true
		}
		currentFoliateSessionId = sessionId
		currentWebViewOrdinal = null
	}

	fun visualLocationOrigin(
		pageIndex: Int?,
		acknowledgement: ReaderPageTurnSettlementAck?
	): ReaderPageVisualLocationOrigin {
		val normalized = pageIndex?.takeIf { it >= 0 }
		val head = relocationQueue.head()
		val exactAcknowledgementMatches =
			normalized != null &&
				acknowledgement != null &&
				head != null &&
				relocationQueue.matchesDispatchedHead(
					token = acknowledgement.token,
					rasterGeneration = acknowledgement.rasterGeneration,
					textureGeneration = acknowledgement.textureGeneration,
					foliateSessionId = acknowledgement.foliateSessionId,
					destinationOrdinal = normalized
				) &&
				relocationLiveDispatchCoordinator.isCurrent(head)
		return readerPageVisualLocationOrigin(
			foliateSessionRelocationPending = foliateSessionRelocationPending,
			exactAcknowledgementMatches = exactAcknowledgementMatches,
			acknowledgementPresent = acknowledgement != null,
			relocationInFlight = relocationQueue.hasInFlightHead()
		)
	}

	fun synchronizeVisualPageIndex(
		pageIndex: Int?,
		_reason: String?,
		acknowledgement: ReaderPageTurnSettlementAck?
	) {
		val normalized = pageIndex?.takeIf { it >= 0 } ?: return
		val origin = visualLocationOrigin(normalized, acknowledgement)
		if (origin != ReaderPageVisualLocationOrigin.StaleAcknowledgement) {
			authoritativeLocationReady = true
			currentWebViewOrdinal = normalized
		}
		when (origin) {
			ReaderPageVisualLocationOrigin.ExactPageTurn -> {
				val matched = requireNotNull(acknowledgement)
				check(
					relocationQueue.acknowledge(
						token = matched.token,
						pageIndex = normalized,
						foliateSessionId = matched.foliateSessionId,
						rasterGeneration = matched.rasterGeneration,
						textureGeneration = matched.textureGeneration
					)
				)
				val acknowledged = requireNotNull(relocationQueue.head())
				check(relocationDispatchTimeout.cancel(acknowledged)) {
					"Acknowledged relocation did not own its dispatch timeout"
				}
				val delayed = qaFaultRegistry?.pauseRelocationAck(
					relocationToken = acknowledged.token.value,
					onAdmitted = { applied ->
						relocationQaFaultCorrelations[
							acknowledged.token.value
						] = applied.correlation()
					},
					completion = { applied ->
						completeAcknowledgedRelocation(
							acknowledged,
							applied.correlation()
						)
					}
				) == true
				if (!delayed) {
					emitRelocationDiagnostic(
						acknowledged,
						ReaderPageRelocationDiagnosticState.Acknowledged
					)
					check(relocationVisualHandoffCoordinator.onAcknowledged(acknowledged)) {
						"Acknowledged relocation did not start visual handoff"
					}
					foliateSessionRelocationPending = false
					refillDecodedWorkingSet(
						acknowledged.destinationOrdinal,
						"foliate-exact-settlement"
					)
				}
			}

			ReaderPageVisualLocationOrigin.PendingExactPageTurn -> Unit

			ReaderPageVisualLocationOrigin.StaleAcknowledgement -> Unit

			ReaderPageVisualLocationOrigin.External -> {
				val sessionRelocation = foliateSessionRelocationPending
				foliateSessionRelocationPending = false
				if (
					!sessionRelocation &&
					currentOrdinal == normalized &&
					relocationQueue.occupiedCount() == 0
				) return
				currentOrdinal = normalized
				invalidate("external-page-relocation")
				if (enabled) onRequestPrewarm()
			}
		}
	}

	private fun completeAcknowledgedRelocation(
		acknowledged: ReaderPageRelocationRequest,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation?
	) {
		val queueCurrent = relocationQueue.matchesAcknowledgedHead(
			token = acknowledged.token.value,
			rasterGeneration = acknowledged.rasterGeneration,
			textureGeneration = acknowledged.textureGeneration,
			foliateSessionId = acknowledged.foliateSessionId,
			destinationOrdinal = acknowledged.destinationOrdinal
		)
		if (!queueCurrent) {
			relocationQaFaultCorrelations.remove(acknowledged.token.value)
			return
		}
		if (!relocationLiveDispatchCoordinator.isCurrent(acknowledged)) {
			relocationLiveDispatchCoordinator.fail(
				acknowledged,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
			return
		}
		emitRelocationDiagnostic(
			acknowledged,
			ReaderPageRelocationDiagnosticState.Acknowledged,
			qaFaultCorrelation = qaFaultCorrelation
		)
		val started = if (qaFaultCorrelation == null) {
			relocationVisualHandoffCoordinator.onAcknowledged(acknowledged)
		} else {
			relocationVisualHandoffCoordinator.onAcknowledged(
				acknowledged,
				qaFaultCorrelation
			)
		}
		check(started) { "Acknowledged relocation did not start visual handoff" }
		foliateSessionRelocationPending = false
		refillDecodedWorkingSet(
			acknowledged.destinationOrdinal,
			"foliate-exact-settlement"
		)
	}

	private fun cancelRelocationsWithDiagnostics(): ReaderPageRelocationDrain =
		cancelRelocationsWithDiagnostics(
			ReaderPageRelocationDiagnosticRejectionReason.QueueInvalidated
		)

	private fun cancelRelocationsWithDiagnostics(
		rejectionReason: ReaderPageRelocationDiagnosticRejectionReason
	): ReaderPageRelocationDrain {
		relocationDispatchTimeout.cancelAll()
		val drained = try {
			relocationGestureCoordinator.cancelAll()
		} finally {
			relocationLiveDispatchCoordinator.releaseAll()
		}
		drained.queued.forEach { request ->
			emitRelocationDiagnostic(
				request,
				ReaderPageRelocationDiagnosticState.Rejected,
				terminal = true,
				rejectionReason = rejectionReason
			)
		}
		return drained
	}

	private fun drainRelocationOwnership(
		reason: String,
		rejectionReason: ReaderPageRelocationDiagnosticRejectionReason =
			ReaderPageRelocationDiagnosticRejectionReason.QueueInvalidated
	) {
		clearRetainedInlineHandoffSnapshot()
		val drained = cancelRelocationsWithDiagnostics(rejectionReason)
		check(relocationGestureCoordinator.reservationCount() == 0)
		check(relocationQueue.reservedCount() == 0)
		check(relocationQueue.queuedCount() == 0)
		if (drained.queued.isNotEmpty() || drained.reservations.isNotEmpty()) {
			Logger.i(
				ReaderPlayLikeCurlFoliateControllerTag,
				"PlayLikeCurl relocation ownership drained " +
					"queued=${drained.queued.size} " +
					"reserved=${drained.reservations.size} reason=$reason"
			)
		}
	}

	fun invalidate(
		reason: String,
		profileRegeneration: Boolean = false,
		relocationRejectionReason: ReaderPageRelocationDiagnosticRejectionReason =
			ReaderPageRelocationDiagnosticRejectionReason.QueueInvalidated
	) {
		requestGeneration += 1L
		decodedRefillGeneration += 1L
		failedLivePresentationGeneration = null
		retainsRejectedSurfaceInputShield = false
		livePresentationRecoveryRequest.clear()
		decodedRefillCenterOrdinal = null
		deferredDecodedRefillCenterOrdinal = null
		discardDecodedWorkingSetPrefetch("invalidated:$reason")
		deckRecoveryCoordinator.cancelAll()
		repairQaFaultCorrelations.clear()
		publishProtectedWindow(emptyList())
		rasterRepairRequests.clear()
		relocationVisualHandoffCoordinator.cancelForQueueInvalidation()
		drainRelocationOwnership(
			"invalidated:$reason",
			relocationRejectionReason
		)
		updateReadiness(
			textureDeck = ReaderTextureDeckState.Empty,
			pendingTextureDeck = ReaderTextureDeckState.Empty,
			interaction = if (profileRegeneration && hasPreparedDeckBefore) {
				ReaderPageInteractionState.BlockingProfileRegeneration
			} else {
				ReaderPageInteractionState.BlockingInitialPreparation
			},
			reason = "invalidated:$reason"
		)
		hideSurface()
		generationOwners.keys.toList().forEach(surfaceView::releaseDeck)
		deckDiagnosticTracker?.cancelAll()
		generationRoles.clear()
		preparedDeckGenerations.clear()
		activeDeckGenerationId = null
		pendingDeckGenerationId = null
		pendingDeckOrdinal = null
		activePages?.obsolete = true
		activePages = null
		preparedPageSets.forEach { pages -> pages.obsolete = true }
		rasterAdapter?.let(::retireRasterAdapter)
		rasterAdapter = null
		foliateRasterLoader = null
		publishRasterProfileEpoch(null)
		requestedProfile = null
		preparedPageSets.toList().forEach(::closeIfUnused)
		Logger.i(ReaderPlayLikeCurlFoliateControllerTag, "PlayLikeCurl invalidated reason=$reason")
	}

	private val destroyFence = ReaderPageControllerDestroyFence(
		scope = teardownScope,
		fenceAdmission = {
			destroyed = true
			enabled = false
		},
		advanceGenerations = {
			requestGeneration += 1L
			decodedRefillGeneration += 1L
			decodedRefillCenterOrdinal = null
			deferredDecodedRefillCenterOrdinal = null
			discardDecodedWorkingSetPrefetch("destroyed")
		},
		cancelActiveGesture = {
			cancelActiveGesture(
				ReaderPageLifecycleCancellationReason.HostDestroyed
			)
		},
		cancelRecovery = deckRecoveryCoordinator::cancelAll,
		closeVisualHandoff = relocationVisualHandoffCoordinator::close,
		cancelRelocations = ::cancelRelocationsWithDiagnostics,
		verifyRelocationsDrained = { cancelled ->
			check(relocationGestureCoordinator.reservationCount() == 0)
			check(relocationQueue.reservedCount() == 0)
			check(relocationQueue.queuedCount() == 0)
			logActivationState(
				"teardown-relocations-cancelled",
				"queued=${cancelled.queued.size} " +
					"reserved=${cancelled.reservations.size}"
			)
		},
		markPageSetsObsolete = {
			preparedPageSets.forEach { pages -> pages.obsolete = true }
		},
		hideSurface = ::hideSurface,
		disposeRendererAndOwners = ::disposeRendererAndOwners
	)

	fun destroy(): Deferred<Unit> = destroyFence.start()

	private suspend fun disposeRendererAndOwners() {
		val rendererDisposed = CompletableDeferred<PageSurfaceDisposalResult>()
		try {
			withContext(NonCancellable) {
				var failure: Throwable? = null
				var rendererResult: PageSurfaceDisposalResult? = null
				failure = captureTeardownFailure(
					failure,
					ReaderPageTeardownStage.RendererDisposal
				) {
					surfaceView.disposeForLifecycleOwner { result ->
						check(rendererDisposed.complete(result))
					}
					rendererResult = rendererDisposed.await()
				}
				failure = captureTeardownFailure(
					failure,
					ReaderPageTeardownStage.ControllerWorker
				) {
					mainTerminalExecutor.closeAndJoin()
					check(mainTerminalExecutor.pendingActionCount() == 0) {
						"Controller main-terminal action ownership did not drain"
					}
					check(surfaceView.pendingMainTerminalActionCount == 0) {
						"Renderer main-terminal action ownership did not drain"
					}
				}
				if (rendererResult == null && rendererDisposed.isCompleted) {
					failure = captureTeardownFailure(
						failure,
						ReaderPageTeardownStage.RendererDisposal
					) {
						rendererResult = rendererDisposed.await()
					}
				}
				val terminalRendererResult = rendererResult
				val rendererOwnershipReleased = terminalRendererResult?.ownership?.let { snapshot ->
					snapshot.activeDeckLeases == 0 &&
						snapshot.pendingDeckLeases == 0 &&
						snapshot.releaseInFlightDeckLeases == 0 &&
						snapshot.orphanDeckLeases == 0 &&
						snapshot.textures == 0
				} == true
				if (terminalRendererResult == null) {
					failure = captureTeardownFailure(
						failure,
						ReaderPageTeardownStage.RendererOwnership
					) {
						error("Renderer disposal did not publish terminal ownership")
					}
				} else {
					val rendererSnapshot = terminalRendererResult.ownership
					failure = captureTeardownFailure(
						failure,
						ReaderPageTeardownStage.RendererDisposal,
						terminalRendererResult.failureStage
					) {
						if (!terminalRendererResult.isSuccessful) {
							throw terminalRendererResult.failure
								?: IllegalStateException(
									"Renderer disposal failed without a cause"
								)
						}
					}
					failure = captureTeardownFailure(
						failure,
						ReaderPageTeardownStage.RendererOwnership
					) {
						check(
							rendererSnapshot.activeDeckLeases == 0 &&
								rendererSnapshot.pendingDeckLeases == 0 &&
								rendererSnapshot.releaseInFlightDeckLeases == 0 &&
								rendererSnapshot.orphanDeckLeases == 0 &&
								rendererSnapshot.textures == 0
						) { "Renderer disposal retained authoritative ownership" }
					}
				}

				val strandedGenerations = generationOwners.keys.toList()
				failure = captureTeardownFailure(
					failure,
					ReaderPageTeardownStage.DeckGeneration
				) {
					check(strandedGenerations.isEmpty()) {
						"Renderer disposal left deck generations owned"
					}
				}
				if (rendererOwnershipReleased) {
					strandedGenerations.forEach { generationId ->
						failure = captureTeardownFailure(
							failure,
							ReaderPageTeardownStage.DeckGeneration
						) {
							releaseGeneration(generationId)
						}
					}
				}
				deckDiagnosticTracker?.cancelAll()
				preparedPageSets.toList().forEach { pages ->
					failure = captureTeardownFailure(
						failure,
						ReaderPageTeardownStage.RasterDeck
					) {
						closeIfUnused(pages)
					}
				}
				failure = captureTeardownFailure(
					failure,
					ReaderPageTeardownStage.RasterDeck
				) {
					check(preparedPageSets.isEmpty()) {
						"Renderer disposal left raster decks pinned"
					}
				}

				disposeRasterAdapterOwners(failure)?.let { throw it }
			}
		} finally {
			teardownJob.complete()
		}
	}

	private suspend fun disposeRasterAdapterOwners(
		initialFailure: Throwable?
	): Throwable? {
		var failure = initialFailure
		val adaptersToClose = rasterAdapterOwners.snapshot()
		adaptersToClose.forEach { adapter ->
			failure = captureTeardownFailure(
				failure,
				ReaderPageTeardownStage.RasterAdapter
			) {
				adapter.close()
			}
		}
		failure = captureTeardownFailure(
			failure,
			ReaderPageTeardownStage.ControllerWorker
		) {
			rasterJob.cancelAndJoin()
		}
		adaptersToClose.forEach { adapter ->
			failure = captureTeardownFailure(
				failure,
				ReaderPageTeardownStage.RasterAdapter
			) {
				adapter.closeAndJoin()
			}
		}
		val adapterMetrics = mutableListOf<ReaderPlayLikeCurlRasterResidencyMetrics>()
		adaptersToClose.forEach { adapter ->
			failure = captureTeardownFailure(
				failure,
				ReaderPageTeardownStage.RasterAdapter
			) {
				adapterMetrics += adapter.metrics()
			}
		}
		var budgetMetrics: ReaderPlayLikeCurlRasterResidencyBudgetMetrics? = null
		failure = captureTeardownFailure(
			failure,
			ReaderPageTeardownStage.RasterAdapter
		) {
			budgetMetrics = rasterResidencyBudget.metrics()
		}
		var finalResidency: ReaderPlayLikeCurlRasterResidencyMetrics? = null
		if (adapterMetrics.size == adaptersToClose.size && budgetMetrics != null) {
			failure = captureTeardownFailure(
				failure,
				ReaderPageTeardownStage.RasterAdapter
			) {
				finalResidency = aggregateRasterResidencyMetrics(
					adapterMetrics,
					checkNotNull(budgetMetrics)
				)
			}
		}
		finalResidency?.let { residency ->
			disposedRasterResidencyMetrics = residency
			failure = captureTeardownFailure(
				failure,
				ReaderPageTeardownStage.RasterAdapter
			) {
				check(residency.isDrained()) {
					"Raster adapter ownership did not drain"
				}
			}
		}
		adaptersToClose.forEach { adapter ->
			failure = captureTeardownFailure(
				failure,
				ReaderPageTeardownStage.RasterAdapter
			) {
				check(rasterAdapterOwners.remove(adapter))
				onOwnershipMutated()
			}
		}
		rasterAdapter = null
		activePages = null
		requestedProfile = null
		rasterRetirementFailure?.let { retirementFailure ->
			failure = captureTeardownFailure(
				failure,
				ReaderPageTeardownStage.RasterAdapter
			) {
				throw retirementFailure
			}
		}
		return failure
	}

	suspend fun destroyAndJoin() {
		destroy().await()
	}

	fun rasterResidencyMetrics(): ReaderPlayLikeCurlRasterResidencyMetrics =
		disposedRasterResidencyMetrics
			?: aggregateRasterResidencyMetrics(
				rasterAdapterOwners.snapshot().map { adapter ->
					adapter.metrics()
				},
				rasterResidencyBudget.metrics()
			)

	fun diagnosticRasterGeneration(): Long = bundleSource.currentGeneration()

	fun diagnosticTextureGeneration(): Long = activeDeckGenerationId ?: -1L

	fun applicationOwnershipMetrics(): ReaderPlayLikeCurlControllerOwnershipMetrics =
		ReaderPlayLikeCurlControllerOwnershipMetrics(
			rasterResidency = rasterResidencyMetrics(),
			pendingVisualCallbacks =
				relocationVisualHandoffCoordinator.pendingCallbackCount() +
					relocationDispatchTimeout.pendingCallbackCount() +
					inlineRasterShield.pendingCallbackCount(),
			pendingVisualCallbackLimit =
				relocationVisualHandoffCoordinator.pendingCallbackLimit() +
					relocationDispatchTimeout.pendingCallbackLimit +
					inlineRasterShield.pendingCallbackLimit,
			relocation = relocationQueue.ownershipSnapshot()
		)

	override fun requestOwnershipSnapshot(
		onResult: (ReaderPageRendererOwnershipResult) -> Unit
	) {
		surfaceView.requestOwnershipSnapshot { result ->
			if (result.status != PageSurfaceOwnershipResult.Status.AVAILABLE) {
				onResult(ReaderPageRendererOwnershipResult.Unavailable(result.status))
				return@requestOwnershipSnapshot
			}
			val snapshot = checkNotNull(result.snapshot) {
				"Available renderer ownership result omitted its snapshot"
			}
			onResult(
				ReaderPageRendererOwnershipResult.Available(
					ReaderPageRendererOwnershipSnapshot(
						activeDeckLeases = snapshot.activeDeckLeases,
						activeDeckLeaseLimit = snapshot.activeDeckLeaseLimit,
						pendingDeckLeases = snapshot.pendingDeckLeases,
						pendingDeckLeaseLimit = snapshot.pendingDeckLeaseLimit,
						releaseInFlightDeckLeases = snapshot.releaseInFlightDeckLeases,
						releaseInFlightDeckLeaseLimit =
							snapshot.releaseInFlightDeckLeaseLimit,
						orphanDeckLeases = snapshot.orphanDeckLeases,
						orphanDeckLeaseLimit = snapshot.orphanDeckLeaseLimit,
						rendererTextures = snapshot.textures,
						rendererTextureLimit = snapshot.textureLimit,
						pendingCallbacks = surfaceView.pendingCallbackCount,
						pendingCallbackLimit = surfaceView.pendingCallbackLimit
					)
				)
			)
		}
	}

	override fun setCallbackCapacityListener(listener: () -> Unit) {
		check(ownershipCapacityListener == null) {
			"Renderer ownership capacity listener is already registered"
		}
		val runnable = Runnable(listener)
		ownershipCapacityListener = listener
		ownershipCapacityRunnable = runnable
		surfaceView.setOwnershipCallbackCapacityListener(runnable)
	}

	override fun clearCallbackCapacityListener(listener: () -> Unit) {
		if (ownershipCapacityListener !== listener) return
		val runnable = checkNotNull(ownershipCapacityRunnable)
		surfaceView.clearOwnershipCallbackCapacityListener(runnable)
		ownershipCapacityListener = null
		ownershipCapacityRunnable = null
	}

	private fun aggregateRasterResidencyMetrics(
		metrics: List<ReaderPlayLikeCurlRasterResidencyMetrics>,
		budget: ReaderPlayLikeCurlRasterResidencyBudgetMetrics
	): ReaderPlayLikeCurlRasterResidencyMetrics {
		check(metrics.sumOf { it.residentEntries } == budget.residentEntries) {
			"Adapter entries differ from shared residency ownership"
		}
		return ReaderPlayLikeCurlRasterResidencyMetrics(
			residentEntries = budget.residentEntries,
			uniqueDecodedBitmaps = metrics.sumOf { it.uniqueDecodedBitmaps },
			residentEntryLimit = budget.residentEntryLimit,
			uniqueDecodedBitmapLimit = metrics.sumOf { it.uniqueDecodedBitmapLimit },
			peakResidentEntries = budget.peakResidentEntries,
			peakUniqueDecodedBitmaps = metrics.sumOf { it.peakUniqueDecodedBitmaps },
			pinnedEntries = metrics.sumOf { it.pinnedEntries },
			activePreparationWorkers = metrics.sumOf { it.activePreparationWorkers },
			activeMaterializationWorkers = metrics.sumOf { it.activeMaterializationWorkers },
			pendingValueReleases = metrics.sumOf { it.pendingValueReleases },
			evictedEntries = metrics.sumOf { it.evictedEntries },
			releasedEntries = metrics.sumOf { it.releasedEntries }
		)
	}

	private suspend fun captureTeardownFailure(
		current: Throwable?,
		stage: ReaderPageTeardownStage,
		rendererStage: PageSurfaceDisposalStage? = null,
		action: suspend () -> Unit
	): Throwable? = try {
		readerPageTeardownStage(stage, rendererStage, action)
		current
	} catch (next: Throwable) {
		if (current == null) next
		else current.apply {
			if (next !== current) addSuppressed(next)
		}
	}

	private suspend fun <T> awaitRasterPreparation(
		preparation: Deferred<T>
	): Result<T> = try {
		Result.success(preparation.await())
	} catch (cancelled: CancellationException) {
		throw cancelled
	} catch (failure: Throwable) {
		Result.failure(failure)
	}

	private fun signalRasterCapacityAvailable(): Boolean {
		if (destroyed) return true
		if (!rasterCapacityRefreshPosted.compareAndSet(false, true)) return true
		val accepted = mainHandler.post {
			rasterCapacityRefreshPosted.set(false)
			if (!destroyed) refreshPreparedDeck()
		}
		if (!accepted) {
			rasterCapacityRefreshPosted.set(false)
			updateReadiness(
				interaction = ReaderPageInteractionState.Failed,
				reason = "capacity-dispatch-rejected"
			)
		}
		return accepted
	}

	private fun ReaderPlayLikeCurlRasterResidencyMetrics.isDrained(): Boolean =
		residentEntries == 0 &&
			uniqueDecodedBitmaps == 0 &&
			pinnedEntries == 0 &&
			activePreparationWorkers == 0 &&
			activeMaterializationWorkers == 0 &&
			pendingValueReleases == 0

	private fun recordRasterRetirementFailure(failure: Throwable) {
		val first = rasterRetirementFailure
		if (first == null) rasterRetirementFailure = failure
		else if (failure !== first) first.addSuppressed(failure)
	}

	private fun retireRasterAdapter(
		adapter: ReaderPlayLikeCurlRasterAdapter<ReaderPlayLikeCurlRasterImage>
	) {
		adapter.close()
		rasterScope.launch {
			val joinResult = runCatching { adapter.closeAndJoin() }
			val metricsResult = runCatching { adapter.metrics() }
			withContext(Dispatchers.Main.immediate) {
				val metrics = metricsResult.getOrNull()
				val drained = metrics?.let { current -> current.isDrained() } == true
				if (drained) {
					check(rasterAdapterOwners.remove(adapter))
					onOwnershipMutated()
					if (!destroyed) refreshPreparedDeck()
				}

				val failure = joinResult.exceptionOrNull()
					?: metricsResult.exceptionOrNull()
					?: if (!drained) {
						IllegalStateException(
							"Retiring raster adapter retained ownership"
						)
					} else {
						null
					}
				if (failure != null) {
					recordRasterRetirementFailure(failure)
					logActivationState(
						"adapter-retirement-failed",
						if (drained) "owner-released" else "owner-retained"
					)
					if (!destroyed) {
						updateReadiness(
							interaction = ReaderPageInteractionState.Failed,
							reason = "adapter-retirement-failed"
						)
					}
				}
			}
		}
	}

	private fun createRasterAdapter(
		profile: ReaderPlayLikeCurlRasterProfile
	): ReaderPlayLikeCurlRasterAdapter<ReaderPlayLikeCurlRasterImage> {
		val loader = ReaderPlayLikeCurlFoliateRasterLoader(
			bundleSource = bundleSource,
			profile = profile,
			webViewProvider = webViewProvider,
			referenceSnapshotProvider = {
				val preferred = profile.pageRequest(currentOrdinal).sourcePageIndex
				bundleSource.retainedReferenceSnapshot(
					preferred,
					profile.transitionKind()
				)
			},
			diagnostics = diagnostics,
			qaFaultRegistry = qaFaultRegistry,
			qaMissTargetOrdinalProvider = { currentOrdinal },
			onMissingRaster = { sourcePageIndex ->
				requestRasterRepair(sourcePageIndex, profile)
			},
			onQaMissingRaster = { sourcePageIndex, correlation ->
				requestRasterRepair(
					sourcePageIndex = sourcePageIndex,
					profile = profile,
					qaFaultCorrelation = correlation
				)
			}
		)
		return ReaderPlayLikeCurlRasterAdapter(
			scope = rasterScope,
			loader = loader,
			rendererDeckLeaseLimit = surfaceView.deckLeaseLimit,
			residencyBudget = rasterResidencyBudget,
			onCapacityAvailable = ::signalRasterCapacityAvailable,
			acquisitionInterceptor = loader::consumeQaMiss,
			publicationDispatcher = Dispatchers.Main.immediate,
			onOwnershipMutated = onOwnershipMutated,
			release = { image ->
				if (!image.bitmap.isRecycled) image.bitmap.recycle()
			}
		).also {
			foliateRasterLoader = loader
		}
	}

	private fun createRasterAdapterOrDefer(
		profile: ReaderPlayLikeCurlRasterProfile
	): ReaderPlayLikeCurlRasterAdapter<ReaderPlayLikeCurlRasterImage>? {
		if (rasterAdapterOwners.size == rasterAdapterOwners.ownerLimit) {
			logActivationState("refresh-gated", "adapter-owner-capacity")
			return null
		}
		val adapter = createRasterAdapter(profile)
		check(rasterAdapterOwners.tryAdd(adapter)) {
			"Raster adapter owner capacity changed during main-thread admission"
		}
		onOwnershipMutated()
		rasterAdapter = adapter
		return adapter
	}

	private fun isAwaitingAuthoritativeRelocation(): Boolean =
		relocationQueue.hasInFlightHead() && currentWebViewOrdinal != currentOrdinal

	private fun refreshPreparedDeck(planRetryAttempt: Int = 0) {
		val gate = when {
			!enabled -> "disabled"
			!attached -> "host-detached"
			destroyed -> "destroyed"
			settlementMutationFence.deferRefreshIfBlocked(activeGestureId) ->
				"gesture-deck-mutation"
			isAwaitingAuthoritativeRelocation() -> "awaiting-authoritative-relocation"
			!capabilitiesAvailable -> "capabilities-unavailable"
			!authoritativeLocationReady -> "authoritative-location-unavailable"
			preparationPhase == ReaderPagePreparationPhase.Preparing -> "preparation-in-progress"
			else -> null
		}
		if (gate != null) {
			logActivationState("refresh-gated", gate)
			return
		}
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
		if (webView == null) {
			logActivationState("refresh-gated", "webview-unavailable")
			return
		}
		val expectedCenterOrdinal = currentOrdinal
		val expectedTurnVersion = committedTurnVersion
		val request = ++requestGeneration
		val centerExpression = expectedCenterOrdinal.toString()
		logActivationState("refresh-requested", "request=$request center=$centerExpression")
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(" +
				"$centerExpression) ?? null)"
		) { encoded ->
			if (!isRequestActive(request, webView)) {
				logActivationState("refresh-gated", "stale-request=$request")
				return@evaluateJavascript
			}
			if (settlementMutationFence.deferRefreshIfBlocked(activeGestureId)) {
				logActivationState("refresh-gated", "gesture-deck-mutation-after-plan")
				return@evaluateJavascript
			}
			if (isAwaitingAuthoritativeRelocation()) {
				logActivationState("refresh-gated", "relocation-drift-after-plan")
				return@evaluateJavascript
			}
			if (
				committedTurnVersion != expectedTurnVersion ||
				currentOrdinal != expectedCenterOrdinal
			) {
				logActivationState("refresh-gated", "stale-turn-request=$request")
				return@evaluateJavascript
			}
			if (preparationPhase == ReaderPagePreparationPhase.Preparing) {
				logActivationState("refresh-gated", "preparation-in-progress-after-plan")
				return@evaluateJavascript
			}
			val plan = readerPageRasterPreparationPlan(encoded)
			if (plan == null) {
				logActivationState(
					"refresh-gated",
					"preparation-plan-unavailable attempt=$planRetryAttempt"
				)
				if (planRetryAttempt == 0) {
					webView.postVisualStateCallback(
						request,
						object : WebView.VisualStateCallback() {
							override fun onComplete(requestId: Long) {
								if (isRequestActive(request, webView)) {
									refreshPreparedDeck(planRetryAttempt = 1)
								}
							}
						}
					)
				} else {
					onProfileBootstrapFailed()
				}
				return@evaluateJavascript
			}
			currentOrdinal = plan.centerPageIndex
			val orientation = if (plan.layoutMode == "spread") {
				ReaderPlayLikeCurlOrientation.Landscape
			} else {
				ReaderPlayLikeCurlOrientation.Portrait
			}
			val profile = ReaderPlayLikeCurlRasterProfile(
				sourceIdentity = "${webView.url.orEmpty()}#$snapshotKey",
				orientation = orientation,
				quality = bitmapQuality,
				pageCount = plan.pageCount,
				readerDirection = plan.readerDirection,
				spreadAnchorParity = if (orientation == ReaderPlayLikeCurlOrientation.Landscape) {
					Math.floorMod(plan.centerPageIndex, 2)
				} else {
					0
				},
				rasterGeneration = bundleSource.currentGeneration()
			)
			prepareProfile(request, profile, plan.centerPageIndex)
		}
	}

	private fun gateForDecodedWorkingSetRefill(
		profile: ReaderPlayLikeCurlRasterProfile,
		destinationWindow: List<Int>
	) {
		val pages = activePages
		if (
			pages?.profile == profile &&
			pages.deck.pageIndices.containsAll(destinationWindow)
		) {
			return
		}
		updateReadiness(
			interaction = ReaderPageInteractionState.RefillingWorkingSet,
			reason = "decoded-refill-required:$currentOrdinal"
		)
	}

	private fun reserveNextDecodedWorkingSet() {
		if (
			activeGestureId != null ||
			decodedWorkingSetPrefetch != null ||
			!enabled ||
			!attached ||
			destroyed
		) {
			return
		}
		val profile = requestedProfile ?: return
		val pages = activePages?.takeIf { prepared -> prepared.profile == profile } ?: return
		val targetOrdinal = readerPlayLikeCurlSettlementTargetOrdinal(
			orientation = profile.orientation,
			currentOrdinal = currentOrdinal,
			pageCount = profile.pageCount,
			pageChange = PageChange.NEXT,
			readerDirection = profile.readerDirection,
			spreadAnchorParity = profile.spreadAnchorParity
		) ?: return
		if (pages.deck.pageIndices.containsAll(profile.preparedPageIndices(targetOrdinal))) return
		startDecodedWorkingSetPrefetch(null, targetOrdinal)
	}

	private fun prefetchDecodedWorkingSet(gestureId: Long, targetOrdinal: Int) {
		val existing = decodedWorkingSetPrefetch
		if (
			existing != null &&
			activeGestureId == gestureId &&
			!existing.committed &&
			existing.gestureId == null &&
			existing.preparation.isActive &&
			existing.sourceOrdinal == currentOrdinal &&
			existing.targetOrdinal == targetOrdinal &&
			existing.publicationFence.isCurrent()
		) {
			existing.gestureId = gestureId
			logActivationState(
				event = "decoded-prefetch-attached",
				detail = "gestureId=$gestureId target=$targetOrdinal"
			)
			return
		}
		discardDecodedWorkingSetPrefetch("superseded")
		startDecodedWorkingSetPrefetch(gestureId, targetOrdinal)
	}

	private fun startDecodedWorkingSetPrefetch(gestureId: Long?, targetOrdinal: Int) {
		val profile = requestedProfile ?: return
		val adapter = rasterAdapter ?: return
		val pages = activePages ?: return
		val foliateSessionId = currentFoliateSessionId ?: return
		if (
			activeGestureId != gestureId ||
			pages.profile != profile ||
			targetOrdinal == currentOrdinal
		) {
			return
		}
		val pageIndices = profile.preparedPageIndices(targetOrdinal)
		if (pages.deck.pageIndices.containsAll(pageIndices)) return
		val prefetch = PendingDecodedWorkingSetPrefetch(
			gestureId = gestureId,
			sourceOrdinal = currentOrdinal,
			targetOrdinal = targetOrdinal,
			foliateSessionId = foliateSessionId,
			profile = profile,
			expectedRequestGeneration = requestGeneration,
			expectedCommittedTurnVersion = committedTurnVersion,
			sourceProtectedWindowVersion = protectedWindowVersion,
			sourceProtectedWindow = currentProtectedWindow.toList(),
			pageIndices = pageIndices
		)
		prefetch.publicationFence = ReaderPlayLikeCurlRasterPublicationFence {
			isDecodedWorkingSetPrefetchCurrent(prefetch)
		}
		prefetch.preparation = adapter.prepare(
			profile = profile,
			pageIndices = pageIndices,
			publicationFence = prefetch.publicationFence,
			missingRasterPolicy = if (gestureId == null) {
				ReaderPlayLikeCurlMissingRasterPolicy.CacheOnly
			} else {
				ReaderPlayLikeCurlMissingRasterPolicy.RequestRepair
			}
		)
		adapter.updateProtectedPageIndices(profile, prefetch.sourceProtectedWindow)
		decodedWorkingSetPrefetch = prefetch
		observeIdleDecodedWorkingSetReserve(prefetch)
		logActivationState(
			event = "decoded-prefetch-started",
			detail = "gestureId=$gestureId target=$targetOrdinal"
		)
	}

	private fun observeIdleDecodedWorkingSetReserve(
		prefetch: PendingDecodedWorkingSetPrefetch
	) {
		rasterScope.launch {
			val preparationResult = awaitRasterPreparation(prefetch.preparation)
			withContext(Dispatchers.Main.immediate) {
				val deck = preparationResult.getOrNull()
				if (decodedWorkingSetPrefetch !== prefetch) {
					if (!prefetch.transferredToRefill) deck?.close()
					return@withContext
				}
				if (prefetch.gestureId != null || prefetch.committed) return@withContext
				decodedWorkingSetPrefetch = null
				prefetch.publicationAllowed = false
				deck?.close()
				rasterAdapter?.updateProtectedPageIndices(
					prefetch.profile,
					currentProtectedWindow
				)
				logActivationState(
					event = "decoded-reserve-completed",
					detail = "target=${prefetch.targetOrdinal} available=${deck != null}"
				)
			}
		}
	}

	private fun commitDecodedWorkingSetPrefetch(
		gestureId: Long,
		targetOrdinal: Int,
		destinationWindow: List<Int>
	) {
		val prefetch = decodedWorkingSetPrefetch ?: return
		if (
			prefetch.gestureId != gestureId ||
			prefetch.targetOrdinal != targetOrdinal ||
			prefetch.pageIndices != destinationWindow ||
			prefetch.committed
		) {
			discardDecodedWorkingSetPrefetch("commit-mismatch", gestureId)
			return
		}
		prefetch.committed = true
		if (!prefetch.publicationFence.isCurrent()) {
			discardDecodedWorkingSetPrefetch("commit-fence-expired", gestureId)
		}
	}

	private fun takeCommittedDecodedWorkingSetPrefetch(
		profile: ReaderPlayLikeCurlRasterProfile,
		centerOrdinal: Int,
		pageIndices: List<Int>
	): PendingDecodedWorkingSetPrefetch? {
		val prefetch = decodedWorkingSetPrefetch ?: return null
		if (
			!prefetch.committed ||
			prefetch.profile != profile ||
			prefetch.targetOrdinal != centerOrdinal ||
			prefetch.pageIndices != pageIndices ||
			!prefetch.publicationFence.isCurrent()
		) {
			discardDecodedWorkingSetPrefetch("refill-mismatch")
			return null
		}
		prefetch.transferredToRefill = true
		decodedWorkingSetPrefetch = null
		return prefetch
	}

	private fun discardDecodedWorkingSetPrefetch(
		reason: String,
		gestureId: Long? = null
	) {
		val prefetch = decodedWorkingSetPrefetch ?: return
		if (gestureId != null && prefetch.gestureId != gestureId) return
		decodedWorkingSetPrefetch = null
		prefetch.publicationAllowed = false
		prefetch.preparation.cancel()
		rasterScope.launch(start = CoroutineStart.UNDISPATCHED) {
			val deck = runCatching { prefetch.preparation.await() }.getOrNull()
			deck?.close()
		}
		logActivationState(
			event = "decoded-prefetch-discarded",
			detail = "gestureId=${prefetch.gestureId} reason=$reason"
		)
	}

	private fun isDecodedWorkingSetPrefetchCurrent(
		prefetch: PendingDecodedWorkingSetPrefetch
	): Boolean {
		if (
			!prefetch.publicationAllowed ||
			destroyed ||
			!enabled ||
			currentFoliateSessionId != prefetch.foliateSessionId ||
			requestedProfile != prefetch.profile ||
			bundleSource.currentGeneration() != prefetch.profile.rasterGeneration
		) {
			return false
		}
		return if (!prefetch.committed) {
			requestGeneration == prefetch.expectedRequestGeneration &&
				(prefetch.gestureId == null || activeGestureId == prefetch.gestureId) &&
				currentOrdinal == prefetch.sourceOrdinal &&
				committedTurnVersion == prefetch.expectedCommittedTurnVersion &&
				protectedWindowVersion == prefetch.sourceProtectedWindowVersion &&
				currentProtectedWindow == prefetch.sourceProtectedWindow
		} else {
			currentOrdinal == prefetch.targetOrdinal &&
				committedTurnVersion ==
					Math.incrementExact(prefetch.expectedCommittedTurnVersion) &&
				currentProtectedWindow == prefetch.pageIndices
		}
	}

	private fun refillDecodedWorkingSet(centerOrdinal: Int, reason: String) {
		val profile = requestedProfile ?: return
		val adapter = rasterAdapter ?: return
		val pageIndices = profile.preparedPageIndices(centerOrdinal)
		publishProtectedWindow(pageIndices)
		val pages = activePages
		if (
			pages?.profile == profile &&
			pages.centerOrdinal == centerOrdinal &&
			pages.deck.pageIndices.containsAll(pageIndices)
		) {
			discardDecodedWorkingSetPrefetch("refill-window-ready")
			return
		}
		if (decodedRefillCenterOrdinal == centerOrdinal) {
			discardDecodedWorkingSetPrefetch("refill-already-active")
			return
		}
		val prefetch = takeCommittedDecodedWorkingSetPrefetch(
			profile = profile,
			centerOrdinal = centerOrdinal,
			pageIndices = pageIndices
		)
		val publicationFence = prefetch?.publicationFence ?: rasterPublicationFence(
			profile = profile,
			centerOrdinal = centerOrdinal,
			protectedWindow = pageIndices,
			expectedRequestGeneration = requestGeneration
		)
		deferredDecodedRefillCenterOrdinal = null
		val refill = ++decodedRefillGeneration
		decodedRefillCenterOrdinal = centerOrdinal
		if (hasPreparedActiveDeckOwnership()) {
			updateReadiness(
				interaction = ReaderPageInteractionState.RefillingWorkingSet,
				reason = "decoded-refill-started:$refill"
			)
		}
		val startedAtNanos = System.nanoTime()
		logActivationState(
			event = "decoded-refill-started",
			detail = "refill=$refill center=$centerOrdinal reason=$reason " +
				"pages=${pageIndices.joinToString(",")}"
		)
		val preparation = prefetch?.preparation ?: adapter.prepare(
			profile = profile,
			pageIndices = pageIndices,
			publicationFence = publicationFence
		)
		teardownScope.launch {
			val preparationResult = awaitRasterPreparation(preparation)
			withContext(NonCancellable + Dispatchers.Main.immediate) {
				val failure = preparationResult.exceptionOrNull()
				if (failure != null) {
					prefetch?.publicationAllowed = false
					if (refill == decodedRefillGeneration) {
						decodedRefillCenterOrdinal = null
						Logger.e(
							ReaderPlayLikeCurlFoliateControllerTag,
							"Decoded refill failed " +
								"failureClass=${failure::class.simpleName ?: "unknown"}"
						)
						if (activeDeckGenerationId != null) {
							updateReadiness(
								interaction = preparedInteractionState(),
								reason = "decoded-refill-failed-fallback:$refill"
							)
						}
						if (activeDeckGenerationId == null) {
							updateReadiness(
								textureDeck = ReaderTextureDeckState.Failed,
								interaction = ReaderPageInteractionState.Failed,
								reason = "decoded-refill-failed:$refill"
							)
						}
					}
					return@withContext
				}
				val deck = preparationResult.getOrNull()
				if (
					deck == null ||
					refill != decodedRefillGeneration ||
					!enabled ||
					destroyed ||
					requestedProfile != profile ||
					!publicationFence.isCurrent()
				) {
					prefetch?.publicationAllowed = false
					deck?.close()
					val isCurrentRefill = refill == decodedRefillGeneration
					val waitsForPreparation =
						deck == null &&
							isCurrentRefill &&
							activeDeckGenerationId != null &&
							preparationPhase == ReaderPagePreparationPhase.Preparing &&
							currentOrdinal == centerOrdinal
					val needsCurrentWindowRetry =
						isCurrentRefill &&
							activeDeckGenerationId != null &&
							enabled &&
							!destroyed &&
							requestedProfile == profile &&
							currentOrdinal == centerOrdinal &&
							!hasDecodedWorkingSetForCurrentOrdinal()
					if (isCurrentRefill) decodedRefillCenterOrdinal = null
					if (waitsForPreparation) {
						deferredDecodedRefillCenterOrdinal = centerOrdinal
						updateReadiness(
							interaction = ReaderPageInteractionState.RefillingWorkingSet,
							reason = "decoded-refill-awaiting-preparation:$refill"
						)
					} else if (deck == null && needsCurrentWindowRetry) {
						deferredDecodedRefillCenterOrdinal = centerOrdinal
						updateReadiness(
							interaction = ReaderPageInteractionState.RefillingWorkingSet,
							reason = "decoded-refill-awaiting-raster:$refill"
						)
					} else if (deck != null && needsCurrentWindowRetry) {
						scheduleDecodedWorkingSetRefillRetry(
							profile = profile,
							centerOrdinal = centerOrdinal,
							refill = refill
						)
					} else if (isCurrentRefill && activeDeckGenerationId != null) {
						updateReadiness(
							interaction = preparedInteractionState(),
							reason = "decoded-refill-deferred-fallback:$refill"
						)
					}
					if (deck == null && isCurrentRefill) {
						logActivationState(
							event = "decoded-refill-deferred",
							detail = "refill=$refill center=$centerOrdinal reason=$reason"
						)
					}
					return@withContext
				}
				prefetch?.publicationAllowed = false
				decodedRefillCenterOrdinal = null
				deferredDecodedRefillCenterOrdinal = null
				activePages?.let { previous ->
					if (previous.generations.isEmpty()) {
						previous.obsolete = true
						closeIfUnused(previous)
					}
				}
				val replacement = PreparedPages(profile, deck, centerOrdinal)
				preparedPageSets += replacement
				activePages = replacement
				updateReadiness(
					interaction = preparedInteractionState(),
					reason = "decoded-refill-completed:$refill"
				)
				logActivationState(
					event = "decoded-refill-completed",
					detail = "refill=$refill center=$centerOrdinal reason=$reason " +
						"pages=${pageIndices.joinToString(",")} " +
						"elapsedMillis=${elapsedMillis(startedAtNanos)}"
				)
				if (activeDeckGenerationId == null) {
					currentOrdinal = centerOrdinal.coerceIn(0, profile.pageCount - 1)
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Preparing,
						interaction = blockingPreparationState(),
						reason = "decoded-repair-submitting:$refill"
					)
					submitLibraryDeck(
						pages = replacement,
						ordinal = currentOrdinal,
						role = ReaderDeckSubmissionRole.Active
					)
				}
				reserveNextDecodedWorkingSet()
			}
		}
	}

	private fun scheduleDecodedWorkingSetRefillRetry(
		profile: ReaderPlayLikeCurlRasterProfile,
		centerOrdinal: Int,
		refill: Long
	) {
		deferredDecodedRefillCenterOrdinal = centerOrdinal
		updateReadiness(
			interaction = ReaderPageInteractionState.RefillingWorkingSet,
			reason = "decoded-refill-fence-retry-scheduled:$refill"
		)
		val posted = mainHandler.post {
			if (
				decodedRefillGeneration != refill ||
				deferredDecodedRefillCenterOrdinal != centerOrdinal
			) {
				return@post
			}
			deferredDecodedRefillCenterOrdinal = null
			if (
				!destroyed &&
				enabled &&
				requestedProfile == profile &&
				currentOrdinal == centerOrdinal &&
				decodedRefillCenterOrdinal == null &&
				!hasDecodedWorkingSetForCurrentOrdinal()
			) {
				refillDecodedWorkingSet(
					centerOrdinal,
					"decoded-refill-fence-retry:$refill"
				)
			}
		}
		if (
			!posted &&
			decodedRefillGeneration == refill &&
			deferredDecodedRefillCenterOrdinal == centerOrdinal
		) {
			deferredDecodedRefillCenterOrdinal = null
		}
	}

	private fun ReaderPlayLikeCurlRasterProfile.preparedPageIndices(
		centerOrdinal: Int
	): List<Int> = readerPlayLikeCurlPreparedPageIndices(
		orientation = orientation,
		currentOrdinal = centerOrdinal,
		pageCount = pageCount,
		readerDirection = readerDirection,
		spreadAnchorParity = spreadAnchorParity
	)

	private fun ReaderPlayLikeCurlRasterProfile.pageRequest(
		logicalOrdinal: Int
	): ReaderPlayLikeCurlFoliatePageRequest = readerPlayLikeCurlFoliatePageRequest(
		orientation = orientation,
		readerDirection = readerDirection,
		logicalOrdinal = logicalOrdinal,
		pageCount = pageCount,
		spreadAnchorParity = spreadAnchorParity
	)

	private fun ReaderPlayLikeCurlRasterProfile.transitionKind(): ReaderPageTurnTransitionKind =
		when (orientation) {
			ReaderPlayLikeCurlOrientation.Portrait ->
				ReaderPageTurnTransitionKind.PortraitSlide
			ReaderPlayLikeCurlOrientation.Landscape ->
				ReaderPageTurnTransitionKind.LandscapeSpreadSlide
		}

	private fun rasterPublicationFence(
		profile: ReaderPlayLikeCurlRasterProfile,
		centerOrdinal: Int,
		protectedWindow: List<Int>,
		expectedRequestGeneration: Long
	): ReaderPlayLikeCurlRasterPublicationFence {
		val expectedTurnVersion = committedTurnVersion
		val expectedWindowVersion = protectedWindowVersion
		val expectedWindow = protectedWindow.toList()
		return ReaderPlayLikeCurlRasterPublicationFence {
			!destroyed &&
				enabled &&
				requestedProfile == profile &&
				bundleSource.currentGeneration() == profile.rasterGeneration &&
				requestGeneration == expectedRequestGeneration &&
				currentOrdinal == centerOrdinal &&
				committedTurnVersion == expectedTurnVersion &&
				protectedWindowVersion == expectedWindowVersion &&
				currentProtectedWindow == expectedWindow
		}
	}

	private fun publishProtectedWindow(window: List<Int>): Long {
		val immutableWindow = window.toList()
		val version = if (currentProtectedWindow != immutableWindow) {
			Math.incrementExact(protectedWindowVersion)
		} else {
			protectedWindowVersion
		}
		applyProtectedWindow(immutableWindow, version)
		return protectedWindowVersion
	}

	private fun applyProtectedWindow(window: List<Int>, version: Long) {
		currentProtectedWindow = window
		protectedWindowVersion = version
		requestedProfile?.let { profile ->
			rasterAdapter?.updateProtectedPageIndices(profile, window)
		}
		publishProtectedRasterOrdinals(window)
	}

	private fun publishProtectedRasterOrdinals(logicalOrdinals: List<Int>) {
		val profile = requestedProfile
		val sourcePageIndices = if (profile == null) {
			emptySet()
		} else {
			readerPlayLikeCurlProtectedSourcePageIndices(profile, logicalOrdinals)
		}
		bundleSource.protectDecodedPageIndices(sourcePageIndices)
		onProtectedRasterSourcePageIndicesChanged(sourcePageIndices)
	}

	private fun isLogicalRasterDecoded(logicalOrdinal: Int): Boolean {
		val profile = requestedProfile ?: return false
		if (rasterAdapter?.hasDecoded(profile, logicalOrdinal) == true) return true
		val request = profile.pageRequest(logicalOrdinal)
		return bundleSource.hasSnapshot(
			request.sourcePageIndex,
			profile.transitionKind()
		)
	}

	private fun requestLogicalRasterRepair(logicalOrdinal: Int) {
		val profile = requestedProfile ?: return
		requestRasterRepair(profile.pageRequest(logicalOrdinal).sourcePageIndex, profile)
	}

	private fun schedulePersistentRefill(
		direction: ReaderPageTurnDirection,
		destinationOrdinal: Int,
		expectedTurnVersion: Long
	) {
		val expectedProfile = requestedProfile ?: return
		val expectedGeneration = requestGeneration
		rasterScope.launch(Dispatchers.Main.immediate) {
			persistentRefillCoordinator.onTurnCommitted(
				direction = direction,
				destinationOrdinal = destinationOrdinal,
				committedTurnVersion = expectedTurnVersion,
				isTurnStillCurrent = {
					!destroyed &&
						requestedProfile == expectedProfile &&
						requestGeneration == expectedGeneration &&
						currentOrdinal == destinationOrdinal &&
						committedTurnVersion == expectedTurnVersion
				},
				isStillCurrent = { fence ->
					!destroyed &&
						requestedProfile == expectedProfile &&
						requestGeneration == expectedGeneration &&
						currentOrdinal == fence.destinationOrdinal &&
						committedTurnVersion == fence.committedTurnVersion &&
						protectedWindowVersion == fence.protectedWindowVersion &&
						currentProtectedWindow == fence.protectedWindow
				}
			)
			if (
				!destroyed &&
				requestedProfile == expectedProfile &&
				requestGeneration == expectedGeneration &&
				currentOrdinal == destinationOrdinal &&
				committedTurnVersion == expectedTurnVersion
			) {
				refillDecodedWorkingSet(
					destinationOrdinal,
					"persistent-refill-completed"
				)
			}
		}
	}

	private fun requestRasterRepair(
		sourcePageIndex: Int,
		profile: ReaderPlayLikeCurlRasterProfile,
		attempt: Int = 0,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) {
		val refillCenter = currentOrdinal
		val recipient = ReaderPlayLikeCurlRasterRepairRecipient(
			fence = ReaderPlayLikeCurlRasterRepairFence(
				profile = profile,
				destinationOrdinal = currentOrdinal,
				committedTurnVersion = committedTurnVersion,
				protectedWindowVersion = protectedWindowVersion,
				protectedWindow = currentProtectedWindow.toList()
			),
			attempt = attempt,
			qaFaultCorrelation = qaFaultCorrelation
		)
		val operationToken = rasterRepairRequests.register(
			profile,
			sourcePageIndex,
			recipient
		)
		qaFaultCorrelation?.let { correlation ->
			onAttachRasterRepairQaFault(sourcePageIndex, correlation)
		}
		if (operationToken == null) return
		logActivationState(
			event = "page-repair-requested",
			detail = "source=$sourcePageIndex center=$refillCenter " +
				"profileGeneration=${profile.rasterGeneration}"
		)
		onRequestRasterRepair(sourcePageIndex) { result ->
			host.post {
				val recipients = rasterRepairRequests.complete(
					profile,
					sourcePageIndex,
					operationToken
				) ?: return@post
				val currentRecipient = if (destroyed || !enabled) {
					null
				} else {
					recipients.lastOrNull { candidate ->
						candidate.fence.matches(
							profile = requestedProfile,
							destinationOrdinal = currentOrdinal,
							committedTurnVersion = committedTurnVersion,
							protectedWindowVersion = protectedWindowVersion,
							protectedWindow = currentProtectedWindow
						)
					}
				}
				if (result !is ReaderPageRasterRepairResult.Repaired) {
					val operationAttempt = recipients.maxOfOrNull { it.attempt } ?: attempt
					logActivationState(
						event = "page-repair-deferred",
						detail = "source=$sourcePageIndex " +
							"center=${currentRecipient?.fence?.destinationOrdinal ?: refillCenter} " +
							"result=${result::class.simpleName} attempt=$operationAttempt"
					)
					if (
						currentRecipient != null &&
						result != ReaderPageRasterRepairResult.Cancelled
					) {
						if (operationAttempt == 0) {
							requestRasterRepair(
								sourcePageIndex,
								profile,
								attempt = 1,
								qaFaultCorrelation = currentRecipient.qaFaultCorrelation
							)
						} else {
							requestPrewarmIfIdle("page-repair-failed")
						}
					}
					return@post
				}
				if (currentRecipient == null) {
					result.diagnosticOperation?.let { operation ->
						diagnostics?.repair(
							operation,
							ReaderPageRepairDiagnosticState.Cancelled
						)
					}
					logActivationState(
						event = "page-repair-deferred",
						detail = "source=$sourcePageIndex " +
							"center=${recipients.lastOrNull()?.fence?.destinationOrdinal ?: refillCenter} " +
							"result=stale-repaired-window"
					)
					return@post
				}
				val destinationOrdinal = currentRecipient.fence.destinationOrdinal
				if (!deckRecoveryCoordinator.accept(result)) {
					logActivationState(
						event = "page-repair-deferred",
						detail = "source=$sourcePageIndex center=$destinationOrdinal " +
							"result=stale-repair-result attempt=${currentRecipient.attempt}"
					)
					if (currentRecipient.attempt == 0) {
						requestRasterRepair(
							sourcePageIndex,
							profile,
							attempt = 1
						)
					} else {
						requestPrewarmIfIdle("page-repair-stale-result")
					}
					return@post
				}
				logActivationState(
					event = "page-repair-completed",
					detail = "source=$sourcePageIndex center=$destinationOrdinal"
				)
			}
		}
	}

	private fun prepareProfile(
		request: Long,
		profile: ReaderPlayLikeCurlRasterProfile,
		centerOrdinal: Int
	) {
		val pageIndices = profile.preparedPageIndices(centerOrdinal)
		if (requestedProfile != profile) {
			activePages?.let { pages ->
				pages.obsolete = true
				closeIfUnused(pages)
			}
			activePages = null
			rasterAdapter?.let(::retireRasterAdapter)
			rasterAdapter = null
			foliateRasterLoader = null
			publishProtectedWindow(emptyList())
			requestedProfile = profile
			publishRasterProfileEpoch(profile)
			publishProtectedWindow(pageIndices)
		} else {
			publishProtectedWindow(pageIndices)
		}
		val adapter = rasterAdapter
			?: createRasterAdapterOrDefer(profile)
			?: run {
				updateReadiness(
					interaction = ReaderPageInteractionState.BlockingProfileRegeneration,
					reason = "adapter-owner-capacity"
				)
				return
			}
		livePresentationRecoveryRequest.claimPreparation()
		val publicationFence = rasterPublicationFence(
			profile = profile,
			centerOrdinal = centerOrdinal,
			protectedWindow = pageIndices,
			expectedRequestGeneration = request
		)
		val startedAtNanos = System.nanoTime()
		logActivationState(
			event = "deck-load-started",
			detail = "request=$request center=$centerOrdinal pages=${pageIndices.joinToString(",")}"
		)
		val preparation = adapter.prepare(
			profile = profile,
			pageIndices = pageIndices,
			publicationFence = publicationFence,
			onProgress = { progress ->
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"deck-load-progress request=$request center=$centerOrdinal " +
						"completed=${progress.completed}/${progress.total} " +
						"elapsedMillis=${elapsedMillis(startedAtNanos)}"
				)
			}
		)
		rasterScope.launch {
			val preparationResult = awaitRasterPreparation(preparation)
			withContext(Dispatchers.Main.immediate) {
				if (settlementMutationFence.deferRefreshIfBlocked(activeGestureId)) {
					preparationResult.getOrNull()?.close()
					logActivationState(
						"refresh-gated",
						"gesture-deck-mutation-before-publication:$request"
					)
					return@withContext
				}
				if (isAwaitingAuthoritativeRelocation()) {
					preparationResult.getOrNull()?.close()
					logActivationState(
						"refresh-gated",
						"relocation-drift-before-publication:$request"
					)
					return@withContext
				}
				val failure = preparationResult.exceptionOrNull()
				if (failure != null) {
					if (
						request == requestGeneration &&
							enabled &&
							!destroyed &&
							publicationFence.isCurrent()
					) {
						updateReadiness(
							textureDeck = ReaderTextureDeckState.Failed,
							interaction = ReaderPageInteractionState.Failed,
							reason = "deck-load-exception:$request"
						)
						Logger.e(
							ReaderPlayLikeCurlFoliateControllerTag,
							"Raster deck load failed " +
								"failureClass=${failure::class.simpleName ?: "unknown"}"
						)
					}
					return@withContext
				}
				val deck = preparationResult.getOrNull()
				if (deck == null) {
					if (
						request == requestGeneration &&
						enabled &&
						!destroyed &&
						publicationFence.isCurrent()
					) {
						if (readinessState.textureDeck == ReaderTextureDeckState.Ready) {
							updateReadiness(
								interaction = ReaderPageInteractionState.BackgroundPrefetch,
								reason = "deck-load-deferred:$request"
							)
						} else {
							updateReadiness(
								textureDeck = ReaderTextureDeckState.Failed,
								interaction = ReaderPageInteractionState.Failed,
								reason = "deck-load-failed:$request"
							)
						}
						logActivationState(
							event = "deck-load-failed",
							detail = "request=$request center=$centerOrdinal " +
								"pages=${pageIndices.joinToString(",")} " +
								"elapsedMillis=${elapsedMillis(startedAtNanos)}"
						)
						logActivationState(
							"refresh-gated",
							"raster-deck-unavailable phase=$preparationPhase"
						)
						if (rasterRepairRequests.isEmpty()) {
							requestPrewarmIfIdle("raster-deck-unavailable")
						} else {
							logActivationState(
								event = "refresh-gated",
								detail = "targeted-page-repair-active"
							)
						}
					}
					return@withContext
				}
			if (
					request != requestGeneration ||
					!enabled ||
					destroyed ||
					requestedProfile != profile ||
					!publicationFence.isCurrent()
				) {
					deck.close()
					return@withContext
				}
				logActivationState(
					event = "deck-load-completed",
					detail = "request=$request center=$centerOrdinal " +
						"pages=${pageIndices.joinToString(",")} " +
						"elapsedMillis=${elapsedMillis(startedAtNanos)}"
				)
				activePages?.let { previous ->
					previous.obsolete = true
					closeIfUnused(previous)
				}
				val pages = PreparedPages(profile, deck, centerOrdinal)
				preparedPageSets += pages
				activePages = pages
				currentOrdinal = centerOrdinal.coerceIn(0, profile.pageCount - 1)
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Preparing,
					interaction = blockingPreparationState(),
					reason = "deck-submitting:$request"
				)
				submitLibraryDeck(
					pages = pages,
					ordinal = currentOrdinal,
					role = ReaderDeckSubmissionRole.Active
				)
				reserveNextDecodedWorkingSet()
			}
		}
	}

	override fun isCurrentRepairWindow(
		repairedPageIndices: Set<Int>,
		centerOrdinal: Int,
		rasterEpoch: Long
	): Boolean {
		val profile = requestedProfile ?: return false
		if (
			destroyed ||
			!enabled ||
			profile.rasterGeneration != rasterEpoch ||
			bundleSource.currentGeneration() != rasterEpoch
		) {
			return false
		}
		val expectedSourceCenter = profile.pageRequest(currentOrdinal).sourcePageIndex
		return readerPlayLikeCurlRepairTargetMatches(
			repairedCenterOrdinal = centerOrdinal,
			expectedSourceCenter = expectedSourceCenter
		)
	}

	override fun hasUsablePreparedActiveDeck(): Boolean =
		hasPreparedActiveDeckOwnership() &&
			readinessState.textureDeck == ReaderTextureDeckState.Ready

	private fun hasPreparedActiveDeckOwnership(): Boolean {
		val generationId = activeDeckGenerationId ?: return false
		val pages = generationOwners[generationId] ?: return false
		return generationRoles[generationId] == ReaderDeckSubmissionRole.Active &&
			generationId in preparedDeckGenerations &&
			!pages.obsolete &&
			pages.profile == requestedProfile
	}

	override fun requestRecoveredDeckBuild(
		requestId: Long,
		repairedPageIndices: Set<Int>,
		centerOrdinal: Int,
		rasterEpoch: Long,
		onBuilt: (ReaderPageRecoveredDeckBuildResult) -> Unit
	) {
		check(isCurrentRepairWindow(repairedPageIndices, centerOrdinal, rasterEpoch)) {
			"Recovered deck build requires the current repaired window"
		}
		check(requestId !in recoveredBuildOperations) {
			"Recovered deck build request is already active"
		}
		val profile = checkNotNull(requestedProfile)
		val logicalCenter = currentOrdinal
		val logicalWindow = profile.preparedPageIndices(logicalCenter)
		val expectedRequestGeneration = requestGeneration
		val publicationFence = rasterPublicationFence(
			profile = profile,
			centerOrdinal = logicalCenter,
			protectedWindow = logicalWindow,
			expectedRequestGeneration = expectedRequestGeneration
		)
		val adapter = checkNotNull(rasterAdapter) {
			"Recovered deck build requires an active raster adapter"
		}
		val preparation = adapter.prepare(
			profile = profile,
			pageIndices = logicalWindow,
			publicationFence = publicationFence
		)
		lateinit var operation: ReaderPageRecoveredDeckBuildOperation<
			ReaderPlayLikeCurlRasterDeck<ReaderPlayLikeCurlRasterImage>
		>
		operation = ReaderPageRecoveredDeckBuildOperation(
			preparation = preparation,
			scope = rasterScope,
			publicationDispatcher = Dispatchers.Main.immediate,
			onResult = result@ { deck, resolveOwnership ->
				if (recoveredBuildOperations[requestId] !== operation) {
					deck?.close()
					resolveOwnership()
					return@result
				}
				val repairWindowCurrent = runCatching {
					publicationFence.isCurrent() &&
						isCurrentRepairWindow(
							repairedPageIndices,
							centerOrdinal,
							rasterEpoch
						)
				}.getOrDefault(false)
				recoveredBuildOperations.remove(requestId)
				if (!repairWindowCurrent) {
					deck?.close()
					resolveOwnership()
					onBuilt(ReaderPageRecoveredDeckBuildResult.Stale)
					return@result
				}
				if (deck == null) {
					resolveOwnership()
					onBuilt(
						ReaderPageRecoveredDeckBuildResult.Failed(
							"recovered-decoded-window-unavailable"
						)
					)
					return@result
				}
				val pages = PreparedPages(profile, deck, logicalCenter)
				val generationId = nextDeckGeneration++
				val libraryDeck = runCatching {
					buildLibraryDeck(pages, logicalCenter, generationId)
				}.getOrElse { failure ->
					pages.obsolete = true
					deck.close()
					resolveOwnership()
					Logger.e(
						ReaderPlayLikeCurlFoliateControllerTag,
						"Failed to build recovered PlayLikeCurl deck",
						failure
					)
					onBuilt(
						ReaderPageRecoveredDeckBuildResult.Failed(
							"recovered-library-deck-build-failed"
						)
					)
					return@result
				}
				pages.generations += generationId
				preparedPageSets += pages
				generationOwners[generationId] = pages
				builtRecoveredDecks[generationId] = BuiltRecoveredDeck(
					pages = pages,
					ordinal = logicalCenter,
					deck = libraryDeck
				)
				resolveOwnership()
				onBuilt(ReaderPageRecoveredDeckBuildResult.Built(generationId))
			},
			onFailure = failed@ { failure ->
				if (recoveredBuildOperations[requestId] !== operation) return@failed
				recoveredBuildOperations.remove(requestId)
				Logger.e(
					ReaderPlayLikeCurlFoliateControllerTag,
					"Failed to hydrate recovered PlayLikeCurl deck",
					failure
				)
				onBuilt(
					ReaderPageRecoveredDeckBuildResult.Failed(
						"recovered-decoded-window-failed"
					)
				)
			}
		)
		recoveredBuildOperations[requestId] = operation
		operation.start()
	}

	override fun cancelRecoveredDeckBuild(requestId: Long) {
		val operation = recoveredBuildOperations.remove(requestId) ?: return
		operation.cancel()
	}

	override fun currentRecoveredDeckRole(): ReaderDeckSubmissionRole {
		val operation = when (val state = deckRecoveryCoordinator.state) {
			is ReaderPageDeckRecoveryState.WaitingForBuild -> state.diagnosticOperation
			is ReaderPageDeckRecoveryState.WaitingForSubmissionCapacity ->
				state.diagnosticOperation
			else -> null
		}
		val repairAttemptId = operation?.attempt
		if (
			repairAttemptId != null &&
			repairAttemptId !in repairQaFaultCorrelations
		) {
			qaFaultRegistry?.consumeAndApply(
				ReaderPageQaFault.ForceRepairWithoutPreparedDeck,
				ReaderPageQaFaultOperationContext(
					repairAttemptId = repairAttemptId
				)
			)?.let { applied ->
				repairQaFaultCorrelations[repairAttemptId] = applied.correlation()
			}
		}
		if (
			repairAttemptId != null &&
			repairQaFaultCorrelations.containsKey(repairAttemptId)
		) {
			return ReaderDeckSubmissionRole.Active
		}
		return if (
			settlementMutationFence.hasUnreconciledSettlement ||
			surfaceView.isSettlementRunning
		) {
			ReaderDeckSubmissionRole.Pending
		} else {
			ReaderDeckSubmissionRole.Active
		}
	}

	override fun submitRecoveredDeck(
		generationId: Long,
		role: ReaderDeckSubmissionRole
	): ReaderPageRecoveredDeckSubmissionResult {
		val built = checkNotNull(builtRecoveredDecks[generationId]) {
			"Recovered deck generation is not owned by the build registry"
		}
		check(!built.pages.obsolete && generationOwners[generationId] === built.pages) {
			"Recovered deck generation is obsolete"
		}
		if (settlementMutationFence.blocksExternalDeckMutation(activeGestureId)) {
			return ReaderPageRecoveredDeckSubmissionResult.AwaitingRendererCapacity
		}
		val repairDiagnostic =
			(deckRecoveryCoordinator.state as?
				ReaderPageDeckRecoveryState.WaitingForPreparation)
				?.takeIf { state -> state.generationId == generationId }
				?.diagnosticOperation
		deckDiagnosticTracker?.begin(
			generation = generationId,
			repairAttempt = repairDiagnostic?.attempt,
			role = role,
			qaFaultCorrelation = repairDiagnostic?.let(::qaFaultCorrelationForRepair)
		)
		updateSurfaceBounds()
		setSurfaceReadingDirection(built.pages.profile)
		logActivationState(
			event = "recovered-deck-submitted",
			detail = "generation=$generationId ordinal=${built.ordinal} role=$role"
		)
		var ownershipTransferred = false
		val result = try {
			submissionCallbackFence.submit(generationId) {
				surfaceView.submitDeckWithResult(built.deck) {
					ownershipTransferred = true
					acceptRecoveredDeckOwnership(generationId, role)
					repairDiagnostic?.let { operation ->
						diagnostics?.repair(
							operation,
							ReaderPageRepairDiagnosticState.Submitted,
							qaFaultCorrelation = qaFaultCorrelationForRepair(operation)
						)
					}
				}
			}
		} catch (failure: Throwable) {
			deckDiagnosticTracker?.cancel(generationId)
			if (ownershipTransferred) {
				rollbackAcceptedRecoveredDeck(generationId, role, failure)
			}
			throw failure
		}
		if (result.status == PageSurfaceDeckSubmissionResult.Status.REJECTED) {
			deckDiagnosticTracker?.cancel(generationId)
			return if (result.rejectionReason == DeckRejectionReason.RESOURCE_CAPACITY) {
				ReaderPageRecoveredDeckSubmissionResult.AwaitingRendererCapacity
			} else {
				ReaderPageRecoveredDeckSubmissionResult.Rejected(
					checkNotNull(result.rejectionReason).name
				)
			}
		}
		check(
			result.status == PageSurfaceDeckSubmissionResult.Status.ACCEPTED &&
				ownershipTransferred
		) {
			"Fresh recovered deck submission did not transfer renderer ownership"
		}
		deckDiagnosticTracker?.submitted(
			generation = generationId,
			active = activeDeckGenerationId,
			pending = pendingDeckGenerationId
		)
		return ReaderPageRecoveredDeckSubmissionResult.Accepted
	}

	private fun acceptRecoveredDeckOwnership(
		generationId: Long,
		role: ReaderDeckSubmissionRole
	) {
		val accepted = checkNotNull(builtRecoveredDecks.remove(generationId))
		generationRoles[generationId] = role
		recoveredDeckGenerations += generationId
		if (
			generationOwners[generationId] !== accepted.pages ||
			generationRoles[generationId] != role
		) {
			return
		}
		when (role) {
			ReaderDeckSubmissionRole.Active -> {
				activeDeckGenerationId = generationId
				notifyPreparedActiveDeckChanged(null)
				if (activePages !== accepted.pages) {
					activePages?.let { previous ->
						previous.obsolete = true
						closeIfUnused(previous)
					}
					activePages = accepted.pages
				}
			}
			ReaderDeckSubmissionRole.Pending -> {
				pendingDeckGenerationId = generationId
				pendingDeckOrdinal = accepted.ordinal
			}
		}
	}

	override fun releaseUnsubmittedRecoveredDeck(generationId: Long) {
		val built = builtRecoveredDecks.remove(generationId) ?: return
		built.pages.obsolete = true
		releaseGeneration(generationId)
	}

	private fun rollbackAcceptedRecoveredDeck(
		generationId: Long,
		role: ReaderDeckSubmissionRole,
		failure: Throwable
	) {
		try {
			tombstoneSubmittedRecoveredDeck(generationId, role)
		} catch (cleanupFailure: Throwable) {
			if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
		}
		if (role == ReaderDeckSubmissionRole.Active) {
			val strandedActive = activePages
			if (strandedActive != null && strandedActive !== generationOwners[generationId]) {
				activePages = null
				strandedActive.obsolete = true
				try {
					closeIfUnused(strandedActive)
				} catch (cleanupFailure: Throwable) {
					if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
				}
			}
		}
		try {
			surfaceView.releaseDeck(generationId)
		} catch (cleanupFailure: Throwable) {
			if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
		}
	}

	override fun cancelSubmittedRecoveredDeck(
		generationId: Long,
		role: ReaderDeckSubmissionRole
	) {
		val currentRole = generationRoles[generationId] ?: return
		if (!readerRecoveredDeckCancellationRoleMatches(role, currentRole)) return
		if (!tombstoneSubmittedRecoveredDeck(generationId, currentRole)) return
		surfaceView.releaseDeck(generationId)
	}

	private fun tombstoneSubmittedRecoveredDeck(
		generationId: Long,
		role: ReaderDeckSubmissionRole
	): Boolean {
		if (generationRoles[generationId] != role) return false
		val pages = generationOwners[generationId] ?: return false
		generationRoles.remove(generationId)
		preparedDeckGenerations -= generationId
		when (role) {
			ReaderDeckSubmissionRole.Active -> {
				if (activeDeckGenerationId == generationId) activeDeckGenerationId = null
				if (activePages === pages) {
					activePages = null
					pages.obsolete = true
				}
				notifyPreparedActiveDeckChanged(null)
			}
			ReaderDeckSubmissionRole.Pending -> {
				if (pendingDeckGenerationId == generationId) {
					pendingDeckGenerationId = null
					pendingDeckOrdinal = null
				}
			}
		}
		return true
	}

	override fun isPrepared(generationId: Long): Boolean =
		generationId in preparedDeckGenerations

	private fun publishUnownedRecoveredDeckFailure(
		generationId: Long,
		role: ReaderDeckSubmissionRole?,
		reason: RenderFailureReason
	) {
		when (role) {
			ReaderDeckSubmissionRole.Active -> updateReadiness(
				textureDeck = ReaderTextureDeckState.Failed,
				interaction = ReaderPageInteractionState.Failed,
				reason = "unowned-recovered-active-failed:$generationId:$reason"
			)
			ReaderDeckSubmissionRole.Pending -> updateReadiness(
				pendingTextureDeck = ReaderTextureDeckState.Failed,
				reason = "unowned-recovered-pending-failed:$generationId:$reason"
			)
			null -> Unit
		}
	}

	private fun qaFaultCorrelationForRepair(
		operation: ReaderPageDiagnosticOperation
	): ReaderPageQaFaultCorrelation? =
		repairQaFaultCorrelations[operation.attempt] ?: operation.qaFaultCorrelation

	private fun onDeckRecoveryStateChanged(state: ReaderPageDeckRecoveryState) {
		when (state) {
			ReaderPageDeckRecoveryState.Idle -> {
				if (hasPreparedActiveDeckOwnership()) {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Ready,
						pendingTextureDeck = ReaderTextureDeckState.Empty,
						interaction = preparedInteractionState(),
						reason = "deck-recovery-idle"
					)
				} else {
					updateReadiness(
						textureDeck = if (activeDeckGenerationId == null) {
							ReaderTextureDeckState.Empty
						} else {
							ReaderTextureDeckState.Preparing
						},
						pendingTextureDeck = ReaderTextureDeckState.Empty,
						interaction = blockingPreparationState(),
						reason = "deck-recovery-idle-without-active"
					)
				}
			}
			is ReaderPageDeckRecoveryState.WaitingForBuild -> {
				if (hasPreparedActiveDeckOwnership()) {
					updateReadiness(
						pendingTextureDeck = ReaderTextureDeckState.Preparing,
						interaction = preparedInteractionState(),
						reason = "deck-recovery-building-pending:${state.requestId}"
					)
				} else {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Preparing,
						pendingTextureDeck = ReaderTextureDeckState.Empty,
						interaction = blockingPreparationState(),
						reason = "deck-recovery-building-active:${state.requestId}"
					)
				}
			}
			is ReaderPageDeckRecoveryState.WaitingForSubmissionCapacity -> {
				if (hasPreparedActiveDeckOwnership()) {
					updateReadiness(
						pendingTextureDeck = ReaderTextureDeckState.Preparing,
						interaction = preparedInteractionState(),
						reason = "deck-recovery-waiting-capacity:${state.generationId}"
					)
				} else {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Preparing,
						pendingTextureDeck = ReaderTextureDeckState.Empty,
						interaction = blockingPreparationState(),
						reason = "deck-recovery-waiting-capacity:${state.generationId}"
					)
				}
			}
			is ReaderPageDeckRecoveryState.WaitingForPreparation -> when (state.role) {
				ReaderDeckSubmissionRole.Active -> updateReadiness(
					textureDeck = ReaderTextureDeckState.Preparing,
					pendingTextureDeck = ReaderTextureDeckState.Empty,
					interaction = blockingPreparationState(),
					reason = "deck-recovery-preparing-active:${state.generationId}"
				)
				ReaderDeckSubmissionRole.Pending -> updateReadiness(
					pendingTextureDeck = ReaderTextureDeckState.Preparing,
					interaction = preparedInteractionState(),
					reason = "deck-recovery-preparing-pending:${state.generationId}"
				)
			}
			is ReaderPageDeckRecoveryState.Ready -> {
				state.diagnosticOperation?.let { operation ->
					diagnostics?.repair(
						operation,
						ReaderPageRepairDiagnosticState.Completed,
						qaFaultCorrelation = qaFaultCorrelationForRepair(operation)
					)
					repairQaFaultCorrelations.remove(operation.attempt)
				}
				when (generationRoles[state.generationId]) {
					ReaderDeckSubmissionRole.Active -> updateReadiness(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = preparedInteractionState(),
						reason = "deck-recovery-ready-active:${state.generationId}"
					)
					ReaderDeckSubmissionRole.Pending -> updateReadiness(
						pendingTextureDeck = ReaderTextureDeckState.Ready,
						interaction = preparedInteractionState(),
						reason = "deck-recovery-ready-pending:${state.generationId}"
					)
					null -> Unit
				}
			}
			is ReaderPageDeckRecoveryState.Failed -> {
				state.diagnosticOperation?.let { operation ->
					diagnostics?.repair(
						operation,
						ReaderPageRepairDiagnosticState.Failed,
						qaFaultCorrelation = qaFaultCorrelationForRepair(operation)
					)
					repairQaFaultCorrelations.remove(operation.attempt)
				}
				if (hasPreparedActiveDeckOwnership()) {
					if (surfaceView.isSettlementRunning) {
						updateReadiness(
							pendingTextureDeck = ReaderTextureDeckState.Failed,
							reason = "deck-recovery-failed-pending:${state.reason}"
						)
					} else {
						updateReadiness(
							textureDeck = ReaderTextureDeckState.Ready,
							pendingTextureDeck = ReaderTextureDeckState.Failed,
							interaction = preparedInteractionState(),
							reason = "deck-recovery-failed-fallback:${state.reason}"
						)
					}
				} else {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Failed,
						pendingTextureDeck = ReaderTextureDeckState.Empty,
						interaction = ReaderPageInteractionState.Failed,
						reason = "deck-recovery-failed-active:${state.reason}"
					)
					finishActiveGesture(
						ReaderPageGestureTerminalOutcome.FailedRecovery,
						ReaderPageGestureTerminalDetail.RecoveryFailed
					)
				}
			}
		}
	}

	private fun buildLibraryDeck(
		pages: PreparedPages,
		ordinal: Int,
		generationId: Long
	): PageDeck<Bitmap> = readerPlayLikeCurlLibraryDeck(
		orientation = pages.profile.orientation,
		generationId = generationId,
		currentOrdinal = ordinal,
		pageCount = pages.profile.pageCount,
		readerDirection = pages.profile.readerDirection,
		spreadAnchorParity = pages.profile.spreadAnchorParity,
		filler = { pageGenerationId, slotRole, sourcePageIndex, leaf, fallbackOrdinal ->
			pages.filler(
				generationId = pageGenerationId,
				role = slotRole,
				sourcePageIndex = sourcePageIndex,
				leaf = leaf,
				fallbackOrdinal = fallbackOrdinal
			)
		},
		page = { pageGenerationId, pageOrdinal ->
			pages.page(pageGenerationId, pageOrdinal)
		}
	)

	private fun setSurfaceReadingDirection(profile: ReaderPlayLikeCurlRasterProfile) {
		surfaceView.setReadingDirection(
			if (profile.readerDirection == ReaderPlayLikeCurlReaderDirection.Rtl) {
				ReadingDirection.RIGHT_TO_LEFT
			} else {
				ReadingDirection.LEFT_TO_RIGHT
			}
		)
	}

	private fun submitLibraryDeck(
		pages: PreparedPages,
		ordinal: Int,
		role: ReaderDeckSubmissionRole
	) {
		if (pages.obsolete || destroyed || !enabled || !attached) return
		val generationId = nextDeckGeneration++
		val deck = runCatching {
			buildLibraryDeck(pages, ordinal, generationId)
		}.getOrElse { error ->
			if (role == ReaderDeckSubmissionRole.Active) {
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Failed,
					interaction = ReaderPageInteractionState.Failed,
					reason = "deck-build-failed:$ordinal"
				)
			}
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"Failed to build PlayLikeCurl deck ordinal=$ordinal phase=$preparationPhase",
				error
			)
			requestPrewarmIfIdle("deck-build-failed")
			return
		}
		deckDiagnosticTracker?.begin(
			generation = generationId,
			repairAttempt = null,
			role = role
		)
		pages.generations += generationId
		generationOwners[generationId] = pages
		generationRoles[generationId] = role
		when (role) {
			ReaderDeckSubmissionRole.Active -> {
				activeDeckGenerationId = generationId
				notifyPreparedActiveDeckChanged(null)
			}
			ReaderDeckSubmissionRole.Pending -> {
				pendingDeckGenerationId
					?.takeIf { it != generationId }
					?.let(surfaceView::releaseDeck)
				pendingDeckGenerationId = generationId
				pendingDeckOrdinal = ordinal
				updateReadiness(
					pendingTextureDeck = ReaderTextureDeckState.Preparing,
					reason = "pending-deck-submitting:$generationId"
				)
			}
		}
		updateSurfaceBounds()
		logActivationState(
			event = "deck-submitted",
			detail = "generation=$generationId ordinal=$ordinal role=$role " +
				"orientation=${pages.profile.orientation}"
		)
		setSurfaceReadingDirection(pages.profile)
		try {
			surfaceView.submitDeck(deck)
		} catch (failure: Throwable) {
			deckDiagnosticTracker?.cancel(generationId)
			throw failure
		}
		deckDiagnosticTracker?.submitted(
			generation = generationId,
			active = activeDeckGenerationId,
			pending = pendingDeckGenerationId
		)
	}

	private fun updateSurfaceBounds() {
		val params = surfaceView.layoutParams ?: return
		if (
			params.width == ViewGroup.LayoutParams.MATCH_PARENT &&
			params.height == ViewGroup.LayoutParams.MATCH_PARENT
		) {
			return
		}
		params.width = ViewGroup.LayoutParams.MATCH_PARENT
		params.height = ViewGroup.LayoutParams.MATCH_PARENT
		surfaceView.layoutParams = params
		surfaceView.requestLayout()
		logActivationState(
			event = "surface-bounds-updated",
			detail = "width=match-parent height=match-parent host=${host.width}x${host.height}"
		)
	}

	private fun ReaderPlayLikeCurlRasterLayout.displayRectInHost(
		leaf: ReaderPlayLikeCurlFoliateLeaf
	) = run {
		val location = IntArray(2)
		host.getLocationInWindow(location)
		checkNotNull(
			displayRect(
				leaf = leaf,
				rendererLeftInWindow = location[0],
				rendererTopInWindow = location[1],
				rendererWidth = host.width,
				rendererHeight = host.height
			)
		) { "Physical page placement does not fit the renderer host" }
	}

	private fun PreparedPages.page(
		generationId: Long,
		ordinal: Int
	): PageImage<Bitmap> {
		val image = checkNotNull(deck.value(ordinal)) {
			"Missing prepared Foliate page $ordinal for ${profile.orientation}"
		}
		return PageImage(
			generationId,
			"${profile.sourceIdentity}:${profile.orientation.name.lowercase()}:$ordinal",
			ordinal,
			image.bitmap.width,
			image.bitmap.height,
			image.layout.displayRectInHost(image.leaf),
			image.bitmap
		)
	}

	private fun PreparedPages.filler(
		generationId: Long,
		role: ReaderPlayLikeCurlDeckSlotRole,
		sourcePageIndex: Int,
		leaf: ReaderPlayLikeCurlPhysicalLeaf,
		fallbackOrdinal: Int
	): PageImage<Bitmap> {
		val borrowed = checkNotNull(deck.value(fallbackOrdinal)) {
			"Missing filler lease page $fallbackOrdinal for ${profile.orientation}"
		}
		val foliateLeaf = readerPlayLikeCurlFillerFoliateLeaf(profile.orientation, leaf)
		val displayRect = borrowed.layout.displayRectInHost(foliateLeaf)
		return PageImage.filler(
			generationId,
			"filler-${role.name}-$sourcePageIndex-${leaf.name}",
			fallbackOrdinal,
			borrowed.bitmap.width,
			borrowed.bitmap.height,
			displayRect,
			borrowed.bitmap,
			borrowed.paperColorArgb
		)
	}

	private fun onHandoffAttemptEvent(
		event: ReaderWebViewVisualHandoffAttemptEvent
	) {
		when (event) {
			is ReaderWebViewVisualHandoffAttemptEvent.Started -> {
				handoffDiagnosticStarts[event.handoffAttemptId] =
					diagnostics?.now() ?: 0L
				relocationQueue.head()?.takeIf { request ->
					request.token.value == event.relocationToken
				}?.let { request ->
					handoffDiagnosticTargets[event.handoffAttemptId] =
						request.destinationOrdinal
				}
			}
			is ReaderWebViewVisualHandoffAttemptEvent.Terminal -> {
				val startedAt =
					handoffDiagnosticStarts.remove(event.handoffAttemptId) ?: return
				val target = handoffDiagnosticTargets.remove(event.handoffAttemptId)
					?: return
				diagnostics?.handoff(
					handoffAttemptId = event.handoffAttemptId,
					token = event.relocationToken,
					target = target,
					visualState = event.visualStateCompleted,
					nextFrame = event.nextFrameCompleted,
					result = event.result.toDiagnosticResult(),
					startedAtMs = startedAt,
					qaFaultCorrelation = event.qaFaultCorrelation
				)
				if (
					event.qaFaultCorrelation != null &&
					(event.result as? ReaderWebViewVisualHandoffResult.Failed)
						?.reason == ReaderWebViewVisualHandoffFailure.TimedOut
				) {
					staleHandoffDiagnosticStarts[event.handoffAttemptId] = startedAt
				}
			}
			is ReaderWebViewVisualHandoffAttemptEvent.StalePhysicalCallbackReleased -> {
				val startedAt = staleHandoffDiagnosticStarts.remove(
					event.handoffAttemptId
				) ?: return
				val request = relocationQueue.head()?.takeIf { candidate ->
					candidate.token.value == event.relocationToken
				} ?: return
				diagnostics?.handoff(
					handoffAttemptId = event.handoffAttemptId,
					token = event.relocationToken,
					target = request.destinationOrdinal,
					visualState = false,
					nextFrame = false,
					result =
						ReaderPageHandoffDiagnosticResult.StalePhysicalCallbackReleased,
					startedAtMs = startedAt,
					qaFaultCorrelation = event.qaFaultCorrelation
				)
			}
		}
	}

	private fun emitRelocationDiagnostic(
		request: ReaderPageRelocationRequest,
		state: ReaderPageRelocationDiagnosticState,
		terminal: Boolean = false,
		rejectionReason: ReaderPageRelocationDiagnosticRejectionReason =
			ReaderPageRelocationDiagnosticRejectionReason.None,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) {
		val startedAt = relocationDiagnosticStarts[request.token.value] ?: return
		val correlation = qaFaultCorrelation
			?: relocationVisualHandoffCoordinator.qaFaultCorrelation(
				request.token.value
			)
			?: relocationQaFaultCorrelations[request.token.value]
		diagnostics?.relocation(
			request = request,
			state = state,
			queueDepth = relocationQueue.ownershipSnapshot().queued,
			startedAtMs = startedAt,
			rejectionReason = rejectionReason,
			qaFaultCorrelation = correlation
		)
		if (terminal) relocationDiagnosticStarts.remove(request.token.value)
		if (terminal) relocationQaFaultCorrelations.remove(request.token.value)
	}

	private fun validateLivePresentation(
		request: ReaderPageRelocationRequest,
		onValidated: (ReaderPageRelocationContentValidationResult) -> Unit
	): ReaderPageRelocationContentValidationHandle {
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
		val generationOwner = generationOwners[request.textureGeneration]
		val profile = generationOwner?.profile
		val foregroundMutationGeneration =
			relocationLiveDispatchCoordinator.mutationGeneration(request)
		if (
			webView == null ||
			activeDeckGenerationId != request.textureGeneration ||
			generationOwner == null ||
			profile == null ||
			foregroundMutationGeneration == null ||
			profile.rasterGeneration != request.rasterGeneration ||
			!livePresentationValidationIsCurrent(
				request,
				generationOwner,
				foregroundMutationGeneration
			)
		) {
			onValidated(ReaderPageRelocationContentValidationResult.Invalidated)
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		val kind = profile.transitionKind()
		val snapshotPageIndices = runCatching {
			profile.pageRequest(request.destinationOrdinal).sourcePageIndex to
				profile.pageRequest(request.sourceOrdinal).sourcePageIndex
		}.getOrNull()
		if (snapshotPageIndices == null) {
			onValidated(ReaderPageRelocationContentValidationResult.Invalidated)
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		val expectedTarget = bundleSource.retainedCurrentLayoutSnapshot(
			pageIndex = snapshotPageIndices.first,
			kind = kind,
			expectedGeneration = request.rasterGeneration,
			expectedQuality = profile.quality
		)
		if (expectedTarget == null) {
			onValidated(ReaderPageRelocationContentValidationResult.Invalidated)
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		val expectedSource = if (snapshotPageIndices.second == snapshotPageIndices.first) {
			null
		} else {
			val retained = bundleSource.retainedCurrentLayoutSnapshot(
				pageIndex = snapshotPageIndices.second,
				kind = kind,
				expectedGeneration = request.rasterGeneration,
				expectedQuality = profile.quality
			)
			if (retained == null) {
				expectedTarget.release()
				onValidated(ReaderPageRelocationContentValidationResult.Invalidated)
				return ReaderPageRelocationContentValidationHandle.Completed
			}
			retained
		}
		if (
			!livePresentationValidationIsCurrent(
				request,
				generationOwner,
				foregroundMutationGeneration
			)
		) {
			expectedTarget.release()
			expectedSource?.release()
			onValidated(ReaderPageRelocationContentValidationResult.Invalidated)
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		retainInlineHandoffSnapshot(request, expectedTarget)
		return try {
			bundleSource.validateLivePresentation(
				webView = webView,
				rendererSurface = surfaceView,
				request = request,
				foregroundMutationGeneration = foregroundMutationGeneration,
				expectedTarget = expectedTarget,
				expectedSource = expectedSource,
				isStillCurrent = {
					livePresentationValidationIsCurrent(
						request,
						generationOwner,
						foregroundMutationGeneration
					)
				},
				onValidated = { result ->
					val fencedResult = if (
						livePresentationValidationIsCurrent(
							request,
							generationOwner,
							foregroundMutationGeneration
						)
					) {
						result
					} else {
						ReaderPageRelocationContentValidationResult.Invalidated
					}
					if (fencedResult == ReaderPageRelocationContentValidationResult.Invalidated) {
						clearRetainedInlineHandoffSnapshot(request)
					}
					onValidated(fencedResult)
				}
			)
		} catch (failure: Throwable) {
			clearRetainedInlineHandoffSnapshot(request)
			throw failure
		}
	}

	private fun retainInlineHandoffSnapshot(
		request: ReaderPageRelocationRequest,
		snapshot: ReaderPageSlideSnapshot
	) {
		snapshot.retain()
		clearRetainedInlineHandoffSnapshot()
		retainedInlineHandoffSnapshot = RetainedInlineHandoffSnapshot(request, snapshot)
	}

	private fun takeInlineHandoffSnapshot(
		request: ReaderPageRelocationRequest
	): ReaderPageSlideSnapshot? {
		val retained = retainedInlineHandoffSnapshot ?: return null
		retainedInlineHandoffSnapshot = null
		return if (retained.request == request) {
			retained.snapshot
		} else {
			retained.snapshot.release()
			null
		}
	}

	private fun clearRetainedInlineHandoffSnapshot(
		request: ReaderPageRelocationRequest
	) {
		if (retainedInlineHandoffSnapshot?.request != request) return
		clearRetainedInlineHandoffSnapshot()
	}

	private fun clearRetainedInlineHandoffSnapshot() {
		retainedInlineHandoffSnapshot?.snapshot?.release()
		retainedInlineHandoffSnapshot = null
	}

	private fun livePresentationValidationIsCurrent(
		request: ReaderPageRelocationRequest,
		generationOwner: PreparedPages,
		foregroundMutationGeneration: ReaderForegroundWebViewMutationGeneration
	): Boolean =
		!destroyed &&
			enabled &&
			relocationLiveDispatchCoordinator.isCurrent(
				request,
				foregroundMutationGeneration
			) &&
			!hasStaticRasterShieldOwnership() &&
			surfaceView.isAttachedToWindow &&
			surfaceView.isShown &&
			surfaceView.visibility == View.VISIBLE &&
			surfaceView.alpha > 0f &&
			surfaceView.holder.surface.isValid &&
			activeDeckGenerationId == request.textureGeneration &&
			generationOwners[request.textureGeneration] === generationOwner &&
			generationOwner.profile.rasterGeneration == request.rasterGeneration &&
			relocationQueue.matchesAcknowledgedHead(
				token = request.token.value,
				rasterGeneration = request.rasterGeneration,
				textureGeneration = request.textureGeneration,
				foliateSessionId = request.foliateSessionId,
				destinationOrdinal = request.destinationOrdinal
			) &&
			relocationVisualState().let { state ->
				state.attached &&
					state.resumed &&
					state.foliateSessionId == request.foliateSessionId &&
					state.webViewOrdinal == request.destinationOrdinal &&
					state.rasterGeneration == request.rasterGeneration &&
					state.textureGeneration == request.textureGeneration
			}

	private fun relocationVisualState(): ReaderPageRelocationVisualState {
		val preparedGeneration = activeDeckGenerationId?.takeIf { generationId ->
			generationId in preparedDeckGenerations
		}?.let { generationId ->
			generationOwners[generationId]?.profile?.rasterGeneration?.let { rasterGeneration ->
				generationId to rasterGeneration
			}
		}
		return ReaderPageRelocationVisualState(
			attached = attached && webViewProvider()?.isAttachedToWindow == true,
			resumed = hostResumed,
			foliateSessionId = currentFoliateSessionId,
			webViewOrdinal = currentWebViewOrdinal,
			rasterGeneration = preparedGeneration?.second,
			textureGeneration = preparedGeneration?.first
		)
	}

	private fun retryRelocationVisualHandoffAttached() {
		val sessionId = currentFoliateSessionId ?: return
		val ordinal = currentWebViewOrdinal ?: return
		relocationVisualHandoffCoordinator.onRetryEvent(
			ReaderPageRelocationVisualRetryEvent.Attached(sessionId, ordinal)
		)
	}

	private fun retryRelocationVisualHandoffResumed() {
		val sessionId = currentFoliateSessionId ?: return
		val ordinal = currentWebViewOrdinal ?: return
		relocationVisualHandoffCoordinator.onRetryEvent(
			ReaderPageRelocationVisualRetryEvent.Resumed(sessionId, ordinal)
		)
	}

	private fun retryRelocationVisualHandoffForPreparedDeck(generationId: Long) {
		val state = relocationVisualState()
		if (state.textureGeneration != generationId) return
		val sessionId = state.foliateSessionId ?: return
		val ordinal = state.webViewOrdinal ?: return
		val rasterGeneration = state.rasterGeneration ?: return
		relocationVisualHandoffCoordinator.onRetryEvent(
			ReaderPageRelocationVisualRetryEvent.Reprepared(
				foliateSessionId = sessionId,
				destinationOrdinal = ordinal,
				rasterGeneration = rasterGeneration,
				textureGeneration = generationId
			)
		)
	}

	private fun replaceRelocationDiagnosticIdentity(
		original: ReaderPageRelocationRequest,
		replacement: ReaderPageRelocationRequest
	) {
		check(relocationLiveDispatchCoordinator.replace(original, replacement)) {
			"Recovery replacement lost exact foreground ownership"
		}
		if (failedLivePresentationGeneration?.matches(original) == true) {
			failedLivePresentationGeneration =
				FailedLivePresentationGeneration(replacement)
		}
		clearRetainedInlineHandoffSnapshot()
		relocationDiagnosticStarts.remove(original.token.value)?.let { startedAt ->
			relocationDiagnosticStarts[replacement.token.value] = startedAt
		}
		relocationQaFaultCorrelations.transfer(
			originalToken = original.token.value,
			replacementToken = replacement.token.value
		)
	}

	private fun completeRelocationVisualHandoff(
		request: ReaderPageRelocationRequest
	) {
		check(relocationLiveDispatchCoordinator.complete(request)) {
			"Committed WebView exposure lost exact foreground ownership"
		}
		emitRelocationDiagnostic(
			request,
			ReaderPageRelocationDiagnosticState.Completed,
			terminal = true
		)
		retainsRejectedSurfaceInputShield = false
		if (failedLivePresentationGeneration != null) {
			failedLivePresentationGeneration = null
			livePresentationRecoveryRequest.clear()
			updateReadiness(
				interaction = preparedInteractionState(),
				reason = "visual-handoff-replacement-validated"
			)
		}
		onOwnershipDiagnosticRequested(ReaderPageOwnershipPhase.SteadyState)
	}

	private fun releaseTerminalContentFailure(
		request: ReaderPageRelocationRequest
	) {
		check(
			relocationLiveDispatchCoordinator.fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.ContentRejected
			)
		) { "Content rejection lost exact foreground ownership" }
		emitRelocationDiagnostic(
			request = request,
			state = ReaderPageRelocationDiagnosticState.Rejected,
			rejectionReason =
				ReaderPageRelocationDiagnosticRejectionReason.ContentRejected,
			terminal = true
		)
		takeInlineHandoffSnapshot(request)?.release()
		val recoveryStillCurrent =
			readerTerminalContentFailureRecoveryStillCurrent(
				destroyed = destroyed,
				failedGenerationMatches =
					failedLivePresentationGeneration?.matches(request) == true,
				currentOrdinal = currentOrdinal,
				destinationOrdinal = request.destinationOrdinal,
				hasNewerSurfacePresentationOwner =
					hasNewerSurfacePresentationOwner(request.gestureId)
			)
		if (!recoveryStillCurrent) {
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"PlayLikeCurl terminal content rejection became stale " +
					"gestureId=${request.gestureId}"
			)
			return
		}
		blockTerminalContentFailureRecovery(
			failedGestureId = request.gestureId,
			reason = "terminal-content-rejected"
		)
	}

	private fun releaseTerminalContentFailureAfterCancelledGesture(
		cancelledGestureId: Long,
		settledOrdinal: Int
	) {
		val failure = failedLivePresentationGeneration ?: return
		if (
			!readerCancelledGestureCanReleaseTerminalContentFailure(
				failedGestureId = failure.gestureId,
				cancelledGestureId = cancelledGestureId,
				currentOrdinal = currentOrdinal,
				settledOrdinal = settledOrdinal,
				presentedSurfaceGestureId = presentedSurfaceGestureId
			)
		) {
			return
		}
		blockTerminalContentFailureRecovery(
			failedGestureId = failure.gestureId,
			reason = "terminal-content-rejected-after-cancelled-gesture"
		)
	}

	private fun blockTerminalContentFailureRecovery(
		failedGestureId: Long,
		reason: String
	) {
		Logger.w(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl terminal content rejection failed closed " +
				"gestureId=$failedGestureId reason=$reason"
		)
		retainsRejectedSurfaceInputShield = false
		failedLivePresentationGeneration = null
		livePresentationRecoveryRequest.request()
		inlineRasterShield.dismiss()
		hideSurface()
		updateReadiness(
			interaction = ReaderPageInteractionState.BlockingProfileRegeneration,
			reason = "visual-handoff-content-rejection-blocked"
		)
		requestPrewarmIfIdle(reason)
		onOwnershipDiagnosticRequested(ReaderPageOwnershipPhase.SteadyState)
	}

	private fun publishRelocationVisualRecovery(
		request: ReaderPageRelocationRequest,
		reason: ReaderWebViewVisualHandoffFailure
	) {
		if (reason == ReaderWebViewVisualHandoffFailure.ContentRejected) {
			failedLivePresentationGeneration = FailedLivePresentationGeneration(request)
			retainsRejectedSurfaceInputShield = true
			onViewerContentInputSuppressed()
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"PlayLikeCurl visual handoff content validation failed reason=$reason"
			)
			updateReadiness(
				interaction = ReaderPageInteractionState.Failed,
				reason = "visual-handoff-content-validation-failed"
			)
			requestLivePresentationRecovery(reason)
			return
		}
		if (reason == ReaderWebViewVisualHandoffFailure.PresentationFailed) {
			failedLivePresentationGeneration = FailedLivePresentationGeneration(request)
			retainsRejectedSurfaceInputShield = true
			onViewerContentInputSuppressed()
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"PlayLikeCurl visual handoff presentation failed reason=$reason"
			)
			updateReadiness(
				interaction = ReaderPageInteractionState.Failed,
				reason = "visual-handoff-presentation-failed"
			)
			retryRelocationVisualHandoffForPreparedDeck(request.textureGeneration)
			return
		}
		check(
			relocationLiveDispatchCoordinator.fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
		) { "Visual handoff failure lost exact foreground ownership" }
	}

	private fun transferAndDispatchRelocation(
		request: ReaderPageRelocationRequest,
		claim: ReaderForegroundWebViewLiveClaim
	) {
		check(relocationLiveDispatchCoordinator.transfer(request, claim)) {
			"Committed relocation duplicated foreground ownership"
		}
		if (relocationQueue.occupiedCount() == 0) {
			check(
				relocationLiveDispatchCoordinator.fail(
					request,
					ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
				)
			) { "Reentrant terminal drain lost exact foreground ownership" }
			return
		}
		try {
			dispatchNextRelocation()
		} catch (failure: Throwable) {
			relocationLiveDispatchCoordinator.fail(
				request,
				ReaderPageRelocationDiagnosticRejectionReason.JavascriptDispatchFailed
			)
			throw failure
		}
	}

	private fun dispatchRelocation(request: ReaderPageRelocationRequest) {
		check(relocationLiveDispatchCoordinator.dispatch(request)) {
			"Recovery relocation did not own exact foreground dispatch"
		}
	}

	private fun dispatchNextRelocation(): Boolean {
		val request = relocationQueue.commandToDispatch() ?: return false
		clearRetainedInlineHandoffSnapshot()
		return relocationLiveDispatchCoordinator.dispatch(request)
	}

	private fun relocationDispatchIsCurrent(
		request: ReaderPageRelocationRequest
	): Boolean =
		!destroyed &&
			enabled &&
			currentFoliateSessionId == request.foliateSessionId &&
			(
				relocationQueue.matchesDispatchedHead(
					token = request.token.value,
					rasterGeneration = request.rasterGeneration,
					textureGeneration = request.textureGeneration,
					foliateSessionId = request.foliateSessionId,
					destinationOrdinal = request.destinationOrdinal
				) ||
				relocationQueue.matchesAcknowledgedHead(
					token = request.token.value,
					rasterGeneration = request.rasterGeneration,
					textureGeneration = request.textureGeneration,
					foliateSessionId = request.foliateSessionId,
					destinationOrdinal = request.destinationOrdinal
				)
			)

	private fun dispatchExactVisualPage(
		request: ReaderPageRelocationRequest,
		generation: ReaderForegroundWebViewMutationGeneration
	): ReaderPageRelocationExactDispatchResult {
		clearRetainedInlineHandoffSnapshot()
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
			?: return ReaderPageRelocationExactDispatchResult.Rejected(
				ReaderPageRelocationDiagnosticRejectionReason.WebViewUnavailable
			)
		val command = JSONObject().apply {
			put("type", "goToVisualPage")
			put("pageIndex", request.destinationOrdinal)
			put("settleToken", request.token.value)
			put("settleGestureId", request.gestureId)
			put("settleSessionId", request.foliateSessionId)
			put("settleRasterGeneration", request.rasterGeneration)
			put("settleTextureGeneration", request.textureGeneration)
			put("settleForegroundMutationGeneration", generation.value)
		}
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl exact page dispatched pageIndex=${request.destinationOrdinal}"
		)
		try {
			webView.evaluateJavascript(
				"window.NavicReaderBridge?.dispatch?.($command)"
			) { }
		} catch (failure: Throwable) {
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"PlayLikeCurl exact page dispatch failed " +
					"failureClass=${failure::class.simpleName ?: "unknown"}"
			)
			return ReaderPageRelocationExactDispatchResult.Rejected(
				ReaderPageRelocationDiagnosticRejectionReason.JavascriptDispatchFailed
			)
		}
		emitRelocationDiagnostic(
			request,
			ReaderPageRelocationDiagnosticState.Dispatched
		)
		relocationDispatchTimeout.arm(request)
		return ReaderPageRelocationExactDispatchResult.Dispatched
	}

	private fun rejectDispatchedRelocation(
		request: ReaderPageRelocationRequest,
		reason: ReaderPageRelocationDiagnosticRejectionReason
	) {
		val queueCurrent = relocationQueue.matchesDispatchedHead(
			token = request.token.value,
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration,
			foliateSessionId = request.foliateSessionId,
			destinationOrdinal = request.destinationOrdinal
		) || relocationQueue.matchesAcknowledgedHead(
			token = request.token.value,
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration,
			foliateSessionId = request.foliateSessionId,
			destinationOrdinal = request.destinationOrdinal
		)
		if (!queueCurrent) {
			return
		}
		cancelActiveGesture(
			ReaderPageLifecycleCancellationReason.RasterProfileInvalidated
		)
		currentOrdinal = readerPageRelocationDispatchRecoveryOrdinal(
			request = request,
			currentFoliateSessionId = currentFoliateSessionId,
			currentWebViewOrdinal = currentWebViewOrdinal
		)
		Logger.w(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl dispatched relocation rejected " +
				"pageIndex=${request.destinationOrdinal} reason=$reason"
		)
		invalidate(
			reason = "relocation-dispatch-${reason.name}",
			relocationRejectionReason = reason
		)
		onOwnershipDiagnosticRequested(ReaderPageOwnershipPhase.SteadyState)
		if (enabled) onRequestPrewarm()
	}

	private fun releaseGeneration(generationId: Long) {
		deckDiagnosticTracker?.cancel(generationId)
		val pages = generationOwners.remove(generationId) ?: return
		val releasedCurrentActive = activeDeckGenerationId == generationId
		generationRoles.remove(generationId)
		preparedDeckGenerations -= generationId
		recoveredDeckGenerations -= generationId
		if (releasedCurrentActive) {
			activeDeckGenerationId = null
			if (activePages === pages) activePages = null
			notifyPreparedActiveDeckChanged(null)
		}
		if (pendingDeckGenerationId == generationId) {
			pendingDeckGenerationId = null
			pendingDeckOrdinal = null
		}
		pages.generations -= generationId
		if (pages.generations.isEmpty() && pages !== activePages) {
			pages.obsolete = true
		}
		closeIfUnused(pages)
	}

	private fun recoverRejectedSettlement(
		sourceOrdinal: Int,
		promotedGeneration: Long
	) {
		recoverRejectedReaderSettlement(
			sourceOrdinal = sourceOrdinal,
			promotedGeneration = promotedGeneration,
			rendererEnabled = enabled,
			restoreSourceOrdinal = { ordinal -> currentOrdinal = ordinal },
			invalidateRenderer = { reason -> invalidate(reason) },
			requestPrewarm = onRequestPrewarm
		)
	}

	private fun promotePendingDeck(currentPageOrdinal: Int): Long? {
		val promotedGeneration = pendingDeckGenerationId
			?.takeIf { pendingDeckOrdinal == currentPageOrdinal }
		if (promotedGeneration == null) {
			updateReadiness(
				textureDeck = ReaderTextureDeckState.Failed,
				interaction = ReaderPageInteractionState.Failed,
				reason = "settlement-missing-pending:$currentPageOrdinal"
			)
			requestPrewarmIfIdle("settlement-missing-pending")
			return null
		}
		val promotedPages = generationOwners[promotedGeneration]
		if (promotedPages != null && promotedPages !== activePages) {
			activePages?.let { previous ->
				previous.obsolete = true
				closeIfUnused(previous)
			}
			activePages = promotedPages
		}
		activeDeckGenerationId = promotedGeneration
		pendingDeckGenerationId = null
		pendingDeckOrdinal = null
		generationRoles[promotedGeneration] = ReaderDeckSubmissionRole.Active
		val prepared = promotedGeneration in preparedDeckGenerations
		updateReadiness(
			textureDeck = if (prepared) ReaderTextureDeckState.Ready else ReaderTextureDeckState.Preparing,
			pendingTextureDeck = ReaderTextureDeckState.Empty,
			interaction = if (prepared) preparedInteractionState() else ReaderPageInteractionState.BackgroundPrefetch,
			reason = "settlement-promoted:$promotedGeneration:$currentPageOrdinal"
		)
		if (prepared) publishPreparedActiveDeck(currentPageOrdinal)
		else notifyPreparedActiveDeckChanged(null)
		return promotedGeneration
	}

	private fun discardPendingDeck(reason: String) {
		val generationId = pendingDeckGenerationId ?: return
		pendingDeckGenerationId = null
		pendingDeckOrdinal = null
		updateReadiness(
			pendingTextureDeck = ReaderTextureDeckState.Empty,
			reason = "pending-deck-discarded:$generationId:$reason"
		)
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl pending deck discarded generation=$generationId reason=$reason"
		)
		surfaceView.releaseDeck(generationId)
	}

	private fun closeIfUnused(pages: PreparedPages) {
		if (!pages.obsolete || pages.generations.isNotEmpty()) return
		preparedPageSets -= pages
		pages.deck.close()
	}

	private fun hideSurfaceAfterGesture(gestureId: Long) {
		if (relocationQueue.ownershipSnapshot().queued > 0) return
		if (
			presentedFrameGestureId != gestureId &&
			presentedSurfaceGestureId != gestureId
		) {
			return
		}
		hideSurface()
	}

	private fun hasNewerSurfacePresentationOwner(gestureId: Long): Boolean =
		presentedFrameGestureId?.let { owner -> owner > gestureId } == true ||
			presentedSurfaceGestureId?.let { owner -> owner > gestureId } == true

	private fun finalizeHandoffPresentation(
		request: ReaderPageRelocationRequest,
		onFinalized: (Boolean) -> Unit
	) {
		val foregroundMutationGeneration =
			relocationLiveDispatchCoordinator.mutationGeneration(request)
		if (foregroundMutationGeneration == null) {
			onFinalized(false)
			return
		}
		val generationOwner = generationOwners[request.textureGeneration]
		if (
			generationOwner == null ||
			!handoffPresentationIsCurrent(
				request,
				generationOwner,
				foregroundMutationGeneration
			)
		) {
			onFinalized(false)
			return
		}
		if (inlineRasterShield.ownsPresentation()) {
			fadeInlineHandoffShield(
				request,
				generationOwner,
				foregroundMutationGeneration,
				onFinalized
			)
			return
		}
		val snapshot = takeInlineHandoffSnapshot(request)
		if (snapshot == null) {
			onFinalized(false)
			return
		}
		if (
			!handoffPresentationIsCurrent(
				request,
				generationOwner,
				foregroundMutationGeneration
			)
		) {
			snapshot.release()
			onFinalized(false)
			return
		}
		snapshot.retain()
		check(retainedInlineHandoffSnapshot == null)
		retainedInlineHandoffSnapshot = RetainedInlineHandoffSnapshot(request, snapshot)
		inlineRasterShield.present(snapshot) { presented ->
			val handoffStillCurrent = handoffPresentationIsCurrent(
				request,
				generationOwner,
				foregroundMutationGeneration
			)
			if (!handoffStillCurrent) {
				if (presented) inlineRasterShield.dismiss()
				clearRetainedInlineHandoffSnapshot(request)
				onFinalized(false)
				return@present
			}
			if (!presented) {
				Logger.w(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl inline raster crossfade presentation failed"
				)
				onFinalized(false)
				return@present
			}
			clearRetainedInlineHandoffSnapshot(request)
			fadeInlineHandoffShield(
				request,
				generationOwner,
				foregroundMutationGeneration,
				onFinalized
			)
		}
	}

	private fun fadeInlineHandoffShield(
		request: ReaderPageRelocationRequest,
		generationOwner: PreparedPages,
		foregroundMutationGeneration: ReaderForegroundWebViewMutationGeneration,
		onFinalized: (Boolean) -> Unit
	) {
		if (
			!inlineRasterShield.ownsPresentation() ||
			!handoffPresentationIsCurrent(
				request,
				generationOwner,
				foregroundMutationGeneration
			)
		) {
			onFinalized(false)
			return
		}
		hideSurfaceBehindInlineRasterShield()
		inlineRasterShield.fadeOut(
			ReaderPageLiveHandoffCrossfadeMillis
		) { exposedFrameCommitted ->
			val finalized =
				exposedFrameCommitted &&
					handoffPresentationIsCurrent(
						request,
						generationOwner,
						foregroundMutationGeneration
					)
			if (finalized) inlineRasterShield.dismiss()
			onFinalized(finalized)
		}
	}

	private fun handoffPresentationIsCurrent(
		request: ReaderPageRelocationRequest,
		generationOwner: PreparedPages,
		foregroundMutationGeneration: ReaderForegroundWebViewMutationGeneration
	): Boolean =
		!destroyed &&
			enabled &&
			relocationLiveDispatchCoordinator.isCurrent(
				request,
				foregroundMutationGeneration
			) &&
			activeDeckGenerationId == request.textureGeneration &&
			generationOwners[request.textureGeneration] === generationOwner &&
			generationOwner.profile.rasterGeneration == request.rasterGeneration &&
			currentOrdinal == request.destinationOrdinal &&
			currentWebViewOrdinal == request.destinationOrdinal &&
			currentFoliateSessionId == request.foliateSessionId &&
			relocationQueue.matchesAcknowledgedHead(
				token = request.token.value,
				rasterGeneration = request.rasterGeneration,
				textureGeneration = request.textureGeneration,
				foliateSessionId = request.foliateSessionId,
				destinationOrdinal = request.destinationOrdinal
			) &&
			!hasNewerSurfacePresentationOwner(request.gestureId)

	private fun hideSurfaceBehindInlineRasterShield() {
		check(inlineRasterShield.ownsPresentation())
		hideCurlSurface()
	}

	private fun hideSurface() {
		if (
			!destroyed &&
			(retainsRejectedSurfaceInputShield || failedLivePresentationGeneration != null)
		) {
			return
		}
		hideCurlSurface()
		inlineRasterShield.dismiss()
		clearRetainedInlineHandoffSnapshot()
	}

	private fun hideCurlSurface() {
		presentedFrameRequestId?.let { requestId ->
			surfaceView.cancelPresentedFrameRequest(requestId)
		}
		presentedFrameRequestId = null
		presentedFrameGestureId = null
		presentedSurfaceGestureId = null
		surfaceView.animate().cancel()
		surfaceView.alpha = 0f
	}

	private fun preparedInteractionState(): ReaderPageInteractionState =
		if (failedLivePresentationGeneration != null) {
			ReaderPageInteractionState.Failed
		} else if (preparationPhase == ReaderPagePreparationPhase.Preparing) {
			ReaderPageInteractionState.BackgroundPrefetch
		} else {
			ReaderPageInteractionState.Ready
		}

	private fun blockingPreparationState(): ReaderPageInteractionState =
		if (hasPreparedDeckBefore) {
			ReaderPageInteractionState.BlockingProfileRegeneration
		} else {
			ReaderPageInteractionState.BlockingInitialPreparation
		}

	private fun updateReadiness(
		textureDeck: ReaderTextureDeckState = readinessState.textureDeck,
		pendingTextureDeck: ReaderTextureDeckState = readinessState.pendingTextureDeck,
		interaction: ReaderPageInteractionState = readinessState.interaction,
		reason: String
	) {
		val latchedInteraction = readerPageLivePresentationInteractionState(
			hasFailedLivePresentation = failedLivePresentationGeneration != null,
			proposed = interaction
		)
		val next = ReaderPageRendererReadinessState(
			textureDeck = textureDeck,
			pendingTextureDeck = pendingTextureDeck,
			interaction = latchedInteraction
		)
		if (next == readinessState) return
		val previous = readinessState
		readinessState = next
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"Readiness transition texture=${previous.textureDeck}->${next.textureDeck} " +
				"pending=${previous.pendingTextureDeck}->${next.pendingTextureDeck} " +
				"interaction=${previous.interaction}->${next.interaction} reason=$reason"
		)
		onReadinessStateChange(next)
	}

	private fun requestLivePresentationRecovery(
		reason: ReaderWebViewVisualHandoffFailure
	) {
		require(
			reason == ReaderWebViewVisualHandoffFailure.ContentRejected ||
				reason == ReaderWebViewVisualHandoffFailure.PresentationFailed
		)
		livePresentationRecoveryRequest.request()
		requestPrewarmIfIdle(
			when (reason) {
				ReaderWebViewVisualHandoffFailure.ContentRejected ->
					"visual-handoff-content-validation-failed"
				ReaderWebViewVisualHandoffFailure.PresentationFailed ->
					"visual-handoff-presentation-failed"
			}
		)
		if (preparationPhase != ReaderPagePreparationPhase.Preparing) {
			refreshPreparedDeck()
		}
	}

	private fun requestPrewarmIfIdle(reason: String) {
		if (preparationPhase != ReaderPagePreparationPhase.Preparing) {
			logActivationState("prewarm-requested", reason)
			onRequestPrewarm()
		} else {
			logActivationState("refresh-gated", "$reason phase=$preparationPhase")
		}
	}

	private fun logActivationState(event: String, detail: String? = null) {
		val relocation = relocationQueue.ownershipSnapshot()
		val trace = buildString {
			append("activation event=")
			append(event)
			if (!detail.isNullOrBlank()) {
				append(" detail=")
				append(detail)
			}
			append(" enabled=")
			append(enabled)
			append(" attached=")
			append(attached)
			append(" destroyed=")
			append(destroyed)
			append(" capabilities=")
			append(capabilitiesAvailable)
			append(" interaction=")
			append(readinessState.interaction)
			append(" textureDeck=")
			append(readinessState.textureDeck)
			append(" pendingTextureDeck=")
			append(readinessState.pendingTextureDeck)
			append(" relocationReserved=")
			append(relocation.reserved)
			append(" relocationQueued=")
			append(relocation.queued)
			append(" activePages=")
			append(activePages != null)
			append(" requestedProfile=")
			append(requestedProfile != null)
			append(" requestGeneration=")
			append(requestGeneration)
			append(" preparationPhase=")
			append(preparationPhase)
		}
		if (trace == lastActivationTrace) return
		lastActivationTrace = trace
		Logger.i(ReaderPlayLikeCurlFoliateControllerTag, trace)
	}

	private fun isRequestActive(request: Long, webView: WebView): Boolean =
		request == requestGeneration &&
			enabled &&
			attached &&
			!destroyed &&
			webView.isAttachedToWindow

	private fun elapsedMillis(startedAtNanos: Long): Long =
		((System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)

	private fun completeSettlementReconciliation(
		gestureId: Long,
		sourceGenerationId: Long,
		retryDeferredRefresh: Boolean
	) {
		val refreshDeferred = settlementMutationFence.onSettlementReconciled(
			gestureId = gestureId,
			sourceGenerationId = sourceGenerationId,
			activeGestureId = activeGestureId
		)
		scheduleRecoveredDeckSubmissionRetry()
		if (!refreshDeferred) return
		if (!retryDeferredRefresh) {
			logActivationState(
				"refresh-gated",
				"stale-refresh-awaiting-authoritative-relocation:$gestureId"
			)
			return
		}
		schedulePreparedDeckRefresh("settlement-reconciled:$gestureId")
	}

	private fun schedulePreparedDeckRefresh(reason: String) {
		val posted = mainHandler.post {
			if (!destroyed && enabled) refreshPreparedDeck()
		}
		if (!posted) {
			updateReadiness(
				interaction = ReaderPageInteractionState.Failed,
				reason = "refresh-dispatch-rejected:$reason"
			)
		}
	}

	private fun finishGesture(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	): Boolean {
		discardDecodedWorkingSetPrefetch("gesture-finished", gestureId)
		if (!relocationGestureCoordinator.finish(gestureId, outcome, detail)) return false
		return publishGestureTerminal(gestureId, outcome, detail)
	}

	private fun publishGestureTerminal(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	): Boolean {
		val activeGestureEnded = activeGestureId == gestureId
		if (activeGestureEnded) activeGestureId = null
		val tapSink = if (tapTurnGestureId == gestureId) {
			tapTurnGestureId = null
			tapTurnTerminalSink.also { tapTurnTerminalSink = null }
		} else {
			null
		}
		val published = when {
			gestureId in hostOwnedTerminalGestureIds -> true
			tapSink == null -> onGestureTerminal(gestureId, outcome, detail)
			else -> tapSink(outcome, detail)
		}
		if (activeGestureEnded && activeGestureId == null) {
			scheduleRecoveredDeckSubmissionRetry()
			if (settlementMutationFence.takeDeferredRefreshIfUnblocked(activeGestureId)) {
				schedulePreparedDeckRefresh("gesture-terminal:$gestureId")
			}
		}
		return published
	}

	private fun scheduleRecoveredDeckSubmissionRetry() {
		if (
			destroyed ||
			deckRecoveryCoordinator.state !is
				ReaderPageDeckRecoveryState.WaitingForSubmissionCapacity ||
			!recoveredDeckSubmissionRetryPosted.compareAndSet(false, true)
		) {
			return
		}
		val posted = mainHandler.post {
			recoveredDeckSubmissionRetryPosted.set(false)
			if (
				!destroyed &&
				enabled &&
				!settlementMutationFence.blocksExternalDeckMutation(activeGestureId) &&
				!surfaceView.isSettlementRunning
			) {
				deckRecoveryCoordinator.onDeckSubmissionCapacityAvailable()
			}
		}
		if (!posted) {
			recoveredDeckSubmissionRetryPosted.set(false)
			deckRecoveryCoordinator.cancelAll()
		}
	}

	private fun finishActiveGesture(
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	) {
		activeGestureId?.let { gestureId -> finishGesture(gestureId, outcome, detail) }
	}
}
