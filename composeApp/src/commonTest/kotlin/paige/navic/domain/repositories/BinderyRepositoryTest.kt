package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
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
		val progress = Json {
			ignoreUnknownKeys = true
			isLenient = true
		}.decodeFromString(
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
						path = "/opds/books?owned=1"
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
						path = "/opds/books?owned=1"
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

	@Test
	fun catalogJsonPreservesDetailMetadataAndPublicationFields() {
		val catalog = decodeBinderyCatalogJson(
			"""
			{
			  "metadata": {
			    "title": "Brandon Sanderson",
			    "identifier": "urn:bindery:author:28",
			    "description": "Author biography",
			    "subject": ["Fantasy", "Hardcover"]
			  },
			  "images": [
			    {"href": "https://example.com/author.jpg", "type": "image/jpeg", "rel": "cover"}
			  ],
			  "properties": {
			    "collectionType": "series",
			    "memberCount": 4,
			    "startYear": 2009,
			    "yearRange": {"start": 2009, "end": 2022},
			    "availability": {
			      "owned": true,
			      "complete": false,
			      "ownedBooks": 3,
			      "missingBooks": 1,
			      "totalBooks": 4,
			      "formats": ["audiobook", "ebook"],
			      "ownedFormats": ["audiobook", "ebook"],
			      "ownedLanguages": ["eng"],
			      "ownedCombinations": [
			        {"format": "audiobook", "language": "eng"},
			        {"format": "ebook", "language": "eng"}
			      ],
			      "languages": ["eng"],
			      "mode": "any"
			    }
			  },
			  "navigation": [
			    {
			      "href": "/opds/authors/28/collections",
			      "title": "Collections",
			      "properties": {
			        "memberCount": 9,
			        "yearRange": {"start": 2005, "end": 2023},
			        "availability": {
			          "owned": true,
			          "complete": true,
			          "ownedBooks": 9,
			          "missingBooks": 0,
			          "totalBooks": 9
			        }
			      },
			      "links": [
			        {
			          "href": "/opds/authors/28/unmonitor",
			          "title": "Unmonitor author",
			          "type": "application/json",
			          "rel": "https://bindery.app/opds/rel/unmonitor"
			        }
			      ]
			    }
			  ],
			  "publications": [
			    {
			      "metadata": {
			        "title": "The Final Empire",
			        "identifier": "urn:bindery:book:3686",
			        "published": "2001-01-01",
			        "description": "Book description",
			        "subject": ["series:Mistborn", "Fantasy"],
			        "duration": 113136.178,
			        "author": [{"name": "Brandon Sanderson", "sortAs": "Sanderson, Brandon"}]
			      },
			      "properties": {
			        "collectionPosition": "1",
			        "collectionPositionSort": 1,
			        "collectionTitle": "Mistborn",
			        "availability": {
			          "owned": false,
			          "complete": false,
			          "ownedBooks": 0,
			          "missingBooks": 1,
			          "totalBooks": 1
			        }
			      },
			      "images": [{"href": "/opds/books/3686/cover", "type": "image/jpeg", "rel": "cover"}]
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals("Brandon Sanderson", catalog.title)
		assertEquals("urn:bindery:author:28", catalog.identifier)
		assertEquals("Author biography", catalog.description)
		assertEquals(listOf("Fantasy", "Hardcover"), catalog.subjects)
		assertEquals("https://example.com/author.jpg", catalog.images.single().href)
		assertEquals("series", catalog.properties["collectionType"])
		assertEquals("4", catalog.properties["memberCount"])
		assertEquals("2009", catalog.properties["startYear"])
		assertNull(catalog.properties["yearRange"])
		assertNull(catalog.properties["availability"])
		assertEquals(
			BinderyAvailability(
				owned = true,
				complete = false,
				ownedBooks = 3,
				missingBooks = 1,
				totalBooks = 4,
				formats = listOf("audiobook", "ebook"),
				ownedFormats = listOf("audiobook", "ebook"),
				ownedLanguages = listOf("eng"),
				ownedCombinations = listOf(
					BinderyAvailabilityCombination(
						format = "audiobook",
						language = "eng"
					),
					BinderyAvailabilityCombination(
						format = "ebook",
						language = "eng"
					)
				),
				languages = listOf("eng"),
				mode = "any"
			),
			catalog.availability
		)
		assertEquals("9", catalog.navigation.single().properties["memberCount"])
		assertNull(catalog.navigation.single().properties["yearRange"])
		assertEquals(
			"/opds/authors/28/unmonitor",
			catalog.navigation.single().links.single().href
		)
		assertEquals(
			listOf("https://bindery.app/opds/rel/unmonitor"),
			catalog.navigation.single().links.single().rel
		)
		assertEquals(
			BinderyAvailability(
				owned = true,
				complete = true,
				ownedBooks = 9,
				missingBooks = 0,
				totalBooks = 9
			),
			catalog.navigation.single().availability
		)
		assertEquals("2001-01-01", catalog.publications.single().published)
		assertEquals("Book description", catalog.publications.single().description)
		assertEquals(listOf("series:Mistborn", "Fantasy"), catalog.publications.single().subjects)
		assertEquals(113136.178, catalog.publications.single().durationSeconds)
		assertEquals("1", catalog.publications.single().properties["collectionPosition"])
		assertEquals("1", catalog.publications.single().properties["collectionPositionSort"])
		assertEquals("Mistborn", catalog.publications.single().properties["collectionTitle"])
		assertEquals(
			BinderyAvailability(ownedBooks = 0, missingBooks = 1, totalBooks = 1),
			catalog.publications.single().availability
		)
	}

	@Test
	fun catalogJsonDecodesTopLevelPublicationAvailabilityForEbookOnlyBooks() {
		val catalog = decodeBinderyCatalogJson(
			"""
			{
			  "metadata": {"title": "Recently Added"},
			  "publications": [
			    {
			      "metadata": {
			        "title": "The Maps of Middle-Earth",
			        "identifier": "urn:bindery:book:3913",
			        "author": [{"name": "J.R.R. Tolkien"}]
			      },
			      "properties": {
			        "availability": {
			          "complete": false,
			          "formats": ["ebook", "audiobook"],
			          "languages": ["eng"],
			          "missingCombinations": [
			            {"format": "audiobook", "language": "eng"}
			          ],
			          "mode": "any",
			          "owned": true,
			          "ownedCombinations": [
			            {"format": "ebook", "language": "eng"}
			          ],
			          "ownedFormats": ["ebook"],
			          "ownedLanguages": ["eng"]
			        }
			      }
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals(
			BinderyAvailability(
				owned = true,
				complete = false,
				formats = listOf("ebook", "audiobook"),
				ownedFormats = listOf("ebook"),
				ownedLanguages = listOf("eng"),
				ownedCombinations = listOf(
					BinderyAvailabilityCombination(format = "ebook", language = "eng")
				),
				languages = listOf("eng"),
				mode = "any"
			),
			catalog.publications.single().availability
		)
	}

	@Test
	fun catalogJsonPreservesFindingMetadataMappingsAndFiles() {
		val catalog = decodeBinderyCatalogJson(
			"""
			{
			  "metadata": {"title": "Findings"},
			  "publications": [
			    {
			      "metadata": {
			        "title": "J.R.R. Tolkien (author) - The Hobbit.epub",
			        "identifier": "urn:bindery:finding:894",
			        "description": "Provider notes",
			        "author": [{"name": "J.R.R. Tolkien"}]
			      },
			      "properties": {
			        "findingId": 894,
			        "mediaType": "ebook",
			        "language": "eng",
			        "format": "epub",
			        "provider": "Anna's Archive",
			        "providerKind": "metadata",
			        "publisher": "Houghton Mifflin Harcourt",
			        "edition": "Annotated",
			        "sizeBytes": 27151009,
			        "fileCount": 1,
			        "availabilityStatus": "imported",
			        "providerComments": "theme: Middle Earth",
			        "files": [
			          {
			            "name": "The Hobbit.epub",
			            "format": "epub",
			            "language": "eng",
			            "size": 27151009
			          }
			        ],
			        "mappings": [
			          {
			            "id": 60452,
			            "bookId": 3816,
			            "bookTitle": "The Hobbit",
			            "authorName": "J.R.R. Tolkien",
			            "confidence": 100,
			            "mediaType": "ebook",
			            "targetLanguage": "eng",
			            "acquisitionStatus": "imported",
			            "acquisitionScope": "file_selection",
			            "selectedBytes": 27151009,
			            "bookFileId": 765,
			            "bookFileFormat": "epub",
			            "bookFileSizeBytes": 27151009,
			            "sourceCatalogCandidateId": 894
			          }
			        ]
			      },
			      "images": [{"href": "/opds/books/3816/cover", "type": "image/jpeg", "rel": "cover"}],
			      "links": [
			        {"href": "/opds/findings/894", "rel": "self", "type": "application/opds-publication+json"},
			        {
			          "href": "/api/v1/findings/894/acquire",
			          "title": "Request download",
			          "type": "application/json",
			          "rel": "https://bindery.app/opds/rel/download-request"
			        }
			      ]
			    }
			  ]
			}
			""".trimIndent()
		)

		val finding = catalog.publications.single().finding
		requireNotNull(finding)
		assertEquals("894", finding.findingId)
		assertEquals("ebook", finding.mediaType)
		assertEquals("eng", finding.language)
		assertEquals("epub", finding.format)
		assertEquals("Anna's Archive", finding.provider)
		assertEquals("Houghton Mifflin Harcourt", finding.publisher)
		assertEquals("Annotated", finding.edition)
		assertEquals(27151009L, finding.sizeBytes)
		assertEquals(1, finding.fileCount)
		assertEquals("imported", finding.availabilityStatus)
		assertEquals("theme: Middle Earth", finding.providerComments)
		assertEquals("The Hobbit.epub", finding.files.single().name)
		assertEquals(27151009L, finding.files.single().sizeBytes)
		assertEquals("3816", finding.mappings.single().bookId)
		assertEquals("The Hobbit", finding.mappings.single().bookTitle)
		assertEquals(100.0, finding.mappings.single().confidence)
		assertEquals("765", finding.mappings.single().bookFileId)
		assertEquals("epub", finding.mappings.single().bookFileFormat)
		assertEquals(27151009L, finding.mappings.single().bookFileSizeBytes)
		assertEquals("894", finding.mappings.single().sourceCatalogCandidateId)
		assertNull(catalog.publications.single().properties["files"])
		assertNull(catalog.publications.single().properties["mappings"])
	}

	@Test
	fun repositoryFetchesBookFindingsFromCanonicalRoute() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			bookFindings = BinderyCatalog(title = "Book Findings")
		)
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		val repository = BinderyRepository(preferences, apiClient)

		assertEquals("Book Findings", repository.getBookFindings("3693").getOrThrow().title)
		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.bookFindingBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.bookFindingHeaders)
		assertEquals(listOf("3693"), apiClient.bookFindingIds)
	}

	@Test
	fun bookFindingsUseFreshMetadataCacheWithoutCallingApiClient() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			bookFindings = BinderyCatalog(title = "Live Findings")
		)
		val metadataCache = RecordingBinderyMetadataCache().apply {
			put(
				BinderyMetadataCacheRecord(
					cacheKey = binderyMetadataCacheKey(
						baseUrl = "https://bindery.example.com/opds",
						payloadType = BinderyMetadataPayloadType.BookFindings,
						path = "3913"
					),
					baseUrl = "https://bindery.example.com/opds",
					payloadType = BinderyMetadataPayloadType.BookFindings,
					path = "3913",
					payloadJson = """{"title":"Cached Findings"}""",
					updatedAtMillis = 4_000L
				)
			)
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 4_000L + BINDERY_METADATA_CACHE_FRESH_MILLIS - 1L }
		)

		val catalog = repository.getBookFindings("3913").getOrThrow()

		assertEquals("Cached Findings", catalog.title)
		assertEquals(emptyList(), apiClient.bookFindingIds)
	}

	@Test
	fun bookFindingsFallBackToStaleMetadataCacheWhenLiveFetchFails() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			bookFindingsFailure = IllegalStateException("Bindery unavailable")
		)
		val metadataCache = RecordingBinderyMetadataCache().apply {
			put(
				BinderyMetadataCacheRecord(
					cacheKey = binderyMetadataCacheKey(
						baseUrl = "https://bindery.example.com/opds",
						payloadType = BinderyMetadataPayloadType.BookFindings,
						path = "3913"
					),
					baseUrl = "https://bindery.example.com/opds",
					payloadType = BinderyMetadataPayloadType.BookFindings,
					path = "3913",
					payloadJson = """{"title":"Stale Findings"}""",
					updatedAtMillis = 4_000L
				)
			)
		}
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 4_000L + BINDERY_METADATA_CACHE_FRESH_MILLIS + 1L }
		)

		val catalog = repository.getBookFindings("3913").getOrThrow()

		assertEquals("Stale Findings", catalog.title)
		assertEquals(listOf("3913"), apiClient.bookFindingIds)
	}

	@Test
	fun manifestJsonPreservesBookLinksPropertiesDurationAndReadingOrder() {
		val manifest = decodeBinderyManifestJson(
			"""
			{
			  "metadata": {
			    "title": "Alcatraz versus the Evil Librarians",
			    "identifier": "urn:bindery:book:3693",
			    "published": "2010-09-18",
			    "description": "Book description",
			    "subject": ["Fantasy", "Juvenile fiction"],
			    "duration": 20054.152,
			    "author": [{"name": "Brandon Sanderson"}]
			  },
			  "links": [
			    {"href": "/opds/books/3693", "type": "application/opds-publication+json", "rel": "self"},
			    {
			      "href": "/opds/books/3693/resources/ebook-1",
			      "type": "application/epub+zip",
			      "rel": "http://opds-spec.org/acquisition",
			      "title": "Alcatraz EPUB",
			      "properties": {
			        "kind": "ebook",
			        "size": 431666,
			        "deliveryPolicy": "local"
			      }
			    }
			  ],
			  "images": [{"href": "/opds/books/3693/cover", "type": "image/jpeg", "rel": "cover"}],
			  "readingOrder": [
			    {
			      "href": "/opds/books/3693/resources/audio-1",
			      "type": "audio/mpeg",
			      "title": "Part 01",
			      "duration": 3763.592,
			      "properties": {
			        "kind": "audio",
			        "size": 120973860,
			        "trackNumber": 1
			      }
			    }
			  ],
			  "properties": {
			    "sourceProvider": "hardcover",
			    "sourceUrl": "https://hardcover.app/books/alcatraz"
			  }
			}
			""".trimIndent()
		)

		assertEquals("urn:bindery:book:3693", manifest.id)
		assertEquals("Alcatraz versus the Evil Librarians", manifest.title)
		assertEquals("Brandon Sanderson", manifest.author)
		assertEquals("2010-09-18", manifest.published)
		assertEquals("Book description", manifest.description)
		assertEquals(listOf("Fantasy", "Juvenile fiction"), manifest.subjects)
		assertEquals(20054.152, manifest.durationSeconds)
		assertEquals("/opds/books/3693/cover", manifest.images.single().href)
		assertEquals("hardcover", manifest.properties["sourceProvider"])
		assertEquals("/opds/books/3693", manifest.links.first().href)
		assertEquals("ebook", manifest.links[1].properties["kind"])
		assertEquals("431666", manifest.links[1].properties["size"])
		assertEquals(
			BinderyReadingOrderItem(
				href = "/opds/books/3693/resources/audio-1",
				title = "Part 01",
				type = "audio/mpeg",
				durationSeconds = 3763.592,
				sizeBytes = 120973860,
				properties = mapOf(
					"kind" to "audio",
					"size" to "120973860",
					"trackNumber" to "1"
				),
				propertyValues = BinderyPropertyBag(
					mapOf(
						"kind" to BinderyPropertyValue.StringValue("audio"),
						"size" to BinderyPropertyValue.NumberValue(120973860.0, "120973860"),
						"trackNumber" to BinderyPropertyValue.NumberValue(1.0, "1")
					)
				),
				metadata = BinderyResourceMetadata(trackNumber = 1)
			),
			manifest.readingOrder.single()
		)
	}

	@Test
	fun resourceCatalogJsonPreservesAudiobookAndEbookResources() {
		val catalog = decodeBinderyResourceCatalogJson(
			"""
			{
			  "metadata": {"title": "Alcatraz Resources"},
			  "resources": [
			    {
			      "href": "/opds/books/3693/resources/ebook-1",
			      "type": "application/epub+zip",
			      "title": "Alcatraz EPUB",
			      "properties": {
			        "kind": "ebook",
			        "size": 431666,
			        "trackNumber": 1
			      }
			    },
			    {
			      "href": "/opds/books/3693/resources/audio-1",
			      "type": "audio/mpeg",
			      "title": "Part 01",
			      "duration": 3763.592,
			      "properties": {
			        "kind": "audio",
			        "size": 120973860,
			        "trackNumber": 1
			      }
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals("Alcatraz Resources", catalog.title)
		assertEquals(
			BinderyBookResource(
				href = "/opds/books/3693/resources/ebook-1",
				title = "Alcatraz EPUB",
				type = "application/epub+zip",
				kind = "ebook",
				durationSeconds = null,
				sizeBytes = 431666,
				properties = mapOf(
					"kind" to "ebook",
					"size" to "431666",
					"trackNumber" to "1"
				),
				propertyValues = BinderyPropertyBag(
					mapOf(
						"kind" to BinderyPropertyValue.StringValue("ebook"),
						"size" to BinderyPropertyValue.NumberValue(431666.0, "431666"),
						"trackNumber" to BinderyPropertyValue.NumberValue(1.0, "1")
					)
				),
				metadata = BinderyResourceMetadata(trackNumber = 1)
			),
			catalog.resources.first()
		)
		assertEquals("audio", catalog.resources[1].kind)
		assertEquals(3763.592, catalog.resources[1].durationSeconds)
		assertEquals(120973860, catalog.resources[1].sizeBytes)
	}

	@Test
	fun resourceAndReadingOrderJsonPreserveStructuredAudioAndSourceMetadata() {
		val manifest = decodeBinderyManifestJson(
			"""
			{
			  "metadata": {"title": "Alcatraz"},
			  "readingOrder": [
			    {
			      "href": "/opds/books/3693/resources/audio-1",
			      "type": "audio/mpeg",
			      "title": "Part 01",
			      "duration": 3763.592,
			      "properties": {
			        "kind": "audio",
			        "resourceKey": "audio-001",
			        "relativePath": "Audio/Part 01.mp3",
			        "durationMs": 3763592,
			        "language": "eng",
			        "chapterLabel": "Chapter 1",
			        "sectionLabel": "Opening",
			        "trackNumber": 1,
			        "discNumber": 1,
			        "narrator": "Michael Kramer",
			        "author": "Brandon Sanderson",
			        "editionSuffix": "unabridged",
			        "sourceProvider": "audible",
			        "audio": {
			          "codec": "mp3",
			          "bitrateKbps": 128,
			          "sampleRateHz": 44100,
			          "channels": 2,
			          "qualityLabel": "High"
			        },
			        "sourceRelease": {
			          "provider": "Audible",
			          "sourceUrl": "https://example.com/audible/alcatraz",
			          "narrator": "Michael Kramer",
			          "readBy": "Michael Kramer",
			          "edition": "Unabridged",
			          "format": "MP3",
			          "categories": ["Fantasy", "Juvenile fiction"],
			          "keywords": ["alcatraz", "sanderson"]
			        }
			      }
			    }
			  ]
			}
			""".trimIndent()
		)
		val resources = decodeBinderyResourceCatalogJson(
			"""
			{
			  "metadata": {"title": "Alcatraz Resources"},
			  "resources": [
			    {
			      "href": "/opds/books/3693/resources/audio-1",
			      "type": "audio/mpeg",
			      "title": "Part 01",
			      "duration": 3763.592,
			      "properties": {
			        "kind": "audio",
			        "resourceKey": "audio-001",
			        "relativePath": "Audio/Part 01.mp3",
			        "durationMs": 3763592,
			        "language": "eng",
			        "chapterLabel": "Chapter 1",
			        "sectionLabel": "Opening",
			        "trackNumber": 1,
			        "discNumber": 1,
			        "narrator": "Michael Kramer",
			        "author": "Brandon Sanderson",
			        "editionSuffix": "unabridged",
			        "sourceProvider": "audible",
			        "audio": {
			          "codec": "mp3",
			          "bitrateKbps": 128,
			          "sampleRateHz": 44100,
			          "channels": 2,
			          "qualityLabel": "High"
			        },
			        "sourceRelease": {
			          "provider": "Audible",
			          "sourceUrl": "https://example.com/audible/alcatraz",
			          "narrator": "Michael Kramer",
			          "readBy": "Michael Kramer",
			          "edition": "Unabridged",
			          "format": "MP3",
			          "categories": ["Fantasy", "Juvenile fiction"],
			          "keywords": ["alcatraz", "sanderson"]
			        }
			      }
			    }
			  ]
			}
			""".trimIndent()
		)
		val expectedMetadata = BinderyResourceMetadata(
			resourceKey = "audio-001",
			relativePath = "Audio/Part 01.mp3",
			durationMs = 3763592,
			language = "eng",
			chapterLabel = "Chapter 1",
			sectionLabel = "Opening",
			trackNumber = 1,
			discNumber = 1,
			narrator = "Michael Kramer",
			author = "Brandon Sanderson",
			editionSuffix = "unabridged",
			sourceProvider = "audible",
			audio = BinderyAudioMetadata(
				codec = "mp3",
				bitrateKbps = 128,
				sampleRateHz = 44100,
				channels = 2,
				qualityLabel = "High"
			),
			sourceRelease = BinderySourceReleaseMetadata(
				provider = "Audible",
				sourceUrl = "https://example.com/audible/alcatraz",
				narrator = "Michael Kramer",
				readBy = "Michael Kramer",
				edition = "Unabridged",
				format = "MP3",
				categories = listOf("Fantasy", "Juvenile fiction"),
				keywords = listOf("alcatraz", "sanderson")
			)
		)

		assertEquals(expectedMetadata, manifest.readingOrder.single().metadata)
		assertEquals(expectedMetadata, resources.resources.single().metadata)
		assertNull(manifest.readingOrder.single().properties["audio"])
		assertNull(resources.resources.single().properties["sourceRelease"])
	}

	@Test
	fun ebookResourceAndFindingJsonKeepEbookButSuppressAudioMetadata() {
		val resources = decodeBinderyResourceCatalogJson(
			"""
			{
			  "metadata": {"title": "The Maps Resources"},
			  "resources": [
			    {
			      "href": "/opds/books/3913/resources/ebook-1",
			      "type": "application/epub+zip",
			      "title": "[Publisher] The Maps of Middle-Earth",
			      "properties": {
			        "kind": "ebook",
			        "format": "epub",
			        "language": "eng",
			        "bitrateBps": 0,
			        "sampleRateHz": 0,
			        "audio": {
			          "bitrateKbps": 0,
			          "sampleRateHz": 0,
			          "channels": 0
			        }
			      }
			    }
			  ]
			}
			""".trimIndent()
		)
		val findings = decodeBinderyCatalogJson(
			"""
			{
			  "metadata": {"title": "Findings"},
			  "publications": [
			    {
			      "metadata": {
			        "identifier": "urn:bindery:finding:ebook",
			        "title": "The Maps EPUB",
			        "properties": {
			          "findingId": "ebook",
			          "mediaType": "ebook",
			          "format": "epub",
			          "language": "eng",
			          "bitrateBps": 0,
			          "sampleRateHz": 0,
			          "files": [
			            {
			              "name": "The Maps.epub",
			              "format": "epub",
			              "bitrateBps": 0,
			              "sampleRateHz": 0
			            }
			          ]
			        }
			      }
			    }
			  ]
			}
			""".trimIndent()
		)

		val resource = resources.resources.single()
		assertEquals("ebook", resource.kind)
		assertEquals("application/epub+zip", resource.type)
		assertEquals("/opds/books/3913/resources/ebook-1", resource.href)
		assertNull(resource.metadata.audio)
		assertNull(resource.propertyValues.values["audio"])
		val finding = findings.publications.single().finding!!
		assertEquals("ebook", finding.mediaType)
		assertEquals("epub", finding.format)
		assertNull(finding.bitrateBps)
		assertNull(finding.sampleRateHz)
		assertNull(finding.files.single().bitrateBps)
		assertNull(finding.files.single().sampleRateHz)
	}

	@Test
	fun opdsPropertiesExposeTypedPropertyBagsForBookResourceAndReadingOrderMetadata() {
		val manifest = decodeBinderyManifestJson(
			"""
			{
			  "metadata": {"title": "Alcatraz"},
			  "properties": {
			    "sourceProvider": "hardcover",
			    "readaloud": true,
			    "qualityScore": 4.5,
			    "tags": ["storyteller", "media-overlay"],
			    "sourceRelease": {
			      "provider": "Hardcover",
			      "edition": "Deluxe"
			    }
			  },
			  "readingOrder": [
			    {
			      "href": "/opds/books/3693/resources/audio-1",
			      "type": "audio/mpeg",
			      "title": "Part 01",
			      "properties": {
			        "trackNumber": 1,
			        "audio": {
			          "codec": "mp3",
			          "channels": 2
			        }
			      }
			    }
			  ]
			}
			""".trimIndent()
		)
		val resources = decodeBinderyResourceCatalogJson(
			"""
			{
			  "metadata": {"title": "Alcatraz Resources"},
			  "resources": [
			    {
			      "href": "/opds/books/3693/resources/readaloud-1",
			      "type": "application/epub+zip",
			      "title": "Alcatraz Readaloud",
			      "properties": {
			        "kind": "ebook",
			        "mediaOverlay": true,
			        "resourceKey": "readaloud-001",
			        "clips": [
			          {"fragmentId": "frag-1", "startSeconds": 0.0, "endSeconds": 4.2}
			        ]
			      }
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals("hardcover", manifest.propertyValues.string("sourceProvider"))
		assertEquals(true, manifest.propertyValues.boolean("readaloud"))
		assertEquals(4.5, manifest.propertyValues.number("qualityScore"))
		assertEquals(
			listOf("storyteller", "media-overlay"),
			manifest.propertyValues.array("tags").mapNotNull { (it as? BinderyPropertyValue.StringValue)?.value }
		)
		assertEquals(
			BinderyPropertyValue.ObjectValue(
				mapOf(
					"provider" to BinderyPropertyValue.StringValue("Hardcover"),
					"edition" to BinderyPropertyValue.StringValue("Deluxe")
				)
			),
			manifest.propertyValues["sourceRelease"]
		)
		assertEquals(1.0, manifest.readingOrder.single().propertyValues.number("trackNumber"))
		assertEquals(
			BinderyPropertyValue.ObjectValue(
				mapOf(
					"codec" to BinderyPropertyValue.StringValue("mp3"),
					"channels" to BinderyPropertyValue.NumberValue(2.0, "2")
				)
			),
			manifest.readingOrder.single().propertyValues["audio"]
		)
		assertEquals(true, resources.resources.single().propertyValues.boolean("mediaOverlay"))
		assertEquals(
			BinderyPropertyValue.ArrayValue(
				listOf(
					BinderyPropertyValue.ObjectValue(
						mapOf(
							"fragmentId" to BinderyPropertyValue.StringValue("frag-1"),
							"startSeconds" to BinderyPropertyValue.NumberValue(0.0, "0.0"),
							"endSeconds" to BinderyPropertyValue.NumberValue(4.2, "4.2")
						)
					)
				)
			),
			resources.resources.single().propertyValues["clips"]
		)
	}

	private class FakeBinderyApiClient(
		private val rootCatalog: BinderyCatalog = BinderyCatalog(title = "Bindery"),
		private val catalog: BinderyCatalog = rootCatalog,
		private val bookFindings: BinderyCatalog = BinderyCatalog(title = "Findings"),
		private val resourceBytes: ByteArray = ByteArray(0),
		private val progress: BinderyReadingProgress = BinderyReadingProgress(
			bookId = "book",
			kind = BinderyReadingProgressKind.Ebook
		),
		private val rootFailure: Throwable? = null,
		private val catalogFailure: Throwable? = null,
		private val bookFindingsFailure: Throwable? = null
	) : BinderyApiClient {
		var rootCalls = 0
		val rootBaseUrls = mutableListOf<String>()
		val rootHeaders = mutableListOf<Map<String, String>>()
		val actionBaseUrls = mutableListOf<String>()
		val actionHeaders = mutableListOf<Map<String, String>>()
		val actionPaths = mutableListOf<String>()
		val catalogBaseUrls = mutableListOf<String>()
		val catalogHeaders = mutableListOf<Map<String, String>>()
		val catalogPaths = mutableListOf<String>()
		val bookFindingBaseUrls = mutableListOf<String>()
		val bookFindingHeaders = mutableListOf<Map<String, String>>()
		val bookFindingIds = mutableListOf<String>()
		val resourceBaseUrls = mutableListOf<String>()
		val resourceHeaders = mutableListOf<Map<String, String>>()
		val resourcePaths = mutableListOf<String>()
		val progressFetchBaseUrls = mutableListOf<String>()
		val progressFetchHeaders = mutableListOf<Map<String, String>>()
		val progressFetchBookIds = mutableListOf<String>()
		val progressFetchAliases = mutableListOf<String?>()
		val progressPutBaseUrls = mutableListOf<String>()
		val progressPutHeaders = mutableListOf<Map<String, String>>()
		val progressPutPayloads = mutableListOf<BinderyReadingProgress>()

		override suspend fun fetchRootCatalog(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): BinderyCatalog {
			rootCalls += 1
			rootBaseUrls += baseUrl
			rootHeaders += requestHeaders
			rootFailure?.let { throw it }
			return rootCatalog
		}

		override suspend fun fetchCatalog(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			path: String
		): BinderyCatalog {
			catalogBaseUrls += baseUrl
			catalogHeaders += requestHeaders
			catalogPaths += path
			catalogFailure?.let { throw it }
			return catalog
		}

		override suspend fun fetchManifest(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			bookId: String
		): BinderyManifest = BinderyManifest(
			id = "urn:bindery:book:$bookId",
			title = "Book $bookId"
		)

		override suspend fun fetchBookResources(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			bookId: String
		): BinderyResourceCatalog = BinderyResourceCatalog(title = "Book $bookId Resources")

		override suspend fun fetchResourceBytes(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			path: String
		): ByteArray {
			resourceBaseUrls += baseUrl
			resourceHeaders += requestHeaders
			resourcePaths += path
			return resourceBytes
		}

		override suspend fun fetchReadingProgress(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			bookId: String,
			alias: String?
		): BinderyReadingProgress {
			progressFetchBaseUrls += baseUrl
			progressFetchHeaders += requestHeaders
			progressFetchBookIds += bookId
			progressFetchAliases += alias
			return progress
		}

		override suspend fun putReadingProgress(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			progress: BinderyReadingProgress
		) {
			progressPutBaseUrls += baseUrl
			progressPutHeaders += requestHeaders
			progressPutPayloads += progress
		}

		override suspend fun fetchBookFindings(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			bookId: String
		): BinderyCatalog {
			bookFindingBaseUrls += baseUrl
			bookFindingHeaders += requestHeaders
			bookFindingIds += bookId
			bookFindingsFailure?.let { throw it }
			return bookFindings
		}

		override suspend fun performAction(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			path: String
		) {
			actionBaseUrls += baseUrl
			actionHeaders += requestHeaders
			actionPaths += path
		}
	}

	private class RecordingBinderyMetadataCache : BinderyMetadataCache {
		val records = linkedMapOf<String, BinderyMetadataCacheRecord>()
		val clearedBaseUrls = mutableListOf<String>()

		override suspend fun get(cacheKey: String): BinderyMetadataCacheRecord? =
			records[cacheKey]

		override suspend fun put(record: BinderyMetadataCacheRecord) {
			records[record.cacheKey] = record
		}

		override suspend fun clearBaseUrl(baseUrl: String) {
			clearedBaseUrls += baseUrl
			records.entries.removeAll { (_, record) -> record.baseUrl == baseUrl }
		}
	}

	private fun configuredBinderyRepository(
		apiClient: BinderyApiClient,
		metadataCache: BinderyMetadataCache,
		currentTimeMillis: () -> Long
	): BinderyRepository {
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		return BinderyRepository(
			preferenceManager = preferences,
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = currentTimeMillis
		)
	}

	private fun binderyRootCatalog(): BinderyCatalog =
		BinderyCatalog(
			title = "Bindery",
			links = listOf(
				BinderyLink(
					href = "/opds/search{?q}",
					rel = listOf("search"),
					type = "application/opds+json"
				)
			),
			navigation = listOf(
				BinderyLink(href = "/opds/books", title = "Books"),
				BinderyLink(href = "/opds/formats/audiobook", title = "Audiobooks"),
				BinderyLink(href = "/opds/authors", title = "Authors"),
				BinderyLink(href = "/opds/series", title = "Series"),
				BinderyLink(href = "/opds/collections", title = "Collections"),
				BinderyLink(href = "/opds/findings", title = "Findings")
			)
		)
}
