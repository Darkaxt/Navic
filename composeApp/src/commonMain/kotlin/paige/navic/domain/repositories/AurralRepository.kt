package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.IntegrationService
import paige.navic.util.core.Logger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "AurralRepository"
private const val AURRAL_DISCOVERY_IMAGE_HYDRATION_LIMIT = 12
private const val AURRAL_DISCOVERY_IMAGE_SEARCH_LIMIT = 5
private const val AURRAL_MONITOR_CONFIRMATION_POLLS_PER_CYCLE = 18
private const val AURRAL_CONFIRMATION_QUEUE_RETAINED_ITEMS = 20
private val AURRAL_MONITOR_CONFIRMATION_DELAY: Duration = 10.seconds
private val AURRAL_CONFIRMATION_REEMIT_DELAY: Duration = 3.minutes
private val AURRAL_JSON = Json {
	ignoreUnknownKeys = true
	isLenient = true
}
internal val AURRAL_LIBRARY_ARTISTS_CACHE_TTL: Duration = 10.minutes
internal const val AURRAL_BASE_URL_REQUIRED_MESSAGE = "Enter the Aurral URL first."
internal const val AURRAL_BASE_URL_INVALID_SCHEME_MESSAGE =
	"Aurral URL must start with http:// or https://."
internal const val AURRAL_BASE_URL_INVALID_HOST_MESSAGE =
	"Aurral URL must include a host and cannot include credentials, a query, or a fragment."
internal const val AURRAL_DISABLED_MESSAGE = "Aurral is disabled."

enum class AurralConfirmationType {
	ArtistMonitoring
}

enum class AurralConfirmationStatus {
	Pending,
	Confirmed,
	Failed
}

private enum class AurralConfirmationPollResult {
	Pending,
	Confirmed,
	Failed
}

data class AurralConfirmationQueueItem(
	val id: String,
	val type: AurralConfirmationType,
	val status: AurralConfirmationStatus,
	val title: String,
	val artistMbid: String? = null,
	val expectedMonitored: Boolean? = null,
	val message: String? = null,
	val updatedAtMillis: Long
)

