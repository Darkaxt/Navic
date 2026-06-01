package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_clear_lastfm_api_key
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_test_connection
import navic.composeapp.generated.resources.info_lastfm_configured
import navic.composeapp.generated.resources.info_lastfm_connected
import navic.composeapp.generated.resources.info_lastfm_disabled
import navic.composeapp.generated.resources.info_lastfm_enabled
import navic.composeapp.generated.resources.info_lastfm_failed
import navic.composeapp.generated.resources.info_lastfm_invalid_api_key
import navic.composeapp.generated.resources.info_lastfm_missing_api_key
import navic.composeapp.generated.resources.info_lastfm_not_configured
import navic.composeapp.generated.resources.info_lastfm_not_tested
import navic.composeapp.generated.resources.info_lastfm_sample_artists
import navic.composeapp.generated.resources.info_lastfm_service_status_failed
import navic.composeapp.generated.resources.info_lastfm_service_status_loading
import navic.composeapp.generated.resources.info_lastfm_service_status_unavailable
import navic.composeapp.generated.resources.info_lastfm_testing
import navic.composeapp.generated.resources.info_lastfm_unsupported
import navic.composeapp.generated.resources.option_lastfm_api_key
import navic.composeapp.generated.resources.option_lastfm_artist_top_tracks
import navic.composeapp.generated.resources.option_lastfm_account_features
import navic.composeapp.generated.resources.option_lastfm_enabled
import navic.composeapp.generated.resources.option_lastfm_integration
import navic.composeapp.generated.resources.option_lastfm_validation_sample
import navic.composeapp.generated.resources.subtitle_lastfm_api_key
import navic.composeapp.generated.resources.subtitle_lastfm_enabled
import navic.composeapp.generated.resources.title_lastfm
import navic.composeapp.generated.resources.title_lastfm_service_status
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.LastFmServiceStatus
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.viewmodels.SettingsLastFmViewModel

