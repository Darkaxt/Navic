package paige.navic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_lyrics
import navic.composeapp.generated.resources.title_actions
import navic.composeapp.generated.resources.title_appearance
import navic.composeapp.generated.resources.title_aurral
import navic.composeapp.generated.resources.title_behaviour
import navic.composeapp.generated.resources.title_bindery
import navic.composeapp.generated.resources.title_bottom_app_bar
import navic.composeapp.generated.resources.title_cache_management
import navic.composeapp.generated.resources.title_data_storage
import navic.composeapp.generated.resources.title_developer
import navic.composeapp.generated.resources.title_ebook_reader
import navic.composeapp.generated.resources.title_integrations
import navic.composeapp.generated.resources.title_lastfm
import navic.composeapp.generated.resources.title_layout
import navic.composeapp.generated.resources.title_lida_clips
import navic.composeapp.generated.resources.title_library
import navic.composeapp.generated.resources.title_mini_player
import navic.composeapp.generated.resources.title_navigation_bar
import navic.composeapp.generated.resources.title_network
import navic.composeapp.generated.resources.title_now_playing
import navic.composeapp.generated.resources.title_playback
import navic.composeapp.generated.resources.title_settings
import navic.composeapp.generated.resources.title_streaming_quality
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.AppLogManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.LidaClipCacheFileInfo
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.repositories.MusicBrainzCacheStats
import paige.navic.reader.ReaderSettings
import paige.navic.reader.readerDefaultSettings
import paige.navic.shared.MediaPlayerViewModel
import kotlin.math.roundToInt

internal data class SettingsSearchContext(
	val preferenceManager: PreferenceManager,
	val appLogManager: AppLogManager,
	val player: MediaPlayerViewModel,
	val sessionManager: SessionManager,
	val musicBrainzArtworkRepository: MusicBrainzArtworkRepository,
	val musicBrainzCacheStats: MusicBrainzCacheStats,
	val lidaClipOfflineFiles: List<LidaClipCacheFileInfo>,
	val lidaClipOfflineSize: Long,
	val readerPublicationCacheSize: Long,
	val isAndroid: Boolean,
	val isApple: Boolean,
	val settings: String,
	val appearance: String,
	val nowPlaying: String,
	val bottomBar: String,
	val playback: String,
	val ebooks: String,
	val dataStorage: String,
	val integrations: String,
	val developer: String,
	val layout: String,
	val library: String,
	val actions: String,
	val behaviour: String,
	val lyrics: String,
	val network: String,
	val lidaClips: String,
	val lastFm: String,
	val bindery: String,
	val aurral: String,
	val cacheManagement: String,
	val miniPlayer: String,
	val navigationBar: String,
	val streamingQuality: String,
	val readerSettings: ReaderSettings,
	val readerLineHeightPercent: Int
) {
	fun path(vararg parts: String): String =
		(listOf(settings) + parts).joinToString(" > ")
}

@Composable
internal fun searchableSettingsRows(): List<SearchableSettingsRow> {
	val preferenceManager = koinInject<PreferenceManager>()
	val appLogManager = koinInject<AppLogManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val sessionManager = koinInject<SessionManager>()
	val storageManager = koinInject<paige.navic.domain.manager.StorageManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val musicBrainzCacheStats by musicBrainzArtworkRepository.cacheStats.collectAsStateWithLifecycle()
	val lidaClipOfflineFiles = remember { storageManager.listLidaClipOfflineFiles() }
	val lidaClipOfflineSize = remember(lidaClipOfflineFiles) {
		lidaClipOfflineFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
	}
	val readerPublicationCacheSize = remember { storageManager.readerPublicationCacheSizeBytes() }
	val platformContext = LocalPlatformContext.current
	val readerSettings = preferenceManager.readerDefaultSettings()
	val context = SettingsSearchContext(
		preferenceManager = preferenceManager,
		appLogManager = appLogManager,
		player = player,
		sessionManager = sessionManager,
		musicBrainzArtworkRepository = musicBrainzArtworkRepository,
		musicBrainzCacheStats = musicBrainzCacheStats,
		lidaClipOfflineFiles = lidaClipOfflineFiles,
		lidaClipOfflineSize = lidaClipOfflineSize,
		readerPublicationCacheSize = readerPublicationCacheSize,
		isAndroid = platformContext.name.lowercase().startsWith("android"),
		isApple = listOf("ios", "ipados").contains(platformContext.name.lowercase()),
		settings = stringResource(Res.string.title_settings),
		appearance = stringResource(Res.string.title_appearance),
		nowPlaying = stringResource(Res.string.title_now_playing),
		bottomBar = stringResource(Res.string.title_bottom_app_bar),
		playback = stringResource(Res.string.title_playback),
		ebooks = stringResource(Res.string.title_ebook_reader),
		dataStorage = stringResource(Res.string.title_data_storage),
		integrations = stringResource(Res.string.title_integrations),
		developer = stringResource(Res.string.title_developer),
		layout = stringResource(Res.string.title_layout),
		library = stringResource(Res.string.title_library),
		actions = stringResource(Res.string.title_actions),
		behaviour = stringResource(Res.string.title_behaviour),
		lyrics = stringResource(Res.string.action_lyrics),
		network = stringResource(Res.string.title_network),
		lidaClips = stringResource(Res.string.title_lida_clips),
		lastFm = stringResource(Res.string.title_lastfm),
		bindery = stringResource(Res.string.title_bindery),
		aurral = stringResource(Res.string.title_aurral),
		cacheManagement = stringResource(Res.string.title_cache_management),
		miniPlayer = stringResource(Res.string.title_mini_player),
		navigationBar = stringResource(Res.string.title_navigation_bar),
		streamingQuality = stringResource(Res.string.title_streaming_quality),
		readerSettings = readerSettings,
		readerLineHeightPercent = (((readerSettings.lineHeight ?: 1.55) * 100.0).roundToInt())
	)

	return buildList {
		addAll(settingsSearchAppearanceRows(context))
		addAll(settingsSearchStreamingRows(context))
		addAll(settingsSearchEbookRows(context))
		addAll(settingsSearchPlaybackRows(context))
		addAll(settingsSearchStorageRows(context))
		addAll(settingsSearchIntegrationRows(context))
		addAll(settingsSearchDeveloperRows(context))
	}
}
