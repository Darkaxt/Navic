package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadStatus

data class PendingPlaybackRecovery(
	val songId: String,
	val queueIndex: Int,
	val positionMs: Long,
	val shouldResume: Boolean,
	val reason: String,
	val downloadLifecycle: PlaybackRecoveryDownloadLifecycle = PlaybackRecoveryDownloadLifecycle.Requesting,
	val downloadIntentGeneration: Long? = null
) {
	fun withPlaybackIntent(playWhenReady: Boolean): PendingPlaybackRecovery =
		copy(shouldResume = playWhenReady)

	fun withDownloadLifecycle(lifecycle: PlaybackRecoveryDownloadLifecycle): PendingPlaybackRecovery =
		copy(downloadLifecycle = lifecycle)

	fun withActiveDownloadRequest(intentGeneration: Long): PendingPlaybackRecovery =
		copy(
			downloadLifecycle = PlaybackRecoveryDownloadLifecycle.Active,
			downloadIntentGeneration = intentGeneration
		)
}

enum class PlaybackRecoveryDownloadLifecycle {
	Requesting,
	Active,
	Rejected
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
	availableSongIds: Set<String>,
	upcomingIndexes: List<Int> = (currentIndex + 1 until queueSongIds.size).toList()
): Int? =
	upcomingIndexes
		.asSequence()
		.filter { index -> index != currentIndex && index in queueSongIds.indices }
		.firstOrNull { index -> queueSongIds[index] in availableSongIds }

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
	val isTerminalFailure =
		pending.downloadLifecycle == PlaybackRecoveryDownloadLifecycle.Rejected ||
			downloadStatus == DownloadStatus.FAILED ||
			(downloadStatus == DownloadStatus.DOWNLOADED && !hasUsableLocalFile) ||
			(
				pending.downloadLifecycle == PlaybackRecoveryDownloadLifecycle.Active &&
					downloadStatus in setOf(null, DownloadStatus.NOT_DOWNLOADED)
			)
	if (!isTerminalFailure) {
		return PlaybackRecoveryResolution.Wait
	}

	return playbackFailureTargetIndex(
		skipMediaOnError = skipMediaOnError,
		nextPlayableIndex = nextPlayableIndex
	)?.let(PlaybackRecoveryResolution::Advance)
		?: PlaybackRecoveryResolution.HoldFailure
}