@Composable
fun SettingsLastFmScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<SettingsLastFmViewModel>()
	val connectionResult by viewModel.connectionResult.collectAsStateWithLifecycle()
	val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
	val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
	val apiKeyConfigured = preferenceManager.lastFmApiKey.isNotBlank()
	val lastFmEnabled = preferenceManager.lastFmEnabled
	val lastFmIntegrationIndicators = integrationLoadingIndicators(
		lastFmLoading = isTestingConnection || serviceStatus is UiState.Loading
	)

	LaunchedEffect(preferenceManager.lastFmEnabled, preferenceManager.lastFmApiKey) {
		viewModel.clearConnectionResult()
		if (lastFmEnabled) {
			delay(500L)
			viewModel.refreshServiceStatus()
		} else {
			viewModel.clearServiceStatus()
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_lastfm)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		Box(Modifier.fillMaxSize()) {
			CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
				Column(
					Modifier
						.padding(innerPadding)
						.verticalScroll(rememberScrollState())
						.padding(top = 16.dp, end = 16.dp, start = 16.dp, bottom = 32.dp)
				) {
				FormTitle(stringResource(Res.string.title_lastfm))
				Form(Modifier.fillMaxWidth()) {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lastfm_enabled)) },
						subtitle = { Text(stringResource(Res.string.subtitle_lastfm_enabled)) },
						value = preferenceManager.lastFmEnabled,
						onSetValue = {
							preferenceManager.lastFmEnabled = it
							viewModel.clearConnectionResult()
							viewModel.clearServiceStatus()
						}
					)
					AnimatedVisibility(
						visible = preferenceManager.lastFmEnabled,
						modifier = Modifier.fillMaxWidth()
					) {
						Column(Modifier.fillMaxWidth()) {
							FormRow {
								Column(Modifier.weight(1f)) {
									Text(stringResource(Res.string.option_lastfm_api_key))
									Text(
										stringResource(Res.string.subtitle_lastfm_api_key),
										style = MaterialTheme.typography.bodyMedium,
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
									TextField(
										value = preferenceManager.lastFmApiKey,
										onValueChange = {
											preferenceManager.lastFmApiKey = it
											viewModel.clearConnectionResult()
											viewModel.clearServiceStatus()
										},
										modifier = Modifier.padding(top = 8.dp),
										singleLine = true,
										visualTransformation = PasswordVisualTransformation(),
										keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
										colors = TextFieldDefaults.colors(
											focusedIndicatorColor = Color.Transparent,
											unfocusedIndicatorColor = Color.Transparent
										),
										shape = MaterialTheme.shapes.medium
									)
								}
							}
							FormRow {
								Text(
									text = lastFmConnectionStateText(
										lastFmConnectionState(
											enabled = preferenceManager.lastFmEnabled,
											apiKey = preferenceManager.lastFmApiKey,
											connectionResult = connectionResult,
											isTestingConnection = isTestingConnection
										)
									),
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
					}
				}
				AnimatedVisibility(
					visible = preferenceManager.lastFmEnabled,
					modifier = Modifier.fillMaxWidth()
				) {
					FormButton(
						onClick = { viewModel.testConnection() },
						enabled = !isTestingConnection && apiKeyConfigured
					) {
						Text(stringResource(Res.string.action_test_connection))
					}
				}
				if (preferenceManager.lastFmEnabled && apiKeyConfigured) {
					FormButton(
						onClick = {
							preferenceManager.lastFmApiKey = ""
							viewModel.clearConnectionResult()
							viewModel.clearServiceStatus()
						},
						color = MaterialTheme.colorScheme.errorContainer
					) {
						Text(stringResource(Res.string.action_clear_lastfm_api_key))
					}
				}
				if (preferenceManager.lastFmEnabled) {
					FormTitle(stringResource(Res.string.title_lastfm_service_status))
					Form(Modifier.fillMaxWidth()) {
						LastFmServiceStatusContent(serviceStatus)
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
			IntegrationLoadingIndicatorStrip(
				indicators = lastFmIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = lastFmIntegrationIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}
}

@Composable
private fun LastFmServiceStatusContent(state: UiState<LastFmServiceStatus?>) {
	val status = state.data
	when {
		status == null && state is UiState.Loading -> FormRow {
			Text(stringResource(Res.string.info_lastfm_service_status_loading))
		}

		status == null -> FormRow {
			Text(stringResource(Res.string.info_lastfm_service_status_unavailable))
		}

		else -> lastFmStatusRows(status).forEach { row ->
			SettingValueRow(
				title = lastFmStatusTitle(row.type),
				value = lastFmStatusValue(row.value)
			)
		}
	}

	if (state is UiState.Error) {
		FormRow {
			Text(
				text = stringResource(
					Res.string.info_lastfm_service_status_failed,
					state.error.message ?: state.error::class.simpleName ?: "Unknown error"
				),
				color = MaterialTheme.colorScheme.error
			)
		}
	}
}

@Composable
private fun SettingValueRow(
	title: String,
	value: String
) {
	FormRow {
		Column(Modifier.weight(1f)) {
			Text(
				text = title,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		Text(
			text = value,
			modifier = Modifier.padding(start = 16.dp),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}

@Composable
private fun lastFmConnectionStateText(state: LastFmConnectionState): String =
	when (state) {
		LastFmConnectionState.Disabled -> stringResource(Res.string.info_lastfm_disabled)
		LastFmConnectionState.MissingApiKey -> stringResource(Res.string.info_lastfm_missing_api_key)
		LastFmConnectionState.NotTested -> stringResource(Res.string.info_lastfm_not_tested)
		LastFmConnectionState.Testing -> stringResource(Res.string.info_lastfm_testing)
		LastFmConnectionState.InvalidApiKey -> stringResource(Res.string.info_lastfm_invalid_api_key)
		is LastFmConnectionState.Connected -> stringResource(Res.string.info_lastfm_connected)
		is LastFmConnectionState.Failed -> stringResource(Res.string.info_lastfm_failed, state.message)
	}

@Composable
private fun lastFmStatusTitle(type: LastFmStatusType): String =
	when (type) {
		LastFmStatusType.ApiKey -> stringResource(Res.string.option_lastfm_api_key)
		LastFmStatusType.Integration -> stringResource(Res.string.option_lastfm_integration)
		LastFmStatusType.ArtistTopTracks -> stringResource(Res.string.option_lastfm_artist_top_tracks)
		LastFmStatusType.AccountFeatures -> stringResource(Res.string.option_lastfm_account_features)
		LastFmStatusType.ValidationSample -> stringResource(Res.string.option_lastfm_validation_sample)
	}

@Composable
private fun lastFmStatusValue(value: LastFmStatusValue): String =
	when (value) {
		LastFmStatusValue.Configured -> stringResource(Res.string.info_lastfm_configured)
		LastFmStatusValue.NotConfigured -> stringResource(Res.string.info_lastfm_not_configured)
		LastFmStatusValue.Enabled -> stringResource(Res.string.info_lastfm_enabled)
		LastFmStatusValue.Disabled -> stringResource(Res.string.info_lastfm_disabled)
		LastFmStatusValue.Unsupported -> stringResource(Res.string.info_lastfm_unsupported)
		is LastFmStatusValue.Count -> stringResource(Res.string.info_lastfm_sample_artists, value.value)
	}
