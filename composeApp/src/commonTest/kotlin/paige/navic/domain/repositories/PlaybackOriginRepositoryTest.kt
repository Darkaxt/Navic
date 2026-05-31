package paige.navic.domain.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import paige.navic.data.database.dao.PlaybackOriginDao
import paige.navic.data.database.entities.PlaybackOriginEntity
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.PlaybackOriginType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PlaybackOriginRepositoryTest {
	@Test
	fun creditIgnoresNonPositiveDurations(): Unit = runBlocking {
		val dao = FakePlaybackOriginDao()
		val repository = PlaybackOriginRepository(
			playbackOriginDao = dao,
			now = { Instant.fromEpochMilliseconds(1_000L) }
		)

		repository.credit(artist, 0L)
		repository.credit(artist, -1L)

		assertEquals(emptyList(), dao.rows.value)
	}

	@Test
	fun creditAccumulatesDurationForSameOrigin(): Unit = runBlocking {
		val dao = FakePlaybackOriginDao()
		val repository = PlaybackOriginRepository(
			playbackOriginDao = dao,
			now = { Instant.fromEpochMilliseconds(1_000L) }
		)

		repository.credit(artist, 2_000L)
		repository.credit(artist.copy(title = "Artist Renamed"), 3_000L)

		val row = dao.rows.value.single()
		assertEquals("Artist:artist", row.originKey)
		assertEquals("Artist Renamed", row.title)
		assertEquals(5_000L, row.totalPlayedMillis)
	}

	@Test
	fun mostPlayedShortcutsSortByDurationThenRecent(): Unit = runBlocking {
		val dao = FakePlaybackOriginDao()
		dao.credit(artist.toEntity(totalPlayedMillis = 2_000L, lastPlayedAt = Instant.fromEpochMilliseconds(1_000L)))
		dao.credit(genre.toEntity(totalPlayedMillis = 5_000L, lastPlayedAt = Instant.fromEpochMilliseconds(500L)))
		dao.credit(album.toEntity(totalPlayedMillis = 5_000L, lastPlayedAt = Instant.fromEpochMilliseconds(2_000L)))
		val repository = PlaybackOriginRepository(
			playbackOriginDao = dao,
			now = { Instant.fromEpochMilliseconds(10_000L) }
		)

		val shortcuts = repository.observeMostPlayed(limit = 3).first()

		assertEquals(listOf("Album", "Genre", "Artist"), shortcuts.map { it.title })
	}

	private val artist = PlaybackOrigin(
		type = PlaybackOriginType.Artist,
		id = "artist",
		title = "Artist"
	)

	private val genre = PlaybackOrigin(
		type = PlaybackOriginType.Genre,
		id = "genre",
		title = "Genre"
	)

	private val album = PlaybackOrigin(
		type = PlaybackOriginType.Album,
		id = "album",
		title = "Album"
	)
}

private class FakePlaybackOriginDao : PlaybackOriginDao {
	val rows = MutableStateFlow<List<PlaybackOriginEntity>>(emptyList())

	override fun observeMostPlayed(limit: Int): Flow<List<PlaybackOriginEntity>> = rows

	override suspend fun getPlaybackOrigin(originKey: String): PlaybackOriginEntity? =
		rows.value.firstOrNull { it.originKey == originKey }

	override suspend fun insertPlaybackOrigin(origin: PlaybackOriginEntity) {
		rows.value = rows.value.filterNot { it.originKey == origin.originKey } + origin
	}

	override suspend fun updatePlaybackOriginCredit(
		originKey: String,
		title: String,
		subtitle: String?,
		coverArtId: String?,
		durationMillis: Long,
		lastPlayedAt: Instant
	) {
		rows.value = rows.value.map { row ->
			if (row.originKey != originKey) {
				row
			} else {
				row.copy(
					title = title,
					subtitle = subtitle,
					coverArtId = coverArtId,
					totalPlayedMillis = row.totalPlayedMillis + durationMillis,
					lastPlayedAt = lastPlayedAt
				)
			}
		}
	}
}
