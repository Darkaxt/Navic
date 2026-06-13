package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.manager.PreferenceManager

class BinderyRepositoryTest {
	@Test
	fun binderyEndpointNormalizesOpdsBaseUrlAndPath() {
		assertEquals(
			"https://bindery.example.com/opds/books",
			binderyEndpoint(" https://bindery.example.com/opds/ ", "/books")
		)
		assertEquals(
			"https://bindery.example.com/opds/books",
			binderyEndpoint(" https://bindery.example.com/opds/ ", "/opds/books")
		)
		assertEquals(
			"https://bindery.example.com/bindery/opds/books/1/manifest",
			binderyEndpoint(" https://bindery.example.com/bindery/opds/ ", "books/1/manifest")
		)
		assertEquals(
			"https://bindery.example.com/api/v1/audiobooks?bookId=3816&limit=100",
			binderyApiEndpoint(" https://bindery.example.com/opds/ ", "audiobooks?bookId=3816&limit=100")
		)
		assertEquals(
			"https://bindery.example.com/bindery/api/v1/audiobooks/88",
			binderyApiEndpoint(" https://bindery.example.com/bindery/opds/ ", "/api/v1/audiobooks/88")
		)
	}

	@Test
	fun binderyImageHeadersAreOnlyUsedForBinderyOriginUrls() {
		val headers = mapOf("X-Api-Key" to "secret")

		assertEquals(
			headers,
			binderyRequestHeadersForUrl(
				baseUrl = "https://bindery.example.com/opds",
				url = "https://bindery.example.com/opds/books/1/cover",
				requestHeaders = headers
			)
		)
		assertEquals(
			emptyMap(),
			binderyRequestHeadersForUrl(
				baseUrl = "https://bindery.example.com/opds",
				url = "https://image.bayimg.com/cover.jpg",
				requestHeaders = headers
			)
		)
		assertEquals(
			emptyMap(),
			binderyRequestHeadersForUrl(
				baseUrl = "https://bindery.example.com/opds",
				url = "https://assets.hardcover.app/edition/book.jpg",
				requestHeaders = headers
			)
		)
	}

	@Test
	fun binderyEndpointRequiresHttpOrHttpsOpdsUrlWithoutCredentialsQueryOrFragment() {
		assertNull(configuredBinderyOpdsBaseUrl("bindery.example.com/opds"))
		assertNull(configuredBinderyOpdsBaseUrl("https:///opds"))
		assertNull(configuredBinderyOpdsBaseUrl("https://user:pass@bindery.example.com/opds"))
		assertNull(configuredBinderyOpdsBaseUrl("https://bindery.example.com/opds?apikey=secret"))
		assertNull(configuredBinderyOpdsBaseUrl("https://bindery.example.com/opds#root"))
		assertEquals(
			"https://bindery.example.com/opds",
			configuredBinderyOpdsBaseUrl(" https://bindery.example.com/opds/ ")
		)

		val error = assertFailsWith<IllegalStateException> {
			binderyEndpoint("bindery.example.com/opds", "/")
		}
		assertEquals(BINDERY_OPDS_URL_INVALID_SCHEME_MESSAGE, error.message)
	}

	@Test
	fun disabledOrIncompleteBinderySettingsDoNotCallApiClient() = runBlocking {
		val apiClient = FakeBinderyApiClient()
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = false
			binderyOpdsBaseUrl = "https://bindery.example.com/opds"
			binderyApiKey = "secret"
		}
		val repository = BinderyRepository(preferences, apiClient)

		assertEquals(BinderyConnectionResult.Disabled, repository.testConnection())
		assertEquals(0, apiClient.rootCalls)

		preferences.binderyEnabled = true
		preferences.binderyOpdsBaseUrl = ""
		assertEquals(BinderyConnectionResult.MissingOpdsUrl, repository.testConnection())
		assertEquals(0, apiClient.rootCalls)

