package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingArtworkTapAction

fun shouldOpenLyricsFromNowPlayingArtworkTap(
	tapArtworkForLyrics: Boolean,
	showNowPlayingArtwork: Boolean,
	hasCurrentSong: Boolean,
	hasResolvedLyrics: Boolean = true
): Boolean = tapArtworkForLyrics && showNowPlayingArtwork && hasCurrentSong && hasResolvedLyrics

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
	hasCurrentSong: Boolean,
	hasResolvedLyrics: Boolean = true
): NowPlayingArtworkTapDestination? {
	if (!showNowPlayingArtwork || !hasCurrentSong) return null

	return when (effectiveNowPlayingArtworkTapAction(configuredAction, legacyTapArtworkForLyrics)) {
		NowPlayingArtworkTapAction.Disabled -> null
		NowPlayingArtworkTapAction.Lyrics -> NowPlayingArtworkTapDestination.Lyrics.takeIf {
			hasResolvedLyrics
		}
		NowPlayingArtworkTapAction.TrackInfo -> NowPlayingArtworkTapDestination.TrackInfo
	}
}

fun shouldShowNowPlayingLyricsAction(
	userActionEnabled: Boolean,
	hasCurrentSong: Boolean,
	hasResolvedLyrics: Boolean
): Boolean =
	userActionEnabled && hasCurrentSong && hasResolvedLyrics

fun shouldShowNowPlayingMusicBrainzInfoAction(
	fallbackEnabled: Boolean,
	hasCurrentSong: Boolean,
	artworkTapDestination: NowPlayingArtworkTapDestination?
): Boolean =
	fallbackEnabled &&
		hasCurrentSong &&
		artworkTapDestination != NowPlayingArtworkTapDestination.TrackInfo
