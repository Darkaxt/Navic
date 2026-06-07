package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
	indices = [
		Index(value = ["baseUrl"]),
		Index(value = ["payloadType"]),
		Index(value = ["updatedAtMillis"])
	]
)
data class AurralMetadataCacheEntity(
	@PrimaryKey val cacheKey: String,
	val baseUrl: String,
	val payloadType: String,
	val path: String,
	val payloadJson: String,
	val updatedAtMillis: Long
)
