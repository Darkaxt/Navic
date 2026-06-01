package paige.navic.ui.screens.activity

import androidx.compose.runtime.Immutable
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LidaClipDownloadEntity
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.lidaClipDownloadQueueDownloads
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.LidaClipsServiceStatus

@Immutable
enum class ActivitySection {
	NavicDownloads,
	Aurral,
	LidaClips
}

@Immutable
data class ActivitySummary(
	val section: ActivitySection,
	val value: String,
	val detail: String,
	val active: Boolean,
	val failed: Boolean
)

@Immutable
data class ActivityDownloadItem(
	val songId: String,
	val title: String,
	val artistName: String?,
	val albumTitle: String?,
	val status: DownloadStatus,
	val progress: Float
)

@Immutable
data class NavicDownloadQueueControls(
	val failedCount: Int,
	val canRetryFailedDownloads: Boolean,
	val canDiscardFailedDownloads: Boolean,
	val canClearDownloadQueue: Boolean
)

@Immutable
data class AurralAcquisitionQueueItemControls(
	val canCancel: Boolean,
	val canRetry: Boolean
)

fun navicDownloadActivitySummary(downloads: List<ActivityDownloadItem>): ActivitySummary {
	val active = downloads.count { item ->
		item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED
	}
	val failed = downloads.count { item -> item.status == DownloadStatus.FAILED }
	return if (downloads.isEmpty()) {
		ActivitySummary(
			section = ActivitySection.NavicDownloads,
			value = "No active downloads",
			detail = "Queue is clear",
			active = false,
			failed = false
		)
	} else {
		ActivitySummary(
			section = ActivitySection.NavicDownloads,
			value = pluralSummary(downloads.size, "item"),
			detail = activityCountDetail(
				listOf(
					active to "active",
					failed to "failed"
				),
				emptyDetail = "Queue is clear"
			) ?: "Queue is clear",
			active = active > 0,
			failed = failed > 0
		)
	}
}

fun navicDownloadQueueControls(downloads: List<ActivityDownloadItem>): NavicDownloadQueueControls {
	val failedCount = downloads.count { item -> item.status == DownloadStatus.FAILED }
	return NavicDownloadQueueControls(
		failedCount = failedCount,
		canRetryFailedDownloads = failedCount > 0,
		canDiscardFailedDownloads = failedCount > 0,
		canClearDownloadQueue = downloads.isNotEmpty()
	)
}

fun aurralAcquisitionQueueItemControls(item: AurralAcquisitionQueueItem): AurralAcquisitionQueueItemControls {
	val progress = aurralAcquisitionProgress(item.status)
	return AurralAcquisitionQueueItemControls(
		canCancel = !item.albumId.isNullOrBlank() || !item.artistMbid.isNullOrBlank(),
		canRetry = progress.failed &&
			!item.albumMbid.isNullOrBlank() &&
			item.albumName.isNotBlank() &&
			!item.artistMbid.isNullOrBlank() &&
			item.artistName.isNotBlank()
	)
}

fun aurralActivitySummary(status: AurralServiceStatus?): ActivitySummary {
	if (status == null) {
		return ActivitySummary(
			section = ActivitySection.Aurral,
			value = "Not checked",
			detail = "Waiting for service status",
			active = false,
			failed = false
		)
	}

	val activeRequests = status.acquisitionQueue.count { item ->
		aurralAcquisitionProgress(item.status).active
	}
	val failedRequests = status.acquisitionQueue.count { item ->
		aurralAcquisitionProgress(item.status).failed
	}
	val flowTrackCount = status.flowTracksPending +
		status.flowTracksDownloading +
		status.flowTracksDone +
		status.flowTracksFailed
	val requestDetail = activityCountDetail(
		listOf(
			activeRequests to "active",
			failedRequests to "failed"
		),
		emptyDetail = "no active requests"
	)
	val flowDetail = activityCountDetail(
		listOf(
			status.flowTracksPending to "pending",
			status.flowTracksDownloading to "downloading",
			status.flowTracksDone to "ready",
			status.flowTracksFailed to "failed"
		),
		emptyDetail = null
	)?.let { detail -> "${pluralSummary(flowTrackCount, "flow track")}: $detail" }

	return ActivitySummary(
		section = ActivitySection.Aurral,
		value = pluralSummary(status.acquisitionQueue.size, "request"),
		detail = listOfNotNull(requestDetail, flowDetail).joinToString("; "),
		active = activeRequests > 0 ||
			status.flowTracksPending > 0 ||
			status.flowTracksDownloading > 0,
		failed = failedRequests > 0 || status.flowTracksFailed > 0
	)
}

fun lidaClipsActivitySummary(
	status: LidaClipsServiceStatus?,
	downloads: List<LidaClipDownloadEntity>
): ActivitySummary {
	val failedHealthChecks = status?.health?.checks.orEmpty().count { check -> !check.ok && !check.skipped }
	val queue = lidaClipDownloadQueueDownloads(downloads)
	if (queue.isNotEmpty()) {
		val queued = queue.count { item -> item.status == DownloadStatus.QUEUED }
		val downloading = queue.count { item -> item.status == DownloadStatus.DOWNLOADING }
		val failed = queue.count { item -> item.status == DownloadStatus.FAILED }
		val queueDetail = activityCountDetail(
			listOf(
				queued to "queued",
				downloading to "downloading",
				failed to "failed"
			),
			emptyDetail = "Queue is active"
		) ?: "Queue is active"
		val healthDetail = activityCountDetail(
			listOf(failedHealthChecks to "health check failed"),
			emptyDetail = null
		)

		return ActivitySummary(
			section = ActivitySection.LidaClips,
			value = pluralSummary(queue.size, "clip download"),
			detail = listOfNotNull(queueDetail, healthDetail).joinToString("; "),
			active = status?.syncRunning == true || queued > 0 || downloading > 0,
			failed = failed > 0 || failedHealthChecks > 0
		)
	}

	if (status == null) {
		return ActivitySummary(
			section = ActivitySection.LidaClips,
			value = "Not checked",
			detail = "Waiting for service status",
			active = false,
			failed = false
		)
	}

	val detail = listOfNotNull(
		"Clip download queue is empty",
		activityCountDetail(
			listOf(failedHealthChecks to "health check failed"),
			emptyDetail = null
		),
		activityCountDetail(
			listOf(
				status.recentClips.size to "recent clip",
				status.recentFailures.size to "recent issue"
			),
			emptyDetail = null
		)
	).joinToString("; ")

	return ActivitySummary(
		section = ActivitySection.LidaClips,
		value = when {
			status.syncPaused -> "Sync paused"
			status.syncRunning -> "Sync running"
			else -> "No active clip downloads"
		},
		detail = detail,
		active = status.syncRunning,
		failed = failedHealthChecks > 0
	)
}

private fun activityCountDetail(
	parts: List<Pair<Int, String>>,
	emptyDetail: String?
): String? =
	parts
		.filter { (count, _) -> count > 0 }
		.joinToString(", ") { (count, label) -> pluralSummary(count, label) }
		.takeIf { it.isNotEmpty() }
		?: emptyDetail

private fun pluralSummary(
	count: Int,
	label: String
	): String =
	when (label) {
		"health check failed" -> "$count health check${if (count == 1) "" else "s"} failed"
		"active",
		"queued",
		"pending",
		"downloading",
		"ready",
		"failed" -> "$count $label"
		else -> "$count $label${if (count == 1) "" else "s"}"
	}
