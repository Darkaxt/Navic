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
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mappers.toEntity
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.AndroidScrobbleManager
import paige.navic.domain.manager.AudioPlaybackArbitrator
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.AudioPlaybackOwner
import paige.navic.domain.models.CollectionShuffleQueueOrder
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.collectionShufflePlaybackPlan
import paige.navic.domain.models.discoverQueueRemovalIndexes
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.audioReverbPresetValue
import paige.navic.domain.models.audioFadeDurationMs
import paige.navic.domain.models.bassBoostStrengthPermille
import paige.navic.domain.models.medleyModeDurationMs
import paige.navic.domain.models.normalizedPlaybackPitch
import paige.navic.domain.models.normalizedPlaybackSpeed
import paige.navic.domain.models.pauseBetweenSongsDelayMs
import paige.navic.domain.models.mediaNotificationActions
import paige.navic.domain.models.playbackVolumeMultiplier
import paige.navic.domain.models.replayGainLoudnessBoostMillibels
import paige.navic.domain.models.replayGainVolumeMultiplier
import paige.navic.domain.models.shouldEnableBassBoost
import paige.navic.domain.models.shouldEnableAudioReverb
import paige.navic.domain.models.shouldAdvanceMedleyMode
import paige.navic.domain.models.shouldPauseBetweenSongsAfterTransition
import paige.navic.domain.models.shouldPausePlaybackWhenVolumeZero
import paige.navic.domain.models.shouldPauseForAudioPlaybackClaim
import paige.navic.domain.models.shouldFadePlaybackCommand
import paige.navic.domain.models.shouldReplaceQueuedMediaItemForDownloadAvailability
import paige.navic.domain.models.shouldRestartCurrentOnPrevious
import paige.navic.domain.models.shouldResumePlaybackWhenAudioDeviceAdded
import paige.navic.domain.models.shouldResumePlaybackAfterVolumeRestored
import paige.navic.domain.models.shouldSkipMediaAfterPlaybackError
import paige.navic.domain.models.songRadioQueue
import paige.navic.domain.models.SongRadioQueueDefaultSize
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
import kotlin.time.Duration.Companion.milliseconds
import coil3.PlatformContext as CoilPlatformContext

