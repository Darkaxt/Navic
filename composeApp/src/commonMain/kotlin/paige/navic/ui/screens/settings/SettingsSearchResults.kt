package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.AppLogManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.StorageManager
import paige.navic.domain.models.MaxNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MaxNowPlayingBackgroundDimPercent
import paige.navic.domain.models.MinNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MinNowPlayingBackgroundDimPercent
import paige.navic.domain.models.BinderyMaxBookGridColumns
import paige.navic.domain.models.BinderyMinBookGridColumns
import paige.navic.domain.models.LidaClipsVideoCacheSizeOptionsMb
import paige.navic.domain.models.lidaClipsVideoCacheSizeLabel
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.models.nowPlayingBackgroundBlurDp
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.models.settings.*
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderBookFontFamily
import paige.navic.reader.ReaderBlackTheme
import paige.navic.reader.ReaderDarkTheme
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderDirectionLtr
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderDuskTheme
import paige.navic.reader.ReaderDyslexicFontFamily
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderFontSourceNavic
import paige.navic.reader.ReaderFontSourcePublisher
import paige.navic.reader.ReaderFontSourceSystem
import paige.navic.reader.ReaderHumanistFontFamily
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderMonoFontFamily
import paige.navic.reader.ReaderOrientationDefault
import paige.navic.reader.ReaderOrientationFree
import paige.navic.reader.ReaderOrientationLandscape
import paige.navic.reader.ReaderOrientationLockedLandscape
import paige.navic.reader.ReaderOrientationLockedPortrait
import paige.navic.reader.ReaderOrientationPortrait
import paige.navic.reader.ReaderOrientationReversePortrait
import paige.navic.reader.ReaderPublisherFontFamily
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderSepiaTheme
import paige.navic.reader.ReaderSerifFontFamily
import paige.navic.reader.ReaderSupportedFlowModes
import paige.navic.reader.ReaderSupportedFontFamilies
import paige.navic.reader.ReaderSupportedFontSources
import paige.navic.reader.ReaderSupportedDirections
import paige.navic.reader.ReaderSupportedOrientations
import paige.navic.reader.ReaderSupportedTapZones
import paige.navic.reader.ReaderSupportedThemes
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneDisabled
import paige.navic.reader.ReaderTapZoneEdge
import paige.navic.reader.ReaderTapZoneKindle
import paige.navic.reader.ReaderTapZoneLShaped
import paige.navic.reader.ReaderTapZoneRightLeft
import paige.navic.reader.readerDefaultSettings
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.components.SettingValueRow
import kotlin.math.roundToInt

private data class SearchableSettingsRow(
	val text: SettingsSearchEntryText,
	val content: @Composable () -> Unit
)

