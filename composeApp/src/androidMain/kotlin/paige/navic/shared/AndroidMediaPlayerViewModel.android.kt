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
import paige.navic.domain.manager.AudioPlaybackArbitrator
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PlaybackOriginCredit
import paige.navic.domain.manager.PlaybackOriginTracker
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.AudioPlaybackOwner
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
import paige.navic.domain.models.playbackPrefetchIndexes
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
import paige.navic.domain.models.shouldPauseForAudioPlaybackClaim
import paige.navic.domain.models.shouldFadePlaybackCommand
import paige.navic.domain.models.shouldReplaceQueuedMediaItemForDownloadAvailability
import paige.navic.domain.models.shouldRestartCurrentOnPrevious
import paige.navic.domain.models.shouldSendNowPlayingWidgetUpdate
import paige.navic.domain.models.shouldResumePlaybackWhenAudioDeviceAdded
import paige.navic.domain.models.shouldResumePlaybackAfterVolumeRestored
import paige.navic.domain.models.shouldSkipMediaAfterPlaybackError
import paige.navic.domain.models.songRadioQueue
import paige.navic.domain.models.SongRadioQueueDefaultSize
import paige.navic.domain.models.settings.AutoFillQueueSource
import paige.navic.domain.models.settings.MediaNotificationAction
import paige.navic.domain.models.systemEqualizerAudioSessionId
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.ui.components.common.CoilBitmapLoader
import paige.navic.ui.core.PlayerUiState
import paige.navic.util.core.Logger
import paige.navic.util.core.ResourceProvider
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val PlaybackOriginCheckpointIntervalMs = 30_000L