class AndroidMediaPlayerViewModel(
	private val application: Application,
	stateRepository: PlayerStateRepository,
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	private val sessionManager: SessionManager,
	private val platformContext: CoilPlatformContext,
	private val songRepository: SongRepository,
	private val artistPhotoCacheDao: ArtistPhotoCacheDao,
	private val musicBrainzArtworkRepository: MusicBrainzArtworkRepository,
	private val playbackOriginRepository: PlaybackOriginRepository,
	private val audioPlaybackArbitrator: AudioPlaybackArbitrator,
	private val preferenceManager: PreferenceManager,
	private val snackBarManager: SnackBarManager
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
	private var pendingSourceErrorRecovery: PendingSourceErrorRecovery? = null
	private var latestDownloadsById: Map<String, DownloadEntity> = emptyMap()
	private var nowPlayingVideoClipAudioActive = false
	private val playbackArtworkResolver = AndroidPlaybackArtworkResolver(
		preferenceManager = preferenceManager,
		musicBrainzArtworkRepository = musicBrainzArtworkRepository
	)
	private val playbackErrorNotifier = AndroidPlaybackErrorNotifier(snackBarManager)
	private val mediaItemFactory = AndroidMediaItemFactory(
		sessionManager = sessionManager,
		downloadManager = downloadManager,
		platformContext = platformContext,
		playbackArtworkForSong = playbackArtworkResolver::resolve,
		streamUriForSongId = ::getStreamUrl
	)
	private val nowPlayingBroadcaster = AndroidNowPlayingBroadcaster(
		application = application,
		sessionManager = sessionManager,
		playbackArtworkForSong = playbackArtworkResolver::resolve
	)
	private val playbackAssetPrefetcher = AndroidPlaybackAssetPrefetcher(
		scope = viewModelScope,
		downloadManager = downloadManager,
		musicBrainzArtworkRepository = musicBrainzArtworkRepository,
		upNextCount = { preferenceManager.nowPlayingUpNextCount },
		onCurrentSongArtworkPrefetched = { prefetchedSong ->
			val state = _uiState.value
			if (state.currentSong?.id == prefetchedSong.id) {
				nowPlayingBroadcaster.send(
					currentSong = state.currentSong,
					isPlaying = !state.isPaused,
					force = true
				)
			}
		}
	)
	private val playbackVolumeFader = AndroidPlaybackVolumeFader(
		scope = viewModelScope,
		effectiveVolume = ::effectivePlaybackVolume
	)
	private val playbackOriginRecorder = AndroidPlaybackOriginRecorder(viewModelScope, playbackOriginRepository)
	private val radioMediaItemFactory = AndroidRadioMediaItemFactory()
	private val queueAutoFiller = AndroidQueueAutoFiller(
		scope = viewModelScope,
		preferenceManager = preferenceManager,
		songRepository = songRepository,
		mediaItemFactory = mediaItemFactory,
		controller = { controller },
		state = { _uiState.value },
		isAvailable = { songId -> isAvailable(songId) },
		fetchServerSimilarSongs = ::fetchServerSimilarSongs,
		appendSongs = { songs ->
			_uiState.update { state -> state.copy(queue = state.queue + songs) }
		}
	)

	init {
		observePlaybackArtworkCache()
		connectToService()
		observeAudioPlaybackClaims()
	}

	private fun observePlaybackArtworkCache() {
		viewModelScope.launch {
			artistPhotoCacheDao.observeArtistPhotoCache().collectLatest { entries ->
				playbackArtworkResolver.updateArtistPhotoCache(entries)
				val state = _uiState.value
				nowPlayingBroadcaster.send(
					currentSong = state.currentSong,
					isPlaying = !state.isPaused,
					force = true
				)
			}
		}
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
						queueAutoFiller.maybeAutoFillQueue()
					}

					override fun onIsPlayingChanged(isPlaying: Boolean) {
						_uiState.update { it.copy(isPaused = !isPlaying) }
						val nowMillis = Clock.System.now().toEpochMilliseconds()
						playbackOriginRecorder.onPlaybackState(
							isPlaying = isPlaying,
							nowMillis = nowMillis
						)
						if (isPlaying) {
							startProgressLoop()
							queueAutoFiller.maybeAutoFillQueue()
							playbackAssetPrefetcher.prefetchCurrentSongArtwork(
								_uiState.value.currentSong,
								isPlaying = true
							)
							playbackAssetPrefetcher.prefetchUpcomingPlaybackAssets(_uiState.value, isPlaying = true)
						}
						nowPlayingBroadcaster.send(
							currentSong = _uiState.value.currentSong,
							isPlaying = isPlaying
						)
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						_uiState.update {
							it.copy(
								isLoading = playbackState == Player.STATE_BUFFERING ||
									pendingSourceErrorRecovery != null
							)
						}
						updatePlaybackDownloadProgress()
						updatePlaybackState()
						if (playbackState == Player.STATE_READY) {
							queueAutoFiller.maybeAutoFillQueue()
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
						if (recoverCurrentMediaItemFromDownloadedFile(this@apply)) {
							return
						}
						playbackErrorNotifier.notify(error)
						val shouldSkip = shouldSkipMediaAfterPlaybackError(
							skipMediaOnError = preferenceManager.skipMediaOnError,
							hasNextMediaItem = hasNextMediaItem()
						)
						if (shouldSkip) {
							clearPendingSourceErrorRecovery()
							seekToNextMediaItem()
							prepare()
							play()
						} else {
							beginPendingSourceErrorRecovery(this@apply)
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
					downloadManager.allDownloads,
					connectivityManager.isCellular,
					snapshotFlow { preferenceManager.streamingQualityWifi },
					snapshotFlow { preferenceManager.streamingQualityCellular },
					snapshotFlow { preferenceManager.isAdvancedTranscodingActive },
					snapshotFlow { preferenceManager.customMaxBitrateWifi },
					snapshotFlow { preferenceManager.customMaxBitrateCellular }
				) { it }.collectLatest { args ->
					@Suppress("UNCHECKED_CAST")
					val downloads = args[0] as List<DownloadEntity>
					latestDownloadsById = downloads.associateBy { download -> download.songId }
					updatePlaybackDownloadProgress()
					val downloadedMap = downloads
						.filter { download ->
							download.status == DownloadStatus.DOWNLOADED && download.filePath != null
						}
						.associate { download -> download.songId to download.filePath!! }
					val player = controller ?: return@collectLatest
					val currentIndex = player.currentMediaItemIndex

					for (i in 0 until player.mediaItemCount) {
						val item = player.getMediaItemAt(i)
						val id = item.mediaId
						val localPath = downloadedMap[id]
						val isRecoveringCurrentItem = pendingSourceErrorRecovery?.songId == id

						val isCurrentlyLocal = item.localConfiguration?.uri?.scheme == "file"
						if (
							!shouldReplaceQueuedMediaItemForDownloadAvailability(
								isCurrentItem = i == currentIndex,
								hasDownloadedFile = localPath != null,
								isCurrentlyLocal = isCurrentlyLocal,
								isRecoveringFromSourceError = isRecoveringCurrentItem
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
								val recovery = pendingSourceErrorRecovery?.takeIf { it.songId == id }
								val currentPosition = recovery?.positionMs ?: player.currentPosition
								player.replaceMediaItem(i, newItem)
								player.seekTo(i, currentPosition)
								if (recovery != null) {
									player.prepare()
									if (recovery.shouldResume) {
										claimMusicPlayback()
										player.play()
									}
									clearPendingSourceErrorRecovery()
								}
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
		playbackVolumeFader.cancel(controller)
		refreshPlaybackVolume()
	}

	override fun setPlaybackOrigin(origin: PlaybackOrigin?) {
		playbackOriginRecorder.setOrigin(origin)
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
		playbackAssetPrefetcher.prefetchCurrentSongArtwork(currentSong, isPlaying = controller.isPlaying)
		playbackAssetPrefetcher.prefetchUpcomingPlaybackAssets(_uiState.value, isPlaying = controller.isPlaying)
		updateProgress()
		nowPlayingBroadcaster.send(
			currentSong = _uiState.value.currentSong,
			isPlaying = controller.isPlaying
		)
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

	private fun beginPendingSourceErrorRecovery(player: MediaController) {
		val mediaId = player.currentMediaItem?.mediaId ?: return
		val song = _uiState.value.queue.getOrNull(player.currentMediaItemIndex)
			?: _uiState.value.currentSong?.takeIf { it.id == mediaId }
			?: return
		val songDurationMs = song.duration.inWholeMilliseconds
		val fallbackPositionMs = if (songDurationMs > 0L) {
			(_uiState.value.progress * songDurationMs).toLong()
		} else {
			0L
		}
		val positionMs = player.currentPosition.takeIf { it > 0L } ?: fallbackPositionMs
		pendingSourceErrorRecovery = PendingSourceErrorRecovery(
			songId = mediaId,
			positionMs = positionMs.coerceAtLeast(0L),
			shouldResume = player.playWhenReady || !_uiState.value.isPaused
		)
		updatePlaybackDownloadProgress()
		downloadManager.prefetchPlaybackSongs(listOf(song))
	}

	private fun recoverCurrentMediaItemFromDownloadedFile(player: MediaController): Boolean {
		val recovery = pendingSourceErrorRecovery
		val mediaId = player.currentMediaItem?.mediaId ?: return false
		val localPath = downloadManager.getDownloadedFilePath(mediaId) ?: return false
		val currentItem = player.currentMediaItem ?: return false
		if (currentItem.localConfiguration?.uri?.scheme == "file") return false

		val effectiveRecovery = recovery?.takeIf { it.songId == mediaId }
			?: PendingSourceErrorRecovery(
				songId = mediaId,
				positionMs = player.currentPosition.coerceAtLeast(0L),
				shouldResume = player.playWhenReady || !_uiState.value.isPaused
			)
		val index = player.currentMediaItemIndex
		player.replaceMediaItem(
			index,
			currentItem.buildUpon()
				.setUri(File(localPath).toUri())
				.build()
		)
		player.seekTo(index, effectiveRecovery.positionMs)
		player.prepare()
		if (effectiveRecovery.shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		clearPendingSourceErrorRecovery()
		return true
	}

	private fun updatePlaybackDownloadProgress() {
		val recovery = pendingSourceErrorRecovery
		val songId = recovery?.songId ?: _uiState.value.currentSong?.id
		val shouldShowProgress = recovery != null || _uiState.value.isLoading
		val download = songId?.let { latestDownloadsById[it] }
		val progress = when (download?.status) {
			DownloadStatus.DOWNLOADING -> download.progress.coerceIn(0f, 1f)
			DownloadStatus.QUEUED -> 0f
			null -> if (recovery != null) 0f else null
			DownloadStatus.DOWNLOADED,
			DownloadStatus.FAILED,
			DownloadStatus.NOT_DOWNLOADED -> null
		}
			?.takeIf { shouldShowProgress }
		_uiState.update { state ->
			state.copy(
				isLoading = if (recovery != null) progress != null else state.isLoading,
				playbackDownloadProgress = progress
			)
		}
	}

	private fun clearPendingSourceErrorRecovery() {
		pendingSourceErrorRecovery = null
		_uiState.update { state ->
			state.copy(
				isLoading = false,
				playbackDownloadProgress = null
			)
		}
	}

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
				playbackOriginRecorder.checkpointIfNeeded(Clock.System.now().toEpochMilliseconds())
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

	override fun addToQueueSingle(song: DomainSong, notify: Boolean) {
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
			if (notify) snackBarManager.notifyAddedToQueue()
		}
	}

	override fun addToQueue(collection: DomainSongCollection, notify: Boolean) {
		val songs = if (collection is DomainAlbum) {
			collection.songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
		} else {
			collection.songs
		}
		addToQueue(songs, notify)
	}

	override fun addToQueue(songs: List<DomainSong>, notify: Boolean) {
		viewModelScope.launch {
			val items = withContext(Dispatchers.Default) { songs.map { it.toMediaItem() } }
			val player = controller
			player?.addMediaItems(items)
			_uiState.update { state ->
				val newQueue = state.queue + songs
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) songs.firstOrNull() else state.currentSong
				)
			}
			player?.let(::playPendingIndexIfAvailable)
			if (notify) snackBarManager.notifyAddedToQueue()
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
			playbackOriginRecorder.setOriginNow(null)
			pendingPlayIndex = null
			queueAutoFiller.cancel()
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
			snackBarManager.notifyPlayNext()
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
			snackBarManager.notifyPlayNext()
		}
	}

	override fun startSongRadio(song: DomainSong) {
		viewModelScope.launch {
			playbackOriginRecorder.setOriginNow(null)
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
				queueAutoFiller.cancel()
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
			playbackOriginRecorder.setOriginNow(null)
			val radioItem = radioMediaItemFactory.create(radio)

			controller?.let { player ->
				player.stop()
				player.clearMediaItems()
				player.setMediaItem(radioItem.mediaItem)
				player.prepare()
				claimMusicPlayback()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = listOf(radioItem.song),
					currentIndex = 0,
					currentSong = radioItem.song,
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
			playbackVolumeFader.cancel(player)
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
			playbackVolumeFader.start(
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
			playbackVolumeFader.cancel(player)
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
			playbackVolumeFader.start(
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

	private fun effectivePlaybackVolume(volume: Float): Float =
		if (nowPlayingVideoClipAudioActive) 0f else volume.coerceIn(0f, 1f)

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
			playbackVolumeFader.cancel(controller)
			queueAutoFiller.cancel()
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

	private fun DomainSong.toMediaItem(): MediaItem =
		mediaItemFactory.toMediaItem(this)
}

private data class PendingSourceErrorRecovery(
	val songId: String,
	val positionMs: Long,
	val shouldResume: Boolean
)
