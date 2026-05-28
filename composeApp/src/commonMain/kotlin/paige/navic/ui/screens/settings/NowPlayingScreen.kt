package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_background_blur
import navic.composeapp.generated.resources.option_now_playing_background_dim
import navic.composeapp.generated.resources.option_now_playing_background_style
import navic.composeapp.generated.resources.option_now_playing_add_to_playlist_action
import navic.composeapp.generated.resources.option_now_playing_artwork
import navic.composeapp.generated.resources.option_now_playing_artwork_size
import navic.composeapp.generated.resources.option_now_playing_discover_queue_action
import navic.composeapp.generated.resources.option_now_playing_download_action
import navic.composeapp.generated.resources.option_now_playing_equalizer_action
import navic.composeapp.generated.resources.option_now_playing_info_style
import navic.composeapp.generated.resources.option_now_playing_lyrics_action
import navic.composeapp.generated.resources.option_now_playing_music_video_action
import navic.composeapp.generated.resources.option_now_playing_more_action
import navic.composeapp.generated.resources.option_now_playing_playback_speed_action
import navic.composeapp.generated.resources.option_now_playing_progress_width
import navic.composeapp.generated.resources.option_now_playing_queue_action
import navic.composeapp.generated.resources.option_now_playing_remaining_time
import navic.composeapp.generated.resources.option_now_playing_repeat_control
import navic.composeapp.generated.resources.option_now_playing_seek_buttons
import navic.composeapp.generated.resources.option_now_playing_shuffle_control
import navic.composeapp.generated.resources.option_now_playing_shrink_artwork_on_pause
import navic.composeapp.generated.resources.option_now_playing_space_playback_controls_evenly
import navic.composeapp.generated.resources.option_now_playing_sleep_timer_action
import navic.composeapp.generated.resources.option_now_playing_slider_style
import navic.composeapp.generated.resources.option_now_playing_song_info
import navic.composeapp.generated.resources.option_now_playing_start_radio_action
import navic.composeapp.generated.resources.option_now_playing_swap_controls_and_timeline
import navic.composeapp.generated.resources.option_now_playing_swipe_up_controls_for_queue
import navic.composeapp.generated.resources.option_now_playing_tap_controls_for_queue
import navic.composeapp.generated.resources.option_now_playing_technical_info_style
import navic.composeapp.generated.resources.option_now_playing_toolbar_position
import navic.composeapp.generated.resources.option_now_playing_up_next
import navic.composeapp.generated.resources.option_now_playing_up_next_artwork
import navic.composeapp.generated.resources.option_now_playing_up_next_count
import navic.composeapp.generated.resources.option_swipe_to_skip
import navic.composeapp.generated.resources.option_tap_artwork_for_lyrics
import navic.composeapp.generated.resources.subtitle_now_playing_background_blur
import navic.composeapp.generated.resources.subtitle_now_playing_background_dim
import navic.composeapp.generated.resources.subtitle_now_playing_add_to_playlist_action
import navic.composeapp.generated.resources.subtitle_now_playing_artwork
import navic.composeapp.generated.resources.subtitle_now_playing_background_style
import navic.composeapp.generated.resources.subtitle_now_playing_discover_queue_action
import navic.composeapp.generated.resources.subtitle_now_playing_download_action
import navic.composeapp.generated.resources.subtitle_now_playing_info_style
import navic.composeapp.generated.resources.subtitle_now_playing_more_action
import navic.composeapp.generated.resources.subtitle_now_playing_progress_width
import navic.composeapp.generated.resources.subtitle_now_playing_remaining_time
import navic.composeapp.generated.resources.subtitle_now_playing_repeat_control
import navic.composeapp.generated.resources.subtitle_now_playing_seek_buttons
import navic.composeapp.generated.resources.subtitle_now_playing_shuffle_control
import navic.composeapp.generated.resources.subtitle_now_playing_shrink_artwork_on_pause
import navic.composeapp.generated.resources.subtitle_now_playing_space_playback_controls_evenly
import navic.composeapp.generated.resources.subtitle_now_playing_start_radio_action
import navic.composeapp.generated.resources.subtitle_now_playing_swap_controls_and_timeline
import navic.composeapp.generated.resources.subtitle_now_playing_swipe_up_controls_for_queue
import navic.composeapp.generated.resources.subtitle_now_playing_tap_controls_for_queue
import navic.composeapp.generated.resources.subtitle_now_playing_technical_info_style
import navic.composeapp.generated.resources.subtitle_now_playing_toolbar_position
import navic.composeapp.generated.resources.subtitle_now_playing_up_next
import navic.composeapp.generated.resources.subtitle_now_playing_up_next_artwork
import navic.composeapp.generated.resources.subtitle_now_playing_up_next_count
import navic.composeapp.generated.resources.title_actions
import navic.composeapp.generated.resources.title_layout
import navic.composeapp.generated.resources.title_now_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.MaxNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MaxNowPlayingBackgroundDimPercent
import paige.navic.domain.models.MinNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MinNowPlayingBackgroundDimPercent
import paige.navic.domain.models.nowPlayingBackgroundBlurDp
import paige.navic.domain.models.settings.NowPlayingArtworkSize
import paige.navic.domain.models.settings.NowPlayingBackgroundStyle
import paige.navic.domain.models.settings.NowPlayingInfoStyle
import paige.navic.domain.models.settings.NowPlayingProgressWidth
import paige.navic.domain.models.settings.NowPlayingTechnicalInfoStyle
import paige.navic.domain.models.settings.ToolbarPosition
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.dialogs.NowPlayingSliderStyleDialog
import kotlin.math.roundToInt

