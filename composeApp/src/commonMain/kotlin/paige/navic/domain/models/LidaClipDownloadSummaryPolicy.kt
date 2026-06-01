package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LidaClipDownloadEntity

@Immutable
data class LidaClipDownloadQueueControls(
	val failedCount: Int,
	val canRetryFailedDownloads: Boolean,
	val canDiscardFailedDownloads: Boolean,
	val canClearDownloadQueue: Boolean
)

fun lidaClipDownloadQueueDownloads(downloads: List<LidaClipDownloadEntity>): List<LidaClipDownloadEntity> =
	downloads
		.filter { download ->
			download.status == DownloadStatus.DOWNLOADING ||
				download.status == DownloadStatus.QUEUED ||
				download.status == DownloadStatus.FAILED ||
				download.status == DownloadStatus.DOWNLOADED
		}
		.sortedWith(compareBy<LidaClipDownloadEntity> { download ->
			when (download.status) {
				DownloadStatus.DOWNLOADING -> 0
				DownloadStatus.QUEUED -> 1
				DownloadStatus.FAILED -> 2
				DownloadStatus.DOWNLOADED -> 3
				DownloadStatus.NOT_DOWNLOADED -> 4
			}
		}.thenByDescending { download -> download.updatedAtMillis })

fun lidaClipDownloadQueueControls(downloads: List<LidaClipDownloadEntity>): LidaClipDownloadQueueControls {
	val queueDownloads = lidaClipDownloadQueueDownloads(downloads)
	val failedCount = queueDownloads.count { download -> download.status == DownloadStatus.FAILED }
	return LidaClipDownloadQueueControls(
		failedCount = failedCount,
		canRetryFailedDownloads = failedCount > 0,
		canDiscardFailedDownloads = failedCount > 0,
		canClearDownloadQueue = queueDownloads.isNotEmpty()
	)
}

fun clearLidaClipDownloadQueueSongIds(downloads: List<LidaClipDownloadEntity>): List<String> =
	lidaClipDownloadQueueDownloads(downloads).map { download -> download.songId }
