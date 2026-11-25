package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import paige.navic.util.core.Logger

const val ReaderIllustrationIndexSchema = "bindery.illustrations.index.v1"
const val ReaderIllustrationPositionPlateBeforeChapter = "plate-before-chapter"
/** A full-page plate before the block (paragraph, heading) containing `ebookStart`, mid-chapter. */
const val ReaderIllustrationPositionPlateBeforeBlock = "plate-before-block"

private const val ReaderIllustrationIndexTag = "ReaderIllustrationIndex"
private const val ReaderIllustrationExtractor = "bindery-epub-text"
private const val ReaderIllustrationExtractorVersion = "1"
private val ReaderIllustrationSha256 = Regex("^[0-9a-f]{64}$")
private val ReaderIllustrationJson = Json { ignoreUnknownKeys = true }

internal fun String.isReaderIllustrationSha256(): Boolean = ReaderIllustrationSha256.matches(this)

/** `GET /api/v1/books/{id}/illustrations`: the book's status plus one entry per ebook file. */
data class BinderyIllustrationReadiness(
	val bookId: Long,
	val status: String?,
	val files: List<BinderyIllustrationFileReadiness>
) {
	val readyFiles: List<BinderyIllustrationFileReadiness>
		get() = files.filter(BinderyIllustrationFileReadiness::isReady)
}

data class BinderyIllustrationFileReadiness(
	val ebookBookFileId: Long,
	val status: String?,
	val indexHref: String?,
	val digest: String?
) {
	val isReady: Boolean
		get() = status?.trim()?.lowercase() in setOf("ready", "partial")
}

/** One validated wire placement of bindery.illustrations.index.v1 (image not yet resolved to disk). */
data class ReaderIllustrationIndexPlacement(
	val sceneId: String,
	val spineIndex: Int,
	val ebookHref: String,
	/** [ReaderIllustrationPositionPlateBeforeChapter] or [ReaderIllustrationPositionPlateBeforeBlock]. */
	val position: String,
	val ebookStart: Int,
	val width: Int,
	val height: Int,
	val imageHref: String?,
	val sha256: String,
	/** Optional living plate (Darkaxt/Navic#77); null shows the static plate only. */
	val living: ReaderIllustrationLiving? = null
)

/**
 * A placement's `living` object: an 8 s seamless H.264 loop of the plate and a greyscale reveal-order
 * map (0 = gets its colour first), both served by sha256 from `illustrations/living/`.
 */
data class ReaderIllustrationLiving(
	val loopSha256: String,
	val revealSha256: String,
	val seconds: Double,
	val loopBytes: Long?,
	val revealBytes: Long?
)

/** Per-spine proof, same shape as the Whispersync index, kept for a later drift check. */
data class ReaderIllustrationSpineProof(
	val spineIndex: Int?,
	val href: String?,
	val sourceHash: String?,
	val extractedTextHash: String?
)

data class ReaderIllustrationIndex(
	val bookId: Long,
	val ebookBookFileId: Long,
	val digest: String,
	val placements: List<ReaderIllustrationIndexPlacement>,
	val spineProofs: List<ReaderIllustrationSpineProof>,
	/** Placements skipped because their position kind, offset or fields are not supported. */
	val rejectedPlacementCount: Int
)

/**
 * Lenient readiness decoder: accepts `files` or `illustrations` arrays whose entries carry
 * `bookFileId`/`ebookBookFileId` and either flat `{status, indexHref, digest}` or a nested
 * `illustrations` object with those fields.
 */
fun decodeBinderyIllustrationReadiness(json: String, expectedBookId: Long): BinderyIllustrationReadiness {
	val root = ReaderIllustrationJson.parseToJsonElement(json).jsonObject
	root.long("bookId")?.let { require(it == expectedBookId) { "Bindery illustrations readiness is for another book." } }
	val entries = (root["files"] as? JsonArray) ?: (root["illustrations"] as? JsonArray) ?: JsonArray(emptyList())
	val files = entries.mapNotNull { element ->
		val file = element as? JsonObject ?: return@mapNotNull null
		val fileId = (file.long("ebookBookFileId") ?: file.long("bookFileId") ?: file.long("fileId"))
			?.takeIf { it > 0L } ?: return@mapNotNull null
		val detail = file["illustrations"] as? JsonObject ?: file
		BinderyIllustrationFileReadiness(
			ebookBookFileId = fileId,
			status = detail.string("status"),
			indexHref = detail.string("indexHref"),
			digest = detail.string("digest")
		)
	}
	return BinderyIllustrationReadiness(
		bookId = expectedBookId,
		status = root.string("illustrationsStatus") ?: root.string("status"),
		files = files
	)
}

/**
 * Decodes and validates bindery.illustrations.index.v1. Unknown schema, another book or file, or an
 * unsupported coordinate basis fail the whole index. A placement that is neither `plate-before-chapter`
 * at `ebookStart` 0 nor `plate-before-block` at a non-negative `ebookStart`, or that has invalid
 * fields, is logged and skipped so it is never misplaced.
 * [expectedEbookBookFileId] null accepts the index's own file (used for local fixtures).
 */
