package paige.navic.di

import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.CredentialStore
import paige.navic.domain.manager.LogManager
import paige.navic.domain.manager.PermissionManager
import paige.navic.domain.manager.QueueNotificationManager
import paige.navic.domain.manager.ShareManager
import paige.navic.domain.manager.StorageManager
import paige.navic.domain.manager.SettingsCredentialStore
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.shared.IOSMediaPlayerViewModel
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.shared.NoOpAudiobookPlaybackManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import coil3.PlatformContext as CoilPlatformContext

actual val platformModule = module {
	single<CredentialStore> { SettingsCredentialStore(get()) }

	single<PlayerStateRepository> {
		val producePath = {
			@OptIn(ExperimentalForeignApi::class)
			val directory = NSFileManager.defaultManager.URLForDirectory(
				directory = NSDocumentDirectory,
				inDomain = NSUserDomainMask,
				appropriateForURL = null,
				create = true,
				error = null
			)
			directory?.path + "/${PlayerStateRepository.DATASTORE_FILE_NAME}"
		}
		PlayerStateRepository(PlayerStateRepository.getInstance(producePath))
	}

	single<AudiobookPlaybackManager> { NoOpAudiobookPlaybackManager() }

	viewModel<MediaPlayerViewModel> {
		IOSMediaPlayerViewModel(
			stateRepository = get(),
			downloadManager = get(),
			connectivityManager = get(),
			syncManager = get(),
			sessionManager = get(),
			playlistDao = get(),
			songDao = get(),
			songRepository = get(),
			playbackOriginRepository = get(),
			preferenceManager = get(),
			snackBarManager = get()
		)
	}

	singleOf(::ShareManager)
	singleOf(::QueueNotificationManager)
	single<CoilPlatformContext> { CoilPlatformContext.INSTANCE }
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
	singleOf(::PermissionManager)
}

@OptIn(ExperimentalForeignApi::class)
internal fun documentDirectory(): String {
	val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
		directory = NSDocumentDirectory,
		inDomain = NSUserDomainMask,
		appropriateForURL = null,
		create = false,
		error = null,
	)
	return requireNotNull(documentDirectory?.path)
}
