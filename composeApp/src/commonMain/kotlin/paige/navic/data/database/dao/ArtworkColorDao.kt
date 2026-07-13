package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import paige.navic.data.database.entities.ArtworkColorEntity

@Dao
interface ArtworkColorDao {
	@Query("SELECT * FROM artwork_colors WHERE artworkKey = :artworkKey")
	suspend fun getColor(artworkKey: String): ArtworkColorEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertColor(color: ArtworkColorEntity)

	@Query("DELETE FROM artwork_colors WHERE artworkKey = :artworkKey")
	suspend fun deleteColor(artworkKey: String)

	@Query("DELETE FROM artwork_colors WHERE updatedAtEpochMillis < :cutoffEpochMillis")
	suspend fun deleteOlderThan(cutoffEpochMillis: Long)

	@Query(
		"DELETE FROM artwork_colors WHERE artworkKey IN (" +
			"SELECT artworkKey FROM artwork_colors ORDER BY updatedAtEpochMillis DESC LIMIT -1 OFFSET :maxEntries)"
	)
	suspend fun trimToNewest(maxEntries: Int)

	@Query("DELETE FROM artwork_colors")
	suspend fun clearAll()
}
