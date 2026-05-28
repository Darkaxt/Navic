package paige.navic.domain.models

fun shouldEnableNowPlayingArtworkSwipe(
	swipeToSkip: Boolean,
	artworkSwipeToSkip: Boolean
): Boolean = swipeToSkip && artworkSwipeToSkip
