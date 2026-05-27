package paige.navic.domain.models

fun shouldPauseMusicForLidaClip(
	pauseMusicPlayback: Boolean,
	hasCurrentSong: Boolean,
	musicIsPaused: Boolean
): Boolean = pauseMusicPlayback && hasCurrentSong && !musicIsPaused

fun shouldResumeMusicAfterLidaClip(
	pauseMusicPlayback: Boolean,
	pausedSongId: String?,
	currentSongId: String?,
	musicIsPaused: Boolean
): Boolean =
	pauseMusicPlayback &&
		pausedSongId != null &&
		currentSongId == pausedSongId &&
		musicIsPaused
