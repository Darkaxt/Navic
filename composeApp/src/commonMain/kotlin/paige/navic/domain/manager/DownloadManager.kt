package paige.navic.domain.manager

import coil3.SingletonImageLoader
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.LyricDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LyricEntity
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.remote.NetworkClientFactory
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.cancelPendingDownloadSongIds
import paige.navic.domain.models.clearDownloadQueueSongIds
import paige.navic.domain.models.collectionDownloadStatus
import paige.navic.domain.models.collectionSongIdsToQueue
import paige.navic.domain.models.downloadSchedulerWorkerCount
import paige.navic.domain.models.downloadedAudioPath
import paige.navic.domain.models.failedDownloadRetryPlan
import paige.navic.domain.models.HostedDownloadFailureAction
import paige.navic.domain.models.PlaybackDownloadRequestResult
import paige.navic.domain.models.hostedDownloadFailureAction
import paige.navic.domain.models.shouldSaveLidaClipWithDownloadedMusic
import paige.navic.domain.models.shouldRejectAudioDownloadContentType
import paige.navic.domain.models.shouldUseDownloadedAudioFile
import paige.navic.domain.models.shouldTreatLidaClipAsMusicVideo
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.LyricsRepository
import paige.navic.util.core.Logger
import paige.navic.util.core.toNetworkHeaders
import kotlin.time.Clock
import kotlin.uuid.Uuid
import coil3.PlatformContext as CoilPlatformContext

