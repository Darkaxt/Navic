package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingInfoStyle

fun nowPlayingInfoSubtitle(
	style: NowPlayingInfoStyle,
	albumTitle: String?,
	artistName: String
): String =
	when (style) {
		NowPlayingInfoStyle.Essential -> artistName
		NowPlayingInfoStyle.AlbumAndArtist -> {
			val album = albumTitle?.trim()?.takeIf { it.isNotEmpty() }
			if (album == null) artistName else "$album \u2022 $artistName"
		}
	}
