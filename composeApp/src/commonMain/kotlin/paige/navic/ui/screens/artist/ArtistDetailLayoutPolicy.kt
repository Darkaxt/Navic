package paige.navic.ui.screens.artist

import paige.navic.domain.models.DomainArtist

private const val TopSongRowHeightDp = 84
private const val MaxTopSongRows = 3

fun artistTopSongsGridRows(songCount: Int): Int =
	songCount.coerceIn(0, MaxTopSongRows)

fun artistTopSongsGridHeightDp(songCount: Int): Int =
	artistTopSongsGridRows(songCount) * TopSongRowHeightDp

fun artistDetailHeadingImageUrl(
	artist: DomainArtist,
	verifiedExternalImageUrl: String? = null
): String? {
	if (!artist.coverArtId.isNullOrBlank()) return null
	return artist.artistImageUrl?.trim()?.takeIf { it.isNotEmpty() }
		?: verifiedExternalImageUrl?.trim()?.takeIf { it.isNotEmpty() }
}