class DownloadManager(
	private val coilPlatformContext: CoilPlatformContext,
	private val downloadDao: AccountDownloadRegistry,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val storageManager: StorageManager,
	private val lyricsRepository: LyricsRepository,
	private val lyricDao: LyricDao,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val lidaClipsRepository: LidaClipsRepository,
	private val lidaClipDownloadManager: LidaClipDownloadManager,
	private val connectivityManager: ConnectivityManager,
	private val navidromeAvailabilityManager: NavidromeAvailabilityManager,
	private val sessionLifetime: AuthenticatedSessionLifetime,
	networkClientFactory: NetworkClientFactory = NetworkClientFactory()
) {
	private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val inactiveSessionScope = CoroutineScope(Dispatchers.IO + Job().apply { cancel() })
	private val scope: CoroutineScope
		get() = sessionLifetime.currentScope() ?: inactiveSessionScope
	private val client = networkClientFactory.create()
	private val downloadWork = DownloadWorkCoordinator()
	private val downloadWakeups = Channel<Unit>(capacity = 1)

	val allDownloads = downloadDao.getAllDownloads().map { it.toImmutableList() }
	val downloadCount = downloadDao.getDownloadsCount()
	val downloadSize = allDownloads.map { downloads ->
		downloads
			.filter { it.status == DownloadStatus.DOWNLOADED && it.filePath != null }
			.sumOf { storageManager.getFileSize(it.filePath!!) }
	}
	val pendingDownloadCount = allDownloads.map { downloads ->
		paige.navic.domain.models.pendingDownloadCount(downloads)
	}

	private val downloadedRows = MutableStateFlow<Map<String, DownloadEntity>>(emptyMap())
	val downloadedSongs: StateFlow<Map<String, String>> = combine(downloadDao.ownerId, downloadedRows) { owner, rows ->
		// This is a UI projection. Validate files when resolving audio, not on every registry update.
		rows.mapNotNull { (id, row) -> row.filePath?.takeIf { owner != null && row.ownerId == owner }?.let { id to it } }
			.toMap()
	}.stateIn(applicationScope, SharingStarted.Eagerly, emptyMap())

	private var libraryDownloadJob: Job? = null
	private val _isDownloadingLibrary = MutableStateFlow(false)
	val isDownloadingLibrary: StateFlow<Boolean> = _isDownloadingLibrary.asStateFlow()
	private val _libraryDownloadProgress = MutableStateFlow(0f)
	val libraryDownloadProgress: StateFlow<Float> = _libraryDownloadProgress.asStateFlow()

	init {
		applicationScope.launch {
			allDownloads.collectLatest { downloads ->
				downloadedRows.value = downloads.filter { it.status == DownloadStatus.DOWNLOADED }.associateBy { it.songId }
			}
		}
		sessionLifetime.repeatInSession {
			try {
				runConnectedDownloadWorkers(
					online = connectivityManager.isOnline,
					recoverInterrupted = ::recoverQueuedDownloads
				) {
					repeat(downloadSchedulerWorkerCount()) {
						launch(Dispatchers.IO) { processSongDownloadQueueWorker() }
					}
					downloadWakeups.trySend(Unit)
					awaitCancellation()
				}
			} finally {
				withContext(NonCancellable) { cleanupSessionWork() }
			}
		}
	}

	fun getDownloadedFilePath(songId: String): String? = getDownloadedFilePath(downloadedRows.value[songId])

	fun getDownloadedFilePath(download: DownloadEntity?): String? =
		downloadedAudioPath(download, downloadDao.ownerId.value, ::isUsableDownloadedAudioFile)

	private fun isUsableDownloadedAudioFile(path: String): Boolean =
		storageManager.fileExists(path) &&
			shouldUseDownloadedAudioFile(storageManager.getFileSize(path))

	fun downloadSong(song: DomainSong): Job {
		return scope.launch(Dispatchers.IO) {
			queueSongDownloads(listOf(song))
		}
	}

	fun prefetchPlaybackSongs(songs: List<DomainSong>): Job {
		return scope.launch(Dispatchers.IO) {
			queueSongDownloads(songs)
		}
	}

	suspend fun requestPlaybackRecoveryDownload(
		song: DomainSong
	): PlaybackDownloadRequestResult {
		if (sessionLifetime.currentScope() == null) {
			return PlaybackDownloadRequestResult.InactiveSession
		}
		return sessionLifetime.runInSession {
			downloadWork.serialized { requestPlaybackRecoveryDownloadLocked(song) }
		}
	}

	private suspend fun requestPlaybackRecoveryDownloadLocked(song: DomainSong): PlaybackDownloadRequestResult {
		if (songDao.getSongById(song.id) == null) {
			return PlaybackDownloadRequestResult.MissingCatalogEntry
		}

		val existing = downloadDao.getDownloadById(song.id)
		if (
			existing?.status == DownloadStatus.DOWNLOADED &&
			existing.filePath?.let(::isUsableDownloadedAudioFile) == true
		) {
			return PlaybackDownloadRequestResult.AlreadyDownloaded(existing.intentGeneration)
		}
		if (
			existing != null &&
			!existing.cancelled &&
			existing.status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)
		) {
			return PlaybackDownloadRequestResult.AlreadyActive(existing.intentGeneration)
		}

		val generation = downloadDao.enqueueFreshIntent(
			songId = song.id,
			queuedAtEpochMs = Clock.System.now().toEpochMilliseconds()
		)
		downloadWakeups.trySend(Unit)
		return PlaybackDownloadRequestResult.Enqueued(generation)
	}

	suspend fun downloadCollection(collection: DomainSongCollection) {
		if (collection.songs.isEmpty()) return
		queueSongDownloads(collection.songs)
	}

	fun downloadEntireLibrary(songs: List<DomainSong>) {
		if (_isDownloadingLibrary.value) return

		libraryDownloadJob = scope.launch(Dispatchers.IO) {
			try {
				_isDownloadingLibrary.value = true
				_libraryDownloadProgress.value = 0f

				val songsToDownload = songs.filter { !isDownloaded(it.id) }
				val totalToDownload = songsToDownload.size

				if (totalToDownload == 0) {
					_isDownloadingLibrary.value = false
					_libraryDownloadProgress.value = 1f
					return@launch
				}

				queueSongDownloads(songsToDownload)
				trackLibraryDownloadProgress(songsToDownload.map { it.id })
			} catch (_: CancellationException) {
				_isDownloadingLibrary.value = false
				_libraryDownloadProgress.value = 0f
			} finally {
				_isDownloadingLibrary.value = false
			}
		}
	}

	fun cancelAllActiveDownloads() {
		resetLibraryDownloadState()
		scope.launch(Dispatchers.IO) {
			clearDownloadRows(::cancelPendingDownloadSongIds)
		}
	}

	fun clearDownloadQueue() {
		resetLibraryDownloadState()
		scope.launch(Dispatchers.IO) {
			clearDownloadRows(::clearDownloadQueueSongIds)
		}
	}

	fun cancelDownload(songId: String) {
		scope.launch(Dispatchers.IO) {
			cancelDownloadWork(listOf(songId))
			downloadWakeups.trySend(Unit)
		}
	}

	fun retryFailedDownloads() {
		scope.launch(Dispatchers.IO) {
			val downloads = downloadDao.getAllDownloadsList()
			val failedSongIds = downloads
				.filter { it.status == DownloadStatus.FAILED }
				.map { it.songId }
			if (failedSongIds.isEmpty()) return@launch

			val songsById = songDao.getSongsByIds(failedSongIds)
				.associateBy { it.songId }
			val retryPlan = failedDownloadRetryPlan(
				downloads = downloads,
				localSongIds = songsById.keys
			)
			retryPlan.staleSongIdsToDelete.forEach { songId ->
				val failed = downloads.firstOrNull { it.songId == songId } ?: return@forEach
				downloadDao.deleteFailedDownloadIfCurrent(songId, failed.intentGeneration)
			}
			var queuedAny = false
			retryPlan.songIdsToRetry.forEach { songId ->
				val failed = downloads.firstOrNull { it.songId == songId } ?: return@forEach
				queuedAny = downloadDao.retryFailedIntent(
					songId = songId,
					generation = failed.intentGeneration,
					queuedAtEpochMs = Clock.System.now().toEpochMilliseconds()
				) == 1 || queuedAny
			}
			if (queuedAny) downloadWakeups.trySend(Unit)
		}
	}

	fun retryFailedDownload(songId: String) {
		scope.launch(Dispatchers.IO) {
			val download = downloadDao.getDownloadById(songId)
			if (download?.status != DownloadStatus.FAILED) return@launch

			val song = songDao.getSongsByIds(listOf(songId))
				.firstOrNull()
				?.toDomainModel()
			if (song == null) {
				downloadDao.deleteFailedDownloadIfCurrent(songId, download.intentGeneration)
				return@launch
			}
			if (
				downloadDao.retryFailedIntent(
					songId = song.id,
					generation = download.intentGeneration,
					queuedAtEpochMs = Clock.System.now().toEpochMilliseconds()
				) == 1
			) {
				downloadWakeups.trySend(Unit)
			}
		}
	}

	fun discardFailedDownloads() {
		scope.launch(Dispatchers.IO) {
			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.FAILED }
				.forEach { downloadDao.deleteFailedDownloadIfCurrent(it.songId, it.intentGeneration) }
		}
	}

	fun cancelCollectionDownload(collection: DomainSongCollection) {
		scope.launch(Dispatchers.IO) {
			collection.songs.forEach { song ->
				cancelDownload(song.id)
			}
		}
	}

	fun deleteDownload(songId: String) {
		scope.launch {
			val download = cancelDownloadWork(listOf(songId)).firstOrNull()
			if (download != null) deleteCapturedDownload(download)
			downloadWakeups.trySend(Unit)
		}
	}

	fun deleteDownloadedCollection(collection: DomainSongCollection) {
		collection.songs.forEach { song ->
			deleteDownload(song.id)
		}
	}

	suspend fun isDownloaded(songId: String): Boolean {
		return getDownloadedFilePath(downloadDao.getDownloadById(songId)) != null
	}

	fun getCollectionDownloadStatus(songIds: List<String>): Flow<DownloadStatus> {
		return allDownloads.map { downloads ->
			collectionDownloadStatus(songIds, downloads)
		}
	}

	fun clearAllDownloads() {
		scope.launch(Dispatchers.IO) {
			resetLibraryDownloadState()
			val rows = cancelDownloadWork(downloadDao.getAllDownloadsList().map { it.songId })
			rows.forEach { deleteCapturedDownload(it) }
			downloadWakeups.trySend(Unit)
			Logger.i("DownloadManager", "cleared all downloads")
		}
	}

	private suspend fun recoverQueuedDownloads() {
		downloadDao.recoverInterruptedDownloads()
		downloadWakeups.trySend(Unit)
	}

	private suspend fun cleanupSessionWork() {
		resetLibraryDownloadState()
	}

	private suspend fun deleteCapturedDownload(download: DownloadEntity) = deletePublishedAudio(
		deleteRow = {
			downloadDao.deleteDownloadIfCurrent(
				download.songId, download.intentGeneration, download.status, download.filePath
			) == 1
		},
		deleteFiles = {
			download.filePath?.let { storageManager.deleteFile(it) }
			storageManager.clearLidaClipOfflineFilesForSong(download.songId)
		}
	)

	private fun resetLibraryDownloadState() {
		libraryDownloadJob?.cancel()
		libraryDownloadJob = null
		_isDownloadingLibrary.value = false
		_libraryDownloadProgress.value = 0f
	}

	private suspend fun clearDownloadRows(songIdsToDelete: (List<DownloadEntity>) -> List<String>) {
		cancelDownloadWork(songIdsToDelete(downloadDao.getAllDownloadsList()))
		downloadWakeups.trySend(Unit)
	}

	private suspend fun cancelDownloadWork(songIds: Collection<String>): List<DownloadEntity> {
		return downloadWork.cancel(songIds) { ids ->
			ids.mapNotNull { id ->
				downloadDao.cancelPendingIntent(id)
				downloadDao.getDownloadById(id)
			}
		}
	}

	private suspend fun processSongDownloadQueueWorker() {
		for (ignored in downloadWakeups) {
			while (true) {
				connectivityManager.isOnline.first { it }
				val processed = downloadWork.runNext(claim = ::claimNextDownloadSlot) { intent ->
					downloadWakeups.trySend(Unit)
					val song = songDao.getSongsByIds(listOf(intent.songId))
						.firstOrNull()
						?.toDomainModel()
					if (song == null) {
						downloadDao.completeIfCurrent(intent.songId, intent.intentGeneration, DownloadStatus.FAILED, 0f, null)
					} else if (isCurrentDownloadIntent(song.id, intent.intentGeneration)) {
						executeDownloadProcess(song, intent.intentGeneration)
					}
				}
				if (!processed) break
				downloadWakeups.trySend(Unit)
			}
		}
	}

	private suspend fun queueSongDownloads(songs: List<DomainSong>) = sessionLifetime.runInSession {
		downloadWork.serialized { enqueueSongDownloads(songs) }
	}

	private suspend fun enqueueSongDownloads(songs: List<DomainSong>) {
		val distinctSongs = songs.distinctBy { it.id }
		if (distinctSongs.isEmpty()) return

		val songIdsToQueue = collectionSongIdsToQueue(
			songIds = distinctSongs.map { it.id },
			downloads = downloadDao.getAllDownloadsList()
		).toSet()
		if (songIdsToQueue.isEmpty()) return

		val queuedAt = Clock.System.now().toEpochMilliseconds()
		distinctSongs.filter { it.id in songIdsToQueue }.forEachIndexed { index, song ->
			downloadDao.enqueueFreshIntent(song.id, queuedAt + index)
		}
		downloadWakeups.trySend(Unit)
	}

	private suspend fun trackLibraryDownloadProgress(songIds: List<String>) {
		val ids = songIds.toSet()
		if (ids.isEmpty()) return

		while (true) {
			val downloadsById = downloadDao.getAllDownloadsList().associateBy { it.songId }
			val finishedCount = ids.count { songId ->
				when (downloadsById[songId]?.status) {
					DownloadStatus.DOWNLOADED,
					DownloadStatus.FAILED,
					null -> true

					DownloadStatus.DOWNLOADING,
					DownloadStatus.QUEUED,
					DownloadStatus.NOT_DOWNLOADED -> false
				}
			}
			_libraryDownloadProgress.value = finishedCount.toFloat() / ids.size.toFloat()
			if (finishedCount >= ids.size) return
			delay(LIBRARY_PROGRESS_POLL_DELAY_MS)
		}
	}

	private suspend fun claimNextDownloadSlot(runningSongIds: Set<String>): DownloadEntity? {
		if (!connectivityManager.isOnline.value) return null
		if (runningSongIds.size >= paige.navic.domain.models.downloadConcurrencyLimit(
				preferenceManager.maxConcurrentDownloads
			)
		) {
			return null
		}
		return downloadDao.claimNextQueuedDownload(runningSongIds)
	}

	private suspend fun isCurrentDownloadIntent(songId: String, generation: Long): Boolean {
		val current = downloadDao.getDownloadById(songId) ?: return false
		return paige.navic.domain.models.canApplyDownloadResult(current, generation)
	}

	private suspend fun executeDownloadProcess(song: DomainSong, generation: Long) {
		try {
			Logger.i("DownloadManager", "beginning download for ${song.id}")
			if (!isCurrentDownloadIntent(song.id, generation)) return

			val extension = song.fileExtension.takeIf { value ->
				value.isNotEmpty() && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
			} ?: "audio"
			val path = storageManager.getDownloadPath("audio-${Uuid.random()}", extension)
			val published = publishAudioDownload(
				finalPath = path,
				write = { temporaryPath -> downloadAudioFile(song, generation, temporaryPath) },
				isUsable = ::isUsableDownloadedAudioFile,
				move = storageManager::moveFile,
				complete = { finalPath ->
					downloadDao.completeIfCurrent(song.id, generation, DownloadStatus.DOWNLOADED, 1f, finalPath) == 1
				},
				delete = { storageManager.deleteFile(it) }
			)
			if (!published) return
			cacheSongCoverArt(song.coverArtId)
			cacheAlbumCoverArt(song.albumId)
			cacheLyrics(song)
			cacheOfflineLidaClip(song)
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			when (hostedDownloadFailureAction(e)) {
				HostedDownloadFailureAction.WaitForService -> withContext(NonCancellable) {
					Logger.w("DownloadManager", "Navidrome unavailable while downloading ${song.id}; preserving queued intent", e)
					navidromeAvailabilityManager.reportUnavailable(NavidromeOutageTrigger.Download, e)
					downloadDao.requeueIfCurrent(song.id, generation)
				}

				HostedDownloadFailureAction.Fail -> {
					Logger.e("DownloadManager", "Terminal download failure for ${song.id}", e)
					downloadDao.completeIfCurrent(
						song.id,
						generation,
						DownloadStatus.FAILED,
						0f,
						null
					)
				}
			}
		}
	}

	private suspend fun cacheSongCoverArt(coverId: String?) {
		try {
			cacheCoverArt(coverId)
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e("DownloadManager", "Failed to cache cover art for $coverId; continuing audio download", e)
		}
	}

	private suspend fun cacheCoverArt(coverId: String?) {
		if (coverId == null) return

		Logger.i("DownloadManager", "caching cover art for $coverId")
		val coverArtUrl = sessionManager.getCoverArtUrl(coverId)

		val imageRequest = ImageRequest.Builder(coilPlatformContext)
			.data(coverArtUrl)
			.memoryCacheKey(coverId)
			.diskCacheKey(coverId)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.DISABLED)
			.httpHeaders(preferenceManager.serverRequestHeadersMap().toNetworkHeaders())
			.build()

		SingletonImageLoader.get(coilPlatformContext).execute(imageRequest)
		Logger.i("DownloadManager", "cached cover art for $coverId")
	}

	private suspend fun cacheAlbumCoverArt(albumId: String?) {
		if (albumId == null) return

		try {
			val albumWithSongs = albumDao.getAlbumById(albumId)
			val albumCoverId = albumWithSongs?.album?.coverArtId

			if (albumCoverId != null) {
				Logger.i("DownloadManager", "Found album cover $albumCoverId for album $albumId")
				cacheCoverArt(albumCoverId)
			}
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e("DownloadManager", "Failed to cache album cover art for album $albumId", e)
		}
	}

	private suspend fun cacheLyrics(song: DomainSong) {
		Logger.i("DownloadManager", "caching lyrics for ${song.id}")
		try {
			val lyricsResult = lyricsRepository.fetchLyrics(song)
			if (lyricsResult != null && lyricsResult.rawContent != null) {
				lyricDao.insertLyrics(
					LyricEntity(
						song.id,
						lyricsResult.rawContent,
						lyricsResult.provider
					)
				)
				Logger.i("DownloadManager", "cached lyrics for ${song.id}")
			}
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e("DownloadManager", "Failed to cache lyrics for ${song.id}", e)
		}
	}

	private fun cacheOfflineLidaClip(song: DomainSong) {
		if (!shouldSaveLidaClipWithDownloadedMusic(
			lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
			lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl,
			saveClipsWithDownloads = preferenceManager.lidaClipsSaveClipsWithDownloads,
			songId = song.id
		)) {
			return
		}

		scope.launch(Dispatchers.IO) {
			try {
				val clip = lidaClipsRepository.findClipForSong(song, forceRefresh = true)
					.getOrNull()
					?.takeIf { shouldTreatLidaClipAsMusicVideo(it) }
					?: return@launch
				lidaClipDownloadManager.getOrQueueClipForPlayback(
					songId = song.id,
					clip = clip,
					persistOffline = true
				).onSuccess { cachedClip ->
					if (cachedClip != null) {
						Logger.i("DownloadManager", "cached LidaClips offline clip for ${song.id}")
					}
				}.onFailure { error ->
					Logger.w("DownloadManager", "Failed to cache LidaClips offline clip for ${song.id}", error)
				}
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.w("DownloadManager", "Failed to resolve LidaClips offline clip for ${song.id}", e)
			}
		}
	}

	private suspend fun downloadAudioFile(song: DomainSong, generation: Long, path: String) = coroutineScope {
		var lastProgress = 0f
		var progressJob: Job? = null

		val request = client.prepareRequest(sessionManager.getStreamUrl(song.id)) {
			method = HttpMethod.Get
			preferenceManager.serverRequestHeadersMap().forEach { (key, value) -> header(key, value) }
			onDownload { bytesSentTotal, contentLength ->
				if (contentLength != null && contentLength > 0L) {
					val progress = (bytesSentTotal.toDouble() / contentLength).toFloat()
					if (progress - lastProgress >= 0.01f || progress == 1f) {
						lastProgress = progress
						Logger.i("DownloadManager", "downloading ${song.id} $progress")

						progressJob?.cancel()

						progressJob = launch {
							downloadDao.updateProgressIfCurrent(
								song.id,
								generation,
								DownloadStatus.DOWNLOADING,
								progress
							)
						}
					}
				} else {
					Logger.i("DownloadManager", "downloaded ${song.id}")
				}
			}
		}

		try {
			request.execute { response ->
				if (response.status.value !in 200..299) {
					throw IllegalStateException(
						"Stream request failed for ${song.id}: HTTP ${response.status.value} ${response.status.description}"
					)
				}
				val contentType = response.headers[HttpHeaders.ContentType]
				if (shouldRejectAudioDownloadContentType(contentType)) {
					throw IllegalStateException(
						"Stream request returned non-audio content for ${song.id}: $contentType"
					)
				}
				Logger.i("DownloadManager", "writing download for ${song.id}")
				storageManager.saveFile(path, response.bodyAsChannel())
				Logger.i("DownloadManager", "wrote download for ${song.id}")
			}
		} finally {
			progressJob?.cancel()
		}
	}

	private companion object {
		const val LIBRARY_PROGRESS_POLL_DELAY_MS = 500L
	}
}
