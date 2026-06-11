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

class BinderyRepositoryCatalogJsonTest {
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
	fun catalogJsonPreservesFindingFileDisplayFieldsAndSuppressesUnknownSizes() {
		val catalog = decodeBinderyCatalogJson(
			"""
			{
			  "metadata": {"title": "Findings"},
			  "publications": [
			    {
			      "metadata": {
			        "title": "Wheel of Time 12 - The Gathering Storm",
			        "identifier": "urn:bindery:finding:19"
			      },
			      "properties": {
			        "findingId": 19,
			        "mediaType": "audiobook",
			        "format": "mp3",
			        "files": [
			          {
			            "displayName": "Robert Jordan The Gathering Storm - 01 - Foreword.mp3",
			            "path": "Wheel of Time/Robert Jordan The Gathering Storm - 01 - Foreword.mp3",
			            "extension": "mp3",
			            "sizeBytes": 0,
			            "durationMs": 123000,
			            "bitrateBps": 128000,
			            "sampleRateHz": 44100
			          },
			          {}
			        ]
			      },
			      "links": [
			        {"href": "/opds/findings/19", "rel": "self", "type": "application/opds-publication+json"}
			      ]
			    }
			  ]
			}
			""".trimIndent()
		)

		val file = catalog.publications.single().finding!!.files.single()
		assertEquals("Robert Jordan The Gathering Storm - 01 - Foreword.mp3", file.name)
		assertEquals("mp3", file.format)
		assertNull(file.sizeBytes)
		assertEquals(123.0, file.durationSeconds)
		assertEquals(128000L, file.bitrateBps)
		assertEquals(44100L, file.sampleRateHz)
	}

}
