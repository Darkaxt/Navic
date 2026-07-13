package paige.navic.domain.repositories

import paige.navic.data.remote.bindery.*

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
	fun binderyHeaderOriginCanonicalizesHostsAndDefaultPorts() {
		val headers = mapOf("X-Api-Key" to "secret")

		assertEquals(
			headers,
			binderyRequestHeadersForUrl(
				baseUrl = "https://BINDERY.example.com/opds",
				url = "https://bindery.EXAMPLE.com:443/audio/chapter.mp3",
				requestHeaders = headers
			)
		)
		assertEquals(
			headers,
			binderyRequestHeadersForUrl(
				baseUrl = "http://bindery.example.com:80/opds",
				url = "http://bindery.example.com/audio/chapter.mp3",
				requestHeaders = headers
			)
		)
		assertEquals(
			emptyMap(),
			binderyRequestHeadersForUrl(
				baseUrl = "https://bindery.example.com:8443/opds",
				url = "https://bindery.example.com/audio/chapter.mp3",
				requestHeaders = headers
			)
		)
	}

	@Test
	fun binderyHeaderOriginRejectsCredentialsUnsupportedSchemesAndMissingUrls() {
		val headers = mapOf("X-Api-Key" to "secret")

		listOf(
			"https://user:pass@bindery.example.com/audio.mp3",
			"file:///data/local/audio.mp3",
			"content://bindery/audio/1",
			null
		).forEach { url ->
			assertEquals(
				emptyMap(),
				binderyRequestHeadersForUrl(
					baseUrl = "https://bindery.example.com/opds",
					url = url,
					requestHeaders = headers
				)
			)
		}
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
	fun bookActionInvalidatesOwnedMetadataWithoutPurgingTheBaseUrl() = runBlocking {
		val metadataCache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(),
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L }
		)

		repository.performAction("/opds/books/3816/monitor").getOrThrow()

		assertEquals(emptyList(), metadataCache.clearedBaseUrls)
		assertEquals(
			listOf(
				Triple("https://bindery.example.com/opds", BinderyMetadataPayloadType.Catalog, null),
				Triple("https://bindery.example.com/opds", BinderyMetadataPayloadType.BookFindings, null),
				Triple("https://bindery.example.com/opds", BinderyMetadataPayloadType.Manifest, "3816"),
				Triple("https://bindery.example.com/opds", BinderyMetadataPayloadType.Resources, "3816"),
				Triple("https://bindery.example.com/opds", BinderyMetadataPayloadType.BookSync, "3816"),
				Triple("https://bindery.example.com/opds", BinderyMetadataPayloadType.AudiobookVersions, "book:3816:")
			),
			metadataCache.clearedPayloads
		)
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
	fun whispersyncSidecarUsesConfiguredOpdsUrlApiKeyHeaderAndCache() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			whispersyncSidecarJson = """
			{
			  "artifactId": "artifact-3",
			  "ebookBookFileId": "3913",
			  "audiobookBookFileId": "694",
			  "segments": [
			    {
			      "audioHref": "Audio/chapter01.m4b",
			      "startMs": 1250,
			      "endMs": 3500,
			      "textHref": "Text/chapter1.xhtml",
			      "fragmentId": "seg-1",
			      "textStart": 10,
			      "textEnd": 42,
			      "spokenText": "Opening words",
			      "ebookText": "Opening words",
			      "label": "Opening"
			    }
			  ]
			}
			""".trimIndent()
		)
		val metadataCache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L }
		)

		val first = repository.getWhispersyncSidecar("/opds/books/3816/sync/3").getOrThrow()
		val second = repository.getWhispersyncSidecar("/opds/books/3816/sync/3").getOrThrow()

		assertEquals("artifact-3", first.artifactId)
		assertEquals(first, second)
		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.whispersyncSidecarBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.whispersyncSidecarHeaders)
		assertEquals(listOf("/opds/books/3816/sync/3"), apiClient.whispersyncSidecarPaths)
		assertTrue(
			metadataCache.records.values.any { record ->
				record.payloadType == BinderyMetadataPayloadType.WhispersyncSidecar &&
					record.path == "/opds/books/3816/sync/3" &&
					"artifact-3" in record.payloadJson
			}
		)
	}

	@Test
	fun whispersyncSidecarRefetchesFreshCacheWhenCueTextIsMissing() = runBlocking {
		val path = "/api/v1/sync/artifacts/2"
		val apiClient = FakeBinderyApiClient(
			whispersyncSidecarJson = """
			{
			  "schema": "bindery.whispersync.sidecar.v1",
			  "bookId": 3959,
			  "ebookBookFileId": 212,
			  "audiobookBookFileId": 572,
			  "cues": [
			    {
			      "id": 1,
			      "audioHref": "Part 01.mp3",
			      "audioStart": 0,
			      "audioEnd": 18.04,
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "ebookStart": 0,
			      "ebookEnd": 78,
			      "text": "I am not a good person."
			    }
			  ]
			}
			""".trimIndent()
		)
		val staleCacheKey = binderyMetadataCacheKey(
			baseUrl = "https://bindery.example.com/opds",
			payloadType = BinderyMetadataPayloadType.WhispersyncSidecar,
			path = path,
			apiKeyFingerprint = binderyApiKeyFingerprint("secret")
		)
		val metadataCache = RecordingBinderyMetadataCache().apply {
			runBlocking {
				put(
					BinderyMetadataCacheRecord(
						cacheKey = staleCacheKey,
						baseUrl = "https://bindery.example.com/opds",
						payloadType = BinderyMetadataPayloadType.WhispersyncSidecar,
						path = path,
						payloadJson = """
						{
						  "artifactId": "2",
						  "segments": [
						    {
						      "audioHref": "Part 01.mp3",
						      "startMs": 0,
						      "endMs": 18040,
						      "textHref": "OEBPS/Text/authorsforeword.xhtml",
						      "textStart": 0,
						      "textEnd": 78
						    }
						  ]
						}
						""".trimIndent(),
						updatedAtMillis = 1_000L
					)
				)
			}
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 2_000L }
		)

		val sidecar = repository.getWhispersyncSidecar(path).getOrThrow()

		assertEquals("I am not a good person.", sidecar.timeline.segments.single().spokenText)
		assertEquals(listOf(path), apiClient.whispersyncSidecarPaths)
		assertTrue(metadataCache.records.getValue(staleCacheKey).payloadJson.contains("I am not a good person."))
	}

	@Test
	fun whispersyncSidecarRefetchesFreshCacheWhenEbookTextIsMissing() = runBlocking {
		val path = "/api/v1/sync/artifacts/2"
		val apiClient = FakeBinderyApiClient(
			whispersyncSidecarJson = """
			{
			  "schema": "bindery.whispersync.sidecar.v1",
			  "bookId": 3959,
			  "ebookBookFileId": 212,
			  "audiobookBookFileId": 572,
			  "cues": [
			    {
			      "id": 3,
			      "audioHref": "Part 01.mp3",
			      "audioStart": 21.44,
			      "audioEnd": 27.52,
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "ebookStart": 123,
			      "ebookEnd": 190,
			      "text": "They call me Oculator Dramatis, Hero, Savior of the 17 Kingdoms.",
			      "ebookText": "THEY CALL ME OCULATOR DRAMATUS, HERO, SAVIOR OF THE TWELVE KINGDOMS"
			    }
			  ]
			}
			""".trimIndent()
		)
		val staleCacheKey = binderyMetadataCacheKey(
			baseUrl = "https://bindery.example.com/opds",
			payloadType = BinderyMetadataPayloadType.WhispersyncSidecar,
			path = path,
			apiKeyFingerprint = binderyApiKeyFingerprint("secret")
		)
		val metadataCache = RecordingBinderyMetadataCache().apply {
			runBlocking {
				put(
					BinderyMetadataCacheRecord(
						cacheKey = staleCacheKey,
						baseUrl = "https://bindery.example.com/opds",
						payloadType = BinderyMetadataPayloadType.WhispersyncSidecar,
						path = path,
						payloadJson = """
						{
						  "artifactId": "2",
						  "segments": [
						    {
						      "audioHref": "Part 01.mp3",
						      "startMs": 21440,
						      "endMs": 27520,
						      "textHref": "OEBPS/Text/authorsforeword.xhtml",
						      "textStart": 123,
						      "textEnd": 190,
						      "spokenText": "They call me Oculator Dramatis, Hero, Savior of the 17 Kingdoms."
						    }
						  ]
						}
						""".trimIndent(),
						updatedAtMillis = 1_000L
					)
				)
			}
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 2_000L }
		)

		val sidecar = repository.getWhispersyncSidecar(path).getOrThrow()

		assertEquals(
			"THEY CALL ME OCULATOR DRAMATUS, HERO, SAVIOR OF THE TWELVE KINGDOMS",
			sidecar.timeline.segments.single().ebookText
		)
		assertEquals(listOf(path), apiClient.whispersyncSidecarPaths)
		assertTrue(metadataCache.records.getValue(staleCacheKey).payloadJson.contains("ebookText"))
	}

	@Test
	fun clearMetadataCacheClearsConfiguredBinderyBaseUrl() = runBlocking {
		val metadataCache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(),
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L }
		)

		repository.clearMetadataCache().getOrThrow()

		assertEquals(listOf("https://bindery.example.com/opds"), metadataCache.clearedBaseUrls)
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

	@Test
	fun whispersyncAudiobookManifestFallsBackToExactBookFilePair() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			audiobookVersions = listOf(
				BinderyAudiobookVersion(
					id = 33,
					bookId = 3816,
					bookFileId = 632,
					title = "Wrong audio"
				),
				BinderyAudiobookVersion(
					id = 34,
					bookId = 3809,
					bookFileId = 633,
					title = "Bastille vs. the Evil Librarians"
				)
			),
			audiobookManifest = BinderyManifest(
				id = "urn:bindery:audiobook:34",
				title = "Bastille vs. the Evil Librarians"
			)
		)
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = RecordingBinderyMetadataCache(),
			currentTimeMillis = { 1_000L }
		)

		val manifest = repository.getWhispersyncAudiobookManifest(
			bookId = "3809",
			audiobookId = null,
			audiobookBookFileId = "633",
			audiobookManifestHref = null
		).getOrThrow()

		assertEquals("Bastille vs. the Evil Librarians", manifest.title)
		assertEquals(listOf("3809"), apiClient.audiobookVersionBookIds)
		assertEquals(listOf("34"), apiClient.audiobookManifestIds)
		assertTrue(apiClient.audiobookManifestPaths.isEmpty())
	}

}
