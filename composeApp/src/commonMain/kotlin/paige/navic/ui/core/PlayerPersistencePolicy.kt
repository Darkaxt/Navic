package paige.navic.ui.core

import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection

internal data class DurablePlayerStateKey(
	val queue: List<DomainSong>,
	val currentSong: DomainSong?,
	val currentCollection: DomainSongCollection?,
	val currentIndex: Int,
	val upcomingIndexes: List<Int>,
	val isPaused: Boolean,
	val isShuffleEnabled: Boolean,
	val repeatMode: Int,
	val playbackSpeed: Float,
	val playbackPitch: Float
)

internal fun PlayerUiState.durablePlayerStateKey(): DurablePlayerStateKey = DurablePlayerStateKey(
	queue = queue,
	currentSong = currentSong,
	currentCollection = currentCollection,
	currentIndex = currentIndex,
	upcomingIndexes = upcomingIndexes,
	isPaused = isPaused,
	isShuffleEnabled = isShuffleEnabled,
	repeatMode = repeatMode,
	playbackSpeed = playbackSpeed,
	playbackPitch = playbackPitch
)
