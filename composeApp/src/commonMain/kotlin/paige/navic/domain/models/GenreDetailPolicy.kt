package paige.navic.domain.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

fun genreArtists(genre: DomainGenre): List<DomainArtist> {
	val albumsByArtist = genreAlbums(genre).groupBy { album ->
		album.artistId.ifBlank { album.artistName }
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
	genreAlbums(genre)
		.flatMap { it.songs }
		.distinctBy { it.id }

fun genreTotalDuration(genre: DomainGenre): Duration =
	genrePlayableSongs(genre).fold(Duration.ZERO) { total, song -> total + song.duration }
