package paige.navic.shared

import android.app.Application
import android.content.Intent
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.activeArtworkUrl
import paige.navic.domain.models.effectiveAurralArtworkPriority
import paige.navic.domain.models.externalFallbackArtworkUrl
import paige.navic.domain.models.shouldSendNowPlayingWidgetUpdate
import paige.navic.domain.models.visiblePlaybackCoverArtId
import paige.navic.domain.models.visiblePlaybackImageUrl
import paige.navic.domain.repositories.MusicBrainzArtworkRepository

internal class AndroidNowPlayingBroadcaster(
	private val application: Application,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val musicBrainzArtworkRepository: MusicBrainzArtworkRepository
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

	private fun currentArtworkUrl(song: DomainSong): String? {
		val externalArtworkUrl = externalFallbackArtworkUrl(
			serverCoverArtId = song.coverArtId,
			externalArtworkUrl = musicBrainzArtworkRepository.artworkBySongId.value[song.id]?.imageUrl
		)
		val artworkPriority = effectiveAurralArtworkPriority(
			aurralEnabled = preferenceManager.aurralEnabled,
			configuredPriority = preferenceManager.coverArtworkPriority
		)
		val visibleCoverArtId = visiblePlaybackCoverArtId(
			serverCoverArtId = song.coverArtId,
			externalArtworkUrl = externalArtworkUrl,
			priority = artworkPriority
		)
		val visibleImageUrl = visiblePlaybackImageUrl(
			serverCoverArtId = song.coverArtId,
			externalArtworkUrl = externalArtworkUrl,
			priority = artworkPriority
		)
		return activeArtworkUrl(
			serverArtworkUrl = visibleCoverArtId?.let { sessionManager.getCoverArtUrl(it) },
			externalArtworkUrl = visibleImageUrl
		)
	}
}
