package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderShellCoverCommitProof

internal fun interface ReaderPresentationDrawRegistration {
	fun unregister()
}

internal interface ReaderPresentationCommitHost {
	val isAttachedToWindow: Boolean
	val currentPresentationBinding: ReaderPresentationBinding?
	val currentShellCoverGeneration: Long?
	val measuredViewportWidth: Int
	val measuredViewportHeight: Int

	fun prepareOpaqueShellCover(coverGeneration: Long)
	fun cancelOpaqueShellCoverPreparation(coverGeneration: Long)
	fun registerShellCoverDrawListener(onDraw: () -> Unit): ReaderPresentationDrawRegistration
	fun postShellCoverAnimationFrame(onFrame: () -> Unit)
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
		val transition = decision.requiredTransition as? ReaderRequiredTransition.CommitShellCover
		if (transition == null) {
			cancelPendingCoverCommit()
			return
		}
		val pending = pendingCoverCommit
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

		pendingCoverCommit = null
		committedTransition = pending.transition
		presentedFrame = Math.incrementExact(presentedFrame)
		onEvent(
			ReaderPresentationEvent.ShellCoverCommitted(
				ReaderShellCoverCommitProof(
					token = pending.transition.token,
					binding = pending.transition.binding,
					coverGeneration = pending.transition.coverGeneration,
					presentedFrame = presentedFrame,
					viewportWidth = pending.geometry.width,
					viewportHeight = pending.geometry.height
				)
			)
		)
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
