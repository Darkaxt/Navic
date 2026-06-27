package paige.navic.shared

import android.app.Application
import android.content.Intent
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.PlaybackArtworkResolution
import paige.navic.domain.models.shouldSendNowPlayingWidgetUpdate

internal class AndroidNowPlayingBroadcaster(
	private val application: Application,
	private val sessionManager: SessionManager,
	private val playbackArtworkForSong: (DomainSong) -> PlaybackArtworkResolution
) {
	private var lastSongId: String? = null
	private var lastIsPlaying: Boolean? = null

	fun send(
		currentSong: DomainSong?,
		isPlaying: Boolean,
		force: Boolean = false
	) {
		val currentSongId = currentSong?.id
		val previousIsPlaying = lastIsPlaying
		if (
			!force &&
			previousIsPlaying != null &&
			!shouldSendNowPlayingWidgetUpdate(
				previousSongId = lastSongId,
				currentSongId = currentSongId,
				previousIsPlaying = previousIsPlaying,
				currentIsPlaying = isPlaying
			)
		) {
			return
		}

		lastSongId = currentSongId
		lastIsPlaying = isPlaying

		val intent = Intent("${application.packageName}.NOW_PLAYING_UPDATED").apply {
			setPackage(application.packageName)
			putExtra("isPlaying", isPlaying)
			putExtra("title", currentSong?.title ?: "Unknown song")
			putExtra("artist", currentSong?.artistName ?: "Unknown artist")
			putExtra("artUrl", currentSong?.let(::currentArtworkUrl))
		}

		application.sendBroadcast(intent)
	}

	private fun currentArtworkUrl(song: DomainSong): String? =
		playbackArtworkForSong(song).let { artwork ->
			artwork.imageUrl ?: artwork.coverArtId?.let(sessionManager::getCoverArtUrl)
		}
}
