package paige.navic.domain.models

private const val SyntheticUnknownArtistName = "[Unknown Artist]"

fun DomainArtist.shouldShowInArtistLists(): Boolean =
	!name.trim().equals(SyntheticUnknownArtistName, ignoreCase = true)

fun shouldShowArtistInArtistLists(artist: DomainArtist): Boolean =
	artist.shouldShowInArtistLists()

fun List<DomainArtist>.visibleArtistListEntries(): List<DomainArtist> =
	filter { it.shouldShowInArtistLists() }
