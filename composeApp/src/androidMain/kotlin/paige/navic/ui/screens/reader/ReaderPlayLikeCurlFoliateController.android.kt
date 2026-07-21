package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import karacken.curl.DeckRejectionReason
import karacken.curl.DeckReleaseReason
import karacken.curl.GestureRejectionReason
import karacken.curl.PageChange
import karacken.curl.PageImage
import karacken.curl.PageSurfaceListener
import karacken.curl.PageSurfaceView
import karacken.curl.RenderCapabilities
import karacken.curl.RenderFailure
import karacken.curl.RenderFailureReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPageOperationPolicy
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.util.core.Logger

private const val ReaderPlayLikeCurlFoliateControllerTag = "ReaderPlayLikeCurlFoliate"

internal enum class ReaderDeckSubmissionRole {
	Active,
	Pending
}

internal fun readerPlayLikeCurlPortraitSurfaceWidth(
	hostWidth: Int,
	hostHeight: Int,
	pageBitmapWidth: Int,
	pageBitmapHeight: Int
): Int {
	if (hostWidth <= 0 || hostHeight <= 0 || pageBitmapWidth <= 0 || pageBitmapHeight <= 0) {
		return hostWidth.coerceAtLeast(1)
	}
	val scaledWidth = (hostHeight.toDouble() * pageBitmapWidth / pageBitmapHeight)
		.toInt()
		.coerceAtLeast(1)
	return scaledWidth.coerceAtMost(hostWidth)
}

/**
 * Production bridge between Foliate's passive raster cache and the imported PlayLikeCurl surface.
 * Foliate remains the pagination authority; this controller owns only immutable raster leases,
 * deformation, and one exact visual-page settlement.
 */
