package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_test_connection
import navic.composeapp.generated.resources.info_aurral_auth_local_or_disabled
import navic.composeapp.generated.resources.info_aurral_auth_required
import navic.composeapp.generated.resources.info_aurral_connected
import navic.composeapp.generated.resources.info_aurral_failed
import navic.composeapp.generated.resources.info_aurral_forbidden
import navic.composeapp.generated.resources.info_aurral_invalid_url
import navic.composeapp.generated.resources.info_aurral_missing_url
import navic.composeapp.generated.resources.info_aurral_acquisition_queue_empty
import navic.composeapp.generated.resources.info_aurral_not_tested
import navic.composeapp.generated.resources.info_aurral_service_status_failed
import navic.composeapp.generated.resources.info_aurral_service_status_loading
import navic.composeapp.generated.resources.info_aurral_service_status_unavailable
import navic.composeapp.generated.resources.info_aurral_testing
import navic.composeapp.generated.resources.info_aurral_unauthorized
import navic.composeapp.generated.resources.info_not_configured
import navic.composeapp.generated.resources.info_service_configured
import navic.composeapp.generated.resources.option_aurral_auth_state
import navic.composeapp.generated.resources.option_aurral_base_url
import navic.composeapp.generated.resources.option_aurral_discovery
import navic.composeapp.generated.resources.option_aurral_enabled
import navic.composeapp.generated.resources.option_aurral_flow_state
import navic.composeapp.generated.resources.option_aurral_flow_tracks
import navic.composeapp.generated.resources.option_aurral_flows
import navic.composeapp.generated.resources.option_aurral_health
import navic.composeapp.generated.resources.option_aurral_lidarr
import navic.composeapp.generated.resources.option_aurral_password
import navic.composeapp.generated.resources.option_aurral_permissions
import navic.composeapp.generated.resources.option_aurral_requests
import navic.composeapp.generated.resources.option_aurral_shared_playlists
import navic.composeapp.generated.resources.option_aurral_user
import navic.composeapp.generated.resources.option_aurral_username
import navic.composeapp.generated.resources.option_aurral_version
import navic.composeapp.generated.resources.subtitle_aurral_enabled
import navic.composeapp.generated.resources.title_aurral
import navic.composeapp.generated.resources.title_aurral_acquisition_queue
import navic.composeapp.generated.resources.title_aurral_service_status
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.repositories.AurralConnectionResult
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.components.SettingValueRow
import paige.navic.ui.screens.settings.viewmodels.SettingsAurralViewModel

