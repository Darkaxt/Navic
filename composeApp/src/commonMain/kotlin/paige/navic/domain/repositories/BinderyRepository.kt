package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLQueryComponent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.reader.readerPublicationResourceLogLabel
import paige.navic.util.core.Logger
import kotlin.time.Clock

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

@Serializable
private data class BinderyProviderCoverCachePayload(
	val coverUrl: String? = null
)

class BinderyRepository(
	private val preferenceManager: PreferenceManager,
	private val apiClient: BinderyApiClient = KtorBinderyApiClient(),
	private val metadataCache: BinderyMetadataCache = NoOpBinderyMetadataCache,
	private val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
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
				hasFindings = catalog.hasNavigationPath("/opds/findings"),
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
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Catalog,
			path = path,
			fetch = { baseUrl, headers -> apiClient.fetchCatalog(baseUrl, headers, path) },
			encode = { catalog -> BinderyJson.encodeToString(catalog) },
			decode = { json -> BinderyJson.decodeFromString<BinderyCatalog>(json) }
		)

	suspend fun getManifest(bookId: String): Result<BinderyManifest> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Manifest,
			path = bookId,
			fetch = { baseUrl, headers -> apiClient.fetchManifest(baseUrl, headers, bookId) },
			encode = { manifest -> BinderyJson.encodeToString(manifest) },
			decode = { json -> BinderyJson.decodeFromString<BinderyManifest>(json) }
		)

	suspend fun getBookResources(bookId: String): Result<BinderyResourceCatalog> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Resources,
			path = bookId,
			fetch = { baseUrl, headers -> apiClient.fetchBookResources(baseUrl, headers, bookId) },
			encode = { resources -> BinderyJson.encodeToString(resources) },
			decode = { json -> BinderyJson.decodeFromString<BinderyResourceCatalog>(json) }
		)

	suspend fun getResourceBytes(path: String): Result<ByteArray> {
		val label = readerPublicationResourceLogLabel(path)
		Logger.i(TAG, "Bindery resource request path=$label")
		return withConfiguredClient { baseUrl, headers ->
			apiClient.fetchResourceBytes(baseUrl, headers, path).also { bytes ->
				Logger.i(TAG, "Bindery resource response path=$label bytes=${bytes.size}")
			}
		}.onFailure { error ->
			Logger.w(TAG, "Bindery resource request failed path=$label", error)
		}
	}

	suspend fun getReadingProgress(
		bookId: String,
		alias: String? = null
	): Result<BinderyReadingProgress> =
		withConfiguredClient { baseUrl, headers ->
			apiClient.fetchReadingProgress(baseUrl, headers, bookId, alias)
		}

	suspend fun putReadingProgress(progress: BinderyReadingProgress): Result<Unit> =
		withConfiguredClient { baseUrl, headers ->
			apiClient.putReadingProgress(baseUrl, headers, progress)
		}

	suspend fun getBookFindings(bookId: String): Result<BinderyCatalog> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.BookFindings,
			path = bookId,
			fetch = { baseUrl, headers -> apiClient.fetchBookFindings(baseUrl, headers, bookId) },
			encode = { catalog -> BinderyJson.encodeToString(catalog) },
			decode = { json -> BinderyJson.decodeFromString<BinderyCatalog>(json) }
		)

	suspend fun getFindingProviderCoverUrl(finding: BinderyFindingMetadata): Result<String?> {
		val provider = finding.providerKind ?: finding.provider
		val findingId = finding.findingId?.trim()?.takeIf { it.isNotEmpty() } ?: "<unknown>"
		if (!provider.isAudioBookBayProvider()) {
			Logger.i(
				TAG,
				"Bindery audiobook provider cover skipped finding=$findingId provider=${provider.orEmpty()} reason=unsupported-provider"
			)
			return Result.success(null)
		}
		val sourceUrl = finding.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }
		if (sourceUrl == null) {
			Logger.i(
				TAG,
				"Bindery audiobook provider cover skipped finding=$findingId provider=${provider.orEmpty()} reason=missing-source-url"
			)
			return Result.success(null)
		}
		return withConfiguredClientAvailability { baseUrl, _ ->
			val cacheKey = binderyMetadataCacheKey(
				baseUrl = baseUrl,
				payloadType = BinderyMetadataPayloadType.ProviderCover,
				path = sourceUrl
			)
			val cached = metadataCache.get(cacheKey)
			if (cached != null && isFresh(cached.updatedAtMillis)) {
				runCatching { BinderyJson.decodeFromString<BinderyProviderCoverCachePayload>(cached.payloadJson) }
					.onSuccess { payload ->
						Logger.i(
							TAG,
							"Bindery audiobook provider cover cache hit finding=$findingId source=${readerPublicationResourceLogLabel(sourceUrl)} cover=${payload.coverUrl?.let(::readerPublicationResourceLogLabel) ?: "<none>"}"
						)
						return@withConfiguredClientAvailability Result.success(payload.coverUrl)
					}
					.onFailure { cacheError ->
						Logger.w(TAG, "Bindery provider cover cache decode failed", cacheError)
					}
			}

			runCatching {
				val html = apiClient.fetchExternalText(sourceUrl)
				binderyAudioBookBayProviderCoverUrl(sourceUrl = sourceUrl, html = html)
			}.fold(
				onSuccess = { coverUrl ->
					metadataCache.put(
						BinderyMetadataCacheRecord(
							cacheKey = cacheKey,
							baseUrl = baseUrl,
							payloadType = BinderyMetadataPayloadType.ProviderCover,
							path = sourceUrl,
							payloadJson = BinderyJson.encodeToString(BinderyProviderCoverCachePayload(coverUrl)),
							updatedAtMillis = currentTimeMillis()
						)
					)
					Logger.i(
						TAG,
						"Bindery audiobook provider cover fetched finding=$findingId source=${readerPublicationResourceLogLabel(sourceUrl)} cover=${coverUrl?.let(::readerPublicationResourceLogLabel) ?: "<none>"}"
					)
					Result.success(coverUrl)
				},
				onFailure = { error ->
					metadataCache.put(
						BinderyMetadataCacheRecord(
							cacheKey = cacheKey,
							baseUrl = baseUrl,
							payloadType = BinderyMetadataPayloadType.ProviderCover,
							path = sourceUrl,
							payloadJson = BinderyJson.encodeToString(BinderyProviderCoverCachePayload(null)),
							updatedAtMillis = currentTimeMillis()
						)
					)
					Logger.w(
						TAG,
						"Bindery audiobook provider cover fetch failed finding=$findingId source=${readerPublicationResourceLogLabel(sourceUrl)}; cached fallback",
						error
					)
					Result.success(null)
				}
			)
		}
	}

	suspend fun performAction(path: String): Result<Unit> {
		val label = readerPublicationResourceLogLabel(path)
		Logger.i(TAG, "Bindery action request path=$label")
		return withConfiguredClient { baseUrl, headers ->
			apiClient.performAction(baseUrl, headers, path)
			metadataCache.clearBaseUrl(baseUrl)
			Logger.i(TAG, "Bindery action completed path=$label")
		}.onFailure { error ->
			Logger.w(TAG, "Bindery action failed path=$label", error)
		}
	}

	private suspend fun <T> withConfiguredCachedPayload(
		payloadType: String,
		path: String,
		fetch: suspend (baseUrl: String, headers: Map<String, String>) -> T,
		encode: (T) -> String,
		decode: (String) -> T
	): Result<T> =
		withConfiguredClientAvailability { baseUrl, headers ->
			val cachePath = path.trim()
			val cacheKey = binderyMetadataCacheKey(baseUrl, payloadType, cachePath)
			val cached = metadataCache.get(cacheKey)
			if (cached != null && isFresh(cached.updatedAtMillis)) {
				runCatching { decode(cached.payloadJson) }
					.onSuccess { cachedPayload ->
						return@withConfiguredClientAvailability Result.success(cachedPayload)
					}
					.onFailure { cacheError ->
						Logger.w(TAG, "Bindery metadata cache decode failed", cacheError)
					}
			}

			runCatching {
				fetch(baseUrl, headers)
			}.fold(
				onSuccess = { live ->
					metadataCache.put(
						BinderyMetadataCacheRecord(
							cacheKey = cacheKey,
							baseUrl = baseUrl,
							payloadType = payloadType,
							path = cachePath,
							payloadJson = encode(live),
							updatedAtMillis = currentTimeMillis()
						)
					)
					Logger.i(
						TAG,
						"Bindery metadata fetched type=$payloadType path=${readerPublicationResourceLogLabel(cachePath)}"
					)
					preferenceManager.markIntegrationServiceAvailable(IntegrationService.Bindery)
					Result.success(live)
				},
				onFailure = { error ->
					Logger.w(
						TAG,
						"Bindery OPDS request failed type=$payloadType path=${readerPublicationResourceLogLabel(cachePath)}",
						error
					)
					preferenceManager.markIntegrationServiceDown(IntegrationService.Bindery)
					if (cached != null) {
						runCatching { decode(cached.payloadJson) }
							.onFailure { cacheError ->
								Logger.w(TAG, "Bindery metadata cache decode failed", cacheError)
							}
					} else {
						Result.failure(error)
					}
				}
			)
		}

	private fun isFresh(updatedAtMillis: Long): Boolean =
		currentTimeMillis() - updatedAtMillis <= BINDERY_METADATA_CACHE_FRESH_MILLIS

	private suspend fun <T> withConfiguredClient(
		action: suspend (baseUrl: String, headers: Map<String, String>) -> T
	): Result<T> {
		return withConfiguredClientAvailability { baseUrl, headers ->
			runCatching {
				action(baseUrl, headers)
			}.onFailure { error ->
				Logger.w(TAG, "Bindery OPDS request failed", error)
			}.recordBinderyAvailability()
		}
	}

	private suspend fun <T> withConfiguredClientAvailability(
		action: suspend (baseUrl: String, headers: Map<String, String>) -> Result<T>
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
		return action(baseUrl, binderyApiKeyHeaders(apiKey))
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

	suspend fun fetchResourceBytes(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): ByteArray

	suspend fun fetchReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		alias: String? = null
	): BinderyReadingProgress

	suspend fun putReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		progress: BinderyReadingProgress
	)

	suspend fun fetchBookFindings(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyCatalog

	suspend fun fetchExternalText(url: String): String

	suspend fun performAction(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	)
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

	override suspend fun fetchResourceBytes(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): ByteArray {
		val safePath = path.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery resource path is required.")
		val response = client.get(binderyEndpoint(baseUrl, safePath)) {
			requestHeaders.forEach { (key, value) -> header(key, value) }
			accept(ContentType.Application.OctetStream)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS resource", response.status))
		}
		return response.body<ByteArray>()
	}

	override suspend fun fetchReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		alias: String?
	): BinderyReadingProgress {
		val path = binderyReadingProgressPath(bookId, alias)
		val response = client.get(binderyEndpoint(baseUrl, path)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery reading progress", response.status))
		}
		return response.body<BinderyReadingProgress>()
	}

	override suspend fun putReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		progress: BinderyReadingProgress
	) {
		val path = binderyReadingProgressPath(progress.bookId, progress.alias)
		val response = client.put(binderyEndpoint(baseUrl, path)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
			contentType(ContentType.Application.Json)
			setBody(progress)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery reading progress", response.status))
		}
	}

	override suspend fun fetchBookFindings(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyCatalog {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		return fetchCatalog(baseUrl, requestHeaders, "books/${encodeUrlPathSegment(safeBookId)}/findings")
	}

	override suspend fun fetchExternalText(url: String): String {
		val safeUrl = url.trim().takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
			?: throw IllegalStateException("Provider source URL must be absolute.")
		val response = client.get(safeUrl) {
			header("User-Agent", "Navic/1.0 provider-cover-resolver")
			accept(ContentType.Text.Html)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Provider source page", response.status))
		}
		return response.bodyAsText()
	}

	override suspend fun performAction(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) {
		val safePath = path.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery action path is required.")
		val response = client.post(binderyEndpoint(baseUrl, safePath)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS action", response.status))
		}
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
	val hasFindings: Boolean = false,
	val progressSyncSupported: Boolean = false,
	val paginationSupported: Boolean = false
)

@Serializable
data class BinderyReadingProgress(
	val bookId: String,
	val alias: String? = null,
	val kind: BinderyReadingProgressKind = BinderyReadingProgressKind.Ebook,
	val resourceHref: String? = null,
	val textHref: String? = null,
	val cfi: String? = null,
	val fragmentId: String? = null,
	val positionMs: Long? = null,
	val durationMs: Long? = null,
	val progressFraction: Double? = null,
	val updatedAt: String? = null
)

@Serializable
enum class BinderyReadingProgressKind {
	@SerialName("ebook")
	Ebook,

	@SerialName("audiobook")
	Audiobook,

	@SerialName("readaloud")
	Readaloud
}

@Serializable
data class BinderyCatalog(
	val title: String,
	val identifier: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val images: List<BinderyLink> = emptyList(),
	val links: List<BinderyLink> = emptyList(),
	val navigation: List<BinderyLink> = emptyList(),
	val publications: List<BinderyPublication> = emptyList(),
	val finding: BinderyFindingMetadata? = null
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

@Serializable
data class BinderyPublication(
	val id: String?,
	val title: String,
	val author: String? = null,
	val published: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val durationSeconds: Double? = null,
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val links: List<BinderyLink> = emptyList(),
	val images: List<BinderyLink> = emptyList(),
	val readingOrder: List<BinderyReadingOrderItem> = emptyList(),
	val finding: BinderyFindingMetadata? = null
)

@Serializable
data class BinderyFindingMetadata(
	val findingId: String? = null,
	val provider: String? = null,
	val providerKind: String? = null,
	val mediaType: String? = null,
	val format: String? = null,
	val language: String? = null,
	val author: String? = null,
	val bookTitleHint: String? = null,
	val edition: String? = null,
	val narrator: String? = null,
	val publisher: String? = null,
	val protocol: String? = null,
	val fileCount: Int? = null,
	val sizeBytes: Long? = null,
	val bitrateBps: Long? = null,
	val sampleRateHz: Long? = null,
	val availabilityStatus: String? = null,
	val availabilityReason: String? = null,
	val sourceUrl: String? = null,
	val coverUrl: String? = null,
	val publishedDate: String? = null,
	val uploadDate: String? = null,
	val providerComments: String? = null,
	val files: List<BinderyFindingFile> = emptyList(),
	val mappings: List<BinderyFindingMapping> = emptyList()
)

@Serializable
data class BinderyFindingFile(
	val name: String? = null,
	val href: String? = null,
	val format: String? = null,
	val language: String? = null,
	val sizeBytes: Long? = null,
	val durationSeconds: Double? = null,
	val bitrateBps: Long? = null,
	val sampleRateHz: Long? = null
)

@Serializable
data class BinderyFindingMapping(
	val id: String? = null,
	val bookId: String? = null,
	val bookTitle: String? = null,
	val authorName: String? = null,
	val confidence: Double? = null,
	val mediaType: String? = null,
	val targetLanguage: String? = null,
	val acquisitionStatus: String? = null,
	val acquisitionScope: String? = null,
	val selectedBytes: Long? = null,
	val bookFileId: String? = null,
	val bookFileFormat: String? = null,
	val bookFileSizeBytes: Long? = null,
	val sourceCatalogCandidateId: String? = null
)

@Serializable
data class BinderyManifest(
	val id: String?,
	val title: String,
	val author: String? = null,
	val published: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val durationSeconds: Double? = null,
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val links: List<BinderyLink> = emptyList(),
	val images: List<BinderyLink> = emptyList(),
	val readingOrder: List<BinderyReadingOrderItem> = emptyList()
)

@Serializable
data class BinderyReadingOrderItem(
	val href: String,
	val title: String,
	val type: String?,
	val durationSeconds: Double? = null,
	val sizeBytes: Long? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val metadata: BinderyResourceMetadata = BinderyResourceMetadata()
)

@Serializable
data class BinderyResourceMetadata(
	val resourceKey: String? = null,
	val relativePath: String? = null,
	val durationMs: Long? = null,
	val language: String? = null,
	val chapterLabel: String? = null,
	val sectionLabel: String? = null,
	val trackNumber: Int? = null,
	val discNumber: Int? = null,
	val narrator: String? = null,
	val author: String? = null,
	val editionSuffix: String? = null,
	val sourceProvider: String? = null,
	val audio: BinderyAudioMetadata? = null,
	val sourceRelease: BinderySourceReleaseMetadata? = null
)

@Serializable
data class BinderyAudioMetadata(
	val codec: String? = null,
	val bitrateKbps: Int? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val qualityLabel: String? = null,
	val qualityScore: Double? = null
)

@Serializable
data class BinderySourceReleaseMetadata(
	val provider: String? = null,
	val sourceUrl: String? = null,
	val narrator: String? = null,
	val readBy: String? = null,
	val edition: String? = null,
	val format: String? = null,
	val categories: List<String> = emptyList(),
	val keywords: List<String> = emptyList()
)

@Serializable
data class BinderyLink(
	val href: String,
	val title: String? = null,
	val type: String? = null,
	val rel: List<String> = emptyList(),
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val images: List<BinderyLink> = emptyList(),
	val links: List<BinderyLink> = emptyList()
)

@Serializable
data class BinderyAvailability(
	val owned: Boolean = false,
	val complete: Boolean = false,
	val ownedBooks: Int? = null,
	val missingBooks: Int? = null,
	val totalBooks: Int? = null,
	val formats: List<String> = emptyList(),
	val ownedFormats: List<String> = emptyList(),
	val ownedLanguages: List<String> = emptyList(),
	val ownedCombinations: List<BinderyAvailabilityCombination> = emptyList(),
	val languages: List<String> = emptyList(),
	val mode: String? = null
)

@Serializable
data class BinderyAvailabilityCombination(
	val format: String,
	val language: String
)

@Serializable
data class BinderyResourceCatalog(
	val title: String,
	val resources: List<BinderyBookResource> = emptyList()
)

@Serializable
data class BinderyBookResource(
	val href: String,
	val title: String,
	val type: String? = null,
	val kind: String? = null,
	val durationSeconds: Double? = null,
	val sizeBytes: Long? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val metadata: BinderyResourceMetadata = BinderyResourceMetadata()
)

@Serializable
data class BinderyPropertyBag(
	val values: Map<String, BinderyPropertyValue> = emptyMap()
) {
	operator fun get(key: String): BinderyPropertyValue? =
		values.entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value

	fun string(key: String): String? =
		(this[key] as? BinderyPropertyValue.StringValue)?.value

	fun number(key: String): Double? =
		(this[key] as? BinderyPropertyValue.NumberValue)?.value

	fun boolean(key: String): Boolean? =
		(this[key] as? BinderyPropertyValue.BooleanValue)?.value

	fun array(key: String): List<BinderyPropertyValue> =
		(this[key] as? BinderyPropertyValue.ArrayValue)?.values.orEmpty()

	fun objectBag(key: String): BinderyPropertyBag? =
		(this[key] as? BinderyPropertyValue.ObjectValue)?.let { BinderyPropertyBag(it.values) }
}

@Serializable
sealed interface BinderyPropertyValue {
	@Serializable
	@SerialName("string")
	data class StringValue(val value: String) : BinderyPropertyValue

	@Serializable
	@SerialName("number")
	data class NumberValue(val value: Double, val raw: String) : BinderyPropertyValue

	@Serializable
	@SerialName("boolean")
	data class BooleanValue(val value: Boolean) : BinderyPropertyValue

	@Serializable
	@SerialName("array")
	data class ArrayValue(val values: List<BinderyPropertyValue>) : BinderyPropertyValue

	@Serializable
	@SerialName("object")
	data class ObjectValue(val values: Map<String, BinderyPropertyValue>) : BinderyPropertyValue
}

class BinderyApiException(
	val status: HttpStatusCode,
	message: String
) : IllegalStateException(message)

@Serializable
private data class BinderyCatalogDto(
	val metadata: BinderyMetadataDto? = null,
	val properties: Map<String, JsonElement>? = null,
	val images: List<BinderyLinkDto>? = null,
	val links: List<BinderyLinkDto>? = null,
	val navigation: List<BinderyLinkDto>? = null,
	val publications: List<BinderyPublicationDto>? = null
)

@Serializable
private data class BinderyResourceCatalogDto(
	val metadata: BinderyMetadataDto? = null,
	val resources: List<BinderyLinkDto>? = null
)

@Serializable
private data class BinderyPublicationDto(
	val metadata: BinderyMetadataDto? = null,
	val properties: Map<String, JsonElement>? = null,
	val links: List<BinderyLinkDto>? = null,
	val images: List<BinderyLinkDto>? = null,
	@SerialName("readingOrder") val readingOrder: List<BinderyLinkDto>? = null
)

@Serializable
private data class BinderyMetadataDto(
	val title: String? = null,
	val identifier: String? = null,
	val sortAs: String? = null,
	val author: List<BinderyContributorDto>? = null,
	val published: String? = null,
	val modified: String? = null,
	val description: String? = null,
	val subject: List<String>? = null,
	val duration: Double? = null,
	val properties: Map<String, JsonElement>? = null
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
	val properties: Map<String, JsonElement>? = null,
	val images: List<BinderyLinkDto>? = null,
	val links: List<BinderyLinkDto>? = null,
	val duration: Double? = null
)

private fun BinderyCatalogDto.toCatalog(): BinderyCatalog {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	val decodedProperties = safeMetadata.properties.orEmpty() + properties.orEmpty()
	return BinderyCatalog(
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Bindery",
		identifier = safeMetadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		description = safeMetadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = safeMetadata.subject.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		availability = decodedProperties.toAvailability(),
		properties = decodedProperties.toStringProperties(),
		propertyValues = decodedProperties.toPropertyBag(),
		images = images.orEmpty().mapNotNull { it.toLink() },
		links = links.orEmpty().mapNotNull { it.toLink() },
		navigation = navigation.orEmpty().mapNotNull { it.toLink() },
		publications = publications.orEmpty().map { it.toPublication() },
		finding = decodedProperties.toFindingMetadata()
	)
}

private fun BinderyPublicationDto.toPublication(): BinderyPublication {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	val decodedProperties = safeMetadata.properties.orEmpty() + properties.orEmpty()
	return BinderyPublication(
		id = safeMetadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
		author = safeMetadata.author.orEmpty().firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
		published = safeMetadata.published?.trim()?.takeIf { it.isNotEmpty() },
		description = safeMetadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = safeMetadata.subject.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		durationSeconds = safeMetadata.duration?.takeIf { it > 0.0 },
		availability = decodedProperties.toAvailability(),
		properties = decodedProperties.toStringProperties(),
		propertyValues = decodedProperties.toPropertyBag(),
		links = links.orEmpty().mapNotNull { it.toLink() },
		images = images.orEmpty().mapNotNull { it.toLink() },
		readingOrder = readingOrder.orEmpty().mapNotNull { it.toReadingOrderItem() },
		finding = decodedProperties.toFindingMetadata()
	)
}

private fun BinderyPublicationDto.toManifest(): BinderyManifest {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	val decodedProperties = safeMetadata.properties.orEmpty() + properties.orEmpty()
	return BinderyManifest(
		id = safeMetadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
		author = safeMetadata.author.orEmpty().firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
		published = safeMetadata.published?.trim()?.takeIf { it.isNotEmpty() },
		description = safeMetadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = safeMetadata.subject.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		durationSeconds = safeMetadata.duration?.takeIf { it > 0.0 },
		availability = decodedProperties.toAvailability(),
		properties = decodedProperties.toStringProperties(),
		propertyValues = decodedProperties.toPropertyBag(),
		links = links.orEmpty().mapNotNull { it.toLink() },
		images = images.orEmpty().mapNotNull { it.toLink() },
		readingOrder = readingOrder.orEmpty().mapNotNull { it.toReadingOrderItem() }
	)
}

private fun BinderyResourceCatalogDto.toResourceCatalog(): BinderyResourceCatalog {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	return BinderyResourceCatalog(
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Resources",
		resources = resources.orEmpty().mapNotNull { it.toBookResource() }
	)
}

internal fun decodeBinderyCatalogJson(jsonText: String): BinderyCatalog =
	BinderyJson.decodeFromString<BinderyCatalogDto>(jsonText).toCatalog()

internal fun decodeBinderyManifestJson(jsonText: String): BinderyManifest =
	BinderyJson.decodeFromString<BinderyPublicationDto>(jsonText).toManifest()

internal fun decodeBinderyResourceCatalogJson(jsonText: String): BinderyResourceCatalog =
	BinderyJson.decodeFromString<BinderyResourceCatalogDto>(jsonText).toResourceCatalog()

private fun BinderyLinkDto.toReadingOrderItem(): BinderyReadingOrderItem? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeProperties = properties.orEmpty()
	val stringProperties = safeProperties.toStringProperties()
	return BinderyReadingOrderItem(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() } ?: safeHref.substringAfterLast('/'),
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		durationSeconds = duration?.takeIf { it > 0.0 },
		sizeBytes = stringProperties.firstNonBlankValue("size")?.toLongOrNull(),
		properties = stringProperties,
		propertyValues = safeProperties.toPropertyBag(),
		metadata = safeProperties.toResourceMetadata()
	)
}

