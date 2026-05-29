package paige.navic.domain.models

private const val SyntheticUnknownArtistName = "[Unknown Artist]"

fun isSyntheticUnknownArtistName(name: String?): Boolean =
	name?.trim()?.equals(SyntheticUnknownArtistName, ignoreCase = true) == true

fun DomainArtist.shouldShowInArtistLists(): Boolean =
	!isSyntheticUnknownArtistName(name)

fun shouldShowArtistInArtistLists(artist: DomainArtist): Boolean =
	artist.shouldShowInArtistLists()

fun List<DomainArtist>.visibleArtistListEntries(): List<DomainArtist> =
	filter { it.shouldShowInArtistLists() }
