package paige.navic.ui.screens.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageTurnEffect
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderPageTurnStateMachine
import paige.navic.util.core.Logger

private const val ReaderPageTurnControllerTag = "ReaderPageTurnController"

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

internal class ReaderPageTurnController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val bundleSource: ReaderPageTurnBundleSource = ReaderPageTurnBundleSource(),
	private val onRequestPrewarm: () -> Unit = {},
	private val onCommitTurn: (ReaderPageTurnPhysicalDirection) -> Unit
) {
	private val state = ReaderPageTurnStateMachine()
	private var activeBundle: ReaderPageTurnBitmapBundle? = null
	private var activePlan: ReaderPageTurnTransitionPlan? = null
	private var activeStateGeneration = 0L
	private var curlView: ReaderPageTurnCurlView? = null
	private var animation: ValueAnimator? = null
	private var enabledForSession = true
	private var edgeOriginY = 0f
	private var pointerY = 0f
	private var gestureHostHeight = 0
	private var settleGeneration = 0L
	private var activeSettleToken: String? = null
	private var releasedWhilePreparing = false
	private var prewarmSession = 0L
	private var prewarmInProgress = false
	private val prewarmPlans = ArrayDeque<String>()
	private val prewarmRetryBudget = ReaderPageTurnPrewarmRetryBudget()
	private var activePrewarmPlan: ReaderPageTurnTransitionPlan? = null
	private var preparationShield: ImageView? = null
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
		if (releasedWhilePreparing && activePlan != null) resolveColdRelease()
	}

	private fun pageAxisWidth(viewWidth: Int): Int =
		activeBundle?.turningFront?.width
			?: if (state.spread) (viewWidth / 2).coerceAtLeast(1) else viewWidth

	private fun setGestureY(edgeOriginY: Float, pointerY: Float, viewHeight: Int) {
		gestureHostHeight = viewHeight.coerceAtLeast(1)
		this.edgeOriginY = edgeOriginY.coerceIn(0f, gestureHostHeight.toFloat())
		this.pointerY = pointerY.coerceIn(0f, gestureHostHeight.toFloat())
		applyGestureYToOverlay()
	}

	fun cancel() {
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
		activeSettleToken = null
		cancelPrewarm()
		cancelPreparation()
		state.cancel()
		animation?.cancel()
		animation = null
		detachOverlay()
		bundleSource.invalidate("destroy")
		applicationContext.unregisterComponentCallbacks(memoryCallbacks)
	}

	fun invalidate(reason: String) {
		if (destroyed) return
		activeSettleToken = null
		cancelPrewarm()
		cancelPreparation()
		removePreparationShield()
		state.cancel()
		animation?.cancel()
		animation = null
		detachOverlay(schedulePrewarm = false)
		bundleSource.invalidate(reason)
	}

	private fun begin(deltaX: Float) {
		cancelPrewarm()
		releasedWhilePreparing = false
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
		val bundleGeneration = bundleSource.beginGeneration()
		val token = "navic-page-turn-preview-${++settleGeneration}"
		val physicalDirection = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) "toward-left" else "toward-right"
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnTransitionPlan?.('$physicalDirection') ?? null)"
		) { encodedPlan ->
			val plan = ReaderPageTurnTransitionPlan.parse(encodedPlan, token, bundleGeneration)
			if (plan == null || !plan.matchesLayout(state.spread)) {
				markPreparationUnavailable(activeStateGeneration, "transition-plan-unavailable")
				return@evaluateJavascript
			}
			if (!state.setTargetPageIndex(activeStateGeneration, plan.targetPageIndex)) {
				return@evaluateJavascript
			}
			activePlan = plan
			bundleSource.cached(plan)?.let { cached ->
				activeBundle = cached
				handleEffects(state.captureSucceeded(activeStateGeneration))
				return@evaluateJavascript
			}
			if (releasedWhilePreparing) {
				resolveColdRelease()
				return@evaluateJavascript
			}
			prepareBundle(webView, plan, activeStateGeneration)
		}
	}

	fun prewarmAdjacent(): Boolean {
		if (prewarmInProgress) return true
		if (
			destroyed ||
			!isAvailable ||
			state.phase != paige.navic.reader.ReaderPageTurnPhase.Idle ||
			curlView != null
		) return false
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: return false
		prewarmInProgress = true
		val session = ++prewarmSession
		queryAdjacentPrewarmPlans(webView, session)
		return true
	}

	private fun queryAdjacentPrewarmPlans(webView: WebView, session: Long) {
		if (!isPrewarmActive(webView, session)) return
		webView.evaluateJavascript(
			"JSON.stringify({" +
				"context: window.NavicReaderBridge?.pageTurnPreviewContext?.() ?? null," +
				"towardLeft: window.NavicReaderBridge?.pageTurnTransitionPlan?.('toward-left') ?? null," +
				"towardRight: window.NavicReaderBridge?.pageTurnTransitionPlan?.('toward-right') ?? null" +
			"})"
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
			payload?.optJSONObject("towardLeft")?.toString()?.let(prewarmPlans::addLast)
			payload?.optJSONObject("towardRight")?.toString()?.let(prewarmPlans::addLast)
			prewarmNext(webView, session)
		}
	}

	private fun expectedLayoutMode(webView: WebView): String =
		if (webView.width >= webView.height * 1.12f) "spread" else "single"

	private fun prewarmNext(webView: WebView, session: Long) {
		if (!isPrewarmActive(webView, session)) return
		val encodedPlan = prewarmPlans.removeFirstOrNull()
		if (encodedPlan == null) {
			finishPrewarm()
			return
		}
		val generation = bundleSource.beginGeneration()
		val token = "navic-page-turn-prewarm-${++settleGeneration}"
		val plan = ReaderPageTurnTransitionPlan.parse(encodedPlan, token, generation)
		if (plan == null || bundleSource.cached(plan) != null) {
			prewarmNext(webView, session)
			return
		}
		activePrewarmPlan = plan
		bundleSource.captureCurrentSurface(webView, plan.generation) { current ->
			if (current == null) {
				if (
					isPrewarmActive(webView, session) &&
					activePrewarmPlan === plan &&
					prewarmRetryBudget.consume(plan.cacheKey)
				) {
					prewarmPlans.addLast(encodedPlan)
				}
				activePrewarmPlan = null
				prewarmNext(webView, session)
				return@captureCurrentSurface
			}
			if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
				current.bitmap.takeUnless { it.isRecycled }?.recycle()
				activePrewarmPlan = null
				prewarmNext(webView, session)
				return@captureCurrentSurface
			}
			webView.evaluateJavascript(
				"window.NavicReaderBridge?.beginPageTurnPreviewPreparation?.(${JSONObject.quote(token)}, ${plan.targetPageIndex})"
			) {
				if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
					current.bitmap.takeUnless { it.isRecycled }?.recycle()
					return@evaluateJavascript
				}
				waitForPrewarmPreviewReady(webView, session, plan, current)
			}
		}
	}

	private fun waitForPrewarmPreviewReady(
		webView: WebView,
		session: Long,
		plan: ReaderPageTurnTransitionPlan,
		current: ReaderPageTurnCaptureResult
	) {
		if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return
		}
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.pageTurnPreviewState?.(${JSONObject.quote(plan.token)})?.status ?? 'missing'"
		) { encodedStatus ->
			if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
				current.bitmap.takeUnless { it.isRecycled }?.recycle()
				return@evaluateJavascript
			}
			when (encodedStatus.javascriptString()) {
				"ready" -> bundleSource.captureBundle(
					webView = webView,
					plan = plan,
					current = current,
					onStagingStarted = { attachPreparationShield(current) }
				) { bundle ->
					removePreparationShield()
					if (!isPrewarmActive(webView, session) || activePrewarmPlan !== plan) {
						activePrewarmPlan = null
						return@captureBundle
					}
					activePrewarmPlan = null
					if (bundle == null) {
						Logger.w(ReaderPageTurnControllerTag, "Page-turn prewarm failed target=${plan.targetPageIndex}")
					}
					prewarmNext(webView, session)
				}
				"failed", "missing" -> {
					current.bitmap.takeUnless { it.isRecycled }?.recycle()
					activePrewarmPlan = null
					prewarmNext(webView, session)
				}
				else -> webView.postOnAnimation {
					waitForPrewarmPreviewReady(webView, session, plan, current)
				}
			}
		}
	}

	private fun isPrewarmActive(webView: WebView, session: Long): Boolean =
		prewarmInProgress &&
			prewarmSession == session &&
			!destroyed &&
			webView.isAttachedToWindow &&
			state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle

	private fun finishPrewarm() {
		activePrewarmPlan = null
		prewarmPlans.clear()
		prewarmRetryBudget.clear()
		prewarmInProgress = false
		removePreparationShield()
		destroyPageTurnPreviewRenderer("prewarm-complete")
	}

	private fun cancelPrewarm() {
		prewarmSession += 1
		activePrewarmPlan = null
		prewarmPlans.clear()
		prewarmRetryBudget.clear()
		prewarmInProgress = false
		bundleSource.cancelActivePreparation()
		removePreparationShield()
		destroyPageTurnPreviewRenderer("prewarm-cancelled")
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
				onStagingStarted = { attachPreparationShield(current) }
			) { bundle ->
					removePreparationShield()
					if (bundle == null || !isPreparationActive(plan, stateGeneration)) {
						markPreparationUnavailable(stateGeneration, "destination-bundle-unavailable")
						return@captureBundle
					}
					activeBundle = bundle
					handleEffects(state.captureSucceeded(stateGeneration))
				}
				"failed", "missing" -> {
					current.bitmap.takeUnless { it.isRecycled }?.recycle()
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
		bundleSource.cancelActivePreparation()
		activePlan?.let { plan ->
			webViewProvider()?.evaluateJavascript(
				"window.NavicReaderBridge?.restorePageTurnLiveComposition?.(${JSONObject.quote(plan.token)})"
			) { }
		}
		removePreparationShield()
		Logger.w(ReaderPageTurnControllerTag, "Page-turn preparation unavailable reason=$reason")
		if (releasedWhilePreparing) resolveColdRelease()
	}

	private fun attachPreparationShield(current: ReaderPageTurnCaptureResult) {
		removePreparationShield()
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val rect = current.sourceRectInWindow
		val shield = ImageView(host.context).apply {
			setImageBitmap(current.bitmap)
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
		val shield = preparationShield ?: return
		preparationShield = null
		shield.setImageDrawable(null)
		(shield.parent as? ViewGroup)?.removeView(shield)
	}

	private fun cancelColdPreparation() {
		val generation = activeStateGeneration
		if (generation != 0L) state.captureFailed(generation)
		cancelPreparation()
		removePreparationShield()
		releasedWhilePreparing = false
		onRequestPrewarm()
	}

	private fun resolveColdRelease() {
		when (state.pendingReleaseCommit) {
			true -> activePlan?.let(::commitColdFallback) ?: commitRelativeColdFallback(state.direction)
			false -> cancelColdPreparation()
			null -> Unit
		}
	}

	private fun commitColdFallback(plan: ReaderPageTurnTransitionPlan) {
		if (activePlan !== plan) return
		val direction = state.direction
		val generation = activeStateGeneration
		if (generation != 0L) state.captureFailed(generation)
		val webView = webViewProvider()
		bundleSource.cancelActivePreparation()
		webView?.evaluateJavascript(
			"window.NavicReaderBridge?.restorePageTurnLiveComposition?.(${JSONObject.quote(plan.token)})"
		) { }
		removePreparationShield()
		activePlan = null
		activeBundle = null
		activeStateGeneration = 0L
		releasedWhilePreparing = false
		if (webView == null || !webView.isAttachedToWindow) {
			onCommitTurn(direction)
			return
		}
		activeSettleToken = plan.token
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.dispatch?.({ type: 'goToVisualPage', pageIndex: ${plan.targetPageIndex}, settleToken: ${JSONObject.quote(plan.token)} })"
		) {
			if (activeSettleToken == plan.token) pollExactPageTurnSettle(webView, plan)
		}
		Logger.i(
			ReaderPageTurnControllerTag,
			"Page-turn cold fallback target=${plan.targetPageIndex} kind=${plan.kind}"
		)
	}

	private fun commitRelativeColdFallback(direction: ReaderPageTurnPhysicalDirection) {
		val generation = activeStateGeneration
		if (generation != 0L) state.captureFailed(generation)
		cancelPreparation()
		removePreparationShield()
		val webView = webViewProvider()
		if (webView == null || !webView.isAttachedToWindow) {
			onCommitTurn(direction)
			onRequestPrewarm()
			return
		}
		val token = "navic-page-turn-cold-${++settleGeneration}"
		activeSettleToken = token
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.armNativePageTurnSettle?.(${JSONObject.quote(token)})"
		) {
			if (activeSettleToken != token) return@evaluateJavascript
			onCommitTurn(direction)
			pollNativePageTurnSettle(webView, token)
		}
		Logger.i(ReaderPageTurnControllerTag, "Page-turn cold relative fallback direction=$direction")
	}

	private fun cancelPreparation() {
		bundleSource.cancelActivePreparation()
		activePlan?.let { plan ->
			webViewProvider()?.evaluateJavascript(
				"window.NavicReaderBridge?.restorePageTurnLiveComposition?.(${JSONObject.quote(plan.token)})"
			) { }
		}
		activePlan = null
		activeStateGeneration = 0L
		releasedWhilePreparing = false
	}

	private fun handleEffects(effects: List<ReaderPageTurnEffect>) {
		for (effect in effects) {
			when (effect) {
				ReaderPageTurnEffect.AttachOverlay -> attachOverlay()
				is ReaderPageTurnEffect.Render -> curlView?.setProgress(effect.progress)
				is ReaderPageTurnEffect.AnimateCommit -> animateCommit(effect.fromProgress)
				is ReaderPageTurnEffect.AnimateRelax -> animateRelax(effect.fromProgress)
				is ReaderPageTurnEffect.Commit -> host.postOnAnimation { commitTurn(effect.direction) }
				ReaderPageTurnEffect.ShowFinalBase -> curlView?.showFinalBase()
				ReaderPageTurnEffect.DetachOverlay -> detachAfterNavigationFrame()
			}
		}
	}

	private fun commitTurn(direction: ReaderPageTurnPhysicalDirection) {
		val webView = webViewProvider()
		if (webView == null || !webView.isAttachedToWindow) {
			onCommitTurn(direction)
			markDestinationSettled()
			return
		}
		val plan = activePlan
		val token = plan?.token ?: "navic-page-turn-${++settleGeneration}"
		activeSettleToken = token
		val quotedToken = JSONObject.quote(token)
		if (plan != null) {
			webView.evaluateJavascript(
				"window.NavicReaderBridge?.dispatch?.({ type: 'goToVisualPage', pageIndex: ${plan.targetPageIndex}, settleToken: $quotedToken })"
			) {
				if (activeSettleToken == token) pollExactPageTurnSettle(webView, plan)
			}
			return
		}
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.armNativePageTurnSettle?.($quotedToken)"
		) {
			if (activeSettleToken != token) return@evaluateJavascript
			onCommitTurn(direction)
			pollNativePageTurnSettle(webView, token)
		}
	}

	private fun pollExactPageTurnSettle(webView: WebView, plan: ReaderPageTurnTransitionPlan) {
		if (activeSettleToken != plan.token || !webView.isAttachedToWindow) return
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.nativePageTurnSettledState?.() ?? null)"
		) { encoded ->
			if (activeSettleToken != plan.token) return@evaluateJavascript
			val settled = encoded.javascriptObject()
			val settledToken = settled?.optString("token")
			val settledPageIndex = settled?.optInt("pageIndex", -1) ?: -1
			if (settledToken == plan.token && settledPageIndex == plan.targetPageIndex) {
				activeSettleToken = null
				if (state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle) {
					onRequestPrewarm()
				} else {
					markDestinationSettled(settledPageIndex)
				}
			} else {
				webView.postOnAnimation { pollExactPageTurnSettle(webView, plan) }
			}
		}
	}

	private fun pollNativePageTurnSettle(webView: WebView, token: String) {
		if (activeSettleToken != token || !webView.isAttachedToWindow) return
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.nativePageTurnSettledToken?.() ?? null"
		) { encoded ->
			if (activeSettleToken != token) return@evaluateJavascript
			val settledToken = runCatching { JSONTokener(encoded.orEmpty()).nextValue() as? String }.getOrNull()
			if (settledToken == token) {
				activeSettleToken = null
				markDestinationSettled()
			} else {
				webView.postOnAnimation { pollNativePageTurnSettle(webView, token) }
			}
		}
	}

	private fun attachOverlay() {
		val bundle = activeBundle ?: return
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val left = bundle.surfaceRectInWindow.left - hostLocation[0]
		val top = bundle.surfaceRectInWindow.top - hostLocation[1]
		val view = ReaderPageTurnCurlView(host.context).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			setBundle(
				bundle = bundle,
				direction = state.direction,
				reverseFaceColor = Color.rgb(234, 217, 174),
				surfaceLeft = left,
				surfaceTop = top
			)
		}
		host.addView(view)
		curlView = view
		applyGestureYToOverlay()
		Logger.i(
			ReaderPageTurnControllerTag,
			"Page-turn overlay attached kind=${bundle.plan.kind} target=${bundle.plan.targetPageIndex} " +
				"left=$left top=$top size=${bundle.currentBase.width}x${bundle.currentBase.height}"
		)
	}

	private fun applyGestureYToOverlay() {
		val bundle = activeBundle ?: return
		val view = curlView ?: return
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val pageTopInHost = bundle.surfaceRectInWindow.top - hostLocation[1] + bundle.turningFrontRectInSurface.top
		view.setGestureY(
			edgeOriginY = edgeOriginY - pageTopInHost,
			pointerY = pointerY - pageTopInHost
		)
	}

	private fun animateCommit(fromProgress: Float) {
		animate(fromProgress, CommitEndProgress, CommitAnimationDurationMs) {
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
			addUpdateListener { animator -> curlView?.setProgress(animator.animatedValue as Float) }
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
		val view = curlView
		curlView = null
		if (view != null) {
			(view.parent as? ViewGroup)?.removeView(view)
			view.clearBundle()
		}
		activeBundle = null
		activePlan = null
		activeStateGeneration = 0L
		releasedWhilePreparing = false
		if (schedulePrewarm && !destroyed) {
			onRequestPrewarm()
		}
	}

	private fun markDestinationSettled(pageIndex: Int? = null) {
		handleEffects(state.destinationSettled(pageIndex))
	}

	private companion object {
		const val CommitAnimationDurationMs = 350L
		const val CommitEndProgress = 2f
		const val RelaxAnimationDurationMs = 160L
	}
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
