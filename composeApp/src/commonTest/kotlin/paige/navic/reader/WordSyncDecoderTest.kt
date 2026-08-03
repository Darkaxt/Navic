package paige.navic.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WordSyncDecoderTest {
	private val identity = WordSyncTestFixtures.identity()

	@Test
	fun decodesStrictIndexAndPreservesChapterDiscovery() {
		val index = decodeWordSyncIndex(
			json = WordSyncTestFixtures.indexJson(),
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
		val index = decodeWordSyncIndex(WordSyncTestFixtures.indexJson(), identity)
		val chapter = decodeWordSyncChapter(
			json = WordSyncTestFixtures.chapterJson(),
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
		val indexJson = WordSyncTestFixtures.indexJson().withoutFields(
			"artifactId",
			"statusEnum",
			"methodEnum"
		)
		val index = decodeWordSyncIndex(indexJson, identity)
		val chapter = decodeWordSyncChapter(
			json = WordSyncTestFixtures.chapterJson().withoutFields("artifactId"),
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
			WordSyncTestFixtures.indexJson().replace("\"endMs\": 2300", "\"endMs\": 2000"),
			identity
		)
		val chapter = decodeWordSyncChapter(
			json = WordSyncTestFixtures.chapterJson().replace("\"audioDurMs\": [300]", "\"audioDurMs\": [0]"),
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
		val index = decodeWordSyncIndex(WordSyncTestFixtures.indexJson(), identity)
		val overlappingJson = WordSyncTestFixtures.chapterJson()
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
				WordSyncTestFixtures.indexJson().replace(
					"bindery.whispersync.wordsync.index.v1",
					"bindery.whispersync.wordsync.index.v2"
				),
				identity
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncIndex(
				WordSyncTestFixtures.indexJson().replace("\"artifactId\": 17", "\"artifactId\": 18"),
				identity
			)
		}
		val index = decodeWordSyncIndex(WordSyncTestFixtures.indexJson(), identity)
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				WordSyncTestFixtures.chapterJson().replace(
					"bindery.whispersync.wordsync.chapter.v1",
					"bindery.whispersync.wordsync.chapter.v2"
				),
				identity,
				index.chapters.single()
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				WordSyncTestFixtures.chapterJson().replace("\"chapterKey\": \"spine-002-chapter\"", "\"chapterKey\": \"other\""),
				identity,
				index.chapters.single()
			)
		}
	}

	@Test
	fun rejectsMalformedParallelArraysAndInconsistentLookup() {
		val index = decodeWordSyncIndex(WordSyncTestFixtures.indexJson(), identity)
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				WordSyncTestFixtures.chapterJson().replace("\"audioDurMs\": [200, 220]", "\"audioDurMs\": [200]"),
				identity,
				index.chapters.single()
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				WordSyncTestFixtures.chapterJson().replace("\"wordIndex\": [0, 1, 0]", "\"wordIndex\": [0, 0, 0]"),
				identity,
				index.chapters.single()
			)
		}
	}

	@Test
	fun rejectsInvalidCoordinateBasisEnumsAndRanges() {
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncIndex(
				WordSyncTestFixtures.indexJson().replace("raw-extracted-text-offsets", "dom-utf16-offsets"),
				identity
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncIndex(
				WordSyncTestFixtures.indexJson().replace("\"3\": \"fuzzy\"", "\"3\": \"approximate\""),
				identity
			)
		}
		val index = decodeWordSyncIndex(WordSyncTestFixtures.indexJson(), identity)
		assertFailsWith<IllegalArgumentException> {
			decodeWordSyncChapter(
				WordSyncTestFixtures.chapterJson().replace("\"confidence\": [98, 97]", "\"confidence\": [98, 101]"),
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

}
