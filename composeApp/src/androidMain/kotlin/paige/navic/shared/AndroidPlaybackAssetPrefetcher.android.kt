package paige.navic.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.playbackArtworkPrefetchIndexes
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger

internal class AndroidPlaybackAssetPrefetcher(
	private val scope: CoroutineScope,
	private val musicBrainzArtworkRepository: MusicBrainzArtworkRepository,
	private val upNextCount: () -> Int,
	private val onCurrentSongArtworkPrefetched: (DomainSong) -> Unit
) {
	private var lastCurrentArtworkPrefetchSongId: String? = null
	private var lastUpcomingPrefetchSignature: String? = null

	fun prefetchCurrentSongArtwork(
		currentSong: DomainSong?,
		isPlaying: Boolean
	) {
		if (!isPlaying || currentSong == null) return
		if (lastCurrentArtworkPrefetchSongId == currentSong.id) return
		lastCurrentArtworkPrefetchSongId = currentSong.id

		scope.launch(Dispatchers.IO) {
			val artwork = musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(currentSong)
				.getOrNull()
			if (artwork?.imageUrl.isNullOrBlank()) return@launch

			withContext(Dispatchers.Main.immediate) {
				onCurrentSongArtworkPrefetched(currentSong)
			}
		}
	}

	fun prefetchUpcomingPlaybackAssets(
		state: PlayerUiState,
		isPlaying: Boolean
	) {
		if (!isPlaying) return
		val indexes = playbackArtworkPrefetchIndexes(
			upcomingIndexes = state.upcomingIndexes,
			upNextCount = upNextCount()
		)
		val songs = indexes.mapNotNull { index -> state.queue.getOrNull(index) }
			.distinctBy { it.id }
		if (songs.isEmpty()) return

		val signature = songs.joinToString("|") { song -> song.id }
		if (signature == lastUpcomingPrefetchSignature) return
		lastUpcomingPrefetchSignature = signature

		scope.launch(Dispatchers.IO) {
			songs.forEach { song ->
				runCatching {
					musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(song)
				}.onFailure { error ->
					Logger.w("MediaPlayer", "Failed to prefetch artwork for upcoming song ${song.id}", error)
				}
			}
		}
	}
}
