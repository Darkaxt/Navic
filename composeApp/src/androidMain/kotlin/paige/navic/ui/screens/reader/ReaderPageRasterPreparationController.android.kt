package paige.navic.ui.screens.reader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import paige.navic.reader.ReaderChapterRasterGenerationState
import paige.navic.reader.ReaderDecodedWorkingSetState
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.readerPagePreparationState
import paige.navic.util.core.Logger

private const val ReaderPageRasterPreparationControllerTag = "ReaderPageRasterPreparation"
private const val ReaderPageRasterMaxAutomaticRetries = 1

private data class ReaderPageRasterBackgroundPrefetch(
	val webView: WebView,
	val centerPageIndex: Int,
	val kind: ReaderPageTurnTransitionKind,
	val currentChapterIndex: Int,
	val currentChapterPageStartIndex: Int,
	val currentChapterPageCount: Int,
	val chapters: List<ReaderPageAdjacentChapterPrefetchChapter>,
	val generation: Long
) {
	fun qualifiedPlan(rasterProfileEpoch: Long): ReaderPageAdjacentChapterPrefetchPlan =
		ReaderPageAdjacentChapterPrefetchPlan(
			key = ReaderPageAdjacentChapterPrefetchKey(
				currentChapterIndex = currentChapterIndex,
				currentChapterPageStartIndex = currentChapterPageStartIndex,
				currentChapterPageCount = currentChapterPageCount,
				rasterProfileEpoch = rasterProfileEpoch,
				rasterEpoch = generation
			),
			chapters = chapters
		)
}

private data class ReaderPageRasterRetryAttempt(
	val sessionId: Long,
	val retryCount: Int,
	val observedVersions: Map<ReaderPageRasterDeferralReason, Long>
) {
	fun observedVersion(reason: ReaderPageRasterDeferralReason): Long =
		checkNotNull(observedVersions[reason])
}

internal fun interface ReaderPageRasterPreparationPlanPort {
	fun query(
		webView: WebView,
		centerPageIndex: Int?,
		onPlan: (ReaderPageRasterPreparationPlan?) -> Unit
	)
}

private class ReaderPageWebViewRasterPreparationPlanPort :
	ReaderPageRasterPreparationPlanPort {
	override fun query(
		webView: WebView,
		centerPageIndex: Int?,
		onPlan: (ReaderPageRasterPreparationPlan?) -> Unit
	) {
		val centerExpression = centerPageIndex?.toString() ?: "null"
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(" +
				"$centerExpression) ?? null)"
		) { encoded -> onPlan(readerPageRasterPreparationPlan(encoded)) }
	}
}

internal fun interface ReaderPageRasterCurrentReferencePort {
	fun captureFresh(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		captureGeometry: ReaderPageTurnCaptureGeometry?,
		generation: Long,
		isCurrent: () -> Boolean,
		onResolved: (ReaderPageSlideSnapshot?) -> Unit
	)
}

private class ReaderPageBundleRasterCurrentReferencePort(
	private val bundleSource: ReaderPageTurnBundleSource
) : ReaderPageRasterCurrentReferencePort {
	override fun captureFresh(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		captureGeometry: ReaderPageTurnCaptureGeometry?,
		generation: Long,
		isCurrent: () -> Boolean,
		onResolved: (ReaderPageSlideSnapshot?) -> Unit
	) {
		bundleSource.captureCurrentSurface(webView, generation, captureGeometry) { current ->
			if (
				current == null ||
				generation != bundleSource.currentGeneration() ||
				!isCurrent()
			) {
				current?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				onResolved(null)
				return@captureCurrentSurface
			}
			val snapshot = bundleSource.cacheCurrentSnapshot(pageIndex, kind, current, generation)
			if (
				snapshot == null ||
				generation != bundleSource.currentGeneration() ||
				!isCurrent()
			) {
				onResolved(null)
				return@captureCurrentSurface
			}
			snapshot.retain()
			onResolved(snapshot)
		}
	}
}

internal fun readerPageTurnCanStartPassivePrewarm(
	destroyed: Boolean,
	sessionEnabled: Boolean,
	visualCommitPending: Boolean,
	idle: Boolean
): Boolean = !destroyed && sessionEnabled && !visualCommitPending && idle

internal fun readerPageCanReusePreparedWindow(
	reason: String?,
	requiredWindowDurable: Boolean,
	visualCenterChanging: Boolean,
	rasterRepairPending: Boolean,
	prewarmPending: Boolean
): Boolean = reason == "page-turn:exact" &&
	requiredWindowDurable &&
	(
		!visualCenterChanging ||
			(!rasterRepairPending && !prewarmPending)
	)

internal fun readerPageRasterAcquisitionTrigger(
	hasPreparedBefore: Boolean,
	persistentRasterEntries: Int
): ReaderPageRasterAcquisitionTrigger {
	require(persistentRasterEntries >= 0)
	return when {
		hasPreparedBefore -> ReaderPageRasterAcquisitionTrigger.WorkingSetRefill
		persistentRasterEntries > 0 -> ReaderPageRasterAcquisitionTrigger.WarmReopen
		else -> ReaderPageRasterAcquisitionTrigger.InitialPreparation
	}
}

/**
 * Prepares immutable Foliate page rasters for PlayLikeCurl.
 *
 * This class intentionally has no gesture, deformation, shader, or settlement behavior. Those
 * responsibilities belong exclusively to the imported PlayLikeCurl library and its Foliate bridge.
 */
