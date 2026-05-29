package paige.navic.domain.models

fun shouldShowLidaClipExtraScreenBackground(
	settingEnabled: Boolean,
	lidaClipsEnabled: Boolean,
	hasCachedClip: Boolean,
	musicIsPlaying: Boolean
): Boolean =
	settingEnabled &&
		lidaClipsEnabled &&
		hasCachedClip &&
		musicIsPlaying

fun isCachedLidaClipStreamUrl(streamUrl: String?): Boolean =
	streamUrl?.startsWith("file:") == true