		preferences.binderyOpdsBaseUrl = "https://bindery.example.com/opds"
		preferences.binderyApiKey = ""
		assertEquals(BinderyConnectionResult.MissingApiKey, repository.testConnection())
		assertEquals(0, apiClient.rootCalls)
	}

	@Test
	fun connectionTestUsesConfiguredOpdsUrlAndApiKeyHeader() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			rootCatalog = binderyRootCatalog()
		)
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		val repository = BinderyRepository(preferences, apiClient)

		assertEquals(
			BinderyConnectionResult.Connected(
				navigationCount = 6,
				audiobooksAvailable = true
			),
			repository.testConnection()
		)
		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.rootBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.rootHeaders)
	}

	@Test
	fun serviceStatusReflectsRootCatalogCapabilities() = runBlocking {
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = "https://bindery.example.com/opds"
			binderyApiKey = "secret"
		}
		val repository = BinderyRepository(
			preferenceManager = preferences,
			apiClient = FakeBinderyApiClient(rootCatalog = binderyRootCatalog())
		)

		assertEquals(
			BinderyServiceStatus(
				enabled = true,
				opdsUrlConfigured = true,
				apiKeyConfigured = true,
				navigationCount = 6,
				hasSearch = true,
				hasAudiobooks = true,
				hasAuthors = true,
				hasSeries = true,
				hasCollections = true,
				hasFindings = true,
				progressSyncSupported = false,
				paginationSupported = false
			),
			repository.getServiceStatus().getOrThrow()
		)
	}

	@Test
	fun performActionPostsAdvertisedActionHrefWithConfiguredOpdsUrlAndApiKeyHeader() = runBlocking {
		val apiClient = FakeBinderyApiClient()
		val metadataCache = RecordingBinderyMetadataCache()
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		val repository = BinderyRepository(
			preferenceManager = preferences,
			apiClient = apiClient,
			metadataCache = metadataCache
		)

		repository.performAction("/opds/discover/authors/hc%3Apeter-sanderson/monitor").getOrThrow()

		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.actionBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.actionHeaders)
		assertEquals(listOf("/opds/discover/authors/hc%3Apeter-sanderson/monitor"), apiClient.actionPaths)
		assertEquals(listOf("https://bindery.example.com/opds"), metadataCache.clearedBaseUrls)
	}

	@Test
	fun resourceBytesUseConfiguredOpdsUrlAndApiKeyHeader() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			resourceBytes = "epub bytes".encodeToByteArray()
		)
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		val repository = BinderyRepository(
			preferenceManager = preferences,
			apiClient = apiClient
		)

		val bytes = repository.getResourceBytes("/opds/books/3693/resources/readaloud-1").getOrThrow()

		assertEquals("epub bytes", bytes.decodeToString())
		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.resourceBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.resourceHeaders)
		assertEquals(listOf("/opds/books/3693/resources/readaloud-1"), apiClient.resourcePaths)
	}

	@Test
	fun audiobookVersionsUseConfiguredOpdsUrlAndApiKeyHeaderAndCache() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			audiobookVersions = listOf(
				BinderyAudiobookVersion(
					id = 88,
					bookId = 3816,
					bookFileId = 791,
					title = "The Hobbit",
					narrator = "Rob Inglis",
					coverUrl = "https://m.media-amazon.com/images/I/61-hOsUyOZL._SL500_.jpg"
				)
			)
		)
		val metadataCache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L }
		)

		val first = repository.getAudiobookVersions("3816").getOrThrow()
		val second = repository.getAudiobookVersions("3816").getOrThrow()

		assertEquals(first, second)
		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.audiobookVersionBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.audiobookVersionHeaders)
		assertEquals(listOf("3816"), apiClient.audiobookVersionBookIds)
		assertEquals(listOf(100), apiClient.audiobookVersionLimits)
		assertTrue(
			metadataCache.records.values.any { record ->
				record.payloadType == BinderyMetadataPayloadType.AudiobookVersions &&
					record.path == "book:3816:limit:100"
			}
		)
	}

}
