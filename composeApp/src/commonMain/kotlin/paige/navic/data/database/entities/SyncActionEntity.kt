package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class SyncActionType {
	STAR, UNSTAR, DELETE_PLAYLIST, SCROBBLE,

	// this is dumb but it works so whatever
	STAR_0, STAR_1, STAR_2, STAR_3, STAR_4, STAR_5
}

@Entity(
	indices = [Index(value = ["deadLettered", "nextAttemptAtEpochMs", "id"])]
)
data class SyncActionEntity(
	@PrimaryKey(autoGenerate = true) val id: Int = 0,
	val actionType: SyncActionType,
	val itemId: String,
	val createdAtEpochMs: Long = 0L,
	val attemptCount: Int = 0,
	val nextAttemptAtEpochMs: Long = 0L,
	val lastError: String? = null,
	val deadLettered: Boolean = false
)
