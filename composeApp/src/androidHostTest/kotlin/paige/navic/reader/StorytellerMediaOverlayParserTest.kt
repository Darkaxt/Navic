package paige.navic.reader

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StorytellerMediaOverlayParserTest {
	@Test
	fun parsesStorytellerEpubMediaOverlayClipsFromOpfAndSmil() {
		val timeline = StorytellerMediaOverlayParser.parse(storytellerEpubFixture())

		assertEquals(2, timeline.clips.size)
		assertEquals(10.0, timeline.durationSeconds)

		val first = timeline.clips.first()
		assertEquals("EPUB/Audio/chapter1.mp3", first.audioResource)
		assertEquals("EPUB/Text/chapter1.xhtml", first.textResource)
		assertEquals("frag-1", first.fragmentId)
		assertEquals(1.25, first.startSeconds)
		assertEquals(3.5, first.endSeconds)
		assertEquals("p1", first.label)
	}

	@Test
	fun mapsAudioPositionsToReaderOverlayAndTextFragmentsBackToAudioSeek() {
		val timeline = StorytellerMediaOverlayParser.parse(storytellerEpubFixture())

		val command = timeline.readerCommandForAudioPosition(
			audioResource = "EPUB/Audio/chapter1.mp3",
			positionMs = 5_500,
			syncEnabled = true
		)

		val overlay = assertIs<ReaderBridgeCommand.ApplyOverlayFragment>(command)
		assertEquals("EPUB/Text/chapter1.xhtml", overlay.fragment.textHref)
		assertEquals("frag-2", overlay.fragment.fragmentId)
		assertEquals(5.0, overlay.fragment.clipBeginSeconds)
		assertEquals(8.0, overlay.fragment.clipEndSeconds)

		val seek = timeline.seekTargetForText("EPUB/Text/chapter1.xhtml#frag-1")
		assertEquals("EPUB/Audio/chapter1.mp3", seek?.audioResource)
		assertEquals(1_250, seek?.positionMs)
		assertNull(
			timeline.readerCommandForAudioPosition(
				audioResource = "EPUB/Audio/chapter1.mp3",
				positionMs = 5_500,
				syncEnabled = false
			)
		)
	}

	@Test
	fun parsesStorytellerPackageAudioResourcesIntoReadaloudSession() {
		val readaloudPackage = StorytellerMediaOverlayParser.parsePackage(storytellerEpubFixture())

		assertEquals(2, readaloudPackage.timeline.clips.size)
		assertEquals(1, readaloudPackage.audioResources.size)
		val audio = readaloudPackage.audioResources.single()
		assertEquals("audio1", audio.id)
		assertEquals("EPUB/Audio/chapter1.mp3", audio.href)
		assertEquals("audio/mpeg", audio.mediaType)
		assertEquals(8_000L, audio.durationMs)

		val session = readaloudPackage.toReadaloudAudioSession(
			id = "book-1",
			title = "Storyteller Book",
			author = "Author",
			narrator = "Narrator",
			audioHrefResolver = { href -> "file:///cache/$href" }
		)
		val track = session.tracks.single()
		assertEquals("audio1", track.resourceKey)
		assertEquals("file:///cache/EPUB/Audio/chapter1.mp3", track.href)
		assertEquals("chapter1.mp3", track.displayTitle)
		assertEquals("Narrator", track.narrator)
		assertEquals("Author", track.author)
		assertEquals(8_000L, track.durationMs)
		assertEquals(1, track.trackNumber)
	}

	private fun storytellerEpubFixture(): ByteArray {
		val entries = mapOf(
			"META-INF/container.xml" to """
				<?xml version="1.0" encoding="UTF-8"?>
				<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
					<rootfiles>
						<rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
					</rootfiles>
				</container>
			""".trimIndent(),
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
			""".trimIndent(),
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
			""".trimIndent(),
			"EPUB/Text/chapter1.xhtml" to """
				<html xmlns="http://www.w3.org/1999/xhtml">
					<body>
						<p id="frag-1">First fragment.</p>
						<p id="frag-2">Second fragment.</p>
					</body>
				</html>
			""".trimIndent()
		)
		return ByteArrayOutputStream().use { output ->
			ZipOutputStream(output).use { zip ->
				entries.forEach { (path, body) ->
					zip.putNextEntry(ZipEntry(path))
					zip.write(body.encodeToByteArray())
					zip.closeEntry()
				}
			}
			output.toByteArray()
		}
	}
}
