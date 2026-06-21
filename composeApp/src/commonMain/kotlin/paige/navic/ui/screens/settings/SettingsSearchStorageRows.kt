package paige.navic.ui.screens.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.MaxNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MaxNowPlayingBackgroundDimPercent
import paige.navic.domain.models.MinNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MinNowPlayingBackgroundDimPercent
import paige.navic.domain.models.LidaClipsVideoCacheSizeOptionsMb
import paige.navic.domain.models.lidaClipsVideoCacheSizeLabel
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.models.nowPlayingBackgroundBlurDp
import paige.navic.domain.models.settings.*
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderFontSourceNavic
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderOrientationDefault
import paige.navic.reader.ReaderNavBarTypeVerticalRight
import paige.navic.reader.ReaderPdfFitWidth
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneInvertNone
import kotlin.math.roundToInt

@Composable
internal fun settingsSearchStorageRows(context: SettingsSearchContext): List<SearchableSettingsRow> = with(context) {
	buildList {
		add(selectionRow(
			id = "data.offline-mode",
			path = path(dataStorage, network),
			title = stringResource(Res.string.option_offline_mode),
			subtitle = stringResource(Res.string.subtitle_offline_mode),
			items = OfflineMode.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.offlineMode,
			onSelect = { preferenceManager.offlineMode = it }
		))
		add(selectionRow(
			id = "data.cover-quality",
			path = path(dataStorage, network),
			title = stringResource(Res.string.option_cover_art_quality),
			items = CoverArtQuality.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.coverArtQuality,
			onSelect = { preferenceManager.coverArtQuality = it }
		))
		add(switchRow(
			id = "integrations.musicbrainz",
			path = path(integrations),
			title = stringResource(Res.string.option_musicbrainz_artwork_fallback),
			subtitle = stringResource(Res.string.subtitle_musicbrainz_artwork_fallback),
			keywords = listOf("cover art archive", "metadata", "artwork"),
			value = preferenceManager.musicBrainzArtworkFallbackEnabled,
			onSetValue = {
				preferenceManager.musicBrainzArtworkFallbackEnabled = it
				musicBrainzArtworkRepository.refreshCacheVisibility()
			}
		))
		add(valueRow(
			id = "data.ebook-cache",
			path = path(dataStorage, cacheManagement),
			title = stringResource(Res.string.option_ebook_cache_size),
			subtitle = stringResource(Res.string.info_clear_ebook_cache_confirmation),
			keywords = listOf("ebook", "epub", "pdf", "reader", "cache", "bindery", "clear cache"),
			value = readerPublicationCacheStorageSizeText(readerPublicationCacheSize)
		))
		add(valueRow(
			id = "data.musicbrainz-cache",
			path = path(dataStorage, cacheManagement),
			title = stringResource(Res.string.option_musicbrainz_cache),
			subtitle = musicBrainzCacheSummaryText(
				artworkSongs = musicBrainzCacheStats.artworkSongs,
				metadataSongs = musicBrainzCacheStats.metadataSongs,
				missingSongs = musicBrainzCacheStats.missingSongs
			),
			keywords = listOf("cover art archive", "metadata", "artwork", "cache"),
			value = musicBrainzCacheValueText(musicBrainzCacheStats.totalSongs)
		))
		add(valueRow(
			id = "data.lida-offline-clips",
			path = path(dataStorage, cacheManagement),
			title = stringResource(Res.string.option_lida_clips_offline_clips),
			subtitle = lidaClipsOfflineClipCountText(lidaClipOfflineFiles.size),
			keywords = listOf("lida", "music video clips", "offline", "cache", "download"),
			value = lidaClipsOfflineStorageSizeText(lidaClipOfflineSize)
		))
		add(valueRow(
			id = "data.lida-video-cache",
			path = path(dataStorage, cacheManagement),
			title = stringResource(Res.string.action_clear_lidaclips_video_cache),
			subtitle = stringResource(Res.string.info_clear_lidaclips_video_cache_confirmation),
			keywords = listOf("lida", "music video clips", "video cache", "clear cache"),
			value = ""
		))
		add(switchRow(
			id = "data.pause-search-history",
			path = path(dataStorage, stringResource(Res.string.action_search_history)),
			title = stringResource(Res.string.option_pause_search_history),
			subtitle = stringResource(Res.string.subtitle_pause_search_history),
			value = preferenceManager.pauseSearchHistory,
			onSetValue = { preferenceManager.pauseSearchHistory = it }
		))
		add(selectionRow(
			id = "data.max-concurrent-downloads",
			path = path(dataStorage, cacheManagement),
			title = stringResource(Res.string.option_max_concurrent_downloads),
			subtitle = stringResource(Res.string.subtitle_max_concurrent_downloads),
			keywords = listOf("download cap", "parallel downloads"),
			items = downloadConcurrencySearchOptions,
			label = { pluralStringResource(Res.plurals.count_songs, it, it) },
			selection = preferenceManager.maxConcurrentDownloads,
			onSelect = { preferenceManager.maxConcurrentDownloads = it }
		))
		add(switchRow(
			id = "data.auto-download-starred-songs",
			path = path(dataStorage, cacheManagement),
			title = stringResource(Res.string.option_auto_download_starred_songs),
			subtitle = stringResource(Res.string.subtitle_auto_download_starred_songs),
			value = preferenceManager.autoDownloadStarredSongs,
			onSetValue = { preferenceManager.autoDownloadStarredSongs = it }
		))
		add(switchRow(
			id = "data.auto-download-starred-albums",
			path = path(dataStorage, cacheManagement),
			title = stringResource(Res.string.option_auto_download_starred_albums),
			subtitle = stringResource(Res.string.subtitle_auto_download_starred_albums),
			value = preferenceManager.autoDownloadStarredAlbums,
			onSetValue = { preferenceManager.autoDownloadStarredAlbums = it }
		))

	}
}