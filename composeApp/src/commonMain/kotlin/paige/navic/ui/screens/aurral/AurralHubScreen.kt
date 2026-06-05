package paige.navic.ui.screens.aurral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_create_aurral_flow
import navic.composeapp.generated.resources.action_open_aurral_settings
import navic.composeapp.generated.resources.action_open_station
import navic.composeapp.generated.resources.action_play_flow
import navic.composeapp.generated.resources.action_play_station
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_search_aurral_albums
import navic.composeapp.generated.resources.action_search_aurral_artists
import navic.composeapp.generated.resources.action_see_all
import navic.composeapp.generated.resources.action_start_aurral_flow
import navic.composeapp.generated.resources.info_aurral_album_search_empty
import navic.composeapp.generated.resources.info_aurral_album_search_failed
import navic.composeapp.generated.resources.info_aurral_flow_action_failed
import navic.composeapp.generated.resources.info_aurral_flow_action_queued
import navic.composeapp.generated.resources.info_aurral_flow_action_updated
import navic.composeapp.generated.resources.info_aurral_flow_permission_required
import navic.composeapp.generated.resources.info_aurral_flow_sources_unavailable
import navic.composeapp.generated.resources.info_aurral_search_empty
import navic.composeapp.generated.resources.info_aurral_search_failed
import navic.composeapp.generated.resources.info_aurral_discover_empty
import navic.composeapp.generated.resources.info_aurral_discover_failed
import navic.composeapp.generated.resources.info_aurral_discover_monitor_added
import navic.composeapp.generated.resources.info_aurral_discover_monitor_failed
import navic.composeapp.generated.resources.info_aurral_flows_empty
import navic.composeapp.generated.resources.info_aurral_acquisition_queue_empty
import navic.composeapp.generated.resources.info_aurral_hub_disabled
import navic.composeapp.generated.resources.info_aurral_hub_missing_url
import navic.composeapp.generated.resources.info_aurral_service_status_failed
import navic.composeapp.generated.resources.info_aurral_service_status_loading
import navic.composeapp.generated.resources.info_aurral_service_status_unavailable
import navic.composeapp.generated.resources.title_aurral
import navic.composeapp.generated.resources.title_aurral_acquisition_queue
import navic.composeapp.generated.resources.title_aurral_based_on_library
import navic.composeapp.generated.resources.title_aurral_because_you_like
import navic.composeapp.generated.resources.title_aurral_create_flow
import navic.composeapp.generated.resources.title_aurral_discover
import navic.composeapp.generated.resources.title_aurral_explore_by_tag
import navic.composeapp.generated.resources.title_aurral_flows
import navic.composeapp.generated.resources.title_aurral_global_top
import navic.composeapp.generated.resources.title_aurral_recently_added
import navic.composeapp.generated.resources.title_aurral_recent_releases
import navic.composeapp.generated.resources.title_aurral_recommended_for_you
import navic.composeapp.generated.resources.title_aurral_requests
import navic.composeapp.generated.resources.title_aurral_search
import navic.composeapp.generated.resources.option_aurral_artist_search
import navic.composeapp.generated.resources.option_aurral_flow_name
import navic.composeapp.generated.resources.option_aurral_flow_size
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralAlbumSearchResult
import paige.navic.domain.repositories.AurralArtistSearchResult
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.domain.repositories.AurralFlowActionResult
import paige.navic.domain.repositories.AurralFlowSummary
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Add
import paige.navic.icons.filled.Settings
import paige.navic.icons.outlined.PlaylistPlay
import paige.navic.icons.outlined.Refresh
import paige.navic.icons.outlined.Search
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.AurralArtistMonitorBadge
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.AurralMonitorActionState
import paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.toArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.library.components.AurralDiscoverTagWall

