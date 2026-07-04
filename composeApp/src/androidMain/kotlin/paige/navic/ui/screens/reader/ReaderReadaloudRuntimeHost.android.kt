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
import paige.navic.reader.ReadaloudPlaybackLogTag
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderMediaOverlaySyncState
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderReadaloudSyncState
import paige.navic.reader.StorytellerReadaloudRuntime
import paige.navic.reader.StorytellerReadaloudRuntimeLoader
import paige.navic.reader.metadataLabelsForPlaybackPosition
import paige.navic.reader.onPlaybackPosition
import paige.navic.reader.onReaderEvent
import paige.navic.reader.readerPublicationCacheRoot
import paige.navic.reader.readerPublicationResourceLogLabel
import paige.navic.reader.setSyncEnabled
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

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
) {
	if (reader.kind != ReaderPublicationKind.Readaloud || !reader.mediaOverlayEnabled) return

	val context = LocalContext.current
	val repository = koinInject<BinderyRepository>()
	var runtime by remember(reader.resourceHref) { mutableStateOf<StorytellerReadaloudRuntime?>(null) }
	var syncState by remember(reader.resourceHref) {
		mutableStateOf(ReaderReadaloudSyncState(overlayState = ReaderMediaOverlaySyncState(readaloudSyncEnabled)))
	}
	val currentRuntime by rememberUpdatedState(runtime)
	val currentSyncState by rememberUpdatedState(syncState)
	val currentOnEngineCommand by rememberUpdatedState(onEngineCommand)
	val currentOnPlaybackState by rememberUpdatedState(onPlaybackState)
	val currentOnError by rememberUpdatedState(onError)
	val controller = remember(context) {
		ReadaloudAudioController(context) { position ->
			val activeRuntime = currentRuntime ?: return@ReadaloudAudioController
			currentOnPlaybackState(
				position.toReaderReadaloudPlaybackUiState(
					isAvailable = true,
					activeAudioLabel = activeRuntime.timeline.activeLabelForPlaybackPosition(
						plan = activeRuntime.playbackPlan,
						position = position
					),
					activeAudioMetadata = activeRuntime.playbackPlan.metadataLabelsForPlaybackPosition(position),
					syncEnabled = currentSyncState.overlayState.syncEnabled
				)
			)
			val nextState = currentSyncState.onPlaybackPosition(
				plan = activeRuntime.playbackPlan,
				timeline = activeRuntime.timeline,
				position = position
			)
			if (nextState.engineCommandKey != currentSyncState.engineCommandKey) {
				nextState.engineCommand?.let { command ->
					currentOnEngineCommand(command, nextState.engineCommandKey)
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
		syncState = ReaderReadaloudSyncState(
			overlayState = ReaderMediaOverlaySyncState(readaloudSyncEnabled)
		)
		Logger.i(
			ReadaloudPlaybackLogTag,
			"Preparing readaloud publication bookId=${reader.bookId} " +
				"resource=${readerPublicationResourceLogLabel(reader.resourceHref)} " +
				"source=${readerPublicationResourceLogLabel(reader.publicationUrl)}"
		)
		runCatching {
			StorytellerReadaloudRuntimeLoader(
				fetchResourceBytes = { path ->
					Logger.i(
						ReadaloudPlaybackLogTag,
						"Fetching readaloud resource path=${readerPublicationResourceLogLabel(path)}"
					)
					repository.getResourceBytes(path).getOrThrow().also { bytes ->
						Logger.i(
							ReadaloudPlaybackLogTag,
							"Fetched readaloud resource path=${readerPublicationResourceLogLabel(path)} bytes=${bytes.size}"
						)
					}
				},
				cacheRoot = readerPublicationCacheRoot(context)
			).load(
				ReaderPublicationResourceRequest(
					bookId = reader.bookId,
					title = reader.title,
					resourceHref = reader.resourceHref,
					sourceUrl = reader.publicationUrl,
					kind = reader.kind,
					format = reader.publicationFormat,
					mediaOverlayEnabled = reader.mediaOverlayEnabled
				)
			)
		}.fold(
			onSuccess = { loadedRuntime ->
				runtime = loadedRuntime
				Logger.i(
					ReadaloudPlaybackLogTag,
					"Readaloud publication prepared url=${readerPublicationResourceLogLabel(loadedRuntime.publicationUrl)} " +
						"cache=${if (loadedRuntime.fromCache) "hit" else "miss"} " +
						"cacheKey=${loadedRuntime.cacheKey} " +
						"tracks=${loadedRuntime.playbackPlan.mediaItems.size} " +
						"clips=${loadedRuntime.timeline.clips.size}"
				)
				controller.load(loadedRuntime.playbackPlan, playWhenReady = false)
				onPlaybackState(
					ReaderReadaloudPlaybackUiState(
						isAvailable = true,
						syncEnabled = syncState.overlayState.syncEnabled
					)
				)
				onPublicationReady(loadedRuntime.publicationUrl)
			},
			onFailure = { error ->
				Logger.e(
					ReadaloudPlaybackLogTag,
					"Failed to load readaloud publication " +
						"bookId=${reader.bookId} resource=${readerPublicationResourceLogLabel(reader.resourceHref)} " +
						"title=${reader.title}",
					error
				)
				onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
				currentOnError(error.message ?: "Unable to load readaloud publication.")
			}
		)
	}

	LaunchedEffect(playbackCommandKey) {
		when (playbackCommand) {
			ReaderReadaloudPlaybackCommand.Play -> controller.play()
			ReaderReadaloudPlaybackCommand.Pause -> controller.pause()
			ReaderReadaloudPlaybackCommand.StopAndReset -> controller.stopAndReset()
			is ReaderReadaloudPlaybackCommand.SeekTo -> controller.seekTo(playbackCommand.positionMs)
			is ReaderReadaloudPlaybackCommand.SeekToTrack ->
				controller.seekTo(playbackCommand.trackIndex, playbackCommand.positionMs)
			is ReaderReadaloudPlaybackCommand.SetSpeed -> controller.setPlaybackSpeed(playbackCommand.speed)
			is ReaderReadaloudPlaybackCommand.SetSyncEnabled -> {
				if (!playbackCommand.enabled) {
					controller.stopAndReset()
				}
				val nextState = syncState.setSyncEnabled(playbackCommand.enabled)
				if (nextState.engineCommandKey != syncState.engineCommandKey) {
					nextState.engineCommand?.let { command ->
						currentOnEngineCommand(command, nextState.engineCommandKey)
					}
				}
				syncState = nextState
			}
			null -> Unit
		}
	}

	LaunchedEffect(readerHostEventKey) {
		val event = (readerHostEvent as? ReaderEngineHostEvent.FoliateBridge)?.event
			?: return@LaunchedEffect
		val activeRuntime = runtime ?: return@LaunchedEffect
		val step = syncState.onReaderEvent(
			plan = activeRuntime.playbackPlan,
			timeline = activeRuntime.timeline,
			event = event
		)
		if (step.state.engineCommandKey != syncState.engineCommandKey) {
			step.state.engineCommand?.let { command ->
				onEngineCommand(command, step.state.engineCommandKey)
			}
		}
		syncState = step.state
		step.audioSeekTarget?.let { seekTarget ->
			controller.seekTo(seekTarget.trackIndex, seekTarget.positionMs)
		}
	}
}

private fun paige.navic.reader.ReadaloudPlaybackPosition.toReaderReadaloudPlaybackUiState(
	isAvailable: Boolean,
	activeAudioLabel: String? = null,
	activeAudioMetadata: paige.navic.reader.ReadaloudPlaybackMetadataLabels? = null,
	syncEnabled: Boolean = true
): ReaderReadaloudPlaybackUiState =
	ReaderReadaloudPlaybackUiState(
		isAvailable = isAvailable,
		isPlaying = isPlaying,
		trackIndex = trackIndex,
		positionMs = positionMs,
		durationMs = durationMs,
		playbackSpeed = playbackSpeed,
		activeAudioLabel = activeAudioLabel,
		activeAudioMetadata = activeAudioMetadata,
		syncEnabled = syncEnabled
	)
