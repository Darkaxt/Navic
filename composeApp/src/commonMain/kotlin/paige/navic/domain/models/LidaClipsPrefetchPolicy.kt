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
	val host = parsedLidaClipsUrlHostOrNull(authority) ?: return null

	return if (host.trim().trimEnd('.').isEmpty()) {
		null
	} else {
		NormalizedLidaClipsBaseUrl(trimmed)
	}
}

private fun parsedLidaClipsUrlHostOrNull(authority: String): String? {
	if (authority.isBlank()) return null

	val host: String
	val portText: String?
	if (authority.startsWith("[")) {
		val closingBracket = authority.indexOf(']')
		if (closingBracket == -1) return null

		host = authority.substring(1, closingBracket)
		val suffix = authority.drop(closingBracket + 1)
		if (suffix.isNotEmpty() && !suffix.startsWith(":")) return null
		portText = suffix.takeIf { it.isNotEmpty() }?.drop(1)
	} else {
		val firstColon = authority.indexOf(':')
		val lastColon = authority.lastIndexOf(':')
		if (firstColon != -1 && firstColon != lastColon) return null

		host = if (lastColon == -1) authority else authority.substring(0, lastColon)
		portText = if (lastColon == -1) null else authority.substring(lastColon + 1)
	}

	val port = portText?.toIntOrNull()
		?.takeIf { it in 1..65535 }
	if (portText != null && port == null) return null

	return host
}
