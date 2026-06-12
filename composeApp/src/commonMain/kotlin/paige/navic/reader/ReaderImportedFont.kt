package paige.navic.reader

private const val ReaderImportedFontFallbackFamily = "Imported Font"
private val ReaderImportedFontAllowedExtensions = setOf("ttf", "otf", "woff", "woff2", "ttc")

data class ReaderImportedFont(
	val family: String,
	val url: String,
	val byteSize: Long
)

fun readerImportedFontFamilyFromDisplayName(displayName: String?): String =
	normalizedReaderCustomFontFamily(
		displayName
			?.substringBeforeLast('.', missingDelimiterValue = displayName)
			.orEmpty()
	) ?: ReaderImportedFontFallbackFamily

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
