package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val ReaderImportedFontFallbackFamily = "Imported Font"
private val ReaderImportedFontAllowedExtensions = setOf("ttf", "otf", "woff", "woff2", "ttc")
const val ReaderRemoteFontBaseUrl = "https://fonts.anxcye.com/"
const val ReaderRemoteFontManifestUrl = "${ReaderRemoteFontBaseUrl}fonts-manifest.json"

data class ReaderImportedFont(
	val family: String,
	val url: String,
	val byteSize: Long
)

data class ReaderRemoteFontLicense(
	val name: String,
	val url: String
)

data class ReaderRemoteFontManifestEntry(
	val id: String,
	val name: String,
	val family: String,
	val files: List<String>,
	val size: Int,
	val preview: String,
	val description: String,
	val official: String,
	val license: ReaderRemoteFontLicense
)

data class ReaderCachedRemoteFont(
	val id: String,
	val name: String,
	val family: String,
	val fonts: List<ReaderImportedFont>,
	val byteSize: Long
)

const val ReaderRemoteFontDownloadStatusNone = "none"
const val ReaderRemoteFontDownloadStatusDownloading = "downloading"
const val ReaderRemoteFontDownloadStatusPaused = "paused"
const val ReaderRemoteFontDownloadStatusCompleted = "completed"
const val ReaderRemoteFontDownloadStatusFailed = "failed"

data class ReaderRemoteFontDownloadState(
	val fontId: String,
	val filePath: String,
	val status: String,
	val progress: Double = 0.0,
	val error: String? = null
)

fun readerImportedFontFamilyFromDisplayName(displayName: String?): String =
	normalizedReaderCustomFontFamily(
		displayName
			?.substringBeforeLast('.', missingDelimiterValue = displayName)
			.orEmpty()
	) ?: ReaderImportedFontFallbackFamily

fun parseReaderRemoteFontManifest(json: String): List<ReaderRemoteFontManifestEntry> {
	val root = Json.parseToJsonElement(json)
	val items = root as? JsonArray ?: error("Remote font manifest must be a JSON array.")
	return items.map { element ->
		val item = element.jsonObject
		val id = item.requiredString("id")
		val name = item.requiredString("name")
		ReaderRemoteFontManifestEntry(
			id = id,
			name = name,
			family = normalizedReaderCustomFontFamily(name)
				?: normalizedReaderCustomFontFamily(id)
				?: ReaderImportedFontFallbackFamily,
			files = item.requiredStringList("files"),
			size = item.optionalInt("size") ?: 0,
			preview = item.optionalString("preview").orEmpty(),
			description = item.optionalString("desc")
				?: item.optionalString("description")
				?: "",
			official = item.optionalString("official").orEmpty(),
			license = item.optionalObject("license")?.let { license ->
				ReaderRemoteFontLicense(
					name = license.optionalString("name").orEmpty(),
					url = license.optionalString("url").orEmpty()
				)
			} ?: ReaderRemoteFontLicense(name = "", url = "")
		)
	}
}

fun readerImportedFontExtension(displayName: String?, mimeType: String?): String? {
	val extension = displayName
		?.substringBefore('?')
		?.substringBefore('#')
		?.substringAfterLast('.', missingDelimiterValue = "")
		?.lowercase()
		?.takeIf { it in ReaderImportedFontAllowedExtensions }
	if (extension != null) return extension

	return when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
		"font/ttf",
		"application/x-font-ttf",
		"application/font-sfnt" -> "ttf"
		"font/otf",
		"application/x-font-otf",
		"application/vnd.ms-opentype" -> "otf"
		"font/woff",
		"application/font-woff",
		"application/x-font-woff" -> "woff"
		"font/woff2",
		"application/font-woff2",
		"application/x-font-woff2" -> "woff2"
		"font/collection" -> "ttc"
		else -> null
	}
}

private fun JsonObject.requiredString(key: String): String =
	optionalString(key)?.takeIf { it.isNotBlank() }
		?: error("Remote font manifest entry is missing '$key'.")

private fun JsonObject.optionalString(key: String): String? =
	get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.optionalInt(key: String): Int? =
	get(key)?.jsonPrimitive?.intOrNull

private fun JsonObject.optionalObject(key: String): JsonObject? =
	get(key)?.jsonObject

private fun JsonObject.requiredStringList(key: String): List<String> =
	get(key)
		?.jsonArray
		?.mapNotNull { element -> element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() } }
		?.takeIf { it.isNotEmpty() }
		?: error("Remote font manifest entry is missing non-empty '$key'.")
