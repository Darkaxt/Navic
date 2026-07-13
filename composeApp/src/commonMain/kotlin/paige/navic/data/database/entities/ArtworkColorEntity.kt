package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "artwork_colors")
data class ArtworkColorEntity(
	@PrimaryKey val artworkKey: String,
	val sourceIdentity: String,
	val color: Int,
	val updatedAtEpochMillis: Long
)
