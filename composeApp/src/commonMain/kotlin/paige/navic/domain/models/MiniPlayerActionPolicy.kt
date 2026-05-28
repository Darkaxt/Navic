package paige.navic.domain.models

fun shouldShowMiniPlayerQueueAction(
	enabled: Boolean,
	hasCurrentSong: Boolean
): Boolean =
	enabled && hasCurrentSong
