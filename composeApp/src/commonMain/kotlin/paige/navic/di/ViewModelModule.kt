package paige.navic.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import paige.navic.domain.models.DomainSong
import paige.navic.ui.components.dialogs.DeletionViewModel
import paige.navic.ui.components.sheets.ChangelogViewModel
import paige.navic.ui.screens.activity.ActivityViewModel
import paige.navic.ui.screens.album.viewmodels.AlbumListViewModel
import paige.navic.ui.screens.artist.viewmodels.ArtistDetailViewModel
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.aurral.AurralHubViewModel
import paige.navic.ui.screens.collection.viewmodels.CollectionDetailViewModel
import paige.navic.ui.screens.genre.viewmodels.GenreDetailViewModel
import paige.navic.ui.screens.genre.viewmodels.GenreListViewModel
import paige.navic.ui.screens.library.MostPlayedShortcutsViewModel
import paige.navic.ui.screens.login.viewmodels.LoginViewModel
import paige.navic.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import paige.navic.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import paige.navic.ui.screens.lidaClips.LidaClipPlayerViewModel
import paige.navic.ui.screens.playlist.viewmodels.PlaylistCreateDialogViewModel
import paige.navic.ui.screens.playlist.viewmodels.PlaylistListViewModel
import paige.navic.ui.screens.playlist.viewmodels.PlaylistUpdateDialogViewModel
import paige.navic.ui.screens.queue.viewmodels.QueueViewModel
import paige.navic.ui.screens.radio.viewmodels.RadioCreateDialogViewModel
import paige.navic.ui.screens.radio.viewmodels.RadioListViewModel
import paige.navic.ui.screens.search.viewmodels.SearchViewModel
import paige.navic.ui.screens.settings.viewmodels.LyricsPriorityViewModel
import paige.navic.ui.screens.settings.viewmodels.NavtabsViewModel
import paige.navic.ui.screens.settings.viewmodels.SettingsAurralViewModel
import paige.navic.ui.screens.settings.viewmodels.SettingsBinderyViewModel
import paige.navic.ui.screens.settings.viewmodels.SettingsDataStorageViewModel
import paige.navic.ui.screens.settings.viewmodels.SettingsLastFmViewModel
import paige.navic.ui.screens.settings.viewmodels.SettingsLidaClipsViewModel
import paige.navic.ui.screens.share.viewmodels.ShareDialogViewModel
import paige.navic.ui.screens.share.viewmodels.ShareListViewModel
import paige.navic.ui.screens.song.viewmodels.SongDetailViewModel
import paige.navic.ui.screens.song.viewmodels.SongListViewModel

val viewModelModule = module {
	viewModelOf(::ArtistDetailViewModel)

	viewModel { (song: DomainSong?) ->
		LyricsScreenViewModel(
			song = song,
			repository = get()
		)
	}

	viewModel { (songs: List<DomainSong>, playlistToExclude: String?) ->
		PlaylistUpdateDialogViewModel(
			songs = songs,
			playlistToExclude = playlistToExclude,
			sessionManager = get(),
			dbRepository = get()
		)
	}

	viewModelOf(::AlbumListViewModel)
	viewModel { params ->
		SongListViewModel(
			initialListType = get(),
			artistId = params.getOrNull(),
			repository = get(),
			playlistRepository = get(),
			downloadManager = get(),
			connectivityManager = get(),
			sessionManager = get()
		)
	}
	viewModelOf(::ArtistListViewModel)
	viewModelOf(::SearchViewModel)
	viewModelOf(::GenreDetailViewModel)
	viewModelOf(::GenreListViewModel)
	viewModelOf(::MostPlayedShortcutsViewModel)
	viewModelOf(::RadioListViewModel)
	viewModelOf(::RadioCreateDialogViewModel)
	viewModelOf(::PlaylistListViewModel)
	viewModelOf(::LoginViewModel)
	viewModelOf(::QueueViewModel)
	viewModelOf(::ShareListViewModel)
	viewModelOf(::DeletionViewModel)
	viewModelOf(::ShareDialogViewModel)
	viewModelOf(::PlaylistCreateDialogViewModel)
	viewModelOf(::CollectionDetailViewModel)
	viewModelOf(::SongDetailViewModel)
	viewModelOf(::SettingsDataStorageViewModel)
	viewModelOf(::SettingsAurralViewModel)
	viewModelOf(::SettingsLastFmViewModel)
	viewModelOf(::SettingsBinderyViewModel)
	viewModelOf(::AurralHubViewModel)
	viewModelOf(::ActivityViewModel)
	viewModelOf(::SettingsLidaClipsViewModel)
	viewModelOf(::ChangelogViewModel)
	viewModel { params ->
		NowPlayingViewModel(
			player = params.get(),
			songRepository = get(),
			lidaClipsRepository = get(),
			lidaClipCacheManager = get(),
			downloadManager = get(),
			preferenceManager = get()
		)
	}
	viewModel { params ->
		LidaClipPlayerViewModel(
			songId = params.get(),
			collectionRepository = get(),
			repository = get(),
			cacheManager = get(),
			downloadManager = get(),
			preferenceManager = get()
		)
	}
	viewModelOf(::NavtabsViewModel)
	viewModelOf(::LyricsPriorityViewModel)
}
