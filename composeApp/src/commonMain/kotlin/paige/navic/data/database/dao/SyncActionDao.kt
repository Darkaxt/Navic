package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entities.SyncActionEntity

@Dao
interface SyncActionDao {
	@Insert
	suspend fun enqueue(action: SyncActionEntity)

	@Transaction
	@Query(
		"SELECT * FROM SyncActionEntity " +
			"WHERE deadLettered = 0 AND nextAttemptAtEpochMs <= :nowEpochMs ORDER BY id ASC"
	)
	suspend fun getDueActions(nowEpochMs: Long): List<SyncActionEntity>

	@Transaction
	@Query("SELECT * FROM SyncActionEntity WHERE deadLettered = 0 ORDER BY id ASC")
	suspend fun getPendingActions(): List<SyncActionEntity>

	@Update
	suspend fun updateAction(action: SyncActionEntity)

	@Query("SELECT COUNT(*) FROM SyncActionEntity WHERE deadLettered = 1")
	fun observeDeadLetterCount(): Flow<Int>

	@Query(
		"SELECT MIN(nextAttemptAtEpochMs) FROM SyncActionEntity " +
			"WHERE deadLettered = 0 AND nextAttemptAtEpochMs > :nowEpochMs"
	)
	suspend fun getNextRetryEpochMs(nowEpochMs: Long): Long?

	@Query("DELETE FROM SyncActionEntity WHERE id = :id")
	suspend fun removeAction(id: Int)

	@Query("DELETE FROM SyncActionEntity")
	suspend fun clearAllActions()
}
