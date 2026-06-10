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
		assertEquals("First fragment.", first.label)
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
		assertEquals("Second fragment.", overlay.fragment.label)

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
		assertEquals("Chapter 1: A Beginning", audio.label)

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
		assertEquals("Storyteller Chapter 1", track.displayTitle)
		assertEquals("An Unexpected Party", track.sectionLabel)
		assertEquals("Andy Serkis", track.narrator)
		assertEquals("Author", track.author)
		assertEquals(8_000L, track.durationMs)
		assertEquals("mp3", track.codec)
		assertEquals(128, track.bitrateKbps)
		assertEquals(44_100L, track.sampleRateHz)
		assertEquals(2, track.channels)
		assertEquals("Studio 128 kbps", track.qualityLabel)
		assertEquals("Storyteller", track.sourceProviderLabel)
		assertEquals("Unabridged / MP3", track.sourceReleaseLabel)
		assertEquals("https://storyteller.local/releases/hobbit", track.sourceUrl)
		assertEquals(7, track.trackNumber)
		assertEquals(1, track.discNumber)

		val labels = session.toReadaloudPlaybackPlan().metadataLabelsForPlaybackPosition(
			ReadaloudPlaybackPosition(
				sessionId = "book-1",
				trackIndex = 0,
				mediaId = "readaloud:audio1",
				positionMs = 1_500,
				durationMs = 8_000L,
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
		assertEquals("https://storyteller.local/releases/hobbit", labels?.sourceUrlLabel)
		assertEquals("mp3 / 128 kbps / 44.1 kHz / stereo", labels?.formatLabel)
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
						<meta property="storyteller:chapter-label" refines="#audio1">Storyteller Chapter 1</meta>
						<meta property="navic:section-label" refines="#audio1">An Unexpected Party</meta>
						<meta property="storyteller:narrator" refines="#audio1">Andy Serkis</meta>
						<meta property="storyteller:quality-label" refines="#audio1">Studio 128 kbps</meta>
						<meta property="storyteller:source-provider" refines="#audio1">Storyteller</meta>
						<meta property="storyteller:source-release" refines="#audio1">Unabridged / MP3</meta>
						<meta property="storyteller:source-url" refines="#audio1">https://storyteller.local/releases/hobbit</meta>
						<meta property="storyteller:codec" refines="#audio1">mp3</meta>
						<meta property="storyteller:bitrate-kbps" refines="#audio1">128</meta>
						<meta property="storyteller:sample-rate-hz" refines="#audio1">44100</meta>
						<meta property="storyteller:channels" refines="#audio1">2</meta>
						<meta property="storyteller:track-number" refines="#audio1">7</meta>
						<meta property="storyteller:disc-number" refines="#audio1">1</meta>
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
					<head>
						<title>Chapter 1</title>
					</head>
					<body>
						<h1>Chapter 1: A Beginning</h1>
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