@Composable
fun SettingsSearchResults(query: String) {
	val rows = searchableSettingsRows()
	val rowById = rows.associateBy { it.text.id }
	val resultRows = filteredSettingsSearchResultItems(
		entries = rows.map { it.text },
		query = query
	).mapNotNull { result ->
		rowById[result.entry.id]?.let { row -> result.path to row }
	}

	CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
		if (resultRows.isEmpty()) {
			Form {
				FormRow {
					Text(
						stringResource(Res.string.info_no_search_results),
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			return@CompositionLocalProvider
		}

		resultRows.forEach { (path, row) ->
			Form(bottomPadding = 12.dp) {
				Text(
					text = path,
					modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary
				)
				row.content()
			}
		}
	}
}

@Composable
private fun searchableSettingsRows(): List<SearchableSettingsRow> {
	val preferenceManager = koinInject<PreferenceManager>()
	val appLogManager = koinInject<AppLogManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val sessionManager = koinInject<SessionManager>()
	val storageManager = koinInject<StorageManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val musicBrainzCacheStats by musicBrainzArtworkRepository.cacheStats.collectAsStateWithLifecycle()
	val lidaClipOfflineFiles = remember { storageManager.listLidaClipOfflineFiles() }
	val lidaClipOfflineSize = remember(lidaClipOfflineFiles) {
		lidaClipOfflineFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
	}
	val platformContext = LocalPlatformContext.current
	val isAndroid = platformContext.name.lowercase().startsWith("android")
	val isApple = listOf("ios", "ipados").contains(platformContext.name.lowercase())

	val settings = stringResource(Res.string.title_settings)
	fun path(vararg parts: String): String = (listOf(settings) + parts).joinToString(" > ")

	val appearance = stringResource(Res.string.title_appearance)
	val nowPlaying = stringResource(Res.string.title_now_playing)
	val bottomBar = stringResource(Res.string.title_bottom_app_bar)
	val playback = stringResource(Res.string.title_playback)
	val ebooks = stringResource(Res.string.title_ebook_reader)
	val dataStorage = stringResource(Res.string.title_data_storage)
	val integrations = stringResource(Res.string.title_integrations)
	val developer = stringResource(Res.string.title_developer)
	val layout = stringResource(Res.string.title_layout)
	val library = stringResource(Res.string.title_library)
	val actions = stringResource(Res.string.title_actions)
	val behaviour = stringResource(Res.string.title_behaviour)
	val lyrics = stringResource(Res.string.action_lyrics)
	val network = stringResource(Res.string.title_network)
	val lidaClips = stringResource(Res.string.title_lida_clips)
	val lastFm = stringResource(Res.string.title_lastfm)
	val bindery = stringResource(Res.string.title_bindery)
	val aurral = stringResource(Res.string.title_aurral)
	val cacheManagement = stringResource(Res.string.title_cache_management)
	val miniPlayer = stringResource(Res.string.title_mini_player)
	val navigationBar = stringResource(Res.string.title_navigation_bar)
	val streamingQuality = stringResource(Res.string.title_streaming_quality)
	val readerSettings = preferenceManager.readerDefaultSettings()
	val readerLineHeightPercent = (((readerSettings.lineHeight ?: 1.55) * 100.0).roundToInt())

	return buildList {
		add(selectionRow(
			id = "appearance.font",
			path = path(appearance),
			title = stringResource(Res.string.title_choose_font),
			keywords = listOf("typeface"),
			items = FontOption.entries,
			label = { it.displayName },
			selection = preferenceManager.font,
			onSelect = { preferenceManager.font = it }
		))
		add(selectionRow(
			id = "appearance.theme",
			path = path(appearance),
			title = stringResource(Res.string.option_choose_theme),
			items = Theme.entries,
			label = { stringResource(it.title) },
			selection = preferenceManager.theme,
			onSelect = { preferenceManager.theme = it }
		))
		add(selectionRow(
			id = "appearance.artwork-shape",
			path = path(appearance, layout),
			title = stringResource(Res.string.option_artwork_shape),
			items = CoverArtShape.entries,
			label = { it.name },
			selection = preferenceManager.coverArtShape,
			onSelect = { preferenceManager.coverArtShape = it }
		))
		add(selectionRow(
			id = "appearance.grid-size",
			path = path(appearance, layout),
			title = stringResource(Res.string.option_grid_items_per_row),
			keywords = listOf(stringResource(Res.string.option_cover_art_size)),
			items = GridSize.entries,
			label = { it.label },
			selection = preferenceManager.gridSize,
			onSelect = { preferenceManager.gridSize = it }
		))
		add(sliderRow(
			id = "appearance.cover-art-size",
			path = path(appearance, layout),
			title = stringResource(Res.string.option_cover_art_size),
			valueText = preferenceManager.artGridItemSize.roundToInt().toString(),
			value = preferenceManager.artGridItemSize,
			onValueChange = { preferenceManager.artGridItemSize = it },
			valueRange = 50f..500f,
			steps = 8
		))
		add(switchRow(
			id = "appearance.quick-picks",
			path = path(appearance, library),
			title = stringResource(Res.string.option_show_quick_picks),
			subtitle = stringResource(Res.string.subtitle_show_quick_picks),
			keywords = listOf("discover", "home", "library"),
			value = preferenceManager.quickPicksEnabled,
			onSetValue = { preferenceManager.quickPicksEnabled = it }
		))
		add(selectionRow(
			id = "appearance.quick-picks-size",
			path = path(appearance, library),
			title = stringResource(Res.string.option_quick_picks_size),
			subtitle = stringResource(Res.string.subtitle_quick_picks_size),
			keywords = listOf("discover", "home", "library", "count", "limit"),
			items = quickPicksLimitSearchOptions,
			label = { it.toString() },
			selection = preferenceManager.quickPicksLimit,
			onSelect = { preferenceManager.quickPicksLimit = it }
		))
		add(selectionRow(
			id = "appearance.quick-picks-min-duration",
			path = path(appearance, library),
			title = stringResource(Res.string.option_quick_picks_min_duration),
			subtitle = stringResource(Res.string.subtitle_quick_picks_min_duration),
			keywords = listOf("discover", "home", "library", "duration", "short tracks", "intro"),
			items = quickPicksMinDurationSearchOptions,
			label = { quickPicksMinDurationLabel(it) },
			selection = preferenceManager.quickPicksMinDurationSeconds,
			onSelect = { preferenceManager.quickPicksMinDurationSeconds = it }
		))
		add(selectionRow(
			id = "appearance.marquee",
			path = path(appearance),
			title = stringResource(Res.string.option_use_marquee_text),
			items = MarqueeSpeed.entries,
			label = { it.name },
			selection = preferenceManager.marqueeSpeed,
			onSelect = { preferenceManager.marqueeSpeed = it }
		))
		add(switchRow(
			id = "appearance.alphabetical-scroll",
			path = path(appearance),
			title = stringResource(Res.string.option_alphabetical_scroll),
			value = preferenceManager.alphabeticalScroll,
			onSetValue = { preferenceManager.alphabeticalScroll = it }
		))
		add(selectionRow(
			id = "appearance.animation-style",
			path = path(appearance),
			title = stringResource(Res.string.option_animation_style),
			items = AnimationStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.animationStyle,
			onSelect = { preferenceManager.animationStyle = it }
		))

		add(switchRow(
			id = "now-playing.swipe-to-skip",
			path = path(nowPlaying),
			title = stringResource(Res.string.option_swipe_to_skip),
			value = preferenceManager.swipeToSkip,
			onSetValue = { preferenceManager.swipeToSkip = it }
		))
		add(selectionRow(
			id = "now-playing.background-style",
			path = path(nowPlaying),
			title = stringResource(Res.string.option_now_playing_background_style),
			subtitle = stringResource(Res.string.subtitle_now_playing_background_style),
			items = NowPlayingBackgroundStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingBackgroundStyle,
			onSelect = { preferenceManager.nowPlayingBackgroundStyle = it }
		))
		add(sliderRow(
			id = "now-playing.background-blur",
			path = path(nowPlaying),
			title = stringResource(Res.string.option_now_playing_background_blur),
			subtitle = stringResource(Res.string.subtitle_now_playing_background_blur),
			valueText = "${nowPlayingBackgroundBlurDp(preferenceManager.nowPlayingBackgroundBlurDp).roundToInt()}dp",
			value = nowPlayingBackgroundBlurDp(preferenceManager.nowPlayingBackgroundBlurDp),
			onValueChange = { preferenceManager.nowPlayingBackgroundBlurDp = it.roundToInt().toFloat() },
			valueRange = MinNowPlayingBackgroundBlurDp..MaxNowPlayingBackgroundBlurDp
		))
		add(sliderRow(
			id = "now-playing.background-dim",
			path = path(nowPlaying),
			title = stringResource(Res.string.option_now_playing_background_dim),
			subtitle = stringResource(Res.string.subtitle_now_playing_background_dim),
			valueText = "${preferenceManager.nowPlayingBackgroundDimPercent}%",
			value = preferenceManager.nowPlayingBackgroundDimPercent.toFloat(),
			onValueChange = { preferenceManager.nowPlayingBackgroundDimPercent = it.roundToInt() },
			valueRange = MinNowPlayingBackgroundDimPercent.toFloat()..MaxNowPlayingBackgroundDimPercent.toFloat()
		))
		add(switchRow(
			id = "now-playing.bottom-gradient",
			path = path(nowPlaying),
			title = stringResource(Res.string.option_now_playing_background_bottom_gradient),
			subtitle = stringResource(Res.string.subtitle_now_playing_background_bottom_gradient),
			value = preferenceManager.nowPlayingBackgroundBottomGradient,
			onSetValue = { preferenceManager.nowPlayingBackgroundBottomGradient = it }
		))
		add(selectionRow(
			id = "now-playing.slider-style",
			path = path(nowPlaying),
			title = stringResource(Res.string.option_now_playing_slider_style),
			items = NowPlayingSliderStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingSliderStyle,
			onSelect = { preferenceManager.nowPlayingSliderStyle = it }
		))
		add(selectionRow(
			id = "now-playing.progress-width",
			path = path(nowPlaying),
			title = stringResource(Res.string.option_now_playing_progress_width),
			subtitle = stringResource(Res.string.subtitle_now_playing_progress_width),
			items = NowPlayingProgressWidth.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingProgressWidth,
			onSelect = { preferenceManager.nowPlayingProgressWidth = it }
		))
		add(switchRow(
			id = "now-playing.show-artwork",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_artwork),
			subtitle = stringResource(Res.string.subtitle_now_playing_artwork),
			value = preferenceManager.showNowPlayingArtwork,
			onSetValue = { preferenceManager.showNowPlayingArtwork = it }
		))
		add(selectionRow(
			id = "now-playing.artwork-size",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_artwork_size),
			items = NowPlayingArtworkSize.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingArtworkSize,
			onSelect = { preferenceManager.nowPlayingArtworkSize = it }
		))
		add(selectionRow(
			id = "now-playing.artwork-tap",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_artwork_tap_action),
			subtitle = stringResource(Res.string.subtitle_now_playing_artwork_tap_action),
			items = NowPlayingArtworkTapAction.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingArtworkTapAction,
			onSelect = {
				preferenceManager.nowPlayingArtworkTapAction = it
				preferenceManager.tapArtworkForLyrics = it == NowPlayingArtworkTapAction.Lyrics
			}
		))
		add(switchRow(
			id = "now-playing.artwork-swipe",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_artwork_swipe),
			subtitle = stringResource(Res.string.subtitle_now_playing_artwork_swipe),
			value = preferenceManager.nowPlayingArtworkSwipeToSkip,
			onSetValue = { preferenceManager.nowPlayingArtworkSwipeToSkip = it }
		))
		add(switchRow(
			id = "now-playing.rotating-artwork",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_rotating_artwork),
			subtitle = stringResource(Res.string.subtitle_now_playing_rotating_artwork),
			keywords = listOf("spin", "cover", "disc"),
			value = preferenceManager.nowPlayingRotatingArtwork,
			onSetValue = { preferenceManager.nowPlayingRotatingArtwork = it }
		))
		add(switchRow(
			id = "now-playing.shrink-artwork",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_shrink_artwork_on_pause),
			subtitle = stringResource(Res.string.subtitle_now_playing_shrink_artwork_on_pause),
			value = preferenceManager.shrinkNowPlayingArtworkOnPause,
			onSetValue = { preferenceManager.shrinkNowPlayingArtworkOnPause = it }
		))
		add(selectionRow(
			id = "now-playing.info-style",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_info_style),
			subtitle = stringResource(Res.string.subtitle_now_playing_info_style),
			items = NowPlayingInfoStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingInfoStyle,
			onSelect = { preferenceManager.nowPlayingInfoStyle = it }
		))
		add(switchRow(
			id = "now-playing.info-icons",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_info_icons),
			subtitle = stringResource(Res.string.subtitle_now_playing_info_icons),
			value = preferenceManager.showNowPlayingInfoIcons,
			onSetValue = { preferenceManager.showNowPlayingInfoIcons = it }
		))
		add(switchRow(
			id = "now-playing.song-info",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_song_info),
			value = preferenceManager.nowPlayingSongInfo,
			onSetValue = { preferenceManager.nowPlayingSongInfo = it }
		))
		add(selectionRow(
			id = "now-playing.technical-info-style",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_technical_info_style),
			subtitle = stringResource(Res.string.subtitle_now_playing_technical_info_style),
			items = NowPlayingTechnicalInfoStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingTechnicalInfoStyle,
			onSelect = { preferenceManager.nowPlayingTechnicalInfoStyle = it }
		))
		add(switchRow(
			id = "now-playing.seek-buttons",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_seek_buttons),
			subtitle = stringResource(Res.string.subtitle_now_playing_seek_buttons),
			value = preferenceManager.showNowPlayingSeekButtons,
			onSetValue = { preferenceManager.showNowPlayingSeekButtons = it }
		))
		add(switchRow(
			id = "now-playing.remaining-time",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_remaining_time),
			subtitle = stringResource(Res.string.subtitle_now_playing_remaining_time),
			value = preferenceManager.showNowPlayingRemainingTime,
			onSetValue = { preferenceManager.showNowPlayingRemainingTime = it }
		))
		add(switchRow(
			id = "now-playing.swap-controls",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_swap_controls_and_timeline),
			subtitle = stringResource(Res.string.subtitle_now_playing_swap_controls_and_timeline),
			value = preferenceManager.swapNowPlayingControlsAndTimeline,
			onSetValue = { preferenceManager.swapNowPlayingControlsAndTimeline = it }
		))
		add(switchRow(
			id = "now-playing.space-controls",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_space_playback_controls_evenly),
			subtitle = stringResource(Res.string.subtitle_now_playing_space_playback_controls_evenly),
			value = preferenceManager.spaceNowPlayingPlaybackControlsEvenly,
			onSetValue = { preferenceManager.spaceNowPlayingPlaybackControlsEvenly = it }
		))
		add(switchRow(
			id = "now-playing.controls-swipe-queue",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_swipe_up_controls_for_queue),
			subtitle = stringResource(Res.string.subtitle_now_playing_swipe_up_controls_for_queue),
			value = preferenceManager.openQueueOnNowPlayingControlsSwipeUp,
			onSetValue = { preferenceManager.openQueueOnNowPlayingControlsSwipeUp = it }
		))
		add(switchRow(
			id = "now-playing.controls-tap-queue",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_tap_controls_for_queue),
			subtitle = stringResource(Res.string.subtitle_now_playing_tap_controls_for_queue),
			value = preferenceManager.openQueueOnNowPlayingControlsTap,
			onSetValue = { preferenceManager.openQueueOnNowPlayingControlsTap = it }
		))
		add(switchRow(
			id = "now-playing.shuffle-control",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_shuffle_control),
			subtitle = stringResource(Res.string.subtitle_now_playing_shuffle_control),
			value = preferenceManager.showNowPlayingShuffleControl,
			onSetValue = { preferenceManager.showNowPlayingShuffleControl = it }
		))
		add(switchRow(
			id = "now-playing.repeat-control",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_repeat_control),
			subtitle = stringResource(Res.string.subtitle_now_playing_repeat_control),
			value = preferenceManager.showNowPlayingRepeatControl,
			onSetValue = { preferenceManager.showNowPlayingRepeatControl = it }
		))
		add(switchRow(
			id = "now-playing.up-next",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_up_next),
			subtitle = stringResource(Res.string.subtitle_now_playing_up_next),
			value = preferenceManager.showNowPlayingUpNext,
			onSetValue = { preferenceManager.showNowPlayingUpNext = it }
		))
		add(switchRow(
			id = "now-playing.up-next-artwork",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_up_next_artwork),
			subtitle = stringResource(Res.string.subtitle_now_playing_up_next_artwork),
			value = preferenceManager.showNowPlayingUpNextArtwork,
			onSetValue = { preferenceManager.showNowPlayingUpNextArtwork = it }
		))
		add(selectionRow(
			id = "now-playing.up-next-count",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_up_next_count),
			subtitle = stringResource(Res.string.subtitle_now_playing_up_next_count),
			items = nowPlayingUpNextCountSearchOptions,
			label = { if (it == 1) "1 song" else "$it songs" },
			selection = preferenceManager.nowPlayingUpNextCount,
			onSelect = { preferenceManager.nowPlayingUpNextCount = it }
		))
		add(selectionRow(
			id = "now-playing.toolbar-position",
			path = path(nowPlaying, layout),
			title = stringResource(Res.string.option_now_playing_toolbar_position),
			subtitle = stringResource(Res.string.subtitle_now_playing_toolbar_position),
			items = ToolbarPosition.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.nowPlayingToolbarPosition,
			onSelect = { preferenceManager.nowPlayingToolbarPosition = it }
		))
		listOf(
			"lyrics" to Triple(
				stringResource(Res.string.option_now_playing_lyrics_action),
				preferenceManager.showNowPlayingLyricsAction,
				{ value: Boolean -> preferenceManager.showNowPlayingLyricsAction = value }
			),
			"queue" to Triple(
				stringResource(Res.string.option_now_playing_queue_action),
				preferenceManager.showNowPlayingQueueAction,
				{ value: Boolean -> preferenceManager.showNowPlayingQueueAction = value }
			),
			"video" to Triple(
				stringResource(Res.string.option_now_playing_music_video_action),
				preferenceManager.showNowPlayingMusicVideoAction,
				{ value: Boolean -> preferenceManager.showNowPlayingMusicVideoAction = value }
			),
			"speed" to Triple(
				stringResource(Res.string.option_now_playing_playback_speed_action),
				preferenceManager.showNowPlayingPlaybackSpeedAction,
				{ value: Boolean -> preferenceManager.showNowPlayingPlaybackSpeedAction = value }
			),
			"sleep" to Triple(
				stringResource(Res.string.option_now_playing_sleep_timer_action),
				preferenceManager.showNowPlayingSleepTimerAction,
				{ value: Boolean -> preferenceManager.showNowPlayingSleepTimerAction = value }
			),
			"radio" to Triple(
				stringResource(Res.string.option_now_playing_start_radio_action),
				preferenceManager.showNowPlayingStartRadioAction,
				{ value: Boolean -> preferenceManager.showNowPlayingStartRadioAction = value }
			),
			"discover" to Triple(
				stringResource(Res.string.option_now_playing_discover_queue_action),
				preferenceManager.showNowPlayingDiscoverQueueAction,
				{ value: Boolean -> preferenceManager.showNowPlayingDiscoverQueueAction = value }
			),
			"download" to Triple(
				stringResource(Res.string.option_now_playing_download_action),
				preferenceManager.showNowPlayingDownloadAction,
				{ value: Boolean -> preferenceManager.showNowPlayingDownloadAction = value }
			),
			"playlist" to Triple(
				stringResource(Res.string.option_now_playing_add_to_playlist_action),
				preferenceManager.showNowPlayingAddToPlaylistAction,
				{ value: Boolean -> preferenceManager.showNowPlayingAddToPlaylistAction = value }
			),
			"more" to Triple(
				stringResource(Res.string.option_now_playing_more_action),
				preferenceManager.showNowPlayingMoreAction,
				{ value: Boolean -> preferenceManager.showNowPlayingMoreAction = value }
			)
		).forEach { (id, data) ->
			add(switchRow(
				id = "now-playing.action-$id",
				path = path(nowPlaying, actions),
				title = data.first,
				value = data.second,
				onSetValue = data.third
			))
		}
		if (isAndroid) {
			add(switchRow(
				id = "now-playing.action-equalizer",
				path = path(nowPlaying, actions),
				title = stringResource(Res.string.option_now_playing_equalizer_action),
				value = preferenceManager.showNowPlayingEqualizerAction,
				onSetValue = { preferenceManager.showNowPlayingEqualizerAction = it }
			))
		}

		add(selectionRow(
			id = "bottom.collapse",
			path = path(bottomBar),
			title = stringResource(Res.string.option_bottom_bar_collapse_mode),
			items = BottomBarCollapseMode.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.bottomBarCollapseMode,
			onSelect = { preferenceManager.bottomBarCollapseMode = it }
		))
		add(selectionRow(
			id = "bottom.visibility",
			path = path(bottomBar),
			title = stringResource(Res.string.option_bottom_bar_visibility_mode),
			items = BottomBarVisibilityMode.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.bottomBarVisibilityMode,
			onSelect = { preferenceManager.bottomBarVisibilityMode = it }
		))
		add(selectionRow(
			id = "bottom.navigation-style",
			path = path(bottomBar, navigationBar),
			title = stringResource(Res.string.option_navigation_bar_style),
			items = NavigationBarStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.navigationBarStyle,
			onSelect = { preferenceManager.navigationBarStyle = it }
		))
		add(selectionRow(
			id = "bottom.navigation-labels",
			path = path(bottomBar, navigationBar),
			title = stringResource(Res.string.option_navigation_bar_label_visibility),
			items = NavigationBarLabelVisibility.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.navigationBarLabelVisibility,
			onSelect = { preferenceManager.navigationBarLabelVisibility = it }
		))
		add(selectionRow(
			id = "bottom.mini-player-style",
			path = path(bottomBar, miniPlayer),
			title = stringResource(Res.string.option_mini_player_style),
			items = MiniPlayerStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.miniPlayerStyle,
			onSelect = { preferenceManager.miniPlayerStyle = it }
		))
		add(selectionRow(
			id = "bottom.mini-player-progress",
			path = path(bottomBar, miniPlayer),
			title = stringResource(Res.string.option_mini_player_progress_style),
			items = MiniPlayerProgressStyle.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.miniPlayerProgressStyle,
			onSelect = { preferenceManager.miniPlayerProgressStyle = it }
		))
		add(switchRow(
			id = "bottom.mini-player-queue",
			path = path(bottomBar, miniPlayer),
			title = stringResource(Res.string.option_mini_player_queue_action),
			subtitle = stringResource(Res.string.subtitle_mini_player_queue_action),
			value = preferenceManager.showMiniPlayerQueueAction,
			onSetValue = { preferenceManager.showMiniPlayerQueueAction = it }
		))

		add(selectionRow(
			id = "streaming.wifi",
			path = path(playback, streamingQuality),
			title = stringResource(Res.string.title_wifi),
			items = StreamingQuality.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.streamingQualityWifi,
			onSelect = { preferenceManager.streamingQualityWifi = it }
		))
		add(selectionRow(
			id = "streaming.cellular",
			path = path(playback, streamingQuality),
			title = stringResource(Res.string.title_cellular),
			items = StreamingQuality.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.streamingQualityCellular,
			onSelect = { preferenceManager.streamingQualityCellular = it }
		))
		add(switchRow(
			id = "streaming.custom-bitrates",
			path = path(playback, streamingQuality),
			title = stringResource(Res.string.option_enable_custom_bitrates),
			subtitle = stringResource(Res.string.subtitle_max_bitrates),
			value = preferenceManager.isAdvancedTranscodingActive,
			onSetValue = { preferenceManager.isAdvancedTranscodingActive = it }
		))
		add(textFieldRow(
			id = "streaming.max-bitrate-wifi",
			path = path(playback, streamingQuality),
			title = stringResource(Res.string.option_max_bitrate_wifi),
			value = preferenceManager.customMaxBitrateWifi.takeIf { it > 0 }?.toString().orEmpty(),
			keyboardType = KeyboardType.NumberPassword,
			digitsOnly = true,
			onValueChange = { preferenceManager.customMaxBitrateWifi = it.toIntOrNull() ?: 0 }
		))
		add(textFieldRow(
			id = "streaming.max-bitrate-cellular",
			path = path(playback, streamingQuality),
			title = stringResource(Res.string.option_max_bitrate_cellular),
			value = preferenceManager.customMaxBitrateCellular.takeIf { it > 0 }?.toString().orEmpty(),
			keyboardType = KeyboardType.NumberPassword,
			digitsOnly = true,
			onValueChange = { preferenceManager.customMaxBitrateCellular = it.toIntOrNull() ?: 0 }
		))

		add(selectionRow(
			id = "ebooks.font-family",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_font_family),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_font_family),
			keywords = listOf("reader", "ebook", "EPUB", "typeface"),
			items = readerFontFamilySearchOptions,
			label = { fontFamily -> readerFontFamilySearchLabel(fontFamily) },
			selection = readerSettings.fontFamily ?: ReaderSansFontFamily,
			onSelect = { fontFamily -> preferenceManager.readerFontFamily = fontFamily }
		))
		add(selectionRow(
			id = "ebooks.font-source",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_font_source),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_font_source),
			keywords = listOf("reader", "ebook", "EPUB", "typeface", "publisher", "book fonts"),
			items = readerFontSourceSearchOptions,
			label = { fontSource -> readerFontSourceSearchLabel(fontSource) },
			selection = readerSettings.fontSource ?: ReaderFontSourceNavic,
			onSelect = { fontSource -> preferenceManager.readerFontSource = fontSource }
		))
		add(selectionRow(
			id = "ebooks.font-size",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_font_size),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_font_size),
			keywords = listOf("reader", "ebook", "EPUB", "text"),
			items = readerFontSizeSearchOptions,
			label = { percent -> "$percent%" },
			selection = readerSettings.fontSizePercent ?: 100,
			onSelect = { percent -> preferenceManager.readerFontSizePercent = percent }
		))
		add(selectionRow(
			id = "ebooks.line-height",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_line_height),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_line_height),
			keywords = listOf("reader", "ebook", "EPUB", "spacing"),
			items = readerLineHeightSearchOptions,
			label = { percent -> readerLineHeightSearchLabel(percent) },
			selection = readerLineHeightPercent,
			onSelect = { percent -> preferenceManager.readerLineHeightPercent = percent }
		))
		add(selectionRow(
			id = "ebooks.paragraph-spacing",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_paragraph_spacing),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_paragraph_spacing),
			keywords = listOf("reader", "ebook", "EPUB", "paragraph", "spacing"),
			items = readerParagraphSpacingSearchOptions,
			label = { percent -> "$percent%" },
			selection = readerSettings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent,
			onSelect = { percent -> preferenceManager.readerParagraphSpacingPercent = percent }
		))
		add(selectionRow(
			id = "ebooks.margin",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_margin),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_margin),
			keywords = listOf("reader", "ebook", "EPUB", "layout"),
			items = readerMarginSearchOptions,
			label = { percent -> "$percent%" },
			selection = readerSettings.marginPercent ?: 0,
			onSelect = { percent -> preferenceManager.readerMarginPercent = percent }
		))
		add(selectionRow(
			id = "ebooks.dim-overlay",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_dim_overlay),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_dim_overlay),
			keywords = listOf("reader", "ebook", "EPUB", "brightness", "dim", "Komikku"),
			items = readerDimOverlaySearchOptions,
			label = { percent -> readerDimOverlaySearchLabel(percent) },
			selection = readerSettings.dimOverlayPercent ?: 0,
			onSelect = { percent -> preferenceManager.readerDimOverlayPercent = percent }
		))
		add(selectionRow(
			id = "ebooks.theme",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_theme),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_theme),
			keywords = listOf("reader", "ebook", "EPUB", "dark", "light"),
			items = readerThemeSearchOptions,
			label = { theme -> readerThemeSearchLabel(theme) },
			selection = readerSettings.theme ?: ReaderLightTheme,
			onSelect = { theme -> preferenceManager.readerTheme = theme }
		))
		add(selectionRow(
			id = "ebooks.orientation",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_orientation),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_orientation),
			keywords = listOf("reader", "ebook", "EPUB", "PDF", "rotation", "orientation", "Komikku"),
			items = readerOrientationSearchOptions,
			label = { orientation -> readerOrientationSearchLabel(orientation) },
			selection = readerSettings.orientation ?: ReaderOrientationDefault,
			onSelect = { orientation -> preferenceManager.readerOrientation = orientation }
		))
		add(switchRow(
			id = "ebooks.fullscreen",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_fullscreen),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_fullscreen),
			keywords = listOf("reader", "ebook", "EPUB", "PDF", "fullscreen", "immersive", "Komikku", "system bars"),
			value = preferenceManager.readerFullscreen,
			onSetValue = { enabled -> preferenceManager.readerFullscreen = enabled }
		))
		add(selectionRow(
			id = "ebooks.direction",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_direction),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_direction),
			keywords = listOf("reader", "ebook", "EPUB", "direction", "RTL", "LTR", "manga", "Komikku"),
			items = readerDirectionSearchOptions,
			label = { direction -> readerDirectionSearchLabel(direction) },
			selection = readerSettings.direction ?: ReaderDirectionDefault,
			onSelect = { direction -> preferenceManager.readerDirection = direction }
		))
		add(selectionRow(
			id = "ebooks.flow",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_flow),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_paged),
			keywords = listOf("reader", "ebook", "EPUB", "paged", "vertical", "scroll", "gaps"),
			items = readerFlowSearchOptions,
			label = { flowMode ->
				readerFlowSearchLabel(flowMode)
			},
			selection = readerSettings.flowMode ?: ReaderFlowPaged,
			onSelect = { flowMode ->
				preferenceManager.readerFlowMode = flowMode
				preferenceManager.readerPaged = flowMode != ReaderFlowScrolled &&
					flowMode != ReaderFlowScrolledGaps
			}
		))
		add(selectionRow(
			id = "ebooks.tap-zone",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_tap_zone),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_tap_zone),
			keywords = listOf("reader", "ebook", "EPUB", "tap", "gesture", "Komikku", "page turn"),
			items = readerTapZoneSearchOptions,
			label = { tapZone -> readerTapZoneSearchLabel(tapZone) },
			selection = readerSettings.tapZone ?: ReaderTapZoneDefault,
			onSelect = { tapZone -> preferenceManager.readerTapZone = tapZone }
		))
		add(switchRow(
			id = "ebooks.smaller-tap-zones",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_smaller_tap_zones),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_smaller_tap_zones),
			keywords = listOf("reader", "ebook", "EPUB", "tap", "gesture", "Komikku", "smaller", "zones"),
			value = preferenceManager.readerSmallerTapZone,
			onSetValue = { enabled -> preferenceManager.readerSmallerTapZone = enabled }
		))
		add(switchRow(
			id = "ebooks.show-tap-zones",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_show_tap_zones),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_show_tap_zones),
			keywords = listOf("reader", "ebook", "EPUB", "tap", "gesture", "Komikku", "debug", "zones", "visible"),
			value = preferenceManager.readerShowTapZones,
			onSetValue = { enabled -> preferenceManager.readerShowTapZones = enabled }
		))
		add(switchRow(
			id = "ebooks.publisher-styles",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_publisher_styles),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_publisher_styles),
			keywords = listOf("reader", "ebook", "EPUB", "publisher", "CSS", "style"),
			value = preferenceManager.readerPublisherStylesEnabled,
			onSetValue = { enabled -> preferenceManager.readerPublisherStylesEnabled = enabled }
		))
		add(switchRow(
			id = "ebooks.keep-screen-on",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_keep_screen_on),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_keep_screen_on),
			keywords = listOf("reader", "ebook", "EPUB", "screen", "awake", "battery"),
			value = preferenceManager.readerKeepScreenOn,
			onSetValue = { enabled -> preferenceManager.readerKeepScreenOn = enabled }
		))
		add(switchRow(
			id = "ebooks.volume-keys",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_volume_keys),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_volume_keys),
			keywords = listOf("reader", "ebook", "EPUB", "volume", "keys", "page turn", "Librera", "Komikku"),
			value = preferenceManager.readerVolumeKeyPageTurns,
			onSetValue = { enabled -> preferenceManager.readerVolumeKeyPageTurns = enabled }
		))
		add(switchRow(
			id = "ebooks.media-overlay",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_media_overlay),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_media_overlay),
			keywords = listOf("reader", "ebook", "Storyteller", "readaloud", "media overlay", "audio labels"),
			value = preferenceManager.readerMediaOverlayEnabled,
			onSetValue = { enabled -> preferenceManager.readerMediaOverlayEnabled = enabled }
		))
		if (isAndroid) {
			add(switchRow(
				id = "ebooks.web-debugging",
				path = path(ebooks),
				title = stringResource(Res.string.option_ebook_reader_web_debugging),
				subtitle = stringResource(Res.string.subtitle_ebook_reader_web_debugging),
				keywords = listOf("reader", "ebook", "EPUB", "WebView", "DevTools", "debugging"),
				value = preferenceManager.readerWebContentsDebuggingEnabled,
				onSetValue = { enabled -> preferenceManager.readerWebContentsDebuggingEnabled = enabled }
			))
		}

		if (!isApple) {
			add(selectionRow(
				id = "playback.replay-gain",
				path = path(playback),
				title = stringResource(Res.string.option_replay_gain),
				items = ReplayGainMode.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.replayGainMode,
				onSelect = {
					preferenceManager.replayGainMode = it
					player.refreshAudioEffects()
				}
			))
			add(switchRow(
				id = "playback.replay-gain-boost",
				path = path(playback),
				title = stringResource(Res.string.option_replay_gain_loudness_boost),
				subtitle = stringResource(Res.string.subtitle_replay_gain_loudness_boost),
				value = preferenceManager.replayGainLoudnessBoost,
				onSetValue = {
					preferenceManager.replayGainLoudnessBoost = it
					player.refreshAudioEffects()
				}
			))
			add(switchRow(
				id = "playback.gapless",
				path = path(playback),
				title = stringResource(Res.string.option_gapless_playback),
				subtitle = stringResource(Res.string.subtitle_gapless_playback),
				value = preferenceManager.gaplessPlayback,
				onSetValue = { preferenceManager.gaplessPlayback = it }
			))
			add(switchRow(
				id = "playback.audio-focus",
				path = path(playback),
				title = stringResource(Res.string.option_respect_audio_focus),
				subtitle = stringResource(Res.string.subtitle_respect_audio_focus),
				keywords = listOf("WhatsApp", "focus", "training"),
				value = preferenceManager.respectAudioFocus,
				onSetValue = { preferenceManager.respectAudioFocus = it }
			))
			add(sliderRow(
				id = "playback.volume",
				path = path(playback),
				title = stringResource(Res.string.option_playback_volume),
				subtitle = stringResource(Res.string.subtitle_playback_volume),
				valueText = "${preferenceManager.playbackVolumePercent}%",
				value = preferenceManager.playbackVolumePercent.toFloat(),
				onValueChange = {
					preferenceManager.playbackVolumePercent = it.roundToInt()
					player.refreshPlaybackVolume()
				},
				valueRange = 0f..100f
			))
			add(switchRow(
				id = "playback.skip-silence",
				path = path(playback),
				title = stringResource(Res.string.option_skip_silence),
				subtitle = stringResource(Res.string.subtitle_skip_silence),
				value = preferenceManager.skipSilence,
				onSetValue = { preferenceManager.skipSilence = it }
			))
			add(switchRow(
				id = "playback.skip-media-on-error",
				path = path(playback),
				title = stringResource(Res.string.option_skip_media_on_error),
				subtitle = stringResource(Res.string.subtitle_skip_media_on_error),
				value = preferenceManager.skipMediaOnError,
				onSetValue = { preferenceManager.skipMediaOnError = it }
			))
			add(switchRow(
				id = "playback.resume-device-connect",
				path = path(playback),
				title = stringResource(Res.string.option_resume_playback_on_audio_device_connect),
				subtitle = stringResource(Res.string.subtitle_resume_playback_on_audio_device_connect),
				value = preferenceManager.resumePlaybackOnAudioDeviceConnect,
				onSetValue = { preferenceManager.resumePlaybackOnAudioDeviceConnect = it }
			))
			add(selectionRow(
				id = "playback.pause-between-songs",
				path = path(playback),
				title = stringResource(Res.string.option_pause_between_songs),
				subtitle = stringResource(Res.string.subtitle_pause_between_songs),
				items = pauseBetweenSongsSearchOptions,
				label = { if (it == 0) stringResource(Res.string.option_off) else "${it}s" },
				selection = preferenceManager.pauseBetweenSongsSeconds,
				onSelect = { preferenceManager.pauseBetweenSongsSeconds = it }
			))
			add(selectionRow(
				id = "playback.medley",
				path = path(playback),
				title = stringResource(Res.string.option_medley_mode),
				subtitle = stringResource(Res.string.subtitle_medley_mode),
				items = medleyModeSearchOptions,
				label = { if (it == 0) stringResource(Res.string.option_off) else "${it}s" },
				selection = preferenceManager.medleyModeSeconds,
				onSelect = { preferenceManager.medleyModeSeconds = it }
			))
			add(selectionRow(
				id = "playback.smart-rewind",
				path = path(playback),
				title = stringResource(Res.string.option_smart_rewind),
				subtitle = stringResource(Res.string.subtitle_smart_rewind),
				items = smartRewindSearchOptions,
				label = { "${it}s" },
				selection = preferenceManager.smartRewindSeconds,
				onSelect = { preferenceManager.smartRewindSeconds = it }
			))
			add(switchRow(
				id = "playback.pause-volume-zero",
				path = path(playback),
				title = stringResource(Res.string.option_pause_playback_on_volume_zero),
				subtitle = stringResource(Res.string.subtitle_pause_playback_on_volume_zero),
				value = preferenceManager.pausePlaybackOnVolumeZero,
				onSetValue = { preferenceManager.pausePlaybackOnVolumeZero = it }
			))
			add(selectionRow(
				id = "playback.audio-fade",
				path = path(playback),
				title = stringResource(Res.string.option_audio_fade),
				subtitle = stringResource(Res.string.subtitle_audio_fade),
				items = audioFadeSearchOptions,
				label = { if (it == 0) stringResource(Res.string.option_off) else if (it < 1000) "${it}ms" else "${it / 1000}s" },
				selection = preferenceManager.audioFadeDurationMs,
				onSelect = { preferenceManager.audioFadeDurationMs = it }
			))
			add(switchRow(
				id = "playback.bass-boost",
				path = path(playback),
				title = stringResource(Res.string.option_bass_boost),
				subtitle = stringResource(Res.string.subtitle_bass_boost),
				value = preferenceManager.bassBoostEnabled,
				onSetValue = {
					preferenceManager.bassBoostEnabled = it
					player.refreshAudioEffects()
				}
			))
			add(sliderRow(
				id = "playback.bass-boost-strength",
				path = path(playback),
				title = stringResource(Res.string.option_bass_boost_strength),
				valueText = "${preferenceManager.bassBoostStrength / 10}%",
				value = preferenceManager.bassBoostStrength.toFloat(),
				onValueChange = {
					preferenceManager.bassBoostStrength = it.roundToInt()
					player.refreshAudioEffects()
				},
				valueRange = 0f..1000f
			))
			add(selectionRow(
				id = "playback.audio-reverb",
				path = path(playback),
				title = stringResource(Res.string.option_audio_reverb),
				subtitle = stringResource(Res.string.subtitle_audio_reverb),
				items = AudioReverbPreset.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.audioReverbPreset,
				onSelect = {
					preferenceManager.audioReverbPreset = it
					player.refreshAudioEffects()
				}
			))
			add(switchRow(
				id = "playback.auto-fill-queue",
				path = path(playback),
				title = stringResource(Res.string.option_auto_fill_queue),
				subtitle = stringResource(Res.string.subtitle_auto_fill_queue),
				value = preferenceManager.autoFillQueue,
				onSetValue = { preferenceManager.autoFillQueue = it }
			))
			add(selectionRow(
				id = "playback.auto-fill-queue-size",
				path = path(playback),
				title = stringResource(Res.string.option_auto_fill_queue_target_size),
				subtitle = stringResource(Res.string.subtitle_auto_fill_queue_target_size),
				items = autoFillQueueTargetSizeSearchOptions,
				label = { "$it songs" },
				selection = preferenceManager.autoFillQueueTargetSize,
				onSelect = { preferenceManager.autoFillQueueTargetSize = it }
			))
			add(selectionRow(
				id = "playback.auto-fill-queue-source",
				path = path(playback),
				title = stringResource(Res.string.option_auto_fill_queue_source),
				subtitle = stringResource(Res.string.subtitle_auto_fill_queue_source),
				items = AutoFillQueueSource.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.autoFillQueueSource,
				onSelect = { preferenceManager.autoFillQueueSource = it }
			))
			add(switchRow(
				id = "playback.now-playing-indicator",
				path = path(playback),
				title = stringResource(Res.string.option_now_playing_indicator),
				subtitle = stringResource(Res.string.subtitle_now_playing_indicator),
				value = preferenceManager.showNowPlayingIndicator,
				onSetValue = { preferenceManager.showNowPlayingIndicator = it }
			))
			add(switchRow(
				id = "playback.playlist-indicator",
				path = path(playback),
				title = stringResource(Res.string.option_playlist_indicator),
				subtitle = stringResource(Res.string.subtitle_playlist_indicator),
				value = preferenceManager.showPlaylistIndicator,
				onSetValue = { preferenceManager.showPlaylistIndicator = it }
			))
			add(switchRow(
				id = "playback.song-swipe-actions",
				path = path(playback),
				title = stringResource(Res.string.option_song_swipe_actions),
				subtitle = stringResource(Res.string.subtitle_song_swipe_actions),
				value = preferenceManager.songSwipeActionsEnabled,
				onSetValue = { preferenceManager.songSwipeActionsEnabled = it }
			))
			add(selectionRow(
				id = "playback.song-swipe-start",
				path = path(playback),
				title = stringResource(Res.string.option_song_swipe_start_to_end_action),
				subtitle = stringResource(Res.string.subtitle_song_swipe_start_to_end_action),
				items = SongSwipeAction.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.songSwipeStartToEndAction,
				onSelect = { preferenceManager.songSwipeStartToEndAction = it }
			))
			add(selectionRow(
				id = "playback.song-swipe-end",
				path = path(playback),
				title = stringResource(Res.string.option_song_swipe_end_to_start_action),
				subtitle = stringResource(Res.string.subtitle_song_swipe_end_to_start_action),
				items = SongSwipeAction.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.songSwipeEndToStartAction,
				onSelect = { preferenceManager.songSwipeEndToStartAction = it }
			))
			add(switchRow(
				id = "playback.queue-swipe-actions",
				path = path(playback),
				title = stringResource(Res.string.option_queue_swipe_actions),
				subtitle = stringResource(Res.string.subtitle_queue_swipe_actions),
				value = preferenceManager.queueSwipeActionsEnabled,
				onSetValue = { preferenceManager.queueSwipeActionsEnabled = it }
			))
			add(selectionRow(
				id = "playback.queue-swipe-start",
				path = path(playback),
				title = stringResource(Res.string.option_queue_swipe_start_to_end_action),
				subtitle = stringResource(Res.string.subtitle_queue_swipe_start_to_end_action),
				items = QueueSwipeAction.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.queueSwipeStartToEndAction,
				onSelect = { preferenceManager.queueSwipeStartToEndAction = it }
			))
			add(selectionRow(
				id = "playback.queue-swipe-end",
				path = path(playback),
				title = stringResource(Res.string.option_queue_swipe_end_to_start_action),
				subtitle = stringResource(Res.string.subtitle_queue_swipe_end_to_start_action),
				items = QueueSwipeAction.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.queueSwipeEndToStartAction,
				onSelect = { preferenceManager.queueSwipeEndToStartAction = it }
			))
			add(switchRow(
				id = "playback.shake-to-skip",
				path = path(playback),
				title = stringResource(Res.string.option_shake_to_skip),
				subtitle = stringResource(Res.string.subtitle_shake_to_skip),
				value = preferenceManager.shakeToSkip,
				onSetValue = { preferenceManager.shakeToSkip = it }
			))
			add(switchRow(
				id = "playback.volume-keys-skip",
				path = path(playback),
				title = stringResource(Res.string.option_volume_keys_skip_tracks),
				subtitle = stringResource(Res.string.subtitle_volume_keys_skip_tracks),
				value = preferenceManager.volumeKeysSkipTracks,
				onSetValue = { preferenceManager.volumeKeysSkipTracks = it }
			))
			add(switchRow(
				id = "playback.persistent-queue",
				path = path(playback),
				title = stringResource(Res.string.option_persistent_queue),
				subtitle = stringResource(Res.string.subtitle_persistent_queue),
				value = preferenceManager.persistentQueue,
				onSetValue = {
					preferenceManager.persistentQueue = it
					if (!it) preferenceManager.resumePlaybackOnStartup = false
				}
			))
			add(switchRow(
				id = "playback.resume-startup",
				path = path(playback),
				title = stringResource(Res.string.option_resume_playback_on_startup),
				subtitle = stringResource(Res.string.subtitle_resume_playback_on_startup),
				value = preferenceManager.resumePlaybackOnStartup,
				onSetValue = { preferenceManager.resumePlaybackOnStartup = it }
			))
			add(switchRow(
				id = "playback.audio-offload",
				path = path(playback),
				title = stringResource(Res.string.option_audio_offload),
				subtitle = stringResource(Res.string.subtitle_audio_offload),
				value = preferenceManager.audioOffload,
				onSetValue = { preferenceManager.audioOffload = it }
			))
		}

		add(switchRow(
			id = "lyrics.autoscroll",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_autoscroll),
			value = preferenceManager.lyricsAutoscroll,
			onSetValue = { preferenceManager.lyricsAutoscroll = it }
		))
		add(switchRow(
			id = "lyrics.beat-by-beat",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_beat_by_beat),
			value = preferenceManager.lyricsBeatByBeat,
			onSetValue = { preferenceManager.lyricsBeatByBeat = it }
		))
		add(switchRow(
			id = "lyrics.keep-alive",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_keep_alive),
			value = preferenceManager.lyricsKeepAlive,
			onSetValue = { preferenceManager.lyricsKeepAlive = it }
		))
		add(switchRow(
			id = "lyrics.blur",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_blur),
			value = preferenceManager.lyricsBlur,
			onSetValue = { preferenceManager.lyricsBlur = it }
		))
		add(switchRow(
			id = "lyrics.bright-inactive",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_bright_inactive),
			value = preferenceManager.lyricsBrightInactive,
			onSetValue = { preferenceManager.lyricsBrightInactive = it }
		))
		add(switchRow(
			id = "lyrics.accent-background",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_accent_background),
			value = preferenceManager.lyricsAccentBackground,
			onSetValue = { preferenceManager.lyricsAccentBackground = it }
		))
		add(switchRow(
			id = "lyrics.show-artwork",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_show_lyrics_artwork),
			value = preferenceManager.showLyricsArtwork,
			onSetValue = { preferenceManager.showLyricsArtwork = it }
		))
		add(switchRow(
			id = "lyrics.jump-on-tap",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_jump_on_tap),
			value = preferenceManager.lyricsJumpOnTap,
			onSetValue = { preferenceManager.lyricsJumpOnTap = it }
		))
		add(selectionRow(
			id = "lyrics.font-size",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_font_size),
			items = LyricsFontSize.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.lyricsFontSize,
			onSelect = { preferenceManager.lyricsFontSize = it }
		))
		add(switchRow(
			id = "lyrics.animate-size",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_animate_size),
			value = preferenceManager.lyricsAnimateSize,
			onSetValue = { preferenceManager.lyricsAnimateSize = it }
		))
		add(selectionRow(
			id = "lyrics.alignment",
			path = path(playback, lyrics),
			title = stringResource(Res.string.option_lyrics_alignment),
			items = LyricsAlignment.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.lyricsAlignment,
			onSelect = { preferenceManager.lyricsAlignment = it }
		))
		add(switchRow(
			id = "behaviour.scrobbling",
			path = path(playback, behaviour),
			title = stringResource(Res.string.option_enable_scrobbling),
			subtitle = stringResource(Res.string.subtitle_enable_scrobbling),
			value = preferenceManager.enableScrobbling,
			onSetValue = { preferenceManager.enableScrobbling = it }
		))
		add(switchRow(
			id = "behaviour.pause-listening-history",
			path = path(playback, behaviour),
			title = stringResource(Res.string.option_pause_listening_history),
			subtitle = stringResource(Res.string.subtitle_pause_listening_history),
			value = preferenceManager.pauseListeningHistory,
			onSetValue = { preferenceManager.pauseListeningHistory = it }
		))
		add(sliderRow(
			id = "behaviour.scrobble-percentage",
			path = path(playback, behaviour),
			title = stringResource(Res.string.option_scrobble_percentage),
			valueText = "${(preferenceManager.scrobblePercentage * 100).roundToInt()}%",
			value = preferenceManager.scrobblePercentage,
			onValueChange = { preferenceManager.scrobblePercentage = it },
			valueRange = 0f..1f
		))
		add(sliderRow(
			id = "behaviour.min-scrobble-duration",
			path = path(playback, behaviour),
			title = stringResource(Res.string.option_min_duration_to_scrobble),
			valueText = "${preferenceManager.minDurationToScrobble.toInt()}s",
			value = preferenceManager.minDurationToScrobble,
			onValueChange = { preferenceManager.minDurationToScrobble = it },
			valueRange = 0f..400f
		))

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

		add(switchRow(
			id = "lida.enabled",
			path = path(integrations, lidaClips),
			title = stringResource(Res.string.option_lida_clips_enabled),
			subtitle = stringResource(Res.string.subtitle_lida_clips_enabled),
			keywords = listOf("music video clips"),
			value = preferenceManager.lidaClipsEnabled,
			onSetValue = { preferenceManager.lidaClipsEnabled = it }
		))
		add(textFieldRow(
			id = "lida.base-url",
			path = path(integrations, lidaClips),
			title = stringResource(Res.string.option_lida_clips_base_url),
			value = preferenceManager.lidaClipsBaseUrl,
			keywords = listOf("endpoint", "server", "music video clips"),
			keyboardType = KeyboardType.Uri,
			onValueChange = { preferenceManager.lidaClipsBaseUrl = it }
		))
		add(textFieldRow(
			id = "lida.api-key",
			path = path(integrations, lidaClips),
			title = stringResource(Res.string.option_lida_clips_api_key),
			value = preferenceManager.lidaClipsApiKey,
			keywords = listOf("token", "music video clips"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.lidaClipsApiKey = it }
		))
		if (isAndroid) {
			add(switchRow(
				id = "lida.pip",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_picture_in_picture),
				subtitle = stringResource(Res.string.subtitle_lida_clips_picture_in_picture),
				value = preferenceManager.lidaClipsPictureInPicture,
				onSetValue = { preferenceManager.lidaClipsPictureInPicture = it }
			))
			add(switchRow(
				id = "lida.landscape",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_landscape_video_mode),
				subtitle = stringResource(Res.string.subtitle_lida_clips_landscape_video_mode),
				value = preferenceManager.lidaClipsLandscapeVideoMode,
				onSetValue = { preferenceManager.lidaClipsLandscapeVideoMode = it }
			))
			add(selectionRow(
				id = "lida.background-video",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_background_video),
				subtitle = stringResource(Res.string.subtitle_lida_clips_background_video),
				items = LidaClipsBackgroundVideoMode.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.lidaClipsBackgroundVideoMode,
				onSelect = { preferenceManager.lidaClipsBackgroundVideoMode = it }
			))
			add(switchRow(
				id = "lida.lyrics-video-background",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_lyrics_video_background),
				subtitle = stringResource(Res.string.subtitle_lida_clips_lyrics_video_background),
				keywords = listOf("lyrics", "background", "music video clips"),
				value = preferenceManager.lidaClipsLyricsVideoBackground,
				onSetValue = { preferenceManager.lidaClipsLyricsVideoBackground = it }
			))
			add(switchRow(
				id = "lida.musicbrainz-video-background",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_musicbrainz_video_background),
				subtitle = stringResource(Res.string.subtitle_lida_clips_musicbrainz_video_background),
				keywords = listOf("musicbrainz", "trivia", "metadata", "background", "music video clips"),
				value = preferenceManager.lidaClipsMusicBrainzInfoVideoBackground,
				onSetValue = { preferenceManager.lidaClipsMusicBrainzInfoVideoBackground = it }
			))
			add(selectionRow(
				id = "lida.video-fit",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_video_fit),
				subtitle = stringResource(Res.string.subtitle_lida_clips_video_fit),
				items = LidaClipsVideoFitMode.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.lidaClipsVideoFitMode,
				onSelect = { preferenceManager.lidaClipsVideoFitMode = it }
			))
			add(selectionRow(
				id = "lida.video-cache-size",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_video_cache_size),
				subtitle = stringResource(Res.string.subtitle_lida_clips_video_cache_size),
				keywords = listOf("cache", "download", "offline", "music video clips"),
				items = LidaClipsVideoCacheSizeOptionsMb,
				label = { lidaClipsVideoCacheSizeLabel(it) },
				selection = preferenceManager.lidaClipsVideoCacheSizeMb,
				onSelect = { preferenceManager.lidaClipsVideoCacheSizeMb = it }
			))
			add(switchRow(
				id = "lida.save-with-downloads",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_save_with_downloads),
				subtitle = stringResource(Res.string.subtitle_lida_clips_save_with_downloads),
				keywords = listOf("download", "offline", "cache", "music video clips"),
				value = preferenceManager.lidaClipsSaveClipsWithDownloads,
				onSetValue = { preferenceManager.lidaClipsSaveClipsWithDownloads = it }
			))
			add(switchRow(
				id = "lida.pause-music",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_pause_music_playback),
				subtitle = stringResource(Res.string.subtitle_lida_clips_pause_music_playback),
				value = preferenceManager.lidaClipsPauseMusicPlayback,
				onSetValue = { preferenceManager.lidaClipsPauseMusicPlayback = it }
			))
			add(switchRow(
				id = "lida.remember-position",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_remember_playback_position),
				subtitle = stringResource(Res.string.subtitle_lida_clips_remember_playback_position),
				value = preferenceManager.lidaClipsRememberPlaybackPosition,
				onSetValue = { preferenceManager.lidaClipsRememberPlaybackPosition = it }
			))
			add(switchRow(
				id = "lida.keep-screen-on",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_keep_screen_on),
				subtitle = stringResource(Res.string.subtitle_lida_clips_keep_screen_on),
				value = preferenceManager.lidaClipsKeepScreenOn,
				onSetValue = { preferenceManager.lidaClipsKeepScreenOn = it }
			))
		}
		add(switchRow(
			id = "aurral.enabled",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_enabled),
			subtitle = stringResource(Res.string.subtitle_aurral_enabled),
			keywords = listOf("Aurral", "Flows", "artist acquisition", "self hosted"),
			value = preferenceManager.aurralEnabled,
			onSetValue = { preferenceManager.aurralEnabled = it }
		))
		add(selectionRow(
			id = "aurral.artist-artwork-priority",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_artist_artwork_priority),
			subtitle = stringResource(Res.string.subtitle_artist_artwork_priority),
			keywords = listOf("Aurral", "artist", "photo", "cover", "artwork"),
			items = ArtworkSourcePriority.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.artistArtworkPriority,
			onSelect = { preferenceManager.artistArtworkPriority = it }
		))
		add(selectionRow(
			id = "aurral.cover-artwork-priority",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_cover_artwork_priority),
			subtitle = stringResource(Res.string.subtitle_cover_artwork_priority),
			keywords = listOf("Aurral", "album", "track", "cover", "artwork"),
			items = ArtworkSourcePriority.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.coverArtworkPriority,
			onSelect = { preferenceManager.coverArtworkPriority = it }
		))
		add(switchRow(
			id = "lastfm.enabled",
			path = path(integrations, lastFm),
			title = stringResource(Res.string.option_lastfm_enabled),
			subtitle = stringResource(Res.string.subtitle_lastfm_enabled),
			keywords = listOf("Last.fm", "artist top tracks", "recommendations", "public metadata"),
			value = preferenceManager.lastFmEnabled,
			onSetValue = { preferenceManager.lastFmEnabled = it }
		))
		add(textFieldRow(
			id = "lastfm.api-key",
			path = path(integrations, lastFm),
			title = stringResource(Res.string.option_lastfm_api_key),
			value = preferenceManager.lastFmApiKey,
			keywords = listOf("Last.fm", "artist top tracks", "scrobble", "recommendations"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.lastFmApiKey = it }
		))
		add(switchRow(
			id = "bindery.enabled",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_enabled),
			subtitle = stringResource(Res.string.subtitle_bindery_enabled),
			keywords = listOf("Bindery", "OPDS", "audiobooks", "long-form"),
			value = preferenceManager.binderyEnabled,
			onSetValue = { preferenceManager.binderyEnabled = it }
		))
		add(textFieldRow(
			id = "bindery.opds-url",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_opds_url),
			value = preferenceManager.binderyOpdsBaseUrl,
			keywords = listOf("Bindery", "OPDS", "endpoint", "server", "audiobooks"),
			keyboardType = KeyboardType.Uri,
			onValueChange = { preferenceManager.binderyOpdsBaseUrl = it }
		))
		add(textFieldRow(
			id = "bindery.api-key",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_api_key),
			value = preferenceManager.binderyApiKey,
			keywords = listOf("Bindery", "OPDS", "token", "audiobooks"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.binderyApiKey = it }
		))
		add(textFieldRow(
			id = "bindery.language-filter",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_language_filter),
			value = preferenceManager.binderyLanguageFilter,
			keywords = listOf("Bindery", "OPDS", "language", "audiobooks", "books"),
			onValueChange = { preferenceManager.binderyLanguageFilter = it }
		))
		add(selectionRow(
			id = "bindery.book-grid-columns",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_book_grid_columns),
			subtitle = stringResource(Res.string.subtitle_bindery_book_grid_columns),
			keywords = listOf("Bindery", "audiobooks", "books", "collections", "grid", "columns"),
			items = binderyBookGridColumnSearchOptions,
			label = { columns -> columns.toString() },
			selection = normalizedBinderyBookGridColumns(preferenceManager.binderyBookGridColumns),
			onSelect = { columns -> preferenceManager.binderyBookGridColumns = columns }
		))
		add(textFieldRow(
			id = "aurral.base-url",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_base_url),
			value = preferenceManager.aurralBaseUrl,
			keywords = listOf("endpoint", "server", "Aurral", "Flows"),
			keyboardType = KeyboardType.Uri,
			onValueChange = { preferenceManager.aurralBaseUrl = it }
		))
		add(textFieldRow(
			id = "aurral.username",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_username),
			value = preferenceManager.aurralUsername,
			keywords = listOf("login", "Basic Auth", "Aurral"),
			onValueChange = { preferenceManager.aurralUsername = it }
		))
		add(textFieldRow(
			id = "aurral.password",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_password),
			value = preferenceManager.aurralPassword,
			keywords = listOf("login", "Basic Auth", "Aurral"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.aurralPassword = it }
		))

		if (!isApple) {
			add(switchRow(
				id = "developer.updates",
				path = path(developer),
				title = stringResource(Res.string.option_check_for_updates),
				subtitle = stringResource(Res.string.subtitle_check_for_updates),
				value = preferenceManager.checkForUpdates,
				onSetValue = { preferenceManager.checkForUpdates = it }
			))
		}
		add(switchRow(
			id = "developer.issue-logging",
			path = path(developer),
			title = stringResource(Res.string.option_issue_logging),
			subtitle = stringResource(Res.string.subtitle_issue_logging),
			keywords = listOf("logs", "diagnostics", "playback", "errors", "issues"),
			value = preferenceManager.issueLoggingEnabled,
			onSetValue = appLogManager::setEnabled
		))
		add(switchRow(
			id = "developer.reverse-proxy-basic-auth",
			path = path(developer, stringResource(Res.string.title_reverse_proxy_auth)),
			title = stringResource(Res.string.option_reverse_proxy_basic_auth),
			subtitle = stringResource(Res.string.subtitle_reverse_proxy_basic_auth),
			keywords = listOf("Traefik", "Authorization", "Basic Auth"),
			value = preferenceManager.reverseProxyBasicAuthEnabled,
			onSetValue = {
				preferenceManager.reverseProxyBasicAuthEnabled = it
				sessionManager.refreshClient()
			}
		))
		add(textFieldRow(
			id = "developer.reverse-proxy-username",
			path = path(developer, stringResource(Res.string.title_reverse_proxy_auth)),
			title = stringResource(Res.string.option_reverse_proxy_username),
			value = preferenceManager.reverseProxyBasicAuthUsername,
			keywords = listOf("Traefik", "Basic Auth"),
			onValueChange = {
				preferenceManager.reverseProxyBasicAuthUsername = it
				sessionManager.refreshClient()
			}
		))
		add(textFieldRow(
			id = "developer.reverse-proxy-password",
			path = path(developer, stringResource(Res.string.title_reverse_proxy_auth)),
			title = stringResource(Res.string.option_reverse_proxy_password),
			value = preferenceManager.reverseProxyBasicAuthPassword,
			keywords = listOf("Traefik", "Basic Auth"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = {
				preferenceManager.reverseProxyBasicAuthPassword = it
				sessionManager.refreshClient()
			}
		))
	}
}

