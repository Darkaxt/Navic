package paige.navic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import paige.navic.domain.models.AurralOwnershipStatus

@Composable
fun AurralOwnershipStatusDot(
	status: AurralOwnershipStatus,
	modifier: Modifier = Modifier,
	size: Dp = 10.dp
) {
	Box(
		modifier = modifier
			.size(size)
			.clip(CircleShape)
			.background(aurralOwnershipStatusColor(status))
			.border(
				width = 1.dp,
				color = MaterialTheme.colorScheme.surface.copy(alpha = .86f),
				shape = CircleShape
			)
	)
}

@Composable
fun aurralOwnershipStatusColor(status: AurralOwnershipStatus): Color =
	when (status) {
		AurralOwnershipStatus.Owned -> Color(0xFF2AC769)
		AurralOwnershipStatus.Partial,
		AurralOwnershipStatus.Requested,
		AurralOwnershipStatus.Processing -> Color(0xFFFFB020)
		AurralOwnershipStatus.Failed,
		AurralOwnershipStatus.Missing -> MaterialTheme.colorScheme.error
	}
