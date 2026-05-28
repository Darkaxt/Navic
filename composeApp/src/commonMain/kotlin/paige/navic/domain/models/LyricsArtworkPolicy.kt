package paige.navic.domain.models

fun shouldShowLyricsArtwork(
	showLyricsArtwork: Boolean,
	coverArtId: String?
): Boolean = showLyricsArtwork && !coverArtId.isNullOrBlank()
