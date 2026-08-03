package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import paige.navic.domain.repositories.BinderyWhispersyncIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WordSyncDecoderTest {
	private val identity = BinderyWhispersyncIdentity(
		bookId = 7,
		ebookBookFileId = 11,
		audiobookBookFileId = 13,
		artifactId = 17
	)

	@Test
	fun decodesStrictIndexAndPreservesChapterDiscovery() {
		val index = decodeWordSyncIndex(
			json = validIndexJson(),
			expectedIdentity = identity
		)

		assertEquals(identity, index.identity)
		assertEquals(
			WordSyncCoordinateBasis(
				extractor = "bindery-epub-text",
				extractorVersion = "1",
				normalization = "raw-extracted-text-offsets",
				ebookTextHash = "sha256:${"a".repeat(64)}"
			),
			index.coordinateBasis
		)
		val chapter = index.chapters.single()
		assertEquals("spine-002-chapter", chapter.chapterKey)
		assertEquals(2, chapter.spineIndex)
		assertEquals(100, chapter.ebookStart)
		assertEquals(109, chapter.ebookEnd)
		assertEquals(2, chapter.audioRanges.size)
		assertEquals(3, chapter.audioWordCount)
	}

	@Test
	fun decodesMultiTrackShardWithCumulativeAudioAndChapterRelativeEbookDeltas() {
		val index = decodeWordSyncIndex(validIndexJson(), identity)
		val chapter = decodeWordSyncChapter(
			json = validChapterJson(),
			expectedIdentity = identity,
			expectedChapter = index.chapters.single()
		)

		assertEquals(1_000, chapter.tracks[0].word(0).audioStartMs)
		assertEquals(1_300, chapter.tracks[0].word(1).audioStartMs)
		assertEquals(103, chapter.tracks[0].word(1).ebookStart)
		assertEquals(105, chapter.tracks[1].word(0).ebookStart)

		val audioWord = assertNotNull(
			chapter.wordAtAudioPosition(
				audioResourceId = "audio-a",
				audioTrackIndex = 0,
				positionMs = 1_300
			)
		)
		assertEquals(103, audioWord.ebookStart)
		assertNull(
			chapter.wordAtAudioPosition(
				audioResourceId = "audio-a",
				audioTrackIndex = 0,
				positionMs = 1_520
			)
		)
		assertEquals(2_000, assertNotNull(chapter.wordAtEbookOffset(105)).audioStartMs)
		assertNull(chapter.wordAtEbookOffset(109))
	}

	@Test
	fun acceptsProtocolOptionalArtifactIdentityAndEnumMaps() {
		val indexJson = validIndexJson().withoutFields(
			"artifactId",
			"statusEnum",
			"methodEnum"
		)
		val index = decodeWordSyncIndex(indexJson, identity)
		val chapter = decodeWordSyncChapter(
			json = validChapterJson().withoutFields("artifactId"),
			expectedIdentity = identity,
			expectedChapter = index.chapters.single()
		)

		assertEquals(identity, index.identity)
		assertEquals(identity, chapter.identity)
		assertEquals("exact", index.statusEnum[1])
		assertEquals("asr-word-timestamp", index.methodEnum[0])
	}

	@Test
	fun acceptsZeroDurationAudioWordsWithoutCreatingAnAudioInterval() {
		val index = decodeWordSyncIndex(
			validIndexJson().replace("\"endMs\": 2300", "\"endMs\": 2000"),
			identity
		)
		val chapter = decodeWordSyncChapter(
			json = validChapterJson().replace("\"audioDurMs\": [300]", "\"audioDurMs\": [0]"),
			expectedIdentity = identity,
			expectedChapter = index.chapters.single()
		)

		assertEquals(2_000, assertNotNull(chapter.wordAtEbookOffset(105)).audioStartMs)
		assertNull(
			chapter.wordAtAudioPosition(
				audioResourceId = "audio-b",
				audioTrackIndex = 1,
				positionMs = 2_000
			)
		)
	}

	@Test
	fun overlappingEbookRangesResolveFirstPublishedLookupEntry() {
		val index = decodeWordSyncIndex(validIndexJson(), identity)
		val overlappingJson = validChapterJson()
			.replace("\"ebookStartDelta\": [0, 3]", "\"ebookStartDelta\": [0, 1]")
			.replace("\"ebookLen\": [2, 1]", "\"ebookLen\": [2, 3]")
			.replace("\"ebookStart\": [100, 103, 105]", "\"ebookStart\": [100, 101, 105]")
		val chapter = decodeWordSyncChapter(
			json = overlappingJson,
			expectedIdentity = identity,
			expectedChapter = index.chapters.single()
		)

		assertEquals(1_000, assertNotNull(chapter.wordAtEbookOffset(101)).audioStartMs)
	}

	@Test
	fun rejectsUnknownSchemasAndIdentityMismatches() {
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncIndex(
				validIndexJson().replace(
					"bindery.whispersync.wordsync.index.v1",
					"bindery.whispersync.wordsync.index.v2"
				),
				identity
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncIndex(
				validIndexJson().replace("\"artifactId\": 17", "\"artifactId\": 18"),
				identity
			)
		}
		val index = decodeWordSyncIndex(validIndexJson(), identity)
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				validChapterJson().replace(
					"bindery.whispersync.wordsync.chapter.v1",
					"bindery.whispersync.wordsync.chapter.v2"
				),
				identity,
				index.chapters.single()
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				validChapterJson().replace("\"chapterKey\": \"spine-002-chapter\"", "\"chapterKey\": \"other\""),
				identity,
				index.chapters.single()
			)
		}
	}

	@Test
	fun rejectsMalformedParallelArraysAndInconsistentLookup() {
		val index = decodeWordSyncIndex(validIndexJson(), identity)
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				validChapterJson().replace("\"audioDurMs\": [200, 220]", "\"audioDurMs\": [200]"),
				identity,
				index.chapters.single()
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				validChapterJson().replace("\"wordIndex\": [0, 1, 0]", "\"wordIndex\": [0, 0, 0]"),
				identity,
				index.chapters.single()
			)
		}
	}

	@Test
	fun rejectsInvalidCoordinateBasisEnumsAndRanges() {
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncIndex(
				validIndexJson().replace("raw-extracted-text-offsets", "dom-utf16-offsets"),
				identity
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncIndex(
				validIndexJson().replace("\"3\": \"fuzzy\"", "\"3\": \"approximate\""),
				identity
			)
		}
		val index = decodeWordSyncIndex(validIndexJson(), identity)
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				validChapterJson().replace("\"confidence\": [98, 97]", "\"confidence\": [98, 101]"),
				identity,
				index.chapters.single()
			)
		}
	}

	private fun String.withoutFields(vararg fields: String): String {
		val excluded = fields.toSet()
		return JsonObject(
			Json.parseToJsonElement(this).jsonObject.filterKeys { it !in excluded }
		).toString()
	}

	private fun validIndexJson(): String =
		"""
		{
		  "schema": "bindery.whispersync.wordsync.index.v1",
		  "version": 1,
		  "bookId": 7,
		  "ebookBookFileId": 11,
		  "audiobookBookFileId": 13,
		  "artifactId": 17,
		  "generatedAt": "2026-08-03T00:00:00Z",
		  "timeScale": 1000,
		  "coordinateBasis": {
		    "extractor": "bindery-epub-text",
		    "extractorVersion": "1",
		    "normalization": "raw-extracted-text-offsets",
		    "ebookTextHash": "sha256:${"a".repeat(64)}"
		  },
		  "statusEnum": {
		    "0": "unmatched-audio",
		    "1": "exact",
		    "2": "normalized",
		    "3": "fuzzy",
		    "4": "semantic-number",
		    "5": "review"
		  },
		  "methodEnum": {
		    "0": "asr-word-timestamp",
		    "1": "forced-align-cue-window",
		    "2": "cue-interpolated-review"
		  },
		  "chapters": [
		    {
		      "chapterKey": "spine-002-chapter",
		      "spineIndex": 2,
		      "ebookHref": "Text/chapter.xhtml",
		      "path": "spine-002-chapter.wsyncw",
		      "href": "/api/v1/sync/artifacts/17/wordsync/spine-002-chapter",
		      "opdsHref": "/opds/books/7/sync/17/wordsync/spine-002-chapter",
		      "ebookStart": 100,
		      "ebookEnd": 109,
		      "audioRanges": [
		        {
		          "audioResourceId": "audio-a",
		          "audioTrackIndex": 0,
		          "audioHref": "Audio/a.mp3",
		          "startMs": 1000,
		          "endMs": 1520
		        },
		        {
		          "audioResourceId": "audio-b",
		          "audioTrackIndex": 1,
		          "audioHref": "Audio/b.mp3",
		          "startMs": 2000,
		          "endMs": 2300
		        }
		      ],
		      "audioWordCount": 3,
		      "matchedAudioWordCount": 2,
		      "reviewAudioWordCount": 1,
		      "unmatchedAudioWordCount": 0,
		      "unmatchedEbookWordCount": 0,
		      "minConfidence": 95,
		      "meanConfidence": 97
		    }
		  ]
		}
		""".trimIndent()

	private fun validChapterJson(): String =
		"""
		{
		  "schema": "bindery.whispersync.wordsync.chapter.v1",
		  "version": 1,
		  "bookId": 7,
		  "ebookBookFileId": 11,
		  "audiobookBookFileId": 13,
		  "artifactId": 17,
		  "chapterKey": "spine-002-chapter",
		  "ebookHref": "Text/chapter.xhtml",
		  "spineIndex": 2,
		  "ebookStart": 100,
		  "ebookEnd": 109,
		  "timeScale": 1000,
		  "tracks": [
		    {
		      "audioResourceId": "audio-a",
		      "audioTrackIndex": 0,
		      "audioHref": "Audio/a.mp3",
		      "baseStartMs": 1000,
		      "audioStartDeltaMs": [0, 300],
		      "audioDurMs": [200, 220],
		      "ebookStartDelta": [0, 3],
		      "ebookLen": [2, 1],
		      "cueId": [1, 1],
		      "status": [1, 3],
		      "confidence": [98, 97],
		      "method": [0, 0],
		      "flags": [0, 0]
		    },
		    {
		      "audioResourceId": "audio-b",
		      "audioTrackIndex": 1,
		      "audioHref": "Audio/b.mp3",
		      "baseStartMs": 2000,
		      "audioStartDeltaMs": [0],
		      "audioDurMs": [300],
		      "ebookStartDelta": [5],
		      "ebookLen": [4],
		      "cueId": [2],
		      "status": [5],
		      "confidence": [95],
		      "method": [1],
		      "flags": [0]
		    }
		  ],
		  "ebookLookup": {
		    "ebookStart": [100, 103, 105],
		    "trackIndex": [0, 0, 1],
		    "wordIndex": [0, 1, 0]
		  },
		  "unmatchedEbook": []
		}
		""".trimIndent()
}
