package paige.navic.ui.screens.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageTurnEffect
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderPageTurnStateMachine
import paige.navic.util.core.Logger

private const val ReaderPageTurnControllerTag = "ReaderPageTurnController"

internal class ReaderPageTurnController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val bitmapSource: ReaderPageTurnBitmapSource = ReaderPageTurnBitmapSource(),
	private val onCommitTurn: (ReaderPageTurnPhysicalDirection) -> Unit
) {
	private val state = ReaderPageTurnStateMachine()
	private var captureResult: ReaderPageTurnCaptureResult? = null
	private var curlView: ReaderPageTurnCurlView? = null
	private var animation: ValueAnimator? = null
	private var enabledForSession = true
	private var edgeOriginY = 0f
	private var pointerY = 0f
	private var gestureHostHeight = 0
	private var settleGeneration = 0L
	private var activeSettleToken: String? = null

	val isAvailable: Boolean
		get() = enabledForSession && bitmapSource.isAvailable

	fun update(deltaX: Float, viewWidth: Int, edgeOriginY: Float, pointerY: Float, viewHeight: Int) {
		if (!isAvailable || viewWidth <= 0) return
		setGestureY(edgeOriginY, pointerY, viewHeight)
		if (state.phase == paige.navic.reader.ReaderPageTurnPhase.Idle) begin(deltaX)
		handleEffects(state.update(deltaX, pageAxisWidth(viewWidth), SystemClock.uptimeMillis()))
	}

	fun release(deltaX: Float, viewWidth: Int, edgeOriginY: Float, pointerY: Float, viewHeight: Int) {
		if (!isAvailable || viewWidth <= 0) return
		setGestureY(edgeOriginY, pointerY, viewHeight)
		handleEffects(state.release(deltaX, pageAxisWidth(viewWidth), SystemClock.uptimeMillis()))
	}

	private fun pageAxisWidth(viewWidth: Int): Int =
		captureResult?.bitmap?.width
			?: if (state.spread) (viewWidth / 2).coerceAtLeast(1) else viewWidth

	private fun setGestureY(edgeOriginY: Float, pointerY: Float, viewHeight: Int) {
		gestureHostHeight = viewHeight.coerceAtLeast(1)
		this.edgeOriginY = edgeOriginY.coerceIn(0f, gestureHostHeight.toFloat())
		this.pointerY = pointerY.coerceIn(0f, gestureHostHeight.toFloat())
		applyGestureYToOverlay()
	}

	fun cancel() {
		activeSettleToken = null
		val effects = state.cancel()
		animation?.cancel()
		animation = null
		handleEffects(effects)
	}

	fun destroy() {
		activeSettleToken = null
		state.cancel()
		animation?.cancel()
		animation = null
		detachOverlay()
	}

	private fun begin(deltaX: Float) {
		val direction = if (deltaX < 0f) {
			ReaderPageTurnPhysicalDirection.TowardLeft
		} else {
			ReaderPageTurnPhysicalDirection.TowardRight
		}
		val webView = webViewProvider() ?: run {
			enabledForSession = false
			return
		}
		val generation = state.begin(direction, spread = webView.width >= webView.height * 1.12f)
		bitmapSource.capturePage(webView, direction) { result ->
			if (result == null) {
				if (state.captureFailed(generation)) {
					Logger.w(ReaderPageTurnControllerTag, "Page-turn capture unavailable; disabling Canvas animation for session")
					enabledForSession = false
				}
			} else {
				val effects = state.captureSucceeded(generation)
				if (effects.isEmpty()) {
					result.bitmap.takeUnless { it.isRecycled }?.recycle()
				} else {
					captureResult = result
					handleEffects(effects)
				}
			}
		}
	}

	private fun handleEffects(effects: List<ReaderPageTurnEffect>) {
		for (effect in effects) {
			when (effect) {
				ReaderPageTurnEffect.AttachOverlay -> attachOverlay()
				is ReaderPageTurnEffect.Render -> curlView?.setProgress(effect.progress)
				is ReaderPageTurnEffect.AnimateCommit -> animateCommit(effect.fromProgress)
				is ReaderPageTurnEffect.AnimateRelax -> animateRelax(effect.fromProgress)
				is ReaderPageTurnEffect.Commit -> commitTurn(effect.direction)
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
		val token = "navic-page-turn-${++settleGeneration}"
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
		val result = captureResult ?: return
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val left = result.sourceRectInWindow.left - hostLocation[0]
		val top = result.sourceRectInWindow.top - hostLocation[1]
		val view = ReaderPageTurnCurlView(host.context).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			setPage(
				bitmap = result.bitmap,
				direction = state.direction,
				reverseFaceColor = result.geometry.reverseFaceColorArgb?.toInt() ?: Color.rgb(234, 217, 174),
				pageLeft = left,
				pageTop = top
			)
		}
		host.addView(view)
		curlView = view
		applyGestureYToOverlay()
		Logger.i(
			ReaderPageTurnControllerTag,
			"Page-turn overlay attached mode=${result.geometry.mode} role=${result.geometry.pageFor(state.direction)?.role} " +
				"left=$left top=$top size=${result.bitmap.width}x${result.bitmap.height}"
		)
	}

	private fun applyGestureYToOverlay() {
		val result = captureResult ?: return
		val view = curlView ?: return
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val pageTopInHost = result.sourceRectInWindow.top - hostLocation[1]
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

	private fun detachOverlay() {
		activeSettleToken = null
		val view = curlView
		curlView = null
		if (view != null) {
			(view.parent as? ViewGroup)?.removeView(view)
			view.releaseBitmap()?.takeUnless { it.isRecycled }?.recycle()
		}
		captureResult = null
	}

	private fun markDestinationSettled() {
		handleEffects(state.destinationSettled())
	}

	private companion object {
		const val CommitAnimationDurationMs = 350L
		const val CommitEndProgress = 2f
		const val RelaxAnimationDurationMs = 160L
	}
}
