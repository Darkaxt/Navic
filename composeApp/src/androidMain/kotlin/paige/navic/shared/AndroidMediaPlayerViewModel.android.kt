package paige.navic.shared

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.interactors.DefaultPlaybackQueueStateReducer
import paige.navic.domain.interactors.PlaybackQueueInteractor
import paige.navic.domain.manager.AudioPlaybackOwnershipClaim
import paige.navic.domain.manager.AudioPlaybackOwnershipCoordinator
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.NavidromeAvailability
import paige.navic.domain.manager.NavidromeAvailabilityManager
import paige.navic.domain.manager.NavidromeOutageTrigger
import paige.navic.domain.manager.OfflineModeCoordinator
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.models.AudioPlaybackOwner
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.QueueSelectionOrigin
import paige.navic.domain.models.QueueSelectionRequest
import paige.navic.domain.models.audioFadeDurationMs
import paige.navic.domain.models.normalizedPlaybackPitch
import paige.navic.domain.models.normalizedPlaybackSpeed
import paige.navic.domain.models.shouldPauseForAudioPlaybackClaim
import paige.navic.domain.models.shouldFadePlaybackCommand
import paige.navic.domain.models.shouldHandlePlaybackErrorVisibly
import paige.navic.domain.models.shouldReplaceQueuedMediaItemForDownloadAvailability
import paige.navic.domain.models.shouldRestartCurrentOnPrevious
import paige.navic.domain.models.SongRadioQueueDefaultSize
import paige.navic.domain.models.settings.OfflineMode
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.ui.core.PlayerUiState
import paige.navic.ui.core.withQueueSongReplacement
import paige.navic.util.core.Logger
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import coil3.PlatformContext as CoilPlatformContext

