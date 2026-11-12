package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_status_calculating
import navic.composeapp.generated.resources.option_cache_size_limit
import navic.composeapp.generated.resources.option_cache_size_on_disk
import navic.composeapp.generated.resources.option_musicbrainz_cache
import navic.composeapp.generated.resources.subtitle_image_cache_size_limit
import navic.composeapp.generated.resources.subtitle_lida_clips_video_cache_size
import navic.composeapp.generated.resources.subtitle_musicbrainz_cache_limit
import navic.composeapp.generated.resources.subtitle_page_preview_cache_size_limit
import navic.composeapp.generated.resources.subtitle_streamed_song_cache_size_limit
import navic.composeapp.generated.resources.title_bindery_reader_cache
import navic.composeapp.generated.resources.title_cache_manager
import navic.composeapp.generated.resources.title_image_cache
import navic.composeapp.generated.resources.title_lida_clips_video_cache
import navic.composeapp.generated.resources.title_musicbrainz_cache
import navic.composeapp.generated.resources.title_streamed_song_cache
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.StorageManager
import paige.navic.domain.models.DangerZoneAction
import paige.navic.domain.models.ImageCacheSizeOptionsMb
import paige.navic.domain.models.LidaClipsVideoCacheSizeOptionsMb
import paige.navic.domain.models.MusicBrainzCacheMaxSongsOptions
import paige.navic.domain.models.PagePreviewCacheSizeOptionsMb
import paige.navic.domain.models.StreamedSongCacheSizeOptionsMb
import paige.navic.domain.models.cacheSizeLimitLabel
import paige.navic.domain.models.cacheSizeMbToBytes
import paige.navic.domain.models.lidaClipCachePrunePlan
import paige.navic.domain.models.lidaClipVideoCacheSizeBytes
import paige.navic.domain.models.lidaClipsVideoCacheSizeLabel
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingValueRow
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

