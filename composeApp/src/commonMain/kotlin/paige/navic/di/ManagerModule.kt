package paige.navic.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import paige.navic.domain.manager.ArtworkColorManager
import paige.navic.domain.manager.AuthenticatedSessionLifetime
import paige.navic.domain.manager.AudioPlaybackOwnershipCoordinator
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.DownloadQueueNotificationCoordinator
import paige.navic.domain.manager.AppLogManager
import paige.navic.domain.manager.LidaClipCacheManager
import paige.navic.domain.manager.LidaClipDownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.manager.SleepTimerManager
import paige.navic.domain.manager.SyncManager
import paige.navic.data.remote.SubsonicClientFactory
import paige.navic.data.remote.NetworkClientFactory

val managerModule = module {
	singleOf(::AudioPlaybackOwnershipCoordinator)
	singleOf(::AuthenticatedSessionLifetime)
	singleOf(::NetworkClientFactory)
	singleOf(::SubsonicClientFactory)
	singleOf(::SleepTimerManager)
	single(createdAtStart = true) {
		SyncManager(get(), get(), get(), get(), get(), get(), get()).apply {
			startPeriodicSync()
		}
	}
	singleOf(::DownloadManager)
	single(createdAtStart = true) {
		DownloadQueueNotificationCoordinator(get(), get(), get()).apply {
			start()
		}
	}
	singleOf(::LidaClipCacheManager)
	singleOf(::LidaClipDownloadManager)
	singleOf(::SessionManager)
	singleOf(::PreferenceManager)
	singleOf(::SnackBarManager)
	singleOf(::ArtworkColorManager)
	single(createdAtStart = true) { AppLogManager(get()) }
}
