package paige.navic.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.util.core.Logger
import java.util.UUID
import kotlin.math.roundToInt

private const val ReaderAlternateLayoutTag = "ReaderAlternateLayout"

/**
 * #57 "Both layouts": the other orientation's physical document size and its capture geometry,
 * both learned from a profile that orientation already produced (see
 * navic-reader-alternate-layout.js). The passive WebView renders at exactly this size.
 */
internal data class ReaderAlternateLayoutGeometry(
	val physicalWidth: Int,
	val physicalHeight: Int,
	val captureGeometry: ReaderPageTurnCaptureGeometry
) {
	val kind: ReaderPageTurnTransitionKind
		get() = if (captureGeometry.mode == ReaderPageTurnLayoutMode.Spread) {
			ReaderPageTurnTransitionKind.LandscapeSpreadSlide
		} else {
			ReaderPageTurnTransitionKind.PortraitSlide
		}
}

/** Only the chapter-local working set: the current chapter and the start of the next one. */
private val ReaderAlternateLayoutPriorities = setOf(
	ReaderPageRasterPriority.Current,
	ReaderPageRasterPriority.NextTransition,
	ReaderPageRasterPriority.PreviousTransition,
	ReaderPageRasterPriority.NextLookahead,
	ReaderPageRasterPriority.PreviousLookahead,
	ReaderPageRasterPriority.CurrentChapter,
	ReaderPageRasterPriority.NextChapter
)

internal fun readerAlternateLayoutTargetOrdinals(plan: ReaderPageRasterPreparationPlan): List<Int> =
	plan.targets
		.filter { target -> target.priority in ReaderAlternateLayoutPriorities }
		.map { target -> target.pageIndex }
		.filter { pageIndex -> pageIndex in 0 until plan.pageCount }
		.distinct()

/**
 * Crops a passive full-frame capture of the alternate layout to its reader surface and derives
 * the raster metadata a later live hydration validates. Owns [bitmap]: it is recycled whenever
 * no raster is returned.
 */
internal fun readerAlternateLayoutRaster(
	bitmap: Bitmap,
	capture: ReaderPassiveRasterGeometry,
	layout: ReaderAlternateLayoutGeometry,
	quality: ReaderPageBitmapQuality
): Pair<Bitmap, ReaderPageRasterMetadata>? {
	fun reject(): Pair<Bitmap, ReaderPageRasterMetadata>? {
		if (!bitmap.isRecycled) bitmap.recycle()
		return null
	}
	val surface = layout.captureGeometry.surfaceRectInWindow(
		0,
		0,
		layout.physicalWidth,
		layout.physicalHeight
	) ?: return reject()
	val sourceLeft = surface.left - capture.captureLeft
	val sourceTop = surface.top - capture.captureTop
	if (
		bitmap.isRecycled ||
		bitmap.width != capture.captureWidth ||
		bitmap.height != capture.captureHeight ||
		sourceLeft < 0 ||
		sourceTop < 0 ||
		sourceLeft + surface.width > bitmap.width ||
		sourceTop + surface.height > bitmap.height
	) return reject()
	val bitmapWidth = readerPageTurnAnimationBitmapDimension(surface.width, quality)
	val bitmapHeight = readerPageTurnAnimationBitmapDimension(surface.height, quality)
	val leaf = layout.captureGeometry.leafGeometry(bitmapWidth, bitmapHeight)
	if (leaf == null || !readerPageRasterGeometryMatches(layout.kind, leaf)) return reject()
	val surfaceBitmap = readerPassiveRasterSurfaceBitmap(
		bitmap = bitmap,
		paperReference = ReaderPagePaperRegistry.referenceFor(bitmap),
		sourceLeft = sourceLeft,
		sourceTop = sourceTop,
		surfaceWidth = surface.width,
		surfaceHeight = surface.height,
		bitmapWidth = bitmapWidth,
		bitmapHeight = bitmapHeight
	) ?: return reject()
	return surfaceBitmap to ReaderPageRasterMetadata(
		surfaceLeft = 0,
		surfaceTop = 0,
		surfaceRight = surfaceBitmap.width,
		surfaceBottom = surfaceBitmap.height,
		fullLeafRect = leaf.fullLeafRect?.toAlternateRasterRect(),
		leftLeafRect = leaf.leftLeafRect?.toAlternateRasterRect(),
		gutterRect = leaf.gutterRect?.toAlternateRasterRect(),
		rightLeafRect = leaf.rightLeafRect?.toAlternateRasterRect(),
		reverseFaceColor = readerPageTurnOpaqueColor(layout.captureGeometry.reverseFaceColorArgb)
	)
}

private fun ReaderPageTurnPixelRect.toAlternateRasterRect() = ReaderPageRasterRect(
	left = left,
	top = top,
	right = right,
	bottom = bottom
)

/**
 * Renders alternate-layout pages one at a time in a dedicated passive WebView and persists each
 * receipt-verified raster with its planned-descriptor alias. A page already aliased is skipped.
 * Any failure or staleness stops the run; nothing here touches the live deck.
 */
