package paige.navic.domain.repositories

import paige.navic.data.remote.bindery.*

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.models.CachedPayload
import paige.navic.domain.models.CachedPayloadSource
import paige.navic.domain.models.OptionalIntegrationFailureKind
import paige.navic.domain.models.OptionalIntegrationHttpFailure
import paige.navic.domain.models.OptionalIntegrationResult
import paige.navic.domain.models.optionalIntegrationFailure
import paige.navic.domain.models.optionalIntegrationResult
import paige.navic.domain.models.optionalIntegrationUnavailable
import paige.navic.reader.*
import paige.navic.util.core.Logger
import kotlin.time.Clock

private const val TAG = "BinderyRepository"
private val BinderyWordSyncCacheMutationMutex = Mutex()
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
				when ((error as? OptionalIntegrationHttpFailure)?.statusCode) {
					401 -> {
						preferenceManager.markIntegrationServiceAvailable(IntegrationService.Bindery)
						BinderyConnectionResult.Unauthorized
					}
					403 -> {
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

	suspend fun getCatalog(path: String, forceRefresh: Boolean = false): Result<BinderyCatalog> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Catalog,
			path = path,
			forceRefresh = forceRefresh,
			fetch = { baseUrl, headers -> apiClient.fetchCatalog(baseUrl, headers, path) },
			encode = { catalog -> BinderyJson.encodeToString(catalog) },
			decode = { json -> BinderyJson.decodeFromString<BinderyCatalog>(json) }
		)

	suspend fun getCatalogOptional(
		path: String,
		forceRefresh: Boolean = false
	): OptionalIntegrationResult<BinderyCatalog> {
		if (!preferenceManager.binderyEnabled) {
			return optionalIntegrationUnavailable(
				kind = OptionalIntegrationFailureKind.Disabled,
				message = "Bindery is disabled."
			)
		}
		val urlError = binderyOpdsBaseUrlConfigurationError(preferenceManager.binderyOpdsBaseUrl)
		if (urlError != null) {
			return optionalIntegrationUnavailable(
				kind = OptionalIntegrationFailureKind.Misconfigured,
				message = urlError
			)
		}
		if (preferenceManager.binderyApiKey.trim().isEmpty()) {
			return optionalIntegrationUnavailable(
				kind = OptionalIntegrationFailureKind.Misconfigured,
				message = BINDERY_API_KEY_REQUIRED_MESSAGE
			)
		}

		return withConfiguredCachedPayloadWithSource(
			payloadType = BinderyMetadataPayloadType.Catalog,
			path = path,
			forceRefresh = forceRefresh,
			fetch = { baseUrl, headers -> apiClient.fetchCatalog(baseUrl, headers, path) },
			encode = { catalog -> BinderyJson.encodeToString(catalog) },
			decode = { json -> BinderyJson.decodeFromString<BinderyCatalog>(json) }
		).fold(
			onSuccess = { payload ->
				optionalIntegrationResult(
					result = Result.success(payload.data),
					staleFailure = payload.staleFailure,
					isEmpty = BinderyCatalog::isOptionalIntegrationEmpty
				)
			},
			onFailure = { error ->
				optionalIntegrationResult(
					result = Result.failure(error),
					isEmpty = BinderyCatalog::isOptionalIntegrationEmpty
				)
			}
		)
	}

	suspend fun getCachedCatalog(path: String): Result<BinderyCatalog?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Catalog,
			path = path,
			decode = { json -> BinderyJson.decodeFromString<BinderyCatalog>(json) }
		)

	suspend fun getManifest(bookId: String, forceRefresh: Boolean = false): Result<BinderyManifest> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Manifest,
			path = bookId,
			forceRefresh = forceRefresh,
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

	suspend fun getBookResources(
		bookId: String,
		forceRefresh: Boolean = false
	): Result<BinderyResourceCatalog> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.Resources,
			path = bookId,
			forceRefresh = forceRefresh,
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
		limit: Int = 100,
		forceRefresh: Boolean = false
	): Result<List<BinderyAudiobookVersion>> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookVersions,
			path = "book:${bookId.trim()}:limit:${limit.coerceIn(1, 500)}",
			forceRefresh = forceRefresh,
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

	suspend fun getAudiobookDetail(
		audiobookId: String,
		forceRefresh: Boolean = false
	): Result<BinderyAudiobookVersion> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookDetail,
			path = audiobookId,
			forceRefresh = forceRefresh,
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

	suspend fun getAudiobookManifest(
		audiobookId: String,
		forceRefresh: Boolean = false
	): Result<BinderyManifest> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookManifest,
			path = audiobookId,
			forceRefresh = forceRefresh,
			fetch = { baseUrl, headers -> apiClient.fetchAudiobookManifest(baseUrl, headers, audiobookId) },
			encode = { manifest -> BinderyJson.encodeToString(manifest) },
			decode = { json -> BinderyJson.decodeFromString<BinderyManifest>(json) }
		)

	suspend fun getAudiobookManifestPath(
		path: String,
		forceRefresh: Boolean = false
	): Result<BinderyManifest> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookManifest,
			path = path,
			forceRefresh = forceRefresh,
			fetch = { baseUrl, headers -> apiClient.fetchAudiobookManifestPath(baseUrl, headers, path) },
			encode = { manifest -> BinderyJson.encodeToString(manifest) },
			decode = { json -> BinderyJson.decodeFromString<BinderyManifest>(json) }
		)

	suspend fun getWhispersyncAudiobookManifest(
		bookId: String?,
		audiobookId: String?,
		audiobookBookFileId: String,
		audiobookManifestHref: String?
	): Result<BinderyManifest> {
		val manifestPath = audiobookManifestHref?.trim()?.takeIf { it.isNotEmpty() }
		if (manifestPath != null) return getAudiobookManifestPath(manifestPath)

		val directAudiobookId = audiobookId?.trim()?.takeIf { it.isNotEmpty() }
		if (directAudiobookId != null) return getAudiobookManifest(directAudiobookId)

		val normalizedBookId = bookId?.trim()?.takeIf { it.isNotEmpty() }
		val normalizedBookFileId = audiobookBookFileId.trim().takeIf { it.isNotEmpty() }
		if (normalizedBookId == null || normalizedBookFileId == null) {
			return Result.failure(
				IllegalStateException("Whispersync sidecar did not expose an audiobook manifest identity.")
			)
		}

		return getAudiobookVersions(normalizedBookId).fold(
			onSuccess = { versions ->
				val version = versions.firstOrNull { candidate ->
					candidate.bookId?.toString() == normalizedBookId &&
						candidate.bookFileId?.toString() == normalizedBookFileId
				} ?: versions.firstOrNull { candidate ->
					candidate.bookFileId?.toString() == normalizedBookFileId
				}
				val versionId = version?.id?.toString()
				if (versionId == null) {
					Result.failure(
						IllegalStateException(
							"Whispersync audiobook bookFileId=$normalizedBookFileId was not found for book=$normalizedBookId."
						)
					)
				} else {
					getAudiobookManifest(versionId)
				}
			},
			onFailure = { error -> Result.failure(error) }
		)
	}

	suspend fun getCachedAudiobookManifest(audiobookId: String): Result<BinderyManifest?> =
		getConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.AudiobookManifest,
			path = audiobookId,
			decode = { json -> BinderyJson.decodeFromString<BinderyManifest>(json) }
		)

	suspend fun getBookSync(bookId: String, forceRefresh: Boolean = false): Result<BinderyBookSync> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.BookSync,
			path = bookId,
			forceRefresh = forceRefresh,
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

	suspend fun getWhispersyncSidecar(
		path: String,
		forceRefresh: Boolean = false
	): Result<WhispersyncSidecar> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.WhispersyncSidecar,
			path = path,
			forceRefresh = forceRefresh,
			fetch = { baseUrl, headers ->
				decodeWhispersyncSidecar(apiClient.fetchWhispersyncSidecarJson(baseUrl, headers, path))
			},
			encode = ::encodeWhispersyncSidecar,
			decode = ::decodeWhispersyncSidecar,
			acceptCached = ::acceptCachedWhispersyncSidecar
		)

	suspend fun getWordSyncIndex(
		identity: BinderyWhispersyncIdentity,
		discovery: BinderyWordSyncDiscovery,
		forceRefresh: Boolean = false
	): Result<WordSyncIndex> {
		val indexHref = runCatching { discovery.requiredWordSyncIndexHref() }
			.getOrElse { return Result.failure(it) }
		return withConfiguredWordSyncPayload(
			payloadType = BinderyMetadataPayloadType.WordSyncIndex,
			forceRefresh = forceRefresh,
			route = { baseUrl -> binderyWordSyncIndexRoute(baseUrl, identity, indexHref) },
			fetch = { baseUrl, headers ->
				apiClient.fetchWordSyncIndexJson(baseUrl, headers, identity, indexHref)
			},
			decode = { json ->
				decodeWordSyncIndex(json, identity).also(discovery::validateWordSyncIndex)
			},
			wordSyncIdentity = identity,
			advancesWordSyncGeneration = true,
			wordSyncGeneratedAt = WordSyncIndex::generatedAt
		)
	}

	suspend fun getWordSyncChapter(
		identity: BinderyWhispersyncIdentity,
		chapter: WordSyncChapterSummary,
		forceRefresh: Boolean = false
	): Result<WordSyncChapter> {
		val chapterHref = chapter.opdsHref?.takeIf { it.isNotBlank() }
			?: chapter.href.takeIf { it.isNotBlank() }
			?: return Result.failure(IllegalArgumentException("Bindery WordSync chapter route is required."))
		return withConfiguredWordSyncPayload(
			payloadType = BinderyMetadataPayloadType.WordSyncChapter,
			forceRefresh = forceRefresh,
			route = { baseUrl ->
				binderyWordSyncChapterRoute(
					baseUrl = baseUrl,
					identity = identity,
					chapterKey = chapter.chapterKey,
					advertisedHref = chapterHref
				)
			},
			fetch = { baseUrl, headers ->
				apiClient.fetchWordSyncChapterJson(
					baseUrl = baseUrl,
					requestHeaders = headers,
					identity = identity,
					chapterKey = chapter.chapterKey,
					advertisedHref = chapterHref
				)
			},
			decode = { json -> decodeWordSyncChapter(json, identity, chapter) },
			wordSyncIdentity = identity,
			advancesWordSyncGeneration = false
		)
	}

	suspend fun clearMetadataCache(): Result<Unit> =
		withConfiguredClientAvailability { baseUrl, _ ->
			metadataCache.clearBaseUrl(baseUrl)
			Result.success(Unit)
		}

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
			invalidateBinderyProgressMutation(baseUrl, progress.bookId)
		}

	suspend fun getBookFindings(bookId: String, forceRefresh: Boolean = false): Result<BinderyCatalog> =
		withConfiguredCachedPayload(
			payloadType = BinderyMetadataPayloadType.BookFindings,
			path = bookId,
			forceRefresh = forceRefresh,
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
				path = sourceUrl,
				apiKeyFingerprint = configuredBinderyCacheFingerprint()
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
				val html = apiClient.fetchExternalText(
					sourceUrl,
					ExternalTextPurpose.AudioBookBayProviderCover
				)
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
			invalidateBinderyAction(baseUrl, path)
			Logger.i(TAG, "Bindery action completed path=$label")
		}.onFailure { error ->
			Logger.w(TAG, "Bindery action failed path=$label", error)
		}
	}

	private suspend fun <T> withConfiguredWordSyncPayload(
		payloadType: String,
		forceRefresh: Boolean,
		route: (String) -> BinderyWordSyncRoute,
		fetch: suspend (baseUrl: String, headers: Map<String, String>) -> String,
		decode: (String) -> T,
		wordSyncIdentity: BinderyWhispersyncIdentity,
		advancesWordSyncGeneration: Boolean,
		wordSyncGeneratedAt: (T) -> String? = { null }
	): Result<T> = withConfiguredClientAvailability { baseUrl, headers ->
		val approvedRoute = runCatching { route(baseUrl) }
			.getOrElse { return@withConfiguredClientAvailability Result.failure(it) }
		val apiKeyFingerprint = binderyApiKeyFingerprint(headers["X-Api-Key"].orEmpty())
		val cacheKey = binderyMetadataCacheKey(
			baseUrl = baseUrl,
			payloadType = payloadType,
			path = approvedRoute.cachePath,
			apiKeyFingerprint = apiKeyFingerprint
		)
		val generationPrefix = binderyWordSyncGenerationPrefix(wordSyncIdentity)
		val markerPath = "${generationPrefix}current"
		val markerKey = binderyMetadataCacheKey(
			baseUrl = baseUrl,
			payloadType = BinderyMetadataPayloadType.WordSyncGeneration,
			path = markerPath,
			apiKeyFingerprint = apiKeyFingerprint
		)
		val committedArtifactId = metadataCache.get(markerKey)?.payloadJson?.toLongOrNull()
		val cached = metadataCache.get(cacheKey).takeIf {
			committedArtifactId == wordSyncIdentity.artifactId
		}
		if (!forceRefresh && cached != null && isFresh(cached.updatedAtMillis)) {
			runCatching { decode(cached.payloadJson) }
				.onSuccess { cachedPayload ->
					return@withConfiguredClientAvailability Result.success(cachedPayload)
				}
				.onFailure { cacheError ->
					Logger.w(TAG, "Bindery WordSync cache decode failed type=$payloadType", cacheError)
				}
		}

		runCatching {
			val rawJson = fetch(baseUrl, headers)
			rawJson to decode(rawJson)
		}.fold(
			onSuccess = { (rawJson, live) ->
				runCatching {
					cacheValidatedWordSyncPayload(
						baseUrl = baseUrl,
						payloadType = payloadType,
						cacheKey = cacheKey,
						cachePath = approvedRoute.cachePath,
						rawJson = rawJson,
						identity = wordSyncIdentity,
						advancesGeneration = advancesWordSyncGeneration,
						generatedAt = wordSyncGeneratedAt(live),
						apiKeyFingerprint = apiKeyFingerprint
					)
				}.onFailure { cacheError ->
					Logger.w(TAG, "Bindery WordSync cache commit failed type=$payloadType", cacheError)
				}
				Logger.i(TAG, "Bindery WordSync metadata fetched type=$payloadType")
				preferenceManager.markIntegrationServiceAvailable(IntegrationService.Bindery)
				Result.success(live)
			},
			onFailure = { error ->
				Logger.w(TAG, "Bindery WordSync request failed type=$payloadType", error)
				preferenceManager.markIntegrationServiceDown(IntegrationService.Bindery)
				if (cached == null) {
					Result.failure(error)
				} else {
					runCatching { decode(cached.payloadJson) }
						.onFailure { cacheError ->
							Logger.w(TAG, "Bindery WordSync cache decode failed type=$payloadType", cacheError)
						}
				}
			}
		)
	}

	private suspend fun cacheValidatedWordSyncPayload(
		baseUrl: String,
		payloadType: String,
		cacheKey: String,
		cachePath: String,
		rawJson: String,
		identity: BinderyWhispersyncIdentity,
		advancesGeneration: Boolean,
		generatedAt: String?,
		apiKeyFingerprint: String
	) {
		BinderyWordSyncCacheMutationMutex.withLock {
			val generationPrefix = binderyWordSyncGenerationPrefix(identity)
			val markerPath = "${generationPrefix}current"
			val markerKey = binderyMetadataCacheKey(
				baseUrl = baseUrl,
				payloadType = BinderyMetadataPayloadType.WordSyncGeneration,
				path = markerPath,
				apiKeyFingerprint = apiKeyFingerprint
			)
			val generatedAtPath = "${generationPrefix}generated-at"
			val generatedAtKey = binderyMetadataCacheKey(
				baseUrl = baseUrl,
				payloadType = BinderyMetadataPayloadType.WordSyncGeneration,
				path = generatedAtPath,
				apiKeyFingerprint = apiKeyFingerprint
			)
			val previousArtifactId = metadataCache.get(markerKey)
				?.payloadJson
				?.toLongOrNull()
			val previousGeneratedAt = metadataCache.get(generatedAtKey)
				?.payloadJson
				?.takeIf(String::isNotBlank)
			val artifactId = identity.artifactId
			if (previousArtifactId != null && artifactId < previousArtifactId) {
				return@withLock
			}
			if (!advancesGeneration && previousArtifactId != null && artifactId != previousArtifactId) {
				return@withLock
			}
			val generationAdvanced =
				advancesGeneration && (previousArtifactId == null || artifactId > previousArtifactId)
			val currentGeneratedAt = generatedAt?.trim()?.takeIf(String::isNotEmpty)
			val publicationRegenerated =
				advancesGeneration &&
					previousArtifactId == artifactId &&
					currentGeneratedAt != null &&
					currentGeneratedAt != previousGeneratedAt
			if (generationAdvanced || publicationRegenerated) {
				listOf(
					BinderyMetadataPayloadType.WordSyncIndex,
					BinderyMetadataPayloadType.WordSyncChapter
				).forEach { stalePayloadType ->
					metadataCache.clearPayload(
						baseUrl = baseUrl,
						payloadType = stalePayloadType,
						path = generationPrefix,
						pathPrefix = true
					)
				}
			}
			val updatedAtMillis = currentTimeMillis()
			metadataCache.put(
				BinderyMetadataCacheRecord(
					cacheKey = cacheKey,
					baseUrl = baseUrl,
					payloadType = payloadType,
					path = cachePath,
					payloadJson = rawJson,
					updatedAtMillis = updatedAtMillis
				)
			)
			if (generationAdvanced) {
				metadataCache.put(
					BinderyMetadataCacheRecord(
						cacheKey = markerKey,
						baseUrl = baseUrl,
						payloadType = BinderyMetadataPayloadType.WordSyncGeneration,
						path = markerPath,
						payloadJson = artifactId.toString(),
						updatedAtMillis = updatedAtMillis
					)
				)
			}
			if (advancesGeneration && currentGeneratedAt != null) {
				metadataCache.put(
					BinderyMetadataCacheRecord(
						cacheKey = generatedAtKey,
						baseUrl = baseUrl,
						payloadType = BinderyMetadataPayloadType.WordSyncGeneration,
						path = generatedAtPath,
						payloadJson = currentGeneratedAt,
						updatedAtMillis = updatedAtMillis
					)
				)
			}
		}
	}

	private suspend fun <T> getConfiguredCachedPayload(
		payloadType: String,
		path: String,
		decode: (String) -> T
	): Result<T?> =
		withConfiguredClientAvailability { baseUrl, _ ->
			val cachePath = path.trim()
			val cacheKey = binderyMetadataCacheKey(
				baseUrl = baseUrl,
				payloadType = payloadType,
				path = cachePath,
				apiKeyFingerprint = configuredBinderyCacheFingerprint()
			)
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
		forceRefresh: Boolean = false,
		fetch: suspend (baseUrl: String, headers: Map<String, String>) -> T,
		encode: (T) -> String,
		decode: (String) -> T,
		acceptCached: (T) -> Boolean = { true }
	): Result<T> = withConfiguredCachedPayloadWithSource(
		payloadType = payloadType,
		path = path,
		forceRefresh = forceRefresh,
		fetch = fetch,
		encode = encode,
		decode = decode,
		acceptCached = acceptCached
	).map { payload -> payload.data }

	private suspend fun <T> withConfiguredCachedPayloadWithSource(
		payloadType: String,
		path: String,
		forceRefresh: Boolean = false,
		fetch: suspend (baseUrl: String, headers: Map<String, String>) -> T,
		encode: (T) -> String,
		decode: (String) -> T,
		acceptCached: (T) -> Boolean = { true }
	): Result<CachedPayload<T>> =
		withConfiguredClientAvailability { baseUrl, headers ->
			val cachePath = path.trim()
			val cacheKey = binderyMetadataCacheKey(
				baseUrl = baseUrl,
				payloadType = payloadType,
				path = cachePath,
				apiKeyFingerprint = configuredBinderyCacheFingerprint()
			)
			val cached = metadataCache.get(cacheKey)
			if (!forceRefresh && cached != null && isFresh(cached.updatedAtMillis)) {
				runCatching { decode(cached.payloadJson) }
					.onSuccess { cachedPayload ->
						if (acceptCached(cachedPayload)) {
							return@withConfiguredClientAvailability Result.success(
								CachedPayload(cachedPayload, CachedPayloadSource.FreshCache)
							)
						}
						Logger.i(
							TAG,
							"Bindery metadata cache stale type=$payloadType path=${readerPublicationResourceLogLabel(cachePath)}"
						)
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
					Result.success(CachedPayload(live, CachedPayloadSource.Live))
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
							.mapCatching { cachedPayload ->
								if (acceptCached(cachedPayload)) {
									CachedPayload(
										data = cachedPayload,
										source = CachedPayloadSource.StaleCache,
										staleFailure = optionalIntegrationFailure(error)
									)
								} else {
									throw IllegalStateException("Cached Bindery payload is stale")
								}
							}
					} else {
						Result.failure(error)
					}
				}
			)
		}

	private fun isFresh(updatedAtMillis: Long): Boolean =
		currentTimeMillis() - updatedAtMillis <= BINDERY_METADATA_CACHE_FRESH_MILLIS

	private fun configuredBinderyCacheFingerprint(): String =
		binderyApiKeyFingerprint(preferenceManager.binderyApiKey)

	private suspend fun invalidateBinderyProgressMutation(baseUrl: String, bookId: String) {
		val normalizedBookId = bookId.trim().takeIf { it.isNotEmpty() }
		if (normalizedBookId == null) {
			metadataCache.clearBaseUrl(baseUrl)
			return
		}
		metadataCache.clearPayload(
			baseUrl = baseUrl,
			payloadType = BinderyMetadataPayloadType.BookSync,
			path = normalizedBookId
		)
	}

	private suspend fun invalidateBinderyAction(baseUrl: String, path: String) {
		val bookId = path.substringBefore('?')
			.trim('/')
			.split('/')
			.let { segments ->
				segments.getOrNull(2).takeIf {
					segments.getOrNull(0) == "opds" && segments.getOrNull(1) == "books"
				}
			}
			?.trim()
			?.takeIf { it.isNotEmpty() }
		if (bookId == null) {
			metadataCache.clearBaseUrl(baseUrl)
			return
		}

		metadataCache.clearPayload(baseUrl, BinderyMetadataPayloadType.Catalog)
		metadataCache.clearPayload(baseUrl, BinderyMetadataPayloadType.BookFindings)
		metadataCache.clearPayload(baseUrl, BinderyMetadataPayloadType.Manifest, bookId)
		metadataCache.clearPayload(baseUrl, BinderyMetadataPayloadType.Resources, bookId)
		metadataCache.clearPayload(baseUrl, BinderyMetadataPayloadType.BookSync, bookId)
		metadataCache.clearPayload(
			baseUrl = baseUrl,
			payloadType = BinderyMetadataPayloadType.AudiobookVersions,
			path = "book:$bookId:",
			pathPrefix = true
		)
	}

	private fun acceptCachedWhispersyncSidecar(sidecar: WhispersyncSidecar): Boolean {
		val rangedSegments = sidecar.timeline.segments.filter { segment ->
			segment.textStart != null && segment.textEnd != null
		}
		if (rangedSegments.isEmpty()) return true
		val hasSpokenText = rangedSegments.any { segment -> !segment.spokenText.isNullOrBlank() }
		val hasEbookText = rangedSegments.any { segment -> !segment.ebookText.isNullOrBlank() }
		return hasSpokenText && hasEbookText
	}

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

