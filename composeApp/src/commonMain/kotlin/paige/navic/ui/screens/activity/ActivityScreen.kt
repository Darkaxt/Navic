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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import navic.composeapp.generated.resources.action_clear_download_queue
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
import paige.navic.data.database.entities.LidaClipDownloadEntity
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.LidaClipDownloadQueueControls
import paige.navic.domain.models.lidaClipDownloadQueueControls
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Close
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.Refresh
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.core.UiState

private const val ACTIVITY_ITEM_LIMIT = 6

@Composable
fun ActivityScreen() {
	val viewModel = koinViewModel<ActivityViewModel>()
	val downloadItems by viewModel.downloadItems.collectAsStateWithLifecycle()
	val lidaClipDownloadItems by viewModel.lidaClipDownloadItems.collectAsStateWithLifecycle()
	val aurralStatus by viewModel.aurralStatus.collectAsStateWithLifecycle()
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
						enabled = aurralStatus !is UiState.Loading
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
					onDiscardFailedDownloads = viewModel::discardFailedDownloads,
					onClearDownloadQueue = viewModel::clearDownloadQueue
				)
				downloadItems.take(ACTIVITY_ITEM_LIMIT).forEach { item ->
					ActivityDownloadRow(
						item = item,
						onCancel = viewModel::cancelDownload,
						onRetry = viewModel::retryDownload
					)
				}
			}

			if (shouldShowAurralActivitySection(aurralStatus.data)) {
				ActivitySection(
					title = stringResource(Res.string.title_aurral),
					summary = aurralActivitySummary(aurralStatus.data),
					loading = aurralStatus is UiState.Loading,
					error = activityQueueSectionError(
						ActivitySection.Aurral,
						(aurralStatus as? UiState.Error)?.error
					)
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
			}

			ActivitySection(
				title = stringResource(Res.string.title_lida_clips),
				summary = lidaClipsActivitySummary(downloads = lidaClipDownloadItems)
			) {
				LidaClipDownloadControlsRow(
					controls = lidaClipDownloadQueueControls(lidaClipDownloadItems),
					onRetryFailedDownloads = viewModel::retryFailedLidaClipDownloads,
					onDiscardFailedDownloads = viewModel::discardFailedLidaClipDownloads,
					onClearDownloadQueue = viewModel::clearLidaClipDownloadQueue
				)
				lidaClipDownloadItems
					.forEach { item ->
						LidaClipDownloadRow(
							item = item,
							onCancel = viewModel::cancelLidaClipDownload,
							onRetry = viewModel::retryLidaClipDownload
						)
					}
			}
		}
	}
}

@Composable
private fun LidaClipDownloadControlsRow(
	controls: LidaClipDownloadQueueControls,
	onRetryFailedDownloads: () -> Unit,
	onDiscardFailedDownloads: () -> Unit,
	onClearDownloadQueue: () -> Unit
) {
	if (
		!controls.canRetryFailedDownloads &&
		!controls.canDiscardFailedDownloads &&
		!controls.canClearDownloadQueue
	) return
	QueueControlsRow(
		canRetryFailedDownloads = controls.canRetryFailedDownloads,
		canDiscardFailedDownloads = controls.canDiscardFailedDownloads,
		canClearDownloadQueue = controls.canClearDownloadQueue,
		onRetryFailedDownloads = onRetryFailedDownloads,
		onDiscardFailedDownloads = onDiscardFailedDownloads,
		onClearDownloadQueue = onClearDownloadQueue
	)
}

@Composable
private fun NavicDownloadControlsRow(
	controls: NavicDownloadQueueControls,
	onRetryFailedDownloads: () -> Unit,
	onDiscardFailedDownloads: () -> Unit,
	onClearDownloadQueue: () -> Unit
) {
	if (
		!controls.canRetryFailedDownloads &&
		!controls.canDiscardFailedDownloads &&
		!controls.canClearDownloadQueue
	) return
	QueueControlsRow(
		canRetryFailedDownloads = controls.canRetryFailedDownloads,
		canDiscardFailedDownloads = controls.canDiscardFailedDownloads,
		canClearDownloadQueue = controls.canClearDownloadQueue,
		onRetryFailedDownloads = onRetryFailedDownloads,
		onDiscardFailedDownloads = onDiscardFailedDownloads,
		onClearDownloadQueue = onClearDownloadQueue
	)
}

