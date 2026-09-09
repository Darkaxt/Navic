package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import paige.navic.domain.models.pauseBetweenSongsDelayMs
import paige.navic.domain.models.shouldPauseBetweenSongsAfterTransition
import paige.navic.domain.models.shouldPausePlaybackWhenVolumeZero
import paige.navic.domain.models.shouldResumePlaybackAfterVolumeRestored

internal class AndroidPlaybackAutoResumeCoordinator(
	private val scope: CoroutineScope,
	private val player: Player,
	private val currentSession: () -> Any?,
	private val gapSeconds: () -> Int,
	private val pauseOnVolumeZero: () -> Boolean,
	private val diagnostic: (String, String, Long) -> Unit = { _, _, _ -> }
) : Player.Listener {
	private val intent = PlaybackAutoResumeIntent()
	private var gapJob: Job? = null
	private var zeroVolumeResume: PlaybackAutoResumeIntent.Token? = null

	fun invalidate() {
		intent.invalidate()
		zeroVolumeResume = null
		gapJob?.cancel()
		gapJob = null
	}

	fun onPlayerCommand(playerCommand: Int) {
		if (playerCommand in invalidatingCommands) invalidate()
	}

	override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
		if (playWhenReady || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) invalidate()
	}

	override fun onTimelineChanged(timeline: Timeline, reason: Int) {
		if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) invalidate()
	}

	override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
		invalidate()
		if (!shouldPauseBetweenSongsAfterTransition(
			gapSeconds(), reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, player.isPlaying, mediaItem != null
		)) return
		val session = currentSession() ?: return
		val delayMs = pauseBetweenSongsDelayMs(gapSeconds())
		diagnostic("pause-between-songs-paused", "delayMs", delayMs)
		player.pause()
		val token = intent.capture(session, player.currentMediaItem, player.currentMediaItemIndex)
		gapJob = scope.launch {
			delay(delayMs)
			if (player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED && consume(token)) {
				diagnostic("pause-between-songs-resumed", "delayMs", delayMs)
				player.play()
			}
		}
	}

	fun onVolumeChanged(volume: Int) {
		if (shouldPausePlaybackWhenVolumeZero(pauseOnVolumeZero(), player.isPlaying, volume)) {
			val session = currentSession() ?: return
			invalidate()
			diagnostic("volume-zero-paused", "volume", volume.toLong())
			player.pause()
			zeroVolumeResume = intent.capture(session, player.currentMediaItem, player.currentMediaItemIndex)
		} else if (shouldResumePlaybackAfterVolumeRestored(pauseOnVolumeZero(), zeroVolumeResume != null, volume)) {
			val token = zeroVolumeResume
			zeroVolumeResume = null
			if (player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED && consume(token)) {
				diagnostic("volume-restored-resumed", "volume", volume.toLong())
				player.play()
			}
		}
	}

	private fun consume(token: PlaybackAutoResumeIntent.Token?): Boolean =
		currentSession()?.let { intent.consume(token, it, player.currentMediaItem, player.currentMediaItemIndex) } == true

	private companion object {
		val invalidatingCommands = setOf(
			Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_PREPARE,
			Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
			Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
			Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
			Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
			Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
			Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE
		)
	}
}
