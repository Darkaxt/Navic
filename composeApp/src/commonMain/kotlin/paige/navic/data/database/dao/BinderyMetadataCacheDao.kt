package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import paige.navic.data.database.entities.BinderyMetadataCacheEntity

@Dao
interface BinderyMetadataCacheDao {
	@Query("SELECT * FROM BinderyMetadataCacheEntity WHERE cacheKey = :cacheKey LIMIT 1")
	suspend fun get(cacheKey: String): BinderyMetadataCacheEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entity: BinderyMetadataCacheEntity)

	@Query("DELETE FROM BinderyMetadataCacheEntity WHERE baseUrl = :baseUrl")
	suspend fun clearBaseUrl(baseUrl: String)

	@Query("DELETE FROM BinderyMetadataCacheEntity")
	suspend fun clearAll()
}
