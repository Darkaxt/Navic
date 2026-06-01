package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class LidaClipDownloadEntity(
	@PrimaryKey val songId: String,
	val clipId: Int,
	val title: String,
	val artist: String?,
	val album: String?,
	val track: String?,
	val durationSeconds: Int?,
	val mimeType: String?,
	val qualityTier: String?,
	val fileName: String?,
	val streamUrl: String,
	val status: DownloadStatus,
	val progress: Float = 0f,
	val filePath: String? = null,
	val persistOffline: Boolean = false,
	val updatedAtMillis: Long = 0L
)