private fun BinderyWordSyncDiscovery.requiredWordSyncIndexHref(): String {
	require(status?.trim()?.lowercase() in setOf("ready", "partial")) {
		"Bindery WordSync discovery is not ready."
	}
	require(schema == WordSyncIndexSchema) { "Bindery WordSync discovery schema is unsupported." }
	require(format == "chapter-sharded-json") { "Bindery WordSync discovery format is unsupported." }
	require(compression == "http") { "Bindery WordSync discovery compression is unsupported." }
	require(timeScale == WordSyncTimeScale) { "Bindery WordSync discovery time scale is unsupported." }
	coverage?.let { require(it in 0.0..1.0) { "Bindery WordSync discovery coverage is invalid." } }
	return opdsIndexHref?.takeIf { it.isNotBlank() }
		?: indexHref?.takeIf { it.isNotBlank() }
		?: throw IllegalArgumentException("Bindery WordSync index route is required.")
}

private fun BinderyWordSyncDiscovery.validateWordSyncIndex(index: WordSyncIndex) {
	shardCount?.let { require(it == index.chapters.size) { "Bindery WordSync shard count mismatch." } }
	val chapterAudioWords = index.chapters.sumOf { it.audioWordCount }
	val unplacedAudioWords = index.unplaced?.audioWordCount ?: 0
	audioWordCount?.let {
		require(it == chapterAudioWords + unplacedAudioWords) {
			"Bindery WordSync audio word count mismatch."
		}
	}
	matchedAudioWordCount?.let {
		require(it == index.chapters.sumOf(WordSyncChapterSummary::matchedAudioWordCount)) {
			"Bindery WordSync matched word count mismatch."
		}
	}
	reviewAudioWordCount?.let {
		require(it == index.chapters.sumOf(WordSyncChapterSummary::reviewAudioWordCount)) {
			"Bindery WordSync review word count mismatch."
		}
	}
	unmatchedAudioWordCount?.let {
		require(
			it == index.chapters.sumOf(WordSyncChapterSummary::unmatchedAudioWordCount) + unplacedAudioWords
		) { "Bindery WordSync unmatched audio word count mismatch." }
	}
	unmatchedEbookWordCount?.let {
		require(it == index.chapters.sumOf(WordSyncChapterSummary::unmatchedEbookWordCount)) {
			"Bindery WordSync unmatched ebook word count mismatch."
		}
	}
}

private fun BinderyCatalog.isOptionalIntegrationEmpty(): Boolean =
	publications.isEmpty() && navigation.isEmpty()
