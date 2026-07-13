package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderReadaloudSyncCoordinatorTest {
	@Test
	fun playbackPositionPublishesReaderCommandsWithStableDispatchKeys() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val initial = ReaderReadaloudSyncState()
		assertIs<ReaderOverlaySyncState>(initial)

		val first = initial.onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 1_500)
		)
		val firstCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(first.engineCommand)
		assertEquals("frag-1", firstCommand.fragment.fragmentId)
		assertEquals("First", firstCommand.fragment.label)
		assertEquals(1L, first.engineCommandKey)

		val duplicate = first.onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 2_000)
		)
		assertEquals(first.engineCommand, duplicate.engineCommand)
		assertEquals(first.engineCommandKey, duplicate.engineCommandKey)

		val outsideClip = duplicate.onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 9_500)
		)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, outsideClip.engineCommand)
		assertEquals(2L, outsideClip.engineCommandKey)
	}

	@Test
	fun readerNavigationPublishesSeekTargetAndSuppressesRepeatedSeekLoop() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val active = ReaderReadaloudSyncState().onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 1_500)
		)

		val seek = active.onReaderEvent(
			plan = plan,
			timeline = timeline,
			event = ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-2")
			)
		)

		assertEquals(0, seek.audioSeekTarget?.trackIndex)
		assertEquals("EPUB/Audio/chapter1.mp3", seek.audioSeekTarget?.audioResource)
		assertEquals(5_000L, seek.audioSeekTarget?.positionMs)
		val seekCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(seek.state.engineCommand)
		assertEquals("frag-2", seekCommand.fragment.fragmentId)
		assertEquals("Second", seekCommand.fragment.label)
		assertEquals(2L, seek.state.engineCommandKey)

		val repeated = seek.state.onReaderEvent(
			plan = plan,
			timeline = timeline,
			event = ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-2")
			)
		)
		assertNull(repeated.audioSeekTarget)
		assertEquals(seek.state.engineCommandKey, repeated.state.engineCommandKey)
	}

	@Test
	fun syncTogglePublishesClearOverlayCommandWhenActiveHighlightIsVisible() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val active = ReaderReadaloudSyncState().onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 1_500)
		)

		val disabled = active.setSyncEnabled(false)

		assertEquals(false, disabled.syncEnabled)
		assertNull(disabled.activeCueKey)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, disabled.engineCommand)
		assertEquals(2L, disabled.engineCommandKey)
	}

	private fun mediaOverlayTimeline(): MediaOverlayTimeline =
		MediaOverlayTimeline(
			clips = listOf(
				MediaOverlayClip(
					audioResource = "EPUB/Audio/chapter1.mp3",
					textResource = "EPUB/Text/chapter1.xhtml",
					fragmentId = "frag-1",
					startSeconds = 1.25,
					endSeconds = 3.5,
					label = "First"
				),
				MediaOverlayClip(
					audioResource = "EPUB/Audio/chapter1.mp3",
					textResource = "EPUB/Text/chapter1.xhtml",
					fragmentId = "frag-2",
					startSeconds = 5.0,
					endSeconds = 8.0,
					label = "Second"
				)
			)
		)

	private fun readaloudPlaybackPlan(): ReadaloudPlaybackPlan =
		ReadaloudPlaybackPlan(
			sessionId = "book-1",
			title = "Storyteller Book",
			kind = ReaderPublicationKind.Readaloud,
			mediaItems = listOf(
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:chapter1",
					uri = "EPUB/Audio/chapter1.mp3",
					title = "Chapter 1",
					subtitle = null,
					artist = "Narrator",
					albumTitle = "Storyteller Book",
					albumArtist = "Author",
					trackNumber = 1,
					discNumber = null,
					requestHeaders = emptyMap()
				)
			),
			startTrackIndex = 0,
			startPositionMs = 0L,
			playbackSpeed = 1f
		)

	private fun playbackPosition(positionMs: Long): ReadaloudPlaybackPosition =
		ReadaloudPlaybackPosition(
			sessionId = "book-1",
			trackIndex = 0,
			mediaId = "readaloud:chapter1",
			positionMs = positionMs,
			durationMs = 10_000L,
			isPlaying = true,
			playbackSpeed = 1f
		)
}
