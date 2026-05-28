package paige.navic.domain.models

fun activeArtworkUrl(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): String? = externalArtworkUrl.nonBlankOrNull() ?: serverArtworkUrl.nonBlankOrNull()

fun dominantColorArtworkUrl(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): String? =
	externalArtworkUrl.nonBlankOrNull()
		?: serverArtworkUrl.nonBlankOrNull()?.withQueryParameter("size", "128")

fun shouldSendServerArtworkHeaders(externalArtworkUrl: String?): Boolean =
	externalArtworkUrl.isNullOrBlank()

private fun String?.nonBlankOrNull(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.withQueryParameter(key: String, value: String): String {
	val separator = when {
		contains("?") -> if (endsWith("?") || endsWith("&")) "" else "&"
		else -> "?"
	}
	return "$this$separator$key=$value"
}
