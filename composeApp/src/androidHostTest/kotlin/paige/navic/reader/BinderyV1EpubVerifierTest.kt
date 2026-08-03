package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BinderyV1EpubVerifierTest {
	@Test
	fun verifiesBinderyExtractionOrderAndShardTokenBoundaries() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val index = BinderyV1EpubTestFixtures.index()

		val session = BinderyV1EpubVerifier().verify(publication, index)
		val verifiedChapter = session.verifyChapter(BinderyV1EpubTestFixtures.chapter())

		assertTrue(session.provenance.coordinateBasis == index.coordinateBasis)
		assertTrue(
			session.provenance.chapters.map { chapter ->
				Triple(chapter.ebookHref, chapter.spineIndex, chapter.extractedByteLength)
			} == listOf(
				Triple(BinderyV1EpubTestFixtures.ChapterHref, 1, BinderyV1EpubTestFixtures.ExtractedByteLength),
				Triple(BinderyV1EpubTestFixtures.ChapterHref, 3, BinderyV1EpubTestFixtures.ExtractedByteLength)
			)
		)
		assertTrue(
			session.provenance.chapters.all { chapter ->
				chapter.tokenCount == BinderyV1EpubTestFixtures.ExtractedTokenCount &&
					chapter.sourceHash == BinderyV1EpubTestFixtures.SourceHash &&
					chapter.extractedTextHash == BinderyV1EpubTestFixtures.ExtractedTextHash
			}
		)
		assertEquals(BinderyV1EpubTestFixtures.ChapterKey, verifiedChapter.chapterKey)
		assertEquals(3, verifiedChapter.wordCount)
		assertEquals(1, verifiedChapter.unmatchedEbookWordCount)
	}

	@Test
	fun rejectsNonCanonicalAggregateHash() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val uppercaseHexHash = "sha256:" + BinderyV1EpubTestFixtures.AggregateHash
			.substringAfter("sha256:")
			.uppercase()

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			BinderyV1EpubVerifier().verify(
				publication,
				BinderyV1EpubTestFixtures.index(hash = uppercaseHexHash)
			)
		}

		assertEquals(WordSyncPublicationVerificationFailure.IndexMismatch, error.failure)
	}

	@Test
	fun rejectsIndexHrefOrSpineThatDoesNotExactlyMatchPublication() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val mismatchedSummaries = listOf(
			BinderyV1EpubTestFixtures.summary(ebookHref = "ops/text/chapter.xhtml"),
			BinderyV1EpubTestFixtures.summary(spineIndex = 2)
		)

		mismatchedSummaries.forEach { summary ->
			val error = assertFailsWith<WordSyncPublicationVerificationException> {
				BinderyV1EpubVerifier().verify(
					publication,
					BinderyV1EpubTestFixtures.index(chapters = listOf(summary))
				)
			}
			assertEquals(WordSyncPublicationVerificationFailure.IndexMismatch, error.failure)
		}
	}

	@Test
	fun rejectsIndexSummaryOutsideExtractedUtf8ByteLength() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val summary = BinderyV1EpubTestFixtures.summary(
			ebookEnd = BinderyV1EpubTestFixtures.ExtractedByteLength + 1
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			BinderyV1EpubVerifier().verify(
				publication,
				BinderyV1EpubTestFixtures.index(chapters = listOf(summary))
			)
		}

		assertEquals(WordSyncPublicationVerificationFailure.IndexMismatch, error.failure)
	}

	@Test
	fun rejectsShardWordBoundaryInsideUtf8CodePoint() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val session = BinderyV1EpubVerifier().verify(publication, BinderyV1EpubTestFixtures.index())
		val words = BinderyV1EpubTestFixtures.validWords.toMutableList().apply {
			this[0] = first().copy(ebookStart = 4, ebookEnd = 5)
		}

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			session.verifyChapter(BinderyV1EpubTestFixtures.chapter(words = words))
		}

		assertEquals(WordSyncPublicationVerificationFailure.ShardMismatch, error.failure)
	}

	@Test
	fun rejectsShardWordThatCoversOnlyPartOfToken() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val session = BinderyV1EpubVerifier().verify(publication, BinderyV1EpubTestFixtures.index())
		val words = BinderyV1EpubTestFixtures.validWords.toMutableList().apply {
			this[1] = this[1].copy(ebookStart = 14, ebookEnd = 20)
		}

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			session.verifyChapter(BinderyV1EpubTestFixtures.chapter(words = words))
		}

		assertEquals(WordSyncPublicationVerificationFailure.ShardMismatch, error.failure)
	}

	@Test
	fun rejectsUnmatchedEbookRangeThatCoversOnlyPartOfToken() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val session = BinderyV1EpubVerifier().verify(publication, BinderyV1EpubTestFixtures.index())
		val unmatched = listOf(
			WordSyncUnmatchedEbook(ebookStart = 21, ebookLen = 3, reason = "not-linked")
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			session.verifyChapter(BinderyV1EpubTestFixtures.chapter(unmatchedEbook = unmatched))
		}

		assertEquals(WordSyncPublicationVerificationFailure.ShardMismatch, error.failure)
	}
}