@Composable
fun SettingsNowPlayingScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_now_playing)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(top = 16.dp, end = 16.dp, start = 16.dp)
			) {
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_swipe_to_skip)) },
						value = preferenceManager.swipeToSkip,
						onSetValue = { preferenceManager.swipeToSkip = it }
					)

					AnimatedVisibility(preferenceManager.showNowPlayingArtwork) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_tap_artwork_for_lyrics)) },
							value = preferenceManager.tapArtworkForLyrics,
							onSetValue = { preferenceManager.tapArtworkForLyrics = it }
						)
					}

					SettingSelectionRow(
						items = NowPlayingBackgroundStyle.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.nowPlayingBackgroundStyle,
						onSelect = { preferenceManager.nowPlayingBackgroundStyle = it },
						description = stringResource(Res.string.subtitle_now_playing_background_style),
						title = { Text(stringResource(Res.string.option_now_playing_background_style)) }
					)

					AnimatedVisibility(
						preferenceManager.nowPlayingBackgroundStyle == NowPlayingBackgroundStyle.Dynamic
					) {
						val backgroundBlurDp = nowPlayingBackgroundBlurDp(
							preferenceManager.nowPlayingBackgroundBlurDp
						)
						NowPlayingBackgroundSliderRow(
							title = stringResource(Res.string.option_now_playing_background_blur),
							subtitle = stringResource(Res.string.subtitle_now_playing_background_blur),
							valueText = "${backgroundBlurDp.roundToInt()}dp",
							value = backgroundBlurDp,
							onValueChange = { value ->
								preferenceManager.nowPlayingBackgroundBlurDp = value.roundToInt().toFloat()
							},
							valueRange = MinNowPlayingBackgroundBlurDp..MaxNowPlayingBackgroundBlurDp
						)
					}

					AnimatedVisibility(
						preferenceManager.nowPlayingBackgroundStyle == NowPlayingBackgroundStyle.Dynamic
					) {
						val dimPercent = preferenceManager.nowPlayingBackgroundDimPercent.coerceIn(
							MinNowPlayingBackgroundDimPercent,
							MaxNowPlayingBackgroundDimPercent
						)
						NowPlayingBackgroundSliderRow(
							title = stringResource(Res.string.option_now_playing_background_dim),
							subtitle = stringResource(Res.string.subtitle_now_playing_background_dim),
							valueText = "$dimPercent%",
							value = dimPercent.toFloat(),
							onValueChange = { value ->
								preferenceManager.nowPlayingBackgroundDimPercent = value.roundToInt()
							},
							valueRange = MinNowPlayingBackgroundDimPercent.toFloat()..
								MaxNowPlayingBackgroundDimPercent.toFloat()
						)
					}

					var showSliderStyleDialog by rememberSaveable { mutableStateOf(false) }
					FormRow(
						onClick = {
							showSliderStyleDialog = true
						}
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.option_now_playing_slider_style))
							Text(
								stringResource(preferenceManager.nowPlayingSliderStyle.displayName),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}

					NowPlayingSliderStyleDialog(
						presented = showSliderStyleDialog,
						onDismissRequest = { showSliderStyleDialog = false }
					)

					SettingSelectionRow(
						items = NowPlayingProgressWidth.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.nowPlayingProgressWidth,
						onSelect = { preferenceManager.nowPlayingProgressWidth = it },
						description = stringResource(Res.string.subtitle_now_playing_progress_width),
						title = { Text(stringResource(Res.string.option_now_playing_progress_width)) }
					)
				}

				FormTitle(stringResource(Res.string.title_layout))
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_artwork)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_artwork)) },
						value = preferenceManager.showNowPlayingArtwork,
						onSetValue = { preferenceManager.showNowPlayingArtwork = it }
					)

					AnimatedVisibility(preferenceManager.showNowPlayingArtwork) {
						SettingSelectionRow(
							items = NowPlayingArtworkSize.entries.toImmutableList(),
							label = { stringResource(it.displayName) },
							selection = preferenceManager.nowPlayingArtworkSize,
							onSelect = { preferenceManager.nowPlayingArtworkSize = it },
							title = { Text(stringResource(Res.string.option_now_playing_artwork_size)) }
						)
					}

					AnimatedVisibility(preferenceManager.showNowPlayingArtwork) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_now_playing_shrink_artwork_on_pause)) },
							subtitle = { Text(stringResource(Res.string.subtitle_now_playing_shrink_artwork_on_pause)) },
							value = preferenceManager.shrinkNowPlayingArtworkOnPause,
							onSetValue = { preferenceManager.shrinkNowPlayingArtworkOnPause = it }
						)
					}

					SettingSelectionRow(
						items = NowPlayingInfoStyle.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.nowPlayingInfoStyle,
						onSelect = { preferenceManager.nowPlayingInfoStyle = it },
						description = stringResource(Res.string.subtitle_now_playing_info_style),
						title = { Text(stringResource(Res.string.option_now_playing_info_style)) }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_song_info)) },
						value = preferenceManager.nowPlayingSongInfo,
						onSetValue = { preferenceManager.nowPlayingSongInfo = it }
					)

					AnimatedVisibility(preferenceManager.nowPlayingSongInfo) {
						SettingSelectionRow(
							items = NowPlayingTechnicalInfoStyle.entries.toImmutableList(),
							label = { stringResource(it.displayName) },
							selection = preferenceManager.nowPlayingTechnicalInfoStyle,
							onSelect = { preferenceManager.nowPlayingTechnicalInfoStyle = it },
							description = stringResource(Res.string.subtitle_now_playing_technical_info_style),
							title = { Text(stringResource(Res.string.option_now_playing_technical_info_style)) }
						)
					}

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_seek_buttons)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_seek_buttons)) },
						value = preferenceManager.showNowPlayingSeekButtons,
						onSetValue = { preferenceManager.showNowPlayingSeekButtons = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_remaining_time)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_remaining_time)) },
						value = preferenceManager.showNowPlayingRemainingTime,
						onSetValue = { preferenceManager.showNowPlayingRemainingTime = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_swap_controls_and_timeline)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_swap_controls_and_timeline)) },
						value = preferenceManager.swapNowPlayingControlsAndTimeline,
						onSetValue = { preferenceManager.swapNowPlayingControlsAndTimeline = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_space_playback_controls_evenly)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_space_playback_controls_evenly)) },
						value = preferenceManager.spaceNowPlayingPlaybackControlsEvenly,
						onSetValue = { preferenceManager.spaceNowPlayingPlaybackControlsEvenly = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_swipe_up_controls_for_queue)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_swipe_up_controls_for_queue)) },
						value = preferenceManager.openQueueOnNowPlayingControlsSwipeUp,
						onSetValue = { preferenceManager.openQueueOnNowPlayingControlsSwipeUp = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_tap_controls_for_queue)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_tap_controls_for_queue)) },
						value = preferenceManager.openQueueOnNowPlayingControlsTap,
						onSetValue = { preferenceManager.openQueueOnNowPlayingControlsTap = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_shuffle_control)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_shuffle_control)) },
						value = preferenceManager.showNowPlayingShuffleControl,
						onSetValue = { preferenceManager.showNowPlayingShuffleControl = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_repeat_control)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_repeat_control)) },
						value = preferenceManager.showNowPlayingRepeatControl,
						onSetValue = { preferenceManager.showNowPlayingRepeatControl = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_up_next)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_up_next)) },
						value = preferenceManager.showNowPlayingUpNext,
						onSetValue = { preferenceManager.showNowPlayingUpNext = it }
					)

					AnimatedVisibility(preferenceManager.showNowPlayingUpNext) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_now_playing_up_next_artwork)) },
							subtitle = { Text(stringResource(Res.string.subtitle_now_playing_up_next_artwork)) },
							value = preferenceManager.showNowPlayingUpNextArtwork,
							onSetValue = { preferenceManager.showNowPlayingUpNextArtwork = it }
						)
					}

					AnimatedVisibility(preferenceManager.showNowPlayingUpNext) {
						SettingSelectionRow(
							items = nowPlayingUpNextCountOptions.toImmutableList(),
							label = { if (it == 1) "1 song" else "$it songs" },
							selection = preferenceManager.nowPlayingUpNextCount,
							onSelect = { preferenceManager.nowPlayingUpNextCount = it },
							description = stringResource(Res.string.subtitle_now_playing_up_next_count),
							title = { Text(stringResource(Res.string.option_now_playing_up_next_count)) }
						)
					}

					SettingSelectionRow(
						items = ToolbarPosition.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.nowPlayingToolbarPosition,
						onSelect = { preferenceManager.nowPlayingToolbarPosition = it },
						description = stringResource(Res.string.subtitle_now_playing_toolbar_position),
						title = { Text(stringResource(Res.string.option_now_playing_toolbar_position)) }
					)
				}

				FormTitle(stringResource(Res.string.title_actions))
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_lyrics_action)) },
						value = preferenceManager.showNowPlayingLyricsAction,
						onSetValue = { preferenceManager.showNowPlayingLyricsAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_queue_action)) },
						value = preferenceManager.showNowPlayingQueueAction,
						onSetValue = { preferenceManager.showNowPlayingQueueAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_more_action)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_more_action)) },
						value = preferenceManager.showNowPlayingMoreAction,
						onSetValue = { preferenceManager.showNowPlayingMoreAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_music_video_action)) },
						value = preferenceManager.showNowPlayingMusicVideoAction,
						onSetValue = { preferenceManager.showNowPlayingMusicVideoAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_playback_speed_action)) },
						value = preferenceManager.showNowPlayingPlaybackSpeedAction,
						onSetValue = { preferenceManager.showNowPlayingPlaybackSpeedAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_sleep_timer_action)) },
						value = preferenceManager.showNowPlayingSleepTimerAction,
						onSetValue = { preferenceManager.showNowPlayingSleepTimerAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_start_radio_action)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_start_radio_action)) },
						value = preferenceManager.showNowPlayingStartRadioAction,
						onSetValue = { preferenceManager.showNowPlayingStartRadioAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_discover_queue_action)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_discover_queue_action)) },
						value = preferenceManager.showNowPlayingDiscoverQueueAction,
						onSetValue = { preferenceManager.showNowPlayingDiscoverQueueAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_download_action)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_download_action)) },
						value = preferenceManager.showNowPlayingDownloadAction,
						onSetValue = { preferenceManager.showNowPlayingDownloadAction = it }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_add_to_playlist_action)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_add_to_playlist_action)) },
						value = preferenceManager.showNowPlayingAddToPlaylistAction,
						onSetValue = { preferenceManager.showNowPlayingAddToPlaylistAction = it }
					)
					if (platformContext.name.lowercase().startsWith("android")) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_now_playing_equalizer_action)) },
							value = preferenceManager.showNowPlayingEqualizerAction,
							onSetValue = { preferenceManager.showNowPlayingEqualizerAction = it }
						)
					}
				}
			}
		}
	}
}

@Composable
private fun NowPlayingBackgroundSliderRow(
	title: String,
	subtitle: String,
	valueText: String,
	value: Float,
	onValueChange: (Float) -> Unit,
	valueRange: ClosedFloatingPointRange<Float>
) {
	FormRow {
		Column(Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Column(Modifier.weight(1f)) {
					Text(title)
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
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
				valueRange = valueRange
			)
		}
	}
}

private val nowPlayingUpNextCountOptions = listOf(1, 2, 3, 5)
