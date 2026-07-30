package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.util.core.Logger
import kotlin.coroutines.resume
import kotlin.math.abs

private const val ReaderPageTurnBitmapSourceTag = "ReaderPageTurnBitmapSource"
private const val PageTurnCaptureSampleColumns = 48
private const val PageTurnCaptureSampleRows = 32
private const val PageTurnCapturePrimarySamplePhase = 0.5f
private const val PageTurnCaptureShiftedSamplePhase = 0f
private const val PageTurnCaptureMinimumLuminanceRange = 32
private const val PageTurnCaptureForegroundDistance = 24
private const val PageTurnCaptureMinimumForegroundSamples = 3
private const val PageTurnCaptureForegroundSampleDivisor = 384

internal data class ReaderPageTurnCaptureResult(
	val bitmap: Bitmap,
	val sourceRectInWindow: Rect,
	val geometry: ReaderPageTurnCaptureGeometry,
	val elapsedMs: Long
)

internal class ReaderPageTurnBitmapSource(
	private var bitmapQuality: ReaderPageBitmapQuality = ReaderPageBitmapQuality.Balanced
) {
	private var visualStateRequestId = 0L

	val isAvailable: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

	fun updateBitmapQuality(quality: ReaderPageBitmapQuality) {
		bitmapQuality = quality
	}

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

	fun captureSurface(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = captureResolvedGeometry(webView, geometry, onCaptured) { resolved, location ->
		resolved.surfaceRectInWindow(
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		)
	}

	suspend fun captureSurfaceAwait(webView: WebView): ReaderPageTurnCaptureResult? =
		suspendCancellableCoroutine { continuation ->
			captureSurface(webView) { result ->
				if (continuation.isActive) {
					continuation.resume(result)
				} else {
					result?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				}
			}
		}

	private fun canCapture(webView: WebView): Boolean =
		isAvailable &&
			webView.isAttachedToWindow &&
			webView.width > 0 &&
			webView.height > 0

	private fun capture(
		webView: WebView,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!canCapture(webView)) {
			onCaptured(null)
			return
		}
		val startedAt = SystemClock.uptimeMillis()
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnCaptureGeometry?.() ?? null)"
		) { encodedGeometry ->
			val geometry = parseGeometry(encodedGeometry)
			if (geometry == null) {
				Logger.i(
					ReaderPageTurnBitmapSourceTag,
					"Page-turn capture unavailable reason=geometry-unavailable"
				)
				onCaptured(null)
				return@evaluateJavascript
			}
			captureResolvedGeometry(webView, geometry, startedAt, onCaptured, resolveRect)
		}
	}

	private fun captureResolvedGeometry(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!canCapture(webView)) {
			onCaptured(null)
			return
		}
		captureResolvedGeometry(
			webView = webView,
			geometry = geometry,
			startedAt = SystemClock.uptimeMillis(),
			onCaptured = onCaptured,
			resolveRect = resolveRect
		)
	}

	private fun captureResolvedGeometry(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		startedAt: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!webView.isAttachedToWindow) {
			onCaptured(null)
			return
		}
		val requestId = ++visualStateRequestId
		webView.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
			override fun onComplete(requestId: Long) {
				if (!webView.isAttachedToWindow) {
					onCaptured(null)
					return
				}
				webView.postOnAnimation {
					captureVisualState(webView, geometry, startedAt, onCaptured, resolveRect)
				}
			}
		})
	}

	private fun captureVisualState(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		startedAt: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?,
		previousSparseSignature: Int? = null
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
				readerPageTurnAnimationBitmapDimension(pixelRect.width, bitmapQuality),
				readerPageTurnAnimationBitmapDimension(pixelRect.height, bitmapQuality),
				Bitmap.Config.ARGB_8888
			)
		}.getOrElse { error ->
			Logger.w(ReaderPageTurnBitmapSourceTag, "Page-turn bitmap allocation failed", error)
			onCaptured(null)
			return
		}
		val backgroundColor = readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb)
		bitmap.eraseColor(backgroundColor)
		val drawn = drawWebViewIntoBitmap(
			webView,
			location,
			sourceRect,
			bitmap,
			backgroundColor
		)
		val foreground = if (drawn) bitmap.analyzeRenderableForeground() else null
		val settledSparse = previousSparseSignature != null &&
			foreground?.sparseSignature == previousSparseSignature
		if (foreground?.renderable == true || settledSparse) {
			bitmap.setHasAlpha(false)
			bitmap.setPremultiplied(true)
			val elapsedMs = SystemClock.uptimeMillis() - startedAt
			Logger.i(
				ReaderPageTurnBitmapSourceTag,
				"Page-turn capture success method=webview-draw rect=$sourceRect " +
					"bitmap=${bitmap.width}x${bitmap.height} " +
					"settledSparse=$settledSparse elapsedMs=$elapsedMs"
			)
			onCaptured(ReaderPageTurnCaptureResult(bitmap, sourceRect, geometry, elapsedMs))
			return
		}

		bitmap.recycle()
		if (foreground?.sparseSignature != null && previousSparseSignature == null) {
			Logger.i(
				ReaderPageTurnBitmapSourceTag,
				"Page-turn capture awaiting stable sparse surface rect=$sourceRect " +
					"samples=${foreground.sampleCount} " +
					"range=${foreground.luminanceRange} " +
					"distant=${foreground.distantSampleCount} " +
					"required=${foreground.requiredDistantSampleCount}"
			)
			webView.postOnAnimation {
				captureVisualState(
					webView = webView,
					geometry = geometry,
					startedAt = startedAt,
					onCaptured = onCaptured,
					resolveRect = resolveRect,
					previousSparseSignature = foreground.sparseSignature
				)
			}
			return
		}

		Logger.i(
			ReaderPageTurnBitmapSourceTag,
			"Page-turn capture rejected unpainted surface rect=$sourceRect " +
				"samples=${foreground?.sampleCount ?: 0} " +
				"range=${foreground?.luminanceRange ?: 0} " +
				"distant=${foreground?.distantSampleCount ?: 0} " +
				"required=${foreground?.requiredDistantSampleCount ?: 0}"
		)
		onCaptured(null)
	}

	internal fun parseGeometry(encoded: String?): ReaderPageTurnCaptureGeometry? =
		readerPageTurnCaptureGeometry(encoded)
}

