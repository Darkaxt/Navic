package paige.navic.ui.screens.bindery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_bindery_monitor
import navic.composeapp.generated.resources.action_bindery_request_download
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.repositories.BinderyLink
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Add
import paige.navic.icons.outlined.Download

@Composable
fun BinderyCardActionButton(
	action: BinderyOpdsAction,
	loading: Boolean,
	onAction: (BinderyLink) -> Unit,
	modifier: Modifier = Modifier
) {
	Surface(
		onClick = { if (!loading) onAction(action.link) },
		modifier = modifier.size(40.dp),
		shape = CircleShape,
		color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .96f),
		contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
		shadowElevation = 3.dp
	) {
		Row(
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically
		) {
			if (loading) {
				CircularProgressIndicator(
					modifier = Modifier.size(18.dp),
					strokeWidth = 2.dp,
					color = MaterialTheme.colorScheme.onSecondaryContainer
				)
			} else {
				Icon(
					imageVector = action.icon(),
					contentDescription = stringResource(action.labelResource()),
					modifier = Modifier.size(20.dp)
				)
			}
		}
	}
}

@Composable
fun BinderyActionButton(
	action: BinderyOpdsAction,
	loading: Boolean,
	onAction: (BinderyLink) -> Unit,
	modifier: Modifier = Modifier
) {
	FilledTonalButton(
		onClick = { onAction(action.link) },
		enabled = !loading,
		modifier = modifier
	) {
		if (loading) {
			CircularProgressIndicator(
				modifier = Modifier.size(ButtonDefaults.IconSize),
				strokeWidth = 2.dp
			)
		} else {
			Icon(
				imageVector = action.icon(),
				contentDescription = null,
				modifier = Modifier.size(ButtonDefaults.IconSize)
			)
		}
		Text(
			text = stringResource(action.labelResource()),
			modifier = Modifier.padding(start = 8.dp)
		)
	}
}

private fun BinderyOpdsAction.icon(): ImageVector =
	when (type) {
		BinderyOpdsActionType.Monitor -> Icons.Outlined.Add
		BinderyOpdsActionType.DownloadRequest -> Icons.Outlined.Download
	}

private fun BinderyOpdsAction.labelResource(): StringResource =
	when (type) {
		BinderyOpdsActionType.Monitor -> Res.string.action_bindery_monitor
		BinderyOpdsActionType.DownloadRequest -> Res.string.action_bindery_request_download
	}
