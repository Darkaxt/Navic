package paige.navic.domain.repositories

import io.ktor.http.Url

enum class ExternalTextPurpose {
	AudioBookBayProviderCover
}

internal data class ApprovedExternalTextRequest(
	val url: String,
	val scheme: String,
	val host: String,
	val port: Int
)

internal val AudioBookBayProviderPageHosts = setOf("audiobookbay.lu")

internal fun approvedExternalTextRequest(
	url: String,
	purpose: ExternalTextPurpose
): ApprovedExternalTextRequest {
	val trimmed = url.trim()
	if (trimmed.isEmpty() || trimmed != url) {
		throw IllegalStateException("Provider source URL must be an absolute approved HTTPS URL.")
	}
	if ('#' in trimmed) {
		throw IllegalStateException("Provider source URL fragments are not allowed.")
	}
	val schemeSeparator = trimmed.indexOf("://")
	if (schemeSeparator <= 0) {
		throw IllegalStateException("Provider source URL must be an absolute approved HTTPS URL.")
	}
	val authority = trimmed.drop(schemeSeparator + 3).takeWhile { character ->
		character != '/' && character != '?' && character != '#'
	}
	if (authority.isEmpty() || '@' in authority) {
		throw IllegalStateException("Provider source URL credentials are not allowed.")
	}

	val parsed = runCatching { Url(trimmed) }.getOrNull()
		?: throw IllegalStateException("Provider source URL must be an absolute approved HTTPS URL.")
	val scheme = parsed.protocol.name.lowercase()
	val host = parsed.host.lowercase()
	val allowedHosts = when (purpose) {
		ExternalTextPurpose.AudioBookBayProviderCover -> AudioBookBayProviderPageHosts
	}
	if (scheme != "https" || parsed.port != 443 || host !in allowedHosts) {
		throw IllegalStateException("Provider source URL is not approved for ${purpose.name}.")
	}
	return ApprovedExternalTextRequest(
		url = parsed.toString(),
		scheme = scheme,
		host = host,
		port = parsed.port
	)
}

internal fun isPublicExternalAddress(address: ByteArray): Boolean =
	when (address.size) {
		4 -> isPublicIpv4Address(address)
		16 -> isPublicIpv6Address(address)
		else -> false
	}

private fun isPublicIpv4Address(address: ByteArray): Boolean {
	val first = address[0].unsigned()
	val second = address[1].unsigned()
	return when {
		first == 0 -> false
		first == 10 -> false
		first == 100 && second in 64..127 -> false
		first == 127 -> false
		first == 169 && second == 254 -> false
		first == 172 && second in 16..31 -> false
		first == 192 && second == 168 -> false
		first == 198 && second in 18..19 -> false
		first >= 224 -> false
		else -> true
	}
}

private fun isPublicIpv6Address(address: ByteArray): Boolean {
	if (address.take(10).all { it == 0.toByte() } &&
		address[10].unsigned() == 0xff &&
		address[11].unsigned() == 0xff
	) {
		return isPublicIpv4Address(address.copyOfRange(12, 16))
	}
	if (address.take(12).all { it == 0.toByte() }) return false

	val first = address[0].unsigned()
	val second = address[1].unsigned()
	return when {
		first and 0xfe == 0xfc -> false
		first == 0xfe && second and 0xc0 == 0x80 -> false
		first == 0xff -> false
		else -> true
	}
}

private fun Byte.unsigned(): Int = toInt() and 0xff
