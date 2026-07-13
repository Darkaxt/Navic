package paige.navic.di

import androidx.room3.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import paige.navic.data.database.CacheDatabase
import paige.navic.data.database.DownloadDatabase
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.LogManager
import paige.navic.domain.manager.QueueNotificationManager
import paige.navic.domain.manager.ShareManager
import paige.navic.domain.manager.StorageManager
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.shared.AndroidAudiobookPlaybackManager
import paige.navic.shared.AndroidMediaPlayerViewModel
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.shared.MediaPlayerViewModel

actual val platformModule = module {
	single<DownloadDatabase> {
		val dbPath = androidApplication()
			.getDatabasePath("downloads.db")
			.absolutePath
		Room
			.databaseBuilder<DownloadDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.addMigrations(DownloadDatabaseMigration4To5)
			.build()
	}

	single<CacheDatabase>(createdAtStart = true) {
		val dbPath = androidApplication()
			.getDatabasePath("cache.db")
			.absolutePath
		val downloadDatabase = get<DownloadDatabase>()
		runBlocking(Dispatchers.IO) {
			migrateLegacyDownloadRegistry(dbPath, downloadDatabase.downloadDao())
		}
		val cacheDatabase = Room
			.databaseBuilder<CacheDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.addMigrations(CacheDatabaseMigration20To21, CacheDatabaseMigration21To22)
			.build()
		runBlocking(Dispatchers.IO) {
			cacheDatabase.albumDao().getAlbumCount()
		}
		cacheDatabase
	}

	single<PlayerStateRepository> {
		val context = androidApplication()
		val producePath = {
			context.filesDir.resolve(PlayerStateRepository.DATASTORE_FILE_NAME).absolutePath
		}
		PlayerStateRepository(
			PreferenceDataStoreFactory.createWithPath(
				produceFile = { producePath().toPath() }
			)
		)
	}

	single<AudiobookPlaybackManager> {
		AndroidAudiobookPlaybackManager(
			application = androidApplication(),
			audioPlaybackOwnershipCoordinator = get()
		)
	}

	single<MediaPlayerViewModel> {
		AndroidMediaPlayerViewModel(
			application = androidApplication(),
			stateRepository = get(),
			albumDao = get(),
			playlistDao = get(),
			songDao = get(),
			downloadManager = get(),
			connectivityManager = get(),
			sessionManager = get(),
			platformContext = get(),
			songRepository = get(),
			artistPhotoCacheDao = get(),
			musicBrainzArtworkRepository = get(),
			playbackOriginRepository = get(),
			audioPlaybackOwnershipCoordinator = get(),
			preferenceManager = get(),
			snackBarManager = get()
		)
	}

	singleOf(::ShareManager)
	singleOf(::QueueNotificationManager)
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
}
