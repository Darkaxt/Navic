package paige.navic.domain.manager

import android.content.Context
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import paige.navic.domain.models.LidaClipCacheFileInfo
import paige.navic.domain.models.lidaClipOfflineFileName
import paige.navic.domain.models.lidaClipOfflineFilePrefix
import java.io.File
import java.io.FileOutputStream

actual class StorageManager(
	private val context: Context
) {
	private val dispatcher = Dispatchers.IO

	actual fun getDownloadPath(songId: String, extension: String): String {
		return File(downloadsDir(), "$songId.$extension").absolutePath
	}

	actual fun getLidaClipVideoCachePath(clipId: Int, extension: String): String {
		return File(lidaClipVideoCacheDir(), "$clipId.$extension").absolutePath
	}

	actual fun getLidaClipVideoCacheTempPath(clipId: Int, extension: String): String {
		return File(lidaClipVideoCacheDir(), "$clipId.$extension.part").absolutePath
	}

	actual fun getLidaClipOfflinePath(songId: String, clipId: Int, extension: String): String {
		return File(lidaClipOfflineDir(), lidaClipOfflineFileName(songId, clipId, extension)).absolutePath
	}

	actual fun getLidaClipOfflineTempPath(songId: String, clipId: Int, extension: String): String {
		return File(lidaClipOfflineDir(), "${lidaClipOfflineFileName(songId, clipId, extension)}.part").absolutePath
	}

	actual fun deleteFile(path: String): Boolean {
		return File(path).delete()
	}

	actual fun fileExists(path: String): Boolean {
		return File(path).isFile
	}

	actual fun moveFile(sourcePath: String, destinationPath: String): Boolean {
		val source = File(sourcePath)
		val destination = File(destinationPath)
		destination.parentFile?.mkdirs()
		if (destination.exists()) destination.delete()
		return source.renameTo(destination)
	}

	actual fun fileUri(path: String): String {
		return File(path).toURI().toString()
	}

	actual fun getFileSize(path: String): Long {
		return try {
			File(path).length()
		} catch (_: Exception) {
			0L
		}
	}

	actual fun touchFile(path: String): Boolean {
		return File(path).takeIf { it.exists() }?.setLastModified(System.currentTimeMillis()) == true
	}

	actual fun listLidaClipVideoCacheFiles(): List<LidaClipCacheFileInfo> {
		return listFiles(lidaClipVideoCacheDir())
	}

	actual fun listLidaClipOfflineFiles(): List<LidaClipCacheFileInfo> {
		return listFiles(lidaClipOfflineDir())
	}

	actual suspend fun saveFile(path: String, channel: ByteReadChannel) {
		withContext(dispatcher) {
			File(path).parentFile?.mkdirs()
			FileOutputStream(path).use { outputStream ->
				channel.copyTo(outputStream)
			}
		}
	}

	actual fun clearDownloads() {
		downloadsDir().listFiles()?.forEach { it.deleteRecursively() }
	}

	actual fun clearLidaClipVideoCache() {
		lidaClipVideoCacheDir().listFiles()?.forEach { it.deleteRecursively() }
	}

	actual fun clearLidaClipOfflineFiles() {
		lidaClipOfflineDir().listFiles()?.forEach { it.deleteRecursively() }
	}

	actual fun clearLidaClipOfflineFilesForSong(songId: String) {
		val prefix = lidaClipOfflineFilePrefix(songId)
		lidaClipOfflineDir()
			.listFiles()
			.orEmpty()
			.filter { it.isFile && it.name.startsWith(prefix) }
			.forEach { it.delete() }
	}

	private fun downloadsDir(): File {
		val dir = File(context.filesDir, "downloads")
		if (!dir.exists()) dir.mkdirs()
		return dir
	}

	private fun lidaClipVideoCacheDir(): File {
		val dir = File(context.cacheDir, "lida_clips")
		if (!dir.exists()) dir.mkdirs()
		return dir
	}

	private fun lidaClipOfflineDir(): File {
		val dir = File(context.filesDir, "lida_clips")
		if (!dir.exists()) dir.mkdirs()
		return dir
	}

	private fun listFiles(dir: File): List<LidaClipCacheFileInfo> =
		dir
			.listFiles()
			.orEmpty()
			.filter { it.isFile && !it.name.endsWith(".part") }
			.map {
				LidaClipCacheFileInfo(
					path = it.absolutePath,
					sizeBytes = it.length(),
					lastModifiedMillis = it.lastModified()
				)
			}
}
