package paige.navic.domain.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

fun artistListSortOptions(): ImmutableList<DomainArtistListType> =
	persistentListOf(
		DomainArtistListType.AlphabeticalByName,
		DomainArtistListType.Starred,
		DomainArtistListType.Random
	)

fun List<DomainArtist>.applyArtistListDirection(reversed: Boolean): List<DomainArtist> =
	if (reversed) asReversed() else this
