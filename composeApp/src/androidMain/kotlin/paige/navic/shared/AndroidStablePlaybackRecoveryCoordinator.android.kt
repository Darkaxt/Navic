package paige.navic.shared

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.NavidromeAvailabilityManager
import paige.navic.domain.manager.NavidromeOutageTrigger
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.NavidromeFailureDisposition
import paige.navic.domain.models.OfflinePlaybackFallbackResolution
import paige.navic.domain.models.PendingPlaybackRecovery
import paige.navic.domain.models.PlaybackDownloadRequestResult
import paige.navic.domain.models.PlaybackRecoveryDownloadLifecycle
import paige.navic.domain.models.PlaybackRecoveryResolution
import paige.navic.domain.models.StalePlaybackProbeResolution
import paige.navic.domain.models.classifyNavidromeFailure
import paige.navic.domain.models.firstPlayableUpcomingIndex
import paige.navic.domain.models.playbackFailureTargetIndex
import paige.navic.domain.models.playbackRecoveryResolution
import paige.navic.domain.models.resolveOfflinePlaybackFallback
import paige.navic.domain.models.shouldProbeStalePlaybackSong
import paige.navic.ui.core.PlayerUiState
import java.io.File

internal class AndroidStablePlaybackRecoveryCoordinator(
	private val scope: CoroutineScope,
	private val downloadManager: DownloadManager,
	private val navidromeAvailabilityManager: NavidromeAvailabilityManager,
	private val diagnostics: AndroidPlaybackDiagnosticsLogger,
	private val isAvailable: (String) -> Boolean,
	private val skipMediaOnError: () -> Boolean,
	private val staleSongResolver: suspend (DomainSong) -> StalePlaybackProbeResolution,
	private val onQueueSongReplaced: (Int, DomainSong) -> Unit,
	private val mediaItemForSong: (DomainSong) -> MediaItem,
	private val claimMusicPlayback: () -> Unit,
	private val notifyPlaybackError: (PlaybackException) -> Unit,
	private val notifyFailedDownload: () -> Unit,
	private val notifySongNotFound: () -> Unit,
	private val markRecoveryPending: () -> Unit,
	private val clearRecoveryUi: () -> Unit
) {
	private var refreshedRemoteSourceKey: RemoteSourceRefreshKey? = null
	private var staleSongProbeKey: RemoteSourceRefreshKey? = null
	private var staleSongProbeJob: Job? = null
	private var pending: PendingPlaybackRecovery? = null
	private var pendingError: PlaybackException? = null
	private var pendingServiceOutage = false

	val pendingSongId: String?
		get() = pending?.songId
	val isWaitingForService: Boolean
		get() = pendingServiceOutage

	fun onMediaItemTransition(mediaItem: MediaItem?, currentIndex: Int) {
		val activeKey = mediaItem?.mediaId?.let { mediaId ->
			RemoteSourceRefreshKey(mediaId, currentIndex)
		}
		refreshedRemoteSourceKey = refreshedRemoteSourceKey?.takeIf { it == activeKey }
		pending?.let { recovery ->
			if (mediaItem?.mediaId != recovery.songId || currentIndex != recovery.queueIndex) {
				clear("media-item-transition")
			}
		}
	}

	fun handleUnavailableAutomaticTransition(player: MediaController, state: PlayerUiState) {
		handleServiceUnavailable(player, state, "unavailable-auto-transition")
	}

	fun isServiceUnavailable(error: PlaybackException): Boolean =
		error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
			error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
			classifyNavidromeFailure(error) == NavidromeFailureDisposition.ServiceUnavailable

	fun handleServiceUnavailable(
		player: MediaController,
		state: PlayerUiState,
		reason: String,
		error: PlaybackException? = null
	) {
		val currentIndex = player.currentMediaItemIndex
		val song = state.queue.getOrNull(currentIndex) ?: state.currentSong ?: return
		if (pendingServiceOutage && pending?.songId == song.id && pending?.queueIndex == currentIndex) return
		if (pending != null) clear("service-outage-replaces-recovery")

		val recovery = pendingRecovery(player, state, song, currentIndex, reason)
		val availableSongIds = state.queue
			.mapNotNull { queuedSong ->
				queuedSong.id.takeIf { downloadManager.getDownloadedFilePath(queuedSong.id) != null }
			}
			.toSet()
		val resolution = resolveOfflinePlaybackFallback(
			currentIndex = currentIndex,
			queueSongIds = state.queue.map(DomainSong::id),
			upcomingIndexes = state.upcomingIndexes,
			availableSongIds = availableSongIds,
			currentUsesLocalFile = player.currentMediaItem?.localConfiguration?.uri?.scheme == "file"
		)

		when (resolution) {
			OfflinePlaybackFallbackResolution.KeepCurrent -> {
				val localPath = downloadManager.getDownloadedFilePath(song.id)
				if (localPath == null) {
					diagnostics.onPlaybackRecoveryDecision(
						event = "offline-current-already-local",
						song = song,
						currentIndex = currentIndex,
						targetIndex = currentIndex,
						reason = reason,
						deferredCount = 0,
						fallbackAvailable = true
					)
					return
				}
				pending = recovery
				pendingError = error
				pendingServiceOutage = true
				resumeCurrentFromLocalFile(player, recovery, localPath, "offline-current-cache")
			}

			OfflinePlaybackFallbackResolution.Hold -> {
				pending = recovery
				pendingError = error
				pendingServiceOutage = true
				markRecoveryPending()
				diagnostics.onRecoveryPending(song, recovery.positionMs, recovery.shouldResume)
				diagnostics.onPlaybackRecoveryDecision(
					event = "offline-no-cached-fallback",
					song = song,
					currentIndex = currentIndex,
					targetIndex = null,
					reason = reason,
					deferredCount = 0,
					fallbackAvailable = false
				)
				player.pause()
			}

			is OfflinePlaybackFallbackResolution.PlayUpcoming -> {
				val targetSong = state.queue.getOrNull(resolution.targetIndex) ?: return
				val localPath = downloadManager.getDownloadedFilePath(targetSong.id) ?: return
				diagnostics.onPlaybackRecoveryDecision(
					event = "offline-cached-upcoming",
					song = song,
					currentIndex = currentIndex,
					targetIndex = resolution.targetIndex,
					reason = reason,
					deferredCount = 0,
					fallbackAvailable = true
				)
				playUpcomingFromLocalFile(player, resolution.targetIndex, localPath, recovery.shouldResume)
			}
		}
	}

	fun handleServiceRestored(player: MediaController, state: PlayerUiState) {
		val recovery = pending?.takeIf { pendingServiceOutage } ?: return
		if (
			player.currentMediaItem?.mediaId != recovery.songId ||
			player.currentMediaItemIndex != recovery.queueIndex
		) {
			clear("service-restored-after-fallback")
			return
		}
		val song = state.queue.getOrNull(recovery.queueIndex)
			?: state.currentSong?.takeIf { it.id == recovery.songId }
			?: run {
				clear("service-restored-missing-song")
				return
			}

		player.replaceMediaItem(recovery.queueIndex, mediaItemForSong(song))
		player.seekTo(recovery.queueIndex, recovery.positionMs)
		player.prepare()
		if (recovery.shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		diagnostics.onPlaybackRetry(
			songId = song.id,
			title = song.title,
			index = recovery.queueIndex,
			positionMs = recovery.positionMs,
			shouldResume = recovery.shouldResume,
			source = "service-restored"
		)
		clear("service-restored")
	}

	fun handlePlayerError(player: MediaController, state: PlayerUiState, error: PlaybackException) {
		if (isServiceUnavailable(error)) {
			navidromeAvailabilityManager.reportUnavailable(NavidromeOutageTrigger.Playback, error)
			handleServiceUnavailable(player, state, error.errorCodeName, error)
			return
		}
		val currentIndex = player.currentMediaItemIndex
		val currentSongId = player.currentMediaItem?.mediaId
		if (pending?.let { it.songId == currentSongId && it.queueIndex == currentIndex } == true) return
		if (beginStaleSongProbe(player, state, error)) return
		if (refreshCurrentRemoteMediaItem(player, state)) return

		val song = state.queue.getOrNull(currentIndex) ?: state.currentSong
		if (song == null) {
			diagnostics.onHardPlaybackFailure(player, error, null, "no-recovery-song")
			notifyPlaybackError(error)
			clear("no-recovery-song")
			return
		}
		beginDownloadRecovery(
			player = player,
			state = state,
			song = song,
			currentIndex = currentIndex,
			reason = error.errorCodeName,
			error = error
		)
	}

	private fun beginStaleSongProbe(
		player: MediaController,
		state: PlayerUiState,
		error: PlaybackException
	): Boolean {
		val currentItem = player.currentMediaItem ?: return false
		if (
			!shouldProbeStalePlaybackSong(
				errorCodeName = error.errorCodeName,
				usesLocalFile = currentItem.localConfiguration?.uri?.scheme == "file"
			)
		) {
			return false
		}
		val currentIndex = player.currentMediaItemIndex
		val key = RemoteSourceRefreshKey(currentItem.mediaId, currentIndex)
		if (staleSongProbeKey == key) return false
		val song = state.queue.getOrNull(currentIndex)
			?: state.currentSong?.takeIf { it.id == currentItem.mediaId }
			?: return false
		val recovery = pendingRecovery(player, state, song, currentIndex, error.errorCodeName)

		staleSongProbeKey = key
		pending = recovery
		pendingError = error
		pendingServiceOutage = false
		markRecoveryPending()
		diagnostics.onRecoveryPending(song, recovery.positionMs, recovery.shouldResume)
		diagnostics.onStaleSongProbeStarted(song, currentIndex, error.errorCodeName)
		staleSongProbeJob?.cancel()
		staleSongProbeJob = scope.launch {
			val resolution = staleSongResolver(song)
			val activeRecovery = pending?.takeIf { pendingRecovery ->
				pendingRecovery.songId == recovery.songId &&
					pendingRecovery.queueIndex == recovery.queueIndex
			}
			val appliesToCurrentItem =
				activeRecovery != null &&
					player.currentMediaItem?.mediaId == recovery.songId &&
					player.currentMediaItemIndex == recovery.queueIndex
			diagnostics.onStaleSongProbeResult(
				songId = recovery.songId,
				index = recovery.queueIndex,
				resolution = resolution,
				appliesToCurrentItem = appliesToCurrentItem
			)
			if (!appliesToCurrentItem) {
				clear("stale-song-probe-result-obsolete")
				return@launch
			}

			when (resolution) {
				StalePlaybackProbeResolution.Current,
				is StalePlaybackProbeResolution.Unresolved ->
					continueAfterStaleSongProbe(player, state, song, activeRecovery, error)

				is StalePlaybackProbeResolution.Replacement -> {
					diagnostics.onStaleSongReplacement(
						oldSongId = recovery.songId,
						replacement = resolution.song,
						index = currentIndex,
						strength = resolution.strength
					)
					onQueueSongReplaced(currentIndex, resolution.song)
					player.replaceMediaItem(currentIndex, mediaItemForSong(resolution.song))
					player.seekTo(currentIndex, activeRecovery.positionMs)
					diagnostics.onPlaybackRetry(
						songId = resolution.song.id,
						title = resolution.song.title,
						index = currentIndex,
						positionMs = activeRecovery.positionMs,
						shouldResume = activeRecovery.shouldResume,
						source = "stale-id-${resolution.strength.name.lowercase()}"
					)
					player.prepare()
					if (activeRecovery.shouldResume) {
						claimMusicPlayback()
						player.play()
					}
					clear("stale-song-replaced")
				}

				StalePlaybackProbeResolution.Missing,
				StalePlaybackProbeResolution.Ambiguous ->
					finishConfirmedMissingSong(player, state, activeRecovery, resolution)

				is StalePlaybackProbeResolution.ServiceUnavailable -> {
					navidromeAvailabilityManager.reportUnavailable(
						NavidromeOutageTrigger.Playback,
						resolution.error
					)
					handleServiceUnavailable(
						player = player,
						state = state,
						reason = "stale-song-probe-service-unavailable"
					)
				}
			}
		}
		return true
	}

	private fun continueAfterStaleSongProbe(
		player: MediaController,
		state: PlayerUiState,
		song: DomainSong,
		recovery: PendingPlaybackRecovery,
		error: PlaybackException
	) {
		pending = null
		pendingError = null
		staleSongProbeJob = null
		clearRecoveryUi()
		if (refreshCurrentRemoteMediaItem(player, state)) return
		beginDownloadRecovery(
			player = player,
			state = state,
			song = song,
			currentIndex = recovery.queueIndex,
			reason = error.errorCodeName,
			error = error
		)
	}

	private fun finishConfirmedMissingSong(
		player: MediaController,
		state: PlayerUiState,
		recovery: PendingPlaybackRecovery,
		resolution: StalePlaybackProbeResolution
	) {
		val targetIndex = playbackFailureTargetIndex(
			skipMediaOnError = skipMediaOnError() && recovery.shouldResume,
			nextPlayableIndex = nextPlayableIndex(state, recovery.queueIndex, recovery.songId)
		)
		diagnostics.onPlaybackRecoveryDecision(
			event = if (targetIndex == null) "stale-song-terminal-held" else "stale-song-terminal-advanced",
			song = state.queue.getOrNull(recovery.queueIndex),
			currentIndex = recovery.queueIndex,
			targetIndex = targetIndex,
			reason = resolution::class.simpleName ?: "stale-song-missing",
			deferredCount = 0,
			fallbackAvailable = targetIndex != null
		)
		notifySongNotFound()
		if (targetIndex == null) {
			player.pause()
			clear("stale-song-terminal-hold")
			return
		}
		player.seekTo(targetIndex, 0L)
		player.prepare()
		claimMusicPlayback()
		player.play()
		clear("stale-song-terminal-skip")
	}

	fun handleDownloadSnapshot(
		player: MediaController,
		state: PlayerUiState,
		downloadsById: Map<String, DownloadEntity>
	) {
		if (pendingServiceOutage) return
		var recovery = pending ?: return
		val download = downloadsById[recovery.songId]
		val expectedGeneration = recovery.downloadIntentGeneration
		if (
			expectedGeneration != null &&
			download != null &&
			download.intentGeneration < expectedGeneration
		) {
			return
		}
		if (
			expectedGeneration != null &&
			download?.intentGeneration == expectedGeneration &&
			download.status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)
		) {
			recovery = recovery.withDownloadLifecycle(PlaybackRecoveryDownloadLifecycle.Active)
			pending = recovery
		} else if (
			expectedGeneration != null &&
			download != null &&
			download.intentGeneration > expectedGeneration &&
			download.status == DownloadStatus.NOT_DOWNLOADED
		) {
			recovery = recovery.withDownloadLifecycle(PlaybackRecoveryDownloadLifecycle.Rejected)
			pending = recovery
		}
		val localPath = downloadManager.getDownloadedFilePath(recovery.songId)
		val resolution = playbackRecoveryResolution(
			pending = recovery,
			currentSongId = player.currentMediaItem?.mediaId,
			currentIndex = player.currentMediaItemIndex,
			downloadStatus = download?.status,
			hasUsableLocalFile = localPath != null,
			skipMediaOnError = skipMediaOnError() && recovery.shouldResume,
			nextPlayableIndex = nextPlayableIndex(state, recovery.queueIndex, recovery.songId)
		)

		when (resolution) {
			PlaybackRecoveryResolution.Wait -> Unit
			PlaybackRecoveryResolution.CancelStale -> clear("stale-download-result")
			PlaybackRecoveryResolution.ResumeCurrent -> {
				if (localPath != null) resumeCurrentFromLocalFile(player, recovery, localPath)
			}
			PlaybackRecoveryResolution.HoldFailure -> finishTerminalFailure(
				player = player,
				state = state,
				recovery = recovery,
				targetIndex = null
			)
			is PlaybackRecoveryResolution.Advance -> finishTerminalFailure(
				player = player,
				state = state,
				recovery = recovery,
				targetIndex = resolution.targetIndex
			)
		}
	}

	fun onUserPause() {
		pending = pending?.withPlaybackIntent(false)
	}

	fun onUserResume(player: MediaController, state: PlayerUiState): Boolean {
		val recovery = pending ?: return false
		if (
			player.currentMediaItem?.mediaId != recovery.songId ||
			player.currentMediaItemIndex != recovery.queueIndex
		) {
			clear("resume-stale-recovery")
			return false
		}

		pending = recovery.withPlaybackIntent(true)
		val localPath = downloadManager.getDownloadedFilePath(recovery.songId)
		if (localPath != null) {
			resumeCurrentFromLocalFile(player, pending ?: recovery, localPath, "resume-request")
		} else {
			if (pendingServiceOutage) requestServiceProbe()
			markRecoveryPending()
			diagnostics.onPlaybackRecoveryDecision(
				event = "recovery-resume-requested",
				song = state.queue.getOrNull(recovery.queueIndex),
				currentIndex = recovery.queueIndex,
				targetIndex = null,
				reason = recovery.reason,
				deferredCount = 1,
				fallbackAvailable = false
			)
		}
		return true
	}

	fun clear(reason: String = "cleared") {
		val songId = pending?.songId
		refreshedRemoteSourceKey = null
		staleSongProbeKey = null
		staleSongProbeJob?.cancel()
		staleSongProbeJob = null
		pending = null
		pendingError = null
		pendingServiceOutage = false
		if (songId != null) diagnostics.onRecoveryCleared(songId, reason)
		clearRecoveryUi()
	}

	private fun refreshCurrentRemoteMediaItem(player: MediaController, state: PlayerUiState): Boolean {
		val currentItem = player.currentMediaItem ?: return false
		if (currentItem.localConfiguration?.uri?.scheme == "file") return false
		val currentIndex = player.currentMediaItemIndex
		val key = RemoteSourceRefreshKey(currentItem.mediaId, currentIndex)
		if (refreshedRemoteSourceKey == key) return false
		val song = state.queue.getOrNull(currentIndex)
			?: state.currentSong?.takeIf { it.id == currentItem.mediaId }
			?: return false

		val positionMs = player.currentPosition.coerceAtLeast(0L)
		val shouldResume = player.playWhenReady
		refreshedRemoteSourceKey = key
		player.replaceMediaItem(currentIndex, mediaItemForSong(song))
		player.seekTo(currentIndex, positionMs)
		diagnostics.onPlaybackRetry(
			songId = song.id,
			title = song.title,
			index = currentIndex,
			positionMs = positionMs,
			shouldResume = shouldResume,
			source = "remote-refresh"
		)
		player.prepare()
		if (shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		return true
	}

	private fun beginDownloadRecovery(
		player: MediaController,
		state: PlayerUiState,
		song: DomainSong,
		currentIndex: Int,
		reason: String,
		error: PlaybackException?
	) {
		val recovery = pendingRecovery(player, state, song, currentIndex, reason)
		pending = recovery
		pendingError = error
		pendingServiceOutage = false
		markRecoveryPending()
		diagnostics.onRecoveryPending(song, recovery.positionMs, recovery.shouldResume)
		diagnostics.onDeferredDownloadRequested(song, currentIndex, reason, 1)
		scope.launch {
			val result = downloadManager.requestPlaybackRecoveryDownload(song)
			diagnostics.onPlaybackDownloadRequestResult(song.id, currentIndex, result)
			val activeRecovery = pending?.takeIf { pendingRecovery ->
				pendingRecovery.songId == recovery.songId &&
					pendingRecovery.queueIndex == recovery.queueIndex
			} ?: return@launch
			if (
				player.currentMediaItem?.mediaId != activeRecovery.songId ||
				player.currentMediaItemIndex != activeRecovery.queueIndex
			) {
				clear("playback-download-request-obsolete")
				return@launch
			}

			when (result) {
				is PlaybackDownloadRequestResult.Enqueued,
				is PlaybackDownloadRequestResult.AlreadyActive -> {
					pending = activeRecovery.withActiveDownloadRequest(
						requireNotNull(result.intentGeneration)
					)
				}

				is PlaybackDownloadRequestResult.AlreadyDownloaded -> {
					val accepted = activeRecovery.withActiveDownloadRequest(result.intentGeneration)
					pending = accepted
					val localPath = downloadManager.getDownloadedFilePath(song.id)
					if (localPath != null) {
						resumeCurrentFromLocalFile(player, accepted, localPath, "download-request-ready")
					} else {
						pending = accepted.withDownloadLifecycle(PlaybackRecoveryDownloadLifecycle.Rejected)
						finishTerminalFailure(
							player = player,
							state = state,
							recovery = pending ?: accepted,
							targetIndex = nextTerminalTarget(state, accepted)
						)
					}
				}

				PlaybackDownloadRequestResult.MissingCatalogEntry,
				PlaybackDownloadRequestResult.InactiveSession -> {
					val rejected = activeRecovery.withDownloadLifecycle(
						PlaybackRecoveryDownloadLifecycle.Rejected
					)
					pending = rejected
					finishTerminalFailure(
						player = player,
						state = state,
						recovery = rejected,
						targetIndex = nextTerminalTarget(state, rejected)
					)
				}
			}
		}
	}

	private fun resumeCurrentFromLocalFile(
		player: MediaController,
		recovery: PendingPlaybackRecovery,
		localPath: String,
		source: String = "download-flow"
	) {
		val currentItem = player.currentMediaItem ?: run {
			clear("missing-current-media-item")
			return
		}
		if (
			currentItem.mediaId != recovery.songId ||
			player.currentMediaItemIndex != recovery.queueIndex
		) {
			clear("local-file-stale")
			return
		}

		player.replaceMediaItem(
			recovery.queueIndex,
			currentItem.buildUpon().setUri(File(localPath).toUri()).build()
		)
		player.seekTo(recovery.queueIndex, recovery.positionMs)
		diagnostics.onRecoveryLocalFileReady(
			recovery.songId,
			recovery.positionMs,
			recovery.shouldResume,
			source
		)
		player.prepare()
		if (recovery.shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		clear(source)
	}

	private fun playUpcomingFromLocalFile(
		player: MediaController,
		targetIndex: Int,
		localPath: String,
		shouldResume: Boolean
	) {
		val targetItem = player.getMediaItemAt(targetIndex)
		player.replaceMediaItem(
			targetIndex,
			targetItem.buildUpon().setUri(File(localPath).toUri()).build()
		)
		player.seekTo(targetIndex, 0L)
		player.prepare()
		if (shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		clear("offline-cached-fallback")
	}

	private fun requestServiceProbe() {
		navidromeAvailabilityManager.requestProbe()
	}

	private fun pendingRecovery(
		player: MediaController,
		state: PlayerUiState,
		song: DomainSong,
		currentIndex: Int,
		reason: String
	): PendingPlaybackRecovery {
		val songDurationMs = song.duration.inWholeMilliseconds
		val fallbackPositionMs = if (songDurationMs > 0L) {
			(state.progress * songDurationMs).toLong()
		} else {
			0L
		}
		return PendingPlaybackRecovery(
			songId = song.id,
			queueIndex = currentIndex,
			positionMs = player.currentPosition.takeIf { it > 0L }
				?: fallbackPositionMs.coerceAtLeast(0L),
			shouldResume = player.playWhenReady || !state.isPaused,
			reason = reason
		)
	}

	private fun finishTerminalFailure(
		player: MediaController,
		state: PlayerUiState,
		recovery: PendingPlaybackRecovery,
		targetIndex: Int?
	) {
		val song = state.queue.getOrNull(recovery.queueIndex)
		diagnostics.onPlaybackRecoveryDecision(
			event = if (targetIndex == null) "recovery-terminal-held" else "recovery-terminal-advanced",
			song = song,
			currentIndex = recovery.queueIndex,
			targetIndex = targetIndex,
			reason = recovery.reason,
			deferredCount = 1,
			fallbackAvailable = targetIndex != null
		)
		pendingError?.let(notifyPlaybackError) ?: notifyFailedDownload()
		if (targetIndex == null) {
			player.pause()
			clear("terminal-download-failure")
			return
		}

		player.seekTo(targetIndex, 0L)
		player.prepare()
		if (recovery.shouldResume) {
			claimMusicPlayback()
			player.play()
		}
		clear("terminal-download-skip")
	}

	private fun nextPlayableIndex(state: PlayerUiState, currentIndex: Int, currentSongId: String?): Int? {
		val availableSongIds = state.queue
			.asSequence()
			.map { it.id }
			.filter { songId -> songId != currentSongId && isAvailable(songId) }
			.toSet()
		return firstPlayableUpcomingIndex(
			currentIndex = currentIndex,
			queueSongIds = state.queue.map { it.id },
			availableSongIds = availableSongIds,
			upcomingIndexes = state.upcomingIndexes
		)
	}

	private fun nextTerminalTarget(
		state: PlayerUiState,
		recovery: PendingPlaybackRecovery
	): Int? = playbackFailureTargetIndex(
		skipMediaOnError = skipMediaOnError() && recovery.shouldResume,
		nextPlayableIndex = nextPlayableIndex(state, recovery.queueIndex, recovery.songId)
	)
}

private data class RemoteSourceRefreshKey(
	val songId: String,
	val queueIndex: Int
)
