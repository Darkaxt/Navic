package paige.navic.ui.components.common

import coil3.request.ImageRequest

internal actual fun ImageRequest.Builder.applyCoverArtNormalization(
	normalization: CoverArtNormalization
): ImageRequest.Builder = this