private fun BinderyLinkDto.toLink(): BinderyLink? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeProperties = properties.orEmpty()
	return BinderyLink(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() },
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		rel = rel.toRelList(),
		availability = safeProperties.toAvailability(),
		properties = safeProperties.toStringProperties(),
		propertyValues = safeProperties.toPropertyBag(),
		images = images.orEmpty().mapNotNull { it.toLink() },
		links = links.orEmpty().mapNotNull { it.toLink() }
	)
}

private fun BinderyLinkDto.toBookResource(): BinderyBookResource? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeProperties = properties.orEmpty()
	val stringProperties = safeProperties.toStringProperties()
	return BinderyBookResource(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() } ?: safeHref.substringAfterLast('/'),
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		kind = stringProperties.firstNonBlankValue("kind"),
		durationSeconds = duration?.takeIf { it > 0.0 },
		sizeBytes = stringProperties.firstNonBlankValue("size")?.toLongOrNull(),
		properties = stringProperties,
		propertyValues = safeProperties.toPropertyBag(),
		metadata = safeProperties.toResourceMetadata()
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

private fun Map<String, JsonElement>.toPropertyBag(): BinderyPropertyBag =
	BinderyPropertyBag(
		mapNotNull { (key, value) ->
			if (!value.shouldKeepPropertyBagEntry(key)) {
				null
			} else {
				value.toBinderyPropertyValue()?.let { key to it }
			}
		}.toMap()
	)

