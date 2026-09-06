package paige.navic.ui.screens.reader

import karacken.curl.PageSurfaceView
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderLiveEnginePresentationProof
import paige.navic.reader.ReaderLiveEngineHandoffDirection
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderShellCoverRetainedFrame
import paige.navic.reader.readerPresentationDecision

internal fun readerDispatchPageHostLifecycleEvent(
	event: ReaderPageHostLifecycleEvent,
	preserveDestinationDeck: Boolean,
	clearDestinationDeckPrewarm: () -> Unit,
	dispatchInputLifecycle: (ReaderPageHostLifecycleEvent) -> List<Long>
): List<Long> {
	if (!preserveDestinationDeck) clearDestinationDeckPrewarm()
	return dispatchInputLifecycle(event)
}

internal fun interface ReaderPresentationDrawRegistration {
	fun unregister()
}

internal interface ReaderPresentationCommitHost {
	val isAttachedToWindow: Boolean
	val currentPresentationBinding: ReaderPresentationBinding?
	val currentShellCoverGeneration: Long?
	val shellCoverSelected: Boolean
	val measuredViewportWidth: Int
	val measuredViewportHeight: Int

	fun prepareOpaqueShellCover(coverGeneration: Long)
	fun cancelOpaqueShellCoverPreparation(coverGeneration: Long)
	fun completeOpaqueShellCoverPreparation(coverGeneration: Long)
	fun registerShellCoverDrawListener(onDraw: () -> Unit): ReaderPresentationDrawRegistration
	fun postShellCoverAnimationFrame(onFrame: () -> Unit)
	fun applyPresentationFrameOwner(decision: ReaderPresentationDecision) = Unit
}

internal data class ReaderNativePagePresentationCandidate(
	val binding: ReaderPresentationBinding,
	val transitionToken: ReaderPresentationToken?,
	val visualPageIndex: Int,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val preparationFacts: ReaderPagePreparationFacts,
	val handoffDirection: ReaderLiveEngineHandoffDirection? = null
)

internal fun ReaderPresentationDecision.authoritativeLiveEngineToNativeTransitionOrNull():
	ReaderRequiredTransition.PresentNativePage? {
	if (lifecycle != ReaderPresentationLifecycleState.Foreground ||
		diagnosticPresentation is ReaderDiagnosticPresentation.Failure
	) return null
	val pending = authority as? ReaderPresentationAuthority.LiveEngineHandoffPending
		?: return null
	if (pending.direction != ReaderLiveEngineHandoffDirection.LiveEngineToNative) return null
	return ReaderRequiredTransition.PresentNativePage(
		token = pending.token,
		binding = pending.binding,
		direction = pending.direction
	)
}

internal data class ReaderNativePagePresentationHostSnapshot(
	val binding: ReaderPresentationBinding?,
	val transitionToken: ReaderPresentationToken?,
	val deck: ReaderPagePreparedActiveDeck?,
	val preparationFacts: ReaderPagePreparationFacts,
	val visualPageIndex: Int?,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val hostAttached: Boolean,
	val windowVisible: Boolean,
	val viewerReplacementAdmitted: Boolean,
	val rendererDeckReady: Boolean,
	val nativePresentationVisible: Boolean,
	val shellCoverSelected: Boolean,
	val handoffDirection: ReaderLiveEngineHandoffDirection? = null
) {
	fun currentCandidateOrNull(): ReaderNativePagePresentationCandidate? {
		val binding = binding ?: return null
		val deck = deck ?: return null
		val visualPageIndex = visualPageIndex ?: return null
		if (
			!hostAttached ||
			!windowVisible ||
			!viewerReplacementAdmitted ||
			!rendererDeckReady ||
			!nativePresentationVisible ||
			(shellCoverSelected && transitionToken == null) ||
			preparationFacts.phase != ReaderPagePreparationPhase.Ready ||
			preparationFacts.generation != binding.preparationGeneration ||
			viewportWidth <= 0 ||
			viewportHeight <= 0 ||
			visualPageIndex != deck.sourceCenterPageIndex ||
			binding.profileGeneration != deck.rasterProfileEpoch ||
			binding.rasterGeneration != deck.rasterEpoch ||
			binding.textureGeneration != deck.generationId ||
			binding.preparationGeneration != deck.preparationGeneration
		) return null
		return ReaderNativePagePresentationCandidate(
			binding = binding,
			transitionToken = transitionToken,
			visualPageIndex = visualPageIndex,
			viewportWidth = viewportWidth,
			viewportHeight = viewportHeight,
			preparationFacts = preparationFacts,
			handoffDirection = handoffDirection
		)
	}
}

