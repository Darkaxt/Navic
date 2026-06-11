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

class BinderyRepositoryResourceJsonTest {
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

}
