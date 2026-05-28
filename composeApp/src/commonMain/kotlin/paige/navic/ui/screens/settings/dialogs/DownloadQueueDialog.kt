package paige.navic.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel_pending_downloads
import navic.composeapp.generated.resources.action_cancel_download
import navic.composeapp.generated.resources.action_delete_download
import navic.composeapp.generated.resources.action_ok
import navic.composeapp.generated.resources.action_retry_failed_downloads
import navic.composeapp.generated.resources.info_download_queue_empty
import navic.composeapp.generated.resources.info_download_status_downloading
import navic.composeapp.generated.resources.info_download_status_failed
import navic.composeapp.generated.resources.info_download_status_queued
import navic.composeapp.generated.resources.option_download_queue
import org.jetbrains.compose.resources.stringResource
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.screens.settings.viewmodels.DownloadQueueItem

@Composable
fun DownloadQueueDialog(
	showDialog: Boolean,
	items: List<DownloadQueueItem>,
	onDismissRequest: () -> Unit,
	onCancelDownload: (String) -> Unit,
	onCancelPendingDownloads: () -> Unit,
	onRetryFailedDownloads: () -> Unit
) {
	if (!showDialog) return

	FormDialog(
		width = 360.dp,
		onDismissRequest = onDismissRequest,
		title = { Text(stringResource(Res.string.option_download_queue)) },
		buttons = {
			if (items.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }) {
				FormRow(
					onClick = {
						onCancelPendingDownloads()
						onDismissRequest()
					}
				) {
					Text(
						text = stringResource(Res.string.action_cancel_pending_downloads),
						color = MaterialTheme.colorScheme.error
					)
				}
			}
			if (items.any { it.canRetry }) {
				FormRow(onClick = onRetryFailedDownloads) {
					Text(stringResource(Res.string.action_retry_failed_downloads))
				}
			}
			FormRow(onClick = onDismissRequest) {
				Text(stringResource(Res.string.action_ok))
			}
		}
	) {
		if (items.isEmpty()) {
			Text(
				text = stringResource(Res.string.info_download_queue_empty),
				style = MaterialTheme.typography.bodyMedium
			)
		} else {
			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				items.forEach { item ->
					DownloadQueueRow(
						item = item,
						onCancelDownload = onCancelDownload
					)
				}
			}
		}
	}
}

@Composable
private fun DownloadQueueRow(
	item: DownloadQueueItem,
	onCancelDownload: (String) -> Unit
) {
	FormRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
		Column(Modifier.weight(1f)) {
			Text(
				text = item.title,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			downloadQueueSubtitle(item)?.let { subtitle ->
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			Text(
				text = item.statusLabel(),
				style = MaterialTheme.typography.labelMedium,
				color = if (item.status == DownloadStatus.FAILED) {
					MaterialTheme.colorScheme.error
				} else {
					MaterialTheme.colorScheme.primary
				}
			)
			if (item.status == DownloadStatus.DOWNLOADING) {
				LinearProgressIndicator(
					progress = { item.progress },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 6.dp)
				)
			}
		}
		if (item.canCancel) {
			TextButton(
				onClick = { onCancelDownload(item.songId) },
				contentPadding = PaddingValues(horizontal = 8.dp)
			) {
				Text(
					text = stringResource(
						if (item.status == DownloadStatus.FAILED) {
							Res.string.action_delete_download
						} else {
							Res.string.action_cancel_download
						}
					)
				)
			}
		}
	}
}

private fun downloadQueueSubtitle(item: DownloadQueueItem): String? =
	listOfNotNull(item.artistName, item.albumTitle)
		.filter { it.isNotBlank() }
		.joinToString(" - ")
		.takeIf { it.isNotBlank() }

@Composable
private fun DownloadQueueItem.statusLabel(): String =
	when (status) {
		DownloadStatus.DOWNLOADING -> stringResource(Res.string.info_download_status_downloading)
		DownloadStatus.QUEUED -> stringResource(Res.string.info_download_status_queued)
		DownloadStatus.FAILED -> stringResource(Res.string.info_download_status_failed)
		DownloadStatus.DOWNLOADED,
		DownloadStatus.NOT_DOWNLOADED -> stringResource(Res.string.info_download_queue_empty)
	}
