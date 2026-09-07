package paige.navic.shared

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.collectionPlaybackOrder
import paige.navic.ui.core.PlayerUiState

internal class AndroidBulkPlaybackCoordinator(
	private val scope: CoroutineScope,
	private val controller: () -> MediaController?,
	private val state: () -> PlayerUiState,
	private val publishState: (PlayerUiState) -> Unit,
	private val mediaItemFactory: AndroidMediaItemFactory,
	private val feedback: PlaybackStartFeedback,
	private val onFailure: (Exception) -> Unit,
	private val cancelPendingRestore: () -> Unit,
	private val clearPlaybackRecovery: (String) -> Unit,
	private val clearPendingQueueSelection: () -> Unit,
	private val cancelQueueAutoFill: () -> Unit,
	private val claimMusicPlayback: () -> Unit
) {
	private val connectedController = MutableStateFlow<MediaController?>(null)

	fun onControllerReady(player: MediaController) {
		connectedController.value = player
	}

	fun onControllerUnavailable() {
		connectedController.value = null
		feedback.cancel()
	}

	fun cancel() = feedback.cancel()

	fun onPlayerEvents(player: Player) {
		if (controller() !== player) return
		if (player.isPlaying || player.playerError != null ||
			!player.playWhenReady || player.playbackState == Player.STATE_ENDED ||
			player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
		) feedback.settle()
	}

	fun playAll(songs: List<DomainSong>, forceShuffle: Boolean) {
		if (songs.isEmpty()) return
		feedback.launch(scope) {
			try {
				prepareAndPlay(songs, forceShuffle)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				onFailure(error)
			}
		}
	}

	private suspend fun prepareAndPlay(songs: List<DomainSong>, forceShuffle: Boolean) {
		cancelPendingRestore()
		clearPlaybackRecovery("play-all")
		clearPendingQueueSelection()
		cancelQueueAutoFill()
		val shuffleEnabled = forceShuffle ||
			(controller()?.shuffleModeEnabled == true) ||
			state().isShuffleEnabled
		val (playbackOrder, mediaItems) = withContext(Dispatchers.Default) {
			val order = collectionPlaybackOrder(songs, shuffleEnabled)
			order to order.map {
				ensureActive()
				mediaItemFactory.toMediaItem(it)
			}
		}
		// Connection waiting belongs to this cancellable request, not saved-state restore.
		val player = controller() ?: connectedController.filterNotNull().first()
		cancelPendingRestore()
		val nextState = state().copy(
			queue = playbackOrder,
			currentIndex = 0,
			upcomingIndexes = emptyList(),
			currentSong = playbackOrder.first(),
			isPaused = false,
			isLoading = true,
			isShuffleEnabled = shuffleEnabled,
			progress = 0f
		)
		publishState(nextState)
		player.shuffleModeEnabled = shuffleEnabled
		player.setMediaItems(mediaItems, 0, 0L)
		player.prepare()
		claimMusicPlayback()
		player.play()
		feedback.starting()
		onPlayerEvents(player)
		feedback.awaitSettled()
	}
}
