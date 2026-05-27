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
	if (!enabled || baseUrl.isBlank() || songId.isNullOrBlank()) return null

	val key = "${baseUrl.trim().trimEnd('/')}|${apiKey.trim()}|$songId"
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
		lidaClipsBaseUrl.isNotBlank() &&
		userActionEnabled &&
		clipAvailability != LidaClipAvailability.Unavailable
