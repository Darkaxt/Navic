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
import karacken.curl.ReadingDirection
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
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.util.core.Logger

private const val ReaderPlayLikeCurlFoliateControllerTag = "ReaderPlayLikeCurlFoliate"

internal enum class ReaderDeckSubmissionRole {
	Active,
	Pending
}

internal data class ReaderPlayLikeCurlRasterRepairRecipient(
	val fence: ReaderPlayLikeCurlRasterRepairFence,
	val attempt: Int = 0
)

internal class ReaderPlayLikeCurlRasterRepairRegistry {
	private data class Operation(
		val token: Long,
		val recipients: MutableList<ReaderPlayLikeCurlRasterRepairRecipient>
	)

	private val operations = mutableMapOf<
		Pair<ReaderPlayLikeCurlRasterProfile, Int>,
		Operation
	>()
	private var nextOperationToken = 0L

	fun register(
		profile: ReaderPlayLikeCurlRasterProfile,
		sourcePageIndex: Int,
		recipient: ReaderPlayLikeCurlRasterRepairRecipient
	): Long? {
		val key = profile to sourcePageIndex
		val existing = operations[key]
		if (existing != null) {
			if (recipient !in existing.recipients) existing.recipients += recipient
			return null
		}
		val token = Math.incrementExact(nextOperationToken)
		nextOperationToken = token
		operations[key] = Operation(token, mutableListOf(recipient))
		return token
	}

	fun complete(
		profile: ReaderPlayLikeCurlRasterProfile,
		sourcePageIndex: Int,
		operationToken: Long
	): List<ReaderPlayLikeCurlRasterRepairRecipient>? {
		val key = profile to sourcePageIndex
		val operation = operations[key]?.takeIf { it.token == operationToken }
			?: return null
		operations.remove(key)
		return operation.recipients.toList()
	}

	fun isEmpty(): Boolean = operations.isEmpty()

	fun clear() {
		operations.clear()
	}
}

internal data class ReaderPlayLikeCurlRasterRepairFence(
	val profile: ReaderPlayLikeCurlRasterProfile,
	val requestGeneration: Long,
	val destinationOrdinal: Int,
	val committedTurnVersion: Long,
	val protectedWindowVersion: Long,
	val protectedWindow: List<Int>
) {
	fun matches(
		profile: ReaderPlayLikeCurlRasterProfile?,
		requestGeneration: Long,
		destinationOrdinal: Int,
		committedTurnVersion: Long,
		protectedWindowVersion: Long,
		protectedWindow: List<Int>
	): Boolean =
		this.profile == profile &&
			this.requestGeneration == requestGeneration &&
			this.destinationOrdinal == destinationOrdinal &&
			this.committedTurnVersion == committedTurnVersion &&
			this.protectedWindowVersion == protectedWindowVersion &&
			this.protectedWindow == protectedWindow
}

internal sealed interface ReaderPageGestureTerminalDetail {
	data class RendererRejected(
		val generationId: Long,
		val reason: GestureRejectionReason
	) : ReaderPageGestureTerminalDetail

	data class RendererCancelled(val generationId: Long) : ReaderPageGestureTerminalDetail

	data class SettlementCompleted(
		val pageChange: PageChange,
		val ordinal: Int
	) : ReaderPageGestureTerminalDetail

	data class SettlementCancelled(val generationId: Long) : ReaderPageGestureTerminalDetail

	data class TouchRejected(val actionMasked: Int) : ReaderPageGestureTerminalDetail

	data class TapTurnUnavailable(val pageChange: PageChange) : ReaderPageGestureTerminalDetail

	data class RelocationCapacityUnavailable(
		val occupied: Int,
		val capacity: Int
	) : ReaderPageGestureTerminalDetail

	data class RelocationReservationProtocolFailure(
		val gestureId: Long
	) : ReaderPageGestureTerminalDetail

	data class TouchProtocolFailure(val actionMasked: Int) : ReaderPageGestureTerminalDetail

	data class TapTurnProtocolFailure(val pageChange: PageChange) : ReaderPageGestureTerminalDetail

	data class RelocationGenerationOrSessionDrift(
		val gestureId: Long
	) : ReaderPageGestureTerminalDetail

	data object ControllerCancelled : ReaderPageGestureTerminalDetail

