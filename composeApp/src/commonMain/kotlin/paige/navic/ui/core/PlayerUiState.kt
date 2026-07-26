package paige.navic.ui.core

import kotlinx.serialization.Serializable
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection

@Serializable
data class PlayerUiState(
	val queue: List<DomainSong> = emptyList(),
	val currentSong: DomainSong? = null,
	val currentCollection: DomainSongCollection? = null,
	val currentIndex: Int = -1,
	val upcomingIndexes: List<Int> = emptyList(),
	val isPaused: Boolean = false,
	val isShuffleEnabled: Boolean = false,
	val repeatMode: Int = 0,
	val progress: Float = 0f,
	val isLoading: Boolean = false,
	val playbackDownloadProgress: Float? = null,
	val playbackSpeed: Float = 1.0f,
	val playbackPitch: Float = 1.0f,
	val playbackBitrate: Int? = null,
	val playbackSampleRate: Int? = null,
	val playbackMimeType: String? = null
)

fun PlayerUiState.withQueueSongReplacement(index: Int, replacement: DomainSong): PlayerUiState {
	if (index !in queue.indices) return this
	val repairedQueue = queue.toMutableList().apply { this[index] = replacement }
	return copy(
		queue = repairedQueue,
		currentSong = if (currentIndex == index) replacement else currentSong
	)
}
