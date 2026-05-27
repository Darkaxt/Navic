package paige.navic.ui.screens.lidaClips

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_lida_clips_android_only
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.ui.components.common.ContentUnavailable

@Composable
actual fun PlatformLidaClipPlayer(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	pictureInPictureEnabled: Boolean,
	landscapeVideoModeEnabled: Boolean,
	videoFitMode: LidaClipsVideoFitMode,
	respectAudioFocus: Boolean,
	startPositionMs: Long,
	retryKey: Int,
	onPlaybackReady: () -> Unit,
	onPlaybackError: (String) -> Unit,
	onPlaybackPositionChange: (Long) -> Unit,
	modifier: Modifier
) {
	ContentUnavailable(
		modifier = modifier,
		icon = Icons.Filled.Play,
		label = stringResource(Res.string.info_lida_clips_android_only)
	)
}
