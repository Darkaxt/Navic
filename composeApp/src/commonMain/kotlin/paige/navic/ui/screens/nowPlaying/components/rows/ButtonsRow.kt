package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.icons.Icons
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.filled.RepeatOn
import paige.navic.icons.filled.RepeatOneOn
import paige.navic.icons.filled.ShuffleOn
import paige.navic.icons.filled.SkipNext
import paige.navic.icons.filled.SkipPrevious
import paige.navic.icons.outlined.Repeat
import paige.navic.icons.outlined.Shuffle
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.NowPlayingPlaybackButtonsArrangement
import paige.navic.domain.models.NowPlayingPlaybackControl
import paige.navic.domain.models.nowPlayingPlaybackButtonsArrangement
import paige.navic.domain.models.nowPlayingPlaybackControls
import paige.navic.domain.models.nowPlayingPlayButtonSpeedLabel
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.playPauseIconPainter

@Composable
fun NowPlayingButtonsRow(
	modifier: Modifier = Modifier
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val scale = remember { Animatable(1f) }
	val enabled = playerState.currentSong != null
	val controls = nowPlayingPlaybackControls(
		showShuffleControl = preferenceManager.showNowPlayingShuffleControl,
		showRepeatControl = preferenceManager.showNowPlayingRepeatControl
	)
	val buttonArrangement = nowPlayingPlaybackButtonsArrangement(
		spaceControlsEvenly = preferenceManager.spaceNowPlayingPlaybackControlsEvenly
	)
	val compactButtons = buttonArrangement == NowPlayingPlaybackButtonsArrangement.Compact
	val speedLabel = nowPlayingPlayButtonSpeedLabel(playerState.playbackSpeed)

	LaunchedEffect(isPressed) {
		if (!isPressed) {
			if (scale.value != 1f) {
				scale.animateTo(
					targetValue = 1.2f,
					animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
				)
				scale.animateTo(
					targetValue = 1f,
					animationSpec = spring(
						dampingRatio = Spring.DampingRatioMediumBouncy,
						stiffness = Spring.StiffnessLow
					)
				)
			}
		} else {
			scale.animateTo(0.95f)
		}
	}

	Row(
		modifier = modifier
			.widthIn(max = 400.dp)
			.then(if (compactButtons) Modifier else Modifier.fillMaxWidth()),
		horizontalArrangement = if (compactButtons) {
			Arrangement.spacedBy(16.dp)
		} else {
			Arrangement.SpaceEvenly
		},
		verticalAlignment = Alignment.CenterVertically
	) {
		controls.forEach { control ->
			when (control) {
				NowPlayingPlaybackControl.Shuffle -> IconButton(
					modifier = nowPlayingButtonModifier(buttonArrangement),
					onClick = {
						platformContext.clickSound()
						player.toggleShuffle()
					},
					enabled = enabled,
				) {
					Icon(
						imageVector = if (playerState.isShuffleEnabled)
							Icons.Filled.ShuffleOn
						else Icons.Outlined.Shuffle,
						contentDescription = null,
						modifier = Modifier.size(24.dp)
					)
				}

				NowPlayingPlaybackControl.Previous -> IconButton(
					modifier = nowPlayingButtonModifier(buttonArrangement),
					onClick = {
						platformContext.clickSound()
						player.previous()
					},
					enabled = enabled
				) {
					Icon(
						imageVector = Icons.Filled.SkipPrevious,
						contentDescription = null,
						modifier = Modifier.size(32.dp)
					)
				}

				NowPlayingPlaybackControl.PlayPause -> IconButton(
					modifier = nowPlayingButtonModifier(
						arrangement = buttonArrangement,
						compactWeight = 1.3f,
						evenSize = 64.dp
					)
						.scale(scale.value)
						.clip(CircleShape)
						.indication(interactionSource, ripple(color = Color.Black)),
					colors = IconButtonDefaults.filledIconButtonColors(),
					onClick = {
						platformContext.clickSound()
						player.togglePlay()
					},
					enabled = enabled,
					interactionSource = interactionSource
				) {
					val painter = playPauseIconPainter(playerState.isPaused)
					AnimatedContent(playerState.isLoading) { isBuffering ->
						if (!isBuffering) {
							Box(
								modifier = Modifier.size(48.dp),
								contentAlignment = Alignment.Center
							) {
								val iconModifier = Modifier
									.align(Alignment.Center)
									.padding(bottom = if (speedLabel != null) 8.dp else 0.dp)
									.size(if (speedLabel != null) 34.dp else 40.dp)

								if (painter != null) {
									Icon(
										painter = painter,
										contentDescription = null,
										modifier = iconModifier
									)
								} else {
									Icon(
										imageVector = if (playerState.isPaused)
											Icons.Filled.Play
										else Icons.Filled.Pause,
										contentDescription = null,
										modifier = iconModifier
									)
								}
								speedLabel?.let { label ->
									Text(
										text = label,
										modifier = Modifier
											.align(Alignment.BottomCenter)
											.padding(bottom = 1.dp),
										color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f),
										style = MaterialTheme.typography.labelSmall
									)
								}
							}
						} else {
							CircularProgressIndicator(
								Modifier.size(40.dp),
								color = MaterialTheme.colorScheme.onPrimary,
								trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .5f),
							)
						}
					}
				}

				NowPlayingPlaybackControl.Next -> IconButton(
					modifier = nowPlayingButtonModifier(buttonArrangement),
					onClick = {
						platformContext.clickSound()
						player.next()
					},
					enabled = enabled,
				) {
					Icon(
						imageVector = Icons.Filled.SkipNext,
						contentDescription = null,
						modifier = Modifier.size(32.dp)
					)
				}

				NowPlayingPlaybackControl.Repeat -> IconButton(
					modifier = nowPlayingButtonModifier(buttonArrangement),
					onClick = {
						platformContext.clickSound()
						player.toggleRepeat()
					},
					enabled = enabled,
				) {
					Icon(
						imageVector = when (playerState.repeatMode) {
							1 -> Icons.Filled.RepeatOneOn
							2 -> Icons.Filled.RepeatOn
							else -> Icons.Outlined.Repeat
						},
						contentDescription = null,
						modifier = Modifier.size(24.dp)
					)
				}
			}
		}
	}
}

private fun RowScope.nowPlayingButtonModifier(
	arrangement: NowPlayingPlaybackButtonsArrangement,
	compactWeight: Float = 1f,
	evenSize: Dp = 48.dp
): Modifier =
	when (arrangement) {
		NowPlayingPlaybackButtonsArrangement.Compact -> Modifier
			.weight(compactWeight)
			.aspectRatio(1f)

		NowPlayingPlaybackButtonsArrangement.EvenlySpaced -> Modifier.size(evenSize)
	}
