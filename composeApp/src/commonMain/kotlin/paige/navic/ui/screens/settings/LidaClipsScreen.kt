package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
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
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_test_connection
import navic.composeapp.generated.resources.info_lida_clips_connected
import navic.composeapp.generated.resources.info_lida_clips_failed
import navic.composeapp.generated.resources.info_lida_clips_health_all_ok
import navic.composeapp.generated.resources.info_lida_clips_health_check_failed
import navic.composeapp.generated.resources.info_lida_clips_health_check_failed_with_detail
import navic.composeapp.generated.resources.info_lida_clips_health_status
import navic.composeapp.generated.resources.info_lida_clips_invalid_url
import navic.composeapp.generated.resources.info_lida_clips_lidarr_track_id
import navic.composeapp.generated.resources.info_lida_clips_more_health_failures
import navic.composeapp.generated.resources.info_lida_clips_more_recent_clips
import navic.composeapp.generated.resources.info_lida_clips_missing_url
import navic.composeapp.generated.resources.info_lida_clips_more_recent_failures
import navic.composeapp.generated.resources.info_lida_clips_no_recent_clips
import navic.composeapp.generated.resources.info_lida_clips_no_recent_failures
import navic.composeapp.generated.resources.info_lida_clips_not_tested
import navic.composeapp.generated.resources.info_lida_clips_recent_failure_reason
import navic.composeapp.generated.resources.info_lida_clips_recent_failure_retry_after
import navic.composeapp.generated.resources.info_lida_clips_recent_failure_updated
import navic.composeapp.generated.resources.info_lida_clips_service_status_failed
import navic.composeapp.generated.resources.info_lida_clips_service_status_loading
import navic.composeapp.generated.resources.info_lida_clips_service_status_unavailable
import navic.composeapp.generated.resources.info_lida_clips_sync_idle
import navic.composeapp.generated.resources.info_lida_clips_sync_running
import navic.composeapp.generated.resources.info_lida_clips_testing
import navic.composeapp.generated.resources.info_lida_clips_unauthorized
import navic.composeapp.generated.resources.info_lida_clips_unknown_track
import navic.composeapp.generated.resources.option_lida_clips_api_key
import navic.composeapp.generated.resources.option_lida_clips_base_url
import navic.composeapp.generated.resources.option_lida_clips_active_clips
import navic.composeapp.generated.resources.option_lida_clips_enabled
import navic.composeapp.generated.resources.option_lida_clips_fallback_clips
import navic.composeapp.generated.resources.option_lida_clips_keep_screen_on
import navic.composeapp.generated.resources.option_lida_clips_landscape_video_mode
import navic.composeapp.generated.resources.option_lida_clips_official_clips
import navic.composeapp.generated.resources.option_lida_clips_pause_music_playback
import navic.composeapp.generated.resources.option_lida_clips_picture_in_picture
import navic.composeapp.generated.resources.option_lida_clips_remember_playback_position
import navic.composeapp.generated.resources.option_lida_clips_sync_paused
import navic.composeapp.generated.resources.option_lida_clips_sync_state
import navic.composeapp.generated.resources.option_lida_clips_video_fit
import navic.composeapp.generated.resources.subtitle_lida_clips_enabled
import navic.composeapp.generated.resources.subtitle_lida_clips_keep_screen_on
import navic.composeapp.generated.resources.subtitle_lida_clips_landscape_video_mode
import navic.composeapp.generated.resources.subtitle_lida_clips_pause_music_playback
import navic.composeapp.generated.resources.subtitle_lida_clips_picture_in_picture
import navic.composeapp.generated.resources.subtitle_lida_clips_remember_playback_position
import navic.composeapp.generated.resources.subtitle_lida_clips_sync_paused
import navic.composeapp.generated.resources.subtitle_lida_clips_video_fit
import navic.composeapp.generated.resources.title_lida_clips
import navic.composeapp.generated.resources.title_lida_clips_health_checks
import navic.composeapp.generated.resources.title_lida_clips_recent_clips
import navic.composeapp.generated.resources.title_lida_clips_recent_failures
import navic.composeapp.generated.resources.title_lida_clips_service_status
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.nextLidaClipsServiceStatusRefreshKey
import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import paige.navic.domain.repositories.LidaClipsConnectionResult
import paige.navic.domain.repositories.LidaClipsHealthCheck
import paige.navic.domain.repositories.LidaClipsRecentFailure
import paige.navic.domain.repositories.LidaClipsServiceStatus
import paige.navic.domain.repositories.configuredLidaClipsBaseUrl
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.components.SettingValueRow
import paige.navic.ui.screens.settings.viewmodels.SettingsLidaClipsViewModel

private const val RECENT_FAILURE_DISPLAY_LIMIT = 3
private const val RECENT_CLIP_DISPLAY_LIMIT = 3
private const val HEALTH_FAILURE_DISPLAY_LIMIT = 4

