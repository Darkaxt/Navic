package paige.navic.ui.screens.reader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.ReadaloudAudioController
import paige.navic.reader.ReadaloudPlaybackLogTag
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderReadaloudReaderInteraction
import paige.navic.reader.ReaderReadaloudSyncState
import paige.navic.reader.ReaderSessionLease
import paige.navic.reader.StorytellerReadaloudRuntime
import paige.navic.reader.StorytellerReadaloudRuntimeLoader
import paige.navic.reader.metadataLabelsForPlaybackPosition
import paige.navic.reader.onPlaybackPosition
import paige.navic.reader.onReaderInteraction
import paige.navic.reader.readerManagedStorageRoot
import paige.navic.reader.setSyncEnabled
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

internal interface ReaderReadaloudController {
	fun load(plan: ReadaloudPlaybackPlan, playWhenReady: Boolean)
	fun play()
	fun pause()
	fun stopAndReset()
	fun seekTo(positionMs: Long)
	fun seekTo(trackIndex: Int, positionMs: Long)
	fun setPlaybackSpeed(value: Float)
	fun release()
}

internal fun interface ReaderReadaloudControllerFactory {
	fun create(
		context: Context,
		onPositionChanged: (ReadaloudPlaybackPosition) -> Unit
	): ReaderReadaloudController
}

private object AndroidReaderReadaloudControllerFactory : ReaderReadaloudControllerFactory {
	override fun create(
		context: Context,
		onPositionChanged: (ReadaloudPlaybackPosition) -> Unit
	): ReaderReadaloudController = AndroidReaderReadaloudController(
		ReadaloudAudioController(context = context, onPositionChanged = onPositionChanged)
	)
}

private class AndroidReaderReadaloudController(
	private val delegate: ReadaloudAudioController
) : ReaderReadaloudController {
	override fun load(plan: ReadaloudPlaybackPlan, playWhenReady: Boolean) =
		delegate.load(plan, playWhenReady)
	override fun play() = delegate.play()
	override fun pause() = delegate.pause()
	override fun stopAndReset() = delegate.stopAndReset()
	override fun seekTo(positionMs: Long) = delegate.seekTo(positionMs)
	override fun seekTo(trackIndex: Int, positionMs: Long) = delegate.seekTo(trackIndex, positionMs)
	override fun setPlaybackSpeed(value: Float) = delegate.setPlaybackSpeed(value)
	override fun release() = delegate.release()
}

@Composable
actual fun ReaderReadaloudRuntimeHost(
	reader: Screen.Reader,
	readaloudSyncEnabled: Boolean,
	readerInteraction: ReaderReadaloudReaderInteraction?,
	readerInteractionKey: Long,
	onPublicationReady: (String) -> Unit,
	onEngineCommand: (ReaderEngineCommand, Long) -> Unit,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit
) = ReaderReadaloudRuntimeHostWithControllerFactory(
	reader = reader,
	readaloudSyncEnabled = readaloudSyncEnabled,
	readerInteraction = readerInteraction,
	readerInteractionKey = readerInteractionKey,
	onPublicationReady = onPublicationReady,
	onEngineCommand = onEngineCommand,
	playbackCommand = playbackCommand,
	playbackCommandKey = playbackCommandKey,
	onPlaybackState = onPlaybackState,
	onError = onError,
	controllerFactory = AndroidReaderReadaloudControllerFactory
)

