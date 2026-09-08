package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
	primaryKeys = ["ownerId", "songId"],
	indices = [Index(value = ["ownerId", "status", "cancelled", "queuedAtEpochMs"])]
)
data class DownloadEntity(
	val songId: String,
	val status: DownloadStatus,
	val progress: Float = 0f,
	val filePath: String? = null,
	val intentGeneration: Long = 0L,
	val queuedAtEpochMs: Long = 0L,
	val cancelled: Boolean = false,
	val ownerId: String = ""
)

@Serializable
enum class DownloadStatus {
	NOT_DOWNLOADED,
	DOWNLOADING,
	DOWNLOADED,
	FAILED,
	QUEUED
}
