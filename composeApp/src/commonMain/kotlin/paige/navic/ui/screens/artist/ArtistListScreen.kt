package paige.navic.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_aurral_search
import navic.composeapp.generated.resources.count_albums
import navic.composeapp.generated.resources.title_artists
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.AurralArtistMonitorBadge
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.MusicIntegrationServices
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.components.sheets.ArtistSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.aurral.AurralArtistSearchDialog
import paige.navic.ui.screens.aurral.AurralConfirmationQueueSnackbar
import paige.navic.ui.screens.aurral.AurralHubViewModel
import paige.navic.ui.screens.aurral.aurralArtistRoute
import paige.navic.ui.screens.artist.components.ArtistListScreenSortButton
import paige.navic.ui.screens.artist.components.ArtistListScreenContent
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Add

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistListScreen(
	nested: Boolean = false,
	listType: DomainArtistListType
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralRepository = koinInject<AurralRepository>()

	val viewModel = koinViewModel<ArtistListViewModel>(
		key = listType.toString(),
		parameters = { parametersOf(listType) }
	)
	val artistsState by viewModel.artistsState.collectAsStateWithLifecycle()
	val selectedArtist by viewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by viewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val selectedSorting by viewModel.listType.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val aurralConfigured = preferenceManager.aurralEnabled &&
		configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null
	val aurralViewModel = koinViewModel<AurralHubViewModel>(key = "artistListAurral")
	val aurralServiceStatus by aurralViewModel.serviceStatus.collectAsStateWithLifecycle()
	val aurralArtistSearchQuery by aurralViewModel.artistSearchQuery.collectAsStateWithLifecycle()
	val aurralArtistSearch by aurralViewModel.artistSearch.collectAsStateWithLifecycle()
	val aurralDiscoverActionState by aurralViewModel.discoverActionState.collectAsStateWithLifecycle()
	val aurralActiveDiscoverArtistId by aurralViewModel.activeDiscoverArtistId.collectAsStateWithLifecycle()
	val aurralConfirmationQueue by aurralRepository.confirmationQueue.collectAsStateWithLifecycle()
	var aurralSearchDialogShown by rememberSaveable { mutableStateOf(false) }
	val artistListIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = aurralConfigured && (
			aurralServiceStatus is UiState.Loading ||
				aurralArtistSearch is UiState.Loading ||
				aurralDiscoverActionState is UiState.Loading
			)
	)
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val actions: @Composable RowScope.() -> Unit = {
		if (aurralConfigured) {
			TopBarButton(
				onClick = {
					aurralSearchDialogShown = true
					aurralViewModel.refreshServiceStatus()
				}
			) {
				Icon(Icons.Outlined.Add, stringResource(Res.string.title_aurral_search))
			}
		}
		ArtistListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setListType(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) }
		)
	}

	LaunchedEffect(
		aurralConfigured,
		preferenceManager.aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (aurralConfigured) {
			aurralViewModel.refreshServiceStatus()
		} else {
			aurralViewModel.clearServiceStatus()
		}
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar({ Text(stringResource(Res.string.title_artists)) }, scrollBehavior, actions)
			} else {
				NestedTopBar({ Text(stringResource(Res.string.title_artists)) }, actions)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (!nested || preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		Box(Modifier.fillMaxSize()) {
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = innerPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = artistsState !is UiState.Loading,
				onRefresh = { viewModel.refreshArtists(true) },
				key = artistsState
			) {
				ArtistListScreenContent(
					state = artistsState,
					starred = starred,
					selectedArtist = selectedArtist,
					selectedArtistAlbums = selectedArtistAlbums,
					gridState = viewModel.gridState,
					scrollBehavior = scrollBehavior,
					innerPadding = innerPadding,
					nested = nested,
					onUpdateSelection = { viewModel.selectArtist(it) },
					onClearSelection = { viewModel.clearSelection() },
					onSetStarred = { viewModel.starArtist(it) },
					onPlayNext = { viewModel.playArtistAlbumsNext(player) },
					onAddToQueue = { viewModel.addArtistAlbumsToQueue(player) }
				)
			}
			IntegrationLoadingIndicatorStrip(
				indicators = artistListIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = artistListIntegrationIndicators,
					relevantServices = MusicIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ErrorSnackbar(
		error = (artistsState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
	AurralConfirmationQueueSnackbar(aurralRepository)

	if (aurralSearchDialogShown) {
		AurralArtistSearchDialog(
			query = aurralArtistSearchQuery,
			artistState = aurralArtistSearch,
			actionState = aurralDiscoverActionState,
			activeArtistId = aurralActiveDiscoverArtistId,
			canMonitorArtist = aurralServiceStatus.data?.addArtist ?: true,
			confirmationQueue = aurralConfirmationQueue,
			preferenceManager = preferenceManager,
			onQueryChange = aurralViewModel::updateArtistSearchQuery,
			onSearchArtists = aurralViewModel::searchArtists,
			onMonitorArtist = aurralViewModel::monitorDiscoveredArtist,
			onOpenArtist = { artist ->
				aurralArtistRoute(artist)?.let { route ->
					aurralSearchDialogShown = false
					backStack.add(route)
				}
			},
			onDismissRequest = { aurralSearchDialogShown = false }
		)
	}
}

@Composable
fun ArtistsScreenItem(
	modifier: Modifier = Modifier,
	tab: String,
	artist: DomainArtist,
	selected: Boolean,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	starred: Boolean,
	aurralMonitorState: AurralMonitorActionState? = null,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSetStarred: (starred: Boolean) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	val uriHandler = LocalUriHandler.current
	val artistImageUrl = artistImageUrlForExternalArtworkPolicy(
		artist = artist,
		externalArtworkEnabled = preferenceManager.aurralEnabled
	)
	val artistImageRequestHeaders = aurralRequestHeadersForUrl(
		baseUrl = preferenceManager.aurralBaseUrl,
		imageUrl = artistImageUrl,
		requestHeaders = preferenceManager.aurralRequestHeadersMap()
	)

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }

	Box(modifier) {
		ArtGridItem(
			onClick = dropUnlessResumed {
				platformContext.clickSound()
				backStack.add(Screen.ArtistDetail(artist.id))
			},
			onLongClick = onSelect,
			coverArtId = artistCoverArtIdForExternalArtworkPolicy(
				artist = artist,
				externalArtworkEnabled = preferenceManager.aurralEnabled
			),
			imageUrl = artistImageUrl,
			imageRequestHeaders = artistImageRequestHeaders,
			imageDiagnosticLabel = "artist-list-${artist.id}",
			title = artist.name,
			subtitle = pluralStringResource(
				Res.plurals.count_albums,
				artist.albumCount,
				artist.albumCount
			),
			coverOverlay = aurralMonitorState?.let { state ->
				{
					AurralArtistMonitorBadge(
						state = state,
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(8.dp)
					)
				}
			},
			id = artist.id,
			tab = tab
		)
		if (selected) {
			ArtistSheet(
				onDismissRequest = onDeselect,
				artist = artist,
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				onAddAllToPlaylist = { playlistDialogShown = true },
				onViewOnLastFm = { 
					onDeselect()
					artist.lastFmUrl?.let { url ->
						uriHandler.openUri(url)
					}
				},
				onViewOnMusicBrainz = { 								
					onDeselect()
					artist.musicBrainzId?.let { id ->
						uriHandler.openUri(
							"https://musicbrainz.org/artist/$id"
						)
					}
				},
				starred = starred,
				onSetStarred = { onSetStarred(!starred) }
			)
		}
		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = selectedArtistAlbums?.flatMap { it.songs }.orEmpty().toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}
