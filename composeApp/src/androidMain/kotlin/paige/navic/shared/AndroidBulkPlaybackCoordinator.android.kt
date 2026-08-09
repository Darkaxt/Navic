package paige.navic.shared

import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
	private val playbackStateSynchronizer: AndroidPlaybackStateSynchronizer,
	private val clearPlaybackRecovery: (String) -> Unit,
	private val clearPendingQueueSelection: () -> Unit,
	private val cancelQueueAutoFill: () -> Unit,
	private val claimMusicPlayback: () -> Unit
) {
	fun playAll(songs: List<DomainSong>, forceShuffle: Boolean) {
		if (songs.isEmpty()) return
		scope.launch {
			clearPlaybackRecovery("play-all")
			clearPendingQueueSelection()
			cancelQueueAutoFill()
			val shuffleEnabled = forceShuffle ||
				(controller()?.shuffleModeEnabled == true) ||
				state().isShuffleEnabled
			val (playbackOrder, mediaItems) = withContext(Dispatchers.Default) {
				val order = collectionPlaybackOrder(songs, shuffleEnabled)
				order to order.map { mediaItemFactory.toMediaItem(it) }
			}
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

			controller()?.let { player ->
				player.shuffleModeEnabled = shuffleEnabled
				player.setMediaItems(mediaItems, 0, 0L)
				player.prepare()
				claimMusicPlayback()
				player.play()
			} ?: playbackStateSynchronizer.sync(nextState)
		}
	}
}
