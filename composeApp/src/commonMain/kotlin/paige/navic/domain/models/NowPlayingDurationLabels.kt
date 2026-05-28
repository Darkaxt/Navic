package paige.navic.domain.models

import paige.navic.util.core.toHoursMinutesSeconds
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class NowPlayingDurationLabels(
	val elapsed: String,
	val remaining: String?,
	val total: String
)

fun nowPlayingDurationLabels(
	duration: Duration?,
	progress: Float,
	showRemainingTime: Boolean
): NowPlayingDurationLabels = when {
	duration == Duration.ZERO -> NowPlayingDurationLabels(
		elapsed = "LIVE",
		remaining = null,
		total = "∞"
	)
	duration != null -> {
		val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0L)
		val elapsedSeconds = (totalSeconds * progress.coerceIn(0f, 1f)).roundToLong()
			.coerceIn(0L, totalSeconds)
		NowPlayingDurationLabels(
			elapsed = elapsedSeconds.seconds.toHoursMinutesSeconds(),
			remaining = if (showRemainingTime) {
				(totalSeconds - elapsedSeconds).seconds.toHoursMinutesSeconds()
			} else null,
			total = duration.toHoursMinutesSeconds()
		)
	}
	else -> NowPlayingDurationLabels(
		elapsed = "--:--",
		remaining = null,
		total = "--:--"
	)
}
