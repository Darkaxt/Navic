package paige.navic.ui.screens.genre

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import paige.navic.domain.models.DomainGenreListType
import paige.navic.domain.models.DomainGenreSummary

fun genreListSortOptions(): ImmutableList<DomainGenreListType> =
	persistentListOf(
		DomainGenreListType.AlbumCount,
		DomainGenreListType.SongCount,
		DomainGenreListType.AlphabeticalByName
	)

/**
 * Filters genres by a case-insensitive name substring, then orders them. Counts sort
 * largest-first and names sort A–Z; [reversed] flips the resulting order.
 */
fun visibleGenreSummaries(
	genres: List<DomainGenreSummary>,
	sorting: DomainGenreListType,
	reversed: Boolean,
	query: String
): ImmutableList<DomainGenreSummary> {
	val needle = query.trim()
	val filtered = if (needle.isEmpty()) {
		genres
	} else {
		genres.filter { it.name.contains(needle, ignoreCase = true) }
	}
	val sorted = filtered.sortedWith(genreListComparator(sorting))
	return (if (reversed) sorted.asReversed() else sorted).toImmutableList()
}

private fun genreListComparator(sorting: DomainGenreListType): Comparator<DomainGenreSummary> {
	val byName = compareBy<DomainGenreSummary> { it.name.lowercase() }.thenBy { it.name }
	return when (sorting) {
		DomainGenreListType.AlbumCount ->
			compareByDescending<DomainGenreSummary> { it.albumCount }.then(byName)
		DomainGenreListType.SongCount ->
			compareByDescending<DomainGenreSummary> { it.songCount }.then(byName)
		DomainGenreListType.AlphabeticalByName -> byName
	}
}
