package paige.navic.shared

import android.app.Application
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
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
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.mappers.toEntity
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.AndroidScrobbleManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.audioReverbPresetValue
import paige.navic.domain.models.audioFadeDurationMs
import paige.navic.domain.models.bassBoostStrengthPermille
import paige.navic.domain.models.normalizedPlaybackPitch
import paige.navic.domain.models.normalizedPlaybackSpeed
import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.domain.models.pauseBetweenSongsDelayMs
import paige.navic.domain.models.queueAutoFillAppendCount
import paige.navic.domain.models.queueAutoFillCandidateSongs
import paige.navic.domain.models.QueueAutoFillRemainingTrigger
import paige.navic.domain.models.shouldEnableBassBoost
import paige.navic.domain.models.shouldEnableAudioReverb
import paige.navic.domain.models.shouldAutoFillQueue
import paige.navic.domain.models.shouldPauseBetweenSongsAfterTransition
import paige.navic.domain.models.shouldPausePlaybackWhenVolumeZero
import paige.navic.domain.models.shouldFadePlaybackCommand
import paige.navic.domain.models.shouldRestartCurrentOnPrevious
import paige.navic.domain.models.shouldResumePlaybackWhenAudioDeviceAdded
import paige.navic.domain.models.shouldResumePlaybackAfterVolumeRestored
import paige.navic.domain.models.shouldSkipMediaAfterPlaybackError
import paige.navic.domain.models.songRadioQueue
import paige.navic.domain.models.SongRadioQueueDefaultSize
import paige.navic.domain.models.settings.AutoFillQueueSource
import paige.navic.domain.models.systemEqualizerAudioSessionId
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.ui.components.common.CoilBitmapLoader
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger
import paige.navic.util.core.ResourceProvider
import paige.navic.util.core.effectiveGain
import java.io.File
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
	private var exoPlayer: ExoPlayer? = null
	private var bassBoost: BassBoost? = null
	private var bassBoostAudioSessionId: Int? = null
	private var reverb: PresetReverb? = null
	private var reverbAudioSessionId: Int? = null

	private val connectivityManager: ConnectivityManager by inject()

	private val syncManager: SyncManager by inject()
	private val sessionManager: SessionManager by inject()
	private val preferenceManager: PreferenceManager by inject()

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

		val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
			.build().apply {
				setSmallIcon(resourceProvider.icNavic)
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
			.setBitmapLoader(CoilBitmapLoader(this, preferenceManager::serverRequestHeadersMap))
			.build()
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
		return mediaSession
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent?.action == ACTION_REFRESH_AUDIO_EFFECTS) {
			applyAudioEffects()
		}
		return super.onStartCommand(intent, flags, startId)
	}

	override fun onTaskRemoved(rootIntent: Intent?) {
		onDestroy()
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
		stopSelf()
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
		applyBassBoost(player)
		applyReverb(player)
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

				player.pause()
				pauseBetweenSongsJob = serviceScope.launch {
					delay(delayMs)
					if (
						player.currentMediaItemIndex == mediaItemIndex &&
						player.mediaItemCount > 0 &&
						player.playbackState != Player.STATE_ENDED
					) {
						player.play()
					}
				}
			}
		})
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
					player.pause()
					pausedByZeroVolume = true
				} else if (
					shouldResumePlaybackAfterVolumeRestored(
						pausePlaybackOnVolumeZero = preferenceManager.pausePlaybackOnVolumeZero,
						pausedByZeroVolume = pausedByZeroVolume,
						volume = volume
					)
				) {
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

	companion object {
		private const val ACTION_REFRESH_AUDIO_EFFECTS =
			"paige.navic.shared.action.REFRESH_AUDIO_EFFECTS"

		fun newSessionToken(context: Context): SessionToken {
			return SessionToken(context, ComponentName(context, PlaybackService::class.java))
		}

		fun refreshAudioEffects(context: Context) {
			context.startService(
				Intent(context, PlaybackService::class.java)
					.setAction(ACTION_REFRESH_AUDIO_EFFECTS)
			)
		}
	}
}

