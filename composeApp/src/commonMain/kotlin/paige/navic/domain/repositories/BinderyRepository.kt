package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.util.core.Logger

private const val TAG = "BinderyRepository"
internal const val BINDERY_OPDS_URL_REQUIRED_MESSAGE = "Enter the Bindery OPDS URL first."
internal const val BINDERY_OPDS_URL_INVALID_SCHEME_MESSAGE =
	"Bindery OPDS URL must start with http:// or https://."
internal const val BINDERY_OPDS_URL_INVALID_HOST_MESSAGE =
	"Bindery OPDS URL must include a host and cannot include credentials, a query, or a fragment."
internal const val BINDERY_API_KEY_REQUIRED_MESSAGE = "Enter the Bindery API key first."

private val BinderyJson = Json {
	ignoreUnknownKeys = true
	isLenient = true
}

class BinderyRepository(
	private val preferenceManager: PreferenceManager,
	private val apiClient: BinderyApiClient = KtorBinderyApiClient()
) {
	suspend fun testConnection(): BinderyConnectionResult {
		if (!preferenceManager.binderyEnabled) return BinderyConnectionResult.Disabled
		val urlError = binderyOpdsBaseUrlConfigurationError(preferenceManager.binderyOpdsBaseUrl)
		if (urlError != null) {
			return if (urlError == BINDERY_OPDS_URL_REQUIRED_MESSAGE) {
				BinderyConnectionResult.MissingOpdsUrl
			} else {
				BinderyConnectionResult.InvalidOpdsUrl(urlError)
			}
		}
		val apiKey = preferenceManager.binderyApiKey.trim()
		if (apiKey.isEmpty()) return BinderyConnectionResult.MissingApiKey
		val baseUrl = configuredBinderyOpdsBaseUrl(preferenceManager.binderyOpdsBaseUrl)
			?: return BinderyConnectionResult.MissingOpdsUrl

		return runCatching {
			apiClient.fetchRootCatalog(baseUrl, binderyApiKeyHeaders(apiKey))
		}.fold(
			onSuccess = { catalog ->
				preferenceManager.markIntegrationServiceAvailable(IntegrationService.Bindery)
				BinderyConnectionResult.Connected(
					navigationCount = catalog.navigation.size,
					audiobooksAvailable = catalog.hasNavigationPath("/opds/formats/audiobook")
				)
			},
			onFailure = { error ->
				when ((error as? BinderyApiException)?.status) {
					HttpStatusCode.Unauthorized -> {
						preferenceManager.markIntegrationServiceAvailable(IntegrationService.Bindery)
						BinderyConnectionResult.Unauthorized
					}
					HttpStatusCode.Forbidden -> {
						preferenceManager.markIntegrationServiceAvailable(IntegrationService.Bindery)
						BinderyConnectionResult.Forbidden
					}
					else -> {
						Logger.w(TAG, "Bindery connection test failed", error)
						preferenceManager.markIntegrationServiceDown(IntegrationService.Bindery)
						BinderyConnectionResult.Failed(error.message ?: error::class.simpleName ?: "Unknown error")
					}
				}
			}
		)
	}

	suspend fun getServiceStatus(): Result<BinderyServiceStatus> {
		val apiKey = preferenceManager.binderyApiKey.trim()
		val configuredUrl = configuredBinderyOpdsBaseUrl(preferenceManager.binderyOpdsBaseUrl)
		if (!preferenceManager.binderyEnabled) {
			return Result.success(
				BinderyServiceStatus(
					enabled = false,
					opdsUrlConfigured = configuredUrl != null,
					apiKeyConfigured = apiKey.isNotEmpty()
				)
			)
		}
		if (configuredUrl == null || apiKey.isEmpty()) {
			return Result.success(
				BinderyServiceStatus(
					enabled = true,
					opdsUrlConfigured = configuredUrl != null,
					apiKeyConfigured = apiKey.isNotEmpty()
				)
			)
		}

		return runCatching {
			val catalog = apiClient.fetchRootCatalog(configuredUrl, binderyApiKeyHeaders(apiKey))
			BinderyServiceStatus(
				enabled = true,
				opdsUrlConfigured = true,
				apiKeyConfigured = true,
				navigationCount = catalog.navigation.size,
				hasSearch = catalog.hasRel("search"),
				hasAudiobooks = catalog.hasNavigationPath("/opds/formats/audiobook"),
				hasAuthors = catalog.hasNavigationPath("/opds/authors"),
				hasSeries = catalog.hasNavigationPath("/opds/series"),
				hasCollections = catalog.hasNavigationPath("/opds/collections"),
				progressSyncSupported = false,
				paginationSupported = catalog.links.any { link ->
					link.rel.any { it.equals("next", ignoreCase = true) }
				}
			)
		}.onFailure { error ->
			Logger.w(TAG, "Bindery service status failed", error)
		}.recordBinderyAvailability()
	}

	suspend fun getCatalog(path: String): Result<BinderyCatalog> =
		withConfiguredClient { baseUrl, headers ->
			apiClient.fetchCatalog(baseUrl, headers, path)
		}

	suspend fun getManifest(bookId: String): Result<BinderyManifest> =
		withConfiguredClient { baseUrl, headers ->
			apiClient.fetchManifest(baseUrl, headers, bookId)
		}

	suspend fun getBookResources(bookId: String): Result<BinderyResourceCatalog> =
		withConfiguredClient { baseUrl, headers ->
			apiClient.fetchBookResources(baseUrl, headers, bookId)
		}

	private suspend fun <T> withConfiguredClient(
		action: suspend (baseUrl: String, headers: Map<String, String>) -> T
	): Result<T> {
		if (!preferenceManager.binderyEnabled) {
			return Result.failure(IllegalStateException("Bindery is disabled."))
		}
		val urlError = binderyOpdsBaseUrlConfigurationError(preferenceManager.binderyOpdsBaseUrl)
		if (urlError != null) return Result.failure(IllegalStateException(urlError))
		val apiKey = preferenceManager.binderyApiKey.trim()
		if (apiKey.isEmpty()) return Result.failure(IllegalStateException(BINDERY_API_KEY_REQUIRED_MESSAGE))
		val baseUrl = configuredBinderyOpdsBaseUrl(preferenceManager.binderyOpdsBaseUrl)
			?: return Result.failure(IllegalStateException(BINDERY_OPDS_URL_REQUIRED_MESSAGE))
		return runCatching {
			action(baseUrl, binderyApiKeyHeaders(apiKey))
		}.onFailure { error ->
			Logger.w(TAG, "Bindery OPDS request failed", error)
		}.recordBinderyAvailability()
	}

	private fun <T> Result<T>.recordBinderyAvailability(): Result<T> =
		onSuccess {
			preferenceManager.markIntegrationServiceAvailable(IntegrationService.Bindery)
		}.onFailure {
			preferenceManager.markIntegrationServiceDown(IntegrationService.Bindery)
		}
}

