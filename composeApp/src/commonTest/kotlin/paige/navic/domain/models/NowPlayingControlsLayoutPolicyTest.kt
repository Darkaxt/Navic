package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingControlsLayoutPolicyTest {
	@Test
	fun wideLandscapeLayoutRequiresBothLandscapeAndTabletWidth() {
		assertEquals(
			true,
			shouldUseWideNowPlayingLandscapeLayout(
				widthDp = 1000,
				heightDp = 640
			)
		)
		assertEquals(
			false,
			shouldUseWideNowPlayingLandscapeLayout(
				widthDp = 820,
				heightDp = 500
			)
		)
		assertEquals(
			false,
			shouldUseWideNowPlayingLandscapeLayout(
				widthDp = 1000,
				heightDp = 1200
			)
		)
	}

	@Test
	fun wideLandscapeContentUsesCenteredProgressColumn() {
		assertEquals(
			NowPlayingWideLandscapeContentLayout(
				progressWidthDp = 760,
				upNextWidthDp = 440
			),
			nowPlayingWideLandscapeContentLayout(
				contentPaneWidthDp = 900
			)
		)
		assertEquals(
			NowPlayingWideLandscapeContentLayout(
				progressWidthDp = 560,
				upNextWidthDp = 420
			),
			nowPlayingWideLandscapeContentLayout(
				contentPaneWidthDp = 560
			)
		)
	}

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
	fun technicalInfoDoesNotAddSeparateBlockInDefaultLayout() {
		assertEquals(
			listOf(
				NowPlayingControlsLayoutBlock.Timeline,
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
	fun technicalInfoDoesNotAddSeparateBlockInSwappedLayout() {
		assertEquals(
			listOf(
				NowPlayingControlsLayoutBlock.PlaybackButtons,
				NowPlayingControlsLayoutBlock.Timeline
			),
			nowPlayingControlsLayoutBlocks(
				swapControlsAndTimeline = true,
				showTechnicalInfo = true
			)
		)
	}

	@Test
	fun technicalInfoOverlaysOnlyBetweenTimelineAndPlaybackButtons() {
		assertEquals(
			true,
			shouldOverlayTechnicalInfoBetween(
				NowPlayingControlsLayoutBlock.PlaybackButtons,
				NowPlayingControlsLayoutBlock.Timeline
			)
		)
		assertEquals(
			true,
			shouldOverlayTechnicalInfoBetween(
				NowPlayingControlsLayoutBlock.Timeline,
				NowPlayingControlsLayoutBlock.PlaybackButtons
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