private fun switchRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	value: Boolean,
	onSetValue: (Boolean) -> Unit
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			SettingSwitchRow(
				title = { Text(title) },
				subtitle = { subtitle?.let { Text(it) } },
				value = value,
				onSetValue = onSetValue
			)
		}
	)

private fun <Item> selectionRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	items: List<Item>,
	label: @Composable (Item) -> String,
	selection: Item,
	onSelect: (Item) -> Unit
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			SettingSelectionRow(
				title = { Text(title) },
				items = items.toImmutableList(),
				label = label,
				description = subtitle,
				selection = selection,
				onSelect = onSelect
			)
		}
	)

private fun valueRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	value: String
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			SettingValueRow(
				title = { Text(title) },
				subtitle = { subtitle?.let { Text(it) } },
				value = value
			)
		}
	)

private fun sliderRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	valueText: String,
	value: Float,
	onValueChange: (Float) -> Unit,
	valueRange: ClosedFloatingPointRange<Float>,
	steps: Int = 0
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			FormRow {
				Column(Modifier.fillMaxWidth()) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						Column(Modifier.weight(1f)) {
							Text(title)
							subtitle?.let {
								Text(
									text = it,
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						Text(
							valueText,
							modifier = Modifier.padding(start = 16.dp),
							fontFamily = FontFamily.Monospace,
							fontWeight = FontWeight(400),
							fontSize = 13.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Slider(
						value = value,
						onValueChange = onValueChange,
						valueRange = valueRange,
						steps = steps
					)
				}
			}
		}
	)

private fun textFieldRow(
	id: String,
	path: String,
	title: String,
	value: String,
	keywords: List<String> = emptyList(),
	keyboardType: KeyboardType = KeyboardType.Text,
	isPassword: Boolean = false,
	digitsOnly: Boolean = false,
	onValueChange: (String) -> Unit
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			keywords = keywords
		),
		content = {
			var fieldValue by remember(value) { mutableStateOf(value) }
			FormRow {
				TextField(
					value = fieldValue,
					onValueChange = { newValue ->
						if (!digitsOnly || newValue.all { it.isDigit() }) {
							fieldValue = newValue
							onValueChange(newValue)
						}
					},
					placeholder = { Text(title) },
					modifier = Modifier.fillMaxWidth(),
					singleLine = true,
					visualTransformation = if (isPassword) {
						PasswordVisualTransformation()
					} else {
						VisualTransformation.None
					},
					keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
					colors = TextFieldDefaults.colors(
						focusedIndicatorColor = Color.Transparent,
						unfocusedIndicatorColor = Color.Transparent
					),
					shape = MaterialTheme.shapes.medium
				)
			}
		}
	)

