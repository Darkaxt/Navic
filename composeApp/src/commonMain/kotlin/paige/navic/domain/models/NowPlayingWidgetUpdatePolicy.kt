package paige.navic.domain.models

fun shouldSendNowPlayingWidgetUpdate(
	previousSongId: String?,
	currentSongId: String?,
	previousIsPlaying: Boolean,
	currentIsPlaying: Boolean
): Boolean =
	previousSongId != currentSongId || previousIsPlaying != currentIsPlaying
