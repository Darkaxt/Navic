package paige.navic.domain.repositories

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.encodeURLQueryComponent

internal fun binderyApiKeyHeaders(apiKey: String): Map<String, String> {
	val trimmed = apiKey.trim()
	return if (trimmed.isEmpty()) emptyMap() else mapOf("X-Api-Key" to trimmed)
}

internal fun binderyRequestHeadersForUrl(
	baseUrl: String,
	url: String?,
	requestHeaders: Map<String, String>
): Map<String, String> {
	val requestOrigin = url?.canonicalHttpOriginOrNull() ?: return emptyMap()
	val configuredOrigin = configuredBinderyOpdsBaseUrl(baseUrl)
		?.canonicalHttpOriginOrNull()
		?: return emptyMap()
	return if (requestOrigin == configuredOrigin) requestHeaders else emptyMap()
}

internal fun binderyAudioBookBayProviderCoverUrl(
	sourceUrl: String,
	html: String
): String? {
	val approvedSource = runCatching {
		approvedExternalTextRequest(sourceUrl, ExternalTextPurpose.AudioBookBayProviderCover)
	}.getOrNull() ?: return null
	val document = runCatching { Ksoup.parse(html, approvedSource.url) }.getOrNull() ?: return null
	val metadataCandidates = document.getElementsByTag("meta").mapNotNull { element ->
		val key = element.attr("property").ifBlank { element.attr("name") }.lowercase()
		element.attr("content").takeIf { key == "og:image" || key == "twitter:image" }
	}
	val imageCandidates = document.getElementsByTag("img").mapNotNull { element ->
		element.attr("src").takeIf(String::isNotBlank)
	}
	val candidates = (metadataCandidates + imageCandidates)
		.mapNotNull { candidate -> binderyAbsoluteProviderImageUrl(approvedSource.url, candidate) }
		.mapNotNull(String::approvedAudioBookBayCoverImageUrlOrNull)
		.distinct()
	return candidates.firstOrNull(String::isAudioBookBayPrimaryCoverUrl)
		?: candidates.firstOrNull(String::isLikelyProviderCoverImageUrl)
}

internal fun binderyEndpoint(baseUrl: String, path: String): String =
	binderyEndpointFromNormalizedBase(
		normalizeBinderyOpdsBaseUrl(baseUrl),
		path
	)

internal fun binderyApiEndpoint(baseUrl: String, path: String): String {
	val normalizedBaseUrl = normalizeBinderyOpdsBaseUrl(baseUrl)
	val trimmedPath = path.trim()
	if (trimmedPath.startsWith("http://", ignoreCase = true) ||
		trimmedPath.startsWith("https://", ignoreCase = true)
	) {
		return trimmedPath
	}
	val apiRoot = binderyApiRoot(normalizedBaseUrl)
	val relativePath = trimmedPath.trimStart('/')
	return if (relativePath.startsWith("api/v1/") || relativePath == "api/v1") {
		"$apiRoot/$relativePath"
	} else {
		"$apiRoot/api/v1/$relativePath"
	}
}

private fun binderyEndpointFromNormalizedBase(baseUrl: String, path: String): String {
	val trimmedPath = path.trim()
	if (trimmedPath.startsWith("http://", ignoreCase = true) ||
		trimmedPath.startsWith("https://", ignoreCase = true)
	) {
		return trimmedPath
	}
	val relativePath = trimmedPath.trimStart('/')
	return if (relativePath.startsWith("opds/")) {
		"${binderyOrigin(baseUrl)}/$relativePath"
	} else if (relativePath.startsWith("api/v1/") || relativePath == "api/v1") {
		"${binderyApiRoot(baseUrl)}/$relativePath"
	} else {
		"$baseUrl/$relativePath"
	}
}

private fun binderyApiRoot(normalizedOpdsBaseUrl: String): String =
	if (normalizedOpdsBaseUrl.endsWith("/opds", ignoreCase = true)) {
		normalizedOpdsBaseUrl.dropLast("/opds".length)
	} else {
		binderyOrigin(normalizedOpdsBaseUrl)
	}

