package paige.navic.domain.models

fun shouldShowNowPlayingInfoIcon(
	enabled: Boolean,
	hasNavigationTarget: Boolean
): Boolean = enabled && hasNavigationTarget
