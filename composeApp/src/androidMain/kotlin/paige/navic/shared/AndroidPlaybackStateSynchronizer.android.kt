package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.restoredShuffleOrder
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger

internal interface AndroidPlaybackStateSynchronizer {
	fun sync(state: PlayerUiState)
	fun onControllerReady()
}

internal class DefaultAndroidPlaybackStateSynchronizer(
	private val scope: CoroutineScope,
	private val controller: () -> MediaController?,
	private val mediaItemForSong: (DomainSong) -> MediaItem,
	private val claimMusicPlayback: () -> Unit
) : AndroidPlaybackStateSynchronizer {
	private var pendingState: PlayerUiState? = null

	override fun sync(state: PlayerUiState) {
		scope.launch {
			val player = controller()
			if (player == null) {
				pendingState = state
				return@launch
			}
			restore(player, state)
		}
	}

	override fun onControllerReady() {
		val state = pendingState ?: return
		pendingState = null
		sync(state)
	}

	private suspend fun restore(player: MediaController, state: PlayerUiState) {
		if (state.queue.isEmpty() || player.mediaItemCount > 0) return
		val mediaItems = withContext(Dispatchers.Default) { state.queue.map(mediaItemForSong) }
		player.setMediaItems(mediaItems)
		player.repeatMode = state.repeatMode
		player.playbackParameters = PlaybackParameters(state.playbackSpeed, state.playbackPitch)
		val index = state.currentIndex.takeIf(mediaItems.indices::contains) ?: 0
		val durationMs = state.queue.getOrNull(index)?.duration?.inWholeMilliseconds ?: 0L
		val position = if (durationMs > 0) (state.progress * durationMs).toLong() else 0L
		val shuffleOrder = if (state.isShuffleEnabled) {
			restoredShuffleOrder(mediaItems.size, index, state.upcomingIndexes)
		} else null
		if (shuffleOrder == null) {
			complete(player, state, index, position)
			return
		}
		Futures.addCallback(
			PlaybackService.restoreShuffleOrder(player, shuffleOrder),
			object : FutureCallback<SessionResult> {
				override fun onSuccess(result: SessionResult?) {
					if (result?.resultCode != SessionResult.RESULT_SUCCESS) {
						Logger.w("MediaPlayer", "Persisted shuffle order was rejected; using a new order")
					}
					complete(player, state, index, position)
				}

				override fun onFailure(error: Throwable) {
					Logger.w("MediaPlayer", "Failed to restore persisted shuffle order", error)
					complete(player, state, index, position)
				}
			},
			MoreExecutors.directExecutor()
		)
	}

	private fun complete(player: MediaController, state: PlayerUiState, index: Int, position: Long) {
		if (controller() !== player) return
		player.shuffleModeEnabled = state.isShuffleEnabled
		player.seekTo(index, position)
		player.prepare()
		if (!state.isPaused) {
			claimMusicPlayback()
			player.play()
		}
	}
}
