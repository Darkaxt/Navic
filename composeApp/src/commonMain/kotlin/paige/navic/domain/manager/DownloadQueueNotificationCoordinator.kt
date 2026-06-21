package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_download_notification_active
import navic.composeapp.generated.resources.info_download_notification_active_failed
import navic.composeapp.generated.resources.title_lida_clip_downloads
import navic.composeapp.generated.resources.title_music_downloads
import org.jetbrains.compose.resources.getString
import paige.navic.domain.models.DownloadQueueNotificationRow
import paige.navic.domain.models.DownloadQueueNotificationState
import paige.navic.domain.models.downloadQueueNotificationState

class DownloadQueueNotificationCoordinator(
	private val downloadManager: DownloadManager,
	private val lidaClipDownloadManager: LidaClipDownloadManager,
	private val queueNotificationManager: QueueNotificationManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	fun start() {
		scope.launch {
			downloadManager.allDownloads.collectLatest { downloads ->
				publishQueueState(
					id = QueueNotificationIds.MUSIC_DOWNLOADS,
					title = getString(Res.string.title_music_downloads),
					rows = downloads.map { download ->
						DownloadQueueNotificationRow(
							status = download.status,
							progress = download.progress
						)
					}
				)
			}
		}
		scope.launch {
			lidaClipDownloadManager.allDownloads.collectLatest { downloads ->
				publishQueueState(
					id = QueueNotificationIds.LIDA_CLIP_DOWNLOADS,
					title = getString(Res.string.title_lida_clip_downloads),
					rows = downloads.map { download ->
						DownloadQueueNotificationRow(
							status = download.status,
							progress = download.progress
						)
					}
				)
			}
		}
	}

	private suspend fun publishQueueState(
		id: Int,
		title: String,
		rows: List<DownloadQueueNotificationRow>
	) {
		val state = downloadQueueNotificationState(rows)
		if (state == null) {
			queueNotificationManager.cancelNotification(id)
			return
		}

		queueNotificationManager.showProgressNotification(
			id = id,
			title = title,
			message = state.message(),
			progress = state.progress,
			indeterminate = state.indeterminate
		)
	}

	private suspend fun DownloadQueueNotificationState.message(): String =
		if (failedCount > 0) {
			getString(
				Res.string.info_download_notification_active_failed,
				activeCount,
				failedCount
			)
		} else {
			getString(Res.string.info_download_notification_active, activeCount)
		}
}