@Composable
fun AurralHubScreen() {
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralRepository = koinInject<AurralRepository>()
	val artistPhotoCacheDao = koinInject<ArtistPhotoCacheDao>()
	val player = koinInject<MediaPlayerViewModel>()
	val viewModel = koinViewModel<AurralHubViewModel>()
	val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
	val discovery by viewModel.discovery.collectAsStateWithLifecycle()
	val artistSearchQuery by viewModel.artistSearchQuery.collectAsStateWithLifecycle()
	val artistSearch by viewModel.artistSearch.collectAsStateWithLifecycle()
	val albumSearch by viewModel.albumSearch.collectAsStateWithLifecycle()
	val flowActionState by viewModel.flowActionState.collectAsStateWithLifecycle()
	val discoverActionState by viewModel.discoverActionState.collectAsStateWithLifecycle()
	val activeFlowActionId by viewModel.activeFlowActionId.collectAsStateWithLifecycle()
	val activeDiscoverArtistId by viewModel.activeDiscoverArtistId.collectAsStateWithLifecycle()
	val stationPlaylists by viewModel.stationPlaylists.collectAsStateWithLifecycle()
	val confirmationQueue by aurralRepository.confirmationQueue.collectAsStateWithLifecycle()
	val cachedArtistPhotos by artistPhotoCacheDao.observeArtistPhotoCache()
		.collectAsStateWithLifecycle(emptyList())
	val artistPhotoCacheEntries = cachedArtistPhotos.map { entry ->
		entry.toArtistHeaderImageCacheEntry()
	}
	val localArtistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "aurralHubLocalArtists",
		parameters = { parametersOf(DomainArtistListType.AlphabeticalByName) }
	)
	val localArtistsState by localArtistsViewModel.artistsState.collectAsStateWithLifecycle()
	val configured = shouldLoadAurralUi(
		aurralEnabled = preferenceManager.aurralEnabled,
		baseUrl = preferenceManager.aurralBaseUrl
	)
	val aurralHubIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = configured && (
			serviceStatus is UiState.Loading ||
				discovery is UiState.Loading ||
				artistSearch is UiState.Loading ||
				albumSearch is UiState.Loading ||
				flowActionState is UiState.Loading ||
				discoverActionState is UiState.Loading
			)
	)
	val scrollState = rememberScrollState()
	BackToTopScrollHandler(scrollState)

	LaunchedEffect(
		preferenceManager.aurralEnabled,
		preferenceManager.aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (configured) {
			viewModel.refreshDiscovery(hydrateMissingImages = false)
			delay(500L)
			viewModel.refreshServiceStatus()
		} else {
			viewModel.clearServiceStatus()
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_aurral)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium,
				actions = {
					TopBarButton(
						onClick = { backStack.add(Screen.Settings.Aurral) }
					) {
						Icon(Icons.Filled.Settings, null)
					}
					TopBarButton(
						onClick = {
							viewModel.refreshDiscovery(hydrateMissingImages = false)
							viewModel.refreshServiceStatus()
						},
						enabled = configured && serviceStatus !is UiState.Loading
					) {
						Icon(Icons.Outlined.Refresh, stringResource(Res.string.action_refresh))
					}
				}
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		AurralConfirmationQueueSnackbar(aurralRepository)
		Box(Modifier.fillMaxSize()) {
			Column(
				Modifier
					.fillMaxSize()
					.padding(top = innerPadding.calculateTopPadding())
					.verticalScroll(scrollState)
					.padding(
						top = 16.dp,
						end = 16.dp,
						start = 16.dp,
						bottom = innerPadding.calculateBottomPadding() + 32.dp
					)
			) {
				when {
					!preferenceManager.aurralEnabled -> AurralHubConfigurationMessage(
						message = stringResource(Res.string.info_aurral_hub_disabled),
						onOpenSettings = { backStack.add(Screen.Settings.Aurral) }
					)

					!configured -> AurralHubConfigurationMessage(
						message = stringResource(Res.string.info_aurral_hub_missing_url),
						onOpenSettings = { backStack.add(Screen.Settings.Aurral) }
					)

					else -> AurralHubContent(
						state = serviceStatus,
						discoveryState = discovery,
						artistSearchQuery = artistSearchQuery,
						artistSearchState = artistSearch,
						albumSearchState = albumSearch,
						flowActionState = flowActionState,
						discoverActionState = discoverActionState,
						activeFlowActionId = activeFlowActionId,
						activeDiscoverArtistId = activeDiscoverArtistId,
						stationPlaylists = stationPlaylists,
						confirmationQueue = confirmationQueue,
						artistPhotoCacheEntries = artistPhotoCacheEntries,
						preferenceManager = preferenceManager,
						onMonitorDiscoverArtist = viewModel::monitorDiscoveredArtist,
						onOpenDiscoverArtist = { artist ->
							aurralArtistRecommendationRoute(
								artist = artist,
								localArtists = localArtistsState.data.orEmpty()
							)?.let(backStack::add)
						},
						onOpenDiscoverCollection = { row ->
							aurralDiscoverCollectionRoute(row)?.let(backStack::add)
						},
						onOpenTag = { tag -> backStack.add(Screen.AurralDiscoverTag(tag)) },
						onOpenSearchAlbum = { album ->
							aurralAlbumSearchDestination(album)?.let(backStack::add)
						},
						onArtistSearchQueryChange = viewModel::updateArtistSearchQuery,
						onSearchArtists = viewModel::searchArtists,
						onSearchAlbums = viewModel::searchAlbums,
						onCreateFlow = viewModel::createFlow,
						onSetFlowEnabled = viewModel::setFlowEnabled,
						onStartFlow = viewModel::startFlow,
						onPlayFlow = { flow -> viewModel.playFlowDirect(flow, player) },
						onPlayStation = { flowId, station -> viewModel.playStation(flowId, station, player) },
						onOpenStation = { station ->
							backStack.add(Screen.CollectionDetail(station.id, "stations"))
						}
					)
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = aurralHubIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = aurralHubIntegrationIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}
}

@Composable
private fun AurralHubConfigurationMessage(
	message: String,
	onOpenSettings: () -> Unit
) {
	Form(Modifier.fillMaxWidth()) {
		FormRow {
			Text(message)
		}
	}
	FormButton(onClick = onOpenSettings) {
		Text(stringResource(Res.string.action_open_aurral_settings))
	}
}

@Composable
private fun AurralHubContent(
	state: UiState<AurralServiceStatus?>,
	discoveryState: UiState<AurralDiscoverySummary?>,
	artistSearchQuery: String,
	artistSearchState: UiState<AurralArtistSearchResult?>,
	albumSearchState: UiState<AurralAlbumSearchResult?>,
	flowActionState: UiState<AurralFlowActionResult?>,
	discoverActionState: UiState<Unit?>,
	activeFlowActionId: String?,
	activeDiscoverArtistId: String?,
	stationPlaylists: List<DomainPlaylist>,
	confirmationQueue: List<AurralConfirmationQueueItem>,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry>,
	preferenceManager: PreferenceManager,
	onMonitorDiscoverArtist: (AurralDiscoverArtist) -> Unit,
	onOpenDiscoverArtist: (AurralDiscoverArtist) -> Unit,
	onOpenDiscoverCollection: (AurralDiscoveryCollectionRow) -> Unit,
	onOpenTag: (String) -> Unit,
	onOpenSearchAlbum: (AurralAlbumSearchItem) -> Unit,
	onArtistSearchQueryChange: (String) -> Unit,
	onSearchArtists: () -> Unit,
	onSearchAlbums: () -> Unit,
	onCreateFlow: (String, Int) -> Unit,
	onSetFlowEnabled: (String, Boolean) -> Unit,
	onStartFlow: (String, Int) -> Unit,
	onPlayFlow: (AurralFlowSummary) -> Unit,
	onPlayStation: (String, DomainPlaylist) -> Unit,
	onOpenStation: (DomainPlaylist) -> Unit
) {
	val snackbarState = LocalSnackbarState.current
	val status = state.data
	val canMonitorArtist = status?.addArtist == true
	val discoverSuccessMessage = stringResource(Res.string.info_aurral_discover_monitor_added)
	val discoverErrorMessage = (discoverActionState as? UiState.Error)?.let { actionError ->
		stringResource(
			Res.string.info_aurral_discover_monitor_failed,
			actionError.error.message ?: actionError.error::class.simpleName ?: "Unknown error"
		)
	}
	val flowSuccessMessage = (flowActionState as? UiState.Success)?.data?.let { result ->
		aurralFlowActionMessage(result)
	}
	val flowErrorMessage = (flowActionState as? UiState.Error)?.let { actionError ->
		stringResource(
			Res.string.info_aurral_flow_action_failed,
			actionError.error.message ?: actionError.error::class.simpleName ?: "Unknown error"
		)
	}
	LaunchedEffect(discoverActionState) {
		when (discoverActionState) {
			is UiState.Success -> if (discoverActionState.data != null) {
				snackbarState.showSnackbar(discoverSuccessMessage)
			}
			is UiState.Error -> discoverErrorMessage?.let { snackbarState.showSnackbar(it) }
			else -> Unit
		}
	}
	LaunchedEffect(flowActionState) {
		when (flowActionState) {
			is UiState.Success -> flowSuccessMessage?.let { snackbarState.showSnackbar(it) }
			is UiState.Error -> flowErrorMessage?.let { snackbarState.showSnackbar(it) }
			else -> Unit
		}
	}
	if (status == null) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					when (state) {
						is UiState.Error -> stringResource(
							Res.string.info_aurral_service_status_failed,
							state.error.message ?: state.error::class.simpleName ?: "Unknown error"
						)

						is UiState.Loading -> stringResource(Res.string.info_aurral_service_status_loading)
						is UiState.Success -> stringResource(Res.string.info_aurral_service_status_unavailable)
					}
				)
			}
		}
	}
	var showCreateFlowDialog by rememberSaveable { mutableStateOf(false) }

	if (status != null) {
		Form(Modifier.fillMaxWidth()) {
			aurralHubSummaryCards(status).forEach { card ->
				AurralHubSummaryRow(card)
			}
		}
	}

	AurralHubArtistSearchSection(
		query = artistSearchQuery,
		artistState = artistSearchState,
		albumState = albumSearchState,
		actionState = discoverActionState,
		activeArtistId = activeDiscoverArtistId,
		canMonitorArtist = canMonitorArtist,
		confirmationQueue = confirmationQueue,
		artistPhotoCacheEntries = artistPhotoCacheEntries,
		preferenceManager = preferenceManager,
		onQueryChange = onArtistSearchQueryChange,
		onSearchArtists = onSearchArtists,
		onSearchAlbums = onSearchAlbums,
		onMonitorArtist = onMonitorDiscoverArtist,
		onOpenArtist = onOpenDiscoverArtist,
		onOpenAlbum = onOpenSearchAlbum
	)

	AurralHubDiscoverSection(
		state = discoveryState,
		actionState = discoverActionState,
		activeArtistId = activeDiscoverArtistId,
		canMonitorArtist = canMonitorArtist,
		confirmationQueue = confirmationQueue,
		artistPhotoCacheEntries = artistPhotoCacheEntries,
		preferenceManager = preferenceManager,
		onMonitorArtist = onMonitorDiscoverArtist,
		onOpenArtist = onOpenDiscoverArtist,
		onOpenAlbum = onOpenSearchAlbum,
		onOpenDiscoverCollection = onOpenDiscoverCollection,
		onOpenTag = onOpenTag
	)

	if (status != null) {
		AurralHubFlowsSection(
			status = status,
			flowActionState = flowActionState,
			activeFlowActionId = activeFlowActionId,
			stationPlaylists = stationPlaylists,
			onCreateFlowClick = { showCreateFlowDialog = true },
			onSetFlowEnabled = onSetFlowEnabled,
			onStartFlow = onStartFlow,
			onPlayFlow = onPlayFlow,
			onPlayStation = onPlayStation,
			onOpenStation = onOpenStation
		)

		AurralHubSectionTitle(stringResource(Res.string.title_aurral_acquisition_queue))
		Form(Modifier.fillMaxWidth()) {
			if (status.acquisitionQueue.isEmpty()) {
				FormRow {
					Text(stringResource(Res.string.info_aurral_acquisition_queue_empty))
				}
			} else {
				status.acquisitionQueue.take(10).forEach { item ->
					AurralHubQueueRow(item)
				}
			}
		}

		if (showCreateFlowDialog) {
			AurralCreateFlowDialog(
				defaultName = nextAurralFlowName(status.flows),
				creating = flowActionState is UiState.Loading && activeFlowActionId == "create",
				onDismissRequest = { showCreateFlowDialog = false },
				onCreate = { name, size ->
					showCreateFlowDialog = false
					onCreateFlow(name, size)
				}
			)
		}
	}

	AnimatedVisibility(state is UiState.Loading) {
		LinearProgressIndicator(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 16.dp)
		)
	}
	if (state is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_service_status_failed,
						state.error.message ?: state.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
	if (flowActionState is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_flow_action_failed,
						flowActionState.error.message
							?: flowActionState.error::class.simpleName
							?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	} else if (flowActionState is UiState.Success && flowActionState.data != null) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(aurralFlowActionMessage(flowActionState.data))
			}
		}
	}
}

