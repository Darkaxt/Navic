package paige.navic.ui.screens.lidaClips

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.settings.LidaClipsVideoFitMode

@Composable
expect fun PlatformLidaClipPlayer(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	pictureInPictureEnabled: Boolean,
	landscapeVideoModeEnabled: Boolean,
	videoFitMode: LidaClipsVideoFitMode,
	respectAudioFocus: Boolean,
	startPositionMs: Long,
	muted: Boolean = false,
	showControls: Boolean = true,
	useTextureView: Boolean = false,
	startProgress: Float? = null,
	playWhenReady: Boolean = true,
	seekProgress: Float? = null,
	seekKey: Int = 0,
	retryKey: Int,
	onPlaybackReady: () -> Unit,
	onFirstFrameRendered: () -> Unit = {},
	onPlaybackError: (String) -> Unit,
	onPlaybackPositionChange: (Long) -> Unit,
	modifier: Modifier
)
