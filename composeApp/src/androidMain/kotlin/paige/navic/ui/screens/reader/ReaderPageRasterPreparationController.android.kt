package paige.navic.ui.screens.reader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import paige.navic.reader.ReaderChapterRasterGenerationState
import paige.navic.reader.ReaderDecodedWorkingSetState
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageReadinessState
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
	val targets: List<ReaderPageRasterBatchTarget>,
	val generation: Long
)

private data class ReaderPageRasterRetryAttempt(
	val sessionId: Long,
	val retryCount: Int,
	val observedVersions: Map<ReaderPageRasterDeferralReason, Long>
) {
	fun observedVersion(reason: ReaderPageRasterDeferralReason): Long =
		checkNotNull(observedVersions[reason])
}

internal fun readerPageTurnCanStartPassivePrewarm(
	destroyed: Boolean,
	sessionEnabled: Boolean,
	visualCommitPending: Boolean,
	idle: Boolean
): Boolean = !destroyed && sessionEnabled && !visualCommitPending && idle

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
	private val onRequestPrewarm: () -> Unit = {},
	private val onAwaitHostEvent: (ReaderPageRasterDeferralReason) -> Unit = {},
	private val onPreparationStateChange: (ReaderPagePreparationState) -> Unit = {}
) {
	private val deferredRetryCoordinator = ReaderPageRasterDeferredRetryCoordinator()
	private val rasterBatchController = ReaderPageRasterBatchController(bundleSource)
	private val rasterRepairBatchController = ReaderPageRasterBatchController(bundleSource)
	private val rasterBackgroundBatchController = ReaderPageRasterBatchController(bundleSource)
	private val rasterRepairCallbacks = linkedMapOf<
		Int,
		MutableList<(ReaderPageRasterRepairResult) -> Unit>
	>()
	private var activeRasterRepairPageIndex: Int? = null
	private var deferredRasterRepairPageIndex: Int? = null
	private var deferredRasterRepairSessionId: Long? = null
	private var resumePrewarmAfterRasterRepairs = false
	private var currentVisualPageIndex: Int? = null
	private var preparedChapterRange: ReaderPageRasterPreparedChapterRange? = null
	private var candidateChapterRange: ReaderPageRasterPreparedChapterRange? = null
	private var preparedRepairPageIndices: Set<Int> = emptySet()
	private var candidateRepairPageIndices: Set<Int> = emptySet()
	private var candidateBackgroundPrefetch: ReaderPageRasterBackgroundPrefetch? = null
	private var prewarmSession = 0L
	private var prewarmRetryAttempt: ReaderPageRasterRetryAttempt? = null
	private var pendingPrewarmRetryCount = 0
	private var deferredPrewarmSessionId: Long? = null
	private var nextDeferredRetrySessionId = 0L
	private var prewarmInProgress = false
	private var rasterPreparationCompleted = 0
	private var rasterPreparationRequired = 0
	private var rasterInteractiveCompleted = 0
	private var rasterInteractiveRequired = 0
	private var activePreparationPageNumber: Int? = null
	private var lastPrewarmBoundary: String? = null
	private var lastPreparationStateTrace: String? = null
	private var hasPreparedBefore = false
	private var backgroundPrefetchSession = 0L
	private var backgroundPrefetchInProgress = false
	private var preparationShield: ImageView? = null
	private var preparationShieldSnapshot: ReaderPageSlideSnapshot? = null
	private var preparationShieldSession: Long? = null
	private var preparationShieldBatchLabel: String? = null
	private var destroyed = false
	private val applicationContext = host.context.applicationContext
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

	fun updateBitmapQuality(value: String?) {
		if (!bundleSource.updateBitmapQuality(normalizeReaderPageBitmapQuality(value))) return
		cancelBackgroundPrefetch("bitmap-quality-changed")
		cancelRasterRepairs("bitmap-quality-changed")
		cancelPrewarm(reason = "bitmap-quality-changed")
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

	fun destroy() {
		if (destroyed) return
		destroyed = true
		cancelBackgroundPrefetch("destroy")
		cancelRasterRepairs("destroy")
		cancelPrewarm(reason = "destroy")
		cancelAllDeferredRetries()
		bundleSource.close()
		currentVisualPageIndex = null
		applicationContext.unregisterComponentCallbacks(memoryCallbacks)
	}

	fun invalidate(reason: String, clearVisualPageIndex: Boolean = false) {
		if (destroyed) return
		cancelBackgroundPrefetch("invalidate:$reason")
		cancelRasterRepairs("invalidate:$reason")
		cancelPrewarm(reason = "invalidate:$reason")
		bundleSource.invalidate(reason)
		preparedChapterRange = null
		candidateChapterRange = null
		preparedRepairPageIndices = emptySet()
		candidateRepairPageIndices = emptySet()
		if (clearVisualPageIndex) currentVisualPageIndex = null
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
			return
		}
		if (pageIndex < 0) return
		if (reason == "page-turn:exact" && preparedChapterRange?.contains(pageIndex) == true) {
			currentVisualPageIndex = pageIndex
			logLoadingEvent(
				event = "ordinary-turn-reused",
				detail = "page=$pageIndex chapter=$preparedChapterRange " +
					"generation=${bundleSource.currentGeneration()}"
			)
			return
		}
		if (currentVisualPageIndex == pageIndex) {
			if (reason == "page-turn:exact") onRequestPrewarm()
			return
		}
		cancelBackgroundPrefetch("visual-index-changed:${reason ?: "unspecified"}")
		cancelRasterRepairs("visual-index-changed:${reason ?: "unspecified"}")
		cancelPrewarm(reason = "visual-index-changed:${reason ?: "unspecified"}")
		currentVisualPageIndex = pageIndex
		logLoadingEvent(
			event = "visual-index-synchronized",
			detail = "page=$pageIndex reason=$reason generation=${bundleSource.currentGeneration()} retained=true"
		)
		onRequestPrewarm()
	}

	fun repairRasterPage(
		pageIndex: Int,
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
		val callbacks = rasterRepairCallbacks.getOrPut(pageIndex) { mutableListOf() }
		callbacks += onComplete
		logLoadingEvent(
			event = "page-repair-requested",
			detail = "page=$pageIndex queued=${activeRasterRepairPageIndex != null} " +
				"pages=$repairPages generation=${bundleSource.currentGeneration()}"
		)
		startNextRasterRepair()
	}

	private fun startNextRasterRepair() {
		if (
			activeRasterRepairPageIndex != null ||
			deferredRasterRepairPageIndex != null ||
			prewarmInProgress ||
			destroyed
		) return
		val pageIndex = rasterRepairCallbacks.keys.firstOrNull() ?: return
		deferredPrewarmSessionId?.let { sessionId ->
			if (deferredRetryCoordinator.cancel(sessionId)) {
				resumePrewarmAfterRasterRepairs = true
			}
		}
		cancelBackgroundPrefetch("page-repair")
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
			bundleSource.retainedSnapshot(index, kind)
		}
		if (reference == null) {
			finishRasterRepair(
				pageIndex = pageIndex,
				result = ReaderPageRasterRepairResult.Deferred(
					ReaderPageRasterDeferralReason.ContentNotReady
				),
				detail = "reference-unavailable:$centerOrdinal"
			)
			onRequestPrewarm()
			return
		}
		activeRasterRepairPageIndex = pageIndex
		val generation = bundleSource.currentGeneration()
		val repairPages = preparedRepairPageIndices.toSet()
		val started = rasterRepairBatchController.start(
			webView = webView,
			kind = kind,
			reference = reference,
			targets = listOf(
				ReaderPageRasterBatchTarget(pageIndex, ReaderPageRasterPriority.CurrentChapter)
			),
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
							rasterEpoch = generation
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
		if (activeRasterRepairPageIndex == pageIndex) activeRasterRepairPageIndex = null
		deferredRasterRepairPageIndex = pageIndex
		deferredRasterRepairSessionId = retryAttempt.sessionId
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
		if (activeRasterRepairPageIndex == pageIndex) activeRasterRepairPageIndex = null
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
		rasterRepairCallbacks.clear()
		activeRasterRepairPageIndex = null
		deferredRasterRepairSessionId?.let(deferredRetryCoordinator::cancel)
		deferredRasterRepairSessionId = null
		deferredRasterRepairPageIndex = null
		resumePrewarmAfterRasterRepairs = false
		rasterRepairBatchController.cancel()
		if (pages.isNotEmpty()) {
			logLoadingEvent(
				event = "page-repair-failed",
				detail = "pages=${pages.joinToString(",")} reason=cancelled:$reason"
			)
		}
		callbacks.forEach { callback -> callback(ReaderPageRasterRepairResult.Cancelled) }
	}

	fun prewarmAdjacent(): Boolean {
		if (prewarmInProgress) return true
		if (
			activeRasterRepairPageIndex != null ||
			deferredRasterRepairPageIndex != null ||
			rasterRepairCallbacks.isNotEmpty()
		) return false
		if (!readerPageTurnCanStartPassivePrewarm(
			destroyed = destroyed,
			sessionEnabled = true,
			visualCommitPending = false,
			idle = true
		)) return false
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: return false
		deferredPrewarmSessionId?.let(deferredRetryCoordinator::cancel)
		cancelBackgroundPrefetch("blocking-session-started")
		cancelRasterRepairs("blocking-session-started")
		prewarmRetryAttempt = newRetryAttempt(pendingPrewarmRetryCount)
		pendingPrewarmRetryCount = 0
		prewarmInProgress = true
		candidateChapterRange = null
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
		queryRasterPreparationPlan(webView, session)
		return true
	}

	private fun queryRasterPreparationPlan(webView: WebView, session: Long) {
		if (!isPrewarmActive(webView, session)) return
		val centerExpression = currentVisualPageIndex?.toString() ?: "null"
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(" +
				"$centerExpression) ?? null)"
		) { encoded ->
			if (!isPrewarmActive(webView, session)) return@evaluateJavascript
			val plan = readerPageRasterPreparationPlan(encoded)
			if (plan == null) {
				logPrewarmBoundary("plan-unavailable", "session=$session")
				finishPrewarm(
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "raster-plan",
						pageIndex = currentVisualPageIndex,
						reason = "pagination-not-ready"
					)
				)
				return@evaluateJavascript
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
				return@evaluateJavascript
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
				return@evaluateJavascript
			}
			logPrewarmBoundary(
				event = "plan-accepted",
				detail = "session=$session layout=${plan.layoutMode} center=${plan.centerPageIndex} " +
					"targets=${plan.targets.size}"
			)
			candidateChapterRange = plan.preparedChapterRange()
			candidateRepairPageIndices = plan.preparedRepairPageIndices()
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
				targets = plan.targets,
				generation = bundleSource.currentGeneration()
			)
			val calibrationTargets = readerPageRasterCalibrationTargets(plan.targets)
			obtainRasterReference(webView, session, plan.centerPageIndex, kind) { reference ->
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
			totalRequired = calibrationTargets.size,
			protectForeground = true
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
		val blockingTargets = readerPageRasterBlockingTargets(plan.targets)
		val followUpTargets = blockingTargets.filterNot { target -> target.pageIndex in calibratedPages }
		if (followUpTargets.isEmpty()) {
			finishPrewarm(ReaderPageRasterBatchOutcome.Ready)
			return
		}
		val reference = bundleSource.retainedSnapshot(plan.centerPageIndex, kind) ?: run {
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
			totalRequired = totalRequired,
			protectForeground = false
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
		protectForeground: Boolean,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	) {
		val generation = bundleSource.currentGeneration()
		rasterBatchController.start(
			webView = webView,
			kind = kind,
			reference = reference,
			targets = targets,
			onStagingStarted = { snapshot ->
				if (protectForeground) {
					reusePreparationShield(snapshot, session, batchLabel)
				} else {
					logLoadingEvent(
						event = "shield-skipped",
						detail = "session=$session batch=$batchLabel page=${snapshot.key.visualPageIndex} " +
							"reason=passive-prewarm"
					)
				}
			},
			onActiveTarget = { target ->
				if (isPrewarmActive(webView, session) && generation == bundleSource.currentGeneration()) {
					activePreparationPageNumber = target.pageIndex + 1
					publishPreparationState(ReaderPagePreparationPhase.Preparing)
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
		onResolved: (ReaderPageSlideSnapshot?) -> Unit
	) {
		bundleSource.retainedSnapshot(pageIndex, kind)?.let { retained ->
			onResolved(retained)
			return
		}
		val generation = bundleSource.currentGeneration()
		bundleSource.captureCurrentSurface(webView, generation) { current ->
			if (current == null || !isPrewarmActive(webView, session)) {
				current?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				onResolved(null)
				return@captureCurrentSurface
			}
			val snapshot = bundleSource.cacheCurrentSnapshot(pageIndex, kind, current, generation)
			snapshot?.retain()
			onResolved(snapshot)
		}
	}

	private fun isPrewarmActive(webView: WebView, session: Long): Boolean =
		prewarmInProgress &&
			prewarmSession == session &&
			!destroyed &&
			webView.isAttachedToWindow

	private fun finishPrewarm(outcome: ReaderPageRasterBatchOutcome) {
		val retryAttempt = prewarmRetryAttempt
		prewarmRetryAttempt = null
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
				val backgroundPrefetch = candidateBackgroundPrefetch
				rasterPreparationCompleted = rasterPreparationRequired
				rasterInteractiveCompleted = rasterInteractiveRequired
				hasPreparedBefore = rasterInteractiveRequired > 0 || hasPreparedBefore
				preparedChapterRange = candidateChapterRange ?: preparedChapterRange
				preparedRepairPageIndices = candidateRepairPageIndices
				publishPreparationState(ReaderPagePreparationPhase.Ready)
				backgroundPrefetch?.let(::scheduleBackgroundPrefetch)
			}
			ReaderPageRasterBatchOutcome.Cancelled -> publishPreparationState(
				if (hasPreparedBefore) ReaderPagePreparationPhase.Ready
				else ReaderPagePreparationPhase.Idle
			)
			is ReaderPageRasterBatchOutcome.Deferred -> {
				logPrewarmBoundary(
					event = "deferred",
					detail = "stage=${outcome.stage} pageIndex=${outcome.pageIndex ?: "none"} " +
						"reason=${outcome.reason}"
				)
				val attempt = checkNotNull(retryAttempt) {
					"Deferred prewarm completed without a retry attempt"
				}
				if (attempt.retryCount >= ReaderPageRasterMaxAutomaticRetries) {
					publishPreparationState(
						phase = ReaderPagePreparationPhase.Failed,
						error = "Page preparation did not become ready.",
						retryable = true
					)
				} else {
					publishPreparationState(ReaderPagePreparationPhase.Preparing)
					deferPrewarm(
						reason = readerPageRasterDeferralReason(outcome),
						retryAttempt = attempt
					)
				}
			}
			is ReaderPageRasterBatchOutcome.Failed -> {
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

	private fun scheduleBackgroundPrefetch(prefetch: ReaderPageRasterBackgroundPrefetch) {
		val targets = readerPageRasterBackgroundTargets(prefetch.targets)
		if (targets.isEmpty()) return
		cancelBackgroundPrefetch("rescheduled")
		val session = ++backgroundPrefetchSession
		logLoadingEvent(
			event = "background-prefetch-scheduled",
			detail = "session=$session center=${prefetch.centerPageIndex} " +
				"pages=${targets.joinToString(",") { target -> target.pageIndex.toString() }} " +
				"generation=${prefetch.generation}"
		)
		host.post {
			if (!isBackgroundPrefetchActive(session, prefetch)) return@post
			Looper.myQueue().addIdleHandler {
				if (isBackgroundPrefetchActive(session, prefetch)) {
					startBackgroundPrefetch(session, prefetch, targets)
				}
				false
			}
		}
	}

	private fun startBackgroundPrefetch(
		session: Long,
		prefetch: ReaderPageRasterBackgroundPrefetch,
		targets: List<ReaderPageRasterBatchTarget>
	) {
		if (!isBackgroundPrefetchActive(session, prefetch)) return
		val reference = bundleSource.retainedSnapshot(prefetch.centerPageIndex, prefetch.kind)
		if (reference == null) {
			logLoadingEvent(
				event = "background-prefetch-failed",
				detail = "session=$session reason=reference-unavailable " +
					"center=${prefetch.centerPageIndex} generation=${prefetch.generation}"
			)
			return
		}
		backgroundPrefetchInProgress = true
		logLoadingEvent(
			event = "background-prefetch-started",
			detail = "session=$session pages=${targets.size} generation=${prefetch.generation}"
		)
		val started = rasterBackgroundBatchController.start(
			webView = prefetch.webView,
			kind = prefetch.kind,
			reference = reference,
			targets = targets,
			onProgress = { completed, required ->
				if (isBackgroundPrefetchActive(session, prefetch)) {
					logLoadingEvent(
						event = "background-prefetch-progress",
						detail = "session=$session completed=$completed/$required " +
							"generation=${prefetch.generation}"
					)
				}
			},
			onComplete = backgroundComplete@ { outcome ->
				if (session != backgroundPrefetchSession) return@backgroundComplete
				backgroundPrefetchInProgress = false
				if (outcome == ReaderPageRasterBatchOutcome.Ready) {
					logLoadingEvent(
						event = "background-prefetch-completed",
						detail = "session=$session outcome=$outcome generation=${prefetch.generation}"
					)
				} else {
					logLoadingEvent(
						event = "background-prefetch-failed",
						detail = "session=$session outcome=$outcome generation=${prefetch.generation}"
					)
				}
			}
		)
		if (!started && session == backgroundPrefetchSession) {
			backgroundPrefetchInProgress = false
		}
	}

	private fun isBackgroundPrefetchActive(
		session: Long,
		prefetch: ReaderPageRasterBackgroundPrefetch
	): Boolean =
		session == backgroundPrefetchSession &&
			!destroyed &&
			!prewarmInProgress &&
			activeRasterRepairPageIndex == null &&
			rasterRepairCallbacks.isEmpty() &&
			prefetch.generation == bundleSource.currentGeneration() &&
			prefetch.webView.isAttachedToWindow

	private fun cancelBackgroundPrefetch(reason: String) {
		val wasInProgress = backgroundPrefetchInProgress
		backgroundPrefetchSession += 1
		backgroundPrefetchInProgress = false
		candidateBackgroundPrefetch = null
		if (wasInProgress) rasterBackgroundBatchController.cancel()
		if (wasInProgress) {
			logLoadingEvent(
				event = "background-prefetch-failed",
				detail = "reason=cancelled:$reason generation=${bundleSource.currentGeneration()}"
			)
		}
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

	private fun cancelPrewarm(reason: String) {
		prewarmSession += 1
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
		if (wasInProgress) rasterBatchController.cancel()
		logLoadingEvent(
			event = "session-cancelled",
			detail = "session=$cancelledSession reason=$reason wasInProgress=$wasInProgress " +
				"visual=$currentVisualPageIndex generation=${bundleSource.currentGeneration()}"
		)
		removePreparationShield(reason = "session-cancelled:$reason")
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

	private fun removePreparationShield(reason: String) {
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
