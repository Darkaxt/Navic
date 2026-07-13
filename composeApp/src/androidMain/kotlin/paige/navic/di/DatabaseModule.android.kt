package paige.navic.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module
import paige.navic.data.database.CacheDatabase
import paige.navic.data.database.DownloadDatabase

actual val databaseModule = module {
	single<DownloadDatabase> {
		val dbPath = androidApplication().getDatabasePath("downloads.db").absolutePath
		Room.databaseBuilder<DownloadDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.addMigrations(DownloadDatabaseMigration4To5)
			.build()
	}

	single<CacheDatabase>(createdAtStart = true) {
		val dbPath = androidApplication().getDatabasePath("cache.db").absolutePath
		val downloadDatabase = get<DownloadDatabase>()
		runBlocking(Dispatchers.IO) {
			migrateLegacyDownloadRegistry(dbPath, downloadDatabase.downloadDao())
		}
		val cacheDatabase = Room.databaseBuilder<CacheDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.addMigrations(
				CacheDatabaseMigration20To21,
				CacheDatabaseMigration21To22,
				CacheDatabaseMigration22To23,
				CacheDatabaseMigration23To24
			)
			.build()
		runBlocking(Dispatchers.IO) {
			cacheDatabase.albumDao().getAlbumCount()
		}
		cacheDatabase
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
