package paige.navic.ui.components.common

import android.graphics.Bitmap
import android.graphics.Color
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import paige.navic.domain.models.CoverArtPixel
import paige.navic.domain.models.coverArtWhitespaceCropBounds

internal actual fun ImageRequest.Builder.applyCoverArtNormalization(
	normalization: CoverArtNormalization
): ImageRequest.Builder =
	when (normalization) {
		CoverArtNormalization.None -> this
		CoverArtNormalization.TrimWhitespace -> transformations(CoverArtWhitespaceCropTransformation)
	}

private object CoverArtWhitespaceCropTransformation : Transformation() {
	override val cacheKey: String = "paige.navic.cover-art-trim-whitespace-v1"

	override suspend fun transform(input: Bitmap, size: Size): Bitmap {
		val crop = coverArtWhitespaceCropBounds(
			width = input.width,
			height = input.height
		) { x, y ->
			val color = input.getPixel(x, y)
			CoverArtPixel(
				red = Color.red(color),
				green = Color.green(color),
				blue = Color.blue(color),
				alpha = Color.alpha(color)
			)
		} ?: return input

		return Bitmap.createBitmap(input, crop.left, crop.top, crop.width, crop.height)
	}
}
