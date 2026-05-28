package paige.navic.domain.models

fun shouldShowLyricsArtwork(
	showLyricsArtwork: Boolean,
	coverArtId: String?,
	imageUrl: String? = null
): Boolean = showLyricsArtwork && (!coverArtId.isNullOrBlank() || !imageUrl.isNullOrBlank())