internal class ReaderAlternateLayoutRasterPrefetcher(
	private val session: ReaderPassiveRasterPrototypeSession<Bitmap>,
	private val manifestPort: ReaderPassiveRasterLiveManifestPort,
	private val bundleSource: ReaderPageTurnBundleSource,
	private val layout: ReaderAlternateLayoutGeometry,
	private val captureEpoch: Long,
	private val rasterGeneration: Long,
	private val mainHandler: Handler = Handler(Looper.getMainLooper())
) : AutoCloseable {
	private class Run(val ordinals: List<Int>, val onComplete: (persisted: Int, finished: Boolean) -> Unit) {
		var index = 0
		var persisted = 0
	}

	private val issuer = ReaderPassiveRasterManifestIssuer()
	private var run: Run? = null
	private var closed = false

	fun start(
		ordinals: List<Int>,
		onComplete: (persisted: Int, finished: Boolean) -> Unit
	): Boolean {
		if (closed || run != null || ordinals.isEmpty()) return false
		val started = Run(ordinals, onComplete)
		run = started
		next(started)
		return true
	}

	private fun isCurrent(target: Run): Boolean = !closed && run === target

	private fun next(target: Run) {
		if (!isCurrent(target)) return
		if (target.index >= target.ordinals.size) {
			finish(target, finished = true)
			return
		}
		val ordinal = target.ordinals[target.index]
		manifestPort.request(ordinal, captureEpoch, rasterGeneration, 0L) { resolution ->
			if (!isCurrent(target)) return@request
			val inputs = (resolution as? ReaderPassiveRasterManifestResolution.Available)?.inputs
			if (inputs == null) {
				Logger.i(ReaderAlternateLayoutTag, "Alternate layout manifest page=$ordinal stopped=$resolution")
				finish(target, finished = false)
				return@request
			}
			bundleSource.lookupPassiveRasterAlias(inputs.rasterDescriptor) { lookup ->
				if (!isCurrent(target)) return@lookupPassiveRasterAlias
				if (lookup is ReaderPassiveRasterAliasLookup.Hit) {
					advance(target, persisted = false)
				} else {
					capture(target, inputs)
				}
			}
		}
	}

	private fun capture(target: Run, inputs: ReaderPassiveRasterManifestInputs) {
		val manifest = issuer.issue(
			liveCommit = issuer.replaceCanonicalCommit(inputs.canonicalCommit),
			opaqueCaptureTarget = inputs.opaqueCaptureTarget,
			visualPageOrdinal = inputs.visualPageOrdinal
		)
		val committing = manifest != null && session.commit(manifest) committed@{ committed ->
			if (!isCurrent(target)) {
				committed?.release()
				return@committed
			}
			if (committed == null || !committed.capture { result -> onCaptured(target, result) }) {
				committed?.release()
				finish(target, finished = false)
			}
		}
		if (!committing) finish(target, finished = false)
	}

	private fun onCaptured(target: Run, result: ReaderPassiveRasterCaptureResult<Bitmap>?) {
		if (!isCurrent(target) || result == null) {
			result?.raster?.release()
			if (isCurrent(target)) finish(target, finished = false)
			return
		}
		val ordinal = result.manifest.visualPageOrdinal
		// The capture is admitted against a freshly issued manifest, so a page turn or relayout
		// since the commit fails admission instead of storing a stale raster.
		manifestPort.request(ordinal, captureEpoch, rasterGeneration, 0L) { resolution ->
			val inputs = (resolution as? ReaderPassiveRasterManifestResolution.Available)?.inputs
			if (!isCurrent(target) || inputs == null) {
				result.raster?.release()
				if (isCurrent(target)) finish(target, finished = false)
				return@request
			}
			bundleSource.persistAlternateLayoutRaster(
				capture = result,
				currentAuthority = ReaderPassiveRasterAdmissionAuthority(
					manifestInputs = inputs,
					activePassiveSessionId = session.passiveSessionId,
					expectedPassiveCommitSequence = result.expectedPassiveCommitSequence
				),
				layout = layout,
				isStillCurrent = { isCurrent(target) }
			) { persisted ->
				mainHandler.post {
					if (!isCurrent(target)) return@post
					if (persisted) advance(target, persisted = true) else finish(target, finished = false)
				}
			}
		}
	}

	private fun advance(target: Run, persisted: Boolean) {
		if (persisted) target.persisted += 1
		target.index += 1
		next(target)
	}

	private fun finish(target: Run, finished: Boolean) {
		if (run !== target) return
		run = null
		target.onComplete(target.persisted, finished)
	}

	fun cancel() {
		run = null
		session.cancelActiveCapture()
	}

	override fun close() {
		if (closed) return
		closed = true
		cancel()
		session.close()
	}
}

/**
 * Owns the "Both layouts" background work for one reader host: once the current layout's
 * preparation has drained, it plans the other orientation in the live document and prefetches
 * it through a short-lived passive WebView, which is closed as soon as the run ends.
 */