class AurralRepository(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient = KtorAurralApiClient(),
	private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
	private val metadataCache: AurralMetadataCache = NoOpAurralMetadataCache,
	private val confirmationWorkerEnabled: Boolean = false
) {
	private val optimisticAlbumRequestsByMbid = mutableMapOf<String, AurralAlbumRequest>()
	private val optimisticArtistMonitoringByMbid = mutableMapOf<String, Boolean>()
	private val releaseGroupCoverUrlsByMbid = mutableMapOf<String, String>()
	private val discoverArtistImageUrlsByName = mutableMapOf<String, String?>()
	private var libraryArtistsCache: AurralLibraryArtistsCacheEntry? = null
	private val confirmationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val confirmationJobs = mutableMapOf<String, Job>()
	private val _confirmationQueue = MutableStateFlow<List<AurralConfirmationQueueItem>>(emptyList())
	val confirmationQueue = _confirmationQueue.asStateFlow()
	private val _artistStateRevision = MutableStateFlow(0)
	val artistStateRevision = _artistStateRevision.asStateFlow()

	init {
		preferenceManager.addIntegrationEnabledChangeListener(IntegrationService.Aurral) { enabled ->
			if (!enabled) {
				cancelConfirmationWork(clearQueue = true)
			}
		}
	}

	suspend fun testConnection(): AurralConnectionResult {
		if (!preferenceManager.aurralEnabled) return AurralConnectionResult.Failed(AURRAL_DISABLED_MESSAGE)
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return AurralConnectionResult.Failed(baseUrlError)
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return AurralConnectionResult.Failed(AURRAL_BASE_URL_REQUIRED_MESSAGE)
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

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
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.fetchServiceStatus(baseUrl, requestHeaders)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral service status failed", error)
		}.recordAurralAvailability()
	}

	suspend fun getActivityStatus(): Result<AurralServiceStatus> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.fetchActivityStatus(baseUrl, requestHeaders)
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			val discovery = cachedAurralPayload<AurralDiscoverySummary>(
				baseUrl = baseUrl,
				payloadType = AurralMetadataPayloadType.Discovery,
				path = "summary",
				operation = "Aurral discovery"
			) {
				apiClient.fetchDiscovery(baseUrl, requestHeaders)
			}
				.withLibraryArtists(
					libraryArtists = getCachedLibraryArtists(baseUrl, requestHeaders)
				)
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

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
					) ?: runCatching {
						apiClient.fetchLibraryArtistMonitoring(
							baseUrl = baseUrl,
							requestHeaders = requestHeaders,
							artistMbid = resolvedArtist.artistMbid
						)
					}.onSuccess { monitored ->
						monitored?.let {
							rememberLibraryArtistMonitoring(
								baseUrl = baseUrl,
								requestHeaders = requestHeaders,
								artistMbid = resolvedArtist.artistMbid,
								artistName = resolvedArtist.artistName,
								monitored = it
							)
						}
					}.onFailure { error ->
						Logger.w(TAG, "Aurral library artist monitoring lookup failed for $artistName", error)
					}.getOrNull()
				}
				enrichment.await().withLocalArtistState(libraryArtistMonitoring.await())
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral artist enrichment failed for $artistName", error)
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.cancelAcquisitionRequest(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				target = target
			)
			item.albumMbid.normalizedAurralCacheKey()?.let { albumKey ->
				if (optimisticAlbumRequestsByMbid.remove(albumKey) != null) {
					bumpArtistStateRevision()
				}
			}
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
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
			rememberOptimisticAlbumRequest(payload)
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
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
			rememberOptimisticAlbumRequest(payload)
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
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
			rememberOptimisticAlbumRequest(payload)
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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
		val payload = AurralArtistMonitorPayload(
			foreignArtistId = artistMbid,
			artistName = artistName,
			monitorOption = if (monitored) "all" else "none",
			monitored = monitored
		)
		val confirmationId = aurralArtistMonitoringConfirmationId(artistMbid)

		upsertConfirmationQueueItem(
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
				startArtistMonitoringConfirmationWorker(
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
			upsertConfirmationQueueItem(
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
	): Result<AurralFlowActionResult> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
		val payload = runCatching {
			aurralDefaultFlowCreatePayload(
				name = name,
				size = size,
				scheduleDay = scheduleDay
			)
		}.getOrElse { error ->
			return Result.failure(IllegalStateException(error.message ?: "Flow details are invalid."))
		}

		return runCatching {
			apiClient.createFlow(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow creation failed for ${payload.name}", error)
		}.recordAurralAvailability()
	}

	suspend fun setFlowEnabled(
		flowId: String,
		enabled: Boolean
	): Result<AurralFlowActionResult> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.setFlowEnabled(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				enabled = enabled
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow enable update failed for $trimmedFlowId", error)
		}.recordAurralAvailability()
	}

	suspend fun startFlow(
		flowId: String,
		limit: Int
	): Result<AurralFlowActionResult> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val safeLimit = limit.takeIf { it > 0 } ?: 30
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			apiClient.startFlow(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				limit = safeLimit
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow start failed for $trimmedFlowId", error)
		}.recordAurralAvailability()
	}

	suspend fun getFlowPlayableSongs(
		flowId: String,
		limit: Int = 200
	): Result<List<DomainSong>> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		if (!preferenceManager.aurralEnabled) return Result.success(emptyList())
		val safeLimit = limit.takeIf { it > 0 } ?: 200
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

		return runCatching {
			val readyJobs = apiClient.fetchFlowJobs(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				limit = safeLimit
			).filter { it.status.equals("done", ignoreCase = true) }
			if (readyJobs.isEmpty()) return@runCatching emptyList()

			val sessionToken = aurralLoginSessionToken(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders
			)
			val streamToken = if (sessionToken == null && requestHeaders.isNotEmpty()) {
				runCatching {
					apiClient.fetchStreamToken(baseUrl, requestHeaders)?.token?.trim()?.takeIf { it.isNotEmpty() }
				}.getOrNull()
			} else {
				null
			}
			val allowUnauthenticatedStream = sessionToken == null &&
				streamToken == null &&
				requestHeaders.isEmpty()

			readyJobs.mapNotNull { job ->
				job.toDomainSong(
					baseUrl = baseUrl,
					sessionToken = sessionToken,
					streamToken = streamToken,
					allowUnauthenticatedStream = allowUnauthenticatedStream
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow playable songs failed for $trimmedFlowId", error)
		}.recordAurralAvailability()
	}

	private suspend fun aurralLoginSessionToken(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): String? {
		val username = preferenceManager.aurralUsername.trim().takeIf { it.isNotEmpty() }
			?: return null
		val password = preferenceManager.aurralPassword.trim().takeIf { it.isNotEmpty() }
			?: return null
		return runCatching {
			apiClient.login(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				username = username,
				password = password
			)?.token?.trim()?.takeIf { it.isNotEmpty() }
		}.getOrNull()
	}

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
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()

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
					rememberReleaseGroupCoverUrl(releaseGroupMbid, coverUrl)
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
		crossinline fetch: suspend () -> T
	): T {
		val cacheKey = aurralMetadataCacheKey(baseUrl, payloadType, path)
		val currentTime = nowMillis()
		val cached = runCatching { metadataCache.get(cacheKey) }
			.onFailure { error -> Logger.w(TAG, "Aurral metadata cache read failed for $operation", error) }
			.getOrNull()
		cached
			?.takeIf { it.isFreshAurralMetadata(currentTime) }
			?.decodeAurralMetadata<T>(operation)
			?.let { return it }

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

	private fun rememberOptimisticAlbumRequest(payload: AurralAlbumRequestPayload) {
		val albumKey = payload.albumMbid.normalizedAurralCacheKey() ?: return
		val request = AurralAlbumRequest(
			albumMbid = payload.albumMbid,
			albumName = payload.albumName,
			artistMbid = payload.artistMbid,
			artistName = payload.artistName,
			status = "requested"
		)
		if (optimisticAlbumRequestsByMbid[albumKey] == request) return
		optimisticAlbumRequestsByMbid[albumKey] = request
		bumpArtistStateRevision()
	}

	private fun rememberOptimisticArtistMonitoring(
		artistMbid: String,
		artistName: String,
		monitored: Boolean
	) {
		val artistKey = artistMbid.normalizedAurralCacheKey() ?: return
		if (optimisticArtistMonitoringByMbid[artistKey] == monitored) return
		optimisticArtistMonitoringByMbid[artistKey] = monitored
		libraryArtistsCache = libraryArtistsCache?.withMonitoring(
			artistMbid = artistMbid,
			artistName = artistName,
			monitored = monitored
		)
		bumpArtistStateRevision()
	}

	private fun rememberReleaseGroupCoverUrl(
		releaseGroupMbid: String,
		coverUrl: String
	) {
		val releaseGroupKey = releaseGroupMbid.normalizedAurralCacheKey() ?: return
		if (releaseGroupCoverUrlsByMbid[releaseGroupKey] == coverUrl) return
		releaseGroupCoverUrlsByMbid[releaseGroupKey] = coverUrl
		bumpArtistStateRevision()
	}

	private fun bumpArtistStateRevision() {
		_artistStateRevision.value = _artistStateRevision.value + 1
	}

	private fun upsertConfirmationQueueItem(item: AurralConfirmationQueueItem) {
		val retained = _confirmationQueue.value
			.filterNot { queued -> queued.id == item.id }
			.takeLast(AURRAL_CONFIRMATION_QUEUE_RETAINED_ITEMS - 1)
		_confirmationQueue.value = retained + item
	}

	private fun cancelConfirmationWork(clearQueue: Boolean) {
		confirmationJobs.values.forEach { job -> job.cancel() }
		confirmationJobs.clear()
		if (clearQueue && _confirmationQueue.value.isNotEmpty()) {
			_confirmationQueue.value = emptyList()
			bumpArtistStateRevision()
		}
	}

	private fun startArtistMonitoringConfirmationWorker(
		confirmationId: String,
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		payload: AurralArtistMonitorPayload
	) {
		confirmationJobs.remove(confirmationId)?.cancel()
		confirmationJobs[confirmationId] = confirmationScope.launch {
			try {
				while (true) {
					if (!canRunAurralBackgroundWork()) {
						removeConfirmationQueueItem(confirmationId)
						return@launch
					}
					when (
						pollArtistMonitoringConfirmation(
							baseUrl = baseUrl,
							requestHeaders = requestHeaders,
							artistMbid = artistMbid,
							expectedMonitored = monitored
						)
					) {
						AurralConfirmationPollResult.Confirmed -> {
							upsertConfirmationQueueItem(
								AurralConfirmationQueueItem(
									id = confirmationId,
									type = AurralConfirmationType.ArtistMonitoring,
									status = AurralConfirmationStatus.Confirmed,
									title = artistName,
									artistMbid = artistMbid,
									expectedMonitored = monitored,
									message = if (monitored) {
										"Aurral confirmed artist monitoring."
									} else {
										"Aurral confirmed monitoring stopped."
									},
									updatedAtMillis = nowMillis()
								)
							)
							rememberOptimisticArtistMonitoring(artistMbid, artistName, monitored)
							return@launch
						}

						AurralConfirmationPollResult.Failed -> {
							markArtistMonitoringConfirmationFailed(
								confirmationId = confirmationId,
								artistMbid = artistMbid,
								artistName = artistName,
								monitored = monitored,
								message = "Aurral monitor confirmation failed."
							)
							return@launch
						}

						AurralConfirmationPollResult.Pending -> Unit
					}

					delay(AURRAL_CONFIRMATION_REEMIT_DELAY)
					if (!canRunAurralBackgroundWork()) {
						removeConfirmationQueueItem(confirmationId)
						return@launch
					}
					runCatching {
						apiClient.monitorArtist(
							baseUrl = baseUrl,
							requestHeaders = requestHeaders,
							artistMbid = artistMbid,
							payload = payload
						)
					}.onFailure { error ->
						markArtistMonitoringConfirmationFailed(
							confirmationId = confirmationId,
							artistMbid = artistMbid,
							artistName = artistName,
							monitored = monitored,
							message = error.message ?: error::class.simpleName ?: "Aurral monitor request failed."
						)
						Logger.w(TAG, "Aurral artist monitoring re-request failed for $artistName", error)
						return@launch
					}
				}
			} finally {
				confirmationJobs.remove(confirmationId)
			}
		}
	}

	private fun canRunAurralBackgroundWork(): Boolean =
		preferenceManager.aurralEnabled &&
			configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null

	private fun removeConfirmationQueueItem(confirmationId: String) {
		val updated = _confirmationQueue.value.filterNot { item -> item.id == confirmationId }
		if (updated.size != _confirmationQueue.value.size) {
			_confirmationQueue.value = updated
			bumpArtistStateRevision()
		}
	}

	private suspend fun pollArtistMonitoringConfirmation(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		expectedMonitored: Boolean
	): AurralConfirmationPollResult {
		repeat(AURRAL_MONITOR_CONFIRMATION_POLLS_PER_CYCLE) { attempt ->
			if (!canRunAurralBackgroundWork()) {
				return AurralConfirmationPollResult.Pending
			}
			val monitored = runCatching {
				apiClient.fetchLibraryArtistMonitoring(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					artistMbid = artistMbid
				)
			}.getOrElse { error ->
				Logger.w(TAG, "Aurral artist monitoring confirmation lookup failed", error)
				return AurralConfirmationPollResult.Failed
			}
			if (monitored == expectedMonitored) return AurralConfirmationPollResult.Confirmed
			if (monitored == null) return AurralConfirmationPollResult.Failed
			if (attempt < AURRAL_MONITOR_CONFIRMATION_POLLS_PER_CYCLE - 1) {
				delay(AURRAL_MONITOR_CONFIRMATION_DELAY)
			}
		}
		return AurralConfirmationPollResult.Pending
	}

	private fun markArtistMonitoringConfirmationFailed(
		confirmationId: String,
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		message: String
	) {
		upsertConfirmationQueueItem(
			AurralConfirmationQueueItem(
				id = confirmationId,
				type = AurralConfirmationType.ArtistMonitoring,
				status = AurralConfirmationStatus.Failed,
				title = artistName,
				artistMbid = artistMbid,
				expectedMonitored = monitored,
				message = message,
				updatedAtMillis = nowMillis()
			)
		)
	}

	private suspend fun getCachedLibraryArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> {
		val cacheKey = aurralLibraryArtistsCacheKey(baseUrl, requestHeaders)
		val currentTime = nowMillis()
		libraryArtistsCache
			?.takeIf { it.key == cacheKey && it.isFresh(currentTime) }
			?.let { return it.artists.withOptimisticMonitoring() }

		val cachedFallback = libraryArtistsCache?.takeIf { it.key == cacheKey }
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
			libraryArtistsCache = AurralLibraryArtistsCacheEntry(
				key = cacheKey,
				artists = artists.withOptimisticMonitoring(),
				loadedAtMillis = currentTime
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral library artists failed", error)
		}.getOrElse {
			cachedFallback?.artists?.withOptimisticMonitoring().orEmpty()
		}
	}

	private fun getCachedLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): Boolean? {
		val cacheKey = aurralLibraryArtistsCacheKey(baseUrl, requestHeaders)
		val currentTime = nowMillis()
		return artistMbid.normalizedAurralCacheKey()?.let(optimisticArtistMonitoringByMbid::get)
			?: libraryArtistsCache
			?.takeIf { it.key == cacheKey && it.isFresh(currentTime) }
			?.artists
			?.findAurralLibraryArtist(artistMbid, artistName)
			?.monitored
	}

	private fun rememberLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String,
		monitored: Boolean
	) {
		val cacheKey = aurralLibraryArtistsCacheKey(baseUrl, requestHeaders)
		val currentTime = nowMillis()
		val current = libraryArtistsCache?.takeIf { it.key == cacheKey }
		libraryArtistsCache = (current ?: AurralLibraryArtistsCacheEntry(
			key = cacheKey,
			artists = emptyList(),
			loadedAtMillis = currentTime
		)).withMonitoring(
			artistMbid = artistMbid,
			artistName = artistName,
			monitored = monitored,
			loadedAtMillis = currentTime
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

	private fun AurralArtistEnrichment.withLocalArtistState(
		libraryArtistMonitoring: Boolean?
	): AurralArtistEnrichment {
		val existingRequestKeys = requests
			.mapNotNull { request -> request.albumMbid.normalizedAurralCacheKey() }
			.toSet()
		val artistKey = artistMbid.normalizedAurralCacheKey()
		val mergedRequests = requests + optimisticAlbumRequestsByMbid.values.filter { request ->
			val requestAlbumKey = request.albumMbid.normalizedAurralCacheKey()
			val requestArtistKey = request.artistMbid.normalizedAurralCacheKey()
			requestAlbumKey != null &&
				requestAlbumKey !in existingRequestKeys &&
				(artistKey == null || requestArtistKey == null || requestArtistKey == artistKey)
		}
		val releaseGroupsWithCovers = releaseGroups.map { releaseGroup ->
			if (!releaseGroup.coverUrl.isNullOrBlank()) {
				releaseGroup
			} else {
				releaseGroup.id.normalizedAurralCacheKey()
					?.let(releaseGroupCoverUrlsByMbid::get)
					?.let { coverUrl -> releaseGroup.copy(coverUrl = coverUrl) }
					?: releaseGroup
			}
		}

		return copy(
			releaseGroups = releaseGroupsWithCovers,
			requests = mergedRequests,
			monitored = artistKey?.let(optimisticArtistMonitoringByMbid::get)
				?: monitored
				?: libraryArtistMonitoring
		)
	}

	private fun AurralDiscoverySummary.withLibraryArtists(
		libraryArtists: List<AurralDiscoverArtist>
	): AurralDiscoverySummary {
		val monitoredLibraryArtists = libraryArtists.withOptimisticMonitoring()
		return copy(
			recentlyAdded = recentlyAdded.withLibraryArtistMonitoring(monitoredLibraryArtists),
			recommendations = recommendations.withLibraryArtistMonitoring(monitoredLibraryArtists),
			globalTop = globalTop.withLibraryArtistMonitoring(monitoredLibraryArtists),
			basedOn = basedOn.withLibraryArtistMonitoring(monitoredLibraryArtists),
			libraryArtists = monitoredLibraryArtists,
			fallbackGenres = fallbackGenres.map { section ->
				section.copy(artists = section.artists.withLibraryArtistMonitoring(monitoredLibraryArtists))
			}
		)
	}

	private fun List<AurralDiscoverArtist>.withOptimisticMonitoring(): List<AurralDiscoverArtist> =
		map { artist ->
			artist.id.normalizedAurralCacheKey()
				?.let(optimisticArtistMonitoringByMbid::get)
				?.let { monitored -> artist.copy(monitored = monitored) }
				?: artist
		}
}

private data class AurralLibraryArtistsCacheEntry(
	val key: String,
	val artists: List<AurralDiscoverArtist>,
	val loadedAtMillis: Long
) {
	fun isFresh(nowMillis: Long): Boolean =
		nowMillis >= loadedAtMillis &&
			nowMillis - loadedAtMillis < AURRAL_LIBRARY_ARTISTS_CACHE_TTL.inWholeMilliseconds

	fun withMonitoring(
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		loadedAtMillis: Long = this.loadedAtMillis
	): AurralLibraryArtistsCacheEntry {
		val updated = artists.updateAurralLibraryArtistMonitoring(
			artistMbid = artistMbid,
			artistName = artistName,
			monitored = monitored
		)
		return copy(artists = updated, loadedAtMillis = loadedAtMillis)
	}
}

private data class ResolvedAurralArtist(
	val artistMbid: String,
	val artistName: String
)

@Serializable
private data class AurralCachedString(
	val value: String? = null
)

private fun aurralLibraryArtistsCacheKey(
	baseUrl: String,
	requestHeaders: Map<String, String>
): String =
	buildString {
		append(baseUrl.trimEnd('/'))
		requestHeaders.entries.sortedBy { entry -> entry.key }.forEach { (key, value) ->
			append('|')
			append(key.lowercase())
			append('=')
			append(value.hashCode())
		}
	}

private fun aurralSearchCachePath(
	query: String,
	limit: Int,
	offset: Int
): String =
	listOf(
		"query=${query.normalizedAurralSearchName().orEmpty()}",
		"limit=${limit.coerceAtLeast(1)}",
		"offset=${offset.coerceAtLeast(0)}"
	).joinToString("|")

private fun aurralAlbumTracksCachePath(
	releaseGroupMbid: String,
	libraryAlbumId: String?
): String =
	listOf(
		"releaseGroup=${releaseGroupMbid.normalizedAurralCacheKey().orEmpty()}",
		"libraryAlbum=${libraryAlbumId.normalizedAurralCacheKey().orEmpty()}"
	).joinToString("|")

private fun aurralArtistEnrichmentCachePath(
	artistMbid: String,
	artistName: String
): String =
	listOf(
		"artist=${artistMbid.normalizedAurralCacheKey().orEmpty()}",
		"name=${artistName.normalizedAurralSearchName().orEmpty()}"
	).joinToString("|")

private fun aurralReleaseGroupCoverCachePath(
	releaseGroupMbid: String,
	artistName: String,
	albumTitle: String
): String =
	listOf(
		"releaseGroup=${releaseGroupMbid.normalizedAurralCacheKey().orEmpty()}",
		"artist=${artistName.normalizedAurralSearchName().orEmpty()}",
		"album=${albumTitle.normalizedAurralSearchName().orEmpty()}"
	).joinToString("|")

private fun aurralArtistMonitoringConfirmationId(artistMbid: String): String =
	"artist-monitor:${artistMbid.normalizedAurralCacheKey() ?: artistMbid.trim()}"

fun aurralArtistMonitoringConfirmationItem(
	queue: List<AurralConfirmationQueueItem>,
	artistMbid: String?
): AurralConfirmationQueueItem? {
	val normalizedMbid = artistMbid.normalizedAurralCacheKey() ?: return null
	val confirmationId = aurralArtistMonitoringConfirmationId(normalizedMbid)
	return queue.lastOrNull { item ->
		item.type == AurralConfirmationType.ArtistMonitoring &&
			(item.id == confirmationId || item.artistMbid.normalizedAurralCacheKey() == normalizedMbid)
	}
}

private fun List<AurralDiscoverArtist>.findAurralLibraryArtist(
	artistMbid: String,
	artistName: String
): AurralDiscoverArtist? {
	val artistKey = artistMbid.normalizedAurralCacheKey()
	val artistNameKey = artistName.normalizedAurralImageLookupName()
	return firstOrNull { artist ->
		(artistKey != null && artist.id.normalizedAurralCacheKey() == artistKey) ||
			(artistNameKey != null && artist.name.normalizedAurralImageLookupName() == artistNameKey)
	}
}

private fun String?.normalizedAurralSearchName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun List<AurralDiscoverArtist>.updateAurralLibraryArtistMonitoring(
	artistMbid: String,
	artistName: String,
	monitored: Boolean
): List<AurralDiscoverArtist> {
	var matched = false
	val updated = map { artist ->
		if (artist.matchesAurralLibraryArtist(artistMbid, artistName)) {
			matched = true
			artist.copy(monitored = monitored)
		} else {
			artist
		}
	}
	return if (matched) {
		updated
	} else {
		updated + AurralDiscoverArtist(
			id = artistMbid,
			name = artistName,
			monitored = monitored
		)
	}
}

private fun AurralDiscoverArtist.matchesAurralLibraryArtist(
	artistMbid: String,
	artistName: String
): Boolean {
	val artistKey = artistMbid.normalizedAurralCacheKey()
	val artistNameKey = artistName.normalizedAurralImageLookupName()
	return (artistKey != null && id.normalizedAurralCacheKey() == artistKey) ||
		(artistNameKey != null && name.normalizedAurralImageLookupName() == artistNameKey)
}

private fun String?.normalizedAurralCacheKey(): String? =
	this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralImageLookupName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

interface AurralApiClient {
	suspend fun testConnection(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralConnectionResult

	suspend fun fetchServiceStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus

	suspend fun fetchActivityStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus =
		fetchServiceStatus(baseUrl, requestHeaders)

	suspend fun fetchDiscovery(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralDiscoverySummary = error("Aurral discovery is not supported by this client.")

	suspend fun fetchLibraryArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> = emptyList()

	suspend fun searchArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralArtistSearchRequest
	): AurralArtistSearchResult = error("Aurral artist search is not supported by this client.")

	suspend fun searchAlbums(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralAlbumSearchRequest
	): AurralAlbumSearchResult = error("Aurral album search is not supported by this client.")

	suspend fun fetchAlbumTracks(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		libraryAlbumId: String?
	): List<AurralAlbumTrackItem> = error("Aurral album tracks are not supported by this client.")

	suspend fun fetchArtistEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment

	suspend fun fetchLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String
	): Boolean?

	suspend fun requestAlbum(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralAlbumRequestPayload
	)

	suspend fun cancelAcquisitionRequest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		target: AurralAcquisitionDeleteTarget
	): Unit = error("Aurral request cancellation is not supported by this client.")

	suspend fun monitorArtist(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		payload: AurralArtistMonitorPayload
	)

	suspend fun fetchReleaseGroupCoverImageUrl(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		artistName: String,
		albumTitle: String
	): String?

	suspend fun createFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralFlowCreatePayload
	): AurralFlowActionResult = error("Aurral Flow creation is not supported by this client.")

	suspend fun setFlowEnabled(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		enabled: Boolean
	): AurralFlowActionResult = error("Aurral Flow updates are not supported by this client.")

	suspend fun startFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): AurralFlowActionResult = error("Aurral Flow starts are not supported by this client.")

	suspend fun fetchFlowJobs(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): List<AurralFlowJobDto> = error("Aurral Flow jobs are not supported by this client.")

	suspend fun login(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		username: String,
		password: String
	): AurralAuthSessionDto? = null

	suspend fun fetchStreamToken(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralStreamTokenDto? = null
}

