package paige.navic.ui.core

import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection

internal data class DurablePlayerStateKey(
	val playbackOwnerId: String?,
	val queue: List<DomainSong>,
	val currentSong: DomainSong?,
	val currentCollection: DomainSongCollection?,
	val currentIndex: Int,
	val upcomingIndexes: List<Int>,
	val shuffleOrder: List<Int>?,
	val isPaused: Boolean,
	val isShuffleEnabled: Boolean,
	val repeatMode: Int,
	val playbackSpeed: Float,
	val playbackPitch: Float
)

internal fun PlayerUiState.durablePlayerStateKey(): DurablePlayerStateKey = DurablePlayerStateKey(
	playbackOwnerId = playbackOwnerId,
	queue = queue,
	currentSong = currentSong,
	currentCollection = currentCollection,
	currentIndex = currentIndex,
	upcomingIndexes = upcomingIndexes,
	shuffleOrder = shuffleOrder,
	isPaused = isPaused,
	isShuffleEnabled = isShuffleEnabled,
	repeatMode = repeatMode,
	playbackSpeed = playbackSpeed,
	playbackPitch = playbackPitch
)

internal fun PlayerUiState.forPlaybackOwner(ownerId: String?, legacyOwnerId: String?): PlayerUiState? {
	if (ownerId == null) return null
	if (playbackOwnerId != ownerId && !(playbackOwnerId == null && ownerId == legacyOwnerId)) return null
	return copy(playbackOwnerId = ownerId)
}
