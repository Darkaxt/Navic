package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
			  "coverage": 0.984,
			  "audioCoverage": 0.973,
			  "ebookCoverage": 0.962,
			  "resources": {
			    "ebookManifestHref": "/opds/books/3809/manifest",
			    "audiobookManifestHref": "/opds/audiobooks/44"
			  },
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
			      "ebookText": "Alcatraz Versus the Evil Librarian AUTHOR’S FOREWORD. THIS IS AUDIBLE",
			      "methods": ["exact", "fuzzy", "sequence"]
			    }
			  ],
			  "gaps": []
			}
			""".trimIndent()
		)

		assertEquals("426", sidecar.ebookBookFileId)
		assertEquals("633", sidecar.audiobookBookFileId)
		assertEquals(0.995, sidecar.score)
		assertEquals(0.984, sidecar.coverage)
		assertEquals(0.973, sidecar.audioCoverage)
		assertEquals(0.962, sidecar.ebookCoverage)
		assertEquals("/opds/books/3809/manifest", sidecar.ebookManifestHref)
		assertEquals("/opds/audiobooks/44", sidecar.audiobookManifestHref)
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
		assertEquals("This is Audible.", segment.spokenText)
		assertEquals("Alcatraz Versus the Evil Librarian AUTHOR’S FOREWORD. THIS IS AUDIBLE", segment.ebookText)
		assertEquals("This is Audible.", segment.toReaderOverlayFragment().spokenText)
		assertEquals("Alcatraz Versus the Evil Librarian AUTHOR’S FOREWORD. THIS IS AUDIBLE", segment.toReaderOverlayFragment().ebookText)
	}

	@Test
	fun binderySidecarKeepsEbookTextSeparateFromAsrTextForReaderHighlighting() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "cues": [
			    {
			      "id": 3,
			      "audioHref": "Part 01.mp3",
			      "audioStart": 21.44,
			      "audioEnd": 27.52,
			      "text": "They call me Oculator Dramatis, Hero, Savior of the 17 Kingdoms.",
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "spineIndex": 6,
			      "ebookStart": 123,
			      "ebookEnd": 190,
			      "ebookText": "THEY CALL ME OCULATOR DRAMATUS, HERO, SAVIOR OF THE TWELVE KINGDOMS"
			    }
			  ]
			}
			""".trimIndent()
		)

		val segment = sidecar.timeline.segments.single()
		assertEquals("They call me Oculator Dramatis, Hero, Savior of the 17 Kingdoms.", segment.spokenText)
		assertEquals("THEY CALL ME OCULATOR DRAMATUS, HERO, SAVIOR OF THE TWELVE KINGDOMS", segment.ebookText)
		assertEquals("THEY CALL ME OCULATOR DRAMATUS, HERO, SAVIOR OF THE TWELVE KINGDOMS", segment.toReaderOverlayFragment().ebookText)
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
	fun visibleTextRangeStartsAtFirstBinderyCueInsteadOfLargestViewportOverlap() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "cues": [
			    {
			      "audioHref": "Part 01.mp3",
			      "audioStart": 0,
			      "audioEnd": 18.04,
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "ebookStart": 0,
			      "ebookEnd": 78
			    },
			    {
			      "audioHref": "Part 01.mp3",
			      "audioStart": 56.08,
			      "audioEnd": 59.86,
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "ebookStart": 607,
			      "ebookEnd": 702
			    },
			    {
			      "audioHref": "Part 01.mp3",
			      "audioStart": 59.86,
			      "audioEnd": 87.32,
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "ebookStart": 703,
			      "ebookEnd": 1161
			    }
			  ]
			}
			""".trimIndent()
		)

		val target = sidecar.timeline.seekTargetForVisibleTextRange(
			textHref = "OEBPS/Text/authorsforeword.xhtml",
			visibleStart = 3,
			visibleEnd = 2479
		)

		assertNotNull(target)
		assertEquals(0, target.positionMs)
		assertEquals(0, target.segment.textStart)
	}

	@Test
	fun activeSegmentSnapsTinyAudioBoundaryGapToNearestCue() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "previous-cue",
					audioResource = "Audio/chapter01.m4b",
					startMs = 1_000,
					endMs = 2_000,
					textHref = "Text/chapter1.xhtml"
				),
				WhispersyncSegment(
					id = "next-cue",
					audioResource = "Audio/chapter01.m4b",
					startMs = 2_003,
					endMs = 3_000,
					textHref = "Text/chapter1.xhtml"
				)
			)
		)

		val segment = timeline.activeSegment(
			audioResource = "Audio/chapter01.m4b",
			positionMs = 2_002
		)

		assertNotNull(segment)
		assertEquals("next-cue", segment.id)

		val wideGapTimeline = timeline.copy(
			segments = listOf(
				timeline.segments[0],
				timeline.segments[1].copy(startMs = 2_300)
			)
		)
		assertNull(
			wideGapTimeline.activeSegment(
				audioResource = "Audio/chapter01.m4b",
				positionMs = 2_150
			)
		)
	}

	@Test
	fun visibleTextRangeIgnoresRangeLessSegmentsInsteadOfSeekingBlindly() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "segments": [
			    {
			      "audioHref": "Audio/chapter01.m4b",
			      "audioStartMs": 12000,
			      "audioEndMs": 18000,
			      "href": "Text/chapter1.xhtml",
			      "label": "Range-less cue"
			    }
			  ]
			}
			""".trimIndent()
		)

		val target = sidecar.timeline.seekTargetForVisibleTextRange(
			textHref = "Text/chapter1.xhtml",
			visibleStart = 300,
			visibleEnd = 450
		)

		assertNull(target)
	}

	@Test
	fun textPointSelectsCueContainingExactEbookOffset() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "cues": [
			    {
			      "audioHref": "Part 01.mp3",
			      "audioStart": 0,
			      "audioEnd": 18.04,
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "ebookStart": 0,
			      "ebookEnd": 78
			    },
			    {
			      "audioHref": "Part 01.mp3",
			      "audioStart": 18.04,
			      "audioEnd": 21.44,
			      "ebookHref": "OEBPS/Text/authorsforeword.xhtml",
			      "ebookStart": 81,
			      "ebookEnd": 121
			    }
			  ]
			}
			""".trimIndent()
		)

		val target = sidecar.timeline.seekTargetForTextPoint(
			textHref = "OEBPS/Text/authorsforeword.xhtml",
			textOffset = 82
		)

		assertNotNull(target)
		assertEquals(18_040, target.positionMs)
		assertEquals(81, target.segment.textStart)
		assertEquals(121, target.segment.textEnd)
		assertNull(
			sidecar.timeline.seekTargetForTextPoint(
				textHref = "OEBPS/Text/authorsforeword.xhtml",
				textOffset = 79
			)
		)
	}

	@Test
	fun sidecarParserSkipsMalformedSegmentsAndAcceptsFractionalMilliseconds() {
		val result = runCatching {
			decodeWhispersyncSidecar(
				"""
				{
				  "segments": [
				    {
				      "audioHref": "Audio/chapter01.m4b",
				      "startMs": { "bad": true },
				      "endMs": 1000,
				      "href": "Text/chapter1.xhtml",
				      "textStart": 0,
				      "textEnd": 10
				    },
				    {
				      "audioHref": "Audio/chapter01.m4b",
				      "startMs": 263360.5,
				      "endMs": 282920.25,
				      "href": "Text/chapter1.xhtml",
				      "textStart": 20,
				      "textEnd": 80
				    }
				  ]
				}
				""".trimIndent()
			)
		}

		assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
		val segment = result.getOrThrow().timeline.segments.single()
		assertEquals(263_361L, segment.startMs)
		assertEquals(282_920L, segment.endMs)
		assertEquals(20, segment.textStart)
		assertEquals(80, segment.textEnd)
	}

	@Test
	fun sidecarParserReportsDroppedSegmentDiagnostics() {
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "segments": [
			    {
			      "audioHref": "Audio/chapter01.m4b",
			      "startMs": 1000,
			      "endMs": 1000,
			      "href": "Text/chapter1.xhtml",
			      "textStart": 0,
			      "textEnd": 20
			    },
			    {
			      "audioHref": "Audio/chapter01.m4b",
			      "startMs": 5000,
			      "endMs": 3000,
			      "href": "Text/chapter1.xhtml",
			      "textStart": 20,
			      "textEnd": 40
			    },
			    {
			      "audioHref": "Audio/chapter01.m4b",
			      "startMs": 7000,
			      "endMs": 9000,
			      "href": "Text/chapter1.xhtml",
			      "textStart": 40,
			      "textEnd": 60
			    }
			  ]
			}
			""".trimIndent()
		)

		assertEquals(1, sidecar.timeline.segments.size)
		assertEquals(2, sidecar.droppedSegmentCount)
		assertEquals(
			listOf(
				"segment[0]: invalid-audio-range",
				"segment[1]: invalid-audio-range"
			),
			sidecar.droppedSegmentReasons
		)
	}

	@Test
	fun activeSegmentDoesNotCrossMatchWrongAudioTrackBySuffixOnly() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "chapter-2",
					audioTrackIndex = 1,
					audioResource = "chapter.m4b",
					startMs = 1_000,
					endMs = 2_000,
					textHref = "Text/chapter2.xhtml",
					textStart = 10,
					textEnd = 30
				)
			)
		)

		val segment = timeline.activeSegment(
			audioResource = "wrong-book/chapter.m4b",
			audioTrackIndex = 0,
			positionMs = 1_500
		)

		assertNull(segment)
	}

	@Test
	fun activeSegmentDoesNotLetStaleResourceOverrideExplicitTrackIndex() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "sidecar-track",
					audioTrackIndex = 1,
					audioResource = "old-release/chapter01.m4b",
					startMs = 9_000,
					endMs = 12_000,
					textHref = "Text/chapter1.xhtml",
					textStart = 10,
					textEnd = 30
				)
			)
		)

		val segment = timeline.activeSegment(
			audioResource = "old-release/chapter01.m4b",
			audioTrackIndex = 0,
			positionMs = 10_500
		)

		assertNull(segment)
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
