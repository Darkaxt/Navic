package paige.navic.shared

import androidx.media3.session.MediaController
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.firstPlayableUpcomingIndex
import paige.navic.domain.models.recoveredDownloadTargetIndex
import paige.navic.domain.models.shouldReplayLastPlayable
import paige.navic.ui.core.PlayerUiState

internal class AndroidPlaybackDownloadRecoveryCoordinator(
	private val downloadManager: DownloadManager,
	private val diagnostics: AndroidPlaybackDiagnosticsLogger,
	private val isAvailable: (String) -> Boolean,
	private val claimMusicPlayback: () -> Unit,
	private val onWaitForDeferredDownload: (song: DomainSong, shouldResume: Boolean) -> Unit,
	private val onDeferredCurrentReady: (songId: String, targetIndex: Int) -> Unit,
	private val updateQueue: (fromIndex: Int, toIndex: Int) -> Unit
) {
	private var lastPlayableSnapshot: LastPlayableSnapshot? = null
	private val deferredPlaybackDownloads = linkedMapOf<String, DeferredPlaybackDownload>()

	fun rememberLastPlayableSong(player: MediaController, state: PlayerUiState) {
		val index = player.currentMediaItemIndex
		val song = state.queue.getOrNull(index) ?: return
		if (!isAvailable(song.id)) return
		lastPlayableSnapshot = LastPlayableSnapshot(song = song)
	}

	fun deferCurrentAndContinueOrReplay(player: MediaController, state: PlayerUiState, reason: String): Boolean {
		val currentIndex = player.currentMediaItemIndex
		val mediaId = player.currentMediaItem?.mediaId
		val song = state.queue.getOrNull(currentIndex)
			?: state.currentSong?.takeIf { it.id == mediaId }
			?: return false

		deferredPlaybackDownloads[song.id] = DeferredPlaybackDownload(
			song = song,
			reason = reason
		)
		downloadManager.prefetchPlaybackSongs(listOf(song))
		diagnostics.onDeferredDownloadRequested(song, currentIndex, reason, deferredPlaybackDownloads.size)

		val availableSongIds = state.queue
			.asSequence()
			.map { it.id }
			.filter { id -> id != song.id && isAvailable(id) }
			.toSet()
		val targetIndex = firstPlayableUpcomingIndex(
			currentIndex = currentIndex,
			queueSongIds = state.queue.map { it.id },
			availableSongIds = availableSongIds
		)
		if (targetIndex != null) {
			diagnostics.onPlaybackRecoveryDecision(
				event = "skip-to-next-playable",
				song = song,
				currentIndex = currentIndex,
				targetIndex = targetIndex,
				reason = reason,
				deferredCount = deferredPlaybackDownloads.size,
				fallbackAvailable = lastPlayableSnapshot != null
			)
			player.seekTo(targetIndex, 0L)
			player.prepare()
			claimMusicPlayback()
			player.play()
			return true
		}

		if (
			shouldReplayLastPlayable(
				hasLastPlayable = lastPlayableSnapshot != null,
				hasPlayableUpcoming = false,
				hasDeferredDownloads = deferredPlaybackDownloads.isNotEmpty()
			) &&
			replayLastPlayable(player, state, reason)
		) {
			return true
		}

		diagnostics.onPlaybackRecoveryDecision(
			event = "waiting-for-deferred-download",
			song = song,
			currentIndex = currentIndex,
			targetIndex = null,
			reason = reason,
			deferredCount = deferredPlaybackDownloads.size,
			fallbackAvailable = false
		)
		onWaitForDeferredDownload(song, player.playWhenReady || !state.isPaused)
		return true
	}

	fun promoteReadyDeferredDownloads(player: MediaController, state: PlayerUiState, downloadedMap: Map<String, String>) {
		if (deferredPlaybackDownloads.isEmpty()) return
		val readyRecoveries = deferredPlaybackDownloads.values
			.filter { recovery -> downloadedMap[recovery.song.id] != null }
		if (readyRecoveries.isEmpty()) return

		readyRecoveries.forEach { recovery ->
			val songId = recovery.song.id
			val sourceIndex = state.queue.indexOfFirst { it.id == songId }
			if (sourceIndex !in 0 until player.mediaItemCount) {
				deferredPlaybackDownloads.remove(songId)
				return@forEach
			}

			val targetIndex = recoveredDownloadTargetIndex(
				currentIndex = player.currentMediaItemIndex,
				queueSize = player.mediaItemCount
			)
			val moveTargetIndex = if (sourceIndex < targetIndex) targetIndex - 1 else targetIndex
			val normalizedTargetIndex = moveTargetIndex.coerceIn(0, player.mediaItemCount - 1)
			if (sourceIndex != normalizedTargetIndex) {
				player.moveMediaItem(sourceIndex, normalizedTargetIndex)
				updateQueue(sourceIndex, normalizedTargetIndex)
				diagnostics.onPlaybackRecoveryDecision(
					event = "deferred-download-reinserted",
					song = recovery.song,
					currentIndex = player.currentMediaItemIndex,
					targetIndex = normalizedTargetIndex,
					reason = recovery.reason,
					deferredCount = deferredPlaybackDownloads.size,
					fallbackAvailable = lastPlayableSnapshot != null
				)
			}

			deferredPlaybackDownloads.remove(songId)
			diagnostics.onDeferredDownloadReady(
				songId = songId,
				title = recovery.song.title,
				targetIndex = normalizedTargetIndex,
				deferredCount = deferredPlaybackDownloads.size
			)
			if (!player.isPlaying) {
				onDeferredCurrentReady(songId, normalizedTargetIndex)
			}
		}
	}

	fun clear() {
		lastPlayableSnapshot = null
		deferredPlaybackDownloads.clear()
	}

	private fun replayLastPlayable(player: MediaController, state: PlayerUiState, reason: String): Boolean {
		val snapshot = lastPlayableSnapshot ?: return false
		val targetIndex = state.queue.indexOfFirst { it.id == snapshot.song.id }
		if (targetIndex !in 0 until player.mediaItemCount) return false
		diagnostics.onReplayLastPlayable(
			song = snapshot.song,
			index = targetIndex,
			reason = reason,
			deferredCount = deferredPlaybackDownloads.size
		)
		player.seekTo(targetIndex, 0L)
		player.prepare()
		claimMusicPlayback()
		player.play()
		return true
	}
}

private data class LastPlayableSnapshot(
	val song: DomainSong
)

private data class DeferredPlaybackDownload(
	val song: DomainSong,
	val reason: String
)
