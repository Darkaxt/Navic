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
	fun technicalInfoRendersBetweenPlaybackButtonsAndTimelineInDefaultLayout() {
		assertEquals(
			listOf(
				NowPlayingControlsLayoutBlock.Timeline,
				NowPlayingControlsLayoutBlock.TechnicalInfo,
				NowPlayingControlsLayoutBlock.PlaybackButtons
			),
			nowPlayingControlsLayoutBlocks(
				swapControlsAndTimeline = false,
				showTechnicalInfo = true
			)
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
	fun technicalInfoRendersBetweenPlaybackButtonsAndTimelineInSwappedLayout() {
		assertEquals(
			listOf(
				NowPlayingControlsLayoutBlock.PlaybackButtons,
				NowPlayingControlsLayoutBlock.TechnicalInfo,
				NowPlayingControlsLayoutBlock.Timeline
			),
			nowPlayingControlsLayoutBlocks(
				swapControlsAndTimeline = true,
				showTechnicalInfo = true
			)
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
	fun defaultPlaybackSpeedDoesNotNeedPlayButtonLabel() {
		assertEquals(
			null,
			nowPlayingPlayButtonSpeedLabel(playbackSpeed = 1.0f)
		)
	}

	@Test
	fun nonDefaultPlaybackSpeedsShowOneDecimalPlayButtonLabel() {
		assertEquals(
			"1.5x",
			nowPlayingPlayButtonSpeedLabel(playbackSpeed = 1.5f)
		)
		assertEquals(
			"0.8x",
			nowPlayingPlayButtonSpeedLabel(playbackSpeed = 0.75f)
		)
		assertEquals(
			"2.0x",
			nowPlayingPlayButtonSpeedLabel(playbackSpeed = 2.0f)
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

	@Test
	fun longPressPlayButtonForPlaybackSpeedRequiresCurrentSong() {
		assertEquals(
			false,
			shouldOpenPlaybackSpeedFromNowPlayingPlayButton(hasCurrentSong = false)
		)
		assertEquals(
			true,
			shouldOpenPlaybackSpeedFromNowPlayingPlayButton(hasCurrentSong = true)
		)
	}
}
