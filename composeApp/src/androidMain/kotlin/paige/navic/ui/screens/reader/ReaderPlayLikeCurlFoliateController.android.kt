package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import karacken.curl.DeckRejectionReason
import karacken.curl.DeckReleaseReason
import karacken.curl.PageChange
import karacken.curl.PageImage
import karacken.curl.PageSurfaceListener
import karacken.curl.PageSurfaceView
import karacken.curl.RenderCapabilities
import karacken.curl.RenderFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.util.core.Logger

private const val ReaderPlayLikeCurlFoliateControllerTag = "ReaderPlayLikeCurlFoliate"

/**
 * Production bridge between Foliate's passive raster cache and the imported PlayLikeCurl surface.
 * Foliate remains the pagination authority; this controller owns only immutable raster leases,
 * deformation, and one exact visual-page settlement.
 */
internal class ReaderPlayLikeCurlFoliateController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val bundleSource: ReaderPageTurnBundleSource,
	private val onRequestPrewarm: () -> Unit
) {
	private class PreparedPages(
		val profile: ReaderPlayLikeCurlRasterProfile,
		val deck: ReaderPlayLikeCurlRasterDeck<Bitmap>
	) {
		val generations = mutableSetOf<Long>()
		var obsolete = false
	}

	val surfaceView = PageSurfaceView(host.context).apply {
		holder.setFormat(PixelFormat.TRANSLUCENT)
		setZOrderMediaOverlay(true)
		setVisible(true)
		alpha = 0f
		visibility = View.VISIBLE
	}

	private val rasterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val generationOwners = mutableMapOf<Long, PreparedPages>()
	private val preparedPageSets = mutableSetOf<PreparedPages>()
	private var rasterAdapter: ReaderPlayLikeCurlRasterAdapter<Bitmap>? = null
	private var activePages: PreparedPages? = null
	private var requestedProfile: ReaderPlayLikeCurlRasterProfile? = null
	private var capabilitiesAvailable = false
	private var enabled = false
	private var attached = false
	private var destroyed = false
	private var interactionReady = false
	private var bitmapQuality = ReaderPageBitmapQuality.Balanced
	private var snapshotKey = Int.MIN_VALUE
	private var currentOrdinal = 0
	private var pendingExactOrdinal: Int? = null
	private var preparationPhase = ReaderPagePreparationPhase.Idle
	private var requestGeneration = 0L
	private var nextDeckGeneration = 1L
	private var lastActivationTrace: String? = null

	init {
		surfaceView.setPageSurfaceListener(object : PageSurfaceListener {
			override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) {
				capabilitiesAvailable = true
				logActivationState(
					event = "capabilities-available",
					detail = "maxTextureSize=${capabilities.maxTextureSize}"
				)
				refreshPreparedDeck()
			}

			override fun onDeckPrepared(generationId: Long) {
				if (generationOwners[generationId] === activePages) {
					interactionReady = true
				}
				logActivationState("deck-prepared", "generation=$generationId")
			}

			override fun onDeckRejected(generationId: Long, reason: DeckRejectionReason) {
				val activeRejected = generationOwners[generationId] === activePages
				releaseGeneration(generationId)
				if (activeRejected) interactionReady = false
				Logger.w(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl deck rejected generation=$generationId reason=$reason"
				)
			}

			override fun onDeckReleased(generationId: Long, reason: DeckReleaseReason) {
				releaseGeneration(generationId)
			}

			override fun onSettlementStarted(
				generationId: Long,
				sourceLogicalPageId: String,
				targetLogicalPageId: String,
				pageChange: PageChange
			) {
				val pages = generationOwners[generationId] ?: activePages ?: return
				val targetOrdinal = targetOrdinal(pageChange, pages.profile)
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement started generation=$generationId " +
						"source=$sourceLogicalPageId target=$targetLogicalPageId " +
						"change=$pageChange targetOrdinal=$targetOrdinal"
				)
				submitLibraryDeck(pages, targetOrdinal)
			}

			override fun onSettlementCompleted(
				generationId: Long,
				currentLogicalPageId: String,
				currentPageOrdinal: Int,
				pageChange: PageChange
			) {
				if (pageChange == PageChange.NONE) {
					Logger.i(
						ReaderPlayLikeCurlFoliateControllerTag,
						"PlayLikeCurl settlement completed generation=$generationId " +
							"page=$currentLogicalPageId ordinal=$currentPageOrdinal change=$pageChange exactDispatch=false"
					)
					hideSurface()
					return
				}
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement completed generation=$generationId " +
						"page=$currentLogicalPageId ordinal=$currentPageOrdinal change=$pageChange exactDispatch=true"
				)
				currentOrdinal = currentPageOrdinal
				pendingExactOrdinal = currentPageOrdinal
				interactionReady = false
				dispatchExactVisualPage(currentPageOrdinal)
			}

			override fun onSettlementCancelled(generationId: Long, currentLogicalPageId: String) {
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement cancelled generation=$generationId page=$currentLogicalPageId"
				)
				hideSurface()
			}

			override fun onRenderFailure(failure: RenderFailure) {
				interactionReady = false
				hideSurface()
				Logger.e(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl render failure generation=${failure.generationId} reason=${failure.reason}"
				)
			}
		})
	}

	val isAvailable: Boolean
		get() = enabled && attached && interactionReady && pendingExactOrdinal == null

	fun setEnabled(value: Boolean) {
		if (enabled == value) return
		enabled = value
		logActivationState("enabled", "value=$value")
		if (value) {
			onRequestPrewarm()
			refreshPreparedDeck()
		} else {
			invalidate("disabled")
		}
	}

	fun updateBitmapQuality(value: String?) {
		val normalized = normalizeReaderPageBitmapQuality(value)
		if (bitmapQuality == normalized) return
		bitmapQuality = normalized
		invalidate("bitmap-quality-${normalized.persistedValue}")
		if (enabled) onRequestPrewarm()
	}

	fun setSnapshotKey(value: Int) {
		if (snapshotKey == value) return
		snapshotKey = value
		invalidate("snapshot-key")
		if (enabled) onRequestPrewarm()
	}

	fun onPreparationStateChanged(state: ReaderPagePreparationState) {
		preparationPhase = state.phase
		if (!enabled || destroyed) return
		logActivationState(
			event = "preparation-state",
			detail = buildString {
				append("phase=${state.phase}")
				append(" completed=${state.completedCount}/${state.requiredCount}")
				if (!state.error.isNullOrBlank()) append(" error=${state.error}")
			}
		)
		when (state.phase) {
			ReaderPagePreparationPhase.Ready -> {
				logActivationState("preparation-ready")
				refreshPreparedDeck()
			}
			ReaderPagePreparationPhase.Failed -> {
				interactionReady = false
				logActivationState("refresh-gated", "preparation-failed")
			}
			else -> Unit
		}
	}

	fun onHostAttached() {
		if (destroyed || attached) return
		attached = true
		surfaceView.attach()
		logActivationState("host-attached")
		if (enabled) {
			onRequestPrewarm()
			refreshPreparedDeck()
		}
	}

	fun onHostSizeChanged() {
		if (!enabled || destroyed) return
		invalidate("size-changed")
		onRequestPrewarm()
	}

	fun onHostContentReady() {
		if (!enabled || destroyed) return
		logActivationState("host-content-ready")
		refreshPreparedDeck()
	}

	fun onHostWindowHidden() {
		hideSurface()
		surfaceView.cancelGesture()
	}

	fun onPageTouchEvent(event: MotionEvent): Boolean {
		if (!isAvailable) return false
		return surfaceView.onPageTouchEvent(event)
	}

	fun showSurfaceForGesture() {
		if (!isAvailable) return
		surfaceView.alpha = 1f
	}

	fun cancelGesture() {
		surfaceView.cancelGesture()
		hideSurface()
	}

	fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {
		val normalized = pageIndex?.takeIf { it >= 0 } ?: return
		if (reason == "page-turn:exact") {
			if (pendingExactOrdinal != normalized) return
			pendingExactOrdinal = null
			currentOrdinal = normalized
			hideSurface()
			interactionReady = activePages?.generations?.isNotEmpty() == true
			onRequestPrewarm()
			refreshPreparedDeck()
			return
		}
		if (currentOrdinal == normalized && pendingExactOrdinal == null) return
		currentOrdinal = normalized
		invalidate("external-page-relocation")
		if (enabled) onRequestPrewarm()
	}

	fun invalidate(reason: String) {
		requestGeneration += 1L
		pendingExactOrdinal = null
		interactionReady = false
		hideSurface()
		surfaceView.cancelGesture()
		generationOwners.keys.toList().forEach(surfaceView::releaseDeck)
		activePages?.obsolete = true
		activePages = null
		preparedPageSets.forEach { pages -> pages.obsolete = true }
		rasterAdapter?.close()
		rasterAdapter = null
		requestedProfile = null
		preparedPageSets.toList().forEach(::closeIfUnused)
		Logger.i(ReaderPlayLikeCurlFoliateControllerTag, "PlayLikeCurl invalidated reason=$reason")
	}

	fun destroy() {
		if (destroyed) return
		destroyed = true
		enabled = false
		invalidate("destroyed")
		if (attached) {
			attached = false
			surfaceView.detach()
		}
		surfaceView.dispose()
		rasterScope.cancel()
	}

	private fun refreshPreparedDeck() {
		val gate = when {
			!enabled -> "disabled"
			!attached -> "host-detached"
			destroyed -> "destroyed"
			!capabilitiesAvailable -> "capabilities-unavailable"
			preparationPhase == ReaderPagePreparationPhase.Preparing -> "preparation-in-progress"
			else -> null
		}
		if (gate != null) {
			logActivationState("refresh-gated", gate)
			return
		}
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
		if (webView == null) {
			logActivationState("refresh-gated", "webview-unavailable")
			return
		}
		val request = ++requestGeneration
		val centerExpression = pendingExactOrdinal?.toString() ?: currentOrdinal.toString()
		logActivationState("refresh-requested", "request=$request center=$centerExpression")
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterPreparationPlan?.(" +
				"$centerExpression) ?? null)"
		) { encoded ->
			if (!isRequestActive(request, webView)) {
				logActivationState("refresh-gated", "stale-request=$request")
				return@evaluateJavascript
			}
			val plan = readerPageRasterPreparationPlan(encoded)
			if (plan == null) {
				logActivationState("refresh-gated", "preparation-plan-unavailable")
				requestPrewarmIfIdle("preparation-plan-unavailable")
				return@evaluateJavascript
			}
			currentOrdinal = plan.centerPageIndex
			val orientation = if (plan.layoutMode == "spread") {
				ReaderPlayLikeCurlOrientation.Landscape
			} else {
				ReaderPlayLikeCurlOrientation.Portrait
			}
			val profile = ReaderPlayLikeCurlRasterProfile(
				sourceIdentity = "${webView.url.orEmpty()}#$snapshotKey",
				orientation = orientation,
				quality = bitmapQuality,
				pageCount = plan.pageCount,
				readerDirection = plan.readerDirection,
				spreadAnchorParity = Math.floorMod(plan.centerPageIndex, 2),
				rasterGeneration = bundleSource.currentGeneration()
			)
			prepareProfile(request, profile, plan.centerPageIndex)
		}
	}

	private fun prepareProfile(
		request: Long,
		profile: ReaderPlayLikeCurlRasterProfile,
		centerOrdinal: Int
	) {
		if (requestedProfile != profile) {
			activePages?.let { pages ->
				pages.obsolete = true
				closeIfUnused(pages)
			}
			activePages = null
			rasterAdapter?.close()
			rasterAdapter = ReaderPlayLikeCurlRasterAdapter(
				scope = rasterScope,
				loader = ReaderPlayLikeCurlFoliateRasterLoader(bundleSource, profile),
				release = Bitmap::recycle
			)
			requestedProfile = profile
		}
		val adapter = rasterAdapter ?: return
		val pageIndices = readerPlayLikeCurlLibraryDeckPageIndices(
			orientation = profile.orientation,
			currentOrdinal = centerOrdinal,
			pageCount = profile.pageCount
		)
		val preparation = adapter.prepare(profile, pageIndices)
		rasterScope.launch {
			val deck = preparation.await()
			if (deck == null) {
				host.post {
					if (request == requestGeneration && enabled && !destroyed) {
						interactionReady = false
						logActivationState(
							"refresh-gated",
							"raster-deck-unavailable phase=$preparationPhase"
						)
						requestPrewarmIfIdle("raster-deck-unavailable")
					}
				}
				return@launch
			}
			host.post {
				if (request != requestGeneration || !enabled || destroyed || requestedProfile != profile) {
					deck.close()
					return@post
				}
				activePages?.let { previous ->
					previous.obsolete = true
					closeIfUnused(previous)
				}
				val pages = PreparedPages(profile, deck)
				preparedPageSets += pages
				activePages = pages
				currentOrdinal = centerOrdinal.coerceIn(0, profile.pageCount - 1)
				interactionReady = false
				submitLibraryDeck(pages, currentOrdinal)
			}
		}
	}

	private fun submitLibraryDeck(pages: PreparedPages, ordinal: Int) {
		if (pages.obsolete || destroyed || !enabled || !attached) return
		val generationId = nextDeckGeneration++
		val deck = runCatching {
			readerPlayLikeCurlLibraryDeck(
				orientation = pages.profile.orientation,
				generationId = generationId,
				currentOrdinal = ordinal,
				pageCount = pages.profile.pageCount,
				page = { pageGenerationId, pageOrdinal ->
					pages.page(pageGenerationId, pageOrdinal)
				}
			)
		}.getOrElse { error ->
			interactionReady = false
			Logger.e(
				ReaderPlayLikeCurlFoliateControllerTag,
				"Failed to build PlayLikeCurl deck ordinal=$ordinal phase=$preparationPhase",
				error
			)
			requestPrewarmIfIdle("deck-build-failed")
			return
		}
		pages.generations += generationId
		generationOwners[generationId] = pages
		logActivationState(
			event = "deck-submitted",
			detail = "generation=$generationId ordinal=$ordinal orientation=${pages.profile.orientation}"
		)
		surfaceView.submitDeck(deck)
	}

	private fun PreparedPages.page(generationId: Long, ordinal: Int): PageImage<Bitmap> {
		val bitmap = checkNotNull(deck.value(ordinal)) {
			"Missing prepared Foliate page $ordinal for ${profile.orientation}"
		}
		return PageImage(
			generationId,
			"${profile.sourceIdentity}:${profile.orientation.name.lowercase()}:$ordinal",
			ordinal,
			bitmap.width,
			bitmap.height,
			bitmap
		)
	}

	private fun targetOrdinal(
		pageChange: PageChange,
		profile: ReaderPlayLikeCurlRasterProfile
	): Int {
		val step = if (profile.orientation == ReaderPlayLikeCurlOrientation.Landscape) 2 else 1
		return when (pageChange) {
			PageChange.PREVIOUS -> (currentOrdinal - step).coerceAtLeast(0)
			PageChange.NEXT -> (currentOrdinal + step).coerceAtMost(profile.pageCount - 1)
			PageChange.NONE -> currentOrdinal
		}
	}

	private fun dispatchExactVisualPage(pageIndex: Int) {
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow } ?: run {
			pendingExactOrdinal = null
			hideSurface()
			return
		}
		val token = "navic-playlikecurl-settle-${nextDeckGeneration++}"
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl exact page dispatched pageIndex=$pageIndex token=$token"
		)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.dispatch?.({ type: 'goToVisualPage', " +
				"pageIndex: $pageIndex, settleToken: ${JSONObject.quote(token)} })"
		) { }
	}

	private fun releaseGeneration(generationId: Long) {
		val pages = generationOwners.remove(generationId) ?: return
		pages.generations -= generationId
		closeIfUnused(pages)
	}

	private fun closeIfUnused(pages: PreparedPages) {
		if (!pages.obsolete || pages.generations.isNotEmpty()) return
		preparedPageSets -= pages
		pages.deck.close()
	}

	private fun hideSurface() {
		surfaceView.alpha = 0f
	}

	private fun requestPrewarmIfIdle(reason: String) {
		if (preparationPhase == ReaderPagePreparationPhase.Idle) {
			logActivationState("prewarm-requested", reason)
			onRequestPrewarm()
		} else {
			logActivationState("refresh-gated", "$reason phase=$preparationPhase")
		}
	}

	private fun logActivationState(event: String, detail: String? = null) {
		val trace = buildString {
			append("activation event=")
			append(event)
			if (!detail.isNullOrBlank()) {
				append(" detail=")
				append(detail)
			}
			append(" enabled=")
			append(enabled)
			append(" attached=")
			append(attached)
			append(" destroyed=")
			append(destroyed)
			append(" capabilities=")
			append(capabilitiesAvailable)
			append(" interactionReady=")
			append(interactionReady)
			append(" pendingExactOrdinal=")
			append(pendingExactOrdinal)
			append(" activePages=")
			append(activePages != null)
			append(" requestedProfile=")
			append(requestedProfile != null)
			append(" requestGeneration=")
			append(requestGeneration)
			append(" preparationPhase=")
			append(preparationPhase)
		}
		if (trace == lastActivationTrace) return
		lastActivationTrace = trace
		Logger.i(ReaderPlayLikeCurlFoliateControllerTag, trace)
	}

	private fun isRequestActive(request: Long, webView: WebView): Boolean =
		request == requestGeneration &&
			enabled &&
			attached &&
			!destroyed &&
			webView.isAttachedToWindow
}
