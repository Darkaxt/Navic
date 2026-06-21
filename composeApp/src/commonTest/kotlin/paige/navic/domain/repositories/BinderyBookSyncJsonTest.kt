package paige.navic.domain.repositories

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
}
