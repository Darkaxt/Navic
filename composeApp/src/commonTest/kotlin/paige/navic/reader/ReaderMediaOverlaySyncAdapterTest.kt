package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderMediaOverlaySyncAdapterTest {
	@Test
	fun playbackPositionHighlightsActiveClipOnlyWhenClipChanges() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val adapter = MediaOverlaySyncAdapter(plan, timeline)
		val initialState = ReaderOverlaySyncState(syncEnabled = true)

		val first = initialState.followPlaybackCue(
			adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 1_500)))
		)
		val firstCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(first.engineCommand)
		assertEquals("frag-1", firstCommand.fragment.fragmentId)
		assertEquals("First", firstCommand.fragment.label)

		val duplicate = first.followPlaybackCue(
			adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 2_000)))
		)
		assertEquals(first.engineCommand, duplicate.engineCommand)
		assertEquals(first.engineCommandKey, duplicate.engineCommandKey)

		val second = duplicate.followPlaybackCue(
			adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 5_500)))
		)
		val secondCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(second.engineCommand)
		assertEquals("frag-2", secondCommand.fragment.fragmentId)
		assertEquals("Second", secondCommand.fragment.label)

		val outsideClip = second.followPlaybackCue(
			adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 9_500)))
		)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, outsideClip.engineCommand)
		assertNull(outsideClip.activeCueKey)
	}

	@Test
	fun readerNavigationToSyncedFragmentBuildsMedia3SeekTargetAndSuppressesOverlayLoop() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val adapter = MediaOverlaySyncAdapter(plan, timeline)
		val active = ReaderOverlaySyncState(syncEnabled = true)
			.followPlaybackCue(
				adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 1_500)))
			)

		val repeated = active.followReaderTarget(
			adapter.readerTarget(
				ReaderBridgeEvent.LocationChanged(
					locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-1")
				)
			)
		)
		assertNull(repeated.seekTarget)

		val seek = active.followReaderTarget(
			adapter.readerTarget(
				ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-2")
			)
			)
		)
		assertEquals(0, seek.seekTarget?.trackIndex)
		assertEquals("EPUB/Audio/chapter1.mp3", seek.seekTarget?.audioResource)
		assertEquals(5_000, seek.seekTarget?.positionMs)
		assertEquals("Second", seek.seekTarget?.clip?.toReaderOverlayFragment()?.label)

		val disabled = active.setSyncEnabled(false).followReaderTarget(
			adapter.readerTarget(
				ReaderBridgeEvent.LocationChanged(
					locator = ReaderLocator(href = "EPUB/Text/chapter1.xhtml#frag-2")
				)
			)
		)
		assertNull(disabled.seekTarget)
	}

	@Test
	fun togglingSyncOffClearsActiveOverlayAndSuppressesLaterHighlightCommands() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val adapter = MediaOverlaySyncAdapter(plan, timeline)
		val active = ReaderOverlaySyncState(syncEnabled = true)
			.followPlaybackCue(
				adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 1_500)))
			)

		val disabled = active.setSyncEnabled(false)

		assertFalse(disabled.syncEnabled)
		assertNull(disabled.activeCueKey)
		assertEquals(ReaderEngineCommand.ClearMediaOverlay, disabled.engineCommand)

		val suppressed = disabled.followPlaybackCue(
			adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 5_500)))
		)
		assertEquals(disabled.engineCommand, suppressed.engineCommand)
		assertFalse(suppressed.syncEnabled)

		val enabled = suppressed.setSyncEnabled(true)
		assertTrue(enabled.syncEnabled)
		assertEquals(suppressed.engineCommand, enabled.engineCommand)

		val resumed = enabled.followPlaybackCue(
			adapter.playbackCue(MediaOverlayPlaybackInput(playbackPosition(positionMs = 5_500)))
		)
		val resumedCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(resumed.engineCommand)
		assertEquals("frag-2", resumedCommand.fragment.fragmentId)
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