private class KtorAurralApiClient : AurralApiClient {
	private val client = HttpClient {
		install(HttpTimeout) {
			requestTimeoutMillis = 30000
			connectTimeoutMillis = 30000
			socketTimeoutMillis = 30000
		}
		install(ContentNegotiation) {
			json(AURRAL_JSON)
		}
	}

	override suspend fun testConnection(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralConnectionResult {
		val response = client.get(aurralEndpoint(baseUrl, "api/health")) {
			aurralJsonRequest(requestHeaders)
		}
		return aurralConnectionResult("Aurral health", response.status)
	}

	override suspend fun fetchServiceStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus {
		val healthResponse = client.get(aurralEndpoint(baseUrl, "api/health")) {
			aurralJsonRequest(requestHeaders)
		}
		if (!healthResponse.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral health", healthResponse.status))
		}
		val health = healthResponse.body<AurralHealthDto>()

		val authMe = fetchAuthMe(baseUrl, requestHeaders)
		val weeklyFlow = fetchWeeklyFlowStatus(baseUrl, requestHeaders)
		val requests = fetchRequests(baseUrl, requestHeaders)

		return aurralServiceStatus(
			health = health,
			authMe = authMe,
			weeklyFlow = weeklyFlow,
			requests = requests
		)
	}

	override suspend fun fetchActivityStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus {
		val authMe = fetchAuthMe(baseUrl, requestHeaders)
		val weeklyFlow = fetchWeeklyFlowStatus(baseUrl, requestHeaders)
		val requests = fetchRequests(baseUrl, requestHeaders)

		return aurralServiceStatus(
			health = AurralHealthDto(),
			authMe = authMe,
			weeklyFlow = weeklyFlow,
			requests = requests
		)
	}

	override suspend fun fetchDiscovery(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralDiscoverySummary {
		val response = client.get(aurralEndpoint(baseUrl, "api/discover")) {
			aurralJsonRequest(requestHeaders)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral Discover", response.status))
		}
		val discovery = response.body<AurralDiscoveryResponseDto>()
		val recentlyAdded = runCatching {
			fetchRecentlyAdded(baseUrl, requestHeaders)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral recently added failed", error)
		}.getOrDefault(emptyList())
		val recentReleases = runCatching {
			fetchRecentReleases(baseUrl, requestHeaders)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral recent releases failed", error)
		}.getOrDefault(emptyList())
		return aurralDiscoverySummary(
			baseUrl = baseUrl,
			response = discovery,
			recentlyAdded = recentlyAdded,
			recentReleases = recentReleases
		)
	}

	private suspend fun fetchRecentlyAdded(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> {
		val response = client.get(aurralEndpoint(baseUrl, "api/library/recent")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> aurralRecentlyAddedArtists(
				baseUrl = baseUrl,
				response = response.body()
			)
			response.status == HttpStatusCode.Unauthorized -> emptyList()
			response.status == HttpStatusCode.Forbidden -> emptyList()
			response.status == HttpStatusCode.NotFound -> emptyList()
			else -> error(aurralHttpErrorMessage("Aurral recently added", response.status))
		}
	}

	override suspend fun fetchLibraryArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralDiscoverArtist> {
		val response = client.get(aurralEndpoint(baseUrl, "api/library/artists")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> aurralLibraryArtists(
				baseUrl = baseUrl,
				response = response.body()
			)
			response.status == HttpStatusCode.Unauthorized -> emptyList()
			response.status == HttpStatusCode.Forbidden -> emptyList()
			response.status == HttpStatusCode.NotFound -> emptyList()
			else -> error(aurralHttpErrorMessage("Aurral library artists", response.status))
		}
	}

	private suspend fun fetchRecentReleases(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralAlbumSearchItem> {
		val response = client.get(aurralEndpoint(baseUrl, "api/library/recent-releases")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> aurralRecentReleases(
				baseUrl = baseUrl,
				response = response.body()
			)
			response.status == HttpStatusCode.Unauthorized -> emptyList()
			response.status == HttpStatusCode.Forbidden -> emptyList()
			response.status == HttpStatusCode.NotFound -> emptyList()
			else -> error(aurralHttpErrorMessage("Aurral recent releases", response.status))
		}
	}

	override suspend fun searchArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralArtistSearchRequest
	): AurralArtistSearchResult {
		val response = client.get(aurralEndpoint(baseUrl, "api/search/artists")) {
			aurralJsonRequest(requestHeaders)
			parameter("query", request.query)
			parameter("limit", request.limit)
			parameter("offset", request.offset)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral artist search", response.status))
		}
		return aurralArtistSearchResult(
			baseUrl = baseUrl,
			query = request.query,
			response = response.body()
		)
	}

	override suspend fun searchAlbums(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		request: AurralAlbumSearchRequest
	): AurralAlbumSearchResult {
		val response = client.get(aurralEndpoint(baseUrl, "api/search")) {
			aurralJsonRequest(requestHeaders)
			parameter("q", request.query)
			parameter("scope", "album")
			parameter("limit", request.limit)
			parameter("offset", request.offset)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral album search", response.status))
		}
		return aurralAlbumSearchResult(
			baseUrl = baseUrl,
			query = request.query,
			response = response.body()
		)
	}

	override suspend fun fetchAlbumTracks(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		libraryAlbumId: String?
	): List<AurralAlbumTrackItem> {
		if (libraryAlbumId != null) {
			val libraryTracks = fetchAlbumTracksOrNull(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				path = "api/library/tracks",
				parameters = mapOf(
					"albumId" to libraryAlbumId,
					"releaseGroupMbid" to releaseGroupMbid
				),
				operation = "Aurral library album tracks"
			)
			if (!libraryTracks.isNullOrEmpty()) return libraryTracks
		}
		return fetchAlbumTracksOrNull(
			baseUrl = baseUrl,
			requestHeaders = requestHeaders,
			path = "api/artists/release-group/${encodeUrlComponent(releaseGroupMbid)}/tracks",
			parameters = emptyMap(),
			operation = "Aurral release-group tracks"
		).orEmpty()
	}

	private suspend fun fetchAlbumTracksOrNull(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String,
		parameters: Map<String, String>,
		operation: String
	): List<AurralAlbumTrackItem>? {
		val response = client.get(aurralEndpoint(baseUrl, path)) {
			aurralJsonRequest(requestHeaders)
			parameters.forEach { (key, value) -> parameter(key, value) }
		}
		return when {
			response.status.isSuccess() -> aurralAlbumTrackItems(
				decodeAurralAlbumTracks(response.bodyAsText())
			)
			response.status == HttpStatusCode.NotFound -> null
			response.status == HttpStatusCode.Unauthorized -> emptyList()
			response.status == HttpStatusCode.Forbidden -> emptyList()
			else -> error(aurralHttpErrorMessage(operation, response.status))
		}
	}

	override suspend fun fetchArtistEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment = coroutineScope {
		val details = async {
			fetchArtistDetails(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		}
		val preview = async {
			fetchArtistPreview(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		}
		val similar = async {
			fetchSimilarArtists(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				artistName = artistName
			)
		}
		val requests = async { fetchRequests(baseUrl, requestHeaders) }

		aurralArtistEnrichment(
			baseUrl = baseUrl,
			details = details.await(),
			preview = preview.await(),
			similar = similar.await(),
			requests = requests.await()
		)
	}

	override suspend fun fetchLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String
	): Boolean? {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/library/artists/${encodeUrlComponent(artistMbid)}")
		) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status.isSuccess() -> response.body<AurralLibraryArtistDto>().monitored ?: false
			response.status == HttpStatusCode.NotFound -> false
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			else -> error(aurralHttpErrorMessage("Aurral library artist lookup", response.status))
		}
	}

	private suspend fun fetchArtistDetails(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistDetailsDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${encodeUrlComponent(artistMbid)}")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
			parameter("mode", "core")
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral artist details", response.status))
		}
		return response.body()
	}

	private suspend fun fetchArtistPreview(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistPreviewDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${encodeUrlComponent(artistMbid)}/preview")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
		}
		return when {
			response.status.isSuccess() -> response.body()
			response.status == HttpStatusCode.Unauthorized -> AurralArtistPreviewDto()
			response.status == HttpStatusCode.Forbidden -> AurralArtistPreviewDto()
			else -> AurralArtistPreviewDto()
		}
	}

