package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.manager.PreferenceManager

class BinderyRepositoryProgressCacheTest {
	@Test
	fun catalogDecodePreservesPublicationReadingOrderResources() {
		val catalog = decodeBinderyCatalogJson(
			"""
			{
			  "metadata": { "title": "Recently Added" },
			  "publications": [
			    {
			      "metadata": {
			        "identifier": "urn:bindery:book:3913",
			        "title": "The Maps of Middle-Earth"
			      },
			      "readingOrder": [
			        {
			          "href": "/opds/books/3913/resources/ebook-b84add4a73f1c4b01513",
			          "title": "The Maps of Middle-Earth",
			          "type": "application/epub+zip",
			          "properties": {
			            "language": "eng"
			          }
			        }
			      ]
			    }
			  ]
			}
			""".trimIndent()
		)

		val item = catalog.publications.single().readingOrder.single()
		assertEquals("/opds/books/3913/resources/ebook-b84add4a73f1c4b01513", item.href)
		assertEquals("application/epub+zip", item.type)
		assertEquals("eng", item.properties["language"])
	}

	@Test
	fun catalogDecodeTreatsExplicitNullPublicationListsAsEmpty() {
		val catalog = decodeBinderyCatalogJson(
			"""
			{
			  "metadata": { "title": "Author Detail" },
			  "images": null,
			  "links": null,
			  "navigation": null,
			  "publications": null
			}
			""".trimIndent()
		)

		assertEquals("Author Detail", catalog.title)
		assertEquals(emptyList(), catalog.images)
		assertEquals(emptyList(), catalog.links)
		assertEquals(emptyList(), catalog.navigation)
		assertEquals(emptyList(), catalog.publications)
	}

