package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

@Dao
interface DownloadDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertDownload(download: DownloadEntity)

	@Transaction
	suspend fun enqueueFreshIntent(songId: String, queuedAtEpochMs: Long): Long {
		val generation = (getDownloadById(songId)?.intentGeneration ?: 0L) + 1L
		insertDownload(
			DownloadEntity(
				songId = songId,
				status = DownloadStatus.QUEUED,
				intentGeneration = generation,
				queuedAtEpochMs = queuedAtEpochMs
			)
		)
		return generation
	}

	@Query("SELECT * FROM DownloadEntity WHERE songId = :songId")
	suspend fun getDownloadById(songId: String): DownloadEntity?

	@Query("SELECT * FROM DownloadEntity")
	fun getAllDownloads(): Flow<List<DownloadEntity>>

	@Query("SELECT * FROM DownloadEntity")
	suspend fun getAllDownloadsList(): List<DownloadEntity>

	@Query(
		"""
		SELECT * FROM DownloadEntity
		WHERE status = 'QUEUED' AND cancelled = 0
		ORDER BY queuedAtEpochMs ASC, songId ASC
		LIMIT 1
		"""
	)
	suspend fun getNextQueuedDownload(): DownloadEntity?

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'DOWNLOADING', progress = 0
		WHERE songId = :songId AND intentGeneration = :generation
			AND status = 'QUEUED' AND cancelled = 0
		"""
	)
	suspend fun claimQueuedDownload(songId: String, generation: Long): Int

	@Transaction
	suspend fun claimNextQueuedDownload(): DownloadEntity? {
		while (true) {
			val next = getNextQueuedDownload() ?: return null
			if (claimQueuedDownload(next.songId, next.intentGeneration) == 1) {
				return next.copy(status = DownloadStatus.DOWNLOADING, progress = 0f)
			}
		}
	}

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'QUEUED', progress = 0, queuedAtEpochMs = :queuedAtEpochMs
		WHERE songId = :songId AND intentGeneration = :generation
			AND status = 'FAILED' AND cancelled = 0
		"""
	)
	suspend fun retryFailedIntent(songId: String, generation: Long, queuedAtEpochMs: Long): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'NOT_DOWNLOADED', progress = 0, filePath = NULL,
			intentGeneration = intentGeneration + 1, cancelled = 1
		WHERE songId = :songId AND status != 'DOWNLOADED'
		"""
	)
	suspend fun cancelPendingIntent(songId: String): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'QUEUED', progress = 0
		WHERE status = 'DOWNLOADING' AND cancelled = 0
		"""
	)
	suspend fun recoverInterruptedDownloads(): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = :status, progress = :progress
		WHERE songId = :songId AND intentGeneration = :generation
			AND status = 'DOWNLOADING' AND cancelled = 0
		"""
	)
	suspend fun updateProgressIfCurrent(
		songId: String,
		generation: Long,
		status: DownloadStatus,
		progress: Float
	): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = :status, progress = :progress, filePath = :filePath
		WHERE songId = :songId AND intentGeneration = :generation
			AND status = 'DOWNLOADING' AND cancelled = 0
		"""
	)
	suspend fun completeIfCurrent(
		songId: String,
		generation: Long,
		status: DownloadStatus,
		progress: Float,
		filePath: String?
	): Int

	@Query("SELECT COUNT(*) FROM DownloadEntity WHERE status = :status")
	fun getDownloadsCount(status: DownloadStatus = DownloadStatus.DOWNLOADED): Flow<Int>

	@Query("DELETE FROM DownloadEntity WHERE songId = :songId")
	suspend fun deleteDownload(songId: String)

	@Query("DELETE FROM DownloadEntity")
	suspend fun clearAllDownloads()
}
