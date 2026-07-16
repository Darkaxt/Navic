package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadStatus

data class PendingPlaybackRecovery(
	val songId: String,
	val queueIndex: Int,
	val positionMs: Long,
	val shouldResume: Boolean,
	val reason: String
) {
	fun withPlaybackIntent(playWhenReady: Boolean): PendingPlaybackRecovery =
		copy(shouldResume = playWhenReady)
}

sealed interface PlaybackRecoveryResolution {
	data object Wait : PlaybackRecoveryResolution
	data object ResumeCurrent : PlaybackRecoveryResolution
	data object CancelStale : PlaybackRecoveryResolution
	data object HoldFailure : PlaybackRecoveryResolution
	data class Advance(val targetIndex: Int) : PlaybackRecoveryResolution
}

fun firstPlayableUpcomingIndex(
	currentIndex: Int,
	queueSongIds: List<String>,
	availableSongIds: Set<String>
): Int? =
	queueSongIds
		.asSequence()
		.drop(currentIndex + 1)
		.withIndex()
		.firstOrNull { (_, songId) -> songId in availableSongIds }
		?.let { (offset, _) -> currentIndex + 1 + offset }

fun playbackFailureTargetIndex(
	skipMediaOnError: Boolean,
	nextPlayableIndex: Int?
): Int? =
	nextPlayableIndex?.takeIf {
		shouldSkipMediaAfterPlaybackError(
			skipMediaOnError = skipMediaOnError,
			hasNextMediaItem = true
		)
	}

fun playbackRecoveryResolution(
	pending: PendingPlaybackRecovery,
	currentSongId: String?,
	currentIndex: Int,
	downloadStatus: DownloadStatus?,
	hasUsableLocalFile: Boolean,
	skipMediaOnError: Boolean,
	nextPlayableIndex: Int?
): PlaybackRecoveryResolution {
	if (pending.songId != currentSongId || pending.queueIndex != currentIndex) {
		return PlaybackRecoveryResolution.CancelStale
	}
	if (downloadStatus == DownloadStatus.DOWNLOADED && hasUsableLocalFile) {
		return PlaybackRecoveryResolution.ResumeCurrent
	}
	if (downloadStatus != DownloadStatus.FAILED) {
		return PlaybackRecoveryResolution.Wait
	}

	return playbackFailureTargetIndex(
		skipMediaOnError = skipMediaOnError,
		nextPlayableIndex = nextPlayableIndex
	)?.let(PlaybackRecoveryResolution::Advance)
		?: PlaybackRecoveryResolution.HoldFailure
}