@Composable
fun SettingsLidaClipsScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<SettingsLidaClipsViewModel>()
	val connectionResult by viewModel.connectionResult.collectAsStateWithLifecycle()
	val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
	val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
	val isUpdatingSyncPaused by viewModel.isUpdatingSyncPaused.collectAsStateWithLifecycle()
	val isLidaClipsUrlConfigured =
		configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl) != null
	val serviceStatusRefreshKey = nextLidaClipsServiceStatusRefreshKey(
		enabled = preferenceManager.lidaClipsEnabled,
		baseUrl = preferenceManager.lidaClipsBaseUrl,
		apiKey = preferenceManager.lidaClipsApiKey
	)

	LaunchedEffect(serviceStatusRefreshKey) {
		if (serviceStatusRefreshKey == null) {
			viewModel.clearServiceStatus()
		} else {
			delay(500L)
			viewModel.refreshServiceStatus()
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_lida_clips)) },
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
				FormTitle(stringResource(Res.string.title_lida_clips))
				Form(Modifier.fillMaxWidth()) {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_lida_clips_enabled)) },
						subtitle = { Text(stringResource(Res.string.subtitle_lida_clips_enabled)) },
						value = preferenceManager.lidaClipsEnabled,
						onSetValue = {
							preferenceManager.lidaClipsEnabled = it
							viewModel.clearConnectionResult()
							viewModel.clearServiceStatus()
						}
					)
					AnimatedVisibility(
						visible = preferenceManager.lidaClipsEnabled,
						modifier = Modifier.fillMaxWidth()
					) {
						Column(Modifier.fillMaxWidth()) {
							LidaClipsField(
								value = preferenceManager.lidaClipsBaseUrl,
								onValueChange = {
									preferenceManager.lidaClipsBaseUrl = it
									viewModel.clearConnectionResult()
									viewModel.clearServiceStatus()
								},
								placeholder = stringResource(Res.string.option_lida_clips_base_url),
								keyboardType = KeyboardType.Uri
							)
							LidaClipsField(
								value = preferenceManager.lidaClipsApiKey,
								onValueChange = {
									preferenceManager.lidaClipsApiKey = it
									viewModel.clearConnectionResult()
									viewModel.clearServiceStatus()
								},
								placeholder = stringResource(Res.string.option_lida_clips_api_key),
								keyboardType = KeyboardType.Password,
								isPassword = true
							)
							if (platformContext.name.lowercase().startsWith("android")) {
								SettingSwitchRow(
									title = { Text(stringResource(Res.string.option_lida_clips_picture_in_picture)) },
									subtitle = { Text(stringResource(Res.string.subtitle_lida_clips_picture_in_picture)) },
									value = preferenceManager.lidaClipsPictureInPicture,
									onSetValue = { preferenceManager.lidaClipsPictureInPicture = it }
								)
								SettingSwitchRow(
									title = { Text(stringResource(Res.string.option_lida_clips_landscape_video_mode)) },
									subtitle = { Text(stringResource(Res.string.subtitle_lida_clips_landscape_video_mode)) },
									value = preferenceManager.lidaClipsLandscapeVideoMode,
									onSetValue = { preferenceManager.lidaClipsLandscapeVideoMode = it }
								)
								SettingSelectionRow(
									title = { Text(stringResource(Res.string.option_lida_clips_video_fit)) },
									items = LidaClipsVideoFitMode.entries.toImmutableList(),
									label = { stringResource(it.displayName) },
									description = stringResource(Res.string.subtitle_lida_clips_video_fit),
									selection = preferenceManager.lidaClipsVideoFitMode,
									onSelect = { preferenceManager.lidaClipsVideoFitMode = it }
								)
								SettingSwitchRow(
									title = { Text(stringResource(Res.string.option_lida_clips_pause_music_playback)) },
									subtitle = { Text(stringResource(Res.string.subtitle_lida_clips_pause_music_playback)) },
									value = preferenceManager.lidaClipsPauseMusicPlayback,
									onSetValue = { preferenceManager.lidaClipsPauseMusicPlayback = it }
								)
								SettingSwitchRow(
									title = { Text(stringResource(Res.string.option_lida_clips_remember_playback_position)) },
									subtitle = { Text(stringResource(Res.string.subtitle_lida_clips_remember_playback_position)) },
									value = preferenceManager.lidaClipsRememberPlaybackPosition,
									onSetValue = { preferenceManager.lidaClipsRememberPlaybackPosition = it }
								)
								SettingSwitchRow(
									title = { Text(stringResource(Res.string.option_lida_clips_keep_screen_on)) },
									subtitle = { Text(stringResource(Res.string.subtitle_lida_clips_keep_screen_on)) },
									value = preferenceManager.lidaClipsKeepScreenOn,
									onSetValue = { preferenceManager.lidaClipsKeepScreenOn = it }
								)
							}
							FormRow {
								Column(Modifier.weight(1f)) {
									Text(connectionStatusText(
										baseUrl = preferenceManager.lidaClipsBaseUrl,
										connectionResult = connectionResult,
										isTestingConnection = isTestingConnection
									))
								}
							}
						}
					}
				}
				AnimatedVisibility(
					visible = preferenceManager.lidaClipsEnabled,
					modifier = Modifier.fillMaxWidth()
				) {
					FormButton(
						onClick = { viewModel.testConnection() },
						enabled = !isTestingConnection &&
							configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl) != null
					) {
						Text(stringResource(Res.string.action_test_connection))
					}
				}
				AnimatedVisibility(
					visible = preferenceManager.lidaClipsEnabled && isLidaClipsUrlConfigured,
					modifier = Modifier.fillMaxWidth()
				) {
					Column(Modifier.fillMaxWidth()) {
						FormTitle(stringResource(Res.string.title_lida_clips_service_status))
						Form(Modifier.fillMaxWidth()) {
							LidaClipsServiceStatusContent(
								state = serviceStatus,
								isUpdatingSyncPaused = isUpdatingSyncPaused,
								onSetSyncPaused = viewModel::setSyncPaused
							)
						}
						FormButton(
							onClick = { viewModel.refreshServiceStatus() },
							enabled = serviceStatus !is UiState.Loading && !isUpdatingSyncPaused
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
private fun connectionStatusText(
	baseUrl: String,
	connectionResult: LidaClipsConnectionResult?,
	isTestingConnection: Boolean
): String {
	if (isTestingConnection) return stringResource(Res.string.info_lida_clips_testing)
	if (baseUrl.isBlank()) return stringResource(Res.string.info_lida_clips_missing_url)
	if (configuredLidaClipsBaseUrl(baseUrl) == null) {
		return stringResource(Res.string.info_lida_clips_invalid_url)
	}

	return when (connectionResult) {
		null -> stringResource(Res.string.info_lida_clips_not_tested)
		LidaClipsConnectionResult.Connected ->
			stringResource(Res.string.info_lida_clips_connected)

		LidaClipsConnectionResult.Unauthorized ->
			stringResource(Res.string.info_lida_clips_unauthorized)

		is LidaClipsConnectionResult.Failed ->
			stringResource(Res.string.info_lida_clips_failed, connectionResult.message)
	}
}

@Composable
private fun LidaClipsServiceStatusContent(
	state: UiState<LidaClipsServiceStatus?>,
	isUpdatingSyncPaused: Boolean,
	onSetSyncPaused: (Boolean) -> Unit
) {
	val status = state.data
	if (status == null) {
		FormRow {
			Text(
				when (state) {
					is UiState.Error ->
						stringResource(
							Res.string.info_lida_clips_service_status_failed,
							state.error.message ?: state.error::class.simpleName ?: "Unknown error"
						)

					is UiState.Loading ->
						stringResource(Res.string.info_lida_clips_service_status_loading)

					is UiState.Success ->
						stringResource(Res.string.info_lida_clips_service_status_unavailable)
				}
			)
		}
		return
	}

	SettingValueRow(
		title = { Text(stringResource(Res.string.option_lida_clips_active_clips)) },
		value = status.activeClips.toString()
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_lida_clips_official_clips)) },
		value = status.officialClips.toString()
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_lida_clips_fallback_clips)) },
		value = status.fallbackClips.toString()
	)
	SettingValueRow(
		title = { Text(stringResource(Res.string.option_lida_clips_sync_state)) },
		value = if (status.syncRunning) {
			stringResource(Res.string.info_lida_clips_sync_running)
		} else {
			stringResource(Res.string.info_lida_clips_sync_idle)
		}
	)
	if (state is UiState.Error) {
		FormRow {
			Text(
				stringResource(
					Res.string.info_lida_clips_service_status_failed,
					state.error.message ?: state.error::class.simpleName ?: "Unknown error"
				)
			)
		}
	}
	if (state is UiState.Loading || isUpdatingSyncPaused) {
		FormRow {
			Text(stringResource(Res.string.info_lida_clips_service_status_loading))
		}
	}
	LidaClipsRecentClips(status.recentClips)
	LidaClipsHealthChecks(
		status = status.health.status,
		checks = status.health.checks
	)
	LidaClipsRecentFailures(status.recentFailures)
	SettingSwitchRow(
		title = { Text(stringResource(Res.string.option_lida_clips_sync_paused)) },
		subtitle = { Text(stringResource(Res.string.subtitle_lida_clips_sync_paused)) },
		value = status.syncPaused,
		onSetValue = {
			if (!isUpdatingSyncPaused) onSetSyncPaused(it)
		}
	)
}

