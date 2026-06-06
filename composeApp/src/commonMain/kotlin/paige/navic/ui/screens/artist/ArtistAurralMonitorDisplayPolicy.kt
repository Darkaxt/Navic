package paige.navic.ui.screens.artist

import paige.navic.ui.components.common.AurralActionIconOverlay

enum class AurralMonitorActionState {
	PendingVerification,
	PendingConfirmation,
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
	state == AurralMonitorActionState.Monitored || state == AurralMonitorActionState.NotMonitored

fun shouldShowVerifiedAurralMonitorAction(aurralMonitored: Boolean?): Boolean =
	isAurralMonitorActionVerified(aurralMonitorActionState(aurralMonitored))

fun aurralMonitorActionIconOverlay(state: AurralMonitorActionState): AurralActionIconOverlay =
	when (state) {
		AurralMonitorActionState.PendingVerification -> AurralActionIconOverlay.QuestionMark
		AurralMonitorActionState.PendingConfirmation -> AurralActionIconOverlay.Progress
		AurralMonitorActionState.Monitored -> AurralActionIconOverlay.None
		AurralMonitorActionState.NotMonitored -> AurralActionIconOverlay.Crossed
	}
