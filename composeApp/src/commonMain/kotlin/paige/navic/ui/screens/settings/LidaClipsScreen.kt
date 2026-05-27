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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_test_connection
import navic.composeapp.generated.resources.info_lida_clips_connected
import navic.composeapp.generated.resources.info_lida_clips_failed
import navic.composeapp.generated.resources.info_lida_clips_not_tested
import navic.composeapp.generated.resources.info_lida_clips_testing
import navic.composeapp.generated.resources.info_lida_clips_unauthorized
import navic.composeapp.generated.resources.option_lida_clips_api_key
import navic.composeapp.generated.resources.option_lida_clips_base_url
import navic.composeapp.generated.resources.option_lida_clips_enabled
import navic.composeapp.generated.resources.option_lida_clips_picture_in_picture
import navic.composeapp.generated.resources.subtitle_lida_clips_enabled
import navic.composeapp.generated.resources.subtitle_lida_clips_picture_in_picture
import navic.composeapp.generated.resources.title_lida_clips
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.LidaClipsConnectionResult
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.viewmodels.SettingsLidaClipsViewModel

@Composable
fun SettingsLidaClipsScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<SettingsLidaClipsViewModel>()
	val connectionResult by viewModel.connectionResult.collectAsStateWithLifecycle()
	val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()

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
								},
								placeholder = stringResource(Res.string.option_lida_clips_base_url),
								keyboardType = KeyboardType.Uri
							)
							LidaClipsField(
								value = preferenceManager.lidaClipsApiKey,
								onValueChange = {
									preferenceManager.lidaClipsApiKey = it
									viewModel.clearConnectionResult()
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
							}
							FormRow {
								Column(Modifier.weight(1f)) {
									Text(connectionStatusText(connectionResult, isTestingConnection))
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
						enabled = !isTestingConnection
					) {
						Text(stringResource(Res.string.action_test_connection))
					}
				}
			}
		}
	}
}

@Composable
private fun connectionStatusText(
	connectionResult: LidaClipsConnectionResult?,
	isTestingConnection: Boolean
): String {
	if (isTestingConnection) return stringResource(Res.string.info_lida_clips_testing)

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
