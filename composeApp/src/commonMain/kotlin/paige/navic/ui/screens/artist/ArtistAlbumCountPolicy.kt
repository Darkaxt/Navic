package paige.navic.ui.screens.artist

fun shouldShowArtistAlbumCount(
	albumCount: Int,
	showUnknownAlbumCount: Boolean = true
): Boolean = showUnknownAlbumCount || albumCount > 0
