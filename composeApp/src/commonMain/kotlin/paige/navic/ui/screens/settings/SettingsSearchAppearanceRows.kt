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
internal fun settingsSearchAppearanceRows(context: SettingsSearchContext): List<SearchableSettingsRow> = with(context) {
	buildList {
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
		add(switchRow(
			id = "appearance.dynamic-themes",
			path = path(appearance),
			title = stringResource(Res.string.option_dynamic_themes),
			subtitle = stringResource(Res.string.subtitle_dynamic_themes),
			keywords = listOf("artwork", "cover", "palette", "colour", "color"),
			value = preferenceManager.dynamicThemes,
			onSetValue = { preferenceManager.dynamicThemes = it }
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
			label = { quickPicksMinDurationSearchLabel(it) },
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

	}
}
