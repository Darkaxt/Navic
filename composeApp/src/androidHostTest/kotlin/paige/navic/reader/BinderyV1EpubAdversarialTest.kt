package paige.navic.reader

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BinderyV1EpubAdversarialTest {
	@Test
	fun rejectsDoctypeCaseInsensitivelyBeforeSax() {
		val declarations = listOf(
			"""<!DOCTYPE package [
				<!ENTITY xxe SYSTEM "file:///synthetic/never-read">
				<!ENTITY a "1234567890">
				<!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">
				<!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;">
			]>""".trimIndent(),
			"<!DoCtYpE package>"
		)

		declarations.forEach { declaration ->
			val publication = publication(
				DefaultContainerPath to DefaultContainer,
				DefaultOpfPath to "$declaration<package/>".encodeToByteArray()
			)
			val error = assertFailsWith<WordSyncPublicationVerificationException> {
				BinderyV1EpubVerifier().verify(
					publication,
					BinderyV1EpubTestFixtures.index(
						hash = BinderyV1EpubTestFixtures.EmptyAggregateHash,
						chapters = emptyList()
					)
				)
			}
			assertEquals(WordSyncPublicationVerificationFailure.InvalidArchive, error.failure)
		}
	}

	@Test
	fun allowsDoctypeTextInsideXmlCommentsCdataAndAttributes() {
		val validOpfs = listOf(
			"<package><!-- literal <!DOCTYPE text --></package>",
			"<package><![CDATA[literal <!DOCTYPE text]]></package>",
			"<package data-note=\"literal <!DOCTYPE text\"></package>"
		)

		validOpfs.forEach { opf ->
			val publication = publication(
				DefaultContainerPath to DefaultContainer,
				DefaultOpfPath to opf.encodeToByteArray()
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
	}

	@Test
	fun acceptsUnboundXmlPrefixesLikeGoTokenDecoder() {
		val container = """
			<epub:container>
				<epub:rootfile full-path="OPS/package.opf"/>
			</epub:container>
		""".trimIndent().encodeToByteArray()
		val opf = """
			<opf:package>
				<opf:item id="chapter" href="Text/unbound.xhtml" media-type="application/xhtml+xml"/>
				<opf:itemref idref="chapter"/>
			</opf:package>
		""".trimIndent().encodeToByteArray()
		val publication = publication(
			DefaultContainerPath to container,
			DefaultOpfPath to opf,
			"OPS/Text/unbound.xhtml" to "<p>Alpha</p>".encodeToByteArray()
		)

		val session = BinderyV1EpubVerifier().verify(
			publication,
			BinderyV1EpubTestFixtures.index(
				hash = "sha256:170ee6886899b29d7283fc55e6e2ca1fb44dc122591ee15fae9c3a2a217e6588",
				chapters = emptyList()
			)
		)

		assertEquals(1, session.provenance.chapters.size)
	}

	@Test
	fun rejectsMalformedUtf8BeforeSourceExtraction() {
		val rawChapter = "<p>A".encodeToByteArray() + byteArrayOf(0xff.toByte()) + " B</p>".encodeToByteArray()
		val publication = singleChapterPublication(
			href = "OPS/Text/invalid.xhtml",
			chapterBytes = rawChapter
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			BinderyV1EpubVerifier().verify(
				publication,
				BinderyV1EpubTestFixtures.index(
					hash = BinderyV1EpubTestFixtures.EmptyAggregateHash,
					chapters = emptyList()
				)
			)
		}

		assertEquals(WordSyncPublicationVerificationFailure.InvalidArchive, error.failure)
	}

	@Test
	fun matchesGoInvalidScalarEntityGolden() {
		listOf("&#xD800;", "&#x110000;").forEach { entity ->
			val publication = singleChapterPublication(
				href = "OPS/Text/scalar.xhtml",
				chapterBytes = "<p>A${entity}B</p>".encodeToByteArray()
			)

			val session = BinderyV1EpubVerifier().verify(
				publication,
				BinderyV1EpubTestFixtures.index(
					hash = "sha256:5c55f7ff00b18d64154ff2dccb6f9ddf8016f6a78cf5c3e8132da9cac6bfe3ab",
					chapters = emptyList()
				)
			)

			assertTrue(
				session.provenance.chapters.single().let { chapter ->
					chapter.extractedByteLength == 6 && chapter.tokenCount == 2
				}
			)
		}
	}

	@Test
	fun duplicateContainerReadsFirstPhysicalEntry() {
		val firstContainer = DefaultContainer
		val secondContainer = "<container><rootfile full-path=\"OPS/missing.opf\"/></container>".encodeToByteArray()
		val opf = singleChapterOpf("Text/chapter.xhtml")
		val publication = duplicateNamePublication(
			entries = listOf(
				"META-INF/container.000" to firstContainer,
				"META-INF/container.111" to secondContainer,
				DefaultOpfPath to opf,
				"OPS/Text/chapter.xhtml" to "<p>First</p>".encodeToByteArray()
			),
			aliases = mapOf(
				"META-INF/container.000" to DefaultContainerPath,
				"META-INF/container.111" to DefaultContainerPath
			)
		)

		val session = BinderyV1EpubVerifier().verify(
			publication,
			BinderyV1EpubTestFixtures.index(
				hash = "sha256:fd553a9712d7449ff8916fb92a439a7e2d1951b1b2d43c524345f89286017227",
				chapters = emptyList()
			)
		)

		assertEquals(1, session.provenance.chapters.size)
	}

	@Test
	fun duplicateChapterReadsFirstPhysicalEntry() {
		val publication = duplicateNamePublication(
			entries = listOf(
				DefaultContainerPath to DefaultContainer,
				DefaultOpfPath to singleChapterOpf("Text/chapter.xhtml"),
				"OPS/Text/chapter.00000" to "<p>First</p>".encodeToByteArray(),
				"OPS/Text/chapter.11111" to "<p>Second</p>".encodeToByteArray()
			),
			aliases = mapOf(
				"OPS/Text/chapter.00000" to "OPS/Text/chapter.xhtml",
				"OPS/Text/chapter.11111" to "OPS/Text/chapter.xhtml"
			)
		)

		val session = BinderyV1EpubVerifier().verify(
			publication,
			BinderyV1EpubTestFixtures.index(
				hash = "sha256:fd553a9712d7449ff8916fb92a439a7e2d1951b1b2d43c524345f89286017227",
				chapters = emptyList()
			)
		)

		assertEquals(1, session.provenance.chapters.size)
	}

	private fun singleChapterPublication(href: String, chapterBytes: ByteArray): File {
		val relativeHref = href.removePrefix("OPS/")
		return publication(
			DefaultContainerPath to DefaultContainer,
			DefaultOpfPath to singleChapterOpf(relativeHref),
			href to chapterBytes
		)
	}

	private fun singleChapterOpf(href: String): ByteArray =
		"""
		<package>
			<item id="chapter" href="$href" media-type="application/xhtml+xml"/>
			<itemref idref="chapter"/>
		</package>
		""".trimIndent().encodeToByteArray()

	private fun publication(vararg entries: Pair<String, ByteArray>): File =
		publication(entries.toList())

	private fun publication(entries: List<Pair<String, ByteArray>>): File {
		val bytes = ByteArrayOutputStream().use { output ->
			ZipOutputStream(output).use { zip ->
				entries.forEach { (name, body) ->
					zip.putNextEntry(ZipEntry(name))
					zip.write(body)
					zip.closeEntry()
				}
			}
			output.toByteArray()
		}
		return createTempFile("bindery-v1-adversarial-", ".epub").toFile().apply { writeBytes(bytes) }
	}

	private fun duplicateNamePublication(
		entries: List<Pair<String, ByteArray>>,
		aliases: Map<String, String>
	): File {
		val bytes = publication(entries).readBytes()
		aliases.forEach { (placeholder, duplicateName) ->
			require(placeholder.encodeToByteArray().size == duplicateName.encodeToByteArray().size)
			bytes.replaceEveryOccurrence(
				placeholder.encodeToByteArray(),
				duplicateName.encodeToByteArray()
			)
		}
		return createTempFile("bindery-v1-duplicate-", ".epub").toFile().apply { writeBytes(bytes) }
	}

	private fun ByteArray.replaceEveryOccurrence(needle: ByteArray, replacement: ByteArray) {
		var index = 0
		while (index <= size - needle.size) {
			if (needle.indices.all { offset -> this[index + offset] == needle[offset] }) {
				replacement.copyInto(this, destinationOffset = index)
				index += needle.size
			} else {
				index += 1
			}
		}
	}

	private companion object {
		const val DefaultContainerPath = "META-INF/container.xml"
		const val DefaultOpfPath = "OPS/package.opf"
		val DefaultContainer =
			"<container><rootfile full-path=\"OPS/package.opf\"/></container>".encodeToByteArray()
	}
}