@Composable
private fun AurralHubArtistSearchSection(
	query: String,
	artistState: UiState<AurralArtistSearchResult?>,
	albumState: UiState<AurralAlbumSearchResult?>,
	actionState: UiState<Unit?>,
	activeArtistId: String?,
	canMonitorArtist: Boolean,
	confirmationQueue: List<AurralConfirmationQueueItem>,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry>,
	preferenceManager: PreferenceManager,
	onQueryChange: (String) -> Unit,
	onSearchArtists: () -> Unit,
	onSearchAlbums: () -> Unit,
	onMonitorArtist: (AurralDiscoverArtist) -> Unit,
	onOpenArtist: (AurralDiscoverArtist) -> Unit,
	onOpenAlbum: (AurralAlbumSearchItem) -> Unit
) {
	AurralHubSectionTitle(stringResource(Res.string.title_aurral_search))
	val trimmedQuery = query.trim()
	val artists = artistState.data?.artists
		?.let {
			aurralHubSearchArtists(
				aurralDiscoverArtistsWithCachedPhotos(
					artists = it,
					entries = artistPhotoCacheEntries,
					artistArtworkPriority = preferenceManager.artistArtworkPriority,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
			)
		}
		.orEmpty()
	val albums = albumState.data?.albums?.let { aurralHubSearchAlbums(it) }.orEmpty()
	val searchingArtists = artistState is UiState.Loading
	val searchingAlbums = albumState is UiState.Loading
	val searching = searchingArtists || searchingAlbums
	val actionInProgress = actionState is UiState.Loading

	Form(Modifier.fillMaxWidth()) {
		FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
			TextField(
				value = query,
				onValueChange = onQueryChange,
				label = { Text(stringResource(Res.string.option_aurral_artist_search)) },
				singleLine = true,
				enabled = !searching,
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
				keyboardActions = KeyboardActions(
					onSearch = {
						if (trimmedQuery.isNotEmpty()) onSearchArtists()
					}
				),
				modifier = Modifier.fillMaxWidth()
			)
		}
		if (artists.isNotEmpty()) {
			artists.forEach { artist ->
				AurralHubDiscoverArtistRow(
					artist = artist,
					canMonitorArtist = canMonitorArtist,
					actionInProgress = actionInProgress,
					active = activeArtistId == artist.id,
					monitorState = aurralDiscoverArtistMonitorActionState(artist, confirmationQueue),
					preferenceManager = preferenceManager,
					onMonitorArtist = onMonitorArtist,
					onOpenArtist = onOpenArtist
				)
			}
		} else if (trimmedQuery.isNotEmpty() && artistState is UiState.Success && artistState.data != null) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_search_empty))
			}
		}
		if (albums.isNotEmpty()) {
			albums.forEach { album ->
				AurralHubAlbumSearchRow(
					album = album,
					preferenceManager = preferenceManager,
					onOpenAlbum = onOpenAlbum
				)
			}
		} else if (trimmedQuery.isNotEmpty() && albumState is UiState.Success && albumState.data != null) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_album_search_empty))
			}
		}
	}

	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		AurralSearchButton(
			text = stringResource(Res.string.action_search_aurral_artists),
			onClick = onSearchArtists,
			enabled = trimmedQuery.isNotEmpty() && !searchingArtists,
			modifier = Modifier.weight(1f)
		)
		AurralSearchButton(
			text = stringResource(Res.string.action_search_aurral_albums),
			onClick = onSearchAlbums,
			enabled = trimmedQuery.isNotEmpty() && !searchingAlbums,
			modifier = Modifier.weight(1f)
		)
	}

	if (searching) {
		LinearProgressIndicator(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 16.dp)
		)
	}
	if (artistState is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_search_failed,
						artistState.error.message ?: artistState.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
	if (albumState is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_album_search_failed,
						albumState.error.message ?: albumState.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
}

@Composable
private fun AurralSearchButton(
	text: String,
	onClick: () -> Unit,
	enabled: Boolean,
	modifier: Modifier = Modifier
) {
	FormRow(
		modifier = modifier,
		onClick = if (enabled) onClick else null,
		horizontalArrangement = Arrangement.Center,
		contentPadding = PaddingValues(14.dp),
		rounding = 5.dp,
		color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .5f)
	) {
		Icon(
			Icons.Outlined.Search,
			null,
			modifier = Modifier.size(18.dp),
			tint = MaterialTheme.colorScheme.onPrimary
		)
		Spacer(Modifier.width(8.dp))
		Text(text, color = MaterialTheme.colorScheme.onPrimary)
	}
}

