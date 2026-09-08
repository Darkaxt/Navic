package paige.navic.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-thread admission and commit boundary for all queue-replacing preparation. */
internal class PlaybackPreparationCoordinator(
	private val scope: CoroutineScope,
	private val feedback: PlaybackStartFeedback
) {
	private var generation = 0L
	private var job: Job? = null

	fun cancel() {
		generation++
		job?.cancel()
		job = null
		feedback.cancel()
	}

	fun launch(
		bulk: Boolean = false,
		isOwnerCurrent: () -> Boolean = { true },
		onAccepted: () -> Unit = {},
		block: suspend Request.() -> Unit
	) {
		if (bulk && feedback.isPending) {
			feedback.launch(scope) {} // Re-show existing progress without replacing its request.
			return
		}
		cancel()
		val request = Request(generation, isOwnerCurrent)
		onAccepted()
		if (bulk) feedback.launch(scope) { request.block() }
		else job = scope.launch { request.block() }
	}

	inner class Request internal constructor(
		private val requestGeneration: Long,
		private val isOwnerCurrent: () -> Boolean
	) {
		suspend fun commit(block: () -> Unit): Boolean {
			currentCoroutineContext().ensureActive()
			if (generation != requestGeneration || !isOwnerCurrent()) return false
			block()
			return true
		}
	}
}
