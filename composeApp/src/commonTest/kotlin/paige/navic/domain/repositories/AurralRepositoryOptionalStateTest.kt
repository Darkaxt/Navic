package paige.navic.domain.repositories

import paige.navic.data.remote.aurral.*

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.OptionalIntegrationFailureKind
import paige.navic.domain.models.OptionalIntegrationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AurralRepositoryOptionalStateTest {
	@Test
	fun disabledAndMissingConfigurationRemainDistinctFailures() = runBlocking {
		val disabled = repository(enabled = false).getDiscoveryOptional(hydrateMissingImages = false)
		val misconfigured = repository(baseUrl = "").getDiscoveryOptional(hydrateMissingImages = false)

		assertEquals(
			OptionalIntegrationFailureKind.Disabled,
			assertIs<OptionalIntegrationResult.Unavailable>(disabled).failure.kind
		)
		assertEquals(
			OptionalIntegrationFailureKind.Misconfigured,
			assertIs<OptionalIntegrationResult.Unavailable>(misconfigured).failure.kind
		)
	}

	@Test
	fun liveDiscoveryDistinguishesEmptyAndAvailable() = runBlocking {
		val empty = repository().getDiscoveryOptional(hydrateMissingImages = false)
		val available = repository(
			discovery = AurralDiscoverySummary(
				recommendations = listOf(AurralDiscoverArtist(id = "artist-1", name = "Artist"))
			)
		).getDiscoveryOptional(hydrateMissingImages = false)

		assertIs<OptionalIntegrationResult.Empty>(empty)
		assertEquals(
			"Artist",
			assertIs<OptionalIntegrationResult.Available<AurralDiscoverySummary>>(available)
				.data.recommendations.single().name
		)
	}

	@Test
	fun failedLiveDiscoveryReturnsStaleCachedDataWithFailure() = runBlocking {
		var nowMillis = 1_000L
		val cache = RecordingOptionalAurralMetadataCache()
		val preferences = preferences()
		AurralRepository(
			preferenceManager = preferences,
			apiClient = OptionalStateAurralApiClient(
				discovery = AurralDiscoverySummary(
					recommendations = listOf(AurralDiscoverArtist(id = "cached", name = "Cached Artist"))
				)
			),
			nowMillis = { nowMillis },
			metadataCache = cache
		).getDiscoveryOptional(hydrateMissingImages = false)

		nowMillis += AURRAL_METADATA_CACHE_FRESH_MILLIS + 1
		val stale = AurralRepository(
			preferenceManager = preferences,
			apiClient = OptionalStateAurralApiClient(failure = IllegalStateException("offline")),
			nowMillis = { nowMillis },
			metadataCache = cache
		).getDiscoveryOptional(hydrateMissingImages = false)

		val result = assertIs<OptionalIntegrationResult.Stale<AurralDiscoverySummary>>(stale)
		assertEquals("Cached Artist", result.data.recommendations.single().name)
		assertEquals(OptionalIntegrationFailureKind.Unavailable, result.failure.kind)
	}

	@Test
	fun liveDiscoveryFailuresRemainTyped() = runBlocking {
		val unauthorized = repository(
			failure = AurralApiException(HttpStatusCode.Unauthorized, "Unauthorized")
		).getDiscoveryOptional(hydrateMissingImages = false)
		val malformed = repository(
			failure = SerializationException("bad discovery payload")
		).getDiscoveryOptional(hydrateMissingImages = false)
		val unavailable = repository(
			failure = IllegalStateException("offline")
		).getDiscoveryOptional(hydrateMissingImages = false)

		assertEquals(
			OptionalIntegrationFailureKind.Unauthorized,
			assertIs<OptionalIntegrationResult.Unavailable>(unauthorized).failure.kind
		)
		assertEquals(
			OptionalIntegrationFailureKind.Malformed,
			assertIs<OptionalIntegrationResult.Unavailable>(malformed).failure.kind
		)
		assertEquals(
			OptionalIntegrationFailureKind.Unavailable,
			assertIs<OptionalIntegrationResult.Unavailable>(unavailable).failure.kind
		)
	}

	private fun repository(
		enabled: Boolean = true,
		baseUrl: String = "https://aurral.example.com",
		discovery: AurralDiscoverySummary = AurralDiscoverySummary(),
		failure: Exception? = null
	): AurralRepository = AurralRepository(
		preferenceManager = preferences(enabled, baseUrl),
		apiClient = OptionalStateAurralApiClient(discovery, failure)
	)

	private fun preferences(
		enabled: Boolean = true,
		baseUrl: String = "https://aurral.example.com"
	): PreferenceManager = PreferenceManager(MapSettings()).apply {
		aurralEnabled = enabled
		aurralBaseUrl = baseUrl
	}
}

private class OptionalStateAurralApiClient(
	private val discovery: AurralDiscoverySummary = AurralDiscoverySummary(),
	private val failure: Exception? = null
) : AurralApiClient {
	override suspend fun testConnection(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralConnectionResult = AurralConnectionResult.Connected

	override suspend fun fetchServiceStatus(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralServiceStatus = AurralServiceStatus()

	override suspend fun fetchDiscovery(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): AurralDiscoverySummary {
		failure?.let { throw it }
		return discovery
	}

	override suspend fun fetchArtistEnrichment(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String
	): AurralArtistEnrichment = AurralArtistEnrichment(artistMbid = artistMbid, artistName = artistName)

	override suspend fun fetchLibraryArtistMonitoring(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String
	): Boolean? = null

	override suspend fun requestAlbum(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		payload: AurralAlbumRequestPayload
	) = Unit

	override suspend fun monitorArtist(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		payload: AurralArtistMonitorPayload
	) = Unit

	override suspend fun fetchReleaseGroupCoverImageUrl(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		releaseGroupMbid: String,
		artistName: String,
		albumTitle: String
	): String? = null
}

private class RecordingOptionalAurralMetadataCache : AurralMetadataCache {
	private val records = mutableMapOf<String, AurralMetadataCacheRecord>()

	override suspend fun get(cacheKey: String): AurralMetadataCacheRecord? = records[cacheKey]

	override suspend fun put(record: AurralMetadataCacheRecord) {
		records[record.cacheKey] = record
	}

	override suspend fun clearBaseUrl(baseUrl: String) {
		records.values.removeAll { it.baseUrl == baseUrl }
	}
}