internal interface ReaderNativePagePresentedFrameSource {
	fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long
	fun requestCandidatePresentedFrame(
		candidate: ReaderNativePagePresentationCandidate,
		onPresented: (Long) -> Unit
	): Long = requestNextPresentedFrame(onPresented)
	fun cancelPresentedFrameRequest(requestId: Long): Boolean
}

internal class ReaderPageSurfacePresentedFrameSource(
	private val surface: PageSurfaceView
) : ReaderNativePagePresentedFrameSource {
	override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = surface.requestNextPresentedFrame { onPresented(requestId) }
		return requestId
	}

	override fun requestCandidatePresentedFrame(
		candidate: ReaderNativePagePresentationCandidate,
		onPresented: (Long) -> Unit
	): Long {
		val generation = candidate.binding.textureGeneration
			?: return PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = surface.requestNativePagePresentedFrame(generation) { onPresented(requestId) }
		return requestId
	}

	override fun cancelPresentedFrameRequest(requestId: Long): Boolean =
		surface.cancelPresentedFrameRequest(requestId)
}

internal class ReaderNativePagePresentationPublisher(
	private val frameSource: ReaderNativePagePresentedFrameSource,
	private val currentCandidate: () -> ReaderNativePagePresentationCandidate?,
	private val currentHandoffTransition: () -> ReaderRequiredTransition.PresentNativePage? = {
		null
	},
	private val handoffTimeoutScheduler: ReaderPageRelocationDispatchTimeoutScheduler? = null,
	private val handoffTimeoutMillis: Long = 10_000L,
	private val onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
) {
	private data class PendingFrame(
		val requestId: Long,
		val candidate: ReaderNativePagePresentationCandidate
	)

	private data class PendingHandoffTimeout(
		var transition: ReaderRequiredTransition.PresentNativePage,
		val action: Runnable
	)

	private var pendingFrame: PendingFrame? = null
	private var pendingHandoffTimeout: PendingHandoffTimeout? = null
	private var failedHandoffTransition: ReaderRequiredTransition.PresentNativePage? = null
	private var lastPublishedCandidate: ReaderNativePagePresentationCandidate? = null
	private var disposed = false

	init {
		require(handoffTimeoutMillis > 0L)
	}

	fun update() {
		if (disposed) return
		val handoffTransition = currentLiveEngineToNativeTransition()
		failedHandoffTransition?.let { failed ->
			failedHandoffTransition = when {
				handoffTransition == null -> failed
				failed.isSameHandoffAttemptAs(handoffTransition) -> handoffTransition
				else -> null
			}
		}
		pendingHandoffTimeout?.let { timeout ->
			when {
				handoffTransition == null -> cancelHandoffTimeout()
				timeout.transition.isSameHandoffAttemptAs(handoffTransition) ->
					timeout.transition = handoffTransition
				else -> cancelHandoffTimeout()
			}
		}
		val failedTransition = failedHandoffTransition
		if (
			handoffTransition != null &&
			failedTransition != handoffTransition &&
			pendingHandoffTimeout == null
		) {
			armHandoffTimeout(handoffTransition)
		}

		val candidate = currentCandidate()
		val published = lastPublishedCandidate
		if (
			candidate != null &&
			published?.transitionToken != null &&
			published.handoffDirection == ReaderLiveEngineHandoffDirection.LiveEngineToNative &&
			candidate.transitionToken == null &&
			candidate.handoffDirection == null &&
			candidate == published.copy(transitionToken = null, handoffDirection = null)
		) {
			lastPublishedCandidate = candidate
		}
		val pending = pendingFrame
		if (pending != null && pending.candidate != candidate) {
			pendingFrame = null
			frameSource.cancelPresentedFrameRequest(pending.requestId)
		}
		if (
			(failedTransition != null &&
				(
					failedTransition == handoffTransition ||
					candidate?.matches(failedTransition) == true ||
					(candidate?.binding == failedTransition.binding &&
						candidate.transitionToken == null)
				)) ||
			candidate == null ||
			candidate == lastPublishedCandidate ||
			pendingFrame?.candidate == candidate ||
			(handoffTransition != null && !candidate.matches(handoffTransition))
		) return

		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = frameSource.requestCandidatePresentedFrame(candidate) { presentedRequestId ->
			onPresentedFrame(requestId, presentedRequestId, candidate)
		}
		if (requestId == PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID) {
			if (handoffTransition != null && candidate.matches(handoffTransition)) {
				failHandoffRegistration(handoffTransition)
			}
			return
		}
		pendingFrame = PendingFrame(requestId, candidate)
	}

	fun dispose() {
		if (disposed) return
		disposed = true
		pendingFrame?.let { frameSource.cancelPresentedFrameRequest(it.requestId) }
		pendingFrame = null
		cancelHandoffTimeout()
		failedHandoffTransition = null
	}

	private fun currentLiveEngineToNativeTransition():
		ReaderRequiredTransition.PresentNativePage? = currentHandoffTransition()?.takeIf {
		it.direction == ReaderLiveEngineHandoffDirection.LiveEngineToNative
	}

	private fun ReaderRequiredTransition.PresentNativePage.isSameHandoffAttemptAs(
		other: ReaderRequiredTransition.PresentNativePage
	): Boolean = token == other.token && direction == other.direction

	private fun ReaderNativePagePresentationCandidate.matches(
		transition: ReaderRequiredTransition.PresentNativePage
	): Boolean = transitionToken == transition.token &&
		handoffDirection == transition.direction &&
		binding == transition.binding

	private fun armHandoffTimeout(
		transition: ReaderRequiredTransition.PresentNativePage
	) {
		val scheduler = handoffTimeoutScheduler ?: return
		lateinit var action: Runnable
		action = Runnable { onHandoffTimeout(action) }
		pendingHandoffTimeout = PendingHandoffTimeout(transition, action)
		if (!scheduler.postDelayed(action, handoffTimeoutMillis)) action.run()
	}

	private fun onHandoffTimeout(action: Runnable) {
		val timeout = pendingHandoffTimeout ?: return
		val transition = timeout.transition
		if (
			disposed || timeout.action !== action ||
			currentLiveEngineToNativeTransition() != transition
		) return
		pendingHandoffTimeout = null
		pendingFrame?.takeIf { it.candidate.matches(transition) }?.let { pending ->
			pendingFrame = null
			frameSource.cancelPresentedFrameRequest(pending.requestId)
		}
		failedHandoffTransition = transition
		onEvent(
			ReaderPresentationEvent.LiveEngineHandoffTimedOut(
				direction = ReaderLiveEngineHandoffDirection.LiveEngineToNative,
				token = transition.token,
				binding = transition.binding
			)
		)
	}

	private fun failHandoffRegistration(
		transition: ReaderRequiredTransition.PresentNativePage
	) {
		cancelHandoffTimeout()
		failedHandoffTransition = transition
		onEvent(
			ReaderPresentationEvent.LiveEngineExposureFailed(
				direction = ReaderLiveEngineHandoffDirection.LiveEngineToNative,
				token = transition.token,
				binding = transition.binding,
				reason = ReaderPresentationFailureReason.NativePresentationUnavailable
			)
		)
	}

	private fun cancelHandoffTimeout() {
		val timeout = pendingHandoffTimeout ?: return
		pendingHandoffTimeout = null
		handoffTimeoutScheduler?.removeCallbacks(timeout.action)
	}

	private fun onPresentedFrame(
		expectedRequestId: Long,
		presentedRequestId: Long,
		armedCandidate: ReaderNativePagePresentationCandidate
	) {
		if (disposed || expectedRequestId != presentedRequestId) return
		val pending = pendingFrame
		if (pending?.requestId != expectedRequestId || pending.candidate != armedCandidate) return
		pendingFrame = null
		currentLiveEngineToNativeTransition()?.takeIf { transition ->
			armedCandidate.matches(transition)
		}?.let { cancelHandoffTimeout() }
		val candidate = currentCandidate()
		if (candidate != armedCandidate || candidate == lastPublishedCandidate) return
		val event = ReaderPresentationEvent.NativePagePresented(
			ReaderNativePagePresentationProof(
				binding = candidate.binding,
				transitionToken = candidate.transitionToken,
				presentedFrame = presentedRequestId,
				viewportWidth = candidate.viewportWidth,
				viewportHeight = candidate.viewportHeight,
				rasterGeneration = requireNotNull(candidate.binding.rasterGeneration),
				textureGeneration = requireNotNull(candidate.binding.textureGeneration)
			)
		)
		var receipt: ReaderPresentationEventReceipt? = null
		try {
			receipt = onEvent(event)
		} finally {
			if (receipt.authorizes(event)) {
				lastPublishedCandidate = candidate
			} else {
				update()
			}
		}
	}
}

