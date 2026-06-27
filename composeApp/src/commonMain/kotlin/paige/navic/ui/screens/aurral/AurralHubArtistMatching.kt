package paige.navic.ui.screens.aurral

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.domain.repositories.aurralArtistMonitoringConfirmationItem
import paige.navic.ui.screens.artist.AurralMonitorActionState
import paige.navic.ui.screens.artist.aurralMonitorActionState

@Immutable
data class AurralArtistIdentity(
	val mbid: String,
	val name: String,
	val imageUrl: String? = null
)

fun aurralDiscoverArtistMonitorActionState(
	artist: AurralDiscoverArtist
): AurralMonitorActionState? =
	artist.monitored?.let(::aurralMonitorActionState)

fun aurralDiscoverArtistMonitorActionState(
	artist: AurralDiscoverArtist,
	confirmationQueue: List<AurralConfirmationQueueItem>
): AurralMonitorActionState {
	val confirmation = aurralArtistMonitoringConfirmationItem(confirmationQueue, artist.id)
	return when (confirmation?.status) {
		AurralConfirmationStatus.Pending -> AurralMonitorActionState.PendingConfirmation
		AurralConfirmationStatus.Confirmed -> {
			if (confirmation.expectedMonitored == false) {
				AurralMonitorActionState.NotMonitored
			} else {
				AurralMonitorActionState.Monitored
			}
		}
		AurralConfirmationStatus.Failed, null ->
			artist.monitored?.let(::aurralMonitorActionState)
				?: AurralMonitorActionState.PendingVerification
	}
}

fun aurralMonitorStateForLocalArtist(
	artist: DomainArtist,
	libraryArtists: List<AurralDiscoverArtist>
): AurralMonitorActionState? {
	if (libraryArtists.isEmpty()) return null
	val artistKey = artist.musicBrainzId.normalizedAurralKey()
	val fallbackArtistKey = artist.id.normalizedAurralKey()
	val nameKey = artist.name.normalizedAurralName()
	val match = libraryArtists.firstOrNull { candidate ->
		(artistKey != null && candidate.id.normalizedAurralKey() == artistKey) ||
			(fallbackArtistKey != null && candidate.id.normalizedAurralKey() == fallbackArtistKey) ||
			(nameKey != null && candidate.name.normalizedAurralName() == nameKey)
	}
	return match?.let(::aurralDiscoverArtistMonitorActionState)
}

fun aurralMonitorStateForLocalArtist(
	artist: DomainArtist,
	libraryArtists: List<AurralDiscoverArtist>,
	confirmationQueue: List<AurralConfirmationQueueItem>
): AurralMonitorActionState? {
	val confirmation = aurralArtistMonitoringConfirmationItem(
		queue = confirmationQueue,
		artistMbid = artist.musicBrainzId ?: artist.id
	)
	return when (confirmation?.status) {
		AurralConfirmationStatus.Pending -> AurralMonitorActionState.PendingConfirmation
		AurralConfirmationStatus.Confirmed -> {
			if (confirmation.expectedMonitored == false) {
				AurralMonitorActionState.NotMonitored
			} else {
				AurralMonitorActionState.Monitored
			}
		}
		AurralConfirmationStatus.Failed, null -> aurralMonitorStateForLocalArtist(artist, libraryArtists)
	}
}
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
		val imageUrl = discoveredArtist.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
		val existingIndex = candidates.indexOfFirst { it.mbid.normalizedAurralKey() == mbid.normalizedAurralKey() }
		if (existingIndex >= 0) {
			val existing = candidates[existingIndex]
			if (existing.imageUrl.isNullOrBlank() && imageUrl != null) {
				candidates[existingIndex] = existing.copy(imageUrl = imageUrl)
			}
		} else {
			candidates += AurralArtistIdentity(
				mbid = mbid,
				name = name,
				imageUrl = imageUrl
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
private fun aurralDiscoverArtistsForLocalArtist(
	discovery: AurralDiscoverySummary
): List<AurralDiscoverArtist> =
	mergeAurralDiscoverArtists(
		discovery.recommendations +
			discovery.recentReleases.mapNotNull { it.toDiscoverArtistRecommendation() } +
			discovery.globalTop +
			discovery.basedOn
	).withLibraryArtistMonitoring(discovery.libraryArtists)
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