internal class ReaderPageRasterPreparationController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val bundleSource: ReaderPageTurnBundleSource = ReaderPageTurnBundleSource(),
	private val diagnostics: ReaderPageRuntimeDiagnostics? = null,
	private val qaFaultRegistry: ReaderPageQaFaultRegistry? = null,
	private val fenceCallbacks: () -> Unit = {},
	private val fenceBundleOwners: () -> Unit = bundleSource::fenceForClose,
	private val closeRendererAndAdapter: suspend () -> Unit = {},
	private val closeBundleOwners: suspend () -> Unit = bundleSource::closeAndJoin,
	private val onRequestPrewarm: () -> Unit = {},
	private val canStartPreparation: () -> Boolean = { true },
	private val onAwaitHostEvent: (ReaderPageRasterDeferralReason) -> Unit = {},
	private val onPreparationStateChange: (ReaderPagePreparationState) -> Unit = {},
	private val rasterBatchController: ReaderPageRasterBatchPort =
		ReaderPageRasterBatchController(bundleSource, diagnostics),
	private val rasterRepairBatchController: ReaderPageRasterBatchPort =
		ReaderPageRasterBatchController(bundleSource, diagnostics),
	private val rasterBackgroundBatchController: ReaderPageRasterBatchPort =
		ReaderPageRasterBatchController(bundleSource, diagnostics),
	private val rasterPlanPort: ReaderPageRasterPreparationPlanPort =
		ReaderPageWebViewRasterPreparationPlanPort(),
	private val currentReferencePort: ReaderPageRasterCurrentReferencePort =
		ReaderPageBundleRasterCurrentReferencePort(bundleSource),
	private val currentLayoutSnapshot: (
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	) -> ReaderPageSlideSnapshot? = bundleSource::retainedCurrentLayoutSnapshot,
	private val initializeRasterCache: suspend (WebView) -> Unit =
		bundleSource::initializeRasterCache,
	private val retainedSnapshot: (
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	) -> ReaderPageSlideSnapshot? = bundleSource::retainedSnapshot
) {
	private val deferredRetryCoordinator = ReaderPageRasterDeferredRetryCoordinator()
	private val adjacentChapterPrefetchCoordinator =
		ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = ::scheduleBackgroundPrefetch,
			onCancel = ::cancelBackgroundPrefetchSubmission
		)
	private var backgroundPrefetchWebView: WebView? = null
	private val backgroundPrefetchAttachmentListener =
		object : View.OnAttachStateChangeListener {
			override fun onViewAttachedToWindow(view: View) {
				if (view === backgroundPrefetchWebView) {
					adjacentChapterPrefetchCoordinator.onHostAvailabilityChanged(true)
				}
			}

			override fun onViewDetachedFromWindow(view: View) {
				if (view === backgroundPrefetchWebView) {
					adjacentChapterPrefetchCoordinator.onHostAvailabilityChanged(false)
					removeBackgroundPrefetchShield()
				}
			}
		}
	private val rasterRepairCallbacks = linkedMapOf<
		Int,
		MutableList<(ReaderPageRasterRepairResult) -> Unit>
	>()
	private val rasterRepairDiagnostics =
		mutableMapOf<Int, ReaderPageDiagnosticOperation>()
	private val rasterRepairQaFaultCorrelations =
		mutableMapOf<Int, ReaderPageQaFaultCorrelation>()
	private var activeRasterRepairPageIndex: Int? = null
	private var nextRasterRepairShieldSession = 0L
	private var activeRasterRepairShieldSession: Long? = null
	private var deferredRasterRepairPageIndex: Int? = null
	private var deferredRasterRepairSessionId: Long? = null
	private var resumePrewarmAfterRasterRepairs = false
	private var currentVisualPageIndex: Int? = null
	private var preparedChapterRange: ReaderPageRasterPreparedChapterRange? = null
	private var candidateChapterRange: ReaderPageRasterPreparedChapterRange? = null
	private var candidateBlockingPageIndices: Set<Int> = emptySet()
	private var preparedPageCount = 0
	private var candidatePageCount = 0
	private var preparedStep = 1
	private var candidateStep = 1
	private val durableRasterPageIndices = linkedSetOf<Int>()
	private var preparedRepairPageIndices: Set<Int> = emptySet()
	private var candidateRepairPageIndices: Set<Int> = emptySet()
	private var candidateBackgroundPrefetch: ReaderPageRasterBackgroundPrefetch? = null
	private var durableBackgroundPrefetch: ReaderPageRasterBackgroundPrefetch? = null
	private var currentRasterProfileEpoch: Long? = null
	private var backgroundBatchSubmission: ReaderPageAdjacentChapterPrefetchSubmission? = null
	private val backgroundPrefetchDiagnosticStarts = mutableMapOf<Long, Long>()
	private var backgroundPrefetchShield: ImageView? = null
	private var backgroundPrefetchShieldSnapshot: ReaderPageSlideSnapshot? = null
	private var backgroundPrefetchShieldSessionId: Long? = null
	private var prewarmSession = 0L
	private var prewarmRetryAttempt: ReaderPageRasterRetryAttempt? = null
	private var activePrewarmDiagnostic: ReaderPageDiagnosticOperation? = null
	private var deferredPrewarmDiagnostic: ReaderPageDiagnosticOperation? = null
	private var pendingPrewarmRetryCount = 0
	private var deferredPrewarmSessionId: Long? = null
	private var nextDeferredRetrySessionId = 0L
	private var nextQaPreparationAttemptId = 0L
	private var prewarmInProgress = false
	private var rasterPreparationCompleted = 0
	private var rasterPreparationRequired = 0
	private var rasterInteractiveCompleted = 0
	private var rasterInteractiveRequired = 0
	private var activePreparationPageNumber: Int? = null
	private var lastPrewarmBoundary: String? = null
	private var lastPreparationStateTrace: String? = null
	private var hasPreparedBefore = false
	private var prewarmAcquisitionTriggerClassified = false
	private var activeAcquisitionTrigger =
		ReaderPageRasterAcquisitionTrigger.InitialPreparation
	private var preparationShield: ImageView? = null
	private var preparationShieldSnapshot: ReaderPageSlideSnapshot? = null
	private var preparationShieldSession: Long? = null
	private var preparationShieldBatchLabel: String? = null
	private val pendingVisualRestorations = linkedSetOf<CompletableDeferred<Unit>>()
	private var destroyed = false
	private val applicationContext = host.context.applicationContext
	private val teardownJob = SupervisorJob()
	private val teardownScope = CoroutineScope(
		teardownJob + Dispatchers.Main.immediate
	)
	private val rasterCacheInitializationJobs = linkedSetOf<Job>()

	private fun trackVisualRestoration(onRestored: () -> Unit): () -> Unit {
		val completion = CompletableDeferred<Unit>()
		pendingVisualRestorations += completion
		return {
			try {
				onRestored()
			} finally {
				pendingVisualRestorations -= completion
				completion.complete(Unit)
			}
		}
	}

	private suspend fun awaitVisualRestorations() {
		while (pendingVisualRestorations.isNotEmpty()) {
			pendingVisualRestorations.toList().awaitAll()
		}
	}

	private val teardown = ReaderPageReaderTeardown(
		scope = teardownScope,
		fenceCallbacks = ::fenceForDestroy,
		fenceBundleOwners = fenceBundleOwners,
		closeRendererAndAdapter = {
			val initializationJobs = rasterCacheInitializationJobs.toList()
			rasterCacheInitializationJobs.clear()
			initializationJobs.forEach { job -> job.cancelAndJoin() }
			awaitVisualRestorations()
			closeRendererAndAdapter()
		},
		closeBundleOwners = closeBundleOwners,
		onFinished = { teardownJob.complete() }
	)
	private val memoryCallbacks = object : ComponentCallbacks2 {
		override fun onConfigurationChanged(newConfig: Configuration) = Unit

		override fun onLowMemory() {
			host.post {
				cancelBackgroundPrefetch("on-low-memory")
				bundleSource.trimMemory("on-low-memory")
			}
		}

		override fun onTrimMemory(level: Int) {
			if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
				host.post {
					cancelBackgroundPrefetch("on-trim-memory:$level")
					bundleSource.trimMemory("on-trim-memory:$level")
				}
			}
		}
	}

	init {
		bundleSource.setPublicationCapacityAvailableListener(onRequestPrewarm)
		applicationContext.registerComponentCallbacks(memoryCallbacks)
	}

	private fun publishPreparationState(
		phase: ReaderPagePreparationPhase,
		error: String? = null,
		retryable: Boolean = false
	) {
		val interactiveRastersReady = rasterInteractiveRequired > 0 &&
			rasterInteractiveCompleted >= rasterInteractiveRequired
		val readiness = ReaderPageReadinessState(
			rasterGeneration = when (phase) {
				ReaderPagePreparationPhase.Idle -> if (hasPreparedBefore) {
					ReaderChapterRasterGenerationState.Ready
				} else {
					ReaderChapterRasterGenerationState.NotScheduled
				}
				ReaderPagePreparationPhase.Preparing ->
					ReaderChapterRasterGenerationState.Generating
				ReaderPagePreparationPhase.Ready -> ReaderChapterRasterGenerationState.Ready
				ReaderPagePreparationPhase.Failed -> ReaderChapterRasterGenerationState.Failed
			},
			decodedWorkingSet = when {
				phase == ReaderPagePreparationPhase.Failed && !hasPreparedBefore ->
					ReaderDecodedWorkingSetState.Failed
				phase == ReaderPagePreparationPhase.Preparing && !interactiveRastersReady ->
					ReaderDecodedWorkingSetState.Hydrating
				hasPreparedBefore || interactiveRastersReady -> ReaderDecodedWorkingSetState.Ready
				else -> ReaderDecodedWorkingSetState.Empty
			},
			textureDeck = if (hasPreparedBefore) {
				ReaderTextureDeckState.Ready
			} else {
				ReaderTextureDeckState.Empty
			},
			interaction = when {
				phase == ReaderPagePreparationPhase.Failed -> ReaderPageInteractionState.Failed
				phase == ReaderPagePreparationPhase.Preparing && !hasPreparedBefore ->
					ReaderPageInteractionState.BlockingInitialPreparation
				phase == ReaderPagePreparationPhase.Preparing -> ReaderPageInteractionState.BackgroundPrefetch
				phase == ReaderPagePreparationPhase.Ready -> ReaderPageInteractionState.Ready
				else -> ReaderPageInteractionState.BlockingInitialPreparation
			}
		)
		val state = readerPagePreparationState(
			phase = phase,
			requiredCount = rasterPreparationRequired,
			completedCount = rasterPreparationCompleted,
			interactiveRequiredCount = rasterInteractiveRequired,
			interactiveCompletedCount = rasterInteractiveCompleted,
			readiness = readiness,
			activePageNumber = activePreparationPageNumber,
			error = error,
			retryable = retryable
		)
		val trace = buildString {
			append("phase=${state.phase}")
			append(" completed=${state.completedCount}/${state.requiredCount}")
			append(" interactive=${state.interactiveCompletedCount}/${state.interactiveRequiredCount}")
			append(" interactiveReady=${state.interactiveReady}")
			append(" raster=${state.readiness.rasterGeneration}")
			append(" decoded=${state.readiness.decodedWorkingSet}")
			append(" presentation=${state.presentation}")
			append(" gestures=${state.gestureDisposition}")
			state.activePageLabel?.let { append(" active=$it") }
			state.error?.takeIf { it.isNotBlank() }?.let { append(" error=$it") }
		}
		if (lastPreparationStateTrace != trace) {
			lastPreparationStateTrace = trace
			Logger.i(
				ReaderPageRasterPreparationControllerTag,
				"Page preparation state $trace"
			)
		}
		onPreparationStateChange(state)
	}

	private fun enterBlockingPreparation(reason: String) {
		if (!hasPreparedBefore) return
		hasPreparedBefore = false
		logLoadingEvent(
			event = "cover-restored",
			detail = "reason=$reason visual=$currentVisualPageIndex " +
				"generation=${bundleSource.currentGeneration()}"
		)
		publishPreparationState(
			if (prewarmInProgress) {
				ReaderPagePreparationPhase.Preparing
			} else {
				ReaderPagePreparationPhase.Idle
			}
		)
	}

	fun updateBitmapQuality(value: String?) {
		if (!bundleSource.updateBitmapQuality(normalizeReaderPageBitmapQuality(value))) return
		cancelBackgroundPrefetch("bitmap-quality-changed")
		cancelRasterRepairs("bitmap-quality-changed")
		cancelPrewarm(reason = "bitmap-quality-changed")
		hasPreparedBefore = false
		durableRasterPageIndices.clear()
		publishPreparationState(ReaderPagePreparationPhase.Idle)
		onRequestPrewarm()
	}

	fun retryPreparation() {
		if (destroyed || prewarmInProgress) return
		onRequestPrewarm()
	}

	fun onProfileBootstrapFailed() {
		if (destroyed) return
		publishPreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			error = "Page preparation could not read the current layout.",
			retryable = true
		)
	}

	fun onRetryEvent(event: ReaderPageRasterRetryEvent): Boolean =
		deferredRetryCoordinator.onRetryEvent(event)

	fun onRasterProfileEpochChanged(epoch: Long?) {
		if (destroyed || currentRasterProfileEpoch == epoch) return
		val replacedProfile = currentRasterProfileEpoch != null && epoch != null
		currentRasterProfileEpoch = epoch
		if (replacedProfile) {
			cancelBackgroundPrefetch("raster-profile-replaced")
			cancelPrewarm(reason = "raster-profile-replaced")
			hasPreparedBefore = false
				durableRasterPageIndices.clear()
			publishPreparationState(ReaderPagePreparationPhase.Idle)
			onRequestPrewarm()
			return
		}
		if (epoch == null) {
			durableBackgroundPrefetch = null
			adjacentChapterPrefetchCoordinator.clear()
			rasterBackgroundBatchController.resetRetryState()
			clearBackgroundPrefetchWebView()
			return
		}
		durableBackgroundPrefetch?.let(::publishDurableAdjacentChapterPlan)
	}

	fun onPreparedActiveDeckChanged(deck: ReaderPagePreparedActiveDeck?) {
		if (destroyed) return
		adjacentChapterPrefetchCoordinator.onPreparedActiveDeckChanged(deck)
	}

	fun onWebViewAttachmentChanged(attached: Boolean) {
		if (destroyed) return
		adjacentChapterPrefetchCoordinator.onHostAvailabilityChanged(attached)
		if (!attached) {
			cancelRasterRepairs("webview-detached")
			deferPrewarmForWebViewDetach()
		}
	}

	fun onPointerInteractionChanged(active: Boolean) {
		if (destroyed) return
		adjacentChapterPrefetchCoordinator.onInteractionActiveChanged(active)
	}

	fun cancelAllDeferredRetries() {
		deferredRetryCoordinator.cancelAll()
		deferredPrewarmSessionId = null
		deferredRasterRepairSessionId = null
		deferredRasterRepairPageIndex = null
	}

	private fun newRetryAttempt(retryCount: Int = 0): ReaderPageRasterRetryAttempt =
		ReaderPageRasterRetryAttempt(
			sessionId = Math.incrementExact(nextDeferredRetrySessionId).also {
				nextDeferredRetrySessionId = it
			},
			retryCount = retryCount,
			observedVersions = ReaderPageRasterDeferralReason.entries.associateWith(
				deferredRetryCoordinator::observeVersion
			)
		)

	fun destroy(): Deferred<Unit> {
		if (!destroyed) destroyed = true
		return teardown.start()
	}

	private fun fenceForDestroy() {
		var failure: Throwable? = null
		fun capture(action: () -> Unit) {
			try {
				action()
			} catch (next: Throwable) {
				val first = failure
				if (first == null) failure = next
				else if (next !== first) first.addSuppressed(next)
			}
		}
		capture(fenceCallbacks)
		capture { cancelBackgroundPrefetch("destroy") }
		capture { cancelRasterRepairs("destroy") }
		capture { cancelPrewarm(reason = "destroy") }
		capture(::cancelAllDeferredRetries)
		capture { currentVisualPageIndex = null }
		capture {
			bundleSource.clearPublicationCapacityAvailableListener(onRequestPrewarm)
		}
		capture { applicationContext.unregisterComponentCallbacks(memoryCallbacks) }
		failure?.let { throw it }
	}

	suspend fun destroyAndJoin() {
		destroy().await()
	}

	fun invalidate(reason: String, clearVisualPageIndex: Boolean = false) {
		if (destroyed) return
		cancelBackgroundPrefetch("invalidate:$reason")
		cancelRasterRepairs("invalidate:$reason")
		cancelPrewarm(reason = "invalidate:$reason")
		bundleSource.invalidate(reason)
		hasPreparedBefore = false
		preparedChapterRange = null
		candidateChapterRange = null
		candidateBlockingPageIndices = emptySet()
		preparedPageCount = 0
		candidatePageCount = 0
		preparedStep = 1
		candidateStep = 1
		durableRasterPageIndices.clear()
		preparedRepairPageIndices = emptySet()
		candidateRepairPageIndices = emptySet()
		if (clearVisualPageIndex) currentVisualPageIndex = null
		publishPreparationState(ReaderPagePreparationPhase.Idle)
		logLoadingEvent(
			event = "invalidated",
			detail = "reason=$reason clearVisualPageIndex=$clearVisualPageIndex " +
				"visual=$currentVisualPageIndex generation=${bundleSource.currentGeneration()}"
		)
	}

	fun invalidateCurrentVisualSnapshot(reason: String) {
		if (destroyed) return
		cancelBackgroundPrefetch("invalidate-current:$reason")
		cancelRasterRepairs("invalidate-current:$reason")
		cancelPrewarm(reason = "invalidate-current:$reason")
		val pageIndex = currentVisualPageIndex
		if (pageIndex == null) {
			bundleSource.invalidate(reason)
		} else {
			bundleSource.invalidatePage(pageIndex, reason)
		}
	}

	fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {
		if (destroyed) return
		if (pageIndex == null) {
			cancelBackgroundPrefetch("visual-index-cleared:${reason ?: "unspecified"}")
			cancelRasterRepairs("visual-index-cleared:${reason ?: "unspecified"}")
			cancelPrewarm(reason = "visual-index-cleared:${reason ?: "unspecified"}")
			currentVisualPageIndex = null
			enterBlockingPreparation("visual-index-cleared:${reason ?: "unspecified"}")
			return
		}
		if (pageIndex < 0) return
		val visualCenterChanging = currentVisualPageIndex != pageIndex
		val requiredWindow = readerPageRasterBlockingWindow(
			centerPageIndex = pageIndex,
			step = preparedStep,
			pageCount = preparedPageCount
		)
		val requiredWindowDurable = requiredWindow.isNotEmpty() &&
			requiredWindow.all(durableRasterPageIndices::contains)
		val prewarmPending = prewarmInProgress || deferredPrewarmSessionId != null
		if (readerPageCanReusePreparedWindow(
				reason = reason,
				requiredWindowDurable = requiredWindowDurable,
				visualCenterChanging = visualCenterChanging,
				rasterRepairPending = rasterRepairCallbacks.isNotEmpty(),
				prewarmPending = prewarmPending
			)
		) {
			currentVisualPageIndex = pageIndex
			bundleSource.protectEncodedWindow(pageIndex, preparedStep, preparedPageCount)
			bundleSource.protectDecodedWindow(pageIndex, preparedStep, preparedPageCount)
			logLoadingEvent(
				event = "ordinary-turn-reused",
				detail = "page=$pageIndex blocking=${requiredWindow.sorted()} " +
					"generation=${bundleSource.currentGeneration()}"
			)
			if (visualCenterChanging && !prewarmPending) onRequestPrewarm()
			return
		}
		if (currentVisualPageIndex == pageIndex) {
			if (reason == "page-turn:exact" && !prewarmPending) {
				if (!requiredWindowDurable) {
					enterBlockingPreparation("incomplete-window-same-center")
				}
				onRequestPrewarm()
			}
			return
		}
		if (
			reason != "page-turn:exact" ||
				!requiredWindowDurable ||
				rasterRepairCallbacks.isNotEmpty()
		) {
			enterBlockingPreparation("visual-index-changed:${reason ?: "unspecified"}")
		}
		beginBlockingBackgroundPrefetchSession()
		cancelRasterRepairs("visual-index-changed:${reason ?: "unspecified"}")
		cancelPrewarm(reason = "visual-index-changed:${reason ?: "unspecified"}")
		currentVisualPageIndex = pageIndex
		logLoadingEvent(
			event = "visual-index-synchronized",
			detail = "page=$pageIndex reason=$reason generation=${bundleSource.currentGeneration()} " +
				"blockingComplete=$requiredWindowDurable"
		)
		onRequestPrewarm()
	}

	fun attachRasterRepairQaFault(
		pageIndex: Int,
		correlation: ReaderPageQaFaultCorrelation
	) {
		if (destroyed || pageIndex < 0) return
		val activeCorrelation =
			rasterRepairQaFaultCorrelations.putIfAbsent(pageIndex, correlation) ?: correlation
		rasterRepairDiagnostics[pageIndex]?.let { operation ->
			if (operation.qaFaultCorrelation == null) {
				rasterRepairDiagnostics[pageIndex] =
					operation.copy(qaFaultCorrelation = activeCorrelation)
			}
		}
	}

	fun repairRasterPage(
		pageIndex: Int,
		onComplete: (ReaderPageRasterRepairResult) -> Unit
	) = repairRasterPage(pageIndex, null, onComplete)

	fun repairRasterPage(
		pageIndex: Int,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation?,
		onComplete: (ReaderPageRasterRepairResult) -> Unit
	) {
		val repairPages = preparedRepairPageIndices
		val immediateFailure = when {
			destroyed -> ReaderPageRasterRepairResult.Cancelled
			pageIndex < 0 || pageIndex !in repairPages ->
				ReaderPageRasterRepairResult.Failed("outside-prepared-window")
			else -> null
		}
		if (immediateFailure != null) {
			logLoadingEvent(
				event = "page-repair-failed",
				detail = "page=$pageIndex reason=outside-prepared-window pages=$repairPages"
			)
			onComplete(immediateFailure)
			return
		}
		enterBlockingPreparation("required-raster-repair:$pageIndex")
		resumePrewarmAfterRasterRepairs = true
		val callbacks = rasterRepairCallbacks.getOrPut(pageIndex) { mutableListOf() }
		callbacks += onComplete
		qaFaultCorrelation?.let { correlation ->
			rasterRepairQaFaultCorrelations.putIfAbsent(pageIndex, correlation)
		}
		logLoadingEvent(
			event = "page-repair-requested",
			detail = "page=$pageIndex queued=${activeRasterRepairPageIndex != null} " +
				"pages=$repairPages generation=${bundleSource.currentGeneration()}"
		)
		startNextRasterRepair()
	}

	private fun allocateRasterRepairShieldSession(): Long {
		val sequence = Math.incrementExact(nextRasterRepairShieldSession)
		nextRasterRepairShieldSession = sequence
		return -sequence
	}

	private fun releaseActiveRasterRepairShield(reason: String) {
		val shieldSession = activeRasterRepairShieldSession ?: return
		activeRasterRepairShieldSession = null
		removePreparationShield(
			reason = reason,
			expectedSession = shieldSession
		)
	}

	private fun clearActiveRasterRepair(pageIndex: Int, reason: String) {
		if (activeRasterRepairPageIndex != pageIndex) return
		activeRasterRepairPageIndex = null
		releaseActiveRasterRepairShield(reason)
	}

	private fun startNextRasterRepair() {
		if (
			activeRasterRepairPageIndex != null ||
			deferredRasterRepairPageIndex != null ||
			prewarmInProgress ||
			destroyed
		) return
		val pageIndex = rasterRepairCallbacks.keys.firstOrNull() ?: return
		if (pageIndex !in rasterRepairDiagnostics) {
			diagnostics?.startOperation(
				rasterGeneration = bundleSource.currentGeneration(),
				ordinal = currentVisualPageIndex ?: pageIndex,
				qaFaultCorrelation = rasterRepairQaFaultCorrelations[pageIndex]
			)?.let { operation ->
				rasterRepairDiagnostics[pageIndex] = operation
				diagnostics.repair(
					operation,
					ReaderPageRepairDiagnosticState.Started
				)
			}
		}
		deferredPrewarmSessionId?.let { sessionId ->
			if (deferredRetryCoordinator.cancel(sessionId)) {
				resumePrewarmAfterRasterRepairs = true
			}
		}
		adjacentChapterPrefetchCoordinator.suspendForForegroundWork()
		val retryAttempt = newRetryAttempt()
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
		if (webView == null) {
			deferRasterRepair(
				pageIndex = pageIndex,
				reason = ReaderPageRasterDeferralReason.WebViewDetached,
				retryAttempt = retryAttempt,
				detail = "webview-unavailable"
			)
			return
		}
		val kind = if (expectedLayoutMode(webView) == "spread") {
			ReaderPageTurnTransitionKind.LandscapeSpreadSlide
		} else {
			ReaderPageTurnTransitionKind.PortraitSlide
		}
		val centerOrdinal = currentVisualPageIndex
		val reference = centerOrdinal?.let { index ->
			retainedSnapshot(index, kind)
		}
		if (reference == null) {
			deferRasterRepair(
				pageIndex = pageIndex,
				reason = ReaderPageRasterDeferralReason.ContentNotReady,
				retryAttempt = retryAttempt,
				detail = "reference-unavailable:$centerOrdinal"
			)
			onRequestPrewarm()
			return
		}
		activeRasterRepairPageIndex = pageIndex
		val repairShieldSession = allocateRasterRepairShieldSession()
		activeRasterRepairShieldSession = repairShieldSession
		val generation = bundleSource.currentGeneration()
		val repairPages = preparedRepairPageIndices.toSet()
		val started = rasterRepairBatchController.start(
			webView = webView,
			kind = kind,
			reference = reference,
			targets = listOf(
				ReaderPageRasterBatchTarget(pageIndex, ReaderPageRasterPriority.CurrentChapter)
			),
			trigger = ReaderPageRasterAcquisitionTrigger.Repair,
			onStagingStarted = { snapshot ->
				reusePreparationShield(
					snapshot = snapshot,
					session = repairShieldSession,
					batchLabel = "repair"
				)
			},
			onComplete = { outcome ->
				when {
					generation != bundleSource.currentGeneration() -> finishRasterRepair(
						pageIndex,
						ReaderPageRasterRepairResult.Cancelled,
						outcome.toString()
					)
					outcome == ReaderPageRasterBatchOutcome.Ready -> finishRasterRepair(
						pageIndex,
						readerPageRasterRepairedResult(
							repairedPageIndices = repairPages,
							centerOrdinal = centerOrdinal,
							rasterEpoch = generation,
							diagnosticOperation =
								rasterRepairDiagnostics[pageIndex]
						),
						outcome.toString()
					)
					outcome == ReaderPageRasterBatchOutcome.Cancelled -> finishRasterRepair(
						pageIndex,
						ReaderPageRasterRepairResult.Cancelled,
						outcome.toString()
					)
					outcome is ReaderPageRasterBatchOutcome.Deferred -> deferRasterRepair(
						pageIndex = pageIndex,
						reason = readerPageRasterDeferralReason(outcome),
						retryAttempt = retryAttempt,
						detail = outcome.toString()
					)
					outcome is ReaderPageRasterBatchOutcome.Failed -> finishRasterRepair(
						pageIndex,
						ReaderPageRasterRepairResult.Failed(outcome.diagnostic),
						outcome.toString()
					)
				}
			}
		)
		if (!started && activeRasterRepairPageIndex == pageIndex) {
			deferRasterRepair(
				pageIndex = pageIndex,
				reason = ReaderPageRasterDeferralReason.ContentNotReady,
				retryAttempt = retryAttempt,
				detail = "batch-start-deferred"
			)
		}
	}

	private fun deferRasterRepair(
		pageIndex: Int,
		reason: ReaderPageRasterDeferralReason,
		retryAttempt: ReaderPageRasterRetryAttempt,
		detail: String
	) {
		if (rasterRepairCallbacks[pageIndex].isNullOrEmpty()) return
		clearActiveRasterRepair(pageIndex, "repair-deferred:$reason")
		deferredRasterRepairPageIndex = pageIndex
		deferredRasterRepairSessionId = retryAttempt.sessionId
		rasterRepairDiagnostics[pageIndex]?.let { operation ->
			diagnostics?.repair(
				operation = operation,
				state = ReaderPageRepairDiagnosticState.Deferred,
				reason = reason
			)
		}
		logLoadingEvent(
			event = "page-repair-deferred",
			detail = "page=$pageIndex reason=$reason detail=$detail " +
				"generation=${bundleSource.currentGeneration()}"
		)
		deferredRetryCoordinator.defer(
			sessionId = retryAttempt.sessionId,
			reason = reason,
			observedVersion = retryAttempt.observedVersion(reason),
			retry = retry@{
				if (deferredRasterRepairSessionId != retryAttempt.sessionId || destroyed) {
					return@retry
				}
				deferredRasterRepairSessionId = null
				deferredRasterRepairPageIndex = null
				startNextRasterRepair()
			},
			cancel = {
				if (deferredRasterRepairSessionId == retryAttempt.sessionId) {
					deferredRasterRepairSessionId = null
					deferredRasterRepairPageIndex = null
				}
			}
		)
		if (
			reason == ReaderPageRasterDeferralReason.LayoutUnstable ||
			reason == ReaderPageRasterDeferralReason.WebViewDetached
		) {
			onAwaitHostEvent(reason)
		}
	}

	private fun finishRasterRepair(
		pageIndex: Int,
		result: ReaderPageRasterRepairResult,
		detail: String
	) {
		val callbacks = rasterRepairCallbacks.remove(pageIndex) ?: return
		rasterRepairQaFaultCorrelations.remove(pageIndex)
		val repairDiagnostic = rasterRepairDiagnostics.remove(pageIndex)
		repairDiagnostic?.let { operation ->
			when (result) {
				is ReaderPageRasterRepairResult.Repaired -> diagnostics?.repair(
					operation,
					ReaderPageRepairDiagnosticState.Ready
				)
				is ReaderPageRasterRepairResult.Deferred -> diagnostics?.repair(
					operation,
					ReaderPageRepairDiagnosticState.Deferred,
					result.reason
				)
				is ReaderPageRasterRepairResult.Failed -> diagnostics?.repair(
					operation,
					ReaderPageRepairDiagnosticState.Failed
				)
				ReaderPageRasterRepairResult.Cancelled -> diagnostics?.repair(
					operation,
					ReaderPageRepairDiagnosticState.Cancelled
				)
			}
		}
		clearActiveRasterRepair(pageIndex, "repair-finished:$result")
		if (deferredRasterRepairPageIndex == pageIndex) {
			deferredRasterRepairSessionId?.let(deferredRetryCoordinator::cancel)
			deferredRasterRepairPageIndex = null
		}
		val completed = result is ReaderPageRasterRepairResult.Repaired
		logLoadingEvent(
			event = if (completed) "page-repair-completed" else "page-repair-failed",
			detail = "page=$pageIndex detail=$detail generation=${bundleSource.currentGeneration()}"
		)
		callbacks.forEach { callback -> callback(result) }
		if (rasterRepairCallbacks.isEmpty()) {
			adjacentChapterPrefetchCoordinator.resumeAfterForegroundWork()
		}
		if (rasterRepairCallbacks.isEmpty() && resumePrewarmAfterRasterRepairs) {
			resumePrewarmAfterRasterRepairs = false
			onRequestPrewarm()
		} else {
			startNextRasterRepair()
		}
	}

	private fun readerPageRasterDeferralReason(
		outcome: ReaderPageRasterBatchOutcome.Deferred
	): ReaderPageRasterDeferralReason {
		val diagnostic = "${outcome.stage}:${outcome.reason}".lowercase()
		return when {
			"detach" in diagnostic || "webview" in diagnostic ->
				ReaderPageRasterDeferralReason.WebViewDetached
			"layout" in diagnostic -> ReaderPageRasterDeferralReason.LayoutUnstable
			"pagination" in diagnostic || "plan" in diagnostic ->
				ReaderPageRasterDeferralReason.PaginationNotReady
			"paused" in diagnostic -> ReaderPageRasterDeferralReason.ReaderPaused
			else -> ReaderPageRasterDeferralReason.ContentNotReady
		}
	}

	private fun cancelRasterRepairs(reason: String) {
		val callbacks = rasterRepairCallbacks.values.flatten()
		val pages = rasterRepairCallbacks.keys.toList()
		rasterRepairDiagnostics.values.forEach { operation ->
			diagnostics?.repair(
				operation,
				ReaderPageRepairDiagnosticState.Cancelled
			)
		}
		rasterRepairDiagnostics.clear()
		rasterRepairQaFaultCorrelations.clear()
		rasterRepairCallbacks.clear()
		val shieldSession = activeRasterRepairShieldSession
		activeRasterRepairPageIndex = null
		activeRasterRepairShieldSession = null
		deferredRasterRepairSessionId?.let(deferredRetryCoordinator::cancel)
		deferredRasterRepairSessionId = null
		deferredRasterRepairPageIndex = null
		resumePrewarmAfterRasterRepairs = false
		rasterRepairBatchController.cancel(
			trackVisualRestoration {
				shieldSession?.let { session ->
					removePreparationShield(
						reason = "repair-cancelled:$reason",
						expectedSession = session
					)
				}
			}
		)
		if (pages.isNotEmpty()) {
			logLoadingEvent(
				event = "page-repair-failed",
				detail = "pages=${pages.joinToString(",")} reason=cancelled:$reason"
			)
		}
		callbacks.forEach { callback -> callback(ReaderPageRasterRepairResult.Cancelled) }
	}

	private fun beginBlockingBackgroundPrefetchSession() {
		adjacentChapterPrefetchCoordinator.beginBlockingSession()
		rasterBackgroundBatchController.resetRetryState()
		candidateBackgroundPrefetch = null
		durableBackgroundPrefetch = null
	}

	fun prewarmAdjacent(): Boolean {
		if (!canStartPreparation()) return false
		if (prewarmInProgress) return true
		if (
			activeRasterRepairPageIndex == null &&
			deferredRasterRepairPageIndex == null &&
			rasterRepairCallbacks.isNotEmpty()
		) {
			startNextRasterRepair()
		}
		if (activeRasterRepairPageIndex != null) {
			resumePrewarmAfterRasterRepairs = true
			return true
		}
		if (!readerPageTurnCanStartPassivePrewarm(
			destroyed = destroyed,
			sessionEnabled = true,
			visualCommitPending = false,
			idle = true
		)) return false
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: return false
		deferredPrewarmSessionId?.let(deferredRetryCoordinator::cancel)
		beginBlockingBackgroundPrefetchSession()
		cancelRasterRepairs("blocking-session-started")
		val resumedDiagnostic = deferredPrewarmDiagnostic
		if (resumedDiagnostic == null) {
			prewarmAcquisitionTriggerClassified = false
		}
		var preparationDiagnostic = if (resumedDiagnostic == null) {
			diagnostics?.startOperation(
				rasterGeneration = bundleSource.currentGeneration(),
				ordinal = currentVisualPageIndex ?: -1
			)
		} else {
			diagnostics?.startRetryOperation(
				root = resumedDiagnostic,
				rasterGeneration = bundleSource.currentGeneration(),
				ordinal = currentVisualPageIndex ?: -1
			)
		}
		var qaDeferralReason: ReaderPageRasterDeferralReason? = null
		if (resumedDiagnostic == null) {
			val preparationAttemptId = preparationDiagnostic?.attempt
				?: Math.incrementExact(nextQaPreparationAttemptId).also {
					nextQaPreparationAttemptId = it
				}
			consumeQaDeferral(preparationAttemptId)?.let { (reason, applied) ->
				qaDeferralReason = reason
				preparationDiagnostic = preparationDiagnostic?.copy(
					qaFaultCorrelation = applied.correlation()
				)
			}
		}
		deferredPrewarmDiagnostic = null
		activePrewarmDiagnostic = preparationDiagnostic
		preparationDiagnostic?.let { operation ->
			diagnostics?.preparation(
				operation,
				ReaderPagePreparationDiagnosticState.Attempted
			)
		}
		prewarmRetryAttempt = newRetryAttempt(pendingPrewarmRetryCount)
		pendingPrewarmRetryCount = 0
		prewarmInProgress = true
		candidateChapterRange = null
		candidateBlockingPageIndices = emptySet()
		candidatePageCount = 0
		candidateStep = 1
		candidateRepairPageIndices = emptySet()
		candidateBackgroundPrefetch = null
		rasterPreparationCompleted = 0
		rasterPreparationRequired = 0
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = 0
		activePreparationPageNumber = currentVisualPageIndex?.plus(1)
		publishPreparationState(ReaderPagePreparationPhase.Preparing)
		val session = ++prewarmSession
		logLoadingEvent(
			event = "session-started",
			detail = "session=$session visual=$currentVisualPageIndex " +
				"generation=${bundleSource.currentGeneration()} preparedBefore=$hasPreparedBefore"
		)
		logPrewarmBoundary(
			event = "started",
			detail = "session=$session visual=$currentVisualPageIndex " +
				"webView=${webView.width}x${webView.height}"
		)
		qaDeferralReason?.let { reason ->
			finishPrewarm(
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "qa-deferral",
					pageIndex = currentVisualPageIndex,
					reason = qaDeferralDiagnostic(reason)
				)
			)
			return true
		}
		initializeRasterCacheAndQueryPlan(
			webView = webView,
			session = session
		)
		return true
	}

	private fun initializeRasterCacheAndQueryPlan(
		webView: WebView,
		session: Long
	) {
		val initializationJob = teardownScope.launch {
			try {
				initializeRasterCache(webView)
			} catch (failure: CancellationException) {
				throw failure
			} catch (failure: Throwable) {
				Logger.w(
					ReaderPageRasterPreparationControllerTag,
					"Page raster cache initialization failed " +
						"failureClass=${failure::class.simpleName ?: "unknown"}"
				)
			}
			if (!isPrewarmSessionActive(session)) return@launch
			if (!webView.isAttachedToWindow) {
				finishPrewarm(
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "raster-cache",
						pageIndex = currentVisualPageIndex,
						reason = "webview-detached"
					)
				)
				return@launch
			}
			if (!prewarmAcquisitionTriggerClassified) {
				activeAcquisitionTrigger = readerPageRasterAcquisitionTrigger(
					hasPreparedBefore = hasPreparedBefore,
					persistentRasterEntries = bundleSource.rasterCacheMetrics().diskEntries
				)
				prewarmAcquisitionTriggerClassified = true
			}
			queryRasterPreparationPlan(webView, session)
		}
		rasterCacheInitializationJobs += initializationJob
		initializationJob.invokeOnCompletion {
			rasterCacheInitializationJobs -= initializationJob
		}
	}

	private fun consumeQaDeferral(
		preparationAttemptId: Long
	): Pair<ReaderPageRasterDeferralReason, ReaderPageQaAppliedFault>? {
		val registry = qaFaultRegistry ?: return null
		val seams = listOf(
			ReaderPageQaFault.DeferContentNotReady to
				ReaderPageRasterDeferralReason.ContentNotReady,
			ReaderPageQaFault.DeferLayoutUnstable to
				ReaderPageRasterDeferralReason.LayoutUnstable,
			ReaderPageQaFault.DeferPaginationNotReady to
				ReaderPageRasterDeferralReason.PaginationNotReady,
			ReaderPageQaFault.DeferWebViewDetached to
				ReaderPageRasterDeferralReason.WebViewDetached,
			ReaderPageQaFault.DeferReaderPaused to
				ReaderPageRasterDeferralReason.ReaderPaused
		)
		seams.forEach { (fault, reason) ->
			val applied = registry.consumeAndApply(
				fault,
				ReaderPageQaFaultOperationContext(
					preparationAttemptId = preparationAttemptId
				)
			)
			if (applied != null) return reason to applied
		}
		return null
	}

	private fun qaDeferralDiagnostic(reason: ReaderPageRasterDeferralReason): String =
		when (reason) {
			ReaderPageRasterDeferralReason.ContentNotReady -> "content-not-ready"
			ReaderPageRasterDeferralReason.LayoutUnstable -> "layout-unstable"
			ReaderPageRasterDeferralReason.PaginationNotReady -> "pagination-not-ready"
			ReaderPageRasterDeferralReason.WebViewDetached -> "webview-detached"
			ReaderPageRasterDeferralReason.ReaderPaused -> "reader-paused"
		}

	private fun queryRasterPreparationPlan(webView: WebView, session: Long) {
		if (!isPrewarmActive(webView, session)) return
		rasterPlanPort.query(webView, currentVisualPageIndex) plan@{ plan ->
			if (!isPrewarmActive(webView, session)) return@plan
			if (plan == null) {
				logPrewarmBoundary("plan-unavailable", "session=$session")
				finishPrewarm(
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "raster-plan",
						pageIndex = currentVisualPageIndex,
						reason = "pagination-not-ready"
					)
				)
				return@plan
			}
			val expectedLayout = expectedLayoutMode(webView)
			if (plan.layoutMode != expectedLayout) {
				logPrewarmBoundary(
					event = "layout-mismatch",
					detail = "session=$session actual=${plan.layoutMode} expected=$expectedLayout " +
						"center=${plan.centerPageIndex}"
				)
				finishPrewarm(
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "raster-layout",
						pageIndex = plan.centerPageIndex,
						reason = "layout-unstable"
					)
				)
				return@plan
			}
			val visualPageIndex = currentVisualPageIndex
			if (visualPageIndex == null) {
				currentVisualPageIndex = plan.centerPageIndex
			} else if (visualPageIndex != plan.centerPageIndex) {
				logPrewarmBoundary(
					event = "center-mismatch",
					detail = "session=$session current=$visualPageIndex plan=${plan.centerPageIndex}"
				)
				finishPrewarm(
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "raster-center",
						pageIndex = plan.centerPageIndex,
						reason = "content-not-ready"
					)
				)
				return@plan
			}
			logPrewarmBoundary(
				event = "plan-accepted",
				detail = "session=$session layout=${plan.layoutMode} center=${plan.centerPageIndex} " +
					"targets=${plan.targets.size}"
			)
			val blockingTargets = plan.blockingTargetsOrNull()
			if (blockingTargets == null) {
				finishPrewarm(
					ReaderPageRasterBatchOutcome.Failed(
						stage = "blocking-window-plan",
						pageIndex = plan.centerPageIndex,
						reason = "incomplete-current-plus-minus-five"
					)
				)
				return@plan
			}
			candidateChapterRange = plan.preparedChapterRange()
			candidateBlockingPageIndices = blockingTargets
				.mapTo(linkedSetOf()) { target -> target.pageIndex }
			candidatePageCount = plan.pageCount
			candidateStep = plan.step
			candidateRepairPageIndices = plan.preparedRepairPageIndices()
			bundleSource.protectEncodedWindow(
				centerPageIndex = plan.centerPageIndex,
				step = plan.step,
				pageCount = plan.pageCount
			)
			bundleSource.protectDecodedWindow(
				centerPageIndex = plan.centerPageIndex,
				step = plan.step,
				pageCount = plan.pageCount
			)
			val kind = if (plan.layoutMode == "spread") {
				ReaderPageTurnTransitionKind.LandscapeSpreadSlide
			} else {
				ReaderPageTurnTransitionKind.PortraitSlide
			}
			candidateBackgroundPrefetch = ReaderPageRasterBackgroundPrefetch(
				webView = webView,
				centerPageIndex = plan.centerPageIndex,
				kind = kind,
				currentChapterIndex = plan.currentChapterIndex,
				currentChapterPageStartIndex = plan.currentChapterPageStartIndex,
				currentChapterPageCount = plan.currentChapterPageCount,
				chapters = plan.adjacentChapterPrefetchChapters(),
				generation = bundleSource.currentGeneration()
			)
			val calibrationTargets = readerPageRasterCalibrationTargets(plan.targets)
			obtainRasterReference(
				webView,
				session,
				plan.centerPageIndex,
				kind,
				plan.captureGeometry
			) { reference ->
				if (!isPrewarmActive(webView, session)) {
					reference?.release()
					return@obtainRasterReference
				}
				if (reference == null || calibrationTargets.isEmpty()) {
					logPrewarmBoundary(
						event = "reference-unavailable",
						detail = "session=$session reference=${reference != null} " +
							"targets=${calibrationTargets.size}"
					)
					finishPrewarm(
						if (reference == null) {
							ReaderPageRasterBatchOutcome.Deferred(
								stage = "raster-reference",
								pageIndex = plan.centerPageIndex,
								reason = "layout-unstable-current-surface"
							)
						} else {
							ReaderPageRasterBatchOutcome.Failed(
								stage = "calibration-plan",
								pageIndex = plan.centerPageIndex,
								reason = "no-calibration-targets"
							)
						}
					)
					return@obtainRasterReference
				}
				startRasterCalibration(webView, session, plan, kind, reference, calibrationTargets)
			}
		}
	}

	private fun expectedLayoutMode(webView: WebView): String =
		if (webView.width >= webView.height * 1.12f) "spread" else "single"

	private fun startRasterCalibration(
		webView: WebView,
		session: Long,
		plan: ReaderPageRasterPreparationPlan,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		calibrationTargets: List<ReaderPageRasterBatchTarget>
	) {
		rasterPreparationCompleted = 0
		rasterPreparationRequired = calibrationTargets.size
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = calibrationTargets.size
		activePreparationPageNumber = calibrationTargets.firstOrNull()?.pageIndex?.plus(1)
		publishPreparationState(ReaderPagePreparationPhase.Preparing)
		startRasterBatch(
			webView = webView,
			session = session,
			batchLabel = "calibration",
			kind = kind,
			reference = reference,
			targets = calibrationTargets,
			completedOffset = 0,
			totalRequired = calibrationTargets.size
		) { outcome ->
			if (!isPrewarmActive(webView, session)) return@startRasterBatch
			if (outcome != ReaderPageRasterBatchOutcome.Ready) {
				finishPrewarm(outcome)
				return@startRasterBatch
			}
			rasterInteractiveCompleted = rasterInteractiveRequired
			publishPreparationState(ReaderPagePreparationPhase.Preparing)
			startRasterFollowUp(webView, session, plan, kind, calibrationTargets)
		}
	}

	private fun startRasterFollowUp(
		webView: WebView,
		session: Long,
		plan: ReaderPageRasterPreparationPlan,
		kind: ReaderPageTurnTransitionKind,
		calibrationTargets: List<ReaderPageRasterBatchTarget>
	) {
		val calibratedPages = calibrationTargets.mapTo(mutableSetOf()) { target -> target.pageIndex }
		val blockingTargets = checkNotNull(plan.blockingTargetsOrNull()) {
			"Accepted raster preparation plan lost its blocking window"
		}
		val followUpTargets = blockingTargets.filterNot { target -> target.pageIndex in calibratedPages }
		if (followUpTargets.isEmpty()) {
			finishPrewarm(ReaderPageRasterBatchOutcome.Ready)
			return
		}
		val reference = retainedSnapshot(plan.centerPageIndex, kind) ?: run {
			finishPrewarm(
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "follow-up-reference",
					pageIndex = plan.centerPageIndex,
					reason = "retained-snapshot-unavailable"
				)
			)
			return
		}
		val completedOffset = calibrationTargets.size
		val totalRequired = blockingTargets.size
		rasterPreparationRequired = totalRequired
		publishPreparationState(ReaderPagePreparationPhase.Preparing)
		startRasterBatch(
			webView = webView,
			session = session,
			batchLabel = "follow-up",
			kind = kind,
			reference = reference,
			targets = followUpTargets,
			completedOffset = completedOffset,
			totalRequired = totalRequired
		) { outcome -> finishPrewarm(outcome) }
	}

	private fun startRasterBatch(
		webView: WebView,
		session: Long,
		batchLabel: String,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		completedOffset: Int,
		totalRequired: Int,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	) {
		val generation = bundleSource.currentGeneration()
		rasterBatchController.start(
			webView = webView,
			kind = kind,
			reference = reference,
			targets = targets,
			trigger = activeAcquisitionTrigger,
			onStagingStarted = { snapshot ->
				reusePreparationShield(snapshot, session, batchLabel)
			},
			onActiveTarget = { target ->
				if (isPrewarmActive(webView, session) && generation == bundleSource.currentGeneration()) {
					activePreparationPageNumber = target.pageIndex + 1
					publishPreparationState(ReaderPagePreparationPhase.Preparing)
				}
			},
			onHydrationMiss = { target ->
				if (isPrewarmActive(webView, session) && generation == bundleSource.currentGeneration()) {
					enterBlockingPreparation("required-cache-miss:${target.pageIndex}")
				}
			},
			onTargetDurable = { target ->
				if (isPrewarmActive(webView, session) && generation == bundleSource.currentGeneration()) {
					durableRasterPageIndices += target.pageIndex
				}
			},
			onProgress = { completedCount, _ ->
				if (isPrewarmActive(webView, session) && generation == bundleSource.currentGeneration()) {
					rasterPreparationCompleted = completedOffset + completedCount
					rasterPreparationRequired = totalRequired
					rasterInteractiveCompleted = minOf(
						rasterPreparationCompleted,
						rasterInteractiveRequired
					)
					publishPreparationState(ReaderPagePreparationPhase.Preparing)
				}
			},
			onComplete = { outcome ->
				if (!isPrewarmActive(webView, session) || generation != bundleSource.currentGeneration()) {
					return@start
				}
				onComplete(outcome)
			}
		)
	}

	private fun obtainRasterReference(
		webView: WebView,
		session: Long,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		captureGeometry: ReaderPageTurnCaptureGeometry?,
		onResolved: (ReaderPageSlideSnapshot?) -> Unit
	) {
		val generation = bundleSource.currentGeneration()
		val isCurrent = {
			generation == bundleSource.currentGeneration() &&
				isPrewarmActive(webView, session)
		}
		currentLayoutSnapshot(pageIndex, kind)?.let { reference ->
			if (isCurrent()) {
				onResolved(reference)
			} else {
				reference.release()
				onResolved(null)
			}
			return
		}
		currentReferencePort.captureFresh(
			webView = webView,
			pageIndex = pageIndex,
			kind = kind,
			captureGeometry = captureGeometry,
			generation = generation,
			isCurrent = isCurrent,
			onResolved = onResolved
		)
	}

	private fun isPrewarmSessionActive(session: Long): Boolean =
		prewarmInProgress &&
			prewarmSession == session &&
			!destroyed

	private fun isPrewarmActive(webView: WebView, session: Long): Boolean =
		isPrewarmSessionActive(session) && webView.isAttachedToWindow

	private fun finishPrewarm(outcome: ReaderPageRasterBatchOutcome) {
		val retryAttempt = prewarmRetryAttempt
		val preparationDiagnostic = activePrewarmDiagnostic
		prewarmRetryAttempt = null
		activePrewarmDiagnostic = null
		prewarmInProgress = false
		logLoadingEvent(
			event = "session-finished",
			detail = "session=$prewarmSession outcome=$outcome " +
				"completed=$rasterPreparationCompleted/$rasterPreparationRequired " +
				"interactive=$rasterInteractiveCompleted/$rasterInteractiveRequired"
		)
		logPrewarmBoundary(
			event = "batch-complete",
			detail = "outcome=$outcome completed=$rasterPreparationCompleted/$rasterPreparationRequired"
		)
		when (outcome) {
			ReaderPageRasterBatchOutcome.Ready -> {
				preparationDiagnostic?.let { operation ->
					diagnostics?.preparation(
						operation,
						ReaderPagePreparationDiagnosticState.Ready
					)
				}
				val backgroundPrefetch = candidateBackgroundPrefetch
				rasterPreparationCompleted = rasterPreparationRequired
				rasterInteractiveCompleted = rasterInteractiveRequired
				hasPreparedBefore = candidateBlockingPageIndices.isNotEmpty()
				preparedChapterRange = candidateChapterRange ?: preparedChapterRange
				preparedPageCount = candidatePageCount
				preparedStep = candidateStep
				durableRasterPageIndices += candidateBlockingPageIndices
				preparedRepairPageIndices = candidateRepairPageIndices
				publishPreparationState(ReaderPagePreparationPhase.Ready)
				backgroundPrefetch?.let(::publishDurableAdjacentChapterPlan)
			}
			ReaderPageRasterBatchOutcome.Cancelled -> {
				preparationDiagnostic?.let { operation ->
					diagnostics?.preparation(
						operation,
						ReaderPagePreparationDiagnosticState.Cancelled
					)
				}
				publishPreparationState(
					if (hasPreparedBefore) ReaderPagePreparationPhase.Ready
					else ReaderPagePreparationPhase.Idle
				)
			}
			is ReaderPageRasterBatchOutcome.Deferred -> {
				logPrewarmBoundary(
					event = "deferred",
					detail = "stage=${outcome.stage} pageIndex=${outcome.pageIndex ?: "none"} " +
						"reason=${outcome.reason}"
				)
				val attempt = checkNotNull(retryAttempt) {
					"Deferred prewarm completed without a retry attempt"
				}
				val reason = readerPageRasterDeferralReason(outcome)
				preparationDiagnostic?.let { operation ->
					diagnostics?.preparation(
						operation = operation,
						state = ReaderPagePreparationDiagnosticState.Deferred,
						reason = reason,
						eventVersion = attempt.observedVersion(reason)
					)
				}
				if (attempt.retryCount >= ReaderPageRasterMaxAutomaticRetries) {
					preparationDiagnostic?.let { operation ->
						diagnostics?.preparation(
							operation,
							ReaderPagePreparationDiagnosticState.Failed,
							reason
						)
					}
					publishPreparationState(
						phase = ReaderPagePreparationPhase.Failed,
						error = "Page preparation did not become ready.",
						retryable = true
					)
				} else {
					deferredPrewarmDiagnostic = preparationDiagnostic
					publishPreparationState(ReaderPagePreparationPhase.Preparing)
					deferPrewarm(
						reason = reason,
						retryAttempt = attempt
					)
				}
			}
			is ReaderPageRasterBatchOutcome.Failed -> {
				preparationDiagnostic?.let { operation ->
					diagnostics?.preparation(
						operation,
						ReaderPagePreparationDiagnosticState.Failed
					)
				}
				Logger.e(
					ReaderPageRasterPreparationControllerTag,
					"Page raster preparation failed ${outcome.diagnostic}"
				)
				publishPreparationState(
					phase = ReaderPagePreparationPhase.Failed,
					error = outcome.userMessage,
					retryable = true
				)
			}
		}
		if (outcome != ReaderPageRasterBatchOutcome.Ready) {
			candidateChapterRange = null
			candidateBlockingPageIndices = emptySet()
			candidatePageCount = 0
			candidateStep = 1
			candidateRepairPageIndices = emptySet()
		}
		candidateBackgroundPrefetch = null
		rasterPreparationCompleted = 0
		rasterPreparationRequired = 0
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = 0
		activePreparationPageNumber = null
		removePreparationShield(reason = "session-finished:$outcome")
		startNextRasterRepair()
	}

	private fun deferPrewarm(
		reason: ReaderPageRasterDeferralReason,
		retryAttempt: ReaderPageRasterRetryAttempt
	) {
		deferredPrewarmSessionId = retryAttempt.sessionId
		deferredRetryCoordinator.defer(
			sessionId = retryAttempt.sessionId,
			reason = reason,
			observedVersion = retryAttempt.observedVersion(reason),
			onResumed = { eventVersion ->
				deferredPrewarmDiagnostic?.let { operation ->
					diagnostics?.preparation(
						operation = operation,
						state = ReaderPagePreparationDiagnosticState.Resumed,
						reason = reason,
						eventVersion = eventVersion
					)
				}
			},
			retry = retry@{
				if (deferredPrewarmSessionId != retryAttempt.sessionId || destroyed) {
					return@retry
				}
				deferredPrewarmSessionId = null
				pendingPrewarmRetryCount = retryAttempt.retryCount + 1
				onRequestPrewarm()
			},
			cancel = {
				if (deferredPrewarmSessionId == retryAttempt.sessionId) {
					deferredPrewarmSessionId = null
				}
			}
		)
		if (
			reason == ReaderPageRasterDeferralReason.LayoutUnstable ||
			reason == ReaderPageRasterDeferralReason.WebViewDetached
		) {
			onAwaitHostEvent(reason)
		}
	}

	private fun observeBackgroundPrefetchWebView(webView: WebView) {
		if (backgroundPrefetchWebView !== webView) {
			backgroundPrefetchWebView?.removeOnAttachStateChangeListener(
				backgroundPrefetchAttachmentListener
			)
			backgroundPrefetchWebView = webView
			webView.addOnAttachStateChangeListener(backgroundPrefetchAttachmentListener)
		}
		adjacentChapterPrefetchCoordinator.onHostAvailabilityChanged(
			webView.isAttachedToWindow
		)
	}

	private fun clearBackgroundPrefetchWebView() {
		backgroundPrefetchWebView?.removeOnAttachStateChangeListener(
			backgroundPrefetchAttachmentListener
		)
		backgroundPrefetchWebView = null
	}

	private fun publishDurableAdjacentChapterPlan(
		prefetch: ReaderPageRasterBackgroundPrefetch
	) {
		durableBackgroundPrefetch = prefetch
		val profileEpoch = currentRasterProfileEpoch ?: return
		if (prefetch.chapters.isEmpty()) {
			adjacentChapterPrefetchCoordinator.clear()
			clearBackgroundPrefetchWebView()
			return
		}
		observeBackgroundPrefetchWebView(prefetch.webView)
		adjacentChapterPrefetchCoordinator.replaceDurablePlan(
			prefetch.qualifiedPlan(profileEpoch)
		)
	}

	private fun emitPrefetchDiagnostic(
		submission: ReaderPageAdjacentChapterPrefetchSubmission,
		state: ReaderPagePrefetchDiagnosticState
	) {
		val runtime = diagnostics ?: return
		val startedAt = if (state == ReaderPagePrefetchDiagnosticState.Queued) {
			backgroundPrefetchDiagnosticStarts.getOrPut(submission.sessionId, runtime::now)
		} else {
			backgroundPrefetchDiagnosticStarts[submission.sessionId] ?: return
		}
		runtime.prefetch(
			prefetchSession = submission.sessionId,
			rasterEpoch = submission.key.rasterEpoch,
			state = state,
			targetCount = submission.targets.size,
			startedAtMs = startedAt
		)
		if (
			state == ReaderPagePrefetchDiagnosticState.Completed ||
			state == ReaderPagePrefetchDiagnosticState.Cancelled ||
			state == ReaderPagePrefetchDiagnosticState.Failed
		) {
			backgroundPrefetchDiagnosticStarts.remove(submission.sessionId)
		}
	}

	private fun scheduleBackgroundPrefetch(
		submission: ReaderPageAdjacentChapterPrefetchSubmission
	) {
		emitPrefetchDiagnostic(submission, ReaderPagePrefetchDiagnosticState.Queued)
		val prefetch = durableBackgroundPrefetch
		val profileEpoch = currentRasterProfileEpoch
		if (
			prefetch == null ||
			profileEpoch == null ||
			prefetch.qualifiedPlan(profileEpoch).key != submission.key
		) {
			emitPrefetchDiagnostic(
				submission,
				ReaderPagePrefetchDiagnosticState.Cancelled
			)
			adjacentChapterPrefetchCoordinator.onBatchFinished(submission)
			return
		}
		logLoadingEvent(
			event = "background-prefetch-scheduled",
			detail = "session=${submission.sessionId} chapter=${submission.chapter.identity.chapterIndex} " +
				"direction=${submission.chapter.identity.direction} pages=${submission.targets.size} " +
				"generation=${prefetch.generation}"
		)
		host.post {
			if (!isBackgroundPrefetchActive(submission, prefetch)) return@post
			Looper.myQueue().addIdleHandler {
				if (isBackgroundPrefetchActive(submission, prefetch)) {
					startBackgroundPrefetch(submission, prefetch)
				}
				false
			}
		}
	}

	private fun startBackgroundPrefetch(
		submission: ReaderPageAdjacentChapterPrefetchSubmission,
		prefetch: ReaderPageRasterBackgroundPrefetch
	) {
		if (!isBackgroundPrefetchActive(submission, prefetch)) return
		emitPrefetchDiagnostic(submission, ReaderPagePrefetchDiagnosticState.Running)
		val reference = retainedSnapshot(prefetch.centerPageIndex, prefetch.kind)
		if (reference == null) {
			emitPrefetchDiagnostic(
				submission,
				ReaderPagePrefetchDiagnosticState.Failed
			)
			logLoadingEvent(
				event = "background-prefetch-failed",
				detail = "session=${submission.sessionId} reason=reference-unavailable " +
					"chapter=${submission.chapter.identity.chapterIndex} generation=${prefetch.generation}"
			)
			adjacentChapterPrefetchCoordinator.onBatchFinished(submission)
			return
		}
		backgroundBatchSubmission = submission
		logLoadingEvent(
			event = "background-prefetch-started",
			detail = "session=${submission.sessionId} chapter=${submission.chapter.identity.chapterIndex} " +
				"pages=${submission.targets.size} generation=${prefetch.generation}"
		)
		val started = rasterBackgroundBatchController.start(
			webView = prefetch.webView,
			kind = prefetch.kind,
			reference = reference,
			targets = submission.targets,
			trigger = ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
			onStagingStarted = { snapshot ->
				showBackgroundPrefetchShield(snapshot, submission)
			},
			onTargetDurable = { target ->
				if (
					adjacentChapterPrefetchCoordinator.onTargetDurable(
						submission = submission,
						pageIndex = target.pageIndex
					)
				) {
					durableRasterPageIndices += target.pageIndex
				}
			},
			onProgress = { completed, required ->
				if (isBackgroundPrefetchActive(submission, prefetch)) {
					logLoadingEvent(
						event = "background-prefetch-progress",
						detail = "session=${submission.sessionId} completed=$completed/$required " +
							"chapter=${submission.chapter.identity.chapterIndex} " +
							"generation=${prefetch.generation}"
					)
				}
			},
			onComplete = backgroundComplete@ { outcome ->
				if (backgroundBatchSubmission == submission) {
					backgroundBatchSubmission = null
				}
				if (!adjacentChapterPrefetchCoordinator.onBatchFinished(submission)) {
					return@backgroundComplete
				}
				removeBackgroundPrefetchShield(submission.sessionId)
				emitPrefetchDiagnostic(
					submission,
					when (outcome) {
						ReaderPageRasterBatchOutcome.Ready ->
							ReaderPagePrefetchDiagnosticState.Completed
						ReaderPageRasterBatchOutcome.Cancelled ->
							ReaderPagePrefetchDiagnosticState.Cancelled
						else -> ReaderPagePrefetchDiagnosticState.Failed
					}
				)
				logLoadingEvent(
					event = if (outcome == ReaderPageRasterBatchOutcome.Ready) {
						"background-prefetch-completed"
					} else {
						"background-prefetch-failed"
					},
					detail = "session=${submission.sessionId} outcome=$outcome " +
						"chapter=${submission.chapter.identity.chapterIndex} " +
						"generation=${prefetch.generation}"
				)
			}
		)
		if (!started && backgroundBatchSubmission == submission) {
			backgroundBatchSubmission = null
			removeBackgroundPrefetchShield(submission.sessionId)
			emitPrefetchDiagnostic(
				submission,
				ReaderPagePrefetchDiagnosticState.Failed
			)
			adjacentChapterPrefetchCoordinator.onBatchFinished(submission)
		}
	}

	private fun isBackgroundPrefetchActive(
		submission: ReaderPageAdjacentChapterPrefetchSubmission,
		prefetch: ReaderPageRasterBackgroundPrefetch
	): Boolean =
		!destroyed &&
			!prewarmInProgress &&
			activeRasterRepairPageIndex == null &&
			rasterRepairCallbacks.isEmpty() &&
			prefetch === durableBackgroundPrefetch &&
			currentRasterProfileEpoch == submission.key.rasterProfileEpoch &&
			prefetch.generation == submission.key.rasterEpoch &&
			prefetch.generation == bundleSource.currentGeneration() &&
			prefetch.webView.isAttachedToWindow &&
			adjacentChapterPrefetchCoordinator.isActive(submission)

	private fun showBackgroundPrefetchShield(
		snapshot: ReaderPageSlideSnapshot,
		submission: ReaderPageAdjacentChapterPrefetchSubmission
	) {
		if (
			backgroundBatchSubmission != submission ||
			!adjacentChapterPrefetchCoordinator.isActive(submission)
		) return

		val currentShield = backgroundPrefetchShield
		val currentSnapshot = backgroundPrefetchShieldSnapshot
		val sameVisualSurface = currentShield != null &&
			currentSnapshot != null &&
			currentSnapshot.key == snapshot.key &&
			currentSnapshot.surfaceRectInWindow == snapshot.surfaceRectInWindow &&
			!currentSnapshot.bitmap.isRecycled
		if (sameVisualSurface) {
			backgroundPrefetchShieldSessionId = submission.sessionId
			currentShield.bringToFront()
			return
		}

		snapshot.retain()
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val rect = snapshot.surfaceRectInWindow
		val layout = FrameLayout.LayoutParams(rect.width(), rect.height()).apply {
			leftMargin = rect.left - hostLocation[0]
			topMargin = rect.top - hostLocation[1]
		}
		val shield = currentShield ?: ImageView(host.context).apply {
			scaleType = ImageView.ScaleType.FIT_XY
			isClickable = false
			isFocusable = false
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		shield.setImageBitmap(snapshot.bitmap)
		shield.layoutParams = layout
		if (currentShield == null) host.addView(shield) else shield.bringToFront()

		backgroundPrefetchShield = shield
		backgroundPrefetchShieldSnapshot = snapshot
		backgroundPrefetchShieldSessionId = submission.sessionId
		currentSnapshot?.release()
	}

	private fun removeBackgroundPrefetchShield(sessionId: Long? = null) {
		if (
			sessionId != null &&
			backgroundPrefetchShieldSessionId != sessionId
		) return
		val shield = backgroundPrefetchShield
		val snapshot = backgroundPrefetchShieldSnapshot
		backgroundPrefetchShield = null
		backgroundPrefetchShieldSnapshot = null
		backgroundPrefetchShieldSessionId = null
		shield?.setImageDrawable(null)
		(shield?.parent as? ViewGroup)?.removeView(shield)
		snapshot?.release()
	}

	private fun cancelBackgroundPrefetchSubmission(
		submission: ReaderPageAdjacentChapterPrefetchSubmission
	) {
		emitPrefetchDiagnostic(
			submission,
			ReaderPagePrefetchDiagnosticState.Cancelled
		)
		val batchStarted = backgroundBatchSubmission == submission
		if (batchStarted) {
			backgroundBatchSubmission = null
			rasterBackgroundBatchController.cancel(
				trackVisualRestoration {
					removeBackgroundPrefetchShield(submission.sessionId)
				}
			)
		} else {
			removeBackgroundPrefetchShield(submission.sessionId)
		}
		logLoadingEvent(
			event = "background-prefetch-failed",
			detail = "session=${submission.sessionId} reason=cancelled:eligibility-changed " +
				"chapter=${submission.chapter.identity.chapterIndex} " +
				"generation=${submission.key.rasterEpoch} started=$batchStarted"
		)
	}

	private fun cancelBackgroundPrefetch(reason: String) {
		candidateBackgroundPrefetch = null
		durableBackgroundPrefetch = null
		adjacentChapterPrefetchCoordinator.clear()
		rasterBackgroundBatchController.resetRetryState()
		clearBackgroundPrefetchWebView()
		logLoadingEvent(
			event = "background-prefetch-cancelled",
			detail = "reason=$reason generation=${bundleSource.currentGeneration()}"
		)
	}

	private fun logPrewarmBoundary(event: String, detail: String? = null) {
		val trace = buildString {
			append(event)
			if (!detail.isNullOrBlank()) {
				append(' ')
				append(detail)
			}
		}
		if (lastPrewarmBoundary == trace) return
		lastPrewarmBoundary = trace
		Logger.i(
			ReaderPageRasterPreparationControllerTag,
			"Page raster passive prewarm $trace"
		)
	}

	private fun deferPrewarmForWebViewDetach() {
		if (!prewarmInProgress) return
		val session = prewarmSession
		rasterCacheInitializationJobs.toList().forEach { job -> job.cancel() }
		rasterBatchController.cancel(
			trackVisualRestoration {
				removePreparationShield(
					reason = "session-detached",
					expectedSession = session
				)
				if (!isPrewarmSessionActive(session)) return@trackVisualRestoration
				finishPrewarm(
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "batch-detached",
						pageIndex = currentVisualPageIndex,
						reason = "webview-detached"
					)
				)
			}
		)
	}

	private fun cancelPrewarm(reason: String) {
		rasterCacheInitializationJobs.toList().forEach { job -> job.cancel() }
		prewarmSession += 1
		val preparationDiagnostic =
			activePrewarmDiagnostic ?: deferredPrewarmDiagnostic
		activePrewarmDiagnostic = null
		deferredPrewarmDiagnostic = null
		preparationDiagnostic?.let { operation ->
			diagnostics?.preparation(
				operation,
				ReaderPagePreparationDiagnosticState.Cancelled
			)
		}
		val wasInProgress = prewarmInProgress
		val cancelledSession = prewarmSession - 1
		prewarmInProgress = false
		prewarmRetryAttempt = null
		pendingPrewarmRetryCount = 0
		deferredPrewarmSessionId?.let(deferredRetryCoordinator::cancel)
		deferredPrewarmSessionId = null
		rasterPreparationCompleted = 0
		rasterPreparationRequired = 0
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = 0
		activePreparationPageNumber = null
		if (wasInProgress) {
			rasterBatchController.cancel(
				trackVisualRestoration {
					removePreparationShield(
						reason = "session-cancelled:$reason",
						expectedSession = cancelledSession
					)
				}
			)
		} else {
			removePreparationShield(
				reason = "session-cancelled:$reason",
				expectedSession = cancelledSession
			)
		}
		logLoadingEvent(
			event = "session-cancelled",
			detail = "session=$cancelledSession reason=$reason wasInProgress=$wasInProgress " +
				"visual=$currentVisualPageIndex generation=${bundleSource.currentGeneration()}"
		)
		publishPreparationState(
			if (hasPreparedBefore) ReaderPagePreparationPhase.Ready else ReaderPagePreparationPhase.Idle
		)
	}

	private fun reusePreparationShield(
		snapshot: ReaderPageSlideSnapshot,
		session: Long,
		batchLabel: String
	) {
		val currentShield = preparationShield
		val currentSnapshot = preparationShieldSnapshot
		val sameVisualSurface = currentShield != null &&
			currentSnapshot != null &&
			currentSnapshot.key == snapshot.key &&
			currentSnapshot.surfaceRectInWindow == snapshot.surfaceRectInWindow &&
			!currentSnapshot.bitmap.isRecycled
		if (sameVisualSurface) {
			preparationShieldSession = session
			preparationShieldBatchLabel = batchLabel
			currentShield.bringToFront()
			logLoadingEvent(
				event = "shield-reused",
				detail = shieldDetail(snapshot, session, batchLabel)
			)
			return
		}

		snapshot.retain()
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val rect = snapshot.surfaceRectInWindow
		val layout = FrameLayout.LayoutParams(rect.width(), rect.height()).apply {
			leftMargin = rect.left - hostLocation[0]
			topMargin = rect.top - hostLocation[1]
		}
		val shield = currentShield ?: ImageView(host.context).apply {
			scaleType = ImageView.ScaleType.FIT_XY
			isClickable = false
			isFocusable = false
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		shield.setImageBitmap(snapshot.bitmap)
		shield.layoutParams = layout
		if (currentShield == null) host.addView(shield) else shield.bringToFront()

		preparationShieldSnapshot = snapshot
		preparationShield = shield
		preparationShieldSession = session
		preparationShieldBatchLabel = batchLabel
		currentSnapshot?.release()
		logLoadingEvent(
			event = if (currentShield == null) "shield-attached" else "shield-updated",
			detail = shieldDetail(snapshot, session, batchLabel)
		)
	}

	private fun removePreparationShield(
		reason: String,
		expectedSession: Long? = null
	) {
		if (
			expectedSession != null &&
			preparationShieldSession != expectedSession
		) return
		val shield = preparationShield
		val snapshot = preparationShieldSnapshot
		val session = preparationShieldSession
		val batchLabel = preparationShieldBatchLabel
		preparationShield = null
		preparationShieldSnapshot = null
		preparationShieldSession = null
		preparationShieldBatchLabel = null
		if (shield != null) {
			shield.setImageDrawable(null)
			(shield.parent as? ViewGroup)?.removeView(shield)
			logLoadingEvent(
				event = "shield-removed",
				detail = "reason=$reason session=${session ?: "none"} " +
					"batch=${batchLabel ?: "none"} page=${snapshot?.key?.visualPageIndex ?: "none"}"
			)
		}
		snapshot?.release()
	}

	private fun shieldDetail(
		snapshot: ReaderPageSlideSnapshot,
		session: Long,
		batchLabel: String
	): String = buildString {
		append("session=$session batch=$batchLabel page=${snapshot.key.visualPageIndex}")
		append(" kind=${snapshot.key.kind}")
		append(" bitmap=${snapshot.bitmap.width}x${snapshot.bitmap.height}")
		append(" rect=${snapshot.surfaceRectInWindow.flattenToString()}")
		append(" bitmapIdentity=${System.identityHashCode(snapshot.bitmap)}")
	}

	private fun logLoadingEvent(event: String, detail: String) {
		Logger.i(
			ReaderPageRasterPreparationControllerTag,
			"Ebook loading event=$event $detail"
		)
	}
}
