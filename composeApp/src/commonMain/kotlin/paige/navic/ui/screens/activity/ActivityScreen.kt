package paige.navic.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_discard_failed_downloads
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_retry
import navic.composeapp.generated.resources.action_retry_failed_downloads
import navic.composeapp.generated.resources.info_download_status_downloading
import navic.composeapp.generated.resources.info_download_status_failed
import navic.composeapp.generated.resources.info_download_status_queued
import navic.composeapp.generated.resources.info_progress
import navic.composeapp.generated.resources.title_activity
import navic.composeapp.generated.resources.title_aurral
import navic.composeapp.generated.resources.title_lida_clips
import navic.composeapp.generated.resources.option_download_queue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.LidaClipsDownloadQueueItem
import paige.navic.domain.repositories.LidaClipsHealthCheck
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Refresh
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.settings.lidaClipsHealthFailureDisplay

private const val ACTIVITY_ITEM_LIMIT = 6

@Composable
fun ActivityScreen() {
	val viewModel = koinViewModel<ActivityViewModel>()
	val downloadItems by viewModel.downloadItems.collectAsStateWithLifecycle()
	val aurralStatus by viewModel.aurralStatus.collectAsStateWithLifecycle()
	val lidaClipsStatus by viewModel.lidaClipsStatus.collectAsStateWithLifecycle()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	LaunchedEffect(Unit) {
		viewModel.refresh()
	}

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(stringResource(Res.string.title_activity)) },
				scrollBehavior = scrollBehavior,
				actions = {
					TopBarButton(
						onClick = viewModel::refresh,
						enabled = aurralStatus !is UiState.Loading &&
							lidaClipsStatus !is UiState.Loading
					) {
						Icon(Icons.Outlined.Refresh, stringResource(Res.string.action_refresh))
					}
				}
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		Column(
			Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.verticalScroll(rememberScrollState())
				.padding(
					top = 16.dp,
					end = 16.dp,
					start = 16.dp,
					bottom = innerPadding.calculateBottomPadding() + 32.dp
				)
		) {
			ActivitySection(
				title = stringResource(Res.string.option_download_queue),
				summary = navicDownloadActivitySummary(downloadItems)
			) {
				NavicDownloadControlsRow(
					controls = navicDownloadQueueControls(downloadItems),
					onRetryFailedDownloads = viewModel::retryFailedDownloads,
					onDiscardFailedDownloads = viewModel::discardFailedDownloads
				)
				downloadItems.take(ACTIVITY_ITEM_LIMIT).forEach { item ->
					ActivityDownloadRow(item)
				}
			}

			ActivitySection(
				title = stringResource(Res.string.title_aurral),
				summary = aurralActivitySummary(aurralStatus.data),
				loading = aurralStatus is UiState.Loading,
				error = (aurralStatus as? UiState.Error)?.error
			) {
				aurralStatus.data?.acquisitionQueue
					.orEmpty()
					.take(ACTIVITY_ITEM_LIMIT)
					.forEach { item ->
						AurralQueueRow(
							item = item,
							onCancel = viewModel::cancelAurralAcquisition,
							onRetry = viewModel::retryAurralAcquisition
						)
					}
			}

			ActivitySection(
				title = stringResource(Res.string.title_lida_clips),
				summary = lidaClipsActivitySummary(lidaClipsStatus.data),
				loading = lidaClipsStatus is UiState.Loading,
				error = (lidaClipsStatus as? UiState.Error)?.error
			) {
				lidaClipsStatus.data?.downloadQueue
					.orEmpty()
					.take(ACTIVITY_ITEM_LIMIT)
					.forEach { item -> LidaClipsDownloadQueueRow(item) }
				lidaClipsStatus.data?.health?.checks
					.orEmpty()
					.filter { check -> !check.ok && !check.skipped }
					.take(ACTIVITY_ITEM_LIMIT)
					.forEach { check -> LidaClipsHealthRow(check) }
			}
		}
	}
}

@Composable
private fun NavicDownloadControlsRow(
	controls: NavicDownloadQueueControls,
	onRetryFailedDownloads: () -> Unit,
	onDiscardFailedDownloads: () -> Unit
) {
	if (!controls.canRetryFailedDownloads && !controls.canDiscardFailedDownloads) return
	FormRow(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
			verticalAlignment = Alignment.CenterVertically
		) {
			if (controls.canRetryFailedDownloads) {
				TextButton(onClick = onRetryFailedDownloads) {
					Text(stringResource(Res.string.action_retry_failed_downloads))
				}
			}
			if (controls.canDiscardFailedDownloads) {
				TextButton(onClick = onDiscardFailedDownloads) {
					Text(
						text = stringResource(Res.string.action_discard_failed_downloads),
						color = MaterialTheme.colorScheme.error
					)
				}
			}
		}
	}
}

@Composable
private fun ActivitySection(
	title: String,
	summary: ActivitySummary,
	loading: Boolean = false,
	error: Exception? = null,
	content: @Composable () -> Unit
) {
	FormTitle(title)
	Form(Modifier.fillMaxWidth()) {
		ActivitySummaryRow(summary)
		content()
		if (loading) {
			FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
				LinearProgressIndicator(Modifier.fillMaxWidth())
			}
		}
		error?.let { failure ->
			FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
				Text(
					text = failure.message ?: failure::class.simpleName ?: "Unknown error",
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.bodyMedium
				)
			}
		}
	}
}

