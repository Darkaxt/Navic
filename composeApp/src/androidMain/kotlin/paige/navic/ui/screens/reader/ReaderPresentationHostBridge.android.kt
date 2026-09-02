package paige.navic.ui.screens.reader

import karacken.curl.PageSurfaceView
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderShellCoverCommitProof

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
}

internal data class ReaderNativePagePresentationCandidate(
	val binding: ReaderPresentationBinding,
	val transitionToken: ReaderPresentationToken?,
	val visualPageIndex: Int,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val preparationFacts: ReaderPagePreparationFacts
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
	val shellCoverSelected: Boolean
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
			preparationFacts = preparationFacts
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
	private val onEvent: (ReaderPresentationEvent) -> Unit
) {
	private data class PendingFrame(
		val requestId: Long,
		val candidate: ReaderNativePagePresentationCandidate
	)

	private var pendingFrame: PendingFrame? = null
	private var lastPublishedCandidate: ReaderNativePagePresentationCandidate? = null
	private var disposed = false

	fun update() {
		if (disposed) return
		val candidate = currentCandidate()
		val pending = pendingFrame
		if (pending != null && pending.candidate != candidate) {
			pendingFrame = null
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
	}

	fun dispose() {
		if (disposed) return
		disposed = true
		pendingFrame?.let { frameSource.cancelPresentedFrameRequest(it.requestId) }
		pendingFrame = null
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
		val candidate = currentCandidate()
		if (candidate != armedCandidate || candidate == lastPublishedCandidate) return
		lastPublishedCandidate = candidate
		onEvent(
			ReaderPresentationEvent.NativePagePresented(
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
		)
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
	private val onEvent: (ReaderPresentationEvent) -> Unit
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
		var receiptEmitted = false
		var emittedProof: ReaderShellCoverCommitProof? = null

		fun unregisterOnce() {
			if (registrationRemoved) return
			registrationRemoved = true
			registration?.unregister()
		}
	}

	private var currentDecision: ReaderPresentationDecision? = null
	private var pendingCoverCommit: PendingCoverCommit? = null
	private var committedTransition: ReaderRequiredTransition.CommitShellCover? = null
	private var presentedFrame = 0L
	private var disposed = false

	fun update(decision: ReaderPresentationDecision) {
		if (disposed) return
		currentDecision = decision
		val pending = pendingCoverCommit
		if (pending?.receiptEmitted == true) {
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
	}

	fun dispose() {
		if (disposed) return
		disposed = true
		cancelPendingCoverCommit()
		currentDecision = null
		committedTransition = null
	}

	private fun beginCoverCommit(
		decision: ReaderPresentationDecision,
		transition: ReaderRequiredTransition.CommitShellCover
	) {
		val geometry = currentGeometryOrNull() ?: return
		if (!hostFactsMatch(decision, transition, geometry, requirePreparedCover = false)) return

		host.prepareOpaqueShellCover(transition.coverGeneration)
		if (!hostFactsMatch(decision, transition, geometry, requirePreparedCover = true)) {
			host.cancelOpaqueShellCoverPreparation(transition.coverGeneration)
			return
		}

		val pending = PendingCoverCommit(transition, geometry)
		pendingCoverCommit = pending
		val registration = host.registerShellCoverDrawListener {
			onCoverDrawn(pending)
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
		pending.receiptEmitted = true
		onEvent(ReaderPresentationEvent.ShellCoverCommitted(proof))
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

private fun ReaderPresentationDecision.retainsPredecessor(
	token: ReaderPresentationToken,
	binding: ReaderPresentationBinding
): Boolean {
	val required = requiredTransition as? ReaderRequiredTransition.CommitShellCover
	if (required?.token != token || required.binding != binding) return false
	return when (val owner = frameOwner) {
		is ReaderPresentationFrameOwner.NativePage -> owner.proof.binding == binding
		is ReaderPresentationFrameOwner.Curl -> owner.frame.binding == binding
		else -> false
	}
}
