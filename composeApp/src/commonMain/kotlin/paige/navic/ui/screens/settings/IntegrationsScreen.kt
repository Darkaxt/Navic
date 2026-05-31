package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_musicbrainz_artwork_fallback
import navic.composeapp.generated.resources.option_aurral
import navic.composeapp.generated.resources.option_aurral_hub
import navic.composeapp.generated.resources.option_lida_clips
import navic.composeapp.generated.resources.subtitle_aurral
import navic.composeapp.generated.resources.subtitle_aurral_hub
import navic.composeapp.generated.resources.subtitle_musicbrainz_artwork_fallback
import navic.composeapp.generated.resources.subtitle_lida_clips
import navic.composeapp.generated.resources.title_integrations
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ChevronForward
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.settings.components.SettingSwitchRow

@Composable
fun SettingsIntegrationsScreen() {
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_integrations)) },
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
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_musicbrainz_artwork_fallback)) },
						subtitle = { Text(stringResource(Res.string.subtitle_musicbrainz_artwork_fallback)) },
						value = preferenceManager.musicBrainzArtworkFallbackEnabled,
						onSetValue = {
							preferenceManager.musicBrainzArtworkFallbackEnabled = it
							musicBrainzArtworkRepository.refreshCacheVisibility()
						}
					)
					FormRow(
						onClick = dropUnlessResumed {
							backStack.add(Screen.Settings.LidaClips)
						}
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.option_lida_clips))
							Text(
								stringResource(Res.string.subtitle_lida_clips),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Icon(Icons.Outlined.ChevronForward, null)
					}
					if (preferenceManager.aurralEnabled &&
						configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null
					) {
						FormRow(
							onClick = dropUnlessResumed {
								backStack.add(Screen.AurralHub)
							}
						) {
							Column(Modifier.weight(1f)) {
								Text(stringResource(Res.string.option_aurral_hub))
								Text(
									stringResource(Res.string.subtitle_aurral_hub),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
							Icon(Icons.Outlined.ChevronForward, null)
						}
					}
					FormRow(
						onClick = dropUnlessResumed {
							backStack.add(Screen.Settings.Aurral)
						}
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.option_aurral))
							Text(
								stringResource(Res.string.subtitle_aurral),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Icon(Icons.Outlined.ChevronForward, null)
					}
				}
			}
		}
	}
}
