package paige.navic.ui.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import paige.navic.reader.ReaderColorFilterModeDarken
import paige.navic.reader.ReaderColorFilterModeLighten
import paige.navic.reader.ReaderColorFilterModeMultiply
import paige.navic.reader.ReaderColorFilterModeOverlay
import paige.navic.reader.ReaderColorFilterModeScreen
import paige.navic.reader.ReaderSettings

@Composable
internal fun KomikkuReaderContentOverlay(
	brightness: Int,
	color: Color?,
	colorBlendMode: BlendMode?,
	modifier: Modifier = Modifier
) {
	// Ported from Komikku ReaderContentOverlay: full-size filter layer independent of content layout.
	if (brightness < 0) {
		Canvas(modifier = modifier) {
			drawRect(Color.Black.copy(alpha = abs(brightness) / 100f))
		}
	}

	if (color != null) {
		Canvas(modifier = modifier) {
			drawRect(
				color = color,
				blendMode = colorBlendMode ?: BlendMode.SrcOver
			)
		}
	}
}

internal fun readerColorFilterColor(settings: ReaderSettings): Color? {
	if (settings.colorFilterEnabled != true) return null
	val argb = settings.colorFilterArgb ?: 0
	return Color(
		red = ((argb ushr 16) and 0xFF) / 255f,
		green = ((argb ushr 8) and 0xFF) / 255f,
		blue = (argb and 0xFF) / 255f,
		alpha = ((argb ushr 24) and 0xFF) / 255f
	)
}

internal fun readerColorFilterBlendMode(colorFilterMode: String?): BlendMode =
	when (colorFilterMode) {
		ReaderColorFilterModeMultiply -> BlendMode.Modulate
		ReaderColorFilterModeScreen -> BlendMode.Screen
		ReaderColorFilterModeOverlay -> BlendMode.Overlay
		ReaderColorFilterModeLighten -> BlendMode.Lighten
		ReaderColorFilterModeDarken -> BlendMode.Darken
		else -> BlendMode.SrcOver
	}