@Composable
private fun AurralHubDiscoverSection(
	state: UiState<AurralDiscoverySummary?>,
	actionState: UiState<Unit?>,
	activeArtistId: String?,
	canMonitorArtist: Boolean,
	confirmationQueue: List<AurralConfirmationQueueItem>,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry>,
	preferenceManager: PreferenceManager,
	onMonitorArtist: (AurralDiscoverArtist) -> Unit,
	onOpenArtist: (AurralDiscoverArtist) -> Unit,
	onOpenAlbum: (AurralAlbumSearchItem) -> Unit,
	onOpenDiscoverCollection: (AurralDiscoveryCollectionRow) -> Unit,
	onOpenTag: (String) -> Unit
) {
	AurralHubSectionTitle(stringResource(Res.string.title_aurral_discover))
	val discovery = state.data
	val rows = discovery
		?.let {
			aurralDiscoveryCollectionRows(
				discovery = it,
				artistPhotoCacheEntries = artistPhotoCacheEntries,
				artistArtworkPriority = preferenceManager.artistArtworkPriority,
				externalArtworkEnabled = preferenceManager.aurralEnabled
			)
		}
		.orEmpty()
	val actionInProgress = actionState is UiState.Loading

	when {
		rows.isEmpty() -> Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_discover_empty))
			}
		}

		else -> rows.forEach { row ->
			AurralHubDiscoveryCollectionTitle(row.collectionTitle())
			Form(Modifier.fillMaxWidth()) {
				when (row) {
					is AurralDiscoveryCollectionRow.Artists -> row.artists.forEach { artist ->
						AurralHubDiscoverArtistRow(
							artist = artist,
							canMonitorArtist = canMonitorArtist,
							actionInProgress = actionInProgress,
							active = activeArtistId == artist.id,
							monitorState = aurralDiscoverArtistMonitorActionState(artist, confirmationQueue),
							preferenceManager = preferenceManager,
							onMonitorArtist = onMonitorArtist,
							onOpenArtist = onOpenArtist
						)
					}

					is AurralDiscoveryCollectionRow.Albums -> row.albums.forEach { album ->
						AurralHubAlbumSearchRow(
							album = album,
							preferenceManager = preferenceManager,
							onOpenAlbum = onOpenAlbum
						)
					}

					is AurralDiscoveryCollectionRow.Tags -> FormRow(
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
					) {
						AurralDiscoverTagWall(
							tags = row.tags,
							modifier = Modifier.fillMaxWidth(),
							onOpenTag = onOpenTag
						)
					}
				}
			}
			if (aurralDiscoverCollectionRoute(row) != null) {
				FormButton(
					onClick = { onOpenDiscoverCollection(row) },
					color = MaterialTheme.colorScheme.secondaryContainer
				) {
					Text(stringResource(Res.string.action_see_all))
				}
			}
		}
	}

	if (state is UiState.Loading) {
		LinearProgressIndicator(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 16.dp)
		)
	}
	if (state is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_discover_failed,
						state.error.message ?: state.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
	when (actionState) {
		is UiState.Error -> Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_discover_monitor_failed,
						actionState.error.message ?: actionState.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
		is UiState.Success -> if (actionState.data != null) {
			Form(Modifier.fillMaxWidth()) {
				FormRow {
					Text(stringResource(Res.string.info_aurral_discover_monitor_added))
				}
			}
		}
		else -> Unit
	}
}