private fun JsonElement.shouldKeepPropertyBagEntry(key: String): Boolean =
	if (key.equals("audio", ignoreCase = true) && this is JsonObject) {
		toAudioMetadata(emptyMap()) != null
	} else {
		true
	}

private fun JsonElement.toBinderyPropertyValue(): BinderyPropertyValue? =
	when (this) {
		is JsonObject -> BinderyPropertyValue.ObjectValue(
			mapNotNull { (key, value) ->
				value.toBinderyPropertyValue()?.let { key to it }
			}.toMap()
		)
		is JsonArray -> BinderyPropertyValue.ArrayValue(mapNotNull { it.toBinderyPropertyValue() })
		is JsonPrimitive -> {
			val content = contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
			when {
				this.isString -> BinderyPropertyValue.StringValue(content)
				content.equals("true", ignoreCase = true) || content.equals("false", ignoreCase = true) ->
					BinderyPropertyValue.BooleanValue(content.toBooleanStrict())
				content.toDoubleOrNull() != null -> BinderyPropertyValue.NumberValue(content.toDouble(), content)
				else -> BinderyPropertyValue.StringValue(content)
			}
		}
	}

private fun Map<String, JsonElement>.toResourceMetadata(): BinderyResourceMetadata {
	val audioObject = jsonObject("audio")
	val sourceReleaseObject = jsonObject("sourceRelease")
	return BinderyResourceMetadata(
		resourceKey = stringValue("resourceKey"),
		relativePath = stringValue("relativePath"),
		durationMs = longValue("durationMs") ?: longValue("duration_ms"),
		language = stringValue("language"),
		chapterLabel = stringValue("chapterLabel"),
		sectionLabel = stringValue("sectionLabel"),
		trackNumber = intValue("trackNumber"),
		discNumber = intValue("discNumber"),
		narrator = stringValue("narrator"),
		author = stringValue("author"),
		editionSuffix = stringValue("editionSuffix"),
		sourceProvider = stringValue("sourceProvider") ?: stringValue("provider"),
		audio = audioObject.toAudioMetadata(this),
		sourceRelease = sourceReleaseObject?.toSourceReleaseMetadata()
	)
}

