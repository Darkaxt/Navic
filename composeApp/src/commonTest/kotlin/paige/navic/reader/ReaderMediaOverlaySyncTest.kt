package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderMediaOverlaySyncTest {
	@Test
	fun playbackPositionHighlightsActiveClipOnlyWhenClipChanges() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val initialState = ReaderMediaOverlaySyncState(syncEnabled = true)

		val first = initialState.onReadaloudPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 1_500)
		)
		val firstCommand = assertIs<ReaderBridgeCommand.ApplyOverlayFragment>(first.readerCommand)
		assertEquals("frag-1", firstCommand.fragment.fragmentId)

		val duplicate = first.state.onReadaloudPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 2_000)
		)
		assertNull(duplicate.readerCommand)
		assertEquals(first.state, duplicate.state)

		val second = duplicate.state.onReadaloudPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 5_500)
		)
		val secondCommand = assertIs<ReaderBridgeCommand.ApplyOverlayFragment>(second.readerCommand)
		assertEquals("frag-2", secondCommand.fragment.fragmentId)

		val outsideClip = second.state.onReadaloudPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 9_500)
		)
		assertEquals(ReaderBridgeCommand.ClearOverlay, outsideClip.readerCommand)
		assertNull(outsideClip.state.activeClipKey)
	}

	@Test
	fun readerNavigationToSyncedFragmentBuildsMedia3SeekTargetAndSuppressesOverlayLoop() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val active = ReaderMediaOverlaySyncState(syncEnabled = true)
			.onReadaloudPlaybackPosition(
				plan = plan,
				timeline = timeline,
				position = playbackPosition(positionMs = 1_500)
			)
			.state

		assertNull(
			active.audioSeekTargetForReaderEvent(
				plan = plan,
				timeline = timeline,
				event = ReaderBridgeEvent.LocationChanged(
					locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-1")
				)
			)
		)

		val seek = active.audioSeekTargetForReaderEvent(
			plan = plan,
			timeline = timeline,
			event = ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-2")
			)
		)
		assertEquals(0, seek?.trackIndex)
		assertEquals("EPUB/Audio/chapter1.mp3", seek?.audioResource)
		assertEquals(5_000, seek?.positionMs)

		assertNull(
			active.copy(syncEnabled = false).audioSeekTargetForReaderEvent(
				plan = plan,
				timeline = timeline,
				event = ReaderBridgeEvent.LocationChanged(
					locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-2")
				)
			)
		)
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
