package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
	indices = [
		Index(value = ["artistId"]),
		Index(value = ["sourceArtistId"]),
		Index(value = ["normalizedName"])
	]
)
data class ArtistPhotoCacheEntity(
	@PrimaryKey val cacheKey: String,
	val artistId: String?,
	val sourceArtistId: String?,
	val name: String,
	val normalizedName: String,
	val imageUrl: String,
	val source: String,
	val updatedAtMillis: Long
)
