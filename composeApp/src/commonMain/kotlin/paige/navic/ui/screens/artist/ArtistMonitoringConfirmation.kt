package paige.navic.ui.screens.artist

import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.aurralArtistMonitoringConfirmationItem
import paige.navic.ui.screens.artist.viewmodels.ArtistState

internal fun aurralMonitoringSubmissionIsVisible(submitting: Boolean, status: AurralConfirmationStatus?): Boolean =
	submitting && (status == null || status == AurralConfirmationStatus.Pending)

internal fun ArtistState.withConfirmedAurralMonitoring(
	queue: List<AurralConfirmationQueueItem>,
	knownMonitoring: Map<String, Boolean?> = emptyMap()
): ArtistState {
	val mbid = aurralArtistMbid ?: artist.musicBrainzId
	val confirmation = aurralArtistMonitoringConfirmationItem(queue, mbid)
		?.takeIf { it.status == AurralConfirmationStatus.Confirmed }
	val monitored = confirmation?.expectedMonitored
		?: mbid?.trim()?.lowercase()?.let(knownMonitoring::get)
		?: return this
	return copy(aurralMonitored = monitored)
}
