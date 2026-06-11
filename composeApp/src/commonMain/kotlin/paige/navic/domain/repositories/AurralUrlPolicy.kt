package paige.navic.domain.repositories

import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal const val AURRAL_BASE_URL_REQUIRED_MESSAGE = "Enter the Aurral URL first."
internal const val AURRAL_BASE_URL_INVALID_SCHEME_MESSAGE =
	"Aurral URL must start with http:// or https://."
internal const val AURRAL_BASE_URL_INVALID_HOST_MESSAGE =
	"Aurral URL must include a host and cannot include credentials, a query, or a fragment."

internal fun aurralEndpoint(baseUrl: String, path: String): String =
	"${normalizeAurralBaseUrl(baseUrl)}/${path.trim().trimStart('/')}"

internal fun configuredAurralBaseUrl(baseUrl: String): String? =
	normalizedAurralBaseUrl(baseUrl)?.value

internal data class NormalizedAurralBaseUrl(val value: String)

internal fun normalizedAurralBaseUrl(baseUrl: String): NormalizedAurralBaseUrl? {
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

	val authority = afterScheme.takeWhile { it != '/' }
	if ('@' in authority) return null

	val host = parsedAurralUrlHostOrNull(authority) ?: return null

	return if (host.trim().trimEnd('.').isEmpty()) {
		null
	} else {
		NormalizedAurralBaseUrl(trimmed)
	}
}

internal fun aurralBaseUrlConfigurationError(baseUrl: String): String? {
	val trimmed = baseUrl.trim().trimEnd('/')
	return when {
		trimmed.isEmpty() -> AURRAL_BASE_URL_REQUIRED_MESSAGE
		!trimmed.hasSupportedHttpScheme() -> AURRAL_BASE_URL_INVALID_SCHEME_MESSAGE
		normalizedAurralBaseUrl(trimmed) == null -> AURRAL_BASE_URL_INVALID_HOST_MESSAGE
		else -> null
	}
}

@OptIn(ExperimentalEncodingApi::class)
internal fun aurralBasicAuthHeaders(username: String, password: String): Map<String, String> {
	val trimmedUsername = username.trim()
	val trimmedPassword = password.trim()
	if (trimmedUsername.isEmpty() || trimmedPassword.isEmpty()) return emptyMap()
	val credentials = "$trimmedUsername:$trimmedPassword"
	return mapOf("Authorization" to "Basic ${Base64.encode(credentials.encodeToByteArray())}")
}

