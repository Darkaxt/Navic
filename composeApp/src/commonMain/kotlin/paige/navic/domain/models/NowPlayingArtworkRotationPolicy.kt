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

enum class NowPlayingMediaSlotMode {
	Empty,
	VinylArtwork,
	ForegroundClip
}

data class NowPlayingTechnicalInfoPlacement(
	val bottomPaddingDp: Int,
	val verticalOffsetDp: Int
)

fun shouldRotateNowPlayingArtwork(
	enabled: Boolean,
	isPaused: Boolean,
	isActiveArtwork: Boolean,
	hasCoverArt: Boolean,
	hasGeneratedArtwork: Boolean = false
): Boolean = enabled && !isPaused && isActiveArtwork && (hasCoverArt || hasGeneratedArtwork)

fun nowPlayingArtworkShapeForPlayback(
	configuredShape: CoverArtShape,
	isRotating: Boolean
): CoverArtShape = if (isRotating) CoverArtShape.Circle else configuredShape

fun shouldShowNowPlayingVinylOverlay(
	isRotatingArtwork: Boolean,
	hasCoverArt: Boolean,
	hasGeneratedArtwork: Boolean = false
): Boolean = isRotatingArtwork && (hasCoverArt || hasGeneratedArtwork)

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

fun retainedNowPlayingForegroundClipSongId(
	foregroundClipSongId: String?,
	currentSongId: String?
): String? = foregroundClipSongId.takeIf { it != null && it == currentSongId }

fun nowPlayingMediaSlotMode(
	showArtwork: Boolean,
	currentSongId: String?,
	foregroundClipSongId: String?,
	hasClip: Boolean
): NowPlayingMediaSlotMode = when {
	currentSongId != null && hasClip && foregroundClipSongId == currentSongId ->
		NowPlayingMediaSlotMode.ForegroundClip

	showArtwork && currentSongId != null -> NowPlayingMediaSlotMode.VinylArtwork
	else -> NowPlayingMediaSlotMode.Empty
}

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
