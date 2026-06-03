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
				navigationCount = 5,
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
				navigationCount = 5,
				hasSearch = true,
				hasAudiobooks = true,
				hasAuthors = true,
				hasSeries = true,
				hasCollections = true,
				progressSyncSupported = false,
				paginationSupported = false
			),
			repository.getServiceStatus().getOrThrow()
		)
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
			    "yearRange": {"start": 2009, "end": 2022}
			  },
			  "navigation": [
			    {
			      "href": "/opds/authors/28/collections",
			      "title": "Collections",
			      "properties": {
			        "memberCount": 9,
			        "yearRange": {"start": 2005, "end": 2023}
			      }
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
			        "collectionTitle": "Mistborn"
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
		assertEquals("9", catalog.navigation.single().properties["memberCount"])
		assertNull(catalog.navigation.single().properties["yearRange"])
		assertEquals("2001-01-01", catalog.publications.single().published)
		assertEquals("Book description", catalog.publications.single().description)
		assertEquals(listOf("series:Mistborn", "Fantasy"), catalog.publications.single().subjects)
		assertEquals(113136.178, catalog.publications.single().durationSeconds)
		assertEquals("1", catalog.publications.single().properties["collectionPosition"])
		assertEquals("1", catalog.publications.single().properties["collectionPositionSort"])
		assertEquals("Mistborn", catalog.publications.single().properties["collectionTitle"])
	}

	private class FakeBinderyApiClient(
		private val rootCatalog: BinderyCatalog = BinderyCatalog(title = "Bindery"),
		private val rootFailure: Throwable? = null
	) : BinderyApiClient {
		var rootCalls = 0
		val rootBaseUrls = mutableListOf<String>()
		val rootHeaders = mutableListOf<Map<String, String>>()

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
				BinderyLink(href = "/opds/collections", title = "Collections")
			)
		)
}
