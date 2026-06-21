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
import paige.navic.reader.WhispersyncSidecar
import paige.navic.reader.decodeWhispersyncSidecar
import paige.navic.reader.encodeWhispersyncSidecar
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

	suspend fun getCachedCatalog(path: String): Result<BinderyCatalog?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Catalog,
			path = path,
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

	suspend fun getCachedManifest(bookId: String): Result<BinderyManifest?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Manifest,
			path = bookId,
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

	suspend fun getCachedBookResources(bookId: String): Result<BinderyResourceCatalog?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Resources,
			path = bookId,
			decode = { json -> BinderyJson.decodeFromString<BinderyResourceCatalog>(json) }
		)

	suspend fun getAudiobookVersions(
		bookId: String,
		limit: Int = 100
	): Result<List<BinderyAudiobookVersion>> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookVersions,
			path = "book:${bookId.trim()}:limit:${limit.coerceIn(1, 500)}",
			fetch = { baseUrl, headers -> apiClient.fetchAudiobookVersions(baseUrl, headers, bookId, limit) },
			encode = { versions -> BinderyJson.encodeToString(versions) },
			decode = { json -> BinderyJson.decodeFromString<List<BinderyAudiobookVersion>>(json) }
		)

	suspend fun getCachedAudiobookVersions(
		bookId: String,
		limit: Int = 100
	): Result<List<BinderyAudiobookVersion>?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookVersions,
			path = "book:${bookId.trim()}:limit:${limit.coerceIn(1, 500)}",
			decode = { json -> BinderyJson.decodeFromString<List<BinderyAudiobookVersion>>(json) }
		)

	suspend fun getAudiobookDetail(audiobookId: String): Result<BinderyAudiobookVersion> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookDetail,
			path = audiobookId,
			fetch = { baseUrl, headers -> apiClient.fetchAudiobookVersion(baseUrl, headers, audiobookId) },
			encode = { version -> BinderyJson.encodeToString(version) },
			decode = { json -> BinderyJson.decodeFromString<BinderyAudiobookVersion>(json) }
		)

	suspend fun getCachedAudiobookDetail(audiobookId: String): Result<BinderyAudiobookVersion?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookDetail,
			path = audiobookId,
			decode = { json -> BinderyJson.decodeFromString<BinderyAudiobookVersion>(json) }
		)

	suspend fun getAudiobookManifest(audiobookId: String): Result<BinderyManifest> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookManifest,
			path = audiobookId,
			fetch = { baseUrl, headers -> apiClient.fetchAudiobookManifest(baseUrl, headers, audiobookId) },
			encode = { manifest -> BinderyJson.encodeToString(manifest) },
			decode = { json -> BinderyJson.decodeFromString<BinderyManifest>(json) }
		)

	suspend fun getCachedAudiobookManifest(audiobookId: String): Result<BinderyManifest?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookManifest,
			path = audiobookId,
			decode = { json -> BinderyJson.decodeFromString<BinderyManifest>(json) }
		)

	suspend fun getBookSync(bookId: String): Result<BinderyBookSync> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.BookSync,
			path = bookId,
			fetch = { baseUrl, headers -> apiClient.fetchBookSync(baseUrl, headers, bookId) },
			encode = { sync -> BinderyJson.encodeToString(sync) },
			decode = { json -> decodeBinderyBookSyncJson(json) }
		)

	suspend fun getCachedBookSync(bookId: String): Result<BinderyBookSync?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.BookSync,
			path = bookId,
			decode = { json -> decodeBinderyBookSyncJson(json) }
		)

	suspend fun getWhispersyncSidecar(path: String): Result<WhispersyncSidecar> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.WhispersyncSidecar,
			path = path,
			fetch = { baseUrl, headers ->
				decodeWhispersyncSidecar(apiClient.fetchWhispersyncSidecarJson(baseUrl, headers, path))
			},
			encode = ::encodeWhispersyncSidecar,
			decode = ::decodeWhispersyncSidecar
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

	suspend fun getCachedBookFindings(bookId: String): Result<BinderyCatalog?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.BookFindings,
			path = bookId,
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

	private suspend fun <T> getConfiguredCachedPayload(
		payloadType: String,
		path: String,
		decode: (String) -> T
	): Result<T?> =
		withConfiguredClientAvailability { baseUrl, _ ->
			val cachePath = path.trim()
			val cacheKey = binderyMetadataCacheKey(baseUrl, payloadType, cachePath)
			val cached = metadataCache.get(cacheKey)
				?: return@withConfiguredClientAvailability Result.success(null)
			runCatching { decode(cached.payloadJson) }
				.onFailure { cacheError ->
					Logger.w(TAG, "Bindery metadata cache decode failed", cacheError)
				}.fold(
					onSuccess = { cachedPayload -> Result.success(cachedPayload) },
					onFailure = { Result.success(null) }
				)
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
