package paige.navic.domain.manager

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.data.database.dao.LidaClipDownloadDao
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LidaClipDownloadEntity
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.models.lidaClipCacheFileExtension
import paige.navic.domain.models.shouldFailHostedDownload
import paige.navic.domain.repositories.lidaClipsStreamRequestHeaders
import paige.navic.util.core.Logger
import kotlin.time.Clock

private const val TAG = "LidaClipDownloadManager"

class LidaClipDownloadManager(
	private val lidaClipDownloadDao: LidaClipDownloadDao,
	private val storageManager: StorageManager,
	private val preferenceManager: PreferenceManager,
	private val lidaClipCacheManager: LidaClipCacheManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val activeDownloadsMutex = Mutex()
	private val activeDownloads = mutableMapOf<String, ActiveLidaClipDownload>()

	val allDownloads = lidaClipDownloadDao.getAllDownloads().map { downloads ->
		downloads.toImmutableList()
	}

	suspend fun getOrQueueClipForPlayback(
		songId: String,
		clip: DomainLidaClip,
		persistOffline: Boolean
	): Result<DomainLidaClip?> {
		cachedDownloadOrNull(songId, clip, persistOffline)?.let { cachedClip ->
			return Result.success(cachedClip)
		}

		val activeDownload = activeDownloadsMutex.withLock {
			activeDownloads[songId]?.let { return@withLock it }

			val result = CompletableDeferred<Result<DomainLidaClip?>>()
			val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
				try {
					result.complete(
						executeDownload(
							songId = songId,
							clip = clip,
							persistOffline = persistOffline
						)
					)
				} catch (error: CancellationException) {
					result.complete(Result.failure(error))
				} finally {
					activeDownloadsMutex.withLock {
						activeDownloads.remove(songId)
					}
				}
			}
			ActiveLidaClipDownload(job, result).also { active ->
				activeDownloads[songId] = active
				job.start()
			}
		}

		return activeDownload.result.await()
	}

	fun cancelDownload(songId: String) {
		scope.launch(Dispatchers.IO) {
			activeDownloadsMutex.withLock {
				activeDownloads.remove(songId)?.job?.cancel()
			}
			lidaClipDownloadDao.deleteDownload(songId)
		}
	}

	fun retryFailedDownloads() {
		scope.launch(Dispatchers.IO) {
			lidaClipDownloadDao.getAllDownloadsList()
				.filter { download -> download.status == DownloadStatus.FAILED }
				.forEach { download ->
					getOrQueueClipForPlayback(
						songId = download.songId,
						clip = download.toDomainLidaClip(),
						persistOffline = download.persistOffline
					)
				}
		}
	}

	fun retryDownload(songId: String) {
		scope.launch(Dispatchers.IO) {
			lidaClipDownloadDao.getAllDownloadsList()
				.firstOrNull { download ->
					download.songId == songId &&
						download.status == DownloadStatus.FAILED
				}
				?.let { download ->
					getOrQueueClipForPlayback(
						songId = download.songId,
						clip = download.toDomainLidaClip(),
						persistOffline = download.persistOffline
					)
				}
		}
	}

	fun discardFailedDownloads() {
		scope.launch(Dispatchers.IO) {
			lidaClipDownloadDao.deleteDownloadsWithStatus(DownloadStatus.FAILED)
		}
	}

	fun clearDownloadQueue() {
		scope.launch(Dispatchers.IO) {
			activeDownloadsMutex.withLock {
				activeDownloads.values.forEach { active -> active.job.cancel() }
				activeDownloads.clear()
			}
			lidaClipDownloadDao.deleteDownloadsWithStatus(DownloadStatus.DOWNLOADING)
			lidaClipDownloadDao.deleteDownloadsWithStatus(DownloadStatus.QUEUED)
			lidaClipDownloadDao.deleteDownloadsWithStatus(DownloadStatus.FAILED)
			lidaClipDownloadDao.deleteDownloadsWithStatus(DownloadStatus.DOWNLOADED)
		}
	}

	private suspend fun executeDownload(
		songId: String,
		clip: DomainLidaClip,
		persistOffline: Boolean
	): Result<DomainLidaClip?> {
		cachedDownloadOrNull(songId, clip, persistOffline)?.let { cachedClip ->
			return Result.success(cachedClip)
		}

		val queued = clip.toDownloadEntity(
			songId = songId,
			status = DownloadStatus.QUEUED,
			progress = 0f,
			filePath = null,
			persistOffline = persistOffline
		)
		lidaClipDownloadDao.insertDownload(queued)

		while (true) {
			try {
				lidaClipDownloadDao.insertDownload(
					queued.copy(
						status = DownloadStatus.DOWNLOADING,
						progress = 0f,
						updatedAtMillis = nowMillis()
					)
				)

				var lastProgress = 0f
				val cachedClip = lidaClipCacheManager.getOrCacheClip(
					clip = clip,
					requestHeaders = lidaClipsStreamRequestHeaders(
						baseUrl = preferenceManager.lidaClipsBaseUrl,
						streamUrl = clip.streamUrl,
						requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
					),
					songId = songId,
					persistOffline = persistOffline,
					onProgress = { progress ->
						val boundedProgress = progress.coerceIn(0f, 1f)
						if (boundedProgress - lastProgress >= 0.01f || boundedProgress == 1f) {
							lastProgress = boundedProgress
							scope.launch(Dispatchers.IO) {
								lidaClipDownloadDao.insertDownload(
									queued.copy(
										status = DownloadStatus.DOWNLOADING,
										progress = boundedProgress,
										updatedAtMillis = nowMillis()
									)
								)
							}
						}
					}
				).getOrThrow()
					?: error("LidaClips video cache is disabled")

				val filePath = cachedFilePathFor(songId, clip)
					?: error("LidaClips video was not cached for playback")
				val playableClip = cachedClip.copy(streamUrl = storageManager.fileUri(filePath))
				lidaClipDownloadDao.insertDownload(
					queued.copy(
						status = DownloadStatus.DOWNLOADED,
						progress = 1f,
						filePath = filePath,
						updatedAtMillis = nowMillis()
					)
				)
				preferenceManager.markIntegrationServiceAvailable(IntegrationService.LidaClips)
				return Result.success(playableClip)
			} catch (error: CancellationException) {
				lidaClipDownloadDao.deleteDownload(songId)
				return Result.failure(error)
			} catch (error: Throwable) {
				if (shouldFailHostedDownload(error)) {
					Logger.w(TAG, "LidaClips service appears unavailable while caching song $songId", error)
					preferenceManager.markIntegrationServiceDown(IntegrationService.LidaClips)
					lidaClipDownloadDao.insertDownload(
						queued.copy(
							status = DownloadStatus.FAILED,
							progress = 0f,
							filePath = null,
							updatedAtMillis = nowMillis()
						)
					)
					return Result.failure(error)
				}

				Logger.w(TAG, "LidaClips cache retry queued for song $songId", error)
				lidaClipDownloadDao.insertDownload(
					queued.copy(
						status = DownloadStatus.QUEUED,
						progress = 0f,
						filePath = null,
						updatedAtMillis = nowMillis()
					)
				)
				delay(HOSTED_DOWNLOAD_RETRY_DELAY_MS)
			}
		}
	}

	private suspend fun cachedDownloadOrNull(
		songId: String,
		clip: DomainLidaClip,
		persistOffline: Boolean
	): DomainLidaClip? {
		val filePath = cachedFilePathFor(songId, clip) ?: return null
		lidaClipDownloadDao.insertDownload(
			clip.toDownloadEntity(
				songId = songId,
				status = DownloadStatus.DOWNLOADED,
				progress = 1f,
				filePath = filePath,
				persistOffline = persistOffline
			)
		)
		return clip.copy(streamUrl = storageManager.fileUri(filePath))
	}

	private fun cachedFilePathFor(
		songId: String,
		clip: DomainLidaClip
	): String? {
		val extension = lidaClipCacheFileExtension(
			mimeType = clip.mimeType,
			fileName = clip.fileName,
			streamUrl = clip.streamUrl
		)
		val offlinePath = storageManager.getLidaClipOfflinePath(songId, clip.id, extension)
		if (storageManager.fileExists(offlinePath) && storageManager.getFileSize(offlinePath) > 0L) {
			storageManager.touchFile(offlinePath)
			return offlinePath
		}

		val cachePath = storageManager.getLidaClipVideoCachePath(clip.id, extension)
		if (storageManager.fileExists(cachePath) && storageManager.getFileSize(cachePath) > 0L) {
			storageManager.touchFile(cachePath)
			return cachePath
		}

		return null
	}

	private fun DomainLidaClip.toDownloadEntity(
		songId: String,
		status: DownloadStatus,
		progress: Float,
		filePath: String?,
		persistOffline: Boolean
	): LidaClipDownloadEntity =
		LidaClipDownloadEntity(
			songId = songId,
			clipId = id,
			title = title,
			artist = artist,
			album = album,
			track = track,
			durationSeconds = durationSeconds,
			mimeType = mimeType,
			qualityTier = qualityTier,
			fileName = fileName,
			streamUrl = streamUrl,
			status = status,
			progress = progress,
			filePath = filePath,
			persistOffline = persistOffline,
			updatedAtMillis = nowMillis()
		)

	private fun LidaClipDownloadEntity.toDomainLidaClip(): DomainLidaClip =
		DomainLidaClip(
			id = clipId,
			navidromeSongId = songId,
			title = title,
			artist = artist,
			album = album,
			track = track,
			durationSeconds = durationSeconds,
			mimeType = mimeType,
			score = null,
			qualityTier = qualityTier,
			fileName = fileName,
			streamUrl = streamUrl
		)

	private fun nowMillis(): Long =
		Clock.System.now().toEpochMilliseconds()

	private data class ActiveLidaClipDownload(
		val job: Job,
		val result: CompletableDeferred<Result<DomainLidaClip?>>
	)

	private companion object {
		const val HOSTED_DOWNLOAD_RETRY_DELAY_MS = 30_000L
	}
}
