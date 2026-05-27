package paige.navic.domain.models

fun shouldResumePlaybackWhenAudioDeviceAdded(
	resumePlaybackOnAudioDeviceConnect: Boolean,
	isPlaying: Boolean,
	hasMediaItems: Boolean,
	hasPlayableDevice: Boolean
): Boolean =
	resumePlaybackOnAudioDeviceConnect &&
		!isPlaying &&
		hasMediaItems &&
		hasPlayableDevice
