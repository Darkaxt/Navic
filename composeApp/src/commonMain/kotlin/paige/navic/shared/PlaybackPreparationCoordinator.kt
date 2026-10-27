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
	private var finishRequest: (() -> Unit)? = null

	fun cancelFromUser(onStartingCancelled: () -> Unit) {
		if (!feedback.isPending) return
		val wasStarting = feedback.state.value?.phase == PlaybackStartPhase.Starting
		cancel()
		if (wasStarting) onStartingCancelled()
	}

	fun cancel() {
		generation++
		val finish = finishRequest
		finishRequest = null
		job?.cancel()
		job = null
		feedback.cancel()
		finish?.invoke()
	}

	fun launch(
		bulk: Boolean = false,
		isOwnerCurrent: () -> Boolean = { true },
		onAccepted: () -> Unit = {},
		onFinished: () -> Unit = {},
		block: suspend Request.() -> Unit
	) {
		if (bulk && feedback.isPending) {
			feedback.launch(scope) {} // Re-show existing progress without replacing its request.
			return
		}
		cancel()
		val request = Request(generation, isOwnerCurrent)
		onAccepted()
		var finished = false
		val finish = {
			if (!finished) { finished = true; onFinished() }
		}
		finishRequest = finish
		val task = if (bulk) feedback.launch(scope) { request.block() }
			else scope.launch { request.block() }.also { job = it }
		task.invokeOnCompletion {
			if (finishRequest === finish) finishRequest = null
			finish()
		}
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
