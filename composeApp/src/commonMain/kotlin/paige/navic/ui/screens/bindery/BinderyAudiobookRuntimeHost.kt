package paige.navic.ui.screens.bindery

import androidx.compose.runtime.Composable
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackPosition

@Composable
expect fun BinderyAudiobookRuntimeHost(
	playbackPlan: ReadaloudPlaybackPlan?,
	bookId: String,
	bookTitle: String,
	versionRowId: String,
	coverUrl: String?,
	coverCacheKey: String,
	imageRequestHeaders: Map<String, String>,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onPlaybackPosition: (ReadaloudPlaybackPosition) -> Unit,
	onError: (String) -> Unit
)
