package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import paige.navic.data.database.entities.AurralMetadataCacheEntity

@Dao
interface AurralMetadataCacheDao {
	@Query("SELECT * FROM AurralMetadataCacheEntity WHERE cacheKey = :cacheKey LIMIT 1")
	suspend fun get(cacheKey: String): AurralMetadataCacheEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entity: AurralMetadataCacheEntity)

	@Query("DELETE FROM AurralMetadataCacheEntity WHERE baseUrl = :baseUrl")
	suspend fun clearBaseUrl(baseUrl: String)

	@Query("DELETE FROM AurralMetadataCacheEntity")
	suspend fun clearAll()
}
