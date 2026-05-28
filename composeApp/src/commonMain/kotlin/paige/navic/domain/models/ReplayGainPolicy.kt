package paige.navic.domain.models

import paige.navic.domain.models.settings.ReplayGainMode
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MaxReplayGainLoudnessBoostDb = 20f

fun replayGainDb(
	replayGain: DomainReplayGain?,
	mode: ReplayGainMode
): Float? = when (mode) {
	ReplayGainMode.Off -> null
	ReplayGainMode.Track -> replayGain?.trackGain ?: replayGain?.albumGain ?: replayGain?.fallbackGain ?: replayGain?.baseGain
	ReplayGainMode.Album -> replayGain?.albumGain ?: replayGain?.trackGain ?: replayGain?.fallbackGain ?: replayGain?.baseGain
}

fun replayGainVolumeMultiplier(
	replayGain: DomainReplayGain?,
	mode: ReplayGainMode,
	loudnessBoostEnabled: Boolean
): Float {
	val gainDb = replayGainDb(replayGain, mode) ?: return 1f
	val volumeGainDb = if (loudnessBoostEnabled) gainDb.coerceAtMost(0f) else gainDb
	return decibelToVolumeMultiplier(volumeGainDb)
}

fun replayGainLoudnessBoostMillibels(
	replayGain: DomainReplayGain?,
	mode: ReplayGainMode,
	loudnessBoostEnabled: Boolean
): Int? {
	if (!loudnessBoostEnabled) return null
	val gainDb = replayGainDb(replayGain, mode) ?: return null
	if (gainDb <= 0f) return null

	return (gainDb.coerceAtMost(MaxReplayGainLoudnessBoostDb) * 100f)
		.roundToInt()
		.coerceAtLeast(1)
}

private fun decibelToVolumeMultiplier(gainDb: Float): Float =
	10.0.pow((gainDb / 20.0)).toFloat().coerceIn(0f..1f)
