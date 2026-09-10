package paige.navic.reader

fun readerPublicationResourceLogLabel(value: String): String {
	val trimmed = value.trim()
	if (trimmed.isBlank()) return "<blank>"
	return when {
		trimmed.startsWith("http://", ignoreCase = true) ||
			trimmed.startsWith("https://", ignoreCase = true) -> "absolute-url"
		trimmed.startsWith("/") -> "relative-path"
		else -> "opaque-resource"
	}
}