internal fun binderyReadingProgressPath(bookId: String, alias: String?): String {
	val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
		?: throw IllegalStateException("Bindery book id is required.")
	val basePath = "books/${encodeUrlPathSegment(safeBookId)}/progress"
	val safeAlias = alias?.trim()?.takeIf { it.isNotEmpty() }
		?: return basePath
	return "$basePath?alias=${safeAlias.encodeURLQueryComponent()}"
}

private fun binderyOrigin(baseUrl: String): String {
	val schemeSeparator = baseUrl.indexOf("://")
	val scheme = baseUrl.substring(0, schemeSeparator)
	val afterScheme = baseUrl.drop(schemeSeparator + 3)
	val authority = afterScheme.takeWhile { it != '/' }
	return "$scheme://$authority"
}

internal fun configuredBinderyOpdsBaseUrl(baseUrl: String): String? =
	normalizedBinderyOpdsBaseUrl(baseUrl)?.value

internal data class NormalizedBinderyOpdsBaseUrl(val value: String)

internal fun normalizedBinderyOpdsBaseUrl(baseUrl: String): NormalizedBinderyOpdsBaseUrl? {
	val trimmed = baseUrl.trim().trimEnd('/')
	val schemeSeparator = trimmed.indexOf("://")
	if (schemeSeparator <= 0) return null

	val scheme = trimmed.substring(0, schemeSeparator)
	if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
		return null
	}

	val afterScheme = trimmed.drop(schemeSeparator + 3)
	if (afterScheme.isBlank()) return null
	if (afterScheme.any { it == '?' || it == '#' }) return null

	val authority = afterScheme.takeWhile { it != '/' }
	if ('@' in authority) return null
	val host = parsedBinderyUrlHostOrNull(authority) ?: return null

	return if (host.trim().trimEnd('.').isEmpty()) {
		null
	} else {
		NormalizedBinderyOpdsBaseUrl(trimmed)
	}
}

internal fun binderyOpdsBaseUrlConfigurationError(baseUrl: String): String? {
	val trimmed = baseUrl.trim().trimEnd('/')
	return when {
		trimmed.isEmpty() -> BINDERY_OPDS_URL_REQUIRED_MESSAGE
		!trimmed.hasSupportedHttpScheme() -> BINDERY_OPDS_URL_INVALID_SCHEME_MESSAGE
		normalizedBinderyOpdsBaseUrl(trimmed) == null -> BINDERY_OPDS_URL_INVALID_HOST_MESSAGE
		else -> null
	}
}

private fun normalizeBinderyOpdsBaseUrl(baseUrl: String): String =
	configuredBinderyOpdsBaseUrl(baseUrl)
		?: error(binderyOpdsBaseUrlConfigurationError(baseUrl) ?: BINDERY_OPDS_URL_REQUIRED_MESSAGE)

private fun parsedBinderyUrlHostOrNull(authority: String): String? {
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
	val port = portText?.toIntOrNull()?.takeIf { it in 1..65535 }
	if (portText != null && port == null) return null
	return host
}

