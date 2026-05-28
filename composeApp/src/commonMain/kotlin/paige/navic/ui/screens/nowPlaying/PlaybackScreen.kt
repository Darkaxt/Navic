package paige.navic.ui.screens.nowPlaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_playback_pitch
import navic.composeapp.generated.resources.option_playback_speed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.domain.models.normalizedPlaybackPitch
import paige.navic.domain.models.normalizedPlaybackSpeed
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.util.core.supportsPlaybackPitch
import paige.navic.util.ui.rememberDraggableListState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackSpeedScreen() {
	val player = koinInject<MediaPlayerViewModel>()
	val lazyListState = rememberLazyListState()
	val haptic = LocalHapticFeedback.current
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val draggableState = rememberDraggableListState(lazyListState) { from, to ->
		player.moveQueueItem(from, to)
		haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
	}

	val selectedSpeed = playerState.playbackSpeed
	val selectedPitch = playerState.playbackPitch
	val pitchSupported = supportsPlaybackPitch()
	val playbackSpeeds = listOf(
		1.0f,
		1.25f,
		1.5f,
		1.75f,
		2.0f
	)

	LazyColumn(
		modifier = Modifier
			.padding(horizontal = 12.dp, vertical = 4.dp)
			.fillMaxWidth()
			.clip(ContinuousRoundedRectangle(topStart = 16.dp, topEnd = 16.dp)),
		state = draggableState.listState
	) {
		item {
			PlaybackParameterSlider(
				label = stringResource(Res.string.option_playback_speed),
				value = selectedSpeed,
				onValueChange = { player.setPlaybackSpeed(normalizedPlaybackSpeed(it)) }
			)

			if (pitchSupported) {
				Spacer(Modifier.height(8.dp))
				PlaybackParameterSlider(
					label = stringResource(Res.string.option_playback_pitch),
					value = selectedPitch,
					onValueChange = { player.setPlaybackPitch(normalizedPlaybackPitch(it)) }
				)
			}

			Spacer(Modifier.height(8.dp))
		}

		item {
			Column(
				modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				Text("${selectedSpeed}x")

				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center
				) {
					playbackSpeeds.forEach { speed ->
						SurfaceButton(
							modifier = Modifier.weight(1f),
							onClick = { player.setPlaybackSpeed(speed) },
							text = "$speed"
						)
					}
				}
			}
		}
	}
}

@Composable
private fun PlaybackParameterSlider(
	label: String,
	value: Float,
	onValueChange: (Float) -> Unit
) {
	Column {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(label)
			Text("${value}x", color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Slider(
			value = value,
			onValueChange = onValueChange,
			valueRange = 0.5f..2.0f
		)
	}
}

@Composable
fun SurfaceButton(
	modifier: Modifier,
	onClick: () -> Unit,
	text: String
) {
	Surface(
		modifier = modifier.padding(4.dp),
		shape = ContinuousCapsule,
		onClick = onClick,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		contentColor = MaterialTheme.colorScheme.onSurface
	) {
		Column(
			modifier = Modifier.padding(8.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Text(text)
		}
	}
}
