package paige.navic.ui.components.common

import coil3.request.ImageRequest

enum class CoverArtNormalization {
	None,
	TrimWhitespace
}

private const val CoverArtTrimWhitespaceCacheSuffix = ":trim-whitespace-v1"

internal fun normalizedCoverArtCacheKey(
	cacheKey: String?,
	normalization: CoverArtNormalization
): String? =
	when (normalization) {
		CoverArtNormalization.None -> cacheKey
		CoverArtNormalization.TrimWhitespace -> cacheKey?.let { "$it$CoverArtTrimWhitespaceCacheSuffix" }
	}

internal expect fun ImageRequest.Builder.applyCoverArtNormalization(
	normalization: CoverArtNormalization
): ImageRequest.Builder