fun decodeReaderIllustrationIndex(
	json: String,
	expectedBookId: Long,
	expectedEbookBookFileId: Long?
): ReaderIllustrationIndex {
	val root = ReaderIllustrationJson.parseToJsonElement(json).jsonObject
	require(root.string("schema") == ReaderIllustrationIndexSchema) { "Unsupported illustrations index schema." }
	require(root.long("bookId") == expectedBookId) { "Illustrations index is for another book." }
	val fileId = root.long("ebookBookFileId")?.takeIf { it > 0L }
		?: throw IllegalArgumentException("Illustrations index ebook file is required.")
	expectedEbookBookFileId?.let { require(fileId == it) { "Illustrations index is for another ebook file." } }
	(root["coordinateBasis"] as? JsonObject)?.let { basis ->
		require(basis.string("extractor") == ReaderIllustrationExtractor) { "Unsupported illustrations extractor." }
		require(basis.string("extractorVersion") == ReaderIllustrationExtractorVersion) {
			"Unsupported illustrations extractor version."
		}
	}
	val digest = root.string("digest")?.takeIf { it.isNotBlank() }
		?: throw IllegalArgumentException("Illustrations index digest is required.")
	val wirePlacements = (root["placements"] as? JsonArray)
		?: throw IllegalArgumentException("Illustrations index placements are required.")
	val placements = wirePlacements.mapIndexedNotNull { index, element ->
		val placement = (element as? JsonObject)?.decodePlacementOrNull()
		if (placement == null) {
			Logger.w(ReaderIllustrationIndexTag, "Skipped illustration placement index=$index book=$expectedBookId")
		}
		placement
	}
	val proofs = ((root["spines"] as? JsonArray) ?: (root["spineProofs"] as? JsonArray)).orEmpty()
		.mapNotNull { element ->
			val proof = element as? JsonObject ?: return@mapNotNull null
			ReaderIllustrationSpineProof(
				spineIndex = proof.long("spineIndex")?.toInt(),
				href = proof.string("href") ?: proof.string("ebookHref"),
				sourceHash = proof.string("sourceHash"),
				extractedTextHash = proof.string("extractedTextHash")
			)
		}
	return ReaderIllustrationIndex(
		bookId = expectedBookId,
		ebookBookFileId = fileId,
		digest = digest,
		placements = placements,
		spineProofs = proofs,
		rejectedPlacementCount = wirePlacements.size - placements.size
	)
}

private fun JsonObject.decodePlacementOrNull(): ReaderIllustrationIndexPlacement? {
	val position = string("position")
	val ebookStart = long("ebookStart") ?: return null
	when (position) {
		ReaderIllustrationPositionPlateBeforeChapter -> if (ebookStart != 0L) return null
		ReaderIllustrationPositionPlateBeforeBlock -> if (ebookStart !in 0L..Int.MAX_VALUE.toLong()) return null
		else -> return null
	}
	val sha256 = string("sha256")?.takeIf(String::isReaderIllustrationSha256) ?: return null
	val spineIndex = long("spineIndex")?.takeIf { it in 0L..Int.MAX_VALUE.toLong() } ?: return null
	val width = long("width")?.takeIf { it in 1L..Int.MAX_VALUE.toLong() } ?: return null
	val height = long("height")?.takeIf { it in 1L..Int.MAX_VALUE.toLong() } ?: return null
	return ReaderIllustrationIndexPlacement(
		sceneId = string("sceneId")?.takeIf { it.isNotBlank() } ?: return null,
		spineIndex = spineIndex.toInt(),
		ebookHref = string("ebookHref")?.takeIf { it.isNotBlank() } ?: return null,
		position = position,
		ebookStart = ebookStart.toInt(),
		width = width.toInt(),
		height = height.toInt(),
		imageHref = string("imageHref"),
		sha256 = sha256,
		living = (this["living"] as? JsonObject)?.decodeLivingOrNull()
	)
}

private fun JsonObject.decodeLivingOrNull(): ReaderIllustrationLiving? {
	val loop = string("loopSha256")?.takeIf(String::isReaderIllustrationSha256) ?: return null
	val reveal = string("revealSha256")?.takeIf(String::isReaderIllustrationSha256) ?: return null
	val seconds = (this["seconds"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
		?.takeIf { it > 0.0 && it <= 60.0 } ?: 8.0
	return ReaderIllustrationLiving(
		loopSha256 = loop,
		revealSha256 = reveal,
		seconds = seconds,
		loopBytes = long("loopBytes")?.takeIf { it > 0L },
		revealBytes = long("revealBytes")?.takeIf { it > 0L }
	)
}

private fun JsonObject.string(key: String): String? =
	(this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.long(key: String): Long? {
	val primitive = this[key] as? JsonPrimitive ?: return null
	return primitive.longOrNull ?: primitive.contentOrNull?.trim()?.toLongOrNull()
}