	@Test
	fun progressFetchUsesConfiguredOpdsUrlAndPreservesTypedReaderLocator() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			progress = BinderyReadingProgress(
				bookId = "3693",
				alias = "darko",
				kind = BinderyReadingProgressKind.Ebook,
				resourceHref = "/opds/books/3693/resources/epub",
				cfi = "epubcfi(/6/8!/4/1:0)",
				fragmentId = "chapter-03",
				progressFraction = 0.34,
				updatedAt = "2026-06-08T12:00:00Z"
			)
		)
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		val repository = BinderyRepository(preferences, apiClient)

		val progress = repository.getReadingProgress(bookId = "3693", alias = "darko").getOrThrow()

		assertEquals("3693", progress.bookId)
		assertEquals("darko", progress.alias)
		assertEquals(BinderyReadingProgressKind.Ebook, progress.kind)
		assertEquals("epubcfi(/6/8!/4/1:0)", progress.cfi)
		assertEquals("/opds/books/3693/resources/epub", progress.resourceHref)
		assertEquals(0.34, progress.progressFraction!!)
		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.progressFetchBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.progressFetchHeaders)
		assertEquals(listOf("3693"), apiClient.progressFetchBookIds)
		assertEquals(listOf<String?>("darko"), apiClient.progressFetchAliases)
	}

	@Test
	fun progressJsonDefaultsMissingKindToEbookForOlderBinderyResponses() {
		val progress = BinderyJson.decodeFromString(
			BinderyReadingProgress.serializer(),
			"""
			{
				"bookId": "3816",
				"resourceHref": "/opds/books/3816/resources/ebook-46bbc0be8508921f8174",
				"cfi": "epubcfi(/6/2)",
				"progressFraction": 0.02
			}
			""".trimIndent()
		)

		assertEquals("3816", progress.bookId)
		assertEquals(BinderyReadingProgressKind.Ebook, progress.kind)
		assertEquals("/opds/books/3816/resources/ebook-46bbc0be8508921f8174", progress.resourceHref)
		assertEquals(0.02, progress.progressFraction)
	}

	@Test
	fun progressJsonDecodesCurrentBinderyProgressSchema() {
		val progress = BinderyJson.decodeFromString(
			BinderyReadingProgress.serializer(),
			"""
			{
				"bookId": 3816,
				"alias": "navic-user-42",
				"resourceKey": "audio-c310a962a70802eb2e65",
				"href": "/opds/books/3816/resources/audio-c310a962a70802eb2e65",
				"position": 123.5,
				"duration": 456.75,
				"positionMs": 123500,
				"durationMs": 456750,
				"completed": false,
				"updatedAt": "2026-06-27T10:00:00Z"
			}
			""".trimIndent()
		)

		assertEquals("3816", progress.bookId)
		assertEquals("navic-user-42", progress.alias)
		assertEquals("audio-c310a962a70802eb2e65", progress.resourceKey)
		assertEquals("/opds/books/3816/resources/audio-c310a962a70802eb2e65", progress.href)
		assertEquals(123.5, progress.position)
		assertEquals(456.75, progress.duration)
		assertEquals(123500L, progress.positionMs)
		assertEquals(456750L, progress.durationMs)
		assertEquals(false, progress.completed)
		assertEquals("2026-06-27T10:00:00Z", progress.updatedAt)
	}

	@Test
	fun progressSaveUsesConfiguredOpdsUrlAndPreservesReadaloudResourcePosition() = runBlocking {
		val apiClient = FakeBinderyApiClient()
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		val repository = BinderyRepository(preferences, apiClient)
		val progress = BinderyReadingProgress(
			bookId = "3693",
			alias = "darko",
			kind = BinderyReadingProgressKind.Readaloud,
			resourceHref = "/opds/books/3693/resources/readaloud-1",
			textHref = "EPUB/Text/chapter-01.xhtml",
			fragmentId = "p-42",
			positionMs = 92_500L,
			durationMs = 180_000L,
			progressFraction = 0.51
		)

		repository.putReadingProgress(progress).getOrThrow()

		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.progressPutBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.progressPutHeaders)
		assertEquals(listOf(progress), apiClient.progressPutPayloads)
	}

	@Test
	fun progressCallsRequireEnabledConfiguredBinderyWithoutCallingApiClient() = runBlocking {
		val apiClient = FakeBinderyApiClient()
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = false
			binderyOpdsBaseUrl = "https://bindery.example.com/opds"
			binderyApiKey = "secret"
		}
		val repository = BinderyRepository(preferences, apiClient)

		assertFailsWith<IllegalStateException> {
			repository.getReadingProgress(bookId = "3693").getOrThrow()
		}
		assertFailsWith<IllegalStateException> {
			repository.putReadingProgress(
				BinderyReadingProgress(
					bookId = "3693",
					kind = BinderyReadingProgressKind.Ebook,
					cfi = "epubcfi(/6/8!/4/1:0)"
				)
			).getOrThrow()
		}
		assertEquals(emptyList(), apiClient.progressFetchBookIds)
		assertEquals(emptyList(), apiClient.progressPutPayloads)
	}

	@Test
	fun catalogUsesFreshMetadataCacheWithoutCallingApiClient() = runBlocking {
		val apiClient = FakeBinderyApiClient(catalog = BinderyCatalog(title = "Live Books"))
		val metadataCache = RecordingBinderyMetadataCache().apply {
			put(
				BinderyMetadataCacheRecord(
					cacheKey = binderyMetadataCacheKey(
						baseUrl = "https://bindery.example.com/opds",
						payloadType = BinderyMetadataPayloadType.Catalog,
						path = "/opds/books?owned=1",
						apiKeyFingerprint = binderyApiKeyFingerprint("secret")
					),
					baseUrl = "https://bindery.example.com/opds",
					payloadType = BinderyMetadataPayloadType.Catalog,
					path = "/opds/books?owned=1",
					payloadJson = """{"title":"Cached Books"}""",
					updatedAtMillis = 1_000L
				)
			)
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L + BINDERY_METADATA_CACHE_FRESH_MILLIS - 1L }
		)

		val catalog = repository.getCatalog("/opds/books?owned=1").getOrThrow()

		assertEquals("Cached Books", catalog.title)
		assertEquals(emptyList(), apiClient.catalogPaths)
	}

	@Test
	fun catalogStoresLiveResponseInMetadataCache() = runBlocking {
		val apiClient = FakeBinderyApiClient(catalog = BinderyCatalog(title = "Live Books"))
		val metadataCache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 2_000L }
		)

		val catalog = repository.getCatalog("/opds/books?owned=1").getOrThrow()

		assertEquals("Live Books", catalog.title)
		assertEquals(listOf("/opds/books?owned=1"), apiClient.catalogPaths)
		val cached = metadataCache.records.values.single()
		assertEquals("https://bindery.example.com/opds", cached.baseUrl)
		assertEquals(BinderyMetadataPayloadType.Catalog, cached.payloadType)
		assertEquals("/opds/books?owned=1", cached.path)
		assertEquals(2_000L, cached.updatedAtMillis)
		assertTrue(cached.payloadJson.contains("Live Books"))
	}

	@Test
	fun catalogFallsBackToStaleMetadataCacheWhenLiveFetchFails() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			catalog = BinderyCatalog(title = "Live Books"),
			catalogFailure = IllegalStateException("Bindery unavailable")
		)
		val metadataCache = RecordingBinderyMetadataCache().apply {
			put(
				BinderyMetadataCacheRecord(
					cacheKey = binderyMetadataCacheKey(
						baseUrl = "https://bindery.example.com/opds",
						payloadType = BinderyMetadataPayloadType.Catalog,
						path = "/opds/books?owned=1",
						apiKeyFingerprint = binderyApiKeyFingerprint("secret")
					),
					baseUrl = "https://bindery.example.com/opds",
					payloadType = BinderyMetadataPayloadType.Catalog,
					path = "/opds/books?owned=1",
					payloadJson = """{"title":"Stale Books"}""",
					updatedAtMillis = 1_000L
				)
			)
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L + BINDERY_METADATA_CACHE_FRESH_MILLIS + 1L }
		)

		val catalog = repository.getCatalog("/opds/books?owned=1").getOrThrow()

		assertEquals("Stale Books", catalog.title)
		assertEquals(listOf("/opds/books?owned=1"), apiClient.catalogPaths)
	}

	@Test
	fun bookDetailCacheAccessorsReturnStaleMetadataWithoutCallingApiClient() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			audiobookVersions = listOf(BinderyAudiobookVersion(id = 999, narrator = "Live Narrator")),
			bookSync = BinderyBookSync(bookId = 3816, whispersyncStatus = "live")
		)
		val metadataCache = RecordingBinderyMetadataCache().apply {
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.Manifest,
				path = "3816",
				payloadJson = BinderyJson.encodeToString(
					BinderyManifest(
						id = "urn:bindery:book:3816",
						title = "Cached Hobbit",
						author = "J. R. R. Tolkien"
					)
				)
			)
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.Resources,
				path = "3816",
				payloadJson = BinderyJson.encodeToString(
					BinderyResourceCatalog(
						title = "Cached Resources",
						resources = listOf(
							BinderyBookResource(
								href = "/opds/books/3816/resources/ebook-1",
								title = "Cached EPUB",
								type = "application/epub+zip"
							)
						)
					)
				)
			)
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.AudiobookVersions,
				path = "book:3816:limit:100",
				payloadJson = BinderyJson.encodeToString(
					listOf(
						BinderyAudiobookVersion(
							id = 88,
							bookFileId = 694,
							narrator = "Rob Inglis"
						)
					)
				)
			)
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.BookSync,
				path = "3816",
				payloadJson = BinderyJson.encodeToString(
					BinderyBookSync(
						bookId = 3816,
						whispersyncStatus = "ready"
					)
				)
			)
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L + BINDERY_METADATA_CACHE_FRESH_MILLIS + 1L }
		)

		val manifest = repository.getCachedManifest("3816").getOrThrow()
		val resources = repository.getCachedBookResources("3816").getOrThrow()
		val audiobooks = repository.getCachedAudiobookVersions("3816").getOrThrow()
		val sync = repository.getCachedBookSync("3816").getOrThrow()

		assertEquals("Cached Hobbit", manifest?.title)
		assertEquals("Cached Resources", resources?.title)
		assertEquals("Cached EPUB", resources?.resources?.single()?.title)
		assertEquals("Rob Inglis", audiobooks?.single()?.narrator)
		assertEquals("ready", sync?.whispersyncStatus)
		assertEquals(emptyList(), apiClient.manifestBookIds)
		assertEquals(emptyList(), apiClient.resourceCatalogBookIds)
		assertEquals(emptyList(), apiClient.audiobookVersionBookIds)
		assertEquals(emptyList(), apiClient.bookSyncIds)
	}

	@Test
	fun binderyEntityCacheAccessorsReturnStaleMetadataWithoutCallingApiClient() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			catalog = BinderyCatalog(title = "Live Catalog"),
			bookFindings = BinderyCatalog(title = "Live Findings"),
			audiobookVersion = BinderyAudiobookVersion(id = 999, narrator = "Live Narrator"),
			audiobookManifest = BinderyManifest(id = "urn:bindery:audiobook:999", title = "Live Manifest")
		)
		val metadataCache = RecordingBinderyMetadataCache().apply {
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.Catalog,
				path = "/opds/books?owned=1",
				payloadJson = BinderyJson.encodeToString(BinderyCatalog(title = "Cached Catalog"))
			)
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.AudiobookDetail,
				path = "88",
				payloadJson = BinderyJson.encodeToString(
					BinderyAudiobookVersion(id = 88, narrator = "Cached Narrator")
				)
			)
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.AudiobookManifest,
				path = "88",
				payloadJson = BinderyJson.encodeToString(
					BinderyManifest(id = "urn:bindery:audiobook:88", title = "Cached Manifest")
				)
			)
			putBookDetailCacheRecord(
				payloadType = BinderyMetadataPayloadType.BookFindings,
				path = "3816",
				payloadJson = BinderyJson.encodeToString(BinderyCatalog(title = "Cached Findings"))
			)
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 1_000L + BINDERY_METADATA_CACHE_FRESH_MILLIS + 1L }
		)

		val catalog = repository.getCachedCatalog("/opds/books?owned=1").getOrThrow()
		val audiobookDetail = repository.getCachedAudiobookDetail("88").getOrThrow()
		val audiobookManifest = repository.getCachedAudiobookManifest("88").getOrThrow()
		val findings = repository.getCachedBookFindings("3816").getOrThrow()

		assertEquals("Cached Catalog", catalog?.title)
		assertEquals("Cached Narrator", audiobookDetail?.narrator)
		assertEquals("Cached Manifest", audiobookManifest?.title)
		assertEquals("Cached Findings", findings?.title)
		assertEquals(emptyList(), apiClient.catalogPaths)
		assertEquals(emptyList(), apiClient.audiobookDetailIds)
		assertEquals(emptyList(), apiClient.audiobookManifestIds)
		assertEquals(emptyList(), apiClient.bookFindingIds)
	}

	@Test
	fun performActionRequiresEnabledConfiguredBinderyWithoutCallingApiClient() = runBlocking {
		val apiClient = FakeBinderyApiClient()
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = false
			binderyOpdsBaseUrl = "https://bindery.example.com/opds"
			binderyApiKey = "secret"
		}
		val repository = BinderyRepository(preferences, apiClient)

		assertFailsWith<IllegalStateException> {
			repository.performAction("/opds/books/1/download").getOrThrow()
		}
		assertEquals(0, apiClient.actionPaths.size)
	}

}

private suspend fun RecordingBinderyMetadataCache.putBookDetailCacheRecord(
	payloadType: String,
	path: String,
	payloadJson: String
) {
	put(
		BinderyMetadataCacheRecord(
			cacheKey = binderyMetadataCacheKey(
				baseUrl = "https://bindery.example.com/opds",
				payloadType = payloadType,
				path = path,
				apiKeyFingerprint = binderyApiKeyFingerprint("secret")
			),
			baseUrl = "https://bindery.example.com/opds",
			payloadType = payloadType,
			path = path,
			payloadJson = payloadJson,
			updatedAtMillis = 1_000L
		)
	)
}
