package paige.navic.ui.screens.aurral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_create_aurral_flow
import navic.composeapp.generated.resources.action_open_aurral_settings
import navic.composeapp.generated.resources.action_open_station
import navic.composeapp.generated.resources.action_play_station
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_start_aurral_flow
import navic.composeapp.generated.resources.info_aurral_flow_action_failed
import navic.composeapp.generated.resources.info_aurral_flow_action_queued
import navic.composeapp.generated.resources.info_aurral_flow_action_updated
import navic.composeapp.generated.resources.info_aurral_flow_permission_required
import navic.composeapp.generated.resources.info_aurral_flow_sources_unavailable
import navic.composeapp.generated.resources.info_aurral_flows_empty
import navic.composeapp.generated.resources.info_aurral_acquisition_queue_empty
import navic.composeapp.generated.resources.info_aurral_hub_disabled
import navic.composeapp.generated.resources.info_aurral_hub_missing_url
import navic.composeapp.generated.resources.info_aurral_service_status_failed
import navic.composeapp.generated.resources.info_aurral_service_status_loading
import navic.composeapp.generated.resources.info_aurral_service_status_unavailable
import navic.composeapp.generated.resources.title_aurral
import navic.composeapp.generated.resources.title_aurral_acquisition_queue
import navic.composeapp.generated.resources.title_aurral_create_flow
import navic.composeapp.generated.resources.title_aurral_discover
import navic.composeapp.generated.resources.title_aurral_flows
import navic.composeapp.generated.resources.title_aurral_requests
import navic.composeapp.generated.resources.option_aurral_flow_name
import navic.composeapp.generated.resources.option_aurral_flow_size
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralFlowActionResult
import paige.navic.domain.repositories.AurralFlowSummary
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Add
import paige.navic.icons.filled.Settings
import paige.navic.icons.outlined.PlaylistPlay
import paige.navic.icons.outlined.Refresh
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen

@Composable
fun AurralHubScreen() {
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val viewModel = koinViewModel<AurralHubViewModel>()
	val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
	val flowActionState by viewModel.flowActionState.collectAsStateWithLifecycle()
	val activeFlowActionId by viewModel.activeFlowActionId.collectAsStateWithLifecycle()
	val stationPlaylists by viewModel.stationPlaylists.collectAsStateWithLifecycle()
	val configured = preferenceManager.aurralEnabled &&
		configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null

	LaunchedEffect(
		preferenceManager.aurralEnabled,
		preferenceManager.aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (configured) {
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
						onClick = { viewModel.refreshServiceStatus() },
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
		Column(
			Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 16.dp, end = 16.dp, start = 16.dp, bottom = 32.dp)
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
					flowActionState = flowActionState,
					activeFlowActionId = activeFlowActionId,
					stationPlaylists = stationPlaylists,
					onCreateFlow = viewModel::createFlow,
					onSetFlowEnabled = viewModel::setFlowEnabled,
					onStartFlow = viewModel::startFlow,
					onPlayStation = { flowId, station -> viewModel.playStation(flowId, station, player) },
					onOpenStation = { station ->
						backStack.add(Screen.CollectionDetail(station.id, "stations"))
					}
				)
			}
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
	flowActionState: UiState<AurralFlowActionResult?>,
	activeFlowActionId: String?,
	stationPlaylists: List<DomainPlaylist>,
	onCreateFlow: (String, Int) -> Unit,
	onSetFlowEnabled: (String, Boolean) -> Unit,
	onStartFlow: (String, Int) -> Unit,
	onPlayStation: (String, DomainPlaylist) -> Unit,
	onOpenStation: (DomainPlaylist) -> Unit
) {
	val status = state.data
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
		return
	}
	var showCreateFlowDialog by rememberSaveable { mutableStateOf(false) }

	Form(Modifier.fillMaxWidth()) {
		aurralHubSummaryCards(status).forEach { card ->
			AurralHubSummaryRow(card)
		}
	}

	AurralHubFlowsSection(
		status = status,
		flowActionState = flowActionState,
		activeFlowActionId = activeFlowActionId,
		stationPlaylists = stationPlaylists,
		onCreateFlowClick = { showCreateFlowDialog = true },
		onSetFlowEnabled = onSetFlowEnabled,
		onStartFlow = onStartFlow,
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
private fun AurralHubFlowsSection(
	status: AurralServiceStatus,
	flowActionState: UiState<AurralFlowActionResult?>,
	activeFlowActionId: String?,
	stationPlaylists: List<DomainPlaylist>,
	onCreateFlowClick: () -> Unit,
	onSetFlowEnabled: (String, Boolean) -> Unit,
	onStartFlow: (String, Int) -> Unit,
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
				AurralHubFlowRow(
					flow = flow,
					station = matchingStation,
					playableStation = aurralPlayableStationForFlow(flow, stationPlaylists),
					actionInProgress = actionInProgress,
					active = activeFlowActionId == flow.id,
					onSetFlowEnabled = onSetFlowEnabled,
					onStartFlow = onStartFlow,
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
	actionInProgress: Boolean,
	active: Boolean,
	onSetFlowEnabled: (String, Boolean) -> Unit,
	onStartFlow: (String, Int) -> Unit,
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
private fun AurralCreateFlowDialog(
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
