package paige.navic.reader

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StorytellerReadaloudRuntimeLoaderTest {
	@Test
	fun opensStorytellerGeneratedReadaloudEpubAsLocalReaderPublicationAndMedia3Plan() = runBlocking {
		val fetchedPaths = mutableListOf<String>()
		val epubBytes = storytellerEpubWithSyncedAudioFixture()
		val loader = StorytellerReadaloudRuntimeLoader(
			fetchResourceBytes = { path ->
				fetchedPaths += path
				epubBytes
			},
			cacheRoot = createTempDirectory("navic-storyteller-open").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3693",
			title = "Alcatraz versus the Evil Librarians",
			resourceHref = "/opds/books/3693/resources/readaloud-1",
			sourceUrl = "https://bindery.local/opds/books/3693/resources/readaloud-1",
			kind = ReaderPublicationKind.Readaloud,
			mediaOverlayEnabled = true
		)

		val runtime = loader.load(request)
		val openStep = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			publicationKey = runtime.publicationUrl,
			openCommand = ReaderBridgeCommand.OpenPublication(
				url = runtime.publicationUrl,
				mediaOverlayEnabled = true
			),
			command = null,
			commandKey = 0L
		)

		assertEquals(listOf("/opds/books/3693/resources/readaloud-1"), fetchedPaths)
		assertTrue(runtime.publicationUrl.startsWith("file:"))
		assertNotEquals(request.sourceUrl, runtime.publicationUrl)
		assertEquals(2, runtime.timeline.clips.size)
		assertEquals(ReaderPublicationKind.Readaloud, runtime.playbackPlan.kind)
		assertEquals(1, runtime.playbackPlan.mediaItems.size)
		val mediaItem = runtime.playbackPlan.mediaItems.single()
		assertTrue(mediaItem.uri.startsWith("file:"))
		assertEquals(emptyMap(), mediaItem.requestHeaders)
		assertEquals("chapter1.mp3", mediaItem.title)
		assertEquals("mpeg", mediaItem.codec)
		assertEquals(
			listOf(
				ReaderBridgeCommand.OpenPublication(
					url = runtime.publicationUrl,
					mediaOverlayEnabled = true
				)
			),
			openStep.commands
		)
	}

	private fun storytellerEpubWithSyncedAudioFixture(): ByteArray {
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
				<package xmlns="http://www.idpf.org/2007/opf" xmlns:media="http://www.idpf.org/epub/vocab/overlays/#" version="3.0">
					<metadata>
						<meta property="media:duration">0:00:10.000</meta>
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
						<seq>
							<par id="p1">
								<text src="../Text/chapter1.xhtml#frag-1"/>
								<audio src="../Audio/chapter1.mp3" clipBegin="0:00:01.250" clipEnd="0:00:03.500"/>
							</par>
							<par id="p2">
								<text src="../Text/chapter1.xhtml#frag-2"/>
								<audio src="../Audio/chapter1.mp3" clipBegin="5s" clipEnd="8s"/>
							</par>
						</seq>
					</body>
				</smil>
			""".trimIndent().encodeToByteArray(),
			"EPUB/Text/chapter1.xhtml" to """
				<html xmlns="http://www.w3.org/1999/xhtml">
					<body>
						<p id="frag-1">First fragment.</p>
						<p id="frag-2">Second fragment.</p>
					</body>
				</html>
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
