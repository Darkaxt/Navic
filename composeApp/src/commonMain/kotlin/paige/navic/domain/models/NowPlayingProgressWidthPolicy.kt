package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingProgressWidth
import paige.navic.domain.models.settings.NowPlayingSliderStyle

fun nowPlayingProgressHorizontalPaddingDp(
	sliderStyle: NowPlayingSliderStyle,
	progressWidth: NowPlayingProgressWidth
): Int = when (progressWidth) {
	NowPlayingProgressWidth.Small -> 90
	NowPlayingProgressWidth.Medium -> 55
	NowPlayingProgressWidth.Big -> 30
	NowPlayingProgressWidth.Biggest -> when (sliderStyle) {
		NowPlayingSliderStyle.Yoyo -> 7
		NowPlayingSliderStyle.Squiggly -> 14
		NowPlayingSliderStyle.Flat,
		NowPlayingSliderStyle.Slim -> 16
	}
	NowPlayingProgressWidth.Expanded -> 0
}
