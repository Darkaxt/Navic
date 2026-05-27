package paige.navic.domain.models

const val LIDA_CLIPS_PREFETCH_REFRESH_AFTER_MILLIS = 10 * 60 * 1000L

fun nextLidaClipsPrefetchKey(
	enabled: Boolean,
	baseUrl: String,
	apiKey: String,
	songId: String?,
	lastPrefetchKey: String?,
	lastPrefetchTimeMillis: Long? = null,
	currentTimeMillis: Long = 0L,
	refreshAfterMillis: Long = LIDA_CLIPS_PREFETCH_REFRESH_AFTER_MILLIS
): String? {
	val normalizedBaseUrl = normalizedLidaClipsBaseUrlOrNull(baseUrl)
	if (!enabled || normalizedBaseUrl == null || songId.isNullOrBlank()) return null

	val key = "$normalizedBaseUrl|${apiKey.trim()}|$songId"
	if (key != lastPrefetchKey) return key
	val lastPrefetchTime = lastPrefetchTimeMillis ?: return null
	return key.takeIf { currentTimeMillis - lastPrefetchTime > refreshAfterMillis }
}

fun shouldShowLidaClipsMusicVideoAction(
	lidaClipsEnabled: Boolean,
	lidaClipsBaseUrl: String,
	userActionEnabled: Boolean
): Boolean =
	lidaClipsEnabled &&
		normalizedLidaClipsBaseUrlOrNull(lidaClipsBaseUrl) != null &&
		userActionEnabled

internal fun normalizedLidaClipsBaseUrlOrNull(baseUrl: String): String? =
	baseUrl.trim().trimEnd('/').takeIf {
		it.startsWith("http://", ignoreCase = true) ||
			it.startsWith("https://", ignoreCase = true)
	}
