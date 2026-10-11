package paige.navic.shared

import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PlaybackAccountBoundary
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger

/** The owning scope and player callbacks are confined to the application thread. */
internal class AndroidPlaybackTransportCoordinator<P : Player>(
	private val scope: CoroutineScope,
	private val controller: () -> P?,
	private val connectedController: StateFlow<P?>,
	private val state: MutableStateFlow<PlayerUiState>,
	private val accountBoundary: PlaybackAccountBoundary,
	private val awaitInitialState: suspend () -> Unit,
	private val restore: suspend (PlayerUiState) -> Boolean,
	private val claimPlayback: () -> Unit,
	private val isPlaybackLoading: () -> Boolean,
	private val onFailure: () -> Unit
) {
	private var job: Job? = null
	private var generation = 0L
	val isPending: Boolean get() = job?.isActive == true

	fun cancel() {
		generation++
		if (isPending) state.update { it.copy(isLoading = false) }
		job?.cancel()
		job = null
	}

	fun run(reason: String, preservePlayIntent: Boolean, beforeWait: () -> Unit, command: (P) -> Unit) {
		cancel()
		val revision = generation
		val account = accountBoundary.capture()
		if (account.ownerId == null) return
		beforeWait()
		job = scope.launch {
			try {
				awaitInitialState()
				state.update { if (it.queue.isNotEmpty()) it.copy(isLoading = true) else it }
				while (isActive && revision == generation && accountBoundary.isCurrent(account)) {
					val player = connectedController.filterNotNull().first()
					if (revision != generation || !accountBoundary.isCurrent(account)) return@launch
					val snapshot = state.value
					if (snapshot.playbackOwnerId != account.ownerId) return@launch
					val restoringEmptyEngine = player.mediaItemCount == 0
					if (restoringEmptyEngine && snapshot.queue.isEmpty()) return@launch
					val ready = restore(snapshot)
					if (controller() !== player) continue
					if (!ready || revision != generation || !accountBoundary.isCurrent(account)) return@launch
					Logger.i("PlaybackRestore", "Applying transport=$reason queueSize=${player.mediaItemCount}")
					command(player)
					if (revision != generation || !accountBoundary.isCurrent(account) || controller() !== player) return@launch
					if (restoringEmptyEngine && preservePlayIntent && !snapshot.isPaused) {
						claimPlayback()
						if (revision == generation && accountBoundary.isCurrent(account) && controller() === player) player.play()
					}
					return@launch
				}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				Logger.e("PlaybackRestore", "Transport reconciliation failed command=$reason; retaining queue", error)
				onFailure()
			} finally {
				if (revision == generation) state.update { it.copy(isLoading = isPlaybackLoading()) }
			}
		}
	}
}
