package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinderyV1EpubVerifierFailureTest {
	@Test
	fun usesFirstRootfileToken() {
		val container = """
			<container>
				<rootfile full-path="OPS/missing.opf"/>
				<rootfile full-path="OPS/package.opf"/>
			</container>
		""".trimIndent()
		val publication = BinderyV1EpubTestFixtures.publication(container = container)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			BinderyV1EpubVerifier().verify(publication, BinderyV1EpubTestFixtures.index())
		}

		assertEquals(WordSyncPublicationVerificationFailure.InvalidArchive, error.failure)
	}

	@Test
	fun doesNotUseFuzzyOrCaseInsensitiveArchivePaths() {
		val opf = """
			<package>
				<item id="chapter" href="Text/chapter.xhtml" media-type="application/xhtml+xml"/>
				<itemref idref="chapter"/>
			</package>
		""".trimIndent()
		val publication = BinderyV1EpubTestFixtures.publication(
			opf = opf,
			chapterEntry = "OPS/text/chapter.xhtml"
		)

		val session = BinderyV1EpubVerifier().verify(
			publication,
			BinderyV1EpubTestFixtures.index(
				hash = BinderyV1EpubTestFixtures.EmptyAggregateHash,
				chapters = emptyList()
			)
		)

		assertTrue(session.provenance.chapters.isEmpty())
	}

	@Test
	fun keepsOpfTokensReadBeforeMalformedTail() {
		val malformedOpf = """
			<package>
				<item id="chapter" href="Text/chapter.xhtml" media-type="application/xhtml+xml"/>
				<itemref idref="chapter"/>
				<broken>
		""".trimIndent()
		val publication = BinderyV1EpubTestFixtures.publication(opf = malformedOpf)

		val session = BinderyV1EpubVerifier().verify(
			publication,
			BinderyV1EpubTestFixtures.index(
				hash = "sha256:43d1a03ad357693036294f284f4f9aa41624517c08a781cf46a352f7d2f82d56",
				chapters = emptyList()
			)
		)

		assertEquals(1, session.provenance.chapters.size)
	}

	@Test
	fun rejectsArchiveEntryCountAboveLimit() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val verifier = BinderyV1EpubVerifier(
			limits = BinderyV1EpubVerificationLimits(maxArchiveEntryCount = 4)
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			verifier.verify(publication, BinderyV1EpubTestFixtures.index())
		}

		assertEquals(WordSyncPublicationVerificationFailure.ResourceLimit, error.failure)
	}

	@Test
	fun rejectsReadableZipEntryAboveDecompressionLimit() {
		val publication = BinderyV1EpubTestFixtures.publication(
			chapterBody = "<p>${"word ".repeat(1_024)}</p>"
		)
		val verifier = BinderyV1EpubVerifier(
			limits = BinderyV1EpubVerificationLimits(maxContentEntryBytes = 128)
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			verifier.verify(publication, BinderyV1EpubTestFixtures.index())
		}

		assertEquals(WordSyncPublicationVerificationFailure.ResourceLimit, error.failure)
	}

	@Test
	fun rejectsRepeatedSpineTextAboveAggregateExtractionLimit() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val verifier = BinderyV1EpubVerifier(
			limits = BinderyV1EpubVerificationLimits(maxTotalExtractedTextBytes = 64)
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			verifier.verify(publication, BinderyV1EpubTestFixtures.index())
		}

		assertEquals(WordSyncPublicationVerificationFailure.ResourceLimit, error.failure)
	}

	@Test
	fun failClosedExceptionDoesNotExposePrivatePublicationValues() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			BinderyV1EpubVerifier().verify(
				publication,
				BinderyV1EpubTestFixtures.index(hash = BinderyV1EpubTestFixtures.EmptyAggregateHash)
			)
		}

		assertEquals("Bindery WordSync publication verification failed.", error.message)
		assertNull(error.cause)
	}
}
