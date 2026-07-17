package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPreparationMode
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.readerPageRasterStorageRoot
import paige.navic.util.core.Logger
import kotlin.math.roundToInt

private const val ReaderPageTurnBundleSourceTag = "ReaderPageTurnBundleSource"
private const val MaxCachedSnapshots = 5

private data class InFlightRasterHydration(
	val generation: Long,
	val callbacks: MutableList<(ReaderPageSlideSnapshot?) -> Unit>
)

internal class ReaderPageTurnBundleSource(
	private val bitmapSource: ReaderPageTurnBitmapSource = ReaderPageTurnBitmapSource(),
	private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
	private var activeGeneration = 0L
	private var bitmapQuality = ReaderPageBitmapQuality.Balanced
	private val rasterJob = SupervisorJob()
	private val rasterScope = CoroutineScope(rasterJob + Dispatchers.Main.immediate)
	private val rasterInitializationMutex = Mutex()
	private val visualStateRequestId = AtomicLong()
	private val snapshotCache = LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>(0, 0.75f, true)
	private val inFlightRasterHydrations = mutableMapOf<String, InFlightRasterHydration>()
	private val stagedRasterGenerations = mutableMapOf<String, ReaderPageRasterGeneration<Bitmap>>()
	private val rasterPublicationInFlight = mutableSetOf<String>()
	private val rasterPublicationCallbacks = mutableMapOf<String, MutableList<(Boolean) -> Unit>>()
	private val rasterPersistenceDiagnostics = linkedSetOf<String>()
	private var rasterCache: ReaderPageRasterCache<Bitmap>? = null
	private var rasterScheduler: ReaderPageRasterScheduler<Bitmap>? = null
	private var activeWebView = WeakReference<WebView>(null)
	private var closed = false
	val isAvailable: Boolean
		get() = bitmapSource.isAvailable

	fun updateBitmapQuality(quality: ReaderPageBitmapQuality): Boolean {
		if (bitmapQuality == quality) return false
		bitmapQuality = quality
		bitmapSource.updateBitmapQuality(quality)
		invalidate("bitmap-quality-${quality.persistedValue}")
		return true
	}

	fun currentGeneration(): Long = activeGeneration

	fun retainedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? = cachedSnapshot(pageIndex, kind)?.also { snapshot -> snapshot.retain() }

	fun cachedSnapshotPageIndices(kind: ReaderPageTurnTransitionKind): List<Int> =
		snapshotCache.keys
			.filter { key -> key.kind == kind }
			.map { key -> key.visualPageIndex }
			.distinct()
			.sorted()

	fun captureCurrentSurface(
		webView: WebView,
		generation: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) {
		activeWebView = WeakReference(webView)
		bitmapSource.captureSurface(webView) { result ->
			if (generation != activeGeneration) {
				result?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				onCaptured(null)
			} else {
				onCaptured(result)
			}
		}
	}

	fun hydrateSnapshot(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		onHydrated: (ReaderPageSlideSnapshot?) -> Unit
	) {
		activeWebView = WeakReference(webView)
		cachedSnapshot(pageIndex, kind)?.let {
			onHydrated(it)
			return
		}
		if (closed || !webView.isAttachedToWindow) {
			onHydrated(null)
			return
		}
		val generation = activeGeneration
		val quality = bitmapQuality
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterDescriptor?.($pageIndex) ?? null)"
		) { encodedDescriptor ->
			val descriptor = readerPageRasterDescriptor(encodedDescriptor)
			if (descriptor == null || closed || generation != activeGeneration || quality != bitmapQuality) {
				onHydrated(null)
				return@evaluateJavascript
			}
			val key = descriptor.key(quality)
			inFlightRasterHydrations[key.digest]
				?.takeIf { request -> request.generation == generation }
				?.let { request ->
					request.callbacks.add(onHydrated)
					return@evaluateJavascript
				}
			val request = InFlightRasterHydration(generation, mutableListOf(onHydrated))
			inFlightRasterHydrations[key.digest] = request
			val liveSurface = Rect(reference.surfaceRectInWindow)
			rasterScope.launch {
				val scheduler = rasterScheduler(webView)
				scheduler.activateProfile(key.profile)
				val cache = rasterCache
				val raster = cache?.let {
					withContext(Dispatchers.IO) {
						cache.readCopy(key) { cached ->
							cached.copy(Bitmap.Config.ARGB_8888, false)
						}
					}
				}
				val hydration = inFlightRasterHydrations.remove(key.digest)
				val hydrated = raster?.let { cached ->
					val bitmap = cached.value
					val leafGeometry = readerPageRasterLeafGeometry(
						metadata = cached.metadata,
						bitmapWidth = bitmap.width,
						bitmapHeight = bitmap.height
					)
					val kindMatches = when (kind) {
						ReaderPageTurnTransitionKind.PortraitSlide -> leafGeometry?.fullLeafRect != null
						ReaderPageTurnTransitionKind.LandscapeSpreadSlide ->
							leafGeometry?.leftLeafRect != null && leafGeometry.rightLeafRect != null
					}
					if (
						closed || generation != activeGeneration || quality != bitmapQuality ||
						hydration?.generation != generation || !kindMatches
					) {
						ReaderAndroidPageRasterCodec.release(bitmap)
						null
					} else {
						ReaderPageSlideSnapshot(
							key = snapshotKey(pageIndex, kind, bitmap, liveSurface),
							bitmap = bitmap,
							surfaceRectInWindow = Rect(reference.surfaceRectInWindow),
							leafGeometry = checkNotNull(leafGeometry),
							reverseFaceColor = cached.metadata.reverseFaceColor
						)
					}
				}
				val cached = hydrated?.let { snapshot ->
					putSnapshot(
						snapshot = snapshot,
						priority = ReaderPageRasterPriority.NextTransition,
						persist = false
					)
				}
				hydration?.callbacks?.forEach { callback -> callback(cached) }
			}
		}
	}

	fun capturePreparedRasterPage(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		itemToken: String,
		priority: ReaderPageRasterPriority,
		onStagingStarted: (ReaderPageSlideSnapshot) -> Unit = {},
		onCaptured: (Boolean) -> Unit
	) {
		activeWebView = WeakReference(webView)
		val generation = activeGeneration
		if (closed || !webView.isAttachedToWindow) {
			onCaptured(false)
			return
		}
		cachedSnapshot(pageIndex, kind)?.let { cached ->
			onCaptured(true)
			schedulePersistentSnapshot(cached, priority) { persisted ->
				if (!persisted) {
					Logger.w(
						ReaderPageTurnBundleSourceTag,
						"Prepared page remains usable after cache persistence failure pageIndex=$pageIndex"
					)
				}
			}
			return
		}
		val destinationKey = snapshotKey(
			pageIndex = pageIndex,
			kind = kind,
			bitmap = reference.bitmap,
			surfaceRectInWindow = reference.surfaceRectInWindow
		)
		reference.retain()
		capturePreparedPage(
			webView = webView,
			token = itemToken,
			generation = generation,
			reference = reference,
			destinationKey = destinationKey,
			onStagingStarted = { onStagingStarted(reference) }
		) { captured ->
			try {
				if (captured == null || generation != activeGeneration || closed) {
					captured?.releaseCacheOwnership()
					onCaptured(false)
					return@capturePreparedPage
				}
				val cached = putSnapshot(captured, priority, persist = false)
				onCaptured(true)
				schedulePersistentSnapshot(cached, priority) { persisted ->
					if (!persisted) {
						Logger.w(
							ReaderPageTurnBundleSourceTag,
							"Captured page remains usable after cache persistence failure pageIndex=$pageIndex"
						)
					}
				}
			} finally {
				reference.release()
			}
		}
	}

	private fun capturePreparedPage(
		webView: WebView,
		token: String,
		generation: Long,
		reference: ReaderPageSlideSnapshot,
		destinationKey: ReaderPageSlideSnapshotKey,
		onStagingStarted: () -> Unit,
		onCaptured: (ReaderPageSlideSnapshot?) -> Unit
	) {
		val captureStartedAt = SystemClock.uptimeMillis()
		val quotedToken = JSONObject.quote(token)
		onStagingStarted()
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.exposePageTurnPreviewFinal?.($quotedToken) === true"
		) { encoded ->
			if (generation != activeGeneration || !encoded.isJavascriptTrue()) {
				restoreLiveComposition(webView, token) { onCaptured(null) }
				return@evaluateJavascript
			}
			webView.postOnAnimation {
				capturePreparedSurface(webView, reference) { bitmap ->
					restoreLiveComposition(webView, token) {
						onCaptured(bitmap?.let {
							ReaderPageSlideSnapshot(
								key = destinationKey,
								bitmap = it,
								surfaceRectInWindow = Rect(reference.surfaceRectInWindow),
								leafGeometry = reference.leafGeometry,
								reverseFaceColor = reference.reverseFaceColor,
								captureMillis = SystemClock.uptimeMillis() - captureStartedAt
							)
						})
					}
				}
			}
		}
	}

	fun cacheCurrentSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		current: ReaderPageTurnCaptureResult,
		generation: Long = activeGeneration
	): ReaderPageSlideSnapshot? = cacheSnapshot(pageIndex, kind, current, generation)

	fun ensurePersistentSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		onPersisted: (Boolean) -> Unit
	) {
		schedulePersistentSnapshot(snapshot, priority, onPersisted)
	}

	fun preparationMode(
		webView: WebView,
		chapterPageCount: Int,
		onResolved: (ReaderPageRasterPreparationMode) -> Unit
	) {
		if (closed || !webView.isAttachedToWindow) return
		activeWebView = WeakReference(webView)
		val generation = activeGeneration
		rasterScope.launch {
			val scheduler = rasterScheduler(webView)
			if (closed || generation != activeGeneration || !webView.isAttachedToWindow) return@launch
			onResolved(scheduler.preparationMode(chapterPageCount))
		}
	}

	private fun cacheSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		current: ReaderPageTurnCaptureResult,
		generation: Long
	): ReaderPageSlideSnapshot? {
		if (generation != activeGeneration) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		val key = snapshotKey(pageIndex, kind, current.bitmap, current.sourceRectInWindow)
		val leafGeometry = current.geometry.leafGeometry(current.bitmap.width, current.bitmap.height) ?: run {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
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
				leafGeometry = leafGeometry,
				reverseFaceColor = readerPageTurnOpaqueColor(current.geometry.reverseFaceColorArgb),
				captureMillis = current.elapsedMs
			),
			ReaderPageRasterPriority.Current
		)
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

	private fun putSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		persist: Boolean = true
	): ReaderPageSlideSnapshot {
		snapshotCache[snapshot.key]?.let { cached ->
			snapshot.releaseCacheOwnership()
			return cached
		}
		snapshotCache[snapshot.key] = snapshot
		if (persist) schedulePersistentSnapshot(snapshot, priority)
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

	private fun schedulePersistentSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		onPersisted: (Boolean) -> Unit = {}
	) {
		val pageIndex = snapshot.key.visualPageIndex
		if (closed) {
			rasterPersistenceSkipped(pageIndex, "bundle-source-closed", activeGeneration)
			onPersisted(false)
			return
		}
		val webView = activeWebView.get()?.takeIf { it.isAttachedToWindow } ?: run {
			rasterPersistenceSkipped(pageIndex, "webview-unavailable", activeGeneration)
			onPersisted(false)
			return
		}
		val generation = activeGeneration
		snapshot.retain()
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterDescriptor?.($pageIndex) ?? null)"
		) { encodedDescriptor ->
			try {
				if (closed || generation != activeGeneration) {
					rasterPersistenceSkipped(pageIndex, "generation-changed", generation)
					onPersisted(false)
					return@evaluateJavascript
				}
				val descriptor = readerPageRasterDescriptor(encodedDescriptor) ?: run {
					rasterPersistenceSkipped(pageIndex, "descriptor-unavailable", generation)
					onPersisted(false)
					return@evaluateJavascript
				}
				val key = descriptor.key(snapshot.key.bitmapQuality)
				rasterPublicationCallbacks.getOrPut(key.digest) { mutableListOf() }.add(onPersisted)
				if (!rasterPublicationInFlight.add(key.digest)) return@evaluateJavascript
				val persistentBitmap = runCatching {
					snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, false)
				}.getOrNull()
				if (persistentBitmap == null) {
					rasterPersistenceSkipped(pageIndex, "bitmap-copy-failed", generation)
					completeRasterPublication(key.digest, false)
					return@evaluateJavascript
				}
				val rasterGeneration = ReaderPageRasterGeneration(
					metadata = snapshot.toRasterMetadata(),
					value = persistentBitmap,
					captureMillis = snapshot.captureMillis.coerceAtLeast(0L)
				)
				stagedRasterGenerations[key.digest] = rasterGeneration
				rasterScope.launch {
					val scheduler = rasterScheduler(webView)
					if (closed) {
						rasterPersistenceSkipped(pageIndex, "bundle-source-closed", generation)
						scheduler.close()
						stagedRasterGenerations.remove(key.digest)?.let { unused ->
							ReaderAndroidPageRasterCodec.release(unused.value)
						}
						completeRasterPublication(key.digest, false)
						return@launch
					}
					scheduler.activateProfile(key.profile)
					val result = scheduler.request(key, priority).await()
					stagedRasterGenerations.remove(key.digest)?.let { unused ->
						ReaderAndroidPageRasterCodec.release(unused.value)
					}
					val persisted = result.status == ReaderPageRasterScheduleStatus.Cached ||
						result.status == ReaderPageRasterScheduleStatus.Published
					if (!persisted) {
						rasterPersistenceSkipped(
							pageIndex,
							"scheduler-${result.status.name.lowercase()}",
							generation
						)
					}
					completeRasterPublication(key.digest, persisted)
					Logger.i(
						ReaderPageTurnBundleSourceTag,
						"Page raster ${result.status.name.lowercase()} page=$pageIndex " +
							"quality=${snapshot.key.bitmapQuality.persistedValue}"
					)
				}
			} finally {
				snapshot.release()
			}
		}
	}

	private fun rasterPersistenceSkipped(
		pageIndex: Int,
		reason: String,
		requestGeneration: Long
	) {
		val diagnosticKey = "$pageIndex:$reason"
		if (!rasterPersistenceDiagnostics.add(diagnosticKey)) return
		while (rasterPersistenceDiagnostics.size > 64) {
			rasterPersistenceDiagnostics.remove(rasterPersistenceDiagnostics.first())
		}
		Logger.w(
			ReaderPageTurnBundleSourceTag,
			"Page raster persistence skipped page=$pageIndex reason=$reason " +
				"requestGeneration=$requestGeneration activeGeneration=$activeGeneration"
		)
	}

	private fun completeRasterPublication(digest: String, persisted: Boolean) {
		rasterPublicationInFlight.remove(digest)
		rasterPublicationCallbacks.remove(digest)?.forEach { callback -> callback(persisted) }
	}

	private suspend fun rasterScheduler(webView: WebView): ReaderPageRasterScheduler<Bitmap> =
		rasterInitializationMutex.withLock {
			rasterScheduler?.let { return@withLock it }
			val cache = withContext(Dispatchers.IO) {
				ReaderPageRasterCache(
					root = readerPageRasterStorageRoot(webView.context.applicationContext),
					codec = ReaderAndroidPageRasterCodec,
					onDiagnostic = { diagnostic ->
						Logger.w(ReaderPageTurnBundleSourceTag, "Page raster cache $diagnostic")
					}
				)
			}
			ReaderPageRasterScheduler(
				scope = rasterScope,
				store = ReaderPageRasterCacheStore(cache),
				generator = ReaderPageRasterGenerator { key -> stagedRasterGenerations.remove(key.digest) },
				release = ReaderAndroidPageRasterCodec::release
			).also { scheduler ->
				rasterCache = cache
				rasterScheduler = scheduler
			}
		}

	private fun ReaderPageSlideSnapshot.toRasterMetadata(): ReaderPageRasterMetadata = ReaderPageRasterMetadata(
		surfaceLeft = 0,
		surfaceTop = 0,
		surfaceRight = bitmap.width,
		surfaceBottom = bitmap.height,
		fullLeafRect = leafGeometry.fullLeafRect?.toRasterRect(),
		leftLeafRect = leafGeometry.leftLeafRect?.toRasterRect(),
		gutterRect = leafGeometry.gutterRect?.toRasterRect(),
		rightLeafRect = leafGeometry.rightLeafRect?.toRasterRect(),
		reverseFaceColor = reverseFaceColor
	)

	private fun paige.navic.reader.ReaderPageTurnPixelRect.toRasterRect() = ReaderPageRasterRect(
		left = left,
		top = top,
		right = right,
		bottom = bottom
	)

	private fun snapshotKey(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		bitmap: Bitmap,
		surfaceRectInWindow: Rect
	): ReaderPageSlideSnapshotKey = ReaderPageSlideSnapshotKey(
		visualPageIndex = pageIndex,
		kind = kind,
		bitmapQuality = bitmapQuality,
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
		if (geometry.pages.isEmpty()) {
			onCaptured(null)
			return
		}
		captureCompositedSurface(
			webView = webView,
			sourceRectInWindow = sourceRectInWindow,
			backgroundColor = readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb),
			onCaptured = onCaptured
		)
	}

	private fun capturePreparedSurface(
		webView: WebView,
		source: ReaderPageSlideSnapshot,
		onCaptured: (Bitmap?) -> Unit
	) = captureCompositedSurface(
		webView = webView,
		sourceRectInWindow = source.surfaceRectInWindow,
		backgroundColor = source.reverseFaceColor,
		onCaptured = onCaptured
	)

	private fun captureCompositedSurface(
		webView: WebView,
		sourceRectInWindow: Rect,
		backgroundColor: Int,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (!webView.isAttachedToWindow || sourceRectInWindow.width() <= 0 || sourceRectInWindow.height() <= 0) {
			onCaptured(null)
			return
		}
		val draw = {
			val bitmap = runCatching {
				Bitmap.createBitmap(
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.width(), bitmapQuality),
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.height(), bitmapQuality),
					Bitmap.Config.ARGB_8888
				)
			}.getOrNull()
			if (bitmap == null) {
				onCaptured(null)
			} else {
				val location = IntArray(2)
				webView.getLocationInWindow(location)
				val canvas = Canvas(bitmap)
				canvas.drawColor(backgroundColor)
				canvas.scale(
					bitmap.width / sourceRectInWindow.width().toFloat(),
					bitmap.height / sourceRectInWindow.height().toFloat()
				)
				canvas.translate(
					-(sourceRectInWindow.left - location[0]).toFloat(),
					-(sourceRectInWindow.top - location[1]).toFloat()
				)
				webView.draw(canvas)
				onCaptured(bitmap)
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

	fun invalidatePage(pageIndex: Int, reason: String) {
		val removed = snapshotCache.entries
			.filter { (key, _) -> key.visualPageIndex == pageIndex }
			.map { it.key to it.value }
		removed.forEach { (key, snapshot) ->
			snapshotCache.remove(key)
			snapshot.releaseCacheOwnership()
		}
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Page-turn snapshot page cleared page=$pageIndex reason=$reason removed=${removed.size} entries=${snapshotCache.keys}"
		)
	}

	fun invalidate(reason: String) {
		activeGeneration += 1
		inFlightRasterHydrations.values.forEach { request -> request.callbacks.forEach { it(null) } }
		inFlightRasterHydrations.clear()
		rasterPublicationCallbacks.values.flatten().forEach { callback -> callback(false) }
		rasterPublicationCallbacks.clear()
		rasterPublicationInFlight.clear()
		snapshotCache.values.distinctBy { System.identityHashCode(it) }.forEach { it.releaseCacheOwnership() }
		snapshotCache.clear()
		Logger.i(ReaderPageTurnBundleSourceTag, "Page-turn snapshot cache cleared reason=$reason")
	}

	fun close() {
		if (closed) return
		closed = true
		invalidate("close")
		rasterScheduler?.close()
		stagedRasterGenerations.values.forEach { generation ->
			ReaderAndroidPageRasterCodec.release(generation.value)
		}
		stagedRasterGenerations.clear()
		rasterJob.cancel()
		rasterCache = null
		activeWebView.clear()
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

internal fun readerPageTurnAnimationBitmapDimension(
	physicalPixels: Int,
	quality: ReaderPageBitmapQuality
): Int = (physicalPixels * quality.scale).roundToInt().coerceAtLeast(1)

internal fun readerPageSlideSnapshotWindow(
	centerPageIndex: Int,
	step: Int,
	pageCount: Int
): List<Int> = listOf(
	centerPageIndex,
	centerPageIndex + step,
	centerPageIndex + (2 * step),
	centerPageIndex - step,
	centerPageIndex - (2 * step)
).filter { it in 0 until pageCount }.distinct()

private fun String?.isJavascriptTrue(): Boolean = runCatching {
	JSONTokener(orEmpty()).nextValue() as? Boolean == true
}.getOrDefault(false)
