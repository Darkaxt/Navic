package paige.navic.domain.manager

import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.lidaClipCacheFileExtension
import paige.navic.domain.models.lidaClipCachePrunePlan
import paige.navic.domain.models.lidaClipVideoCacheSizeBytes
import paige.navic.domain.models.shouldUseCachedLidaClipVideo
import paige.navic.domain.models.shouldUseOfflineLidaClipVideo
import paige.navic.util.core.Logger

private const val TAG = "LidaClipCacheManager"

class LidaClipCacheManager(
	private val storageManager: StorageManager,
	private val preferenceManager: PreferenceManager
) {
	private val client = HttpClient()
	private val locksGuard = Mutex()
	private val clipLocks = mutableMapOf<Int, Mutex>()

	suspend fun getOrCacheClip(
		clip: DomainLidaClip,
		requestHeaders: Map<String, String>,
		songId: String? = null,
		persistOffline: Boolean = false,
		onProgress: (Float) -> Unit = {}
	): Result<DomainLidaClip?> {
		val extension = lidaClipCacheFileExtension(
			mimeType = clip.mimeType,
			fileName = clip.fileName,
			streamUrl = clip.streamUrl
		)
		if (!songId.isNullOrBlank()) {
			offlineClipOrNull(songId, clip, extension)?.let {
				return Result.success(it)
			}
		}

		if (persistOffline && !songId.isNullOrBlank()) {
			return getOrSaveOfflineClip(songId, clip, requestHeaders, extension, onProgress)
		}

		val cacheSizeMb = preferenceManager.lidaClipsVideoCacheSizeMb
		if (cacheSizeMb <= 0) return Result.success(null)

		val cachePath = storageManager.getLidaClipVideoCachePath(clip.id, extension)
		cachedClipOrNull(clip, cachePath, cacheSizeMb)?.let {
			pruneCache(protectedPath = cachePath)
			return Result.success(it)
		}

		return try {
			clipLock(clip.id).withLock {
				cachedClipOrNull(clip, cachePath, cacheSizeMb)?.let {
					pruneCache(protectedPath = cachePath)
					return@withLock it
				}

				val tempPath = storageManager.getLidaClipVideoCacheTempPath(clip.id, extension)
				try {
					storageManager.deleteFile(tempPath)
					downloadClip(clip, requestHeaders, tempPath, onProgress)
					if (storageManager.getFileSize(tempPath) <= 0L) {
						error("Cached LidaClips video was empty")
					}
					if (!storageManager.moveFile(tempPath, cachePath)) {
						error("Failed to move cached LidaClips video into place")
					}
				} catch (error: Throwable) {
					storageManager.deleteFile(tempPath)
					throw error
				}
				storageManager.touchFile(cachePath)
				pruneCache(protectedPath = cachePath)

				clip.copy(streamUrl = storageManager.fileUri(cachePath))
			}.let { Result.success(it) }
		} catch (error: CancellationException) {
			throw error
		} catch (error: Throwable) {
			Logger.w(TAG, "Failed to cache LidaClips video for clip ${clip.id}", error)
			Result.failure(error)
		}
	}

	fun cachedClipFor(clip: DomainLidaClip): DomainLidaClip? {
		return cachedClipFor(songId = null, clip = clip)
	}

	fun cachedClipFor(
		songId: String?,
		clip: DomainLidaClip
	): DomainLidaClip? {
		val extension = lidaClipCacheFileExtension(
			mimeType = clip.mimeType,
			fileName = clip.fileName,
			streamUrl = clip.streamUrl
		)
		if (!songId.isNullOrBlank()) {
			offlineClipOrNull(songId, clip, extension)?.let { return it }
		}

		val cacheSizeMb = preferenceManager.lidaClipsVideoCacheSizeMb
		if (cacheSizeMb <= 0) return null

		val cachePath = storageManager.getLidaClipVideoCachePath(clip.id, extension)
		return cachedClipOrNull(clip, cachePath, cacheSizeMb)
	}

	private suspend fun getOrSaveOfflineClip(
		songId: String,
		clip: DomainLidaClip,
		requestHeaders: Map<String, String>,
		extension: String,
		onProgress: (Float) -> Unit
	): Result<DomainLidaClip?> {
		val offlinePath = storageManager.getLidaClipOfflinePath(songId, clip.id, extension)
		offlineClipOrNull(songId, clip, extension)?.let {
			return Result.success(it)
		}

		return try {
			clipLock(clip.id).withLock {
				offlineClipOrNull(songId, clip, extension)?.let {
					return@withLock it
				}

				val tempPath = storageManager.getLidaClipOfflineTempPath(songId, clip.id, extension)
				try {
					storageManager.deleteFile(tempPath)
					downloadClip(clip, requestHeaders, tempPath, onProgress)
					if (storageManager.getFileSize(tempPath) <= 0L) {
						error("Offline LidaClips video was empty")
					}
					if (!storageManager.moveFile(tempPath, offlinePath)) {
						error("Failed to move offline LidaClips video into place")
					}
				} catch (error: Throwable) {
					storageManager.deleteFile(tempPath)
					throw error
				}
				storageManager.touchFile(offlinePath)
				clip.copy(streamUrl = storageManager.fileUri(offlinePath))
			}.let { Result.success(it) }
		} catch (error: CancellationException) {
			throw error
		} catch (error: Throwable) {
			Logger.w(TAG, "Failed to save offline LidaClips video for clip ${clip.id}", error)
			Result.failure(error)
		}
	}

	private fun offlineClipOrNull(
		songId: String,
		clip: DomainLidaClip,
		extension: String
	): DomainLidaClip? {
		val offlinePath = storageManager.getLidaClipOfflinePath(songId, clip.id, extension)
		val offlineFileExists = storageManager.fileExists(offlinePath) &&
			storageManager.getFileSize(offlinePath) > 0L
		if (!shouldUseOfflineLidaClipVideo(offlineFileExists)) return null

		storageManager.touchFile(offlinePath)
		return clip.copy(streamUrl = storageManager.fileUri(offlinePath))
	}

	private fun cachedClipOrNull(
		clip: DomainLidaClip,
		cachePath: String,
		cacheSizeMb: Int
	): DomainLidaClip? {
		val cacheFileExists = storageManager.fileExists(cachePath) &&
			storageManager.getFileSize(cachePath) > 0L
		if (!shouldUseCachedLidaClipVideo(cacheSizeMb, cacheFileExists)) return null

		storageManager.touchFile(cachePath)
		return clip.copy(streamUrl = storageManager.fileUri(cachePath))
	}

	private suspend fun downloadClip(
		clip: DomainLidaClip,
		requestHeaders: Map<String, String>,
		tempPath: String,
		onProgress: (Float) -> Unit
	) {
		val request = client.prepareRequest(clip.streamUrl) {
			method = HttpMethod.Get
			requestHeaders.forEach { (key, value) ->
				header(key, value)
			}
			onDownload { bytesSentTotal, contentLength ->
				if (contentLength != null && contentLength > 0L) {
					onProgress((bytesSentTotal.toDouble() / contentLength).toFloat())
				}
			}
		}

		request.execute { response ->
			if (!response.status.isSuccess()) {
				error("LidaClips video cache request returned HTTP ${response.status.value}")
			}
			storageManager.saveFile(tempPath, response.bodyAsChannel())
		}
	}

	private fun pruneCache(protectedPath: String) {
		val maxSizeBytes = lidaClipVideoCacheSizeBytes(preferenceManager.lidaClipsVideoCacheSizeMb)
		lidaClipCachePrunePlan(
			files = storageManager.listLidaClipVideoCacheFiles(),
			maxSizeBytes = maxSizeBytes,
			protectedPath = protectedPath
		).forEach(storageManager::deleteFile)
	}

	private suspend fun clipLock(clipId: Int): Mutex =
		locksGuard.withLock {
			clipLocks.getOrPut(clipId) { Mutex() }
		}
}
