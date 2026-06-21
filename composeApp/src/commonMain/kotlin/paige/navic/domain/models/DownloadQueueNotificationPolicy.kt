package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadStatus

data class DownloadQueueNotificationRow(
	val status: DownloadStatus,
	val progress: Float
)

data class DownloadQueueNotificationState(
	val activeCount: Int,
	val failedCount: Int,
	val progress: Float,
	val indeterminate: Boolean
)

fun downloadQueueNotificationState(rows: List<DownloadQueueNotificationRow>): DownloadQueueNotificationState? {
	val activeRows = rows.filter { row ->
		row.status == DownloadStatus.DOWNLOADING ||
			row.status == DownloadStatus.QUEUED
	}
	if (activeRows.isEmpty()) return null

	val failedCount = rows.count { row -> row.status == DownloadStatus.FAILED }
	val hasDownloadingRow = activeRows.any { row -> row.status == DownloadStatus.DOWNLOADING }
	val progress = activeRows
		.sumOf { row ->
			if (row.status == DownloadStatus.DOWNLOADING) {
				row.progress.coerceIn(0f, 1f).toDouble()
			} else {
				0.0
			}
		}
		.toFloat() / activeRows.size.toFloat()

	return DownloadQueueNotificationState(
		activeCount = activeRows.size,
		failedCount = failedCount,
		progress = progress.coerceIn(0f, 1f),
		indeterminate = !hasDownloadingRow
	)
}
