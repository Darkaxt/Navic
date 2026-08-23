package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWhispersyncSyncCoordinatorTest {
	@Test
	fun typedAdapterResolvesProgressiveCueAndReaderRepeatPolicies() {
		val adapter = WhispersyncOverlaySyncAdapter(whispersyncTimeline())

		val playbackCue = assertNotNull(
			adapter.playbackCue(
				WhispersyncPlaybackSyncInput(
					audioResource = "Audio/chapter01.m4b",
					positionMs = 1_500
				)
			)
		)
		assertEquals("seg-1", playbackCue.fragment.fragmentId)
		assertEquals(14, playbackCue.progressTextEnd)

		val visibleRange = assertNotNull(
			adapter.readerTarget(
				WhispersyncReaderSyncInput.VisibleRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 70,
					visibleEnd = 125
				)
			)
		)
		assertFalse(visibleRange.repeatSeek)
		assertEquals(5_000L, visibleRange.seekTarget.positionMs)

		val textPoint = assertNotNull(
			adapter.readerTarget(
				WhispersyncReaderSyncInput.TextPoint(
					textHref = "Text/chapter1.xhtml",
					textOffset = 90
				)
			)
		)
		assertTrue(textPoint.repeatSeek)
		assertEquals(5_000L, textPoint.seekTarget.positionMs)
	}

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
		assertEquals(1L, first.engineCommandKey)

		val confirmedFirst = first.confirmOverlay(first.activeOverlayRequestId)
		val duplicate = confirmedFirst.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "/Audio/chapter01.m4b",
			positionMs = 2_000
		)
		val progressCommand = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(duplicate.engineCommand)
		assertEquals("seg-1", progressCommand.fragment.fragmentId)
		assertEquals(21, progressCommand.fragment.textProgressEnd)
		assertEquals(2L, duplicate.engineCommandKey)

		val outsideSegment = duplicate.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 9_500
		)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, outsideSegment.engineCommand)
		assertEquals(3L, outsideSegment.engineCommandKey)
		assertNull(outsideSegment.activeCueKey)
	}

	@Test
	fun playbackPositionGraduallyUpdatesActiveCueTextProgress() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "cue-progress",
					audioResource = "Audio/chapter01.m4b",
					startMs = 1_000,
					endMs = 3_000,
					textHref = "Text/chapter1.xhtml",
					fragmentId = "sentence-1",
					textStart = 100,
					textEnd = 140,
					label = "Progress sentence"
				)
			)
		)

		val start = ReaderWhispersyncSyncState().onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_000
		)
		val startCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(start.engineCommand)
		assertEquals(100, startCommand.fragment.textProgressEnd)
		assertEquals(0.0, startCommand.fragment.textProgressFraction ?: -1.0, 0.0001)
		assertEquals(1L, start.engineCommandKey)

		val confirmedStart = start.confirmOverlay(start.activeOverlayRequestId)
		val quarter = confirmedStart.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_500
		)
		val quarterCommand = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(quarter.engineCommand)
		assertEquals(110, quarterCommand.fragment.textProgressEnd)
		assertEquals(0.25, quarterCommand.fragment.textProgressFraction ?: -1.0, 0.0001)
		assertEquals(2L, quarter.engineCommandKey)

		val sameCharacter = quarter.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_520
		)
		assertEquals(quarter.engineCommand, sameCharacter.engineCommand)
		assertEquals(quarter.engineCommandKey, sameCharacter.engineCommandKey)

		val complete = sameCharacter.onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 2_999
		)
		val completeCommand = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(complete.engineCommand)
		assertEquals(140, completeCommand.fragment.textProgressEnd)
		assertEquals(0.9995, completeCommand.fragment.textProgressFraction ?: -1.0, 0.0001)
		assertEquals(3L, complete.engineCommandKey)

		val textSeek = complete.onTextPoint(
			timeline = timeline,
			textHref = "Text/chapter1.xhtml",
			textOffset = 120
		)
		val textSeekCommand = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(textSeek.state.engineCommand)
		assertEquals(100, textSeekCommand.fragment.textProgressEnd)
		assertEquals(4L, textSeek.state.engineCommandKey)
	}

	@Test
	fun playbackPositionUsesLeadOnlyForVisualHighlightProgress() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "cue-lead",
					audioResource = "Audio/chapter01.m4b",
					startMs = 1_000,
					endMs = 5_000,
					textHref = "Text/chapter1.xhtml",
					fragmentId = "sentence-lead",
					textStart = 200,
					textEnd = 240,
					label = "Lead sentence"
				)
			)
		)

		val start = ReaderWhispersyncSyncState().onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_000,
			highlightLeadMs = 1_000
		)
		val startCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(start.state.engineCommand)
		assertEquals(210, startCommand.fragment.textProgressEnd)
		assertEquals(0.25, startCommand.fragment.textProgressFraction ?: -1.0, 0.0001)
		assertEquals(ReaderWhispersyncStatusKind.Playing, start.status?.kind)
		assertEquals(1_000L, start.status?.positionMs)

		val confirmedStart = start.state.confirmOverlay(
			start.state.activeOverlayRequestId
		)
		val nearEnd = confirmedStart.onAudiobookPlaybackPositionStep(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 4_500,
			highlightLeadMs = 1_000
		)
		val nearEndCommand = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(nearEnd.state.engineCommand)
		assertEquals(240, nearEndCommand.fragment.textProgressEnd)
		assertEquals(1.0, nearEndCommand.fragment.textProgressFraction ?: -1.0, 0.0001)
		assertEquals(4_500L, nearEnd.status?.positionMs)
	}

	@Test
	fun playbackPositionIncludesNextCueHintForWebHighlightClamping() {
		val timeline = WhispersyncTimeline(
			segments = listOf(
				WhispersyncSegment(
					id = "cue-1",
					audioResource = "Audio/chapter01.m4b",
					startMs = 0,
					endMs = 18_040,
					textHref = "Text/authorsforeword.xhtml",
					textStart = 0,
					textEnd = 78,
					spokenText = "I am not a good person.",
					ebookText = "Alcatraz Versus the Evil Librarian AUTHOR’S FOREWORD. I AM NOT A GOOD PERSON"
				),
				WhispersyncSegment(
					id = "cue-2",
					audioResource = "Audio/chapter01.m4b",
					startMs = 18_040,
					endMs = 21_440,
					textHref = "Text/authorsforeword.xhtml",
					textStart = 81,
					textEnd = 121,
					spokenText = "Oh, I know what the stories say about me.",
					ebookText = "OH, I KNOW WHAT THE STORIES SAY ABOUT ME"
				)
			)
		)

		val state = ReaderWhispersyncSyncState().onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_000
		)

		val command = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(state.engineCommand)
		assertEquals("Text/authorsforeword.xhtml", command.fragment.nextTextHref)
		assertEquals(81, command.fragment.nextTextStart)
		assertEquals(121, command.fragment.nextTextEnd)
		assertEquals("OH, I KNOW WHAT THE STORIES SAY ABOUT ME", command.fragment.nextEbookText)
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
		assertNull(gap.state.activeCueKey)
	}

	@Test
	fun visibleTextRangeResolvesPreparedTargetWithoutMutatingPlaybackSyncState() {
		val timeline = whispersyncTimeline()
		val active = ReaderWhispersyncSyncState().onAudiobookPlaybackPosition(
			timeline = timeline,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 1_500
		)

		val prepared = assertNotNull(
			readerWhispersyncVisibleTarget(
				timeline = timeline,
				textHref = "Text/chapter1.xhtml",
				visibleStart = 70,
				visibleEnd = 125
			)
		)

		assertEquals("Audio/chapter01.m4b", prepared.seekTarget.audioResource)
		assertEquals(5_000L, prepared.seekTarget.positionMs)
		assertEquals("seg-2", prepared.cue.fragment.fragmentId)
		assertEquals("Second sentence", prepared.cue.fragment.label)
		assertEquals(1L, active.engineCommandKey)
		assertEquals("seg-1", active.activeCueKey?.let { active.engineCommand.overlayFragmentOrNull()?.fragmentId })
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
		assertNull(disabled.activeCueKey)
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
