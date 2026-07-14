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
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderPageSlideCoordinator
import paige.navic.reader.ReaderPageSlideCoordinatorEffect
import paige.navic.reader.ReaderPageTurnStateMachine
import paige.navic.reader.ReaderWebRuntime
import paige.navic.util.core.Logger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val ReaderPageTurnControllerTag = "ReaderPageTurnController"
internal const val ReaderPageTurnOpenGlPrototypeEnabled = true

internal class ReaderPageTurnPrewarmRetryBudget(
	private val maxRetriesPerKey: Int = 1
) {
	private val retriesByKey = mutableMapOf<String, Int>()

	fun consume(key: String): Boolean {
		val retries = retriesByKey[key] ?: 0
		if (retries >= maxRetriesPerKey) return false
		retriesByKey[key] = retries + 1
		return true
	}

	fun clear() {
		retriesByKey.clear()
	}
}

internal fun readerPageTurnCanStartPassivePrewarm(
	destroyed: Boolean,
	available: Boolean,
	visualCommitPending: Boolean,
	idle: Boolean
): Boolean = !destroyed && available && !visualCommitPending && idle

internal class ReaderPageTurnController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val bundleSource: ReaderPageTurnBundleSource = ReaderPageTurnBundleSource(),
	private val onRequestPrewarm: () -> Unit = {},
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
	private val prewarmPlans = ArrayDeque<String>()
	private val prewarmRetryBudget = ReaderPageTurnPrewarmRetryBudget()
	private var activePrewarmPlan: ReaderPageTurnTransitionPlan? = null
	private var activePrewarmLivePageIndex: Int? = null
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
		bundleSource.invalidate("destroy")
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
			}
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
			if (prewarmInProgress && activePrewarmPlan?.sameTransitionAs(plan) == true) {
				waitForActivePrewarmBundle(
					webView = webView,
					plan = plan,
					stateGeneration = activeStateGeneration,
					matchingPrewarmSession = prewarmSession
				)
				return@evaluateJavascript
			}
			if (prewarmInProgress) cancelPrewarm()
			prepareBundle(webView, plan, activeStateGeneration)
		}
	}

	private fun waitForActivePrewarmBundle(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		stateGeneration: Long,
		matchingPrewarmSession: Long
	) {
		if (!isPreparationActive(plan, stateGeneration) || !webView.isAttachedToWindow) return
		bundleSource.cached(plan)?.let { cached ->
			activatePreparedTransition(plan, stateGeneration, cached)
			return
		}
		val matchingPrewarmStillActive =
			prewarmInProgress &&
				prewarmSession == matchingPrewarmSession &&
				activePrewarmPlan?.sameTransitionAs(plan) == true
		if (!matchingPrewarmStillActive) {
			markPreparationUnavailable(stateGeneration, "matching-prewarm-unavailable")
			return
		}
		webView.postOnAnimation {
			waitForActivePrewarmBundle(webView, plan, stateGeneration, matchingPrewarmSession)
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
			available = isAvailable,
			visualCommitPending = visualCommitPending,
			idle = state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle
		)) return false
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: return false
		prewarmInProgress = true
		val session = ++prewarmSession
		queryAdjacentPrewarmPlans(webView, session)
		return true
	}

	private fun queryAdjacentPrewarmPlans(webView: WebView, session: Long) {
		if (!isPrewarmActive(webView, session)) return
		val visualPageIndex = slideCoordinator?.visualPageIndex
		val centerExpression = visualPageIndex?.toString()
			?: "Number(bridge.pageTurnPreviewContext?.()?.pageIndex)"
		webView.evaluateJavascript(
			"JSON.stringify((() => {" +
				"const bridge = window.NavicReaderBridge;" +
				"const context = bridge?.pageTurnPreviewContext?.() ?? null;" +
				"const center = $centerExpression;" +
				"const step = context?.layoutMode === 'spread' ? 2 : 1;" +
				"return { context, plans: [" +
					"bridge?.pageTurnTransitionPlan?.('toward-left', center) ?? null," +
					"bridge?.pageTurnTransitionPlan?.('toward-right', center) ?? null," +
					"bridge?.pageTurnTransitionPlan?.('toward-left', center - step) ?? null," +
					"bridge?.pageTurnTransitionPlan?.('toward-right', center - step) ?? null," +
					"bridge?.pageTurnTransitionPlan?.('toward-left', center + step) ?? null," +
					"bridge?.pageTurnTransitionPlan?.('toward-right', center + step) ?? null" +
				"] };" +
			"})())"
		) { encoded ->
			if (!isPrewarmActive(webView, session)) return@evaluateJavascript
			val payload = encoded.javascriptObject()
			val context = payload?.optJSONObject("context")
			val pageIndex = context?.optInt("pageIndex", -1) ?: -1
			val pageCount = context?.optInt("pageCount", 0) ?: 0
			val layoutMode = context?.optString("layoutMode")
			if (pageIndex < 0 || pageCount <= 0 || layoutMode != expectedLayoutMode(webView)) {
				webView.postOnAnimation { queryAdjacentPrewarmPlans(webView, session) }
				return@evaluateJavascript
			}
			prewarmPlans.clear()
			prewarmRetryBudget.clear()
			activePrewarmLivePageIndex = pageIndex
			val center = slideCoordinator?.visualPageIndex ?: pageIndex
			val step = if (layoutMode == "spread") 2 else 1
			val desiredTargets = readerPageSlideSnapshotWindow(center, step, pageCount).drop(1)
			val plans = payload?.optJSONArray("plans")
			val plansByTarget = buildMap<Int, String> {
				if (plans != null) {
					for (index in 0 until plans.length()) {
						val plan = plans.optJSONObject(index) ?: continue
						val source = plan.optInt("sourcePageIndex", -1)
						val target = plan.optInt("targetPageIndex", -1)
						if (source >= 0 && target >= 0 && target in desiredTargets) put(target, plan.toString())
					}
				}
			}
			desiredTargets.mapNotNull(plansByTarget::get).forEach(prewarmPlans::addLast)
			prewarmNext(webView, session)
		}
	}

	private fun expectedLayoutMode(webView: WebView): String =
		if (webView.width >= webView.height * 1.12f) "spread" else "single"

	private fun prewarmNext(webView: WebView, session: Long) {
		if (!isPrewarmActive(webView, session)) return
		if (state.phase != paige.navic.reader.ReaderPageTurnPhase.Idle) {
			finishPrewarm()
			return
		}
		val encodedPlan = prewarmPlans.removeFirstOrNull()
		if (encodedPlan == null) {
			finishPrewarm()
			return
		}
		val generation = bundleSource.currentGeneration()
		val token = "navic-page-turn-prewarm-${++settleGeneration}"
		val plan = ReaderPageTurnTransitionPlan.parse(encodedPlan, token, generation)
		if (plan == null || bundleSource.isCached(plan)) {
			prewarmNext(webView, session)
			return
		}
		activePrewarmPlan = plan
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.beginPageTurnPreviewPreparation?.(${JSONObject.quote(token)}, ${plan.targetPageIndex})"
		) {
			if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
				return@evaluateJavascript
			}
			waitForPrewarmPreviewReady(webView, session, plan, encodedPlan)
		}
	}

	private fun waitForPrewarmPreviewReady(
		webView: WebView,
		session: Long,
		plan: ReaderPageTurnTransitionPlan,
		encodedPlan: String
	) {
		if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
			return
		}
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.pageTurnPreviewState?.(${JSONObject.quote(plan.token)})?.status ?? 'missing'"
		) { encodedStatus ->
			if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
				return@evaluateJavascript
			}
			when (encodedStatus.javascriptString()) {
				"ready" -> capturePreparedPrewarm(webView, session, plan, encodedPlan)
				"failed", "missing" -> {
					activePrewarmPlan = null
					prewarmNext(webView, session)
				}
				else -> webView.postOnAnimation {
					waitForPrewarmPreviewReady(webView, session, plan, encodedPlan)
				}
			}
		}
	}

	private fun capturePreparedPrewarm(
		webView: WebView,
		session: Long,
		plan: ReaderPageTurnTransitionPlan,
		encodedPlan: String
	) {
		if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) return
		val visualPageIndex = slideCoordinator?.visualPageIndex
		if (visualPageIndex == null) {
			completePreparedPrewarm(webView, session, plan, encodedPlan, null)
			return
		}
		val sourceCached = bundleSource.hasCachedSnapshot(plan.sourcePageIndex, plan.kind)
		val shieldCached = bundleSource.hasCachedSnapshot(visualPageIndex, plan.kind)
		when {
			sourceCached && shieldCached ->
				captureCachedPrewarm(webView, session, plan, encodedPlan, visualPageIndex)
			plan.sourcePageIndex == visualPageIndex ->
				seedInitialPrewarmSnapshot(webView, session, plan, encodedPlan, visualPageIndex)
			else -> {
				Logger.w(
					ReaderPageTurnControllerTag,
					"Page-turn rolling prewarm cache unavailable source=${plan.sourcePageIndex} " +
						"visual=$visualPageIndex target=${plan.targetPageIndex}"
				)
				completePreparedPrewarm(webView, session, plan, encodedPlan, null)
			}
		}
	}

	private fun captureCachedPrewarm(
		webView: WebView,
		session: Long,
		plan: ReaderPageTurnTransitionPlan,
		encodedPlan: String,
		visualPageIndex: Int
	) {
		bundleSource.capturePreparedBundle(
			webView = webView,
			plan = plan,
			shieldPageIndex = visualPageIndex,
			onStagingStarted = ::attachPreparationShield
		) { transition ->
			completePreparedPrewarm(webView, session, plan, encodedPlan, transition)
		}
	}

	private fun seedInitialPrewarmSnapshot(
		webView: WebView,
		session: Long,
		plan: ReaderPageTurnTransitionPlan,
		encodedPlan: String,
		visualPageIndex: Int
	) {
		Logger.i(
			ReaderPageTurnControllerTag,
			"Page-turn initial prewarm seed page=$visualPageIndex target=${plan.targetPageIndex}"
		)
		bundleSource.captureCurrentSurface(webView, plan.generation) { current ->
			if (current == null || !isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
				current?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				completePreparedPrewarm(webView, session, plan, encodedPlan, null)
				return@captureCurrentSurface
			}
			bundleSource.captureBundle(
				webView = webView,
				plan = plan,
				current = current,
				currentPageIndex = visualPageIndex,
				currentCanRepresentSource = true,
				onStagingStarted = ::attachPreparationShield
			) { transition ->
				completePreparedPrewarm(webView, session, plan, encodedPlan, transition)
			}
		}
	}

	private fun completePreparedPrewarm(
		webView: WebView,
		session: Long,
		plan: ReaderPageTurnTransitionPlan,
		encodedPlan: String,
		transition: ReaderPageSlideTransition?
	) {
		removePreparationShield()
		if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
			transition?.close()
			if (activePrewarmPlan === plan) activePrewarmPlan = null
			return
		}
		activePrewarmPlan = null
		if (transition == null) {
			if (prewarmRetryBudget.consume(plan.cacheKey)) {
				prewarmPlans.addLast(encodedPlan)
			}
			Logger.w(ReaderPageTurnControllerTag, "Page-turn prewarm failed target=${plan.targetPageIndex}")
		}
		transition?.close()
		prewarmNext(webView, session)
	}

	private fun isPrewarmActive(webView: WebView, session: Long): Boolean =
		prewarmInProgress &&
			prewarmSession == session &&
			!destroyed &&
			webView.isAttachedToWindow

	private fun finishPrewarm() {
		activePrewarmPlan = null
		activePrewarmLivePageIndex = null
		prewarmPlans.clear()
		prewarmRetryBudget.clear()
		prewarmInProgress = false
		removePreparationShield()
	}

	private fun cancelPrewarm() {
		prewarmSession += 1
		activePrewarmPlan = null
		activePrewarmLivePageIndex = null
		prewarmPlans.clear()
		prewarmRetryBudget.clear()
		prewarmInProgress = false
		removePreparationShield()
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
		val quotedToken = JSONObject.quote(plan.token)
		bundleSource.captureCurrentSurface(webView, plan.generation) { current ->
			if (current == null || !isPreparationActive(plan, stateGeneration)) {
				current?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				markPreparationUnavailable(stateGeneration, "current-surface-unavailable")
				return@captureCurrentSurface
			}
			webView.evaluateJavascript(
				"window.NavicReaderBridge?.beginPageTurnPreviewPreparation?.($quotedToken, ${plan.targetPageIndex})"
			) {
				if (!isPreparationActive(plan, stateGeneration)) {
					current.bitmap.takeUnless { it.isRecycled }?.recycle()
					return@evaluateJavascript
				}
				waitForPreviewReady(webView, plan, stateGeneration, current)
			}
		}
	}

	private fun waitForPreviewReady(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		stateGeneration: Long,
		current: ReaderPageTurnCaptureResult
	) {
		if (!isPreparationActive(plan, stateGeneration) || !webView.isAttachedToWindow) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return
		}
		val quotedToken = JSONObject.quote(plan.token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.pageTurnPreviewState?.($quotedToken)?.status ?? 'missing'"
		) { encodedStatus ->
			if (!isPreparationActive(plan, stateGeneration)) {
				current.bitmap.takeUnless { it.isRecycled }?.recycle()
				return@evaluateJavascript
			}
			when (encodedStatus.javascriptString()) {
				"ready" -> bundleSource.captureBundle(
					webView = webView,
				plan = plan,
				current = current,
				currentCanRepresentSource = plan.sourcePageIndex == slideCoordinator?.settledPageIndex,
				onStagingStarted = ::attachPreparationShield
				) { transition ->
					if (transition == null) {
						markPreparationUnavailable(stateGeneration, "destination-bundle-unavailable")
						return@captureBundle
					}
					if (!isPreparationActive(plan, stateGeneration)) {
						transition.close()
						removePreparationShield()
						return@captureBundle
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
					bundleSource.cacheCurrentSnapshot(plan, current)?.let(::attachPreparationShield)
					markPreparationUnavailable(
						stateGeneration,
						"destination-preview-${encodedStatus.javascriptString()}"
					)
				}
				else -> webView.postOnAnimation {
					waitForPreviewReady(webView, plan, stateGeneration, current)
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
