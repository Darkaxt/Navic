package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_background_style
import navic.composeapp.generated.resources.option_now_playing_artwork
import navic.composeapp.generated.resources.option_now_playing_equalizer_action
import navic.composeapp.generated.resources.option_now_playing_lyrics_action
import navic.composeapp.generated.resources.option_now_playing_music_video_action
import navic.composeapp.generated.resources.option_now_playing_playback_speed_action
import navic.composeapp.generated.resources.option_now_playing_queue_action
import navic.composeapp.generated.resources.option_now_playing_remaining_time
import navic.composeapp.generated.resources.option_now_playing_seek_buttons
import navic.composeapp.generated.resources.option_now_playing_sleep_timer_action
import navic.composeapp.generated.resources.option_now_playing_slider_style
import navic.composeapp.generated.resources.option_now_playing_song_info
import navic.composeapp.generated.resources.option_now_playing_toolbar_position
import navic.composeapp.generated.resources.option_now_playing_up_next
import navic.composeapp.generated.resources.option_now_playing_up_next_artwork
import navic.composeapp.generated.resources.option_now_playing_up_next_count
import navic.composeapp.generated.resources.option_swipe_to_skip
import navic.composeapp.generated.resources.option_tap_artwork_for_lyrics
import navic.composeapp.generated.resources.subtitle_now_playing_artwork
import navic.composeapp.generated.resources.subtitle_now_playing_background_style
import navic.composeapp.generated.resources.subtitle_now_playing_remaining_time
import navic.composeapp.generated.resources.subtitle_now_playing_seek_buttons
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
import paige.navic.domain.models.settings.NowPlayingBackgroundStyle
import paige.navic.domain.models.settings.ToolbarPosition
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.dialogs.NowPlayingSliderStyleDialog

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
				}

				FormTitle(stringResource(Res.string.title_layout))
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_artwork)) },
						subtitle = { Text(stringResource(Res.string.subtitle_now_playing_artwork)) },
						value = preferenceManager.showNowPlayingArtwork,
						onSetValue = { preferenceManager.showNowPlayingArtwork = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_now_playing_song_info)) },
						value = preferenceManager.nowPlayingSongInfo,
						onSetValue = { preferenceManager.nowPlayingSongInfo = it }
					)

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

private val nowPlayingUpNextCountOptions = listOf(1, 2, 3, 5)
