package paige.navic.domain.models

import kotlin.math.max
import kotlin.math.min

data class CoverArtPixel(
	val red: Int,
	val green: Int,
	val blue: Int,
	val alpha: Int
) {
	companion object {
		val White = CoverArtPixel(255, 255, 255, 255)
		val Transparent = CoverArtPixel(0, 0, 0, 0)
	}
}

data class CoverArtCropBounds(
	val left: Int,
	val top: Int,
	val rightExclusive: Int,
	val bottomExclusive: Int
) {
	val width: Int
		get() = rightExclusive - left
	val height: Int
		get() = bottomExclusive - top
}

private const val CoverArtTransparentAlphaThreshold = 16
private const val CoverArtNearWhiteMinimumChannel = 235
private const val CoverArtNearWhiteMaxChannelSpread = 28
private const val CoverArtMinimumCropMarginFraction = 0.04f
private const val CoverArtMinimumRemainingContentFraction = 0.25f

fun coverArtWhitespaceCropBounds(
	width: Int,
	height: Int,
	pixelAt: (x: Int, y: Int) -> CoverArtPixel
): CoverArtCropBounds? {
	if (width <= 1 || height <= 1) return null

	var left = width
	var top = height
	var right = -1
	var bottom = -1

	for (y in 0 until height) {
		for (x in 0 until width) {
			if (!pixelAt(x, y).isRemovableCoverArtBackground()) {
				left = min(left, x)
				top = min(top, y)
				right = max(right, x)
				bottom = max(bottom, y)
			}
		}
	}

	if (right < left || bottom < top) return null

	val crop = CoverArtCropBounds(
		left = left,
		top = top,
		rightExclusive = right + 1,
		bottomExclusive = bottom + 1
	)
	if (crop.left == 0 && crop.top == 0 && crop.rightExclusive == width && crop.bottomExclusive == height) {
		return null
	}
	if (crop.width < width * CoverArtMinimumRemainingContentFraction ||
		crop.height < height * CoverArtMinimumRemainingContentFraction
	) {
		return null
	}

	val maxHorizontalMargin = max(crop.left, width - crop.rightExclusive)
	val maxVerticalMargin = max(crop.top, height - crop.bottomExclusive)
	val meaningfulHorizontalCrop = maxHorizontalMargin >= width * CoverArtMinimumCropMarginFraction
	val meaningfulVerticalCrop = maxVerticalMargin >= height * CoverArtMinimumCropMarginFraction
	if (!meaningfulHorizontalCrop && !meaningfulVerticalCrop) return null

	return crop
}

private fun CoverArtPixel.isRemovableCoverArtBackground(): Boolean {
	if (alpha <= CoverArtTransparentAlphaThreshold) return true
	return red >= CoverArtNearWhiteMinimumChannel &&
		green >= CoverArtNearWhiteMinimumChannel &&
		blue >= CoverArtNearWhiteMinimumChannel &&
		maxOf(red, green, blue) - minOf(red, green, blue) <= CoverArtNearWhiteMaxChannelSpread
}
