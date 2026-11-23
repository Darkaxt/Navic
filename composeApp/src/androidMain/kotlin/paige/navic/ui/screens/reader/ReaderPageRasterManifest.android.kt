package paige.navic.ui.screens.reader

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.util.core.Logger

// 7: rasters carry their page-number labels, so the total page count is part of the key (#31).
internal const val ReaderPageRasterSchemaVersion = 8
internal const val ReaderPageRasterManifestFileName = "manifest.json"

internal data class ReaderPageRasterProfile(
	val publicationHash: String,
	val paginationHash: String,
	val layoutHash: String,
	val decorationHash: String,
	val quality: ReaderPageBitmapQuality,
	val schemaVersion: Int
)

/**
 * Whether two profiles belong to the same live raster set. Layout and decoration fingerprints are
 * observed per spine section, so they distinguish rasters but must not retire or reject another
 * section's rasters of the same publication, pagination and quality (curl deck needs several sections).
 */
internal fun ReaderPageRasterProfile.sameRetentionScope(other: ReaderPageRasterProfile): Boolean =
	publicationHash == other.publicationHash &&
		paginationHash == other.paginationHash &&
		quality == other.quality &&
		schemaVersion == other.schemaVersion

internal data class ReaderPageRasterChapterKey(
	val profile: ReaderPageRasterProfile,
	val spineIndex: Int,
	val hrefHash: String
)

internal data class ReaderPageRasterKey(
	val publicationHash: String,
	val paginationHash: String,
	val spineIndex: Int,
	val hrefHash: String,
	val chapterPageIndex: Int,
	val visualPageOrdinal: Int,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val layoutHash: String,
	val decorationHash: String,
	val quality: ReaderPageBitmapQuality,
	val pageCount: Int = 0,
	val schemaVersion: Int = ReaderPageRasterSchemaVersion
) {
	val profile: ReaderPageRasterProfile
		get() = ReaderPageRasterProfile(
			publicationHash = publicationHash,
			paginationHash = paginationHash,
			layoutHash = layoutHash,
			decorationHash = decorationHash,
			quality = quality,
			schemaVersion = schemaVersion
		)

	val chapter: ReaderPageRasterChapterKey
		get() = ReaderPageRasterChapterKey(
			profile = profile,
			spineIndex = spineIndex,
			hrefHash = hrefHash
		)

	// Computed once per key: the manifest sorts and the cache looks up entries by digest, and
	// recomputing it per comparison cost ~5 s per page write at 1.3k entries.
	val identity: String by lazy(LazyThreadSafetyMode.PUBLICATION) { readerPageRasterKeyIdentity(this) }

	val digest: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
		MessageDigest.getInstance("SHA-256")
			.digest(identity.encodeToByteArray())
			.toLowerHex()
	}
}

private val LowerHexDigits = "0123456789abcdef".toCharArray()

private fun ByteArray.toLowerHex(): String {
	val chars = CharArray(size * 2)
	forEachIndexed { index, byte ->
		val value = byte.toInt() and 0xff
		chars[index * 2] = LowerHexDigits[value ushr 4]
		chars[index * 2 + 1] = LowerHexDigits[value and 0x0f]
	}
	return String(chars)
}

internal data class ReaderPageRasterRect(
	val left: Int,
	val top: Int,
	val right: Int,
	val bottom: Int
)

internal data class ReaderPageRasterMetadata(
	val surfaceLeft: Int,
	val surfaceTop: Int,
	val surfaceRight: Int,
	val surfaceBottom: Int,
	val fullLeafRect: ReaderPageRasterRect?,
	val leftLeafRect: ReaderPageRasterRect?,
	val gutterRect: ReaderPageRasterRect?,
	val rightLeafRect: ReaderPageRasterRect?,
	val reverseFaceColor: Int
)

internal data class ReaderPageRasterManifestEntry(
	val key: ReaderPageRasterKey,
	val rasterFileName: String,
	val byteSize: Long,
	val lastAccessEpochMillis: Long,
	val metadata: ReaderPageRasterMetadata,
	/** Planned (pre-render) descriptor digest whose receipt-verified capture produced this raster. */
	val issuedDigest: String? = null,
	/** App install token the alias was recorded under; a different install never reuses it. */
	val aliasToken: String? = null
)

internal const val ReaderPageRasterManifestSlowWriteNanos = 50_000_000L

