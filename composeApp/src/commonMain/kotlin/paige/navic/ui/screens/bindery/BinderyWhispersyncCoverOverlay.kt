package paige.navic.ui.screens.bindery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import paige.navic.domain.repositories.BinderyLink
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Headset

fun binderyBookCoverOverlay(
	hasActionableWhispersync: Boolean,
	action: BinderyOpdsAction?,
	loading: Boolean,
	onAction: (BinderyLink) -> Unit
): (@Composable BoxScope.() -> Unit)? =
	if (!hasActionableWhispersync && action == null) {
		null
	} else {
		{
			if (hasActionableWhispersync) {
				// Top-right, so it never sits on the availability dot (top-left).
				BinderyWhispersyncCoverBadge(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(6.dp)
				)
			}
			if (action != null) {
				BinderyCardActionButton(
					action = action,
					loading = loading,
					onAction = onAction,
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(8.dp)
				)
			}
		}
	}

/** A round paper sticker on the cover, readable on light and dark artwork alike. */
@Composable
private fun BinderyWhispersyncCoverBadge(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.size(26.dp)
			.shadow(elevation = 2.dp, shape = CircleShape)
			.background(Color(0xFFF3E5C2), CircleShape)
			.border(width = 1.dp, color = Color(0xFF3A2E1E).copy(alpha = 0.35f), shape = CircleShape),
		contentAlignment = Alignment.Center
	) {
		Icon(
			imageVector = Icons.Outlined.Headset,
			contentDescription = "Whispersync available",
			modifier = Modifier.size(17.dp),
			tint = Color(0xFF3A2E1E)
		)
	}
}
