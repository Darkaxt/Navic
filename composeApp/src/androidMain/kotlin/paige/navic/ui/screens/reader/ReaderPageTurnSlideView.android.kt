package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import kotlin.math.max

internal class ReaderPageTurnSlideView(context: Context) : View(context) {
	private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var bundle: ReaderPageTurnBitmapBundle? = null
	private var direction = ReaderPageTurnPhysicalDirection.TowardLeft
	private var progress = 0f
	private var surfaceLeft = 0f
	private var surfaceTop = 0f
	private var showFinalBase = false

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)
	}

	fun setBundle(
		bundle: ReaderPageTurnBitmapBundle,
		direction: ReaderPageTurnPhysicalDirection,
		surfaceLeft: Int,
		surfaceTop: Int
	) {
		this.bundle = bundle
		this.direction = direction
		this.surfaceLeft = surfaceLeft.toFloat()
		this.surfaceTop = surfaceTop.toFloat()
		showFinalBase = false
		invalidate()
	}

	fun setProgress(progress: Float) {
		this.progress = progress.coerceIn(0f, 1f)
		invalidate()
	}

	fun showFinalBase() {
		showFinalBase = true
		invalidate()
	}

	fun clearBundle() {
		bundle = null
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		val bundle = bundle ?: return
		val current = bundle.currentBase
		val destination = bundle.finalBase
		val width = current.width.toFloat()
		val height = current.height.toFloat()
		if (width <= 0f || height <= 0f) return

		val restore = canvas.save()
		canvas.translate(surfaceLeft, surfaceTop)
		canvas.scale(bundle.renderScaleX, bundle.renderScaleY)
		if (showFinalBase) {
			canvas.drawBitmap(destination, 0f, 0f, bitmapPaint)
			canvas.restoreToCount(restore)
			return
		}
		val clip = canvas.save()
		canvas.clipRect(0f, 0f, width, height)
		if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) {
			drawForward(canvas, current, destination, width, height)
		} else {
			drawBackward(canvas, current, destination, width, height)
		}
		canvas.restoreToCount(clip)
		canvas.restoreToCount(restore)
	}

	private fun drawForward(
		canvas: Canvas,
		current: android.graphics.Bitmap,
		destination: android.graphics.Bitmap,
		width: Float,
		height: Float
	) {
		canvas.drawBitmap(destination, 0f, 0f, bitmapPaint)
		val moving = canvas.save()
		canvas.translate(-width * progress, 0f)
		canvas.drawBitmap(current, 0f, 0f, bitmapPaint)
		canvas.restoreToCount(moving)
		drawMovingEdge(canvas, width * (1f - progress), height, towardLeft = true)
	}

	private fun drawBackward(
		canvas: Canvas,
		current: android.graphics.Bitmap,
		destination: android.graphics.Bitmap,
		width: Float,
		height: Float
	) {
		canvas.drawBitmap(current, 0f, 0f, bitmapPaint)
		val moving = canvas.save()
		canvas.translate(-width + width * progress, 0f)
		canvas.drawBitmap(destination, 0f, 0f, bitmapPaint)
		canvas.restoreToCount(moving)
		drawMovingEdge(canvas, width * progress, height, towardLeft = false)
	}

	private fun drawMovingEdge(canvas: Canvas, edgeX: Float, height: Float, towardLeft: Boolean) {
		val density = resources.displayMetrics.density
		val shadowWidth = max(8f, 12f * density)
		val highlightWidth = max(1f, density)
		shadowPaint.color = Color.argb(58, 18, 14, 10)
		highlightPaint.color = Color.argb(112, 255, 252, 244)
		if (towardLeft) {
			canvas.drawRect(edgeX, 0f, edgeX + shadowWidth, height, shadowPaint)
			canvas.drawRect(edgeX - highlightWidth, 0f, edgeX, height, highlightPaint)
		} else {
			canvas.drawRect(edgeX - shadowWidth, 0f, edgeX, height, shadowPaint)
			canvas.drawRect(edgeX, 0f, edgeX + highlightWidth, height, highlightPaint)
		}
	}
}
