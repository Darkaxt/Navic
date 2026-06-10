package paige.navic.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.ui.core.AudiobookMiniPlayerUiState

interface AudiobookPlaybackManager {
	val uiState: StateFlow<AudiobookMiniPlayerUiState>

	fun load(
		playbackPlan: ReadaloudPlaybackPlan?,
		bookId: String,
		bookTitle: String,
		versionRowId: String,
		coverUrl: String?,
		coverCacheKey: String?,
		imageRequestHeaders: Map<String, String>,
		playWhenReady: Boolean
	)

	fun dispatch(command: ReaderReadaloudPlaybackCommand)
}

class NoOpAudiobookPlaybackManager : AudiobookPlaybackManager {
	private val _uiState = MutableStateFlow(AudiobookMiniPlayerUiState())
	override val uiState: StateFlow<AudiobookMiniPlayerUiState> = _uiState.asStateFlow()

	override fun load(
		playbackPlan: ReadaloudPlaybackPlan?,
		bookId: String,
		bookTitle: String,
		versionRowId: String,
		coverUrl: String?,
		coverCacheKey: String?,
		imageRequestHeaders: Map<String, String>,
		playWhenReady: Boolean
	) = Unit

	override fun dispatch(command: ReaderReadaloudPlaybackCommand) = Unit
}
