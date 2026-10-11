package paige.navic.ui.components.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import paige.navic.domain.models.NowPlayingVinylGrooveEndRadiusFraction
import paige.navic.domain.models.NowPlayingVinylGrooveStartRadiusFraction
import paige.navic.domain.models.NowPlayingVinylLabelRadiusFraction
import paige.navic.domain.models.NowPlayingVinylSpindleRadiusFraction
import kotlin.math.min

@Composable
internal fun VinylRecordOverlay(
	modifier: Modifier = Modifier,
	labelRadiusFraction: Float = NowPlayingVinylLabelRadiusFraction,
	grooveStartRadiusFraction: Float = NowPlayingVinylGrooveStartRadiusFraction
) {
	val colorScheme = MaterialTheme.colorScheme
	Canvas(modifier = modifier) {
		val radius = min(size.width, size.height) / 2f
		if (radius <= 0f) return@Canvas
		val center = Offset(size.width / 2f, size.height / 2f)
		val startRadius = radius * grooveStartRadiusFraction
		val endRadius = radius * NowPlayingVinylGrooveEndRadiusFraction
		val grooveCount = 48
		val baseStrokeWidth = (radius * 0.0028f).coerceAtLeast(0.65f)
		val accentStrokeWidth = (radius * 0.004f).coerceAtLeast(0.9f)
		drawCircle(colorScheme.scrim.copy(alpha = 0.16f), radius * 0.985f, center)
		drawCircle(colorScheme.onSurface.copy(alpha = 0.16f), radius * 0.99f, center,
			style = Stroke(width = radius * 0.018f))
		repeat(grooveCount) { index ->
			val progress = index / (grooveCount - 1).toFloat()
			drawCircle(
				color = colorScheme.onSurface.copy(alpha = if (index % 7 == 0) 0.22f else 0.13f),
				radius = startRadius + (endRadius - startRadius) * progress,
				center = center,
				style = Stroke(width = if (index % 7 == 0) accentStrokeWidth else baseStrokeWidth,
					cap = StrokeCap.Round)
			)
		}
		drawCircle(colorScheme.surface.copy(alpha = 0.22f), radius * labelRadiusFraction, center)
		drawCircle(colorScheme.onSurface.copy(alpha = 0.32f), radius * labelRadiusFraction, center,
			style = Stroke(width = radius * 0.008f))
		drawCircle(colorScheme.surface.copy(alpha = 0.92f), radius * NowPlayingVinylSpindleRadiusFraction, center)
		drawCircle(colorScheme.onSurface.copy(alpha = 0.28f), radius * NowPlayingVinylSpindleRadiusFraction, center,
			style = Stroke(width = radius * 0.004f))
	}
}
