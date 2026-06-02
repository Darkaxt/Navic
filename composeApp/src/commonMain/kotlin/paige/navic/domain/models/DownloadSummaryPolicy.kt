package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

const val DefaultMaxConcurrentDownloads = 3
const val MaxSupportedConcurrentDownloads = 10

fun downloadConcurrencyLimit(configuredLimit: Int): Int =
	configuredLimit.coerceIn(1, MaxSupportedConcurrentDownloads)

fun downloadSchedulerWorkerCount(): Int = MaxSupportedConcurrentDownloads

fun canStartQueuedDownload(
	activeDownloadSongIds: Set<String>,
	queuedSongId: String,
	configuredLimit: Int
): Boolean =
	queuedSongId !in activeDownloadSongIds &&
		activeDownloadSongIds.size < downloadConcurrencyLimit(configuredLimit)

fun pendingDownloadCount(downloads: List<DownloadEntity>): Int =
	downloads.count { download ->
		download.status == DownloadStatus.QUEUED ||
			download.status == DownloadStatus.DOWNLOADING
	}

fun downloadQueueDownloads(downloads: List<DownloadEntity>): List<DownloadEntity> =
	downloads
		.filter { download ->
			download.status == DownloadStatus.DOWNLOADING ||
				download.status == DownloadStatus.QUEUED ||
				download.status == DownloadStatus.FAILED
		}
		.sortedBy { download ->
			when (download.status) {
				DownloadStatus.DOWNLOADING -> 0
				DownloadStatus.QUEUED -> 1
				DownloadStatus.FAILED -> 2
				DownloadStatus.DOWNLOADED,
				DownloadStatus.NOT_DOWNLOADED -> 3
			}
		}

fun cancelPendingDownloadSongIds(downloads: List<DownloadEntity>): List<String> =
	downloadQueueDownloads(downloads)
		.filter { download ->
			download.status == DownloadStatus.DOWNLOADING ||
				download.status == DownloadStatus.QUEUED
		}
		.map { it.songId }

fun clearDownloadQueueSongIds(downloads: List<DownloadEntity>): List<String> =
	downloadQueueDownloads(downloads).map { it.songId }

data class FailedDownloadRetryPlan(
	val songIdsToRetry: List<String>,
	val staleSongIdsToDelete: List<String>
)

fun failedDownloadRetryPlan(
	downloads: List<DownloadEntity>,
	localSongIds: Set<String>
): FailedDownloadRetryPlan {
	val failedSongIds = downloads
		.filter { it.status == DownloadStatus.FAILED }
		.map { it.songId }
	return FailedDownloadRetryPlan(
		songIdsToRetry = failedSongIds.filter { it in localSongIds },
		staleSongIdsToDelete = failedSongIds.filter { it !in localSongIds }
	)
}

fun retryableFailedDownloadSongIds(
	downloads: List<DownloadEntity>,
	localSongIds: Set<String>
): List<String> =
	failedDownloadRetryPlan(downloads, localSongIds).songIdsToRetry