private fun JsonObject?.toAudioMetadata(fallback: Map<String, JsonElement>): BinderyAudioMetadata? {
	val audio = BinderyAudioMetadata(
		codec = this?.stringValue("codec") ?: fallback.stringValue("codec"),
		bitrateKbps = (this?.intValue("bitrateKbps") ?: fallback.intValue("bitrateKbps"))?.takeIf { it > 0 },
		sampleRateHz = (this?.longValue("sampleRateHz") ?: fallback.longValue("sampleRateHz"))?.takeIf { it > 0L },
		channels = (this?.intValue("channels") ?: fallback.intValue("channels"))?.takeIf { it > 0 },
		qualityLabel = this?.stringValue("qualityLabel") ?: fallback.stringValue("qualityLabel"),
		qualityScore = this?.doubleValue("qualityScore") ?: fallback.doubleValue("qualityScore")
	)
	return audio.takeIf(BinderyAudioMetadata::hasContent)
}

private fun JsonObject.toSourceReleaseMetadata(): BinderySourceReleaseMetadata? {
	val release = BinderySourceReleaseMetadata(
		provider = stringValue("provider"),
		sourceUrl = stringValue("sourceUrl"),
		narrator = stringValue("narrator"),
		readBy = stringValue("readBy"),
		edition = stringValue("edition"),
		format = stringValue("format"),
		categories = stringList("categories"),
		keywords = stringList("keywords")
	)
	return release.takeIf(BinderySourceReleaseMetadata::hasContent)
}