@Composable
private fun AurralDiscoveryCollectionRow.collectionTitle(): String =
	when (this) {
		is AurralDiscoveryCollectionRow.Artists ->
			if (kind == AurralDiscoveryCollectionKind.GenreArtists) {
				stringResource(kind.titleResource(), tag.orEmpty())
			} else {
				stringResource(kind.titleResource())
			}

		is AurralDiscoveryCollectionRow.Albums -> stringResource(kind.titleResource())
		is AurralDiscoveryCollectionRow.Tags -> stringResource(kind.titleResource())
	}

@Composable
private fun AurralHubDiscoveryCollectionTitle(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.titleSmall,
		fontWeight = FontWeight.SemiBold,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 10.dp, start = 4.dp, bottom = 4.dp)
	)
}

@Composable
fun AurralHubDiscoverArtistRow(
	artist: AurralDiscoverArtist,
	canMonitorArtist: Boolean,
	actionInProgress: Boolean,
	active: Boolean,
	monitorState: AurralMonitorActionState,
	preferenceManager: PreferenceManager,
	onMonitorArtist: (AurralDiscoverArtist) -> Unit,
	onOpenArtist: (AurralDiscoverArtist) -> Unit
) {
	val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
	val requestHeaders = preferenceManager.aurralRequestHeadersMap()
	val imageRequestHeaders = if (baseUrl != null) {
		aurralRequestHeadersForUrl(baseUrl, artist.imageUrl, requestHeaders)
	} else {
		emptyMap()
	}

	FormRow(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
		onClick = { onOpenArtist(artist) }
	) {
		CoverArt(
			modifier = Modifier.size(56.dp),
			coverArtId = null,
			imageUrl = artist.imageUrl,
			imageRequestHeaders = imageRequestHeaders,
			contentDescription = artist.name,
			fallbackKind = "Artist"
		)
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(start = 12.dp)
		) {
			Text(
				text = artist.name,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = aurralDiscoverArtistDetail(artist),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	if (canMonitorArtist) {
		val monitorEnabled = !actionInProgress && monitorState == AurralMonitorActionState.NotMonitored
		IconButton(
			onClick = { onMonitorArtist(artist) },
			enabled = monitorEnabled
		) {
			if (active) {
				CircularProgressIndicator(modifier = Modifier.size(20.dp))
			} else {
				AurralArtistMonitorBadge(state = monitorState)
			}
		}
	}
}
}

@Composable
private fun AurralHubAlbumSearchRow(
	album: AurralAlbumSearchItem,
	preferenceManager: PreferenceManager,
	onOpenAlbum: (AurralAlbumSearchItem) -> Unit
) {
	val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
	val requestHeaders = preferenceManager.aurralRequestHeadersMap()
	val imageRequestHeaders = if (baseUrl != null) {
		aurralRequestHeadersForUrl(baseUrl, album.coverUrl, requestHeaders)
	} else {
		emptyMap()
	}
	val ownershipStatus = aurralSearchAlbumOwnershipStatus(album)
	val colorFilter = remember(ownershipStatus) {
		if (ownershipStatus == AurralOwnershipStatus.Missing) {
			ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
		} else {
			null
		}
	}

	FormRow(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
		onClick = { onOpenAlbum(album) }
	) {
		Box {
			CoverArt(
				modifier = Modifier.size(56.dp),
				coverArtId = null,
				imageUrl = album.coverUrl,
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = album.title,
				fallbackKind = album.primaryType ?: "Album",
				colorFilter = colorFilter
			)
			AurralOwnershipStatusDot(
				status = ownershipStatus,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(5.dp),
				size = 9.dp
			)
		}
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(start = 12.dp)
		) {
			Text(
				text = album.title,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = aurralAlbumSearchDetail(album),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

private fun aurralAlbumSearchDetail(album: AurralAlbumSearchItem): String {
	val year = album.releaseDate?.trim()?.take(4)?.takeIf { value ->
		value.length == 4 && value.all { it.isDigit() }
	}
	val type = album.primaryType ?: album.secondaryTypes.firstOrNull()
	val status = when {
		album.inLibrary -> "in library"
		else -> album.status
	}
	return listOfNotNull(
		album.artistName,
		year,
		type,
		status
	).joinToString(" • ")
}

@Composable
private fun AurralHubFlowsSection(
	status: AurralServiceStatus,
	flowActionState: UiState<AurralFlowActionResult?>,
	activeFlowActionId: String?,
	stationPlaylists: List<DomainPlaylist>,
	onCreateFlowClick: () -> Unit,
	onSetFlowEnabled: (String, Boolean) -> Unit,
	onStartFlow: (String, Int) -> Unit,
	onPlayFlow: (AurralFlowSummary) -> Unit,
	onPlayStation: (String, DomainPlaylist) -> Unit,
	onOpenStation: (DomainPlaylist) -> Unit
) {
	AurralHubSectionTitle(stringResource(Res.string.title_aurral_flows))

	if (!status.accessFlow) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_flow_permission_required))
			}
		}
		return
	}

	val actionInProgress = flowActionState is UiState.Loading
	Form(Modifier.fillMaxWidth()) {
		if (status.flows.isEmpty()) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_flows_empty))
			}
		} else {
			status.flows.forEach { flow ->
				val matchingStation = aurralStationForFlow(flow, stationPlaylists)
				val playableStation = aurralPlayableStationForFlow(flow, stationPlaylists)
				AurralHubFlowRow(
					flow = flow,
					station = matchingStation,
					playableStation = playableStation,
					offerDirectPlayback = shouldOfferAurralDirectFlowPlayback(flow, stationPlaylists),
					actionInProgress = actionInProgress,
					active = activeFlowActionId == flow.id,
					onSetFlowEnabled = onSetFlowEnabled,
					onStartFlow = onStartFlow,
					onPlayFlow = onPlayFlow,
					onPlayStation = onPlayStation,
					onOpenStation = onOpenStation
				)
			}
		}
	}

	if (canCreateAurralFlow(status)) {
		FormButton(
			onClick = onCreateFlowClick,
			enabled = !actionInProgress,
			color = MaterialTheme.colorScheme.primary
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.Center
			) {
				Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(8.dp))
				Text(stringResource(Res.string.action_create_aurral_flow))
			}
		}
	} else {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(Res.string.info_aurral_flow_sources_unavailable),
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}

