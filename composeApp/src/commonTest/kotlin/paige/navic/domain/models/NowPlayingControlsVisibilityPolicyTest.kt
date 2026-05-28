package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingControlsVisibilityPolicyTest {
	@Test
	fun defaultControlsKeepCurrentButtonOrder() {
		assertEquals(
			listOf(
				NowPlayingPlaybackControl.Shuffle,
				NowPlayingPlaybackControl.Previous,
				NowPlayingPlaybackControl.PlayPause,
				NowPlayingPlaybackControl.Next,
				NowPlayingPlaybackControl.Repeat
			),
			nowPlayingPlaybackControls(
				showShuffleControl = true,
				showRepeatControl = true
			)
		)
	}

	@Test
	fun shuffleControlCanBeHiddenWithoutMovingCoreTransportControls() {
		assertEquals(
			listOf(
				NowPlayingPlaybackControl.Previous,
				NowPlayingPlaybackControl.PlayPause,
				NowPlayingPlaybackControl.Next,
				NowPlayingPlaybackControl.Repeat
			),
			nowPlayingPlaybackControls(
				showShuffleControl = false,
				showRepeatControl = true
			)
		)
	}

	@Test
	fun repeatControlCanBeHiddenWithoutMovingCoreTransportControls() {
		assertEquals(
			listOf(
				NowPlayingPlaybackControl.Shuffle,
				NowPlayingPlaybackControl.Previous,
				NowPlayingPlaybackControl.PlayPause,
				NowPlayingPlaybackControl.Next
			),
			nowPlayingPlaybackControls(
				showShuffleControl = true,
				showRepeatControl = false
			)
		)
	}

	@Test
	fun bothEdgeControlsCanBeHidden() {
		assertEquals(
			listOf(
				NowPlayingPlaybackControl.Previous,
				NowPlayingPlaybackControl.PlayPause,
				NowPlayingPlaybackControl.Next
			),
			nowPlayingPlaybackControls(
				showShuffleControl = false,
				showRepeatControl = false
			)
		)
	}
}
