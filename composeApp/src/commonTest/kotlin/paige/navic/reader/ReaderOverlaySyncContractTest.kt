package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReaderOverlaySyncContractTest {
	@Test
	fun bothTimelineAdaptersHonorTheOverlaySyncContract() {
		overlaySyncHarnesses().forEach { harness ->
			val first = harness.followFirstPlayback(ReaderOverlaySyncState())
			assertIs<ReaderEngineCommand.ApplyMediaOverlay>(first.engineCommand, harness.name)

			val repeated = harness.followRepeatedPlayback(first)
			assertEquals(first.engineCommandKey, repeated.engineCommandKey, harness.name)

			val second = harness.followSecondPlayback(repeated)
			assertEquals(first.engineCommandKey + 1L, second.engineCommandKey, harness.name)

			val cleared = harness.followOutsidePlayback(second)
			assertEquals(ReaderEngineCommand.ClearMediaOverlay, cleared.engineCommand, harness.name)

			val reader = harness.followReaderTarget(ReaderOverlaySyncState())
			assertNotNull(reader.seekTarget, harness.name)

			val repeatedReader = harness.followRepeatedReaderTarget(reader.state)
			assertNull(repeatedReader.seekTarget, harness.name)
			assertEquals(reader.state.engineCommandKey, repeatedReader.state.engineCommandKey, harness.name)
		}
	}

	@Test
	fun bothTimelineAdaptersClearOnceAndIgnorePlaybackWhileDisabled() {
		overlaySyncHarnesses().forEach { harness ->
			val active = harness.followFirstPlayback(ReaderOverlaySyncState())
			val disabled = active.setSyncEnabled(false)
			assertEquals(ReaderEngineCommand.ClearMediaOverlay, disabled.engineCommand, harness.name)
			assertEquals(active.engineCommandKey + 1L, disabled.engineCommandKey, harness.name)

			val disabledAgain = disabled.setSyncEnabled(false)
			assertEquals(disabled.engineCommandKey, disabledAgain.engineCommandKey, harness.name)

			val ignored = harness.followSecondPlayback(disabledAgain)
			assertEquals(disabledAgain, ignored, harness.name)
		}
	}

	private fun overlaySyncHarnesses(): List<OverlaySyncHarness> =
		listOf(whispersyncHarness(), mediaOverlayHarness())

	private fun whispersyncHarness(): OverlaySyncHarness {
		val adapter = WhispersyncOverlaySyncAdapter(whispersyncTimeline())
		fun playbackCue(positionMs: Long): ReaderOverlayCue? =
			adapter.playbackCue(
				WhispersyncPlaybackSyncInput(
					audioResource = "Audio/chapter01.m4b",
					positionMs = positionMs
				)
			)
		fun readerTarget(): ReaderOverlayReaderTarget<*>? =
			adapter.readerTarget(
				WhispersyncReaderSyncInput.VisibleRange(
					textHref = "Text/chapter1.xhtml",
					visibleStart = 80,
					visibleEnd = 120
				)
			)

		return object : OverlaySyncHarness {
			override val name = "Whispersync"
			override fun followFirstPlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(1_500))
			override fun followRepeatedPlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(1_500))
			override fun followSecondPlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(5_500))
			override fun followOutsidePlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(9_500))
			override fun followReaderTarget(state: ReaderOverlaySyncState) =
				state.followReaderTarget(readerTarget())
			override fun followRepeatedReaderTarget(state: ReaderOverlaySyncState) =
				state.followReaderTarget(readerTarget())
		}
	}

	private fun mediaOverlayHarness(): OverlaySyncHarness {
		val adapter = MediaOverlaySyncAdapter(readaloudPlaybackPlan(), mediaOverlayTimeline())
		fun playbackCue(trackIndex: Int, positionMs: Long): ReaderOverlayCue? =
			adapter.playbackCue(
				MediaOverlayPlaybackInput(
					ReadaloudPlaybackPosition(
						sessionId = "book-1",
						trackIndex = trackIndex,
						mediaId = "readaloud:chapter${trackIndex + 1}",
						positionMs = positionMs,
						durationMs = 10_000,
						isPlaying = true,
						playbackSpeed = 1f
					)
				)
			)
		fun readerTarget(): ReaderOverlayReaderTarget<*>? =
			adapter.readerTarget(
				ReaderBridgeEvent.LocationChanged(
					locator = ReaderLocator(href = "EPUB/Text/chapter2.xhtml#frag-2")
				)
			)

		return object : OverlaySyncHarness {
			override val name = "EPUB media overlay"
			override fun followFirstPlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(trackIndex = 0, positionMs = 1_500))
			override fun followRepeatedPlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(trackIndex = 0, positionMs = 1_500))
			override fun followSecondPlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(trackIndex = 1, positionMs = 5_500))
			override fun followOutsidePlayback(state: ReaderOverlaySyncState) =
				state.followPlaybackCue(playbackCue(trackIndex = 1, positionMs = 9_500))
			override fun followReaderTarget(state: ReaderOverlaySyncState) =
				state.followReaderTarget(readerTarget())
			override fun followRepeatedReaderTarget(state: ReaderOverlaySyncState) =
				state.followReaderTarget(readerTarget())
		}
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
					textStart = 10,
					textEnd = 42
				),
				WhispersyncSegment(
					id = "b",
					audioResource = "Audio/chapter01.m4b",
					startMs = 5_000,
					endMs = 8_000,
					textHref = "Text/chapter1.xhtml",
					fragmentId = "seg-2",
					textStart = 80,
					textEnd = 140
				)
			)
		)

	private fun mediaOverlayTimeline(): MediaOverlayTimeline =
		MediaOverlayTimeline(
			clips = listOf(
				MediaOverlayClip(
					audioResource = "EPUB/Audio/chapter1.mp3",
					textResource = "EPUB/Text/chapter1.xhtml",
					fragmentId = "frag-1",
					startSeconds = 1.25,
					endSeconds = 3.5
				),
				MediaOverlayClip(
					audioResource = "EPUB/Audio/chapter2.mp3",
					textResource = "EPUB/Text/chapter2.xhtml",
					fragmentId = "frag-2",
					startSeconds = 5.0,
					endSeconds = 8.0
				)
			)
		)

	private fun readaloudPlaybackPlan(): ReadaloudPlaybackPlan =
		ReadaloudPlaybackPlan(
			sessionId = "book-1",
			title = "Storyteller Book",
			kind = ReaderPublicationKind.Readaloud,
			mediaItems = listOf(1, 2).map { chapter ->
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:chapter$chapter",
					uri = "EPUB/Audio/chapter$chapter.mp3",
					title = "Chapter $chapter",
					subtitle = null,
					artist = "Narrator",
					albumTitle = "Storyteller Book",
					albumArtist = "Author",
					trackNumber = chapter,
					discNumber = null,
					requestHeaders = emptyMap()
				)
			},
			startTrackIndex = 0,
			startPositionMs = 0,
			playbackSpeed = 1f
		)

	private interface OverlaySyncHarness {
		val name: String
		fun followFirstPlayback(state: ReaderOverlaySyncState): ReaderOverlaySyncState
		fun followRepeatedPlayback(state: ReaderOverlaySyncState): ReaderOverlaySyncState
		fun followSecondPlayback(state: ReaderOverlaySyncState): ReaderOverlaySyncState
		fun followOutsidePlayback(state: ReaderOverlaySyncState): ReaderOverlaySyncState
		fun followReaderTarget(state: ReaderOverlaySyncState): ReaderOverlayReaderStep<*>
		fun followRepeatedReaderTarget(state: ReaderOverlaySyncState): ReaderOverlayReaderStep<*>
	}
}
