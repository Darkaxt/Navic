package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/**
 * Software-draws [sourceInView] of [view] into [target], resampling to the target size.
 *
 * #54: a WebView drawn into a down-scaled software canvas rasterizes its text at that scale,
 * so the page captured at open did not match the prepared page rasters and failed live
 * visual validation when the reader turned back onto it. The view is therefore always drawn
 * at native (1:1) scale; only the finished bitmap is filter-resampled.
 */
internal fun drawViewAtNativeScale(
	view: View,
	sourceInView: Rect,
	target: Bitmap,
	backgroundColor: Int?
): Boolean {
	val width = sourceInView.width()
	val height = sourceInView.height()
	if (width <= 0 || height <= 0) return false
	if (target.width == width && target.height == height) {
		drawViewRegionAtNativeScale(view, sourceInView, target, backgroundColor)
		return true
	}
	val native = runCatching {
		Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
	}.getOrNull() ?: return false
	try {
		drawViewRegionAtNativeScale(view, sourceInView, native, backgroundColor)
		target.eraseColor(backgroundColor ?: Color.TRANSPARENT)
		Canvas(target).drawBitmap(
			native,
			null,
			Rect(0, 0, target.width, target.height),
			Paint(Paint.FILTER_BITMAP_FLAG)
		)
	} finally {
		native.recycle()
	}
	return true
}

private fun drawViewRegionAtNativeScale(
	view: View,
	sourceInView: Rect,
	bitmap: Bitmap,
	backgroundColor: Int?
) {
	bitmap.eraseColor(backgroundColor ?: Color.TRANSPARENT)
	val canvas = Canvas(bitmap)
	canvas.translate(-sourceInView.left.toFloat(), -sourceInView.top.toFloat())
	view.draw(canvas)
}
