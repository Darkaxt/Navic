package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingControlsLayoutPolicyTest {
	@Test
	fun defaultLayoutKeepsTimelineAbovePlaybackButtons() {
		assertEquals(
			listOf(
				NowPlayingControlsLayoutBlock.Timeline,
				NowPlayingControlsLayoutBlock.PlaybackButtons
			),
			nowPlayingControlsLayoutBlocks(swapControlsAndTimeline = false)
		)
	}

	@Test
	fun swappedLayoutMovesPlaybackButtonsAboveTimeline() {
		assertEquals(
			listOf(
				NowPlayingControlsLayoutBlock.PlaybackButtons,
				NowPlayingControlsLayoutBlock.Timeline
			),
			nowPlayingControlsLayoutBlocks(swapControlsAndTimeline = true)
		)
	}
}