internal enum class ReaderShellCoverHostLayer {
	Hidden,
	PreparedBehindPredecessor,
	Selected
}

internal class ReaderShellCoverLayerController(
	private val onPrepareBehindPredecessor: () -> Unit,
	private val onHidePreparedCover: () -> Unit,
	private val onSelectCover: () -> Unit,
	private val onInvalidateRasterDeck: () -> Unit,
	private val onInvalidatePreparation: () -> Unit
) {
	var currentLayer: ReaderShellCoverHostLayer = ReaderShellCoverHostLayer.Hidden
		private set

	fun prepareCoverBehindPredecessor() {
		if (currentLayer == ReaderShellCoverHostLayer.PreparedBehindPredecessor) return
		onPrepareBehindPredecessor()
		currentLayer = ReaderShellCoverHostLayer.PreparedBehindPredecessor
	}

	fun hidePreparedCover() {
		if (currentLayer != ReaderShellCoverHostLayer.PreparedBehindPredecessor) return
		onHidePreparedCover()
		currentLayer = ReaderShellCoverHostLayer.Hidden
	}

	fun coverHidden() {
		if (currentLayer == ReaderShellCoverHostLayer.Selected) {
			currentLayer = ReaderShellCoverHostLayer.Hidden
		}
	}

	fun selectCover(preserveNativePresentationProof: Boolean) {
		if (currentLayer == ReaderShellCoverHostLayer.Selected) return
		onSelectCover()
		currentLayer = ReaderShellCoverHostLayer.Selected
		if (!preserveNativePresentationProof) {
			onInvalidateRasterDeck()
			onInvalidatePreparation()
		}
	}
}

