package paige.navic.domain.interactors

import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.PlayerUiState

interface PlaybackQueueStateReducer {
	fun append(state: PlayerUiState, songs: List<DomainSong>): PlayerUiState
	fun removeAt(state: PlayerUiState, index: Int): PlayerUiState
	fun move(state: PlayerUiState, fromIndex: Int, toIndex: Int): PlayerUiState
	fun insertNext(state: PlayerUiState, songs: List<DomainSong>): PlayerUiState
}

object DefaultPlaybackQueueStateReducer : PlaybackQueueStateReducer {
	override fun append(state: PlayerUiState, songs: List<DomainSong>): PlayerUiState {
		val newQueue = state.queue + songs
		return state.copy(
			queue = newQueue,
			currentIndex = if (state.currentIndex == -1 && newQueue.isNotEmpty()) 0 else state.currentIndex,
			currentSong = if (state.currentIndex == -1) newQueue.firstOrNull() else state.currentSong
		)
	}

	override fun removeAt(state: PlayerUiState, index: Int): PlayerUiState {
		val newQueue = state.queue.toMutableList().apply { removeAt(index) }
		val newIndex = when {
			index < state.currentIndex -> state.currentIndex - 1
			index == state.currentIndex -> if (newQueue.isEmpty()) -1 else state.currentIndex.coerceAtMost(newQueue.lastIndex)
			else -> state.currentIndex
		}
		return state.copy(
			queue = newQueue,
			currentIndex = newIndex,
			currentSong = newQueue.getOrNull(newIndex)
		)
	}

	override fun move(state: PlayerUiState, fromIndex: Int, toIndex: Int): PlayerUiState {
		val newQueue = state.queue.toMutableList().apply {
			val item = removeAt(fromIndex)
			add(toIndex, item)
		}
		val newIndex = when (state.currentIndex) {
			fromIndex -> toIndex
			in (fromIndex + 1)..toIndex -> state.currentIndex - 1
			in toIndex until fromIndex -> state.currentIndex + 1
			else -> state.currentIndex
		}
		return state.copy(
			queue = newQueue,
			currentIndex = newIndex,
			currentSong = newQueue.getOrNull(newIndex)
		)
	}

	override fun insertNext(state: PlayerUiState, songs: List<DomainSong>): PlayerUiState {
		if (state.queue.isEmpty()) return append(state, songs)
		val insertionIndex = (state.currentIndex + 1).coerceIn(0, state.queue.size)
		val newQueue = state.queue.toMutableList().apply { addAll(insertionIndex, songs) }
		return state.copy(queue = newQueue)
	}
}
