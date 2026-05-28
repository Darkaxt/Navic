package paige.navic.domain.models

import kotlin.math.roundToLong
import kotlin.time.Duration

fun nowPlayingSeekProgress(
	currentProgress: Float,
	duration: Duration?,
	adjustment: Duration
): Float? {
	val durationMs = duration?.inWholeMilliseconds ?: return null
	if (durationMs <= 0L) return null

	val currentMs = (durationMs * currentProgress.coerceIn(0f, 1f)).roundToLong()
	val adjustedMs = (currentMs + adjustment.inWholeMilliseconds).coerceIn(0L, durationMs)
	return adjustedMs.toFloat() / durationMs.toFloat()
}