@Composable
private fun QueueControlsRow(
	canRetryFailedDownloads: Boolean,
	canDiscardFailedDownloads: Boolean,
	canClearDownloadQueue: Boolean,
	onRetryFailedDownloads: () -> Unit,
	onDiscardFailedDownloads: () -> Unit,
	onClearDownloadQueue: () -> Unit
) {
	FormRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
			verticalAlignment = Alignment.CenterVertically
		) {
			if (canRetryFailedDownloads) {
				QueueActionIconButton(
					icon = Icons.Outlined.Refresh,
					contentDescription = stringResource(Res.string.action_retry_failed_downloads),
					onClick = onRetryFailedDownloads
				)
			}
			if (canDiscardFailedDownloads) {
				QueueActionIconButton(
					icon = Icons.Outlined.Delete,
					contentDescription = stringResource(Res.string.action_discard_failed_downloads),
					onClick = onDiscardFailedDownloads,
					tint = MaterialTheme.colorScheme.error
				)
			}
			if (canClearDownloadQueue) {
				QueueActionIconButton(
					icon = Icons.Outlined.Close,
					contentDescription = stringResource(Res.string.action_clear_download_queue),
					onClick = onClearDownloadQueue,
					tint = MaterialTheme.colorScheme.error
				)
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
private fun ActivityDownloadRow(
	item: ActivityDownloadItem,
	onCancel: (String) -> Unit,
	onRetry: (String) -> Unit
) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
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
				if (item.status == DownloadStatus.FAILED) {
					QueueActionIconButton(
						icon = Icons.Outlined.Refresh,
						contentDescription = stringResource(Res.string.action_retry),
						onClick = { onRetry(item.songId) }
					)
				}
				if (item.status == DownloadStatus.DOWNLOADING ||
					item.status == DownloadStatus.QUEUED ||
					item.status == DownloadStatus.FAILED
				) {
					QueueActionIconButton(
						icon = Icons.Outlined.Close,
						contentDescription = stringResource(Res.string.action_cancel),
						onClick = { onCancel(item.songId) },
						tint = MaterialTheme.colorScheme.error
					)
				}
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
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
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
				if (controls.canRetry) {
					QueueActionIconButton(
						icon = Icons.Outlined.Refresh,
						contentDescription = stringResource(Res.string.action_retry),
						onClick = { onRetry(item) }
					)
				}
				if (controls.canCancel) {
					QueueActionIconButton(
						icon = Icons.Outlined.Close,
						contentDescription = stringResource(Res.string.action_cancel),
						onClick = { onCancel(item) },
						tint = MaterialTheme.colorScheme.error
					)
				}
			}
			if (progress.active) {
				LinearProgressIndicator(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.height(3.dp)
				)
			}
		}
	}
}

@Composable
private fun LidaClipDownloadRow(
	item: LidaClipDownloadEntity,
	onCancel: (String) -> Unit,
	onRetry: (String) -> Unit
) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(Modifier.weight(1f)) {
					Text(
						text = item.title,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					lidaClipDownloadSubtitle(item)?.let { subtitle ->
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
				if (item.status == DownloadStatus.FAILED) {
					QueueActionIconButton(
						icon = Icons.Outlined.Refresh,
						contentDescription = stringResource(Res.string.action_retry),
						onClick = { onRetry(item.songId) }
					)
				}
				if (item.status == DownloadStatus.DOWNLOADING ||
					item.status == DownloadStatus.QUEUED ||
					item.status == DownloadStatus.FAILED
				) {
					QueueActionIconButton(
						icon = Icons.Outlined.Close,
						contentDescription = stringResource(Res.string.action_cancel),
						onClick = { onCancel(item.songId) },
						tint = MaterialTheme.colorScheme.error
					)
				}
			}
			if (item.status == DownloadStatus.DOWNLOADING) {
				Text(
					text = stringResource(Res.string.info_progress),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.padding(top = 8.dp)
				)
				LinearProgressIndicator(
					progress = { item.progress.coerceIn(0f, 1f) },
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
private fun QueueActionIconButton(
	icon: ImageVector,
	contentDescription: String,
	onClick: () -> Unit,
	tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
	IconButton(onClick = onClick) {
		Icon(
			imageVector = icon,
			contentDescription = contentDescription,
			tint = tint
		)
	}
}

@Composable
private fun downloadStatusText(status: DownloadStatus): String =
	when (status) {
		DownloadStatus.DOWNLOADING -> stringResource(Res.string.info_download_status_downloading)
		DownloadStatus.QUEUED -> stringResource(Res.string.info_download_status_queued)
		DownloadStatus.FAILED -> stringResource(Res.string.info_download_status_failed)
		DownloadStatus.DOWNLOADED -> "Ready"
		DownloadStatus.NOT_DOWNLOADED -> status.name.lowercase()
	}

private fun downloadSubtitle(item: ActivityDownloadItem): String? =
	listOfNotNull(item.artistName, item.albumTitle)
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.joinToString(" - ")
		.takeIf { it.isNotEmpty() }

private fun lidaClipDownloadSubtitle(item: LidaClipDownloadEntity): String? =
	listOfNotNull(item.artist, item.album, item.qualityTier)
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.joinToString(" - ")
		.takeIf { it.isNotEmpty() }