/** Per-cache size on disk, size limit and clear action. */
@Composable
fun SettingsCacheManagerScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val storageManager = koinInject<StorageManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val binderyRepository = koinInject<BinderyRepository>()
	val scope = rememberCoroutineScope()
	val imageLoader = SingletonImageLoader.get(LocalCoilPlatformContext.current)
	val musicBrainzCacheStats by musicBrainzArtworkRepository.cacheStats.collectAsStateWithLifecycle()

	var pendingAction by remember { mutableStateOf<DangerZoneAction?>(null) }
	// Bumped after every clear or limit change so the sizes below are re-measured.
	var refreshKey by remember { mutableIntStateOf(0) }
	var streamedSongSize by remember { mutableStateOf<Long?>(null) }
	var imageSize by remember { mutableStateOf<Long?>(null) }
	var lidaClipsVideoSize by remember { mutableStateOf<Long?>(null) }
	var readerPublicationSize by remember { mutableStateOf<Long?>(null) }

	LaunchedEffect(refreshKey) {
		withContext(Dispatchers.IO) {
			streamedSongSize = storageManager.streamedSongCacheSizeBytes()
			imageSize = imageLoader.diskCache?.size ?: 0L
			lidaClipsVideoSize = storageManager.listLidaClipVideoCacheFiles().sumOf { it.sizeBytes.coerceAtLeast(0L) }
			readerPublicationSize = storageManager.readerPublicationCacheSizeBytes()
		}
	}

	fun runInBackground(block: suspend () -> Unit) {
		scope.launch(Dispatchers.IO) {
			block()
			refreshKey++
		}
	}

	fun runClear(action: DangerZoneAction) {
		when (action) {
			DangerZoneAction.ClearStreamedSongCache -> runInBackground { storageManager.clearStreamedSongCache() }
			DangerZoneAction.ClearImageCache -> {
				imageLoader.memoryCache?.clear()
				runInBackground { imageLoader.diskCache?.clear() }
			}
			DangerZoneAction.ClearLidaClipsVideoCache -> runInBackground { storageManager.clearLidaClipVideoCache() }
			DangerZoneAction.ClearBinderyCache -> runInBackground {
				// Page previews may be held by an open book, so they are removed on the next app start.
				preferenceManager.clearPagePreviewsOnNextStart = true
				storageManager.clearReaderPublicationCache()
				binderyRepository.clearMetadataCache()
			}
			DangerZoneAction.ClearMusicBrainzCache -> musicBrainzArtworkRepository.clearCache()
			else -> Unit
		}
	}

	DangerZoneConfirmationDialog(
		action = pendingAction,
		onDismissRequest = { pendingAction = null },
		onConfirm = { action ->
			runClear(action)
			pendingAction = null
		}
	)

	val calculating = stringResource(Res.string.info_status_calculating)
	fun sizeText(bytes: Long?): String = bytes?.let(::storageSizeText) ?: calculating

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_cache_manager)) },
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
				FormTitle(stringResource(Res.string.title_streamed_song_cache))
				Form {
					SizeOnDiskRow(sizeText(streamedSongSize))
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_cache_size_limit)) },
						items = StreamedSongCacheSizeOptionsMb.toImmutableList(),
						label = { cacheSizeLimitLabel(it) },
						description = stringResource(Res.string.subtitle_streamed_song_cache_size_limit),
						selection = preferenceManager.streamedSongCacheSizeMb,
						onSelect = {
							preferenceManager.streamedSongCacheSizeMb = it
							runInBackground { storageManager.setStreamedSongCacheMaxBytes(cacheSizeMbToBytes(it)) }
						}
					)
					ClearCacheRow(DangerZoneAction.ClearStreamedSongCache) { pendingAction = it }
				}

				FormTitle(stringResource(Res.string.title_image_cache))
				Form {
					SizeOnDiskRow(sizeText(imageSize))
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_cache_size_limit)) },
						items = ImageCacheSizeOptionsMb.toImmutableList(),
						label = { cacheSizeLimitLabel(it) },
						description = stringResource(Res.string.subtitle_image_cache_size_limit),
						selection = preferenceManager.imageCacheSizeMb,
						onSelect = { preferenceManager.imageCacheSizeMb = it }
					)
					ClearCacheRow(DangerZoneAction.ClearImageCache) { pendingAction = it }
				}

				FormTitle(stringResource(Res.string.title_lida_clips_video_cache))
				Form {
					SizeOnDiskRow(sizeText(lidaClipsVideoSize))
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_cache_size_limit)) },
						items = LidaClipsVideoCacheSizeOptionsMb.toImmutableList(),
						label = { lidaClipsVideoCacheSizeLabel(it) },
						description = stringResource(Res.string.subtitle_lida_clips_video_cache_size),
						selection = preferenceManager.lidaClipsVideoCacheSizeMb,
						onSelect = {
							preferenceManager.lidaClipsVideoCacheSizeMb = it
							runInBackground {
								lidaClipCachePrunePlan(
									files = storageManager.listLidaClipVideoCacheFiles(),
									maxSizeBytes = lidaClipVideoCacheSizeBytes(it),
									protectedPath = ""
								).forEach(storageManager::deleteFile)
							}
						}
					)
					ClearCacheRow(DangerZoneAction.ClearLidaClipsVideoCache) { pendingAction = it }
				}

				FormTitle(stringResource(Res.string.title_bindery_reader_cache))
				Form {
					SizeOnDiskRow(sizeText(readerPublicationSize))
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_cache_size_limit)) },
						items = PagePreviewCacheSizeOptionsMb.toImmutableList(),
						label = { cacheSizeLimitLabel(it) },
						description = stringResource(Res.string.subtitle_page_preview_cache_size_limit),
						selection = preferenceManager.pagePreviewCacheSizeMb,
						onSelect = { preferenceManager.pagePreviewCacheSizeMb = it }
					)
					ClearCacheRow(DangerZoneAction.ClearBinderyCache) { pendingAction = it }
				}

				FormTitle(stringResource(Res.string.title_musicbrainz_cache))
				Form {
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
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_cache_size_limit)) },
						items = MusicBrainzCacheMaxSongsOptions.toImmutableList(),
						label = { musicBrainzCacheValueText(it) },
						description = stringResource(Res.string.subtitle_musicbrainz_cache_limit),
						selection = preferenceManager.musicBrainzCacheMaxSongs,
						onSelect = {
							preferenceManager.musicBrainzCacheMaxSongs = it
							musicBrainzArtworkRepository.applyCacheLimit()
						}
					)
					ClearCacheRow(DangerZoneAction.ClearMusicBrainzCache) { pendingAction = it }
				}
			}
		}
	}
}

@Composable
private fun SizeOnDiskRow(value: String) {
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_cache_size_on_disk)) },
		value = value
	)
}

@Composable
private fun ClearCacheRow(action: DangerZoneAction, onClick: (DangerZoneAction) -> Unit) {
	FormRow(onClick = { onClick(action) }) {
		Text(
			stringResource(action.title),
			color = MaterialTheme.colorScheme.error
		)
	}
}
