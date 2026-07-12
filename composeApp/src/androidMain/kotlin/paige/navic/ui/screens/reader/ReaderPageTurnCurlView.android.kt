package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.os.Build
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
	private var bundle: ReaderPageTurnBitmapBundle? = null
	private var direction: ReaderPageTurnPhysicalDirection = ReaderPageTurnPhysicalDirection.TowardLeft
	private var progress: Float = 0f
	private var edgeOriginY: Float = 0f
	private var pointerY: Float = 0f
	private var reverseFaceColor: Int = Color.rgb(234, 217, 174)
	private var surfaceLeft = 0f
	private var surfaceTop = 0f
	private var showFinalBase = false
	private val vertices = FloatArray((MeshColumns + 1) * (MeshRows + 1) * 2)
	private var cachedGeometry: ReaderPageTurnEdgeFoldGeometry? = null
	private var cachedWidth = 0f
	private var cachedHeight = 0f

	init {
		isClickable = false
		isFocusable = false
		setLayerType(LAYER_TYPE_HARDWARE, null)
	}

	fun setBundle(
		bundle: ReaderPageTurnBitmapBundle,
		direction: ReaderPageTurnPhysicalDirection,
		reverseFaceColor: Int,
		surfaceLeft: Int,
		surfaceTop: Int
	) {
		this.bundle = bundle
		this.direction = direction
		this.reverseFaceColor = reverseFaceColor
		this.surfaceLeft = surfaceLeft.toFloat()
		this.surfaceTop = surfaceTop.toFloat()
		this.showFinalBase = false
		invalidateGeometry()
		invalidate()
	}

	fun showFinalBase() {
		showFinalBase = true
		invalidateGeometry()
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

	fun clearBundle() {
		bundle = null
		invalidateGeometry()
	}

	override fun onDraw(canvas: Canvas) {
		val bundle = bundle ?: return
		val currentBase = bundle.currentBase
		val underneath = bundle.underneath
		val source = bundle.turningFront
		val reverse = bundle.turningReverse
		val finalBase = bundle.finalBase
		val pageRect = bundle.turningFrontRectInSurface
		val underneathRect = bundle.underneathRectInSurface
		val pageWidth = source.width.toFloat()
		val pageHeight = source.height.toFloat()
		if (pageWidth <= 0f || pageHeight <= 0f) return
		val restoreCount = canvas.save()
		canvas.translate(surfaceLeft, surfaceTop)
		if (showFinalBase) {
			canvas.drawBitmap(finalBase, 0f, 0f, bitmapPaint)
			canvas.restoreToCount(restoreCount)
			return
		}
		when (bundle.plan.kind) {
			ReaderPageTurnTransitionKind.PortraitSlide -> {
				drawPortraitSlide(canvas, bundle)
				canvas.restoreToCount(restoreCount)
				return
			}
			ReaderPageTurnTransitionKind.PortraitLeaf,
			ReaderPageTurnTransitionKind.LandscapeLeaf -> Unit
		}
		canvas.drawBitmap(currentBase, 0f, 0f, bitmapPaint)
		if (underneath != null && underneathRect != null) {
			canvas.drawBitmap(underneath, null, android.graphics.RectF(underneathRect), bitmapPaint)
		}
		canvas.translate(pageRect.left.toFloat(), pageRect.top.toFloat())
		if (progress <= 0.001f) {
			canvas.restoreToCount(restoreCount)
			return
		}

		val geometry = geometry(pageWidth, pageHeight)
		buildMesh(pageWidth, pageHeight, geometry)
		buildFoldPaths(geometry)
		drawFrontFace(canvas, source)
		drawReverseFace(canvas, reverse, pageWidth, pageHeight, geometry)
		drawCrease(canvas, pageWidth)
		drawEdgeHighlight(canvas)
		canvas.restoreToCount(restoreCount)
	}

	private fun drawPortraitSlide(canvas: Canvas, bundle: ReaderPageTurnBitmapBundle) {
		val current = bundle.currentBase
		val target = bundle.finalBase
		val width = current.width.toFloat()
		val height = current.height.toFloat()
		if (width <= 0f || height <= 0f) return
		val fraction = (progress / MaxTurnProgress).coerceIn(0f, 1f)
		val towardNext = bundle.plan.logicalDirection == ReaderPageTurnLogicalDirection.Next
		val currentOffset = if (towardNext) -width * fraction else width * fraction
		val targetOffset = if (towardNext) width + currentOffset else -width + currentOffset
		val clip = canvas.save()
		canvas.clipRect(0f, 0f, width, height)
		val currentRestore = canvas.save()
		canvas.translate(currentOffset, 0f)
		canvas.drawBitmap(current, 0f, 0f, bitmapPaint)
		canvas.restoreToCount(currentRestore)
		val targetRestore = canvas.save()
		canvas.translate(targetOffset, 0f)
		canvas.drawBitmap(target, 0f, 0f, bitmapPaint)
		canvas.restoreToCount(targetRestore)
		canvas.restoreToCount(clip)
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

	private fun drawFrontFace(canvas: Canvas, source: Bitmap) {
		val frontRestore = canvas.save()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			canvas.clipOutPath(foldedRegionPath)
		} else {
			@Suppress("DEPRECATION")
			canvas.clipPath(foldedRegionPath, Region.Op.DIFFERENCE)
		}
		canvas.drawBitmapMesh(source, MeshColumns, MeshRows, vertices, 0, null, 0, bitmapPaint)
		canvas.restoreToCount(frontRestore)
	}

	private fun drawReverseFace(
		canvas: Canvas,
		reverse: Bitmap?,
		pageWidth: Float,
		pageHeight: Float,
		geometry: ReaderPageTurnEdgeFoldGeometry
	) {
		val centerY = edgeOriginY.coerceIn(0f, pageHeight)
		val creaseX = geometry.foldBoundaryX(centerY)
		val freeEdgeX = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) pageWidth else 0f
		val outerX = geometry.map(ReaderPageTurnPoint(freeEdgeX, centerY)).x
		if (kotlin.math.abs(outerX - creaseX) <= 0.5f) return
		val reverseRestore = canvas.save()
		canvas.clipPath(reversePath)
		if (reverse != null) {
			canvas.drawBitmapMesh(reverse, MeshColumns, MeshRows, vertices, 0, null, 0, bitmapPaint)
		} else {
			reversePaint.color = reverseFaceColor
			canvas.drawPath(reversePath, reversePaint)
		}
		reversePaint.shader = LinearGradient(
			creaseX,
			centerY,
			outerX,
			centerY,
			intArrayOf(
				Color.argb(82, 28, 18, 8),
				Color.TRANSPARENT,
				Color.argb(34, 255, 252, 240)
			),
			floatArrayOf(0f, 0.66f, 1f),
			Shader.TileMode.CLAMP
		)
		canvas.drawPath(reversePath, reversePaint)
		reversePaint.shader = null
		canvas.restoreToCount(reverseRestore)
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
