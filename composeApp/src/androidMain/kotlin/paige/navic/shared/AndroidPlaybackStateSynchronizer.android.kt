package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.restoredShuffleOrder
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger
import kotlin.coroutines.resume

internal interface AndroidPlaybackStateSynchronizer {
	fun sync(state: PlayerUiState)
	fun onControllerReady()
	fun cancelPendingRestore()
	suspend fun restoreForCommand(state: PlayerUiState): Boolean
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DefaultAndroidPlaybackStateSynchronizer(
	private val scope: CoroutineScope,
	private val controller: () -> Player?,
	private val mediaItemForSong: (DomainSong) -> MediaItem,
	private val claimMusicPlayback: () -> Unit,
	private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AndroidPlaybackStateSynchronizer {
	private var pendingState: PlayerUiState? = null
	private var restoreJob: Job? = null
	private var restoreGeneration = 0L

	override fun cancelPendingRestore() {
		restoreGeneration++
		restoreJob?.cancel()
		restoreJob = null
		pendingState = null
	}

	override fun sync(state: PlayerUiState) {
		cancelPendingRestore()
		val generation = restoreGeneration
		restoreJob = scope.launch {
			val player = controller()
			if (player == null) {
				pendingState = state
				return@launch
			}
			try {
				restore(player, state, generation)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				Logger.e("PlaybackRestore", "Initial engine restore failed; retained queue is available for retry", error)
			}
		}
	}

	override fun onControllerReady() {
		val state = pendingState ?: return
		pendingState = null
		sync(state)
	}

	override suspend fun restoreForCommand(state: PlayerUiState): Boolean {
		cancelPendingRestore()
		val player = controller() ?: return false
		return restore(player, state.copy(isPaused = true), restoreGeneration)
	}

	private suspend fun restore(player: Player, state: PlayerUiState, generation: Long): Boolean {
		if (player.mediaItemCount > 0) return true
		if (state.queue.isEmpty()) return false
		Logger.i("PlaybackRestore", "Reconciling empty engine queueSize=${state.queue.size} index=${state.currentIndex}")
		val mediaItems = withContext(mappingDispatcher) {
			state.queue.map { song ->
				ensureActive()
				mediaItemForSong(song)
			}
		}
		if (controller() !== player || restoreGeneration != generation) return false
		// A live queue installed while mapping owns the engine; never overwrite it with a stale snapshot.
		if (player.mediaItemCount > 0) return true
		val index = state.currentIndex.takeIf(mediaItems.indices::contains) ?: 0
		val durationMs = state.queue[index].duration.inWholeMilliseconds.coerceAtLeast(0L)
		val progress = state.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
		val position = (progress * durationMs).toLong()
		player.setMediaItems(mediaItems, index, position)
		player.repeatMode = state.repeatMode
		player.playbackParameters = PlaybackParameters(state.playbackSpeed, state.playbackPitch)
		player.shuffleModeEnabled = state.isShuffleEnabled
		val shuffleOrder = if (state.isShuffleEnabled) {
			restoredShuffleOrder(mediaItems.size, index, state.upcomingIndexes, state.shuffleOrder)
		} else null
		if (shuffleOrder != null) {
			when (player) {
				is ExoPlayer -> player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(shuffleOrder.toIntArray(), 0L))
				is MediaController -> awaitShuffleOrder(player, shuffleOrder)
			}
		}
		if (controller() !== player || restoreGeneration != generation) return false
		player.prepare()
		if (!state.isPaused) {
			claimMusicPlayback()
			player.play()
		}
		Logger.i("PlaybackRestore", "Engine ready queueSize=${player.mediaItemCount} index=$index positionMs=$position")
		return true
	}

	private suspend fun awaitShuffleOrder(player: MediaController, order: List<Int>) = suspendCancellableCoroutine { continuation ->
		val future = PlaybackService.restoreShuffleOrder(player, order)
		continuation.invokeOnCancellation { future.cancel(false) }
		Futures.addCallback(
			future,
			object : FutureCallback<SessionResult> {
				override fun onSuccess(result: SessionResult?) {
					if (result?.resultCode != SessionResult.RESULT_SUCCESS) {
						Logger.w("MediaPlayer", "Persisted shuffle order was rejected; using a new order")
					}
					if (continuation.isActive) continuation.resume(Unit)
				}

				override fun onFailure(error: Throwable) {
					Logger.w("MediaPlayer", "Failed to restore persisted shuffle order", error)
					if (continuation.isActive) continuation.resume(Unit)
				}
			},
			MoreExecutors.directExecutor()
		)
	}
}
