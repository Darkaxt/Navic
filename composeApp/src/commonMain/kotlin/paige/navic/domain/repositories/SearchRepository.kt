package paige.navic.domain.repositories

import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.models.visibleArtistListEntries

class SearchRepository(
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) {
	val isOnline = connectivityManager.isOnline

	suspend fun searchRemote(query: String): List<Any> {
		val data = sessionManager.withApi { it.searchID3(query) }

		albumDao.insertAlbumsIgnoringConflicts(data.albums.map { it.toEntity() })
		artistDao.insertSearchArtists(data.artists.map { it.toEntity() })
		val searchAlbumCoverArtById = data.albums.associate { it.id to it.coverArtId }
		songDao.insertSongsIgnoringConflicts(
			data.songs.map { song ->
				song.toEntity(albumCoverArtId = song.albumId?.let(searchAlbumCoverArtById::get))
			}
		)

		val albums = albumDao.getAlbumsByIds(data.albums.map { it.id })
		val artists = artistDao.getArtistsByIds(data.artists.map { it.id })
		val songs = songDao.getSongsByIds(data.songs.map { it.id })

		return albums.map { it.toDomainModel() } +
			artists.map { it.toDomainModel() }.visibleArtistListEntries() +
			songs.map { it.toDomainModel() }
	}

	suspend fun searchLocal(query: String): List<Any> {
		val localAlbums = albumDao.searchAlbumsList(query).map { it.toDomainModel() }
		val localArtists = artistDao.searchArtistsList(query)
			.map { it.toDomainModel() }
			.visibleArtistListEntries()
		val localSongs = songDao.searchSongsList(query).map { it.toDomainModel() }
		val localPlaylists = playlistDao.searchPlaylistsList(query).map { it.toDomainModel() }

		return listOf(localAlbums, localArtists, localSongs, localPlaylists).flatten()
	}
}