@Composable
private fun AurralHubFlowRow(
	flow: AurralFlowSummary,
	station: DomainPlaylist?,
	playableStation: DomainPlaylist?,
	offerDirectPlayback: Boolean,
	actionInProgress: Boolean,
	active: Boolean,
	onSetFlowEnabled: (String, Boolean) -> Unit,
	onStartFlow: (String, Int) -> Unit,
	onPlayFlow: (AurralFlowSummary) -> Unit,
	onPlayStation: (String, DomainPlaylist) -> Unit,
	onOpenStation: (DomainPlaylist) -> Unit
) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.weight(1f)) {
			Text(
				text = flow.name,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = aurralFlowDetail(flow),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			if (active) {
				LinearProgressIndicator(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.height(3.dp)
				)
			}
		}
		Row(
			modifier = Modifier.padding(start = 12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			playableStation?.let { stationToPlay ->
				IconButton(
					onClick = { onPlayStation(flow.id, stationToPlay) },
					enabled = !actionInProgress
				) {
					Icon(Icons.Filled.Play, stringResource(Res.string.action_play_station))
				}
			}
			if (playableStation == null && offerDirectPlayback) {
				IconButton(
					onClick = { onPlayFlow(flow) },
					enabled = !actionInProgress
				) {
					Icon(Icons.Filled.Play, stringResource(Res.string.action_play_flow))
				}
			}
			station?.let { matchingStation ->
				IconButton(
					onClick = { onOpenStation(matchingStation) }
				) {
					Icon(Icons.Outlined.PlaylistPlay, stringResource(Res.string.action_open_station))
				}
			}
			IconButton(
				onClick = { onStartFlow(flow.id, flow.size) },
				enabled = flow.enabled && !actionInProgress
			) {
				Icon(Icons.Outlined.Refresh, stringResource(Res.string.action_start_aurral_flow))
			}
			Switch(
				checked = flow.enabled,
				onCheckedChange = { onSetFlowEnabled(flow.id, it) },
				enabled = !actionInProgress
			)
		}
	}
}