class AndroidMediaPlayerViewModel(
	private val application: Application,
	stateRepository: PlayerStateRepository,
	private val albumDao: AlbumDao,
	private val playbackQueueInteractor: PlaybackQueueInteractor,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	private val offlineModeCoordinator: OfflineModeCoordinator,
	private val navidromeAvailabilityManager: NavidromeAvailabilityManager,
	private val sessionManager: SessionManager,
	private val platformContext: CoilPlatformContext,
	private val artistPhotoCacheDao: ArtistPhotoCacheDao,
	private val musicBrainzArtworkRepository: MusicBrainzArtworkRepository,
	private val playbackOriginRepository: PlaybackOriginRepository,
	private val audioPlaybackOwnershipCoordinator: AudioPlaybackOwnershipCoordinator,
	private val preferenceManager: PreferenceManager,
	private val snackBarManager: SnackBarManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	downloadManager = downloadManager,
	connectivityManager = connectivityManager,
	preferenceManager = preferenceManager
) {
	private var controller: MediaController? = null
	private var playbackClaim: AudioPlaybackOwnershipClaim? = null
	private val mediaControllerConnection: AndroidMediaControllerConnection = DefaultAndroidMediaControllerConnection(
		application = application,
		onConnected = { connectedController ->
			controller = connectedController
			Logger.i("MediaPlayer", "Media controller connected")
			setupController()
		},
		onConnectionFailed = { error ->
			controller = null
			Logger.e("MediaPlayer", "Failed to connect media controller", error)
		},
		onDisconnected = { disconnectedController ->
			if (controller === disconnectedController) controller = null
			Logger.w("MediaPlayer", "Media controller disconnected; reconnecting")
			releaseMusicPlayback()
		}
	)

	private var loadingCollectionId: String? = null

	private var pendingQueueSelection: QueueSelectionRequest? = null
	private var latestDownloadsById: Map<String, DownloadEntity> = emptyMap()
	private var nowPlayingVideoClipAudioActive = false
	private val playbackArtworkResolver = AndroidPlaybackArtworkResolver(
		preferenceManager = preferenceManager,
		musicBrainzArtworkRepository = musicBrainzArtworkRepository
	)
	private val playbackErrorNotifier = AndroidPlaybackErrorNotifier(snackBarManager)
	private val playbackDiagnostics = AndroidPlaybackDiagnosticsLogger()
	private val audioEffectsController: AndroidAudioEffectsController =
		DefaultAndroidAudioEffectsController(application, preferenceManager)
	private val mediaItemFactory = AndroidMediaItemFactory(
		sessionManager = sessionManager,
		downloadManager = downloadManager,
		platformContext = platformContext,
		playbackArtworkForSong = playbackArtworkResolver::resolve,
		streamUriForSongId = ::getStreamUrl
	)
	private val staleSongResolver = createStalePlaybackSongResolver(sessionManager, playbackQueueInteractor)
	private val playbackRecovery = AndroidStablePlaybackRecoveryCoordinator(
		scope = viewModelScope,
		downloadManager = downloadManager,
		navidromeAvailabilityManager = navidromeAvailabilityManager,
		diagnostics = playbackDiagnostics,
		isAvailable = ::isAvailable,
		skipMediaOnError = { preferenceManager.skipMediaOnError },
		staleSongResolver = staleSongResolver::resolve,
		onQueueSongReplaced = ::replaceQueuedSong,
		mediaItemForSong = mediaItemFactory::toMediaItem,
		claimMusicPlayback = ::claimMusicPlayback,
		notifyPlaybackError = playbackErrorNotifier::notify,
		notifyFailedDownload = playbackErrorNotifier::notifyFailedDownload,
		notifySongNotFound = playbackErrorNotifier::notifySongNotFound,
		markRecoveryPending = ::markPlaybackRecoveryPending,
		clearRecoveryUi = ::clearPlaybackRecoveryUi
	)

	private fun replaceQueuedSong(index: Int, replacement: DomainSong) =
		_uiState.update { state -> state.withQueueSongReplacement(index, replacement) }

	private val downloadedMediaRecovery: AndroidDownloadedMediaRecovery =
		DefaultAndroidDownloadedMediaRecovery(
			downloadManager = downloadManager,
			diagnostics = playbackDiagnostics,
			claimMusicPlayback = ::claimMusicPlayback
		)
	private val playbackStateSynchronizer: AndroidPlaybackStateSynchronizer =
		DefaultAndroidPlaybackStateSynchronizer(
			scope = viewModelScope,
			controller = { controller },
			mediaItemForSong = mediaItemFactory::toMediaItem,
			claimMusicPlayback = ::claimMusicPlayback
		)
	private val nowPlayingBroadcaster = AndroidNowPlayingBroadcaster(
		application = application,
		sessionManager = sessionManager,
		playbackArtworkForSong = playbackArtworkResolver::resolve
	)
	private val playbackAssetPrefetcher = AndroidPlaybackAssetPrefetcher(
		scope = viewModelScope,
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
	private val queueStateReducer = DefaultPlaybackQueueStateReducer
	private val queueAutoFiller = AndroidQueueAutoFiller(
		scope = viewModelScope,
		preferenceManager = preferenceManager,
		loadLibrarySongs = playbackQueueInteractor::librarySongs,
		mediaItemFactory = mediaItemFactory,
		controller = { controller },
		state = { _uiState.value },
		isAvailable = { songId -> isAvailable(songId) },
		fetchServerSimilarSongs = playbackQueueInteractor::serverSimilarSongs,
		appendSongs = { songs ->
			_uiState.update { state -> state.copy(queue = state.queue + songs) }
		}
	)
	private val bulkPlaybackCoordinator = AndroidBulkPlaybackCoordinator(
		scope = viewModelScope,
		controller = { controller },
		state = { _uiState.value },
		publishState = { _uiState.value = it },
		mediaItemFactory = mediaItemFactory,
		playbackStateSynchronizer = playbackStateSynchronizer,
		clearPlaybackRecovery = playbackRecovery::clear,
		clearPendingQueueSelection = { pendingQueueSelection = null },
		cancelQueueAutoFill = queueAutoFiller::cancel,
		claimMusicPlayback = ::claimMusicPlayback
	)

	init {
		observePlaybackArtworkCache()
		mediaControllerConnection.connect()
		observeAudioPlaybackClaims()
		observeNavidromeAvailability()
	}

	private fun observeNavidromeAvailability() {
		viewModelScope.launch {
			connectivityManager.isNetworkAvailable.collectLatest { networkAvailable ->
				if (!networkAvailable && sessionManager.isLoggedIn.value) {
					navidromeAvailabilityManager.reportUnavailable(NavidromeOutageTrigger.RawNetworkLost)
				}
			}
		}
		viewModelScope.launch {
			combine(
				navidromeAvailabilityManager.state,
				connectivityManager.isOnline,
				offlineModeCoordinator.state
			) { availability, isOnline, offlineMode ->
				Triple(availability, isOnline, offlineMode.selectedMode)
			}.collectLatest { (availability, isOnline, selectedMode) ->
				val player = controller ?: return@collectLatest
				when {
					availability is NavidromeAvailability.Unavailable ->
						playbackRecovery.handleServiceUnavailable(
							player,
							_uiState.value,
							"navidrome-unavailable"
						)

					!isOnline && selectedMode != OfflineMode.Auto ->
						playbackRecovery.handleServiceUnavailable(
							player,
							_uiState.value,
							"selected-offline-mode"
						)

					isOnline -> playbackRecovery.handleServiceRestored(player, _uiState.value)
				}
			}
		}
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
			audioPlaybackOwnershipCoordinator.activeClaim.collectLatest { claim ->
				val claimedOwner = claim?.owner ?: return@collectLatest
				if (controller == null) return@collectLatest
				if (
					shouldPauseForAudioPlaybackClaim(
						currentOwner = AudioPlaybackOwner.Music,
						claimedOwner = claimedOwner,
						isPlaying = playbackClaim != null
					)
				) {
					playbackClaim = null
					pause()
				}
			}
		}
	}

	private fun claimMusicPlayback() {
		playbackClaim = audioPlaybackOwnershipCoordinator.claim(AudioPlaybackOwner.Music)
	}

	private fun releaseMusicPlayback() {
		playbackClaim?.let(audioPlaybackOwnershipCoordinator::release)
		playbackClaim = null
	}

	private fun getStreamUrl(id: String): Uri {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
		}
		val container = if (isCellular) preferenceManager.streamingQualityCellular.containerAndroid else preferenceManager.streamingQualityWifi.containerAndroid

		return sessionManager.getStreamUrl(id, bitrate, container)
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
						playbackRecovery.onMediaItemTransition(mediaItem, currentMediaItemIndex)

						if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
							mediaItem?.mediaId?.let { id ->
								if (!isAvailable(id)) {
									playbackRecovery.handleUnavailableAutomaticTransition(this@apply, _uiState.value)
								}
							}
						}
						queueAutoFiller.maybeAutoFillQueue()
					}

					override fun onIsPlayingChanged(isPlaying: Boolean) {
						_uiState.update { it.copy(isPaused = !playWhenReady) }
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
						playbackDiagnostics.onIsPlayingChanged(
							this@apply,
							isPlaying,
							_uiState.value.currentSong,
							playbackRecovery.pendingSongId
						)
					}

					override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
						_uiState.update { it.copy(isPaused = !playWhenReady) }
						if (!playWhenReady) releaseMusicPlayback()
						playbackDiagnostics.onPlayWhenReadyChanged(
							this@apply,
							playWhenReady,
							reason,
							_uiState.value.currentSong,
							playbackRecovery.pendingSongId
						)
					}

					override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
						playbackDiagnostics.onPlaybackSuppressionReasonChanged(
							this@apply,
							playbackSuppressionReason,
							_uiState.value.currentSong,
							playbackRecovery.pendingSongId
						)
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						_uiState.update {
							it.copy(
								isLoading = playbackState == Player.STATE_BUFFERING ||
									playbackRecovery.pendingSongId != null
							)
						}
						updatePlaybackDownloadProgress()
						updatePlaybackState()
						if (playbackState == Player.STATE_READY) {
							queueAutoFiller.maybeAutoFillQueue()
						}
						if (playbackState == Player.STATE_ENDED) releaseMusicPlayback()
					}

					override fun onPlayerError(error: PlaybackException) {
						val currentUiState = _uiState.value
						Logger.w(
							"MediaPlayer",
							"Playback error mediaId=${currentMediaItem?.mediaId} " +
								"index=$currentMediaItemIndex " +
								"code=${error.errorCodeName} message=${error.message}",
							error
						)
						playbackDiagnostics.onPlayerError(this@apply, error, currentUiState.currentSong)
						if (
							!shouldHandlePlaybackErrorVisibly(
								playWhenReady = playWhenReady,
								isUiPaused = currentUiState.isPaused,
								hasPendingSourceErrorRecovery = playbackRecovery.pendingSongId != null
							)
						) {
							_uiState.update { state ->
								state.copy(
									isLoading = false,
									playbackDownloadProgress = null
								)
							}
							return
						}
						if (playbackRecovery.isServiceUnavailable(error)) {
							playbackRecovery.handlePlayerError(this@apply, currentUiState, error)
							return
						}
						if (downloadedMediaRecovery.recover(this@apply)) {
							return
						}
						playbackRecovery.handlePlayerError(this@apply, currentUiState, error)
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
				when {
					navidromeAvailabilityManager.state.value is NavidromeAvailability.Unavailable ->
						playbackRecovery.handleServiceUnavailable(
							this,
							_uiState.value,
							"controller-connected-offline"
						)

					connectivityManager.isOnline.value ->
						playbackRecovery.handleServiceRestored(this, _uiState.value)
				}
				updatePlaybackProperties(currentTracks)
				refreshAudioEffects()
				playPendingQueueSelectionIfAvailable(this)

				downloadManager.allDownloads.first()
				playbackStateSynchronizer.onControllerReady()

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
					playbackDiagnostics.onRecoveryDownloadStatus(
						playbackRecovery.pendingSongId,
						playbackRecovery.pendingSongId?.let(latestDownloadsById::get),
						playbackRecovery.pendingSongId != null
					)
					playbackRecovery.handleDownloadSnapshot(
						player = player,
						state = _uiState.value,
						downloadsById = latestDownloadsById
					)
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
								isCurrentlyLocal = isCurrentlyLocal,
								isRecoveringFromSourceError = false
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

						newItem?.let { player.replaceMediaItem(i, it) }
					}
				}
			}
		}
	}

	override fun refreshAudioEffects() {
		audioEffectsController.refresh(
			player = controller,
			song = _uiState.value.currentSong,
			forceMuted = nowPlayingVideoClipAudioActive
		)
	}

	override fun refreshPlaybackVolume() {
		audioEffectsController.refreshVolume(
			player = controller,
			song = _uiState.value.currentSong,
			forceMuted = nowPlayingVideoClipAudioActive
		)
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
				isPaused = !controller.playWhenReady,
				isShuffleEnabled = controller.shuffleModeEnabled,
				repeatMode = controller.repeatMode
			)
		}
		refreshPlaybackVolume()
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

	private fun updatePlaybackDownloadProgress() {
		val recoverySongId = playbackRecovery.pendingSongId
		if (playbackRecovery.isWaitingForService) {
			_uiState.update { state -> state.copy(playbackDownloadProgress = null) }
			return
		}
		val songId = recoverySongId ?: _uiState.value.currentSong?.id
		val shouldShowProgress = recoverySongId != null || _uiState.value.isLoading
		val download = songId?.let { latestDownloadsById[it] }
		val progress = when (download?.status) {
			DownloadStatus.DOWNLOADING -> download.progress.coerceIn(0f, 1f)
			DownloadStatus.QUEUED -> 0f
			null -> if (recoverySongId != null) 0f else null
			DownloadStatus.DOWNLOADED,
			DownloadStatus.FAILED,
			DownloadStatus.NOT_DOWNLOADED -> null
		}
			?.takeIf { shouldShowProgress }
		_uiState.update { state ->
			state.copy(playbackDownloadProgress = progress)
		}
	}

	private fun markPlaybackRecoveryPending() {
		_uiState.update { state -> state.copy(isLoading = true) }
		updatePlaybackDownloadProgress()
	}

	private fun clearPlaybackRecoveryUi() {
		_uiState.update { state ->
			state.copy(
				isLoading = false,
				playbackDownloadProgress = null
			)
		}
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		playbackStateSynchronizer.sync(state)
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
			_uiState.update { state -> queueStateReducer.append(state, listOf(song)) }
			player?.let(::playPendingQueueSelectionIfAvailable)
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
			_uiState.update { state -> queueStateReducer.append(state, songs) }
			player?.let(::playPendingQueueSelectionIfAvailable)
			if (notify) snackBarManager.notifyAddedToQueue()
		}
	}

	override fun removeFromQueue(index: Int) {
		viewModelScope.launch {
			controller?.removeMediaItem(index)
			_uiState.update { state -> queueStateReducer.removeAt(state, index) }
		}
	}

	override fun applyDiscoverQueueFilter(onComplete: (removedCount: Int) -> Unit) {
		viewModelScope.launch {
			val initialState = _uiState.value
			if (initialState.currentIndex !in initialState.queue.indices) {
				onComplete(0)
				return@launch
			}

			val initialRemovalIndexes = withContext(Dispatchers.IO) {
				playbackQueueInteractor.discoverRemovalIndexes(
					queue = initialState.queue,
					currentIndex = initialState.currentIndex
				)
			}
			val state = _uiState.value
			val removalIndexes = if (state === initialState) initialRemovalIndexes else withContext(Dispatchers.IO) {
				playbackQueueInteractor.discoverRemovalIndexes(state.queue, state.currentIndex)
			}
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
			_uiState.update { state -> queueStateReducer.move(state, fromIndex, toIndex) }
		}
	}

	override fun clearQueue() {
		viewModelScope.launch {
			playbackOriginRecorder.setOriginNow(null)
			pendingQueueSelection = null
			playbackRecovery.clear()
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

	override fun selectQueueItem(index: Int, playWhenReady: Boolean, origin: QueueSelectionOrigin) {
		val request = QueueSelectionRequest(index, playWhenReady, origin)
		viewModelScope.launch {
			if (index < 0) return@launch
			playbackRecovery.clear("queue-selection")

			val player = controller
			if (player == null || !selectQueueItemIfAvailable(player, request)) {
				pendingQueueSelection = request
			}
		}
	}

	private fun selectQueueItemIfAvailable(player: MediaController, request: QueueSelectionRequest): Boolean {
		if (request.index !in 0 until player.mediaItemCount) return false

		playbackDiagnostics.onQueueSelection(request, _uiState.value.queue.getOrNull(request.index))
		player.seekTo(request.index, 0L)
		if (request.playWhenReady) claimMusicPlayback()
		player.playWhenReady = request.playWhenReady
		return true
	}

	private fun playPendingQueueSelectionIfAvailable(player: MediaController) {
		val request = pendingQueueSelection ?: return
		if (selectQueueItemIfAvailable(player, request)) {
			pendingQueueSelection = null
		}
	}

	override fun playCollection(collection: DomainSongCollection, startSong: DomainSong) {
		viewModelScope.launch {
			playbackRecovery.clear("play-collection")
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
			_uiState.update { state -> queueStateReducer.insertNext(state, listOf(song)) }
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
			_uiState.update { state -> queueStateReducer.insertNext(state, newCollection) }
			snackBarManager.notifyPlayNext()
		}
	}

	override fun startSongRadio(song: DomainSong) {
		viewModelScope.launch {
			playbackRecovery.clear("song-radio")
			playbackOriginRecorder.setOriginNow(null)
			try {
				val radioQueue = withContext(Dispatchers.IO) {
					playbackQueueInteractor.songRadio(
						seedSong = song,
						limit = SongRadioQueueDefaultSize,
						isAvailable = ::isAvailable
					)
				}
				if (radioQueue.isEmpty()) return@launch

				val mediaItems = withContext(Dispatchers.Default) {
					radioQueue.map { it.toMediaItem() }
				}
				pendingQueueSelection = null
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

	override fun playRadio(radio: DomainRadio) {
		viewModelScope.launch {
			playbackRecovery.clear("play-radio")
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
		playAll(collection.songs, forceShuffle = true)
	}

	override fun playAll(songs: List<DomainSong>, forceShuffle: Boolean) {
		bulkPlaybackCoordinator.playAll(songs, forceShuffle)
	}

	override fun pause() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val player = controller ?: return@launch
			playbackRecovery.onUserPause()
			playbackVolumeFader.cancel(player)
			val fadeDurationMs = preferenceManager.audioFadeDurationMs
			if (
				!shouldFadePlaybackCommand(
					audioFadeDurationMs = fadeDurationMs,
					alreadyInTargetState = !player.isPlaying
				)
			) {
				player.pause()
				releaseMusicPlayback()
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
					releaseMusicPlayback()
					player.volume = effectivePlaybackVolume(originalVolume)
				}
			)
		}
	}

	override fun resume() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val player = controller ?: return@launch
			if (playbackRecovery.onUserResume(player, _uiState.value)) return@launch
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

			val targetVolume = audioEffectsController.targetVolume(
				song = _uiState.value.currentSong,
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
			playbackRecovery.clear("next")
			if (controller?.hasNextMediaItem() == true) controller?.seekToNextMediaItem()
		}
	}

	override fun previous() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			playbackRecovery.clear("previous")
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
		playbackVolumeFader.cancel(controller)
		queueAutoFiller.cancel()
		releaseMusicPlayback()
		mediaControllerConnection.close()
		controller = null
		super.onCleared()
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
		return audioEffectsController.openSystemEqualizer(controller)
	}

	private fun DomainSong.toMediaItem(): MediaItem =
		mediaItemFactory.toMediaItem(this)
}
