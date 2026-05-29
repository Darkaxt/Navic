package paige.navic.domain.manager

import io.ktor.client.HttpClient
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
		requestHeaders: Map<String, String>
	): Result<DomainLidaClip?> {
		val cacheSizeMb = preferenceManager.lidaClipsVideoCacheSizeMb
		if (cacheSizeMb <= 0) return Result.success(null)

		val extension = lidaClipCacheFileExtension(
			mimeType = clip.mimeType,
			fileName = clip.fileName,
			streamUrl = clip.streamUrl
		)
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
					downloadClip(clip, requestHeaders, tempPath)
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
		val cacheSizeMb = preferenceManager.lidaClipsVideoCacheSizeMb
		if (cacheSizeMb <= 0) return null

		val extension = lidaClipCacheFileExtension(
			mimeType = clip.mimeType,
			fileName = clip.fileName,
			streamUrl = clip.streamUrl
		)
		val cachePath = storageManager.getLidaClipVideoCachePath(clip.id, extension)
		return cachedClipOrNull(clip, cachePath, cacheSizeMb)
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
		tempPath: String
	) {
		val request = client.prepareRequest(clip.streamUrl) {
			method = HttpMethod.Get
			requestHeaders.forEach { (key, value) ->
				header(key, value)
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