	private suspend fun fetchSimilarArtists(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralSimilarArtistsDto {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/artists/${encodeUrlComponent(artistMbid)}/similar")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("artistName", artistName)
			parameter("limit", 20)
		}
		return when {
			response.status.isSuccess() -> response.body()
			response.status == HttpStatusCode.Unauthorized -> AurralSimilarArtistsDto()
			response.status == HttpStatusCode.Forbidden -> AurralSimilarArtistsDto()
			else -> AurralSimilarArtistsDto()
		}
	}

	private suspend fun fetchAuthMe(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralAuthMeDto? {
		val response = client.get(aurralEndpoint(baseUrl, "api/auth/me")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral auth", response.status))
		}
	}

	private suspend fun fetchWeeklyFlowStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralWeeklyFlowStatusDto? {
		val response = client.get(aurralEndpoint(baseUrl, "api/weekly-flow/status?includeJobs=false")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral Flow status", response.status))
		}
	}

	private suspend fun fetchRequests(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): List<AurralRequestDto> {
		val response = client.get(aurralEndpoint(baseUrl, "api/requests")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> emptyList()
			response.status == HttpStatusCode.Forbidden -> emptyList()
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral requests", response.status))
		}
	}

	override suspend fun requestAlbum(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralAlbumRequestPayload
	) {
		val response = client.post(aurralEndpoint(baseUrl, "api/library/albums/request")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(payload)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral album request", response.status))
		}
	}

	override suspend fun cancelAcquisitionRequest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		target: AurralAcquisitionDeleteTarget
	) {
		val path = when (target) {
			is AurralAcquisitionDeleteTarget.Album -> {
				"api/requests/album/${encodeUrlComponent(target.albumId)}"
			}
			is AurralAcquisitionDeleteTarget.Artist -> {
				"api/requests/${encodeUrlComponent(target.artistMbid)}"
			}
		}
		val response = client.delete(aurralEndpoint(baseUrl, path)) {
			aurralJsonRequest(requestHeaders)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral request cancellation", response.status))
		}
	}

	override suspend fun createFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralFlowCreatePayload
	): AurralFlowActionResult {
		val response = client.post(aurralEndpoint(baseUrl, "api/weekly-flow/flows")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(payload)
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral Flow creation", response.status))
		}
		return response.body<AurralFlowActionDto>().toResult()
	}

	override suspend fun setFlowEnabled(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		enabled: Boolean
	): AurralFlowActionResult {
		val response = client.put(
			aurralEndpoint(baseUrl, "api/weekly-flow/flows/${encodeUrlComponent(flowId)}/enabled")
		) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(AurralFlowEnabledPayload(enabled))
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral Flow update", response.status))
		}
		return response.body<AurralFlowActionDto>().toResult()
	}

	override suspend fun startFlow(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): AurralFlowActionResult {
		val response = client.post(
			aurralEndpoint(baseUrl, "api/weekly-flow/start/${encodeUrlComponent(flowId)}")
		) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(AurralFlowStartPayload(limit))
		}
		if (!response.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral Flow start", response.status))
		}
		return response.body<AurralFlowActionDto>().toResult()
	}

	override suspend fun fetchFlowJobs(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		flowId: String,
		limit: Int
	): List<AurralFlowJobDto> {
		val response = client.get(
			aurralEndpoint(baseUrl, "api/weekly-flow/jobs/${encodeUrlComponent(flowId)}")
		) {
			aurralJsonRequest(requestHeaders)
			parameter("limit", limit)
		}
		return when {
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral Flow jobs", response.status))
		}
	}

	override suspend fun login(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		username: String,
		password: String
	): AurralAuthSessionDto? {
		val response = client.post(aurralEndpoint(baseUrl, "api/auth/login")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(AurralLoginPayload(username = username, password = password))
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral login", response.status))
		}
	}

	override suspend fun fetchStreamToken(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralStreamTokenDto? {
		val response = client.post(aurralEndpoint(baseUrl, "api/health/stream-token")) {
			aurralJsonRequest(requestHeaders)
		}
		return when {
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			response.status.isSuccess() -> response.body()
			else -> error(aurralHttpErrorMessage("Aurral stream token", response.status))
		}
	}

	override suspend fun monitorArtist(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		payload: AurralArtistMonitorPayload
	) {
		val artistEndpoint = aurralEndpoint(baseUrl, "api/library/artists/${encodeUrlComponent(artistMbid)}")
		val existingResponse = client.get(artistEndpoint) {
			aurralJsonRequest(requestHeaders)
		}
		if (existingResponse.status.isSuccess()) {
			val updateResponse = client.put(artistEndpoint) {
				aurralJsonRequest(requestHeaders)
				header("Content-Type", ContentType.Application.Json.toString())
				setBody(payload.toMonitoringUpdatePayload())
			}
			if (updateResponse.status.isSuccess()) {
				return
			}
			error(aurralHttpErrorMessage("Aurral artist monitoring", updateResponse.status))
		}
		if (existingResponse.status != HttpStatusCode.NotFound) {
			error(aurralHttpErrorMessage("Aurral artist lookup", existingResponse.status))
		}
		if (!payload.monitored) {
			return
		}

		val addResponse = client.post(aurralEndpoint(baseUrl, "api/library/artists")) {
			aurralJsonRequest(requestHeaders)
			header("Content-Type", ContentType.Application.Json.toString())
			setBody(payload)
		}
		if (!addResponse.status.isSuccess()) {
			error(aurralHttpErrorMessage("Aurral artist add", addResponse.status))
		}
	}

	override suspend fun fetchReleaseGroupCoverImageUrl(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		artistName: String,
		albumTitle: String
	): String? {
		val response = client.get(
			aurralEndpoint(
				baseUrl,
				"api/artists/release-group/${encodeUrlComponent(releaseGroupMbid)}/cover"
			)
		) {
			aurralJsonRequest(requestHeaders)
			artistName.takeIf { it.isNotBlank() }?.let { parameter("artistName", it) }
			albumTitle.takeIf { it.isNotBlank() }?.let { parameter("albumTitle", it) }
		}
		return when {
			response.status.isSuccess() -> response
				.body<AurralReleaseGroupCoverDto>()
				.images
				.firstNotNullOfOrNull { image -> aurralAbsoluteImageUrl(baseUrl, image.image) }
			response.status == HttpStatusCode.Unauthorized -> null
			response.status == HttpStatusCode.Forbidden -> null
			else -> null
		}
	}
}

