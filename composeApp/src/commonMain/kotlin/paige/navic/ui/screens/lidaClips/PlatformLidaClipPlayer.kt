package paige.navic.ui.screens.lidaClips

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import paige.navic.domain.models.DomainLidaClip

@Composable
expect fun PlatformLidaClipPlayer(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	modifier: Modifier
)
