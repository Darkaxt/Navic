package paige.navic.domain.models

import paige.navic.domain.models.settings.CoverArtShape

const val NowPlayingArtworkRotationDurationMs = 8000
const val NowPlayingArtworkRevealDurationMs = 180
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

enum class NowPlayingDiscFitMode {
	Crop,
	Fit,
	SoftEdgeCompress
}

enum class NowPlayingDiscContentScale {
	Crop,
	Fit
}

data class NowPlayingTechnicalInfoPlacement(
	val bottomPaddingDp: Int,
	val verticalOffsetDp: Int
)

data class NowPlayingArtworkRequestIdentity(
	val songId: String,
	val coverArtId: String?,
	val imageUrl: String?,
	val imageCacheKey: String?
)

fun isNowPlayingVinylArtworkReady(
	hasCoverArt: Boolean,
	hasGeneratedArtwork: Boolean,
	requestedArtwork: NowPlayingArtworkRequestIdentity,
	resolvedArtwork: NowPlayingArtworkRequestIdentity?
): Boolean =
	if (hasCoverArt) {
		resolvedArtwork == requestedArtwork
	} else {
		hasGeneratedArtwork
	}

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

fun shouldUseNowPlayingVinylPresentation(
	isWideLandscape: Boolean,
	isRotatingArtwork: Boolean,
	hasCoverArt: Boolean,
	hasGeneratedArtwork: Boolean = false
): Boolean =
	(isWideLandscape || isRotatingArtwork) && (hasCoverArt || hasGeneratedArtwork)

fun shouldShowNowPlayingVinylOverlay(
	isVinylPresentation: Boolean,
	hasCoverArt: Boolean,
	hasGeneratedArtwork: Boolean = false
): Boolean = isVinylPresentation && (hasCoverArt || hasGeneratedArtwork)

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

fun nowPlayingDiscFitMode(
	isWideLandscape: Boolean,
	isVinylArtwork: Boolean,
	hasRealArtwork: Boolean
): NowPlayingDiscFitMode =
	if (isWideLandscape && isVinylArtwork && hasRealArtwork) {
		NowPlayingDiscFitMode.SoftEdgeCompress
	} else {
		NowPlayingDiscFitMode.Crop
	}

fun nowPlayingDiscContentScale(
	fitMode: NowPlayingDiscFitMode,
	imageWidth: Int?,
	imageHeight: Int?
): NowPlayingDiscContentScale {
	if (fitMode == NowPlayingDiscFitMode.Crop) return NowPlayingDiscContentScale.Crop
	if (fitMode == NowPlayingDiscFitMode.Fit) return NowPlayingDiscContentScale.Fit

	val width = imageWidth ?: return NowPlayingDiscContentScale.Crop
	val height = imageHeight ?: return NowPlayingDiscContentScale.Crop
	if (width <= 0 || height <= 0) return NowPlayingDiscContentScale.Crop

	val wider = width.toFloat() / height.toFloat()
	val taller = height.toFloat() / width.toFloat()
	val aspectRatio = maxOf(wider, taller)
	return if (aspectRatio >= NowPlayingDiscSoftEdgeFitAspectThreshold) {
		NowPlayingDiscContentScale.Fit
	} else {
		NowPlayingDiscContentScale.Crop
	}
}

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

private const val NowPlayingDiscSoftEdgeFitAspectThreshold = 1.18f
