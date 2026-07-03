package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReaderWhispersyncSyncCoordinatorTest {
	@Test
	fun playbackPositionUsesSidecarTrackIndexWhenPlaybackResourceDiffersFromAudioHref() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "cue-999",
					audioResourceId = "track-001",
					audioTrackIndex = 0,
					audioResource = "6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b",
					startMs = 263_360,
					endMs = 282_920,
					textHref = "OEBPS/xhtml/Authorforeword.xhtml",
					textStart = 3,
					textEnd = 4851,
					label = "Author foreword"
				)
			)
		)

		val step = ReaderWhispersyncSyncState().onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=633",
			audioTrackIndex = 0,
			positionMs = 263_500
		)

		val command = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(step.state.engineCommand)
		assertEquals("OEBPS/xhtml/Authorforeword.xhtml", command.fragment.textHref)
		assertEquals("Author foreword", command.fragment.label)
		assertEquals(ReaderWhispersyncStatusKind.Playing, step.status?.kind)
		val diagnostic = assertNotNull(step.activeSegment)
		assertEquals("cue-999", diagnostic.segmentId)
		assertEquals("6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b", diagnostic.audioResource)
		assertEquals(0, diagnostic.audioTrackIndex)
		assertEquals(263_500L, diagnostic.positionMs)
		assertEquals("OEBPS/xhtml/Authorforeword.xhtml", diagnostic.textHref)
		assertEquals(3, diagnostic.textStart)
		assertEquals(4851, diagnostic.textEnd)
		assertEquals("Author foreword", diagnostic.label)
		assertEquals(true, diagnostic.applyMediaOverlay)
	}

	@Test
	fun playbackPositionPublishesReaderOverlayCommandsWithStableDispatchKeys() {
		val timeline = whispersyncTimeline()
		val initial = ReaderWhispersyncSyncState()

		val first = initial.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_500
		)
		val firstCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(first.engineCommand)
		assertEquals("seg-1", firstCommand.fragment.fragmentId)
		assertEquals("Opening sentence", firstCommand.fragment.label)
		assertEquals(0.111, firstCommand.fragment.progress ?: -1.0, absoluteTolerance = 0.001)
		assertEquals(14, firstCommand.fragment.progressTextEnd)
		assertEquals(1L, first.engineCommandKey)

		val progressed = first.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "/Audio/chapter01.m4b",
			positionMs = 2_000
		)
		val progressedCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(progressed.engineCommand)
		assertEquals("seg-1", progressedCommand.fragment.fragmentId)
		assertEquals(0.333, progressedCommand.fragment.progress ?: -1.0, absoluteTolerance = 0.001)
		assertEquals(21, progressedCommand.fragment.progressTextEnd)
		assertEquals(2L, progressed.engineCommandKey)

		val duplicate = progressed.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "/Audio/chapter01.m4b",
			positionMs = 2_000
		)
		assertEquals(progressed.engineCommand, duplicate.engineCommand)
		assertEquals(progressed.engineCommandKey, duplicate.engineCommandKey)

		val duplicateStep = progressed.onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "/Audio/chapter01.m4b",
			positionMs = 2_000
		)
		assertEquals("a", duplicateStep.activeSegment?.segmentId)
		assertEquals(false, duplicateStep.activeSegment?.applyMediaOverlay)

		val outsideSegment = duplicate.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 9_500
		)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, outsideSegment.engineCommand)
		assertEquals(3L, outsideSegment.engineCommandKey)
		assertNull(outsideSegment.activeSegmentKey)
	}

	@Test
	fun playbackPositionInTimelineGapClearsOverlayWithoutMismatchRepairPrompt() {
		val timeline = whispersyncTimeline()
		val active = ReaderWhispersyncSyncState().onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_500
		)

		val gap = active.onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 4_000
		)

		assertEquals(ReaderEngineCommand.ClearMediaOverlay, gap.state.engineCommand)
		val status = assertNotNull(gap.status)
		assertEquals(ReaderWhispersyncStatusKind.NoActiveCue, status.kind)
		assertFalse(status.requiresAttention)
		assertFalse(status.repairable)
		assertNull(gap.state.activeSegmentKey)
	}

	@Test
	fun visibleTextRangePublishesSeekTargetAndSuppressesRepeatedSeekLoop() {
		val timeline = whispersyncTimeline()
		val active = ReaderWhispersyncSyncState().onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_500
		)

		val seek = active.onVisibleTextRange(
			timeline = timeline,
			textHref = "Text/chapter1.xhtml",
			visibleStart = 70,
			visibleEnd = 125
		)

		assertEquals("Audio/chapter01.m4b", seek.audioSeekTarget?.audioResource)
		assertEquals(5_000L, seek.audioSeekTarget?.positionMs)
		val seekCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(seek.state.engineCommand)
		assertEquals("seg-2", seekCommand.fragment.fragmentId)
		assertEquals("Second sentence", seekCommand.fragment.label)
		assertEquals(2L, seek.state.engineCommandKey)

		val repeated = seek.state.onVisibleTextRange(
			timeline = timeline,
			textHref = "/Text/chapter1.xhtml",
			visibleStart = 75,
			visibleEnd = 115
		)
		assertNull(repeated.audioSeekTarget)
		assertEquals(seek.state.engineCommandKey, repeated.state.engineCommandKey)
	}

	@Test
	fun textOffsetPublishesSeekTargetFromExplicitLongPressOnly() {
		val timeline = whispersyncTimeline()

		val seek = ReaderWhispersyncSyncState().onTextOffset(
			timeline = timeline,
			textHref = "/Text/chapter1.xhtml",
			textOffset = 95
		)

		assertEquals("Audio/chapter01.m4b", seek.audioSeekTarget?.audioResource)
		assertEquals(5_000L, seek.audioSeekTarget?.positionMs)
		assertEquals(ReaderWhispersyncStatusKind.SeekingAudio, seek.status?.kind)
		val command = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(seek.state.engineCommand)
		assertEquals("seg-2", command.fragment.fragmentId)
		assertEquals("Second sentence", command.fragment.label)
		assertEquals(1L, seek.state.engineCommandKey)

		val miss = seek.state.onTextOffset(
			timeline = timeline,
			textHref = "/Text/chapter1.xhtml",
			textOffset = 70
		)
		assertNull(miss.audioSeekTarget)
		assertEquals(seek.state.engineCommandKey, miss.state.engineCommandKey)
		assertEquals(ReaderWhispersyncStatusKind.Ready, miss.status?.kind)
	}

	@Test
	fun syncTogglePublishesClearOverlayCommandWhenActiveSegmentIsVisible() {
		val active = ReaderWhispersyncSyncState().onAudiobookPlaybackPosition(
			timeline = whispersyncTimeline(),
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_500
		)

		val disabled = active.setSyncEnabled(false)

		assertEquals(false, disabled.syncEnabled)
		assertNull(disabled.activeSegmentKey)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, disabled.engineCommand)
		assertEquals(2L, disabled.engineCommandKey)

		val suppressed = disabled.onAudiobookPlaybackPosition(
			timeline = whispersyncTimeline(),
			audioResource = "Audio/chapter01.m4b",
			positionMs = 5_500
		)
		assertEquals(disabled.engineCommand, suppressed.engineCommand)
		assertEquals(disabled.engineCommandKey, suppressed.engineCommandKey)
		assertEquals(false, suppressed.syncEnabled)
	}

	@Test
	fun pageBoundaryPausesAudioWhenPlaybackReachesLastOnPageSegment() {
		val timeline = pageBoundedTimeline()
		val visible = ReaderWhispersyncVisibleTextRange(
			textHref = "Text/chapter1.xhtml",
			visibleStart = 0,
			visibleEnd = 150
		)
		// Audio has crossed into segment c (off-page); the page boundary is b.endMs (8000).
		val step = ReaderWhispersyncSyncState().onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 9_500,
			visibleTextRange = visible
		)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, step.playbackCommand)
		assertEquals(true, step.state.pausedAtBoundary)
		assertEquals(ReaderWhispersyncStatusKind.PausedAtPageBoundary, step.status?.kind)
		assertEquals(false, step.activeSegment?.applyMediaOverlay)
	}

	@Test
	fun onPagePlaybackHighlightsInPlaceWithoutPausing() {
		val timeline = pageBoundedTimeline()
		val visible = ReaderWhispersyncVisibleTextRange(
			textHref = "Text/chapter1.xhtml",
			visibleStart = 0,
			visibleEnd = 150
		)
		val step = ReaderWhispersyncSyncState().onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_500,
			visibleTextRange = visible
		)
		assertNull(step.playbackCommand)
		assertEquals(false, step.state.pausedAtBoundary)
		assertEquals(ReaderWhispersyncStatusKind.Playing, step.status?.kind)
		assertIs<ReaderEngineCommand.ApplyMediaOverlay>(step.state.engineCommand)
	}

	@Test
	fun pageTurnWhilePausedAtBoundaryResumesAudioSeamlesslyWithoutSeeking() {
		val timeline = pageBoundedTimeline()
		val step = ReaderWhispersyncSyncState(pausedAtBoundary = true)
			.onVisibleTextRange(
				timeline = timeline,
				textHref = "Text/chapter1.xhtml",
				visibleStart = 190,
				visibleEnd = 270
			)
		// Seamless resume: Play from the paused position, no seek (so a split
		// sentence is not restarted from its beginning).
		assertNull(step.audioSeekTarget)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, step.playbackCommand)
		assertEquals(false, step.state.pausedAtBoundary)
	}

	@Test
	fun pageTurnWhileUserPausedSeeksButDoesNotResumeAudio() {
		val timeline = pageBoundedTimeline()
		val step = ReaderWhispersyncSyncState(pausedAtBoundary = false)
			.onVisibleTextRange(
				timeline = timeline,
				textHref = "Text/chapter1.xhtml",
				visibleStart = 190,
				visibleEnd = 270
			)
		assertNotNull(step.audioSeekTarget)
		assertNull(step.playbackCommand)
	}

	@Test
	fun audioBoundaryInterpolatesMidClipWhenSentenceSpansPageBreak() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "s",
					audioResource = "Audio/chapter01.m4b",
					startMs = 10_000,
					endMs = 20_000,
					textHref = "Text/chapter1.xhtml",
					textStart = 100,
					textEnd = 200,
					label = "Long sentence spanning pages"
				)
			)
		)
		// Visible page ends mid-sentence (char 150 of 100-200 → 50% of the clip).
		assertEquals(15_000L, timeline.audioBoundaryForVisibleTextRange("Text/chapter1.xhtml", 0, 150))
		// Whole sentence on page → boundary is the clip end (no interpolation).
		assertEquals(20_000L, timeline.audioBoundaryForVisibleTextRange("Text/chapter1.xhtml", 0, 200))
	}

	@Test
	fun splitSentencePausesAtInterpolatedPageBoundary() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "s",
					audioResource = "Audio/chapter01.m4b",
					startMs = 10_000,
					endMs = 20_000,
					textHref = "Text/chapter1.xhtml",
					textStart = 100,
					textEnd = 200,
					label = "Long sentence"
				)
			)
		)
		val visible = ReaderWhispersyncVisibleTextRange(
			textHref = "Text/chapter1.xhtml",
			visibleStart = 0,
			visibleEnd = 150
		)
		// Before the interpolated boundary (15000ms) → still playing, no pause.
		val before = ReaderWhispersyncSyncState().onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 14_000,
			visibleTextRange = visible
		)
		assertNull(before.playbackCommand)
		assertEquals(ReaderWhispersyncStatusKind.Playing, before.status?.kind)
		// At/after the interpolated boundary → pause mid-sentence.
		val at = ReaderWhispersyncSyncState().onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 15_500,
			visibleTextRange = visible
		)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, at.playbackCommand)
		assertEquals(true, at.state.pausedAtBoundary)
		assertEquals(ReaderWhispersyncStatusKind.PausedAtPageBoundary, at.status?.kind)
	}

	private fun pageBoundedTimeline(): WhispersyncTimeline =
		WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "a",
					audioResource = "Audio/chapter01.m4b",
					startMs = 1_250,
					endMs = 3_500,
					textHref = "Text/chapter1.xhtml",
					textStart = 10,
					textEnd = 42,
					label = "Opening sentence"
				),
				WhispersyncSegment(
					id = "b",
					audioResource = "Audio/chapter01.m4b",
					startMs = 5_000,
					endMs = 8_000,
					textHref = "Text/chapter1.xhtml",
					textStart = 80,
					textEnd = 140,
					label = "Second sentence"
				),
				WhispersyncSegment(
					id = "c",
					audioResource = "Audio/chapter01.m4b",
					startMs = 9_000,
					endMs = 11_000,
					textHref = "Text/chapter1.xhtml",
					textStart = 200,
					textEnd = 260,
					label = "Next page sentence"
				)
			)
		)

	private fun whispersyncTimeline(): WhispersyncTimeline =
		WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "a",
					audioResource = "Audio/chapter01.m4b",
					startMs = 1_250,
					endMs = 3_500,
					textHref = "Text/chapter1.xhtml",
					fragmentId = "seg-1",
					rangeCfi = "epubcfi(/6/2!/4/2,/1:0,/1:32)",
					textStart = 10,
					textEnd = 42,
					label = "Opening sentence"
				),
				WhispersyncSegment(
					id = "b",
					audioResource = "Audio/chapter01.m4b",
					startMs = 5_000,
					endMs = 8_000,
					textHref = "Text/chapter1.xhtml",
					fragmentId = "seg-2",
					rangeCfi = "epubcfi(/6/2!/4/4,/1:0,/1:24)",
					textStart = 80,
					textEnd = 140,
					label = "Second sentence"
				)
			)
		)
}
