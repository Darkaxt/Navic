package paige.navic.domain.models

fun medleyModeDurationMs(medleyModeSeconds: Int): Long =
	medleyModeSeconds.coerceAtLeast(0).toLong() * 1_000L

fun shouldAdvanceMedleyMode(
	medleyModeSeconds: Int,
	isPlaying: Boolean,
	hasNextMediaItem: Boolean,
	currentPositionMs: Long,
	alreadyAdvancedCurrentItem: Boolean
): Boolean {
	val durationMs = medleyModeDurationMs(medleyModeSeconds)
	return durationMs > 0L &&
		isPlaying &&
		hasNextMediaItem &&
		!alreadyAdvancedCurrentItem &&
		currentPositionMs >= durationMs
}
