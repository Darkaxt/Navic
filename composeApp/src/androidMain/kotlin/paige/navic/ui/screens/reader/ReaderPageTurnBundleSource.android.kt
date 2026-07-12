package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.util.core.Logger

private const val ReaderPageTurnBundleSourceTag = "ReaderPageTurnBundleSource"
private const val MaxCachedBundles = 3

internal class ReaderPageTurnBundleSource(
	private val bitmapSource: ReaderPageTurnBitmapSource = ReaderPageTurnBitmapSource(),
	private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
	private var activeGeneration = 0L
	private val cache = LinkedHashMap<String, ReaderPageTurnBitmapBundle>(0, 0.75f, true)
	val isAvailable: Boolean
		get() = bitmapSource.isAvailable

	fun beginGeneration(): Long {
		activeGeneration += 1
		return activeGeneration
	}

	fun cancelActivePreparation() {
		activeGeneration += 1
	}

	fun cached(plan: ReaderPageTurnTransitionPlan): ReaderPageTurnBitmapBundle? = cache[plan.cacheKey]

	fun captureCurrentSurface(
		webView: WebView,
		generation: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) {
		bitmapSource.captureSurface(webView) { result ->
			if (generation != activeGeneration) {
				result?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				onCaptured(null)
			} else {
				onCaptured(result)
			}
		}
	}

	fun captureBundle(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		current: ReaderPageTurnCaptureResult,
		onPrepared: (ReaderPageTurnBitmapBundle?) -> Unit
	) {
		val generation = plan.generation
		if (generation != activeGeneration || !webView.isAttachedToWindow) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			onPrepared(null)
			return
		}
		cached(plan)?.let { cached ->
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			onPrepared(cached)
			return
		}
		val quotedToken = JSONObject.quote(plan.token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.exposePageTurnPreviewFinal?.($quotedToken) === true"
		) { encoded ->
			if (generation != activeGeneration || !encoded.isJavascriptTrue()) {
				current.bitmap.takeUnless { it.isRecycled }?.recycle()
				restoreLiveComposition(webView, plan.token)
				onPrepared(null)
				return@evaluateJavascript
			}
			webView.postOnAnimation {
				captureStagedSurface(webView, current.geometry, current.sourceRectInWindow) { finalBase ->
					restoreLiveComposition(webView, plan.token)
					if (finalBase == null) {
						current.bitmap.takeUnless { it.isRecycled }?.recycle()
						onPrepared(null)
						return@captureStagedSurface
					}
					if (
						plan.kind == ReaderPageTurnTransitionKind.PortraitLeaf &&
						plan.underneathPageIndex != null
					) {
						capturePortraitUnderneath(webView, plan, current) { underneathBase ->
							if (underneathBase == null) {
								current.bitmap.takeUnless { it.isRecycled }?.recycle()
								finalBase.takeUnless { it.isRecycled }?.recycle()
								onPrepared(null)
							} else {
								completeBundleCapture(webView, plan, current, finalBase, underneathBase, onPrepared)
							}
						}
					} else {
						completeBundleCapture(webView, plan, current, finalBase, null, onPrepared)
					}
				}
			}
		}
	}

	private fun capturePortraitUnderneath(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		current: ReaderPageTurnCaptureResult,
		onCaptured: (Bitmap?) -> Unit
	) {
		val pageIndex = plan.underneathPageIndex ?: run {
			onCaptured(null)
			return
		}
		val quotedToken = JSONObject.quote(plan.token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.beginPageTurnPreviewPreparation?.($quotedToken, $pageIndex)"
		) {
			waitForPortraitUnderneathReady(webView, plan, current, onCaptured)
		}
	}

	private fun waitForPortraitUnderneathReady(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		current: ReaderPageTurnCaptureResult,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (plan.generation != activeGeneration || !webView.isAttachedToWindow) {
			onCaptured(null)
			return
		}
		val quotedToken = JSONObject.quote(plan.token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.pageTurnPreviewState?.($quotedToken)?.status ?? 'missing'"
		) { encodedStatus ->
			if (plan.generation != activeGeneration || !webView.isAttachedToWindow) {
				onCaptured(null)
				return@evaluateJavascript
			}
			when (encodedStatus.javascriptString()) {
				"ready" -> webView.evaluateJavascript(
					"window.NavicReaderBridge?.exposePageTurnPreviewFinal?.($quotedToken) === true"
				) { encodedExposed ->
					if (plan.generation != activeGeneration || !encodedExposed.isJavascriptTrue()) {
						restoreLiveComposition(webView, plan.token)
						onCaptured(null)
						return@evaluateJavascript
					}
					webView.postOnAnimation {
						captureStagedSurface(webView, current.geometry, current.sourceRectInWindow) { underneathBase ->
							restoreLiveComposition(webView, plan.token)
							onCaptured(underneathBase)
						}
					}
				}
				"failed", "missing" -> {
					restoreLiveComposition(webView, plan.token)
					onCaptured(null)
				}
				else -> webView.postOnAnimation {
					waitForPortraitUnderneathReady(webView, plan, current, onCaptured)
				}
			}
		}
	}

	private fun completeBundleCapture(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		current: ReaderPageTurnCaptureResult,
		finalBase: Bitmap,
		underneathBase: Bitmap?,
		onPrepared: (ReaderPageTurnBitmapBundle?) -> Unit
	) {
		val bundle = buildBundle(webView, plan, current, finalBase, underneathBase)
		underneathBase?.takeUnless { it.isRecycled }?.recycle()
		if (bundle == null) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			finalBase.takeUnless { it.isRecycled }?.recycle()
			onPrepared(null)
			return
		}
		if (plan.generation != activeGeneration) {
			bundle.recycle()
			onPrepared(null)
			return
		}
		put(bundle)
		onPrepared(bundle)
	}

	internal fun captureStagedSurface(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		sourceRectInWindow: Rect,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (!webView.isAttachedToWindow || sourceRectInWindow.width() <= 0 || sourceRectInWindow.height() <= 0) {
			onCaptured(null)
			return
		}
		val draw = {
			val bitmap = runCatching {
				Bitmap.createBitmap(sourceRectInWindow.width(), sourceRectInWindow.height(), Bitmap.Config.ARGB_8888)
			}.getOrNull()
			if (bitmap == null) {
				onCaptured(null)
			} else {
				val location = IntArray(2)
				webView.getLocationInWindow(location)
				val canvas = Canvas(bitmap)
				canvas.translate(
					-(sourceRectInWindow.left - location[0]).toFloat(),
					-(sourceRectInWindow.top - location[1]).toFloat()
				)
				webView.draw(canvas)
				if (geometry.pages.isEmpty()) {
					bitmap.recycle()
					onCaptured(null)
				} else {
					onCaptured(bitmap)
				}
			}
		}
		if (Looper.myLooper() == Looper.getMainLooper()) draw() else mainHandler.post(draw)
	}

	fun invalidate(reason: String) {
		activeGeneration += 1
		cache.values.distinctBy { System.identityHashCode(it) }.forEach { it.recycle() }
		cache.clear()
		Logger.i(ReaderPageTurnBundleSourceTag, "Page-turn bundle cache cleared reason=$reason")
	}

	private fun buildBundle(
		webView: WebView,
		plan: ReaderPageTurnTransitionPlan,
		current: ReaderPageTurnCaptureResult,
		finalBase: Bitmap,
		underneathBase: Bitmap?
	): ReaderPageTurnBitmapBundle? {
		val front = cropPage(webView, current.bitmap, current.geometry, current.sourceRectInWindow, plan.turningFrontPageSide)
			?: return null
		val reverseCapture = if (plan.turningReversePageSide != null) {
			cropPage(webView, finalBase, current.geometry, current.sourceRectInWindow, plan.turningReversePageSide)
				?: return null
		} else {
			null
		}
		val underneath = if (plan.underneathPageSide != null) {
			cropPage(
				webView,
				underneathBase ?: finalBase,
				current.geometry,
				current.sourceRectInWindow,
				plan.underneathPageSide
			)
				?: return null
		} else {
			null
		}
		return ReaderPageTurnBitmapBundle(
			plan = plan,
			surfaceRectInWindow = Rect(current.sourceRectInWindow),
			turningFrontRectInSurface = front.rectInSurface,
			underneathRectInSurface = underneath?.rectInSurface,
			currentBase = current.bitmap,
			turningFront = front.bitmap,
			turningReverse = reverseCapture?.bitmap?.mirroredHorizontally(),
			underneath = underneath?.bitmap,
			finalBase = finalBase
		)
	}

	private fun cropPage(
		webView: WebView,
		base: Bitmap,
		geometry: ReaderPageTurnCaptureGeometry,
		surfaceRectInWindow: Rect,
		side: ReaderPageTurnPhysicalSide
	): ReaderPageTurnPageCapture? {
		val location = IntArray(2)
		webView.getLocationInWindow(location)
		val role = side.toPageRole().takeUnless {
			it != ReaderPageTurnPageRole.Full && geometry.mode == paige.navic.reader.ReaderPageTurnLayoutMode.Single
		} ?: ReaderPageTurnPageRole.Full
		val page = geometry.pageRectInWindow(
			role = role,
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		) ?: return null
		val left = (page.left - surfaceRectInWindow.left).coerceIn(0, base.width)
		val top = (page.top - surfaceRectInWindow.top).coerceIn(0, base.height)
		val right = (page.right - surfaceRectInWindow.left).coerceIn(left, base.width)
		val bottom = (page.bottom - surfaceRectInWindow.top).coerceIn(top, base.height)
		if (right <= left || bottom <= top) return null
		return ReaderPageTurnPageCapture(
			bitmap = Bitmap.createBitmap(base, left, top, right - left, bottom - top),
			rectInSurface = Rect(left, top, right, bottom)
		)
	}

	private fun put(bundle: ReaderPageTurnBitmapBundle) {
		cache.put(bundle.plan.cacheKey, bundle)?.takeIf { it !== bundle }?.recycle()
		while (cache.size > MaxCachedBundles) {
			val eldest = cache.entries.iterator().next()
			cache.remove(eldest.key)
			eldest.value.recycle()
		}
	}

	private fun restoreLiveComposition(webView: WebView, token: String) {
		val quotedToken = JSONObject.quote(token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.restorePageTurnLiveComposition?.($quotedToken)"
		) { }
	}
}

private data class ReaderPageTurnPageCapture(
	val bitmap: Bitmap,
	val rectInSurface: Rect
)

private fun Bitmap.mirroredHorizontally(): Bitmap {
	val matrix = Matrix().apply { setScale(-1f, 1f, width / 2f, height / 2f) }
	return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
		if (it !== this && !isRecycled) recycle()
	}
}

private fun String?.isJavascriptTrue(): Boolean = runCatching {
	JSONTokener(orEmpty()).nextValue() as? Boolean == true
}.getOrDefault(false)

private fun String?.javascriptString(): String? = runCatching {
	JSONTokener(orEmpty()).nextValue() as? String
}.getOrNull()