internal fun readerPageTurnCaptureGeometry(
	encoded: String?
): ReaderPageTurnCaptureGeometry? = runCatching {
	val raw = encoded.orEmpty().trim()
	val firstPass = Json.parseToJsonElement(raw)
	val json = if (raw.startsWith('"')) {
		Json.parseToJsonElement(firstPass.jsonPrimitive.contentOrNull.orEmpty()).jsonObject
	} else {
		firstPass.jsonObject
	}
	val viewportWidth = json["viewportWidth"]?.jsonPrimitive?.doubleOrNull ?: return null
	val viewportHeight = json["viewportHeight"]?.jsonPrimitive?.doubleOrNull ?: return null
	if (!viewportWidth.isFinite() || !viewportHeight.isFinite()) return null
	val pages = json["pages"]?.jsonArray.orEmpty().mapNotNull { element ->
		val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
		val left = item["left"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		val top = item["top"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		val width = item["width"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		val height = item["height"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		ReaderPageTurnPageRect(
			role = when (item["role"]?.jsonPrimitive?.contentOrNull) {
				"left" -> ReaderPageTurnPageRole.Left
				"right" -> ReaderPageTurnPageRole.Right
				else -> ReaderPageTurnPageRole.Full
			},
			left = left,
			top = top,
			width = width,
			height = height
		)
	}
	ReaderPageTurnCaptureGeometry(
		viewportWidth = viewportWidth,
		viewportHeight = viewportHeight,
		mode = if (json["mode"]?.jsonPrimitive?.contentOrNull == "spread") {
			ReaderPageTurnLayoutMode.Spread
		} else {
			ReaderPageTurnLayoutMode.Single
		},
		pages = pages,
		reverseFaceColorArgb = json["reverseFaceColorArgb"]?.jsonPrimitive?.longOrNull
	)
}.getOrNull()

private fun drawWebViewIntoBitmap(
	webView: WebView,
	webViewLocationInWindow: IntArray,
	sourceRectInWindow: Rect,
	bitmap: Bitmap,
	backgroundColorArgb: Int
): Boolean = runCatching {
	bitmap.eraseColor(backgroundColorArgb)
	val canvas = Canvas(bitmap)
	val checkpoint = canvas.save()
	canvas.scale(
		bitmap.width.toFloat() / sourceRectInWindow.width(),
		bitmap.height.toFloat() / sourceRectInWindow.height()
	)
	canvas.translate(
		webViewLocationInWindow[0] - sourceRectInWindow.left.toFloat(),
		webViewLocationInWindow[1] - sourceRectInWindow.top.toFloat()
	)
	webView.draw(canvas)
	canvas.restoreToCount(checkpoint)
	true
}.getOrElse { failure ->
	Logger.w(
		ReaderPageTurnBitmapSourceTag,
		"Page-turn WebView draw fallback failed failureClass=${failure::class.simpleName ?: "unknown"}"
	)
	false
}

private data class ReaderPageTurnForegroundAnalysis(
	val sampleCount: Int,
	val luminanceRange: Int,
	val distantSampleCount: Int,
	val requiredDistantSampleCount: Int,
	val renderable: Boolean,
	val sparseSignature: Int?
)

private fun Bitmap.analyzeRenderableForeground(): ReaderPageTurnForegroundAnalysis =
	readerPageTurnCaptureForegroundAnalysis(width, height) { x, y -> getPixel(x, y) }

private fun readerPageTurnCaptureForegroundAnalysis(
	width: Int,
	height: Int,
	pixelAt: (Int, Int) -> Int
): ReaderPageTurnForegroundAnalysis {
	val primary = readerPageTurnSampledForegroundAnalysis(
		width = width,
		height = height,
		samplePhase = PageTurnCapturePrimarySamplePhase,
		pixelAt = pixelAt
	)
	if (primary.renderable) return primary
	val shifted = readerPageTurnSampledForegroundAnalysis(
		width = width,
		height = height,
		samplePhase = PageTurnCaptureShiftedSamplePhase,
		pixelAt = pixelAt
	)
	if (shifted.renderable) return shifted
	return when {
		primary.sparseSignature != null && shifted.sparseSignature == null -> primary
		shifted.sparseSignature != null && primary.sparseSignature == null -> shifted
		shifted.luminanceRange > primary.luminanceRange -> shifted
		shifted.luminanceRange == primary.luminanceRange &&
			shifted.distantSampleCount > primary.distantSampleCount -> shifted
		else -> primary
	}
}

private fun readerPageTurnSampledForegroundAnalysis(
	width: Int,
	height: Int,
	samplePhase: Float,
	pixelAt: (Int, Int) -> Int
): ReaderPageTurnForegroundAnalysis {
	val columns = PageTurnCaptureSampleColumns.coerceAtMost(width)
	val rows = PageTurnCaptureSampleRows.coerceAtMost(height)
	if (columns <= 0 || rows <= 0) {
		return ReaderPageTurnForegroundAnalysis(0, 0, 0, 0, false, null)
	}
	val pixels = ArrayList<Int>(columns * rows)
	for (row in 0 until rows) {
		val y = ((row + samplePhase) * height / rows).toInt().coerceIn(0, height - 1)
		if (y < height * 0.06f || y > height * 0.94f) continue
		for (column in 0 until columns) {
			val xFraction = (column + samplePhase) / columns
			if (xFraction < 0.06f || xFraction > 0.94f || xFraction in 0.47f..0.53f) continue
			val x = ((column + samplePhase) * width / columns).toInt().coerceIn(0, width - 1)
			pixels += pixelAt(x, y)
		}
	}
	return readerPageTurnForegroundAnalysis(pixels.toIntArray())
}

internal fun readerPageTurnCaptureContainsForeground(
	width: Int,
	height: Int,
	pixelAt: (Int, Int) -> Int
): Boolean = readerPageTurnCaptureForegroundAnalysis(width, height, pixelAt).renderable

private fun readerPageTurnForegroundAnalysis(pixels: IntArray): ReaderPageTurnForegroundAnalysis {
	if (pixels.isEmpty()) return ReaderPageTurnForegroundAnalysis(0, 0, 0, 0, false, null)
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
	val requiredDistantSamples = maxOf(
		PageTurnCaptureMinimumForegroundSamples,
		pixels.size / PageTurnCaptureForegroundSampleDivisor
	)
	val distantSamples = luminance.count { value ->
		abs(value - baseline) >= PageTurnCaptureForegroundDistance
	}
	val renderable =
		range >= PageTurnCaptureMinimumLuminanceRange &&
			distantSamples >= requiredDistantSamples
	val sparseSignature = if (
		!renderable &&
		range >= PageTurnCaptureMinimumLuminanceRange &&
		distantSamples > 0
	) {
		luminance.contentHashCode()
	} else {
		null
	}
	return ReaderPageTurnForegroundAnalysis(
		sampleCount = pixels.size,
		luminanceRange = range,
		distantSampleCount = distantSamples,
		requiredDistantSampleCount = requiredDistantSamples,
		renderable = renderable,
		sparseSignature = sparseSignature
	)
}

internal fun readerPageTurnPixelsContainForeground(pixels: IntArray): Boolean =
	readerPageTurnForegroundAnalysis(pixels).renderable

internal fun readerPageTurnSparseForegroundSettled(
	previousPixels: IntArray,
	currentPixels: IntArray
): Boolean {
	val previous = readerPageTurnForegroundAnalysis(previousPixels).sparseSignature
	return previous != null &&
		readerPageTurnForegroundAnalysis(currentPixels).sparseSignature == previous
}
