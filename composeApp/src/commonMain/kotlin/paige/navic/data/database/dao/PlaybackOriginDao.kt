package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entities.PlaybackOriginEntity
import kotlin.time.Instant

@Dao
interface PlaybackOriginDao {
	@Query("SELECT * FROM PlaybackOriginEntity ORDER BY totalPlayedMillis DESC, lastPlayedAt DESC LIMIT :limit")
	fun observeMostPlayed(limit: Int): Flow<List<PlaybackOriginEntity>>

	@Query("SELECT * FROM PlaybackOriginEntity WHERE originKey = :originKey LIMIT 1")
	suspend fun getPlaybackOrigin(originKey: String): PlaybackOriginEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertPlaybackOrigin(origin: PlaybackOriginEntity)

	@Query(
		"""
		UPDATE PlaybackOriginEntity
		SET title = :title,
			subtitle = :subtitle,
			coverArtId = :coverArtId,
			totalPlayedMillis = totalPlayedMillis + :durationMillis,
			lastPlayedAt = :lastPlayedAt
		WHERE originKey = :originKey
		"""
	)
	suspend fun updatePlaybackOriginCredit(
		originKey: String,
		title: String,
		subtitle: String?,
		coverArtId: String?,
		durationMillis: Long,
		lastPlayedAt: Instant
	)

	@Transaction
	suspend fun credit(origin: PlaybackOriginEntity) {
		val existing = getPlaybackOrigin(origin.originKey)
		if (existing == null) {
			insertPlaybackOrigin(origin)
		} else {
			updatePlaybackOriginCredit(
				originKey = origin.originKey,
				title = origin.title,
				subtitle = origin.subtitle,
				coverArtId = origin.coverArtId,
				durationMillis = origin.totalPlayedMillis,
				lastPlayedAt = origin.lastPlayedAt
			)
		}
	}
}
