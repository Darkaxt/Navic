package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Headset
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderWhispersyncPlaybackControlState
import paige.navic.reader.ReaderWhispersyncStatus

private val readerWhispersyncBadgeFadeAnimationSpec = tween<Float>(150)

@Composable
internal fun KomikkuWhispersyncPlaybackControl(
	control: ReaderWhispersyncPlaybackControlState,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onOpenPlayer: () -> Unit,
	modifier: Modifier = Modifier
) {
	val latestControl = rememberUpdatedState(control)
	val latestOnCommand = rememberUpdatedState(onCommand)
	val latestOnOpenPlayer = rememberUpdatedState(onOpenPlayer)
	AnimatedVisibility(
		visible = control.visible,
		enter = fadeIn(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		exit = fadeOut(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		modifier = modifier.pointerInput(Unit) {
			detectTapGestures(
				onLongPress = { latestOnOpenPlayer.value() },
				onTap = {
					val currentControl = latestControl.value
					if (currentControl.enabled) {
						currentControl.command?.let(latestOnCommand.value)
					}
				}
			)
		}
	) {
		val glyphColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (control.enabled) 0.52f else 0.34f)
		Box(
			modifier = Modifier.size(48.dp),
			contentAlignment = Alignment.Center
		) {
			Icon(
				imageVector = Icons.Outlined.Headset,
				contentDescription = control.contentDescription,
				tint = glyphColor,
				modifier = Modifier.size(25.dp)
			)
			if (control.crossed) {
				Canvas(
					modifier = Modifier
						.matchParentSize()
						.padding(13.dp)
				) {
					drawLine(
						color = glyphColor,
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
