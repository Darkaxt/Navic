package paige.navic.domain.models

fun shouldOpenLyricsFromNowPlayingArtworkTap(
	tapArtworkForLyrics: Boolean,
	showNowPlayingArtwork: Boolean,
	hasCurrentSong: Boolean
): Boolean = tapArtworkForLyrics && showNowPlayingArtwork && hasCurrentSong
