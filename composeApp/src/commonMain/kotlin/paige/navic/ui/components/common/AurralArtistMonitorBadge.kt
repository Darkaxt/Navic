package paige.navic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Visibility
import paige.navic.icons.outlined.VisibilityOff
import paige.navic.ui.screens.artist.AurralMonitorActionState

@Composable
fun AurralArtistMonitorBadge(
	state: AurralMonitorActionState,
	modifier: Modifier = Modifier
) {
	Box(
		modifier = modifier
			.size(28.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.surface.copy(alpha = .86f)),
		contentAlignment = Alignment.Center
	) {
		Icon(
			imageVector = when (state) {
				AurralMonitorActionState.Monitored -> Icons.Outlined.Visibility
				AurralMonitorActionState.NotMonitored -> Icons.Outlined.VisibilityOff
				AurralMonitorActionState.PendingVerification -> Icons.Outlined.Visibility
			},
			contentDescription = null,
			tint = when (state) {
				AurralMonitorActionState.Monitored -> MaterialTheme.colorScheme.primary
				AurralMonitorActionState.NotMonitored -> MaterialTheme.colorScheme.onSurfaceVariant
				AurralMonitorActionState.PendingVerification -> MaterialTheme.colorScheme.onSurfaceVariant
			},
			modifier = Modifier.size(18.dp)
		)
	}
}
