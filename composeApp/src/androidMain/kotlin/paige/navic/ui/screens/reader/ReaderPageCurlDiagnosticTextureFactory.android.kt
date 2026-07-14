package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

private data class ReaderPageCurlDiagnosticBitmapPair(
	val source: Bitmap,
	val destination: Bitmap
)

internal object ReaderPageCurlDiagnosticTextureFactory {
	private val pairs = mutableMapOf<String, WeakReference<ReaderPageCurlDiagnosticBitmapPair>>()

	@Synchronized
	fun from(base: ReaderPageCurlTextureSet): ReaderPageCurlTextureSet {
		val key = "${base.bitmapWidth}x${base.bitmapHeight}"
		val pair = pairs[key]?.get() ?: ReaderPageCurlDiagnosticBitmapPair(
			source = createCheckerBitmap(
				width = base.bitmapWidth,
				height = base.bitmapHeight,
				leftLabel = "SOURCE LEFT",
				rightLabel = "SOURCE RIGHT",
				firstColor = Color.rgb(26, 72, 102),
				secondColor = Color.rgb(18, 49, 69)
			),
			destination = createCheckerBitmap(
				width = base.bitmapWidth,
				height = base.bitmapHeight,
				leftLabel = "DESTINATION LEFT",
				rightLabel = "DESTINATION RIGHT",
				firstColor = Color.rgb(103, 63, 38),
				secondColor = Color.rgb(70, 42, 26)
			)
		).also { created -> pairs[key] = WeakReference(created) }
		return base.copy(
			identity = "${base.identity}:diagnostic:$key",
			sourceBitmap = pair.source,
			destinationBitmap = pair.destination
		)
	}

	private fun createCheckerBitmap(
		width: Int,
		height: Int,
		leftLabel: String,
		rightLabel: String,
		firstColor: Int,
		secondColor: Int
	): Bitmap {
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		val cell = max(16, min(width, height) / 8)
		var row = 0
		var top = 0
		while (top < height) {
			var column = 0
			var left = 0
			while (left < width) {
				paint.color = if ((row + column) % 2 == 0) firstColor else secondColor
				canvas.drawRect(
					left.toFloat(),
					top.toFloat(),
					min(left + cell, width).toFloat(),
					min(top + cell, height).toFloat(),
					paint
				)
				left += cell
				column += 1
			}
			top += cell
			row += 1
		}

		paint.color = Color.WHITE
		paint.textAlign = Paint.Align.CENTER
		paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
		paint.textSize = max(18f, min(width / 22f, height / 9f))
		val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
		canvas.drawText(leftLabel, width * 0.25f, baseline, paint)
		canvas.drawText(rightLabel, width * 0.75f, baseline, paint)
		return bitmap
	}
}
