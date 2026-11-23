package paige.navic.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.reader.ReaderBookPreparationPhase
import paige.navic.reader.ReaderBookPreparationProgress
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.reader.ReaderPreparationOrientation
import paige.navic.util.core.Logger
import java.util.UUID
import kotlin.math.roundToInt

private const val ReaderAlternateLayoutTag = "ReaderAlternateLayout"

/** How often a whole-book run reads the JS alternate-profile build status (#70). */
private const val ReaderAlternateLayoutProfilePollMillis = 500L

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

/** #70 "Prepare pages": the whole alternate book, every page ordinal of its plan, in order. */
internal fun readerAlternateLayoutBookOrdinals(plan: ReaderPageRasterPreparationPlan): List<Int> =
	(0 until plan.pageCount).toList()

/**
 * Progress of a whole-book alternate run after its first [donePages] ordinals: a chapter counts
 * once all of its pages are done, since the run walks the book in page order.
 */
internal fun readerAlternateLayoutBookProgress(
	plan: ReaderPageRasterPreparationPlan,
	donePages: Int,
	phase: ReaderBookPreparationPhase,
	orientation: ReaderPreparationOrientation
): ReaderBookPreparationProgress {
	val chapters = plan.chapterRanges.filter { range -> range.pageCount > 0 }
	return ReaderBookPreparationProgress(
		phase = phase,
		preparedPages = donePages.coerceIn(0, plan.pageCount),
		plannedPages = plan.pageCount,
		preparedChapters = chapters.count { range -> range.endPageIndexExclusive <= donePages },
		plannedChapters = chapters.size,
		orientation = orientation
	)
}

/** The state of the JS alternate-profile build (#70): idle, building, ready or failed. */
internal data class ReaderAlternateProfileStatus(val state: String, val reason: String)

