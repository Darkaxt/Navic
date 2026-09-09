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
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSongCollection
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
	private val claimMusicPlayback: () -> Unit,
	private val currentSession: () -> Any?,
	private val onStartAccepted: () -> Unit,
	private val loadSongRadio: suspend (DomainSong) -> List<DomainSong>,
	private val clearPlaybackOrigin: () -> Unit
) {
	private val connectedController = MutableStateFlow<MediaController?>(null)
	private val preparation = PlaybackPreparationCoordinator(scope, feedback)
	private val radioMediaItemFactory = AndroidRadioMediaItemFactory()

	fun onControllerReady(player: MediaController) {
		if (connectedController.value?.let { it !== player } == true) cancel()
		connectedController.value = player
	}

	fun onControllerUnavailable() {
		connectedController.value = null
		cancel()
	}

	fun cancel() {
		preparation.cancel()
		cancelPendingRestore()
		clearPendingQueueSelection()
	}

	fun onPlayerEvents(player: Player) {
		if (controller() !== player) return
		if (player.isPlaying || player.playerError != null ||
			!player.playWhenReady || player.playbackState == Player.STATE_ENDED ||
			player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
		) feedback.settle()
	}

	fun playAll(songs: List<DomainSong>, forceShuffle: Boolean) {
		if (songs.isEmpty()) return
		launchStart("play-all", bulk = true) {
			prepareAndPlay(songs, forceShuffle)
		}
	}

	private fun launchStart(
		reason: String,
		bulk: Boolean = false,
		block: suspend PlaybackPreparationCoordinator.Request.() -> Unit
	) {
		val session = currentSession()
		val initialController = controller()
		preparation.launch(
			bulk = bulk,
			isOwnerCurrent = {
				session != null && currentSession() === session &&
					(initialController == null || controller() === initialController)
			},
			onAccepted = {
				cancelPendingRestore()
				clearPlaybackRecovery(reason)
				clearPendingQueueSelection()
				cancelQueueAutoFill()
				onStartAccepted()
			}
		) {
			try {
				block()
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				commit { onFailure(error) }
			}
		}
	}

	private suspend fun PlaybackPreparationCoordinator.Request.prepareAndPlay(songs: List<DomainSong>, forceShuffle: Boolean) {
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
		if (!commit {
			val nextState = state().copy(
				queue = playbackOrder,
				currentIndex = 0,
				upcomingIndexes = emptyList(),
				shuffleOrder = null,
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
		}) return
		feedback.awaitSettled()
	}

	fun playCollection(collection: DomainSongCollection, startSong: DomainSong) {
		launchStart("play-collection") {
			val (items, newCollection) = withContext(Dispatchers.Default) {
				val songs = if (collection is DomainAlbum) {
					collection.songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
				} else collection.songs
				songs.map { ensureActive(); mediaItemFactory.toMediaItem(it) } to songs
			}
			if (newCollection.isEmpty()) return@launchStart
			val startIndex = newCollection.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)
			val player = controller() ?: connectedController.filterNotNull().first()
			commit {
				publishState(state().copy(queue = newCollection, currentIndex = startIndex,
					currentSong = newCollection[startIndex], upcomingIndexes = emptyList(),
					shuffleOrder = null, progress = 0f, isPaused = false))
				player.setMediaItems(items, startIndex, 0L)
				player.prepare()
				claimMusicPlayback()
				player.play()
			}
		}
	}

	fun startSongRadio(song: DomainSong) {
		launchStart("song-radio") {
			val radioQueue = withContext(Dispatchers.IO) { loadSongRadio(song) }
			if (radioQueue.isEmpty()) return@launchStart
			val mediaItems = withContext(Dispatchers.Default) {
				radioQueue.map { ensureActive(); mediaItemFactory.toMediaItem(it) }
			}
			val player = controller() ?: connectedController.filterNotNull().first()
			commit {
				clearPlaybackOrigin()
				publishState(state().copy(queue = radioQueue, currentIndex = 0,
					currentSong = radioQueue.first(), currentCollection = null,
					upcomingIndexes = emptyList(), shuffleOrder = null, isShuffleEnabled = false,
					isPaused = false, progress = 0f))
				player.shuffleModeEnabled = false
				player.setMediaItems(mediaItems, 0, 0L)
				player.prepare()
				claimMusicPlayback()
				player.play()
			}
		}
	}

	fun playRadio(radio: DomainRadio) {
		launchStart("play-radio") {
			val radioItem = radioMediaItemFactory.create(radio)
			val player = controller() ?: connectedController.filterNotNull().first()
			commit {
				clearPlaybackOrigin()
				publishState(state().copy(queue = listOf(radioItem.song), currentIndex = 0,
					currentSong = radioItem.song, upcomingIndexes = emptyList(), shuffleOrder = null,
					isLoading = true, isPaused = false, progress = 0f))
				player.stop()
				player.setMediaItem(radioItem.mediaItem)
				player.prepare()
				claimMusicPlayback()
				player.play()
			}
		}
	}
}
