package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_seek_backward_seconds
import navic.composeapp.generated.resources.action_seek_forward_seconds
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.nowPlayingDurationLabels
import paige.navic.domain.models.nowPlayingSeekProgress
import paige.navic.icons.Icons
import paige.navic.icons.filled.Forward10
import paige.navic.icons.filled.Replay10
import paige.navic.shared.MediaPlayerViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun NowPlayingDurationsRow() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val duration = playerState.currentSong?.duration
	val canSeek = duration != null && duration > Duration.ZERO
	val labels = nowPlayingDurationLabels(
		duration = duration,
		progress = playerState.progress,
		showRemainingTime = preferenceManager.showNowPlayingRemainingTime
	)
	val style = MaterialTheme.typography.bodyMedium
		.copy(
			shadow = Shadow(
				color = MaterialTheme.colorScheme.inverseOnSurface,
				offset = Offset(0f, 4f),
				blurRadius = 10f
			)
		)
	val color = MaterialTheme.colorScheme.onSurfaceVariant
	Row(
		modifier = Modifier.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (preferenceManager.showNowPlayingSeekButtons) {
			NowPlayingSeekButton(
				enabled = canSeek,
				contentDescription = stringResource(Res.string.action_seek_backward_seconds, 10),
				onClick = {
					val target = nowPlayingSeekProgress(
						currentProgress = playerState.progress,
						duration = duration,
						adjustment = (-10).seconds
					) ?: return@NowPlayingSeekButton
					platformContext.clickSound()
					player.seek(target)
				}
			) { contentDescription ->
				Icon(
					imageVector = Icons.Filled.Replay10,
					contentDescription = contentDescription,
					modifier = Modifier.size(20.dp)
				)
			}
		}
		Text(labels.elapsed, color = color, style = style)
		Spacer(Modifier.weight(1f))
		labels.remaining?.let {
			Text(it, color = color, style = style)
			Spacer(Modifier.weight(1f))
		}
		Text(labels.total, color = color, style = style)
		if (preferenceManager.showNowPlayingSeekButtons) {
			NowPlayingSeekButton(
				enabled = canSeek,
				contentDescription = stringResource(Res.string.action_seek_forward_seconds, 10),
				onClick = {
					val target = nowPlayingSeekProgress(
						currentProgress = playerState.progress,
						duration = duration,
						adjustment = 10.seconds
					) ?: return@NowPlayingSeekButton
					platformContext.clickSound()
					player.seek(target)
				}
			) { contentDescription ->
				Icon(
					imageVector = Icons.Filled.Forward10,
					contentDescription = contentDescription,
					modifier = Modifier.size(20.dp)
				)
			}
		}
	}
}

@Composable
private fun NowPlayingSeekButton(
	enabled: Boolean,
	contentDescription: String,
	onClick: () -> Unit,
	content: @Composable (contentDescription: String) -> Unit
) {
	IconButton(
		modifier = Modifier.size(32.dp),
		onClick = onClick,
		enabled = enabled
	) {
		content(contentDescription)
	}
}