private fun HttpRequestBuilder.aurralJsonRequest(
	requestHeaders: Map<String, String> = emptyMap()
) {
	accept(ContentType.Application.Json)
	requestHeaders.forEach { (key, value) ->
		header(key, value)
	}
}

sealed interface AurralConnectionResult {
	data object Connected : AurralConnectionResult
	data object Unauthorized : AurralConnectionResult
	data object Forbidden : AurralConnectionResult
	data class Failed(val message: String) : AurralConnectionResult
}

data class AurralServiceStatus(
	val healthStatus: String = "unknown",
	val appVersion: String? = null,
	val authRequired: Boolean = false,
	val username: String? = null,
	val role: String? = null,
	val accessFlow: Boolean = false,
	val addArtist: Boolean = false,
	val addAlbum: Boolean = false,
	val changeMonitoring: Boolean = false,
	val lidarrConfigured: Boolean = false,
	val discoveryRecommendationsCount: Int = 0,
	val discoveryUpdating: Boolean = false,
	val flowsCount: Int = 0,
	val enabledFlowsCount: Int = 0,
	val sharedPlaylistsCount: Int = 0,
	val requestsCount: Int = 0,
	val flowTracksTotal: Int = 0,
	val flowTracksPending: Int = 0,
	val flowTracksDownloading: Int = 0,
	val flowTracksDone: Int = 0,
	val flowTracksFailed: Int = 0,
	val flowPhase: String? = null,
	val flowMessage: String? = null,
	val flows: List<AurralFlowSummary> = emptyList(),
	val flowCapabilities: AurralFlowCapabilities = AurralFlowCapabilities(),
	val acquisitionQueue: List<AurralAcquisitionQueueItem> = emptyList()
)

sealed class AurralAcquisitionDeleteTarget {
	data class Album(val albumId: String) : AurralAcquisitionDeleteTarget()
	data class Artist(val artistMbid: String) : AurralAcquisitionDeleteTarget()
}

@Serializable
data class AurralDiscoverySummary(
	val recentlyAdded: List<AurralDiscoverArtist> = emptyList(),
	val recommendations: List<AurralDiscoverArtist> = emptyList(),
	val globalTop: List<AurralDiscoverArtist> = emptyList(),
	val basedOn: List<AurralDiscoverArtist> = emptyList(),
	val libraryArtists: List<AurralDiscoverArtist> = emptyList(),
	val recentReleases: List<AurralAlbumSearchItem> = emptyList(),
	val fallbackGenres: List<AurralFallbackGenreSection> = emptyList(),
	val topTags: List<String> = emptyList(),
	val topGenres: List<String> = emptyList(),
	val isUpdating: Boolean = false,
	val stale: Boolean = false,
	val provider: String? = null,
	val discoveryMode: String? = null
)

@Serializable
data class AurralDiscoverArtist(
	val id: String,
	val name: String,
	val imageUrl: String? = null,
	val tags: List<String> = emptyList(),
	val matchedTags: List<String> = emptyList(),
	val reason: String? = null,
	val sourceType: String? = null,
	val discoveryTier: String? = null,
	val monitored: Boolean? = null,
	val recommendedAlbums: List<AurralAlbumSearchItem> = emptyList(),
	val detailsIdVerified: Boolean = false
)

@Serializable
data class AurralFallbackGenreSection(
	val genre: String,
	val artists: List<AurralDiscoverArtist>
)

@Serializable
data class AurralArtistSearchRequest(
	val query: String,
	val limit: Int = 12,
	val offset: Int = 0
)

@Serializable
data class AurralArtistSearchResult(
	val query: String = "",
	val count: Int = 0,
	val offset: Int = 0,
	val artists: List<AurralDiscoverArtist> = emptyList()
)

@Serializable
data class AurralAlbumSearchRequest(
	val query: String,
	val limit: Int = 12,
	val offset: Int = 0
)

@Serializable
data class AurralAlbumSearchResult(
	val query: String = "",
	val count: Int = 0,
	val offset: Int = 0,
	val hasMore: Boolean = false,
	val albums: List<AurralAlbumSearchItem> = emptyList()
)

@Serializable
data class AurralAlbumSearchItem(
	val id: String,
	val title: String,
	val artistName: String,
	val artistMbid: String,
	val releaseDate: String? = null,
	val primaryType: String? = null,
	val secondaryTypes: List<String> = emptyList(),
	val coverUrl: String? = null,
	val inLibrary: Boolean = false,
	val libraryAlbumId: String? = null,
	val libraryArtistId: String? = null,
	val status: String? = null
)

@Serializable
data class AurralAlbumTrackItem(
	val id: String,
	val title: String,
	val artistName: String? = null,
	val recordingMbid: String? = null,
	val discNumber: Int? = null,
	val trackNumber: Int? = null,
	val durationMs: Long? = null,
	val previewUrl: String? = null,
	val status: String? = null,
	val requested: Boolean? = null
)

data class AurralFlowSummary(
	val id: String,
	val name: String,
	val enabled: Boolean,
	val size: Int = 30,
	val nextRunAt: Long? = null,
	val mix: AurralFlowMix = AurralFlowMix(),
	val tags: List<String> = emptyList(),
	val relatedArtists: List<String> = emptyList(),
	val scheduleDays: List<Int> = emptyList(),
	val scheduleTime: String = "00:00",
	val stats: AurralFlowStats = AurralFlowStats()
)

@Serializable
data class AurralFlowMix(
	val discover: Int = 34,
	val mix: Int = 33,
	val trending: Int = 33,
	val focus: Int = 0
)

data class AurralFlowStats(
	val total: Int = 0,
	val pending: Int = 0,
	val downloading: Int = 0,
	val done: Int = 0,
	val failed: Int = 0
)

data class AurralFlowCapabilities(
	val lastfmRequired: Boolean = false,
	val availableSources: List<String> = emptyList(),
	val unavailableSources: Map<String, String> = emptyMap()
)

data class AurralFlowActionResult(
	val success: Boolean = false,
	val flowId: String? = null,
	val enabled: Boolean? = null,
	val tracksQueued: Int = 0,
	val reserveTracks: Int = 0,
	val jobIds: List<String> = emptyList(),
	val message: String? = null,
	val flow: AurralFlowSummary? = null
)

data class AurralAcquisitionQueueItem(
	val id: String,
	val type: String,
	val albumId: String?,
	val albumMbid: String?,
	val albumName: String,
	val artistId: String?,
	val artistMbid: String?,
	val artistName: String,
	val status: String,
	val requestedAt: String?,
	val inQueue: Boolean
)

@Serializable
data class AurralAlbumRequestPayload(
	val albumMbid: String,
	val albumName: String,
	val artistMbid: String,
	val artistName: String,
	val triggerSearch: Boolean = true
)

@Serializable
data class AurralArtistMonitorPayload(
	@SerialName("foreignArtistId") val foreignArtistId: String,
	val artistName: String,
	val monitorOption: String = "all",
	val monitored: Boolean = true
)

@Serializable
data class AurralFlowCreatePayload(
	val name: String,
	val size: Int,
	val mix: AurralFlowMix = AurralFlowMix(),
	val deepDive: Boolean = false,
	val tags: List<String> = emptyList(),
	@SerialName("relatedArtists") val relatedArtists: List<String> = emptyList(),
	@SerialName("scheduleDays") val scheduleDays: List<Int>,
	@SerialName("scheduleTime") val scheduleTime: String = "00:00"
)

@Serializable
private data class AurralFlowEnabledPayload(
	val enabled: Boolean
)

@Serializable
private data class AurralFlowStartPayload(
	val limit: Int
)

@Serializable
internal data class AurralLoginPayload(
	val username: String,
	val password: String
)

@Serializable
data class AurralAuthSessionDto(
	val token: String? = null,
	@SerialName("expiresAt") val expiresAt: String? = null
)

@Serializable
data class AurralStreamTokenDto(
	val token: String? = null,
	@SerialName("expiresIn") val expiresIn: Int? = null
)

@Serializable
data class AurralFlowJobDto(
	val id: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("trackName") val trackName: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	val status: String? = null,
	@SerialName("playlistType") val playlistType: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("albumMbid") val albumMbid: String? = null,
	@SerialName("trackMbid") val trackMbid: String? = null,
	@SerialName("releaseYear") val releaseYear: String? = null,
	@SerialName("durationMs") val durationMs: Long? = null,
	@SerialName("finalPath") val finalPath: String? = null
)

@Serializable
private data class AurralArtistMonitoringUpdatePayload(
	val monitored: Boolean = true,
	val monitorOption: String = "all"
)

@Serializable
internal data class AurralHealthDto(
	val status: String = "unknown",
	@SerialName("appVersion") val appVersion: String? = null,
	@SerialName("authRequired") val authRequired: Boolean = false,
	@SerialName("lidarrConfigured") val lidarrConfigured: Boolean = false,
	@SerialName("rootFolderConfigured") val rootFolderConfigured: Boolean = false,
	val discovery: AurralDiscoveryDto? = null,
	val user: AurralUserDto? = null
)

@Serializable
internal data class AurralDiscoveryDto(
	@SerialName("recommendationsCount") val recommendationsCount: Int = 0,
	@SerialName("isUpdating") val isUpdating: Boolean = false
)

@Serializable
internal data class AurralDiscoveryResponseDto(
	val recommendations: List<AurralDiscoverArtistDto> = emptyList(),
	@SerialName("globalTop") val globalTop: List<AurralDiscoverArtistDto> = emptyList(),
	@SerialName("basedOn") val basedOn: List<AurralDiscoverArtistDto> = emptyList(),
	@SerialName("fallbackGenres") val fallbackGenres: List<AurralFallbackGenreSectionDto> = emptyList(),
	@SerialName("topTags") val topTags: List<String> = emptyList(),
	@SerialName("topGenres") val topGenres: List<String> = emptyList(),
	@SerialName("isUpdating") val isUpdating: Boolean = false,
	val stale: Boolean = false,
	val provider: String? = null,
	@SerialName("discoveryMode") val discoveryMode: String? = null
)

