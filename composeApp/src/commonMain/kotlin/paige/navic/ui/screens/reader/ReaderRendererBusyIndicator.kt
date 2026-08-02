package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.reader_renderer_busy
import org.jetbrains.compose.resources.stringResource

private val ReaderRendererBusyRed = Color(0xFFE53935)

@Composable
internal fun ReaderRendererBusyIndicator(
	visible: Boolean,
	modifier: Modifier = Modifier
) {
	val description = stringResource(Res.string.reader_renderer_busy)
	AnimatedVisibility(
		visible = visible,
		modifier = modifier,
		enter = fadeIn(animationSpec = tween(durationMillis = 120)),
		exit = fadeOut(animationSpec = tween(durationMillis = 180))
	) {
		Box(
			modifier = Modifier
				.size(48.dp)
				.background(Color.Black.copy(alpha = 0.68f), CircleShape)
				.semantics {
					contentDescription = description
					liveRegion = LiveRegionMode.Polite
				},
			contentAlignment = Alignment.Center
		) {
			Canvas(modifier = Modifier.size(30.dp)) {
				val strokeWidth = 3.dp.toPx()
				drawCircle(
					color = ReaderRendererBusyRed,
					style = Stroke(width = strokeWidth)
				)
				val inset = size.minDimension * 0.22f
				drawLine(
					color = ReaderRendererBusyRed,
					start = Offset(inset, inset),
					end = Offset(size.width - inset, size.height - inset),
					strokeWidth = strokeWidth,
					cap = StrokeCap.Round
				)
			}
		}
	}
}
