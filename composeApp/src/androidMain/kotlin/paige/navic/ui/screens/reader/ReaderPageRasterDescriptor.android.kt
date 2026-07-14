package paige.navic.ui.screens.reader

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.reader.ReaderPageBitmapQuality

internal data class ReaderPageRasterDescriptor(
	val publicationUrl: String,
	val paginationFingerprint: String,
	val layoutFingerprint: String,
	val decorationFingerprint: String,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val pageCount: Int,
	val spineIndex: Int,
	val href: String,
	val chapterPageIndex: Int,
	val chapterPageCount: Int,
	val visualPageOrdinal: Int
) {
	fun key(quality: ReaderPageBitmapQuality): ReaderPageRasterKey = ReaderPageRasterKey(
		publicationHash = publicationUrl.readerPageRasterHash(),
		paginationHash = paginationFingerprint,
		spineIndex = spineIndex,
		hrefHash = href.readerPageRasterHash(),
		chapterPageIndex = chapterPageIndex,
		visualPageOrdinal = visualPageOrdinal,
		viewportWidth = viewportWidth,
		viewportHeight = viewportHeight,
		layoutHash = layoutFingerprint,
		decorationHash = decorationFingerprint,
		quality = quality
	)
}

internal fun readerPageRasterDescriptor(encoded: String?): ReaderPageRasterDescriptor? =
	runCatching { readerPageRasterDescriptorOrThrow(encoded) }.getOrNull()

internal fun readerPageRasterDescriptorOrThrow(encoded: String?): ReaderPageRasterDescriptor {
	val raw = encoded.orEmpty().trim()
	val firstPass = Json.parseToJsonElement(raw)
	val json = if (raw.startsWith('"')) {
		Json.parseToJsonElement(firstPass.jsonPrimitive.contentOrNull ?: error("Raster descriptor is not a string"))
			.jsonObject
	} else firstPass.jsonObject
	return ReaderPageRasterDescriptor(
		publicationUrl = json.requiredString("publicationUrl"),
		paginationFingerprint = json.requiredString("paginationFingerprint"),
		layoutFingerprint = json.requiredString("layoutFingerprint"),
		decorationFingerprint = json.requiredString("decorationFingerprint"),
		viewportWidth = json.requiredPositiveInt("viewportWidth"),
		viewportHeight = json.requiredPositiveInt("viewportHeight"),
		pageCount = json.requiredPositiveInt("pageCount"),
		spineIndex = json.requiredNonNegativeInt("spineIndex"),
		href = json.requiredString("href"),
		chapterPageIndex = json.requiredNonNegativeInt("chapterPageIndex"),
		chapterPageCount = json.requiredPositiveInt("chapterPageCount"),
		visualPageOrdinal = json.requiredNonNegativeInt("visualPageOrdinal")
	)
}

private fun JsonObject.requiredString(name: String): String =
	getValue(name).jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) ?: error("$name is blank")

private fun JsonObject.requiredPositiveInt(name: String): Int =
	getValue(name).jsonPrimitive.intOrNull?.takeIf { value -> value > 0 } ?: error("$name must be positive")

private fun JsonObject.requiredNonNegativeInt(name: String): Int =
	getValue(name).jsonPrimitive.intOrNull?.takeIf { value -> value >= 0 } ?: error("$name must be non-negative")

private fun String.readerPageRasterHash(): String = MessageDigest.getInstance("SHA-256")
	.digest(encodeToByteArray())
	.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
