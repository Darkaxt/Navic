package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = true
			binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
			binderyApiKey = " secret "
		}
		val repository = BinderyRepository(preferences, apiClient)

		repository.performAction("/opds/discover/authors/hc%3Apeter-sanderson/monitor").getOrThrow()

		assertEquals(listOf("https://bindery.example.com/opds"), apiClient.actionBaseUrls)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), apiClient.actionHeaders)
		assertEquals(listOf("/opds/discover/authors/hc%3Apeter-sanderson/monitor"), apiClient.actionPaths)
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
			      "ownedFormats": ["audiobook", "ebook"],
			      "ownedLanguages": ["eng"],
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
				ownedFormats = listOf("audiobook", "ebook"),
				ownedLanguages = listOf("eng"),
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
			            "selectedBytes": 27151009
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
				)
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
				)
			),
			catalog.resources.first()
		)
		assertEquals("audio", catalog.resources[1].kind)
		assertEquals(3763.592, catalog.resources[1].durationSeconds)
		assertEquals(120973860, catalog.resources[1].sizeBytes)
	}

	private class FakeBinderyApiClient(
		private val rootCatalog: BinderyCatalog = BinderyCatalog(title = "Bindery"),
		private val bookFindings: BinderyCatalog = BinderyCatalog(title = "Findings"),
		private val rootFailure: Throwable? = null
	) : BinderyApiClient {
		var rootCalls = 0
		val rootBaseUrls = mutableListOf<String>()
		val rootHeaders = mutableListOf<Map<String, String>>()
		val actionBaseUrls = mutableListOf<String>()
		val actionHeaders = mutableListOf<Map<String, String>>()
		val actionPaths = mutableListOf<String>()
		val bookFindingBaseUrls = mutableListOf<String>()
		val bookFindingHeaders = mutableListOf<Map<String, String>>()
		val bookFindingIds = mutableListOf<String>()

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
		): BinderyCatalog = rootCatalog

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

		override suspend fun fetchBookFindings(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			bookId: String
		): BinderyCatalog {
			bookFindingBaseUrls += baseUrl
			bookFindingHeaders += requestHeaders
			bookFindingIds += bookId
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
