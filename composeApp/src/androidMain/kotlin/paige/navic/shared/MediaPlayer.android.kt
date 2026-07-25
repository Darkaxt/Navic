package paige.navic.shared

import android.app.Application
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mappers.toEntity
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.AndroidScrobbleManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.NavidromeAvailability
import paige.navic.domain.manager.NavidromeAvailabilityManager
import paige.navic.domain.manager.PlaybackOriginCredit
import paige.navic.domain.manager.PlaybackOriginTracker
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.CollectionShuffleQueueOrder
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.activeArtworkUrl
import paige.navic.domain.models.collectionShufflePlaybackPlan
import paige.navic.domain.models.externalFallbackArtworkUrl
import paige.navic.domain.models.discoverQueueRemovalIndexes
import paige.navic.domain.models.audioReverbPresetValue
import paige.navic.domain.models.audioFadeDurationMs
import paige.navic.domain.models.bassBoostStrengthPermille
import paige.navic.domain.models.medleyModeDurationMs
import paige.navic.domain.models.normalizedPlaybackPitch
import paige.navic.domain.models.normalizedPlaybackSpeed
import paige.navic.domain.models.pauseBetweenSongsDelayMs
import paige.navic.domain.models.playbackVolumeMultiplier
import paige.navic.domain.models.mediaNotificationActions
import paige.navic.domain.models.queueAutoFillAppendCount
import paige.navic.domain.models.queueAutoFillCandidateSongs
import paige.navic.domain.models.QueueAutoFillRemainingTrigger
import paige.navic.domain.models.replayGainLoudnessBoostMillibels
import paige.navic.domain.models.replayGainVolumeMultiplier
import paige.navic.domain.models.shouldEnableBassBoost
import paige.navic.domain.models.shouldEnableAudioReverb
import paige.navic.domain.models.shouldAutoFillQueue
import paige.navic.domain.models.shouldAdvanceMedleyMode
import paige.navic.domain.models.shouldPauseBetweenSongsAfterTransition
import paige.navic.domain.models.shouldPausePlaybackWhenVolumeZero
import paige.navic.domain.models.shouldFadePlaybackCommand
import paige.navic.domain.models.shouldReplaceQueuedMediaItemForDownloadAvailability
import paige.navic.domain.models.shouldRestartCurrentOnPrevious
import paige.navic.domain.models.shouldSendNowPlayingWidgetUpdate
import paige.navic.domain.models.shouldResumePlaybackWhenAudioDeviceAdded
import paige.navic.domain.models.shouldResumePlaybackAfterVolumeRestored
import paige.navic.domain.models.songRadioQueue
import paige.navic.domain.models.SongRadioQueueDefaultSize
import paige.navic.domain.models.settings.AutoFillQueueSource
import paige.navic.domain.models.settings.MediaNotificationAction
import paige.navic.domain.models.systemEqualizerAudioSessionId
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger
import paige.navic.util.core.ResourceProvider
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PlaybackService : MediaSessionService(), KoinComponent {
	private var mediaSession: MediaSession? = null
	private val serviceScope = MainScope()
	private var scrobbleManager: AndroidScrobbleManager? = null
	private val resourceProvider: ResourceProvider by inject()
	private var audioManager: AudioManager? = null
	private var audioDeviceCallback: AudioDeviceCallback? = null
	private var volumeZeroObserver: ContentObserver? = null
	private var pauseBetweenSongsJob: Job? = null
	private var medleyModeJob: Job? = null
	private var exoPlayer: ExoPlayer? = null
	private var bassBoost: BassBoost? = null
	private var bassBoostAudioSessionId: Int? = null
	private var reverb: PresetReverb? = null
	private var reverbAudioSessionId: Int? = null
	private var replayGainLoudnessEnhancer: LoudnessEnhancer? = null
	private var replayGainLoudnessAudioSessionId: Int? = null
	private var replayGainLoudnessTargetGainMillibels: Int? = null

	private val connectivityManager: ConnectivityManager by inject()

	private val syncManager: SyncManager by inject()
	private val sessionManager: SessionManager by inject()
	private val preferenceManager: PreferenceManager by inject()
	private val navidromeAvailabilityManager: NavidromeAvailabilityManager by inject()

	@OptIn(UnstableApi::class)
	override fun onCreate() {
		super.onCreate()
		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				/* minBufferMs = */ 32_000,
				/* maxBufferMs = */ 64_000,
				/* bufferForPlaybackMs = */ 2_500,
				/* bufferForPlaybackAfterRebufferMs = */ 5_000
			)
			.setBackBuffer(10_000, true)
			.build()

		val defaultNotificationProvider = DefaultMediaNotificationProvider.Builder(this)
			.build().apply {
				setSmallIcon(resourceProvider.icNavic)
		}
		val notificationProvider = OfflineAwareMediaNotificationProvider(
			context = this,
			delegate = defaultNotificationProvider
		)
		serviceScope.launch {
			navidromeAvailabilityManager.state.collectLatest { availability ->
				notificationProvider.setConnectionLost(
					availability is NavidromeAvailability.Unavailable
				)
			}
		}

		val httpDataSourceFactory = DefaultHttpDataSource.Factory()
			.setDefaultRequestProperties(preferenceManager.serverRequestHeadersMap())
		val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

		val player = ExoPlayer.Builder(this)
			.setLoadControl(loadControl)
			.setMediaSourceFactory(mediaSourceFactory)
			.setHandleAudioBecomingNoisy(true)
			.setWakeMode(C.WAKE_MODE_NETWORK)
			.build()
			.apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
						.build(),
					preferenceManager.respectAudioFocus
				)
				skipSilenceEnabled = preferenceManager.skipSilence
				setMediaNotificationProvider(notificationProvider)
				trackSelectionParameters =
					trackSelectionParameters.buildUpon().setAudioOffloadPreferences(
						TrackSelectionParameters.AudioOffloadPreferences
							.Builder()
							.setIsGaplessSupportRequired(preferenceManager.gaplessPlayback)
							.setAudioOffloadMode(
								if (preferenceManager.audioOffload) {
									TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
								} else {
									TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
								}
							)
							.build()
					).build()
			}
		exoPlayer = player

		registerAudioEffectsListener(player)
		registerPauseBetweenSongsListener(player)
		startMedleyModeLoop(player)
		registerAudioDeviceCallback(player)
		registerVolumeZeroObserver(player)

		scrobbleManager =
			AndroidScrobbleManager(player, serviceScope, connectivityManager, syncManager, sessionManager, preferenceManager)

		val sessionIntent = applicationContext.packageManager
			.getLaunchIntentForPackage(applicationContext.packageName)
			?.apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
					Intent.FLAG_ACTIVITY_CLEAR_TOP
			}

		val sessionPendingIntent = PendingIntent.getActivity(
			this,
			0,
			sessionIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		mediaSession = MediaSession.Builder(this, player)
			.setSessionActivity(sessionPendingIntent)
			.setCallback(PlaybackSessionCallback(player))
			.build()
			.also { session ->
				registerMediaNotificationActionListener(session, player)
				updateMediaNotificationActions(session, player)
			}
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
		return mediaSession
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_REFRESH_AUDIO_EFFECTS -> applyAudioEffects()
			ACTION_SET_REPLAY_GAIN_LOUDNESS_BOOST -> {
				replayGainLoudnessTargetGainMillibels = if (
					intent.getBooleanExtra(EXTRA_REPLAY_GAIN_LOUDNESS_ENABLED, false)
				) {
					intent.getIntExtra(EXTRA_REPLAY_GAIN_LOUDNESS_TARGET_GAIN_MB, 0)
				} else {
					null
				}
				applyReplayGainLoudnessBoost()
			}
		}
		return super.onStartCommand(intent, flags, startId)
	}

	override fun onTaskRemoved(rootIntent: Intent?) {
		if (exoPlayer?.isPlaying != true) {
			stopSelf()
		}
	}

	override fun onDestroy() {
		unregisterAudioDeviceCallback()
		unregisterVolumeZeroObserver()
		scrobbleManager?.release()
		releaseAudioEffects()
		serviceScope.cancel()
		stopForeground(STOP_FOREGROUND_REMOVE)
		mediaSession?.run {
			player.stop()
			player.release()
			release()
		}
		super.onDestroy()
		mediaSession = null
		exoPlayer = null
	}

	private fun registerAudioEffectsListener(player: ExoPlayer) {
		player.addListener(object : Player.Listener {
			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				applyAudioEffects(player)
			}
		})
		applyAudioEffects(player)
	}

	private fun applyAudioEffects(player: ExoPlayer? = exoPlayer) {
		applyReplayGainLoudnessBoost(player)
		applyBassBoost(player)
		applyReverb(player)
	}

	private fun applyReplayGainLoudnessBoost(player: ExoPlayer? = exoPlayer) {
		val targetGain = replayGainLoudnessTargetGainMillibels
		val audioSessionId = player?.audioSessionId
		if (targetGain == null || audioSessionId == null || audioSessionId <= 0) {
			releaseReplayGainLoudnessBoost()
			return
		}

		runCatching {
			if (replayGainLoudnessAudioSessionId != audioSessionId) {
				releaseReplayGainLoudnessBoost()
				replayGainLoudnessEnhancer = LoudnessEnhancer(audioSessionId)
				replayGainLoudnessAudioSessionId = audioSessionId
			}
			replayGainLoudnessEnhancer?.setTargetGain(targetGain)
			replayGainLoudnessEnhancer?.enabled = true
		}.onFailure { error ->
			Logger.w("PlaybackService", "ReplayGain loudness boost unavailable", error)
			releaseReplayGainLoudnessBoost()
		}
	}

	private fun releaseReplayGainLoudnessBoost() {
		runCatching {
			replayGainLoudnessEnhancer?.enabled = false
			replayGainLoudnessEnhancer?.release()
		}.onFailure { error ->
			Logger.w("PlaybackService", "Failed to release ReplayGain loudness boost", error)
		}
		replayGainLoudnessEnhancer = null
		replayGainLoudnessAudioSessionId = null
	}

	private fun applyBassBoost(player: ExoPlayer?) {
		val audioSessionId = player?.audioSessionId
		if (
			!shouldEnableBassBoost(
				bassBoostEnabled = preferenceManager.bassBoostEnabled,
				audioSessionId = audioSessionId
			)
		) {
			releaseBassBoost()
			return
		}

		val sessionId = audioSessionId ?: return
		runCatching {
			if (bassBoostAudioSessionId != sessionId) {
				releaseBassBoost()
				bassBoost = BassBoost(0, sessionId)
				bassBoostAudioSessionId = sessionId
			}
			bassBoost?.setStrength(bassBoostStrengthPermille(preferenceManager.bassBoostStrength))
			bassBoost?.enabled = true
		}.onFailure { error ->
			Logger.w("PlaybackService", "Bass boost unavailable", error)
			releaseBassBoost()
		}
	}

	private fun releaseBassBoost() {
		runCatching {
			bassBoost?.enabled = false
			bassBoost?.release()
		}.onFailure { error ->
			Logger.w("PlaybackService", "Failed to release bass boost", error)
		}
		bassBoost = null
		bassBoostAudioSessionId = null
	}

	private fun applyReverb(player: ExoPlayer?) {
		val audioSessionId = player?.audioSessionId
		val preset = preferenceManager.audioReverbPreset
		if (!shouldEnableAudioReverb(preset, audioSessionId)) {
			releaseReverb(player)
			return
		}

		val sessionId = audioSessionId ?: return
		runCatching {
			if (reverbAudioSessionId != sessionId) {
				releaseReverb(player)
				reverb = PresetReverb(1, sessionId)
				reverbAudioSessionId = sessionId
			}
			val effect = reverb ?: return
			effect.preset = audioReverbPresetValue(preset)
			effect.enabled = true
			player.setAuxEffectInfo(AuxEffectInfo(effect.id, 1f))
		}.onFailure { error ->
			Logger.w("PlaybackService", "Reverb unavailable", error)
			releaseReverb(player)
		}
	}

	private fun releaseReverb(player: ExoPlayer? = exoPlayer) {
		runCatching {
			player?.clearAuxEffectInfo()
			reverb?.enabled = false
			reverb?.release()
		}.onFailure { error ->
			Logger.w("PlaybackService", "Failed to release reverb", error)
		}
		reverb = null
		reverbAudioSessionId = null
	}

	private fun releaseAudioEffects(player: ExoPlayer? = exoPlayer) {
		releaseReplayGainLoudnessBoost()
		releaseBassBoost()
		releaseReverb(player)
	}

	private fun registerAudioDeviceCallback(player: ExoPlayer) {
		if (!preferenceManager.resumePlaybackOnAudioDeviceConnect) return

		audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
		audioDeviceCallback = object : AudioDeviceCallback() {
			override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
				if (
					shouldResumePlaybackWhenAudioDeviceAdded(
						resumePlaybackOnAudioDeviceConnect = preferenceManager.resumePlaybackOnAudioDeviceConnect,
						isPlaying = player.isPlaying,
						hasMediaItems = player.mediaItemCount > 0,
						hasPlayableDevice = addedDevices.any { it.canPlayMusic() }
					)
				) {
					player.play()
				}
			}
		}
		audioManager?.registerAudioDeviceCallback(
			audioDeviceCallback,
			Handler(Looper.getMainLooper())
		)
	}

	private fun unregisterAudioDeviceCallback() {
		audioDeviceCallback?.let { callback ->
			audioManager?.unregisterAudioDeviceCallback(callback)
		}
		audioDeviceCallback = null
		audioManager = null
	}

	private fun registerPauseBetweenSongsListener(player: ExoPlayer) {
		player.addListener(object : Player.Listener {
			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				if (
					!shouldPauseBetweenSongsAfterTransition(
						pauseBetweenSongsSeconds = preferenceManager.pauseBetweenSongsSeconds,
						isAutomaticTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
						isPlaying = player.isPlaying,
						hasMediaItem = mediaItem != null
					)
				) {
					return
				}

				pauseBetweenSongsJob?.cancel()
				val mediaItemIndex = player.currentMediaItemIndex
				val delayMs = pauseBetweenSongsDelayMs(preferenceManager.pauseBetweenSongsSeconds)

				logPlaybackServiceDiagnostic("pause-between-songs-paused", player, "delayMs" to delayMs)
				player.pause()
				pauseBetweenSongsJob = serviceScope.launch {
					delay(delayMs)
					if (
						player.currentMediaItemIndex == mediaItemIndex &&
						player.mediaItemCount > 0 &&
						player.playbackState != Player.STATE_ENDED
					) {
						logPlaybackServiceDiagnostic("pause-between-songs-resumed", player, "delayMs" to delayMs)
						player.play()
					}
				}
			}
		})
	}

	private fun startMedleyModeLoop(player: ExoPlayer) {
		medleyModeJob?.cancel()
		medleyModeJob = serviceScope.launch {
			var advancedMediaItemIndex: Int? = null
			while (true) {
				val durationMs = medleyModeDurationMs(preferenceManager.medleyModeSeconds)
				val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
				if (durationMs <= 0L || currentPositionMs < durationMs) {
					advancedMediaItemIndex = null
				}

				val currentIndex = player.currentMediaItemIndex
				if (
					shouldAdvanceMedleyMode(
						medleyModeSeconds = preferenceManager.medleyModeSeconds,
						isPlaying = player.isPlaying,
						hasNextMediaItem = player.hasNextMediaItem(),
						currentPositionMs = currentPositionMs,
						alreadyAdvancedCurrentItem = advancedMediaItemIndex == currentIndex
					)
				) {
					advancedMediaItemIndex = currentIndex
					player.seekToNextMediaItem()
				}

				delay(500.milliseconds)
			}
		}
	}

	private fun registerVolumeZeroObserver(player: ExoPlayer) {
		if (!preferenceManager.pausePlaybackOnVolumeZero) return

		val manager = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
		val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
			private var pausedByZeroVolume = false

			override fun onChange(selfChange: Boolean) {
				val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
				if (
					shouldPausePlaybackWhenVolumeZero(
						pausePlaybackOnVolumeZero = preferenceManager.pausePlaybackOnVolumeZero,
						isPlaying = player.isPlaying,
						volume = volume
					)
				) {
					logPlaybackServiceDiagnostic("volume-zero-paused", player, "volume" to volume)
					player.pause()
					pausedByZeroVolume = true
				} else if (
					shouldResumePlaybackAfterVolumeRestored(
						pausePlaybackOnVolumeZero = preferenceManager.pausePlaybackOnVolumeZero,
						pausedByZeroVolume = pausedByZeroVolume,
						volume = volume
					)
				) {
					logPlaybackServiceDiagnostic("volume-restored-resumed", player, "volume" to volume)
					player.play()
					pausedByZeroVolume = false
				}
			}
		}
		volumeZeroObserver = observer
		contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
	}

	private fun unregisterVolumeZeroObserver() {
		volumeZeroObserver?.let(contentResolver::unregisterContentObserver)
		volumeZeroObserver = null
	}

	private fun AudioDeviceInfo.canPlayMusic(): Boolean {
		if (!isSink) return false

		return when (type) {
			AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
			AudioDeviceInfo.TYPE_WIRED_HEADSET,
			AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
			AudioDeviceInfo.TYPE_USB_HEADSET -> true
			else -> false
		}
	}

	@OptIn(UnstableApi::class)
	private fun registerMediaNotificationActionListener(session: MediaSession, player: Player) {
		player.addListener(object : Player.Listener {
			override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
				updateMediaNotificationActions(session, player)
			}

			override fun onRepeatModeChanged(repeatMode: Int) {
				updateMediaNotificationActions(session, player)
			}
		})
	}

	@OptIn(UnstableApi::class)
	private fun updateMediaNotificationActions(session: MediaSession, player: Player) {
		val buttons = mediaNotificationActions(
			firstAction = preferenceManager.mediaNotificationFirstAction,
			secondAction = preferenceManager.mediaNotificationSecondAction
		).map { action ->
			action.toMediaNotificationButton(player)
		}

		session.setMediaButtonPreferences(buttons)
	}

	@OptIn(UnstableApi::class)
	private fun MediaNotificationAction.toMediaNotificationButton(player: Player): CommandButton =
		when (this) {
			MediaNotificationAction.Disabled -> error("Disabled notification actions are filtered before button creation")
			MediaNotificationAction.Shuffle -> CommandButton.Builder(
				if (player.shuffleModeEnabled) {
					CommandButton.ICON_SHUFFLE_ON
				} else {
					CommandButton.ICON_SHUFFLE_OFF
				}
			)
				.setDisplayName("Shuffle")
				.setSessionCommand(toggleShuffleCommand)
				.build()
			MediaNotificationAction.Repeat -> CommandButton.Builder(
				when (player.repeatMode) {
					Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
					Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
					else -> CommandButton.ICON_REPEAT_OFF
				}
			)
				.setDisplayName("Repeat")
				.setSessionCommand(toggleRepeatCommand)
				.build()
		}

	private inner class PlaybackSessionCallback(
		private val player: ExoPlayer
	) : MediaSession.Callback {
		override fun onConnect(
			session: MediaSession,
			controllerInfo: MediaSession.ControllerInfo
		): MediaSession.ConnectionResult {
			val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
				.buildUpon()
				.add(toggleShuffleCommand)
				.add(toggleRepeatCommand)
				.add(restoreShuffleOrderCommand)
				.build()

			return MediaSession.ConnectionResult.accept(
				sessionCommands,
				MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
			)
		}

		override fun onCustomCommand(
			session: MediaSession,
			controllerInfo: MediaSession.ControllerInfo,
			customCommand: SessionCommand,
			args: Bundle
		): ListenableFuture<SessionResult> {
			when (customCommand.customAction) {
				ACTION_TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
				ACTION_RESTORE_SHUFFLE_ORDER -> {
					val order = args.getIntArray(EXTRA_SHUFFLE_ORDER)
					if (
						order == null ||
						order.size != player.mediaItemCount ||
						order.toSet() != (0 until player.mediaItemCount).toSet()
					) {
						return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
					}
					player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order, 0L))
				}
				ACTION_TOGGLE_REPEAT -> {
					player.repeatMode = when (player.repeatMode) {
						Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
						Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
						else -> Player.REPEAT_MODE_OFF
					}
				}
				else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
			}

			updateMediaNotificationActions(session, player)
			return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
		}
	}

	companion object {
		private const val ACTION_REFRESH_AUDIO_EFFECTS =
			"paige.navic.shared.action.REFRESH_AUDIO_EFFECTS"
		private const val ACTION_SET_REPLAY_GAIN_LOUDNESS_BOOST =
			"paige.navic.shared.action.SET_REPLAY_GAIN_LOUDNESS_BOOST"
		private const val ACTION_TOGGLE_SHUFFLE =
			"paige.navic.shared.action.TOGGLE_SHUFFLE"
		private const val ACTION_TOGGLE_REPEAT =
			"paige.navic.shared.action.TOGGLE_REPEAT"
		private const val ACTION_RESTORE_SHUFFLE_ORDER =
			"paige.navic.shared.action.RESTORE_SHUFFLE_ORDER"
		private const val EXTRA_SHUFFLE_ORDER =
			"paige.navic.shared.extra.SHUFFLE_ORDER"
		private const val EXTRA_REPLAY_GAIN_LOUDNESS_ENABLED =
			"paige.navic.shared.extra.REPLAY_GAIN_LOUDNESS_ENABLED"
		private const val EXTRA_REPLAY_GAIN_LOUDNESS_TARGET_GAIN_MB =
			"paige.navic.shared.extra.REPLAY_GAIN_LOUDNESS_TARGET_GAIN_MB"
		private val toggleShuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
		private val toggleRepeatCommand = SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY)
		private val restoreShuffleOrderCommand = SessionCommand(ACTION_RESTORE_SHUFFLE_ORDER, Bundle.EMPTY)

		fun newSessionToken(context: Context): SessionToken {
			return SessionToken(context, ComponentName(context, PlaybackService::class.java))
		}

		fun restoreShuffleOrder(
			controller: MediaController,
			order: List<Int>
		): ListenableFuture<SessionResult> = controller.sendCustomCommand(
			restoreShuffleOrderCommand,
			Bundle().apply { putIntArray(EXTRA_SHUFFLE_ORDER, order.toIntArray()) }
		)

		fun refreshAudioEffects(context: Context) {
			context.startService(
				Intent(context, PlaybackService::class.java)
					.setAction(ACTION_REFRESH_AUDIO_EFFECTS)
			)
		}

		fun setReplayGainLoudnessBoost(
			context: Context,
			targetGainMillibels: Int?
		) {
			context.startService(
				Intent(context, PlaybackService::class.java)
					.setAction(ACTION_SET_REPLAY_GAIN_LOUDNESS_BOOST)
					.putExtra(EXTRA_REPLAY_GAIN_LOUDNESS_ENABLED, targetGainMillibels != null)
					.putExtra(EXTRA_REPLAY_GAIN_LOUDNESS_TARGET_GAIN_MB, targetGainMillibels ?: 0)
			)
		}
	}
}
