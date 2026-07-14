package paige.navic.ui.screens.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import kotlin.math.abs

/** Standalone ReaderDev harness for the original PlayLikeCurl interaction. */
class ReaderPlayLikeCurlReferenceView(context: Context) : GLSurfaceView(context) {
	private val model = ReaderPlayLikeCurlReferenceModel(pageCount = ReferencePageCount)
	private val pageRenderer = ReaderPlayLikeCurlReferenceRenderer(context, model)
	private var settlementAnimator: ValueAnimator? = null
	private var settlementRunning = false

	private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
		override fun onDown(event: MotionEvent): Boolean = true

		override fun onFling(
			downEvent: MotionEvent?,
			moveEvent: MotionEvent,
			velocityX: Float,
			velocityY: Float
		): Boolean {
			val start = downEvent ?: return false
			if (abs(start.y - moveEvent.y) > SwipeMaxOffPath) return false
			if (abs(velocityX) < SwipeThresholdVelocity) return false
			return when {
				start.x - moveEvent.x > SwipeMinDistance -> {
					settle(model.flingTowardNext())
					true
				}
				moveEvent.x - start.x > SwipeMinDistance -> {
					settle(model.flingTowardPrevious())
					true
				}
				else -> false
			}
		}
	})

	init {
		setEGLContextClientVersion(2)
		setEGLConfigChooser(8, 8, 8, 8, 16, 0)
		setRenderer(pageRenderer)
		renderMode = RENDERMODE_CONTINUOUSLY
		preserveEGLContextOnPause = true
		isClickable = true
		isFocusable = true
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (settlementRunning) return true
		val detectorHandled = gestureDetector.onTouchEvent(event)
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> model.beginGesture(event.x)
			MotionEvent.ACTION_MOVE -> model.dragTo(event.x, width.toFloat())
			MotionEvent.ACTION_UP -> if (!detectorHandled) settle(model.release())
			MotionEvent.ACTION_CANCEL -> model.cancelGesture()
		}
		return true
	}

	override fun performClick(): Boolean {
		super.performClick()
		return true
	}

	private fun settle(settlement: ReaderPlayLikeCurlSettlement) {
		val startPercent = currentPagePercent()
		if (startPercent == settlement.targetPercent.toFloat()) {
			model.completeSettlement(settlement)
			return
		}
		settlementAnimator?.cancel()
		settlementRunning = true
		settlementAnimator = ValueAnimator.ofFloat(startPercent, settlement.targetPercent.toFloat()).apply {
			duration = settlement.durationMillis
			interpolator = settlement.interpolator.toAndroidInterpolator()
			addUpdateListener { animation ->
				model.updateSettlement(animation.animatedValue as Float)
			}
			addListener(object : AnimatorListenerAdapter() {
				private var cancelled = false

				override fun onAnimationCancel(animation: Animator) {
					cancelled = true
				}

				override fun onAnimationEnd(animation: Animator) {
					if (!cancelled) model.completeSettlement(settlement)
					settlementRunning = false
				}
			})
			start()
		}
	}

	private fun currentPagePercent(): Float {
		val activeState = when (model.activePage) {
			ReaderPlayLikeCurlActivePage.Left -> model.leftPage
			ReaderPlayLikeCurlActivePage.Right -> model.rightPage
			ReaderPlayLikeCurlActivePage.Current -> model.frontPage
		}
		return activeState.curlPosition / ReaderPlayLikeCurlReferenceModel.Grid * 100f
	}

	private fun ReaderPlayLikeCurlInterpolator.toAndroidInterpolator(): Interpolator = when (this) {
		ReaderPlayLikeCurlInterpolator.AccelerateDecelerate -> AccelerateDecelerateInterpolator()
		ReaderPlayLikeCurlInterpolator.Decelerate -> DecelerateInterpolator()
	}

	private companion object {
		const val ReferencePageCount = 8
		const val SwipeMinDistance = 120
		const val SwipeMaxOffPath = 250
		const val SwipeThresholdVelocity = 200
	}
}
