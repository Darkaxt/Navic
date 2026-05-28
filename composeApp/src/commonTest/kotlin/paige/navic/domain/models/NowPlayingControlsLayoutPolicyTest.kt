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

	@Test
	fun defaultPlaybackButtonsUseCompactSpacing() {
		assertEquals(
			NowPlayingPlaybackButtonsArrangement.Compact,
			nowPlayingPlaybackButtonsArrangement(spaceControlsEvenly = false)
		)
	}

	@Test
	fun playbackButtonsCanUseEvenSpacing() {
		assertEquals(
			NowPlayingPlaybackButtonsArrangement.EvenlySpaced,
			nowPlayingPlaybackButtonsArrangement(spaceControlsEvenly = true)
		)
	}

	@Test
	fun tapControlsForQueueRequiresSettingAndCurrentSong() {
		assertEquals(
			false,
			shouldOpenQueueFromNowPlayingControlsTap(
				enabled = false,
				hasCurrentSong = true
			)
		)
		assertEquals(
			false,
			shouldOpenQueueFromNowPlayingControlsTap(
				enabled = true,
				hasCurrentSong = false
			)
		)
		assertEquals(
			true,
			shouldOpenQueueFromNowPlayingControlsTap(
				enabled = true,
				hasCurrentSong = true
			)
		)
	}
}
