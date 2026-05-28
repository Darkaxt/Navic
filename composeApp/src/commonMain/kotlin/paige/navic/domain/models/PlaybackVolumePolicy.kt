package paige.navic.domain.models

fun playbackVolumeMultiplier(
	playbackVolumePercent: Int,
	replayGainVolumeMultiplier: Float
): Float {
	val baseVolume = playbackVolumePercent.coerceIn(0, 100).toFloat() / 100f
	val replayGain = replayGainVolumeMultiplier.coerceIn(0f, 1f)
	return (baseVolume * replayGain).coerceIn(0f, 1f)
}
