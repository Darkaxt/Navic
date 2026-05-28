package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

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

fun retryableFailedDownloadSongIds(
	downloads: List<DownloadEntity>,
	localSongIds: Set<String>
): List<String> =
	downloads
		.filter { download ->
			download.status == DownloadStatus.FAILED &&
				download.songId in localSongIds
		}
		.map { it.songId }
