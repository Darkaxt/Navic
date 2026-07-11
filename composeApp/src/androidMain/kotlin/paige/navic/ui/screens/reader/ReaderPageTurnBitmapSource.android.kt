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

private const val ReaderPageTurnBitmapSourceTag = "ReaderPageTurnBitmapSource"

internal data class ReaderPageTurnCaptureResult(
	val bitmap: Bitmap,
	val sourceRectInWindow: Rect,
	val geometry: ReaderPageTurnCaptureGeometry,
	val elapsedMs: Long
)

internal class ReaderPageTurnBitmapSource(
	private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
	val isAvailable: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

	fun capturePage(
		webView: WebView,
		direction: ReaderPageTurnPhysicalDirection,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
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
			val location = IntArray(2)
			webView.getLocationInWindow(location)
			val pixelRect = geometry.sourceRectInWindow(
				direction = direction,
				webViewWindowLeft = location[0],
				webViewWindowTop = location[1],
				webViewWidth = webView.width,
				webViewHeight = webView.height
			)
			if (pixelRect == null) {
				onCaptured(null)
				return@evaluateJavascript
			}
			val sourceRect = Rect(pixelRect.left, pixelRect.top, pixelRect.right, pixelRect.bottom)
			val bitmap = runCatching {
				Bitmap.createBitmap(pixelRect.width, pixelRect.height, Bitmap.Config.ARGB_8888)
			}.getOrElse { error ->
				Logger.w(ReaderPageTurnBitmapSourceTag, "Page-turn bitmap allocation failed", error)
				onCaptured(null)
				return@evaluateJavascript
			}
			try {
				PixelCopy.request(
					window,
					sourceRect,
					bitmap,
					{ result ->
						if (result == PixelCopy.SUCCESS) {
							val elapsedMs = SystemClock.uptimeMillis() - startedAt
							Logger.i(
								ReaderPageTurnBitmapSourceTag,
								"Page-turn capture success rect=$sourceRect bitmap=${bitmap.width}x${bitmap.height} elapsedMs=$elapsedMs"
							)
							onCaptured(ReaderPageTurnCaptureResult(bitmap, sourceRect, geometry, elapsedMs))
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
	}

	private fun parseGeometry(encoded: String?): ReaderPageTurnCaptureGeometry? = runCatching {
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

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.findActivityOrNull()
	else -> null
}

