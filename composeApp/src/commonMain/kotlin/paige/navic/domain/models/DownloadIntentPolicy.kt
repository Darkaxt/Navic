package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

fun cancelledDownloadIntent(current: DownloadEntity): DownloadEntity = current.copy(
	status = DownloadStatus.NOT_DOWNLOADED,
	progress = 0f,
	filePath = null,
	intentGeneration = current.intentGeneration + 1L,
	cancelled = true
)

fun canRetryDownloadIntent(current: DownloadEntity, observedGeneration: Long): Boolean =
	current.status == DownloadStatus.FAILED &&
		!current.cancelled &&
		current.intentGeneration == observedGeneration

fun canApplyDownloadResult(current: DownloadEntity, workerGeneration: Long): Boolean =
	current.status == DownloadStatus.DOWNLOADING &&
		!current.cancelled &&
		current.intentGeneration == workerGeneration