	data class RenderFailed(
		val generationId: Long,
		val reason: RenderFailureReason
	) : ReaderPageGestureTerminalDetail

	data object RecoveryFailed : ReaderPageGestureTerminalDetail
}

internal sealed interface ReaderPageTurnStartResult {
	data object Settling : ReaderPageTurnStartResult

	data class TerminalPublished(
		val outcome: ReaderPageGestureTerminalOutcome,
		val detail: ReaderPageGestureTerminalDetail
	) : ReaderPageTurnStartResult
}

internal sealed interface ReaderPageCurlDispatchResult {
	data object Accepted : ReaderPageCurlDispatchResult
	data object TerminalPublished : ReaderPageCurlDispatchResult
}

internal sealed interface ReaderPageTapDispatchResult {
	data object Settling : ReaderPageTapDispatchResult
	data object TerminalPublished : ReaderPageTapDispatchResult

	data class CompleteInHost(
		val outcome: ReaderPageGestureTerminalOutcome
	) : ReaderPageTapDispatchResult
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
		detail: ReaderPageGestureTerminalDetail
	) -> Boolean,
	private val onReadinessStateChange: (ReaderPageRendererReadinessState) -> Unit = {},
	private val onUnsafeLifecycleEvent: (ReaderPageHostLifecycleEvent) -> Unit = {}
) : ReaderPageTapTurnPort {
	private class PreparedPages(
		val profile: ReaderPlayLikeCurlRasterProfile,
		val deck: ReaderPlayLikeCurlRasterDeck<ReaderPlayLikeCurlRasterImage>,
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
	private var rasterAdapter: ReaderPlayLikeCurlRasterAdapter<ReaderPlayLikeCurlRasterImage>? = null
	private var foliateRasterLoader: ReaderPlayLikeCurlFoliateRasterLoader? = null
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
	private var committedTurnVersion = 0L
	private var protectedWindowVersion = 0L
	private var currentProtectedWindow = emptyList<Int>()
	private val rasterRepairRequests = ReaderPlayLikeCurlRasterRepairRegistry()
	private var nextDeckGeneration = 1L
	private var activeGestureId: Long? = null
	private var tapTurnGestureId: Long? = null
	private var tapTurnTerminalSink: ((
		ReaderPageGestureTerminalOutcome,
		ReaderPageGestureTerminalDetail
	) -> Boolean)? = null
	private var synchronousTurnGestureId: Long? = null
	private var synchronousTurnTerminal: ReaderPageTurnStartResult.TerminalPublished? = null
	private var activeDeckGenerationId: Long? = null
	private var pendingDeckGenerationId: Long? = null
	private var pendingDeckOrdinal: Int? = null
	private var lastActivationTrace: String? = null
	private val persistentRefillCoordinator = ReaderPagePersistentRefillCoordinator(
		protectedWindowForCenter = { centerOrdinal ->
			requestedProfile?.preparedPageIndices(centerOrdinal).orEmpty()
		},
		publishProtectedWindow = ::publishProtectedWindow,
		isDecoded = ::isLogicalRasterDecoded,
		hydratePersistent = { logicalOrdinal, fence, isStillCurrent ->
			foliateRasterLoader?.hydratePersistent(logicalOrdinal) {
				isStillCurrent(fence)
			} == true
		},
		requestRepair = ::requestLogicalRasterRepair
	)

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
					GestureRejectionReason.BOUNDARY ->
						ReaderPageGestureTerminalOutcome.RejectedBoundary
					GestureRejectionReason.SETTLEMENT_RUNNING ->
						ReaderPageGestureTerminalOutcome.RejectedSettling
					else ->
						ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
				}
				if (!finishGesture(
						gestureId,
						outcome,
						ReaderPageGestureTerminalDetail.RendererRejected(
							generationId,
							reason
						)
					)
				) {
					return
				}
				hideSurface()
			}

			override fun onGestureCancelled(gestureId: Long, generationId: Long) {
				finishGesture(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledByUser,
					ReaderPageGestureTerminalDetail.RendererCancelled(generationId)
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
						pageChange = pageChange,
						readerDirection = prepared.profile.readerDirection,
						spreadAnchorParity = prepared.profile.spreadAnchorParity
					)
				}
				if (pages != null && targetOrdinal != null) {
					submitLibraryDeck(
						pages = pages,
						ordinal = targetOrdinal,
						role = ReaderDeckSubmissionRole.Pending
					)
				}
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement started gestureId=$gestureId generation=$generationId " +
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
				val outcome = when (pageChange) {
					PageChange.NEXT -> ReaderPageGestureTerminalOutcome.CommittedForward
					PageChange.PREVIOUS -> ReaderPageGestureTerminalOutcome.CommittedBackward
					PageChange.NONE -> ReaderPageGestureTerminalOutcome.CancelledByUser
				}
				if (!finishGesture(
						gestureId,
						outcome,
						ReaderPageGestureTerminalDetail.SettlementCompleted(
							pageChange,
							currentPageOrdinal
						)
					)
				) {
					return
				}
				if (pageChange == PageChange.NONE) {
					discardPendingDeck("settlement-none")
					Logger.i(
						ReaderPlayLikeCurlFoliateControllerTag,
						"PlayLikeCurl settlement completed generation=$generationId " +
							"ordinal=$currentPageOrdinal change=$pageChange exactDispatch=false"
					)
					hideSurface()
					updateReadiness(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = preparedInteractionState(),
						reason = "settlement-completed-none:$gestureId"
					)
					return
				}
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement completed generation=$generationId " +
						"ordinal=$currentPageOrdinal change=$pageChange exactDispatch=true"
				)
				currentOrdinal = currentPageOrdinal
				pendingExactOrdinal = currentPageOrdinal
				committedTurnVersion = Math.incrementExact(committedTurnVersion)
				schedulePersistentRefill(
					direction = when (pageChange) {
						PageChange.NEXT -> ReaderPageTurnDirection.Next
						PageChange.PREVIOUS -> ReaderPageTurnDirection.Previous
						PageChange.NONE -> error("Non-committed settlement reached refill")
					},
					destinationOrdinal = currentPageOrdinal,
					expectedTurnVersion = committedTurnVersion
				)
				promotePendingDeck(currentPageOrdinal)
				dispatchExactVisualPage(currentPageOrdinal)
			}

			override fun onSettlementCancelled(
				gestureId: Long,
				generationId: Long,
				currentLogicalPageId: String
			) {
				if (!finishGesture(
						gestureId,
						ReaderPageGestureTerminalOutcome.CancelledByUser,
						ReaderPageGestureTerminalDetail.SettlementCancelled(generationId)
					)
				) {
					return
				}
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl settlement cancelled generation=$generationId"
				)
				discardPendingDeck("settlement-cancelled")
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
					ReaderPageGestureTerminalDetail.RenderFailed(
						failure.generationId,
						failure.reason
					)
				)
				onUnsafeLifecycleEvent(
					if (
						failure.reason == RenderFailureReason.CONTEXT &&
						!failure.isRecoverable
					) {
						ReaderPageHostLifecycleEvent.UnsafeContextLost
					} else {
						ReaderPageHostLifecycleEvent.GlFailed
					}
				)
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

	private val canContinueAcceptedPointer: Boolean
		get() = enabled && attached && pageOperationPolicy.continueActivePointer

	fun setPageOperationPolicy(policy: ReaderPageOperationPolicy) {
		pageOperationPolicy = policy
	}

	private fun unavailableGestureOutcome(): ReaderPageGestureTerminalOutcome =
		(pageOperationPolicy.newPointer as? ReaderPageNewPointerDecision.Reject)?.outcome
			?: ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable

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
		invalidate(
			reason = "bitmap-quality-${normalized.persistedValue}",
			profileRegeneration = true
		)
		if (enabled) onRequestPrewarm()
	}

	fun setSnapshotKey(value: Int) {
		if (snapshotKey == value) return
		snapshotKey = value
		invalidate(
			reason = "snapshot-key",
			profileRegeneration = true
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
			profileRegeneration = true
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
	}

	fun onPageTouchEvent(
		event: MotionEvent,
		gestureId: Long
	): ReaderPageCurlDispatchResult {
		if (event.actionMasked == MotionEvent.ACTION_DOWN) {
			activeGestureId = gestureId
			if (!canContinueAcceptedPointer) {
				finishGesture(
					gestureId,
					unavailableGestureOutcome(),
					ReaderPageGestureTerminalDetail.TouchRejected(event.actionMasked)
				)
				hideSurface()
				return ReaderPageCurlDispatchResult.TerminalPublished
			}
		}
		surfaceView.onPageTouchEvent(event, gestureId)
		return if (
			event.actionMasked == MotionEvent.ACTION_DOWN &&
			activeGestureId != gestureId
		) {
			ReaderPageCurlDispatchResult.TerminalPublished
		} else {
			ReaderPageCurlDispatchResult.Accepted
		}
	}

	override fun start(
		gestureId: Long,
		pageChange: PageChange,
		onTerminal: (
			ReaderPageGestureTerminalOutcome,
			ReaderPageGestureTerminalDetail
		) -> Boolean
	): ReaderPageTurnStartResult {
		check(tapTurnGestureId == null && tapTurnTerminalSink == null) {
			"A tap turn terminal sink is already installed"
		}
		tapTurnGestureId = gestureId
		tapTurnTerminalSink = onTerminal
		return try {
			startTapTurn(pageChange, gestureId)
		} catch (failure: Throwable) {
			if (tapTurnGestureId == gestureId) {
				tapTurnGestureId = null
				tapTurnTerminalSink = null
			}
			throw failure
		}
	}

	private fun startTapTurn(
		pageChange: PageChange,
		gestureId: Long
	): ReaderPageTurnStartResult {
		check(synchronousTurnGestureId == null) {
			"A tap turn is already starting"
		}
		activeGestureId = gestureId
		synchronousTurnGestureId = gestureId
		synchronousTurnTerminal = null

		return try {
			if (!isAvailable) {
				finishGesture(
					gestureId,
					unavailableGestureOutcome(),
					ReaderPageGestureTerminalDetail.TapTurnUnavailable(pageChange)
				)
				checkNotNull(synchronousTurnTerminal)
			} else {
				surfaceView.alpha = 1f
				val accepted = surfaceView.turn(pageChange, gestureId)
				Logger.i(
					ReaderPlayLikeCurlFoliateControllerTag,
					"PlayLikeCurl tap turn change=$pageChange accepted=$accepted"
				)
				if (accepted) {
					ReaderPageTurnStartResult.Settling
				} else {
					hideSurface()
					synchronousTurnTerminal ?: run {
						finishGesture(
							gestureId,
							ReaderPageGestureTerminalOutcome.FailedRenderer,
							ReaderPageGestureTerminalDetail.TapTurnProtocolFailure(
								pageChange
							)
						)
						checkNotNull(synchronousTurnTerminal)
					}
				}
			}
		} finally {
			synchronousTurnGestureId = null
			synchronousTurnTerminal = null
		}
	}

	fun showSurfaceForGesture() {
		if (!canContinueAcceptedPointer) return
		surfaceView.alpha = 1f
	}

	fun cancelGesture(gestureId: Long) {
		activeGestureId = gestureId
		surfaceView.cancelGesture(gestureId)
		if (activeGestureId == gestureId) {
			finishGesture(
				gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser,
				ReaderPageGestureTerminalDetail.ControllerCancelled
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
		invalidate("external-page-relocation")
		if (enabled) onRequestPrewarm()
	}

	fun invalidate(
		reason: String,
		profileRegeneration: Boolean = false
	) {
		requestGeneration += 1L
		decodedRefillGeneration += 1L
		decodedRefillCenterOrdinal = null
		publishProtectedWindow(emptyList())
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
		foliateRasterLoader = null
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
		val pageIndices = profile.preparedPageIndices(centerOrdinal)
		publishProtectedWindow(pageIndices)
		val publicationFence = rasterPublicationFence(
			profile = profile,
			centerOrdinal = centerOrdinal,
			protectedWindow = pageIndices,
			expectedRequestGeneration = requestGeneration
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
		val preparation = adapter.prepare(
			profile = profile,
			pageIndices = pageIndices,
			publicationFence = publicationFence
		)
		rasterScope.launch {
			val deck = preparation.await()
			host.post {
				if (
					deck == null ||
					refill != decodedRefillGeneration ||
					!enabled ||
					destroyed ||
					requestedProfile != profile ||
					!publicationFence.isCurrent()
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

	private fun ReaderPlayLikeCurlRasterProfile.preparedPageIndices(
		centerOrdinal: Int
	): List<Int> = readerPlayLikeCurlPreparedPageIndices(
		orientation = orientation,
		currentOrdinal = centerOrdinal,
		pageCount = pageCount,
		readerDirection = readerDirection,
		spreadAnchorParity = spreadAnchorParity
	)

	private fun ReaderPlayLikeCurlRasterProfile.pageRequest(
		logicalOrdinal: Int
	): ReaderPlayLikeCurlFoliatePageRequest = readerPlayLikeCurlFoliatePageRequest(
		orientation = orientation,
		readerDirection = readerDirection,
		logicalOrdinal = logicalOrdinal,
		pageCount = pageCount,
		spreadAnchorParity = spreadAnchorParity
	)

	private fun ReaderPlayLikeCurlRasterProfile.transitionKind(): ReaderPageTurnTransitionKind =
		when (orientation) {
			ReaderPlayLikeCurlOrientation.Portrait ->
				ReaderPageTurnTransitionKind.PortraitSlide
			ReaderPlayLikeCurlOrientation.Landscape ->
				ReaderPageTurnTransitionKind.LandscapeSpreadSlide
		}

	private fun rasterPublicationFence(
		profile: ReaderPlayLikeCurlRasterProfile,
		centerOrdinal: Int,
		protectedWindow: List<Int>,
		expectedRequestGeneration: Long
	): ReaderPlayLikeCurlRasterPublicationFence {
		val expectedTurnVersion = committedTurnVersion
		val expectedWindowVersion = protectedWindowVersion
		val expectedWindow = protectedWindow.toList()
		return ReaderPlayLikeCurlRasterPublicationFence {
			!destroyed &&
				enabled &&
				requestedProfile == profile &&
				bundleSource.currentGeneration() == profile.rasterGeneration &&
				requestGeneration == expectedRequestGeneration &&
				currentOrdinal == centerOrdinal &&
				committedTurnVersion == expectedTurnVersion &&
				protectedWindowVersion == expectedWindowVersion &&
				currentProtectedWindow == expectedWindow
		}
	}

	private fun publishProtectedWindow(window: List<Int>): Long {
		val immutableWindow = window.toList()
		if (currentProtectedWindow != immutableWindow) {
			protectedWindowVersion = Math.incrementExact(protectedWindowVersion)
			currentProtectedWindow = immutableWindow
		}
		publishProtectedRasterOrdinals(immutableWindow)
		return protectedWindowVersion
	}

	private fun publishProtectedRasterOrdinals(logicalOrdinals: List<Int>) {
		val profile = requestedProfile
		val sourcePageIndices = if (profile == null) {
			emptySet()
		} else {
			logicalOrdinals.mapTo(linkedSetOf()) { ordinal ->
				profile.pageRequest(ordinal).sourcePageIndex
			}
		}
		bundleSource.protectDecodedPageIndices(sourcePageIndices)
	}

	private fun isLogicalRasterDecoded(logicalOrdinal: Int): Boolean {
		val profile = requestedProfile ?: return false
		if (rasterAdapter?.hasDecoded(profile, logicalOrdinal) == true) return true
		val request = profile.pageRequest(logicalOrdinal)
		return bundleSource.hasSnapshot(
			request.sourcePageIndex,
			profile.transitionKind()
		)
	}

	private fun requestLogicalRasterRepair(logicalOrdinal: Int) {
		val profile = requestedProfile ?: return
		requestRasterRepair(profile.pageRequest(logicalOrdinal).sourcePageIndex, profile)
	}

	private fun schedulePersistentRefill(
		direction: ReaderPageTurnDirection,
		destinationOrdinal: Int,
		expectedTurnVersion: Long
	) {
		val expectedProfile = requestedProfile ?: return
		val expectedGeneration = requestGeneration
		rasterScope.launch(Dispatchers.Main.immediate) {
			persistentRefillCoordinator.onTurnCommitted(
				direction = direction,
				destinationOrdinal = destinationOrdinal,
				committedTurnVersion = expectedTurnVersion,
				isTurnStillCurrent = {
					!destroyed &&
						requestedProfile == expectedProfile &&
						requestGeneration == expectedGeneration &&
						currentOrdinal == destinationOrdinal &&
						committedTurnVersion == expectedTurnVersion
				},
				isStillCurrent = { fence ->
					!destroyed &&
						requestedProfile == expectedProfile &&
						requestGeneration == expectedGeneration &&
						currentOrdinal == fence.destinationOrdinal &&
						committedTurnVersion == fence.committedTurnVersion &&
						protectedWindowVersion == fence.protectedWindowVersion &&
						currentProtectedWindow == fence.protectedWindow
				}
			)
		}
	}

	private fun requestRasterRepair(
		sourcePageIndex: Int,
		profile: ReaderPlayLikeCurlRasterProfile,
		attempt: Int = 0
	) {
		val refillCenter = currentOrdinal
		val recipient = ReaderPlayLikeCurlRasterRepairRecipient(
			fence = ReaderPlayLikeCurlRasterRepairFence(
				profile = profile,
				requestGeneration = requestGeneration,
				destinationOrdinal = currentOrdinal,
				committedTurnVersion = committedTurnVersion,
				protectedWindowVersion = protectedWindowVersion,
				protectedWindow = currentProtectedWindow.toList()
			),
			attempt = attempt
		)
		val operationToken = rasterRepairRequests.register(
			profile,
			sourcePageIndex,
			recipient
		) ?: return
		logActivationState(
			event = "page-repair-requested",
			detail = "source=$sourcePageIndex center=$refillCenter " +
				"profileGeneration=${profile.rasterGeneration}"
		)
		onRequestRasterRepair(sourcePageIndex) { success ->
			host.post {
				val recipients = rasterRepairRequests.complete(
					profile,
					sourcePageIndex,
					operationToken
				) ?: return@post
				val currentRecipient = if (destroyed || !enabled) {
					null
				} else {
					recipients.lastOrNull { candidate ->
						candidate.fence.matches(
							profile = requestedProfile,
							requestGeneration = requestGeneration,
							destinationOrdinal = currentOrdinal,
							committedTurnVersion = committedTurnVersion,
							protectedWindowVersion = protectedWindowVersion,
							protectedWindow = currentProtectedWindow
						)
					}
				}
				if (!success) {
					val operationAttempt = recipients.maxOfOrNull { it.attempt } ?: attempt
					logActivationState(
						event = "page-repair-deferred",
						detail = "source=$sourcePageIndex " +
							"center=${currentRecipient?.fence?.destinationOrdinal ?: refillCenter} " +
							"success=false attempt=$operationAttempt"
					)
					if (currentRecipient != null) {
						if (operationAttempt == 0) {
							requestRasterRepair(
								sourcePageIndex,
								profile,
								attempt = 1
							)
						} else {
							requestPrewarmIfIdle("page-repair-failed")
						}
					}
					return@post
				}
				if (currentRecipient == null) {
					logActivationState(
						event = "page-repair-deferred",
						detail = "source=$sourcePageIndex " +
							"center=${recipients.lastOrNull()?.fence?.destinationOrdinal ?: refillCenter} " +
							"success=$success"
					)
					return@post
				}
				val destinationOrdinal = currentRecipient.fence.destinationOrdinal
				logActivationState(
					event = "page-repair-completed",
					detail = "source=$sourcePageIndex center=$destinationOrdinal"
				)
				decodedRefillGeneration += 1L
				decodedRefillCenterOrdinal = null
				refillDecodedWorkingSet(
					destinationOrdinal,
					"page-repair:$sourcePageIndex"
				)
			}
		}
	}

	private fun prepareProfile(
		request: Long,
		profile: ReaderPlayLikeCurlRasterProfile,
		centerOrdinal: Int
	) {
		val pageIndices = profile.preparedPageIndices(centerOrdinal)
		if (requestedProfile != profile) {
			activePages?.let { pages ->
				pages.obsolete = true
				closeIfUnused(pages)
			}
			activePages = null
			rasterAdapter?.close()
			publishProtectedWindow(emptyList())
			requestedProfile = profile
			publishProtectedWindow(pageIndices)
			val loader = ReaderPlayLikeCurlFoliateRasterLoader(
				bundleSource = bundleSource,
				profile = profile,
				webViewProvider = webViewProvider,
				referenceSnapshotProvider = {
					val preferred = profile.pageRequest(currentOrdinal).sourcePageIndex
					bundleSource.retainedReferenceSnapshot(
						preferred,
						profile.transitionKind()
					)
				},
				onMissingRaster = { sourcePageIndex ->
					requestRasterRepair(sourcePageIndex, profile)
				}
			)
			foliateRasterLoader = loader
			rasterAdapter = ReaderPlayLikeCurlRasterAdapter(
				scope = rasterScope,
				loader = loader,
				release = { image ->
					if (!image.bitmap.isRecycled) image.bitmap.recycle()
				},
				publicationDispatcher = Dispatchers.Main.immediate
			)
		} else {
			publishProtectedWindow(pageIndices)
		}
		val adapter = rasterAdapter ?: return
		val publicationFence = rasterPublicationFence(
			profile = profile,
			centerOrdinal = centerOrdinal,
			protectedWindow = pageIndices,
			expectedRequestGeneration = request
		)
		val startedAtNanos = System.nanoTime()
		logActivationState(
			event = "deck-load-started",
			detail = "request=$request center=$centerOrdinal pages=${pageIndices.joinToString(",")}"
		)
		val preparation = adapter.prepare(
			profile = profile,
			pageIndices = pageIndices,
			publicationFence = publicationFence,
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
					if (
						request == requestGeneration &&
						enabled &&
						!destroyed &&
						publicationFence.isCurrent()
					) {
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
				if (
					request != requestGeneration ||
					!enabled ||
					destroyed ||
					requestedProfile != profile ||
					!publicationFence.isCurrent()
				) {
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
				readerDirection = pages.profile.readerDirection,
				spreadAnchorParity = pages.profile.spreadAnchorParity,
				filler = { pageGenerationId, slotRole, sourcePageIndex, leaf, fallbackOrdinal ->
					pages.filler(
						generationId = pageGenerationId,
						role = slotRole,
						sourcePageIndex = sourcePageIndex,
						leaf = leaf,
						fallbackOrdinal = fallbackOrdinal
					)
				},
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
		surfaceView.setReadingDirection(
			if (pages.profile.readerDirection == ReaderPlayLikeCurlReaderDirection.Rtl) {
				ReadingDirection.RIGHT_TO_LEFT
			} else {
				ReadingDirection.LEFT_TO_RIGHT
			}
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
						pageBitmapWidth = page.bitmap.width,
						pageBitmapHeight = page.bitmap.height
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

	private fun PreparedPages.page(
		generationId: Long,
		ordinal: Int
	): PageImage<Bitmap> {
		val image = checkNotNull(deck.value(ordinal)) {
			"Missing prepared Foliate page $ordinal for ${profile.orientation}"
		}
		return PageImage(
			generationId,
			"${profile.sourceIdentity}:${profile.orientation.name.lowercase()}:$ordinal",
			ordinal,
			image.bitmap.width,
			image.bitmap.height,
			image.bitmap
		)
	}

	private fun PreparedPages.filler(
		generationId: Long,
		role: ReaderPlayLikeCurlDeckSlotRole,
		sourcePageIndex: Int,
		leaf: ReaderPlayLikeCurlPhysicalLeaf,
		fallbackOrdinal: Int
	): PageImage<Bitmap> {
		val borrowed = checkNotNull(deck.value(fallbackOrdinal)) {
			"Missing filler lease page $fallbackOrdinal for ${profile.orientation}"
		}
		return PageImage.filler(
			generationId,
			"filler-${role.name}-$sourcePageIndex-${leaf.name}",
			fallbackOrdinal,
			borrowed.bitmap.width,
			borrowed.bitmap.height,
			borrowed.bitmap,
			borrowed.paperColorArgb
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
		detail: ReaderPageGestureTerminalDetail
	): Boolean {
		if (activeGestureId == gestureId) activeGestureId = null
		val tapSink = if (tapTurnGestureId == gestureId) {
			tapTurnGestureId = null
			tapTurnTerminalSink.also { tapTurnTerminalSink = null }
		} else {
			null
		}
		val published = if (tapSink == null) {
			onGestureTerminal(gestureId, outcome, detail)
		} else {
			tapSink(outcome, detail)
		}
		if (synchronousTurnGestureId == gestureId) {
			check(published) { "Synchronous renderer terminal lost the host CAS" }
			check(synchronousTurnTerminal == null) {
				"Tap turn published more than one synchronous terminal"
			}
			synchronousTurnTerminal = ReaderPageTurnStartResult.TerminalPublished(
				outcome = outcome,
				detail = detail
			)
		}
		return published
	}

	private fun finishActiveGesture(
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	) {
		activeGestureId?.let { gestureId -> finishGesture(gestureId, outcome, detail) }
	}
}
