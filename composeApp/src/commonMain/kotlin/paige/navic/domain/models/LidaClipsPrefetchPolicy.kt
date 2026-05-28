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

	val key = "$normalizedBaseUrl|${lidaClipsKeyFingerprint(apiKey.trim())}|$songId"
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
	normalizedLidaClipsBaseUrl(baseUrl)?.value

internal data class NormalizedLidaClipsBaseUrl(val value: String)

internal fun normalizedLidaClipsBaseUrl(baseUrl: String): NormalizedLidaClipsBaseUrl? {
	val trimmed = baseUrl.trim().trimEnd('/')
	val schemeSeparator = trimmed.indexOf("://")
	if (schemeSeparator <= 0) return null

	val scheme = trimmed.substring(0, schemeSeparator)
	if (!scheme.equals("http", ignoreCase = true) &&
		!scheme.equals("https", ignoreCase = true)
	) {
		return null
	}

	val afterScheme = trimmed.drop(schemeSeparator + 3)
	if (afterScheme.isBlank()) return null
	if (afterScheme.any { it == '?' || it == '#' }) return null

	val authority = afterScheme
		.takeWhile { it != '/' }
		.substringAfterLast('@')
	if (authority.isBlank()) return null

	val host = when {
		authority.startsWith("[") -> {
			val closingBracket = authority.indexOf(']')
			if (closingBracket == -1) return null
			authority.substring(1, closingBracket)
		}

		else -> authority.substringBefore(':')
	}

	return if (host.trim().trimEnd('.').isEmpty()) {
		null
	} else {
		NormalizedLidaClipsBaseUrl(trimmed)
	}
}
