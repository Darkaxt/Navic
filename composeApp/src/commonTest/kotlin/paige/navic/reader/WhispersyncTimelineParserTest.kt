package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WhispersyncTimelineParserTest {
	@Test
	fun productionBinderySidecarCuesParseIntoTimelineSegments() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "version": 1,
			  "schema": "bindery.whispersync.sidecar.v1",
			  "backend": "whispersync",
			  "bookId": 3809,
			  "ebookBookFileId": 426,
			  "audiobookBookFileId": 633,
			  "language": "en",
			  "score": 0.995,
			  "cues": [
			    {
			      "id": 1,
			      "audioResourceId": "track-001",
			      "audioTrackIndex": 0,
			      "audioHref": "6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b",
			      "audioStart": 0,
			      "audioEnd": 28.28,
			      "text": "This is Audible.",
			      "status": "matched",
			      "score": 1,
			      "ebookHref": "OEBPS/xhtml/Authorforeword.xhtml",
			      "spineIndex": 6,
			      "ebookStart": 0,
			      "ebookEnd": 107,
			      "methods": ["exact", "fuzzy", "sequence"]
			    }
			  ],
			  "gaps": []
			}
			""".trimIndent()
		)

		assertEquals("426", sidecar.ebookBookFileId)
		assertEquals("633", sidecar.audiobookBookFileId)
		val segment = sidecar.timeline.segments.single()
		assertEquals("1", segment.id)
		assertEquals("track-001", segment.audioResourceId)
		assertEquals(0, segment.audioTrackIndex)
		assertEquals("6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b", segment.audioResource)
		assertEquals(0, segment.startMs)
		assertEquals(28_280, segment.endMs)
		assertEquals("OEBPS/xhtml/Authorforeword.xhtml", segment.textHref)
		assertEquals(0, segment.textStart)
		assertEquals(107, segment.textEnd)
	}

	@Test
	fun binderySidecarParsesSegmentsAndFindsActiveAudioPosition() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "artifactId": "artifact-42",
			  "ebook": {
			    "bookFileId": "ebook-file-1",
			    "href": "OEBPS/Text/chapter1.xhtml",
			    "documentTextLength": 1200
			  },
			  "audiobook": {
			    "bookFileId": "audio-file-7",
			    "resources": [
			      { "href": "Audio/part01.mp3", "durationMs": 60000 }
			    ]
			  },
			  "segments": [
			    {
			      "id": "seg-1",
			      "audioResource": "Audio/part01.mp3",
			      "startMs": 1250,
			      "endMs": 3500,
			      "textHref": "OEBPS/Text/chapter1.xhtml",
			      "fragmentId": "frag-1",
			      "rangeCfi": "epubcfi(/6/2[chapter1]!/4/2/6,/1:0,/1:32)",
			      "textStart": 10,
			      "textEnd": 42,
			      "label": "Opening sentence"
			    },
			    {
			      "id": "seg-2",
			      "audioResource": "Audio/part01.mp3",
			      "startMs": 5000,
			      "endMs": 8000,
			      "textHref": "OEBPS/Text/chapter1.xhtml",
			      "textStart": 100,
			      "textEnd": 180,
			      "label": "Second sentence"
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals("artifact-42", sidecar.artifactId)
		assertEquals("ebook-file-1", sidecar.ebookBookFileId)
		assertEquals("audio-file-7", sidecar.audiobookBookFileId)
		assertEquals(1200, sidecar.documentTextLength)
		assertEquals(2, sidecar.timeline.segments.size)

		val active = sidecar.timeline.activeSegment(
			audioResource = "/Audio/part01.mp3",
			positionMs = 1_500
		)
		assertNotNull(active)
		assertEquals("seg-1", active.id)
		assertEquals("OEBPS/Text/chapter1.xhtml", active.textHref)
		assertEquals("frag-1", active.fragmentId)
		assertEquals("Opening sentence", active.label)

		val overlay = active.toReaderOverlayFragment()
		assertEquals("Audio/part01.mp3", overlay.resourceHref)
		assertEquals("frag-1", overlay.fragmentId)
		assertEquals("OEBPS/Text/chapter1.xhtml", overlay.textHref)
		assertEquals(1.25, overlay.clipBeginSeconds)
		assertEquals(3.5, overlay.clipEndSeconds)
		assertEquals(10, overlay.textStart)
		assertEquals(42, overlay.textEnd)
		assertEquals("Opening sentence", overlay.label)

		assertNull(
			sidecar.timeline.activeSegment(
				audioResource = "Audio/part01.mp3",
				positionMs = 4_000
			)
		)
	}

	@Test
	fun visibleTextRangeChoosesBestOverlappingAudioSeekTarget() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "artifactId": "artifact-77",
			  "ebookBookFileId": "ebook-file-2",
			  "audiobookBookFileId": "audio-file-9",
			  "alignments": [
			    {
			      "audio": { "href": "Audio/chapter01.m4b" },
			      "audioStartMs": 0,
			      "audioEndMs": 1000,
			      "href": "Text/chapter1.xhtml",
			      "textStart": 0,
			      "textEnd": 30
			    },
			    {
			      "audio": { "href": "Audio/chapter01.m4b" },
			      "audioStartMs": 1250,
			      "audioEndMs": 2400,
			      "href": "Text/chapter1.xhtml",
			      "textStart": 40,
			      "textEnd": 95,
			      "label": "Visible paragraph"
			    },
			    {
			      "audio": { "href": "Audio/chapter01.m4b" },
			      "audioStartMs": 3000,
			      "audioEndMs": 4200,
			      "href": "Text/chapter1.xhtml",
			      "textStart": 120,
			      "textEnd": 180
			    }
			  ]
			}
			""".trimIndent()
		)

		val target = sidecar.timeline.seekTargetForVisibleTextRange(
			textHref = "/Text/chapter1.xhtml",
			visibleStart = 50,
			visibleEnd = 90
		)

		assertNotNull(target)
		assertEquals("Audio/chapter01.m4b", target.audioResource)
		assertEquals(1_250, target.positionMs)
		assertEquals("Visible paragraph", target.segment.label)
	}

	@Test
	fun missingDocumentTextLengthKeepsUsableSegments() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "artifactId": "artifact-without-length",
			  "segments": [
			    {
			      "audioHref": "tracks/chapter-02.mp3",
			      "startSeconds": 9.5,
			      "endSeconds": 10.75,
			      "textResource": "Text/chapter2.xhtml",
			      "rangeCfi": "epubcfi(/6/4!/4/8,/1:0,/1:18)",
			      "textStart": 200,
			      "textEnd": 230
			    }
			  ]
			}
			""".trimIndent()
		)

		assertNull(sidecar.documentTextLength)
		val segment = sidecar.timeline.segments.single()
		assertEquals("tracks/chapter-02.mp3", segment.audioResource)
		assertEquals(9_500, segment.startMs)
		assertEquals(10_750, segment.endMs)
		assertEquals("Text/chapter2.xhtml", segment.textHref)
		assertEquals("epubcfi(/6/4!/4/8,/1:0,/1:18)", segment.rangeCfi)
	}
}
