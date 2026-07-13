package paige.navic.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StorytellerReadaloudStreamingTest {
	@Test
	fun streamsLargeReferencedAudioWithoutBufferingOrOpeningUnreferencedPayloads() {
		val root = createTempDirectory("navic-readaloud-streaming").toFile()
		val publicationFile = root.resolve("large-readaloud.epub")
		writeLargeReadaloudEpub(publicationFile)
		val metrics = StorytellerArchiveReadMetrics()

		val materialized = StorytellerEpubArchive.open(publicationFile, metrics).use { archive ->
			val readaloudPackage = StorytellerMediaOverlayParser.parsePackage(archive)
			StorytellerReadaloudAudioCache.materialize(
				sessionId = "large-book",
				archive = archive,
				publicationFile = publicationFile,
				publicationUrl = "https://appassets.androidplatform.net/reader-cache/reader-publications/large/publication.epub",
				readaloudPackage = readaloudPackage,
				cacheRoot = root.resolve("managed")
			)
		}

		assertSame(publicationFile, materialized.publicationFile)
		assertEquals(1, metrics.archiveOpenCount)
		assertTrue(metrics.peakBufferedMetadataBytes < 1024 * 1024)
		assertEquals(64 * 1024, metrics.peakStreamBufferBytes)
		assertEquals(ReferencedAudioBytes, metrics.streamedAudioBytes)
		assertEquals(listOf("EPUB/Audio/chapter1.mp3"), metrics.streamedEntryNames)
		assertFalse(metrics.openedEntryNames.contains("EPUB/Unused/decoy.bin"))
		assertEquals(ReferencedAudioBytes, materialized.cachedAudioFiles.single().length())
	}

	@Test
	fun productionReadaloudPathDoesNotMaterializeWholeArchiveMapsOrDuplicatePublicationBytes() {
		val parser = productionSource("StorytellerMediaOverlayParser.android.kt")
		val cache = productionSource("StorytellerReadaloudAudioCache.android.kt")
		val loader = productionSource("StorytellerReadaloudRuntimeLoader.android.kt")

		assertFalse(parser.contains("fun epubEntries("))
		assertFalse(cache.contains("fun epubEntries("))
		assertFalse(parser.contains("Map<String, ByteArray>"))
		assertFalse(cache.contains("Map<String, ByteArray>"))
		assertFalse(loader.contains("resolved.publicationFile.readBytes()"))
		assertFalse(cache.contains("publicationFile.writeBytes(epubBytes)"))
	}

	private fun writeLargeReadaloudEpub(target: File) {
		target.parentFile?.mkdirs()
		ZipOutputStream(target.outputStream().buffered()).use { zip ->
			zip.writeTextEntry(
				"META-INF/container.xml",
				"""
					<?xml version="1.0" encoding="UTF-8"?>
					<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
						<rootfiles>
							<rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
						</rootfiles>
					</container>
				""".trimIndent()
			)
			zip.writeTextEntry(
				"EPUB/package.opf",
				"""
					<?xml version="1.0" encoding="UTF-8"?>
					<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
						<metadata>
							<meta property="media:duration" refines="#audio1">0:00:08.000</meta>
						</metadata>
						<manifest>
							<item id="chapter1" href="Text/chapter1.xhtml" media-type="application/xhtml+xml" media-overlay="mo1"/>
							<item id="mo1" href="Overlays/chapter1.smil" media-type="application/smil+xml"/>
							<item id="audio1" href="Audio/chapter1.mp3" media-type="audio/mpeg"/>
						</manifest>
						<spine><itemref idref="chapter1"/></spine>
					</package>
				""".trimIndent()
			)
			zip.writeTextEntry(
				"EPUB/Overlays/chapter1.smil",
				"""
					<?xml version="1.0" encoding="UTF-8"?>
					<smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
						<body><par id="p1">
							<text src="../Text/chapter1.xhtml#frag-1"/>
							<audio src="../Audio/chapter1.mp3" clipBegin="0s" clipEnd="8s"/>
						</par></body>
					</smil>
				""".trimIndent()
			)
			zip.writeTextEntry(
				"EPUB/Text/chapter1.xhtml",
				"<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p id=\"frag-1\">First.</p></body></html>"
			)
			zip.writeGeneratedEntry("EPUB/Audio/chapter1.mp3", ReferencedAudioBytes)
			zip.writeGeneratedEntry("EPUB/Unused/decoy.bin", UnreferencedPayloadBytes)
		}
	}

	private fun ZipOutputStream.writeTextEntry(path: String, body: String) {
		putNextEntry(ZipEntry(path))
		write(body.encodeToByteArray())
		closeEntry()
	}

	private fun ZipOutputStream.writeGeneratedEntry(path: String, byteCount: Long) {
		putNextEntry(ZipEntry(path))
		val block = ByteArray(64 * 1024) { index -> ((index * 31) and 0xff).toByte() }
		var remaining = byteCount
		while (remaining > 0L) {
			val count = minOf(block.size.toLong(), remaining).toInt()
			write(block, 0, count)
			remaining -= count
		}
		closeEntry()
	}

	private fun productionSource(name: String): String =
		listOf(
			File("src/androidMain/kotlin/paige/navic/reader/$name"),
			File("composeApp/src/androidMain/kotlin/paige/navic/reader/$name")
		).first(File::isFile).readText()

	private companion object {
		const val ReferencedAudioBytes = 24L * 1024 * 1024
		const val UnreferencedPayloadBytes = 32L * 1024 * 1024
	}
}
