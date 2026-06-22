package paige.navic.domain.models

import paige.navic.domain.models.settings.CoverArtShape

const val NowPlayingArtworkRotationDurationMs = 8000
const val NowPlayingVinylSpindleRadiusFraction = 0.025f
const val NowPlayingVinylLabelRadiusFraction = 0.17f
const val NowPlayingVinylGrooveStartRadiusFraction = 0.24f
const val NowPlayingVinylGrooveEndRadiusFraction = 0.95f
const val NowPlayingVideoArtworkCrossfadeDurationMs = 260
const val NowPlayingVideoArtworkCrossfadeInitialScale = 0.985f

enum class NowPlayingFallbackLabelStyle {
	Center,
	Arc
}

data class NowPlayingTechnicalInfoPlacement(
	val bottomPaddingDp: Int,
	val verticalOffsetDp: Int
)

fun shouldRotateNowPlayingArtwork(
	enabled: Boolean,
	isPaused: Boolean,
	isActiveArtwork: Boolean,
	hasCoverArt: Boolean
): Boolean = enabled && !isPaused && isActiveArtwork && hasCoverArt

fun nowPlayingArtworkShapeForPlayback(
	configuredShape: CoverArtShape,
	isRotating: Boolean
): CoverArtShape = if (isRotating) CoverArtShape.Circle else configuredShape

fun shouldShowNowPlayingVinylOverlay(
	isRotatingArtwork: Boolean,
	hasCoverArt: Boolean
): Boolean = isRotatingArtwork && hasCoverArt

fun nowPlayingVinylOverlayRotationDegrees(
	isRotatingArtwork: Boolean,
	artworkRotationDegrees: Float
): Float = if (isRotatingArtwork) artworkRotationDegrees else 0f

fun nowPlayingArtworkRotationDegreesForElapsedMillis(elapsedMillis: Long): Float {
	val durationMillis = NowPlayingArtworkRotationDurationMs.toLong()
	if (durationMillis <= 0L) return 0f

	val cycleMillis = elapsedMillis.floorMod(durationMillis)
	return cycleMillis.toFloat() / durationMillis.toFloat() * 360f
}

fun shouldUseTurnTableWidgetVinylArtwork(hasCoverArt: Boolean): Boolean = hasCoverArt

fun nowPlayingFallbackLabelStyle(isRotatingArtwork: Boolean): NowPlayingFallbackLabelStyle =
	if (isRotatingArtwork) NowPlayingFallbackLabelStyle.Arc else NowPlayingFallbackLabelStyle.Center

fun nowPlayingTechnicalInfoPlacement(
	isLandscape: Boolean,
	isVinylArtwork: Boolean
): NowPlayingTechnicalInfoPlacement =
	NowPlayingTechnicalInfoPlacement(
		bottomPaddingDp = if (isLandscape) 16 else 8,
		verticalOffsetDp = when {
			!isVinylArtwork -> 0
			isLandscape -> 14
			else -> 28
		}
	)

private fun Long.floorMod(modulus: Long): Long = ((this % modulus) + modulus) % modulus
