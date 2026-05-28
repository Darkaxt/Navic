package paige.navic.domain.models

fun shouldShowPlaylistIndicator(
	userEnabled: Boolean,
	isInPlaylist: Boolean,
	isPlaylistScreen: Boolean
): Boolean = userEnabled && isInPlaylist && !isPlaylistScreen
