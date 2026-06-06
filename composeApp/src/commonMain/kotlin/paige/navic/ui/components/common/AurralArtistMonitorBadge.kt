package paige.navic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import paige.navic.ui.screens.artist.AurralMonitorActionState
import paige.navic.ui.screens.artist.aurralMonitorActionIconOverlay

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
		AurralActionIcon(
			overlay = aurralMonitorActionIconOverlay(state),
			contentDescription = null,
			tint = when (state) {
				AurralMonitorActionState.Monitored -> MaterialTheme.colorScheme.primary
				AurralMonitorActionState.NotMonitored -> MaterialTheme.colorScheme.onSurfaceVariant
				AurralMonitorActionState.PendingVerification -> MaterialTheme.colorScheme.onSurfaceVariant
				AurralMonitorActionState.PendingConfirmation -> MaterialTheme.colorScheme.primary
			},
			overlayColor = MaterialTheme.colorScheme.primary,
			progressColor = MaterialTheme.colorScheme.primary,
			size = 20.dp
		)
	}
}
