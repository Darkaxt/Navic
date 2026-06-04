package paige.navic.ui.screens.library

internal const val MOST_PLAYED_ARTWORK_TAG = "MostPlayedArtwork"

internal fun mostPlayedDiagnosticUrlSummary(value: String?): String {
	val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "none"
	val withoutFragment = trimmed.substringBefore('#')
	val hasQuery = '?' in withoutFragment
	val withoutQuery = withoutFragment.substringBefore('?')
	val prefix = when {
		withoutQuery.startsWith("http://", ignoreCase = true) ||
			withoutQuery.startsWith("https://", ignoreCase = true) -> ""
		withoutQuery.startsWith("/") -> "relative:"
		else -> "value:"
	}
	return prefix + withoutQuery.takeLastIfTooLong(maxLength = 140) + if (hasQuery) "?query" else ""
}

internal fun mostPlayedDiagnosticHeaderSummary(headers: Map<String, String>): String =
	headers.keys
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.sorted()
		.joinToString(",")
		.ifEmpty { "none" }

internal fun mostPlayedDiagnosticText(value: String?, maxLength: Int = 80): String =
	value
		?.trim()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }
		?.takeLastIfTooLong(maxLength)
		?: "none"

private fun String.takeLastIfTooLong(maxLength: Int): String =
	if (length <= maxLength) this else "...${takeLast(maxLength - 3)}"
