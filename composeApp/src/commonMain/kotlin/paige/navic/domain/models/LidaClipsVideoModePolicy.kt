package paige.navic.domain.models

import paige.navic.domain.models.settings.LidaClipsVideoFitMode

fun shouldUseLidaClipsLandscapeVideoMode(
	enabled: Boolean,
	videoActive: Boolean
): Boolean = enabled && videoActive

fun shouldCropLidaClipsVideoFrame(mode: LidaClipsVideoFitMode): Boolean =
	mode == LidaClipsVideoFitMode.Crop
