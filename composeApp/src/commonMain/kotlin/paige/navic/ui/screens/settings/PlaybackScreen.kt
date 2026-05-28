package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_lyrics
import navic.composeapp.generated.resources.action_system_equalizer
import navic.composeapp.generated.resources.option_audio_fade
import navic.composeapp.generated.resources.option_audio_offload
import navic.composeapp.generated.resources.option_audio_reverb
import navic.composeapp.generated.resources.option_auto_fill_queue
import navic.composeapp.generated.resources.option_auto_fill_queue_source
import navic.composeapp.generated.resources.option_auto_fill_queue_target_size
import navic.composeapp.generated.resources.option_bass_boost
import navic.composeapp.generated.resources.option_bass_boost_strength
import navic.composeapp.generated.resources.option_enable_scrobbling
import navic.composeapp.generated.resources.option_gapless_playback
import navic.composeapp.generated.resources.option_lyrics_autoscroll
import navic.composeapp.generated.resources.option_lyrics_accent_background
import navic.composeapp.generated.resources.option_lyrics_beat_by_beat
import navic.composeapp.generated.resources.option_lyrics_blur
import navic.composeapp.generated.resources.option_lyrics_bright_inactive
import navic.composeapp.generated.resources.option_lyrics_alignment
import navic.composeapp.generated.resources.option_lyrics_font_size
import navic.composeapp.generated.resources.option_lyrics_jump_on_tap
import navic.composeapp.generated.resources.option_lyrics_keep_alive
import navic.composeapp.generated.resources.option_lyrics_priority
import navic.composeapp.generated.resources.option_min_duration_to_scrobble
import navic.composeapp.generated.resources.option_medley_mode
import navic.composeapp.generated.resources.option_off
import navic.composeapp.generated.resources.option_now_playing_indicator
import navic.composeapp.generated.resources.option_pause_playback_on_volume_zero
import navic.composeapp.generated.resources.option_pause_between_songs
import navic.composeapp.generated.resources.option_persistent_queue
import navic.composeapp.generated.resources.option_playback_volume
import navic.composeapp.generated.resources.option_playlist_indicator
import navic.composeapp.generated.resources.option_queue_swipe_actions
import navic.composeapp.generated.resources.option_queue_swipe_end_to_start_action
import navic.composeapp.generated.resources.option_queue_swipe_start_to_end_action
import navic.composeapp.generated.resources.option_queue_shuffle_limit
import navic.composeapp.generated.resources.option_replay_gain
import navic.composeapp.generated.resources.option_replay_gain_loudness_boost
import navic.composeapp.generated.resources.option_respect_audio_focus
import navic.composeapp.generated.resources.option_resume_playback_on_audio_device_connect
import navic.composeapp.generated.resources.option_resume_playback_on_startup
import navic.composeapp.generated.resources.option_scrobble_percentage
import navic.composeapp.generated.resources.option_shake_to_skip
import navic.composeapp.generated.resources.option_song_swipe_actions
import navic.composeapp.generated.resources.option_song_swipe_end_to_start_action
import navic.composeapp.generated.resources.option_song_swipe_start_to_end_action
import navic.composeapp.generated.resources.option_smart_rewind
import navic.composeapp.generated.resources.option_skip_media_on_error
import navic.composeapp.generated.resources.option_skip_silence
import navic.composeapp.generated.resources.option_unlimited
import navic.composeapp.generated.resources.option_volume_keys_skip_tracks
import navic.composeapp.generated.resources.subtitle_audio_offload
import navic.composeapp.generated.resources.subtitle_audio_fade
import navic.composeapp.generated.resources.subtitle_audio_reverb
import navic.composeapp.generated.resources.subtitle_auto_fill_queue
import navic.composeapp.generated.resources.subtitle_auto_fill_queue_source
import navic.composeapp.generated.resources.subtitle_auto_fill_queue_target_size
import navic.composeapp.generated.resources.subtitle_bass_boost
import navic.composeapp.generated.resources.subtitle_enable_scrobbling
import navic.composeapp.generated.resources.subtitle_gapless_playback
import navic.composeapp.generated.resources.subtitle_medley_mode
import navic.composeapp.generated.resources.subtitle_now_playing_indicator
import navic.composeapp.generated.resources.subtitle_pause_between_songs
import navic.composeapp.generated.resources.subtitle_pause_playback_on_volume_zero
import navic.composeapp.generated.resources.subtitle_persistent_queue
import navic.composeapp.generated.resources.subtitle_playback_volume
import navic.composeapp.generated.resources.subtitle_playlist_indicator
import navic.composeapp.generated.resources.subtitle_queue_swipe_actions
import navic.composeapp.generated.resources.subtitle_queue_swipe_end_to_start_action
import navic.composeapp.generated.resources.subtitle_queue_swipe_start_to_end_action
import navic.composeapp.generated.resources.subtitle_queue_shuffle_limit
import navic.composeapp.generated.resources.subtitle_replay_gain_loudness_boost
import navic.composeapp.generated.resources.subtitle_respect_audio_focus
import navic.composeapp.generated.resources.subtitle_resume_playback_on_audio_device_connect
import navic.composeapp.generated.resources.subtitle_resume_playback_on_startup
import navic.composeapp.generated.resources.subtitle_shake_to_skip
import navic.composeapp.generated.resources.subtitle_song_swipe_actions
import navic.composeapp.generated.resources.subtitle_song_swipe_end_to_start_action
import navic.composeapp.generated.resources.subtitle_song_swipe_start_to_end_action
import navic.composeapp.generated.resources.subtitle_smart_rewind
import navic.composeapp.generated.resources.subtitle_skip_media_on_error
import navic.composeapp.generated.resources.subtitle_skip_silence
import navic.composeapp.generated.resources.subtitle_streaming_quality
import navic.composeapp.generated.resources.subtitle_system_equalizer
import navic.composeapp.generated.resources.subtitle_volume_keys_skip_tracks
import navic.composeapp.generated.resources.title_behaviour
import navic.composeapp.generated.resources.title_playback
import navic.composeapp.generated.resources.title_streaming_quality
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.AutoFillQueueSource
import paige.navic.domain.models.settings.AudioReverbPreset
import paige.navic.domain.models.settings.LyricsAlignment
import paige.navic.domain.models.settings.LyricsFontSize
import paige.navic.domain.models.settings.QueueSwipeAction
import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.domain.models.settings.SongSwipeAction
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ChevronForward
import paige.navic.icons.outlined.Equalizer
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.dialogs.LyricsPriorityDialog
import kotlin.math.roundToInt

