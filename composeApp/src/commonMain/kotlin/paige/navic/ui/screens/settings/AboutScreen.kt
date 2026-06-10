package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_app_version
import navic.composeapp.generated.resources.notice_copied
import navic.composeapp.generated.resources.title_about
import navic.composeapp.generated.resources.title_acknowledgements
import navic.composeapp.generated.resources.title_source
import org.jetbrains.compose.resources.stringResource
import paige.navic.LocalPlatformContext
import paige.navic.LocalNavStack
import paige.navic.LocalSnackbarState
import paige.navic.LocalUpdateCheckRequester
import paige.navic.ui.navigation.Screen
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ChevronForward
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.layouts.NestedTopBar

const val ABOUT_SOURCE_URL = "https://github.com/Darkaxt/Navic"

@Composable
fun SettingsAboutScreen() {
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val uriHandler = LocalUriHandler.current
	val backStack = LocalNavStack.current
	val snackbarState = LocalSnackbarState.current
	val coroutineScope = rememberCoroutineScope()
	val requestUpdateCheck = LocalUpdateCheckRequester.current
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val copiedMessage = stringResource(Res.string.notice_copied)
	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_about)) },
				hideBack = hideBack
			)
		}
	) { innerPadding ->
		Column(
			Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 12.dp, end = 12.dp, start = 12.dp)
		) {
			Form {
				val text = buildString {
					append(platformContext.name + "\n")
					append(stringResource(Res.string.info_app_version, platformContext.appVersion))
				}
				FormRow(
					onClick = requestUpdateCheck,
					onLongClick = {
						clipboard.setText(AnnotatedString(text))
						coroutineScope.launch {
							snackbarState.showSnackbar(copiedMessage)
						}
					}
				) {
					Text(text)
				}
			}
			Form {
				FormRow(onClick = {
					uriHandler.openUri(ABOUT_SOURCE_URL)
				}) {
					Text(stringResource(Res.string.title_source))
					Icon(Icons.Outlined.ChevronForward, null)
				}
				FormRow(onClick = dropUnlessResumed {
					backStack.add(Screen.Settings.Acknowledgements)
				}) {
					Text(stringResource(Res.string.title_acknowledgements))
					Icon(Icons.Outlined.ChevronForward, null)
				}
			}
		}
	}
}
