package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entities.ArtistPhotoCacheEntity

@Dao
interface ArtistPhotoCacheDao {
	@Query("SELECT * FROM ArtistPhotoCacheEntity ORDER BY updatedAtMillis DESC")
	fun observeArtistPhotoCache(): Flow<List<ArtistPhotoCacheEntity>>

	@Query(
		"""
		SELECT * FROM ArtistPhotoCacheEntity
		WHERE artistId IN (:artistIds)
			OR sourceArtistId IN (:artistIds)
			OR LOWER(TRIM(normalizedName)) IN (:normalizedArtistNames)
			OR LOWER(TRIM(name)) IN (:normalizedArtistNames)
		ORDER BY updatedAtMillis DESC
		"""
	)
	fun observeArtistPhotoCacheByIdentity(
		artistIds: List<String>,
		normalizedArtistNames: List<String>
	): Flow<List<ArtistPhotoCacheEntity>>

	@Query("SELECT * FROM ArtistPhotoCacheEntity ORDER BY updatedAtMillis DESC")
	suspend fun getArtistPhotoCache(): List<ArtistPhotoCacheEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertArtistPhotoCacheEntries(entries: List<ArtistPhotoCacheEntity>)

	@Query("DELETE FROM ArtistPhotoCacheEntity")
	suspend fun clearArtistPhotoCache()
}
