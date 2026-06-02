package paige.navic.androidApp.widgets.turntable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt
import paige.navic.domain.models.NowPlayingVinylGrooveEndRadiusFraction
import paige.navic.domain.models.NowPlayingVinylGrooveStartRadiusFraction
import paige.navic.domain.models.NowPlayingVinylLabelRadiusFraction
import paige.navic.domain.models.NowPlayingVinylSpindleRadiusFraction

internal fun createTurnTableVinylArtworkBitmap(cover: Bitmap): Bitmap {
	val size = min(cover.width, cover.height).coerceAtLeast(1)
	val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
	val canvas = Canvas(output)
	val radius = size / 2f
	val center = radius
	val circle = Path().apply {
		addCircle(center, center, radius, Path.Direction.CW)
	}
	val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	val sourceSize = min(cover.width, cover.height)
	val sourceLeft = (cover.width - sourceSize) / 2
	val sourceTop = (cover.height - sourceSize) / 2
	val source = Rect(sourceLeft, sourceTop, sourceLeft + sourceSize, sourceTop + sourceSize)
	val target = RectF(0f, 0f, size.toFloat(), size.toFloat())

	canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.rgb(18, 18, 22)
	})
	canvas.save()
	canvas.clipPath(circle)
	canvas.drawBitmap(cover, source, target, coverPaint)
	canvas.restore()

	val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = vinylColor(alpha = 0.18f, red = 0, green = 0, blue = 0)
	}
	canvas.drawCircle(center, center, radius * 0.985f, overlayPaint)

	val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	strokePaint.strokeWidth = (radius * 0.018f).coerceAtLeast(2f)
	strokePaint.color = vinylColor(alpha = 0.22f, red = 255, green = 255, blue = 255)
	canvas.drawCircle(center, center, radius * 0.99f, strokePaint)

	val startRadius = radius * NowPlayingVinylGrooveStartRadiusFraction
	val endRadius = radius * NowPlayingVinylGrooveEndRadiusFraction
	val grooveCount = 48
	val baseStrokeWidth = (radius * 0.0028f).coerceAtLeast(0.65f)
	val accentStrokeWidth = (radius * 0.004f).coerceAtLeast(0.9f)
	repeat(grooveCount) { index ->
		val progress = index / (grooveCount - 1).toFloat()
		val grooveRadius = startRadius + (endRadius - startRadius) * progress
		val accent = index % 7 == 0
		strokePaint.strokeWidth = if (accent) accentStrokeWidth else baseStrokeWidth
		strokePaint.color = vinylColor(
			alpha = if (accent) 0.28f else 0.16f,
			red = 255,
			green = 255,
			blue = 255
		)
		canvas.drawCircle(center, center, grooveRadius, strokePaint)
	}

	val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = vinylColor(alpha = 0.22f, red = 255, green = 255, blue = 255)
	}
	canvas.drawCircle(center, center, radius * NowPlayingVinylLabelRadiusFraction, fillPaint)

	strokePaint.strokeWidth = (radius * 0.008f).coerceAtLeast(1f)
	strokePaint.color = vinylColor(alpha = 0.32f, red = 255, green = 255, blue = 255)
	canvas.drawCircle(center, center, radius * NowPlayingVinylLabelRadiusFraction, strokePaint)

	fillPaint.color = vinylColor(alpha = 0.88f, red = 28, green = 28, blue = 32)
	canvas.drawCircle(center, center, radius * NowPlayingVinylSpindleRadiusFraction, fillPaint)

	strokePaint.strokeWidth = (radius * 0.004f).coerceAtLeast(1f)
	strokePaint.color = vinylColor(alpha = 0.34f, red = 255, green = 255, blue = 255)
	canvas.drawCircle(center, center, radius * NowPlayingVinylSpindleRadiusFraction, strokePaint)

	return output
}

private fun vinylColor(alpha: Float, red: Int, green: Int, blue: Int): Int =
	Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), red, green, blue)
