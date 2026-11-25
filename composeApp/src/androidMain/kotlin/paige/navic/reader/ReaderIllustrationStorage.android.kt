package paige.navic.reader

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val ReaderIllustrationStorageDirectoryName = "reader-illustrations"
private const val ReaderIllustrationFixtureDirectoryName = "fixtures"

/**
 * Illustration plates under `<reader managed root>/reader-illustrations/<sha256>.webp`. Like
 * reader-publications they are never wiped at startup or with a reader session. The WebView reaches
 * them through the existing `/reader-cache/` InternalStoragePathHandler on the managed root.
 * A fixture index may be placed at `reader-illustrations/fixtures/<bindery book id>.json`.
 */
class AndroidReaderIllustrationStorage internal constructor(
	private val root: () -> File
) : ReaderIllustrationStorage {
	constructor(context: Context) : this({
		readerManagedStorageRoot(context).resolve(ReaderIllustrationStorageDirectoryName)
	})

	override fun imageSizeBytes(sha256: String): Long {
		if (!sha256.isReaderIllustrationSha256()) return 0L
		val file = imageFile(sha256)
		return if (file.isFile) file.length().coerceAtLeast(0L) else 0L
	}

	override suspend fun writeImage(sha256: String, bytes: ByteArray) {
		require(sha256.isReaderIllustrationSha256()) { "Illustration image hash is invalid." }
		withContext(Dispatchers.IO) { writeAtomically(imageFile(sha256), bytes) }
	}

	override fun readFixtureIndexJson(bookId: Long): String? {
		val file = root().resolve(ReaderIllustrationFixtureDirectoryName).resolve("$bookId.json")
		return if (file.isFile) file.readText() else null
	}

	override fun imageUrl(sha256: String): String =
		readerPublicationAssetUrl("$ReaderIllustrationStorageDirectoryName/$sha256.webp")

	/** Absolute path of a stored plate image, or null when absent. */
	fun imageFilePath(sha256: String): String? =
		if (imageSizeBytes(sha256) > 0L) imageFile(sha256).absolutePath else null

	override fun livingFilePath(sha256: String, extension: String): String? {
		if (!sha256.isReaderIllustrationSha256() || extension !in LivingExtensions) return null
		val file = root().resolve("$sha256.$extension")
		return if (file.isFile && file.length() > 0L) file.absolutePath else null
	}

	override suspend fun writeLiving(sha256: String, extension: String, bytes: ByteArray) {
		require(sha256.isReaderIllustrationSha256() && extension in LivingExtensions) { "Living plate name is invalid." }
		withContext(Dispatchers.IO) { writeAtomically(root().resolve("$sha256.$extension"), bytes) }
	}

	override fun freeSpaceBytes(): Long {
		val dir = root().also { it.mkdirs() }
		return dir.usableSpace.takeIf { it > 0L } ?: Long.MAX_VALUE
	}

	private fun imageFile(sha256: String): File = root().resolve("$sha256.webp")

	private companion object {
		val LivingExtensions = setOf("mp4", "png")

		fun writeAtomically(target: File, bytes: ByteArray) {
			target.parentFile?.mkdirs()
			val staging = File(target.parentFile, "${target.name}.tmp")
			staging.outputStream().use { output ->
				output.write(bytes)
				output.fd.sync()
			}
			if (!staging.renameTo(target)) {
				target.delete()
				check(staging.renameTo(target)) { "Illustration file could not be stored" }
			}
		}
	}
}