private val nowPlayingUpNextCountSearchOptions = listOf(1, 2, 3, 5)
private val pauseBetweenSongsSearchOptions = listOf(0, 5, 10, 15, 20, 30, 40, 50, 60)
private val medleyModeSearchOptions = listOf(0, 15, 30, 45, 60)
private val smartRewindSearchOptions = listOf(1, 2, 3, 5, 10, 15, 30)
private val audioFadeSearchOptions = listOf(0, 250, 500, 1000, 2000)
private val autoFillQueueTargetSizeSearchOptions = listOf(10, 25, 50, 100)
private val downloadConcurrencySearchOptions = listOf(1, 2, 3, 5, 10)
private val binderyBookGridColumnSearchOptions = (BinderyMinBookGridColumns..BinderyMaxBookGridColumns).toList()
private val readerFontFamilySearchOptions = ReaderSupportedFontFamilies
private val readerFontSourceSearchOptions = ReaderSupportedFontSources
private val readerFontSizeSearchOptions = listOf(90, 100, 112, 125, 140, 160, 180)
private val readerLineHeightSearchOptions = listOf(120, 135, 155, 170, 190, 220)
private val readerParagraphSpacingSearchOptions = listOf(0, 25, 50, 75, 100, 150, 200)
private val readerMarginSearchOptions = listOf(0, 4, 8, 12, 16, 24)
private val readerDimOverlaySearchOptions = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80)
private val readerThemeSearchOptions = ReaderSupportedThemes
private val readerOrientationSearchOptions = ReaderSupportedOrientations
private val readerDirectionSearchOptions = ReaderSupportedDirections
private val readerFlowSearchOptions = ReaderSupportedFlowModes
private val readerTapZoneSearchOptions = ReaderSupportedTapZones