internal class ReaderAlternateLayoutPreparation(
	private val activityProvider: () -> Activity?,
	private val liveWebViewProvider: () -> WebView?,
	private val bundleSource: ReaderPageTurnBundleSource,
	private val captureEpochProvider: () -> Long
) {
	private var generation = 0L
	private var prefetcher: ReaderAlternateLayoutRasterPrefetcher? = null
	private var completedPlan: String? = null
	private val mainHandler = Handler(Looper.getMainLooper())

	val isRunning: Boolean
		get() = prefetcher != null

	fun onCurrentLayoutSettled() {
		if (prefetcher != null) return
		val webView = liveWebViewProvider()?.takeIf { it.isAttachedToWindow }
			?: return skipped("no-live-webview")
		val token = ++generation
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnAlternateLayoutPlan?.() ?? null)"
		) { encoded ->
			if (token != generation || prefetcher != null) return@evaluateJavascript
			val plan = readerPageRasterPreparationPlan(encoded, requireCurrentLive = false)
			val captureGeometry = plan?.captureGeometry
			val ordinals = plan?.let(::readerAlternateLayoutTargetOrdinals).orEmpty()
			val activity = activityProvider()
			if (captureGeometry == null || ordinals.isEmpty() || activity == null) {
				return@evaluateJavascript skipped(
					if (plan == null) "no-plan ${encoded?.take(120)}" else "no-targets activity=${activity != null}"
				)
			}
			// Turning pages inside the same chapter keeps the same working set, so it does not
			// start another passive WebView just to find every page already aliased.
			val signature = "${plan.pageCount}:$captureGeometry:${ordinals.sorted()}"
			if (signature == completedPlan) return@evaluateJavascript
			val density = webView.resources.displayMetrics.density
			val layout = ReaderAlternateLayoutGeometry(
				physicalWidth = (captureGeometry.viewportWidth * density).roundToInt(),
				physicalHeight = (captureGeometry.viewportHeight * density).roundToInt(),
				captureGeometry = captureGeometry
			)
			if (layout.physicalWidth <= 0 || layout.physicalHeight <= 0) {
				return@evaluateJavascript skipped("empty-geometry")
			}
			start(token, signature, activity, layout, ordinals)
		}
	}

	private fun skipped(reason: String) {
		Logger.i(ReaderAlternateLayoutTag, "Alternate layout prefetch skipped reason=$reason")
	}

	private fun start(
		token: Long,
		planSignature: String,
		activity: Activity,
		layout: ReaderAlternateLayoutGeometry,
		ordinals: List<Int>
	) {
		var created: ReaderAlternateLayoutRasterPrefetcher? = null
		val runtime = ReaderPassiveRasterWebViewHost(
			activity = activity,
			passiveSessionId = UUID.randomUUID().toString(),
			viewportGeometry = ReaderPassiveRasterGeometry(
				viewportWidth = layout.physicalWidth,
				viewportHeight = layout.physicalHeight,
				captureLeft = 0,
				captureTop = 0,
				captureRight = layout.physicalWidth,
				captureBottom = layout.physicalHeight
			),
			onRendererGone = {
				mainHandler.post { if (token == generation) cancel("renderer-gone") }
			},
			onReady = ready@{
				val owned = created ?: return@ready
				if (token != generation || prefetcher !== owned) return@ready
				Logger.i(ReaderAlternateLayoutTag, "Alternate layout prefetch started pages=${ordinals.size}")
				owned.start(ordinals) { persisted, finished ->
					Logger.i(
						ReaderAlternateLayoutTag,
						"Alternate layout prefetch ended persisted=$persisted finished=$finished"
					)
					if (token != generation || prefetcher !== owned) return@start
					// A stopped run is not retried for the same plan; a new plan (turn, rotation,
					// settings) starts afresh and skips every page already aliased.
					completedPlan = planSignature
					// Closed after the session's own callback stack unwinds.
					mainHandler.post { if (prefetcher === owned) closeCurrent() }
				}
			}
		)
		val session = ReaderPassiveRasterPrototypeSession(
			runtime = runtime,
			releaseRaster = { bitmap: Bitmap -> bitmap.takeUnless { it.isRecycled }?.recycle() }
		)
		created = ReaderAlternateLayoutRasterPrefetcher(
			session = session,
			manifestPort = ReaderPageLivePassiveRasterManifestPort(
				bridgeMethod = "pageTurnAlternatePassiveRasterManifestInputs",
				measuredSize = { layout.physicalWidth to layout.physicalHeight }
			) { liveWebViewProvider() },
			bundleSource = bundleSource,
			layout = layout,
			captureEpoch = captureEpochProvider(),
			rasterGeneration = bundleSource.currentGeneration()
		)
		prefetcher = created
	}

	/** Stops any planning or prefetch; the next settled current layout plans again. */
	fun cancel(reason: String) {
		generation += 1
		if (prefetcher != null) Logger.i(ReaderAlternateLayoutTag, "Alternate layout prefetch cancelled reason=$reason")
		closeCurrent()
	}

	private fun closeCurrent() {
		val current = prefetcher ?: return
		prefetcher = null
		current.close()
	}
}
