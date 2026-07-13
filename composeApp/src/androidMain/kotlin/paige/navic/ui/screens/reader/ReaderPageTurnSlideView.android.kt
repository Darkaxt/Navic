package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import kotlin.math.max
import kotlin.math.roundToInt

internal class ReaderPageTurnSlideView(context: Context) : View(context) {
	private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val sourceWavePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val destinationWavePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val handoffPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val movingEdgePath = Path()
	private val waveGeometry = ReaderPageTurnWaveGeometry()
	private var transition: ReaderPageSlideTransition? = null
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

	fun setTransition(
		transition: ReaderPageSlideTransition,
		direction: ReaderPageTurnPhysicalDirection,
		surfaceLeft: Int,
		surfaceTop: Int
	) {
		this.transition = transition
		this.direction = direction
		this.surfaceLeft = surfaceLeft.toFloat()
		this.surfaceTop = surfaceTop.toFloat()
		sourceWavePaint.shader = BitmapShader(transition.source.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
		destinationWavePaint.shader = BitmapShader(
			transition.destination.bitmap,
			Shader.TileMode.CLAMP,
			Shader.TileMode.CLAMP
		)
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

	fun clearTransition() {
		transition = null
		sourceWavePaint.shader = null
		destinationWavePaint.shader = null
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		val transition = transition ?: return
		val current = transition.source.bitmap
		val destination = transition.destination.bitmap
		val width = current.width.toFloat()
		val height = current.height.toFloat()
		if (width <= 0f || height <= 0f) return

		val restore = canvas.save()
		canvas.translate(surfaceLeft, surfaceTop)
		canvas.scale(transition.renderScaleX, transition.renderScaleY)
		if (showFinalBase || progress >= 1f) {
			canvas.drawBitmap(destination, 0f, 0f, bitmapPaint)
			canvas.restoreToCount(restore)
			return
		}
		val clip = canvas.save()
		canvas.clipRect(0f, 0f, width, height)
		if (progress <= 0f) {
			canvas.drawBitmap(current, 0f, 0f, bitmapPaint)
		} else if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) {
			drawForward(canvas, transition, current, destination)
		} else {
			drawBackward(canvas, transition, current, destination)
		}
		canvas.restoreToCount(clip)
		canvas.restoreToCount(restore)
	}

	private fun drawForward(
		canvas: Canvas,
		transition: ReaderPageSlideTransition,
		current: Bitmap,
		destination: Bitmap
	) {
		canvas.drawBitmap(destination, 0f, 0f, bitmapPaint)
		val activeLeaf = activeLeafRect(transition) ?: run {
			canvas.drawBitmap(current, 0f, 0f, bitmapPaint)
			return
		}
		if (transition.plan.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) {
			drawBitmapClipped(canvas, current, left = 0f, right = activeLeaf.left.toFloat())
		}
		drawActiveLeaf(
			canvas = canvas,
			bitmap = current,
			paint = sourceWavePaint,
			leafRect = activeLeaf,
			fixedEdge = ReaderPageTurnFixedEdge.Left,
			openness = 1f - progress
		)
		drawMovingEdge(canvas)
		if (transition.plan.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) {
			drawInactiveHandoff(canvas, destination, left = 0f, right = activeLeaf.left.toFloat())
		}
	}

	private fun drawBackward(
		canvas: Canvas,
		transition: ReaderPageSlideTransition,
		current: Bitmap,
		destination: Bitmap
	) {
		canvas.drawBitmap(current, 0f, 0f, bitmapPaint)
		val activeLeaf = activeLeafRect(transition) ?: return
		drawActiveLeaf(
			canvas = canvas,
			bitmap = destination,
			paint = destinationWavePaint,
			leafRect = activeLeaf,
			fixedEdge = if (transition.plan.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) {
				ReaderPageTurnFixedEdge.Right
			} else {
				ReaderPageTurnFixedEdge.Left
			},
			openness = progress
		)
		drawMovingEdge(canvas)
		if (transition.plan.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) {
			drawInactiveHandoff(
				canvas,
				destination,
				left = activeLeaf.right.toFloat(),
				right = current.width.toFloat()
			)
		}
	}

	private fun activeLeafRect(transition: ReaderPageSlideTransition): ReaderPageTurnPixelRect? =
		if (transition.plan.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) {
			if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) {
				transition.leafGeometry.rightLeafRect
			} else {
				transition.leafGeometry.leftLeafRect
			}
		} else {
			transition.leafGeometry.fullLeafRect
		}

	private fun drawActiveLeaf(
		canvas: Canvas,
		bitmap: Bitmap,
		paint: Paint,
		leafRect: ReaderPageTurnPixelRect,
		fixedEdge: ReaderPageTurnFixedEdge,
		openness: Float
	) {
		if (bitmap.isRecycled) return
		waveGeometry.update(leafRect, fixedEdge, openness)
		canvas.drawVertices(
			Canvas.VertexMode.TRIANGLES,
			waveGeometry.vertices.size,
			waveGeometry.vertices,
			0,
			waveGeometry.textureCoordinates,
			0,
			null,
			0,
			waveGeometry.indices,
			0,
			waveGeometry.indices.size,
			paint
		)
	}

	private fun drawBitmapClipped(canvas: Canvas, bitmap: Bitmap, left: Float, right: Float, alpha: Float = 1f) {
		if (right <= left || alpha <= 0f) return
		val restore = canvas.save()
		canvas.clipRect(left, 0f, right, bitmap.height.toFloat())
		bitmapPaint.alpha = (255f * alpha.coerceIn(0f, 1f)).roundToInt()
		canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
		bitmapPaint.alpha = 255
		canvas.restoreToCount(restore)
	}

	private fun drawInactiveHandoff(canvas: Canvas, bitmap: Bitmap, left: Float, right: Float) {
		val linear = ((progress - InactiveHandoffStart) / (1f - InactiveHandoffStart)).coerceIn(0f, 1f)
		val eased = linear * linear * (3f - 2f * linear)
		drawBitmapClipped(canvas, bitmap, left, right, eased)
	}

	private fun drawMovingEdge(canvas: Canvas) {
		val density = resources.displayMetrics.density
		val shadowWidth = max(5f, 8f * density)
		val highlightWidth = max(1f, 0.8f * density)
		shadowPaint.color = Color.argb(58, 18, 14, 10)
		highlightPaint.color = Color.argb(112, 255, 252, 244)
		shadowPaint.style = Paint.Style.STROKE
		shadowPaint.strokeWidth = shadowWidth
		highlightPaint.style = Paint.Style.STROKE
		highlightPaint.strokeWidth = highlightWidth
		movingEdgePath.rewind()
		for (row in 0..waveGeometry.rows) {
			val x = waveGeometry.vertexX(waveGeometry.columns, row)
			val y = waveGeometry.vertexY(waveGeometry.columns, row)
			if (row == 0) movingEdgePath.moveTo(x, y) else movingEdgePath.lineTo(x, y)
		}
		canvas.drawPath(movingEdgePath, shadowPaint)
		canvas.drawPath(movingEdgePath, highlightPaint)
	}

	private companion object {
		const val InactiveHandoffStart = 0.78f
	}
}