private fun BinderyAudioMetadata.hasContent(): Boolean =
	codec != null ||
		bitrateKbps != null ||
		sampleRateHz != null ||
		channels != null ||
		qualityLabel != null ||
		qualityScore != null

private fun BinderySourceReleaseMetadata.hasContent(): Boolean =
	listOf(provider, sourceUrl, narrator, readBy, edition, format).any { !it.isNullOrBlank() } ||
		categories.isNotEmpty() ||
		keywords.isNotEmpty()

private fun Map<String, JsonElement>.toFindingMetadata(): BinderyFindingMetadata? {
	val files = jsonArray("files").mapNotNull { (it as? JsonObject)?.toFindingFile() }
	val mappings = jsonArray("mappings").mapNotNull { (it as? JsonObject)?.toFindingMapping() }
	val finding = BinderyFindingMetadata(
		findingId = stringValue("findingId") ?: stringValue("id"),
		provider = stringValue("provider"),
		providerKind = stringValue("providerKind"),
		mediaType = stringValue("mediaType") ?: stringValue("kind"),
		format = stringValue("format"),
		language = stringValue("language"),
		author = stringValue("author"),
		bookTitleHint = stringValue("bookTitleHint") ?: stringValue("bookTitle"),
		edition = stringValue("edition") ?: stringValue("version"),
		narrator = stringValue("narrator"),
		publisher = stringValue("publisher"),
		protocol = stringValue("protocol"),
		fileCount = intValue("fileCount"),
		sizeBytes = longValue("sizeBytes") ?: longValue("size") ?: longValue("selectedBytes"),
		bitrateBps = longValue("bitrateBps")?.takeIf { it > 0L },
		sampleRateHz = longValue("sampleRateHz")?.takeIf { it > 0L },
		availabilityStatus = stringValue("availabilityStatus"),
		availabilityReason = stringValue("availabilityReason"),
		sourceUrl = stringValue("sourceUrl") ?: stringValue("downloadUrl"),
		coverUrl = stringValue("coverUrl") ?: stringValue("image") ?: stringValue("cover"),
		publishedDate = stringValue("publishedDate"),
		uploadDate = stringValue("uploadDate"),
		providerComments = stringValue("providerComments") ?: stringValue("providerNotes"),
		files = files,
		mappings = mappings
	)
	return finding.takeIf(BinderyFindingMetadata::hasContent)
}

