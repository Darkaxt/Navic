package paige.navic.domain.models

import paige.navic.domain.models.settings.ArtworkSourcePriority

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

@Suppress("UNUSED_PARAMETER")
fun externalFallbackArtworkUrl(
	serverCoverArtId: String?,
	externalArtworkUrl: String?,
	serverCoverLoadFailed: Boolean = false
): String? =
	externalArtworkUrl.nonBlankOrNull()

@Suppress("UNUSED_PARAMETER")
fun externalFallbackArtworkCacheKey(
	serverCoverArtId: String?,
	externalArtworkCacheKey: String?,
	serverCoverLoadFailed: Boolean = false
): String? =
	externalArtworkCacheKey.nonBlankOrNull()

fun shouldSendServerArtworkHeaders(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): Boolean =
	externalArtworkUrl.nonBlankOrNull() == null &&
		(serverArtworkUrl.nonBlankOrNull() != null || externalArtworkUrl.isNullOrBlank())

fun effectiveAurralArtworkPriority(
	aurralEnabled: Boolean,
	configuredPriority: ArtworkSourcePriority
): ArtworkSourcePriority =
	if (aurralEnabled) {
		ArtworkSourcePriority.AurralFirst
	} else {
		configuredPriority
	}

@Suppress("UNUSED_PARAMETER")
fun visiblePlaybackCoverArtId(
	serverCoverArtId: String?,
	externalArtworkUrl: String?,
	priority: ArtworkSourcePriority
): String? =
	when (priority) {
		ArtworkSourcePriority.AurralFirst -> null
		ArtworkSourcePriority.NativeFirst,
		ArtworkSourcePriority.NativeOnly -> serverCoverArtId.nonBlankOrNull()
	}

fun visiblePlaybackImageUrl(
	serverCoverArtId: String?,
	externalArtworkUrl: String?,
	priority: ArtworkSourcePriority
): String? {
	val external = externalArtworkUrl.nonBlankOrNull()
	return when (priority) {
		ArtworkSourcePriority.AurralFirst -> external
		ArtworkSourcePriority.NativeFirst -> if (serverCoverArtId.nonBlankOrNull() == null) external else null
		ArtworkSourcePriority.NativeOnly -> null
	}
}

private fun String?.nonBlankOrNull(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.withQueryParameter(key: String, value: String): String {
	val separator = when {
		contains("?") -> if (endsWith("?") || endsWith("&")) "" else "&"
		else -> "?"
	}
	return "$this$separator$key=$value"
}