@Composable
fun SettingsPlaybackScreen() {
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	var showLyricsPriorityDialog by rememberSaveable { mutableStateOf(false) }
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_playback)) },
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
					FormRow(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.StreamingQuality) },
						horizontalArrangement = Arrangement.Start
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.title_streaming_quality))
							Text(
								text = stringResource(Res.string.subtitle_streaming_quality),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Icon(Icons.Outlined.ChevronForward, null)
					}
					if (!listOf("ipados", "ios").contains(platformContext.name.lowercase())) {
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_replay_gain)) },
							items = ReplayGainMode.entries.toImmutableList(),
							label = { stringResource(it.displayName) },
							selection = preferenceManager.replayGainMode,
							onSelect = {
								preferenceManager.replayGainMode = it
								player.refreshAudioEffects()
							}
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_replay_gain_loudness_boost)) },
							subtitle = { Text(stringResource(Res.string.subtitle_replay_gain_loudness_boost)) },
							value = preferenceManager.replayGainLoudnessBoost,
							onSetValue = {
								preferenceManager.replayGainLoudnessBoost = it
								player.refreshAudioEffects()
							}
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_gapless_playback)) },
							subtitle = { Text(stringResource(Res.string.subtitle_gapless_playback)) },
							value = preferenceManager.gaplessPlayback,
							onSetValue = { preferenceManager.gaplessPlayback = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_respect_audio_focus)) },
							subtitle = { Text(stringResource(Res.string.subtitle_respect_audio_focus)) },
							value = preferenceManager.respectAudioFocus,
							onSetValue = { preferenceManager.respectAudioFocus = it }
						)
						FormRow {
							Column(Modifier.fillMaxWidth()) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween
								) {
									Column(Modifier.weight(1f)) {
										Text(stringResource(Res.string.option_playback_volume))
										Text(
											text = stringResource(Res.string.subtitle_playback_volume),
											style = MaterialTheme.typography.bodyMedium,
											color = MaterialTheme.colorScheme.onSurfaceVariant
										)
									}
									Text(
										"${preferenceManager.playbackVolumePercent}%",
										modifier = Modifier.padding(start = 16.dp),
										fontFamily = FontFamily.Monospace,
										fontWeight = FontWeight(400),
										fontSize = 13.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
								Slider(
									value = preferenceManager.playbackVolumePercent.toFloat(),
									onValueChange = { value ->
										preferenceManager.playbackVolumePercent = value.roundToInt()
										player.refreshPlaybackVolume()
									},
									valueRange = 0f..100f,
								)
							}
						}
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_skip_silence)) },
							subtitle = { Text(stringResource(Res.string.subtitle_skip_silence)) },
							value = preferenceManager.skipSilence,
							onSetValue = { preferenceManager.skipSilence = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_skip_media_on_error)) },
							subtitle = { Text(stringResource(Res.string.subtitle_skip_media_on_error)) },
							value = preferenceManager.skipMediaOnError,
							onSetValue = { preferenceManager.skipMediaOnError = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_resume_playback_on_audio_device_connect)) },
							subtitle = { Text(stringResource(Res.string.subtitle_resume_playback_on_audio_device_connect)) },
							value = preferenceManager.resumePlaybackOnAudioDeviceConnect,
							onSetValue = { preferenceManager.resumePlaybackOnAudioDeviceConnect = it }
						)
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_pause_between_songs)) },
							items = pauseBetweenSongsOptions.toImmutableList(),
							label = { seconds ->
								if (seconds == 0) {
									stringResource(Res.string.option_off)
								} else {
									"${seconds}s"
								}
							},
							description = stringResource(Res.string.subtitle_pause_between_songs),
							selection = preferenceManager.pauseBetweenSongsSeconds,
							onSelect = { preferenceManager.pauseBetweenSongsSeconds = it }
						)
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_medley_mode)) },
							items = medleyModeOptions.toImmutableList(),
							label = { seconds ->
								if (seconds == 0) {
									stringResource(Res.string.option_off)
								} else {
									"${seconds}s"
								}
							},
							description = stringResource(Res.string.subtitle_medley_mode),
							selection = preferenceManager.medleyModeSeconds,
							onSelect = { preferenceManager.medleyModeSeconds = it }
						)
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_smart_rewind)) },
							items = smartRewindOptions.toImmutableList(),
							label = { "${it}s" },
							description = stringResource(Res.string.subtitle_smart_rewind),
							selection = preferenceManager.smartRewindSeconds,
							onSelect = { preferenceManager.smartRewindSeconds = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_pause_playback_on_volume_zero)) },
							subtitle = { Text(stringResource(Res.string.subtitle_pause_playback_on_volume_zero)) },
							value = preferenceManager.pausePlaybackOnVolumeZero,
							onSetValue = { preferenceManager.pausePlaybackOnVolumeZero = it }
						)
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_audio_fade)) },
							items = audioFadeDurationOptions.toImmutableList(),
							label = { durationMs ->
								when {
									durationMs == 0 -> stringResource(Res.string.option_off)
									durationMs < 1000 -> "${durationMs}ms"
									else -> "${durationMs / 1000}s"
								}
							},
							description = stringResource(Res.string.subtitle_audio_fade),
							selection = preferenceManager.audioFadeDurationMs,
							onSelect = { preferenceManager.audioFadeDurationMs = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_bass_boost)) },
							subtitle = { Text(stringResource(Res.string.subtitle_bass_boost)) },
							value = preferenceManager.bassBoostEnabled,
							onSetValue = {
								preferenceManager.bassBoostEnabled = it
								player.refreshAudioEffects()
							}
						)
						AnimatedVisibility(preferenceManager.bassBoostEnabled) {
							FormRow {
								Column(Modifier.fillMaxWidth()) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Text(stringResource(Res.string.option_bass_boost_strength))
										Text(
											"${preferenceManager.bassBoostStrength / 10}%",
											fontFamily = FontFamily.Monospace,
											fontWeight = FontWeight(400),
											fontSize = 13.sp,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
										)
									}
									Slider(
										value = preferenceManager.bassBoostStrength.toFloat(),
										onValueChange = { value ->
											preferenceManager.bassBoostStrength = value.roundToInt()
											player.refreshAudioEffects()
										},
										valueRange = 0f..1000f,
									)
								}
							}
						}
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_audio_reverb)) },
							items = AudioReverbPreset.entries.toImmutableList(),
							label = { stringResource(it.displayName) },
							description = stringResource(Res.string.subtitle_audio_reverb),
							selection = preferenceManager.audioReverbPreset,
							onSelect = {
								preferenceManager.audioReverbPreset = it
								player.refreshAudioEffects()
							}
						)
						FormRow(
							onClick = {
								player.openSystemEqualizer()
							},
							horizontalArrangement = Arrangement.spacedBy(14.dp)
						) {
							Icon(Icons.Outlined.Equalizer, null)
							Column(Modifier.weight(1f)) {
								Text(stringResource(Res.string.action_system_equalizer))
								Text(
									text = stringResource(Res.string.subtitle_system_equalizer),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_auto_fill_queue)) },
							subtitle = { Text(stringResource(Res.string.subtitle_auto_fill_queue)) },
							value = preferenceManager.autoFillQueue,
							onSetValue = { preferenceManager.autoFillQueue = it }
						)
						AnimatedVisibility(preferenceManager.autoFillQueue) {
							Column {
								SettingSelectionRow(
									title = { Text(stringResource(Res.string.option_auto_fill_queue_target_size)) },
									items = autoFillQueueTargetSizeOptions.toImmutableList(),
									label = { "$it songs" },
									description = stringResource(Res.string.subtitle_auto_fill_queue_target_size),
									selection = preferenceManager.autoFillQueueTargetSize,
									onSelect = { preferenceManager.autoFillQueueTargetSize = it }
								)
								SettingSelectionRow(
									title = { Text(stringResource(Res.string.option_auto_fill_queue_source)) },
									items = AutoFillQueueSource.entries.toImmutableList(),
									label = { stringResource(it.displayName) },
									description = stringResource(Res.string.subtitle_auto_fill_queue_source),
									selection = preferenceManager.autoFillQueueSource,
									onSelect = { preferenceManager.autoFillQueueSource = it }
								)
							}
						}
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_queue_shuffle_limit)) },
							items = queueShuffleLimitOptions.toImmutableList(),
							label = { limit ->
								if (limit == 0) {
									stringResource(Res.string.option_unlimited)
								} else {
									"$limit songs"
								}
							},
							description = stringResource(Res.string.subtitle_queue_shuffle_limit),
							selection = preferenceManager.queueShuffleLimit,
							onSelect = { preferenceManager.queueShuffleLimit = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_now_playing_indicator)) },
							subtitle = { Text(stringResource(Res.string.subtitle_now_playing_indicator)) },
							value = preferenceManager.showNowPlayingIndicator,
							onSetValue = { preferenceManager.showNowPlayingIndicator = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_playlist_indicator)) },
							subtitle = { Text(stringResource(Res.string.subtitle_playlist_indicator)) },
							value = preferenceManager.showPlaylistIndicator,
							onSetValue = { preferenceManager.showPlaylistIndicator = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_song_swipe_actions)) },
							subtitle = { Text(stringResource(Res.string.subtitle_song_swipe_actions)) },
							value = preferenceManager.songSwipeActionsEnabled,
							onSetValue = { preferenceManager.songSwipeActionsEnabled = it }
						)
						AnimatedVisibility(preferenceManager.songSwipeActionsEnabled) {
							Column {
								SettingSelectionRow(
									title = { Text(stringResource(Res.string.option_song_swipe_start_to_end_action)) },
									items = SongSwipeAction.entries.toImmutableList(),
									label = { stringResource(it.displayName) },
									description = stringResource(Res.string.subtitle_song_swipe_start_to_end_action),
									selection = preferenceManager.songSwipeStartToEndAction,
									onSelect = { preferenceManager.songSwipeStartToEndAction = it }
								)
								SettingSelectionRow(
									title = { Text(stringResource(Res.string.option_song_swipe_end_to_start_action)) },
									items = SongSwipeAction.entries.toImmutableList(),
									label = { stringResource(it.displayName) },
									description = stringResource(Res.string.subtitle_song_swipe_end_to_start_action),
									selection = preferenceManager.songSwipeEndToStartAction,
									onSelect = { preferenceManager.songSwipeEndToStartAction = it }
								)
							}
						}
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_queue_swipe_actions)) },
							subtitle = { Text(stringResource(Res.string.subtitle_queue_swipe_actions)) },
							value = preferenceManager.queueSwipeActionsEnabled,
							onSetValue = { preferenceManager.queueSwipeActionsEnabled = it }
						)
						AnimatedVisibility(preferenceManager.queueSwipeActionsEnabled) {
							Column {
								SettingSelectionRow(
									title = { Text(stringResource(Res.string.option_queue_swipe_start_to_end_action)) },
									items = QueueSwipeAction.entries.toImmutableList(),
									label = { stringResource(it.displayName) },
									description = stringResource(Res.string.subtitle_queue_swipe_start_to_end_action),
									selection = preferenceManager.queueSwipeStartToEndAction,
									onSelect = { preferenceManager.queueSwipeStartToEndAction = it }
								)
								SettingSelectionRow(
									title = { Text(stringResource(Res.string.option_queue_swipe_end_to_start_action)) },
									items = QueueSwipeAction.entries.toImmutableList(),
									label = { stringResource(it.displayName) },
									description = stringResource(Res.string.subtitle_queue_swipe_end_to_start_action),
									selection = preferenceManager.queueSwipeEndToStartAction,
									onSelect = { preferenceManager.queueSwipeEndToStartAction = it }
								)
							}
						}
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_shake_to_skip)) },
							subtitle = { Text(stringResource(Res.string.subtitle_shake_to_skip)) },
							value = preferenceManager.shakeToSkip,
							onSetValue = { preferenceManager.shakeToSkip = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_volume_keys_skip_tracks)) },
							subtitle = { Text(stringResource(Res.string.subtitle_volume_keys_skip_tracks)) },
							value = preferenceManager.volumeKeysSkipTracks,
							onSetValue = { preferenceManager.volumeKeysSkipTracks = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_persistent_queue)) },
							subtitle = { Text(stringResource(Res.string.subtitle_persistent_queue)) },
							value = preferenceManager.persistentQueue,
							onSetValue = {
								preferenceManager.persistentQueue = it
								if (!it) preferenceManager.resumePlaybackOnStartup = false
							}
						)
						AnimatedVisibility(preferenceManager.persistentQueue) {
							SettingSwitchRow(
								title = { Text(stringResource(Res.string.option_resume_playback_on_startup)) },
								subtitle = { Text(stringResource(Res.string.subtitle_resume_playback_on_startup)) },
								value = preferenceManager.resumePlaybackOnStartup,
								onSetValue = { preferenceManager.resumePlaybackOnStartup = it }
							)
						}
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_audio_offload)) },
							subtitle = { Text(stringResource(Res.string.subtitle_audio_offload)) },
							value = preferenceManager.audioOffload,
							onSetValue = { preferenceManager.audioOffload = it }
						)
					}
				}

				FormTitle(stringResource(Res.string.action_lyrics))
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lyrics_autoscroll)) },
						value = preferenceManager.lyricsAutoscroll,
						onSetValue = { preferenceManager.lyricsAutoscroll = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lyrics_beat_by_beat)) },
						value = preferenceManager.lyricsBeatByBeat,
						onSetValue = { preferenceManager.lyricsBeatByBeat = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lyrics_keep_alive)) },
						value = preferenceManager.lyricsKeepAlive,
						onSetValue = { preferenceManager.lyricsKeepAlive = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lyrics_blur)) },
						value = preferenceManager.lyricsBlur,
						onSetValue = { preferenceManager.lyricsBlur = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lyrics_bright_inactive)) },
						value = preferenceManager.lyricsBrightInactive,
						onSetValue = { preferenceManager.lyricsBrightInactive = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lyrics_accent_background)) },
						value = preferenceManager.lyricsAccentBackground,
						onSetValue = { preferenceManager.lyricsAccentBackground = it }
					)

					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lyrics_jump_on_tap)) },
						value = preferenceManager.lyricsJumpOnTap,
						onSetValue = { preferenceManager.lyricsJumpOnTap = it }
					)

					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_lyrics_font_size)) },
						items = LyricsFontSize.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.lyricsFontSize,
						onSelect = { preferenceManager.lyricsFontSize = it }
					)

					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_lyrics_alignment)) },
						items = LyricsAlignment.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.lyricsAlignment,
						onSelect = { preferenceManager.lyricsAlignment = it }
					)

					FormRow(
						onClick = { showLyricsPriorityDialog = true }
					) {
						Text(stringResource(Res.string.option_lyrics_priority))
					}
				}

				FormTitle(stringResource(Res.string.title_behaviour))
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_enable_scrobbling)) },
						subtitle = { Text(stringResource(Res.string.subtitle_enable_scrobbling)) },
						value = preferenceManager.enableScrobbling,
						onSetValue = { preferenceManager.enableScrobbling = it }
					)

					FormRow {
						Column(Modifier.fillMaxWidth()) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text(stringResource(Res.string.option_scrobble_percentage))
								Text(
									"${(preferenceManager.scrobblePercentage * 100).roundToInt()}%",
									fontFamily = FontFamily.Monospace,
									fontWeight = FontWeight(400),
									fontSize = 13.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Slider(
								value = preferenceManager.scrobblePercentage,
								onValueChange = {
									preferenceManager.scrobblePercentage = it
								},
								valueRange = 0f..1f,
							)
						}
					}
					FormRow {
						Column(Modifier.fillMaxWidth()) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text(stringResource(Res.string.option_min_duration_to_scrobble))
								Text(
									"${preferenceManager.minDurationToScrobble.toInt()}s",
									fontFamily = FontFamily.Monospace,
									fontWeight = FontWeight(400),
									fontSize = 13.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Slider(
								value = preferenceManager.minDurationToScrobble,
								onValueChange = {
									preferenceManager.minDurationToScrobble = it
								},
								valueRange = 0f..400f,
							)
						}
					}
				}
			}
		}
		LyricsPriorityDialog(
			presented = showLyricsPriorityDialog,
			onDismissRequest = { showLyricsPriorityDialog = false }
		)
	}
}

private val pauseBetweenSongsOptions = listOf(0, 5, 10, 15, 20, 30, 40, 50, 60)
private val medleyModeOptions = listOf(0, 15, 30, 45, 60)
private val smartRewindOptions = listOf(1, 2, 3, 5, 10, 15, 30)
private val audioFadeDurationOptions = listOf(0, 250, 500, 1000, 2000)
private val autoFillQueueTargetSizeOptions = listOf(10, 25, 50, 100)
private val queueShuffleLimitOptions = listOf(0, 50, 100, 200, 500, 1000, 2000, 3000)
