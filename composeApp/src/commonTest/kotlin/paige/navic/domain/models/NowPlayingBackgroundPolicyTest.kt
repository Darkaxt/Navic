package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingBackgroundPolicyTest {
	@Test
	fun blurStrengthIsClampedToSupportedRange() {
		assertEquals(0f, nowPlayingBackgroundBlurDp(-1f))
		assertEquals(80f, nowPlayingBackgroundBlurDp(80f))
		assertEquals(120f, nowPlayingBackgroundBlurDp(160f))
	}

	@Test
	fun dimPercentIsConvertedToAlphaAndClamped() {
		assertEquals(0f, nowPlayingBackgroundDimAlpha(-1))
		assertEquals(0.4f, nowPlayingBackgroundDimAlpha(40))
		assertEquals(1f, nowPlayingBackgroundDimAlpha(120))
	}

	@Test
	fun bottomGradientOnlyAppliesToDynamicBackgroundWhenEnabled() {
		assertEquals(false, shouldShowNowPlayingBackgroundBottomGradient(enabled = false, isDynamicBackground = true))
		assertEquals(false, shouldShowNowPlayingBackgroundBottomGradient(enabled = true, isDynamicBackground = false))
		assertEquals(true, shouldShowNowPlayingBackgroundBottomGradient(enabled = true, isDynamicBackground = true))
	}
}