interface BinderyApiClient {
	suspend fun fetchRootCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): BinderyCatalog

	suspend fun fetchCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyCatalog

	suspend fun fetchManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyManifest

	suspend fun fetchBookResources(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyResourceCatalog
}

private class KtorBinderyApiClient : BinderyApiClient {
	private val client = HttpClient {
		install(HttpTimeout) {
			requestTimeoutMillis = 45_000
			connectTimeoutMillis = 10_000
			socketTimeoutMillis = 45_000
		}
		install(ContentNegotiation) {
			json(BinderyJson)
		}
	}

	override suspend fun fetchRootCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): BinderyCatalog =
		fetchCatalog(baseUrl, requestHeaders, "/")

	override suspend fun fetchCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyCatalog {
		val response = client.get(binderyEndpoint(baseUrl, path)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "opds+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS catalog", response.status))
		}
		return response.body<BinderyCatalogDto>().toCatalog()
	}

	override suspend fun fetchManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyManifest {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		val response = client.get(binderyEndpoint(baseUrl, "books/${encodeUrlPathSegment(safeBookId)}/manifest")) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "audiobook+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS manifest", response.status))
		}
		return response.body<BinderyPublicationDto>().toManifest()
	}

	override suspend fun fetchBookResources(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyResourceCatalog {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		val response = client.get(binderyEndpoint(baseUrl, "books/${encodeUrlPathSegment(safeBookId)}/resources")) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "opds+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS resources", response.status))
		}
		return response.body<BinderyResourceCatalogDto>().toResourceCatalog()
	}
}

sealed interface BinderyConnectionResult {
	data object Disabled : BinderyConnectionResult
	data object MissingOpdsUrl : BinderyConnectionResult
	data class InvalidOpdsUrl(val message: String) : BinderyConnectionResult
	data object MissingApiKey : BinderyConnectionResult
	data object Unauthorized : BinderyConnectionResult
	data object Forbidden : BinderyConnectionResult
	data class Connected(
		val navigationCount: Int,
		val audiobooksAvailable: Boolean
	) : BinderyConnectionResult
	data class Failed(val message: String) : BinderyConnectionResult
}

data class BinderyServiceStatus(
	val enabled: Boolean = true,
	val opdsUrlConfigured: Boolean,
	val apiKeyConfigured: Boolean,
	val navigationCount: Int = 0,
	val hasSearch: Boolean = false,
	val hasAudiobooks: Boolean = false,
	val hasAuthors: Boolean = false,
	val hasSeries: Boolean = false,
	val hasCollections: Boolean = false,
	val progressSyncSupported: Boolean = false,
	val paginationSupported: Boolean = false
)

