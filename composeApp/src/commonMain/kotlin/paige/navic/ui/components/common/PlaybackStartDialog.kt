package paige.navic.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_close
import navic.composeapp.generated.resources.info_preparing_playback
import navic.composeapp.generated.resources.info_starting_playback
import org.jetbrains.compose.resources.stringResource
import paige.navic.shared.PlaybackStartFeedback
import paige.navic.shared.PlaybackStartPhase

@Composable
fun PlaybackStartDialog(feedback: PlaybackStartFeedback) {
	val status by feedback.state.collectAsStateWithLifecycle()
	val current = status?.takeIf { it.visible } ?: return
	AlertDialog(
		onDismissRequest = feedback::hide,
		text = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(20.dp)
			) {
				CircularProgressIndicator(modifier = Modifier.size(32.dp))
				Text(stringResource(when (current.phase) {
					PlaybackStartPhase.Preparing -> Res.string.info_preparing_playback
					PlaybackStartPhase.Starting -> Res.string.info_starting_playback
				}))
			}
		},
		confirmButton = {
			TextButton(onClick = feedback::hide) {
				Text(stringResource(Res.string.action_close))
			}
		}
	)
}