internal fun readerAlternateProfileStatus(encoded: String?): ReaderAlternateProfileStatus? {
	val raw = encoded.orEmpty().trim()
	return runCatching {
		val firstPass = Json.parseToJsonElement(raw)
		val json = if (raw.startsWith('"')) {
			Json.parseToJsonElement(firstPass.jsonPrimitive.contentOrNull.orEmpty()).jsonObject
		} else firstPass.jsonObject
		ReaderAlternateProfileStatus(
			state = json["state"]?.jsonPrimitive?.contentOrNull ?: return null,
			reason = json["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
		)
	}.getOrNull()
}

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

private const val ReaderAlternateLayoutPageAttempts = 5
private const val ReaderAlternateLayoutPageRetryMillis = 750L

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
	private class Run(
		val ordinals: List<Int>,
		val onProgress: (done: Int) -> Unit,
		val onComplete: (persisted: Int, finished: Boolean, descriptor: ReaderPageRasterDescriptor?) -> Unit
	) {
		var index = 0
		var persisted = 0
		var pageAttempts = 0
		var pageStartedAt = 0L
		var committedAt = 0L
		var capturedAt = 0L
		var descriptor: ReaderPageRasterDescriptor? = null
	}

	private val issuer = ReaderPassiveRasterManifestIssuer()
	private var run: Run? = null
	private var closed = false

	/**
	 * [onProgress] reports each page made durable (persisted or already aliased); [onComplete]
	 * receives the planned descriptor of the last durable page, which names its pagination.
	 */
	fun start(
		ordinals: List<Int>,
		onProgress: (done: Int) -> Unit = {},
		onComplete: (persisted: Int, finished: Boolean, descriptor: ReaderPageRasterDescriptor?) -> Unit
	): Boolean {
		if (closed || run != null || ordinals.isEmpty()) return false
		val started = Run(ordinals, onProgress, onComplete)
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
		target.pageStartedAt = SystemClock.elapsedRealtime()
		manifestPort.request(ordinal, captureEpoch, rasterGeneration, 0L) { resolution ->
			if (!isCurrent(target)) return@request
			val inputs = (resolution as? ReaderPassiveRasterManifestResolution.Available)?.inputs
			if (inputs == null) {
				fail(target, "manifest-$resolution")
				return@request
			}
			bundleSource.lookupPassiveRasterAlias(inputs.rasterDescriptor) { lookup ->
				if (!isCurrent(target)) return@lookupPassiveRasterAlias
				if (lookup is ReaderPassiveRasterAliasLookup.Hit) {
					target.descriptor = inputs.rasterDescriptor
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
			target.committedAt = SystemClock.elapsedRealtime()
			if (committed == null || !committed.capture { result -> onCaptured(target, result) }) {
				committed?.release()
				fail(target, if (committed == null) "commit-failed" else "capture-refused")
			}
		}
		if (!committing) fail(target, if (manifest == null) "manifest-not-issued" else "commit-refused")
	}

	private fun onCaptured(target: Run, result: ReaderPassiveRasterCaptureResult<Bitmap>?) {
		if (!isCurrent(target) || result == null) {
			result?.raster?.release()
			if (isCurrent(target)) fail(target, "capture-failed")
			return
		}
		target.capturedAt = SystemClock.elapsedRealtime()
		val ordinal = result.manifest.visualPageOrdinal
		// The capture is admitted against a freshly issued manifest, so a page turn or relayout
		// since the commit fails admission instead of storing a stale raster.
		manifestPort.request(ordinal, captureEpoch, rasterGeneration, 0L) { resolution ->
			val inputs = (resolution as? ReaderPassiveRasterManifestResolution.Available)?.inputs
			if (!isCurrent(target) || inputs == null) {
				result.raster?.release()
				if (isCurrent(target)) fail(target, "admission-manifest-$resolution")
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
					if (persisted) {
						val now = SystemClock.elapsedRealtime()
						Logger.i(
							ReaderAlternateLayoutTag,
							"Alternate layout page=$ordinal timing commit=${target.committedAt - target.pageStartedAt} " +
								"capture=${target.capturedAt - target.committedAt} " +
								"persist=${now - target.capturedAt} total=${now - target.pageStartedAt}"
						)
						target.descriptor = inputs.rasterDescriptor
						advance(target, persisted = true)
					} else {
						fail(target, "persist-failed")
					}
				}
			}
		}
	}

	/**
	 * A page that fails is tried again a few times before the run stops (#70, #71): a page turn
	 * in the live reader changes the commit a page was issued against, and a chapter opening
	 * can still be settling, so one miss is not a reason to abandon the whole book.
	 */
	private fun fail(target: Run, reason: String) {
		if (!isCurrent(target)) return
		if (target.pageAttempts >= ReaderAlternateLayoutPageAttempts) {
			finish(target, finished = false, reason = reason)
			return
		}
		target.pageAttempts += 1
		Logger.i(
			ReaderAlternateLayoutTag,
			"Alternate layout page=${target.ordinals.getOrNull(target.index)} retry=${target.pageAttempts} " +
				"reason=${reason.take(200)}"
		)
		mainHandler.postDelayed(
			{ if (isCurrent(target)) next(target) },
			ReaderAlternateLayoutPageRetryMillis * target.pageAttempts
		)
	}

	private fun advance(target: Run, persisted: Boolean) {
		if (persisted) target.persisted += 1
		target.pageAttempts = 0
		target.index += 1
		target.onProgress(target.index)
		if (isCurrent(target)) next(target)
	}

	private fun finish(target: Run, finished: Boolean, reason: String? = null) {
		if (run !== target) return
		if (!finished) {
			Logger.i(
				ReaderAlternateLayoutTag,
				"Alternate layout page=${target.ordinals.getOrNull(target.index)} index=${target.index} " +
					"stopped reason=${reason?.take(200)}"
			)
		}
		run = null
		target.onComplete(target.persisted, finished, target.descriptor)
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
 *
 * #70 "Prepare pages": [prepareBook] instead builds the other orientation's complete profile in
 * the live document (no rotation), prefetches every page of it and records it prepared. It
 * reports its own progress; while it is active the chapter-local prefetch does not run.
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

	private class Book(
		val orientation: ReaderPreparationOrientation,
		var listener: (ReaderBookPreparationProgress) -> Unit
	) {
		var progress = ReaderBookPreparationProgress(
			phase = ReaderBookPreparationPhase.Starting,
			orientation = orientation
		)
	}

	private var book: Book? = null

	val isRunning: Boolean
		get() = prefetcher != null

	/** Whether a whole-book run owns the progress (#70); the current layout's reports wait. */
	val bookPreparationActive: Boolean
		get() = book != null

	/**
	 * Prepares the whole book in [orientation], the one the reader is not showing. Asking again
	 * for the same orientation only re-reports, unless the last run was incomplete.
	 */
	fun prepareBook(
		orientation: ReaderPreparationOrientation,
		listener: (ReaderBookPreparationProgress) -> Unit
	) {
		val existing = book
		if (existing != null && existing.orientation == orientation) {
			existing.listener = listener
			if (existing.progress.phase != ReaderBookPreparationPhase.Incomplete) {
				listener(existing.progress)
				return
			}
		}
		cancel("book-preparation")
		val target = existing?.takeIf { it.orientation == orientation } ?: Book(orientation, listener)
		book = target
		reportBook(target, target.progress.copy(phase = ReaderBookPreparationPhase.Preparing))
		val token = generation
		val webView = liveWebViewProvider()?.takeIf { it.isAttachedToWindow }
			?: return failBook(target, "no-live-webview")
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.ensureAlternatePaginationProfile?.() ?? null)"
		) { encoded -> onBookProfileStatus(target, token, encoded) }
	}

	/** Retries an incomplete whole-book run; true when one is active. */
	fun retryBook(): Boolean {
		val target = book ?: return false
		if (target.progress.phase == ReaderBookPreparationPhase.Incomplete) {
			prepareBook(target.orientation, target.listener)
		}
		return true
	}

	fun stopBook() {
		if (book == null) return
		book = null
		cancel("book-preparation-ended")
	}

	private fun bookIsCurrent(target: Book, token: Long) = book === target && token == generation

	private fun reportBook(target: Book, progress: ReaderBookPreparationProgress) {
		if (book !== target) return
		target.progress = progress
		target.listener(progress)
	}

	private fun failBook(target: Book, reason: String) {
		Logger.i(
			ReaderAlternateLayoutTag,
			"Alternate layout book preparation incomplete orientation=${target.orientation.persistedValue} " +
				"reason=$reason"
		)
		reportBook(target, target.progress.copy(phase = ReaderBookPreparationPhase.Incomplete))
	}

	private fun onBookProfileStatus(target: Book, token: Long, encoded: String?) {
		if (!bookIsCurrent(target, token)) return
		val status = readerAlternateProfileStatus(encoded)
			?: return failBook(target, "no-profile-status ${encoded?.take(120)}")
		when (status.state) {
			"ready" -> planBook(target, token)
			"building", "idle" -> mainHandler.postDelayed({
				if (!bookIsCurrent(target, token)) return@postDelayed
				val webView = liveWebViewProvider()?.takeIf { it.isAttachedToWindow }
					?: return@postDelayed failBook(target, "no-live-webview")
				webView.evaluateJavascript(
					"JSON.stringify(window.NavicReaderBridge?.alternatePaginationProfileStatus?.() ?? null)"
				) { next -> onBookProfileStatus(target, token, next) }
			}, ReaderAlternateLayoutProfilePollMillis)
			else -> failBook(target, "profile-${status.state} ${status.reason}")
		}
	}

	private fun planBook(target: Book, token: Long) {
		val webView = liveWebViewProvider()?.takeIf { it.isAttachedToWindow }
			?: return failBook(target, "no-live-webview")
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnAlternateLayoutPlan?.() ?? null)"
		) { encoded ->
			if (!bookIsCurrent(target, token)) return@evaluateJavascript
			val plan = readerPageRasterPreparationPlan(encoded, requireCurrentLive = false)
				?: return@evaluateJavascript failBook(target, "no-plan ${encoded?.take(120)}")
			val layout = plan.captureGeometry?.let { geometry -> layoutGeometry(webView, geometry) }
				?: return@evaluateJavascript failBook(target, "empty-geometry")
			val activity = activityProvider() ?: return@evaluateJavascript failBook(target, "no-activity")
			val ordinals = readerAlternateLayoutBookOrdinals(plan)
			reportBook(
				target,
				readerAlternateLayoutBookProgress(plan, 0, ReaderBookPreparationPhase.Preparing, target.orientation)
			)
			start(
				token = token,
				activity = activity,
				layout = layout,
				ordinals = ordinals,
				onProgress = { done ->
					reportBook(
						target,
						readerAlternateLayoutBookProgress(plan, done, ReaderBookPreparationPhase.Preparing, target.orientation)
					)
				}
			) { _, finished, descriptor ->
				if (!finished || descriptor == null) {
					failBook(target, "prefetch-stopped")
					return@start
				}
				bundleSource.markAlternateBookPrepared(target.orientation, descriptor) { marked ->
					if (!bookIsCurrent(target, token)) return@markAlternateBookPrepared
					if (!marked) return@markAlternateBookPrepared failBook(target, "record-failed")
					reportBook(
						target,
						readerAlternateLayoutBookProgress(
							plan,
							plan.pageCount,
							ReaderBookPreparationPhase.Complete,
							target.orientation
						)
					)
				}
			}
		}
	}

	private fun layoutGeometry(
		webView: WebView,
		captureGeometry: ReaderPageTurnCaptureGeometry
	): ReaderAlternateLayoutGeometry? {
		val density = webView.resources.displayMetrics.density
		return ReaderAlternateLayoutGeometry(
			physicalWidth = (captureGeometry.viewportWidth * density).roundToInt(),
			physicalHeight = (captureGeometry.viewportHeight * density).roundToInt(),
			captureGeometry = captureGeometry
		).takeIf { layout -> layout.physicalWidth > 0 && layout.physicalHeight > 0 }
	}

	fun onCurrentLayoutSettled() {
		if (prefetcher != null || book != null) return
		val webView = liveWebViewProvider()?.takeIf { it.isAttachedToWindow }
			?: return skipped("no-live-webview")
		val token = ++generation
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnAlternateLayoutPlan?.() ?? null)"
		) { encoded ->
			if (token != generation || prefetcher != null || book != null) return@evaluateJavascript
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
			val layout = layoutGeometry(webView, captureGeometry)
				?: return@evaluateJavascript skipped("empty-geometry")
			start(token, activity, layout, ordinals) { _, _, _ ->
				// A stopped run is not retried for the same plan; a new plan (turn, rotation,
				// settings) starts afresh and skips every page already aliased.
				completedPlan = signature
			}
		}
	}

	private fun skipped(reason: String) {
		Logger.i(ReaderAlternateLayoutTag, "Alternate layout prefetch skipped reason=$reason")
	}

	private fun start(
		token: Long,
		activity: Activity,
		layout: ReaderAlternateLayoutGeometry,
		ordinals: List<Int>,
		onProgress: (done: Int) -> Unit = {},
		onEnded: (persisted: Int, finished: Boolean, descriptor: ReaderPageRasterDescriptor?) -> Unit
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
				val started = owned.start(
					ordinals,
					onProgress = { done -> if (token == generation && prefetcher === owned) onProgress(done) }
				) { persisted, finished, descriptor ->
					Logger.i(
						ReaderAlternateLayoutTag,
						"Alternate layout prefetch ended persisted=$persisted finished=$finished"
					)
					if (token != generation || prefetcher !== owned) return@start
					// Closed after the session's own callback stack unwinds.
					mainHandler.post { if (prefetcher === owned) closeCurrent() }
					onEnded(persisted, finished, descriptor)
				}
				if (!started) {
					closeCurrent()
					onEnded(0, false, null)
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

	/**
	 * Stops any planning or prefetch; the next settled current layout plans again. A whole-book
	 * run stopped by the host (pause, relayout) stays incomplete until it is retried.
	 */
	fun cancel(reason: String) {
		generation += 1
		if (prefetcher != null) Logger.i(ReaderAlternateLayoutTag, "Alternate layout prefetch cancelled reason=$reason")
		closeCurrent()
		val target = book
		if (
			target != null &&
			reason != "book-preparation" &&
			target.progress.phase != ReaderBookPreparationPhase.Complete &&
			target.progress.phase != ReaderBookPreparationPhase.Incomplete
		) failBook(target, "cancelled-$reason")
	}

	private fun closeCurrent() {
		val current = prefetcher ?: return
		prefetcher = null
		current.close()
	}
}
