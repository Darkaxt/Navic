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
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import navic.composeapp.generated.resources.Res
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
import paige.navic.domain.models.OptionalIntegrationResult
import paige.navic.domain.models.settings.BottomBarVisibilityMode
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
import paige.navic.data.remote.aurral.aurralRequestHeadersForUrl
import paige.navic.data.remote.aurral.configuredAurralBaseUrl
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
import paige.navic.ui.components.common.AurralIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.OptionalIntegrationStatus
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
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
	val discoveryAvailability by viewModel.discoveryAvailability.collectAsStateWithLifecycle()
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
	val artistPhotoCacheEntries by produceState<List<ArtistHeaderImageCacheEntry>>(
		initialValue = emptyList(),
		cachedArtistPhotos
	) {
		value = withContext(Dispatchers.Default) {
			cachedArtistPhotos.map { entry ->
				entry.toArtistHeaderImageCacheEntry()
			}
		}
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
							viewModel.refreshDiscovery(
								hydrateMissingImages = false,
								forceRefresh = true
							)
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
						discoveryAvailability = discoveryAvailability,
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
					loadingIndicators = aurralHubIntegrationIndicators,
					relevantServices = AurralIntegrationServices
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
	discoveryAvailability: OptionalIntegrationResult<AurralDiscoverySummary>?,
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
	OptionalIntegrationStatus(
		result = discoveryAvailability.takeUnless { it is OptionalIntegrationResult.Empty },
		modifier = Modifier.padding(vertical = 8.dp)
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
