package paige.navic.domain.manager


import io.ktor.utils.io.ByteReadChannel
import paige.navic.domain.models.LidaClipCacheFileInfo

expect class StorageManager {
	fun getDownloadPath(songId: String, extension: String): String
	fun getLidaClipVideoCachePath(clipId: Int, extension: String): String
	fun getLidaClipVideoCacheTempPath(clipId: Int, extension: String): String
	fun getLidaClipOfflinePath(songId: String, clipId: Int, extension: String): String
	fun getLidaClipOfflineTempPath(songId: String, clipId: Int, extension: String): String
	fun deleteFile(path: String): Boolean
	fun fileExists(path: String): Boolean
	fun moveFile(sourcePath: String, destinationPath: String): Boolean
	fun fileUri(path: String): String
	fun getFileSize(path: String): Long
	fun touchFile(path: String): Boolean
	fun listLidaClipVideoCacheFiles(): List<LidaClipCacheFileInfo>
	fun listLidaClipOfflineFiles(): List<LidaClipCacheFileInfo>
	fun readerPublicationCacheSizeBytes(): Long
	suspend fun saveFile(path: String, channel: ByteReadChannel)
	fun clearDownloads()
	fun clearLidaClipVideoCache()
	fun clearReaderPublicationCache()
	fun clearLidaClipOfflineFiles()
	fun clearLidaClipOfflineFilesForSong(songId: String)
}
