@file:OptIn(ExperimentalForeignApi::class)

package paige.navic.domain.manager

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import paige.navic.domain.models.LidaClipCacheFileInfo
import paige.navic.domain.models.lidaClipOfflineFileName
import paige.navic.domain.models.lidaClipOfflineFilePrefix
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSOutputStream
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.outputStreamToFileAtPath
import platform.posix.utime

private const val NSDateReferenceDateUnixOffsetSeconds = 978_307_200.0

actual class StorageManager {
	private val dispatcher = Dispatchers.IO

	actual fun getDownloadPath(songId: String, extension: String): String {
		return downloadsDir().URLByAppendingPathComponent("$songId.$extension")!!.path!!
	}

	actual fun getLidaClipVideoCachePath(clipId: Int, extension: String): String {
		return lidaClipVideoCacheDir().URLByAppendingPathComponent("$clipId.$extension")!!.path!!
	}

	actual fun getLidaClipVideoCacheTempPath(clipId: Int, extension: String): String {
		return lidaClipVideoCacheDir().URLByAppendingPathComponent("$clipId.$extension.part")!!.path!!
	}

	actual fun getLidaClipOfflinePath(songId: String, clipId: Int, extension: String): String {
		return lidaClipOfflineDir()
			.URLByAppendingPathComponent(lidaClipOfflineFileName(songId, clipId, extension))!!
			.path!!
	}

	actual fun getLidaClipOfflineTempPath(songId: String, clipId: Int, extension: String): String {
		return lidaClipOfflineDir()
			.URLByAppendingPathComponent("${lidaClipOfflineFileName(songId, clipId, extension)}.part")!!
			.path!!
	}

	actual fun deleteFile(path: String): Boolean {
		return NSFileManager.defaultManager.removeItemAtPath(path, null)
	}

	actual fun fileExists(path: String): Boolean {
		return NSFileManager.defaultManager.fileExistsAtPath(path)
	}

	actual fun moveFile(sourcePath: String, destinationPath: String): Boolean {
		val manager = NSFileManager.defaultManager
		if (manager.fileExistsAtPath(destinationPath)) {
			manager.removeItemAtPath(destinationPath, null)
		}
		return manager.moveItemAtPath(sourcePath, destinationPath, null)
	}

	actual fun fileUri(path: String): String {
		return NSURL.fileURLWithPath(path).absoluteString ?: path
	}

	actual fun getFileSize(path: String): Long {
		val manager = NSFileManager.defaultManager
		val attributes = manager.attributesOfItemAtPath(path, null)
		return (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
	}

	actual fun touchFile(path: String): Boolean {
		return utime(path, null) == 0
	}

	actual fun listLidaClipVideoCacheFiles(): List<LidaClipCacheFileInfo> {
		return listFiles(lidaClipVideoCacheDir())
	}

	actual fun listLidaClipOfflineFiles(): List<LidaClipCacheFileInfo> {
		return listFiles(lidaClipOfflineDir())
	}

	private fun listFiles(directoryUrl: NSURL): List<LidaClipCacheFileInfo> {
		val manager = NSFileManager.defaultManager
		val dir = directoryUrl.path ?: return emptyList()
		val names = manager.contentsOfDirectoryAtPath(dir, null).orEmpty()
		return names
			.mapNotNull { it as? String }
			.filterNot { it.endsWith(".part") }
			.mapNotNull { name ->
				val path = directoryUrl.URLByAppendingPathComponent(name)?.path
					?: return@mapNotNull null
				val attributes = manager.attributesOfItemAtPath(path, null)
				LidaClipCacheFileInfo(
					path = path,
					sizeBytes = (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L,
					lastModifiedMillis = (attributes?.get(NSFileModificationDate) as? NSDate)
						?.let { date ->
							((date.timeIntervalSinceReferenceDate + NSDateReferenceDateUnixOffsetSeconds) * 1000.0).toLong()
						} ?: 0L
				)
			}
	}

	actual suspend fun saveFile(path: String, channel: ByteReadChannel) {
		withContext(dispatcher) {
			val outputStream = NSOutputStream.outputStreamToFileAtPath(path, false)
			outputStream.open()
			try {
				val buffer = ByteArray(64 * 1024)
				while (true) {
					val read = channel.readAvailable(buffer)
					if (read == -1) break
					if (read > 0) {
						buffer.usePinned { pinned ->
							outputStream.write(pinned.addressOf(0).reinterpret(), read.toULong())
						}
					}
				}
			} finally {
				outputStream.close()
			}
		}
	}

	actual fun clearDownloads() {
		clearDirectory(downloadsDir())
	}

	actual fun clearLidaClipVideoCache() {
		clearDirectory(lidaClipVideoCacheDir())
	}

	actual fun clearLidaClipOfflineFiles() {
		clearDirectory(lidaClipOfflineDir())
	}

	actual fun clearLidaClipOfflineFilesForSong(songId: String) {
		val manager = NSFileManager.defaultManager
		val dir = lidaClipOfflineDir()
		val prefix = lidaClipOfflineFilePrefix(songId)
		val dirPath = dir.path ?: return
		manager.contentsOfDirectoryAtPath(dirPath, null)
			.orEmpty()
			.mapNotNull { it as? String }
			.filter { it.startsWith(prefix) }
			.forEach { name ->
				dir.URLByAppendingPathComponent(name)?.path?.let { path ->
					manager.removeItemAtPath(path, null)
				}
			}
	}

	private fun downloadsDir(): NSURL =
		directory(NSDocumentDirectory, "downloads")

	private fun lidaClipVideoCacheDir(): NSURL =
		directory(NSCachesDirectory, "lida_clips")

	private fun lidaClipOfflineDir(): NSURL =
		directory(NSDocumentDirectory, "lida_clips")

	private fun directory(directory: ULong, child: String): NSURL {
		val manager = NSFileManager.defaultManager
		val url = manager.URLsForDirectory(directory, NSUserDomainMask).first() as NSURL
		val dir = url.URLByAppendingPathComponent(child)!!
		if (!manager.fileExistsAtPath(dir.path!!)) {
			manager.createDirectoryAtURL(dir, true, null, null)
		}
		return dir
	}

	private fun clearDirectory(dir: NSURL) {
		val manager = NSFileManager.defaultManager
		if (manager.fileExistsAtPath(dir.path!!)) {
			manager.removeItemAtURL(dir, null)
			manager.createDirectoryAtURL(dir, true, null, null)
		}
	}

	private fun <T> ByteArray.usePinned(block: (Pinned<ByteArray>) -> T): T {
		val pinned = this.pin()
		try {
			return block(pinned)
		} finally {
			pinned.unpin()
		}
	}
}
