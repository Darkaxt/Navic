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

		val first = initial.onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 1_500)
		)
		val firstCommand = assertIs<ReaderBridgeCommand.ApplyOverlayFragment>(first.readerCommand)
		assertEquals("frag-1", firstCommand.fragment.fragmentId)
		assertEquals(1L, first.readerCommandKey)

		val duplicate = first.onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 2_000)
		)
		assertEquals(first.readerCommand, duplicate.readerCommand)
		assertEquals(first.readerCommandKey, duplicate.readerCommandKey)

		val outsideClip = duplicate.onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 9_500)
		)
		assertEquals(ReaderBridgeCommand.ClearOverlay, outsideClip.readerCommand)
		assertEquals(2L, outsideClip.readerCommandKey)
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
		val seekCommand = assertIs<ReaderBridgeCommand.ApplyOverlayFragment>(seek.state.readerCommand)
		assertEquals("frag-2", seekCommand.fragment.fragmentId)
		assertEquals(2L, seek.state.readerCommandKey)

		val repeated = seek.state.onReaderEvent(
			plan = plan,
			timeline = timeline,
			event = ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-2")
			)
		)
		assertNull(repeated.audioSeekTarget)
		assertEquals(seek.state.readerCommandKey, repeated.state.readerCommandKey)
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
