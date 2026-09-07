package paige.navic.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlaybackStartPhase { Preparing, Starting }

data class PlaybackStartStatus(
	val phase: PlaybackStartPhase,
	val visible: Boolean = true
)

/** Owned by the player; commands and playback callbacks run on the main thread. */
class PlaybackStartFeedback {
	private val _state = MutableStateFlow<PlaybackStartStatus?>(null)
	val state = _state.asStateFlow()
	private var active: Request? = null
	val isPending: Boolean get() = active != null

	internal class Request {
		lateinit var job: Job
		val settled = CompletableDeferred<Unit>()
	}

	internal fun launch(scope: CoroutineScope, block: suspend () -> Unit) {
		if (active != null) {
			_state.value = _state.value?.copy(visible = true)
			return
		}
		val request = Request()
		active = request
		_state.value = PlaybackStartStatus(PlaybackStartPhase.Preparing)
		request.job = scope.launch(start = CoroutineStart.LAZY) {
			block()
		}
		request.job.invokeOnCompletion {
			if (active === request) {
				active = null
				_state.value = null
			}
		}
		request.job.start()
	}

	internal fun starting() {
		_state.value = _state.value?.copy(phase = PlaybackStartPhase.Starting)
	}

	internal suspend fun awaitSettled() {
		active?.settled?.await()
	}

	internal fun settle() {
		if (_state.value?.phase == PlaybackStartPhase.Starting) active?.settled?.complete(Unit)
	}

	fun hide() {
		_state.value = _state.value?.copy(visible = false)
	}

	internal fun cancel() {
		val request = active
		active = null
		_state.value = null
		request?.job?.cancel()
	}
}
