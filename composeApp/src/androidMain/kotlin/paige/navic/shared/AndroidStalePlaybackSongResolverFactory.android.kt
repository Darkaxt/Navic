package paige.navic.shared

import paige.navic.domain.interactors.PlaybackQueueInteractor
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.StalePlaybackSongResolver

internal fun createStalePlaybackSongResolver(
	sessionManager: SessionManager,
	playbackQueueInteractor: PlaybackQueueInteractor
): StalePlaybackSongResolver = StalePlaybackSongResolver(
	fetchSongById = { songId ->
		sessionManager.withApi { api -> api.getSong(songId) }.let { }
	},
	loadCurrentSongs = playbackQueueInteractor::librarySongs
)
