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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_clear_bindery_api_key
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_test_connection
import navic.composeapp.generated.resources.info_bindery_configured
import navic.composeapp.generated.resources.info_bindery_connected
import navic.composeapp.generated.resources.info_bindery_connected_without_audiobooks
import navic.composeapp.generated.resources.info_bindery_count
import navic.composeapp.generated.resources.info_bindery_disabled
import navic.composeapp.generated.resources.info_bindery_enabled
import navic.composeapp.generated.resources.info_bindery_failed
import navic.composeapp.generated.resources.info_bindery_forbidden
import navic.composeapp.generated.resources.info_bindery_invalid_opds_url
import navic.composeapp.generated.resources.info_bindery_missing_api_key
import navic.composeapp.generated.resources.info_bindery_missing_opds_url
import navic.composeapp.generated.resources.info_bindery_not_configured
import navic.composeapp.generated.resources.info_bindery_not_tested
import navic.composeapp.generated.resources.info_bindery_service_status_failed
import navic.composeapp.generated.resources.info_bindery_service_status_loading
import navic.composeapp.generated.resources.info_bindery_service_status_unavailable
import navic.composeapp.generated.resources.info_bindery_testing
import navic.composeapp.generated.resources.info_bindery_unauthorized
import navic.composeapp.generated.resources.info_bindery_unsupported
import navic.composeapp.generated.resources.option_bindery_api_key
import navic.composeapp.generated.resources.option_bindery_audiobooks
import navic.composeapp.generated.resources.option_bindery_authors
import navic.composeapp.generated.resources.option_bindery_book_grid_columns
import navic.composeapp.generated.resources.option_bindery_collections
import navic.composeapp.generated.resources.option_bindery_enabled
import navic.composeapp.generated.resources.option_bindery_findings
import navic.composeapp.generated.resources.option_bindery_language_filter
import navic.composeapp.generated.resources.option_bindery_navigation
import navic.composeapp.generated.resources.option_bindery_opds_url
import navic.composeapp.generated.resources.option_bindery_pagination
import navic.composeapp.generated.resources.option_bindery_progress_sync
import navic.composeapp.generated.resources.option_bindery_search
import navic.composeapp.generated.resources.option_bindery_series
import navic.composeapp.generated.resources.subtitle_bindery_api_key
import navic.composeapp.generated.resources.subtitle_bindery_book_grid_columns
import navic.composeapp.generated.resources.subtitle_bindery_enabled
import navic.composeapp.generated.resources.subtitle_bindery_language_filter
import navic.composeapp.generated.resources.subtitle_bindery_opds_url
import navic.composeapp.generated.resources.title_bindery
import navic.composeapp.generated.resources.title_bindery_service_status
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.BinderyMaxBookGridColumns
import paige.navic.domain.models.BinderyMinBookGridColumns
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.repositories.BinderyServiceStatus
import paige.navic.domain.repositories.configuredBinderyOpdsBaseUrl
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.common.BinderyIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.components.SettingValueRow
import paige.navic.ui.screens.settings.viewmodels.SettingsBinderyViewModel

