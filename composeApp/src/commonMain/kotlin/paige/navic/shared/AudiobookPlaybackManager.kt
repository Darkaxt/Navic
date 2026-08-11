package paige.navic.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.ui.core.AudiobookMiniPlayerUiState

data class AudiobookPlaybackTimelineSnapshot(
	val sessionGeneration: Long,
	val position: ReadaloudPlaybackPosition
) {
	init {
		require(sessionGeneration >= 0L)
	}
}

interface AudiobookPlaybackManager {
	val uiState: StateFlow<AudiobookMiniPlayerUiState>
	val playbackTimelineRevision: StateFlow<Long>

	fun currentPlaybackTimelineSnapshot(): AudiobookPlaybackTimelineSnapshot?

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
	private val _playbackTimelineRevision = MutableStateFlow(0L)
	override val playbackTimelineRevision: StateFlow<Long> =
		_playbackTimelineRevision.asStateFlow()

	override fun currentPlaybackTimelineSnapshot(): AudiobookPlaybackTimelineSnapshot? = null

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
