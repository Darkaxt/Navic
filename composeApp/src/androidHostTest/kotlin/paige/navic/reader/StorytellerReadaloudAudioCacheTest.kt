package paige.navic.reader

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorytellerReadaloudAudioCacheTest {
	@Test
	fun extractsReferencedStorytellerAudioResourcesToPlayableFileUris() {
		val epubBytes = storytellerEpubWithAudioFixture()
		val readaloudPackage = StorytellerMediaOverlayParser.parsePackage(epubBytes)
		val cacheRoot = createTempDirectory("navic-readaloud-cache").toFile()

		val cache = StorytellerReadaloudAudioCache.materialize(
			sessionId = "book/3693",
			epubBytes = epubBytes,
			readaloudPackage = readaloudPackage,
			cacheRoot = cacheRoot
		)

		val uri = cache.audioHrefResolver("EPUB/Audio/chapter1.mp3")
		assertTrue(uri.startsWith("file:"))
		assertTrue(cache.publicationUri.startsWith("https://appassets.androidplatform.net/reader-cache/storyteller-readaloud/"))
		assertTrue(cache.publicationFile.exists())
		assertEquals(epubBytes.size.toLong(), cache.publicationFile.length())
		val cachedFile = cache.cachedAudioFiles.single()
		assertTrue(cachedFile.exists())
		assertEquals("AUDIO_BYTES", cachedFile.readText())
		assertEquals(cachedFile.toURI().toString(), uri)
		val sessionDirectory = cache.publicationFile.parentFile!!
		assertEquals(1, cache.sessionLease.release())
		assertTrue(!sessionDirectory.exists())
	}

	private fun storytellerEpubWithAudioFixture(): ByteArray {
		val entries = mapOf(
			"META-INF/container.xml" to """
				<?xml version="1.0" encoding="UTF-8"?>
				<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
					<rootfiles>
						<rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
					</rootfiles>
				</container>
			""".trimIndent().encodeToByteArray(),
			"EPUB/package.opf" to """
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
					<spine>
						<itemref idref="chapter1"/>
					</spine>
				</package>
			""".trimIndent().encodeToByteArray(),
			"EPUB/Overlays/chapter1.smil" to """
				<?xml version="1.0" encoding="UTF-8"?>
				<smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
					<body>
						<par id="p1">
							<text src="../Text/chapter1.xhtml#frag-1"/>
							<audio src="../Audio/chapter1.mp3" clipBegin="0:00:01.250" clipEnd="0:00:03.500"/>
						</par>
					</body>
				</smil>
			""".trimIndent().encodeToByteArray(),
			"EPUB/Text/chapter1.xhtml" to """
				<html xmlns="http://www.w3.org/1999/xhtml"><body><p id="frag-1">First.</p></body></html>
			""".trimIndent().encodeToByteArray(),
			"EPUB/Audio/chapter1.mp3" to "AUDIO_BYTES".encodeToByteArray()
		)
		return ByteArrayOutputStream().use { output ->
			ZipOutputStream(output).use { zip ->
				entries.forEach { (path, bytes) ->
					zip.putNextEntry(ZipEntry(path))
					zip.write(bytes)
					zip.closeEntry()
				}
			}
			output.toByteArray()
		}
	}
}