private fun String.hasSupportedHttpScheme(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

internal fun String?.isAudioBookBayProvider(): Boolean =
	this?.trim()?.lowercase() in setOf("audiobookbay", "audio book bay", "abb")

private data class CanonicalHttpOrigin(
	val scheme: String,
	val host: String,
	val port: Int
)

private fun String.canonicalHttpOriginOrNull(): CanonicalHttpOrigin? {
	val trimmed = trim()
	val schemeSeparator = trimmed.indexOf("://")
	if (schemeSeparator <= 0) return null
	val afterScheme = trimmed.drop(schemeSeparator + 3)
	val authority = afterScheme.takeWhile { it != '/' }.takeIf { it.isNotBlank() } ?: return null
	if ('@' in authority) return null
	val parsed = runCatching { Url(trimmed) }.getOrNull() ?: return null
	val scheme = parsed.protocol.name.lowercase()
	if (scheme != "http" && scheme != "https") return null
	val host = parsed.host.trim().trimEnd('.').lowercase().takeIf { it.isNotEmpty() } ?: return null
	return CanonicalHttpOrigin(scheme = scheme, host = host, port = parsed.port)
}

private fun String.httpUrlOriginOrNull(): String? = canonicalHttpOriginOrNull()?.let { origin ->
	val defaultPort = if (origin.scheme == "https") 443 else 80
	val authority = if (origin.port == defaultPort) origin.host else "${origin.host}:${origin.port}"
	"${origin.scheme}://$authority"
}

private fun binderyAbsoluteProviderImageUrl(baseUrl: String, candidate: String): String? {
	val value = candidate.htmlAttributeDecode().trim()
		.takeIf { it.isNotEmpty() }
		?: return null
	val absolute = when {
		value.startsWith("http://", ignoreCase = true) ||
			value.startsWith("https://", ignoreCase = true) -> value
		value.startsWith("//") -> {
			val scheme = baseUrl.substringBefore("://", "https")
				.takeIf { it.equals("http", ignoreCase = true) || it.equals("https", ignoreCase = true) }
				?: "https"
			"$scheme:$value"
		}
		value.startsWith("/") -> {
			val origin = baseUrl.httpUrlOriginOrNull() ?: return null
			"$origin$value"
		}
		else -> {
			val baseWithoutQuery = baseUrl.substringBefore("?").substringBefore("#")
			val directory = baseWithoutQuery.substringBeforeLast('/', missingDelimiterValue = baseWithoutQuery)
			"$directory/$value"
		}
	}
	return absolute.upgradeKnownProviderImageUrl()
}

private fun String.htmlAttributeDecode(): String =
	replace("&amp;", "&")
		.replace("&#038;", "&")
		.replace("&#38;", "&")
		.replace("&quot;", "\"")
		.replace("&#34;", "\"")
		.replace("&#39;", "'")
		.replace("&apos;", "'")

private fun String.upgradeKnownProviderImageUrl(): String =
	if (startsWith("http://image.bayimg.com/", ignoreCase = true)) {
		"https://" + drop("http://".length)
	} else {
		this
	}

private fun String.approvedAudioBookBayCoverImageUrlOrNull(): String? {
	val trimmed = trim()
	if ('#' in trimmed) return null
	val parsed = runCatching { Url(trimmed) }.getOrNull() ?: return null
	val host = parsed.host.lowercase()
	return parsed.toString().takeIf {
		parsed.protocol.name.equals("https", ignoreCase = true) &&
			parsed.port == 443 &&
			host in AudioBookBayCoverImageHosts
	}
}

private fun String.isAudioBookBayPrimaryCoverUrl(): Boolean {
	val parsed = runCatching { Url(this) }.getOrNull() ?: return false
	return parsed.host.equals("image.bayimg.com", ignoreCase = true) && hasProviderImageExtension()
}

private fun String.isLikelyProviderCoverImageUrl(): Boolean {
	val normalized = lowercase()
	if (!normalized.hasProviderImageExtension()) return false
	if ("gravatar.com/" in normalized) return false
	if ("/avatar/" in normalized) return false
	if ("/images/search." in normalized) return false
	if ("/images/trr." in normalized) return false
	if ("/images/tlt." in normalized) return false
	if ("/images/bz." in normalized) return false
	if ("/images/" in normalized && normalized.endsWith(".gif")) return false
	return true
}

private fun String.hasProviderImageExtension(): Boolean {
	val path = substringBefore("?").substringBefore("#").lowercase()
	return path.endsWith(".jpg") ||
		path.endsWith(".jpeg") ||
		path.endsWith(".png") ||
		path.endsWith(".webp")
}

private val AudioBookBayCoverImageHosts = setOf(
	"audiobookbay.lu",
	"image.bayimg.com"
)

internal fun binderyHttpErrorMessage(
	operation: String,
	status: HttpStatusCode
): String =
	when (status) {
		HttpStatusCode.Unauthorized -> "$operation unauthorized. Check the Bindery API key."
		HttpStatusCode.Forbidden -> "$operation forbidden. Check the Bindery API key permissions."
		else -> "$operation returned HTTP ${status.value}"
	}

internal fun encodeUrlPathSegment(value: String): String {
	val hex = "0123456789ABCDEF"
	return buildString {
		value.encodeToByteArray().forEach { byte ->
			val code = byte.toInt() and 0xff
			val char = code.toChar()
			if (
				char in 'A'..'Z' ||
				char in 'a'..'z' ||
				char in '0'..'9' ||
				char == '-' ||
				char == '.' ||
				char == '_' ||
				char == '~'
			) {
				append(char)
			} else {
				append('%')
				append(hex[code shr 4])
				append(hex[code and 0x0f])
			}
		}
	}
}
