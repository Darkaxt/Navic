package paige.navic.domain.models

enum class LidaClipAvailability {
	Unknown,
	Available,
	Unavailable
}

fun nextLidaClipsPrefetchKey(
	enabled: Boolean,
	baseUrl: String,
	apiKey: String,
	songId: String?,
	lastPrefetchKey: String?
): String? {
	val normalizedBaseUrl = normalizedLidaClipsBaseUrlOrNull(baseUrl)
	if (!enabled || normalizedBaseUrl == null || songId.isNullOrBlank()) return null

	val key = "$normalizedBaseUrl|${apiKey.trim()}|$songId"
	return key.takeIf { it != lastPrefetchKey }
}

fun lidaClipAvailability(clip: DomainLidaClip?): LidaClipAvailability =
	if (clip == null) LidaClipAvailability.Unavailable else LidaClipAvailability.Available

fun shouldShowLidaClipsMusicVideoAction(
	lidaClipsEnabled: Boolean,
	lidaClipsBaseUrl: String,
	userActionEnabled: Boolean,
	clipAvailability: LidaClipAvailability
): Boolean =
	lidaClipsEnabled &&
		normalizedLidaClipsBaseUrlOrNull(lidaClipsBaseUrl) != null &&
		userActionEnabled &&
		clipAvailability != LidaClipAvailability.Unavailable

private fun normalizedLidaClipsBaseUrlOrNull(baseUrl: String): String? =
	baseUrl.trim().trimEnd('/').takeIf {
		it.startsWith("http://", ignoreCase = true) ||
			it.startsWith("https://", ignoreCase = true)
	}