@Composable
internal fun ReaderReadaloudRuntimeHostWithControllerFactory(
	reader: Screen.Reader,
	readaloudSyncEnabled: Boolean,
	readerInteraction: ReaderReadaloudReaderInteraction?,
	readerInteractionKey: Long,
	onPublicationReady: (String) -> Unit,
	onEngineCommand: (ReaderEngineCommand, Long) -> Unit,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit,
	controllerFactory: ReaderReadaloudControllerFactory
) {
	if (reader.kind != ReaderPublicationKind.Readaloud || !reader.mediaOverlayEnabled) return

	val context = LocalContext.current
	val repository = koinInject<BinderyRepository>()
	val runtimeOwner = remember(reader) { mutableStateOf(true) }
	var runtime by remember(runtimeOwner) { mutableStateOf<StorytellerReadaloudRuntime?>(null) }
	var syncState by remember(runtimeOwner) {
		mutableStateOf(ReaderReadaloudSyncState(syncEnabled = readaloudSyncEnabled))
	}
	var consumedUserNavigationCausalSequence by remember(runtimeOwner) {
		mutableStateOf<Long?>(null)
	}
	val currentReader by rememberUpdatedState(reader)
	val currentRuntimeOwner by rememberUpdatedState(runtimeOwner)
	val currentRuntime by rememberUpdatedState(runtime)
	val currentSyncState by rememberUpdatedState(syncState)
	val currentOnEngineCommand by rememberUpdatedState(onEngineCommand)
	val currentOnPlaybackState by rememberUpdatedState(onPlaybackState)
	val sessionLeases = remember(runtimeOwner) { mutableListOf<ReaderSessionLease>() }
	val controller = remember(context, runtimeOwner, controllerFactory) {
		val controllerReader = reader
		val controllerOwner = runtimeOwner
		controllerFactory.create(
			context = context,
			onPositionChanged = positionChanged@{ position ->
				if (!controllerOwner.value || currentRuntimeOwner !== controllerOwner || currentReader != controllerReader) {
					return@positionChanged
				}
				val activeRuntime = currentRuntime ?: return@positionChanged
				if (position.sessionId != activeRuntime.playbackPlan.sessionId) return@positionChanged
				currentOnPlaybackState(
					position.toReaderReadaloudPlaybackUiState(
						isAvailable = true,
						activeAudioLabel = activeRuntime.timeline.activeLabelForPlaybackPosition(
							plan = activeRuntime.playbackPlan,
							position = position
						),
						activeAudioMetadata = activeRuntime.playbackPlan.metadataLabelsForPlaybackPosition(position),
						syncEnabled = currentSyncState.syncEnabled
					)
				)
				val nextState = currentSyncState.onPlaybackPosition(
					plan = activeRuntime.playbackPlan,
					timeline = activeRuntime.timeline,
					position = position
				)
				if (nextState.engineCommandKey != currentSyncState.engineCommandKey) {
					nextState.engineCommand?.let { command ->
						if (controllerOwner.value && currentRuntimeOwner === controllerOwner && currentReader == controllerReader) {
							currentOnEngineCommand(command, nextState.engineCommandKey)
						}
					}
				}
				if (controllerOwner.value && currentRuntimeOwner === controllerOwner && currentReader == controllerReader) {
					syncState = nextState
				}
			}
		)
	}

	DisposableEffect(controller, sessionLeases) {
		onDispose {
			runtimeOwner.value = false
			controller.release()
			sessionLeases.forEach(ReaderSessionLease::release)
		}
	}

	LaunchedEffect(runtimeOwner) {
		val operationReader = reader
		val operationOwner = runtimeOwner
		val operationOnPublicationReady = onPublicationReady
		val operationOnPlaybackState = onPlaybackState
		val operationOnError = onError
		runtime = null
		syncState = ReaderReadaloudSyncState(syncEnabled = readaloudSyncEnabled)
		consumedUserNavigationCausalSequence = null
		Logger.i(
			ReadaloudPlaybackLogTag,
			"Preparing readaloud publication"
		)
		val loadedRuntime = try {
			StorytellerReadaloudRuntimeLoader(
				fetchResourceBytes = { path ->
					Logger.i(
						ReadaloudPlaybackLogTag,
						"Fetching readaloud resource"
					)
					repository.getResourceBytes(path).getOrThrow().also { bytes ->
						Logger.i(
							ReadaloudPlaybackLogTag,
							"Fetched readaloud resource bytes=${bytes.size}"
						)
					}
				},
				cacheRoot = readerManagedStorageRoot(context)
			).load(
				ReaderPublicationResourceRequest(
					bookId = operationReader.bookId,
					title = operationReader.title,
					resourceHref = operationReader.resourceHref,
					sourceUrl = operationReader.publicationUrl,
					kind = operationReader.kind,
					format = operationReader.publicationFormat,
					mediaOverlayEnabled = operationReader.mediaOverlayEnabled
				)
			)
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (_: Throwable) {
			currentCoroutineContext().ensureActive()
			if (operationOwner.value && currentRuntimeOwner === operationOwner && currentReader == operationReader) {
				Logger.e(
					ReadaloudPlaybackLogTag,
					"Failed to load readaloud publication"
				)
				currentCoroutineContext().ensureActive()
				operationOnPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
				currentCoroutineContext().ensureActive()
				if (operationOwner.value && currentRuntimeOwner === operationOwner && currentReader == operationReader) {
					operationOnError("Unable to load readaloud publication.")
				}
			}
			return@LaunchedEffect
		}

		val operationContext = currentCoroutineContext()
		if (!operationContext.isActive || !operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) {
			loadedRuntime.sessionLease.release()
			operationContext.ensureActive()
			return@LaunchedEffect
		}
		sessionLeases += loadedRuntime.sessionLease
		runtime = loadedRuntime
		Logger.i(
			ReadaloudPlaybackLogTag,
			"Readaloud publication prepared fromCache=${loadedRuntime.fromCache} " +
				"tracks=${loadedRuntime.playbackPlan.mediaItems.size} " +
				"clips=${loadedRuntime.timeline.clips.size}"
		)
		currentCoroutineContext().ensureActive()
		if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
		controller.load(loadedRuntime.playbackPlan, playWhenReady = false)
		currentCoroutineContext().ensureActive()
		if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
		operationOnPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				syncEnabled = syncState.syncEnabled
			)
		)
		currentCoroutineContext().ensureActive()
		if (operationOwner.value && currentRuntimeOwner === operationOwner && currentReader == operationReader) {
			operationOnPublicationReady(loadedRuntime.publicationUrl)
		}
	}

	LaunchedEffect(runtimeOwner, controller, playbackCommandKey) {
		val operationReader = reader
		val operationOwner = runtimeOwner
		val operationCommand = playbackCommand
		currentCoroutineContext().ensureActive()
		if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
		when (operationCommand) {
			ReaderReadaloudPlaybackCommand.Play -> controller.play()
			ReaderReadaloudPlaybackCommand.Pause -> controller.pause()
			ReaderReadaloudPlaybackCommand.StopAndReset -> controller.stopAndReset()
			is ReaderReadaloudPlaybackCommand.SeekTo -> controller.seekTo(operationCommand.positionMs)
			is ReaderReadaloudPlaybackCommand.SeekToTrack ->
				controller.seekTo(operationCommand.trackIndex, operationCommand.positionMs)
			is ReaderReadaloudPlaybackCommand.SetSpeed -> controller.setPlaybackSpeed(operationCommand.speed)
			is ReaderReadaloudPlaybackCommand.SetSyncEnabled -> {
				if (!operationCommand.enabled) {
					currentCoroutineContext().ensureActive()
					if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
					controller.stopAndReset()
				}
				val nextState = syncState.setSyncEnabled(operationCommand.enabled)
				if (nextState.engineCommandKey != syncState.engineCommandKey) {
					nextState.engineCommand?.let { command ->
						currentCoroutineContext().ensureActive()
						if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
						currentOnEngineCommand(command, nextState.engineCommandKey)
					}
				}
				currentCoroutineContext().ensureActive()
				if (operationOwner.value && currentRuntimeOwner === operationOwner && currentReader == operationReader) syncState = nextState
			}
			null -> Unit
		}
	}

	LaunchedEffect(runtimeOwner, controller, readerInteractionKey) {
		val operationReader = reader
		val operationOwner = runtimeOwner
		val interaction = readerInteraction ?: return@LaunchedEffect
		val activeRuntime = runtime ?: return@LaunchedEffect
		currentCoroutineContext().ensureActive()
		if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
		val navigationSequence =
			(interaction as? ReaderReadaloudReaderInteraction.UserNavigation)?.causalSequence
		if (navigationSequence != null && navigationSequence == consumedUserNavigationCausalSequence) {
			return@LaunchedEffect
		}
		val step = syncState.onReaderInteraction(
			plan = activeRuntime.playbackPlan,
			timeline = activeRuntime.timeline,
			interaction = interaction
		)
		step.consumedUserNavigationCausalSequence?.let {
			currentCoroutineContext().ensureActive()
			if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
			consumedUserNavigationCausalSequence = it
		}
		if (step.state.engineCommandKey != syncState.engineCommandKey) {
			step.state.engineCommand?.let { command ->
				currentCoroutineContext().ensureActive()
				if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
				currentOnEngineCommand(command, step.state.engineCommandKey)
			}
		}
		currentCoroutineContext().ensureActive()
		if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
		syncState = step.state
		step.audioSeekTarget?.let { seekTarget ->
			currentCoroutineContext().ensureActive()
			if (!operationOwner.value || currentRuntimeOwner !== operationOwner || currentReader != operationReader) return@LaunchedEffect
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
