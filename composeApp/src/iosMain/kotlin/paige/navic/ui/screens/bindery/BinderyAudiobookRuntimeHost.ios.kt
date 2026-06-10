package paige.navic.ui.screens.bindery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReadaloudPlaybackPlan

@Composable
actual fun BinderyAudiobookRuntimeHost(
	playbackPlan: ReadaloudPlaybackPlan?,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit
) {
	LaunchedEffect(playbackPlan) {
		onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
	}
}
