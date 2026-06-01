package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.LastFmTopTrack

class LastFmRepositoryTest {
	@Test
	fun blankApiKeyDoesNotCallLastFmDuringConnectionTest() = runBlocking {
		val apiClient = FakeLastFmApiClient()
		val repository = LastFmRepository(
			preferenceManager = PreferenceManager(MapSettings()),
			apiClient = apiClient
		)

		assertEquals(LastFmConnectionResult.MissingApiKey, repository.testConnection())
		assertEquals(0, apiClient.validationCalls)
	}

	@Test
	fun disabledIntegrationDoesNotCallLastFmAndReportsDisabledStatus() = runBlocking {
		val apiClient = FakeLastFmApiClient()
		val preferences = PreferenceManager(MapSettings()).apply {
			lastFmEnabled = false
			lastFmApiKey = "configured-key"
		}
		val repository = LastFmRepository(
			preferenceManager = preferences,
			apiClient = apiClient
		)

		assertEquals(LastFmConnectionResult.Disabled, repository.testConnection())
		assertEquals(
			LastFmServiceStatus(
				enabled = false,
				apiKeyConfigured = true,
				artistTopTracksEnabled = false
			),
			repository.getServiceStatus().getOrThrow()
		)
		assertEquals(
			emptyList(),
			repository.getArtistTopTracks("Artist", null).getOrThrow()
		)
		assertEquals(0, apiClient.validationCalls)
	}

	@Test
	fun connectionTestReturnsSampleMetricWhenApiKeyIsValid() = runBlocking {
		val apiClient = FakeLastFmApiClient(
			probeResult = LastFmServiceProbe(sampleArtistCount = 1)
		)
		val preferences = PreferenceManager(MapSettings()).apply {
			lastFmApiKey = " valid-key "
		}
		val repository = LastFmRepository(
			preferenceManager = preferences,
			apiClient = apiClient
		)

		assertEquals(
			LastFmConnectionResult.Connected(sampleArtistCount = 1),
			repository.testConnection()
		)
		assertEquals(listOf("valid-key"), apiClient.validatedApiKeys)
	}

	@Test
	fun invalidLastFmApiKeyErrorMapsToInvalidApiKeyResult() = runBlocking {
		val apiClient = FakeLastFmApiClient(
			probeFailure = LastFmApiException(code = 10, detail = "Invalid API key")
		)
		val preferences = PreferenceManager(MapSettings()).apply {
			lastFmApiKey = "bad-key"
		}
		val repository = LastFmRepository(
			preferenceManager = preferences,
			apiClient = apiClient
		)

		assertEquals(LastFmConnectionResult.InvalidApiKey, repository.testConnection())
	}

	@Test
	fun serviceStatusReflectsConfiguredApiKeyAndProbeMetric() = runBlocking {
		val apiClient = FakeLastFmApiClient(
			probeResult = LastFmServiceProbe(sampleArtistCount = 3)
		)
		val preferences = PreferenceManager(MapSettings()).apply {
			lastFmApiKey = "configured-key"
		}
		val repository = LastFmRepository(
			preferenceManager = preferences,
			apiClient = apiClient
		)

		assertEquals(
			LastFmServiceStatus(
				enabled = true,
				apiKeyConfigured = true,
				artistTopTracksEnabled = true,
				sampleArtistCount = 3
			),
			repository.getServiceStatus().getOrThrow()
		)
	}

	private class FakeLastFmApiClient(
		private val probeResult: LastFmServiceProbe = LastFmServiceProbe(sampleArtistCount = 0),
		private val probeFailure: Throwable? = null
	) : LastFmApiClient {
		var validationCalls = 0
		val validatedApiKeys = mutableListOf<String>()

		override suspend fun fetchArtistTopTracks(
			apiKey: String,
			artistName: String?,
			artistMbid: String?,
			limit: Int
		): List<LastFmTopTrack> = emptyList()

		override suspend fun probeService(apiKey: String): LastFmServiceProbe {
			validationCalls += 1
			validatedApiKeys += apiKey
			probeFailure?.let { throw it }
			return probeResult
		}
	}
}