private fun JsonObject.toFindingFile(): BinderyFindingFile? {
	val file = BinderyFindingFile(
		name = stringValue("name")
			?: stringValue("title")
			?: stringValue("displayName")
			?: stringValue("relativePath")
			?: stringValue("path")?.substringAfterLast('/')?.substringAfterLast('\\'),
		href = stringValue("href") ?: stringValue("url"),
		format = stringValue("format") ?: stringValue("extension"),
		language = stringValue("language"),
		sizeBytes = (longValue("sizeBytes") ?: longValue("size"))?.takeIf { it > 0L },
		durationSeconds = (doubleValue("durationSeconds")
			?: doubleValue("duration")
			?: longValue("durationMs")?.let { it.toDouble() / 1000.0 })?.takeIf { it > 0.0 },
		bitrateBps = longValue("bitrateBps")?.takeIf { it > 0L },
		sampleRateHz = longValue("sampleRateHz")?.takeIf { it > 0L }
	)
	return file.takeIf(BinderyFindingFile::hasContent)
}

private fun BinderyFindingFile.hasContent(): Boolean =
	listOf(name, href, format, language).any { !it.isNullOrBlank() } ||
		sizeBytes != null ||
		durationSeconds != null ||
		bitrateBps != null ||
		sampleRateHz != null

