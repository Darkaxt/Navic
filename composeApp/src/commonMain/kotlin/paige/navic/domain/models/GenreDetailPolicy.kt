package paige.navic.domain.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

fun genreArtists(genre: DomainGenre): List<DomainArtist> {
	return genreArtistsFromAlbums(genreAlbums(genre))
}

fun genreArtistsFromAlbums(albums: List<DomainAlbum>): List<DomainArtist> {
	val albumsByArtist = albums.groupBy { album ->
		album.artistId?.takeIf { it.isNotBlank() } ?: album.artistName
	}

	return albumsByArtist.map { (artistId, albums) ->
		val firstAlbum = albums.first()
		DomainArtist(
			id = artistId,
			name = firstAlbum.artistName,
			albumCount = albums.size,
			coverArtId = firstAlbum.coverArtId
		)
	}.sortedBy { it.name.lowercase() }
}

fun genreAlbums(genre: DomainGenre): List<DomainAlbum> =
	genre.albums.sortedByAlbumYearDescending()

fun genrePlayableSongs(genre: DomainGenre): List<DomainSong> =
	genrePlayableSongsFromAlbums(genreAlbums(genre))

fun genrePlayableSongsFromAlbums(albums: List<DomainAlbum>): List<DomainSong> =
	albums
		.flatMap { it.songs }
		.distinctBy { it.id }

fun genreTotalDuration(genre: DomainGenre): Duration =
	genreTotalDuration(genrePlayableSongs(genre))

fun genreTotalDuration(songs: List<DomainSong>): Duration =
	songs.fold(Duration.ZERO) { total, song -> total + song.duration }
