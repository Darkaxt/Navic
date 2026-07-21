package paige.navic.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.AndroidKeystoreCredentialStore
import paige.navic.domain.manager.CredentialStore
import paige.navic.domain.manager.LogManager
import paige.navic.domain.manager.PermissionManager
import paige.navic.domain.manager.QueueNotificationManager
import paige.navic.domain.manager.ShareManager
import paige.navic.domain.manager.StorageManager
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.shared.AndroidAudiobookPlaybackManager
import paige.navic.shared.AndroidMediaPlayerViewModel
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.shared.MediaPlayerViewModel

actual val platformModule = module {
	single<CredentialStore> { AndroidKeystoreCredentialStore(androidApplication()) }

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
			playbackQueueInteractor = get(),
			downloadManager = get(),
			connectivityManager = get(),
			sessionManager = get(),
			platformContext = get(),
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
	singleOf(::PermissionManager)
}
