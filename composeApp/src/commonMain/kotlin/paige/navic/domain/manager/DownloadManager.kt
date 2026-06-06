package paige.navic.domain.manager

import coil3.SingletonImageLoader
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.dao.LyricDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LyricEntity
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.canStartQueuedDownload
import paige.navic.domain.models.cancelPendingDownloadSongIds
import paige.navic.domain.models.clearDownloadQueueSongIds
import paige.navic.domain.models.collectionDownloadStatus
import paige.navic.domain.models.collectionSongIdsToQueue
import paige.navic.domain.models.downloadSchedulerWorkerCount
import paige.navic.domain.models.failedDownloadRetryPlan
import paige.navic.domain.models.queuedDownloadRecovery
import paige.navic.domain.models.shouldSaveLidaClipWithDownloadedMusic
import paige.navic.domain.models.shouldFailHostedDownload
import paige.navic.domain.models.shouldTreatLidaClipAsMusicVideo
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.LyricsRepository
import paige.navic.util.core.Logger
import paige.navic.util.core.toNetworkHeaders
import coil3.PlatformContext as CoilPlatformContext

class DownloadManager(
	private val coilPlatformContext: CoilPlatformContext,
	private val downloadDao: DownloadDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val storageManager: StorageManager,
	private val lyricsRepository: LyricsRepository,
	private val lyricDao: LyricDao,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val lidaClipsRepository: LidaClipsRepository,
	private val lidaClipDownloadManager: LidaClipDownloadManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val client = HttpClient()
	private val activeDownloadsMutex = Mutex()
	private val activeDownloads = mutableMapOf<String, Job>()
	private val runningDownloadSlotsMutex = Mutex()
	private val runningDownloadSlots = mutableSetOf<String>()
	private val songDownloadQueue = Channel<DomainSong>(Channel.UNLIMITED)
	private val queuedSongIdsMutex = Mutex()
	private val queuedSongIds = mutableSetOf<String>()
	private val startupQueueRecovery = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
		recoverQueuedDownloads()
	}

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

	private val _downloadedSongs = MutableStateFlow<Map<String, String>>(emptyMap())
	val downloadedSongs: StateFlow<Map<String, String>> = _downloadedSongs.asStateFlow()

	private var libraryDownloadJob: Job? = null
	private val _isDownloadingLibrary = MutableStateFlow(false)
	val isDownloadingLibrary: StateFlow<Boolean> = _isDownloadingLibrary.asStateFlow()
	private val _libraryDownloadProgress = MutableStateFlow(0f)
	val libraryDownloadProgress: StateFlow<Float> = _libraryDownloadProgress.asStateFlow()

	init {
		startupQueueRecovery.start()
		scope.launch {
			allDownloads.collectLatest { downloads ->
				_downloadedSongs.value = downloads
					.filter { it.status == DownloadStatus.DOWNLOADED && it.filePath != null }
					.associate { it.songId to it.filePath!! }
			}
		}
		repeat(downloadSchedulerWorkerCount()) {
			scope.launch(Dispatchers.IO) {
				processSongDownloadQueueWorker()
			}
		}
	}

	fun getDownloadedFilePath(songId: String): String? {
		return _downloadedSongs.value[songId]
	}

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
			queuedSongIdsMutex.withLock {
				queuedSongIds.remove(songId)
			}

			activeDownloadsMutex.withLock {
				activeDownloads[songId]?.cancel()
				activeDownloads.remove(songId)
			}

			val existing = downloadDao.getDownloadById(songId)
			if (existing?.status == DownloadStatus.DOWNLOADING
				|| existing?.status == DownloadStatus.FAILED
				|| existing?.status == DownloadStatus.QUEUED
			) {
				downloadDao.deleteDownload(songId)
			}
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
				downloadDao.deleteDownload(songId)
			}
			val songsToRetry = retryPlan.songIdsToRetry
				.mapNotNull { songId -> songsById[songId]?.toDomainModel() }
			queueSongDownloads(songsToRetry)
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
				downloadDao.deleteDownload(songId)
				return@launch
			}
			queueSongDownloads(listOf(song))
		}
	}

	fun discardFailedDownloads() {
		scope.launch(Dispatchers.IO) {
			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.FAILED }
				.forEach { downloadDao.deleteDownload(it.songId) }
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
		cancelDownload(songId)
		scope.launch {
			val download = downloadDao.getDownloadById(songId)
			download?.filePath?.let { storageManager.deleteFile(it) }
			storageManager.clearLidaClipOfflineFilesForSong(songId)
			downloadDao.deleteDownload(songId)
		}
	}

	fun deleteDownloadedCollection(collection: DomainSongCollection) {
		collection.songs.forEach { song ->
			deleteDownload(song.id)
		}
	}

	suspend fun isDownloaded(songId: String): Boolean {
		return downloadDao.getDownloadById(songId)?.status == DownloadStatus.DOWNLOADED
	}

	fun getCollectionDownloadStatus(songIds: List<String>): Flow<DownloadStatus> {
		return allDownloads.map { downloads ->
			collectionDownloadStatus(songIds, downloads)
		}
	}

	fun clearAllDownloads() {
		scope.launch(Dispatchers.IO) {
			cancelAllActiveDownloads()
			storageManager.clearDownloads()
			storageManager.clearLidaClipOfflineFiles()
			downloadDao.clearAllDownloads()
			Logger.i("DownloadManager", "cleared all downloads")
		}
	}

	private suspend fun recoverQueuedDownloads() {
		val downloads = downloadDao.getAllDownloadsList()
		val queuedSongIds = downloads
			.filter { it.status == DownloadStatus.QUEUED }
			.map { it.songId }
		if (queuedSongIds.isEmpty()) return

		val songsById = songDao.getSongsByIds(queuedSongIds)
			.associateBy { it.songId }
		val recovery = queuedDownloadRecovery(
			downloads = downloads,
			localSongIds = songsById.keys
		)

		recovery.songIdsToDelete.forEach { songId ->
			downloadDao.deleteDownload(songId)
		}

		val songsToResume = recovery.songIdsToResume.mapNotNull { songId ->
			songsById[songId]?.toDomainModel()
		}
		if (songsToResume.isEmpty()) return
		sendSongsToQueue(songsToResume)
	}

	private fun resetLibraryDownloadState() {
		libraryDownloadJob?.cancel()
		libraryDownloadJob = null
		_isDownloadingLibrary.value = false
		_libraryDownloadProgress.value = 0f
	}

	private suspend fun clearDownloadRows(songIdsToDelete: (List<DownloadEntity>) -> List<String>) {
		queuedSongIdsMutex.withLock {
			queuedSongIds.clear()
		}

		val jobsToCancel = activeDownloadsMutex.withLock {
			val copy = activeDownloads.toMap()
			activeDownloads.clear()
			copy
		}
		jobsToCancel.values.forEach { job -> job.cancel() }

		songIdsToDelete(downloadDao.getAllDownloadsList())
			.distinct()
			.forEach { songId -> downloadDao.deleteDownload(songId) }
	}

	private suspend fun processSongDownloadQueueWorker() {
		startupQueueRecovery.join()
		for (song in songDownloadQueue) {
			if (!shouldStartQueuedSong(song.id)) {
				queuedSongIdsMutex.withLock { queuedSongIds.remove(song.id) }
				continue
			}

			if (!acquireDownloadSlot(song.id)) {
				queuedSongIdsMutex.withLock { queuedSongIds.remove(song.id) }
				continue
			}

			try {
				runDownloadJob(song)
			} finally {
				releaseDownloadSlot(song.id)
				queuedSongIdsMutex.withLock { queuedSongIds.remove(song.id) }
			}
		}
	}

	private suspend fun runDownloadJob(song: DomainSong) {
		val downloadJob = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
			if (shouldStartQueuedSong(song.id)) {
				executeDownloadProcess(song)
			}
		}

		activeDownloadsMutex.withLock {
			activeDownloads[song.id] = downloadJob
		}

		try {
			downloadJob.start()
			downloadJob.join()
		} finally {
			activeDownloadsMutex.withLock {
				if (activeDownloads[song.id] == downloadJob) {
					activeDownloads.remove(song.id)
				}
			}
		}
	}

	private suspend fun queueSongDownloads(songs: List<DomainSong>) {
		startupQueueRecovery.join()
		val distinctSongs = songs.distinctBy { it.id }
		if (distinctSongs.isEmpty()) return

		val songIdsToQueue = collectionSongIdsToQueue(
			songIds = distinctSongs.map { it.id },
			downloads = downloadDao.getAllDownloadsList()
		).toSet()
		if (songIdsToQueue.isEmpty()) return

		val songsToQueue = distinctSongs.filter { it.id in songIdsToQueue }
		songsToQueue.forEach { song ->
			downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.QUEUED, 0f))
		}
		sendSongsToQueue(songsToQueue)
	}

	private suspend fun sendSongsToQueue(songs: List<DomainSong>) {
		songs.distinctBy { it.id }.forEach { song ->
			val shouldSend = queuedSongIdsMutex.withLock {
				queuedSongIds.add(song.id)
			}
			if (shouldSend) {
				songDownloadQueue.send(song)
			}
		}
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

	private suspend fun shouldStartQueuedSong(songId: String): Boolean =
		when (downloadDao.getDownloadById(songId)?.status) {
			DownloadStatus.QUEUED,
			DownloadStatus.FAILED -> true

			DownloadStatus.DOWNLOADING,
			DownloadStatus.DOWNLOADED,
			DownloadStatus.NOT_DOWNLOADED,
			null -> false
		}

	private suspend fun acquireDownloadSlot(songId: String): Boolean {
		while (shouldStartQueuedSong(songId)) {
			val acquired = runningDownloadSlotsMutex.withLock {
				if (
					canStartQueuedDownload(
						activeDownloadSongIds = runningDownloadSlots,
						queuedSongId = songId,
						configuredLimit = preferenceManager.maxConcurrentDownloads
					)
				) {
					runningDownloadSlots += songId
					true
				} else {
					false
				}
			}
			if (acquired) return true
			delay(DOWNLOAD_SLOT_RETRY_DELAY_MS)
		}
		return false
	}

	private suspend fun releaseDownloadSlot(songId: String) {
		runningDownloadSlotsMutex.withLock {
			runningDownloadSlots.remove(songId)
		}
	}

	private suspend fun executeDownloadProcess(song: DomainSong) {
		while (true) {
			try {
				Logger.i("DownloadManager", "beginning download for ${song.id}")
				downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.DOWNLOADING, 0f))

				downloadAudioFile(song)
				cacheSongCoverArt(song.coverArtId)
				cacheAlbumCoverArt(song.albumId)
				cacheLyrics(song)
				cacheOfflineLidaClip(song)
				return
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				if (shouldFailHostedDownload(e)) {
					Logger.e("DownloadManager", "Navidrome service appears unavailable while downloading ${song.id}", e)
					downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.FAILED, 0f))
					return
				}
				Logger.w("DownloadManager", "Download retry queued for ${song.id}", e)
				downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.QUEUED, 0f))
				delay(HOSTED_DOWNLOAD_RETRY_DELAY_MS)
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
			.size(Size.ORIGINAL)
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

	private suspend fun downloadAudioFile(song: DomainSong) {
		var lastProgress = 0f
		var progressJob: Job? = null

		val request = client.prepareRequest(sessionManager.api.getStreamUrl(song.id)) {
			method = HttpMethod.Get
			preferenceManager.serverRequestHeadersMap().forEach { (key, value) -> header(key, value) }
			onDownload { bytesSentTotal, contentLength ->
				if (contentLength != null && contentLength > 0L) {
					val progress = (bytesSentTotal.toDouble() / contentLength).toFloat()
					if (progress - lastProgress >= 0.01f || progress == 1f) {
						lastProgress = progress
						Logger.i("DownloadManager", "downloading ${song.id} $progress")

						progressJob?.cancel()

						progressJob = scope.launch {
							downloadDao.updateProgress(
								song.id,
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

		request.execute { response ->
			if (response.status.value !in 200..299) {
				throw IllegalStateException(
					"Stream request failed for ${song.id}: HTTP ${response.status.value} ${response.status.description}"
				)
			}
			Logger.i("DownloadManager", "writing download for ${song.id}")
			val path = storageManager.getDownloadPath(song.id, song.fileExtension)
			storageManager.saveFile(path, response.bodyAsChannel())
			Logger.i("DownloadManager", "wrote download for ${song.id}")

			progressJob?.cancel()

			downloadDao.insertDownload(
				DownloadEntity(
					song.id,
					DownloadStatus.DOWNLOADED,
					1f,
					path
				)
			)
		}
	}

	private companion object {
		const val DOWNLOAD_SLOT_RETRY_DELAY_MS = 250L
		const val LIBRARY_PROGRESS_POLL_DELAY_MS = 500L
		const val HOSTED_DOWNLOAD_RETRY_DELAY_MS = 30_000L
	}
}
