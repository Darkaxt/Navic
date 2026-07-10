package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import paige.navic.reader.ReaderEngineHostCommand
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderSettings

@Composable
actual fun ReaderEngineWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
	externalShellCover: Boolean,
	suppressWebShellCover: Boolean,
	nativeShellCoverTint: String?,
	settings: ReaderSettings,
	startCfi: String?,
	startHref: String?,
	startProgress: Double?,
	command: ReaderEngineHostCommand?,
	commandKey: Long,
	onEvent: (ReaderEngineHostEvent) -> Unit,
	modifier: Modifier
) {
	Box(
		modifier = modifier.fillMaxSize(),
		contentAlignment = Alignment.Center
	) {
		Text("Reader engine content is currently available on Android.")
	}
}
