package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.koinInject
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.ReadaloudAudioController
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderReadaloudSyncState
import paige.navic.reader.StorytellerReadaloudRuntime
import paige.navic.reader.StorytellerReadaloudRuntimeLoader
import paige.navic.reader.onPlaybackPosition
import paige.navic.reader.onReaderEvent
import paige.navic.ui.navigation.Screen
import java.io.File

@Composable
actual fun ReaderReadaloudRuntimeHost(
	reader: Screen.Reader,
	readerEvent: ReaderBridgeEvent?,
	readerEventKey: Long,
	onPublicationReady: (String) -> Unit,
	onReaderCommand: (ReaderBridgeCommand, Long) -> Unit,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit
) {
	if (reader.kind != ReaderPublicationKind.Readaloud || !reader.mediaOverlayEnabled) return

	val context = LocalContext.current
	val repository = koinInject<BinderyRepository>()
	var runtime by remember(reader.resourceHref) { mutableStateOf<StorytellerReadaloudRuntime?>(null) }
	var syncState by remember(reader.resourceHref) { mutableStateOf(ReaderReadaloudSyncState()) }
	val currentRuntime by rememberUpdatedState(runtime)
	val currentSyncState by rememberUpdatedState(syncState)
	val currentOnReaderCommand by rememberUpdatedState(onReaderCommand)
	val currentOnPlaybackState by rememberUpdatedState(onPlaybackState)
	val currentOnError by rememberUpdatedState(onError)
	val controller = remember(context) {
		ReadaloudAudioController(context) { position ->
			currentOnPlaybackState(position.toReaderReadaloudPlaybackUiState(isAvailable = currentRuntime != null))
			val activeRuntime = currentRuntime ?: return@ReadaloudAudioController
			val nextState = currentSyncState.onPlaybackPosition(
				plan = activeRuntime.playbackPlan,
				timeline = activeRuntime.timeline,
				position = position
			)
			if (nextState.readerCommandKey != currentSyncState.readerCommandKey) {
				nextState.readerCommand?.let { command ->
					currentOnReaderCommand(command, nextState.readerCommandKey)
				}
			}
			syncState = nextState
		}
	}

	DisposableEffect(controller) {
		onDispose {
			controller.release()
		}
	}

	LaunchedEffect(reader.bookId, reader.resourceHref, reader.title) {
		runtime = null
		syncState = ReaderReadaloudSyncState()
		runCatching {
			StorytellerReadaloudRuntimeLoader(
				fetchResourceBytes = { path -> repository.getResourceBytes(path).getOrThrow() },
				cacheRoot = File(context.cacheDir, "reader")
			).load(
				ReaderPublicationResourceRequest(
					bookId = reader.bookId,
					title = reader.title,
					resourceHref = reader.resourceHref,
					sourceUrl = reader.publicationUrl,
					kind = reader.kind,
					mediaOverlayEnabled = reader.mediaOverlayEnabled
				)
			)
		}.fold(
			onSuccess = { loadedRuntime ->
				runtime = loadedRuntime
				controller.load(loadedRuntime.playbackPlan, playWhenReady = false)
				onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = true))
				onPublicationReady(loadedRuntime.publicationUrl)
			},
			onFailure = { error ->
				onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
				currentOnError(error.message ?: "Unable to load readaloud publication.")
			}
		)
	}

	LaunchedEffect(playbackCommandKey) {
		when (playbackCommand) {
			ReaderReadaloudPlaybackCommand.Play -> controller.play()
			ReaderReadaloudPlaybackCommand.Pause -> controller.pause()
			null -> Unit
		}
	}

	LaunchedEffect(readerEventKey) {
		val event = readerEvent ?: return@LaunchedEffect
		val activeRuntime = runtime ?: return@LaunchedEffect
		val step = syncState.onReaderEvent(
			plan = activeRuntime.playbackPlan,
			timeline = activeRuntime.timeline,
			event = event
		)
		if (step.state.readerCommandKey != syncState.readerCommandKey) {
			step.state.readerCommand?.let { command ->
				onReaderCommand(command, step.state.readerCommandKey)
			}
		}
		syncState = step.state
		step.audioSeekTarget?.let { seekTarget ->
			controller.seekTo(seekTarget.trackIndex, seekTarget.positionMs)
		}
	}
}

private fun paige.navic.reader.ReadaloudPlaybackPosition.toReaderReadaloudPlaybackUiState(
	isAvailable: Boolean
): ReaderReadaloudPlaybackUiState =
	ReaderReadaloudPlaybackUiState(
		isAvailable = isAvailable,
		isPlaying = isPlaying,
		positionMs = positionMs,
		durationMs = durationMs,
		playbackSpeed = playbackSpeed
	)
