package paige.navic.domain.models

fun nextLidaClipsPrefetchKey(
	enabled: Boolean,
	baseUrl: String,
	apiKey: String,
	songId: String?,
	lastPrefetchKey: String?
): String? {
	if (!enabled || songId.isNullOrBlank()) return null

	val key = "${baseUrl.trim().trimEnd('/')}|${apiKey.trim()}|$songId"
	return key.takeIf { it != lastPrefetchKey }
}
