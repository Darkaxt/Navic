package paige.navic.reader

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BinderyV1EpubResourceBudgetTest {
	@Test
	fun rejectsCumulativeTokensBeforeGrowingPublicationArrays() {
		val entries = mutableListOf(
			ContainerPath to "<container><rootfile full-path=\"OPS/package.opf\"/></container>".encodeToByteArray()
		)
		val manifest = buildString {
			append("<package>")
			repeat(4) { index ->
				append("<item id=\"c$index\" href=\"Text/c$index.xhtml\" media-type=\"application/xhtml+xml\"/>")
			}
			repeat(4) { index -> append("<itemref idref=\"c$index\"/>") }
			append("</package>")
		}
		entries += OpfPath to manifest.encodeToByteArray()
		repeat(4) { index ->
			entries += "OPS/Text/c$index.xhtml" to "<p>a a</p>".encodeToByteArray()
		}
		val verifier = BinderyV1EpubVerifier(
			limits = BinderyV1EpubVerificationLimits(
				maxTotalExtractedTextBytes = 16,
				maxTotalTokenCount = 5
			)
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			verifier.verify(
				publication(entries),
				BinderyV1EpubTestFixtures.index(
					hash = BinderyV1EpubTestFixtures.EmptyAggregateHash,
					chapters = emptyList()
				)
			)
		}

		assertEquals(WordSyncPublicationVerificationFailure.ResourceLimit, error.failure)
	}

	@Test
	fun rejectsCumulativeZipEntryNameMetadataBytes() {
		val entries = listOf(
			ContainerPath to "<container><rootfile full-path=\"OPS/package.opf\"/></container>".encodeToByteArray(),
			OpfPath to "<package/>".encodeToByteArray(),
			"synthetic/${"n".repeat(80)}" to byteArrayOf(1)
		)
		val verifier = BinderyV1EpubVerifier(
			limits = BinderyV1EpubVerificationLimits(maxArchiveEntryNameBytes = 64)
		)

		val error = assertFailsWith<WordSyncPublicationVerificationException> {
			verifier.verify(
				publication(entries),
				BinderyV1EpubTestFixtures.index(
					hash = BinderyV1EpubTestFixtures.EmptyAggregateHash,
					chapters = emptyList()
				)
			)
		}

		assertEquals(WordSyncPublicationVerificationFailure.ResourceLimit, error.failure)
	}

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
		return createTempFile("bindery-v1-budget-", ".epub").toFile().apply { writeBytes(bytes) }
	}

	private companion object {
		const val ContainerPath = "META-INF/container.xml"
		const val OpfPath = "OPS/package.opf"
	}
}