@Composable
private fun ActivitySummaryRow(summary: ActivitySummary) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.weight(1f)) {
			Text(summary.value, fontWeight = FontWeight.Medium)
			Text(
				text = summary.detail,
				style = MaterialTheme.typography.bodyMedium,
				color = when {
					summary.failed -> MaterialTheme.colorScheme.error
					summary.active -> MaterialTheme.colorScheme.primary
					else -> MaterialTheme.colorScheme.onSurfaceVariant
				},
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@Composable
private fun ActivityDownloadRow(item: ActivityDownloadItem) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.fillMaxWidth()) {
			Row(Modifier.fillMaxWidth()) {
				Column(Modifier.weight(1f)) {
					Text(
						text = item.title,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					downloadSubtitle(item)?.let { subtitle ->
						Text(
							text = subtitle,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
				Text(
					text = downloadStatusText(item.status),
					modifier = Modifier.padding(start = 16.dp),
					style = MaterialTheme.typography.bodyMedium,
					color = if (item.status == DownloadStatus.FAILED) {
						MaterialTheme.colorScheme.error
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					},
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			if (item.status == DownloadStatus.DOWNLOADING) {
				Text(
					text = stringResource(Res.string.info_progress),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.padding(top = 8.dp)
				)
				LinearProgressIndicator(
					progress = { item.progress },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 4.dp)
						.height(3.dp)
				)
			}
		}
	}
}

@Composable
private fun AurralQueueRow(
	item: AurralAcquisitionQueueItem,
	onCancel: (AurralAcquisitionQueueItem) -> Unit,
	onRetry: (AurralAcquisitionQueueItem) -> Unit
) {
	val progress = aurralAcquisitionProgress(item.status)
	val controls = aurralAcquisitionQueueItemControls(item)
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.fillMaxWidth()) {
			Row(Modifier.fillMaxWidth()) {
				Column(Modifier.weight(1f)) {
					Text(
						text = item.albumName,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = item.artistName,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				Text(
					text = item.status,
					modifier = Modifier.padding(start = 16.dp),
					style = MaterialTheme.typography.bodyMedium,
					color = if (progress.failed) {
						MaterialTheme.colorScheme.error
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					},
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			if (progress.active) {
				LinearProgressIndicator(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.height(3.dp)
				)
			}
			if (controls.canRetry || controls.canCancel) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
					verticalAlignment = Alignment.CenterVertically
				) {
					if (controls.canRetry) {
						TextButton(onClick = { onRetry(item) }) {
							Text(stringResource(Res.string.action_retry))
						}
					}
					if (controls.canCancel) {
						TextButton(onClick = { onCancel(item) }) {
							Text(
								text = stringResource(Res.string.action_cancel),
								color = MaterialTheme.colorScheme.error
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun LidaClipsDownloadQueueRow(item: LidaClipsDownloadQueueItem) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Row(Modifier.fillMaxWidth()) {
			Column(Modifier.weight(1f)) {
				Text(
					text = lidaClipsDownloadQueueTitle(item),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				lidaClipsDownloadQueueSubtitle(item)?.let { subtitle ->
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
			Text(
				text = lidaClipsDownloadQueueStatusText(item),
				modifier = Modifier.padding(start = 16.dp),
				style = MaterialTheme.typography.bodyMedium,
				color = if (item.isFailedLidaClipsDownload()) {
					MaterialTheme.colorScheme.error
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@Composable
private fun LidaClipsHealthRow(check: LidaClipsHealthCheck) {
	val display = lidaClipsHealthFailureDisplay(check)
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.fillMaxWidth()) {
			Text(
				text = display.name,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			display.detail?.let { detail ->
				Text(
					text = detail,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.error,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
	}
}

@Composable
private fun downloadStatusText(status: DownloadStatus): String =
	when (status) {
		DownloadStatus.DOWNLOADING -> stringResource(Res.string.info_download_status_downloading)
		DownloadStatus.QUEUED -> stringResource(Res.string.info_download_status_queued)
		DownloadStatus.FAILED -> stringResource(Res.string.info_download_status_failed)
		DownloadStatus.DOWNLOADED,
		DownloadStatus.NOT_DOWNLOADED -> status.name.lowercase()
	}

private fun downloadSubtitle(item: ActivityDownloadItem): String? =
	listOfNotNull(item.artistName, item.albumTitle)
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.joinToString(" - ")
		.takeIf { it.isNotEmpty() }

private fun lidaClipsDownloadQueueTitle(item: LidaClipsDownloadQueueItem): String =
	item.track?.trim()?.takeIf { it.isNotEmpty() }
		?: item.lidarrTrackId?.let { id -> "Track $id" }
		?: "Clip download"

private fun lidaClipsDownloadQueueSubtitle(item: LidaClipsDownloadQueueItem): String? =
	listOfNotNull(item.artist, item.album)
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.joinToString(" - ")
		.takeIf { it.isNotEmpty() }

@Composable
private fun lidaClipsDownloadQueueStatusText(item: LidaClipsDownloadQueueItem): String =
	when (item.status) {
		"queued" -> stringResource(Res.string.info_download_status_queued)
		"downloading" -> stringResource(Res.string.info_download_status_downloading)
		"failed" -> stringResource(Res.string.info_download_status_failed)
		else -> item.status.replace('_', ' ').replaceFirstChar { char ->
			if (char.isLowerCase()) char.titlecase() else char.toString()
		}
	}
