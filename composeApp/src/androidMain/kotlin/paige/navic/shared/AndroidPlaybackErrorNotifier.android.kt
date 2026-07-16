package paige.navic.shared

import androidx.media3.common.PlaybackException
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.notice_failed_download
import navic.composeapp.generated.resources.notice_failed_to_play_song
import navic.composeapp.generated.resources.notice_song_not_found
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.models.PlaybackErrorNotice
import paige.navic.domain.models.playbackErrorNotice

internal class AndroidPlaybackErrorNotifier(
	private val snackBarManager: SnackBarManager
) {
	fun notifyFailedDownload() {
		snackBarManager.notify(Res.string.notice_failed_download)
	}

	fun notify(error: PlaybackException) {
		val notice = playbackErrorNotice(
			errorCodeName = error.errorCodeName,
			message = error.message,
			details = error.throwableMessages()
		)
		snackBarManager.notify(
			when (notice) {
				PlaybackErrorNotice.SongNotFound -> Res.string.notice_song_not_found
				PlaybackErrorNotice.FailedDownload -> Res.string.notice_failed_download
				PlaybackErrorNotice.FailedToPlaySong -> Res.string.notice_failed_to_play_song
			}
		)
	}
}

private fun Throwable.throwableMessages(): List<String> {
	val messages = mutableListOf<String>()
	val seen = mutableSetOf<Throwable>()
	var current: Throwable? = this
	while (current != null && seen.add(current)) {
		current.message?.takeIf { it.isNotBlank() }?.let(messages::add)
		current = current.cause
	}
	return messages
}
