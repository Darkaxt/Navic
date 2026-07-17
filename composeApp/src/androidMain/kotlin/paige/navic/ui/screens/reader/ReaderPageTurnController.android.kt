package paige.navic.ui.screens.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageTurnEffect
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPreparationMode
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageSlideCoordinator
import paige.navic.reader.ReaderPageSlideCoordinatorEffect
import paige.navic.reader.ReaderPageTurnStateMachine
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.readerPagePreparationState
import paige.navic.util.core.Logger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val ReaderPageTurnControllerTag = "ReaderPageTurnController"
internal const val ReaderPageTurnOpenGlPrototypeEnabled = true

internal fun readerPageTurnCanStartPassivePrewarm(
	destroyed: Boolean,
	sessionEnabled: Boolean,
	visualCommitPending: Boolean,
	idle: Boolean
): Boolean = !destroyed && sessionEnabled && !visualCommitPending && idle

internal class ReaderPageTurnController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val bundleSource: ReaderPageTurnBundleSource = ReaderPageTurnBundleSource(),
	private val onRequestPrewarm: () -> Unit = {},
	private val onPreparationStateChange: (ReaderPagePreparationState) -> Unit = {},
	private val onCommitTurn: (ReaderPageTurnPhysicalDirection) -> Unit
) {
	private val state = ReaderPageTurnStateMachine()
	private var activeTransition: ReaderPageSlideTransition? = null
	private var activePlan: ReaderPageTurnTransitionPlan? = null
	private var activeStateGeneration = 0L
	private var activeLeafAxisWidth: Int? = null
	private var slideView: ReaderPageTurnSlideView? = null
	private var curlGlView: ReaderPageCurlGlView? = null
	private var overlayAttached = false
	private var animation: ValueAnimator? = null
	private var enabledForSession = true
	private var edgeOriginY = 0f
	private var pointerY = 0f
	private var gestureHostHeight = 0
	private var settleGeneration = 0L
	private var activeSettleToken: String? = null
	private var releasedWhilePreparing = false
	private var preparationUnavailable = false
	private var prewarmSession = 0L
	private var prewarmInProgress = false
	private val rasterBatchController = ReaderPageRasterBatchController(bundleSource)
	private var rasterPreparationCompleted = 0
	private var rasterPreparationRequired = 0
	private var rasterInteractiveCompleted = 0
	private var rasterInteractiveRequired = 0
	private var activePreparationPageNumber: Int? = null
	private var lastPrewarmBoundary: String? = null
	private var lastPreparationStateTrace: String? = null
	private var hasPreparedBefore = false
	private var slideCoordinator: ReaderPageSlideCoordinator? = null
	private var visualCommitPending = false
	private var preparationShield: ImageView? = null
	private var preparationShieldSnapshot: ReaderPageSlideSnapshot? = null
	private var destroyed = false
	private val applicationContext = host.context.applicationContext
	private val memoryCallbacks = object : ComponentCallbacks2 {
		override fun onConfigurationChanged(newConfig: Configuration) = Unit

		override fun onLowMemory() {
			host.post { invalidate("memory-pressure") }
		}

		override fun onTrimMemory(level: Int) {
			if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
				host.post { invalidate("memory-pressure") }
			}
		}
	}

	init {
		applicationContext.registerComponentCallbacks(memoryCallbacks)
	}

	val isAvailable: Boolean
		get() = enabledForSession && bundleSource.isAvailable

	private fun publishPreparationState(
		phase: ReaderPagePreparationPhase,
		error: String? = null,
		retryable: Boolean = false
	) {
		val state = readerPagePreparationState(
			phase = phase,
			requiredCount = rasterPreparationRequired,
			completedCount = rasterPreparationCompleted,
			interactiveRequiredCount = rasterInteractiveRequired,
			interactiveCompletedCount = rasterInteractiveCompleted,
			hasPreparedBefore = hasPreparedBefore,
			activePageNumber = activePreparationPageNumber,
			error = error,
			retryable = retryable
		)
		val trace = buildString {
			append("phase=${state.phase}")
			append(" completed=${state.completedCount}/${state.requiredCount}")
			append(" interactive=${state.interactiveCompletedCount}/${state.interactiveRequiredCount}")
			append(" interactiveReady=${state.interactiveReady}")
			append(" hasPreparedBefore=$hasPreparedBefore")
			append(" presentation=${state.presentation}")
			append(" gestures=${state.gestureDisposition}")
			state.activePageLabel?.let { append(" active=$it") }
			state.error?.takeIf { it.isNotBlank() }?.let { append(" error=$it") }
		}
		if (lastPreparationStateTrace != trace) {
			lastPreparationStateTrace = trace
			Logger.i(ReaderPageTurnControllerTag, "Page preparation state $trace")
		}
		onPreparationStateChange(state)
	}

	fun updateBitmapQuality(value: String?) {
		if (!bundleSource.updateBitmapQuality(normalizeReaderPageBitmapQuality(value))) return
		cancelPrewarm()
		cancelPreparation()
		removePreparationShield()
		state.cancel()
		animation?.cancel()
		animation = null
		detachOverlay(schedulePrewarm = false)
		slideCoordinator = null
		onRequestPrewarm()
	}

	fun retryPreparation() {
		if (destroyed || prewarmInProgress) return
		onRequestPrewarm()
	}

	fun update(deltaX: Float, viewWidth: Int, edgeOriginY: Float, pointerY: Float, viewHeight: Int) {
		if (!isAvailable || viewWidth <= 0) return
		setGestureY(edgeOriginY, pointerY, viewHeight)
		if (state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle) begin(deltaX)
		handleEffects(state.update(deltaX, pageAxisWidth(viewWidth), SystemClock.uptimeMillis()))
	}

	fun release(deltaX: Float, viewWidth: Int, edgeOriginY: Float, pointerY: Float, viewHeight: Int) {
		if (!isAvailable || viewWidth <= 0) return
		setGestureY(edgeOriginY, pointerY, viewHeight)
		releasedWhilePreparing = state.phase == paige.navic.reader.ReaderPageTurnPhase.Preparing
		handleEffects(state.release(deltaX, pageAxisWidth(viewWidth), SystemClock.uptimeMillis()))
		if (releasedWhilePreparing && preparationUnavailable) resolveColdRelease()
	}

	private fun pageAxisWidth(viewWidth: Int): Int = activeTransition
		?.let(::transitionLeafAxisWidth)
		?: activeLeafAxisWidth
		?: viewWidth.coerceAtLeast(1)

	private fun transitionLeafAxisWidth(transition: ReaderPageSlideTransition): Int? = transition
		.activeLeafRect(state.direction)
		?.let { leaf -> (leaf.width * transition.renderScaleX).roundToInt().coerceAtLeast(1) }

	private fun setGestureY(edgeOriginY: Float, pointerY: Float, viewHeight: Int) {
		gestureHostHeight = viewHeight.coerceAtLeast(1)
		this.edgeOriginY = edgeOriginY.coerceIn(0f, gestureHostHeight.toFloat())
		this.pointerY = pointerY.coerceIn(0f, gestureHostHeight.toFloat())
		applyGestureYToOverlay()
	}

	fun cancel() {
		visualCommitPending = false
		activeSettleToken = null
		cancelPrewarm()
		cancelPreparation()
		removePreparationShield()
		releasedWhilePreparing = false
		val effects = state.cancel()
		animation?.cancel()
		animation = null
		handleEffects(effects)
	}

	fun destroy() {
		if (destroyed) return
		destroyed = true
		visualCommitPending = false
		activeSettleToken = null
		cancelPrewarm()
		destroyPageTurnPreviewRenderer("controller-destroyed")
		cancelPreparation()
		state.cancel()
		animation?.cancel()
		animation = null
		detachOverlay()
		bundleSource.close()
		slideCoordinator = null
		applicationContext.unregisterComponentCallbacks(memoryCallbacks)
	}

	fun invalidate(reason: String) {
		if (destroyed) return
		visualCommitPending = false
		activeSettleToken = null
		cancelPrewarm()
		destroyPageTurnPreviewRenderer(reason)
		cancelPreparation()
		removePreparationShield()
		state.cancel()
		animation?.cancel()
		animation = null
		detachOverlay(schedulePrewarm = false)
		bundleSource.invalidate(reason)
		slideCoordinator = null
	}

	fun invalidateCurrentVisualSnapshot(reason: String) {
		if (destroyed) return
		cancelPrewarm()
		val pageIndex = slideCoordinator?.visualPageIndex
		if (pageIndex == null) {
			bundleSource.invalidate(reason)
		} else {
			bundleSource.invalidatePage(pageIndex, reason)
		}
	}

	fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {
		if (destroyed || pageIndex == null || pageIndex < 0) return
		val coordinator = slideCoordinator
		if (reason == "page-turn:exact") {
			if (
				coordinator != null &&
				coordinator.activeSettlementTarget == pageIndex
			) {
				activeSettleToken = null
				handleCoordinatorEffects(
					coordinator.settlementReported(
						reportedGeneration = coordinator.generation,
						pageIndex = pageIndex,
						renderable = true
					)
				)
				onRequestPrewarm()
				return
			}
			val exactCoordinator = coordinator ?: ReaderPageSlideCoordinator(pageIndex).also {
				slideCoordinator = it
			}
			if (
				exactCoordinator.visualPageIndex != pageIndex ||
				exactCoordinator.settledPageIndex != pageIndex
			) {
				cancelPrewarm()
				exactCoordinator.invalidate(pageIndex)
				Logger.i(
					ReaderPageTurnControllerTag,
					"Page-turn external exact page synchronized page=$pageIndex"
				)
			}
			onRequestPrewarm()
			return
		}
		if (slideCoordinator?.visualPageIndex == pageIndex) return
		visualCommitPending = false
		activeSettleToken = null
		cancelPrewarm()
		cancelPreparation()
		removePreparationShield()
		state.cancel()
		animation?.cancel()
		animation = null
		detachOverlay(schedulePrewarm = false)
		bundleSource.invalidate("external-page-relocation")
		slideCoordinator = ReaderPageSlideCoordinator(pageIndex)
		Logger.i(ReaderPageTurnControllerTag, "Page-turn visual index synchronized page=$pageIndex")
	}

	private fun begin(deltaX: Float) {
		activeLeafAxisWidth = null
		releasedWhilePreparing = false
		preparationUnavailable = false
		val direction = if (deltaX < 0f) {
			ReaderPageTurnPhysicalDirection.TowardLeft
		} else {
			ReaderPageTurnPhysicalDirection.TowardRight
		}
		val webView = webViewProvider() ?: run {
			enabledForSession = false
			return
		}
		activeStateGeneration = state.begin(direction, spread = webView.width >= webView.height * 1.12f)
		val bundleGeneration = bundleSource.currentGeneration()
		val token = "navic-page-turn-preview-${++settleGeneration}"
		val physicalDirection = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) "toward-left" else "toward-right"
		val visualPageIndex = slideCoordinator?.visualPageIndex?.toString() ?: "null"
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnTransitionPlan?.('$physicalDirection', $visualPageIndex) ?? null)"
		) { encodedPlan ->
			val plan = ReaderPageTurnTransitionPlan.parse(encodedPlan, token, bundleGeneration)
			if (plan == null || !plan.matchesLayout(state.spread)) {
				markPreparationUnavailable(activeStateGeneration, "transition-plan-unavailable")
				return@evaluateJavascript
			}
			val coordinator = slideCoordinator ?: ReaderPageSlideCoordinator(plan.sourcePageIndex).also {
				slideCoordinator = it
			}
			if (coordinator.visualPageIndex != plan.sourcePageIndex) {
				markPreparationUnavailable(activeStateGeneration, "visual-source-mismatch")
				return@evaluateJavascript
			}
			activePlan = plan
			bundleSource.cached(plan)?.let { cached ->
				activatePreparedTransition(plan, activeStateGeneration, cached)
				return@evaluateJavascript
			}
			if (prewarmInProgress) cancelPrewarm()
			prepareBundle(webView, plan, activeStateGeneration)
		}
	}

	private fun activatePreparedTransition(
		plan: ReaderPageTurnTransitionPlan,
		stateGeneration: Long,
		transition: ReaderPageSlideTransition
	) {
		if (!isPreparationActive(plan, stateGeneration)) {
			transition.close()
			return
		}
		activeTransition?.close()
		activeTransition = transition
		activeLeafAxisWidth = transitionLeafAxisWidth(transition)
		activeLeafAxisWidth?.let { handleEffects(state.rebaseAxisSize(it)) }
		handleEffects(state.captureSucceeded(stateGeneration))
	}

	fun prewarmAdjacent(): Boolean {
		if (prewarmInProgress) return true
		if (!readerPageTurnCanStartPassivePrewarm(
			destroyed = destroyed,
			sessionEnabled = enabledForSession,
			visualCommitPending = visualCommitPending,
			idle = state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle
		)) return false
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: return false
		prewarmInProgress = true
		rasterPreparationCompleted = 0
		rasterPreparationRequired = 0
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = 0
		activePreparationPageNumber = slideCoordinator?.visualPageIndex?.plus(1)
		publishPreparationState(ReaderPagePreparationPhase.Preparing)
		val session = ++prewarmSession
		logPrewarmBoundary(
			event = "started",
			detail = "session=$session visual=${slideCoordinator?.visualPageIndex} " +
				"webView=${webView.width}x${webView.height}"
		)
		queryRasterPreparationPlan(webView, session)
		return true
	}

	private fun queryRasterPreparationPlan(webView: WebView, session: Long) {
		if (!isPrewarmActive(webView, session)) return
		val visualPageIndex = slideCoordinator?.visualPageIndex
		val centerExpression = visualPageIndex?.toString() ?: "null"
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(" +
				"$centerExpression) ?? null)"
		) { encoded ->
			if (!isPrewarmActive(webView, session)) return@evaluateJavascript
			val plan = readerPageRasterPreparationPlan(encoded)
			if (plan == null) {
				logPrewarmBoundary("plan-unavailable", "session=$session encoded=$encoded")
				webView.postOnAnimation { queryRasterPreparationPlan(webView, session) }
				return@evaluateJavascript
			}
			val expectedLayout = expectedLayoutMode(webView)
			if (plan.layoutMode != expectedLayout) {
				logPrewarmBoundary(
					event = "layout-mismatch",
					detail = "session=$session actual=${plan.layoutMode} expected=$expectedLayout " +
						"center=${plan.centerPageIndex}"
				)
				webView.postOnAnimation { queryRasterPreparationPlan(webView, session) }
				return@evaluateJavascript
			}
			val coordinator = slideCoordinator ?: ReaderPageSlideCoordinator(plan.centerPageIndex).also {
				slideCoordinator = it
			}
			if (coordinator.visualPageIndex != plan.centerPageIndex) {
				logPrewarmBoundary(
					event = "center-mismatch",
					detail = "session=$session coordinator=${coordinator.visualPageIndex} " +
						"plan=${plan.centerPageIndex}"
				)
				webView.postOnAnimation { queryRasterPreparationPlan(webView, session) }
				return@evaluateJavascript
			}
			logPrewarmBoundary(
				event = "plan-accepted",
				detail = "session=$session layout=${plan.layoutMode} center=${plan.centerPageIndex} " +
					"targets=${plan.targets.size}"
			)
			val kind = if (plan.layoutMode == "spread") {
				ReaderPageTurnTransitionKind.LandscapeSpreadSlide
			} else {
				ReaderPageTurnTransitionKind.PortraitSlide
			}
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
								reason = "current-surface-unavailable"
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
			hasPreparedBefore = rasterInteractiveRequired > 0
			publishPreparationState(ReaderPagePreparationPhase.Preparing)
			bundleSource.preparationMode(webView, plan.currentChapterPageCount) { mode ->
				if (!isPrewarmActive(webView, session)) return@preparationMode
				startRasterFollowUp(webView, session, plan, kind, calibrationTargets, mode)
			}
		}
	}

	private fun startRasterFollowUp(
		webView: WebView,
		session: Long,
		plan: ReaderPageRasterPreparationPlan,
		kind: ReaderPageTurnTransitionKind,
		calibrationTargets: List<ReaderPageRasterBatchTarget>,
		mode: ReaderPageRasterPreparationMode
	) {
		val calibratedPages = calibrationTargets.mapTo(mutableSetOf()) { target -> target.pageIndex }
		val followUpTargets = readerPageRasterFollowUpTargets(
			targets = plan.targets,
			centerPageIndex = plan.centerPageIndex,
			step = plan.step,
			mode = mode
		).filterNot { target -> target.pageIndex in calibratedPages }
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
		val totalRequired = completedOffset + followUpTargets.size
		rasterPreparationRequired = totalRequired
		publishPreparationState(ReaderPagePreparationPhase.Preparing)
		startRasterBatch(
			webView = webView,
			session = session,
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
			onStagingStarted = ::attachPreparationShield,
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
					if (rasterInteractiveCompleted >= rasterInteractiveRequired && rasterInteractiveRequired > 0) {
						hasPreparedBefore = true
					}
					publishPreparationState(ReaderPagePreparationPhase.Preparing)
				}
			},
			onComplete = { outcome ->
				if (!isPrewarmActive(webView, session) || generation != bundleSource.currentGeneration()) return@start
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
		prewarmInProgress = false
		logPrewarmBoundary(
			event = "batch-complete",
			detail = "outcome=$outcome completed=$rasterPreparationCompleted/$rasterPreparationRequired"
		)
		when (outcome) {
			ReaderPageRasterBatchOutcome.Ready -> {
				rasterPreparationCompleted = rasterPreparationRequired
				rasterInteractiveCompleted = rasterInteractiveRequired
				hasPreparedBefore = rasterInteractiveRequired > 0 || hasPreparedBefore
				publishPreparationState(ReaderPagePreparationPhase.Ready)
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
				publishPreparationState(ReaderPagePreparationPhase.Preparing)
			}
			is ReaderPageRasterBatchOutcome.Failed -> {
				Logger.e(
					ReaderPageTurnControllerTag,
					"Page-turn raster preparation failed ${outcome.diagnostic}"
				)
				publishPreparationState(
					phase = ReaderPagePreparationPhase.Failed,
					error = outcome.userMessage,
					retryable = true
				)
			}
		}
		rasterPreparationCompleted = 0
		rasterPreparationRequired = 0
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = 0
		activePreparationPageNumber = null
		removePreparationShield()
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
		Logger.i(ReaderPageTurnControllerTag, "Page-turn passive prewarm $trace")
	}

	private fun cancelPrewarm() {
		prewarmSession += 1
		val wasInProgress = prewarmInProgress
		prewarmInProgress = false
		rasterPreparationCompleted = 0
		rasterPreparationRequired = 0
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = 0
		activePreparationPageNumber = null
		if (wasInProgress) rasterBatchController.cancel()
		removePreparationShield()
		publishPreparationState(
			if (hasPreparedBefore) ReaderPagePreparationPhase.Ready else ReaderPagePreparationPhase.Idle
		)
	}

	private fun destroyPageTurnPreviewRenderer(reason: String) {
		webViewProvider()?.takeIf { it.isAttachedToWindow }?.evaluateJavascript(
			"window.NavicReaderBridge?.destroyPageTurnPreviewRenderer?.(${JSONObject.quote(reason)})"
		) { }
	}

	private fun prepareBundle(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		stateGeneration: Long
	) {
		if (bundleSource.hasCachedSnapshot(plan.sourcePageIndex, plan.kind)) {
			hydrateGestureDestination(webView, plan, stateGeneration)
			return
		}
		bundleSource.captureCurrentSurface(webView, plan.generation) { current ->
			if (current == null || !isPreparationActive(plan, stateGeneration)) {
				current?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				markPreparationUnavailable(stateGeneration, "current-surface-unavailable")
				return@captureCurrentSurface
			}
			val source = bundleSource.cacheCurrentSnapshot(plan, current)
			if (source == null) {
				markPreparationUnavailable(stateGeneration, "current-snapshot-unavailable")
				return@captureCurrentSurface
			}
			hydrateGestureDestination(webView, plan, stateGeneration)
		}
	}

	private fun hydrateGestureDestination(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		stateGeneration: Long
	) {
		bundleSource.hydratePreparedBundle(webView, plan) { transition ->
			if (!isPreparationActive(plan, stateGeneration)) {
				transition?.close()
				return@hydratePreparedBundle
			}
			if (transition != null) {
				activatePreparedTransition(plan, stateGeneration, transition)
				return@hydratePreparedBundle
			}
			val quotedToken = JSONObject.quote(plan.token)
			webView.evaluateJavascript(
				"window.NavicReaderBridge?.beginPageTurnPreviewPreparation?.($quotedToken, ${plan.targetPageIndex})"
			) {
				if (!isPreparationActive(plan, stateGeneration)) return@evaluateJavascript
				waitForPreviewReady(webView, plan, stateGeneration)
			}
		}
	}

	private fun waitForPreviewReady(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		stateGeneration: Long
	) {
		if (!isPreparationActive(plan, stateGeneration) || !webView.isAttachedToWindow) {
			return
		}
		val quotedToken = JSONObject.quote(plan.token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.pageTurnPreviewState?.($quotedToken)?.status ?? 'missing'"
		) { encodedStatus ->
			if (!isPreparationActive(plan, stateGeneration)) {
				return@evaluateJavascript
			}
			when (encodedStatus.javascriptString()) {
				"ready" -> bundleSource.capturePreparedBundle(
					webView = webView,
					plan = plan,
					shieldPageIndex = plan.sourcePageIndex,
					onStagingStarted = ::attachPreparationShield
				) { transition ->
					if (transition == null) {
						markPreparationUnavailable(stateGeneration, "destination-bundle-unavailable")
						return@capturePreparedBundle
					}
					if (!isPreparationActive(plan, stateGeneration)) {
						transition.close()
						removePreparationShield()
						return@capturePreparedBundle
					}
					removePreparationShield()
					preparationUnavailable = false
					activeTransition?.close()
					activeTransition = transition
					activeLeafAxisWidth = transitionLeafAxisWidth(transition)
					activeLeafAxisWidth?.let { handleEffects(state.rebaseAxisSize(it)) }
					handleEffects(state.captureSucceeded(stateGeneration))
				}
				"failed", "missing" -> {
					markPreparationUnavailable(
						stateGeneration,
						"destination-preview-${encodedStatus.javascriptString()}"
					)
				}
				else -> webView.postOnAnimation {
					waitForPreviewReady(webView, plan, stateGeneration)
				}
			}
		}
	}

	private fun isPreparationActive(plan: ReaderPageTurnTransitionPlan, stateGeneration: Long): Boolean =
		activePlan === plan && activeStateGeneration == stateGeneration

	private fun markPreparationUnavailable(stateGeneration: Long, reason: String) {
		if (
			activeStateGeneration != stateGeneration ||
			state.phase != paige.navic.reader.ReaderPageTurnPhase.Preparing
		) return
		preparationUnavailable = true
		activePlan?.let { plan ->
			webViewProvider()?.evaluateJavascript(
				"window.NavicReaderBridge?.restorePageTurnLiveComposition?.(${JSONObject.quote(plan.token)})"
			) { }
		}
		Logger.w(ReaderPageTurnControllerTag, "Page-turn preparation unavailable reason=$reason")
		if (releasedWhilePreparing) resolveColdRelease()
	}

	private fun attachPreparationShield(snapshot: ReaderPageSlideSnapshot) {
		if (overlayAttached) return
		removePreparationShield()
		snapshot.retain()
		preparationShieldSnapshot = snapshot
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val rect = snapshot.surfaceRectInWindow
		val shield = ImageView(host.context).apply {
			setImageBitmap(snapshot.bitmap)
			scaleType = ImageView.ScaleType.FIT_XY
			isClickable = false
			isFocusable = false
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
			layoutParams = FrameLayout.LayoutParams(rect.width(), rect.height()).apply {
				leftMargin = rect.left - hostLocation[0]
				topMargin = rect.top - hostLocation[1]
			}
		}
		host.addView(shield)
		preparationShield = shield
	}

	private fun removePreparationShield() {
		val shield = preparationShield
		preparationShield = null
		if (shield != null) {
			shield.setImageDrawable(null)
			(shield.parent as? ViewGroup)?.removeView(shield)
		}
		preparationShieldSnapshot?.release()
		preparationShieldSnapshot = null
	}

	private fun cancelColdPreparation() {
		val generation = activeStateGeneration
		if (generation != 0L) state.captureFailed(generation)
		cancelPreparation()
		removePreparationShield()
		releasedWhilePreparing = false
		preparationUnavailable = false
		onRequestPrewarm()
	}

	private fun resolveColdRelease() {
		when (state.pendingReleaseCommit) {
			true -> {
				val plan = activePlan
				if (plan != null && preparationShieldSnapshot != null) commitShieldedColdFallback(plan)
				else cancelColdPreparation()
			}
			false -> cancelColdPreparation()
			null -> Unit
		}
	}

	private fun commitShieldedColdFallback(plan: ReaderPageTurnTransitionPlan) {
		if (activePlan !== plan) return
		val generation = activeStateGeneration
		if (generation != 0L) state.captureFailed(generation)
		val webView = webViewProvider()
		activePlan = null
		activeTransition?.close()
		activeTransition = null
		activeStateGeneration = 0L
		releasedWhilePreparing = false
		preparationUnavailable = false
		if (webView == null || !webView.isAttachedToWindow) {
			removePreparationShield()
			return
		}
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.restorePageTurnLiveComposition?.(${JSONObject.quote(plan.token)})"
		) {
			val coordinator = slideCoordinator ?: ReaderPageSlideCoordinator(plan.sourcePageIndex).also {
				slideCoordinator = it
			}
			handleCoordinatorEffects(coordinator.visualCommitted(plan.targetPageIndex))
		}
		Logger.i(
			ReaderPageTurnControllerTag,
			"Page-turn shielded cold fallback target=${plan.targetPageIndex} kind=${plan.kind}"
		)
	}

	private fun cancelPreparation() {
		activePlan?.let { plan ->
			webViewProvider()?.evaluateJavascript(
				"window.NavicReaderBridge?.restorePageTurnLiveComposition?.(${JSONObject.quote(plan.token)})"
			) { }
		}
		activePlan = null
		activeStateGeneration = 0L
		releasedWhilePreparing = false
		preparationUnavailable = false
	}

	private fun handleEffects(effects: List<ReaderPageTurnEffect>) {
		for (effect in effects) {
			when (effect) {
				ReaderPageTurnEffect.AttachOverlay -> attachOverlay()
				is ReaderPageTurnEffect.Render -> setOverlayProgress(effect.progress)
				is ReaderPageTurnEffect.AnimateCommit -> animateCommit(effect.fromProgress)
				is ReaderPageTurnEffect.AnimateRelax -> animateRelax(effect.fromProgress)
				is ReaderPageTurnEffect.Commit -> {
					visualCommitPending = true
					host.postOnAnimation { commitTurn(effect.direction) }
				}
				ReaderPageTurnEffect.ShowFinalBase -> showOverlayFinalBase()
				ReaderPageTurnEffect.DetachOverlay -> {
					if (slideCoordinator?.activeSettlementTarget == null) detachAfterNavigationFrame()
				}
			}
		}
	}

	private fun commitTurn(direction: ReaderPageTurnPhysicalDirection) {
		val webView = webViewProvider()
		if (webView == null || !webView.isAttachedToWindow) {
			visualCommitPending = false
			onCommitTurn(direction)
			detachAfterNavigationFrame()
			return
		}
		val plan = activePlan
		if (plan != null) {
			val coordinator = slideCoordinator ?: ReaderPageSlideCoordinator(plan.sourcePageIndex).also {
				slideCoordinator = it
			}
			handleCoordinatorEffects(coordinator.visualCommitted(plan.targetPageIndex))
			visualCommitPending = false
			onRequestPrewarm()
			return
		}
		val token = "navic-page-turn-${++settleGeneration}"
		visualCommitPending = false
		activeSettleToken = token
		val quotedToken = JSONObject.quote(token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.armNativePageTurnSettle?.($quotedToken)"
		) {
			if (activeSettleToken != token) return@evaluateJavascript
			onCommitTurn(direction)
			pollNativePageTurnSettle(webView, token)
		}
	}

	private fun handleCoordinatorEffects(effects: List<ReaderPageSlideCoordinatorEffect>) {
		for (effect in effects) {
			when (effect) {
				is ReaderPageSlideCoordinatorEffect.SettleExact -> {
					if (slideCoordinator == null) continue
					dispatchExactSettlement(effect.pageIndex)
				}
				ReaderPageSlideCoordinatorEffect.RemoveFinalShield -> {
					if (state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle) detachAfterNavigationFrame()
				}
			}
		}
	}

	private fun dispatchExactSettlement(pageIndex: Int) {
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: return
		val token = "navic-page-slide-settle-${++settleGeneration}"
		activeSettleToken = token
		val quotedToken = JSONObject.quote(token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.dispatch?.({ type: 'goToVisualPage', pageIndex: $pageIndex, settleToken: $quotedToken })"
		) { }
	}

	private fun pollNativePageTurnSettle(webView: WebView, token: String) {
		if (activeSettleToken != token || !webView.isAttachedToWindow) return
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.nativePageTurnSettledToken?.() ?? null"
		) { encoded ->
			if (activeSettleToken != token) return@evaluateJavascript
			val settledToken = runCatching { JSONTokener(encoded.orEmpty()).nextValue() as? String }.getOrNull()
			if (settledToken == token) {
				readNativePageTurnSettledState(webView, token)
			} else {
				webView.postOnAnimation { pollNativePageTurnSettle(webView, token) }
			}
		}
	}

	private fun readNativePageTurnSettledState(webView: WebView, token: String) {
		if (activeSettleToken != token || !webView.isAttachedToWindow) return
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.nativePageTurnSettledState?.() ?? null)"
		) { encoded ->
			if (activeSettleToken != token) return@evaluateJavascript
			val settled = runCatching {
				val jsonText = JSONTokener(encoded.orEmpty()).nextValue() as? String
				jsonText?.let(::JSONObject)
			}.getOrNull()
			if (settled?.optString("token") != token) {
				webView.postOnAnimation { pollNativePageTurnSettle(webView, token) }
				return@evaluateJavascript
			}
			activeSettleToken = null
			if (settled.optBoolean("cancelled", false)) {
				slideCoordinator = null
				detachOverlay()
			} else {
				detachAfterNavigationFrame()
			}
		}
	}

	private fun attachOverlay() {
		val transition = activeTransition ?: return
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val left = transition.surfaceRectInWindow.left - hostLocation[0]
		val top = transition.surfaceRectInWindow.top - hostLocation[1]
		if (ReaderPageTurnOpenGlPrototypeEnabled) {
			val view = curlGlView ?: ReaderPageCurlGlView(host.context).also { created ->
				created.layoutParams = FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT
				)
				host.addView(created)
				curlGlView = created
			}
			view.setTransition(
				transition = transition,
				direction = state.direction,
				surfaceLeft = left,
				surfaceTop = top,
				diagnosticsEnabled = ReaderWebRuntime.isWebContentsDebuggingEnabled()
			)
		} else {
			val view = slideView ?: ReaderPageTurnSlideView(host.context).also { created ->
				created.layoutParams = FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT
				)
				host.addView(created)
				slideView = created
			}
			view.setTransition(transition, state.direction, left, top)
		}
		overlayAttached = true
		Logger.i(
			ReaderPageTurnControllerTag,
			"Page-turn overlay attached kind=${transition.plan.kind} target=${transition.plan.targetPageIndex} " +
				"left=$left top=$top size=${transition.source.bitmap.width}x${transition.source.bitmap.height}"
		)
	}

	private fun applyGestureYToOverlay() {
		// The bounded wave is deterministic for now; preserve Y for diagnostics and later touch anchoring.
	}

	private fun setOverlayProgress(progress: Float) {
		if (ReaderPageTurnOpenGlPrototypeEnabled) curlGlView?.setProgress(progress)
		else slideView?.setProgress(progress)
	}

	private fun showOverlayFinalBase() {
		if (ReaderPageTurnOpenGlPrototypeEnabled) curlGlView?.showFinalBase()
		else slideView?.showFinalBase()
	}

	private fun animateCommit(fromProgress: Float) {
		animate(
			from = fromProgress,
			to = CommitEndProgress,
			durationMs = readerPageTurnRemainingAnimationDuration(
				from = fromProgress,
				to = CommitEndProgress,
				fullDurationMs = CommitAnimationDurationMs
			)
		) {
			handleEffects(state.animationFinished())
		}
	}

	private fun animateRelax(fromProgress: Float) {
		animate(fromProgress, 0f, RelaxAnimationDurationMs) {
			handleEffects(state.animationFinished())
		}
	}

	private fun animate(from: Float, to: Float, durationMs: Long, onFinished: () -> Unit) {
		animation?.cancel()
		animation = ValueAnimator.ofFloat(from.coerceIn(0f, CommitEndProgress), to).apply {
			duration = durationMs
			addUpdateListener { animator -> setOverlayProgress(animator.animatedValue as Float) }
			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					this@ReaderPageTurnController.animation = null
					onFinished()
				}
			})
			start()
		}
	}

	private fun detachAfterNavigationFrame() {
		if (state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle) {
			host.postOnAnimation { host.postOnAnimation { detachOverlay() } }
		} else {
			detachOverlay()
		}
	}

	private fun detachOverlay(schedulePrewarm: Boolean = true) {
		activeSettleToken = null
		removePreparationShield()
		val view = slideView
		slideView = null
		if (view != null) {
			(view.parent as? ViewGroup)?.removeView(view)
			view.clearTransition()
		}
		curlGlView?.clearTransition()
		overlayAttached = false
		if (destroyed) {
			curlGlView?.let { glView ->
				(glView.parent as? ViewGroup)?.removeView(glView)
				glView.onPause()
			}
			curlGlView = null
		}
		activeTransition?.close()
		activeTransition = null
		activePlan = null
		activeStateGeneration = 0L
		activeLeafAxisWidth = null
		releasedWhilePreparing = false
		preparationUnavailable = false
		if (schedulePrewarm && !destroyed) {
			onRequestPrewarm()
		}
	}

	private companion object {
		const val CommitAnimationDurationMs = 350L
		const val CommitEndProgress = 1f
		const val RelaxAnimationDurationMs = 160L
	}
}

private fun ReaderPageTurnTransitionPlan.sameTransitionAs(other: ReaderPageTurnTransitionPlan): Boolean =
	cacheKey == other.cacheKey

internal fun readerPageTurnRemainingAnimationDuration(
	from: Float,
	to: Float,
	fullDurationMs: Long
): Long {
	if (fullDurationMs <= 0L) return 0L
	val fullDistance = abs(to)
	if (fullDistance <= 0.001f) return 0L
	val remainingFraction = (abs(to - from) / fullDistance).coerceIn(0f, 1f)
	return (fullDurationMs * remainingFraction).roundToLong()
}

private fun String?.javascriptString(): String? = runCatching {
	JSONTokener(orEmpty()).nextValue() as? String
}.getOrNull()

private fun String?.javascriptObject(): JSONObject? = runCatching {
	when (val decoded = JSONTokener(orEmpty()).nextValue()) {
		is JSONObject -> decoded
		is String -> JSONObject(decoded)
		else -> null
	}
}.getOrNull()
