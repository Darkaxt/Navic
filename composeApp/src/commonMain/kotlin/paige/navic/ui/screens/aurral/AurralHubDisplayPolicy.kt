package paige.navic.ui.screens.aurral

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.repositories.AurralServiceStatus

@Immutable
enum class AurralHubSection {
	Discover,
	Requests,
	Flows
}

@Immutable
data class AurralHubSummaryCard(
	val section: AurralHubSection,
	val value: String,
	val detail: String,
	val active: Boolean
)

fun aurralHubSummaryCards(status: AurralServiceStatus): List<AurralHubSummaryCard> =
	listOf(
		AurralHubSummaryCard(
			section = AurralHubSection.Discover,
			value = pluralSummary(status.discoveryRecommendationsCount, "recommendation"),
			detail = if (status.discoveryUpdating) "updating" else "ready",
			active = status.discoveryUpdating
		),
		AurralHubSummaryCard(
			section = AurralHubSection.Requests,
			value = pluralSummary(status.requestsCount, "request"),
			detail = aurralRequestSummary(status),
			active = status.acquisitionQueue.any { aurralAcquisitionProgress(it.status).active }
		),
		AurralHubSummaryCard(
			section = AurralHubSection.Flows,
			value = "${status.enabledFlowsCount} / ${status.flowsCount} enabled",
			detail = aurralFlowSummary(status),
			active = status.flowTracksPending > 0 || status.flowTracksDownloading > 0
		)
	)

private fun aurralRequestSummary(status: AurralServiceStatus): String {
	val active = status.acquisitionQueue.count { aurralAcquisitionProgress(it.status).active }
	val ready = status.acquisitionQueue.count { aurralAcquisitionProgress(it.status).completed }
	val failed = status.acquisitionQueue.count { aurralAcquisitionProgress(it.status).failed }
	val parts = buildList {
		if (active > 0) add(pluralSummary(active, "active"))
		if (ready > 0) add(pluralSummary(ready, "ready"))
		if (failed > 0) add(pluralSummary(failed, "failed"))
	}
	return parts.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "no active requests"
}

private fun aurralFlowSummary(status: AurralServiceStatus): String {
	val trackParts = buildList {
		if (status.flowTracksPending > 0) add(statusSummary(status.flowTracksPending, "pending"))
		if (status.flowTracksDownloading > 0) add(statusSummary(status.flowTracksDownloading, "downloading"))
		if (status.flowTracksDone > 0) add(statusSummary(status.flowTracksDone, "ready"))
		if (status.flowTracksFailed > 0) add(statusSummary(status.flowTracksFailed, "failed"))
	}
	val sharedPlaylists = pluralSummary(status.sharedPlaylistsCount, "shared playlist")
	return if (trackParts.isEmpty()) {
		"${pluralSummary(status.flowTracksTotal, "track")}; $sharedPlaylists"
	} else {
		"${pluralSummary(status.flowTracksTotal, "track")}: ${trackParts.joinToString(", ")}; $sharedPlaylists"
	}
}

private fun pluralSummary(
	count: Int,
	label: String
): String = "$count $label${if (count == 1) "" else "s"}"

private fun statusSummary(
	count: Int,
	label: String
): String = "$count $label"
