package paige.navic.util.core

import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.domain.models.DomainReplayGain
import paige.navic.domain.models.replayGainVolumeMultiplier

fun DomainReplayGain.effectiveGain(mode: ReplayGainMode = ReplayGainMode.Track): Float =
	replayGainVolumeMultiplier(this, mode, loudnessBoostEnabled = false)