@Composable
private fun LidaClipsRecentClips(clips: List<DomainLidaClip>) {
	FormRow {
		Column(Modifier.weight(1f)) {
			Text(
				text = stringResource(Res.string.title_lida_clips_recent_clips),
				style = MaterialTheme.typography.titleSmall
			)
			if (clips.isEmpty()) {
				Text(
					text = stringResource(Res.string.info_lida_clips_no_recent_clips),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			} else {
				clips.take(RECENT_CLIP_DISPLAY_LIMIT).forEach { clip ->
					LidaClipsRecentClipItem(clip)
				}
				val hiddenClipCount = clips.size - RECENT_CLIP_DISPLAY_LIMIT
				if (hiddenClipCount > 0) {
					Text(
						text = stringResource(
							Res.string.info_lida_clips_more_recent_clips,
							hiddenClipCount
						),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	}
}

@Composable
private fun LidaClipsRecentClipItem(clip: DomainLidaClip) {
	Text(
		text = lidaClipsRecentClipTitle(clip),
		style = MaterialTheme.typography.bodyMedium
	)
	lidaClipsRecentClipSubtitle(clip)?.let { subtitle ->
		Text(
			text = subtitle,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

@Composable
private fun LidaClipsHealthChecks(
	status: String,
	checks: List<LidaClipsHealthCheck>
) {
	val failedChecks = checks.filter { !it.ok && !it.skipped }

	FormRow {
		Column(Modifier.weight(1f)) {
			Text(
				text = stringResource(Res.string.title_lida_clips_health_checks),
				style = MaterialTheme.typography.titleSmall
			)
			Text(
				text = stringResource(
					Res.string.info_lida_clips_health_status,
					status.cleanLidaClipsDisplayText() ?: "unknown"
				),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			if (failedChecks.isEmpty()) {
				Text(
					text = stringResource(Res.string.info_lida_clips_health_all_ok),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			} else {
				failedChecks.take(HEALTH_FAILURE_DISPLAY_LIMIT).forEach { check ->
					Text(
						text = lidaClipsHealthFailureText(check),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				val hiddenFailureCount = failedChecks.size - HEALTH_FAILURE_DISPLAY_LIMIT
				if (hiddenFailureCount > 0) {
					Text(
						text = stringResource(
							Res.string.info_lida_clips_more_health_failures,
							hiddenFailureCount
						),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	}
}

@Composable
private fun lidaClipsHealthFailureText(check: LidaClipsHealthCheck): String {
	val display = lidaClipsHealthFailureDisplay(check)
	return if (display.detail == null) {
		stringResource(Res.string.info_lida_clips_health_check_failed, display.name)
	} else {
		stringResource(
			Res.string.info_lida_clips_health_check_failed_with_detail,
			display.name,
			display.detail
		)
	}
}

@Composable
private fun LidaClipsRecentFailures(failures: List<LidaClipsRecentFailure>) {
	FormRow {
		Column(Modifier.weight(1f)) {
			Text(
				text = stringResource(Res.string.title_lida_clips_recent_failures),
				style = MaterialTheme.typography.titleSmall
			)
			if (failures.isEmpty()) {
				Text(
					text = stringResource(Res.string.info_lida_clips_no_recent_failures),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			} else {
				failures.take(RECENT_FAILURE_DISPLAY_LIMIT).forEach { failure ->
					LidaClipsRecentFailureItem(failure)
				}
				val hiddenFailureCount = failures.size - RECENT_FAILURE_DISPLAY_LIMIT
				if (hiddenFailureCount > 0) {
					Text(
						text = stringResource(
							Res.string.info_lida_clips_more_recent_failures,
							hiddenFailureCount
						),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	}
}

@Composable
private fun LidaClipsRecentFailureItem(failure: LidaClipsRecentFailure) {
	Text(
		text = lidaClipsRecentFailureTitle(
			failure = failure,
			unknownTrackText = stringResource(Res.string.info_lida_clips_unknown_track),
			lidarrTrackText = failure.lidarrTrackId?.let {
				stringResource(Res.string.info_lida_clips_lidarr_track_id, it)
			}
		),
		style = MaterialTheme.typography.bodyMedium
	)
	failure.reason.cleanLidaClipsDisplayText()?.let { reason ->
		Text(
			text = stringResource(Res.string.info_lida_clips_recent_failure_reason, reason),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
	failure.retryAfter.cleanLidaClipsDisplayText()?.let { retryAfter ->
		Text(
			text = stringResource(Res.string.info_lida_clips_recent_failure_retry_after, retryAfter),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
	failure.updatedAt.cleanLidaClipsDisplayText()?.let { updatedAt ->
		Text(
			text = stringResource(Res.string.info_lida_clips_recent_failure_updated, updatedAt),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

@Composable
private fun LidaClipsField(
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