@Composable
private fun readerFontFamilySearchLabel(fontFamily: String): String =
	when (fontFamily) {
		ReaderSerifFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_serif)
		ReaderBookFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_book)
		ReaderHumanistFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_humanist)
		ReaderDyslexicFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_dyslexic)
		ReaderMonoFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_mono)
		ReaderPublisherFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_publisher)
		else -> stringResource(Res.string.option_ebook_reader_font_family_sans)
	}

@Composable
private fun readerFontSourceSearchLabel(fontSource: String): String =
	when (fontSource) {
		ReaderFontSourceSystem -> stringResource(Res.string.option_ebook_reader_font_source_system)
		ReaderFontSourcePublisher -> stringResource(Res.string.option_ebook_reader_font_source_publisher)
		else -> stringResource(Res.string.option_ebook_reader_font_source_navic)
	}

@Composable
private fun readerThemeSearchLabel(theme: String): String =
	when (theme) {
		ReaderSepiaTheme -> stringResource(Res.string.option_ebook_reader_theme_sepia)
		ReaderDuskTheme -> stringResource(Res.string.option_ebook_reader_theme_dusk)
		ReaderDarkTheme -> stringResource(Res.string.option_ebook_reader_theme_dark)
		ReaderBlackTheme -> stringResource(Res.string.option_ebook_reader_theme_black)
		else -> stringResource(Res.string.option_ebook_reader_theme_light)
	}