private fun JsonObject.toFindingMapping(): BinderyFindingMapping =
	BinderyFindingMapping(
		id = stringValue("id"),
		bookId = stringValue("bookId"),
		bookTitle = stringValue("bookTitle") ?: stringValue("title"),
		authorName = stringValue("authorName") ?: stringValue("author"),
		confidence = doubleValue("confidence"),
		mediaType = stringValue("mediaType"),
		targetLanguage = stringValue("targetLanguage") ?: stringValue("language"),
		acquisitionStatus = stringValue("acquisitionStatus"),
		acquisitionScope = stringValue("acquisitionScope"),
		selectedBytes = longValue("selectedBytes"),
		bookFileId = stringValue("bookFileId")?.takeUnless { it == "0" },
		bookFileFormat = stringValue("bookFileFormat"),
		bookFileSizeBytes = longValue("bookFileSizeBytes"),
		sourceCatalogCandidateId = stringValue("sourceCatalogCandidateId")?.takeUnless { it == "0" }
	)

private fun BinderyFindingMetadata.hasContent(): Boolean =
	listOf(
		findingId,
		provider,
		providerKind,
		mediaType,
		format,
		language,
		author,
		bookTitleHint,
		edition,
		narrator,
		publisher,
		protocol,
		availabilityStatus,
		availabilityReason,
		sourceUrl,
		coverUrl,
		publishedDate,
		uploadDate,
		providerComments
	).any { !it.isNullOrBlank() } ||
		fileCount != null ||
		sizeBytes != null ||
		bitrateBps != null ||
		sampleRateHz != null ||
		files.isNotEmpty() ||
		mappings.isNotEmpty()

private fun Map<String, JsonElement>.toAvailability(): BinderyAvailability? {
	val value = this["availability"] as? JsonObject ?: return null
	return BinderyAvailability(
		owned = value.booleanValue("owned") ?: false,
		complete = value.booleanValue("complete") ?: false,
		ownedBooks = value.intValue("ownedBooks"),
		missingBooks = value.intValue("missingBooks"),
		totalBooks = value.intValue("totalBooks"),
		formats = value.stringList("formats"),
		ownedFormats = value.stringList("ownedFormats"),
		ownedLanguages = value.stringList("ownedLanguages"),
		ownedCombinations = value.objectList("ownedCombinations")
			.mapNotNull(JsonObject::toAvailabilityCombination),
		languages = value.stringList("languages"),
		mode = value.stringValue("mode")
	)
}

