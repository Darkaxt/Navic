package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Audiobooks
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderWhispersyncPlaybackControlState
import paige.navic.reader.ReaderWhispersyncStatus

private val readerWhispersyncBadgeFadeAnimationSpec = tween<Float>(150)

@Composable
internal fun KomikkuWhispersyncPlaybackControl(
	control: ReaderWhispersyncPlaybackControlState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	modifier: Modifier = Modifier
) {
	AnimatedVisibility(
		visible = control.visible,
		enter = fadeIn(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		exit = fadeOut(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		modifier = modifier.pointerInput(Unit) {}
	) {
		val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
		Surface(
			shape = RoundedCornerShape(999.dp),
			color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
			contentColor = contentColor
		) {
			Box(
				modifier = Modifier.size(48.dp),
				contentAlignment = Alignment.Center
			) {
				if (control.loading) {
					CircularProgressIndicator(
						modifier = Modifier
							.matchParentSize()
							.padding(7.dp),
						color = contentColor,
						strokeWidth = 2.dp
					)
				}
				IconButton(
					onClick = { control.command?.let(onCommand) },
					enabled = control.enabled
				) {
					Icon(
						imageVector = Icons.Outlined.Audiobooks,
						contentDescription = control.contentDescription,
						modifier = Modifier.size(24.dp)
					)
				}
				if (control.crossed) {
					Canvas(
						modifier = Modifier
							.matchParentSize()
							.padding(13.dp)
					) {
						drawLine(
							color = contentColor,
							start = Offset(size.width * 0.14f, size.height * 0.86f),
							end = Offset(size.width * 0.86f, size.height * 0.14f),
							strokeWidth = 2.dp.toPx(),
							cap = StrokeCap.Round
						)
					}
				}
			}
		}
	}
}

@Composable
internal fun KomikkuWhispersyncStatusBadge(
	status: ReaderWhispersyncStatus,
	onRepairMismatch: () -> Unit,
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
				if (status.repairable) {
					TextButton(onClick = onRepairMismatch) {
						Text(text = "Resync")
					}
				}
			}
		}
	}
}
