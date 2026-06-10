package paige.navic.ui.screens.bindery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import org.koin.compose.koinInject
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackLogTag
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.ui.core.AudiobookMiniPlayerUiState
import paige.navic.util.core.Logger

@Composable
actual fun BinderyAudiobookRuntimeHost(
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
	onPlaybackPosition: (paige.navic.reader.ReadaloudPlaybackPosition) -> Unit,
	onError: (String) -> Unit
) {
	val playbackManager = koinInject<AudiobookPlaybackManager>()
	val miniPlayerState by playbackManager.uiState.collectAsState()
	val currentOnPlaybackState = rememberUpdatedState(onPlaybackState)
	val currentOnPlaybackPosition = rememberUpdatedState(onPlaybackPosition)
	val currentOnError = rememberUpdatedState(onError)

	LaunchedEffect(playbackPlan, bookId, bookTitle, versionRowId, coverUrl, coverCacheKey, imageRequestHeaders) {
		val plan = playbackPlan
		if (plan == null) {
			onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
			return@LaunchedEffect
		}
		runCatching {
			playbackManager.load(
				playbackPlan = plan,
				bookId = bookId,
				bookTitle = bookTitle,
				versionRowId = versionRowId,
				coverUrl = coverUrl,
				coverCacheKey = coverCacheKey,
				imageRequestHeaders = imageRequestHeaders,
				playWhenReady = true
			)
		}.onFailure { error ->
			Logger.e(ReadaloudPlaybackLogTag, "Failed to load Bindery audiobook playback plan", error)
			onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
			currentOnError.value(error.message ?: "Unable to load audiobook playback.")
		}
	}

	LaunchedEffect(playbackCommandKey) {
		playbackCommand?.let { command ->
			playbackManager.dispatch(command)
		}
	}

	LaunchedEffect(miniPlayerState) {
		currentOnPlaybackState.value(miniPlayerState.toReaderReadaloudPlaybackUiState())
		if (miniPlayerState.isAvailable) {
			currentOnPlaybackPosition.value(miniPlayerState.toReadaloudPlaybackPosition())
		}
	}
}

private fun AudiobookMiniPlayerUiState.toReaderReadaloudPlaybackUiState(): ReaderReadaloudPlaybackUiState =
	ReaderReadaloudPlaybackUiState(
		isAvailable = isAvailable,
		isPlaying = isPlaying,
		trackIndex = trackIndex,
		positionMs = positionMs,
		durationMs = durationMs,
		playbackSpeed = playbackSpeed,
		activeAudioMetadata = activeAudioMetadata
	)

private fun AudiobookMiniPlayerUiState.toReadaloudPlaybackPosition(): ReadaloudPlaybackPosition =
	ReadaloudPlaybackPosition(
		sessionId = bookId,
		trackIndex = trackIndex,
		mediaId = mediaId,
		positionMs = positionMs,
		durationMs = durationMs,
		isPlaying = isPlaying,
		playbackSpeed = playbackSpeed
	)
