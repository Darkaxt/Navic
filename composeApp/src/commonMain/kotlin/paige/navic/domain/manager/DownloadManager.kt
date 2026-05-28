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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
import paige.navic.domain.models.collectionDownloadStatus
import paige.navic.domain.models.collectionSongIdsToQueue
import paige.navic.domain.models.queuedDownloadRecovery
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
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val client = HttpClient()
	private val activeDownloadsMutex = Mutex()
	private val activeDownloads = mutableMapOf<String, Job>()
	private val downloadSemaphore =
		Semaphore(10)// idk a good number, maybe u should be able to choose
	private val collectionDownloadQueue = Channel<QueuedCollectionDownload>(Channel.UNLIMITED)
	private val collectionQueueMutex = Mutex()
	private val queuedCollectionDownloads = mutableMapOf<String, QueuedCollectionDownload>()
	private val runningCollectionDownloads = mutableMapOf<String, Job>()
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

	private data class QueuedCollectionDownload(
		val id: String,
		val songs: List<DomainSong>
	)

	init {
		startupQueueRecovery.start()
		scope.launch {
			allDownloads.collectLatest { downloads ->
				_downloadedSongs.value = downloads
					.filter { it.status == DownloadStatus.DOWNLOADED && it.filePath != null }
					.associate { it.songId to it.filePath!! }
			}
		}
		scope.launch(Dispatchers.IO) {
			processCollectionDownloadQueue()
		}
	}

	fun getDownloadedFilePath(songId: String): String? {
		return _downloadedSongs.value[songId]
	}

	fun downloadSong(song: DomainSong): Job {
		val job = scope.launch(Dispatchers.IO) {
			val alreadyActive = activeDownloadsMutex.withLock { activeDownloads.containsKey(song.id) }
			if (alreadyActive) return@launch

			try {
				activeDownloadsMutex.withLock { activeDownloads[song.id] = coroutineContext[Job]!! }

				downloadSemaphore.withPermit {
					executeDownloadProcess(song)
				}
			} finally {
				activeDownloadsMutex.withLock { activeDownloads.remove(song.id) }
			}
		}
		return job
	}

	suspend fun downloadCollection(collection: DomainSongCollection) {
		startupQueueRecovery.join()
		if (collection.songs.isEmpty()) return

		val songIdsToQueue = collectionSongIdsToQueue(
			songIds = collection.songs.map { it.id },
			downloads = downloadDao.getAllDownloadsList()
		).toSet()
		if (songIdsToQueue.isEmpty()) return

		val queuedDownload = QueuedCollectionDownload(
			id = collection.id,
			songs = collection.songs.filter { it.id in songIdsToQueue }
		)

		collectionQueueMutex.withLock {
			if (queuedCollectionDownloads.containsKey(collection.id) ||
				runningCollectionDownloads.containsKey(collection.id)
			) {
				return
			}

			queuedCollectionDownloads[collection.id] = queuedDownload
			queuedDownload.songs.forEach { song ->
				downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.QUEUED, 0f))
			}
			collectionDownloadQueue.send(queuedDownload)
		}
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

				val downloadQueue = Channel<DomainSong>(Channel.UNLIMITED)
				songsToDownload.forEach { downloadQueue.trySend(it) }
				downloadQueue.close()

				var processedCount = 0
				val progressMutex = Mutex()

				val workers = List(10) {
					launch {
						for (song in downloadQueue) {
							downloadSong(song).join()

							progressMutex.withLock {
								processedCount++
								_libraryDownloadProgress.value = processedCount.toFloat() / totalToDownload.toFloat()
							}
						}
					}
				}

				workers.joinAll()
				_isDownloadingLibrary.value = false

			} catch (_: CancellationException) {
				_isDownloadingLibrary.value = false
				_libraryDownloadProgress.value = 0f
			}
		}
	}

	fun cancelAllActiveDownloads() {
		libraryDownloadJob?.cancel()
		libraryDownloadJob = null
		_isDownloadingLibrary.value = false
		_libraryDownloadProgress.value = 0f

		scope.launch(Dispatchers.IO) {
			val collectionJobsToCancel = collectionQueueMutex.withLock {
				val jobs = runningCollectionDownloads.values.toList()
				queuedCollectionDownloads.clear()
				runningCollectionDownloads.clear()
				jobs
			}
			collectionJobsToCancel.forEach { it.cancel() }

			val jobsToCancel = activeDownloadsMutex.withLock {
				val copy = activeDownloads.toMap()
				activeDownloads.clear()
				copy
			}

			jobsToCancel.forEach { (songId, job) ->
				job.cancel()
				val existing = downloadDao.getDownloadById(songId)
				if (existing?.status == DownloadStatus.DOWNLOADING ||
					existing?.status == DownloadStatus.QUEUED
				) {
					downloadDao.deleteDownload(songId)
				}
			}

			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.QUEUED }
				.forEach { downloadDao.deleteDownload(it.songId) }
		}
	}

	fun cancelDownload(songId: String) {
		scope.launch(Dispatchers.IO) {
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

	fun cancelCollectionDownload(collection: DomainSongCollection) {
		scope.launch(Dispatchers.IO) {
			collectionQueueMutex.withLock {
				queuedCollectionDownloads.remove(collection.id)
				runningCollectionDownloads.remove(collection.id)?.cancel()
			}
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

		val queuedDownload = QueuedCollectionDownload(
			id = RECOVERED_COLLECTION_DOWNLOAD_ID,
			songs = songsToResume
		)
		collectionQueueMutex.withLock {
			queuedCollectionDownloads[queuedDownload.id] = queuedDownload
			collectionDownloadQueue.send(queuedDownload)
		}
	}

	private suspend fun processCollectionDownloadQueue() {
		startupQueueRecovery.join()
		for (queuedDownload in collectionDownloadQueue) {
			val collectionJob = collectionQueueMutex.withLock {
				val request = queuedCollectionDownloads.remove(queuedDownload.id) ?: return@withLock null
				scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
					downloadQueuedCollection(request)
				}.also { runningCollectionDownloads[request.id] = it }
			} ?: continue

			collectionJob.start()
			try {
				collectionJob.join()
			} finally {
				collectionQueueMutex.withLock {
					runningCollectionDownloads.remove(queuedDownload.id)
				}
			}
		}
	}

	private suspend fun downloadQueuedCollection(queuedDownload: QueuedCollectionDownload) {
		queuedDownload.songs
			.filter { song ->
				when (downloadDao.getDownloadById(song.id)?.status) {
					DownloadStatus.QUEUED,
					DownloadStatus.FAILED -> true

					else -> false
				}
			}
			.map { downloadSong(it) }
			.joinAll()
	}

	private suspend fun executeDownloadProcess(song: DomainSong) {
		try {
			Logger.i("DownloadManager", "beginning download for ${song.id}")
			downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.DOWNLOADING, 0f))

			cacheCoverArt(song.coverArtId)
			cacheAlbumCoverArt(song.albumId)
			cacheLyrics(song)
			downloadAudioFile(song)

		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e("DownloadManager", "Failed to download song ${song.id}", e)
			downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.FAILED, 0f))
		} finally {
			activeDownloadsMutex.withLock {
				activeDownloads.remove(song.id)
			}
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
		const val RECOVERED_COLLECTION_DOWNLOAD_ID = "__recovered_queued_downloads__"
	}
}
