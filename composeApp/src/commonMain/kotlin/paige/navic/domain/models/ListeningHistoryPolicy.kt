package paige.navic.domain.models

fun shouldSubmitListeningHistory(
	enableScrobbling: Boolean,
	pauseListeningHistory: Boolean,
	songId: String?
): Boolean =
	enableScrobbling &&
		!pauseListeningHistory &&
		hasStableNavidromeSongId(songId)
