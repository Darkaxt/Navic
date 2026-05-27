package paige.navic.domain.models

import paige.navic.domain.models.settings.QueueSwipeAction

fun queueSwipeActionForDirection(
	enabled: Boolean,
	startToEndAction: QueueSwipeAction,
	endToStartAction: QueueSwipeAction,
	direction: SongSwipeDirection
): QueueSwipeAction {
	if (!enabled) return QueueSwipeAction.Disabled

	return when (direction) {
		SongSwipeDirection.StartToEnd -> startToEndAction
		SongSwipeDirection.EndToStart -> endToStartAction
	}
}

fun queuePlayNextTargetIndex(
	fromIndex: Int,
	currentIndex: Int,
	queueSize: Int
): Int? {
	if (fromIndex !in 0 until queueSize) return null
	if (currentIndex !in 0 until queueSize) return null
	if (fromIndex == currentIndex) return null

	return if (fromIndex < currentIndex) currentIndex else currentIndex + 1
}
