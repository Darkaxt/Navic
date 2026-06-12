package paige.navic.reader

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

private const val ReaderImportedFontDirectoryName = "fonts"
private const val ReaderImportedFontMaxBytes = 64L * 1024L * 1024L

class ReaderImportedFontCache(
	private val cacheRoot: File,
	private val maxBytes: Long = ReaderImportedFontMaxBytes
) {
	fun cachedFontsByteSize(): Long =
		fontDirectory()
			.listFiles()
			.orEmpty()
			.filter { file -> file.isFile && !file.name.endsWith(".part") }
			.sumOf { file -> file.length().coerceAtLeast(0L) }

	fun clearImportedFonts(): Int {
		val directory = fontDirectory()
		if (!directory.exists()) return 0
		var deleted = 0
		directory.listFiles().orEmpty().forEach { file ->
			if (file.deleteRecursively()) deleted++
		}
		return deleted
	}

	fun importFont(
		input: InputStream,
		displayName: String?,
		mimeType: String?
	): ReaderImportedFont {
		val extension = readerImportedFontExtension(displayName, mimeType)
			?: throw IllegalArgumentException("Selected file is not a supported font.")
		val family = readerImportedFontFamilyFromDisplayName(displayName)
		val fontDirectory = fontDirectory()
		fontDirectory.mkdirs()
		val tempFile = File(fontDirectory, "import-${System.nanoTime()}.$extension.part")
		val digest = MessageDigest.getInstance("SHA-256")
		var byteSize = 0L

		try {
			input.use { source ->
				tempFile.outputStream().use { output ->
					val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
					while (true) {
						val read = source.read(buffer)
						if (read < 0) break
						byteSize += read
						if (byteSize > maxBytes) {
							throw IllegalArgumentException("Selected font is too large.")
						}
						digest.update(buffer, 0, read)
						output.write(buffer, 0, read)
					}
				}
			}
			if (byteSize <= 0L) {
				throw IllegalArgumentException("Selected font is empty.")
			}
			val cacheName = "imported-${digest.digest().toHexString().take(24)}.$extension"
			val targetFile = File(fontDirectory, cacheName)
			if (targetFile.isFile && targetFile.length() > 0L) {
				tempFile.delete()
			} else if (!tempFile.renameTo(targetFile)) {
				tempFile.copyTo(targetFile, overwrite = true)
				tempFile.delete()
			}
			return ReaderImportedFont(
				family = family,
				url = readerPublicationAssetUrl("$ReaderImportedFontDirectoryName/${targetFile.name}"),
				byteSize = targetFile.length()
			)
		} catch (error: Throwable) {
			tempFile.delete()
			throw error
		}
	}

	private fun fontDirectory(): File =
		File(cacheRoot, ReaderImportedFontDirectoryName)
}

private fun ByteArray.toHexString(): String =
	joinToString(separator = "") { byte -> "%02x".format(byte) }
