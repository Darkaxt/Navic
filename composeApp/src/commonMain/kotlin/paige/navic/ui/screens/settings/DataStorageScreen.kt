package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.SingletonImageLoader
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_cancel_download
import navic.composeapp.generated.resources.action_search_history
import navic.composeapp.generated.resources.action_trigger_sync
import navic.composeapp.generated.resources.count_songs
import navic.composeapp.generated.resources.info_library_download
import navic.composeapp.generated.resources.info_library_download_warning
import navic.composeapp.generated.resources.info_not_available_offline
import navic.composeapp.generated.resources.info_progress
import navic.composeapp.generated.resources.info_status_calculating
import navic.composeapp.generated.resources.info_status_downloading
import navic.composeapp.generated.resources.info_sync_date_format
import navic.composeapp.generated.resources.info_sync_hours_ago
import navic.composeapp.generated.resources.info_sync_just_now
import navic.composeapp.generated.resources.info_sync_mins_ago
import navic.composeapp.generated.resources.info_sync_never
import navic.composeapp.generated.resources.option_auto_download_starred_albums
import navic.composeapp.generated.resources.option_auto_download_starred_songs
import navic.composeapp.generated.resources.option_cover_art_quality
import navic.composeapp.generated.resources.option_download_queue
import navic.composeapp.generated.resources.option_downloaded_songs
import navic.composeapp.generated.resources.option_image_cache_size
import navic.composeapp.generated.resources.option_last_sync
import navic.composeapp.generated.resources.option_lida_clips
import navic.composeapp.generated.resources.option_live_status
import navic.composeapp.generated.resources.option_max_concurrent_downloads
import navic.composeapp.generated.resources.option_musicbrainz_cache
import navic.composeapp.generated.resources.option_musicbrainz_artwork_fallback
import navic.composeapp.generated.resources.option_offline_mode
import navic.composeapp.generated.resources.option_pause_search_history
import navic.composeapp.generated.resources.option_pending_actions
import navic.composeapp.generated.resources.subtitle_auto_download_starred_albums
import navic.composeapp.generated.resources.subtitle_auto_download_starred_songs
import navic.composeapp.generated.resources.subtitle_download_queue
import navic.composeapp.generated.resources.subtitle_lida_clips
import navic.composeapp.generated.resources.subtitle_max_concurrent_downloads
import navic.composeapp.generated.resources.subtitle_musicbrainz_artwork_fallback
import navic.composeapp.generated.resources.subtitle_offline_mode
import navic.composeapp.generated.resources.subtitle_pause_search_history
import navic.composeapp.generated.resources.subtitle_pending_actions
import navic.composeapp.generated.resources.subtitle_rebuild_database
import navic.composeapp.generated.resources.subtitle_trigger_sync
import navic.composeapp.generated.resources.title_cache_management
import navic.composeapp.generated.resources.title_danger_zone
import navic.composeapp.generated.resources.title_data_storage
import navic.composeapp.generated.resources.title_library_download
import navic.composeapp.generated.resources.title_network
import navic.composeapp.generated.resources.title_sync_control
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DangerZoneAction
import paige.navic.domain.models.dangerZoneActions
import paige.navic.domain.models.settings.CoverArtQuality
import paige.navic.domain.models.settings.OfflineMode
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ChevronForward
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.Offline
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.dialogs.BulkDownloadDialog
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.components.SettingValueRow
import paige.navic.ui.screens.settings.dialogs.DownloadQueueDialog
import paige.navic.ui.screens.settings.viewmodels.SettingsDataStorageViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

