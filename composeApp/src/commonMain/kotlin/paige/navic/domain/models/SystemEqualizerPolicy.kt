package paige.navic.domain.models

fun systemEqualizerAudioSessionId(audioSessionId: Int?): Int? =
	audioSessionId?.takeIf { it > 0 }
