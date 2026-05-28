package paige.navic.domain.models

fun queueTotalDurationLabel(totalSeconds: Long): String {
	val safeSeconds = totalSeconds.coerceAtLeast(0)
	val hours = safeSeconds / 3600
	val minutes = (safeSeconds % 3600) / 60
	val seconds = safeSeconds % 60

	return buildString {
		if (hours > 0) {
			append("${hours}h ")
		}

		if (minutes > 0 || hours > 0) {
			append("${minutes}m ")
		}

		append("${seconds}s")
	}
}

fun queueTotalDurationLabel(queue: List<DomainSong>): String =
	queueTotalDurationLabel(queue.sumOf { it.duration.inWholeSeconds })