@Composable
fun SettingsDataStorageScreen() {
	val viewModel = koinViewModel<SettingsDataStorageViewModel>()

	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val scope = rememberCoroutineScope()
	val coilPlatformContext = LocalCoilPlatformContext.current
	val imageLoader = SingletonImageLoader.get(coilPlatformContext)

	val syncState by viewModel.syncState.collectAsStateWithLifecycle()
	val pendingActionCount by viewModel.pendingActionCount.collectAsStateWithLifecycle()
	val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle(0)
	val downloadSize by viewModel.downloadSize.collectAsStateWithLifecycle(0L)
	val pendingDownloadCount by viewModel.pendingDownloadCount.collectAsStateWithLifecycle(0)
	val downloadQueueItems by viewModel.downloadQueueItems.collectAsStateWithLifecycle()
	val musicBrainzCacheStats by musicBrainzArtworkRepository.cacheStats.collectAsStateWithLifecycle()

	var showLibraryDownloadDialog by remember { mutableStateOf(false) }
	var showDownloadQueueDialog by remember { mutableStateOf(false) }
	var pendingDangerZoneAction by remember { mutableStateOf<DangerZoneAction?>(null) }
	val isDownloadingLibrary by viewModel.isDownloadingLibrary.collectAsStateWithLifecycle()
	val libraryDownloadProgress by viewModel.libraryDownloadProgress.collectAsStateWithLifecycle()

	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

	val calculating = stringResource(Res.string.info_status_calculating)
	var imageCacheSizeText by remember { mutableStateOf(calculating) }

	val downloadedSize = remember(downloadSize) { downloadStorageSizeText(downloadSize) }

	val smoothSyncProgress by animateFloatAsState(
		if (syncState.isSyncing) syncState.progress else 0f,
		animationSpec = tween(
			durationMillis = 250,
			easing = EaseOut
		)
	)

	val smoothLibraryDownloadProgress by animateFloatAsState(
		targetValue = libraryDownloadProgress.coerceIn(0f, 1f),
		animationSpec = tween(durationMillis = 500, easing = EaseOut)
	)

	fun runDangerZoneAction(action: DangerZoneAction) {
		when (action) {
			DangerZoneAction.ClearImageCache -> {
				imageLoader.memoryCache?.clear()
				musicBrainzArtworkRepository.clearCache()
				scope.launch(Dispatchers.IO) {
					imageLoader.diskCache?.clear()
					imageCacheSizeText = imageCacheStorageSizeText(0)
				}
			}
			DangerZoneAction.ClearPendingSyncActions -> viewModel.removeAllActions()
			DangerZoneAction.ClearDownloads -> viewModel.clearAllDownloads()
			DangerZoneAction.RebuildDatabase -> if (isOnline) viewModel.rebuildDatabase()
		}
	}

	val offlineModifier = Modifier.alpha(if (isOnline) 1f else 0.75f)
	val offlineIcon = @Composable {
		if (!isOnline) {
			Icon(
				Icons.Outlined.Offline,
				stringResource(Res.string.info_not_available_offline),
				modifier = Modifier.size(20.dp)
			)
		}
	}

	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			val sizeBytes = imageLoader.diskCache?.size ?: 0L
			imageCacheSizeText = imageCacheStorageSizeText(sizeBytes)
		}
	}

	BulkDownloadDialog(
		title = stringResource(Res.string.title_library_download),
		message = stringResource(Res.string.info_library_download_warning),
		showDialog = showLibraryDownloadDialog,
		onDismissRequest = { showLibraryDownloadDialog = false },
		onConfirm = {
			showLibraryDownloadDialog = false
			viewModel.downloadEntireLibrary()
		}
	)

	DownloadQueueDialog(
		showDialog = showDownloadQueueDialog,
		items = downloadQueueItems,
		onDismissRequest = { showDownloadQueueDialog = false },
		onCancelDownload = viewModel::cancelDownload,
		onCancelPendingDownloads = viewModel::cancelPendingDownloads,
		onRetryFailedDownloads = viewModel::retryFailedDownloads
	)

	DangerZoneConfirmationDialog(
		action = pendingDangerZoneAction,
		onDismissRequest = { pendingDangerZoneAction = null },
		onConfirm = { action ->
			runDangerZoneAction(action)
			pendingDangerZoneAction = null
		}
	)

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_data_storage)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		},
		contentWindowInsets = WindowInsets.statusBars
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			Column(
				Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(top = 16.dp, end = 16.dp, start = 16.dp, bottom = 32.dp)
			) {
				FormTitle(stringResource(Res.string.title_network))
				Form {
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_offline_mode)) },
						items = OfflineMode.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						description = stringResource(Res.string.subtitle_offline_mode),
						selection = preferenceManager.offlineMode,
						onSelect = { preferenceManager.offlineMode = it }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_cover_art_quality)) },
						items = CoverArtQuality.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.coverArtQuality,
						onSelect = {
							preferenceManager.coverArtQuality = it
							imageLoader.memoryCache?.clear()
							scope.launch(Dispatchers.IO) {
								imageLoader.diskCache?.clear()
								imageCacheSizeText = imageCacheStorageSizeText(0)
							}
						}
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_musicbrainz_artwork_fallback)) },
						subtitle = { Text(stringResource(Res.string.subtitle_musicbrainz_artwork_fallback)) },
						value = preferenceManager.musicBrainzArtworkFallbackEnabled,
						onSetValue = {
							preferenceManager.musicBrainzArtworkFallbackEnabled = it
							musicBrainzArtworkRepository.refreshCacheVisibility()
						}
					)
					FormRow(
						onClick = dropUnlessResumed {
							backStack.add(Screen.Settings.LidaClips)
						}
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.option_lida_clips))
							Text(
								stringResource(Res.string.subtitle_lida_clips),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Icon(Icons.Outlined.ChevronForward, null)
					}
				}

				FormTitle(stringResource(Res.string.action_search_history))
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_pause_search_history)) },
						subtitle = { Text(stringResource(Res.string.subtitle_pause_search_history)) },
						value = preferenceManager.pauseSearchHistory,
						onSetValue = { preferenceManager.pauseSearchHistory = it }
					)
				}

				FormTitle(stringResource(Res.string.title_sync_control))
				Form {
					FormRow {
						Column(Modifier.fillMaxWidth()) {
							Column {
								Text(stringResource(Res.string.option_live_status))
								Text(
									text = stringResource(syncState.message),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
							AnimatedVisibility(
								syncState.isSyncing,
								enter = fadeIn() + expandVertically(clip = false),
								exit = fadeOut() + shrinkVertically(clip = false)
							) {
								LinearProgressIndicator(
									progress = {
										if (!syncState.isSyncing)
											1f
										else smoothSyncProgress.coerceIn(0f, 1f)
									},
									modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
								)
							}
						}
					}

					FormRow(
						modifier = offlineModifier,
						onClick = if (isOnline) {
							{ viewModel.triggerManualSync() }
						} else null
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.action_trigger_sync))
							Text(
								stringResource(Res.string.subtitle_trigger_sync),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						offlineIcon()
					}

					FormRow {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.option_last_sync))
							Text(
								text = if (preferenceManager.lastFullSyncTime == 0L) {
									stringResource(Res.string.info_sync_never)
								} else {
									Instant.fromEpochMilliseconds(
										preferenceManager.lastFullSyncTime
									).toRelativeString(
										justNow = stringResource(Res.string.info_sync_just_now),
										minsAgo = stringResource(Res.string.info_sync_mins_ago),
										hoursAgo = stringResource(Res.string.info_sync_hours_ago),
										dateFormat = stringResource(Res.string.info_sync_date_format)
									)
								},
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}

				FormTitle(stringResource(Res.string.title_cache_management))
				Form {
					FormRow {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.option_pending_actions))
							Text(
								stringResource(
									Res.string.subtitle_pending_actions,
									pendingActionCount
								),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}

					SettingValueRow(
						title = { Text(stringResource(Res.string.option_downloaded_songs)) },
						subtitle = {
							Text(
								pluralStringResource(
									Res.plurals.count_songs,
									downloadCount,
									downloadCount
								)
							)
						},
						value = downloadedSize
					)

					SettingValueRow(
						title = { Text(stringResource(Res.string.option_download_queue)) },
						subtitle = { Text(stringResource(Res.string.subtitle_download_queue)) },
						value = pluralStringResource(
							Res.plurals.count_songs,
							pendingDownloadCount,
							pendingDownloadCount
						),
						onClick = { showDownloadQueueDialog = true }
					)

					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_max_concurrent_downloads)) },
						items = downloadConcurrencyOptions.toImmutableList(),
						label = { count ->
							pluralStringResource(Res.plurals.count_songs, count, count)
						},
						description = stringResource(Res.string.subtitle_max_concurrent_downloads),
						selection = preferenceManager.maxConcurrentDownloads,
						onSelect = { preferenceManager.maxConcurrentDownloads = it }
					)

					SettingValueRow(
						title = { Text(stringResource(Res.string.option_image_cache_size)) },
						value = imageCacheSizeText
					)

					SettingValueRow(
						title = { Text(stringResource(Res.string.option_musicbrainz_cache)) },
						subtitle = {
							Text(
								musicBrainzCacheSummaryText(
									artworkSongs = musicBrainzCacheStats.artworkSongs,
									metadataSongs = musicBrainzCacheStats.metadataSongs,
									missingSongs = musicBrainzCacheStats.missingSongs
								)
							)
						},
						value = musicBrainzCacheValueText(musicBrainzCacheStats.totalSongs)
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_auto_download_starred_songs)) },
						subtitle = { Text(stringResource(Res.string.subtitle_auto_download_starred_songs)) },
						value = preferenceManager.autoDownloadStarredSongs,
						onSetValue = { preferenceManager.autoDownloadStarredSongs = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_auto_download_starred_albums)) },
						subtitle = { Text(stringResource(Res.string.subtitle_auto_download_starred_albums)) },
						value = preferenceManager.autoDownloadStarredAlbums,
						onSetValue = { preferenceManager.autoDownloadStarredAlbums = it }
					)

					FormRow(
						modifier = offlineModifier,
						onClick = if (!isDownloadingLibrary && isOnline) {
							{ showLibraryDownloadDialog = true }
						} else null
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.title_library_download))
							Text(
								text = stringResource(if (isDownloadingLibrary) Res.string.info_status_downloading else Res.string.info_library_download),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)

							AnimatedVisibility(
								visible = isDownloadingLibrary,
								enter = fadeIn() + expandVertically(clip = false),
								exit = fadeOut() + shrinkVertically(clip = false)
							) {
								Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween,
										verticalAlignment = Alignment.CenterVertically
									) {
										Text(
											text = stringResource(Res.string.info_progress),
											style = MaterialTheme.typography.labelMedium,
											color = MaterialTheme.colorScheme.primary
										)

										Row(verticalAlignment = Alignment.CenterVertically) {
											TextButton(
												onClick = {
													platformContext.clickSound()
													viewModel.cancelLibraryDownload()
												},
												contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
												modifier = Modifier.padding(end = 8.dp)
											) {
												Text(
													stringResource(Res.string.action_cancel_download),
													style = MaterialTheme.typography.labelLarge,
													color = MaterialTheme.colorScheme.error
												)
											}

											Text(
												text = "${(smoothLibraryDownloadProgress * 100).toInt()}%",
												style = MaterialTheme.typography.labelMedium,
												color = MaterialTheme.colorScheme.primary
											)
										}
									}

									LinearProgressIndicator(
										progress = { smoothLibraryDownloadProgress },
										modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
									)
								}
							}
						}
						offlineIcon()
					}
				}

				FormTitle(stringResource(Res.string.title_danger_zone))
				Form {
					dangerZoneActions().forEach { action ->
						val enabled = action != DangerZoneAction.RebuildDatabase || isOnline
						FormRow(
							modifier = if (action == DangerZoneAction.RebuildDatabase) {
								offlineModifier
							} else {
								Modifier
							},
							onClick = if (enabled) {
								{ pendingDangerZoneAction = action }
							} else null
						) {
							Column(Modifier.weight(1f)) {
								Text(
									stringResource(action.title),
									color = MaterialTheme.colorScheme.error
								)
								if (action == DangerZoneAction.RebuildDatabase) {
									Text(
										stringResource(Res.string.subtitle_rebuild_database),
										style = MaterialTheme.typography.bodyMedium,
										color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
									)
								}
							}
							if (action == DangerZoneAction.RebuildDatabase) {
								offlineIcon()
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun DangerZoneConfirmationDialog(
	action: DangerZoneAction?,
	onDismissRequest: () -> Unit,
	onConfirm: (DangerZoneAction) -> Unit
) {
	action ?: return

	FormDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
		title = { Text(stringResource(action.title)) },
		buttons = {
			FormButton(
				onClick = { onConfirm(action) },
				color = MaterialTheme.colorScheme.error
			) {
				Text(stringResource(action.title))
			}
			FormButton(onClick = onDismissRequest) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
		content = {
			Text(stringResource(action.confirmationMessage))
		}
	)
}

private fun Instant.toRelativeString(
	justNow: String,
	minsAgo: String,
	hoursAgo: String,
	dateFormat: String
): String {
	val now = Clock.System.now()
	val diff = now - this
	val seconds = diff.inWholeSeconds

	return when {
		seconds < 60 -> justNow
		seconds < 3600 -> minsAgo.replace($$"%1$d", (seconds / 60).toString())
		seconds < 86400 -> hoursAgo.replace($$"%1$d", (seconds / 3600).toString())
		else -> {
			val date = this.toLocalDateTime(TimeZone.currentSystemDefault())
			val monthName = date.month.name.lowercase().take(3)
			dateFormat
				.replace($$"%1$d", date.day.toString())
				.replace($$"%1$s", monthName)
		}
	}
}

private val downloadConcurrencyOptions = listOf(1, 2, 3, 5, 10)
