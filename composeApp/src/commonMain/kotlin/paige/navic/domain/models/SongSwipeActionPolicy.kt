package paige.navic.domain.models

import paige.navic.domain.models.settings.SongSwipeAction

enum class SongSwipeDirection {
	StartToEnd,
	EndToStart
}

fun songSwipeActionForDirection(
	enabled: Boolean,
	startToEndAction: SongSwipeAction,
	endToStartAction: SongSwipeAction,
	direction: SongSwipeDirection
): SongSwipeAction {
	if (!enabled) return SongSwipeAction.Disabled

	return when (direction) {
		SongSwipeDirection.StartToEnd -> startToEndAction
		SongSwipeDirection.EndToStart -> endToStartAction
	}
}