internal class ReaderPresentationHostBridge(
	private val host: ReaderPresentationCommitHost,
	private val liveEngineVisualHandoff: ReaderWebViewVisualHandoff? = null,
	private val liveEngineExposureRequired: () -> Boolean = { false },
	transitionTimeoutScheduler: ReaderPageRelocationDispatchTimeoutScheduler = HandlerTimeoutScheduler(),
	transitionNowMillis: () -> Long = android.os.SystemClock::uptimeMillis,
	private val onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
) {
	private data class ViewportGeometry(
		val width: Int,
		val height: Int
	)

	private class PendingCoverCommit(
		val transition: ReaderRequiredTransition.CommitShellCover,
		val geometry: ViewportGeometry
	) {
		var registration: ReaderPresentationDrawRegistration? = null
		var registrationRemoved = false
		var frameScheduled = false
		var acceptedReceipt: ReaderPresentationEventReceipt? = null
		var emittedProof: ReaderShellCoverCommitProof? = null

		fun unregisterOnce() {
			if (registrationRemoved) return
			registrationRemoved = true
			registration?.unregister()
		}
	}

	private class PendingLiveEngineExposure(
		var transition: ReaderRequiredTransition.ExposeLiveEngine
	) {
		var emittedEvent: ReaderPresentationEvent? = null
		var acceptedReceipt: ReaderPresentationEventReceipt? = null
		var receiptDispatchInProgress = false
	}

	private data class CancelledLiveEngineHandoff(
		val direction: ReaderLiveEngineHandoffDirection,
		val binding: ReaderPresentationBinding
	)

	private var currentDecision: ReaderPresentationDecision? = null
	private var pendingCoverCommit: PendingCoverCommit? = null
	private var pendingLiveEngineExposure: PendingLiveEngineExposure? = null
	private var cancelledLiveEngineHandoff: CancelledLiveEngineHandoff? = null
	private var lastLiveEngineExposureRequired: Boolean? = null
	private var committedTransition: ReaderRequiredTransition.CommitShellCover? = null
	private var presentedFrame = 0L
	private var disposed = false
	private val transitionTimeout = ReaderPresentationTransitionTimeout(
		scheduler = transitionTimeoutScheduler,
		nowMillis = transitionNowMillis
	) { event ->
		val receipt = onEvent(event).takeIf { it.authorizes(event) }
		if (receipt != null) update(readerPresentationDecision(receipt.postState))
		receipt != null
	}

	fun update(decision: ReaderPresentationDecision) {
		if (disposed) return
		val liveEngineRequired = liveEngineExposureRequired()
		synchronizeLiveEngineHandoffIntent(decision, liveEngineRequired)
		currentDecision = decision
		transitionTimeout.update(decision)
		if (currentDecision != decision) return
		host.applyPresentationFrameOwner(decision)
		if (decision.lifecycle != ReaderPresentationLifecycleState.Foreground) {
			cancelPendingCoverCommit()
			cancelPendingLiveEngineExposure()
			return
		}
		if (requestInitialShellCoverIfRequired(decision, liveEngineRequired)) return
		if (requestLiveEngineExposureIfRequired(decision, liveEngineRequired)) return
		if (requestNativePageExposureIfRequired(decision, liveEngineRequired)) return
		updateLiveEngineExposure(decision, liveEngineRequired)
		val pending = pendingCoverCommit
		if (pending?.emittedProof != null && pending.acceptedReceipt == null) {
			dispatchCoverReceipt(
				pending,
				ReaderPresentationEvent.ShellCoverCommitted(pending.emittedProof!!)
			)
			return
		}
		if (pending?.acceptedReceipt != null) {
			if (acceptedShellCoverDecisionMatches(decision, pending)) {
				completePendingCoverCommit(pending)
				return
			}
			val emittedTransition = pending.transition
			cancelPendingCoverCommit()
			committedTransition = emittedTransition
			val replacement =
				decision.requiredTransition as? ReaderRequiredTransition.CommitShellCover
			if (replacement == null || replacement == emittedTransition) return
			committedTransition = null
			beginCoverCommit(decision, replacement)
			return
		}

		val transition = decision.requiredTransition as? ReaderRequiredTransition.CommitShellCover
		if (transition == null) {
			cancelPendingCoverCommit()
			return
		}
		if (pending?.transition == transition) {
			val geometry = currentGeometryOrNull()
			if (
				geometry == pending.geometry &&
				hostFactsMatch(
					decision,
					transition,
					geometry,
					requirePreparedCover = true
				)
			) return
		}
		if (committedTransition == transition) return
		cancelPendingCoverCommit()
		committedTransition = null
		beginCoverCommit(decision, transition)
	}

	fun onHostDetached() {
		cancelPendingCoverCommit()
		cancelPendingLiveEngineExposure()
	}

	fun dispose() {
		if (disposed) return
		disposed = true
		transitionTimeout.cancel()
		cancelPendingCoverCommit()
		cancelPendingLiveEngineExposure()
		liveEngineVisualHandoff?.close()
		currentDecision = null
		cancelledLiveEngineHandoff = null
		lastLiveEngineExposureRequired = null
		committedTransition = null
	}

	private fun synchronizeLiveEngineHandoffIntent(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	) {
		val previousMode = lastLiveEngineExposureRequired
		if (previousMode != null && previousMode != liveEngineRequired) {
			cancelledLiveEngineHandoff = null
		}
		lastLiveEngineExposureRequired = liveEngineRequired
		if (cancelledLiveEngineHandoff?.binding != decision.targetBinding) {
			cancelledLiveEngineHandoff = null
		}

		val previousPending = currentDecision?.authority as?
			ReaderPresentationAuthority.LiveEngineHandoffPending
		val pending = decision.authority as?
			ReaderPresentationAuthority.LiveEngineHandoffPending
		if (pending != null) {
			if (pending != previousPending) cancelledLiveEngineHandoff = null
			return
		}
		if (previousPending != null && decision.restoresPredecessorFor(previousPending)) {
			cancelledLiveEngineHandoff = CancelledLiveEngineHandoff(
				previousPending.direction,
				previousPending.binding
			)
		}
	}

	private fun cancelledHandoffMatches(
		direction: ReaderLiveEngineHandoffDirection,
		binding: ReaderPresentationBinding?
	): Boolean = cancelledLiveEngineHandoff == binding?.let {
		CancelledLiveEngineHandoff(direction, it)
	}

	private fun ReaderPresentationDecision.restoresPredecessorFor(
		pending: ReaderPresentationAuthority.LiveEngineHandoffPending
	): Boolean {
		if (
			targetBinding != pending.binding ||
			requiredTransition != ReaderRequiredTransition.None ||
			diagnosticPresentation != ReaderDiagnosticPresentation.Hidden
		) return false
		return when (val retained = pending.retainedFrame) {
			is ReaderPresentationFrameOwner.NativePage ->
				authority == ReaderPresentationAuthority.SettledNativePage(retained) &&
					frameOwner == retained
			is ReaderPresentationFrameOwner.LiveEngine ->
				authority == ReaderPresentationAuthority.LiveEngineExposed(retained) &&
					frameOwner == retained
			is ReaderPresentationFrameOwner.ShellCover ->
				authority == ReaderPresentationAuthority.ShellCover(retained.proof) &&
					frameOwner == retained
			else -> false
		}
	}

	private fun requestInitialShellCoverIfRequired(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	): Boolean {
		val binding = decision.targetBinding ?: return false
		if (
			!liveEngineRequired ||
			!host.isAttachedToWindow ||
			host.currentPresentationBinding != binding ||
			decision.requiredTransition != ReaderRequiredTransition.None ||
			decision.authority != ReaderPresentationAuthority.Unavailable ||
			decision.frameOwner != ReaderPresentationFrameOwner.Neutral
		) return false
		val event = ReaderPresentationEvent.ShellCoverRequested(
			coverGeneration = binding.publicationGeneration
		)
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return false
		update(readerPresentationDecision(receipt.postState))
		return true
	}

	private fun requestLiveEngineExposureIfRequired(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	): Boolean {
		if (!liveEngineRequired) return false
		if (
			!host.isAttachedToWindow ||
			host.currentPresentationBinding != decision.targetBinding ||
			decision.requiredTransition != ReaderRequiredTransition.None ||
			cancelledHandoffMatches(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine,
				decision.targetBinding
			) ||
			(
				decision.authority !is ReaderPresentationAuthority.ShellCover &&
					decision.authority !is ReaderPresentationAuthority.SettledNativePage
			)
		) return false
		val event = ReaderPresentationEvent.WebViewHandoffRequested(
			ReaderLiveEngineHandoffDirection.NativeToLiveEngine
		)
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return false
		update(readerPresentationDecision(receipt.postState))
		return true
	}

	private fun requestNativePageExposureIfRequired(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	): Boolean {
		if (liveEngineRequired) return false
		if (
			!host.isAttachedToWindow ||
			decision.requiredTransition != ReaderRequiredTransition.None ||
			cancelledHandoffMatches(
				ReaderLiveEngineHandoffDirection.LiveEngineToNative,
				decision.targetBinding
			) ||
			decision.authority !is ReaderPresentationAuthority.LiveEngineExposed
		) return false
		val event = ReaderPresentationEvent.WebViewHandoffRequested(
			ReaderLiveEngineHandoffDirection.LiveEngineToNative
		)
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return false
		update(readerPresentationDecision(receipt.postState))
		return true
	}

	private fun updateLiveEngineExposure(
		decision: ReaderPresentationDecision,
		liveEngineRequired: Boolean
	) {
		if (!liveEngineRequired) {
			cancelPendingLiveEngineExposure()
			return
		}
		val transition = decision.requiredTransition as? ReaderRequiredTransition.ExposeLiveEngine
		val pending = pendingLiveEngineExposure
		if (pending != null) {
			when {
				pending.acceptedReceipt != null -> {
					if (acceptedLiveEngineDecisionMatches(decision, pending)) {
						pendingLiveEngineExposure = null
						return
					}
					cancelPendingLiveEngineExposure()
				}
				pending.transition != transition -> {
					if (transition?.token == pending.transition.token) {
						if (
							liveEngineVisualHandoff?.rebindPresentationRequest(
								pending.transition,
								transition
							) == true
						) {
							pending.transition = transition
							return
						}
						cancelPendingLiveEngineExposure()
						PendingLiveEngineExposure(transition).also { failed ->
							pendingLiveEngineExposure = failed
							publishLiveEngineExposureFailure(
								failed,
								ReaderWebViewVisualHandoffFailure.Invalidated
							)
						}
						return
					}
					cancelPendingLiveEngineExposure()
				}
				pending.emittedEvent != null -> {
					dispatchLiveEngineReceipt(pending, pending.emittedEvent!!)
					return
				}
				else -> return
			}
		}
		if (transition == null || !liveEngineHostFactsMatch(decision, transition)) return

		val next = PendingLiveEngineExposure(transition)
		pendingLiveEngineExposure = next
		val handoff = liveEngineVisualHandoff
		if (handoff == null) {
			publishLiveEngineExposureFailure(
				next,
				ReaderWebViewVisualHandoffFailure.Detached
			)
			return
		}
		try {
			handoff.await(transition.token, transition.binding, deadlineDelegated = true) { result ->
				onLiveEngineVisualHandoffResult(next, result)
			}
		} catch (_: Throwable) {
			if (pendingLiveEngineExposure === next && next.emittedEvent == null) {
				publishLiveEngineExposureFailure(
					next,
					ReaderWebViewVisualHandoffFailure.Invalidated
				)
			}
		}
	}

	private fun onLiveEngineVisualHandoffResult(
		pending: PendingLiveEngineExposure,
		result: ReaderPresentationWebViewVisualHandoffResult
	) {
		if (pendingLiveEngineExposure !== pending || disposed) return
		if (
			result.token != pending.transition.token ||
			result.binding != pending.transition.binding
		) return
		when (result) {
			is ReaderPresentationWebViewVisualHandoffResult.Ready -> {
				val decision = currentDecision ?: return
				if (!liveEngineHostFactsMatch(decision, pending.transition)) return
				val event = ReaderPresentationEvent.LiveEngineExposureCommitted(
					ReaderLiveEnginePresentationProof(
						token = result.token,
						binding = result.binding,
						presentedFrameSequence = result.presentedFrameSequence
					)
				)
				pending.emittedEvent = event
				dispatchLiveEngineReceipt(pending, event)
			}
			is ReaderPresentationWebViewVisualHandoffResult.Failed ->
				publishLiveEngineExposureFailure(pending, result.reason)
		}
	}

	private fun publishLiveEngineExposureFailure(
		pending: PendingLiveEngineExposure,
		reason: ReaderWebViewVisualHandoffFailure
	) {
		if (pendingLiveEngineExposure !== pending || pending.emittedEvent != null) return
		val event = ReaderPresentationEvent.LiveEngineExposureFailed(
			direction = pending.transition.direction,
			token = pending.transition.token,
			binding = pending.transition.binding,
			reason = if (reason == ReaderWebViewVisualHandoffFailure.TimedOut) {
				ReaderPresentationFailureReason.TimedOut
			} else {
				ReaderPresentationFailureReason.LiveEngineUnavailable
			}
		)
		pending.emittedEvent = event
		dispatchLiveEngineReceipt(pending, event)
	}

	private fun dispatchLiveEngineReceipt(
		pending: PendingLiveEngineExposure,
		event: ReaderPresentationEvent
	) {
		if (pendingLiveEngineExposure !== pending || pending.receiptDispatchInProgress) return
		pending.receiptDispatchInProgress = true
		val receipt = try {
			onEvent(event).takeIf { it.authorizes(event) }
		} finally {
			pending.receiptDispatchInProgress = false
		} ?: return
		pending.acceptedReceipt = receipt
		update(readerPresentationDecision(receipt.postState))
	}

	private fun acceptedLiveEngineDecisionMatches(
		decision: ReaderPresentationDecision,
		pending: PendingLiveEngineExposure
	): Boolean {
		val event = pending.emittedEvent as? ReaderPresentationEvent.LiveEngineExposureCommitted
			?: return false
		val proof = event.proof
		val authority = decision.authority as? ReaderPresentationAuthority.LiveEngineExposed
		return authority?.frame?.proof == proof &&
			decision.frameOwner == ReaderPresentationFrameOwner.LiveEngine(proof) &&
			decision.targetBinding == pending.transition.binding &&
			decision.requiredTransition == ReaderRequiredTransition.None &&
			proof.token == pending.transition.token &&
			proof.binding == pending.transition.binding
	}

	private fun liveEngineHostFactsMatch(
		decision: ReaderPresentationDecision,
		transition: ReaderRequiredTransition.ExposeLiveEngine
	): Boolean {
		val authority = decision.authority as?
			ReaderPresentationAuthority.LiveEngineHandoffPending ?: return false
		return host.isAttachedToWindow &&
			host.currentPresentationBinding == transition.binding &&
			decision.targetBinding == transition.binding &&
			decision.requiredTransition == transition &&
			(
				decision.frameOwner is ReaderPresentationFrameOwner.NativePage ||
					decision.frameOwner is ReaderPresentationFrameOwner.ShellCover
			) &&
			authority.retainedFrame == decision.frameOwner &&
			authority.direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine &&
			authority.token == transition.token &&
			authority.binding == transition.binding
	}

	private fun cancelPendingLiveEngineExposure() {
		if (pendingLiveEngineExposure == null) return
		pendingLiveEngineExposure = null
		liveEngineVisualHandoff?.invalidate()
	}

	private fun beginCoverCommit(
		decision: ReaderPresentationDecision,
		transition: ReaderRequiredTransition.CommitShellCover
	) {
		val geometry = currentGeometryOrNull() ?: return
		if (!hostFactsMatch(decision, transition, geometry, requirePreparedCover = false)) return

		try {
			host.prepareOpaqueShellCover(transition.coverGeneration)
		} catch (_: Throwable) {
			failCoverCommit(transition)
			return
		}
		if (!hostFactsMatch(decision, transition, geometry, requirePreparedCover = true)) {
			host.cancelOpaqueShellCoverPreparation(transition.coverGeneration)
			return
		}

		val pending = PendingCoverCommit(transition, geometry)
		pendingCoverCommit = pending
		val registration = try {
			host.registerShellCoverDrawListener {
				onCoverDrawn(pending)
			}
		} catch (_: Throwable) {
			pendingCoverCommit = null
			failCoverCommit(transition)
			return
		}
		pending.registration = registration
		when {
			pending.registrationRemoved -> registration.unregister()
			pendingCoverCommit !== pending -> pending.unregisterOnce()
		}
	}

	private fun onCoverDrawn(pending: PendingCoverCommit) {
		if (pendingCoverCommit !== pending || pending.frameScheduled) return
		val decision = currentDecision
		if (
			decision == null ||
			!hostFactsMatch(
				decision,
				pending.transition,
				pending.geometry,
				requirePreparedCover = true
			)
		) {
			cancelPendingCoverCommit()
			return
		}

		pending.frameScheduled = true
		pending.unregisterOnce()
		host.postShellCoverAnimationFrame {
			onCoverAnimationFrame(pending)
		}
	}

	private fun onCoverAnimationFrame(pending: PendingCoverCommit) {
		if (pendingCoverCommit !== pending) return
		val decision = currentDecision
		if (
			decision == null ||
			!hostFactsMatch(
				decision,
				pending.transition,
				pending.geometry,
				requirePreparedCover = true
			)
		) {
			cancelPendingCoverCommit()
			return
		}

		presentedFrame = Math.incrementExact(presentedFrame)
		val proof = ReaderShellCoverCommitProof(
			token = pending.transition.token,
			binding = pending.transition.binding,
			coverGeneration = pending.transition.coverGeneration,
			presentedFrame = presentedFrame,
			viewportWidth = pending.geometry.width,
			viewportHeight = pending.geometry.height
		)
		pending.emittedProof = proof
		dispatchCoverReceipt(
			pending,
			ReaderPresentationEvent.ShellCoverCommitted(proof)
		)
	}

	private fun dispatchCoverReceipt(
		pending: PendingCoverCommit,
		event: ReaderPresentationEvent
	) {
		val receipt = onEvent(event).takeIf { it.authorizes(event) } ?: return
		pending.acceptedReceipt = receipt
		val receiptDecision = readerPresentationDecision(receipt.postState)
		currentDecision = receiptDecision
		transitionTimeout.update(receiptDecision)
		if (acceptedShellCoverDecisionMatches(receiptDecision, pending)) {
			completePendingCoverCommit(pending)
		}
	}

	private fun acceptedShellCoverDecisionMatches(
		decision: ReaderPresentationDecision,
		pending: PendingCoverCommit
	): Boolean {
		val proof = (decision.frameOwner as? ReaderPresentationFrameOwner.ShellCover)?.proof
		val authorityProof = (decision.authority as? ReaderPresentationAuthority.ShellCover)?.proof
		return proof != null &&
			proof == pending.emittedProof &&
			authorityProof == proof &&
			proof.token == pending.transition.token &&
			proof.binding == pending.transition.binding &&
			proof.coverGeneration == pending.transition.coverGeneration &&
			proof.viewportWidth == pending.geometry.width &&
			proof.viewportHeight == pending.geometry.height &&
			decision.requiredTransition !is ReaderRequiredTransition.CommitShellCover &&
			host.shellCoverSelected &&
			host.isAttachedToWindow &&
			host.currentPresentationBinding == pending.transition.binding &&
			host.currentShellCoverGeneration == pending.transition.coverGeneration &&
			host.measuredViewportWidth == pending.geometry.width &&
			host.measuredViewportHeight == pending.geometry.height
	}

	private fun completePendingCoverCommit(pending: PendingCoverCommit) {
		if (pendingCoverCommit !== pending) return
		pendingCoverCommit = null
		pending.unregisterOnce()
		committedTransition = pending.transition
		host.completeOpaqueShellCoverPreparation(pending.transition.coverGeneration)
	}

	private fun cancelPendingCoverCommit() {
		val pending = pendingCoverCommit ?: return
		pendingCoverCommit = null
		pending.unregisterOnce()
		host.cancelOpaqueShellCoverPreparation(pending.transition.coverGeneration)
	}

	private fun failCoverCommit(
		transition: ReaderRequiredTransition.CommitShellCover
	) {
		try {
			host.cancelOpaqueShellCoverPreparation(transition.coverGeneration)
		} finally {
			onEvent(
				ReaderPresentationEvent.ShellCoverFailed(
					token = transition.token,
					binding = transition.binding
				)
			)
		}
	}

	private fun currentGeometryOrNull(): ViewportGeometry? {
		val width = host.measuredViewportWidth
		val height = host.measuredViewportHeight
		return if (width > 0 && height > 0) ViewportGeometry(width, height) else null
	}

	private fun hostFactsMatch(
		decision: ReaderPresentationDecision,
		transition: ReaderRequiredTransition.CommitShellCover,
		geometry: ViewportGeometry,
		requirePreparedCover: Boolean
	): Boolean =
		host.isAttachedToWindow &&
			decision.requiredTransition == transition &&
			decision.retainsPredecessor(transition.token, transition.binding) &&
			host.currentPresentationBinding == transition.binding &&
			(!requirePreparedCover ||
				host.currentShellCoverGeneration == transition.coverGeneration) &&
			host.measuredViewportWidth == geometry.width &&
			host.measuredViewportHeight == geometry.height
}

internal fun ReaderPresentationEventReceipt?.authorizes(
	event: ReaderPresentationEvent
): Boolean = this != null &&
	this.event == event &&
	(
		disposition == ReaderPresentationEventDisposition.Accepted ||
			disposition == ReaderPresentationEventDisposition.Idempotent
	)

private fun ReaderPresentationDecision.retainsPredecessor(
	token: ReaderPresentationToken,
	binding: ReaderPresentationBinding
): Boolean {
	val required = requiredTransition as? ReaderRequiredTransition.CommitShellCover
	if (required?.token != token || required.binding != binding) return false
	return when (val owner = frameOwner) {
		ReaderPresentationFrameOwner.Neutral -> {
			val pending = authority as? ReaderPresentationAuthority.ShellCoverCommitPending
			pending?.token == token &&
				pending.binding == binding &&
				pending.retainedFrame == ReaderShellCoverRetainedFrame.Neutral(binding)
		}
		is ReaderPresentationFrameOwner.NativePage -> owner.proof.binding == binding
		is ReaderPresentationFrameOwner.Curl -> owner.frame.binding == binding
		else -> false
	}
}