class AndroidMediaPlayerViewModel(
	private val application: Application,
	stateRepository: PlayerStateRepository,
	private val albumDao: AlbumDao,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	private val sessionManager: SessionManager,
	private val songRepository: SongRepository,
	private val preferenceManager: PreferenceManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	downloadManager = downloadManager,
	connectivityManager = connectivityManager,
	preferenceManager = preferenceManager
) {
	private var controller: MediaController? = null
	private var controllerFuture: ListenableFuture<MediaController>? = null

	private var loadingCollectionId: String? = null

	private var pendingSyncState: PlayerUiState? = null
	private var pendingPlayIndex: Int? = null
	private var playbackFadeJob: Job? = null
	private var playbackFadeRestoreVolume: Float? = null
	private var autoFillQueueJob: Job? = null

	init {
		connectToService()
	}

	private fun connectToService() {
		viewModelScope.launch {
			val sessionToken = PlaybackService.newSessionToken(application)
			controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
			controllerFuture?.addListener({
				controller = controllerFuture?.get()
				setupController()
			}, MoreExecutors.directExecutor())
		}
	}

	private fun getStreamUrl(id: String): Uri {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
		}
		val container = if (isCellular) preferenceManager.streamingQualityCellular.containerAndroid else preferenceManager.streamingQualityWifi.containerAndroid

		return sessionManager.api.getStreamUrl(id, bitrate, container)
			.toUri()
			.buildUpon()
			.appendQueryParameter("estimateContentLength", "true")
			.build()
	}

	private fun setupController() {
		viewModelScope.launch {
			controller?.apply {
				addListener(object : Player.Listener {
					override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
						updatePlaybackState()

						mediaItem?.mediaId?.let { id ->
							if (!isAvailable(id)) {
								controller?.seekToNextMediaItem()
							}
						}
						maybeAutoFillQueue()
					}

					override fun onIsPlayingChanged(isPlaying: Boolean) {
						_uiState.update { it.copy(isPaused = !isPlaying) }
						if (isPlaying) {
							startProgressLoop()
							maybeAutoFillQueue()
						}
						val intent =
							Intent("${application.packageName}.NOW_PLAYING_UPDATED").apply {
								setPackage(application.packageName)
								putExtra("isPlaying", isPlaying)
								putExtra(
									"title",
									_uiState.value.currentSong?.title ?: "Unknown song"
								)
								putExtra(
									"artist",
									_uiState.value.currentSong?.artistName ?: "Unknown artist"
								)
								putExtra(
									"artUrl",
									_uiState.value.currentSong?.coverArtId?.let {
										sessionManager.getCoverArtUrl(it)
									})
							}

						application.sendBroadcast(intent)
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						_uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
						updatePlaybackState()
						if (playbackState == Player.STATE_READY) {
							maybeAutoFillQueue()
						}
					}

					override fun onPlayerError(error: PlaybackException) {
						Logger.w("MediaPlayer", "Playback error", error)
						val shouldSkip = shouldSkipMediaAfterPlaybackError(
							skipMediaOnError = preferenceManager.skipMediaOnError,
							hasNextMediaItem = hasNextMediaItem()
						)
						if (shouldSkip) {
							seekToNextMediaItem()
							prepare()
							play()
						}
					}

					override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
						_uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
					}

					override fun onRepeatModeChanged(repeatMode: Int) {
						_uiState.update { it.copy(repeatMode = repeatMode) }
					}

					override fun onTracksChanged(tracks: Tracks) {
						updatePlaybackProperties(tracks)
					}

					override fun onAudioSessionIdChanged(audioSessionId: Int) {
						refreshAudioEffects()
					}

					override fun onTimelineChanged(timeline: Timeline, reason: Int) {
						updatePlaybackState()
					}
				})
				updatePlaybackState()
				updatePlaybackProperties(currentTracks)
				refreshAudioEffects()
				playPendingIndexIfAvailable(this)

				downloadManager.allDownloads.first()
				pendingSyncState?.let { state ->
					syncPlayerWithState(state)
					pendingSyncState = null
				}

				downloadManager.downloadedSongs.collectLatest { downloadedMap ->
					val player = controller ?: return@collectLatest

					for (i in 0 until player.mediaItemCount) {
						val item = player.getMediaItemAt(i)
						val id = item.mediaId
						val localPath = downloadedMap[id]

						val isCurrentlyLocal = item.localConfiguration?.uri?.scheme == "file"

						if (localPath != null && !isCurrentlyLocal) {
							val newItem = item.buildUpon()
								.setUri(File(localPath).toUri())
								.build()
							player.replaceMediaItem(i, newItem)
						} else if (localPath == null && isCurrentlyLocal) {
							val newItem = item.buildUpon()
								.setUri(getStreamUrl(id))
								.build()
							player.replaceMediaItem(i, newItem)
						}
					}
				}
			}
		}
	}

	override fun refreshAudioEffects() {
		runCatching {
			PlaybackService.refreshAudioEffects(application)
		}.onFailure { error ->
			Logger.w("MediaPlayer", "Failed to refresh audio effects", error)
		}
	}

	private fun refreshCurrentCollection(albumId: String) {
		if (loadingCollectionId == albumId) return
		loadingCollectionId = albumId

		viewModelScope.launch {
			runCatching {
				val album = albumDao.getAlbumById(albumId)

				_uiState.update { it.copy(currentCollection = album?.toDomainModel()) }
			}.onFailure {
				loadingCollectionId = null
			}
		}
	}

	private fun updatePlaybackState() {
		val controller = controller ?: return
		val index = controller.currentMediaItemIndex
		val currentSong = _uiState.value.queue.getOrNull(index)

		val derivedCollection = currentSong?.let { song ->
			val stateCollection = _uiState.value.currentCollection

			if (stateCollection?.id == song.albumId.toString()) {
				stateCollection
			} else {
				refreshCurrentCollection(song.albumId.toString())
				null
			}
		}

		_uiState.update { state ->
			state.copy(
				currentIndex = index,
				currentSong = currentSong,
				currentCollection = derivedCollection ?: state.currentCollection,
				isPaused = !controller.isPlaying,
				isShuffleEnabled = controller.shuffleModeEnabled,
				repeatMode = controller.repeatMode
			)
		}
		applyReplayGain()
		updateProgress()
	}

	private fun applyReplayGain() {
		if (preferenceManager.replayGainMode != ReplayGainMode.Off) {
			(_uiState.value.currentSong)?.replayGain?.let { replayGain ->
				controller?.volume = replayGain.effectiveGain(preferenceManager.replayGainMode)
			}
		} else {
			controller?.volume = 1f
		}
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		viewModelScope.launch {
			val player = controller

			if (player == null) {
				pendingSyncState = state
				return@launch
			}

			if (state.queue.isEmpty() || player.mediaItemCount > 0) return@launch

			val mediaItems = withContext(Dispatchers.Default) {
				state.queue.map { it.toMediaItem() }
			}

			player.setMediaItems(mediaItems)

			player.shuffleModeEnabled = state.isShuffleEnabled
			player.repeatMode = state.repeatMode
			player.playbackParameters = PlaybackParameters(state.playbackSpeed, state.playbackPitch)

			val index = if (state.currentIndex in mediaItems.indices) state.currentIndex else 0

			val songDurationMs = state.queue.getOrNull(index)?.duration?.inWholeMilliseconds ?: 0L

			val position = if (songDurationMs > 0) {
				(state.progress * songDurationMs).toLong()
			} else {
				0L
			}

			player.seekTo(index, position)
			player.prepare()
			if (!state.isPaused) {
				player.play()
			}
		}
	}

	private fun startProgressLoop() {
		viewModelScope.launch {
			while (controller?.isPlaying == true) {
				val player = controller ?: break
				val duration = player.duration.coerceAtLeast(1)
				val progress =
					(player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
				_uiState.update { it.copy(progress = progress) }
				delay(200.milliseconds)
			}
		}
	}

	private fun updateProgress() {
		controller?.let { player ->
			val duration = player.duration.coerceAtLeast(1)
			val pos = player.currentPosition
			val progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
			_uiState.update { it.copy(progress = progress) }
		}
	}

	private fun maybeAutoFillQueue() {
		val player = controller ?: return
		val state = _uiState.value
		if (
			!shouldAutoFillQueue(
				autoFillQueue = preferenceManager.autoFillQueue,
				isPlaying = player.isPlaying,
				isRadioQueue = state.queue.any { it.id.startsWith("radio_") },
				queueSize = state.queue.size,
				currentIndex = state.currentIndex,
				remainingTrigger = QueueAutoFillRemainingTrigger,
				targetSize = preferenceManager.autoFillQueueTargetSize
			)
		) {
			return
		}
		if (autoFillQueueJob?.isActive == true) return

		autoFillQueueJob = viewModelScope.launch {
			try {
				val allSongs = withContext(Dispatchers.IO) {
					songRepository.getAllSongs()
				}.shuffled()

				val currentPlayer = controller ?: return@launch
				val currentState = _uiState.value
				if (
					!shouldAutoFillQueue(
						autoFillQueue = preferenceManager.autoFillQueue,
						isPlaying = currentPlayer.isPlaying,
						isRadioQueue = currentState.queue.any { it.id.startsWith("radio_") },
						queueSize = currentState.queue.size,
						currentIndex = currentState.currentIndex,
						remainingTrigger = QueueAutoFillRemainingTrigger,
						targetSize = preferenceManager.autoFillQueueTargetSize
					)
				) {
					return@launch
				}

				val appendCount = queueAutoFillAppendCount(
					queueSize = currentState.queue.size,
					targetSize = preferenceManager.autoFillQueueTargetSize
				)
				val serverSimilarSongs = if (
					preferenceManager.autoFillQueueSource == AutoFillQueueSource.SimilarToCurrentSong &&
					currentState.currentSong != null
				) {
					withContext(Dispatchers.IO) {
						fetchServerSimilarSongs(
							songId = currentState.currentSong.id,
							limit = appendCount * 2
						)
					}
				} else {
					emptyList()
				}
				val preferredSongIds = serverSimilarSongs.map { it.id }
				val queuedIds = currentState.queue.mapTo(mutableSetOf()) { it.id }
				val songsToAppend = queueAutoFillCandidateSongs(
					candidateSongs = (serverSimilarSongs + allSongs).filter { isAvailable(it.id) },
					queuedIds = queuedIds,
					limit = appendCount,
					source = preferenceManager.autoFillQueueSource,
					currentSong = currentState.currentSong,
					preferredSongIds = preferredSongIds
				)
				if (songsToAppend.isEmpty()) return@launch

				val mediaItems = withContext(Dispatchers.Default) {
					songsToAppend.map { it.toMediaItem() }
				}
				currentPlayer.addMediaItems(mediaItems)
				_uiState.update { it.copy(queue = it.queue + songsToAppend) }
			} catch (error: Exception) {
				Logger.w("MediaPlayer", "Queue auto-fill failed", error)
			} finally {
				autoFillQueueJob = null
			}
		}
	}

	@OptIn(UnstableApi::class)
	private fun updatePlaybackProperties(tracks: Tracks) {
		val audioGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
		if (audioGroup != null) {
			for (i in 0 until audioGroup.length) {
				if (audioGroup.isTrackSelected(i)) {
					val format = audioGroup.getTrackFormat(i)
					Logger.i("MediaPlayer", "Active Track Format: $format")
					_uiState.update { state ->
						state.copy(
							playbackBitrate = format.bitrate.takeIf { it > 0 },
							playbackSampleRate = format.sampleRate.takeIf { it > 0 },
							playbackMimeType = format.sampleMimeType
						)
					}
					break
				}
			}
		}
	}

	override fun addToQueueSingle(song: DomainSong) {
		viewModelScope.launch {
			val player = controller
			player?.addMediaItem(withContext(Dispatchers.Default) { song.toMediaItem() })
			_uiState.update { state ->
				val newQueue = state.queue + song
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) song else state.currentSong
				)
			}
			player?.let(::playPendingIndexIfAvailable)
		}
	}

	override fun addToQueue(collection: DomainSongCollection) {
		viewModelScope.launch {
			val (items, newCollection) = withContext(Dispatchers.Default) {
				val newCollection = if (collection is DomainAlbum) collection.songs.sortedWith(compareBy(
					{ it.discNumber },
					{ it.trackNumber }
				)) else collection.songs
				newCollection.map { it.toMediaItem() } to newCollection
			}
			val player = controller
			player?.addMediaItems(items)
			_uiState.update { state ->
				val newQueue = state.queue + newCollection
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) newCollection.firstOrNull() else state.currentSong
				)
			}
			player?.let(::playPendingIndexIfAvailable)
		}
	}

	override fun removeFromQueue(index: Int) {
		viewModelScope.launch {
			controller?.removeMediaItem(index)
			_uiState.update { state ->
				val newQueue = state.queue.toMutableList().apply { removeAt(index) }
				val newIndex = when {
					index < state.currentIndex -> state.currentIndex - 1
					index == state.currentIndex -> if (newQueue.isEmpty()) -1 else state.currentIndex.coerceAtMost(
						newQueue.size - 1
					)

					else -> state.currentIndex
				}
				state.copy(
					queue = newQueue,
					currentIndex = newIndex,
					currentSong = if (newIndex == -1) null else newQueue[newIndex]
				)
			}
		}
	}

	override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
		viewModelScope.launch {
			controller?.moveMediaItem(fromIndex, toIndex)
			_uiState.update { state ->
				val newQueue = state.queue.toMutableList().apply {
					val item = removeAt(fromIndex)
					add(toIndex, item)
				}
				val newIndex = when (state.currentIndex) {
					fromIndex -> toIndex
					in (fromIndex + 1)..toIndex -> state.currentIndex - 1
					in toIndex until fromIndex -> state.currentIndex + 1
					else -> state.currentIndex
				}
				state.copy(
					queue = newQueue,
					currentIndex = newIndex,
					currentSong = if (newIndex == -1) null else newQueue[newIndex]
				)
			}
		}
	}

	override fun clearQueue() {
		viewModelScope.launch {
			pendingPlayIndex = null
			autoFillQueueJob?.cancel()
			autoFillQueueJob = null
			_uiState.update {
				it.copy(
					queue = emptyList(),
					currentSong = null,
					currentIndex = -1,
					progress = 0f
				)
			}
			controller?.clearMediaItems()
		}
	}

	override fun playAt(index: Int) {
		viewModelScope.launch {
			if (index < 0) return@launch

			val player = controller
			if (player == null || !playAtIfAvailable(player, index)) {
				pendingPlayIndex = index
			}
		}
	}

	private fun playAtIfAvailable(player: MediaController, index: Int): Boolean {
		if (index !in 0 until player.mediaItemCount) return false

		player.seekTo(index, 0L)
		player.play()
		return true
	}

	private fun playPendingIndexIfAvailable(player: MediaController) {
		val index = pendingPlayIndex ?: return
		if (playAtIfAvailable(player, index)) {
			pendingPlayIndex = null
		}
	}

	override fun playNextSingle(song: DomainSong) {
		viewModelScope.launch {
			controller?.addMediaItem(
				_uiState.value.currentIndex + 1,
				withContext(Dispatchers.Default) { song.toMediaItem() }
			)
			_uiState.update { state ->
				val newQueue =
					if (state.queue.isEmpty())
						state.queue + song
					else
						state.queue.slice(0..state.currentIndex) + song + state.queue.slice(state.currentIndex+1..<state.queue.size)
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) song else state.currentSong
				)
			}
		}
	}

	override fun playNext(collection: DomainSongCollection) {
		viewModelScope.launch {
			val (items, newCollection) = withContext(Dispatchers.Default) {
				val newCollection = if (collection is DomainAlbum) collection.songs.sortedWith(compareBy(
					{ it.discNumber },
					{ it.trackNumber }
				)) else collection.songs
				newCollection.map { it.toMediaItem() } to newCollection
			}
			controller?.addMediaItems(_uiState.value.currentIndex + 1, items)
			_uiState.update { state ->
				val newQueue = 
					if (state.queue.isEmpty()) 
						state.queue + newCollection
					else
						state.queue.slice(0..state.currentIndex) + newCollection + state.queue.slice(
							state.currentIndex+1..<state.queue.size
						)
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) newCollection.firstOrNull() else state.currentSong
				)
			}
		}
	}

	override fun startSongRadio(song: DomainSong) {
		viewModelScope.launch {
			try {
				val serverSimilarSongs = withContext(Dispatchers.IO) {
					fetchServerSimilarSongs(
						songId = song.id,
						limit = SongRadioQueueDefaultSize
					)
				}.filter { isAvailable(it.id) }
				val songs = withContext(Dispatchers.IO) {
					songRepository.getAllSongs()
				}.filter { isAvailable(it.id) }.shuffled()
				val radioQueue = songRadioQueue(
					seedSong = song,
					candidateSongs = serverSimilarSongs + songs,
					limit = SongRadioQueueDefaultSize,
					preferredSongIds = serverSimilarSongs.map { it.id }
				)
				if (radioQueue.isEmpty()) return@launch

				val mediaItems = withContext(Dispatchers.Default) {
					radioQueue.map { it.toMediaItem() }
				}
				pendingPlayIndex = null
				autoFillQueueJob?.cancel()
				autoFillQueueJob = null
				controller?.let { player ->
					player.shuffleModeEnabled = false
					player.setMediaItems(mediaItems, 0, 0L)
					player.prepare()
					player.play()
				}

				_uiState.update {
					it.copy(
						queue = radioQueue,
						currentIndex = 0,
						currentSong = radioQueue.firstOrNull(),
						currentCollection = null,
						isPaused = false,
						progress = 0f
					)
				}
			} catch (error: Exception) {
				Logger.w("MediaPlayer", "Song radio failed", error)
			}
		}
	}

	private suspend fun fetchServerSimilarSongs(
		songId: String,
		limit: Int
	): List<DomainSong> =
		runCatching {
			sessionManager.api
				.getSimilarSongsID3(songId, limit.coerceAtLeast(0))
				.map { it.toEntity().toDomainModel() }
		}.onFailure { error ->
			Logger.w("MediaPlayer", "Navidrome similar-song lookup failed", error)
		}.getOrDefault(emptyList())

	override fun playRadio(radio: DomainRadio) {
		viewModelScope.launch {
			val radioId = "radio_${radio.name.hashCode()}"

			val dummyRadioSong = DomainSong(
				id = radioId,
				title = radio.name,
				artistName = "Live Radio",
				albumId = "radio_album",
				albumTitle = "Live Stream",
				duration = Duration.ZERO,
				trackNumber = 1,
				coverArtId = null,
				artistId = "",
				parentId = "",
				comment = null,
				discNumber = null,
				isrc = emptyList(),
				year = null,
				genre = null,
				genres = emptyList(),
				moods = emptyList(),
				bpm = null,
				contributors = emptyList(),
				playCount = 0,
				userRating = 0,
				averageRating = null,
				bitRate = null,
				bitDepth = null,
				sampleRate = null,
				audioChannelCount = null,
				replayGain = null,
				fileSize = 0,
				fileExtension = "",
				mimeType = "",
				filePath = radio.streamUrl,
				starredAt = null,
				musicBrainzId = null,
				explicitStatus = DomainExplicitStatus.Unknown
			)

			val metadata = MediaMetadata.Builder()
				.setTitle(radio.name)
				.setArtist("Live Radio")
				.setIsPlayable(true)
				.build()

			val mediaItem = MediaItem.Builder()
				.setUri(radio.streamUrl)
				.setMediaId("radio_${radio.name.hashCode()}")
				.setMediaMetadata(metadata)
				.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
				.build()

			controller?.let { player ->
				player.stop()
				player.clearMediaItems()
				player.setMediaItem(mediaItem)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = listOf(dummyRadioSong),
					currentIndex = 0,
					currentSong = dummyRadioSong,
					isLoading = true
				)
			}
		}
	}

	override fun shufflePlay(collection: DomainSongCollection) {
		viewModelScope.launch {
			val (shuffledSongs, mediaItems) = withContext(Dispatchers.Default) {
				val songs = collection.songs.shuffled()
				songs to songs.map { it.toMediaItem() }
			}

			controller?.let { player ->
				player.shuffleModeEnabled = false
				player.setMediaItems(mediaItems, 0, 0L)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = shuffledSongs,
					currentIndex = 0,
					currentSong = shuffledSongs.firstOrNull()
				)
			}
		}
	}

	override fun pause() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val player = controller ?: return@launch
			cancelPlaybackFade(player)
			val fadeDurationMs = preferenceManager.audioFadeDurationMs
			if (
				!shouldFadePlaybackCommand(
					audioFadeDurationMs = fadeDurationMs,
					alreadyInTargetState = !player.isPlaying
				)
			) {
				player.pause()
				return@launch
			}

			val originalVolume = player.volume.coerceIn(0f, 1f)
			startPlaybackVolumeFade(
				player = player,
				startVolume = originalVolume,
				targetVolume = 0f,
				durationMs = audioFadeDurationMs(fadeDurationMs),
				restoreVolumeOnCancel = originalVolume,
				onEnd = {
					player.pause()
					player.volume = originalVolume
				}
			)
		}
	}

	override fun resume() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val player = controller ?: return@launch
			cancelPlaybackFade(player)
			val fadeDurationMs = preferenceManager.audioFadeDurationMs
			if (
				!shouldFadePlaybackCommand(
					audioFadeDurationMs = fadeDurationMs,
					alreadyInTargetState = player.isPlaying
				)
			) {
				player.play()
				return@launch
			}

			val targetVolume = player.volume.coerceIn(0f, 1f).takeIf { it > 0f } ?: 1f
			startPlaybackVolumeFade(
				player = player,
				startVolume = 0f,
				targetVolume = targetVolume,
				durationMs = audioFadeDurationMs(fadeDurationMs),
				restoreVolumeOnCancel = targetVolume,
				onStart = {
					player.volume = 0f
					player.play()
				}
			)
		}
	}

	private fun cancelPlaybackFade(player: MediaController? = controller) {
		playbackFadeJob?.cancel()
		playbackFadeJob = null
		playbackFadeRestoreVolume?.let { volume ->
			player?.volume = volume
		}
		playbackFadeRestoreVolume = null
	}

	private fun startPlaybackVolumeFade(
		player: MediaController,
		startVolume: Float,
		targetVolume: Float,
		durationMs: Long,
		restoreVolumeOnCancel: Float,
		onStart: () -> Unit = {},
		onEnd: () -> Unit = {}
	) {
		playbackFadeJob?.cancel()
		playbackFadeRestoreVolume = restoreVolumeOnCancel
		playbackFadeJob = viewModelScope.launch(Dispatchers.Main.immediate) {
			onStart()
			val steps = (durationMs / 16L).coerceAtLeast(1L).toInt()
			repeat(steps) { step ->
				val progress = (step + 1).toFloat() / steps.toFloat()
				player.volume = startVolume + ((targetVolume - startVolume) * progress)
				delay(16L)
			}
			player.volume = targetVolume
			playbackFadeJob = null
			playbackFadeRestoreVolume = null
			onEnd()
		}
	}

	override fun next() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			if (controller?.hasNextMediaItem() == true) controller?.seekToNextMediaItem()
		}
	}

	override fun previous() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val controller = controller ?: return@launch
			if (
				shouldRestartCurrentOnPrevious(
					smartRewindSeconds = preferenceManager.smartRewindSeconds,
					hasPreviousMediaItem = controller.hasPreviousMediaItem(),
					currentPositionMs = controller.currentPosition
				)
			) {
				controller.seekTo(0)
			} else {
				controller.seekToPreviousMediaItem()
			}
		}
	}

	override fun toggleShuffle() {
		viewModelScope.launch {
			controller?.let { player ->
				player.shuffleModeEnabled = !player.shuffleModeEnabled
			}
		}
	}

	override fun toggleRepeat() {
		viewModelScope.launch {
			controller?.let { player ->
				player.repeatMode = when (player.repeatMode) {
					Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
					Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
					else -> Player.REPEAT_MODE_OFF
				}
			}
		}
	}

	override fun seek(normalized: Float) {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			controller?.let {
				val target = (it.duration * normalized).toLong()
				it.seekTo(target)
				_uiState.update { state ->
					state.copy(progress = normalized)
				}
			}
		}
	}

	override fun onCleared() {
		viewModelScope.launch {
			cancelPlaybackFade()
			autoFillQueueJob?.cancel()
			autoFillQueueJob = null
			super.onCleared()
			controllerFuture?.let { MediaController.releaseFuture(it) }
		}
	}

	override fun setPlaybackSpeed(value: Float) {
		val speed = normalizedPlaybackSpeed(value)
		viewModelScope.launch {
			controller?.playbackParameters = PlaybackParameters(speed, _uiState.value.playbackPitch)
		}
		_uiState.update { it.copy(playbackSpeed = speed) }
	}

	override fun setPlaybackPitch(value: Float) {
		val pitch = normalizedPlaybackPitch(value)
		viewModelScope.launch {
			controller?.playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed, pitch)
		}
		_uiState.update { it.copy(playbackPitch = pitch) }
	}

	override fun openSystemEqualizer(): Boolean {
		val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
			systemEqualizerAudioSessionId(controller?.audioSessionId)?.let { audioSessionId ->
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
			}
			putExtra(AudioEffect.EXTRA_PACKAGE_NAME, application.packageName)
			putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}

		return try {
			application.startActivity(intent)
			true
		} catch (error: ActivityNotFoundException) {
			Logger.w("MediaPlayer", "System equalizer not available", error)
			false
		}
	}

	private fun DomainSong.toMediaItem(): MediaItem {
		val metadata = MediaMetadata.Builder()
			.setTitle(title)
			.setArtist(artistName)
			.setAlbumTitle(albumTitle)
			.setArtworkUri(
				coverArtId?.let { sessionManager.getCoverArtUrl(it).toUri() }
			)
			.build()

		val uri = when {
			id.startsWith("radio_") && !filePath.isNullOrEmpty() -> {
				filePath.toUri()
			}

			else -> {
				val localPath = downloadManager.getDownloadedFilePath(id)
				if (localPath != null) {
					File(localPath).toUri()
				} else {
					getStreamUrl(id)
				}
			}
		}

		val builder = MediaItem.Builder()
			.setUri(uri)
			.setMediaId(id)
			.setMediaMetadata(metadata)

		if (id.startsWith("radio_")) {
			builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
		}

		return builder.build()
	}
}
