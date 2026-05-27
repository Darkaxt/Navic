package paige.navic.domain.models

fun shouldOpenLyricsFromNowPlayingArtworkTap(
	tapArtworkForLyrics: Boolean,
	hasCurrentSong: Boolean
): Boolean = tapArtworkForLyrics && hasCurrentSong
