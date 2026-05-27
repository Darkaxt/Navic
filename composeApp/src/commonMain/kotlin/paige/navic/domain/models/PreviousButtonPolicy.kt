package paige.navic.domain.models

fun shouldRestartCurrentOnPrevious(
	smartRewindSeconds: Int,
	hasPreviousMediaItem: Boolean,
	currentPositionMs: Long
): Boolean =
	!hasPreviousMediaItem ||
		currentPositionMs > smartRewindThresholdMs(smartRewindSeconds)

private fun smartRewindThresholdMs(smartRewindSeconds: Int): Long =
	smartRewindSeconds.coerceAtLeast(0) * 1000L
