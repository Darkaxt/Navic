package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.util.core.Logger
import kotlin.math.roundToInt

private const val ReaderPageTurnBundleSourceTag = "ReaderPageTurnBundleSource"
private const val MaxCachedSnapshots = 5
private const val ReaderPageTurnAnimationBitmapScale = 0.5f

private data class InFlightSnapshotRequest(
	val generation: Long,
	val callbacks: MutableList<(ReaderPageSlideSnapshot?) -> Unit>
)

internal class ReaderPageTurnBundleSource(
	private val bitmapSource: ReaderPageTurnBitmapSource = ReaderPageTurnBitmapSource(),
	private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
	private var activeGeneration = 0L
	private val visualStateRequestId = AtomicLong()
	private val snapshotCache = LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>(0, 0.75f, true)
	private val inFlightSnapshotRequests = mutableMapOf<ReaderPageSlideSnapshotKey, InFlightSnapshotRequest>()
	val isAvailable: Boolean
		get() = bitmapSource.isAvailable

	fun beginGeneration(): Long {
		activeGeneration += 1
		return activeGeneration
	}

	fun cancelActivePreparation() {
		activeGeneration += 1
	}

	fun cached(plan: ReaderPageTurnTransitionPlan): ReaderPageSlideTransition? {
		val source = cachedSnapshot(plan.sourcePageIndex, plan.kind)
		val destination = cachedSnapshot(plan.targetPageIndex, plan.kind)
		val transition = if (source != null && destination != null) {
			ReaderPageSlideTransition(plan, source, destination)
		} else {
			null
		}
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Page-turn snapshot cache ${if (transition == null) "miss" else "hit"} " +
				"source=${plan.sourcePageIndex} target=${plan.targetPageIndex} entries=${snapshotCache.keys}"
		)
		return transition
	}

	fun isCached(plan: ReaderPageTurnTransitionPlan): Boolean =
		cachedSnapshot(plan.sourcePageIndex, plan.kind) != null &&
			cachedSnapshot(plan.targetPageIndex, plan.kind) != null

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
		currentCanRepresentSource: Boolean = true,
		onStagingStarted: (ReaderPageSlideSnapshot) -> Unit = {},
		onPrepared: (ReaderPageSlideTransition?) -> Unit
	) {
		val generation = plan.generation
		if (generation != activeGeneration || !webView.isAttachedToWindow) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			onPrepared(null)
			return
		}

		val cachedSource = cachedSnapshot(plan.sourcePageIndex, plan.kind)
		val source = cachedSource?.also {
			if (it.bitmap !== current.bitmap) current.bitmap.takeUnless { bitmap -> bitmap.isRecycled }?.recycle()
		} ?: if (currentCanRepresentSource) {
			cacheCurrentSnapshot(plan, current)
		} else {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			null
		} ?: run {
			onPrepared(null)
			return
		}
		cached(plan)?.let { cached ->
			onPrepared(cached)
			return
		}

		val destinationKey = snapshotKey(
			plan.targetPageIndex,
			plan.kind,
			source.bitmap,
			source.surfaceRectInWindow
		)
		source.retain()
		requestSnapshot(destinationKey, generation, onPrepared = { destination ->
			try {
				onPrepared(destination?.let { ReaderPageSlideTransition(plan, source, it) })
			} finally {
				source.release()
			}
		}) { complete ->
			val quotedToken = JSONObject.quote(plan.token)
			onStagingStarted(source)
			webView.evaluateJavascript(
				"window.NavicReaderBridge?.exposePageTurnPreviewFinal?.($quotedToken) === true"
			) { encoded ->
				if (generation != activeGeneration || !encoded.isJavascriptTrue()) {
					restoreLiveComposition(webView, plan.token) { complete(null) }
					return@evaluateJavascript
				}
				webView.postOnAnimation {
					captureStagedSurface(webView, current.geometry, source.surfaceRectInWindow) { bitmap ->
						restoreLiveComposition(webView, plan.token) {
							complete(bitmap?.let {
								ReaderPageSlideSnapshot(
									key = destinationKey,
									bitmap = it,
									surfaceRectInWindow = Rect(source.surfaceRectInWindow),
									reverseFaceColor = readerPageTurnOpaqueColor(current.geometry.reverseFaceColorArgb)
								)
							})
						}
					}
				}
			}
		}
	}

	fun cacheCurrentSnapshot(
		plan: ReaderPageTurnTransitionPlan,
		current: ReaderPageTurnCaptureResult
	): ReaderPageSlideSnapshot? {
		if (plan.generation != activeGeneration) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		val key = snapshotKey(plan.sourcePageIndex, plan.kind, current.bitmap, current.sourceRectInWindow)
		snapshotCache[key]?.let { cached ->
			snapshotCache[key]
			if (cached.bitmap !== current.bitmap) current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return cached
		}
		return putSnapshot(
			ReaderPageSlideSnapshot(
				key = key,
				bitmap = current.bitmap,
				surfaceRectInWindow = Rect(current.sourceRectInWindow),
				reverseFaceColor = readerPageTurnOpaqueColor(current.geometry.reverseFaceColorArgb)
			)
		)
	}

	private fun requestSnapshot(
		key: ReaderPageSlideSnapshotKey,
		generation: Long,
		onPrepared: (ReaderPageSlideSnapshot?) -> Unit,
		capture: ((ReaderPageSlideSnapshot?) -> Unit) -> Unit
	) {
		snapshotCache[key]?.let {
			onPrepared(it)
			return
		}
		inFlightSnapshotRequests[key]?.takeIf { it.generation == generation }?.let { request ->
			request.callbacks.add(onPrepared)
			return
		}

		val callbacks = mutableListOf(onPrepared)
		inFlightSnapshotRequests[key] = InFlightSnapshotRequest(generation, callbacks)
		capture { captured ->
			val request = inFlightSnapshotRequests.remove(key)
			if (request == null || request.generation != generation || generation != activeGeneration) {
				val staleSnapshot = captured
				staleSnapshot?.releaseCacheOwnership()
				request?.callbacks?.forEach { it(null) }
				return@capture
			}
			val cached = captured?.let(::putSnapshot)
			request.callbacks.forEach { it(cached) }
		}
	}

	private fun cachedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? = snapshotCache.entries
		.lastOrNull { (key, _) -> key.visualPageIndex == pageIndex && key.kind == kind }
		?.let { (key, value) ->
			snapshotCache[key]
			value
		}

	private fun putSnapshot(snapshot: ReaderPageSlideSnapshot): ReaderPageSlideSnapshot {
		snapshotCache[snapshot.key]?.let { cached ->
			snapshot.releaseCacheOwnership()
			return cached
		}
		snapshotCache[snapshot.key] = snapshot
		while (snapshotCache.size > MaxCachedSnapshots) {
			val eldest = snapshotCache.entries.iterator().next()
			snapshotCache.remove(eldest.key)
			eldest.value.releaseCacheOwnership()
		}
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Page-turn snapshot cached key=${snapshot.key} entries=${snapshotCache.keys}"
		)
		return snapshot
	}

	private fun snapshotKey(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		bitmap: Bitmap,
		surfaceRectInWindow: Rect
	): ReaderPageSlideSnapshotKey = ReaderPageSlideSnapshotKey(
		visualPageIndex = pageIndex,
		kind = kind,
		bitmapWidth = bitmap.width,
		bitmapHeight = bitmap.height,
		surfaceWidth = surfaceRectInWindow.width(),
		surfaceHeight = surfaceRectInWindow.height()
	)

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
				Bitmap.createBitmap(
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.width()),
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.height()),
					Bitmap.Config.ARGB_8888
				)
			}.getOrNull()
			if (bitmap == null) {
				onCaptured(null)
			} else {
				val location = IntArray(2)
				webView.getLocationInWindow(location)
				val canvas = Canvas(bitmap)
				canvas.drawColor(readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb))
				canvas.scale(
					bitmap.width / sourceRectInWindow.width().toFloat(),
					bitmap.height / sourceRectInWindow.height().toFloat()
				)
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
		val awaitCompositedPreview = {
			if (!webView.isAttachedToWindow) {
				onCaptured(null)
			} else {
				webView.postVisualStateCallback(
					visualStateRequestId.incrementAndGet(),
					object : WebView.VisualStateCallback() {
						override fun onComplete(requestId: Long) {
							if (!webView.isAttachedToWindow) onCaptured(null)
							else webView.postOnAnimation(draw)
						}
					}
				)
			}
		}
		if (Looper.myLooper() == Looper.getMainLooper()) awaitCompositedPreview() else mainHandler.post(awaitCompositedPreview)
	}

	fun invalidate(reason: String) {
		activeGeneration += 1
		inFlightSnapshotRequests.values.forEach { request -> request.callbacks.forEach { it(null) } }
		inFlightSnapshotRequests.clear()
		snapshotCache.values.distinctBy { System.identityHashCode(it) }.forEach { it.releaseCacheOwnership() }
		snapshotCache.clear()
		Logger.i(ReaderPageTurnBundleSourceTag, "Page-turn snapshot cache cleared reason=$reason")
	}

	private fun restoreLiveComposition(
		webView: WebView,
		token: String,
		onRestored: () -> Unit = {}
	) {
		if (!webView.isAttachedToWindow) {
			onRestored()
			return
		}
		val quotedToken = JSONObject.quote(token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.restorePageTurnLiveComposition?.($quotedToken)"
		) {
			if (!webView.isAttachedToWindow) {
				onRestored()
				return@evaluateJavascript
			}
			webView.postVisualStateCallback(
				visualStateRequestId.incrementAndGet(),
				object : WebView.VisualStateCallback() {
					override fun onComplete(requestId: Long) {
						if (!webView.isAttachedToWindow) onRestored()
						else webView.postOnAnimation(onRestored)
					}
				}
			)
		}
	}
}

internal fun readerPageTurnOpaqueColor(argb: Long?): Int {
	val color = argb?.toInt() ?: Color.rgb(234, 217, 174)
	return color or Color.BLACK
}

internal fun readerPageTurnAnimationBitmapDimension(physicalPixels: Int): Int =
	(physicalPixels * ReaderPageTurnAnimationBitmapScale).roundToInt().coerceAtLeast(1)

internal fun readerPageSlideSnapshotWindow(
	centerPageIndex: Int,
	step: Int,
	pageCount: Int
): List<Int> = listOf(
	centerPageIndex,
	centerPageIndex - step,
	centerPageIndex + step,
	centerPageIndex - (2 * step),
	centerPageIndex + (2 * step)
).filter { it in 0 until pageCount }.distinct()

private fun String?.isJavascriptTrue(): Boolean = runCatching {
	JSONTokener(orEmpty()).nextValue() as? Boolean == true
}.getOrDefault(false)

private fun String?.javascriptString(): String? = runCatching {
	JSONTokener(orEmpty()).nextValue() as? String
}.getOrNull()
