package paige.navic.domain.models

const val DefaultNowPlayingBackgroundBlurDp = 80f
const val MinNowPlayingBackgroundBlurDp = 0f
const val MaxNowPlayingBackgroundBlurDp = 120f

const val DefaultNowPlayingBackgroundDimPercent = 40
const val MinNowPlayingBackgroundDimPercent = 0
const val MaxNowPlayingBackgroundDimPercent = 100

fun nowPlayingBackgroundBlurDp(
	blurDp: Float
): Float = blurDp.coerceIn(MinNowPlayingBackgroundBlurDp, MaxNowPlayingBackgroundBlurDp)

fun nowPlayingBackgroundDimAlpha(
	dimPercent: Int
): Float = dimPercent
	.coerceIn(MinNowPlayingBackgroundDimPercent, MaxNowPlayingBackgroundDimPercent)
	.toFloat() / 100f

fun shouldShowNowPlayingBackgroundBottomGradient(
	enabled: Boolean,
	isDynamicBackground: Boolean
): Boolean = enabled && isDynamicBackground