@Serializable
internal data class AurralFallbackGenreSectionDto(
	val genre: String? = null,
	val name: String? = null,
	val title: String? = null,
	val artists: List<AurralDiscoverArtistDto> = emptyList()
)

@Serializable
internal data class AurralArtistSearchResponseDto(
	val artists: List<AurralDiscoverArtistDto> = emptyList(),
	val count: Int = 0,
	val offset: Int = 0
)

@Serializable
internal data class AurralAlbumSearchResponseDto(
	val query: String? = null,
	val count: Int = 0,
	val offset: Int = 0,
	@SerialName("hasMore") val hasMore: Boolean = false,
	val items: List<AurralAlbumSearchItemDto> = emptyList()
)

@Serializable
internal data class AurralAlbumSearchItemDto(
	val id: String? = null,
	val mbid: String? = null,
	@SerialName("foreignAlbumId") val foreignAlbumId: String? = null,
	val title: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("foreignArtistId") val foreignArtistId: String? = null,
	@SerialName("releaseDate") val releaseDate: String? = null,
	@SerialName("primaryType") val primaryType: String? = null,
	@SerialName("secondaryTypes") val secondaryTypes: List<String> = emptyList(),
	@SerialName("coverUrl") val coverUrl: String? = null,
	@SerialName("inLibrary") val inLibrary: Boolean = false,
	@SerialName("libraryAlbumId") val libraryAlbumId: String? = null,
	@SerialName("libraryArtistId") val libraryArtistId: String? = null,
	val status: String? = null
)

@Serializable
internal data class AurralAlbumTracksResponseDto(
	val tracks: List<AurralAlbumTrackDto> = emptyList()
)

@Serializable
internal data class AurralAlbumTrackDto(
	val id: String? = null,
	val mbid: String? = null,
	@SerialName("recordingMbid") val recordingMbid: String? = null,
	val title: String? = null,
	val artist: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("artistCredit") val artistCredit: String? = null,
	@SerialName("trackName") val trackName: String? = null,
	@SerialName("recordingTitle") val recordingTitle: String? = null,
	@SerialName("discNumber") val discNumber: Int? = null,
	@SerialName("mediumNumber") val mediumNumber: Int? = null,
	@SerialName("trackNumber") val trackNumber: Int? = null,
	val position: Int? = null,
	@SerialName("absoluteTrackNumber") val absoluteTrackNumber: Int? = null,
	val length: Long? = null,
	@SerialName("durationMs") val durationMs: Long? = null,
	@SerialName("duration_ms") val durationMsSnake: Long? = null,
	@SerialName("previewUrl") val previewUrl: String? = null,
	@SerialName("preview_url") val previewUrlSnake: String? = null,
	val requested: Boolean? = null,
	val status: String? = null
)

@Serializable
internal data class AurralDiscoverArtistDto(
	val id: String? = null,
	val mbid: String? = null,
	@SerialName("foreignArtistId") val foreignArtistId: String? = null,
	@SerialName("foreignAlbumId") val foreignAlbumId: String? = null,
	val name: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	val title: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	val type: String? = null,
	val image: String? = null,
	val imageUrl: String? = null,
	@SerialName("coverUrl") val coverUrl: String? = null,
	@SerialName("releaseDate") val releaseDate: String? = null,
	@SerialName("primaryType") val primaryType: String? = null,
	@SerialName("secondaryTypes") val secondaryTypes: List<String> = emptyList(),
	val status: String? = null,
	val tags: List<String> = emptyList(),
	val genres: List<String> = emptyList(),
	@SerialName("matchedTags") val matchedTags: List<String> = emptyList(),
	@SerialName("sourceArtist") val sourceArtist: String? = null,
	@SerialName("sourceArtists") val sourceArtists: List<String> = emptyList(),
	@SerialName("sourceType") val sourceType: String? = null,
	@SerialName("discoveryTier") val discoveryTier: String? = null,
	val monitored: Boolean? = null,
	@SerialName("monitorOption") val monitorOption: String? = null
)

@Serializable
internal data class AurralAuthMeDto(
	val user: AurralUserDto? = null,
	@SerialName("expiresAt") val expiresAt: String? = null
)

@Serializable
internal data class AurralUserDto(
	val id: Int? = null,
	val username: String? = null,
	val role: String? = null,
	val permissions: AurralPermissionsDto = AurralPermissionsDto()
)

@Serializable
internal data class AurralPermissionsDto(
	@SerialName("accessFlow") val accessFlow: Boolean = false,
	@SerialName("addArtist") val addArtist: Boolean = false,
	@SerialName("addAlbum") val addAlbum: Boolean = false,
	@SerialName("changeMonitoring") val changeMonitoring: Boolean = false
)

@Serializable
internal data class AurralWeeklyFlowStatusDto(
	val flows: List<AurralFlowDto> = emptyList(),
	@SerialName("sharedPlaylists") val sharedPlaylists: List<AurralSharedPlaylistDto> = emptyList(),
	@SerialName("flowStats") val flowStats: Map<String, AurralFlowStatsDto> = emptyMap(),
	val stats: AurralFlowStatsDto = AurralFlowStatsDto(),
	val capabilities: AurralFlowCapabilitiesDto = AurralFlowCapabilitiesDto(),
	val hint: AurralFlowHintDto? = null
)

@Serializable
internal data class AurralFlowDto(
	val id: String? = null,
	val name: String? = null,
	val enabled: Boolean = false,
	val size: Int? = null,
	@SerialName("nextRunAt") val nextRunAt: Long? = null,
	val mix: AurralFlowMixDto = AurralFlowMixDto(),
	val tags: List<String> = emptyList(),
	@SerialName("relatedArtists") val relatedArtists: List<String> = emptyList(),
	@SerialName("scheduleDays") val scheduleDays: List<Int> = emptyList(),
	@SerialName("scheduleTime") val scheduleTime: String? = null
)

@Serializable
internal data class AurralSharedPlaylistDto(
	val id: String? = null,
	val name: String? = null
)

@Serializable
internal data class AurralFlowStatsDto(
	val total: Int = 0,
	val pending: Int = 0,
	val downloading: Int = 0,
	val done: Int = 0,
	val failed: Int = 0
)

@Serializable
internal data class AurralFlowMixDto(
	val discover: Int = 34,
	val mix: Int = 33,
	val trending: Int = 33,
	val focus: Int = 0
)

@Serializable
internal data class AurralFlowCapabilitiesDto(
	@SerialName("lastfmRequired") val lastfmRequired: Boolean = false,
	@SerialName("availableSources") val availableSources: List<String> = emptyList(),
	@SerialName("unavailableSources") val unavailableSources: Map<String, String> = emptyMap()
)

@Serializable
internal data class AurralFlowHintDto(
	val phase: String? = null,
	val message: String? = null
)

@Serializable
internal data class AurralRequestDto(
	val id: String? = null,
	val type: String? = null,
	val mbid: String? = null,
	val name: String? = null,
	@SerialName("albumId") val albumId: String? = null,
	@SerialName("albumMbid") val albumMbid: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	@SerialName("artistId") val artistId: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	val status: String? = null,
	@SerialName("requestedAt") val requestedAt: String? = null,
	@SerialName("inQueue") val inQueue: Boolean = false
)

@Serializable
internal data class AurralArtistDetailsDto(
	val id: String? = null,
	val name: String? = null,
	@SerialName("_lidarrData") val lidarrData: AurralArtistLidarrDataDto? = null,
	@SerialName("release-groups") val releaseGroups: List<AurralReleaseGroupDto> = emptyList()
)

@Serializable
internal data class AurralArtistLidarrDataDto(
	val monitored: Boolean? = null
)

@Serializable
internal data class AurralLibraryArtistDto(
	val monitored: Boolean? = null,
	@SerialName("monitorOption") val monitorOption: String? = null
)

@Serializable
internal data class AurralReleaseGroupDto(
	val id: String? = null,
	val title: String? = null,
	@SerialName("first-release-date") val firstReleaseDate: String? = null,
	@SerialName("primary-type") val primaryType: String? = null,
	@SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
	@SerialName("_coverUrl") val coverUrl: String? = null
)

@Serializable
internal data class AurralArtistPreviewDto(
	val tracks: List<AurralPreviewTrackDto> = emptyList()
)

@Serializable
internal data class AurralPreviewTrackDto(
	val id: String? = null,
	val title: String? = null,
	val album: String? = null,
	@SerialName("preview_url") val previewUrl: String? = null,
	@SerialName("duration_ms") val durationMs: Long? = null,
	val owned: Boolean? = null,
	val requested: Boolean? = null,
	@SerialName("inLibrary") val inLibrary: Boolean? = null,
	val status: String? = null
)

@Serializable
internal data class AurralSimilarArtistsDto(
	val artists: List<AurralSimilarArtistDto> = emptyList()
)

@Serializable
internal data class AurralReleaseGroupCoverDto(
	val images: List<AurralReleaseGroupCoverImageDto> = emptyList()
)

@Serializable
internal data class AurralReleaseGroupCoverImageDto(
	val image: String? = null
)

@Serializable
internal data class AurralSimilarArtistDto(
	val id: String? = null,
	val name: String? = null,
	val image: String? = null,
	val imageUrl: String? = null,
	val match: Int? = null
)

@Serializable
internal data class AurralFlowActionDto(
	val success: Boolean = false,
	@SerialName("flowId") val flowId: String? = null,
	val enabled: Boolean? = null,
	@SerialName("tracksQueued") val tracksQueued: Int = 0,
	@SerialName("reserveTracks") val reserveTracks: Int = 0,
	@SerialName("jobIds") val jobIds: List<String> = emptyList(),
	val message: String? = null,
	val flow: AurralFlowDto? = null
)

internal fun aurralDiscoverySummary(
	baseUrl: String,
	response: AurralDiscoveryResponseDto,
	recentlyAdded: List<AurralDiscoverArtist> = emptyList(),
	libraryArtists: List<AurralDiscoverArtist> = emptyList(),
	recentReleases: List<AurralAlbumSearchItem> = emptyList()
): AurralDiscoverySummary {
	val safeLibraryArtists = libraryArtists
	return AurralDiscoverySummary(
		recentlyAdded = recentlyAdded.withLibraryArtistMonitoring(safeLibraryArtists),
		recommendations = response.recommendations.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		globalTop = response.globalTop.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		basedOn = response.basedOn.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		libraryArtists = safeLibraryArtists,
		recentReleases = recentReleases,
		fallbackGenres = response.fallbackGenres.mapNotNull { it.toFallbackGenreSection(baseUrl) }
			.map { section ->
				section.copy(artists = section.artists.withLibraryArtistMonitoring(safeLibraryArtists))
			},
		topTags = response.topTags.cleanedAurralStrings(),
		topGenres = response.topGenres.cleanedAurralStrings(),
		isUpdating = response.isUpdating,
		stale = response.stale,
		provider = response.provider?.trim()?.takeIf { it.isNotEmpty() },
		discoveryMode = response.discoveryMode?.trim()?.takeIf { it.isNotEmpty() }
	)
}

