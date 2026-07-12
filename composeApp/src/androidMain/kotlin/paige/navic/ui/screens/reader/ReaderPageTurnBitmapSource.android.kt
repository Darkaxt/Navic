package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.util.core.Logger
import kotlin.math.abs

private const val ReaderPageTurnBitmapSourceTag = "ReaderPageTurnBitmapSource"
private const val PageTurnCaptureSampleColumns = 48
private const val PageTurnCaptureSampleRows = 32
private const val PageTurnCaptureMinimumLuminanceRange = 32
private const val PageTurnCaptureForegroundDistance = 24

internal data class ReaderPageTurnCaptureResult(
	val bitmap: Bitmap,
	val sourceRectInWindow: Rect,
	val geometry: ReaderPageTurnCaptureGeometry,
	val elapsedMs: Long
)

internal class ReaderPageTurnBitmapSource(
	private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
	private var visualStateRequestId = 0L

	val isAvailable: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

	fun capturePage(
		webView: WebView,
		direction: ReaderPageTurnPhysicalDirection,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = capture(webView, onCaptured) { geometry, location ->
		geometry.sourceRectInWindow(
			direction = direction,
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		)
	}

	fun captureSurface(
		webView: WebView,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = capture(webView, onCaptured) { geometry, location ->
		geometry.surfaceRectInWindow(
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		)
	}

	private fun capture(
		webView: WebView,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!isAvailable || !webView.isAttachedToWindow || webView.width <= 0 || webView.height <= 0) {
			onCaptured(null)
			return
		}
		val startedAt = SystemClock.uptimeMillis()
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnCaptureGeometry?.() ?? null)"
		) { encodedGeometry ->
			val geometry = parseGeometry(encodedGeometry)
			val window = webView.context.findActivityOrNull()?.window
			if (geometry == null || window == null || !webView.isAttachedToWindow) {
				onCaptured(null)
				return@evaluateJavascript
			}
			val requestId = ++visualStateRequestId
			webView.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
				override fun onComplete(requestId: Long) {
					if (!webView.isAttachedToWindow) {
						onCaptured(null)
						return
					}
					webView.postOnAnimation {
						captureVisualState(webView, geometry, window, startedAt, onCaptured, resolveRect)
					}
				}
			})
		}
	}

	private fun captureVisualState(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		window: android.view.Window,
		startedAt: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!webView.isAttachedToWindow) {
			onCaptured(null)
			return
		}
			val location = IntArray(2)
			webView.getLocationInWindow(location)
			val pixelRect = resolveRect(geometry, location)
			if (pixelRect == null) {
				onCaptured(null)
				return
			}
			val sourceRect = Rect(pixelRect.left, pixelRect.top, pixelRect.right, pixelRect.bottom)
			val bitmap = runCatching {
				Bitmap.createBitmap(
					readerPageTurnAnimationBitmapDimension(pixelRect.width),
					readerPageTurnAnimationBitmapDimension(pixelRect.height),
					Bitmap.Config.ARGB_8888
				)
			}.getOrElse { error ->
				Logger.w(ReaderPageTurnBitmapSourceTag, "Page-turn bitmap allocation failed", error)
				onCaptured(null)
				return
			}
			try {
				PixelCopy.request(
					window,
					sourceRect,
					bitmap,
					{ result ->
						if (result == PixelCopy.SUCCESS && bitmap.containsRenderableForeground()) {
							val elapsedMs = SystemClock.uptimeMillis() - startedAt
							Logger.i(
								ReaderPageTurnBitmapSourceTag,
								"Page-turn capture success rect=$sourceRect bitmap=${bitmap.width}x${bitmap.height} elapsedMs=$elapsedMs"
							)
							onCaptured(ReaderPageTurnCaptureResult(bitmap, sourceRect, geometry, elapsedMs))
						} else if (result == PixelCopy.SUCCESS) {
							bitmap.recycle()
							Logger.i(
								ReaderPageTurnBitmapSourceTag,
								"Page-turn capture rejected unpainted surface rect=$sourceRect"
							)
							onCaptured(null)
						} else {
							bitmap.recycle()
							Logger.w(ReaderPageTurnBitmapSourceTag, "Page-turn PixelCopy failed result=$result rect=$sourceRect")
							onCaptured(null)
						}
					},
					mainHandler
				)
			} catch (error: Throwable) {
				bitmap.recycle()
				Logger.w(ReaderPageTurnBitmapSourceTag, "Page-turn PixelCopy threw", error)
				onCaptured(null)
			}
	}

	internal fun parseGeometry(encoded: String?): ReaderPageTurnCaptureGeometry? = runCatching {
		val decoded = JSONTokener(encoded.orEmpty()).nextValue()
		val jsonText = when (decoded) {
			is String -> decoded
			is JSONObject -> decoded.toString()
			else -> return null
		}
		val json = JSONObject(jsonText)
		val viewportWidth = json.optDouble("viewportWidth")
		val viewportHeight = json.optDouble("viewportHeight")
		if (!viewportWidth.isFinite() || !viewportHeight.isFinite()) return null
		ReaderPageTurnCaptureGeometry(
			viewportWidth = viewportWidth,
			viewportHeight = viewportHeight,
			mode = if (json.optString("mode") == "spread") ReaderPageTurnLayoutMode.Spread else ReaderPageTurnLayoutMode.Single,
			pages = json.optJSONArray("pages").toPageRects(),
			reverseFaceColorArgb = json.optLong("reverseFaceColorArgb").takeIf { json.has("reverseFaceColorArgb") }
		)
	}.getOrNull()

	private fun JSONArray?.toPageRects(): List<ReaderPageTurnPageRect> = buildList {
		val array = this@toPageRects ?: return@buildList
		for (index in 0 until array.length()) {
			val item = array.optJSONObject(index) ?: continue
			val role = when (item.optString("role")) {
				"left" -> ReaderPageTurnPageRole.Left
				"right" -> ReaderPageTurnPageRole.Right
				else -> ReaderPageTurnPageRole.Full
			}
			add(
				ReaderPageTurnPageRect(
					role = role,
					left = item.optDouble("left"),
					top = item.optDouble("top"),
					width = item.optDouble("width"),
					height = item.optDouble("height")
				)
			)
		}
	}
}

