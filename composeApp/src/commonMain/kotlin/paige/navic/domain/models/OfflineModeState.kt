package paige.navic.domain.models

import paige.navic.domain.models.settings.OfflineMode

enum class AutomaticOfflineReason {
	NavidromeUnavailable
}

data class OfflineModeState(
	val selectedMode: OfflineMode,
	val effectiveMode: OfflineMode,
	val automaticReason: AutomaticOfflineReason?
) {
	val isAutomaticallyForced: Boolean
		get() = automaticReason != null
}

fun offlineModeState(
	selectedMode: OfflineMode,
	automaticReason: AutomaticOfflineReason?
): OfflineModeState = OfflineModeState(
	selectedMode = selectedMode,
	effectiveMode = if (automaticReason == null) selectedMode else OfflineMode.Forced,
	automaticReason = automaticReason
)

fun isOnlineForOfflineMode(
	isNetworkAvailable: Boolean,
	isCellular: Boolean,
	offlineMode: OfflineMode
): Boolean = when (offlineMode) {
	OfflineMode.Forced -> false
	OfflineMode.NoWiFi -> isNetworkAvailable && !isCellular
	OfflineMode.Auto -> isNetworkAvailable
}
