package paige.navic.ui.screens.aurral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_open_aurral_settings
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.info_aurral_acquisition_queue_empty
import navic.composeapp.generated.resources.info_aurral_hub_disabled
import navic.composeapp.generated.resources.info_aurral_hub_missing_url
import navic.composeapp.generated.resources.info_aurral_service_status_failed
import navic.composeapp.generated.resources.info_aurral_service_status_loading
import navic.composeapp.generated.resources.info_aurral_service_status_unavailable
import navic.composeapp.generated.resources.title_aurral
import navic.composeapp.generated.resources.title_aurral_acquisition_queue
import navic.composeapp.generated.resources.title_aurral_discover
import navic.composeapp.generated.resources.title_aurral_flows
import navic.composeapp.generated.resources.title_aurral_requests
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Settings
import paige.navic.icons.outlined.Refresh
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
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
	val viewModel = koinViewModel<AurralHubViewModel>()
	val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
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

				else -> AurralHubContent(serviceStatus)
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
private fun AurralHubContent(state: UiState<AurralServiceStatus?>) {
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

	Form(Modifier.fillMaxWidth()) {
		aurralHubSummaryCards(status).forEach { card ->
			AurralHubSummaryRow(card)
		}
	}

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
