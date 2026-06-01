package paige.navic.di

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import paige.navic.data.database.dao.PlaybackOriginDao
import paige.navic.data.database.entities.PlaybackOriginEntity
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.LastFmRepository
import paige.navic.domain.repositories.PlaybackOriginRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Instant

class RepositoryModuleTest {
	@AfterTest
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun playbackOriginRepositoryResolvesWithDefaultClock() {
		val app = startKoin {
			modules(
				module {
					single<PlaybackOriginDao> { FakePlaybackOriginDao() }
				},
				repositoryModule
			)
		}

		assertIs<PlaybackOriginRepository>(app.koin.get<PlaybackOriginRepository>())
	}

	@Test
	fun lastFmRepositoryResolvesWithDefaultApiClient() {
		val app = startKoin {
			modules(
				module {
					single { PreferenceManager(MapSettings()) }
				},
				repositoryModule
			)
		}

		assertIs<LastFmRepository>(app.koin.get<LastFmRepository>())
	}
}

private class FakePlaybackOriginDao : PlaybackOriginDao {
	override fun observeMostPlayed(limit: Int): Flow<List<PlaybackOriginEntity>> = emptyFlow()

	override suspend fun getPlaybackOrigin(originKey: String): PlaybackOriginEntity? = null

	override suspend fun insertPlaybackOrigin(origin: PlaybackOriginEntity) = Unit

	override suspend fun updatePlaybackOriginCredit(
		originKey: String,
		title: String,
		subtitle: String?,
		coverArtId: String?,
		durationMillis: Long,
		lastPlayedAt: Instant
	) = Unit
}
