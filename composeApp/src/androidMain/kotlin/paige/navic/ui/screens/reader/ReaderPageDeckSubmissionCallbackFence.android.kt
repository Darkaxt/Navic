package paige.navic.ui.screens.reader

import karacken.curl.DeckRejectionReason
import karacken.curl.PageSurfaceDeckSubmissionResult

internal class ReaderPageDeckSubmissionCallbackFence {
	private data class Captured(
		val generationId: Long,
		val reason: DeckRejectionReason
	)

	private class Active(val generationId: Long) {
		var captured: Captured? = null
	}

	private val lock = Any()
	private var active: Active? = null

	fun onDeckRejected(
		generationId: Long,
		reason: DeckRejectionReason
	): Boolean = synchronized(lock) {
		val current = active
		if (current == null || current.generationId != generationId) {
			return@synchronized false
		}
		check(current.captured == null) {
			"Matching deck rejection was delivered more than once"
		}
		current.captured = Captured(generationId, reason)
		true
	}

	fun submit(
		generationId: Long,
		action: () -> PageSurfaceDeckSubmissionResult
	): PageSurfaceDeckSubmissionResult {
		val owner = synchronized(lock) {
			check(active == null) { "Deck submission fence is already active" }
			Active(generationId).also { active = it }
		}
		val result = try {
			action()
		} finally {
			synchronized(lock) {
				check(active === owner)
				active = null
			}
		}
		val captured = owner.captured
		if (result.status == PageSurfaceDeckSubmissionResult.Status.REJECTED) {
			checkNotNull(captured) {
				"Returned rejection omitted the synchronous listener callback"
			}
			check(captured.reason == result.rejectionReason) {
				"Returned rejection and synchronous callback diverged"
			}
		} else {
			check(captured == null) {
				"Accepted submission emitted a synchronous rejection"
			}
		}
		return result
	}
}
