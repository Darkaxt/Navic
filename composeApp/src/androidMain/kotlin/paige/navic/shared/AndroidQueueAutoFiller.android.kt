package paige.navic.shared

import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.QueueAutoFillRemainingTrigger
import paige.navic.domain.models.queueAutoFillAppendCount
import paige.navic.domain.models.queueAutoFillCandidateSongs
import paige.navic.domain.models.settings.AutoFillQueueSource
import paige.navic.domain.models.shouldAutoFillQueue
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger

internal class AndroidQueueAutoFiller(
	private val scope: CoroutineScope,
	private val preferenceManager: PreferenceManager,
	private val loadLibrarySongs: suspend () -> List<DomainSong>,
	private val mediaItemFactory: AndroidMediaItemFactory,
	private val controller: () -> MediaController?,
	private val state: () -> PlayerUiState,
	private val isAvailable: (String) -> Boolean,
	private val fetchServerSimilarSongs: suspend (songId: String, limit: Int) -> List<DomainSong>,
	private val appendSongs: (List<DomainSong>) -> Unit
) {
	private var autoFillQueueJob: Job? = null

	fun maybeAutoFillQueue() {
		val player = controller() ?: return
		val currentState = state()
		if (!shouldAutoFillQueue(player, currentState)) return
		if (autoFillQueueJob?.isActive == true) return

		autoFillQueueJob = scope.launch {
			try {
				val allSongs = withContext(Dispatchers.IO) {
					loadLibrarySongs()
				}.shuffled()

				val currentPlayer = controller() ?: return@launch
				val latestState = state()
				if (!shouldAutoFillQueue(currentPlayer, latestState)) return@launch

				val appendCount = queueAutoFillAppendCount(
					queueSize = latestState.queue.size,
					targetSize = preferenceManager.autoFillQueueTargetSize
				)
				val serverSimilarSongs = if (
					preferenceManager.autoFillQueueSource == AutoFillQueueSource.SimilarToCurrentSong &&
					latestState.currentSong != null
				) {
					withContext(Dispatchers.IO) {
						fetchServerSimilarSongs(
							latestState.currentSong.id,
							appendCount * 2
						)
					}
				} else {
					emptyList()
				}
				val preferredSongIds = serverSimilarSongs.map { it.id }
				val queuedIds = latestState.queue.mapTo(mutableSetOf()) { it.id }
				val recentQueueSongs = latestState.queue
					.take(latestState.currentIndex + 1)
					.takeLast(10)
				val songsToAppend = queueAutoFillCandidateSongs(
					candidateSongs = (serverSimilarSongs + allSongs).filter { isAvailable(it.id) },
					queuedIds = queuedIds,
					limit = appendCount,
					source = preferenceManager.autoFillQueueSource,
					currentSong = latestState.currentSong,
					preferredSongIds = preferredSongIds,
					recentSongs = recentQueueSongs
				)
				if (songsToAppend.isEmpty()) return@launch

				val mediaItems = withContext(Dispatchers.Default) {
					songsToAppend.map { mediaItemFactory.toMediaItem(it) }
				}
				currentPlayer.addMediaItems(mediaItems)
				appendSongs(songsToAppend)
			} catch (error: Exception) {
				Logger.w("MediaPlayer", "Queue auto-fill failed", error)
			} finally {
				autoFillQueueJob = null
			}
		}
	}

	fun cancel() {
		autoFillQueueJob?.cancel()
		autoFillQueueJob = null
	}

	private fun shouldAutoFillQueue(
		player: MediaController,
		currentState: PlayerUiState
	): Boolean =
		shouldAutoFillQueue(
			autoFillQueue = preferenceManager.autoFillQueue,
			isPlaying = player.isPlaying,
			isRadioQueue = currentState.queue.any { it.id.startsWith("radio_") },
			queueSize = currentState.queue.size,
			currentIndex = currentState.currentIndex,
			remainingTrigger = QueueAutoFillRemainingTrigger,
			targetSize = preferenceManager.autoFillQueueTargetSize
		)
}