@Composable
private fun readerOrientationSearchLabel(orientation: String): String =
	when (orientation) {
		ReaderOrientationFree -> stringResource(Res.string.option_ebook_reader_orientation_free)
		ReaderOrientationPortrait -> stringResource(Res.string.option_ebook_reader_orientation_portrait)
		ReaderOrientationLandscape -> stringResource(Res.string.option_ebook_reader_orientation_landscape)
		ReaderOrientationLockedPortrait -> stringResource(Res.string.option_ebook_reader_orientation_locked_portrait)
		ReaderOrientationLockedLandscape -> stringResource(Res.string.option_ebook_reader_orientation_locked_landscape)
		ReaderOrientationReversePortrait -> stringResource(Res.string.option_ebook_reader_orientation_reverse_portrait)
		else -> stringResource(Res.string.option_ebook_reader_orientation_default)
	}

@Composable
private fun readerDirectionSearchLabel(direction: String): String =
	when (direction) {
		ReaderDirectionLtr -> stringResource(Res.string.option_ebook_reader_direction_ltr)
		ReaderDirectionRtl -> stringResource(Res.string.option_ebook_reader_direction_rtl)
		else -> stringResource(Res.string.option_ebook_reader_direction_default)
	}

@Composable
private fun readerFlowSearchLabel(flowMode: String): String =
	when (flowMode) {
		ReaderFlowPagedVertical -> stringResource(Res.string.option_ebook_reader_paged_vertical)
		ReaderFlowScrolled -> stringResource(Res.string.option_ebook_reader_scroll)
		ReaderFlowScrolledGaps -> stringResource(Res.string.option_ebook_reader_scroll_gaps)
		else -> stringResource(Res.string.option_ebook_reader_paged)
	}

