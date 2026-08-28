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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Headset
import paige.navic.icons.outlined.Visibility
import paige.navic.icons.outlined.VisibilityOff
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderWhispersyncPlaybackControlState
import paige.navic.reader.ReaderWhispersyncStatus
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_resync
import org.jetbrains.compose.resources.stringResource

private val readerWhispersyncBadgeFadeAnimationSpec = tween<Float>(150)

internal data class ReaderWhispersyncCueMapReportSurface(
	val label: String,
	val maxLines: Int = Int.MAX_VALUE,
	val softWrap: Boolean = true
) {
	init {
		require(label.isNotBlank())
		require(maxLines > 0)
	}
}

internal fun readerWhispersyncCueMapReportSurface(
	enabled: Boolean,
	diagnosticLabel: String
): ReaderWhispersyncCueMapReportSurface? =
	diagnosticLabel.takeIf { enabled && it.isNotBlank() }
		?.let(::ReaderWhispersyncCueMapReportSurface)

@Composable
internal fun KomikkuWhispersyncPlaybackControl(
	control: ReaderWhispersyncPlaybackControlState,
	readerTheme: String?,
	onCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onOpenPlayer: () -> Unit,
	modifier: Modifier = Modifier
) {
	val latestControl = rememberUpdatedState(control)
	val latestOnCommand = rememberUpdatedState(onCommand)
	val latestOnOpenPlayer = rememberUpdatedState(onOpenPlayer)
	val accessibilityDescription = control.contentDescription.localizedDescription()
	AnimatedVisibility(
		visible = control.visible,
		enter = fadeIn(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		exit = fadeOut(animationSpec = readerWhispersyncBadgeFadeAnimationSpec),
		modifier = modifier
			.semantics {
				contentDescription = accessibilityDescription
				role = Role.Button
				if (control.enabled) {
					onClick(label = accessibilityDescription) {
						latestControl.value.command?.let(latestOnCommand.value)
						true
					}
				} else {
					disabled()
				}
			}
			.pointerInput(Unit) {
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
		val glyphColor = if (control.noAudioCueOnPage) {
			MaterialTheme.colorScheme.error
		} else {
			readerThemeForegroundColor(readerTheme).copy(alpha = 0.86f)
		}
		Box(
			modifier = Modifier.size(48.dp),
			contentAlignment = Alignment.Center
		) {
			Icon(
				imageVector = Icons.Outlined.Headset,
				contentDescription = null,
				tint = glyphColor,
				modifier = Modifier.size(22.dp)
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
internal fun KomikkuWhispersyncCueMapControl(
	enabled: Boolean,
	diagnosticLabel: String,
	readerTheme: String?,
	onToggle: () -> Unit,
	modifier: Modifier = Modifier
) {
	val reportSurface = readerWhispersyncCueMapReportSurface(
		enabled = enabled,
		diagnosticLabel = diagnosticLabel
	)
	Column(
		modifier = modifier.widthIn(max = 360.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		IconButton(
			onClick = onToggle,
			modifier = Modifier.size(48.dp)
		) {
			Icon(
				imageVector = if (enabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
				contentDescription = if (enabled) "Hide cue map" else "Show cue map",
				tint = readerThemeForegroundColor(readerTheme).copy(alpha = if (enabled) 1f else 0.72f),
				modifier = Modifier.size(22.dp)
			)
		}
		reportSurface?.let { reportSurface ->
			Text(
				text = reportSurface.label,
				style = MaterialTheme.typography.labelSmall,
				color = readerThemeForegroundColor(readerTheme).copy(alpha = 0.86f),
				maxLines = reportSurface.maxLines,
				softWrap = reportSurface.softWrap,
				overflow = TextOverflow.Visible,
				modifier = Modifier.padding(horizontal = 4.dp)
			)
		}
	}
}

@Composable
internal fun KomikkuWhispersyncStatusBadge(
	status: ReaderWhispersyncStatus,
	onRepairMismatch: () -> Unit,
	modifier: Modifier = Modifier
) {
	val label = status.localizedLabel()
	val detail = status.localizedDetail()
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
					text = label,
					style = MaterialTheme.typography.labelLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				detail?.let {
					Text(
						text = it,
						style = MaterialTheme.typography.labelSmall,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				if (status.repairable) {
					TextButton(onClick = onRepairMismatch) {
						Text(text = stringResource(Res.string.action_resync))
					}
				}
			}
		}
	}
}
