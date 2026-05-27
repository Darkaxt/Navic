package paige.navic.domain.models

fun shouldUseLidaClipsLandscapeVideoMode(
	enabled: Boolean,
	videoActive: Boolean
): Boolean = enabled && videoActive
