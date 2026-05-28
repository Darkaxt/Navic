package paige.navic.domain.models

import kotlin.math.round

private const val MinPlaybackParameter = 0.5f
private const val MaxPlaybackParameter = 2.0f

fun normalizedPlaybackSpeed(value: Float): Float = normalizedPlaybackParameter(value)

fun normalizedPlaybackPitch(value: Float): Float = normalizedPlaybackParameter(value)

private fun normalizedPlaybackParameter(value: Float): Float =
	(round(value.coerceIn(MinPlaybackParameter, MaxPlaybackParameter) * 100) / 100f)
