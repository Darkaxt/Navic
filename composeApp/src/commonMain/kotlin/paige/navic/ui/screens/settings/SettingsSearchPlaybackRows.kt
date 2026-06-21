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
internal fun settingsSearchStreamingRows(context: SettingsSearchContext): List<SearchableSettingsRow> = with(context) {
	buildList {
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

	}
}

@Composable
internal fun settingsSearchPlaybackRows(context: SettingsSearchContext): List<SearchableSettingsRow> = with(context) {
	buildList {
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
				label = { if (it < 0) stringResource(Res.string.option_off) else "${it}s" },
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

	}
}