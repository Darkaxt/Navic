package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingProgressWidth
import paige.navic.domain.models.settings.NowPlayingSliderStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingProgressWidthPolicyTest {
	@Test
	fun biggestWidthPreservesCurrentPerSliderPadding() {
		assertEquals(
			16,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Flat,
				progressWidth = NowPlayingProgressWidth.Biggest
			)
		)
		assertEquals(
			14,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Squiggly,
				progressWidth = NowPlayingProgressWidth.Biggest
			)
		)
		assertEquals(
			7,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Yoyo,
				progressWidth = NowPlayingProgressWidth.Biggest
			)
		)
		assertEquals(
			16,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Slim,
				progressWidth = NowPlayingProgressWidth.Biggest
			)
		)
	}

	@Test
	fun nonDefaultWidthsFollowKreateTimelinePaddingScale() {
		assertEquals(
			90,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Squiggly,
				progressWidth = NowPlayingProgressWidth.Small
			)
		)
		assertEquals(
			55,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Squiggly,
				progressWidth = NowPlayingProgressWidth.Medium
			)
		)
		assertEquals(
			30,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Squiggly,
				progressWidth = NowPlayingProgressWidth.Big
			)
		)
	}

	@Test
	fun expandedWidthRemovesHorizontalPadding() {
		assertEquals(
			0,
			nowPlayingProgressHorizontalPaddingDp(
				sliderStyle = NowPlayingSliderStyle.Yoyo,
				progressWidth = NowPlayingProgressWidth.Expanded
			)
		)
	}
}
