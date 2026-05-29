package paige.navic.domain.models

import paige.navic.domain.models.settings.CoverArtShape

const val NowPlayingArtworkRotationDurationMs = 8000

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
