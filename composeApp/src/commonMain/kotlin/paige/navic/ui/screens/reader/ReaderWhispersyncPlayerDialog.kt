package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import paige.navic.icons.Icons
import paige.navic.icons.filled.Forward10
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.filled.Replay10
import paige.navic.icons.outlined.Close
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderWhispersyncStatus
import paige.navic.reader.readerReadaloudPlaybackSpeedLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuWhispersyncPlayerDialog(
	status: ReaderWhispersyncStatus,
	playbackState: ReaderReadaloudPlaybackUiState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onDismissRequest: () -> Unit
) {
	BasicAlertDialog(onDismissRequest = onDismissRequest) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.fillMaxWidth(0.82f)
				.widthIn(max = 520.dp)
		) {
			Column(
				modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				KomikkuWhispersyncPlayerHeader(
					status = status,
					playbackState = playbackState,
					onDismissRequest = onDismissRequest
				)
				KomikkuWhispersyncPlayerProgress(
					playbackState = playbackState,
					onCommand = onCommand
				)
				KomikkuWhispersyncTransportRow(
					playbackState = playbackState,
					onCommand = onCommand
				)
				HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
				KomikkuWhispersyncSpeedRow(
					playbackState = playbackState,
					onCommand = onCommand
				)
				KomikkuWhispersyncSyncRow(
					playbackState = playbackState,
					onCommand = onCommand
				)
			}
		}
	}
}

@Composable
private fun KomikkuWhispersyncPlayerHeader(
	status: ReaderWhispersyncStatus,
	playbackState: ReaderReadaloudPlaybackUiState,
	onDismissRequest: () -> Unit
) {
	val metadata = playbackState.activeAudioMetadata
	val title = metadata?.chapterLabel
		?: playbackState.activeAudioLabel
		?: status.label
		?: "Whispersync audiobook"
	val detail = listOfNotNull(
		metadata?.sectionLabel,
		metadata?.narratorLabel,
		metadata?.formatLabel
	).firstOrNull { it.isNotBlank() }
		?: status.detail
		?: if (playbackState.isAvailable) {
			"Track ${playbackState.trackIndex + 1}"
		} else {
			"Audio not loaded"
		}

	Row(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = detail,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		IconButton(onClick = onDismissRequest) {
			Icon(Icons.Outlined.Close, contentDescription = "Close")
		}
	}
}

@Composable
private fun KomikkuWhispersyncPlayerProgress(
	playbackState: ReaderReadaloudPlaybackUiState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	val durationMs = playbackState.durationMs?.takeIf { it > 0L }
	val progress = durationMs
		?.let { duration -> playbackState.positionMs.toFloat() / duration.toFloat() }
		?.coerceIn(0f, 1f)
		?: 0f

	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Slider(
			value = progress,
			onValueChange = { value ->
				durationMs?.let { duration ->
					onCommand(ReaderReadaloudPlaybackCommand.SeekTo((duration * value).roundToLong()))
				}
			},
			enabled = playbackState.isAvailable && durationMs != null
		)
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text = playbackState.positionMs.whispersyncTimeLabel(),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			Text(
				text = durationMs?.let { " - ${(it - playbackState.positionMs).coerceAtLeast(0L).whispersyncTimeLabel()}" }
					.orEmpty(),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.weight(1f)
			)
		}
	}
}

@Composable
private fun KomikkuWhispersyncTransportRow(
	playbackState: ReaderReadaloudPlaybackUiState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically
	) {
		KomikkuWhispersyncSeekButton(
			label = "-30",
			enabled = playbackState.isAvailable,
			onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(playbackState.positionMs - 30_000L)) }
		)
		IconButton(
			onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(playbackState.positionMs - 10_000L)) },
			enabled = playbackState.isAvailable
		) {
			Icon(Icons.Filled.Replay10, contentDescription = "Seek back 10 seconds")
		}
		Surface(
			onClick = {
				onCommand(
					if (playbackState.isPlaying) {
						ReaderReadaloudPlaybackCommand.Pause
					} else {
						ReaderReadaloudPlaybackCommand.Play
					}
				)
			},
			enabled = playbackState.isAvailable,
			shape = CircleShape,
			color = MaterialTheme.colorScheme.primaryContainer,
			contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
			modifier = Modifier.size(58.dp)
		) {
			Box(contentAlignment = Alignment.Center) {
				Icon(
					imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.Play,
					contentDescription = if (playbackState.isPlaying) "Pause audiobook" else "Play audiobook"
				)
			}
		}
		IconButton(
			onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(playbackState.positionMs + 10_000L)) },
			enabled = playbackState.isAvailable
		) {
			Icon(Icons.Filled.Forward10, contentDescription = "Seek forward 10 seconds")
		}
		KomikkuWhispersyncSeekButton(
			label = "+30",
			enabled = playbackState.isAvailable,
			onClick = { onCommand(ReaderReadaloudPlaybackCommand.SeekTo(playbackState.positionMs + 30_000L)) }
		)
	}
}

@Composable
private fun KomikkuWhispersyncSeekButton(
	label: String,
	enabled: Boolean,
	onClick: () -> Unit
) {
	TextButton(
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier.size(52.dp)
	) {
		Text(text = label, style = MaterialTheme.typography.labelLarge)
	}
}

@Composable
private fun KomikkuWhispersyncSpeedRow(
	playbackState: ReaderReadaloudPlaybackUiState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			text = "Speed",
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.SemiBold
		)
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			speeds.forEach { speed ->
				FilterChip(
					selected = readerReadaloudPlaybackSpeedLabel(playbackState.playbackSpeed) ==
						readerReadaloudPlaybackSpeedLabel(speed),
					onClick = { playbackState.speedCommandFor(speed)?.let(onCommand) },
					enabled = playbackState.isAvailable,
					label = { Text(readerReadaloudPlaybackSpeedLabel(speed)) }
				)
			}
		}
	}
}

@Composable
private fun KomikkuWhispersyncSyncRow(
	playbackState: ReaderReadaloudPlaybackUiState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = "Audio sync",
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.SemiBold
			)
			Text(
				text = if (playbackState.syncEnabled) "Following the ebook page" else "Playback detached from page turns",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		FilterChip(
			selected = playbackState.syncEnabled,
			onClick = { playbackState.toggleSyncCommand()?.let(onCommand) },
			enabled = playbackState.isAvailable,
			label = { Text(if (playbackState.syncEnabled) "On" else "Off") }
		)
	}
}

private fun Long.whispersyncTimeLabel(): String {
	val totalSeconds = coerceAtLeast(0L) / 1000L
	val hours = totalSeconds / 3600L
	val minutes = (totalSeconds % 3600L) / 60L
	val seconds = totalSeconds % 60L
	return if (hours > 0L) {
		"$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
	} else {
		"$minutes:${seconds.twoDigits()}"
	}
}

private fun Long.twoDigits(): String =
	if (this < 10L) "0$this" else toString()