@Composable
fun SettingsBinderyScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<SettingsBinderyViewModel>()
	val connectionResult by viewModel.connectionResult.collectAsStateWithLifecycle()
	val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
	val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
	val isOpdsUrlConfigured =
		configuredBinderyOpdsBaseUrl(preferenceManager.binderyOpdsBaseUrl) != null
	val isApiKeyConfigured = preferenceManager.binderyApiKey.isNotBlank()
	val binderyIntegrationIndicators = integrationLoadingIndicators(
		binderyLoading = isTestingConnection || serviceStatus is UiState.Loading
	)

	LaunchedEffect(
		preferenceManager.binderyEnabled,
		preferenceManager.binderyOpdsBaseUrl,
		preferenceManager.binderyApiKey
	) {
		viewModel.clearConnectionResult()
		if (preferenceManager.binderyEnabled && isOpdsUrlConfigured && isApiKeyConfigured) {
			delay(500L)
			viewModel.refreshServiceStatus()
		} else {
			viewModel.clearServiceStatus()
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_bindery)) },
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
				FormTitle(stringResource(Res.string.title_bindery))
				Form(Modifier.fillMaxWidth()) {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_bindery_enabled)) },
						subtitle = { Text(stringResource(Res.string.subtitle_bindery_enabled)) },
						value = preferenceManager.binderyEnabled,
						onSetValue = {
							preferenceManager.binderyEnabled = it
							viewModel.clearConnectionResult()
							viewModel.clearServiceStatus()
						}
					)
					AnimatedVisibility(
						visible = preferenceManager.binderyEnabled,
						modifier = Modifier.fillMaxWidth()
					) {
						Column(Modifier.fillMaxWidth()) {
							BinderyField(
								title = stringResource(Res.string.option_bindery_opds_url),
								subtitle = stringResource(Res.string.subtitle_bindery_opds_url),
								value = preferenceManager.binderyOpdsBaseUrl,
								onValueChange = {
									preferenceManager.binderyOpdsBaseUrl = it
									viewModel.clearConnectionResult()
									viewModel.clearServiceStatus()
								},
								keyboardType = KeyboardType.Uri
							)
							BinderyField(
								title = stringResource(Res.string.option_bindery_api_key),
								subtitle = stringResource(Res.string.subtitle_bindery_api_key),
								value = preferenceManager.binderyApiKey,
								onValueChange = {
									preferenceManager.binderyApiKey = it
									viewModel.clearConnectionResult()
									viewModel.clearServiceStatus()
								},
								keyboardType = KeyboardType.Password,
								isPassword = true
							)
							BinderyField(
								title = stringResource(Res.string.option_bindery_language_filter),
								subtitle = stringResource(Res.string.subtitle_bindery_language_filter),
								value = preferenceManager.binderyLanguageFilter,
								onValueChange = { preferenceManager.binderyLanguageFilter = it },
								keyboardType = KeyboardType.Text
							)
							SettingSelectionRow(
								title = { Text(stringResource(Res.string.option_bindery_book_grid_columns)) },
								items = (BinderyMinBookGridColumns..BinderyMaxBookGridColumns).toList().toImmutableList(),
								label = { columns -> columns.toString() },
								description = stringResource(Res.string.subtitle_bindery_book_grid_columns),
								selection = normalizedBinderyBookGridColumns(preferenceManager.binderyBookGridColumns),
								onSelect = { columns -> preferenceManager.binderyBookGridColumns = columns }
							)
							FormRow {
								Column(Modifier.weight(1f)) {
									Text(
										text = binderyConnectionStateText(
											binderyConnectionState(
												enabled = preferenceManager.binderyEnabled,
												opdsUrl = preferenceManager.binderyOpdsBaseUrl,
												apiKey = preferenceManager.binderyApiKey,
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
				}
				AnimatedVisibility(
					visible = preferenceManager.binderyEnabled,
					modifier = Modifier.fillMaxWidth()
				) {
					FormButton(
						onClick = { viewModel.testConnection() },
						enabled = !isTestingConnection && isOpdsUrlConfigured && isApiKeyConfigured
					) {
						Text(stringResource(Res.string.action_test_connection))
					}
				}
				if (preferenceManager.binderyEnabled && isApiKeyConfigured) {
					FormButton(
						onClick = {
							preferenceManager.binderyApiKey = ""
							viewModel.clearConnectionResult()
							viewModel.clearServiceStatus()
						},
						color = MaterialTheme.colorScheme.errorContainer
					) {
						Text(stringResource(Res.string.action_clear_bindery_api_key))
					}
				}
				if (preferenceManager.binderyEnabled) {
					FormTitle(stringResource(Res.string.title_bindery_service_status))
					Form(Modifier.fillMaxWidth()) {
						BinderyServiceStatusContent(serviceStatus)
					}
					FormButton(
						onClick = { viewModel.refreshServiceStatus() },
						enabled = serviceStatus !is UiState.Loading && isOpdsUrlConfigured && isApiKeyConfigured
					) {
						Text(stringResource(Res.string.action_refresh))
					}
				}
			}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = binderyIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = binderyIntegrationIndicators,
					relevantServices = BinderyIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}
}

@Composable
private fun BinderyField(
	title: String,
	subtitle: String,
	value: String,
	onValueChange: (String) -> Unit,
	keyboardType: KeyboardType,
	isPassword: Boolean = false
) {
	FormRow {
		Column(Modifier.weight(1f)) {
			Text(title)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			TextField(
				value = value,
				onValueChange = onValueChange,
				placeholder = { Text(title) },
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp),
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
}

@Composable
private fun BinderyServiceStatusContent(state: UiState<BinderyServiceStatus?>) {
	val status = state.data
	when {
		status == null && state is UiState.Loading -> FormRow {
			Text(stringResource(Res.string.info_bindery_service_status_loading))
		}

		status == null -> FormRow {
			Text(stringResource(Res.string.info_bindery_service_status_unavailable))
		}

		else -> binderyStatusRows(status).forEach { row ->
			SettingValueRow(
				title = { Text(binderyStatusTitle(row.type)) },
				value = binderyStatusValue(row.value)
			)
		}
	}

	if (state is UiState.Error) {
		FormRow {
			Text(
				text = stringResource(
					Res.string.info_bindery_service_status_failed,
					state.error.message ?: state.error::class.simpleName ?: "Unknown error"
				),
				color = MaterialTheme.colorScheme.error
			)
		}
	}
}

@Composable
private fun binderyConnectionStateText(state: BinderyConnectionState): String =
	when (state) {
		BinderyConnectionState.Disabled -> stringResource(Res.string.info_bindery_disabled)
		BinderyConnectionState.MissingOpdsUrl -> stringResource(Res.string.info_bindery_missing_opds_url)
		BinderyConnectionState.MissingApiKey -> stringResource(Res.string.info_bindery_missing_api_key)
		BinderyConnectionState.NotTested -> stringResource(Res.string.info_bindery_not_tested)
		BinderyConnectionState.Testing -> stringResource(Res.string.info_bindery_testing)
		BinderyConnectionState.Unauthorized -> stringResource(Res.string.info_bindery_unauthorized)
		BinderyConnectionState.Forbidden -> stringResource(Res.string.info_bindery_forbidden)
		is BinderyConnectionState.InvalidOpdsUrl ->
			stringResource(Res.string.info_bindery_invalid_opds_url, state.message)
		is BinderyConnectionState.Connected -> if (state.audiobooksAvailable) {
			stringResource(Res.string.info_bindery_connected)
		} else {
			stringResource(Res.string.info_bindery_connected_without_audiobooks)
		}
		is BinderyConnectionState.Failed -> stringResource(Res.string.info_bindery_failed, state.message)
	}

@Composable
private fun binderyStatusTitle(type: BinderyStatusType): String =
	when (type) {
		BinderyStatusType.OpdsUrl -> stringResource(Res.string.option_bindery_opds_url)
		BinderyStatusType.ApiKey -> stringResource(Res.string.option_bindery_api_key)
		BinderyStatusType.Audiobooks -> stringResource(Res.string.option_bindery_audiobooks)
		BinderyStatusType.Authors -> stringResource(Res.string.option_bindery_authors)
		BinderyStatusType.Collections -> stringResource(Res.string.option_bindery_collections)
		BinderyStatusType.Findings -> stringResource(Res.string.option_bindery_findings)
		BinderyStatusType.Series -> stringResource(Res.string.option_bindery_series)
		BinderyStatusType.Search -> stringResource(Res.string.option_bindery_search)
		BinderyStatusType.Navigation -> stringResource(Res.string.option_bindery_navigation)
		BinderyStatusType.ProgressSync -> stringResource(Res.string.option_bindery_progress_sync)
		BinderyStatusType.Pagination -> stringResource(Res.string.option_bindery_pagination)
	}

@Composable
private fun binderyStatusValue(value: BinderyStatusValue): String =
	when (value) {
		BinderyStatusValue.Configured -> stringResource(Res.string.info_bindery_configured)
		BinderyStatusValue.NotConfigured -> stringResource(Res.string.info_bindery_not_configured)
		BinderyStatusValue.Enabled -> stringResource(Res.string.info_bindery_enabled)
		BinderyStatusValue.Disabled -> stringResource(Res.string.info_bindery_disabled)
		BinderyStatusValue.Unsupported -> stringResource(Res.string.info_bindery_unsupported)
		is BinderyStatusValue.Count -> stringResource(Res.string.info_bindery_count, value.value)
	}
