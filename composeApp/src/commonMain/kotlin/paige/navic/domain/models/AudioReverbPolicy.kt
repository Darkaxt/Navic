package paige.navic.domain.models

import paige.navic.domain.models.settings.AudioReverbPreset

fun audioReverbPresetValue(preset: AudioReverbPreset): Short =
	preset.androidPresetValue.toShort()

fun shouldEnableAudioReverb(
	preset: AudioReverbPreset,
	audioSessionId: Int?
): Boolean = preset != AudioReverbPreset.Off && audioSessionId != null && audioSessionId > 0
