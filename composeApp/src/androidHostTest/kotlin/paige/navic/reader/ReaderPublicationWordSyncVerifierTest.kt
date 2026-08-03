package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReaderPublicationWordSyncVerifierTest {
	@Test
	fun managedEpubExposesExactBinderyVerifier() {
		val publication = BinderyV1EpubTestFixtures.publication()
		val verifier = androidWordSyncPublicationVerifierOrNull(
			publicationFile = publication,
			format = ReaderPublicationFormat.Epub
		)

		val session = assertNotNull(verifier).verify(BinderyV1EpubTestFixtures.index())

		assertEquals(WordSyncCoordinateBasis(
			extractor = "bindery-epub-text",
			extractorVersion = "1",
			normalization = "raw-extracted-text-offsets",
			ebookTextHash = BinderyV1EpubTestFixtures.AggregateHash
		), session.provenance.coordinateBasis)
		assertEquals(BinderyV1EpubTestFixtures.ChapterHref, session.provenance.chapters[1].ebookHref)
	}

	@Test
	fun nonEpubManagedPublicationsDoNotExposeVerifier() {
		val publication = BinderyV1EpubTestFixtures.publication()

		ReaderPublicationFormat.entries
			.filterNot { it == ReaderPublicationFormat.Epub }
			.forEach { format ->
				assertNull(androidWordSyncPublicationVerifierOrNull(publication, format))
			}
	}

	@Test
	fun platformRuntimeHooksFailClosedWithoutManagedAndroidEpubBytes() {
		val androidHost = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()
		val iosHost = repoFile(
			"composeApp/src/iosMain/kotlin/paige/navic/ui/screens/reader/ReaderPublicationRuntimeHost.ios.kt"
		).readText()

		assertContains(
			androidHost,
			"currentOnPublicationReady(directUrl, preferredShellCoverUrl, null, savedProgress, null)"
		)
		assertContains(androidHost, "androidWordSyncPublicationVerifierOrNull(")
		assertContains(androidHost, "publicationFile = resolved.publicationFile")
		assertContains(androidHost, "format = reader.publicationFormat")
		assertContains(iosHost, "onPublicationReady(reader.publicationUrl, null, null, null, null)")
	}
}