internal class ReaderPageRasterManifest(
	private val root: File
) {
	val file: File
		get() = root.resolve(ReaderPageRasterManifestFileName)

	fun read(): List<ReaderPageRasterManifestEntry> {
		if (!file.isFile || Files.isSymbolicLink(file.toPath())) return emptyList()
		return runCatching {
			val json = Json.parseToJsonElement(file.readText()).jsonObject
			if (json.int("schemaVersion") != ReaderPageRasterSchemaVersion) return@runCatching emptyList()
			json["entries"]?.jsonArray.orEmpty().mapNotNull { element ->
				runCatching { element.jsonObject.toManifestEntry() }.getOrNull()
			}
		}.getOrElse {
			file.delete()
			emptyList()
		}
	}

	fun write(entries: Collection<ReaderPageRasterManifestEntry>): Boolean {
		val startedAt = System.nanoTime()
		val payload = buildJsonObject {
			put("schemaVersion", ReaderPageRasterSchemaVersion)
			put("entries", buildJsonArray {
				entries.sortedBy { entry -> entry.key.digest }.forEach { entry -> add(entry.toJson()) }
			})
		}.toString().encodeToByteArray()
		val serializedAt = System.nanoTime()
		val written = readerPageRasterAtomicWrite(file) { output -> output.write(payload) }
		val finishedAt = System.nanoTime()
		if (finishedAt - startedAt >= ReaderPageRasterManifestSlowWriteNanos) {
			Logger.i(
				"ReaderPageRasterCache",
				"Manifest write entries=${entries.size} bytes=${payload.size} " +
					"serializeMs=${(serializedAt - startedAt) / 1_000_000L} " +
					"writeSyncMs=${(finishedAt - serializedAt) / 1_000_000L}"
			)
		}
		return written
	}
}

internal fun readerPageRasterAtomicWrite(
	target: File,
	write: (FileOutputStream) -> Unit
): Boolean {
	val parent = target.parentFile ?: return false
	parent.mkdirs()
	if (Files.isSymbolicLink(parent.toPath())) return false
	val temporary = parent.resolve("${target.name}.${System.nanoTime()}.tmp")
	return try {
		FileOutputStream(temporary).use { output ->
			write(output)
			output.fd.sync()
		}
		readerPageRasterPromote(temporary, target)
		true
	} catch (_: Throwable) {
		temporary.delete()
		false
	}
}

internal fun readerPageRasterPromote(source: File, target: File) {
	try {
		Files.move(
			source.toPath(),
			target.toPath(),
			StandardCopyOption.ATOMIC_MOVE,
			StandardCopyOption.REPLACE_EXISTING
		)
	} catch (_: AtomicMoveNotSupportedException) {
		Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
	}
}

/**
 * The key's identity is exactly its manifest JSON text. Opening the cache digests every stored
 * key, and the generic JSON encoder made that cost seconds per open at 1.3k entries, so plain
 * strings are written directly; anything that would need escaping takes the encoder.
 */
internal fun readerPageRasterKeyIdentity(key: ReaderPageRasterKey): String {
	val quality = key.quality.persistedValue
	val strings = arrayOf(
		key.publicationHash,
		key.paginationHash,
		key.hrefHash,
		key.layoutHash,
		key.decorationHash,
		quality
	)
	if (strings.any { value -> value.any { char -> char < ' ' || char == '"' || char == '\\' } }) {
		return key.toJson().toString()
	}
	return buildString(256) {
		append("{\"publicationHash\":\"").append(key.publicationHash)
		append("\",\"paginationHash\":\"").append(key.paginationHash)
		append("\",\"spineIndex\":").append(key.spineIndex)
		append(",\"hrefHash\":\"").append(key.hrefHash)
		append("\",\"chapterPageIndex\":").append(key.chapterPageIndex)
		append(",\"visualPageOrdinal\":").append(key.visualPageOrdinal)
		append(",\"viewportWidth\":").append(key.viewportWidth)
		append(",\"viewportHeight\":").append(key.viewportHeight)
		append(",\"layoutHash\":\"").append(key.layoutHash)
		append("\",\"decorationHash\":\"").append(key.decorationHash)
		append("\",\"quality\":\"").append(quality)
		append("\",\"pageCount\":").append(key.pageCount)
		append(",\"schemaVersion\":").append(key.schemaVersion)
		append('}')
	}
}

internal fun ReaderPageRasterKey.encodedIdentityForTest(): String = toJson().toString()