@Composable
fun SettingsAurralScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<SettingsAurralViewModel>()
	val connectionResult by viewModel.connectionResult.collectAsStateWithLifecycle()
	val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
	val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
	val isAurralUrlConfigured =
		configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null

	LaunchedEffect(
		preferenceManager.aurralEnabled,
		preferenceManager.aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (preferenceManager.aurralEnabled && isAurralUrlConfigured) {
			delay(500L)
			viewModel.refreshServiceStatus()
		} else {
			viewModel.clearServiceStatus()
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_aurral)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			Column(
				Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(top = 16.dp, end = 16.dp, start = 16.dp, bottom = 32.dp)
			) {
				FormTitle(stringResource(Res.string.title_aurral))
				Form(Modifier.fillMaxWidth()) {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_aurral_enabled)) },
						subtitle = { Text(stringResource(Res.string.subtitle_aurral_enabled)) },
						value = preferenceManager.aurralEnabled,
						onSetValue = {
							preferenceManager.aurralEnabled = it
							viewModel.clearConnectionResult()
							viewModel.clearServiceStatus()
						}
					)
					AnimatedVisibility(
						visible = preferenceManager.aurralEnabled,
						modifier = Modifier.fillMaxWidth()
					) {
						Column(Modifier.fillMaxWidth()) {
							AurralField(
								value = preferenceManager.aurralBaseUrl,
								onValueChange = {
									preferenceManager.aurralBaseUrl = it
									viewModel.clearConnectionResult()
									viewModel.clearServiceStatus()
								},
								placeholder = stringResource(Res.string.option_aurral_base_url),
								keyboardType = KeyboardType.Uri
							)
							AurralField(
								value = preferenceManager.aurralUsername,
								onValueChange = {
									preferenceManager.aurralUsername = it
									viewModel.clearConnectionResult()
									viewModel.clearServiceStatus()
								},
								placeholder = stringResource(Res.string.option_aurral_username),
								keyboardType = KeyboardType.Text
							)
							AurralField(
								value = preferenceManager.aurralPassword,
								onValueChange = {
									preferenceManager.aurralPassword = it
									viewModel.clearConnectionResult()
									viewModel.clearServiceStatus()
								},
								placeholder = stringResource(Res.string.option_aurral_password),
								keyboardType = KeyboardType.Password,
								isPassword = true
							)
							FormRow {
								Column(Modifier.weight(1f)) {
									Text(
										aurralConnectionStatusText(
											baseUrl = preferenceManager.aurralBaseUrl,
											connectionResult = connectionResult,
											isTestingConnection = isTestingConnection
										)
									)
								}
							}
						}
					}
				}
				AnimatedVisibility(
					visible = preferenceManager.aurralEnabled,
					modifier = Modifier.fillMaxWidth()
				) {
					FormButton(
						onClick = { viewModel.testConnection() },
						enabled = !isTestingConnection && isAurralUrlConfigured
					) {
						Text(stringResource(Res.string.action_test_connection))
					}
				}
				AnimatedVisibility(
					visible = preferenceManager.aurralEnabled && isAurralUrlConfigured,
					modifier = Modifier.fillMaxWidth()
				) {
					Column(Modifier.fillMaxWidth()) {
						FormTitle(stringResource(Res.string.title_aurral_service_status))
						Form(Modifier.fillMaxWidth()) {
							AurralServiceStatusContent(serviceStatus)
						}
						FormTitle(stringResource(Res.string.title_aurral_acquisition_queue))
						Form(Modifier.fillMaxWidth()) {
							AurralAcquisitionQueueContent(serviceStatus)
						}
						FormButton(
							onClick = { viewModel.refreshServiceStatus() },
							enabled = serviceStatus !is UiState.Loading
						) {
							Text(stringResource(Res.string.action_refresh))
						}
					}
				}
			}
		}
	}
}

@Composable
private fun AurralAcquisitionQueueContent(state: UiState<AurralServiceStatus?>) {
	val status = state.data
	when {
		status == null && state is UiState.Loading -> FormRow {
			Text(stringResource(Res.string.info_aurral_service_status_loading))
		}

		status == null -> FormRow {
			Text(stringResource(Res.string.info_aurral_service_status_unavailable))
		}

		status.acquisitionQueue.isEmpty() -> FormRow {
			Text(stringResource(Res.string.info_aurral_acquisition_queue_empty))
		}

		else -> status.acquisitionQueue.take(8).forEach { item ->
			AurralAcquisitionQueueRow(item)
		}
	}
}

