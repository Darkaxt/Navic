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
		val archiveMetrics = mutableListOf<StorytellerArchiveReadMetrics>()
		val epubBytes = storytellerEpubWithSyncedAudioFixture()
		val cacheRoot = createTempDirectory("navic-storyteller-open").toFile()
		val loader = StorytellerReadaloudRuntimeLoader(
			fetchResourceBytes = { path ->
				fetchedPaths += path
				epubBytes
			},
			cacheRoot = cacheRoot,
			archiveReadObserver = archiveMetrics::add
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
			runtimeGeneration = 0,
			publicationKey = runtime.publicationUrl,
			openCommand = ReaderBridgeCommand.OpenPublication(
				url = runtime.publicationUrl,
				mediaOverlayEnabled = true
			),
			command = null,
			commandKey = 0L
		)

		assertEquals(listOf("/opds/books/3693/resources/readaloud-1"), fetchedPaths)
		assertEquals(1, archiveMetrics.single().archiveOpenCount)
		assertEquals(listOf("EPUB/Audio/chapter1.mp3"), archiveMetrics.single().streamedEntryNames)
		assertTrue(runtime.publicationUrl.startsWith("https://appassets.androidplatform.net/reader-cache/reader-publications/"))
		assertNotEquals(request.sourceUrl, runtime.publicationUrl)
		assertEquals(2, runtime.timeline.clips.size)
		assertEquals(ReaderPublicationKind.Readaloud, runtime.playbackPlan.kind)
		assertEquals(1, runtime.playbackPlan.mediaItems.size)
		val mediaItem = runtime.playbackPlan.mediaItems.single()
		assertTrue(mediaItem.uri.startsWith("file:"))
		assertEquals(emptyMap(), mediaItem.requestHeaders)
		assertEquals("Storyteller Chapter 1", mediaItem.title)
		assertEquals("An Unexpected Party", mediaItem.subtitle)
		assertEquals("Andy Serkis", mediaItem.artist)
		assertEquals("mp3", mediaItem.codec)
		assertEquals(128, mediaItem.bitrateKbps)
		assertEquals(44100L, mediaItem.sampleRateHz)
		assertEquals(2, mediaItem.channels)
		assertEquals("Studio 128 kbps", mediaItem.qualityLabel)
		assertEquals("Storyteller", mediaItem.sourceProviderLabel)
		assertEquals("Unabridged / MP3", mediaItem.sourceReleaseLabel)
		assertEquals("https://storyteller.local/releases/alcatraz", mediaItem.sourceUrl)
		val labels = runtime.playbackPlan.metadataLabelsForPlaybackPosition(
			ReadaloudPlaybackPosition(
				sessionId = "3693",
				trackIndex = 0,
				mediaId = mediaItem.mediaId,
				positionMs = 1_500,
				durationMs = mediaItem.durationMs,
				isPlaying = true,
				playbackSpeed = 1f
			)
		)
		assertEquals("Storyteller Chapter 1", labels?.chapterLabel)
		assertEquals("An Unexpected Party", labels?.sectionLabel)
		assertEquals("Andy Serkis", labels?.narratorLabel)
		assertEquals("Studio 128 kbps", labels?.qualityLabel)
		assertEquals("Storyteller", labels?.sourceProviderLabel)
		assertEquals("Unabridged / MP3", labels?.sourceReleaseLabel)
		assertEquals("https://storyteller.local/releases/alcatraz", labels?.sourceUrlLabel)
		assertEquals("mp3 / 128 kbps / 44.1 kHz / stereo", labels?.formatLabel)
		assertEquals(
			listOf(
				ReaderBridgeCommand.OpenPublication(
					url = runtime.publicationUrl,
					mediaOverlayEnabled = true
				)
			),
			openStep.commands.map { it.command }
		)
		assertEquals(1, cacheRoot.walkTopDown().count { file -> file.isFile && file.extension == "epub" })
		assertEquals(2, runtime.sessionLease.release())
		assertTrue(!cacheRoot.resolve("reader-publications/${runtime.cacheKey}").exists())
		assertTrue(!cacheRoot.resolve("storyteller-readaloud/${runtime.cacheKey}").exists())
		assertEquals(0, cacheRoot.walkTopDown().count { file -> file.isFile && file.extension == "epub" })
	}

	@Test
	fun reusesCachedStorytellerReadaloudPackageWithoutFetchingAgain() = runBlocking {
		var fetchCount = 0
		val archiveMetrics = mutableListOf<StorytellerArchiveReadMetrics>()
		val epubBytes = storytellerEpubWithSyncedAudioFixture()
		val loader = StorytellerReadaloudRuntimeLoader(
			fetchResourceBytes = {
				fetchCount += 1
				epubBytes
			},
			cacheRoot = createTempDirectory("navic-storyteller-cache").toFile(),
			archiveReadObserver = archiveMetrics::add
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3693",
			title = "Alcatraz",
			resourceHref = "/opds/books/3693/resources/readaloud-1",
			sourceUrl = "https://bindery.local/opds/books/3693/resources/readaloud-1",
			kind = ReaderPublicationKind.Readaloud,
			mediaOverlayEnabled = true
		)

		val first = loader.load(request)
		val second = loader.load(request)

		assertEquals(1, fetchCount)
		assertEquals(2, archiveMetrics.size)
		assertEquals(1, archiveMetrics[0].archiveOpenCount)
		assertEquals(1, archiveMetrics[1].archiveOpenCount)
		assertEquals(listOf("EPUB/Audio/chapter1.mp3"), archiveMetrics[0].streamedEntryNames)
		assertEquals(emptyList(), archiveMetrics[1].streamedEntryNames)
		assertEquals(false, first.fromCache)
		assertEquals(true, second.fromCache)
		assertEquals(first.publicationUrl, second.publicationUrl)
		assertEquals(
			first.playbackPlan.mediaItems.single().uri,
			second.playbackPlan.mediaItems.single().uri
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
						<meta property="storyteller:chapter-label" refines="#audio1">Storyteller Chapter 1</meta>
						<meta property="storyteller:section-label" refines="#audio1">An Unexpected Party</meta>
						<meta property="storyteller:narrator" refines="#audio1">Andy Serkis</meta>
						<meta property="storyteller:quality-label" refines="#audio1">Studio 128 kbps</meta>
						<meta property="storyteller:source-provider" refines="#audio1">Storyteller</meta>
						<meta property="storyteller:source-release" refines="#audio1">Unabridged / MP3</meta>
						<meta property="storyteller:source-url" refines="#audio1">https://storyteller.local/releases/alcatraz</meta>
						<meta property="storyteller:codec" refines="#audio1">mp3</meta>
						<meta property="storyteller:bitrate-kbps" refines="#audio1">128</meta>
						<meta property="storyteller:sample-rate-hz" refines="#audio1">44100</meta>
						<meta property="storyteller:channels" refines="#audio1">2</meta>
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