@Composable
fun AurralCreateFlowDialog(
	defaultName: String,
	creating: Boolean,
	onDismissRequest: () -> Unit,
	onCreate: (String, Int) -> Unit
) {
	var name by rememberSaveable(defaultName) { mutableStateOf(defaultName) }
	var sizeText by rememberSaveable { mutableStateOf("30") }
	val size = sizeText.trim().toIntOrNull()
	val valid = name.trim().isNotEmpty() && size != null && size > 0

	FormDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Add, null) },
		title = { Text(stringResource(Res.string.title_aurral_create_flow)) },
		buttons = {
			FormButton(
				onClick = { onCreate(name, size ?: 30) },
				enabled = valid && !creating,
				color = MaterialTheme.colorScheme.primary
			) {
				if (creating) {
					CircularProgressIndicator(modifier = Modifier.size(20.dp))
				} else {
					Text(stringResource(Res.string.action_create_aurral_flow))
				}
			}
			FormButton(
				onClick = onDismissRequest,
				enabled = !creating
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
		content = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				TextField(
					value = name,
					onValueChange = { name = it },
					label = { Text(stringResource(Res.string.option_aurral_flow_name)) },
					singleLine = true
				)
				TextField(
					value = sizeText,
					onValueChange = { sizeText = it.filter(Char::isDigit).take(3) },
					label = { Text(stringResource(Res.string.option_aurral_flow_size)) },
					singleLine = true
				)
			}
		}
	)
}

