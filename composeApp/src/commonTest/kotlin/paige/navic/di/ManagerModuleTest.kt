package paige.navic.di

import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import paige.navic.data.database.dao.ArtworkColorDao
import paige.navic.data.database.entities.ArtworkColorEntity
import paige.navic.domain.manager.ArtworkColorManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs

class ManagerModuleTest {
	@AfterTest
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun artworkColorManagerResolvesWithDefaultClock() {
		val app = startKoin {
			modules(
				module {
					single<ArtworkColorDao> { FakeArtworkColorDao() }
				},
				managerModule
			)
		}

		assertIs<ArtworkColorManager>(app.koin.get<ArtworkColorManager>())
	}
}

private class FakeArtworkColorDao : ArtworkColorDao {
	override suspend fun getColor(artworkKey: String): ArtworkColorEntity? = null

	override suspend fun upsertColor(color: ArtworkColorEntity) = Unit

	override suspend fun deleteColor(artworkKey: String) = Unit

	override suspend fun deleteOlderThan(cutoffEpochMillis: Long) = Unit

	override suspend fun trimToNewest(maxEntries: Int) = Unit

	override suspend fun clearAll() = Unit
}
