package paige.navic.shared

import androidx.core.net.toUri
import androidx.media3.session.MediaController
import paige.navic.domain.manager.DownloadManager
import java.io.File

internal interface AndroidDownloadedMediaRecovery {
	fun recover(player: MediaController): Boolean
}

internal class DefaultAndroidDownloadedMediaRecovery(
	private val downloadManager: DownloadManager,
	private val diagnostics: AndroidPlaybackDiagnosticsLogger,
	private val claimMusicPlayback: () -> Unit
) : AndroidDownloadedMediaRecovery {
	override fun recover(player: MediaController): Boolean {
		val currentItem = player.currentMediaItem ?: return false
		val mediaId = currentItem.mediaId
		val localPath = downloadManager.getDownloadedFilePath(mediaId) ?: return false
		if (currentItem.localConfiguration?.uri?.scheme == "file") return false

		val positionMs = player.currentPosition.coerceAtLeast(0L)
		val shouldResume = player.playWhenReady
		val index = player.currentMediaItemIndex
		player.replaceMediaItem(index, currentItem.buildUpon().setUri(File(localPath).toUri()).build())
		player.seekTo(index, positionMs)
		diagnostics.onRecoveryLocalFileReady(mediaId, positionMs, shouldResume, "player-error")
		player.prepare()
		if (shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		return true
	}
}