private fun ReaderPageRasterKey.toJson(): JsonObject = buildJsonObject {
	put("publicationHash", publicationHash)
	put("paginationHash", paginationHash)
	put("spineIndex", spineIndex)
	put("hrefHash", hrefHash)
	put("chapterPageIndex", chapterPageIndex)
	put("visualPageOrdinal", visualPageOrdinal)
	put("viewportWidth", viewportWidth)
	put("viewportHeight", viewportHeight)
	put("layoutHash", layoutHash)
	put("decorationHash", decorationHash)
	put("quality", quality.persistedValue)
	put("pageCount", pageCount)
	put("schemaVersion", schemaVersion)
}

private fun ReaderPageRasterManifestEntry.toJson(): JsonObject = buildJsonObject {
	put("key", key.toJson())
	put("rasterFileName", rasterFileName)
	put("byteSize", byteSize)
	put("lastAccessEpochMillis", lastAccessEpochMillis)
	put("metadata", metadata.toJson())
	issuedDigest?.let { digest -> put("issuedDigest", digest) }
	aliasToken?.let { token -> put("aliasToken", token) }
}

private fun ReaderPageRasterMetadata.toJson(): JsonObject = buildJsonObject {
	put("surfaceLeft", surfaceLeft)
	put("surfaceTop", surfaceTop)
	put("surfaceRight", surfaceRight)
	put("surfaceBottom", surfaceBottom)
	put("fullLeafRect", fullLeafRect?.toJson() ?: JsonNull)
	put("leftLeafRect", leftLeafRect?.toJson() ?: JsonNull)
	put("gutterRect", gutterRect?.toJson() ?: JsonNull)
	put("rightLeafRect", rightLeafRect?.toJson() ?: JsonNull)
	put("reverseFaceColor", reverseFaceColor)
}

private fun ReaderPageRasterRect.toJson(): JsonObject = buildJsonObject {
	put("left", left)
	put("top", top)
	put("right", right)
	put("bottom", bottom)
}

private fun JsonObject.toManifestEntry(): ReaderPageRasterManifestEntry {
	val keyJson = getValue("key").jsonObject
	return ReaderPageRasterManifestEntry(
		key = ReaderPageRasterKey(
			publicationHash = keyJson.string("publicationHash"),
			paginationHash = keyJson.string("paginationHash"),
			spineIndex = keyJson.int("spineIndex"),
			hrefHash = keyJson.string("hrefHash"),
			chapterPageIndex = keyJson.int("chapterPageIndex"),
			visualPageOrdinal = keyJson.int("visualPageOrdinal"),
			viewportWidth = keyJson.int("viewportWidth"),
			viewportHeight = keyJson.int("viewportHeight"),
			layoutHash = keyJson.string("layoutHash"),
			decorationHash = keyJson.string("decorationHash"),
			quality = normalizeReaderPageBitmapQuality(keyJson.string("quality")),
			pageCount = keyJson.int("pageCount"),
			schemaVersion = keyJson.int("schemaVersion")
		),
		rasterFileName = string("rasterFileName"),
		byteSize = long("byteSize"),
		lastAccessEpochMillis = long("lastAccessEpochMillis"),
		metadata = getValue("metadata").jsonObject.toRasterMetadata(),
		issuedDigest = get("issuedDigest")?.jsonPrimitive?.contentOrNull,
		aliasToken = get("aliasToken")?.jsonPrimitive?.contentOrNull
	)
}

private fun JsonObject.toRasterMetadata(): ReaderPageRasterMetadata = ReaderPageRasterMetadata(
	surfaceLeft = int("surfaceLeft"),
	surfaceTop = int("surfaceTop"),
	surfaceRight = int("surfaceRight"),
	surfaceBottom = int("surfaceBottom"),
	fullLeafRect = optionalRect("fullLeafRect"),
	leftLeafRect = optionalRect("leftLeafRect"),
	gutterRect = optionalRect("gutterRect"),
	rightLeafRect = optionalRect("rightLeafRect"),
	reverseFaceColor = int("reverseFaceColor")
)

private fun JsonObject.optionalRect(name: String): ReaderPageRasterRect? =
	get(name)?.takeUnless { element -> element.toString() == "null" }?.jsonObject?.let { json ->
		ReaderPageRasterRect(json.int("left"), json.int("top"), json.int("right"), json.int("bottom"))
	}

private fun JsonObject.string(name: String): String =
	getValue(name).jsonPrimitive.contentOrNull ?: error("Missing string '$name'")

private fun JsonObject.int(name: String): Int =
	getValue(name).jsonPrimitive.intOrNull ?: error("Missing integer '$name'")

private fun JsonObject.long(name: String): Long =
	getValue(name).jsonPrimitive.longOrNull ?: error("Missing long '$name'")
