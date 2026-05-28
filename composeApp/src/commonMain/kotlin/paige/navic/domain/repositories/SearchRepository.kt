package paige.navic.domain.repositories

import kotlinx.coroutines.CancellationException
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.models.visibleArtistListEntries
import paige.navic.util.core.Logger

class SearchRepository(
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) {
	val isOnline = connectivityManager.isOnline

	suspend fun search(query: String): List<Any> {
		return if (isOnline.value) {
			try {
				val data = sessionManager.api.searchID3(query)

				albumDao.insertAlbumsIgnoringConflicts(data.albums.map { it.toEntity() })
				artistDao.insertArtistsIgnoringConflicts(data.artists.map { it.toEntity() })
				val searchAlbumCoverArtById = data.albums.associate { it.id to it.coverArtId }
				songDao.insertSongsIgnoringConflicts(
					data.songs.map { song ->
						song.toEntity(albumCoverArtId = song.albumId?.let(searchAlbumCoverArtById::get))
					}
				)

				val albums = albumDao.getAlbumsByIds(data.albums.map { it.id })
				val artists = artistDao.getArtistsByIds(data.artists.map { it.id })
				val songs = songDao.getSongsByIds(data.songs.map { it.id })
				val localPlaylists = playlistDao.searchPlaylistsList(query)

				(albums.map { it.toDomainModel() }
					+ artists.map { it.toDomainModel() }.visibleArtistListEntries()
					+ songs.map { it.toDomainModel() }
					+ localPlaylists.map { it.toDomainModel() })
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.e("SearchRepository", "Online search failed despite network connection, falling back to local DB", e)
				performLocalSearch(query)
			}
		} else {
			Logger.i("SearchRepository", "Device offline, performing local search.")
			performLocalSearch(query)
		}
	}

	private suspend fun performLocalSearch(query: String): List<Any> {
		val localAlbums = albumDao.searchAlbumsList(query).map { it.toDomainModel() }
		val localArtists = artistDao.searchArtistsList(query)
			.map { it.toDomainModel() }
			.visibleArtistListEntries()
		val localSongs = songDao.searchSongsList(query).map { it.toDomainModel() }
		val localPlaylists = playlistDao.searchPlaylistsList(query).map { it.toDomainModel() }

		return listOf(localAlbums, localArtists, localSongs, localPlaylists).flatten()
	}
}