data class BinderyCatalog(
	val title: String,
	val identifier: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val properties: Map<String, String> = emptyMap(),
	val images: List<BinderyLink> = emptyList(),
	val links: List<BinderyLink> = emptyList(),
	val navigation: List<BinderyLink> = emptyList(),
	val publications: List<BinderyPublication> = emptyList()
) {
	fun hasRel(rel: String): Boolean =
		links.any { link -> link.rel.any { it.equals(rel, ignoreCase = true) } }

	fun hasNavigationPath(path: String): Boolean {
		val normalizedPath = path.trim().trimEnd('/').lowercase()
		return navigation.any { link ->
			link.href.trim().trimEnd('/').lowercase() == normalizedPath
		}
	}
}

data class BinderyPublication(
	val id: String?,
	val title: String,
	val author: String? = null,
	val published: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val durationSeconds: Double? = null,
	val properties: Map<String, String> = emptyMap(),
	val links: List<BinderyLink> = emptyList(),
	val images: List<BinderyLink> = emptyList()
)

data class BinderyManifest(
	val id: String?,
	val title: String,
	val author: String? = null,
	val published: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val durationSeconds: Double? = null,
	val properties: Map<String, String> = emptyMap(),
	val links: List<BinderyLink> = emptyList(),
	val images: List<BinderyLink> = emptyList(),
	val readingOrder: List<BinderyReadingOrderItem> = emptyList()
)

data class BinderyReadingOrderItem(
	val href: String,
	val title: String,
	val type: String?,
	val durationSeconds: Double? = null,
	val sizeBytes: Long? = null
)

data class BinderyLink(
	val href: String,
	val title: String? = null,
	val type: String? = null,
	val rel: List<String> = emptyList(),
	val properties: Map<String, String> = emptyMap(),
	val images: List<BinderyLink> = emptyList()
)

data class BinderyResourceCatalog(
	val title: String,
	val resources: List<BinderyBookResource> = emptyList()
)

data class BinderyBookResource(
	val href: String,
	val title: String,
	val type: String? = null,
	val kind: String? = null,
	val durationSeconds: Double? = null,
	val sizeBytes: Long? = null,
	val properties: Map<String, String> = emptyMap()
)

class BinderyApiException(
	val status: HttpStatusCode,
	message: String
) : IllegalStateException(message)

@Serializable
private data class BinderyCatalogDto(
	val metadata: BinderyMetadataDto = BinderyMetadataDto(),
	val properties: Map<String, JsonElement> = emptyMap(),
	val images: List<BinderyLinkDto> = emptyList(),
	val links: List<BinderyLinkDto> = emptyList(),
	val navigation: List<BinderyLinkDto> = emptyList(),
	val publications: List<BinderyPublicationDto> = emptyList()
)

@Serializable
private data class BinderyResourceCatalogDto(
	val metadata: BinderyMetadataDto = BinderyMetadataDto(),
	val resources: List<BinderyLinkDto> = emptyList()
)

@Serializable
private data class BinderyPublicationDto(
	val metadata: BinderyMetadataDto = BinderyMetadataDto(),
	val properties: Map<String, JsonElement> = emptyMap(),
	val links: List<BinderyLinkDto> = emptyList(),
	val images: List<BinderyLinkDto> = emptyList(),
	@SerialName("readingOrder") val readingOrder: List<BinderyLinkDto> = emptyList()
)

@Serializable
private data class BinderyMetadataDto(
	val title: String? = null,
	val identifier: String? = null,
	val sortAs: String? = null,
	val author: List<BinderyContributorDto> = emptyList(),
	val published: String? = null,
	val modified: String? = null,
	val description: String? = null,
	val subject: List<String> = emptyList(),
	val duration: Double? = null
)

@Serializable
private data class BinderyContributorDto(
	val name: String? = null
)

@Serializable
private data class BinderyLinkDto(
	val href: String? = null,
	val title: String? = null,
	val type: String? = null,
	val rel: JsonElement? = null,
	val properties: Map<String, JsonElement> = emptyMap(),
	val images: List<BinderyLinkDto> = emptyList(),
	val duration: Double? = null
)

private fun BinderyCatalogDto.toCatalog(): BinderyCatalog =
	BinderyCatalog(
		title = metadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Bindery",
		identifier = metadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		description = metadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = metadata.subject.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		properties = properties.toStringProperties(),
		images = images.mapNotNull { it.toLink() },
		links = links.mapNotNull { it.toLink() },
		navigation = navigation.mapNotNull { it.toLink() },
		publications = publications.map { it.toPublication() }
	)

