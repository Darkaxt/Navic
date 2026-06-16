package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderPaginationProfileStatus

private val readerPaginationBadgeFadeAnimationSpec = tween<Float>(150)

@Composable
internal fun KomikkuPaginationProfileStatusBadge(
	profile: ReaderPaginationProfileStatus,
	modifier: Modifier = Modifier
) {
	AnimatedVisibility(
		visible = profile.status == "measuring" || profile.status == "failed",
		enter = fadeIn(animationSpec = readerPaginationBadgeFadeAnimationSpec),
		exit = fadeOut(animationSpec = readerPaginationBadgeFadeAnimationSpec),
		modifier = modifier
	) {
		Surface(
			shape = RoundedCornerShape(18.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = 0.88f),
			contentColor = MaterialTheme.colorScheme.onSurface
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 180.dp, max = 280.dp)
					.padding(horizontal = 14.dp, vertical = 10.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)
			) {
				Text(
					text = profile.label,
					style = MaterialTheme.typography.labelMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				profile.progressFraction?.let { progress ->
					LinearProgressIndicator(
						progress = { progress.coerceIn(0f, 1f) },
						modifier = Modifier.fillMaxWidth(),
						color = MaterialTheme.colorScheme.primary,
						trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
					)
				}
			}
		}
	}
}
