package paige.navic.ui.screens.aurral

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.isStationPlaylist
import paige.navic.domain.models.stationDisplayName
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.domain.repositories.AurralFlowSummary
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.ui.navigation.Screen

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

@Immutable
data class AurralArtistIdentity(
	val mbid: String,
	val name: String,
	val imageUrl: String? = null
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

fun aurralHubDiscoverArtists(
	discovery: AurralDiscoverySummary,
	limit: Int = 8
): List<AurralDiscoverArtist> =
	aurralDiscoverListArtists(discovery)
		.take(limit.coerceAtLeast(0))

fun aurralHubDiscoverHasMore(
	discovery: AurralDiscoverySummary,
	visibleLimit: Int = 8
): Boolean =
	aurralDiscoverListArtists(discovery).size > visibleLimit.coerceAtLeast(0)

fun aurralDiscoverListArtists(
	discovery: AurralDiscoverySummary
): List<AurralDiscoverArtist> =
	mergeAurralDiscoverArtists(
		discovery.recommendations +
			discovery.recentReleases.mapNotNull { it.toDiscoverArtistRecommendation() } +
			discovery.globalTop
	)

fun aurralRecommendedAlbumsForArtist(
	discovery: AurralDiscoverySummary,
	artistMbid: String?,
	artistName: String?,
	limit: Int = 8
): List<AurralAlbumSearchItem> {
	val artistKey = artistMbid.normalizedAurralKey()
	val nameKey = artistName.normalizedAurralName()
	return (
		(discovery.recommendations + discovery.globalTop + discovery.basedOn)
			.filter { artist -> artist.matchesArtist(artistKey, nameKey) }
			.flatMap { it.recommendedAlbums } +
			discovery.recentReleases.filter { album -> album.matchesArtist(artistKey, nameKey) }
		)
		.filter { album ->
			album.id.isNotBlank() &&
				album.title.isNotBlank() &&
				album.artistName.isNotBlank() &&
				album.artistMbid.isNotBlank()
		}
		.distinctBy { it.id.trim().lowercase() }
		.take(limit.coerceAtLeast(0))
}

fun aurralArtistIdentityForLocalArtist(
	discovery: AurralDiscoverySummary,
	artist: DomainArtist
): AurralArtistIdentity? =
	aurralArtistIdentityCandidatesForLocalArtist(discovery, artist).firstOrNull()

fun aurralArtistIdentityCandidatesForLocalArtist(
	discovery: AurralDiscoverySummary,
	artist: DomainArtist
): List<AurralArtistIdentity> {
	val localMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
	val localName = artist.name.trim().takeIf { it.isNotEmpty() }
	val localMbidKey = localMbid.normalizedAurralKey()
	val localNameKey = localName.normalizedAurralName()
	val discoveredArtists = aurralDiscoverArtistsForLocalArtist(discovery)
	val candidates = mutableListOf<AurralArtistIdentity>()

	if (localMbid != null) {
		candidates += AurralArtistIdentity(
			mbid = localMbid,
			name = localName ?: localMbid
		)
	}

	listOfNotNull(
		discoveredArtists.firstOrNull { discoveredArtist ->
			localMbidKey != null &&
				discoveredArtist.id.normalizedAurralKey() == localMbidKey
		},
		discoveredArtists.firstOrNull { discoveredArtist ->
			localNameKey != null &&
				discoveredArtist.name.normalizedAurralName() == localNameKey
		}
	).forEach { discoveredArtist ->
		val mbid = discoveredArtist.id.trim().takeIf { it.isNotEmpty() } ?: return@forEach
		val name = discoveredArtist.name.trim().takeIf { it.isNotEmpty() } ?: localName ?: mbid
		if (candidates.none { it.mbid.normalizedAurralKey() == mbid.normalizedAurralKey() }) {
			candidates += AurralArtistIdentity(
				mbid = mbid,
				name = name,
				imageUrl = discoveredArtist.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
			)
		}
	}

	return candidates
}

fun aurralRecommendedAlbumsForLocalArtist(
	discovery: AurralDiscoverySummary,
	artist: DomainArtist,
	limit: Int = 8
): List<AurralAlbumSearchItem> {
	return aurralRecommendedAlbumsForArtist(
		discovery = discovery,
		artistMbid = artist.musicBrainzId,
		artistName = artist.name,
		limit = limit
	)
}

fun aurralHubSearchArtists(
	artists: List<AurralDiscoverArtist>,
	limit: Int = 8
): List<AurralDiscoverArtist> =
	artists
		.filter { artist -> artist.id.isNotBlank() && artist.name.isNotBlank() }
		.distinctBy { it.id.trim().lowercase() }
		.take(limit.coerceAtLeast(0))

fun aurralHubSearchAlbums(
	albums: List<AurralAlbumSearchItem>,
	limit: Int = 8
): List<AurralAlbumSearchItem> =
	albums
		.filter { album ->
			album.id.isNotBlank() &&
				album.title.isNotBlank() &&
				album.artistName.isNotBlank() &&
				album.artistMbid.isNotBlank()
		}
		.distinctBy { it.id.trim().lowercase() }
		.sortedWith(
			compareBy<AurralAlbumSearchItem> { it.releaseDate.aurralSearchYearOrNull() == null }
				.thenByDescending { it.releaseDate.aurralSearchYearOrNull() ?: Int.MIN_VALUE }
				.thenBy { it.title.trim().lowercase() }
		)
		.take(limit.coerceAtLeast(0))

fun aurralArtistRoute(artist: AurralDiscoverArtist): Screen.AurralArtist? {
	val artistMbid = artist.id.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = artist.name.trim().takeIf { it.isNotEmpty() } ?: return null
	return Screen.AurralArtist(
		artistMbid = artistMbid,
		artistName = artistName,
		imageUrl = artist.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
	)
}

fun aurralArtistRecommendationRoute(
	artist: AurralDiscoverArtist,
	localArtists: List<DomainArtist>
): Screen? {
	val artistKey = artist.id.normalizedAurralKey()
	val nameKey = artist.name.normalizedAurralName()
	val localArtist = localArtists.firstOrNull { local ->
		val localMbidKey = local.musicBrainzId.normalizedAurralKey()
		val localNameKey = local.name.normalizedAurralName()
		(artistKey != null && localMbidKey != null && artistKey == localMbidKey) ||
			(nameKey != null && localNameKey != null && nameKey == localNameKey)
	}
	return localArtist?.let { Screen.ArtistDetail(it.id) } ?: aurralArtistRoute(artist)
}

private fun aurralDiscoverArtistsForLocalArtist(
	discovery: AurralDiscoverySummary
): List<AurralDiscoverArtist> =
	mergeAurralDiscoverArtists(
		discovery.recommendations +
			discovery.recentReleases.mapNotNull { it.toDiscoverArtistRecommendation() } +
			discovery.globalTop +
			discovery.basedOn
	)

fun aurralAlbumSearchRoute(album: AurralAlbumSearchItem): Screen.AurralMissingAlbum? {
	val releaseGroupId = album.id.trim().takeIf { it.isNotEmpty() } ?: return null
	val title = album.title.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistMbid = album.artistMbid.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = album.artistName.trim().takeIf { it.isNotEmpty() } ?: return null
	return Screen.AurralMissingAlbum(
		artistId = artistMbid,
		artistName = artistName,
		artistMbid = artistMbid,
		releaseGroupId = releaseGroupId,
		title = title,
		year = album.releaseDate.aurralSearchYearOrNull()?.toString(),
		primaryType = album.primaryType?.trim()?.takeIf { it.isNotEmpty() }
			?: album.secondaryTypes.firstOrNull(),
		coverUrl = album.coverUrl?.trim()?.takeIf { it.isNotEmpty() },
		requestStatus = album.status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

private fun AurralAlbumSearchItem.toDiscoverArtistRecommendation(): AurralDiscoverArtist? {
	val artistMbid = artistMbid.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = artistName.trim().takeIf { it.isNotEmpty() } ?: return null
	return AurralDiscoverArtist(
		id = artistMbid,
		name = artistName,
		reason = "Recommended: ${title.trim()}",
		recommendedAlbums = listOf(this)
	)
}

private fun mergeAurralDiscoverArtists(
	artists: List<AurralDiscoverArtist>
): List<AurralDiscoverArtist> {
	val merged = linkedMapOf<String, AurralDiscoverArtist>()
	artists.forEach { artist ->
		val key = artist.id.normalizedAurralKey() ?: return@forEach
		val safeArtist = artist.copy(
			id = artist.id.trim(),
			name = artist.name.trim(),
			recommendedAlbums = artist.recommendedAlbums.distinctBy { it.id.trim().lowercase() }
		)
		val existing = merged[key]
		merged[key] = if (existing == null) {
			safeArtist
		} else {
			existing.copy(
				imageUrl = existing.imageUrl ?: safeArtist.imageUrl,
				tags = (existing.tags + safeArtist.tags).distinctBy { it.trim().lowercase() },
				matchedTags = (existing.matchedTags + safeArtist.matchedTags)
					.distinctBy { it.trim().lowercase() },
				reason = existing.reason ?: safeArtist.reason,
				sourceType = existing.sourceType ?: safeArtist.sourceType,
				discoveryTier = existing.discoveryTier ?: safeArtist.discoveryTier,
				recommendedAlbums = (existing.recommendedAlbums + safeArtist.recommendedAlbums)
					.distinctBy { it.id.trim().lowercase() }
			)
		}
	}
	return merged.values.toList()
}

private fun AurralDiscoverArtist.matchesArtist(
	artistKey: String?,
	nameKey: String?
): Boolean =
	(artistKey != null && id.normalizedAurralKey() == artistKey) ||
		(nameKey != null && name.normalizedAurralName() == nameKey)

private fun AurralAlbumSearchItem.matchesArtist(
	artistKey: String?,
	nameKey: String?
): Boolean =
	(artistKey != null && artistMbid.normalizedAurralKey() == artistKey) ||
		(nameKey != null && artistName.normalizedAurralName() == nameKey)

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

fun aurralDiscoverArtistDetail(artist: AurralDiscoverArtist): String {
	val tags = artist.matchedTags.ifEmpty { artist.tags }.take(3)
	return listOfNotNull(
		artist.reason,
		tags.takeIf { it.isNotEmpty() }?.joinToString(", ")
	).joinToString(" • ").takeIf { it.isNotEmpty() }
		?: artist.discoveryTier
		?: artist.sourceType
		?: ""
}

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

private fun String?.normalizedAurralKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun String?.aurralSearchYearOrNull(): Int? =
	this
		?.trim()
		?.take(4)
		?.takeIf { value -> value.length == 4 && value.all { it.isDigit() } }
		?.toIntOrNull()
