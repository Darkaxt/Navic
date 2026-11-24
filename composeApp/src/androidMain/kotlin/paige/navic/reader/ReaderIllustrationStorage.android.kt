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
		withContext(Dispatchers.IO) {
			val target = imageFile(sha256)
			target.parentFile?.mkdirs()
			val staging = File(target.parentFile, "${target.name}.tmp")
			staging.outputStream().use { output ->
				output.write(bytes)
				output.fd.sync()
			}
			if (!staging.renameTo(target)) {
				target.delete()
				check(staging.renameTo(target)) { "Illustration image could not be stored" }
			}
		}
	}

	override fun readFixtureIndexJson(bookId: Long): String? {
		val file = root().resolve(ReaderIllustrationFixtureDirectoryName).resolve("$bookId.json")
		return if (file.isFile) file.readText() else null
	}

	override fun imageUrl(sha256: String): String =
		readerPublicationAssetUrl("$ReaderIllustrationStorageDirectoryName/$sha256.webp")

	private fun imageFile(sha256: String): File = root().resolve("$sha256.webp")
}
