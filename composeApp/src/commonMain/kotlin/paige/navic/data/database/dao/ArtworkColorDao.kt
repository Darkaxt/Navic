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
}