@Composable
private fun readerTapZoneSearchLabel(tapZone: String): String =
	when (tapZone) {
		ReaderTapZoneEdge -> stringResource(Res.string.option_ebook_reader_tap_zone_edge)
		ReaderTapZoneKindle -> stringResource(Res.string.option_ebook_reader_tap_zone_kindle)
		ReaderTapZoneLShaped -> stringResource(Res.string.option_ebook_reader_tap_zone_l_shaped)
		ReaderTapZoneRightLeft -> stringResource(Res.string.option_ebook_reader_tap_zone_right_left)
		ReaderTapZoneDisabled -> stringResource(Res.string.option_ebook_reader_tap_zone_disabled)
		else -> stringResource(Res.string.option_ebook_reader_tap_zone_default)
	}

@Composable
private fun readerLineHeightSearchLabel(percent: Int): String =
	"${percent / 100}.${(percent % 100).toString().padStart(2, '0')}".trimEnd('0').trimEnd('.')

@Composable
private fun readerDimOverlaySearchLabel(percent: Int): String =
	if (percent <= 0) stringResource(Res.string.option_off) else "$percent%"
private val quickPicksLimitSearchOptions = listOf(10, 20, 30, 50)
private val quickPicksMinDurationSearchOptions = listOf(0, 30, 60, 120, 180)

@Composable
private fun quickPicksMinDurationLabel(seconds: Int): String =
	when {
		seconds <= 0 -> stringResource(Res.string.option_off)
		seconds < 60 -> "${seconds}s"
		else -> "${seconds / 60} min"
	}
