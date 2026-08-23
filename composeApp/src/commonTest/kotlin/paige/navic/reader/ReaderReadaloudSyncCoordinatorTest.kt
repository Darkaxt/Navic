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
	fun validatedNavigationAndExplicitSelectionCanSeek() {
		val timeline = mediaOverlayTimeline()
		val plan = readaloudPlaybackPlan()
		val active = ReaderReadaloudSyncState().onPlaybackPosition(
			plan = plan,
			timeline = timeline,
			position = playbackPosition(positionMs = 1_500)
		)

		val navigated = active.onReaderInteraction(
			plan = plan,
			timeline = timeline,
			interaction = ReaderReadaloudReaderInteraction.UserNavigation(
				textHref = "EPUB/Text/chapter1.xhtml#frag-2",
				causalSequence = 17L
			)
		)
		assertEquals(5_000L, navigated.audioSeekTarget?.positionMs)
		assertEquals(17L, navigated.consumedUserNavigationCausalSequence)

		val selected = navigated.state.onReaderInteraction(
			plan = plan,
			timeline = timeline,
			interaction = ReaderReadaloudReaderInteraction.ExplicitSelection(
				textHref = "EPUB/Text/chapter1.xhtml#frag-1"
			)
		)
		assertEquals(0, selected.audioSeekTarget?.trackIndex)
		assertEquals("EPUB/Audio/chapter1.mp3", selected.audioSeekTarget?.audioResource)
		assertEquals(1_250L, selected.audioSeekTarget?.positionMs)
		val seekCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(selected.state.engineCommand)
		assertEquals("frag-1", seekCommand.fragment.fragmentId)
		assertEquals("First", seekCommand.fragment.label)
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