internal class ReaderPlayLikeCurlFoliateController(
	private val host: ViewGroup,
	private val webViewProvider: () -> WebView?,
	private val bundleSource: ReaderPageTurnBundleSource,
	private val onRequestPrewarm: () -> Unit,
	private val onRequestRasterRepair: (Int, (Boolean) -> Unit) -> Unit,
	private val onGestureTerminal: (
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: String
	) -> Unit,
	private val onReadinessStateChange: (ReaderPageRendererReadinessState) -> Unit = {}
) {
	private class PreparedPages(
		val profile: ReaderPlayLikeCurlRasterProfile,
		val deck: ReaderPlayLikeCurlRasterDeck<Bitmap>,
		val centerOrdinal: Int
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
	private val generationRoles = mutableMapOf<Long, ReaderDeckSubmissionRole>()
	private val preparedDeckGenerations = mutableSetOf<Long>()
	private val preparedPageSets = mutableSetOf<PreparedPages>()
	private var rasterAdapter: ReaderPlayLikeCurlRasterAdapter<Bitmap>? = null
	private var activePages: PreparedPages? = null
	private var requestedProfile: ReaderPlayLikeCurlRasterProfile? = null
	private var capabilitiesAvailable = false
	private var enabled = false
	private var attached = false
	private var destroyed = false
	private var readinessState = ReaderPageRendererReadinessState()
	private var pageOperationPolicy = readerPageOperationPolicy(
		ReaderPageReadinessState(
			textureDeck = readinessState.textureDeck,
			interaction = readinessState.interaction
		)
	)
	private var hasPreparedDeckBefore = false
	private var bitmapQuality = ReaderPageBitmapQuality.Balanced
	private var snapshotKey = Int.MIN_VALUE
	private var currentOrdinal = 0
	private var authoritativeLocationReady = false
	private var pendingExactOrdinal: Int? = null
	private var preparationPhase = ReaderPagePreparationPhase.Idle
	private var requestGeneration = 0L
	private var decodedRefillGeneration = 0L
	private var decodedRefillCenterOrdinal: Int? = null
	private val rasterRepairRequests = mutableSetOf<Pair<ReaderPlayLikeCurlRasterProfile, Int>>()
	private var nextDeckGeneration = 1L
	private var activeGestureId: Long? = null
	private var activeDeckGenerationId: Long? = null
	private var pendingDeckGenerationId: Long? = null
	private var pendingDeckOrdinal: Int? = null
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
				preparedDeckGenerations += generationId
				if (generationId == activeDeckGenerationId) {
					hasPreparedDeckBefore = true
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = preparedInteractionState(),
						reason = "deck-prepared:$generationId"
					)
				}
				logActivationState("deck-prepared", "generation=$generationId")
			}

			override fun onDeckRejected(generationId: Long, reason: DeckRejectionReason) {
				val activeRejected = generationId == activeDeckGenerationId
				val pendingRejected = generationId == pendingDeckGenerationId
				releaseGeneration(generationId)
				if (pendingRejected) {
					pendingDeckGenerationId = null
					pendingDeckOrdinal = null
				}
				if (activeRejected) {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Failed,
						interaction = ReaderPageInteractionState.Failed,
						reason = "deck-rejected:$generationId:$reason"
					)
				}
				Logger.w(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl deck rejected generation=$generationId reason=$reason"
				)
			}

			override fun onDeckReleased(generationId: Long, reason: DeckReleaseReason) {
				releaseGeneration(generationId)
			}

			override fun onGestureRejected(
				gestureId: Long,
				generationId: Long,
				reason: GestureRejectionReason
			) {
				val outcome = when (reason) {
					GestureRejectionReason.SETTLEMENT_RUNNING ->
						ReaderPageGestureTerminalOutcome.RejectedSettling
					else -> ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
				}
				finishGesture(
					gestureId,
					outcome,
					"renderer-rejected generation=$generationId reason=$reason"
				)
			}

			override fun onGestureCancelled(gestureId: Long, generationId: Long) {
				finishGesture(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledByUser,
					"renderer-cancelled generation=$generationId"
				)
			}

			override fun onSettlementStarted(
				gestureId: Long,
				generationId: Long,
				sourceLogicalPageId: String,
				targetLogicalPageId: String,
				pageChange: PageChange
			) {
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Settling,
					interaction = ReaderPageInteractionState.Settling,
					reason = "settlement-started:$gestureId"
				)
				val pages = activePages
				val targetOrdinal = pages?.let { prepared ->
					readerPlayLikeCurlSettlementTargetOrdinal(
						orientation = prepared.profile.orientation,
						currentOrdinal = currentOrdinal,
						pageCount = prepared.profile.pageCount,
						pageChange = pageChange
					)
				}
				if (pages != null && targetOrdinal != null) {
					refillDecodedWorkingSet(targetOrdinal, "settlement-started:$gestureId")
					submitLibraryDeck(
						pages = pages,
						ordinal = targetOrdinal,
						role = ReaderDeckSubmissionRole.Pending
					)
				}
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement started gestureId=$gestureId generation=$generationId " +
						"source=$sourceLogicalPageId target=$targetLogicalPageId " +
						"change=$pageChange"
				)
			}

			override fun onSettlementCompleted(
				gestureId: Long,
				generationId: Long,
				currentLogicalPageId: String,
				currentPageOrdinal: Int,
				pageChange: PageChange
			) {
				if (pageChange == PageChange.NONE) {
					discardPendingDeck("settlement-none")
					Logger.i(
						ReaderPlayLikeCurlFoliateControllerTag,
						"PlayLikeCurl settlement completed generation=$generationId " +
							"page=$currentLogicalPageId ordinal=$currentPageOrdinal change=$pageChange exactDispatch=false"
					)
					hideSurface()
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = preparedInteractionState(),
						reason = "settlement-completed-none:$gestureId"
					)
					finishGesture(
						gestureId,
						ReaderPageGestureTerminalOutcome.CancelledByUser,
						"settlement-completed change=NONE ordinal=$currentPageOrdinal"
					)
					return
				}
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement completed generation=$generationId " +
						"page=$currentLogicalPageId ordinal=$currentPageOrdinal change=$pageChange exactDispatch=true"
				)
				currentOrdinal = currentPageOrdinal
				pendingExactOrdinal = currentPageOrdinal
				promotePendingDeck(currentPageOrdinal)
				finishGesture(
					gestureId,
					if (pageChange == PageChange.NEXT) {
						ReaderPageGestureTerminalOutcome.CommittedForward
					} else {
						ReaderPageGestureTerminalOutcome.CommittedBackward
					},
					"settlement-completed change=$pageChange ordinal=$currentPageOrdinal"
				)
				dispatchExactVisualPage(currentPageOrdinal)
			}

			override fun onSettlementCancelled(
				gestureId: Long,
				generationId: Long,
				currentLogicalPageId: String
			) {
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement cancelled generation=$generationId page=$currentLogicalPageId"
				)
				discardPendingDeck("settlement-cancelled")
				finishGesture(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledByUser,
					"settlement-cancelled generation=$generationId page=$currentLogicalPageId"
				)
				if (
					readinessState.textureDeck != ReaderTextureDeckState.Failed &&
					readinessState.interaction != ReaderPageInteractionState.Failed
				) {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = preparedInteractionState(),
						reason = "settlement-cancelled:$gestureId"
					)
				}
				hideSurface()
			}

			override fun onRenderFailure(failure: RenderFailure) {
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Failed,
					interaction = ReaderPageInteractionState.Failed,
					reason = "render-failure:${failure.generationId}:${failure.reason}"
				)
				finishActiveGesture(
					ReaderPageGestureTerminalOutcome.FailedRenderer,
					"render-failure generation=${failure.generationId} reason=${failure.reason}"
				)
				if (!failure.isRecoverable()) {
					cancelRendererWork(
						if (failure.reason == RenderFailureReason.CONTEXT) {
							ReaderPageLifecycleCancellationReason.UnsafeContextLoss
						} else {
							ReaderPageLifecycleCancellationReason.GlFailure
						}
					)
				}
				hideSurface()
				Logger.e(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl render failure generation=${failure.generationId} reason=${failure.reason}"
				)
			}
		})
	}

	val isAvailable: Boolean
		get() = enabled && attached &&
			pageOperationPolicy.newPointer is ReaderPageNewPointerDecision.Accept

	fun setPageOperationPolicy(policy: ReaderPageOperationPolicy) {
		pageOperationPolicy = policy
	}

	private fun unavailableGestureOutcome(): ReaderPageGestureTerminalOutcome =
		(pageOperationPolicy.newPointer as? ReaderPageNewPointerDecision.Reject)?.outcome
			?: ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable

	fun setEnabled(
		value: Boolean,
		cancellationReason: ReaderPageLifecycleCancellationReason =
			ReaderPageLifecycleCancellationReason.CanvasDisabled
	) {
		if (enabled == value) return
		enabled = value
		logActivationState("enabled", "value=$value")
		if (value) {
			onRequestPrewarm()
			refreshPreparedDeck()
		} else {
			invalidate("disabled", cancellationReason = cancellationReason)
		}
	}

	fun updateBitmapQuality(value: String?) {
		val normalized = normalizeReaderPageBitmapQuality(value)
		if (bitmapQuality == normalized) return
		bitmapQuality = normalized
		invalidate(
			reason = "bitmap-quality-${normalized.persistedValue}",
			profileRegeneration = true,
			cancellationReason = ReaderPageLifecycleCancellationReason.RasterProfileInvalidated
		)
		if (enabled) onRequestPrewarm()
	}

	fun setSnapshotKey(value: Int) {
		if (snapshotKey == value) return
		snapshotKey = value
		invalidate(
			reason = "snapshot-key",
			profileRegeneration = true,
			cancellationReason = ReaderPageLifecycleCancellationReason.RasterProfileInvalidated
		)
		if (enabled) onRequestPrewarm()
	}

	fun onPreparationStateChanged(state: ReaderPagePreparationState) {
		setPageOperationPolicy(state.operationPolicy)
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
				if (readinessState.textureDeck == ReaderTextureDeckState.Ready) {
					updateReadiness(
						interaction = ReaderPageInteractionState.Ready,
						reason = "raster-preparation-ready"
					)
				}
				if (activePages == null) refreshPreparedDeck()
			}
			ReaderPagePreparationPhase.Failed -> {
				updateReadiness(
					interaction = ReaderPageInteractionState.Failed,
					reason = "raster-preparation-failed"
				)
				logActivationState("refresh-gated", "preparation-failed")
			}
			ReaderPagePreparationPhase.Preparing -> {
				if (
					readinessState.textureDeck == ReaderTextureDeckState.Settling ||
					readinessState.interaction == ReaderPageInteractionState.Settling
				) {
					logActivationState("readiness-preserved", "raster-preparation-during-settlement")
				} else if (readinessState.textureDeck == ReaderTextureDeckState.Ready) {
					updateReadiness(
						interaction = ReaderPageInteractionState.BackgroundPrefetch,
						reason = "raster-background-prefetch"
					)
				} else {
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Preparing,
						interaction = blockingPreparationState(),
						reason = "raster-preparation-blocking"
					)
				}
			}
			ReaderPagePreparationPhase.Idle -> Unit
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
		invalidate(
			reason = "size-changed",
			profileRegeneration = true,
			cancellationReason = ReaderPageLifecycleCancellationReason.RasterProfileInvalidated
		)
		onRequestPrewarm()
	}

	fun onHostContentReady() {
		if (!enabled || destroyed) return
		logActivationState("host-content-ready")
		refreshPreparedDeck()
	}

	fun onHostWindowHidden() {
		hideSurface()
		cancelActiveGesture(ReaderPageLifecycleCancellationReason.CanvasDisabled)
	}

	fun onPageTouchEvent(event: MotionEvent, gestureId: Long): Boolean {
		if (event.actionMasked == MotionEvent.ACTION_DOWN) {
			activeGestureId = gestureId
			if (!isAvailable) {
				finishGesture(
					gestureId,
					unavailableGestureOutcome(),
					"touch-rejected action=${event.actionMasked} controller-unavailable"
				)
				return false
			}
		}
		return surfaceView.onPageTouchEvent(event, gestureId)
	}

	fun turn(pageChange: PageChange, gestureId: Long): Boolean {
		activeGestureId = gestureId
		if (!isAvailable) {
			Logger.i(
				ReaderPlayLikeCurlFoliateControllerTag,
				"PlayLikeCurl tap turn change=$pageChange accepted=false reason=not-available"
			)
			finishGesture(
				gestureId,
				unavailableGestureOutcome(),
				"tap-turn-rejected change=$pageChange controller-unavailable"
			)
			return false
		}
		surfaceView.alpha = 1f
		val accepted = surfaceView.turn(pageChange, gestureId)
		if (!accepted) {
			hideSurface()
			if (activeGestureId == gestureId) {
				finishGesture(
					gestureId,
					ReaderPageGestureTerminalOutcome.RejectedBoundary,
					"tap-turn-rejected change=$pageChange boundary"
				)
			}
		}
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl tap turn change=$pageChange accepted=$accepted"
		)
		return accepted
	}

	fun showSurfaceForGesture() {
		if (!isAvailable) return
		surfaceView.alpha = 1f
	}

	fun cancelGesture(gestureId: Long) {
		activeGestureId = gestureId
		surfaceView.cancelGesture(gestureId)
		if (activeGestureId == gestureId) {
			finishGesture(
				gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser,
				"controller-cancel"
			)
		}
		hideSurface()
	}

	private fun cancelRendererWork(cancellationReason: ReaderPageLifecycleCancellationReason) {
		surfaceView.cancelGesture()
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl renderer work cancelled reason=$cancellationReason"
		)
	}

	fun cancelActiveGesture(cancellationReason: ReaderPageLifecycleCancellationReason) {
		val gestureId = activeGestureId
		if (gestureId != null) {
			cancelGesture(gestureId)
		} else {
			cancelRendererWork(cancellationReason)
			hideSurface()
		}
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl active gesture cancelled reason=$cancellationReason"
		)
	}

	fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {
		val normalized = pageIndex?.takeIf { it >= 0 } ?: return
		authoritativeLocationReady = true
		if (reason == "page-turn:exact") {
			if (pendingExactOrdinal != normalized) return
			pendingExactOrdinal = null
			currentOrdinal = normalized
			if (activeGestureId == null) hideSurface()
			if (activePages?.generations?.isNotEmpty() == true) {
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Ready,
					interaction = ReaderPageInteractionState.Ready,
					reason = "foliate-exact-settlement:$normalized"
				)
			}
			refillDecodedWorkingSet(normalized, "foliate-exact-settlement")
			return
		}
		if (currentOrdinal == normalized && pendingExactOrdinal == null) return
		currentOrdinal = normalized
		invalidate(
			"external-page-relocation",
			cancellationReason = ReaderPageLifecycleCancellationReason.RendererReplaced
		)
		if (enabled) onRequestPrewarm()
	}

	fun invalidate(
		reason: String,
		profileRegeneration: Boolean = false,
		cancellationReason: ReaderPageLifecycleCancellationReason? = null
	) {
		cancellationReason?.let(::cancelActiveGesture)
		requestGeneration += 1L
		decodedRefillGeneration += 1L
		decodedRefillCenterOrdinal = null
		rasterRepairRequests.clear()
		pendingExactOrdinal = null
		updateReadiness(
			textureDeck = ReaderTextureDeckState.Empty,
			interaction = if (profileRegeneration && hasPreparedDeckBefore) {
				ReaderPageInteractionState.BlockingProfileRegeneration
			} else {
				ReaderPageInteractionState.BlockingInitialPreparation
			},
			reason = "invalidated:$reason"
		)
		hideSurface()
		generationOwners.keys.toList().forEach(surfaceView::releaseDeck)
		generationRoles.clear()
		preparedDeckGenerations.clear()
		activeDeckGenerationId = null
		pendingDeckGenerationId = null
		pendingDeckOrdinal = null
		activePages?.obsolete = true
		activePages = null
		preparedPageSets.forEach { pages -> pages.obsolete = true }
		rasterAdapter?.close()
		rasterAdapter = null
		requestedProfile = null
		preparedPageSets.toList().forEach(::closeIfUnused)
		Logger.i(ReaderPlayLikeCurlFoliateControllerTag, "PlayLikeCurl invalidated reason=$reason")
	}

	fun destroy(cancellationReason: ReaderPageLifecycleCancellationReason) {
		if (destroyed) return
		destroyed = true
		enabled = false
		invalidate("destroyed", cancellationReason = cancellationReason)
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
			!authoritativeLocationReady -> "authoritative-location-unavailable"
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
			if (preparationPhase == ReaderPagePreparationPhase.Preparing) {
				logActivationState("refresh-gated", "preparation-in-progress-after-plan")
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
				spreadAnchorParity = if (orientation == ReaderPlayLikeCurlOrientation.Landscape) {
					Math.floorMod(plan.centerPageIndex, 2)
				} else {
					0
				},
				rasterGeneration = bundleSource.currentGeneration()
			)
			prepareProfile(request, profile, plan.centerPageIndex)
		}
	}

	private fun refillDecodedWorkingSet(centerOrdinal: Int, reason: String) {
		val profile = requestedProfile ?: return
		val adapter = rasterAdapter ?: return
		val pageIndices = readerPlayLikeCurlPreparedPageIndices(
			orientation = profile.orientation,
			currentOrdinal = centerOrdinal,
			pageCount = profile.pageCount
		)
		val pages = activePages
		if (
			pages?.profile == profile &&
			pages.centerOrdinal == centerOrdinal &&
			pages.deck.pageIndices.containsAll(pageIndices)
		) return
		if (decodedRefillCenterOrdinal == centerOrdinal) return
		val refill = ++decodedRefillGeneration
		decodedRefillCenterOrdinal = centerOrdinal
		val startedAtNanos = System.nanoTime()
		logActivationState(
			event = "decoded-refill-started",
			detail = "refill=$refill center=$centerOrdinal reason=$reason " +
				"pages=${pageIndices.joinToString(",")}"
		)
		val preparation = adapter.prepare(profile, pageIndices)
		rasterScope.launch {
			val deck = preparation.await()
			host.post {
				if (
					deck == null ||
					refill != decodedRefillGeneration ||
					!enabled ||
					destroyed ||
					requestedProfile != profile
				) {
					deck?.close()
					if (refill == decodedRefillGeneration) decodedRefillCenterOrdinal = null
					if (deck == null && refill == decodedRefillGeneration) {
						logActivationState(
							event = "decoded-refill-deferred",
							detail = "refill=$refill center=$centerOrdinal reason=$reason"
						)
					}
					return@post
				}
				decodedRefillCenterOrdinal = null
				activePages?.let { previous ->
					previous.obsolete = true
					closeIfUnused(previous)
				}
				val replacement = PreparedPages(profile, deck, centerOrdinal)
				preparedPageSets += replacement
				activePages = replacement
				logActivationState(
					event = "decoded-refill-completed",
					detail = "refill=$refill center=$centerOrdinal reason=$reason " +
						"pages=${pageIndices.joinToString(",")} " +
						"elapsedMillis=${elapsedMillis(startedAtNanos)}"
				)
				if (activeDeckGenerationId == null) {
					currentOrdinal = centerOrdinal.coerceIn(0, profile.pageCount - 1)
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Preparing,
						interaction = blockingPreparationState(),
						reason = "decoded-repair-submitting:$refill"
					)
					submitLibraryDeck(
						pages = replacement,
						ordinal = currentOrdinal,
						role = ReaderDeckSubmissionRole.Active
					)
				}
			}
		}
	}

	private fun requestRasterRepair(
		sourcePageIndex: Int,
		profile: ReaderPlayLikeCurlRasterProfile
	) {
		val key = profile to sourcePageIndex
		if (!rasterRepairRequests.add(key)) return
		val refillCenter = decodedRefillCenterOrdinal ?: pendingExactOrdinal ?: currentOrdinal
		logActivationState(
			event = "page-repair-requested",
			detail = "source=$sourcePageIndex center=$refillCenter " +
				"profileGeneration=${profile.rasterGeneration}"
		)
		onRequestRasterRepair(sourcePageIndex) { success ->
			host.post {
				rasterRepairRequests.remove(key)
				if (!success || destroyed || !enabled || requestedProfile != profile) {
					logActivationState(
						event = "page-repair-deferred",
						detail = "source=$sourcePageIndex center=$refillCenter success=$success"
					)
					return@post
				}
				logActivationState(
					event = "page-repair-completed",
					detail = "source=$sourcePageIndex center=$refillCenter"
				)
				decodedRefillGeneration += 1L
				decodedRefillCenterOrdinal = null
				refillDecodedWorkingSet(refillCenter, "page-repair:$sourcePageIndex")
			}
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
				loader = ReaderPlayLikeCurlFoliateRasterLoader(
					bundleSource = bundleSource,
					profile = profile,
					onMissingRaster = { sourcePageIndex ->
						requestRasterRepair(sourcePageIndex, profile)
					}
				),
				release = Bitmap::recycle
			)
			requestedProfile = profile
		}
		val adapter = rasterAdapter ?: return
		val pageIndices = readerPlayLikeCurlPreparedPageIndices(
			orientation = profile.orientation,
			currentOrdinal = centerOrdinal,
			pageCount = profile.pageCount
		)
		val startedAtNanos = System.nanoTime()
		logActivationState(
			event = "deck-load-started",
			detail = "request=$request center=$centerOrdinal pages=${pageIndices.joinToString(",")}"
		)
		val preparation = adapter.prepare(
			profile = profile,
			pageIndices = pageIndices,
			onProgress = { progress ->
				host.post {
					if (request == requestGeneration && enabled && !destroyed) {
						logActivationState(
							event = "deck-load-progress",
							detail = "request=$request center=$centerOrdinal " +
								"completed=${progress.completed}/${progress.total} " +
								"elapsedMillis=${elapsedMillis(startedAtNanos)}"
						)
					}
				}
			}
		)
		rasterScope.launch {
			val deck = preparation.await()
			if (deck == null) {
				host.post {
					if (request == requestGeneration && enabled && !destroyed) {
						if (readinessState.textureDeck == ReaderTextureDeckState.Ready) {
							updateReadiness(
								interaction = ReaderPageInteractionState.BackgroundPrefetch,
								reason = "deck-load-deferred:$request"
							)
						} else {
							updateReadiness(
								textureDeck = ReaderTextureDeckState.Failed,
								interaction = ReaderPageInteractionState.Failed,
								reason = "deck-load-failed:$request"
							)
						}
						logActivationState(
							event = "deck-load-failed",
							detail = "request=$request center=$centerOrdinal " +
								"pages=${pageIndices.joinToString(",")} " +
								"elapsedMillis=${elapsedMillis(startedAtNanos)}"
						)
						logActivationState(
							"refresh-gated",
							"raster-deck-unavailable phase=$preparationPhase"
						)
						if (rasterRepairRequests.isEmpty()) {
							requestPrewarmIfIdle("raster-deck-unavailable")
						} else {
							logActivationState(
								event = "refresh-gated",
								detail = "targeted-page-repair-active"
							)
						}
					}
				}
				return@launch
			}
			host.post {
				if (request != requestGeneration || !enabled || destroyed || requestedProfile != profile) {
					deck.close()
					return@post
				}
				logActivationState(
					event = "deck-load-completed",
					detail = "request=$request center=$centerOrdinal " +
						"pages=${pageIndices.joinToString(",")} " +
						"elapsedMillis=${elapsedMillis(startedAtNanos)}"
				)
				activePages?.let { previous ->
					previous.obsolete = true
					closeIfUnused(previous)
				}
				val pages = PreparedPages(profile, deck, centerOrdinal)
				preparedPageSets += pages
				activePages = pages
				currentOrdinal = centerOrdinal.coerceIn(0, profile.pageCount - 1)
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Preparing,
					interaction = blockingPreparationState(),
					reason = "deck-submitting:$request"
				)
				submitLibraryDeck(
					pages = pages,
					ordinal = currentOrdinal,
					role = ReaderDeckSubmissionRole.Active
				)
			}
		}
	}

	private fun submitLibraryDeck(
		pages: PreparedPages,
		ordinal: Int,
		role: ReaderDeckSubmissionRole
	) {
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
			if (role == ReaderDeckSubmissionRole.Active) {
				updateReadiness(
					textureDeck = ReaderTextureDeckState.Failed,
					interaction = ReaderPageInteractionState.Failed,
					reason = "deck-build-failed:$ordinal"
				)
			}
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
		generationRoles[generationId] = role
		when (role) {
			ReaderDeckSubmissionRole.Active -> activeDeckGenerationId = generationId
			ReaderDeckSubmissionRole.Pending -> {
				pendingDeckGenerationId = generationId
				pendingDeckOrdinal = ordinal
			}
		}
		updateSurfaceBounds(pages, ordinal)
		logActivationState(
			event = "deck-submitted",
			detail = "generation=$generationId ordinal=$ordinal role=$role " +
				"orientation=${pages.profile.orientation}"
		)
		surfaceView.submitDeck(deck)
	}

	private fun updateSurfaceBounds(pages: PreparedPages, ordinal: Int) {
		val targetWidth = when (pages.profile.orientation) {
			ReaderPlayLikeCurlOrientation.Landscape -> ViewGroup.LayoutParams.MATCH_PARENT
			ReaderPlayLikeCurlOrientation.Portrait -> {
				val page = pages.deck.value(ordinal)
				if (page == null || host.width <= 0 || host.height <= 0) {
					ViewGroup.LayoutParams.MATCH_PARENT
				} else {
					readerPlayLikeCurlPortraitSurfaceWidth(
						hostWidth = host.width,
						hostHeight = host.height,
						pageBitmapWidth = page.width,
						pageBitmapHeight = page.height
					)
				}
			}
		}
		val params = surfaceView.layoutParams ?: return
		if (params.width == targetWidth && params.height == ViewGroup.LayoutParams.MATCH_PARENT) return
		params.width = targetWidth
		params.height = ViewGroup.LayoutParams.MATCH_PARENT
		surfaceView.layoutParams = params
		surfaceView.requestLayout()
		logActivationState(
			event = "surface-bounds-updated",
			detail = "orientation=${pages.profile.orientation} width=$targetWidth host=${host.width}x${host.height}"
		)
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
		generationRoles.remove(generationId)
		preparedDeckGenerations -= generationId
		if (activeDeckGenerationId == generationId) activeDeckGenerationId = null
		if (pendingDeckGenerationId == generationId) {
			pendingDeckGenerationId = null
			pendingDeckOrdinal = null
		}
		pages.generations -= generationId
		closeIfUnused(pages)
	}

	private fun promotePendingDeck(currentPageOrdinal: Int) {
		val promotedGeneration = pendingDeckGenerationId
			?.takeIf { pendingDeckOrdinal == currentPageOrdinal }
		if (promotedGeneration == null) {
			updateReadiness(
				textureDeck = ReaderTextureDeckState.Failed,
				interaction = ReaderPageInteractionState.Failed,
				reason = "settlement-missing-pending:$currentPageOrdinal"
			)
			requestPrewarmIfIdle("settlement-missing-pending")
			return
		}
		activeDeckGenerationId = promotedGeneration
		pendingDeckGenerationId = null
		pendingDeckOrdinal = null
		generationRoles[promotedGeneration] = ReaderDeckSubmissionRole.Active
		val prepared = promotedGeneration in preparedDeckGenerations
		updateReadiness(
			textureDeck = if (prepared) ReaderTextureDeckState.Ready else ReaderTextureDeckState.Preparing,
			interaction = if (prepared) preparedInteractionState() else ReaderPageInteractionState.BackgroundPrefetch,
			reason = "settlement-promoted:$promotedGeneration:$currentPageOrdinal"
		)
	}

	private fun discardPendingDeck(reason: String) {
		val generationId = pendingDeckGenerationId ?: return
		pendingDeckGenerationId = null
		pendingDeckOrdinal = null
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"PlayLikeCurl pending deck discarded generation=$generationId reason=$reason"
		)
		surfaceView.releaseDeck(generationId)
	}

	private fun closeIfUnused(pages: PreparedPages) {
		if (!pages.obsolete || pages.generations.isNotEmpty()) return
		preparedPageSets -= pages
		pages.deck.close()
	}

	private fun hideSurface() {
		surfaceView.alpha = 0f
	}

	private fun preparedInteractionState(): ReaderPageInteractionState =
		if (preparationPhase == ReaderPagePreparationPhase.Preparing) {
			ReaderPageInteractionState.BackgroundPrefetch
		} else {
			ReaderPageInteractionState.Ready
		}

	private fun blockingPreparationState(): ReaderPageInteractionState =
		if (hasPreparedDeckBefore) {
			ReaderPageInteractionState.BlockingProfileRegeneration
		} else {
			ReaderPageInteractionState.BlockingInitialPreparation
		}

	private fun updateReadiness(
		textureDeck: ReaderTextureDeckState = readinessState.textureDeck,
		interaction: ReaderPageInteractionState = readinessState.interaction,
		reason: String
	) {
		val next = ReaderPageRendererReadinessState(
			textureDeck = textureDeck,
			interaction = interaction
		)
		if (next == readinessState) return
		val previous = readinessState
		readinessState = next
		Logger.i(
			ReaderPlayLikeCurlFoliateControllerTag,
			"Readiness transition texture=${previous.textureDeck}->${next.textureDeck} " +
				"interaction=${previous.interaction}->${next.interaction} reason=$reason"
		)
		onReadinessStateChange(next)
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
			append(" interaction=")
			append(readinessState.interaction)
			append(" textureDeck=")
			append(readinessState.textureDeck)
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

	private fun elapsedMillis(startedAtNanos: Long): Long =
		((System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)

	private fun finishGesture(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: String
	) {
		if (activeGestureId == gestureId) activeGestureId = null
		onGestureTerminal(gestureId, outcome, detail)
	}

	private fun finishActiveGesture(
		outcome: ReaderPageGestureTerminalOutcome,
		detail: String
	) {
		activeGestureId?.let { gestureId -> finishGesture(gestureId, outcome, detail) }
	}
}
