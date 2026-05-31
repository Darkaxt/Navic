package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import paige.navic.data.database.dao.PlaybackOriginDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOrigin
import kotlin.time.Clock
import kotlin.time.Instant

class PlaybackOriginRepository(
	private val playbackOriginDao: PlaybackOriginDao,
	private val now: () -> Instant = { Clock.System.now() }
) {
	fun observeMostPlayed(limit: Int): Flow<ImmutableList<DomainMostPlayedShortcut>> =
		playbackOriginDao.observeMostPlayed(limit)
			.map { rows ->
				rows.mapNotNull { it.toDomainModel() }
					.sortedWith(
						compareByDescending<DomainMostPlayedShortcut> { it.totalPlayedMillis }
							.thenByDescending { it.lastPlayedAt }
					)
					.take(limit)
					.toImmutableList()
			}
			.flowOn(Dispatchers.IO)

	suspend fun credit(origin: PlaybackOrigin, durationMillis: Long) {
		if (durationMillis <= 0L) return

		playbackOriginDao.credit(
			origin.toEntity(
				totalPlayedMillis = durationMillis,
				lastPlayedAt = now()
			)
		)
	}
}
