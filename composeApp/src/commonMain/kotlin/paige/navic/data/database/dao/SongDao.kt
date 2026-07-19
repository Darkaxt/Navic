package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entities.SongEntity
import paige.navic.util.core.Logger

data class SongArtistArtwork(
	val artistId: String?,
	val artistName: String?,
	val coverArtId: String?,
	val year: Int?,
	val albumTitle: String?,
	val title: String,
	val playCount: Int
)

@Dao
interface SongDao {
	@Query("SELECT * FROM SongEntity WHERE songId = :songId LIMIT 1")
	suspend fun getSongById(songId: String): SongEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertSong(song: SongEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertSongs(songs: List<SongEntity>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertSongsIgnoringConflicts(songs: List<SongEntity>)

	@Query("SELECT * FROM SongEntity")
	suspend fun getAllSongs(): List<SongEntity>

	@Query("SELECT * FROM SongEntity")
	fun getAllSongsFlow(): Flow<List<SongEntity>>

	@Query("SELECT * FROM SongEntity WHERE belongsToAlbumId = :albumId")
	suspend fun getSongsByAlbumId(albumId: String): List<SongEntity>

	@Query(
		"""
		SELECT * FROM SongEntity
		WHERE (:artistId IS NOT NULL AND artistId COLLATE NOCASE = :artistId)
			OR (:artistName IS NOT NULL AND artistName COLLATE NOCASE = :artistName)
			OR (:contributorArtistIdPattern IS NOT NULL AND contributors LIKE :contributorArtistIdPattern ESCAPE '\')
			OR (:contributorMusicBrainzIdPattern IS NOT NULL AND contributors LIKE :contributorMusicBrainzIdPattern ESCAPE '\')
			OR (:contributorArtistNamePattern IS NOT NULL AND contributors LIKE :contributorArtistNamePattern ESCAPE '\')
		"""
	)
	suspend fun getSongsByArtistCreditCandidates(
		artistId: String?,
		artistName: String?,
		contributorArtistIdPattern: String?,
		contributorMusicBrainzIdPattern: String?,
		contributorArtistNamePattern: String?
	): List<SongEntity>

	@Query("DELETE FROM SongEntity WHERE songId = :songId")
	suspend fun deleteSong(songId: String)

	// TODO
	@Query("SELECT EXISTS(SELECT 1 FROM SongEntity WHERE songId = :songId AND starredAt IS NOT NULL)")
	suspend fun isSongStarred(songId: String): Boolean

	@Query("SELECT songId FROM SongEntity WHERE songId IN (:songIds) AND starredAt IS NOT NULL")
	suspend fun getStarredSongIds(songIds: List<String>): List<String>

	@Query("SELECT userRating FROM SongEntity WHERE songId = :songId")
	suspend fun getSongRating(songId: String): Int?

	@Query("DELETE FROM SongEntity")
	suspend fun clearAllSongs()

	@Query("SELECT songId FROM SongEntity")
	suspend fun getAllSongIds(): List<String>

	@Query("SELECT * FROM SongEntity WHERE songId IN (:ids)")
	suspend fun getSongsByIds(ids: List<String>): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE title LIKE '%' || :query || '%' COLLATE NOCASE")
	suspend fun searchSongsList(query: String): List<SongEntity>

	@Query(
		"""
		SELECT artistId, artistName, coverArtId, year, albumTitle, title, playCount
		FROM SongEntity
		WHERE coverArtId IS NOT NULL AND coverArtId != ''
		ORDER BY playCount DESC, year DESC, albumTitle COLLATE NOCASE ASC, title COLLATE NOCASE ASC
		"""
	)
	fun observeArtistSongArtwork(): Flow<List<SongArtistArtwork>>

	@Transaction
	suspend fun updateSongsByAlbumId(albumId: String, remoteSongs: List<SongEntity>) {
		val remoteIds = remoteSongs.map { it.songId }.toSet()
		getSongsByAlbumId(albumId).forEach { localSong ->
			if (localSong.songId !in remoteIds) {
				Logger.w("SongDao", "song ${localSong.songId} no longer belongs to album $albumId")
				deleteSong(localSong.songId)
			}
		}
		insertSongs(remoteSongs)
	}

	@Transaction
	suspend fun updateAllSongs(remoteSongs: List<SongEntity>) {
		val remoteIds = remoteSongs.map { it.songId }.toSet()
		getAllSongIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("SongDao", "song $localId no longer exists remotely")
				deleteSong(localId)
			}
		}
		insertSongs(remoteSongs)
	}

	@Transaction
	suspend fun deleteObsoleteSongs(remoteIds: Set<String>) {
		getAllSongIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("SongDao", "song $localId no longer exists remotely")
				deleteSong(localId)
			}
		}
	}
}