internal fun aurralArtistSearchResult(
	baseUrl: String,
	query: String,
	response: AurralArtistSearchResponseDto
): AurralArtistSearchResult =
	AurralArtistSearchResult(
		query = query,
		count = response.count,
		offset = response.offset,
		artists = response.artists.mapNotNull { it.toDiscoverArtist(baseUrl) }
	)

internal fun aurralAlbumSearchResult(
	baseUrl: String,
	query: String,
	response: AurralAlbumSearchResponseDto
): AurralAlbumSearchResult =
	AurralAlbumSearchResult(
		query = response.query?.trim()?.takeIf { it.isNotEmpty() } ?: query,
		count = response.count,
		offset = response.offset,
		hasMore = response.hasMore,
		albums = response.items.mapNotNull { it.toAlbumSearchItem(baseUrl) }
	)

internal fun aurralRecentReleases(
	baseUrl: String,
	response: List<AurralAlbumSearchItemDto>
): List<AurralAlbumSearchItem> =
	response.mapNotNull { it.toAlbumSearchItem(baseUrl) }

internal fun decodeAurralAlbumTracks(responseText: String): List<AurralAlbumTrackDto> =
	runCatching {
		AURRAL_JSON.decodeFromString<List<AurralAlbumTrackDto>>(responseText)
	}.getOrElse {
		AURRAL_JSON.decodeFromString<AurralAlbumTracksResponseDto>(responseText).tracks
	}

internal fun aurralAlbumTrackItems(
	response: List<AurralAlbumTrackDto>
): List<AurralAlbumTrackItem> =
	response.mapNotNull { it.toAlbumTrackItem() }

internal fun aurralRecentlyAddedArtists(
	baseUrl: String,
	response: List<AurralDiscoverArtistDto>
): List<AurralDiscoverArtist> =
	response.mapNotNull { it.toRecentlyAddedArtist(baseUrl) }

internal fun aurralLibraryArtists(
	baseUrl: String,
	response: List<AurralDiscoverArtistDto>
): List<AurralDiscoverArtist> =
	response.mapNotNull { it.toLibraryArtist(baseUrl) }

private fun AurralDiscoverArtistDto.toDiscoverArtist(baseUrl: String): AurralDiscoverArtist? {
	val recommendedAlbum = toRecommendedAlbum(baseUrl)
	if (recommendedAlbum != null) {
		val recommendedArtistId = listOf(artistMbid, foreignArtistId)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
			?: return null
		val recommendedArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
			?: return null
		return AurralDiscoverArtist(
			id = recommendedArtistId,
			name = recommendedArtistName,
			imageUrl = null,
			tags = (tags + genres).cleanedAurralStrings(),
			matchedTags = matchedTags.cleanedAurralStrings(),
			reason = "Recommended: ${recommendedAlbum.title}",
			sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
			discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
			monitored = monitored,
			recommendedAlbums = listOf(recommendedAlbum),
			detailsIdVerified = true
		)
	}

	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(name, artistName)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		reason = aurralDiscoveryReason(this),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored,
		detailsIdVerified = artistId.verified
	)
}

private fun AurralDiscoverArtistDto.toRecentlyAddedArtist(baseUrl: String): AurralDiscoverArtist? {
	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(artistName, name, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored,
		detailsIdVerified = artistId.verified
	)
}

private fun AurralDiscoverArtistDto.toLibraryArtist(baseUrl: String): AurralDiscoverArtist? {
	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(artistName, name, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored ?: monitorOption?.trim()?.equals("none", ignoreCase = true)?.not(),
		detailsIdVerified = artistId.verified
	)
}

private data class AurralArtistIdCandidate(
	val id: String,
	val verified: Boolean
)

private fun AurralDiscoverArtistDto.verifiedAurralArtistIdCandidate(): AurralArtistIdCandidate? =
	listOf(
		foreignArtistId to true,
		mbid to true,
		artistMbid to true,
		id to false
	).firstNotNullOfOrNull { (candidate, verified) ->
		candidate?.trim()?.takeIf(String::isNotEmpty)?.let { AurralArtistIdCandidate(it, verified) }
	}

private fun AurralFallbackGenreSectionDto.toFallbackGenreSection(baseUrl: String): AurralFallbackGenreSection? {
	val safeGenre = listOf(genre, name, title)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtists = artists
		.mapNotNull { it.toDiscoverArtist(baseUrl) }
		.distinctBy { it.id.trim().lowercase() }
	if (safeArtists.isEmpty()) return null
	return AurralFallbackGenreSection(
		genre = safeGenre,
		artists = safeArtists
	)
}

