package paige.navic.ui.screens.bindery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackPosition

@Composable
actual fun BinderyAudiobookRuntimeHost(
	playbackPlan: ReadaloudPlaybackPlan?,
	bookId: String,
	bookTitle: String,
	versionRowId: String,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onPlaybackPosition: (ReadaloudPlaybackPosition) -> Unit,
	onError: (String) -> Unit
) {
	LaunchedEffect(playbackPlan) {
		onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
	}
}
