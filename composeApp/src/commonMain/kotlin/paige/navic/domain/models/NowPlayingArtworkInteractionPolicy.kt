package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingArtworkTapAction

fun shouldOpenLyricsFromNowPlayingArtworkTap(
	tapArtworkForLyrics: Boolean,
	showNowPlayingArtwork: Boolean,
	hasCurrentSong: Boolean
): Boolean = tapArtworkForLyrics && showNowPlayingArtwork && hasCurrentSong

enum class NowPlayingArtworkTapDestination {
	Lyrics,
	TrackInfo
}

fun effectiveNowPlayingArtworkTapAction(
	configuredAction: NowPlayingArtworkTapAction,
	legacyTapArtworkForLyrics: Boolean
): NowPlayingArtworkTapAction =
	if (configuredAction == NowPlayingArtworkTapAction.Disabled && legacyTapArtworkForLyrics) {
		NowPlayingArtworkTapAction.Lyrics
	} else {
		configuredAction
	}

fun nowPlayingArtworkTapDestination(
	configuredAction: NowPlayingArtworkTapAction,
	legacyTapArtworkForLyrics: Boolean,
	showNowPlayingArtwork: Boolean,
	hasCurrentSong: Boolean
): NowPlayingArtworkTapDestination? {
	if (!showNowPlayingArtwork || !hasCurrentSong) return null

	return when (effectiveNowPlayingArtworkTapAction(configuredAction, legacyTapArtworkForLyrics)) {
		NowPlayingArtworkTapAction.Disabled -> null
		NowPlayingArtworkTapAction.Lyrics -> NowPlayingArtworkTapDestination.Lyrics
		NowPlayingArtworkTapAction.TrackInfo -> NowPlayingArtworkTapDestination.TrackInfo
	}
}
