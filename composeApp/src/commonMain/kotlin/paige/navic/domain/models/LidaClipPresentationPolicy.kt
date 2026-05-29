package paige.navic.domain.models

import paige.navic.domain.models.settings.LidaClipsBackgroundVideoMode
import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import kotlin.math.roundToLong

fun shouldShowLidaClipBackgroundVideo(mode: LidaClipsBackgroundVideoMode): Boolean =
	mode != LidaClipsBackgroundVideoMode.Off

fun shouldBlurLidaClipBackgroundVideo(mode: LidaClipsBackgroundVideoMode): Boolean =
	mode == LidaClipsBackgroundVideoMode.Blurred

fun lidaClipBackgroundVideoFitMode(mode: LidaClipsBackgroundVideoMode): LidaClipsVideoFitMode =
	if (shouldShowLidaClipBackgroundVideo(mode)) LidaClipsVideoFitMode.Crop else LidaClipsVideoFitMode.Fit

fun lidaClipForegroundVideoFitMode(mode: LidaClipsVideoFitMode): LidaClipsVideoFitMode =
	mode

fun lidaClipDurationMs(durationSeconds: Int?): Long? =
	durationSeconds?.takeIf { it > 0 }?.toLong()?.times(1000L)

fun lidaClipProgressStartPositionMs(songProgress: Float, clipDurationMs: Long?): Long {
	val duration = clipDurationMs?.takeIf { it > 0L } ?: return 0L
	return (duration * songProgress.coerceIn(0f, 1f)).roundToLong().coerceIn(0L, duration)
}
