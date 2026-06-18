package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

private const val ReaderImportedFontDirectoryName = "fonts"
private const val ReaderRemoteFontDirectoryName = "remote"
private const val ReaderRemoteFontMetadataFileName = "remote-font.json"
private const val ReaderImportedFontMaxBytes = 64L * 1024L * 1024L

class ReaderImportedFontCache(
	private val cacheRoot: File,
	private val maxBytes: Long = ReaderImportedFontMaxBytes
) {
	fun cachedFontsByteSize(): Long =
		fontDirectory()
			.walkTopDown()
			.filter { file ->
				file.isFile &&
					!file.name.endsWith(".part") &&
					file.name != ReaderRemoteFontMetadataFileName
			}
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

	fun fetchRemoteFontManifest(
		manifestUrl: String = ReaderRemoteFontManifestUrl,
		fetchText: (String) -> String
	): List<ReaderRemoteFontManifestEntry> =
		parseReaderRemoteFontManifest(fetchText(manifestUrl))

	fun downloadRemoteFont(
		entry: ReaderRemoteFontManifestEntry,
		baseUrl: String = ReaderRemoteFontBaseUrl,
		fetchBytes: (String) -> ByteArray
	): ReaderCachedRemoteFont {
		val remoteId = safeRemoteFontId(entry.id)
		val rootDirectory = remoteFontRootDirectory()
		val remoteDirectory = remoteFontDirectory(remoteId)
		val tempDirectory = File(rootDirectory, "$remoteId.part")
		val normalizedBaseUrl = baseUrl.trimEnd('/') + "/"
		val downloadedFonts = mutableListOf<ReaderImportedFont>()
		var byteSize = 0L

		tempDirectory.deleteRecursively()
		tempDirectory.mkdirs()

		try {
			entry.files.forEachIndexed { index, filePath ->
				val sourceFileName = filePath.substringAfterLast('/')
				val extension = readerImportedFontExtension(sourceFileName, null)
					?: throw IllegalArgumentException("Remote font '$sourceFileName' is not a supported font.")
				val fileName = "${index}-${safeRemoteFontFileName(sourceFileName)}.$extension"
				val bytes = fetchBytes(normalizedBaseUrl + filePath.trimStart('/'))
				if (bytes.isEmpty()) {
					throw IllegalArgumentException("Remote font '$sourceFileName' is empty.")
				}
				byteSize += bytes.size
				if (byteSize > maxBytes) {
					throw IllegalArgumentException("Remote font package '${entry.name}' is too large.")
				}
				File(tempDirectory, fileName).writeBytes(bytes)
				downloadedFonts += ReaderImportedFont(
					family = entry.family,
					url = remoteFontUrl(remoteId, fileName),
					byteSize = bytes.size.toLong()
				)
			}

			remoteDirectory.deleteRecursively()
			if (!tempDirectory.renameTo(remoteDirectory)) {
				tempDirectory.copyRecursively(remoteDirectory, overwrite = true)
				tempDirectory.deleteRecursively()
			}

			val cached = ReaderCachedRemoteFont(
				id = entry.id,
				name = entry.name,
				family = entry.family,
				fonts = downloadedFonts,
				byteSize = byteSize
			)
			writeRemoteFontMetadata(remoteDirectory, cached)
			return cached
		} catch (error: Throwable) {
			tempDirectory.deleteRecursively()
			throw error
		}
	}

	fun listRemoteFonts(): List<ReaderCachedRemoteFont> =
		remoteFontRootDirectory()
			.listFiles()
			.orEmpty()
			.filter { directory -> directory.isDirectory && !directory.name.endsWith(".part") }
			.mapNotNull { directory -> readRemoteFontMetadata(directory) }
			.sortedBy { remoteFont -> remoteFont.name.lowercase() }

	fun deleteRemoteFont(id: String): Int {
		val directory = remoteFontDirectory(safeRemoteFontId(id))
		return if (directory.exists() && directory.deleteRecursively()) 1 else 0
	}

	private fun fontDirectory(): File =
		File(cacheRoot, ReaderImportedFontDirectoryName)

	private fun remoteFontRootDirectory(): File =
		File(fontDirectory(), ReaderRemoteFontDirectoryName)

	private fun remoteFontDirectory(id: String): File =
		File(remoteFontRootDirectory(), id)

	private fun writeRemoteFontMetadata(directory: File, cached: ReaderCachedRemoteFont) {
		directory.mkdirs()
		val metadata = buildJsonObject {
			put("id", cached.id)
			put("name", cached.name)
			put("family", cached.family)
			put("byteSize", cached.byteSize)
			put(
				"fonts",
				buildJsonArray {
					cached.fonts.forEach { font ->
						add(
							buildJsonObject {
								put("family", font.family)
								put("url", font.url)
								put("byteSize", font.byteSize)
							}
						)
					}
				}
			)
		}
		File(directory, ReaderRemoteFontMetadataFileName).writeText(metadata.toString())
	}

	private fun readRemoteFontMetadata(directory: File): ReaderCachedRemoteFont? =
		runCatching {
			val metadata = Json.parseToJsonElement(File(directory, ReaderRemoteFontMetadataFileName).readText()).jsonObject
			val fonts = metadata["fonts"]
				?.jsonArray
				?.mapNotNull { fontElement ->
					val font = fontElement.jsonObject
					val family = font.stringValue("family") ?: return@mapNotNull null
					val url = font.stringValue("url") ?: return@mapNotNull null
					ReaderImportedFont(
						family = family,
						url = url,
						byteSize = font.longValue("byteSize") ?: 0L
					)
				}
				.orEmpty()
			if (fonts.isEmpty()) return@runCatching null
			ReaderCachedRemoteFont(
				id = metadata.stringValue("id") ?: return@runCatching null,
				name = metadata.stringValue("name") ?: return@runCatching null,
				family = metadata.stringValue("family") ?: return@runCatching null,
				fonts = fonts,
				byteSize = metadata.longValue("byteSize") ?: fonts.sumOf { font -> font.byteSize }
			)
		}.getOrNull()
}

private fun remoteFontUrl(remoteId: String, fileName: String): String =
	readerPublicationAssetUrl("$ReaderImportedFontDirectoryName/$ReaderRemoteFontDirectoryName/$remoteId/$fileName")

private fun safeRemoteFontId(id: String): String =
	id.trim()
		.lowercase()
		.replace(Regex("[^a-z0-9._-]+"), "-")
		.trim('-')
		.ifBlank { "remote-font" }

private fun safeRemoteFontFileName(fileName: String): String =
	fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
		.trim()
		.replace(Regex("[^A-Za-z0-9._-]+"), "-")
		.trim('-', '.', '_')
		.ifBlank { "font" }

private fun JsonObject.stringValue(key: String): String? =
	get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.longValue(key: String): Long? =
	get(key)?.jsonPrimitive?.longOrNull

private fun ByteArray.toHexString(): String =
	joinToString(separator = "") { byte -> "%02x".format(byte) }