@Composable
private fun aurralFlowActionMessage(result: AurralFlowActionResult): String =
	result.message
		?: if (result.tracksQueued > 0) {
			stringResource(Res.string.info_aurral_flow_action_queued, result.tracksQueued)
		} else {
			stringResource(Res.string.info_aurral_flow_action_updated)
		}

@Composable
private fun AurralHubSummaryRow(card: AurralHubSummaryCard) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.weight(1f)) {
			Text(
				text = when (card.section) {
					AurralHubSection.Discover -> stringResource(Res.string.title_aurral_discover)
					AurralHubSection.Requests -> stringResource(Res.string.title_aurral_requests)
					AurralHubSection.Flows -> stringResource(Res.string.title_aurral_flows)
				},
				fontWeight = FontWeight.Medium
			)
			Text(
				text = card.detail,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
		Text(
			text = card.value,
			modifier = Modifier.padding(start = 16.dp),
			style = MaterialTheme.typography.bodyMedium,
			color = if (card.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis
		)
	}
}

@Composable
private fun AurralHubSectionTitle(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
	)
}

private fun AurralDiscoveryCollectionKind.titleResource(): StringResource =
	when (this) {
		AurralDiscoveryCollectionKind.RecentlyAddedArtists -> Res.string.title_aurral_recently_added
		AurralDiscoveryCollectionKind.RecentReleases -> Res.string.title_aurral_recent_releases
		AurralDiscoveryCollectionKind.RecommendedArtists -> Res.string.title_aurral_recommended_for_you
		AurralDiscoveryCollectionKind.BasedOnArtists -> Res.string.title_aurral_based_on_library
		AurralDiscoveryCollectionKind.GlobalTopArtists -> Res.string.title_aurral_global_top
		AurralDiscoveryCollectionKind.GenreArtists -> Res.string.title_aurral_because_you_like
		AurralDiscoveryCollectionKind.TopTags -> Res.string.title_aurral_explore_by_tag
	}

@Composable
private fun AurralHubDiscoverTagRow(
	tag: String,
	onOpenTag: (String) -> Unit
) {
	FormRow(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
		onClick = { onOpenTag(tag) }
	) {
		Text(
			text = "#$tag",
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			color = MaterialTheme.colorScheme.primary
		)
	}
}

@Composable
private fun AurralHubQueueRow(item: AurralAcquisitionQueueItem) {
	val progress = aurralAcquisitionProgress(item.status)
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.fillMaxWidth()) {
			Row(Modifier.fillMaxWidth()) {
				Column(Modifier.weight(1f)) {
					Text(
						text = item.albumName,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = item.artistName,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				Text(
					text = item.status,
					modifier = Modifier.padding(start = 16.dp),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			val color = when {
				progress.failed -> MaterialTheme.colorScheme.error
				progress.completed -> MaterialTheme.colorScheme.primary
				else -> MaterialTheme.colorScheme.tertiary
			}
			if (progress.active) {
				LinearProgressIndicator(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.height(3.dp),
					color = color
				)
			} else {
				LinearProgressIndicator(
					progress = { 1f },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.height(3.dp),
					color = color
				)
			}
		}
	}
}
