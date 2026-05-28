package paige.navic.domain.models

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun nowPlayingSeekButtonAdjustment(isLongPress: Boolean): Duration =
	if (isLongPress) 30.seconds else 10.seconds

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
