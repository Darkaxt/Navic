package paige.navic.domain.models

fun activeArtworkUrl(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): String? = serverArtworkUrl.nonBlankOrNull() ?: externalArtworkUrl.nonBlankOrNull()

fun dominantColorArtworkUrl(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): String? =
	serverArtworkUrl.nonBlankOrNull()?.withQueryParameter("size", "128")
		?: externalArtworkUrl.nonBlankOrNull()

fun externalFallbackArtworkUrl(
	serverCoverArtId: String?,
	externalArtworkUrl: String?
): String? =
	if (serverCoverArtId.isNullOrBlank()) {
		externalArtworkUrl.nonBlankOrNull()
	} else {
		null
	}

fun externalFallbackArtworkCacheKey(
	serverCoverArtId: String?,
	externalArtworkCacheKey: String?
): String? =
	if (serverCoverArtId.isNullOrBlank()) {
		externalArtworkCacheKey.nonBlankOrNull()
	} else {
		null
	}

fun shouldSendServerArtworkHeaders(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): Boolean =
	serverArtworkUrl.nonBlankOrNull() != null || externalArtworkUrl.isNullOrBlank()

private fun String?.nonBlankOrNull(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.withQueryParameter(key: String, value: String): String {
	val separator = when {
		contains("?") -> if (endsWith("?") || endsWith("&")) "" else "&"
		else -> "?"
	}
	return "$this$separator$key=$value"
}
