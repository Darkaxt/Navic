package paige.navic.ui.screens.reader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
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
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.readerPagePreparationState
import paige.navic.util.core.Logger

private const val ReaderPageRasterPreparationControllerTag = "ReaderPageRasterPreparation"

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
	private val onPreparationStateChange: (ReaderPagePreparationState) -> Unit = {}
) {
	private val rasterBatchController = ReaderPageRasterBatchController(bundleSource)
	private var currentVisualPageIndex: Int? = null
	private var prewarmSession = 0L
	private var prewarmInProgress = false
	private var rasterPreparationCompleted = 0
	private var rasterPreparationRequired = 0
	private var rasterInteractiveCompleted = 0
	private var rasterInteractiveRequired = 0
	private var activePreparationPageNumber: Int? = null
	private var lastPrewarmBoundary: String? = null
	private var lastPreparationStateTrace: String? = null
	private var hasPreparedBefore = false
	private var preparationShield: ImageView? = null
	private var preparationShieldSnapshot: ReaderPageSlideSnapshot? = null
	private var preparationShieldSession: Long? = null
	private var preparationShieldBatchLabel: String? = null
	private var destroyed = false
	private val applicationContext = host.context.applicationContext
	private val memoryCallbacks = object : ComponentCallbacks2 {
		override fun onConfigurationChanged(newConfig: Configuration) = Unit

		override fun onLowMemory() {
			host.post { bundleSource.trimMemory("on-low-memory") }
		}

		override fun onTrimMemory(level: Int) {
			if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
				host.post { bundleSource.trimMemory("on-trim-memory:$level") }
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
		cancelPrewarm(reason = "bitmap-quality-changed")
		onRequestPrewarm()
	}

	fun retryPreparation() {
		if (destroyed || prewarmInProgress) return
		onRequestPrewarm()
	}

	fun destroy() {
		if (destroyed) return
		destroyed = true
		cancelPrewarm(reason = "destroy")
		bundleSource.close()
		currentVisualPageIndex = null
		applicationContext.unregisterComponentCallbacks(memoryCallbacks)
	}

	fun invalidate(reason: String, clearVisualPageIndex: Boolean = false) {
		if (destroyed) return
		cancelPrewarm(reason = "invalidate:$reason")
		bundleSource.invalidate(reason)
		if (clearVisualPageIndex) currentVisualPageIndex = null
		logLoadingEvent(
			event = "invalidated",
			detail = "reason=$reason clearVisualPageIndex=$clearVisualPageIndex " +
				"visual=$currentVisualPageIndex generation=${bundleSource.currentGeneration()}"
		)
	}

	fun invalidateCurrentVisualSnapshot(reason: String) {
		if (destroyed) return
		cancelPrewarm(reason = "invalidate-current:$reason")
		val pageIndex = currentVisualPageIndex
		if (pageIndex == null) {
			bundleSource.invalidate(reason)
		} else {
			bundleSource.invalidatePage(pageIndex, reason)
		}
	}

	fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {
		if (destroyed || pageIndex == null || pageIndex < 0) return
		if (currentVisualPageIndex == pageIndex) {
			if (reason == "page-turn:exact") onRequestPrewarm()
			return
		}
		cancelPrewarm(reason = "visual-index-changed:${reason ?: "unspecified"}")
		currentVisualPageIndex = pageIndex
		logLoadingEvent(
			event = "visual-index-synchronized",
			detail = "page=$pageIndex reason=$reason generation=${bundleSource.currentGeneration()} retained=true"
		)
		onRequestPrewarm()
	}

	fun prewarmAdjacent(): Boolean {
		if (prewarmInProgress) return true
		if (!readerPageTurnCanStartPassivePrewarm(
			destroyed = destroyed,
			sessionEnabled = true,
			visualCommitPending = false,
			idle = true
		)) return false
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: return false
		prewarmInProgress = true
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
			val visualPageIndex = currentVisualPageIndex
			if (visualPageIndex == null) {
				currentVisualPageIndex = plan.centerPageIndex
			} else if (visualPageIndex != plan.centerPageIndex) {
				logPrewarmBoundary(
					event = "center-mismatch",
					detail = "session=$session current=$visualPageIndex plan=${plan.centerPageIndex}"
				)
				webView.postOnAnimation { queryRasterPreparationPlan(webView, session) }
				return@evaluateJavascript
			}
			logPrewarmBoundary(
				event = "plan-accepted",
				detail = "session=$session layout=${plan.layoutMode} center=${plan.centerPageIndex} " +
					"targets=${plan.targets.size}"
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
		rasterPreparationCompleted = 0
		rasterPreparationRequired = 0
		rasterInteractiveCompleted = 0
		rasterInteractiveRequired = 0
		activePreparationPageNumber = null
		removePreparationShield(reason = "session-finished:$outcome")
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
