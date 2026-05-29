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
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.MaxNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MaxNowPlayingBackgroundDimPercent
import paige.navic.domain.models.MinNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MinNowPlayingBackgroundDimPercent
import paige.navic.domain.models.nowPlayingBackgroundBlurDp
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.models.settings.*
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import kotlin.math.roundToInt

private data class SearchableSettingsRow(
	val text: SettingsSearchEntryText,
	val content: @Composable () -> Unit
)

private data class SearchableSettingsRowGroup(
	val path: String,
	val rows: List<SearchableSettingsRow>
)

@Composable
fun SettingsSearchResults(query: String) {
	val rows = searchableSettingsRows()
	val rowById = rows.associateBy { it.text.id }
	val resultGroups = filteredSettingsSearchEntryGroups(
		entries = rows.map { it.text },
		query = query
	).mapNotNull { group ->
		val groupRows = group.entries.mapNotNull { rowById[it.id] }
		groupRows.takeIf { it.isNotEmpty() }?.let {
			SearchableSettingsRowGroup(path = group.path, rows = it)
		}
	}

	CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
		if (resultGroups.isEmpty()) {
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

		resultGroups.forEach { group ->
			Form(bottomPadding = 12.dp) {
				Text(
					text = group.path,
					modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary
				)
				group.rows.forEach { result ->
					result.content()
				}
			}
		}
	}
}

@Composable
private fun searchableSettingsRows(): List<SearchableSettingsRow> {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val sessionManager = koinInject<SessionManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val platformContext = LocalPlatformContext.current
	val isAndroid = platformContext.name.lowercase().startsWith("android")
	val isApple = listOf("ios", "ipados").contains(platformContext.name.lowercase())

	val settings = stringResource(Res.string.title_settings)
	fun path(vararg parts: String): String = (listOf(settings) + parts).joinToString(" > ")

	val appearance = stringResource(Res.string.title_appearance)
	val nowPlaying = stringResource(Res.string.title_now_playing)
	val bottomBar = stringResource(Res.string.title_bottom_app_bar)
	val playback = stringResource(Res.string.title_playback)
	val dataStorage = stringResource(Res.string.title_data_storage)
	val developer = stringResource(Res.string.title_developer)
	val layout = stringResource(Res.string.title_layout)
	val library = stringResource(Res.string.title_library)
	val actions = stringResource(Res.string.title_actions)
	val behaviour = stringResource(Res.string.title_behaviour)
	val lyrics = stringResource(Res.string.action_lyrics)
	val network = stringResource(Res.string.title_network)
	val lidaClips = stringResource(Res.string.title_lida_clips)
	val cacheManagement = stringResource(Res.string.title_cache_management)
	val miniPlayer = stringResource(Res.string.title_mini_player)
	val navigationBar = stringResource(Res.string.title_navigation_bar)
	val streamingQuality = stringResource(Res.string.title_streaming_quality)

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
			add(selectionRow(
				id = "playback.queue-shuffle-limit",
				path = path(playback),
				title = stringResource(Res.string.option_queue_shuffle_limit),
				subtitle = stringResource(Res.string.subtitle_queue_shuffle_limit),
				items = queueShuffleLimitSearchOptions,
				label = { if (it == 0) stringResource(Res.string.option_unlimited) else "$it songs" },
				selection = preferenceManager.queueShuffleLimit,
				onSelect = { preferenceManager.queueShuffleLimit = it }
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
			id = "data.musicbrainz",
			path = path(dataStorage, network),
			title = stringResource(Res.string.option_musicbrainz_artwork_fallback),
			subtitle = stringResource(Res.string.subtitle_musicbrainz_artwork_fallback),
			keywords = listOf("cover art archive", "metadata", "artwork"),
			value = preferenceManager.musicBrainzArtworkFallbackEnabled,
			onSetValue = {
				preferenceManager.musicBrainzArtworkFallbackEnabled = it
				musicBrainzArtworkRepository.refreshCacheVisibility()
			}
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
			path = path(dataStorage, lidaClips),
			title = stringResource(Res.string.option_lida_clips_enabled),
			subtitle = stringResource(Res.string.subtitle_lida_clips_enabled),
			keywords = listOf("music video clips"),
			value = preferenceManager.lidaClipsEnabled,
			onSetValue = { preferenceManager.lidaClipsEnabled = it }
		))
		add(textFieldRow(
			id = "lida.base-url",
			path = path(dataStorage, lidaClips),
			title = stringResource(Res.string.option_lida_clips_base_url),
			value = preferenceManager.lidaClipsBaseUrl,
			keywords = listOf("endpoint", "server", "music video clips"),
			keyboardType = KeyboardType.Uri,
			onValueChange = { preferenceManager.lidaClipsBaseUrl = it }
		))
		add(textFieldRow(
			id = "lida.api-key",
			path = path(dataStorage, lidaClips),
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
				path = path(dataStorage, lidaClips),
				title = stringResource(Res.string.option_lida_clips_picture_in_picture),
				subtitle = stringResource(Res.string.subtitle_lida_clips_picture_in_picture),
				value = preferenceManager.lidaClipsPictureInPicture,
				onSetValue = { preferenceManager.lidaClipsPictureInPicture = it }
			))
			add(switchRow(
				id = "lida.landscape",
				path = path(dataStorage, lidaClips),
				title = stringResource(Res.string.option_lida_clips_landscape_video_mode),
				subtitle = stringResource(Res.string.subtitle_lida_clips_landscape_video_mode),
				value = preferenceManager.lidaClipsLandscapeVideoMode,
				onSetValue = { preferenceManager.lidaClipsLandscapeVideoMode = it }
			))
			add(selectionRow(
				id = "lida.video-fit",
				path = path(dataStorage, lidaClips),
				title = stringResource(Res.string.option_lida_clips_video_fit),
				subtitle = stringResource(Res.string.subtitle_lida_clips_video_fit),
				items = LidaClipsVideoFitMode.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.lidaClipsVideoFitMode,
				onSelect = { preferenceManager.lidaClipsVideoFitMode = it }
			))
			add(switchRow(
				id = "lida.pause-music",
				path = path(dataStorage, lidaClips),
				title = stringResource(Res.string.option_lida_clips_pause_music_playback),
				subtitle = stringResource(Res.string.subtitle_lida_clips_pause_music_playback),
				value = preferenceManager.lidaClipsPauseMusicPlayback,
				onSetValue = { preferenceManager.lidaClipsPauseMusicPlayback = it }
			))
			add(switchRow(
				id = "lida.remember-position",
				path = path(dataStorage, lidaClips),
				title = stringResource(Res.string.option_lida_clips_remember_playback_position),
				subtitle = stringResource(Res.string.subtitle_lida_clips_remember_playback_position),
				value = preferenceManager.lidaClipsRememberPlaybackPosition,
				onSetValue = { preferenceManager.lidaClipsRememberPlaybackPosition = it }
			))
			add(switchRow(
				id = "lida.keep-screen-on",
				path = path(dataStorage, lidaClips),
				title = stringResource(Res.string.option_lida_clips_keep_screen_on),
				subtitle = stringResource(Res.string.subtitle_lida_clips_keep_screen_on),
				value = preferenceManager.lidaClipsKeepScreenOn,
				onSetValue = { preferenceManager.lidaClipsKeepScreenOn = it }
			))
		}

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
private val queueShuffleLimitSearchOptions = listOf(0, 50, 100, 200, 500, 1000, 2000, 3000)
private val downloadConcurrencySearchOptions = listOf(1, 2, 3, 5, 10)
private val quickPicksLimitSearchOptions = listOf(10, 20, 30, 50)
