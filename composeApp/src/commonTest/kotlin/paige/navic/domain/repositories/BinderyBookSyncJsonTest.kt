package paige.navic.domain.repositories

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class BinderyBookSyncJsonTest {
	@Test
	fun decodesDirectBookSyncPayload() {
		val sync = decodeBinderyBookSyncJson(
			"""
			{
			  "bookId": 3809,
			  "whispersyncStatus": "ready",
			  "syncPairCounts": { "ready": 1 },
			  "syncPairs": [
			    {
			      "bookId": 3809,
			      "ebookBookFileId": 426,
			      "audiobookBookFileId": 633,
			      "whispersync": {
			        "status": "ready",
			        "artifactId": 3,
			        "artifactHref": "/opds/books/3809/sync/3",
			        "score": 0.91,
			        "coverage": 0.87
			      }
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals(3809, sync.bookId)
		assertEquals("ready", sync.whispersyncStatus)
		assertEquals(1, sync.syncPairCounts["ready"])
		assertEquals(1, sync.syncPairs.size)
		assertEquals(426, sync.syncPairs.single().ebookBookFileId)
		assertEquals(633, sync.syncPairs.single().audiobookBookFileId)
		assertEquals(3, sync.syncPairs.single().whispersync?.artifactId)
	}

	@Test
	fun decodesOpdsCatalogPropertiesBookSyncPayloadFromLiveBinderyShape() {
		val sync = decodeBinderyBookSyncJson(
			"""
			{
			  "@context": "https://readium.org/webpub-manifest/context.jsonld",
			  "metadata": {
			    "title": "Whispersync pairs for Bastille vs. the Evil Librarians"
			  },
			  "links": [],
			  "publications": [
			    {
			      "metadata": {
			        "title": "Whispersync pair 426 / 633",
			        "identifier": "3"
			      },
			      "links": [
			        {
			          "rel": "alternate",
			          "type": "application/json; charset=utf-8",
			          "href": "/opds/books/3809/sync/3"
			        }
			      ],
			      "properties": {
			        "bookId": 3809,
			        "ebookBookFileId": 426,
			        "audiobookBookFileId": 633,
			        "whispersync": {
			          "status": "ready",
			          "artifactId": 3,
			          "artifactHref": "/opds/books/3809/sync/3",
			          "score": 0.91,
			          "coverage": 0.87
			        }
			      }
			    }
			  ],
			  "properties": {
			    "whispersyncStatus": "ready",
			    "syncPairCounts": { "ready": 1 },
			    "syncPairs": [
			      {
			        "bookId": 3809,
			        "ebookBookFileId": 426,
			        "audiobookBookFileId": 633,
			        "whispersync": {
			          "status": "ready",
			          "artifactId": 3,
			          "artifactHref": "/opds/books/3809/sync/3",
			          "score": 0.91,
			          "coverage": 0.87
			        }
			      }
			    ]
			  }
			}
			""".trimIndent()
		)

		assertEquals("ready", sync.whispersyncStatus)
		assertEquals(1, sync.syncPairCounts["ready"])
		assertEquals(1, sync.syncPairs.size)
		assertEquals(3809, sync.syncPairs.single().bookId)
		assertEquals(426, sync.syncPairs.single().ebookBookFileId)
		assertEquals(633, sync.syncPairs.single().audiobookBookFileId)
		assertEquals("ready", sync.syncPairs.single().whispersync?.status)
		assertEquals("/opds/books/3809/sync/3", sync.syncPairs.single().whispersync?.artifactHref)
	}

	@Test
	fun decodesAudiobookDetailWhispersyncSummaryFields() {
		val detail = BinderyJson.decodeFromString<BinderyAudiobookVersion>(
			"""
			{
			  "id": 44,
			  "bookId": 3809,
			  "bookFileId": 426,
			  "title": "Bastille vs. the Evil Librarians",
			  "whispersyncAvailable": true,
			  "whispersyncReadyCount": 1,
			  "whispersyncStatus": "ready",
			  "whispersync": [
			    {
			      "bookId": 3809,
			      "ebookBookFileId": 633,
			      "audiobookBookFileId": 426,
			      "whispersync": {
			        "status": "ready",
			        "artifactId": 12,
			        "artifactHref": "/opds/books/3809/sync/12"
			      }
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals(true, detail.whispersyncAvailable)
		assertEquals(1, detail.whispersyncReadyCount)
		assertEquals("ready", detail.whispersyncStatus)
		assertEquals(633, detail.whispersync.single().ebookBookFileId)
		assertEquals("/opds/books/3809/sync/12", detail.whispersync.single().whispersync?.artifactHref)
	}

	@Test
	fun decodesAudiobookDetailProviderProvenanceFieldsFromNavicApiSchema() {
		val detail = BinderyJson.decodeFromString<BinderyAudiobookVersion>(
			"""
			{
			  "id": 7,
			  "bookId": 3816,
			  "bookFileId": 694,
			  "title": "The Hobbit",
			  "studio": "Audible Studios",
			  "provenance": {
			    "provider": "AudioBook Bay",
			    "providerKind": "audiobookbay",
			    "providerTitle": "The Hobbit - J. R. R. Tolkien Audiobook MP3 [Unabridged]",
			    "providerSourceUrl": "https://audiobookbay.lu/abss/the-hobbit/",
			    "mappingStatus": "selected",
			    "metadataProvider": "audible",
			    "metadataConfidence": "high",
			    "metadataConfidenceScore": 95,
			    "metadataConfidenceReason": "title/narrator/duration match"
			  }
			}
			""".trimIndent()
		)

		assertEquals("Audible Studios", detail.studio)
		assertEquals("audiobookbay", detail.provenance?.providerKind)
		assertEquals("The Hobbit - J. R. R. Tolkien Audiobook MP3 [Unabridged]", detail.provenance?.providerTitle)
		assertEquals("selected", detail.provenance?.mappingStatus)
		assertEquals("audible", detail.provenance?.metadataProvider)
		assertEquals("high", detail.provenance?.metadataConfidence)
		assertEquals(95, detail.provenance?.metadataConfidenceScore)
		assertEquals("title/narrator/duration match", detail.provenance?.metadataConfidenceReason)
	}

	@Test
	fun readyWhispersyncPairOnlyRequiresReadyStatusAndArtifactHref() {
		val pair = BinderyJson.decodeFromString<BinderySyncPair>(
			"""
			{
			  "bookId": 3816,
			  "ebookBookFileId": 435,
			  "audiobookBookFileId": 694,
			  "whispersync": {
			    "status": "ready",
			    "artifactHref": "/opds/books/3816/sync/12"
			  }
			}
			""".trimIndent()
		)

		assertEquals(true, pair.hasReadyWhispersyncArtifact())
	}

	@Test
	fun decodesWhispersyncLastJobStateAndFractionalProgressFromNavicApiSchema() {
		val sync = decodeBinderyBookSyncJson(
			"""
			{
			  "bookId": 3816,
			  "whispersyncStatus": "pending",
			  "syncPairs": [
			    {
			      "bookId": 3816,
			      "ebookBookFileId": 435,
			      "audiobookBookFileId": 694,
			      "whispersync": {
			        "status": "pending",
			        "artifactHref": "",
			        "lastJob": {
			          "id": 21,
			          "state": "active",
			          "status": "running",
			          "phase": "transcribing",
			          "progressPercent": 23.5,
			          "message": "Transcribed chunk 13 of 76 for Whispersync",
			          "updatedAt": "2026-06-27T10:05:00Z"
			        }
			      }
			    }
			  ]
			}
			""".trimIndent()
		)

		val lastJob = sync.syncPairs.single().whispersync?.lastJob
		assertEquals("active", lastJob?.state)
		assertEquals("running", lastJob?.status)
		assertEquals(23.5, lastJob?.progressPercent)
		assertEquals("2026-06-27T10:05:00Z", lastJob?.updatedAt)
	}
}
