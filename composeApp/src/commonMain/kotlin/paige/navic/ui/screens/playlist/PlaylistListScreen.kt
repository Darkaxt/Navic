package paige.navic.ui.screens.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_aurral_create_flow
import navic.composeapp.generated.resources.title_create_playlist
import navic.composeapp.generated.resources.title_playlists
import navic.composeapp.generated.resources.title_stations
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.regularPlaylists
import paige.navic.domain.models.settings.BottomBarCollapseMode
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.models.stationPlaylists
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Add
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.dialogs.DeletionDialog
import paige.navic.ui.components.dialogs.DeletionEndpoint
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.aurral.AurralCreateFlowDialog
import paige.navic.ui.screens.aurral.AurralHubViewModel
import paige.navic.ui.screens.aurral.nextAurralFlowName
import paige.navic.ui.screens.playlist.components.PlaylistListScreenSortButton
import paige.navic.ui.screens.playlist.components.playlistListScreenContent
import paige.navic.ui.screens.playlist.dialogs.PlaylistCreateDialog
import paige.navic.ui.screens.playlist.viewmodels.PlaylistListViewModel
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.util.ui.withoutTop
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistListScreen(
	nested: Boolean = false,
	stationsOnly: Boolean = false
) {
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<PlaylistListViewModel>()
	val player = koinInject<MediaPlayerViewModel>()
	val playlistsState by viewModel.playlistsState.collectAsState()
	val displayedPlaylistsState = playlistsState.filterByStationMode(stationsOnly)
	val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
	val selectedSorting by viewModel.selectedSorting.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val aurralConfigured = preferenceManager.aurralEnabled &&
		configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null
	val aurralViewModel = koinViewModel<AurralHubViewModel>(key = "stationListAurral")
	val aurralServiceStatus by aurralViewModel.serviceStatus.collectAsStateWithLifecycle()
	val aurralFlowActionState by aurralViewModel.flowActionState.collectAsStateWithLifecycle()
	val aurralActiveFlowActionId by aurralViewModel.activeFlowActionId.collectAsStateWithLifecycle()
	val playlistIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = stationsOnly && aurralConfigured && (
			aurralServiceStatus is UiState.Loading ||
				aurralFlowActionState is UiState.Loading
			)
	)

	val platformContext = LocalPlatformContext.current
	val scrollManager = LocalBottomBarScrollManager.current

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var deletionId by remember { mutableStateOf<String?>(null) }
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
	val scaleInSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

	var createDialogShown by rememberSaveable { mutableStateOf(false) }

	val gridState = rememberLazyGridState()
	val title = if (stationsOnly) Res.string.title_stations else Res.string.title_playlists
	val tab = if (stationsOnly) "stations" else "playlists"

	LaunchedEffect(
		stationsOnly,
		aurralConfigured,
		preferenceManager.aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (stationsOnly && aurralConfigured) {
			aurralViewModel.refreshServiceStatus()
		} else if (stationsOnly) {
			aurralViewModel.clearServiceStatus()
		}
	}

	LaunchedEffect(stationsOnly, aurralFlowActionState) {
		if (stationsOnly && aurralFlowActionState is UiState.Success && aurralFlowActionState.data != null) {
			viewModel.refreshPlaylists(true)
		}
	}

	val actions: @Composable RowScope.() -> Unit = {
		PlaylistListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setSorting(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) }
		)
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					title = { Text(stringResource(title)) },
					scrollBehavior = scrollBehavior,
					actions = actions
				)
			} else {
				NestedTopBar(
					title = { Text(stringResource(title)) },
					actions = actions
				)
			}
		},
		floatingActionButton = {
			if (!stationsOnly || aurralConfigured) {
				AnimatedContent(
					!scrollManager.isTriggered
						|| preferenceManager.bottomBarCollapseMode == BottomBarCollapseMode.Never,
					transitionSpec = {
						val transformOrigin = TransformOrigin(0f, 1f)
						(slideInHorizontally(slideSpec) { it / 2 }
							+ scaleIn(scaleInSpec, transformOrigin = transformOrigin)
							+ slideInVertically(slideSpec) { it / 2 })
							.togetherWith(slideOutHorizontally(slideSpec) { it / 2 }
								+ scaleOut(transformOrigin = transformOrigin)
								+ slideOutVertically(slideSpec) { it / 2 })
							.using(SizeTransform(clip = false))
					}
				) { notScrolled ->
					if (notScrolled) {
						MediumFloatingActionButton(
							shape = MaterialTheme.shapes.large,
							containerColor = MaterialTheme.colorScheme.primary,
							onClick = {
								platformContext.clickSound()
								createDialogShown = true
							}
						) {
							Icon(
								imageVector = Icons.Outlined.Add,
								contentDescription = stringResource(
									if (stationsOnly) {
										Res.string.title_aurral_create_flow
									} else {
										Res.string.title_create_playlist
									}
								),
								modifier = Modifier.size(26.dp)
							)
						}
					}
				}
			}
		},
		bottomBar = {
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
				finished = playlistsState !is UiState.Loading,
				onRefresh = {
					viewModel.refreshPlaylists(true)
					if (stationsOnly && aurralConfigured) {
						aurralViewModel.refreshServiceStatus()
					}
				},
				key = playlistsState
			) {
				ArtGrid(
					modifier = if (!nested)
						Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
					else Modifier,
					state = gridState,
					contentPadding = innerPadding.withoutTop(),
					verticalArrangement = if ((displayedPlaylistsState as? UiState.Success)?.data?.isEmpty() == true)
						Arrangement.Center
					else Arrangement.spacedBy(12.dp)
				) {
					playlistListScreenContent(
						state = displayedPlaylistsState,
						tab = tab,
						stationsOnly = stationsOnly,
						selectedPlaylist = selectedPlaylist,
						onUpdateSelection = { viewModel.selectPlaylist(it) },
						onClearSelection = { viewModel.clearSelection() },
						onSetShareId = { newShareId ->
							shareId = newShareId
						},
						onSetDeletionId = { newDeletionId ->
							deletionId = newDeletionId
						},
						onPlayNext = { viewModel.playSelectedPlaylistNext(player) },
						onAddToQueue = { viewModel.addSelectedPlaylistToQueue(player) }
					)
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = playlistIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = playlistIntegrationIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
				)
		}
	}

	ErrorSnackbar(
		error = (playlistsState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	DeletionDialog(
		endpoint = DeletionEndpoint.PLAYLIST,
		id = deletionId,
		onIdClear = { deletionId = null },
		onRefresh = { viewModel.refreshPlaylists(true) }
	)

	if (createDialogShown) {
		if (stationsOnly) {
			AurralCreateFlowDialog(
				defaultName = nextAurralFlowName(aurralServiceStatus.data?.flows.orEmpty()),
				creating = aurralFlowActionState is UiState.Loading && aurralActiveFlowActionId == "create",
				onDismissRequest = { createDialogShown = false },
				onCreate = { name, size ->
					createDialogShown = false
					aurralViewModel.createFlow(name, size)
				}
			)
		} else {
			PlaylistCreateDialog(
				onDismissRequest = { createDialogShown = false },
				onRefresh = { viewModel.refreshPlaylists(true) }
			)
		}
	}
}

private fun UiState<List<DomainPlaylist>>.filterByStationMode(
	stationsOnly: Boolean
): UiState<List<DomainPlaylist>> {
	fun List<DomainPlaylist>.filtered() =
		if (stationsOnly) stationPlaylists() else regularPlaylists()

	return when (this) {
		is UiState.Error -> UiState.Error(error, data?.filtered())
		is UiState.Loading -> UiState.Loading(data?.filtered())
		is UiState.Success -> UiState.Success(data.filtered())
	}
}