class AndroidMediaPlayerViewModel(
	private val application: Application,
	stateRepository: PlayerStateRepository,
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	private val sessionManager: SessionManager,
	private val songRepository: SongRepository,
	private val musicBrainzArtworkRepository: MusicBrainzArtworkRepository,
	private val playbackOriginRepository: PlaybackOriginRepository,
	private val audioPlaybackArbitrator: AudioPlaybackArbitrator,
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
	private var lastMusicBrainzArtworkPrefetchSongId: String? = null
	private var lastPlaybackPrefetchSignature: String? = null
	private var lastNowPlayingWidgetSongId: String? = null
	private var lastNowPlayingWidgetIsPlaying: Boolean? = null
	private var nowPlayingVideoClipAudioActive = false
	private val playbackOriginTracker = PlaybackOriginTracker()
	private var lastPlaybackOriginCheckpointMillis = 0L

	init {
		connectToService()
		observeAudioPlaybackClaims()
	}

	private fun observeAudioPlaybackClaims() {
		viewModelScope.launch {
			audioPlaybackArbitrator.claims.collectLatest { claimedOwner ->
				val player = controller ?: return@collectLatest
				if (
					shouldPauseForAudioPlaybackClaim(
						currentOwner = AudioPlaybackOwner.Music,
						claimedOwner = claimedOwner,
						isPlaying = player.isPlaying
					)
				) {
					pause()
				}
			}
		}
	}

	private fun claimMusicPlayback() {
		audioPlaybackArbitrator.claim(AudioPlaybackOwner.Music)
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

						if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
							mediaItem?.mediaId?.let { id ->
								if (!isAvailable(id)) {
									controller?.seekToNextMediaItem()
								}
							}
						}
						maybeAutoFillQueue()
					}

					override fun onIsPlayingChanged(isPlaying: Boolean) {
						_uiState.update { it.copy(isPaused = !isPlaying) }
						val nowMillis = Clock.System.now().toEpochMilliseconds()
						recordPlaybackOriginCredit(
							playbackOriginTracker.onPlaybackState(
								isPlaying = isPlaying,
								nowMillis = nowMillis
							)
						)
						if (isPlaying) {
							lastPlaybackOriginCheckpointMillis = nowMillis
							startProgressLoop()
							maybeAutoFillQueue()
							prefetchMusicBrainzArtworkForCurrentSong(_uiState.value.currentSong, isPlaying = true)
							prefetchUpcomingPlaybackAssets(_uiState.value, isPlaying = true)
						}
						sendNowPlayingBroadcast(isPlaying)
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						_uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
						updatePlaybackState()
						if (playbackState == Player.STATE_READY) {
							maybeAutoFillQueue()
						}
					}

					override fun onPlayerError(error: PlaybackException) {
						Logger.w(
							"MediaPlayer",
							"Playback error mediaId=${currentMediaItem?.mediaId} " +
								"index=$currentMediaItemIndex " +
								"code=${error.errorCodeName} message=${error.message}",
							error
						)
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
						updatePlaybackState()
					}

					override fun onRepeatModeChanged(repeatMode: Int) {
						updatePlaybackState()
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

				combine(
					downloadManager.downloadedSongs,
					connectivityManager.isCellular,
					snapshotFlow { preferenceManager.streamingQualityWifi },
					snapshotFlow { preferenceManager.streamingQualityCellular },
					snapshotFlow { preferenceManager.isAdvancedTranscodingActive },
					snapshotFlow { preferenceManager.customMaxBitrateWifi },
					snapshotFlow { preferenceManager.customMaxBitrateCellular }
				) { it }.collectLatest { args ->
					@Suppress("UNCHECKED_CAST")
					val downloadedMap = args[0] as Map<String, String>
					val player = controller ?: return@collectLatest
					val currentIndex = player.currentMediaItemIndex

					for (i in 0 until player.mediaItemCount) {
						val item = player.getMediaItemAt(i)
						val id = item.mediaId
						val localPath = downloadedMap[id]

						val isCurrentlyLocal = item.localConfiguration?.uri?.scheme == "file"
						if (
							!shouldReplaceQueuedMediaItemForDownloadAvailability(
								isCurrentItem = i == currentIndex,
								hasDownloadedFile = localPath != null,
								isCurrentlyLocal = isCurrentlyLocal
							)
						) {
							continue
						}

						val newItem = if (localPath != null) {
							if (!isCurrentlyLocal) {
								item.buildUpon()
									.setUri(File(localPath).toUri())
									.build()
							} else null
						} else {
							val newUri = getStreamUrl(id)
							if (isCurrentlyLocal || item.localConfiguration?.uri != newUri) {
								item.buildUpon()
									.setUri(newUri)
									.build()
							} else null
						}

						if (newItem != null) {
							if (i == player.currentMediaItemIndex) {
								val currentPosition = player.currentPosition
								player.replaceMediaItem(i, newItem)
								player.seekTo(i, currentPosition)
							} else {
								player.replaceMediaItem(i, newItem)
							}
						}
					}
				}
			}
		}
	}

	override fun refreshAudioEffects() {
		runCatching {
			applyReplayGain()
			PlaybackService.refreshAudioEffects(application)
		}.onFailure { error ->
			Logger.w("MediaPlayer", "Failed to refresh audio effects", error)
		}
	}

	override fun refreshPlaybackVolume() {
		runCatching {
			applyReplayGain()
		}.onFailure { error ->
			Logger.w("MediaPlayer", "Failed to refresh playback volume", error)
		}
	}

	override fun setNowPlayingVideoClipAudioActive(active: Boolean) {
		if (nowPlayingVideoClipAudioActive == active) return
		nowPlayingVideoClipAudioActive = active
		cancelPlaybackFade()
		refreshPlaybackVolume()
	}

	override fun setPlaybackOrigin(origin: PlaybackOrigin?) {
		viewModelScope.launch {
			setPlaybackOriginNow(origin)
		}
	}

	private fun setPlaybackOriginNow(origin: PlaybackOrigin?) {
		val nowMillis = Clock.System.now().toEpochMilliseconds()
		recordPlaybackOriginCredit(playbackOriginTracker.setOrigin(origin, nowMillis))
		lastPlaybackOriginCheckpointMillis = nowMillis
	}

	private fun recordPlaybackOriginCredit(credit: PlaybackOriginCredit?) {
		if (credit == null) return
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				playbackOriginRepository.credit(
					origin = credit.origin,
					durationMillis = credit.durationMillis
				)
			}.onFailure { error ->
				Logger.w("MediaPlayer", "Failed to credit playback origin", error)
			}
		}
	}

	private fun checkpointPlaybackOriginIfNeeded(nowMillis: Long) {
		if (nowMillis - lastPlaybackOriginCheckpointMillis < PlaybackOriginCheckpointIntervalMs) {
			return
		}
		recordPlaybackOriginCredit(playbackOriginTracker.checkpoint(nowMillis))
		lastPlaybackOriginCheckpointMillis = nowMillis
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
		val upcomingIndexes = controller.upcomingMediaItemIndexes()

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
				upcomingIndexes = upcomingIndexes,
				currentSong = currentSong,
				currentCollection = derivedCollection ?: state.currentCollection,
				isPaused = !controller.isPlaying,
				isShuffleEnabled = controller.shuffleModeEnabled,
				repeatMode = controller.repeatMode
			)
		}
		applyReplayGain()
		prefetchMusicBrainzArtworkForCurrentSong(currentSong, isPlaying = controller.isPlaying)
		prefetchUpcomingPlaybackAssets(_uiState.value, isPlaying = controller.isPlaying)
		updateProgress()
		sendNowPlayingBroadcast(isPlaying = controller.isPlaying)
	}

	private fun MediaController.upcomingMediaItemIndexes(): List<Int> {
		val itemCount = mediaItemCount
		val currentIndex = currentMediaItemIndex
		if (itemCount <= 0 || currentIndex !in 0 until itemCount) return emptyList()
		if (repeatMode == Player.REPEAT_MODE_ONE) return listOf(currentIndex)

		val timeline = currentTimeline
		if (timeline.isEmpty) return emptyList()

		val indexes = mutableListOf<Int>()
		var index = currentIndex
		repeat(itemCount - 1) {
			val nextIndex = timeline.getNextWindowIndex(
				index,
				repeatMode,
				shuffleModeEnabled
			)
			if (nextIndex == C.INDEX_UNSET || nextIndex !in 0 until itemCount) {
				return indexes
			}
			if (nextIndex == currentIndex) return indexes

			indexes += nextIndex
			index = nextIndex
		}
		return indexes
	}

	private fun prefetchMusicBrainzArtworkForCurrentSong(
		currentSong: DomainSong?,
		isPlaying: Boolean
	) {
		if (!isPlaying || currentSong == null) return
		if (lastMusicBrainzArtworkPrefetchSongId == currentSong.id) return
		lastMusicBrainzArtworkPrefetchSongId = currentSong.id

		viewModelScope.launch(Dispatchers.IO) {
			val artwork = musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(currentSong)
				.getOrNull()
			if (artwork?.imageUrl.isNullOrBlank()) return@launch

			withContext(Dispatchers.Main.immediate) {
				val state = _uiState.value
				if (state.currentSong?.id == currentSong.id) {
					sendNowPlayingBroadcast(isPlaying = !state.isPaused, force = true)
				}
			}
		}
	}

	private fun prefetchUpcomingPlaybackAssets(
		state: PlayerUiState,
		isPlaying: Boolean
	) {
		if (!isPlaying) return
		val indexes = playbackPrefetchIndexes(
			upcomingIndexes = state.upcomingIndexes,
			upNextCount = preferenceManager.nowPlayingUpNextCount
		)
		val songs = indexes.mapNotNull { index -> state.queue.getOrNull(index) }
			.distinctBy { it.id }
		if (songs.isEmpty()) return

		val signature = songs.joinToString("|") { song -> song.id }
		if (signature == lastPlaybackPrefetchSignature) return
		lastPlaybackPrefetchSignature = signature

		downloadManager.prefetchPlaybackSongs(songs)
		viewModelScope.launch(Dispatchers.IO) {
			songs.forEach { song ->
				runCatching {
					musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(song)
				}.onFailure { error ->
					Logger.w("MediaPlayer", "Failed to prefetch artwork for upcoming song ${song.id}", error)
				}
			}
		}
	}

	private fun sendNowPlayingBroadcast(isPlaying: Boolean, force: Boolean = false) {
		val currentSong = _uiState.value.currentSong
		val currentSongId = currentSong?.id
		val previousIsPlaying = lastNowPlayingWidgetIsPlaying
		if (
			!force &&
			previousIsPlaying != null &&
			!shouldSendNowPlayingWidgetUpdate(
				previousSongId = lastNowPlayingWidgetSongId,
				currentSongId = currentSongId,
				previousIsPlaying = previousIsPlaying,
				currentIsPlaying = isPlaying
			)
		) {
			return
		}

		lastNowPlayingWidgetSongId = currentSongId
		lastNowPlayingWidgetIsPlaying = isPlaying

		val intent = Intent("${application.packageName}.NOW_PLAYING_UPDATED").apply {
			setPackage(application.packageName)
			putExtra("isPlaying", isPlaying)
			putExtra("title", currentSong?.title ?: "Unknown song")
			putExtra("artist", currentSong?.artistName ?: "Unknown artist")
			putExtra("artUrl", currentSong?.let(::currentArtworkUrl))
		}

		application.sendBroadcast(intent)
	}

	private fun currentArtworkUrl(song: DomainSong): String? =
		activeArtworkUrl(
			serverArtworkUrl = song.coverArtId?.let { sessionManager.getCoverArtUrl(it) },
			externalArtworkUrl = externalFallbackArtworkUrl(
				serverCoverArtId = song.coverArtId,
				externalArtworkUrl = musicBrainzArtworkRepository.artworkBySongId.value[song.id]?.imageUrl
			)
		)

	private fun applyReplayGain() {
		val replayGain = _uiState.value.currentSong?.replayGain
		val replayGainMode = preferenceManager.replayGainMode
		val loudnessBoostEnabled = preferenceManager.replayGainLoudnessBoost
		controller?.volume = playbackVolumeMultiplier(
			playbackVolumePercent = preferenceManager.playbackVolumePercent,
			replayGainVolumeMultiplier = replayGainVolumeMultiplier(
				replayGain = replayGain,
				mode = replayGainMode,
				loudnessBoostEnabled = loudnessBoostEnabled
			),
			forceMuted = nowPlayingVideoClipAudioActive
		)
		PlaybackService.setReplayGainLoudnessBoost(
			application,
			replayGainLoudnessBoostMillibels(
				replayGain = replayGain,
				mode = replayGainMode,
				loudnessBoostEnabled = loudnessBoostEnabled
			)
		)
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
				claimMusicPlayback()
				player.play()
			}
		}
	}

	private fun startProgressLoop() {
		viewModelScope.launch {
			while (controller?.isPlaying == true) {
				val player = controller ?: break
				checkpointPlaybackOriginIfNeeded(Clock.System.now().toEpochMilliseconds())
				val duration = player.duration
				if (duration > 0) {
					val progress =
						(player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
					_uiState.update { it.copy(progress = progress) }
				}
				delay(200.milliseconds)
			}
		}
	}

	private fun updateProgress() {
		controller?.let { player ->
			val duration = player.duration
			if (duration > 0) {
				val pos = player.currentPosition
				val progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
				_uiState.update { it.copy(progress = progress) }
			}
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
				val recentQueueSongs = currentState.queue
					.take(currentState.currentIndex + 1)
					.takeLast(10)
				val songsToAppend = queueAutoFillCandidateSongs(
					candidateSongs = (serverSimilarSongs + allSongs).filter { isAvailable(it.id) },
					queuedIds = queuedIds,
					limit = appendCount,
					source = preferenceManager.autoFillQueueSource,
					currentSong = currentState.currentSong,
					preferredSongIds = preferredSongIds,
					recentSongs = recentQueueSongs
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

	override fun applyDiscoverQueueFilter(onComplete: (removedCount: Int) -> Unit) {
		viewModelScope.launch {
			val initialState = _uiState.value
			if (initialState.currentIndex !in initialState.queue.indices) {
				onComplete(0)
				return@launch
			}

			val candidateIds = initialState.queue
				.drop(initialState.currentIndex + 1)
				.map { it.id }
				.filterNot { it.startsWith("radio_") }
				.distinct()
			if (candidateIds.isEmpty()) {
				onComplete(0)
				return@launch
			}

			val knownSongIds = withContext(Dispatchers.IO) {
				songDao.getStarredSongIds(candidateIds).toSet() +
					playlistDao.getPlaylistSongIds(candidateIds)
			}
			val state = _uiState.value
			val removalIndexes = discoverQueueRemovalIndexes(
				queueSongIds = state.queue.map { it.id },
				currentIndex = state.currentIndex,
				knownSongIds = knownSongIds
			)
			if (removalIndexes.isEmpty()) {
				onComplete(0)
				return@launch
			}

			val player = controller
			if (player == null || removalIndexes.any { it !in 0 until player.mediaItemCount }) {
				onComplete(0)
				return@launch
			}

			runCatching {
				removalIndexes.asReversed().forEach { player.removeMediaItem(it) }
			}.onFailure { error ->
				Logger.w("MediaPlayer", "Discover queue filter failed", error)
				onComplete(0)
				return@launch
			}

			val newQueue = state.queue.toMutableList().apply {
				removalIndexes.asReversed().forEach { removeAt(it) }
			}
			_uiState.value = state.copy(queue = newQueue)
			onComplete(removalIndexes.size)
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
			setPlaybackOriginNow(null)
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
		claimMusicPlayback()
		player.play()
		return true
	}

	private fun playPendingIndexIfAvailable(player: MediaController) {
		val index = pendingPlayIndex ?: return
		if (playAtIfAvailable(player, index)) {
			pendingPlayIndex = null
		}
	}

	override fun playCollection(collection: DomainSongCollection, startSong: DomainSong) {
		viewModelScope.launch {
			val (items, newCollection) = withContext(Dispatchers.Default) {
				val sortedCollection = if (collection is DomainAlbum) {
					collection.songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
				} else {
					collection.songs
				}
				sortedCollection.map { it.toMediaItem() } to sortedCollection
			}

			val startIndex = newCollection.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)

			controller?.let { player ->
				player.setMediaItems(items, startIndex, 0L)
				player.prepare()
				claimMusicPlayback()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = newCollection,
					currentIndex = startIndex,
					currentSong = newCollection.getOrNull(startIndex)
				)
			}
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
			setPlaybackOriginNow(null)
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
					claimMusicPlayback()
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
			setPlaybackOriginNow(null)
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
				claimMusicPlayback()
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
			val plan = collectionShufflePlaybackPlan()
			val songs = when (plan.queueOrder) {
				CollectionShuffleQueueOrder.Canonical -> collection.songs
				CollectionShuffleQueueOrder.Shuffled -> collection.songs.shuffled()
			}
			val mediaItems = withContext(Dispatchers.Default) {
				songs.map { it.toMediaItem() }
			}

			controller?.let { player ->
				player.shuffleModeEnabled = plan.enablePlayerShuffle
				player.setMediaItems(mediaItems, 0, 0L)
				player.prepare()
				claimMusicPlayback()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = songs,
					currentIndex = 0,
					currentSong = songs.firstOrNull(),
					isShuffleEnabled = plan.enablePlayerShuffle
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
					player.volume = effectivePlaybackVolume(originalVolume)
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
				claimMusicPlayback()
				player.play()
				return@launch
			}

			val targetVolume = playbackVolumeMultiplier(
				playbackVolumePercent = preferenceManager.playbackVolumePercent,
				replayGainVolumeMultiplier = replayGainVolumeMultiplier(
					replayGain = _uiState.value.currentSong?.replayGain,
					mode = preferenceManager.replayGainMode,
					loudnessBoostEnabled = preferenceManager.replayGainLoudnessBoost
				),
				forceMuted = nowPlayingVideoClipAudioActive
			)
			startPlaybackVolumeFade(
				player = player,
				startVolume = 0f,
				targetVolume = targetVolume,
				durationMs = audioFadeDurationMs(fadeDurationMs),
				restoreVolumeOnCancel = targetVolume,
				onStart = {
					claimMusicPlayback()
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
			player?.volume = effectivePlaybackVolume(volume)
		}
		playbackFadeRestoreVolume = null
	}

	private fun effectivePlaybackVolume(volume: Float): Float =
		if (nowPlayingVideoClipAudioActive) 0f else volume.coerceIn(0f, 1f)

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
				player.volume = effectivePlaybackVolume(
					startVolume + ((targetVolume - startVolume) * progress)
				)
				delay(16L)
			}
			player.volume = effectivePlaybackVolume(targetVolume)
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
				val progress = normalized.coerceIn(0f, 1f)
				val target = (it.duration * progress).toLong()
				it.seekTo(target)
				_uiState.update { state ->
					state.copy(progress = progress)
				}
				publishSeekEvent(progress)
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
			id.startsWith(AurralFlowSongIdPrefix) && !filePath.isNullOrEmpty() -> {
				filePath.toUri()
			}

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
