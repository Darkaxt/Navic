package paige.navic.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.dsl.module
import paige.navic.data.database.CacheDatabase
import paige.navic.data.database.DownloadDatabase

actual val databaseModule = module {
	single<CacheDatabase> {
		Room.databaseBuilder<CacheDatabase>(documentDirectory() + "/cache.db")
			.setDriver(BundledSQLiteDriver())
			.fallbackToDestructiveMigration(true)
			.build()
	}

	single<DownloadDatabase> {
		Room.databaseBuilder<DownloadDatabase>(documentDirectory() + "/downloads.db")
			.setDriver(BundledSQLiteDriver())
			.fallbackToDestructiveMigration(true)
			.build()
	}

	registerDatabaseDaos()
}

private fun org.koin.core.module.Module.registerDatabaseDaos() {
	single { get<CacheDatabase>().albumDao() }
	single { get<CacheDatabase>().genreDao() }
	single { get<CacheDatabase>().playlistDao() }
	single { get<CacheDatabase>().songDao() }
	single { get<CacheDatabase>().artistDao() }
	single { get<CacheDatabase>().radioDao() }
	single { get<CacheDatabase>().lyricDao() }
	single { get<CacheDatabase>().syncActionDao() }
	single { get<CacheDatabase>().playbackOriginDao() }
	single { get<CacheDatabase>().artistPhotoCacheDao() }
	single { get<CacheDatabase>().aurralMetadataCacheDao() }
	single { get<CacheDatabase>().binderyMetadataCacheDao() }
	single { get<CacheDatabase>().artworkColorDao() }
	single { get<DownloadDatabase>().downloadDao() }
	single { get<DownloadDatabase>().lidaClipDownloadDao() }
}
