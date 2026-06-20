package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderWhispersyncStatus

private val readerWhispersyncBadgeFadeAnimationSpec = tween<Float>(150)

@Composable
internal fun KomikkuWhispersyncStatusBadge(
	status: ReaderWhispersyncStatus,
	modifier: Modifier = Modifier
) {
	AnimatedVisibility(
		visible = status.requiresAttention,
		enter = fadeIn(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		exit = fadeOut(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		modifier = modifier
	) {
		Surface(
			shape = RoundedCornerShape(18.dp),
			color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
			contentColor = MaterialTheme.colorScheme.onErrorContainer
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 180.dp, max = 300.dp)
					.padding(horizontal = 14.dp, vertical = 10.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				Text(
					text = status.label ?: "Whispersync",
					style = MaterialTheme.typography.labelLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				status.detail?.takeIf { it.isNotBlank() }?.let { detail ->
					Text(
						text = detail,
						style = MaterialTheme.typography.labelSmall,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
	}
}
