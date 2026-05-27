package paige.navic.ui.screens.lidaClips

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import paige.navic.domain.models.DomainLidaClip

@Composable
expect fun PlatformLidaClipPlayer(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	pictureInPictureEnabled: Boolean,
	startPositionMs: Long,
	retryKey: Int,
	onPlaybackReady: () -> Unit,
	onPlaybackError: (String) -> Unit,
	onPlaybackPositionChange: (Long) -> Unit,
	modifier: Modifier
)
