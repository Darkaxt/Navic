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
	val rowIndexByKey = mutableMapOf<String, Int>()

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
			val key = artistCreditIdentityKey(resolvedArtist.name)
			val index = rowIndexByKey[key]
			if (index == null) {
				rowIndexByKey[key] = resolvedRows.size
				resolvedRows += resolvedArtist
			} else if (isAurralNameLookupArtistId(resolvedRows[index].id) && isAurralNameLookupArtistId(resolvedArtist.id)) {
				// A name credited on several combined credits counts the albums of all of them.
				val row = resolvedRows[index]
				resolvedRows[index] = row.copy(albumCount = row.albumCount + resolvedArtist.albumCount)
			}
		}
	}

	return resolvedRows
}

/** True when [name] is one of the artists credited by [credit] (the whole credit or one of its split names). */
fun artistCreditIncludesName(credit: String?, name: String): Boolean {
	val wanted = artistCreditIdentityKey(name).takeIf { it.isNotEmpty() } ?: return false
	val whole = credit?.takeIf { it.isNotBlank() } ?: return false
	return artistCreditIdentityKey(whole) == wanted ||
		splitArtistCredit(whole).any { part -> artistCreditIdentityKey(part) == wanted }
}

/**
 * A credited name with no library artist of its own: it lists the combined credit's albums (its
 * artist page finds them by [artistCreditIncludesName]).
 */
private fun DomainArtist.asSyntheticArtistCredit(name: String): DomainArtist =
	copy(
		id = aurralNameLookupArtistId(name),
		name = name,
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
