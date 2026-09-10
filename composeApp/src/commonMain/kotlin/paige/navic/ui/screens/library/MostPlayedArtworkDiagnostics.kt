package paige.navic.ui.screens.library

internal const val MOST_PLAYED_ARTWORK_TAG = "MostPlayedArtwork"

internal fun mostPlayedDiagnosticUrlSummary(value: String?): String {
	val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "none"
	return when {
		trimmed.startsWith("http://", ignoreCase = true) ||
			trimmed.startsWith("https://", ignoreCase = true) -> "absolute-url"
		trimmed.startsWith("/") -> "relative-path"
		else -> "opaque-resource"
	}
}

internal fun mostPlayedDiagnosticHeaderSummary(headers: Map<String, String>): String =
	"count=${headers.keys.count { it.isNotBlank() }}"

internal fun mostPlayedDiagnosticText(value: String?): String =
	if (value.isNullOrBlank()) "none" else "present"
