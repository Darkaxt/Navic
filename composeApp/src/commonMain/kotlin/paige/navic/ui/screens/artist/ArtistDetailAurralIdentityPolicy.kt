package paige.navic.ui.screens.artist

import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralArtistSearchResult
import paige.navic.ui.screens.aurral.AurralArtistIdentity

fun artistDetailAurralFallbackIdentities(artist: DomainArtist): List<AurralArtistIdentity> {
	val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
	val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
	return when {
		artistMbid != null -> listOf(
			AurralArtistIdentity(
				mbid = artistMbid,
				name = artistName ?: artistMbid
			)
		)

		artistName != null -> listOf(
			AurralArtistIdentity(
				mbid = aurralNameLookupArtistId(artistName),
				name = artistName,
				imageUrl = artist.artistImageUrl?.trim()?.takeIf { it.isNotEmpty() }
			)
		)

		else -> emptyList()
	}
}

fun artistDetailAurralCandidateArtist(
	artist: DomainArtist,
	identity: AurralArtistIdentity
): DomainArtist =
	artist.copy(
		name = identity.name,
		musicBrainzId = identity.mbid.takeUnless(::isAurralNameLookupArtistId)
	)

fun artistDetailAurralSearchImageUrl(
	artistMbid: String?,
	artistName: String?,
	search: AurralArtistSearchResult
): String? {
	val mbidKey = artistMbid.normalizedAurralIdentityKey()
	val nameKey = artistName.normalizedAurralIdentityName()
	return search.artists
		.firstOrNull { artist ->
			val imageUrl = artist.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
			val matchesMbid = mbidKey != null && artist.id.normalizedAurralIdentityKey() == mbidKey
			val matchesVerifiedName = mbidKey == null &&
				nameKey != null &&
				artist.detailsIdVerified &&
				artist.name.normalizedAurralIdentityName() == nameKey
			imageUrl != null && (matchesMbid || matchesVerifiedName)
		}
		?.imageUrl
		?.trim()
		?.takeIf { it.isNotEmpty() }
}

private fun String?.normalizedAurralIdentityKey(): String? =
	this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralIdentityName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""[^a-z0-9]+"""), " ")
		?.trim()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }
