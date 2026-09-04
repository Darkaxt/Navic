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
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
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

	override fun cancelPresentedFrameRequest(requestId: Long): Boolean =
		surface.cancelPresentedFrameRequest(requestId)
}

internal class ReaderNativePagePresentationPublisher(
	private val frameSource: ReaderNativePagePresentedFrameSource,
	private val currentCandidate: () -> ReaderNativePagePresentationCandidate?,
	private val handoffTimeoutScheduler: ReaderPageRelocationDispatchTimeoutScheduler? = null,
	private val handoffTimeoutMillis: Long = 10_000L,
	private val onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
) {
	private data class PendingFrame(
		val requestId: Long,
		val candidate: ReaderNativePagePresentationCandidate
	)

	private data class PendingHandoffTimeout(
		val requestId: Long,
		val candidate: ReaderNativePagePresentationCandidate,
		val action: Runnable
	)

	private var pendingFrame: PendingFrame? = null
	private var pendingHandoffTimeout: PendingHandoffTimeout? = null
	private var lastPublishedCandidate: ReaderNativePagePresentationCandidate? = null
	private var disposed = false

	init {
		require(handoffTimeoutMillis > 0L)
	}

	fun update() {
		if (disposed) return
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
			cancelHandoffTimeout(pending.requestId)
			frameSource.cancelPresentedFrameRequest(pending.requestId)
		}
		if (
			candidate == null ||
			candidate == lastPublishedCandidate ||
			pendingFrame?.candidate == candidate
		) return

		var requestId = PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID
		requestId = frameSource.requestNextPresentedFrame { presentedRequestId ->
			onPresentedFrame(requestId, presentedRequestId, candidate)
		}
		if (requestId == PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID) return
		pendingFrame = PendingFrame(requestId, candidate)
		armHandoffTimeout(requestId, candidate)
	}

	fun dispose() {
		if (disposed) return
		disposed = true
		pendingFrame?.let { frameSource.cancelPresentedFrameRequest(it.requestId) }
		pendingFrame = null
		cancelHandoffTimeout()
	}

	private fun armHandoffTimeout(
		requestId: Long,
		candidate: ReaderNativePagePresentationCandidate
	) {
		val scheduler = handoffTimeoutScheduler ?: return
		if (
			candidate.handoffDirection != ReaderLiveEngineHandoffDirection.LiveEngineToNative ||
			candidate.transitionToken == null
		) return
		lateinit var action: Runnable
		action = Runnable { onHandoffTimeout(requestId, candidate, action) }
		pendingHandoffTimeout = PendingHandoffTimeout(requestId, candidate, action)
		if (!scheduler.postDelayed(action, handoffTimeoutMillis)) action.run()
	}

	private fun onHandoffTimeout(
		requestId: Long,
		candidate: ReaderNativePagePresentationCandidate,
		action: Runnable
	) {
		val timeout = pendingHandoffTimeout
		if (
			disposed || timeout?.requestId != requestId ||
			timeout.candidate != candidate || timeout.action !== action
		) return
		pendingHandoffTimeout = null
		val pending = pendingFrame
		if (pending?.requestId != requestId || pending.candidate != candidate) return
		pendingFrame = null
		frameSource.cancelPresentedFrameRequest(requestId)
		onEvent(ReaderPresentationEvent.TimedOut(candidate.transitionToken))
	}

	private fun cancelHandoffTimeout(requestId: Long? = null) {
		val timeout = pendingHandoffTimeout ?: return
		if (requestId != null && timeout.requestId != requestId) return
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
		cancelHandoffTimeout(expectedRequestId)
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
		val transition: ReaderRequiredTransition.ExposeLiveEngine
	) {
		var emittedEvent: ReaderPresentationEvent? = null
		var acceptedReceipt: ReaderPresentationEventReceipt? = null
		var receiptDispatchInProgress = false
	}

	private var currentDecision: ReaderPresentationDecision? = null
	private var pendingCoverCommit: PendingCoverCommit? = null
	private var pendingLiveEngineExposure: PendingLiveEngineExposure? = null
	private var committedTransition: ReaderRequiredTransition.CommitShellCover? = null
	private var presentedFrame = 0L
	private var disposed = false

	fun update(decision: ReaderPresentationDecision) {
		if (disposed) return
		currentDecision = decision
		host.applyPresentationFrameOwner(decision)
		if (requestLiveEngineExposureIfRequired(decision)) return
		updateLiveEngineExposure(decision)
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
		cancelPendingCoverCommit()
		cancelPendingLiveEngineExposure()
		liveEngineVisualHandoff?.close()
		currentDecision = null
		committedTransition = null
	}

	private fun requestLiveEngineExposureIfRequired(
		decision: ReaderPresentationDecision
	): Boolean {
		if (!liveEngineExposureRequired()) return false
		if (
			!host.isAttachedToWindow ||
			host.currentPresentationBinding != decision.targetBinding ||
			decision.requiredTransition != ReaderRequiredTransition.None ||
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

	private fun updateLiveEngineExposure(decision: ReaderPresentationDecision) {
		if (!liveEngineExposureRequired()) {
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
				pending.transition != transition -> cancelPendingLiveEngineExposure()
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
			handoff.await(transition.token, transition.binding) { result ->
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
