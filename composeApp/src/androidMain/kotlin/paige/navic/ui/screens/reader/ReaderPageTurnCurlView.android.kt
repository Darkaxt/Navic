package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import paige.navic.reader.ReaderPageTurnEdgeFoldGeometry
import paige.navic.reader.ReaderPageTurnPoint
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

internal class ReaderPageTurnCurlView(context: Context) : View(context) {
	private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val reversePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val underlayPaint = Paint()
	private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
	}
	private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
	}
	private val reversePath = Path()
	private val foldBoundaryPath = Path()
	private val foldedRegionPath = Path()
	private var bitmap: Bitmap? = null
	private var direction: ReaderPageTurnPhysicalDirection = ReaderPageTurnPhysicalDirection.TowardLeft
	private var progress: Float = 0f
	private var edgeOriginY: Float = 0f
	private var pointerY: Float = 0f
	private var reverseFaceColor: Int = Color.rgb(234, 217, 174)
	private var pageLeft = 0f
	private var pageTop = 0f
	private var destinationSettled = false
	private val vertices = FloatArray((MeshColumns + 1) * (MeshRows + 1) * 2)
	private var cachedGeometry: ReaderPageTurnEdgeFoldGeometry? = null
	private var cachedWidth = 0f
	private var cachedHeight = 0f

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)
	}

	fun setPage(
		bitmap: Bitmap,
		direction: ReaderPageTurnPhysicalDirection,
		reverseFaceColor: Int,
		pageLeft: Int,
		pageTop: Int
	) {
		this.bitmap = bitmap
		this.direction = direction
		this.reverseFaceColor = reverseFaceColor
		this.pageLeft = pageLeft.toFloat()
		this.pageTop = pageTop.toFloat()
		this.destinationSettled = false
		invalidateGeometry()
		invalidate()
	}

	fun setDestinationSettled() {
		destinationSettled = true
		invalidate()
	}

	fun setGestureY(edgeOriginY: Float, pointerY: Float) {
		this.edgeOriginY = edgeOriginY
		this.pointerY = pointerY
		invalidateGeometry()
		invalidate()
	}

	fun setProgress(progress: Float) {
		this.progress = progress.coerceIn(0f, MaxTurnProgress)
		invalidateGeometry()
		invalidate()
	}

	fun releaseBitmap(): Bitmap? = bitmap.also { bitmap = null }

	override fun onDraw(canvas: Canvas) {
		val source = bitmap ?: return
		val pageWidth = source.width.toFloat()
		val pageHeight = source.height.toFloat()
		if (pageWidth <= 0f || pageHeight <= 0f) return
		if (!destinationSettled) {
			underlayPaint.color = reverseFaceColor
			canvas.drawRect(pageLeft, pageTop, pageLeft + pageWidth, pageTop + pageHeight, underlayPaint)
		}
		val restoreCount = canvas.save()
		canvas.translate(pageLeft, pageTop)
		if (progress <= 0.001f) {
			canvas.drawBitmap(source, null, android.graphics.RectF(0f, 0f, pageWidth, pageHeight), bitmapPaint)
			canvas.restoreToCount(restoreCount)
			return
		}

		val geometry = geometry(pageWidth, pageHeight)
		buildMesh(pageWidth, pageHeight, geometry)
		canvas.drawBitmapMesh(source, MeshColumns, MeshRows, vertices, 0, null, 0, bitmapPaint)
		buildFoldPaths(geometry)
		drawReverseFace(canvas, pageWidth, pageHeight, geometry)
		drawCrease(canvas, pageWidth)
		drawEdgeHighlight(canvas)
		canvas.restoreToCount(restoreCount)
	}

	private fun geometry(pageWidth: Float, pageHeight: Float): ReaderPageTurnEdgeFoldGeometry {
		cachedGeometry?.takeIf { cachedWidth == pageWidth && cachedHeight == pageHeight }?.let { return it }
		return ReaderPageTurnEdgeFoldGeometry(
			width = pageWidth,
			height = pageHeight,
			progress = progress,
			direction = direction,
			edgeOriginY = edgeOriginY.coerceIn(0f, pageHeight),
			pointerY = pointerY.coerceIn(0f, pageHeight)
		).also {
			cachedGeometry = it
			cachedWidth = pageWidth
			cachedHeight = pageHeight
		}
	}

	private fun buildMesh(
		pageWidth: Float,
		pageHeight: Float,
		geometry: ReaderPageTurnEdgeFoldGeometry
	) {
		for (row in 0..MeshRows) {
			val y = pageHeight * row / MeshRows
			for (column in 0..MeshColumns) {
				val baseX = pageWidth * column / MeshColumns
				val index = (row * (MeshColumns + 1) + column) * 2
				geometry.mapInto(baseX, y, vertices, index)
			}
		}
	}

	private fun buildFoldPaths(geometry: ReaderPageTurnEdgeFoldGeometry) {
		foldBoundaryPath.reset()
		foldedRegionPath.reset()
		reversePath.reset()
		geometry.visibleCreaseSegment()?.let { segment ->
			foldBoundaryPath.moveTo(segment.first.x, segment.first.y)
			foldBoundaryPath.lineTo(segment.second.x, segment.second.y)
		}
		geometry.foldedRegionOutline().forEachIndexed { index, point ->
			if (index == 0) {
				foldedRegionPath.moveTo(point.x, point.y)
				reversePath.moveTo(point.x, point.y)
			} else {
				foldedRegionPath.lineTo(point.x, point.y)
				reversePath.lineTo(point.x, point.y)
			}
		}
		foldedRegionPath.close()
		reversePath.close()
	}

	private fun drawReverseFace(
		canvas: Canvas,
		pageWidth: Float,
		pageHeight: Float,
		geometry: ReaderPageTurnEdgeFoldGeometry
	) {
		val centerY = edgeOriginY.coerceIn(0f, pageHeight)
		val creaseX = geometry.foldBoundaryX(centerY)
		val freeEdgeX = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) pageWidth else 0f
		val outerX = geometry.map(ReaderPageTurnPoint(freeEdgeX, centerY)).x
		if (kotlin.math.abs(outerX - creaseX) <= 0.5f) return
		reversePaint.shader = LinearGradient(
			creaseX,
			centerY,
			outerX,
			centerY,
			intArrayOf(darken(reverseFaceColor, 0.72f), reverseFaceColor, lighten(reverseFaceColor, 0.12f)),
			floatArrayOf(0f, 0.66f, 1f),
			Shader.TileMode.CLAMP
		)
		canvas.drawPath(reversePath, reversePaint)
		reversePaint.shader = null
	}

	private fun drawCrease(canvas: Canvas, pageWidth: Float) {
		val envelope = sin(progress / MaxTurnProgress * PI).toFloat().coerceAtLeast(0f)
		if (envelope <= 0.001f) return
		shadowPaint.color = Color.argb((64 * envelope).toInt(), 28, 18, 8)
		shadowPaint.strokeWidth = max(8f, pageWidth * 0.032f * envelope)
		canvas.drawPath(foldBoundaryPath, shadowPaint)
		shadowPaint.color = Color.argb((82 * envelope).toInt(), 46, 29, 14)
		shadowPaint.strokeWidth = max(2f, pageWidth * 0.009f * envelope)
		canvas.drawPath(foldBoundaryPath, shadowPaint)
	}

	private fun drawEdgeHighlight(canvas: Canvas) {
		val envelope = sin(progress / MaxTurnProgress * PI).toFloat().coerceAtLeast(0f)
		if (envelope <= 0.001f) return
		edgePaint.color = Color.argb((118 * envelope).toInt(), 255, 252, 240)
		edgePaint.strokeWidth = max(1.5f, resources.displayMetrics.density)
		canvas.drawPath(foldedRegionPath, edgePaint)
	}

	private fun invalidateGeometry() {
		cachedGeometry = null
		cachedWidth = 0f
		cachedHeight = 0f
	}

	private fun darken(color: Int, factor: Float): Int = Color.rgb(
		(Color.red(color) * factor).toInt().coerceIn(0, 255),
		(Color.green(color) * factor).toInt().coerceIn(0, 255),
		(Color.blue(color) * factor).toInt().coerceIn(0, 255)
	)

	private fun lighten(color: Int, amount: Float): Int = Color.rgb(
		(Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255),
		(Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255),
		(Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
	)

	private companion object {
		const val MaxTurnProgress = 2f
		const val MeshColumns = 28
		const val MeshRows = 24
	}
}
