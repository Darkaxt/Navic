package paige.navic.domain.models

fun shouldAutoDownloadStarredSong(
	autoDownloadStarredSongs: Boolean,
	isStarring: Boolean,
	isOnline: Boolean,
	isDownloaded: Boolean
): Boolean = autoDownloadStarredSongs && isStarring && isOnline && !isDownloaded
