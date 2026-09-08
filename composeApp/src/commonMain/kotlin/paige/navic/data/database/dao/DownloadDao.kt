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
	suspend fun enqueueFreshIntent(ownerId: String, songId: String, queuedAtEpochMs: Long): Long {
		val generation = (getDownloadById(ownerId, songId)?.intentGeneration ?: 0L) + 1L
		insertDownload(
			DownloadEntity(
				ownerId = ownerId,
				songId = songId,
				status = DownloadStatus.QUEUED,
				intentGeneration = generation,
				queuedAtEpochMs = queuedAtEpochMs
			)
		)
		return generation
	}

	@Query("SELECT * FROM DownloadEntity WHERE ownerId = :ownerId AND songId = :songId")
	suspend fun getDownloadById(ownerId: String, songId: String): DownloadEntity?

	@Query("SELECT * FROM DownloadEntity WHERE ownerId = :ownerId")
	fun getAllDownloads(ownerId: String): Flow<List<DownloadEntity>>

	@Query("SELECT * FROM DownloadEntity WHERE ownerId = :ownerId")
	suspend fun getAllDownloadsList(ownerId: String): List<DownloadEntity>

	@Query("SELECT * FROM DownloadEntity")
	suspend fun getAllDownloadsForMigration(): List<DownloadEntity>

	@Query(
		"""
		SELECT * FROM DownloadEntity
		WHERE ownerId = :ownerId AND status = 'QUEUED' AND cancelled = 0
			AND songId NOT IN (:excludedSongIds)
		ORDER BY queuedAtEpochMs ASC, songId ASC
		LIMIT 1
		"""
	)
	suspend fun getNextQueuedDownload(ownerId: String, excludedSongIds: Set<String>): DownloadEntity?

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'DOWNLOADING', progress = 0
		WHERE ownerId = :ownerId AND songId = :songId AND intentGeneration = :generation
			AND status = 'QUEUED' AND cancelled = 0
		"""
	)
	suspend fun claimQueuedDownload(ownerId: String, songId: String, generation: Long): Int

	@Transaction
	suspend fun claimNextQueuedDownload(ownerId: String, excludedSongIds: Set<String> = emptySet()): DownloadEntity? {
		while (true) {
			val next = getNextQueuedDownload(ownerId, excludedSongIds) ?: return null
			if (claimQueuedDownload(ownerId, next.songId, next.intentGeneration) == 1) {
				return next.copy(status = DownloadStatus.DOWNLOADING, progress = 0f)
			}
		}
	}

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'QUEUED', progress = 0, queuedAtEpochMs = :queuedAtEpochMs
		WHERE ownerId = :ownerId AND songId = :songId AND intentGeneration = :generation
			AND status = 'FAILED' AND cancelled = 0
		"""
	)
	suspend fun retryFailedIntent(ownerId: String, songId: String, generation: Long, queuedAtEpochMs: Long): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'NOT_DOWNLOADED', progress = 0, filePath = NULL,
			intentGeneration = intentGeneration + 1, cancelled = 1
		WHERE ownerId = :ownerId AND songId = :songId AND status != 'DOWNLOADED'
		"""
	)
	suspend fun cancelPendingIntent(ownerId: String, songId: String): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'QUEUED', progress = 0
		WHERE ownerId = :ownerId AND status = 'DOWNLOADING' AND cancelled = 0
		"""
	)
	suspend fun recoverInterruptedDownloads(ownerId: String): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = 'QUEUED', progress = 0
		WHERE ownerId = :ownerId AND songId = :songId AND intentGeneration = :generation
			AND status = 'DOWNLOADING' AND cancelled = 0
		"""
	)
	suspend fun requeueIfCurrent(ownerId: String, songId: String, generation: Long): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = :status, progress = :progress
		WHERE ownerId = :ownerId AND songId = :songId AND intentGeneration = :generation
			AND status = 'DOWNLOADING' AND cancelled = 0
		"""
	)
	suspend fun updateProgressIfCurrent(
		ownerId: String,
		songId: String,
		generation: Long,
		status: DownloadStatus,
		progress: Float
	): Int

	@Query(
		"""
		UPDATE DownloadEntity
		SET status = :status, progress = :progress, filePath = :filePath
		WHERE ownerId = :ownerId AND songId = :songId AND intentGeneration = :generation
			AND status = 'DOWNLOADING' AND cancelled = 0
		"""
	)
	suspend fun completeIfCurrent(
		ownerId: String,
		songId: String,
		generation: Long,
		status: DownloadStatus,
		progress: Float,
		filePath: String?
	): Int

	@Query("SELECT COUNT(*) FROM DownloadEntity WHERE ownerId = :ownerId AND status = :status")
	fun getDownloadsCount(ownerId: String, status: DownloadStatus = DownloadStatus.DOWNLOADED): Flow<Int>

	@Query("DELETE FROM DownloadEntity WHERE ownerId = :ownerId AND songId = :songId")
	suspend fun deleteDownload(ownerId: String, songId: String)

	@Query("DELETE FROM DownloadEntity WHERE ownerId = :ownerId AND songId = :songId AND intentGeneration = :generation AND status = :expectedStatus AND filePath IS :expectedFilePath")
	suspend fun deleteDownloadIfCurrent(
		ownerId: String, songId: String, generation: Long, expectedStatus: DownloadStatus, expectedFilePath: String?
	): Int

	@Query("DELETE FROM DownloadEntity WHERE ownerId = :ownerId AND songId = :songId AND intentGeneration = :generation AND status = 'FAILED'")
	suspend fun deleteFailedDownloadIfCurrent(ownerId: String, songId: String, generation: Long): Int

	@Query("DELETE FROM DownloadEntity WHERE ownerId = :ownerId")
	suspend fun clearAllDownloads(ownerId: String)
}
