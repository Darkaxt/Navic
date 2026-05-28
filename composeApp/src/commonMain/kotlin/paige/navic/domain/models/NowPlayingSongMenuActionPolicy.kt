package paige.navic.domain.models

fun shouldShowNowPlayingStartRadioAction(
	userActionEnabled: Boolean,
	songId: String?
): Boolean =
	userActionEnabled &&
		hasStableNavidromeSongId(songId)

fun shouldShowNowPlayingDiscoverQueueAction(
	userActionEnabled: Boolean,
	hasUpcomingSongs: Boolean
): Boolean =
	userActionEnabled && hasUpcomingSongs

fun shouldShowNowPlayingAddToPlaylistAction(
	userActionEnabled: Boolean,
	songId: String?
): Boolean =
	userActionEnabled && songId != null

fun shouldShowNowPlayingDownloadAction(
	userActionEnabled: Boolean,
	songId: String?
): Boolean =
	userActionEnabled &&
		hasStableNavidromeSongId(songId)

fun shouldShowNowPlayingMoreAction(userActionEnabled: Boolean): Boolean =
	userActionEnabled
