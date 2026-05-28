package paige.navic.ui.components.sheets

internal fun shouldRunUpdateCheck(
	platformName: String,
	automaticChecksEnabled: Boolean,
	forceCheckRequests: Int
): Boolean {
	val normalizedPlatform = platformName.lowercase()
	val isAppleMobilePlatform = normalizedPlatform == "ios" || normalizedPlatform == "ipados"
	return !isAppleMobilePlatform && (automaticChecksEnabled || forceCheckRequests > 0)
}
