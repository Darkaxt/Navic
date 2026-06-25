package paige.navic.ui.screens.artist

import paige.navic.domain.models.DomainArtist
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