private fun BinderyPublicationDto.toPublication(): BinderyPublication =
	BinderyPublication(
		id = metadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		title = metadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
		author = metadata.author.firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
		published = metadata.published?.trim()?.takeIf { it.isNotEmpty() },
		description = metadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = metadata.subject.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		durationSeconds = metadata.duration?.takeIf { it > 0.0 },
		properties = properties.toStringProperties(),
		links = links.mapNotNull { it.toLink() },
		images = images.mapNotNull { it.toLink() }
	)

private fun BinderyPublicationDto.toManifest(): BinderyManifest =
	BinderyManifest(
		id = metadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		title = metadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
		author = metadata.author.firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
		published = metadata.published?.trim()?.takeIf { it.isNotEmpty() },
		description = metadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = metadata.subject.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		durationSeconds = metadata.duration?.takeIf { it > 0.0 },
		properties = properties.toStringProperties(),
		links = links.mapNotNull { it.toLink() },
		images = images.mapNotNull { it.toLink() },
		readingOrder = readingOrder.mapNotNull { it.toReadingOrderItem() }
	)

private fun BinderyResourceCatalogDto.toResourceCatalog(): BinderyResourceCatalog =
	BinderyResourceCatalog(
		title = metadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Resources",
		resources = resources.mapNotNull { it.toBookResource() }
	)

internal fun decodeBinderyCatalogJson(jsonText: String): BinderyCatalog =
	BinderyJson.decodeFromString<BinderyCatalogDto>(jsonText).toCatalog()

internal fun decodeBinderyManifestJson(jsonText: String): BinderyManifest =
	BinderyJson.decodeFromString<BinderyPublicationDto>(jsonText).toManifest()

internal fun decodeBinderyResourceCatalogJson(jsonText: String): BinderyResourceCatalog =
	BinderyJson.decodeFromString<BinderyResourceCatalogDto>(jsonText).toResourceCatalog()

private fun BinderyLinkDto.toReadingOrderItem(): BinderyReadingOrderItem? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	return BinderyReadingOrderItem(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() } ?: safeHref.substringAfterLast('/'),
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		durationSeconds = duration?.takeIf { it > 0.0 },
		sizeBytes = properties["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
	)
}

private fun BinderyLinkDto.toLink(): BinderyLink? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	return BinderyLink(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() },
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		rel = rel.toRelList(),
		properties = properties.toStringProperties(),
		images = images.mapNotNull { it.toLink() }
	)
}

private fun BinderyLinkDto.toBookResource(): BinderyBookResource? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val stringProperties = properties.toStringProperties()
	return BinderyBookResource(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() } ?: safeHref.substringAfterLast('/'),
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		kind = stringProperties.firstNonBlankValue("kind"),
		durationSeconds = duration?.takeIf { it > 0.0 },
		sizeBytes = stringProperties.firstNonBlankValue("size")?.toLongOrNull(),
		properties = stringProperties
	)
}

private fun Map<String, JsonElement>.toStringProperties(): Map<String, String> =
	mapNotNull { (key, value) ->
		(value as? JsonPrimitive)
			?.contentOrNull
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.let { key to it }
	}.toMap()

private fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { desiredKey ->
		entries.firstOrNull { (key, value) ->
			key.equals(desiredKey, ignoreCase = true) && value.isNotBlank()
		}?.value?.trim()
	}

private fun JsonElement?.toRelList(): List<String> =
	when (this) {
		null -> emptyList()
		is JsonPrimitive -> listOfNotNull(contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
		is JsonArray -> mapNotNull { element ->
			element.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
		}
		else -> emptyList()
	}

private fun io.ktor.client.request.HttpRequestBuilder.binderyJsonRequest(headers: Map<String, String>) {
	headers.forEach { (key, value) -> header(key, value) }
	contentType(ContentType.Application.Json)
}

internal fun binderyApiKeyHeaders(apiKey: String): Map<String, String> {
	val trimmed = apiKey.trim()
	return if (trimmed.isEmpty()) emptyMap() else mapOf("X-Api-Key" to trimmed)
}

internal fun binderyEndpoint(baseUrl: String, path: String): String =
	binderyEndpointFromNormalizedBase(
		normalizeBinderyOpdsBaseUrl(baseUrl),
		path
	)

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
	} else {
		"$baseUrl/$relativePath"
	}
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

private fun binderyHttpErrorMessage(
	operation: String,
	status: HttpStatusCode
): String =
	when (status) {
		HttpStatusCode.Unauthorized -> "$operation unauthorized. Check the Bindery API key."
		HttpStatusCode.Forbidden -> "$operation forbidden. Check the Bindery API key permissions."
		else -> "$operation returned HTTP ${status.value}"
	}

private fun encodeUrlPathSegment(value: String): String {
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
