package paige.navic.ui.components.common

internal fun visibleCoverArtIdForAurralPolicy(
	coverArtId: String?,
	imageUrl: String?,
	aurralEnabled: Boolean
): String? {
	val nativeCoverArtId = coverArtId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (!aurralEnabled) return nativeCoverArtId
	return nativeCoverArtId.takeUnless { !imageUrl.isNullOrBlank() }
}

internal fun visibleImageUrlForAurralPolicy(
	imageUrl: String?,
	nativeCoverArtId: String? = null,
	aurralEnabled: Boolean
): String? {
	val resolvedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (!aurralEnabled) return resolvedImageUrl
	val hasNativeCoverFallback = !nativeCoverArtId.isNullOrBlank()
	return resolvedImageUrl.takeUnless { hasNativeCoverFallback && it.isNavidromeArtworkUrl() }
}

private fun String.isNavidromeArtworkUrl(): Boolean {
	val normalized = lowercase()
	return "navidrome" in normalized ||
		"/rest/getcoverart" in normalized ||
		"/rest/getartistimage" in normalized ||
		"/getcoverart" in normalized ||
		"/getartistimage" in normalized
}
