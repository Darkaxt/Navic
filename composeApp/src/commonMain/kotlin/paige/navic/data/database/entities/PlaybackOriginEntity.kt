package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
@Entity
data class PlaybackOriginEntity(
	@PrimaryKey val originKey: String,
	val type: String,
	val itemId: String,
	val title: String,
	val subtitle: String?,
	val coverArtId: String?,
	val totalPlayedMillis: Long,
	val lastPlayedAt: Instant
)
