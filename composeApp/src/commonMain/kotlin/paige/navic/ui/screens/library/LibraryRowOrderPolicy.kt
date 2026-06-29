package paige.navic.ui.screens.library

import androidx.compose.runtime.Immutable
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionKind

private const val LibraryRowPreferenceSeparator = "|"

@Immutable
enum class LibraryRowId(val preferenceId: String) {
	QuickPicks("quick_picks"),
	MostPlayed("most_played"),
	NewestAlbums("newest_albums"),
	StarredAlbums("starred_albums"),
	RecentAlbums("recent_albums"),
	Stations("stations"),
	Playlists("playlists"),
	MoodMixes("mood_mixes"),
	GenreMixes("genre_mixes"),
	Artists("artists"),
	Genres("genres"),
	AurralRecentlyAdded("aurral_recently_added"),
	AurralRecentReleases("aurral_recent_releases"),
	AurralRecommended("aurral_recommended"),
	AurralBasedOnLibrary("aurral_based_on_library"),
	AurralGlobalTop("aurral_global_top"),
	AurralGenreRows("aurral_genre_rows"),
	AurralTags("aurral_tags");

	companion object {
		fun fromPreferenceId(value: String): LibraryRowId? =
			entries.firstOrNull { row -> row.preferenceId == value }
	}
}

val DefaultLibraryRowOrder: List<LibraryRowId> = listOf(
	LibraryRowId.QuickPicks,
	LibraryRowId.MostPlayed,
	LibraryRowId.NewestAlbums,
	LibraryRowId.StarredAlbums,
	LibraryRowId.RecentAlbums,
	LibraryRowId.Stations,
	LibraryRowId.Playlists,
	LibraryRowId.MoodMixes,
	LibraryRowId.GenreMixes,
	LibraryRowId.Artists,
	LibraryRowId.Genres,
	LibraryRowId.AurralRecentlyAdded,
	LibraryRowId.AurralRecentReleases,
	LibraryRowId.AurralRecommended,
	LibraryRowId.AurralBasedOnLibrary,
	LibraryRowId.AurralGlobalTop,
	LibraryRowId.AurralGenreRows,
	LibraryRowId.AurralTags
)

fun effectiveLibraryRowOrder(
	savedOrder: String,
	allRows: List<LibraryRowId> = DefaultLibraryRowOrder
): List<LibraryRowId> {
	val allowed = allRows.toSet()
	val savedRows = savedOrder
		.split(LibraryRowPreferenceSeparator)
		.mapNotNull(LibraryRowId::fromPreferenceId)
		.filter { row -> row in allowed }
		.distinct()
	return savedRows + allRows.filterNot { row -> row in savedRows }
}

fun visibleLibraryRows(
	savedOrder: String,
	hiddenRows: String,
	allRows: List<LibraryRowId> = DefaultLibraryRowOrder
): List<LibraryRowId> {
	val hidden = hiddenLibraryRows(hiddenRows, allRows)
	return effectiveLibraryRowOrder(savedOrder, allRows)
		.filterNot { row -> row in hidden }
}

fun hiddenLibraryRows(
	hiddenRows: String,
	allRows: List<LibraryRowId> = DefaultLibraryRowOrder
): Set<LibraryRowId> {
	val allowed = allRows.toSet()
	return hiddenRows
		.split(LibraryRowPreferenceSeparator)
		.mapNotNull(LibraryRowId::fromPreferenceId)
		.filter { row -> row in allowed }
		.toSet()
}

fun libraryRowOrderPreference(rows: List<LibraryRowId>): String =
	rows.joinToString(LibraryRowPreferenceSeparator) { row -> row.preferenceId }

fun libraryRowHiddenPreference(rows: Set<LibraryRowId>): String =
	rows.joinToString(LibraryRowPreferenceSeparator) { row -> row.preferenceId }

fun moveLibraryRow(
	rows: List<LibraryRowId>,
	fromIndex: Int,
	toIndex: Int
): List<LibraryRowId> {
	if (fromIndex !in rows.indices || toIndex !in rows.indices || fromIndex == toIndex) return rows
	return rows.toMutableList().apply {
		add(toIndex, removeAt(fromIndex))
	}
}

fun libraryRowIdForAurralKind(kind: AurralDiscoveryCollectionKind): LibraryRowId =
	when (kind) {
		AurralDiscoveryCollectionKind.RecentlyAddedArtists -> LibraryRowId.AurralRecentlyAdded
		AurralDiscoveryCollectionKind.RecentReleases -> LibraryRowId.AurralRecentReleases
		AurralDiscoveryCollectionKind.RecommendedArtists -> LibraryRowId.AurralRecommended
		AurralDiscoveryCollectionKind.BasedOnArtists -> LibraryRowId.AurralBasedOnLibrary
		AurralDiscoveryCollectionKind.GlobalTopArtists -> LibraryRowId.AurralGlobalTop
		AurralDiscoveryCollectionKind.GenreArtists -> LibraryRowId.AurralGenreRows
		AurralDiscoveryCollectionKind.TopTags -> LibraryRowId.AurralTags
	}
