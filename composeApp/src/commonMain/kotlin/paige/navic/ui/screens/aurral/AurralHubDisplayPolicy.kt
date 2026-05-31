package paige.navic.ui.screens.aurral

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.isStationPlaylist
import paige.navic.domain.models.stationDisplayName
import paige.navic.domain.repositories.AurralFlowSummary
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

fun canCreateAurralFlow(status: AurralServiceStatus): Boolean =
	status.accessFlow && status.flowCapabilities.unavailableSources.isEmpty()

fun nextAurralFlowName(
	flows: List<AurralFlowSummary>,
	baseName: String = "Discover"
): String {
	val normalizedBase = baseName.trim().takeIf { it.isNotEmpty() } ?: "Discover"
	val existingNames = flows
		.map { it.name.trim().lowercase() }
		.filter { it.isNotEmpty() }
		.toSet()
	if (normalizedBase.lowercase() !in existingNames) return normalizedBase
	var index = 2
	while (index < 10000) {
		val candidate = "$normalizedBase $index"
		if (candidate.lowercase() !in existingNames) return candidate
		index += 1
	}
	return "$normalizedBase ${flows.size + 1}"
}

fun aurralFlowDetail(flow: AurralFlowSummary): String {
	val stats = flow.stats
	val statusParts = buildList {
		if (stats.done > 0) add(statusSummary(stats.done, "ready"))
		if (stats.pending > 0) add(statusSummary(stats.pending, "pending"))
		if (stats.downloading > 0) add(statusSummary(stats.downloading, "downloading"))
		if (stats.failed > 0) add(statusSummary(stats.failed, "failed"))
	}
	val schedule = aurralScheduleSummary(flow.scheduleDays, flow.scheduleTime)
	val parts = buildList {
		add(pluralSummary(flow.size, "track"))
		if (statusParts.isNotEmpty()) add(statusParts.joinToString(", "))
		if (schedule.isNotEmpty()) add(schedule)
	}
	return parts.joinToString("; ")
}

fun aurralStationForFlow(
	flow: AurralFlowSummary,
	playlists: List<DomainPlaylist>
): DomainPlaylist? {
	val flowName = flow.name.normalizedAurralFlowStationName() ?: return null
	return playlists.firstOrNull { playlist ->
		playlist.isStationPlaylist() &&
			playlist.stationDisplayName().normalizedAurralFlowStationName() == flowName
	}
}

fun aurralPlayableStationForFlow(
	flow: AurralFlowSummary,
	playlists: List<DomainPlaylist>
): DomainPlaylist? =
	aurralStationForFlow(flow, playlists)?.takeIf { station ->
		station.songCount > 0 || station.songs.isNotEmpty()
	}

fun shouldOfferAurralDirectFlowPlayback(
	flow: AurralFlowSummary,
	playlists: List<DomainPlaylist>
): Boolean =
	flow.enabled &&
		flow.stats.done > 0 &&
		aurralPlayableStationForFlow(flow, playlists) == null

private fun aurralScheduleSummary(
	scheduleDays: List<Int>,
	scheduleTime: String
): String {
	val dayNames = scheduleDays
		.distinct()
		.sorted()
		.mapNotNull { day ->
			when (day) {
				0 -> "Sun"
				1 -> "Mon"
				2 -> "Tue"
				3 -> "Wed"
				4 -> "Thu"
				5 -> "Fri"
				6 -> "Sat"
				else -> null
			}
		}
	if (dayNames.isEmpty()) return ""
	val safeTime = scheduleTime.trim().takeIf { it.isNotEmpty() } ?: "00:00"
	return "${dayNames.joinToString(", ")} at $safeTime"
}

private fun String.normalizedAurralFlowStationName(): String? =
	trim()
		.removePrefix("[A]")
		.trim()
		.lowercase()
		.replace(Regex("""\s+"""), " ")
		.takeIf { it.isNotEmpty() }
