package paige.navic.ui.screens.bindery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import paige.navic.reader.ReadaloudAudioController
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackLogTag
import paige.navic.util.core.Logger

@Composable
actual fun BinderyAudiobookRuntimeHost(
	playbackPlan: ReadaloudPlaybackPlan?,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onPlaybackPosition: (paige.navic.reader.ReadaloudPlaybackPosition) -> Unit,
	onError: (String) -> Unit
) {
	val context = LocalContext.current
	val currentOnPlaybackState = rememberUpdatedState(onPlaybackState)
	val currentOnPlaybackPosition = rememberUpdatedState(onPlaybackPosition)
	val currentOnError = rememberUpdatedState(onError)
	val currentPlaybackPlan = rememberUpdatedState(playbackPlan)
	val controller = remember(context) {
		ReadaloudAudioController(context) { position ->
			currentOnPlaybackPosition.value(position)
			currentOnPlaybackState.value(
				ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = position.isPlaying,
					trackIndex = position.trackIndex,
					positionMs = position.positionMs,
					durationMs = position.durationMs,
					playbackSpeed = position.playbackSpeed,
					activeAudioMetadata = currentPlaybackPlan.value?.metadataLabelsForPosition(position)
				)
			)
		}
	}

	DisposableEffect(controller) {
		onDispose {
			controller.release()
		}
	}

	LaunchedEffect(playbackPlan) {
		val plan = playbackPlan
		if (plan == null) {
			onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
			return@LaunchedEffect
		}
		runCatching {
			controller.load(plan, playWhenReady = true)
			onPlaybackState(
				ReaderReadaloudPlaybackUiState(
					isAvailable = plan.mediaItems.isNotEmpty(),
					trackIndex = plan.startTrackIndex,
					positionMs = plan.startPositionMs,
					durationMs = plan.mediaItems.getOrNull(plan.startTrackIndex)?.durationMs,
					playbackSpeed = plan.playbackSpeed,
					activeAudioMetadata = plan.mediaItems
						.getOrNull(plan.startTrackIndex)
						?.toReadaloudPlaybackMetadataLabels()
				)
			)
		}.onFailure { error ->
			Logger.e(ReadaloudPlaybackLogTag, "Failed to load Bindery audiobook playback plan", error)
			onPlaybackState(ReaderReadaloudPlaybackUiState(isAvailable = false))
			currentOnError.value(error.message ?: "Unable to load audiobook playback.")
		}
	}

	LaunchedEffect(playbackCommandKey) {
		when (val command = playbackCommand) {
			ReaderReadaloudPlaybackCommand.Play -> controller.play()
			ReaderReadaloudPlaybackCommand.Pause -> controller.pause()
			is ReaderReadaloudPlaybackCommand.SeekTo -> controller.seekTo(command.positionMs)
			is ReaderReadaloudPlaybackCommand.SeekToTrack -> controller.seekTo(command.trackIndex, command.positionMs)
			is ReaderReadaloudPlaybackCommand.SetSpeed -> controller.setPlaybackSpeed(command.speed)
			is ReaderReadaloudPlaybackCommand.SetSyncEnabled,
			null -> Unit
		}
	}
}

private fun ReadaloudPlaybackPlan.metadataLabelsForPosition(
	position: paige.navic.reader.ReadaloudPlaybackPosition
) = mediaItems.getOrNull(position.trackIndex)?.toReadaloudPlaybackMetadataLabels()

private fun paige.navic.reader.ReadaloudMediaItemDescriptor.toReadaloudPlaybackMetadataLabels() =
	paige.navic.reader.ReadaloudPlaybackMetadataLabels(
		chapterLabel = title.trimLabel(),
		sectionLabel = subtitle.trimLabel(),
		narratorLabel = artist.trimLabel(),
		qualityLabel = qualityLabel.trimLabel(),
		sourceProviderLabel = sourceProviderLabel.trimLabel(),
		sourceReleaseLabel = sourceReleaseLabel.trimLabel(),
		sourceUrlLabel = sourceUrl.trimLabel(),
		formatLabel = listOfNotNull(
			codec.trimLabel(),
			bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
		).joinToString(separator = " / ").takeIf { it.isNotBlank() }
	)

private fun String?.trimLabel(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }
