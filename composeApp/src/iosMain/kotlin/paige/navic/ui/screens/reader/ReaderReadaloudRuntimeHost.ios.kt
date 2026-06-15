package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.ui.navigation.Screen

@Composable
actual fun ReaderReadaloudRuntimeHost(
	reader: Screen.Reader,
	readaloudSyncEnabled: Boolean,
	readerHostEvent: ReaderEngineHostEvent?,
	readerHostEventKey: Long,
	onPublicationReady: (String) -> Unit,
	onEngineCommand: (ReaderEngineCommand, Long) -> Unit,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit
) = Unit
