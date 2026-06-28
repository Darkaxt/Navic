package paige.navic.ui.screens.bindery

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
				BinderyWhispersyncCoverBadge(
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(8.dp)
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

@Composable
private fun BinderyWhispersyncCoverBadge(modifier: Modifier = Modifier) {
	Icon(
		imageVector = Icons.Outlined.Headset,
		contentDescription = "Whispersync available",
		modifier = modifier.size(22.dp),
		tint = Color(0xFFF3E5C2).copy(alpha = 0.62f)
	)
}
