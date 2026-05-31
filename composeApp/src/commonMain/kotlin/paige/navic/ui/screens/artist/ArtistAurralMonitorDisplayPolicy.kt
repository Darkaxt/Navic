package paige.navic.ui.screens.artist

enum class AurralMonitorActionState {
	PendingVerification,
	Monitored,
	NotMonitored
}

fun aurralMonitorActionState(aurralMonitored: Boolean?): AurralMonitorActionState =
	when (aurralMonitored) {
		true -> AurralMonitorActionState.Monitored
		false -> AurralMonitorActionState.NotMonitored
		null -> AurralMonitorActionState.PendingVerification
	}

fun isAurralMonitorActionVerified(state: AurralMonitorActionState): Boolean =
	state != AurralMonitorActionState.PendingVerification

fun shouldShowVerifiedAurralMonitorAction(aurralMonitored: Boolean?): Boolean =
	isAurralMonitorActionVerified(aurralMonitorActionState(aurralMonitored))
