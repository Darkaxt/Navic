package paige.navic.ui.screens.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Standalone ReaderDev harness for the original PlayLikeCurl interaction. */
class ReaderPlayLikeCurlReferenceView(
	context: Context,
	mode: ReaderPlayLikeCurlReferenceMode = ReaderPlayLikeCurlReferenceMode.Reference
) : GLSurfaceView(context) {
	private val bitmapSource: ReaderPlayLikeCurlBitmapSource = when (mode) {
		ReaderPlayLikeCurlReferenceMode.Reference -> ReaderPlayLikeCurlAssetBitmapSource(context)
		ReaderPlayLikeCurlReferenceMode.Diagnostic -> ReaderPlayLikeCurlDiagnosticBitmapSource()
	}
	private val model = ReaderPlayLikeCurlReferenceModel(pageCount = bitmapSource.pageCount)
	private val pageRenderer = ReaderPlayLikeCurlReferenceRenderer(model)
	private val rasterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val rasterAdapter = ReaderPlayLikeCurlRasterAdapter(
		scope = rasterScope,
		loader = bitmapSource,
		release = Bitmap::recycle
	)
	private var settlementAnimator: ValueAnimator? = null
	private var settlementRunning = false
	@Volatile
	private var requestedProfile: ReaderPlayLikeCurlRasterProfile? = null
	@Volatile
	private var interactionReady = false
	@Volatile
	private var detached = false

	var onPreparationProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
	var onPreparationCoverReady: (Bitmap) -> Unit = {}
	var onInteractionReadyChanged: (Boolean) -> Unit = {}

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

	override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
		super.onSizeChanged(width, height, oldWidth, oldHeight)
		if (width <= 0 || height <= 0 || (width == oldWidth && height == oldHeight)) return
		prepareRasterDeck(
			if (height > width) ReaderPlayLikeCurlOrientation.Portrait
			else ReaderPlayLikeCurlOrientation.Landscape
		)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (!interactionReady) return true
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

	override fun onDetachedFromWindow() {
		detached = true
		requestedProfile = null
		interactionReady = false
		settlementAnimator?.cancel()
		queueEvent(pageRenderer::releaseRasterDeck)
		rasterAdapter.close()
		super.onDetachedFromWindow()
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

	private fun prepareRasterDeck(orientation: ReaderPlayLikeCurlOrientation) {
		val profile = bitmapSource.profile(orientation)
		if (requestedProfile == profile && interactionReady) return
		requestedProfile = profile
		interactionReady = false
		onInteractionReadyChanged(false)
		val preparation = rasterAdapter.prepare(
			profile = profile,
			pageIndices = (0 until bitmapSource.pageCount).toList()
		) { progress ->
			post {
				if (requestedProfile == profile && !detached) {
					onPreparationProgress(progress.completed, progress.total)
				}
			}
		}
		rasterScope.launch {
			val deck = preparation.await() ?: return@launch
			if (requestedProfile != profile || detached) {
				deck.close()
				return@launch
			}
			deck.value(0)?.let { cover -> post { onPreparationCoverReady(cover) } }
			queueEvent {
				if (requestedProfile != profile || detached) {
					deck.close()
					return@queueEvent
				}
				pageRenderer.installRasterDeck(deck)
				post {
					if (requestedProfile == profile && !detached) {
						interactionReady = true
						onInteractionReadyChanged(true)
					}
				}
			}
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
		const val SwipeMinDistance = 120
		const val SwipeMaxOffPath = 250
		const val SwipeThresholdVelocity = 200
	}
}
