package paige.navic.ui.components.common

@Suppress("UNUSED_PARAMETER")
internal fun visibleCoverArtIdForAurralPolicy(
	coverArtId: String?,
	imageUrl: String?,
	aurralEnabled: Boolean
): String? {
	val nativeCoverArtId = coverArtId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (!aurralEnabled) return nativeCoverArtId
	return null
}

internal fun visibleImageUrlForAurralPolicy(
	imageUrl: String?,
	aurralEnabled: Boolean
): String? {
	val resolvedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (!aurralEnabled) return resolvedImageUrl
	return resolvedImageUrl.takeUnless { it.isNavidromeArtworkUrl() }
}

private fun String.isNavidromeArtworkUrl(): Boolean {
	val normalized = lowercase()
	return "navidrome" in normalized ||
		"/rest/getcoverart" in normalized ||
		"/rest/getartistimage" in normalized ||
		"/getcoverart" in normalized ||
		"/getartistimage" in normalized
}
