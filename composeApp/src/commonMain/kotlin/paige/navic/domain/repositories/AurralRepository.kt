package paige.navic.domain.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.IntegrationService
import paige.navic.util.core.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


private const val TAG = "AurralRepository"
private const val AURRAL_DISCOVERY_IMAGE_HYDRATION_LIMIT = 12
private const val AURRAL_DISCOVERY_IMAGE_SEARCH_LIMIT = 5
internal val AURRAL_LIBRARY_ARTISTS_CACHE_TTL: Duration = 10.minutes
internal const val AURRAL_DISABLED_MESSAGE = "Aurral is disabled."

class AurralRepository(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient = KtorAurralApiClient(),
	private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
	private val metadataCache: AurralMetadataCache = NoOpAurralMetadataCache,
	private val confirmationWorkerEnabled: Boolean = false
) {
	private val discoverArtistImageUrlsByName = mutableMapOf<String, String?>()
	private val localState = AurralRepositoryLocalState(nowMillis)
	private val auth = AurralRepositoryAuth(preferenceManager, apiClient)
	private val flowActions = AurralFlowRepositoryActions(preferenceManager, apiClient, auth)
	private val confirmationQueueManager = AurralConfirmationQueueManager(
		preferenceManager = preferenceManager,
		apiClient = apiClient,
		nowMillis = nowMillis,
		onArtistStateChanged = localState::bumpArtistStateRevision,
		onArtistMonitoringConfirmed = localState::rememberOptimisticArtistMonitoring
	)
	val confirmationQueue = confirmationQueueManager.confirmationQueue
	val albumRequests = localState.albumRequests
	val libraryArtistMonitorStates = localState.libraryArtistMonitorStates
	val artistStateRevision = localState.artistStateRevision

	init {
		preferenceManager.addIntegrationEnabledChangeListener(IntegrationService.Aurral) { enabled ->
			if (!enabled) {
				confirmationQueueManager.cancel(clearQueue = true)
				localState.clearIntegrationState()
			}
		}
	}

	suspend fun testConnection(): AurralConnectionResult {
		if (!preferenceManager.aurralEnabled) return AurralConnectionResult.Failed(AURRAL_DISABLED_MESSAGE)
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return AurralConnectionResult.Failed(baseUrlError)
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return AurralConnectionResult.Failed(AURRAL_BASE_URL_REQUIRED_MESSAGE)
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return try {
			apiClient.testConnection(baseUrl, requestHeaders)
				.recordAurralAvailability()
		} catch (e: Exception) {
			Logger.w(TAG, "Aurral connection test failed", e)
			preferenceManager.markIntegrationServiceDown(IntegrationService.Aurral)
			AurralConnectionResult.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
		}
	}

	suspend fun getServiceStatus(): Result<AurralServiceStatus> {
		if (!preferenceManager.aurralEnabled) {
			localState.clearAlbumRequests()
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) {
			localState.clearAlbumRequests()
			return Result.failure(IllegalStateException(baseUrlError))
		}
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: run {
				localState.clearAlbumRequests()
				return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
			}
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			apiClient.fetchServiceStatus(baseUrl, requestHeaders)
		}.onSuccess { status ->
			localState.setAlbumRequests(status.acquisitionQueue.map { it.toAlbumRequest() })
		}.onFailure { error ->
			Logger.w(TAG, "Aurral service status failed", error)
		}.recordAurralAvailability()
	}

	suspend fun refreshAlbumRequests(): Result<List<AurralAlbumRequest>> =
		getServiceStatus().map { status -> status.acquisitionQueue.map { it.toAlbumRequest() } }

	suspend fun getActivityStatus(): Result<AurralServiceStatus> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			apiClient.fetchActivityStatus(baseUrl, requestHeaders)
		}.onSuccess { status ->
			localState.setAlbumRequests(status.acquisitionQueue.map { it.toAlbumRequest() })
		}.onFailure { error ->
			Logger.w(TAG, "Aurral activity status failed", error)
		}.recordAurralAvailability()
	}

	suspend fun getDiscovery(
		hydrateMissingImages: Boolean = true
	): Result<AurralDiscoverySummary> {
		if (!preferenceManager.aurralEnabled) return Result.success(AurralDiscoverySummary())
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			val discovery = cachedAurralPayload<AurralDiscoverySummary>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.Discovery,
				path = "summary",
				operation = "Aurral discovery"
			) {
				apiClient.fetchDiscovery(baseUrl, requestHeaders)
			}
				.let { summary ->
					localState.withLibraryArtists(
						discovery = summary,
						libraryArtists = getCachedLibraryArtists(baseUrl, requestHeaders)
					)
				}
			if (hydrateMissingImages) {
				hydrateMissingDiscoveryArtistImages(baseUrl, requestHeaders, discovery)
			} else {
				discovery
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral discovery failed", error)
		}
	}

	suspend fun getLibraryDiscovery(): Result<AurralDiscoverySummary> =
		getDiscovery(hydrateMissingImages = false)

	suspend fun refreshLibraryArtistMonitorStates(): Result<Map<String, Boolean?>> {
		if (!preferenceManager.aurralEnabled) {
			localState.clearIntegrationState()
			return Result.success(emptyMap())
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			getCachedLibraryArtists(baseUrl, requestHeaders)
			localState.libraryArtistMonitorStates.value
		}.onFailure { error ->
			Logger.w(TAG, "Aurral library artist monitor state refresh failed", error)
		}.recordAurralAvailability()
	}

	fun discoveryConfigurationKey(hydrateMissingImages: Boolean = true): String? {
		if (!preferenceManager.aurralEnabled) return "disabled"
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return null
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
			.entries
			.sortedBy { it.key }
			.joinToString("|") { (key, value) -> "$key:${value.hashCode()}" }
		return listOf(
			baseUrl.trimEnd('/'),
			hydrateMissingImages.toString(),
			requestHeaders
		).joinToString("|")
	}

	suspend fun searchArtists(
		query: String,
		limit: Int = 12,
		offset: Int = 0
	): Result<AurralArtistSearchResult> {
		val trimmedQuery = query.trim()
		if (trimmedQuery.isEmpty()) return Result.success(AurralArtistSearchResult())
		if (!preferenceManager.aurralEnabled) return Result.success(AurralArtistSearchResult())
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val request = AurralArtistSearchRequest(
			query = trimmedQuery,
			limit = limit.coerceIn(1, 50),
			offset = offset.coerceAtLeast(0)
		)

		return runCatching {
			cachedAurralPayload<AurralArtistSearchResult>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.ArtistSearch,
				path = aurralSearchCachePath(request.query, request.limit, request.offset),
				operation = "Aurral artist search for $trimmedQuery"
			) {
				apiClient.searchArtists(baseUrl, requestHeaders, request)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist search failed for $trimmedQuery", error)
		}
	}

	suspend fun searchAlbums(
		query: String,
		limit: Int = 12,
		offset: Int = 0
	): Result<AurralAlbumSearchResult> {
		val trimmedQuery = query.trim()
		if (trimmedQuery.isEmpty()) return Result.success(AurralAlbumSearchResult())
		if (!preferenceManager.aurralEnabled) return Result.success(AurralAlbumSearchResult())
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val request = AurralAlbumSearchRequest(
			query = trimmedQuery,
			limit = limit.coerceIn(1, 50),
			offset = offset.coerceAtLeast(0)
		)

		return runCatching {
			cachedAurralPayload<AurralAlbumSearchResult>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.AlbumSearch,
				path = aurralSearchCachePath(request.query, request.limit, request.offset),
				operation = "Aurral album search for $trimmedQuery"
			) {
				apiClient.searchAlbums(baseUrl, requestHeaders, request)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album search failed for $trimmedQuery", error)
		}
	}

	suspend fun getAlbumTracks(
		album: AurralAlbumSearchItem
	): Result<List<AurralAlbumTrackItem>> {
		if (!preferenceManager.aurralEnabled) return Result.success(emptyList())
		val releaseGroupMbid = album.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(emptyList())
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			val libraryAlbumId = album.libraryAlbumId?.trim()?.takeIf { it.isNotEmpty() }
			cachedAurralPayload<List<AurralAlbumTrackItem>>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.AlbumTracks,
				path = aurralAlbumTracksCachePath(releaseGroupMbid, libraryAlbumId),
				operation = "Aurral album tracks for ${album.title}"
			) {
				apiClient.fetchAlbumTracks(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					releaseGroupMbid = releaseGroupMbid,
					libraryAlbumId = libraryAlbumId
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album track lookup failed for ${album.title}", error)
		}
	}

	suspend fun getArtistEnrichment(artist: DomainArtist): Result<AurralArtistEnrichment?> {
		if (!preferenceManager.aurralEnabled) return Result.success(null)
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val directArtistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			val resolvedArtist = resolveArtistForEnrichment(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = directArtistMbid,
				artistName = artistName
			) ?: return@runCatching null
			coroutineScope {
				val enrichment = async {
					cachedAurralPayload<AurralArtistEnrichment>(
						baseUrl = baseUrl,
						payloadType = AurralMetadataPayloadType.ArtistEnrichment,
						path = aurralArtistEnrichmentCachePath(
							artistMbid = resolvedArtist.artistMbid,
							artistName = resolvedArtist.artistName
						),
						operation = "Aurral artist enrichment for ${resolvedArtist.artistName}"
					) {
						apiClient.fetchArtistEnrichment(
							baseUrl = baseUrl,
							requestHeaders = requestHeaders,
							artistMbid = resolvedArtist.artistMbid,
							artistName = resolvedArtist.artistName
						)
					}
				}
				val libraryArtistMonitoring = async {
					getCachedLibraryArtistMonitoring(
						baseUrl = baseUrl,
						requestHeaders = requestHeaders,
						artistMbid = resolvedArtist.artistMbid,
						artistName = resolvedArtist.artistName
					) ?: apiClient.fetchLibraryArtistMonitoring(
						baseUrl = baseUrl,
						requestHeaders = requestHeaders,
						artistMbid = resolvedArtist.artistMbid
					).also { monitored ->
						monitored?.let {
							rememberLibraryArtistMonitoring(
								baseUrl = baseUrl,
								requestHeaders = requestHeaders,
								artistMbid = resolvedArtist.artistMbid,
								artistName = resolvedArtist.artistName,
								monitored = it
							)
						}
					}
				}
				localState.withLocalArtistState(
					enrichment = enrichment.await(),
					libraryArtistMonitoring = libraryArtistMonitoring.await()
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist enrichment failed for $artistName", error)
		}
	}

	suspend fun getArtistCoreEnrichment(artist: DomainArtist): Result<AurralArtistEnrichment?> {
		if (!preferenceManager.aurralEnabled) return Result.success(null)
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val directArtistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			val resolvedArtist = resolveArtistForEnrichment(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = directArtistMbid,
				artistName = artistName
			) ?: return@runCatching null
			coroutineScope {
				val enrichment = async {
					cachedAurralPayload<AurralArtistEnrichment>(
						baseUrl = baseUrl,
						payloadType = AurralMetadataPayloadType.ArtistCoreEnrichment,
						path = aurralArtistEnrichmentCachePath(
							artistMbid = resolvedArtist.artistMbid,
							artistName = resolvedArtist.artistName
						),
						operation = "Aurral artist core enrichment for ${resolvedArtist.artistName}",
						useFreshCache = false
					) {
						apiClient.fetchArtistCoreEnrichment(
							baseUrl = baseUrl,
							requestHeaders = requestHeaders,
							artistMbid = resolvedArtist.artistMbid,
							artistName = resolvedArtist.artistName
						)
					}
				}
				val libraryArtistMonitoring = async {
					getCachedLibraryArtistMonitoring(
						baseUrl = baseUrl,
						requestHeaders = requestHeaders,
						artistMbid = resolvedArtist.artistMbid,
						artistName = resolvedArtist.artistName
					) ?: apiClient.fetchLibraryArtistMonitoring(
						baseUrl = baseUrl,
						requestHeaders = requestHeaders,
						artistMbid = resolvedArtist.artistMbid
					).also { monitored ->
						monitored?.let {
							rememberLibraryArtistMonitoring(
								baseUrl = baseUrl,
								requestHeaders = requestHeaders,
								artistMbid = resolvedArtist.artistMbid,
								artistName = resolvedArtist.artistName,
								monitored = it
							)
						}
					}
				}
				localState.withLocalArtistState(
					enrichment = enrichment.await(),
					libraryArtistMonitoring = libraryArtistMonitoring.await()
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist core enrichment failed for $artistName", error)
		}
	}

	suspend fun getArtistPreviewTracks(artist: DomainArtist): Result<List<AurralPreviewTrack>> {
		if (!preferenceManager.aurralEnabled) return Result.success(emptyList())
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(emptyList())
		val directArtistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			val resolvedArtist = resolveArtistForEnrichment(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = directArtistMbid,
				artistName = artistName
			) ?: return@runCatching emptyList()
			cachedAurralPayload<List<AurralPreviewTrack>>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.ArtistPreviewTracks,
				path = aurralArtistSectionCachePath(
					artistMbid = resolvedArtist.artistMbid,
					artistName = resolvedArtist.artistName,
					section = "preview"
				),
				operation = "Aurral artist preview tracks for ${resolvedArtist.artistName}",
				useFreshCache = false
			) {
				apiClient.fetchArtistPreviewTracks(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					artistMbid = resolvedArtist.artistMbid,
					artistName = resolvedArtist.artistName
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist preview tracks failed for $artistName", error)
		}
	}

	suspend fun getArtistSimilarArtists(artist: DomainArtist): Result<List<AurralSimilarArtist>> {
		if (!preferenceManager.aurralEnabled) return Result.success(emptyList())
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(emptyList())
		val directArtistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			val resolvedArtist = resolveArtistForEnrichment(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = directArtistMbid,
				artistName = artistName
			) ?: return@runCatching emptyList()
			cachedAurralPayload<List<AurralSimilarArtist>>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.ArtistSimilarArtists,
				path = aurralArtistSectionCachePath(
					artistMbid = resolvedArtist.artistMbid,
					artistName = resolvedArtist.artistName,
					section = "similar"
				),
				operation = "Aurral similar artists for ${resolvedArtist.artistName}",
				useFreshCache = false
			) {
				apiClient.fetchArtistSimilarArtists(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					artistMbid = resolvedArtist.artistMbid,
					artistName = resolvedArtist.artistName
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral similar artists failed for $artistName", error)
		}
	}

	suspend fun getArtistAlbumRequests(artist: DomainArtist): Result<List<AurralAlbumRequest>> {
		if (!preferenceManager.aurralEnabled) return Result.success(emptyList())
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(emptyList())
		val directArtistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			val resolvedArtist = resolveArtistForEnrichment(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = directArtistMbid,
				artistName = artistName
			) ?: return@runCatching emptyList()
			val requests = cachedAurralPayload<List<AurralAlbumRequest>>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.AlbumRequests,
				path = aurralArtistSectionCachePath(
					artistMbid = resolvedArtist.artistMbid,
					artistName = resolvedArtist.artistName,
					section = "requests"
				),
				operation = "Aurral album requests for ${resolvedArtist.artistName}",
				useFreshCache = false
			) {
				apiClient.fetchAlbumRequests(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders
				)
			}
			localState.withLocalArtistState(
				enrichment = AurralArtistEnrichment(
					artistMbid = resolvedArtist.artistMbid,
					artistName = resolvedArtist.artistName,
					requests = requests
				),
				libraryArtistMonitoring = null
			).requests
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album requests failed for $artistName", error)
		}
	}

	suspend fun getCachedArtistEnrichment(artist: DomainArtist): Result<AurralArtistEnrichment?> {
		if (!preferenceManager.aurralEnabled) return Result.success(null)
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val cacheKey = aurralMetadataCacheKey(
			baseUrl = baseUrl,
			payloadType = AurralMetadataPayloadType.ArtistEnrichment,
			path = aurralArtistEnrichmentCachePath(
				artistMbid = artistMbid,
				artistName = artistName
			)
		)
		return runCatching {
			metadataCache.get(cacheKey)
				?.decodeAurralMetadata<AurralArtistEnrichment>("cached Aurral artist enrichment for $artistName")
				?: metadataCache.get(
					aurralMetadataCacheKey(
						baseUrl = baseUrl,
						payloadType = AurralMetadataPayloadType.ArtistCoreEnrichment,
						path = aurralArtistEnrichmentCachePath(
							artistMbid = artistMbid,
							artistName = artistName
						)
					)
				)?.decodeAurralMetadata<AurralArtistEnrichment>("cached Aurral artist core enrichment for $artistName")
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist enrichment cache read failed for $artistName", error)
		}
	}

	private suspend fun resolveArtistForEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String?,
		artistName: String
	): ResolvedAurralArtist? {
		artistMbid?.let { return ResolvedAurralArtist(artistMbid = it, artistName = artistName) }
		val request = AurralArtistSearchRequest(
			query = artistName,
			limit = 5,
			offset = 0
		)
		val search = runCatching {
			cachedAurralPayload<AurralArtistSearchResult>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.ArtistSearch,
				path = aurralSearchCachePath(request.query, request.limit, request.offset),
				operation = "Aurral artist lookup for $artistName"
			) {
				apiClient.searchArtists(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					request = request
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist name lookup failed for $artistName", error)
		}.getOrNull() ?: return null
		val normalizedName = artistName.normalizedAurralSearchName()
		val artist = search.artists.firstOrNull {
			it.name.normalizedAurralSearchName() == normalizedName
		} ?: search.artists.firstOrNull()
		val resolvedMbid = artist?.id?.trim()
			?.takeIf { it.isNotEmpty() && artist.detailsIdVerified }
			?: return null
		val resolvedName = artist.name.trim().takeIf { it.isNotEmpty() } ?: artistName
		return ResolvedAurralArtist(
			artistMbid = resolvedMbid,
			artistName = resolvedName
		)
	}

	suspend fun cancelAcquisitionRequest(item: AurralAcquisitionQueueItem): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val target = aurralAcquisitionDeleteTarget(item)
			?: return Result.failure(IllegalStateException("Aurral request delete target is unavailable."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			apiClient.cancelAcquisitionRequest(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				target = target
			)
			localState.removeAlbumRequest(item.albumMbid, item.albumName)
			clearAurralMetadataCache(baseUrl)
			Unit
		}.onFailure { error ->
			Logger.w(TAG, "Aurral acquisition cancel failed for ${item.albumName}", error)
		}.recordAurralAvailability()
	}

	suspend fun retryAcquisitionRequest(item: AurralAcquisitionQueueItem): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val albumMbid = item.albumMbid?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album MusicBrainz ID is required."))
		val artistMbid = item.artistMbid?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val albumName = item.albumName.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album title is required."))
		val artistName = item.artistName.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralAlbumRequestPayload(
			albumMbid = albumMbid,
			albumName = albumName,
			artistMbid = artistMbid,
			artistName = artistName,
			triggerSearch = true
		)

		return runCatching {
			apiClient.requestAlbum(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
			localState.rememberOptimisticAlbumRequest(payload)
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral acquisition retry failed for $albumName", error)
		}.recordAurralAvailability()
	}

	suspend fun requestAlbum(
		artist: DomainArtist,
		releaseGroup: AurralReleaseGroup
	): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val albumMbid = releaseGroup.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album MusicBrainz ID is required."))
		val albumName = releaseGroup.title.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album title is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralAlbumRequestPayload(
			albumMbid = albumMbid,
			albumName = albumName,
			artistMbid = artistMbid,
			artistName = artist.name,
			triggerSearch = true
		)

		return runCatching {
			apiClient.requestAlbum(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
			localState.rememberOptimisticAlbumRequest(payload)
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album request failed for $albumName", error)
		}.recordAurralAvailability()
	}

	suspend fun requestAlbum(album: AurralAlbumSearchItem): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val albumMbid = album.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album MusicBrainz ID is required."))
		val albumName = album.title.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album title is required."))
		val artistMbid = album.artistMbid.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val artistName = album.artistName.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralAlbumRequestPayload(
			albumMbid = albumMbid,
			albumName = albumName,
			artistMbid = artistMbid,
			artistName = artistName,
			triggerSearch = true
		)

		return runCatching {
			apiClient.requestAlbum(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
			localState.rememberOptimisticAlbumRequest(payload)
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album request failed for $albumName", error)
		}.recordAurralAvailability()
	}

	suspend fun monitorArtist(artist: DomainArtist): Result<Unit> {
		return setArtistMonitoring(artist, monitored = true)
	}

	suspend fun setArtistMonitoring(
		artist: DomainArtist,
		monitored: Boolean
	): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		return setArtistMonitoringByAurralId(artistMbid, artistName, monitored)
	}

	suspend fun monitorDiscoveredArtist(artist: AurralDiscoverArtist): Result<Unit> {
		val artistMbid = artist.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		return setArtistMonitoringByAurralId(artistMbid, artistName, monitored = true)
	}

	private suspend fun setArtistMonitoringByAurralId(
		artistMbid: String,
		artistName: String,
		monitored: Boolean
	): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralArtistMonitorPayload(
			foreignArtistId = artistMbid,
			artistName = artistName,
			monitorOption = if (monitored) "all" else "none",
			monitored = monitored
		)
		val confirmationId = aurralArtistMonitoringConfirmationId(artistMbid)

		confirmationQueueManager.upsert(
			AurralConfirmationQueueItem(
				id = confirmationId,
				type = AurralConfirmationType.ArtistMonitoring,
				status = AurralConfirmationStatus.Pending,
				title = artistName,
				artistMbid = artistMbid,
				expectedMonitored = monitored,
				message = if (monitored) {
					"Waiting for Aurral to confirm artist monitoring."
				} else {
					"Waiting for Aurral to confirm monitoring stopped."
				},
				updatedAtMillis = nowMillis()
			)
		)
		return runCatching {
			apiClient.monitorArtist(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				payload = payload
			)
			if (confirmationWorkerEnabled) {
				confirmationQueueManager.startArtistMonitoringConfirmationWorker(
					confirmationId = confirmationId,
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					artistMbid = artistMbid,
					artistName = artistName,
					monitored = monitored,
					payload = payload
				)
			}
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			confirmationQueueManager.upsert(
				AurralConfirmationQueueItem(
					id = confirmationId,
					type = AurralConfirmationType.ArtistMonitoring,
					status = AurralConfirmationStatus.Failed,
					title = artistName,
					artistMbid = artistMbid,
					expectedMonitored = monitored,
					message = error.message ?: error::class.simpleName ?: "Aurral confirmation failed.",
					updatedAtMillis = nowMillis()
				)
			)
			Logger.w(TAG, "Aurral artist monitoring failed for $artistName", error)
		}.recordAurralAvailability()
	}

	suspend fun createFlow(
		name: String,
		size: Int,
		scheduleDay: Int = currentAurralScheduleDay()
	): Result<AurralFlowActionResult> =
		flowActions.createFlow(name, size, scheduleDay)

	suspend fun setFlowEnabled(
		flowId: String,
		enabled: Boolean
	): Result<AurralFlowActionResult> =
		flowActions.setFlowEnabled(flowId, enabled)

	suspend fun startFlow(
		flowId: String,
		limit: Int
	): Result<AurralFlowActionResult> =
		flowActions.startFlow(flowId, limit)

	suspend fun getFlowPlayableSongs(
		flowId: String,
		limit: Int = 200
	): Result<List<DomainSong>> =
		flowActions.getFlowPlayableSongs(flowId, limit)

	private suspend fun aurralApiRequestHeaders(baseUrl: String): Map<String, String> =
		auth.apiRequestHeaders(baseUrl)

	suspend fun getReleaseGroupCoverImageUrl(
		releaseGroup: AurralReleaseGroup,
		artistName: String
	): Result<String?> {
		if (!preferenceManager.aurralEnabled) return Result.success(null)
		val releaseGroupMbid = releaseGroup.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val albumTitle = releaseGroup.title.trim().takeIf { it.isNotEmpty() }
			?: return Result.success(null)
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			cachedAurralPayload<AurralCachedString>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.ReleaseGroupCover,
				path = aurralReleaseGroupCoverCachePath(
					releaseGroupMbid = releaseGroupMbid,
					artistName = artistName,
					albumTitle = albumTitle
				),
				operation = "Aurral release group cover for $albumTitle"
			) {
				AurralCachedString(
					value = apiClient.fetchReleaseGroupCoverImageUrl(
						baseUrl = baseUrl,
						requestHeaders = requestHeaders,
						releaseGroupMbid = releaseGroupMbid,
						artistName = artistName.trim(),
						albumTitle = albumTitle
					)
				)
			}.value.also { coverUrl ->
				if (!coverUrl.isNullOrBlank()) {
					localState.rememberReleaseGroupCoverUrl(releaseGroupMbid, coverUrl)
				}
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral release group cover failed for $albumTitle", error)
		}
	}

	private fun AurralConnectionResult.recordAurralAvailability(): AurralConnectionResult {
		when (this) {
			is AurralConnectionResult.Failed ->
				preferenceManager.markIntegrationServiceDown(IntegrationService.Aurral)

			AurralConnectionResult.Forbidden,
			AurralConnectionResult.Unauthorized,
			AurralConnectionResult.Connected ->
				preferenceManager.markIntegrationServiceAvailable(IntegrationService.Aurral)
		}
		return this
	}

	private fun <T> Result<T>.recordAurralAvailability(): Result<T> =
		onSuccess {
			preferenceManager.markIntegrationServiceAvailable(IntegrationService.Aurral)
		}.onFailure {
			preferenceManager.markIntegrationServiceDown(IntegrationService.Aurral)
		}

	private suspend inline fun <reified T> cachedAurralPayload(
		baseUrl: String,
		payloadType: String,
		path: String,
		operation: String,
		useFreshCache: Boolean = true,
		crossinline fetch: suspend () -> T
	): T {
		val cacheKey = aurralMetadataCacheKey(baseUrl, payloadType, path)
		val currentTime = nowMillis()
		val cached = runCatching { metadataCache.get(cacheKey) }
			.onFailure { error -> Logger.w(TAG, "Aurral metadata cache read failed for $operation", error) }
			.getOrNull()
		if (useFreshCache) {
			cached
				?.takeIf { it.isFreshAurralMetadata(currentTime) }
				?.decodeAurralMetadata<T>(operation)
				?.let { return it }
		}

		return try {
			fetch().also { payload ->
				preferenceManager.markIntegrationServiceAvailable(IntegrationService.Aurral)
				runCatching {
					metadataCache.put(
						AurralMetadataCacheRecord(
							cacheKey = cacheKey,
							baseUrl = baseUrl,
							payloadType = payloadType,
							path = path,
							payloadJson = AURRAL_JSON.encodeToString(payload),
							updatedAtMillis = currentTime
						)
					)
				}.onFailure { error ->
					Logger.w(TAG, "Aurral metadata cache write failed for $operation", error)
				}
			}
		} catch (error: Exception) {
			preferenceManager.markIntegrationServiceDown(IntegrationService.Aurral)
			cached
				?.decodeAurralMetadata<T>(operation)
				?.let { payload ->
					Logger.w(TAG, "$operation failed; using stale Aurral metadata cache", error)
					return payload
				}
			throw error
		}
	}

	private suspend fun clearAurralMetadataCache(baseUrl: String) {
		runCatching {
			metadataCache.clearBaseUrl(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral metadata cache clear failed", error)
		}
	}

	private fun AurralMetadataCacheRecord.isFreshAurralMetadata(currentTime: Long): Boolean =
		currentTime >= updatedAtMillis &&
			currentTime - updatedAtMillis < AURRAL_METADATA_CACHE_FRESH_MILLIS

	private inline fun <reified T> AurralMetadataCacheRecord.decodeAurralMetadata(
		operation: String
	): T? =
		runCatching {
			AURRAL_JSON.decodeFromString<T>(payloadJson)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral metadata cache decode failed for $operation", error)
		}.getOrNull()

	private suspend fun getCachedLibraryArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> {
		val cacheKey = aurralLibraryArtistsCacheKey(baseUrl, requestHeaders)
		val currentTime = nowMillis()
		localState.freshLibraryArtists(cacheKey, currentTime)?.let { return it }

		return runCatching {
			cachedAurralPayload<List<AurralDiscoverArtist>>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.LibraryArtists,
				path = cacheKey,
				operation = "Aurral library artists"
			) {
				apiClient.fetchLibraryArtists(baseUrl, requestHeaders)
			}
		}.onSuccess { artists ->
			localState.rememberLibraryArtists(cacheKey, artists, currentTime)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral library artists failed", error)
		}.getOrElse {
			localState.cachedLibraryArtistsFallback(cacheKey)
		}
	}

	private fun getCachedLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): Boolean? {
		val cacheKey = aurralLibraryArtistsCacheKey(baseUrl, requestHeaders)
		return localState.cachedLibraryArtistMonitoring(cacheKey, artistMbid, artistName, nowMillis())
	}

	private fun rememberLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String,
		monitored: Boolean
	) {
		val cacheKey = aurralLibraryArtistsCacheKey(baseUrl, requestHeaders)
		localState.rememberLibraryArtistMonitoring(
			cacheKey = cacheKey,
			artistMbid = artistMbid,
			artistName = artistName,
			monitored = monitored
		)
	}

	private suspend fun hydrateMissingDiscoveryArtistImages(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		discovery: AurralDiscoverySummary
	): AurralDiscoverySummary =
		discovery.copy(
			recommendations = hydrateMissingDiscoveryArtistImages(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artists = discovery.recommendations
			),
			globalTop = hydrateMissingDiscoveryArtistImages(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artists = discovery.globalTop
			),
			basedOn = hydrateMissingDiscoveryArtistImages(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artists = discovery.basedOn
			)
		)

	private suspend fun hydrateMissingDiscoveryArtistImages(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artists: List<AurralDiscoverArtist>
	): List<AurralDiscoverArtist> =
		artists.mapIndexed { index, artist ->
			if (index >= AURRAL_DISCOVERY_IMAGE_HYDRATION_LIMIT || !artist.imageUrl.isNullOrBlank()) {
				artist
			} else {
				val imageUrl = discoveryArtistImageUrlByName(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					artistName = artist.name
				)
				imageUrl?.let { artist.copy(imageUrl = it) } ?: artist
			}
		}

	private suspend fun discoveryArtistImageUrlByName(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistName: String
	): String? {
		val nameKey = artistName.normalizedAurralImageLookupName() ?: return null
		if (discoverArtistImageUrlsByName.containsKey(nameKey)) {
			return discoverArtistImageUrlsByName[nameKey]
		}
		val imageUrl = runCatching {
			val request = AurralArtistSearchRequest(
				query = artistName.trim(),
				limit = AURRAL_DISCOVERY_IMAGE_SEARCH_LIMIT,
				offset = 0
			)
			cachedAurralPayload<AurralArtistSearchResult>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.ArtistSearch,
				path = aurralSearchCachePath(request.query, request.limit, request.offset),
				operation = "Aurral discovery image lookup for $artistName"
			) {
				apiClient.searchArtists(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					request = request
				)
			}.artists
				.firstOrNull { candidate ->
					candidate.name.normalizedAurralImageLookupName() == nameKey &&
						!candidate.imageUrl.isNullOrBlank()
				}
				?.imageUrl
				?.let { aurralAbsoluteImageUrl(baseUrl, it) }
		}.onFailure { error ->
			Logger.w(TAG, "Aurral discovery image lookup failed for $artistName", error)
		}.getOrNull()
		discoverArtistImageUrlsByName[nameKey] = imageUrl
		return imageUrl
	}

}
