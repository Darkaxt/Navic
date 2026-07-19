package paige.navic.di

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import paige.navic.data.database.ArtistPhotoSnapshotStore
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.dao.ArtworkColorDao
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.data.database.entities.ArtworkColorEntity
import paige.navic.domain.manager.AppLogManager
import paige.navic.domain.manager.ArtworkColorManager
import paige.navic.domain.manager.CredentialStore
import paige.navic.domain.manager.SettingsCredentialStore
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

	@Test
	fun appLogManagerResolvesWithDefaultClockAndLimit() {
		val app = startKoin {
			modules(
				managerModule,
				module {
					single<Settings> { MapSettings() }
					single<CredentialStore> { SettingsCredentialStore(get()) }
				}
			)
		}

		assertIs<AppLogManager>(app.koin.get<AppLogManager>())
	}

	@Test
	fun artistPhotoSnapshotStoreResolvesWithOwnedDefaultScope() {
		val app = startKoin {
			modules(
				module {
					single<ArtistPhotoCacheDao> { FakeArtistPhotoCacheDao() }
				},
				managerModule
			)
		}

		assertIs<ArtistPhotoSnapshotStore>(app.koin.get<ArtistPhotoSnapshotStore>())
	}
}

private class FakeArtistPhotoCacheDao : ArtistPhotoCacheDao {
	override fun observeArtistPhotoCache(): Flow<List<ArtistPhotoCacheEntity>> = flowOf(emptyList())
	override fun observeArtistPhotoCacheByIdentity(
		artistIds: List<String>,
		normalizedArtistNames: List<String>
	): Flow<List<ArtistPhotoCacheEntity>> = flowOf(emptyList())

	override suspend fun getArtistPhotoCache(): List<ArtistPhotoCacheEntity> = emptyList()

	override suspend fun upsertArtistPhotoCacheEntries(entries: List<ArtistPhotoCacheEntity>) = Unit

	override suspend fun clearArtistPhotoCache() = Unit
}

private class FakeArtworkColorDao : ArtworkColorDao {
	override suspend fun getColor(artworkKey: String): ArtworkColorEntity? = null

	override suspend fun upsertColor(color: ArtworkColorEntity) = Unit

	override suspend fun deleteColor(artworkKey: String) = Unit

	override suspend fun deleteOlderThan(cutoffEpochMillis: Long) = Unit

	override suspend fun trimToNewest(maxEntries: Int) = Unit

	override suspend fun clearAll() = Unit
}
