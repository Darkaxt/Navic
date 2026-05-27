package paige.navic.domain.models

fun audioFadeDurationMs(audioFadeDurationMs: Int): Long =
	audioFadeDurationMs.coerceAtLeast(0).toLong()

fun shouldFadePlaybackCommand(
	audioFadeDurationMs: Int,
	alreadyInTargetState: Boolean
): Boolean = audioFadeDurationMs(audioFadeDurationMs) > 0L && !alreadyInTargetState
