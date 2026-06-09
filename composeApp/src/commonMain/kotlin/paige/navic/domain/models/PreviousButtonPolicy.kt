package paige.navic.domain.models

fun shouldRestartCurrentOnPrevious(
	smartRewindSeconds: Int,
	hasPreviousMediaItem: Boolean,
	currentPositionMs: Long
): Boolean =
	!hasPreviousMediaItem ||
		(smartRewindSeconds >= 0 && currentPositionMs > smartRewindThresholdMs(smartRewindSeconds))

private fun smartRewindThresholdMs(smartRewindSeconds: Int): Long =
	smartRewindSeconds.coerceAtLeast(0) * 1000L
