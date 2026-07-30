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

internal const val ReaderPageRasterSchemaVersion = 4
internal const val ReaderPageRasterManifestFileName = "manifest.json"

internal data class ReaderPageRasterProfile(
	val publicationHash: String,
	val paginationHash: String,
	val layoutHash: String,
	val decorationHash: String,
	val quality: ReaderPageBitmapQuality,
	val schemaVersion: Int
)

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

	val identity: String
		get() = toJson().toString()

	val digest: String
		get() = MessageDigest.getInstance("SHA-256")
			.digest(identity.encodeToByteArray())
			.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
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
	val metadata: ReaderPageRasterMetadata
)

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
		val payload = buildJsonObject {
			put("schemaVersion", ReaderPageRasterSchemaVersion)
			put("entries", buildJsonArray {
				entries.sortedBy { entry -> entry.key.digest }.forEach { entry -> add(entry.toJson()) }
			})
		}.toString().encodeToByteArray()
		return readerPageRasterAtomicWrite(file) { output -> output.write(payload) }
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
	put("schemaVersion", schemaVersion)
}

private fun ReaderPageRasterManifestEntry.toJson(): JsonObject = buildJsonObject {
	put("key", key.toJson())
	put("rasterFileName", rasterFileName)
	put("byteSize", byteSize)
	put("lastAccessEpochMillis", lastAccessEpochMillis)
	put("metadata", metadata.toJson())
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
			schemaVersion = keyJson.int("schemaVersion")
		),
		rasterFileName = string("rasterFileName"),
		byteSize = long("byteSize"),
		lastAccessEpochMillis = long("lastAccessEpochMillis"),
		metadata = getValue("metadata").jsonObject.toRasterMetadata()
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