private fun Bitmap.containsRenderableForeground(): Boolean {
	val columns = PageTurnCaptureSampleColumns.coerceAtMost(width)
	val rows = PageTurnCaptureSampleRows.coerceAtMost(height)
	if (columns <= 0 || rows <= 0) return false
	val pixels = ArrayList<Int>(columns * rows)
	for (row in 0 until rows) {
		val y = ((row + 0.5f) * height / rows).toInt().coerceIn(0, height - 1)
		if (y < height * 0.06f || y > height * 0.94f) continue
		for (column in 0 until columns) {
			val xFraction = (column + 0.5f) / columns
			if (xFraction < 0.06f || xFraction > 0.94f || xFraction in 0.47f..0.53f) continue
			val x = ((column + 0.5f) * width / columns).toInt().coerceIn(0, width - 1)
			pixels += getPixel(x, y)
		}
	}
	return readerPageTurnPixelsContainForeground(pixels.toIntArray())
}

internal fun readerPageTurnPixelsContainForeground(pixels: IntArray): Boolean {
	if (pixels.isEmpty()) return false
	val luminance = IntArray(pixels.size) { index ->
		val color = pixels[index]
		val red = color ushr 16 and 0xff
		val green = color ushr 8 and 0xff
		val blue = color and 0xff
		(red * 54 + green * 183 + blue * 19) ushr 8
	}
	val sorted = luminance.sortedArray()
	val baseline = sorted[sorted.size / 2]
	val range = sorted.last() - sorted.first()
	if (range < PageTurnCaptureMinimumLuminanceRange) return false
	val requiredForegroundSamples = maxOf(4, pixels.size / 256)
	return luminance.count { value -> abs(value - baseline) >= PageTurnCaptureForegroundDistance } >=
		requiredForegroundSamples
}

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.findActivityOrNull()
	else -> null
}
