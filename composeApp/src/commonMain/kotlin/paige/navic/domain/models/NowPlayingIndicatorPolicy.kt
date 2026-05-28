package paige.navic.domain.models

fun shouldShowNowPlayingIndicator(
	userEnabled: Boolean,
	isCurrentSong: Boolean
): Boolean = userEnabled && isCurrentSong