private fun Map<String, JsonElement>.jsonArray(key: String): JsonArray =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value as? JsonArray
		?: JsonArray(emptyList())

private fun Map<String, JsonElement>.jsonObject(key: String): JsonObject? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value as? JsonObject

private fun Map<String, JsonElement>.stringValue(key: String): String? =
	(entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value as? JsonPrimitive)
		?.contentOrNull
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun Map<String, JsonElement>.intValue(key: String): Int? =
	stringValue(key)?.toIntOrNull()

private fun Map<String, JsonElement>.longValue(key: String): Long? =
	stringValue(key)?.toLongOrNull()

private fun Map<String, JsonElement>.doubleValue(key: String): Double? =
	stringValue(key)?.toDoubleOrNull()

private fun JsonObject.stringValue(key: String): String? =
	(get(key) as? JsonPrimitive)
		?.contentOrNull
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun JsonObject.booleanValue(key: String): Boolean? =
	stringValue(key)?.toBooleanStrictOrNull()

private fun JsonObject.intValue(key: String): Int? =
	stringValue(key)?.toIntOrNull()

private fun JsonObject.longValue(key: String): Long? =
	stringValue(key)?.toLongOrNull()

private fun JsonObject.doubleValue(key: String): Double? =
	stringValue(key)?.toDoubleOrNull()

private fun JsonObject.stringList(key: String): List<String> =
	when (val value = get(key)) {
		is JsonArray -> value.mapNotNull { element ->
			(element as? JsonPrimitive)
				?.contentOrNull
				?.trim()
				?.takeIf { it.isNotEmpty() }
		}
		is JsonPrimitive -> value.contentOrNull
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.split(',')
			?.mapNotNull { item -> item.trim().takeIf(String::isNotEmpty) }
			.orEmpty()
		else -> emptyList()
	}

private fun JsonObject.objectList(key: String): List<JsonObject> =
	(get(key) as? JsonArray)
		?.mapNotNull { element -> element as? JsonObject }
		.orEmpty()

private fun JsonObject.toAvailabilityCombination(): BinderyAvailabilityCombination? {
	val format = stringValue("format") ?: return null
	val language = stringValue("language") ?: return null
	return BinderyAvailabilityCombination(
		format = format,
		language = language
	)
}

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

internal fun binderyRequestHeadersForUrl(
	baseUrl: String,
	url: String?,
	requestHeaders: Map<String, String>
): Map<String, String> {
	val imageOrigin = url?.httpUrlOriginOrNull() ?: return emptyMap()
	val binderyOrigin = runCatching { binderyOrigin(normalizeBinderyOpdsBaseUrl(baseUrl)) }.getOrNull()
		?.lowercase()
		?: return emptyMap()
	return if (imageOrigin == binderyOrigin) requestHeaders else emptyMap()
}

internal fun binderyAudioBookBayProviderCoverUrl(
	sourceUrl: String,
	html: String
): String? {
	val candidates = (
		AudioBookBayMetaImageRegexes.flatMap { regex ->
			regex.findAll(html).map { match -> match.groupValues[1] }
		} +
			AudioBookBayImageSrcRegex.findAll(html).map { match -> match.groupValues[1] }
		)
		.mapNotNull { candidate -> binderyAbsoluteProviderImageUrl(sourceUrl, candidate) }
		.distinct()
	return candidates.firstOrNull(String::isAudioBookBayPrimaryCoverUrl)
		?: candidates.firstOrNull(String::isLikelyProviderCoverImageUrl)
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

private fun binderyReadingProgressPath(bookId: String, alias: String?): String {
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

private val AudioBookBayMetaImageRegexes = listOf(
	Regex(
		"""<meta\b[^>]*(?:property|name)\s*=\s*["'](?:og:image|twitter:image)["'][^>]*content\s*=\s*["']([^"']+)["'][^>]*>""",
		RegexOption.IGNORE_CASE
	),
	Regex(
		"""<meta\b[^>]*content\s*=\s*["']([^"']+)["'][^>]*(?:property|name)\s*=\s*["'](?:og:image|twitter:image)["'][^>]*>""",
		RegexOption.IGNORE_CASE
	)
)

private val AudioBookBayImageSrcRegex =
	Regex("""<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

private fun String?.isAudioBookBayProvider(): Boolean =
	this?.trim()?.lowercase() in setOf("audiobookbay", "audio book bay", "abb")

private fun String.httpUrlOriginOrNull(): String? {
	val trimmed = trim()
	val schemeSeparator = trimmed.indexOf("://")
	if (schemeSeparator <= 0) return null
	val scheme = trimmed.substring(0, schemeSeparator).lowercase()
	if (scheme != "http" && scheme != "https") return null
	val afterScheme = trimmed.drop(schemeSeparator + 3)
	val authority = afterScheme.takeWhile { it != '/' }.takeIf { it.isNotBlank() } ?: return null
	return "$scheme://${authority.lowercase()}"
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

private fun String.isAudioBookBayPrimaryCoverUrl(): Boolean {
	val normalized = lowercase()
	return "bayimg.com/" in normalized && normalized.hasProviderImageExtension()
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
