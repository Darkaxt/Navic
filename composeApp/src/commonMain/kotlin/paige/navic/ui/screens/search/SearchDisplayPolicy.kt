package paige.navic.ui.screens.search

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist

@Immutable
data class SearchResultBuckets(
	val albums: List<DomainAlbum> = emptyList(),
	val artists: List<DomainArtist> = emptyList(),
	val songs: List<DomainSong> = emptyList(),
	val aurralArtists: List<AurralDiscoverArtist> = emptyList(),
	val aurralAlbums: List<AurralAlbumSearchItem> = emptyList()
) {
	val isEmpty: Boolean
		get() = albums.isEmpty() &&
			artists.isEmpty() &&
			songs.isEmpty() &&
			aurralArtists.isEmpty() &&
			aurralAlbums.isEmpty()
}

fun combinedSearchResults(
	localResults: List<Any>,
	aurralArtists: List<AurralDiscoverArtist> = emptyList(),
	aurralAlbums: List<AurralAlbumSearchItem> = emptyList()
): List<Any> = localResults + aurralArtists + aurralAlbums

fun searchResultBuckets(
	results: List<Any>,
	category: SearchCategory
): SearchResultBuckets {
	val showAll = category == SearchCategory.ALL
	val showAlbums = showAll || category == SearchCategory.ALBUMS
	val showArtists = showAll || category == SearchCategory.ARTISTS
	val showSongs = showAll || category == SearchCategory.SONGS
	return SearchResultBuckets(
		albums = if (showAlbums) results.filterIsInstance<DomainAlbum>() else emptyList(),
		artists = if (showArtists) results.filterIsInstance<DomainArtist>() else emptyList(),
		songs = if (showSongs) results.filterIsInstance<DomainSong>() else emptyList(),
		aurralArtists = if (showArtists) results.filterIsInstance<AurralDiscoverArtist>() else emptyList(),
		aurralAlbums = if (showAlbums) results.filterIsInstance<AurralAlbumSearchItem>() else emptyList()
	)
}
