package paige.navic.reader

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class StorytellerArchiveReadMetrics(
	var archiveOpenCount: Int = 0,
	var peakBufferedMetadataBytes: Int = 0,
	var peakStreamBufferBytes: Int = 0,
	var streamedAudioBytes: Long = 0L,
	val openedEntryNames: MutableList<String> = mutableListOf(),
	val streamedEntryNames: MutableList<String> = mutableListOf()
)

internal class StorytellerEpubArchive private constructor(
	private val zipFile: ZipFile,
	private val metrics: StorytellerArchiveReadMetrics
) : Closeable {
	private val entriesByPath: Map<String, ZipEntry> = buildMap {
		val entries = zipFile.entries()
		while (entries.hasMoreElements()) {
			val entry = entries.nextElement()
			if (entry.isDirectory) continue
			val path = normalizedMediaOverlayResource(entry.name)
			require(path.isNotBlank()) { "EPUB archive contains an empty entry path." }
			require(put(path, entry) == null) { "EPUB archive contains duplicate normalized entry '$path'." }
		}
	}

	val entryNames: Set<String>
		get() = entriesByPath.keys

	fun contains(path: String): Boolean =
		entriesByPath.containsKey(normalizedMediaOverlayResource(path))

	fun entrySize(path: String): Long? =
		entriesByPath[normalizedMediaOverlayResource(path)]
			?.size
			?.takeIf { it >= 0L }

	fun readMetadata(path: String): ByteArray? {
		val normalizedPath = normalizedMediaOverlayResource(path)
		val entry = entriesByPath[normalizedPath] ?: return null
		val declaredSize = entry.size
		require(declaredSize < 0L || declaredSize <= MaxMetadataEntryBytes) {
			"EPUB metadata entry '$normalizedPath' exceeds the $MaxMetadataEntryBytes-byte limit."
		}
		metrics.openedEntryNames += normalizedPath
		val initialCapacity = declaredSize
			.takeIf { it in 1..MaxMetadataEntryBytes }
			?.toInt()
			?: DefaultMetadataCapacity
		val bytes = zipFile.getInputStream(entry).use { input ->
			ByteArrayOutputStream(initialCapacity).use { output ->
				val buffer = ByteArray(StreamBufferBytes)
				var total = 0
				while (true) {
					val count = input.read(buffer)
					if (count < 0) break
					total += count
					require(total <= MaxMetadataEntryBytes) {
						"EPUB metadata entry '$normalizedPath' exceeds the $MaxMetadataEntryBytes-byte limit."
					}
					output.write(buffer, 0, count)
				}
				output.toByteArray()
			}
		}
		metrics.peakBufferedMetadataBytes = maxOf(metrics.peakBufferedMetadataBytes, bytes.size)
		return bytes
	}

	fun copyEntryTo(path: String, target: File): Boolean {
		val normalizedPath = normalizedMediaOverlayResource(path)
		val entry = entriesByPath[normalizedPath] ?: return false
		target.parentFile?.mkdirs()
		val buffer = ByteArray(StreamBufferBytes)
		metrics.peakStreamBufferBytes = maxOf(metrics.peakStreamBufferBytes, buffer.size)
		metrics.openedEntryNames += normalizedPath
		metrics.streamedEntryNames += normalizedPath
		var copied = 0L
		try {
			zipFile.getInputStream(entry).use { input ->
				FileOutputStream(target).buffered().use { output ->
					while (true) {
						val count = input.read(buffer)
						if (count < 0) break
						output.write(buffer, 0, count)
						copied += count
					}
				}
			}
		} catch (error: Throwable) {
			target.delete()
			throw error
		}
		metrics.streamedAudioBytes += copied
		return true
	}

	override fun close() {
		zipFile.close()
	}

	companion object {
		private const val MaxMetadataEntryBytes = 8 * 1024 * 1024
		private const val DefaultMetadataCapacity = 8 * 1024
		internal const val StreamBufferBytes = 64 * 1024

		fun open(
			file: File,
			metrics: StorytellerArchiveReadMetrics = StorytellerArchiveReadMetrics()
		): StorytellerEpubArchive {
			require(file.isFile) { "Storyteller EPUB file is missing: ${file.path}" }
			val zipFile = ZipFile(file)
			return try {
				StorytellerEpubArchive(zipFile, metrics).also {
					metrics.archiveOpenCount += 1
				}
			} catch (error: Throwable) {
				zipFile.close()
				throw error
			}
		}
	}
}
