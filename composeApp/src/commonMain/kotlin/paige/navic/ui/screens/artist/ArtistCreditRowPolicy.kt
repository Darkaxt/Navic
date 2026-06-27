package paige.navic.ui.screens.artist

import paige.navic.domain.models.ArtistCreditContext
import paige.navic.domain.models.ArtistCreditResolution
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.artistCreditDisplayNames
import paige.navic.domain.models.artistCreditIdentityKey
import paige.navic.domain.models.splitArtistCredit

internal fun artistCreditResolvedRows(
	artists: List<DomainArtist>,
	resolutionForArtist: (DomainArtist) -> ArtistCreditResolution?
): List<DomainArtist> {
	val localArtistsByName = artists.associateBy { artist -> artistCreditIdentityKey(artist.name) }
	val resolvedRows = mutableListOf<DomainArtist>()
	val seen = mutableSetOf<String>()

	artists.forEach { artist ->
		val resolution = resolutionForArtist(artist)
		val isCompoundCredit = splitArtistCredit(artist.name).size > 1
		val displayNames = artistCreditDisplayNames(
			context = ArtistCreditContext(originalCredit = artist.name, sourceId = artist.id),
			cachedResolution = resolution
		)
		val artistsForCredit = if (resolution != null && displayNames.size > 1) {
			displayNames.map { name ->
				localArtistsByName[artistCreditIdentityKey(name)] ?: artist.asSyntheticArtistCredit(name)
			}
		} else if (isCompoundCredit) {
			emptyList()
		} else {
			listOf(artist)
		}

		artistsForCredit.forEach { resolvedArtist ->
			if (seen.add(artistCreditIdentityKey(resolvedArtist.name))) {
				resolvedRows += resolvedArtist
			}
		}
	}

	return resolvedRows
}

private fun DomainArtist.asSyntheticArtistCredit(name: String): DomainArtist =
	copy(
		id = aurralNameLookupArtistId(name),
		name = name,
		albumCount = 0,
		coverArtId = null,
		artistImageUrl = null,
		starredAt = null,
		userRating = null,
		sortName = name,
		musicBrainzId = null,
		lastFmUrl = null,
		roles = emptyList(),
		biography = null,
		similarArtistIds = emptyList()
	)
