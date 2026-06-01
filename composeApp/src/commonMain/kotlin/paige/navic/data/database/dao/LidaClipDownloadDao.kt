package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LidaClipDownloadEntity

@Dao
interface LidaClipDownloadDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertDownload(download: LidaClipDownloadEntity)

	@Query("SELECT * FROM LidaClipDownloadEntity WHERE songId = :songId")
	suspend fun getDownloadBySongId(songId: String): LidaClipDownloadEntity?

	@Query("SELECT * FROM LidaClipDownloadEntity")
	fun getAllDownloads(): Flow<List<LidaClipDownloadEntity>>

	@Query("SELECT * FROM LidaClipDownloadEntity")
	suspend fun getAllDownloadsList(): List<LidaClipDownloadEntity>

	@Query("DELETE FROM LidaClipDownloadEntity WHERE songId = :songId")
	suspend fun deleteDownload(songId: String)

	@Query("DELETE FROM LidaClipDownloadEntity WHERE status = :status")
	suspend fun deleteDownloadsWithStatus(status: DownloadStatus)

	@Query("DELETE FROM LidaClipDownloadEntity")
	suspend fun clearAllDownloads()
}
