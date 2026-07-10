package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.firstPlayableUpcomingIndex
import paige.navic.domain.models.playbackFailureTargetIndex
import paige.navic.ui.core.PlayerUiState

internal class AndroidStablePlaybackRecoveryCoordinator(
	private val diagnostics: AndroidPlaybackDiagnosticsLogger,
	private val isAvailable: (String) -> Boolean,
	private val skipMediaOnError: () -> Boolean,
	private val mediaItemForSong: (DomainSong) -> MediaItem,
	private val claimMusicPlayback: () -> Unit,
	private val notifyPlaybackError: (PlaybackException) -> Unit,
	private val clearRecoveryUi: () -> Unit
) {
	private var refreshedRemoteSourceKey: RemoteSourceRefreshKey? = null

	fun onMediaItemTransition(mediaItem: MediaItem?, currentIndex: Int) {
		val activeKey = mediaItem?.mediaId?.let { mediaId ->
			RemoteSourceRefreshKey(mediaId, currentIndex)
		}
		refreshedRemoteSourceKey = refreshedRemoteSourceKey?.takeIf { it == activeKey }
	}

	fun handleUnavailableAutomaticTransition(player: MediaController, state: PlayerUiState) {
		val currentIndex = player.currentMediaItemIndex
		val song = state.queue.getOrNull(currentIndex) ?: state.currentSong
		applyFinalPolicy(
			player = player,
			state = state,
			song = song,
			currentIndex = currentIndex,
			reason = "unavailable-auto-transition",
			heldEvent = "unavailable-item-held",
			skippedEvent = "unavailable-item-skipped"
		)
	}

	fun handlePlayerError(player: MediaController, state: PlayerUiState, error: PlaybackException) {
		if (refreshCurrentRemoteMediaItem(player, state)) return

		val currentIndex = player.currentMediaItemIndex
		val song = state.queue.getOrNull(currentIndex) ?: state.currentSong
		diagnostics.onHardPlaybackFailure(player, error, song, "remote-refresh-exhausted")
		notifyPlaybackError(error)
		applyFinalPolicy(
			player = player,
			state = state,
			song = song,
			currentIndex = currentIndex,
			reason = error.errorCodeName,
			heldEvent = "playback-error-held",
			skippedEvent = "playback-error-skipped"
		)
	}

	fun clear() {
		refreshedRemoteSourceKey = null
	}

	private fun refreshCurrentRemoteMediaItem(player: MediaController, state: PlayerUiState): Boolean {
		val currentItem = player.currentMediaItem ?: return false
		if (currentItem.localConfiguration?.uri?.scheme == "file") return false
		val currentIndex = player.currentMediaItemIndex
		val key = RemoteSourceRefreshKey(currentItem.mediaId, currentIndex)
		if (refreshedRemoteSourceKey == key) return false
		val song = state.queue.getOrNull(currentIndex)
			?: state.currentSong?.takeIf { it.id == currentItem.mediaId }
			?: return false

		val positionMs = player.currentPosition.coerceAtLeast(0L)
		val shouldResume = player.playWhenReady
		refreshedRemoteSourceKey = key
		player.replaceMediaItem(currentIndex, mediaItemForSong(song))
		player.seekTo(currentIndex, positionMs)
		diagnostics.onPlaybackRetry(
			songId = song.id,
			title = song.title,
			index = currentIndex,
			positionMs = positionMs,
			shouldResume = shouldResume,
			source = "remote-refresh"
		)
		player.prepare()
		if (shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		return true
	}

	private fun applyFinalPolicy(
		player: MediaController,
		state: PlayerUiState,
		song: DomainSong?,
		currentIndex: Int,
		reason: String,
		heldEvent: String,
		skippedEvent: String
	) {
		val targetIndex = playbackFailureTargetIndex(
			skipMediaOnError = skipMediaOnError(),
			nextPlayableIndex = nextPlayableIndex(state, currentIndex, song?.id)
		)
		diagnostics.onPlaybackRecoveryDecision(
			event = if (targetIndex == null) heldEvent else skippedEvent,
			song = song,
			currentIndex = currentIndex,
			targetIndex = targetIndex,
			reason = reason,
			deferredCount = 0,
			fallbackAvailable = targetIndex != null
		)
		if (targetIndex == null) {
			player.pause()
			clearRecoveryUi()
			return
		}

		player.seekTo(targetIndex, 0L)
		player.prepare()
		claimMusicPlayback()
		player.play()
	}

	private fun nextPlayableIndex(state: PlayerUiState, currentIndex: Int, currentSongId: String?): Int? {
		val availableSongIds = state.queue
			.asSequence()
			.map { it.id }
			.filter { songId -> songId != currentSongId && isAvailable(songId) }
			.toSet()
		return firstPlayableUpcomingIndex(
			currentIndex = currentIndex,
			queueSongIds = state.queue.map { it.id },
			availableSongIds = availableSongIds
		)
	}
}

private data class RemoteSourceRefreshKey(
	val songId: String,
	val queueIndex: Int
)