private fun AurralDiscoverArtistDto.toRecommendedAlbum(baseUrl: String): AurralAlbumSearchItem? {
	val kind = type?.trim()?.lowercase()
	val albumTitle = listOf(albumName, title, name.takeIf { kind == "album" })
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val albumId = listOf(foreignAlbumId, mbid, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
		?: return null
	val safeArtistMbid = listOf(artistMbid, foreignArtistId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralAlbumSearchItem(
		id = albumId,
		title = albumTitle,
		artistName = safeArtistName,
		artistMbid = safeArtistMbid,
		releaseDate = releaseDate?.trim()?.takeIf { it.isNotEmpty() },
		primaryType = primaryType?.trim()?.takeIf { it.isNotEmpty() },
		secondaryTypes = secondaryTypes.cleanedAurralStrings(),
		coverUrl = aurralAbsoluteImageUrl(baseUrl, coverUrl ?: imageUrl ?: image),
		status = status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

private fun AurralAlbumSearchItemDto.toAlbumSearchItem(baseUrl: String): AurralAlbumSearchItem? {
	val albumId = listOf(id, mbid, foreignAlbumId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val albumTitle = listOf(title, albumName)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeArtistMbid = listOf(artistMbid, foreignArtistId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralAlbumSearchItem(
		id = albumId,
		title = albumTitle,
		artistName = safeArtistName,
		artistMbid = safeArtistMbid,
		releaseDate = releaseDate?.trim()?.takeIf { it.isNotEmpty() },
		primaryType = primaryType?.trim()?.takeIf { it.isNotEmpty() },
		secondaryTypes = secondaryTypes.cleanedAurralStrings(),
		coverUrl = aurralAbsoluteImageUrl(baseUrl, coverUrl),
		inLibrary = inLibrary,
		libraryAlbumId = libraryAlbumId?.trim()?.takeIf { it.isNotEmpty() },
		libraryArtistId = libraryArtistId?.trim()?.takeIf { it.isNotEmpty() },
		status = status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

private fun AurralAlbumTrackDto.toAlbumTrackItem(): AurralAlbumTrackItem? {
	val safeTitle = listOf(title, trackName, recordingTitle)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeId = listOf(id, mbid, recordingMbid)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: "track-${discNumber ?: mediumNumber ?: 1}-${trackNumber ?: position ?: absoluteTrackNumber ?: safeTitle}"
	return AurralAlbumTrackItem(
		id = safeId,
		title = safeTitle,
		artistName = listOf(artistName, artistCredit, artist)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		recordingMbid = listOf(recordingMbid, mbid)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		discNumber = discNumber ?: mediumNumber,
		trackNumber = trackNumber ?: position ?: absoluteTrackNumber,
		durationMs = durationMs ?: durationMsSnake ?: length,
		previewUrl = listOf(previewUrl, previewUrlSnake)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		status = status?.trim()?.takeIf { it.isNotEmpty() },
		requested = requested
	)
}

private fun aurralDiscoveryReason(artist: AurralDiscoverArtistDto): String? {
	val sourceArtist = artist.sourceArtist?.trim()?.takeIf { it.isNotEmpty() }
	if (sourceArtist != null) return "Similar to $sourceArtist"
	val sourceArtists = artist.sourceArtists.cleanedAurralStrings()
	return when {
		sourceArtists.size == 1 -> "Because you listen to ${sourceArtists.single()}"
		sourceArtists.size > 1 -> "Because you listen to ${sourceArtists.take(2).joinToString(", ")}"
		artist.discoveryTier?.trim()?.equals("deeper", ignoreCase = true) == true ->
			"A deeper discovery pick"
		else -> null
	}
}

private fun List<AurralDiscoverArtist>.withLibraryArtistMonitoring(
	libraryArtists: List<AurralDiscoverArtist>
): List<AurralDiscoverArtist> {
	if (libraryArtists.isEmpty()) return this
	val libraryById = libraryArtists
		.mapNotNull { artist -> artist.id.normalizedAurralArtistKey()?.let { it to artist } }
		.toMap()
	val libraryByName = libraryArtists
		.mapNotNull { artist -> artist.name.normalizedAurralArtistName()?.let { it to artist } }
		.toMap()
	return map { artist ->
		val libraryArtist = artist.id.normalizedAurralArtistKey()?.let(libraryById::get)
			?: artist.name.normalizedAurralArtistName()?.let(libraryByName::get)
		val preferredImageUrl = libraryArtist?.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
			?: artist.imageUrl
		val monitored = artist.monitored ?: libraryArtist?.monitored
		if (preferredImageUrl != artist.imageUrl || monitored != artist.monitored) {
			artist.copy(
				imageUrl = preferredImageUrl,
				monitored = monitored
			)
		} else {
			artist
		}
	}
}

private fun List<String>.cleanedAurralStrings(): List<String> =
	mapNotNull { it.trim().takeIf(String::isNotEmpty) }
		.distinctBy { it.lowercase() }

private fun String?.normalizedAurralArtistKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralArtistName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

internal fun aurralServiceStatus(
	health: AurralHealthDto,
	authMe: AurralAuthMeDto?,
	weeklyFlow: AurralWeeklyFlowStatusDto?,
	requests: List<AurralRequestDto>
): AurralServiceStatus {
	val user = authMe?.user ?: health.user
	val stats = weeklyFlow?.stats ?: AurralFlowStatsDto()
	val flows = weeklyFlow?.flows.orEmpty().mapNotNull { flow ->
		val id = flow.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		flow.toSummary(weeklyFlow?.flowStats?.get(id))
	}
	return AurralServiceStatus(
		healthStatus = health.status,
		appVersion = health.appVersion,
		authRequired = health.authRequired,
		username = user?.username,
		role = user?.role,
		accessFlow = user?.permissions?.accessFlow == true,
		addArtist = user?.permissions?.addArtist == true,
		addAlbum = user?.permissions?.addAlbum == true,
		changeMonitoring = user?.permissions?.changeMonitoring == true,
		lidarrConfigured = health.lidarrConfigured || health.rootFolderConfigured,
		discoveryRecommendationsCount = health.discovery?.recommendationsCount ?: 0,
		discoveryUpdating = health.discovery?.isUpdating == true,
		flowsCount = weeklyFlow?.flows.orEmpty().size,
		enabledFlowsCount = weeklyFlow?.flows.orEmpty().count { it.enabled },
		sharedPlaylistsCount = weeklyFlow?.sharedPlaylists.orEmpty().size,
		requestsCount = requests.size,
		flowTracksTotal = stats.total,
		flowTracksPending = stats.pending,
		flowTracksDownloading = stats.downloading,
		flowTracksDone = stats.done,
		flowTracksFailed = stats.failed,
		flowPhase = weeklyFlow?.hint?.phase,
		flowMessage = weeklyFlow?.hint?.message,
		flows = flows,
		flowCapabilities = weeklyFlow?.capabilities?.toCapabilities() ?: AurralFlowCapabilities(),
		acquisitionQueue = requests.mapNotNull(::aurralAcquisitionQueueItem)
	)
}

internal fun aurralDefaultFlowCreatePayload(
	name: String,
	size: Int,
	scheduleDay: Int
): AurralFlowCreatePayload {
	val trimmedName = name.trim().takeIf { it.isNotEmpty() }
		?: error("Flow name is required.")
	if (size <= 0) error("Flow size must be positive.")
	if (scheduleDay !in 0..6) error("Flow schedule day must be between 0 and 6.")
	return AurralFlowCreatePayload(
		name = trimmedName,
		size = size,
		mix = AurralFlowMix(discover = 34, mix = 33, trending = 33, focus = 0),
		scheduleDays = listOf(scheduleDay),
		scheduleTime = "00:00"
	)
}

internal fun currentAurralScheduleDay(): Int {
	val isoDay = Clock.System.now()
		.toLocalDateTime(TimeZone.currentSystemDefault())
		.dayOfWeek
		.ordinal + 1
	return if (isoDay == 7) 0 else isoDay
}

private fun AurralFlowDto.toSummary(stats: AurralFlowStatsDto? = null): AurralFlowSummary? {
	val id = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Flow"
	val safeSize = size?.takeIf { it > 0 } ?: 30
	return AurralFlowSummary(
		id = id,
		name = name,
		enabled = enabled,
		size = safeSize,
		nextRunAt = nextRunAt,
		mix = mix.toMix(),
		tags = tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		relatedArtists = relatedArtists.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		scheduleDays = scheduleDays.filter { it in 0..6 },
		scheduleTime = scheduleTime?.trim()?.takeIf { it.isNotEmpty() } ?: "00:00",
		stats = stats.toStats()
	)
}

private fun AurralFlowMixDto.toMix(): AurralFlowMix =
	AurralFlowMix(
		discover = discover,
		mix = mix,
		trending = trending,
		focus = focus
	)

private fun AurralFlowStatsDto?.toStats(): AurralFlowStats =
	AurralFlowStats(
		total = this?.total ?: 0,
		pending = this?.pending ?: 0,
		downloading = this?.downloading ?: 0,
		done = this?.done ?: 0,
		failed = this?.failed ?: 0
	)

private fun AurralFlowCapabilitiesDto.toCapabilities(): AurralFlowCapabilities =
	AurralFlowCapabilities(
		lastfmRequired = lastfmRequired,
		availableSources = availableSources.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		unavailableSources = unavailableSources.mapNotNull { (key, value) ->
			val normalizedKey = key.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			val normalizedValue = value.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			normalizedKey to normalizedValue
		}.toMap()
	)

private fun AurralFlowActionDto.toResult(): AurralFlowActionResult =
	AurralFlowActionResult(
		success = success,
		flowId = flow?.id?.trim()?.takeIf { it.isNotEmpty() }
			?: flowId?.trim()?.takeIf { it.isNotEmpty() },
		enabled = enabled ?: flow?.enabled,
		tracksQueued = tracksQueued,
		reserveTracks = reserveTracks,
		jobIds = jobIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		message = message?.trim()?.takeIf { it.isNotEmpty() },
		flow = flow?.toSummary()
	)

private fun AurralFlowJobDto.toDomainSong(
	baseUrl: String,
	sessionToken: String?,
	streamToken: String?,
	allowUnauthenticatedStream: Boolean
): DomainSong? {
	val jobId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val title = trackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val streamUrl = when {
		!sessionToken.isNullOrBlank() -> aurralFlowStreamUrl(baseUrl, jobId, sessionToken)
		!streamToken.isNullOrBlank() -> aurralFlowStreamTokenUrl(baseUrl, jobId, streamToken)
		allowUnauthenticatedStream -> aurralFlowRawStreamUrl(baseUrl, jobId)
		else -> null
	} ?: return null
	val fileExtension = finalPath
		?.substringBefore('?')
		?.substringAfterLast('/')
		?.substringAfterLast('.', "")
		?.lowercase()
		?.takeIf { it.isNotEmpty() }
		?: "mp3"

	return DomainSong(
		id = "$AurralFlowSongIdPrefix$jobId",
		title = title,
		artistName = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Aurral",
		artistId = artistMbid?.trim()?.takeIf { it.isNotEmpty() } ?: "",
		albumTitle = albumName?.trim()?.takeIf { it.isNotEmpty() },
		albumId = albumMbid?.trim()?.takeIf { it.isNotEmpty() },
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = releaseYear?.trim()?.take(4)?.toIntOrNull(),
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = durationMs?.takeIf { it > 0 }?.milliseconds ?: 0.milliseconds,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = fileExtension,
		mimeType = fileExtension.toAurralAudioMimeType(),
		filePath = streamUrl,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = trackMbid?.trim()?.takeIf { it.isNotEmpty() },
		explicitStatus = DomainExplicitStatus.Unknown
	)
}

private fun String.toAurralAudioMimeType(): String =
	when (lowercase()) {
		"flac" -> "audio/flac"
		"m4a", "mp4" -> "audio/mp4"
		"ogg", "oga" -> "audio/ogg"
		"wav" -> "audio/wav"
		"aac" -> "audio/aac"
		else -> "audio/mpeg"
	}

internal fun aurralAcquisitionQueueItem(request: AurralRequestDto): AurralAcquisitionQueueItem? {
	val id = request.id?.trim()?.takeIf { it.isNotEmpty() }
		?: request.albumId?.trim()?.takeIf { it.isNotEmpty() }?.let { "album-$it" }
		?: request.resolvedAlbumMbid()?.let { "album-$it" }
		?: return null
	val albumName = request.resolvedAlbumName() ?: return null
	val artistName = request.artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Artist"
	val status = request.status?.trim()?.takeIf { it.isNotEmpty() } ?: "requested"
	return AurralAcquisitionQueueItem(
		id = id,
		type = request.type?.trim()?.takeIf { it.isNotEmpty() } ?: "album",
		albumId = request.albumId?.trim()?.takeIf { it.isNotEmpty() },
		albumMbid = request.resolvedAlbumMbid(),
		albumName = albumName,
		artistId = request.artistId?.trim()?.takeIf { it.isNotEmpty() },
		artistMbid = request.artistMbid?.trim()?.takeIf { it.isNotEmpty() },
		artistName = artistName,
		status = status,
		requestedAt = request.requestedAt?.trim()?.takeIf { it.isNotEmpty() },
		inQueue = request.inQueue
	)
}

fun aurralAcquisitionDeleteTarget(item: AurralAcquisitionQueueItem): AurralAcquisitionDeleteTarget? =
	item.albumId?.trim()?.takeIf { it.isNotEmpty() }?.let(AurralAcquisitionDeleteTarget::Album)
		?: item.artistMbid?.trim()?.takeIf { it.isNotEmpty() }?.let(AurralAcquisitionDeleteTarget::Artist)

private fun AurralRequestDto.resolvedAlbumMbid(): String? =
	albumMbid?.trim()?.takeIf { it.isNotEmpty() }
		?: mbid?.trim()?.takeIf { it.isNotEmpty() }

private fun AurralRequestDto.resolvedAlbumName(): String? =
	albumName?.trim()?.takeIf { it.isNotEmpty() }
		?: name?.trim()?.takeIf { it.isNotEmpty() }

private fun AurralArtistMonitorPayload.toMonitoringUpdatePayload(): AurralArtistMonitoringUpdatePayload =
	AurralArtistMonitoringUpdatePayload(
		monitored = monitored,
		monitorOption = monitorOption
	)

internal fun aurralArtistEnrichment(
	baseUrl: String,
	details: AurralArtistDetailsDto,
	preview: AurralArtistPreviewDto,
	similar: AurralSimilarArtistsDto,
	requests: List<AurralRequestDto>
): AurralArtistEnrichment {
	val artistMbid = details.id.orEmpty()
	val artistName = details.name.orEmpty()
	return AurralArtistEnrichment(
		artistMbid = artistMbid,
		artistName = artistName,
		releaseGroups = details.releaseGroups.mapNotNull { releaseGroup ->
			val id = releaseGroup.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val title = releaseGroup.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralReleaseGroup(
				id = id,
				title = title,
				firstReleaseDate = releaseGroup.firstReleaseDate,
				primaryType = releaseGroup.primaryType,
				secondaryTypes = releaseGroup.secondaryTypes,
				coverUrl = releaseGroup.coverUrl
			)
		},
		previewTracks = preview.tracks.mapNotNull { track ->
			val id = track.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val title = track.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralPreviewTrack(
				id = id,
				title = title,
				album = track.album,
				previewUrl = track.previewUrl,
				durationMs = track.durationMs,
				owned = track.owned,
				requested = track.requested,
				inLibrary = track.inLibrary,
				status = track.status
			)
		},
		similarArtists = similar.artists.mapNotNull { artist ->
			val id = artist.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val name = artist.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralSimilarArtist(
				id = id,
				name = name,
				imageUrl = aurralAbsoluteImageUrl(baseUrl, artist.imageUrl ?: artist.image),
				matchPercent = artist.match
			)
		},
		requests = requests.map { request ->
			AurralAlbumRequest(
				albumMbid = request.resolvedAlbumMbid(),
				albumName = request.resolvedAlbumName(),
				artistMbid = request.artistMbid,
				artistName = request.artistName,
				status = request.status
			)
		},
		monitored = details.lidarrData?.monitored
	)
}

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

private fun aurralFlowRawStreamUrl(
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