@Composable
private fun AurralAcquisitionQueueRow(item: AurralAcquisitionQueueItem) {
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

@Composable
private fun aurralConnectionStatusText(
	baseUrl: String,
	connectionResult: AurralConnectionResult?,
	isTestingConnection: Boolean
): String =
	when (
		val display = aurralConnectionStatusDisplay(
			baseUrl = baseUrl,
			connectionResult = connectionResult,
			isTestingConnection = isTestingConnection
		)
	) {
		AurralConnectionStatusDisplay.MissingUrl ->
			stringResource(Res.string.info_aurral_missing_url)

		AurralConnectionStatusDisplay.InvalidUrl ->
			stringResource(Res.string.info_aurral_invalid_url)

		AurralConnectionStatusDisplay.Testing ->
			stringResource(Res.string.info_aurral_testing)

		AurralConnectionStatusDisplay.NotTested ->
			stringResource(Res.string.info_aurral_not_tested)

		AurralConnectionStatusDisplay.Connected ->
			stringResource(Res.string.info_aurral_connected)

		AurralConnectionStatusDisplay.Unauthorized ->
			stringResource(Res.string.info_aurral_unauthorized)

		AurralConnectionStatusDisplay.Forbidden ->
			stringResource(Res.string.info_aurral_forbidden)

		is AurralConnectionStatusDisplay.Failed ->
			stringResource(Res.string.info_aurral_failed, display.message)
	}

@Composable
private fun AurralServiceStatusContent(state: UiState<AurralServiceStatus?>) {
	val status = state.data
	if (status == null) {
		FormRow {
			Text(
				when (state) {
					is UiState.Error ->
						stringResource(
							Res.string.info_aurral_service_status_failed,
							state.error.message ?: state.error::class.simpleName ?: "Unknown error"
						)

					is UiState.Loading ->
						stringResource(Res.string.info_aurral_service_status_loading)

					is UiState.Success ->
						stringResource(Res.string.info_aurral_service_status_unavailable)
				}
			)
		}
		return
	}

	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_health)) },
		value = status.healthStatus
	)
	status.appVersion?.takeIf { it.isNotBlank() }?.let { version ->
		SettingValueRow(
			title = { Text(stringResource(Res.string.option_aurral_version)) },
			value = version
		)
	}
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_auth_state)) },
		value = if (status.authRequired) {
			stringResource(Res.string.info_aurral_auth_required)
		} else {
			stringResource(Res.string.info_aurral_auth_local_or_disabled)
		}
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_user)) },
		value = listOfNotNull(status.username, status.role?.let { "($it)" })
			.joinToString(" ")
			.ifBlank { aurralPermissionSummary(status) }
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_permissions)) },
		value = aurralPermissionSummary(status)
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_lidarr)) },
		value = if (status.lidarrConfigured) {
			stringResource(Res.string.info_service_configured)
		} else {
			stringResource(Res.string.info_not_configured)
		}
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_discovery)) },
		value = if (status.discoveryUpdating) {
			"${status.discoveryRecommendationsCount} updating"
		} else {
			status.discoveryRecommendationsCount.toString()
		}
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_flows)) },
		value = "${status.enabledFlowsCount} / ${status.flowsCount}"
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_shared_playlists)) },
		value = status.sharedPlaylistsCount.toString()
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_requests)) },
		value = status.requestsCount.toString()
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_aurral_flow_tracks)) },
		value = aurralFlowTrackSummary(status)
	)
	status.flowMessage?.takeIf { it.isNotBlank() }?.let { message ->
		SettingValueRow(
			title = { Text(stringResource(Res.string.option_aurral_flow_state)) },
			subtitle = { status.flowPhase?.takeIf { it.isNotBlank() }?.let { Text(it) } },
			value = message
		)
	}
	if (state is UiState.Error) {
		FormRow {
			Text(
				stringResource(
					Res.string.info_aurral_service_status_failed,
					state.error.message ?: state.error::class.simpleName ?: "Unknown error"
				)
			)
		}
	}
	if (state is UiState.Loading) {
		FormRow {
			Text(stringResource(Res.string.info_aurral_service_status_loading))
		}
	}
}

@Composable
private fun AurralField(
	value: String,
	onValueChange: (String) -> Unit,
	placeholder: String,
	keyboardType: KeyboardType,
	isPassword: Boolean = false
) {
	FormRow {
		TextField(
			value = value,
			onValueChange = onValueChange,
			placeholder = { Text(placeholder) },
			modifier = Modifier.fillMaxWidth(),
			singleLine = true,
			visualTransformation = if (isPassword) {
				PasswordVisualTransformation()
			} else {
				VisualTransformation.None
			},
			keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
			colors = TextFieldDefaults.colors(
				focusedIndicatorColor = Color.Transparent,
				unfocusedIndicatorColor = Color.Transparent
			),
			shape = MaterialTheme.shapes.medium
		)
	}
}
