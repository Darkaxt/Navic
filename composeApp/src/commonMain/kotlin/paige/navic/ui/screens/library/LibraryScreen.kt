package paige.navic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_library
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.QueueDuplicateAction
import paige.navic.domain.models.duplicateQueueActionFor
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.TrackIntegrationServiceAttemptStatus
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicatorOverlayTopPadding
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.dialogs.DeletionDialog
import paige.navic.ui.components.dialogs.DeletionEndpoint
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.screens.album.viewmodels.AlbumListViewModel
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.aurral.AurralHubViewModel
import paige.navic.ui.screens.aurral.aurralAlbumSearchDestination
import paige.navic.ui.screens.aurral.aurralArtistRecommendationRoute
import paige.navic.ui.screens.genre.viewmodels.GenreListViewModel
import paige.navic.ui.screens.library.components.LibraryScreenContent
import paige.navic.ui.screens.login.viewmodels.LoginViewModel
import paige.navic.ui.screens.playlist.dialogs.PlaylistCreateDialog
import paige.navic.ui.screens.playlist.viewmodels.PlaylistListViewModel
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.screens.song.viewmodels.SongListViewModel
import paige.navic.ui.core.LoginUiState
import paige.navic.ui.core.UiState
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
	val quickPicksViewModel = koinViewModel<SongListViewModel>(
		key = "libraryQuickPicks",
		parameters = { parametersOf(DomainSongListType.QuickPicks) }
	)
	val quickPicksState by quickPicksViewModel.songsState.collectAsStateWithLifecycle()
	val selectedQuickPick by quickPicksViewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedQuickPickIsStarred by quickPicksViewModel.starred.collectAsStateWithLifecycle()
	val selectedQuickPickRating by quickPicksViewModel.selectedSongRating.collectAsStateWithLifecycle()
	val quickPickDownloads by quickPicksViewModel.allDownloads.collectAsStateWithLifecycle()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "libraryAlbums",
		parameters = { parametersOf(DomainAlbumListType.Recent) }
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val aurralAlbumRequests by albumsViewModel.aurralAlbumRequests.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()

	val newestAlbumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "libraryNewestAlbums",
		parameters = { parametersOf(DomainAlbumListType.Newest) }
	)
	val newestAlbumsState by newestAlbumsViewModel.albumsState.collectAsStateWithLifecycle()

	val starredAlbumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "libraryStarredAlbums",
		parameters = { parametersOf(DomainAlbumListType.Starred) }
	)
	val starredAlbumsState by starredAlbumsViewModel.albumsState.collectAsStateWithLifecycle()

	val playlistsViewModel = koinViewModel<PlaylistListViewModel>()
	val playlistsState by playlistsViewModel.playlistsState.collectAsStateWithLifecycle()
	val selectedPlaylist by playlistsViewModel.selectedPlaylist.collectAsStateWithLifecycle()

	val artistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "libraryArtists",
		parameters = { parametersOf(DomainArtistListType.AlphabeticalByName) }
	)
	val artistsState by artistsViewModel.artistsState.collectAsStateWithLifecycle()
	val selectedArtist by artistsViewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by artistsViewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val selectedArtistIsStarred by artistsViewModel.starred.collectAsStateWithLifecycle()

	val genresViewModel = koinViewModel<GenreListViewModel>()
	val genresState by genresViewModel.genresState.collectAsStateWithLifecycle()

	val mostPlayedShortcutsViewModel = koinViewModel<MostPlayedShortcutsViewModel>()
	val mostPlayedShortcutsState by mostPlayedShortcutsViewModel.shortcutsState.collectAsStateWithLifecycle()

	val loginViewModel = koinViewModel<LoginViewModel>()
	val loginState by loginViewModel.loginState.collectAsStateWithLifecycle()
	val isLoggedIn = loginState is LoginUiState.Success

	val aurralViewModel = koinViewModel<AurralHubViewModel>(key = "libraryAurral")
	val aurralDiscovery by aurralViewModel.discovery.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var playlistDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
	var playlistCreateDialogShown by rememberSaveable { mutableStateOf(false) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var queueDuplicateAction by remember { mutableStateOf<QueueDuplicateAction?>(null) }

	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val quickPicksEnabled = preferenceManager.quickPicksEnabled
	val quickPicksLimit = preferenceManager.quickPicksLimit
	val quickPicksMinDurationSeconds = preferenceManager.quickPicksMinDurationSeconds
	val aurralConfigured = preferenceManager.aurralEnabled &&
		configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null
	val aurralCollectionRowsState = libraryAurralCollectionRowsState(
		aurralConfigured = aurralConfigured,
		discoveryState = aurralDiscovery
	)
	val libraryIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = aurralConfigured && aurralDiscovery is UiState.Loading,
		binderyLoading = false
	)

	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	TrackIntegrationServiceAttemptStatus(
		preferenceManager = preferenceManager,
		service = IntegrationService.Aurral,
		enabled = aurralConfigured,
		loading = aurralDiscovery is UiState.Loading,
		failed = aurralDiscovery is UiState.Error,
		available = aurralDiscovery is UiState.Success && aurralDiscovery.data != null
	)

	fun queueSongOrConfirmDuplicate(
		song: DomainSong,
		action: QueueDuplicateAction,
		onQueue: () -> Unit
	) {
		val duplicateAction = duplicateQueueActionFor(
			queueSongIds = player.uiState.value.queue.map { it.id },
			songId = song.id,
			action = action
		)
		if (duplicateAction != null) {
			songToQueue = song
			queueDuplicateAction = duplicateAction
		} else {
			onQueue()
		}
	}

	LaunchedEffect(isLoggedIn, quickPicksEnabled, quickPicksLimit, quickPicksMinDurationSeconds) {
		if (!isLoggedIn) return@LaunchedEffect

		if (quickPicksEnabled) {
			quickPicksViewModel.refreshSongs(false)
		}
		albumsViewModel.refreshAlbums(false)
		newestAlbumsViewModel.refreshAlbums(false)
		starredAlbumsViewModel.refreshAlbums(false)
		playlistsViewModel.refreshPlaylists(false)
		artistsViewModel.refreshArtists(false)
		genresViewModel.refreshGenres(false)
	}

	LaunchedEffect(
		isLoggedIn,
		aurralConfigured,
		preferenceManager.aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (isLoggedIn && aurralConfigured) {
			aurralViewModel.refreshDiscovery(hydrateMissingImages = true)
		} else {
			aurralViewModel.clearServiceStatus()
		}
	}

	Scaffold(
		topBar = { RootTopBar({ Text(stringResource(Res.string.title_library)) }, scrollBehavior) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		Box(Modifier.fillMaxSize()) {
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = innerPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = albumsState !is UiState.Loading &&
					newestAlbumsState !is UiState.Loading &&
					starredAlbumsState !is UiState.Loading &&
					(!quickPicksEnabled || quickPicksState !is UiState.Loading) &&
					playlistsState !is UiState.Loading &&
					artistsState !is UiState.Loading &&
					genresState !is UiState.Loading &&
					mostPlayedShortcutsState !is UiState.Loading &&
					(!aurralConfigured || aurralDiscovery !is UiState.Loading),
				onRefresh = {
					if (quickPicksEnabled) {
						quickPicksViewModel.refreshSongs(true)
					}
					albumsViewModel.refreshAlbums(true)
					newestAlbumsViewModel.refreshAlbums(false)
					starredAlbumsViewModel.refreshAlbums(false)
					playlistsViewModel.refreshPlaylists(true)
					artistsViewModel.refreshArtists(true)
					genresViewModel.refreshGenres(true)
					if (aurralConfigured) {
						aurralViewModel.refreshDiscovery(hydrateMissingImages = true)
					}
				},
				key = listOf(
					quickPicksState,
					albumsState,
					newestAlbumsState,
					starredAlbumsState,
					playlistsState,
					artistsState,
					genresState,
					mostPlayedShortcutsState,
					aurralDiscovery
				)
			) {
				LibraryScreenContent(
				scrollBehavior = scrollBehavior,
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },

				quickPicksEnabled = quickPicksEnabled,
				quickPicksState = quickPicksState,
				selectedQuickPick = selectedQuickPick,
				selectedQuickPickIsStarred = selectedQuickPickIsStarred,
				selectedQuickPickRating = selectedQuickPickRating,
				quickPickDownloads = quickPickDownloads,
				onSelectQuickPick = { quickPicksViewModel.selectSong(it) },
				onClearQuickPickSelection = { quickPicksViewModel.clearSelection() },
				onStarSelectedQuickPick = { quickPicksViewModel.starSong(it) },
				onStartQuickPickRadio = { player.startSongRadio(it) },
				onPlayQuickPickNext = { song ->
					queueSongOrConfirmDuplicate(song, QueueDuplicateAction.PlayNext) {
						player.playNextSingle(song)
					}
				},
				onAddQuickPickToQueue = { song ->
					queueSongOrConfirmDuplicate(song, QueueDuplicateAction.AddToQueue) {
						player.addToQueueSingle(song)
					}
				},
				onPlayQuickPick = { song ->
					player.clearQueue()
					player.addToQueueSingle(song)
					player.playAt(0)
				},
				onRateSelectedQuickPick = { quickPicksViewModel.rateSelectedSong(it) },
				onDownloadQuickPick = { quickPicksViewModel.downloadSong(it) },
				onCancelQuickPickDownload = { quickPicksViewModel.cancelDownload(it.id) },
				onDeleteQuickPickDownload = { quickPicksViewModel.deleteDownload(it.id) },

				mostPlayedShortcutsState = mostPlayedShortcutsState,

				albumsState = albumsState,
				newestAlbumsState = newestAlbumsState,
				starredAlbumsState = starredAlbumsState,
				aurralAlbumRequests = libraryAlbumAurralRequests(
					showAurralHub = aurralConfigured,
					requests = aurralAlbumRequests
				),
				selectedAlbum = selectedAlbum,
				selectedAlbumIsStarred = selectedAlbumIsStarred,
				selectedAlbumRating = selectedAlbumRating,
				onSelectAlbum = { albumsViewModel.selectAlbum(it) },
				onClearAlbumSelection = { albumsViewModel.clearSelection() },
				onStarSelectedAlbum = { albumsViewModel.starAlbum(it) },
				onPlayAlbumNext = { if (selectedAlbum != null) player.playNext(selectedAlbum as DomainSongCollection)},
				onAddAlbumToQueue = { if (selectedAlbum != null) player.addToQueue(selectedAlbum as DomainSongCollection)},
				onRateSelectedAlbum = { albumsViewModel.setRating(it) },

				artistsState = artistsState,
				selectedArtist = selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				selectedArtistIsStarred = selectedArtistIsStarred,
				onSelectArtist = { artistsViewModel.selectArtist(it) },
				onClearArtistSelection = { artistsViewModel.clearSelection() },
				onStarSelectedArtist = { artistsViewModel.starArtist(it) },
				onPlayArtistNext = { if (selectedArtist != null) artistsViewModel.playArtistAlbumsNext(player)},
				onAddArtistToQueue = { if (selectedArtist != null) artistsViewModel.addArtistAlbumsToQueue(player)},
				aurralLibraryArtists = aurralDiscovery.data?.libraryArtists.orEmpty(),
				aurralCollectionRowsState = aurralCollectionRowsState,
				onOpenAurralDiscoverArtist = { artist ->
					aurralArtistRecommendationRoute(
						artist = artist,
						localArtists = artistsState.data.orEmpty()
					)?.let(backStack::add)
				},
				onOpenAurralDiscoverAlbum = { album ->
					aurralAlbumSearchDestination(album)?.let(backStack::add)
				},

				playlistsState = playlistsState,
				selectedPlaylist = selectedPlaylist,
				onSelectPlaylist = { playlistsViewModel.selectPlaylist(it) },
				onClearPlaylistSelection = { playlistsViewModel.clearSelection() },
				onDeletePlaylist = { playlistDeletionId = it },
				onPlayPlaylistNext = { playlistsViewModel.playSelectedPlaylistNext(player) },
				onAddPlaylistToQueue = { playlistsViewModel.addSelectedPlaylistToQueue(player) },

				genresState = genresState
				)
			}
			IntegrationLoadingIndicatorStrip(
				indicators = libraryIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = libraryIntegrationIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(
						start = 12.dp,
						top = integrationLoadingIndicatorOverlayTopPadding(
							statusBarTop = WindowInsets.statusBars.asPaddingValues()
								.calculateTopPadding()
						)
					)
			)
		}
	}

	val flattenedErrors = listOf(
		(quickPicksState as? UiState.Error)?.error,
		(albumsState as? UiState.Error)?.error,
		(newestAlbumsState as? UiState.Error)?.error,
		(starredAlbumsState as? UiState.Error)?.error,
		(playlistsState as? UiState.Error)?.error,
		(artistsState as? UiState.Error)?.error,
		(genresState as? UiState.Error)?.error,
		(mostPlayedShortcutsState as? UiState.Error)?.error
	).mapNotNull { it?.stackTraceToString() }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")

	ErrorSnackbar(
		error = flattenedErrors?.let { Error(it) },
		onClearError = {
			quickPicksViewModel.clearError()
			albumsViewModel.clearError()
			newestAlbumsViewModel.clearError()
			starredAlbumsViewModel.clearError()
			playlistsViewModel.clearError()
			artistsViewModel.clearError()
			genresViewModel.clearError()
			mostPlayedShortcutsViewModel.clearError()
			aurralViewModel.clearServiceStatus()
		}
	)

    ShareDialog(
        id = shareId,
        onIdClear = { shareId = null },
        expiry = shareExpiry,
        onExpiryChange = { shareExpiry = it }
    )

    DeletionDialog(
        endpoint = DeletionEndpoint.PLAYLIST,
        id = playlistDeletionId,
        onIdClear = { playlistDeletionId = null },
        onRefresh = { playlistsViewModel.refreshPlaylists(false) }
    )

	if (playlistCreateDialogShown) {
        PlaylistCreateDialog(
            onDismissRequest = { playlistCreateDialogShown = false },
            onRefresh = { playlistsViewModel.refreshPlaylists(true) }
        )
	}

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = {
				songToQueue = null
				queueDuplicateAction = null
			},
			onConfirm = {
				songToQueue?.let { song ->
					when (queueDuplicateAction) {
						QueueDuplicateAction.PlayNext -> player.playNextSingle(song)
						QueueDuplicateAction.AddToQueue,
						null -> player.addToQueueSingle(song)
					}
				}
			}
		)
	}
}