internal fun aurralBearerAuthHeaders(token: String?): Map<String, String> {
	val trimmedToken = token?.trim().orEmpty()
	return if (trimmedToken.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $trimmedToken")
}

internal fun aurralFlowStreamUrl(
	baseUrl: String,
	jobId: String,
	sessionToken: String?
): String? {
	val trimmedJobId = jobId.trim()
	val trimmedToken = sessionToken?.trim().orEmpty()
	if (trimmedJobId.isEmpty() || trimmedToken.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/stream/${encodeUrlComponent(trimmedJobId)}"
	) + "?token=${encodeUrlComponent(trimmedToken)}"
}

internal fun aurralFlowStreamTokenUrl(
	baseUrl: String,
	jobId: String,
	streamToken: String?
): String? {
	val trimmedJobId = jobId.trim()
	val trimmedToken = streamToken?.trim().orEmpty()
	if (trimmedJobId.isEmpty() || trimmedToken.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/stream/${encodeUrlComponent(trimmedJobId)}"
	) + "?st=${encodeUrlComponent(trimmedToken)}"
}

internal fun aurralFlowRawStreamUrl(
	baseUrl: String,
	jobId: String
): String? {
	val trimmedJobId = jobId.trim()
	if (trimmedJobId.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/stream/${encodeUrlComponent(trimmedJobId)}"
	)
}

internal fun aurralFlowArtworkUrl(
	baseUrl: String,
	playlistId: String,
	sessionToken: String?
): String? {
	val trimmedPlaylistId = playlistId.trim()
	val trimmedToken = sessionToken?.trim().orEmpty()
	if (trimmedPlaylistId.isEmpty() || trimmedToken.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	return aurralEndpoint(
		configuredBaseUrl,
		"api/weekly-flow/artwork/${encodeUrlComponent(trimmedPlaylistId)}"
	) + "?token=${encodeUrlComponent(trimmedToken)}"
}

internal fun aurralReleaseGroupCoverUrl(
	baseUrl: String,
	releaseGroupMbid: String,
	artistName: String,
	albumTitle: String
): String? {
	val trimmedReleaseGroupMbid = releaseGroupMbid.trim()
	if (trimmedReleaseGroupMbid.isEmpty()) return null
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl) ?: return null
	val query = listOfNotNull(
		artistName.trim().takeIf { it.isNotEmpty() }?.let {
			"artistName=${encodeUrlComponent(it)}"
		},
		albumTitle.trim().takeIf { it.isNotEmpty() }?.let {
			"albumTitle=${encodeUrlComponent(it)}"
		}
	).joinToString("&")
	val endpoint = aurralEndpoint(
		configuredBaseUrl,
		"api/artists/release-group/${encodeUrlComponent(trimmedReleaseGroupMbid)}/cover"
	)
	return if (query.isEmpty()) endpoint else "$endpoint?$query"
}

internal fun aurralAbsoluteImageUrl(
	baseUrl: String,
	imageUrl: String?
): String? {
	val trimmedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (
		trimmedImageUrl.startsWith("http://", ignoreCase = true) ||
		trimmedImageUrl.startsWith("https://", ignoreCase = true)
	) {
		return trimmedImageUrl
	}
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl)?.trimEnd('/') ?: return null
	return when {
		trimmedImageUrl.startsWith("/") -> configuredBaseUrl + trimmedImageUrl
		else -> "$configuredBaseUrl/${trimmedImageUrl.trimStart('/')}"
	}
}

internal fun aurralRequestHeadersForUrl(
	baseUrl: String,
	imageUrl: String?,
	requestHeaders: Map<String, String>
): Map<String, String> {
	if (requestHeaders.isEmpty()) return emptyMap()
	val configuredBaseUrl = configuredAurralBaseUrl(baseUrl)?.trimEnd('/') ?: return emptyMap()
	val trimmedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyMap()
	return if (trimmedImageUrl.startsWith("$configuredBaseUrl/", ignoreCase = true)) {
		requestHeaders
	} else {
		emptyMap()
	}
}

internal fun aurralConnectionResult(
	operation: String,
	status: HttpStatusCode
): AurralConnectionResult =
	when {
		status == HttpStatusCode.Unauthorized -> AurralConnectionResult.Unauthorized
		status == HttpStatusCode.Forbidden -> AurralConnectionResult.Forbidden
		status.isSuccess() -> AurralConnectionResult.Connected
		else -> AurralConnectionResult.Failed(aurralHttpErrorMessage(operation, status))
	}

internal fun aurralHttpErrorMessage(
	operation: String,
	status: HttpStatusCode
): String =
	when (status) {
		HttpStatusCode.Unauthorized -> "$operation unauthorized. Check the Aurral username and password."
		HttpStatusCode.Forbidden -> "$operation forbidden. Check the Aurral user permissions."
		else -> "$operation returned HTTP ${status.value}"
	}

private fun normalizeAurralBaseUrl(baseUrl: String): String =
	configuredAurralBaseUrl(baseUrl)
		?: error(aurralBaseUrlConfigurationError(baseUrl)
			?: AURRAL_BASE_URL_REQUIRED_MESSAGE)

private fun parsedAurralUrlHostOrNull(authority: String): String? {
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

private fun String.hasSupportedHttpScheme(): Boolean =
	startsWith("http://", ignoreCase = true) ||
		startsWith("https://", ignoreCase = true)

internal fun aurralEncodeUrlComponent(value: String): String =
	encodeUrlComponent(value)

private fun encodeUrlComponent(value: String): String {
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
